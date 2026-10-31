# Assets and liabilities (snapshot register)

**Status:** Product decisions locked (2026-10-18). **Release 1 complete** — V12 + `/positions` API. Envelope UI unchanged. `/assets` is Release 2.

Locked rules live in [`PRODUCT.md`](PRODUCT.md) §9. This file is the decided implementation plan. Additional net worth work stays on the README To Do as item **#4**.

## What this is (and is not)

Envelopes stay the **transaction** asset class: deposits, withdrawals, transfers, reports.

This feature is a **snapshot register**. You create an item, set a value and date, and update that value infrequently. The app keeps a history of those updates. There are **no** deposits, withdrawals, or transfers on these items, and **no** link to envelope transactions in v1.

- One value per item. Investment accounts are a single total (no holdings/tickers).
- Two page sections: **Assets** and **Liabilities**. Liability amounts are entered as positive balances owed. Current **Net** = assets − liabilities.
- Envelope bundle totals appear in **Assets** as **read-only** computed lines (not snapshot rows you edit).
- Household-wide: every member sees every item (same household DEK as bundles). Not nested under an envelope bundle.

The assets page **Net** line is register arithmetic, not the Net Worth feature. README To Do **#4** stays after this ships (dedicated net-worth view, history/charts, or other rollups). Snapshot history is stored so #4 has data later.

```text
Envelope class                    Snapshot class
  bundle → envelopes → txs          item → valuation snapshots
        \                              /
         \                            /
          +--> /assets page <--------+
               read-only bundle totals
               + latest snapshot per item
```

## Data model

New Flyway migration (V12), household-scoped. Parallel to envelope tables — do **not** reuse `ledger_account` / `ledger_transaction`.

- `financial_position` — `id`, `household_id`, `kind` (`ASSET`|`LIABILITY`), `status` (`ACTIVE`|`ARCHIVED`), `encrypted_name`, timestamps, `archived_at`
- `financial_position_snapshot` — `id`, `position_id`, `as_of_date`, `encrypted_payload` (cents), `created_at`
- `household.position_count` — active snapshot items

Envelope cash is **not** stored here. The UI loads existing ledger APIs and sums balances the same way the ledger page does.

## API (user WAR)

Path `/positions` (covers assets and liabilities):

- `GET /positions` — active items (optional `includeArchived`); each row includes latest snapshot ciphertext
- `POST /positions` — `{ kind, encryptedName, asOfDate, encryptedPayload }`
- `PUT /positions/{id}` — rename only
- `POST /positions/{id}/snapshots` — new valuation `{ asOfDate, encryptedPayload }`
- `POST /positions/{id}/archive`

Reuse session + key-setup gates and per-user write rate limits.

## UI

New unlocked page **`/assets`**, same chrome/unlock pattern as the ledger. Link from the avatar menu and a control on the ledger home.

1. **Assets** — read-only row per **active** envelope bundle (`{bundle name} · envelopes`, computed total); then snapshot assets (name, current value, as-of date). Add / rename / update value / archive.
2. **Liabilities** — snapshot liabilities only (positive owed).
3. Footer: Assets, Liabilities, **Net**.

Envelope rows are not editable. Optional: click through to the ledger with that bundle selected. Archived bundles omitted.

Bump `UserNav.STATIC_ASSET_VERSION` when JS ships.

## Releases

1. **Schema + API** — V12, repositories, resource. Envelope UI unchanged.
2. **`/assets` UI** — sections, snapshot CRUD, read-only bundle lines, current Net. Cache-bust.
3. **Export, admin, docs** — offboard includes positions; admin `/users` Positions count; README / marketing. Mark To Do **#3** done. **Leave To Do #4**.

Verify with `docker build --target build`. Brad owns commit/PR unless asked that turn.

## Out of scope

- Linking a loan payment to an envelope withdrawal
- Investment holdings, tickers, or price feeds
- Per-user private assets
- Nesting snapshot items under a bundle
- Additional net worth work (To Do #4)
- Editing or deleting historical snapshots
