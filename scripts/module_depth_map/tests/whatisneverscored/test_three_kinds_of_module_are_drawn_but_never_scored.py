"""The three exclusions, one fixture apiece, and the rule each of them is attributable to.

Every fixture here is a source tree built to be excluded for exactly one reason, so the
suite reads as the statement of the exclusion rules rather than as a list of things this
application happens to contain. The rules themselves are read from the configuration file
beside the tool, which is the file a reader who disagrees with an exclusion would edit.
"""

import json

from ... import graph, scoring
from ..support.sourcetrees import SourceTreeTest


class SourceOfKnownShapeTest(SourceTreeTest):
    """A test that turns a handful of Java files into modules and reads what was excluded."""

    def modules(self, *sources):
        tree = self.tree("fixture")
        for name, body in sources:
            tree.java("shop.till", name, body)

        self.document = graph.build([graph.java_root(tree.root)])

        self.assertEqual([], self.document["source"]["unparsed"])
        return {module["name"]: module for module in self.document["modules"]}

    def excluded_by(self, *sources):
        return {
            name: module["excludedBy"] and module["excludedBy"]["rule"]
            for name, module in self.modules(*sources).items()
        }


class AValueThatOnlyCarriesDataIsNeverScoredTest(SourceOfKnownShapeTest):
    """A record and an enum hide nothing: their interface is their content."""

    def test_a_record_is_excluded_as_a_data_carrier(self):
        self.assertEqual(
            {"Receipt": "data carrier"},
            self.excluded_by(("Receipt", "public record Receipt(long cents, String iban) {}")),
        )

    def test_an_enum_is_excluded_as_a_data_carrier(self):
        self.assertEqual(
            {"Aisle": "data carrier"},
            self.excluded_by(("Aisle", "public enum Aisle {\n    LEFT,\n    RIGHT\n}")),
        )

    def test_a_record_that_writes_a_method_of_its_own_is_still_a_data_carrier(self):
        """The rule is what the declaration is, not how much was typed inside it.

        A record with a computed accessor is still a thing whose interface is the values
        it carries, and ranking it beside the modules that hide something would be the
        false positive the exclusion exists to prevent.
        """
        self.assertEqual(
            {"Receipt": "data carrier"},
            self.excluded_by(
                ("Receipt", "public record Receipt(long cents) {\n"
                            "    public long inEuros() { return cents / 100; }\n}")
            ),
        )

    def test_a_class_that_carries_values_is_not_excluded_by_this_rule(self):
        """The rule reaches what the language marks, and no further.

        A class holding two fields may well be a data carrier in spirit. The rule does not
        say so, because the only way to tell would be a judgement rather than a fact, and
        a rule that guesses is one nobody can argue with.
        """
        self.assertEqual(
            {"Basket": None},
            self.excluded_by(("Basket", "public class Basket {\n    public long size() { return 0; }\n}")),
        )


class ARepositoryNobodyWroteAnImplementationForIsNeverScoredTest(SourceOfKnownShapeTest):

    def test_an_interface_extending_a_spring_data_repository_is_excluded(self):
        self.assertEqual(
            {"Tills": "generated repository"},
            self.excluded_by(
                ("Tills", "public interface Tills extends JpaRepository<Till, Long> {\n"
                          "    Optional<Till> findByIban(String iban);\n}")
            ),
        )

    def test_the_repository_is_recognised_however_it_is_written(self):
        """Each of the interfaces the rule names, qualified or not, generic or not."""
        self.assertEqual(
            {
                "Crud": "generated repository",
                "Paging": "generated repository",
                "Plain": "generated repository",
                "Qualified": "generated repository",
            },
            self.excluded_by(
                ("Crud", "public interface Crud extends CrudRepository<Till, Long> {}"),
                ("Paging", "public interface Paging extends PagingAndSortingRepository<Till, Long> {}"),
                ("Plain", "public interface Plain extends Repository<Till, Long> {}"),
                ("Qualified", "public interface Qualified extends\n"
                              "        org.springframework.data.jpa.repository.JpaRepository<Till, Long> {}"),
            ),
        )

    def test_an_interface_this_repository_implements_itself_is_scored(self):
        """The exclusion is about an implementation nobody wrote, not about interfaces.

        An interface somebody has to write a class for is a seam like any other, and the
        cost of learning it is a real cost.
        """
        self.assertEqual(
            {"Shelf": None},
            self.excluded_by(("Shelf", "public interface Shelf {\n    void restock(long id);\n}")),
        )

    def test_a_class_extending_a_repository_is_not_excluded_by_this_rule(self):
        """A class has an implementation in it, whatever it is built on."""
        self.assertEqual(
            {"Tills": None},
            self.excluded_by(
                ("Tills", "public class Tills extends JpaRepository {\n    public void save() {}\n}")
            ),
        )


class TheEntryPointIsNeverScoredTest(SourceOfKnownShapeTest):

    def test_the_class_marked_as_the_application_is_excluded_as_the_entry_point(self):
        self.assertEqual(
            {"ShopApplication": "entry point"},
            self.excluded_by(
                ("ShopApplication", "@SpringBootApplication\npublic class ShopApplication {\n"
                                    "    public static void main(String[] args) {}\n}")
            ),
        )

    def test_the_entry_point_is_found_when_the_annotation_carries_arguments(self):
        """An argument holding braces of its own is the ordinary way this is written.

        `@SpringBootApplication(scanBasePackages = {"shop"})` is a brace inside an
        annotation, and reading only as far back as the nearest brace lost the annotation
        entirely — so the entry point arrived as a module no rule had excluded and was
        scored like anything else, with nothing said about it.
        """
        self.assertEqual(
            {"ShopApplication": "entry point"},
            self.excluded_by(
                ("ShopApplication",
                 '@SpringBootApplication(scanBasePackages = {"shop"}, proxyBeanMethods = false)\n'
                 "public class ShopApplication {\n    public static void main(String[] args) {}\n}")
            ),
        )

    def test_the_entry_point_is_found_when_the_annotation_is_written_out_in_full(self):
        self.assertEqual(
            {"ShopApplication": "entry point"},
            self.excluded_by(
                ("ShopApplication",
                 "@org.springframework.boot.autoconfigure.SpringBootApplication\n"
                 "public class ShopApplication {\n    public static void main(String[] args) {}\n}")
            ),
        )

    def test_a_module_does_not_inherit_the_annotation_of_the_declaration_above_it(self):
        self.assertEqual(
            {"Till": None},
            self.excluded_by(
                ("Till", "public class Till {\n"
                         "    @SpringBootApplication\n"
                         "    private long id;\n"
                         "    public void ring() {}\n}")
            ),
        )

    def test_a_class_marked_with_something_else_is_scored(self):
        self.assertEqual(
            {"Till": None},
            self.excluded_by(
                ("Till", "@RestController\n@Transactional\npublic class Till {\n"
                         "    public void ring() {}\n}")
            ),
        )


class AnExcludedModuleIsStillDrawnTest(SourceOfKnownShapeTest):
    """Excluding a module from the scoring is not excluding it from the picture.

    The page is meant to show the whole application, not only the parts that were scored:
    a reader who cannot see the thirty data carriers cannot tell that they were left out
    on purpose from the fact that they were never there.
    """

    def source(self):
        return self.modules(
            ("Till", "public class Till {\n    public Receipt ring(long id) { return null; }\n}"),
            ("Receipt", "public record Receipt(long cents) {}"),
            ("Tills", "public interface Tills extends JpaRepository<Till, Long> {}"),
            ("ShopApplication", "@SpringBootApplication\npublic class ShopApplication {\n"
                                "    public static void main(String[] args) {}\n}"),
        )

    def test_an_excluded_module_is_in_the_graph_beside_the_scored_ones(self):
        modules = self.source()

        self.assertEqual(
            ["Receipt", "ShopApplication", "Till", "Tills"], sorted(modules)
        )

    def test_an_excluded_module_is_in_the_package_the_page_draws_it_under(self):
        self.source()

        self.assertEqual(
            [
                {
                    "name": "shop.till",
                    "moduleIds": [
                        "shop.till.Receipt",
                        "shop.till.ShopApplication",
                        "shop.till.Till",
                        "shop.till.Tills",
                    ],
                }
            ],
            self.document["packages"],
        )

    def test_an_excluded_module_has_no_cost_rather_than_a_cost_of_nothing(self):
        """Absent, not zero: it was never counted, not counted to nothing.

        A zero would put every data carrier at the bottom of a ranking it was deliberately
        kept out of, which is the reading the exclusion exists to prevent.
        """
        modules = self.source()

        self.assertIsNone(modules["Receipt"]["interface"]["cost"])
        self.assertIsNone(modules["Tills"]["interface"]["cost"])
        self.assertIsNone(modules["ShopApplication"]["interface"]["cost"])
        self.assertEqual(4, modules["Till"]["interface"]["cost"])

    def test_an_excluded_module_still_shows_what_the_rule_declined_to_measure(self):
        """The interface is read either way, so a reader can see what was not scored."""
        modules = self.source()

        self.assertEqual(
            ["cents"], [m["name"] for m in modules["Receipt"]["interface"]["methods"]]
        )

    def test_the_run_says_how_many_modules_were_scored_and_how_many_were_not(self):
        self.source()

        self.assertEqual(1, self.document["scoring"]["modulesScored"])
        self.assertEqual(3, self.document["scoring"]["modulesNeverScored"])


class NoModuleIsExcludedWithoutARuleThatSaysSoTest(SourceOfKnownShapeTest):
    """"Why was this ignored?" always has an answer a reader can point at."""

    def test_every_exclusion_names_the_rule_that_caused_it(self):
        modules = self.modules(
            ("Receipt", "public record Receipt(long cents) {}"),
            ("Tills", "public interface Tills extends JpaRepository<Till, Long> {}"),
            ("ShopApplication", "@SpringBootApplication\npublic class ShopApplication {}"),
        )

        self.assertEqual(
            {"data carrier", "entry point", "generated repository"},
            {module["excludedBy"]["rule"] for module in modules.values()},
        )

    def test_every_exclusion_names_the_fact_about_the_module_that_matched(self):
        """The rule and the evidence, so the reader can check the match rather than trust it."""
        modules = self.modules(
            ("Receipt", "public record Receipt(long cents) {}"),
            ("Tills", "public interface Tills extends JpaRepository<Till, Long> {}"),
            ("ShopApplication", "@SpringBootApplication\npublic class ShopApplication {}"),
        )

        self.assertEqual(
            {
                "Receipt": "kind is record",
                "ShopApplication": "annotated with SpringBootApplication",
                "Tills": "kind is interface, extends or implements JpaRepository",
            },
            {name: module["excludedBy"]["matched"] for name, module in modules.items()},
        )

    def test_a_module_no_rule_covers_carries_no_exclusion_at_all(self):
        modules = self.modules(("Till", "public class Till {\n    public void ring() {}\n}"))

        self.assertIsNone(modules["Till"]["excludedBy"])
        self.assertEqual(1, modules["Till"]["interface"]["cost"])

    def test_a_module_is_never_both_scored_and_excluded(self):
        modules = self.modules(
            ("Till", "public class Till {\n    public void ring() {}\n}"),
            ("Receipt", "public record Receipt(long cents) {}"),
        )

        for module in modules.values():
            self.assertEqual(
                module["excludedBy"] is None,
                module["interface"]["cost"] is not None,
                module["id"],
            )

    def test_the_first_rule_in_the_file_is_the_one_recorded_against_a_module(self):
        """Two rules can cover one module, and which one is named has to be answerable.

        The file's own order decides it, so a reader can see which by reading down the
        file rather than by working out what this code does with a tie.
        """
        rules = scoring.load(
            self.written_rules(
                {"rule": "first", "because": "it comes first", "when": {"kind": ["record"]}},
                {"rule": "second", "because": "it comes second", "when": {"kind": ["record"]}},
            )
        )
        tree = self.tree("fixture")
        tree.java("shop.till", "Receipt", "public record Receipt(long cents) {}")

        document = graph.build([graph.java_root(tree.root)], rules)

        self.assertEqual("first", document["modules"][0]["excludedBy"]["rule"])

    def written_rules(self, *exclusions):
        """A configuration file holding these exclusions and this tool's own weights."""
        with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
            document = json.loads(handle.read().decode("utf-8"))
        document["exclusions"] = list(exclusions)
        return self.tree("rules").raw("scoring.json", json.dumps(document))


class TheGraphCarriesTheRulesThatProducedItTest(SourceOfKnownShapeTest):
    """The page explains the score from the rules, not from anything written into it."""

    def test_every_rule_in_the_file_is_named_in_the_graph_with_what_it_excluded(self):
        self.modules(
            ("Receipt", "public record Receipt(long cents) {}"),
            ("Basket", "public record Basket(long size) {}"),
            ("Till", "public class Till {\n    public void ring() {}\n}"),
        )

        counted = {
            entry["rule"]: entry["modulesExcluded"]
            for entry in self.document["scoring"]["exclusions"]
        }
        self.assertEqual({"data carrier": 2, "entry point": 0, "generated repository": 0}, counted)

    def test_every_rule_carries_the_reason_it_was_written_for(self):
        self.modules(("Till", "public class Till {\n    public void ring() {}\n}"))

        for entry in self.document["scoring"]["exclusions"]:
            self.assertTrue(entry["because"].strip(), entry["rule"])

    def test_the_graph_names_the_file_the_rules_came_from(self):
        self.modules(("Till", "public class Till {\n    public void ring() {}\n}"))

        self.assertEqual(
            "scripts/module_depth_map/scoring.json", self.document["scoring"]["configuration"]
        )

    def test_the_rules_named_in_the_graph_are_the_rules_that_were_applied(self):
        """Nothing on the page comes from anywhere but the document, this included."""
        modules = self.modules(("Receipt", "public record Receipt(long cents) {}"))

        named = [entry["rule"] for entry in self.document["scoring"]["exclusions"]]
        self.assertIn(modules["Receipt"]["excludedBy"]["rule"], named)
