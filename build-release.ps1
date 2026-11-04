param(
    [string]$Jdk = 'C:\Program Files\Java\jdk-17.0.5',
    [string]$SigningDirectory = 'D:\Github\android-build-tools\signing'
)
$ErrorActionPreference='Stop'
$env:JAVA_HOME=$Jdk
# JDK 17 Gradle workers can misread argument paths under a non-ASCII Windows
# profile. The development machine keeps an ASCII junction to the same cache.
if (Test-Path -LiteralPath 'D:\Github\android-build-tools\gradle-cache') {
    $env:GRADLE_USER_HOME='D:\Github\android-build-tools\gradle-cache'
}
$env:POCKET_KEYSTORE=Join-Path $SigningDirectory 'pocket-release.jks'
$passwordPath=Join-Path $SigningDirectory 'pocket-password.txt'
if (!(Test-Path -LiteralPath $env:POCKET_KEYSTORE) -or !(Test-Path -LiteralPath $passwordPath)) {
    throw '未找到签名文件。请先配置自己的发布签名，详见 README。不要提交私钥。'
}
$env:POCKET_KEYSTORE_PASSWORD=[System.IO.File]::ReadAllText($passwordPath).Trim()
try {
    & "$PSScriptRoot\gradlew.bat" --no-daemon testDebugUnitTest assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Gradle 构建失败：$LASTEXITCODE" }
    Write-Output "APK：$PSScriptRoot\app\build\outputs\apk\release\app-release.apk"
} finally {
    Remove-Item Env:\POCKET_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
}
