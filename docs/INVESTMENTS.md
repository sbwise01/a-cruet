# Investment tracking and performance

**Status:** Shipped (2026-11-07). [`PRODUCT.md`](PRODUCT.md) §14. Same train as §15–§16: [`REPORTING_QUEUE.md`](REPORTING_QUEUE.md).

This file is the decided implementation plan. It is **not** envelope transactions and **not** holdings/tickers. It adds a monthly **net contribution** on opted-in snapshot **assets**, then a return report.

Value over time ([`VALUE_OVER_TIME.md`](VALUE_OVER_TIME.md)) stays the dollar path for any item or group. This feature answers **how much of the change was new money vs gain or loss**.

## What this is (and is not)

Snapshot items stay a **register of totals**, not a ledger of trades. An investment-type asset is still one account number (401k, brokerage). Once a month the household records **ending value** plus **net cash in or out**.

```text
asset (investment flag) ── monthly snapshot { cents, flowCents }
        │
        └── simple monthly return ── trailing 12-month annualized return
```

This is **not**:

- Envelope deposits / withdrawals / transfers (too much precision; different asset class)
- Holdings, lots, tickers, cost basis, or a price feed
- A server enum named `INVESTMENTS` (the **Investments** group remains a household ciphertext label)
- Liabilities (paying down a loan is not a contribution)
- Inferring return from value over time alone

## Decisions

| Item | Decision |
|------|----------|
| Who | Snapshot **assets** only. Optional. Existing items stay off until opted in |
| Flag | Plaintext `investment` boolean on `financial_position`, default **false**. Same class of metadata as `ASSET`/`LIABILITY`. Server may know *how many* flagged items, never names or amounts |
| Set flag | **Add asset** checkbox. Existing items: compact row control (not the Update-value form). Toggle does not append a valuation |
| Monthly update | Same Update-value form as today. If flagged: **Current value** + **Net contribution this month** (empty = 0). If not flagged: value only |
| Payload | Ciphertext `{ cents, flowCents? }`. Missing `flowCents` = 0 (old snapshots) |
| Several updates in one month | **Sum** `flowCents` whose `as_of_date` falls in that calendar month. Ending value = latest snapshot on or before month-end (same as value over time) |
| Return | Simple monthly: `r = (EMV − BMV − C) / BMV` when BMV > 0. Else no % that month (new/funded account). Chart = **trailing 12-month annualized** as `mean(r) × 12` (same as `sum(r) × (12/k)`). Not compound `(1+r)^12 − 1`. Not cumulative since inception |
| Timing | No intra-month weighting (not Modified Dietz in v1). Contribution sits in the month of its `as_of_date` |
| Report series | Flagged **assets**, plus **groups that contain at least one flagged asset**. Sum those members’ values and flows, then the same return math |
| Mixed groups | Car in **Autos** is ignored for this report; a flagged brokerage in the same group is included |
| Liabilities | Never flagged. Reject `investment: true` on a liability |
| Envelopes | Out of this report (use Envelope balance / value over time as appropriate) |
| Membership | Current flagged members (same rewrite-the-past rule as value over time) |
| Chart / table | Monthly axis, year labels each January (same as value over time). Chart: **trailing 12-month annualized %**. Table: month-end, value, net contribution, monthly %, annualized % |
| Dollar path | Remains `/reports/value-over-time`. This page does not replace it |
| Scope | Household-wide (same DEK). Unlocked to write flows or view the report |
| Admin | No extra `/users` column in v1 (`position_count` is enough) |
| Offboard | Positions include `investment`. Snapshot export includes `flowCents` when present |

Key loss unchanged. Admins still cannot decrypt.

## Data model

New Flyway migration **`V16__position_investment.sql`**, schema version **1.6.0**.

- `financial_position.investment` — `BOOLEAN NOT NULL DEFAULT false`
- Check: `investment` is false when `kind = 'LIABILITY'` (or reject in the service; DB check is better)
- No household counter. No new table. Snapshot rows unchanged (flow lives in ciphertext)

Do **not** reuse `ledger_transaction`. Do **not** store `flowCents` in plaintext.

## API (user WAR)

`/positions` (existing resource):

- `GET` includes `investment` (boolean)
- `POST` accepts optional `investment` (default false). Reject if `kind` is `LIABILITY` and `investment` is true
- `PUT /positions/{id}` can set `investment` without a valuation (omit = no change; same omit-vs-value pattern as `groupId`)

`POST /positions/{id}/snapshots` unchanged at the HTTP layer (`encryptedPayload` is opaque). Client puts `flowCents` in the blob for flagged assets.

## UI

### `/assets`

- Add asset: checkbox **Track as investment**
- Row: compact **I** control (filled when flagged), not on the amount form — same idea as **G** for groups
- Update value: extra **Net contribution this month** only when flagged. Hint: money added (+) or withdrawn (−) since the last total; not a trade list
- Live Net **unchanged** (still latest values; flows are not a second net)

### `/reports/investment-performance`

Unlock chrome like other reports. No bundle picker.

- Heading **Investment performance**
- One dropdown: flagged assets; then groups with ≥1 flagged asset
- Trailing 12-month annualized-return chart; table as in Decisions
- Hub tile on `/reports` (reuse `/media/report-graphs.png` unless a new media file exists)

Bump `UserNav.STATIC_ASSET_VERSION` when JS ships.

## Releases

One train with piggyback, net-worth sketch, and investment gain: [`REPORTING_QUEUE.md`](REPORTING_QUEUE.md).

This feature’s **schema** is release 1. Its **assets UI + performance page** are release 2. **Offboard / marketing** is release 3.

## Out of scope

- Envelope ledger for investments
- Holdings, tickers, lots, dividends as their own lines, brokerage connections
- Modified Dietz / intra-month weighting
- IRR as a separate money-weighted metric (linked monthly TWR only)
- Investment flag on liabilities
- Admin **Investments** count
- Membership history
- Replacing value over time
- `incomeCents` / drawdown (later-scope; not the gain report)
