// geetest-validator 业务逻辑（替换原 jQuery + alert 实现）
import { detectLang, loadDict, applyDom, makeT } from './i18n.js';
import { initTheme } from './theme.js';

function $(id) { return document.getElementById(id); }

const STATUS_ICONS = {
    info:    'icon-info',
    warn:    'icon-warn',
    error:   'icon-error',
    success: 'icon-check',
};

function getQuery(name) {
    return new URLSearchParams(window.location.search).get(name);
}

const state = {
    t: (k) => k, // 占位翻译函数，dict 加载完成后替换
    token: getQuery('token'),
    gt: getQuery('gt'),
    challenge: getQuery('challenge'),
    type: getQuery('type') || 'login',
    captchaObj: null,
};

function setStatus(kind, msgKey, params) {
    const bar = $('statusbar');
    if (!bar) return;
    bar.className = 'statusbar statusbar--' + kind;
    const iconId = STATUS_ICONS[kind] || STATUS_ICONS.info;
    // 重建内部结构：图标 + 文本（避免 innerHTML XSS 风险，使用 DOM API）
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
    span.textContent = state.t(msgKey, params);
    bar.appendChild(span);
    bar.hidden = false;
}

function clearStatus() {
    const bar = $('statusbar');
    if (!bar) return;
    bar.hidden = true;
    bar.textContent = '';
}

function showRetry(onRetry) {
    const btn = $('btn-retry');
    if (!btn) return;
    btn.hidden = false;
    btn.onclick = () => {
        btn.hidden = true;
        onRetry();
    };
}

function hideRetry() {
    const btn = $('btn-retry');
    if (btn) btn.hidden = true;
}

function tryCloseWithCountdown(successKey) {
    let seconds = 3;
    setStatus('success', successKey, { seconds });
    const timer = setInterval(() => {
        seconds--;
        if (seconds <= 0) {
            clearInterval(timer);
            try { window.open('', '_self'); window.close(); } catch (_) { /* ignore */ }
            // 浏览器可能拒绝 close（非脚本打开的窗口），改提示用户手动关闭
            setStatus('success', 'tip.successManualClose');
            const btn = $('btn-close');
            if (btn) btn.hidden = false;
        } else {
            setStatus('success', successKey, { seconds });
        }
    }, 1000);
}

async function submit() {
    if (!state.captchaObj) {
        setStatus('warn', 'tip.pleaseComplete');
        return;
    }
    const result = state.captchaObj.getValidate();
    if (!result) {
        setStatus('warn', 'tip.pleaseComplete');
        return;
    }
    $('validate').value = result.geetest_validate;
    $('seccode').value = result.geetest_seccode;
    setStatus('info', 'tip.submitting');
    hideRetry();
    const url = state.type === 'sms' ? '/geetest/sms' : '/geetest/login';
    try {
        const body = new URLSearchParams({
            token: state.token,
            challenge: state.challenge,
            validate: result.geetest_validate,
            seccode: result.geetest_seccode,
        });
        const resp = await fetch(url, {
            method: 'POST',
            credentials: 'omit',
            cache: 'no-store',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
            body,
        });
        if (!resp.ok) throw new Error('HTTP ' + resp.status);
        const data = await resp.json().catch(() => ({}));
        if (data && (data.code === 0 || data.code === '0')) {
            tryCloseWithCountdown('tip.success');
        } else {
            setStatus('error', 'tip.fail', { message: (data && data.message) || ('code=' + (data && data.code)) });
            showRetry(submit);
        }
    } catch (e) {
        setStatus('error', 'tip.networkError');
        showRetry(submit);
    }
}

function initCaptcha() {
    if (!state.gt || !state.challenge || !state.token) {
        setStatus('error', 'tip.missingParams');
        return;
    }
    setStatus('info', 'tip.loading');
    // initGeetest 由 gt.js 注入到全局
    window.initGeetest({
        gt: state.gt,
        challenge: state.challenge,
        offline: false,
        new_captcha: true,
        product: 'popup',
        width: '300px',
        https: true,
    }, (captchaObj) => {
        state.captchaObj = captchaObj;
        captchaObj.appendTo('#captcha');
        captchaObj.onReady(() => clearStatus());
        captchaObj.onError(() => {
            setStatus('error', 'tip.networkError');
            showRetry(initCaptcha);
        });
    });
}

(async function bootstrap() {
    initTheme('#theme-switch');
    try {
        const dict = await loadDict(detectLang());
        state.t = makeT(dict);
        applyDom(dict);
    } catch (e) {
        // i18n 加载失败时，DOM 显示 key，至少功能可用
    }
    // 填充只读字段，便于调试
    if (state.gt) $('gt').value = state.gt;
    if (state.challenge) $('challenge').value = state.challenge;
    // 按钮事件
    $('btn-login').addEventListener('click', submit);
    const btnClose = $('btn-close');
    if (btnClose) btnClose.addEventListener('click', () => {
        try { window.open('', '_self'); window.close(); } catch (_) {}
        window.location.href = 'about:blank';
    });
    // 提交按钮在 SMS 流程时改为 “发送短信验证码”
    if (state.type === 'sms') {
        const loginBtn = $('btn-login');
        loginBtn.setAttribute('data-i18n', 'btn.sms');
        loginBtn.textContent = state.t('btn.sms');
    }
    initCaptcha();
})();
