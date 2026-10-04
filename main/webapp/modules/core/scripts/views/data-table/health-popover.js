/*
 * M4 HealthPopover: breakdown popover opened from the health bar.
 * Depends on InvestigationStore (M3).
 */
var HealthPopover = {};

HealthPopover.CLASSES = ['numeric', 'blank', 'wrongType', 'error'];

// ---- pure logic ----

/*
 * Whole-number percentages per class that always total 100 (largest remainder):
 * floor each exact share, then hand the missing points to the classes with the
 * largest fractional parts, earlier classes first on ties.
 * All zeros when total is 0.
 */
HealthPopover.percentages = function(counts, total) {
  var classes = HealthPopover.CLASSES;
  var result = {};
  if (!total) {
    classes.forEach(function(c) { result[c] = 0; });
    return result;
  }
  var remainders = classes.map(function(c, i) {
    var exact = counts[c] * 100 / total;
    result[c] = Math.floor(exact);
    return { cls: c, index: i, rem: exact - result[c] };
  });
  var missing = 100 - classes.reduce(function(a, c) { return a + result[c]; }, 0);
  remainders.sort(function(a, b) { return b.rem - a.rem || a.index - b.index; });
  for (var i = 0; i < missing; i++) {
    result[remainders[i].cls]++;
  }
  return result;
};

// ---- rendering ----

HealthPopover._open = null; // { columnName, elmt }

HealthPopover._bar = function(columnName) {
  return $('.health-bar').filter(function() { return $(this).attr('data-column') === columnName; });
};

HealthPopover.isOpenFor = function(columnName) {
  return !!HealthPopover._open && HealthPopover._open.columnName === columnName;
};

HealthPopover.toggle = function(columnName) {
  if (HealthPopover.isOpenFor(columnName)) {
    HealthPopover.close(true);
  } else {
    HealthPopover.open(columnName);
  }
};

HealthPopover.open = function(columnName) {
  var health = InvestigationStore.getState().health[columnName];
  if (!health) {
    return;
  }
  HealthPopover.close(false);
  var elmt = $('<div>')
    .addClass('health-popover')
    .attr({ role: 'dialog', 'data-column': columnName, 'aria-label': $.i18n('core-investigation/popover-title', columnName) })
    .appendTo(document.body);
  HealthPopover._open = { columnName: columnName, elmt: elmt };
  HealthPopover._fill(elmt, columnName, health);
  HealthPopover._position(elmt, columnName);
  HealthPopover._bar(columnName).attr('aria-expanded', 'true');
  $(document).on('mousedown.healthPopover', HealthPopover._onOutsideMouseDown);
  $(document).on('keydown.healthPopover', HealthPopover._onKeyDown);
};

// returnFocus: move focus back to the bar (Escape and toggle), not on outside click.
HealthPopover.close = function(returnFocus) {
  var open = HealthPopover._open;
  if (!open) {
    return;
  }
  HealthPopover._open = null;
  open.elmt.remove();
  $(document).off('.healthPopover');
  var bar = HealthPopover._bar(open.columnName).attr('aria-expanded', 'false');
  if (returnFocus) {
    bar.trigger('focus');
  }
};

HealthPopover._fill = function(elmt, columnName, health) {
  var pct = HealthPopover.percentages(health.counts, health.total);
  elmt.empty();
  $('<div>').addClass('health-popover-title').text($.i18n('core-investigation/popover-title', columnName)).appendTo(elmt);
  $('<div>').addClass('health-popover-total').attr('data-total', health.total)
    .text($.i18n('core-investigation/popover-total', health.total)).appendTo(elmt);
  HealthPopover.CLASSES.forEach(function(cls) {
    var count = health.counts[cls];
    var row = $('<div>').addClass('health-popover-row').attr('data-class', cls).toggleClass('is-zero', count === 0).appendTo(elmt);
    $('<span>').addClass('health-popover-swatch health-bar-' + cls).appendTo(row);
    $('<span>').addClass('health-popover-name').text($.i18n('core-investigation/class-' + cls)).appendTo(row);
    $('<span>').addClass('health-popover-count').text(count).appendTo(row);
    $('<span>').addClass('health-popover-pct').text(pct[cls] + '%').appendTo(row);
    $('<div>').addClass('health-popover-explain').text($.i18n('core-investigation/class-' + cls + '-explain')).appendTo(row);
  });
};

HealthPopover._position = function(elmt, columnName) {
  var bar = HealthPopover._bar(columnName);
  if (!bar.length) {
    return;
  }
  var rect = bar[0].getBoundingClientRect();
  var left = Math.max(4, Math.min(rect.left, window.innerWidth - elmt.outerWidth() - 4));
  elmt.css({ top: rect.bottom + 4, left: left });
};

HealthPopover._onOutsideMouseDown = function(e) {
  var open = HealthPopover._open;
  if (!open) {
    return;
  }
  var target = $(e.target);
  if (target.closest(open.elmt).length || target.closest(HealthPopover._bar(open.columnName)).length) {
    return;
  }
  HealthPopover.close(false);
};

HealthPopover._onKeyDown = function(e) {
  if (e.key === 'Escape' && HealthPopover._open) {
    e.stopPropagation();
    HealthPopover.close(true);
  }
};

// Keep the open popover in step with the data; close it if the column no longer has a bar.
HealthPopover.onHealthUpdated = function(event) {
  var open = HealthPopover._open;
  if (!open || open.columnName !== event.columnName) {
    return;
  }
  var health = InvestigationStore.getState().health[event.columnName];
  if (!health || !health.counts || !health.counts.numeric) {
    HealthPopover.close(false);
    return;
  }
  HealthPopover._fill(open.elmt, event.columnName, health);
  HealthPopover._position(open.elmt, event.columnName);
};

if (typeof InvestigationStore !== 'undefined') {
  InvestigationStore.on('health-updated', HealthPopover.onHealthUpdated);
}
