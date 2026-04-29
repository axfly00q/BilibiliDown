package nicelee.server.auth;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;

import nicelee.bilibili.util.HttpCookies;
import nicelee.server.util.ResponseUtil;
import nicelee.ui.Global;

/**
 * Web 控制台鉴权过滤器（v2，已弃用密码鉴权）。
 *
 * 设计：
 *  - 默认所有控制台 / API 不需要密码（webAuthEnable=false）
 *  - 仅以下 "需要 B 站账号" 的 API 在未登录 B 站时拒绝：
 *      /api/history*, /api/parse-history*, /api/fav*
 *  - HTML 页面始终允许加载，由前端检测 B 站登录态后自行渲染 "请先登录"
 *  - 兼容老路径：若有人显式开启 webAuthEnable，再走旧的 Cookie 校验逻辑
 */
public class AuthFilter {

	public static SessionStore.Session handle(BufferedWriter out, String path, HashMap<String, String> headersMap) throws IOException {
		SessionStore.Session result = handle0(out, path, headersMap);
		SessionStore.CURRENT.set(result);
		return result;
	}

	private static SessionStore.Session handle0(BufferedWriter out, String path, HashMap<String, String> headersMap) throws IOException {
		if (path == null) return null;

		// 1. 受 B 站登录态保护的 API
		if (isBiliRestrictedApi(path)) {
			boolean biliLogin = Global.isLogin && HttpCookies.getGlobalCookies() != null;
			if (!biliLogin) {
				ResponseUtil.writeJsonStatus(out, 401, "{\"code\":401,\"message\":\"need bilibili login\"}");
				return null;
			}
		}

		// 2. 已弃用的旧密码鉴权（仅当显式开启时启用）
		if (Global.webAuthEnable) {
			return legacyPwdAuth(out, path, headersMap);
		}

		return new SessionStore.Session("anon", "user");
	}

	private static boolean isBiliRestrictedApi(String path) {
		return path.startsWith("/api/history")
				|| path.startsWith("/api/parse-history")
				|| path.startsWith("/api/fav");
	}

	// ---------- 以下为旧版密码鉴权，保留以兼容 webAuthEnable=true 配置 ----------

	private static SessionStore.Session legacyPwdAuth(BufferedWriter out, String path, HashMap<String, String> headersMap) throws IOException {
		if (isPublic(path)) return new SessionStore.Session("anon", "user");

		String cookie = headersMap.get("cookie");
		String sid = SessionStore.parseCookie(cookie, SessionStore.COOKIE_NAME);
		SessionStore.Session sess = SessionStore.get(sid);
		if (sess != null) return sess;

		if (path.startsWith("/api/") || path.startsWith("/sse/")) {
			ResponseUtil.writeJsonStatus(out, 401, "{\"code\":401,\"message\":\"unauthorized\"}");
		} else if (path.startsWith("/console/") || path.startsWith("/files/")) {
			ResponseUtil.response302(out, "/console/account.html");
			ResponseUtil.endResponseHeader(out);
		} else {
			ResponseUtil.response401(out);
			ResponseUtil.endResponseHeader(out);
		}
		return null;
	}

	private static boolean isPublic(String path) {
		if (path.equals("/console/account.html")) return true;
		if (path.equals("/api/auth/login") || path.equals("/api/auth/logout")) return true;
		if (path.startsWith("/console/js/")) return true;
		if (path.startsWith("/console/css/")) return true;
		if (path.startsWith("/static/")) return true;
		if (path.startsWith("/cookieRefresh/")) return true;
		return false;
	}
}
