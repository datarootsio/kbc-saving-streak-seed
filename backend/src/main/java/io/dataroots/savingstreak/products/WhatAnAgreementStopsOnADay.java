package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import io.dataroots.savingstreak.deposits.AConditionInTheWay;
import io.dataroots.savingstreak.deposits.ConditionOnTheWayOut;

import static io.dataroots.savingstreak.deposits.AmountOfMoney.asMoney;

/**
 * What an agreement puts in the way of money leaving on a <em>named</em> day — the two refusals this
 * bank actually gives, written down once, as arithmetic over the figures rather than as a walk over
 * rows.
 *
 * <p><strong>Why this class exists at all.</strong> {@link TheConditionsOnTheWayOut} asks the two
 * conditions of the rows in front of it and of the clock; that is exactly right for a withdrawal
 * somebody is making now, and it is useless to anybody asking about a day that has not happened yet.
 * The what-if simulator replays a year of nights over a snapshot, and a scenario that takes money
 * out of a locked fixed term has to meet the same sentence at the same door rather than be quietly
 * projected as though the lock were not there. So the rows and the clock are lifted out and what is
 * left — the judgement and the words — lives here, where all three callers can reach it:
 * {@link TheTermAnAccountIsLockedInto} and {@link NoticesService} hand it today's rows and today's
 * date, and the fold hands it the snapshot's rows and the day the customer said they would take the
 * money out.
 *
 * <p><strong>Nothing is restated and that is the whole point.</strong> Before this class the term's
 * sentence lived in the service that reads the agreement and the notice's sentence lived in the
 * service that reads the notices, which was fine while there was one caller each. A simulator that
 * wrote its own version of either would be the second place this bank says why it is holding
 * somebody's money, and the two would drift the first time a penalty or a notice period was
 * reworded — leaving a customer told one thing by the projection and another by the withdrawal
 * screen. The services keep their reads, their transactions and their log lines; what they no longer
 * keep is an opinion about how the refusal reads.
 *
 * <p><strong>Readiness is recomputed from the day the notice was given, never read off the
 * record.</strong> {@link NoticeGiven} carries {@code ready}, {@code daysLeft} and {@code readyOn}
 * as they stood on the day that record was made, which for a fold is the day the window opened and
 * not the day being asked about. Only {@code givenOn} and {@code stillStanding} are facts rather
 * than readings, so those two are what this works from, and {@link WhenANoticeIsReady} turns them
 * into an answer about whichever day it was handed. For a caller asking about today the two
 * readings are the same figure by construction, which is why {@code NoticesService} can hand its own
 * records straight over and get the sentence it has always given, word for word.
 *
 * <p><strong>The order is {@link ConditionOnTheWayOut}'s and is not decided here.</strong>
 * {@link #whatStopsTakingOn} asks the term first and the notice second because that enum declares
 * them in that order and says at length why — a customer told about notice first would give notice
 * on money that was locked away for another eight months anyway. A floor is asked of nobody, here as
 * in the live gate, because a minimum balance withholds a bonus rather than holding anybody's money.
 *
 * <p>Pure, static and public, like {@code LoyaltyRate} and for the same argued reason: there is
 * nothing of this module's machinery in it. No repository, no entity, no clock, no state — four
 * figures, a list of two-field facts and a day. A module that calls this is not reading Products; it
 * is quoting a rule Products wrote down.
 */
public final class WhatAnAgreementStopsOnADay {

    private WhatAnAgreementStopsOnADay() {
        // arithmetic and words, not a thing
    }

    /**
     * The one condition in the way of taking that much out of an account on that agreement on that
     * day, or nothing at all when there is none.
     *
     * <p>The term first and the notice second, stopping at the first that has something to say, for
     * the reason {@link ConditionOnTheWayOut} gives: one reason at a time, and the biggest one
     * first.
     *
     * @param termMonths             how long the term is, and nought on an account that is not on
     *                               one
     * @param maturesOn              the day the term is up, and null on an account with no term
     * @param earlyExitPenaltyDays   how many days of interest breaking it costs, for the sentence
     * @param noticeDays             how much warning the agreement asks for, and nought when it asks
     *                               for none
     * @param standing               the notices still standing on the account, in the order a
     *                               withdrawal would spend them
     * @param amount                 what is being taken out
     * @param on                     the day it would be taken out on, which is today for a live
     *                               withdrawal and a day in the future for a projected one
     */
    public static Optional<AConditionInTheWay> whatStopsTakingOn(int termMonths, LocalDate maturesOn,
                                                                 int earlyExitPenaltyDays,
                                                                 int noticeDays,
                                                                 List<NoticeGiven> standing,
                                                                 BigDecimal amount, LocalDate on) {
        Optional<AConditionInTheWay> locked =
                aTermThatHasNotMatured(termMonths, maturesOn, earlyExitPenaltyDays, on);
        if (locked.isPresent()) {
            return locked;
        }
        return noticeThatHasNotRun(noticeDays, standing, amount, on);
    }

    /**
     * The lock a fixed term puts on every euro in the account until the day it matures, in the words
     * a customer meets on the withdrawal screen.
     *
     * <p>About the whole account rather than about the amount, which is why no amount is asked for:
     * a term that has not matured refuses a withdrawal of one cent exactly as it refuses one of
     * everything, and the sentence tells the customer the only two things they can do about it —
     * wait for the day, or break it for a stated price.
     *
     * @param maturesOn the day the term is up, and null on an account that is not on a term at all,
     *                  which stops nothing
     */
    public static Optional<AConditionInTheWay> aTermThatHasNotMatured(int termMonths,
                                                                      LocalDate maturesOn,
                                                                      int earlyExitPenaltyDays,
                                                                      LocalDate on) {
        if (maturesOn == null || termMonths <= 0
                || TheTermAnAccountIsLockedInto.hasMatured(maturesOn, on)) {
            return Optional.empty();
        }
        long daysLeft = TheTermAnAccountIsLockedInto.daysLeftOn(maturesOn, on);
        String reason = "That savings account is a " + termMonths + "-month fixed term and "
                + "the money is locked away until it matures on " + maturesOn + ", which is "
                + inDays(daysLeft) + " away. You can break the term early if you have to — ask what "
                + "it would cost first, because it costs " + earlyExitPenaltyDays + " days of "
                + "interest.";
        return Optional.of(
                new AConditionInTheWay(ConditionOnTheWayOut.A_TERM_THAT_HAS_NOT_MATURED, reason));
    }

    /**
     * Whether the notice already given covers this much on that day, and the sentence saying what to
     * do about it when it does not.
     *
     * <p>An agreement that asks for no notice stops nothing, which is three of the four products and
     * every account written before the catalogue existed.
     *
     * <p>The sentence names both figures and then names the one thing that would change them: the
     * soonest notice still running, if there is one, or the day notice given today would be ready
     * on, if there is not. A refusal that merely said no would leave the customer to work out which
     * day to come back on.
     */
    public static Optional<AConditionInTheWay> noticeThatHasNotRun(int noticeDays,
                                                                   List<NoticeGiven> standing,
                                                                   BigDecimal amount,
                                                                   LocalDate on) {
        if (noticeDays == 0) {
            return Optional.empty();
        }
        BigDecimal readyToday = readyToTakeOn(noticeDays, standing, on);
        if (readyToday.compareTo(amount) >= 0) {
            return Optional.empty();
        }
        String opening = "That savings account asks for "
                + WhenANoticeIsReady.daysInWords(noticeDays) + "' notice. EUR "
                + asMoney(readyToday) + " of it is ready to take today, and you asked for EUR "
                + asMoney(amount) + ". ";
        String reason = standing.stream()
                .filter(given -> !WhenANoticeIsReady.readyOn(given.givenOn(), noticeDays, on))
                .min(Comparator.comparing(NoticeGiven::readyOn).thenComparing(NoticeGiven::id))
                .map(soonest -> opening + "The notice you gave on EUR "
                        + asMoney(soonest.stillStanding()) + " on " + soonest.givenOn() + " has "
                        + WhenANoticeIsReady.daysInWords(WhenANoticeIsReady.daysLeftOn(
                                soonest.givenOn(), noticeDays, on))
                        + " left to run, and is ready on " + soonest.readyOn() + ".")
                .orElseGet(() -> opening + "No notice is still running, so give notice on EUR "
                        + asMoney(amount.subtract(readyToday)) + " and it is yours on "
                        + WhenANoticeIsReady.readyOn(on, noticeDays) + ".");
        return Optional.of(
                new AConditionInTheWay(ConditionOnTheWayOut.NOTICE_THAT_HAS_NOT_RUN, reason));
    }

    /**
     * How much of what is standing has finished waiting by that day — the figure a withdrawal is
     * weighed against.
     *
     * <p>Added up rather than asked of the records' own {@code ready} flag, for the reason the class
     * javadoc gives: that flag is a reading taken on the day the records were made.
     */
    public static BigDecimal readyToTakeOn(int noticeDays, List<NoticeGiven> standing,
                                           LocalDate on) {
        return standing.stream()
                .filter(given -> WhenANoticeIsReady.readyOn(given.givenOn(), noticeDays, on))
                .map(NoticeGiven::stillStanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** How a stretch of days reads in a sentence: one day, or that many days. */
    private static String inDays(long days) {
        return days == 1 ? "1 day" : days + " days";
    }
}
