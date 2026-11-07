# Investment gain over time

**Status:** Shipped (2026-11-07). Locked in [`PRODUCT.md`](PRODUCT.md) §16. Same train as §14–§15: [`REPORTING_QUEUE.md`](REPORTING_QUEUE.md).

This file is the decided plan for a household **dollar-gain** report of opted-in investments, plus an optional 5-year sketch that reuses the **investment slice** of net-worth projection ([`NET_WORTH_PROJECTION.md`](NET_WORTH_PROJECTION.md)).

Performance answers **percent**. Value over time answers **level**. This report answers **how many dollars the market added or ate**, with contributions stripped out:

```text
all flagged assets ── sum first ── monthly (EMV − BMV − C) ── trailing 12-month $ (unscaled)
                                              └── optional dash: 5 years of median r (C grows the pile, not the gain)
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
| Gain | Each month: `EMV − BMV − C` on the combined series (carry-forward like value over time). Missing `flowCents` = 0. Annualized = **sum** of those monthly gains over trailing `k ≤ 12` months. Do **not** multiply by `12/k` |
| First month | No BMV → no gain that month (same as performance’s “no %”) |
| Chart | **Trailing 12-month dollar gain** (sum, not scaled) through every month-end. Empty months are $0 in the window. January **year** labels. Not bars. Not a running total since inception. Not `sum × (12/k)` |
| Table | Month, that month’s gain, annualized gain. Red when negative. **Only** months with a snapshot on the series **or** a non-zero gain |
| Path | `/reports/investment-gain` (unlocked). Hub tile on `/reports` (reuse `/media/report-graphs.png` unless a new media file exists) |
| Empty | Nothing flagged → hint to **Track as investment** on Assets. One snapshot → no line until a second month-end exists |
| Name | **Investment gain over time** — not “income” (losses are first-class; cash yield is later) |

### Projection

Same investment run-rate as net worth, plotted as **annualized dollar gain** on this one series (`value × r × 4` on the dash — simple, not compound).

| Item | Decision |
|------|----------|
| Engine | Median of last **4** quarter-sums of combined `flowCents`; median of last **4** quarterly simple returns on the **summed** pile. Item-by-item is net worth’s job |
| `C` | Lands on the notional balance so future **dollar** gain can grow. **Never** added to the gain line |
| Horizon | **5 years**, quarterly steps, chart-only dash. Toggle **off** by default. Independent of the net-worth toggle |
| Join | **Last point on this chart** (month-end carry-forward). Not live Net — there is no second register |
| Gate | **4 consecutive calendar quarters in the trailing 12 months**. A quarter counts if **any** flagged asset has a snapshot in it (a new HSA does not block the 401k) |
| Locked UI | Visible disabled toggle; short title on a wrapper; quarter-by-quarter **hint** (same pattern as net worth, household investments only) |
| Copy | *If this pace continued — not a prediction.* |
| Do not | Extend recent monthly dollar gains with a straight line (ignores compounding on new money) |

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
