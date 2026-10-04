// Tests for the health section of InvestigationStore (M3), with a stubbed transport.
const test = require('node:test');
const assert = require('node:assert');
const vm = require('node:vm');

function makeStore({columns = ['name', 'amount'], engine = {facets: [], mode: 'row-based'}} = {}) {
  const sandbox = require('./load')('project/investigation-store.js');
  const store = vm.runInContext('InvestigationStore', sandbox);
  const calls = [];
  store._setDeps({
    columns: () => columns.slice(),
    engineJSON: () => engine,
    post: (columnName, engineJson, onDone, onError) => calls.push({columnName, engineJson, onDone, onError}),
  });
  return {store, calls};
}
const payload = (columnName, numeric, blank = 0) => ({
  code: 'ok', columnName, total: numeric + blank, counts: {numeric, blank, wrongType: 0, error: 0},
});

test('fetchHealth sends column and engine, stores the result and emits health-updated', () => {
  const {store, calls} = makeStore();
  const events = [];
  store.on('health-updated', (e) => events.push(e.columnName));
  store.fetchHealth('amount');
  assert.strictEqual(calls.length, 1);
  assert.strictEqual(calls[0].columnName, 'amount');
  assert.strictEqual(calls[0].engineJson.mode, 'row-based');
  calls[0].onDone(payload('amount', 5, 1));
  assert.strictEqual(store.getState().health.amount.total, 6);
  assert.strictEqual(store.getState().health.amount.counts.numeric, 5);
  assert.deepStrictEqual([...events], ['amount']);
});

test('story 7/8: an update that changes the engine or data refetches every column', () => {
  const {store, calls} = makeStore();
  store.onProjectUpdate({engineChanged: true});
  assert.deepStrictEqual(calls.map((c) => c.columnName), ['name', 'amount']);
});

test('an update that cannot change counts (e.g. only history) does not refetch', () => {
  const {store, calls} = makeStore();
  store.onProjectUpdate({});
  assert.strictEqual(calls.length, 0);
});

test('story 54: a stale response never overwrites a newer one', () => {
  const {store, calls} = makeStore();
  store.fetchHealth('amount');
  store.fetchHealth('amount');
  calls[1].onDone(payload('amount', 5, 1));
  calls[0].onDone(payload('amount', 1, 0));
  assert.strictEqual(store.getState().health.amount.total, 6);
});

test('ensureHealth fetches once when there is no data and nothing in flight', () => {
  const {store, calls} = makeStore();
  store.ensureHealth('amount');
  store.ensureHealth('amount');
  assert.strictEqual(calls.length, 1);
  calls[0].onDone(payload('amount', 5, 1));
  store.ensureHealth('amount');
  assert.strictEqual(calls.length, 1);
});

test('a failed request leaves no data and does not throw', () => {
  const {store, calls} = makeStore();
  store.fetchHealth('amount');
  calls[0].onDone({code: 'error', message: 'boom'});
  assert.strictEqual(store.getState().health.amount, undefined);
});

test('getState returns a snapshot that cannot mutate the store', () => {
  const {store, calls} = makeStore();
  store.fetchHealth('amount');
  calls[0].onDone(payload('amount', 5, 1));
  store.getState().health.amount.counts.numeric = 99;
  assert.strictEqual(store.getState().health.amount.counts.numeric, 5);
});

// ---- cards: range selection (ticket 07) ----

function makeCardStore({range = null} = {}) {
  const sandbox = require('./load')('project/investigation-store.js');
  const store = vm.runInContext('InvestigationStore', sandbox);
  const cardCalls = [];
  const writes = [];
  const state = {range};
  store._setDeps({
    columns: () => ['name', 'amount'],
    engineJSON: () => ({facets: [], mode: 'row-based'}),
    post: () => {},
    cardPost: (columnName, engineJson, onDone, onError) => cardCalls.push({columnName, engineJson, onDone, onError}),
    readRange: () => state.range,
    writeRange: (columnName, config) => writes.push({columnName, config}),
  });
  return {store, cardCalls, writes, state};
}
const cardPayload = (kept, total = 6) => ({
  code: 'ok', columnName: 'amount', total, counts: {numeric: 5, blank: 1, wrongType: 0, error: 0},
  stats: {min: -1, max: 2.5, mean: 0.5, median: 0},
  histogram: {binWidth: 0.175, min: -1, bins: [1, 4]},
  selection: {keptRows: kept, totalRows: total},
});

test('openCard stores selection and no range when the column has no range facet', () => {
  const {store, cardCalls} = makeCardStore();
  store.openCard('amount');
  cardCalls[0].onDone(cardPayload(6));
  const card = store.getState().cards[0];
  assert.deepStrictEqual({...card.selection}, {keptRows: 6, totalRows: 6});
  assert.strictEqual(card.range, null);
});

test('openCard adopts an existing range facet on the column instead of creating one (no duplicate)', () => {
  const {store, cardCalls, writes} = makeCardStore({range: {from: 0, to: 1, includeBlank: false, includeWrongType: false, includeError: false}});
  store.openCard('amount');
  cardCalls[0].onDone(cardPayload(3));
  assert.strictEqual(store.getState().cards[0].range.from, 0);
  assert.strictEqual(store.getState().cards[0].range.to, 1);
  assert.strictEqual(writes.length, 0);
});

test('setRange writes a core range facet config on the column that excludes problem rows by default', () => {
  const {store, cardCalls, writes} = makeCardStore();
  store.openCard('amount');
  cardCalls[0].onDone(cardPayload(6));
  store.setRange('amount', {from: 0, to: 1});
  assert.strictEqual(writes.length, 1);
  const c = writes[0].config;
  assert.strictEqual(writes[0].columnName, 'amount');
  assert.strictEqual(c.columnName, 'amount');
  assert.strictEqual(c.name, 'amount');
  assert.strictEqual(c.expression, 'value');
  assert.strictEqual(c.from, 0);
  // the facet's upper bound is exclusive, the card's is inclusive
  assert.ok(c.to > 1 && c.to < 1.001, 'to=' + c.to);
  assert.strictEqual(c.selectNumeric, true);
  assert.strictEqual(c.selectNonNumeric, false);
  assert.strictEqual(c.selectBlank, false);
  assert.strictEqual(c.selectError, false);
});

test('setRange honours the include flags', () => {
  const {store, writes} = makeCardStore();
  store.setRange('amount', {from: 0, to: 1, includeBlank: true, includeWrongType: true, includeError: true});
  const c = writes[0].config;
  assert.deepStrictEqual([c.selectBlank, c.selectNonNumeric, c.selectError], [true, true, true]);
});

test('resetRange asks the engine to clear the column range', () => {
  const {store, writes} = makeCardStore();
  store.resetRange('amount');
  assert.deepStrictEqual({...writes[0]}, {columnName: 'amount', config: null});
});

test('an engine change refreshes selection and range but keeps the histogram', () => {
  const {store, cardCalls, state} = makeCardStore();
  store.openCard('amount');
  cardCalls[0].onDone(cardPayload(6));
  state.range = {from: 0, to: 1, includeBlank: false, includeWrongType: false, includeError: false};
  store.onProjectUpdate({engineChanged: true});
  const refresh = cardCalls[cardCalls.length - 1];
  assert.strictEqual(cardCalls.length, 2);
  const shrunk = cardPayload(3);
  shrunk.histogram = {binWidth: 1, min: 0, bins: [2]};
  refresh.onDone(shrunk);
  const card = store.getState().cards[0];
  assert.strictEqual(card.selection.keptRows, 3);
  assert.strictEqual(card.range.to, 1);
  assert.deepStrictEqual([...card.histogram.bins], [1, 4]);
});

test('a stale selection response never overwrites a newer one', () => {
  const {store, cardCalls} = makeCardStore();
  store.openCard('amount');
  cardCalls[0].onDone(cardPayload(6));
  store.onProjectUpdate({engineChanged: true});
  store.onProjectUpdate({engineChanged: true});
  cardCalls[2].onDone(cardPayload(3));
  cardCalls[1].onDone(cardPayload(5));
  assert.strictEqual(store.getState().cards[0].selection.keptRows, 3);
});

test('a data-only update with no open card does not call the card endpoint', () => {
  const {store, cardCalls} = makeCardStore();
  store.onProjectUpdate({engineChanged: true});
  assert.strictEqual(cardCalls.length, 0);
});

// ---- cards: problem-row toggles (ticket 09) ----

test('setProblemRows on a card with a range rewrites the facet with the same bounds and the new flags', () => {
  const {store, cardCalls, writes, state} = makeCardStore();
  store.openCard('amount');
  cardCalls[0].onDone(cardPayload(6));
  state.range = {from: 0, to: 1, includeBlank: false, includeWrongType: false, includeError: false};
  store.setRange('amount', {from: 0, to: 1});
  store.refreshCardSelections();
  cardCalls[1].onDone(cardPayload(3));
  writes.length = 0;
  store.setProblemRows('amount', {blank: true});
  assert.strictEqual(writes.length, 1);
  const c = writes[0].config;
  assert.deepStrictEqual([c.selectBlank, c.selectNonNumeric, c.selectError], [true, false, false]);
  assert.strictEqual(c.from, 0);
  assert.ok(Math.abs(c.to - 1) < 1e-6, 'upper bound is not nudged up a second time');
  assert.strictEqual(c.selectNumeric, true);
});

test('setProblemRows keeps the other flags', () => {
  const {store, cardCalls, writes, state} = makeCardStore({range: {from: 0, to: 1, includeBlank: true, includeWrongType: false, includeError: true}});
  store.openCard('amount');
  cardCalls[0].onDone(cardPayload(4));
  store.setProblemRows('amount', {wrongType: true});
  const c = writes[0].config;
  assert.deepStrictEqual([c.selectBlank, c.selectNonNumeric, c.selectError], [true, true, true]);
});

test('setProblemRows without a range writes nothing and the next setRange uses the choice', () => {
  const {store, cardCalls, writes} = makeCardStore();
  store.openCard('amount');
  cardCalls[0].onDone(cardPayload(6));
  store.setProblemRows('amount', {blank: true});
  assert.strictEqual(writes.length, 0);
  assert.deepStrictEqual({...store.getState().cards[0].problemRows}, {blank: true, wrongType: false, error: false});
  store.setRange('amount', {from: 0, to: 1});
  assert.strictEqual(writes[0].config.selectBlank, true);
});

test('problem-row defaults are all off, and follow the facet once there is a range', () => {
  const {store, cardCalls} = makeCardStore({range: {from: 0, to: 1, includeBlank: false, includeWrongType: true, includeError: false}});
  store.openCard('amount');
  assert.deepStrictEqual({...store.getState().cards[0].problemRows}, {blank: false, wrongType: false, error: false});
  cardCalls[0].onDone(cardPayload(4));
  assert.deepStrictEqual({...store.getState().cards[0].problemRows}, {blank: false, wrongType: true, error: false});
});
