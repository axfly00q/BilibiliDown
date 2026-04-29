@echo off
cd /d %~dp0
:: 优先使用随附的 JRE，否则使用系统 PATH 中的 Java
set Path=%~dp0minimal-bilibilidown-jre\bin;%Path%
set Path=%~dp0runtime\bin\;%Path%
:: headless=true  -> 不弹出 Swing 窗口
:: autoOpenBrowser=true (默认) -> 启动后自动在默认浏览器打开 Web 控制台
start javaw -Dfile.encoding=utf-8 ^
            -Dbilibili.headless=true ^
            -Dbilibili.prop.log=false ^
            -Dhttps.protocols=TLSv1.2 ^
            -jar launch.jar
