"""The checked-in page is the page this source produces, not the page it produced once.

Two fresh runs agreeing with each other says nothing about the files a reader actually
opens. Those are committed, so they go stale the moment a Java class lands and nobody
reruns the tool — and a stale page is worse than no page, because it is trusted. This is
the only test that fails when the outputs in `docs/` no longer describe the source.
"""

import json
import os

from ... import cli
from ..support.sourcetrees import (
    BACKEND_SOURCE,
    FRONTEND_SOURCE,
    REPOSITORY,
    SourceTreeTest,
    bytes_of,
)

COMMITTED_GRAPH = os.path.join(REPOSITORY, "docs", "module-depth-map.json")
COMMITTED_PAGE = os.path.join(REPOSITORY, "docs", "module-depth-map.html")


class TheCommittedOutputsAreWhatAFreshRunWritesTest(SourceTreeTest):

    def setUp(self):
        super().setUp()
        self.graph_path = os.path.join(self.scratch, "fresh", "module-depth-map.json")
        self.page_path = os.path.join(self.scratch, "fresh", "module-depth-map.html")
        # Both halves of the application, because the committed page is a page about
        # both: a run given one of them would be compared against a document holding the
        # other and would fail for a reason that is nothing to do with the source.
        exit_code = cli.main(
            ["--source", BACKEND_SOURCE, "--source", FRONTEND_SOURCE,
             "--graph", self.graph_path, "--page", self.page_path,
             "--snapshot-date", self.the_day_the_committed_outputs_say_they_are_of(),
             "--log-level", "ERROR"]
        )
        self.assertEqual(0, exit_code)

    def the_day_the_committed_outputs_say_they_are_of(self):
        """The snapshot date the committed graph carries, handed back to the fresh run.

        The date is the one value in these files that the source cannot produce: it is
        whatever whoever last ran the tool said the day was, and no reading of this
        repository can rediscover it. So the fresh run is given the committed one, and
        what this test compares is everything else — which is exactly its subject.
        Whoever regenerates the outputs on a new day changes the date in both files and
        this test follows them; what it will not let past is a page describing source
        that has moved on.

        What it cannot catch is a regeneration handed the date it found here — the fresh
        run and the committed file then agree about a day neither of them was. That is
        why the failure messages below name the day to type rather than a day, and why
        `TheDocumentedWayToRegenerateDoesNotAge` keeps one out of the two lines a
        maintainer copies the command from.
        """
        with open(COMMITTED_GRAPH, encoding="utf-8") as handle:
            return json.load(handle)["snapshot"]["date"]

    def test_the_committed_graph_document_is_byte_identical_to_a_fresh_run(self):
        self.assertEqual(
            bytes_of(COMMITTED_GRAPH),
            bytes_of(self.graph_path),
            "docs/module-depth-map.json is out of date: run python3 "
            "scripts/module-depth-map.py --snapshot-date <the day you are dating it> "
            "\u2014 the day you are regenerating it on, not the day the file already "
            "carries",
        )

    def test_the_committed_page_is_byte_identical_to_a_fresh_run(self):
        self.assertEqual(
            bytes_of(COMMITTED_PAGE),
            bytes_of(self.page_path),
            "docs/module-depth-map.html is out of date: run python3 "
            "scripts/module-depth-map.py --snapshot-date <the day you are dating it> "
            "\u2014 the day you are regenerating it on, not the day the file already "
            "carries",
        )
