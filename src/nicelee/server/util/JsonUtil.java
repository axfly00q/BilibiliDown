package nicelee.server.util;

/**
 * 极简 JSON 字符串工具：仅用于 Web 控制台 API 输出。
 * 不引入大量依赖；复杂结构使用 org.json。
 */
public class JsonUtil {

	public static String escape(String s) {
		if (s == null) return "";
		StringBuilder sb = new StringBuilder(s.length() + 8);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '\\': sb.append("\\\\"); break;
				case '"': sb.append("\\\""); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				case '\b': sb.append("\\b"); break;
				case '\f': sb.append("\\f"); break;
				default:
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
			}
		}
		return sb.toString();
	}

	/** "key":"value" */
	public static String kv(String key, String value) {
		return "\"" + escape(key) + "\":\"" + escape(value) + "\"";
	}

	/** "key":number */
	public static String kvN(String key, long value) {
		return "\"" + escape(key) + "\":" + value;
	}

	public static String kvB(String key, boolean value) {
		return "\"" + escape(key) + "\":" + (value ? "true" : "false");
	}

	public static String ok() {
		return "{\"code\":0,\"message\":\"ok\"}";
	}

	public static String okData(String dataJson) {
		return "{\"code\":0,\"message\":\"ok\",\"data\":" + dataJson + "}";
	}

	public static String err(int code, String message) {
		return "{\"code\":" + code + ",\"message\":\"" + escape(message) + "\"}";
	}
}
