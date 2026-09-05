"""Interface cost, stated one rule at a time.

Each fixture is a source tree built to produce one number, so the suite reads as the
statement of the scoring rules rather than as a set of examples. Nothing here is asserted
against the application's own code: a rule established against a module somebody is still
writing changes meaning every time they save.
"""

import json
import re

from ... import graph, page, scoring
from ..support.sourcetrees import SourceTreeTest


class SourceOfKnownShapeTest(SourceTreeTest):
    """A test that turns a handful of Java files into modules and reads their interfaces."""

    def modules(self, *sources):
        tree = self.tree("fixture")
        for name, body in sources:
            tree.java("shop.till", name, body)

        self.document = graph.build([graph.java_root(tree.root)], scoring.load())

        self.assertEqual([], self.document["source"]["unparsed"])
        return {module["name"]: module for module in self.document["modules"]}

    def rendered(self, *sources):
        """These modules, and the page rendered from the document they made."""
        modules = self.modules(*sources)
        return modules, page.render(
            self.document, graph.serialise(self.document)
        ).decode("utf-8")

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

    def test_a_method_named_after_a_contextual_keyword_is_still_a_method(self):
        """`record` is a legal method name, and a module in this application would use it.

        The one member with a parameter list that is not a method is a nested record, and
        telling the two apart by the word alone dropped `void record(Deposit)` from the
        interface without a word said — the module cheaper than the source makes it, and
        nothing in `unparsed` to say so. The keyword only declares a type when a name
        follows it, so both are asserted here: the method counts, the nested record does
        not, and the record's own accessor is not the outer module's method either.
        """
        module = self.modules(
            ("Till",
             "public class Till {\n"
             "    record Row(long id) {}\n"
             "    public void record(Receipt receipt) {}\n"
             "    public Receipt record() { return null; }\n"
             "}"),
        )["Till"]

        self.assertEqual(
            [("record", [], "Receipt"), ("record", ["Receipt"], "void")],
            [
                (method["name"], method["parameters"], method["returns"])
                for method in module["interface"]["methods"]
            ],
        )
        self.assertEqual(["Row"], module["nested"])


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
                 "documentedRefusals": [], "cost": 1},
                {"name": "ring", "visibility": "public", "parameters": ["long", "int"],
                 "returns": "void", "documentedRefusals": [], "cost": 3},
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
            ["long"], [t["name"] for t in module["interface"]["typesCrossingTheSeam"]]
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


class AMethodThatHandsNothingBackCrossesNoSeamTest(SourceOfKnownShapeTest):
    """`void` is where Java writes a return type. It is not one, and nothing crosses there.

    A caller of `public void ring()` learns one method and no types at all. Folded in with
    the returns that really are types, it was counted as one — invisible only because the
    weight a familiar type carries is zero in the committed file, and wrong at every other
    value. Nine modules on the committed page published a type called `void`, and
    `AccountsService`'s card read "7 types every caller already knows" with `void` among
    the seven.

    Both edits below are the ones the configuration file exists to invite, and both are
    where the fault showed: at a non-zero weight a caller is charged for a type they never
    meet, and with the familiar list emptied they are told there is a type here to go and
    learn.
    """

    HANDS_BACK_NOTHING = ("Till", "public class Till {\n    public void ring() {}\n}")

    def scored_with(self, source, **interface_cost):
        """One module, scored with these `interfaceCost` fields replaced.

        `weights` is merged rather than replaced, so a test moves one weight and leaves
        the other three where the file put them.
        """
        with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
            configuration = json.loads(handle.read().decode("utf-8"))
        cost = configuration["interfaceCost"]
        cost["weights"] = dict(cost["weights"], **interface_cost.pop("weights", {}))
        cost.update(interface_cost)
        rules = scoring.load(self.tree("rules").raw("scoring.json", json.dumps(configuration)))

        name, body = source
        tree = self.tree("fixture")
        tree.java("shop.till", name, body)
        document = graph.build([graph.java_root(tree.root)], rules)

        self.assertEqual([], document["source"]["unparsed"])
        return {module["name"]: module for module in document["modules"]}[name]

    def test_a_method_handing_nothing_back_puts_no_type_across_the_seam(self):
        module = self.modules(self.HANDS_BACK_NOTHING)["Till"]

        self.assertEqual([], module["interface"]["typesCrossingTheSeam"])
        self.assertEqual(1, module["interface"]["cost"])

    def test_paying_for_a_familiar_type_does_not_pay_for_handing_nothing_back(self):
        module = self.scored_with(
            self.HANDS_BACK_NOTHING, weights={"typeEveryCallerAlreadyKnows": 1}
        )

        self.assertEqual([], module["interface"]["typesCrossingTheSeam"])
        self.assertEqual(1, module["interface"]["cost"])

    def test_charging_for_every_type_a_caller_meets_still_charges_for_none_here(self):
        """The edit the README blesses: no type is taken for granted, and there is no type."""
        module = self.scored_with(self.HANDS_BACK_NOTHING, typesEveryCallerAlreadyKnows=[])

        self.assertEqual([], module["interface"]["typesCrossingTheSeam"])
        self.assertEqual(1, module["interface"]["cost"])

    def test_a_method_handing_back_nothing_still_puts_its_parameters_across(self):
        """Only the return is nothing. What the caller passes in is unaffected."""
        module = self.modules(
            ("Till", "public class Till {\n    public void ring(Receipt it) {}\n}")
        )["Till"]

        self.assertEqual(
            [{"name": "Receipt", "mustBeLearned": True}],
            module["interface"]["typesCrossingTheSeam"],
        )

    def test_the_boxed_void_a_method_can_hand_back_is_a_type_like_any_other(self):
        """`Void` is a class a caller can be handed, and is not the word Java writes for
        nothing. Filtering the word rather than the return would take this with it.
        """
        module = self.modules(
            ("Till", "public class Till {\n    public Void ring() { return null; }\n}")
        )["Till"]

        self.assertEqual(
            [{"name": "Void", "mustBeLearned": False}],
            module["interface"]["typesCrossingTheSeam"],
        )


class AsJavaScriptReadsIt:
    """A value out of the graph document, reached with dots the way the renderer reaches it.

    `module.interface.cost` is one expression in JavaScript and two subscripts in Python.
    This is the adapter that lets the page's own expression be worked out here against
    the document it was rendered from, rather than against a second copy of it written in
    the test — which is the fault this suite was told about elsewhere: a formula
    re-implemented beside the one it is meant to establish agrees with itself.
    """

    def __init__(self, value):
        self._value = value

    def __getattr__(self, name):
        found = self._value[name]
        return AsJavaScriptReadsIt(found) if isinstance(found, dict) else found


class EveryBarIsDrawnOnOneScaleTest(SourceOfKnownShapeTest):
    """Two bars mean something side by side, and the scale they share is in the graph.

    The page multiplies each cost by its own width and divides by this one number, so a
    bar is checkable against the document it was drawn from rather than against
    arithmetic only the browser can do. Both halves are established here: the number in
    the graph, and the expression the page draws with — taken out of the rendered file
    and worked out against that document, because until it was, a width hardcoded to a
    constant left every bar identical with the whole suite green.
    """

    # The width of a bar is the one thing on this page that only a browser works out, and
    # for six attempts nothing established it: hardcoding it to a constant left every bar
    # identical, the committed page regenerated, and the whole suite green. What a test
    # without a browser can reach is the expression, which the rendered file carries as
    # text — so it is lifted out of the page and read here.
    _A_WIDTH_IS_SET = re.compile(r"style\.width\s*=\s*([^;]+);")
    _A_SHARE_OF_THE_SCALE = re.compile(
        r"\A\(\s*(?P<when>.+?)\s*\?\s*(?P<then>.+?)\s*:\s*(?P<otherwise>.+?)\s*\)"
        r'\s*\+\s*"%"\Z'
    )

    A_FEW_COSTS = (
        ("Till", "public class Till {\n"
                 "    public Receipt ring(long id, String iban) { return null; }\n"
                 "    public int add(int a, int b) { return a + b; }\n}"),
        ("Shelf", "public class Shelf {\n    public void restock() {}\n}"),
        ("Receipt", "public record Receipt(long cents) {}"),
    )

    A_REFUSAL = (
        ("Gate", "public class Gate {\n"
                 "    /** @throws Shut if it is shut */\n"
                 "    public void go() { throw new Shut(); }\n}"),
    )

    def test_a_bar_is_drawn_from_its_own_modules_cost_and_the_scale_in_the_document(self):
        """One width is set on this page, and every part of it comes out of the document.

        One expression still, now applied to each band of a bar in turn rather than to a
        bar as a whole. That is what keeps the check possible at all: two expressions
        would be two things to read out of the file and two chances for one of them to
        be a constant nobody noticed.
        """
        _, rendered = self.rendered(*self.A_FEW_COSTS)

        widths = self._A_WIDTH_IS_SET.findall(rendered)

        self.assertEqual(1, len(widths), widths)
        self.assertIn("part.cost", widths[0])
        self.assertIn("widest", widths[0])
        self.assertIn("var widest = document_.scoring.widestInterface;", rendered)
        self.assertIn("cost: module.interface.costWithoutRefusals", rendered)
        self.assertIn("cost: module.interface.refusalCost", rendered)

    def _drawn(self, rendered, modules, band):
        """The width the page's own expression works out for one band of every scored bar.

        The expression is taken out of the rendered file rather than written again in
        this test, because a formula written twice agrees with itself: what is checked is
        the arithmetic a browser would do, over the document the page carries.
        """
        widest = self.document["scoring"]["widestInterface"]
        shape = self._A_SHARE_OF_THE_SCALE.match(
            self._A_WIDTH_IS_SET.findall(rendered)[0].strip()
        )
        self.assertIsNotNone(
            shape,
            "the page sets a width this test cannot read; read it and say what it does",
        )

        drawn = {}
        for name, module in modules.items():
            if module["excludedBy"] is not None:
                continue
            reached = {
                "part": AsJavaScriptReadsIt({"cost": module["interface"][band]}),
                "widest": widest,
            }
            half = "then" if eval(shape.group("when"), {}, reached) else "otherwise"
            drawn[name] = eval(shape.group(half), {}, reached)
        return drawn

    def test_the_width_the_page_works_out_is_the_cost_as_a_share_of_the_scale(self):
        """Every bar the fixture draws, band by band, against the one scale in the document."""
        modules, rendered = self.rendered(*self.A_FEW_COSTS)
        widest = self.document["scoring"]["widestInterface"]

        learned = self._drawn(rendered, modules, "costWithoutRefusals")
        refused = self._drawn(rendered, modules, "refusalCost")

        self.assertEqual(["Shelf", "Till"], sorted(learned))
        self.assertEqual(100.0, learned["Till"] + refused["Till"])
        self.assertAlmostEqual(
            100.0 * modules["Shelf"]["interface"]["cost"] / widest,
            learned["Shelf"] + refused["Shelf"],
        )

    def test_the_two_bands_of_a_bar_come_to_the_bar(self):
        """A bar split into bands that do not add up to it is two claims on one card."""
        modules, rendered = self.rendered(*self.A_FEW_COSTS, *self.A_REFUSAL)
        widest = self.document["scoring"]["widestInterface"]

        learned = self._drawn(rendered, modules, "costWithoutRefusals")
        refused = self._drawn(rendered, modules, "refusalCost")

        self.assertGreater(refused["Gate"], 0)
        for name, module in modules.items():
            if module["excludedBy"] is not None:
                continue
            self.assertAlmostEqual(
                100.0 * module["interface"]["cost"] / widest,
                learned[name] + refused[name],
                msg=name,
            )

    def test_the_page_divides_by_nothing_when_no_module_was_scored_at_all(self):
        """A page of nothing but data carriers reaches the same expression with widest 0."""
        _, rendered = self.rendered(("Receipt", "public record Receipt(long cents) {}"))
        shape = self._A_SHARE_OF_THE_SCALE.match(
            self._A_WIDTH_IS_SET.findall(rendered)[0].strip()
        )
        reached = {"part": AsJavaScriptReadsIt({"cost": 0}), "widest": 0}

        self.assertFalse(eval(shape.group("when"), {}, reached))
        self.assertEqual(0, eval(shape.group("otherwise"), {}, reached))

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


class WhatABarLeavesOutIsSaidOnThePageTest(SourceOfKnownShapeTest):
    """A number that measures part of a thing has to say which part, or it reads as all of it.

    A constructor is deliberately not counted — how a module is built is this framework's
    business rather than a caller's — and for six attempts the page's list of what a bar
    leaves out named five things and not that one. Three modules in this application
    declare a constructor and nothing else, so their cards read "0 to learn" with nothing
    anywhere on the page to tell that zero from an empty class, while a caller writing
    `new JobFailed(what, cause)` has the constructor and both types crossing it to learn.
    """

    ONLY_A_CONSTRUCTOR = (
        "JobFailed",
        "public class JobFailed extends RuntimeException {\n"
        "    JobFailed(String what, Throwable cause) { super(what, cause); }\n}",
    )
    NOTHING_AT_ALL = ("SchedulingIsOn", "public class SchedulingIsOn {}")

    def test_a_module_whose_only_member_is_a_constructor_is_scored_at_nothing(self):
        """The fixture behind the wording: the zero on the page is a real zero."""
        modules, _ = self.rendered(self.ONLY_A_CONSTRUCTOR, self.NOTHING_AT_ALL)

        for name in ("JobFailed", "SchedulingIsOn"):
            self.assertEqual(0, modules[name]["interface"]["cost"], name)
            self.assertEqual([], modules[name]["interface"]["methods"], name)

    def test_the_page_names_the_constructor_among_the_things_a_bar_leaves_out(self):
        _, rendered = self.rendered(self.ONLY_A_CONSTRUCTOR)

        self.assertIn("the constructor a caller writes new against", rendered)

    def test_a_bar_at_nothing_says_that_is_what_this_bar_counts_and_not_what_there_is(self):
        """The card itself, not only the paragraph three boxes above it."""
        _, rendered = self.rendered(self.ONLY_A_CONSTRUCTOR)

        self.assertIn("module.interface.cost === 0", rendered)
        self.assertIn(
            "nothing this bar counts, which is not the same as nothing to learn", rendered
        )


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

    def test_an_array_written_after_the_parameters_is_the_array_written_on_the_type(self):
        """`int total()[]` and `int[] total()` hand a caller back the same array.

        The mirror of the parameter spelling above, one place further along the signature:
        brackets dropped here would hand a caller back the element type instead of the
        array, and say so in the graph as if the source had.
        """
        methods = self.methods_of(
            "Till",
            "public class Till {\n"
            "    public int takings()[] { return null; }\n"
            "    public int[] alsoTakings() { return null; }\n"
            "}",
        )

        self.assertEqual("int[]", methods["takings"]["returns"])
        self.assertEqual(methods["alsoTakings"]["returns"], methods["takings"]["returns"])

    def test_a_records_accessor_for_a_varargs_component_hands_back_the_array(self):
        """`record Sale(int... cents)` accepts any number of ints and hands back `int[]`.

        The dots say how many on the way in and nothing at all on the way out, so the
        component and its accessor are spelled differently on purpose — and the accessor
        is what a caller of the record meets.
        """
        methods = self.methods_of(
            "Sale", "public record Sale(String name, int... cents) {}"
        )

        self.assertEqual("int[]", methods["cents"]["returns"])
        self.assertEqual("String", methods["name"]["returns"])

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


class ACostCanBeAddedUpFromThePartsDrawnUnderItTest(SourceTreeTest):
    """A number a reader cannot take apart is a number they cannot argue with.

    The page draws a breakdown under each bar, and the whole claim of the page is that
    the score above it can be disagreed with. The breakdown counted only the types this
    application invented, so a reader who set `typeEveryCallerAlreadyKnows` to anything
    but zero — one of the four weights the file exists to let them change — was shown
    "1 method, 2 parameters, 0 types" under a cost of 6, three of which came from the
    type it said there were none of. Two boxes above, the same page told them what that
    kind of type costs.
    """

    def weighted(self, already_known, *sources):
        """These modules, scored with a weight of `already_known` on a familiar type."""
        with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
            configuration = json.loads(handle.read().decode("utf-8"))
        configuration["interfaceCost"]["weights"]["typeEveryCallerAlreadyKnows"] = already_known
        rules = scoring.load(self.tree("rules").raw("scoring.json", json.dumps(configuration)))

        tree = self.tree("fixture")
        for name, body in sources:
            tree.java("shop.till", name, body)
        self.document = graph.build([graph.java_root(tree.root)], rules)

        self.assertEqual([], self.document["source"]["unparsed"])
        return {module["name"]: module for module in self.document["modules"]}

    def test_a_type_every_caller_knows_is_counted_in_the_cost_at_the_weight_given(self):
        modules = self.weighted(
            3, ("Till", "public class Till {\n    public int add(int a, int b) { return a + b; }\n}")
        )
        interface = modules["Till"]["interface"]

        self.assertEqual(
            [{"name": "int", "mustBeLearned": False}], interface["typesCrossingTheSeam"]
        )
        self.assertEqual(1 + 2 + 3, interface["cost"])

    def test_every_cost_is_the_weighted_sum_of_the_counts_the_graph_publishes(self):
        """The parts are read from the document and the weights from the file it names.

        Nothing here restates what a weight is worth: a test holding its own copy of the
        weights would go red when a reader edited the file, which is the opposite of the
        property this whole branch is about.
        """
        modules = self.weighted(
            3,
            ("Till", "public class Till {\n"
                     "    public Receipt ring(long id, String iban) { return null; }\n"
                     "    public int add(int a, int b) { return a + b; }\n}"),
            ("Shelf", "public interface Shelf {\n    void restock(Optional<Long> id);\n}"),
        )
        weights = self.document["scoring"]["weights"]

        for name, module in modules.items():
            interface = module["interface"]
            crossing = interface["typesCrossingTheSeam"]
            counted = {
                "method": len(interface["methods"]),
                "parameter": sum(len(method["parameters"]) for method in interface["methods"]),
                "typeToLearn": sum(1 for type_ in crossing if type_["mustBeLearned"]),
                "typeEveryCallerAlreadyKnows": sum(
                    1 for type_ in crossing if not type_["mustBeLearned"]
                ),
                "refusal": len(interface["refusals"]),
            }
            self.assertEqual(sorted(weights), sorted(counted), name)
            self.assertEqual(
                sum(weights[weight] * how_many for weight, how_many in counted.items()),
                interface["cost"],
                name,
            )

    def test_the_breakdown_the_page_draws_has_a_term_for_every_weight(self):
        """The renderer runs in a browser this suite does not have, so what is checked
        here is the one thing the rendered file can be asked: that every weight a cost is
        added up from has a count drawn under the bar. A weight with no term is a part of
        every total with nothing beneath it to account for.
        """
        self.weighted(
            3, ("Till", "public class Till {\n    public int add(int a, int b) { return a + b; }\n}")
        )
        rendered = page.render(self.document, graph.serialise(self.document)).decode("utf-8")

        self.assertEqual(
            sorted(self.document["scoring"]["weights"]),
            sorted(set(re.findall(r'weight: "(\w+)"', rendered))),
        )


class ARecordThatWritesAnAccessorOffersOneOfItTest(SourceOfKnownShapeTest):
    """Java compiles one `cents()` whether or not the record writes it out.

    A record's components each give it an accessor, and a record is allowed to write one
    of those accessors itself — defensive copying and normalising are how a record is
    given an invariant. Both were counted, so a record that wrote two of its accessors
    offered four methods where a caller meets two: an interface priced at twice what the
    source asks for, with nothing said about it, and a scale every other bar on the page
    is drawn against moved by it.

    The pair here writes the same interface twice, once with the accessors typed out and
    once without, and asserts the two agree rather than asserting either number alone.
    """

    def scored(self, *sources):
        """These modules, with the rule that never scores a record taken out of the file.

        The cost of a record is absent under the committed configuration, so a doubled
        method only reaches a bar once a reader makes the edit criterion eight invites.
        The method list is in the graph either way, which is what the page and every
        later measurement read.
        """
        with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
            configuration = json.loads(handle.read().decode("utf-8"))
        configuration["exclusions"] = [
            rule for rule in configuration["exclusions"] if rule["rule"] != "data carrier"
        ]
        rules = scoring.load(self.tree("rules").raw("scoring.json", json.dumps(configuration)))

        tree = self.tree("fixture")
        for name, body in sources:
            tree.java("shop.till", name, body)
        self.document = graph.build([graph.java_root(tree.root)], rules)

        self.assertEqual([], self.document["source"]["unparsed"])
        return {module["name"]: module for module in self.document["modules"]}

    WRITES_THEM = (
        "Coin",
        "public record Coin(long cents, List<String> tags) {\n"
        "    @Override public long cents() { return cents < 0 ? 0 : cents; }\n"
        "    @Override public List<String> tags() { return List.copyOf(tags); }\n}",
    )
    WRITES_NEITHER = ("Plain", "public record Plain(long cents, List<String> tags) {}")

    def test_the_accessor_a_record_writes_is_the_one_its_component_gives_it(self):
        methods = self.modules(self.WRITES_THEM)["Coin"]["interface"]["methods"]

        self.assertEqual(["cents", "tags"], [method["name"] for method in methods])
        self.assertEqual(["long", "List<String>"], [method["returns"] for method in methods])

    def test_writing_an_accessor_out_costs_a_caller_what_leaving_it_out_costs(self):
        modules = self.scored(self.WRITES_THEM, self.WRITES_NEITHER)

        self.assertEqual(
            [method["name"] for method in modules["Plain"]["interface"]["methods"]],
            [method["name"] for method in modules["Coin"]["interface"]["methods"]],
        )
        self.assertEqual(
            modules["Plain"]["interface"]["cost"], modules["Coin"]["interface"]["cost"]
        )

    def test_a_record_that_writes_its_own_accessors_does_not_move_the_scale(self):
        """The doubled interface was the widest one, so every other bar was drawn to it."""
        self.scored(self.WRITES_THEM, self.WRITES_NEITHER)

        self.assertEqual(2, self.document["scoring"]["widestInterface"])

    def test_a_method_that_only_shares_a_components_name_is_a_method_of_its_own(self):
        """`cents(int scale)` is not the accessor: a caller has both to learn."""
        methods = self.modules(
            ("Coin", "public record Coin(long cents) {\n"
                     "    public long cents(int scale) { return cents * scale; }\n}")
        )["Coin"]["interface"]["methods"]

        self.assertEqual(["cents", "cents"], [method["name"] for method in methods])
        self.assertEqual([[], ["int"]], [method["parameters"] for method in methods])


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
