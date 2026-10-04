// Pure-logic tests for HealthPopover.percentages (M4). Run: node --test main/tests/js/*.test.js
const test = require('node:test');
const assert = require('node:assert');
const vm = require('node:vm');
const HealthPopover = vm.runInContext('HealthPopover', require('./load')('views/data-table/health-popover.js'));

const counts = (numeric, blank = 0, wrongType = 0, error = 0) => ({numeric, blank, wrongType, error});
const pct = (c, total) => JSON.parse(JSON.stringify(HealthPopover.percentages(c, total)));
const sum = (p) => Object.values(p).reduce((a, b) => a + b, 0);

test('story 10: sample amount, 5 numeric and 1 blank, is 83% / 17%', () => {
  assert.deepStrictEqual(pct(counts(5, 1), 6), {numeric: 83, blank: 17, wrongType: 0, error: 0});
});

test('story 11: three equal classes are 34 / 33 / 33 and total 100', () => {
  const p = pct(counts(2, 2, 2), 6);
  assert.deepStrictEqual(p, {numeric: 34, blank: 33, wrongType: 33, error: 0});
});

test('a single class is 100', () => {
  assert.deepStrictEqual(pct(counts(7), 7), {numeric: 100, blank: 0, wrongType: 0, error: 0});
});

test('the extra points go to the largest remainders, not the largest counts', () => {
  // 1/3 numeric, 1/3 blank, 1/3 ... use 4 classes: 50.5, 24.75, 24.75, 0 -> floors 50,24,24 (98), extras to .75 .75
  const p = pct(counts(202, 99, 99), 400);
  assert.deepStrictEqual(p, {numeric: 50, blank: 25, wrongType: 25, error: 0});
});

test('a rare class that rounds to 0 stays 0 and the total is still 100', () => {
  const p = pct(counts(2417, 0, 0, 1), 2418);
  assert.strictEqual(sum(p), 100);
  assert.strictEqual(p.error, 0);
});

test('percentages always total 100 across many splits', () => {
  for (let a = 0; a < 12; a++) for (let b = 0; b < 12; b++) for (let c = 0; c < 12; c++) {
    const t = a + b + c + 5;
    assert.strictEqual(sum(pct(counts(a, b, c, 5), t)), 100);
  }
});

test('an empty column has all zero percentages', () => {
  assert.deepStrictEqual(pct(counts(0), 0), {numeric: 0, blank: 0, wrongType: 0, error: 0});
});
