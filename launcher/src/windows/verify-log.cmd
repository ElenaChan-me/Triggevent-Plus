@echo off
rem ACT のログを再生して、コールアウトの一覧をログと同じフォルダに書き出す。例: verify-log.cmd Network_xxxx.log --pack voicepacks\mia
setlocal
set "HERE=%~dp0"
"%HERE%jre\bin\java.exe" -cp "%HERE%userdata;%HERE%deps\*;%HERE%user\*;%HERE%${launcherJar}" gg.xp.xivsupport.gui.VerifyLog %*
