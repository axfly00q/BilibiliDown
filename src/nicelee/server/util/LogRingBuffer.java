package nicelee.server.util;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 进程内日志环形缓冲区：拦截 java.lang.System.out / System.err，
 * 将每行日志（带递增 seq + 级别 + 时间戳）保留在内存中，供 /api/logs 增量拉取。
 *
 * - 不引入第三方日志框架，零依赖
 * - 同时把日志原样写到原 PrintStream，桌面端 / 终端用户体验不变
 * - 容量默认 2000 行；超出后丢弃最旧的
 *
 * 使用：在 main() 最开头调用 {@link #install()}（必须早于 nicelee.bilibili.util.custom.System
 *      与 Global 的 static 初始化，否则 custom.System.out 会缓存原始 PrintStream）。
 */
public class LogRingBuffer {

	public static final int CAPACITY = 2000;

	public static class Entry {
		public final long seq;
		public final long ts;
		public final String level; // INFO / ERROR
		public final String text;
		public Entry(long seq, long ts, String level, String text) {
			this.seq = seq; this.ts = ts; this.level = level; this.text = text;
		}
	}

	private static final Object LOCK = new Object();
	private static final Entry[] BUFFER = new Entry[CAPACITY];
	private static long nextSeq = 1;
	private static int writeIdx = 0;
	private static int size = 0;
	private static volatile boolean installed = false;

	/**
	 * 接管 System.out / System.err。可重复调用，幂等。
	 */
	public static synchronized void install() {
		if (installed) return;
		PrintStream origOut = System.out;
		PrintStream origErr = System.err;
		System.setOut(new PrintStream(new TeeStream(origOut, "INFO"), true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(new TeeStream(origErr, "ERROR"), true, StandardCharsets.UTF_8));
		installed = true;
	}

	public static boolean isInstalled() { return installed; }

	private static void append(String level, String line) {
		if (line == null || line.isEmpty()) return;
		long now = System.currentTimeMillis();
		synchronized (LOCK) {
			BUFFER[writeIdx] = new Entry(nextSeq++, now, level, line);
			writeIdx = (writeIdx + 1) % CAPACITY;
			if (size < CAPACITY) size++;
		}
	}

	/**
	 * 拉取 seq 严格大于 since 的日志条目；可按级别过滤；最多返回 limit 条。
	 */
	public static List<Entry> tail(long since, String levelFilter, int limit) {
		List<Entry> result = new ArrayList<>(Math.min(limit, 256));
		synchronized (LOCK) {
			int start = (writeIdx - size + CAPACITY) % CAPACITY;
			for (int i = 0; i < size; i++) {
				Entry e = BUFFER[(start + i) % CAPACITY];
				if (e == null) continue;
				if (e.seq <= since) continue;
				if (levelFilter != null && !levelFilter.isEmpty()
						&& !levelFilter.equalsIgnoreCase(e.level)) continue;
				result.add(e);
				if (result.size() >= limit) break;
			}
		}
		return result;
	}

	/** 当前最大 seq，前端首次进入时用作 baseline。 */
	public static long currentSeq() {
		synchronized (LOCK) { return nextSeq - 1; }
	}

	/**
	 * Tee：把字节同时写给原 PrintStream 与按行切分到环形缓冲。
	 * 累积原始字节，遇到 '\n' 时按系统默认字符集（即 PrintStream 实际使用的编码）解码成一行，
	 * 这样可以正确处理 UTF-8 / GBK 等多字节编码的中文。
	 */
	private static class TeeStream extends OutputStream {
		private final PrintStream original;
		private final String level;
		private final java.io.ByteArrayOutputStream lineBuf = new java.io.ByteArrayOutputStream(256);

		TeeStream(PrintStream original, String level) {
			this.original = original;
			this.level = level;
		}

		@Override
		public void write(int b) {
			original.write(b);
			synchronized (lineBuf) {
				if (b == '\n') {
					flushLine();
				} else if (b != '\r') {
					if (lineBuf.size() < 8192) lineBuf.write(b);
				}
			}
		}

		@Override
		public void write(byte[] b, int off, int len) {
			original.write(b, off, len);
			synchronized (lineBuf) {
				int start = off;
				int end = off + len;
				for (int i = off; i < end; i++) {
					if (b[i] == '\n') {
						int chunk = i - start;
						int cap = 8192 - lineBuf.size();
						if (chunk > 0 && cap > 0) lineBuf.write(b, start, Math.min(chunk, cap));
						flushLine();
						start = i + 1;
					}
				}
				if (start < end) {
					int chunk = end - start;
					int cap = 8192 - lineBuf.size();
					if (cap > 0) lineBuf.write(b, start, Math.min(chunk, cap));
				}
			}
		}

		@Override public void flush() { original.flush(); }

		private void flushLine() {
			String s = new String(lineBuf.toByteArray(), java.nio.charset.Charset.defaultCharset());
			lineBuf.reset();
			// 去除可能的尾部 \r（CRLF 行尾）
			if (!s.isEmpty() && s.charAt(s.length() - 1) == '\r') s = s.substring(0, s.length() - 1);
			append(level, s);
		}
	}
}

