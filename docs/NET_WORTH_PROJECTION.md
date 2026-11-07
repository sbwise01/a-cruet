# Net worth register follow-on (piggyback + projection)

**Status:** Shipped (2026-11-07). Locked in [`PRODUCT.md`](PRODUCT.md) §15. One train with §14 and §16: [`REPORTING_QUEUE.md`](REPORTING_QUEUE.md).

This file is the decided plan for two follow-ons to the shipped net-worth register ([`NET_WORTH.md`](NET_WORTH.md) / PRODUCT §11):

1. **Piggyback** — stamp a COMPUTED observation when a **today** position snapshot is saved, and collapse the **chart** to one point per calendar quarter.
2. **Projection** — optional 5-year dashed line on `/reports/net-worth`, a **clarity sketch** of today’s run-rate, not a prediction.

```text
today's position snapshot (as-of = today)
        └── if register stale or already today ── append COMPUTED (live Net)

/reports/net-worth
        ├── table: every observation (unchanged)
        ├── chart history: latest observation per calendar quarter
        └── optional dash: 5 years from live Net (engine B)
```

This is **not**:

- Fitting a line through saved net-worth observations (manual year-end totals have no asset split)
- A new report page
- A server cron or a silent write on unlock
- Replacing or deleting register rows (append-only stays)
- Inventing line items for past years
- Drawdown / retirement income (later-scope; README To Do **#4** is the revolving-credit brand question)

## Decisions

### Piggyback (amends §11 “no opportunistic write”)

| Item | Decision |
|------|----------|
| When | After a successful **asset or liability** snapshot whose **as-of is today** |
| What | Append COMPUTED from **live Net** (same payload as **Save net worth**) |
| Stale | Latest register as-of is empty, **≥ 1 calendar month** old (existing reminder clock), **or is already today** |
| Same day | **Append** every qualifying snapshot. No replace. Chart shows latest per date before quarterly collapse; table keeps every row |
| Not when | Unlock, envelope tx, **past** as-of (statement backfill), locked session |
| Save button | Unchanged. Home reminder unchanged (envelope-only months still need it) |
| Cap | Still **600** rows |

### Chart display (history)

| Item | Decision |
|------|----------|
| Table | **Every** row (manual, computed, auto). Newest-first as today |
| Chart | **Latest observation per calendar quarter** (Jan–Mar, Apr–Jun, Jul–Sep, Oct–Dec), plotted at that row’s as-of date. Year ticks stay |
| Same day | Extra auto rows are table noise; they collapse with the date, then with the quarter |

### Projection (engine B)

Project **today’s live ingredients** item-by-item, then sum. Vesting, transfers, and one-time spikes belong in **history**; they do not repeat in the future.

| Item | Decision |
|------|----------|
| Horizon | **5 years**, **20** quarter-end steps from today. Not 10. Not a year picker |
| Join | **Live Net today** (join B). Sparse quarterly history may leave a **gap**. Do **not** write a register row for “today” just to join |
| Where | Dashed overlay on `/reports/net-worth` **chart only**. Table stays facts |
| Toggle | Off by default. Independent of the gain-report toggle |
| Copy | *If this pace continued — not a prediction.* |
| Grain | **Calendar quarters**, matching the asset reminder and statement PDFs — not 12 zero-filled months |
| Investments | Flagged assets only. Each item: **median of last 4 quarter-sums of `flowCents`**, **median of last 4 quarterly simple returns** `r = (EMV − BMV − C) / BMV` when BMV > 0. Future: `C` lands on the notional balance; gain is `r × BMV`. A vest is one fat quarter; the median barely moves |
| Non-investment assets | **Freeze** last snapshot. No phantom house appreciation |
| Envelopes | **Freeze** last bundle totals |
| Liabilities | Quarter change = balance at quarter-end minus prior quarter-end (carry-forward). **Median of the 4 deltas**. If median **< 0**, keep applying that dollar paydown each future quarter, **floor $0**. If median **≥ 0**, **freeze** last balance (do not project new borrowing). Revolving-credit *brand* is README To Do **#4**, not a second engine |
| Moving item | An **investment** or a **paying-down** liability (median Δ < 0) |
| Gate | **4 consecutive calendar quarters in the trailing 12 months** with a snapshot on **at least one** moving item. Year-end net-worth stamps, houses, cars, and envelopes **do not** count |
| Locked UI | Toggle **visible but disabled**. Short **title** on a wrapper (disabled controls do not hover). **Hint** is the recipe: quarter-by-quarter scoreboard (missing / ok), closest moving item, how to backfill (investment: value + net contribution on statement dates; loan: four balances). House marks that sit in a quarter still show **missing** |
| Empty months | Not $0 flow. No observation in a quarter means that quarter is **missing** for the gate |
| Depends on | Investment flag + contribution field ([`INVESTMENTS.md`](INVESTMENTS.md) R1–R2) |

Shared investment run-rate with [`INVESTMENT_GAIN.md`](INVESTMENT_GAIN.md): same `r` and `C` medians. Net worth applies them **item-by-item** then sums. The gain report **sums first**. The two dashed lines may differ; they answer different questions.

## Releases

One train with investments and gain: [`REPORTING_QUEUE.md`](REPORTING_QUEUE.md).

No new tables. **Piggyback + quarterly chart + dashed line** ship in release 2 (UI). **Landing copy** in release 3. The dashed line still needs release 1’s `investment` column.

## Out of scope

- 10-year horizon
- Fitting the net-worth observation series
- Same-day **replace** / edit / delete
- Silent write on unlock or envelope tx
- Extra home nag for the projection
- Projecting new borrowing (growing credit cards)
- Drawdown, RMDs, `incomeCents`
- Server-side amounts
