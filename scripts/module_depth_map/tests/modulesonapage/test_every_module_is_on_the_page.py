"""Every module in the source reaches the page, grouped by its package, and nothing else does."""

import json
import logging
import os
import re

from ... import cli, graph, page, scoring
from ..support.sourcetrees import SourceTreeTest


class EveryModuleIsOnThePageTest(SourceTreeTest):

    def source(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")
        tree.java("shop.till", "Receipt", "public record Receipt(long cents) {}")
        tree.java("shop.stock", "Shelf", "interface Shelf {\n    void restock();\n}")
        tree.java("shop.stock", "Aisle", "public enum Aisle {\n    LEFT,\n    RIGHT\n}")
        return graph.build([graph.java_root(tree.root)], scoring.load())

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

    def test_an_annotation_is_the_same_kind_written_as_one_token_or_two(self):
        """`@ interface` is legal Java, and it read as a plain interface.

        The kind is what a rule in the configuration file matches on and what the page
        prints, so an annotation arriving as an interface is a rule that can never fire
        on it and a card that says the wrong word — silently, on source the compiler is
        perfectly happy with.
        """
        tree = self.tree("fixture")
        tree.java("shop.till", "Marks", "public @ interface Marks {\n    String value();\n}")
        tree.java("shop.till", "AlsoMarks", "public @interface AlsoMarks {\n    String value();\n}")

        document = graph.build([graph.java_root(tree.root)], scoring.load())

        self.assertEqual([], document["source"]["unparsed"])
        self.assertEqual(
            {"AlsoMarks": "annotation", "Marks": "annotation"},
            {module["name"]: module["kind"] for module in document["modules"]},
        )

    def test_a_type_declared_inside_another_is_named_on_the_module_that_holds_it(self):
        tree = self.tree("fixture")
        tree.java(
            "shop.till",
            "Refused",
            "public class Refused extends RuntimeException {\n"
            "    public enum Kind {\n        NO_STOCK\n    }\n}",
        )

        document = graph.build([graph.java_root(tree.root)], scoring.load())

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

        rendered = page.render(document, graph.serialise(document)).decode("utf-8")

        self.assertEqual(document, json.loads(_embedded_graph(rendered)))

    def test_the_graph_the_page_carries_is_the_bytes_the_graph_document_was_written_as(self):
        document = self.source()

        rendered = page.render(document, graph.serialise(document)).decode("utf-8")

        self.assertEqual(
            graph.serialise(document).decode("utf-8").rstrip("\n"),
            _unescaped(_embedded_graph(rendered)),
        )

    def test_nothing_on_the_page_comes_from_anywhere_but_the_graph_document(self):
        document = self.source()

        rendered = page.render(document, graph.serialise(document)).decode("utf-8")

        outside_the_graph = rendered.replace(_embedded_graph(rendered), "")
        for module in document["modules"]:
            self.assertNotIn(module["name"], outside_the_graph)
            self.assertNotIn(module["package"], outside_the_graph)

    def test_the_document_says_which_shape_it_is(self):
        """An agent reading the graph is promised a shape, so the document has to name it.

        The tool refuses a *configuration* whose schema it does not know; versioning what
        it writes is the same promise kept in the other direction. It moved to /2 when the
        document grew a `scoring` object and gave every module an `interface` and an
        `excludedBy` — a reader looking for what /1 promised finds none of them.
        """
        document = self.source()

        self.assertEqual(graph.SCHEMA, document["schema"])

    def test_the_page_draws_the_shape_the_graph_writes(self):
        """The renderer reads one shape, and the file it reads is written by one writer.

        The page reaches straight into `scoring` and `interface`, so a document of an
        older shape throws halfway down a single pass — a page that ends early rather
        than one that says it could not be drawn. It checks the version first, and the
        version it checks for has to be the version the graph stamps: two constants that
        drift apart would refuse every document the tool itself writes.
        """
        document = self.source()

        rendered = page.render(document, graph.serialise(document)).decode("utf-8")

        outside_the_graph = rendered.replace(_embedded_graph(rendered), "")
        self.assertIn(graph.SCHEMA, outside_the_graph)

    def test_the_page_loads_nothing_from_outside_itself(self):
        document = self.source()
        rendered = page.render(document, graph.serialise(document)).decode("utf-8")

        self.assertNotIn("http://", rendered)
        self.assertNotIn("https://", rendered)
        self.assertEqual([], re.findall(r"\b(?:src|href)\s*=", rendered))

    def test_the_page_is_legible_in_either_theme(self):
        document = self.source()
        rendered = page.render(document, graph.serialise(document)).decode("utf-8")

        self.assertIn("color-scheme: light dark", rendered)
        self.assertIn("@media (prefers-color-scheme: dark)", rendered)


def _embedded_graph(rendered):
    opening = '<script id="%s" type="application/json">\n' % page.GRAPH_ELEMENT_ID
    start = rendered.index(opening) + len(opening)
    end = rendered.index("\n</script>", start)
    return rendered[start:end]


def _unescaped(embedded):
    return embedded.replace("\\u003c", "<").replace("\\u003e", ">").replace("\\u0026", "&")


class NoDeclarationIsWalkedPastTest(SourceTreeTest):
    """Declarations that do not open their own line reach the page like any other.

    Every one of these is legal Java that a line-anchored pattern drops without a word:
    the module simply is not there, and nothing on the page says so. The page's claim is
    that it shows every module, so these are the shapes that claim has to survive.
    """

    def test_a_declaration_after_an_annotation_on_the_same_line_is_still_a_module(self):
        tree = self.tree("fixture")
        tree.raw(
            "shop/till/Foo.java",
            "package shop.till;\n\n@Deprecated public class Foo {}\nclass Bar {}\n",
        )

        document = graph.build([graph.java_root(tree.root)], scoring.load())

        self.assertEqual([], document["source"]["unparsed"])
        self.assertEqual(["shop.till.Bar", "shop.till.Foo"], [m["id"] for m in document["modules"]])

    def test_a_nested_declaration_after_an_annotation_is_named_on_its_module(self):
        tree = self.tree("fixture")
        tree.java(
            "shop.till",
            "Outer",
            "public class Outer {\n    @Deprecated public enum Kind { A }\n}",
        )

        document = graph.build([graph.java_root(tree.root)], scoring.load())

        self.assertEqual(["Kind"], document["modules"][0]["nested"])

    def test_a_declaration_that_is_not_first_on_its_line_is_still_found(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "B", "class B { class N {} }")

        document = graph.build([graph.java_root(tree.root)], scoring.load())

        self.assertEqual(["shop.till.B"], [m["id"] for m in document["modules"]])
        self.assertEqual(["N"], document["modules"][0]["nested"])


class NothingIsInventedThatTheSourceDoesNotDeclareTest(SourceTreeTest):
    """The page may not show a module the source does not have, either."""

    def test_a_comment_opening_with_slash_star_slash_is_a_comment_all_the_way(self):
        tree = self.tree("fixture")
        tree.raw(
            "shop/till/Real.java",
            "package shop.till;\n\n/*/ class Ghost {} */\npublic class Real {}\n",
        )

        document = graph.build([graph.java_root(tree.root)], scoring.load())

        self.assertEqual(["shop.till.Real"], [m["id"] for m in document["modules"]])
        self.assertEqual([], document["source"]["unparsed"])

    def test_a_module_records_the_number_of_lines_its_file_actually_has(self):
        tree = self.tree("fixture")
        tree.raw(
            "shop/till/Till.java",
            "package shop.till;\n\npublic class Till {\n}\n",
        )

        document = graph.build([graph.java_root(tree.root)], scoring.load())

        self.assertEqual(4, document["modules"][0]["lines"])

    def test_two_files_declaring_the_same_module_are_refused_rather_than_drawn_twice(self):
        first, second = self.tree("first"), self.tree("second")
        for tree in (first, second):
            tree.java("shop.till", "Till", "public class Till {}")
        roots = [graph.java_root(first.root), graph.java_root(second.root)]

        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            with self.assertRaises(graph.DuplicateModules):
                graph.build(roots, scoring.load())

        self.assertIn("shop.till.Till", logged.output[0])
        self.assertIn("first/shop/till/Till.java", logged.output[0])
        self.assertIn("second/shop/till/Till.java", logged.output[0])

    def test_the_command_refuses_that_run_rather_than_writing_a_page_it_cannot_draw(self):
        first, second = self.tree("first"), self.tree("second")
        for tree in (first, second):
            tree.java("shop.till", "Till", "public class Till {}")
        graph_path = os.path.join(self.scratch, "out", "graph.json")
        page_path = os.path.join(self.scratch, "out", "page.html")

        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            exit_code = cli.main(
                ["--source", first.root, "--source", second.root,
                 "--graph", graph_path, "--page", page_path, "--log-level", "WARNING"]
            )

        self.assertEqual(3, exit_code)
        self.assertTrue(any("refused to run" in line for line in logged.output))
        self.assertFalse(os.path.exists(graph_path))
        self.assertFalse(os.path.exists(page_path))


class NestedTypesAreToldApartTest(SourceTreeTest):
    """A nested name says where it sits, so two of them are never the same word twice.

    `nested: B, C, C, D` is unreadable: a reader cannot tell which `C` is which, and the
    repeat looks like a fault in the page rather than a fact about the source.
    """

    def test_two_nested_types_with_the_same_name_are_named_by_where_they_sit(self):
        tree = self.tree("fixture")
        tree.java(
            "shop.till",
            "A",
            "class A {\n"
            "    class B { class C {} }\n"
            "    class D { class C {} }\n"
            "}",
        )

        document = graph.build([graph.java_root(tree.root)], scoring.load())

        self.assertEqual(["B", "B.C", "D", "D.C"], document["modules"][0]["nested"])

    def test_a_type_declared_inside_a_method_belongs_to_the_module_that_holds_it(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "A", "class A {\n    void m() { class Row {} }\n}")

        document = graph.build([graph.java_root(tree.root)], scoring.load())

        self.assertEqual(["shop.till.A"], [m["id"] for m in document["modules"]])
        self.assertEqual(["Row"], document["modules"][0]["nested"])

    def test_the_same_nested_name_is_carried_once_rather_than_drawn_twice(self):
        tree = self.tree("fixture")
        tree.java(
            "shop.till",
            "A",
            "class A {\n    void m() { class Row {} }\n    void n() { class Row {} }\n}",
        )

        document = graph.build([graph.java_root(tree.root)], scoring.load())

        self.assertEqual(["Row"], document["modules"][0]["nested"])


class LegalJavaIsNeverCalledUnreadableTest(SourceTreeTest):
    """The alarm band only stays worth reading while it never cries wolf.

    Every fixture here is Java the compiler accepts. Failing one of them would take two
    real modules off the page and paint the band over a file with nothing wrong with it.
    """

    def parsed(self, name, body):
        tree = self.tree("fixture")
        tree.raw("shop/till/%s.java" % name, body)

        document = graph.build([graph.java_root(tree.root)], scoring.load())

        self.assertEqual([], document["source"]["unparsed"])
        return document

    def test_a_text_block_holding_an_escaped_triple_quote_is_read(self):
        document = self.parsed(
            "A",
            "package shop.till;\n\n"
            "public class A {\n"
            '    static final String S = """\n'
            '        say \\"""\n'
            '        """;\n'
            "    class Real {}\n"
            "}\n",
        )

        self.assertEqual(["shop.till.A"], [m["id"] for m in document["modules"]])
        self.assertEqual(["Real"], document["modules"][0]["nested"])

    def test_a_text_block_holding_one_or_two_quotes_is_read(self):
        document = self.parsed(
            "B",
            "package shop.till;\n\n"
            "public class B {\n"
            '    static final String S = """\n'
            '        a " and a "" and a } brace\n'
            '        """;\n'
            "}\n",
        )

        self.assertEqual(["shop.till.B"], [m["id"] for m in document["modules"]])

    def test_a_string_holding_an_escaped_backslash_or_quote_is_read(self):
        document = self.parsed(
            "C",
            "package shop.till;\n\n"
            "public class C {\n"
            '    static final String BACKSLASH = "\\\\";\n'
            '    static final String QUOTE = "\\"";\n'
            "    static final char TICK = '\\'';\n"
            "}\n",
        )

        self.assertEqual(["shop.till.C"], [m["id"] for m in document["modules"]])
