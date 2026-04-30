import { api } from '/console/js/api.js';
import { applyI18n, injectLangSwitch, injectThemeSwitch, applyRoleNav } from '/console/js/i18n.js';
import { manualCheckUpdate } from '/console/js/update.js';
const $ = (id) => document.getElementById(id);
function escape(s){ return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function toast(msg){ const t=$('toast'); t.textContent=msg; t.classList.add('show'); setTimeout(()=>t.classList.remove('show'), 4000); }

let original = {};

function fieldHtml(f) {
  const id = 'fld_' + f.key.replace(/\W/g, '_');
  let ctl;
  if (f.key === 'bilibili.savePath') {
    ctl = `<div class="browse-row">
      <input id="${id}" data-key="${escape(f.key)}" type="text" value="${escape(f.value)}">
      <button type="button" data-browse="${id}">浏览…</button>
    </div>
    <div class="resolved" id="${id}_abs"></div>`;
  } else if (f.type === 'select') {
    const opts = f.valids.split(',').map(v => `<option value="${escape(v)}"${v === f.value ? ' selected' : ''}>${escape(v)}</option>`).join('');
    ctl = `<select id="${id}" data-key="${escape(f.key)}">${opts}</select>`;
  } else if (f.type === 'bool') {
    const opts = ['true', 'false'].map(v => `<option value="${v}"${v === String(f.value) ? ' selected' : ''}>${v}</option>`).join('');
    ctl = `<select id="${id}" data-key="${escape(f.key)}">${opts}</select>`;
  } else if (f.type === 'int') {
    ctl = `<input id="${id}" data-key="${escape(f.key)}" type="number" value="${escape(f.value)}">`;
  } else {
    const isPwd = f.key.endsWith('.password');
    ctl = `<input id="${id}" data-key="${escape(f.key)}" type="${isPwd ? 'password' : 'text'}" value="${escape(f.value)}" ${isPwd ? 'autocomplete="new-password"' : ''}>`;
  }
  return `<div class="field">
    <div class="label">${escape(f.note || f.key)}<span class="key">${escape(f.key)}</span></div>
    <div class="ctl">${ctl}<div class="note" id="${id}_note">${escape(f.desc || '')}</div></div>
  </div>`;
}

async function load() {
  $('err').textContent = '';
  $('status').textContent = '加载中...';
  try {
    const r = await api.settingsList();
    const list = r.data || [];
    original = {};
    list.forEach(f => { original[f.key] = String(f.value); });
    $('form').innerHTML = list.map(fieldHtml).join('');
    $('status').textContent = '已加载 ' + list.length + ' 项';
    // 显示 savePath 解析后的绝对路径
    try {
      const sp = await api.fsSavePath();
      const absEl = document.getElementById('fld_bilibili_savePath_abs');
      if (absEl && sp.data) absEl.textContent = '实际目录：' + sp.data.abs;
    } catch {}
  } catch (e) {
    $('err').textContent = e.message || '加载失败';
    $('status').textContent = '';
  }
}

async function save() {
  $('err').textContent = '';
  const changed = {};
  document.querySelectorAll('#form [data-key]').forEach(el => {
    const k = el.dataset.key;
    const v = el.value;
    if (v !== original[k]) changed[k] = v;
  });
  if (Object.keys(changed).length === 0) { toast('没有修改'); return; }
  $('save').disabled = true;
  $('status').textContent = '保存中...';
  try {
    const r = await api.settingsSave(changed);
    toast('已保存 ' + r.data.saved + ' 项');
    $('status').textContent = '已保存 · ' + new Date().toLocaleTimeString();
    Object.assign(original, changed);
  } catch (e) {
    $('err').textContent = e.message || '保存失败';
    $('status').textContent = '';
  } finally {
    $('save').disabled = false;
  }
}

$('reload').onclick = load;
$('save').onclick = save;
const cuBtn = document.getElementById('check-update');
if (cuBtn) cuBtn.onclick = () => manualCheckUpdate();
// 控制台密码鉴权已弃用，原“登出控制台”按钮已从页面移除。

// === 目录选择器 ===
let currentDir = '';
async function openPicker(targetInputId) {
  const mask = $('dir-modal');
  mask.dataset.target = targetInputId;
  $('dir-err').textContent = '';
  // 起始目录：优先使用 input 当前值的解析路径，否则用 savePath
  let start = '';
  try {
    const inputEl = document.getElementById(targetInputId);
    const v = (inputEl && inputEl.value || '').trim();
    if (v) {
      // 让后端帮我们规范化（直接 list 一次）
      try { await loadDir(v); start = currentDir; } catch {}
    }
    if (!start) {
      const sp = await api.fsSavePath();
      if (sp.data && sp.data.abs) { await loadDir(sp.data.abs); start = currentDir; }
    }
  } catch {}
  if (!start) await loadDir('');
  mask.classList.add('show');
}
async function loadDir(path) {
  $('dir-err').textContent = '';
  try {
    const r = await api.fsList(path || '');
    const d = r.data || {};
    currentDir = d.cwd || '';
    $('dir-cwd').textContent = currentDir || '(根)';
    // 盘符 / 根
    const drv = $('dir-drive');
    drv.innerHTML = '';
    (d.drives || []).forEach(p => {
      const o = document.createElement('option');
      o.value = p; o.textContent = p;
      if (currentDir && (currentDir === p || currentDir.toLowerCase().startsWith(p.toLowerCase()))) o.selected = true;
      drv.appendChild(o);
    });
    // 列表
    const ul = $('dir-list');
    ul.innerHTML = '';
    if (!d.dirs || d.dirs.length === 0) {
      const li = document.createElement('li');
      li.className = 'empty';
      li.textContent = currentDir ? '（空目录）' : '请从上方选择盘符';
      ul.appendChild(li);
    } else {
      d.dirs.forEach(name => {
        const li = document.createElement('li');
        li.textContent = '📁 ' + name;
        li.onclick = () => loadDir(currentDir.replace(/[\\/]+$/, '') + (currentDir.endsWith('\\') || currentDir.endsWith('/') ? '' : (currentDir.includes('\\') ? '\\' : '/')) + name);
        ul.appendChild(li);
      });
    }
    // 上一级
    $('dir-up').disabled = !d.parent;
    $('dir-up').dataset.parent = d.parent || '';
  } catch (e) {
    $('dir-err').textContent = e.message || '读取失败';
  }
}
$('dir-up').onclick = () => { const p = $('dir-up').dataset.parent; if (p) loadDir(p); };
$('dir-drive').onchange = (e) => loadDir(e.target.value);
$('dir-cancel').onclick = () => $('dir-modal').classList.remove('show');
$('dir-ok').onclick = () => {
  if (!currentDir) { $('dir-err').textContent = '请先选择一个目录'; return; }
  const targetId = $('dir-modal').dataset.target;
  const inputEl = document.getElementById(targetId);
  if (inputEl) {
    inputEl.value = currentDir;
    inputEl.dispatchEvent(new Event('input', { bubbles: true }));
  }
  $('dir-modal').classList.remove('show');
};
$('dir-mkdir').onclick = async () => {
  if (!currentDir) { $('dir-err').textContent = '请先进入一个目录'; return; }
  const name = prompt('新建文件夹名称：');
  if (!name) return;
  try {
    const r = await api.fsMkdir(currentDir, name.trim());
    await loadDir(r.data.path || currentDir);
  } catch (e) {
    $('dir-err').textContent = e.message || '创建失败';
  }
};
$('dir-modal').addEventListener('click', (e) => { if (e.target === $('dir-modal')) $('dir-modal').classList.remove('show'); });
// 委托：点击表单中的"浏览…"
document.addEventListener('click', (e) => {
  const b = e.target.closest('button[data-browse]');
  if (b) { e.preventDefault(); openPicker(b.dataset.browse); }
});

applyI18n();
injectLangSwitch('.topnav');
injectThemeSwitch('.topnav');
applyRoleNav();
load();
