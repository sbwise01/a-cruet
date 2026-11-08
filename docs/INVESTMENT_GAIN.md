# Investment gain over time

**Status:** Shipped (2026-11-07). Locked in [`PRODUCT.md`](PRODUCT.md) §16. Same train as §14–§15: [`REPORTING_QUEUE.md`](REPORTING_QUEUE.md).

This file is the decided plan for a household **dollar-gain** report of opted-in investments, plus an optional 5-year sketch that reuses the **investment slice** of net-worth projection ([`NET_WORTH_PROJECTION.md`](NET_WORTH_PROJECTION.md)).

Performance answers **percent**. Value over time answers **level**. This report answers **what annual dollar income this pile would produce at its trailing return if you stopped contributing**:

```text
all flagged assets ── sum first ── monthly r ── annualized % ── EMV × that %
                                              └── optional dash: 5 years, C = 0, pile grows from r only
```

This is **not**:

- Investment performance (that page keeps per-asset and group dropdowns)
- Value over time
- Envelope transactions
- Dividends / RMDs / cash “income” as a third field (`incomeCents` is later-scope, post-retirement)
- Groups or per-account zoom (detail lives on performance)

## Decisions

| Item | Decision |
|------|----------|
| When | Same UI release as performance ([`REPORTING_QUEUE.md`](REPORTING_QUEUE.md) R2). Reuses `investment` + `flowCents`. No new column |
| Series | **Household only** — every flagged asset, **sum first**. No dropdown. **No groups** |
| Gain | Each month’s **realized** gain is still `EMV − BMV − C` (table). **Annualized gain** is `EMV × mean(r) × 12` on the trailing window — current principal times the same simple annualized return as performance. Not a sum of past dollars. Not YTD. Not cumulative |
| First month | No BMV → no % and no annualized $ (same as performance) |
| Chart | **Principal × trailing annualized return** through every month-end. January **year** labels. Not bars. Does not reset in January. Contributions change EMV (the pile), they are not income |
| Table | Month, that month’s gain, annualized gain. Red when negative. **Only** months with a snapshot on the series **or** a non-zero gain |
| Path | `/reports/investment-gain` (unlocked). Hub tile on `/reports` (reuse `/media/report-graphs.png` unless a new media file exists) |
| Empty | Nothing flagged → hint to **Track as investment** on Assets. One snapshot → no line until a second month-end exists |
| Name | **Investment gain over time** — not “income” (losses are first-class; cash yield is later) |

### Projection

Same investment **return** run-rate as net worth, plotted as **annualized dollar income on a static pile** (`value ×` last trailing annualized `%`; **C = 0** on the dash).

| Item | Decision |
|------|----------|
| Engine | Median of last **4** quarter-sums of combined `flowCents`; median of last **4** quarterly simple returns on the **summed** pile. Item-by-item is net worth’s job |
| `C` | **Zero** on this dash — the question is income if you stop contributing. History already has contributions inside EMV |
| Horizon | **5 years**, quarterly steps, chart-only dash. Toggle **off** by default. Independent of the net-worth toggle |
| Join | **Last point on this chart** (month-end carry-forward). Not live Net — there is no second register |
| Gate | **4 consecutive calendar quarters in the trailing 12 months**. A quarter counts if **any** flagged asset has a snapshot in it (a new HSA does not block the 401k) |
| Locked UI | Visible disabled toggle; short title on a wrapper; quarter-by-quarter **hint** (same pattern as net worth, household investments only) |
| Copy | *If you stopped contributing — income on this pile, not a prediction.* |
| Do not | Sum monthly dollar gains (YTD / trailing P&L / cumulative). That sawtooths and is not income on the pile |

The two dashed lines **may disagree** with `/reports/net-worth`: this page sums first; net worth projects each flagged asset then sums, and also freezes houses and pays down loans.

## Releases

One train with investments and net-worth sketch: [`REPORTING_QUEUE.md`](REPORTING_QUEUE.md).

No new tables. The **page, household line, and sketch** ship in release 2 (UI). **Landing copy** in release 3. Needs release 1’s `investment` column.

## Out of scope

- Groups or per-asset dropdown
- Period bar chart
- `incomeCents`, dividends as lines, RMDs, drawdown
- Modified Dietz / IRR
- Replacing performance or value over time
- Using year-end net-worth stamps as gain
