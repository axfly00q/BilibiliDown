# BilibiliDown Web 控制台 — 一步步打开并验证全部功能

> 本指南面向 Windows + PowerShell 环境（Linux/Mac 命令略有不同，会在末尾给出替代）。
> 假设你的项目根目录为 `d:\BilibiliDown-master\BilibiliDown-master\`，下文用 `<ROOT>` 代指。

---

## 0. 前置环境

| 软件 | 版本要求 | 检查命令 |
|---|---|---|
| JDK | **Java 17+**（推荐 21 / 24） | `java -version` |
| PowerShell | 5.1+ | `$PSVersionTable.PSVersion` |
| 浏览器 | Chromium / Edge / Firefox 最新 | — |

依赖 jar 已在 `libs\` 目录里，无需 Maven/Gradle。

---

## 1. 编译源码

打开 PowerShell，进入项目根目录：

```powershell
cd 'd:\BilibiliDown-master\BilibiliDown-master'

# 1) 收集所有 .java 源文件清单
New-Item -ItemType Directory -Path build -Force | Out-Null
Get-ChildItem -Recurse -Filter *.java -Path src,src-launcher |
    ForEach-Object { $_.FullName } | Set-Content build\sources.txt -Encoding ASCII

# 2) 编译（输出到 build\）
javac -d build -encoding UTF-8 -nowarn -cp "libs\*" "@build\sources.txt"
```

预计输出 ~270 个 `.class` 文件。无报错即成功。

---

## 2. 拷贝资源（HTML/CSS/JS/图片）

Java 不会自动复制非 `.java` 资源：

```powershell
Copy-Item -Recurse -Force src\resources build\
```

> 之后 **只改前端 HTML/CSS/JS 不需要重新编译 Java**，只要再次执行
> `Copy-Item -Force src\resources\web-console\* build\resources\web-console\` 就立刻生效。

---

## 3. 启动应用

### 3.1 桌面 + Web 双开模式（推荐首次使用）

```powershell
java -Djava.awt.headless=false -cp "build;libs\*" nicelee.ui.FrameMain
```

启动约 10–15 秒后：
- 桌面 Swing 窗口弹出
- 终端打印类似：
  ```
  Web 控制台已启动: http://127.0.0.1:8787/console/login.html
  初始密码: 6749ef19ebe9  (仅本次会话有效)
  ```
- **请务必复制这个初始密码**，浏览器登录会用到。

### 3.2 后台 / Headless 服务器模式（无图形界面）

```powershell
# Windows 即使 headless，也必须保留 java.awt.headless=false（QrCodeUtil 用到 BufferedImage）
java -Djava.awt.headless=false -Dbilibili.headless=true `
     -cp "build;libs\*" nicelee.ui.FrameMain `
     1> app.out.log 2> app.err.log
```

完全无 Swing 窗口；密码同样写到 `app.out.log` 末尾。可用：

```powershell
Get-Content app.out.log | Select-String '初始密码'
```

### 3.3 持久化登录密码（可选）

编辑 [config/app.config](config/app.config)，添加一行：

```
bilibili.web.auth.password=你自己的密码
```

下次启动就用这个密码而不是随机密码。

---

## 4. 浏览器登录

打开 <http://127.0.0.1:8787/console/login.html>

- 用户名：`admin`
- 密码：第 3 步获得的密码
- 右下角下拉框可切 **中文 / English / 日本語**

登录成功后跳转到控制台首页 `/console/index.html`，看到 8 张功能卡片。

---

## 5. 功能逐项验证清单

> 每条都给出「操作」+「期望结果」。逐项打勾即可完成全验收。

### 5.1 ✅ 主题切换（顶栏右上）
- 点击「浅色 / 自动 / 暗色」三按钮
- 期望：界面颜色立即切换；刷新页面后保持记忆
- 来源：[src/resources/web-console/js/theme.js](src/resources/web-console/js/theme.js)

### 5.2 ✅ 多语言切换（顶栏下拉）
- 选择 English / 日本語 / 中文
- 期望：所有标题、按钮、提示文字立刻翻译；刷新后保持
- 字典：[src/resources/web-console/js/i18n.js](src/resources/web-console/js/i18n.js)

### 5.3 ✅ 解析视频
- 进入「解析与下载」（`/console/parse.html`）
- 输入框输入：`BV1XGAUzJEZB`（或任意视频 URL）
- 点「解析」
- 期望：下方出现该视频信息卡 + 多 P/多视频列表 + 每行可选清晰度下拉

### 5.4 ✅ 提交下载
- 在解析结果某一行选清晰度（如 1080P）
- 点「下载」
- 期望：弹出「已加入下载队列」提示

### 5.5 ✅ 下载任务实时进度（SSE）
- 切到「任务列表」（`/console/downloads.html`）
- 期望：表格显示刚才提交的任务；进度百分比 / 速度 **每秒自动更新**（无需刷新）
- 完成后列「下载文件」按钮可点击直接下载到本地浏览器

### 5.6 ✅ 暂停 / 继续 / 删除任务
- 任务列表每行的「暂停」按钮 → 状态切到「已暂停」
- 再点「继续」恢复；点「删除」从列表移除

### 5.7 ✅ 设置在线编辑
- 进入「设置」（`/console/settings.html`）
- 修改「下载根目录」或「文件命名格式」
- 点「保存」
- 期望：弹出保存成功；用记事本打开 [config/app.config](config/app.config) 看到新值已写入

### 5.8 ✅ B 站账号 — 扫码登录
- 进入「B 站账号」（`/console/account.html`）
- 未登录时**只显示**「尚未登录」+「扫码登录」按钮（Cookie 卡片**已隐藏**）
- 点「扫码登录」→ 显示二维码
- 用手机 B 站 App 扫码并确认
- 期望：页面自动刷新出头像、昵称、UID，**Cookie 刷新卡片自动出现**

### 5.9 ✅ Cookie 刷新（已登录后才出现）
- 5.8 完成后，「Cookie 刷新」卡片可见
- 点「执行刷新」→ 后端调用 B 站接口轮转 SESSDATA
- 期望：成功提示 + 刷新时间更新

### 5.10 ✅ 退出 B 站账号
- 在账号页点「退出登录」
- 期望：头像消失，回到未登录态，**Cookie 卡片重新隐藏**

### 5.11 ✅ 移动端响应式（手机视图）
- 在 Edge / Chrome 按 **F12 → 切换设备工具栏 (Ctrl+Shift+M) → iPhone SE**
- 刷新任意控制台页
- 期望：
  - 顶栏纵向堆叠
  - 卡片栅格变 **单列**
  - 表单 label/input 上下排列
- CSS 来源：[src/resources/web-console/css/style.css](src/resources/web-console/css/style.css#L125)（`@media (max-width: 768px)`）

### 5.12 ✅ 退出控制台
- 顶栏「退出登录」按钮 → 回到 `/console/login.html`
- BD_SESSION cookie 立即失效

---

## 6. 用 curl 做接口冒烟测试（可选）

```powershell
$base = 'http://127.0.0.1:8787'
$pwd  = '6749ef19ebe9'   # 替换为你的密码

# 1) 登录拿 Cookie
$sess = Invoke-WebRequest -Uri "$base/api/console/login" -Method POST `
  -Body (@{username='admin';password=$pwd} | ConvertTo-Json) `
  -ContentType 'application/json' -SessionVariable s -UseBasicParsing
$s.Cookies.GetCookies("$base") | Select-Object Name,Value

# 2) 调几个核心接口
Invoke-WebRequest "$base/api/account/status"      -WebSession $s -UseBasicParsing | Select Content
Invoke-WebRequest "$base/api/settings/list"       -WebSession $s -UseBasicParsing | Select StatusCode
Invoke-WebRequest "$base/api/downloads/list"      -WebSession $s -UseBasicParsing | Select StatusCode
```

期望：全部 `200 OK`。

---

## 7. 停止服务

桌面模式：直接关 Swing 窗口或托盘退出。
Headless 模式：

```powershell
Get-Process java | Where-Object { $_.MainWindowTitle -eq '' } | Stop-Process
```

---

## 8. 故障排查

| 现象 | 排查 |
|---|---|
| `连接被拒绝 / ERR_CONNECTION_REFUSED` | 进程在但端口没监听：`netstat -ano \| Select-String ':8787'`。无输出说明 SocketServer 未启动，杀掉 Java 重新执行第 3 步。 |
| 浏览器一直停在登录页 | 看终端是否打印「初始密码」；密码每次重启变化，没设 `bilibili.web.auth.password` 就用最新一行。 |
| 页面文字未翻译 / JS 报错 `Unexpected string` | 资源未拷贝到 build。重新执行第 2 步：`Copy-Item -Force src\resources\web-console\js\i18n.js build\resources\web-console\js\i18n.js` |
| 解析视频报「需要登录」 | 先在「B 站账号」页扫码登录后再解析。 |
| 二维码不显示 | 必须保留 `-Djava.awt.headless=false`，否则 `BufferedImage` 抛 HeadlessException。 |
| Settings 保存后没生效 | 部分设置（如端口、bind host）需重启进程。 |
| 桌面端样式正常但手机端没变化 | 强制刷新（Ctrl+F5）或清浏览器缓存；CSS 在 [style.css#L125](src/resources/web-console/css/style.css#L125)。 |

---

## 9. Linux / Mac 命令对照

```bash
# 编译
find src src-launcher -name "*.java" > build/sources.txt
javac -d build -encoding UTF-8 -nowarn -cp "libs/*" @build/sources.txt
# 资源
cp -r src/resources build/
# 启动
java -Djava.awt.headless=false -cp "build:libs/*" nicelee.ui.FrameMain
# Headless
java -Djava.awt.headless=false -Dbilibili.headless=true -cp "build:libs/*" nicelee.ui.FrameMain > app.out.log 2>&1 &
```

---

完成以上 12 项 ✅，本次 Web 控制台 Phase A–E 的全部功能即视为通过验收。
