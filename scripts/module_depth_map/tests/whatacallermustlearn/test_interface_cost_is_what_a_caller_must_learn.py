"""Interface cost, stated one rule at a time.

Each fixture is a source tree built to produce one number, so the suite reads as the
statement of the scoring rules rather than as a set of examples. Nothing here is asserted
against the application's own code: a rule established against a module somebody is still
writing changes meaning every time they save.
"""

from ... import graph
from ..support.sourcetrees import SourceTreeTest


class SourceOfKnownShapeTest(SourceTreeTest):
    """A test that turns a handful of Java files into modules and reads their interfaces."""

    def modules(self, *sources):
        tree = self.tree("fixture")
        for name, body in sources:
            tree.java("shop.till", name, body)

        document = graph.build([graph.java_root(tree.root)])

        self.assertEqual([], document["source"]["unparsed"])
        return {module["name"]: module for module in document["modules"]}

    def cost_of(self, body):
        return self.modules(("Till", body))["Till"]["interface"]["cost"]


class EveryMethodACallerCanReachIsCountedTest(SourceOfKnownShapeTest):

    def test_one_method_taking_and_handing_back_nothing_costs_one(self):
        self.assertEqual(1, self.cost_of("public class Till {\n    public void ring() {}\n}"))

    def test_three_methods_cost_three(self):
        self.assertEqual(
            3,
            self.cost_of(
                "public class Till {\n"
                "    public void ring() {}\n"
                "    public void open() {}\n"
                "    public void close() {}\n"
                "}"
            ),
        )

    def test_a_method_nobody_outside_can_reach_costs_a_caller_nothing(self):
        self.assertEqual(
            1,
            self.cost_of(
                "public class Till {\n"
                "    public void ring() {}\n"
                "    private void countTheDrawer() {}\n"
                "}"
            ),
        )

    def test_a_method_the_package_can_reach_is_part_of_the_interface(self):
        """Package-private is reachable from outside the module, which is what is measured.

        The module is the class. A method its neighbours can call is a method somebody
        has to learn, whether or not the whole application can call it.
        """
        module = self.modules(("Till", "class Till {\n    void ring() {}\n}"))["Till"]

        self.assertEqual(1, module["interface"]["cost"])
        self.assertEqual(["package-private"], [m["visibility"] for m in module["interface"]["methods"]])

    def test_an_interface_method_written_without_a_modifier_is_public(self):
        module = self.modules(("Shelf", "interface Shelf {\n    void restock();\n}"))["Shelf"]

        self.assertEqual(["public"], [m["visibility"] for m in module["interface"]["methods"]])

    def test_a_constructor_is_not_a_method_a_caller_learns_here(self):
        """How a module is built is the framework's business; what it offers is the caller's."""
        self.assertEqual(
            1,
            self.cost_of(
                "public class Till {\n"
                "    private final long id;\n"
                "    public Till(long id, String name, java.math.BigDecimal float_) {\n"
                "        this.id = id;\n"
                "    }\n"
                "    public void ring() {}\n"
                "}"
            ),
        )

    def test_nothing_written_inside_a_method_is_read_as_part_of_the_interface(self):
        """A call, a local class and a lambda all look like declarations and are not."""
        self.assertEqual(
            1,
            self.cost_of(
                "public class Till {\n"
                "    public void ring() {\n"
                "        Runnable later = () -> { open(); };\n"
                "        class Row { void hidden() {} }\n"
                "        if (later != null) { later.run(); }\n"
                "    }\n"
                "    private void open() {}\n"
                "}"
            ),
        )

    def test_a_field_is_not_a_method_however_it_is_initialised(self):
        self.assertEqual(
            1,
            self.cost_of(
                "public class Till {\n"
                "    static final String NAME = String.valueOf(1);\n"
                "    static final int[] SIZES = {1, 2};\n"
                "    public void ring() {}\n"
                "}"
            ),
        )


class EveryParameterOfThoseMethodsIsCountedTest(SourceOfKnownShapeTest):

    def test_a_method_with_two_parameters_costs_the_call_and_both_of_them(self):
        self.assertEqual(
            3, self.cost_of("public class Till {\n    public void ring(long id, int cents) {}\n}")
        )

    def test_a_parameter_carrying_an_annotation_is_still_one_parameter(self):
        self.assertEqual(
            2,
            self.cost_of(
                "public class Till {\n"
                "    public void ring(@Named(\"id\") final long id) {}\n"
                "}"
            ),
        )

    def test_a_generic_parameter_is_one_parameter_however_many_commas_it_holds(self):
        module = self.modules(
            ("Till", "import java.util.Map;\npublic class Till {\n"
                     "    public void ring(Map<String, Long> takings) {}\n}")
        )["Till"]

        self.assertEqual(
            [["Map<String, Long>"]], [m["parameters"] for m in module["interface"]["methods"]]
        )


class EveryTypeCrossingTheSeamIsCountedTest(SourceOfKnownShapeTest):

    def test_a_type_this_application_invented_is_a_type_the_caller_has_to_learn(self):
        module = self.modules(
            ("Receipt", "public class Receipt {}"),
            ("Till", "public class Till {\n    public Receipt ring() { return null; }\n}"),
        )["Till"]

        self.assertEqual(3, module["interface"]["cost"])
        self.assertEqual(
            [{"name": "Receipt", "mustBeLearned": True}],
            module["interface"]["typesCrossingTheSeam"],
        )

    def test_a_type_is_learned_once_however_many_methods_hand_it_over(self):
        self.assertEqual(
            4,
            self.cost_of(
                "public class Till {\n"
                "    public Receipt ring() { return null; }\n"
                "    public Receipt reprint() { return null; }\n"
                "}"
            ),
        )

    def test_a_type_inside_a_generic_crosses_the_seam_as_much_as_one_that_is_not(self):
        module = self.modules(
            ("Till", "import java.util.List;\npublic class Till {\n"
                     "    public List<Receipt> takings() { return null; }\n}")
        )["Till"]

        self.assertEqual(
            [
                {"name": "List", "mustBeLearned": False},
                {"name": "Receipt", "mustBeLearned": True},
            ],
            module["interface"]["typesCrossingTheSeam"],
        )
        self.assertEqual(3, module["interface"]["cost"])

    def test_a_type_a_parameter_carries_crosses_the_seam_like_one_a_return_does(self):
        self.assertEqual(4, self.cost_of("public class Till {\n    public void ring(Receipt it) {}\n}"))

    def test_a_type_written_with_its_package_is_the_same_type_as_one_without(self):
        self.assertEqual(
            5,
            self.cost_of(
                "public class Till {\n"
                "    public Receipt ring() { return null; }\n"
                "    public void reprint(shop.till.Receipt it) {}\n"
                "}"
            ),
        )


class ADomainTypeCostsMoreThanAPrimitiveTest(SourceOfKnownShapeTest):
    """The reason interface cost is not a method count.

    Both modules below present one method with no parameters. One hands back a number
    every caller already knows how to read; the other hands back a type they have to go
    and learn before the answer means anything.
    """

    def test_handing_back_a_domain_type_costs_more_than_handing_back_a_primitive(self):
        modules = self.modules(
            ("Plain", "public class Plain {\n    public long takings() { return 0; }\n}"),
            ("Rich", "public class Rich {\n    public Receipt takings() { return null; }\n}"),
        )

        self.assertEqual(1, modules["Plain"]["interface"]["cost"])
        self.assertEqual(3, modules["Rich"]["interface"]["cost"])
        self.assertLess(
            modules["Plain"]["interface"]["cost"], modules["Rich"]["interface"]["cost"]
        )

    def test_the_type_a_caller_already_knows_is_told_apart_from_the_one_they_do_not(self):
        module = self.modules(
            ("Till", "import java.math.BigDecimal;\npublic class Till {\n"
                     "    public BigDecimal owed(Receipt it) { return null; }\n}")
        )["Till"]

        self.assertEqual(
            [
                {"name": "BigDecimal", "mustBeLearned": False},
                {"name": "Receipt", "mustBeLearned": True},
            ],
            module["interface"]["typesCrossingTheSeam"],
        )


class WhatEachMethodCostsIsSaidPerMethodTest(SourceOfKnownShapeTest):
    """The number on a module is checkable, because the parts it was added up from are there."""

    def test_each_method_carries_the_call_and_its_own_parameters(self):
        module = self.modules(
            ("Till", "public class Till {\n"
                     "    public void ring(long id, int cents) {}\n"
                     "    public void open() {}\n}")
        )["Till"]

        self.assertEqual(
            [
                {"name": "open", "visibility": "public", "parameters": [], "returns": "void",
                 "cost": 1},
                {"name": "ring", "visibility": "public", "parameters": ["long", "int"],
                 "returns": "void", "cost": 3},
            ],
            module["interface"]["methods"],
        )

    def test_the_methods_and_the_types_are_emitted_in_a_stable_sorted_order(self):
        module = self.modules(
            ("Till", "public class Till {\n"
                     "    public Receipt zebra() { return null; }\n"
                     "    public Aisle ant() { return null; }\n}")
        )["Till"]

        self.assertEqual(["ant", "zebra"], [m["name"] for m in module["interface"]["methods"]])
        self.assertEqual(
            ["Aisle", "Receipt"],
            [t["name"] for t in module["interface"]["typesCrossingTheSeam"]],
        )
