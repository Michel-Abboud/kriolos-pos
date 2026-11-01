# Build and run on Windows

The source/API baseline is Java 17 (`maven.compiler.release=17`); Maven accepts JDK 17 or newer. The Windows app image bundles the runtime from the JDK selected by `-JdkHome` or `JAVA_HOME`, so an image made with JDK 21 runs on its own Java 21 runtime. SQLite and HSQLDB startup, migrations and the headless app regression suite are tested on JDK 17. Some optional database drivers require newer Java; the app explicitly loads the selected embedded driver to avoid depending on discovery of unrelated drivers.

## Run a development checkout

Install JDK 17 and use a local database or the default embedded SQLite database. HSQLDB is also supported. The easiest edit-compile-run loop is:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\scripts\run-dev.cmd
```

Close the POS window after a change and run the same command again. It compiles the changed classes and launches directly from the Maven `target\classes` output; it does not create an installer. On its first run, it prepares the dependency jars. To run against an isolated configuration, pass `-ConfigFile C:\path\to\scratch.properties`.

For a packaged-jar check, use the following commands instead:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\mvnw.cmd -pl kriolos-opos-app -am -DskipTests package
& "$env:JAVA_HOME\bin\java.exe" --add-opens=java.desktop/java.beans=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED -jar .\kriolos-opos-app\target\kriolos-pos.jar
```

The first launch writes `%APPDATA%\KriolOS\kriolos-opos.properties`. The current default is `jdbc:sqlite:` followed by `%APPDATA%\KriolOS\kriolopos.db`; no database server is needed. The Configuration screen can change it. A missing embedded database is created on connection, then Liquibase initializes its tables, resources and default users. Deleting a database does not reset the properties file or restore old data. Use `-ConfigFile C:\path\to\scratch.properties` with the dev script, or `-Dpos.config.file=C:\path\to\scratch.properties` before `-jar`, to isolate experiments. A GUI session is required.

SQLite and HSQLDB both support automatic creation and initialization. `EmbeddedDatabaseStartupTest` verifies a missing database, migration and reopening in separate Java processes without JDBC driver flags. Startup explicitly loads the selected embedded driver before the login connection test and Session connection, avoiding optional-driver discovery failures. See [HSQLDB configuration and data location](hsqldb.md).

## Build an installable MSI

Install JDK 17 and **WiX Toolset 3.14** (the `candle.exe` and `light.exe` command-line tools). Its usual directory is `C:\Program Files (x86)\WiX Toolset v3.14\bin`. Add that directory to the user PATH and reopen PowerShell. The script uses JDK 17's `jpackage`, which expects these WiX 3 tools for MSI creation. Verify `candle.exe -?` and `light.exe -?`, then run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\scripts\build-windows-installer.cmd
```

The script automatically detects WiX in that standard directory when the current PowerShell window has not yet received the updated PATH.

The MSI appears at `dist\windows\<timestamp>\kriolosPOS-1.0.0.msi`. The command wrapper runs the PowerShell script under a process-local execution policy, so it also works on PCs where direct `.ps1` execution is disabled. The script builds the app and dependencies, stages the jars, and invokes JDK `jpackage` to bundle a Java runtime and make a Start Menu shortcut. To repackage an already built checkout, pass `-SkipBuild`. Install the MSI on the other Windows PC; a separate Java installation is not needed there. User configuration and embedded databases live under that PC's `%APPDATA%\KriolOS` directory and are not copied into the installer. Transfer or restore data separately if needed.

The repo also has a `kriolos-opos-jpackage` Maven module and release pipeline. The script is a direct, repeatable developer workflow that uses the app module's current `target/kriolos-pos.jar` and `target/lib` output.

For a portable app image without WiX, run `.\scripts\build-windows-installer.cmd -ImageOnly`. Launch `dist\windows\<timestamp>\kriolosPOS\kriolosPOS.exe`. The complete command reference, permanent PATH command, current-window workaround, and WiX checks are in [`scripts/README.md`](../../scripts/README.md).

Each timestamped folder is a fixed snapshot of that build. Compiling source or running `run-dev.cmd` does not update an older `dist\windows\...\kriolosPOS.exe` or an installed copy. After a fix, rebuild without `-SkipBuild` and launch the EXE from the newest output folder, or install the newly generated MSI. Database files remain in `%APPDATA%\KriolOS`; keep them when replacing the app. A missing-driver error from an old EXE is not fixed by deleting the database.
