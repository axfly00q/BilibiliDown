package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;

import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.ui.Global;

/**
 * 远程下载已完成的文件。安全约束：仅允许位于 Global.savePath 下的文件。
 * 路径形式：/files/<相对savePath的子路径>
 */
@Controller(path = "/files", note = "下载已生成的文件（受限于全局下载目录）")
public class ControllerFiles {

	@Controller(path = "", matchAll = false, note = "/files/<rel>")
	public String download(BufferedWriter out, OutputStream outRaw,
			@Value(key = "pathData") String path) throws IOException {
		if (path == null || path.length() <= "/files/".length()) {
			ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "missing file path"));
			return null;
		}
		String rel = path.substring("/files/".length());
		// URL 解码
		try { rel = java.net.URLDecoder.decode(rel, "UTF-8"); } catch (Exception ignored) {}
		if (rel.contains("\0")) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "invalid path"));
			return null;
		}

		File base = new File(Global.savePath);
		File target = new File(base, rel);
		String baseCanon, targetCanon;
		try {
			baseCanon = base.getCanonicalPath();
			targetCanon = target.getCanonicalPath();
		} catch (IOException e) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "bad path"));
			return null;
		}
		String sep = java.io.File.separator;
		if (!targetCanon.equals(baseCanon) && !targetCanon.startsWith(baseCanon + sep)) {
			ResponseUtil.writeJsonStatus(out, 403, JsonUtil.err(403, "outside savePath"));
			return null;
		}
		if (!target.exists() || target.isDirectory()) {
			ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "not found"));
			return null;
		}

		String fname = target.getName();
		String fnameEnc;
		try { fnameEnc = URLEncoder.encode(fname, "UTF-8").replace("+", "%20"); }
		catch (Exception e) { fnameEnc = fname; }

		ResponseUtil.response200OK(out);
		ResponseUtil.responseHeader(out, "Content-Type", "application/octet-stream");
		ResponseUtil.responseHeader(out, "Content-Length", String.valueOf(target.length()));
		ResponseUtil.responseHeader(out, "Content-Disposition",
				"attachment; filename*=UTF-8''" + fnameEnc);
		ResponseUtil.writeApiSecurityHeaders(out);
		ResponseUtil.endResponseHeader(out);
		try (FileInputStream fis = new FileInputStream(target)) {
			byte[] buf = new byte[8192];
			int n;
			while ((n = fis.read(buf)) > 0) {
				outRaw.write(buf, 0, n);
			}
			outRaw.flush();
		}
		return null;
	}
}
