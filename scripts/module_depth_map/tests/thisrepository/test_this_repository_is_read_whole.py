"""Properties that must hold when the tool is run over this repository's own source.

Deliberately not specific numbers: those would fail every time a feature lands, and the
thing worth asserting is that nothing was quietly dropped.

Both source roots, because the page draws both halves of this application and this is the
suite whose job is that nothing in it goes missing. Pointed at the backend alone — which
it was, for as long as there was only one — a frontend file that silently stopped parsing
passed it.
"""

import os

from ... import graph, languages, scoring
from ..support.sourcetrees import (
    A_SNAPSHOT,
    BACKEND_SOURCE,
    FRONTEND_SOURCE,
    SourceTreeTest,
)


def this_repository():
    """The graph of both of this repository's source roots, read with the shipped rules."""
    return graph.build(
        [graph.source_root(BACKEND_SOURCE), graph.source_root(FRONTEND_SOURCE)],
        scoring.load(), A_SNAPSHOT,
    )


def source_files_on_disk():
    """Every file under either root a language here reads, as (root label, path under it).

    Named by root as well as by path because two roots can hold the same relative path,
    and a set of bare paths would then quietly hold one entry for two files.
    """
    found = set()
    for label, whole in (
        ("backend/src/main/java", BACKEND_SOURCE),
        ("frontend/src", FRONTEND_SOURCE),
    ):
        for directory, _, names in os.walk(whole):
            for name in names:
                if languages.of(name) is None:
                    continue
                path = os.path.relpath(os.path.join(directory, name), whole)
                found.add((label, path.replace(os.sep, "/")))
    return found


class ThisRepositoryIsReadWholeTest(SourceTreeTest):

    def setUp(self):
        super().setUp()
        self.document = this_repository()

    def test_both_halves_of_this_application_are_on_the_one_page(self):
        """The picture is of the application rather than of the half of it written in Java."""
        self.assertEqual(
            ["backend/src/main/java", "frontend/src"], self.document["source"]["roots"]
        )
        self.assertEqual(["java", "typescript"], self.document["source"]["languages"])
        for language in ("java", "typescript"):
            self.assertTrue(
                any(module["language"] == language for module in self.document["modules"]),
                language,
            )

    def test_every_source_file_is_either_read_or_reported_as_unparseable(self):
        """Nothing under either source root goes missing without the document saying so.

        Three answers, not two. A file the tool reads and finds no module in is the third,
        and `package-info.java` is the one Java defines for it: it appears under no module
        and in no failure, and asking for two answers made adding one red this suite for a
        file the README names as read rather than reported. The set of such files is each
        parser's own, so this test cannot drift from what the tool actually skips.
        """
        on_disk = source_files_on_disk()
        declaring_nothing = {
            (root, path) for root, path in on_disk
            if path.rsplit("/", 1)[-1] in {
                name
                for language in languages.ALL
                for name in getattr(language, "DECLARES_NO_TYPE", ())
            }
        }
        reported = {
            (entry["root"], entry["path"]) for entry in self.document["source"]["unparsed"]
        }
        parsed = {(module["root"], module["path"]) for module in self.document["modules"]}

        self.assertEqual(on_disk, parsed | reported | declaring_nothing)
        self.assertEqual(len(on_disk), self.document["source"]["filesSeen"])

    def test_the_whole_of_this_application_can_be_read(self):
        self.assertEqual([], self.document["source"]["unparsed"])
        self.assertEqual(
            self.document["source"]["filesSeen"], self.document["source"]["filesParsed"]
        )

    def test_every_module_belongs_to_a_package_the_graph_names(self):
        named = {package["name"] for package in self.document["packages"]}

        for module in self.document["modules"]:
            self.assertIn(module["package"], named)

    def test_every_module_a_package_names_is_a_module_the_graph_holds(self):
        known = {module["id"] for module in self.document["modules"]}

        for package in self.document["packages"]:
            for module_id in package["moduleIds"]:
                self.assertIn(module_id, known)

    def test_the_modules_a_reader_would_look_for_first_are_there(self):
        by_id = {module["id"]: module for module in self.document["modules"]}

        self.assertIn("io.dataroots.savingstreak.accounts.AccountsService", by_id)
        self.assertIn("io.dataroots.savingstreak.deposits.DepositsService", by_id)
        self.assertEqual(
            "io.dataroots.savingstreak.deposits",
            by_id["io.dataroots.savingstreak.deposits.DepositsService"]["package"],
        )
        self.assertIn("frontend/src/App", by_id)
        self.assertIn("frontend/src/api", by_id)
        self.assertEqual("frontend/src", by_id["frontend/src/api"]["package"])
        self.assertEqual("file", by_id["frontend/src/api"]["kind"])


class EveryModuleInThisRepositoryIsScoredOrExcludedByARuleTest(SourceTreeTest):
    """The property, not the numbers.

    Which module costs what changes every time a feature lands, and asserting any of
    those numbers here would make the suite a tax on writing code. What must hold whatever
    is written is that no module fell between the two: nothing is scored and excluded at
    once, and nothing is neither.
    """

    def setUp(self):
        super().setUp()
        self.document = this_repository()

    def test_every_module_is_either_scored_or_excluded_and_never_both(self):
        for module in self.document["modules"]:
            self.assertEqual(
                module["excludedBy"] is None,
                module["interface"]["cost"] is not None,
                module["id"],
            )

    def test_every_exclusion_names_a_rule_the_configuration_file_holds(self):
        named = {entry["rule"] for entry in self.document["scoring"]["exclusions"]}

        for module in self.document["modules"]:
            if module["excludedBy"]:
                self.assertIn(module["excludedBy"]["rule"], named, module["id"])
                self.assertTrue(module["excludedBy"]["matched"].strip(), module["id"])

    def test_the_counts_the_graph_reports_are_the_modules_it_holds(self):
        scored = [m for m in self.document["modules"] if m["interface"]["cost"] is not None]

        self.assertEqual(len(scored), self.document["scoring"]["modulesScored"])
        self.assertEqual(
            len(self.document["modules"]) - len(scored),
            self.document["scoring"]["modulesNeverScored"],
        )
        self.assertEqual(
            len(self.document["modules"]) - len(scored),
            sum(entry["modulesExcluded"] for entry in self.document["scoring"]["exclusions"]),
        )

    def test_every_cost_is_a_whole_number_the_page_can_draw_a_bar_from(self):
        """One scale for every bar means one kind of number behind all of them.

        The weights come out of the document rather than being written here. Writing one
        of them into this file would make editing `scoring.json` — the whole point of the
        configuration being a file — red the suite, which is the opposite of the property
        the ticket claims.
        """
        weights = self.document["scoring"]["weights"]
        for module in self.document["modules"]:
            cost = module["interface"]["cost"]
            if cost is None:
                continue
            self.assertIsInstance(cost, int)
            self.assertGreaterEqual(cost, 0)
            self.assertEqual(
                cost,
                sum(method["cost"] for method in module["interface"]["methods"])
                + sum(
                    weights["typeToLearn" if t["mustBeLearned"]
                            else "typeEveryCallerAlreadyKnows"]
                    for t in module["interface"]["typesCrossingTheSeam"]
                )
                + weights["refusal"] * len(module["interface"]["refusals"]),
                module["id"],
            )
            # The band is reported apart from the rest of the cost as well as counted
            # into it, and a band that did not come out of the same total would be two
            # numbers for one bar.
            self.assertEqual(
                weights["refusal"] * len(module["interface"]["refusals"]),
                module["interface"]["refusalCost"],
                module["id"],
            )
            self.assertEqual(
                cost,
                module["interface"]["costWithoutRefusals"]
                + module["interface"]["refusalCost"],
                module["id"],
            )

    def test_every_line_in_every_fan_points_at_a_module_this_graph_holds(self):
        """A fan a reader cannot follow is worse than no fan.

        The transaction is the one thing reached that is not a module, and it says so by
        carrying no module id at all rather than by carrying one nothing answers to.
        """
        held = {module["id"] for module in self.document["modules"]}

        for module in self.document["modules"]:
            for entry in module["reach"]["reaches"]:
                if entry["kind"] == "transaction":
                    self.assertIsNone(entry["moduleId"], module["id"])
                    continue
                self.assertIn(entry["moduleId"], held, module["id"])
                self.assertTrue(entry["matched"].strip(), module["id"])

    def test_every_reach_is_a_whole_number_the_page_can_draw_a_fan_from(self):
        furthest = self.document["scoring"]["widestReach"]

        for module in self.document["modules"]:
            reach = module["reach"]
            self.assertEqual(len(reach["reaches"]), reach["count"], module["id"])
            self.assertLessEqual(reach["count"], furthest, module["id"])
            self.assertEqual(
                len({entry["moduleId"] for entry in reach["reaches"]}),
                len(reach["reaches"]),
                module["id"],
            )
        self.assertEqual(
            furthest, max(module["reach"]["count"] for module in self.document["modules"])
        )

    def test_every_depth_is_the_two_numbers_it_was_taken_from(self):
        """Leverage is checkable by hand, on every card, or it is a ranking nobody can argue with."""
        for module in self.document["modules"]:
            depth = module["depth"]
            self.assertEqual(module["reach"]["count"], depth["reach"], module["id"])
            self.assertEqual(module["interface"]["cost"], depth["interfaceCost"], module["id"])
            if not depth["interfaceCost"]:
                self.assertIsNone(depth["leverage"], module["id"])
            else:
                self.assertEqual(
                    round(depth["reach"] / depth["interfaceCost"], 2),
                    depth["leverage"],
                    module["id"],
                )

    def test_the_three_kinds_of_thing_a_module_can_reach_are_all_present_here(self):
        """A rule for what is reached that matched nothing would be one nobody could check."""
        found = {
            entry["kind"]
            for module in self.document["modules"]
            for entry in module["reach"]["reaches"]
        }

        self.assertEqual({"module", "adapter", "record", "transaction"}, found)

    def test_the_three_kinds_of_thing_the_rules_name_are_all_present_to_exclude(self):
        """A rule that matches nothing here would be a rule nobody could have checked."""
        for entry in self.document["scoring"]["exclusions"]:
            self.assertGreater(entry["modulesExcluded"], 0, entry["rule"])

    def test_the_module_the_specification_predicted_would_be_dear_is_dear(self):
        """The tool can be checked against a prediction rather than merely admired.

        The specification named `AccountsService` from reading, before the tool existed:
        many methods over a small implementation, against the module it calls deep. The
        comparison is what was predicted and the comparison is what is asserted — a
        method count written in here would red the suite the day somebody adds a method,
        which is a tax on writing code rather than a property of the tool.
        """
        by_id = {module["id"]: module for module in self.document["modules"]}
        wide = by_id["io.dataroots.savingstreak.accounts.AccountsService"]["interface"]
        deep = by_id["io.dataroots.savingstreak.deposits.DepositsService"]["interface"]

        self.assertGreater(len(wide["methods"]), len(deep["methods"]))
        self.assertGreater(wide["cost"], deep["cost"])

    def test_the_module_the_specification_predicted_would_be_shallow_reads_as_shallow(self):
        """The same prediction again, now that there is a numerator to read it against.

        `AccountsService` asks more of a caller than `DepositsService` and coordinates
        less on their behalf, which is the whole of what this page means by shallow. Both
        halves are compared rather than pinned to a number, so a feature landing in either
        module moves the figures without reddening the suite.
        """
        by_id = {module["id"]: module for module in self.document["modules"]}
        wide = by_id["io.dataroots.savingstreak.accounts.AccountsService"]
        deep = by_id["io.dataroots.savingstreak.deposits.DepositsService"]

        self.assertGreater(deep["reach"]["count"], wide["reach"]["count"])
        self.assertGreater(deep["depth"]["leverage"], wide["depth"]["leverage"])


class TheDeletionTestHoldsOnThisRepositoryTest(SourceTreeTest):
    """What must be true of every verdict here, and the one verdict the specification predicted.

    The properties first, because they are what has to hold whatever anybody writes next:
    a verdict on every module that was scored, none on any module that was not, and every
    caller behind a count pointing at a module this graph holds. The prediction after,
    because a mechanical test that cannot be checked against something somebody said
    before it existed is one nobody has any reason to believe.
    """

    def setUp(self):
        super().setUp()
        self.document = this_repository()
        self.by_id = {module["id"]: module for module in self.document["modules"]}
        self.rule = self.document["scoring"]["deletionTest"]

    def test_every_module_records_how_many_others_call_it(self):
        for module in self.document["modules"]:
            callers = module["callers"]
            self.assertEqual(len(callers["moduleIds"]), callers["count"], module["id"])
            self.assertEqual(sorted(set(callers["moduleIds"])), callers["moduleIds"], module["id"])
            self.assertNotIn(module["id"], callers["moduleIds"], module["id"])
            for caller in callers["moduleIds"]:
                self.assertIn(caller, self.by_id, module["id"])

    def test_a_caller_is_a_module_with_a_line_in_its_fan_to_this_one(self):
        """The count read the other way round, so it can be checked against the fans it came from."""
        for module in self.document["modules"]:
            reaching = sorted(
                other["id"]
                for other in self.document["modules"]
                for entry in other["reach"]["reaches"]
                if entry["moduleId"] == module["id"]
            )
            self.assertEqual(reaching, module["callers"]["moduleIds"], module["id"])

    def test_every_scored_module_carries_a_verdict_and_no_other_module_does(self):
        for module in self.document["modules"]:
            test = module["deletionTest"]
            self.assertEqual(
                module["excludedBy"] is None, test["verdict"] is not None, module["id"]
            )
            self.assertEqual(
                test["verdict"] is None, test["because"] is None, module["id"]
            )

    def test_every_verdict_states_the_counts_it_was_read_off(self):
        for module in self.document["modules"]:
            test = module["deletionTest"]
            self.assertEqual(module["reach"]["count"], test["reach"], module["id"])
            self.assertEqual(module["callers"]["count"], test["callers"], module["id"])
            self.assertEqual(
                len(module["interface"]["methods"]), test["methods"], module["id"]
            )

    def test_every_verdict_is_one_the_configuration_file_names(self):
        named = {
            self.rule[answer]["verdict"]: self.rule[answer]["because"]
            for answer in ("passThrough", "earnsItsKeep", "noFinding")
        }

        for module in self.document["modules"]:
            test = module["deletionTest"]
            if test["verdict"] is None:
                continue
            self.assertIn(test["verdict"], named, module["id"])
            self.assertEqual(named[test["verdict"]], test["because"], module["id"])

    def test_the_verdict_counts_the_graph_reports_are_the_verdicts_it_holds(self):
        for entry in self.rule["modulesByVerdict"]:
            self.assertEqual(
                sum(
                    1
                    for module in self.document["modules"]
                    if module["deletionTest"]["verdict"] == entry["verdict"]
                ),
                entry["modules"],
                entry["verdict"],
            )

    def test_the_module_the_specification_predicted_is_named_a_pass_through(self):
        """`AccountsService` was called shallow from reading, before this tool existed.

        Nothing tells the tool about it: the verdict falls out of how much that module
        coordinates, how many methods it presents, and how many modules go through it —
        all three of which are read off the source. The verdict itself is taken from the
        configuration rather than written here, so rewording it in the file does not red
        this suite.
        """
        accounts = self.by_id["io.dataroots.savingstreak.accounts.AccountsService"]

        self.assertEqual(
            self.rule["passThrough"]["verdict"], accounts["deletionTest"]["verdict"]
        )
        self.assertGreaterEqual(
            accounts["deletionTest"]["callers"], self.rule["passThrough"]["callersAtLeast"]
        )
        self.assertLessEqual(
            accounts["deletionTest"]["reach"], accounts["deletionTest"]["methods"]
        )

    def test_the_module_it_calls_earns_its_keep_by_the_same_rule(self):
        """The comparison is the finding: the same rule, the two modules, opposite answers."""
        deposits = self.by_id["io.dataroots.savingstreak.deposits.DepositsService"]

        self.assertEqual(
            self.rule["earnsItsKeep"]["verdict"], deposits["deletionTest"]["verdict"]
        )
        self.assertGreater(
            deposits["deletionTest"]["reach"], deposits["deletionTest"]["methods"]
        )
