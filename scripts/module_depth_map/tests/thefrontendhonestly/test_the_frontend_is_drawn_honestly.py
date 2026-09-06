"""The frontend is on the page, measured by the same rules, and its result is not smoothed.

The property this package establishes is one measure over the whole application. So every
fixture here is written twice where it can be — once in Java and once in TypeScript, the
same shape both times — and asserts that the two come out at the same numbers. A test that
only checked the TypeScript side would pass just as happily if the frontend were being
scored by a second set of rules nobody could see.

The other half is the finding this page exists for, and it is the reason the frontend is
drawn at all: a file can be enormous and present almost nothing, and under a measure of
leverage that is a narrow bar over a narrow fan rather than a deep module. The padding
fixtures below are the ones that would catch a line count creeping back into the measure —
the same module with two hundred more lines of implementation has to come out at exactly
the numbers it had before.
"""

import json
import logging
import os
import re

from ... import cli, graph, javasource, languages, page, scoring, typescriptsource
from ..support.sourcetrees import FRONTEND_SOURCE, SourceTreeTest, bytes_of

# The same module written in both languages: one method taking one parameter of a type the
# configuration does not call familiar, handing back another, and coordinating one
# collaborator through a call. Everything the two readings have to agree about is in here.
A_JAVA_MODULE = """import shop.till.Prices;

public class Till {

    private final Prices prices;

    Till(Prices prices) {
        this.prices = prices;
    }

    public Receipt ring(Basket basket) {
        prices.of(basket);
        return null;
    }
}"""

A_TYPESCRIPT_MODULE = """import { of } from './prices'
import type { Basket, Receipt } from './baskets'

export function ring(basket: Basket): Receipt {
  return of(basket)
}"""

# The same TypeScript module with two hundred lines of implementation added and not one
# more thing exported or coordinated. Under a measure of implementation lines over
# interface lines this is the deepest module in the tree.
A_TYPESCRIPT_MODULE_PADDED = """import { of } from './prices'
import type { Basket, Receipt } from './baskets'

%s

export function ring(basket: Basket): Receipt {
  return of(basket)
}""" % "\n".join(
    "function helper%d(): number {\n  return %d\n}" % (each, each) for each in range(50)
)


class ATypeScriptFileIsAModuleTest(SourceTreeTest):
    """The grain, and the fact that the card says which grain it is.

    A file rather than a class, because that is what an import names: whoever writes
    `import { ring } from './till'` gets whatever the file exports, so the file is the
    thing with an interface and an implementation. Its id is the path it sits at, which is
    the same string the import resolves to — so a fan line can be checked against an
    import statement rather than against this tool's opinion of one.
    """

    def setUp(self):
        super().setUp()
        tree = self.tree("web")
        tree.typescript("", "till.ts", A_TYPESCRIPT_MODULE)
        tree.typescript("", "prices.ts", "export function of(basket: unknown): number {\n  return 0\n}")
        tree.typescript("panels", "card.tsx",
                        "export default function Card() {\n  return <p>hello</p>\n}")
        self.document = graph.build([graph.source_root(tree.root)], scoring.load())
        self.by_id = {module["id"]: module for module in self.document["modules"]}

    def test_each_file_is_one_module_named_by_the_path_it_sits_at(self):
        self.assertEqual(
            ["web/panels/card", "web/prices", "web/till"], sorted(self.by_id)
        )

    def test_a_module_says_it_was_read_at_file_grain_and_which_language_read_it(self):
        till = self.by_id["web/till"]

        self.assertEqual("file", till["kind"])
        self.assertEqual("typescript", till["language"])
        self.assertEqual("till.ts", till["path"])

    def test_the_directory_a_file_sits_in_is_the_package_it_is_grouped_under(self):
        named = {package["name"]: package["moduleIds"] for package in self.document["packages"]}

        self.assertEqual(["web/prices", "web/till"], sorted(named["web"]))
        self.assertEqual(["web/panels/card"], named["web/panels"])

    def test_the_document_says_which_languages_it_was_read_in(self):
        self.assertEqual(["typescript"], self.document["source"]["languages"])

    def test_one_directory_holding_both_languages_is_read_as_both(self):
        """Which language a file is read with is its own ending, and nothing on a command line.

        The alternative is a flag per language, and then a directory holding both is a
        question somebody has to answer before the tool will read it — for a repository
        where the two live side by side, which plenty do.
        """
        tree = self.tree("mixed")
        tree.java("shop", "Till", "public class Till {\n    public void ring() {}\n}")
        tree.typescript("", "till.ts", "export function ring(): void {}")

        document = graph.build([graph.source_root(tree.root)], scoring.load())

        self.assertEqual(["java", "typescript"], document["source"]["languages"])
        self.assertEqual(
            {"shop.Till": "java", "mixed/till": "typescript"},
            {module["id"]: module["language"] for module in document["modules"]},
        )


class TheSameRulesMeasureBothHalvesTest(SourceTreeTest):
    """One measure over the whole application, asserted by writing one shape in both languages.

    The numbers are spelled out rather than merely compared, because "the two agree" would
    hold just as well if both were wrong. Each is read off the weights in the committed
    configuration: a method, its one parameter, and the two types crossing the seam.
    """

    def setUp(self):
        super().setUp()
        self.rules = scoring.load()
        java = self.tree("backend")
        java.java("shop.till", "Till", A_JAVA_MODULE)
        java.java("shop.till", "Prices", "public class Prices {\n    public long of(Basket basket) { return 0; }\n}")
        java.java("shop.till", "Basket", "public class Basket {\n    public long size() { return 0; }\n}")
        java.java("shop.till", "Receipt", "public class Receipt {\n    public long cents() { return 0; }\n}")
        web = self.tree("web")
        web.typescript("", "till.ts", A_TYPESCRIPT_MODULE)
        web.typescript("", "prices.ts", "export function of(basket: unknown): number {\n  return 0\n}")
        web.typescript("", "baskets.ts",
                        "export type Basket = { size: number }\n"
                        "export type Receipt = { cents: number }")
        self.java = self.built(java, "shop.till.Till")
        self.typescript = self.built(web, "web/till")

    def built(self, tree, module_id):
        document = graph.build([graph.source_root(tree.root)], self.rules)
        return {module["id"]: module for module in document["modules"]}[module_id]

    def test_an_interface_costs_the_same_in_both_languages(self):
        weights = self.rules.weights
        expected = (
            weights["method"]
            + weights["parameter"]
            + 2 * weights["typeToLearn"]
        )

        self.assertEqual(expected, self.java["interface"]["cost"])
        self.assertEqual(expected, self.typescript["interface"]["cost"])

    def test_the_method_a_caller_can_reach_is_read_as_the_source_wrote_it(self):
        self.assertEqual(
            [{"name": "ring", "visibility": "public", "parameters": ["Basket"],
              "returns": "Receipt", "documentedRefusals": [],
              "cost": self.rules.weights["method"] + self.rules.weights["parameter"]}],
            self.typescript["interface"]["methods"],
        )

    def test_the_types_crossing_the_seam_are_the_same_two_in_both_languages(self):
        self.assertEqual(
            [{"name": "Basket", "mustBeLearned": True},
             {"name": "Receipt", "mustBeLearned": True}],
            self.typescript["interface"]["typesCrossingTheSeam"],
        )
        self.assertEqual(
            self.java["interface"]["typesCrossingTheSeam"],
            self.typescript["interface"]["typesCrossingTheSeam"],
        )

    def test_a_type_the_configuration_calls_familiar_costs_a_typescript_caller_nothing(self):
        tree = self.tree("familiar")
        tree.typescript("", "till.ts", "export function ring(id: number): string {\n  return ''\n}")

        till = self.built(tree, "familiar/till")

        self.assertEqual(
            [{"name": "number", "mustBeLearned": False},
             {"name": "string", "mustBeLearned": False}],
            till["interface"]["typesCrossingTheSeam"],
        )
        self.assertEqual(
            self.rules.weights["method"] + self.rules.weights["parameter"],
            till["interface"]["cost"],
        )

    def test_reach_and_depth_are_the_same_in_both_languages(self):
        self.assertEqual(1, self.java["reach"]["count"])
        self.assertEqual(1, self.typescript["reach"]["count"])
        self.assertEqual(self.java["depth"], self.typescript["depth"])

    def test_the_deletion_test_is_run_on_a_typescript_module_too(self):
        self.assertEqual(
            self.java["deletionTest"]["verdict"], self.typescript["deletionTest"]["verdict"]
        )
        self.assertIsNotNone(self.typescript["deletionTest"]["verdict"])

    def test_no_exclusion_rule_declines_to_score_a_typescript_file(self):
        """A file is not a record, not a generated repository and not an entry point.

        Which matters because the alternative is the frontend arriving as a box with a
        rule name on it and no numbers: drawn, and saying nothing.
        """
        self.assertIsNone(self.typescript["excludedBy"])
        self.assertIsNotNone(self.typescript["interface"]["cost"])


class WhatAFrontendModuleExportsAndReachesIsReadFromTheSourceTest(SourceTreeTest):
    """Both sides of a TypeScript seam, read from the file and never guessed at."""

    def module(self, name, body, *others):
        tree = self.tree("web")
        tree.typescript("", name, body)
        for each, source in others:
            tree.typescript("", each, source)
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        return {module["id"]: module for module in document["modules"]}

    def test_only_what_the_file_exports_is_on_its_interface(self):
        found = self.module(
            "till.ts",
            "function change(cents: number): number {\n  return cents\n}\n\n"
            "export function ring(cents: number): number {\n  return change(cents)\n}",
        )

        self.assertEqual(
            ["ring"], [method["name"] for method in found["web/till"]["interface"]["methods"]]
        )

    def test_an_exported_arrow_function_is_a_method_a_caller_can_reach(self):
        found = self.module(
            "till.ts", "export const ring = (cents: number): number => cents"
        )

        self.assertEqual(
            [{"name": "ring", "visibility": "public", "parameters": ["number"],
              "returns": "number", "documentedRefusals": [], "cost": 2}],
            found["web/till"]["interface"]["methods"],
        )

    def test_a_default_export_is_read_under_the_name_the_source_gives_it(self):
        found = self.module("page.tsx", "export default function Page() {\n  return null\n}")

        self.assertEqual(
            ["Page"], [method["name"] for method in found["web/page"]["interface"]["methods"]]
        )

    def test_an_export_naming_something_this_file_declares_is_read_as_that_declaration(self):
        """`export default App` at the foot of the file is how a React component is offered.

        Read as nothing, a file whose whole interface is written that way presents no
        method at all — which is exactly the finding this page exists to make, and it
        would then be making it about a reading rather than about the source.
        """
        found = self.module(
            "page.tsx",
            "function Page(title: string) {\n  return title\n}\n\nexport default Page",
        )

        self.assertEqual(
            [{"name": "Page", "visibility": "public", "parameters": ["string"],
              "returns": "(inferred)", "documentedRefusals": [], "cost": 2}],
            found["web/page"]["interface"]["methods"],
        )

    def test_a_name_exported_in_a_list_is_read_as_the_declaration_it_names(self):
        found = self.module(
            "till.ts",
            "function ring(cents: number): number {\n  return cents\n}\n"
            "function hidden(): void {}\n\n"
            "export { ring as rings }",
        )

        self.assertEqual(
            ["rings"],
            [method["name"] for method in found["web/till"]["interface"]["methods"]],
        )

    def test_a_name_exported_out_of_another_file_is_named_nowhere_here(self):
        """Its signature is written in that file, so this one has nothing to price.

        A floor in the direction every other reading here leans, and logged rather than
        dropped in silence.
        """
        found = self.module(
            "index.ts",
            "export { of } from './prices'",
            ("prices.ts", "export function of(): number {\n  return 0\n}"),
        )

        self.assertEqual([], found["web/index"]["interface"]["methods"])

    def test_a_type_the_file_declares_is_named_on_it_rather_than_measured(self):
        found = self.module(
            "till.ts",
            "export type Receipt = { cents: number }\n\n"
            "export function ring(): void {}",
        )

        self.assertEqual(["Receipt"], found["web/till"]["nested"])
        self.assertEqual(
            ["ring"], [method["name"] for method in found["web/till"]["interface"]["methods"]]
        )

    def test_a_module_reaches_the_one_it_calls_an_imported_function_of(self):
        found = self.module(
            "till.ts",
            "import { of } from './prices'\n\n"
            "export function ring(): number {\n  return of()\n}",
            ("prices.ts", "export function of(): number {\n  return 0\n}"),
        )

        self.assertEqual(
            [{"kind": "module", "name": "prices", "moduleId": "web/prices",
              "matched": typescriptsource.IMPORTED_EVIDENCE % "of"}],
            found["web/till"]["reach"]["reaches"],
        )
        self.assertEqual(["web/till"], found["web/prices"]["callers"]["moduleIds"])

    def test_a_module_reaches_the_one_whose_component_it_writes_as_an_element(self):
        """`<Card />` compiles to a call building a `Card`, and is coordination the same way.

        Read no other way, a page that lays out five components coordinates nothing at
        all: React's own spelling for using one is a tag rather than a call.
        """
        found = self.module(
            "page.tsx",
            "import Card from './card'\n\n"
            "export default function Page() {\n  return <Card />\n}",
            ("card.tsx", "export default function Card() {\n  return null\n}"),
        )

        self.assertEqual(
            [{"kind": "module", "name": "card", "moduleId": "web/card",
              "matched": "builds one"}],
            found["web/page"]["reach"]["reaches"],
        )

    def test_a_lowercase_tag_reaches_nothing_because_it_is_not_a_name_in_scope(self):
        found = self.module(
            "page.tsx",
            "import card from './card'\n\n"
            "export default function Page() {\n  return <card />\n}",
            ("card.tsx", "export default function card() {\n  return null\n}"),
        )

        self.assertEqual([], found["web/page"]["reach"]["reaches"])

    def test_a_generic_annotated_with_an_imported_type_builds_nothing(self):
        """`useState<Receipt>(null)` writes the same six characters as `<Receipt ...>`.

        Read as an element it credited every file that annotates a generic with an
        imported type with constructing one — a fan line to a card, under an evidence
        string saying the file built something it never built.
        """
        found = self.module(
            "page.tsx",
            "import { hold } from './receipts'\n"
            "import type { Receipt } from './receipts'\n\n"
            "export default function Page() {\n"
            "  const kept = hold<Receipt>()\n"
            "  return kept\n}",
            ("receipts.ts",
             "export type Receipt = { cents: number }\n"
             "export function hold<T>(): T | null {\n  return null\n}"),
        )

        self.assertEqual(
            [typescriptsource.IMPORTED_EVIDENCE % "hold"],
            [entry["matched"] for entry in found["web/page"]["reach"]["reaches"]],
        )

    def test_a_module_reaches_nothing_for_importing_a_dependency(self):
        """Somebody else's source is not in this graph, so it is not in any fan.

        Which is what stops a module raising its own reach by installing more packages,
        exactly as the Java side cannot raise it by importing more of the JDK.
        """
        found = self.module(
            "till.ts",
            "import { useState } from 'react'\n\n"
            "export function ring(): void {\n  useState()\n}",
        )

        self.assertEqual([], found["web/till"]["reach"]["reaches"])

    def test_a_refusal_the_body_throws_is_on_the_band_and_reported_as_undocumented(self):
        found = self.module(
            "till.ts",
            "export function ring(cents: number): number {\n"
            "  if (cents <= 0) {\n    throw new Error('not an amount')\n  }\n"
            "  return cents\n}",
        )
        till = found["web/till"]

        self.assertEqual(
            [{"name": "Error", "documented": False, "documentedBy": [], "raised": True,
              "checked": True}],
            till["interface"]["refusals"],
        )
        self.assertEqual(
            ["raised but never documented"],
            [finding["finding"] for finding in till["findings"]],
        )

    def test_a_refusal_a_jsdoc_block_promises_is_read_as_documented(self):
        found = self.module(
            "till.ts",
            "/**\n * Rings a basket up.\n *\n * @throws {Refused} when the basket is empty\n */\n"
            "export function ring(cents: number): number {\n"
            "  throw new Refused('empty')\n}\n\n"
            "class Refused extends Error {}",
        )
        till = found["web/till"]

        self.assertEqual(
            [{"name": "Refused", "documented": True, "documentedBy": ["ring"],
              "raised": True, "checked": True}],
            till["interface"]["refusals"],
        )
        self.assertEqual([], till["findings"])


class SizeIsNotDepthTest(SourceTreeTest):
    """The finding the frontend is on the page for: a big module is not a deep one.

    Two hundred more lines of implementation and not one more thing exported or
    coordinated has to leave every number exactly where it was. Under a measure of
    implementation lines over interface lines the padded fixture is the deepest module in
    its tree, which is the measure this tool rejected and the reason it draws a fan.
    """

    def measured(self, body, named="web"):
        tree = self.tree(named)
        tree.typescript("", "till.ts", body)
        tree.typescript("", "prices.ts", "export function of(basket: unknown): number {\n  return 0\n}")
        tree.typescript("", "baskets.ts",
                        "export type Basket = { size: number }\n"
                        "export type Receipt = { cents: number }")
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        return document, {module["id"]: module for module in document["modules"]}[named + "/till"]

    def test_two_hundred_more_lines_of_implementation_move_nothing(self):
        _, plain = self.measured(A_TYPESCRIPT_MODULE, "web")
        _, padded = self.measured(A_TYPESCRIPT_MODULE_PADDED, "padded")

        self.assertGreater(padded["lines"], plain["lines"] + 150)
        self.assertEqual(plain["interface"]["cost"], padded["interface"]["cost"])
        self.assertEqual(plain["reach"]["count"], padded["reach"]["count"])
        self.assertEqual(plain["depth"], padded["depth"])
        self.assertEqual(plain["deletionTest"], padded["deletionTest"])

    def test_the_longest_module_is_not_the_one_with_the_most_leverage(self):
        """The claim in one assertion, on a tree built so that the two come apart.

        The padded module is by far the longest thing in it and coordinates one thing
        behind one export; a four-line module beside it coordinates three.
        """
        tree = self.tree("web")
        tree.typescript("", "till.ts", A_TYPESCRIPT_MODULE_PADDED)
        tree.typescript("", "prices.ts", "export function of(basket: unknown): number {\n  return 0\n}")
        tree.typescript("", "baskets.ts",
                        "export type Basket = { size: number }\n"
                        "export type Receipt = { cents: number }")
        tree.typescript("", "shelf.ts", "export function take(): void {}")
        tree.typescript("", "open.ts",
                        "import { of } from './prices'\n"
                        "import { take } from './shelf'\n"
                        "import Card from './card'\n\n"
                        "export function open(): void {\n"
                        "  of(0)\n  take()\n  Card()\n}")
        tree.typescript("", "card.tsx", "export default function Card() {\n  return null\n}")

        document = graph.build([graph.source_root(tree.root)], scoring.load())
        by_id = {module["id"]: module for module in document["modules"]}
        longest = max(by_id.values(), key=lambda module: module["lines"])

        self.assertEqual("web/till", longest["id"])
        self.assertLess(longest["depth"]["leverage"], by_id["web/open"]["depth"]["leverage"])

    def test_the_document_names_its_longest_module_with_the_numbers_it_was_measured_at(self):
        """So that the page can say what a line count is worth without counting anything.

        Every value on the page's own section about this is read straight off here: a page
        that worked the answer out itself would be a second measurement standing beside
        the document's, with a reader unable to say which they were looking at.
        """
        document, till = self.measured(A_TYPESCRIPT_MODULE_PADDED)

        self.assertEqual(
            {
                "moduleId": "web/till",
                "name": "till",
                "package": "web",
                "language": "typescript",
                "lines": till["lines"],
                "interfaceCost": till["interface"]["cost"],
                "methods": len(till["interface"]["methods"]),
                "reach": till["reach"]["count"],
                "leverage": till["depth"]["leverage"],
            },
            document["scoring"]["largest"],
        )

    def test_the_longest_module_is_settled_by_id_when_two_files_are_the_same_length(self):
        tree = self.tree("web")
        tree.typescript("", "zebra.ts", "export function z(): void {}")
        tree.typescript("", "ant.ts", "export function a(): void {}")

        document = graph.build([graph.source_root(tree.root)], scoring.load())

        self.assertEqual("web/zebra", document["scoring"]["largest"]["moduleId"])


class TypeScriptThatCannotBeReadIsNamedTest(SourceTreeTest):
    """A frontend file the tool cannot read is reported by name, exactly as a Java one is.

    The whole reason this matters is that the alternative is silent: a file read as empty
    presents nothing and coordinates nothing, which is indistinguishable from the real
    finding this page exists to make. So every one of these is a named path with a reason
    on it, and the modules around it are still drawn.
    """

    def read(self, name, body, *others):
        tree = self.tree("web")
        tree.typescript("", name, body)
        tree.typescript("", "prices.ts", "export function of(): number {\n  return 0\n}")
        for each, source in others:
            tree.typescript("", each, source)
        return graph.build([graph.source_root(tree.root)], scoring.load())

    def failure_for(self, name, body):
        document = self.read(name, body)
        self.assertEqual(1, document["source"]["filesUnparsed"], document["source"]["unparsed"])
        entry = document["source"]["unparsed"][0]
        self.assertEqual(name, entry["path"])
        self.assertEqual("web", entry["root"])
        # Everything else in the tree is still on the page: one bad file costs one card.
        self.assertEqual(["web/prices"], [module["id"] for module in document["modules"]])
        return entry["reason"]

    def test_braces_left_open_at_the_end_of_a_file_are_named(self):
        self.assertIn("braces do not balance", self.failure_for(
            "till.ts", "export function ring(): void {\n"
        ))

    def test_a_closing_brace_with_nothing_open_is_named_with_its_line(self):
        reason = self.failure_for("till.ts", "export function ring(): void {}\n}\n")

        self.assertIn("braces do not balance", reason)
        self.assertIn("line 2", reason)

    def test_a_template_literal_that_is_never_closed_is_named(self):
        reason = self.failure_for(
            "till.ts", "export function ring(): string {\n  return `a\n}\n"
        )

        self.assertIn("template literal", reason)
        self.assertIn("line 2", reason)

    def test_a_block_comment_that_is_never_closed_is_named(self):
        reason = self.failure_for("till.ts", "/* open\nexport function ring(): void {}\n")

        self.assertIn("block comment", reason)
        self.assertIn("line 1", reason)

    def test_an_export_this_tool_has_no_reading_of_is_named_rather_than_dropped(self):
        """An export nobody read is an interface quietly cheaper than the source makes it.

        Which is what this page calls deep, so there is nothing here that can be passed
        over: `export` is reserved, and one that opened nothing readable is TypeScript
        this tool has stopped reading.
        """
        reason = self.failure_for(
            "till.ts", "export declare function ring(): void\n"
        )

        self.assertIn("export", reason)
        self.assertIn("line 1", reason)

    def test_a_parameter_with_no_type_written_on_it_is_named_with_its_line(self):
        reason = self.failure_for(
            "till.ts", "export function ring(basket): void {\n}\n"
        )

        self.assertIn("parameter", reason)
        self.assertIn("ring", reason)

    def test_a_return_this_tool_cannot_tell_from_a_body_is_named(self):
        reason = self.failure_for(
            "till.ts", "export function ring() = 3\n"
        )

        self.assertIn("ring", reason)

    def test_the_run_says_out_loud_that_the_page_is_drawn_from_fewer_files(self):
        graph_path = os.path.join(self.scratch, "out", "graph.json")
        page_path = os.path.join(self.scratch, "out", "page.html")
        tree = self.tree("web")
        tree.typescript("", "till.ts", "export function ring(): void {\n")

        with self.assertLogs("module_depth_map", level="WARNING") as logged:
            exit_code = cli.main(
                ["--source", tree.root, "--graph", graph_path, "--page", page_path]
            )

        self.assertEqual(0, exit_code)
        said = "\n".join(logged.output)
        self.assertIn("till.ts", said)
        self.assertIn("braces do not balance", said)
        # Both outputs are still written: a file that would not parse is a card missing
        # from the page, not a run with nothing to show.
        self.assertTrue(bytes_of(graph_path))
        self.assertTrue(bytes_of(page_path))

    def test_a_file_with_an_apostrophe_in_its_prose_is_read_rather_than_failed(self):
        """The rule that makes JSX readable at all, pinned so it cannot quietly go away.

        A quote opens a string only when a partner follows it on the same line, because
        JavaScript strings do not span lines. Without it a single `account's` in a
        paragraph blanked out the rest of the file, and every module after it vanished.
        """
        document = self.read(
            "page.tsx",
            "export default function Page() {\n"
            "  return <p>This account's deposits, and what they've earned</p>\n}",
        )

        self.assertEqual([], document["source"]["unparsed"])
        self.assertEqual(
            ["web/page", "web/prices"],
            sorted(module["id"] for module in document["modules"]),
        )

    def test_a_regular_expression_holding_braces_does_not_unbalance_the_file(self):
        document = self.read(
            "till.ts",
            "export function spaced(iban: string): string {\n"
            "  return iban.replace(/(.{4})/g, '$1 ').trim()\n}",
        )

        self.assertEqual([], document["source"]["unparsed"])
        self.assertEqual(
            ["spaced"],
            [
                method["name"]
                for module in document["modules"]
                if module["id"] == "web/till"
                for method in module["interface"]["methods"]
            ],
        )


class LegalTypeScriptIsNotFailedForBeingWrittenTheUsualWayTest(SourceTreeTest):
    """A file `tsc` compiles has to be a file this reading reads.

    The counterpart of the Java side's `LegalJavaIsNotFailedForBeingWrittenTightly`, and
    it is here because the failure it guards is one step worse than a misread number: a
    file failed by name is a whole module off the page, and every fan line into it with
    it. The rule this repository holds itself to is in the README's parse-failure section
    — an alarm that cries wolf stops being read — so each shape below is one this reading
    refused, or read wrongly, on source with nothing at all the matter with it.

    Every one of them was found by review rather than by this suite, which is why they are
    written down here rather than only fixed. All but two are how this frontend's own
    ~40 components are written today; they were latent only because none of them is
    exported.
    """

    def read(self, name, body, *others):
        tree = self.tree("web")
        tree.typescript("", name, body)
        for each, source in others:
            tree.typescript("", each, source)
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        self.assertEqual([], document["source"]["unparsed"])
        return {module["id"]: module for module in document["modules"]}

    def interface_of(self, name, body):
        module_id = "web/" + name.rsplit(".", 1)[0]
        return self.read(name, body)[module_id]["interface"]

    def test_a_destructured_parameter_is_one_parameter_with_the_type_after_the_pattern(self):
        """How every component in this frontend takes its props, and it failed the file.

        `_first_at_depth_zero` stopped at the `{` that opens the pattern and found neither
        a colon nor an `=`, so the reason given was that a parameter with its type written
        three characters further along had no type written on it. Export any one of
        App.tsx's components — the likeliest edit anybody makes to this repository — and
        all 1545 lines left the graph.
        """
        interface = self.interface_of(
            "banking.tsx",
            "type Props = { customer: string; onSignOut: () => void }\n"
            "\n"
            "export function Banking({ customer, onSignOut }: Props) {\n"
            "  return <div>{customer}</div>\n"
            "}\n",
        )

        self.assertEqual(
            [("Banking", ["Props"])],
            [(method["name"], method["parameters"]) for method in interface["methods"]],
        )
        self.assertEqual(
            [{"name": "Props", "mustBeLearned": True}],
            interface["typesCrossingTheSeam"],
        )

    def test_a_destructured_parameter_with_defaults_inside_the_pattern_is_read_too(self):
        interface = self.interface_of(
            "card.tsx",
            "type Props = { title: string; open?: boolean }\n"
            "\n"
            "export function Card({ title, open = false }: Props): string {\n"
            "  return title\n"
            "}\n",
        )

        self.assertEqual(["Props"], interface["methods"][0]["parameters"])

    def test_a_default_export_written_as_an_arrow_is_a_method_a_caller_can_reach(self):
        """The second commonest React default export, and it failed the file outright.

        `export default function () {}` was already read under the name `default`, which
        is what a caller of the module imports it by. An arrow is the same thing written
        the other way round.
        """
        interface = self.interface_of(
            "app.ts",
            "export default (id: number): number => {\n  return id\n}\n",
        )

        self.assertEqual(
            [("default", ["number"], "number")],
            [
                (method["name"], method["parameters"], method["returns"])
                for method in interface["methods"]
            ],
        )

    def test_a_default_export_of_a_call_is_named_and_skipped_rather_than_failed(self):
        """`export default connect(App)` is legal and is not a signature this tool prices.

        Named and skipped is the answer an exported class already gets: what it costs a
        caller is whatever the expression evaluates to, and nothing here evaluates it.
        Failing the file instead took the module and every fan line into it off the page.
        """
        with self.assertLogs("module_depth_map.typescriptsource", level="DEBUG") as logged:
            interface = self.interface_of(
                "app.tsx",
                "function App(): string {\n  return \'\'\n}\n"
                "export default wrap(App)\n",
            )

        self.assertEqual([], interface["methods"])
        said = "\n".join(logged.output)
        self.assertIn("export not read as a method", said)
        self.assertIn("line=4", said)
        self.assertIn("expression", said)

    def test_a_default_export_of_a_literal_is_named_and_skipped_rather_than_failed(self):
        interface = self.interface_of("answer.ts", "export default 42\n")

        self.assertEqual([], interface["methods"])

    def test_a_default_export_of_an_element_is_named_and_skipped_rather_than_failed(self):
        interface = self.interface_of("spinner.tsx", "export default <div />\n")

        self.assertEqual([], interface["methods"])

    def test_a_const_initialised_with_a_bracket_group_is_not_read_as_a_function(self):
        """`export const total = (1 + 2)` was failed for a parameter called `1 + 2`.

        A reason that is not true of the source is worse than no reading at all: it sends
        whoever reads the alarm looking for a parameter nobody wrote. A bracket group is
        an arrow's parameter list only when an arrow follows it.
        """
        interface = self.interface_of("total.ts", "export const total = (1 + 2)\n")

        self.assertEqual([], interface["methods"])

    def test_a_const_bound_to_an_element_is_not_read_as_a_function_either(self):
        interface = self.interface_of("spinner.tsx", "export const spinner = <div />\n")

        self.assertEqual([], interface["methods"])

    def test_a_documented_refusal_above_a_const_bound_arrow_is_read_as_documented(self):
        """The same shape written two ways has to come out the same, and did not.

        `_documented_before` looks back from the declaration to the JSDoc across nothing
        but words, and an arrow starts on the far side of the `=`. So the const form
        produced `raised but never documented` against a module that had documented its
        refusal — the page's own words: a machine that accused a module which kept its
        word would stop being read.
        """
        promise = "/** @throws Refused when it will not */\n"
        body = (
            "(a: number): number => {\n"
            "  if (a === 0) {\n    throw new Refused()\n  }\n  return a\n}\n"
        )
        modules = self.read(
            "viaconst.ts",
            "export class Refused extends Error {}\n" + promise + "export const viaConst = " + body,
            ("viafunction.ts",
             "export class Refused extends Error {}\n" + promise
             + "export function viaFunction(a: number): number {\n"
             "  if (a === 0) {\n    throw new Refused()\n  }\n  return a\n}\n"),
        )

        for module_id in ("web/viaconst", "web/viafunction"):
            module = modules[module_id]
            self.assertEqual(
                [("Refused", True, True)],
                [
                    (refusal["name"], refusal["documented"], refusal["raised"])
                    for refusal in module["interface"]["refusals"]
                ],
                module_id,
            )
            self.assertEqual([], module["findings"], module_id)

    def test_a_documented_refusal_above_a_const_bound_function_expression_is_read_too(self):
        module = self.read(
            "till.ts",
            "export class Refused extends Error {}\n"
            "/** @throws Refused when it will not */\n"
            "export const ring = function (a: number): number {\n"
            "  throw new Refused()\n}\n",
        )["web/till"]

        self.assertEqual(
            [("Refused", True, True)],
            [
                (refusal["name"], refusal["documented"], refusal["raised"])
                for refusal in module["interface"]["refusals"]
            ],
        )
        self.assertEqual([], module["findings"])

    def test_a_generic_function_charges_a_caller_for_no_type_called_t(self):
        """A type variable is a hole the caller fills, not a type they go and learn.

        The Java side records a method's own `<T>` precisely so the rule can leave it
        out; the TypeScript `Method` was built without them, so the card named a type `T`
        a reader could go looking for and would never find, and the inflated cost was the
        denominator leverage is divided by.
        """
        interface = self.interface_of(
            "first.ts", "export function first<T>(items: T[]): T {\n  return items[0]\n}\n"
        )
        rules = scoring.load()

        self.assertEqual([], interface["typesCrossingTheSeam"])
        self.assertEqual(
            rules.weights["method"] + rules.weights["parameter"], interface["cost"]
        )

    def test_a_generic_comes_out_at_the_same_numbers_as_the_same_shape_in_java(self):
        """Which is the claim the ticket is about: one measure, applied to both halves.

        A type variable is read out of the declaration in both languages and charged in
        neither, and a bound is charged in neither either — it is written where the
        caller's own type goes rather than across the seam, and the Java side has always
        answered it that way.
        """
        java = self.tree("backend")
        java.java(
            "shop", "A",
            "import java.util.List;\npublic class A {\n"
            "    public <T extends Basket> T first(List<T> of) { return null; }\n}",
        )
        web = self.tree("web2")
        web.typescript(
            "", "a.ts",
            "export function first<T extends Basket>(of: T[]): T {\n  return of[0]\n}",
        )
        rules = scoring.load()

        def built(tree, module_id):
            document = graph.build([graph.source_root(tree.root)], rules)
            self.assertEqual([], document["source"]["unparsed"])
            return {module["id"]: module for module in document["modules"]}[module_id]

        one = built(java, "shop.A")["interface"]
        other = built(web, "web2/a")["interface"]

        self.assertEqual(
            rules.weights["method"] + rules.weights["parameter"], other["cost"]
        )
        self.assertEqual(one["cost"], other["cost"])
        for interface in (one, other):
            self.assertNotIn(
                "T", [type_["name"] for type_ in interface["typesCrossingTheSeam"]]
            )

    def test_a_generic_arrow_charges_a_caller_for_no_type_variable_either(self):
        interface = self.interface_of(
            "first.ts", "export const first = <T,>(items: T[]): T => items[0]\n"
        )

        self.assertEqual([], interface["typesCrossingTheSeam"])

    def test_a_return_type_written_as_an_object_literal_is_read_whole(self):
        """Read as the empty string, a caller was charged nothing for a return type.

        Which is the one direction this reading must never be wrong in: a cheap interface
        over a fan is what this page calls deep, so a return type stepped over on the way
        to the body is a module that looks better designed than its source is.
        """
        interface = self.interface_of(
            "till.ts", "export function f(): { id: number } {\n  return { id: 1 }\n}\n"
        )

        self.assertEqual("{ id: number }", interface["methods"][0]["returns"])
        self.assertEqual(
            [{"name": "number", "mustBeLearned": False}], interface["typesCrossingTheSeam"]
        )

    def test_a_property_of_an_object_return_type_is_not_a_type_to_learn(self):
        interface = self.interface_of(
            "till.ts",
            "export function f(): { basket: Basket } {\n  return { basket: null }\n}\n",
        )

        self.assertEqual(
            [{"name": "Basket", "mustBeLearned": True}], interface["typesCrossingTheSeam"]
        )

    def test_a_signature_with_no_body_is_read_as_one_rather_than_swallowing_the_next(self):
        """An overload ends at a `;`, which is asked about before any brace is looked for.

        What is asserted is the *return type* of the signature that survives. Read without
        that rule it came back as `string export function ring(id: number): string` —
        every word of the next declaration's header charged to a caller as a type to learn
        — so this stays a test of where a signature ends. Only one method comes out
        because the implementation signature under it is not one a caller can call, which
        is `AnOverloadSetIsWhatACallerCanCallTest` below.
        """
        interface = self.interface_of(
            "till.ts",
            "export function ring(id: number): string\n"
            "export function ring(id: number): string {\n  return \'\'\n}\n",
        )

        self.assertEqual(
            [("ring", "string")],
            [(method["name"], method["returns"]) for method in interface["methods"]],
        )

    def test_a_parameter_typed_by_the_binding_above_it_does_not_fail_the_file(self):
        """`const ring: Ring = (a) => a` writes the parameter's type on the binding.

        TypeScript reads it from there and `strict` compiles it, so failing the file is an
        alarm over source with nothing the matter with it. The type is recorded as
        unwritten rather than named, which is what this reading can honestly say: a
        parameter crosses the seam there and no type was read to put on it.
        """
        interface = self.interface_of(
            "till.ts",
            "type Ring = (a: number) => number\nexport const ring: Ring = (a) => a\n",
        )

        self.assertEqual(
            [("ring", ["(inferred)"])],
            [(method["name"], method["parameters"]) for method in interface["methods"]],
        )

    def test_a_return_type_written_as_a_function_is_read_rather_than_declined(self):
        """The `>` of an `=>` closes no bracket, and counted as one it hid the real arrow."""
        interface = self.interface_of(
            "till.ts", "export const ring = (): (() => void) => {\n  return () => {}\n}\n"
        )

        self.assertEqual(
            [("ring", "(() => void)")],
            [(method["name"], method["returns"]) for method in interface["methods"]],
        )

    def test_a_type_reached_through_an_import_type_names_no_type_called_import(self):
        """`import('./api').Customer` is a type, and `import` is not one anybody learns."""
        interface = self.interface_of(
            "till.ts", "export function f(): import('./api').Customer {\n  return null\n}\n"
        )

        self.assertEqual(
            [{"name": "Customer", "mustBeLearned": True}], interface["typesCrossingTheSeam"]
        )

    def test_a_parameter_whose_type_is_a_function_with_a_default_is_read_as_one_type(self):
        """Three `=` are written and only the last of them assigns a default value."""
        interface = self.interface_of(
            "till.ts",
            "export function ring(done: (a: number) => void = () => {}): void {\n}\n",
        )

        self.assertEqual(["(a: number) => void"], interface["methods"][0]["parameters"])


    def test_a_function_return_type_written_as_a_function_type_is_read(self):
        """The `=>` counted as a bracket closing failed the file, and only here.

        The `const`-bound arrow above has been read this way for an attempt; the
        `function` form goes through a different scanner, which counted the `>` of an `=>`
        as a bracket closing, took the depth below zero and reported that nothing closed
        the return type. Every shape below `tsc --strict` compiles without a word, and
        each of them took the whole file — and every fan line into it — off the page.
        """
        written = (
            "() => void",
            "(x: number) => number",
            "(() => void)",
            "Array<() => void>",
            "Promise<(x: number) => number>",
            "[boolean, () => void]",
        )
        tree = self.tree("web")
        for index, each in enumerate(written):
            tree.typescript(
                "", "f%d.ts" % index,
                "export function f(): %s {\n  return null as any\n}\n" % each,
            )
        document = graph.build([graph.source_root(tree.root)], scoring.load())

        self.assertEqual([], document["source"]["unparsed"])
        self.assertEqual(
            list(written),
            [
                module["interface"]["methods"][0]["returns"]
                for module in sorted(document["modules"], key=lambda each: each["id"])
            ],
        )

    def test_an_async_function_returning_a_function_type_is_read_too(self):
        interface = self.interface_of(
            "till.ts",
            "export async function f(): Promise<() => void> {\n  return () => {}\n}\n",
        )

        self.assertEqual("Promise<() => void>", interface["methods"][0]["returns"])

    def test_a_parameter_and_a_return_both_written_as_function_types_are_read(self):
        interface = self.interface_of(
            "till.ts",
            "export function useDebounce(fn: () => void, ms: number): () => void {\n"
            "  return fn\n}\n",
        )

        self.assertEqual(["() => void", "number"], interface["methods"][0]["parameters"])
        self.assertEqual("() => void", interface["methods"][0]["returns"])

    def test_a_return_type_on_the_same_line_as_its_body_is_read_whole(self):
        """Read short, the type carried the front of the body and invented a type to learn.

        No warning was printed for it either: the return came out as
        `(url: string) => Promise<Response> { return`, and `return` was charged to a
        caller as a type they must go and learn — a name a reader can look for and will
        never find, sitting in the cost that leverage is divided by.
        """
        interface = self.interface_of(
            "till.ts",
            "export function makeFetcher(): (url: string) => Promise<Response> "
            "{ return async u => fetch(u) }\n",
        )

        self.assertEqual(
            "(url: string) => Promise<Response>", interface["methods"][0]["returns"]
        )
        self.assertNotIn(
            "return", [type_["name"] for type_ in interface["typesCrossingTheSeam"]]
        )

    def test_two_parameters_are_two_when_the_first_is_a_generic_holding_an_arrow(self):
        """A parameter list that collapsed left an interface cheaper than the source.

        The comma between the two parameters sits after a `>` that closes the generic and
        after the `>` of an `=>` that closes nothing. Counted as one, the depth never came
        back to zero, the comma never split, and one method taking two things was charged
        for taking one.
        """
        interface = self.interface_of(
            "till.ts",
            "export function ring(m: Map<string, () => string>, n: number): void {}\n",
        )

        self.assertEqual(
            ["Map<string, () => string>", "number"],
            interface["methods"][0]["parameters"],
        )

    def test_a_generic_parameter_list_comes_out_at_the_same_numbers_in_both_languages(self):
        """Which is the claim the ticket makes: the Java reader read this and this did not.

        Two parameters, the first a generic holding another type, and everything a caller
        meets on the list each language calls familiar. The numbers have to be the same on
        both sides or "measured by the same rules" is not true of the page.
        """
        java = self.tree("backend")
        java.java(
            "shop", "Till",
            "import java.util.List;\nimport java.util.Map;\npublic class Till {\n"
            "    public void ring(Map<String, List<String>> m, long n) { }\n}",
        )
        web = self.tree("web2")
        web.typescript(
            "", "till.ts",
            "export function ring(m: Map<string, () => string>, n: number): void {}",
        )
        rules = scoring.load()

        def built(tree, module_id):
            document = graph.build([graph.source_root(tree.root)], rules)
            self.assertEqual([], document["source"]["unparsed"])
            return {module["id"]: module for module in document["modules"]}[module_id]

        one = built(java, "shop.Till")["interface"]
        other = built(web, "web2/till")["interface"]

        self.assertEqual(2, len(one["methods"][0]["parameters"]))
        self.assertEqual(2, len(other["methods"][0]["parameters"]))
        self.assertEqual(
            rules.weights["method"] + 2 * rules.weights["parameter"], other["cost"]
        )
        self.assertEqual(one["cost"], other["cost"])

    def test_the_stock_vite_import_ordering_is_read(self):
        """A side-effect import that is not the last one, in a file with no semicolons.

        This is what `npm create vite@latest -- --template react-ts` writes, and it is what
        this repository's own `main.tsx` writes with the one line moved. The import clause
        was allowed to run over newlines — a long one really does — so `import
        './index.css'` swallowed the statement under it and the file was failed for an
        import "naming no module". `main` off the page takes the only fan line into `App`
        with it.
        """
        modules = self.read(
            "main.tsx",
            "import { StrictMode } from 'react'\n"
            "import { createRoot } from 'react-dom/client'\n"
            "import './index.css'\n"
            "import App from './App.tsx'\n"
            "\n"
            "export function go(): void {\n"
            "  createRoot(document.body).render(<StrictMode><App /></StrictMode>)\n"
            "}\n",
            ("App.tsx", "export default function App() {\n  return <p>hi</p>\n}\n"),
        )

        self.assertEqual(
            ["web/App"],
            [reached["moduleId"] for reached in modules["web/main"]["reach"]["reaches"]],
        )

    def test_an_import_clause_written_over_several_lines_is_still_one_import(self):
        """The other half of the same rule: a clause really may run over newlines."""
        modules = self.read(
            "uses.ts",
            "import {\n  one,\n  two as second,\n} from './api'\n"
            "export function load(): number {\n  return one() + second()\n}\n",
            ("api.ts",
             "export function one(): number {\n  return 1\n}\n"
             "export function two(): number {\n  return 2\n}\n"),
        )

        self.assertEqual(
            ["web/api"],
            [reached["moduleId"] for reached in modules["web/uses"]["reach"]["reaches"]],
        )

    def test_an_apostrophe_in_prose_beside_a_real_string_leaves_the_braces_alone(self):
        """The JSX-prose rule one step further along than the README used to describe it.

        A `'` opened a string wherever a matching one followed on the same line, and in
        `<p>Don't miss it {label('key')}</p>` the matching one is the quote that opens a
        real string. Everything between them was blanked, `{` included and `}` not, and
        the file was failed for braces that do not balance — a reason that is not true of
        the source. Nothing JavaScript compiles writes a string against the end of a word,
        so a quote written there is an apostrophe.
        """
        interface = self.interface_of(
            "note.tsx",
            "function label(k: string): string {\n  return k\n}\n"
            "export function Note(): unknown {\n"
            "  return <p>Don't miss it {label('key')}</p>\n"
            "}\n",
        )

        self.assertEqual(["Note"], [method["name"] for method in interface["methods"]])

    def test_prose_full_of_apostrophes_is_read_whichever_side_the_string_is_on(self):
        interface = self.interface_of(
            "note.tsx",
            "const key = 'k'\n"
            "export function Note(): unknown {\n"
            "  return <p>Anke's savings, the 1970's, and it's fine</p>\n"
            "}\n"
            "export function read(): string {\n  return key\n}\n",
        )

        self.assertEqual(
            ["Note", "read"], [method["name"] for method in interface["methods"]]
        )

    def test_a_string_written_against_the_word_before_it_is_still_a_string(self):
        """`from'./api'` and `return'x'` are legal, and the few words that allow it are named."""
        modules = self.read(
            "uses.ts",
            "import { one }from'./api'\n"
            "export function load(): number {\n  return one()\n}\n",
            ("api.ts", "export function one(): number {\n  return 1\n}\n"),
        )

        self.assertEqual(
            ["web/api"],
            [reached["moduleId"] for reached in modules["web/uses"]["reach"]["reaches"]],
        )

    def test_an_exported_abstract_class_is_named_on_the_module_rather_than_failed(self):
        """`abstract` was read as a declaration living somewhere this tool was not pointed at.

        It is not: the body of `export abstract class Shape {}` is right there in the
        file, and a plain `export class Shape {}` is merely named on the module and
        skipped. So the reason printed was untrue of the source, and one abstract class
        anywhere in a file took every line of it off the page.
        """
        with self.assertLogs("module_depth_map.typescriptsource", level=logging.DEBUG) as logged:
            modules = self.read(
                "shape.ts", "export abstract class Shape {\n  abstract area(): number\n}\n"
            )

        self.assertEqual(["Shape"], modules["web/shape"]["nested"])
        self.assertTrue(
            any("export not read as a method" in line and "export=Shape" in line
                for line in logged.output),
            logged.output,
        )

    def test_every_declarator_of_an_exported_const_is_read(self):
        """`export const a = 1, b = () => {}` exports two names and `b` is a function.

        Read only as far as the first declarator, `b` was in `methods`, in `types` and in
        the declined log alike — nowhere at all. An export left out is an interface
        cheaper than the source makes it, and there is nothing to fail on, because the
        source is perfectly legal.
        """
        with self.assertLogs("module_depth_map.typescriptsource", level=logging.DEBUG) as logged:
            interface = self.interface_of("till.ts", "export const a = 1, b = () => {}\n")

        self.assertEqual(["b"], [method["name"] for method in interface["methods"]])
        self.assertTrue(
            any("export not read as a method" in line and "export=a" in line
                for line in logged.output),
            logged.output,
        )

    def test_a_declarator_after_one_with_a_body_of_its_own_is_read(self):
        interface = self.interface_of(
            "till.ts",
            "export const ring = (a: number): number => {\n  return a\n}, "
            "of = (b: number): number => b\n",
        )

        self.assertEqual(
            ["of", "ring"], sorted(method["name"] for method in interface["methods"])
        )

    def test_a_declarator_list_stops_at_the_declaration_after_it(self):
        """With no semicolon anywhere, a word that can only open a statement ends the list."""
        interface = self.interface_of(
            "till.ts",
            "export const a = 1, b = (): number => 2\n"
            "export const c = 3, d = (): number => 4\n",
        )

        self.assertEqual(
            ["b", "d"], sorted(method["name"] for method in interface["methods"])
        )

    def test_a_type_predicate_names_no_type_after_the_parameter_it_is_about(self):
        """`asserts x is number` writes a parameter's name where a type is read from.

        The same fault as a return type read short: a name on the card that a reader can
        go looking for and will never find, charged into the cost leverage is divided by.
        """
        interface = self.interface_of(
            "till.ts",
            "export function isCustomer(each: unknown): each is { id: number } {\n"
            "  return typeof each === 'object'\n}\n",
        )

        self.assertNotIn(
            "each", [type_["name"] for type_ in interface["typesCrossingTheSeam"]]
        )

    def test_a_function_type_written_as_a_parameter_charges_no_type_variable(self):
        """A `<T>` inside a written type is a hole the caller fills, exactly as a method's is."""
        interface = self.interface_of(
            "till.ts",
            "export function pick(of: <T>(items: T[]) => T, xs: string[]): string {\n"
            "  return of(xs)\n}\n",
        )

        self.assertNotIn(
            "T", [type_["name"] for type_ in interface["typesCrossingTheSeam"]]
        )

    def test_a_self_closing_tag_with_a_prop_expression_inside_a_map_is_read(self):
        """The single most common line in React, and this reading refused the whole file.

        `_AFTER_WHICH_A_REGEX_CAN_START` held `}`, so the brace closing `key={i}` let the
        `/` of the ` />` after it open a regular expression; the scan ran to the next
        slash on the line — the one in `</ul>` — and blanked the `}` that was going to
        close `{items.map(`. The file was then failed for braces that do not balance,
        which is not true of it: `tsc --noEmit --strict --jsx react-jsx` compiles this
        without a word. A whole module off the page, and every fan line into it.

        `<div a={x} /></div>` survived by luck, because of where the next slash happened
        to land, so the fixture has to be the `.map()` shape specifically.
        """
        interface = self.interface_of(
            "list.tsx",
            "export function List(items: string[]): JSX.Element {\n"
            "  return <ul>{items.map(i => <li key={i} />)}</ul>\n}\n",
        )

        self.assertEqual(
            [("List", ["string[]"])],
            [(method["name"], method["parameters"]) for method in interface["methods"]],
        )

    def test_a_self_closing_tag_followed_by_a_conditional_sibling_is_read(self):
        """The same cause one step along: the brace with nothing open was the report."""
        interface = self.interface_of(
            "note.tsx",
            "export function Note(x: boolean): JSX.Element {\n"
            "  return <><div className={x ? 'a' : 'b'} /> {x ? <Bar /> : null}</>\n}\n",
        )

        self.assertEqual(
            ["Note"], [method["name"] for method in interface["methods"]]
        )

    def test_a_slash_written_between_two_expression_containers_is_read(self):
        """`<span>{done} / {total}</span>`, which is how a progress line is written.

        This one costs no file: the braces still balance, because everything between the
        two slashes is blanked whole. What it costs is the code in between — here a call
        to something the file imports, and with it the only fan line the module has, gone
        with no line in any log saying so.
        """
        modules = self.read(
            "bar.tsx",
            "import { asMoney } from './money'\n"
            "export function Bar(done: number, total: number): JSX.Element {\n"
            "  return <span>{done} / {asMoney(total)}</span>\n}\n",
            ("money.ts", "export function asMoney(cents: number): string {\n"
                         "  return String(cents)\n}"),
        )

        self.assertEqual(
            ["web/money"],
            [each["moduleId"] for each in modules["web/bar"]["reach"]["reaches"]],
        )

    def test_a_comparison_in_a_declarator_does_not_lose_the_declarators_after_it(self):
        """`export const flag = 1 < 2, b = ...` — `b` is a function a caller can import.

        `_next_declarator` counted the `<` of the comparison as a bracket nothing closed,
        so the depth stayed above zero for the rest of the scan and the comma separating
        the two declarators was never seen. `b` was then in `methods`, in `types` and in
        the declined log alike — nowhere at all, which is the one thing this reading must
        never do with something a caller can reach.
        """
        interface = self.interface_of(
            "till.ts", "export const flag = 1 < 2, b = (x: number): number => x\n"
        )

        self.assertEqual(
            [("b", ["number"], "number")],
            [(each["name"], each["parameters"], each["returns"])
             for each in interface["methods"]],
        )

    def test_a_type_argument_list_in_a_declarator_does_not_split_it(self):
        """The floor under the rule above: a `<` a `>` closes on the same line is a bracket.

        Read as no bracket at all, the comma inside `Map<string, number>` would separate
        two declarators the source never wrote.
        """
        interface = self.interface_of(
            "till.ts",
            "export const held = new Map<string, number>(), b = (x: number): number => x\n",
        )

        self.assertEqual(
            ["b"], [method["name"] for method in interface["methods"]]
        )

    def test_a_written_type_wrapped_onto_a_second_line_keeps_both_names(self):
        """`const held: Till |\n  Receipt = ...` was recorded as the type `Till |`.

        A newline at bracket depth zero ended the written type, and a `|` continuation is
        outside every bracket — so a call written through `held` drew a fan line to one of
        the two names the source wrote, and the evidence printed on the card was a string
        that is not a type at all.
        """
        modules = self.read(
            "till.ts",
            "import { make } from './make'\n"
            "import type { Receipt } from './receipts'\n"
            "import type { Till } from './tills'\n"
            "const held: Till |\n  Receipt = make()\n"
            "export function go(): number {\n  held.ring()\n  return 1\n}\n",
            ("make.ts", "export function make(): number {\n  return 1\n}"),
            ("receipts.ts", "export type Receipt = { ring(): void }"),
            ("tills.ts", "export type Till = { ring(): void }"),
        )

        self.assertEqual(
            ["web/make", "web/receipts", "web/tills"],
            sorted(each["moduleId"] for each in modules["web/till"]["reach"]["reaches"]),
        )
        self.assertIn(
            "called through the field held, which holds a Till | Receipt",
            [each["matched"] for each in modules["web/till"]["reach"]["reaches"]],
        )

    def test_a_written_type_wrapped_with_the_joiner_leading_keeps_both_names(self):
        """The other way a formatter breaks the same type."""
        modules = self.read(
            "till.ts",
            "import { make } from './make'\n"
            "import type { Receipt } from './receipts'\n"
            "import type { Till } from './tills'\n"
            "const held: Till\n  | Receipt = make()\n"
            "export function go(): number {\n  held.ring()\n  return 1\n}\n",
            ("make.ts", "export function make(): number {\n  return 1\n}"),
            ("receipts.ts", "export type Receipt = { ring(): void }"),
            ("tills.ts", "export type Till = { ring(): void }"),
        )

        self.assertEqual(
            ["web/make", "web/receipts", "web/tills"],
            sorted(each["moduleId"] for each in modules["web/till"]["reach"]["reaches"]),
        )

    def test_a_string_literal_type_is_printed_as_the_source_wrote_it(self):
        """A parameter came out `' ' | ' '`, which is the masked text rather than a type.

        Nothing moved and no file failed, which is why it went unnoticed: the only thing
        wrong with it was that a reader checking the card against the source would find a
        type the file does not contain. A type is measured off the masked text — a comma
        inside a string must not split a parameter list — and spelled off the original.
        """
        interface = self.interface_of(
            "till.ts",
            "export function union(a: 'one' | 'two' | 'three'): number {\n"
            "  return a.length\n}\n",
        )

        self.assertEqual(
            ["'one' | 'two' | 'three'"], interface["methods"][0]["parameters"]
        )

    def test_a_string_literal_type_names_no_type_a_caller_must_learn(self):
        """The floor under the rule above: a literal type is a value, not a type.

        Spelled back onto the card and then read for names, `one`, `two` and `three` were
        each charged to a caller as a type to go and learn.
        """
        interface = self.interface_of(
            "till.ts",
            "export function union(a: 'one' | 'two' | 'three'): 'read' | 'write' {\n"
            "  return 'read'\n}\n",
        )

        self.assertEqual(
            "'read' | 'write'", interface["methods"][0]["returns"]
        )
        self.assertEqual([], interface["typesCrossingTheSeam"])

    def test_a_string_literal_type_inside_a_generic_is_printed_as_written(self):
        interface = self.interface_of(
            "till.ts",
            "export function keyed(a: Record<'a'|'b', number>): number {\n  return 1\n}\n",
        )

        self.assertEqual(
            ["Record<'a' | 'b', number>"], interface["methods"][0]["parameters"]
        )


class WhatAModuleReachesIsTheNameItsBodyWritesTest(SourceTreeTest):
    """A fan line is followed from the name the body wrote, not the name the module was asked for.

    An import binds a module's member under a name the importing file gets to choose, and
    the body then writes that one. Matched the other way round, only the imports that
    renamed nothing were ever followed: every renamed import, and every default import —
    whose member is the word `default` — reached nothing at all, which is exactly what a
    shallow module looks like.
    """

    def modules(self, *sources):
        tree = self.tree("web")
        for name, body in sources:
            tree.typescript("", name, body)
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        self.assertEqual([], document["source"]["unparsed"])
        return {module["id"]: module for module in document["modules"]}

    def test_a_renamed_import_that_is_called_reaches_the_module_it_came_from(self):
        modules = self.modules(
            ("api.ts", "export function fetchDeposits(id: number): number {\n  return id\n}"),
            ("uses.ts",
             "import { fetchDeposits as fd } from \'./api\'\n"
             "export function load(id: number): number {\n  return fd(id)\n}"),
        )

        self.assertEqual(
            ["web/api"],
            [reached["moduleId"] for reached in modules["web/uses"]["reach"]["reaches"]],
        )

    def test_the_fan_line_says_the_name_the_body_wrote_so_it_can_be_checked(self):
        modules = self.modules(
            ("api.ts", "export function fetchDeposits(id: number): number {\n  return id\n}"),
            ("uses.ts",
             "import { fetchDeposits as fd } from \'./api\'\n"
             "export function load(id: number): number {\n  return fd(id)\n}"),
        )

        self.assertIn(
            "fd", modules["web/uses"]["reach"]["reaches"][0]["matched"]
        )

    def test_a_default_import_that_is_called_reaches_the_module_it_came_from(self):
        modules = self.modules(
            ("App.ts", "export default function App(): number {\n  return 1\n}"),
            ("main.ts",
             "import App from \'./App\'\n"
             "export function boot(): number {\n  return App()\n}"),
        )

        self.assertEqual(
            ["web/App"],
            [reached["moduleId"] for reached in modules["web/main"]["reach"]["reaches"]],
        )

    def test_a_flow_through_a_renamed_import_is_walked_rather_than_stopping_there(self):
        """The same defect truncated a flow, which is a path and not a set."""
        tree = self.tree("web")
        tree.typescript("", "api.ts", "export function fetchDeposits(id: number): number {\n  return id\n}")
        tree.typescript(
            "", "uses.ts",
            "import { fetchDeposits as fd } from \'./api\'\n"
            "export function load(id: number): number {\n  return fd(id)\n}",
        )
        configuration = _committed()
        configuration["flows"] = [
            {
                "flow": "a load",
                "because": "the one flow this fixture has",
                "entryPoint": {"module": "web/uses", "method": "load"},
            }
        ]
        rules = scoring.load(
            self.tree("rules").raw("scoring.json", _as_json(configuration))
        )

        document = graph.build([graph.source_root(tree.root)], rules)

        self.assertEqual(
            ["web/uses", "web/api"],
            [step["moduleId"] for step in document["flows"][0]["path"]],
        )
        self.assertEqual("fd", document["flows"][0]["path"][1]["call"])

    def test_a_name_the_body_declares_itself_is_not_a_call_to_an_import_of_that_name(self):
        """The floor the Java side keeps, kept here: a declaration is not a call."""
        modules = self.modules(
            ("api.ts", "export function of(id: number): number {\n  return id\n}"),
            ("uses.ts",
             "import { of } from \'./api\'\n"
             "function of(id: number): number {\n  return id\n}\n"
             "export function load(id: number): number {\n  return of(id)\n}"),
        )

        self.assertEqual([], modules["web/uses"]["reach"]["reaches"])


    def test_a_name_a_construction_qualifies_is_not_a_name_called_on(self):
        """`new api.Thing()` writes the characters a call on `api` writes and is neither.

        Read as a receiver it drew a fan line whose evidence read "called on api" over a
        file that calls nothing on it — a sentence a reader can check against the source
        and find false, which is the one failure the Java side says its own receiver
        reading exists to make impossible. This reading reintroduced it by building
        receivers with no guard at all.
        """
        modules = self.modules(
            ("api.ts", "export class Thing {}"),
            ("uses.ts",
             "import * as api from './api'\n"
             "export function make(): number {\n  const t = new api.Thing()\n  return 1\n}"),
        )

        self.assertEqual([], modules["web/uses"]["reach"]["reaches"])

    def test_a_name_a_namespace_import_binds_is_still_reached_by_calling_through_it(self):
        """The floor above it: what really is called on the namespace still draws a line."""
        modules = self.modules(
            ("api.ts", "export function one(): number {\n  return 1\n}"),
            ("uses.ts",
             "import * as api from './api'\n"
             "export function make(): number {\n  return api.one()\n}"),
        )

        self.assertEqual(
            ["web/api"],
            [reached["moduleId"] for reached in modules["web/uses"]["reach"]["reaches"]],
        )

    def test_a_module_named_after_a_name_it_imports_still_reaches_it(self):
        """Two files of the same bytes, one of them named after a function it imports.

        A name a body declares is not a call to an import that shares its spelling, and a
        Java class's own name is one of those: it is in scope inside the file that
        declares it. A TypeScript module's name is the basename of its file and a binding
        nowhere in it — so read the Java way, `format.ts` was taken to declare `format`,
        its own import of that name was never followed, and it reached nothing while the
        identical file next to it reached `util`. Naming a file after the thing it is
        about is the ordinary way to write a frontend, and this repository's own `api.ts`
        is one rename away from it.
        """
        body = ("import { format } from './util'\n"
                "export function run(n: number): string {\n  return format(n)\n}")
        modules = self.modules(
            ("util.ts", "export function format(n: number): string {\n  return String(n)\n}"),
            ("format.ts", body),
            ("other.ts", body),
        )

        for module_id in ("web/format", "web/other"):
            with self.subTest(module=module_id):
                self.assertEqual(
                    [("web/util", "calls format, which this file imports from it")],
                    [(each["moduleId"], each["matched"])
                     for each in modules[module_id]["reach"]["reaches"]],
                )

    def test_a_java_module_still_declares_its_own_name_for_itself(self):
        """The floor under the rule above, on the side the rule was written for.

        A Java class really does bind its own name, so a body writing `of(...)` inside a
        class called `of` is writing its own declaration and reaches nothing. Read without
        that, a module that merely imports a member and happens to declare something of
        the same name is credited with calling the module the import came from.
        """
        tree = self.tree("backend")
        tree.java("shop", "Prices", "public class Prices {\n"
                  "    public static long of(long each) { return each; }\n}")
        tree.java("shop.till", "Till",
                  "import static shop.Prices.of;\npublic class Till {\n"
                  "    public long ring(long each) { return of(each); }\n"
                  "    private long of(long each) { return each; }\n}")
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        modules = {module["id"]: module for module in document["modules"]}

        self.assertEqual([], modules["shop.till.Till"]["reach"]["reaches"])

    def test_a_jsx_element_written_with_a_dot_reaches_nothing_rather_than_the_namespace(self):
        """`<Icons.Chevron />` is `new api.Thing()` written as an element, and gets its answer.

        The name the source wrote is `Icons.Chevron`, which names no module here. Matched
        without the dot the name read was `Icons`, which would say the file builds the
        namespace itself — a sentence a reader can check against the source and find
        false. So this is a floor, and it is named on the page beside the other floors
        rather than left where somebody checking a fan would find a gap nobody accounted
        for.
        """
        modules = self.modules(
            ("icons.tsx", "export function Chevron(): JSX.Element {\n  return <i />\n}"),
            ("row.tsx",
             "import * as Icons from './icons'\n"
             "export function Row(): JSX.Element {\n  return <Icons.Chevron />\n}"),
        )

        self.assertEqual([], modules["web/row"]["reach"]["reaches"])

    def test_the_run_says_which_qualified_names_it_followed_nowhere(self):
        """A floor nobody can see is not one anybody can trust, so it is said out loud.

        Once per file rather than once per element: a `.tsx` file writes tens of them and
        a line each would be a line inside a loop.
        """
        tree = self.tree("web")
        tree.typescript("", "icons.tsx",
                        "export function Chevron(): JSX.Element {\n  return <i />\n}")
        tree.typescript("", "row.tsx",
                        "import * as Icons from './icons'\n"
                        "export function Row(): JSX.Element {\n  return <Icons.Chevron />\n}")

        with self.assertLogs("module_depth_map.typescriptsource", level="DEBUG") as logged:
            graph.build([graph.source_root(tree.root)], scoring.load())

        said = "\n".join(logged.output)
        self.assertIn("qualified names read as reaching no module here", said)
        self.assertIn("Icons.Chevron", said)

    def test_the_page_names_a_dotted_name_among_the_readings_that_understate(self):
        """A floor whose edge a reader cannot see is not one they can trust.

        The page lists its understating readings by name, and this one was absent — so
        somebody checking a fan against the source would have found a gap the page does
        not account for.
        """
        tree = self.tree("web")
        tree.typescript("", "icons.tsx",
                        "export function Chevron(): JSX.Element {\n  return <i />\n}")
        document = graph.build([graph.source_root(tree.root)], scoring.load())

        rendered = page.render(document, graph.serialise(document)).decode("utf-8")

        self.assertIn("a name written with a dot in front of it", rendered)
        self.assertIn("Icons.Chevron", rendered)
        self.assertIn("api.Thing()", rendered)

    def test_a_jsx_element_from_a_named_import_still_reaches_its_module(self):
        """The floor above it, which is the reading this one must not have broken."""
        modules = self.modules(
            ("icons.tsx", "export function Chevron(): JSX.Element {\n  return <i />\n}"),
            ("row.tsx",
             "import { Chevron } from './icons'\n"
             "export function Row(): JSX.Element {\n  return <Chevron />\n}"),
        )

        self.assertEqual(
            ["web/icons"],
            [reached["moduleId"] for reached in modules["web/row"]["reach"]["reaches"]],
        )


class AnOverloadSetIsWhatACallerCanCallTest(SourceTreeTest):
    """TypeScript never exposes an overload set's implementation signature to a caller.

    Two signatures over an implementation give a caller two calls they can make, not
    three, and the third is one `tsc` refuses to let anybody write. Read as a method it
    was charged a method and a parameter, the card named a call a reader can go looking
    for and will never be able to make, and the inflated cost was the denominator leverage
    is divided by.

    This is a place where measuring both halves of the application by the same rules means
    reading two shapes differently rather than the same. Every Java overload is genuinely
    callable; exactly one TypeScript overload in every set is not.
    """

    THE_SET = ("export function ring(id: string): string\n"
               "export function ring(id: number): string\n"
               "export function ring(id: string | number): string {\n"
               "  return String(id)\n}\n")

    def module(self, body, under="web"):
        tree = self.tree(under)
        tree.typescript("", "till.ts", body)
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        self.assertEqual([], document["source"]["unparsed"])
        return {module["id"]: module for module in document["modules"]}[under + "/till"]

    def test_only_the_signatures_a_caller_can_call_are_on_the_interface(self):
        interface = self.module(self.THE_SET)["interface"]

        self.assertEqual(
            [("ring", ["number"]), ("ring", ["string"])],
            sorted((method["name"], method["parameters"])
                   for method in interface["methods"]),
        )

    def test_the_implementation_signature_is_charged_nothing(self):
        """The cost is the two callable signatures and no more: a method and a parameter each."""
        interface = self.module(self.THE_SET)["interface"]

        self.assertEqual(4, interface["cost"])

    def test_widening_the_implementation_signature_moves_no_number(self):
        """Which is the whole claim: it is not part of what a caller has to learn.

        The strongest form of "the same cost as the equivalent set" this can be written
        as — the implementation may be spelled any way the source likes and the interface
        is the same interface.
        """
        wider = ("export function ring(id: string): string\n"
                 "export function ring(id: number): string\n"
                 "export function ring(id: string | number | boolean): string {\n"
                 "  return String(id)\n}\n")

        self.assertEqual(
            self.module(self.THE_SET, under="narrow")["interface"],
            self.module(wider, under="wide")["interface"],
        )

    def test_a_function_written_once_is_still_the_one_method_it_is(self):
        """The floor under all of it: a body is only an implementation where a header is above it."""
        interface = self.module(
            "export function ring(id: string | number): string {\n  return String(id)\n}\n"
        )["interface"]

        self.assertEqual(
            [("ring", ["string | number"])],
            [(method["name"], method["parameters"]) for method in interface["methods"]],
        )
        self.assertEqual(2, interface["cost"])

    def test_a_refusal_documented_on_the_implementation_is_still_the_module_s_word(self):
        """The one thing hiding it must not cost: a module accused of breaking its word.

        TypeScript's own language service shows a caller the implementation's JSDoc for an
        overload that carries none of its own, and this reads it the same way. Left behind
        instead, a module that documented its refusal exactly where TypeScript wants it
        written would be reported for raising one it never promised.
        """
        module = self.module(
            "export class Refused extends Error {}\n"
            "export function ring(id: string): string\n"
            "export function ring(id: number): string\n"
            "/** @throws Refused when the till will not */\n"
            "export function ring(id: string | number): string {\n"
            "  if (id === 0) {\n    throw new Refused()\n  }\n"
            "  return String(id)\n}\n"
        )

        self.assertEqual([], module["findings"])
        self.assertEqual(
            [("Refused", True, True)],
            [(each["name"], each["documented"], each["raised"])
             for each in module["interface"]["refusals"]],
        )

    def test_every_java_overload_is_still_a_method_a_caller_can_call(self):
        """The other side of "the same rules": here the reading is not the same reading."""
        tree = self.tree("backend")
        tree.java("shop", "Till", "public class Till {\n"
                  "    public String ring(String id) { return id; }\n"
                  "    public String ring(long id) { return \"\"; }\n}")
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        interface = {module["id"]: module
                     for module in document["modules"]}["shop.Till"]["interface"]

        self.assertEqual(
            [("ring", ["String"]), ("ring", ["long"])],
            sorted((method["name"], method["parameters"])
                   for method in interface["methods"]),
        )


class WhatACallerAlreadyKnowsIsAJudgementPerLanguageTest(SourceTreeTest):
    """The familiar-type list is one list per language, and the file writes both.

    Read as one shared list, "measured by the same rules" was implemented as "measured
    by one shared list" — and the two are not the same claim. `string` is free to a
    TypeScript caller and is not a name a Java one ever meets; a Java module whose seam is
    spelled with a domain type called `Response` was charged nothing for it off a name
    that meant `fetch`'s. No number in this repository moved either way, which is exactly
    why nothing would have said so.
    """

    def setUp(self):
        super().setUp()
        self.rules = scoring.load()

    def crossing(self, tree, module_id):
        document = graph.build([graph.source_root(tree.root)], self.rules)
        self.assertEqual([], document["source"]["unparsed"])
        module = {module["id"]: module for module in document["modules"]}[module_id]
        return {
            type_["name"]: type_["mustBeLearned"]
            for type_ in module["interface"]["typesCrossingTheSeam"]
        }

    def test_a_java_seam_spelled_with_a_typescript_platform_type_is_charged_for_it(self):
        tree = self.tree("backend")
        tree.java(
            "shop", "Till", "public class Till {\n    public Response ring(Response given) "
            "{ return given; }\n}",
        )

        self.assertEqual({"Response": True}, self.crossing(tree, "shop.Till"))

    def test_a_typescript_seam_spelled_with_a_java_type_is_charged_for_it(self):
        tree = self.tree("web")
        tree.typescript("", "till.ts", "export function ring(given: Optional): Optional {\n"
                        "  return given\n}")

        self.assertEqual({"Optional": True}, self.crossing(tree, "web/till"))

    def test_each_language_still_gets_the_types_its_own_list_names(self):
        tree = self.tree("mixed")
        tree.java("shop", "Till", "public class Till {\n    public String ring(long id) "
                  "{ return null; }\n}")
        tree.typescript("", "till.ts", "export function ring(id: number): string {\n"
                        "  return \'\'\n}")

        self.assertEqual({"String": False, "long": False}, self.crossing(tree, "shop.Till"))
        self.assertEqual({"number": False, "string": False}, self.crossing(tree, "mixed/till"))

    def test_the_document_publishes_the_list_each_language_was_scored_against(self):
        tree = self.tree("mixed")
        tree.java("shop", "Till", "public class Till {\n    public void ring() {}\n}")
        tree.typescript("", "till.ts", "export function ring(): void {}")

        document = graph.build([graph.source_root(tree.root)], self.rules)

        published = document["scoring"]["typesEveryCallerAlreadyKnows"]
        self.assertEqual(["java", "typescript"], sorted(published))
        for language in ("java", "typescript"):
            self.assertEqual(sorted(self.rules.already_known[language]), published[language])


class OneAccountOfWhatBothReadingsShareTest(SourceTreeTest):
    """The helpers both readings need are one function, because a copy of one drifts.

    `after_balanced` and `in_evaluation_order` were made public on the Java side from the
    start, on the grounds that matching a bracket to its partner and evaluating arguments
    before a call are facts about punctuation rather than about Java. Three more —
    splitting a list on its commas, spelling a type, and counting the line an offset sits
    on — were hand-copied instead, and the copies drifted. Two findings against this branch
    are what the drift cost, and both are held here.
    """

    def read(self, tree, module_id):
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        self.assertEqual([], document["source"]["unparsed"])
        return {module["id"]: module for module in document["modules"]}[module_id]

    def test_one_type_spelled_two_ways_is_one_string_in_typescript(self):
        """`Record<string,number>` and `Record<string, number>` are one type, not two.

        The Java copy of this rule names the hazard in its own words — one document held
        both `Map<String, Long>` and `Map<String,Long>`, which reads as two types a caller
        has to learn where there is one — and the TypeScript copy dropped the comma.
        """
        tree = self.tree("web")
        tree.typescript(
            "", "till.ts",
            "export function a(m: Record<string,number>): void {}\n"
            "export function b(m: Record<string, number>): void {}\n",
        )

        interface = self.read(tree, "web/till")["interface"]

        self.assertEqual(
            ["Record<string, number>"],
            sorted({method["parameters"][0] for method in interface["methods"]}),
        )

    def test_one_type_spelled_two_ways_is_one_string_in_java_too(self):
        """The same fixture on the side the rule was written for, so the two cannot part."""
        tree = self.tree("backend")
        tree.java(
            "shop", "Till",
            "import java.util.Map;\npublic class Till {\n"
            "    public void a(Map<String,Long> m) { }\n"
            "    public void b(Map<String, Long> m) { }\n}",
        )

        interface = self.read(tree, "shop.Till")["interface"]

        self.assertEqual(
            ["Map<String, Long>"],
            sorted({method["parameters"][0] for method in interface["methods"]}),
        )

    def test_one_union_spelled_two_ways_is_one_string(self):
        """`string|null` and `string | null` are one type, exactly as the comma case is.

        The same hazard `normalised`'s own docstring names, fixed for the comma and missed
        for the joiner beside it. Nothing counts differently either way; what a reader
        sees on two cards does.
        """
        tree = self.tree("web")
        tree.typescript(
            "", "till.ts",
            "export function a(m: string|null): void {}\n"
            "export function b(m: string | null): void {}\n",
        )

        interface = self.read(tree, "web/till")["interface"]

        self.assertEqual(
            ["string | null"],
            sorted({method["parameters"][0] for method in interface["methods"]}),
        )

    def test_one_object_type_spelled_two_ways_is_one_string(self):
        """`{ id: number }` and `{id: number}` are one type, exactly as the comma case is.

        A brace group is how TypeScript spells an anonymous object type, and it is the
        commonest anonymous type there is — every inline props type in a React file is
        one. The shared speller knew about `<`, `[`, `,`, `|` and `&` and had never been
        told about the two characters this side writes most.
        """
        tree = self.tree("web")
        tree.typescript(
            "", "till.ts",
            "export function a(x: { id: number }): void {}\n"
            "export function b(x: {id: number}): void {}\n",
        )

        interface = self.read(tree, "web/till")["interface"]

        self.assertEqual(
            ["{ id: number }"],
            sorted({method["parameters"][0] for method in interface["methods"]}),
        )

    def test_one_function_type_spelled_two_ways_is_one_string(self):
        """`(a: number) => number` and `(a: number)=>number` are one type too."""
        tree = self.tree("web")
        tree.typescript(
            "", "till.ts",
            "export function e(f: (a: number) => number): void {}\n"
            "export function g(f: (a: number)=>number): void {}\n",
        )

        interface = self.read(tree, "web/till")["interface"]

        self.assertEqual(
            ["(a: number) => number"],
            sorted({method["parameters"][0] for method in interface["methods"]}),
        )

    def test_an_index_signature_is_printed_the_way_the_source_wrote_it(self):
        """`{ [k: string]: number }` in the source, and on the card.

        The rule that closes an array's brackets up against the type they belong to fired
        on the *opening* brace of a group whose closing one nothing balanced, so the card
        printed `{[k: string]: number }` — a spelling in neither the source nor any normal
        form. That is the same objection an earlier round of this ticket raised about a
        string literal type printed as its mask.
        """
        tree = self.tree("web")
        tree.typescript(
            "", "till.ts",
            "export function c(x: { [k: string]: number }): void {}\n",
        )

        interface = self.read(tree, "web/till")["interface"]

        self.assertEqual(
            ["{ [k: string]: number }"], interface["methods"][0]["parameters"]
        )

    def test_an_empty_object_type_keeps_the_spelling_everybody_writes(self):
        """`{}` is not written `{ }` anywhere, and a normal form nobody writes is a third one."""
        for written in ("{}", "{ }", "{\n}"):
            with self.subTest(written=written):
                self.assertEqual("{}", javasource.normalised(written))

    def test_the_brace_and_arrow_rules_live_in_the_speller_both_readings_share(self):
        """Asked of the shared function itself, because Java writes neither into a type.

        Its types hold no braces at all and no `=>`, so there is no Java fixture to write
        — and the rule still belongs in the one speller rather than on the TypeScript
        side, for the reason the comma and the union do: whether two strings are one type
        spelled twice is a question about spacing, and a second answer to it is one that
        can drift from this one.
        """
        for written in ("{ id: number }", "{id: number}", "{  id: number  }"):
            with self.subTest(written=written):
                self.assertEqual("{ id: number }", javasource.normalised(written))
        for written in ("(a: A) => B", "(a: A)=>B", "(a: A)  =>  B"):
            with self.subTest(written=written):
                self.assertEqual("(a: A) => B", javasource.normalised(written))

    def test_the_union_rule_lives_in_the_speller_both_readings_share(self):
        """Asked of the shared function itself, because Java writes no union into a type.

        Its `|` is a multi-catch's and its `&` a type bound's, and neither is a parameter,
        a return or a field — so there is no Java fixture to write. The rule still belongs
        in the one speller rather than on the TypeScript side, for the reason the comma
        does: whether two strings are one type spelled twice is a question about spacing,
        and a second answer to it is one that can drift from this one.
        """
        for written in ("string|null", "string | null", "string  |  null"):
            with self.subTest(written=written):
                self.assertEqual("string | null", javasource.normalised(written))
        self.assertEqual("A & B", typescriptsource.normalised("A&B"))

    def test_the_typescript_reading_holds_no_copy_of_a_helper_the_java_one_owns(self):
        """A guard on the arrangement itself, since a copy is what drifted last time."""
        for name in ("split_on_commas", "spans_between_commas", "normalised", "line_of",
                     "after_balanced", "after_the_arguments", "in_evaluation_order",
                     "ends_an_arrow"):
            with self.subTest(helper=name):
                self.assertIs(
                    getattr(javasource, name), getattr(typescriptsource, name)
                )
        source = open(typescriptsource.__file__, encoding="utf-8").read()
        for name in ("split_on_commas", "spans_between_commas", "normalised", "line_of",
                     "after_the_arguments"):
            with self.subTest(helper=name):
                self.assertNotIn("def %s(" % name, source)


class TestCodeBuildOutputAndDependenciesAreNotInTheGraphTest(SourceTreeTest):
    """The graph covers the application's own source, and says under which rule.

    Left in, the frontend alone arrives as tens of thousands of files under
    `node_modules`, and the finding is buried under somebody else's source. A directory is
    skipped whole rather than walked into, so it costs one line in the document rather
    than everything inside it.
    """

    def setUp(self):
        super().setUp()
        self.rules = scoring.load()
        tree = self.tree("web")
        tree.typescript("", "till.ts", "export function ring(): void {}")
        tree.typescript("", "till.test.ts", "export function ringsUp(): void {}")
        tree.typescript("", "till.spec.tsx", "export function alsoRingsUp(): void {}")
        tree.typescript("", "shims.d.ts", "export declare function generated(): void")
        tree.typescript("node_modules/react", "index.ts", "export function useState(): void {}")
        tree.typescript("dist/assets", "bundle.ts", "export function built(): void {}")
        tree.typescript("__tests__", "helpers.ts", "export function helping(): void {}")
        tree.java("shop", "Till", "public class Till {}")
        tree.java("shop", "TillTest", "public class TillTest {}")
        self.tree_root = tree
        self.document = graph.build([graph.source_root(tree.root)], self.rules)

    def test_only_the_application_s_own_source_is_a_module(self):
        self.assertEqual(
            ["shop.Till", "web/till"],
            sorted(module["id"] for module in self.document["modules"]),
        )

    def test_every_path_that_was_not_read_is_named_with_the_rule_and_what_matched(self):
        not_read = self.document["source"]["notRead"]

        self.assertEqual(self.rules.sources_not_read.rule, not_read["rule"])
        self.assertTrue(not_read["because"].strip())
        self.assertEqual(
            {
                ("__tests__", "a directory named __tests__"),
                ("dist", "a directory named dist"),
                ("node_modules", "a directory named node_modules"),
                ("shims.d.ts", "a name ending in .d.ts"),
                ("till.spec.tsx", "a name ending in .spec.tsx"),
                ("till.test.ts", "a name ending in .test.ts"),
                ("shop/TillTest.java", "a name ending in Test.java"),
            },
            {(entry["path"], entry["matched"]) for entry in not_read["paths"]},
        )
        for entry in not_read["paths"]:
            self.assertEqual("web", entry["root"])

    def test_a_skipped_directory_is_one_path_rather_than_everything_inside_it(self):
        """Which is the whole reason a directory is matched by name and never walked into."""
        named = [entry["path"] for entry in self.document["source"]["notRead"]["paths"]]

        self.assertIn("node_modules", named)
        self.assertNotIn("node_modules/react/index.ts", named)

    def test_a_path_a_rule_declined_is_not_counted_as_a_file_that_would_not_parse(self):
        self.assertEqual([], self.document["source"]["unparsed"])
        self.assertEqual(0, self.document["source"]["filesUnparsed"])
        self.assertEqual(2, self.document["source"]["filesSeen"])

    def test_the_run_says_which_rule_declined_each_path(self):
        with self.assertLogs("module_depth_map.graph", level="INFO") as logged:
            graph.build([graph.source_root(self.tree_root.root)], self.rules)

        said = "\n".join(logged.output)
        self.assertIn("source not read", said)
        self.assertIn("node_modules", said)
        self.assertIn(self.rules.sources_not_read.rule, said)

    def test_the_paths_that_were_not_read_come_out_sorted(self):
        paths = [
            (entry["root"], entry["path"])
            for entry in self.document["source"]["notRead"]["paths"]
        ]

        self.assertEqual(sorted(paths), paths)

    def test_a_configuration_naming_none_of_this_is_refused_rather_than_defaulted(self):
        """A file that decided nothing must not be read as "read everything".

        The reading it would produce is the one this tool refuses everywhere else: an
        output that looks like somebody's judgement, taken with the file they wrote it in
        never consulted.
        """
        document = {
            key: value
            for key, value in _committed().items()
            if key != "sourcesNotRead"
        }
        written = self.tree("rules").raw("scoring.json", _as_json(document))

        with self.assertRaises(scoring.ConfigurationRefused) as refused:
            scoring.load(written)

        self.assertIn("sourcesNotRead", refused.exception.reason)

    def test_a_directory_rule_written_as_a_path_is_refused_as_one_that_could_never_fire(self):
        document = dict(_committed())
        document["sourcesNotRead"] = dict(
            document["sourcesNotRead"], directories=["frontend/node_modules"]
        )
        written = self.tree("rules").raw("scoring.json", _as_json(document))

        with self.assertRaises(scoring.ConfigurationRefused) as refused:
            scoring.load(written)

        self.assertIn("frontend/node_modules", refused.exception.reason)

    def test_the_rule_can_be_emptied_by_somebody_who_wants_everything_read(self):
        """A position somebody can hold: read whatever I point you at.

        Which is why it is a pair of empty lists rather than a missing key — the first is
        a decision in the file, the second is nobody having made one.
        """
        document = dict(_committed())
        document["sourcesNotRead"] = dict(
            document["sourcesNotRead"], directories=[], namesEndingWith=[]
        )
        written = self.tree("rules").raw("scoring.json", _as_json(document))

        found = graph.build(
            [graph.source_root(self.tree_root.root)], scoring.load(written)
        )

        self.assertIn("web/till.test", [module["id"] for module in found["modules"]])
        self.assertEqual([], found["source"]["notRead"]["paths"])

    def test_a_source_root_the_rule_declines_by_its_own_name_is_not_walked_into(self):
        """The rule holds a directory out by name, and a root is a directory too.

        Checked only against what the walk found underneath, `--source
        frontend/node_modules` walked straight into the one directory the rule exists to
        keep out: twenty-six dependency modules scored, drawn and reported as this
        application's own source, with the rule that names them reporting nothing at all.
        """
        inside = graph.source_root(os.path.join(self.tree_root.root, "node_modules"))

        document = graph.build([inside], self.rules)

        self.assertEqual([], document["modules"])
        self.assertEqual(
            [{"root": "web/node_modules", "path": "",
              "matched": "a directory named node_modules"}],
            document["source"]["notRead"]["paths"],
        )

    def test_a_root_the_rule_declines_is_said_out_loud_as_the_root_itself(self):
        inside = graph.source_root(os.path.join(self.tree_root.root, "dist"))

        with self.assertLogs("module_depth_map.graph", level="INFO") as logged:
            graph.build([inside], self.rules)

        said = "\n".join(logged.output)
        self.assertIn("source not read", said)
        self.assertIn("path=the root itself", said)
        self.assertIn("a directory named dist", said)


class ThisFrontendIsReadWholeTest(SourceTreeTest):
    """Properties that must hold when the tool is run over this repository's own frontend.

    Not the numbers, which move every time a feature lands, but the things that have to be
    true whatever is written there: every file read or reported, every module scored rather
    than boxed, and the frontend visible as its own shape rather than as one card.
    """

    def setUp(self):
        super().setUp()
        self.document = graph.build([graph.source_root(FRONTEND_SOURCE)], scoring.load())
        self.by_id = {module["id"]: module for module in self.document["modules"]}

    def test_the_whole_of_this_frontend_can_be_read(self):
        self.assertEqual([], self.document["source"]["unparsed"])
        self.assertEqual(
            self.document["source"]["filesSeen"], self.document["source"]["filesParsed"]
        )
        self.assertGreater(self.document["source"]["filesParsed"], 0)

    def test_every_typescript_file_on_disk_is_either_read_or_reported(self):
        on_disk = set()
        for directory, _, names in os.walk(FRONTEND_SOURCE):
            for name in names:
                if name.endswith(languages.typescriptsource.SUFFIXES):
                    whole = os.path.join(directory, name)
                    on_disk.add(os.path.relpath(whole, FRONTEND_SOURCE).replace(os.sep, "/"))

        reported = {entry["path"] for entry in self.document["source"]["unparsed"]}
        declined = {entry["path"] for entry in self.document["source"]["notRead"]["paths"]}
        read = {module["path"] for module in self.document["modules"]}

        self.assertEqual(on_disk, read | reported | declined)

    def test_the_frontend_is_drawn_as_its_own_modules_rather_than_as_one_box(self):
        """The picture is of the application, which means the frontend has a shape on it.

        Every file is a card of its own, every card carries the numbers the backend's
        cards carry, and no rule declines to price any of them. Collapsed into a single
        unscored box instead, the page would be a picture of the backend with a label
        beside it.
        """
        self.assertGreater(len(self.by_id), 1)
        for module in self.by_id.values():
            self.assertEqual("typescript", module["language"])
            self.assertIsNone(module["excludedBy"], module["id"])
            self.assertIsNotNone(module["interface"]["cost"], module["id"])
            self.assertIsNotNone(module["deletionTest"]["verdict"], module["id"])
            self.assertIn("count", module["reach"])

    def test_the_modules_a_reader_would_look_for_first_are_there(self):
        self.assertIn("frontend/src/App", self.by_id)
        self.assertIn("frontend/src/api", self.by_id)
        self.assertEqual("frontend/src", self.by_id["frontend/src/App"]["package"])

    def test_the_component_that_holds_this_whole_page_presents_one_export(self):
        """The specimen the specification predicted before this reading existed.

        Named here rather than left to be admired: the largest file in the repository
        offers a caller one method and coordinates one thing, so it is drawn as the
        narrowest bar over the narrowest fan on the page. Asserted as a shape rather than
        as a number, so that a feature landing in that file does not redden the suite.
        """
        app = self.by_id["frontend/src/App"]

        self.assertEqual(1, len(app["interface"]["methods"]))
        self.assertGreater(app["lines"], 500)
        self.assertLessEqual(app["reach"]["count"], app["deletionTest"]["methods"])

    def test_every_module_a_frontend_module_reaches_is_a_module_this_graph_holds(self):
        for module in self.by_id.values():
            for entry in module["reach"]["reaches"]:
                if entry["moduleId"] is not None:
                    self.assertIn(entry["moduleId"], self.by_id, module["id"])
            for caller in module["callers"]["moduleIds"]:
                self.assertIn(caller, self.by_id, module["id"])


class TheFrontendIsOnThePageTest(SourceTreeTest):
    """The page is a rendering of the graph, so the frontend being in one puts it in the other.

    Read off the rendered bytes rather than off a browser, because what is asserted is
    that the page carries the document and names the two grains it was read at — not
    anything about how it lays them out.
    """

    def setUp(self):
        super().setUp()
        tree = self.tree("mixed")
        tree.java("shop", "Till", "public class Till {\n    public void ring() {}\n}")
        tree.typescript("", "till.ts", "export function ring(): void {}")
        self.document = graph.build([graph.source_root(tree.root)], scoring.load())
        self.rendered = page.render(
            self.document, graph.serialise(self.document)
        ).decode("utf-8")

    def test_the_page_carries_every_module_the_graph_holds(self):
        for module in self.document["modules"]:
            self.assertIn(module["id"], self.rendered)

    def test_the_page_says_which_grain_each_half_was_read_at(self):
        self.assertIn("A Java module is a class", self.rendered)
        self.assertIn("A TypeScript module is a file", self.rendered)

    def test_the_grain_each_language_was_read_at_is_the_document_s_own_answer(self):
        """Written into the renderer, it was a claim about this tool rather than this run.

        The page then said "both halves of it, java" over a run of one source root, and
        described a TypeScript grain on a page with no TypeScript on it — the first
        sentence a reader meets, on a page whose whole claim is that what it says can be
        checked against the document it carries.
        """
        self.assertEqual(
            [
                {"language": "java", "says": javasource.GRAIN},
                {"language": "typescript", "says": typescriptsource.GRAIN},
            ],
            self.document["source"]["readAt"],
        )
        for entry in self.document["source"]["readAt"]:
            self.assertIn(entry["says"], self.rendered)

    def test_a_run_that_read_one_language_says_nothing_about_the_other(self):
        tree = self.tree("backend")
        tree.java("shop", "Till", "public class Till {\n    public void ring() {}\n}")

        document = graph.build([graph.source_root(tree.root)], scoring.load())
        rendered = page.render(document, graph.serialise(document)).decode("utf-8")

        self.assertEqual(
            [{"language": "java", "says": javasource.GRAIN}], document["source"]["readAt"]
        )
        self.assertNotIn(typescriptsource.GRAIN, rendered)

    def test_no_grain_sentence_is_written_into_the_renderer(self):
        """The guard on the fix: prose about a language, held in the one file that draws.

        A sentence written here is a sentence the page says whatever it was pointed at,
        which is how the lede came to describe both halves of an application on a run
        that read one.
        """
        for grain in (javasource.GRAIN, typescriptsource.GRAIN):
            self.assertNotIn(grain, page._SCRIPT)

    def test_the_longest_module_is_drawn_with_no_cost_when_a_rule_never_scored_it(self):
        """A rule can decline the longest file in a repository, and one here does.

        The section printed `costing null units of interface` through the guard on the
        leverage, which is a number nobody gave it wearing the shape of one.
        """
        tree = self.tree("records")
        tree.java(
            "shop", "Receipt",
            "public record Receipt(long cents) {\n" + "    // %s\n" * 40 % tuple(range(40)) + "}",
        )
        tree.java("shop", "Till", "public class Till {\n    public void ring() {}\n}")

        document = graph.build([graph.source_root(tree.root)], scoring.load())

        largest = document["scoring"]["largest"]
        self.assertEqual("shop.Receipt", largest["moduleId"])
        self.assertIsNone(largest["interfaceCost"])
        self.assertIsNone(largest["leverage"])

    def test_the_page_says_what_a_line_count_is_worth(self):
        self.assertIn("What a line count is worth here", self.rendered)
        self.assertIn(self.document["scoring"]["largest"]["moduleId"], self.rendered)

    def test_the_page_names_the_rule_that_declined_to_read_anything(self):
        self.assertIn("What was not read at all", self.rendered)
        self.assertIn(self.document["source"]["notRead"]["rule"], self.rendered)

    def test_nothing_on_the_page_is_worked_out_from_a_line_count(self):
        """`lines` is printed and never divided by, which is the whole claim of the page.

        Asserted against the renderer's own source rather than against the output, because
        the failure being guarded is a line count creeping into a measure — and that would
        be written here, in the one place that draws the shapes.
        """
        renderer = page._SCRIPT

        for arithmetic in re.finditer(r"\.lines\s*[/*+-]|[/*]\s*\w+\.lines", renderer):
            self.fail("the renderer does arithmetic on a line count: %r" % arithmetic.group(0))


def _committed():
    import json

    with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
        return json.loads(handle.read().decode("utf-8"))


def _as_json(document):
    import json

    return json.dumps(document, indent=2)


class AFanLineSaysWhatTheSourceSaysTest(SourceTreeTest):
    """Every line in a fan is a sentence a reader can check against the file it came from.

    The Java reading has refused to read a declaration as a call since it was written, and
    calls that the one failure it exists to make impossible. The TypeScript reading built
    the same three sets with none of those guards, so a construction was reported as a
    call and a member signature in a type declaration was reported as one too — the second
    inventing a fan line outright over a file whose truthful reach is nothing at all.
    """

    def modules(self, *sources):
        tree = self.tree("web")
        for name, body in sources:
            tree.typescript("", name, body)
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        self.assertEqual([], document["source"]["unparsed"])
        return {module["id"]: module for module in document["modules"]}

    def test_a_construction_of_a_named_import_builds_rather_than_calls(self):
        """`throw new SignInFailed()` calls nothing, and the evidence has to say so.

        The name has brackets after it and nothing but `new` in front, so it read as a
        bare call as well as a construction — and the fan said "calls SignInFailed, which
        this file imports from it" about a file that calls nothing. The module is still
        reached: building a collaborator is coordinating it, and that is what the
        construction reading already reports.
        """
        modules = self.modules(
            ("failed.ts", "export class SignInFailed extends Error {}"),
            ("use.ts",
             "import { SignInFailed } from './failed'\n"
             "export function go(ok: boolean): number {\n"
             "  if (!ok) {\n    throw new SignInFailed()\n  }\n  return 1\n}"),
        )

        self.assertEqual(
            [("web/failed", "builds one")],
            [(each["moduleId"], each["matched"])
             for each in modules["web/use"]["reach"]["reaches"]],
        )

    def test_a_flow_says_the_same_thing_about_that_line_as_the_fan_does(self):
        """A flow and a fan reading one line of source two ways is worse than either."""
        tree = self.tree("web")
        tree.typescript("", "failed.ts", "export class SignInFailed extends Error {}")
        tree.typescript(
            "", "use.ts",
            "import { SignInFailed } from './failed'\n"
            "export function go(ok: boolean): number {\n"
            "  if (!ok) {\n    throw new SignInFailed()\n  }\n  return 1\n}",
        )
        configuration = _committed()
        configuration["flows"] = [
            {
                "flow": "a refusal",
                "because": "the one flow this fixture has",
                "entryPoint": {"module": "web/use", "method": "go"},
            }
        ]
        rules = scoring.load(
            self.tree("rules").raw("scoring.json", _as_json(configuration))
        )

        document = graph.build([graph.source_root(tree.root)], rules)

        self.assertEqual(
            ["web/use", "web/failed"],
            [step["moduleId"] for step in document["flows"][0]["path"]],
        )
        self.assertIn("builds", document["flows"][0]["path"][1]["matched"])
        self.assertNotIn("calls", document["flows"][0]["path"][1]["matched"])

    def test_a_member_signature_in_an_interface_is_not_a_call(self):
        """The truthful reach of this file is nothing at all.

        `save` is written once, as a member signature inside a type declaration, and the
        file also imports a `save`. Read as a call it drew a fan line to `./repo` that the
        source says nothing about — a line invented rather than misattributed.
        """
        modules = self.modules(
            ("repo.ts", "export function save(id: string): void {}"),
            ("api.ts",
             "import { save } from './repo'\n"
             "export interface Api {\n  save(id: string): void\n}\n"
             "export function nothing(): number {\n  return 1\n}"),
        )

        self.assertEqual([], modules["web/api"]["reach"]["reaches"])

    def test_a_member_signature_in_a_type_alias_is_not_a_call_either(self):
        """The same shape written the other way TypeScript writes an object type."""
        modules = self.modules(
            ("repo.ts", "export function save(id: string): void {}"),
            ("api.ts",
             "import { save } from './repo'\n"
             "export type Api = {\n  save(id: string): void\n}\n"
             "export function nothing(): number {\n  return 1\n}"),
        )

        self.assertEqual([], modules["web/api"]["reach"]["reaches"])

    def test_a_method_written_with_a_body_is_a_declaration_too(self):
        """`save(id) { ... }` opens a brace where a call would have finished."""
        modules = self.modules(
            ("repo.ts", "export function save(id: string): void {}"),
            ("api.ts",
             "import { save } from './repo'\n"
             "class Store {\n  save(id: string) {\n    return id\n  }\n}\n"
             "export function nothing(): number {\n  return 1\n}"),
        )

        self.assertEqual([], modules["web/api"]["reach"]["reaches"])

    def test_a_call_the_body_really_writes_is_still_a_call(self):
        """The floor under all three: this must not have turned reach into nothing."""
        modules = self.modules(
            ("repo.ts", "export function save(id: string): void {}"),
            ("api.ts",
             "import { save } from './repo'\n"
             "export function store(id: string): void {\n  save(id)\n}"),
        )

        self.assertEqual(
            [("web/repo", "calls save, which this file imports from it")],
            [(each["moduleId"], each["matched"])
             for each in modules["web/api"]["reach"]["reaches"]],
        )

    def test_a_comparison_against_an_imported_name_builds_nothing(self):
        """`if (0 < Max && n > 1)` is arithmetic, and this file builds nothing at all.

        Whether a `<` opens a tag is decided by what stands in front of it, and a value
        standing there means it is an operator on that value. A name was asked about and a
        closing bracket was, and a *number* was neither — so a comparison against an
        imported constant, which is ordinary code, put a construction of that constant in
        the fan of a file that constructs nothing.
        """
        modules = self.modules(
            ("limits.ts", "export const Max = 10"),
            ("f.tsx",
             "import { Max } from './limits'\n"
             "export function F(n: number): number {\n"
             "  if (0 < Max && n > 1) {\n    return 1\n  }\n  return 0\n}"),
        )

        self.assertEqual([], modules["web/f"]["reach"]["reaches"])

    def test_an_element_of_that_same_name_is_still_built(self):
        """The floor under it: a `<` with no value in front of it still opens a tag."""
        modules = self.modules(
            ("limits.tsx", "export function Max(): JSX.Element {\n  return <i />\n}"),
            ("f.tsx",
             "import { Max } from './limits'\n"
             "export function F(): JSX.Element {\n  return <Max />\n}"),
        )

        self.assertEqual(
            [("web/limits", "builds one")],
            [(each["moduleId"], each["matched"])
             for each in modules["web/f"]["reach"]["reaches"]],
        )

    def test_a_reserved_word_left_in_front_of_a_dot_is_not_a_receiver(self):
        """`return /x/.test(s)` calls nothing on anything called `return`.

        A construct this reading masks leaves the characters it stood for blank, so the
        word in front of the regular expression ended up in front of the dot with a gap
        between them. Nothing can be imported under the name `return`, so this one drew no
        line — but the same shape after any other masked construct attaches a real name to
        a call the source never wrote on it.
        """
        tree = self.tree("web")
        tree.typescript(
            "", "till.ts",
            "export function f(s: string): boolean {\n  return /x/.test(s)\n}",
        )
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        self.assertEqual([], document["source"]["unparsed"])
        read = typescriptsource.parse(
            open(os.path.join(tree.root, "till.ts"), encoding="utf-8").read(),
            "till.ts",
            graph.source_root(tree.root),
        )

        self.assertEqual((), read.top_level[0].receivers)

    def test_a_real_receiver_is_still_read(self):
        """The floor under it: what really is called on a name still draws its line."""
        modules = self.modules(
            ("api.ts", "export function one(): number {\n  return 1\n}"),
            ("uses.ts",
             "import * as api from './api'\n"
             "export function make(): number {\n  return api.one()\n}"),
        )

        self.assertEqual(
            ["web/api"],
            [each["moduleId"] for each in modules["web/uses"]["reach"]["reaches"]],
        )

    def test_an_async_arrow_reads_no_call_to_anything_called_async(self):
        """`async` is a word a function is written with, not a function.

        Every other word of its kind — `await`, `typeof`, `void`, `catch` — is already
        held out of what a bracket after a name means. This one was not, so an async arrow
        drew a fan line in any file that also imported something called `async`, which is
        legal: the word is only contextually reserved.
        """
        tree = self.tree("web")
        tree.typescript(
            "", "till.ts",
            "export const go = async (id: string): Promise<number> => {\n  return 1\n}",
        )
        read = typescriptsource.parse(
            open(os.path.join(tree.root, "till.ts"), encoding="utf-8").read(),
            "till.ts",
            graph.source_root(tree.root),
        )

        self.assertNotIn("async", read.top_level[0].called)

    def test_the_page_names_no_floor_this_reading_does_not_have(self):
        """A gap the page admits to and does not have devalues the ones it does.

        The fan paragraph listed an import of a directory — `./components`, where the
        module is the `index` inside it — among the readings that leave a fan shorter than
        the source. It is followed, and has been since imports were resolved at all.
        """
        tree = self.tree("web2")
        tree.typescript("components", "index.ts", "export function Card(): number {\n  return 1\n}")
        tree.typescript(
            "", "App.ts",
            "import { Card } from './components'\n"
            "export function go(): number {\n  return Card()\n}",
        )
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        by_id = {module["id"]: module for module in document["modules"]}
        rendered = page.render(document, graph.serialise(document)).decode("utf-8")

        self.assertEqual(
            ["web2/components/index"],
            [each["moduleId"] for each in by_id["web2/App"]["reach"]["reaches"]],
        )
        self.assertNotIn("an import of a directory rather than of a file", rendered)


class ADeclarationIsReadUnderTheNameItWasGivenTest(SourceTreeTest):
    """What a module declares is named on its card, so every name there has to be one.

    The word after `type`, `interface`, `enum` or `class` was taken for a name whatever it
    was, and three ordinary spellings write something else there. The card then named a
    type called `{` that a reader can go looking for and will never find, which is the
    objection this ticket has been sent back for twice in other places.
    """

    def declared_by(self, name, body):
        tree = self.tree("web")
        tree.typescript("", name, body)
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        self.assertEqual([], document["source"]["unparsed"])
        module = {each["id"]: each for each in document["modules"]}["web/" + name.split(".")[0]]
        return module["nested"]

    def test_a_type_only_re_export_names_no_type_on_the_card(self):
        self.assertEqual([], self.declared_by("till.ts", "export type { Deposit } from './api'\n"))

    def test_a_type_only_star_re_export_names_no_type_on_the_card(self):
        """The `*` a generator is written with belongs to `function` and to nothing else.

        Offered to every keyword, it made `export type * from './other'` read as a
        declaration of a type called `from`.
        """
        self.assertEqual([], self.declared_by("till.ts", "export type * from './other'\n"))

    def test_a_default_class_with_no_name_names_no_type_on_the_card(self):
        self.assertEqual(
            [], self.declared_by("till.ts", "export default class {\n  ring(): number {\n    return 1\n  }\n}\n")
        )

    def test_a_default_class_written_with_a_supertype_names_no_type_either(self):
        self.assertEqual(
            [],
            self.declared_by(
                "till.ts", "class Base {}\nexport default class extends Base {}\n"
            )[1:],
        )

    def test_a_generator_function_is_still_read_under_its_own_name(self):
        """The floor under the lookbehind: `function* load()` is a function called `load`."""
        tree = self.tree("web")
        tree.typescript(
            "", "till.ts",
            "export function* load(id: number): Generator<number> {\n  yield id\n}",
        )
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        module = {each["id"]: each for each in document["modules"]}["web/till"]

        self.assertEqual(
            ["load"], [method["name"] for method in module["interface"]["methods"]]
        )

    def test_a_const_enum_is_a_type_rather_than_a_binding_called_enum(self):
        """`export const enum Direction { Up }` declares a type and assigns nothing.

        Read as a `const` binding, the name after the keyword was the word `enum`, the
        type was named nowhere, and the log carried a line about an export nobody wrote.
        """
        self.assertEqual(
            ["Direction"],
            self.declared_by("till.ts", "export const enum Direction { Up, Down }\n"),
        )

    def test_a_type_that_really_is_declared_is_still_named_on_the_card(self):
        """The floor under all four: a named declaration still reaches the card."""
        self.assertEqual(
            ["Deposit"],
            self.declared_by("till.ts", "export type Deposit = { cents: number }\n"),
        )

    def test_every_name_the_card_carries_is_one_a_reader_could_look_up(self):
        """The property behind the four fixtures above, over the real frontend."""
        document = graph.build([graph.source_root(FRONTEND_SOURCE)], scoring.load())

        for module in document["modules"]:
            for name in module["nested"]:
                self.assertRegex(name, r"^[A-Za-z_$][\w$]*$", module["id"])

    def test_each_one_is_named_in_the_log_rather_than_dropped(self):
        """`_decline`'s docstring calls that log the complete list of what was skipped."""
        tree = self.tree("web")
        tree.typescript("", "till.ts", "export type { Deposit } from './api'\n")

        with self.assertLogs("module_depth_map.typescriptsource", level="DEBUG") as logged:
            graph.build([graph.source_root(tree.root)], scoring.load())

        self.assertIn("export not read as a method", "\n".join(logged.output))


class ADeclarationListIsSplitWhereTheSourceSplitsItTest(SourceTreeTest):
    """A `<` inside a declarator's value is a bracket only where it is one.

    Read as a bracket outright, a comparison left the depth above zero and every declarator
    after it was lost. Read as no bracket at all, the comma inside a type argument list
    split a declarator the source never wrote. The name in front of it settles which, and
    the end of the declaration rather than the end of the line is how far its partner may
    be looked for.
    """

    def read(self, body):
        tree = self.tree("web")
        tree.typescript("", "till.ts", body)
        with self.assertLogs("module_depth_map.typescriptsource", level="DEBUG") as logged:
            document = graph.build([graph.source_root(tree.root)], scoring.load())
        self.assertEqual([], document["source"]["unparsed"])
        module = {each["id"]: each for each in document["modules"]}["web/till"]
        return module, "\n".join(logged.output)

    def test_a_wrapped_type_argument_list_declines_no_export_the_file_does_not_hold(self):
        """There is no export called `number` in this file, and the log said there was.

        `q` was measured either way and no number moved, so this is what a reader sees
        rather than what anything counts — but the log `_decline` calls the complete list
        of what was skipped is only worth reading while every line in it is true.
        """
        module, said = self.read(
            "export const p = new Map<\n  string,\n  number\n>(), q = (x: number): number => x\n"
        )

        self.assertEqual(
            ["q"], [method["name"] for method in module["interface"]["methods"]]
        )
        self.assertNotIn("export=number", said)

    def test_an_index_signature_in_an_annotation_declines_no_bracket_either(self):
        module, said = self.read(
            "export const x: { [k: string]: number } = {}, y = (n: number): number => n\n"
        )

        self.assertEqual(
            ["y"], [method["name"] for method in module["interface"]["methods"]]
        )
        self.assertNotIn("export=(", said)

    def test_a_comparison_still_leaves_the_declarator_after_it_readable(self):
        """The floor this rule was written for, held while the rule changed under it."""
        module, _ = self.read("export const flag = 1 < 2, b = (x: number): number => x\n")

        self.assertEqual(
            ["b"], [method["name"] for method in module["interface"]["methods"]]
        )

    def test_a_type_argument_list_on_one_line_is_still_one_declarator(self):
        """And the other floor: the comma inside it splits nothing."""
        module, said = self.read(
            "export const a = new Map<string, number>(), b = (x: number): number => x\n"
        )

        self.assertEqual(
            ["b"], [method["name"] for method in module["interface"]["methods"]]
        )
        self.assertIn("export=a", said)

    def test_a_declarator_with_nothing_assigned_is_still_declined_by_name(self):
        module, said = self.read("export let a, b\n")

        self.assertIn("export=a", said)
        self.assertIn("export=b", said)


class AConstructionKeepsThePlaceTheSourceGaveItTest(SourceTreeTest):
    """The order a flow is walked in is the order the source evaluates its calls.

    A construction written with no argument list has no brackets to balance, and the span
    was measured with a bracket matcher all the same — so it ran on to the *next* call's
    brackets, the construction was read as enclosing the call written after it, and the two
    came back the wrong way round. The Java side has had a reading for exactly this since
    it was written; this one called the bracket matcher directly.
    """

    def path_through(self, *sources):
        tree = self.tree("web")
        for name, body in sources:
            tree.typescript("", name, body)
        configuration = _committed()
        configuration["flows"] = [
            {
                "flow": "a sale",
                "because": "the one flow this fixture has",
                "entryPoint": {"module": "web/till", "method": "ring"},
            }
        ]
        rules = scoring.load(
            self.tree("rules").raw("scoring.json", _as_json(configuration))
        )
        document = graph.build([graph.source_root(tree.root)], rules)
        self.assertEqual([], document["source"]["unparsed"])
        return [step["moduleId"] for step in document["flows"][0]["path"]]

    def test_a_construction_with_no_brackets_keeps_the_order_the_source_wrote(self):
        self.assertEqual(
            ["web/till", "web/thing", "web/other"],
            self.path_through(
                ("thing.ts", "export class Thing {}"),
                ("other.ts", "export function other(n: number): number {\n  return n\n}"),
                ("till.ts",
                 "import { Thing } from './thing'\n"
                 "import { other } from './other'\n"
                 "export function ring(): number {\n"
                 "  const t = new Thing\n  other(1)\n  return 1\n}"),
            ),
        )

    def test_the_same_construction_with_brackets_reads_the_same_way(self):
        """The floor: the spelling a writer happens to prefer must not move the order."""
        self.assertEqual(
            ["web/till", "web/thing", "web/other"],
            self.path_through(
                ("thing.ts", "export class Thing {}"),
                ("other.ts", "export function other(n: number): number {\n  return n\n}"),
                ("till.ts",
                 "import { Thing } from './thing'\n"
                 "import { other } from './other'\n"
                 "export function ring(): number {\n"
                 "  const t = new Thing()\n  other(1)\n  return 1\n}"),
            ),
        )

    def test_a_call_inside_anothers_arguments_is_still_evaluated_first(self):
        """And the reading this must not have broken, which is why the span is measured."""
        self.assertEqual(
            ["web/till", "web/thing", "web/other"],
            self.path_through(
                ("thing.ts", "export class Thing {}"),
                ("other.ts", "export function other(n: unknown): number {\n  return 1\n}"),
                ("till.ts",
                 "import { Thing } from './thing'\n"
                 "import { other } from './other'\n"
                 "export function ring(): number {\n"
                 "  return other(new Thing())\n}"),
            ),
        )


class ADefaultRootThatIsNotThereIsSkippedTest(SourceTreeTest):
    """A directory this tool chose is not worth refusing a whole run for.

    A `--source` an operator typed is: they said to read it, and a page drawn from what was
    left answers a question nobody asked. A default is different — before there were two of
    them the single default always existed — and refusing for one meant a repository with
    only a backend in it got neither output written, over a frontend nobody said was there.
    """

    def setUp(self):
        super().setUp()
        self.here = os.getcwd()
        self.addCleanup(os.chdir, self.here)
        self.backend = os.path.join(self.scratch, "backend", "src", "main", "java")
        os.makedirs(os.path.join(self.backend, "shop"))
        with open(os.path.join(self.backend, "shop", "Till.java"), "w", encoding="utf-8") as handle:
            handle.write("package shop;\n\npublic class Till {\n"
                         "    public long ring(long id) { return id; }\n}\n")

    def run_with_no_source(self):
        os.chdir(self.scratch)
        graph_path = os.path.join(self.scratch, "out", "graph.json")
        page_path = os.path.join(self.scratch, "out", "page.html")
        code = cli.main(["--graph", graph_path, "--page", page_path])
        return code, graph_path, page_path

    def test_the_default_root_that_is_there_is_still_drawn(self):
        code, graph_path, page_path = self.run_with_no_source()

        self.assertEqual(0, code)
        written = json.loads(bytes_of(graph_path).decode("utf-8"))
        self.assertEqual(["backend/src/main/java"], written["source"]["roots"])
        self.assertEqual(
            ["shop.Till"], [module["id"] for module in written["modules"]]
        )
        self.assertTrue(bytes_of(page_path))

    def test_the_document_says_which_default_was_skipped_and_why(self):
        _, graph_path, _ = self.run_with_no_source()

        written = json.loads(bytes_of(graph_path).decode("utf-8"))

        self.assertEqual(
            ["frontend/src"],
            [entry["root"] for entry in written["source"]["rootsNotRead"]],
        )
        self.assertTrue(written["source"]["rootsNotRead"][0]["reason"].strip())

    def test_the_page_says_it_too(self):
        """Half an application missing from a picture of one is worth a sentence."""
        _, _, page_path = self.run_with_no_source()

        rendered = bytes_of(page_path).decode("utf-8")

        self.assertIn("frontend/src", rendered)
        self.assertIn("What was not read at all", rendered)

    def test_the_run_says_it_at_info_rather_than_passing_over_it(self):
        os.chdir(self.scratch)
        with self.assertLogs("module_depth_map", level="INFO") as logged:
            cli.main([
                "--graph", os.path.join(self.scratch, "out", "graph.json"),
                "--page", os.path.join(self.scratch, "out", "page.html"),
            ])

        said = "\n".join(logged.output)
        self.assertIn("default source not read", said)
        self.assertIn("frontend/src", said)

    def test_a_directory_the_operator_typed_is_still_refused_for(self):
        os.chdir(self.scratch)
        with self.assertLogs("module_depth_map.cli", level="WARNING") as logged:
            code = cli.main([
                "--source", self.backend,
                "--source", os.path.join(self.scratch, "nowhere"),
                "--graph", os.path.join(self.scratch, "out", "graph.json"),
                "--page", os.path.join(self.scratch, "out", "page.html"),
            ])

        self.assertEqual(2, code)
        self.assertIn("no such source directory", "\n".join(logged.output))
        self.assertFalse(os.path.exists(os.path.join(self.scratch, "out", "graph.json")))

    def test_a_run_with_no_source_at_all_is_still_refused(self):
        """Every default missing is no source at all, and a page of nothing is not an answer."""
        empty = os.path.join(self.scratch, "empty")
        os.makedirs(empty)
        os.chdir(empty)

        with self.assertLogs("module_depth_map.cli", level="WARNING") as logged:
            code = cli.main([
                "--graph", os.path.join(empty, "graph.json"),
                "--page", os.path.join(empty, "page.html"),
            ])

        self.assertEqual(2, code)
        self.assertIn("no such source directory", "\n".join(logged.output))


class ThreeExportsAreFailedByNameOnPurposeTest(SourceTreeTest):
    """The whole of the exception to "legal TypeScript is never failed", counted.

    The README said two of them and the reading holds three: `export module Foo {}` is the
    older spelling of `export namespace Foo {}` and is failed for the same reason. In a
    file whose standard is that a reader can check every sentence against the source, a
    count is the cheapest thing there is to falsify.
    """

    def failure_for(self, word, body):
        tree = self.tree("web-" + word)
        tree.typescript("", "till.ts", body)
        document = graph.build([graph.source_root(tree.root)], scoring.load())
        self.assertEqual(1, len(document["source"]["unparsed"]))
        return document["source"]["unparsed"][0]["reason"]

    def test_each_of_the_three_is_failed_with_the_word_that_stopped_it(self):
        for word, body in (
            ("declare", "export declare function ring(id: number): number\n"),
            ("namespace", "export namespace Shop {\n  export const a = 1\n}\n"),
            ("module", "export module Shop {\n  export const a = 1\n}\n"),
        ):
            with self.subTest(word=word):
                self.assertIn(word, self.failure_for(word, body))

    def test_the_reading_names_those_three_and_no_others(self):
        self.assertEqual(
            ("declare", "namespace", "module"), typescriptsource._NOT_READ_HERE
        )

    def test_the_readme_names_the_same_three(self):
        """The prose and the tuple, held together, since a count is what drifted."""
        beside_the_tool = os.path.join(
            os.path.dirname(typescriptsource.__file__), "README.md"
        )
        with open(beside_the_tool, encoding="utf-8") as handle:
            readme = handle.read()

        self.assertIn("Three exports are still failed by name on purpose", readme)
        for word in typescriptsource._NOT_READ_HERE:
            self.assertIn("`export %s`" % word, readme)

    def test_the_count_of_what_comes_from_the_java_reading_is_the_count_written_down(self):
        """The sentence over the import list says how many names are in it.

        A file whose whole standard is that a reader can check every sentence against the
        source has no business getting its own arithmetic wrong, and this one said ten
        over a list of thirteen. Held here so that adding a fourteenth name reddens the
        suite rather than quietly making the sentence false again.
        """
        with open(typescriptsource.__file__, encoding="utf-8") as handle:
            source = handle.read()
        imported = re.search(
            r"from \.javasource import \(\n(.*?)\n\)\n", source, re.S
        )
        names = [line.strip().rstrip(",") for line in imported.group(1).splitlines()]
        shapes = [name for name in names if name[0].isupper()]

        self.assertEqual(14, len(names))
        self.assertEqual(6, len(shapes))
        self.assertIn("Fourteen names come from the Java reading", source)
        self.assertIn("Six are the", source)
        self.assertIn("The other eight are punctuation", source)
