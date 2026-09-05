"""A file the tool cannot read is named and counted, never scored as an empty module."""

import logging
import os
import unittest

from ... import cli, graph, page, scoring
from ..support.sourcetrees import SourceTreeTest


class SourceThatCannotBeReadIsNamedTest(SourceTreeTest):

    def tree_with_one_broken_file(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")
        tree.raw("shop/till/Broken.java", "package shop.till;\n\npublic class Broken {\n")
        return tree

    def test_the_file_is_named_in_the_graph_with_the_reason_it_could_not_be_read(self):
        document = graph.build([graph.source_root(self.tree_with_one_broken_file().root)], scoring.load())

        self.assertEqual(1, len(document["source"]["unparsed"]))
        failure = document["source"]["unparsed"][0]
        self.assertEqual("shop/till/Broken.java", failure["path"])
        self.assertEqual("fixture", failure["root"])
        self.assertIn("braces do not balance", failure["reason"])

    def test_the_file_is_not_turned_into_a_module_with_nothing_in_it(self):
        document = graph.build([graph.source_root(self.tree_with_one_broken_file().root)], scoring.load())

        self.assertEqual(["shop.till.Till"], [module["id"] for module in document["modules"]])

    def test_the_run_says_how_many_files_it_read_and_how_many_it_could_not(self):
        document = graph.build([graph.source_root(self.tree_with_one_broken_file().root)], scoring.load())

        self.assertEqual(2, document["source"]["filesSeen"])
        self.assertEqual(1, document["source"]["filesParsed"])
        self.assertEqual(1, document["source"]["filesUnparsed"])

    def test_the_run_warns_by_name_rather_than_passing_over_it(self):
        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            graph.build([graph.source_root(self.tree_with_one_broken_file().root)], scoring.load())

        warnings = [line for line in logged.output if "could not parse" in line]
        self.assertEqual(1, len(warnings))
        self.assertIn("shop/till/Broken.java", warnings[0])
        self.assertIn("braces do not balance", warnings[0])

    def test_the_page_says_which_files_it_was_not_drawn_from(self):
        document = graph.build([graph.source_root(self.tree_with_one_broken_file().root)], scoring.load())

        rendered = page.render(document, graph.serialise(document)).decode("utf-8")

        self.assertIn("shop/till/Broken.java", rendered)
        self.assertIn("could not be read", rendered)

    def test_a_file_with_no_type_declaration_at_all_is_reported_rather_than_ignored(self):
        tree = self.tree("fixture")
        tree.raw("shop/till/notes.java", "package shop.till;\n\n// nothing but a note\n")

        document = graph.build([graph.source_root(tree.root)], scoring.load())

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

        document = graph.build([graph.source_root(tree.root)], scoring.load())

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

        document = graph.build([graph.source_root(tree.root)], scoring.load())

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
        document = graph.build([graph.source_root(self.tree_with_one_unopenable_file().root)], scoring.load())

        self.assertEqual(1, document["source"]["filesUnparsed"])
        failure = document["source"]["unparsed"][0]
        self.assertEqual("shop/till/Gone.java", failure["path"])
        self.assertIn("could not be opened", failure["reason"])
        self.assertEqual(["shop.till.Till"], [module["id"] for module in document["modules"]])

    def test_the_reason_says_what_went_wrong_without_naming_this_machine(self):
        document = graph.build([graph.source_root(self.tree_with_one_unopenable_file().root)], scoring.load())

        reason = document["source"]["unparsed"][0]["reason"]
        self.assertNotIn(self.scratch, reason)
        self.assertNotIn("/", reason)

    def test_the_run_warns_by_name(self):
        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            graph.build([graph.source_root(self.tree_with_one_unopenable_file().root)], scoring.load())

        warnings = [line for line in logged.output if "could not read source file" in line]
        self.assertEqual(1, len(warnings))
        self.assertIn("shop/till/Gone.java", warnings[0])
        self.assertIn("could not be opened", warnings[0])

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

        document = graph.build([graph.source_root(tree.root)], scoring.load())

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

        document = graph.build([graph.source_root(tree.root)], scoring.load())

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
        document = graph.build([graph.source_root(self.tree_with_both_descriptors().root)], scoring.load())

        self.assertEqual([], document["source"]["unparsed"])
        self.assertEqual(3, document["source"]["filesSeen"])
        self.assertEqual(3, document["source"]["filesParsed"])

    def test_neither_becomes_a_module(self):
        document = graph.build([graph.source_root(self.tree_with_both_descriptors().root)], scoring.load())

        self.assertEqual(["shop.till.Till"], [module["id"] for module in document["modules"]])

    def test_the_page_draws_no_alarm_when_every_file_was_read(self):
        document = graph.build([graph.source_root(self.tree_with_both_descriptors().root)], scoring.load())

        rendered = page.render(document, graph.serialise(document)).decode("utf-8")

        self.assertNotIn("package-info.java", rendered)


class AnUnclosedRegionIsNamedTest(SourceTreeTest):
    """A comment or literal that is never closed swallows the rest of the file.

    This is the most ordinary way a Java file is broken mid-edit, and the worst shape of
    failure this tool can have: everything after the opener is blanked, so the parser
    sees a short, well-formed file and says nothing at all. Every one of these fixtures
    hides two declarations behind the opener, and the test is that the file is named
    rather than that the declarations are found.
    """

    def failure_for(self, body):
        tree = self.tree("fixture")
        tree.raw("shop/till/A.java", body)

        document = graph.build([graph.source_root(tree.root)], scoring.load())

        self.assertEqual([], document["modules"])
        self.assertEqual(1, document["source"]["filesUnparsed"])
        return document["source"]["unparsed"][0]["reason"]

    def test_a_block_comment_that_is_never_closed_is_named_with_the_line_it_opened_on(self):
        reason = self.failure_for(
            "package shop.till;\npublic class A {}\n/* forgot to close\n"
            "class B { void x() {} }\nclass C { void y() {} }\n"
        )

        self.assertEqual("block comment is never closed: opened on line 3", reason)

    def test_a_text_block_that_is_never_closed_is_named(self):
        reason = self.failure_for(
            'package shop.till;\npublic class A {\n    String s = """\n    hello\n}\nclass B {}\n'
        )

        self.assertEqual("text block is never closed: opened on line 3", reason)

    def test_a_string_that_is_never_closed_is_named(self):
        reason = self.failure_for(
            'package shop.till;\npublic class A {\n    String s = "oops;\n}\nclass B {}\n'
        )

        self.assertEqual("string literal is never closed: opened on line 3", reason)

    def test_a_character_literal_that_is_never_closed_is_named(self):
        reason = self.failure_for(
            "package shop.till;\npublic class A {\n    char c = 'x;\n}\nclass B {}\n"
        )

        self.assertEqual("character literal is never closed: opened on line 3", reason)

    def test_the_run_warns_rather_than_dropping_the_declarations_quietly(self):
        tree = self.tree("fixture")
        tree.raw("shop/till/A.java", "package shop.till;\npublic class A {}\n/* open\nclass B {}\n")

        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            graph.build([graph.source_root(tree.root)], scoring.load())

        warnings = [line for line in logged.output if "could not parse" in line]
        self.assertEqual(1, len(warnings))
        self.assertIn("shop/till/A.java", warnings[0])
        self.assertIn("block comment is never closed", warnings[0])


class BracesThatCloseMoreThanTheyOpenAreNamedTest(SourceTreeTest):
    """Unbalanced braces that happen to net to zero are unbalanced all the same.

    Counting only the total misses the whole class of file where a stray `}` shifts every
    later declaration to a depth the source does not have. That does not merely lose a
    module: it can hang a top-level type off an unrelated one as a nested type, which is
    a wrong answer rather than a missing one.
    """

    def failure_for(self, body):
        tree = self.tree("fixture")
        tree.raw("shop/till/A.java", body)

        document = graph.build([graph.source_root(tree.root)], scoring.load())

        self.assertEqual([], document["modules"])
        self.assertEqual(1, document["source"]["filesUnparsed"])
        return document["source"]["unparsed"][0]["reason"]

    def test_a_closing_brace_with_nothing_open_is_named_with_its_line(self):
        reason = self.failure_for("package shop.till;\n}\nclass Vanished {\nclass Real {}\n")

        self.assertEqual(
            "braces do not balance: a closing brace with nothing open on line 2", reason
        )

    def test_a_type_is_never_drawn_as_nested_under_one_that_does_not_hold_it(self):
        reason = self.failure_for("package shop.till;\npublic class A {}\n}\nclass B {\n")

        self.assertEqual(
            "braces do not balance: a closing brace with nothing open on line 3", reason
        )

    def test_a_type_the_parser_cannot_place_is_named_rather_than_attributed(self):
        reason = self.failure_for(
            "package shop.till;\npublic class A {}\nvoid stray() { class L {} }\n"
        )

        self.assertIn("a type this parser cannot place: L on line 3", reason)


class ADirectoryThatWillNotOpenIsNamedTest(SourceTreeTest):
    """A directory the walk cannot list takes every module in it off the page.

    Silently, if nothing looks: the files under it are never seen, so they are neither
    parsed nor reported, and every count on the page still adds up. That is the same
    failure the per-file rule exists to prevent, one level up.
    """

    def tree_with_one_locked_directory(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {}")
        tree.java("shop.locked", "Hidden", "public class Hidden {}")
        locked = os.path.join(tree.root, "shop", "locked")
        os.chmod(locked, 0o000)
        self.addCleanup(os.chmod, locked, 0o755)
        return tree

    @unittest.skipIf(hasattr(os, "geteuid") and os.geteuid() == 0, "root can read anything")
    def test_the_directory_is_named_and_counted_rather_than_passed_over(self):
        document = graph.build([graph.source_root(self.tree_with_one_locked_directory().root)], scoring.load())

        self.assertEqual(["shop.till.Till"], [module["id"] for module in document["modules"]])
        self.assertEqual(1, document["source"]["filesUnparsed"])
        failure = document["source"]["unparsed"][0]
        self.assertEqual("shop/locked", failure["path"])
        self.assertIn("directory could not be read", failure["reason"])

    @unittest.skipIf(hasattr(os, "geteuid") and os.geteuid() == 0, "root can read anything")
    def test_the_run_warns_by_name(self):
        tree = self.tree_with_one_locked_directory()

        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            graph.build([graph.source_root(tree.root)], scoring.load())

        warnings = [line for line in logged.output if "could not read source directory" in line]
        self.assertEqual(1, len(warnings))
        self.assertIn("shop/locked", warnings[0])


class SourceReachedTwiceIsReadOnceTest(SourceTreeTest):
    """A symlinked directory is followed, but the same directory is not read twice.

    Stepping over a symlinked directory would drop the modules under it without a word;
    walking into the same directory twice would draw each of them twice and then refuse
    the whole run for duplicate ids. Neither is what the reader asked for.
    """

    def test_a_symlinked_directory_is_read_rather_than_skipped(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {}")
        elsewhere = os.path.join(self.scratch, "elsewhere", "shop", "stock")
        os.makedirs(elsewhere)
        with open(os.path.join(elsewhere, "Shelf.java"), "w", encoding="utf-8") as handle:
            handle.write("package shop.stock;\n\npublic class Shelf {}\n")
        os.symlink(os.path.join(self.scratch, "elsewhere", "shop", "stock"),
                   os.path.join(tree.root, "shop", "stock"))

        document = graph.build([graph.source_root(tree.root)], scoring.load())

        self.assertEqual([], document["source"]["unparsed"])
        self.assertEqual(
            ["shop.stock.Shelf", "shop.till.Till"], [module["id"] for module in document["modules"]]
        )

    def test_a_directory_reachable_twice_is_read_once_and_the_repeat_is_said_out_loud(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {}")
        os.symlink(os.path.join(tree.root, "shop"), os.path.join(tree.root, "again"))

        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            document = graph.build([graph.source_root(tree.root)], scoring.load())

        self.assertEqual(["shop.till.Till"], [module["id"] for module in document["modules"]])
        self.assertTrue(any("not reading source directory" in line for line in logged.output))


class LegalJavaIsNotFailedForBeingWrittenTightlyTest(SourceTreeTest):
    """A file the compiler reads has to be a file this parser reads.

    The alarm band over a file with nothing wrong with it is the same fault as a module
    priced at zero for a shape nobody understood, one step louder: a reader who meets a
    false alarm reads the next one less carefully, and the whole promise of the band is
    that it never cries wolf.

    The shape asserted here is a modifier written flush against a type-parameter list.
    `javac` compiles it; this parser read `static` as part of the return type, called the
    whole thing `static<T> T`, and failed the file — every module in it off the page for a
    space nobody wrote. It was found by review rather than by this suite, which is the
    reason it is written down here rather than only fixed.
    """

    def read(self, body):
        tree = self.tree("fixture")
        tree.raw("shop/till/A.java", body)

        document = graph.build([graph.source_root(tree.root)], scoring.load())

        self.assertEqual([], document["source"]["unparsed"])
        return {module["name"]: module for module in document["modules"]}

    def test_a_modifier_flush_against_a_type_parameter_list_is_read_rather_than_failed(self):
        modules = self.read(
            "package shop.till;\npublic class A {\n"
            "    static<T> T first(T only) { return only; }\n}\n"
        )
        interface = modules["A"]["interface"]

        self.assertEqual(["first"], [method["name"] for method in interface["methods"]])
        self.assertEqual(["T"], interface["methods"][0]["parameters"])
        self.assertEqual("T", interface["methods"][0]["returns"])
        self.assertEqual([], interface["typesCrossingTheSeam"])

    def test_a_type_written_flush_against_the_name_it_declares_is_read_too(self):
        """The two neighbouring spellings, which have always worked and have to keep working."""
        modules = self.read(
            "package shop.till;\npublic class A {\n"
            "    public java.util.List<String>all() { return null; }\n"
            "    public String[]some() { return null; }\n}\n"
        )

        self.assertEqual(
            ["all", "some"],
            [method["name"] for method in modules["A"]["interface"]["methods"]],
        )


class AShapeThatWouldMakeAnInterfaceCheaperIsNamedTest(SourceTreeTest):
    """The three places where losing part of a declaration would look like a finding.

    A parameter that cannot be read, a member handing back something that is not a type,
    and a declaration whose body cannot be found are all places where this parser can
    carry on with less of an interface than the source has. Less interface is a lower
    cost, a lower cost is a shorter bar, and a short bar is what this page calls deep. So
    each of them fails the file by name and by line instead, the way an unclosed comment
    does — the reader is told which shape was not understood rather than being handed a
    module that looks well designed.
    """

    def failure_for(self, body):
        tree = self.tree("fixture")
        tree.raw("shop/till/A.java", body)

        document = graph.build([graph.source_root(tree.root)], scoring.load())

        self.assertEqual([], document["modules"])
        self.assertEqual(1, document["source"]["filesUnparsed"])
        return document["source"]["unparsed"][0]["reason"]

    def test_a_parameter_that_cannot_be_read_is_named_with_its_line(self):
        reason = self.failure_for(
            "package shop.till;\npublic class A {\n    public void take(int) {}\n}\n"
        )

        self.assertEqual("a parameter this parser cannot read: 'int' on line 3", reason)

    def test_a_record_component_that_cannot_be_read_is_named_too(self):
        reason = self.failure_for("package shop.till;\npublic record A(long) {}\n")

        self.assertEqual("a parameter this parser cannot read: 'long' on line 2", reason)

    def test_a_member_handing_back_something_that_is_not_a_type_is_named(self):
        reason = self.failure_for(
            "package shop.till;\npublic class A {\n    int one, two() { return 2; }\n}\n"
        )

        self.assertIn("a member this parser cannot read: two on line 3", reason)
        self.assertIn("hands back", reason)

    def test_a_declaration_whose_body_cannot_be_found_is_named(self):
        """Every kind of type Java declares has a body, so not finding one is being lost."""
        reason = self.failure_for("package shop.till;\nclass A\n")

        self.assertEqual(
            "a type declaration whose body this parser cannot find: A on line 2", reason
        )

    def test_the_run_warns_by_name_rather_than_drawing_a_cheaper_module(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")
        tree.raw(
            "shop/till/A.java",
            "package shop.till;\npublic class A {\n    public void take(int) {}\n}\n",
        )

        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            document = graph.build([graph.source_root(tree.root)], scoring.load())

        warnings = [line for line in logged.output if "could not parse" in line]
        self.assertEqual(1, len(warnings))
        self.assertIn("shop/till/A.java", warnings[0])
        self.assertIn("a parameter this parser cannot read", warnings[0])
        self.assertEqual(["shop.till.Till"], [module["id"] for module in document["modules"]])
