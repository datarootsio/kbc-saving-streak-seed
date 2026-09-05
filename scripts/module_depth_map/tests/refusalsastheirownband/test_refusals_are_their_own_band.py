"""Every refusal a module can answer with, priced as interface and reported apart from it.

Three fixtures carry the whole claim, one for each way documentation and implementation
can stand to each other: a module that documents a refusal it cannot raise, a module that
raises one it never documents, and a module where the two agree. They are purpose-built
source trees rather than the application, so they say what the rule is rather than what
this week's code happens to do — and the two disagreements stay reproducible after
somebody fixes the ones this repository currently has.

Nothing here spells a finding. Both are named in `scoring.json`, and a test below reads
them out of the document and then greps the analyser for them, because "the code and the
comment disagree" is only a finding a reader can argue with if the words behind it are in
a file they can edit.
"""

import json
import os

from ... import graph, page, scoring
from ..support.sourcetrees import SourceTreeTest

TOOL = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# The refusal itself: a type a module can answer with. Never scored, and that is the
# point of having it in the tree — a refusal is read off the module that raises it, not
# off the module that declares it.
A_REFUSAL = (
    "public class Shut extends RuntimeException {\n"
    "    public Shut(String reason) { super(reason); }\n}"
)
ANOTHER_REFUSAL = (
    "public class Locked extends RuntimeException {\n"
    "    public Locked(String reason) { super(reason); }\n}"
)

# Documentation and implementation agreeing: what the javadoc promises is what the body
# throws.
KEEPS_ITS_WORD = (
    "public class Gate {\n"
    "\n"
    "    /**\n"
    "     * Lets one through.\n"
    "     *\n"
    "     * @throws Shut if the gate is shut\n"
    "     */\n"
    "    public void go(long id) {\n"
    "        if (id < 0) { throw new Shut(\"the gate is shut\"); }\n"
    "    }\n}"
)

# A promise nothing can keep: the javadoc names a refusal the body never throws.
PROMISES_WHAT_IT_CANNOT_DO = (
    "public class Turnstile {\n"
    "\n"
    "    /**\n"
    "     * Lets one through.\n"
    "     *\n"
    "     * @throws Shut if the turnstile is shut\n"
    "     */\n"
    "    public void go(long id) {\n"
    "    }\n}"
)

# One name declared twice, handing back two different things. Which of them
# `throw refusing("no")` means is javac's answer to give and not this tool's, and reading
# it as either one is how a module that kept its word came to carry a finding in each
# direction — and a `String` on its interface as a refusal.
TWO_HELPERS_OF_ONE_NAME = (
    "public class Overloaded {\n"
    "\n"
    "    /**\n"
    "     * Lets one through.\n"
    "     *\n"
    "     * @throws Shut if it is shut\n"
    "     */\n"
    "    public void go(long id) {\n"
    "        if (id < 0) { throw refusing(\"no\"); }\n"
    "    }\n"
    "\n"
    "    private Shut refusing(String why) { return new Shut(why); }\n"
    "\n"
    "    private String refusing(int code) { return \"code \" + code; }\n}"
)

# The same shape, agreeing. Whichever of the two the call meant, it throws a `Shut`, so
# there is nothing here to resolve and nothing to guess at either.
TWO_HELPERS_THAT_AGREE = (
    "public class Overloaded {\n"
    "\n"
    "    /** @throws Shut if it is shut */\n"
    "    public void go(long id) {\n"
    "        if (id < 0) { throw refusing(\"no\"); }\n"
    "    }\n"
    "\n"
    "    private Shut refusing(String why) { return new Shut(why); }\n"
    "\n"
    "    private Shut refusing(String why, long id) { return new Shut(why); }\n}"
)

# The commonest validation idiom in Java: a constructor that refuses, documented on the
# constructor because that is the member a caller of `new Built(-1)` meets.
A_CONSTRUCTOR_THAT_REFUSES = (
    "public class Built {\n"
    "\n"
    "    private final long cents;\n"
    "\n"
    "    /**\n"
    "     * Builds one.\n"
    "     *\n"
    "     * @throws Shut if the amount is negative\n"
    "     */\n"
    "    public Built(long cents) {\n"
    "        if (cents < 0) { throw new Shut(\"negative\"); }\n"
    "        this.cents = cents;\n"
    "    }\n"
    "\n"
    "    public long cents() { return cents; }\n}"
)

# A promise made to whoever implements it. The interface's own body throws nothing, and
# there is nothing there that ever could.
A_SEAM_WITH_NO_BODY_UNDER_IT = (
    "public interface Turnstiles {\n"
    "\n"
    "    /**\n"
    "     * Lets one through.\n"
    "     *\n"
    "     * @throws Shut if it is shut\n"
    "     */\n"
    "    void go(long id);\n}"
)

# The other direction: a body that refuses and a seam that says nothing about it.
REFUSES_WITHOUT_SAYING_SO = (
    "public class Barrier {\n"
    "\n"
    "    /** Lets one through. */\n"
    "    public void go(long id) {\n"
    "        if (id < 0) { throw new Locked(\"the barrier is down\"); }\n"
    "    }\n}"
)


class SourceOfKnownShapeTest(SourceTreeTest):
    """A test that turns a handful of Java files into modules and reads their refusals."""

    def modules(self, *sources):
        self.trees = getattr(self, "trees", 0) + 1
        tree = self.tree("fixture-%d" % self.trees)
        for name, body in sources:
            tree.java("shop.gate", name, body)

        self.document = graph.build([graph.java_root(tree.root)], scoring.load(self.rules()))

        self.assertEqual([], self.document["source"]["unparsed"])
        return {module["name"]: module for module in self.document["modules"]}

    def rendered(self, *sources):
        modules = self.modules(*sources)
        return modules, page.render(
            self.document, graph.serialise(self.document)
        ).decode("utf-8")

    def rules(self):
        return getattr(self, "written_rules", None)

    def as_written(self):
        with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
            return json.loads(handle.read().decode("utf-8"))

    def scoring_with(self, refusals=None, weights=None):
        """The tool's own configuration with one section changed, written to a file."""
        document = self.as_written()
        if refusals is not None:
            document["refusals"] = refusals
        if weights is not None:
            document["interfaceCost"] = dict(document["interfaceCost"], weights=weights)
        path = os.path.join(self.scratch, "rules.json")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(json.dumps(document, indent=2))
        self.written_rules = path
        return path

    def the_rule(self):
        return self.document["scoring"]["refusals"]

    def three_gates(self):
        """The whole cast: the agreeing module and both disagreements, in one graph."""
        return (
            ("Shut", A_REFUSAL),
            ("Locked", ANOTHER_REFUSAL),
            ("Gate", KEEPS_ITS_WORD),
            ("Turnstile", PROMISES_WHAT_IT_CANNOT_DO),
            ("Barrier", REFUSES_WITHOUT_SAYING_SO),
        )

    def refusals_of(self, module):
        """One module's refusals as {name: (documented, raised)}."""
        return {
            refusal["name"]: (refusal["documented"], refusal["raised"])
            for refusal in module["interface"]["refusals"]
        }

    def checked_of(self, module):
        """Which of one module's refusals this tool held against its implementation."""
        return {
            refusal["name"]: refusal["checked"]
            for refusal in module["interface"]["refusals"]
        }


class EveryRefusalAModuleCanAnswerWithIsPartOfItsInterfaceTest(SourceOfKnownShapeTest):
    """A caller who does not know how a module refuses has not learned the module."""

    def test_a_refusal_a_module_documents_and_raises_is_on_its_interface(self):
        modules = self.modules(("Shut", A_REFUSAL), ("Gate", KEEPS_ITS_WORD))

        self.assertEqual({"Shut": (True, True)}, self.refusals_of(modules["Gate"]))

    def test_a_refusal_only_the_body_raises_is_still_on_the_interface(self):
        """A caller meets it whether or not anybody wrote it down."""
        modules = self.modules(("Locked", ANOTHER_REFUSAL), ("Barrier", REFUSES_WITHOUT_SAYING_SO))

        self.assertEqual({"Locked": (False, True)}, self.refusals_of(modules["Barrier"]))

    def test_a_refusal_only_the_documentation_promises_is_still_on_the_interface(self):
        """A caller writes a catch for it, so it is something they had to learn."""
        modules = self.modules(("Shut", A_REFUSAL), ("Turnstile", PROMISES_WHAT_IT_CANNOT_DO))

        self.assertEqual({"Shut": (True, False)}, self.refusals_of(modules["Turnstile"]))

    def test_a_module_that_can_refuse_in_no_way_carries_an_empty_band(self):
        """Absent is not the same as nothing, and a missing key would be neither."""
        modules = self.modules(
            ("Shelf", "public class Shelf {\n    public void restock() {}\n}")
        )

        self.assertEqual([], modules["Shelf"]["interface"]["refusals"])
        self.assertEqual(0, modules["Shelf"]["interface"]["refusalCost"])

    def test_each_refusal_names_the_methods_that_document_it(self):
        modules = self.modules(("Shut", A_REFUSAL), ("Gate", KEEPS_ITS_WORD))

        self.assertEqual(
            [
                {
                    "name": "Shut",
                    "documented": True,
                    "documentedBy": ["go"],
                    "raised": True,
                    "checked": True,
                }
            ],
            modules["Gate"]["interface"]["refusals"],
        )

    def test_the_method_carries_what_its_own_documentation_promised(self):
        """Per method as well as per module, so the module's band can be checked against it."""
        modules = self.modules(("Shut", A_REFUSAL), ("Gate", KEEPS_ITS_WORD))
        methods = {
            method["name"]: method for method in modules["Gate"]["interface"]["methods"]
        }

        self.assertEqual(["Shut"], methods["go"]["documentedRefusals"])

    def test_a_refusal_raised_through_a_method_of_the_modules_own_is_read(self):
        """`throw refusing(why)` is a throw of whatever `refusing` hands back.

        The one spelling of an indirect throw that can be followed without resolving
        names the way javac does: the declaration is in the same file, and its return
        type has already been read. Both of the modules in this repository that document
        a refusal *and* keep their word write it this way, and left unfollowed each of
        them is reported as promising something it never raises.
        """
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Latch", "public class Latch {\n"
                      "\n"
                      "    /** @throws Shut if it is shut */\n"
                      "    public void go(long id) {\n"
                      "        if (id < 0) { throw refusing(\"shut\"); }\n"
                      "    }\n"
                      "\n"
                      "    private Shut refusing(String reason) { return new Shut(reason); }\n}"),
        )

        self.assertEqual({"Shut": (True, True)}, self.refusals_of(modules["Latch"]))
        self.assertEqual([], modules["Latch"]["findings"])

    def test_the_refusals_are_emitted_in_a_stable_sorted_order(self):
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Locked", ANOTHER_REFUSAL),
            ("Both", "public class Both {\n"
                     "    public void a() { throw new Shut(\"a\"); }\n"
                     "    public void b() { throw new Locked(\"b\"); }\n}"),
        )

        self.assertEqual(
            ["Locked", "Shut"],
            [refusal["name"] for refusal in modules["Both"]["interface"]["refusals"]],
        )

    def test_a_module_no_rule_scores_still_has_its_refusals_read(self):
        """Its band is absent, like the rest of its cost; the names are facts either way."""
        modules = self.modules(
            ("Locked", ANOTHER_REFUSAL),
            ("Coin", "public record Coin(long cents) {\n"
                     "    public Coin {\n"
                     "        if (cents < 0) { throw new Locked(\"no\"); }\n"
                     "    }\n}"),
        )

        self.assertEqual({"Locked": (False, True)}, self.refusals_of(modules["Coin"]))
        self.assertIsNone(modules["Coin"]["interface"]["refusalCost"])
        self.assertIsNone(modules["Coin"]["interface"]["cost"])


class ADocumentedRefusalIsReadFromTheSourcesOwnWordsTest(SourceOfKnownShapeTest):
    """`@throws`, and nothing else. Nothing here infers a refusal from a name or a shape."""

    def test_a_refusal_named_only_in_prose_is_not_a_documented_one(self):
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Gate", "public class Gate {\n"
                     "\n"
                     "    /** Lets one through. Shut when the gate is shut. */\n"
                     "    public void go(long id) {}\n}"),
        )

        self.assertEqual([], modules["Gate"]["interface"]["refusals"])

    def test_a_refusal_named_in_an_inline_tag_is_prose_rather_than_a_promise(self):
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Gate", "public class Gate {\n"
                     "\n"
                     "    /** Lets one through, unless {@link Shut} says otherwise. */\n"
                     "    public void go(long id) {}\n}"),
        )

        self.assertEqual([], modules["Gate"]["interface"]["refusals"])

    def test_a_tag_written_in_an_ordinary_comment_is_not_documentation(self):
        """Javadoc opens `/**`. A `/*` block is a note to whoever is editing the file."""
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Gate", "public class Gate {\n"
                     "\n"
                     "    /* @throws Shut if the gate is shut */\n"
                     "    public void go(long id) {}\n}"),
        )

        self.assertEqual([], modules["Gate"]["interface"]["refusals"])

    def test_a_refusal_written_with_its_package_is_the_refusal_written_without_it(self):
        modules = self.modules(
            ("Gate", "public class Gate {\n"
                     "\n"
                     "    /** @throws java.lang.IllegalStateException if it is shut */\n"
                     "    public void go(long id) {\n"
                     "        throw new IllegalStateException(\"shut\");\n"
                     "    }\n}"),
        )

        self.assertEqual({"IllegalStateException": (True, True)}, self.refusals_of(modules["Gate"]))
        self.assertEqual([], modules["Gate"]["findings"])

    def test_a_javadoc_with_an_annotation_under_it_still_documents_the_method(self):
        """`@Transactional` is written between the javadoc and the method it belongs to."""
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Gate", "public class Gate {\n"
                     "\n"
                     "    /** @throws Shut if the gate is shut */\n"
                     "    @Deprecated\n"
                     "    public void go(long id) { throw new Shut(\"shut\"); }\n}"),
        )

        self.assertEqual({"Shut": (True, True)}, self.refusals_of(modules["Gate"]))

    def test_a_javadoc_written_flush_against_its_member_still_documents_it(self):
        """Legal, ugly, and the one shape a search by offset can get wrong.

        Nothing at all between the block's `*/` and the method, so the two offsets are
        equal — and a search that compared the names as a tie-break would step past the
        block to whatever came before it.
        """
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Gate", "public class Gate {\n"
                     "    /** @throws Shut if it is shut */public void go(long id) {\n"
                     "        throw new Shut(\"shut\");\n"
                     "    }\n}"),
        )

        self.assertEqual({"Shut": (True, True)}, self.refusals_of(modules["Gate"]))

    def test_a_javadoc_documents_the_member_under_it_and_not_the_next_one(self):
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Gate", "public class Gate {\n"
                     "\n"
                     "    /** @throws Shut if the gate is shut */\n"
                     "    public void go(long id) { throw new Shut(\"shut\"); }\n"
                     "\n"
                     "    public void close() {}\n}"),
        )
        methods = {
            method["name"]: method for method in modules["Gate"]["interface"]["methods"]
        }

        self.assertEqual(["Shut"], methods["go"]["documentedRefusals"])
        self.assertEqual([], methods["close"]["documentedRefusals"])

    def test_a_refusal_documented_on_a_method_no_caller_can_reach_is_not_the_modules(self):
        """A `@throws` on a private helper documents the helper, not the seam."""
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Gate", "public class Gate {\n"
                     "\n"
                     "    public void go(long id) { check(id); }\n"
                     "\n"
                     "    /** @throws Shut if the gate is shut */\n"
                     "    private void check(long id) {}\n}"),
        )

        self.assertEqual([], modules["Gate"]["interface"]["refusals"])


class ARefusalCostsACallerAndIsCountedApartTest(SourceOfKnownShapeTest):
    """Priced with the rest of the interface, and reported as a band of its own."""

    def test_a_refusal_adds_its_weight_to_what_the_interface_costs(self):
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Gate", KEEPS_ITS_WORD),
            ("Plain", "public class Plain {\n    public void go(long id) {}\n}"),
        )
        weights = self.document["scoring"]["weights"]

        self.assertEqual(
            modules["Plain"]["interface"]["cost"] + weights["refusal"],
            modules["Gate"]["interface"]["cost"],
        )

    def test_the_band_and_the_rest_of_the_cost_come_to_the_cost(self):
        modules = self.modules(*self.three_gates())

        for name, module in modules.items():
            interface = module["interface"]
            if interface["cost"] is None:
                continue
            self.assertEqual(
                interface["cost"],
                interface["costWithoutRefusals"] + interface["refusalCost"],
                name,
            )

    def test_the_band_is_the_refusals_at_the_weight_the_file_gives_them(self):
        modules = self.modules(*self.three_gates())
        weights = self.document["scoring"]["weights"]

        for name, module in modules.items():
            interface = module["interface"]
            if interface["cost"] is None:
                continue
            self.assertEqual(
                weights["refusal"] * len(interface["refusals"]),
                interface["refusalCost"],
                name,
            )

    def test_an_honestly_wide_interface_is_told_from_a_merely_wide_one(self):
        """Two modules of the same cost, one of which is wide because it says how it fails."""
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Honest", "public class Honest {\n"
                       "\n"
                       "    /** @throws Shut if it is shut */\n"
                       "    public void go(long id) { throw new Shut(\"shut\"); }\n}"),
            ("Merely", "public class Merely {\n"
                       "    public void go(long id) {}\n"
                       "    public void stop(long id) {}\n}"),
        )

        self.assertEqual(
            modules["Merely"]["interface"]["cost"], modules["Honest"]["interface"]["cost"]
        )
        self.assertEqual(2, modules["Honest"]["interface"]["refusalCost"])
        self.assertEqual(0, modules["Merely"]["interface"]["refusalCost"])

    def test_repricing_a_refusal_in_the_file_reprices_every_module_that_has_one(self):
        """The weight is a number in `scoring.json`, and moving it moves the band."""
        self.scoring_with(
            weights=dict(self.as_written()["interfaceCost"]["weights"], refusal=7)
        )

        modules = self.modules(("Shut", A_REFUSAL), ("Gate", KEEPS_ITS_WORD))

        self.assertEqual(7, modules["Gate"]["interface"]["refusalCost"])

    def test_charging_nothing_for_a_refusal_still_reports_the_band(self):
        """A reader who thinks a refusal should be free can say so and still see them."""
        self.scoring_with(
            weights=dict(self.as_written()["interfaceCost"]["weights"], refusal=0)
        )

        modules = self.modules(("Shut", A_REFUSAL), ("Gate", KEEPS_ITS_WORD))

        self.assertEqual(0, modules["Gate"]["interface"]["refusalCost"])
        self.assertEqual({"Shut": (True, True)}, self.refusals_of(modules["Gate"]))

    def test_the_document_counts_the_refusals_it_read(self):
        self.modules(*self.three_gates())

        self.assertEqual(3, self.the_rule()["refusalsRead"])


class WhereTheDocumentationAndTheCodeDisagreeTest(SourceOfKnownShapeTest):
    """One finding in each direction, each naming both sides of what it found."""

    def findings_of(self, module):
        return {finding["refusal"]: finding for finding in module["findings"]}

    def test_a_module_documenting_a_refusal_it_cannot_raise_produces_a_finding(self):
        modules = self.modules(*self.three_gates())

        found = self.findings_of(modules["Turnstile"])["Shut"]

        self.assertEqual(self.the_rule()["documentedNeverRaised"]["finding"], found["finding"])
        self.assertEqual(self.the_rule()["documentedNeverRaised"]["because"], found["because"])

    def test_that_finding_names_both_sides_of_the_disagreement(self):
        modules = self.modules(*self.three_gates())

        found = self.findings_of(modules["Turnstile"])["Shut"]

        self.assertEqual("Shut", found["refusal"])
        self.assertTrue(found["documented"])
        self.assertEqual(["go"], found["documentedBy"])
        self.assertFalse(found["raised"])

    def test_a_module_raising_a_refusal_it_does_not_document_produces_a_finding(self):
        modules = self.modules(*self.three_gates())

        found = self.findings_of(modules["Barrier"])["Locked"]

        self.assertEqual(self.the_rule()["raisedNeverDocumented"]["finding"], found["finding"])
        self.assertEqual(self.the_rule()["raisedNeverDocumented"]["because"], found["because"])

    def test_that_finding_names_both_sides_too(self):
        modules = self.modules(*self.three_gates())

        found = self.findings_of(modules["Barrier"])["Locked"]

        self.assertEqual("Locked", found["refusal"])
        self.assertFalse(found["documented"])
        self.assertEqual([], found["documentedBy"])
        self.assertTrue(found["raised"])

    def test_a_module_whose_documentation_and_code_agree_produces_no_finding(self):
        modules = self.modules(*self.three_gates())

        self.assertEqual([], modules["Gate"]["findings"])

    def test_a_module_with_no_refusals_at_all_produces_no_finding(self):
        modules = self.modules(
            ("Shelf", "public class Shelf {\n    public void restock() {}\n}")
        )

        self.assertEqual([], modules["Shelf"]["findings"])

    def test_every_module_in_the_graph_carries_findings_even_when_there_are_none(self):
        modules = self.modules(*self.three_gates())

        for name, module in modules.items():
            self.assertIsInstance(module["findings"], list, name)

    def test_one_module_can_be_in_both_disagreements_at_once(self):
        """Two refusals, one stale promise and one silent throw, on the same module."""
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Locked", ANOTHER_REFUSAL),
            ("Gate", "public class Gate {\n"
                     "\n"
                     "    /** @throws Shut if it is shut */\n"
                     "    public void go(long id) { throw new Locked(\"down\"); }\n}"),
        )

        self.assertEqual(
            [
                (self.the_rule()["raisedNeverDocumented"]["finding"], "Locked"),
                (self.the_rule()["documentedNeverRaised"]["finding"], "Shut"),
            ],
            [(f["finding"], f["refusal"]) for f in modules["Gate"]["findings"]],
        )

    def test_the_document_counts_the_findings_by_kind_and_the_modules_they_are_on(self):
        self.modules(*self.three_gates())

        self.assertEqual(
            [
                {"finding": self.the_rule()["documentedNeverRaised"]["finding"], "findings": 1},
                {"finding": self.the_rule()["raisedNeverDocumented"]["finding"], "findings": 1},
            ],
            self.the_rule()["findingsByKind"],
        )
        self.assertEqual(2, self.the_rule()["modulesWithFindings"])

    def test_two_runs_over_one_source_give_the_same_findings_and_the_same_words(self):
        first = self.modules(*self.three_gates())
        second = self.modules(*self.three_gates())

        self.assertEqual(
            [first[name]["findings"] for name in sorted(first)],
            [second[name]["findings"] for name in sorted(second)],
        )


class TheWordsForADisagreementLiveInTheFileTest(SourceOfKnownShapeTest):
    """Reword the rule and every finding rewords with it; nothing in the tool spells one."""

    def test_rewording_a_finding_rewords_every_module_that_receives_it(self):
        self.scoring_with(
            refusals=dict(
                self.as_written()["refusals"],
                documentedNeverRaised={
                    "finding": "says it can and cannot",
                    "because": "Because I say so, and because I can point at where I said it.",
                },
            )
        )

        modules = self.modules(("Shut", A_REFUSAL), ("Turnstile", PROMISES_WHAT_IT_CANNOT_DO))

        self.assertEqual(
            [("says it can and cannot", "Shut")],
            [(f["finding"], f["refusal"]) for f in modules["Turnstile"]["findings"]],
        )
        self.assertEqual(
            "Because I say so, and because I can point at where I said it.",
            modules["Turnstile"]["findings"][0]["because"],
        )

    def test_no_finding_this_tool_renders_is_written_in_the_analyser(self):
        self.modules(*self.three_gates())
        written = self.as_written()["refusals"]
        findings = [
            written[side]["finding"]
            for side in ("documentedNeverRaised", "raisedNeverDocumented")
        ]

        for name in sorted(os.listdir(TOOL)):
            if not name.endswith(".py"):
                continue
            with open(os.path.join(TOOL, name), "r", encoding="utf-8") as handle:
                source = handle.read()
            for finding in findings:
                for quoted in ('"%s"' % finding, "'%s'" % finding):
                    self.assertNotIn(quoted, source, "%s names the finding %r" % (name, finding))

    def test_the_refusal_rule_the_committed_file_holds_is_the_one_in_the_document(self):
        self.modules(*self.three_gates())
        written = self.as_written()["refusals"]

        self.assertEqual(written["because"], self.the_rule()["because"])
        for side in ("documentedNeverRaised", "raisedNeverDocumented"):
            self.assertEqual(written[side]["finding"], self.the_rule()[side]["finding"])
            self.assertEqual(written[side]["because"], self.the_rule()[side]["because"])


class TheRefusalRuleIsUsedOrTheRunStopsTest(SourceOfKnownShapeTest):
    """A refusal reported under a rule nobody wrote is worse than no report at all."""

    def refusal_for(self, **changed):
        document = self.as_written()
        for key, value in changed.items():
            if value is None:
                document.pop(key, None)
            else:
                document[key] = value
        path = os.path.join(self.scratch, "broken.json")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(json.dumps(document, indent=2))
        with self.assertRaises(scoring.ConfigurationRefused) as refused:
            scoring.load(path)
        return refused.exception.reason

    def test_a_file_with_no_refusal_rule_is_refused_rather_than_naming_nothing(self):
        reason = self.refusal_for(refusals=None)

        self.assertIn("refusals", reason)
        self.assertIn("it has to be an object", reason)

    def test_a_finding_with_nothing_written_on_it_is_refused(self):
        written = self.as_written()["refusals"]
        written["raisedNeverDocumented"] = dict(
            written["raisedNeverDocumented"], finding="   "
        )

        reason = self.refusal_for(refusals=written)

        self.assertIn("raisedNeverDocumented", reason)
        self.assertIn("no reader could act on", reason)

    def test_a_finding_nobody_can_read_the_reason_for_is_refused(self):
        written = self.as_written()["refusals"]
        written["documentedNeverRaised"] = dict(
            written["documentedNeverRaised"], because=""
        )

        reason = self.refusal_for(refusals=written)

        self.assertIn("documentedNeverRaised", reason)
        self.assertIn("argue with", reason)

    def test_two_findings_spelled_the_same_are_refused_rather_than_collapsed(self):
        written = self.as_written()["refusals"]
        written["raisedNeverDocumented"] = dict(
            written["raisedNeverDocumented"],
            finding=written["documentedNeverRaised"]["finding"],
        )

        reason = self.refusal_for(refusals=written)

        self.assertIn("could not be told from the other", reason)

    def test_a_key_this_tool_does_not_read_is_refused_rather_than_ignored(self):
        written = dict(self.as_written()["refusals"], somethingNew=1)

        reason = self.refusal_for(refusals=written)

        self.assertIn("refusals names somethingNew", reason)

    def test_a_configuration_with_no_refusal_weight_is_refused(self):
        weights = dict(self.as_written()["interfaceCost"]["weights"])
        weights.pop("refusal")

        reason = self.refusal_for(
            interfaceCost=dict(self.as_written()["interfaceCost"], weights=weights)
        )

        self.assertIn("interfaceCost.weights.refusal", reason)


class ARefusalOnlyOneSideOfWhichCouldBeReadTest(SourceOfKnownShapeTest):
    """The one thing this feature cannot afford: accusing a module that kept its word.

    A finding is worth what a reader's trust in it is worth, and a machine that says
    "documented but never raised" about a module doing exactly what its javadoc says stops
    being read at all. So where one side of a refusal could not be read, the refusal is
    still on the band — a caller has it to learn either way — and no finding is made about
    it. The card still prints both sides, so a reader can go and check what the tool would
    not.
    """

    def test_a_name_declared_twice_that_disagrees_is_followed_to_neither(self):
        """Which `refusing` a call meant is javac's answer, and it is not guessed at."""
        modules = self.modules(("Shut", A_REFUSAL), ("Overloaded", TWO_HELPERS_OF_ONE_NAME))

        self.assertEqual({"Shut": (True, False)}, self.refusals_of(modules["Overloaded"]))

    def test_a_type_that_is_not_a_refusal_at_all_never_reaches_the_band(self):
        """`private String refusing(int)` hands back a String, which nothing can throw."""
        modules = self.modules(("Shut", A_REFUSAL), ("Overloaded", TWO_HELPERS_OF_ONE_NAME))

        self.assertNotIn("String", self.refusals_of(modules["Overloaded"]))
        self.assertEqual(
            self.document["scoring"]["weights"]["refusal"],
            modules["Overloaded"]["interface"]["refusalCost"],
        )

    def test_a_module_whose_helpers_collide_is_accused_of_nothing(self):
        """Documentation and implementation agree here; only this tool cannot see it."""
        modules = self.modules(("Shut", A_REFUSAL), ("Overloaded", TWO_HELPERS_OF_ONE_NAME))

        self.assertEqual([], modules["Overloaded"]["findings"])
        self.assertEqual({"Shut": False}, self.checked_of(modules["Overloaded"]))

    def test_two_declarations_of_one_name_that_agree_are_followed(self):
        """Whichever the call meant it throws a Shut, so nothing has to be resolved."""
        modules = self.modules(("Shut", A_REFUSAL), ("Overloaded", TWO_HELPERS_THAT_AGREE))

        self.assertEqual({"Shut": (True, True)}, self.refusals_of(modules["Overloaded"]))
        self.assertEqual({"Shut": True}, self.checked_of(modules["Overloaded"]))
        self.assertEqual([], modules["Overloaded"]["findings"])

    def test_a_throw_of_a_name_rather_than_a_type_leaves_the_promise_unchecked(self):
        """`throw thrown` needs a type only javac resolves, so nothing is claimed about it."""
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Rethrows", "public class Rethrows {\n"
                         "\n"
                         "    /** @throws Shut if it is shut */\n"
                         "    public void go(Shut thrown) { throw thrown; }\n}"),
        )

        self.assertEqual({"Shut": (True, False)}, self.refusals_of(modules["Rethrows"]))
        self.assertEqual({"Shut": False}, self.checked_of(modules["Rethrows"]))
        self.assertEqual([], modules["Rethrows"]["findings"])

    def test_a_body_this_tool_read_whole_is_still_held_to_what_it_promised(self):
        """The floor is not an excuse: a module with nothing unreadable is checked."""
        modules = self.modules(("Shut", A_REFUSAL), ("Turnstile", PROMISES_WHAT_IT_CANNOT_DO))

        self.assertEqual({"Shut": True}, self.checked_of(modules["Turnstile"]))
        self.assertEqual(
            [self.the_rule()["documentedNeverRaised"]["finding"]],
            [f["finding"] for f in modules["Turnstile"]["findings"]],
        )

    def test_a_refusal_promised_by_a_method_with_no_body_is_on_the_band(self):
        """A caller of the interface has it to learn, whoever ends up raising it."""
        modules = self.modules(("Shut", A_REFUSAL), ("Turnstiles", A_SEAM_WITH_NO_BODY_UNDER_IT))

        self.assertEqual({"Shut": (True, False)}, self.refusals_of(modules["Turnstiles"]))

    def test_a_refusal_promised_by_a_method_with_no_body_is_never_a_finding(self):
        """The promise is to whoever implements it; this module was never going to keep it."""
        modules = self.modules(("Shut", A_REFUSAL), ("Turnstiles", A_SEAM_WITH_NO_BODY_UNDER_IT))

        self.assertEqual({"Shut": False}, self.checked_of(modules["Turnstiles"]))
        self.assertEqual([], modules["Turnstiles"]["findings"])

    def test_an_abstract_method_promising_a_refusal_is_not_accused_either(self):
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Gates", "public abstract class Gates {\n"
                      "\n"
                      "    /** @throws Shut if it is shut */\n"
                      "    public abstract void go(long id);\n}"),
        )

        self.assertEqual([], modules["Gates"]["findings"])

    def test_a_module_with_a_body_and_a_bodiless_promise_is_checked_on_the_body(self):
        """One unimplemented signature does not buy the rest of the module an alibi."""
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Locked", ANOTHER_REFUSAL),
            ("Gates", "public abstract class Gates {\n"
                      "\n"
                      "    /** @throws Shut if it is shut */\n"
                      "    public abstract void go(long id);\n"
                      "\n"
                      "    /** @throws Locked if it is locked */\n"
                      "    public void close(long id) {}\n}"),
        )

        self.assertEqual({"Shut": False, "Locked": True}, self.checked_of(modules["Gates"]))
        self.assertEqual(
            ["Locked"], [f["refusal"] for f in modules["Gates"]["findings"]]
        )

    def test_the_document_counts_the_refusals_it_never_checked(self):
        """Three denominators, and this is the honest one: neither finding nor agreement."""
        self.modules(
            ("Shut", A_REFUSAL),
            ("Locked", ANOTHER_REFUSAL),
            ("Turnstiles", A_SEAM_WITH_NO_BODY_UNDER_IT),
            ("Gate", KEEPS_ITS_WORD),
            ("Barrier", REFUSES_WITHOUT_SAYING_SO),
        )

        self.assertEqual(3, self.the_rule()["refusalsRead"])
        self.assertEqual(1, self.the_rule()["refusalsNotChecked"])


class WhatAConstructorSaysAboutHowAModuleRefusesTest(SourceOfKnownShapeTest):
    """Both sides of a constructor's refusal, or neither. Reading one is what accuses.

    A constructor's body is read into what a module raises — `new AmountOfMoney(-1)`
    refuses, and a caller meets that refusal — so its `@throws` is read as the module
    documenting itself. The other way round was the alternative: stop reading a
    constructor's throw. That says something false about the source, and validating in a
    constructor is the sanctioned way to give a Java value an invariant.
    """

    def test_a_refusal_documented_on_a_constructor_is_the_modules_own(self):
        modules = self.modules(("Shut", A_REFUSAL), ("Built", A_CONSTRUCTOR_THAT_REFUSES))

        self.assertEqual({"Shut": (True, True)}, self.refusals_of(modules["Built"]))

    def test_a_constructor_that_documents_what_it_throws_is_accused_of_nothing(self):
        modules = self.modules(("Shut", A_REFUSAL), ("Built", A_CONSTRUCTOR_THAT_REFUSES))

        self.assertEqual([], modules["Built"]["findings"])

    def test_the_constructor_is_named_as_what_documents_it(self):
        """By the module's own name, which is what a constructor is called."""
        modules = self.modules(("Shut", A_REFUSAL), ("Built", A_CONSTRUCTOR_THAT_REFUSES))

        self.assertEqual(
            ["Built"], modules["Built"]["interface"]["refusals"][0]["documentedBy"]
        )

    def test_a_constructor_promising_a_refusal_nothing_raises_is_still_a_finding(self):
        """Read as documentation, so a stale one on a constructor is caught like any other."""
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Built", "public class Built {\n"
                      "\n"
                      "    /** @throws Shut if the amount is negative */\n"
                      "    public Built(long cents) {}\n}"),
        )

        self.assertEqual(
            [(self.the_rule()["documentedNeverRaised"]["finding"], "Shut")],
            [(f["finding"], f["refusal"]) for f in modules["Built"]["findings"]],
        )

    def test_a_refusal_documented_on_a_constructor_no_caller_can_reach_is_not_the_modules(self):
        """A private constructor is a factory's business, the way a private helper is."""
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Built", "public class Built {\n"
                      "\n"
                      "    /** @throws Shut if the amount is negative */\n"
                      "    private Built(long cents) {}\n"
                      "\n"
                      "    public static Built of(long cents) { return new Built(cents); }\n}"),
        )

        self.assertEqual([], modules["Built"]["interface"]["refusals"])
        self.assertEqual([], modules["Built"]["findings"])

    def test_a_records_compact_constructor_documents_the_record(self):
        """The one constructor Java lets you write with no parameter list at all."""
        modules = self.modules(
            ("Locked", ANOTHER_REFUSAL),
            ("Coin", "public record Coin(long cents) {\n"
                     "\n"
                     "    /** @throws Locked if the amount is negative */\n"
                     "    public Coin {\n"
                     "        if (cents < 0) { throw new Locked(\"no\"); }\n"
                     "    }\n}"),
        )

        self.assertEqual({"Locked": (True, True)}, self.refusals_of(modules["Coin"]))
        self.assertEqual([], modules["Coin"]["findings"])

    def test_a_method_named_after_its_module_is_not_its_constructor(self):
        """`public Coin coin()` writes the type's name twice and constructs nothing."""
        modules = self.modules(
            ("Shut", A_REFUSAL),
            ("Coin", "public class Coin {\n"
                     "\n"
                     "    /** @throws Shut if it is shut */\n"
                     "    public Coin Coin() { throw new Shut(\"shut\"); }\n}"),
        )

        self.assertEqual(
            ["Coin"], modules["Coin"]["interface"]["refusals"][0]["documentedBy"]
        )
        self.assertEqual(
            ["Coin"], [m["name"] for m in modules["Coin"]["interface"]["methods"]]
        )


class TheBandIsDrawnOnThePageTest(SourceOfKnownShapeTest):
    """The page shows the band, names the refusals, and prints both sides of a finding."""

    def body(self, rendered, name):
        found = rendered[rendered.index("function " + name):]
        return found[:found.index("\n  }")]

    def script(self, rendered):
        return rendered[rendered.index("</script>"):]

    def test_the_bar_is_drawn_in_two_bands_read_from_the_document(self):
        _, rendered = self.rendered(*self.three_gates())
        drawn = self.body(rendered, "bandsOf")

        self.assertIn("cost: module.interface.costWithoutRefusals", drawn)
        self.assertIn("cost: module.interface.refusalCost", drawn)
        self.assertIn('band: "refuse"', drawn)

    def test_the_refusals_are_named_on_the_same_card_as_the_band(self):
        _, rendered = self.rendered(*self.three_gates())
        card = rendered[rendered.index("package_.moduleIds.forEach"):]
        card = card[:card.index("\n  });")]

        self.assertIn("drawShape(item, module);", card)
        self.assertIn("drawRefusals(item, module);", card)
        self.assertIn("drawFindings(item, module);", card)
        self.assertLess(card.index("drawShape"), card.index("drawRefusals"))

    def test_what_the_page_draws_for_a_refusal_comes_out_of_the_document(self):
        _, rendered = self.rendered(*self.three_gates())
        drawn = self.body(rendered, "drawRefusals")

        self.assertIn("module.interface.refusals", drawn)
        self.assertIn("refusal.name", drawn)
        self.assertIn("refusal.documentedBy", drawn)
        self.assertIn("module.findings", drawn)

    def test_what_the_page_draws_for_a_finding_names_both_sides(self):
        _, rendered = self.rendered(*self.three_gates())
        drawn = self.body(rendered, "drawFindings")

        self.assertIn("finding.finding", drawn)
        self.assertIn("finding.refusal", drawn)
        self.assertIn("finding.documented", drawn)
        self.assertIn("finding.documentedBy", drawn)
        self.assertIn("finding.raised", drawn)
        self.assertIn("line.title = finding.because;", drawn)

    def test_a_module_with_nothing_to_refuse_has_no_line_drawn_for_it(self):
        _, rendered = self.rendered(*self.three_gates())
        drawn = self.body(rendered, "drawRefusals")

        self.assertIn("if (refusals.length === 0) { return; }", drawn)

    def test_no_finding_is_spelled_in_the_page_that_draws_it(self):
        _, rendered = self.rendered(*self.three_gates())
        script = self.script(rendered)

        for side in ("documentedNeverRaised", "raisedNeverDocumented"):
            named = self.the_rule()[side]
            for quoted in ('"%s"' % named["finding"], "'%s'" % named["finding"]):
                self.assertNotIn(quoted, script, side)
            self.assertNotIn(named["because"][:40], script, side)

    def test_the_page_prints_the_rule_the_band_is_argued_for_with(self):
        _, rendered = self.rendered(*self.three_gates())
        script = self.script(rendered)

        self.assertIn("document_.scoring.refusals.because", script)
        self.assertIn("document_.scoring.refusals.documentedNeverRaised", script)
        self.assertIn("document_.scoring.refusals.raisedNeverDocumented", script)
        self.assertIn("document_.scoring.weights.refusal", script)

    def test_the_page_says_what_the_band_cannot_see(self):
        """A floor nobody can see the edge of is not one a reader can trust."""
        _, rendered = self.rendered(*self.three_gates())
        script = self.script(rendered)

        self.assertIn("throw thrown", script)
        self.assertIn("not guessed at", script)

    def test_the_page_says_which_promises_it_never_held_against_anything(self):
        """A withheld finding is invisible unless the page says it was withheld."""
        _, rendered = self.rendered(*self.three_gates())
        script = self.script(rendered)

        self.assertIn("document_.scoring.refusals.refusalsNotChecked", script)
        self.assertIn("a method with no body", script)

    def test_a_refusal_the_page_never_checked_is_marked_apart_from_both(self):
        """Neither a finding nor an agreement, and drawn as neither."""
        _, rendered = self.rendered(
            ("Shut", A_REFUSAL), ("Turnstiles", A_SEAM_WITH_NO_BODY_UNDER_IT)
        )
        drawn = self.body(rendered, "drawRefusals")

        self.assertIn("refusal.checked", drawn)
        self.assertIn('"refusal unchecked"', drawn)
        self.assertIn(".refusal.unchecked", rendered)
