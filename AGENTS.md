# Repository guide for coding agents

Read [docs/maintainer-guide/README.md](docs/maintainer-guide/README.md) before modifying this POS. The companion [architecture notes](docs/maintainer-guide/architecture.md) map startup, sales layouts, data access, and receipt resources. [Installer instructions](docs/maintainer-guide/installer.md) and [HSQLDB notes](docs/maintainer-guide/hsqldb.md) record the Windows workflow and embedded database support.

This is a Java 17 Maven reactor and Swing application. Keep NetBeans generated sections intact. `JPanelTicketSales` routes between classic and modern layouts, so inspect every active layout when changing a visible sale total. `TicketInfo.getTotal()` is the source for payment and accounting; display-only conversions belong in separate methods. Receipt templates are Velocity resources that may already be stored in the database.

Use the root Maven wrapper and build affected modules with `-am`. Keep scratch POS databases separate from working store data.

After completing and verifying requested changes, create a Git commit with a meaningful message describing the behavior changed and relevant validation. Commit distinct fixes separately, preserve unrelated user changes, and do not push unless requested.
