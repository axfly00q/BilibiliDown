#!/bin/bash
# =========================================================
# 用 jpackage 将 BilibiliDown 打包为 macOS 应用 (.dmg)
#   - 产出 dist/BilibiliDown-6.50-mac.dmg
#   - 用户双击 .dmg，将 BilibiliDown.app 拖入 /Applications 即可
#   - 自带 JRE，目标机器无需单独安装 Java
#
# 前提：
#   1) JDK 17+（含 jpackage）已在 PATH
#   2) Xcode Command Line Tools 已安装：xcode-select --install
# =========================================================

set -e
cd "$(dirname "$0")"

APP_NAME=BilibiliDown
APP_VERSION="${APP_VERSION:-6.50}"
APP_VENDOR=nicelee
DIST_INPUT="$(pwd)/dist-input"
DIST_OUT="$(pwd)/dist"

echo "[1/6] 清理旧产物 ..."
rm -rf "$DIST_INPUT" "$DIST_OUT" build build-launcher
mkdir -p "$DIST_INPUT" build build-launcher

echo "[2/6] 编译主程序 src/ ..."
find "$(pwd)/src" -name "*.java" \
  ! -path "*/test/junit/*" \
  > build/sources.txt
javac -d build -encoding UTF-8 -nowarn \
  -cp "libs/core-3.3.3.jar:libs/javax.mail-1.6.2.jar:libs/jaf-1.1.1-activation.jar" \
  "@build/sources.txt"
if [ $? -ne 0 ]; then echo "主程序编译失败"; exit 1; fi
cp -r src/resources/. build/resources/

echo "[3/6] 编译启动器 src-launcher/ ..."
find "$(pwd)/src-launcher" -name "*.java" > build-launcher/sources.txt
javac -d build-launcher -encoding UTF-8 -nowarn "@build-launcher/sources.txt"
if [ $? -ne 0 ]; then echo "启动器编译失败"; exit 1; fi

echo "[4/6] 打包 INeedBiliAV.jar / launch.jar ..."
(cd build        && jar cfe "$DIST_INPUT/INeedBiliAV.jar" nicelee.ui.FrameMain .)
(cd build-launcher && jar cfe "$DIST_INPUT/launch.jar"   nicelee.memory.App   .)

echo "[5/6] 复制依赖 (libs / config) ..."
cp -r libs/. "$DIST_INPUT/libs/"
mkdir -p "$DIST_INPUT/config"
[ -f release/config/app.config ] && cp release/config/app.config "$DIST_INPUT/config/"
# 如有 macOS 版 ffmpeg，取消注释下一行并填写路径：
# cp /path/to/ffmpeg-mac "$DIST_INPUT/ffmpeg"

echo "[6/6] 生成 .dmg 安装包 ..."
# 如需自定义图标（.icns 格式），追加参数：--icon /path/to/icon.icns
# 如需生成 .pkg 安装包，将 --type dmg 改为 --type pkg
jpackage \
  --type dmg \
  --name   "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "$APP_VENDOR" \
  --input  "$DIST_INPUT" \
  --main-jar launch.jar \
  --main-class nicelee.memory.App \
  --java-options "-Dfile.encoding=utf-8" \
  --java-options "-Dbilibili.headless=true" \
  --java-options "-Dbilibili.headless.autoOpenBrowser=true" \
  --java-options "-Dhttps.protocols=TLSv1.2" \
  --dest   "$DIST_OUT" \
  --add-modules java.base,java.desktop,java.naming,java.net.http,java.logging,java.sql,java.security.jgss,jdk.crypto.ec,jdk.unsupported

if [ $? -ne 0 ]; then echo "jpackage 失败"; exit 1; fi

echo ""
echo "============================================================"
echo " 完成！"
echo "   安装包 = $DIST_OUT/${APP_NAME}-${APP_VERSION}.dmg"
echo ""
echo " 使用方法（最终用户）"
echo "   1) 双击 .dmg 文件，挂载磁盘镜像"
echo "   2) 将 ${APP_NAME}.app 拖入 /Applications"
echo "   3) 启动后浏览器自动弹出 http://127.0.0.1:8787/console/index.html"
echo "      (首次启动约 12 秒，不会出现 Swing 桌面窗口)"
echo "============================================================"
