/**
 * Quarterly run-rate helpers for net-worth and investment-gain sketches (PRODUCT.md §15–§16).
 */
window.AcruetProjection = (() => {
  const STEPS = 20;
  const TITLE_LOCKED = 'Needs 4 quarters of investment or loan history.';

  function localIsoDate(date = new Date()) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  function parseIso(isoDate) {
    const [year, month, day] = String(isoDate).split('-').map(Number);
    return { year, month, day };
  }

  function monthEndIso(year, month) {
    return localIsoDate(new Date(year, month, 0));
  }

  function pad2(value) {
    return String(value).padStart(2, '0');
  }

  function iso(year, month, day) {
    return `${year}-${pad2(month)}-${pad2(day)}`;
  }

  function trailingQuarters(todayIso = localIsoDate()) {
    const { year, month } = parseIso(todayIso);
    let q = Math.floor((month - 1) / 3);
    let y = year;
    const quarters = [];
    for (let index = 0; index < 4; index += 1) {
      const startMonth = q * 3 + 1;
      quarters.unshift({
        id: `${y}-Q${q + 1}`,
        label: `${shortMonth(startMonth)} ${y}`,
        start: iso(y, startMonth, 1),
        end: monthEndIso(y, startMonth + 2),
      });
      q -= 1;
      if (q < 0) {
        q = 3;
        y -= 1;
      }
    }
    return quarters;
  }

  function shortMonth(month) {
    return ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][month - 1];
  }

  function inQuarter(asOfDate, quarter) {
    return asOfDate >= quarter.start && asOfDate <= quarter.end;
  }

  function latestOnOrBefore(snapshots, asOfDate) {
    let latest = null;
    snapshots.forEach((snapshot) => {
      if (snapshot.asOfDate <= asOfDate) {
        latest = snapshot;
      }
    });
    return latest;
  }

  function flowInQuarter(snapshots, quarter) {
    return snapshots.reduce((sum, snapshot) => {
      if (inQuarter(snapshot.asOfDate, quarter)) {
        return sum + Number(snapshot.flowCents || 0);
      }
      return sum;
    }, 0);
  }

  function hasSnapshotInQuarter(snapshots, quarter) {
    return snapshots.some((snapshot) => inQuarter(snapshot.asOfDate, quarter));
  }

  function median(values) {
    const sorted = values.filter((value) => Number.isFinite(value)).slice().sort((left, right) => left - right);
    if (!sorted.length) {
      return 0;
    }
    const mid = Math.floor(sorted.length / 2);
    if (sorted.length % 2 === 0) {
      return Math.round((sorted[mid - 1] + sorted[mid]) / 2);
    }
    return sorted[mid];
  }

  function medianRate(values) {
    const sorted = values.filter((value) => Number.isFinite(value)).slice().sort((left, right) => left - right);
    if (!sorted.length) {
      return 0;
    }
    const mid = Math.floor(sorted.length / 2);
    if (sorted.length % 2 === 0) {
      return (sorted[mid - 1] + sorted[mid]) / 2;
    }
    return sorted[mid];
  }

  function simpleReturn(bmv, emv, flow) {
    if (!(bmv > 0)) {
      return null;
    }
    return (emv - bmv - flow) / bmv;
  }

  function investmentQuarters(snapshots, quarters) {
    const previousEnd = (index) => (index === 0
      ? localIsoDate(new Date(parseIso(quarters[0].start).year, parseIso(quarters[0].start).month - 1, 0))
      : quarters[index - 1].end);
    return quarters.map((quarter, index) => {
      const begin = latestOnOrBefore(snapshots, previousEnd(index));
      const end = latestOnOrBefore(snapshots, quarter.end);
      const flow = flowInQuarter(snapshots, quarter);
      const bmv = begin ? begin.cents : null;
      const emv = end ? end.cents : null;
      return {
        quarter,
        present: hasSnapshotInQuarter(snapshots, quarter),
        bmv,
        emv,
        flow,
        r: bmv != null && emv != null ? simpleReturn(bmv, emv, flow) : null,
        delta: bmv != null && emv != null ? emv - bmv : null,
      };
    });
  }

  function sumOnOrBefore(lists, asOfDate) {
    let sum = 0;
    let any = false;
    lists.forEach((list) => {
      const latest = latestOnOrBefore(list, asOfDate);
      if (latest) {
        any = true;
        sum += latest.cents;
      }
    });
    return any ? sum : null;
  }

  function sumFlow(lists, quarter) {
    return lists.reduce((total, list) => total + flowInQuarter(list, quarter), 0);
  }

  function investmentRunRateFromLists(lists, quarters = trailingQuarters()) {
    const previousEnd = (index) => (index === 0
      ? localIsoDate(new Date(parseIso(quarters[0].start).year, parseIso(quarters[0].start).month - 1, 0))
      : quarters[index - 1].end);
    const rows = quarters.map((quarter, index) => {
      const bmv = sumOnOrBefore(lists, previousEnd(index));
      const emv = sumOnOrBefore(lists, quarter.end);
      const flow = sumFlow(lists, quarter);
      return {
        quarter,
        present: lists.some((list) => hasSnapshotInQuarter(list, quarter)),
        bmv,
        emv,
        flow,
        r: bmv != null && emv != null ? simpleReturn(bmv, emv, flow) : null,
      };
    });
    const last = rows[rows.length - 1];
    return {
      rows,
      c: median(rows.map((row) => row.flow)),
      r: medianRate(rows.map((row) => row.r).filter((value) => value != null)),
      lastEmv: last && last.emv != null ? last.emv : 0,
    };
  }

  function investmentRunRate(snapshots, quarters = trailingQuarters()) {
    const rows = investmentQuarters(snapshots, quarters);
    const flows = rows.map((row) => row.flow);
    const rates = rows.map((row) => row.r).filter((value) => value != null);
    const last = rows[rows.length - 1];
    return {
      rows,
      c: median(flows),
      r: medianRate(rates),
      lastEmv: last && last.emv != null ? last.emv : 0,
    };
  }

  function liabilityRunRate(snapshots, quarters = trailingQuarters()) {
    const rows = investmentQuarters(snapshots, quarters);
    const deltas = rows.map((row) => row.delta).filter((value) => value != null);
    const last = rows[rows.length - 1];
    const delta = median(deltas);
    return {
      rows,
      delta,
      payingDown: delta < 0,
      lastEmv: last && last.emv != null ? last.emv : 0,
    };
  }

  function allQuartersPresent(rows) {
    return rows.every((row) => row.present);
  }

  function gateForItems(items, quarters = trailingQuarters()) {
    let best = null;
    items.forEach((item) => {
      const rows = investmentQuarters(item.snapshots || [], quarters);
      const moving = item.investment || (item.kind === 'LIABILITY' && liabilityRunRate(item.snapshots || [], quarters).payingDown);
      if (!moving) {
        return;
      }
      const presentCount = rows.filter((row) => row.present).length;
      const candidate = {
        item,
        rows,
        presentCount,
        ready: presentCount === quarters.length,
      };
      if (!best || candidate.presentCount > best.presentCount) {
        best = candidate;
      }
    });
    return {
      quarters,
      ready: Boolean(best && best.ready),
      best,
    };
  }

  function householdInvestmentGate(flaggedItems, quarters = trailingQuarters()) {
    const present = quarters.map((quarter) =>
      flaggedItems.some((item) => hasSnapshotInQuarter(item.snapshots || [], quarter)));
    return {
      quarters,
      present,
      ready: present.every(Boolean),
    };
  }

  function scoreboardHtml(quarters, presentById, closestName) {
    const parts = quarters.map((quarter, index) => {
      const ok = presentById[index];
      return `${quarter.label} ${ok ? 'ok' : 'missing'}`;
    });
    const extra = closestName ? ` Closest: ${closestName}.` : '';
    return `Last 4 quarters: ${parts.join(' · ')}.${extra} Add those as-of dates on an investment (value + net contribution) or a loan you are paying down. Quarterly statements are enough. House, car, envelopes, and year-end net worth do not count.`;
  }

  function projectInvestmentValue(startCents, contribution, rate, steps = STEPS) {
    const points = [];
    let value = startCents;
    let date = quarterEndAfter(localIsoDate());
    for (let index = 0; index < steps; index += 1) {
      const gain = Math.round(value * rate);
      value = Math.max(0, value + contribution + gain);
      points.push({ asOfDate: date, cents: value, gainCents: gain });
      date = nextQuarterEnd(date);
    }
    return points;
  }

  function projectGain(startCents, startCumulative, contribution, rate, steps = STEPS) {
    const points = [];
    let value = startCents;
    let cumulative = startCumulative;
    let date = quarterEndAfter(localIsoDate());
    for (let index = 0; index < steps; index += 1) {
      const gain = Math.round(value * rate);
      value = Math.max(0, value + contribution + gain);
      cumulative += gain;
      points.push({ asOfDate: date, cents: cumulative, periodGain: gain });
      date = nextQuarterEnd(date);
    }
    return points;
  }

  function quarterEndAfter(todayIso) {
    const quarters = trailingQuarters(todayIso);
    const current = quarters[quarters.length - 1];
    if (todayIso < current.end) {
      return current.end;
    }
    return nextQuarterEnd(current.end);
  }

  function nextQuarterEnd(isoDate) {
    const { year, month } = parseIso(isoDate);
    const q = Math.floor((month - 1) / 3);
    if (q === 3) {
      return monthEndIso(year + 1, 3);
    }
    return monthEndIso(year, (q + 1) * 3 + 3);
  }

  function futureQuarterEnds(fromIso, steps = STEPS) {
    const points = [];
    let date = quarterEndAfter(fromIso);
    for (let index = 0; index < steps; index += 1) {
      points.push(date);
      date = nextQuarterEnd(date);
    }
    return points;
  }

  function asOfTimestamp(asOfDate) {
    return Date.parse(`${asOfDate}T12:00:00Z`);
  }

  return {
    STEPS,
    TITLE_LOCKED,
    localIsoDate,
    trailingQuarters,
    hasSnapshotInQuarter,
    investmentRunRate,
    investmentRunRateFromLists,
    liabilityRunRate,
    allQuartersPresent,
    gateForItems,
    householdInvestmentGate,
    scoreboardHtml,
    projectInvestmentValue,
    projectGain,
    futureQuarterEnds,
    asOfTimestamp,
    latestOnOrBefore,
    median,
    medianRate,
  };
})();
