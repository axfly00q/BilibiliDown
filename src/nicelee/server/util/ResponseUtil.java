package nicelee.server.util;

import java.io.BufferedWriter;
import java.io.IOException;

public class ResponseUtil {

	// CSP for the geetest-validator page (loads gt.js + posts to local server + iframes geetest)
	// Allows inline style because gt.js injects style attributes; scripts strictly limited to self + geetest CDNs.
	private static final String CSP_GEETEST = "default-src 'self'; "
			+ "img-src 'self' data: https: http:; "
			+ "script-src 'self' https://static.geetest.com https://*.geetest.com; "
			+ "style-src 'self' 'unsafe-inline'; "
			+ "connect-src 'self' https://api.geetest.com https://*.geetest.com; "
			+ "frame-src 'self' https://*.geetest.com; "
			+ "base-uri 'none'; form-action 'self'";

	// CSP for the cookieRefresh page (needs wasm + remote script from s1.hdslb.com)
	private static final String CSP_COOKIE_REFRESH = "default-src 'self'; "
			+ "img-src 'self' data:; "
			+ "script-src 'self' https://s1.hdslb.com 'wasm-unsafe-eval'; "
			+ "style-src 'self' 'unsafe-inline'; "
			+ "connect-src 'self'; "
			+ "base-uri 'none'; form-action 'self'";

	// CSP for plain JSON / JS / CSS / API responses
	private static final String CSP_API = "default-src 'none'; frame-ancestors 'none'";

	public static void response200OK(BufferedWriter out) throws IOException {
		out.write("HTTP/1.1 200 OK\r\n");
		out.flush();
	}
	
	public static void response404NotFound(BufferedWriter out) throws IOException {
		out.write("HTTP/1.1 404 Not Found\r\n");
		out.flush();
	}
	
	public static void responseHeader(BufferedWriter out, String name, String value) throws IOException {
		out.write(String.format("%s: %s\r\n", name, value));
	}
	
	public static void endResponseHeader(BufferedWriter out) throws IOException {
		out.write("\r\n");
		out.flush();
	}
	
	public static void endResponse(BufferedWriter out) throws IOException {
		out.write("\r\n");
		out.flush();
	}
	
	public static void htmlResponseBegin(BufferedWriter out) throws IOException {
		ResponseUtil.response200OK(out);
		ResponseUtil.responseHeader(out, "Content-Type", "text/html; charset=UTF-8");
		ResponseUtil.endResponseHeader(out);
	}
	
	public static void htmlResponseEnd(BufferedWriter out) throws IOException {
		ResponseUtil.endResponse(out);
	}
	
	public static void Response404(BufferedWriter out) throws IOException {
		ResponseUtil.endResponse(out);
	}

	/**
	 * 通用安全响应头：所有响应都建议带上的最小安全头
	 */
	public static void writeBaseSecurityHeaders(BufferedWriter out) throws IOException {
		responseHeader(out, "X-Content-Type-Options", "nosniff");
		responseHeader(out, "X-Frame-Options", "DENY");
		responseHeader(out, "Referrer-Policy", "no-referrer");
		responseHeader(out, "Cache-Control", "no-store");
	}

	/**
	 * 写入 JSON / JS / CSS / API 类响应的安全头（含一个最严格的 CSP）
	 */
	public static void writeApiSecurityHeaders(BufferedWriter out) throws IOException {
		writeBaseSecurityHeaders(out);
		responseHeader(out, "Content-Security-Policy", CSP_API);
	}

	/**
	 * 写入 geetest 页面的安全头
	 */
	public static void writeGeetestSecurityHeaders(BufferedWriter out) throws IOException {
		writeBaseSecurityHeaders(out);
		responseHeader(out, "Content-Security-Policy", CSP_GEETEST);
	}

	/**
	 * 写入 cookieRefresh 页面的安全头
	 */
	public static void writeCookieRefreshSecurityHeaders(BufferedWriter out) throws IOException {
		writeBaseSecurityHeaders(out);
		responseHeader(out, "Content-Security-Policy", CSP_COOKIE_REFRESH);
	}

	/**
	 * 写入 Web 控制台导航页的安全头：仅允许同域脚本/样式/字体/图片，最严格
	 */
	private static final String CSP_CONSOLE = "default-src 'self'; "
			+ "img-src 'self' data:; "
			+ "script-src 'self'; "
			+ "style-src 'self' 'unsafe-inline'; "
			+ "connect-src 'self'; "
			+ "base-uri 'none'; form-action 'self'";

	public static void writeConsoleSecurityHeaders(BufferedWriter out) throws IOException {
		writeBaseSecurityHeaders(out);
		responseHeader(out, "Content-Security-Policy", CSP_CONSOLE);
	}

	// ====== Web 控制台扩展：登录/SSE/JSON 工具 ======

	public static void response401(BufferedWriter out) throws IOException {
		out.write("HTTP/1.1 401 Unauthorized\r\n");
		out.flush();
	}

	public static void response403(BufferedWriter out) throws IOException {
		out.write("HTTP/1.1 403 Forbidden\r\n");
		out.flush();
	}

	public static void response302(BufferedWriter out, String location) throws IOException {
		out.write("HTTP/1.1 302 Found\r\n");
		out.write("Location: " + location + "\r\n");
		out.flush();
	}

	/** 写一个 Set-Cookie 头 */
	public static void writeSetCookie(BufferedWriter out, String name, String value, int maxAgeSec) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append(name).append('=').append(value);
		sb.append("; Path=/; HttpOnly; SameSite=Lax");
		if (maxAgeSec >= 0) sb.append("; Max-Age=").append(maxAgeSec);
		responseHeader(out, "Set-Cookie", sb.toString());
	}

	/** 写一段 JSON 响应（200 + 头 + body），body 由调用方提供 */
	public static void writeJson(BufferedWriter out, String json) throws IOException {
		response200OK(out);
		responseHeader(out, "Content-Type", "application/json; charset=UTF-8");
		writeApiSecurityHeaders(out);
		endResponseHeader(out);
		out.write(json);
		endResponse(out);
	}

	public static void writeJsonStatus(BufferedWriter out, int status, String json) throws IOException {
		out.write("HTTP/1.1 " + status + " " + statusText(status) + "\r\n");
		out.flush();
		responseHeader(out, "Content-Type", "application/json; charset=UTF-8");
		writeApiSecurityHeaders(out);
		endResponseHeader(out);
		out.write(json);
		endResponse(out);
	}

	private static String statusText(int code) {
		switch (code) {
			case 200: return "OK";
			case 400: return "Bad Request";
			case 401: return "Unauthorized";
			case 403: return "Forbidden";
			case 404: return "Not Found";
			case 405: return "Method Not Allowed";
			case 429: return "Too Many Requests";
			case 500: return "Internal Server Error";
			default: return "OK";
		}
	}

	/** 写 SSE 响应头（之后由调用方持续写 data: ... 帧） */
	public static void writeSseHeader(BufferedWriter out) throws IOException {
		response200OK(out);
		responseHeader(out, "Content-Type", "text/event-stream; charset=UTF-8");
		responseHeader(out, "Cache-Control", "no-store");
		responseHeader(out, "X-Accel-Buffering", "no");
		responseHeader(out, "Connection", "keep-alive");
		// SSE 一般不需要 CSP
		endResponseHeader(out);
	}

	/** Web 控制台 App 页面 CSP：与 navigation 一致，但允许 SSE/同源 fetch */
	private static final String CSP_CONSOLE_APP = "default-src 'self'; "
			+ "img-src 'self' data: https: http:; "
			+ "script-src 'self'; "
			+ "style-src 'self' 'unsafe-inline'; "
			+ "connect-src 'self'; "
			+ "base-uri 'none'; form-action 'self'";

	public static void writeConsoleAppSecurityHeaders(BufferedWriter out) throws IOException {
		writeBaseSecurityHeaders(out);
		responseHeader(out, "Content-Security-Policy", CSP_CONSOLE_APP);
	}

}
