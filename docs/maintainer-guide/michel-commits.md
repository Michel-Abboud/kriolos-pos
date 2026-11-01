# Michel Abboud changes on main

Reviewed on 2026-11-01, through `f75ee66d` (formerly `66d6516e`, amended to describe its changes). The six consecutive commits after `72c7e222` use the author email `michel.ziad.abboud@gmail.com` with the names `ABBOUD MICHEL` and `michel`. This records how the imported changes fit the current source tree; it is not a claim that every affected workflow was manually exercised.

| Commit | Behavior and integration |
| --- | --- |
| `31b4952c` | Adds currency configuration, decimal display conversion, receipt getters, classic `TicketSummaryPane` and both modern totals; adds maintainer docs, development runner and Windows packaging scripts. Accounting/payment totals stay in the main currency. |
| `a886dd34` | Adds EAN-13 generation, exact active-catalog collision checking and product-editor controls; repairs CSV category/supplier mapping, default handling, and scanner stock import behavior. Adds HSQLDB import integration tests. |
| `546841bf` | Restores auto-commit after Liquibase so maintenance writes survive shutdown; improves maintenance scroll/navigation layout and remembers the activated database. Adds category restart coverage. |
| `736bced9` | Adds mapped category CSV import, parent/name resolution, validation before writes and transactional rollback. Installs menu entries and permissions for category import, including existing stored menus. Adds sales/catalog layout controls. |
| `38169eed` | Adapts the imported changes to main's service APIs (`CatalogService` and `SupplierService`) and adds regression coverage for the converted classic total. Main extracted responsibilities from the older `DataLogicPIM`/`DataLogicSystem` layout. |
| `f75ee66d` | Fixes repeated mounting of cached maintenance editors; uses a full-day default for sales reports; repairs SQLite epoch-millisecond dates with a database-specific migration and consistent timestamp binding; leaves transaction commits to the enclosing transaction; reuses one Session connection. Also improves Enter actions, receipt-print defaults across layouts, category visibility and barcode lookup, status/catalog toggles, and audited sale/line deletion including suspended-sale removal. |

## Consequences for future changes

- Use typed service ports from `AppView.getBean(Class)` when available. `SystemService` extends both `SecurityService` and `ResourceService`; database activation changes which store these services access.
- Login exists before a database connects. Editable login resources must load after successful activation, use the active store, and refresh on logout; bundled defaults must work without a database.
- Resource edits are database writes through `ResourcesPanel` / `ResourcesView` and `DefaultSaveProvider`. Avoid a separate branding configuration mechanism or overwriting existing resources on migration.
- Keep maintenance components mounted once, preserve the current transaction ownership rules, and use scratch databases for migrations and persistence tests.
- Current classic sale totals live in `TicketSummaryPane`; the previous release-branch `JPanelTicket` footer implementation is no longer the owner of that UI.

## Verification with the login branding change

On JDK 17, 428 regression tests passed in headless mode, including fresh HSQLDB/SQLite login resource migrations, image/text saves, preservation across upgrade/restart, and Swing content refresh. The seven `PosUIModalTest` tests require a display and passed separately in the normal graphical run. A single graphical full-suite run waits for the CSV import completion dialog, while a fully headless run cannot create the modal tests' windows. Reproduce the automated portion from the root with:

```powershell
.\mvnw.cmd -pl kriolos-opos-app -am "-Djava.awt.headless=true" "-Dtest=*,!PosUIModalTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Djdbc.drivers=org.hsqldb.jdbc.JDBCDriver,org.sqlite.JDBC" test
```

Explicit JDBC driver registration avoids another bundled driver's service-discovery incompatibility with JDK 17. Test output is under each module's `target/surefire-reports`; scratch databases use JUnit temporary directories.
