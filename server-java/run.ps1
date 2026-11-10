$ErrorActionPreference = "Stop"
$projectRoot = Split-Path $PSScriptRoot -Parent

# Build/run with JDK 17 without touching the user's global JAVA_HOME.
$javaHome = $env:ZHX_JAVA_HOME
if (-not $javaHome) {
    $javaExe = (Get-Command java).Source
    $javaHome = Split-Path (Split-Path $javaExe)
}
# Java writes version information to stderr; Windows PowerShell treats it as an error.
$javaInfo = New-Object System.Diagnostics.ProcessStartInfo
$javaInfo.FileName = "$javaHome\bin\java.exe"
$javaInfo.Arguments = '-version'
$javaInfo.UseShellExecute = $false
$javaInfo.CreateNoWindow = $true
$javaInfo.RedirectStandardError = $true
$javaProcess = [System.Diagnostics.Process]::Start($javaInfo)
$version = $javaProcess.StandardError.ReadToEnd()
$javaProcess.WaitForExit()
$javaProcess.Dispose()
if ($version -notmatch '17\.') {
    Write-Error "需要 JDK 17（当前 java 是 $version）。可设置 ZHX_JAVA_HOME 指向 JDK 17 后重试。"
    exit 1
}
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"

# 低内存环境也可启动；已有 MAVEN_OPTS 时不覆盖。
if (-not $env:MAVEN_OPTS) {
    $env:MAVEN_OPTS = "-Xmx320m -Xms32m -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -XX:CICompilerCount=2"
}

$mvn = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvn) {
    Write-Error "未找到 Maven（mvn）。"
    exit 1
}

& $mvn.Source --file "$PSScriptRoot\pom.xml" spring-boot:run "-Dspring-boot.run.workingDirectory=$projectRoot" "-Dspring-boot.run.jvmArguments=-Xmx384m -Xss512k -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -XX:CICompilerCount=2" @args
