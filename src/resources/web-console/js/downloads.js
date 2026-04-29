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
    <td><span class="badge ${t.status}">${statusLabel(t.status)}</span></td>
    <td>
      <div class="progress"><div style="width:${pct}%"></div></div>
      <div class="progress-text">${pct}%</div>
    </td>
    <td>${fmtBytes(t.currentDown)} / ${fmtBytes(t.totalSize)}</td>
    <td>${t.speed > 0 ? fmtBytes(t.speed) + '/s' : '-'}</td>
    <td class="ops">
      ${t.status === 'active' ? '<button data-act="pause">暂停</button>' : ''}
      ${(t.status === 'paused' || t.status === 'fail') ? '<button data-act="resume">继续</button>' : ''}
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
  btn.disabled = true;
  try {
    if (btn.dataset.act === 'pause') await api.pause(id);
    else if (btn.dataset.act === 'resume') await api.resume(id);
    else if (btn.dataset.act === 'remove') await api.remove(id);
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
  try { const r = await api.taskList(); render(r.data); } catch {}
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
        qn: 0, page: 0
      }));
      render(fullLike);
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
