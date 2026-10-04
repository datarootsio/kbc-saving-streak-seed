/*******************************************************************************
 * Copyright (C) 2026, OpenRefine contributors
 * All rights reserved.
 ******************************************************************************/

package com.google.refine.browsing.util;

import java.util.Map;

/** Class counts for a set of cells. Accessors mirror a record (the build targets Java 11). */
public final class NumericHealth {

    private final int total;
    private final Map<CellClass, Integer> counts;

    private final Double min;
    private final Double max;
    private final Double mean;
    private final Double median;

    public NumericHealth(int total, Map<CellClass, Integer> counts, Double min, Double max, Double mean, Double median) {
        this.total = total;
        this.counts = counts;
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.median = median;
    }

    /** Stats of the numeric cells; null when there are none. */
    public Double min() {
        return min;
    }

    public Double max() {
        return max;
    }

    public Double mean() {
        return mean;
    }

    public Double median() {
        return median;
    }

    public int total() {
        return total;
    }

    public Map<CellClass, Integer> counts() {
        return counts;
    }
}
