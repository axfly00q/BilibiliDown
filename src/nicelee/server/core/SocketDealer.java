package nicelee.server.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nicelee.server.auth.AuthFilter;

public class SocketDealer extends PathDealer implements Runnable {

	// 与客户端之间的联系
	BufferedReader in;
	BufferedWriter out;
	OutputStream outRaw;

	/**
	 * SSE / 长连接劫持标志：Controller 写入 SSE 数据流时设置 true，
	 * SocketDealer 在 finally 中跳过关闭 socket，由 Controller 自行管理生命周期。
	 */
	public static final ThreadLocal<Boolean> HIJACKED = new ThreadLocal<>();

	public SocketDealer(Socket socketClient) {
		super(socketClient);
	}

	final static Pattern urlPattern = Pattern.compile("^(?:GET|POST) ([^ \\?]+)\\??([^ \\?]*) HTTP.*$");
	final static Pattern contentLengthPattern = Pattern.compile("^content-length *: *([0-9]+)$");
	final static Pattern headersPattern = Pattern.compile("^([^:]+) *: *(.+)$");
	@Override
	public void run() {
		String path = null, param = null, data = null;
		HashMap<String, String> headersMap = new HashMap<>(16, 0.999f);
		int dataLen = -1;
		try {
			in = new BufferedReader(new InputStreamReader(socketClient.getInputStream(), "utf-8"));
			outRaw = socketClient.getOutputStream();
			out = new BufferedWriter(new OutputStreamWriter(outRaw, "utf-8"));
			
			// 读取url请求
			String line = null;
			while ((line = in.readLine()) != null) {
				// 处理Path
				Matcher matcher = urlPattern.matcher(line);
				if(path == null && matcher.find()) {
					// System.out.println("正在处理请求: " + line);
					path = matcher.group(1);
					param = matcher.group(2);
					continue;
				}
				
				// 处理Content-Length
				matcher = contentLengthPattern.matcher(line.toLowerCase());
				if(dataLen<0 && matcher.find()) {
					dataLen = Integer.parseInt(matcher.group(1));
				}
				
				// 处理headers
				matcher = headersPattern.matcher(line.toLowerCase());
				if(matcher.find()) {
					headersMap.put(matcher.group(1), matcher.group(2));
					//System.out.printf("header-%s : %s\r\n", matcher.group(1), matcher.group(2));
				}
				
				// 处理结尾
				if(line.length() == 0) {
					if(dataLen > 0) {
						char[] buffer = new char[dataLen];
						in.read(buffer);
						data = new String(buffer);
						//System.out.println(data);
					}
					break;
				}
			}
			
			// 处理请求并返回内容
			// 鉴权过滤
			if (AuthFilter.handle(out, path, headersMap) == null) {
				return;
			}
			dealRequest(out, outRaw, path, param, data, headersMap);
			
		} catch (SocketException e) {
		} catch (IOException e) {
		} catch (IndexOutOfBoundsException e) {
		} catch (Exception e) {
			//e.printStackTrace();
		} finally {
			// 清理 AuthFilter 设置的会话 ThreadLocal，避免线程复用泄漏
			nicelee.server.auth.SessionStore.CURRENT.remove();
			// SSE / 长连接劫持：保留 socket 由 Controller 自行关闭
			Boolean hijacked = HIJACKED.get();
			HIJACKED.remove();
			if (Boolean.TRUE.equals(hijacked)) {
				return;
			}
			//System.out.println(path + " -线程结束...");
			try {
				in.close();
			} catch (Exception e) {
			}
			try {
				out.close();
			} catch (Exception e) {
			}
			try {
				socketClient.close();
			} catch (Exception e) {
			}
		}
	}
	
}
