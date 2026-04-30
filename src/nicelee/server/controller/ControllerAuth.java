package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;

import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.server.auth.LoginRateLimiter;
import nicelee.server.auth.SessionStore;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.ui.Global;

@Controller(path = "/api/auth", note = "Web 控制台登录鉴权")
public class ControllerAuth {

	@Controller(path = "/login", matchAll = true, note = "登录, body={username,password}")
	public String login(BufferedWriter out,
			@Value(key = "postData") String body,
			@Value(key = "ipData") String ip) throws IOException {
		// 限流：同一 IP 短时间多次失败将被临时锁定，缓解暴力破解
		long lockedSec = LoginRateLimiter.lockedSecondsRemaining(ip);
		if (lockedSec > 0) {
			ResponseUtil.writeJsonStatus(out, 429,
					JsonUtil.err(429, "too many failed attempts, retry after " + lockedSec + "s"));
			return null;
		}
		String user = extract(body, "username");
		String pass = extract(body, "password");
		if (user == null || pass == null) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "missing fields"));
			return null;
		}
		if (!user.equals(Global.webAuthUsername) || !pass.equals(Global.webAuthPassword)) {
			// 简单延迟，缓解暴力破解
			try { Thread.sleep(500); } catch (InterruptedException ignored) {}
			LoginRateLimiter.onFailure(ip);
			ResponseUtil.writeJsonStatus(out, 401, JsonUtil.err(401, "invalid credentials"));
			return null;
		}
		LoginRateLimiter.onSuccess(ip);
		String sid = SessionStore.create(user);
		ResponseUtil.response200OK(out);
		ResponseUtil.responseHeader(out, "Content-Type", "application/json; charset=UTF-8");
		ResponseUtil.writeApiSecurityHeaders(out);
		ResponseUtil.writeSetCookie(out, SessionStore.COOKIE_NAME, sid, SessionStore.SESSION_TTL_SEC);
		ResponseUtil.endResponseHeader(out);
		out.write(JsonUtil.okData("{" + JsonUtil.kv("username", user) + "}"));
		ResponseUtil.endResponse(out);
		return null;
	}

	@Controller(path = "/guest", matchAll = true, note = "游客登录")
	public String guest(BufferedWriter out) throws IOException {
		String sid = SessionStore.create("guest", "guest");
		ResponseUtil.response200OK(out);
		ResponseUtil.responseHeader(out, "Content-Type", "application/json; charset=UTF-8");
		ResponseUtil.writeApiSecurityHeaders(out);
		ResponseUtil.writeSetCookie(out, SessionStore.COOKIE_NAME, sid, SessionStore.SESSION_TTL_SEC);
		ResponseUtil.endResponseHeader(out);
		out.write(JsonUtil.okData("{" + JsonUtil.kv("username", "guest") + "," + JsonUtil.kv("role", "guest") + "}"));
		ResponseUtil.endResponse(out);
		return null;
	}

	@Controller(path = "/logout", matchAll = true, note = "登出")
	public String logout(BufferedWriter out,
			@Value(key = "postData") String body) throws IOException {
		// header cookie 在此处不可见（PathDealer 未传），仅清除浏览器侧 Cookie
		ResponseUtil.response200OK(out);
		ResponseUtil.responseHeader(out, "Content-Type", "application/json; charset=UTF-8");
		ResponseUtil.writeApiSecurityHeaders(out);
		ResponseUtil.writeSetCookie(out, SessionStore.COOKIE_NAME, "", 0);
		ResponseUtil.endResponseHeader(out);
		out.write(JsonUtil.ok());
		ResponseUtil.endResponse(out);
		return null;
	}

	@Controller(path = "/me", matchAll = true, note = "当前登录态（鉴权通过即返回 ok）")
	public String me(BufferedWriter out) throws IOException {
		SessionStore.Session sess = SessionStore.CURRENT.get();
		String user = sess != null ? sess.username : Global.webAuthUsername;
		String role = sess != null ? sess.role : "user";
		ResponseUtil.writeJson(out, JsonUtil.okData("{" + JsonUtil.kv("username", user) + "," + JsonUtil.kv("role", role) + "}"));
		return null;
	}

	private static String extract(String body, String key) {
		if (body == null) return null;
		// 支持 application/x-www-form-urlencoded 与简单 JSON
		// JSON: "key":"value"
		String pat = "\"" + key + "\"";
		int i = body.indexOf(pat);
		if (i >= 0) {
			int colon = body.indexOf(':', i + pat.length());
			if (colon < 0) return null;
			int q1 = body.indexOf('"', colon + 1);
			if (q1 < 0) return null;
			int q2 = body.indexOf('"', q1 + 1);
			if (q2 < 0) return null;
			return body.substring(q1 + 1, q2);
		}
		// form
		for (String part : body.split("&")) {
			int eq = part.indexOf('=');
			if (eq > 0 && key.equals(part.substring(0, eq))) {
				try {
					return java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8");
				} catch (Exception e) { return part.substring(eq + 1); }
			}
		}
		return null;
	}
}
