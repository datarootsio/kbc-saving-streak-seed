package io.dataroots.savingstreak.scheme;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only face this module has: what the scheme says today, what it has ever said, and which
 * version any given week was judged under.
 *
 * <p><strong>Everything about the scheme goes through here.</strong> The entity and its repository
 * are package-private, so a threshold is not a column anybody outside can read and a version is not
 * a row anybody outside can write. What leaves is {@link TheSchemeAsPublished} and
 * {@link TheSchemeEachWeekWasJudgedUnder}, which are values in the units the rest of this
 * application speaks — euros, multiples, percentages and counts, never basis points and never
 * cents.
 *
 * <p><strong>This module depends on nothing else in the application, and that direction is the
 * point.</strong> Streaks, Points and Notifications will depend on it; it depends on none of them.
 * It holds numbers and no rules: what a week asks for lives here, and whether a particular week
 * secured itself is argued where it has always been argued. A scheme module that knew how a streak
 * was counted would be a second place the streak is decided, which is exactly the shape of bug this
 * feature exists to remove.
 *
 * <p><strong>One method writes, and it only ever adds a row.</strong>
 * {@link #publishTheNextVersion} is the whole of what can be written through this class: version
 * <em>n+1</em>, numbered here and never by the caller, dated on a Monday still to come. There is no
 * door at all that edits or deletes a version — not as a check that could be loosened, but as a
 * method that was never written, which is the only enforceable form that promise has. Every other
 * method here is a read-only transaction, which is a statement about what it does rather than an
 * optimisation: a transaction that cannot write is one that cannot accidentally rewrite a scheme
 * somebody's week was judged under. The door that previews a candidate without writing one is a
 * later ticket.
 *
 * <p><strong>The scheme is read rather than cached.</strong> It is a handful of rows in SQLite, read
 * the way the products module reads its versions and for the same stated reason: a stored "current
 * version" is a second place the answer lives, and two stored figures that must agree eventually
 * stop agreeing. The one concession is {@link #theSchemeThroughItsVersions()}, which hands the whole
 * history out once so that a derivation walking twenty-six weeks backwards asks the database once
 * rather than twenty-six times.
 *
 * <p>The day is read off the application's own clock rather than {@code LocalDate.now()}, in the
 * zone this application counts its days in, so that a trainer who winds the clock across a
 * published Monday sees the version that would be in force then — which is the whole of how this
 * feature is demonstrated in a room.
 */
@Service
public class SchemeService {

    private static final Logger log = LoggerFactory.getLogger(SchemeService.class);

    private final SchemeVersionRepository versions;
    private final Clock clock;

    SchemeService(SchemeVersionRepository versions, Clock clock) {
        this.versions = versions;
        this.clock = clock;
    }

    /**
     * What the scheme says today: every figure, the Monday it started on, its number, and the line
     * saying what changed.
     *
     * <p>The version in force rather than the newest published, and the difference is the one this
     * whole module turns on: a version announced for a Monday still to come is published, readable
     * in the history, and deciding nothing. {@link TheSchemeInForceOn} is where that rule is
     * argued and this method does not repeat it.
     */
    @Transactional(readOnly = true)
    public TheSchemeAsPublished theSchemeInForce() {
        LocalDate today = today();
        TheSchemeAsPublished inForce = theSchemeThroughItsVersions().onTheDayOf(today);
        log.debug("the scheme in force was read on={} version={} effectiveFrom={} "
                        + "weeklyThreshold={} theOrdinaryRate={} extraForEachFurtherWeek={} "
                        + "theMostAStreakPays={} howLongABatchOfPointsLasts={}",
                today, inForce.version(), inForce.effectiveFrom(), inForce.weeklyThreshold(),
                inForce.theOrdinaryRate(), inForce.extraForEachFurtherWeek(),
                inForce.theMostAStreakPays(), inForce.howLongABatchOfPointsLasts());
        return inForce;
    }

    /**
     * Every version the bank has ever published, newest first, each with its Monday and its line
     * saying what changed.
     *
     * <p>All of them, including a version dated ahead of today. A history that hid what has been
     * announced for next month would hide exactly the thing somebody has come to this page for —
     * and user story 5 is that a change is something a customer is told about rather than something
     * they discover.
     *
     * <p>Newest first, because this is a page somebody scrolls to find out what changed last. The
     * products module serves its history oldest first, deliberately, because a product's history
     * reads as the story of one agreement from the beginning; the scheme's reads as a log of
     * repricings, and the one anybody is looking for is the most recent.
     */
    @Transactional(readOnly = true)
    public List<TheSchemeAsPublished> everyVersionPublished() {
        List<TheSchemeAsPublished> history = theSchemeThroughItsVersions().newestFirst();
        log.debug("the versions of the scheme were read versions={} newest={} oldest={}",
                history.size(),
                history.get(0).version(),
                history.get(history.size() - 1).version());
        return history;
    }

    /**
     * Which version of the scheme a given savings week was judged under.
     *
     * <p><strong>The load-bearing read, and the one the tickets after this one exist for.</strong>
     * A week is judged by the scheme in force on its own Monday, so raising the weekly minimum next
     * Monday leaves every week behind it standing exactly as it stood. The whole history is read
     * and the week is resolved against it, rather than a query per week, for the reason
     * {@link TheSchemeEachWeekWasJudgedUnder} gives.
     *
     * <p>Here as well as on the history value, because both callers are real: a rule asking about
     * one week — what does this week ask for — should not have to hold a history to ask, and a
     * derivation walking twenty-six of them should not ask the database twenty-six times. Both go
     * through the same function, so the two cannot disagree.
     */
    @Transactional(readOnly = true)
    public TheSchemeAsPublished theSchemeThatJudged(SavingsWeek week) {
        TheSchemeAsPublished judging = theSchemeThroughItsVersions().forTheWeekOf(week);
        log.debug("the scheme a week was judged under was read weekStartsOn={} version={} "
                        + "effectiveFrom={} weeklyThreshold={}",
                week.startsOn(), judging.version(), judging.effectiveFrom(),
                judging.weeklyThreshold());
        return judging;
    }

    /**
     * The whole published history in one value, for the derivations that span weeks.
     *
     * <p><strong>A history rather than a set of figures, and that is the shape of the
     * boundary.</strong> The streak derivation walks back through weeks and asks, for each week it
     * reaches, what that week's Monday asked for — so one set of figures could never be the right
     * argument to it. Handing it today's threshold is precisely the bug: every EUR 60 week a
     * customer secured would un-secure itself the morning the minimum rose.
     *
     * <p>One read of the database, handed down. That is the single concession this module makes to
     * reading the scheme rather than caching it, and it is a concession about consistency as much
     * as about cost: twenty-six reads inside one derivation could in principle straddle a publish,
     * and a run counted half under one history and half under another would be a run nobody could
     * reproduce.
     */
    @Transactional(readOnly = true)
    public TheSchemeEachWeekWasJudgedUnder theSchemeThroughItsVersions() {
        return new TheSchemeEachWeekWasJudgedUnder(versions.findAllByOrderByVersionAsc().stream()
                .map(SchemeVersion::asPublished)
                .toList());
    }

    /**
     * Publishes the next version of the scheme and answers with it: which version it became, and
     * whether its day has come.
     *
     * <p><strong>The number is this module's and never the caller's.</strong> One higher than the
     * last row, counted here, because a number sent from outside is a number two administrators can
     * send at once — and two rows claiming to be version 4 would be two answers to "what was my
     * week judged under". {@code SchemeVersionRepository}'s unique index is what catches the race
     * this counting cannot; the counting is what means nobody has to think about it.
     *
     * <p><strong>It adds a row and touches nothing.</strong> No version already published is read
     * for its figures, corrected or replaced; there is no "current version" flag to move, because
     * which version is in force is derived from the dates at read time. That is what makes this
     * method safe to call on a database people's weeks have already been judged against: the whole
     * of what it does is append.
     *
     * <p><strong>Three things are judged, in three places, and the split is deliberate.</strong>
     * Whether "0,50" is a number and whether "31/12/2026" is a day are facts about the form and are
     * decided in the web layer. Whether the figures are a scheme, and whether the day is a Monday
     * still to come, are decided by {@link WhatAVersionOfTheSchemeMaySay} — a pure reading of the
     * form and the clock. Only the last one is here, because only the last one is a question about
     * the rows: whether this Monday would strand a version somebody has already announced.
     *
     * <p><strong>The same Monday as an announced version is allowed, and that is how an
     * announcement is corrected.</strong> {@link TheSchemeInForceOn} takes the highest version
     * whose day has come, so publishing another version for a Monday already announced simply wins
     * — which is the only way to take back a mistake in a module with no edit door, and is exactly
     * what the products module does with two versions dated on one morning. An <em>earlier</em>
     * Monday is refused instead, because the higher number would take effect first and the version
     * announced for later would never come into force on any day at all: published, readable, paid
     * for by nobody, and silently dead. A module whose promise is that nothing is ever deleted
     * cannot let arithmetic delete something.
     *
     * @throws SchemeRefused when the form is not a scheme, the day is not a Monday still to come,
     *                       or the Monday would strand a version already announced
     */
    @Transactional
    public TheVersionThatNowExists publishTheNextVersion(ANewVersionOfTheScheme typed) {
        LocalDate today = today();
        List<TheSchemeAsPublished> alreadyPublished = everyVersionAlreadyPublished(today);
        TheSchemeAsPublished theOneBefore = alreadyPublished.get(alreadyPublished.size() - 1);
        SchemeVersion next = theVersionThatWouldFollow(alreadyPublished, today, typed);

        TheSchemeAsPublished written = versions.save(next).asPublished();
        // One INFO line carrying every figure that was decided, because this is the business event
        // the whole module exists for: what the scheme became, from when, and what the person who
        // published it said about it. It is the first thing anybody debugging a rate, an expiry or
        // a notification after this morning will want, and it is answered here rather than
        // reconstructed from the rows.
        log.info("a new version of the scheme was published version={} effectiveFrom={} "
                        + "itsDayHasCome={} weeklyThreshold={} theOrdinaryRate={} "
                        + "extraForEachFurtherWeek={} theMostAStreakPays={} "
                        + "howLongABatchOfPointsLasts={} balanceRungs={} "
                        + "whatShareOfABudgetIsRunningLow={} howManyOutstandingIsASpiral={} "
                        + "daysBeforeAMaturityIsWorthSaying={} "
                        + "daysBeforeAnAnniversaryIsWorthSaying={} theOneBefore={} whatChanged={}",
                written.version(), written.effectiveFrom(), written.hasStartedBy(today),
                written.weeklyThreshold(), written.theOrdinaryRate(),
                written.extraForEachFurtherWeek(), written.theMostAStreakPays(),
                written.howLongABatchOfPointsLasts(), written.balanceRungs(),
                written.whatShareOfABudgetIsRunningLow(), written.howManyOutstandingIsASpiral(),
                written.daysBeforeAMaturityIsWorthSaying(),
                written.daysBeforeAnAnniversaryIsWorthSaying(), theOneBefore.version(),
                written.whatChanged());
        return new TheVersionThatNowExists(written, written.hasStartedBy(today));
    }

    /**
     * The version a publish of this form would write, read and refused exactly as a publish would
     * read and refuse it — and then thrown away instead of saved.
     *
     * <p><strong>This exists so that preview and publish can never disagree about what is
     * sayable.</strong> A candidate posted to the preview door has to come back refused in the same
     * sentence it would be refused in at the publishing door, or an administrator learns the rule
     * twice and the second time is the time it matters. The only way to guarantee that is for one
     * reading to serve both, so this and {@link #publishTheNextVersion} run the identical
     * {@link #theVersionThatWouldFollow} over the identical rows, and differ in exactly one
     * statement: the save.
     *
     * <p><strong>It carries the version number the publish would give it</strong>, because the
     * number is part of the answer. A preview that showed thirteen figures and no version would
     * leave whoever reads it comparing an unnamed thing against version 4, and the screen would
     * have to invent "version 5" by adding one to something — which is the second place a version
     * number would be worked out, in a module whose whole argument is that there is only ever one.
     *
     * <p><strong>Read-only, and it writes nothing at all.</strong> {@code SchemeVersion.published}
     * makes an unsaved entity, nothing is handed to the repository, and the transaction is marked
     * read-only so that an accidental flush of one would be refused rather than performed. The
     * preview built on top of this makes the same promise about the whole application, and this is
     * the end of it that could most easily have broken it.
     *
     * @throws SchemeRefused in the same words {@link #publishTheNextVersion} would refuse it
     */
    @Transactional(readOnly = true)
    public TheSchemeAsPublished theVersionAPublishWouldWrite(ANewVersionOfTheScheme typed) {
        LocalDate today = today();
        TheSchemeAsPublished itWouldBe =
                theVersionThatWouldFollow(everyVersionAlreadyPublished(today), today, typed)
                        .asPublished();
        log.debug("the version a publish would write was read without writing it version={} "
                        + "effectiveFrom={} weeklyThreshold={} theOrdinaryRate={} "
                        + "extraForEachFurtherWeek={} theMostAStreakPays={} "
                        + "howLongABatchOfPointsLasts={} balanceRungs={} "
                        + "whatShareOfABudgetIsRunningLow={} howManyOutstandingIsASpiral={} "
                        + "daysBeforeAMaturityIsWorthSaying={} "
                        + "daysBeforeAnAnniversaryIsWorthSaying={}",
                itWouldBe.version(), itWouldBe.effectiveFrom(), itWouldBe.weeklyThreshold(),
                itWouldBe.theOrdinaryRate(), itWouldBe.extraForEachFurtherWeek(),
                itWouldBe.theMostAStreakPays(), itWouldBe.howLongABatchOfPointsLasts(),
                itWouldBe.balanceRungs(), itWouldBe.whatShareOfABudgetIsRunningLow(),
                itWouldBe.howManyOutstandingIsASpiral(),
                itWouldBe.daysBeforeAMaturityIsWorthSaying(),
                itWouldBe.daysBeforeAnAnniversaryIsWorthSaying());
        return itWouldBe;
    }

    /**
     * Every version the bank has already published, oldest first, with the impossible database
     * ruled out rather than left to surface as an index out of bounds.
     *
     * <p>The reading {@link TheSchemeInForceOn} makes of the same impossible state, and for the
     * same reason: a bank whose scheme was never written down is a broken database rather than a
     * customer's mistake, so there is no sentence to refuse anybody with. Nobody did anything
     * wrong, and there is no version number to count from.
     */
    private List<TheSchemeAsPublished> everyVersionAlreadyPublished(LocalDate today) {
        List<TheSchemeAsPublished> alreadyPublished = versions.findAllByOrderByVersionAsc().stream()
                .map(SchemeVersion::asPublished)
                .toList();
        if (alreadyPublished.isEmpty()) {
            throw new IllegalArgumentException("no version of the scheme has been published, so "
                    + "there is no version to follow on " + today);
        }
        return alreadyPublished;
    }

    /**
     * The next version as this form reads, numbered and judged — unsaved, so that the one caller
     * who wants to keep it can save it and the one who does not can drop it.
     *
     * <p>Both judgements happen here and in this order. {@link WhatAVersionOfTheSchemeMaySay} reads
     * the form against the clock; this class then asks the one question that is about the rows,
     * which is whether the Monday would strand a version somebody has already announced. Splitting
     * them between publish and preview is how the two doors would start disagreeing, which is the
     * thing the preview exists to make impossible.
     */
    private SchemeVersion theVersionThatWouldFollow(List<TheSchemeAsPublished> alreadyPublished,
                                                    LocalDate today, ANewVersionOfTheScheme typed) {
        int version = alreadyPublished.get(alreadyPublished.size() - 1).version() + 1;
        SchemeVersion next = WhatAVersionOfTheSchemeMaySay.readInto(version, today, typed);
        refuseAMondayThatWouldStrandAnAnnouncedVersion(
                version, typed.effectiveFrom(), alreadyPublished, today);
        return next;
    }

    /**
     * Refuses a Monday that comes before one a published version is already waiting on.
     *
     * <p>Only versions that have not started are in view. One that took effect a year ago cannot be
     * stranded by anything, and the ordinary case — every version in the history already in force —
     * asks this question of an empty list and answers nothing.
     *
     * <p>The sentence names both Mondays and says what to do with each, because the fix is a choice
     * the administrator has to make rather than a typo they have to spot: publish on or after the
     * announced day, or publish on exactly that day to supersede it.
     */
    private void refuseAMondayThatWouldStrandAnAnnouncedVersion(
            int version, LocalDate effectiveFrom, List<TheSchemeAsPublished> alreadyPublished,
            LocalDate today) {
        alreadyPublished.stream()
                .filter(announced -> !announced.hasStartedBy(today))
                .filter(announced -> announced.effectiveFrom().isAfter(effectiveFrom))
                .findFirst()
                .ifPresent(stranded -> {
                    String reason = "Version " + version + " cannot take effect on " + effectiveFrom
                            + ", because version " + stranded.version()
                            + " has already been announced for " + stranded.effectiveFrom()
                            + " and would then never come into force at all — the scheme in force "
                            + "is the highest version whose day has come. Publish on "
                            + stranded.effectiveFrom() + " to supersede it, or on a Monday after "
                            + "it.";
                    log.warn("a new version of the scheme was refused version={} effectiveFrom={} "
                                    + "strandedVersion={} strandedEffectiveFrom={} kind={} "
                                    + "reason={}",
                            version, effectiveFrom, stranded.version(), stranded.effectiveFrom(),
                            SchemeRefused.Kind.A_MONDAY_BEFORE_ONE_ALREADY_ANNOUNCED, reason);
                    throw new SchemeRefused(
                            SchemeRefused.Kind.A_MONDAY_BEFORE_ONE_ALREADY_ANNOUNCED, reason);
                });
    }

    /**
     * The day this application thinks it is, in the zone it counts its days in.
     *
     * <p>Borrowed from {@link SavingsWeek} rather than written out again, because a second copy of
     * the zone is a copy that can be changed on its own — and a scheme that decided which version
     * was in force in a different zone from the one a week is counted in would disagree with the
     * rest of the application for a few hours every evening, on exactly the boundary that matters
     * most here: the turn of a Monday.
     */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
    }
}
