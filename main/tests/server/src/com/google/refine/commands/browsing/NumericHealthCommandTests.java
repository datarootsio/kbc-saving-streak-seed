/*******************************************************************************
 * Copyright (C) 2026, OpenRefine contributors
 * All rights reserved.
 ******************************************************************************/

package com.google.refine.commands.browsing;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.io.Serializable;

import javax.servlet.ServletException;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.refine.commands.Command;
import com.google.refine.commands.CommandTestBase;
import com.google.refine.expr.EvalError;
import com.google.refine.expr.MetaParser;
import com.google.refine.grel.Parser;
import com.google.refine.model.Project;
import com.google.refine.util.ParsingUtilities;

public class NumericHealthCommandTests extends CommandTestBase {

    @BeforeMethod
    public void setUpCommand() {
        command = new NumericHealthCommand();
    }

    @Test
    public void testCSRFProtection() throws ServletException, IOException {
        command.doPost(request, response);
        assertCSRFCheckFailed();
    }

    private Project amountProject() {
        return createProject(new String[] { "name", "amount" },
                new Serializable[][] {
                        { "Alpha", 0L },
                        { "alpha", 1L },
                        { "ALPHA", -1L },
                        { "Beta", null },
                        { "Gamma", 2.5 },
                        { "Delta", 0L },
                });
    }

    private JsonNode post(Project project, String columnName, String engineJson) throws Exception {
        when(request.getParameter("project")).thenReturn(Long.toString(project.id));
        when(request.getParameter("csrf_token")).thenReturn(Command.csrfFactory.getFreshToken());
        when(request.getParameter("columnName")).thenReturn(columnName);
        when(request.getParameter("engine")).thenReturn(engineJson);
        command.doPost(request, response);
        return ParsingUtilities.mapper.readTree(writer.toString());
    }

    private static void assertCounts(JsonNode json, int total, int numeric, int blank, int wrongType, int error) {
        assertEquals(json.get("code").asText(), "ok");
        assertEquals(json.get("total").asInt(), total);
        JsonNode counts = json.get("counts");
        assertEquals(counts.get("numeric").asInt(), numeric);
        assertEquals(counts.get("blank").asInt(), blank);
        assertEquals(counts.get("wrongType").asInt(), wrongType);
        assertEquals(counts.get("error").asInt(), error);
    }

    @Test
    public void testAmountColumnCountsWithNoFacets() throws Exception {
        JsonNode json = post(amountProject(), "amount", "{\"mode\":\"row-based\",\"facets\":[]}");
        assertEquals(json.get("columnName").asText(), "amount");
        assertCounts(json, 6, 5, 1, 0, 0);
    }

    private static String listFacetOn(String column, String selectedValueJson) {
        return "{\"type\":\"list\",\"name\":\"" + column + "\",\"columnName\":\"" + column + "\","
                + "\"expression\":\"value\",\"omitBlank\":false,\"omitError\":false,"
                + "\"selection\":[{\"v\":" + selectedValueJson + "}],\"selectBlank\":false,\"selectError\":false,\"invert\":false}";
    }

    @BeforeMethod
    public void registerGRELParser() {
        MetaParser.registerLanguageParser("grel", "GREL", Parser.grelParser, "value");
    }

    @AfterMethod
    public void unregisterGRELParser() {
        MetaParser.unregisterLanguageParser("grel");
    }

    @Test
    public void testCountsCoverOnlyRowsMatchingFacet() throws Exception {
        String engine = "{\"mode\":\"row-based\",\"facets\":[" + listFacetOn("name", "{\"v\":\"Alpha\",\"l\":\"Alpha\"}") + "]}";
        assertCounts(post(amountProject(), "amount", engine), 1, 1, 0, 0, 0);
    }

    private Project recordProject() {
        return createProject(new String[] { "key", "amount" },
                new Serializable[][] {
                        { "A", 1L },
                        { null, "x" },
                        { "B", 2L },
                });
    }

    @Test
    public void testRowModeCountsOnlyMatchingRow() throws Exception {
        String engine = "{\"mode\":\"row-based\",\"facets\":[" + listFacetOn("amount", "{\"v\":1,\"l\":\"1\"}") + "]}";
        assertCounts(post(recordProject(), "amount", engine), 1, 1, 0, 0, 0);
    }

    @Test
    public void testRecordModeCountsAllRowsOfMatchingRecord() throws Exception {
        String engine = "{\"mode\":\"record-based\",\"facets\":[" + listFacetOn("amount", "{\"v\":1,\"l\":\"1\"}") + "]}";
        assertCounts(post(recordProject(), "amount", engine), 2, 1, 0, 1, 0);
    }

    @Test
    public void testErrorCellsAreCountedAsError() throws Exception {
        Project project = createProject(new String[] { "amount" },
                new Serializable[][] { { 1L }, { new EvalError("boom") }, { "  " }, { "NaN" } });
        assertCounts(post(project, "amount", "{\"mode\":\"row-based\",\"facets\":[]}"), 4, 1, 1, 1, 1);
    }

    @Test
    public void testUnknownColumnReturnsError() throws Exception {
        JsonNode json = post(amountProject(), "nope", "{\"mode\":\"row-based\",\"facets\":[]}");
        assertEquals(json.get("code").asText(), "error");
        assertTrue(json.get("message").asText().contains("No such column: nope"), json.toString());
    }

    @Test
    public void testUnknownProjectReturnsError() throws Exception {
        when(request.getParameter("project")).thenReturn("123456789");
        when(request.getParameter("csrf_token")).thenReturn(Command.csrfFactory.getFreshToken());
        when(request.getParameter("columnName")).thenReturn("amount");
        command.doPost(request, response);
        assertErrorNotCSRF();
    }

    @Test
    public void testStatsOfAmountColumn() throws Exception {
        JsonNode stats = post(amountProject(), "amount", "{\"mode\":\"row-based\",\"facets\":[]}").get("stats");
        assertEquals(stats.get("min").asDouble(), -1.0);
        assertEquals(stats.get("max").asDouble(), 2.5);
        assertEquals(stats.get("mean").asDouble(), 0.5);
        assertEquals(stats.get("median").asDouble(), 0.0);
    }

    @Test
    public void testStatsAreNullWhenNoNumericCells() throws Exception {
        Project project = createProject(new String[] { "name" }, new Serializable[][] { { "a" }, { "b" } });
        JsonNode json = post(project, "name", "{\"mode\":\"row-based\",\"facets\":[]}");
        assertTrue(json.has("stats"), json.toString());
        assertTrue(json.get("stats").isNull(), json.toString());
    }

    private static final String NO_FACETS = "{\"mode\":\"row-based\",\"facets\":[]}";

    private JsonNode postWithBins(Project project, String bins) throws Exception {
        when(request.getParameter("bins")).thenReturn(bins);
        return post(project, "amount", NO_FACETS);
    }

    private static int sum(JsonNode bins) {
        int s = 0;
        for (JsonNode b : bins) {
            s += b.asInt();
        }
        return s;
    }

    @Test
    public void testHistogramDefaultsToTwentyBins() throws Exception {
        JsonNode h = post(amountProject(), "amount", NO_FACETS).get("histogram");
        assertEquals(h.get("min").asDouble(), -1.0);
        assertEquals(h.get("binWidth").asDouble(), 0.175, 1e-9);
        assertEquals(h.get("bins").size(), 20);
        assertEquals(sum(h.get("bins")), 5);
        assertEquals(h.get("bins").get(0).asInt(), 1);
        assertEquals(h.get("bins").get(5).asInt(), 2);
        assertEquals(h.get("bins").get(11).asInt(), 1);
        assertEquals(h.get("bins").get(19).asInt(), 1);
    }

    @Test
    public void testBinsParameterIsHonoured() throws Exception {
        assertEquals(postWithBins(amountProject(), "7").get("histogram").get("bins").size(), 7);
    }

    @Test
    public void testBinsLowerLimitIsOne() throws Exception {
        assertEquals(postWithBins(amountProject(), "1").get("histogram").get("bins").size(), 1);
    }

    @Test
    public void testBinsUpperLimitIsHundred() throws Exception {
        assertEquals(postWithBins(amountProject(), "100").get("histogram").get("bins").size(), 100);
    }

    private void assertBinsRejected(String bad) throws Exception {
        JsonNode json = postWithBins(amountProject(), bad);
        assertEquals(json.get("code").asText(), "error", bad);
        assertTrue(json.get("message").asText().contains("bins"), json.toString());
    }

    @Test
    public void testZeroBinsIsAnError() throws Exception {
        assertBinsRejected("0");
    }

    @Test
    public void testTooManyBinsIsAnError() throws Exception {
        assertBinsRejected("101");
    }

    @Test
    public void testNonIntegerBinsIsAnError() throws Exception {
        assertBinsRejected("2.5");
    }

    @Test
    public void testSingleDistinctValueGivesOneBin() throws Exception {
        Project project = createProject(new String[] { "amount" }, new Serializable[][] { { 7L }, { 7L }, { 7L } });
        JsonNode h = post(project, "amount", NO_FACETS).get("histogram");
        assertEquals(h.get("bins").size(), 1);
        assertEquals(h.get("bins").get(0).asInt(), 3);
        assertEquals(h.get("min").asDouble(), 7.0);
    }

    @Test
    public void testHistogramIsNullWhenNoNumericCells() throws Exception {
        Project project = createProject(new String[] { "name" }, new Serializable[][] { { "a" }, { "b" } });
        JsonNode json = post(project, "name", NO_FACETS);
        assertTrue(json.has("histogram"), json.toString());
        assertTrue(json.get("histogram").isNull(), json.toString());
    }

    private static String rangeFacetOn(String column, double from, double to, boolean selectBlank) {
        return "{\"type\":\"range\",\"name\":\"" + column + "\",\"columnName\":\"" + column + "\","
                + "\"expression\":\"value\",\"from\":" + from + ",\"to\":" + to
                + ",\"selectNumeric\":true,\"selectNonNumeric\":false,\"selectBlank\":" + selectBlank + ",\"selectError\":false}";
    }

    private static void assertSelection(JsonNode json, int kept, int total) {
        assertEquals(json.get("code").asText(), "ok");
        assertEquals(json.get("selection").get("keptRows").asInt(), kept);
        assertEquals(json.get("selection").get("totalRows").asInt(), total);
    }

    @Test
    public void testSelectionWithoutFacetsKeepsEveryRow() throws Exception {
        assertSelection(post(amountProject(), "amount", "{\"mode\":\"row-based\",\"facets\":[]}"), 6, 6);
    }

    @Test
    public void testSelectionReflectsOwnRangeFacet() throws Exception {
        String engine = "{\"mode\":\"row-based\",\"facets\":[" + rangeFacetOn("amount", 0, 1.0000001, false) + "]}";
        assertSelection(post(amountProject(), "amount", engine), 3, 6);
    }

    @Test
    public void testSelectionCountsBlankRowsWhenRangeSelectsBlank() throws Exception {
        String engine = "{\"mode\":\"row-based\",\"facets\":[" + rangeFacetOn("amount", 0, 1.0000001, true) + "]}";
        assertSelection(post(amountProject(), "amount", engine), 4, 6);
    }

    @Test
    public void testSelectionCombinesRangeWithOtherFacets() throws Exception {
        String engine = "{\"mode\":\"row-based\",\"facets\":[" + rangeFacetOn("amount", 0, 1.0000001, false) + ","
                + listFacetOn("name", "{\"v\":\"Alpha\",\"l\":\"Alpha\"}") + "]}";
        assertSelection(post(amountProject(), "amount", engine), 1, 6);
    }
}
