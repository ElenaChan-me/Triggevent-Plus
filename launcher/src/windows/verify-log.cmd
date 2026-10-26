@echo off
rem ACT のログを再生して、コールアウトの一覧を書き出す。フォルダを渡すと中のログをまとめて確かめる。
rem 例: verify-log.cmd "%LOCALAPPDATA%\XIVToolHub\tools\act\FFXIVLogs" --pack "%LOCALAPPDATA%\XIVToolHub\tools\voicepack-mia"
setlocal
set "HERE=%~dp0"
"%HERE%jre\bin\java.exe" -cp "%HERE%userdata;%HERE%preload\*;%HERE%deps\*;%HERE%user\*;%HERE%${launcherJar}" gg.xp.xivsupport.gui.VerifyLog %*
