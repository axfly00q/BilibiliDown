import { api } from '/console/js/api.js';
import { applyI18n, injectLangSwitch, injectThemeSwitch, applyRoleNav, getRole, t } from '/console/js/i18n.js';
const $ = (id) => document.getElementById(id);
let qualities = [];
function toast(msg){ const t=$('toast'); t.textContent=msg; t.classList.add('show'); setTimeout(()=>t.classList.remove('show'), 4000); }
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
    // 查询当前账号 / 视频实际可用的清晰度（失败则使用内置全量枚举）
    let availQns = null;
    try {
      const clips = (r.data && r.data.clips) || [];
      const aid = r.data && r.data.avId;
      if (clips.length && aid && /^(av|BV)/i.test(aid)) {
        const a = await api.qualityAvail(aid, clips[0].cid);
        if (a && a.data && Array.isArray(a.data.qns)) availQns = new Set(a.data.qns);
      }
    } catch {}
    render(r.data, availQns);
    if (r.data && r.data.cvExportFile) {
      toast('专栏 HTML 已导出: ' + r.data.cvExportFile);
    }
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

function renderPictureGallery(d, clips) {
  const html = `
    <section class="video-card">
      <div>${d.preview ? `<img src="${d.preview}" referrerpolicy="no-referrer" alt="封面" style="max-width:200px;border-radius:6px;">` : ''}
        <div style="margin-top:8px;display:flex;flex-direction:column;gap:6px;">
          <button id="save-cover" class="cover-btn">${t('parse.saveCover','下载封面图')}</button>
          <button id="dl-all-pics" class="cover-btn">${t('parse.dlAllPics','下载全部图片')}</button>
        </div>
      </div>
      <div>
        <h2>${escape(d.title)}</h2>
        <div class="meta">${escape(d.avId)} · ${escape(d.author || '')}</div>
        <div class="meta brief">${escape(d.brief || '')}</div>
        ${d.cvExportFile ? `
          <div class="cv-export-tip" style="margin-top:10px;padding:10px 12px;border:1px solid var(--border);border-radius:6px;background:var(--surface-2);font-size:12.5px;line-height:1.6;">
            <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
              <span style="color:var(--brand);font-weight:600;">📄 专栏 HTML 已导出</span>
              <code style="flex:1;min-width:200px;color:var(--text-muted);font-family:ui-monospace,SFMono-Regular,monospace;word-break:break-all;">${escape(d.cvExportFile)}</code>
              <button id="copy-cv-path" style="padding:3px 10px;border:1px solid var(--border);background:var(--surface);color:var(--text);border-radius:4px;cursor:pointer;font-size:12px;">复制路径</button>
            </div>
            <div style="margin-top:6px;color:var(--text-muted);">💡 想要 PDF？用浏览器打开此 HTML → 按 <kbd style="padding:1px 6px;border:1px solid var(--border);border-radius:3px;background:var(--surface);font-family:ui-monospace,monospace;">Ctrl</kbd> + <kbd style="padding:1px 6px;border:1px solid var(--border);border-radius:3px;background:var(--surface);font-family:ui-monospace,monospace;">P</kbd> → 目标打印机选「另存为 PDF」即可。</div>
          </div>` : ''}
        <div id="clips" class="pic-grid" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:10px;margin-top:12px;">
          ${clips.length === 0 ? '<p class="meta">未发现可下载图片</p>' :
            clips.map(c => `
              <div class="pic-item" data-cid="${c.cid}" style="border:1px solid var(--border);border-radius:6px;overflow:hidden;background:var(--surface-2);">
                ${c.preview ? `<img src="${c.preview}" referrerpolicy="no-referrer" loading="lazy" style="width:100%;height:140px;object-fit:cover;display:block;">` : `<div style="height:140px;display:flex;align-items:center;justify-content:center;color:var(--text-muted);">P${c.page}</div>`}
                <div style="display:flex;align-items:center;justify-content:space-between;padding:6px 8px;font-size:12px;">
                  <span style="color:var(--text-muted);">P${c.page}</span>
                  <button data-action="add-pic" data-cid="${c.cid}" style="padding:3px 10px;border:1px solid var(--brand);background:var(--brand);color:#fff;border-radius:4px;cursor:pointer;font-size:12px;">${t('parse.dlPic','下载')}</button>
                </div>
              </div>`).join('')}
        </div>
      </div>
    </section>`;
  $('result').innerHTML = html;
  const copyBtn = $('copy-cv-path');
  if (copyBtn) {
    copyBtn.addEventListener('click', async () => {
      try { await navigator.clipboard.writeText(d.cvExportFile); toast('路径已复制'); }
      catch { toast('复制失败，请手动选中'); }
    });
  }
  $('save-cover').addEventListener('click', async (e) => {
    e.target.disabled = true;
    try { const r = await api.saveCover(d.avId); toast('封面已保存: ' + r.data.file); }
    catch (err) { toast(err.message || '保存失败'); }
    finally { e.target.disabled = false; }
  });
  $('dl-all-pics').addEventListener('click', async (e) => {
    e.target.disabled = true;
    let ok = 0, fail = 0;
    for (const c of clips) {
      try { await api.submit(d.avId, c.cid, 0); ok++; } catch { fail++; }
    }
    toast(`已加入下载: ${ok} 张${fail ? ', 失败 '+fail : ''}`);
    e.target.disabled = false;
  });
  $('result').addEventListener('click', async (e) => {
    if (e.target.dataset.action !== 'add-pic') return;
    const cid = e.target.dataset.cid;
    e.target.disabled = true;
    try { await api.submit(d.avId, cid, 0); toast('已加入下载队列'); }
    catch (err) { toast(err.message || '提交失败'); }
    finally { e.target.disabled = false; }
  });
}

function render(d, availQns) {
  if (!d) { $('result').innerHTML = ''; return; }
  const clips = d.clips || [];
  // 检测是否为图片型解析（专栏 cv / 图文动态 opus / 相簿 h）
  const isPicture = /^(cv|opus|h)\d+/i.test(d.avId || '');
  if (isPicture) return renderPictureGallery(d, clips);
  // 仅保留当前账号真正能拿到的清晰度（availQns 为 null 时表示未获取到，退回全量）
  const videoQs = qualities.filter(q => q.qn < 800 && (!availQns || availQns.has(q.qn)));
  const extraQs = qualities.filter(q => q.qn >= 800);
  const qOptions = videoQs.map(q => `<option value="${q.qn}">${q.quality}</option>`).join('');
  const extraBtns = extraQs.map(q => `<button data-action="add" data-qn="${q.qn}" class="extra">${q.quality}</button>`).join('');
  const availTip = availQns
    ? `<div class="meta" style="margin-top:4px;color:var(--text-muted);font-size:11.5px;">下拉仅列出当前账号可用的清晰度（B 站返回）</div>`
    : `<div class="meta" style="margin-top:4px;color:var(--text-muted);font-size:11.5px;">⚠️ 未能查到可用清晰度，列出的是全量枚举，选择超出账号权限的项会被服务端自动降级</div>`;
  const html = `
    <section class="video-card">
      <div>${d.preview ? `<img src="${d.preview}" referrerpolicy="no-referrer" alt="封面">` : ''}
        <button id="save-cover" class="cover-btn">下载封面图</button>
      </div>
      <div>
        <h2>${escape(d.title)}</h2>
        <div class="meta">${escape(d.avId)} · ${escape(d.author || '')}</div>
        <div class="meta brief">${escape(d.brief || '')}</div>
        ${availTip}
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
renderHistoryPanel();
// 支持 /console/parse.html?input=BVxxx 自动解析（确保 qualities 已加载完）
(async () => {
  await init();
  try {
    const q = new URLSearchParams(location.search).get('input');
    if (q) { document.getElementById('input').value = q; doParse(); }
  } catch {}
})();
