package nicelee.bilibili.pushers.impl;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import nicelee.bilibili.annotations.Bilibili;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.TaskInfo;
import nicelee.bilibili.pushers.IPush;
import nicelee.bilibili.util.Logger;
import nicelee.ui.Global;

/**
 * 通用 Webhook 推送器：把下载汇总结果以 HTTP POST 发送到任意 URL。
 *
 * 同一份代码即可适配 Bark / Server酱 / 钉钉 / 企业微信 / Telegram Bot / Discord / Gotify 等：
 * 它们都是"接收 JSON / 表单 POST"的 HTTP 端点，差异只在 URL 与 body 字段名。
 *
 * 配置 (在 app.config 中)：
 *   bilibili.download.push.type     = Webhook
 *   bilibili.download.push.webhook.url      = https://your.endpoint/path
 *   bilibili.download.push.webhook.method   = POST            (POST/GET, 默认 POST)
 *   bilibili.download.push.webhook.headers  = Content-Type: application/json | Authorization: Bearer xxx
 *                                              （多个 header 用 " | " 分隔）
 *   bilibili.download.push.webhook.template = {"title":"{title}","content":"{content}"}
 *                                              （占位符 {title} {content} {success} {fail} {total} 会被替换）
 *
 * 占位符列表：
 *   {title}   下载汇总短标题（"YYYY-MM-DD HH:MM 新增X个 成功Y失败Z"）
 *   {content} 多行明细（成功/失败的视频列表）
 *   {success} 成功数量
 *   {fail}    失败数量
 *   {total}   总数
 *   {ts}      毫秒时间戳
 */
@Bilibili(name = "WebhookPush", type = "pusher", note = "将结果通过 HTTP POST 推送到 Webhook")
public class WebhookPush implements IPush {

	@Override
	public String type() {
		return "Webhook";
	}

	@Override
	public IPush newInstance() {
		return new WebhookPush();
	}

	@Override
	public void push(Map<ClipInfo, TaskInfo> currentTaskList, long begin, long end) {
		String url = setting("bilibili.download.push.webhook.url", "");
		if (url == null || url.isEmpty()) {
			Logger.println("WebhookPush: 未配置 bilibili.download.push.webhook.url，跳过推送");
			return;
		}
		String method = setting("bilibili.download.push.webhook.method", "POST").toUpperCase();
		String headersRaw = setting("bilibili.download.push.webhook.headers",
				"Content-Type: application/json; charset=UTF-8");
		String template = setting("bilibili.download.push.webhook.template",
				"{\"title\":\"{title}\",\"content\":\"{content}\"}");

		// 汇总
		int successCnt = 0, failCnt = 0;
		List<TaskInfo> successTasks = new ArrayList<>();
		List<TaskInfo> failTasks = new ArrayList<>();
		for (TaskInfo task : currentTaskList.values()) {
			if ("success".equals(task.getStatus())) {
				successCnt++;
				successTasks.add(task);
			} else {
				failCnt++;
				failTasks.add(task);
			}
		}
		int total = successCnt + failCnt;
		String title = String.format("%1$tF %1$tR 新增视频:%2$d个, 成功:%3$d，失败:%4$d",
				new Date(end > 0 ? end : System.currentTimeMillis()), total, successCnt, failCnt);

		StringBuilder content = new StringBuilder();
		if (!successTasks.isEmpty()) {
			content.append("✅ 成功:\n");
			for (TaskInfo t : successTasks) {
				content.append("  - ").append(safe(t.getFileName())).append('\n');
			}
		}
		if (!failTasks.isEmpty()) {
			content.append("❌ 失败:\n");
			for (TaskInfo t : failTasks) {
				ClipInfo c = t.getClip();
				content.append("  - ").append(safe(t.getStatus())).append(' ')
						.append(c == null ? "" : safe(c.getAvId())).append(' ')
						.append(c == null ? "" : safe(c.getAvTitle())).append('\n');
			}
		}
		if (content.length() == 0) content.append("(empty)");

		// 占位符替换
		String body = template
				.replace("{title}", jsonEscape(title))
				.replace("{content}", jsonEscape(content.toString()))
				.replace("{success}", String.valueOf(successCnt))
				.replace("{fail}", String.valueOf(failCnt))
				.replace("{total}", String.valueOf(total))
				.replace("{ts}", String.valueOf(System.currentTimeMillis()));

		// 发送
		try {
			HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
			conn.setRequestMethod(method);
			conn.setConnectTimeout(10_000);
			conn.setReadTimeout(15_000);
			conn.setInstanceFollowRedirects(true);
			// headers
			for (String h : headersRaw.split("\\s*\\|\\s*")) {
				int colon = h.indexOf(':');
				if (colon > 0) {
					conn.setRequestProperty(h.substring(0, colon).trim(), h.substring(colon + 1).trim());
				}
			}
			if (!"GET".equals(method)) {
				conn.setDoOutput(true);
				try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
					dos.write(body.getBytes(StandardCharsets.UTF_8));
				}
			}
			int code = conn.getResponseCode();
			InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
			StringBuilder resp = new StringBuilder();
			if (in != null) {
				try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
					String line;
					int max = 0;
					while ((line = r.readLine()) != null && max++ < 20) {
						resp.append(line).append('\n');
					}
				}
			}
			Logger.println("WebhookPush -> " + url + " status=" + code + " resp=" + resp.toString().trim());
		} catch (Exception e) {
			Logger.println("WebhookPush 发送失败: " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

	private static String setting(String key, String def) {
		try {
			String v = Global.settings == null ? null : Global.settings.get(key);
			if (v == null || v.isEmpty()) return def;
			return v;
		} catch (Exception e) { return def; }
	}

	private static String safe(String s) { return s == null ? "" : s; }

	/** 仅用于占位符注入到 JSON 字符串字段中，避免破坏外层 JSON 结构。 */
	private static String jsonEscape(String s) {
		if (s == null) return "";
		StringBuilder sb = new StringBuilder(s.length() + 16);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"': sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
			}
		}
		return sb.toString();
	}
}
