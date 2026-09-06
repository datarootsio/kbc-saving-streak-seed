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
import unittest

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

    def test_a_day_written_in_digits_that_are_not_arabic_numerals_is_refused(self):
        """One day may not have several spellings, or one argument writes two documents.

        `\\d` in a Python pattern matches every Unicode decimal digit there is, and
        `int()` parses all of them, so a check written with `\\d` reads `٢٠٢٦-٠٩-٠٦` and
        `２０２６-０９-０６` as dates and the page renders whichever one it was handed. That is
        the same failure `date.fromisoformat` was avoided for — the same day producing two
        different documents — arriving by another door, so it gets the same refusal
        `20260906` gets.
        """
        tree = self.source()

        for given in ("٢٠٢٦-٠٩-٠٦",
                      "２０２６-０９-０６",
                      "2026-٩٦-06"):
            with self.subTest(given=given):
                with self.assertRaises(graph.SnapshotNotADate):
                    graph.a_snapshot_date(given)
                with self.assertRaises(graph.SnapshotNotADate):
                    graph.build([graph.source_root(tree.root)], scoring.load(), given)

    def test_the_seam_says_both_of_the_refusals_it_answers_with(self):
        """A caller guarding what the contract declares should not meet a third thing.

        `graph.build` is this tool's own seam, and it refuses twice: a snapshot date that
        is not a day, and two files claiming one module id. A contract naming one of the
        two is the declared-versus-thrown disagreement this tool reports as a finding on
        other modules, written into the module that does the reporting.
        """
        contract = graph.build.__doc__

        for refusal in ("SnapshotNotADate", "DuplicateModules"):
            self.assertIn(refusal, contract, refusal)

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

        Read over what lands on a card, which is narrower than the document in two
        directions. It is narrower than every string under a module, because the rest of
        what a module carries is names read out of the source — a class called `TodoItem`,
        a method called `rewrite` — and the source's own words are not this page's claims
        about it; scanning those would fail on a repository this tool is supposed to be
        able to read. And it is narrower than every string in the document, because prose
        about a *measure* is allowed sentences prose about a module is not: "nothing to
        fix" is an accurate thing for a heading to say, and a scan over everything bans
        it. The exclusion reasons are in scope even though they sit under `scoring`,
        because every excluded card prints the one that excluded it.
        """
        printed_on_a_card = list(_prose_about_a_module_in(self.document["modules"]))
        printed_on_a_card += list(
            _prose_about_a_module_in(self.document["scoring"]["exclusions"], "exclusions")
        )

        self.assertTrue(printed_on_a_card)
        for path, said in printed_on_a_card:
            for word in WORDS_A_PROPOSAL_IS_WRITTEN_WITH:
                self.assertNotIn(word, said.lower(), path)

    def test_the_two_refusal_findings_do_not_end_on_the_same_thought(self):
        """Two findings, and a reader has to be able to tell what is different about them.

        They are the two halves of one disagreement — documented and not raised, raised
        and not documented — and a card carries one of them without the other beside it.
        Ending both on "which side is honest is a question for a person" tells a reader
        holding one card nothing about which half they are holding.
        """
        refusals = self.document["scoring"]["refusals"]
        one = refusals["documentedNeverRaised"]["because"]
        other = refusals["raisedNeverDocumented"]["because"]

        self.assertNotEqual(_last_sentence_of(one), _last_sentence_of(other))
        self.assertIn("never arrive", one)
        self.assertIn("meets it at run time", other)

    def test_the_page_proposes_nothing_in_its_own_words_either(self):
        """The two words no denial needs, so finding either is finding a proposal."""
        for word in ("refactor", "rewrite"):
            self.assertNotIn(word, self.rendered.lower())


class ThePageSaysItsLimitsOnTheSmallestRunThereIsTest(SourceTreeTest):
    """One file, one module, nothing excluded: the run every plural on this page breaks on.

    The sentences this section is made of are counted sentences, and a counted sentence
    is only as good as its singular. Read off the script rather than off a rendering,
    because the page builds each of them by concatenation in the browser and there is no
    browser here; what a test can hold is that no verb was written outside the `count`
    call that decides whether its noun is one thing or several.
    """

    def setUp(self):
        super().setUp()
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")
        self.document = graph.build(
            [graph.source_root(tree.root)], scoring.load(), A_SNAPSHOT
        )
        self.rendered = page.render(
            self.document, graph.serialise(self.document)
        ).decode("utf-8")

    def test_the_run_really_is_the_one_every_plural_breaks_on(self):
        """The fixture is only worth anything if it is genuinely one of everything."""
        self.assertEqual(1, self.document["source"]["filesSeen"])
        self.assertEqual(1, len(self.document["modules"]))
        self.assertEqual(1, self.document["scoring"]["modulesScored"])
        self.assertEqual(0, self.document["scoring"]["modulesNeverScored"])

    def test_every_sentence_shaped_n_of_m_counts_its_verb_apart_from_its_noun(self):
        """"1 of 1 module ... are scored" is what a hardcoded verb renders on this run.

        Folding the verb into the `count` call that pluralises the noun fixes that one
        and breaks another: in "N of M modules are scored" the noun agrees with M and the
        verb with N, so one count driving both renders "1 of 74 modules are scored" on a
        repository with a single scored module. The page counts them apart.
        """
        self.assertIn("function verb(n, is, are)", self.rendered)
        self.assertIn('verb(document_.scoring.modulesScored, "is", "are")', self.rendered)
        self.assertIn(
            'verb(document_.scoring.modulesNeverScored, "is", "are")', self.rendered
        )

    def test_what_was_counted_is_called_a_path_the_way_the_alarm_above_it_does(self):
        """`filesSeen` counts a directory that would not open, and the alarm box says so.

        A page that calls a directory a file two inches under a box calling it a source
        path is a page disagreeing with itself about what it read.
        """
        self.assertIn('"source path", "source paths"', self.rendered)
        self.assertIn('count(document_.source.filesUnparsed, "path", "paths")', self.rendered)

    def test_a_run_that_excluded_nothing_says_so_rather_than_listing_empty_rules(self):
        """Three bullets each reading "0 modules" is a promise the page just broke.

        The sentence over the list says every rule that excluded anything is in it. On a
        run where no rule excluded anything, the list is not drawn and the section says
        what happened in words.
        """
        self.assertIn("exclusion.modulesExcluded > 0", self.rendered)
        self.assertIn("if (excludingRules.length > 0)", self.rendered)
        self.assertIn("Nothing was left out.", self.rendered)


class TheUsageLineSaysWhatTheHelpSaysTest(unittest.TestCase):
    """`--help` is where a person finds out this tool will not date a page for them."""

    def test_the_one_argument_with_no_default_is_not_drawn_as_an_optional_one(self):
        """Brackets mean "you may leave this out", and leaving this one out is a refusal.

        argparse brackets everything it has not been told is `required=True`, and this
        one is refused in words by `main` instead — so the generated line said
        `[--snapshot-date YYYY-MM-DD]` four lines above help text reading "Required".
        """
        usage = cli.parser().format_usage()
        help_text = cli.parser().format_help()

        self.assertIn("--snapshot-date " + graph.SNAPSHOT_DATE, usage)
        self.assertNotIn("[--snapshot-date", usage)
        self.assertIn("Required", help_text)

    def test_every_argument_this_command_takes_is_on_its_usage_line(self):
        """The usage line is written out by hand, and this is what keeps it true.

        Reads argparse's own list of actions rather than a list of flags repeated here,
        so that an argument added to the parser and forgotten on the usage line fails
        rather than going unmentioned. One spelling of each is enough — argparse writes
        `-h` for the argument that also answers to `--help` — so what is asserted is that
        every argument is reachable from the line, not that every alias is on it.
        """
        parser = cli.parser()
        usage = parser.format_usage()

        for action in parser._actions:
            self.assertTrue(
                any(flag in usage for flag in action.option_strings),
                action.option_strings,
            )


class ThisRepositoryIsDatedByWhoeverRanTheToolTest(SourceTreeTest):
    """The committed outputs carry a day, and it is the day somebody typed."""

    def test_the_committed_graph_says_which_day_it_is_a_picture_of(self):
        committed = os.path.join(
            os.path.dirname(os.path.abspath(__file__)),
            "..", "..", "..", "..", "docs", "module-depth-map.json",
        )
        with open(committed, encoding="utf-8") as handle:
            document = json.load(handle)

        # `[0-9]`, not `\d`: `\d` matches every Unicode decimal digit, so a graph dated
        # `٢٠٢٦-٠٩-٠٦` would satisfy a `\d` pattern here and this test would wave through
        # the one thing it exists to catch.
        self.assertRegex(document["snapshot"]["date"], r"\A[0-9]{4}-[0-9]{2}-[0-9]{2}\Z")
        self.assertEqual(
            document["snapshot"]["date"],
            graph.a_snapshot_date(document["snapshot"]["date"]),
        )


# The keys under a module whose value is a sentence this tool wrote about that module:
# a deletion-test verdict and the reasoning behind it, a finding and why it is one, and
# the fact about the module a rule matched on. Everything else a module carries is a name
# read out of the source — its id, its package, its methods, the types crossing its seam —
# and those are the source's words rather than this page's claims about it.
PROSE_THIS_TOOL_WROTE_ABOUT_A_MODULE = ("because", "finding", "verdict", "matched")


def _prose_about_a_module_in(document, path="modules", key=None):
    """Every sentence this tool wrote about a module, with where in the document it sits."""
    if isinstance(document, dict):
        for name, value in document.items():
            for found in _prose_about_a_module_in(value, path + "." + str(name), name):
                yield found
    elif isinstance(document, list):
        for index, value in enumerate(document):
            for found in _prose_about_a_module_in(value, "%s[%d]" % (path, index), key):
                yield found
    elif isinstance(document, str) and key in PROSE_THIS_TOOL_WROTE_ABOUT_A_MODULE:
        yield path, document


def _last_sentence_of(prose):
    """What a reader of a card is left with, which is the thing being compared."""
    return prose.rstrip(".").rsplit(". ", 1)[-1]


def _embedded_graph(rendered):
    """The graph document as it sits inside the page, so the rest can be read apart from it."""
    opening = '<script id="' + page.GRAPH_ELEMENT_ID + '" type="application/json">\n'
    start = rendered.index(opening) + len(opening)
    end = rendered.index("\n</script>", start)
    return rendered[start:end]
