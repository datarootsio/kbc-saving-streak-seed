package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import io.dataroots.savingstreak.deposits.AmountOfMoney;

/**
 * The next six weeks of one current account's cash flow, week by week, and what that says its holder
 * could save each week.
 *
 * <p><strong>The figure this whole feature exists to produce.</strong> Everything else in this
 * module — the words a customer describes their money with, the figure on each of them, the spends,
 * the bills filed under them, the carry — is here so that this answer can be given: over the weeks a
 * person actually plans on, how much room is there after everything they have already promised
 * themselves? A balance says "you have 2480 euros". A month card says what one month leaves. This
 * says what each of the next six weeks leaves, which is the only one of the three that a customer
 * can act on this afternoon.
 *
 * <p><strong>Six weeks, beginning on the Monday the current week began on.</strong> Six because it
 * spans a whole monthly pay cycle with room either side, and because six rows fit on a screen
 * somebody can read. The Monday because that is the week boundary {@code SavingsWeek} already owns:
 * a second definition of a week would put a salary and the rows it pays for in different weeks, and
 * would leave a customer comparing a budgeting week against a streak week that is not the same seven
 * days.
 *
 * <p><strong>It and {@code TheMonthAhead} are two windows on one calendar, not two calendars.</strong>
 * Both read the bill dates and the payday dates out of Accounts' own {@code WhenABillIsDue} and
 * {@code WhenIncomeIsDue} — the very calendars the 01:00 and 02:30 runs walk — so the two cards
 * cannot disagree about which day anything falls on and differ only in how far they look and how
 * finely they cut it. Neither grows a copy of the other's arithmetic. The month card's own
 * documentation argues at length about a clamp this window does not need, because six weeks is a
 * count of days rather than a calendar month.
 *
 * <p><strong>{@code couldSaveWeekly} is offered and never applied.</strong> It is what the six
 * weeks leave, divided by six and quoted as a weekly figure beside whatever the customer has
 * declared to the goals engine. Goals plan on what their holder <em>said</em>, and a module reaching
 * across to overwrite that declaration would take a commitment and turn it into a derived number
 * that changes every time somebody buys petrol. Adopting it is one press, which sends this figure to
 * the capacity endpoint that already exists; the two are put side by side in the web and frontend
 * layers, which is where this application already puts modules that must not know about each other,
 * and neither {@code budgets} nor {@code goals} learns that the other exists.
 *
 * <p><strong>Never negative, even where {@code leftOver} is.</strong> A customer whose six weeks do
 * not cover themselves could save nothing; quoting them a negative weekly figure would be offering
 * them a capacity nobody could declare and the engine could not spend. {@code leftOver} beside it is
 * the honest figure and is negative when it is negative, which is the week-by-week warning this read
 * is also for. Rounded <em>down</em> to the cent, because the whole purpose of the number is to be
 * one a customer will not be caught out by.
 *
 * <p><strong>Six weeks is a display horizon and it is not the scheme.</strong> Worth saying
 * plainly now that the bank publishes a scheme in dated versions, because a run of secured weeks
 * is also counted in weeks and the two counts have nothing to do with each other. This one is how
 * many rows fit on a card somebody reads on a Tuesday; the scheme's figures are what a week has to
 * take in to be secured and what a run of them pays. Nothing on this card is priced by the scheme
 * and nothing published on the scheme lengthens or shortens this window. The prose audit that came
 * with the scheme's workspace went looking for sentences with a policy figure written into them,
 * found three — all about how long a batch of points lasts — and left the sentence about the next
 * six weeks exactly where it was, on purpose.
 *
 * <p>Every figure is derived on every read from the declarations, the movements and the two
 * calendars. Nothing is stored, no projection is kept and no job produces any of it: record a spend,
 * correct a split, declare a bill or supersede a budget, and the next read of this says something
 * else, with nothing to invalidate.
 */
public record TheWeeksAhead(long currentAccountId, LocalDate from, LocalDate until,
                            BigDecimal arriving, BigDecimal committed, BigDecimal claimedByBudgets,
                            BigDecimal leftOver, BigDecimal couldSaveWeekly,
                            List<WhatAWeekAheadHolds> weeks) {

    /**
     * How far ahead this looks, in one place.
     *
     * <p>Counted in weeks rather than in days or in a month, because the rows are weeks: the window
     * is exactly the six rows drawn in it, and a horizon stated in any other unit would be a second
     * answer to how long the card is. A figure a training exercise might want to change, which is
     * why it is one named constant with its reasoning beside it rather than a literal in a loop.
     */
    public static final int HOW_MANY_WEEKS_IT_LOOKS_AHEAD = 6;

    /**
     * The six rows with the totals summed from them and the weekly offer taken off the total, so
     * that the figure at the top of the card and the figures under it cannot be worked out two ways.
     *
     * <p>The one place {@code couldSaveWeekly} comes from. A second division anywhere — on a page,
     * in a controller — would be a second answer to what a customer could save, and the two would
     * disagree the first time either changed.
     */
    static TheWeeksAhead of(long currentAccountId, List<WhatAWeekAheadHolds> weeks) {
        BigDecimal arriving = totalOf(weeks, WhatAWeekAheadHolds::arriving);
        BigDecimal committed = totalOf(weeks, WhatAWeekAheadHolds::committed);
        BigDecimal claimedByBudgets = totalOf(weeks, WhatAWeekAheadHolds::claimedByBudgets);
        BigDecimal leftOver = arriving.subtract(committed).subtract(claimedByBudgets);
        return new TheWeeksAhead(currentAccountId, weeks.get(0).startsOn(),
                weeks.get(weeks.size() - 1).endsOn(),
                AmountOfMoney.quotedToTheCent(arriving),
                AmountOfMoney.quotedToTheCent(committed),
                AmountOfMoney.quotedToTheCent(claimedByBudgets),
                AmountOfMoney.quotedToTheCent(leftOver),
                whatThatSaysCouldBeSavedEachWeek(leftOver, weeks.size()),
                weeks);
    }

    /**
     * What the six weeks' leftover says can go into savings each week: the total divided by the
     * number of weeks it was counted over, rounded down to the cent, and never less than nothing.
     *
     * <p>Down rather than up, and this is the opposite decision from the one the goals engine makes
     * about a deadline minimum — deliberately, because the two figures promise opposite things. A
     * deadline minimum is the least that <em>arrives in time</em>, so it rounds up and arrives with
     * a cent to spare; this is the most that can be <em>spared</em>, so it rounds down and leaves a
     * cent behind. Rounded up, a customer adopting it would be locking away a cent a week they did
     * not have, which is a small amount of exactly the wrong thing.
     *
     * <p>Divided by the weeks actually counted rather than by the constant, so that the two can
     * never drift apart: the figure is an average of the rows the customer is looking at.
     */
    private static BigDecimal whatThatSaysCouldBeSavedEachWeek(BigDecimal leftOver, int weeks) {
        if (leftOver.signum() <= 0) {
            return AmountOfMoney.quotedToTheCent(BigDecimal.ZERO);
        }
        return leftOver.divide(BigDecimal.valueOf(weeks), 2, RoundingMode.FLOOR);
    }

    /** One column added down the rows, so that the card and the rows are one answer read twice. */
    private static BigDecimal totalOf(List<WhatAWeekAheadHolds> weeks,
                                      Function<WhatAWeekAheadHolds, BigDecimal> column) {
        return weeks.stream().map(column).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Whether there is anything to offer at all, which is what tells a customer with room from one
     * whose six weeks are already spoken for.
     *
     * <p>Asked here rather than by every screen comparing the figure against nought, for the reason
     * {@code SavingCapacityOnAnAccount.isDeclared} is asked there: a comparison made once cannot be
     * made two ways, and a page offering a customer a press that would declare nothing would be
     * offering them a worse plan than the one they have.
     */
    public boolean isWorthOffering() {
        return couldSaveWeekly.signum() > 0;
    }
}
