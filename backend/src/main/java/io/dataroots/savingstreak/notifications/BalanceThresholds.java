package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The ladder a savings balance climbs, and the only place its rungs are written down.
 *
 * <p>Fixed and shared by every customer. Per-customer targets were considered and rejected: there is
 * no screen on which to set one, and a ladder everybody is on is what a training application wants
 * to demonstrate — two customers saving the same amount get told the same thing.
 *
 * <p>Quoted to the cent like every other amount of money in this application, so that a rung a
 * notification carries reads as money in the page it is rendered in rather than as a bare integer.
 *
 * <p>Three questions, and they are the three the rule needs. Which rung a balance stands on is what
 * the sweep compares against the record; the rung below one is how a
 * {@link NotificationReason#BALANCE_THRESHOLD_LOST} row is read back into the position it describes;
 * the rung above an amount is which rung a fallen balance has fallen off. All three are total — any
 * amount can be asked about, including an amount that is not a rung at all, which is what keeps them
 * honest if the ladder is ever changed under rows that were written against the old one.
 */
final class BalanceThresholds {

    /**
     * The rungs, ascending. Round figures far enough apart that climbing from one to the next is an
     * occasion: a customer who has just been told about EUR 100 is not told about EUR 200 a week
     * later.
     */
    static final List<BigDecimal> THE_RUNGS = List.of(
            new BigDecimal("100.00"),
            new BigDecimal("500.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("2500.00"),
            new BigDecimal("5000.00"),
            new BigDecimal("10000.00"));

    private BalanceThresholds() {
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
     * that has been emptied ends up.
     */
    static Optional<BigDecimal> theRungStoodOnWith(BigDecimal balance) {
        BigDecimal stoodOn = null;
        for (BigDecimal rung : THE_RUNGS) {
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
     * altogether.
     *
     * <p>Compared with {@link BigDecimal#compareTo} rather than {@code equals}, here and in the two
     * beside it. An amount that has been through the database comes back as {@code 100.0} where it
     * went in as {@code 100.00}, and those two are equal as money and unequal as objects.
     */
    static Optional<BigDecimal> theRungBelow(BigDecimal amount) {
        BigDecimal below = null;
        for (BigDecimal rung : THE_RUNGS) {
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
    static Optional<BigDecimal> theRungAbove(BigDecimal amount) {
        for (BigDecimal rung : THE_RUNGS) {
            if (rung.compareTo(amount) > 0) {
                return Optional.of(rung);
            }
        }
        return Optional.empty();
    }
}
