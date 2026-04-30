package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.LogRingBuffer;
import nicelee.server.util.ResponseUtil;

/**
 * Web 控制台 - 实时日志查看
 *  GET /api/logs?since=<seq>&level=<INFO|ERROR>&limit=<n>
 *      → { code:0, data:{ since, nextSince, lines:[{seq,ts,level,text}...] } }
 *  GET /api/logs/seq        → { code:0, data:{ seq:<currentMaxSeq> } }
 */
@Controller(path = "/api/logs", note = "Web 控制台 - 实时日志")
public class ControllerLogs {

	@Controller(path = "", matchAll = true, note = "增量拉取日志")
	public String list(BufferedWriter out,
			@Value(key = "paramData") String params) throws IOException {
		long since = parseLong(params, "since", 0);
		int limit = (int) Math.min(500, Math.max(1, parseLong(params, "limit", 200)));
		String level = parseString(params, "level", "");

		List<LogRingBuffer.Entry> lines = LogRingBuffer.tail(since, level, limit);
		long maxSeq = since;
		StringBuilder sb = new StringBuilder(1024 + lines.size() * 80);
		sb.append("{").append(JsonUtil.kvN("since", since))
				.append(',').append("\"installed\":").append(LogRingBuffer.isInstalled())
				.append(',').append("\"lines\":[");
		for (int i = 0; i < lines.size(); i++) {
			LogRingBuffer.Entry e = lines.get(i);
			if (e.seq > maxSeq) maxSeq = e.seq;
			if (i > 0) sb.append(',');
			sb.append('{')
					.append(JsonUtil.kvN("seq", e.seq)).append(',')
					.append(JsonUtil.kvN("ts", e.ts)).append(',')
					.append(JsonUtil.kv("level", e.level)).append(',')
					.append(JsonUtil.kv("text", e.text))
					.append('}');
		}
		sb.append("],").append(JsonUtil.kvN("nextSince", maxSeq)).append('}');
		ResponseUtil.writeJson(out, JsonUtil.okData(sb.toString()));
		return null;
	}

	@Controller(path = "/seq", matchAll = true, note = "查询当前最大 seq")
	public String seq(BufferedWriter out) throws IOException {
		ResponseUtil.writeJson(out,
				JsonUtil.okData("{" + JsonUtil.kvN("seq", LogRingBuffer.currentSeq()) + "}"));
		return null;
	}

	private static long parseLong(String params, String key, long def) {
		String v = parseString(params, key, null);
		if (v == null) return def;
		try { return Long.parseLong(v); } catch (Exception e) { return def; }
	}

	private static String parseString(String params, String key, String def) {
		if (params == null || params.isEmpty()) return def;
		for (String pair : params.split("&")) {
			int eq = pair.indexOf('=');
			if (eq <= 0) continue;
			if (key.equals(pair.substring(0, eq))) {
				try { return java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8"); }
				catch (Exception e) { return pair.substring(eq + 1); }
			}
		}
		return def;
	}
}
