"""Depth is leverage: what a caller sets in motion per unit of interface they must learn.

Every fixture here is a source tree built to have one shape — a module coordinating four
things behind one method, a module coordinating one thing per method — so the suite reads
as the statement of what reach is rather than as a list of what this application happens
to contain. Each asserts the depth it was built to produce, numerator and denominator, so
a change to either can be argued with rather than merely noticed.

The measure this replaced is the reason for the shape of these tests. Depth as
implementation lines over interface lines pays a module for padding, and the padding
fixture below is the one that would catch it coming back: the same module with sixty more
lines of implementation and not one more thing coordinated has to come out at exactly the
depth it had before.
"""

import logging
import re
import unittest

from ... import graph, javasource, page, scoring
from ..support.sourcetrees import SourceTreeTest

# The collaborators the fixtures reach for. Each is here to be one kind of reached thing:
# a module with a method to call, storage whose implementation is generated, a row that
# outlives the call, and — in another package — a module that has to be imported to be
# spelled by name.
A_MODULE_TO_CALL = "public class Prices {\n    public long of(long id) { return 0; }\n}"
AN_ADAPTER = (
    "import org.springframework.data.jpa.repository.JpaRepository;\n\n"
    "interface ReceiptRepository extends JpaRepository<Receipt, Long> {\n}"
)
A_PERSISTENT_RECORD = (
    "import jakarta.persistence.Entity;\n\n"
    "@Entity\nclass Receipt {\n"
    "    Receipt(long id, long cents) {}\n"
    "    public long id() { return 0; }\n}"
)
A_MODULE_IN_ANOTHER_PACKAGE = "public class Shelf {\n    public void take(long id) {}\n}"
# The same name as the record above and nothing persistent about it, so that a test can
# put one of each in two packages and say which one a `new` written out in full built.
A_RECORD_THAT_IS_NOT_PERSISTENT = (
    "class Receipt {\n    Receipt(long id) {}\n    public long id() { return 0; }\n}"
)

A_DEEP_MODULE = """import org.springframework.transaction.annotation.Transactional;

import shop.stock.Shelf;

public class Till {

    private final ReceiptRepository receipts;
    private final Shelf shelf;
    private final Prices prices;

    Till(ReceiptRepository receipts, Shelf shelf, Prices prices) {
        this.receipts = receipts;
        this.shelf = shelf;
        this.prices = prices;
    }

    @Transactional
    public long ring(long id) {
        shelf.take(id);
        long cents = prices.of(id);
        receipts.save(new Receipt(id, cents));
        return cents;
    }
}"""

# The same module with sixty more lines of implementation and not one more thing
# coordinated: the same three collaborators, the same record, the same transaction, said
# at greater length. Under a line-count measure this is the deepest module in the tree.
A_DEEP_MODULE_PADDED = """import org.springframework.transaction.annotation.Transactional;

import shop.stock.Shelf;

public class Till {

    private final ReceiptRepository receipts;
    private final Shelf shelf;
    private final Prices prices;

    Till(ReceiptRepository receipts, Shelf shelf, Prices prices) {
        this.receipts = receipts;
        this.shelf = shelf;
        this.prices = prices;
    }

    @Transactional
    public long ring(long id) {
        shelf.take(id);
        long cents = prices.of(id);
        long first = cents;
        long second = first;
        long third = second;
        long fourth = third;
        long fifth = fourth;
        long sixth = fifth;
        long seventh = sixth;
        long eighth = seventh;
        long ninth = eighth;
        long tenth = ninth;
        long eleventh = tenth;
        long twelfth = eleventh;
        long thirteenth = twelfth;
        long fourteenth = thirteenth;
        long fifteenth = fourteenth;
        long sixteenth = fifteenth;
        long seventeenth = sixteenth;
        long eighteenth = seventeenth;
        long nineteenth = eighteenth;
        long twentieth = nineteenth;
        shelf.take(id);
        shelf.take(id);
        shelf.take(id);
        prices.of(id);
        prices.of(id);
        prices.of(id);
        receipts.save(new Receipt(id, twentieth));
        receipts.save(new Receipt(id, twentieth));
        receipts.save(new Receipt(id, twentieth));
        return again(twentieth);
    }

    private long again(long cents) {
        long once = cents;
        long twice = once;
        long thrice = twice;
        long more = thrice;
        long yetMore = more;
        long stillMore = yetMore;
        long moreAgain = stillMore;
        long enough = moreAgain;
        return enough;
    }
}"""

# One method per collaborator, coordinating nothing: everything it is handed it hands
# straight on, and a caller who deleted it would call the same three modules themselves.
A_PASS_THROUGH = """import shop.stock.Shelf;

public class Counter {

    private final Shelf shelf;
    private final Prices prices;

    Counter(Shelf shelf, Prices prices) {
        this.shelf = shelf;
        this.prices = prices;
    }

    public void take(long id) {
        shelf.take(id);
    }

    public long price(long id) {
        return prices.of(id);
    }
}"""


class SourceOfKnownShapeTest(SourceTreeTest):
    """A test that turns a handful of Java files into modules and reads what they reach."""

    def modules(self, *sources, **elsewhere):
        """The graph for these files under `shop.till`, plus any written in other packages.

        A source root of its own each time, so that one test can read two source trees —
        which is what the padding fixture needs: the same module twice, at two lengths,
        and two modules of one id in one tree is a run this tool refuses outright.
        """
        self.trees = getattr(self, "trees", 0) + 1
        tree = self.tree("fixture-%d" % self.trees)
        for name, body in sources:
            tree.java("shop.till", name, body)
        for name, (package, body) in elsewhere.items():
            tree.java(package, name, body)

        self.document = graph.build([graph.java_root(tree.root)], scoring.load())

        self.assertEqual([], self.document["source"]["unparsed"])
        return {module["name"]: module for module in self.document["modules"]}

    def collaborators(self):
        """The whole cast the fixtures in this file call, as files in a source tree."""
        return {
            "Prices": ("shop.till", A_MODULE_TO_CALL),
            "ReceiptRepository": ("shop.till", AN_ADAPTER),
            "Receipt": ("shop.till", A_PERSISTENT_RECORD),
            "Shelf": ("shop.stock", A_MODULE_IN_ANOTHER_PACKAGE),
        }

    def rendered(self, *sources, **elsewhere):
        """These modules, and the page a browser would draw them from, as text."""
        modules = self.modules(*sources, **elsewhere)
        return modules, page.render(
            self.document, graph.serialise(self.document)
        ).decode("utf-8")

    def reached(self, module):
        """What one module reaches, as (kind, name) pairs, in the order the fan draws them."""
        return [(entry["kind"], entry["name"]) for entry in module["reach"]["reaches"]]


class WhatAModuleCoordinatesIsWhatItReachesTest(SourceOfKnownShapeTest):
    """The four things reach counts, each established on a fixture of its own."""

    def test_a_module_it_calls_is_one_thing_reached(self):
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    private final Prices prices;\n"
                     "    Till(Prices prices) { this.prices = prices; }\n"
                     "    public long ring(long id) { return prices.of(id); }\n}"),
            ("Prices", A_MODULE_TO_CALL),
        )

        self.assertEqual([("module", "Prices")], self.reached(modules["Till"]))

    def test_an_adapter_it_drives_is_one_thing_reached(self):
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    private final ReceiptRepository receipts;\n"
                     "    Till(ReceiptRepository receipts) { this.receipts = receipts; }\n"
                     "    public long count() { return receipts.count(); }\n}"),
            ("ReceiptRepository", AN_ADAPTER),
            ("Receipt", A_PERSISTENT_RECORD),
        )

        self.assertEqual([("adapter", "ReceiptRepository")], self.reached(modules["Till"]))

    def test_a_persistent_record_it_writes_is_one_thing_reached(self):
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    public Receipt ring(long id) { return new Receipt(id, 0); }\n}"),
            ("Receipt", A_PERSISTENT_RECORD),
        )

        self.assertEqual([("record", "Receipt")], self.reached(modules["Till"]))
        self.assertEqual(
            "builds one: annotated with Entity",
            modules["Till"]["reach"]["reaches"][0]["matched"],
        )

    def test_the_transaction_it_establishes_is_one_thing_reached(self):
        modules = self.modules(
            ("Till", "import org.springframework.transaction.annotation.Transactional;\n\n"
                     "public class Till {\n"
                     "    @Transactional\n    public void ring(long id) {}\n}"),
        )

        self.assertEqual([("transaction", "a transaction")], self.reached(modules["Till"]))
        self.assertEqual(
            "ring is annotated with Transactional",
            modules["Till"]["reach"]["reaches"][0]["matched"],
        )

    def test_a_module_that_marks_itself_transactional_establishes_one_too(self):
        modules = self.modules(
            ("Till", "import org.springframework.transaction.annotation.Transactional;\n\n"
                     "@Transactional\npublic class Till {\n    public void ring(long id) {}\n}"),
        )

        self.assertEqual([("transaction", "a transaction")], self.reached(modules["Till"]))
        self.assertEqual(
            "the module is annotated with Transactional",
            modules["Till"]["reach"]["reaches"][0]["matched"],
        )

    def test_a_module_that_coordinates_nothing_reaches_nothing(self):
        modules = self.modules(
            ("Till", "public class Till {\n    public long ring(long id) { return id; }\n}"),
        )

        self.assertEqual([], self.reached(modules["Till"]))
        self.assertEqual(0, modules["Till"]["reach"]["count"])

    def test_one_thing_reached_twice_is_one_thing_reached(self):
        """Reach counts distinct things: the same collaborator called again is not another."""
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    private final Prices prices;\n"
                     "    Till(Prices prices) { this.prices = prices; }\n"
                     "    public long ring(long id) {\n"
                     "        prices.of(id);\n        prices.of(id);\n"
                     "        return prices.of(id);\n    }\n}"),
            ("Prices", A_MODULE_TO_CALL),
        )

        self.assertEqual([("module", "Prices")], self.reached(modules["Till"]))

    def test_a_record_it_both_builds_and_calls_is_one_thing_reached(self):
        """Two readings of one thing settle on the stronger, and still draw one line."""
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    private final Receipt receipt;\n"
                     "    Till(Receipt receipt) { this.receipt = receipt; }\n"
                     "    public long ring(long id) {\n"
                     "        Receipt made = new Receipt(id, receipt.id());\n"
                     "        return made.id();\n    }\n}"),
            ("Receipt", A_PERSISTENT_RECORD),
        )

        self.assertEqual([("record", "Receipt")], self.reached(modules["Till"]))

    def test_a_name_this_graph_does_not_hold_is_not_reached(self):
        """A module cannot raise its own score by importing more of the JDK.

        Nothing outside the source read is counted, which is what keeps every line in a
        fan pointing at something a reader can go and find on the same page.
        """
        modules = self.modules(
            ("Till", "import java.math.BigDecimal;\nimport java.time.Clock;\n\n"
                     "public class Till {\n"
                     "    private final Clock clock;\n"
                     "    Till(Clock clock) { this.clock = clock; }\n"
                     "    public long ring() {\n"
                     "        BigDecimal.valueOf(clock.millis());\n"
                     "        return clock.millis();\n    }\n}"),
        )

        self.assertEqual([], self.reached(modules["Till"]))

    def test_a_module_does_not_reach_itself(self):
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    public static long none() { return 0; }\n"
                     "    public long ring() { return Till.none(); }\n}"),
        )

        self.assertEqual([], self.reached(modules["Till"]))


class HowANameIsFollowedToAModuleTest(SourceOfKnownShapeTest):
    """A name in a body means the module the compiler would say it means, or nothing."""

    def test_a_call_through_a_field_reaches_what_the_field_holds(self):
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    private final Prices prices;\n"
                     "    Till(Prices prices) { this.prices = prices; }\n"
                     "    public long ring(long id) { return this.prices.of(id); }\n}"),
            ("Prices", A_MODULE_TO_CALL),
        )

        self.assertEqual([("module", "Prices")], self.reached(modules["Till"]))
        self.assertEqual(
            "called through the field prices, which holds a Prices",
            modules["Till"]["reach"]["reaches"][0]["matched"],
        )

    def test_a_field_holding_a_collection_of_collaborators_reaches_them(self):
        """A module keeps a collaborator in whatever shape it needs it in."""
        modules = self.modules(
            ("Till", "import java.util.List;\n\npublic class Till {\n"
                     "    private final List<Prices> everyPrice;\n"
                     "    Till(List<Prices> everyPrice) { this.everyPrice = everyPrice; }\n"
                     "    public int count() { return everyPrice.size(); }\n}"),
            ("Prices", A_MODULE_TO_CALL),
        )

        self.assertEqual([("module", "Prices")], self.reached(modules["Till"]))

    def test_a_static_call_reaches_the_module_it_names(self):
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    public long ring(long id) { return Prices.zero(); }\n}"),
            ("Prices", "public class Prices {\n    public static long zero() { return 0; }\n}"),
        )

        self.assertEqual([("module", "Prices")], self.reached(modules["Till"]))
        self.assertEqual("called on Prices", modules["Till"]["reach"]["reaches"][0]["matched"])

    def test_a_member_imported_statically_reaches_the_module_it_came_from(self):
        """The import is the only thing tying `zero()` to the module that declares it."""
        modules = self.modules(
            ("Till", "import static shop.till.Prices.zero;\n\npublic class Till {\n"
                     "    public long ring(long id) { return zero(); }\n}"),
            ("Prices", "public class Prices {\n    public static long zero() { return 0; }\n}"),
        )

        self.assertEqual([("module", "Prices")], self.reached(modules["Till"]))
        self.assertEqual(
            "calls zero, imported statically from it",
            modules["Till"]["reach"]["reaches"][0]["matched"],
        )

    def test_a_member_imported_statically_and_never_called_reaches_nothing(self):
        """The negative twin of the test above, and the one that fails on a name.

        A module declares `of` and returns a constant. It also imports `Prices.of`
        statically, the way a file left half-edited does. Nothing in its body calls
        anything, so it reaches nothing — and the only reading that says otherwise is one
        that mistakes the module's own declaration for a call, and then prints "calls of,
        imported statically from it" about a body that does no such thing.
        """
        modules = self.modules(
            ("Till", "import static shop.till.Prices.of;\n\npublic class Till {\n"
                     "    public long of(long cents) { return cents; }\n"
                     "    public long ring(long id) { return 1; }\n}"),
            ("Prices", A_MODULE_TO_CALL),
        )

        self.assertEqual([], self.reached(modules["Till"]))
        self.assertEqual(0, modules["Till"]["reach"]["count"])

    def test_a_constructor_is_not_read_as_a_call_to_a_static_import_of_its_name(self):
        """The same mistake, spelled with the one declaration named after the type."""
        modules = self.modules(
            ("Till", "import static shop.stock.Names.Till;\n\npublic class Till {\n"
                     "    private final long cents;\n"
                     "    public Till(long cents) { this.cents = cents; }\n"
                     "    public long ring() { return cents; }\n}"),
            Names=("shop.stock", "public class Names {\n"
                                 "    public static long Till() { return 0; }\n}"),
        )

        self.assertEqual([], self.reached(modules["Till"]))

    def test_a_member_imported_statically_and_declared_one_brace_deeper_reaches_nothing(self):
        """The same mistake as above, with the declaration moved into a nested class.

        Everything a module's body reaches for is read over the whole body, nested types
        and all — so everything it *declares* has to be read over the whole body too.
        Read from the top-level type's own methods only, this module has no `of` of its
        own, its `of` in the body is taken for a call, and the graph says it calls a
        module whose name appears nowhere but an import it never uses.

        A static import beside a nested record or a helper class whose method shares the
        name is everyday Java: this repository statically imports `AmountOfMoney.asMoney`
        in two services.
        """
        modules = self.modules(
            ("Till", "import static shop.till.Prices.of;\n\npublic class Till {\n"
                     "    static final class Coin {\n"
                     "        long of(long cents) { return cents; }\n"
                     "    }\n"
                     "    public long ring(long id) { return 1; }\n}"),
            ("Prices", A_MODULE_TO_CALL),
        )

        self.assertEqual([], self.reached(modules["Till"]))
        self.assertEqual(0, modules["Till"]["reach"]["count"])

    def test_a_member_imported_statically_and_declared_in_an_anonymous_class_reaches_nothing(self):
        """The same again, in the one body no declaration keyword opens.

        An anonymous class has no `class Coin` in front of its brace, so nothing can list
        it as a type and read its members. Its methods are told from calls the way every
        other declaration is: by what is written in front of the name.
        """
        modules = self.modules(
            ("Till", "import static shop.till.Prices.of;\n\npublic class Till {\n"
                     "    public Object ring(long id) {\n"
                     "        return new Object() {\n"
                     "            long of(long cents) { return cents; }\n"
                     "        };\n"
                     "    }\n}"),
            ("Prices", A_MODULE_TO_CALL),
        )

        self.assertEqual([], self.reached(modules["Till"]))

    def test_a_call_written_inside_an_anonymous_class_is_still_a_call(self):
        """The other side of the test above: what is declined is declarations, not depth.

        A guard that answered "declaration" for everything written inside a body it
        cannot name would be a fan missing every call a lambda or an anonymous class
        makes, which is a floor low enough to be useless.
        """
        modules = self.modules(
            ("Till", "import static shop.till.Prices.of;\n\npublic class Till {\n"
                     "    public Runnable ring(long id) {\n"
                     "        return new Runnable() {\n"
                     "            public void run() { of(id); }\n"
                     "        };\n"
                     "    }\n}"),
            ("Prices", A_MODULE_TO_CALL),
        )

        self.assertEqual([("module", "Prices")], self.reached(modules["Till"]))
        self.assertEqual(
            "calls of, imported statically from it",
            modules["Till"]["reach"]["reaches"][0]["matched"],
        )

    def test_a_module_that_both_declares_a_name_and_calls_the_import_reports_a_floor(self):
        """What the fix above costs, stated rather than left to be discovered.

        A module declaring `of` and calling the statically imported `of` really does reach
        `Prices`, and this tool does not say so: a declaration and a call are written the
        same way here, and reach errs towards saying less about the source than the source
        says rather than towards saying something the source does not.
        """
        modules = self.modules(
            ("Till", "import static shop.till.Prices.of;\n\npublic class Till {\n"
                     "    public long of(long cents) { return cents; }\n"
                     "    public long ring(long id) { return of(id) + of(1); }\n}"),
            ("Prices", A_MODULE_TO_CALL),
        )

        self.assertEqual([], self.reached(modules["Till"]))

    def test_a_name_declared_one_brace_deeper_and_called_reports_the_same_floor(self):
        """The whole body decides, and the cost of that is stated rather than discovered.

        Here the declaration is a nested class's and the call is the outer method's, so
        Java would read `of(id)` as the imported `Prices.of` — a declaration inside `Coin`
        shadows nothing outside it. This tool does not go that far: it reads the names its
        body declares as one set, because knowing which brace a name was declared under is
        knowing where in the body each call was written too, and half of that is a guess.

        So this reach is dropped. A fan shorter than the source is the direction every
        reading here is willing to be wrong in; a line to a card the module never calls is
        not.
        """
        modules = self.modules(
            ("Till", "import static shop.till.Prices.of;\n\npublic class Till {\n"
                     "    static final class Coin {\n"
                     "        long of(long cents) { return cents; }\n"
                     "    }\n"
                     "    public long ring(long id) { return of(id); }\n}"),
            ("Prices", A_MODULE_TO_CALL),
        )

        self.assertEqual([], self.reached(modules["Till"]))

    def test_a_call_written_out_in_full_is_not_followed_and_the_page_says_so(self):
        """A documented omission rather than a silent one: reach is a floor and says which.

        `shop.till.Prices.zero()` is a call this reader does not follow — it reads the
        name in front of the last dot, and there is a dotted path in front of that. The
        page and the README both name the spelling, because a floor nobody can see the
        edge of is not a floor a reader can trust.
        """
        modules, rendered = self.rendered(
            ("Till", "public class Till {\n"
                     "    public long ring(long id) { return shop.till.Prices.zero(); }\n}"),
            ("Prices", "public class Prices {\n    public static long zero() { return 0; }\n}"),
        )

        self.assertEqual([], self.reached(modules["Till"]))
        self.assertIn("written out in full", rendered)

    def test_a_name_is_followed_through_the_import_that_spells_it(self):
        """Two modules of one name, and the import decides which one is reached."""
        modules = self.modules(
            ("Till", "import shop.stock.Prices;\n\npublic class Till {\n"
                     "    private final Prices prices;\n"
                     "    Till(Prices prices) { this.prices = prices; }\n"
                     "    public long ring(long id) { return prices.of(id); }\n}"),
            ("Prices", A_MODULE_TO_CALL),
            Prices=("shop.stock", A_MODULE_TO_CALL),
        )

        self.assertEqual(
            ["shop.stock.Prices"],
            [entry["moduleId"] for entry in modules["Till"]["reach"]["reaches"]],
        )

    def test_a_name_a_nested_type_shadows_is_not_followed_to_the_module_next_door(self):
        """`Kind.of(x)` inside a module that nests a `Kind` means the nested one.

        That is how Java reads it — a member type shadows the package the file sits in
        and every import above it — and a nested type is not a module, so the name
        reaches nothing. Followed to the top-level `Kind` instead, the page draws a line
        to a card this module never calls, and the collision is not hypothetical: this
        repository already nests a `SavingsAccountResponse` inside
        `CustomerAccountsResponse` while a top-level one sits in the same package.
        """
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    enum Kind {\n"
                     "        COIN;\n"
                     "        static long of(String s) { return 1; }\n"
                     "    }\n"
                     "    public long ring(String s) { return Kind.of(s); }\n}"),
            ("Kind", "public class Kind {\n"
                     "    public static long of(String s) { return 0; }\n}"),
        )

        self.assertEqual([], self.reached(modules["Till"]))
        self.assertEqual(["Kind"], modules["Till"]["nested"])

    def test_a_new_written_out_in_full_builds_the_module_that_name_spells(self):
        """A name written with its package on it means that one and nothing else.

        Two `Receipt`s, one of them persistent, and the `new` says which: the package in
        front of the name is the whole of the answer, and reading only the word after the
        last dot throws it away.
        """
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    public long ring() { return new shop.stock.Receipt(1L).id(); }\n}"),
            ("Receipt", A_RECORD_THAT_IS_NOT_PERSISTENT),
            Receipt=("shop.stock", A_PERSISTENT_RECORD),
        )

        self.assertEqual([("record", "Receipt")], self.reached(modules["Till"]))
        self.assertEqual(
            "shop.stock.Receipt", modules["Till"]["reach"]["reaches"][0]["moduleId"]
        )

    def test_a_new_written_out_in_full_is_not_credited_to_the_module_next_door(self):
        """The same two files with the persistence moved, and the answer moves with it.

        Cut back to `Receipt`, this built the record in its own package: the wrong
        module, a kind the module it named does not have, and an evidence string —
        "builds one: annotated with Entity" — about a class carrying no such annotation.
        """
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    public long ring() { return new shop.stock.Receipt(1L).id(); }\n}"),
            ("Receipt", A_PERSISTENT_RECORD),
            Receipt=("shop.stock", A_RECORD_THAT_IS_NOT_PERSISTENT),
        )

        self.assertEqual([], self.reached(modules["Till"]))

    def test_a_new_written_out_in_full_that_names_nothing_here_reaches_nothing(self):
        """`new java.util.ArrayList<>()` is not this source tree's business."""
        modules = self.modules(
            ("Till", "import java.util.ArrayList;\n\npublic class Till {\n"
                     "    public Object ring() { return new java.util.ArrayList<>(); }\n}"),
            ("Receipt", A_PERSISTENT_RECORD),
        )

        self.assertEqual([], self.reached(modules["Till"]))

    def test_a_call_written_in_a_comment_reaches_nothing(self):
        modules = self.modules(
            ("Till", "public class Till {\n"
                     "    /** Whoever calls this could have called Prices.zero() instead. */\n"
                     "    public long ring(long id) { return 0; }\n}"),
            ("Prices", "public class Prices {\n    public static long zero() { return 0; }\n}"),
        )

        self.assertEqual([], self.reached(modules["Till"]))


class WhatAModuleHoldsIsReadHoweverItIsSpelledTest(unittest.TestCase):
    """A field is how a call through it is followed, so a field dropped is a reach lost.

    These read the parser directly rather than a graph, because what is being established
    is that nothing leaves this file in silence: a member read as a field arrives as one,
    and a member declined says which member and why at DEBUG. A field quietly dropped
    understates a fan with nothing to grep for, in the one file whose whole promise is
    that it does not do that.
    """

    def parsed(self, body):
        return javasource.parse("package shop.till;\n\n" + body + "\n", "Till.java").top_level[0]

    def test_an_array_field_is_read_when_the_brackets_are_written_after_the_name(self):
        """`DepositRepository deposits[]` holds what `DepositRepository[] deposits` holds.

        The header ends in `]`, so there is no name at the end of it to find until the
        brackets come off. Looked for the other way round, this member was declined as
        having no name — and declined with no line at all, because the branch that
        declines it never logged.
        """
        till = self.parsed(
            "public class Till {\n    private DepositRepository deposits[];\n}"
        )

        self.assertEqual(
            [("deposits", "DepositRepository[]")],
            [(field.name, field.written) for field in till.fields],
        )

    def test_both_spellings_of_an_array_field_hold_the_same_thing(self):
        after = self.parsed("public class Till {\n    private int xs[];\n}")
        before = self.parsed("public class Till {\n    private int[] xs;\n}")

        self.assertEqual(
            [(field.name, field.written) for field in before.fields],
            [(field.name, field.written) for field in after.fields],
        )

    def test_a_member_that_is_not_a_field_at_all_is_declined_with_a_line(self):
        """An initialiser block is not a field, and says so where a reader can grep it."""
        with self.assertLogs("module_depth_map.javasource", level=logging.DEBUG) as logged:
            till = self.parsed("public class Till {\n    static { }\n}")

        self.assertEqual((), till.fields)
        self.assertTrue(
            any("member not read as a field" in line for line in logged.output),
            logged.output,
        )


class EveryLineInAFanResolvesTest(SourceOfKnownShapeTest):
    """Nothing is reached that the same document does not also hold.

    The transaction is the one entry with no module behind it, and it carries `moduleId`
    null to say so: it is a thing the module establishes rather than a thing it calls.
    Every other line in every fan names a module id the graph holds, which is what lets a
    reader follow a line to the card at the other end of it.
    """

    def test_every_reach_in_a_fixture_names_a_module_the_graph_holds(self):
        modules = self.modules(("Till", A_DEEP_MODULE), **self.collaborators())
        held = {module["id"] for module in self.document["modules"]}

        for module in self.document["modules"]:
            for entry in module["reach"]["reaches"]:
                if entry["kind"] == "transaction":
                    self.assertIsNone(entry["moduleId"], module["id"])
                else:
                    self.assertIn(entry["moduleId"], held, module["id"])
                    self.assertEqual(
                        entry["name"],
                        modules[entry["name"]]["name"],
                        module["id"],
                    )

    def test_the_count_the_graph_reports_is_the_lines_it_lists(self):
        modules = self.modules(("Till", A_DEEP_MODULE), **self.collaborators())

        for module in modules.values():
            self.assertEqual(
                len(module["reach"]["reaches"]),
                module["reach"]["count"],
                module["id"],
            )
            self.assertEqual(module["reach"]["count"], module["depth"]["reach"], module["id"])

    def test_one_thing_reached_is_one_line_however_it_was_found(self):
        """Two ids in a fan are two lines, and a repeated id would draw two for one thing."""
        modules = self.modules(("Till", A_DEEP_MODULE), **self.collaborators())
        drawn = [entry["moduleId"] for entry in modules["Till"]["reach"]["reaches"]]

        self.assertEqual(len(drawn), len(set(drawn)))


class DepthIsReachOverWhatACallerMustLearnTest(SourceOfKnownShapeTest):
    """The two shapes the page exists to tell apart, each asserting its own numbers."""

    def test_a_deep_module_coordinates_several_things_behind_one_method(self):
        modules = self.modules(("Till", A_DEEP_MODULE), **self.collaborators())
        till = modules["Till"]

        self.assertEqual(
            [
                ("adapter", "ReceiptRepository"),
                ("module", "Prices"),
                ("module", "Shelf"),
                ("record", "Receipt"),
                ("transaction", "a transaction"),
            ],
            self.reached(till),
        )
        self.assertEqual(5, till["reach"]["count"])
        self.assertEqual(2, till["interface"]["cost"])
        self.assertEqual({"reach": 5, "interfaceCost": 2, "leverage": 2.5}, till["depth"])

    def test_a_pass_through_coordinates_one_thing_per_method(self):
        modules = self.modules(("Counter", A_PASS_THROUGH), **self.collaborators())
        counter = modules["Counter"]

        self.assertEqual(
            [("module", "Prices"), ("module", "Shelf")], self.reached(counter)
        )
        self.assertEqual(len(counter["interface"]["methods"]), counter["reach"]["count"])
        self.assertEqual(2, counter["reach"]["count"])
        self.assertEqual(4, counter["interface"]["cost"])
        self.assertEqual({"reach": 2, "interfaceCost": 4, "leverage": 0.5}, counter["depth"])

    def test_the_deep_module_reads_as_deeper_than_the_pass_through(self):
        """The comparison the page is drawn to make, on the two fixtures built to make it."""
        modules = self.modules(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH), **self.collaborators()
        )

        self.assertGreater(
            modules["Till"]["depth"]["leverage"], modules["Counter"]["depth"]["leverage"]
        )
        self.assertGreater(
            modules["Till"]["reach"]["count"], modules["Counter"]["reach"]["count"]
        )
        self.assertLess(
            modules["Till"]["interface"]["cost"], modules["Counter"]["interface"]["cost"]
        )

    def test_depth_carries_the_two_numbers_it_was_taken_from(self):
        """A ratio nobody can check is a ranking. Both terms are in the document."""
        modules = self.modules(("Till", A_DEEP_MODULE), **self.collaborators())
        depth = modules["Till"]["depth"]

        self.assertEqual(modules["Till"]["reach"]["count"], depth["reach"])
        self.assertEqual(modules["Till"]["interface"]["cost"], depth["interfaceCost"])
        self.assertEqual(
            round(depth["reach"] / depth["interfaceCost"], 2), depth["leverage"]
        )

    def test_a_module_with_nothing_on_its_bar_is_given_no_ratio(self):
        """Reach over nothing is not infinite leverage, and is not reported as a number."""
        modules = self.modules(
            ("Till", "class Till {\n    private Till() {}\n}"),
        )

        self.assertEqual(0, modules["Till"]["interface"]["cost"])
        self.assertIsNone(modules["Till"]["depth"]["leverage"])

    def test_a_module_no_rule_scores_is_given_no_ratio_and_still_a_fan(self):
        """An excluded module coordinates whatever it coordinates, and is drawn doing it."""
        modules = self.modules(
            ("Ledger", "import org.springframework.transaction.annotation.Transactional;\n"
                       "import org.springframework.data.jpa.repository.JpaRepository;\n\n"
                       "interface Ledger extends JpaRepository<Receipt, Long> {\n"
                       "    @Transactional\n    int clear();\n}"),
            ("Receipt", A_PERSISTENT_RECORD),
        )

        self.assertEqual("generated repository", modules["Ledger"]["excludedBy"]["rule"])
        self.assertIsNone(modules["Ledger"]["interface"]["cost"])
        self.assertIsNone(modules["Ledger"]["depth"]["leverage"])
        self.assertEqual([("transaction", "a transaction")], self.reached(modules["Ledger"]))


class WritingMoreLinesIsNotDepthTest(SourceOfKnownShapeTest):
    """The measure this one replaced would have made the padded fixture the deepest here.

    Reach is a count of distinct things coordinated, so there is nothing a keyboard can do
    to it. This is the test that says so: the same module, sixty lines longer, the same
    calls made three times each, and every number it is scored on unmoved.
    """

    def test_padding_the_implementation_changes_neither_reach_nor_depth(self):
        plain = self.modules(("Till", A_DEEP_MODULE), **self.collaborators())["Till"]
        padded = self.modules(("Till", A_DEEP_MODULE_PADDED), **self.collaborators())["Till"]

        self.assertGreater(padded["lines"], plain["lines"] * 2)
        self.assertEqual(plain["reach"], padded["reach"])
        self.assertEqual(plain["depth"], padded["depth"])

    def test_the_padded_module_is_no_deeper_than_the_pass_through_is_shallow(self):
        """Lines over lines would rank these the other way round, which is the whole point."""
        modules = self.modules(
            ("Till", A_DEEP_MODULE_PADDED),
            ("Counter", A_PASS_THROUGH),
            **self.collaborators()
        )

        self.assertGreater(modules["Till"]["lines"], modules["Counter"]["lines"])
        self.assertEqual(2.5, modules["Till"]["depth"]["leverage"])
        self.assertEqual(0.5, modules["Counter"]["depth"]["leverage"])


class TheScaleEveryFanIsDrawnAgainstTest(SourceOfKnownShapeTest):
    """One number, in the document, so two fans can be compared by eye and by hand."""

    def test_the_scale_is_the_furthest_reach_the_graph_holds(self):
        modules = self.modules(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH), **self.collaborators()
        )

        self.assertEqual(5, modules["Till"]["reach"]["count"])
        self.assertEqual(5, self.document["scoring"]["widestReach"])

    def test_no_module_reaches_further_than_the_scale_so_no_fan_runs_past_its_card(self):
        self.modules(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH), **self.collaborators()
        )

        for module in self.document["modules"]:
            self.assertLessEqual(
                module["reach"]["count"], self.document["scoring"]["widestReach"], module["id"]
            )

    def test_a_module_no_rule_scores_still_counts_towards_the_scale_it_is_drawn_on(self):
        """Every module is drawn with a fan, so every module sets the scale for one."""
        modules = self.modules(
            ("Ledger", "import org.springframework.transaction.annotation.Transactional;\n"
                       "import org.springframework.data.jpa.repository.JpaRepository;\n\n"
                       "interface Ledger extends JpaRepository<Receipt, Long> {\n"
                       "    @Transactional\n    int clear();\n}"),
            ("Receipt", A_PERSISTENT_RECORD),
        )

        self.assertEqual(1, modules["Ledger"]["reach"]["count"])
        self.assertEqual(1, self.document["scoring"]["widestReach"])


class TheRulesForWhatIsReachedLiveInTheFileTest(SourceOfKnownShapeTest):
    """What an adapter is, what a record is and what a transaction is are all arguable.

    Each is a rule in the configuration file beside the tool, carried into the graph with
    the sentence it is argued for, so the page can print the rule rather than a
    description of one and a reader who disagrees knows which line to edit.
    """

    def test_the_graph_carries_the_reason_each_rule_gives_for_itself(self):
        self.modules(("Till", A_DEEP_MODULE), **self.collaborators())
        reach = self.document["scoring"]["reach"]

        for named in ("adapter", "persistentRecord", "transaction"):
            self.assertTrue(reach[named].strip(), named)
        self.assertEqual(["Transactional"], reach["transactionAnnotations"])

    def test_a_configuration_with_no_reach_rules_is_refused_rather_than_counting_none(self):
        """A missing rule would read as an adapter being nothing, which is a score nobody wrote."""
        import json
        import os

        with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
            document = json.loads(handle.read().decode("utf-8"))
        del document["reach"]
        path = os.path.join(self.scratch, "no-reach.json")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(json.dumps(document))

        with self.assertRaises(scoring.ConfigurationRefused) as refused:
            scoring.load(path)

        self.assertIn("reach", refused.exception.reason)

    def test_changing_what_counts_as_an_adapter_changes_what_the_fan_says(self):
        """The rule is the file's, and the tool reports whatever the file says it is."""
        import json
        import os

        with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
            document = json.loads(handle.read().decode("utf-8"))
        document["reach"]["adapter"]["when"] = {"nameEndsWith": ["Prices"]}
        path = os.path.join(self.scratch, "prices-are-adapters.json")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(json.dumps(document))

        tree = self.tree("fixture")
        tree.java("shop.till", "Till", A_DEEP_MODULE)
        for name, (package, body) in self.collaborators().items():
            tree.java(package, name, body)
        document = graph.build([graph.java_root(tree.root)], scoring.load(path))
        till = {module["name"]: module for module in document["modules"]}["Till"]

        self.assertEqual(
            [
                ("adapter", "Prices"),
                ("module", "ReceiptRepository"),
                ("module", "Shelf"),
                ("record", "Receipt"),
                ("transaction", "a transaction"),
            ],
            self.reached(till),
        )


class AsJavaScriptReadsIt:
    """A document the page's own expressions can be evaluated against, dotted names and all."""

    def __init__(self, value):
        self._value = value

    def __getattr__(self, name):
        found = self._value[name]
        return AsJavaScriptReadsIt(found) if isinstance(found, dict) else found


class TheFanIsDrawnFromTheReachTest(SourceOfKnownShapeTest):
    """The one thing on this page only a browser works out, lifted out and worked out here.

    The same hazard the bar's width had, and the same remedy: a fan whose span was
    hardcoded would leave every module on the page the same shape, the committed page
    regenerated and the whole suite green. So the expression the page draws with is taken
    out of the rendered file as text and evaluated against the document that file carries.
    Nothing else here reads the rendered page: what a fan says is a property of the graph,
    and it is asserted on the graph everywhere above.
    """

    _A_SPAN_IS_SET = re.compile(r"var span = ([^;]+);")
    _A_SHARE_OF_THE_SCALE = re.compile(
        r"\A(?P<when>.+?)\s*\?\s*(?P<then>.+?)\s*:\s*(?P<otherwise>.+?)\Z"
    )

    def test_the_page_draws_one_line_and_one_foot_for_each_thing_reached(self):
        _, rendered = self.rendered(("Till", A_DEEP_MODULE), **self.collaborators())
        fan = rendered[rendered.index("function drawFan"):]
        fan = fan[:fan.index("\n  }")]

        self.assertIn("var reaches = module.reach.reaches;", fan)
        self.assertIn("reaches.forEach(function (reached, index) {", fan)
        self.assertEqual(1, fan.count('draw(fan, "line"'))
        self.assertEqual(1, fan.count('draw(fan, "circle"'))
        self.assertIn("reached.kind", fan)

    def test_a_fan_is_drawn_from_its_own_modules_reach_and_the_scale_in_the_document(self):
        _, rendered = self.rendered(("Till", A_DEEP_MODULE), **self.collaborators())

        spans = self._A_SPAN_IS_SET.findall(rendered)

        self.assertEqual(1, len(spans), spans)
        self.assertIn("module.reach.count", spans[0])
        self.assertIn("furthest", spans[0])
        self.assertIn("var furthest = document_.scoring.widestReach;", rendered)

    def test_the_span_the_page_works_out_is_the_reach_as_a_share_of_the_scale(self):
        """The page's own expression, worked out here for every fan the fixture draws."""
        modules, rendered = self.rendered(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH), **self.collaborators()
        )
        furthest = self.document["scoring"]["widestReach"]
        shape = self._A_SHARE_OF_THE_SCALE.match(
            self._A_SPAN_IS_SET.findall(rendered)[0].strip()
        )
        self.assertIsNotNone(
            shape, "the page sets a span this test cannot read; read it and say what it does"
        )

        drawn = {}
        for name, module in modules.items():
            reached = {
                "module": AsJavaScriptReadsIt(module),
                "furthest": furthest,
                "SHAPE": AsJavaScriptReadsIt(self.shape(rendered)),
            }
            half = "then" if eval(shape.group("when"), {}, reached) else "otherwise"
            drawn[name] = eval(shape.group(half), {}, reached)

        shape_ = self.shape(rendered)
        whole = shape_["width"] - 2 * shape_["foot"] - shape_["ink"]
        self.assertEqual(whole, drawn["Till"])
        self.assertAlmostEqual(whole * 2 / furthest, drawn["Counter"])
        self.assertEqual(0, drawn["Prices"])

    def test_the_widest_fan_on_the_page_is_drawn_whole_rather_than_clipped(self):
        """The card the scale is anchored to is the one a full-width span cuts in half.

        Every other fan is narrower than the widest and lands well inside its card, so the
        one card that can be clipped is the specimen — the fan every other fan on the page
        is read against, and the one a reader looks at first. Its outermost feet are
        circles drawn at the ends of its span, so the span has to stop a foot short of
        each edge of the viewBox.
        """
        modules, rendered = self.rendered(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH), **self.collaborators()
        )
        shape_ = self.shape(rendered)
        furthest = self.document["scoring"]["widestReach"]
        self.assertEqual(
            furthest,
            modules["Till"]["reach"]["count"],
            "this fixture no longer holds the fan the scale comes from",
        )

        feet = self.feet(rendered, modules["Till"], furthest, shape_)

        takes_up = shape_["foot"] + shape_["ink"] / 2.0
        self.assertEqual(furthest, len(feet))
        self.assertGreaterEqual(min(feet), takes_up)
        self.assertLessEqual(max(feet), shape_["width"] - takes_up)

    def test_a_fan_narrower_than_the_widest_is_still_centred_on_its_card(self):
        """Insetting the span must not shift a fan off the middle of the bar above it."""
        modules, rendered = self.rendered(
            ("Till", A_DEEP_MODULE), ("Counter", A_PASS_THROUGH), **self.collaborators()
        )
        shape_ = self.shape(rendered)
        furthest = self.document["scoring"]["widestReach"]

        feet = self.feet(rendered, modules["Counter"], furthest, shape_)

        self.assertAlmostEqual(shape_["width"] / 2, (min(feet) + max(feet)) / 2)

    def test_the_page_divides_by_nothing_when_no_module_reaches_anything(self):
        """A page of modules that coordinate nothing reaches the same expression with 0."""
        modules, rendered = self.rendered(
            ("Till", "public class Till {\n    public long ring(long id) { return id; }\n}")
        )
        shape = self._A_SHARE_OF_THE_SCALE.match(
            self._A_SPAN_IS_SET.findall(rendered)[0].strip()
        )
        reached = {
            "module": AsJavaScriptReadsIt(modules["Till"]),
            "furthest": 0,
            "SHAPE": AsJavaScriptReadsIt(self.shape(rendered)),
        }

        self.assertEqual(0, self.document["scoring"]["widestReach"])
        self.assertFalse(eval(shape.group("when"), {}, reached))
        self.assertEqual(0, eval(shape.group("otherwise"), {}, reached))

    def feet(self, rendered, module, furthest, shape_):
        """Where the page puts each foot of one module's fan, by its own two expressions.

        Both are lifted out of the rendered file as text and worked out here, for the
        reason the class docstring gives: a geometry asserted against numbers written down
        in this file would stay green through any change to the page that draws it.
        """
        reaches = module["reach"]["reaches"]
        known = {
            "module": AsJavaScriptReadsIt(module),
            "furthest": furthest,
            "SHAPE": AsJavaScriptReadsIt(shape_),
            "reaches": AsJavaScriptReadsIt({"length": len(reaches)}),
        }
        span = self._A_SHARE_OF_THE_SCALE.match(
            self._A_SPAN_IS_SET.findall(rendered)[0].strip()
        )
        half = "then" if eval(span.group("when"), {}, known) else "otherwise"
        known["span"] = eval(span.group(half), {}, known)

        written = re.search(r"var foot = (.+?);", rendered, re.S).group(1)
        placed = self._A_SHARE_OF_THE_SCALE.match(
            " ".join(written.split()).replace("===", "==")
        )
        self.assertIsNotNone(
            placed, "the page places a foot in a way this test cannot read; read it"
        )
        drawn = []
        for index in range(len(reaches)):
            known["index"] = index
            half = "then" if eval(placed.group("when"), {}, known) else "otherwise"
            drawn.append(eval(placed.group(half), {}, known))
        return drawn

    def shape(self, rendered):
        """The geometry the page draws every fan to, read out of the page that holds it."""
        written = re.search(r"var SHAPE = \{(.+?)\};", rendered).group(1)
        return {
            part.split(":")[0].strip(): int(part.split(":")[1])
            for part in written.split(",")
        }


class TheShapeIsDrawnInOnePassTest(SourceOfKnownShapeTest):
    """The renderer walks the document once, so anything it throws on it never finishes.

    A throw halfway down reads as a page that stopped early rather than as a page that
    failed: every package section after the module that threw is simply absent, with
    nothing saying why. The rule that follows is written over `drawInterface` in the page
    and asserted here, because a rule kept only in a comment is one the next thing drawn
    beside it can break silently — which is how the fan's own bar came to break it.
    """

    def body(self, rendered, name):
        """One function of the page's script, from its opening line to its closing brace."""
        found = rendered[rendered.index("function " + name):]
        return found[:found.index("\n  }")]

    def test_nothing_decides_an_exclusion_by_looking_at_the_cost_that_is_missing(self):
        """A module with an exclusion but a cost, or a cost but none, is still drawable.

        Cost is null exactly when `excludedBy` is set today, and a renderer that reads the
        rule off `excludedBy` having decided on `cost === null` is one document away from
        throwing. It costs nothing to branch on the fact that carries the exclusion.
        """
        _, rendered = self.rendered(("Till", A_DEEP_MODULE), **self.collaborators())

        for name in ["drawShape", "drawInterface"]:
            body = self.body(rendered, name)
            self.assertIn("module.excludedBy", body, name)
            self.assertNotIn("cost !== null", body, name)
            self.assertNotIn("cost === null", body, name)

    def test_the_bar_and_the_fan_are_drawn_for_a_module_no_rule_scores(self):
        """The one shape both branches have to produce, on a module that has no cost."""
        modules = self.modules(
            ("Till", A_DEEP_MODULE),
            ("Ledger", AN_ADAPTER.replace("ReceiptRepository", "Ledger")),
            **self.collaborators()
        )

        self.assertIsNone(modules["Ledger"]["interface"]["cost"])
        self.assertEqual("generated repository", modules["Ledger"]["excludedBy"]["rule"])
        self.assertEqual(0, modules["Ledger"]["reach"]["count"])
