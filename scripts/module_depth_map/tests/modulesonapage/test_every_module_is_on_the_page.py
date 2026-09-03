"""Every module in the source reaches the page, grouped by its package, and nothing else does."""

import json
import re

from ... import graph, page
from ..support.sourcetrees import SourceTreeTest


class EveryModuleIsOnThePageTest(SourceTreeTest):

    def source(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")
        tree.java("shop.till", "Receipt", "public record Receipt(long cents) {}")
        tree.java("shop.stock", "Shelf", "interface Shelf {\n    void restock();\n}")
        tree.java("shop.stock", "Aisle", "public enum Aisle {\n    LEFT,\n    RIGHT\n}")
        return graph.build([graph.java_root(tree.root)])

    def test_every_type_in_the_source_is_a_module_in_the_graph(self):
        document = self.source()

        self.assertEqual(
            ["shop.stock.Aisle", "shop.stock.Shelf", "shop.till.Receipt", "shop.till.Till"],
            [module["id"] for module in document["modules"]],
        )

    def test_each_module_is_identified_at_class_grain(self):
        document = self.source()

        till = [module for module in document["modules"] if module["name"] == "Till"][0]
        self.assertEqual("shop.till", till["package"])
        self.assertEqual("class", till["kind"])
        self.assertEqual("shop/till/Till.java", till["path"])

    def test_a_type_declared_inside_another_is_named_on_the_module_that_holds_it(self):
        tree = self.tree("fixture")
        tree.java(
            "shop.till",
            "Refused",
            "public class Refused extends RuntimeException {\n"
            "    public enum Kind {\n        NO_STOCK\n    }\n}",
        )

        document = graph.build([graph.java_root(tree.root)])

        self.assertEqual(["shop.till.Refused"], [module["id"] for module in document["modules"]])
        self.assertEqual(["Kind"], document["modules"][0]["nested"])

    def test_modules_are_grouped_by_the_package_they_live_in(self):
        document = self.source()

        self.assertEqual(
            [
                {"name": "shop.stock", "moduleIds": ["shop.stock.Aisle", "shop.stock.Shelf"]},
                {"name": "shop.till", "moduleIds": ["shop.till.Receipt", "shop.till.Till"]},
            ],
            document["packages"],
        )

    def test_every_module_a_package_names_is_a_module_the_graph_holds(self):
        document = self.source()

        known = {module["id"] for module in document["modules"]}
        for package in document["packages"]:
            for module_id in package["moduleIds"]:
                self.assertIn(module_id, known)

    def test_the_page_carries_the_graph_document_it_was_rendered_from(self):
        document = self.source()

        rendered = page.render(document).decode("utf-8")

        self.assertEqual(document, json.loads(_embedded_graph(rendered)))

    def test_the_graph_the_page_carries_is_the_bytes_the_graph_document_was_written_as(self):
        document = self.source()

        rendered = page.render(document).decode("utf-8")

        self.assertEqual(
            graph.serialise(document).decode("utf-8").rstrip("\n"),
            _unescaped(_embedded_graph(rendered)),
        )

    def test_nothing_on_the_page_comes_from_anywhere_but_the_graph_document(self):
        document = self.source()

        rendered = page.render(document).decode("utf-8")

        outside_the_graph = rendered.replace(_embedded_graph(rendered), "")
        for module in document["modules"]:
            self.assertNotIn(module["name"], outside_the_graph)
            self.assertNotIn(module["package"], outside_the_graph)

    def test_the_page_loads_nothing_from_outside_itself(self):
        rendered = page.render(self.source()).decode("utf-8")

        self.assertNotIn("http://", rendered)
        self.assertNotIn("https://", rendered)
        self.assertEqual([], re.findall(r"\b(?:src|href)\s*=", rendered))

    def test_the_page_is_legible_in_either_theme(self):
        rendered = page.render(self.source()).decode("utf-8")

        self.assertIn("color-scheme: light dark", rendered)
        self.assertIn("@media (prefers-color-scheme: dark)", rendered)


def _embedded_graph(rendered):
    opening = '<script id="%s" type="application/json">\n' % page.GRAPH_ELEMENT_ID
    start = rendered.index(opening) + len(opening)
    end = rendered.index("\n</script>", start)
    return rendered[start:end]


def _unescaped(embedded):
    return embedded.replace("\\u003c", "<").replace("\\u003e", ">").replace("\\u0026", "&")
