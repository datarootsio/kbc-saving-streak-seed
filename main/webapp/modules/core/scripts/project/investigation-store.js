/*
 * M3 InvestigationStore: single source of UI state for the numeric investigation feature.
 * Sections are delimited so that independent slices can be merged side by side.
 */
var InvestigationStore = InvestigationStore || {};

(function() {
  var handlers = {};

  InvestigationStore.on = InvestigationStore.on || function(eventName, handler) {
    (handlers[eventName] = handlers[eventName] || []).push(handler);
  };

  var emit = function(eventName, payload) {
    var list = handlers[eventName] || [];
    for (var i = 0; i < list.length; i++) {
      list[i](payload);
    }
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
        emit("cards-changed", InvestigationStore.getState());
      },
      "json",
      function() {
        if (cards.indexOf(card) >= 0) {
          card.status = "error";
          emit("cards-changed", InvestigationStore.getState());
        }
      }
    );
  };

  InvestigationStore.openCard = function(columnName) {
    if (findCard(columnName)) {
      return;
    }
    var card = { columnName: columnName, status: "loading", stats: null, counts: null, histogram: null, total: null };
    cards.push(card);
    emit("cards-changed", InvestigationStore.getState());
    fetchCard(card);
  };

  InvestigationStore.closeCard = function(columnName) {
    var card = findCard(columnName);
    if (!card) {
      return;
    }
    cards.splice(cards.indexOf(card), 1);
    emit("cards-changed", InvestigationStore.getState());
  };

  InvestigationStore.getState = function() {
    return {
      cards: cards.map(function(c) {
        return { columnName: c.columnName, status: c.status, stats: c.stats, counts: c.counts, histogram: c.histogram, total: c.total };
      })
    };
  };
  // ---- end cards ----
})();
