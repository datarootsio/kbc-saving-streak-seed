package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ladder itself, at the rule rather than over HTTP.
 *
 * <p>Beside {@code LoyaltyAnniversaryTest} and {@code PointsExpiryTest}, and here for the reason
 * they are. Every other test of this feature drives the whole application over HTTP, which keeps it
 * free of the storage decisions later slices need to change; the boundaries of this ladder cannot be
 * reached that way without a deposit, a sweep and an application per case — a balance exactly on a
 * rung, a cent short of one, and a balance above the top of the ladder are three of those, and each
 * of the six rungs has all three.
 *
 * <p>So the arithmetic is asserted here, on the three functions that decide it, and the HTTP tests
 * are about the rule that uses them: that a change of rung is announced once and an unchanged rung
 * is not announced at all.
 */
class BalanceThresholdsTest {

    /**
     * The ladder is written down in exactly one place, and this is that place read back.
     *
     * <p>A test of a constant, which is worth having exactly once: every other test in this feature
     * names an amount of money because it has to deposit one, and this is the assertion that says
     * which of those amounts are rungs. Anything else naming a rung as a literal is naming it a
     * second time.
     */
    @Test
    void the_rungs_are_six_round_figures_ascending_and_quoted_to_the_cent() {
        assertThat(BalanceThresholds.THE_RUNGS)
                .containsExactlyElementsOf(List.of(
                        new BigDecimal("100.00"),
                        new BigDecimal("500.00"),
                        new BigDecimal("1000.00"),
                        new BigDecimal("2500.00"),
                        new BigDecimal("5000.00"),
                        new BigDecimal("10000.00")));
        assertThat(BalanceThresholds.THE_RUNGS)
                .as("ascending, because every reading of the ladder walks it in order")
                .isSorted();
        assertThat(BalanceThresholds.THE_RUNGS)
                .as("money, at two decimal places, as every other amount in this application is")
                .allSatisfy(rung -> assertThat(rung.scale()).isEqualTo(2));
    }

    /**
     * A balance reaching a rung exactly stands on it, and a balance a cent short of it does not.
     *
     * <p>The boundary the word "reached" decides. A customer whose balance reads exactly the round
     * figure they were aiming at has reached it, and one who is a cent short has not — which is the
     * difference between a notification arriving on the deposit that got them there and arriving on
     * the one after it.
     */
    @Test
    void a_balance_stands_on_the_rung_it_reaches_exactly_and_not_on_the_one_it_is_a_cent_short_of() {
        for (BigDecimal rung : BalanceThresholds.THE_RUNGS) {
            assertThat(BalanceThresholds.theRungStoodOnWith(rung))
                    .as("a balance of exactly " + rung.toPlainString() + " has reached it")
                    .contains(rung);
            assertThat(BalanceThresholds.theRungStoodOnWith(rung.subtract(aCent())))
                    .as("a cent short of " + rung.toPlainString() + " is not standing on it")
                    .isNotEqualTo(Optional.of(rung));
        }
    }

    /**
     * Below the lowest rung there is no rung at all, which is where every account starts and where an
     * emptied one ends up.
     */
    @Test
    void a_balance_below_the_lowest_rung_stands_on_nothing() {
        assertThat(BalanceThresholds.theRungStoodOnWith(new BigDecimal("0.00"))).isEmpty();
        assertThat(BalanceThresholds.theRungStoodOnWith(new BigDecimal("99.99"))).isEmpty();
    }

    /**
     * A balance above the top of the ladder stands on the top rung and stays there.
     *
     * <p>Which is the whole of what the ladder has to say about a large balance: there is no rung
     * above the last one, so a customer saving past it is told about it once and not again.
     */
    @Test
    void a_balance_above_the_top_of_the_ladder_stands_on_the_top_rung() {
        BigDecimal theTop = theTopRung();

        assertThat(BalanceThresholds.theRungStoodOnWith(theTop.add(aCent()))).contains(theTop);
        assertThat(BalanceThresholds.theRungStoodOnWith(theTop.multiply(new BigDecimal("10"))))
                .contains(theTop);
        assertThat(BalanceThresholds.theRungAbove(theTop))
                .as("there is nothing above the top of the ladder to climb to")
                .isEmpty();
    }

    /**
     * Each rung's neighbours are the rungs either side of it, and the ends have one neighbour each.
     *
     * <p>The two readings the rule turns on. Reading a lost rung back into a position is
     * {@code theRungBelow}, and deciding which rung a fallen balance has fallen off is
     * {@code theRungAbove} — and they have to be each other's inverse, or a fall would be announced
     * twice.
     */
    @Test
    void the_rung_below_and_the_rung_above_are_the_neighbours_on_the_ladder() {
        List<BigDecimal> rungs = BalanceThresholds.THE_RUNGS;
        for (int i = 0; i < rungs.size(); i++) {
            BigDecimal rung = rungs.get(i);
            assertThat(BalanceThresholds.theRungBelow(rung))
                    .isEqualTo(i == 0 ? Optional.empty() : Optional.of(rungs.get(i - 1)));
            assertThat(BalanceThresholds.theRungAbove(rung))
                    .isEqualTo(i == rungs.size() - 1
                            ? Optional.empty() : Optional.of(rungs.get(i + 1)));
        }
        for (BigDecimal rung : rungs) {
            BalanceThresholds.theRungAbove(rung).ifPresent(above ->
                    assertThat(BalanceThresholds.theRungBelow(above))
                            .as("reading a lost rung back has to land on the rung the balance is on")
                            .contains(rung));
        }
    }

    /**
     * Asked about an amount that is not a rung, both neighbours are still answered, and about the
     * amount rather than about a rung it might have been rounded to.
     *
     * <p>Which is what the sweep needs of {@code theRungAbove}: it is asked what a balance has
     * fallen off, and a balance is hardly ever a round figure. A balance of EUR 600 has fallen off
     * EUR 1.000, not off EUR 500 — it is still standing on EUR 500.
     */
    @Test
    void an_amount_between_two_rungs_has_both_of_them_as_neighbours() {
        BigDecimal betweenTwoRungs = new BigDecimal("600.00");

        assertThat(BalanceThresholds.theRungStoodOnWith(betweenTwoRungs))
                .contains(new BigDecimal("500.00"));
        assertThat(BalanceThresholds.theRungAbove(betweenTwoRungs))
                .contains(new BigDecimal("1000.00"));
        assertThat(BalanceThresholds.theRungBelow(betweenTwoRungs))
                .contains(new BigDecimal("500.00"));
    }

    /**
     * A figure that has been through the database is compared as money and not as an object.
     *
     * <p>SQLite has no decimal type: EUR 100,00 goes in and comes back as {@code 100.0}, which is
     * the same money and a different {@link BigDecimal}. A ladder that compared with
     * {@code equals} would read every rung it had ever written down as a position it did not
     * recognise.
     */
    @Test
    void a_rung_that_has_been_through_the_database_is_still_that_rung() {
        BigDecimal asSqliteHandsItBack = new BigDecimal("100.0");

        assertThat(BalanceThresholds.theRungStoodOnWith(asSqliteHandsItBack))
                .contains(new BigDecimal("100.00"));
        assertThat(BalanceThresholds.theRungBelow(asSqliteHandsItBack)).isEmpty();
        assertThat(BalanceThresholds.theRungAbove(asSqliteHandsItBack))
                .contains(new BigDecimal("500.00"));
    }

    private static BigDecimal theTopRung() {
        return BalanceThresholds.THE_RUNGS.get(BalanceThresholds.THE_RUNGS.size() - 1);
    }

    private static BigDecimal aCent() {
        return new BigDecimal("0.01");
    }
}
