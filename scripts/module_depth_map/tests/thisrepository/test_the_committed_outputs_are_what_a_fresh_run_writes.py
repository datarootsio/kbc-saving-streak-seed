"""The checked-in page is the page this source produces, not the page it produced once.

Two fresh runs agreeing with each other says nothing about the files a reader actually
opens. Those are committed, so they go stale the moment a Java class lands and nobody
reruns the tool — and a stale page is worse than no page, because it is trusted. This is
the only test that fails when the outputs in `docs/` no longer describe the source.
"""

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
             "--graph", self.graph_path, "--page", self.page_path, "--log-level", "ERROR"]
        )
        self.assertEqual(0, exit_code)

    def test_the_committed_graph_document_is_byte_identical_to_a_fresh_run(self):
        self.assertEqual(
            bytes_of(COMMITTED_GRAPH),
            bytes_of(self.graph_path),
            "docs/module-depth-map.json is out of date: run python3 scripts/module-depth-map.py",
        )

    def test_the_committed_page_is_byte_identical_to_a_fresh_run(self):
        self.assertEqual(
            bytes_of(COMMITTED_PAGE),
            bytes_of(self.page_path),
            "docs/module-depth-map.html is out of date: run python3 scripts/module-depth-map.py",
        )
