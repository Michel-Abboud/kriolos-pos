# Architecture and change checklist

## Runtime path

`BootstrapPOS` starts the desktop app. `WindowShell` hosts the application or configuration panel. `ApplicationShell` opens the selected `DatabaseConfig`, creates a `Session`, runs `DBMigrator`, initializes `DataLogicSystem` and the other services, then exposes them through `AppView`. This is where a fresh database is initialized; a working JDBC connection by itself is not enough.

The app is a Maven reactor. The important dependency direction is `base` (JDBC utilities and shared infrastructure) -> `domain` (tickets, application properties, data logic) -> `app` (Swing screens, migrations, receipt templates). `hardware` contains device integration. `ui-api` defines sales layout and catalog extension points; `ui-native` and `ui-flatlaf` provide UI implementations. Other modules supply reports, images, translations, and plugins. `kriolos-opos-jpackage` is a packaging module, not the application entry point.

## Sale and receipt path

`JPanelTicketSales` routes to `JPanelTicketSalesClassic` (which extends `JPanelTicket`) or a modern layout through `SaleLayoutManager`. For a classic sale, scanned products become `TicketLineInfo` objects in `TicketInfo`; `JTicketLines` renders them. `JPanelTicket.printPartialTotals()` refreshes subtotal, tax, and total after ticket edits. Modern one uses `ModernTicketPane.updateTicketDisplay()` and modern two uses `ModernTwoTicketPane.updateTicket()` for their own totals. The payment dialog and persistence use `TicketInfo.getTotal()`, so display features must not mutate that value.

The printable receipt is a `resources` database entry named `Printer.Ticket` (or another selected resource). `JPanelTicket.printTicket()` reads its XML, evaluates Velocity with `ticket` in the context, and passes the result to `TicketParser`. New ticket getters can therefore be referenced as `${ticket.methodName()}`. A source XML edit affects a fresh seed or deployment path; an established store may still use its database copy. Test both the on-screen total and the editable receipt resource.

## Config and database path

`AppConfig` owns property defaults and the per-user file path. `JPanelConfiguration` contains the tabs and calls `loadProperties()` / `saveProperties()` on each `PanelConfig`. Startup needs a restart to consume changed settings. Database connection details are separate from currency settings. `Session` selects a SQL adapter using JDBC metadata; `DBMigrator` applies the Liquibase files in `pos_liquidbase`. The DB selector is a separate Swing list, so the presence of a JDBC driver dependency does not mean its engine is exposed in Configuration.

## Before changing a feature

- Trace the active layout; classic and modern views may format the same model separately.
- Check existing Antora docs under `docs/modules/guide-devel`, but verify claims in current source because some pages discuss migrations or future architecture.
- Preserve NetBeans generated blocks and paired `.form` files. Place adapter wiring after `initComponents()`.
- Keep UI updates on the Swing event thread. Database, network, and device work should run off it.
- Add focused domain tests for monetary rules, then build the app reactor. For a database change, use a scratch database and test a fresh startup plus restart.
- Check `git diff --check` and run `./mvnw.cmd -pl kriolos-opos-app -am test` before sharing a build.
