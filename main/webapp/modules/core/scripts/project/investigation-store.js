/*
 * M3 InvestigationStore: single source of UI state for the numeric investigation.
 * No DOM. Talks to the server through an injectable transport.
 */
// Browser-only: finds, creates, edits and clears the card's range facet in ui.browsingEngine.
// The facet is the ordinary left-panel range facet, so edits made in either place show in both.
var RangeFilterBridge = (function() {
  var owned = {}; // columnName -> true when the card created the facet

  var find = function(columnName) {
    var key = JSON.stringify(['range', 'value', columnName]);
    var facets = ui.browsingEngine._facets;
    for (var i = 0; i < facets.length; i++) {
      var f = facets[i].facet;
      if (f instanceof RangeFacet && f.uniquenessCriterion() === key) {
        return f;
      }
    }
    return null;
  };

  return {
    read: function(columnName) {
      var f = find(columnName);
      if (!f || !f.hasSelection()) {
        return null;
      }
      var j = f.getJSON();
      return { from: j.from, to: j.to, includeBlank: j.selectBlank, includeWrongType: j.selectNonNumeric, includeError: j.selectError };
    },
    write: function(columnName, config) {
      var f = find(columnName);
      if (config === null) {
        if (f && owned[columnName]) {
          delete owned[columnName];
          ui.browsingEngine.removeFacet(f);
        } else if (f) {
          f.reset();
          Refine.update({ engineChanged: true });
        }
      } else if (f) {
        f.setSelection(config);
      } else {
        owned[columnName] = true;
        ui.browsingEngine.addFacet("range", config, {}, true);
      }
    }
  };
})();

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
    if (options && options.engineChanged) {
      store.refreshCardSelections();
    }
  };

  store.getState = function() {
    var snapshot = JSON.parse(JSON.stringify(state));
    snapshot.cards = cards.map(function(c) {
      return { columnName: c.columnName, status: c.status, stats: c.stats, counts: c.counts, histogram: c.histogram, total: c.total,
        selection: c.selection, range: c.range, problemRows: { blank: c.problemRows.blank, wrongType: c.problemRows.wrongType, error: c.problemRows.error } };
    });
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

  // The card's range is an ordinary core/range facet (AD3). RangeFacet's upper bound is
  // exclusive but the card's is inclusive, so the facet's "to" is nudged up by this much.
  var RANGE_UPPER_EPSILON = 1e-7;

  // full: apply stats/counts/histogram/total too; otherwise only selection and range
  // (until ticket 08 the response counts include the card's own range facet).
  var fetchCard = function(card, full) {
    var mine = card.seq = (card.seq || 0) + 1;
    if (full) {
      card.status = "loading";
    }
    deps.cardPost(card.columnName, deps.engineJSON(true), function(data) {
      if (cards.indexOf(card) < 0 || card.seq !== mine) {
        return; // closed while loading, or superseded
      }
      if (data.code === "ok") {
        if (full) {
          card.status = "ready";
          card.stats = data.stats;
          card.counts = data.counts;
          card.histogram = data.histogram;
          card.total = data.total;
        }
        card.selection = data.selection;
        card.range = deps.readRange(card.columnName);
        if (card.range) {
          card.problemRows = { blank: !!card.range.includeBlank, wrongType: !!card.range.includeWrongType, error: !!card.range.includeError };
        }
      } else if (full) {
        card.status = "error";
      }
      emit("cards-changed", store.getState());
    }, function() {
      if (cards.indexOf(card) >= 0 && card.seq === mine && full) {
        card.status = "error";
        emit("cards-changed", store.getState());
      }
    });
  };

  store.refreshCardSelections = function() {
    cards.forEach(function(card) {
      if (card.status === "ready") {
        fetchCard(card, false);
      }
    });
  };

  var rangeConfig = function(columnName, from, toExclusive, p) {
    return {
      name: columnName,
      expression: "value",
      columnName: columnName,
      from: from,
      to: toExclusive,
      selectNumeric: true,
      selectNonNumeric: !!p.wrongType,
      selectBlank: !!p.blank,
      selectError: !!p.error
    };
  };

  store.setRange = function(columnName, r) {
    var card = findCard(columnName);
    var p = card ? card.problemRows : { blank: false, wrongType: false, error: false };
    deps.writeRange(columnName, rangeConfig(columnName, r.from, r.to + RANGE_UPPER_EPSILON, {
      blank: r.includeBlank === undefined ? p.blank : r.includeBlank,
      wrongType: r.includeWrongType === undefined ? p.wrongType : r.includeWrongType,
      error: r.includeError === undefined ? p.error : r.includeError
    }));
  };

  // Toggles for blank / wrong-type / error rows: flags {blank, wrongType, error}, any subset.
  // They live in the range facet's selectBlank / selectNonNumeric / selectError. With no range
  // yet they are remembered on the card and used by the next setRange (defaults: all off).
  store.setProblemRows = function(columnName, flags) {
    var card = findCard(columnName);
    if (!card) {
      return;
    }
    var p = card.problemRows;
    ["blank", "wrongType", "error"].forEach(function(k) {
      if (flags[k] !== undefined) {
        p[k] = !!flags[k];
      }
    });
    var live = deps.readRange(columnName); // the facet, not card.range, which lags behind a fetch
    if (live) {
      // the facet's own bound is already exclusive, so it is passed unchanged
      deps.writeRange(columnName, rangeConfig(columnName, live.from, live.to, p));
    } else {
      emit("cards-changed", store.getState());
    }
  };

  store.resetRange = function(columnName) {
    deps.writeRange(columnName, null);
  };

  store.openCard = function(columnName) {
    if (findCard(columnName)) {
      return;
    }
    var card = { columnName: columnName, status: "loading", stats: null, counts: null, histogram: null, total: null,
                 selection: null, range: null,
                 problemRows: { blank: false, wrongType: false, error: false } };
    cards.push(card);
    emit("cards-changed", store.getState());
    fetchCard(card, true);
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

  // ---- browser wiring (not exercised by the node unit tests) ----

  store._setDeps({
    columns: function() {
      return theProject.columnModel.columns.map(function(c) { return c.name; });
    },
    engineJSON: function(keepUnrestricted) { return ui.browsingEngine.getJSON(!!keepUnrestricted); },
    cardPost: function(columnName, engineJson, onDone, onError) {
      Refine.postCSRF(
        "command/core/get-numeric-health",
        { project: theProject.id, columnName: columnName, bins: 20, engine: JSON.stringify(engineJson) },
        onDone,
        "json",
        onError
      );
    },
    readRange: function(columnName) { return RangeFilterBridge.read(columnName); },
    writeRange: function(columnName, config) { RangeFilterBridge.write(columnName, config); },
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
