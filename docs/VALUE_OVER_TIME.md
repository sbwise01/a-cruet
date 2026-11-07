# Value over time (snapshot items and groups)

**Status:** Shipped (2026-11-07). Locked in [`PRODUCT.md`](PRODUCT.md) §13. This is the snapshot dollar-path report. **Return** is [`INVESTMENTS.md`](INVESTMENTS.md); **dollar gain** is [`INVESTMENT_GAIN.md`](INVESTMENT_GAIN.md).

This file is the decided implementation plan for a household **value-over-time** report of snapshot assets, liabilities, and groups.

## What this is (and is not)

The net-worth report plots **saved household observations** on a yearly time axis. This report plots **snapshot register history** for one series the user picks: an asset, an asset group, a liability, or a liability group.

Amounts stay ciphertext. The browser decrypts `GET /positions/{id}/snapshots` and resamples to **month-end**, carrying the last known value forward.

```text
dropdown ── asset | liability ── that item's snapshots
        └── asset group | liability group ── current members of that kind ── sum
                              └── month-end points, carry last value forward
```

This is **not**:

- Holdings, tickers, or price feeds. **Return / contribution** is [`INVESTMENTS.md`](INVESTMENTS.md) / PRODUCT §14. **Dollar gain** is [`INVESTMENT_GAIN.md`](INVESTMENT_GAIN.md) / PRODUCT §16
- Reconstructing envelope cash (**Envelopes / Savings** is not on this dropdown; use Envelope balance chart)
- Membership history — regrouping **rewrites** a group chart’s past (current members’ histories, as if they were always in the group)
- Interpolating a slope between two values inside a month (carry-forward is hold-last)
- A substitute for the net-worth register ([`NET_WORTH.md`](NET_WORTH.md))

## Decisions

| Item | Decision |
|------|----------|
| Series | One of four: **asset**, **asset group**, **liability**, **liability group** |
| Dropdown | One `<select>` with those four optgroups. Active items and groups only |
| Mixed groups | Appear **twice** when they have both kinds: asset-side sum and liability-side sum |
| Empty groups | Omitted. Ungrouped items appear only as individuals |
| Envelopes | **Not** on this dropdown |
| Membership | **Current** members of that kind. No membership ledger |
| Liabilities | Positive amounts owed (same as `/assets`) |
| Chart | One point per **calendar month-end** from the first snapshot month through the **current local month** |
| Carry-forward | y = latest snapshot with `as_of_date` ≤ that month-end (tie-break `created_at`). Repeat last value through this month. No point before the first snapshot (do not start at $0) |
| Group sum | Each month, sum members’ carried values; a member with no snapshot yet contributes 0 that month |
| X axis | Monthly ticks; **year** label when the month is January; other months unlabeled |
| Table | Same monthly series (month-end + amount), not the raw snapshot list |
| Scope | Household-wide (same DEK). Unlocked session required |
| Storage | **None new.** Existing position snapshots |
| Origin | User app `/reports/value-over-time`. No admin column, no extra offboard (already in positions export) |
| Hub tile | Reuse `/media/report-graphs.png` |

Key loss unchanged. Admins still cannot decrypt.

## Data / API

No Flyway. No new resource.

- `GET /positions` — active items (`kind`, `groupId`, `encryptedName`)
- `GET /position-groups` — active groups
- `GET /positions/{id}/snapshots` — `asOfDate` + ciphertext cents

Selecting a group may fetch snapshots for each current member of that kind (household cap 50 items).

## UI

`/reports/value-over-time` while unlocked. Same chrome/unlock as other reports. No bundle picker.

- Heading **Value over time**
- One dropdown; changing it redraws the chart and table
- Line chart (Chart.js), then table
- Hub tile on `/reports` next to Net worth

Bump `UserNav.STATIC_ASSET_VERSION` when JS ships.

## Releases

1. **Page + chart** — route, dropdown, monthly carry-forward line + table, cache-bust. ✅ (2026-11-07; `docker build --target build`)
2. **Hub tile + docs** — `/reports` tile; PRODUCT §13; README / marketing; README To Do **#3** closed. ✅ (2026-11-07; `docker build --target build`)

Verify with `docker build --target build`. Brad owns commit/PR unless asked that turn. This feature is **complete**.

## Out of scope

- Envelope cash / Envelopes / Savings on this dropdown
- Group membership history
- Holdings, tickers, or return metrics (see [`INVESTMENTS.md`](INVESTMENTS.md); dollar gain is [`INVESTMENT_GAIN.md`](INVESTMENT_GAIN.md))
- Archived items
- Date-range filters (series is first snapshot → current month)
- Editing snapshot history
