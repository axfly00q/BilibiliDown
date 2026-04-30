package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;

import nicelee.bilibili.INeedAV;
import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.bilibili.enums.VideoQualityEnum;
import nicelee.bilibili.parsers.IInputParser;
import nicelee.bilibili.parsers.impl.AbstractBaseParser;
import nicelee.server.util.JsonUtil;
import nicelee.server.util.ResponseUtil;

@Controller(path = "/api/quality", note = "可选清晰度枚举")
public class ControllerVideo {

	@Controller(path = "/list", matchAll = true, note = "返回内置清晰度枚举")
	public String list(BufferedWriter out, @Value(key = "paramData") String ignored) throws IOException {
		StringBuilder sb = new StringBuilder("[");
		boolean first = true;
		for (VideoQualityEnum q : VideoQualityEnum.values()) {
			if (!first) sb.append(',');
			first = false;
			sb.append('{')
				.append(JsonUtil.kvN("qn", q.getQn())).append(',')
				.append(JsonUtil.kv("quality", q.getQuality()))
				.append('}');
		}
		sb.append(']');
		ResponseUtil.writeJson(out, JsonUtil.okData(sb.toString()));
		return null;
	}

	/**
	 * 返回当前账号 / Cookie / 视频实际可用的清晰度列表（来自 B 站 playurl 接口的 accept_quality）。
	 *  GET /api/quality/avail?avId=BV1xxxxxxx&cid=12345
	 *  返回 { qns:[127,120,...], qualities:[{qn,quality}, ...] }
	 *  - qns 仅包含视频清晰度（不含弹幕/字幕/音频虚拟 qn），按 B 站返回顺序（一般高→低）。
	 *  - qualities 是与内置枚举求交集后的标签列表。
	 *  非视频（如 cv 专栏 / opus 图文 / 直播 / 音频）或调用失败时返回 503，前端应回退到内置全量。
	 */
	@Controller(path = "/avail", matchAll = true, note = "查询账号实际可下载的清晰度")
	public String avail(BufferedWriter out,
			@Value(key = "avId") String avId,
			@Value(key = "cid") String cid) throws IOException {
		if (avId == null || avId.isEmpty() || cid == null || cid.isEmpty()) {
			ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "missing avId or cid"));
			return null;
		}
		try {
			INeedAV ina = new INeedAV();
			String validId = ina.getValidID(avId);
			if (validId == null || validId.isEmpty()) {
				ResponseUtil.writeJsonStatus(out, 400, JsonUtil.err(400, "无法解析 avId: " + avId));
				return null;
			}
			IInputParser p = ina.getInputParser(validId).selectParser(validId);
			if (!(p instanceof AbstractBaseParser)) {
				ResponseUtil.writeJsonStatus(out, 503, JsonUtil.err(503, "该类型不支持精确查询，请使用内置枚举"));
				return null;
			}
			int[] qns = ((AbstractBaseParser) p).getVideoQNList(validId, cid);
			if (qns == null || qns.length == 0) {
				ResponseUtil.writeJsonStatus(out, 503, JsonUtil.err(503, "未返回 accept_quality"));
				return null;
			}
			StringBuilder qarr = new StringBuilder("[");
			for (int i = 0; i < qns.length; i++) { if (i > 0) qarr.append(','); qarr.append(qns[i]); }
			qarr.append(']');
			StringBuilder labels = new StringBuilder("[");
			boolean first = true;
			for (int qn : qns) {
				for (VideoQualityEnum q : VideoQualityEnum.values()) {
					if (q.getQn() == qn) {
						if (!first) labels.append(',');
						first = false;
						labels.append('{')
							.append(JsonUtil.kvN("qn", q.getQn())).append(',')
							.append(JsonUtil.kv("quality", q.getQuality()))
							.append('}');
						break;
					}
				}
			}
			labels.append(']');
			StringBuilder sb = new StringBuilder();
			sb.append('{')
				.append("\"qns\":").append(qarr).append(',')
				.append("\"qualities\":").append(labels)
				.append('}');
			ResponseUtil.writeJson(out, JsonUtil.okData(sb.toString()));
		} catch (Exception e) {
			ResponseUtil.writeJsonStatus(out, 503, JsonUtil.err(503, e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage())));
		}
		return null;
	}
}
