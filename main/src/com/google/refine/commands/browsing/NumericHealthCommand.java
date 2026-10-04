/*******************************************************************************
 * Copyright (C) 2026, OpenRefine contributors
 * All rights reserved.
 ******************************************************************************/

package com.google.refine.commands.browsing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.refine.browsing.Engine;
import com.google.refine.browsing.RowVisitor;
import com.google.refine.browsing.util.CellClass;
import com.google.refine.browsing.util.NumericHealth;
import com.google.refine.browsing.util.NumericHealthCalculator;
import com.google.refine.commands.Command;
import com.google.refine.model.Cell;
import com.google.refine.model.Column;
import com.google.refine.model.Project;
import com.google.refine.model.Row;

public class NumericHealthCommand extends Command {

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!hasValidCSRFToken(request)) {
            respondCSRFError(response);
            return;
        }
        try {
            Project project = getProject(request);
            Engine engine = getEngine(request, project);
            String columnName = request.getParameter("columnName");
            Column column = columnName == null ? null : project.columnModel.getColumnByName(columnName);
            if (column == null) {
                throw new IllegalArgumentException("No such column: " + columnName);
            }
            int binCount = parseBins(request.getParameter("bins"));
            int cellIndex = column.getCellIndex();

            List<Cell> cells = new ArrayList<>();
            engine.getAllFilteredRows().accept(project, new RowVisitor() {

                @Override
                public void start(Project project) {
                }

                @Override
                public boolean visit(Project project, int rowIndex, Row row) {
                    cells.add(row.getCell(cellIndex));
                    return false;
                }

                @Override
                public void end(Project project) {
                }
            });
            NumericHealth health = NumericHealthCalculator.compute(cells, binCount);

            Map<String, Object> counts = new LinkedHashMap<>();
            counts.put("numeric", health.counts().get(CellClass.NUMERIC));
            counts.put("blank", health.counts().get(CellClass.BLANK));
            counts.put("wrongType", health.counts().get(CellClass.WRONG_TYPE));
            counts.put("error", health.counts().get(CellClass.ERROR));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", "ok");
            result.put("columnName", columnName);
            result.put("total", health.total());
            result.put("counts", counts);
            if (health.min() == null) {
                result.put("stats", null);
            } else {
                Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("min", health.min());
                stats.put("max", health.max());
                stats.put("mean", health.mean());
                stats.put("median", health.median());
                result.put("stats", stats);
            }
            if (health.histogramBins() == null) {
                result.put("histogram", null);
            } else {
                Map<String, Object> histogram = new LinkedHashMap<>();
                histogram.put("binWidth", health.histogramBinWidth());
                histogram.put("min", health.histogramMin());
                histogram.put("bins", health.histogramBins());
                result.put("histogram", histogram);
            }
            // Reflects every facet in the engine config, including this column's own range facet.
            // (Excluding the own facet from counts/histogram is ticket 08.)
            Map<String, Object> selection = new LinkedHashMap<>();
            selection.put("keptRows", cells.size());
            selection.put("totalRows", project.rows.size());
            result.put("selection", selection);
            respondJSON(response, result);
        } catch (Exception e) {
            respondException(response, e);
        }
    }

    private static final int DEFAULT_BINS = 20;
    private static final int MAX_BINS = 100;

    /** Absent means the default; anything that is not an integer in 1..100 is rejected. */
    private static int parseBins(String param) {
        if (param == null || param.isEmpty()) {
            return DEFAULT_BINS;
        }
        try {
            int bins = Integer.parseInt(param.trim());
            if (bins >= 1 && bins <= MAX_BINS) {
                return bins;
            }
        } catch (NumberFormatException e) {
            // fall through to the error below
        }
        throw new IllegalArgumentException("bins must be an integer between 1 and " + MAX_BINS + ": " + param);
    }
}
