// cookieRefresh 业务逻辑（提供给 /cookieRefresh/index.html 使用，CSP 严格模式下需外联）
import { detectLang, loadDict, applyDom, makeT } from './i18n.js';
import { initTheme } from './theme.js';

let t = (k) => k;

const STATUS_ICONS = {
    info:    'icon-info',
    warn:    'icon-warn',
    error:   'icon-error',
    success: 'icon-check',
};

function $(id) { return document.getElementById(id); }
function getQuery(name) { return new URLSearchParams(window.location.search).get(name); }

function setStatus(kind, key, params) {
    const bar = $('statusbar');
    if (!bar) return;
    bar.className = 'statusbar statusbar--' + kind;
    const iconId = STATUS_ICONS[kind] || STATUS_ICONS.info;
    bar.textContent = '';
    const svgNS = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(svgNS, 'svg');
    svg.setAttribute('class', 'statusbar__icon');
    const use = document.createElementNS(svgNS, 'use');
    use.setAttribute('href', '#' + iconId);
    svg.appendChild(use);
    bar.appendChild(svg);
    const span = document.createElement('span');
    span.className = 'statusbar__text';
    span.textContent = t(key, params);
    bar.appendChild(span);
}

function showRetry(onRetry) {
    const btn = $('btn-retry');
    btn.hidden = false;
    btn.onclick = () => { btn.hidden = true; onRetry(); };
}

function hexEncode(str) {
    let s = '';
    for (let i = 0; i < str.length; i++) s += str.charCodeAt(i).toString(16);
    return s;
}

function tryClose(successKey) {
    let seconds = 5;
    setStatus('success', successKey, { seconds });
    const timer = setInterval(() => {
        seconds--;
        if (seconds <= 0) {
            clearInterval(timer);
            try { window.open('', '_self'); window.close(); } catch (_) { /* ignore */ }
            setStatus('success', 'tip.refresh.successManualClose');
            $('btn-close').hidden = false;
        } else {
            setStatus('success', successKey, { seconds });
        }
    }, 1000);
}

async function postRefresh(sufix) {
    try {
        const body = new URLSearchParams({ sufix });
        const resp = await fetch('/cookieRefresh/refresh_csrf', {
            method: 'POST',
            credentials: 'omit',
            cache: 'no-store',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
            body,
        });
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const data = await resp.json().catch(() => ({}));
        if (data && (data.code === 0 || data.code === '0')) {
            tryClose('tip.refresh.success');
        } else {
            setStatus('error', 'tip.refresh.fail', { message: (data && data.message) || ('code=' + (data && data.code)) });
            showRetry(run);
        }
    } catch (e) {
        setStatus('error', 'tip.refresh.fail', { message: e.message || 'network error' });
        showRetry(run);
    }
}

function run() {
    setStatus('info', 'tip.wasm.loading');
    const ts = getQuery('timestamp');
    const t0 = ts ? Number(ts) : (Date.now() - 3000);
    if (!window.wasmInit || typeof window.wasmInit.default !== 'function') {
        setStatus('error', 'tip.refresh.fail', { message: 'wasm not loaded' });
        showRetry(run);
        return;
    }
    window.wasmInit.default().then(() => {
        setStatus('info', 'tip.wasm.ready');
        const data = hexEncode('refresh_' + t0);
        const sufix = window.wasmInit.encrypt({ data, digest: 'SHA256' });
        postRefresh(sufix);
    }).catch((e) => {
        setStatus('error', 'tip.refresh.fail', { message: (e && e.message) || 'wasm init failed' });
        showRetry(run);
    });
}

(async function bootstrap() {
    initTheme('#theme-switch');
    try {
        const dict = await loadDict(detectLang());
        t = makeT(dict);
        applyDom(dict);
    } catch (_) { /* keep keys */ }
    $('btn-close').addEventListener('click', () => {
        try { window.open('', '_self'); window.close(); } catch (_) { /* ignore */ }
        window.location.href = 'about:blank';
    });
    run();
})();
