# a-cruet

> *"The wise store up choice food and olive oil, but fools gulp theirs down."* — Proverbs 21:20

**a-cruet** is a personal web app for **envelope budgeting** — setting aside money for specific purposes (Christmas fund, vacation, health expenses, and similar goals) and tracking how those balances change over time.

A **cruet** is a small glass vessel for valued liquids like olive oil. The name also echoes **"accrue it"** — the accounting idea of letting savings build up deliberately rather than spending as it arrives.

---

## What it does

Envelope budgeting treats each savings goal as its own **envelope**. When money comes in, you decide how much belongs in each envelope. When you spend or move money, you take it from the right envelope so you always know what each goal still has.

With a-cruet you can:

- **Apply for access** — submit a short application; an administrator reviews and approves new users
- **Sign in** securely and manage your own envelopes
- **Record deposits**, **withdrawals**, and **transfers** between envelopes
- **Group envelopes into bundles** — each bundle is its own closed set of envelopes (for example a year or a vacation plan); transfers stay inside the bundle
- **View reports** — a spreadsheet-style history and a chart of balances over time for the bundle you have selected, **value over time** for a snapshot item or group, **investment performance**, household **investment gain over time**, and an optional 5-year sketch on net worth
- **Track other assets and liabilities** — cars, investment totals, loans, and similar items as dated snapshot values, separate from envelope transactions
- **Group snapshot items** — optional household labels such as Investments or Autos so related assets and debts sit together. Envelope cash is its own Envelopes / Savings row
- **Save a net worth history** — stamp today’s live Assets Net, or add past year-end totals you already wrote down. The Assets Net line is today only; the register is the history you keep
- **Share a household ledger** — an existing member can invite family by email so you use the same bundles and envelopes together (each person has their own sign-in and encryption passphrase; up to five members per household)
- **Protect your data** with a personal encryption passphrase that only you know

The app is built for people who want a simple, intentional way to **manage personal finances** — allocate savings by purpose, track other assets and debts, review transactions, and keep a net worth history — not a full double-entry accounting system or a bank connection.

---

## How your privacy works

Your financial details — envelope names, amounts, notes, and balances — are **encrypted in your browser** before they ever reach the server. The service stores scrambled data it cannot read.

You choose a **passphrase** to unlock your ledger. That is not your sign-in password. The app asks you to save a **recovery file** when you first set up encryption. After the first unlock you may optionally register this device (Touch ID, Face ID, or Windows Hello). A registered device can also set a new passphrase if you forget the old one. If you lose your passphrase, recovery file, and every registered device, **your data cannot be recovered** — not even by an administrator. That is intentional: your savings picture stays yours alone.

Administrators can see **who** uses the app and **how much activity** there is (for example, how many envelopes or transactions exist), but they **never** see your actual balances or descriptions.

---

## Getting access

a-cruet is **invite-by-approval**, not open self-registration:

1. You fill out a short application (name, contact details, and why you want access)
2. You confirm your email address
3. An administrator reviews your request
4. If approved, you receive sign-in instructions and create your encryption key on first login

If an application is not approved, you may apply again after a waiting period. Repeated rejections can block further applications from the same email until an administrator clears it.

---

## For builders

Technical requirements, architecture decisions, and rollout phases live in:

- [`PRODUCT.md`](docs/PRODUCT.md) — locked product and engineering decisions
- [`ROLLOUT.md`](docs/ROLLOUT.md) — phased implementation plan for wise-k8s
- [`ASSETS.md`](docs/ASSETS.md) — snapshot assets/liabilities and asset groups (shipped)
- [`DEVICE_UNLOCK.md`](docs/DEVICE_UNLOCK.md) — WebAuthn device unlock (shipped)
- [`NET_WORTH.md`](docs/NET_WORTH.md) — net worth register (complete)
- [`VALUE_OVER_TIME.md`](docs/VALUE_OVER_TIME.md) — snapshot value-over-time report (shipped)
- [`INVESTMENTS.md`](docs/INVESTMENTS.md) — investment flag, monthly contribution, performance report (shipped)
- [`NET_WORTH_PROJECTION.md`](docs/NET_WORTH_PROJECTION.md) — net-worth piggyback stamp, quarterly chart, 5-year sketch (shipped)
- [`INVESTMENT_GAIN.md`](docs/INVESTMENT_GAIN.md) — household investment gain over time + sketch (shipped)
- [`REPORTING_QUEUE.md`](docs/REPORTING_QUEUE.md) — one schema / UI / docs train for §14–§16 (shipped)
- [`OPERATIONS.md`](docs/OPERATIONS.md) — Keycloak admin console steps (including after operator CR re-apply)

## To Do list

1. **Database migration strategy** — Today Flyway runs inline on Tomcat startup; decide how to handle long-running migrations and backfills (dedicated Job vs startup-time Flyway, idempotent batch steps, online/offline cutover).
2. **Bootstrap admin is an a-cruet user** — First admin today is a Keycloak console role plus a separate SQL backfill. Treat bootstrap as auto-approved: creating `acruet_user` (and household + default bundle) is part of bootstrapping so they always appear on `/users`. Do not reuse the Approve/provision-Keycloak path (that email already exists).
3. **Credit cards / revolving credit** — Decide the product stance before treating a growing card as a projected liability (or any dedicated card feature). Paying a card in full each month is envelope spending, not a snapshot liability; carrying a balance is a liability like any other loan. The brand question is whether a-cruet **meets people where they are** (let a card stay on `/assets` and let a growing balance be part of the picture) or treats revolving debt as out of scope so the product only fits households that already live like the verse. Not locked.
