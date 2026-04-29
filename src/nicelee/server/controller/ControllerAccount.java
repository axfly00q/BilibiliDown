package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import nicelee.bilibili.INeedLogin;
import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.bilibili.util.HttpCookies;
import nicelee.bilibili.util.QrCodeUtil;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.ui.Global;
import nicelee.ui.thread.CookieRefreshThread;

/**
 * Web 控制台 - B 站账号 (扫码登录、登录态、Cookie 刷新)。
 *  /api/account/status            GET   当前登录态
 *  /api/account/qr/start          POST  申请二维码 (返回 key + qrUrl + qrPng base64)
 *  /api/account/qr/poll?key=xxx   GET   轮询扫码状态
 *  /api/account/logout            POST  清空本地 cookie 文件 + 内存 cookie
 *  /api/account/cookie/refresh    POST  调用桌面端 CookieRefreshThread 刷新
 */
@Controller(path = "/api/account", note = "Web 控制台 - B 站账号桥接")
public class ControllerAccount {

	/** key -> 关联的 INeedLogin 实例（保留登录上下文，含 Cookie/refreshToken） */
	private static final ConcurrentHashMap<String, INeedLogin> SESSIONS = new ConcurrentHashMap<>();

	@Controller(path = "/status", matchAll = true, note = "当前 B 站账号登录态")
	public String status(BufferedWriter out) throws IOException {
		boolean login = Global.isLogin && HttpCookies.getGlobalCookies() != null;
		StringBuilder data = new StringBuilder();
		data.append('{').append(JsonUtil.kvB("login", login));
		if (login) {
			INeedLogin tmp = new INeedLogin();
			tmp.getLoginStatus(HttpCookies.getGlobalCookies());
			if (tmp.user != null) {
				data.append(',').append(JsonUtil.kv("uname", tmp.user.getName()))
						.append(',').append(JsonUtil.kv("face", tmp.user.getPoster()))
						.append(',').append(JsonUtil.kv("uid", String.valueOf(tmp.user.getUid())));
			}
		}
		data.append('}');
		ResponseUtil.writeJson(out, JsonUtil.okData(data.toString()));
		return null;
	}

	@Controller(path = "/qr/start", matchAll = true, note = "申请扫码登录二维码")
	public String qrStart(BufferedWriter out, OutputStream outRaw) throws IOException {
		try {
			INeedLogin inl = new INeedLogin();
			String authKey = inl.getAuthKey();
			SESSIONS.put(authKey, inl);
			// 旧 session 清理：超过 20 个时简单截断
			if (SESSIONS.size() > 20) {
				for (String k : SESSIONS.keySet()) {
					SESSIONS.remove(k);
					if (SESSIONS.size() <= 10) break;
				}
				SESSIONS.put(authKey, inl);
			}
			String qrUrl = inl.qrCodeStr;
			String pngB64 = renderQrPng(qrUrl);
			StringBuilder data = new StringBuilder();
			data.append('{')
					.append(JsonUtil.kv("key", authKey)).append(',')
					.append(JsonUtil.kv("qrUrl", qrUrl)).append(',')
					.append(JsonUtil.kv("qrPng", pngB64))
					.append('}');
			ResponseUtil.writeJson(out, JsonUtil.okData(data.toString()));
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, e.getMessage()));
		}
		return null;
	}

	@Controller(path = "/qr/poll", matchAll = true, note = "轮询扫码登录状态")
	public String qrPoll(BufferedWriter out, @Value(key = "key") String key) throws IOException {
		if (key == null || key.isEmpty()) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "missing key"));
			return null;
		}
		INeedLogin inl = SESSIONS.get(key);
		if (inl == null) {
			ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "qr session not found"));
			return null;
		}
		try {
			boolean ok = inl.getAuthStatus(key);
			if (ok && inl.iCookies != null) {
				// 落盘 + 内存
				inl.saveCookiesAndToken();
				HttpCookies.setGlobalCookies(inl.iCookies);
				Global.isLogin = true;
				Global.needToLogin = false;
				inl.getLoginStatus(inl.iCookies);
				SESSIONS.remove(key);
				StringBuilder data = new StringBuilder();
				data.append('{').append(JsonUtil.kv("status", "ok"));
				if (inl.user != null) {
					data.append(',').append(JsonUtil.kv("uname", inl.user.getName()))
							.append(',').append(JsonUtil.kv("face", inl.user.getPoster()))
							.append(',').append(JsonUtil.kv("uid", String.valueOf(inl.user.getUid())));
				}
				data.append('}');
				ResponseUtil.writeJson(out, JsonUtil.okData(data.toString()));
			} else {
				ResponseUtil.writeJson(out, JsonUtil.okData("{" + JsonUtil.kv("status", "waiting") + "}"));
			}
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, e.getMessage()));
		}
		return null;
	}

	@Controller(path = "/logout", matchAll = true, note = "清空 B 站登录 Cookie")
	public String logoutBili(BufferedWriter out) throws IOException {
		try {
			List<HttpCookie> empty = new java.util.ArrayList<>();
			HttpCookies.setGlobalCookies(empty);
			Global.isLogin = false;
			Global.needToLogin = true;
			java.io.File file = nicelee.bilibili.util.ResourcesUtil.sourceOf("./config/cookies.config");
			if (file.exists()) file.delete();
			ResponseUtil.writeJson(out, JsonUtil.ok());
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, e.getMessage()));
		}
		return null;
	}

	@Controller(path = "/cookie/refresh", matchAll = true, note = "触发 Cookie 刷新")
	public String cookieRefresh(BufferedWriter out) throws IOException {
		try {
			if (HttpCookies.getGlobalCookies() == null) {
				ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "未登录, 无法刷新"));
				return null;
			}
			CookieRefreshThread.showTips = false;
			CookieRefreshThread th = CookieRefreshThread.newInstance();
			th.start();
			ResponseUtil.writeJson(out, JsonUtil.okData("{" + JsonUtil.kv("status", "started") + "}"));
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, e.getMessage()));
		}
		return null;
	}

	private static String renderQrPng(String content) {
		try {
			// 不走 QrCodeUtil.createQrCode，它会裁掉静息区（quiet zone）导致手机扫不出。
			// 这里直接用 zxing 的位矩阵 + 保留 margin=2。
			java.util.Hashtable<com.google.zxing.EncodeHintType, Object> hints = new java.util.Hashtable<>();
			hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION,
					com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
			hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET,
					com.google.zxing.common.CharacterSetECI.UTF8);
			hints.put(com.google.zxing.EncodeHintType.MARGIN, 2);
			com.google.zxing.qrcode.QRCodeWriter writer = new com.google.zxing.qrcode.QRCodeWriter();
			com.google.zxing.common.BitMatrix matrix = writer.encode(
					content, com.google.zxing.BarcodeFormat.QR_CODE, 480, 480, hints);
			int w = matrix.getWidth();
			int h = matrix.getHeight();
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h,
					java.awt.image.BufferedImage.TYPE_INT_RGB);
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					img.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
				}
			}
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ImageIO.write(img, "png", bos);
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
		} catch (Exception e) {
			return "";
		}
	}
}
