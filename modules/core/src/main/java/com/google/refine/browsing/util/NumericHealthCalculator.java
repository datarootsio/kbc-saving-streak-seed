/*******************************************************************************
 * Copyright (C) 2026, OpenRefine contributors
 * All rights reserved.
 ******************************************************************************/

package com.google.refine.browsing.util;

import java.util.EnumMap;
import java.util.Map;

import com.google.refine.expr.EvalError;
import com.google.refine.model.Cell;

public final class NumericHealthCalculator {

    private NumericHealthCalculator() {
    }

    public static CellClass classify(Cell cell) {
        if (cell != null && cell.value instanceof EvalError) {
            return CellClass.ERROR;
        }
        if (cell == null || cell.value == null
                || (cell.value instanceof String && ((String) cell.value).trim().isEmpty())) {
            return CellClass.BLANK;
        }
        if (cell.value instanceof Number && Double.isFinite(((Number) cell.value).doubleValue())) {
            return CellClass.NUMERIC;
        }
        return CellClass.WRONG_TYPE;
    }

    public static NumericHealth compute(Iterable<Cell> cells) {
        Map<CellClass, Integer> counts = new EnumMap<>(CellClass.class);
        for (CellClass c : CellClass.values()) {
            counts.put(c, 0);
        }
        int total = 0;
        for (Cell cell : cells) {
            counts.merge(classify(cell), 1, Integer::sum);
            total++;
        }
        return new NumericHealth(total, counts);
    }
}
