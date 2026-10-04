// Parity with the server's NumericHealthCalculator.classify (error > blank > numeric > wrongType).
// Cell JSON shape: {v: value} or {e: "error message"}; a missing cell is null/undefined.
const test = require('node:test');
const assert = require('node:assert');
const vm = require('node:vm');
const CellHighlighter = vm.runInContext('CellHighlighter', require('./load')('views/data-table/cell-highlighter.js'));

const cls = (cell) => CellHighlighter.classFor(cell);
const matches = (cell, c) => CellHighlighter.isHighlighted(cell, c);

test('numbers are numeric, including 0, negatives and decimals', () => {
  for (const v of [0, -1, 2.5, -0.5, 1e21]) assert.strictEqual(cls({v}), 'numeric', String(v));
});

test('null, empty and whitespace-only strings, and missing cells are blank', () => {
  for (const c of [{v: null}, {v: ''}, {v: '   '}, {v: '\t\n'}, null, undefined, {}]) {
    assert.strictEqual(cls(c), 'blank', JSON.stringify(c));
  }
});

test('error cells are error, and win over everything else', () => {
  assert.strictEqual(cls({e: 'bad'}), 'error');
  assert.strictEqual(cls({e: 'bad', v: 5}), 'error');
  assert.strictEqual(cls({e: 'bad', v: null}), 'error');
  assert.strictEqual(cls({e: ''}), 'error');
});

test('text, numeric-looking strings, NaN/Infinity (string or number) and booleans are wrongType', () => {
  for (const v of ['abc', '12', 'NaN', 'Infinity', '-Infinity', true, false, NaN, Infinity, -Infinity]) {
    assert.strictEqual(cls({v}), 'wrongType', String(v));
  }
});

test('isHighlighted(cell, wanted) returns true only for the wanted class, false when none is active', () => {
  assert.strictEqual(matches({v: null}, 'blank'), true);
  assert.strictEqual(matches({v: 1}, 'blank'), false);
  assert.strictEqual(matches({v: 1}, 'numeric'), true);
  assert.strictEqual(matches({v: 1}, null), false);
  assert.strictEqual(matches({v: 1}, undefined), false);
});
