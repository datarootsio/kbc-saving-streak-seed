"""A file the tool cannot read is named and counted, never scored as an empty module."""

import logging

from ... import graph, page
from ..support.sourcetrees import SourceTreeTest


class SourceThatCannotBeReadIsNamedTest(SourceTreeTest):

    def tree_with_one_broken_file(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")
        tree.raw("shop/till/Broken.java", "package shop.till;\n\npublic class Broken {\n")
        return tree

    def test_the_file_is_named_in_the_graph_with_the_reason_it_could_not_be_read(self):
        document = graph.build([graph.java_root(self.tree_with_one_broken_file().root)])

        self.assertEqual(1, len(document["source"]["unparsed"]))
        failure = document["source"]["unparsed"][0]
        self.assertEqual("shop/till/Broken.java", failure["path"])
        self.assertEqual("fixture", failure["root"])
        self.assertIn("braces do not balance", failure["reason"])

    def test_the_file_is_not_turned_into_a_module_with_nothing_in_it(self):
        document = graph.build([graph.java_root(self.tree_with_one_broken_file().root)])

        self.assertEqual(["shop.till.Till"], [module["id"] for module in document["modules"]])

    def test_the_run_says_how_many_files_it_read_and_how_many_it_could_not(self):
        document = graph.build([graph.java_root(self.tree_with_one_broken_file().root)])

        self.assertEqual(2, document["source"]["filesSeen"])
        self.assertEqual(1, document["source"]["filesParsed"])
        self.assertEqual(1, document["source"]["filesUnparsed"])

    def test_the_run_warns_by_name_rather_than_passing_over_it(self):
        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            graph.build([graph.java_root(self.tree_with_one_broken_file().root)])

        warnings = [line for line in logged.output if "could not parse" in line]
        self.assertEqual(1, len(warnings))
        self.assertIn("shop/till/Broken.java", warnings[0])
        self.assertIn("braces do not balance", warnings[0])

    def test_the_page_says_which_files_it_was_not_drawn_from(self):
        document = graph.build([graph.java_root(self.tree_with_one_broken_file().root)])

        rendered = page.render(document).decode("utf-8")

        self.assertIn("shop/till/Broken.java", rendered)
        self.assertIn("could not be read", rendered)

    def test_a_file_with_no_type_declaration_at_all_is_reported_rather_than_ignored(self):
        tree = self.tree("fixture")
        tree.raw("shop/till/notes.java", "package shop.till;\n\n// nothing but a note\n")

        document = graph.build([graph.java_root(tree.root)])

        self.assertEqual([], document["modules"])
        self.assertEqual(
            [{"root": "fixture", "path": "shop/till/notes.java", "reason": "no top-level type declaration"}],
            document["source"]["unparsed"],
        )

    def test_a_file_that_is_not_text_at_all_is_reported_rather_than_ignored(self):
        tree = self.tree("fixture")
        path = tree.raw("shop/till/Bytes.java", "")
        with open(path, "wb") as handle:
            handle.write(b"package shop.till;\n\xff\xfe\x00 class Bytes {}\n")

        document = graph.build([graph.java_root(tree.root)])

        self.assertEqual([], document["modules"])
        self.assertIn("not valid UTF-8", document["source"]["unparsed"][0]["reason"])

    def test_a_brace_inside_a_comment_or_a_string_does_not_make_a_file_unreadable(self):
        tree = self.tree("fixture")
        tree.java(
            "shop.till",
            "Till",
            "/** Opens with { and never closes it. */\n"
            "public class Till {\n"
            '    static final String CURLY = "}";\n'
            "    // } neither does this\n"
            "}",
        )

        document = graph.build([graph.java_root(tree.root)])

        self.assertEqual([], document["source"]["unparsed"])
        self.assertEqual(["shop.till.Till"], [module["id"] for module in document["modules"]])
