@echo off
chcp 65001 >nul
cls
echo ========================================
echo   TVBoxOS Auto Build Script
echo ========================================

:: 如需指定 JDK，请取消注释并设置路径
:: set JAVA_HOME=M:\jdk11
:: set PATH=%JAVA_HOME%\bin;%PATH%

echo.
echo [INFO] JDK detected
echo [INFO] 准备构建 APK
echo.

:: 如果命令行传递了任务参数（例如 build.bat assembleJava32Release），则直接构建
if not "%1"=="" (
    set TASK=%1
    goto :buildTask
)

:: 否则显示菜单
:menu
set "choice="  :: 关键：每次进入菜单强制清空choice变量
echo 可用的构建任务：
echo   1. assembleJava32Release
echo   2. assembleJava64Release
echo   3. assemblePythonRelease
echo   4. assemblePython32Release
echo   5. assemblePython64Release
echo   6. assembleRelease
echo   7. 全部构建(All)
echo   0. 退出
echo.
set /p choice="请输入选项 (0-7)，直接回车默认1: "
:: ========== 新增逻辑：回车空输入默认选1 ==========
if not defined choice (
    echo 未输入选项，默认执行 1.assembleJava32Release
    set choice=1
)
:: =================================================
if "%choice%"=="1" set TASK=assembleJava32Release & goto :buildTask
if "%choice%"=="2" set TASK=assembleJava64Release & goto :buildTask
if "%choice%"=="3" set TASK=assemblePythonRelease & goto :buildTask
if "%choice%"=="4" set TASK=assemblePython32Release & goto :buildTask
if "%choice%"=="5" set TASK=assemblePython64Release & goto :buildTask
if "%choice%"=="6" set TASK=assembleRelease & goto :buildTask
if "%choice%"=="7" goto :buildAll
if "%choice%"=="0" exit /b

echo 无效选项，请重新选择。
goto :menu

:buildTask
echo.
echo [INFO] 正在构建 %TASK% ...
call gradlew.bat app:%TASK%
if errorlevel 1 (
    echo.
    echo [ERROR] 构建 %TASK% 失败！
    pause
    exit /b 1
) else (
    echo.
    echo ========================================
    echo [SUCCESS] %TASK% 构建成功！
    echo APK 路径：app\build\outputs\apk\release\
    echo ========================================
)
pause
exit /b

:buildAll
echo.
echo [INFO] 开始构建所有任务...
set TASKS=assembleJava32Release assembleJava64Release assemblePythonRelease assemblePython32Release assemblePython64Release assembleRelease
for %%t in (%TASKS%) do (
    echo.
    echo ========================================
    echo [BUILD] %%t
    echo ========================================
    call gradlew.bat app:%%t
    if errorlevel 1 (
        echo [ERROR] 构建 %%t 失败！
        pause
        exit /b 1
    )
)
echo.
echo ========================================
echo [SUCCESS] 所有任务构建完成！
echo ========================================
pause
exit /b