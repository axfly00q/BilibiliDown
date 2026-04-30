package nicelee.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记录每个任务最近一次失败原因（按 avId-pPage 作为 key）。
 * 仅供 Web 控制台弹窗显示，不持久化。
 */
public class TaskErrorStore {

	private static final int MAX_ENTRIES = 512;
	private static final Map<String, String> ERRORS = new ConcurrentHashMap<>();

	public static void put(String key, String message) {
		if (key == null || key.isEmpty() || message == null) return;
		if (ERRORS.size() >= MAX_ENTRIES) {
			// 简单截断：清空一半（粗暴但够用）
			int kept = 0;
			for (String k : ERRORS.keySet().toArray(new String[0])) {
				if (++kept > MAX_ENTRIES / 2) ERRORS.remove(k);
			}
		}
		// 截断过长内容
		String safe = message.length() > 4000 ? message.substring(0, 4000) + "...(truncated)" : message;
		ERRORS.put(key, safe);
	}

	public static String get(String key) {
		if (key == null) return null;
		return ERRORS.get(key);
	}

	public static void clear(String key) {
		if (key != null) ERRORS.remove(key);
	}
}
