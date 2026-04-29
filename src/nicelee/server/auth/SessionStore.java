package nicelee.server.auth;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Session 存储（进程重启即失效）。
 */
public class SessionStore {

	public static final String COOKIE_NAME = "BD_SESSION";
	public static final int SESSION_TTL_SEC = 12 * 3600; // 12h

	private static final SecureRandom RNG = new SecureRandom();
	private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

	/** 当前请求线程的会话（由 AuthFilter 设置） */
	public static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

	public static class Session {
		public final String id;
		public final String username;
		public final String role;          // "user" or "guest"
		public final long createdAt;
		public volatile long lastAccess;

		public Session(String id, String username) { this(id, username, "user"); }
		public Session(String id, String username, String role) {
			this.id = id;
			this.username = username;
			this.role = role == null ? "user" : role;
			this.createdAt = System.currentTimeMillis();
			this.lastAccess = this.createdAt;
		}
		public boolean isGuest() { return "guest".equals(role); }
	}

	public static String create(String username) { return create(username, "user"); }
	public static String create(String username, String role) {
		byte[] buf = new byte[24];
		RNG.nextBytes(buf);
		StringBuilder sb = new StringBuilder(48);
		for (byte b : buf) sb.append(String.format("%02x", b & 0xff));
		String id = sb.toString();
		SESSIONS.put(id, new Session(id, username, role));
		return id;
	}

	public static Session get(String id) {
		if (id == null) return null;
		Session s = SESSIONS.get(id);
		if (s == null) return null;
		long now = System.currentTimeMillis();
		if (now - s.lastAccess > SESSION_TTL_SEC * 1000L) {
			SESSIONS.remove(id);
			return null;
		}
		s.lastAccess = now;
		return s;
	}

	public static void invalidate(String id) {
		if (id != null) SESSIONS.remove(id);
	}

	/** 从原始 Cookie 头中解析指定名称的值 */
	public static String parseCookie(String cookieHeader, String name) {
		if (cookieHeader == null) return null;
		for (String part : cookieHeader.split(";")) {
			String s = part.trim();
			int eq = s.indexOf('=');
			if (eq <= 0) continue;
			String k = s.substring(0, eq).trim();
			if (k.equalsIgnoreCase(name)) {
				return s.substring(eq + 1).trim();
			}
		}
		return null;
	}
}
