// 在线更新检查模块。被 console.js（首页）和 settings.js（手动按钮）共用。
import { api } from '/console/js/api.js';

const SKIP_KEY = 'bd-skip-update-ver';

function escape(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// 把 GitHub release body（Markdown）渲染为简单 HTML：标题、列表、代码块、链接、加粗
function renderNotes(md) {
  if (!md) return '<p style="color:var(--text-muted);">暂无更新说明</p>';
  const lines = String(md).replace(/\r/g, '').split('\n');
  const out = [];
  let inUl = false, inCode = false;
  const closeUl = () => { if (inUl) { out.push('</ul>'); inUl = false; } };
  for (const raw of lines) {
    const line = raw;
    if (line.trim().startsWith('```')) {
      closeUl();
      if (!inCode) { out.push('<pre><code>'); inCode = true; }
      else { out.push('</code></pre>'); inCode = false; }
      continue;
    }
    if (inCode) { out.push(escape(line)); continue; }
    let m;
    if ((m = line.match(/^(#{1,6})\s+(.*)$/))) {
      closeUl();
      const lvl = Math.min(m[1].length + 2, 6);
      out.push(`<h${lvl}>${inline(m[2])}</h${lvl}>`);
      continue;
    }
    if ((m = line.match(/^\s*[-*]\s+(.*)$/))) {
      if (!inUl) { out.push('<ul>'); inUl = true; }
      out.push('<li>' + inline(m[1]) + '</li>');
      continue;
    }
    closeUl();
    if (line.trim() === '') out.push('');
    else out.push('<p>' + inline(line) + '</p>');
  }
  closeUl();
  if (inCode) out.push('</code></pre>');
  return out.join('\n');
}

function inline(s) {
  s = escape(s);
  s = s.replace(/`([^`]+)`/g, '<code>$1</code>');
  s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  s = s.replace(/\[([^\]]+)\]\((https?:[^)]+)\)/g, '<a href="$2" target="_blank" rel="noreferrer noopener">$1</a>');
  // 裸链接
  s = s.replace(/(^|[^"'>])\b(https?:\/\/[^\s<]+)/g, (m, p, u) => p + '<a href="' + u + '" target="_blank" rel="noreferrer noopener">' + u + '</a>');
  return s;
}

function ensureModal() {
  let m = document.getElementById('bd-update-modal');
  if (m) return m;
  m = document.createElement('div');
  m.id = 'bd-update-modal';
  m.innerHTML = `
    <style>
      #bd-update-modal { position:fixed; inset:0; background:rgba(0,0,0,.5); display:none; align-items:center; justify-content:center; z-index:10000; }
      #bd-update-modal.show { display:flex; }
      #bd-update-modal .box { background:var(--surface,#fff); color:var(--text,#222); border:1px solid var(--border,#ddd); border-radius:10px; width:640px; max-width:92vw; max-height:82vh; display:flex; flex-direction:column; box-shadow:0 8px 32px rgba(0,0,0,.25); }
      #bd-update-modal .hd { padding:14px 18px; border-bottom:1px solid var(--border,#eee); display:flex; align-items:center; gap:10px; }
      #bd-update-modal .hd .ico { width:28px; height:28px; border-radius:6px; background:var(--brand-soft,#fde7ef); color:var(--brand,#fb7299); display:flex; align-items:center; justify-content:center; font-weight:700; }
      #bd-update-modal .hd h3 { margin:0; font-size:16px; }
      #bd-update-modal .hd .ver { font-size:12px; color:var(--text-muted,#888); margin-left:6px; }
      #bd-update-modal .bd { padding:14px 18px; overflow:auto; flex:1; font-size:13.5px; line-height:1.6; }
      #bd-update-modal .bd h3, #bd-update-modal .bd h4, #bd-update-modal .bd h5 { margin:12px 0 6px; }
      #bd-update-modal .bd ul { padding-left:22px; margin:6px 0; }
      #bd-update-modal .bd code { background:var(--surface-2,#f5f5f5); padding:1px 5px; border-radius:3px; font-family:ui-monospace,SFMono-Regular,monospace; font-size:12.5px; }
      #bd-update-modal .bd pre { background:var(--surface-2,#f5f5f5); padding:8px 10px; border-radius:6px; overflow:auto; }
      #bd-update-modal .bd a { color:var(--brand,#fb7299); }
      #bd-update-modal .ft { padding:10px 18px; border-top:1px solid var(--border,#eee); display:flex; gap:8px; justify-content:flex-end; align-items:center; }
      #bd-update-modal .ft .meta { flex:1; font-size:12px; color:var(--text-muted,#888); }
      #bd-update-modal .ft button { padding:7px 14px; border:1px solid var(--border,#ddd); background:var(--surface,#fff); color:var(--text,#222); border-radius:6px; cursor:pointer; font-size:13px; }
      #bd-update-modal .ft button.primary { background:var(--brand,#fb7299); color:#fff; border-color:var(--brand,#fb7299); }
      #bd-update-modal .ft button:hover { opacity:.92; }
    </style>
    <div class="box" role="dialog" aria-modal="true">
      <div class="hd">
        <div class="ico">↑</div>
        <h3 id="bd-up-title">发现新版本</h3>
        <span class="ver" id="bd-up-ver"></span>
      </div>
      <div class="bd" id="bd-up-body"></div>
      <div class="ft">
        <span class="meta" id="bd-up-meta"></span>
        <button id="bd-up-skip" type="button">本版本不再提醒</button>
        <button id="bd-up-later" type="button">稍后再说</button>
        <button id="bd-up-go" class="primary" type="button">立即更新</button>
      </div>
    </div>`;
  document.body.appendChild(m);
  m.addEventListener('click', (e) => { if (e.target === m) m.classList.remove('show'); });
  return m;
}

export function showUpdateModal(info) {
  const m = ensureModal();
  document.getElementById('bd-up-title').textContent = info.name || ('新版本 ' + info.latest);
  document.getElementById('bd-up-ver').textContent = '当前 ' + (info.current || '?') + ' → 最新 ' + info.latest;
  document.getElementById('bd-up-body').innerHTML = renderNotes(info.body);
  document.getElementById('bd-up-meta').textContent = info.publishedAt ? ('发布于 ' + info.publishedAt.replace('T', ' ').replace('Z', ' UTC')) : '';
  const goBtn = document.getElementById('bd-up-go');
  goBtn.onclick = () => {
    if (info.htmlUrl) window.open(info.htmlUrl, '_blank', 'noopener');
    m.classList.remove('show');
  };
  document.getElementById('bd-up-later').onclick = () => m.classList.remove('show');
  document.getElementById('bd-up-skip').onclick = () => {
    try { localStorage.setItem(SKIP_KEY, info.latest); } catch {}
    m.classList.remove('show');
  };
  m.classList.add('show');
}

/** 自动检查：仅在有新版且用户未跳过该版本时弹窗 */
export async function autoCheckUpdate() {
  try {
    const r = await api.updateCheck();
    const d = (r && r.data) || {};
    if (!d.hasUpdate || !d.latest) return;
    let skip = '';
    try { skip = localStorage.getItem(SKIP_KEY) || ''; } catch {}
    if (skip === d.latest) return;
    showUpdateModal(d);
  } catch (e) {
    // 自动检查静默失败
    console.warn('[update-check] failed:', e && e.message);
  }
}

/** 手动检查：无论是否有更新都弹窗（无更新时显示已是最新） */
export async function manualCheckUpdate() {
  try {
    const r = await api.updateRefresh();
    const d = (r && r.data) || {};
    if (d.hasUpdate) {
      showUpdateModal(d);
    } else {
      alert('已是最新版本：' + (d.current || '?') + (d.latest ? '（GitHub 最新 ' + d.latest + '）' : ''));
    }
  } catch (e) {
    alert('检查更新失败：' + (e.message || e));
  }
}
