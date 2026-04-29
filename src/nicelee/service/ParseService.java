package nicelee.service;

import nicelee.bilibili.INeedAV;
import nicelee.bilibili.model.VideoInfo;
import nicelee.ui.Global;

/**
 * 解析输入（URL/av/BV）。当前阶段只支持单个视频/合集/收藏夹/番剧/搜索的「第一次解析」，
 * 与桌面端 TabIndex 的行为对齐。具体复杂解析直接复用 INeedAV。
 */
public class ParseService {

	/** 提取规范 avId（同桌面端 TabIndex.search） */
	public static String validId(String input) {
		INeedAV av = new INeedAV();
		return av.getValidID(input);
	}

	/**
	 * 获取视频详情。
	 * @param avId 已经过 validId 过滤的 ID
	 */
	public static VideoInfo getDetail(String avId) {
		INeedAV av = new INeedAV();
		return av.getVideoDetail(avId, Global.downloadFormat, false);
	}
}
