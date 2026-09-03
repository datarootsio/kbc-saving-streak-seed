"""Two runs over unchanged source write identical bytes.

The single most important test here: every other claim the page makes rests on it, and a
change in the output always meaning a change in the code is the whole point.
"""

import os
import re

from ... import cli, graph, page
from ..support.sourcetrees import BACKEND_SOURCE, SourceTreeTest


class TheSameSourceGivesTheSameBytesTest(SourceTreeTest):

    def source(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")
        tree.java("shop.stock", "Shelf", "interface Shelf {\n    void restock();\n}")
        tree.java("shop.stock", "Aisle", "public enum Aisle {\n    LEFT\n}")
        return tree

    def test_the_graph_document_is_byte_identical_between_runs(self):
        tree = self.source()

        first = graph.serialise(graph.build([graph.java_root(tree.root)]))
        second = graph.serialise(graph.build([graph.java_root(tree.root)]))

        self.assertEqual(first, second)

    def test_the_page_is_byte_identical_between_runs(self):
        tree = self.source()

        first = page.render(graph.build([graph.java_root(tree.root)]))
        second = page.render(graph.build([graph.java_root(tree.root)]))

        self.assertEqual(first, second)

    def test_the_whole_command_writes_the_same_two_files_twice(self):
        tree = self.source()
        first = self.run_command(tree.root, "first")
        second = self.run_command(tree.root, "second")

        self.assertEqual(first, second)

    def test_the_command_writes_the_same_two_files_twice_over_this_repository(self):
        first = self.run_command(BACKEND_SOURCE, "first")
        second = self.run_command(BACKEND_SOURCE, "second")

        self.assertEqual(first, second)

    def test_every_collection_in_the_graph_is_sorted(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Zebra", "public class Zebra {}")
        tree.java("shop.till", "Ant", "public class Ant {}")
        tree.java("shop.aisle", "Middle", "public class Middle {}")

        document = graph.build([graph.java_root(tree.root)])

        self.assertEqual(
            ["shop.aisle.Middle", "shop.till.Ant", "shop.till.Zebra"],
            [module["id"] for module in document["modules"]],
        )
        self.assertEqual(["shop.aisle", "shop.till"], [p["name"] for p in document["packages"]])
        for package in document["packages"]:
            self.assertEqual(sorted(package["moduleIds"]), package["moduleIds"])

    def run_command(self, source, run):
        graph_path = os.path.join(self.scratch, run, "graph.json")
        page_path = os.path.join(self.scratch, run, "page.html")
        exit_code = cli.main(
            ["--source", source, "--graph", graph_path, "--page", page_path, "--log-level", "ERROR"]
        )
        self.assertEqual(0, exit_code)
        return _bytes_of(graph_path), _bytes_of(page_path)


class NothingMachineSpecificIsWrittenTest(SourceTreeTest):
    """No timestamp, no absolute path, nothing that is true only on the machine that ran."""

    def written(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {}")
        document = graph.build([graph.java_root(tree.root)])
        return tree, graph.serialise(document).decode("utf-8"), page.render(document).decode("utf-8")

    def test_neither_output_names_a_directory_on_this_machine(self):
        tree, written_graph, written_page = self.written()

        for written in (written_graph, written_page):
            self.assertNotIn(tree.root, written)
            self.assertNotIn(self.scratch, written)
            self.assertNotIn(os.path.expanduser("~"), written)

    def test_the_source_root_is_named_by_where_it_sits_in_its_repository(self):
        tree, written_graph, _ = self.written()

        self.assertIn('"fixture"', written_graph)

    def test_this_repository_is_named_by_its_own_layout_rather_than_its_location(self):
        document = graph.build([graph.java_root(BACKEND_SOURCE)])

        self.assertEqual(["backend/src/main/java"], document["source"]["roots"])

    def test_neither_output_carries_anything_that_looks_like_a_date_or_a_time(self):
        _, written_graph, written_page = self.written()

        for written in (written_graph, written_page):
            self.assertEqual([], re.findall(r"\d{4}-\d{2}-\d{2}", written))
            self.assertEqual([], re.findall(r"\d{2}:\d{2}:\d{2}", written))


def _bytes_of(path):
    with open(path, "rb") as handle:
        return handle.read()
