/*******************************************************************************
 * Copyright (C) 2026, OpenRefine contributors
 * All rights reserved.
 ******************************************************************************/

package com.google.refine.browsing.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import com.google.refine.expr.EvalError;
import com.google.refine.model.Cell;

public class NumericHealthCalculatorTests {

    private static Cell cell(java.io.Serializable v) {
        return new Cell(v, null);
    }

    @Test
    public void finiteNumbersAreNumeric() {
        assertEquals(NumericHealthCalculator.classify(cell(0L)), CellClass.NUMERIC);
        assertEquals(NumericHealthCalculator.classify(cell(-1)), CellClass.NUMERIC);
        assertEquals(NumericHealthCalculator.classify(cell(2.5)), CellClass.NUMERIC);
    }

    @Test
    public void nullAndWhitespaceOnlyStringsAreBlank() {
        assertEquals(NumericHealthCalculator.classify(null), CellClass.BLANK);
        assertEquals(NumericHealthCalculator.classify(cell(null)), CellClass.BLANK);
        assertEquals(NumericHealthCalculator.classify(cell("")), CellClass.BLANK);
        assertEquals(NumericHealthCalculator.classify(cell("  ")), CellClass.BLANK);
    }

    @Test
    public void nonFiniteNumbersAndNonNumberValuesAreWrongType() {
        assertEquals(NumericHealthCalculator.classify(cell(Double.NaN)), CellClass.WRONG_TYPE);
        assertEquals(NumericHealthCalculator.classify(cell(Double.POSITIVE_INFINITY)), CellClass.WRONG_TYPE);
        assertEquals(NumericHealthCalculator.classify(cell("NaN")), CellClass.WRONG_TYPE);
        assertEquals(NumericHealthCalculator.classify(cell("Infinity")), CellClass.WRONG_TYPE);
        assertEquals(NumericHealthCalculator.classify(cell("12,5")), CellClass.WRONG_TYPE);
        assertEquals(NumericHealthCalculator.classify(cell(true)), CellClass.WRONG_TYPE);
    }

    @Test
    public void evalErrorIsError() {
        assertEquals(NumericHealthCalculator.classify(cell(new EvalError("boom"))), CellClass.ERROR);
    }

    private static void assertCounts(NumericHealth h, int numeric, int blank, int wrongType, int error) {
        assertEquals(h.total(), numeric + blank + wrongType + error);
        assertEquals(h.counts().get(CellClass.NUMERIC).intValue(), numeric);
        assertEquals(h.counts().get(CellClass.BLANK).intValue(), blank);
        assertEquals(h.counts().get(CellClass.WRONG_TYPE).intValue(), wrongType);
        assertEquals(h.counts().get(CellClass.ERROR).intValue(), error);
    }

    private static NumericHealth computeOf(java.io.Serializable... values) {
        List<Cell> cells = new ArrayList<>();
        for (java.io.Serializable v : values) {
            cells.add(cell(v));
        }
        return computeWithBins(20, values);
    }

    private static NumericHealth computeWithBins(int bins, java.io.Serializable... values) {
        List<Cell> cells = new ArrayList<>();
        for (java.io.Serializable v : values) {
            cells.add(cell(v));
        }
        return NumericHealthCalculator.compute(cells, bins);
    }

    @Test
    public void amountColumnOfSampleFile() {
        assertCounts(computeOf(0L, 1L, -1L, null, 2.5, 0L), 5, 1, 0, 0);
    }

    @Test
    public void allTextIsWrongType() {
        assertCounts(computeOf("a", "b", "c"), 0, 0, 3, 0);
    }

    @Test
    public void onlyBlanksAndWhitespace() {
        assertCounts(computeOf("", "  ", ""), 0, 3, 0, 0);
    }

    @Test
    public void mixedColumnClassesSumToTotal() {
        assertCounts(computeOf(1L, "12,5", "17/05/2017", new EvalError("#error")), 1, 0, 2, 1);
    }

    @Test
    public void nonFiniteStringsAreWrongType() {
        assertCounts(computeOf("NaN", "Infinity"), 0, 0, 2, 0);
    }

    @Test
    public void emptyInputHasZeroCountsForEveryClass() {
        assertCounts(computeOf(), 0, 0, 0, 0);
    }

    @Test
    public void statsOfSampleAmountColumn() {
        NumericHealth h = computeOf(0L, 1L, -1L, null, 2.5, 0L);
        assertEquals(h.min(), Double.valueOf(-1.0));
        assertEquals(h.max(), Double.valueOf(2.5));
        assertEquals(h.mean(), Double.valueOf(0.5));
        assertEquals(h.median(), Double.valueOf(0.0));
    }

    @Test
    public void medianOfOddCountIsMiddleValue() {
        assertEquals(computeOf(5L, 1L, 3L).median(), Double.valueOf(3.0));
    }

    @Test
    public void medianOfEvenCountIsMeanOfMiddleTwo() {
        assertEquals(computeOf(4L, 1L, 3L, 2L).median(), Double.valueOf(2.5));
    }

    @Test
    public void singleValueStats() {
        NumericHealth h = computeOf(7L);
        assertEquals(h.min(), Double.valueOf(7.0));
        assertEquals(h.max(), Double.valueOf(7.0));
        assertEquals(h.mean(), Double.valueOf(7.0));
        assertEquals(h.median(), Double.valueOf(7.0));
    }

    @Test
    public void statsIgnoreNonNumericCells() {
        NumericHealth h = computeOf("a", 2L, "", 4L, new EvalError("x"), "NaN");
        assertEquals(h.min(), Double.valueOf(2.0));
        assertEquals(h.max(), Double.valueOf(4.0));
        assertEquals(h.mean(), Double.valueOf(3.0));
    }

    @Test
    public void statsAreNullWithoutNumericCells() {
        NumericHealth h = computeOf("a", null);
        assertNull(h.min());
        assertNull(h.max());
        assertNull(h.mean());
        assertNull(h.median());
        assertNull(computeOf().min());
    }

    @Test
    public void histogramOfAmountColumnSumsToFiveAndPlacesEdgeValues() {
        NumericHealth h = computeOf(0L, 1L, -1L, null, 2.5, 0L);
        int[] bins = h.histogramBins();
        assertEquals(bins.length, 20);
        assertEquals(java.util.Arrays.stream(bins).sum(), 5);
        assertEquals(h.histogramMin(), Double.valueOf(-1.0));
        assertEquals(h.histogramBinWidth(), 0.175, 1e-9);
        assertEquals(bins[0], 1); // -1
        assertEquals(bins[5], 2); // 0, 0 : (0 - -1) / 0.175 = 5.7
        assertEquals(bins[11], 1); // 1 : 2 / 0.175 = 11.4
        assertEquals(bins[19], 1); // 2.5 = max falls in last bin
    }

    @Test
    public void maxValueFallsInLastBin() {
        int[] bins = computeWithBins(4, 0L, 10L).histogramBins();
        assertEquals(bins, new int[] { 1, 0, 0, 1 });
    }

    @Test
    public void exactBinBoundaryGoesToUpperBin() {
        // width 1: 0 -> bin 0, 1 -> bin 1, 2 -> bin 2, 4 -> last (bin 3)
        assertEquals(computeWithBins(4, 0L, 1L, 2L, 4L).histogramBins(), new int[] { 1, 1, 1, 1 });
    }

    @Test
    public void negativeValuesAreBinned() {
        assertEquals(computeWithBins(2, -10L, -9L, -1L).histogramBins(), new int[] { 2, 1 });
    }

    @Test
    public void singleDistinctValueGivesOneBin() {
        NumericHealth h = computeOf(7L, 7L, 7L);
        assertEquals(h.histogramBins(), new int[] { 3 });
        assertEquals(h.histogramMin(), Double.valueOf(7.0));
        assertEquals(h.histogramBinWidth(), 0.0);
    }

    @Test
    public void singleBinRequestedCollectsEverything() {
        assertEquals(computeWithBins(1, 1L, 5L, 9L).histogramBins(), new int[] { 3 });
    }

    @Test
    public void nonNumericCellsAreNotBinned() {
        assertEquals(java.util.Arrays.stream(computeWithBins(5, "a", 1L, "", 2L, new EvalError("x")).histogramBins()).sum(), 2);
    }

    @Test
    public void histogramIsNullWithoutNumericCells() {
        assertNull(computeOf("a", null).histogramBins());
        assertNull(computeOf().histogramBins());
        assertNull(computeOf().histogramMin());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void zeroBinsRejected() {
        computeWithBins(0, 1L);
    }

    // ---- ticket 11: odd values (stories 50, 51) ----

    @Test
    public void allBlankColumnHasNoStatsAndNoHistogram() {
        NumericHealth h = computeOf(null, "", "  ", null);
        assertCounts(h, 0, 4, 0, 0);
        assertNull(h.min());
        assertNull(h.median());
        assertNull(h.histogramBins());
    }

    @Test
    public void scientificNotationStringsAreWrongTypeButNumbersAreNumeric() {
        assertEquals(NumericHealthCalculator.classify(cell("1e5")), CellClass.WRONG_TYPE);
        assertEquals(NumericHealthCalculator.classify(cell("1.5E+300")), CellClass.WRONG_TYPE);
        assertEquals(NumericHealthCalculator.classify(cell(1.5e300)), CellClass.NUMERIC);
        assertEquals(NumericHealthCalculator.classify(cell(Double.MAX_VALUE)), CellClass.NUMERIC);
        assertEquals(NumericHealthCalculator.classify(cell(Double.MIN_VALUE)), CellClass.NUMERIC);
        assertEquals(NumericHealthCalculator.classify(cell(Double.POSITIVE_INFINITY)), CellClass.WRONG_TYPE);
        assertEquals(NumericHealthCalculator.classify(cell(Double.NEGATIVE_INFINITY)), CellClass.WRONG_TYPE);
    }

    private static void assertAllFinite(NumericHealth h) {
        for (Double d : new Double[] { h.min(), h.max(), h.mean(), h.median(), h.histogramMin(), h.histogramBinWidth() }) {
            assertTrue(d != null && Double.isFinite(d), "not finite: " + d);
        }
    }

    @Test
    public void hugeValuesGiveFiniteMeanMedianAndWidth() {
        NumericHealth h = computeOf(Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, Double.MAX_VALUE);
        assertAllFinite(h);
        assertEquals(h.max(), Double.valueOf(Double.MAX_VALUE));
        int sum = 0;
        for (int b : h.histogramBins()) {
            sum += b;
        }
        assertEquals(sum, 4);
    }

    @Test
    public void hugeRangeBinsEveryValueExactlyOnce() {
        NumericHealth h = computeOf(-Double.MAX_VALUE, 0.0, Double.MAX_VALUE);
        assertEquals(h.histogramBins()[0], 1);
        assertEquals(h.histogramBins()[h.histogramBins().length - 1], 1);
        int sum = 0;
        for (int b : h.histogramBins()) {
            sum += b;
        }
        assertEquals(sum, 3);
    }

    @Test
    public void denormalRangeStillBinsEveryValue() {
        NumericHealth h = computeOf(0.0, Double.MIN_VALUE);
        int sum = 0;
        for (int b : h.histogramBins()) {
            sum += b;
        }
        assertEquals(sum, 2);
    }
}
