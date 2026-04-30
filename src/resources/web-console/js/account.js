import { api } from '/console/js/api.js';
import { applyI18n, injectLangSwitch, injectThemeSwitch, t, applyRoleNav } from '/console/js/i18n.js';

const $ = (id) => document.getElementById(id);
function escape(s){ return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }
function toast(msg){ const e=$('toast'); e.textContent=msg; e.classList.add('show'); setTimeout(()=>e.classList.remove('show'), 4000); }

let pollTimer = null;
let curKey = null;

async function refreshStatus() {
  try {
    const r = await api.accountStatus();
    const d = r.data || {};
    const body = $('status-body');
    if (d.login) {
      body.innerHTML = `
        <img class="face" src="${escape(d.face || '')}" referrerpolicy="no-referrer" alt="">
        <div>
          <div class="uname">${escape(d.uname || '')}</div>
          <div class="uid">UID: ${escape(d.uid || '')}</div>
        </div>
        <span style="flex:1"></span>
        <button class="danger" id="bili-logout">${t('account.logout', '退出 B 站登录')}</button>`;
      $('qr-card').hidden = true;
      $('cookie-card').hidden = false;
      $('bili-logout').onclick = doBiliLogout;
    } else {
      body.innerHTML = `<span>${t('account.notLogin', '尚未登录 B 站账号')}</span>
        <span style="flex:1"></span>
        <button class="primary" id="bili-login">${t('account.login', '扫码登录')}</button>`;
      $('cookie-card').hidden = true;
      $('bili-login').onclick = startQr;
    }
  } catch (e) {
    $('err').textContent = e.message || 'failed';
  }
}

async function startQr() {
  $('err').textContent = '';
  stopPoll();
  $('qr-card').hidden = false;
  $('qr-status').textContent = t('account.qr.loading', '正在生成二维码...');
  try {
    const r = await api.accountQrStart();
    const d = r.data || {};
    curKey = d.key;
    $('qr-img').src = d.qrPng;
    $('qr-status').textContent = t('account.qr.tip', '请使用手机 B 站 App 扫描二维码');
    pollTimer = setInterval(pollOnce, 2000);
  } catch (e) {
    $('qr-status').textContent = '';
    $('err').textContent = e.message || 'failed';
  }
}

async function pollOnce() {
  if (!curKey) return;
  try {
    const r = await api.accountQrPoll(curKey);
    const d = r.data || {};
    if (d.status === 'ok') {
      stopPoll();
      toast(t('account.loginOk', '登录成功'));
      refreshStatus();
    }
  } catch (e) {
    // 404 = 二维码过期；停止
    if (/404/.test(e.message || '')) {
      stopPoll();
      $('qr-status').textContent = t('account.qr.expired', '二维码已失效，请刷新');
    }
  }
}

function stopPoll() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
}

async function doBiliLogout() {
  if (!confirm(t('account.logout.confirm', '确认退出 B 站登录？'))) return;
  try {
    await api.accountLogout();
    toast(t('account.loggedOut', '已退出'));
    refreshStatus();
  } catch (e) {
    $('err').textContent = e.message || 'failed';
  }
}

async function doCookieRefresh() {
  $('cookie-status').textContent = t('account.cookie.refreshing', '刷新中...');
  try {
    await api.accountCookieRefresh();
    toast(t('account.cookie.started', '刷新已触发，请稍候查看日志'));
    $('cookie-status').textContent = t('account.cookie.started', '刷新已触发');
  } catch (e) {
    $('cookie-status').textContent = '';
    $('err').textContent = e.message || 'failed';
  }
}

$('qr-refresh').onclick = startQr;
$('cookie-refresh').onclick = doCookieRefresh;
// 控制台登出按钮已移除
applyI18n();injectLangSwitch('.topnav');
injectThemeSwitch('.topnav');applyRoleNav();
refreshStatus();
