# KriolOS POS maintainer guide

This checkout is a Java 17, Maven, Swing continuation of uniCenta oPOS. If you already know uniCenta's screens, start with the code map below. The existing Antora documentation in `docs/modules/guide-devel` describes concepts and schema; this guide records where current behavior lives in this checkout.

## Code map

| Concern | Current location |
| --- | --- |
| Parent build, module versions, Java baseline | `pom.xml` |
| Startup and window lifecycle | `kriolos-opos-app/.../forms/BootstrapPOS.java`, `ApplicationShell.java` |
| Configuration file and defaults | `kriolos-opos-domain/.../forms/AppConfig.java` |
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

## Product barcode generation

The product editor has a **Generate** button immediately to the right of Barcode Type. It creates a 13-digit value with the internal-use `200` prefix, calculates the EAN-13 check digit, and queries `DataLogicPIM.getProductInfoByCode()` before filling the Barcode field. A collision generates another candidate, up to 100 attempts. The button also selects the `EAN-13` barcode type. The existing unique index `products_inx_1` on `products.code` remains the final protection against concurrent duplicates at save time. The checksum implementation and tests are in `Ean13BarcodeGenerator`; the lookup remains in `ProductsEditor` because the domain generator has no database dependency.

## More notes

- [Build and run on Windows](installer.md)
- [Architecture and change checklist](architecture.md)
- [Embedded HSQLDB for development and installs](hsqldb.md)
- [Embedded Derby status and restoration plan](derby.md)
- Existing [development guide](../modules/guide-devel/pages/index.adoc) and [database overview](../modules/guide-devel/pages/database-overview.adoc)
