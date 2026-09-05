"""Three business events, each traceable across the modules it passes through.

A flow is defined by one thing and one thing only: the call a caller makes to enter it.
Everything else about it — which modules it passes through, and in what order — is walked
out of the calls that call's body makes, and then out of the calls each of *those* bodies
makes, so a flow is a reading of the code rather than a second description of the code
kept beside it by hand.

The grain is the method, and the tests below hold it there. Walked over each module's
reach instead, a flow is the entry module's whole transitive reach: two flows differing
only in their method come back identical, and the path names every module the entry class
touches through any of its methods rather than the ones this call goes through. Reach is a
set of distinct things and has to be — that is what stops a module raising its depth by
writing more calls — and a set has neither an order nor any idea which method wrote it.

The tests here drive the same seam as the rest of the suite: source directory in, graph
document out, and the page asserted as a rendering of that document. What is established
is the property rather than the itinerary — that a flow is walked and not written down,
that every module it names is a module the graph holds, and that a walk which cannot start
ends in no path rather than in a shorter one.
"""

import json
import logging
import os
import re

from ... import graph, page, scoring
from ..support.sourcetrees import BACKEND_SOURCE, SourceTreeTest

TOOL = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
REPOSITORY = os.path.dirname(os.path.dirname(TOOL))

# The entry point of the fixture's flow: two collaborators, an adapter and a record it
# builds, so that the walk out of it has more than one kind of thing to follow.
AN_ENTRY_POINT = """public class Till {
    private final Prices prices;
    private final Rates rates;
    private final ReceiptRepository receipts;
    private final Ledger ledger;

    Till(Prices prices, Rates rates, ReceiptRepository receipts, Ledger ledger) {
        this.prices = prices;
        this.rates = rates;
        this.receipts = receipts;
        this.ledger = ledger;
    }

    public Receipt ring(long id) {
        prices.of(id);
        rates.today();
        receipts.count();
        return new Receipt();
    }

    public long stocktake() {
        return ledger.post();
    }
}"""

# One module further along the flow, reaching one more.
A_MODULE_ON_THE_WAY = """public class Prices {
    private final Rates rates;

    Prices(Rates rates) { this.rates = rates; }

    public long of(long id) { return rates.today(); }
}"""

# The far end, and it calls back the way it came: the graph has cycles in it, and a flow
# is a path through modules rather than a transcript of calls.
A_MODULE_THAT_CALLS_BACK = """public class Rates {
    private final Prices prices;

    Rates(Prices prices) { this.prices = prices; }

    public long today() { return prices.of(1); }
}"""

AN_ADAPTER = (
    "import org.springframework.data.repository.CrudRepository;\n\n"
    "public interface ReceiptRepository extends CrudRepository<Receipt, Long> {}"
)

A_PERSISTENT_RECORD = (
    "@Entity\nclass Receipt {\n"
    "    @Id private long id;\n"
    "    public long id() { return id; }\n"
    "}"
)

# A module no flow in this fixture ever reaches, and which reaches nothing itself. It is
# both the thing a flow must leave off and the entry point that cannot start a walk.
A_MODULE_OFF_EVERY_FLOW = """public class Kiosk {
    public void open() {}
}"""

# Reached by the entry module and by no call the flow makes: `Till.stocktake` calls it and
# `Till.ring` does not. It is in the entry module's reach, and a flow read off that reach
# puts it on the path — which is the whole difference between a flow through a call and a
# claim about everything the entry class does.
A_MODULE_ONLY_ANOTHER_METHOD_REACHES = """public class Ledger {
    public long post() { return 1; }
}"""

# A module whose one call writes another call inside its arguments. Java builds the receipt
# before it saves it, and a path that read the characters left to right numbered the
# repository ahead of the record it was handed.
A_CALL_INSIDE_ANOTHERS_ARGUMENTS = """public class Bagger {
    private final ReceiptRepository receipts;

    Bagger(ReceiptRepository receipts) { this.receipts = receipts; }

    public Receipt bag() { return receipts.save(new Receipt()); }
}"""

# An interface: it presents a call a caller can make and writes no body under it, so there
# is nothing here for a walk to follow.
A_PROMISE_WITH_NO_BODY_UNDER_IT = """public interface Tills {
    Receipt ring(long id);
}"""


class FlowTest(SourceTreeTest):
    """A source tree with one flow through it, and configurations that name flows in it."""

    def setUp(self):
        super().setUp()
        with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
            self.as_committed = json.loads(handle.read().decode("utf-8"))
        self.written = 0
        tree = self.tree("fixture")
        for name, body in (
            ("Till", AN_ENTRY_POINT),
            ("Prices", A_MODULE_ON_THE_WAY),
            ("Rates", A_MODULE_THAT_CALLS_BACK),
            ("ReceiptRepository", AN_ADAPTER),
            ("Receipt", A_PERSISTENT_RECORD),
            ("Kiosk", A_MODULE_OFF_EVERY_FLOW),
            ("Ledger", A_MODULE_ONLY_ANOTHER_METHOD_REACHES),
            ("Bagger", A_CALL_INSIDE_ANOTHERS_ARGUMENTS),
            ("Tills", A_PROMISE_WITH_NO_BODY_UNDER_IT),
        ):
            tree.java("shop.till", name, body)
        self.root = graph.java_root(tree.root)

    def rules_naming(self, *flows):
        """The tool's own configuration with these flows in place of the ones it ships."""
        document = dict(self.as_committed, flows=list(flows))
        self.written += 1
        path = os.path.join(self.scratch, "rules-%d.json" % self.written)
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(json.dumps(document, indent=2))
        return path

    def a_flow(self, module="shop.till.Till", method="ring", flow="a sale"):
        return {
            "flow": flow,
            "because": "One thing this fixture does, worth following end to end.",
            "entryPoint": {"module": module, "method": method},
        }

    def flows_of(self, *flows):
        """The flows the graph holds when the configuration names these."""
        document = graph.build([self.root], scoring.load(self.rules_naming(*flows)))
        return document["flows"]

    def module(self, module_id):
        """One module of the graph this fixture builds, for reading its fan off."""
        document = graph.build([self.root], scoring.load(self.rules_naming(self.a_flow())))
        return {module["id"]: module for module in document["modules"]}[module_id]

    def one_flow(self, **entry_point):
        flows = self.flows_of(self.a_flow(**entry_point))
        self.assertEqual(1, len(flows))
        return flows[0]

    def refusal_for(self, *flows):
        """The reason the tool gave for refusing a configuration naming these flows."""
        with self.assertRaises(scoring.ConfigurationRefused) as refused:
            scoring.load(self.rules_naming(*flows))
        return refused.exception.reason


class AFlowIsWalkedOutOfTheCallGraphTest(FlowTest):
    """The modules a flow passes through are read from the fans, never listed by hand."""

    def test_the_flow_passes_through_every_module_the_entry_call_reaches(self):
        """`ring` calls Prices, then Rates, then the repository, then builds a receipt.

        In that order, because that is the order the source makes the calls in. Not the
        order the entry module's fan holds them — that is sorted by kind and then by name,
        which would put the adapter first — and not the order the cards happen to be laid
        out in either.
        """
        flow = self.one_flow()

        self.assertTrue(flow["resolved"], flow["couldNotResolve"])
        self.assertEqual(
            ["Till", "Prices", "Rates", "ReceiptRepository", "Receipt"],
            [step["name"] for step in flow["path"]],
        )

    def test_the_order_is_the_calls_order_and_not_the_fans(self):
        """The fan is sorted by kind and then by name; a flow is sorted by nothing."""
        flow = self.one_flow()
        entry = self.module("shop.till.Till")

        self.assertEqual(
            ["ReceiptRepository", "Ledger", "Prices", "Rates", "Receipt"],
            [reached["name"] for reached in entry["reach"]["reaches"]],
        )
        self.assertEqual(
            ["Prices", "Rates", "ReceiptRepository", "Receipt"],
            [step["name"] for step in flow["path"][1:]],
        )

    def test_a_module_only_another_method_of_the_entry_reaches_is_not_on_the_flow(self):
        """The one thing method grain buys, and the reason a flow names its method.

        `Till.stocktake` calls the ledger and `Till.ring` does not, so the ledger is in
        the entry module's reach and is not on this flow. Read off that reach instead, a
        flow through `ring` would claim a call to `ring` posts to the ledger.
        """
        flow = self.one_flow()

        self.assertIn("shop.till.Ledger", [
            reached["moduleId"] for reached in self.module("shop.till.Till")["reach"]["reaches"]
        ])
        self.assertNotIn("Ledger", [step["name"] for step in flow["path"]])

    def test_two_flows_differing_only_in_their_method_are_two_different_paths(self):
        """Otherwise the method is decoration on the entry point rather than half of it."""
        flows = self.flows_of(
            self.a_flow(flow="a sale"),
            self.a_flow(method="stocktake", flow="a stocktake"),
        )

        self.assertEqual(
            ["Till", "Prices", "Rates", "ReceiptRepository", "Receipt"],
            [step["name"] for step in flows[0]["path"]],
        )
        self.assertEqual(["Till", "Ledger"], [step["name"] for step in flows[1]["path"]])

    def test_a_call_a_private_helper_makes_is_on_the_flow(self):
        """A call on the module's own method is followed and is not a step of its own.

        `Rates` is reached by `Prices.of` rather than by anything `Till.ring` writes, and
        a walk that stopped at the methods a caller can see would report this sale as
        touching one module.
        """
        flow = self.one_flow()
        reached_from = {step["name"]: step["reachedFrom"] for step in flow["path"][1:]}

        self.assertEqual("shop.till.Prices", reached_from["Rates"])

    def test_a_call_written_inside_anothers_arguments_is_the_earlier_step(self):
        """Java builds the receipt before it saves it, whatever order the characters are in."""
        flow = self.one_flow(module="shop.till.Bagger", method="bag")

        self.assertEqual(
            ["Bagger", "Receipt", "ReceiptRepository"],
            [step["name"] for step in flow["path"]],
        )

    def test_every_step_says_which_call_put_the_flow_there(self):
        """A step a reader cannot go and look up is a picture rather than a reading."""
        flow = self.one_flow()
        by_name = {step["name"]: step for step in flow["path"]}

        self.assertEqual("ring", by_name["Prices"]["calledFrom"])
        self.assertEqual("prices.of", by_name["Prices"]["call"])
        self.assertEqual("of", by_name["Rates"]["calledFrom"])
        self.assertEqual("rates.today", by_name["Rates"]["call"])
        self.assertEqual("new Receipt", by_name["Receipt"]["call"])
        self.assertIsNone(flow["path"][0]["calledFrom"])
        self.assertIsNone(flow["path"][0]["call"])

    def test_the_order_is_the_order_the_walk_enters_them(self):
        """Numbered from the entry point outwards, one number per module, no gaps."""
        flow = self.one_flow()

        self.assertEqual(
            list(range(1, len(flow["path"]) + 1)),
            [step["step"] for step in flow["path"]],
        )
        self.assertEqual(flow["modules"], len(flow["path"]))

    def test_the_entry_point_is_the_first_module_and_is_reached_from_nothing(self):
        flow = self.one_flow()
        entered = flow["path"][0]

        self.assertEqual("shop.till.Till", entered["moduleId"])
        self.assertEqual(1, entered["step"])
        self.assertIsNone(entered["reachedFrom"])
        self.assertIn("ring", entered["matched"])
        self.assertEqual(
            {"moduleId": "shop.till.Till", "method": "ring"}, flow["entryPoint"]
        )

    def test_every_step_after_the_first_says_which_module_it_was_reached_from(self):
        """A path a reader cannot check is a picture rather than a reading of the code."""
        flow = self.one_flow()
        reached_from = {
            step["name"]: step["reachedFrom"] for step in flow["path"][1:]
        }

        self.assertEqual(
            {
                "ReceiptRepository": "shop.till.Till",
                "Prices": "shop.till.Till",
                "Rates": "shop.till.Prices",
                "Receipt": "shop.till.Till",
            },
            reached_from,
        )

    def test_each_step_carries_the_words_the_fan_was_read_with(self):
        """The same sentence the card prints under the module it drew a line to."""
        flow = self.one_flow()
        matched = {step["name"]: step["matched"] for step in flow["path"]}

        self.assertIn("prices", matched["Prices"])
        self.assertEqual("builds one", matched["Receipt"].split(":")[0])

    def test_a_module_reached_twice_is_one_step_at_the_place_it_was_first_entered(self):
        """The graph has cycles in it, and a flow is a path rather than a transcript.

        `Rates` is reached from the entry point directly and from `Prices` on the way,
        and `Rates` calls `Prices` back. Walked without a guard that is a flow with no
        end; walked with one it is a module entered once, at the step it was first
        entered at.
        """
        flow = self.one_flow()
        names = [step["name"] for step in flow["path"]]

        self.assertEqual(sorted(set(names)), sorted(names))
        self.assertEqual(3, dict(zip(names, [step["step"] for step in flow["path"]]))["Rates"])

    def test_a_module_the_flow_never_reaches_is_not_on_it(self):
        """Otherwise choosing a flow would highlight the application and say nothing."""
        flow = self.one_flow()

        self.assertNotIn("Kiosk", [step["name"] for step in flow["path"]])

    def test_the_transaction_a_module_establishes_is_reached_and_is_not_a_step(self):
        """A flow passes through modules; nothing on the page could be highlighted for it."""
        document = graph.build(
            [graph.java_root(BACKEND_SOURCE)], scoring.load()
        )
        by_id = {module["id"]: module for module in document["modules"]}
        deposit = [flow for flow in document["flows"] if flow["flow"] == "a deposit"][0]
        entry = by_id[deposit["entryPoint"]["moduleId"]]

        self.assertIn(
            "transaction", [reached["kind"] for reached in entry["reach"]["reaches"]]
        )
        for step in deposit["path"]:
            self.assertIsNotNone(step["moduleId"])


class EveryModuleNamedInAFlowIsOneTheGraphHoldsTest(FlowTest):
    """A step pointing at a card that is not on the page is a flow about nothing."""

    def test_every_step_of_every_flow_names_a_module_in_the_document(self):
        document = graph.build(
            [self.root],
            scoring.load(self.rules_naming(self.a_flow(), self.a_flow(flow="another sale"))),
        )
        held = {module["id"] for module in document["modules"]}

        self.assertNotEqual([], document["flows"])
        for flow in document["flows"]:
            self.assertNotEqual([], flow["path"], flow["flow"])
            for step in flow["path"]:
                self.assertIn(step["moduleId"], held)

    def test_every_module_a_step_says_it_was_reached_from_is_in_the_document_too(self):
        document = graph.build([self.root], scoring.load(self.rules_naming(self.a_flow())))
        held = {module["id"] for module in document["modules"]}

        for flow in document["flows"]:
            for step in flow["path"]:
                if step["reachedFrom"] is not None:
                    self.assertIn(step["reachedFrom"], held)

    def test_a_step_names_the_module_by_the_name_and_package_the_document_gave_it(self):
        document = graph.build([self.root], scoring.load(self.rules_naming(self.a_flow())))
        by_id = {module["id"]: module for module in document["modules"]}

        for step in document["flows"][0]["path"]:
            self.assertEqual(by_id[step["moduleId"]]["name"], step["name"])
            self.assertEqual(by_id[step["moduleId"]]["package"], step["package"])


class AFlowThatCannotBeWalkedFailsRatherThanShortensTest(FlowTest):
    """Four ways a walk can fail, and one answer to all four: no path at all.

    A flow half walked is the one output worse than no flow. Every module on it is real,
    the path is followable, and the business event it claims to trace stopped happening
    that way some commits ago — so a reader is misled by something that looks checked.
    """

    def test_a_flow_whose_entry_point_is_not_in_the_graph_carries_no_path(self):
        flow = self.one_flow(module="shop.till.Nowhere")

        self.assertFalse(flow["resolved"])
        self.assertEqual([], flow["path"])
        self.assertEqual(0, flow["modules"])
        self.assertIn("shop.till.Nowhere", flow["couldNotResolve"])
        self.assertIn("nowhere for this flow to start", flow["couldNotResolve"])

    def test_a_flow_whose_method_no_caller_can_reach_carries_no_path(self):
        """The module is still there; the call the flow is entered by is not.

        This is the drift a flow is supposed to fail on rather than quietly survive: the
        walk out of the module would resolve perfectly well and would be a path through
        an event nobody can enter any more.
        """
        flow = self.one_flow(method="cashUp")

        self.assertFalse(flow["resolved"])
        self.assertEqual([], flow["path"])
        self.assertIn("cashUp", flow["couldNotResolve"])
        self.assertIn("ring", flow["couldNotResolve"])

    def test_a_flow_whose_entry_call_reaches_nothing_carries_no_path(self):
        flow = self.one_flow(module="shop.till.Kiosk", method="open")

        self.assertFalse(flow["resolved"])
        self.assertEqual([], flow["path"])
        self.assertIn("reaches no other module this graph holds", flow["couldNotResolve"])
        self.assertIn("open", flow["couldNotResolve"])

    def test_a_flow_entered_through_a_call_with_no_body_under_it_carries_no_path(self):
        """An interface promises the call and writes none of it, so there is nothing to follow.

        Named as its own failure rather than left to come out as "reaches nothing", because
        the two send a reader to different places: one method emptied out, or a flow
        pointed at the promise instead of at the module that keeps it.
        """
        flow = self.one_flow(module="shop.till.Tills", method="ring")

        self.assertFalse(flow["resolved"])
        self.assertEqual([], flow["path"])
        self.assertIn("writes no body for it here", flow["couldNotResolve"])

    def test_the_run_says_so_out_loud_rather_than_leaving_it_to_the_page(self):
        with self.assertLogs("module_depth_map.graph", level=logging.WARNING) as logged:
            graph.build(
                [self.root],
                scoring.load(self.rules_naming(self.a_flow(module="shop.till.Nowhere"))),
            )

        said = "\n".join(logged.output)
        self.assertIn("flow not traced", said)
        self.assertIn("a sale", said)
        self.assertIn("no path rather than a shorter one", said)

    def test_a_flow_that_could_not_be_walked_never_stops_the_ones_that_can(self):
        """One stale flow is a finding about that flow, not about the other two."""
        flows = self.flows_of(
            self.a_flow(module="shop.till.Nowhere", flow="a sale that moved"),
            self.a_flow(flow="a sale"),
        )

        self.assertEqual([False, True], [flow["resolved"] for flow in flows])
        self.assertEqual(5, flows[1]["modules"])


class TheFlowsAreDefinedByTheirEntryPointInTheConfigurationTest(FlowTest):
    """The file decides where a flow starts, and decides nothing else about it."""

    def test_moving_a_flows_entry_point_moves_the_whole_path_with_it(self):
        """Which is the whole of what "derived, not listed" means here."""
        from_the_till = self.one_flow()
        from_prices = self.one_flow(module="shop.till.Prices", method="of")

        self.assertEqual(
            ["Till", "Prices", "Rates", "ReceiptRepository", "Receipt"],
            [step["name"] for step in from_the_till["path"]],
        )
        self.assertEqual(
            ["Prices", "Rates"], [step["name"] for step in from_prices["path"]]
        )

    def test_the_order_the_file_writes_the_flows_in_is_the_order_they_are_offered_in(self):
        """Which flow a reader is shown first is a decision somebody made in the file."""
        flows = self.flows_of(
            self.a_flow(flow="the second one"), self.a_flow(flow="the first one")
        )

        self.assertEqual(
            ["the second one", "the first one"], [flow["flow"] for flow in flows]
        )

    def test_a_flow_carries_the_reason_it_is_worth_tracing_from_the_file(self):
        flow = self.one_flow()

        self.assertEqual(
            "One thing this fixture does, worth following end to end.", flow["because"]
        )

    def test_a_configuration_naming_no_flow_is_a_position_somebody_can_hold(self):
        self.assertEqual([], self.flows_of())

    def test_a_configuration_with_no_flows_key_at_all_is_refused(self):
        """Deciding nothing and deciding "none" are different answers."""
        document = dict(self.as_committed)
        del document["flows"]
        path = os.path.join(self.scratch, "no-flows.json")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(json.dumps(document, indent=2))

        with self.assertRaises(scoring.ConfigurationRefused) as refused:
            scoring.load(path)

        self.assertIn("flows", refused.exception.reason)

    def test_a_flow_nobody_can_name_is_refused(self):
        reason = self.refusal_for(dict(self.a_flow(), flow="  "))

        self.assertIn("flows[0].flow", reason)
        self.assertIn("nobody can name", reason)

    def test_a_flow_with_no_reason_written_on_it_is_refused(self):
        reason = self.refusal_for(dict(self.a_flow(), because=""))

        self.assertIn("flows[0].because", reason)

    def test_two_flows_of_one_name_are_refused(self):
        reason = self.refusal_for(self.a_flow(), self.a_flow())

        self.assertIn("flows[1].flow", reason)
        self.assertIn("could not tell which they had chosen", reason)

    def test_an_entry_point_written_as_a_simple_name_is_refused(self):
        """Two packages can hold a module of one name, and a flow may not pick one."""
        reason = self.refusal_for(self.a_flow(module="Till"))

        self.assertIn("flows[0].entryPoint.module", reason)
        self.assertIn("written in full", reason)

    def test_an_entry_point_with_no_method_on_it_is_refused(self):
        entry = self.a_flow()
        del entry["entryPoint"]["method"]
        reason = self.refusal_for(entry)

        self.assertIn("flows[0].entryPoint.method", reason)
        self.assertIn("entered by calling one method", reason)

    def test_a_key_this_tool_does_not_read_is_refused_rather_than_ignored(self):
        """A path written into the file would look like a rule and would do nothing."""
        reason = self.refusal_for(dict(self.a_flow(), through=["shop.till.Prices"]))

        self.assertIn("flows[0] names through", reason)

    def test_an_entry_point_naming_something_beside_a_module_and_a_method_is_refused(self):
        entry = self.a_flow()
        entry["entryPoint"]["step"] = 2
        reason = self.refusal_for(entry)

        self.assertIn("flows[0].entryPoint names step", reason)


class TheFlowsThisRepositoryShipsTest(SourceTreeTest):
    """The three things this application actually does, over this application's source."""

    def setUp(self):
        super().setUp()
        self.document = graph.build([graph.java_root(BACKEND_SOURCE)], scoring.load())
        self.flows = {flow["flow"]: flow for flow in self.document["flows"]}

    def test_a_deposit_a_withdrawal_and_a_reward_claim_are_each_available_as_a_flow(self):
        self.assertEqual(
            ["a deposit", "a withdrawal, and the deposits it draws down", "a reward claimed"],
            [flow["flow"] for flow in self.document["flows"]],
        )

    def test_every_flow_resolves_to_a_real_path_through_real_modules(self):
        """The property, and the one that fails when the code moves under a flow."""
        held = {module["id"] for module in self.document["modules"]}

        for flow in self.document["flows"]:
            self.assertTrue(flow["resolved"], "%s: %s" % (flow["flow"], flow["couldNotResolve"]))
            self.assertIsNone(flow["couldNotResolve"])
            self.assertNotEqual([], flow["path"], flow["flow"])
            for step in flow["path"]:
                self.assertIn(step["moduleId"], held)

    def test_each_flow_is_entered_through_the_module_that_coordinates_that_event(self):
        self.assertEqual(
            {
                "a deposit": (
                    "io.dataroots.savingstreak.deposits.DepositsService", "deposit"),
                "a withdrawal, and the deposits it draws down": (
                    "io.dataroots.savingstreak.deposits.WithdrawalsService", "withdraw"),
                "a reward claimed": (
                    "io.dataroots.savingstreak.rewards.RewardsService", "claim"),
            },
            {
                name: (flow["entryPoint"]["moduleId"], flow["entryPoint"]["method"])
                for name, flow in self.flows.items()
            },
        )

    def test_a_withdrawal_passes_through_the_deposits_it_draws_down(self):
        """The one flow that cannot be read off a single module, and the reason it is here."""
        withdrawal = [
            step["name"]
            for step in self.flows["a withdrawal, and the deposits it draws down"]["path"]
        ]

        self.assertIn("DepositRepository", withdrawal)
        self.assertIn("WithdrawalAllocation", withdrawal)

    def test_a_deposit_passes_through_the_points_a_euro_earns(self):
        deposit = [step["name"] for step in self.flows["a deposit"]["path"]]

        self.assertIn("PointsService", deposit)
        self.assertIn("Deposit", deposit)

    def test_a_reward_claim_passes_through_the_points_it_spends(self):
        claim = [step["name"] for step in self.flows["a reward claimed"]["path"]]

        self.assertIn("PointsService", claim)
        self.assertIn("Redemption", claim)

    def test_every_step_is_a_reach_the_module_it_came_from_already_has(self):
        """The two readings of one source, held against each other.

        A flow follows a name the way reach does — the same resolver, the same imports,
        the same package — so every edge on a path has to be an edge in the fan the card
        draws. An edge the fan does not hold would be the one fault this tool cannot
        afford: a path drawn from one reading of the source and a shape drawn from
        another, with a reader unable to say which of the two the page was showing them.
        """
        by_id = {module["id"]: module for module in self.document["modules"]}

        for flow in self.document["flows"]:
            for step in flow["path"][1:]:
                reaches = {
                    reached["moduleId"]
                    for reached in by_id[step["reachedFrom"]]["reach"]["reaches"]
                }
                self.assertIn(
                    step["moduleId"],
                    reaches,
                    "%s: %s is on the path from %s, which does not reach it"
                    % (flow["flow"], step["moduleId"], step["reachedFrom"]),
                )

    def test_a_module_is_never_numbered_before_the_module_that_called_it(self):
        """What "in the order it passes through them" has to mean, at minimum.

        A flow cannot arrive somewhere before it arrives at whatever sent it there. The
        walk gives every module the step it was first entered at, so this holds for any
        source; it stops holding the moment the path is ordered by anything other than
        the walk — sorting the steps by kind and name breaks it on the first flow.
        """
        for flow in self.document["flows"]:
            at = {step["moduleId"]: step["step"] for step in flow["path"]}
            for step in flow["path"][1:]:
                self.assertLess(
                    at[step["reachedFrom"]],
                    step["step"],
                    "%s: %s is numbered before %s, which called it"
                    % (flow["flow"], step["moduleId"], step["reachedFrom"]),
                )

    def test_a_deposit_asks_the_accounts_before_it_records_anything(self):
        """The order of the event, which is the order `DepositsService.deposit` is written in.

        The accounts are asked first — a deposit is a movement between two of them, and if
        there is no such movement to make the amount is beside the point — and the deposit
        is saved after. Read off the entry module's fan instead, the steps come out sorted
        by kind and then by name, which puts the repository first and tells a reader the
        story backwards.
        """
        at = {step["name"]: step["step"] for step in self.flows["a deposit"]["path"]}

        self.assertLess(at["AccountsService"], at["DepositRepository"])
        self.assertLess(at["DepositRepository"], at["PointsCreditRepository"])

    def test_a_deposit_does_not_pass_through_the_customers_table(self):
        """A deposit names both accounts by identifier and never reads a customer.

        `AccountsService.accountsOf` does, and `CustomerAccounts` is built nowhere else, so
        both are in the entry module's transitive reach and neither is on this flow. Held
        as a test because a flow read off that reach put both of them on the page, and the
        SQL a deposit actually runs names no `customer` table at all.
        """
        deposit = [step["name"] for step in self.flows["a deposit"]["path"]]

        self.assertNotIn("CustomerRepository", deposit)
        self.assertNotIn("CustomerAccounts", deposit)

    def test_the_three_flows_are_three_different_paths(self):
        """Otherwise one entry point is doing the work of three, and none of them traces."""
        paths = [
            tuple(step["moduleId"] for step in flow["path"])
            for flow in self.document["flows"]
        ]

        self.assertEqual(3, len(set(paths)))

    def test_no_path_through_the_modules_is_written_into_the_configuration(self):
        """A flow is its entry point. Anything else in the file would be a hand-drawn path.

        Read mechanically rather than asserted by eye: every module id the flows section
        of the checked-in configuration holds has to be one of the three entry points, and
        every key on a flow has to be one of the three this tool reads.
        """
        with open(scoring.DEFAULT_CONFIGURATION, "rb") as handle:
            written = json.loads(handle.read().decode("utf-8"))
        entry_points = {
            flow["entryPoint"]["module"] for flow in written["flows"]
        }
        named = set(
            re.findall(r"io\.dataroots\.savingstreak\.[\w.]+", json.dumps(written["flows"]))
        )

        self.assertEqual(entry_points, named)
        for flow in written["flows"]:
            self.assertEqual({"flow", "because", "entryPoint"}, set(flow))
            self.assertEqual({"module", "method"}, set(flow["entryPoint"]))


class ThePageDrawsTheFlowsItIsGivenTest(FlowTest):
    """The chooser, the highlighting, and the clearing — all read off the document."""

    def setUp(self):
        super().setUp()
        self.document = graph.build(
            [self.root],
            scoring.load(
                self.rules_naming(
                    self.a_flow(),
                    self.a_flow(module="shop.till.Nowhere", flow="a sale that moved"),
                )
            ),
        )
        self.rendered = page.render(
            self.document, graph.serialise(self.document)
        ).decode("utf-8")

    def body(self, name):
        found = self.rendered[self.rendered.index("function " + name):]
        return found[:found.index("\n  }")]

    def test_the_page_offers_one_button_per_flow_and_one_that_clears_the_choice(self):
        drawn = self.body("drawFlows")

        self.assertIn("document_.flows.forEach", drawn)
        self.assertIn('add(add(chooser, "li"), "button", "flow", flow.flow)', drawn)
        self.assertIn('"button", "clear", "Clear"', drawn)

    def test_choosing_a_flow_marks_every_module_on_it_and_sets_the_rest_aside(self):
        drawn = self.body("chooseFlow")

        self.assertIn("flow.path.forEach", drawn)
        self.assertIn('card.className = step ? "module onTheFlow" : "module aside"', drawn)

    def test_the_number_on_a_card_is_the_step_the_document_gave_it(self):
        drawn = self.body("putTheStepOn")

        self.assertIn("step.step", drawn)
        self.assertIn("step.matched", drawn)

    def test_clearing_the_choice_puts_every_module_back_on_an_equal_footing(self):
        drawn = self.body("clearFlow")

        self.assertIn('card.className = "module"', drawn)
        self.assertIn("takeTheStepsOff()", drawn)
        self.assertIn('aria-pressed", "false"', drawn)

    def test_the_page_never_counts_a_path_it_could_read_instead(self):
        """`flow.modules` is the document's own count; counting the list is a second one."""
        for drawn in (self.body("drawFlows"), self.body("chooseFlow"),
                      self.body("drawTheChosenFlow"), self.body("clearFlow")):
            for used in re.findall(r"\.length[^\n]*", drawn):
                self.assertTrue(
                    used.startswith(".length === 0"),
                    "the flows section uses .length for something else: %s" % used,
                )
        self.assertIn("flow.modules", self.body("drawTheChosenFlow"))

    def test_every_value_the_flows_section_reads_is_a_key_the_document_has(self):
        """A misspelled key is not an error in a browser: it is the word `undefined`."""
        reading = "\n".join(
            self.body(name)
            for name in ("drawFlows", "chooseFlow", "drawTheChosenFlow", "putTheStepOn")
        )
        paths = sorted(set(re.findall(r"flow\.[A-Za-z][A-Za-z0-9_.]*", reading)))
        steps = sorted(set(re.findall(r"step\.[A-Za-z][A-Za-z0-9_.]*", reading)))

        self.assertNotEqual([], paths)
        self.assertNotEqual([], steps)
        for path in paths:
            for flow in self.document["flows"]:
                self.resolve(path, flow)
        for path in steps:
            for step in self.document["flows"][0]["path"]:
                self.resolve(path, step)

    def resolve(self, path, against):
        """Walk a `flow.` or `step.` path the page reads against a real document entry."""
        at = against
        walked = []
        for step in path.split(".")[1:]:
            if step in ("forEach", "join", "length"):
                return
            walked.append(step)
            self.assertIsInstance(
                at, dict, "%s: %s is not something with keys" % (path, ".".join(walked[:-1]))
            )
            self.assertIn(step, at, "%s: the document has no %s" % (path, ".".join(walked)))
            at = at[step]
            if at is None:
                return

    def test_a_flow_the_graph_could_not_walk_is_named_with_the_reason_and_cannot_be_chosen(self):
        drawn = self.body("drawFlows")

        self.assertIn("flow.couldNotResolve", drawn)
        self.assertIn("chooses.disabled = true;", drawn)
        self.assertIn("cannot be traced through this graph", drawn)

    def test_the_page_says_what_choosing_a_flow_does_before_a_reader_tries_it(self):
        drawn = self.body("drawFlows")

        self.assertIn("Flows through the modules", drawn)
        self.assertIn("numbers them in the order it ", drawn)
        self.assertIn("passes through them", drawn)
        self.assertIn("No flow is a path anybody wrote down", drawn)

    def test_the_page_names_the_file_the_flows_are_defined_in(self):
        """So a reader who disagrees with a flow is pointed at the file they would edit."""
        self.assertIn("document_.scoring.configuration", self.body("drawFlows"))

    def test_a_document_naming_no_flow_says_so_rather_than_drawing_an_empty_chooser(self):
        drawn = self.body("drawFlows")

        self.assertIn("document_.flows.length === 0", drawn)
        self.assertIn("names no flow", drawn)


class TheCommittedPageCarriesTheFlowsTest(SourceTreeTest):
    """The page in `docs/` is the one a reader opens, so the flows have to be in it."""

    def test_the_committed_outputs_hold_the_three_flows_and_the_chooser_that_offers_them(self):
        with open(os.path.join(REPOSITORY, "docs", "module-depth-map.html"),
                  encoding="utf-8") as it:
            committed = it.read()
        with open(os.path.join(REPOSITORY, "docs", "module-depth-map.json"),
                  encoding="utf-8") as it:
            written = json.load(it)

        self.assertIn("function drawFlows", committed)
        self.assertIn("function chooseFlow", committed)
        self.assertEqual(3, len(written["flows"]))
        for flow in written["flows"]:
            self.assertTrue(flow["resolved"], flow["flow"])
            self.assertNotEqual([], flow["path"])
