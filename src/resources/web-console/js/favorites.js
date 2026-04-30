import { api } from '/console/js/api.js';
import { applyI18n, injectLangSwitch, injectThemeSwitch, applyRoleNav } from '/console/js/i18n.js';

const $ = (id) => document.getElementById(id);
function escape(s) { return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }

let currentFavId = null;
let allItems = [];     // 当前收藏夹的全部 clip
let curPage = 1;
const PAGE_SIZE = 20;

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
  $('items').innerHTML = '<div class="empty">加载中（拉取全部页，可能需要几秒）...</div>';
  $('pager').hidden = true;
  allItems = [];
  curPage = 1;
  try {
    const r = await api.favItems(currentFavId);
    const data = (r && r.data) || {};
    allItems = data.items || [];
    if (!allItems.length) {
      $('items').innerHTML = '<div class="empty">该收藏夹为空</div>';
      return;
    }
    renderPage();
    $('submit-all').disabled = false;
  } catch (err) {
    $('items').innerHTML = '<div class="empty" style="color:#c33;">' + escape(err.message || 'error') + '</div>';
  }
});

function renderPage() {
  const total = allItems.length;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  if (curPage > totalPages) curPage = totalPages;
  if (curPage < 1) curPage = 1;
  const start = (curPage - 1) * PAGE_SIZE;
  const slice = allItems.slice(start, start + PAGE_SIZE);
  $('items').innerHTML = slice.map(it => {
    const href = it.avId ? ('/console/parse.html?input=' + encodeURIComponent(it.avId)) : '#';
    return `<a class="item" href="${href}" title="点击解析并下载">
       ${it.preview ? `<img src="${escape(it.preview)}" referrerpolicy="no-referrer" loading="lazy">` : ''}
       <div class="body">
         <div class="title">${escape(it.avTitle || it.title)}</div>
         <div class="meta">${escape(it.upName || '')} · ${escape(it.avId || '')}</div>
       </div>
     </a>`;
  }).join('');
  renderPager(totalPages, total);
}

function renderPager(totalPages, total) {
  const pg = $('pager');
  if (totalPages <= 1) { pg.hidden = true; pg.innerHTML = ''; return; }
  pg.hidden = false;
  const btns = [];
  btns.push(`<button data-go="1" ${curPage === 1 ? 'disabled' : ''}>« 首页</button>`);
  btns.push(`<button data-go="${curPage - 1}" ${curPage === 1 ? 'disabled' : ''}>‹ 上一页</button>`);
  // 页码（最多显示 7 个，居中当前页）
  let from = Math.max(1, curPage - 3);
  let to = Math.min(totalPages, from + 6);
  from = Math.max(1, to - 6);
  for (let i = from; i <= to; i++) {
    btns.push(`<button data-go="${i}" class="${i === curPage ? 'active' : ''}">${i}</button>`);
  }
  btns.push(`<button data-go="${curPage + 1}" ${curPage === totalPages ? 'disabled' : ''}>下一页 ›</button>`);
  btns.push(`<button data-go="${totalPages}" ${curPage === totalPages ? 'disabled' : ''}>末页 »</button>`);
  btns.push(`<span class="info">第 ${curPage}/${totalPages} 页 · 共 ${total} 个视频</span>`);
  pg.innerHTML = btns.join('');
}

$('pager').addEventListener('click', (e) => {
  const b = e.target.closest('button[data-go]');
  if (!b || b.disabled) return;
  curPage = parseInt(b.dataset.go, 10) || 1;
  renderPage();
  // 滚动到列表顶部
  document.querySelector('section.panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
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
