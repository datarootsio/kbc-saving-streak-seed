"""What the command says about the run, and what it leaves behind when it refuses.

A run nobody can read is a run nobody can check. These are the properties a reader of the
log relies on: the level asked for is the level given, a refusal says why at the level
this repository reserves for refusals, and a run that cannot finish leaves no half of a
pair of outputs behind.
"""

import logging
import os

from ... import cli, page
from ..support.sourcetrees import SourceTreeTest, bytes_of


class TheRunSaysWhatItDidTest(SourceTreeTest):

    def source(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {}")
        return tree

    def run_over(self, root, *arguments):
        return cli.main(
            ["--source", root,
             "--graph", os.path.join(self.scratch, "out", "graph.json"),
             "--page", os.path.join(self.scratch, "out", "page.html")] + list(arguments)
        )

    def test_one_line_says_what_the_run_read_and_what_it_wrote(self):
        tree = self.source()

        with self.assertLogs("module_depth_map", level=logging.INFO) as logged:
            self.assertEqual(0, self.run_over(tree.root))

        finished = [line for line in logged.output if "run finished" in line]
        self.assertEqual(1, len(finished))
        self.assertIn("filesParsed=1", finished[0])
        self.assertIn("modules=1", finished[0])

    def test_the_log_level_asked_for_is_the_level_given_on_a_second_run_too(self):
        """basicConfig does nothing once the root logger is configured, unless forced.

        Without that, the level of every run after the first is whatever the first run
        asked for — so a caller that runs this twice in one process, the suite included,
        gets a log it did not ask for and cannot tell.
        """
        tree = self.source()
        self.leave_the_root_logger_as_it_was_found()

        self.run_over(tree.root, "--log-level", "ERROR")
        self.run_over(tree.root, "--log-level", "DEBUG")

        self.assertEqual(logging.DEBUG, logging.getLogger().level)

    def leave_the_root_logger_as_it_was_found(self):
        """This test configures logging for the whole process, so it puts it back."""
        root = logging.getLogger()
        level, handlers = root.level, list(root.handlers)
        self.addCleanup(setattr, root, "handlers", handlers)
        self.addCleanup(root.setLevel, level)

    def test_every_member_not_read_as_a_method_says_which_one_and_why(self):
        """The one decision here that cannot fail loudly, so it leaves a line instead.

        A member with a parameter list is either a method or one of three things that
        read like one, and the parser cannot prove which. When it decides "not a method"
        it is either right or it has just lost part of an interface — twice now it had —
        so a reader at DEBUG gets the member, the line and the reason, and can argue.
        """
        tree = self.tree("fixture")
        tree.java(
            "shop.till", "Till",
            "public class Till {\n"
            "    private static final String NAME = String.valueOf(1);\n"
            "    record Row(long id) {}\n"
            "    public Till(long id) {}\n"
            "    public void ring() {}\n"
            "}",
        )

        with self.assertLogs("module_depth_map", level=logging.DEBUG) as logged:
            self.assertEqual(0, self.run_over(tree.root))

        declined = [line for line in logged.output if "member not read as a method" in line]
        self.assertEqual(3, len(declined), logged.output)
        self.assertIn("so it is a field", " ".join(declined))
        self.assertIn("names a type in it", " ".join(declined))
        self.assertIn("so it is a constructor", " ".join(declined))
        for line in declined:
            self.assertIn("line=", line)
            self.assertNotIn("ring()", line)

    def test_the_interface_of_a_module_that_is_never_scored_is_reported_too(self):
        """An excluded module is drawn with its interface, so the log has to hold one.

        The page shows a never-scored module the name of its rule and nothing else, so
        DEBUG is the only place a reader can check that the rule declined something real
        rather than something the parser lost — which is exactly the blind spot the
        record whose accessor was counted twice lived in. The line sat inside the branch
        that prices an interface, so all thirty-six of them said only that they had been
        excluded.
        """
        tree = self.tree("fixture")
        tree.java("shop.till", "Receipt", "public record Receipt(long cents, String iban) {}")

        with self.assertLogs("module_depth_map", level=logging.DEBUG) as logged:
            self.assertEqual(0, self.run_over(tree.root))

        read = [line for line in logged.output if "interface read" in line]
        self.assertEqual(1, len(read), logged.output)
        self.assertIn("name=Receipt", read[0])
        self.assertIn("methods=2", read[0])
        self.assertIn("cost=none", read[0])
        self.assertIn(
            "module excluded from scoring name=Receipt", "\n".join(logged.output)
        )

    def test_a_refusal_says_why_at_the_level_this_repository_reserves_for_refusals(self):
        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            exit_code = self.run_over(os.path.join(self.scratch, "no-such-directory"))

        self.assertEqual(2, exit_code)
        self.assertEqual(["WARNING"], [line.split(":")[0] for line in logged.output])
        self.assertIn("no such source directory", logged.output[0])


class NeitherOutputIsWrittenWithoutTheOtherTest(SourceTreeTest):
    """The graph and the page are written together, or neither is and the run says so.

    A page that failed to render after the graph had landed would leave a fresh document
    beside a stale picture of it — and the stale one is the file a reader opens. Every
    test here drives one of the ways that can happen and asserts the same three things:
    the previous run's files are still the previous run's, nothing is left half written,
    and the reason is in the log rather than in a traceback.
    """

    def a_module(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {}")
        return tree

    def a_previous_run(self):
        """An output directory with both of last run's files already in it."""
        out = os.path.join(self.scratch, "out")
        os.makedirs(out)
        for name in ("graph.json", "page.html"):
            with open(os.path.join(out, name), "wb") as handle:
                handle.write(b"the previous run's " + name.encode("ascii"))
        return out

    def test_a_page_that_cannot_be_rendered_leaves_no_graph_behind(self):
        tree = self.a_module()
        graph_path = os.path.join(self.scratch, "out", "graph.json")
        rendering = page.render
        page.render = _refuse_to_render
        self.addCleanup(setattr, page, "render", rendering)

        with self.assertLogs("module_depth_map.cli", level=logging.ERROR) as logged:
            code = cli.main(
                ["--source", tree.root, "--graph", graph_path,
                 "--page", os.path.join(self.scratch, "out", "page.html")]
            )

        self.assertEqual(5, code)
        self.assertFalse(os.path.exists(graph_path))
        self.assertIn("no room on the disk for a page", "\n".join(logged.output))
        self.assertIn("Traceback", "\n".join(logged.output))

    def test_a_page_that_cannot_be_written_leaves_the_graph_as_it_was(self):
        """The other half of the same claim, and the one rendering to bytes first misses.

        Rendering both outputs before writing either only closes the window where the
        page cannot be built. The window where it cannot be *written* — a read-only
        directory, a full disk — is still open if the graph is written in place first,
        and it leaves behind exactly what this class says cannot happen.
        """
        tree = self.a_module()
        out = self.a_previous_run()
        blocker = os.path.join(out, "not-a-directory")
        with open(blocker, "w", encoding="utf-8") as handle:
            handle.write("a file, so nothing can be written underneath it")

        with self.assertLogs("module_depth_map.cli", level=logging.WARNING) as logged:
            code = cli.main(
                ["--source", tree.root, "--graph", os.path.join(out, "graph.json"),
                 "--page", os.path.join(blocker, "page.html")]
            )

        self.assertEqual(5, code)
        self.assertEqual(b"the previous run's graph.json", bytes_of(os.path.join(out, "graph.json")))
        self.assertEqual(
            ["graph.json", "not-a-directory", "page.html"], sorted(os.listdir(out))
        )
        # The output that failed, not the one that staged perfectly well: a refusal
        # naming the file a reader can still open is a refusal pointing them at the
        # wrong file to go and fix.
        said = "\n".join(logged.output)
        self.assertIn("page.html could not be written", said)
        self.assertNotIn("graph.json could not be written", said)
        self.assertNotIn("Traceback", said)

    def test_a_graph_whose_directory_cannot_be_made_is_refused_by_name(self):
        """The first output failing is the same refusal as the second one failing.

        `os.makedirs` runs before anything is staged, so the failure arrived with nothing
        written and nothing to name it with — an `IndexError` out of the run, no warning,
        no exit 5, and a traceback naming a line of `cli.py` instead of the file a reader
        would have to go and make room for.
        """
        tree = self.a_module()
        out = self.a_previous_run()
        blocker = os.path.join(out, "not-a-directory")
        with open(blocker, "w", encoding="utf-8") as handle:
            handle.write("a file, so nothing can be written underneath it")

        with self.assertLogs("module_depth_map.cli", level=logging.WARNING) as logged:
            code = cli.main(
                ["--source", tree.root, "--graph", os.path.join(blocker, "graph.json"),
                 "--page", os.path.join(out, "page.html")]
            )

        self.assertEqual(5, code)
        said = "\n".join(logged.output)
        self.assertIn("graph.json could not be written", said)
        self.assertNotIn("Traceback", said)
        self.assertEqual(b"the previous run's page.html", bytes_of(os.path.join(out, "page.html")))
        self.assertEqual(
            ["graph.json", "not-a-directory", "page.html"], sorted(os.listdir(out))
        )

    def test_a_page_whose_path_is_a_directory_leaves_the_graph_as_it_was(self):
        """The way a *move* fails after both files have been written whole.

        `page.html.writing` opens perfectly well beside a directory called `page.html`,
        so staging both outputs succeeds and the failure lands on the rename — which is
        after the graph has already been moved into place. The graph landed, the page
        never did, and `page.html.writing` was left behind, all under a traceback.
        """
        tree = self.a_module()
        out = self.a_previous_run()
        os.remove(os.path.join(out, "page.html"))
        os.makedirs(os.path.join(out, "page.html"))

        with self.assertLogs("module_depth_map.cli", level=logging.WARNING) as logged:
            code = cli.main(
                ["--source", tree.root, "--graph", os.path.join(out, "graph.json"),
                 "--page", os.path.join(out, "page.html")]
            )

        self.assertEqual(5, code)
        self.assertEqual(b"the previous run's graph.json", bytes_of(os.path.join(out, "graph.json")))
        self.assertEqual(["graph.json", "page.html"], sorted(os.listdir(out)))
        self.assertEqual([], os.listdir(os.path.join(out, "page.html")))
        self.assertIn("is a directory", "\n".join(logged.output))

    def test_a_move_that_fails_anyway_says_which_file_landed_and_which_did_not(self):
        """Two renames are not one step, and the run has to say so when the second fails.

        This is the failure that cannot be checked for in advance, so what is asserted is
        not that it cannot happen but that it is never quiet: an error carrying the
        exception, naming the file that landed, so a reader knows the page beside the
        graph is the old one. Nothing is left half written either way.
        """
        tree = self.a_module()
        out = self.a_previous_run()
        moving = os.replace
        os.replace = _refuse_the_second_move(moving)
        self.addCleanup(setattr, os, "replace", moving)

        with self.assertLogs("module_depth_map.cli", level=logging.ERROR) as logged:
            code = cli.main(
                ["--source", tree.root, "--graph", os.path.join(out, "graph.json"),
                 "--page", os.path.join(out, "page.html")]
            )

        self.assertEqual(5, code)
        said = "\n".join(logged.output)
        self.assertIn("graph.json", said)
        self.assertIn("no room to rename anything", said)
        self.assertIn("Traceback", said)
        self.assertEqual(b"the previous run's page.html", bytes_of(os.path.join(out, "page.html")))
        self.assertEqual(["graph.json", "page.html"], sorted(os.listdir(out)))


def _refuse_to_render(document, serialised):
    raise OSError("no room on the disk for a page")


def _refuse_the_second_move(moving):
    """The real `os.replace` for the first call, and a failure for every one after it."""
    moved = []

    def replace(source, destination):
        moved.append(destination)
        if len(moved) > 1:
            raise OSError("no room to rename anything")
        return moving(source, destination)

    return replace
