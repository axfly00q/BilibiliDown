package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import nicelee.bilibili.INeedLogin;
import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.util.HttpCookies;
import nicelee.bilibili.util.HttpHeaders;
import nicelee.bilibili.util.HttpRequestUtil;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.service.DownloadService;
import nicelee.service.ParseService;
import nicelee.ui.Global;

/**
 * 收藏夹相关 API（仅对 B 站已登录用户开放，AuthFilter 已拦截 /api/fav* 未登录请求）。
 *
 *  /api/fav/list                  GET   当前账号创建的收藏夹列表
 *  /api/fav/{favId}/items         GET   指定收藏夹的视频列表（支持 ?qn=xxx 用于后续提交回填默认 qn）
 *  /api/fav/{favId}/submitAll     POST  body={qn:80}  把整个收藏夹提交到下载队列
 */
@Controller(path = "/api", note = "B 站收藏夹（仅登录用户）")
public class ControllerFav {

	private static final Pattern ITEMS_PAT = Pattern.compile("^/api/fav/([0-9]+)/items$");
	private static final Pattern SUBMIT_PAT = Pattern.compile("^/api/fav/([0-9]+)/submitAll$");

	@Controller(path = "/fav", matchAll = false, note = "收藏夹相关全部接口")
	public String dispatch(BufferedWriter out,
			@Value(key = "pathData") String path,
			@Value(key = "postData") String body) throws IOException {
		try {
			if ("/api/fav/list".equals(path)) return listFolders(out);
			Matcher m = ITEMS_PAT.matcher(path);
			if (m.matches()) return listItems(out, m.group(1));
			m = SUBMIT_PAT.matcher(path);
			if (m.matches()) return submitAll(out, m.group(1), body);
			ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "unknown path: " + path));
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, e.getClass().getSimpleName() + ": " + e.getMessage()));
		}
		return null;
	}

	private String listFolders(BufferedWriter out) throws IOException {
		long mid = currentMid();
		if (mid <= 0) {
			ResponseUtil.writeJsonStatus(out, 401, JsonUtil.err(401, "无法获取 B 站 uid，请重新登录"));
			return null;
		}
		String url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" + mid;
		String json = new HttpRequestUtil().getContent(url, new HttpHeaders().getAllFavListHeaders(mid),
				HttpCookies.getGlobalCookies());
		JSONObject jobj = new JSONObject(json);
		if (jobj.optInt("code", -1) != 0) {
			ResponseUtil.writeJsonStatus(out, 502, JsonUtil.err(502, "B 站返回: " + jobj.optString("message")));
			return null;
		}
		JSONObject data = jobj.optJSONObject("data");
		JSONArray list = data == null ? null : data.optJSONArray("list");
		StringBuilder sb = new StringBuilder("[");
		if (list != null) {
			for (int i = 0; i < list.length(); i++) {
				if (i > 0) sb.append(',');
				JSONObject f = list.getJSONObject(i);
				sb.append('{')
					.append(JsonUtil.kv("id", String.valueOf(f.optLong("id")))).append(',')
					.append(JsonUtil.kv("title", f.optString("title"))).append(',')
					.append(JsonUtil.kvN("count", f.optInt("media_count"))).append(',')
					.append(JsonUtil.kvN("attr", f.optInt("attr")))
					.append('}');
			}
		}
		sb.append(']');
		ResponseUtil.writeJson(out, JsonUtil.okData(sb.toString()));
		return null;
	}

	private String listItems(BufferedWriter out, String favId) throws IOException {
		// 拉取全部页：Global.pageSize 默认 5，桌面端依赖该值分页展示。Web 控制台需要"整个收藏夹"，
		// 因此循环 p=1..N 调用 ParseService.getDetail，直到返回空或与上一页相同。
		LinkedHashMap<Long, ClipInfo> all = new LinkedHashMap<>();
		String title = "", owner = "";
		final int MAX_PAGES = 200; // 安全上限：5/页 * 200 = 1000 视频；20/页 * 200 = 4000
		int prevSize = -1;
		for (int page = 1; page <= MAX_PAGES; page++) {
			VideoInfo info = ParseService.getDetail("ml" + favId + "p=" + page);
			if (info == null || info.getClips() == null) break;
			if (page == 1) {
				title = info.getVideoName() == null ? "" : info.getVideoName();
				owner = info.getAuthor() == null ? "" : info.getAuthor();
			}
			LinkedHashMap<Long, ClipInfo> pageClips = info.getClips();
			if (pageClips.isEmpty()) break;
			int beforeMerge = all.size();
			for (java.util.Map.Entry<Long, ClipInfo> e : pageClips.entrySet()) {
				if (!all.containsKey(e.getKey())) all.put(e.getKey(), e.getValue());
			}
			// 该页没有带来任何新数据 => 已到末页（或 API 异常返回相同页）
			if (all.size() == beforeMerge) break;
			// 页内数量 < 单页大小，认为是末页
			if (prevSize >= 0 && pageClips.size() < prevSize) break;
			prevSize = pageClips.size();
		}
		if (all.isEmpty()) {
			ResponseUtil.writeJsonStatus(out, 502, JsonUtil.err(502, "收藏夹解析失败或为空"));
			return null;
		}
		StringBuilder sb = new StringBuilder("{");
		sb.append(JsonUtil.kv("title", title)).append(',')
				.append(JsonUtil.kv("owner", owner)).append(',')
				.append("\"items\":[");
		boolean first = true;
		for (ClipInfo c : all.values()) {
			if (!first) sb.append(',');
			first = false;
			sb.append('{')
				.append(JsonUtil.kv("avId", n(c.getAvId()))).append(',')
				.append(JsonUtil.kvN("cid", c.getcId())).append(',')
				.append(JsonUtil.kvN("page", c.getPage())).append(',')
				.append(JsonUtil.kv("title", n(c.getTitle()))).append(',')
				.append(JsonUtil.kv("avTitle", n(c.getAvTitle()))).append(',')
				.append(JsonUtil.kv("upName", n(c.getUpName()))).append(',')
				.append(JsonUtil.kv("preview", n(c.getPicPreview())))
				.append('}');
		}
		sb.append("]}");
		ResponseUtil.writeJson(out, JsonUtil.okData(sb.toString()));
		return null;
	}

	private String submitAll(BufferedWriter out, String favId, String body) throws IOException {
		int qn = parseInt(jsonStr(body, "qn"), 80);
		// 与 listItems 一致，循环全部页
		LinkedHashMap<Long, ClipInfo> all = new LinkedHashMap<>();
		VideoInfo refInfo = null;
		final int MAX_PAGES = 200;
		int prevSize = -1;
		for (int page = 1; page <= MAX_PAGES; page++) {
			VideoInfo info = ParseService.getDetail("ml" + favId + "p=" + page);
			if (info == null || info.getClips() == null) break;
			if (refInfo == null) refInfo = info;
			LinkedHashMap<Long, ClipInfo> pageClips = info.getClips();
			if (pageClips.isEmpty()) break;
			int beforeMerge = all.size();
			for (java.util.Map.Entry<Long, ClipInfo> e : pageClips.entrySet()) {
				if (!all.containsKey(e.getKey())) all.put(e.getKey(), e.getValue());
			}
			if (all.size() == beforeMerge) break;
			if (prevSize >= 0 && pageClips.size() < prevSize) break;
			prevSize = pageClips.size();
		}
		if (refInfo == null || all.isEmpty()) {
			ResponseUtil.writeJsonStatus(out, 502, JsonUtil.err(502, "收藏夹解析失败或为空"));
			return null;
		}
		int n = 0;
		Iterator<ClipInfo> it = all.values().iterator();
		while (it.hasNext()) {
			ClipInfo c = it.next();
			try {
				DownloadService.submit(refInfo, c, qn);
				n++;
			} catch (Exception ignored) {}
		}
		ResponseUtil.writeJson(out, JsonUtil.okData("{" + JsonUtil.kvN("submitted", n) + "}"));
		return null;
	}

	private static long currentMid() {
		try {
			if (Global.isLogin && HttpCookies.getGlobalCookies() != null) {
				INeedLogin tmp = new INeedLogin();
				tmp.getLoginStatus(HttpCookies.getGlobalCookies());
				if (tmp.user != null) return tmp.user.getUid();
			}
		} catch (Exception ignored) {}
		return 0;
	}

	private static String n(String s) { return s == null ? "" : s; }

	private static int parseInt(String s, int def) {
		if (s == null || s.isEmpty()) return def;
		try { return Integer.parseInt(s); } catch (Exception e) { return def; }
	}

	private static String jsonStr(String body, String key) {
		if (body == null) return null;
		String pat = "\"" + key + "\"";
		int i = body.indexOf(pat);
		if (i < 0) return null;
		int colon = body.indexOf(':', i + pat.length());
		if (colon < 0) return null;
		int j = colon + 1;
		while (j < body.length() && Character.isWhitespace(body.charAt(j))) j++;
		if (j >= body.length()) return null;
		char c = body.charAt(j);
		if (c == '"') {
			int q2 = body.indexOf('"', j + 1);
			return q2 < 0 ? null : body.substring(j + 1, q2);
		}
		int end = j;
		while (end < body.length() && (Character.isDigit(body.charAt(end)) || body.charAt(end) == '-')) end++;
		return body.substring(j, end);
	}
}
