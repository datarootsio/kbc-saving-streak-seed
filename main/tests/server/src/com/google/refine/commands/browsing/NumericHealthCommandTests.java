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
}
