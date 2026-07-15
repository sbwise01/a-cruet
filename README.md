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
- **View reports** — a spreadsheet-style history and a chart of balances over time
- **Protect your data** with a personal encryption passphrase that only you know

The app is built for people who want a simple, intentional way to **allocate and track savings by purpose**, not a full double-entry accounting system or a bank connection.

---

## How your privacy works

Your financial details — envelope names, amounts, notes, and balances — are **encrypted in your browser** before they ever reach the server. The service stores scrambled data it cannot read.

You choose a **passphrase** to unlock your ledger. The app asks you to save a **recovery file** when you first set up encryption. If you lose both your passphrase and that file, **your data cannot be recovered** — not even by an administrator. That is intentional: your savings picture stays yours alone.

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

## To Do list

1. **Database migration strategy** — Today Flyway runs inline on Tomcat startup; decide how to handle long-running migrations and backfills (dedicated Job vs startup-time Flyway, idempotent batch steps, online/offline cutover).
