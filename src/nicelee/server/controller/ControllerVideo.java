package nicelee.server.controller;

import java.io.BufferedWriter;
import java.io.IOException;

import nicelee.bilibili.annotations.Controller;
import nicelee.bilibili.annotations.Value;
import nicelee.bilibili.enums.VideoQualityEnum;
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
}
