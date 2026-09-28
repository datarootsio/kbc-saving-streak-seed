package io.dataroots.savingstreak.scheme;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.dataroots.savingstreak.deposits.AmountOfMoney;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads what somebody typed into the figures a version of the scheme is made of, and refuses in
 * this module's own words whatever cannot be one.
 *
 * <p><strong>A class of its own rather than a method on the service</strong>, for the reason
 * {@link TheSchemeInForceOn} is one: it is a pure reading of one record and a day, it is the only
 * place the rules about what a version may say are written down, and those rules are exactly the
 * sort of thing that gets re-decided slightly differently the second time somebody needs them.
 * There is nothing to construct and nothing to inject. It is also what makes the calendar rules
 * testable at the days an API test would need a wound clock to reach.
 *
 * <p><strong>It refuses and logs here rather than answering with reasons.</strong>
 * {@link BasisPointsOfTheScheme} and {@code AmountOfMoney} both hand back a sentence instead of
 * throwing, because each of them is shared by callers who refuse differently; this class has one
 * caller and one refusal, so the sentence and the {@link SchemeRefused} it becomes are one step.
 * That also puts the WARN line exactly where the decision is made, with the figure that decided it,
 * rather than at a service that would have to restate what it was told.
 *
 * <p><strong>Every figure is judged, and the first objection wins.</strong> A form with three
 * mistakes in it comes back naming one of them, which is what every other refusal in this
 * application does. Collecting all three would read better on a screen and would be the only
 * refusal here shaped like a list, and a screen that showed one sentence for eleven boxes and a
 * list for one of them is a screen nobody can write a renderer for.
 *
 * <p><strong>Nothing is defaulted, nothing is rounded and nothing is carried over.</strong> An
 * absent figure is refused by name; a step quoted more finely than the column holds is refused
 * rather than rounded to the nearest ten-thousandth. Rounding would move a figure nobody typed, on
 * a scheme everybody the bank has is about to be living under, which is the failure this whole
 * feature exists to prevent.
 *
 * <p><strong>The two calendar rules are here and the one history rule is not.</strong> Whether the
 * day is a Monday and whether it is still to come are decided by the form and the day it arrived
 * on, so they are read here with everything else somebody typed. Whether it would strand a version
 * already announced is a question about the rows, so {@link SchemeService} asks it after this class
 * has said the form is a scheme — which is the same split {@code WhatAVersionMaySay} and
 * {@code ProductsService} draw between a figure and a history.
 */
final class WhatAVersionOfTheSchemeMaySay {

    private static final Logger log =
            LoggerFactory.getLogger(WhatAVersionOfTheSchemeMaySay.class);

    /**
     * The names the refusals use, which are the words on the form rather than the names of the
     * columns.
     *
     * <p>Written as sentence openings — "What a week asks for cannot be less than nothing" — so
     * that one phrase serves every objection a figure can draw, instead of a set of phrasings per
     * figure that drift into sounding like four different applications.
     */
    private static final String WHAT_A_WEEK_ASKS_FOR = "What a week asks for";
    private static final String THE_ORDINARY_RATE = "The ordinary rate";
    private static final String WHAT_EACH_FURTHER_WEEK_ADDS = "What each further week adds";
    private static final String THE_MOST_A_STREAK_PAYS = "The most a streak pays";
    private static final String HOW_LONG_POINTS_LAST = "How long a batch of points lasts, in months,";
    private static final String A_BALANCE_RUNG = "A balance rung";
    private static final String WHEN_A_BUDGET_IS_RUNNING_LOW =
            "The share of a budget at which it is running low";
    private static final String HOW_MANY_OUTSTANDING_IS_A_SPIRAL =
            "How many bills outstanding is arrears piling up";
    private static final String DAYS_BEFORE_A_MATURITY =
            "The days of warning before a maturity, in days,";
    private static final String DAYS_BEFORE_AN_ANNIVERSARY =
            "The days of warning before an anniversary, in days,";

    /** A hundred percent, which is the whole of a budget and the most this share may be. */
    private static final BigDecimal THE_WHOLE_OF_A_BUDGET = new BigDecimal("100");

    /** One percent, which is the least a share can be and still mean anything. */
    private static final BigDecimal THE_LEAST_A_SHARE_CAN_BE = BigDecimal.ONE;

    /** The largest amount a {@code long} of cents has room for, read off the column. */
    private static final BigDecimal THE_MOST_AN_AMOUNT_CAN_BE =
            BigDecimal.valueOf(Long.MAX_VALUE, 2);

    private WhatAVersionOfTheSchemeMaySay() {
    }

    /**
     * The next version of the scheme, built out of what was typed, or a refusal saying what cannot
     * be read as one.
     *
     * <p>The version number is this module's own — the caller counted the rows — so it cannot be
     * wrong here and it is not judged. Everything else came off a form.
     *
     * @param version one higher than the last the bank published, counted by the caller
     * @param today   the day this application thinks it is, so that "still to come" is measured
     *                against the clock a trainer winds rather than against the machine's
     * @param typed   what somebody running the bank filled in
     * @throws SchemeRefused when any part of it is not something a version of the scheme can say
     */
    static SchemeVersion readInto(int version, LocalDate today, ANewVersionOfTheScheme typed) {
        log.debug("a new version of the scheme is being read version={} today={} effectiveFrom={} "
                        + "weeklyThreshold={} theOrdinaryRate={} extraForEachFurtherWeek={} "
                        + "theMostAStreakPays={} howLongABatchOfPointsLasts={} balanceRungs={} "
                        + "whatShareOfABudgetIsRunningLow={} howManyOutstandingIsASpiral={} "
                        + "daysBeforeAMaturityIsWorthSaying={} "
                        + "daysBeforeAnAnniversaryIsWorthSaying={} whatChanged={}",
                version, today, typed.effectiveFrom(), typed.weeklyThreshold(),
                typed.theOrdinaryRate(), typed.extraForEachFurtherWeek(),
                typed.theMostAStreakPays(), typed.howLongABatchOfPointsLasts(),
                typed.balanceRungs(), typed.whatShareOfABudgetIsRunningLow(),
                typed.howManyOutstandingIsASpiral(), typed.daysBeforeAMaturityIsWorthSaying(),
                typed.daysBeforeAnAnniversaryIsWorthSaying(), typed.whatChanged());

        // Read in the order the form draws them, one local per figure that a later one is judged
        // against, because the first objection wins and "the first" should mean the first box an
        // administrator would look at rather than the first argument the compiler happened to
        // evaluate. The ordinary rate is hoisted because the cap is judged against it.
        LocalDate effectiveFrom = aMondayStillToCome(today, typed.effectiveFrom());
        String whatChanged = aLineSayingWhatChanged(typed.whatChanged());
        long weeklyThresholdCents = whatAWeekAsksFor(typed.weeklyThreshold());
        int theOrdinaryRate = theOrdinaryRate(typed.theOrdinaryRate());

        return SchemeVersion.published(
                version,
                effectiveFrom,
                weeklyThresholdCents,
                theOrdinaryRate,
                whatEachFurtherWeekAdds(typed.extraForEachFurtherWeek()),
                theMostAStreakPays(typed.theMostAStreakPays(), theOrdinaryRate),
                aCountOfAtLeastOne(HOW_LONG_POINTS_LAST, typed.howLongABatchOfPointsLasts(),
                        "Points that lasted no months at all would expire the moment they were "
                                + "earned."),
                theRungsABalanceClimbs(typed.balanceRungs()),
                whenABudgetIsRunningLow(typed.whatShareOfABudgetIsRunningLow()),
                aCountOfAtLeastOne(HOW_MANY_OUTSTANDING_IS_A_SPIRAL,
                        typed.howManyOutstandingIsASpiral(),
                        "Nought bills outstanding would be arrears piling up for everybody who owes "
                                + "nothing."),
                aCountOfAtLeastOne(DAYS_BEFORE_A_MATURITY, typed.daysBeforeAMaturityIsWorthSaying(),
                        "Nought days of warning is a warning that arrives on the morning of the "
                                + "thing it warns about."),
                aCountOfAtLeastOne(DAYS_BEFORE_AN_ANNIVERSARY,
                        typed.daysBeforeAnAnniversaryIsWorthSaying(),
                        "Nought days of warning is a warning that arrives on the morning of the "
                                + "thing it warns about."),
                whatChanged);
    }

    /**
     * The day it takes effect, which has to be a Monday and has to be still to come.
     *
     * <p><strong>The rule this ticket is really about, and the one place the scheme refuses
     * something a product happily accepts.</strong> {@code ANewVersionOfTheTerms} says its day "may
     * be in the past, today, or ahead of today", and both halves of that are wrong here.
     *
     * <p>A Monday, because a savings week runs Monday to Sunday and is judged by the scheme in
     * force on its own Monday. A version taking effect on a Wednesday would leave one week half at
     * one threshold and half at another — not a week anybody can be told the rules of before it
     * starts, and not a week this application could report a single verdict on.
     *
     * <p>Still to come, because the scheme is dated rather than pinned. An account is written under
     * the version of a product's terms it was opened with, so a backdated version of those terms
     * changes nothing that was already decided; nobody is pinned to a version of the scheme, and a
     * customer's run of weeks is re-derived from the whole ledger every time anybody reads it. A
     * version dated yesterday would therefore re-judge weeks that have already been counted — every
     * EUR 60 week somebody secured would un-secure itself the morning the minimum rose, their
     * current run would shorten, their best-ever run would shorten, and nothing would log it. That
     * is the failure this feature exists to remove, so it is refused at the door rather than
     * mitigated behind it.
     *
     * <p>Today is refused with the past rather than allowed with the future. A version taking
     * effect this morning would land in the middle of a week that is already running, which is the
     * Wednesday problem wearing a Monday's clothes on six days out of seven — and on the seventh it
     * would re-judge the week that started at midnight and may already have had deposits counted
     * against it.
     *
     * <p>The sentence names the earliest day that would have been accepted, so that the fix is one
     * edit rather than a calendar exercise.
     */
    private static LocalDate aMondayStillToCome(LocalDate today, LocalDate effectiveFrom) {
        if (effectiveFrom == null) {
            throw refuse(SchemeRefused.Kind.A_VERSION_WITH_NO_DAY_IT_TAKES_EFFECT,
                    "A new version of the scheme needs the Monday it takes effect. Write it as a "
                            + "date, like " + theNextMondayAfter(today) + " — it has to be a "
                            + "Monday, and it has to be still to come.");
        }
        if (effectiveFrom.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw refuse(SchemeRefused.Kind.A_MONDAY_A_VERSION_CANNOT_TAKE_EFFECT_ON,
                    "The scheme can only change on a Monday, and " + effectiveFrom + " is a "
                            + aDayNamed(effectiveFrom) + ". A savings week runs from Monday to "
                            + "Sunday, so a version starting mid-week would judge one week under "
                            + "two schemes. The next Monday still to come is "
                            + theNextMondayAfter(today) + ".");
        }
        if (!effectiveFrom.isAfter(today)) {
            throw refuse(SchemeRefused.Kind.A_MONDAY_A_VERSION_CANNOT_TAKE_EFFECT_ON,
                    "The scheme can only change on a Monday still to come, and " + effectiveFrom
                            + " is not: today is " + today + ". A week already judged cannot be "
                            + "re-judged, so a version is never backdated. The earliest Monday it "
                            + "can take effect on is " + theNextMondayAfter(today) + ".");
        }
        return effectiveFrom;
    }

    /** The first Monday strictly after that day, which is the earliest a version may start. */
    private static LocalDate theNextMondayAfter(LocalDate today) {
        return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    /** The day of the week in the words a sentence wants — "Wednesday" rather than "WEDNESDAY". */
    private static String aDayNamed(LocalDate day) {
        String shouted = day.getDayOfWeek().name();
        return shouted.charAt(0) + shouted.substring(1).toLowerCase();
    }

    /**
     * The line saying what changed, which every version of the scheme owes the customer.
     *
     * <p>Required on the first version as well as on every one after it, which is where the scheme
     * parts company with a product's terms. Nobody opted into the scheme, so there is no version of
     * it that a customer chose and can be assumed to already understand. The seed writes its own
     * line, so this rule has no first version to make an exception of.
     */
    private static String aLineSayingWhatChanged(String whatChanged) {
        if (whatChanged == null || whatChanged.isBlank()) {
            throw refuse(SchemeRefused.Kind.A_VERSION_THAT_DOES_NOT_SAY_WHAT_CHANGED,
                    "A new version of the scheme has to say what changed and why, in one line, "
                            + "because it applies to everybody from its Monday and a customer whose "
                            + "rate moves will come looking for the reason.");
        }
        return whatChanged.trim();
    }

    /**
     * What a week has to take in to secure itself, as the cents a row holds.
     *
     * <p>Quoted through {@code AmountOfMoney} rather than checked here, so that "an amount of money
     * has at most two decimal places" is the sentence a customer typing into a deposit box already
     * gets. How finely a euro is written down is a decision this application made once, and a
     * second copy of it in the scheme module is a second copy that can be changed on its own.
     *
     * <p>Nought is refused, where a product's floor accepts it. A floor of nought is an agreement
     * with no floor to keep; a week that asks for nothing is not a week — every week would secure
     * itself, every customer would walk the whole ladder without saving a cent, and the scheme
     * would pay its best rate for doing nothing at all.
     */
    private static long whatAWeekAsksFor(BigDecimal euros) {
        if (euros == null) {
            throw nothingWasSent(WHAT_A_WEEK_ASKS_FOR);
        }
        if (euros.signum() <= 0) {
            throw refuse(SchemeRefused.Kind.A_FIGURE_THE_SCHEME_CANNOT_CARRY,
                    WHAT_A_WEEK_ASKS_FOR + " has to be more than nothing, and "
                            + euros.toPlainString() + " is not. A week that asks for nothing is a "
                            + "week everybody secures without saving anything.");
        }
        return asCents(WHAT_A_WEEK_ASKS_FOR, euros);
    }

    /**
     * The rate the first week of a run pays, which is the one multiple that can never be nought.
     *
     * <p>Nought <em>times</em> is not an offer: it is a deposit that silently earns no points
     * however much is saved, and {@code 1.0000} is what "changes nothing" spells for a factor. That
     * is the reading the products module's points multiplier already makes of the same unit, and it
     * is the same reading for the same reason.
     */
    private static int theOrdinaryRate(BigDecimal multiple) {
        int basisPoints = aMultiple(THE_ORDINARY_RATE, multiple);
        if (basisPoints == 0) {
            throw refuse(SchemeRefused.Kind.A_FIGURE_THE_SCHEME_CANNOT_CARRY,
                    THE_ORDINARY_RATE + " has to be more than nothing — 1.0000 is the rate that "
                            + "changes nothing — and " + multiple.toPlainString() + " is not. A "
                            + "scheme whose first week pays nothing pays nothing for every week "
                            + "after it too.");
        }
        return basisPoints;
    }

    /**
     * What each further week of a run adds, which is the one figure on this form that may be
     * nought.
     *
     * <p>A flat ladder is a scheme: a bank that pays the same for one week as for twenty has made a
     * decision and can write a line explaining it. What cannot be published is a ladder that
     * descends, and a step below nothing is the plainest way to build one — a run of weeks that
     * paid less the longer it ran would be a reward for stopping.
     */
    private static int whatEachFurtherWeekAdds(BigDecimal multiple) {
        if (multiple != null && multiple.signum() < 0) {
            throw refuse(SchemeRefused.Kind.A_LADDER_THAT_DESCENDS,
                    WHAT_EACH_FURTHER_WEEK_ADDS + " cannot be less than nothing, and "
                            + multiple.toPlainString() + " is. A ladder that descends would pay a "
                            + "customer less the longer they kept saving. Write 0.0000 for a "
                            + "scheme that pays the same every week.");
        }
        return aMultiple(WHAT_EACH_FURTHER_WEEK_ADDS, multiple);
    }

    /**
     * Where the ladder stops climbing, which can never be below where it starts.
     *
     * <p>Equal to the ordinary rate is allowed and is the same flat scheme a step of nought makes:
     * the ladder is still a ladder, it just has one rung. Below it is not a cap at all — it is a
     * first week that pays more than the scheme will ever pay again, which is the ladder descending
     * at its first step.
     *
     * <p>The sentence names both figures, because the fix is a choice between them.
     */
    private static int theMostAStreakPays(BigDecimal multiple, int theOrdinaryRate) {
        int basisPoints = aMultiple(THE_MOST_A_STREAK_PAYS, multiple);
        if (basisPoints < theOrdinaryRate) {
            throw refuse(SchemeRefused.Kind.A_LADDER_THAT_DESCENDS,
                    THE_MOST_A_STREAK_PAYS + " is " + multiple.toPlainString() + ", which is less "
                            + "than the ordinary rate of "
                            + BasisPointsOfTheScheme.asAMultiple(theOrdinaryRate).toPlainString()
                            + ". A cap below the rate a first week is paid at is a ladder that "
                            + "descends at its first step.");
        }
        return basisPoints;
    }

    /**
     * The rungs a balance climbs, as the ascending cents the collection holds.
     *
     * <p><strong>Three objections, and all three are about the list being a ladder.</strong> No
     * rungs at all is not an empty ladder but a scheme in which nobody is ever congratulated on
     * anything, and it is far likelier to be a form that lost its list than a decision somebody
     * made. Rungs that do not strictly ascend are rungs in the wrong places: two at one figure
     * congratulate somebody twice for arriving once, and one below the one before it can never be
     * climbed past. A rung that is not a whole euro is a threshold nobody would write on purpose —
     * "you have reached EUR 499.99" is a sentence no bank sends — and the whole-euro rule is what
     * keeps the six seeded rungs readable as the round figures they are.
     *
     * <p>Nought and below are refused with the other rungs rather than as amounts, because a rung
     * at nought is one every customer has already climbed and a rung below nought is one nobody can
     * fail to have.
     */
    private static List<Long> theRungsABalanceClimbs(List<BigDecimal> rungs) {
        if (rungs == null || rungs.isEmpty()) {
            throw refuse(SchemeRefused.Kind.RUNGS_THAT_ARE_NOT_A_LADDER,
                    "A new version of the scheme has to say at least one balance rung, because a "
                            + "scheme with no rungs congratulates nobody on anything. Write them in "
                            + "whole euros, ascending, like 100, 500 and 1000.");
        }
        List<Long> cents = new ArrayList<>(rungs.size());
        BigDecimal theOneBefore = null;
        for (BigDecimal rung : rungs) {
            if (rung == null) {
                throw nothingWasSent(A_BALANCE_RUNG);
            }
            if (rung.signum() <= 0) {
                throw refuse(SchemeRefused.Kind.RUNGS_THAT_ARE_NOT_A_LADDER,
                        A_BALANCE_RUNG + " has to be more than nothing, and " + rung.toPlainString()
                                + " is not. A rung at nought is one every customer has already "
                                + "reached.");
            }
            if (rung.stripTrailingZeros().scale() > 0) {
                throw refuse(SchemeRefused.Kind.RUNGS_THAT_ARE_NOT_A_LADDER,
                        A_BALANCE_RUNG + " has to be a whole number of euros, and "
                                + rung.toPlainString() + " is not. Write it like 500, because these "
                                + "are the figures a customer is congratulated on reaching.");
            }
            if (theOneBefore != null && rung.compareTo(theOneBefore) <= 0) {
                throw refuse(SchemeRefused.Kind.RUNGS_THAT_ARE_NOT_A_LADDER,
                        "The balance rungs have to climb, and " + rung.toPlainString()
                                + " comes after " + theOneBefore.toPlainString()
                                + ". Write them strictly ascending, each one higher than the last.");
            }
            cents.add(asCents(A_BALANCE_RUNG, rung));
            theOneBefore = rung;
        }
        return cents;
    }

    /**
     * The share of a budget at which it is said to be running low, as the basis points a row holds.
     *
     * <p>A percentage between one and a hundred, and both edges are the rule rather than the
     * column. Nought percent would warn everybody with a budget the moment they opened it, before
     * they had spent anything; more than a hundred percent is a line nobody can cross, so the
     * warning would never be sent at all and nothing would say why. A hundred exactly is allowed
     * and is a bank that warns you when the budget is gone rather than before, which is a
     * defensible if unhelpful policy and is somebody's to make.
     */
    private static int whenABudgetIsRunningLow(BigDecimal percentage) {
        if (percentage == null) {
            throw nothingWasSent(WHEN_A_BUDGET_IS_RUNNING_LOW);
        }
        theUnitsObjection(WHEN_A_BUDGET_IS_RUNNING_LOW, () ->
                BasisPointsOfTheScheme.whyItIsNotAPercentageThatCanBeHeld(
                        WHEN_A_BUDGET_IS_RUNNING_LOW, percentage));
        if (percentage.compareTo(THE_LEAST_A_SHARE_CAN_BE) < 0
                || percentage.compareTo(THE_WHOLE_OF_A_BUDGET) > 0) {
            throw refuse(SchemeRefused.Kind.A_FIGURE_THE_SCHEME_CANNOT_CARRY,
                    WHEN_A_BUDGET_IS_RUNNING_LOW + " has to be between 1% and 100%, and "
                            + percentage.toPlainString() + "% is not. Write it as a percentage of "
                            + "the budget, like 80.00 for four fifths.");
        }
        return BasisPointsOfTheScheme.ofAPercentage(percentage);
    }

    /**
     * A count of months, bills or days, which is a whole thing and can never be less than one.
     *
     * <p><strong>At least one, where a product's counts accept nought, and the difference is that
     * nought is not an absence here.</strong> A product reads nought as no notice to give and no
     * term to serve — the rule simply does not apply. Every count the scheme carries applies to
     * everybody all the time: points last for some number of months, arrears start at some number
     * of bills, and a warning is sent some number of days out. Nought in any of them is not the
     * rule being switched off but the rule being made absurd, so the sentence says what nought
     * would actually mean rather than just refusing the number.
     */
    private static int aCountOfAtLeastOne(String which, Integer count, String whatNoughtWouldMean) {
        if (count == null) {
            throw nothingWasSent(which);
        }
        if (count < 1) {
            throw refuse(SchemeRefused.Kind.A_FIGURE_THE_SCHEME_CANNOT_CARRY,
                    which + " cannot be less than one, and " + count + " is. "
                            + whatNoughtWouldMean);
        }
        return count;
    }

    /**
     * One multiple read through the unit that holds it: absent is refused by name, the unit is
     * asked for its sentence, and only then is the figure converted.
     *
     * <p>The conversion happens last rather than first, because {@code intValueExact} on a multiple
     * nobody has checked is how a refusal turns into a stack trace.
     */
    private static int aMultiple(String which, BigDecimal multiple) {
        if (multiple == null) {
            throw nothingWasSent(which);
        }
        theUnitsObjection(which, () ->
                BasisPointsOfTheScheme.whyItIsNotAMultipleThatCanBeHeld(which, multiple));
        return BasisPointsOfTheScheme.ofAMultiple(multiple);
    }

    /**
     * An amount as the cents a column holds, judged as money rather than as a number.
     *
     * <p>{@code AmountOfMoney}'s own sentence about decimal places, so that the words somebody gets
     * for typing "50.005" into the weekly threshold are the words they get for typing it into a
     * deposit box. The ceiling is the edge of the column and not a view about how much a bank may
     * ask somebody to save in a week.
     */
    private static long asCents(String which, BigDecimal euros) {
        AmountOfMoney.whyItIsNotQuotedToTheCent(euros).ifPresent(reason -> {
            throw refuse(SchemeRefused.Kind.A_FIGURE_THE_SCHEME_CANNOT_CARRY, which + ": " + reason);
        });
        if (euros.compareTo(THE_MOST_AN_AMOUNT_CAN_BE) > 0) {
            throw refuse(SchemeRefused.Kind.A_FIGURE_THE_SCHEME_CANNOT_CARRY,
                    which + " of " + AmountOfMoney.asMoney(euros)
                            + " is more than this application can write down.");
        }
        return euros.movePointRight(2).longValueExact();
    }

    /** Whatever the unit has to say about a figure, turned into this module's refusal. */
    private static void theUnitsObjection(String which, Supplier<Optional<String>> whyNot) {
        whyNot.get().ifPresent(reason -> {
            throw refuse(SchemeRefused.Kind.A_FIGURE_THE_SCHEME_CANNOT_CARRY, reason);
        });
    }

    /**
     * The refusal for a box nobody filled in, in one wording for all eleven figures — because
     * eleven wordings of one objection is how an application starts sounding like eleven.
     *
     * <p>The sentence says the thing an administrator actually needs to know, which is not that the
     * box is empty but that emptiness is not a shortcut: a version of the scheme carries nothing
     * over from the version before it, and there is no figure here where nought is the rule being
     * left out.
     */
    private static SchemeRefused nothingWasSent(String which) {
        return refuse(SchemeRefused.Kind.A_FIGURE_THE_SCHEME_CANNOT_CARRY,
                which + " was not sent. A version of the scheme says every figure outright and "
                        + "carries nothing over from the version before it, and there is no figure "
                        + "here that nought would leave out.");
    }

    /**
     * Says no, in words, and leaves a line in the log saying the same words.
     *
     * <p>The sentence is logged rather than a summary of it, so that what the log says and what the
     * administrator was told are the same thing.
     */
    private static SchemeRefused refuse(SchemeRefused.Kind kind, String reason) {
        log.warn("a new version of the scheme was refused kind={} reason={}", kind, reason);
        return new SchemeRefused(kind, reason);
    }
}
