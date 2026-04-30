package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;

import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.service.ParseService;

@Controller(path = "/api/parse", note = "解析输入（URL/av/BV）")
public class ControllerParse {

	@Controller(path = "", matchAll = true, note = "解析: GET ?input=...  返回 avId")
	public String parse(BufferedWriter out,
			@Value(key = "input") String inputRaw) throws IOException {
		if (inputRaw == null || inputRaw.isEmpty()) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "missing input"));
			return null;
		}
		try {
			String input = java.net.URLDecoder.decode(inputRaw, "UTF-8");
			String avId = ParseService.validId(input);
			if (avId == null || avId.isEmpty()) {
				ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "无法解析输入"));
				return null;
			}
			VideoInfo info = ParseService.getDetail(avId);
			if (info == null || info.getVideoId() == null) {
				ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, "解析失败：未获取到视频信息（可能未登录、未开播或不支持的链接）"));
				return null;
			}
			StringBuilder sb = new StringBuilder();
			sb.append('{')
				.append(JsonUtil.kv("avId", info.getVideoId())).append(',')
				.append(JsonUtil.kv("title", info.getVideoName() == null ? "" : info.getVideoName())).append(',')
				.append(JsonUtil.kv("author", info.getAuthor() == null ? "" : info.getAuthor())).append(',')
				.append(JsonUtil.kv("brief", info.getBrief() == null ? "" : info.getBrief())).append(',')
				.append(JsonUtil.kv("preview", info.getVideoPreview() == null ? "" : info.getVideoPreview())).append(',')
				.append("\"clips\":[");
			boolean first = true;
			if (info.getClips() != null) {
				for (ClipInfo c : info.getClips().values()) {
					if (!first) sb.append(',');
					first = false;
					sb.append('{')
						.append(JsonUtil.kv("cid", String.valueOf(c.getcId()))).append(',')
						.append(JsonUtil.kvN("page", c.getPage())).append(',')
						.append(JsonUtil.kv("title", c.getTitle() == null ? "" : c.getTitle())).append(',')
						.append(JsonUtil.kv("avTitle", c.getAvTitle() == null ? "" : c.getAvTitle())).append(',')
						.append(JsonUtil.kv("preview", c.getPicPreview() == null ? "" : c.getPicPreview()))
						.append('}');
				}
			}
			sb.append("]");
			// 专栏 HTML 导出提示（一次性，读后置空）
			String exported = nicelee.ui.Global.lastCvExportFile;
			if (exported != null && !exported.isEmpty()) {
				nicelee.ui.Global.lastCvExportFile = null;
				sb.append(',').append(JsonUtil.kv("cvExportFile", exported));
			}
			sb.append("}");
			ResponseUtil.writeJson(out, JsonUtil.okData(sb.toString()));
			// 写入解析历史（仅 非游客）
			try {
				nicelee.server.auth.SessionStore.Session sess = nicelee.server.auth.SessionStore.CURRENT.get();
				if (sess != null && !sess.isGuest()) {
					nicelee.service.HistoryStore.Entry e = new nicelee.service.HistoryStore.Entry();
					e.key = info.getVideoId();
					e.type = "parse";
					e.title = info.getVideoName();
					e.avId = info.getVideoId();
					e.input = input;
					e.status = "parsed";
					e.ts = System.currentTimeMillis();
					nicelee.service.HistoryStore.PARSE.add(e);
				}
			} catch (Exception ignored) {}
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage())));
		}
		return null;
	}
}
