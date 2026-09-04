"""What the command says about the run, and what it leaves behind when it refuses.

A run nobody can read is a run nobody can check. These are the properties a reader of the
log relies on: the level asked for is the level given, a refusal says why at the level
this repository reserves for refusals, and a run that cannot finish leaves no half of a
pair of outputs behind.
"""

import logging
import os

from ... import cli, page
from ..support.sourcetrees import SourceTreeTest


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

    def test_a_refusal_says_why_at_the_level_this_repository_reserves_for_refusals(self):
        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            exit_code = self.run_over(os.path.join(self.scratch, "no-such-directory"))

        self.assertEqual(2, exit_code)
        self.assertEqual(["WARNING"], [line.split(":")[0] for line in logged.output])
        self.assertIn("no such source directory", logged.output[0])


class NeitherOutputIsWrittenWithoutTheOtherTest(SourceTreeTest):
    """The graph and the page are written together or not at all.

    A page that failed to render after the graph had landed would leave a fresh document
    beside a stale picture of it — and the stale one is the file a reader opens.
    """

    def test_a_page_that_cannot_be_rendered_leaves_no_graph_behind(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {}")
        graph_path = os.path.join(self.scratch, "out", "graph.json")
        rendering = page.render
        page.render = _refuse_to_render
        self.addCleanup(setattr, page, "render", rendering)

        with self.assertRaises(OSError):
            cli.main(
                ["--source", tree.root, "--graph", graph_path,
                 "--page", os.path.join(self.scratch, "out", "page.html"),
                 "--log-level", "ERROR"]
            )

        self.assertFalse(os.path.exists(graph_path))


def _refuse_to_render(document, serialised):
    raise OSError("no room on the disk for a page")
