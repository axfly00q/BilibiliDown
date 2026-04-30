import { api, fmtBytes, statusLabel } from '/console/js/api.js';
import { applyI18n, injectLangSwitch, injectThemeSwitch, applyRoleNav } from '/console/js/i18n.js';
const $ = (id) => document.getElementById(id);
function escape(s){ return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function escapeAttr(s){ return escape(s); }

function row(t) {
  const pct = t.totalSize > 0 ? Math.min(100, (t.currentDown / t.totalSize) * 100).toFixed(1)
            : (t.status === 'done' ? 100 : 0);
  // 优先使用后端给出的 relPath；否则退化为最后一段
  const fname = (t.fileName && t.fileName !== '尚未生成') ? t.fileName.split(/[\\/]/).pop() : '';
  const relForLink = t.relPath || fname;
  const linkHref = relForLink ? ('/files/' + relForLink.split('/').map(encodeURIComponent).join('/')) : '';
  const dlBtn = (t.status === 'done' && linkHref) ? `<a href="${linkHref}" download>下载</a>` : '';
  const tip = escapeAttr(t.absPath || t.fileName || t.title || '');
  return `<tr data-id="${escapeAttr(t.id)}">
    <td title="${tip}">${escape(t.title || t.id)}${t.absPath ? `<div style="font-size:11px;color:var(--text-muted);margin-top:2px;word-break:break-all;">${escape(t.absPath)}</div>` : ''}</td>
    <td><span class="badge ${t.status}">${statusLabel(t.status)}</span>${t.status === 'fail' && t.lastError ? ` <button data-act="errinfo" title="查看错误详情" style="background:transparent;border:none;color:var(--brand);cursor:pointer;font-size:14px;padding:0 4px;">ⓘ</button>` : ''}</td>
    <td>
      <div class="progress"><div style="width:${pct}%"></div></div>
      <div class="progress-text">${pct}%</div>
    </td>
    <td>${fmtBytes(t.currentDown)} / ${fmtBytes(t.totalSize)}</td>
    <td>${t.speed > 0 ? fmtBytes(t.speed) + '/s' : '-'}</td>
    <td class="ops">
      ${t.status === 'active' ? '<button data-act="pause">暂停</button>' : ''}
      ${(t.status === 'paused' || t.status === 'fail') ? '<button data-act="resume">继续</button>' : ''}
      <button data-act="prio-top" title="置顶">⤒</button>
      <button data-act="prio-up" title="上移">↑</button>
      <button data-act="prio-down" title="下移">↓</button>
      <button data-act="remove">删除</button>
      ${t.absPath ? `<button data-act="open" data-path="${escapeAttr(t.absPath)}" title="打开所在目录">📂</button>` : ''}
      ${dlBtn}
    </td>
  </tr>`;
}

function render(tasks) {
  if (!tasks || tasks.length === 0) {
    $('tbody').innerHTML = '<tr><td colspan="6" class="empty">暂无任务</td></tr>';
    return;
  }
  $('tbody').innerHTML = tasks.map(row).join('');
}

// ---------- 过滤 / 排序 / 分页 ----------
const view = {
  text: '',
  status: '',
  sortBy: 'default',
  sortDir: 'desc',
  pageSize: 50,
  page: 1,
  raw: []
};

function applyView() {
  let list = view.raw.slice();
  // 过滤
  const kw = view.text.trim().toLowerCase();
  if (kw) {
    list = list.filter(t => {
      const hay = ((t.title || '') + ' ' + (t.avId || '') + ' ' + (t.absPath || '') + ' ' + (t.fileName || '') + ' ' + (t.id || '')).toLowerCase();
      return hay.indexOf(kw) >= 0;
    });
  }
  if (view.status) list = list.filter(t => t.status === view.status);
  // 排序
  const dir = view.sortDir === 'asc' ? 1 : -1;
  const cmpStr = (a,b) => String(a||'').localeCompare(String(b||''));
  switch (view.sortBy) {
    case 'title': list.sort((a,b) => cmpStr(a.title, b.title) * dir); break;
    case 'progress': list.sort((a,b) => {
      const pa = a.totalSize > 0 ? a.currentDown / a.totalSize : (a.status==='done'?1:0);
      const pb = b.totalSize > 0 ? b.currentDown / b.totalSize : (b.status==='done'?1:0);
      return (pa - pb) * dir;
    }); break;
    case 'size': list.sort((a,b) => ((a.totalSize||0) - (b.totalSize||0)) * dir); break;
    case 'speed': list.sort((a,b) => ((a.speed||0) - (b.speed||0)) * dir); break;
    case 'status': list.sort((a,b) => cmpStr(a.status, b.status) * dir); break;
    default: /* default = backend order */ break;
  }
  // 分页
  const total = list.length;
  let pageSize = view.pageSize > 0 ? view.pageSize : total;
  if (pageSize <= 0) pageSize = total || 1;
  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  if (view.page > totalPages) view.page = totalPages;
  if (view.page < 1) view.page = 1;
  const start = (view.page - 1) * pageSize;
  const slice = view.pageSize > 0 ? list.slice(start, start + pageSize) : list;
  render(slice);
  renderPager(total, totalPages);
}

function renderPager(total, totalPages) {
  const el = $('pager');
  if (!el) return;
  if (total <= (view.pageSize > 0 ? view.pageSize : total)) {
    el.innerHTML = `共 ${total} 条`;
    return;
  }
  const pages = [];
  for (let p = 1; p <= totalPages; p++) {
    if (p === 1 || p === totalPages || Math.abs(p - view.page) <= 2) {
      pages.push(p);
    } else if (pages[pages.length - 1] !== '…') {
      pages.push('…');
    }
  }
  const btnStyle = 'padding:4px 10px;border:1px solid var(--border);background:var(--surface);color:var(--text);border-radius:4px;cursor:pointer;font-size:12px;';
  const activeStyle = btnStyle + 'background:var(--brand);color:#fff;border-color:var(--brand);';
  el.innerHTML =
    `<button data-pg="prev" style="${btnStyle}" ${view.page<=1?'disabled':''}>‹</button>` +
    pages.map(p => p === '…'
      ? '<span>…</span>'
      : `<button data-pg="${p}" style="${p===view.page?activeStyle:btnStyle}">${p}</button>`).join('') +
    `<button data-pg="next" style="${btnStyle}" ${view.page>=totalPages?'disabled':''}>›</button>` +
    `<span style="margin-left:10px;">第 ${view.page} / ${totalPages} 页 · 共 ${total} 条</span>`;
}

function setRaw(tasks) {
  view.raw = tasks || [];
  applyView();
}

// 工具栏事件
const elText = $('filter-text');
const elStatus = $('filter-status');
const elSortBy = $('sort-by');
const elSortDir = $('sort-dir');
const elPageSize = $('page-size');
if (elText) elText.addEventListener('input', () => { view.text = elText.value; view.page = 1; applyView(); });
if (elStatus) elStatus.addEventListener('change', () => { view.status = elStatus.value; view.page = 1; applyView(); });
if (elSortBy) elSortBy.addEventListener('change', () => { view.sortBy = elSortBy.value; applyView(); });
if (elSortDir) elSortDir.addEventListener('change', () => { view.sortDir = elSortDir.value; applyView(); });
if (elPageSize) elPageSize.addEventListener('change', () => { view.pageSize = parseInt(elPageSize.value, 10) || 50; view.page = 1; applyView(); });
const pagerEl = document.getElementById('pager');
if (pagerEl) pagerEl.addEventListener('click', (e) => {
  const b = e.target.closest('button[data-pg]');
  if (!b) return;
  const v = b.dataset.pg;
  if (v === 'prev') view.page--;
  else if (v === 'next') view.page++;
  else view.page = parseInt(v, 10);
  applyView();
});

$('tbody').addEventListener('click', async (e) => {
  const btn = e.target.closest('button[data-act]');
  if (!btn) return;
  const id = btn.closest('tr').dataset.id;
  if (btn.dataset.act === 'open') {
    btn.disabled = true;
    try { await api.fsOpen(btn.dataset.path); } catch (err) { alert(err.message || '打开失败'); }
    finally { btn.disabled = false; }
    return;
  }
  if (btn.dataset.act === 'errinfo') {
    const t = (view.raw || []).find(x => x.id === id);
    showErrorModal(t);
    return;
  }
  btn.disabled = true;
  try {
    if (btn.dataset.act === 'pause') await api.pause(id);
    else if (btn.dataset.act === 'resume') await api.resume(id);
    else if (btn.dataset.act === 'remove') await api.remove(id);
    else if (btn.dataset.act === 'prio-top') await api.movePriority(id, 'top');
    else if (btn.dataset.act === 'prio-up') await api.movePriority(id, 'up');
    else if (btn.dataset.act === 'prio-down') await api.movePriority(id, 'down');
    refresh();
  } catch (err) {
    alert(err.message || '操作失败');
  } finally { btn.disabled = false; }
});

const openDirBtn = document.getElementById('open-save-dir');
if (openDirBtn) openDirBtn.addEventListener('click', async () => {
  openDirBtn.disabled = true;
  try { await api.fsOpen(''); } catch (e) { alert(e.message || '打开失败'); }
  finally { openDirBtn.disabled = false; }
});

$('pause-all').onclick = async () => { await api.pauseAll(); refresh(); };
$('resume-all').onclick = async () => { await api.resumeAll(); refresh(); };
$('remove-done').onclick = async () => { await api.removeDone(); refresh(); };
// 控制台密码鉴权已弃用。

async function refresh() {
  try { const r = await api.taskList(); setRaw(r.data); } catch {}
}

let es;
function startSse() {
  if (es) es.close();
  es = new EventSource('/sse/tasks');
  es.addEventListener('tasks', (e) => {
    try {
      const list = JSON.parse(e.data);
      const fullLike = list.map(x => ({
        id: x.id, title: x.title, status: x.status,
        currentDown: x.currentDown, totalSize: x.totalSize,
        speed: x.speed, fileName: x.fileName, absPath: x.absPath, relPath: x.relPath,
        avId: x.avId,
        qn: 0, page: 0
      }));
      setRaw(fullLike);
      $('status').textContent = '实时更新中 · 共 ' + fullLike.length + ' 个任务';
    } catch {}
  });
  es.onerror = () => {
    $('status').textContent = '推送已断开，10 秒后重试...';
    setTimeout(startSse, 10000);
  };
}

refresh();
startSse();
applyI18n();
injectLangSwitch('.topnav');
injectThemeSwitch('.topnav');
applyRoleNav();
// 顶部显示当前下载根目录
api.fsSavePath().then(r => {
  if (r && r.data && r.data.abs) $('save-dir').textContent = r.data.abs;
}).catch(() => {});

// ---------- 失败详情弹窗 ----------
function showErrorModal(t) {
  if (!t) return;
  let mask = document.getElementById('err-modal');
  if (mask) mask.remove();
  mask = document.createElement('div');
  mask.id = 'err-modal';
  mask.style.cssText = 'position:fixed;inset:0;background:rgba(0,0,0,.45);z-index:9999;display:flex;align-items:center;justify-content:center;padding:20px;';
  mask.innerHTML = `
    <div style="background:var(--surface);color:var(--text);border:1px solid var(--border);border-radius:var(--radius-md,8px);max-width:760px;width:100%;max-height:80vh;display:flex;flex-direction:column;overflow:hidden;box-shadow:0 8px 32px rgba(0,0,0,.4);">
      <div style="padding:14px 18px;border-bottom:1px solid var(--border);display:flex;align-items:center;gap:10px;">
        <strong style="flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">失败详情 · ${escape(t.title || t.id)}</strong>
        <button data-close style="background:transparent;border:none;color:var(--text-muted);font-size:20px;cursor:pointer;">×</button>
      </div>
      <div style="padding:14px 18px;overflow:auto;font-size:12.5px;">
        <div style="color:var(--text-muted);margin-bottom:6px;">任务 ID：${escape(t.id)}</div>
        ${t.absPath ? `<div style="color:var(--text-muted);margin-bottom:10px;word-break:break-all;">路径：${escape(t.absPath)}</div>` : ''}
        <pre style="margin:0;padding:12px;background:var(--surface-2,#0d1117);color:#e6edf3;border-radius:var(--radius-sm,4px);white-space:pre-wrap;word-break:break-all;font-size:12px;line-height:1.5;max-height:50vh;overflow:auto;">${escape(t.lastError || '(无可用错误信息)')}</pre>
      </div>
      <div style="padding:10px 18px;border-top:1px solid var(--border);display:flex;justify-content:flex-end;gap:8px;">
        <button data-copy>复制错误</button>
        <button data-close>关闭</button>
      </div>
    </div>`;
  document.body.appendChild(mask);
  mask.addEventListener('click', (e) => {
    if (e.target === mask || e.target.matches('[data-close]')) mask.remove();
    else if (e.target.matches('[data-copy]')) {
      try { navigator.clipboard.writeText(t.lastError || ''); e.target.textContent = '已复制'; } catch {}
    }
  });
}
