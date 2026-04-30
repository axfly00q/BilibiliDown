package nicelee.bilibili.parsers.impl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import nicelee.bilibili.annotations.Bilibili;
import nicelee.bilibili.model.ClipInfo;
import nicelee.bilibili.model.VideoInfo;
import nicelee.bilibili.util.HttpCookies;
import nicelee.bilibili.util.HttpHeaders;
import nicelee.bilibili.util.Logger;

/**
 * #2 直播回放下载 - 解析 https://live.bilibili.com/record/{rid}
 * 每个 segment 作为独立 ClipInfo，由 FLVDownloader 下载。
 */
@Bilibili(name = "URL4LiveRecordParser", note = "直播回放解析（多段录像）")
public class URL4LiveRecordParser extends AbstractBaseParser {

	// 同时匹配 URL（live.bilibili.com/record/{rid}）与 validId 形式（rec{rid}）
	private final static Pattern pattern = Pattern.compile("live\\.bilibili\\.com/record/([A-Za-z0-9]+)|^rec([A-Za-z0-9]+)$");
	private String rid;

	public URL4LiveRecordParser(Object... obj) {
		super(obj);
	}

	@Override
	public boolean matches(String input) {
		matcher = pattern.matcher(input);
		boolean m = matcher.find();
		if (m) {
			rid = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
			Logger.println("匹配 URL4LiveRecordParser, rid = " + rid);
		}
		return m;
	}

	@Override
	public String validStr(String input) {
		return "rec" + rid;
	}

	@Override
	public VideoInfo result(String input, int videoFormat, boolean getVideoLink) {
		Logger.println("URL4LiveRecordParser 正在获取结果: rec" + rid);
		String idStr = "rec" + rid;
		VideoInfo viInfo = new VideoInfo();
		viInfo.setVideoId(idStr);
		HashMap<String, String> headers = new HttpHeaders().getCommonHeaders("https://live.bilibili.com");

		// 1. 录像基本信息
		String infoUrl = "https://api.live.bilibili.com/xlive/web-room/v1/record/getInfoByLiveRecord?rid=" + rid;
		String infoJson = util.getContent(infoUrl, headers, HttpCookies.globalCookiesWithFingerprint());
		Logger.println(infoJson);
		String title = idStr;
		String cover = null;
		String upName = "";
		long uid = 0;
		long startTs = System.currentTimeMillis();
		try {
			JSONObject d = new JSONObject(infoJson).getJSONObject("data");
			JSONObject live = d.optJSONObject("live_record_info");
			if (live != null) {
				title = live.optString("title", idStr);
				cover = live.optString("cover", null);
				startTs = live.optLong("start_timestamp", 0) * 1000L;
				if (startTs == 0) startTs = System.currentTimeMillis();
			}
			JSONObject anchor = d.optJSONObject("anchor_info");
			if (anchor != null) {
				upName = anchor.optString("uname", upName);
				uid = anchor.optLong("uid", 0);
			}
		} catch (Exception e) {
			Logger.println("getInfoByLiveRecord 解析失败: " + e.getMessage());
		}
		viInfo.setVideoName(title);
		viInfo.setVideoPreview(cover);
		viInfo.setAuthor(upName);
		viInfo.setAuthorId(String.valueOf(uid));

		// 2. 取分段列表
		LinkedHashMap<Long, ClipInfo> clipMap = new LinkedHashMap<>();
		try {
			String urlListUrl = "https://api.live.bilibili.com/xlive/web-room/v1/record/getLiveRecordUrl?rid=" + rid
					+ "&platform=html5";
			String json = util.getContent(urlListUrl, headers, HttpCookies.globalCookiesWithFingerprint());
			Logger.println(json);
			JSONObject data = new JSONObject(json).getJSONObject("data");
			JSONArray list = data.optJSONArray("list");
			if (list == null) list = new JSONArray();
			for (int i = 0; i < list.length(); i++) {
				JSONObject seg = list.getJSONObject(i);
				String url = seg.optString("url");
				if (url == null || url.isEmpty()) continue;
				ClipInfo clip = new ClipInfo();
				clip.setAvTitle(title);
				clip.setAvId(idStr);
				clip.setcId(i + 1);
				clip.setPage(i + 1);
				clip.setRemark(i + 1);
				clip.setTitle("第" + (i + 1) + "段");
				clip.setPicPreview(cover);
				clip.setUpName(upName);
				clip.setUpId(String.valueOf(uid));
				clip.setcTime(startTs);
				HashMap<Integer, String> links = new LinkedHashMap<>();
				links.put(0, url);
				clip.setLinks(links);
				clipMap.put(clip.getcId(), clip);
			}
		} catch (Exception e) {
			Logger.println("getLiveRecordUrl 解析失败: " + e.getMessage());
		}
		viInfo.setClips(clipMap);
		return viInfo;
	}

	/**
	 * 下载流程会调用该方法获取某一段的下载链接。重新调用 getLiveRecordUrl。
	 * cid 在 result() 中被设为段索引+1（1-based）。
	 */
	@Override
	public String getVideoLink(String bvId, String cid, int qn, int downFormat) {
		try {
			int segIdx = Integer.parseInt(cid);
			HashMap<String, String> headers = new HttpHeaders().getCommonHeaders("https://live.bilibili.com");
			String urlListUrl = "https://api.live.bilibili.com/xlive/web-room/v1/record/getLiveRecordUrl?rid=" + rid
					+ "&platform=html5";
			String json = util.getContent(urlListUrl, headers, HttpCookies.globalCookiesWithFingerprint());
			JSONArray list = new JSONObject(json).getJSONObject("data").optJSONArray("list");
			if (list == null || segIdx < 1 || segIdx > list.length()) {
				Logger.println("URL4LiveRecordParser.getVideoLink: 段索引越界 " + segIdx);
				return null;
			}
			String url = list.getJSONObject(segIdx - 1).optString("url");
			paramSetter.setRealQN(qn);
			return url == null || url.isEmpty() ? null : url;
		} catch (Exception e) {
			Logger.println("URL4LiveRecordParser.getVideoLink 异常: " + e.getMessage());
			return null;
		}
	}
}
