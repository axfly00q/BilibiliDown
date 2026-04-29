// 简易 i18n：根据 localStorage 'bd-lang' 或浏览器语言切换；只覆盖 Web 控制台页面常用词条。
const DICT = {
  'zh-CN': {
    'nav.home': '首页', 'nav.parse': '解析', 'nav.downloads': '下载任务',
    'nav.settings': '设置', 'nav.account': 'B 站账号', 'nav.logout': '登出控制台',
    'common.loading': '加载中...', 'common.save': '保存', 'common.cancel': '取消',
    'common.refresh': '刷新', 'common.empty': '暂无数据',
    'parse.placeholder': '输入 av 号 / BV 号 / 视频 URL，按 Enter 解析',
    'parse.btn': '解析', 'parse.add': '加入下载', 'parse.cover': '保存封面',
    'downloads.resumeAll': '全部继续', 'downloads.pauseAll': '全部暂停', 'downloads.removeDone': '清除已完成',
    'downloads.col.title': '标题', 'downloads.col.qn': '清晰度', 'downloads.col.progress': '进度',
    'downloads.col.size': '大小', 'downloads.col.status': '状态', 'downloads.col.ops': '操作',
    'downloads.empty': '暂无下载任务',
    'settings.reload': '重新读取', 'settings.save': '保存修改',
    'settings.picker.title': '选择下载保存目录', 'settings.picker.up': '↑ 上一级',
    'settings.picker.mkdir': '+ 新建文件夹', 'settings.picker.choose': '选择此目录',
    'downloads.saveDir': '下载目录：',
    'nav.history': '历史记录',
    'login.guest': '以游客身份进入',
    'login.guestNote': '游客模式不保存历史记录',
    'downloads.openDir': '打开目录',
    'downloads.openFile': '打开',
    'history.title': '历史记录',
    'history.tab.download': '下载历史', 'history.tab.parse': '解析历史',
    'history.empty': '暂无历史', 'history.clear': '清空',
    'history.col.title': '标题', 'history.col.path': '路径',
    'history.col.size': '大小', 'history.col.time': '时间',
    'history.col.input': '输入', 'history.col.action': '操作',
    'parse.history': '最近解析',
    'common.delete': '删除',
    'account.title.status': '登录状态', 'account.title.qr': '扫码登录', 'account.title.cookie': 'Cookie 工具',
    'account.loading': '加载中...', 'account.notLogin': '尚未登录 B 站账号',
    'account.login': '扫码登录', 'account.logout': '退出 B 站登录',
    'account.logout.confirm': '确认退出 B 站登录？', 'account.loggedOut': '已退出',
    'account.qr.tip': '请使用手机 B 站 App 扫描二维码', 'account.qr.refresh': '刷新二维码',
    'account.qr.loading': '正在生成二维码...', 'account.qr.expired': '二维码已失效，请刷新',
    'account.loginOk': '登录成功',
    'account.cookie.refresh': '刷新 Cookie', 'account.cookie.refreshing': '刷新中...',
    'account.cookie.started': '刷新已触发，请稍候查看日志',
    'account.cookie.note': '若长期未登录或 Cookie 即将过期，可点此刷新。需要本机能正常访问 B 站。',
    'theme.light': '浅色', 'theme.auto': '自动', 'theme.dark': '暗色',
    'login.title': 'BilibiliDown 控制台登录', 'login.username': '用户名', 'login.password': '密码', 'login.submit': '登录', 'login.fail': '登录失败',
    'page.console.title': 'BilibiliDown 控制台',
    'header.subtitle.console': 'Web 控制台',
    'a11y.themeToggle': '主题切换',
    'console.title': 'BilibiliDown 控制台',
    'console.intro': '选择下面的功能卡片进入对应页面。',
    'console.footer': '项目开源 · 本控制台与桌面主程共用状态',
    'console.entry.geetest.title': '极验验证',
    'console.entry.geetest.desc': '打开 B 站验证流程页面，用于调试。',
    'console.entry.cookie.title': 'Cookie 刷新',
    'console.entry.cookie.desc': '打开 Cookie 刷新页面（需先登录）。',
    'console.entry.docs.title': '项目文档',
    'console.entry.docs.desc': '查看使用文档与 Wiki。',
    'console.entry.github.title': 'GitHub 仓库',
    'console.entry.github.desc': '访问源代码仓库、提 issue。',
  },
  'en-US': {
    'nav.home': 'Home', 'nav.parse': 'Parse', 'nav.downloads': 'Downloads',
    'nav.settings': 'Settings', 'nav.account': 'Bilibili Account', 'nav.logout': 'Sign out',
    'common.loading': 'Loading...', 'common.save': 'Save', 'common.cancel': 'Cancel',
    'common.refresh': 'Refresh', 'common.empty': 'No data',
    'parse.placeholder': 'Enter av/BV id or video URL, press Enter to parse',
    'parse.btn': 'Parse', 'parse.add': 'Add to queue', 'parse.cover': 'Save cover',
    'downloads.resumeAll': 'Resume all', 'downloads.pauseAll': 'Pause all', 'downloads.removeDone': 'Clear done',
    'downloads.col.title': 'Title', 'downloads.col.qn': 'Quality', 'downloads.col.progress': 'Progress',
    'downloads.col.size': 'Size', 'downloads.col.status': 'Status', 'downloads.col.ops': 'Actions',
    'downloads.empty': 'No tasks',
    'settings.reload': 'Reload', 'settings.save': 'Save changes',
    'settings.picker.title': 'Pick download folder', 'settings.picker.up': '↑ Up',
    'settings.picker.mkdir': '+ New folder', 'settings.picker.choose': 'Choose this folder',
    'downloads.saveDir': 'Download folder: ',
    'nav.history': 'History',
    'login.guest': 'Continue as guest',
    'login.guestNote': 'Guest mode does not save history',
    'downloads.openDir': 'Open folder',
    'downloads.openFile': 'Open',
    'history.title': 'History',
    'history.tab.download': 'Downloads', 'history.tab.parse': 'Parses',
    'history.empty': 'No history', 'history.clear': 'Clear',
    'history.col.title': 'Title', 'history.col.path': 'Path',
    'history.col.size': 'Size', 'history.col.time': 'Time',
    'history.col.input': 'Input', 'history.col.action': 'Action',
    'parse.history': 'Recent parses',
    'common.delete': 'Delete',
    'account.title.status': 'Login status', 'account.title.qr': 'QR Login', 'account.title.cookie': 'Cookie tools',
    'account.loading': 'Loading...', 'account.notLogin': 'Not logged in to Bilibili',
    'account.login': 'Scan to log in', 'account.logout': 'Log out',
    'account.logout.confirm': 'Log out of Bilibili?', 'account.loggedOut': 'Logged out',
    'account.qr.tip': 'Scan with the Bilibili mobile app', 'account.qr.refresh': 'Refresh QR',
    'account.qr.loading': 'Generating QR code...', 'account.qr.expired': 'QR expired, refresh please',
    'account.loginOk': 'Login successful',
    'account.cookie.refresh': 'Refresh cookies', 'account.cookie.refreshing': 'Refreshing...',
    'account.cookie.started': 'Refresh triggered, see logs for details',
    'account.cookie.note': 'Use this if not logged in for a long time or cookies are about to expire.',
    'theme.light': 'Light', 'theme.auto': 'Auto', 'theme.dark': 'Dark',
    'login.title': 'BilibiliDown Console Login', 'login.username': 'Username', 'login.password': 'Password', 'login.submit': 'Sign in', 'login.fail': 'Login failed',
    'page.console.title': 'BilibiliDown Console',
    'header.subtitle.console': 'Web Console',
    'a11y.themeToggle': 'Theme switch',
    'console.title': 'BilibiliDown Console',
    'console.intro': 'Pick a card below to open the corresponding page.',
    'console.footer': 'Open source · Console shares state with the desktop app',
    'console.entry.geetest.title': 'Geetest validation',
    'console.entry.geetest.desc': 'Open the Bilibili captcha flow page (debug).',
    'console.entry.cookie.title': 'Cookie refresh',
    'console.entry.cookie.desc': 'Open the Cookie refresh page (login required).',
    'console.entry.docs.title': 'Documentation',
    'console.entry.docs.desc': 'View the user manual and Wiki.',
    'console.entry.github.title': 'GitHub repo',
    'console.entry.github.desc': 'Visit the source repository, file issues.',
  },
  'ja-JP': {
    'nav.home': 'ホーム', 'nav.parse': '解析', 'nav.downloads': 'ダウンロード',
    'nav.settings': '設定', 'nav.account': 'Bilibili アカウント', 'nav.logout': 'サインアウト',
    'common.loading': '読み込み中...', 'common.save': '保存', 'common.cancel': 'キャンセル',
    'common.refresh': '更新', 'common.empty': 'データなし',
    'parse.placeholder': 'av/BV ID または URL を入力し Enter',
    'parse.btn': '解析', 'parse.add': 'キューに追加', 'parse.cover': 'カバー保存',
    'downloads.resumeAll': '全て再開', 'downloads.pauseAll': '全て一時停止', 'downloads.removeDone': '完了を削除',
    'downloads.col.title': 'タイトル', 'downloads.col.qn': '画質', 'downloads.col.progress': '進捗',
    'downloads.col.size': 'サイズ', 'downloads.col.status': '状態', 'downloads.col.ops': '操作',
    'downloads.empty': 'タスクなし',
    'settings.reload': '再読み込み', 'settings.save': '変更を保存',
    'settings.picker.title': 'ダウンロード先を選択', 'settings.picker.up': '↑ 上へ',
    'settings.picker.mkdir': '+ 新規フォルダ', 'settings.picker.choose': 'このフォルダを選択',
    'downloads.saveDir': '保存先：',
    'nav.history': '履歴',
    'login.guest': 'ゲストとして進む',
    'login.guestNote': 'ゲストモードでは履歴は保存されません',
    'downloads.openDir': 'フォルダを開く',
    'downloads.openFile': '開く',
    'history.title': '履歴',
    'history.tab.download': 'ダウンロード', 'history.tab.parse': '解析',
    'history.empty': '履歴なし', 'history.clear': 'クリア',
    'history.col.title': 'タイトル', 'history.col.path': 'パス',
    'history.col.size': 'サイズ', 'history.col.time': '日時',
    'history.col.input': '入力', 'history.col.action': '操作',
    'parse.history': '最近の解析',
    'common.delete': '削除',
    'account.title.status': 'ログイン状態', 'account.title.qr': 'QR ログイン', 'account.title.cookie': 'Cookie ツール',
    'account.loading': '読み込み中...', 'account.notLogin': '未ログイン',
    'account.login': 'QR ログイン', 'account.logout': 'ログアウト',
    'account.logout.confirm': 'ログアウトしますか？', 'account.loggedOut': 'ログアウトしました',
    'account.qr.tip': 'Bilibili アプリで QR コードを読み取ってください', 'account.qr.refresh': 'QR を更新',
    'account.qr.loading': 'QR コード生成中...', 'account.qr.expired': 'QR コードが期限切れです',
    'account.loginOk': 'ログイン成功',
    'account.cookie.refresh': 'Cookie 更新', 'account.cookie.refreshing': '更新中...',
    'account.cookie.started': '更新を開始しました（詳細はログを参照）',
    'account.cookie.note': '長時間ログインしていない場合や Cookie が失効しそうな場合に使用します。',
    'theme.light': 'ライト', 'theme.auto': '自動', 'theme.dark': 'ダーク',
    'login.title': 'BilibiliDown コンソールログイン', 'login.username': 'ユーザー名', 'login.password': 'パスワード', 'login.submit': 'サインイン', 'login.fail': 'ログイン失敗',
    'page.console.title': 'BilibiliDown コンソール',
    'header.subtitle.console': 'Web コンソール',
    'a11y.themeToggle': 'テーマ切替',
    'console.title': 'BilibiliDown コンソール',
    'console.intro': '以下のカードを選んで各ページを開きます。',
    'console.footer': 'オープンソース · コンソールとデスクトップで状態共有',
    'console.entry.geetest.title': 'ジーテスト認証',
    'console.entry.geetest.desc': 'Bilibili 認証フローページを開く（デバッグ用）。',
    'console.entry.cookie.title': 'Cookie 更新',
    'console.entry.cookie.desc': 'Cookie 更新ページを開く（要ログイン）。',
    'console.entry.docs.title': 'ドキュメント',
    'console.entry.docs.desc': 'ユーザーマニュアルと Wiki を表示。',
    'console.entry.github.title': 'GitHub リポジトリ',
    'console.entry.github.desc': 'ソースリポジトリ、Issue 提出。',
  },
};

function detectLang() {
  const stored = (typeof localStorage !== 'undefined') && localStorage.getItem('bd-lang');
  if (stored && DICT[stored]) return stored;
  const nav = (navigator.language || 'zh-CN').toLowerCase();
  if (nav.startsWith('en')) return 'en-US';
  if (nav.startsWith('ja')) return 'ja-JP';
  return 'zh-CN';
}

let curLang = detectLang();
// 自动应用已保存的主题（所有导入此模块的页面均生效）
(function () {
  try {
    const v = localStorage.getItem('bd-theme');
    if (v === 'light' || v === 'dark') document.documentElement.setAttribute('data-theme', v);
    else document.documentElement.removeAttribute('data-theme');
  } catch (_) {}
})();
export function getLang() { return curLang; }
export function setLang(l) {
  if (!DICT[l]) return;
  curLang = l;
  try { localStorage.setItem('bd-lang', l); } catch {}
  applyI18n();
}
export function t(key, fallback) {
  const v = DICT[curLang] && DICT[curLang][key];
  if (v != null) return v;
  if (fallback != null) return fallback;
  return DICT['zh-CN'][key] || key;
}
export function applyI18n(root) {
  const r = root || document;
  r.querySelectorAll('[data-i18n]').forEach(el => {
    const key = el.getAttribute('data-i18n');
    const v = t(key, null);
    if (v != null) el.textContent = v;
  });
  r.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
    const key = el.getAttribute('data-i18n-placeholder');
    const v = t(key, null);
    if (v != null) el.setAttribute('placeholder', v);
  });
  r.querySelectorAll('[data-i18n-attr]').forEach(el => {
    const spec = el.getAttribute('data-i18n-attr');
    spec.split(';').forEach(pair => {
      const [attr, key] = pair.split(':').map(s => s && s.trim());
      if (attr && key) {
        const v = t(key, null);
        if (v != null) el.setAttribute(attr, v);
      }
    });
  });
}

// 隐藏仅注册用户可见的导航项（[data-nav-role="user"]），用于游客模式。
// role 来自 /api/auth/me，缓存到 sessionStorage，避免重复请求。
let _roleCache = null;
export async function getRole() {
  // 旧版以 /api/auth/me 区分 user/guest，已弃用，统一返回 'user'
  return 'user';
}
export async function applyRoleNav() {
  // 检查 B 站登录态：未登录时给 data-nav-role="bili" 的链接加灰阶提示（不隐藏，方便用户进入页面看到提示）
  let biliLogin = false;
  try {
    const r = await fetch('/api/account/status', { credentials: 'same-origin' });
    if (r.ok) {
      const j = await r.json();
      biliLogin = !!(j && j.data && j.data.login);
    }
  } catch {}
  if (!biliLogin) {
    document.querySelectorAll('[data-nav-role="bili"]').forEach(el => {
      el.title = '需要登录 B 站账号';
      el.style.opacity = '0.55';
    });
  }
}

// 注入语言切换器到 .topnav / .topbar 末尾
export function injectLangSwitch(targetSelector) {
  const target = document.querySelector(targetSelector || '.topnav');
  if (!target || target.querySelector('.lang-switch')) return;
  const sel = document.createElement('select');
  sel.className = 'lang-switch';
  sel.style.cssText = 'margin-left:8px;padding:4px 6px;border:1px solid var(--border);background:var(--surface);color:var(--text);border-radius:4px;font-size:12px;';
  [['zh-CN','中文'],['en-US','English'],['ja-JP','日本語']].forEach(([v,l])=>{
    const o = document.createElement('option'); o.value=v; o.textContent=l;
    if (v === curLang) o.selected = true;
    sel.appendChild(o);
  });
  sel.onchange = () => setLang(sel.value);
  target.appendChild(sel);
}

// 注入主题切换下拉到 .topnav / .topbar 末尾（与语言切换并排）
export function injectThemeSwitch(targetSelector) {
  const target = document.querySelector(targetSelector || '.topnav');
  if (!target || target.querySelector('.theme-switch-sel')) return;
  const THEMES = [['light', t('theme.light', '浅色')], ['auto', t('theme.auto', '自动')], ['dark', t('theme.dark', '暗色')]];
  const sel = document.createElement('select');
  sel.className = 'theme-switch-sel';
  sel.style.cssText = 'margin-left:4px;padding:4px 6px;border:1px solid var(--border);background:var(--surface);color:var(--text);border-radius:4px;font-size:12px;';
  const cur = (() => { try { return localStorage.getItem('bd-theme') || 'auto'; } catch { return 'auto'; } })();
  THEMES.forEach(([v, label]) => {
    const o = document.createElement('option');
    o.value = v; o.textContent = label;
    if (v === cur) o.selected = true;
    sel.appendChild(o);
  });
  sel.onchange = () => {
    const v = sel.value;
    try { localStorage.setItem('bd-theme', v); } catch (_) {}
    if (v === 'light' || v === 'dark') document.documentElement.setAttribute('data-theme', v);
    else document.documentElement.removeAttribute('data-theme');
  };
  target.appendChild(sel);
}
