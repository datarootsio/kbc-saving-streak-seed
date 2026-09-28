package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The carry: how much of one category's earlier months arrives in a later one, month by month, under
 * the rule that stood in each of them.
 *
 * <p><strong>A pure function over months, and the reason the rest of this module can stay
 * small.</strong> For one category it walks forward from the first month a figure stood in, and for
 * each month it takes the budget that stood, adds what carried into it, subtracts what it cost, and
 * hands the difference on according to <em>that month's</em> rule — nothing, the surplus only if
 * positive, or the difference whatever its sign. What comes out is what arrives in the month asked
 * about. {@link RolloverRule#whatItHandsOn} is the one place the three rules differ; everything else
 * below is the same arithmetic whichever rule stood.
 *
 * <p><strong>Nothing is stored and there is no month-end close.</strong> This runs on every read,
 * from the declarations and the movements, exactly as every other preview in this codebase does.
 * There is no rollup row, no cursor to advance, no catch-up to get right and nothing to invalidate —
 * which is what makes a correction to a March split move March's carry, April's, and every month
 * after them, rather than leave a note saying March was wrong. A stored carry would be a figure free
 * to disagree with the records it was summed from, and the first month it did would be the month a
 * customer stopped believing any of it.
 *
 * <p><strong>The rule is read per month and never from the figure standing now.</strong> Changing
 * the rule supersedes the budget exactly as changing the amount does, so a customer who turns
 * Groceries into an envelope in June has said something about June — and April and May go on folding
 * under the rule they were actually kept under. Reading the standing rule and applying it backwards
 * would rearrange a customer's history the moment they changed their mind, which is precisely what
 * the supersession exists to prevent.
 *
 * <p><strong>A month with no figure in it carries nothing out of itself.</strong> Not budgeting a
 * category is not the same as budgeting it at nought: the customer has stopped holding themselves to
 * anything, so there is no surplus to keep and no overspend to chase, and the chain restarts at the
 * next figure they name. The alternative — carrying a surplus across months nobody was being
 * measured in — would hand a customer money they had earned under a promise they had withdrawn.
 *
 * <p>Static, with no state, no clock, no repository and no bean, exactly as
 * {@code HowTheWeeklyMoneyIsSpent}, {@code HowAnAmountIsSplit} and {@link TheMonthAMomentFallsIn}
 * are. Everything it needs is a value its caller already holds, which is what lets the interesting
 * cases — three rules over three months with one overspent month in the middle, a budget that took
 * effect thirty months ago — be asserted directly instead of being wound onto a clock an hour at a
 * time.
 */
final class WhatCarriesIntoAMonth {

    private static final Logger log = LoggerFactory.getLogger(WhatCarriesIntoAMonth.class);

    /**
     * How far back a carry is folded: two years of months, and no further.
     *
     * <p><strong>A decision rather than a defensive guard.</strong> A chain is only worth walking as
     * far as a customer would recognise it. Two years spans every seasonal story a budget can tell —
     * a Christmas that was paid for by the autumn, a boiler that took a winter to work off — and
     * beyond it a surplus quoted this month would be the residue of a promise its holder made before
     * they had this job, this rent or these categories, which is a figure nobody can act on and one
     * no honest read should offer.
     *
     * <p>It is also what keeps a demo honest. The development clock winds forward in whole days and
     * a trainer showing envelopes carry may put years on it in an afternoon; an unbounded fold would
     * quietly grow the cost of every page in the feature with every wind, and the page that got slow
     * would be the one the feature exists to draw. Bounded, the read costs the same in year three as
     * it does in month two.
     *
     * <p>Beyond the bound the carry is taken as nought rather than approximated. Twenty-four is a
     * figure a training exercise might well want to change, which is exactly why it is one named
     * constant with its reasoning beside it rather than a literal in a loop.
     */
    static final int HOW_MANY_MONTHS_A_CARRY_IS_FOLDED_OVER = 24;

    private WhatCarriesIntoAMonth() {
    }

    /**
     * The earliest month any read of {@code month} has to know about, which is the month the fold
     * would start from if a figure had stood for ever.
     *
     * <p>Exposed so that the caller reading the budgets and the movements out of the database can
     * ask for exactly the window this class will walk. Two places deciding how far back to look
     * would be two places holding the bound, and the day they disagreed the fold would either walk
     * months it had no figures for or be handed figures it would throw away.
     */
    static YearMonth theEarliestMonthItWalksFrom(YearMonth month) {
        return month.minusMonths(HOW_MANY_MONTHS_A_CARRY_IS_FOLDED_OVER);
    }

    /**
     * What carries into one month on one category.
     *
     * <p>The months strictly before {@code month} are folded and {@code month} itself is not: what
     * comes back is what arrives in it, before a euro of it has been spent. A month still running is
     * folded into exactly as one already gone is — there is no close, so a customer reads on the
     * third what they would read on the thirty-first.
     *
     * <p>The walk starts at the first month a figure stood in, or at the bound if that is later, and
     * the carry into that first month is nought. A category whose first budget took effect thirty
     * months ago therefore starts twenty-four months back with a clean slate rather than walking to
     * the beginning: see {@link #HOW_MANY_MONTHS_A_CARRY_IS_FOLDED_OVER}, where that trade is
     * argued.
     *
     * <p>Every figure is quoted to the cent as it is handed on, so that twenty-four months of
     * arithmetic cannot accumulate a tenth of a cent that a customer would then have to explain.
     *
     * @param categoryId          carried for the log alone, so that a carry somebody disputes can be
     *                            followed month by month without reading this code — the same reason
     *                            {@code HowTheWeeklyMoneyIsSpent} carries its account
     * @param month               the month being read, which is folded <em>into</em> and not over
     * @param whatStoodInEachMonth the figure and the rule that governed each month, with a month
     *                            absent from it being a month the category carried no figure in
     * @param whatItCostInEachMonth what the category cost in each month — its bills and its spends
     *                            added — with a month absent from it having cost nothing
     */
    static BigDecimal carriedInto(long categoryId, YearMonth month,
                                  Map<YearMonth, ABudgetThatStood> whatStoodInEachMonth,
                                  Map<YearMonth, BigDecimal> whatItCostInEachMonth) {
        YearMonth from = theMonthTheWalkBeginsIn(month, whatStoodInEachMonth);
        if (from == null) {
            log.debug("carry folded categoryId={} month={} months=0 carriedIn={} reason={}",
                    categoryId, month, AmountOfMoney.asMoney(BigDecimal.ZERO),
                    "no figure has ever stood on this category in the months this fold covers");
            return BigDecimal.ZERO;
        }

        BigDecimal carry = BigDecimal.ZERO;
        int walked = 0;
        for (YearMonth walking = from; walking.isBefore(month); walking = walking.plusMonths(1)) {
            walked++;
            ABudgetThatStood stood = whatStoodInEachMonth.get(walking);
            BigDecimal cost = whatItCostInEachMonth.getOrDefault(walking, BigDecimal.ZERO);
            if (stood == null) {
                // A month nobody was holding themselves to anything in. It keeps neither its
                // surplus nor its overspend, and the chain begins again at the next figure named.
                log.debug("carry folded categoryId={} month={} budgeted={} carriedIn={} spent={} "
                                + "handsOn={}",
                        categoryId, walking, "not budgeted", AmountOfMoney.asMoney(carry),
                        AmountOfMoney.asMoney(cost), AmountOfMoney.asMoney(BigDecimal.ZERO));
                carry = BigDecimal.ZERO;
                continue;
            }
            BigDecimal allowed = stood.amount().add(carry);
            BigDecimal left = allowed.subtract(cost);
            BigDecimal handsOn = AmountOfMoney.quotedToTheCent(stood.rollover().whatItHandsOn(left));
            // One line per month of the chain, naming every figure that decided the next one. This
            // is what a customer disputing "why does May start with forty euros in it" is answered
            // from, and it is why the fold logs rather than merely returning.
            log.debug("carry folded categoryId={} month={} rule={} budgeted={} carriedIn={} "
                            + "allowed={} spent={} left={} handsOn={}",
                    categoryId, walking, stood.rollover(), AmountOfMoney.asMoney(stood.amount()),
                    AmountOfMoney.asMoney(carry), AmountOfMoney.asMoney(allowed),
                    AmountOfMoney.asMoney(cost), AmountOfMoney.asMoney(left),
                    AmountOfMoney.asMoney(handsOn));
            carry = handsOn;
        }

        log.debug("carry folded categoryId={} month={} from={} months={} carriedIn={}", categoryId,
                month, from, walked, AmountOfMoney.asMoney(carry));
        return carry;
    }

    /**
     * The month the walk begins in: the first one a figure stood in, held back to the bound, or
     * nothing at all if no figure ever stood in a month this fold covers.
     *
     * <p>Read off the figures themselves rather than taken as an argument, so that there is no way
     * for a caller to start the walk somewhere the rows do not support. A category budgeted for the
     * first time last month folds one month rather than twenty-four, which is both the right answer
     * and the quiet one in the log.
     */
    private static YearMonth theMonthTheWalkBeginsIn(YearMonth month,
                                                     Map<YearMonth, ABudgetThatStood> whatStood) {
        YearMonth bound = theEarliestMonthItWalksFrom(month);
        YearMonth earliest = null;
        for (YearMonth stoodIn : whatStood.keySet()) {
            if (!stoodIn.isBefore(month)) {
                // The month being read, and anything after it, is not folded over: the fold answers
                // what arrives in that month, and what its own figure allows is not a carry.
                continue;
            }
            YearMonth from = stoodIn.isBefore(bound) ? bound : stoodIn;
            if (earliest == null || from.isBefore(earliest)) {
                earliest = from;
            }
        }
        return earliest;
    }
}
