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

    def test_a_date_this_tool_will_not_read_never_reaches_the_build_seam(self):
        """`build` refuses with two exceptions and `main` guards one. This is why that holds.

        `main` reads the date before it walks anything and hands `build` what
        `a_snapshot_date` gave back, so the reading inside `build` is asked the same
        question about the same string and cannot answer it differently — which is the
        whole reason a `SnapshotNotADate` is not caught around that call. The claim is
        about an order of statements, so it is held by an order of statements: `build` is
        replaced with something that refuses to be called at all, and a misspelled date
        still comes back as the refusal `main` writes rather than as a traceback.
        """
        tree = self.source()
        was = graph.build

        def build_that_must_not_be_reached(*arguments, **named):
            raise AssertionError("main walked the source before reading the date")

        graph.build = build_that_must_not_be_reached
        self.addCleanup(setattr, graph, "build", was)
        with self.assertLogs("module_depth_map.cli", level=logging.WARNING) as logged:
            code = self.run_over(tree.root, "--snapshot-date", "2026-02-30")

        self.assertEqual(6, code)
        self.assertIn("is not a day there was", "\n".join(logged.output))
        # And the pass-through the comment beside that call rests on: the reading answers
        # with the string it was handed, so asking it a second time about its own answer
        # is the same question and gets the same one back.
        read = graph.a_snapshot_date("2026-02-28")
        self.assertEqual("2026-02-28", read)
        self.assertEqual(read, graph.a_snapshot_date(read))

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
        # `ring` documents a refusal it never throws and throws one it never documents,
        # which is what puts a finding on a card. Without a disagreement in here every
        # module in this fixture carried nought findings, and the scan below — which is
        # the whole of criterion 7's guarantee — walked a `findings` list that was always
        # empty while eight cards of the committed page carried that prose.
        tree.java(
            "shop.till", "Till",
            "public class Till {\n"
            "    private final Prices prices;\n"
            "    Till(Prices prices) { this.prices = prices; }\n"
            "    /**\n"
            "     * Ring a sale up.\n"
            "     *\n"
            "     * @throws IllegalStateException when this till has not been opened yet\n"
            "     */\n"
            "    public long ring(long cents) {\n"
            "        if (cents <= 0) { throw new IllegalArgumentException(\"cents\"); }\n"
            "        return prices.of(cents);\n"
            "    }\n"
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
        because every excluded card prints the one that excluded it, and the two
        disagreement kinds under `scoring.refusals` are in scope for exactly the same
        reason: each is copied verbatim onto every card whose module has that finding —
        eight cards of the committed page — so a proposal written in one of them is a
        proposal on a card. `scoring.refusals.because` is not in scope: it is the band's
        own explanation, prose about a measure, and it is printed under a heading rather
        than on any module.
        """
        printed_on_a_card = list(_prose_about_a_module_in(self.document["modules"]))
        printed_on_a_card += list(
            _prose_about_a_module_in(self.document["scoring"]["exclusions"], "exclusions")
        )
        printed_on_a_card += list(
            _prose_about_a_module_in(self.a_finding_on_a_card_is_written_with(), "refusals")
        )

        self.assertTrue(printed_on_a_card)
        for path, said in printed_on_a_card:
            for word in WORDS_A_PROPOSAL_IS_WRITTEN_WITH:
                self.assertNotIn(word, said.lower(), path)

    def a_finding_on_a_card_is_written_with(self):
        """The prose behind a refusal finding, which a card prints word for word.

        Named apart from the scan so that the two kinds are listed in one place: a third
        kind of disagreement added under `scoring.refusals` and not added here would be
        prose on a card that nothing reads, which is the hole this was written to close.
        """
        refusals = self.document["scoring"]["refusals"]
        return {kind: refusals[kind] for kind in FINDINGS_A_CARD_PRINTS}

    def test_the_prose_a_finding_puts_on_a_card_is_the_prose_under_scoring_refusals(self):
        """Both halves of the scan above, held to being about the same sentences.

        The scan reads the source of a finding as well as its copies, and this is what
        says they are copies: what a module carries under `findings` is `because` and
        `finding` taken verbatim from `scoring.refusals`, so scanning either without the
        other would be scanning the same prose twice or missing half of where it lands.
        The fixture has to carry one of each, or the modules half of the scan walks an
        empty list and nothing here is being checked at all.
        """
        source = self.document["scoring"]["refusals"]
        found = [
            (module["id"], finding)
            for module in self.document["modules"]
            for finding in module["findings"]
        ]

        self.assertEqual(
            {source[kind]["finding"] for kind in FINDINGS_A_CARD_PRINTS},
            {finding["finding"] for _, finding in found},
            "the fixture no longer carries one finding of each kind",
        )
        by_finding = {source[kind]["finding"]: source[kind]["because"]
                      for kind in FINDINGS_A_CARD_PRINTS}
        for module_id, finding in found:
            self.assertEqual(by_finding[finding["finding"]], finding["because"], module_id)

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
        repository with a single scored module. The page counts them apart, and hands the
        verb both numbers — see the class below for the run where N alone is not enough.
        """
        self.assertIn("function verb(n, of, is, are)", self.rendered)
        self.assertIn(
            'verb(document_.scoring.modulesScored, document_.modules.length, "is", "are")',
            self.rendered,
        )
        self.assertIn(
            'verb(document_.scoring.modulesNeverScored, document_.modules.length, '
            '"is", "are")',
            self.rendered,
        )

    def test_no_sentence_asks_the_verb_to_conjugate_on_one_number(self):
        """Every call, not only the two named above, because the next one is the risk.

        A third counted sentence written the old way — `verb(n, "is", "are")` — would
        take `"is"` for the second number and render "1 of 1 module 74 scored" or worse,
        and it would pass both assertions above by leaving them untouched. So every call
        of the helper in the page is read, and each one has to hand over both numbers and
        both spellings of the verb.
        """
        calls = re.findall(r"\bverb\(([^)]*)\)", self.rendered)
        calls = [call for call in calls if call != "n, of, is, are"]

        self.assertTrue(calls)
        for call in calls:
            handed = [part.strip() for part in call.split(",")]
            self.assertEqual(4, len(handed), call)
            self.assertEqual(['"is"', '"are"'], handed[2:], call)

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
        self.assertIn("Nothing was left out of the scoring.", self.rendered)


class ThePageSaysItsLimitsWhenNothingCouldBeScoredTest(SourceTreeTest):
    """One module, and a rule declined to price it: the run a verb counted on N alone gets wrong.

    "N of M modules are scored" has two numbers in it and either of them being one makes
    the sentence singular. A verb agreeing with N alone reads correctly on every run this
    repository can produce — none of its six packages is a single module nothing scored —
    and renders "0 of 1 module drawn here are scored" on this one, which is a tree of one
    module that a rule declines to price. Narrow, and still the page failing to agree
    with itself inside six words.
    """

    def setUp(self):
        super().setUp()
        tree = self.tree("fixture")
        # A record with nothing but its components is a data carrier, which the rules
        # decline to price: one module drawn, none scored.
        tree.java("shop.till", "Money", "public record Money(long cents) {}")
        self.document = graph.build(
            [graph.source_root(tree.root)], scoring.load(), A_SNAPSHOT
        )
        self.rendered = page.render(
            self.document, graph.serialise(self.document)
        ).decode("utf-8")

    def test_the_run_really_is_the_one_a_verb_counted_on_n_alone_breaks_on(self):
        """The fixture is worth nothing unless it is genuinely M of one and N of nought."""
        self.assertEqual(1, len(self.document["modules"]))
        self.assertEqual(0, self.document["scoring"]["modulesScored"])
        self.assertEqual(1, self.document["scoring"]["modulesNeverScored"])

    def test_the_verb_is_singular_when_either_number_beside_it_is_one(self):
        """The rule itself, because this run is the one it exists for.

        Read off the script: the sentence only exists once a browser has joined a dozen
        strings, and there is no browser in this suite. What is held here is the rule the
        browser will apply — singular when the number that governs the verb is one, and
        singular when there is only one thing to be counted of, which is this run. The
        rendered sentence was read in a browser on this fixture as well.
        """
        self.assertIn(
            "function verb(n, of, is, are) { return n === 1 || of === 1 ? is : are; }",
            self.rendered,
        )


class ThePageDoesNotDenyAnExclusionItHasJustListedTest(SourceTreeTest):
    """A rule declined a path and no rule excluded a module: the run where the two halves disagree.

    "What was left out" has two halves on this page and they are counted separately: a
    path a rule declined to read, and a module that was read and then not priced. The
    fixture here is the run that has one of each kind of answer — one declined directory,
    and one module that every scoring rule was happy to price — and it is the run where a
    summary written for the second half lands directly under a list belonging to the
    first. An unqualified "Nothing was left out." is then the page denying, one block
    later and inside one screen, the exclusion it has just drawn a bullet for.

    Neither of the two classes above can see this: both build a tree with nothing in it a
    rule would decline to read, so `source.notRead.paths` is empty in both and the
    sentence is true in both.
    """

    def setUp(self):
        super().setUp()
        tree = self.tree("fixture")
        # One class every rule prices, so nothing is excluded from the scoring, and one
        # directory named on `sourcesNotRead.directories`, so something is excluded from
        # the reading. `node_modules` is the rule already in `scoring.json`; nothing here
        # is arranged for the test beyond putting a file inside it.
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")
        tree.typescript("node_modules/left-pad", "index.ts", "export const pad = (s) => s;")
        self.document = graph.build(
            [graph.source_root(tree.root)], scoring.load(), A_SNAPSHOT
        )
        self.rendered = page.render(
            self.document, graph.serialise(self.document)
        ).decode("utf-8")

    def test_the_run_really_is_one_with_a_declined_path_and_no_excluded_module(self):
        """The fixture is worth nothing unless it genuinely has one of each answer."""
        self.assertEqual(1, len(self.document["source"]["notRead"]["paths"]))
        self.assertEqual(0, self.document["scoring"]["modulesNeverScored"])
        self.assertEqual(1, len(self.document["modules"]))

    def test_the_declined_path_the_page_will_list_is_the_one_the_rule_named(self):
        """What the section above draws, so that the sentence below really does follow it."""
        declined = self.document["source"]["notRead"]["paths"][0]

        self.assertEqual("node_modules", os.path.basename(declined["path"]))
        self.assertIn("node_modules", declined["matched"])

    def test_no_run_can_render_a_summary_that_denies_an_exclusion_outright(self):
        """The three words a skimming reader takes have to be true on every run there is.

        The heading gives the sentence its scope and a reader who reads the heading is not
        misled. A reader who reads three words is, so the scope is in the three words: the
        page says nothing was left out *of the scoring*, which is a claim
        `modulesNeverScored === 0` really does settle, and never that nothing was left
        out, which it does not.
        """
        self.assertIn("Nothing was left out of the scoring.", self.rendered)
        self.assertNotIn("Nothing was left out.", self.rendered)
        self.assertNotIn("Nothing was left out,", self.rendered)

    def test_the_summary_accounts_for_the_paths_a_rule_declined_as_well(self):
        """Scoping the sentence stops it lying; it does not answer the reader's question.

        Somebody who reaches this section wanting to know what is missing from the page
        has been told about one half of the answer and is standing under a list of the
        other half. So the paragraph reconciles the two itself, on the runs where there is
        anything to reconcile, and names the heading the rest of the answer is under
        rather than pointing at it.
        """
        self.assertIn("document_.source.notRead.paths.length > 0", self.rendered)
        self.assertIn("What was left out of the reading is a different list", self.rendered)

    def test_a_pronoun_standing_for_a_counted_noun_is_counted_too(self):
        """"1 path ... nothing inside them" is what a hardcoded pronoun renders here.

        The number of declined paths is one on this run, which is the run the plural was
        written blind to. A pronoun agrees with its antecedent for the same reason a noun
        agrees with its number, and the page has one rule for both.
        """
        self.assertIn("function word(n, one, many) { return n === 1 ? one : many; }", self.rendered)
        self.assertIn(
            'word(document_.source.notRead.paths.length, "it", "them")', self.rendered
        )
        self.assertNotIn("and nothing inside them is drawn here", self.rendered)


class TheCoverageSentenceSendsAReaderTheWayThePageIsDrawnTest(SourceTreeTest):
    """The sentence that hands a reader on to the two exclusion sections names them in page order.

    "the two headings that follow" was true and useless: a reader who wanted the scoring
    half read the next heading and met the reading half. The remedy is the one the
    section-naming fix used before it — name the heading instead of pointing at it — and
    the order the sentence names them in has to be the order the page draws them.
    """

    def setUp(self):
        super().setUp()
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")
        document = graph.build([graph.source_root(tree.root)], scoring.load(), A_SNAPSHOT)
        self.rendered = page.render(document, graph.serialise(document)).decode("utf-8")

    def test_each_heading_is_written_once_so_the_sentence_cannot_drift_from_the_section(self):
        """A name in two places is a name that gets changed in one of them."""
        self.assertIn('var HEADING_NOT_READ = "What was not read at all";', self.rendered)
        self.assertIn(
            'var HEADING_NEVER_SCORED = "What was never scored, and under which rule";',
            self.rendered,
        )
        self.assertIn('add(declined, "h2", null, HEADING_NOT_READ)', self.rendered)
        self.assertIn('add(neverScored, "h2", null, HEADING_NEVER_SCORED)', self.rendered)

    def test_the_sentence_names_the_headings_rather_than_counting_them(self):
        self.assertNotIn("the two headings that follow", self.rendered)
        self.assertIn("What was left out of the reading altogether is under", self.rendered)

    def test_the_sentence_names_them_in_the_order_the_page_draws_them(self):
        """Reading first and scoring second, because that is the order the sections come in."""
        sentence = self.rendered[
            self.rendered.index("What was left out of the reading altogether is under") :
        ]
        sentence = sentence[: sentence.index("”.") + 2]

        self.assertLess(
            sentence.index("HEADING_NOT_READ"),
            sentence.index("HEADING_NEVER_SCORED"),
            sentence,
        )
        self.assertLess(
            self.rendered.index('add(declined, "h2", null, HEADING_NOT_READ)'),
            self.rendered.index('add(neverScored, "h2", null, HEADING_NEVER_SCORED)'),
        )


class TheRunSaysWhatItCouldNotReadInThePagesOwnWordsTest(SourceTreeTest):
    """The end-of-run warning reports the two numbers the page's coverage sentence reports.

    It belongs beside that sentence rather than with the rest of the run's log lines,
    because it is the same finding said twice: "N of M source paths, K could not be read"
    is what the counts box shows, what the coverage paragraph says, and what this line
    warns. A run that calls an unopenable directory a file in its log while the page it
    just wrote calls it a path is one thing under two names, and a reader holding the log
    beside the page cannot tell whether the two counts are even about the same thing.

    The pronoun is counted for the same reason every other pronoun on the page is: one
    unreadable path is not "them".
    """

    def tree_with_one_locked_directory(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", "public class Till {\n    public void ring() {}\n}")
        tree.java("shop.locked", "Hidden", "public class Hidden {}")
        locked = os.path.join(tree.root, "shop", "locked")
        os.chmod(locked, 0o000)
        self.addCleanup(os.chmod, locked, 0o755)
        return tree

    def run_over(self, root):
        return cli.main(
            ["--source", root,
             "--graph", os.path.join(self.scratch, "out", "graph.json"),
             "--page", os.path.join(self.scratch, "out", "page.html"),
             "--snapshot-date", A_SNAPSHOT]
        )

    def warning_from_a_run_that_could_not_read_everything(self):
        tree = self.tree_with_one_locked_directory()
        with self.assertLogs("module_depth_map", level=logging.WARNING) as logged:
            self.assertEqual(0, self.run_over(tree.root))
        said = [line for line in logged.output if "the page is drawn from" in line]
        self.assertEqual(1, len(said), logged.output)
        return said[0]

    @unittest.skipIf(hasattr(os, "geteuid") and os.geteuid() == 0, "root can read anything")
    def test_what_could_not_be_read_is_called_a_path_the_way_the_page_calls_it_one(self):
        warning = self.warning_from_a_run_that_could_not_read_everything()

        self.assertIn("1 of 2 source paths", warning)
        self.assertNotIn("source files", warning)

    @unittest.skipIf(hasattr(os, "geteuid") and os.geteuid() == 0, "root can read anything")
    def test_one_unreadable_path_is_referred_to_in_the_singular(self):
        warning = self.warning_from_a_run_that_could_not_read_everything()

        self.assertIn("every module declared inside it is missing from the page", warning)
        self.assertNotIn("inside them", warning)


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


class TheDocumentedWayToRegenerateDoesNotAgeTest(unittest.TestCase):
    """The two lines a maintainer copies the command from, held to carrying no day.

    A day written into either of them is a trap with a delay on it.
    `TheCommittedOutputsAreWhatAFreshRunWrites` hands its fresh run the date it reads out
    of the committed graph — it has to, because the date is the one value in those files
    no reading of this repository can rediscover — so a maintainer who adds a class, sees
    that test fail, copies the command as printed and regenerates gets two green byte
    comparisons over a page dated before the source it describes. Nothing downstream can
    catch that: a stale date is a perfectly well-formed one. What can be caught is the
    literal, here, in the two places it would be copied from.
    """

    COPIED_FROM = (
        os.path.join(os.path.dirname(os.path.abspath(__file__)),
                     "..", "..", "..", "module-depth-map.py"),
        os.path.join(os.path.dirname(os.path.abspath(__file__)),
                     "..", "..", "README.md"),
    )

    def test_neither_place_spells_the_argument_with_a_day_that_will_go_stale(self):
        for path in self.COPIED_FROM:
            with self.subTest(path=os.path.basename(path)), \
                    open(path, encoding="utf-8") as handle:
                said = handle.read()

                self.assertIn("--snapshot-date", said)
                self.assertEqual(
                    [],
                    re.findall(r"--snapshot-date\s+[0-9]{4}-[0-9]{2}-[0-9]{2}", said),
                    "%s tells a maintainer to regenerate with a day that will go stale; "
                    "the tests say it as --snapshot-date <the day you are dating it>"
                    % os.path.basename(path),
                )

    def test_both_places_say_it_the_way_the_failing_test_says_it(self):
        """One spelling of the placeholder, and it is the one a failure hands a reader.

        Somebody meets this in a failure message before they meet it in the README, and
        two spellings of the same instruction is one of them being ignored.
        """
        for path in self.COPIED_FROM:
            with self.subTest(path=os.path.basename(path)), \
                    open(path, encoding="utf-8") as handle:
                self.assertIn(
                    "--snapshot-date <the day you are dating it>", handle.read()
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

# The disagreements under `scoring.refusals` whose prose is copied onto a card. Each of
# these is written once and printed on every card whose module carries that finding, so
# it is prose about a module wherever in the document it happens to be stored — the same
# reason `scoring.exclusions[].because` is read. `scoring.refusals.because` is not one of
# them: it is the band's own explanation, printed under a heading and about the measure
# rather than about any module.
FINDINGS_A_CARD_PRINTS = ("documentedNeverRaised", "raisedNeverDocumented")


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
