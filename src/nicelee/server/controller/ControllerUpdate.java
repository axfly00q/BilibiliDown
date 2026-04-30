package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;

import org.json.JSONObject;

import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.util.HttpRequestUtil;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.ui.Global;

/**
 * Web 控制台 - 在线更新检查。
 *  GET /api/update/check  →  { current, latest, hasUpdate, name, body, htmlUrl, publishedAt }
 *
 * 数据源默认 GitHub Releases API；可在 app.config 配置 bilibili.update.checkUrl 覆盖。
 * 返回的 JSON 结构需要兼容 GitHub: { tag_name, name, html_url, body, published_at }。
 *
 * 缓存：本地内存缓存 30 分钟（含失败结果），避免频繁打 GitHub。
 */
@Controller(path = "/api/update", note = "Web 控制台 - 在线更新检查")
public class ControllerUpdate {

	private static final String DEFAULT_URL = "https://api.github.com/repos/nICEnnnnnnnLee/BilibiliDown/releases/latest";
	private static final long CACHE_TTL_MS = 30L * 60L * 1000L; // 30 分钟

	private static volatile String cachedJson;       // 已渲染好的 data 部分
	private static volatile long cachedAt;
	private static final Object LOCK = new Object();

	@Controller(path = "/check", matchAll = true, note = "GET 检查更新")
	public String check(BufferedWriter out) throws IOException {
		try {
			String data = getOrFetch(false);
			ResponseUtil.writeJson(out, JsonUtil.okData(data));
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 502,
					JsonUtil.err(502, "检查更新失败: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
		}
		return null;
	}

	@Controller(path = "/refresh", matchAll = true, note = "GET 强制刷新缓存")
	public String refresh(BufferedWriter out) throws IOException {
		try {
			String data = getOrFetch(true);
			ResponseUtil.writeJson(out, JsonUtil.okData(data));
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 502,
					JsonUtil.err(502, "刷新失败: " + e.getMessage()));
		}
		return null;
	}

	private String getOrFetch(boolean force) throws Exception {
		long now = System.currentTimeMillis();
		if (!force && cachedJson != null && (now - cachedAt) < CACHE_TTL_MS) {
			return cachedJson;
		}
		synchronized (LOCK) {
			if (!force && cachedJson != null && (now - cachedAt) < CACHE_TTL_MS) {
				return cachedJson;
			}
			String url = Global.settings.getOrDefault("bilibili.update.checkUrl", DEFAULT_URL);
			if (url == null || url.isEmpty()) url = DEFAULT_URL;
			HashMap<String, String> headers = new HashMap<>();
			headers.put("Accept", "application/vnd.github+json");
			headers.put("User-Agent", "BilibiliDown-UpdateChecker");
			String body = new HttpRequestUtil().getContent(url, headers);
			if (body == null || body.isEmpty()) throw new RuntimeException("空响应");
			JSONObject j = new JSONObject(body);
			String latest = j.optString("tag_name", "");
			String name = j.optString("name", latest);
			String html = j.optString("html_url", "");
			String notes = j.optString("body", "");
			String pub = j.optString("published_at", "");
			String current = nv(Global.version);
			boolean hasUpdate = !latest.isEmpty() && compareVer(latest, current) > 0;
			StringBuilder sb = new StringBuilder("{");
			sb.append(JsonUtil.kv("current", current)).append(',')
					.append(JsonUtil.kv("latest", latest)).append(',')
					.append(JsonUtil.kvB("hasUpdate", hasUpdate)).append(',')
					.append(JsonUtil.kv("name", name)).append(',')
					.append(JsonUtil.kv("body", notes)).append(',')
					.append(JsonUtil.kv("htmlUrl", html)).append(',')
					.append(JsonUtil.kv("publishedAt", pub))
					.append('}');
			cachedJson = sb.toString();
			cachedAt = now;
			return cachedJson;
		}
	}

	private static String nv(String s) { return s == null ? "" : s; }

	/** 简单的 vX.Y.Z 比较：数字段按数字比较；忽略前缀 v/V。返回 a-b 符号。 */
	static int compareVer(String a, String b) {
		String[] sa = stripV(a).split("[.\\-_]+");
		String[] sb = stripV(b).split("[.\\-_]+");
		int len = Math.max(sa.length, sb.length);
		for (int i = 0; i < len; i++) {
			long va = numPart(i < sa.length ? sa[i] : "0");
			long vb = numPart(i < sb.length ? sb[i] : "0");
			if (va != vb) return Long.compare(va, vb);
		}
		return 0;
	}

	private static String stripV(String s) {
		if (s == null) return "";
		s = s.trim();
		if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
		return s;
	}

	private static long numPart(String s) {
		StringBuilder n = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= '0' && c <= '9') n.append(c);
			else break;
		}
		if (n.length() == 0) return 0;
		try { return Long.parseLong(n.toString()); } catch (Exception e) { return 0; }
	}
}
