/*******************************************************************************
 * Copyright (C) 2026, OpenRefine contributors
 * All rights reserved.
 ******************************************************************************/

package com.google.refine.browsing.util;

import static org.testng.Assert.assertEquals;

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
        return NumericHealthCalculator.compute(cells);
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
}
