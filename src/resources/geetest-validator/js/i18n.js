// 轻量 i18n 工具：从 /static/i18n/<lang>.json 加载文案，并替换 [data-i18n] / [data-i18n-attr] 节点
// 使用原生 fetch + ES module，无外部依赖。

const SUPPORTED = ['zh-CN', 'en-US', 'ja-JP'];
const FALLBACK = 'zh-CN';

function getQueryVariable(name) {
    const params = new URLSearchParams(window.location.search);
    return params.get(name);
}

function normalize(lang) {
    if (!lang) return null;
    if (SUPPORTED.includes(lang)) return lang;
    const lower = lang.toLowerCase();
    if (lower.startsWith('zh')) {
        // zh-TW / zh-HK / zh-Hant 也回退到 zh-CN（暂不区分繁简）
        return 'zh-CN';
    }
    if (lower.startsWith('ja')) return 'ja-JP';
    if (lower.startsWith('en')) return 'en-US';
    return null;
}

export function detectLang() {
    const fromQuery = normalize(getQueryVariable('lang'));
    if (fromQuery) return fromQuery;
    const fromNav = normalize(navigator.language || (navigator.languages && navigator.languages[0]));
    if (fromNav) return fromNav;
    return FALLBACK;
}

async function fetchDict(lang) {
    const resp = await fetch('/static/i18n/' + lang + '.json', { credentials: 'omit', cache: 'no-store' });
    if (!resp.ok) throw new Error('lang not found: ' + lang);
    return resp.json();
}

export async function loadDict(lang) {
    try {
        return await fetchDict(lang);
    } catch (e) {
        if (lang !== FALLBACK) {
            return fetchDict(FALLBACK);
        }
        throw e;
    }
}

function format(template, params) {
    if (!params) return template;
    return template.replace(/\{(\w+)\}/g, (_, key) => (key in params ? String(params[key]) : '{' + key + '}'));
}

export function makeT(dict) {
    return function t(key, params) {
        const raw = dict[key];
        if (raw == null) return key;
        return format(raw, params);
    };
}

export function applyDom(dict, root = document) {
    const t = makeT(dict);
    root.querySelectorAll('[data-i18n]').forEach((el) => {
        const key = el.getAttribute('data-i18n');
        el.textContent = t(key);
    });
    root.querySelectorAll('[data-i18n-attr]').forEach((el) => {
        // 格式： attr1:key1,attr2:key2
        const spec = el.getAttribute('data-i18n-attr');
        spec.split(',').forEach((pair) => {
            const [attr, key] = pair.split(':').map((s) => s.trim());
            if (attr && key) el.setAttribute(attr, t(key));
        });
    });
    if (dict['page.title']) {
        document.title = dict['page.title'];
    }
    document.documentElement.setAttribute('lang', detectLang());
}
