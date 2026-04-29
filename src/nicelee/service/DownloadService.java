package nicelee.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nicelee.bilibili.INeedAV;
import nicelee.bilibili.downloaders.IDownloader;
import nicelee.bilibili.enums.StatusEnum;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.ui.Global;
import nicelee.ui.item.DownloadInfoPanel;
import nicelee.ui.thread.DownloadRunnable;

/**
 * 下载任务的 Web 门面：直接读写 Global.downloadTaskList，
 * 并通过 DownloadRunnable 复用桌面端的下载入队流程，
 * 因此提交后桌面端 TabDownload 也会自动出现对应任务。
 */
public class DownloadService {

	/** 列出所有任务快照 */
	public static List<TaskSnapshot> list() {
		List<TaskSnapshot> result = new ArrayList<>();
		for (Map.Entry<DownloadInfoPanel, IDownloader> entry : Global.downloadTaskList.entrySet()) {
			TaskSnapshot s = toSnapshot(entry.getKey(), entry.getValue());
			recordHistoryIfDone(s);
			result.add(s);
		}
		return result;
	}

	/** 任务一旦标记为 done 就尝试写入历史记录（HistoryStore 内部按 key 去重）。 */
	private static void recordHistoryIfDone(TaskSnapshot s) {
		try {
			if (s == null || s.id == null) return;
			if (!"done".equals(s.status) && !"fail".equals(s.status)) return;
			HistoryStore.Entry e = new HistoryStore.Entry();
			e.key = s.id;
			e.type = "download";
			e.title = s.title;
			e.avId = s.avId;
			e.page = s.page;
			e.qn = s.qn;
			e.absPath = s.absPath;
			e.relPath = s.relPath;
			e.size = s.totalSize > 0 ? s.totalSize : s.currentDown;
			e.status = s.status;
			e.ts = System.currentTimeMillis();
			HistoryStore.DOWNLOAD.add(e);
		} catch (Exception ignored) {}
	}

	private static TaskSnapshot toSnapshot(DownloadInfoPanel p, IDownloader downloader) {
		TaskSnapshot s = new TaskSnapshot();
		s.id = p.getAvid() + "-" + p.getQn() + "-p" + p.getClipInfo().getPage();
		s.avId = p.getAvid();
		s.page = p.getClipInfo().getPage();
		s.qn = p.getQn();
		s.realQn = p.getRealqn();
		s.title = p.formattedTitle != null ? p.formattedTitle : p.getClipInfo().getAvTitle();
		try {
			s.fileName = p.getLbFileName() != null ? p.getLbFileName().getText() : null;
		} catch (Exception ignored) {}
		// 进度
		long now = System.currentTimeMillis();
		long dt = now - p.getLastCntTime();
		long dc = -1;
		try {
			java.lang.reflect.Field f1 = DownloadInfoPanel.class.getDeclaredField("currentDown");
			java.lang.reflect.Field f2 = DownloadInfoPanel.class.getDeclaredField("totalSize");
			f1.setAccessible(true); f2.setAccessible(true);
			s.currentDown = f1.getLong(p);
			s.totalSize = f2.getLong(p);
			dc = s.currentDown - p.getLastCnt();
		} catch (Exception ignored) {}
		if (dt > 0 && dc >= 0) {
			s.speed = dc * 1000L / dt;
		}
		// 解析绝对/相对路径，并在文件实际存在时用磁盘大小回填进度（修复 dedup/已存在导致 0% 的显示）
		if (s.fileName != null && !s.fileName.isEmpty() && !"尚未生成".equals(s.fileName)) {
			try {
				java.io.File f = new java.io.File(s.fileName);
				if (f.isAbsolute()) {
					s.absPath = f.getAbsolutePath();
				} else {
					s.absPath = new java.io.File(Global.savePath, s.fileName).getAbsolutePath();
				}
				java.io.File base = new java.io.File(Global.savePath).getCanonicalFile();
				java.io.File abs = new java.io.File(s.absPath).getCanonicalFile();
				String basePath = base.getPath();
				String absStr = abs.getPath();
				if (absStr.equals(basePath)) {
					s.relPath = "";
				} else if (absStr.startsWith(basePath + java.io.File.separator)) {
					s.relPath = absStr.substring(basePath.length() + 1).replace(java.io.File.separatorChar, '/');
				}
				if (abs.exists() && abs.isFile()) {
					long len = abs.length();
					if (s.totalSize <= 0) s.totalSize = len;
					if (s.currentDown <= 0) s.currentDown = len;
				}
			} catch (Exception ignored) {}
		}
		// 状态
		StatusEnum st = downloader == null ? StatusEnum.NONE : downloader.currentStatus();
		if (st == null) st = StatusEnum.NONE;
		switch (st) {
			case DOWNLOADING: s.status = p.stopOnQueue ? "paused" : "active"; break;
			case PROCESSING: s.status = "processing"; break;
			case STOP: s.status = "paused"; break;
			case FAIL: s.status = "fail"; break;
			case SUCCESS: s.status = "done"; break;
			default: s.status = "queued";
		}
		return s;
	}

	private static DownloadInfoPanel find(String id) {
		for (DownloadInfoPanel p : Global.downloadTaskList.keySet()) {
			String pid = p.getAvid() + "-" + p.getQn() + "-p" + p.getClipInfo().getPage();
			if (pid.equals(id)) return p;
		}
		return null;
	}

	public static boolean pause(String id) {
		DownloadInfoPanel p = find(id);
		if (p == null) return false;
		try { p.stopTask(); } catch (Exception ignored) {}
		return true;
	}

	public static boolean resume(String id) {
		DownloadInfoPanel p = find(id);
		if (p == null) return false;
		try { p.setFailCnt(0); p.continueTask(); } catch (Exception ignored) {}
		return true;
	}

	public static boolean remove(String id) {
		DownloadInfoPanel p = find(id);
		if (p == null) return false;
		try { p.removeTask(true); } catch (Exception ignored) {}
		return true;
	}

	public static int pauseAll() {
		int n = 0;
		for (DownloadInfoPanel p : Global.downloadTaskList.keySet()) {
			try { p.stopTask(); n++; } catch (Exception ignored) {}
		}
		return n;
	}

	public static int resumeAll() {
		int n = 0;
		for (DownloadInfoPanel p : Global.downloadTaskList.keySet()) {
			try { p.setFailCnt(0); p.continueTask(); n++; } catch (Exception ignored) {}
		}
		return n;
	}

	public static int removeDone() {
		int n = 0;
		for (DownloadInfoPanel p : new ArrayList<>(Global.downloadTaskList.keySet())) {
			try {
				IDownloader d = Global.downloadTaskList.get(p);
				if (d != null && d.currentStatus() == StatusEnum.SUCCESS) {
					p.removeTask(true);
					n++;
				}
			} catch (Exception ignored) {}
		}
		return n;
	}

	/**
	 * 提交一个新下载。复用桌面端 DownloadRunnable，由其内部 new DownloadInfoPanel() 并加入任务列表。
	 * 注意：DownloadRunnable.run() 会触发 UI 更新，因此当前实现要求 Global.downloadTab != null。
	 * Web 端显式提交：通过 ThreadLocal 跳过 repo 已下载弹窗，避免阻塞 + 让任务真正进入列表。
	 */
	public static void submit(VideoInfo avInfo, ClipInfo clip, int qn) {
		DownloadRunnable downThread = new DownloadRunnable(avInfo, clip, qn) {
			@Override
			public void run() {
				DownloadRunnable.SKIP_REPO_CHECK.set(Boolean.TRUE);
				try { super.run(); } finally { DownloadRunnable.SKIP_REPO_CHECK.remove(); }
			}
		};
		Global.queryThreadPool.execute(downThread);
	}
}
