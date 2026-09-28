package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One published version of a savings product's agreement as the API reports it. Shared for the same
 * reason as {@link BalancesView}: a second copy of the shape can drift into disagreeing about it,
 * and then one of them is testing a contract nobody serves.
 *
 * <p>It is the same record in both places the API sends one — nested inside
 * {@link SavingsProductView} as the terms on offer today, and listed on its own as the history a
 * product has published — because that is what the API does, and a test with two shapes for it
 * would stop noticing the day the two stopped being identical.
 *
 * <p>The rates are {@link BigDecimal} and so is the floor, because they are percentages and money
 * and a {@code double} in a test is how a test learns to accept 0.6000000000000001. They are
 * compared by value rather than by scale throughout, since how many places a figure carries over
 * the wire is a formatting question and not the rule under test.
 *
 * <p>The maturity action is text rather than an enum the test imports. A test asserting
 * {@code "ROLL_OVER"} is asserting what actually goes over the wire, which is what a page reads;
 * sharing the backend's enum would let a value be renamed on both sides at once with every test
 * still passing.
 *
 * <p>{@code whatChanged} is null on a first version, and a test that expects a sentence there is
 * expecting the backend to have invented one.
 *
 * <p>{@code whatIsDifferent} is the backend's own sentences saying what this version moved about the
 * version before it, and it is read as sentences rather than picked apart. A test that rebuilt
 * "The rate goes from 0.60% a year to 0.50% a year." out of two figures would be a second copy of
 * the wording the backend is supposed to own, and would go on passing the day the two stopped
 * agreeing. It is empty on a first version and on a version that moved no figure, and empty again on
 * the copy nested inside {@link SavingsProductView}, which is a card rather than a comparison.
 */
public record TermsVersionView(String productCode, int version, LocalDate effectiveFrom,
                               BigDecimal annualRatePercent, BigDecimal bonusRatePercent,
                               int noticeDays, int termMonths, BigDecimal minimumBalance,
                               int earlyExitPenaltyDays, BigDecimal pointsMultiplier,
                               BigDecimal anniversaryRatePercent, String maturityAction,
                               String whatChanged, List<String> whatIsDifferent) {
}
