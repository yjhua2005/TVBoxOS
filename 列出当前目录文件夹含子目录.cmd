@echo off
for /d %%i in (*) do echo %%~fi >> 11.txt
echo 当前目录及子目录完整路径11.txt
pause
echo ===================== >> 11.txt
@echo off
for /r %%i in (*) do echo %%~fi >>11.txt
echo 当前目录文件及子目录文件完整路径11.txt
pause