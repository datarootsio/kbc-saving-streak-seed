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
  var self = this;
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
  } else if (card.total === 0) {
    $('<div>').addClass("investigation-card-empty investigation-card-empty-project").text($.i18n("core-investigation/empty-project")).appendTo(elmts.bodyDiv);
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
    self._renderHistogram(card.histogram, elmts.bodyDiv, card.range);
    self._renderRange(card, elmts.bodyDiv);
    self._renderToggles(card, elmts.bodyDiv);
  }
  return dom;
};

InvestigationCardStrip._binOutsideRange = function(histogram, i, range) {
  if (!range) {
    return false;
  }
  var lower = histogram.min + i * histogram.binWidth;
  var upper = lower + histogram.binWidth;
  return upper < range.from || lower > range.to;
};

InvestigationCardStrip.prototype._renderRange = function(card, parent) {
  var fmt = InvestigationCardStrip._formatNumber;
  var min = card.stats.min;
  var max = card.stats.max;
  var from = card.range ? Math.max(card.range.from, min) : min;
  var to = card.range ? Math.min(card.range.to, max) : max;
  var wrap = $('<div>').addClass("investigation-range").appendTo(parent);
  var fromInput = $('<input type="range" step="any">').addClass("investigation-range-from")
    .attr({ min: min, max: max, "aria-label": $.i18n("core-investigation/range-from") }).val(from).appendTo(wrap);
  var toInput = $('<input type="range" step="any">').addClass("investigation-range-to")
    .attr({ min: min, max: max, "aria-label": $.i18n("core-investigation/range-to") }).val(to).appendTo(wrap);
  var labels = $('<div>').addClass("investigation-range-labels").appendTo(wrap);
  var fromLabel = $('<span>').addClass("investigation-range-from-label").text(fmt(from)).appendTo(labels);
  var toLabel = $('<span>').addClass("investigation-range-to-label").text(fmt(to)).appendTo(labels);

  var current = function() {
    var a = Number(fromInput.val());
    var b = Number(toInput.val());
    return { from: Math.min(a, b), to: Math.max(a, b) };
  };
  wrap.find("input").on("input", function() {
    var r = current();
    fromLabel.text(fmt(r.from));
    toLabel.text(fmt(r.to));
  });
  wrap.find("input").on("change", function() {
    var r = current();
    var old = card.range || {};
    InvestigationStore.setRange(card.columnName, {
      from: r.from, to: r.to,
      includeBlank: old.includeBlank, includeWrongType: old.includeWrongType, includeError: old.includeError
    });
  });

  if (card.range) {
    $('<button type="button">').addClass("investigation-range-reset").text($.i18n("core-investigation/range-reset"))
      .on("click", function() { InvestigationStore.resetRange(card.columnName); }).appendTo(wrap);
    if (card.selection) {
      var pct = card.selection.totalRows > 0 ? Math.round(100 * card.selection.keptRows / card.selection.totalRows) : 0;
      $('<div>').addClass("investigation-kept")
        .text($.i18n("core-investigation/keeps", card.selection.keptRows, card.selection.totalRows, pct)).appendTo(wrap);
    }
  }
};

// Keep-visible toggles for problem rows under the range selection (all off by default).
InvestigationCardStrip.prototype._renderToggles = function(card, parent) {
  var wrap = $('<div>').addClass("investigation-toggles").attr({ role: "group", "aria-label": $.i18n("core-investigation/toggles-title") }).appendTo(parent);
  $('<div>').addClass("investigation-toggles-title").text($.i18n("core-investigation/toggles-title")).appendTo(wrap);
  ["blank", "wrongType", "error"].forEach(function(kind) {
    var label = $('<label>').addClass("investigation-toggle").attr("data-kind", kind).appendTo(wrap);
    $('<input type="checkbox">').prop("checked", !!card.problemRows[kind]).on("change", function() {
      var flags = {};
      flags[kind] = this.checked;
      InvestigationStore.setProblemRows(card.columnName, flags);
    }).appendTo(label);
    $('<span>').addClass("investigation-toggle-label").text($.i18n("core-investigation/toggle-" + kind)).appendTo(label);
    $('<span>').addClass("investigation-toggle-count").text(card.counts[kind]).appendTo(label);
  });
};

InvestigationCardStrip.prototype._renderHistogram = function(histogram, parent, range) {
  if (!histogram) {
    return;
  }
  var fmt = InvestigationCardStrip._formatNumber;
  var peak = Math.max.apply(null, histogram.bins);
  var wrap = $('<div>').addClass("investigation-histogram").appendTo(parent);
  var bars = $('<div>').addClass("investigation-histogram-bars").appendTo(wrap);
  histogram.bins.forEach(function(count, i) {
    var from = histogram.min + i * histogram.binWidth;
    var bar = $('<div>').addClass("investigation-histogram-bar").attr("data-count", count)
      .toggleClass("investigation-histogram-bar-out", InvestigationCardStrip._binOutsideRange(histogram, i, range))
      .attr("title", $.i18n("core-investigation/bin-title", fmt(from), fmt(from + histogram.binWidth), count))
      .appendTo(bars);
    $('<div>').addClass("investigation-histogram-fill").css("height", (peak > 0 ? (100 * count / peak) : 0) + "%").appendTo(bar);
  });
  if (histogram.bins.length === 1) {
    $('<div>').addClass("investigation-histogram-single").text($.i18n("core-investigation/single-value")).appendTo(wrap);
  }
};
