# Investment gain over time

**Status:** Planned (2026-11-07). Locked in [`PRODUCT.md`](PRODUCT.md) §16. **After** investment performance ([`INVESTMENTS.md`](INVESTMENTS.md) R3). Do **not** start until Brad says so.

This file is the decided plan for a household **dollar-gain** report of opted-in investments, plus an optional 5-year sketch that reuses the **investment slice** of net-worth projection ([`NET_WORTH_PROJECTION.md`](NET_WORTH_PROJECTION.md)).

Performance answers **percent**. Value over time answers **level**. This report answers **how many dollars the market added or ate**, with contributions stripped out:

```text
all flagged assets ── sum first ── monthly (EMV − BMV − C) ── cumulative line
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
| When | After [`INVESTMENTS.md`](INVESTMENTS.md) R3. Reuses `investment` + `flowCents`. No new column |
| Series | **Household only** — every flagged asset, **sum first**. No dropdown. **No groups** |
| Gain | Each month: `EMV − BMV − C` on the combined series (carry-forward like value over time). Missing `flowCents` = 0 |
| First month | No BMV → no gain that month (same as performance’s “no %”) |
| Chart | **Cumulative** dollar line through **every** month-end from first snapshot through the current local month. Empty months are **$0 gain** (flat). January **year** labels. Not bars |
| Table | Month, gain, running total. Red when that month’s gain is negative. **Only** months with a snapshot on the series **or** a non-zero gain — not twelve $0 rows for a quarterly household |
| Path | `/reports/investment-gain` (unlocked). Hub tile on `/reports` (reuse `/media/report-graphs.png` unless a new media file exists) |
| Empty | Nothing flagged → hint to **Track as investment** on Assets. One snapshot → no line until a second month-end exists |
| Name | **Investment gain over time** — not “income” (losses are first-class; cash yield is later) |

### Projection

Same investment run-rate as net worth, plotted as **cumulative gain** on this one series.

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

Brad owns commit/PR unless asked that turn. `docker build --target build` each release. Do **not** close Flyway-strategy or bootstrap-admin To Dos.

1. **History page** — `/reports/investment-gain`, hub tile, chart + sparse table, PRODUCT §16, README / marketing. Cache-bust.
2. **Projection overlay** — toggle, 4-quarter gate, dashed 5 years from the last chart point.

**Verify R1:** two flagged assets, quarterly statements → cumulative line moves by combined `EMV − BMV − C`; a contribution-only quarter with flat values is ~$0 that month; table omits empty middle months; no group dropdown; package succeeds.

**Verify R2:** four household investment quarters unlock the toggle; three do not; turning it on does not write snapshots; a fat vest quarter does not become the forward `C`; package succeeds.

## Out of scope

- Groups or per-asset dropdown
- Period bar chart
- `incomeCents`, dividends as lines, RMDs, drawdown
- Modified Dietz / IRR
- Replacing performance or value over time
- Using year-end net-worth stamps as gain
