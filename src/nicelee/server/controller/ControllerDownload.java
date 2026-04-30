package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.service.DownloadService;
import nicelee.service.ParseService;
import nicelee.service.TaskSnapshot;

@Controller(path = "/api/download", note = "下载任务管理")
public class ControllerDownload {

	private static final Pattern ID_PAT = Pattern.compile("^/api/download/([^/]+)/(pause|resume|remove)$");
	private static final Pattern ALL_PAT = Pattern.compile("^/api/download/all/(pause|resume|done)$");
	private static final Pattern PRIO_PAT = Pattern.compile("^/api/download/([^/]+)/priority$");
	private static final Pattern MOVE_PAT = Pattern.compile("^/api/download/([^/]+)/move$");

	/**
	 * 单一入口，避免 PathDealer 的 getMethods() 顺序不确定 + 前缀匹配吞掉子路径的问题。
	 * 内部按 path 派发到 list / submit / op。
	 */
	@Controller(path = "/", matchAll = false, note = "下载相关全部接口")
	public String dispatch(BufferedWriter out,
			@Value(key = "pathData") String path,
			@Value(key = "postData") String body) throws IOException {
		if ("/api/download/list".equals(path)) return list(out);
		if ("/api/download/submit".equals(path)) return submit(out, body);
		Matcher m2 = ALL_PAT.matcher(path);
		if (m2.matches()) return opAll(out, m2.group(1));
		Matcher mp = PRIO_PAT.matcher(path);
		if (mp.matches()) return opPriority(out, mp.group(1), body);
		Matcher mv = MOVE_PAT.matcher(path);
		if (mv.matches()) return opMove(out, mv.group(1), body);
		Matcher m = ID_PAT.matcher(path);
		if (m.matches()) return opSingle(out, m.group(1), m.group(2));
		ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "unknown path: " + path));
		return null;
	}

	private String list(BufferedWriter out) throws IOException {
		List<TaskSnapshot> tasks = DownloadService.list();
		ResponseUtil.writeJson(out, JsonUtil.okData(snapshotJson(tasks)));
		return null;
	}

	private String submit(BufferedWriter out, String body) throws IOException {
		String avId = jsonStr(body, "avId");
		String cidStr = jsonStr(body, "cid");
		String qnStr = jsonStr(body, "qn");
		if (avId == null || cidStr == null || qnStr == null) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "missing avId/cid/qn"));
			return null;
		}
		try {
			long cid = Long.parseLong(cidStr);
			int qn = Integer.parseInt(qnStr);
			VideoInfo info = ParseService.getDetail(avId);
			ClipInfo target = null;
			if (info.getClips() != null) target = info.getClips().get(cid);
			if (target == null) {
				ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "clip not found in video"));
				return null;
			}
			DownloadService.submit(info, target, qn);
			ResponseUtil.writeJson(out, JsonUtil.ok());
		} catch (NumberFormatException e) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "cid/qn must be numeric"));
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, e.getClass().getSimpleName() + ": " + e.getMessage()));
		}
		return null;
	}

	private String opSingle(BufferedWriter out, String id, String action) throws IOException {
		boolean ok;
		switch (action) {
			case "pause": ok = DownloadService.pause(id); break;
			case "resume": ok = DownloadService.resume(id); break;
			case "remove": ok = DownloadService.remove(id); break;
			default: ok = false;
		}
		if (!ok) ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "task not found"));
		else ResponseUtil.writeJson(out, JsonUtil.ok());
		return null;
	}

	private String opAll(BufferedWriter out, String action) throws IOException {
		int n;
		switch (action) {
			case "pause": n = DownloadService.pauseAll(); break;
			case "resume": n = DownloadService.resumeAll(); break;
			case "done": n = DownloadService.removeDone(); break;
			default: n = -1;
		}
		ResponseUtil.writeJson(out, JsonUtil.okData("{\"affected\":" + n + "}"));
		return null;
	}

	private String opPriority(BufferedWriter out, String id, String body) throws IOException {
		String pStr = jsonStr(body, "priority");
		if (pStr == null) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "missing priority"));
			return null;
		}
		try {
			int prio = Integer.parseInt(pStr.trim());
			boolean ok = DownloadService.setPriority(id, prio);
			if (!ok) ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "task not found"));
			else ResponseUtil.writeJson(out, JsonUtil.ok());
		} catch (NumberFormatException e) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "priority must be int"));
		}
		return null;
	}

	private String opMove(BufferedWriter out, String id, String body) throws IOException {
		String dir = jsonStr(body, "direction");
		if (dir == null) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "missing direction"));
			return null;
		}
		boolean ok = DownloadService.move(id, dir);
		if (!ok) ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "task not found or invalid direction"));
		else ResponseUtil.writeJson(out, JsonUtil.ok());
		return null;
	}

	private static String snapshotJson(List<TaskSnapshot> tasks) {
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		boolean first = true;
		for (TaskSnapshot t : tasks) {
			if (!first) sb.append(',');
			first = false;
			sb.append('{')
				.append(JsonUtil.kv("id", n(t.id))).append(',')
				.append(JsonUtil.kv("avId", n(t.avId))).append(',')
				.append(JsonUtil.kvN("page", t.page)).append(',')
				.append(JsonUtil.kvN("qn", t.qn)).append(',')
				.append(JsonUtil.kvN("realQn", t.realQn)).append(',')
				.append(JsonUtil.kv("title", n(t.title))).append(',')
				.append(JsonUtil.kv("fileName", n(t.fileName))).append(',')
				.append(JsonUtil.kv("absPath", n(t.absPath))).append(',')
				.append(JsonUtil.kv("relPath", n(t.relPath))).append(',')
				.append(JsonUtil.kv("status", n(t.status))).append(',')
				.append(JsonUtil.kvN("currentDown", t.currentDown)).append(',')
				.append(JsonUtil.kvN("totalSize", t.totalSize)).append(',')
				.append(JsonUtil.kvN("speed", t.speed)).append(',')
				.append(JsonUtil.kv("lastError", n(t.lastError))).append(',')
				.append(JsonUtil.kvN("priority", t.priority))
				.append('}');
		}
		sb.append(']');
		return sb.toString();
	}

	private static String n(String s) { return s == null ? "" : s; }

	private static String jsonStr(String body, String key) {
		if (body == null) return null;
		String pat = "\"" + key + "\"";
		int i = body.indexOf(pat);
		if (i < 0) return null;
		int colon = body.indexOf(':', i + pat.length());
		if (colon < 0) return null;
		// 跳过空白
		int j = colon + 1;
		while (j < body.length() && Character.isWhitespace(body.charAt(j))) j++;
		if (j >= body.length()) return null;
		char c = body.charAt(j);
		if (c == '"') {
			int q2 = body.indexOf('"', j + 1);
			return q2 < 0 ? null : body.substring(j + 1, q2);
		} else {
			int end = j;
			while (end < body.length() && (Character.isDigit(body.charAt(end)) || body.charAt(end) == '-' || body.charAt(end) == '.')) end++;
			return body.substring(j, end);
		}
	}
}
