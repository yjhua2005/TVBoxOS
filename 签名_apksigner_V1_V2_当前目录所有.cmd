@echo off
set "KEY_STORE=my-release-key.keystore"
set "KEY_PASS=123456"
set "ALIAS_PASS=123456"
set "APK_SIGNER=M:\Android\SDK\build-tools\30.0.3\apksigner.bat"

for %%f in (*.apk) do (
    if not "%%~nf"=="*_signed" (
        echo 正在签名: %%f
        call "%APK_SIGNER%" sign --ks %KEY_STORE% --ks-pass pass:%KEY_PASS% --key-pass pass:%ALIAS_PASS% --out "%%~nf_signed.apk" "%%f"
    )
)

echo 全部签名完成！
pause