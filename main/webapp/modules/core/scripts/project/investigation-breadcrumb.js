/*
 * M6 InvestigationBreadcrumb: "N rows -> amount 0 to 5 x -> 7 -> ... -> M shown", above the cards.
 * One chip per numeric investigation filter; other facet types only change the last number.
 * Depends on InvestigationStore.
 */
function InvestigationBreadcrumb(div) {
  var self = this;
  this._div = div;
  InvestigationStore.on("cards-changed", function(state) {
    self._error = state.breadcrumbError;
    self._render(state.breadcrumb);
  });
}

InvestigationBreadcrumb.prototype._render = function(bc) {
  var fmt = InvestigationCardStrip._formatNumber;
  var div = this._div;
  div.empty();
  if (this._error) {
    // placed first so it is visible even when there are no numbers to show
    var err = $('<span>').addClass("investigation-breadcrumb-error").attr("role", "alert").appendTo(div);
    $('<span>').text($.i18n("core-investigation/breadcrumb-error")).appendTo(err);
    $('<button type="button">').addClass("investigation-breadcrumb-retry").text($.i18n("core-investigation/retry"))
      .on("click", function() { InvestigationStore.retryBreadcrumb(); }).appendTo(err);
  }
  if (bc) {
    var arrow = function() { $('<span>').addClass("investigation-breadcrumb-arrow").text("\u2192").appendTo(div); };
    $('<span>').addClass("investigation-breadcrumb-total").text($.i18n("core-investigation/breadcrumb-total", bc.total)).appendTo(div);
    bc.steps.forEach(function(step) {
      arrow();
      var chip = $('<span>').addClass("investigation-breadcrumb-chip").attr("data-column", step.columnName).appendTo(div);
      $('<span>').addClass("investigation-breadcrumb-label")
        .text(step.columnName + " " + $.i18n("core-investigation/range", fmt(step.from), fmt(step.to))).appendTo(chip);
      $('<button type="button">').addClass("investigation-breadcrumb-remove").text("\u00d7")
        .attr("aria-label", $.i18n("core-investigation/breadcrumb-remove", step.columnName))
        .attr("title", $.i18n("core-investigation/breadcrumb-remove", step.columnName))
        .on("click", function() { InvestigationStore.removeFilter(step.columnName); }).appendTo(chip);
      arrow();
      $('<span>').addClass("investigation-breadcrumb-count").text(step.kept).appendTo(div);
    });
    arrow();
    $('<span>').addClass("investigation-breadcrumb-shown").text($.i18n("core-investigation/breadcrumb-shown", bc.shown)).appendTo(div);
  }
  resizeAll();
};
