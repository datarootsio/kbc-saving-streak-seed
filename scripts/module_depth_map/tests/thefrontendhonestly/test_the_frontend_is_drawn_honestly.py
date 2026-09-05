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

import os
import re

from ... import cli, graph, languages, page, scoring, typescriptsource
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
