@echo off
:: =========================================================
:: 用 jpackage 将 BilibiliDown 打包为 Windows 应用
::   - 默认产出 app-image：dist\BilibiliDown\ 目录（内含 BilibiliDown.exe + 自带 JRE）
::     可压缩成 zip 直接分发，双击 .exe 启动 -> 自动打开浏览器到 Web 控制台。
::   - 如本机已安装 WiX Toolset 3.x，可改 --type 为 exe 或 msi 生成单文件安装包。
:: 前提：JDK 17+（含 jpackage）已在 PATH。
:: =========================================================
chcp 65001 >nul
cd /d %~dp0
setlocal enabledelayedexpansion

set APP_NAME=BilibiliDown
set APP_VERSION=6.41
set APP_VENDOR=nicelee
set DIST_INPUT=%~dp0dist-input
set DIST_OUT=%~dp0dist

echo [1/6] 清理旧产物 ...
if exist "%DIST_INPUT%" rmdir /s /q "%DIST_INPUT%"
if exist "%DIST_OUT%" rmdir /s /q "%DIST_OUT%"
if exist build-launcher rmdir /s /q build-launcher
mkdir "%DIST_INPUT%"
mkdir build-launcher

echo [2/6] 编译主程序 src\ ...
if exist build rmdir /s /q build
mkdir build
powershell -NoProfile -Command "Get-ChildItem -Recurse src -Filter *.java | Where-Object { $_.FullName -notmatch '\\test\\junit\\' } | ForEach-Object { $_.FullName } | Set-Content -Encoding ASCII build\sources.txt"
javac -d build -encoding UTF-8 -nowarn -cp "libs\core-3.3.3.jar;libs\javax.mail-1.6.2.jar;libs\jaf-1.1.1-activation.jar" "@build\sources.txt"
if errorlevel 1 ( echo 主程序编译失败 & exit /b 1 )
xcopy /e /y /q src\resources\* build\resources\ >nul

echo [3/6] 编译启动器 src-launcher\ ...
powershell -NoProfile -Command "Get-ChildItem -Recurse src-launcher -Filter *.java | ForEach-Object { $_.FullName } | Set-Content -Encoding ASCII build-launcher\sources.txt"
javac -d build-launcher -encoding UTF-8 -nowarn "@build-launcher\sources.txt"
if errorlevel 1 ( echo 启动器编译失败 & exit /b 1 )

echo [4/6] 打包 INeedBiliAV.jar / launch.jar ...
pushd build
jar cfe "%DIST_INPUT%\INeedBiliAV.jar" nicelee.ui.FrameMain .
popd
pushd build-launcher
jar cfe "%DIST_INPUT%\launch.jar" nicelee.memory.App .
popd

echo [5/6] 复制依赖 (libs / ffmpeg / config) ...
xcopy /e /y /q libs "%DIST_INPUT%\libs\" >nul
if exist release\ffmpeg.exe copy /y release\ffmpeg.exe "%DIST_INPUT%\" >nul
if not exist "%DIST_INPUT%\config" mkdir "%DIST_INPUT%\config"
if exist release\config\app.config copy /y release\config\app.config "%DIST_INPUT%\config\" >nul

echo [6/6] 生成 app-image ...
jpackage ^
  --type app-image ^
  --name "%APP_NAME%" ^
  --app-version %APP_VERSION% ^
  --vendor "%APP_VENDOR%" ^
  --input "%DIST_INPUT%" ^
  --main-jar launch.jar ^
  --main-class nicelee.memory.App ^
  --java-options "-Dfile.encoding=utf-8" ^
  --java-options "-Dbilibili.headless=true" ^
  --java-options "-Dbilibili.headless.autoOpenBrowser=true" ^
  --java-options "-Dhttps.protocols=TLSv1.2" ^
  --dest "%DIST_OUT%" ^
  --add-modules java.base,java.desktop,java.naming,java.net.http,java.logging,java.sql,java.security.jgss,jdk.crypto.ec,jdk.unsupported

if errorlevel 1 ( echo jpackage 失败 & exit /b 1 )

echo.
echo ============================================================
echo  完成！应用目录: %DIST_OUT%\%APP_NAME%
echo  双击 %DIST_OUT%\%APP_NAME%\%APP_NAME%.exe 启动
echo  - 不会弹出 Swing 桌面窗口
echo  - 服务就绪后自动在浏览器打开 http://127.0.0.1:8787/console/index.html
echo ============================================================
endlocal
