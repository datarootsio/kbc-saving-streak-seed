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
    var snapshot = JSON.parse(JSON.stringify(state));
    snapshot.cards = cards.map(function(c) {
      return { columnName: c.columnName, status: c.status, stats: c.stats, counts: c.counts, histogram: c.histogram, total: c.total };
    });
    snapshot.highlight = JSON.parse(JSON.stringify(highlight));
    return snapshot;
  };

  // ---- cards ----
  var cards = []; // [{columnName, status: "loading"|"ready"|"error", stats, counts, histogram, total}]

  var findCard = function(columnName) {
    for (var i = 0; i < cards.length; i++) {
      if (cards[i].columnName === columnName) {
        return cards[i];
      }
    }
    return null;
  };

  var fetchCard = function(card) {
    card.status = "loading";
    Refine.postCSRF(
      "command/core/get-numeric-health",
      {
        project: theProject.id,
        columnName: card.columnName,
        bins: 20,
        engine: JSON.stringify(ui.browsingEngine.getJSON(true))
      },
      function(data) {
        if (cards.indexOf(card) < 0) {
          return; // closed while loading
        }
        if (data.code === "ok") {
          card.status = "ready";
          card.stats = data.stats;
          card.counts = data.counts;
          card.histogram = data.histogram;
          card.total = data.total;
        } else {
          card.status = "error";
        }
        emit("cards-changed", store.getState());
      },
      "json",
      function() {
        if (cards.indexOf(card) >= 0) {
          card.status = "error";
          emit("cards-changed", store.getState());
        }
      }
    );
  };

  store.openCard = function(columnName) {
    if (findCard(columnName)) {
      return;
    }
    var card = { columnName: columnName, status: "loading", stats: null, counts: null, histogram: null, total: null };
    cards.push(card);
    emit("cards-changed", store.getState());
    fetchCard(card);
  };

  store.closeCard = function(columnName) {
    var card = findCard(columnName);
    if (!card) {
      return;
    }
    cards.splice(cards.indexOf(card), 1);
    emit("cards-changed", store.getState());
  };

  // ---- end cards ----

  // ---- highlight ----
  // One active cell class per column; in memory only, so a reload clears it.
  var highlight = {}; // columnName -> 'numeric' | 'blank' | 'wrongType' | 'error'
  var HIGHLIGHT_CLASSES = ['numeric', 'blank', 'wrongType', 'error'];

  store.setHighlight = function(columnName, cellClass) {
    if (cellClass !== null && HIGHLIGHT_CLASSES.indexOf(cellClass) < 0) {
      return;
    }
    if ((highlight[columnName] || null) === cellClass) {
      return;
    }
    if (cellClass === null) {
      delete highlight[columnName];
    } else {
      highlight[columnName] = cellClass;
    }
    emit('highlight-changed', { columnName: columnName, cellClass: cellClass });
  };

  store.getHighlight = function(columnName) {
    return highlight[columnName] || null;
  };

  // ---- end highlight ----

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
