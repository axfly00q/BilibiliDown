// Web 控制台主页：主题切换 + 鉴权检查
import { api } from '/console/js/api.js';
import { applyI18n, injectLangSwitch, applyRoleNav } from '/console/js/i18n.js';
import { autoCheckUpdate } from '/console/js/update.js';

// 主题
const KEY = 'bd-theme';
function applyTheme(t) {
  if (t === 'auto') document.documentElement.removeAttribute('data-theme');
  else document.documentElement.setAttribute('data-theme', t);
  document.querySelectorAll('.theme-switch__btn').forEach(b => {
    b.setAttribute('aria-pressed', String(b.dataset.themeValue === t));
  });
}
const saved = localStorage.getItem(KEY) || 'auto';
applyTheme(saved);
document.querySelectorAll('.theme-switch__btn').forEach(b => {
  b.addEventListener('click', () => {
    const v = b.dataset.themeValue;
    localStorage.setItem(KEY, v);
    applyTheme(v);
  });
});

// 控制台鉴权已弃用，无需检查登录态

// i18n
applyI18n();
injectLangSwitch('.topbar');
applyRoleNav();

// 进入控制台首页时自动检查更新（30 分钟缓存，无更新或被跳过则静默）
autoCheckUpdate();
