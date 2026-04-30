package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.bilibili.util.ConfigUtil;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.ui.Global;

/**
 * Web 控制台 - 设置接口。仅暴露常用、改了立刻生效的字段；其余字段建议在桌面端修改。
 *  - GET  /api/settings/list   读取当前值 + schema
 *  - POST /api/settings/save   body: { key: value, ... } 仅允许白名单 key
 */
@Controller(path = "/api/settings", note = "Web 控制台 - 设置")
public class ControllerSettings {

	/** 字段元数据：key, type(string|int|bool|select), valids(逗号分隔, select用), note, desc(详细说明) */
	private static final String[][] SCHEMA = new String[][] {
			{ "bilibili.savePath", "string", "", "下载文件保存路径（重启生效）",
					"所有视频/音频/封面下载后保存的根目录。可填相对路径（相对于程序运行目录）或绝对路径。修改后需重启程序生效。" },
			{ "bilibili.download.poolSize", "int", "", "最大同时下载任务数（重启生效）",
					"同一时刻并行下载的任务数量。值越大下载越快，但占用带宽和 CPU 越多，过大可能触发 B 站限流。建议 2~5。修改后需重启生效。" },
			{ "bilibili.download.maxFailRetry", "int", "", "下载失败重试次数",
					"单个分片或任务下载失败时的自动重试次数。网络不稳定时可调大。0 表示不重试。" },
			{ "bilibili.format", "select", "0,1,2", "优先下载格式 0=MP4(合并) 1=FLV 2=MP4(单流)",
					"0：dash 流分别下载视频/音频再用 ffmpeg 合并成 mp4（推荐，画质最好）；1：FLV 单文件，老格式，部分清晰度不可用；2：MP4 单流，无需 ffmpeg 但可选清晰度少。" },
			{ "bilibili.name.format", "string", "", "下载文件命名格式（语法详见 app.config）",
					"占位符如 avTitle/avId/upName/listName/favTime 等会被实际值替换；详见 app.config 注释。例如 (:listName 0_listName\\)\\\\UpName\\\\avTitle。" },
			{ "bilibili.repo", "select", "on,off", "是否使用仓库去重",
					"on：在提交下载前先检查 download-history.json，已下载过的视频会跳过；off：每次都重新下载。" },
			{ "bilibili.repo.save", "select", "on,off", "是否保存下载记录",
					"on：成功下载后写入 download-history.json，作为后续去重依据；off：不写记录（关闭后去重也会失效）。" },
			{ "bilibili.alert.isAlertIfDownloded", "bool", "true,false", "已下载视频再次提交时弹窗提示",
					"true：在桌面端再次解析/提交一个已下载过的视频时弹窗提醒；false：静默重复下载（仍受去重影响）。" },
			{ "bilibili.web.auth.enable", "bool", "true,false", "Web 控制台启用账号密码鉴权（重启生效）",
					"true：访问 /console/* 需要登录（账号密码下两项配置）；false：任何人访问本机 8787 端口都可使用控制台（仅本地访问时较安全）。" },
			{ "bilibili.web.auth.username", "string", "", "Web 控制台登录用户名",
					"启用鉴权后用于登录 Web 控制台的用户名，默认 admin。" },
			{ "bilibili.web.auth.password", "string", "", "Web 控制台登录密码（留空将自动生成）",
					"启用鉴权后用于登录 Web 控制台的密码。留空时程序首次启动会随机生成并打印到日志/桌面端。" },
			{ "bilibili.userAgent.pc", "string", "", "HTTP 请求 User-Agent",
					"程序请求 B 站 API 时使用的 UA，默认是 Firefox。除非遇到反爬错误，一般不要改动。" },
			{ "bilibili.frontend.lang", "select", "auto,zh-CN,en-US,ja-JP", "前端页面语言",
					"auto：跟随浏览器；zh-CN/en-US/ja-JP：强制指定语言。修改后下次刷新页面生效。" },
			{ "bilibili.danmu.keepXml", "bool", "true,false", "下载弹幕时保留 XML 原文件",
					"true：除转换出 ASS 字幕外，同时保留 B 站原始 XML 弹幕文件；false：仅保留 ASS。" },
			{ "bilibili.cv.exportHtml", "bool", "true,false", "解析专栏时同时导出 HTML 文章",
					"true：解析 cv 专栏时，把标题/正文段落/图片导出为 {savePath}/{author}/cv{id}.html，便于离线阅读（图片仍是远程外链）；false：不导出。" },
	};

	@Controller(path = "/list", matchAll = true, note = "GET 读取当前设置")
	public String list(BufferedWriter out) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		for (int i = 0; i < SCHEMA.length; i++) {
			String[] row = SCHEMA[i];
			String key = row[0];
			String value = Global.settings.get(key);
			if (value == null || value.isEmpty()) {
				value = readGlobalAsString(key);
			}
			if (value == null) value = "";
			if (i > 0) sb.append(',');
			sb.append('{')
					.append(JsonUtil.kv("key", key)).append(',')
					.append(JsonUtil.kv("type", row[1])).append(',')
					.append(JsonUtil.kv("valids", row[2])).append(',')
					.append(JsonUtil.kv("note", row[3])).append(',')
					.append(JsonUtil.kv("desc", row.length > 4 ? row[4] : "")).append(',')
					.append(JsonUtil.kv("value", value))
					.append('}');
		}
		sb.append(']');
		ResponseUtil.writeJson(out, JsonUtil.okData(sb.toString()));
		return null;
	}

	/** 当 settings map 没有该 key 时，从 Global 静态字段读取实际生效值 */
	private static String readGlobalAsString(String key) {
		try {
			Object v;
			switch (key) {
				case "bilibili.savePath": v = Global.savePath; break;
				case "bilibili.download.maxFailRetry": v = Global.maxFailRetry; break;
				case "bilibili.format": v = Global.downloadFormat; break;
				case "bilibili.name.format": v = Global.formatStr; break;
				case "bilibili.repo": v = Global.useRepo ? "on" : "off"; break;
				case "bilibili.repo.save": v = Global.saveToRepo ? "on" : "off"; break;
				case "bilibili.alert.isAlertIfDownloded": v = Global.isAlertIfDownloded; break;
				case "bilibili.web.auth.enable": v = Global.webAuthEnable; break;
				case "bilibili.web.auth.username": v = Global.webAuthUsername; break;
				case "bilibili.web.auth.password": v = Global.webAuthPassword; break;
				case "bilibili.userAgent.pc": v = Global.userAgent; break;
				case "bilibili.frontend.lang": v = Global.frontendLang; break;
				case "bilibili.danmu.keepXml": v = Global.danmuKeepXml; break;
				case "bilibili.cv.exportHtml": v = Global.cvExportHtml; break;
				default: return null;
			}
			return v == null ? "" : String.valueOf(v);
		} catch (Throwable t) { return null; }
	}

	@Controller(path = "/save", matchAll = true, note = "POST 保存设置, body=JSON {key:value,...}")
	public String save(BufferedWriter out,
			@Value(key = "postData") String body) throws IOException {
		if (body == null || body.isEmpty()) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "empty body"));
			return null;
		}
		Map<String, String> incoming = parseFlatJson(body);
		if (incoming.isEmpty()) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "no recognizable fields"));
			return null;
		}
		// 校验
		Map<String, String[]> schemaMap = new LinkedHashMap<>();
		for (String[] row : SCHEMA) schemaMap.put(row[0], row);

		StringBuilder errs = new StringBuilder();
		Map<String, String> applied = new LinkedHashMap<>();
		for (Map.Entry<String, String> e : incoming.entrySet()) {
			String key = e.getKey();
			String val = e.getValue() == null ? "" : e.getValue().trim();
			String[] row = schemaMap.get(key);
			if (row == null) { append(errs, key + ": not allowed"); continue; }
			String type = row[1];
			if ("int".equals(type)) {
				try { Integer.parseInt(val); } catch (Exception ex) { append(errs, key + ": not int"); continue; }
			} else if ("bool".equals(type)) {
				if (!"true".equalsIgnoreCase(val) && !"false".equalsIgnoreCase(val)) {
					append(errs, key + ": not bool"); continue;
				}
			} else if ("select".equals(type)) {
				boolean ok = false;
				for (String v : row[2].split(",")) if (v.equals(val)) { ok = true; break; }
				if (!ok) { append(errs, key + ": not in [" + row[2] + "]"); continue; }
			} else {
				// string: 仅做长度限制，避免恶意巨值
				if (val.length() > 4096) { append(errs, key + ": too long"); continue; }
			}
			applied.put(key, val);
		}
		if (errs.length() > 0) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, errs.toString()));
			return null;
		}
		// 写 Global.settings
		for (Map.Entry<String, String> e : applied.entrySet()) {
			Global.settings.put(e.getKey(), e.getValue());
			applyHot(e.getKey(), e.getValue());
		}
		// 持久化到 app.config
		boolean saved = ConfigUtil.saveConfig();
		if (!saved) {
			ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, "save file failed"));
			return null;
		}
		StringBuilder data = new StringBuilder("{\"saved\":").append(applied.size())
				.append(",\"keys\":[");
		int i = 0;
		for (String k : applied.keySet()) {
			if (i++ > 0) data.append(',');
			data.append('"').append(JsonUtil.escape(k)).append('"');
		}
		data.append("]}");
		ResponseUtil.writeJson(out, JsonUtil.okData(data.toString()));
		return null;
	}

	/** 对部分字段做"热更新"——直接更新内存值，无需重启 */
	private static void applyHot(String key, String val) {
		switch (key) {
			case "bilibili.savePath":
				// 仅修改 settings；savePath 已被 Global.init() 处理过尾部斜杠，
				// 重启后才以新值重新规范化。这里也同步内存，保证 cover 等模块下次保存到新位置。
				if (val != null && !val.isEmpty()) {
					String p = val;
					if (p.endsWith("\\")) p = p.substring(0, p.length() - 1) + "/";
					else if (!p.endsWith("/")) p = p + "/";
					Global.savePath = p;
				}
				break;
			case "bilibili.download.maxFailRetry":
				try { Global.maxFailRetry = Integer.parseInt(val); } catch (Exception ignored) {}
				break;
			case "bilibili.format":
				try { Global.downloadFormat = Integer.parseInt(val); } catch (Exception ignored) {}
				break;
			case "bilibili.name.format":
				Global.formatStr = val;
				break;
			case "bilibili.repo":
				Global.useRepo = "on".equalsIgnoreCase(val);
				break;
			case "bilibili.repo.save":
				Global.saveToRepo = "on".equalsIgnoreCase(val) || Global.useRepo;
				break;
			case "bilibili.alert.isAlertIfDownloded":
				Global.isAlertIfDownloded = "true".equalsIgnoreCase(val);
				break;
			case "bilibili.web.auth.enable":
				Global.webAuthEnable = "true".equalsIgnoreCase(val);
				break;
			case "bilibili.web.auth.username":
				Global.webAuthUsername = val;
				break;
			case "bilibili.web.auth.password":
				Global.webAuthPassword = val;
				break;
			case "bilibili.userAgent.pc":
				Global.userAgent = val;
				break;
			case "bilibili.frontend.lang":
				Global.frontendLang = val;
				break;
			case "bilibili.danmu.keepXml":
				Global.danmuKeepXml = "true".equalsIgnoreCase(val);
				break;
			case "bilibili.cv.exportHtml":
				Global.cvExportHtml = "true".equalsIgnoreCase(val);
				break;
			default:
				// poolSize 等需要重启，不在此处理
				break;
		}
	}

	private static void append(StringBuilder sb, String msg) {
		if (sb.length() > 0) sb.append("; ");
		sb.append(msg);
	}

	/** 简易 JSON object → Map<String,String>，仅支持 {"k":"v","k2":"v2"} 这种平面结构。 */
	private static final Pattern PAT_FIELD = Pattern.compile(
			"\"([A-Za-z0-9_.\\-]+)\"\\s*:\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|(true|false|-?\\d+))");

	private static Map<String, String> parseFlatJson(String body) {
		Map<String, String> map = new LinkedHashMap<>();
		Matcher m = PAT_FIELD.matcher(body);
		while (m.find()) {
			String k = m.group(1);
			String v = m.group(2) != null ? unescapeJson(m.group(2)) : m.group(3);
			map.put(k, v);
		}
		return map;
	}

	private static String unescapeJson(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' && i + 1 < s.length()) {
				char n = s.charAt(++i);
				switch (n) {
					case 'n': sb.append('\n'); break;
					case 't': sb.append('\t'); break;
					case 'r': sb.append('\r'); break;
					case '"': sb.append('"'); break;
					case '\\': sb.append('\\'); break;
					case '/': sb.append('/'); break;
					case 'u':
						if (i + 4 < s.length()) {
							try { sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; }
							catch (Exception e) { sb.append(n); }
						} else sb.append(n);
						break;
					default: sb.append(n);
				}
			} else sb.append(c);
		}
		return sb.toString();
	}
}
