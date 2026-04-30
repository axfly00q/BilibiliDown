package nicelee.server.auth;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流：单 IP 在窗口期内累计失败超过阈值后，临时锁定一段时间。
 *
 * 设计要点：
 *  - 全部内存态，重启清零，符合"密码也是临时生成"的现状。
 *  - 用 ConcurrentHashMap 保证并发；定期 sweep 过期项，避免攻击者用大量 IP 撑爆内存。
 *  - 阈值默认：5 分钟内 5 次失败 → 锁定 15 分钟（远大于人类输错的合理范围，但仍给暴力破解致命减速）。
 */
public class LoginRateLimiter {

	/** 失败统计窗口（毫秒） */
	public static final long WINDOW_MS = 5 * 60 * 1000L;
	/** 触发锁定的失败次数阈值 */
	public static final int FAIL_THRESHOLD = 5;
	/** 锁定时长（毫秒） */
	public static final long LOCK_MS = 15 * 60 * 1000L;
	/** 整个表上限，超过即触发整体清理（防御内存攻击） */
	private static final int MAX_ENTRIES = 4096;

	private static final ConcurrentHashMap<String, Record> RECORDS = new ConcurrentHashMap<>();
	private static volatile long lastSweep = 0L;

	private static class Record {
		volatile int failCount;
		volatile long firstFailAt;
		volatile long lockUntil;
	}

	/**
	 * @return 若该 IP 当前处于锁定状态，返回剩余秒数；否则返回 0
	 */
	public static long lockedSecondsRemaining(String ip) {
		if (ip == null || ip.isEmpty()) return 0;
		Record r = RECORDS.get(ip);
		if (r == null) return 0;
		long now = System.currentTimeMillis();
		if (r.lockUntil > now) return (r.lockUntil - now + 999) / 1000;
		return 0;
	}

	/** 记录一次失败；若达到阈值则进入锁定。 */
	public static void onFailure(String ip) {
		if (ip == null || ip.isEmpty()) return;
		long now = System.currentTimeMillis();
		Record r = RECORDS.computeIfAbsent(ip, k -> new Record());
		synchronized (r) {
			if (r.lockUntil > now) return; // 已锁定就不再累加
			if (now - r.firstFailAt > WINDOW_MS) {
				r.failCount = 0;
				r.firstFailAt = now;
			}
			r.failCount++;
			if (r.failCount >= FAIL_THRESHOLD) {
				r.lockUntil = now + LOCK_MS;
			}
		}
		maybeSweep();
	}

	/** 登录成功 / 主动清理：清空该 IP 的失败记录与锁定。 */
	public static void onSuccess(String ip) {
		if (ip == null || ip.isEmpty()) return;
		RECORDS.remove(ip);
	}

	/** 周期性清理过期项。 */
	private static void maybeSweep() {
		long now = System.currentTimeMillis();
		if (now - lastSweep < 60_000L && RECORDS.size() < MAX_ENTRIES) return;
		lastSweep = now;
		Iterator<Map.Entry<String, Record>> it = RECORDS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Record> e = it.next();
			Record r = e.getValue();
			boolean lockExpired = r.lockUntil <= now;
			boolean windowExpired = (now - r.firstFailAt) > WINDOW_MS;
			if (lockExpired && windowExpired) it.remove();
		}
	}
}
