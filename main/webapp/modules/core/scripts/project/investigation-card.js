/*
 * M5 InvestigationCard: renders one card per open column into the strip above the grid.
 * Header and stats only; depends on InvestigationStore.
 */
function InvestigationCardStrip(div) {
  var self = this;
  this._div = div;
  InvestigationStore.on("cards-changed", function(state) {
    self._render(state);
  });
}

InvestigationCardStrip._formatNumber = function(n) {
  return String(Number(n.toFixed(4)));
};

InvestigationCardStrip.prototype._render = function(state) {
  var self = this;
  this._div.empty();
  state.cards.forEach(function(card) {
    self._div.append(self._renderCard(card));
  });
  resizeAll();
};

InvestigationCardStrip.prototype._renderCard = function(card) {
  var fmt = InvestigationCardStrip._formatNumber;
  var dom = $(DOM.loadHTML("core", "scripts/project/investigation-card.html"));
  var elmts = DOM.bind(dom);
  dom.attr("data-column", card.columnName);
  elmts.titleSpan.text(card.columnName);
  elmts.closeButton.attr("aria-label", $.i18n("core-investigation/close")).attr("title", $.i18n("core-investigation/close"))
    .on("click", function() { InvestigationStore.closeCard(card.columnName); });

  if (card.status === "loading") {
    $('<div>').addClass("investigation-card-status").text($.i18n("core-investigation/loading")).appendTo(elmts.bodyDiv);
  } else if (card.status === "error") {
    $('<div>').addClass("investigation-card-status investigation-card-error").text($.i18n("core-investigation/error")).appendTo(elmts.bodyDiv);
  } else if (card.stats === null) {
    $('<div>').addClass("investigation-card-empty").text($.i18n("core-investigation/no-numeric")).appendTo(elmts.bodyDiv);
  } else {
    elmts.rangeSpan.text($.i18n("core-investigation/range", fmt(card.stats.min), fmt(card.stats.max)));
    var list = $('<dl>').addClass("investigation-card-stats").appendTo(elmts.bodyDiv);
    [
      ["count", card.counts.numeric],
      ["min", fmt(card.stats.min)],
      ["max", fmt(card.stats.max)],
      ["mean", fmt(card.stats.mean)],
      ["median", fmt(card.stats.median)]
    ].forEach(function(stat) {
      var item = $('<div>').attr("data-stat", stat[0]).appendTo(list);
      $('<dt>').text($.i18n("core-investigation/stat-" + stat[0])).appendTo(item);
      $('<dd>').text(stat[1]).appendTo(item);
    });
  }
  return dom;
};
