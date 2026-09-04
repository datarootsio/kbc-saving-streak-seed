"""Properties that must hold when the tool is run over this repository's own backend source.

Deliberately not specific numbers: those would fail every time a feature lands, and the
thing worth asserting is that nothing was quietly dropped.
"""

import os

from ... import graph, javasource, scoring
from ..support.sourcetrees import BACKEND_SOURCE, SourceTreeTest


class ThisRepositoryIsReadWholeTest(SourceTreeTest):

    def setUp(self):
        super().setUp()
        self.document = graph.build([graph.java_root(BACKEND_SOURCE)], scoring.load())

    def test_every_source_file_is_either_read_or_reported_as_unparseable(self):
        """Nothing under the source root goes missing without the document saying so.

        Three answers, not two. A file the tool reads and finds no module in is the third,
        and `package-info.java` is the one Java defines for it: it appears under no module
        and in no failure, and asking for two answers made adding one red this suite for a
        file the README names as read rather than reported. The set of such files is the
        parser's own, so this test cannot drift from what the tool actually skips.
        """
        on_disk = set()
        for directory, _, names in os.walk(BACKEND_SOURCE):
            for name in names:
                if name.endswith(".java"):
                    whole = os.path.join(directory, name)
                    on_disk.add(os.path.relpath(whole, BACKEND_SOURCE).replace(os.sep, "/"))

        reported = {entry["path"] for entry in self.document["source"]["unparsed"]}
        parsed = {module["path"] for module in self.document["modules"]}
        declaring_nothing = {
            path for path in on_disk
            if path.rsplit("/", 1)[-1] in javasource.DECLARES_NO_TYPE
        }

        self.assertEqual(on_disk, parsed | reported | declaring_nothing)
        self.assertEqual(len(on_disk), self.document["source"]["filesSeen"])

    def test_the_whole_of_this_backend_can_be_read(self):
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


class EveryModuleInThisRepositoryIsScoredOrExcludedByARuleTest(SourceTreeTest):
    """The property, not the numbers.

    Which module costs what changes every time a feature lands, and asserting any of
    those numbers here would make the suite a tax on writing code. What must hold whatever
    is written is that no module fell between the two: nothing is scored and excluded at
    once, and nothing is neither.
    """

    def setUp(self):
        super().setUp()
        self.document = graph.build([graph.java_root(BACKEND_SOURCE)], scoring.load())

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
                ),
                module["id"],
            )

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
