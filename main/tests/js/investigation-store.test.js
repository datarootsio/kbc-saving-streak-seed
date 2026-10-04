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
