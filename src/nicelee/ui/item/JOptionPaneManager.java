package nicelee.ui.item;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JDialog;

import nicelee.ui.item.JOptionPane;

import nicelee.ui.Global;

public class JOptionPaneManager {

	static JOptionPaneManager instance4CommonMsg = new JOptionPaneManager();
	static JOptionPaneManager instance4ErrMsg = new JOptionPaneManager();
	static JOptionPaneManager instance4Confirm = new JOptionPaneManager();

	public static void showMsgWithNewThread(String title, String msg) {
		instance4CommonMsg.showMsgWithNewThread0(title, msg, false);
	}

	public static void alertErrMsgWithNewThread(String title, String msg) {
		instance4ErrMsg.showMsgWithNewThread0(title, msg, true);
	}

	/**
	 * 同步弹出"已下载视频再次提交"的确认框，居中且总在最前。
	 * @return 1 = 继续下载 (重复下载)；0 = 不下载；-1 = 全部取消(中断其它弹窗)
	 */
	public static int confirmReDownload(String title, String msg) {
		return instance4Confirm.confirmReDownload0(title, msg);
	}

	private int confirmReDownload0(String title, String msg) {
		// 与 showMsgWithNewThread0 共享同一计数容量上限，避免同一时刻弹窗过多
		if (promptThreads.size() >= Global.maxAlertPrompt) {
			return 0; // 超过上限，直接按"不下载"处理
		}
		Thread current = Thread.currentThread();
		promptThreads.add(current);
		final AtomicInteger result = new AtomicInteger(0);
		try {
			Object[] options = { "继续下载（重复下载）", "不下载", "全部取消" };
			javax.swing.JOptionPane pane = new javax.swing.JOptionPane(buildMsgComponent(msg),
					javax.swing.JOptionPane.QUESTION_MESSAGE, javax.swing.JOptionPane.YES_NO_CANCEL_OPTION, null,
					options, options[1]);
			JDialog dialog = pane.createDialog(null, title);
			dialog.setAlwaysOnTop(true);
			dialog.setLocationRelativeTo(null); // 屏幕居中
			dialog.setVisible(true);
			Object sel = pane.getValue();
			dialog.dispose();
			if (sel != null) {
				for (int i = 0; i < options.length; i++) {
					if (options[i].equals(sel)) {
						if (i == 0) result.set(1);
						else if (i == 2) result.set(-1);
						break;
					}
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		} finally {
			synchronized (promptThreads) {
				if (result.get() == -1) {
					interruptAllThread();
				} else {
					promptThreads.remove(current);
				}
			}
		}
		return result.get();
	}

	private static java.awt.Component buildMsgComponent(String msg) {
		javax.swing.JTextArea ta = new javax.swing.JTextArea(msg);
		ta.setEditable(false);
		ta.setOpaque(false);
		ta.setFont(new javax.swing.JLabel().getFont());
		return ta;
	}

//	public static void alertErrMsg(String title, String msg) {
//		instance4ErrMsg.showMsg0(title, msg, true);
//	}

	private ConcurrentLinkedQueue<Thread> promptThreads = new ConcurrentLinkedQueue<Thread>();

//	private void showMsg0(String title, String msg, boolean isErrMsg) {
//		if ((Global.isAlertIfDownloded || isErrMsg) && promptThreads.size() < Global.maxAlertPrompt) {
//			promptThreads.add(Thread.currentThread());
//
//			Object[] options = { "关闭", "关闭所有" };
//			int m = JOptionPane.showOptionDialog(null, msg, title, JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE,
//					null, options, options[0]);
//			synchronized (promptThreads) {
//				if (m == 1) {
//					interruptAllThread();
//				} else {
//					promptThreads.remove(Thread.currentThread());
//				}
//			}
//		}
//	}

	private void showMsgWithNewThread0(String title, String msg, boolean isErrMsg) {
		if ((Global.isAlertIfDownloded || isErrMsg) && promptThreads.size() < Global.maxAlertPrompt) {
			Thread t = new Thread(new Runnable() {
				public void run() {
					Object[] options = { "关闭", "关闭所有" };
					javax.swing.JOptionPane pane = new javax.swing.JOptionPane(buildMsgComponent(msg),
							isErrMsg ? javax.swing.JOptionPane.ERROR_MESSAGE : javax.swing.JOptionPane.PLAIN_MESSAGE,
							javax.swing.JOptionPane.YES_NO_OPTION, null, options, options[0]);
					JDialog dialog = pane.createDialog(null, title);
					dialog.setAlwaysOnTop(true);
					dialog.setLocationRelativeTo(null); // 屏幕居中
					dialog.setVisible(true);
					Object sel = pane.getValue();
					dialog.dispose();
					int m = (sel != null && sel.equals(options[1])) ? 1 : 0;

					synchronized (promptThreads) {
						if (m == 1) {
							interruptAllThread();
						} else {
							promptThreads.remove(Thread.currentThread());
						}
					}
				}
			});
			promptThreads.add(t);
			t.start();
		}
	}

	private void interruptAllThread() {
		// 不管怎样，先移除当前线程
		promptThreads.remove(Thread.currentThread());
		for (Thread t : promptThreads) {
			if (t.isAlive()) {
				t.interrupt();
			}
		}
	}
}
