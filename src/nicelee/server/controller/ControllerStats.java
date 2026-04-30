package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nicelee.bilibili.annotations.Controller;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.service.DownloadService;
import nicelee.service.HistoryStore;
import nicelee.service.TaskSnapshot;

/**
 * 仪表盘统计接口。
 * 数据全部来自 HistoryStore.DOWNLOAD（历史已完成/失败）+ DownloadService.list()（当前队列）。
 */
@Controller(path = "/api/stats", note = "统计")
public class ControllerStats {

	@Controller(path = "/summary", note = "GET /api/stats/summary")
	public String summary(BufferedWriter out) throws IOException {
		List<HistoryStore.Entry> hist = HistoryStore.DOWNLOAD.list();
		List<TaskSnapshot> active = DownloadService.list();

		long now = System.currentTimeMillis();
		long todayStart = startOfDay(now, 0);
		long weekStart = startOfDay(now, -6);   // 含今天，共 7 天
		long monthStart = startOfDay(now, -29); // 含今天，共 30 天

		long todayCnt = 0, weekCnt = 0, monthCnt = 0, totalCnt = 0;
		long todayBytes = 0, weekBytes = 0, monthBytes = 0, totalBytes = 0;
		long doneCnt = 0, failCnt = 0;

		// 按 UP 主聚合（用 title 前缀做不到，HistoryStore 里没有 author 字段；
		// 这里改为按 avId 分布；前端会展示 Top 10 avId）
		// 同时按"日期"做柱状图：最近 14 天
		long[] days14Cnt = new long[14];
		long[] days14Bytes = new long[14];
		Map<String, long[]> byAv = new HashMap<>(); // [count, bytes]

		for (HistoryStore.Entry e : hist) {
			totalCnt++;
			long size = e.size != null ? e.size : 0L;
			totalBytes += size;
			if ("done".equals(e.status)) doneCnt++;
			else if ("fail".equals(e.status)) failCnt++;
			if (e.ts >= todayStart) { todayCnt++; todayBytes += size; }
			if (e.ts >= weekStart) { weekCnt++; weekBytes += size; }
			if (e.ts >= monthStart) { monthCnt++; monthBytes += size; }

			// 14 天分桶：index 0=13天前 ... index 13=今天
			long dayDiff = (todayStart - startOfDay(e.ts, 0)) / 86400000L;
			if (dayDiff >= 0 && dayDiff < 14) {
				int idx = 13 - (int) dayDiff;
				days14Cnt[idx]++;
				days14Bytes[idx] += size;
			}

			if (e.avId != null && !e.avId.isEmpty()) {
				long[] agg = byAv.computeIfAbsent(e.avId, k -> new long[2]);
				agg[0]++;
				agg[1] += size;
			}
		}

		// 当前进行中
		long activeCnt = 0, activeBytes = 0;
		for (TaskSnapshot t : active) {
			if ("active".equals(t.status) || "processing".equals(t.status)) {
				activeCnt++;
				activeBytes += t.currentDown;
			}
		}

		double successRate = (doneCnt + failCnt) == 0 ? 1.0 : (double) doneCnt / (doneCnt + failCnt);

		// Top 10 avId
		List<Map.Entry<String, long[]>> topList = new ArrayList<>(byAv.entrySet());
		topList.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
		if (topList.size() > 10) topList = topList.subList(0, 10);

		// 拼 JSON
		StringBuilder sb = new StringBuilder();
		sb.append('{');
		sb.append("\"totals\":{")
			.append("\"history\":").append(totalCnt).append(',')
			.append("\"done\":").append(doneCnt).append(',')
			.append("\"fail\":").append(failCnt).append(',')
			.append("\"bytes\":").append(totalBytes).append(',')
			.append("\"successRate\":").append(String.format("%.4f", successRate))
			.append("},");
		sb.append("\"today\":{").append("\"count\":").append(todayCnt).append(',').append("\"bytes\":").append(todayBytes).append("},");
		sb.append("\"week\":{").append("\"count\":").append(weekCnt).append(',').append("\"bytes\":").append(weekBytes).append("},");
		sb.append("\"month\":{").append("\"count\":").append(monthCnt).append(',').append("\"bytes\":").append(monthBytes).append("},");
		sb.append("\"active\":{").append("\"count\":").append(activeCnt).append(',').append("\"bytes\":").append(activeBytes).append("},");
		// 14 天序列
		sb.append("\"days14\":{\"labels\":[");
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(todayStart);
		cal.add(Calendar.DAY_OF_MONTH, -13);
		boolean firstD = true;
		for (int i = 0; i < 14; i++) {
			if (!firstD) sb.append(',');
			firstD = false;
			sb.append('"').append(String.format("%02d-%02d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))).append('"');
			cal.add(Calendar.DAY_OF_MONTH, 1);
		}
		sb.append("],\"counts\":[");
		for (int i = 0; i < 14; i++) { if (i > 0) sb.append(','); sb.append(days14Cnt[i]); }
		sb.append("],\"bytes\":[");
		for (int i = 0; i < 14; i++) { if (i > 0) sb.append(','); sb.append(days14Bytes[i]); }
		sb.append("]},");
		// Top
		sb.append("\"topAv\":[");
		boolean firstT = true;
		for (Map.Entry<String, long[]> e : topList) {
			if (!firstT) sb.append(',');
			firstT = false;
			// 找一个 title 作为展示
			String title = "";
			for (HistoryStore.Entry h : hist) {
				if (e.getKey().equals(h.avId)) { title = h.title != null ? h.title : ""; break; }
			}
			sb.append('{')
				.append(JsonUtil.kv("avId", e.getKey())).append(',')
				.append(JsonUtil.kv("title", title)).append(',')
				.append(JsonUtil.kvN("count", e.getValue()[0])).append(',')
				.append(JsonUtil.kvN("bytes", e.getValue()[1]))
				.append('}');
		}
		sb.append(']');
		sb.append('}');

		ResponseUtil.writeJson(out, JsonUtil.okData(sb.toString()));
		return null;
	}

	private static long startOfDay(long ts, int dayOffset) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(ts);
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		c.add(Calendar.DAY_OF_MONTH, dayOffset);
		return c.getTimeInMillis();
	}

	// 抑制 unused import 警告
	@SuppressWarnings("unused") private void __keepImports(Comparator<?> c) {}
}
