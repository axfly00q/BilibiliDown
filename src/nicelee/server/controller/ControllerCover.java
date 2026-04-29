package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import nicelee.bilibili.INeedAV;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.util.HttpHeaders;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.service.ParseService;
import nicelee.ui.Global;

/**
 * 封面图下载：浏览器直拉 b 站图片会被 referer 拒绝，需要后端代理保存。
 *  - GET /api/cover/save?avId=BVxxx&cid=yyy → 保存到 Global.savePath，返回 {file: "<相对路径>"}
 *  - 不指定 cid 时取首个 clip 的 picPreview。
 *  - 文件名：<avId>-cover-p<page>.<ext>
 */
@nicelee.bilibili.annotations.Controller(path = "/api/cover", note = "封面图下载")
public class ControllerCover {

	@nicelee.bilibili.annotations.Controller(path = "/save", matchAll = true, note = "GET ?avId=&cid=（可选） 保存封面图")
	public String save(BufferedWriter out,
			@nicelee.bilibili.annotations.Value(key = "avId") String avId,
			@nicelee.bilibili.annotations.Value(key = "cid") String cidStr) throws IOException {
		if (avId == null || avId.isEmpty()) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "missing avId"));
			return null;
		}
		try {
			String validId = new INeedAV().getValidID(avId);
			VideoInfo info = ParseService.getDetail(validId);
			if (info == null || info.getClips() == null || info.getClips().isEmpty()) {
				ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "no clips"));
				return null;
			}
			ClipInfo clip;
			if (cidStr != null && !cidStr.isEmpty()) {
				try {
					long cid = Long.parseLong(cidStr);
					clip = info.getClips().get(cid);
				} catch (NumberFormatException e) {
					ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "bad cid"));
					return null;
				}
				if (clip == null) {
					ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "clip not found"));
					return null;
				}
			} else {
				clip = info.getClips().values().iterator().next();
			}
			String picUrl = clip.getPicPreview();
			if (picUrl == null || picUrl.isEmpty()) {
				ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "no cover preview url"));
				return null;
			}

			String ext = guessExt(picUrl);
			String fname = String.format("%s-cover-p%d.%s", validId, clip.getPage(), ext);
			File base = new File(Global.savePath);
			base.mkdirs();
			File target = new File(base, fname);

			download(picUrl, target);

			String enc = URLEncoder.encode(fname, "UTF-8").replace("+", "%20");
			String json = "{\"file\":\"" + JsonUtil.escape(fname) + "\",\"size\":" + target.length()
					+ ",\"url\":\"/files/" + enc + "\"}";
			ResponseUtil.writeJson(out, JsonUtil.okData(json));
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, e.getClass().getSimpleName() + ": " + e.getMessage()));
		}
		return null;
	}

	private static String guessExt(String url) {
		String low = url.toLowerCase();
		int q = low.indexOf('?');
		if (q >= 0) low = low.substring(0, q);
		if (low.endsWith(".jpg") || low.endsWith(".jpeg")) return "jpg";
		if (low.endsWith(".png")) return "png";
		if (low.endsWith(".webp")) return "webp";
		if (low.endsWith(".gif")) return "gif";
		return "jpg";
	}

	private static void download(String url, File target) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(15000);
		conn.setReadTimeout(30000);
		// 复用项目的 PC UA + 设置 referer 满足 b 站防盗链
		for (java.util.Map.Entry<String, String> e : new HttpHeaders().getCommonHeaders().entrySet()) {
			conn.setRequestProperty(e.getKey(), e.getValue());
		}
		conn.setRequestProperty("Referer", "https://www.bilibili.com/");
		conn.connect();
		int code = conn.getResponseCode();
		if (code / 100 != 2) {
			throw new IOException("HTTP " + code + " from " + url);
		}
		try (InputStream in = conn.getInputStream();
			 FileOutputStream fos = new FileOutputStream(target)) {
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
		} finally {
			conn.disconnect();
		}
	}
}
