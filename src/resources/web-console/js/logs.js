import { api } from '/console/js/api.js';
import { applyI18n, injectLangSwitch, applyRoleNav, t } from '/console/js/i18n.js';

// 主题：与其他页面行为一致
const THEME_KEY = 'bd-theme';
const savedTheme = localStorage.getItem(THEME_KEY) || 'auto';
if (savedTheme !== 'auto') document.documentElement.setAttribute('data-theme', savedTheme);
else { document.body.dataset.theme = 'auto'; }

applyI18n();
injectLangSwitch('.topnav');
applyRoleNav();

const pane = document.getElementById('pane');
const elLevel = document.getElementById('level');
const elFilter = document.getElementById('filter');
const elAuto = document.getElementById('autoscroll');
const elPaused = document.getElementById('paused');
const elStatus = document.getElementById('status');
const elClear = document.getElementById('clear');
const elCopy = document.getElementById('copy');
const elReload = document.getElementById('reload');

const MAX_RENDER = 3000; // 前端最多渲染多少行，超出从顶端裁剪

let since = 0;
let buffer = [];   // 已渲染的行 [{seq,ts,level,text}]
let timer = null;

function fmtTs(ms) {
  const d = new Date(ms);
  const pad = n => String(n).padStart(2, '0');
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function escapeHtml(s) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function highlight(text, kw) {
  const safe = escapeHtml(text);
  if (!kw) return safe;
  try {
    const re = new RegExp(kw.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi');
    return safe.replace(re, m => `<mark>${m}</mark>`);
  } catch { return safe; }
}

function render() {
  const kw = elFilter.value.trim();
  if (buffer.length === 0) {
    pane.innerHTML = `<div class="empty">${t('logs.empty', '暂无日志')}</div>`;
    return;
  }
  // 简单全量重绘（行数有限，max ~3000）
  const html = buffer.map(e =>
    `<div class="ln"><span class="ts">${fmtTs(e.ts)}</span>` +
    `<span class="lv ${e.level}">${e.level}</span>` +
    `<span class="tx">${highlight(e.text, kw)}</span></div>`
  ).join('');
  pane.innerHTML = html;
  if (elAuto.checked) pane.scrollTop = pane.scrollHeight;
}

async function tick() {
  if (elPaused.checked) { elStatus.textContent = '⏸ ' + t('logs.paused', '已暂停'); return; }
  try {
    const r = await api.logsTail(since, elLevel.value, 500);
    const data = r && r.data;
    if (!data) return;
    if (data.installed === false) {
      elStatus.textContent = t('logs.notInstalled', '日志拦截未启用 (需重启)');
    }
    const lines = data.lines || [];
    if (lines.length > 0) {
      buffer.push(...lines);
      if (buffer.length > MAX_RENDER) buffer = buffer.slice(buffer.length - MAX_RENDER);
      since = data.nextSince || since;
      render();
    }
    elStatus.textContent = `seq=${since} · ${buffer.length} ${t('logs.linesUnit', '行')}`;
  } catch (e) {
    elStatus.textContent = '✗ ' + (e.message || e);
  }
}

function startPolling() {
  if (timer) clearInterval(timer);
  timer = setInterval(tick, 1500);
}

elClear.addEventListener('click', () => { buffer = []; render(); });
elCopy.addEventListener('click', async () => {
  const txt = buffer.map(e => `[${new Date(e.ts).toISOString()}] [${e.level}] ${e.text}`).join('\n');
  try { await navigator.clipboard.writeText(txt); elStatus.textContent = '✓ ' + t('logs.copied', '已复制'); }
  catch { elStatus.textContent = '✗ clipboard'; }
});
elReload.addEventListener('click', () => {
  buffer = []; since = 0; render(); tick();
});
elLevel.addEventListener('change', () => { buffer = []; since = 0; render(); tick(); });
elFilter.addEventListener('input', render);

// 初始化：先取当前 seq 作为起点（避免首次拉到 2000 行历史也行；这里直接拿全部历史更友好）
tick();
startPolling();
