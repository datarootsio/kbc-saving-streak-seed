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
