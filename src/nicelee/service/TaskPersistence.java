package nicelee.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.util.ResourcesUtil;
import nicelee.ui.Global;
import nicelee.ui.item.DownloadInfoPanel;

/**
 * 活动任务持久化：把当前下载列表里非"已完成/已成功"的任务存到 config/active-tasks.json，
 * 启动时重新通过 DownloadService.submit 入队，达成"重启恢复"。
 *
 * 真正的 "断点续传" 由各 Downloader 自行根据已下载文件大小处理（B 站官方 CDN 支持 Range）。
 */
public class TaskPersistence {

	private static final String REL_PATH = "./config/active-tasks.json";
	private static volatile boolean restoring = false;
	private static volatile long lastSaveTs = 0;
	private static final Object SAVE_LOCK = new Object();

	/** 触发持久化（频繁调用安全：1s 内只写一次） */
	public static void scheduleSave() {
		if (restoring) return;
		long now = System.currentTimeMillis();
		if (now - lastSaveTs < 1000L) return;
		lastSaveTs = now;
		try {
			save();
		} catch (Throwable t) {
			System.err.println("[TaskPersistence] save failed: " + t);
		}
	}

	public static synchronized void save() {
		synchronized (SAVE_LOCK) {
			JSONArray arr = new JSONArray();
			try {
				if (Global.downloadTaskList == null) return;
				for (DownloadInfoPanel p : Global.downloadTaskList.keySet()) {
					try {
						nicelee.bilibili.downloaders.IDownloader d = Global.downloadTaskList.get(p);
						// 跳过已成功完成的
						if (d != null && d.currentStatus() == nicelee.bilibili.enums.StatusEnum.SUCCESS) continue;
						JSONObject o = new JSONObject();
						o.put("avId", p.getAvid());
						o.put("cid", p.getCid());
						o.put("qn", p.getQn());
						o.put("page", p.getClipInfo() != null ? p.getClipInfo().getPage() : 1);
						o.put("priority", p.priority);
						o.put("title", p.getClipInfo() != null ? p.getClipInfo().getAvTitle() : "");
						arr.put(o);
					} catch (Exception ignored) {}
				}
				File f = ResourcesUtil.sourceOf(REL_PATH);
				if (f.getParentFile() != null) f.getParentFile().mkdirs();
				try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
					w.write(arr.toString());
				}
			} catch (Exception e) {
				System.err.println("[TaskPersistence] write failed: " + e);
			}
		}
	}

	/** 启动时调用：读取并重新提交。失败不抛出。 */
	public static void restore() {
		try {
			File f = ResourcesUtil.sourceOf(REL_PATH);
			if (!f.exists()) return;
			StringBuilder sb = new StringBuilder();
			try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
				String line;
				while ((line = br.readLine()) != null) sb.append(line);
			}
			if (sb.length() == 0) return;
			JSONArray arr = new JSONArray(sb.toString());
			List<Runnable> jobs = new ArrayList<>();
			restoring = true;
			for (int i = 0; i < arr.length(); i++) {
				try {
					JSONObject o = arr.getJSONObject(i);
					final String avId = o.getString("avId");
					final long cid = o.optLong("cid", -1);
					final int qn = o.optInt("qn", 80);
					final int prio = o.optInt("priority", 0);
					jobs.add(() -> {
						try {
							VideoInfo info = ParseService.getDetail(avId);
							if (info == null || info.getClips() == null) return;
							ClipInfo target = info.getClips().get(cid);
							if (target == null) return;
							DownloadService.submit(info, target, qn);
							if (prio != 0) {
								// 等待 panel 出现后再设置优先级
								String taskId = avId + "-" + qn + "-p" + target.getPage();
								for (int t = 0; t < 30; t++) {
									if (DownloadService.setPriority(taskId, prio)) break;
									try { Thread.sleep(200); } catch (InterruptedException ie) { break; }
								}
							}
						} catch (Throwable t) {
							System.err.println("[TaskPersistence] restore one failed: " + t);
						}
					});
				} catch (Exception ignored) {}
			}
			// 串行恢复以避免一次拉太多 detail
			Thread th = new Thread(() -> {
				try {
					for (Runnable r : jobs) {
						try { r.run(); } catch (Throwable ignored) {}
						try { Thread.sleep(300); } catch (InterruptedException ie) { return; }
					}
				} finally {
					restoring = false;
				}
			}, "TaskPersistence-Restore");
			th.setDaemon(true);
			th.start();
		} catch (Exception e) {
			restoring = false;
			System.err.println("[TaskPersistence] restore failed: " + e);
		}
	}
}
