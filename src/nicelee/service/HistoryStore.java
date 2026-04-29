package nicelee.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import nicelee.bilibili.util.ResourcesUtil;
import nicelee.server.util.JsonUtil;

/**
 * 历史记录存储（下载历史 + 解析历史）。
 * 文件格式：JSON 数组，整体加载/整体写回；并发用 synchronized 串行化（写量低）。
 * 单文件容量上限按调用方传入；超出后按时间淘汰最早。
 */
public class HistoryStore {

	public static final HistoryStore DOWNLOAD = new HistoryStore("./config/download-history.json", 100);
	public static final HistoryStore PARSE    = new HistoryStore("./config/parse-history.json", 30);

	private final String relPath;
	private final int max;
	private final LinkedList<Entry> items = new LinkedList<>();
	private boolean loaded = false;

	public static class Entry {
		public String key;          // 用于去重（下载: id；解析: avId）
		public String type;         // download / parse
		public String title;
		public String avId;
		public Integer page;
		public Integer qn;
		public String absPath;
		public String relPath;
		public Long size;
		public String status;       // done / fail / parsed
		public long ts;
		public String input;        // 解析时原始输入
	}

	private HistoryStore(String relPath, int max) {
		this.relPath = relPath;
		this.max = max;
	}

	private synchronized void ensureLoaded() {
		if (loaded) return;
		loaded = true;
		try {
			File f = ResourcesUtil.sourceOf(relPath);
			if (!f.exists()) return;
			StringBuilder sb = new StringBuilder();
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) sb.append(line);
			}
			parseInto(sb.toString(), items);
		} catch (Exception ignored) {}
	}

	public synchronized List<Entry> list() {
		ensureLoaded();
		List<Entry> snap = new ArrayList<>(items);
		Collections.reverse(snap);   // 新→旧
		return snap;
	}

	public synchronized void add(Entry e) {
		if (e == null || e.key == null) return;
		ensureLoaded();
		// 去重：同 key 移除旧
		items.removeIf(x -> e.key.equals(x.key));
		items.add(e);
		while (items.size() > max) items.removeFirst();
		persist();
	}

	public synchronized boolean remove(String key) {
		ensureLoaded();
		boolean changed = items.removeIf(x -> key != null && key.equals(x.key));
		if (changed) persist();
		return changed;
	}

	public synchronized void clear() {
		ensureLoaded();
		items.clear();
		persist();
	}

	private void persist() {
		try {
			File f = ResourcesUtil.sourceOf(relPath);
			File dir = f.getParentFile();
			if (dir != null && !dir.exists()) dir.mkdirs();
			try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
				w.write('[');
				boolean first = true;
				for (Entry e : items) {
					if (!first) w.write(',');
					first = false;
					w.write(toJson(e));
				}
				w.write(']');
			}
		} catch (IOException ignored) {}
	}

	public static String toJson(Entry e) {
		StringBuilder sb = new StringBuilder(160);
		sb.append('{')
			.append(JsonUtil.kv("key", n(e.key))).append(',')
			.append(JsonUtil.kv("type", n(e.type))).append(',')
			.append(JsonUtil.kv("title", n(e.title))).append(',')
			.append(JsonUtil.kv("avId", n(e.avId))).append(',')
			.append(JsonUtil.kvN("page", e.page == null ? 0 : e.page)).append(',')
			.append(JsonUtil.kvN("qn", e.qn == null ? 0 : e.qn)).append(',')
			.append(JsonUtil.kv("absPath", n(e.absPath))).append(',')
			.append(JsonUtil.kv("relPath", n(e.relPath))).append(',')
			.append(JsonUtil.kvN("size", e.size == null ? 0 : e.size)).append(',')
			.append(JsonUtil.kv("status", n(e.status))).append(',')
			.append(JsonUtil.kvN("ts", e.ts)).append(',')
			.append(JsonUtil.kv("input", n(e.input)))
			.append('}');
		return sb.toString();
	}

	private static String n(String s) { return s == null ? "" : s; }

	/** 极简 JSON 数组解析：仅支持本类自己写出的扁平对象。 */
	private static void parseInto(String text, LinkedList<Entry> out) {
		if (text == null) return;
		int i = text.indexOf('[');
		if (i < 0) return;
		int depth = 0;
		int objStart = -1;
		boolean inStr = false;
		boolean esc = false;
		for (int p = i; p < text.length(); p++) {
			char c = text.charAt(p);
			if (esc) { esc = false; continue; }
			if (c == '\\') { esc = true; continue; }
			if (c == '"') { inStr = !inStr; continue; }
			if (inStr) continue;
			if (c == '{') { if (depth++ == 0) objStart = p; }
			else if (c == '}') {
				if (--depth == 0 && objStart >= 0) {
					Entry e = parseObject(text.substring(objStart, p + 1));
					if (e != null) out.add(e);
					objStart = -1;
				}
			}
		}
	}

	private static Entry parseObject(String obj) {
		Entry e = new Entry();
		e.key = strField(obj, "key");
		e.type = strField(obj, "type");
		e.title = strField(obj, "title");
		e.avId = strField(obj, "avId");
		e.absPath = strField(obj, "absPath");
		e.relPath = strField(obj, "relPath");
		e.status = strField(obj, "status");
		e.input = strField(obj, "input");
		Long page = numField(obj, "page"); e.page = page == null ? 0 : page.intValue();
		Long qn = numField(obj, "qn");     e.qn = qn == null ? 0 : qn.intValue();
		e.size = numField(obj, "size");
		Long ts = numField(obj, "ts");     e.ts = ts == null ? 0L : ts;
		if (e.key == null || e.key.isEmpty()) return null;
		return e;
	}

	private static String strField(String obj, String key) {
		String pat = "\"" + key + "\":\"";
		int i = obj.indexOf(pat);
		if (i < 0) return null;
		int p = i + pat.length();
		StringBuilder sb = new StringBuilder();
		while (p < obj.length()) {
			char c = obj.charAt(p);
			if (c == '\\' && p + 1 < obj.length()) {
				char n = obj.charAt(p + 1);
				if (n == 'n') sb.append('\n');
				else if (n == 'r') sb.append('\r');
				else if (n == 't') sb.append('\t');
				else sb.append(n);
				p += 2;
			} else if (c == '"') return sb.toString();
			else { sb.append(c); p++; }
		}
		return sb.toString();
	}

	private static Long numField(String obj, String key) {
		String pat = "\"" + key + "\":";
		int i = obj.indexOf(pat);
		if (i < 0) return null;
		int p = i + pat.length();
		while (p < obj.length() && Character.isWhitespace(obj.charAt(p))) p++;
		int s = p;
		if (s < obj.length() && obj.charAt(s) == '"') return null;  // 不该是字符串
		while (p < obj.length()) {
			char c = obj.charAt(p);
			if (c == '-' || (c >= '0' && c <= '9')) p++;
			else break;
		}
		if (p == s) return null;
		try { return Long.parseLong(obj.substring(s, p)); } catch (Exception e) { return null; }
	}
}
