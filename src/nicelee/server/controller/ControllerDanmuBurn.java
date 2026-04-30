package nicelee.server.controller;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;
import nicelee.ui.Global;

/**
 * 把弹幕 ASS 通过 ffmpeg subtitles 滤镜烧录到视频里。
 * POST /api/danmu/burn  body: {"video":"<abs path>", "ass":"<abs path>", "output":"<abs path optional>"}
 * 返回：{"output": "...", "exit": 0}
 */
@Controller(path = "/api/danmu", note = "弹幕处理")
public class ControllerDanmuBurn {

	@Controller(path = "/burn", matchAll = true, note = "POST /api/danmu/burn")
	public String burn(BufferedWriter out, @Value(key = "postData") String body) throws IOException {
		String video = jsonStr(body, "video");
		String ass = jsonStr(body, "ass");
		String output = jsonStr(body, "output");
		if (video == null || ass == null) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "missing video/ass"));
			return null;
		}
		File vf = new File(video);
		File af = new File(ass);
		if (!vf.exists() || !af.exists()) {
			ResponseUtil.writeJsonStatus(out, 404, JsonUtil.err(404, "file not found"));
			return null;
		}
		if (output == null || output.isEmpty()) {
			String n = vf.getName();
			int dot = n.lastIndexOf('.');
			String stem = dot > 0 ? n.substring(0, dot) : n;
			String ext = dot > 0 ? n.substring(dot) : ".mp4";
			output = new File(vf.getParentFile(), stem + ".burnt" + ext).getAbsolutePath();
		}
		// ffmpeg 的 subtitles 滤镜要求路径用 '/' 且在 Windows 上需要转义冒号
		String assForFilter = af.getAbsolutePath().replace('\\', '/').replace(":", "\\:");
		String[] cmd = new String[] {
			Global.ffmpegPath, "-y", "-i", vf.getAbsolutePath(),
			"-vf", "subtitles='" + assForFilter + "'",
			"-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
			"-c:a", "copy",
			output
		};
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
			Process proc = pb.start();
			StringBuilder log = new StringBuilder();
			try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				int lineCnt = 0;
				while ((line = br.readLine()) != null) {
					if (lineCnt++ < 200) log.append(line).append('\n');
				}
			}
			int code = proc.waitFor();
			StringBuilder sb = new StringBuilder();
			sb.append('{')
				.append(JsonUtil.kv("output", output)).append(',')
				.append(JsonUtil.kvN("exit", code)).append(',')
				.append(JsonUtil.kv("log", log.toString().length() > 2000 ? log.substring(0, 2000) : log.toString()))
				.append('}');
			if (code != 0) ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, "ffmpeg exit " + code + ": " + log));
			else ResponseUtil.writeJson(out, JsonUtil.okData(sb.toString()));
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 500, JsonUtil.err(500, e.getClass().getSimpleName() + ": " + e.getMessage()));
		}
		return null;
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
		if (j >= body.length()) return null;
		if (body.charAt(j) == '"') {
			int q2 = body.indexOf('"', j + 1);
			return q2 < 0 ? null : body.substring(j + 1, q2);
		}
		return null;
	}
}
