# Embedded HSQLDB

HSQLDB is the current embedded database for KriolOS POS. The app includes HSQLDB 2.7.2, recognizes `HSQL Database Engine` in its database session layer, and creates a file database without a separate database server. `AppConfig.defaultConfig()` sets the primary JDBC URL to `jdbc:hsqldb:file:` followed by `%APPDATA%\KriolOS\kriolopos.hsqldb`. The app's configuration file is `%APPDATA%\KriolOS\kriolos-opos.properties`.

On 2026-10-24, a fresh scratch HSQLDB file was tested with JDK 17 and the built app jars. `DBMigrator.execDBMigration()` applied all 177 Liquibase changesets. The database then closed and reopened with its `APPLICATIONS` table present. This verifies connection, initial schema migration, and file persistence. It does not establish that every sale, payment, printer, or upgrade path works; test those on a scratch installation before deploying changes.

## Development and installation

Use the default HSQLDB setting when launching a clean checkout or installing the MSI on another Windows PC. There is no HSQLDB service to install or start. The MSI bundles the driver and runtime; each Windows user gets configuration and database files under their own `%APPDATA%\KriolOS`. The installer does not transfer data from another machine. See [build and run instructions](installer.md).

For isolated experiments, use a separate Windows account or a separate configuration file via `-Dkriolos.config=C:\path\to\scratch.properties`, with its database URL directed at a scratch directory. Do not point a new checkout at a production or older uniCenta database until its compatibility and backup have been checked.

Back up the complete HSQLDB file set with the POS closed, including files with the `.properties`, `.script`, `.data`, `.log`, and `.backup` suffixes if present. Restore that set together to the same database base path. A live file copy can be inconsistent.
