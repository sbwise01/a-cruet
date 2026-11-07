# Assets and liabilities (snapshot register)

**Status:** Product decisions locked (2026-10-18). Snapshot register shipped. Quarterly reminders: [`PRODUCT.md`](PRODUCT.md) §10 (shipped). Net worth history: [`NET_WORTH.md`](NET_WORTH.md) / PRODUCT §11 (shipped). **Asset groups** shipped (PRODUCT §12; releases 4–6).

Locked register rules live in [`PRODUCT.md`](PRODUCT.md) §9. Group rules live in §12. This file is the decided implementation plan.

## What this is (and is not)

Envelopes stay the **transaction** asset class: deposits, withdrawals, transfers, reports.

This feature is a **snapshot register**. You create an item, set a value and date, and update that value infrequently. The app keeps a history of those updates. There are **no** deposits, withdrawals, or transfers on these items, and **no** link to envelope transactions in v1.

- One value per item. Investment accounts are a single total (no holdings/tickers).
- Two page sections: **Assets** and **Liabilities**. Liability amounts are entered as positive balances owed. Current **Net** = assets − liabilities.
- Envelope bundle totals appear in **Assets** as **read-only** computed lines (not snapshot rows you edit).
- Household-wide: every member sees every item (same household DEK as bundles). Not nested under an envelope bundle.

The assets page **Net** line is live register arithmetic. History lives in the net-worth register ([`NET_WORTH.md`](NET_WORTH.md)). Quarterly line-item reminders are PRODUCT.md §10.

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

New unlocked page **`/assets`**, same chrome/unlock pattern as the ledger. Reached from the signed-in home launcher. Header icon and avatar **Home** return to `/`.

1. **Assets** — envelope bundle totals and snapshot assets, sorted by amount. Add / rename / update value / archive.
2. **Liabilities** — snapshot liabilities only (positive owed).
3. Footer: Assets, Liabilities, **Net**.

Envelope rows are not editable. Optional: click through to the ledger with that bundle selected. Archived bundles omitted.

Bump `UserNav.STATIC_ASSET_VERSION` when JS ships.

## Releases

1. **Schema + API** — V12, repositories, resource. Envelope UI unchanged. ✅
2. **`/assets` UI** — sections, snapshot CRUD, read-only bundle lines, current Net. Cache-bust. ✅
3. **Export, admin, docs** — offboard includes positions; admin `/users` Positions count; README / marketing. Snapshot To Do closed. Net worth shipped later as PRODUCT §11. ✅

Verify with `docker build --target build`. Brad owns commit/PR unless asked that turn.

## Asset groups

Household labels so snapshot items can be reported together later — **Investments**, **Autos**, and whatever else the household names. Prerequisite for the value-over-time report ([`VALUE_OVER_TIME.md`](VALUE_OVER_TIME.md) / PRODUCT §13). Locked in [`PRODUCT.md`](PRODUCT.md) §12.

### What this is (and is not)

Groups are **display and report labels**, not a second ledger. They do **not** isolate items the way envelope bundles isolate transactions. There is no transfer between groups. Live Assets **Net** does not change.

- Household-defined names (ciphertext), same DEK as the rest of the register
- Optional: existing items stay **ungrouped** after migration; new items may be created ungrouped
- A group may hold **assets and/or liabilities** (cars and auto loans can share **Autos**)
- Envelope-bundle cash lines stay their own read-only rows — they are not snapshot items and do not join a group
- Moving an item to another group (or to ungrouped) updates membership only. It does not write a valuation snapshot

```text
group (optional) ── financial_position ── valuation snapshots
        │
        └── Investments / Autos / …
```

This is **not**:

- A plaintext enum the server understands (`INVESTMENT`, `AUTO`) — names are ciphertext
- Nesting items under an envelope bundle
- Holdings, tickers, or price feeds (value-over-time is [`VALUE_OVER_TIME.md`](VALUE_OVER_TIME.md))
- Required membership or a Flyway-created default group

### Data model

New Flyway migration **`V15__position_groups.sql`**, household-scoped. Schema version **1.5.0**.

- `financial_position_group` — `id`, `household_id`, `encrypted_name`, `status` (`ACTIVE`|`ARCHIVED`), timestamps, `archived_at`
- `financial_position.group_id` — nullable FK to `financial_position_group` (`ON DELETE SET NULL`)
- Index `(household_id, group_id)` on `financial_position`
- `household.position_group_count` — active groups

Do **not** store group names or report-type codes in plaintext. Do **not** reuse `envelope_bundle`.

### API (user WAR)

Path `/position-groups` (signed-in + key-setup complete):

- `GET /position-groups` — active groups (optional `includeArchived`)
- `POST /position-groups` — `{ encryptedName }`
- `PUT /position-groups/{id}` — rename only
- `POST /position-groups/{id}/archive` — only when no **active** items still use the group

`/positions`:

- `GET` includes `groupId` (nullable)
- `POST` accepts optional `groupId`
- `PUT /positions/{id}` also assigns or clears `groupId` (rename-or-regroup; still no valuation change)

Reuse session + key-setup gates and per-user write rate limits. Reject when active group count ≥ **20**. Reject archive while active items remain in the group (move or ungroup first). Archived groups do not count toward the cap. Archived items may keep a `group_id` for export.

### UI

`/assets` while unlocked:

- Default `/assets` view: each group is one **collapsed** row (name + sum); ungrouped items are individual rows; groups and ungrouped items sort together by amount. Envelope bundle totals are one group labeled **Envelopes / Savings** (expand to see each bundle). Expand/collapse via an arrow to the left of the group name. No Ungrouped heading or subtotal.
- Group subtotal (sum of latest values in that section). Assets and Liabilities sums sit on the section headings, aligned with item amounts. The footer is a matching **Net** heading and sum (Save / historical actions under it)
- Add / rename groups. Archive a group after its active items are moved or ungrouped. Client-side reserved name: **Envelopes / Savings** (envelope cash row; server cannot enforce — names are ciphertext)
- Add item: group picker on the create form (including Ungrouped). Existing items: compact **G** button between the name and Archive; click opens the group list. Update-value form does not change group
- Envelope-bundle lines stay above snapshot groups, still read-only

Bump `UserNav.STATIC_ASSET_VERSION` when JS ships.

### Releases

4. **Schema + API** — V15, repositories, group resource, `groupId` on positions. `/assets` UI unchanged (items remain ungrouped). ✅ (2026-11-07; `docker build --target build`)
5. **`/assets` UI** — headings, subtotals, create/rename/archive groups, assign on add (and a row picker for existing items — not the amount form). Cache-bust. ✅ (2026-11-07; `docker build --target build`)
6. **Export, admin, docs** — offboard includes groups + each item’s `groupId`; admin `/users` **Groups** count; README / marketing mention grouping. Do **not** mark investment performance reports done. ✅ (2026-11-07; `docker build --target build`)

**Verify R4:** unit tests for cap (20), archive blocked while active items remain, cascade on household delete; `GET /positions` includes `groupId` (null for existing items); package succeeds. ✅ (2026-11-07; `docker build --target build`)

**Verify R5–R6:** create Investments and Autos; assign items; ungrouped still allowed; live Net unchanged; archive blocked until the group is empty of active items; offboard JSON has `positionGroups` and each position’s `groupId`; admin `/users` shows **Groups**; package succeeds (`docker build --target build`). Brad owns commit/PR unless asked that turn.

## Out of scope

- Linking a loan payment to an envelope withdrawal
- Investment holdings, tickers, or price feeds (value-over-time report is [`VALUE_OVER_TIME.md`](VALUE_OVER_TIME.md))
- Per-user private assets
- Nesting snapshot items under a bundle
- Net-worth **history** (shipped separately — PRODUCT §11 / [`NET_WORTH.md`](NET_WORTH.md))
- Editing or deleting historical snapshots
- Server-known group types or a required default group
