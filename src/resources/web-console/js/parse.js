import { api } from '/console/js/api.js';
import { applyI18n, injectLangSwitch, injectThemeSwitch, applyRoleNav, getRole, t } from '/console/js/i18n.js';
const $ = (id) => document.getElementById(id);
let qualities = [];
function toast(msg){ const t=$('toast'); t.textContent=msg; t.classList.add('show'); setTimeout(()=>t.classList.remove('show'), 1800); }
function escape(s){ return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }

async function init() {
  try { qualities = (await api.qualityList()).data || []; } catch {}
}

async function doParse() {
  const v = $('input').value.trim();
  if (!v) return;
  $('err').textContent = ''; $('result').innerHTML = '<p class="meta">解析中...</p>';
  try {
    const r = await api.parse(v);
    render(r.data);
    // 记录到本地（游客）或让服务器写入（服务器侧已动）
    if (await isGuest()) saveLocalParseHistory({ key: r.data.avId, title: r.data.title, avId: r.data.avId, input: v, ts: Date.now() });
    renderHistoryPanel();
  } catch (e) {
    $('result').innerHTML = '';
    $('err').textContent = e.message || '解析失败';
  }
}

let _isGuest = null;
async function isGuest() {
  // 未登录 B 站时使用 localStorage 暂存解析历史；登录后走服务端
  if (_isGuest != null) return _isGuest;
  try {
    const r = await fetch('/api/account/status', { credentials: 'same-origin' });
    if (r.ok) {
      const j = await r.json();
      _isGuest = !(j && j.data && j.data.login);
    } else {
      _isGuest = true;
    }
  } catch { _isGuest = true; }
  return _isGuest;
}
const LS_KEY = 'bd-parse-history';
function loadLocalParseHistory() {
  try { return JSON.parse(localStorage.getItem(LS_KEY) || '[]'); } catch { return []; }
}
function saveLocalParseHistory(entry) {
  let list = loadLocalParseHistory();
  list = list.filter(x => x.key !== entry.key);
  list.push(entry);
  while (list.length > 30) list.shift();
  try { localStorage.setItem(LS_KEY, JSON.stringify(list)); } catch {}
}
function removeLocalParseHistory(key) {
  const list = loadLocalParseHistory().filter(x => x.key !== key);
  try { localStorage.setItem(LS_KEY, JSON.stringify(list)); } catch {}
}
async function renderHistoryPanel() {
  const box = document.getElementById('history-list');
  if (!box) return;
  let items = [];
  if (await isGuest()) {
    items = loadLocalParseHistory().slice().reverse();
  } else {
    try { const r = await api.parseHistory(); items = (r && r.data) || []; } catch {}
  }
  if (!items.length) {
    box.innerHTML = '<div style="color:var(--text-muted);font-size:12px;">' + t('history.empty', '暂无历史') + '</div>';
    return;
  }
  box.innerHTML = items.map(it => `
    <div data-key="${escape(it.key)}" style="display:flex;gap:8px;align-items:center;padding:4px 0;border-bottom:1px dashed var(--border);">
      <a href="#" data-act="reuse" data-input="${escape(it.input || it.avId || it.key)}" style="flex:1;color:var(--text);text-decoration:none;">${escape(it.title || it.avId || it.key)}</a>
      <span style="color:var(--text-muted);font-size:11.5px;font-family:ui-monospace,SFMono-Regular,monospace;">${escape(it.input || it.avId || '')}</span>
      <button data-act="del" style="padding:2px 8px;border:1px solid var(--border);background:var(--surface);color:var(--text-muted);border-radius:4px;cursor:pointer;font-size:11px;">✕</button>
    </div>`).join('');
}

document.getElementById('history-list').addEventListener('click', async (e) => {
  const reuse = e.target.closest('a[data-act="reuse"]');
  if (reuse) {
    e.preventDefault();
    document.getElementById('input').value = reuse.dataset.input;
    doParse();
    return;
  }
  const del = e.target.closest('button[data-act="del"]');
  if (del) {
    const key = del.closest('[data-key]').dataset.key;
    if (await isGuest()) removeLocalParseHistory(key);
    else { try { await api.parseHistoryRemove(key); } catch {} }
    renderHistoryPanel();
  }
});

function render(d) {
  if (!d) { $('result').innerHTML = ''; return; }
  const clips = d.clips || [];
  const videoQs = qualities.filter(q => q.qn < 800);
  const extraQs = qualities.filter(q => q.qn >= 800);
  const qOptions = videoQs.map(q => `<option value="${q.qn}">${q.quality}</option>`).join('');
  const extraBtns = extraQs.map(q => `<button data-action="add" data-qn="${q.qn}" class="extra">${q.quality}</button>`).join('');
  const html = `
    <section class="video-card">
      <div>${d.preview ? `<img src="${d.preview}" referrerpolicy="no-referrer" alt="封面">` : ''}
        <button id="save-cover" class="cover-btn">下载封面图</button>
      </div>
      <div>
        <h2>${escape(d.title)}</h2>
        <div class="meta">${escape(d.avId)} · ${escape(d.author || '')}</div>
        <div class="meta brief">${escape(d.brief || '')}</div>
        <div id="clips" class="clips-wrap">
          ${clips.length === 0 ? '<p class="meta">未发现可下载分P</p>' :
            clips.map(c => `
              <div class="clip" data-cid="${c.cid}">
                <div class="title">P${c.page} · ${escape(c.title)}</div>
                <div class="clip-actions">
                  <span class="clip-label">视频:</span>
                  <select class="qn">${qOptions}</select>
                  <button data-action="add" data-qn="">加入下载</button>
                  ${extraBtns ? '<span class="clip-label clip-label-ext">扩展:</span>' + extraBtns : ''}
                </div>
              </div>`).join('')}
        </div>
      </div>
    </section>`;
  $('result').innerHTML = html;
  $('save-cover').addEventListener('click', async (e) => {
    e.target.disabled = true;
    try {
      const r = await api.saveCover(d.avId);
      toast('封面已保存: ' + r.data.file);
    } catch (err) {
      toast(err.message || '保存失败');
    } finally { e.target.disabled = false; }
  });
  $('result').addEventListener('click', async (e) => {
    if (e.target.dataset.action !== 'add') return;
    const row = e.target.closest('.clip');
    const cid = row.dataset.cid;
    const qn = e.target.dataset.qn || row.querySelector('select.qn').value;
    e.target.disabled = true;
    try {
      await api.submit(d.avId, cid, qn);
      toast('已加入下载队列 (qn=' + qn + ')');
    } catch (err) {
      toast(err.message || '提交失败');
    } finally {
      e.target.disabled = false;
    }
  });
}

$('parse').addEventListener('click', doParse);
$('input').addEventListener('keydown', e => { if (e.key === 'Enter') doParse(); });
// 控制台密码鉴权已弃用。
applyI18n();
injectLangSwitch('.topnav');
injectThemeSwitch('.topnav');
applyRoleNav();
init();
renderHistoryPanel();
// 支持 /console/parse.html?input=BVxxx 自动解析
try {
  const q = new URLSearchParams(location.search).get('input');
  if (q) { document.getElementById('input').value = q; doParse(); }
} catch {}
