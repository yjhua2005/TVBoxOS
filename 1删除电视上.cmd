adb connect 192.168.1.13
adb shell pm list packages | findstr tvbox

adb -s 192.168.1.13 shell pm clear com.github.tvbox.osc.jun
adb  -s 192.168.1.13  uninstall com.github.tvbox.osc.jun
pause