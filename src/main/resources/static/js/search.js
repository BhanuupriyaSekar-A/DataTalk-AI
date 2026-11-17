/* SL Talk AI - Home search with two modes. Suggestions appear while typing; Enter shows full results or asks AI.
   Application Search: pages, actions and reports are matched in the browser; customers, devices, transactions
   and locations come from /api/search (scoped to the user). No AI is called.
   Report Search: the AI report chat (chat.js, via window.SLTalkChat). Submitting sends the question to the existing
   AI workflow (/api/chat/ask); instant reports that match the question are offered next to the answer. */
(function () {
    const BASE = '/sltalk';
    const API = BASE + '/api';
    const DEBOUNCE_MS = 250;
    const chat = window.SLTalkChat;

    const MODES = {
        app: {label: 'Application Search', placeholder: 'Search anything in SL Talk AI...', maxLength: 100,
            submitIcon: 'fa-arrow-right', submitLabel: 'Search'},
        report: {label: 'Report Search', placeholder: 'Ask for a report, e.g. "Generate a fees report for last month"...', maxLength: 1000,
            submitIcon: 'fa-paper-plane', submitLabel: 'Send'}
    };

    const PAGES = [
        {label: 'Report Search', icon: 'fa-wand-magic-sparkles', keys: 'ai chat ask question assistant report reports custom generate instant',
            sub: 'Explore SeedLive Reports', run: () => switchMode('report')},
        {label: 'Dashboard', url: '/dashboard', icon: 'fa-gauge', keys: 'home overview summary main kpi', sub: 'Business summary and key figures'},
        {label: 'Customers', url: '/customers', icon: 'fa-building', keys: 'customer clients client merchants merchant', sub: 'Customer list and details', admin: true},
        {label: 'Devices', url: '/devices', icon: 'fa-cash-register', keys: 'device terminals terminal pos readers reader atm', sub: 'Device list, status and details'},
        {label: 'Transactions', url: '/transactions', icon: 'fa-receipt', keys: 'transaction payments payment sales txn', sub: 'Payment transactions'},
        {label: 'Locations', url: '/locations', icon: 'fa-location-dot', keys: 'location branches branch stores store sites site', sub: 'Locations and their devices'},
        {label: 'Query Analytics', url: '/analytics', icon: 'fa-chart-line', keys: 'analytics query usage statistics stats', sub: 'Report Search usage statistics', admin: true},
        {label: 'Project Info', url: '/poc-info', icon: 'fa-circle-info', keys: 'project about poc information help', sub: 'About this application'}
    ];

    const ACTIONS = [
        {label: 'Add Customer', url: '/customers?action=new', icon: 'fa-user-plus', keys: 'create new register customer client merchant', sub: 'Open the Create Customer form', admin: true},
        {label: 'Edit Customer', url: '/customers?action=edit', icon: 'fa-user-pen', keys: 'update modify change customer client merchant', sub: 'Find a customer, open it and click Edit', admin: true},
        {label: 'Add Device', url: '/devices?action=new', icon: 'fa-plus', keys: 'create new register device terminal pos reader', sub: 'Open the Add Device form', admin: true},
        {label: 'Create Location', url: '/locations?action=new', icon: 'fa-map-pin', keys: 'add new register location branch store site', sub: 'Open the Create Location form', admin: true},
        {label: 'Logout', icon: 'fa-right-from-bracket', keys: 'log out sign out signout exit', sub: 'End your session', run: () => window.logout()}
    ];

    // A record-type word in the query ("customer ABC", "Chennai location") limits the record results to that type.
    const ENTITY_WORDS = {
        customer: 'customers', customers: 'customers', client: 'customers', clients: 'customers', merchant: 'customers', merchants: 'customers',
        device: 'devices', devices: 'devices', terminal: 'devices', terminals: 'devices',
        transaction: 'transactions', transactions: 'transactions', txn: 'transactions',
        location: 'locations', locations: 'locations', branch: 'locations', branches: 'locations',
        store: 'locations', stores: 'locations', site: 'locations', sites: 'locations'
    };

    const STOP_WORDS = new Set(['a', 'an', 'the', 'of', 'for', 'by', 'and', 'or', 'in', 'on', 'to', 'with', 'show', 'me', 'my',
        'all', 'list', 'report', 'reports', 'give', 'get', 'find', 'what', 'which', 'is', 'are', 'per', 'i', 'want', 'need', 'see',
        'generate', 'create', 'make', 'last', 'this', 'month', 'week', 'day', 'days', 'year', 'today', 'total', 'particular']);

    const app = document.getElementById('searchApp');
    const form = document.getElementById('searchForm');
    const input = document.getElementById('searchInput');
    const clearBtn = document.getElementById('searchClear');
    const submitBtn = document.getElementById('sendBtn');
    const box = document.getElementById('suggestBox');
    const resultsView = document.getElementById('resultsView');
    const resultsMeta = document.getElementById('resultsMeta');
    const resultsBody = document.getElementById('resultsBody');
    const tabs = Array.from(document.querySelectorAll('.mode-tab'));

    const state = {mode: 'app', view: 'home', isAdmin: false, reports: null};
    const recordCache = new Map();
    let items = [], active = -1, timer = null, suggestToken = 0, resultsToken = 0, resultItems = [];

    // ---------- helpers ----------
    const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
    const words = s => String(s ?? '').toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
    const fmtDate = v => String(v ?? '').replace('T', ' ').substring(0, 16);
    const titleCase = s => String(s ?? '').replace(/(^|[\s_-])([a-z])/g, (m, p, c) => (p === '_' || p === '-' ? ' ' : p) + c.toUpperCase());
    const hit = (w, own) => own.some(o => o.startsWith(w) || (w.length > 3 && o.startsWith(w.replace(/s$/, ''))));
    // Every typed word must start one of the entry's words ("add cust", "fee rep", "sign out").
    const matches = (text, q) => { const own = words(text); return words(q).every(w => hit(w, own)); };
    const visible = list => list.filter(e => !e.admin || state.isAdmin);
    const spinner = text => `<div class="search-state"><span class="spinner-border spinner-border-sm me-2" role="status"></span>${esc(text)}</div>`;

    function parseQuery(q) {
        const parts = q.trim().split(/\s+/).filter(Boolean);
        const i = parts.findIndex(p => ENTITY_WORDS[p.toLowerCase()]);
        if (i < 0 || parts.length < 2) return {entity: null, text: q.trim()};
        const entity = ENTITY_WORDS[parts[i].toLowerCase()];
        parts.splice(i, 1);
        return {entity, text: parts.join(' ')};
    }

    const recordsSearchable = text => text.length >= 2 || /^\d+$/.test(text);

    // Record lookups are cached per text as {state: loading|done|error, data, error, waiters}; errors are retried on new input.
    function lookupRecords(text, onDone) {
        let e = recordCache.get(text);
        if (!e) {
            e = {state: 'loading', waiters: []};
            recordCache.set(text, e);
            fetch(API + '/search?q=' + encodeURIComponent(text)).then(async res => {
                if (res.status === 401) { window.location.href = BASE + '/login'; throw new Error('Session expired'); }
                let body = null;
                try { body = await res.json(); } catch (err) { /* non-JSON */ }
                if (!res.ok || !body || !body.success) throw new Error((body && (body.message || body.error)) || 'HTTP ' + res.status);
                e.state = 'done';
                e.data = body.data;
            }).catch(err => {
                e.state = 'error';
                e.error = err.message;
            }).finally(() => e.waiters.splice(0).forEach(fn => fn()));
        }
        if (e.state === 'loading') e.waiters.push(onDone);
        return e;
    }

    function loadUser() {
        return fetch(API + '/user-info').then(r => r.json()).then(b => {
            if (b.success) state.isAdmin = b.data.role === 'ADMIN';
        }).catch(() => {}).finally(refresh);
    }

    // ---------- reports ----------
    const reportText = r => [r.name, r.category, 'report reports', r.description, (r.examples || []).join(' ')].join(' ');
    const openReport = r => { switchMode('report'); chat.runReport(r.id, r.name); };
    const reportItem = r => ({icon: 'fa-bolt', label: r.name, sub: titleCase(r.category) + ' - instant report', run: () => openReport(r)});

    // Scores reports against a request by name, then category, then description/examples; generic words are ignored.
    // full = every meaningful word of the request matched the report.
    function scoreReports(q) {
        const terms = words(q).filter(w => !STOP_WORDS.has(w));
        if (!terms.length) return [];
        return (state.reports || []).map(r => {
            const name = words(r.name), cat = words(r.category), body = words(r.description + ' ' + (r.examples || []).join(' '));
            let score = 0, hits = 0;
            terms.forEach(t => {
                const s = hit(t, name) ? 3 : hit(t, cat) ? 2 : hit(t, body) ? 1 : 0;
                score += s;
                if (s) hits++;
            });
            return {r, score, full: hits === terms.length};
        }).filter(x => x.score > 0)
            .sort((a, b) => (b.full - a.full) || b.score - a.score || a.r.name.localeCompare(b.r.name));
    }

    // ---------- Application Search results ----------
    function recordGroups(data, entity) {
        if (!data) return [];
        const want = k => !entity || entity === k;
        return [
            ['Customers', want('customers') ? data.customers.map(c => ({icon: 'fa-building', label: c.customerName,
                sub: 'Customer #' + c.customerId + ' · ' + c.status + (c.email ? ' · ' + c.email : ''), url: BASE + '/customers?id=' + c.customerId})) : []],
            ['Devices', want('devices') ? data.devices.map(v => ({icon: 'fa-cash-register', label: v.serial_number,
                sub: 'Device #' + v.device_id + ' · ' + titleCase(String(v.device_type || '').toLowerCase()) + ' · ' + v.status + ' · ' + v.location_name,
                url: BASE + '/devices?id=' + v.device_id})) : []],
            ['Transactions', want('transactions') ? data.transactions.map(t => ({icon: 'fa-receipt', label: 'Transaction #' + t.transaction_id,
                sub: t.amount + ' · ' + t.transaction_status + ' · ' + fmtDate(t.transaction_time) + ' · ' + t.serial_number,
                url: BASE + '/transactions?id=' + t.transaction_id})) : []],
            ['Locations', want('locations') ? data.locations.map(l => ({icon: 'fa-location-dot', label: l.location_name,
                sub: 'Location #' + l.location_id + ' · ' + [l.city, l.state].filter(Boolean).join(', ') + ' · ' + l.customer_name,
                url: BASE + '/locations?id=' + l.location_id})) : []]
        ].map(([title, list]) => ({title, items: list}));
    }

    function appGroups(q, data, entity, reportLimit) {
        const groups = [
            {title: 'Pages', items: visible(PAGES).filter(e => matches(e.label + ' ' + e.keys, q)).map(e => ({...e, url: e.url && BASE + e.url}))},
            {title: 'Actions', items: visible(ACTIONS).filter(e => matches(e.label + ' ' + e.keys, q)).map(e => ({...e, url: e.url && BASE + e.url}))},
            ...recordGroups(data, entity),
            {title: 'Reports', items: (state.reports || []).filter(r => matches(reportText(r), q)).slice(0, reportLimit).map(reportItem)}
        ];
        return groups.filter(g => g.items.length);
    }

    // Record lookup for a query: {state: idle|loading|done|error, data, error, entity}
    function recordStatus(q, onDone) {
        const {entity, text} = parseQuery(q);
        if (!recordsSearchable(text)) return {state: 'idle', entity};
        const e = lookupRecords(text, onDone);
        return {state: e.state, data: e.data, error: e.error, entity};
    }

    // ---------- suggestions dropdown (shown while typing) ----------
    function suggestionGroups(q, token) {
        if (state.mode === 'report') {
            const ask = {icon: 'fa-wand-magic-sparkles', label: 'Ask AI: ' + q, sub: 'Generate a custom report from your request',
                run: () => sendQuestion(q)};
            return {groups: [{title: 'Report Search', items: [ask]},
                {title: 'Matching reports', items: scoreReports(q).slice(0, 5).map(x => reportItem(x.r))}], rec: {state: 'idle'}};
        }
        const rec = recordStatus(q, () => { if (token === suggestToken) renderSuggestions(); });
        return {groups: appGroups(q, rec.data, rec.entity, 5), rec};
    }

    function renderSuggestions() {
        const q = input.value.trim();
        const token = ++suggestToken;
        items = [];
        active = -1;
        if (!q) { closeSuggestions(); return; }
        const {groups, rec} = suggestionGroups(q, token);
        let html = '';
        groups.filter(g => g.items.length).forEach(g => {
            html += `<div class="suggest-group" role="presentation">${esc(g.title)}</div>`;
            g.items.slice(0, 6).forEach(it => {
                const i = items.push(it) - 1;
                html += `<div class="suggest-item" role="option" id="sg-opt-${i}" data-i="${i}" aria-selected="false">
                    <i class="fas ${it.icon}" aria-hidden="true"></i>
                    <div class="suggest-text"><div class="suggest-label">${esc(it.label)}</div>${it.sub ? `<div class="suggest-sub">${esc(it.sub)}</div>` : ''}</div></div>`;
            });
        });
        if (rec.state === 'loading') html += spinner('Searching records...');
        else if (rec.state === 'error') html += `<div class="search-state text-danger"><i class="fas fa-triangle-exclamation me-1"></i>Could not search records: ${esc(rec.error)}</div>`;
        else if (!items.length && !state.reports) html += spinner('Searching...');
        else if (!items.length) html += `<div class="search-state">No matches for "${esc(q)}".</div>`;
        html += state.mode === 'report'
            ? '<div class="suggest-footer"><i class="fas fa-turn-down fa-rotate-90 me-1"></i>Press Enter to ask AI</div>'
            : '<div class="suggest-footer"><i class="fas fa-turn-down fa-rotate-90 me-1"></i>Press Enter to see all Application Search results</div>';
        box.innerHTML = html;
        box.classList.remove('d-none');
        input.setAttribute('aria-expanded', 'true');
    }

    function closeSuggestions() {
        clearTimeout(timer);
        ++suggestToken;
        box.classList.add('d-none');
        box.innerHTML = '';
        items = [];
        active = -1;
        input.setAttribute('aria-expanded', 'false');
        input.removeAttribute('aria-activedescendant');
    }

    function setActive(i) {
        if (!items.length) return;
        active = (i + items.length) % items.length;
        box.querySelectorAll('.suggest-item').forEach(el => {
            const on = Number(el.dataset.i) === active;
            el.classList.toggle('active', on);
            el.setAttribute('aria-selected', on);
            if (on) el.scrollIntoView({block: 'nearest'});
        });
        input.setAttribute('aria-activedescendant', 'sg-opt-' + active);
    }

    function open(it) {
        if (!it) return;
        closeSuggestions();
        if (state.mode === 'report' && it.run) {
            input.value = '';
            clearBtn.classList.add('d-none');
        }
        if (it.run) it.run();
        else window.location.href = it.url;
    }

    // ---------- Application Search results page ----------
    function resultItem(it) {
        const i = resultItems.push(it) - 1;
        const inner = `<span class="result-icon"><i class="fas ${it.icon}" aria-hidden="true"></i></span>
            <span class="result-text"><span class="result-label">${esc(it.label)}</span>${it.sub ? `<span class="result-sub">${esc(it.sub)}</span>` : ''}</span>
            <i class="fas fa-chevron-right result-chevron" aria-hidden="true"></i>`;
        return it.run ? `<button type="button" class="result-item" data-r="${i}">${inner}</button>`
            : `<a class="result-item" href="${esc(it.url)}">${inner}</a>`;
    }

    function noResults(q) {
        return `<div class="results-empty">
            <i class="fas fa-magnifying-glass empty-icon" aria-hidden="true"></i>
            <h2 class="h5">No results found for "${esc(q)}"</h2>
            <p class="text-muted mb-3">Check the spelling, try fewer or more general words, or search by ID (e.g. "device 12").
                Looking for data or a report? Ask in Report Search.</p>
            <button type="button" class="btn btn-primary" data-switch-mode="report"><i class="fas fa-wand-magic-sparkles me-1"></i>Ask in Report Search</button>
            </div>`;
    }

    function renderResults() {
        const q = input.value.trim();
        const token = ++resultsToken;
        resultItems = [];
        const rec = recordStatus(q, () => { if (token === resultsToken && state.view === 'results') renderResults(); });
        const groups = appGroups(q, rec.data, rec.entity, 20);
        const count = groups.reduce((n, g) => n + g.items.length, 0);
        resultsMeta.textContent = rec.state === 'loading' ? 'Searching...' : count + ' result' + (count === 1 ? '' : 's') + ' for "' + q + '"';
        let html = groups.map(g => `<section class="result-group"><h2 class="result-group-title">${esc(g.title)}
            <span class="badge rounded-pill">${g.items.length}</span></h2>${g.items.map(resultItem).join('')}</section>`).join('');
        if (rec.state === 'loading') html += spinner('Searching customers, devices, transactions and locations...');
        else if (rec.state === 'error') html += `<div class="alert alert-danger"><i class="fas fa-triangle-exclamation me-1"></i>Could not search records: ${esc(rec.error)}</div>`;
        else if (!count && !state.reports) html = spinner('Searching...');
        else if (!count) html = noResults(q);
        resultsBody.innerHTML = html;
    }

    // ---------- Report Search (AI chat) ----------
    function sendQuestion(q) {
        q = (q || '').trim();
        if (!q) { input.focus(); return; }
        if (chat.isBusy()) {
            document.getElementById('chatStatus').textContent = 'Please wait for the current report to finish...';
            return;
        }
        closeSuggestions();
        const related = scoreReports(q).filter(x => x.full).slice(0, 3).map(x => x.r);
        input.value = '';
        clearBtn.classList.add('d-none');
        chat.ask(q, related);
    }

    // ---------- view / mode / history ----------
    function setMode(mode) {
        state.mode = MODES[mode] ? mode : 'app';
        const m = MODES[state.mode];
        input.placeholder = m.placeholder;
        input.maxLength = m.maxLength;
        if (input.value.length > m.maxLength) input.value = input.value.substring(0, m.maxLength);
        submitBtn.innerHTML = `<i class="fas ${m.submitIcon}"></i>`;
        submitBtn.setAttribute('aria-label', m.submitLabel);
        input.setAttribute('aria-label', m.label);
        tabs.forEach(t => {
            const on = t.dataset.mode === state.mode;
            t.classList.toggle('active', on);
            t.setAttribute('aria-selected', on);
            t.tabIndex = on ? 0 : -1;
        });
    }

    function setView(view) {
        // Recent Questions are shown again every time Report Search is opened.
        if (view === 'report' && state.view !== 'report') chat.showHistory(true);
        state.view = view;
        app.classList.toggle('is-home', view === 'home');
        app.classList.toggle('is-results', view === 'results');
        app.classList.toggle('is-report', view === 'report');
        resultsView.classList.toggle('d-none', view !== 'results');
        document.title = 'SL Talk AI - ' + (view === 'home' ? 'Home' : MODES[state.mode].label);
        if (view === 'results') renderResults();
    }

    function pushUrl() {
        const params = state.mode === 'report' ? {mode: 'report'} : {mode: 'app', q: input.value.trim()};
        const url = window.location.pathname + '?' + new URLSearchParams(params);
        if (url !== window.location.pathname + window.location.search) history.pushState(null, '', url);
    }

    function search() {
        const q = input.value.trim();
        if (state.mode === 'report') { sendQuestion(q); return; }
        if (!q) { input.focus(); return; }
        closeSuggestions();
        pushUrl();
        setView('results');
    }

    function switchMode(mode) {
        if (mode === state.mode) return; // Don't switch if already in this mode
        setMode(mode);
        closeSuggestions();
        
        // Keep current view stable - just change the mode and input placeholder
        // Don't jump to 'results' or 'home' just because user switched tabs
        if (mode === 'report') {
            // Switching TO Report Search: show report view
            if (state.view !== 'report') {
                pushUrl();
                setView('report');
            }
        } else {
            // Switching TO Application Search: stay in current view, but clear the input
            input.value = '';
            clearBtn.classList.add('d-none');
            if (state.view === 'report') {
                // Coming FROM report search - go to home
                history.pushState(null, '', window.location.pathname);
                setView('home');
            }
        }
        input.focus();
    }

    function fromUrl() {
        const params = new URLSearchParams(window.location.search);
        closeSuggestions();
        if (params.get('mode') === 'report') {
            setMode('report');
            setView('report');
            // Links from elsewhere: ?report=id runs an instant report, ?ask=text prefills a question (not sent).
            if (params.get('report')) chat.runReport(params.get('report'), params.get('name') || params.get('report'));
            input.value = params.get('ask') || '';
            if (params.has('report') || params.has('ask')) history.replaceState(null, '', window.location.pathname + '?mode=report');
        } else {
            setMode('app');
            input.value = params.get('q') || '';
            setView(params.has('q') ? 'results' : 'home');
        }
        clearBtn.classList.toggle('d-none', !input.value);
    }

    function refresh() {
        if (state.view === 'results') renderResults();
        if (!box.classList.contains('d-none')) renderSuggestions();
    }

    // ---------- events ----------
    form.addEventListener('submit', ev => {
        ev.preventDefault();
        if (active >= 0) open(items[active]);
        else search();
    });

    input.addEventListener('input', () => {
        clearBtn.classList.toggle('d-none', !input.value);
        clearTimeout(timer);
        recordCache.forEach((e, k) => { if (e.state === 'error') recordCache.delete(k); });
        if (!input.value.trim()) { closeSuggestions(); return; }
        timer = setTimeout(renderSuggestions, state.mode === 'app' ? DEBOUNCE_MS : 0);
    });

    input.addEventListener('keydown', ev => {
        const isOpen = !box.classList.contains('d-none');
        if (ev.key === 'ArrowDown') { ev.preventDefault(); if (!isOpen) renderSuggestions(); else setActive(active + 1); }
        else if (ev.key === 'ArrowUp') { ev.preventDefault(); if (isOpen) setActive(active - 1); }
        else if (ev.key === 'Escape') {
            if (isOpen) closeSuggestions();
            else { input.value = ''; clearBtn.classList.add('d-none'); }
        }
    });

    input.addEventListener('focus', () => { if (input.value.trim() && state.view !== 'results') renderSuggestions(); });
    box.addEventListener('mousedown', ev => ev.preventDefault());
    box.addEventListener('click', ev => {
        const el = ev.target.closest('.suggest-item');
        if (el) open(items[Number(el.dataset.i)]);
    });
    clearBtn.addEventListener('click', () => {
        input.value = '';
        clearBtn.classList.add('d-none');
        closeSuggestions();
        input.focus();
    });
    document.addEventListener('click', ev => { if (!form.contains(ev.target)) closeSuggestions(); });

    tabs.forEach((t, i) => {
        t.addEventListener('click', () => switchMode(t.dataset.mode));
        t.addEventListener('keydown', ev => {
            if (ev.key !== 'ArrowRight' && ev.key !== 'ArrowLeft') return;
            ev.preventDefault();
            const next = tabs[(i + (ev.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length];
            next.focus();
            switchMode(next.dataset.mode);
        });
    });

    resultsBody.addEventListener('click', ev => {
        const btn = ev.target.closest('[data-switch-mode]');
        if (btn) { switchMode(btn.dataset.switchMode); return; }
        const run = ev.target.closest('[data-r]');
        if (run) resultItems[Number(run.dataset.r)].run();
    });

    // Example questions under Report Search go through the same path as typed questions.
    document.getElementById('reportPanel').addEventListener('click', ev => {
        const ex = ev.target.closest('.example-q');
        if (ex) sendQuestion(ex.textContent);
    });

    window.addEventListener('popstate', fromUrl);

    chat.reports.then(list => { state.reports = list; }, () => { state.reports = []; }).finally(refresh);
    fromUrl();
    loadUser();
})();
