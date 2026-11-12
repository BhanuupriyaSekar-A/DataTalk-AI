/* DataTalk AI - chat page. All data comes from the backend APIs; nothing is faked. */
const API = '/datatalk/api';
const charts = {};

// ---------- helpers ----------
function esc(v) {
    return String(v ?? '').replace(/[&<>"']/g, c => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
}

async function api(path, options = {}) {
    const res = await fetch(API + path, {headers: {'Content-Type': 'application/json'}, ...options});
    if (res.status === 401) {
        window.location.href = '/datatalk/login';
        throw new Error('Session expired');
    }
    let body = null;
    try { body = await res.json(); } catch (e) { /* non-JSON */ }
    if (!res.ok || !body || body.success === false) {
        throw new Error((body && (body.message || body.error)) || ('Request failed (HTTP ' + res.status + ')'));
    }
    return body;
}

function filters() {
    return {
        dateFrom: document.getElementById('filterFrom').value || null,
        dateTo: document.getElementById('filterTo').value || null,
        status: document.getElementById('filterStatus').value || null
    };
}

function filterQuery() {
    const f = filters();
    const p = new URLSearchParams();
    Object.entries(f).forEach(([k, v]) => { if (v) p.set(k, v); });
    return p.toString();
}

function resetFilters() {
    ['filterFrom', 'filterTo', 'filterStatus'].forEach(id => document.getElementById(id).value = '');
}

function scrollBottom() {
    const box = document.getElementById('chatMessages');
    box.scrollTop = box.scrollHeight;
}

function formatCell(v, type) {
    if (v === null || v === undefined) return '<span class="text-muted">-</span>';
    if (type === 'number' && typeof v === 'number') {
        return Number.isInteger(v) ? v.toLocaleString() : v.toLocaleString(undefined, {maximumFractionDigits: 2});
    }
    if (type === 'number' && !isNaN(Number(v))) {
        return Number(v).toLocaleString(undefined, {maximumFractionDigits: 2});
    }
    return esc(v);
}

// ---------- rendering ----------
function addUserMessage(text) {
    const div = document.createElement('div');
    div.className = 'msg-user p-2 px-3 mb-3';
    div.textContent = text;
    document.getElementById('chatMessages').appendChild(div);
    scrollBottom();
}

function addLoading() {
    const div = document.createElement('div');
    div.className = 'msg-ai p-3 mb-3';
    div.innerHTML = '<div class="d-flex align-items-center"><div class="spinner-border spinner-border-sm text-primary me-2"></div>'
        + '<span>Thinking... generating and validating a secure query (this can take up to a minute; a fallback AI model is used if the primary one is slow).</span></div>';
    document.getElementById('chatMessages').appendChild(div);
    scrollBottom();
    return div;
}

function sqlDetails(sql) {
    if (!sql) return '';
    return `<details class="mt-2"><summary class="small text-muted">SQL details</summary><pre class="sql mt-2 mb-0">${esc(sql)}</pre></details>`;
}

/** Which AI provider/model produced this answer (or CACHE / PREDEFINED when no AI call was made). */
function modelBadge(r) {
    if (r.source === 'CACHE') return '<span class="badge bg-secondary"><i class="fas fa-database"></i> Cached SQL - no AI call</span>';
    if (r.source === 'PREDEFINED') return '<span class="badge bg-secondary"><i class="fas fa-book"></i> Predefined report - no AI call</span>';
    if (!r.aiProvider || r.aiProvider === 'none') return '';
    const fallback = r.aiFailures && r.aiFailures.length;
    const title = fallback ? 'Fallback used. Failed first: ' + r.aiFailures.join(' | ') : 'Primary AI provider';
    return `<span class="badge ${fallback ? 'bg-warning text-dark' : 'bg-info text-dark'}" title="${esc(title)}">`
        + `<i class="fas fa-microchip"></i> ${r.status === 'AI_ERROR' ? 'Tried' : 'Answered by'} ${esc(r.aiProvider)}${fallback ? ' (fallback)' : ''}</span>`;
}

async function loadAiInfo() {
    const box = document.getElementById('aiModelInfo');
    try {
        const body = await api('/chat/ai-info');
        const active = body.data.filter(p => p.configured);
        if (!active.length) {
            box.innerHTML = '<i class="fas fa-triangle-exclamation"></i> No AI provider configured (set NVIDIA_API_KEY / GEMINI_API_KEY)';
            return;
        }
        box.innerHTML = '<i class="fas fa-microchip"></i> AI: ' + active.map((p, i) =>
            `<span class="badge ${i === 0 ? 'bg-light text-dark' : 'bg-primary border border-light'}" title="${esc(p.role)}">`
            + `${i === 0 ? 'Primary' : 'Fallback ' + i}: ${esc(p.provider)} / ${esc(p.model)}</span>`).join(' ');
    } catch (e) {
        box.textContent = 'AI model info unavailable';
    }
}

function renderAnswer(container, r) {
    if (r.status !== 'SUCCESS') {
        const cls = r.status === 'NOT_ANSWERABLE' ? 'alert-info' : 'alert-warning';
        const icon = r.status === 'NOT_ANSWERABLE' ? 'fa-circle-question' : 'fa-triangle-exclamation';
        container.innerHTML = `<div class="alert ${cls} mb-0"><i class="fas ${icon}"></i> <strong>${esc(r.status.replace('_', ' '))}</strong><br>${esc(r.message)}`
            + (r.understanding ? `<div class="small mt-1 text-muted">Understood as: ${esc(r.understanding)}</div>` : '')
            + `<div class="mt-1">${modelBadge(r)}</div>`
            + sqlDetails(r.sql) + '</div>';
        return;
    }
    const id = r.chatId;
    container.dataset.chatId = id;
    container.innerHTML = `
        <div class="d-flex justify-content-between align-items-start flex-wrap gap-2">
            <div>
                <h6 class="mb-1"><i class="fas fa-chart-simple text-primary"></i> ${esc(r.title)}</h6>
                <div class="small">${esc(r.explanation || '')}</div>
                ${r.understanding && r.understanding !== r.explanation ? `<div class="small text-muted">Understood as: ${esc(r.understanding)}</div>` : ''}
            </div>
            <div class="btn-group btn-group-sm">
                <button class="btn btn-outline-primary" onclick="applyFilters(${id})" title="Re-run with the current filters"><i class="fas fa-filter"></i> Apply filters</button>
                <button class="btn btn-outline-danger" onclick="exportReport(${id}, 'pdf', this)"><i class="fas fa-file-pdf"></i> PDF</button>
                <button class="btn btn-outline-success" onclick="exportReport(${id}, 'excel', this)"><i class="fas fa-file-excel"></i> Excel</button>
            </div>
        </div>
        <div class="mt-1">${modelBadge(r)}</div>
        <div class="small text-muted mt-1" id="meta-${id}"></div>
        <div class="chart-box mt-2 ${r.chartType === 'table' ? 'd-none' : ''}" id="chartwrap-${id}"><canvas id="chart-${id}"></canvas></div>
        <div class="table-responsive mt-2" id="table-${id}"></div>
        <div id="pager-${id}"></div>
        ${sqlDetails(r.sql)}`;
    container.dataset.chartType = r.chartType;
    container.dataset.source = r.source;
    container.dataset.duration = r.durationMs;
    renderPage(container, r.page);
}

function renderPage(container, page) {
    const id = container.dataset.chatId;
    const meta = document.getElementById('meta-' + id);
    const source = container.dataset.source === 'CACHE' ? 'reused earlier SQL (no AI call)'
        : container.dataset.source === 'PREDEFINED' ? 'predefined report' : 'AI generated';
    meta.textContent = `${page.totalRows.toLocaleString()} row(s) - ${source}` + (container.dataset.duration ? ` - ${container.dataset.duration} ms` : '');

    const table = document.getElementById('table-' + id);
    if (page.totalRows === 0) {
        table.innerHTML = '<div class="alert alert-light border small mb-0">No records match this question for your data scope and filters.</div>';
        document.getElementById('chartwrap-' + id).classList.add('d-none');
        document.getElementById('pager-' + id).innerHTML = '';
        return;
    }
    let html = '<table class="table table-sm table-striped table-hover report-table mb-1"><thead class="table-light"><tr>';
    page.columns.forEach((c, i) => html += `<th class="${page.columnTypes[i] === 'number' ? 'text-end' : ''}">${esc(c)}</th>`);
    html += '</tr></thead><tbody>';
    page.rows.forEach(row => {
        html += '<tr>' + row.map((v, i) => `<td class="${page.columnTypes[i] === 'number' ? 'num' : ''}">${formatCell(v, page.columnTypes[i])}</td>`).join('') + '</tr>';
    });
    html += '</tbody></table>';
    table.innerHTML = html;

    const pager = document.getElementById('pager-' + id);
    if (page.totalPages > 1) {
        pager.innerHTML = `<div class="d-flex align-items-center gap-2 small">
            <button class="btn btn-sm btn-outline-secondary" ${page.page <= 1 ? 'disabled' : ''} onclick="loadPage(${id}, ${page.page - 1})">&laquo; Prev</button>
            <span>Page ${page.page} of ${page.totalPages}</span>
            <button class="btn btn-sm btn-outline-secondary" ${page.page >= page.totalPages ? 'disabled' : ''} onclick="loadPage(${id}, ${page.page + 1})">Next &raquo;</button>
            <select class="form-select form-select-sm w-auto ms-2" onchange="loadPage(${id}, 1, this.value)">
                ${[10, 25, 50, 100].map(s => `<option ${s === page.pageSize ? 'selected' : ''}>${s}</option>`).join('')}
            </select><span class="text-muted">rows per page</span></div>`;
    } else {
        pager.innerHTML = '';
    }
    container.dataset.pageSize = page.pageSize;
    renderChart(container, page);
}

function renderChart(container, page) {
    const id = container.dataset.chatId;
    const type = container.dataset.chartType;
    const wrap = document.getElementById('chartwrap-' + id);
    if (charts[id]) { charts[id].destroy(); delete charts[id]; }
    const numIdx = page.columnTypes.map((t, i) => t === 'number' ? i : -1).filter(i => i >= 0);
    let labelIdx = page.columnTypes.findIndex(t => t !== 'number');
    if (type === 'table' || typeof Chart === 'undefined' || numIdx.length === 0 || page.rows.length < 1) {
        wrap.classList.add('d-none');
        return;
    }
    if (labelIdx < 0) labelIdx = 0;
    const valueCols = (type === 'pie' ? numIdx.filter(i => i !== labelIdx).slice(0, 1) : numIdx.filter(i => i !== labelIdx).slice(0, 3));
    if (valueCols.length === 0) { wrap.classList.add('d-none'); return; }
    wrap.classList.remove('d-none');
    const rows = type === 'pie' ? page.rows.slice(0, 12) : page.rows;
    const labels = rows.map(r => {
        const parts = page.columnTypes.map((t, i) => t !== 'number' ? r[i] : null).filter(v => v !== null);
        return parts.length ? parts.join(' / ') : String(r[labelIdx]);
    });
    const palette = ['#0d6efd', '#dc3545', '#198754', '#ffc107', '#6f42c1', '#20c997', '#fd7e14', '#0dcaf0', '#6c757d', '#d63384', '#6610f2', '#adb5bd'];
    charts[id] = new Chart(document.getElementById('chart-' + id), {
        type: type === 'pie' ? 'pie' : type,
        data: {
            labels,
            datasets: valueCols.map((c, n) => ({
                label: page.columns[c],
                data: rows.map(r => r[c] === null ? null : Number(r[c])),
                backgroundColor: type === 'pie' ? palette : palette[n],
                borderColor: type === 'pie' ? '#fff' : palette[n],
                fill: false, tension: 0.2
            }))
        },
        options: {
            responsive: true, maintainAspectRatio: false,
            plugins: {legend: {display: type === 'pie' || valueCols.length > 1}},
            scales: type === 'pie' ? {} : {y: {beginAtZero: true}}
        }
    });
}

// ---------- actions ----------
async function ask(question) {
    addUserMessage(question);
    const loading = addLoading();
    setBusy(true);
    try {
        const f = filters();
        const body = await api('/chat/ask', {method: 'POST', body: JSON.stringify({question, ...f})});
        renderAnswer(loading, body.data);
    } catch (e) {
        loading.innerHTML = `<div class="alert alert-danger mb-0"><i class="fas fa-circle-xmark"></i> ${esc(e.message)}</div>`;
    } finally {
        setBusy(false);
        scrollBottom();
        loadHistory();
    }
}

async function runReport(reportId, name) {
    addUserMessage('Run report: ' + name);
    const loading = addLoading();
    setBusy(true);
    try {
        const body = await api(`/reports/${encodeURIComponent(reportId)}/run`, {method: 'POST', body: JSON.stringify({params: {}, ...filters()})});
        renderAnswer(loading, body.data);
    } catch (e) {
        loading.innerHTML = `<div class="alert alert-danger mb-0"><i class="fas fa-circle-xmark"></i> ${esc(e.message)}</div>`;
    } finally {
        setBusy(false);
        scrollBottom();
        loadHistory();
    }
}

async function loadPage(chatId, page, pageSize) {
    const container = document.querySelector(`[data-chat-id="${chatId}"]`);
    const size = pageSize || container.dataset.pageSize || 25;
    const table = document.getElementById('table-' + chatId);
    table.style.opacity = 0.5;
    try {
        const body = await api(`/chat/${chatId}/results?page=${page}&pageSize=${size}&${container.dataset.filterQuery || ''}`);
        renderPage(container, body.data);
    } catch (e) {
        table.insertAdjacentHTML('afterbegin', `<div class="alert alert-danger small py-1">${esc(e.message)}</div>`);
    } finally {
        table.style.opacity = 1;
    }
}

function applyFilters(chatId) {
    const container = document.querySelector(`[data-chat-id="${chatId}"]`);
    container.dataset.filterQuery = filterQuery();
    loadPage(chatId, 1);
}

async function exportReport(chatId, format, btn) {
    const container = document.querySelector(`[data-chat-id="${chatId}"]`);
    const q = container.dataset.filterQuery || '';
    const original = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span>';
    try {
        const res = await fetch(`${API}/chat/${chatId}/export?format=${format}&${q}`);
        if (res.status === 401) { window.location.href = '/datatalk/login'; return; }
        if (res.status === 202) {
            const body = await res.json();
            btn.innerHTML = `<span class="spinner-border spinner-border-sm"></span> ${body.data.rows.toLocaleString()} rows...`;
            await pollExport(body.data.jobId);
        } else if (res.ok) {
            const blob = await res.blob();
            const cd = res.headers.get('Content-Disposition') || '';
            const name = (cd.match(/filename="?([^"]+)"?/) || [])[1] || `report.${format === 'pdf' ? 'pdf' : 'xlsx'}`;
            const a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = name;
            a.click();
            setTimeout(() => URL.revokeObjectURL(a.href), 5000);
        } else {
            let msg = 'Export failed';
            try { msg = (await res.json()).message || msg; } catch (e) { /* ignore */ }
            alert(msg);
        }
    } catch (e) {
        alert('Export failed: ' + e.message);
    } finally {
        btn.disabled = false;
        btn.innerHTML = original;
    }
}

async function pollExport(jobId) {
    for (let i = 0; i < 300; i++) {
        await new Promise(r => setTimeout(r, 2000));
        const body = await api(`/exports/${jobId}`);
        if (body.data.status === 'COMPLETED') {
            window.location.href = `${API}/exports/${jobId}/download`;
            return;
        }
        if (body.data.status === 'FAILED') {
            throw new Error(body.data.error || 'Export failed');
        }
    }
    throw new Error('Export is taking too long; please try again later.');
}

async function clearConversation() {
    if (!confirm('Start a new conversation? The current messages will be cleared from the screen (history is kept).')) return;
    try {
        await api('/chat/conversation/new', {method: 'POST'});
        Object.values(charts).forEach(c => c.destroy());
        const box = document.getElementById('chatMessages');
        const welcome = document.getElementById('welcome');
        box.innerHTML = '';
        box.appendChild(welcome);
    } catch (e) {
        alert(e.message);
    }
}

function setBusy(busy) {
    document.getElementById('sendBtn').disabled = busy;
    document.getElementById('micBtn').disabled = busy;
    document.getElementById('questionInput').disabled = busy;
    document.getElementById('loadingText').textContent = busy ? 'Working on your report...' : '';
}

// ---------- sidebar ----------
async function loadReports() {
    const list = document.getElementById('reportList');
    try {
        const body = await api('/reports');
        if (!body.data.length) {
            list.innerHTML = '<div class="p-3 small text-muted">No reports available for your role.</div>';
            return;
        }
        const groups = {};
        body.data.forEach(r => (groups[r.category] = groups[r.category] || []).push(r));
        list.innerHTML = Object.entries(groups).map(([cat, reports]) =>
            `<div class="px-3 pt-2 small fw-bold text-uppercase text-muted">${esc(cat)}</div><div class="list-group list-group-flush">`
            + reports.map(r => `<a class="list-group-item list-group-item-action" data-id="${esc(r.id)}" data-name="${esc(r.name)}" title="${esc(r.description)}">${esc(r.name)}</a>`).join('')
            + '</div>').join('');
        list.querySelectorAll('[data-id]').forEach(a => a.addEventListener('click', () => runReport(a.dataset.id, a.dataset.name)));
    } catch (e) {
        list.innerHTML = `<div class="p-3 small text-danger">${esc(e.message)}</div>`;
    }
}

async function loadHistory() {
    const list = document.getElementById('historyList');
    try {
        const body = await api('/chat/history?limit=15');
        if (!body.data.length) {
            list.innerHTML = '<div class="p-3 small text-muted">No questions yet.</div>';
            return;
        }
        list.innerHTML = body.data.map(h => {
            const ok = h.queryStatus === 'EXECUTED';
            return `<a class="list-group-item list-group-item-action history-item" data-q="${esc(h.question)}">
                <i class="fas ${ok ? 'fa-circle-check text-success' : 'fa-circle-exclamation text-warning'}"></i>
                ${esc(h.question.length > 70 ? h.question.substring(0, 70) + '...' : h.question)}
                <div class="text-muted">${esc((h.createdAt || '').replace('T', ' ').substring(0, 16))}${ok && h.rowCount !== null ? ' - ' + h.rowCount + ' rows' : ''}${h.aiProvider ? ' - ' + esc(h.aiProvider) : ''}</div></a>`;
        }).join('');
        list.querySelectorAll('[data-q]').forEach(a => a.addEventListener('click', () => {
            const input = document.getElementById('questionInput');
            input.value = a.dataset.q;
            input.focus();
            updateCount();
        }));
    } catch (e) {
        list.innerHTML = `<div class="p-3 small text-danger">${esc(e.message)}</div>`;
    }
}

function updateCount() {
    document.getElementById('charCount').textContent = document.getElementById('questionInput').value.length;
}

// ---------- voice input (browser Web Speech API) ----------
let recognition = null;
let listening = false;
const LISTENING_MSG = 'Listening... speak now, click the stop button when done.';
const VOICE_ERRORS = {
    'not-allowed': 'Microphone permission denied. Allow microphone access in your browser settings.',
    'service-not-allowed': 'Microphone permission denied. Allow microphone access in your browser settings.',
    'audio-capture': 'No microphone found.',
    'no-speech': 'No speech detected. Please try again.',
    'network': 'Voice recognition needs an internet connection.',
    'aborted': ''
};

function setMicState(on) {
    listening = on;
    const btn = document.getElementById('micBtn');
    btn.classList.toggle('btn-danger', on);
    btn.classList.toggle('btn-outline-secondary', !on);
    btn.innerHTML = on ? '<i class="fas fa-stop fa-beat"></i>' : '<i class="fas fa-microphone"></i>';
    btn.title = on ? 'Stop listening' : 'Voice input';
}

function initVoice() {
    const btn = document.getElementById('micBtn');
    const status = document.getElementById('loadingText');
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
        btn.title = 'Voice input is not supported in this browser';
        btn.addEventListener('click', () => status.textContent = 'Voice input is not supported in this browser. Try Chrome or Edge.');
        return;
    }
    const input = document.getElementById('questionInput');
    let base = '', error = '';
    recognition = new SR();
    recognition.lang = navigator.language || 'en-US';
    recognition.interimResults = true;
    recognition.continuous = true;
    recognition.onstart = () => {
        error = '';
        base = input.value.trim();
        setMicState(true);
        status.textContent = LISTENING_MSG;
    };
    recognition.onresult = ev => {
        const text = Array.from(ev.results).map(r => r[0].transcript).join('').trim();
        input.value = ((base ? base + ' ' : '') + text).substring(0, input.maxLength);
        updateCount();
    };
    recognition.onerror = ev => error = ev.error in VOICE_ERRORS ? VOICE_ERRORS[ev.error] : 'Voice input error: ' + ev.error;
    recognition.onend = () => {
        setMicState(false);
        if (error) status.textContent = error;
        else if (status.textContent === LISTENING_MSG) status.textContent = '';
        if (!input.disabled) input.focus();
    };
    btn.addEventListener('click', () => {
        if (listening) recognition.stop();
        else recognition.start();
    });
}

async function logout() {
    if (confirm('Are you sure you want to logout?')) {
        try { await fetch(API + '/logout', {method: 'POST'}); } finally { window.location.href = '/datatalk/login?logout=true'; }
    }
}

// ---------- init ----------
window.addEventListener('DOMContentLoaded', async () => {
    try {
        const body = await api('/user-info');
        document.getElementById('userDisplay').textContent = body.data.username;
        document.getElementById('userRole').textContent = body.data.role + (body.data.customerName ? ' - ' + body.data.customerName : '');
        if (body.data.role === 'ADMIN') document.getElementById('analyticsNav').classList.remove('d-none');
    } catch (e) {
        window.location.href = '/datatalk/login';
        return;
    }
    const input = document.getElementById('questionInput');
    document.getElementById('askForm').addEventListener('submit', ev => {
        ev.preventDefault();
        const q = input.value.trim();
        if (!q) return;
        if (listening) recognition.abort();
        input.value = '';
        updateCount();
        ask(q);
    });
    input.addEventListener('keydown', ev => {
        if (ev.key === 'Enter' && !ev.shiftKey) {
            ev.preventDefault();
            document.getElementById('askForm').requestSubmit();
        }
    });
    input.addEventListener('input', updateCount);
    document.querySelectorAll('.example-q').forEach(a => a.addEventListener('click', ev => {
        ev.preventDefault();
        ask(a.textContent);
    }));
    // Opened from Global Search: run that predefined report (no AI call)
    const params = new URLSearchParams(window.location.search);
    if (params.get('report')) {
        history.replaceState(null, '', window.location.pathname);
        runReport(params.get('report'), params.get('name') || params.get('report'));
    }
    initVoice();
    loadAiInfo();
    loadReports();
    loadHistory();
});
