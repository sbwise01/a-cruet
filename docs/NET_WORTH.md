# Net worth register

**Status:** Product decisions locked (2026-11-01). Not shipped. Locked in [`PRODUCT.md`](PRODUCT.md) §11. README To Do **#3** stays open until release 3.

This file is the decided implementation plan for a household **history of net worth**, including years the user only recorded as a yearly total.

## What this is (and is not)

The Assets page **Net** line is live register arithmetic: active envelope-bundle totals + latest snapshot assets − latest snapshot liabilities. That is **today’s** picture. It does not remember last December, and it cannot invent line items for years when the user only wrote down a single number.

**Net worth register** is an append-only series of **observations**:

- **Manual** — date + one total (year-end backfill; no breakdown)
- **Computed** — client saves the live Assets Net math (optional breakdown in the ciphertext)

```text
Envelope totals (now)     ─┐
Latest snapshot assets    ─┼── live Net on /assets ── "Save net worth" ── COMPUTED row
Latest snapshot liabilities─┘

Year-end totals you already have ── "Add historical" ── MANUAL row

                         net_worth_snapshot ── chart / table
```

This is **not**:

- Replacing or hiding the live Net line
- A fake “Net worth” asset on the snapshot register (would hit the 50-item cap and mix concepts)
- Reconstructing 2008–2025 from today’s cars, 401k, and loans
- A server CronJob that sums the household (the server never has amounts)
- A silent monthly write on unlock

## Decisions

| Item | Decision |
|------|----------|
| Storage | New household table — **not** `financial_position` |
| Scope | **All household members** see the same series (same DEK) |
| Append | New row per save. No edit/delete of past observations |
| `as_of_date` | **Plaintext** (same class as position valuations) so staleness does not require decrypt |
| Payload | Ciphertext `{ netCents, source }`. Computed may add `envelopeCents`, `assetCents`, `liabilityCents` |
| Save current | Button **next to Net** on `/assets` while unlocked. Same math as the Net line. At will, including same day |
| Historical | Form on the Net worth report: as-of date + amount. Source `MANUAL` |
| Reminder | Signed-in **home** (`/`) when latest `as_of_date` **plus one calendar month** is on or before today, **or** the series is empty |
| Reminder actions | **Save net worth** writes a COMPUTED row (not quarterly “Done”). **Not now** snoozes **7 local days** |
| Preview | `/?netWorthReminder=1` (linked + key setup) |
| Two home dialogs | Quarterly assets reminder first ([`PRODUCT.md`](PRODUCT.md) §10); then this one if still stale |
| Staleness field | Latest **`as_of_date`**, not `created_at` |
| Chart | Stored points only — no interpolated months between yearly backfills |
| Cap | **600** rows per household |
| Idle / lock | Must be **unlocked** to save computed or add historical (ciphertext). Staleness check can use plaintext dates without unlock |
| Origin | User app only. Admin: operational **count** on `/users`, never amounts |
| Currency | **USD** cents, same as the rest of the app |

Key loss unchanged: passphrase + recovery file + every device. Admins still cannot decrypt.

## Data model

New Flyway migration **`V14__net_worth_snapshots.sql`**, household-scoped. Schema version **1.4.0**.

- `net_worth_snapshot` — `id`, `household_id`, `as_of_date`, `encrypted_payload`, `created_at`
- Foreign key to `household` with `ON DELETE CASCADE`
- Index `(household_id, as_of_date DESC, created_at DESC)` for “latest” and chart order
- `household.net_worth_snapshot_count` — active/all rows (append-only; count = row count)

Do **not** reuse `financial_position` / `kind`. Do **not** store plaintext cents.

## API (user WAR)

Path `/net-worth` (signed-in + key-setup complete):

- `GET /net-worth` — list observations (`id`, `asOfDate`, `encryptedPayload`, `createdAt`) plus `count` / `limit`
- `GET /net-worth/latest` — latest by `as_of_date` then `created_at` (plaintext date is enough for the home reminder; payload included when present)
- `POST /net-worth` — `{ asOfDate, encryptedPayload }` (client sets source inside ciphertext)
- No update/delete in v1

Reuse session + key-setup gates and per-user write rate limits. Reject when `count >= 600`.

Offboard export: add a net-worth CSV/JSON section (date, net, source, optional breakdown).

Admin `/users`: show **Net worth** count from `household.net_worth_snapshot_count`.

## UI

### Assets (`/assets`)

Keep the live Net row. Add **Save net worth** beside it (disabled while locked or when Net is still loading). Success is a short confirmation; it does not navigate away.

### Reports hub

Third tile **Net worth** (reuse an existing media image or the assets tile if no new art). Page:

- Chart of observations (date × net), Chart.js like the balance report
- Table: as-of, net (comma formatted), source
- **Add historical total** — date + amount
- Computed tooltips may show the three-part breakdown

### Home reminder

Same chrome family as the quarterly assets dialog. Copy distinguishes first save vs “last stamp is over a month old.” Primary action saves **today’s** computed net (as-of = local today). Link to `/assets` for line-item edits. Escape / backdrop = Not now.

Bump `UserNav.STATIC_ASSET_VERSION` when JS ships.

## Rollout

Three releases. Verify each with `docker build --target build`. Brad owns commit/PR unless asked that turn. Do not mark README To Do **#3** done until release 3.

### Release 1 — Schema + API

- Flyway `V14__net_worth_snapshots.sql` and schema version **1.4.0**
- Repository + `NetWorthResource` list / latest / create
- Cap (600), household count, rate limit
- No UI change; Assets Net unchanged

**Verify:** unit tests for cap, latest ordering (`as_of_date` then `created_at`), cascade on household delete; package succeeds. ✅ (2026-11-01; `docker build --target build`)

### Release 2 — Save current, backfill, report

- `acruet-assets.js`: **Save net worth** next to Net (unlocked; COMPUTED payload + breakdown)
- Reports hub tile + chart/table + **Add historical total**
- Offboard export includes the series
- Admin `/users` Net worth count
- Cache-bust (`UserNav.STATIC_ASSET_VERSION` `20261101-10`)

**Verify:** save current from `/assets` and see the point on the report; add a year-end total (e.g. 31 Dec 2010) and see it on the chart without inventing line items; live Assets Net still matches today’s calculator. Package succeeds. ✅ (2026-11-01; `docker build --target build`)

### Release 3 — Sign-in reminder + docs

- Home modal when latest `as_of_date` + 1 month ≤ today, or series empty
- **Save net worth** writes COMPUTED; **Not now** = 7-day `localStorage` snooze
- Queue after quarterly assets reminder when both apply
- `/?netWorthReminder=1` preview
- Point [`PRODUCT.md`](PRODUCT.md) §11 at this file (already locked) and mark README To Do **#3** done
- Unauthenticated landing (`MarketingContent`) and matching README: net worth is a **history you save** (including past year-end totals), not only the live Assets Net line

**Verify:** empty series shows first-save copy; after save, reminder stays gone until as-of + 1 month; Not now suppressed for 7 days; `?netWorthReminder=1` still shows; quarterly dialog still wins when both are due; public `/` copy mentions saved net-worth history / year-end backfill.

## Out of scope

- Server-side monthly CronJob or any plaintext net
- Silent write on unlock
- Interpolating months between yearly backfills
- Editing or deleting an observation
- Per-user private net worth (household series only)
- Investment performance / holdings (README To Do **#4**)
- Treating the Assets Net line as this feature
