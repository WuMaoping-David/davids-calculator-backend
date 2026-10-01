param([switch]$Test)
$ErrorActionPreference = 'Stop'
$backendRoot = $PSScriptRoot
$dependencies = @(
    @{ Name = 'h2-2.2.224.jar'; Url = 'https://repo.maven.apache.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar'; Sha256 = 'B9D8F19358ADA82A4F6EB5B174C6CFE320A375B5A9CB5A4FE456D623E6E55497' },
    @{ Name = 'gson-2.13.2.jar'; Url = 'https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar'; Sha256 = 'DD0CE1B55A3ED2080CB70F9C655850CDA86C206862310009DCB5E5C95265A5E0' }
)
New-Item -ItemType Directory -Force -Path "$backendRoot/lib", "$backendRoot/target/classes" | Out-Null
foreach ($dependency in $dependencies) {
    $destination = Join-Path "$backendRoot/lib" $dependency.Name
    if (-not (Test-Path -LiteralPath $destination)) {
        Write-Host "Downloading $($dependency.Name)..."
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -UseBasicParsing -Uri $dependency.Url -OutFile $destination
    }
    if ((Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash -ne $dependency.Sha256) {
        throw "Dependency checksum mismatch: $destination"
    }
}
$javac = (Get-Command javac -ErrorAction Stop).Source
$classpath = "$backendRoot/lib/h2-2.2.224.jar;$backendRoot/lib/gson-2.13.2.jar"
$sources = @(Get-ChildItem -LiteralPath "$backendRoot/src/main/java" -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
& $javac -encoding UTF-8 -source 8 -target 8 -cp $classpath -d "$backendRoot/target/classes" $sources
if ($LASTEXITCODE -ne 0) { throw 'Java backend compilation failed.' }
if ($Test) {
    New-Item -ItemType Directory -Force -Path "$backendRoot/target/test-classes" | Out-Null
    $tests = @(Get-ChildItem -LiteralPath "$backendRoot/src/test/java" -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)
    & $javac -encoding UTF-8 -source 8 -target 8 -cp "$backendRoot/target/classes;$classpath" -d "$backendRoot/target/test-classes" $tests
    if ($LASTEXITCODE -ne 0) { throw 'Java test compilation failed.' }
    foreach ($testClass in @('cn.calculator.ExpressionCalculatorTest', 'cn.calculator.HistoryRepositoryTest')) {
        & java '-Dfile.encoding=UTF-8' -cp "$backendRoot/target/test-classes;$backendRoot/target/classes;$classpath" $testClass
        if ($LASTEXITCODE -ne 0) { throw "Tests failed: $testClass" }
    }
}
Write-Host 'Backend compiled successfully.'
