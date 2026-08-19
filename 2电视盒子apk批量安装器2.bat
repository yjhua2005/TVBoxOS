@echo off
echo 安装前请保证小米电视\盒子处于联网状态
echo 保证小米电视\盒子与电脑在同一局域网内
echo 点击小米电视\盒子设置--关于--网络信息
echo 查看电视的IP地址（如：192.168.1.2）
echo ====================================
:start
:: 直接回车默认使用 192.168.1.13
set "ip=192.168.1.13"
set /p "ip=请输入小米电视\盒子IP地址(直接回车默认 192.168.1.13)："

adb kill-server
adb connect %ip% >%temp%\mitv_result
findstr "unable" %temp%\mitv_result >nul && (echo IP地址有误呦~~，请重新核对 && goto :start) || goto :end

:end
echo 连接成功，正在安装管理软件（如小米电视\盒子有安装提示，请点击“安装”）,如需批量安装请复制修改下一行代码！


COLOR 2f
ECHO. 将安装包放入本目录（可放置多个APK）
ECHO. 放置好后可继续
pause

FOR %%i IN (*.apk) DO ( 
:: 	ECHO 安装：%%i >> 操作记录.txt
        ECHO 安装：%%i
    ECHO 请您等待传输，并留意手机提示
 	adb -s 192.168.1.13 install "%%i"
    ECHO.
 	)
echo ====================================
echo 安装成功~~，请运行小米电视\盒子上面的应用
echo 根据网络信息修改
echo 感谢伟大的百度

pause