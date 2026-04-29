// 主题切换：light / dark / auto，持久化到 localStorage('bd_theme')
// 公开 API：initTheme(rootSelector?) 在 DOM ready 后调用即可。

const KEY = 'bd_theme';
const VALID = ['auto', 'light', 'dark'];

function read() {
    try {
        const v = localStorage.getItem(KEY);
        return VALID.includes(v) ? v : 'auto';
    } catch (_) { return 'auto'; }
}

function write(v) {
    try { localStorage.setItem(KEY, v); } catch (_) { /* ignore */ }
}

function apply(theme) {
    document.documentElement.setAttribute('data-theme', theme);
}

export function initTheme(rootSelector = '#theme-switch') {
    const current = read();
    apply(current);

    const root = document.querySelector(rootSelector);
    if (!root) return;
    const buttons = root.querySelectorAll('[data-theme-value]');
    function refresh(active) {
        buttons.forEach((btn) => {
            const v = btn.getAttribute('data-theme-value');
            btn.setAttribute('aria-pressed', String(v === active));
        });
    }
    refresh(current);
    buttons.forEach((btn) => {
        btn.addEventListener('click', () => {
            const v = btn.getAttribute('data-theme-value');
            if (!VALID.includes(v)) return;
            write(v);
            apply(v);
            refresh(v);
        });
    });
}
