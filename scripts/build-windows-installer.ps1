param(
    [string]$JdkHome = $env:JAVA_HOME,
    [switch]$SkipBuild,
    [switch]$ImageOnly
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (-not $JdkHome) {
    $jpackageOnPath = (Get-Command jpackage.exe -ErrorAction Stop).Source
    $JdkHome = Split-Path (Split-Path $jpackageOnPath -Parent) -Parent
}
if (-not (Test-Path -LiteralPath (Join-Path $JdkHome 'bin\jpackage.exe'))) {
    throw "jpackage.exe was not found under JDK home: $JdkHome"
}
$env:JAVA_HOME = (Resolve-Path $JdkHome).Path
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + [IO.Path]::PathSeparator + $env:PATH
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
$java = (Get-Command java.exe -ErrorAction Stop).Source
$javaVersion = (& $java --version | Select-Object -First 1).ToString()
if ($javaVersion -notmatch '^java (17|18|19|2[0-9])\.') {
    throw "JDK 17 or newer is required. Found: $javaVersion"
}
if (-not $ImageOnly -and ((-not (Get-Command candle.exe -ErrorAction SilentlyContinue)) -or
    (-not (Get-Command light.exe -ErrorAction SilentlyContinue)))) {
    throw 'WiX Toolset 3 candle.exe and light.exe must be on PATH to create an MSI with JDK 17 jpackage.'
}

Push-Location $root
try {
    if (-not $SkipBuild) {
        & (Join-Path $root 'mvnw.cmd') -pl kriolos-opos-app -am -DskipTests package -B --no-transfer-progress
        if ($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE" }
    }

    $appTarget = Join-Path $root 'kriolos-opos-app\target'
    $appJar = Join-Path $appTarget 'kriolos-pos.jar'
    $lib = Join-Path $appTarget 'lib'
    if (-not (Test-Path $appJar) -or -not (Test-Path $lib)) {
        throw 'Build output is missing. Run this script without -SkipBuild first.'
    }

    $staging = Join-Path $appTarget ('installer-input-' + [guid]::NewGuid().ToString('N'))
    $targetFull = [IO.Path]::GetFullPath($appTarget).TrimEnd('\') + '\'
    $stagingFull = [IO.Path]::GetFullPath($staging)
    if (-not $stagingFull.StartsWith($targetFull, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Installer staging path is outside the build target.'
    }
    New-Item -ItemType Directory -Path $staging | Out-Null
    try {
        Copy-Item -LiteralPath $appJar -Destination $staging
        $stagedLib = Join-Path $staging 'lib'
        New-Item -ItemType Directory -Path $stagedLib | Out-Null
        Copy-Item -Path (Join-Path $lib '*.jar') -Destination $stagedLib
        if (-not (Get-ChildItem -LiteralPath $stagedLib -Filter '*.jar')) {
            throw 'No runtime dependencies were staged.'
        }
        $output = Join-Path $root ('dist\windows\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
        New-Item -ItemType Directory -Path $output -Force | Out-Null
        $packageType = if ($ImageOnly) { 'app-image' } else { 'msi' }
        $packageArgs = @('--type', $packageType, '--name', 'kriolosPOS',
            '--app-version', '1.0.0', '--input', $staging, '--dest', $output,
            '--main-jar', 'kriolos-pos.jar', '--main-class', 'com.openbravo.pos.forms.BootstrapPOS',
            '--icon', (Join-Path $root 'kriolos-opos-jpackage\icons\app_icon.ico'),
            '--vendor', 'KriolOS', '--java-options', '-Dfile.encoding=UTF-8',
            '--java-options', '--add-opens=java.desktop/java.beans=ALL-UNNAMED',
            '--java-options', '--add-opens=java.base/java.lang=ALL-UNNAMED')
        if (-not $ImageOnly) { $packageArgs += @('--win-menu', '--win-shortcut', '--win-dir-chooser') }
        & $jpackage @packageArgs
        if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }
        if ($ImageOnly) {
            $image = Join-Path $output 'kriolosPOS'
            if (-not (Test-Path $image)) { throw 'jpackage reported success but produced no app image.' }
            Get-Item -LiteralPath $image | Select-Object FullName
        } else {
            $installer = Get-ChildItem -LiteralPath $output -Filter '*.msi'
            if (-not $installer) { throw 'jpackage reported success but produced no MSI.' }
            $installer | Select-Object FullName, Length
        }
    }
    finally {
        Remove-Item -LiteralPath $stagingFull -Recurse -Force
    }
}
finally {
    Pop-Location
}
