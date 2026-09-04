"""Changing what counts is a diff on a configuration file, never an edit to the analyser.

The whole claim of the page is that a score can be argued with, and it can only be argued
with if the argument is somewhere a reader can reach. So each test here changes one thing
in a configuration file, runs the tool over source it does not touch, and asserts the
output moved — and one of them asserts, mechanically, that no weight and no rule name
this repository scores by is written anywhere in the analyser.
"""

import json
import os

from ... import cli, graph, javasource, page, scoring
from ..support.sourcetrees import SourceTreeTest

TOOL = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

A_MODULE = "public class Till {\n    public Receipt ring(long id) { return null; }\n}"
A_DATA_CARRIER = "public record Receipt(long cents) {}"


class RulesFromAFileTest(SourceTreeTest):
    """A test that runs the tool with rules it wrote itself."""

    def setUp(self):
        super().setUp()
        with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
            self.as_committed = json.loads(handle.read().decode("utf-8"))
        self.written = 0

    def rules(self, **changes):
        """The tool's own configuration with these top-level keys replaced, as a file."""
        document = dict(self.as_committed)
        document.update(changes)
        self.written += 1
        path = os.path.join(self.scratch, "rules-%d.json" % self.written)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(json.dumps(document, indent=2))
        return path

    def weights(self, **changes):
        """The tool's own configuration with these interface-cost weights replaced."""
        cost = dict(self.as_committed["interfaceCost"])
        cost["weights"] = dict(cost["weights"], **changes)
        return self.rules(interfaceCost=cost)

    def source(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", A_MODULE)
        tree.java("shop.till", "Receipt", A_DATA_CARRIER)
        return graph.java_root(tree.root)

    def modules(self, root, path=None):
        document = graph.build([root], scoring.load(path))
        self.assertEqual([], document["source"]["unparsed"])
        return {module["name"]: module for module in document["modules"]}


class ChangingAWeightChangesTheScoreTest(RulesFromAFileTest):

    def test_the_committed_weights_score_this_fixture_at_four(self):
        """The number the other tests here move away from: one call, one parameter, one type."""
        self.assertEqual(4, self.modules(self.source())["Till"]["interface"]["cost"])

    def test_making_a_method_cost_more_makes_every_module_that_has_one_cost_more(self):
        root = self.source()

        self.assertEqual(
            13, self.modules(root, self.weights(method=10))["Till"]["interface"]["cost"]
        )

    def test_making_a_parameter_cost_nothing_takes_parameters_out_of_the_count(self):
        root = self.source()

        self.assertEqual(
            3, self.modules(root, self.weights(parameter=0))["Till"]["interface"]["cost"]
        )

    def test_making_a_domain_type_cost_the_same_as_a_familiar_one_flattens_the_difference(self):
        """The rule that makes a domain type dearer than a primitive is itself a weight.

        Someone who thinks it should not be can say so in the file, and the page then
        stops making that distinction — which is the point of the file being there.
        """
        root = self.source()
        rules = self.weights(typeToLearn=0, typeEveryCallerAlreadyKnows=0)

        modules = self.modules(root, rules)

        self.assertEqual(2, modules["Till"]["interface"]["cost"])

    def test_the_weights_the_graph_names_are_the_weights_that_were_applied(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", A_MODULE)

        document = graph.build(
            [graph.java_root(tree.root)], scoring.load(self.weights(method=7))
        )

        self.assertEqual(7, document["scoring"]["weights"]["method"])

    def test_narrowing_what_counts_as_reachable_narrows_the_interface(self):
        tree = self.tree("fixture")
        tree.java(
            "shop.till", "Till",
            "class Till {\n    public void ring() {}\n    void countTheDrawer() {}\n}",
        )
        root = graph.java_root(tree.root)
        cost = dict(self.as_committed["interfaceCost"], reachableFromOutside=["public"])

        self.assertEqual(2, self.modules(root)["Till"]["interface"]["cost"])
        self.assertEqual(
            1,
            self.modules(root, self.rules(interfaceCost=cost))["Till"]["interface"]["cost"],
        )

    def test_a_type_named_as_already_known_stops_being_a_type_to_learn(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", A_MODULE)
        root = graph.java_root(tree.root)
        cost = dict(
            self.as_committed["interfaceCost"],
            typesEveryCallerAlreadyKnows=self.as_committed["interfaceCost"][
                "typesEveryCallerAlreadyKnows"
            ]
            + ["Receipt"],
        )

        modules = self.modules(root, self.rules(interfaceCost=cost))

        self.assertEqual(2, modules["Till"]["interface"]["cost"])
        self.assertEqual(
            [{"name": "Receipt", "mustBeLearned": False}, {"name": "long", "mustBeLearned": False}],
            modules["Till"]["interface"]["typesCrossingTheSeam"],
        )


class ChangingAnExclusionChangesWhatIsScoredTest(RulesFromAFileTest):

    def test_taking_a_rule_out_of_the_file_scores_what_it_used_to_exclude(self):
        root = self.source()
        without_data_carriers = [
            exclusion
            for exclusion in self.as_committed["exclusions"]
            if exclusion["rule"] != "data carrier"
        ]

        modules = self.modules(root, self.rules(exclusions=without_data_carriers))

        self.assertIsNone(modules["Receipt"]["excludedBy"])
        self.assertEqual(1, modules["Receipt"]["interface"]["cost"])

    def test_adding_a_rule_to_the_file_excludes_what_used_to_be_scored(self):
        root = self.source()
        rules = self.rules(
            exclusions=self.as_committed["exclusions"]
            + [
                {
                    "rule": "till",
                    "because": "A till is somebody else's problem this week.",
                    "when": {"nameEndsWith": ["Till"]},
                }
            ]
        )

        modules = self.modules(root, rules)

        self.assertEqual("till", modules["Till"]["excludedBy"]["rule"])
        self.assertEqual("name ends with Till", modules["Till"]["excludedBy"]["matched"])
        self.assertIsNone(modules["Till"]["interface"]["cost"])

    def test_renaming_a_rule_renames_it_everywhere_the_output_says_it(self):
        """The graph carries the rule's own words, so one reworded reason is one diff."""
        root = self.source()
        renamed = [dict(exclusion) for exclusion in self.as_committed["exclusions"]]
        renamed[0] = dict(renamed[0], rule="value carrier", because="Reworded on purpose.")

        modules = self.modules(root, self.rules(exclusions=renamed))

        self.assertEqual("value carrier", modules["Receipt"]["excludedBy"]["rule"])

    def test_a_file_with_no_exclusions_at_all_scores_everything(self):
        root = self.source()

        modules = self.modules(root, self.rules(exclusions=[]))

        self.assertEqual([None, None], [modules[name]["excludedBy"] for name in sorted(modules)])

    def test_the_page_says_what_the_file_says_rather_than_what_the_tool_used_to_say(self):
        """The page is a rendering of the document, so an edited rule reaches the reader."""
        tree = self.tree("fixture")
        tree.java("shop.till", "Receipt", A_DATA_CARRIER)
        renamed = [
            dict(exclusion, rule="value carrier", because="Reworded on purpose.")
            if exclusion["rule"] == "data carrier"
            else exclusion
            for exclusion in self.as_committed["exclusions"]
        ]

        document = graph.build(
            [graph.java_root(tree.root)], scoring.load(self.rules(exclusions=renamed))
        )
        rendered = page.render(document, graph.serialise(document)).decode("utf-8")

        self.assertIn("value carrier", rendered)
        self.assertIn("Reworded on purpose.", rendered)


class ARuleThatCouldNeverMatchIsRefusedTest(RulesFromAFileTest):
    """A rule the file names has to be able to fire, or naming it is worse than silence.

    `load`'s own reason for existing is that a condition this tool cannot apply reads as
    "always true" or as "never true", and either one is a score nobody chose. The one
    shape it used to let through is the natural mistake: a name written the way the
    *source* writes it. `extends org.springframework.data.jpa.repository.JpaRepository`
    is matched perfectly well in Java, so writing the same words in the rule looks right
    — but the parser records an annotation, a supertype and a module's name by simple
    name, so the qualified form matched nothing, excluded nothing, and said nothing.
    """

    def refusal_for(self, **changes):
        with self.assertRaises(scoring.ConfigurationRefused) as refused:
            scoring.load(self.rules(**changes))
        return refused.exception.reason

    def with_a_rule(self, when):
        return {
            "exclusions": [
                {"rule": "the rule under test", "because": "For the test.", "when": when}
            ]
        }

    def test_a_qualified_annotation_name_is_refused_rather_than_matching_nothing(self):
        reason = self.refusal_for(
            **self.with_a_rule(
                {"annotatedWith": ["org.springframework.boot.autoconfigure.SpringBootApplication"]}
            )
        )

        self.assertIn("annotatedWith", reason)
        self.assertIn("simple name", reason)
        self.assertIn("write SpringBootApplication instead", reason)

    def test_a_qualified_supertype_name_is_refused_too(self):
        reason = self.refusal_for(
            **self.with_a_rule(
                {"kind": ["interface"],
                 "extendsOrImplements": ["org.springframework.data.jpa.repository.JpaRepository"]}
            )
        )

        self.assertIn("extendsOrImplements", reason)
        self.assertIn("write JpaRepository instead", reason)

    def test_a_suffix_with_a_dot_in_it_is_refused_because_a_module_name_has_none(self):
        reason = self.refusal_for(**self.with_a_rule({"nameEndsWith": ["till.Till"]}))

        self.assertIn("nameEndsWith", reason)
        self.assertIn("write Till instead", reason)

    def test_a_qualified_familiar_type_is_refused_rather_than_making_string_dear(self):
        """Otherwise every caller is charged for learning `String`, `List` and `Optional`."""
        cost = dict(
            self.as_committed["interfaceCost"],
            typesEveryCallerAlreadyKnows=["java.lang.String", "java.util.List"],
        )

        reason = self.refusal_for(interfaceCost=cost)

        self.assertIn("typesEveryCallerAlreadyKnows", reason)
        self.assertIn("write List, String instead", reason)

    def test_a_kind_this_tool_never_reports_is_refused(self):
        reason = self.refusal_for(**self.with_a_rule({"kind": ["struct"]}))

        self.assertIn("struct", reason)
        for kind in javasource.KINDS:
            self.assertIn(kind, reason)

    def test_the_simple_name_the_refusal_asks_for_is_a_rule_that_does_fire(self):
        """The control: the refusal names the form that works, and it works."""
        tree = self.tree("fixture")
        tree.java(
            "shop", "Application",
            '@SpringBootApplication(scanBasePackages = {"shop"})\n'
            "public class Application {\n"
            "    public static void main(String[] args) {}\n}",
        )
        rules = self.rules(
            **self.with_a_rule({"annotatedWith": ["SpringBootApplication"]})
        )

        modules = self.modules(graph.java_root(tree.root), rules)

        self.assertEqual("the rule under test", modules["Application"]["excludedBy"]["rule"])
        self.assertEqual(
            "annotated with SpringBootApplication",
            modules["Application"]["excludedBy"]["matched"],
        )

    def test_the_command_refuses_to_run_on_a_rule_that_could_never_fire(self):
        """Exit 4 and a warning, the same as any other configuration it cannot use."""
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", A_MODULE)
        out = os.path.join(self.scratch, "out")

        with self.assertLogs("module_depth_map.cli", level="WARNING") as logged:
            code = cli.main(
                [
                    "--source", tree.root,
                    "--graph", os.path.join(out, "graph.json"),
                    "--page", os.path.join(out, "page.html"),
                    "--scoring", self.rules(
                        **self.with_a_rule({"annotatedWith": ["a.b.C"]})
                    ),
                ]
            )

        self.assertEqual(4, code)
        self.assertFalse(os.path.exists(out))
        self.assertIn("refused to run", "\n".join(logged.output))
        self.assertIn("simple name", "\n".join(logged.output))


class TheFileIsUsedOrTheRunStopsTest(RulesFromAFileTest):
    """Every path through `load` ends in the file the reader named, or in a refusal.

    Falling back to the built-in rules is the reading `ConfigurationRefused` calls the
    worst of both: a page that looks like a score somebody chose, while the file they
    chose it in was never opened.
    """

    def test_an_empty_path_is_a_file_this_tool_cannot_read_rather_than_no_file(self):
        with self.assertRaises(scoring.ConfigurationRefused) as refused:
            scoring.load("")

        self.assertIn("could not be opened", refused.exception.reason)

    def test_the_command_refuses_an_empty_scoring_path_rather_than_scoring_by_default(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", A_MODULE)
        out = os.path.join(self.scratch, "out")

        with self.assertLogs("module_depth_map.cli", level="WARNING") as logged:
            code = cli.main(
                [
                    "--source", tree.root,
                    "--graph", os.path.join(out, "graph.json"),
                    "--page", os.path.join(out, "page.html"),
                    "--scoring", "",
                ]
            )

        self.assertEqual(4, code)
        self.assertFalse(os.path.exists(out))
        self.assertIn("refused to run", "\n".join(logged.output))

    def test_the_file_may_say_that_a_caller_already_knows_nothing_at_all(self):
        """An arguable position is not a misspelling, and the file has to take it.

        Charging a caller for every type they meet, `String` and `int` included, is a
        judgement somebody can hold and defend. An empty list is refused everywhere a
        rule is matched on names, because there it could only ever be a condition that
        never fires; here it fires on everything.
        """
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", A_MODULE)
        cost = dict(self.as_committed["interfaceCost"], typesEveryCallerAlreadyKnows=[])

        modules = self.modules(graph.java_root(tree.root), self.rules(interfaceCost=cost))

        self.assertEqual(
            [{"name": "Receipt", "mustBeLearned": True}, {"name": "long", "mustBeLearned": True}],
            modules["Till"]["interface"]["typesCrossingTheSeam"],
        )
        self.assertEqual(1 + 1 + 2 + 2, modules["Till"]["interface"]["cost"])

    def test_a_condition_a_rule_can_be_written_with_carries_its_own_two_halves(self):
        """How it is checked and how the value is read, in one entry apiece.

        The check used to be a chain beside the table: everything not matched on names
        was validated as a list of Java kinds, so a fifth condition added with a reader
        of its own and no entry in that chain would refuse every legal value it was
        given, in a message about kinds. The table above it was made a dictionary to
        close exactly this hazard one step earlier.
        """
        for name, condition in scoring.CONDITIONS.items():
            self.assertTrue(callable(getattr(condition, "met", None)), name)
            self.assertTrue(callable(getattr(condition, "valid", None)), name)


class AnEnumConstantIsNotScoredAsAMethodTest(RulesFromAFileTest):
    """What the invented enum method cost, read through the file that decides scores.

    Enums are excluded by a rule, so a method invented out of a constant only reached a
    bar once that rule was taken out of the file — which is an edit the ticket's own
    criterion invites, and the edit the test above this one makes.
    """

    def test_an_enum_scored_by_a_file_without_the_rule_costs_what_it_offers(self):
        tree = self.tree("fixture")
        tree.java(
            "shop.till", "Kind",
            "public enum Kind {\n"
            '    A("x") { int n() { return 1; } },\n'
            '    B("y");\n'
            "    Kind(String s) {}\n"
            "    public String label() { return null; }\n}",
        )
        without_data_carriers = [
            exclusion
            for exclusion in self.as_committed["exclusions"]
            if exclusion["rule"] != "data carrier"
        ]

        modules = self.modules(
            graph.java_root(tree.root), self.rules(exclusions=without_data_carriers)
        )

        self.assertIsNone(modules["Kind"]["excludedBy"])
        self.assertEqual(
            ["label"], [method["name"] for method in modules["Kind"]["interface"]["methods"]]
        )
        self.assertEqual(1, modules["Kind"]["interface"]["cost"])


class TheFileIsTheOnlyPlaceTheRulesLiveTest(RulesFromAFileTest):
    """Criterion four, asserted mechanically rather than by reading the analyser."""

    def test_the_configuration_sits_beside_the_tool(self):
        self.assertEqual(
            os.path.join(TOOL, "scoring.json"), scoring.DEFAULT_CONFIGURATION
        )
        self.assertTrue(os.path.isfile(scoring.DEFAULT_CONFIGURATION))

    def test_no_rule_this_repository_scores_by_is_named_in_the_analyser(self):
        """A rule name written into the code would be a rule an edit to the file could not move.

        A rule's name inside a string literal is the shape that would matter: the same
        words in a comment explaining why the code is as it is are prose, not a rule.
        """
        named = [exclusion["rule"] for exclusion in self.as_committed["exclusions"]]
        for source in _analyser_source():
            for rule in named:
                for quoted in ('"%s"' % rule, "'%s'" % rule):
                    self.assertNotIn(
                        quoted, source[1], "%s names the rule %r" % (source[0], rule)
                    )

    def test_no_type_the_file_calls_familiar_is_named_in_the_analyser(self):
        """Nor is the list of types every caller already knows, which is a judgement too.

        Every name on the list, short ones included. `int`, `long`, `void`, `List`, `Map`
        and `Set` are the names most likely to be reached for as a literal, so exempting
        them for being short would leave the guard covering only the names nobody would
        have hardcoded anyway.
        """
        familiar = self.as_committed["interfaceCost"]["typesEveryCallerAlreadyKnows"]
        for source in _analyser_source():
            for name in familiar:
                self.assertNotIn(
                    '"%s"' % name, source[1], "%s names the type %r" % (source[0], name)
                )

    def test_a_weight_the_file_leaves_out_is_refused_rather_than_defaulted(self):
        """A default weight would score a module with a number nobody chose.

        The reading it would produce is the worst of both: an output that looks like
        somebody\'s judgement, while the file they wrote their judgement in was ignored.
        So there is nothing to fall back to, and each of the four weights is checked for
        by name — a misspelled one is a refusal rather than a silent zero.
        """
        for name in scoring.WEIGHTS:
            cost = dict(self.as_committed["interfaceCost"])
            cost["weights"] = {
                key: value for key, value in cost["weights"].items() if key != name
            }

            with self.assertRaises(scoring.ConfigurationRefused) as refused:
                scoring.load(self.rules(interfaceCost=cost))

            self.assertIn(name, refused.exception.reason)

    def test_a_weight_the_file_misspells_is_refused_rather_than_ignored(self):
        cost = dict(self.as_committed["interfaceCost"])
        cost["weights"] = dict(cost["weights"], methodd=3)

        with self.assertRaises(scoring.ConfigurationRefused) as refused:
            scoring.load(self.rules(interfaceCost=cost))

        self.assertIn("methodd", refused.exception.reason)

    def test_a_configuration_this_tool_cannot_use_stops_the_run(self):
        """Because a run that quietly fell back to defaults would make every test here a lie."""
        broken = os.path.join(self.scratch, "broken.json")
        with open(broken, "w", encoding="utf-8") as handle:
            handle.write('{"schema": "module-depth-map-scoring/1", "interfaceCost": {}}')

        with self.assertRaises(scoring.ConfigurationRefused) as refused:
            scoring.load(broken)

        self.assertIn("interfaceCost", refused.exception.reason)

    def test_the_command_refuses_to_run_rather_than_score_with_rules_nobody_wrote(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", A_MODULE)
        out = os.path.join(self.scratch, "out")
        missing = os.path.join(self.scratch, "no-such-rules.json")

        with self.assertLogs("module_depth_map.cli", level="WARNING") as logged:
            code = cli.main(
                [
                    "--source", tree.root,
                    "--graph", os.path.join(out, "graph.json"),
                    "--page", os.path.join(out, "page.html"),
                    "--scoring", missing,
                ]
            )

        self.assertEqual(4, code)
        self.assertFalse(os.path.exists(out))
        self.assertIn("refused to run", "\n".join(logged.output))
        self.assertIn("could not be opened", "\n".join(logged.output))

    def test_the_command_applies_the_rules_it_was_pointed_at(self):
        tree = self.tree("fixture")
        tree.java("shop.till", "Till", A_MODULE)
        out = os.path.join(self.scratch, "out")

        code = cli.main(
            [
                "--source", tree.root,
                "--graph", os.path.join(out, "graph.json"),
                "--page", os.path.join(out, "page.html"),
                "--scoring", self.weights(method=10),
            ]
        )

        self.assertEqual(0, code)
        with open(os.path.join(out, "graph.json"), "rb") as handle:
            written = json.loads(handle.read().decode("utf-8"))
        self.assertEqual(10, written["scoring"]["weights"]["method"])
        self.assertEqual(13, written["modules"][0]["interface"]["cost"])


def _analyser_source():
    """Every file the tool is made of, as (name, text).

    `scoring.py` included, and it is the one that matters: it is the file that reads the
    rules, so it is where a rule's name or a familiar type would most plausibly be
    written as a shortcut. The names of the *fields* rules are written in do appear
    there, and have to; what must appear nowhere is a rule's own name, or one of the
    judgements the file makes.
    """
    return [
        (os.path.basename(module.__file__), _text(module.__file__))
        for module in (graph, javasource, page, cli, scoring)
    ]


def _text(path):
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()
