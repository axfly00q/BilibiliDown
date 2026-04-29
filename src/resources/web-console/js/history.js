import { api, fmtBytes } from '/console/js/api.js';
import { applyI18n, injectLangSwitch, injectThemeSwitch, applyRoleNav, t } from '/console/js/i18n.js';

const $ = (id) => document.getElementById(id);
function escape(s){ return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function fmtTime(ms) {
  if (!ms) return '-';
  const d = new Date(Number(ms));
  const pad = n => String(n).padStart(2, '0');
  return d.getFullYear() + '-' + pad(d.getMonth()+1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
}

let mode = 'dl';

async function load() {
  const fn = mode === 'dl' ? api.history : api.parseHistory;
  let items = [];
  try { const r = await fn(); items = (r && r.data) || []; } catch (e) {
    if (e && e.status === 401) {
      $('content').innerHTML = '<div class="empty">需要登录 B 站账号才能查看历史记录服务。<br><a href="/console/account.html">前往登录</a></div>';
      $('counter').textContent = '';
      return;
    }
    $('content').innerHTML = '<div class="empty">' + escape(e.message || 'error') + '</div>';
    return;
  }
  $('counter').textContent = '共 ' + items.length + ' 条';
  if (!items.length) {
    $('content').innerHTML = '<div class="empty">' + t('history.empty', '暂无历史') + '</div>';
    return;
  }
  if (mode === 'dl') {
    $('content').innerHTML = renderDownloadTable(items);
  } else {
    $('content').innerHTML = renderParseTable(items);
  }
}

function renderDownloadTable(items) {
  return `<table>
    <thead><tr>
      <th style="width:36%">${t('history.col.title','标题')}</th>
      <th>${t('history.col.size','大小')}</th>
      <th>${t('history.col.time','时间')}</th>
      <th style="width:24%">${t('history.col.action','操作')}</th>
    </tr></thead>
    <tbody>
      ${items.map(it => {
        const link = it.relPath ? '/files/' + it.relPath.split('/').map(encodeURIComponent).join('/') : '';
        return `<tr data-key="${escape(it.key)}">
          <td>
            <div>${escape(it.title || it.key)}</div>
            ${it.absPath ? `<div class="path">${escape(it.absPath)}</div>` : ''}
          </td>
          <td>${fmtBytes(it.size || 0)}</td>
          <td>${fmtTime(it.ts)}</td>
          <td class="ops">
            ${link ? `<a href="${link}" download>${t('downloads.openFile','下载')}</a>` : ''}
            ${it.absPath ? `<button data-act="open" data-path="${escape(it.absPath)}">${t('downloads.openDir','打开目录')}</button>` : ''}
            <button data-act="del">${t('common.delete','删除')}</button>
          </td>
        </tr>`;
      }).join('')}
    </tbody>
  </table>`;
}

function renderParseTable(items) {
  return `<table>
    <thead><tr>
      <th style="width:46%">${t('history.col.title','标题')}</th>
      <th>${t('history.col.input','输入')}</th>
      <th>${t('history.col.time','时间')}</th>
      <th style="width:14%">${t('history.col.action','操作')}</th>
    </tr></thead>
    <tbody>
      ${items.map(it => `<tr data-key="${escape(it.key)}">
        <td>${escape(it.title || it.avId || it.key)}</td>
        <td class="path">${escape(it.input || it.avId || '')}</td>
        <td>${fmtTime(it.ts)}</td>
        <td class="ops">
          <a href="/console/parse.html?input=${encodeURIComponent(it.input || it.avId || it.key)}">${t('nav.parse','解析')}</a>
          <button data-act="del">${t('common.delete','删除')}</button>
        </td>
      </tr>`).join('')}
    </tbody>
  </table>`;
}

document.addEventListener('click', async (e) => {
  const tab = e.target.closest('#tab-dl, #tab-ps');
  if (tab) {
    mode = tab.id === 'tab-dl' ? 'dl' : 'ps';
    $('tab-dl').classList.toggle('active', mode === 'dl');
    $('tab-ps').classList.toggle('active', mode === 'ps');
    load();
    return;
  }
  const del = e.target.closest('button[data-act="del"]');
  if (del) {
    const key = del.closest('tr').dataset.key;
    try {
      if (mode === 'dl') await api.historyRemove(key);
      else await api.parseHistoryRemove(key);
      load();
    } catch (err) { alert(err.message || 'failed'); }
    return;
  }
  const open = e.target.closest('button[data-act="open"]');
  if (open) {
    try { await api.fsOpen(open.dataset.path); } catch (err) { alert(err.message || 'failed'); }
  }
});

$('clear').addEventListener('click', async () => {
  if (!confirm('确认清空当前历史？')) return;
  try {
    if (mode === 'dl') await api.historyClear();
    else await api.parseHistoryClear();
    load();
  } catch (e) { alert(e.message || 'failed'); }
});

// 控制台密码鉴权已弃用。

applyI18n();
injectLangSwitch('.topnav');
injectThemeSwitch('.topnav');
applyRoleNav();
load();
