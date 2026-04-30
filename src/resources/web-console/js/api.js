// 公共 API 客户端
async function request(method, url, body) {
  const opts = { method, credentials: 'same-origin', headers: {} };
  if (body !== undefined) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = typeof body === 'string' ? body : JSON.stringify(body);
  }
  const res = await fetch(url, opts);
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : {}; } catch { data = { raw: text }; }
  if (res.status === 401) {
    // 未登录 B 站。不再强制跳转上下文 / 废弃的控制台登录页，调用方自行提示。
    const err = new Error('unauthorized');
    err.status = 401;
    err.payload = data;
    throw err;
  }
  if (!res.ok) {
    throw new Error((data && data.message) ? data.message : ('HTTP ' + res.status));
  }
  return data;
}

export const api = {
  parse: (input) => request('GET', '/api/parse?input=' + encodeURIComponent(input)),
  qualityList: () => request('GET', '/api/quality/list'),
  taskList: () => request('GET', '/api/download/list'),
  submit: (avId, cid, qn) => request('POST', '/api/download/submit', { avId, cid: String(cid), qn: String(qn) }),
  pause: (id) => request('POST', '/api/download/' + encodeURIComponent(id) + '/pause'),
  resume: (id) => request('POST', '/api/download/' + encodeURIComponent(id) + '/resume'),
  remove: (id) => request('POST', '/api/download/' + encodeURIComponent(id) + '/remove'),
  pauseAll: () => request('POST', '/api/download/all/pause'),
  resumeAll: () => request('POST', '/api/download/all/resume'),
  removeDone: () => request('POST', '/api/download/all/done'),
  saveCover: (avId, cid) => request('GET', '/api/cover/save?avId=' + encodeURIComponent(avId) + (cid ? '&cid=' + encodeURIComponent(cid) : '')),
  settingsList: () => request('GET', '/api/settings/list'),
  settingsSave: (kv) => request('POST', '/api/settings/save', kv),
  accountStatus: () => request('GET', '/api/account/status'),
  accountQrStart: () => request('POST', '/api/account/qr/start', {}),
  accountQrPoll: (key) => request('GET', '/api/account/qr/poll?key=' + encodeURIComponent(key)),
  accountLogout: () => request('POST', '/api/account/logout', {}),
  accountCookieRefresh: () => request('POST', '/api/account/cookie/refresh', {}),
  fsSavePath: () => request('GET', '/api/fs/savePath'),
  fsList: (path) => request('GET', '/api/fs/list' + (path ? ('?path=' + encodeURIComponent(path)) : '')),
  fsMkdir: (path, name) => request('POST', '/api/fs/mkdir', { path, name }),
  fsOpen: (path) => request('GET', '/api/fs/open' + (path ? ('?path=' + encodeURIComponent(path)) : '')),
  history: () => request('GET', '/api/history'),
  historyClear: () => request('POST', '/api/history/clear', {}),
  historyRemove: (key) => request('POST', '/api/history/' + encodeURIComponent(key) + '/remove', {}),
  parseHistory: () => request('GET', '/api/parse-history'),
  parseHistoryClear: () => request('POST', '/api/parse-history/clear', {}),
  parseHistoryRemove: (key) => request('POST', '/api/parse-history/' + encodeURIComponent(key) + '/remove', {}),
  favList: () => request('GET', '/api/fav/list'),
  favItems: (id) => request('GET', '/api/fav/' + encodeURIComponent(id) + '/items'),
  favSubmitAll: (id, qn) => request('POST', '/api/fav/' + encodeURIComponent(id) + '/submitAll', { qn: String(qn) }),
  updateCheck: () => request('GET', '/api/update/check'),
  updateRefresh: () => request('GET', '/api/update/refresh'),
  logsTail: (since, level, limit) => {
    const qs = new URLSearchParams();
    if (since != null) qs.set('since', String(since));
    if (level) qs.set('level', level);
    if (limit) qs.set('limit', String(limit));
    const s = qs.toString();
    return request('GET', '/api/logs' + (s ? ('?' + s) : ''));
  },
  logsSeq: () => request('GET', '/api/logs/seq'),
};

export function fmtBytes(b) {
  if (b == null || isNaN(b)) return '-';
  const u = ['B','KB','MB','GB','TB'];
  let i = 0; let v = Number(b);
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
  return v.toFixed(v >= 100 ? 0 : v >= 10 ? 1 : 2) + ' ' + u[i];
}

export function statusLabel(s) {
  return ({
    active: '下载中', paused: '已暂停', done: '已完成', fail: '失败',
    queued: '队列中', processing: '转码中', none: '未开始'
  })[s] || s || '-';
}
