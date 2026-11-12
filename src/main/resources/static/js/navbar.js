/* DataTalk AI - shared navbar: role-based menu items, user name, and Global Search.
   Global Search finds menus (read from the navbar), actions, reports and records. It never calls AI Chat. */
(function () {
    const BASE = '/datatalk';
    const DEBOUNCE_MS = 300;

    const ACTIONS = [
        {label: 'Add Customer', url: '/customers?action=new', icon: 'fa-user-plus', keys: 'create new register customer client merchant', admin: true},
        {label: 'Edit Customer', url: '/customers?action=edit', icon: 'fa-user-pen', keys: 'update modify change customer client merchant', admin: true},
        {label: 'Add Device', url: '/devices?action=new', icon: 'fa-plus', keys: 'create new register device terminal pos reader', admin: true},
        {label: 'Create Location', url: '/locations?action=new', icon: 'fa-map-pin', keys: 'add new register location branch store site', admin: true},
        {label: 'Logout', icon: 'fa-right-from-bracket', keys: 'log out sign out signout exit', run: () => window.logout()}
    ];

    const nav = document.querySelector('#navbarNav .navbar-nav');
    if (!nav) return;

    if (typeof window.logout !== 'function') {
        window.logout = async function () {
            try { await fetch(BASE + '/api/logout', {method: 'POST'}); } finally { window.location.href = BASE + '/login?logout=true'; }
        };
    }

    let isAdmin = false;
    fetch(BASE + '/api/user-info').then(r => r.json()).then(b => {
        if (!b.success) return;
        isAdmin = b.data.role === 'ADMIN';
        if (isAdmin) nav.querySelectorAll('[data-role="ADMIN"]').forEach(el => el.classList.remove('d-none'));
        const user = document.getElementById('userDisplay');
        const role = document.getElementById('userRole');
        if (user && user.textContent === 'User') user.textContent = b.data.username;
        if (role && !role.textContent) role.textContent = b.data.role + (b.data.customerName ? ' - ' + b.data.customerName : '');
    }).catch(() => {});

    // ---------- search box ----------
    const li = document.createElement('li');
    li.className = 'nav-item global-search';
    li.innerHTML = `
        <div class="global-search-box" role="search">
            <i class="fas fa-magnifying-glass gs-icon" aria-hidden="true"></i>
            <input type="text" class="form-control form-control-sm" id="gsInput" maxlength="100" autocomplete="off"
                   placeholder="Search pages, reports, records..." aria-label="Global search"
                   role="combobox" aria-expanded="false" aria-controls="gsResults" aria-autocomplete="list">
            <button type="button" class="gs-clear d-none" id="gsClear" aria-label="Clear search"><i class="fas fa-xmark"></i></button>
            <div class="gs-results d-none" id="gsResults" role="listbox"></div>
        </div>`;
    nav.insertBefore(li, nav.firstElementChild);

    const input = li.querySelector('#gsInput');
    const clearBtn = li.querySelector('#gsClear');
    const box = li.querySelector('#gsResults');
    let timer = null, controller = null, items = [], active = -1, reports = null;
    let records = {state: 'idle', data: null, error: null};

    const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
    const words = s => String(s).toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);

    // Every typed word must start one of the entry's words ("add cust", "fee rep", "sign out").
    function matches(text, q) {
        const own = words(text);
        return words(q).every(w => own.some(o => o.startsWith(w) || (w.length > 3 && o.startsWith(w.replace(/s$/, '')))));
    }

    // Menus come from the navbar itself, so search and navigation always list the same pages.
    function menus() {
        return Array.from(nav.querySelectorAll(':scope > li.nav-item:not(.d-none):not(.global-search) > a.nav-link[href^="' + BASE + '/"]'))
            .map(a => ({label: a.textContent.trim(), url: a.getAttribute('href'), icon: a.dataset.icon || 'fa-link', keys: a.dataset.searchKeys || ''}));
    }

    function loadReports() {
        if (reports) return;
        reports = [];
        fetch(BASE + '/api/reports').then(r => r.json()).then(b => {
            if (!b.success) return;
            reports = b.data;
            if (input.value.trim()) render();
        }).catch(() => {});
    }

    function render() {
        const q = input.value.trim();
        items = [];
        active = -1;
        if (!q) { close(); return; }
        const pick = (list, text) => list.filter(e => matches(text(e), q));
        const fmtDate = v => String(v ?? '').replace('T', ' ').substring(0, 16);
        const groups = [
            ['Menus', pick(menus(), e => e.label + ' ' + e.keys)],
            ['Actions', pick(ACTIONS.filter(e => !e.admin || isAdmin), e => e.label + ' ' + e.keys)
                .map(e => ({...e, url: e.url && BASE + e.url}))],
            ['Reports', pick(reports || [], r => r.name + ' ' + r.category + ' report reports ' + r.description).slice(0, 8)
                .map(r => ({icon: 'fa-file-lines', label: r.name, sub: r.category + ' report',
                    url: BASE + '/chat?report=' + encodeURIComponent(r.id) + '&name=' + encodeURIComponent(r.name)}))]
        ];
        const d = records.data;
        if (d) {
            groups.push(['Customers', d.customers.map(c => ({icon: 'fa-building', label: c.customerName,
                sub: 'Customer #' + c.customerId + ' - ' + c.status, url: BASE + '/customers?id=' + c.customerId}))]);
            groups.push(['Devices', d.devices.map(v => ({icon: 'fa-cash-register', label: v.serial_number,
                sub: 'Device #' + v.device_id + ' - ' + v.device_type + ' - ' + v.status + ' - ' + v.location_name,
                url: BASE + '/devices?id=' + v.device_id}))]);
            groups.push(['Transactions', d.transactions.map(t => ({icon: 'fa-receipt', label: 'Transaction #' + t.transaction_id,
                sub: t.amount + ' - ' + t.transaction_status + ' - ' + fmtDate(t.transaction_time) + ' - ' + t.serial_number,
                url: BASE + '/transactions?id=' + t.transaction_id}))]);
            groups.push(['Locations', d.locations.map(l => ({icon: 'fa-location-dot', label: l.location_name,
                sub: 'Location #' + l.location_id + ' - ' + [l.city, l.state].filter(Boolean).join(', ') + ' - ' + l.customer_name,
                url: BASE + '/locations?id=' + l.location_id}))]);
        }

        let html = '';
        groups.forEach(([title, list]) => {
            if (!list.length) return;
            html += `<div class="gs-group" role="presentation">${title}</div>`;
            list.forEach(it => {
                const i = items.push(it) - 1;
                html += `<div class="gs-item" role="option" id="gs-opt-${i}" data-i="${i}" aria-selected="false">
                    <i class="fas ${it.icon}" aria-hidden="true"></i>
                    <div><div>${esc(it.label)}</div>${it.sub ? `<div class="gs-sub">${esc(it.sub)}</div>` : ''}</div></div>`;
            });
        });
        if (records.state === 'loading') {
            html += '<div class="gs-state"><span class="spinner-border spinner-border-sm me-2"></span>Searching records...</div>';
        } else if (records.state === 'error') {
            html += `<div class="gs-state text-danger"><i class="fas fa-triangle-exclamation me-1"></i>Could not search records: ${esc(records.error)}</div>`;
        } else if (!items.length) {
            html += `<div class="gs-state">No results for "${esc(q)}"</div>`;
        }
        box.innerHTML = html;
        box.classList.remove('d-none');
        input.setAttribute('aria-expanded', 'true');
    }

    function close() {
        box.classList.add('d-none');
        box.innerHTML = '';
        input.setAttribute('aria-expanded', 'false');
        input.removeAttribute('aria-activedescendant');
    }

    function setActive(i) {
        if (!items.length) return;
        active = (i + items.length) % items.length;
        box.querySelectorAll('.gs-item').forEach(el => {
            const on = Number(el.dataset.i) === active;
            el.classList.toggle('active', on);
            el.setAttribute('aria-selected', on);
            if (on) el.scrollIntoView({block: 'nearest'});
        });
        input.setAttribute('aria-activedescendant', 'gs-opt-' + active);
    }

    function select(i) {
        const it = items[i];
        if (!it) return;
        close();
        if (it.run) it.run();
        else window.location.href = it.url;
    }

    async function searchRecords(q) {
        if (controller) controller.abort();
        controller = new AbortController();
        try {
            const res = await fetch(BASE + '/api/search?q=' + encodeURIComponent(q), {signal: controller.signal});
            if (res.status === 401) { window.location.href = BASE + '/login'; return; }
            let body = null;
            try { body = await res.json(); } catch (e) { /* non-JSON */ }
            if (!res.ok || !body || !body.success) throw new Error((body && (body.message || body.error)) || 'HTTP ' + res.status);
            records = {state: 'done', data: body.data, error: null};
        } catch (e) {
            if (e.name === 'AbortError') return;
            records = {state: 'error', data: null, error: e.message};
        }
        if (input.value.trim() === q) render();
    }

    input.addEventListener('input', () => {
        const q = input.value.trim();
        clearBtn.classList.toggle('d-none', !input.value);
        clearTimeout(timer);
        if (controller) controller.abort();
        loadReports();
        // Single characters only search records when they are an id (e.g. device 5)
        const searchable = q.length >= 2 || /^\d+$/.test(q);
        records = searchable ? {state: 'loading', data: null, error: null} : {state: 'idle', data: null, error: null};
        render();
        if (searchable) timer = setTimeout(() => searchRecords(q), DEBOUNCE_MS);
    });

    input.addEventListener('keydown', ev => {
        if (ev.key === 'ArrowDown') { ev.preventDefault(); if (box.classList.contains('d-none')) render(); else setActive(active + 1); }
        else if (ev.key === 'ArrowUp') { ev.preventDefault(); setActive(active - 1); }
        else if (ev.key === 'Enter') { ev.preventDefault(); select(active >= 0 ? active : 0); }
        else if (ev.key === 'Escape') {
            if (!box.classList.contains('d-none')) close();
            else { input.value = ''; input.dispatchEvent(new Event('input')); }
        }
    });

    input.addEventListener('focus', () => { loadReports(); if (input.value.trim()) render(); });
    box.addEventListener('mousedown', ev => ev.preventDefault());
    box.addEventListener('click', ev => {
        const el = ev.target.closest('.gs-item');
        if (el) select(Number(el.dataset.i));
    });
    clearBtn.addEventListener('click', () => {
        input.value = '';
        input.dispatchEvent(new Event('input'));
        input.focus();
    });
    document.addEventListener('click', ev => { if (!li.contains(ev.target)) close(); });
})();
