@echo off
REM 本机开发用 Redis 5（端口 6380，支持 Stream）。双击启动，窗口保持即服务运行。
REM 脚本位于 deploy\redis 目录，%~dp0 即脚本所在目录，项目整体移动后无需改路径。
title Redis 6380
"%~dp0redis-server.exe" "%~dp0redis.windows.conf"
pause
