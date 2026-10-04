/*
 * M4 HealthBar: renders the numeric health bar in a column header.
 * Depends on InvestigationStore (M3) only.
 */
var HealthBar = {};

HealthBar.MIN_SEGMENT_PCT = 2;
HealthBar.CLASSES = ['numeric', 'blank', 'wrongType', 'error'];

// ---- pure logic ----

/*
 * Returns [{cls, count, widthPct}] for the non-zero classes, or null when the
 * column has no numeric cell (no bar is shown). A non-zero class narrower than
 * MIN_SEGMENT_PCT is widened to it; the other segments share the remainder
 * proportionally so that widths always sum to 100.
 */
HealthBar.computeSegments = function(counts, total) {
  if (!total || !counts || !counts.numeric) {
    return null;
  }
  var present = HealthBar.CLASSES.filter(function(c) { return counts[c] > 0; });
  var isSmall = function(c) { return counts[c] * 100 / total < HealthBar.MIN_SEGMENT_PCT; };
  var small = present.filter(isSmall);
  var large = present.filter(function(c) { return !isSmall(c); });
  var largeCount = large.reduce(function(a, c) { return a + counts[c]; }, 0);
  var room = 100 - small.length * HealthBar.MIN_SEGMENT_PCT;
  return present.map(function(c) {
    return {
      cls: c,
      count: counts[c],
      widthPct: isSmall(c) ? HealthBar.MIN_SEGMENT_PCT : room * counts[c] / largeCount
    };
  });
};

// ---- rendering ----

HealthBar.SLOT_ATTR = 'data-health-column';

// Mounts a bar slot for a column into a header element and paints it from cached state.
HealthBar.mount = function(slot, columnName) {
  $(slot).attr(HealthBar.SLOT_ATTR, columnName);
  InvestigationStore.ensureHealth(columnName);
  HealthBar._paint($(slot), columnName);
};

HealthBar._paint = function(slot, columnName) {
  slot.empty();
  var health = InvestigationStore.getState().health[columnName];
  var segments = health && HealthBar.computeSegments(health.counts, health.total);
  if (!segments) {
    return;
  }
  var c = health.counts;
  var label = $.i18n('core-investigation/health-title', c.numeric, c.blank, c.wrongType, c.error);
  var bar = $('<div>')
    .addClass('health-bar')
    .attr({ 'data-column': columnName, 'data-total': health.total, role: 'img', 'aria-label': label, title: label })
    .appendTo(slot);
  segments.forEach(function(s) {
    $('<span>')
      .addClass('health-bar-segment health-bar-' + s.cls)
      .attr({ 'data-class': s.cls, 'data-count': s.count, 'data-width-pct': s.widthPct.toFixed(2) })
      .css('width', s.widthPct + '%')
      .appendTo(bar);
  });
};

HealthBar.onHealthUpdated = function(event) {
  $('[' + HealthBar.SLOT_ATTR + ']').each(function() {
    var slot = $(this);
    if (slot.attr(HealthBar.SLOT_ATTR) === event.columnName) {
      HealthBar._paint(slot, event.columnName);
    }
  });
};

if (typeof InvestigationStore !== 'undefined') {
  InvestigationStore.on('health-updated', HealthBar.onHealthUpdated);
}
