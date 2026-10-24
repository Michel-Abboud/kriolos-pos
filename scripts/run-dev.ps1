param(
    [switch]$SkipCompile,
    [string]$ConfigFile
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    throw 'Set JAVA_HOME to a JDK 17 installation before running the development app.'
}
$java = Join-Path $javaHome 'bin\java.exe'
if (-not (Test-Path -LiteralPath $java)) {
    throw "java.exe was not found under JAVA_HOME: $javaHome"
}

$appTarget = Join-Path $root 'kriolos-opos-app\target'
$runtimeLib = Join-Path $appTarget 'lib'
if (-not $SkipCompile) {
    Push-Location $root
    try {
        $hasRuntimeJars = (Test-Path -LiteralPath $runtimeLib) -and
            (@(Get-ChildItem -LiteralPath $runtimeLib -Filter '*.jar' -File -ErrorAction SilentlyContinue).Count -gt 0)
        $compileGoal = if ($hasRuntimeJars) { 'compile' } else { 'package' }
        & (Join-Path $root 'mvnw.cmd') -pl kriolos-opos-app -am $compileGoal -DskipTests -B --no-transfer-progress
        if ($LASTEXITCODE -ne 0) { throw "Maven $compileGoal failed with exit code $LASTEXITCODE" }
    }
    finally {
        Pop-Location
    }
}

$classes = @(Get-ChildItem -LiteralPath $root -Directory | ForEach-Object {
    $classesPath = Join-Path $_.FullName 'target\classes'
    if (Test-Path -LiteralPath $classesPath) { $classesPath }
})
$classes += Join-Path $appTarget 'classes'
$jars = @(Get-ChildItem -LiteralPath $runtimeLib -Filter '*.jar' -File -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
if ($classes.Count -eq 0 -or $jars.Count -eq 0) {
    throw 'Runtime classes or dependency jars are missing. Run this script without -SkipCompile first.'
}

$classpath = (($classes + $jars) -join [IO.Path]::PathSeparator)
$javaArgs = @('-Djdbc.drivers=org.hsqldb.jdbc.JDBCDriver',
    '--add-opens=java.desktop/java.beans=ALL-UNNAMED',
    '--add-opens=java.base/java.lang=ALL-UNNAMED',
    '--add-opens=java.base/java.io=ALL-UNNAMED',
    '--add-opens=java.base/java.util=ALL-UNNAMED')
if ($ConfigFile) {
    $javaArgs += "-Dpos.config.file=$([IO.Path]::GetFullPath($ConfigFile))"
}
$javaArgs += @('-cp', $classpath, 'com.openbravo.pos.forms.BootstrapPOS')
& $java @javaArgs
exit $LASTEXITCODE
