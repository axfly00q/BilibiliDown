import { api, fmtBytes } from '/console/js/api.js';
import { applyI18n, injectLangSwitch, injectThemeSwitch, applyRoleNav, t } from '/console/js/i18n.js';

const $ = (id) => document.getElementById(id);
function escape(s){ return String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }

function renderCards(d) {
  const total = d.totals || {};
  const today = d.today || {};
  const week = d.week || {};
  const month = d.month || {};
  const active = d.active || {};
  const succPct = ((total.successRate || 0) * 100).toFixed(1) + '%';
  const cards = [
    { label: t('dash.cardActive', '当前活动'), num: active.count || 0, sub: fmtBytes(active.bytes || 0) },
    { label: t('dash.cardToday', '今日完成'), num: today.count || 0, sub: fmtBytes(today.bytes || 0) },
    { label: t('dash.cardWeek', '近 7 天'), num: week.count || 0, sub: fmtBytes(week.bytes || 0) },
    { label: t('dash.cardMonth', '近 30 天'), num: month.count || 0, sub: fmtBytes(month.bytes || 0) },
    { label: t('dash.cardHistory', '历史总数'), num: total.history || 0, sub: fmtBytes(total.bytes || 0) },
    { label: t('dash.cardSuccess', '成功率'), num: succPct, sub: (total.done||0) + ' / ' + ((total.done||0) + (total.fail||0)) },
  ];
  $('cards').innerHTML = cards.map(c => `
    <div class="card">
      <div class="label">${escape(c.label)}</div>
      <div class="num">${escape(String(c.num))}</div>
      <div class="sub">${escape(c.sub)}</div>
    </div>`).join('');
}

function renderBarChart(targetId, labels, values, formatVal) {
  const W = 1200, H = 200, padL = 40, padR = 10, padT = 10, padB = 30;
  const innerW = W - padL - padR;
  const innerH = H - padT - padB;
  const max = Math.max(1, ...values);
  const barGap = 4;
  const barW = (innerW - barGap * (values.length - 1)) / values.length;
  let svg = `<svg class="chart" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none" style="height:200px;">`;
  // y-axis (top max label)
  svg += `<line class="axis" x1="${padL}" y1="${padT}" x2="${padL}" y2="${H-padB}"/>`;
  svg += `<line class="axis" x1="${padL}" y1="${H-padB}" x2="${W-padR}" y2="${H-padB}"/>`;
  svg += `<text class="label" x="${padL-4}" y="${padT+10}" text-anchor="end">${escape(formatVal(max))}</text>`;
  svg += `<text class="label" x="${padL-4}" y="${H-padB}" text-anchor="end">0</text>`;
  values.forEach((v, i) => {
    const h = (v / max) * innerH;
    const x = padL + i * (barW + barGap);
    const y = H - padB - h;
    svg += `<rect class="bar" x="${x.toFixed(1)}" y="${y.toFixed(1)}" width="${barW.toFixed(1)}" height="${h.toFixed(1)}"><title>${escape(labels[i])}: ${escape(formatVal(v))}</title></rect>`;
    svg += `<text class="label" x="${(x + barW/2).toFixed(1)}" y="${H - padB + 14}" text-anchor="middle">${escape(labels[i])}</text>`;
  });
  svg += `</svg>`;
  $(targetId).innerHTML = svg;
}

function renderTop(top) {
  if (!top || top.length === 0) {
    $('top-list').innerHTML = `<div class="empty">${escape(t('dash.empty', '暂无数据'))}</div>`;
    return;
  }
  const rows = top.map((e, i) => `
    <tr>
      <td>${i + 1}</td>
      <td><a href="https://www.bilibili.com/video/${escape(e.avId)}" target="_blank" rel="noopener">${escape(e.avId)}</a></td>
      <td title="${escape(e.title || '')}">${escape((e.title || '').slice(0, 60))}</td>
      <td class="num">${e.count}</td>
      <td class="num">${escape(fmtBytes(e.bytes || 0))}</td>
    </tr>`).join('');
  $('top-list').innerHTML = `
    <table class="top-table">
      <thead><tr><th>#</th><th>avId</th><th>${escape(t('dash.colTitle','标题'))}</th><th class="num">${escape(t('dash.colCount','次数'))}</th><th class="num">${escape(t('dash.colBytes','流量'))}</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>`;
}

async function load() {
  try {
    const r = await api.statsSummary();
    const d = r.data || {};
    renderCards(d);
    const days = d.days14 || { labels: [], counts: [], bytes: [] };
    renderBarChart('chart-cnt', days.labels, days.counts, (v) => String(v));
    renderBarChart('chart-bytes', days.labels, days.bytes, fmtBytes);
    renderTop(d.topAv || []);
  } catch (e) {
    $('top-list').innerHTML = `<div class="empty">${escape(t('dash.loadFail','加载失败') + ': ' + (e.message || e))}</div>`;
  }
}

applyI18n();
injectLangSwitch('.topnav');
injectThemeSwitch('.topnav');
applyRoleNav();
load();
setInterval(load, 30000);
