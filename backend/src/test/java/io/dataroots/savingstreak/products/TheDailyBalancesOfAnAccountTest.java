package io.dataroots.savingstreak.products;

import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.products.TheDailyBalancesOfAnAccount.AMovementOfMoney;
import io.dataroots.savingstreak.products.TheDailyBalancesOfAnAccount.TheBalanceAcrossAPeriod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The walk that says what an account was worth on every day of a month, at the rule rather than
 * over HTTP.
 *
 * <p>Beside {@code PointsExpiryTest} and {@code LoyaltyAnniversaryTest}, and here for the reason
 * they are. Every other test in this repository drives the whole application over its API, which is
 * what keeps them free of storage decisions later slices need to change — but the only way to move
 * time in this application is {@code POST /api/dev/clock/advance}, which takes whole days and
 * refuses to go backwards. An API test can therefore show that a month's interest is a twelfth of
 * the rate on a balance; it cannot land a deposit on the last day of a period, take half of it back
 * out for one day, and empty the account for a fortnight, all inside one month, and then ask what
 * the average was. Those are the cases the arithmetic is most likely to be wrong in, so they are
 * asserted here.
 *
 * <p>Every case below is a sentence from the ticket: everything arriving on the last day, everything
 * on the first, a one-day dip, a period with no movement at all, and an account empty for part of
 * it. Each of them also stands for a decision argued in {@link TheDailyBalancesOfAnAccount} — the
 * average rather than either end, a day's balance read at the end of the day, the lowest coming out
 * of the same walk — and each is only worth arguing if something fails when it is undone.
 */
class TheDailyBalancesOfAnAccountTest {

    /** A thirty-one day period, so that the divisions below are ones a reader can redo. */
    private static final LocalDate MARCH = LocalDate.of(2026, 3, 1);

    private static final LocalDate APRIL = LocalDate.of(2026, 4, 1);

    /**
     * Money that was there for the whole period is averaged at itself, and a period nothing moved
     * in is that figure on every one of its days.
     *
     * <p>The case an account is in most months, and the one a walk over only the movements inside
     * the window would get wrong: there are none, so a walk that started from nothing would report
     * an average of nothing on an account holding a thousand euros.
     */
    @Test
    void a_period_with_no_movement_is_worth_what_the_account_was_already_holding() {
        TheBalanceAcrossAPeriod balances = across(
                List.of(paidIn(LocalDate.of(2026, 1, 20), 100_000)));

        assertThat(balances.averageCents()).isEqualTo(100_000);
        assertThat(balances.lowestCents()).isEqualTo(100_000);
        assertThat(balances.days()).isEqualTo(31);
    }

    /**
     * Money that arrives on the first day is paid for the whole period, because it was there for
     * the whole of it.
     *
     * <p>A day's balance is the balance at the <em>end</em> of that day, so a deposit on the 1st is
     * in the average from the 1st. Counting it from the day after would make this account worth
     * thirty days out of thirty-one and would be a rule with no reason a customer could see.
     */
    @Test
    void money_that_arrives_on_the_first_day_is_paid_for_every_day_of_the_period() {
        TheBalanceAcrossAPeriod balances = across(List.of(paidIn(MARCH, 310_000)));

        assertThat(balances.averageCents()).isEqualTo(310_000);
        assertThat(balances.lowestCents()).isEqualTo(310_000);
    }

    /**
     * Money that arrives on the last day is paid for one day of the period, which is the sentence
     * the average daily balance exists to make true.
     *
     * <p>The closing balance would pay a whole month on it. Three thousand one hundred euros
     * arriving on the 31st of a thirty-one day month averages a hundred: one day out of thirty-one,
     * which a reader can redo in their head, and which is also the strongest single argument
     * against paying on the balance at either end of a period.
     */
    @Test
    void money_that_arrives_on_the_last_day_is_paid_for_one_day() {
        TheBalanceAcrossAPeriod balances = across(List.of(paidIn(MARCH.plusDays(30), 310_000)));

        assertThat(balances.averageCents()).isEqualTo(10_000);
        // And the lowest is nought, because the account held nothing on the thirty days before it.
        assertThat(balances.lowestCents()).isZero();
    }

    /**
     * A balance that dips for a single day and comes back costs a thirty-first of the dip in the
     * average — and the lowest remembers the dip in full.
     *
     * <p>The two figures pulling in different directions is the whole reason both are taken from
     * one walk. The average is generous, so a day's dip does not cost a customer the month's
     * interest; the lowest is not, so a floor cannot be kept by putting the money back before
     * anybody looks. Paying on the lowest would have cost this account nine tenths of its month for
     * one afternoon.
     */
    @Test
    void a_balance_that_dips_for_one_day_loses_one_days_worth_of_the_average() {
        TheBalanceAcrossAPeriod balances = across(List.of(
                paidIn(LocalDate.of(2026, 2, 1), 100_000),
                takenOut(MARCH.plusDays(9), 90_000),
                paidIn(MARCH.plusDays(10), 90_000)));

        // Thirty days at a thousand euros and one at a hundred, over thirty-one days.
        assertThat(balances.averageCents()).isEqualTo((30 * 100_000 + 10_000) / 31);
        assertThat(balances.lowestCents()).isEqualTo(10_000);
    }

    /**
     * An account empty for part of the period is averaged over the whole of it, including the days
     * it held nothing.
     *
     * <p>A period is a month, not a count of the days money happened to be in the account, and
     * averaging over only the days with a balance would pay somebody who saved for one day of March
     * a whole month's interest.
     */
    @Test
    void an_account_empty_for_part_of_the_period_is_averaged_over_the_whole_of_it() {
        TheBalanceAcrossAPeriod balances = across(List.of(
                paidIn(LocalDate.of(2026, 2, 10), 62_000),
                // Emptied on the first of the month and paid back in with twenty days to go.
                takenOut(MARCH, 62_000),
                paidIn(MARCH.plusDays(11), 62_000)));

        // Twenty days at six hundred and twenty euros and eleven at nothing.
        assertThat(balances.averageCents()).isEqualTo(20 * 62_000 / 31);
        assertThat(balances.lowestCents()).isZero();
    }

    /**
     * Movements after the period has ended do not touch it, which is what lets a year be paid in one
     * run without the first month being worked out on money that arrived in the twelfth.
     */
    @Test
    void money_that_arrives_after_the_period_has_ended_is_no_part_of_it() {
        TheBalanceAcrossAPeriod balances = across(List.of(
                paidIn(MARCH, 50_000),
                paidIn(APRIL, 1_000_000),
                paidIn(APRIL.plusDays(15), 1_000_000)));

        assertThat(balances.averageCents()).isEqualTo(50_000);
    }

    /**
     * The average is floored to the cent, downwards, once — so a figure that does not divide evenly
     * is reported as the balance the account can be said to have held rather than a cent above it.
     */
    @Test
    void an_average_that_does_not_divide_evenly_is_floored_to_the_cent() {
        TheBalanceAcrossAPeriod balances = across(List.of(
                paidIn(MARCH, 10_000),
                paidIn(MARCH.plusDays(1), 1)));

        // A day at a hundred euros and thirty at a hundred euros and a cent: 3 100 030 cents over
        // thirty-one days is 100 000.967…, which is a hundred euros and no cent at all.
        assertThat(balances.averageCents()).isEqualTo((30 * 10_001 + 10_000) / 31);
        assertThat(balances.averageCents()).isEqualTo(10_000);
    }

    /** A period of no days at all is a caller's mistake rather than anything a customer could do. */
    @Test
    void a_period_that_is_not_at_least_a_day_long_is_refused_to_the_caller() {
        assertThatThrownBy(() -> TheDailyBalancesOfAnAccount.across(List.of(), MARCH, MARCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least a day long");
    }

    private static TheBalanceAcrossAPeriod across(List<AMovementOfMoney> movements) {
        return TheDailyBalancesOfAnAccount.across(movements, MARCH, APRIL);
    }

    private static AMovementOfMoney paidIn(LocalDate on, long cents) {
        return new AMovementOfMoney(on, cents);
    }

    private static AMovementOfMoney takenOut(LocalDate on, long cents) {
        return new AMovementOfMoney(on, -cents);
    }
}
