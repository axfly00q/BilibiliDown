import { api } from '/console/js/api.js';
import { applyI18n, injectLangSwitch, injectThemeSwitch, applyRoleNav } from '/console/js/i18n.js';

const $ = (id) => document.getElementById(id);
function escape(s) { return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }

let currentFavId = null;

async function init() {
  // 检查 B 站登录态
  let login = false;
  try {
    const r = await fetch('/api/account/status', { credentials: 'same-origin' });
    if (r.ok) {
      const j = await r.json();
      login = !!(j && j.data && j.data.login);
    }
  } catch {}
  if (!login) {
    $('auth-warn').innerHTML = '<div class="notice">需要登录 B 站账号才能使用收藏夹下载服务。<br><a href="/console/account.html">前往登录</a></div>';
    return;
  }
  $('main').hidden = false;
  await loadFolders();
}

async function loadFolders() {
  let list = [];
  try {
    const r = await api.favList();
    list = (r && r.data) || [];
  } catch (e) {
    if (e && e.status === 401) {
      $('auth-warn').innerHTML = '<div class="notice">需要登录 B 站账号。<br><a href="/console/account.html">前往登录</a></div>';
      $('main').hidden = true;
      return;
    }
    $('folders').innerHTML = '<li style="color:#c33;">' + escape(e.message || 'error') + '</li>';
    return;
  }
  if (!list.length) {
    $('folders').innerHTML = '<li class="empty" style="color:var(--text-muted);">暂无收藏夹</li>';
    return;
  }
  $('folders').innerHTML = list.map(f =>
    `<li data-id="${escape(f.id)}" data-title="${escape(f.title)}">
       <span>${escape(f.title)}</span>
       <span class="cnt">${f.count}</span>
     </li>`).join('');
}

$('folders').addEventListener('click', async (e) => {
  const li = e.target.closest('li[data-id]');
  if (!li) return;
  document.querySelectorAll('#folders li').forEach(x => x.classList.remove('active'));
  li.classList.add('active');
  currentFavId = li.dataset.id;
  $('title').textContent = li.dataset.title;
  $('submit-all').disabled = true;
  $('items').innerHTML = '<div class="empty">加载中...</div>';
  try {
    const r = await api.favItems(currentFavId);
    const data = (r && r.data) || {};
    const items = data.items || [];
    if (!items.length) {
      $('items').innerHTML = '<div class="empty">该收藏夹为空</div>';
      return;
    }
    $('items').innerHTML = items.map(it =>
      `<div class="item">
         ${it.preview ? `<img src="${escape(it.preview)}" referrerpolicy="no-referrer" loading="lazy">` : ''}
         <div class="body">
           <div class="title">${escape(it.avTitle || it.title)}</div>
           <div class="meta">${escape(it.upName || '')} · ${escape(it.avId || '')}</div>
         </div>
       </div>`).join('');
    $('submit-all').disabled = false;
  } catch (err) {
    $('items').innerHTML = '<div class="empty" style="color:#c33;">' + escape(err.message || 'error') + '</div>';
  }
});

$('submit-all').addEventListener('click', async () => {
  if (!currentFavId) return;
  if (!confirm('将整个收藏夹的视频加入下载队列，确认？')) return;
  $('submit-all').disabled = true;
  $('submit-all').textContent = '提交中...';
  try {
    const qn = $('qn').value;
    const r = await api.favSubmitAll(currentFavId, qn);
    const n = (r && r.data && r.data.submitted) || 0;
    alert('已提交 ' + n + ' 个下载任务');
  } catch (e) {
    alert(e.message || 'failed');
  } finally {
    $('submit-all').disabled = false;
    $('submit-all').textContent = '下载整个收藏夹';
  }
});

applyI18n();
injectLangSwitch('.topnav');
injectThemeSwitch('.topnav');
applyRoleNav();
init();
