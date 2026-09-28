package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;

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
 * of the six rungs has all three. So the arithmetic is asserted here, on the three functions that
 * decide it, and the HTTP tests are about the rule that uses them: that a change of rung is
 * announced once and an unchanged rung is not announced at all.
 *
 * <p><strong>This class absorbed {@code BalanceThresholdsTest} when the constant it tested was
 * deleted.</strong> That test asserted the same three readings against the static ladder, and every
 * one of its cases that is still a fact about the <em>rule</em> rather than about the constant is
 * below, asked of {@link #THE_LADDER_IN_FORCE_TODAY}. The two that did not survive are the two that
 * were about the constant: the assertion that the ladder is those six round figures, which is now a
 * fact about what version 1 of the scheme is seeded at and is pinned where the seed is tested; and
 * the assertion that the value type answers what the static form answers, which had nothing left to
 * compare against once the static form was gone.
 *
 * <p>And it goes on asserting the things that only became askable once the ladder was a published
 * figure: a ladder that is not today's is walked the same way, and — the promise the class this
 * record came from made before this feature existed — all three functions stay total when the rungs
 * move under rows that were written against the old ones.
 */
class TheBalanceRungsTest {

    /**
     * The ladder every balance in this application has climbed: the six rungs version 1 of the
     * scheme publishes, written out rather than read off anything.
     *
     * <p>Written out because there is nothing left to read it off — and because reading it off the
     * seeded row would make every assertion below a statement that the code equals itself. Which
     * amounts are rungs today is a fact about the seed and is asserted where the seed is; what is
     * asserted here is what the three readings do to a ladder, and the ladder the application
     * actually runs on is the one whose boundaries a reader can check against the sentences above.
     */
    private static final TheBalanceRungs THE_LADDER_IN_FORCE_TODAY = new TheBalanceRungs(List.of(
            new BigDecimal("100.00"),
            new BigDecimal("500.00"),
            new BigDecimal("1000.00"),
            new BigDecimal("2500.00"),
            new BigDecimal("5000.00"),
            new BigDecimal("10000.00")));

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
        for (BigDecimal rung : THE_LADDER_IN_FORCE_TODAY.rungs()) {
            assertThat(THE_LADDER_IN_FORCE_TODAY.theRungStoodOnWith(rung))
                    .as("a balance of exactly " + rung.toPlainString() + " has reached it")
                    .contains(rung);
            assertThat(THE_LADDER_IN_FORCE_TODAY.theRungStoodOnWith(rung.subtract(aCent())))
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
        assertThat(THE_LADDER_IN_FORCE_TODAY.theRungStoodOnWith(new BigDecimal("0.00"))).isEmpty();
        assertThat(THE_LADDER_IN_FORCE_TODAY.theRungStoodOnWith(new BigDecimal("99.99"))).isEmpty();
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

        assertThat(THE_LADDER_IN_FORCE_TODAY.theRungStoodOnWith(theTop.add(aCent())))
                .contains(theTop);
        assertThat(THE_LADDER_IN_FORCE_TODAY.theRungStoodOnWith(
                theTop.multiply(new BigDecimal("10")))).contains(theTop);
        assertThat(THE_LADDER_IN_FORCE_TODAY.theRungAbove(theTop))
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
        List<BigDecimal> rungs = THE_LADDER_IN_FORCE_TODAY.rungs();
        for (int i = 0; i < rungs.size(); i++) {
            BigDecimal rung = rungs.get(i);
            assertThat(THE_LADDER_IN_FORCE_TODAY.theRungBelow(rung))
                    .isEqualTo(i == 0 ? Optional.empty() : Optional.of(rungs.get(i - 1)));
            assertThat(THE_LADDER_IN_FORCE_TODAY.theRungAbove(rung))
                    .isEqualTo(i == rungs.size() - 1
                            ? Optional.empty() : Optional.of(rungs.get(i + 1)));
        }
        for (BigDecimal rung : rungs) {
            THE_LADDER_IN_FORCE_TODAY.theRungAbove(rung).ifPresent(above ->
                    assertThat(THE_LADDER_IN_FORCE_TODAY.theRungBelow(above))
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

        assertThat(THE_LADDER_IN_FORCE_TODAY.theRungStoodOnWith(betweenTwoRungs))
                .contains(new BigDecimal("500.00"));
        assertThat(THE_LADDER_IN_FORCE_TODAY.theRungAbove(betweenTwoRungs))
                .contains(new BigDecimal("1000.00"));
        assertThat(THE_LADDER_IN_FORCE_TODAY.theRungBelow(betweenTwoRungs))
                .contains(new BigDecimal("500.00"));
    }

    /**
     * A figure that has been through the database is compared as money and not as an object.
     *
     * <p>SQLite has no decimal type: EUR 100,00 goes in and comes back as {@code 100.0}, which is
     * the same money and a different {@link BigDecimal}. A ladder that compared with
     * {@code equals} would read every rung it had ever written down as a position it did not
     * recognise — and now that the rungs themselves come out of the database on every sweep, that is
     * true of both sides of the comparison rather than only of the balance.
     */
    @Test
    void a_rung_that_has_been_through_the_database_is_still_that_rung() {
        BigDecimal asSqliteHandsItBack = new BigDecimal("100.0");

        assertThat(THE_LADDER_IN_FORCE_TODAY.theRungStoodOnWith(asSqliteHandsItBack))
                .contains(new BigDecimal("100.00"));
        assertThat(THE_LADDER_IN_FORCE_TODAY.theRungBelow(asSqliteHandsItBack)).isEmpty();
        assertThat(THE_LADDER_IN_FORCE_TODAY.theRungAbove(asSqliteHandsItBack))
                .contains(new BigDecimal("500.00"));
    }

    /**
     * A ladder that is not today's is walked the same way, including a ladder of one rung.
     *
     * <p>Reaching this over HTTP would take a published version of the scheme, a customer, a
     * deposit and a sweep, and would then assert one amount. A bank that publishes two rungs, or
     * one, has published a ladder, and it is worth knowing here that the three readings hold on it.
     */
    @Test
    void a_ladder_nobody_has_published_before_is_walked_exactly_the_same_way() {
        TheBalanceRungs twoRungs = new TheBalanceRungs(List.of(
                new BigDecimal("250.00"), new BigDecimal("750.00")));

        assertThat(twoRungs.theRungStoodOnWith(new BigDecimal("249.99"))).isEmpty();
        assertThat(twoRungs.theRungStoodOnWith(new BigDecimal("250.00")))
                .as("reached rather than passed")
                .contains(new BigDecimal("250.00"));
        assertThat(twoRungs.theRungStoodOnWith(new BigDecimal("900.00")))
                .contains(new BigDecimal("750.00"));
        assertThat(twoRungs.theRungBelow(new BigDecimal("750.00")))
                .contains(new BigDecimal("250.00"));
        assertThat(twoRungs.theRungAbove(new BigDecimal("250.00")))
                .contains(new BigDecimal("750.00"));
        assertThat(twoRungs.theRungAbove(new BigDecimal("750.00")))
                .as("above the top of the ladder there is no rung")
                .isEmpty();
    }

    /**
     * A notification written against a rung the bank no longer publishes is still read back into a
     * position.
     *
     * <p>The promise that was made before there was anything that could break it: a row saying EUR
     * 500 was lost is read as "the balance stands on the rung below EUR 500", and it goes on being
     * read that way after EUR 500 has stopped being a rung at all. Nothing throws, nothing returns a
     * rung that is not on this ladder, and a row from before a repricing is still a row somebody can
     * open. Ticket 06 raises no notification against an old ladder and rewrites none either; this is
     * what makes reading one back safe.
     */
    @Test
    void a_rung_that_has_stopped_being_a_rung_is_still_read_back_into_a_position() {
        TheBalanceRungs theRungsNow = new TheBalanceRungs(List.of(
                new BigDecimal("200.00"), new BigDecimal("2000.00")));
        BigDecimal aRungThatUsedToExist = new BigDecimal("500.00");

        assertThat(theRungsNow.theRungBelow(aRungThatUsedToExist))
                .as("the position a EUR 500 loss describes, read on the ladder in force now")
                .contains(new BigDecimal("200.00"));
        assertThat(theRungsNow.theRungAbove(aRungThatUsedToExist))
                .contains(new BigDecimal("2000.00"));
        assertThat(theRungsNow.theRungStoodOnWith(aRungThatUsedToExist))
                .contains(new BigDecimal("200.00"));
    }

    /**
     * A scheme that publishes no rungs at all congratulates nobody, and is answered rather than
     * thrown at.
     *
     * <p>An empty ladder is a real thing to publish — a bank that has decided to stop saying
     * anything about balances — and every one of the three readings has an honest empty answer for
     * it. Standing below the ladder is a position the rule already had to be able to hold.
     */
    @Test
    void a_ladder_with_no_rungs_leaves_every_balance_standing_on_nothing() {
        TheBalanceRungs noRungs = new TheBalanceRungs(List.of());

        assertThat(noRungs.theRungStoodOnWith(new BigDecimal("1000000.00"))).isEmpty();
        assertThat(noRungs.theRungBelow(new BigDecimal("1000000.00"))).isEmpty();
        assertThat(noRungs.theRungAbove(new BigDecimal("0.00"))).isEmpty();
    }

    /**
     * The rungs a published version of the scheme describes are the ones it publishes, in the order
     * it publishes them.
     *
     * <p>A small test of a small factory, and the one place in the application that assembly
     * happens.
     */
    @Test
    void the_rungs_in_a_published_scheme_are_the_list_it_carries() {
        TheSchemeAsPublished scheme = new TheSchemeAsPublished(
                1, LocalDate.of(2020, 1, 6), new BigDecimal("50.00"), new BigDecimal("1.0000"),
                new BigDecimal("0.1000"), new BigDecimal("1.5000"), 12,
                List.of(new BigDecimal("100.00"), new BigDecimal("500.00")),
                new BigDecimal("80.00"), 3, 30, 30, "what the scheme has always said");

        TheBalanceRungs rungs = TheBalanceRungs.theRungsIn(scheme);

        assertThat(rungs.rungs()).containsExactly(
                new BigDecimal("100.00"), new BigDecimal("500.00"));
        assertThat(rungs.theRungStoodOnWith(new BigDecimal("600.00")))
                .contains(new BigDecimal("500.00"));
    }

    private static BigDecimal theTopRung() {
        List<BigDecimal> rungs = THE_LADDER_IN_FORCE_TODAY.rungs();
        return rungs.get(rungs.size() - 1);
    }

    private static BigDecimal aCent() {
        return new BigDecimal("0.01");
    }
}
