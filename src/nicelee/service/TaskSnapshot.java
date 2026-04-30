package nicelee.service;

/**
 * 任务快照（Web API 与 SSE 输出用）。
 */
public class TaskSnapshot {
	public String id;          // record: avid-qn-pPage（用作前端唯一标识）
	public String avId;
	public int page;
	public int qn;
	public int realQn;
	public String title;       // 显示名
	public String fileName;    // 当前生成的文件全路径或相对名（兼容旧字段：通常为绝对路径）
	public String absPath;     // 绝对路径（与 fileName 等价；新前端使用此字段更明确）
	public String relPath;     // 相对 Global.savePath 的相对路径，用于 /files/<relPath> 下载
	public String status;      // active / done / paused / fail / queued / processing / none
	public long currentDown;
	public long totalSize;
	public long speed;         // bytes/s
	public String lastError;   // 最近一次失败原因（仅 status==fail 时有意义）
	public int priority;       // 用户设定的优先级（数值越大越优先）
}
