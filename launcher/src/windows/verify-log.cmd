@echo off
rem ACT のログを再生して、コールアウトの一覧を書き出す。フォルダを渡すと中のログをまとめて確かめる。例: verify-log.cmd Network_xxxx.log --pack voicepacks\mia
setlocal
set "HERE=%~dp0"
"%HERE%jre\bin\java.exe" -cp "%HERE%userdata;%HERE%deps\*;%HERE%user\*;%HERE%${launcherJar}" gg.xp.xivsupport.gui.VerifyLog %*
