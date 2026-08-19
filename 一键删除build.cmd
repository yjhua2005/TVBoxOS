taskkill /f /im java.exe 2>nul
taskkill /f /im javaw.exe 2>nul

del /s /q     "D:\TVBoxOS\app\proguardMapping.txt" 
rd /s /q      "D:\TVBoxOS\.gradle" 
rd /s /q      "D:\TVBoxOS\app\build" 
::rd /s /q     "D:\TVBoxOS\drive_module\build" 
rd /s /q      "D:\TVBoxOS\player\build" 
rd /s /q      "D:\TVBoxOS\pyramid\build" 
rd /s /q      "D:\TVBoxOS\quickjs\build"
::rd /s /q    "D:\TVBoxOS\tvtoolbar\build" 


pause