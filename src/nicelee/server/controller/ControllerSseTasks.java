package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

import nicelee.bilibili.annotations.Controller;
import nicelee.server.core.SocketDealer;
import nicelee.server.util.ResponseUtil;
import nicelee.service.DownloadService;
import nicelee.service.TaskSnapshot;

/**
 * 下载任务进度 SSE 推送。
 * 每个连接占用一个 http 线程：每秒轮询一次 DownloadService.list() 写入 SSE 帧。
 * 注意：连接生命周期由本控制器自己管理（设置 HIJACKED=true 跳过 SocketDealer 的关闭）。
 */
@Controller(path = "/sse/tasks", note = "任务进度 SSE")
public class ControllerSseTasks {

	private static final long INTERVAL_MS = 1500L;
	private static final long HEARTBEAT_MS = 15000L;

	@Controller(path = "", matchAll = true, note = "GET /sse/tasks")
	public String stream(BufferedWriter out) throws IOException {
		SocketDealer.HIJACKED.set(Boolean.TRUE);
		try {
			ResponseUtil.writeSseHeader(out);
			out.write("retry: 5000\n\n");
			out.flush();

			long lastHeartbeat = System.currentTimeMillis();
			while (true) {
				List<TaskSnapshot> tasks = DownloadService.list();
				StringBuilder sb = new StringBuilder();
				sb.append('[');
				boolean first = true;
				for (TaskSnapshot t : tasks) {
					if (!first) sb.append(',');
					first = false;
					sb.append('{')
						.append("\"id\":\"").append(esc(t.id)).append("\",")
						.append("\"status\":\"").append(esc(t.status)).append("\",")
						.append("\"currentDown\":").append(t.currentDown).append(',')
						.append("\"totalSize\":").append(t.totalSize).append(',')
						.append("\"speed\":").append(t.speed).append(',')
						.append("\"fileName\":\"").append(esc(t.fileName)).append("\",")
						.append("\"absPath\":\"").append(esc(t.absPath)).append("\",")
						.append("\"relPath\":\"").append(esc(t.relPath)).append("\",")
						.append("\"avId\":\"").append(esc(t.avId)).append("\",")
						.append("\"lastError\":\"").append(esc(t.lastError)).append("\",")
						.append("\"title\":\"").append(esc(t.title)).append("\"")
						.append('}');
				}
				sb.append(']');
				out.write("event: tasks\ndata: " + sb.toString() + "\n\n");
				out.flush();

				long now = System.currentTimeMillis();
				if (now - lastHeartbeat > HEARTBEAT_MS) {
					out.write(": ping\n\n");
					out.flush();
					lastHeartbeat = now;
				}
				try { Thread.sleep(INTERVAL_MS); } catch (InterruptedException ignored) { break; }
			}
		} catch (IOException ioe) {
			// 客户端断开
		} finally {
			try { out.close(); } catch (Exception ignored) {}
		}
		return null;
	}

	private static String esc(String s) {
		if (s == null) return "";
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
}
