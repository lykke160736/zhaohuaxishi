param(
  [ValidateSet('build', 'mcp', 'assistant')]
  [string]$Service = 'build'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
# Maven 和 IDEA 应使用同一套 JDK 17 或更新版本。
$javaPath = (Get-Command java -ErrorAction Stop).Source
$env:JAVA_HOME = Split-Path (Split-Path $javaPath -Parent) -Parent
$localConfig = Join-Path $PSScriptRoot 'local.ps1'
if (Test-Path -LiteralPath $localConfig) { . $localConfig }
Set-Location $projectRoot
if ($Service -eq 'build') {
  & mvn -B -ntp "-Dmaven.wagon.rto=15000" -s (Join-Path $PSScriptRoot 'maven-settings.xml') -f (Join-Path $PSScriptRoot 'pom.xml') "-Dmaven.repo.local=$projectRoot\.maven-cache" package
  exit $LASTEXITCODE
}
$module = if ($Service -eq 'mcp') { 'business-mcp' } else { 'assistant' }
$jar = Join-Path $PSScriptRoot "$module\target\$module-1.0.0.jar"
if (!(Test-Path -LiteralPath $jar)) { throw '请先运行：.\ai-services\start.ps1 build' }
& "$env:JAVA_HOME\bin\java.exe" -jar $jar
exit $LASTEXITCODE
