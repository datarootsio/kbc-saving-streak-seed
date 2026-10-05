// Pure-logic tests for HealthBar.computeSegments (M4). Run: node --test main/tests/js
const test = require('node:test');
const assert = require('node:assert');
const vm = require('node:vm');
const HealthBar = vm.runInContext('HealthBar', require('./load')('views/data-table/health-bar.js'));

const counts = (numeric, blank = 0, wrongType = 0, error = 0) => ({numeric, blank, wrongType, error});
const sum = (segs) => segs.reduce((a, s) => a + s.widthPct, 0);

test('story 2: widths are proportional to counts and sum to 100', () => {
  const segs = HealthBar.computeSegments(counts(5, 1), 6);
  assert.deepStrictEqual([...segs.map((s) => s.cls)], ['numeric', 'blank']);
  assert.ok(Math.abs(segs[0].widthPct - (500 / 6)) < 1e-9);
  assert.ok(Math.abs(sum(segs) - 100) < 1e-9);
});

test('story 3: a fully numeric column is a single full-width numeric segment', () => {
  const segs = HealthBar.computeSegments(counts(6), 6);
  assert.deepStrictEqual(JSON.parse(JSON.stringify(segs)), [{cls: 'numeric', count: 6, widthPct: 100}]);
});

test('story 4: 1 error in 2418 rows still gets a visible sliver and widths still sum to 100', () => {
  const segs = HealthBar.computeSegments(counts(2417, 0, 0, 1), 2418);
  const err = segs.find((s) => s.cls === 'error');
  assert.strictEqual(err.widthPct, HealthBar.MIN_SEGMENT_PCT);
  assert.ok(Math.abs(sum(segs) - 100) < 1e-9);
  assert.ok(segs.find((s) => s.cls === 'numeric').widthPct < 100);
});

test('zero-count classes produce no segment', () => {
  const segs = HealthBar.computeSegments(counts(3, 0, 3, 0), 6);
  assert.deepStrictEqual([...segs.map((s) => s.cls)], ['numeric', 'wrongType']);
});

test('story 5: a column with no numeric cells has no bar (null)', () => {
  assert.strictEqual(HealthBar.computeSegments(counts(0, 2, 3, 0), 5), null);
  assert.strictEqual(HealthBar.computeSegments(counts(0, 0, 0, 0), 0), null);
});
