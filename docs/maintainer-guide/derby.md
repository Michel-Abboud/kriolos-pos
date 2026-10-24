# Embedded Apache Derby status

**Status: not currently usable with the project's JDK 17 baseline.** The app declares `org.apache.derby:derby:10.17.1.0`, `Session` recognizes database product name `Apache Derby`, and `SessionDBDerby` implements dialect operations. But the local `derby-10.17.1.0.jar` and `derbyshared-10.17.1.0.jar` contain Java class-file version 63 (Java 19); a JDK 17 installer cannot load them. The Configuration > Database engine list only adds MariaDB, MySQL, and HSQLDB, while its old `Derby` branch is dormant. The default embedded database is HSQLDB in `%APPDATA%\KriolOS`, not Derby. A successful JDBC connection on a newer JDK would still not prove schema initialization, sale, printing, or upgrade compatibility.

Derby restoration is deferred because the existing HSQLDB option covers the embedded, serverless use case. See [HSQLDB for development and installs](hsqldb.md). No Derby database changes are needed for the current work.

## Restoration plan, if Derby is needed later

1. Add Apache Derby to the database selector and provide a local path URL such as `jdbc:derby:%APPDATA%\KriolOS\kriolopos-derby;create=true`. Resolve `%APPDATA%` to an absolute path in code; Java JDBC does not expand the percent syntax itself.
2. Replace the Derby dependency with a release whose class files support Java 17, or deliberately raise the app and installer JDK baseline together. Verify the packaged installer includes both the Derby engine and shared jars.
3. Create a fresh scratch Derby database and run the full `DBMigrator` Liquibase changelog; fix any Derby-specific SQL, data types, or seed-data issues in dedicated changesets. Never experiment on the uniCenta database from another machine.
4. Exercise login, product lookup, a sale and refund, cash close, receipt print, restart, backup, and restore. Add an integration test for a fresh database and one for an upgrade before presenting Derby as supported.

The plan above is only for a future Derby requirement; do not use the existing Java 19 Derby jars with the current Java 17 installer.
