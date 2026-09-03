"""Properties that must hold when the tool is run over this repository's own backend source.

Deliberately not specific numbers: those would fail every time a feature lands, and the
thing worth asserting is that nothing was quietly dropped.
"""

import os

from ... import graph
from ..support.sourcetrees import BACKEND_SOURCE, SourceTreeTest


class ThisRepositoryIsReadWholeTest(SourceTreeTest):

    def setUp(self):
        super().setUp()
        self.document = graph.build([graph.java_root(BACKEND_SOURCE)])

    def test_every_source_file_is_either_parsed_or_reported_as_unparseable(self):
        on_disk = set()
        for directory, _, names in os.walk(BACKEND_SOURCE):
            for name in names:
                if name.endswith(".java"):
                    whole = os.path.join(directory, name)
                    on_disk.add(os.path.relpath(whole, BACKEND_SOURCE).replace(os.sep, "/"))

        reported = {entry["path"] for entry in self.document["source"]["unparsed"]}
        parsed = {module["path"] for module in self.document["modules"]}

        self.assertEqual(on_disk, parsed | reported)
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
