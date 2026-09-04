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

    def methods_of(self, name, body):
        """The interface of one module, by method name."""
        module = self.modules((name, body))[name]
        return {method["name"]: method for method in module["interface"]["methods"]}


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


class ATypeVariableIsNotATypeAnybodyLearnsTest(SourceOfKnownShapeTest):
    """A hole the caller fills with a type they already hold, not a type to go and read.

    Counted as a domain type it would price the letter, and every generic signature would
    read as asking more of a caller than the same signature without one does.
    """

    def test_a_type_variable_a_method_introduces_costs_nothing(self):
        module = self.modules(
            ("Till", "import java.util.List;\npublic class Till {\n"
                     "    public <T> T first(List<T> of) { return null; }\n}")
        )["Till"]

        self.assertEqual(
            [{"name": "List", "mustBeLearned": False}],
            module["interface"]["typesCrossingTheSeam"],
        )
        self.assertEqual(2, module["interface"]["cost"])

    def test_a_type_variable_the_module_introduces_costs_nothing(self):
        module = self.modules(
            ("Shelf", "public interface Shelf<T> {\n    T get(long id);\n    void put(T it);\n}")
        )["Shelf"]

        self.assertEqual(
            ["long", "void"], [t["name"] for t in module["interface"]["typesCrossingTheSeam"]]
        )
        self.assertEqual(4, module["interface"]["cost"])

    def test_a_bounded_type_variable_is_still_a_variable(self):
        module = self.modules(
            ("Till", "import java.util.List;\npublic class Till {\n"
                     "    public <T extends Comparable<T>> T largest(List<T> in) { return null; }\n}")
        )["Till"]

        self.assertEqual(
            ["List"], [t["name"] for t in module["interface"]["typesCrossingTheSeam"]]
        )

    def test_two_type_variables_are_both_holes(self):
        module = self.modules(
            ("Cache", "public interface Cache<K, V> {\n    V get(K key);\n}")
        )["Cache"]

        self.assertEqual([], module["interface"]["typesCrossingTheSeam"])
        self.assertEqual(2, module["interface"]["cost"])

    def test_a_type_of_the_same_name_as_no_variable_is_still_learned(self):
        """The rule is what the signature declared, not how short the name is."""
        module = self.modules(
            ("Till", "public class Till {\n    public T ring() { return null; }\n}")
        )["Till"]

        self.assertEqual(
            [{"name": "T", "mustBeLearned": True}],
            module["interface"]["typesCrossingTheSeam"],
        )
        self.assertEqual(3, module["interface"]["cost"])

    def test_the_method_that_declared_the_variable_is_the_only_one_it_is_a_hole_in(self):
        module = self.modules(
            ("Till", "import java.util.List;\npublic class Till {\n"
                     "    public <T> T first(List<T> of) { return null; }\n"
                     "    public T stored() { return null; }\n}")
        )["Till"]

        self.assertEqual(
            [{"name": "List", "mustBeLearned": False}, {"name": "T", "mustBeLearned": True}],
            module["interface"]["typesCrossingTheSeam"],
        )


class EveryBarIsDrawnOnOneScaleTest(SourceOfKnownShapeTest):
    """Two bars mean something side by side, and the scale they share is in the graph.

    The page multiplies each cost by its own width and divides by this one number, so a
    bar is checkable against the document it was drawn from rather than against
    arithmetic only the browser can do. The proportions themselves are a browser fact and
    were measured in one: the bars this suite pins are the numbers behind them.
    """

    def document_for(self, *sources):
        self.modules(*sources)
        return self.document

    def modules(self, *sources):
        tree = self.tree("fixture")
        for name, body in sources:
            tree.java("shop.till", name, body)
        self.document = graph.build([graph.java_root(tree.root)])
        self.assertEqual([], self.document["source"]["unparsed"])
        return {module["name"]: module for module in self.document["modules"]}

    def test_the_scale_is_the_dearest_interface_the_graph_holds(self):
        modules = self.modules(
            ("Till", "public class Till {\n    public void ring(long id, int cents) {}\n}"),
            ("Shelf", "public class Shelf {\n    public void restock() {}\n}"),
        )

        self.assertEqual(3, modules["Till"]["interface"]["cost"])
        self.assertEqual(1, modules["Shelf"]["interface"]["cost"])
        self.assertEqual(3, self.document["scoring"]["widestInterface"])

    def test_no_module_costs_more_than_the_scale_so_no_bar_runs_past_its_track(self):
        self.modules(
            ("Till", "public class Till {\n    public Receipt ring(long id) { return null; }\n}"),
            ("Shelf", "public class Shelf {\n    public void restock() {}\n}"),
            ("Receipt", "public record Receipt(long cents) {}"),
        )

        for module in self.document["modules"]:
            if module["interface"]["cost"] is not None:
                self.assertLessEqual(
                    module["interface"]["cost"],
                    self.document["scoring"]["widestInterface"],
                    module["id"],
                )

    def test_an_excluded_module_does_not_set_the_scale_it_is_kept_out_of(self):
        """Otherwise the widest bar on the page could be a bar nobody drew."""
        self.modules(
            ("Shelf", "public class Shelf {\n    public void restock() {}\n}"),
            ("Receipt", "public record Receipt(long a, long b, long c, long d, long e) {}"),
        )

        self.assertEqual(1, self.document["scoring"]["widestInterface"])

    def test_the_scale_is_nothing_when_no_module_was_scored_at_all(self):
        """A page with only data carriers on it divides by this, so it has to be safe."""
        self.modules(("Receipt", "public record Receipt(long cents) {}"))

        self.assertEqual(0, self.document["scoring"]["widestInterface"])


class OneInterfaceCostsTheSameHoweverItIsWrittenTest(SourceOfKnownShapeTest):
    """Two spellings of one interface are one interface, and cost one thing.

    Java lets a caller's obligation be written more than one way, and every way it can be
    written is a way this parser can lose part of it. Losing part of an interface makes a
    module look cheaper than it is, and cheap is what this page calls deep — so each of
    these fixtures writes the same obligation twice and asserts the two agree, rather than
    asserting either number on its own.
    """

    def test_an_array_written_on_the_name_is_the_array_written_on_the_type(self):
        methods = self.methods_of(
            "Till",
            "public class Till {\n"
            "    public void take(int xs[], String name) {}\n"
            "    public void alsoTake(int[] xs, String name) {}\n"
            "}",
        )

        self.assertEqual(["int[]", "String"], methods["take"]["parameters"])
        self.assertEqual(methods["alsoTake"]["parameters"], methods["take"]["parameters"])
        self.assertEqual(methods["alsoTake"]["cost"], methods["take"]["cost"])

    def test_a_two_dimensional_array_is_the_same_however_the_brackets_are_split(self):
        methods = self.methods_of(
            "Till",
            "public class Till {\n"
            "    public void grid(int cells[][]) {}\n"
            "    public void alsoGrid(int[] cells[]) {}\n"
            "    public void plainGrid(int[][] cells) {}\n"
            "}",
        )

        self.assertEqual(["int[][]"], methods["grid"]["parameters"])
        self.assertEqual(["int[][]"], methods["alsoGrid"]["parameters"])
        self.assertEqual(["int[][]"], methods["plainGrid"]["parameters"])

    def test_a_type_argument_is_spelled_one_way_wherever_it_is_written(self):
        """Otherwise one document holds two names for the type a caller learns once."""
        module = self.modules(
            ("Till", "import java.util.Map;\npublic class Till {\n"
                     "    public Map<String, Long> tally(Map<String,Long> so_far) { return null; }\n}")
        )["Till"]
        method = module["interface"]["methods"][0]

        self.assertEqual(method["returns"], method["parameters"][0])
        self.assertEqual("Map<String, Long>", method["returns"])

    def test_the_receiver_a_method_can_name_is_not_a_parameter_a_caller_passes(self):
        """`void ring(Till this, long id)` is one parameter, and `this` is not it."""
        methods = self.methods_of(
            "Till",
            "public class Till {\n    public void ring(Till this, long id) {}\n}",
        )

        self.assertEqual(["long"], methods["ring"]["parameters"])
        self.assertEqual(2, methods["ring"]["cost"])


class AnAnnotationsArgumentIsNeverReadAsABodyTest(SourceOfKnownShapeTest):
    """A brace inside an annotation's arguments is not the brace a body opens with.

    `@Values({"a", "b"})` on a record component put a `{` between the module's name and
    its body, and every scan looking for "the first brace after the name" took it for the
    body. The interface then read as nothing at all: no methods, no types crossing the
    seam, nothing said — a module priced at the cost of an empty interface. This
    repository holds twenty-six records, and an annotation with an array argument on one
    of their components is an ordinary edit away.
    """

    def test_a_record_whose_component_is_annotated_still_offers_its_whole_interface(self):
        module = self.modules(
            ("R", 'public record R(@Values({"a", "b"}) String s, long cents)\n'
                  "        implements Comparable<R> {\n"
                  "    public String pretty() { return s + cents; }\n"
                  "    public int compareTo(R other) { return 0; }\n}"),
        )["R"]

        self.assertEqual(
            ["cents", "compareTo", "pretty", "s"],
            [method["name"] for method in module["interface"]["methods"]],
        )
        self.assertEqual(
            ["R", "String", "int", "long"],
            [type_["name"] for type_ in module["interface"]["typesCrossingTheSeam"]],
        )

    def test_an_excluded_module_of_that_shape_is_still_drawn_with_what_it_offers(self):
        """Criterion six: a rule declining to measure an interface has to leave one to see."""
        module = self.modules(
            ("R", 'public record R(@Values({"a"}) String s) {}'),
        )["R"]

        self.assertEqual("data carrier", module["excludedBy"]["rule"])
        self.assertIsNone(module["interface"]["cost"])
        self.assertEqual(["s"], [method["name"] for method in module["interface"]["methods"]])

    def test_a_type_declared_inside_such_a_record_is_still_named_on_it(self):
        """The same fixture used to fail the whole file rather than read it."""
        module = self.modules(
            ("R", 'public record R(@Values({"a"}) String s) {\n'
                  "    record Row(long id) {}\n}"),
        )["R"]

        self.assertEqual(["Row"], module["nested"])
        self.assertEqual(["s"], [method["name"] for method in module["interface"]["methods"]])

    def test_a_supertypes_type_argument_can_be_annotated_too(self):
        methods = self.methods_of(
            "Till", 'public class Till extends Base<@Values({"a"}) String> {\n'
                    "    public void ring() {}\n}"
        )

        self.assertEqual(["ring"], sorted(methods))

    def test_an_annotation_on_a_parameter_is_not_a_body_either(self):
        methods = self.methods_of(
            "Till", "public class Till {\n"
                    '    public void ring(@Values({"a"}) String name) {}\n'
                    "    public void open() {}\n}"
        )

        self.assertEqual(["open", "ring"], sorted(methods))
        self.assertEqual(["String"], methods["ring"]["parameters"])


class AnEnumConstantIsNotAMethodTest(SourceOfKnownShapeTest):
    """An enum's constants are written where its members are and are not members.

    A constant carrying arguments after one carrying a body was read as a method — with
    the comma between them as its return type — and the module was charged for it. Enums
    are excluded from scoring by a rule the configuration file names, so the invented
    method only reached a bar when that rule was taken out; the interface it inflated was
    in the graph either way.
    """

    def test_a_constant_with_a_body_leaves_the_constant_after_it_a_constant(self):
        module = self.modules(
            ("Kind", "public enum Kind {\n"
                     '    A("x") { int n() { return 1; } },\n'
                     '    B("y");\n'
                     "    Kind(String s) {}\n"
                     "    public String label() { return null; }\n}"),
        )["Kind"]

        self.assertEqual(
            ["label"], [method["name"] for method in module["interface"]["methods"]]
        )

    def test_an_enum_of_nothing_but_constants_offers_nothing(self):
        module = self.modules(("Kind", "public enum Kind { A, B }"))["Kind"]

        self.assertEqual([], module["interface"]["methods"])
