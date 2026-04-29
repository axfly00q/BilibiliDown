package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.service.HistoryStore;

/**
 * 下载历史 + 解析历史的统一入口。
 *  GET    /api/history                          -> 下载历史列表
 *  DELETE /api/history/clear (POST 兼容)         -> 清空
 *  POST   /api/history/{key}/remove             -> 删除单条
 *  GET    /api/parse-history                    -> 解析历史列表
 *  POST   /api/parse-history/clear              -> 清空
 *  POST   /api/parse-history/{key}/remove       -> 删除单条
 */
@Controller(path = "/api", note = "历史记录")
public class ControllerHistory {

	private static final Pattern HISTORY_REMOVE = Pattern.compile("^/api/history/([^/]+)/remove$");
	private static final Pattern PARSE_HISTORY_REMOVE = Pattern.compile("^/api/parse-history/([^/]+)/remove$");

	@Controller(path = "/history", matchAll = false, note = "下载历史")
	public String history(BufferedWriter out, @Value(key = "pathData") String path) throws IOException {
		return dispatch(out, path, HistoryStore.DOWNLOAD, "/api/history");
	}

	@Controller(path = "/parse-history", matchAll = false, note = "解析历史")
	public String parseHistory(BufferedWriter out, @Value(key = "pathData") String path) throws IOException {
		return dispatch(out, path, HistoryStore.PARSE, "/api/parse-history");
	}

	private String dispatch(BufferedWriter out, String path, HistoryStore store, String prefix) throws IOException {
		if (path == null) path = prefix;
		if (path.equals(prefix)) {
			List<HistoryStore.Entry> all = store.list();
			StringBuilder sb = new StringBuilder();
			sb.append('[');
			boolean first = true;
			for (HistoryStore.Entry e : all) {
				if (!first) sb.append(',');
				first = false;
				sb.append(HistoryStore.toJson(e));
			}
			sb.append(']');
			ResponseUtil.writeJson(out, JsonUtil.okData(sb.toString()));
			return null;
		}
		if (path.equals(prefix + "/clear")) {
			store.clear();
			ResponseUtil.writeJson(out, JsonUtil.ok());
			return null;
		}
		Pattern pat = prefix.equals("/api/history") ? HISTORY_REMOVE : PARSE_HISTORY_REMOVE;
		Matcher m = pat.matcher(path);
		if (m.matches()) {
			String key;
			try { key = java.net.URLDecoder.decode(m.group(1), "UTF-8"); } catch (Exception e) { key = m.group(1); }
			boolean ok = store.remove(key);
			if (ok) ResponseUtil.writeJson(out, JsonUtil.ok());
			else ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "not found"));
			return null;
		}
		ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "unknown path: " + path));
		return null;
	}
}
