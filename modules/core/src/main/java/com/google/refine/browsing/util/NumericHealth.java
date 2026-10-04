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

    public NumericHealth(int total, Map<CellClass, Integer> counts) {
        this.total = total;
        this.counts = counts;
    }

    public int total() {
        return total;
    }

    public Map<CellClass, Integer> counts() {
        return counts;
    }
}
