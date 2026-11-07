# Reporting queue (one three-release train)

**Status:** Shipped (2026-11-07). Covers PRODUCT §14–§16 in **one** schema / UI / docs split — the same breakdown as asset groups R4–R6.

Do **not** ship these as separate 3-release features. The only new schema in the queue is the investment flag; piggyback, charts, and sketches are client-side on existing rows.

| Release | What |
|---------|------|
| **1 Schema + API** | `V16` `financial_position.investment`. Positions JSON includes `investment`. Reject `true` on a liability. PUT can toggle without a snapshot. **`/assets` and reports unchanged** (everything stays unflagged). |
| **2 UI** | `/assets` Track as investment + **I** + contribution field. Today-as-of snapshot **piggyback** COMPUTED net worth. Net-worth **chart** latest-per-quarter. `/reports/investment-performance` (trailing 12-month annualized %). `/reports/investment-gain` (trailing 12-month $, unscaled). Both **5-year** toggles. Hub tiles. Cache-bust. |
| **3 Export, admin, docs** | Offboard `investment` + `flowCents`. README / marketing. PRODUCT §14–§16 shipped. No extra admin column. Do **not** close Flyway-strategy, bootstrap-admin, or credit-cards To Dos. |

Product rules stay in:

- [`INVESTMENTS.md`](INVESTMENTS.md) / §14
- [`NET_WORTH_PROJECTION.md`](NET_WORTH_PROJECTION.md) / §15
- [`INVESTMENT_GAIN.md`](INVESTMENT_GAIN.md) / §16

**Verify R1:** default `investment` false; liability rejected; PUT toggle without snapshot; `GET /positions` includes `investment`; `docker build --target build`. ✅ (2026-11-07)

**Verify R2:** flag a 401k and enter quarterly value + contribution; unflagged car has no contribution field; Net ignores flows; today snapshot appends COMPUTED when register is stale or already today; past as-of does not; net-worth chart one point per quarter; performance chart is trailing 12-month annualized %, ~0 when only contribution changed; gain chart is trailing 12-month household `EMV − BMV − C` (not scaled up); four quarters unlock both sketches; house marks do not unlock net-worth sketch; package succeeds. ✅ package (2026-11-07; `docker build --target build`). Cluster UI still Brad.

**Verify R3:** offboard JSON has `investment` and `flowCents`; landing/README mention performance, gain, and the net-worth sketch; admin `/users` still has no Investments column; package succeeds. ✅ package (2026-11-07; `docker build --target build`).

Brad owns commit/PR unless asked that turn.
