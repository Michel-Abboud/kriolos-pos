# Generate Windows builds in `dist\windows`

Run these commands from the repository root in PowerShell. Install these prerequisites first:

- JDK 17, including `jpackage.exe`.
- WiX Toolset **3.x** for MSI builds. JDK 17 `jpackage` needs WiX 3's `candle.exe` and `light.exe`; WiX 4 or newer alone is not sufficient.

The normal WiX 3.14 installation directory is `C:\Program Files (x86)\WiX Toolset v3.14\bin`. Add it permanently to your user PATH from PowerShell without replacing the existing PATH:

```powershell
$wixBin = 'C:\Program Files (x86)\WiX Toolset v3.14\bin'
$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
if (($userPath -split ';') -notcontains $wixBin) {
    [Environment]::SetEnvironmentVariable('Path', "$userPath;$wixBin", 'User')
}
```

Close and reopen PowerShell after changing the user PATH. Then set JDK 17 for the new window and verify every required command:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

java --version
jpackage --version
candle.exe -?
light.exe -?
```

The verified WiX version for this workflow is `3.14.1.8722`. If WiX was installed elsewhere, locate it with `Get-ChildItem 'C:\Program Files (x86)' -Directory -Filter 'WiX*'` and use its `bin` directory. JDK 17's `jpackage` does not use WiX 4's `wix.exe` in this workflow.

To build the application and create a new MSI, run:

```powershell
.\scripts\build-windows-installer.cmd
```

The build script also detects `C:\Program Files (x86)\WiX Toolset v3.14\bin` automatically. This means the command works from a PowerShell window opened before the PATH was updated, provided WiX is installed in that standard directory.

The output is a new timestamped directory:

```text
dist\windows\yyyyMMdd-HHmmss\kriolosPOS-1.0.0.msi
```

Run the command again after changing code to compile it and produce a fresh installer. The MSI includes its own Java runtime and HSQLDB driver. The installed program stores that Windows user's configuration and embedded database under `%APPDATA%\KriolOS`; those files are not embedded in the MSI.

If `candle.exe` is still reported as unknown immediately after editing PATH, either reopen PowerShell or enable WiX in the current window with:

```powershell
$env:Path = "C:\Program Files (x86)\WiX Toolset v3.14\bin;$env:Path"
```

If the application was already built and only the packaging step needs to be repeated, use:

```powershell
.\scripts\build-windows-installer.cmd -SkipBuild
```

## Portable test build

To generate a portable application directory without requiring WiX, run:

```powershell
.\scripts\build-windows-installer.cmd -ImageOnly
```

Launch the result from:

```text
dist\windows\yyyyMMdd-HHmmss\kriolosPOS\kriolosPOS.exe
```

To repackage an existing compile as a portable directory, combine both options:

```powershell
.\scripts\build-windows-installer.cmd -SkipBuild -ImageOnly
```

For development launch instructions and database notes, see [the maintainer guide](../docs/maintainer-guide/installer.md).

## Fast UI development loop

You do not need an MSI or a new `jpackage` app image after every source change. Set `JAVA_HOME` to JDK 17 and run this from the repository root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\scripts\run-dev.cmd
```

The first run packages the dependencies needed for the local runtime. Later runs only compile changed Java classes and launch `BootstrapPOS` directly from the reactor's `target\classes` directories. Edit a file, close the running POS window, and run the same command again. The script explicitly registers the bundled HSQLDB JDBC driver and uses the default embedded database. Pass `-ConfigFile C:\path\to\scratch.properties` when you want an isolated database configuration.

Java class changes are loaded when the process starts, so a restart is expected. An installer is needed only when testing installation or distributing the app. The POM's optional HotSwap profile is not required by this workflow.
