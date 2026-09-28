package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;

/**
 * A ladder of balance rungs, and the three questions the sweep asks of one.
 *
 * <p><strong>This is {@code BalanceThresholds} with the list handed in rather than written
 * down, and that class is gone.</strong> The three functions are the same three functions, arrived
 * at the same way and answering the same thing; what moved is where the rungs come from. A published
 * scheme now says which figures a customer is congratulated on reaching, so the ladder is a value a
 * caller holds rather than a static everybody reads. The static list and the three static functions
 * beside it delegated here while the sweep was being moved over, and were deleted the moment it had
 * been: a bridge nobody removes is how two ladders start disagreeing, and that one would have gone
 * on quietly congratulating somebody on EUR 500 after the bank stopped publishing EUR 500 as a rung
 * — with nothing anywhere objecting, because EUR 500 is a perfectly good rung.
 *
 * <p>The warning that class carried is now a warning about the argument, and it is sharper for it:
 * there is still exactly one place a balance is turned into a position on a ladder, and a caller
 * that walked a list of rungs itself would be the second place that reading is decided.
 *
 * <p><strong>It lives in {@code notifications} and not in {@code scheme}, deliberately.</strong> The
 * scheme module depends on nothing in this application: it publishes a list of euro amounts, and
 * what a rung <em>means</em> — stood on, below, above — is notifications' vocabulary. A rungs type
 * in the scheme module would drag "the rung a balance has fallen off" into a module that is supposed
 * to know nothing about what anybody is told. The ladder a run of weeks climbs is the same argument
 * pointing at {@code streaks}, and lives there for it.
 *
 * <p><strong>All three answers are total, and that is what this type is for.</strong> Any amount can
 * be asked about, including an amount that is not a rung at all and an amount that was a rung under
 * a ladder nobody publishes any more. That was already the promise the
 * class this came from made — "which is what keeps them honest if the ladder is ever
 * changed under rows that were written against the old one" — and it stopped being hypothetical the
 * moment the rungs are a published figure somebody can change on a Monday: a
 * {@link NotificationReason#BALANCE_THRESHOLD_LOST} row written against EUR 500 is still read back
 * into a position after EUR 500 has stopped being a rung.
 *
 * <p>Compared with {@link BigDecimal#compareTo} rather than {@code equals} throughout, here as in
 * the class this came from. An amount that has been through the database comes back as {@code 100.0}
 * where it went in as {@code 100.00}, and those two are equal as money and unequal as objects.
 *
 * <p>Ascending is assumed rather than enforced, because the scheme record already states it as a
 * property of what it publishes and re-sorting here would be a second opinion about an order that
 * module has settled. A ladder handed over out of order is a ladder whose rungs are in the wrong
 * places, which is a thing to refuse at the door that publishes one.
 */
record TheBalanceRungs(List<BigDecimal> rungs) {

    TheBalanceRungs {
        // Copied, because a ladder is a value and a caller that could go on adding to the list it
        // handed over would be able to move a rung out from under a reading already taken from it.
        rungs = List.copyOf(rungs);
    }

    /**
     * The rungs a published version of the scheme says a balance climbs.
     *
     * <p>The one place the scheme's list of euro amounts becomes a ladder, so that no caller
     * assembles one out of loose figures. Nothing is converted: the scheme already publishes them as
     * euros quoted to the cent, ascending, in the units the rest of this application speaks.
     */
    static TheBalanceRungs theRungsIn(TheSchemeAsPublished scheme) {
        return new TheBalanceRungs(scheme.balanceRungs());
    }

    /**
     * The highest rung the given balance reaches, or nothing at all when it reaches none.
     *
     * <p>Reaches rather than passes: a balance of exactly EUR 100 stands on the EUR 100 rung, and a
     * balance a cent short of it stands on nothing. "Reached" is the word the reason uses, and a
     * customer whose balance reads exactly the figure they were aiming at has reached it.
     *
     * <p>Nothing at all rather than a zeroth rung, because standing below the ladder is a position
     * the rule has to be able to hold: it is where every account starts, and it is where a balance
     * that has been emptied ends up. It is also where every account stands under a scheme that
     * publishes no rungs at all, which is a scheme that congratulates nobody and is answerable
     * rather than exceptional.
     */
    Optional<BigDecimal> theRungStoodOnWith(BigDecimal balance) {
        BigDecimal stoodOn = null;
        for (BigDecimal rung : rungs) {
            if (rung.compareTo(balance) <= 0) {
                stoodOn = rung;
            }
        }
        return Optional.ofNullable(stoodOn);
    }

    /**
     * The highest rung strictly below the given amount, or nothing at all when there is none.
     *
     * <p>How a record of a rung being lost is read back. A row saying EUR 500 was lost says the
     * balance stands on the rung below EUR 500, which is EUR 100 — and below the lowest rung there
     * is no rung, which is the honest answer for a balance that has fallen off the ladder
     * altogether, and the honest answer for a row written against a rung this ladder no longer has.
     */
    Optional<BigDecimal> theRungBelow(BigDecimal amount) {
        BigDecimal below = null;
        for (BigDecimal rung : rungs) {
            if (rung.compareTo(amount) < 0) {
                below = rung;
            }
        }
        return Optional.ofNullable(below);
    }

    /**
     * The lowest rung strictly above the given amount, or nothing at all when the amount is at or
     * above the top of the ladder.
     *
     * <p>Which rung a balance has fallen off, asked of the balance it has fallen to: the lowest one
     * it no longer reaches. A balance that has dropped from EUR 1.100 to EUR 600 no longer reaches
     * EUR 1.000, and that is the figure a customer is told about.
     */
    Optional<BigDecimal> theRungAbove(BigDecimal amount) {
        for (BigDecimal rung : rungs) {
            if (rung.compareTo(amount) > 0) {
                return Optional.of(rung);
            }
        }
        return Optional.empty();
    }
}
