"""What the page admits: the day it is a picture of, and the parts of an interface it never read.

Two properties, and they hold each other up. A page that says what it did not measure is
one a reader can weigh; a page that also says which day it is a picture of is one they
can weigh *now*, six months after it was generated. Neither is decoration: this
repository is extended by agents, and an undated ranking of shallow modules with no
statement of its own blind spots is read as a list of work to be done.

Most of this drives the graph seam, because the page is a rendering of the document and
the document is where the date has to be. Where a property really is the page's own — that
it draws the date it was given rather than one of its own, that its words name it an
observation rather than a backlog — the rendered page is read, the way the tests for the
constructor it does not count and the themes it draws in already do.
"""

import datetime
import json
import logging
import os
import re

from ... import cli, graph, page, scoring
from ..support.sourcetrees import A_SNAPSHOT, SourceTreeTest

# What a proposal is written with. None of these belongs in an observation, and the whole
# point of the framing is that this tool stops one word short of every one of them: it
# reports what a module's shape is and never what anybody should do about it.
WORDS_A_PROPOSAL_IS_WRITTEN_WITH = (
    "refactor",
    "rewrite",
    "should be",
    "ought to be",
    "needs to be",
    "must be changed",
    "to fix",
    "todo",
)


class TheSnapshotDateIsGivenToTheRunTest(SourceTreeTest):
    """The day the document is an observation of is an argument, never a reading of a clock."""

    def source(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")
        return tree

    def test_the_document_carries_the_day_the_run_was_given(self):
        tree = self.source()

        document = graph.build([graph.source_root(tree.root)], scoring.load(), "2019-07-04")

        self.assertEqual({"date": "2019-07-04"}, document["snapshot"])

    def test_a_day_that_is_not_a_day_is_refused_at_the_seam_rather_than_carried(self):
        """The date is the one value on the page nothing else can be held against.

        Every number here can be checked against the source. `2026-13-40` can be checked
        against nothing, and a page carrying it would be a dated page that is not dated.
        """
        tree = self.source()

        for given in ("", "today", "2026-13-01", "2026-02-30", "20260906", "2026-9-6",
                      "2026-09-06T09:00", None):
            with self.subTest(given=given):
                with self.assertRaises(graph.SnapshotNotADate):
                    graph.build([graph.source_root(tree.root)], scoring.load(), given)

    def test_a_day_there_was_is_read_whichever_year_it_is_in(self):
        """A leap day is a day, and refusing one would be an alarm crying wolf."""
        tree = self.source()

        for given in ("2024-02-29", "1999-12-31", "2100-01-01"):
            with self.subTest(given=given):
                document = graph.build(
                    [graph.source_root(tree.root)], scoring.load(), given
                )
                self.assertEqual(given, document["snapshot"]["date"])

    def test_the_command_refuses_to_run_undated_and_says_why(self):
        tree = self.source()

        with self.assertLogs("module_depth_map.cli", level=logging.WARNING) as logged:
            code = self.run_over(tree.root)

        self.assertEqual(6, code)
        said = "\n".join(logged.output)
        self.assertIn("refused to run: no snapshot date given", said)
        self.assertIn("clock", said)
        self.assertIn("--snapshot-date", said)
        self.assertNotIn("Traceback", said)

    def test_the_command_refuses_a_day_there_was_not_and_names_it(self):
        tree = self.source()

        for given in ("2026-02-30", "yesterday", "20260906"):
            with self.subTest(given=given):
                with self.assertLogs("module_depth_map.cli", level=logging.WARNING) as logged:
                    code = self.run_over(tree.root, "--snapshot-date", given)

                self.assertEqual(6, code)
                said = "\n".join(logged.output)
                self.assertIn("refused to run", said)
                self.assertIn(given, said)
                self.assertNotIn("Traceback", said)

    def test_a_run_that_cannot_be_dated_writes_neither_output(self):
        """The refusal comes before the source is walked, so both files are last run's."""
        tree = self.source()
        out = os.path.join(self.scratch, "out")
        os.makedirs(out)
        for name in ("graph.json", "page.html"):
            with open(os.path.join(out, name), "wb") as handle:
                handle.write(b"the previous run's " + name.encode("ascii"))

        code = cli.main(
            ["--source", tree.root, "--graph", os.path.join(out, "graph.json"),
             "--page", os.path.join(out, "page.html"), "--log-level", "ERROR"]
        )

        self.assertEqual(6, code)
        self.assertEqual(["graph.json", "page.html"], sorted(os.listdir(out)))
        for name in ("graph.json", "page.html"):
            with open(os.path.join(out, name), "rb") as handle:
                self.assertEqual(b"the previous run's " + name.encode("ascii"), handle.read())

    def test_the_run_says_which_day_it_dated_the_page(self):
        """A reader of the log can tell which day the files it just wrote are of."""
        tree = self.source()

        with self.assertLogs("module_depth_map", level=logging.INFO) as logged:
            self.assertEqual(0, self.run_over(tree.root, "--snapshot-date", "2019-07-04"))

        for line in ("run started", "graph built", "run finished"):
            said = [each for each in logged.output if line in each]
            self.assertEqual(1, len(said), line)
            self.assertIn("snapshotDate=2019-07-04", said[0])

    def test_the_inputs_behind_the_date_are_at_debug_and_say_where_it_came_from(self):
        tree = self.source()

        with self.assertLogs("module_depth_map", level=logging.DEBUG) as logged:
            self.assertEqual(0, self.run_over(tree.root, "--snapshot-date", A_SNAPSHOT))

        given = [line for line in logged.output if "snapshot date given" in line]
        self.assertEqual(1, len(given), logged.output)
        self.assertIn("date=" + A_SNAPSHOT, given[0])
        self.assertIn("no clock is read", given[0])

    def test_today_is_nowhere_in_a_document_dated_some_other_day(self):
        """The clock is the one thing that could put today's date in here. It is not read.

        The test may look at a clock; the tool may not. So the fixture is dated a day
        that is not today, and today's date is asserted absent from both outputs.
        """
        tree = self.source()

        document = graph.build([graph.source_root(tree.root)], scoring.load(), "1999-12-31")
        written = graph.serialise(document)
        drawn = page.render(document, written)

        today = datetime.date.today().isoformat()
        self.assertNotEqual("1999-12-31", today)
        for output in (written.decode("utf-8"), drawn.decode("utf-8")):
            self.assertNotIn(today, output)

    def run_over(self, root, *arguments):
        return cli.main(
            ["--source", root,
             "--graph", os.path.join(self.scratch, "out", "graph.json"),
             "--page", os.path.join(self.scratch, "out", "page.html")] + list(arguments)
        )


class ThePageSaysItsOwnLimitsTest(SourceTreeTest):
    """What a reader is told about the picture before they are told what to read into it."""

    def setUp(self):
        super().setUp()
        tree = self.tree("fixture")
        tree.java(
            "shop.till", "Till",
            "public class Till {\n"
            "    private final Prices prices;\n"
            "    Till(Prices prices) { this.prices = prices; }\n"
            "    public long ring(long cents) { return prices.of(cents); }\n"
            "}",
        )
        tree.java("shop.till", "Prices", "public class Prices {\n    public long of(long c) { return c; }\n}")
        tree.java("shop.till", "Receipt", "public record Receipt(long cents) {}")
        self.document = graph.build([graph.source_root(tree.root)], scoring.load(), A_SNAPSHOT)
        self.rendered = page.render(
            self.document, graph.serialise(self.document)
        ).decode("utf-8")

    def test_the_page_draws_the_day_the_document_carries_rather_than_one_of_its_own(self):
        """The date on the page is read out of the document, so it cannot be a second date.

        Asserted as the absence of any date in the renderer itself: a page holding one
        would be a page that could disagree with the graph beside it, and the two are
        supposed to be the same fact written once.
        """
        outside = self.rendered.replace(_embedded_graph(self.rendered), "")

        self.assertEqual([], re.findall(r"\d{4}-\d{2}-\d{2}", outside))
        self.assertIn("document_.snapshot.date", outside)

    def test_the_page_names_itself_an_observation_of_that_day_rather_than_a_backlog(self):
        self.assertIn(
            "An observation of this repository's source as it stood on ", self.rendered
        )
        self.assertIn("Not a list of work to be done", self.rendered)
        self.assertIn("nothing here is ranked, nothing here is proposed", self.rendered)

    def test_the_page_says_which_parts_of_an_interface_it_does_not_measure(self):
        self.assertIn("What this page does not measure", self.rendered)
        self.assertIn("the invariants a module states in prose", self.rendered)
        self.assertIn("the order calls have to be made in", self.rendered)

    def test_the_page_says_a_score_is_a_floor_on_an_interface_rather_than_all_of_it(self):
        """Naming the two unmeasured parts is half of it; saying what that costs is the rest."""
        self.assertIn(
            "score is a floor on what an interface asks of a caller, never the whole of "
            "it", self.rendered
        )

    def test_the_page_says_how_much_of_the_source_it_was_drawn_from(self):
        self.assertIn("Every number here was read from ", self.rendered)
        self.assertIn("document_.source.filesParsed", self.rendered)
        self.assertIn("document_.source.filesSeen", self.rendered)

    def test_the_page_gives_what_was_never_scored_a_heading_naming_the_rule_behind_it(self):
        self.assertIn("What was never scored, and under which rule", self.rendered)

    def test_every_module_the_rules_declined_names_the_rule_that_declined_it(self):
        """The list on the page is this, drawn: a rule per exclusion, and a count with it."""
        named = {entry["rule"]: entry for entry in self.document["scoring"]["exclusions"]}

        excluded = [module for module in self.document["modules"] if module["excludedBy"]]
        self.assertEqual(["shop.till.Receipt"], [module["id"] for module in excluded])
        for module in excluded:
            self.assertIn(module["excludedBy"]["rule"], named)
            self.assertTrue(module["excludedBy"]["matched"], module["id"])
        for rule, entry in named.items():
            self.assertTrue(entry["because"], rule)
            self.assertEqual(
                sum(1 for module in excluded if module["excludedBy"]["rule"] == rule),
                entry["modulesExcluded"],
                rule,
            )

    def test_nothing_the_document_says_about_a_module_proposes_changing_it(self):
        """Everything the page says about a module comes from here, so this is where to look.

        A card, a panel and a verdict are drawn out of this document and out of nothing
        else, so a proposal on the page would have to be a proposal in here. The page's
        own prose is about the measures rather than about any module, and the two tests
        above are what hold it to an observation.
        """
        for path, said in _every_string_in(self.document):
            for word in WORDS_A_PROPOSAL_IS_WRITTEN_WITH:
                self.assertNotIn(word, said.lower(), path)

    def test_the_page_proposes_nothing_in_its_own_words_either(self):
        """The two words no denial needs, so finding either is finding a proposal."""
        for word in ("refactor", "rewrite"):
            self.assertNotIn(word, self.rendered.lower())


class ThisRepositoryIsDatedByWhoeverRanTheToolTest(SourceTreeTest):
    """The committed outputs carry a day, and it is the day somebody typed."""

    def test_the_committed_graph_says_which_day_it_is_a_picture_of(self):
        committed = os.path.join(
            os.path.dirname(os.path.abspath(__file__)),
            "..", "..", "..", "..", "docs", "module-depth-map.json",
        )
        with open(committed, encoding="utf-8") as handle:
            document = json.load(handle)

        self.assertRegex(document["snapshot"]["date"], r"\A\d{4}-\d{2}-\d{2}\Z")
        self.assertEqual(
            document["snapshot"]["date"],
            graph.a_snapshot_date(document["snapshot"]["date"]),
        )


def _every_string_in(document, path="document"):
    """Every string anywhere in the document, with where it was found."""
    if isinstance(document, dict):
        for key, value in document.items():
            for found in _every_string_in(value, path + "." + str(key)):
                yield found
    elif isinstance(document, list):
        for index, value in enumerate(document):
            for found in _every_string_in(value, "%s[%d]" % (path, index)):
                yield found
    elif isinstance(document, str):
        yield path, document


def _embedded_graph(rendered):
    """The graph document as it sits inside the page, so the rest can be read apart from it."""
    opening = '<script id="' + page.GRAPH_ELEMENT_ID + '" type="application/json">\n'
    start = rendered.index(opening) + len(opening)
    end = rendered.index("\n</script>", start)
    return rendered[start:end]
