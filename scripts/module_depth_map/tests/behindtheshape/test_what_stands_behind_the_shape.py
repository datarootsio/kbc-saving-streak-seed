"""Clicking a module opens everything standing behind its shape, and none of it is new.

The shape on a card makes a claim; the panel is where a reader checks it. So the property
these tests establish is not that the panel looks a certain way — it is that every fact in
it is one the graph document already holds, read straight off it. A panel that worked a
number out while the page was drawing would be a second measurement standing beside the
one an agent reads out of the file, with nothing to say which of the two the shape came
from.

Two of these are mechanical rather than illustrative, and they are the ones worth keeping:
one resolves every `module.` path the panel reads against a real document and fails on a
key the document does not have — a misspelling that would otherwise reach the page as the
word `undefined` — and one refuses any use of `.length` except asking whether a list is
empty, which is how counting gets into a renderer that promised not to count.
"""

import json
import os
import re

from ... import graph, page, scoring
from ..support.sourcetrees import SourceTreeTest

# The cast, each written to earn one shape the panel has to be able to draw.
A_MODULE_TO_CALL = "public class Prices {\n    public long of(long id) { return 0; }\n}"
AN_ADAPTER = (
    "import org.springframework.data.jpa.repository.JpaRepository;\n\n"
    "interface ReceiptRepository extends JpaRepository<Receipt, Long> {\n}"
)
A_PERSISTENT_RECORD = (
    "import jakarta.persistence.Entity;\n\n"
    "@Entity\nclass Receipt {\n"
    "    Receipt(long cents) {}\n"
    "    public long cents() { return 0; }\n}"
)
A_DATA_CARRIER = "public record Line(long cents, String what) {}"

# Scored, and priced at nothing: no method a caller can reach, so every term the bar
# counts is zero. A caller still has `new Gate()` to learn, which is the whole reason a
# naked zero needs a sentence beside it.
A_MODULE_THE_BAR_PRICES_AT_NOTHING = "public class Gate {\n    public Gate() {}\n}"

# Deep: four things coordinated behind one method, and a refusal it documents and keeps.
A_MODULE_WORTH_OPENING = """import java.math.BigDecimal;

import org.springframework.transaction.annotation.Transactional;

public class Till {

    private final ReceiptRepository receipts;
    private final Prices prices;

    Till(ReceiptRepository receipts, Prices prices) {
        this.receipts = receipts;
        this.prices = prices;
    }

    /**
     * Rings one thing up.
     *
     * @throws IllegalArgumentException if there is no such thing
     */
    @Transactional
    public Receipt ring(long id, BigDecimal discount) {
        if (id < 0) { throw new IllegalArgumentException("no such thing"); }
        long cents = prices.of(id);
        Receipt receipt = new Receipt(cents);
        receipts.save(receipt);
        return receipt;
    }
}"""

# A refusal in the body that the seam says nothing about: one finding, on one module.
A_MODULE_WITH_A_FINDING = """public class Barrier {

    /** Lets one through. */
    public void go(long id) {
        if (id < 0) { throw new IllegalStateException("the barrier is down"); }
    }
}"""

# Two callers, so that "which modules go through it" has more than one line to draw.
A_CALLER = """import java.math.BigDecimal;

public class Shop {

    private final Till till;

    Shop(Till till) { this.till = till; }

    public void sell(long id) { till.ring(id, BigDecimal.ONE); }
}"""

ANOTHER_CALLER = """import java.math.BigDecimal;

public class Market {

    private final Till till;

    Market(Till till) { this.till = till; }

    public void sell(long id) { till.ring(id, BigDecimal.TEN); }
}"""

# What the panel is drawn by, all of it, and the only place these tests read.
PANEL = (
    "openBehind",
    "drawBehind",
    "drawBehindInterface",
    "drawBehindMethods",
    "drawBehindTypes",
    "drawBehindRefusals",
    "drawBehindReach",
    "drawBehindCallers",
    "drawBehindVerdict",
    "drawBehindFindings",
)

# Members of a JavaScript array, not keys of the document. A path ending in one of these
# is resolved without it; anything else has to be a key the document really has.
NOT_A_KEY = ("forEach", "join", "length")


class BehindTheShapeTest(SourceTreeTest):
    """One source tree with every shape the panel has to draw, and the page for it."""

    def setUp(self):
        super().setUp()
        tree = self.tree("fixture")
        for name, body in (
            ("Till", A_MODULE_WORTH_OPENING),
            ("Prices", A_MODULE_TO_CALL),
            ("ReceiptRepository", AN_ADAPTER),
            ("Receipt", A_PERSISTENT_RECORD),
            ("Line", A_DATA_CARRIER),
            ("Barrier", A_MODULE_WITH_A_FINDING),
            ("Gate", A_MODULE_THE_BAR_PRICES_AT_NOTHING),
            ("Shop", A_CALLER),
            ("Market", ANOTHER_CALLER),
        ):
            tree.java("shop.till", name, body)
        self.document = graph.build([graph.java_root(tree.root)], scoring.load())
        self.assertEqual([], self.document["source"]["unparsed"])
        self.modules = {module["name"]: module for module in self.document["modules"]}
        self.rendered = page.render(
            self.document, graph.serialise(self.document)
        ).decode("utf-8")

    def body(self, name):
        """One function of the renderer, as its source."""
        found = self.rendered[self.rendered.index("function " + name):]
        return found[:found.index("\n  }")]

    def panel(self):
        """Every function the panel is drawn by, as one piece of source."""
        return "\n".join(self.body(name) for name in PANEL)

    def script(self):
        """The renderer: everything past the script element the graph document sits in."""
        return self.rendered[self.rendered.index("</script>"):]

    def card(self):
        """The loop that draws one card per module."""
        found = self.rendered[self.rendered.index("package_.moduleIds.forEach"):]
        return found[:found.index("\n  });")]


class ClickingAModuleOpensWhatStandsBehindItTest(BehindTheShapeTest):

    def test_the_card_is_the_control_and_the_name_on_it_is_a_button(self):
        """A pointer opens the panel anywhere on the card; a keyboard opens it by tabbing.

        One opening handler, on the card, is deliberate: the button's own click bubbles up
        to it, and a second handler on the button would open the same panel twice. The
        press beside it opens nothing — it only records where the gesture began.
        """
        card = self.card()

        self.assertIn('add(item, "button", "name", module.name)', card)
        self.assertIn('opens.setAttribute("type", "button")', card)
        self.assertIn('opens.setAttribute("aria-haspopup", "dialog")', card)
        self.assertIn('item.addEventListener("mousedown", pressedOnACard);', card)
        self.assertIn(
            'item.addEventListener("click", function (event) {\n'
            "        if (aDragRatherThanAClick(event, item)) { return; }\n"
            "        openWhenNoSecondClickFollows(event, function () {\n"
            "          openBehind(module, opens);\n"
            "        });",
            card,
        )
        self.assertEqual(1, card.count("openBehind("))
        self.assertEqual(2, card.count("addEventListener"))

    def test_the_panel_is_a_dialog_so_that_the_keyboard_is_the_browsers_business(self):
        """Escape, the focus that goes in and the focus that comes back out are all free.

        A hand-rolled panel has to trap the tab key itself, and one that traps it wrong is
        a page a keyboard cannot leave.
        """
        script = self.rendered[self.rendered.index("</script>"):]

        self.assertIn('var panel = add(root, "dialog", "behind");', script)
        self.assertIn('panel.setAttribute("aria-labelledby", BEHIND_NAME);', script)
        self.assertIn("panel.showModal();", script)
        self.assertIn('behindBody.setAttribute("tabindex", "0");', script)

    def test_the_card_takes_the_focus_before_the_panel_opens(self):
        """Which is what makes closing hand the keyboard back to the card it came from.

        The browser writes down where focus was when a modal dialog opened. Clicking the
        body of a card focuses nothing, so without this the note said "nowhere", closing
        dropped the keyboard on a hidden element, and Tab started again from the top of
        the page. Doing it in the `close` event instead is a different thing: that event
        is a queued task, and it lands after the browser has restored focus itself.
        """
        opening = self.body("openBehind")

        self.assertIn("from.focus();", opening)
        self.assertLess(opening.index("from.focus();"), opening.index("panel.showModal()"))

    def test_the_page_says_the_panel_is_there_and_what_is_in_it(self):
        """An affordance nobody mentions is one a reader has to find by accident."""
        script = self.rendered[self.rendered.index("</script>"):]

        self.assertIn("Click any module", script)
        self.assertIn("press Enter", script)


class EveryPanelOpensAtTheTopOfItselfTest(BehindTheShapeTest):
    """One module's panel must never open where the reader left the last one.

    `behindBody` outlives every open — it is emptied and refilled rather than rebuilt —
    and a browser keeps the scroll offset of an element it has hidden. The sticky heading
    is what makes this worth a test rather than a shrug: the panel does not look wrong,
    so a reader clicking through modules reads the second one's findings believing they
    are at the top of its interface.
    """

    def test_the_body_is_scrolled_back_to_the_top_every_time_a_panel_opens(self):
        opening = self.body("openBehind")

        self.assertIn("behindBody.scrollTop = 0;", opening)

    def test_the_top_is_taken_once_the_panel_is_open_and_has_a_scroll_to_move(self):
        """An element with no layout box has no scroll position, so order is the fix."""
        opening = self.body("openBehind")

        self.assertLess(
            opening.index("panel.showModal()"),
            opening.index("behindBody.scrollTop = 0;"),
        )


class WhatCountsAsAClickOnTheBackdropTest(BehindTheShapeTest):
    """Both ends of the gesture, because a drag out of the panel reports the dialog too.

    A click's target is the nearest common ancestor of where the pointer went down and
    where it came up. Selecting a caller id inside the panel and releasing past its edge
    makes that ancestor the dialog itself, so a handler reading only the click would close
    the panel and take the reader's selection with it. Reading the press and the release
    separately is what tells a dismissal from a drag that merely ended somewhere else.
    """

    def test_the_press_and_the_release_both_have_to_have_landed_on_the_backdrop(self):
        script = self.script()

        self.assertIn(
            'panel.addEventListener("mousedown", function (event) {\n'
            "    pressedOn = opensAContextMenu(event) ? null : event.target;",
            script,
        )
        self.assertIn(
            'panel.addEventListener("mouseup", function (event) {\n'
            "    if (event.target === panel && pressedOn === panel) { shutBehind(); }",
            script,
        )

    def test_no_click_closes_the_panel_by_where_it_came_up_alone(self):
        """The whole bug is a handler that reads one end of the gesture and acts on it."""
        script = self.script()

        self.assertNotIn("if (event.target === panel) { shutBehind(); }", script)
        self.assertNotIn('panel.addEventListener("click"', script)


class NoGestureAskingForAContextMenuOpensOrClosesThePanelTest(BehindTheShapeTest):
    """`mousedown` and `mouseup` fire for every way a reader can ask for a menu.

    Without a guard, a press beside the panel meant for the context menu dismisses it, so
    the menu opens over a page the panel has just left — while the same press inside the
    panel, which the browser handles itself, correctly leaves it open, so the two ends of
    one gesture disagree. The secondary button is the visible half of the rule; the other
    is macOS's Ctrl+click, which arrives as the primary button with `ctrlKey` set and
    sails through a rule reading `button` alone. The card is the same story from the other
    side: the press that records where a gesture began must not record one that was never
    going to open anything.
    """

    def test_the_page_names_the_button_rather_than_writing_the_number_twice(self):
        self.assertIn("var PRIMARY_BUTTON = 0;", self.script())

    def test_asking_for_a_menu_is_both_the_button_and_the_key_that_stands_for_it(self):
        self.assertIn(
            "function opensAContextMenu(event) {\n"
            "    return event.button !== PRIMARY_BUTTON || event.ctrlKey;",
            self.script(),
        )

    def test_the_backdrop_reads_it_before_it_remembers_where_a_press_landed(self):
        self.assertIn(
            "pressedOn = opensAContextMenu(event) ? null : event.target;",
            self.script(),
        )

    def test_the_card_reads_it_before_it_remembers_where_a_press_landed(self):
        self.assertIn(
            "pressedAt = opensAContextMenu(event)\n"
            "      ? null\n"
            "      : {x: event.clientX, y: event.clientY};",
            self.script(),
        )

    def test_neither_end_is_left_reading_the_button_on_its_own(self):
        """One rule, read by both, is what stops the two ends disagreeing again."""
        script = self.script()

        self.assertEqual(2, script.count("opensAContextMenu(event)\n      ?")
                         + script.count("opensAContextMenu(event) ?"))
        self.assertNotIn("event.button === PRIMARY_BUTTON", script)


class WhatCountsAsAClickOnACardTest(BehindTheShapeTest):
    """The backdrop's rule, said for the card, because the card is text a reader wants.

    A click's target is the nearest common ancestor of the press and the release, so a
    drag across a module's name reports the card itself — and a handler reading only the
    click throws a modal over the page and takes the half-made selection with it, because
    opening the panel moves the focus and a focus move collapses a selection. On the
    backdrop a stray drag lost a selection; on the card it loses the selection and covers
    the page. The card's two ends are both inside itself, so the rule is where the press
    and the release landed rather than what they landed on.
    """

    def gesture(self):
        """The three functions the card's pointer rule is written in."""
        return "\n".join(
            self.body(name)
            for name in ("pressedOnACard", "aDragRatherThanAClick", "selectionInside")
        )

    def test_the_press_records_where_it_landed_and_opens_nothing(self):
        pressing = self.body("pressedOnACard")

        self.assertIn("pressedAt = opensAContextMenu(event)", pressing)
        self.assertIn("{x: event.clientX, y: event.clientY}", pressing)
        self.assertNotIn("openBehind", pressing)

    def test_a_release_away_from_the_press_is_a_drag_and_opens_nothing(self):
        deciding = self.body("aDragRatherThanAClick")

        self.assertIn(
            "if (Math.abs(event.clientX - at.x) > A_STEADY_HAND) { return true; }",
            deciding,
        )
        self.assertIn(
            "if (Math.abs(event.clientY - at.y) > A_STEADY_HAND) { return true; }",
            deciding,
        )

    def test_a_press_that_left_text_selected_is_a_drag_however_short_it_was(self):
        """A drag of a pixel or two across a word is a selection the distance cannot see."""
        self.assertIn("return selectionInside(card);", self.body("aDragRatherThanAClick"))
        selecting = self.body("selectionInside")

        self.assertIn("window.getSelection", selecting)
        self.assertIn("selected.isCollapsed", selecting)
        self.assertIn(
            "return card.contains(selected.getRangeAt(0).commonAncestorContainer);",
            selecting,
        )

    def test_the_keyboards_click_has_no_press_behind_it_and_opens_the_panel(self):
        """Enter on the name button is a click with no mouse in it: `detail` is 0.

        Which is why the card arms on the press and decides on the click, rather than
        moving the opening to `mouseup` the way the backdrop's dismissal moved: a
        `mouseup` the keyboard never fires is a panel the keyboard cannot open.
        """
        deciding = self.body("aDragRatherThanAClick")

        self.assertIn("if (event.detail === 0) { return false; }", deciding)
        self.assertLess(
            deciding.index("event.detail === 0"), deciding.index("at === null")
        )
        self.assertNotIn('item.addEventListener("mouseup"', self.card())

    def test_a_press_that_never_became_a_click_is_not_left_standing(self):
        """Dragged off the card and released on the page, then a click somewhere else."""
        deciding = self.body("aDragRatherThanAClick")

        self.assertIn("var at = pressedAt;\n    pressedAt = null;", deciding)
        self.assertIn("if (at === null) { return true; }", deciding)

    def test_the_tolerance_is_named_and_narrower_than_a_word(self):
        """A trackpad click drifts a pixel or two; an exact match would feel broken."""
        self.assertIn("var A_STEADY_HAND = 3;", self.script())


class TakingAWordOutOfACardIsNotAskingForThePanelTest(BehindTheShapeTest):
    """A double-click is two clicks, and the first of them looks exactly like a click.

    Nothing has been selected when it arrives, the pointer has not moved and `detail` is
    1, so every guard the card has says "a click" and the panel opens under click two —
    which then lands on whatever the modal put beneath the pointer. The reader who
    double-clicked a module name to copy it gets the panel's own text selected instead of
    the name, or, on a card in an outer column, a second click on the backdrop that
    dismisses the panel again: neither the word nor the panel.

    No single click can tell the two apart, so the opening waits out the interval a second
    click has to arrive in, and the second press calls it off. The keyboard's Enter is not
    a gesture that can grow — `detail` is 0 — and opens straight away, which is the
    constraint that keeps the opening on the click rather than on `mouseup`.
    """

    def test_the_first_click_asks_for_the_panel_rather_than_opening_it(self):
        waiting = self.body("openWhenNoSecondClickFollows")

        self.assertIn("holdTheOpening();", waiting)
        self.assertIn("opening = window.setTimeout(function () {", waiting)
        self.assertIn("}, A_SECOND_CLICK);", waiting)

    def test_the_keyboard_opens_the_panel_without_waiting_for_anything(self):
        waiting = self.body("openWhenNoSecondClickFollows")

        self.assertIn("if (event.detail === 0) {\n      open();\n      return;\n    }",
                      waiting)
        self.assertLess(waiting.index("event.detail === 0"),
                        waiting.index("setTimeout"))

    def test_a_click_that_is_the_second_of_a_gesture_opens_nothing(self):
        self.assertIn("if (event.detail > 1) { return; }",
                      self.body("openWhenNoSecondClickFollows"))

    def test_the_second_press_calls_off_the_opening_the_first_click_asked_for(self):
        """A press is a whole click earlier than the click, and it is where the browser
        selects the word the reader is after."""
        pressing = self.body("pressedOnACard")

        self.assertIn("if (event.detail > 1) { holdTheOpening(); }", pressing)

    def test_only_one_opening_is_ever_waiting(self):
        """A click on another card while one is pending must not open two panels."""
        waiting = self.body("openWhenNoSecondClickFollows")
        holding = self.body("holdTheOpening")

        self.assertTrue(waiting.startswith(
            "function openWhenNoSecondClickFollows(event, open) {\n"
            "    holdTheOpening();"), waiting)
        self.assertIn("window.clearTimeout(opening);\n      opening = null;", holding)

    def test_the_interval_is_named_and_is_the_one_the_platforms_default_to(self):
        self.assertIn("var A_SECOND_CLICK = 500;", self.script())

    def test_the_name_a_reader_double_clicks_is_text_a_browser_will_select(self):
        """A browser selects nothing inside a button unless the page says otherwise.

        Which would leave the module's name — the string most worth taking off this page
        — the one word on a card that cannot be copied, while the rule above carefully
        keeps the panel out of the way of taking it.
        """
        rule = self.rendered[self.rendered.index(".module button.name {"):]

        self.assertIn("user-select: text;", rule[:rule.index("}")])
        self.assertIn("-webkit-user-select: text;", rule[:rule.index("}")])


class ThePanelWithoutAModalDialogToOpenItInTest(BehindTheShapeTest):
    """The fallback branch has to be able to do what its comment says it does.

    Where `showModal` is missing, `<dialog>` is an unknown element: the `open` attribute
    the fallback sets and clears means nothing to the browser's own stylesheet, so without
    a rule of ours the empty panel shell renders in the page flow from load and no close
    ever takes it away.
    """

    def test_a_panel_that_is_not_open_is_not_on_the_page(self):
        self.assertIn("dialog.behind:not([open]) { display: none; }", self.rendered)

    def test_a_panel_that_is_open_is_a_block_rather_than_a_run_of_inline_text(self):
        """Both halves, because a browser's own sheet supplies both.

        An unknown element defaults to `display: inline`, so a rule that only hides the
        panel leaves the fallback's `open` attribute showing it as an inline run spliced
        into the page flow, with the panel's own width inert. The closed rule is the more
        specific of the two, so where `<dialog>` is known nothing changes.
        """
        opened = self.rendered.index("dialog.behind { display: block; }")

        self.assertLess(
            opened, self.rendered.index("dialog.behind:not([open]) { display: none; }")
        )

    def test_the_fallback_opens_and_closes_by_the_attribute_that_rule_reads(self):
        self.assertIn('panel.setAttribute("open", "open");', self.body("openBehind"))
        self.assertIn('panel.removeAttribute("open");', self.body("shutBehind"))


class AZeroIsCaveatedWhereverItIsPrintedTest(BehindTheShapeTest):
    """The card's caveat, in the panel a reader opens to check the card.

    A cost of 0 means nothing this bar counts, which is not the same as nothing to learn.
    The card said so and the panel did not, which put the stronger claim in the place
    billed as where a reader checks the weaker one.
    """

    def test_the_card_and_the_panel_say_it_from_one_string(self):
        """Two copies is how one of them comes to be edited and the other left behind."""
        script = self.script()

        self.assertEqual(
            1, script.count("nothing this bar counts, which is not the same as"))
        self.assertIn("NOT_THE_SAME_AS_NOTHING_TO_LEARN", self.body("drawInterface"))
        self.assertIn(
            "NOT_THE_SAME_AS_NOTHING_TO_LEARN", self.body("drawBehindInterface"))

    def test_both_say_it_on_the_same_condition(self):
        for drawn in (self.body("drawInterface"), self.body("drawBehindInterface")):
            self.assertLess(
                drawn.index("module.interface.cost === 0"),
                drawn.index("NOT_THE_SAME_AS_NOTHING_TO_LEARN"),
            )

    def test_the_fixture_holds_a_module_the_bar_prices_at_nothing(self):
        """Without one, the branch above is a sentence no document could reach."""
        gate = self.modules["Gate"]

        self.assertIsNone(gate["excludedBy"])
        self.assertEqual(0, gate["interface"]["cost"])
        self.assertEqual([], gate["interface"]["methods"])

    def test_the_caveat_is_not_said_of_a_module_no_rule_scored(self):
        """A never-scored module has no zero on it to caveat: it has a rule instead."""
        branch = self.body("drawBehindInterface")
        excluded = branch[branch.index("if (module.excludedBy) {"):branch.index("} else {")]

        self.assertNotIn("NOT_THE_SAME_AS_NOTHING_TO_LEARN", excluded)


class ThePanelIsReadStraightOffTheDocumentTest(BehindTheShapeTest):
    """The claim the whole panel rests on, established twice and mechanically."""

    def test_every_value_the_panel_reads_is_a_key_the_document_has(self):
        """A misspelled key is not an error in a browser: it is the word `undefined`.

        Every `module.` path the panel reads is resolved here against every module in a
        real document, so a key that moved, or was never there, fails the suite instead of
        printing nothing on the page.
        """
        paths = sorted(set(re.findall(r"module\.[A-Za-z][A-Za-z0-9_.]*", self.panel())))

        self.assertNotEqual([], paths)
        for path in paths:
            steps = path.split(".")[1:]
            while steps and steps[-1] in NOT_A_KEY:
                steps = steps[:-1]
            for module in self.document["modules"]:
                at, walked = module, []
                for step in steps:
                    walked.append(step)
                    self.assertIsInstance(
                        at, dict,
                        "%s: %s is not something with keys in %s"
                        % (path, ".".join(walked[:-1]), module["id"]),
                    )
                    self.assertIn(
                        step, at,
                        "%s: the document has no %s on %s"
                        % (path, ".".join(walked), module["id"]),
                    )
                    at = at[step]
                    # A key the document holds as null is still a key it holds — a module
                    # nothing excluded, a verdict never given. What sits under it is
                    # resolved on the modules that have one, which this fixture has.
                    if at is None:
                        break

    def test_the_panel_never_counts_anything_it_could_read_instead(self):
        """`.length` is how counting gets into a renderer that promised not to count.

        A list is allowed to be asked whether it is empty, because "there are none" is a
        sentence rather than a measurement. Anything else — a length printed, a length
        added to something — is a second number beside the document's own, and a reader
        could not tell which of the two the shape above was drawn from.
        """
        for used in re.findall(r"\.length[^\n]*", self.panel()):
            self.assertTrue(
                used.startswith(".length === 0"),
                "the panel uses .length for something other than an empty list: %s" % used,
            )

    def test_the_counts_the_panel_prints_are_the_documents_own(self):
        reading = self.panel()

        self.assertIn("module.reach.count", reading)
        self.assertIn("module.callers.count", reading)
        self.assertIn("module.deletionTest.methods", reading)
        self.assertIn("module.lines", reading)


class EveryMethodWithWhatItCostsACallerTest(BehindTheShapeTest):

    def test_the_panel_draws_each_method_with_the_cost_the_document_gave_it(self):
        drawn = self.body("drawBehindMethods")

        for read in ("method.visibility", "method.returns", "method.name",
                     "method.parameters.join", "method.cost",
                     "method.documentedRefusals"):
            self.assertIn(read, drawn)

    def test_the_document_prices_each_method_of_the_module_the_panel_will_draw(self):
        """What the panel prints per method has to be there per method to print."""
        till = self.modules["Till"]

        self.assertEqual(
            [{"name": "ring", "parameters": ["long", "BigDecimal"], "cost": 3,
              "returns": "Receipt", "visibility": "public",
              "documentedRefusals": ["IllegalArgumentException"]}],
            [dict(method) for method in till["interface"]["methods"]],
        )

    def test_the_types_that_cross_the_seam_are_named_with_it(self):
        drawn = self.body("drawBehindTypes")

        self.assertIn("type.name", drawn)
        self.assertIn("type.mustBeLearned", drawn)

    def test_the_refusals_it_can_answer_with_are_named_with_both_sides_of_each(self):
        drawn = self.body("drawBehindRefusals")

        self.assertIn("refusal.name", drawn)
        self.assertIn("refusal.checked", drawn)
        self.assertIn("documentedBy(refusal)", drawn)
        self.assertIn("raisedOrNot(refusal)", drawn)


class EverythingItReachesAndEveryModuleThatCallsItTest(BehindTheShapeTest):

    def test_each_thing_reached_is_named_with_its_kind_and_how_it_was_read(self):
        drawn = self.body("drawBehindReach")

        for read in ("reached.kind", "reached.name", "reached.matched", "reached.moduleId"):
            self.assertIn(read, drawn)
        self.assertIn("module.reach.reaches", drawn)

    def test_a_module_that_reaches_nothing_is_told_so_rather_than_left_blank(self):
        drawn = self.body("drawBehindReach")

        self.assertIn("module.reach.reaches", drawn)
        self.assertIn("reaches nothing this graph holds", drawn)

    def test_the_module_the_panel_will_draw_reaches_what_the_fixture_gave_it(self):
        till = self.modules["Till"]

        self.assertEqual(
            [("adapter", "ReceiptRepository"), ("module", "Prices"),
             ("record", "Receipt"), ("transaction", "a transaction")],
            [(reached["kind"], reached["name"]) for reached in till["reach"]["reaches"]],
        )

    def test_every_module_that_calls_this_one_is_named(self):
        drawn = self.body("drawBehindCallers")

        self.assertIn("module.callers.moduleIds", drawn)
        self.assertIn("module.callers.count", drawn)

    def test_a_module_nothing_calls_is_told_so_rather_than_left_blank(self):
        drawn = self.body("drawBehindCallers")

        self.assertIn("No module in this graph calls this one.", drawn)

    def test_the_callers_the_panel_will_name_are_the_ones_the_document_holds(self):
        self.assertEqual(
            ["shop.till.Market", "shop.till.Shop"],
            self.modules["Till"]["callers"]["moduleIds"],
        )


class TheVerdictAndTheFindingsAgainstItTest(BehindTheShapeTest):

    def test_the_panel_carries_the_verdict_and_the_three_counts_behind_it(self):
        drawn = self.body("drawBehindVerdict")

        for read in ("test.verdict", "test.reach", "test.methods", "test.callers",
                     "test.because"):
            self.assertIn(read, drawn)

    def test_a_module_no_rule_scores_is_given_no_verdict_and_the_panel_says_so(self):
        drawn = self.body("drawBehindVerdict")

        self.assertIn("test.verdict === null", drawn)
        self.assertIn("never scored, so the deletion test gives it no verdict", drawn)

    def test_every_finding_is_listed_with_both_sides_of_it_and_the_reason(self):
        drawn = self.body("drawBehindFindings")

        for read in ("finding.finding", "finding.refusal", "finding.because",
                     "documentedBy(finding)", "raisedOrNot(finding)"):
            self.assertIn(read, drawn)

    def test_a_module_with_no_finding_against_it_says_so_plainly(self):
        """Silence and "there are none" are different answers, and only one is checkable."""
        drawn = self.body("drawBehindFindings")

        self.assertIn("module.findings.length === 0", drawn)
        self.assertIn("There is no finding against this module.", drawn)

    def test_the_module_the_panel_will_draw_carries_the_finding_the_fixture_earned(self):
        barrier = self.modules["Barrier"]

        self.assertEqual(
            [("raised but never documented", "IllegalStateException")],
            [(finding["finding"], finding["refusal"]) for finding in barrier["findings"]],
        )


class ThePanelCannotPrintTwoDisagreeingNumbersInSilenceTest(BehindTheShapeTest):
    """The guards the card carries, said again in the place a reader checks the card with.

    The panel prints the reach a depth was taken over in one section and lists the reach
    itself in the next, and prints a verdict's three counts under both. All of them are
    the document's own — nothing here counts anything — so a disagreement between them is
    a disagreement inside the file, and the panel is where a reader's argument with the
    shape stops: there is nowhere further to open.
    """

    def test_a_depth_taken_over_some_other_reach_than_the_panel_lists_says_so(self):
        drawn = self.body("drawBehindInterface")

        self.assertIn("if (module.depth.reach !== module.reach.count) {", drawn)
        self.assertIn("but this panel lists ", drawn)

    def test_a_verdict_read_off_some_other_counts_than_the_panel_lists_says_so(self):
        drawn = self.body("drawBehindVerdict")

        self.assertIn(
            "if (test.reach !== module.reach.count "
            "|| test.callers !== module.callers.count) {",
            drawn,
        )
        self.assertIn("the rest of this panel was not drawn from", drawn)

    def test_both_guards_are_drawn_in_the_ink_the_rest_of_the_line_is(self):
        """`.says` is the softer ink supporting prose is drawn in, and a reading that

        contradicts itself is not supporting prose. The card's own guard passes no class
        either, so the three read the same weight wherever a reader meets them.
        """
        self.assertIn(
            'over.appendChild(document.createTextNode(\n'
            '          " — but this panel lists "',
            self.body("drawBehindInterface"),
        )
        self.assertIn(
            'line.appendChild(document.createTextNode(\n'
            '        " — but this panel lists "',
            self.body("drawBehindVerdict"),
        )

    def test_the_document_this_page_carries_holds_no_such_disagreement(self):
        """So the branches above are a guard rather than a sentence anybody reads today."""
        for module in self.document["modules"]:
            test = module["deletionTest"]
            if module["depth"]["reach"] is not None:
                self.assertEqual(
                    module["reach"]["count"], module["depth"]["reach"], module["id"]
                )
            if test["verdict"] is not None:
                self.assertEqual(module["reach"]["count"], test["reach"], module["id"])
                self.assertEqual(
                    module["callers"]["count"], test["callers"], module["id"]
                )


class AnExcludedModulesPanelNamesTheRuleRatherThanAScoreTest(BehindTheShapeTest):

    def excluded_branch(self):
        """What the panel draws for a module no rule scores, and nothing else."""
        drawn = self.body("drawBehindInterface")
        start = drawn.index("if (module.excludedBy) {")
        return drawn[start:drawn.index("} else {", start)]

    def test_the_rule_that_excluded_it_is_named_with_what_matched(self):
        branch = self.excluded_branch()

        self.assertIn("module.excludedBy.rule", branch)
        self.assertIn("module.excludedBy.matched", branch)
        self.assertIn("because[module.excludedBy.rule]", branch)

    def test_no_score_is_drawn_for_a_module_no_rule_scored(self):
        """Even a zero would be the score the rules declined to give, wearing a number."""
        branch = self.excluded_branch()

        for score in ("interface.cost", "interface.refusalCost",
                      "interface.costWithoutRefusals", "depth.leverage",
                      "depth.interfaceCost"):
            self.assertNotIn(score, branch)

    def test_which_side_of_the_list_a_type_falls_on_is_left_off_it_too(self):
        drawn = self.body("drawBehindTypes")

        self.assertIn("if (module.excludedBy) { return; }", drawn)

    def test_the_document_excludes_the_fixture_by_the_rules_the_panel_will_print(self):
        self.assertEqual(
            {"Line": "data carrier", "ReceiptRepository": "generated repository"},
            {
                name: module["excludedBy"]["rule"]
                for name, module in self.modules.items()
                if module["excludedBy"]
            },
        )
        for name in ("Line", "ReceiptRepository"):
            self.assertIsNone(self.modules[name]["interface"]["cost"])
            self.assertIsNone(self.modules[name]["deletionTest"]["verdict"])


class TheCommittedPageDrawsThePanelTest(BehindTheShapeTest):
    """The page in `docs/` is the one a reader opens, so the panel has to be in it."""

    def test_the_committed_page_carries_the_panel_and_the_document_it_reads(self):
        here = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(
            os.path.dirname(os.path.abspath(__file__))))))
        with open(os.path.join(here, "docs", "module-depth-map.html"), encoding="utf-8") as it:
            committed = it.read()
        with open(os.path.join(here, "docs", "module-depth-map.json"), encoding="utf-8") as it:
            written = json.load(it)

        for drawn in PANEL:
            self.assertIn("function " + drawn, committed)
        self.assertIn('add(root, "dialog", "behind")', committed)
        self.assertNotEqual([], written["modules"])
