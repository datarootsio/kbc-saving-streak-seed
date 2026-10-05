/*******************************************************************************
 * Copyright (C) 2026, OpenRefine contributors
 * All rights reserved.
 ******************************************************************************/

package com.google.refine.browsing.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
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

    public static NumericHealth compute(Iterable<Cell> cells, int binCount) {
        if (binCount < 1) {
            throw new IllegalArgumentException("binCount must be at least 1: " + binCount);
        }
        Map<CellClass, Integer> counts = new EnumMap<>(CellClass.class);
        for (CellClass c : CellClass.values()) {
            counts.put(c, 0);
        }
        int total = 0;
        List<Double> numbers = new ArrayList<>();
        for (Cell cell : cells) {
            CellClass cellClass = classify(cell);
            counts.merge(cellClass, 1, Integer::sum);
            if (cellClass == CellClass.NUMERIC) {
                numbers.add(((Number) cell.value).doubleValue());
            }
            total++;
        }
        if (numbers.isEmpty()) {
            return new NumericHealth(total, counts, null, null, null, null, null, 0, null);
        }
        Collections.sort(numbers);
        double sum = 0;
        for (double n : numbers) {
            sum += n;
        }
        int size = numbers.size();
        double median;
        if (size % 2 == 1) {
            median = numbers.get(size / 2);
        } else {
            double lo = numbers.get(size / 2 - 1);
            double hi = numbers.get(size / 2);
            median = Double.isFinite(lo + hi) ? (lo + hi) / 2 : lo / 2 + hi / 2;
        }
        double mean = sum / size;
        if (!Double.isFinite(mean)) {
            // The sum overflowed (values near Double.MAX_VALUE): divide before adding.
            mean = 0;
            for (double n : numbers) {
                mean += n / size;
            }
        }
        double min = numbers.get(0);
        double max = numbers.get(size - 1);
        int[] bins;
        double binWidth;
        if (min == max) {
            bins = new int[] { size };
            binWidth = 0;
        } else {
            bins = new int[binCount];
            if (Double.isFinite(max - min)) {
                binWidth = (max - min) / binCount;
                for (double n : numbers) {
                    bins[Math.min(binCount - 1, (int) ((n - min) / binWidth))]++;
                }
            } else {
                // max - min overflows: work on halves of every quantity.
                binWidth = max / binCount - min / binCount;
                double halfSpan = max / 2 - min / 2;
                for (double n : numbers) {
                    bins[Math.min(binCount - 1, (int) ((n / 2 - min / 2) / halfSpan * binCount))]++;
                }
            }
        }
        return new NumericHealth(total, counts, min, max, mean, median, min, binWidth, bins);
    }
}
