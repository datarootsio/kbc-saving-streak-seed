package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A month's budget laid out over the weeks it touches: evenly over its days, summed into weeks, and
 * adding back up to the month exactly.
 *
 * <p><strong>The one place a month becomes weeks, and the reason the weekly forecast can be checked
 * with a pencil.</strong> A customer plans on weeks and declares budgets on months, and something
 * has to convert between the two; doing it in the read that draws the rows would put the conversion
 * beside every other figure it is being added to, where a cent going missing would be invisible.
 *
 * <p><strong>An even spread, and never a prediction.</strong> Every day of the month is handed the
 * same share of the figure, and a week gets the days of that month it actually holds. This
 * application invents no behaviour anywhere — a spend is declared rather than generated, a bill
 * falls on the day its holder said — and supposing that somebody spends more of their Groceries on
 * a Saturday would be exactly the invention the whole feature is built not to make. What the spread
 * claims is arithmetic; what it does not claim is knowledge of anybody's week.
 *
 * <p><strong>Rounded to the cent with the remainder on the last week the month touches, so the rows
 * sum back to the month.</strong> Seven thirty-firsts of two hundred euros is 45.1612…, and six
 * weeks rounded independently come to a cent less than the month they came from. That cent has to
 * go somewhere a reader can find it: on the last week, where it is the residue of the month being
 * finished off, rather than spread as a fraction nobody can quote or dropped as a rounding nobody
 * can see. A forecast that lost a cent a month would be wrong by an amount a customer could neither
 * check nor account for, in the one figure this feature invites them to lock away in savings on.
 *
 * <p><strong>Every week the month touches, including the ones it only leans into.</strong> A month
 * beginning on a Sunday puts one day into the week that began in the month before, and a month
 * ending on a Tuesday puts two into the week that runs on into the next. Both of those weeks carry
 * a share, because a week is a week whichever months it straddles and a customer reading a Monday
 * row at the turn of a month is looking at exactly such a week. What the caller does with a week
 * outside its own window is the caller's business: this answers about the month.
 *
 * <p><strong>The week boundary is {@link SavingsWeek}'s and there is no second definition of a week
 * in this module.</strong> The streak already owns what a week is — Monday to Sunday, counted in
 * Brussels — and a budgeting week that began on a different day would put a salary and the rows it
 * was supposed to pay for in different weeks. Quoting it is quoting a rule rather than reading a
 * module: no repository, no entity and no state.
 *
 * <p>Static, with no state, no clock, no repository and no bean, exactly as
 * {@link WhatCarriesIntoAMonth}, {@link TheMonthAMomentFallsIn}, {@code HowAnAmountIsSplit} and
 * {@code HowTheWeeklyMoneyIsSpent} are. Everything it needs is two values the caller already holds,
 * which is what lets the interesting cases — a month of four whole weeks, one hanging off both
 * ends, one whose division does not come out in cents — be asserted directly rather than wound onto
 * a clock a day at a time.
 */
final class HowAMonthsBudgetIsSpreadOverWeeks {

    private static final Logger log =
            LoggerFactory.getLogger(HowAMonthsBudgetIsSpreadOverWeeks.class);

    /** How many places money has here, which is {@code AmountOfMoney}'s answer and not a second one. */
    private static final int TO_THE_CENT = 2;

    private HowAMonthsBudgetIsSpreadOverWeeks() {
    }

    /**
     * What each week the month touches is handed of the month's figure, in calendar order, keyed on
     * the Monday the week begins on.
     *
     * <p>The Monday rather than a {@link SavingsWeek}, because that is the one figure that
     * identifies a week and it is what the caller is already holding for each of its rows — a map
     * keyed on the record would be a map whose lookups depended on a second type's equality.
     *
     * <p>The answer always adds back up to {@code allowance} to the cent. That is the whole contract
     * and the reason the remainder is placed rather than dropped.
     *
     * @param month     the month whose figure is being laid out
     * @param allowance what that month allows altogether — the budgets plus their carry for a month
     *                  still to come, or what is left of them in the month the clock is in. Never
     *                  null; a month allowing nothing is a month whose every week claims nothing,
     *                  which is a real answer rather than an absence.
     */
    static Map<LocalDate, BigDecimal> acrossTheWeeksItTouches(YearMonth month,
                                                              BigDecimal allowance) {
        BigDecimal toShareOut = AmountOfMoney.quotedToTheCent(allowance);
        Map<LocalDate, Integer> daysInEachWeek = howManyOfItsDaysEachWeekHolds(month);
        BigDecimal daysInTheMonth = BigDecimal.valueOf(month.lengthOfMonth());

        Map<LocalDate, BigDecimal> spread = new LinkedHashMap<>();
        BigDecimal handedOut = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, Integer> week : daysInEachWeek.entrySet()) {
            BigDecimal share = toShareOut
                    .multiply(BigDecimal.valueOf(week.getValue()))
                    .divide(daysInTheMonth, TO_THE_CENT, RoundingMode.HALF_UP);
            spread.put(week.getKey(), share);
            handedOut = handedOut.add(share);
        }

        // The cent the roundings lost, put on the last week the month touches. Merged rather than
        // put, because a month of a single week is one entry and the remainder belongs on it too.
        LocalDate theLastWeekItTouches = theLastOf(daysInEachWeek);
        BigDecimal remainder = toShareOut.subtract(handedOut);
        spread.merge(theLastWeekItTouches, remainder, BigDecimal::add);

        // The apportionment behind every row this month contributes to, on one line: what was being
        // shared out, over how many days, how many of them each week held, and what each week ended
        // up with — which is what a customer saying "why does that week claim 45.16" is answered
        // from. The remainder is named separately because it is the one figure in the row that is
        // not a division, and a reviewer checking the rows against the month is checking exactly it.
        log.debug("a month's budget spread over weeks month={} allowance={} daysInTheMonth={} "
                        + "weeks={} remainderOn={} remainder={} spread=[{}]",
                month, AmountOfMoney.asMoney(toShareOut), month.lengthOfMonth(), spread.size(),
                theLastWeekItTouches, AmountOfMoney.asMoney(remainder),
                asWeeks(spread, daysInEachWeek));
        return spread;
    }

    /**
     * How many days of this month each week the month touches actually holds, in calendar order.
     *
     * <p>Counted day by day through {@link SavingsWeek#containing(LocalDate)} rather than worked out
     * from the month's first and last weekday. It is thirty-one iterations at most, it is run once
     * per month per read, and the arithmetic it replaces — which is the arithmetic that is wrong
     * about February in a leap year and about a month beginning on a Sunday — is the arithmetic this
     * whole class exists not to have two versions of.
     *
     * <p>A {@link LinkedHashMap} because the order is the calendar's and the caller reads the last
     * entry to place the remainder.
     */
    private static Map<LocalDate, Integer> howManyOfItsDaysEachWeekHolds(YearMonth month) {
        Map<LocalDate, Integer> days = new LinkedHashMap<>();
        for (int dayOfMonth = 1; dayOfMonth <= month.lengthOfMonth(); dayOfMonth++) {
            LocalDate theWeekItIsIn = SavingsWeek.containing(month.atDay(dayOfMonth)).startsOn();
            days.merge(theWeekItIsIn, 1, Integer::sum);
        }
        return days;
    }

    /**
     * The last week the month touches, which is where the remainder lands.
     *
     * <p>Read off the walk above rather than worked out again from the month's last day, so that
     * the week the remainder goes on is provably one of the weeks that was handed a share — a
     * second derivation could name a week that is not in the map and quietly invent a row.
     */
    private static LocalDate theLastOf(Map<LocalDate, Integer> daysInEachWeek) {
        LocalDate last = null;
        for (LocalDate week : daysInEachWeek.keySet()) {
            last = week;
        }
        return last;
    }

    /**
     * The weeks written out for the log line: the Monday, how many days of the month it held, and
     * what it was given.
     *
     * <p>Assembled unconditionally rather than under a debug guard, unlike the goals engine's own
     * line: this is at most six short entries and it runs once per month per read, where that one
     * writes out every goal on an account on every read of every goal.
     */
    private static String asWeeks(Map<LocalDate, BigDecimal> spread,
                                  Map<LocalDate, Integer> daysInEachWeek) {
        List<String> said = new ArrayList<>();
        spread.forEach((week, share) -> said.add(week + " days=" + daysInEachWeek.get(week)
                + " claims=" + AmountOfMoney.asMoney(share)));
        return String.join("; ", said);
    }
}
