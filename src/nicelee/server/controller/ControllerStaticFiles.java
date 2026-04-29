package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.regex.Pattern;

import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.server.util.ResponseUtil;

@Controller(path = "/static", note = "静态文件")
public class ControllerStaticFiles {

	// 仅允许字母、数字、点、下划线、中划线和正斜杠；用于阻止路径穿越（".."、反斜杠等）
	private static final Pattern SAFE_PATH = Pattern.compile("^/[A-Za-z0-9._/-]+$");

	@Controller(path = "/index.html", matchAll = true, note = "html主页")
	public String html(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		// /static
		String realPath = path.substring(7);
		makeFileResponse(out, outRaw, realPath, "text/html; charset=UTF-8", FileKind.GEETEST_HTML);
		return null;
	}

	@Controller(path = "/js", matchAll = false, note = "js文件")
	public String js(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		String realPath = path.substring(7);
		makeFileResponse(out, outRaw, realPath, "application/javascript; charset=utf-8", FileKind.API);
		return null;
	}

	@Controller(path = "/css", matchAll = false, note = "css文件")
	public String css(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		String realPath = path.substring(7);
		makeFileResponse(out, outRaw, realPath, "text/css; charset=utf-8", FileKind.API);
		return null;
	}

	@Controller(path = "/i18n", matchAll = false, note = "i18n 国际化 JSON 文件")
	public String i18n(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		String realPath = path.substring(7);
		makeFileResponse(out, outRaw, realPath, "application/json; charset=utf-8", FileKind.API);
		return null;
	}

	private enum FileKind { GEETEST_HTML, API }

	public void makeFileResponse(BufferedWriter out, OutputStream outRaw, String path, String contentType) {
		makeFileResponse(out, outRaw, path, contentType, FileKind.API);
	}

	private void makeFileResponse(BufferedWriter out, OutputStream outRaw, String path, String contentType, FileKind kind) {
		try {
			// 路径安全校验：阻止 ".."、反斜杠、空字节等导致的路径穿越
			if (path == null || path.isEmpty() || path.contains("..") || !SAFE_PATH.matcher(path).matches()) {
				ResponseUtil.response404NotFound(out);
				ResponseUtil.writeApiSecurityHeaders(out);
				ResponseUtil.responseHeader(out, "Content-Length", "0");
				ResponseUtil.endResponseHeader(out);
				return;
			}
			URL url = this.getClass().getResource("/resources/geetest-validator" + path);
			if (url != null) {
				InputStream in = this.getClass().getResourceAsStream("/resources/geetest-validator" + path);
				ResponseUtil.response200OK(out);
				ResponseUtil.responseHeader(out, "Content-Type", contentType);
				if (kind == FileKind.GEETEST_HTML) {
					ResponseUtil.writeGeetestSecurityHeaders(out);
				} else {
					ResponseUtil.writeApiSecurityHeaders(out);
				}
				ResponseUtil.endResponseHeader(out);
				byte[] buffer = new byte[4096];
				int len = in.read(buffer);
				while (len > 0) {
					outRaw.write(buffer, 0, len);
					len = in.read(buffer);
				}
				in.close();
			} else {
				ResponseUtil.response404NotFound(out);
				ResponseUtil.writeApiSecurityHeaders(out);
				ResponseUtil.responseHeader(out, "Content-Length", "0");
				ResponseUtil.endResponseHeader(out);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
