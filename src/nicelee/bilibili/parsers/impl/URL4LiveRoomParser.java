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
 * #1 直播录制 - 解析 https://live.bilibili.com/{roomId}
 * 取得当前直播 FLV 流地址，构造单个 ClipInfo，
 * 然后由 FLVDownloader 持续写入直到流断开。
 */
@Bilibili(name = "URL4LiveRoomParser", note = "直播间解析（直播录制）")
public class URL4LiveRoomParser extends AbstractBaseParser {

	// 同时匹配 URL（live.bilibili.com/{roomId}）与 validId 形式（live{roomId}）
	private final static Pattern pattern = Pattern.compile("live\\.bilibili\\.com/(?:h5/|blanc/)?(\\d+)|^live(\\d+)$");
	private String roomId;

	public URL4LiveRoomParser(Object... obj) {
		super(obj);
	}

	@Override
	public boolean matches(String input) {
		matcher = pattern.matcher(input);
		boolean matches = matcher.find();
		if (matches) {
			roomId = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
			Logger.println("匹配 URL4LiveRoomParser, roomId = " + roomId);
		}
		return matches;
	}

	@Override
	public String validStr(String input) {
		return "live" + roomId;
	}

	@Override
	public VideoInfo result(String input, int videoFormat, boolean getVideoLink) {
		Logger.println("URL4LiveRoomParser 正在获取结果: live" + roomId);
		String liveIdStr = "live" + roomId;
		VideoInfo viInfo = new VideoInfo();
		viInfo.setVideoId(liveIdStr);

		HashMap<String, String> headers = new HttpHeaders().getCommonHeaders("https://live.bilibili.com");

		// 1. 房间基本信息（标题/主播/封面/状态）
		String infoUrl = "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=" + roomId;
		String infoJson = util.getContent(infoUrl, headers, HttpCookies.globalCookiesWithFingerprint());
		Logger.println(infoJson);
		String title = liveIdStr;
		String cover = null;
		long realRoomId = Long.parseLong(roomId);
		long uid = 0;
		int liveStatus = 0;
		long liveTime = 0;
		try {
			JSONObject d = new JSONObject(infoJson).getJSONObject("data");
			title = d.optString("title", liveIdStr);
			cover = d.optString("user_cover", null);
			realRoomId = d.optLong("room_id", realRoomId);
			uid = d.optLong("uid", 0);
			liveStatus = d.optInt("live_status", 0);
			liveTime = d.optLong("live_time", 0) * 1000L;
		} catch (Exception e) {
			Logger.println("获取直播间信息失败: " + e.getMessage());
		}
		viInfo.setVideoName(title);
		viInfo.setVideoPreview(cover);

		// 2. 主播昵称
		String upName = "live-" + uid;
		try {
			String userUrl = "https://api.live.bilibili.com/live_user/v1/Master/info?uid=" + uid;
			String userJson = util.getContent(userUrl, headers, HttpCookies.globalCookiesWithFingerprint());
			JSONObject info = new JSONObject(userJson).getJSONObject("data").getJSONObject("info");
			upName = info.optString("uname", upName);
		} catch (Exception ignored) {}
		viInfo.setAuthor(upName);
		viInfo.setAuthorId(String.valueOf(uid));

		// 3. 取播放地址（FLV）。qn=10000 原画；如未开播则用占位提示。
		String flvUrl = null;
		if (liveStatus == 1) {
			flvUrl = fetchFlvUrl(realRoomId, headers);
		}
		if (flvUrl == null) {
			Logger.println("URL4LiveRoomParser: 未取到 FLV 地址（可能未开播或被风控）");
		}

		ClipInfo clip = new ClipInfo();
		clip.setAvTitle(title);
		clip.setAvId(liveIdStr);
		clip.setcId(1);
		clip.setPage(1);
		clip.setRemark(1);
		clip.setTitle(title);
		clip.setPicPreview(cover);
		clip.setUpName(upName);
		clip.setUpId(String.valueOf(uid));
		clip.setcTime(liveTime > 0 ? liveTime : System.currentTimeMillis());
		HashMap<Integer, String> links = new LinkedHashMap<>();
		if (flvUrl != null) links.put(0, flvUrl);
		clip.setLinks(links);

		LinkedHashMap<Long, ClipInfo> clipMap = new LinkedHashMap<>();
		clipMap.put(clip.getcId(), clip);
		viInfo.setClips(clipMap);
		return viInfo;
	}

	/**
	 * 下载流程会调用 InputParser.getVideoLink（不依赖之前 result() 缓存的链接），
	 * 这里重新向直播 API 请求一份新鲜的 FLV 地址。qn 使用 10000（原画）。
	 */
	@Override
	public String getVideoLink(String bvId, String cid, int qn, int downFormat) {
		try {
			long realRoomId = Long.parseLong(roomId);
			HashMap<String, String> headers = new HttpHeaders().getCommonHeaders("https://live.bilibili.com");
			// 先试 get_info 拿到 real_room_id（可能与输入不同）
			try {
				String infoJson = util.getContent(
						"https://api.live.bilibili.com/room/v1/Room/get_info?room_id=" + roomId,
						headers, HttpCookies.globalCookiesWithFingerprint());
				realRoomId = new JSONObject(infoJson).getJSONObject("data").optLong("room_id", realRoomId);
			} catch (Exception ignored) {}
			String flv = fetchFlvUrl(realRoomId, headers);
			if (flv == null) {
				Logger.println("URL4LiveRoomParser.getVideoLink: 未能获取 FLV（可能未开播或被风控）");
				return null;
			}
			paramSetter.setRealQN(qn);
			return flv;
		} catch (Exception e) {
			Logger.println("URL4LiveRoomParser.getVideoLink 异常: " + e.getMessage());
			return null;
		}
	}

	/**
	 * 调用直播 playUrl 接口，返回 FLV 直链。失败返回 null。
	 */
	private String fetchFlvUrl(long realRoomId, HashMap<String, String> headers) {
		try {
			String url = "https://api.live.bilibili.com/room/v1/Room/playUrl?cid=" + realRoomId
					+ "&qn=10000&platform=web";
			String json = util.getContent(url, headers, HttpCookies.globalCookiesWithFingerprint());
			Logger.println(json);
			JSONObject data = new JSONObject(json).getJSONObject("data");
			JSONArray durl = data.optJSONArray("durl");
			if (durl != null && durl.length() > 0) {
				return durl.getJSONObject(0).getString("url");
			}
		} catch (Exception e) {
			Logger.println("playUrl 解析失败: " + e.getMessage());
		}
		// 备选：xlive 新接口
		try {
			String url2 = "https://api.live.bilibili.com/xlive/web-room/v2/index/getRoomPlayInfo?room_id="
					+ realRoomId + "&qn=10000&platform=web&protocol=0&format=0&codec=0";
			String json = util.getContent(url2, headers, HttpCookies.globalCookiesWithFingerprint());
			Logger.println(json);
			JSONObject pinfo = new JSONObject(json).getJSONObject("data").getJSONObject("playurl_info")
					.getJSONObject("playurl");
			JSONArray streams = pinfo.getJSONArray("stream");
			JSONObject fmt = streams.getJSONObject(0).getJSONArray("format").getJSONObject(0);
			JSONObject codec = fmt.getJSONArray("codec").getJSONObject(0);
			String base = codec.getString("base_url");
			JSONObject host = codec.getJSONArray("url_info").getJSONObject(0);
			return host.getString("host") + base + host.getString("extra");
		} catch (Exception e) {
			Logger.println("getRoomPlayInfo 解析失败: " + e.getMessage());
		}
		return null;
	}
}
