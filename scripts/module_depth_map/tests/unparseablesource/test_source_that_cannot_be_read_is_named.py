"""A file the tool cannot read is named and counted, never scored as an empty module."""

import logging
import os

from ... import cli, graph, page
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


class SourceThatCannotBeOpenedIsNamedTest(SourceTreeTest):
    """A file that will not open at all costs one card, never the whole page.

    A dangling symlink, a file with no read permission and a file deleted since the walk
    all arrive as OSError. Letting one out of the parser ends the run with a traceback
    and writes neither output, so a single unreadable file destroys the page instead of
    being named on it.
    """

    def tree_with_one_unopenable_file(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {}")
        os.symlink("/no/such/target.java", os.path.join(tree.root, "shop", "till", "Gone.java"))
        return tree

    def test_the_file_is_named_and_counted_rather_than_ending_the_run(self):
        document = graph.build([graph.java_root(self.tree_with_one_unopenable_file().root)])

        self.assertEqual(1, document["source"]["filesUnparsed"])
        failure = document["source"]["unparsed"][0]
        self.assertEqual("shop/till/Gone.java", failure["path"])
        self.assertIn("could not be opened", failure["reason"])
        self.assertEqual(["shop.till.Till"], [module["id"] for module in document["modules"]])

    def test_the_reason_says_what_went_wrong_without_naming_this_machine(self):
        document = graph.build([graph.java_root(self.tree_with_one_unopenable_file().root)])

        reason = document["source"]["unparsed"][0]["reason"]
        self.assertNotIn(self.scratch, reason)
        self.assertNotIn("/", reason)

    def test_the_run_warns_by_name(self):
        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            graph.build([graph.java_root(self.tree_with_one_unopenable_file().root)])

        warnings = [line for line in logged.output if "could not parse" in line]
        self.assertEqual(1, len(warnings))
        self.assertIn("shop/till/Gone.java", warnings[0])

    def test_both_outputs_are_still_written(self):
        tree = self.tree_with_one_unopenable_file()
        graph_path = os.path.join(self.scratch, "out", "graph.json")
        page_path = os.path.join(self.scratch, "out", "page.html")

        exit_code = cli.main(
            ["--source", tree.root, "--graph", graph_path, "--page", page_path,
             "--log-level", "ERROR"]
        )

        self.assertEqual(0, exit_code)
        self.assertTrue(os.path.exists(graph_path))
        self.assertTrue(os.path.exists(page_path))


class ADeclarationTheParserCannotReadIsNamedTest(SourceTreeTest):
    """Java the patterns do not match fails the file loudly instead of shrinking it.

    This is the promise behind every count on the page: silence means nothing was
    missed. A declaration keyword the parser walked past would otherwise cost a module
    with no warning, no unparsed entry and nothing on the page to say so.
    """

    def test_a_declaration_keyword_that_matched_nothing_fails_the_file_by_name(self):
        tree = self.tree("fixture")
        tree.raw(
            "shop/till/Odd.java",
            "package shop.till;\n\npublic class Odd {}\n\nclass 2Bad {}\n",
        )

        document = graph.build([graph.java_root(tree.root)])

        self.assertEqual([], document["modules"])
        self.assertEqual(1, document["source"]["filesUnparsed"])
        reason = document["source"]["unparsed"][0]["reason"]
        self.assertIn("cannot read", reason)
        self.assertIn("line 5", reason)

    def test_a_class_literal_is_not_mistaken_for_a_declaration_it_missed(self):
        tree = self.tree("fixture")
        tree.java(
            "shop.till",
            "Till",
            "public class Till {\n"
            "    static final Class<?> SELF = Till.class;\n"
            "    void log() { report(Till.class.getName()); }\n"
            "}",
        )

        document = graph.build([graph.java_root(tree.root)])

        self.assertEqual([], document["source"]["unparsed"])
        self.assertEqual(["shop.till.Till"], [module["id"] for module in document["modules"]])


class AFileDeclaringNoTypeOnPurposeIsNotAnAlarmTest(SourceTreeTest):
    """`package-info.java` and `module-info.java` are well formed and declare no module.

    Counting them as unreadable paints the page's alarm band over files with nothing
    wrong with them, and an alarm that cries wolf stops being read.
    """

    def tree_with_both_descriptors(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {}")
        tree.raw("shop/till/package-info.java", "/**\n * What lives here.\n */\npackage shop.till;\n")
        tree.raw("module-info.java", "module shop.till {\n    requires java.base;\n}\n")
        return tree

    def test_neither_is_reported_as_a_file_that_could_not_be_read(self):
        document = graph.build([graph.java_root(self.tree_with_both_descriptors().root)])

        self.assertEqual([], document["source"]["unparsed"])
        self.assertEqual(3, document["source"]["filesSeen"])
        self.assertEqual(3, document["source"]["filesParsed"])

    def test_neither_becomes_a_module(self):
        document = graph.build([graph.java_root(self.tree_with_both_descriptors().root)])

        self.assertEqual(["shop.till.Till"], [module["id"] for module in document["modules"]])

    def test_the_page_draws_no_alarm_when_every_file_was_read(self):
        document = graph.build([graph.java_root(self.tree_with_both_descriptors().root)])

        rendered = page.render(document).decode("utf-8")

        self.assertNotIn("package-info.java", rendered)
