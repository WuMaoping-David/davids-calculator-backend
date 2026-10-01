param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
if (-not $SkipBuild) { & "$PSScriptRoot/build.ps1" }
Push-Location $PSScriptRoot
try {
    & java '-Dfile.encoding=UTF-8' -cp "target/classes;lib/h2-2.2.224.jar;lib/gson-2.13.2.jar" cn.calculator.CalculatorApplication
    if ($LASTEXITCODE -ne 0) { throw 'Backend stopped with an error.' }
} finally { Pop-Location }
