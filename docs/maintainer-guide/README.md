# KriolOS POS maintainer guide

This checkout is a Java 17, Maven, Swing continuation of uniCenta oPOS. If you already know uniCenta's screens, start with the code map below. The existing Antora documentation in `docs/modules/guide-devel` describes concepts and schema; this guide records where current behavior lives in this checkout.

## Code map

| Concern | Current location |
| --- | --- |
| Parent build, module versions, Java baseline | `pom.xml` |
| Startup and window lifecycle | `kriolos-opos-app/.../forms/BootstrapPOS.java`, `ApplicationShell.java` |
| Configuration file and defaults | `kriolos-opos-domain/.../config/AppConfig.java` |
| Configuration screen | `kriolos-opos-app/.../config/JPanelConfiguration.java`; each tab implements `PanelConfig` |
| Sale editing and classic ticket panel | `kriolos-opos-app/.../sales/JPanelTicket.java`, `JTicketLines.java` |
| Modern sale layouts | `kriolos-opos-app/.../sales/modern/` and `kriolos-opos-ui-api` |
| Ticket, lines, totals | `kriolos-opos-domain/.../ticket/TicketInfo.java`, `TicketLineInfo.java` |
| Database access | `kriolos-opos-base/.../data/loader/Session.java`; app `data/DBMigrator.java` |
| Schema and seed data | `kriolos-opos-app/src/main/resources/pos_liquidbase/` |
| Editable receipt and ticket templates | `kriolos-opos-app/src/main/resources/com/openbravo/pos/templates/`; runtime copies live in the `resources` database table |
| Hardware/printers | `kriolos-opos-hardware`, app `printer` package |
| Windows installer | `scripts/build-windows-installer.ps1`; existing jpackage module in `kriolos-opos-jpackage` |

The root `README.adoc` describes the fork history: KriolOS descends from uniCenta oPOS 4.5, which descended from Openbravo POS. Names such as `com.openbravo.*` and `Printer.Ticket` remain from that lineage. Do not assume current database behavior from an older uniCenta installation: this repo has newer configuration, Liquibase migrations, and sales layouts.

## Typical change path

1. Read `docs/modules/guide-devel/pages/develop-ticket.adoc` and `resources-overview.adoc` for the existing concepts.
2. Locate the active view and model with `rg`. `JPanelTicket` owns the classic scanned-item list and calls `TicketInfo` for totals. Modern layouts may have their own total display.
3. Keep financial calculations in the domain module; the Swing views format and display results. Do not change `TicketInfo.getTotal()` merely to add display conversions: payment and accounting code uses it.
4. Configuration is stored per Windows user in `%APPDATA%\KriolOS\kriolos-opos.properties`. `JPanelConfiguration` loads each `PanelConfig`, then saves all tabs. Changes need a restart.
5. Receipt templates use Velocity expressions. `JPanelTicket.printTicket` supplies `ticket`, `taxes`, and other variables to the printer parser. The default XML files are initial resources, so editing a source template may not replace a copy already stored in an existing database. Edit the corresponding resource in the running POS when testing an existing installation.
6. Preserve NetBeans `GEN-BEGIN`/`GEN-END` sections and the matching `.form` files. New dynamic components can be wired after `initComponents()`.
7. Build from the root with `./mvnw.cmd -pl kriolos-opos-app -am test` and test the relevant layout and receipt on a scratch database.

## Dual currency added here

Open Configuration > Currencies. Enter three values: main ISO 4217 code, secondary ISO 4217 code, and the number of secondary units for one main unit. For example, `USD`, `LBP`, `90000`. The secondary code may be blank to disable conversion. The sale views show separate side by side totals labeled from the configured codes, such as `USD Total` and `LBP Total`. The conversion uses decimal arithmetic and the secondary currency's fraction digits. Existing prices, taxes, payments, cash totals, and saved tickets remain in the main currency; the secondary figure is informational and uses the current configured rate when displayed or reprinted.

In a `Printer.Ticket` resource, use `${ticket.printPrimaryTotal()}` and `${ticket.printSecondaryTotal()}`. The latter is empty when disabled. The older `${ticket.printTotal()}` remains valid and retains its original formatting. For example:

```xml
<line><text align="left" length="12">Total</text><text align="right" length="20">${ticket.printPrimaryTotal()}</text></line>
<line><text align="left" length="12">Converted</text><text align="right" length="20">${ticket.printSecondaryTotal()}</text></line>
```

This is a display conversion. It does not lock a historical exchange rate into the sale; a reprint after a rate change uses the new rate.

## Sales controls and editable resources

Detailed catalog cards render a product's saved **Button HTML** directly; plain product/category names are escaped before being wrapped in HTML. Placeholder initials are extracted from the rendered text, with entities and tags removed, using Unicode characters. Do not HTML-escape a complete saved `<html>` document or derive initials from its raw markup: that exposes tags and produces initials such as `<>`. `ProductCardV2Test` covers both text paths, Arabic, images, prices and selection, and writes a preview to `kriolos-opos-app/target/catalog-cards-preview.png`.

The divider below the classic sale totals resizes the product catalog. Its arrow controls can fully collapse and restore the catalog. **Hide status bar** collapses the separate bottom bar containing the database details, Exit, and current user; **Show status bar** restores it.

Deleting the current sale or a selected line takes effect immediately. Role permissions still apply, line and sale removals are audited, and the optional supervisor PIN still applies to deleting a sale. Deleting a current sale also removes its suspended copy so it cannot return after switching screens.

Configuration > Ticket Setup > **Receipt Print Off** sets the initial checkout printer state. When checked, the printer starts off in classic and both modern sales layouts. The cashier can still turn it on for an individual receipt. In classic sales, `Ticket.Buttons` can also set `<printselected value="false"/>` to start with printing off. Configuration changes require a restart.

The Resources screen edits entries in the current database: login branding (`Login.*`), `Printer.Ticket` and other `Printer.*` receipt templates, `Menu.Root`, `Ticket.Buttons`, and `script.*` scripts. Most other prompts, status messages, and screen labels come from `pos_messages.properties` and the language bundles; changing those requires rebuilding the application. Configuration > Language & Region selects the language. Existing database resources are preserved when installing a new build, so edit those resources in the running application when customizing an existing store.

## Startup screen text and images

Open **Maintenance > Resources** after activating the store database. The following resources are added automatically on the first startup of this build, including existing databases:

| What to change | Where to edit |
| --- | --- |
| Central welcome text | `Login.Welcome` (Text): plain text or HTML beginning with `<html>` |
| Logo at the top of the login screen | `Login.Logo` (Image): use the resource's image picker to import a PNG/JPEG; it fits within 320 by 120 pixels and keeps its aspect ratio |
| Heading above the login users | `Login.Header` (Text), initially `Login` |
| Text beside the logo | `Login.Vendor` (Text), initially empty; accepts plain text or Swing HTML |
| Database/status prompts | `pos_messages.properties`, including `button.activate` and `label.database.*`; translated labels live in the language modules |
| User names and user pictures | Maintenance > Users in the running application |
| Database names in the selector | Configuration > Database |

Save each resource, then log out to preview it. No rebuild or restart is needed for these resource edits. Content belongs to the active database, so each store can have different branding. The last successfully loaded branding is cached locally under `%APPDATA%\KriolOS\login-branding`, separately for each database URL, name and username. Future startups load that cache before the database is available, then refresh it from the database on connection and logout. Before the first connection, or if the cache is unavailable, a neutral connection prompt appears instead of briefly showing the original company branding. Passwords are not written to the cache. Missing/invalid logos in the connected store fall back to the bundled logo, and missing text resources fall back to bundled defaults. Empty text deliberately hides that text. HTML uses Swing's limited HTML renderer, rather than browser JavaScript or modern CSS. For example, set `Login.Welcome` to:

```html
<html><center><h1>My Store</h1><p>Welcome! Select your user to begin.</p></center></html>
```

The default welcome HTML includes the project's license notice; the distribution's license files and source notices are separate. Default templates live in `src/main/resources/com/openbravo/pos/templates/Login.*`; the guarded `db-changelog-login-branding.xml` inserts missing defaults and preserves store customizations.

Enter submits password/text prompts. In classic sales, Enter opens checkout when no barcode is pending; with a pending barcode, it adds or looks up that product. The on-screen barcode button keeps its barcode-only action. Checkout permissions and the payment confirmation step still apply.

## Product barcode generation

The product editor has a **Generate** button immediately to the right of Barcode Type. It creates a 13-digit value with the internal-use `200` prefix, calculates the EAN-13 check digit, and queries `CatalogService.getProductInfoByCode()` against the active store before filling the Barcode field. A collision generates another candidate, up to 100 attempts. The button also selects the `EAN-13` barcode type. The existing unique index `products_inx_1` on `products.code` remains the final protection against concurrent duplicates at save time. The checksum implementation and tests are in `Ean13BarcodeGenerator`; the lookup remains in `ProductsEditor` because the domain generator has no database dependency.

## More notes

### Existing database orders checksum compatibility

Upstream commit `0478bbdb` edited the already released orders-table changeset `1603928315936-19`, changing `qty` from INT to DOUBLE and `displayid` from INT to VARCHAR(50). Databases initialized before that change can reject startup with the recorded checksum `9:e9b9b069ca8f50a61072fc15f2b6b39d`. The original changeset now accepts that exact historical checksum; other unexpected checksums still fail validation. `db-changelog-orders-compatibility.xml` performs the actual column upgrade as a new migration, preserving rows and defaults. SQLite rebuilds only the legacy integer table, copying its rows and identity sequence; compatible SQLite tables are left in place. HSQLDB and SQLite upgrade/restart behavior is covered by `OrdersMigrationCompatibilityTest`. Run the updated application against the existing database; do not delete its files or clear all Liquibase checksums to resolve this error.

### Maintenance navigation and SQLite sale dates

Maintenance views are cached. `JPanelTable` mounts its editor, scroll panes, navigation controls, and listeners only once, then reloads records on each activation. Rebuilding that component tree on every visit accumulated overlapping controls and left old viewports behind.

SQLite datetime columns use formatted local-time text (`yyyy-MM-dd HH:mm:ss.SSS`), matching static SQL report filters. Both prepared timestamp writers use the same format through `JdbcTimestamp`. The SQLite-only `20261031-sqlite-timestamps` migration converts earlier epoch-millisecond values, including receipt and inventory dates, while leaving formatted dates and NULLs intact. This repair runs at the next database startup. `SQLiteSalesPersistenceTest` covers save/search/load/report, inventory and taxes, restart, legacy-date repair, and rollback after a payment failure. Statements must leave commit/rollback to the enclosing `Transaction`; committing an individual payment or status update can leave a partial sale. `Session` opens one connection and reuses it for driver detection.

Sales report scripts use `JParamsDatesInterval.setToday()` for a full-day default, from today's midnight inclusive to tomorrow's midnight exclusive. The default refreshes when revisiting on a later day; manually edited date ranges are preserved. An end time fixed to the minute the report was first opened would hide later sales. Closed Cash and reports grouped by `closedcash.DATEEND` still require a closed cash session.

### Import categories from CSV

Open **Tools > Categories Import**, choose a CSV file, select its encoding and separator, then click **Read headers**. Map **Name** and any optional fields before clicking **Import categories**. A sample file is:

```csv
Name,Parent,Text tip,Show name,Catalog order,Catalog color,In catalog,Image
Drinks,,Cold and hot drinks,yes,1,#3478A2,yes,
Tea,Drinks,Tea products,yes,2,#5A9B69,yes,images/tea.png
```

Supported fields are ID, Name, Parent, Text tip, Show name, Catalog order, Catalog color, In catalog, and Image. Parent accepts a category ID or name, including a parent later in the same file. Image paths are relative to the CSV file. Boolean fields accept `yes/no`, `true/false`, `y/n`, or `1/0`. Existing categories update by ID when provided, otherwise by a unique case-insensitive name. Unmapped or blank optional fields retain their existing values; new rows use the normal category defaults. The whole file is checked before changes are committed, and a database error rolls the import back. Roles with permission to import products also have permission to import categories. Existing stores receive the new Tools entry at runtime when their stored menu still contains the standard Products Import entry.

- [Build and run on Windows](installer.md)
- [Architecture and change checklist](architecture.md)
- [Michel Abboud commit review](michel-commits.md)
- [Embedded HSQLDB for development and installs](hsqldb.md)
- Existing [development guide](../modules/guide-devel/pages/index.adoc) and [database overview](../modules/guide-devel/pages/database-overview.adoc)
