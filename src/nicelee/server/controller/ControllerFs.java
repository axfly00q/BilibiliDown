package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.ui.Global;

/**
 * Web 控制台 - 服务器端文件系统浏览（仅用于设置页选择"下载保存路径"）。
 *
 * 端点：
 *   GET  /api/fs/savePath               => { abs, raw }   当前 Global.savePath 的绝对路径与原值
 *   GET  /api/fs/list?path=<absDir>     => { cwd, parent, drives:[...], dirs:[...] }
 *                                          path 缺省时：Windows 返回所有盘符；其他系统返回根目录列表
 *   POST /api/fs/mkdir  body { path, name } => { path: <new abs path> }
 *
 * 鉴权由 ControllerLogin 的过滤器统一处理（与其他 /api/ 接口一致）。
 */
@Controller(path = "/api/fs", note = "Web 控制台 - 服务器端目录浏览")
public class ControllerFs {

	private static final boolean IS_WIN = System.getProperty("os.name", "").toLowerCase().startsWith("win");

	@Controller(path = "/savePath", matchAll = true, note = "当前下载根目录")
	public String savePath(BufferedWriter out) throws IOException {
		String raw = Global.savePath == null ? "" : Global.savePath;
		String abs;
		try {
			abs = new File(raw.isEmpty() ? "." : raw).getCanonicalPath();
		} catch (IOException e) {
			abs = new File(raw.isEmpty() ? "." : raw).getAbsolutePath();
		}
		StringBuilder sb = new StringBuilder();
		sb.append('{')
			.append(JsonUtil.kv("raw", raw)).append(',')
			.append(JsonUtil.kv("abs", abs))
			.append('}');
		ResponseUtil.writeJson(out, JsonUtil.okData(sb.toString()));
		return null;
	}

	@Controller(path = "/list", matchAll = true, note = "列出某目录下所有子目录")
	public String list(BufferedWriter out, @Value(key = "path") String path) throws IOException {
		String p = path == null ? "" : path.trim();
		// 框架不会自动 URL-decode 查询参数，这里手动解一次
		if (!p.isEmpty()) {
			try { p = java.net.URLDecoder.decode(p, "UTF-8"); } catch (Exception ignored) {}
		}
		// 没指定 path → 根列表
		if (p.isEmpty()) {
			ResponseUtil.writeJson(out, JsonUtil.okData(rootJson()));
			return null;
		}
		File dir = new File(p);
		if (!dir.exists()) {
			ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "path not exists: " + p));
			return null;
		}
		if (!dir.isDirectory()) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "not a directory: " + p));
			return null;
		}
		String cwd, parent;
		try {
			cwd = dir.getCanonicalPath();
		} catch (IOException e) {
			cwd = dir.getAbsolutePath();
		}
		File parentFile = new File(cwd).getParentFile();
		parent = parentFile == null ? "" : parentFile.getAbsolutePath();

		List<String> dirs = new ArrayList<>();
		File[] children = dir.listFiles();
		if (children != null) {
			for (File c : children) {
				if (c.isDirectory() && !c.isHidden()) dirs.add(c.getName());
			}
		}
		Collections.sort(dirs, String.CASE_INSENSITIVE_ORDER);

		StringBuilder sb = new StringBuilder();
		sb.append('{')
			.append(JsonUtil.kv("cwd", cwd)).append(',')
			.append(JsonUtil.kv("parent", parent)).append(',')
			.append("\"drives\":").append(drivesJson()).append(',')
			.append("\"dirs\":").append(strArrayJson(dirs))
			.append('}');
		ResponseUtil.writeJson(out, JsonUtil.okData(sb.toString()));
		return null;
	}

	@Controller(path = "/open", matchAll = true, note = "在服务器本地打开文件资源管理器定位到指定路径")
	public String open(BufferedWriter out, @Value(key = "path") String path) throws IOException {
		String p = path == null ? "" : path.trim();
		if (!p.isEmpty()) {
			try { p = java.net.URLDecoder.decode(p, "UTF-8"); } catch (Exception ignored) {}
		}
		File target;
		if (p.isEmpty()) {
			target = new File(Global.savePath == null ? "." : Global.savePath);
		} else {
			target = new File(p);
		}
		if (!target.exists()) {
			ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "path not exists"));
			return null;
		}
		try {
			File dir = target.isDirectory() ? target : target.getParentFile();
			if (IS_WIN) {
				if (target.isDirectory()) {
					new ProcessBuilder("explorer.exe", target.getAbsolutePath()).start();
				} else {
					new ProcessBuilder("explorer.exe", "/select,", target.getAbsolutePath()).start();
				}
			} else if (java.awt.Desktop.isDesktopSupported()) {
				java.awt.Desktop.getDesktop().open(dir);
			}
			ResponseUtil.writeJson(out, JsonUtil.ok());
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, e.getMessage()));
		}
		return null;
	}

	@Controller(path = "/mkdir", matchAll = true, note = "在指定目录下新建子目录")
	public String mkdir(BufferedWriter out,
			@Value(key = "postData") String body) throws IOException {
		String parent = jsonStr(body, "path");
		String name = jsonStr(body, "name");
		if (parent == null || parent.isEmpty() || name == null || name.isEmpty()) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "missing path or name"));
			return null;
		}
		// 拒绝路径分隔符，防止越权
		if (name.contains("/") || name.contains("\\") || name.contains("..") || name.contains("\0")) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "invalid name"));
			return null;
		}
		File parentDir = new File(parent);
		if (!parentDir.isDirectory()) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "parent not a directory"));
			return null;
		}
		File target = new File(parentDir, name);
		if (!target.exists() && !target.mkdirs()) {
			ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, "mkdir failed"));
			return null;
		}
		String abs;
		try { abs = target.getCanonicalPath(); } catch (IOException e) { abs = target.getAbsolutePath(); }
		ResponseUtil.writeJson(out, JsonUtil.okData("{" + JsonUtil.kv("path", abs) + "}"));
		return null;
	}

	// ---------- helpers ----------

	private String rootJson() {
		StringBuilder sb = new StringBuilder();
		sb.append('{')
			.append(JsonUtil.kv("cwd", "")).append(',')
			.append(JsonUtil.kv("parent", "")).append(',')
			.append("\"drives\":").append(drivesJson()).append(',')
			.append("\"dirs\":[]")
			.append('}');
		return sb.toString();
	}

	private String drivesJson() {
		List<String> drives = new ArrayList<>();
		if (IS_WIN) {
			File[] roots = File.listRoots();
			if (roots != null) {
				for (File r : roots) drives.add(r.getAbsolutePath());
			}
		} else {
			drives.add("/");
		}
		Collections.sort(drives);
		return strArrayJson(drives);
	}

	private static String strArrayJson(List<String> list) {
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) sb.append(',');
			sb.append('"').append(escape(list.get(i))).append('"');
		}
		sb.append(']');
		return sb.toString();
	}

	private static String escape(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '\\': sb.append("\\\\"); break;
				case '"': sb.append("\\\""); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				default:
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
			}
		}
		return sb.toString();
	}

	private static String jsonStr(String body, String key) {
		if (body == null) return null;
		String pat = "\"" + key + "\"";
		int i = body.indexOf(pat);
		if (i < 0) return null;
		int colon = body.indexOf(':', i + pat.length());
		if (colon < 0) return null;
		int j = colon + 1;
		while (j < body.length() && Character.isWhitespace(body.charAt(j))) j++;
		if (j >= body.length() || body.charAt(j) != '"') return null;
		StringBuilder sb = new StringBuilder();
		j++;
		while (j < body.length()) {
			char c = body.charAt(j);
			if (c == '\\' && j + 1 < body.length()) {
				char n = body.charAt(j + 1);
				if (n == 'n') sb.append('\n');
				else if (n == 'r') sb.append('\r');
				else if (n == 't') sb.append('\t');
				else sb.append(n);
				j += 2;
			} else if (c == '"') {
				return sb.toString();
			} else {
				sb.append(c);
				j++;
			}
		}
		return null;
	}

	@SuppressWarnings("unused")
	private static List<String> safeList(File[] arr) {
		return arr == null ? new ArrayList<>() : Arrays.asList(Arrays.stream(arr).map(File::getName).toArray(String[]::new));
	}
}
