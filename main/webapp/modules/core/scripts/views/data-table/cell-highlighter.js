/*
 * M7 CellHighlighter: client-side classification of a grid cell, mirroring the
 * server's NumericHealthCalculator.classify. Precedence: error > blank > numeric > wrongType.
 * Cell JSON: {v: value} or {e: "message"}; null/undefined for a missing cell.
 */
var CellHighlighter = {};

CellHighlighter.classFor = function(cell) {
  if (cell && cell.e !== undefined && cell.e !== null) {
    return 'error';
  }
  var v = cell ? cell.v : null;
  if (v === null || v === undefined || (typeof v === 'string' && v.trim() === '')) {
    return 'blank';
  }
  if (typeof v === 'number' && isFinite(v)) {
    return 'numeric';
  }
  return 'wrongType';
};

// True when a class is active and the cell belongs to it.
CellHighlighter.isHighlighted = function(cell, cellClass) {
  return !!cellClass && CellHighlighter.classFor(cell) === cellClass;
};
