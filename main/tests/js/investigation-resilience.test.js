// Ticket 11: loading and error states, retry, vanished columns, out-of-order responses (store).
const test = require('node:test');
const assert = require('node:assert');
const vm = require('node:vm');

function makeStore({columns = ['name', 'amount', 'qty'], ranges = {}} = {}) {
  const sandbox = require('./load')('project/investigation-store.js');
  const store = vm.runInContext('InvestigationStore', sandbox);
  const health = [], cards = [], steps = [], writes = [], removed = [];
  const ctl = {columns: columns.slice(), ranges: {...ranges}, cells: {}};
  columns.forEach((c, i) => { ctl.cells[c] = i; });
  store._setDeps({
    columns: () => ctl.columns.slice(),
    cellIndexOf: (c) => (c in ctl.cells ? ctl.cells[c] : -1),
    nameOfCellIndex: (i) => ctl.columns.find((c) => ctl.cells[c] === i) || null,
    engineJSON: () => ({mode: 'row-based', facets: []}),
    post: (columnName, engineJson, onDone, onError) => health.push({columnName, onDone, onError}),
    cardPost: (columnName, engineJson, onDone, onError) => cards.push({columnName, onDone, onError}),
    stepPost: (columnName, engineJson, onDone, onError) => steps.push({columnName, engineJson, onDone, onError}),
    readRange: (c) => ctl.ranges[c] || null,
    writeRange: (c, config) => writes.push({c, config}),
    removeRange: (c) => { if (ctl.ranges[c]) { removed.push(c); delete ctl.ranges[c]; } }, // like the bridge: only a facet that exists
  });
  return {store, health, cards, steps, writes, removed, ctl};
}
const ok = (columnName, numeric, blank = 0) => ({
  code: 'ok', columnName, total: numeric + blank, counts: {numeric, blank, wrongType: 0, error: 0},
  stats: {min: 0, max: 1, mean: 0, median: 0}, histogram: {binWidth: 1, min: 0, bins: [numeric]},
  selection: {keptRows: numeric + blank, totalRows: numeric + blank},
});
const snap = (o) => JSON.parse(JSON.stringify(o));

// ---- health: loading and retryable error (story 34) ----

test('health is loading while the first request is pending, and not after', () => {
  const {store, health} = makeStore();
  store.fetchHealth('amount');
  assert.strictEqual(store.getState().healthStatus.amount, 'loading');
  health[0].onDone(ok('amount', 3));
  assert.strictEqual(store.getState().healthStatus.amount, undefined);
});

test('a refetch with data on screen is not shown as loading', () => {
  const {store, health} = makeStore();
  store.fetchHealth('amount');
  health[0].onDone(ok('amount', 3));
  store.fetchHealth('amount');
  assert.strictEqual(store.getState().healthStatus.amount, undefined);
});

test('a failed health request (transport or error code) is an error state and emits', () => {
  const {store, health} = makeStore();
  const events = [];
  store.on('health-updated', (e) => events.push(e.columnName));
  store.fetchHealth('amount');
  health[0].onError();
  assert.strictEqual(store.getState().healthStatus.amount, 'error');
  assert.ok(events.includes('amount'));
  store.fetchHealth('qty');
  health[1].onDone({code: 'error', message: 'boom'});
  assert.strictEqual(store.getState().healthStatus.qty, 'error');
});

test('retryHealth refetches and a success clears the error', () => {
  const {store, health} = makeStore();
  store.fetchHealth('amount');
  health[0].onError();
  store.retryHealth('amount');
  assert.strictEqual(health.length, 2);
  assert.strictEqual(store.getState().healthStatus.amount, 'loading');
  health[1].onDone(ok('amount', 3));
  assert.strictEqual(store.getState().healthStatus.amount, undefined);
  assert.strictEqual(store.getState().health.amount.counts.numeric, 3);
});

test('a stale health failure does not flag an error over newer good data', () => {
  const {store, health} = makeStore();
  store.fetchHealth('amount');
  store.fetchHealth('amount');
  health[1].onDone(ok('amount', 3));
  health[0].onError();
  assert.strictEqual(store.getState().healthStatus.amount, undefined);
  assert.strictEqual(store.getState().health.amount.counts.numeric, 3);
});

test('ensureHealth does not refetch a column in error (retry is explicit)', () => {
  const {store, health} = makeStore();
  store.fetchHealth('amount');
  health[0].onError();
  store.ensureHealth('amount');
  assert.strictEqual(health.length, 1);
});

// ---- cards: retry and refresh failure (story 34) ----

test('a card whose first load fails is in error; retryCard loads again and recovers', () => {
  const {store, cards} = makeStore();
  store.openCard('amount');
  cards[0].onError();
  assert.strictEqual(store.getState().cards[0].status, 'error');
  store.retryCard('amount');
  assert.strictEqual(store.getState().cards[0].status, 'loading');
  assert.strictEqual(cards.length, 2);
  cards[1].onDone(ok('amount', 3));
  assert.strictEqual(store.getState().cards[0].status, 'ready');
  assert.strictEqual(store.getState().cards[0].refreshError, false);
});

test('a failed refresh keeps the data and flags refreshError; retryCard clears it on success', () => {
  const {store, cards} = makeStore();
  store.openCard('amount');
  cards[0].onDone(ok('amount', 3));
  store.onProjectUpdate({cellsChanged: true});
  cards[1].onError();
  const c = store.getState().cards[0];
  assert.strictEqual(c.status, 'ready');
  assert.strictEqual(c.total, 3);
  assert.strictEqual(c.refreshError, true);
  store.retryCard('amount');
  assert.strictEqual(store.getState().cards[0].status, 'ready');
  cards[2].onDone(ok('amount', 4));
  assert.strictEqual(store.getState().cards[0].total, 4);
  assert.strictEqual(store.getState().cards[0].refreshError, false);
});

test('an error-code refresh response also flags refreshError', () => {
  const {store, cards} = makeStore();
  store.openCard('amount');
  cards[0].onDone(ok('amount', 3));
  store.onProjectUpdate({cellsChanged: true});
  cards[1].onDone({code: 'error', message: 'x'});
  assert.strictEqual(store.getState().cards[0].refreshError, true);
});

test('a stale card failure does not flag an error over a newer good response', () => {
  const {store, cards} = makeStore();
  store.openCard('amount');
  cards[0].onDone(ok('amount', 3));
  store.onProjectUpdate({cellsChanged: true});
  store.onProjectUpdate({cellsChanged: true});
  cards[2].onDone(ok('amount', 5));
  cards[1].onError();
  assert.strictEqual(store.getState().cards[0].refreshError, false);
  assert.strictEqual(store.getState().cards[0].total, 5);
});

// ---- breadcrumb: failure and retry ----

const withFilter = () => {
  const m = makeStore({ranges: {amount: {from: 0, to: 1, includeBlank: false, includeWrongType: false, includeError: false}}});
  m.store.openCard('amount');
  m.cards[0].onDone(ok('amount', 3));
  return m;
};

test('a failed breadcrumb request flags breadcrumbError; retryBreadcrumb recovers', () => {
  const {store, steps} = withFilter();
  steps.splice(0).forEach((s) => s.onError());
  assert.strictEqual(store.getState().breadcrumbError, true);
  store.retryBreadcrumb();
  assert.strictEqual(store.getState().breadcrumbError, false);
  steps.splice(0).forEach((s) => s.onDone(ok('amount', 3)));
  assert.ok(store.getState().breadcrumb);
  assert.strictEqual(store.getState().breadcrumbError, false);
});

test('a stale breadcrumb failure does not flag an error over a newer answer', () => {
  const {store, steps} = withFilter();
  const first = steps.splice(0);
  store.refreshBreadcrumb();
  steps.splice(0).forEach((s) => s.onDone(ok('amount', 3)));
  first.forEach((s) => s.onError());
  assert.strictEqual(store.getState().breadcrumbError, false);
  assert.ok(store.getState().breadcrumb);
});

test('out-of-order breadcrumb answers: the older full set never replaces the newer', () => {
  const {store, steps} = withFilter();
  const first = steps.splice(0);
  store.refreshBreadcrumb();
  const second = steps.splice(0);
  second.forEach((s) => s.onDone({...ok('amount', 3), selection: {keptRows: 2, totalRows: 6}}));
  first.forEach((s) => s.onDone({...ok('amount', 3), selection: {keptRows: 5, totalRows: 6}}));
  assert.strictEqual(store.getState().breadcrumb.shown, 2);
});

// ---- vanished columns (story 52) ----

test('a card whose column no longer exists is closed with a notice, its highlight and range facet cleared', () => {
  const m = withFilter();
  const {store, ctl, removed} = m;
  store.setHighlight('amount', 'blank');
  const highlightEvents = [];
  store.on('highlight-changed', (e) => highlightEvents.push(snap(e)));
  ctl.columns = ['name', 'qty'];
  store.onProjectUpdate({modelsChanged: true});
  const s = store.getState();
  assert.strictEqual(s.cards.length, 0);
  assert.deepStrictEqual(snap(s.notices), [{columnName: 'amount', renamedTo: null}]);
  assert.strictEqual(s.highlight.amount, undefined);
  assert.deepStrictEqual(highlightEvents, [{columnName: 'amount', cellClass: null}]);
  assert.deepStrictEqual(removed, ['amount']);
});

test('a vanished column makes no request for it and the other cards keep refreshing', () => {
  const {store, ctl, cards, health} = makeStore();
  store.openCard('amount'); store.openCard('qty');
  cards.splice(0).forEach((c) => c.onDone(ok(c.columnName, 3)));
  ctl.columns = ['name', 'qty'];
  store.onProjectUpdate({modelsChanged: true});
  assert.deepStrictEqual(cards.map((c) => c.columnName), ['qty']);
  assert.ok(health.every((h) => h.columnName !== 'amount'));
});

test('a highlight on a vanished column is cleared even without a card', () => {
  const {store, ctl} = makeStore();
  store.setHighlight('amount', 'wrongType');
  ctl.columns = ['name', 'qty'];
  store.onProjectUpdate({modelsChanged: true});
  assert.strictEqual(store.getState().highlight.amount, undefined);
  assert.strictEqual(store.getState().notices.length, 0);
});

test('a card with no range facet and a vanished column removes no facet', () => {
  const {store, ctl, cards, removed} = makeStore();
  store.openCard('amount');
  cards[0].onDone(ok('amount', 3));
  ctl.columns = ['name', 'qty'];
  store.onProjectUpdate({modelsChanged: true});
  assert.deepStrictEqual(removed, []);
  assert.strictEqual(store.getState().cards.length, 0);
});

test('dismissNotice removes the notice', () => {
  const {store, ctl, cards} = makeStore();
  store.openCard('amount');
  cards[0].onDone(ok('amount', 3));
  ctl.columns = ['name', 'qty'];
  store.onProjectUpdate({modelsChanged: true});
  store.dismissNotice('amount');
  assert.strictEqual(store.getState().notices.length, 0);
});

test('a loading card whose column vanished ignores the late response', () => {
  const {store, ctl, cards} = makeStore();
  store.openCard('amount');
  ctl.columns = ['name', 'qty'];
  store.onProjectUpdate({modelsChanged: true});
  cards[0].onDone(ok('amount', 3));
  assert.strictEqual(store.getState().cards.length, 0);
});

test('reopening a card for a column of the same name clears its old notice', () => {
  const {store, ctl, cards} = makeStore();
  store.openCard('amount');
  cards[0].onDone(ok('amount', 3));
  ctl.columns = ['name', 'qty'];
  store.onProjectUpdate({modelsChanged: true});
  ctl.columns = ['name', 'qty', 'amount'];
  store.openCard('amount');
  assert.strictEqual(store.getState().notices.length, 0);
});

test('a renamed column closes its card with a notice naming the new name, and clears the range facet under the new name', () => {
  const {store, ctl, removed, cards} = makeStore({ranges: {}});
  store.openCard('amount');
  cards[0].onDone(ok('amount', 3));
  // the engine follows a rename: the facet now lives under the new name; cell index is unchanged
  ctl.columns = ['name', 'price', 'qty'];
  ctl.cells = {name: 0, price: 1, qty: 2};
  ctl.ranges = {price: {from: 0, to: 1}};
  store.setHighlight('amount', 'blank');
  store.onProjectUpdate({modelsChanged: true});
  const s = store.getState();
  assert.strictEqual(s.cards.length, 0);
  assert.deepStrictEqual(snap(s.notices), [{columnName: 'amount', renamedTo: 'price'}]);
  assert.deepStrictEqual(removed, ['price']);
  assert.strictEqual(s.highlight.amount, undefined);
});
