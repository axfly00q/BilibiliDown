package nicelee.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URL;
import java.util.Enumeration;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import nicelee.ui.item.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;

import nicelee.bilibili.INeedLogin;
import nicelee.bilibili.PackageScanLoader;
import nicelee.bilibili.util.CmdUtil;
import nicelee.bilibili.util.ConfigUtil;
import nicelee.bilibili.util.HttpCookies;
import nicelee.bilibili.util.RepoUtil;
import nicelee.bilibili.util.ResourcesUtil;
import nicelee.bilibili.util.SysUtil;
import nicelee.ui.item.MJTitleBar;
import nicelee.ui.thread.BatchDownloadRbyRThread;
import nicelee.ui.thread.CookieRefreshThread;
import nicelee.ui.thread.LoginThread;
import nicelee.ui.thread.MonitoringThread;

public class FrameMain extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	JTabbedPane jTabbedpane;// 存放选项卡的组件
	MJTitleBar titleBar;// 标题栏组件

	public static void main(String[] args) {
		System.out.println();
		// System.getProperties().setProperty("file.encoding", "utf-8");
		boolean isFFmpegSupported = SysUtil.surportFFmpegOfficially();
		System.out.println("Java version:" + System.getProperty("java.specification.version"));
		System.out.println(ResourcesUtil.baseDirectory());
		// 读取配置文件
		ConfigUtil.initConfigs();
		// 系统属性覆盖：-Dbilibili.headless=true
		String hd = System.getProperty("bilibili.headless");
		if (hd != null) Global.headless = "true".equalsIgnoreCase(hd);
		// -v 打印版本，然后退出
		if(args.length == 1 && "-v".equalsIgnoreCase(args[0])) {
			System.out.println(Global.version);
			System.exit(0);
		}
		// 初始化 - 检查对数据文件夹是否有"写"的权限
		InitCheck.checkFileAccess();
		// 显示过渡动画（无头模式跳过）
		if (!Global.headless) {
			Global.frWaiting = new FrameWaiting();
			Global.frWaiting.start();
		}

		if (Global.lockCheck) {
			if (ConfigUtil.isRunning()) {
				Global.frWaiting.stop();
				JOptionPane.showMessageDialog(null, "程序已经在运行!", "请注意!!", JOptionPane.WARNING_MESSAGE);
				return;
			}
			ConfigUtil.createLock();
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				ConfigUtil.deleteLock();
			}));
		}
		
		nicelee.bilibili.util.custom.System.init(Global.syncServerTime);
//		// 如果存在hosts文件，那么使之生效
//		if (HostSetUtil.readHostsFromFile("config/hosts.config")) {
//			HostSetUtil.injectHosts();
//		}
		// 初始化主题
		initUITheme();
		// 初始化UI
		FrameMain main = new FrameMain();
		main.InitUI();
		// 初始化监控线程，用于刷新下载面板
		MonitoringThread th = new MonitoringThread();
		th.start();

		// 尝试刷新cookie
		INeedLogin inl = new INeedLogin();
		String cookiesStr = inl.readCookies();
		if (cookiesStr != null) {
			Global.needToLogin = true;
			if(Global.tryRefreshCookieOnStartup && !Global.runWASMinBrowser) {
				HttpCookies.setGlobalCookies(HttpCookies.convertCookies(cookiesStr));
				CookieRefreshThread.showTips = false;
				CookieRefreshThread thCR = CookieRefreshThread.newInstance();
				thCR.start();
				try {
					thCR.join();
				} catch (InterruptedException e1) {
				}
				CookieRefreshThread.showTips = true;
			}
		}
		// 初始化 - 登录
		LoginThread loginTh = new LoginThread();
		loginTh.start();

		// 初始化 - ffmpeg环境判断
		InitCheck.checkFFmpeg(isFFmpegSupported);

		//
		if (Global.saveToRepo) {
			RepoUtil.init(false);
		}
//		FrameQRCode qr = new FrameQRCode("https://www.bilibili.com/");
//		qr.initUI();
//		qr.dispose();
		// 预扫描加载类
		PackageScanLoader.validParserClasses.isEmpty();
		// 自动启动 Web 控制台 HTTP 服务（鉴权见 app.config: bilibili.web.auth.*）
		try { Global.ensureWebServerStarted(); } catch (Throwable t) { t.printStackTrace(); }
		if(Global.batchDownloadRbyRRunOnStartup) {
			// 开始按计划周期性批量下载
			new Thread(new Runnable() {
				@Override
				public void run() {
					// 等待相关线程运行完毕
					try {
						loginTh.join();
					} catch (InterruptedException e) {}
					new BatchDownloadRbyRThread(Global.batchDownloadConfigName).start();
				}
			}).start();
		}
		System.out.println("如果过度界面显示时间过长，可双击跳过");
		try {
			while (!Global.headless && Global.frWaiting != null && Global.frWaiting.isVisible()) {
				Thread.sleep(1000);
			}
		} catch (InterruptedException e) {
			if (Global.frWaiting != null) Global.frWaiting.stop();
		}
		Global.frWaiting = null;
		if (!Global.headless) {
			main.setVisible(true);
			main.setExtendedState(JFrame.NORMAL);
			main.toFront();
		} else {
			System.out.println("[headless] Web 控制台模式启动完成，未弹出桌面主窗口。");
			if (Global.autoOpenBrowser) {
				String url = "http://127.0.0.1:" + Global.serverPort + "/console/index.html";
				try {
					java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
					if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
						desktop.browse(new java.net.URI(url));
						System.out.println("[headless] 已在浏览器中打开: " + url);
					} else {
						// 回退：调用系统命令打开
						String os = System.getProperty("os.name", "").toLowerCase();
						if (os.contains("win")) {
							Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
						} else if (os.contains("mac")) {
							Runtime.getRuntime().exec(new String[]{"open", url});
						} else {
							Runtime.getRuntime().exec(new String[]{"xdg-open", url});
						}
						System.out.println("[headless] 已调用系统命令打开: " + url);
					}
				} catch (Exception e) {
					System.out.println("[headless] 自动打开浏览器失败，请手动访问: " + url);
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * 
	 */
	static void initUITheme() {
		try {
			if (!Global.themeDefault) {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				Font font = new Font("Dialog", Font.PLAIN, 12);
				Enumeration<Object> keys = UIManager.getDefaults().keys();
				while (keys.hasMoreElements()) {
					Object key = keys.nextElement();
					Object value = UIManager.get(key);
					if (value instanceof javax.swing.plaf.FontUIResource) {
						UIManager.put(key, font);
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 */
	public void InitUI() {

		this.setTitle("BiliBili Down~~" + Global.version);
		this.setSize(1200, 745);
		this.setResizable(false);
		this.setLocationRelativeTo(null);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		URL iconURL = this.getClass().getResource("/resources/favicon.png");
		ImageIcon icon = new ImageIcon(iconURL);
		this.setIconImage(icon.getImage());

		// pane 作为内容容器
		JPanel pane = new JPanel();
		pane.setBackground(Color.WHITE);
		pane.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY));
		// 添加标题栏
		titleBar = new MJTitleBar(this, true, true);
		pane.add(titleBar);

		jTabbedpane = new JTabbedPane();
		Global.tabs = jTabbedpane;
		jTabbedpane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		jTabbedpane.setPreferredSize(new Dimension(1194, 706));
		// Index Tab
		Global.index = new TabIndex(jTabbedpane);
		jTabbedpane.addTab("主页", Global.index);
		// 下载页
		Global.downloadTab = new TabDownload();
		jTabbedpane.addTab("下载页", Global.downloadTab);
		// 作品页
//		JLabel label = new JLabel("作品页");
//		TabVideo tab = new TabVideo(label);
//		jTabbedpane.addTab("作品页", tab);
//		jTabbedpane.setTabComponentAt(jTabbedpane.indexOfComponent(tab), label);
//		jTabbedpane.addTab("设置页", new TabSettings(jTabbedpane));
		
		pane.add(jTabbedpane);
		this.setContentPane(pane);
		// 关闭窗口时
		this.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				super.windowClosing(e);
				CmdUtil.deleteAllInactiveCmdTemp();
			}
		});
//		this.setVisible(true);
		SysTray.buildSysTray(this, icon.getImage());
	}

	@Override
	public void setTitle(String title) {
		super.setTitle(title);
		if (titleBar != null) {
			titleBar.setTitle(title);
		}
	}

}
