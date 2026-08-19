::截屏后直接删除设备里的文件（干净）
adb connect 192.168.1.13
adb -s 192.168.1.13 shell screencap -p /sdcard/screen.png && adb -s 192.168.1.13 pull /sdcard/screen.png && adb -s 192.168.1.13 shell rm /sdcard/screen.png