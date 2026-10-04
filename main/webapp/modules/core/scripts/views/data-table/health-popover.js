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
 * largest fractional parts, earlier classes first on ties. A class with a zero
 * count never receives a point. All zeros when total is 0.
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
    return { cls: c, index: i, rem: counts[c] > 0 ? exact - result[c] : -1 };
  });
  var missing = 100 - classes.reduce(function(a, c) { return a + result[c]; }, 0);
  remainders.sort(function(a, b) { return b.rem - a.rem || a.index - b.index; });
  for (var i = 0; i < missing; i++) {
    result[remainders[i].cls]++;
  }
  return result;
};
