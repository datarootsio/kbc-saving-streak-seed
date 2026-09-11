"""Would deleting this module concentrate complexity, or merely move it to its callers?

The verdict is mechanical, so every test here is a source tree built to earn one: a module
coordinating five things behind one method, a module coordinating a thing per method with
two other modules going through it, a module that concentrates nothing and that nobody
calls. Each asserts the verdict it was built to receive *and* the three counts the verdict
was read off, because a word nobody can check the arithmetic behind is an opinion with a
mechanism painted on it.

Nothing here reads the analyser for its thresholds. They live in `scoring.json`, and two
tests below move them and watch every verdict move — which is the whole claim: "this is a
pass-through" is a finding a reader can argue with by editing a number they can point at.
"""

import json
import os

from ... import cli, graph, page, scoring
from ..support.sourcetrees import A_SNAPSHOT, SourceTreeTest

TOOL = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# The cast the fixtures coordinate: a module with a method to call, storage whose
# implementation is generated, a row that outlives the call, and a module in another
# package that has to be imported to be named.
A_MODULE_TO_CALL = "public class Prices {\n    public long of(long id) { return 0; }\n}"
AN_ADAPTER = (
    "import org.springframework.data.jpa.repository.JpaRepository;\n\n"
    "interface ReceiptRepository extends JpaRepository<Receipt, Long> {\n}"
)
A_PERSISTENT_RECORD = (
    "import jakarta.persistence.Entity;\n\n"
    "@Entity\nclass Receipt {\n"
    "    Receipt(long id, long cents) {}\n"
    "    public long id() { return 0; }\n}"
)
A_MODULE_IN_ANOTHER_PACKAGE = "public class Shelf {\n    public void take(long id) {}\n}"

# Five things coordinated behind one method: three collaborators, a record and the
# transaction they all run in. Deleting it puts all five back into every caller.
A_DEEP_MODULE = """import org.springframework.transaction.annotation.Transactional;

import shop.stock.Shelf;

public class Till {

    private final ReceiptRepository receipts;
    private final Shelf shelf;
    private final Prices prices;

    Till(ReceiptRepository receipts, Shelf shelf, Prices prices) {
        this.receipts = receipts;
        this.shelf = shelf;
        this.prices = prices;
    }

    @Transactional
    public long ring(long id) {
        shelf.take(id);
        long cents = prices.of(id);
        receipts.save(new Receipt(id, cents));
        return cents;
    }
}"""

# One method per collaborator, coordinating nothing. Everything it is handed it hands
# straight on, so a caller that deleted it would call the same two modules itself.
A_PASS_THROUGH = """import shop.stock.Shelf;

public class Counter {

    private final Shelf shelf;
    private final Prices prices;

    Counter(Shelf shelf, Prices prices) {
        this.shelf = shelf;
        this.prices = prices;
    }

    public void take(long id) {
        shelf.take(id);
    }

    public long price(long id) {
        return prices.of(id);
    }
}"""


def _going_through_the_counter(name):
    """A module in another package that holds a Counter and calls it: one caller apiece."""
    return (
        "import shop.till.Counter;\n\n"
        "public class %s {\n\n"
        "    private final Counter counter;\n\n"
        "    %s(Counter counter) {\n"
        "        this.counter = counter;\n"
        "    }\n\n"
        "    public void serve(long id) {\n"
        "        counter.take(id);\n"
        "    }\n}" % (name, name)
    )


class SourceOfKnownShapeTest(SourceTreeTest):
    """A test that turns a handful of Java files into modules and reads the verdicts on them."""

    def modules(self, *sources, **elsewhere):
        """The graph for these files under `shop.till`, plus any written in other packages."""
        self.trees = getattr(self, "trees", 0) + 1
        tree = self.tree("fixture-%d" % self.trees)
        for name, body in sources:
            tree.java("shop.till", name, body)
        for name, (package, body) in elsewhere.items():
            tree.java(package, name, body)

        self.document = graph.build([graph.source_root(tree.root)], scoring.load(self.rules()), A_SNAPSHOT)

        self.assertEqual([], self.document["source"]["unparsed"])
        return {module["name"]: module for module in self.document["modules"]}

    def rules(self):
        """The rules to score with: the tool's own, unless a test wrote its own file."""
        return getattr(self, "written_rules", None)

    def collaborators(self):
        """The whole cast the fixtures here coordinate, as files in a source tree."""
        return {
            "Prices": ("shop.till", A_MODULE_TO_CALL),
            "ReceiptRepository": ("shop.till", AN_ADAPTER),
            "Receipt": ("shop.till", A_PERSISTENT_RECORD),
            "Shelf": ("shop.stock", A_MODULE_IN_ANOTHER_PACKAGE),
        }

    def two_callers(self):
        """Two modules that hold a `Counter` and call it, in a package of their own."""
        return {
            "Queue": ("shop.front", _going_through_the_counter("Queue")),
            "Kiosk": ("shop.front", _going_through_the_counter("Kiosk")),
        }

    def verdict_of(self, module):
        """One module's verdict as (verdict, reach, methods, callers)."""
        test = module["deletionTest"]
        return test["verdict"], test["reach"], test["methods"], test["callers"]

    def as_written(self):
        """The tool's own configuration file, as a document a test can change."""
        with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
            return json.loads(handle.read().decode("utf-8"))

    def scoring_with(self, deletion_test):
        """The tool's own configuration with this deletion test in it, written to a file."""
        document = dict(self.as_written(), deletionTest=deletion_test)
        path = os.path.join(self.scratch, "rules.json")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(json.dumps(document, indent=2))
        self.written_rules = path
        return path

    def rendered(self, *sources, **elsewhere):
        """These modules, and the page a browser would draw them from, as text."""
        modules = self.modules(*sources, **elsewhere)
        return modules, page.render(
            self.document, graph.serialise(self.document)
        ).decode("utf-8")

    def the_rule(self):
        """The deletion test the document was scored by."""
        return self.document["scoring"]["deletionTest"]


class WhoGoesThroughAModuleIsCountedTest(SourceOfKnownShapeTest):
    """The graph reads itself backwards: every line in a fan is one caller of what it points at."""

    def test_a_module_two_others_call_is_recorded_as_having_two_callers(self):
        modules = self.modules(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )

        self.assertEqual(2, modules["Counter"]["callers"]["count"])
        self.assertEqual(
            ["shop.front.Kiosk", "shop.front.Queue"], modules["Counter"]["callers"]["moduleIds"]
        )

    def test_every_module_in_the_graph_carries_a_caller_count(self):
        """Every module, including the ones no rule scores: a count is not a judgement."""
        modules = self.modules(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH),
            **dict(self.collaborators(), **self.two_callers())
        )

        for name, module in sorted(modules.items()):
            self.assertIn("callers", module, name)
            self.assertEqual(
                len(module["callers"]["moduleIds"]), module["callers"]["count"], name
            )
        self.assertIsNotNone(modules["ReceiptRepository"]["excludedBy"])
        self.assertEqual(1, modules["ReceiptRepository"]["callers"]["count"])

    def test_a_module_nobody_calls_has_no_callers_rather_than_a_missing_count(self):
        modules = self.modules(("Till", A_DEEP_MODULE), **self.collaborators())

        self.assertEqual({"count": 0, "moduleIds": []}, modules["Till"]["callers"])

    def test_one_caller_reaching_a_module_twice_is_one_caller(self):
        """Two calls are one line in the fan, so they are one module going through it."""
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    private final Prices prices;\n"
                     "    Till(Prices prices) { this.prices = prices; }\n"
                     "    public long ring(long id) { return prices.of(id) + prices.of(id); }\n}"),
            **self.collaborators()
        )

        self.assertEqual(1, modules["Prices"]["callers"]["count"])
        self.assertEqual(["shop.till.Till"], modules["Prices"]["callers"]["moduleIds"])

    def test_a_module_that_calls_itself_is_not_one_of_its_own_callers(self):
        """"How many *other* modules call it" is the question, and reach never names itself."""
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    public long ring(long id) { return Till.ring(id); }\n}"),
        )

        self.assertEqual(0, modules["Till"]["callers"]["count"])

    def test_every_caller_named_is_a_module_the_graph_holds(self):
        modules = self.modules(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH),
            **dict(self.collaborators(), **self.two_callers())
        )
        held = {module["id"] for module in self.document["modules"]}

        for name, module in sorted(modules.items()):
            for caller in module["callers"]["moduleIds"]:
                self.assertIn(caller, held, name)

    def test_a_caller_count_is_the_fans_that_point_at_it(self):
        """The count read the other way round: one module's callers are everyone reaching it."""
        modules = self.modules(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH),
            **dict(self.collaborators(), **self.two_callers())
        )

        for name, module in sorted(modules.items()):
            reaching = sorted(
                other["id"]
                for other in self.document["modules"]
                for entry in other["reach"]["reaches"]
                if entry["moduleId"] == module["id"]
            )
            self.assertEqual(reaching, module["callers"]["moduleIds"], name)


class TheTwoVerdictsAFixtureIsBuiltToEarnTest(SourceOfKnownShapeTest):
    """The two shapes the test exists to tell apart, each on the fixture built for it."""

    def test_a_pass_through_two_modules_go_through_is_reported_as_one(self):
        modules = self.modules(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )

        self.assertEqual(
            (self.the_rule()["passThrough"]["verdict"], 2, 2, 2),
            self.verdict_of(modules["Counter"]),
        )

    def test_a_module_with_substantial_reach_is_reported_as_earning_its_keep(self):
        modules = self.modules(("Till", A_DEEP_MODULE), **self.collaborators())

        self.assertEqual(
            (self.the_rule()["earnsItsKeep"]["verdict"], 5, 1, 0),
            self.verdict_of(modules["Till"]),
        )

    def test_the_deep_module_and_the_pass_through_are_judged_differently_in_one_graph(self):
        """Both fixtures in one tree, so the two verdicts are read off one run of the rules."""
        modules = self.modules(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH),
            **dict(self.collaborators(), **self.two_callers())
        )

        self.assertEqual(
            self.the_rule()["earnsItsKeep"]["verdict"], modules["Till"]["deletionTest"]["verdict"]
        )
        self.assertEqual(
            self.the_rule()["passThrough"]["verdict"],
            modules["Counter"]["deletionTest"]["verdict"],
        )

    def test_a_module_that_coordinates_one_thing_with_two_callers_is_a_pass_through(self):
        """The rule at its plainest: one thing coordinated, two modules going through it."""
        one_call = (
            "public class Counter {\n\n"
            "    private final Prices prices;\n\n"
            "    Counter(Prices prices) {\n"
            "        this.prices = prices;\n"
            "    }\n\n"
            "    public long price(long id) {\n"
            "        return prices.of(id);\n"
            "    }\n}"
        )
        modules = self.modules(
            ("Counter", one_call), **dict(self.collaborators(), **self.two_callers())
        )

        self.assertEqual(
            (self.the_rule()["passThrough"]["verdict"], 1, 1, 2),
            self.verdict_of(modules["Counter"]),
        )

    def test_a_module_presenting_no_method_at_all_still_gets_the_same_reading(self):
        """Reach of one and two callers, with no method to divide by: the floor in the file.

        An allowance of a thing per method would be nothing at all here, and a module
        coordinating one thing would read as concentrating it. Coordinating one thing is
        coordinating nothing however few methods it is presented behind, which is what
        `reachAtMost.neverBelow` says and what this fixture is built to catch.
        """
        sealed = (
            "import org.springframework.transaction.annotation.Transactional;\n\n"
            "public class Vault {\n\n"
            "    @Transactional\n"
            "    private void seal() {}\n}"
        )
        holding = (
            "import shop.till.Vault;\n\n"
            "public class %s {\n"
            "    public void hold() {\n"
            "        Vault vault = new Vault();\n"
            "    }\n}"
        )
        modules = self.modules(
            ("Vault", sealed),
            Queue=("shop.front", holding % "Queue"),
            Kiosk=("shop.front", holding % "Kiosk"),
        )

        self.assertEqual([], modules["Vault"]["interface"]["methods"])
        self.assertEqual(1, modules["Vault"]["reach"]["count"])
        self.assertEqual(
            (self.the_rule()["passThrough"]["verdict"], 1, 0, 2),
            self.verdict_of(modules["Vault"]),
        )

    def test_a_module_that_concentrates_nothing_and_nobody_calls_is_given_no_finding(self):
        """The third answer, and the reason there are three.

        There is nowhere for deleting this module to move anything to, so calling it a
        pass-through would be a claim about callers that do not exist — and saying it
        earns its keep would be a claim about coordination it does not do.
        """
        modules = self.modules(("Counter", A_PASS_THROUGH), **self.collaborators())

        self.assertEqual(
            (self.the_rule()["noFinding"]["verdict"], 2, 2, 0),
            self.verdict_of(modules["Counter"]),
        )

    def test_a_module_no_rule_scores_is_given_no_verdict_at_all(self):
        """It was never measured, and a judgement on it would be the score nobody gave it."""
        modules = self.modules(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )
        adapter = modules["ReceiptRepository"]

        self.assertEqual("generated repository", adapter["excludedBy"]["rule"])
        self.assertIsNone(adapter["deletionTest"]["verdict"])
        self.assertIsNone(adapter["deletionTest"]["because"])
        self.assertEqual(
            (adapter["reach"]["count"], adapter["callers"]["count"]),
            (adapter["deletionTest"]["reach"], adapter["deletionTest"]["callers"]),
        )


class EveryVerdictStatesWhatItWasReadOffTest(SourceOfKnownShapeTest):
    """A verdict nobody can check the arithmetic behind is an opinion with a mechanism on it."""

    def test_every_verdict_carries_the_reach_and_the_caller_count_it_came_from(self):
        modules = self.modules(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH),
            **dict(self.collaborators(), **self.two_callers())
        )

        for name, module in sorted(modules.items()):
            test = module["deletionTest"]
            self.assertEqual(module["reach"]["count"], test["reach"], name)
            self.assertEqual(module["callers"]["count"], test["callers"], name)
            self.assertEqual(len(module["interface"]["methods"]), test["methods"], name)

    def test_every_verdict_carries_the_sentence_the_rule_is_argued_for_with(self):
        modules = self.modules(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH),
            **dict(self.collaborators(), **self.two_callers())
        )
        argued = {
            answer["verdict"]: answer["because"]
            for answer in (
                self.the_rule()["passThrough"],
                self.the_rule()["earnsItsKeep"],
                self.the_rule()["noFinding"],
            )
        }

        for name, module in sorted(modules.items()):
            test = module["deletionTest"]
            if test["verdict"] is None:
                continue
            self.assertEqual(argued[test["verdict"]], test["because"], name)

    def test_the_counts_the_graph_reports_are_the_verdicts_it_holds(self):
        modules = self.modules(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH),
            **dict(self.collaborators(), **self.two_callers())
        )

        for entry in self.the_rule()["modulesByVerdict"]:
            self.assertEqual(
                sum(
                    1
                    for module in modules.values()
                    if module["deletionTest"]["verdict"] == entry["verdict"]
                ),
                entry["modules"],
                entry["verdict"],
            )
        self.assertEqual(
            len(modules) - self.document["scoring"]["modulesNeverScored"],
            sum(entry["modules"] for entry in self.the_rule()["modulesByVerdict"]),
        )

    def test_the_thresholds_that_decided_are_in_the_document_they_decided(self):
        """The rule travels with the answers, so a verdict can be recomputed from the graph."""
        self.modules(("Till", A_DEEP_MODULE), **self.collaborators())
        rule = self.the_rule()["passThrough"]

        self.assertIsInstance(rule["reachAtMost"]["perMethod"], int)
        self.assertIsInstance(rule["reachAtMost"]["neverBelow"], int)
        self.assertIsInstance(rule["callersAtLeast"], int)
        for module in self.document["modules"]:
            test = module["deletionTest"]
            if test["verdict"] is None:
                continue
            allowance = max(
                rule["reachAtMost"]["neverBelow"],
                rule["reachAtMost"]["perMethod"] * test["methods"],
            )
            if test["reach"] > allowance:
                expected = self.the_rule()["earnsItsKeep"]["verdict"]
            elif test["callers"] >= rule["callersAtLeast"]:
                expected = rule["verdict"]
            else:
                expected = self.the_rule()["noFinding"]["verdict"]
            self.assertEqual(expected, test["verdict"], module["id"])


class TheSameSourceAlwaysGivesTheSameVerdictTest(SourceOfKnownShapeTest):
    """No wording that varies between runs, because no wording is invented at run time."""

    def test_two_runs_over_one_source_give_the_same_verdicts_and_the_same_words(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", A_DEEP_MODULE)
        tree.java("shop.till", "Counter", A_PASS_THROUGH)
        for name, (package, body) in dict(
            self.collaborators(), **self.two_callers()
        ).items():
            tree.java(package, name, body)
        root = graph.source_root(tree.root)

        first = graph.build([root], scoring.load(), A_SNAPSHOT)
        second = graph.build([root], scoring.load(), A_SNAPSHOT)

        self.assertEqual(
            [module["deletionTest"] for module in first["modules"]],
            [module["deletionTest"] for module in second["modules"]],
        )
        self.assertEqual(graph.serialise(first), graph.serialise(second))

    def test_no_verdict_this_repository_renders_is_written_in_the_analyser(self):
        """The verdict is computed, not written: the words are in the file, not in the code.

        A verdict spelled inside the tool would be one an edit to `scoring.json` could not
        move, and the page's whole claim is that a finding can be argued with by editing
        the rule that produced it.
        """
        written = self.as_written()["deletionTest"]
        verdicts = [
            written[answer]["verdict"]
            for answer in ("passThrough", "earnsItsKeep", "noFinding")
        ]
        for name in sorted(os.listdir(TOOL)):
            if not name.endswith(".py"):
                continue
            with open(os.path.join(TOOL, name), "r", encoding="utf-8") as handle:
                source = handle.read()
            for verdict in verdicts:
                for quoted in ('"%s"' % verdict, "'%s'" % verdict):
                    self.assertNotIn(quoted, source, "%s names the verdict %r" % (name, verdict))


class TheLineIsDrawnInTheFileTest(SourceOfKnownShapeTest):
    """Where a pass-through stops being one is a number in `scoring.json`, and it moves."""

    def test_asking_for_a_third_caller_leaves_the_pass_through_without_a_finding(self):
        written = self.as_written()["deletionTest"]
        written["passThrough"] = dict(written["passThrough"], callersAtLeast=3)
        self.scoring_with(written)

        modules = self.modules(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )

        self.assertEqual(
            (self.the_rule()["noFinding"]["verdict"], 2, 2, 2),
            self.verdict_of(modules["Counter"]),
        )

    def test_allowing_a_module_nothing_per_method_makes_the_pass_through_earn_its_keep(self):
        """The same source, the same two callers, and a line drawn somewhere else."""
        written = self.as_written()["deletionTest"]
        written["passThrough"] = dict(
            written["passThrough"], reachAtMost={"perMethod": 0, "neverBelow": 1}
        )
        self.scoring_with(written)

        modules = self.modules(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )

        self.assertEqual(
            (self.the_rule()["earnsItsKeep"]["verdict"], 2, 2, 2),
            self.verdict_of(modules["Counter"]),
        )

    def test_rewording_a_verdict_rewords_every_module_that_receives_it(self):
        written = self.as_written()["deletionTest"]
        written["passThrough"] = dict(
            written["passThrough"],
            verdict="moves the work rather than removing it",
            because="Because I say so, and because I can point at where I said it.",
        )
        self.scoring_with(written)

        modules = self.modules(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )

        self.assertEqual(
            "moves the work rather than removing it",
            modules["Counter"]["deletionTest"]["verdict"],
        )
        self.assertEqual(
            "Because I say so, and because I can point at where I said it.",
            modules["Counter"]["deletionTest"]["because"],
        )

    def test_the_deletion_test_the_committed_file_holds_is_the_one_in_the_document(self):
        self.modules(("Till", A_DEEP_MODULE), **self.collaborators())
        written = self.as_written()["deletionTest"]

        for answer in ("passThrough", "earnsItsKeep", "noFinding"):
            self.assertEqual(
                written[answer]["verdict"], self.the_rule()[answer]["verdict"], answer
            )
            self.assertEqual(
                written[answer]["because"], self.the_rule()[answer]["because"], answer
            )
        self.assertEqual(
            written["passThrough"]["reachAtMost"], self.the_rule()["passThrough"]["reachAtMost"]
        )
        self.assertEqual(
            written["passThrough"]["callersAtLeast"],
            self.the_rule()["passThrough"]["callersAtLeast"],
        )


class ADeletionTestNobodyWroteIsRefusedTest(SourceTreeTest):
    """A verdict rendered from a threshold nobody wrote is worse than no verdict at all."""

    def setUp(self):
        super().setUp()
        with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
            self.as_committed = json.loads(handle.read().decode("utf-8"))
        self.written = 0

    def refusal_for(self, deletion_test):
        document = dict(self.as_committed)
        if deletion_test is None:
            document.pop("deletionTest")
        else:
            document["deletionTest"] = deletion_test
        self.written += 1
        path = os.path.join(self.scratch, "rules-%d.json" % self.written)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(json.dumps(document, indent=2))
        with self.assertRaises(scoring.ConfigurationRefused) as refused:
            scoring.load(path)
        return refused.exception.reason

    def changed(self, **changes):
        written = dict(self.as_committed["deletionTest"])
        written["passThrough"] = dict(written["passThrough"], **changes)
        return written

    def test_a_file_with_no_deletion_test_is_refused_rather_than_judging_nothing(self):
        reason = self.refusal_for(None)

        self.assertIn("deletionTest", reason)

    def test_a_threshold_that_is_not_a_number_is_refused(self):
        reason = self.refusal_for(self.changed(callersAtLeast="two"))

        self.assertIn("deletionTest.passThrough.callersAtLeast", reason)
        self.assertIn("whole number", reason)

    def test_a_threshold_written_as_true_is_refused_rather_than_read_as_one(self):
        reason = self.refusal_for(self.changed(reachAtMost={"perMethod": True, "neverBelow": 1}))

        self.assertIn("reachAtMost.perMethod", reason)

    def test_a_negative_threshold_is_refused(self):
        reason = self.refusal_for(self.changed(reachAtMost={"perMethod": -1, "neverBelow": 1}))

        self.assertIn("reachAtMost.perMethod", reason)
        self.assertIn("less than none", reason)

    def test_asking_for_fewer_than_two_callers_is_refused(self):
        """A pass-through is a claim about where complexity moves to, so there has to be a where."""
        reason = self.refusal_for(self.changed(callersAtLeast=1))

        self.assertIn("callersAtLeast", reason)
        self.assertIn("has not moved anywhere a reader can see", reason)

    def test_a_verdict_with_nothing_written_on_it_is_refused(self):
        written = dict(self.as_committed["deletionTest"])
        written["earnsItsKeep"] = dict(written["earnsItsKeep"], verdict="   ")

        reason = self.refusal_for(written)

        self.assertIn("deletionTest.earnsItsKeep.verdict", reason)

    def test_a_verdict_nobody_can_read_the_reason_for_is_refused(self):
        written = dict(self.as_committed["deletionTest"])
        written["noFinding"] = dict(written["noFinding"], because="")

        reason = self.refusal_for(written)

        self.assertIn("deletionTest.noFinding.because", reason)
        self.assertIn("nobody can argue with", reason)

    def test_two_verdicts_spelled_the_same_are_refused_rather_than_collapsed(self):
        written = dict(self.as_committed["deletionTest"])
        written["noFinding"] = dict(
            written["noFinding"], verdict=written["earnsItsKeep"]["verdict"]
        )

        reason = self.refusal_for(written)

        self.assertIn("could not be told from the other", reason)

    def test_a_key_this_tool_does_not_read_is_refused_rather_than_ignored(self):
        reason = self.refusal_for(dict(self.as_committed["deletionTest"], somethingNew={}))

        self.assertIn("somethingNew", reason)

    def test_a_run_with_a_deletion_test_this_tool_cannot_use_writes_nothing(self):
        """The refusal is the run's, not a traceback's: exit 4, and both outputs untouched."""
        path = os.path.join(self.scratch, "unusable.json")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(json.dumps(dict(self.as_committed, deletionTest={}), indent=2))
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", A_DEEP_MODULE)
        written_to = os.path.join(self.scratch, "out")

        code = cli.main(
            ["--source", tree.root, "--scoring", path,
             "--graph", os.path.join(written_to, "graph.json"),
             "--page", os.path.join(written_to, "page.html"),
             "--log-level", "ERROR",
             "--snapshot-date", A_SNAPSHOT]
        )

        self.assertEqual(4, code)
        self.assertFalse(os.path.exists(written_to))


class TheVerdictIsOnThePageBesideTheModuleTest(SourceOfKnownShapeTest):
    """Drawn on the card it judges, from the document, in the document's own words."""

    def body(self, rendered, name):
        """One function of the page's script, from its opening line to its closing brace."""
        found = rendered[rendered.index("function " + name):]
        return found[:found.index("\n  }")]

    def script(self, rendered):
        """The page's renderer, with the graph document it carries cut away.

        Everything the page *says* has to come out of the document, so the check that the
        wording is not written into the page cannot be run against a file that carries the
        wording as data.
        """
        return rendered[rendered.index("</script>"):]

    def test_the_verdict_is_drawn_on_the_same_card_as_the_shape_it_follows(self):
        _, rendered = self.rendered(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )
        card = rendered[rendered.index("package_.moduleIds.forEach"):]
        card = card[:card.index("\n  });")]

        self.assertIn("drawShape(item, module);", card)
        self.assertIn("drawReach(item, module);", card)
        self.assertIn("drawVerdict(item, module);", card)
        self.assertLess(card.index("drawReach"), card.index("drawVerdict"))

    def test_the_verdict_the_page_draws_is_the_one_the_document_holds(self):
        _, rendered = self.rendered(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )
        drawn = self.body(rendered, "drawVerdict")

        self.assertIn("var test = module.deletionTest;", drawn)
        self.assertIn("test.verdict", drawn)
        self.assertIn("test.reach", drawn)
        self.assertIn("test.methods", drawn)
        self.assertIn("test.callers", drawn)
        self.assertIn("line.title = test.because;", drawn)

    def test_a_module_with_no_verdict_has_none_drawn_for_it(self):
        _, rendered = self.rendered(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )
        drawn = self.body(rendered, "drawVerdict")

        self.assertIn("if (test.verdict === null) { return; }", drawn)

    def test_no_verdict_is_spelled_in_the_page_that_draws_it(self):
        """The page is a rendering of the document, and a rule written into it would not be.

        A verdict inside a string literal is the shape that would matter, which is why the
        quoted form is what is looked for: the page's own prose about what a pass-through
        looks like is prose, and it is drawn from no module.
        """
        _, rendered = self.rendered(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )
        script = self.script(rendered)

        for answer in ("passThrough", "earnsItsKeep", "noFinding"):
            verdict = self.the_rule()[answer]["verdict"]
            for quoted in ('"%s"' % verdict, "'%s'" % verdict):
                self.assertNotIn(quoted, script, answer)
            self.assertNotIn(self.the_rule()[answer]["because"][:40], script, answer)

    def test_the_page_prints_the_rule_and_the_thresholds_from_the_document(self):
        _, rendered = self.rendered(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )
        script = self.script(rendered)

        self.assertIn("What the verdict says", script)
        self.assertIn("document_.scoring.deletionTest.passThrough.reachAtMost.perMethod", script)
        self.assertIn("document_.scoring.deletionTest.passThrough.reachAtMost.neverBelow", script)
        self.assertIn("document_.scoring.deletionTest.passThrough.callersAtLeast", script)
        self.assertIn("document_.scoring.deletionTest.modulesByVerdict", script)
        self.assertIn("document_.scoring.configuration", script)

    def test_the_page_says_a_verdict_is_an_observation_rather_than_a_proposal(self):
        _, rendered = self.rendered(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )
        script = self.script(rendered)

        self.assertIn("not a proposal to delete it", script)
        self.assertIn("no module here is ", script)

    def test_the_page_admits_that_a_verdict_inherits_the_floor_under_the_fan(self):
        """A module coordinating what this graph does not hold reads as coordinating nothing."""
        _, rendered = self.rendered(
            ("Counter", A_PASS_THROUGH), **dict(self.collaborators(), **self.two_callers())
        )
        script = self.script(rendered)

        self.assertIn("that fan is a floor", script)
        self.assertIn("on a count that is short", script)
