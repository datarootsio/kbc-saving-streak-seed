"""Two runs over unchanged source write identical bytes.

The single most important test here: every other claim the page makes rests on it, and a
change in the output always meaning a change in the code is the whole point.
"""

import datetime
import os
import re

from ... import cli, graph, page, scoring
from ..support.sourcetrees import (
    A_SNAPSHOT,
    BACKEND_SOURCE,
    FRONTEND_SOURCE,
    SourceTree,
    SourceTreeTest,
    bytes_of,
)


class TheSameSourceGivesTheSameBytesTest(SourceTreeTest):

    def source(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")
        tree.java("shop.stock", "Shelf", "interface Shelf {\n    void restock();\n}")
        tree.java("shop.stock", "Aisle", "public enum Aisle {\n    LEFT\n}")
        # A second language in the same tree, because determinism has to hold over what
        # the tool actually reads rather than over the half of it that came first: a set
        # iterated somewhere in the TypeScript reading would move bytes between runs.
        tree.typescript("web", "till.ts", "export function ring(id: number): void {}")
        tree.typescript("web", "page.tsx",
                        "import { ring } from './till'\n\n"
                        "export default function Page() {\n  return <button onClick={() "
                        "=> ring(1)}>Ring</button>\n}")
        return tree

    def test_the_graph_document_is_byte_identical_between_runs(self):
        tree = self.source()

        first = graph.serialise(
            graph.build([graph.source_root(tree.root)], scoring.load(), A_SNAPSHOT))
        second = graph.serialise(
            graph.build([graph.source_root(tree.root)], scoring.load(), A_SNAPSHOT))

        self.assertEqual(first, second)

    def test_the_page_is_byte_identical_between_runs(self):
        tree = self.source()

        first = _rendered(graph.build([graph.source_root(tree.root)], scoring.load(), A_SNAPSHOT))
        second = _rendered(graph.build([graph.source_root(tree.root)], scoring.load(), A_SNAPSHOT))

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

    def test_the_command_writes_the_same_two_files_twice_over_this_frontend(self):
        first = self.run_command(FRONTEND_SOURCE, "first-web")
        second = self.run_command(FRONTEND_SOURCE, "second-web")

        self.assertEqual(first, second)

    def test_every_collection_in_the_graph_is_sorted(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Zebra", "public class Zebra {}")
        tree.java("shop.till", "Ant", "public class Ant {}")
        tree.java("shop.aisle", "Middle", "public class Middle {}")

        document = graph.build([graph.source_root(tree.root)], scoring.load(), A_SNAPSHOT)

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
            ["--source", source, "--graph", graph_path, "--page", page_path,
             "--log-level", "ERROR", "--snapshot-date", A_SNAPSHOT]
        )
        self.assertEqual(0, exit_code)
        return bytes_of(graph_path), bytes_of(page_path)


class NothingMachineSpecificIsWrittenTest(SourceTreeTest):
    """No timestamp, no absolute path, nothing that is true only on the machine that ran."""

    def written(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {}")
        document = graph.build([graph.source_root(tree.root)], scoring.load(), A_SNAPSHOT)
        return tree, graph.serialise(document).decode("utf-8"), _rendered(document).decode("utf-8")

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
        document = graph.build([graph.source_root(BACKEND_SOURCE)], scoring.load(), A_SNAPSHOT)

        self.assertEqual(["backend/src/main/java"], document["source"]["roots"])

    def checkout(self, name, git):
        """A source root inside a checkout whose `.git` is written by `git(path)`."""
        top = os.path.join(self.scratch, name)
        root = os.path.join(top, "backend", "src", "main", "java")
        os.makedirs(root)
        git(os.path.join(top, ".git"))
        return root

    def test_a_root_is_named_by_its_layout_when_git_is_a_directory(self):
        root = self.checkout("clone", os.makedirs)

        self.assertEqual("backend/src/main/java", graph.label_for(root))

    def test_a_root_is_named_by_its_layout_when_git_is_a_file(self):
        """A worktree's `.git` is a file pointing at the real one, and must count too.

        The tool is run inside worktrees, so a walk that only recognised a directory
        would name this root `java` there and `backend/src/main/java` in an ordinary
        clone: the same source, two different outputs.
        """
        root = self.checkout("worktree", _gitdir_pointer)

        self.assertEqual("backend/src/main/java", graph.label_for(root))

    def test_the_two_checkout_shapes_write_the_same_bytes(self):
        clone = self.checkout("clone", os.makedirs)
        worktree = self.checkout("worktree", _gitdir_pointer)
        for root in (clone, worktree):
            SourceTree(root).java("shop.till", "Till", "public class Till {}")

        written = [
            graph.serialise(graph.build([graph.source_root(r)], scoring.load(), A_SNAPSHOT))
            for r in (clone, worktree)
        ]

        self.assertEqual(written[0], written[1])

    def test_a_root_that_is_its_own_repository_is_named_rather_than_called_a_dot(self):
        """The repository root is where every checkout differs, so it is named by neither.

        Its own directory name is a different word in every clone and worktree, and "."
        — what a relative path answers here — reads on the page as "Source read: ." and
        prefixes every failure as `./shop/Till.java`, which tells a reader nothing.
        """
        top = os.path.join(self.scratch, "clone")
        os.makedirs(os.path.join(top, ".git"))

        self.assertEqual(graph.REPOSITORY_ROOT, graph.label_for(top))

    def test_the_only_date_in_either_output_is_the_one_the_run_was_given(self):
        """One date, and it is the run's own argument. It used to be none at all.

        The page has to carry the day it is an observation of, so "no date anywhere" is
        no longer the property to hold — and it was never quite the right one, because
        what it stood in for is that nothing here is read off the machine. That is what
        is asserted now: every date-shaped string in either output is the one this run
        was handed, and there is still no time of day in either, because a time is a
        thing only a clock can answer.
        """
        _, written_graph, written_page = self.written()

        for written in (written_graph, written_page):
            self.assertEqual(
                {A_SNAPSHOT}, set(re.findall(r"\d{4}-\d{2}-\d{2}", written))
            )
            self.assertEqual([], re.findall(r"\d{2}:\d{2}:\d{2}", written))

    def test_today_is_nowhere_in_either_output(self):
        """The direct test of the one thing a clock could get into this: today's date.

        A run dated 2001-02-03 that carried today's date anywhere would be a run that
        asked the machine what day it is, whatever it was told. Written with the test's
        own clock, because the test is allowed one and the tool is not.
        """
        _, written_graph, written_page = self.written()

        today = datetime.date.today().isoformat()
        for written in (written_graph, written_page):
            self.assertNotIn(today, written)

    def test_two_runs_dated_differently_differ_in_the_date_and_nothing_else(self):
        """The date is carried, not measured: no score, shape or verdict moves with it.

        Held as its own property because the date is the one input to this tool that is
        not source. A date that had leaked into anything measured would make the page a
        different picture on a different day, over a repository that had not changed.
        """
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")

        first = graph.serialise(
            graph.build([graph.source_root(tree.root)], scoring.load(), "2001-02-03"))
        second = graph.serialise(
            graph.build([graph.source_root(tree.root)], scoring.load(), "2020-12-31"))

        self.assertNotEqual(first, second)
        self.assertEqual(
            first.decode("utf-8").replace("2001-02-03", "a day"),
            second.decode("utf-8").replace("2020-12-31", "a day"),
        )


def _gitdir_pointer(path):
    """What `git worktree add` writes in place of a `.git` directory."""
    with open(path, "w", encoding="utf-8") as handle:
        handle.write("gitdir: /elsewhere/.git/worktrees/checkout\n")


def _rendered(document):
    return page.render(document, graph.serialise(document))
