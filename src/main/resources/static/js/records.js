/* DataTalk AI - Customers, Devices, Locations and Transactions pages: list, read-only details, create/edit forms.
   ?id=N shows the read-only details; editing only happens after clicking Edit. */
const BASE = '/datatalk';
const API = BASE + '/api';
const PAGE = document.body.dataset.page;
let isAdmin = false;
let formModal;
let formSubmit = null;

// ---------- helpers ----------
function esc(v) {
    return String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
}

async function api(path, options = {}) {
    const res = await fetch(API + path, {headers: {'Content-Type': 'application/json'}, ...options});
    if (res.status === 401) {
        window.location.href = BASE + '/login';
        throw new Error('Session expired');
    }
    let body = null;
    try { body = await res.json(); } catch (e) { /* non-JSON */ }
    if (!res.ok || !body || body.success === false) {
        throw new Error((body && (body.message || body.error)) || ('Request failed (HTTP ' + res.status + ')'));
    }
    return body;
}

const dash = '<span class="text-muted">-</span>';
const text = v => (v === null || v === undefined || v === '') ? dash : esc(v);
const when = v => v ? esc(String(v).replace('T', ' ').substring(0, 16)) : dash;
const day = v => v ? esc(String(v).substring(0, 10)) : dash;
const money = v => v === null || v === undefined ? dash : esc(Number(v).toLocaleString(undefined, {minimumFractionDigits: 2, maximumFractionDigits: 2}));
const badge = v => v ? `<span class="badge ${['ACTIVE', 'APPROVED', 'COMPLETED'].includes(v) ? 'bg-success'
    : ['ERROR', 'DECLINED', 'FAILED', 'SUSPENDED'].includes(v) ? 'bg-danger' : 'bg-secondary'}">${esc(v)}</span>` : dash;
const link = (page, id, label) => id === null || id === undefined ? text(label)
    : `<a href="${BASE}/${page}?id=${encodeURIComponent(id)}">${esc(label ?? id)}</a>`;
const customerLink = (id, name) => isAdmin ? link('customers', id, name) : text(name);

function showAlert(id, message, type) {
    const el = document.getElementById(id);
    el.className = 'alert alert-' + type + (message ? '' : ' d-none');
    el.textContent = message || '';
}

function table(columns, rows, empty) {
    if (!rows || !rows.length) return `<p class="text-muted small m-3">${esc(empty)}</p>`;
    return '<div class="table-responsive"><table class="table table-sm mb-0"><thead><tr>'
        + columns.map(c => `<th>${esc(c[0])}</th>`).join('') + '</tr></thead><tbody>'
        + rows.map(r => '<tr>' + columns.map(c => `<td>${c[1](r)}</td>`).join('') + '</tr>').join('')
        + '</tbody></table></div>';
}

function section(title, html) {
    return `<div class="card mb-3"><div class="card-header">${esc(title)}</div><div class="card-body p-0">${html}</div></div>`;
}

const TXN_COLUMNS = [
    ['ID', t => link('transactions', t.transaction_id)], ['Time', t => when(t.transaction_time)],
    ['Amount', t => money(t.amount)], ['Status', t => badge(t.transaction_status)], ['Card', t => text(t.card_type)],
    ['Device', t => link('devices', t.device_id, t.serial_number)]
];

// ---------- page definitions ----------
const PAGES = {
    customers: {
        filter: 'Filter by name, email or ID',
        list: q => '/admin/customers?q=' + encodeURIComponent(q),
        id: r => r.customerId,
        columns: [['ID', r => text(r.customerId)], ['Name', r => text(r.customerName)], ['Email', r => text(r.email)],
            ['Phone', r => text(r.phone)], ['Status', r => badge(r.status)], ['Created', r => day(r.createdDate)]],
        detail: id => '/admin/customers/' + id,
        render: d => {
            const c = d.customer, s = d.summary || {};
            return {
                title: c.customerName,
                fields: [['Customer ID', text(c.customerId)], ['Name', text(c.customerName)], ['Email', text(c.email)],
                    ['Phone', text(c.phone)], ['Status', badge(c.status)], ['Created', when(c.createdDate)]],
                sections: section('Summary', table([['Devices', r => text(r.deviceCount)], ['Active devices', r => text(r.activeDevices)],
                    ['Locations', r => text(r.locationCount)], ['Transactions (30d)', r => text(r.recentTransactions)],
                    ['Approved amount (30d)', r => money(r.approvedAmount)]], [s], 'No summary available.')),
                actions: `<button class="btn btn-sm btn-primary" id="editBtn"><i class="fas fa-pen"></i> Edit</button>`,
                bind: () => document.getElementById('editBtn').addEventListener('click', () => openForm('customers', c))
            };
        },
        createLabel: '<i class="fas fa-user-plus"></i> Add Customer'
    },
    devices: {
        filter: 'Filter by serial number, ID or type',
        list: q => '/devices?q=' + encodeURIComponent(q),
        id: r => r.device_id,
        columns: [['ID', r => text(r.device_id)], ['Serial number', r => text(r.serial_number)], ['Type', r => text(r.device_type)],
            ['Status', r => badge(r.status)], ['Location', r => text(r.location_name)], ['Customer', r => text(r.customer_name)],
            ['Last communication', r => when(r.last_communication_time)]],
        detail: id => '/devices/' + id,
        render: d => {
            const v = d.device;
            return {
                title: 'Device ' + v.serial_number,
                fields: [['Device ID', text(v.device_id)], ['Serial number', text(v.serial_number)], ['Type', text(v.device_type)],
                    ['Status', badge(v.status)], ['Firmware', text(v.firmware_version)], ['Activated', when(v.activated_date)],
                    ['Last communication', when(v.last_communication_time)],
                    ['Location', link('locations', v.location_id, v.location_name + (v.city ? ' (' + v.city + ')' : ''))],
                    ['Customer', customerLink(v.customer_id, v.customer_name)]],
                sections: (d.transactions ? section('Recent transactions', table(TXN_COLUMNS, d.transactions, 'No transactions.')) : '')
                    + (d.events ? section('Status events', table([['Time', e => when(e.event_time)], ['Event', e => text(e.event_type)],
                        ['Description', e => text(e.description)]], d.events, 'No status events.')) : '')
            };
        },
        createLabel: '<i class="fas fa-plus"></i> Add Device'
    },
    locations: {
        filter: 'Filter by name, city or ID',
        list: q => '/locations?q=' + encodeURIComponent(q),
        id: r => r.location_id,
        columns: [['ID', r => text(r.location_id)], ['Name', r => text(r.location_name)], ['City', r => text(r.city)],
            ['State', r => text(r.state)], ['Timezone', r => text(r.timezone)], ['Customer', r => text(r.customer_name)]],
        detail: id => '/locations/' + id,
        render: d => {
            const l = d.location;
            return {
                title: l.location_name,
                fields: [['Location ID', text(l.location_id)], ['Name', text(l.location_name)], ['City', text(l.city)],
                    ['State', text(l.state)], ['Timezone', text(l.timezone)], ['Created', when(l.created_date)],
                    ['Customer', customerLink(l.customer_id, l.customer_name)]],
                sections: (d.devices ? section('Devices', table([['ID', v => link('devices', v.device_id)],
                        ['Serial number', v => link('devices', v.device_id, v.serial_number)], ['Type', v => text(v.device_type)],
                        ['Status', v => badge(v.status)]], d.devices, 'No devices at this location.')) : '')
                    + (d.transactions ? section('Recent transactions', table(TXN_COLUMNS, d.transactions, 'No transactions.')) : '')
            };
        },
        createLabel: '<i class="fas fa-map-pin"></i> Create Location'
    },
    transactions: {
        filter: 'Filter by transaction ID, device serial or status',
        list: q => '/transactions?q=' + encodeURIComponent(q),
        id: r => r.transaction_id,
        columns: [['ID', r => text(r.transaction_id)], ['Time', r => when(r.transaction_time)], ['Amount', r => money(r.amount)],
            ['Status', r => badge(r.transaction_status)], ['Card', r => text(r.card_type)], ['Entry', r => text(r.entry_mode)],
            ['Device', r => text(r.serial_number)], ['Location', r => text(r.location_name)]],
        detail: id => '/transactions/' + id,
        render: t => ({
            title: 'Transaction #' + t.transaction_id,
            fields: [['Transaction ID', text(t.transaction_id)], ['Time', when(t.transaction_time)], ['Amount', money(t.amount)],
                ['Status', badge(t.transaction_status)], ['Decline reason', text(t.decline_reason)], ['Card type', text(t.card_type)],
                ['Entry mode', text(t.entry_mode)], ['Device', link('devices', t.device_id, t.serial_number)],
                ['Location', link('locations', t.location_id, t.location_name)], ['Customer', customerLink(t.customer_id, t.customer_name)]],
            sections: ''
        })
    }
};

// ---------- forms (ADMIN) ----------
const opt = (values, selected) => values.map(v => `<option${v === selected ? ' selected' : ''}>${v}</option>`).join('');
const input = (id, label, value, attrs = '') =>
    `<div class="mb-3"><label for="${id}" class="form-label">${label}</label><input class="form-control" id="${id}" value="${esc(value)}" ${attrs}></div>`;
const select = (id, label, options, attrs = '') =>
    `<div class="mb-3"><label for="${id}" class="form-label">${label}</label><select class="form-select" id="${id}" ${attrs}>${options}</select></div>`;
const val = id => document.getElementById(id).value.trim();

async function customerOptions() {
    const list = (await api('/admin/customers')).data;
    return '<option value="">Select a customer...</option>'
        + list.map(c => `<option value="${c.customerId}">${esc(c.customerName)} (#${c.customerId})</option>`).join('');
}

const FORMS = {
    customers: {
        title: c => c ? 'Edit Customer #' + c.customerId : 'Add Customer',
        fields: async c => input('fName', 'Customer name *', c?.customerName, 'maxlength="255" required')
            + input('fEmail', 'Email *', c?.email, 'type="email" maxlength="255" required')
            + input('fPhone', 'Phone', c?.phone, 'maxlength="20"')
            + select('fStatus', 'Status', opt(['ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED'], c?.status || 'ACTIVE')),
        save: c => api(c ? '/admin/customers/' + c.customerId : '/admin/customers', {
            method: c ? 'PUT' : 'POST',
            body: JSON.stringify({customerName: val('fName'), email: val('fEmail'), phone: val('fPhone') || null, status: val('fStatus')})
        }),
        idOf: d => d.customerId
    },
    devices: {
        title: () => 'Add Device',
        fields: async () => select('fCustomer', 'Customer *', await customerOptions(), 'required')
            + select('fLocation', 'Location *', '<option value="">Select a customer first</option>', 'required')
            + input('fSerial', 'Serial number *', '', 'maxlength="100" required')
            + select('fType', 'Device type', opt(['POS_TERMINAL', 'MOBILE_READER', 'PAYMENT_GATEWAY', 'ATM'], 'POS_TERMINAL'))
            + input('fFirmware', 'Firmware version', '', 'maxlength="50"')
            + select('fStatus', 'Status', opt(['INACTIVE', 'ACTIVE', 'MAINTENANCE'], 'INACTIVE')),
        bind: () => document.getElementById('fCustomer').addEventListener('change', async ev => {
            const loc = document.getElementById('fLocation');
            loc.innerHTML = '<option value="">Loading...</option>';
            if (!ev.target.value) { loc.innerHTML = '<option value="">Select a customer first</option>'; return; }
            try {
                const list = (await api('/locations?customerId=' + encodeURIComponent(ev.target.value))).data;
                loc.innerHTML = list.length ? list.map(l => `<option value="${l.location_id}">${esc(l.location_name)}</option>`).join('')
                    : '<option value="">This customer has no locations - create one first</option>';
            } catch (e) {
                loc.innerHTML = '<option value="">Could not load locations</option>';
            }
        }),
        save: () => api('/admin/devices', {method: 'POST', body: JSON.stringify({
            customerId: Number(val('fCustomer')) || null, locationId: Number(val('fLocation')) || null,
            serialNumber: val('fSerial'), deviceType: val('fType'), firmwareVersion: val('fFirmware') || null, status: val('fStatus')
        })}),
        idOf: d => d.device_id
    },
    locations: {
        title: () => 'Create Location',
        fields: async () => select('fCustomer', 'Customer *', await customerOptions(), 'required')
            + input('fName', 'Location name *', '', 'maxlength="255" required')
            + input('fCity', 'City', '', 'maxlength="100"')
            + input('fState', 'State', '', 'maxlength="100"')
            + input('fTimezone', 'Timezone', 'UTC', 'maxlength="50" placeholder="e.g. America/New_York"'),
        save: () => api('/admin/locations', {method: 'POST', body: JSON.stringify({
            customerId: Number(val('fCustomer')) || null, locationName: val('fName'), city: val('fCity') || null,
            state: val('fState') || null, timezone: val('fTimezone') || null
        })}),
        idOf: d => d.location_id
    }
};

async function openForm(page, existing) {
    const form = FORMS[page];
    showAlert('formAlert', '', 'danger');
    document.getElementById('formTitle').textContent = form.title(existing);
    try {
        document.getElementById('formFields').innerHTML = await form.fields(existing);
    } catch (e) {
        showAlert('pageAlert', 'Could not open the form: ' + e.message, 'danger');
        return;
    }
    if (form.bind) form.bind();
    formSubmit = async () => {
        const body = await form.save(existing);
        window.location.href = BASE + '/' + page + '?id=' + encodeURIComponent(form.idOf(body.data)) + '&saved=1';
    };
    formModal.show();
}

// ---------- views ----------
async function loadList() {
    const cfg = PAGES[PAGE];
    const body = document.getElementById('listBody');
    try {
        const rows = (await api(cfg.list(document.getElementById('listFilter').value.trim()))).data;
        body.innerHTML = rows.length ? rows.map(r => `<tr data-id="${esc(cfg.id(r))}">`
            + cfg.columns.map(c => `<td>${c[1](r)}</td>`).join('') + '</tr>').join('')
            : `<tr><td colspan="${cfg.columns.length}" class="text-muted p-3">No records found.</td></tr>`;
    } catch (e) {
        body.innerHTML = `<tr><td colspan="${cfg.columns.length}" class="text-danger p-3">${esc(e.message)}</td></tr>`;
    }
}

function showList(params) {
    const cfg = PAGES[PAGE];
    document.getElementById('listView').classList.remove('d-none');
    document.getElementById('listHead').innerHTML = cfg.columns.map(c => `<th>${esc(c[0])}</th>`).join('');
    const filter = document.getElementById('listFilter');
    filter.placeholder = cfg.filter;
    let timer;
    filter.addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(loadList, 300); });
    document.getElementById('listBody').addEventListener('click', ev => {
        const row = ev.target.closest('tr[data-id]');
        if (row && !ev.target.closest('a')) window.location.href = BASE + '/' + PAGE + '?id=' + encodeURIComponent(row.dataset.id);
    });
    const createBtn = document.getElementById('createBtn');
    if (FORMS[PAGE] && cfg.createLabel && isAdmin) {
        createBtn.innerHTML = cfg.createLabel;
        createBtn.classList.remove('d-none');
        createBtn.addEventListener('click', () => openForm(PAGE));
    }
    if (params.get('action') === 'new' && FORMS[PAGE] && isAdmin) {
        openForm(PAGE);
    } else if (params.get('action') === 'edit' && PAGE === 'customers') {
        showAlert('pageAlert', 'Find the customer, open it and click Edit.', 'info');
        filter.focus();
    }
    if (params.has('action')) history.replaceState(null, '', window.location.pathname);
    loadList();
}

async function showDetail(id, params) {
    const cfg = PAGES[PAGE];
    document.getElementById('detailView').classList.remove('d-none');
    document.getElementById('backLink').href = BASE + '/' + PAGE;
    if (params.has('saved')) {
        showAlert('pageAlert', 'Saved successfully.', 'success');
        history.replaceState(null, '', window.location.pathname + '?id=' + encodeURIComponent(id));
    }
    document.getElementById('detailTitle').textContent = 'Loading...';
    try {
        const v = cfg.render((await api(cfg.detail(encodeURIComponent(id)))).data);
        document.getElementById('detailTitle').textContent = v.title;
        document.getElementById('detailFields').innerHTML = v.fields
            .map(([k, html]) => `<dt class="col-sm-4 col-lg-3 text-muted fw-normal">${esc(k)}</dt><dd class="col-sm-8 col-lg-9">${html}</dd>`).join('');
        document.getElementById('detailSections').innerHTML = v.sections || '';
        document.getElementById('detailActions').innerHTML = v.actions || '';
        if (v.bind) v.bind();
    } catch (e) {
        document.getElementById('detailTitle').textContent = 'Not available';
        showAlert('pageAlert', e.message, 'danger');
    }
}

window.addEventListener('DOMContentLoaded', async () => {
    formModal = new bootstrap.Modal(document.getElementById('formModal'));
    document.getElementById('recordForm').addEventListener('submit', async ev => {
        ev.preventDefault();
        const btn = document.getElementById('saveBtn');
        btn.disabled = true;
        try {
            await formSubmit();
        } catch (e) {
            showAlert('formAlert', e.message, 'danger');
        } finally {
            btn.disabled = false;
        }
    });
    try {
        isAdmin = (await api('/user-info')).data.role === 'ADMIN';
    } catch (e) {
        return;
    }
    const params = new URLSearchParams(window.location.search);
    const id = params.get('id');
    if (id && /^\d+$/.test(id)) showDetail(id, params);
    else showList(params);
});
