// Tests for the highlight section of InvestigationStore (M3).
const test = require('node:test');
const assert = require('node:assert');
const vm = require('node:vm');

const makeStore = () => vm.runInContext('InvestigationStore', require('./load')('project/investigation-store.js'));

test('highlight starts empty', () => {
  assert.deepStrictEqual(JSON.parse(JSON.stringify(makeStore().getState().highlight)), {});
});

test('setHighlight records the class for the column and emits highlight-changed', () => {
  const store = makeStore();
  const events = [];
  store.on('highlight-changed', (e) => events.push(JSON.parse(JSON.stringify(e))));
  store.setHighlight('amount', 'blank');
  assert.strictEqual(store.getState().highlight.amount, 'blank');
  assert.deepStrictEqual(events, [{columnName: 'amount', cellClass: 'blank'}]);
});

test('one active class per column: a second class replaces the first', () => {
  const store = makeStore();
  store.setHighlight('amount', 'blank');
  store.setHighlight('amount', 'wrongType');
  assert.strictEqual(store.getState().highlight.amount, 'wrongType');
});

test('null clears the column highlight and emits', () => {
  const store = makeStore();
  const events = [];
  store.setHighlight('amount', 'blank');
  store.on('highlight-changed', (e) => events.push(JSON.parse(JSON.stringify(e))));
  store.setHighlight('amount', null);
  assert.strictEqual('amount' in store.getState().highlight, false);
  assert.deepStrictEqual(events, [{columnName: 'amount', cellClass: null}]);
});

test('columns are independent', () => {
  const store = makeStore();
  store.setHighlight('a', 'blank');
  store.setHighlight('b', 'error');
  store.setHighlight('a', null);
  assert.deepStrictEqual(JSON.parse(JSON.stringify(store.getState().highlight)), {b: 'error'});
});

test('setting the same value again does not emit', () => {
  const store = makeStore();
  let n = 0;
  store.setHighlight('amount', 'blank');
  store.on('highlight-changed', () => n++);
  store.setHighlight('amount', 'blank');
  store.setHighlight('other', null);
  assert.strictEqual(n, 0);
});

test('an unknown class is rejected and leaves the state alone', () => {
  const store = makeStore();
  store.setHighlight('amount', 'blank');
  store.setHighlight('amount', 'bogus');
  assert.strictEqual(store.getState().highlight.amount, 'blank');
});

test('getState returns a snapshot: mutating it does not change the store', () => {
  const store = makeStore();
  store.setHighlight('amount', 'blank');
  store.getState().highlight.amount = 'error';
  assert.strictEqual(store.getState().highlight.amount, 'blank');
});

test('story 19: highlight is not touched by project updates (re-render keeps it)', () => {
  const store = makeStore();
  store.setHighlight('amount', 'blank');
  store._setDeps({columns: () => ['amount'], engineJSON: () => ({}), post: () => {}});
  store.onProjectUpdate({cellsChanged: true});
  assert.strictEqual(store.getState().highlight.amount, 'blank');
});
