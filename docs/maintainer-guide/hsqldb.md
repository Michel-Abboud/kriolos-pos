# Embedded HSQLDB

HSQLDB is a supported embedded alternative to the current default, SQLite. The app includes HSQLDB 2.7.4, recognizes `HSQL Database Engine` in its database session layer, and creates a file database without a separate server. To use it, set the database URL to `jdbc:hsqldb:file:` followed by `%APPDATA%\KriolOS\kriolopos.hsqldb` (expand `%APPDATA%` to its absolute path), with username `SA` and an empty password. The app's configuration file is `%APPDATA%\KriolOS\kriolos-opos.properties`. The current SQLite default is `%APPDATA%\KriolOS\kriolopos.db`; older versions of this guide incorrectly described HSQLDB as the current default.

On 2026-10-24, a fresh scratch HSQLDB file was tested with JDK 17 and the built app jars. `DBMigrator.execDBMigration()` applied all 177 Liquibase changesets. The database then closed and reopened with its `APPLICATIONS` table present. This verifies connection, initial schema migration, and file persistence. It does not establish that every sale, payment, printer, or upgrade path works; test those on a scratch installation before deploying changes.

## Development and installation

Select HSQLDB in Configuration when preferred; there is no HSQLDB service to install or start. Both embedded engines create a missing database on connection, then the POS initializes it using Liquibase. The MSI bundles the drivers and runtime; each Windows user gets configuration and database files under their own `%APPDATA%\KriolOS`. The installer does not transfer data from another machine. See [build and run instructions](installer.md).

For isolated experiments, use a separate Windows account or a separate configuration file via `-Dpos.config.file=C:\path\to\scratch.properties`, with its database URL directed at a scratch directory. Do not point a new checkout at a production or older uniCenta database until its compatibility and backup have been checked.

Back up the complete HSQLDB file set with the POS closed, including files with the `.properties`, `.script`, `.data`, `.log`, and `.backup` suffixes if present. Restore that set together to the same database base path. A live file copy can be inconsistent.
