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
 * 下载专栏里面的图片
 * <p>https://www.bilibili.com/read/cv23435927/?from=readlist</p>
 * <p>https://www.bilibili.com/read/mobile?id=23435927</p>
 *
 */
@Bilibili(name = "URL4PictureCVParser", note = "图片解析 - 专栏")
public class URL4PictureCVParser extends AbstractBaseParser {

	private final static Pattern pattern = Pattern.compile("\\.bilibili\\.com/read/(mobile\\?id=|cv)([0-9]+)");
	private final static Pattern barePattern = Pattern.compile("^cv([0-9]+)$");
//	private final static Pattern picSrcPattern = Pattern.compile("img (data-)?src=\"([^\"]+)\"");
	private String cvIdNumber;

	public URL4PictureCVParser(Object... obj) {
		super(obj);
	}

	@Override
	public boolean matches(String input) {
		matcher = pattern.matcher(input);
		if (matcher.find()) {
			cvIdNumber = matcher.group(2);
			Logger.println("匹配URL4PictureCVParser: cv" + cvIdNumber);
			return true;
		}
		matcher = barePattern.matcher(input);
		if (matcher.find()) {
			cvIdNumber = matcher.group(1);
			Logger.println("匹配URL4PictureCVParser(裸 ID): cv" + cvIdNumber);
			return true;
		}
		return false;
	}

	@Override
	public String validStr(String input) {
		return input;
	}

	@Override
	public VideoInfo result(String input, int videoFormat, boolean getVideoLink) {
		return getCVDetail(cvIdNumber);
	}

	final static protected HashMap<String, String> headers = new HttpHeaders().getCommonHeaders("www.bilibili.com");
	protected VideoInfo getCVDetail(String cvIdNumber) {
		Logger.println("URL4PictureCVParser正在获取结果: cv" + cvIdNumber);
		String cvIdStr = "cv" + cvIdNumber;
		VideoInfo viInfo = new VideoInfo();
		viInfo.setVideoId(cvIdStr);

		String urlOpus = "https://www.bilibili.com/read/" + cvIdStr;
		String html = util.getContent(urlOpus, headers, HttpCookies.globalCookiesWithFingerprint());
		int begin = html.indexOf("window.__INITIAL_STATE__=");
		int end = html.indexOf(";(function()", begin);
		JSONObject jObj = null;
		JSONObject rootForOpus = null;
		if (begin >= 0 && end > begin) {
			String json = html.substring(begin + 25, end);
			Logger.println(json);
			JSONObject root = new JSONObject(json);
			// 兼容多种结构: 旧版 root.detail, 新版可能在 root.readInfo / root.cvInfo / root 自己就是 detail
			// 最新 isModern 版本: opus 嵌在 readInfo.opus / cvInfo.opus 里
			if (root.has("readInfo") && root.getJSONObject("readInfo").has("opus")) {
				rootForOpus = root.getJSONObject("readInfo");
			} else if (root.has("cvInfo") && root.getJSONObject("cvInfo").has("opus")) {
				rootForOpus = root.getJSONObject("cvInfo");
			} else if (root.has("opus")) {
				rootForOpus = root;
			} else if (root.has("detail")) {
				jObj = root.getJSONObject("detail");
			} else if (root.has("readInfo")) {
				jObj = root.getJSONObject("readInfo");
			} else if (root.has("cvInfo")) {
				jObj = root.getJSONObject("cvInfo");
			} else if (root.has("modules")) {
				jObj = root;
			}
		}
		// 最新版结构：root.opus.content.paragraphs
		if (rootForOpus != null) {
			Logger.println("CV: 检测到新版 opus 结构，使用 opus 解析路径");
			return getCVDetailFromOpus(cvIdStr, rootForOpus, viInfo);
		}
		if (jObj == null || !jObj.has("modules")) {
			Logger.println("CV: __INITIAL_STATE__.detail 不存在，回退到 HTML 直接解析");
			return getCVDetailFromHtml(cvIdStr, html, viInfo);
		}

		// 判断动态的类型， 11 图文 12 专栏 1 UP主投稿了 17 直播开播了
//		JSONObject jBasic = jObj.getJSONObject("basic");
//		int type = jBasic.optInt("comment_type");
		JSONArray jParagraphs = null, jTopPics = null, jModules = jObj.getJSONArray("modules");
		JSONObject jUp = null;
		for (int i = 0; i < jModules.length(); i++) {
			JSONObject module = jModules.getJSONObject(i);
			String mType = module.getString("module_type");
			if (mType.equals("MODULE_TYPE_AUTHOR"))
				jUp = module.getJSONObject("module_author");
			else if (mType.equals("MODULE_TYPE_CONTENT"))
				jParagraphs = module.getJSONObject("module_content").getJSONArray("paragraphs");
			else if (mType.equals("MODULE_TYPE_TOP"))
				jTopPics = module.getJSONObject("module_top").getJSONObject("display").getJSONObject("album")
						.getJSONArray("pics");
			else if (mType.equals("MODULE_TYPE_TITLE"))
				viInfo.setVideoName(module.getJSONObject("module_title").getString("text"));
			if (jUp != null && jParagraphs != null && jTopPics != null && viInfo.getVideoName() != null)
				break;
		}
		// 总体大致信息
		String author = jUp.getString("name");
		String authorId = jUp.optString("mid");
		long cTime = jUp.optLong("pub_ts") * 1000;
//		viInfo.setVideoId(opusIdStr);
		viInfo.setAuthor(author);
		viInfo.setAuthorId(authorId);
		// 设置 brief videoName
		for (int i = 0; i < jParagraphs.length(); i++) {
			JSONObject jPara = jParagraphs.getJSONObject(i);
			int paraType = jPara.optInt("para_type");
			if (paraType == 1) {
				JSONArray nodes = jPara.getJSONObject("text").getJSONArray("nodes");
				for (int nIdx = 0; nIdx < nodes.length(); nIdx++) {
					JSONObject node = nodes.getJSONObject(nIdx);
					if ("TEXT_NODE_TYPE_WORD".equals(node.getString("type"))) {
						String text = node.getJSONObject("word").getString("words");
						viInfo.setBrief(text);
						if (viInfo.getVideoName() == null) {
							String videoName = text;
							if (videoName.length() > 15)
								videoName = videoName.substring(0, 15);
							viInfo.setVideoName(videoName);
						}
						break;
					}
				}
			}
		}
		if (viInfo.getVideoName() == null)
			viInfo.setVideoName("空");

		LinkedHashMap<Long, ClipInfo> clipMap = new LinkedHashMap<Long, ClipInfo>();
		int picIndex = 0;
		// 先遍历 jTopPics
		if (jTopPics != null) {
			for (int i = 0; i < jTopPics.length(); i++) {
				String picUrl = jTopPics.getJSONObject(i).getString("url");
				ClipInfo clip = newCommonClip(cvIdStr, viInfo, author, authorId, cTime, null, null);
				setPicOfClip(clip, clipMap, picIndex, picUrl);
				if (viInfo.getVideoPreview() == null)
					viInfo.setVideoPreview(picUrl);
				picIndex++;
			}
		}
		// 再遍历 jParagraphs
		for (int i = 0; i < jParagraphs.length(); i++) {
			JSONObject jPara = jParagraphs.getJSONObject(i);
			int paraType = jPara.optInt("para_type");
			if (paraType == 2) {
				JSONArray pics = jPara.getJSONObject("pic").getJSONArray("pics");
				for (int nIdx = 0; nIdx < pics.length(); nIdx++) {
					String picUrl = pics.getJSONObject(nIdx).getString("url");
					ClipInfo clip = newCommonClip(cvIdStr, viInfo, author, authorId, cTime, null, null);
					setPicOfClip(clip, clipMap, picIndex, picUrl);
					if (viInfo.getVideoPreview() == null)
						viInfo.setVideoPreview(picUrl);
					picIndex++;
				}
			}
		}
		viInfo.setClips(clipMap);
//		viInfo.print();
		// #4 导出文章 HTML/文本（可选）
		try {
			if (nicelee.ui.Global.cvExportHtml) {
				exportArticleHtml(cvIdStr, viInfo, jParagraphs, jTopPics);
			}
		} catch (Throwable t) {
			Logger.println("CV 导出 HTML 失败: " + t.getMessage());
		}
		return viInfo;
	}

	protected void setPicOfClip(ClipInfo clip, LinkedHashMap<Long, ClipInfo> clipMap, int picIndex, String picUrl) {
		clip.setcId(picIndex);
		clip.setPage(picIndex);
		clip.setRemark(picIndex);
		clip.setTitle("第" + picIndex + "张");
		clip.setPicPreview(picUrl);
		LinkedHashMap<Integer, String> links = new LinkedHashMap<Integer, String>();
		links.put(0, picUrl);
		clip.setLinks(links);
		clipMap.put(clip.getcId(), clip);
	}

	protected ClipInfo newCommonClip(String cvIdStr, VideoInfo viInfo, String author, String authorId, long cTime,
			String listName, String listOwnerName) {
		ClipInfo clip = new ClipInfo();
		clip.setAvTitle(viInfo.getVideoName());
		clip.setAvId(cvIdStr);
		clip.setUpName(author);
		clip.setUpId(authorId);
		clip.setcTime(cTime);
		clip.setListName(listName);
		clip.setListOwnerName(listOwnerName);
		return clip;
	}

	/**
	 * 新版专栏结构：root.opus.content.paragraphs（2025+ 版本）
	 * paragraphs 格式与旧版 MODULE_TYPE_CONTENT 相同，可直接复用
	 */
	protected VideoInfo getCVDetailFromOpus(String cvIdStr, JSONObject root, VideoInfo viInfo) {
		JSONObject opus = root.getJSONObject("opus");
		// 标题
		String title = opus.optString("title", cvIdStr);
		if (title.isEmpty()) title = cvIdStr;
		viInfo.setVideoName(title);
		// 封面：优先使用第一张正文图片（见下方循环）；article.cover 仅作为兜底
		String fallbackCover = null;
		try {
			JSONArray coverArr = opus.getJSONObject("article").getJSONArray("cover");
			if (coverArr.length() > 0) fallbackCover = coverArr.getJSONObject(0).getString("url");
		} catch (Exception ignored) {}
		// 作者 uid：opus.pub_info.uid
		long uid = 0;
		long cTime = System.currentTimeMillis();
		try {
			JSONObject pubInfo = opus.getJSONObject("pub_info");
			uid = pubInfo.optLong("uid", 0);
			long pubTs = pubInfo.optLong("pub_time", 0);
			if (pubTs > 0) cTime = pubTs * 1000L;
		} catch (Exception ignored) {}
		// 作者名：upInfo 通常只有 fans，尝试从 root.upInfo 取；否则用 uid
		String upName = String.valueOf(uid);
		try {
			// root 可能有 authorInfo 或其他字段，先尝试
			JSONObject upInfo = root.optJSONObject("upInfo");
			if (upInfo != null && upInfo.has("name")) upName = upInfo.getString("name");
		} catch (Exception ignored) {}
		viInfo.setAuthor(upName);
		viInfo.setAuthorId(String.valueOf(uid));
		// 段落
		JSONArray paragraphs = null;
		try {
			paragraphs = opus.getJSONObject("content").getJSONArray("paragraphs");
		} catch (Exception e) {
			Logger.println("CV opus: 无法读取 paragraphs: " + e.getMessage());
		}
		// 设置 brief / 短标题
		if (paragraphs != null) {
			for (int i = 0; i < paragraphs.length() && viInfo.getBrief() == null; i++) {
				JSONObject p = paragraphs.getJSONObject(i);
				if (p.optInt("para_type") == 1) {
					try {
						JSONArray nodes = p.getJSONObject("text").getJSONArray("nodes");
						for (int n = 0; n < nodes.length(); n++) {
							JSONObject node = nodes.getJSONObject(n);
							String w = node.optJSONObject("word") != null ? node.getJSONObject("word").optString("words") : null;
							if (w != null && !w.trim().isEmpty() && !w.equals("\n")) {
								viInfo.setBrief(w);
								break;
							}
						}
					} catch (Exception ignored) {}
				}
			}
		}
		// 构建 clipMap（图片列表）
		LinkedHashMap<Long, ClipInfo> clipMap = new LinkedHashMap<Long, ClipInfo>();
		int picIndex = 0;
		if (paragraphs != null) {
			for (int i = 0; i < paragraphs.length(); i++) {
				JSONObject jPara = paragraphs.getJSONObject(i);
				int paraType = jPara.optInt("para_type");
				if (paraType == 2) {
					try {
						JSONArray pics = jPara.getJSONObject("pic").getJSONArray("pics");
						for (int n = 0; n < pics.length(); n++) {
							String picUrl = pics.getJSONObject(n).getString("url");
							ClipInfo clip = newCommonClip(cvIdStr, viInfo, upName, String.valueOf(uid), cTime, null, null);
							setPicOfClip(clip, clipMap, picIndex, picUrl);
							if (viInfo.getVideoPreview() == null) viInfo.setVideoPreview(picUrl);
							picIndex++;
						}
					} catch (Exception ignored) {}
				}
			}
		}
		viInfo.setClips(clipMap);
		// 没有正文图片时才回退到文章 banner
		if (viInfo.getVideoPreview() == null) viInfo.setVideoPreview(fallbackCover);
		try {
			if (nicelee.ui.Global.cvExportHtml) {
				exportArticleHtml(cvIdStr, viInfo, paragraphs, null);
			}
		} catch (Throwable t) {
			Logger.println("CV 导出 HTML 失败: " + t.getMessage());
		}
		Logger.println("CV(opus 新版): 解析到 " + picIndex + " 张图片");
		return viInfo;
	}

	/**
	 * 当 __INITIAL_STATE__.detail 缺失时，直接从 HTML 中抓取标题/作者/封面/正文图片。
	 */
	protected VideoInfo getCVDetailFromHtml(String cvIdStr, String html, VideoInfo viInfo) {
		// 标题：<meta property="og:title" content="...">
		String title = matchFirst(html, "<meta\\s+property=\"og:title\"\\s+content=\"([^\"]+)\"", 1);
		if (title == null || title.isEmpty()) title = matchFirst(html, "<title>([^<]+)</title>", 1);
		if (title == null) title = cvIdStr;
		// 去掉 “_哔哩哔哩 ...” 等后缀
		int sep = title.indexOf("_哔哩哔哩");
		if (sep > 0) title = title.substring(0, sep);
		viInfo.setVideoName(title);

		String cover = matchFirst(html, "<meta\\s+property=\"og:image\"\\s+content=\"([^\"]+)\"", 1);
		// 不立即设置封面，等下方扫描完正文图片，优先用第一张正文图片
		String fallbackCover = cover;

		String author = matchFirst(html, "<meta\\s+(?:itemprop|name)=\"author\"\\s+content=\"([^\"]+)\"", 1);
		if (author == null) author = matchFirst(html, "\"author\"\\s*:\\s*\\{[^}]*?\"name\"\\s*:\\s*\"([^\"]+)\"", 1);
		if (author == null) author = "";
		viInfo.setAuthor(author);
		viInfo.setAuthorId("");

		// 收集图片：限定在 <div class="article-content"...> 区域内
		String body = html;
		int aBegin = html.indexOf("article-content");
		if (aBegin > 0) {
			int divEnd = html.indexOf("</article>", aBegin);
			if (divEnd < 0) divEnd = html.indexOf("<footer", aBegin);
			if (divEnd > aBegin) body = html.substring(aBegin, divEnd);
		}
		java.util.regex.Pattern picPat = java.util.regex.Pattern.compile(
				"<img[^>]+(?:data-src|src)=\"([^\"]+)\"", java.util.regex.Pattern.CASE_INSENSITIVE);
		java.util.regex.Matcher mPic = picPat.matcher(body);
		LinkedHashMap<Long, ClipInfo> clipMap = new LinkedHashMap<Long, ClipInfo>();
		int picIndex = 0;
		long cTime = System.currentTimeMillis();
		while (mPic.find()) {
			String picUrl = mPic.group(1);
			if (picUrl.startsWith("//")) picUrl = "https:" + picUrl;
			if (picUrl.contains("bfs/article/") || picUrl.contains("/bfs/new_dyn/")
					|| picUrl.contains("hdslb.com")) {
				ClipInfo clip = newCommonClip(cvIdStr, viInfo, author, "", cTime, null, null);
				setPicOfClip(clip, clipMap, picIndex, picUrl);
				if (viInfo.getVideoPreview() == null) viInfo.setVideoPreview(picUrl);
				picIndex++;
			}
		}
		viInfo.setClips(clipMap);
		if (viInfo.getVideoPreview() == null) viInfo.setVideoPreview(fallbackCover);
		Logger.println("CV(HTML 兜底): 解析到 " + picIndex + " 张图片");
		return viInfo;
	}

	private static String matchFirst(String text, String regex, int group) {
		try {
			java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(text);
			if (m.find()) return m.group(group);
		} catch (Exception ignored) {}
		return null;
	}

	/**
	 * #4 把专栏的标题、正文段落（按顺序）和图片一起导出成 HTML 文件，
	 * 输出位置：{Global.savePath}/{author}/cv{id}.html
	 */
	private void exportArticleHtml(String cvIdStr, VideoInfo viInfo, JSONArray paragraphs, JSONArray topPics) {
		StringBuilder sb = new StringBuilder();
		sb.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">");
		sb.append("<title>").append(escape(viInfo.getVideoName())).append("</title>");
		sb.append("<style>")
		  .append("body{max-width:760px;margin:24px auto;padding:0 12px;font-family:system-ui,'PingFang SC','Microsoft YaHei',sans-serif;line-height:1.7;color:#222}")
		  .append("h1{font-size:1.6em;margin:.2em 0}")
		  .append("h2{font-size:1.3em;margin:1em 0 .4em}")
		  .append("img{max-width:100%;height:auto;display:block;margin:12px auto;border-radius:4px}")
		  .append("img.deco{max-height:64px;margin:6px auto}")
		  .append(".meta{color:#888;font-size:.9em;margin-bottom:16px;border-bottom:1px solid #eee;padding-bottom:8px}")
		  .append("p{margin:.6em 0;white-space:pre-wrap}")
		  .append("ul,ol{padding-left:1.6em;margin:.4em 0}")
		  .append("li{margin:.2em 0}")
		  .append("hr{border:none;border-top:1px dashed #ccc;margin:1.2em 0}")
		  .append("</style></head><body>");
		sb.append("<h1>").append(escape(viInfo.getVideoName())).append("</h1>");
		sb.append("<div class=\"meta\">作者：").append(escape(viInfo.getAuthor()))
		  .append(" · 来源：bilibili 专栏 <a href=\"https://www.bilibili.com/read/")
		  .append(cvIdStr).append("\">").append(cvIdStr).append("</a></div>");
		if (topPics != null) {
			for (int i = 0; i < topPics.length(); i++) {
				sb.append("<img src=\"").append(escape(topPics.getJSONObject(i).optString("url"))).append("\">");
			}
		}
		if (paragraphs != null) {
			int curListLevel = 0;            // 0 表示未开列表
			for (int i = 0; i < paragraphs.length(); i++) {
				JSONObject jPara = paragraphs.getJSONObject(i);
				int paraType = jPara.optInt("para_type");
				boolean isList = (paraType == 6);
				// 列表收尾
				if (curListLevel > 0 && !isList) {
					sb.append("</ul>");
					curListLevel = 0;
				}
				if (paraType == 1) {
					// 文本段，可能内含 H1 风格（font_size>=24 加粗 视为 h2）
					String text = renderTextNodes(jPara.optJSONObject("text"));
					if (text == null || text.trim().isEmpty()) continue;
					boolean asHeading = isHeadingPara(jPara);
					sb.append(asHeading ? "<h2>" : "<p>").append(text).append(asHeading ? "</h2>" : "</p>");
				} else if (paraType == 2) {
					JSONArray pics = jPara.optJSONObject("pic") != null
							? jPara.getJSONObject("pic").optJSONArray("pics") : null;
					if (pics == null) continue;
					for (int n = 0; n < pics.length(); n++) {
						sb.append("<img src=\"").append(escape(pics.getJSONObject(n).optString("url"))).append("\">");
					}
				} else if (paraType == 3) {
					// 分隔线（line.pic.url 是装饰图）
					JSONObject line = jPara.optJSONObject("line");
					if (line != null && line.optJSONObject("pic") != null) {
						sb.append("<img class=\"deco\" src=\"")
						  .append(escape(line.getJSONObject("pic").optString("url"))).append("\">");
					} else {
						sb.append("<hr>");
					}
				} else if (isList) {
					if (curListLevel == 0) {
						sb.append("<ul>");
						curListLevel = 1;
					}
					String text = renderTextNodes(jPara.optJSONObject("text"));
					sb.append("<li>").append(text == null ? "" : text).append("</li>");
				} else if (paraType == 4) {
					// 引用段（部分版本用 4），尽力尝试
					String text = renderTextNodes(jPara.optJSONObject("text"));
					if (text != null && !text.trim().isEmpty())
						sb.append("<blockquote>").append(text).append("</blockquote>");
				}
			}
			if (curListLevel > 0) sb.append("</ul>");
		}
		sb.append("</body></html>");

		String savePath = nicelee.ui.Global.savePath;
		if (savePath == null || savePath.isEmpty()) savePath = "download";
		java.io.File dir = new java.io.File(savePath, sanitize(viInfo.getAuthor()));
		if (!dir.exists()) dir.mkdirs();
		java.io.File outFile = new java.io.File(dir, cvIdStr + ".html");
		try (java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(
				new java.io.FileOutputStream(outFile), java.nio.charset.StandardCharsets.UTF_8)) {
			w.write(sb.toString());
			Logger.println("CV 文章已导出: " + outFile.getAbsolutePath());
			nicelee.ui.Global.lastCvExportFile = outFile.getAbsolutePath();
		} catch (java.io.IOException e) {
			Logger.println("写出 CV HTML 失败: " + e.getMessage());
		}
	}

	/** 把 text.nodes 数组渲染为带样式的 HTML 片段。兼容 node_type=1 / type=TEXT_NODE_TYPE_WORD 两种格式。 */
	private static String renderTextNodes(JSONObject textObj) {
		if (textObj == null) return null;
		JSONArray nodes = textObj.optJSONArray("nodes");
		if (nodes == null) return null;
		StringBuilder sb = new StringBuilder();
		for (int n = 0; n < nodes.length(); n++) {
			JSONObject node = nodes.getJSONObject(n);
			JSONObject word = node.optJSONObject("word");
			if (word == null) continue;
			String w = word.optString("words", "");
			if (w.isEmpty()) continue;
			JSONObject style = word.optJSONObject("style");
			boolean bold = style != null && style.optBoolean("bold", false);
			boolean italic = style != null && style.optBoolean("italic", false);
			String color = word.optString("color", "");
			StringBuilder open = new StringBuilder(), close = new StringBuilder();
			if (color != null && !color.isEmpty()) {
				open.append("<span style=\"color:").append(escape(color)).append("\">");
				close.insert(0, "</span>");
			}
			if (bold) { open.append("<strong>"); close.insert(0, "</strong>"); }
			if (italic) { open.append("<em>"); close.insert(0, "</em>"); }
			sb.append(open).append(escape(w)).append(close);
		}
		return sb.toString();
	}

	/** 当一段文本只包含一个 24pt 加粗 节点时，视为标题 */
	private static boolean isHeadingPara(JSONObject jPara) {
		JSONObject t = jPara.optJSONObject("text");
		if (t == null) return false;
		JSONArray nodes = t.optJSONArray("nodes");
		if (nodes == null || nodes.length() == 0) return false;
		boolean any = false;
		for (int i = 0; i < nodes.length(); i++) {
			JSONObject w = nodes.getJSONObject(i).optJSONObject("word");
			if (w == null) continue;
			int fs = w.optInt("font_size", 17);
			JSONObject style = w.optJSONObject("style");
			boolean bold = style != null && style.optBoolean("bold", false);
			if (fs >= 22 && bold) any = true;
			else if (!w.optString("words", "").trim().isEmpty()) return false;
		}
		return any;
	}

	private static String escape(String s) {
		if (s == null) return "";
		StringBuilder b = new StringBuilder(s.length() + 16);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '<': b.append("&lt;"); break;
				case '>': b.append("&gt;"); break;
				case '&': b.append("&amp;"); break;
				case '"': b.append("&quot;"); break;
				default: b.append(c);
			}
		}
		return b.toString();
	}

	private static String sanitize(String s) {
		if (s == null || s.isEmpty()) return "_";
		return s.replaceAll("[\\\\/:*?\"<>|]", "_");
	}

//	protected VideoInfo getCVDetailCounter352(String cvIdNumber) {
//		Logger.println("URL4PictureCVParser正在获取结果: cv" + cvIdNumber);
//		String cvIdStr = "cv" + cvIdNumber;
//		VideoInfo viInfo = new VideoInfo();
//		viInfo.setVideoId(cvIdStr);
//		// 容易被风控 {"code":-352,"message":"-352","ttl":1}
//		String url = "https://api.bilibili.com/x/article/view?gaia_source=main_web&web_location=333.976&id="
//				+ cvIdNumber;
//		url = API.encWbi(url);
////		HashMap<String, String> headers_json = new HttpHeaders().getCommonHeaders();
//		HashMap<String, String> headers_json = new HashMap<>();
//		headers_json.put("Host", "api.bilibili.com");
//		headers_json.put("User-Agent",
//				"Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/109.0");
//		headers_json.put("Connection", "keep-alive");
//		headers_json.put("Accept",
//				"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
//		headers_json.put("Accept-Language", "zh-CN,zh;q=0.8,en-US;q=0.5,en;q=0.3");
////		headers_json.put("Origin", "https://www.bilibili.com");
////		headers_json.put("Referer", "https://www.bilibili.com");
//		String json = util.getContent(url, headers_json, HttpCookies.globalCookiesWithFingerprint());
//		Logger.println(url);
//		Logger.println(json);
//		JSONObject jObj = new JSONObject(json).getJSONObject("data");
//		JSONObject jUp = jObj.getJSONObject("author");
//		
//		// 总体大致信息
//		String videoName = jObj.getString("title");
//		String brief = jObj.getString("summary");
//		String author = jUp.getString("name");
//		String authorId = jUp.optString("mid");
//		String videoPreview = jObj.getJSONArray("image_urls").getString(0);
//		viInfo.setVideoName(videoName);
//		viInfo.setBrief(brief);
//		viInfo.setAuthor(author);
//		viInfo.setAuthorId(authorId);
//		viInfo.setVideoPreview(videoPreview);
//		
//		LinkedHashMap<Long, ClipInfo> clipMap = new LinkedHashMap<Long, ClipInfo>();
//		
//		JSONObject opus = jObj.optJSONObject("opus");
//		if (opus != null) {
//			JSONArray jParas = jObj.getJSONObject("opus").getJSONObject("content").getJSONArray("paragraphs");
//			for (int i = 0, picIndex = 0; i < jParas.length(); i++) {
//				JSONObject para = jParas.getJSONObject(i);
//				if (para.getInt("para_type") != 2) {
//					continue;
//				}
//				JSONArray pics = para.getJSONObject("pic").getJSONArray("pics");
//				for (int j = 0; j < pics.length(); j++) {
//					String picUrl = pics.getJSONObject(j).getString("url");
//					ClipInfo clip = new ClipInfo();
//					clip.setAvTitle(viInfo.getVideoName());
//					clip.setAvId(cvIdStr);
//					clip.setcId(picIndex);
//					clip.setPage(picIndex);
//					clip.setRemark(picIndex);
//					clip.setTitle("第" + picIndex + "张");
//					clip.setPicPreview(picUrl);
//					clip.setUpName(author);
//					clip.setUpId(authorId);
//					LinkedHashMap<Integer, String> links = new LinkedHashMap<Integer, String>();
//					links.put(0, picUrl);
//					clip.setLinks(links);
//					clipMap.put(clip.getcId(), clip);
//					picIndex++;
//				}
//			}
//		} else {
//			String content = jObj.getString("content");
//			Logger.println(content);
//			Pattern picPattern = Pattern.compile("img src=\"([^\"]+)\"");
//			Matcher m = picPattern.matcher(content);
//			int picIndex = 0;
//			while(m.find()) {
//				String picUrl = m.group(1);
//				if(picUrl.startsWith("//"))
//					picUrl = "http:" + picUrl;
//				ClipInfo clip = new ClipInfo();
//				clip.setAvTitle(viInfo.getVideoName());
//				clip.setAvId(cvIdStr);
//				clip.setcId(picIndex);
//				clip.setPage(picIndex);
//				clip.setRemark(picIndex);
//				clip.setTitle("第" + picIndex + "张");
//				clip.setPicPreview(picUrl);
//				clip.setUpName(author);
//				clip.setUpId(authorId);
//				LinkedHashMap<Integer, String> links = new LinkedHashMap<Integer, String>();
//				links.put(0, picUrl);
//				clip.setLinks(links);
//				clipMap.put(clip.getcId(), clip);
//				picIndex++;
//			}
//		}
//		
//		viInfo.setClips(clipMap);
//		viInfo.print();
//		return viInfo;
//	}

}
