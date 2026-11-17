/* SL Talk AI - shared minimal header: signed-in user's name/role and logout.
   Navigation to pages, records and reports happens through the Home search (search.js). */
(function () {
    const BASE = '/sltalk';

    if (typeof window.logout !== 'function') {
        window.logout = async function () {
            try { await fetch(BASE + '/api/logout', {method: 'POST'}); } finally { window.location.href = BASE + '/login?logout=true'; }
        };
    }

    // Display titles for the technical role codes (presentation only; authorization still uses the codes).
    const ROLE_LABELS = {ADMIN: 'Administrator', BUSINESS: 'Business Manager', MANAGER: 'Store Manager', CUSTOMER: 'Customer Analyst'};
    const roleLabel = code => ROLE_LABELS[code] || String(code || '').charAt(0) + String(code || '').slice(1).toLowerCase();
    window.dtRoleLabel = roleLabel;
    const initials = name => String(name).trim().split(/\s+/).slice(0, 2).map(w => w.charAt(0).toUpperCase()).join('');
    const setText = (id, text) => { const el = document.getElementById(id); if (el) el.textContent = text; };

    fetch(BASE + '/api/user-info').then(r => r.json()).then(b => {
        if (!b.success) return;
        const name = b.data.fullName || b.data.username;
        const role = roleLabel(b.data.role);
        setText('userDisplay', name);
        setText('userRoleLabel', role);
        setText('userMenuName', name);
        setText('userRole', role);
        setText('userOrg', b.data.customerName || 'All customers');
        const avatar = document.getElementById('userAvatar');
        if (avatar && initials(name)) avatar.textContent = initials(name);
        const chip = document.getElementById('userDropdown');
        if (chip) chip.title = name + ' - ' + role;
    }).catch(() => {});
})();
