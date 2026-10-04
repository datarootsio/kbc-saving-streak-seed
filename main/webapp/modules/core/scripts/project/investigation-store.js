/*
 * M3 InvestigationStore: single source of UI state for the numeric investigation.
 * No DOM. Talks to the server through an injectable transport.
 */
var InvestigationStore = (function() {
  var state = { health: {} };
  var handlers = {};
  var deps = null;

  function emit(eventName, payload) {
    (handlers[eventName] || []).slice().forEach(function(h) { h(payload); });
  }

  var store = {};

  store.on = function(eventName, handler) {
    (handlers[eventName] = handlers[eventName] || []).push(handler);
  };

  store._setDeps = function(d) { deps = d; };

  // ---- health ----

  var seq = {};        // columnName -> sequence number of the latest request
  var inFlight = {};   // columnName -> true while the latest request is pending
  var REFETCH_FLAGS = ['everythingChanged', 'modelsChanged', 'rowsChanged', 'rowMetadataChanged',
                       'cellsChanged', 'engineChanged', 'columnStatsChanged'];

  store.fetchHealth = function(columnName) {
    var mine = seq[columnName] = (seq[columnName] || 0) + 1;
    inFlight[columnName] = true;
    var isLatest = function() { return seq[columnName] === mine; };
    deps.post(columnName, deps.engineJSON(), function(data) {
      if (!isLatest()) { return; }
      inFlight[columnName] = false;
      if (data.code !== 'ok') {
        delete state.health[columnName];
      } else {
        state.health[columnName] = { total: data.total, counts: data.counts };
      }
      emit('health-updated', { columnName: columnName });
    }, function() {
      if (isLatest()) { inFlight[columnName] = false; }
    });
  };

  store.ensureHealth = function(columnName) {
    if (!state.health[columnName] && !inFlight[columnName]) {
      store.fetchHealth(columnName);
    }
  };

  store.refreshAll = function() {
    deps.columns().forEach(store.fetchHealth);
  };

  // Called after every Refine.update (edit, undo/redo, facet change).
  store.onProjectUpdate = function(options) {
    if (REFETCH_FLAGS.some(function(f) { return options && options[f]; })) {
      store.refreshAll();
    }
  };

  store.getState = function() {
    return JSON.parse(JSON.stringify(state));
  };

  // ---- browser wiring (not exercised by the node unit tests) ----

  store._setDeps({
    columns: function() {
      return theProject.columnModel.columns.map(function(c) { return c.name; });
    },
    engineJSON: function() { return ui.browsingEngine.getJSON(); },
    post: function(columnName, engineJson, onDone, onError) {
      Refine.postCSRF(
        "command/core/get-numeric-health?" + $.param({ project: theProject.id }),
        { columnName: columnName, engine: JSON.stringify(engineJson), bins: 1 },
        onDone,
        "json",
        onError
      );
    }
  });

  if (typeof Refine !== 'undefined' && Refine.registerUpdateFunction) {
    Refine.registerUpdateFunction(store.onProjectUpdate);
  }

  return store;
})();
