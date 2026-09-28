package io.dataroots.savingstreak.schemepreview;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.Customer;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.WithdrawalsService;
import io.dataroots.savingstreak.notifications.HowManyStandOnTheFarSideOfALine;
import io.dataroots.savingstreak.notifications.NotificationsService;
import io.dataroots.savingstreak.points.PointsExpiry;
import io.dataroots.savingstreak.scheme.ANewVersionOfTheScheme;
import io.dataroots.savingstreak.scheme.SchemeRefused;
import io.dataroots.savingstreak.scheme.SchemeService;
import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;
import io.dataroots.savingstreak.scheme.TheSchemeEachWeekWasJudgedUnder;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.streaks.WeekAndStreak;
import io.dataroots.savingstreak.streaks.WeekAndStreakDerivation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a candidate version of the scheme would do, answered without publishing it and without
 * writing anything at all.
 *
 * <p><strong>It answers a counterfactual, because the honest answer to the obvious question is
 * "nothing".</strong> A version of the scheme is never retroactive — a week is judged by the
 * version in force on its own Monday and no week has yet been judged under a candidate — so
 * "what changes on the effective date" is answered by an empty list every single time, for every
 * candidate, however violent. The question worth asking before publishing is therefore:
 *
 * <blockquote>If this had been the rule for the last twenty-six weeks, here is what every
 * customer's run and rate would read today.</blockquote>
 *
 * <p>Twenty-six weeks fills the six-rung multiplier ladder four times over, which is long enough
 * that a steady saver is at the cap under both readings and a lapsed one is visibly not — and short
 * enough to walk for every customer inside one request. The response says all of this about itself,
 * in {@link WhatThisSchemeWouldDo#whatThisIs}, rather than relying on a screen to caption it.
 *
 * <p><strong>Recomputed, not replayed, and the difference is the design.</strong>
 * {@link WeekAndStreakDerivation} is already a pure function over the ledger that reads no clock and
 * takes the scheme's history as an argument. So the whole answer is: run it twice per customer —
 * once against the history the bank actually published, once against a history whose last
 * twenty-six weeks are the candidate — and subtract. Nothing is simulated, nothing is stepped
 * through a night at a time, and the figures the preview shows are computed by exactly the code
 * that computes the figures on the customer's own screen. A preview computed any other way would be
 * a second implementation of the derivation, which is the one bug this feature exists to remove.
 *
 * <p><strong>{@code simulation/TheNightReplayed} is deliberately not reused, and this is the
 * paragraph that says so.</strong> It is single-account, forward-looking and scenario-shaped: it
 * asks what happens to one savings account if its holder does something differently from tomorrow.
 * This asks what every customer's past would have read like under a different rule. Those are
 * opposite shapes — one account against all of them, forward against backward, a customer's choice
 * against the bank's — and forcing one through the other would have gained nothing but a dependency
 * from the administration path into the what-if simulator, which then has to keep working for two
 * unrelated reasons for ever.
 *
 * <p><strong>A module of its own, above the ones it reads.</strong> It could not live in
 * {@code scheme}: that module depends on nothing in this application, which is exactly what lets
 * six modules be priced from it without a cycle, and a preview has to read the ledger, the accounts
 * and the notification rules. It could not live in {@code streaks} or {@code notifications} either
 * — it is about both and belongs to neither. So it sits above all of them and, like every other
 * edge in this application, only reads: none of the modules below learns that a preview exists.
 *
 * <p><strong>It writes nothing, and that is the load-bearing promise.</strong> No version row, no
 * notification, no expiry, no point, no movement. The transaction is read-only so that an
 * accidental flush would be refused rather than performed, every service it calls is called on a
 * reading method, and {@link SchemeService#theVersionAPublishWouldWrite} makes the candidate as an
 * entity and drops it. A preview that wrote would be a preview nobody could afford to run, and the
 * suite proves this one does not by reading the whole application back afterwards.
 *
 * <p><strong>A candidate that would be refused on publish is refused here, in the same
 * words.</strong> Not re-checked here: the same reading, over the same rows, through the same
 * method the publishing door calls. Two doors that disagreed about what is sayable would teach an
 * administrator the rule twice, and the second time is the time it costs somebody.
 */
@Service
public class SchemePreviewService {

    private static final Logger log = LoggerFactory.getLogger(SchemePreviewService.class);

    /**
     * How far back the counterfactual reaches, counting the week running now as the last of them.
     *
     * <p>Twenty-six because the ladder it is mostly about is six rungs long: a stretch four times
     * the height of the ladder cannot flatter a customer who has only just started, and cannot
     * punish one who lapsed last spring. It is also the figure the spec argues for by name, and it
     * is deliberately not a figure the scheme publishes — how far a preview looks back is a
     * property of the tool, not a term the bank offers anybody.
     */
    private static final int HOW_MANY_WEEKS_THE_COUNTERFACTUAL_REACHES_BACK = 26;

    /** Worst first means the largest fall first, so a bigger fall sorts before a smaller one. */
    private static final Comparator<ACustomerWhoseRunWouldRead> WORST_FIRST =
            Comparator.comparingInt(ACustomerWhoseRunWouldRead::theFallInWeeks).reversed()
                    .thenComparing(Comparator.comparing(
                            ACustomerWhoseRunWouldRead::theFallInRate).reversed())
                    .thenComparingInt(who -> who.bestRunWouldRead() - who.bestRunNow())
                    .thenComparingLong(ACustomerWhoseRunWouldRead::customerId);

    /**
     * How many of the worst-affected are named.
     *
     * <p>Twenty because the list is meant to be read, and a list nobody reads to the end is a list
     * that hid its twenty-first row. The roll-up beside it already carries the size of the whole
     * population, so the cap loses a count from nothing.
     */
    private static final int HOW_MANY_ARE_NAMED = 20;

    private final SchemeService scheme;
    private final AccountsService accounts;
    private final DepositsService deposits;
    private final WithdrawalsService withdrawals;
    private final NotificationsService notifications;
    private final Clock clock;

    SchemePreviewService(SchemeService scheme, AccountsService accounts, DepositsService deposits,
                         WithdrawalsService withdrawals, NotificationsService notifications,
                         Clock clock) {
        this.scheme = scheme;
        this.accounts = accounts;
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Reads the candidate, refuses it exactly as a publish would, and answers with everything
     * publishing it would do.
     *
     * <p>One moment is read off the clock and handed to every part of the answer. Two reads of the
     * clock inside one preview could straddle midnight, and a report in which the runs were counted
     * on Sunday and the notifications on Monday would be a report nobody could reproduce — on the
     * one boundary that matters most here, which is the turn of a Monday.
     *
     * <p>One read of the published history, likewise, for the reason
     * {@link SchemeService#theSchemeThroughItsVersions} gives: the same history is handed to both
     * derivations, so the only difference between the two readings is the candidate.
     *
     * @throws SchemeRefused in the same words {@link SchemeService#publishTheNextVersion} would use
     */
    @Transactional(readOnly = true)
    public WhatThisSchemeWouldDo whatThisSchemeWouldDo(ANewVersionOfTheScheme candidate) {
        Instant now = clock.instant();
        TheSchemeAsPublished inForce = scheme.theSchemeInForce();
        TheSchemeAsPublished itWouldBecome = scheme.theVersionAPublishWouldWrite(candidate);
        TheSchemeEachWeekWasJudgedUnder asPublished = scheme.theSchemeThroughItsVersions();
        LocalDate since = theMondayTheCounterfactualStartsOn(now);
        TheSchemeEachWeekWasJudgedUnder asIfItHadBeenTheRule =
                theHistoryTheCandidateWouldHaveMade(asPublished, itWouldBecome, since);

        List<ACustomerWhoseRunWouldRead> everybody =
                howEveryCustomersRunWouldRead(now, asPublished, asIfItHadBeenTheRule);
        List<ACustomerWhoseRunWouldRead> whoMoved = everybody.stream()
                .filter(SchemePreviewService::itReadsDifferently)
                .sorted(WORST_FIRST)
                .toList();
        HowManyRunsWouldRead runs = theSizeOfIt(everybody, whoMoved);
        List<HowManyStandOnTheFarSideOfALine> lines =
                notifications.whoWouldStandOnTheFarSideOfEachLine(inForce, itWouldBecome, now);

        WhatThisSchemeWouldDo previewed = new WhatThisSchemeWouldDo(
                whatThisIs(since),
                HOW_MANY_WEEKS_THE_COUNTERFACTUAL_REACHES_BACK,
                since,
                inForce,
                itWouldBecome,
                theFigureByFigureDifference(inForce, itWouldBecome),
                runs,
                whoMoved.stream().limit(HOW_MANY_ARE_NAMED).toList(),
                whatWouldHappenToPoints(inForce, itWouldBecome),
                lines);

        // One INFO line per preview, carrying the counts it produced and not one customer's name.
        // This is the business event — somebody asked what a repricing would do — and the counts are
        // the whole of what a reviewer needs to see that the question was asked and what the answer
        // was. The named list is deliberately absent: a log that carried it would be a report of
        // every customer's run, written nightly to a file nobody has decided who may read, produced
        // by a screen anybody can reach.
        log.info("a candidate scheme was previewed versionItWouldBecome={} effectiveFrom={} "
                        + "asIfItHadBeenTheRuleSince={} overHowManyWeeks={} customersExamined={} "
                        + "runsThatWouldReadDifferently={} whoWouldGain={} whoWouldLose={} "
                        + "whoAreUntouched={} theLargestFallInWeeks={} theLargestFallInRate={} "
                        + "figuresThatWouldChange={} named={} itWouldChangeNothing={}",
                itWouldBecome.version(), itWouldBecome.effectiveFrom(), since,
                HOW_MANY_WEEKS_THE_COUNTERFACTUAL_REACHES_BACK, runs.customersExamined(),
                runs.runsThatWouldReadDifferently(), runs.whoWouldGain(), runs.whoWouldLose(),
                runs.whoAreUntouched(), runs.theLargestFallInWeeks(), runs.theLargestFallInRate(),
                previewed.figures().stream().filter(AFigureAsItWouldRead::itWouldChange).count(),
                previewed.theWorstAffected().size(), previewed.itWouldChangeNothing());
        for (HowManyStandOnTheFarSideOfALine line : lines) {
            log.info("a line the scheme draws would move people line={} wouldBeToldAndIsNot={} "
                            + "isToldAndWouldNotBe={}",
                    line.line(), line.wouldBeToldAndIsNot(), line.isToldAndWouldNotBe());
        }
        return previewed;
    }

    /**
     * The Monday the counterfactual starts on: the one twenty-six weeks back, counting the week
     * running now as the last of the twenty-six.
     *
     * <p>A Monday and not a date twenty-six weeks ago, because a version of the scheme may only
     * take effect on a Monday and a counterfactual history that broke that rule would judge one
     * week under two schemes — the exact thing the Monday rule exists to prevent. Read off
     * {@link SavingsWeek}, so that the preview counts its weeks in the zone the rest of the
     * application counts them in.
     */
    private LocalDate theMondayTheCounterfactualStartsOn(Instant now) {
        return SavingsWeek.containing(now).startsOn()
                .minusWeeks(HOW_MANY_WEEKS_THE_COUNTERFACTUAL_REACHES_BACK - 1L);
    }

    /**
     * The history the bank would have had if the candidate had been published twenty-six weeks ago:
     * everything actually published, with the candidate added at the back, dated to that Monday.
     *
     * <p><strong>Added rather than substituted, and that is what makes it a history and not a set of
     * figures.</strong> A week older than the counterfactual still finds the version that actually
     * judged it, because the candidate carries the highest version number and
     * {@code TheSchemeInForceOn} takes the highest version <em>whose day has come</em> — so before
     * that Monday it has not started and the real history answers, and from it the candidate wins.
     * Nothing about the derivation changes; it is handed a history exactly like any other and does
     * not know that one of the rows never existed.
     *
     * <p>It also, correctly, buries any version the bank has announced for a Monday inside the
     * counterfactual: had the candidate been the rule since then, it would have been the rule since
     * then.
     */
    private TheSchemeEachWeekWasJudgedUnder theHistoryTheCandidateWouldHaveMade(
            TheSchemeEachWeekWasJudgedUnder asPublished, TheSchemeAsPublished itWouldBecome,
            LocalDate since) {
        List<TheSchemeAsPublished> asIf =
                new ArrayList<>(asPublished.everyVersionPublished());
        asIf.add(new TheSchemeAsPublished(
                itWouldBecome.version(),
                since,
                itWouldBecome.weeklyThreshold(),
                itWouldBecome.theOrdinaryRate(),
                itWouldBecome.extraForEachFurtherWeek(),
                itWouldBecome.theMostAStreakPays(),
                itWouldBecome.howLongABatchOfPointsLasts(),
                itWouldBecome.balanceRungs(),
                itWouldBecome.whatShareOfABudgetIsRunningLow(),
                itWouldBecome.howManyOutstandingIsASpiral(),
                itWouldBecome.daysBeforeAMaturityIsWorthSaying(),
                itWouldBecome.daysBeforeAnAnniversaryIsWorthSaying(),
                itWouldBecome.whatChanged()));
        log.debug("the history a candidate scheme would have made asIfItHadBeenTheRuleSince={} "
                        + "versionsActuallyPublished={} candidateVersion={} weeklyThreshold={}",
                since, asPublished.everyVersionPublished().size(), itWouldBecome.version(),
                itWouldBecome.weeklyThreshold());
        return new TheSchemeEachWeekWasJudgedUnder(asIf);
    }

    /**
     * Both readings of every customer's run: the one the bank shows them today, and the one they
     * would be shown had the candidate been the rule.
     *
     * <p>Every customer, not every savings account. A run of weeks is a property of the person —
     * the derivation counts what they put away across everything they hold — so an account is not
     * the unit, and counting accounts would report a customer with two of them twice.
     */
    private List<ACustomerWhoseRunWouldRead> howEveryCustomersRunWouldRead(
            Instant now, TheSchemeEachWeekWasJudgedUnder asPublished,
            TheSchemeEachWeekWasJudgedUnder asIfItHadBeenTheRule) {
        List<ACustomerWhoseRunWouldRead> everybody = new ArrayList<>();
        for (Customer customer : accounts.customers()) {
            long customerId = customer.getId();
            WeekAndStreak asItReads = WeekAndStreakDerivation.asAt(
                    deposits, withdrawals, customerId, now, asPublished);
            WeekAndStreak asItWouldRead = WeekAndStreakDerivation.asAt(
                    deposits, withdrawals, customerId, now, asIfItHadBeenTheRule);
            BigDecimal rateNow = asItReads.streak().multiplier();
            BigDecimal rateWouldRead = asItWouldRead.streak().multiplier();
            everybody.add(new ACustomerWhoseRunWouldRead(
                    customerId,
                    customer.getName(),
                    asItReads.streak().currentWeeks(),
                    asItWouldRead.streak().currentWeeks(),
                    asItReads.streak().bestWeeks(),
                    asItWouldRead.streak().bestWeeks(),
                    rateNow,
                    rateWouldRead,
                    asItReads.streak().currentWeeks() - asItWouldRead.streak().currentWeeks(),
                    rateNow.subtract(rateWouldRead)));
        }
        return everybody;
    }

    /**
     * Whether any of the three figures a customer is shown reads differently.
     *
     * <p>All three, because they fail independently: a candidate that only moves the ladder leaves
     * every run the length it was and changes the rate on all of them, and a candidate that only
     * raises the weekly minimum can shorten a best-ever run that ended in March without touching a
     * run that has not started.
     */
    private static boolean itReadsDifferently(ACustomerWhoseRunWouldRead who) {
        return who.currentRunNow() != who.currentRunWouldRead()
                || who.bestRunNow() != who.bestRunWouldRead()
                || who.rateNow().compareTo(who.rateWouldRead()) != 0;
    }

    /**
     * The roll-up: how many moved, which way, and the worst single fall of each kind.
     *
     * <p>The direction is decided on the run first, then the rate, then the best-ever run, for the
     * reason {@link HowManyRunsWouldRead} argues: that is the customer's own order of noticing.
     */
    private HowManyRunsWouldRead theSizeOfIt(List<ACustomerWhoseRunWouldRead> everybody,
                                             List<ACustomerWhoseRunWouldRead> whoMoved) {
        int whoWouldLose = (int) whoMoved.stream().filter(this::itWouldCostThem).count();
        int theLargestFallInWeeks = whoMoved.stream()
                .mapToInt(ACustomerWhoseRunWouldRead::theFallInWeeks)
                .max()
                .orElse(0);
        BigDecimal theLargestFallInRate = whoMoved.stream()
                .map(ACustomerWhoseRunWouldRead::theFallInRate)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        return new HowManyRunsWouldRead(
                everybody.size(),
                whoMoved.size(),
                whoMoved.size() - whoWouldLose,
                whoWouldLose,
                everybody.size() - whoMoved.size(),
                // Never negative: a candidate under which everybody gains has no largest fall, and
                // reporting the smallest gain as a negative fall would be a figure read as a loss.
                Math.max(theLargestFallInWeeks, 0),
                theLargestFallInRate.max(BigDecimal.ZERO));
    }

    /** Whether the first of the three figures that differs differs downwards. */
    private boolean itWouldCostThem(ACustomerWhoseRunWouldRead who) {
        if (who.currentRunNow() != who.currentRunWouldRead()) {
            return who.currentRunWouldRead() < who.currentRunNow();
        }
        if (who.rateNow().compareTo(who.rateWouldRead()) != 0) {
            return who.rateWouldRead().compareTo(who.rateNow()) < 0;
        }
        return who.bestRunWouldRead() < who.bestRunNow();
    }

    /**
     * The mechanical difference between the two versions, figure by figure.
     *
     * <p><strong>Ten figures, and the three that are left out are left out on purpose.</strong> The
     * version number and the Monday it takes effect always differ — a new version is by definition a
     * new number on a new Monday — and a diff row that is true of every candidate ever previewed is
     * a row a reader learns to skip, taking the rows beside it with them. The line saying what
     * changed is prose rather than a figure and is on both versions in full. All three travel whole
     * on {@code theVersionInForce} and {@code theVersionThisWouldBecome}.
     *
     * <p>Named with the names the publishing form uses, so that "this row is the one I typed in that
     * box" needs no translation table, and spelled with the same {@code toPlainString} the form
     * spells its figures with.
     */
    private List<AFigureAsItWouldRead> theFigureByFigureDifference(TheSchemeAsPublished inForce,
                                                                   TheSchemeAsPublished candidate) {
        return List.of(
                AFigureAsItWouldRead.of("weeklyThreshold",
                        inForce.weeklyThreshold().toPlainString(),
                        candidate.weeklyThreshold().toPlainString()),
                AFigureAsItWouldRead.of("theOrdinaryRate",
                        inForce.theOrdinaryRate().toPlainString(),
                        candidate.theOrdinaryRate().toPlainString()),
                AFigureAsItWouldRead.of("extraForEachFurtherWeek",
                        inForce.extraForEachFurtherWeek().toPlainString(),
                        candidate.extraForEachFurtherWeek().toPlainString()),
                AFigureAsItWouldRead.of("theMostAStreakPays",
                        inForce.theMostAStreakPays().toPlainString(),
                        candidate.theMostAStreakPays().toPlainString()),
                AFigureAsItWouldRead.of("howLongABatchOfPointsLasts",
                        String.valueOf(inForce.howLongABatchOfPointsLasts()),
                        String.valueOf(candidate.howLongABatchOfPointsLasts())),
                AFigureAsItWouldRead.of("balanceRungs",
                        asALadder(inForce), asALadder(candidate)),
                AFigureAsItWouldRead.of("whatShareOfABudgetIsRunningLow",
                        inForce.whatShareOfABudgetIsRunningLow().toPlainString(),
                        candidate.whatShareOfABudgetIsRunningLow().toPlainString()),
                AFigureAsItWouldRead.of("howManyOutstandingIsASpiral",
                        String.valueOf(inForce.howManyOutstandingIsASpiral()),
                        String.valueOf(candidate.howManyOutstandingIsASpiral())),
                AFigureAsItWouldRead.of("daysBeforeAMaturityIsWorthSaying",
                        String.valueOf(inForce.daysBeforeAMaturityIsWorthSaying()),
                        String.valueOf(candidate.daysBeforeAMaturityIsWorthSaying())),
                AFigureAsItWouldRead.of("daysBeforeAnAnniversaryIsWorthSaying",
                        String.valueOf(inForce.daysBeforeAnAnniversaryIsWorthSaying()),
                        String.valueOf(candidate.daysBeforeAnAnniversaryIsWorthSaying())));
    }

    /** The whole ladder as one reading, because one rung moving is a change to the ladder. */
    private String asALadder(TheSchemeAsPublished scheme) {
        return scheme.balanceRungs().stream()
                .map(BigDecimal::toPlainString)
                .collect(Collectors.joining(", "));
    }

    /**
     * What the candidate does to points: nothing to anything already earned, and a new lifetime from
     * the Monday it takes effect.
     *
     * <p>The day a new batch would die is worked out by {@link PointsExpiry}, which is the function
     * the ledger stamps on a credit when it is earned. Adding months here would be a second place
     * the calendar is done, and the second place is always the one that gets February wrong.
     */
    private WhatWouldHappenToPoints whatWouldHappenToPoints(TheSchemeAsPublished inForce,
                                                            TheSchemeAsPublished candidate) {
        Instant earnedOnTheFirstMorning = candidate.effectiveFrom()
                .atStartOfDay(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toInstant();
        LocalDate wouldDieOn = PointsExpiry.dayOf(PointsExpiry.anniversaryOf(
                earnedOnTheFirstMorning, candidate.howLongABatchOfPointsLasts()));
        String saidPlainly = "No batch of points already earned changes its expiry: every batch "
                + "keeps the lifetime it was promised on the day it was earned, which is written "
                + "down on the batch itself and is never recomputed. A batch earned from "
                + candidate.effectiveFrom() + " would last "
                + candidate.howLongABatchOfPointsLasts() + " months instead of "
                + inForce.howLongABatchOfPointsLasts() + ", so one earned on that Monday would "
                + "expire on " + wouldDieOn + ".";
        return new WhatWouldHappenToPoints(true, inForce.howLongABatchOfPointsLasts(),
                candidate.howLongABatchOfPointsLasts(), wouldDieOn, saidPlainly);
    }

    /** The label the response carries about itself, with the Monday it reaches back to named. */
    private String whatThisIs(LocalDate since) {
        return "This is a counterfactual, not a prediction of the effective date. Nothing changes "
                + "on the day this version takes effect, because a week is judged by the scheme in "
                + "force on its own Monday and no week has yet been judged under this one. What "
                + "follows is the other question: if this had been the rule for the last "
                + HOW_MANY_WEEKS_THE_COUNTERFACTUAL_REACHES_BACK + " weeks, since " + since
                + ", here is what every customer's run and rate would read today.";
    }
}
