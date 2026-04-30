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

/**
 * Web 控制台导航页（方案 A）：在浏览器里展示一个现代化的入口页面，
 * 提供登录、Cookie 刷新、关于等功能跳转。
 */
@Controller(path = "/console", note = "Web 控制台导航页")
public class ControllerWebConsole {

	private static final Pattern SAFE_PATH = Pattern.compile("^/[A-Za-z0-9._/-]+$");

	@Controller(path = "/index.html", matchAll = true, note = "Web 控制台主页")
	public String index(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		String realPath = path.substring(8);
		makeFileResponse(out, outRaw, realPath, "text/html; charset=UTF-8");
		return null;
	}

	@Controller(path = "/login.html", matchAll = true, note = "登录页（已弃用，自动跳转账号页）")
	public String login(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		try {
			ResponseUtil.response302(out, "/console/account.html");
			ResponseUtil.endResponseHeader(out);
		} catch (IOException e) { e.printStackTrace(); }
		return null;
	}

	@Controller(path = "/parse.html", matchAll = true, note = "解析页")
	public String parse(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		makeFileResponse(out, outRaw, path.substring(8), "text/html; charset=UTF-8");
		return null;
	}

	@Controller(path = "/downloads.html", matchAll = true, note = "下载任务页")
	public String downloads(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		makeFileResponse(out, outRaw, path.substring(8), "text/html; charset=UTF-8");
		return null;
	}

	@Controller(path = "/settings.html", matchAll = true, note = "设置页")
	public String settings(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		makeFileResponse(out, outRaw, path.substring(8), "text/html; charset=UTF-8");
		return null;
	}

	@Controller(path = "/account.html", matchAll = true, note = "B 站账号页")
	public String account(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		makeFileResponse(out, outRaw, path.substring(8), "text/html; charset=UTF-8");
		return null;
	}

	@Controller(path = "/history.html", matchAll = true, note = "历史记录页")
	public String history(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		makeFileResponse(out, outRaw, path.substring(8), "text/html; charset=UTF-8");
		return null;
	}

	@Controller(path = "/favorites.html", matchAll = true, note = "收藏夹页")
	public String favorites(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		makeFileResponse(out, outRaw, path.substring(8), "text/html; charset=UTF-8");
		return null;
	}

	@Controller(path = "/logs.html", matchAll = true, note = "实时日志页")
	public String logs(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		makeFileResponse(out, outRaw, path.substring(8), "text/html; charset=UTF-8");
		return null;
	}

	@Controller(path = "/dashboard.html", matchAll = true, note = "仪表盘页")
	public String dashboard(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		makeFileResponse(out, outRaw, path.substring(8), "text/html; charset=UTF-8");
		return null;
	}

	@Controller(path = "/js/", matchAll = false, note = "JS 资源")
	public String js(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		makeFileResponse(out, outRaw, path.substring(8), "application/javascript; charset=UTF-8");
		return null;
	}

	@Controller(path = "/css/", matchAll = false, note = "CSS 资源")
	public String css(BufferedWriter out, OutputStream outRaw, @Value(key = "pathData") String path) {
		makeFileResponse(out, outRaw, path.substring(8), "text/css; charset=UTF-8");
		return null;
	}

	private void makeFileResponse(BufferedWriter out, OutputStream outRaw, String path, String contentType) {
		try {
			if (path == null || path.isEmpty() || path.contains("..") || !SAFE_PATH.matcher(path).matches()) {
				ResponseUtil.response404NotFound(out);
				ResponseUtil.writeApiSecurityHeaders(out);
				ResponseUtil.responseHeader(out, "Content-Length", "0");
				ResponseUtil.endResponseHeader(out);
				return;
			}
			URL url = this.getClass().getResource("/resources/web-console" + path);
			if (url != null) {
				try (InputStream in = this.getClass().getResourceAsStream("/resources/web-console" + path)) {
					ResponseUtil.response200OK(out);
					ResponseUtil.responseHeader(out, "Content-Type", contentType);
					ResponseUtil.writeConsoleAppSecurityHeaders(out);
					ResponseUtil.endResponseHeader(out);
					byte[] buffer = new byte[4096];
					int len = in.read(buffer);
					while (len > 0) {
						outRaw.write(buffer, 0, len);
						len = in.read(buffer);
					}
				}
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
