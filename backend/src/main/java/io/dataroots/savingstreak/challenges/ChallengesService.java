package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.points.PointsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.challenges.ChallengeRefused.Kind.ALREADY_DONE_AND_NOT_REPEATABLE;
import static io.dataroots.savingstreak.challenges.ChallengeRefused.Kind.ALREADY_ENROLLED;
import static io.dataroots.savingstreak.challenges.ChallengeRefused.Kind.NOT_ENROLLED;
import static io.dataroots.savingstreak.challenges.ChallengeRefused.Kind.NO_SUCH_CHALLENGE;
import static io.dataroots.savingstreak.challenges.ChallengeRefused.Kind.NO_SUCH_CUSTOMER;
import static io.dataroots.savingstreak.challenges.ChallengeRefused.Kind.THE_SEASON_HAS_CLOSED;
import static io.dataroots.savingstreak.challenges.ChallengeRefused.Kind.THE_SEASON_HAS_NOT_OPENED;

/**
 * The Challenges module's face to the rest of the application: what a customer could take on, the
 * taking on of one, the leaving of one, and how far through each of theirs they are.
 *
 * <p><strong>A customer, never a savings account.</strong> Points are one pot per person and the
 * high-water mark a challenge reads already spans every savings account somebody holds, so a
 * challenge keyed by account would contradict both and would make "save EUR 500" satisfiable twice
 * by one customer with two accounts. This module is keyed by customer and never learns that savings
 * accounts exist — the same words the points ledger uses about itself.
 *
 * <p><strong>Downstream of everything it reads, and nothing reads it.</strong> It asks Deposits for
 * the customer's high-water mark and Accounts whether the customer is there, and that is the whole
 * of its coupling. No deposit, withdrawal, streak, points, loyalty or reward path is touched by a
 * challenge being joined or left, which is what makes the arrow point one way: enrolling changes
 * nothing about the money, and moving money changes nothing about an enrolment except what the next
 * read of it will say.
 *
 * <p><strong>It owns the enrolment and no progress at all.</strong> The reading, the next rung and
 * what that rung still asks for are worked out from the deposit ledger every time somebody looks,
 * because the development clock winds time in both directions and a stored counter would end up
 * describing a week that is now in the future. The one thing recorded is the mark the enrolment
 * measures from, and that is recorded precisely because it is history rather than state.
 *
 * <p><strong>Judging is one pass and it is idempotent.</strong> {@link #judge} reads every live
 * enrolment, takes each one's reading, and awards every rung that reading clears <em>and that has
 * no award row yet</em> — which is the only condition there is, so running it twice awards nothing
 * twice. It is called from the challenge endpoints before they answer, so a customer who has just
 * paid in sees the badge on the screen they are already looking at rather than at half past three
 * to-morrow morning. The deposit and withdrawal paths are not touched at all, which is what keeps
 * the arrow pointing one way.
 *
 * <p><strong>It knows that kinds exist and nothing about what any of them means.</strong> Every
 * reading goes through the {@link HowAChallengeIsRead} registered for the challenge's kind, indexed
 * once when this service is built and checked then for a kind claimed twice or answered by nobody.
 * So adding a kind is a value on the enum, a reading beside it and a seeded row — and not one line
 * in this file. Read that interface's own note for why the seam is there rather than here.
 *
 * <p><strong>A season is a window and this is the only place it is read.</strong> A challenge
 * belonging to a campaign cannot be joined before the window opens or after it has closed, and an
 * enrolment still running when the window closes is judged one last time and then lapses, keeping
 * everything it had already won. Both of those are decided against the moment the application's
 * clock reads, so winding it forward closes a season exactly as a night passing would — and a
 * challenge belonging to no campaign never asks the question at all.
 *
 * <p><strong>It credits points through the one door and knows nothing else about them.</strong> A
 * rung's points go into the customer's ordinary pot under a reason of this module's own, against
 * the award that won them. They are then points like any other: they age, expire, spend
 * oldest-first, can be given away and buy anything in the catalogue, because nothing in the ledger
 * has a special case for where they came from.
 */
@Service
public class ChallengesService {

    private static final Logger log = LoggerFactory.getLogger(ChallengesService.class);

    private final ChallengeDefinitionRepository definitions;
    private final CampaignRepository campaigns;
    private final ChallengeEnrolmentRepository enrolments;
    private final ChallengeAwardRepository awards;
    private final AccountsService accounts;
    private final DepositsService deposits;
    private final PointsService points;
    private final Clock clock;
    private final Map<ChallengeKind, HowAChallengeIsRead> readings;

    ChallengesService(ChallengeDefinitionRepository definitions, CampaignRepository campaigns,
                      ChallengeEnrolmentRepository enrolments, ChallengeAwardRepository awards,
                      AccountsService accounts, DepositsService deposits, PointsService points,
                      Clock clock, List<HowAChallengeIsRead> readings) {
        this.definitions = definitions;
        this.campaigns = campaigns;
        this.enrolments = enrolments;
        this.awards = awards;
        this.accounts = accounts;
        this.deposits = deposits;
        this.points = points;
        this.clock = clock;
        this.readings = indexedByKind(readings);
        log.debug("challenge readings wired kinds={}", this.readings.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        read -> read.getValue().getClass().getSimpleName())));
    }

    /**
     * Every kind against the one reading that answers for it, worked out once when the application
     * starts and never again.
     *
     * <p><strong>Both ways of getting it wrong are refused here, before anything is served.</strong>
     * Two readings claiming one kind is an ambiguity nothing later could resolve: the card and the
     * judging pass would each get whichever Spring happened to hand over, and the two could differ.
     * A kind with no reading at all is worse and quieter — a value on the enum means a definition can
     * be seeded with it, which means a card the application offers, a customer can enrol in, and
     * nothing can read. Failing to start names the kind and is the only way either of those is found
     * by the person who caused it rather than by a customer.
     *
     * <p>Where a switch used to be exhaustive and the compiler asked about a new value, this asks at
     * start-up instead. That is the price of the seam and it is paid deliberately: the check is a few
     * seconds later than the compiler's and it is a check nobody can forget to write, because it is
     * written once here rather than once per place that switches.
     */
    private static Map<ChallengeKind, HowAChallengeIsRead> indexedByKind(
            List<HowAChallengeIsRead> readings) {
        Map<ChallengeKind, HowAChallengeIsRead> byKind = new EnumMap<>(ChallengeKind.class);
        for (HowAChallengeIsRead reading : readings) {
            HowAChallengeIsRead already = byKind.put(reading.kind(), reading);
            if (already != null) {
                throw new IllegalStateException("two readings answer for " + reading.kind() + ": "
                        + already.getClass().getSimpleName() + " and "
                        + reading.getClass().getSimpleName()
                        + ". A kind is one question and it may have only one answer.");
            }
        }
        for (ChallengeKind kind : ChallengeKind.values()) {
            if (!byKind.containsKey(kind)) {
                throw new IllegalStateException("no reading answers for " + kind
                        + ". A kind this application can offer on a card and cannot read is a "
                        + "challenge a customer could enrol in and never make progress in; add a "
                        + HowAChallengeIsRead.class.getSimpleName() + " for " + kind + ".");
            }
        }
        return byKind;
    }

    /**
     * Every challenge open to the customer, with their standing in each.
     *
     * <p>The catalogue and the customer's place in it answered together, because that is the screen:
     * a page that read the challenges and then asked after each one separately would make a request
     * per row to draw itself. The mark is read once for the whole list, not once per card, for the
     * same reason — it is one figure about the person and every challenge of theirs is measured
     * against it.
     *
     * <p><strong>It judges before it answers</strong>, so the badge a customer has just earned is on
     * the screen they are already looking at rather than at half past three to-morrow morning. That
     * makes a read a write, which is a thing worth saying out loud — and it is the right trade,
     * because the alternative is a page that shows somebody a reading past gold and no gold.
     *
     * @throws ChallengeRefused if there is no such customer
     */
    @Transactional
    public List<AChallengeAsItStands> challengesFor(long customerId) {
        refuseUnlessTheCustomerIsThere(customerId, null);
        // The same moment the pass judged at is the moment the cards are read as of, so that a card
        // and the badge minted a line above it can never be answers about two different instants.
        Instant now = judgeBeforeAnswering(customerId);

        Map<String, ChallengeEnrolment> latestPerChallenge = theLatestEnrolmentPerChallenge(customerId);
        Map<Long, BigDecimal> whatFinishedIt = theReadingThatFinishedEachEnrolment(customerId);
        // The badges of the enrolments these cards are about, read in one query for the whole list
        // rather than one per card — and of the ended ones too, because an award is a fact and a
        // ladder on a challenge somebody left still has its rungs lit.
        Map<Long, Map<Rung, Instant>> wonOnEach =
                theRungsAlreadyWonOn(List.copyOf(latestPerChallenge.values()));
        // The seasons read once for the whole list rather than once per card, for the reason the
        // mark is: there are a handful of them and every card that belongs to one is asked the same
        // question about the same moment.
        Map<String, Campaign> seasons = theSeasonsByCode();

        List<AChallengeAsItStands> cards = definitions.findAllByOrderByIdAsc().stream()
                .map(definition -> asItStands(customerId, definition,
                        latestPerChallenge.get(definition.code()), now, whatFinishedIt, wonOnEach,
                        seasons.get(definition.campaignCode())))
                .toList();
        // One line for the whole read rather than one per card: this runs every time the tab is
        // opened, and each card's own reading says below what it was worked out from.
        log.debug("challenges read customerId={} now={} offered={} enrolledIn={}",
                customerId, now, cards.size(), cards.stream().filter(AChallengeAsItStands::enrolled)
                        .map(AChallengeAsItStands::code).toList());
        return cards;
    }

    /**
     * Takes the customer on to a challenge, recording the mark it will measure from.
     *
     * <p>The mark is read and written in the same transaction as the enrolment, and that ordering is
     * the load-bearing part: an enrolment written first and marked afterwards would count everything
     * saved in between, which is exactly the saving the customer had already done before they
     * decided to join.
     *
     * <p>The customer is checked before the challenge is, for the same reason a deposit checks its
     * accounts before its amount: if there is no such person, what they were trying to join is
     * beside the point.
     *
     * <p><strong>A repeat is allowed and asks for genuinely new money; a one-off is refused.</strong>
     * Re-enrolling is refused only while the enrolment they hold is still running, so a challenge
     * they have finished can be taken on again — and because the mark is read here and now, the
     * second round starts at nought with the first round's saving underneath it rather than inside
     * it. Finishing "save EUR 500" twice therefore means EUR 1,000 genuinely put away and not
     * EUR 500 walked in and out. A definition the bank has declared unrepeatable makes a different
     * promise, that the badge means the first and only time, and there is no arithmetic that keeps
     * that promise — so it is kept by refusing.
     *
     * <p><strong>A season is checked before anything else about the enrolment.</strong> Whether the
     * window is open is a fact about the challenge rather than about this customer's history with
     * it, so it is answered as soon as the challenge is known and before the pass that judges
     * anything: a campaign that closed last month refuses everybody identically, and there is
     * nothing about their own enrolments that could change the answer.
     *
     * @throws ChallengeRefused if there is no such customer, no such challenge, its season has not
     *                          opened or has closed, they are in it already, or they have finished a
     *                          one-off
     */
    @Transactional
    public AnEnrolment enrol(long customerId, String challengeCode) {
        refuseUnlessTheCustomerIsThere(customerId, challengeCode);
        ChallengeDefinition definition = theChallengeOrRefuse(customerId, challengeCode);
        refuseUnlessTheSeasonIsOpen(customerId, definition, clock.instant());
        judgeBeforeAnswering(customerId);

        enrolments.findFirstByCustomerIdAndChallengeCodeOrderByIdDesc(customerId, definition.code())
                .filter(taken -> taken.state().isLive())
                .ifPresent(taken -> {
                    throw refusing(customerId, definition.code(), ALREADY_ENROLLED,
                            "You are already taking on " + definition.title() + " ("
                                    + definition.code() + ").");
                });

        refuseASecondGoAtAOneOff(customerId, definition);

        BigDecimal measuringFrom = deposits.mostEverSavedBy(customerId);
        // One moment for the enrolment, read from the application's clock and truncated the way a
        // deposit's is, so that a clock a trainer winds forward carries enrolments along with the
        // deposits they are measured against.
        Instant clockReads = clock.instant();
        Instant enrolledAt = clockReads.truncatedTo(ChronoUnit.MILLIS);
        log.debug("enrolment takes its moment and its mark from the application customerId={} "
                        + "challenge={} clockReads={} recordedMoment={} mostEverSaved={}",
                customerId, definition.code(), clockReads, enrolledAt, measuringFrom);

        AnEnrolment taken = enrolments.save(ChallengeEnrolment.taking(
                customerId, definition.code(), measuringFrom, enrolledAt)).asTaken();
        log.info("challenge enrolled customerId={} challenge={} enrolmentId={} kind={} "
                        + "measuringFrom={} enrolledAt={}",
                customerId, taken.challengeCode(), taken.id(), definition.kind(),
                taken.measuringFrom(), taken.enrolledAt());
        return taken;
    }

    /**
     * Ends the customer's enrolment in a challenge, leaving the record of it behind.
     *
     * <p>A state change and never a delete: the badges a later slice hangs off an enrolment point at
     * it, and a customer having left a challenge is a thing that happened, which nothing else
     * records. Nothing already awarded is touched — quitting costs nobody anything they had already
     * earned.
     *
     * @throws ChallengeRefused if there is no such customer, no such challenge, or nothing of theirs
     *                          to leave
     */
    @Transactional
    public AnEnrolment leave(long customerId, String challengeCode) {
        refuseUnlessTheCustomerIsThere(customerId, challengeCode);
        ChallengeDefinition definition = theChallengeOrRefuse(customerId, challengeCode);
        // Before it is ended rather than after, so that a rung the customer had already cleared is
        // paid on the way out. Quitting costs nobody anything they had already earned, and a rung
        // they had reached and never looked at is something they had earned.
        judgeBeforeAnswering(customerId);

        ChallengeEnrolment running = enrolments
                .findFirstByCustomerIdAndChallengeCodeOrderByIdDesc(customerId, definition.code())
                .filter(taken -> taken.state().isLive())
                .orElseThrow(() -> refusing(customerId, definition.code(), NOT_ENROLLED,
                        "You are not taking on " + definition.title() + " (" + definition.code()
                                + "), so there is nothing to leave."));

        Instant endedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        running.abandon(endedAt);
        AnEnrolment ended = enrolments.save(running).asTaken();
        log.info("challenge enrolment ended customerId={} challenge={} enrolmentId={} state={} "
                        + "measuringFrom={} endedAt={}",
                customerId, ended.challengeCode(), ended.id(), ended.state(), ended.measuringFrom(),
                ended.endedAt());
        return ended;
    }

    /**
     * Everything the customer has ever achieved, newest first — the trophy case.
     *
     * <p>A list that only ever grows. Nothing in it is revoked, recomputed or expired, whatever
     * becomes of the money or of the points it paid: an achievement is a record of something that
     * happened, and spending your own savings is never punished here.
     *
     * <p>Each one carries the figures that were recorded when it was won rather than to-day's, so an
     * old badge explains itself and a threshold the bank retunes next month does not rewrite what
     * anybody was paid. The title is the exception and is read off the definition as it now stands,
     * because a challenge that has been renamed should appear under its name.
     *
     * <p>It judges before it answers, like every other read here, so a customer who has just paid in
     * and gone looking for the badge finds it rather than finding yesterday's case.
     *
     * @throws ChallengeRefused if there is no such customer
     */
    @Transactional
    public List<AnAchievement> achievementsOf(long customerId) {
        refuseUnlessTheCustomerIsThere(customerId, null);
        judgeBeforeAnswering(customerId);

        Map<String, String> titles = definitions.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(ChallengeDefinition::code, ChallengeDefinition::title));
        List<AnAchievement> won = awards.findByCustomerIdOrderByAwardedAtDescIdDesc(customerId)
                .stream()
                // A challenge the bank has since withdrawn still has to appear, under the code the
                // customer won it with: the award is a fact, and a missing definition is no reason
                // to hide something somebody earned.
                .map(award -> award.asWon(
                        titles.getOrDefault(award.challengeCode(), award.challengeCode())))
                .toList();
        log.debug("trophy case read customerId={} achievements={} pointsTheyPaid={}",
                customerId, won.size(), won.stream().mapToLong(AnAchievement::points).sum());
        return won;
    }

    /**
     * Every season the bank runs, with its window and the challenges in it — the one read here that
     * names nobody.
     *
     * <p>Everything else this service answers is somebody's: their card, their enrolment, their
     * trophy case. What campaigns are running and until when is the same answer for everybody, in
     * the way the rewards catalogue is, and a customer who has joined nothing still has to be able
     * to see that there is a season on and how long is left of it. So there is no customer in the
     * signature and nothing here is judged — there is nobody to judge.
     *
     * <p>Every season rather than only the open ones. A campaign that is over is still something a
     * card points at, because an enrolment that lapsed has to be able to say which window ran out
     * from under it, and a listing showing what has finished beside what is running is how somebody
     * tells "I missed it" from "there has never been one".
     *
     * <p>Whether each one is open is answered here, against the moment the application's clock
     * reads, rather than left to whoever draws the banner: it is the same question
     * {@link #enrol} refuses on, and two places answering it is a page that offers a season this
     * service will not let anybody join.
     */
    @Transactional(readOnly = true)
    public List<ASeasonAndItsChallenges> campaignsOnOffer() {
        Instant now = clock.instant();
        Map<String, List<AChallengeInASeason>> inEach = definitions.findAllByOrderByIdAsc().stream()
                .filter(definition -> definition.campaignCode() != null)
                .collect(Collectors.groupingBy(ChallengeDefinition::campaignCode,
                        Collectors.mapping(
                                definition -> new AChallengeInASeason(
                                        definition.code(), definition.title()),
                                Collectors.toList())));
        List<ASeasonAndItsChallenges> running = campaigns.findAllByOrderByIdAsc().stream()
                .map(season -> new ASeasonAndItsChallenges(season.asRunning(now),
                        inEach.getOrDefault(season.code(), List.of())))
                .toList();
        log.debug("seasons read now={} seasons={} open={}",
                now, running.stream().map(season -> season.season().code()).toList(),
                running.stream().filter(season -> season.season().open())
                        .map(season -> season.season().code()).toList());
        return running;
    }

    /**
     * Awards every rung the customer has now cleared and not yet been paid for, credits what each
     * one is worth, and finishes the enrolments that have reached their last rung or whose season
     * has closed under them.
     *
     * <p><strong>Idempotent, and by construction rather than by care.</strong> "Has this rung of
     * this enrolment already been awarded" is the only condition there is: a second pass finds an
     * award row for everything the reading clears and writes nothing. There is no high-water mark of
     * rungs to keep in step, no counter to increment and nothing to reset — the awards themselves
     * are the record of what has been paid, so the pass cannot disagree with them. The database
     * keeps the same rule as a unique index over the enrolment and the rung, which is what holds
     * when two of these run at the same instant.
     *
     * <p><strong>Rungs are marks on one running figure.</strong> There is one reading per enrolment
     * and the rungs are places on it, which is what stops the same euro from paying bronze twice —
     * and it is also what makes one large deposit clear all three at once and be paid for all three,
     * each with its own dated award and its own batch of points. Saving a lot in one go is never
     * worse than saving it in instalments.
     *
     * <p><strong>The award is written before the points are credited</strong>, and the credit
     * references it, so that what this module says it paid and what the ledger was actually paid are
     * one decision rather than two. The same ordering a paid loyalty anniversary uses, for the same
     * reason.
     *
     * <p>Only live enrolments are judged. One that was left or finished is not counting any more,
     * and everything it already won is untouched — the awards point at it by identifier and nothing
     * here ever deletes one.
     *
     * <p>Called from the challenge endpoints before they answer, so a customer who has just made a
     * deposit sees the badge on the screen they are already looking at. The deposit and withdrawal
     * paths are not touched at all, which is what keeps this module downstream of everything it
     * reads.
     *
     * @param customerId whose enrolments to judge
     * @param now        the moment to judge as of, which is what every award written here is dated
     *                   at and what the points it pays are earned at
     */
    @Transactional
    public void judge(long customerId, Instant now) {
        List<ChallengeEnrolment> live = enrolments.findByCustomerIdOrderByIdDesc(customerId).stream()
                .filter(taken -> taken.state().isLive())
                .toList();
        if (live.isEmpty()) {
            // The ordinary case for most customers on most reads, and worth a line all the same: it
            // says the pass ran, so a tab with no badge on it is not blamed on a step nobody sees.
            log.debug("challenges judged customerId={} now={} liveEnrolments=0", customerId, now);
            return;
        }
        Map<Long, Map<Rung, Instant>> alreadyWon = theRungsAlreadyWonOn(live);
        Map<String, ChallengeDefinition> offered = definitions.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(ChallengeDefinition::code, Function.identity()));
        Map<String, Campaign> seasons = theSeasonsByCode();
        log.debug("challenges judged customerId={} now={} liveEnrolments={}",
                customerId, now, live.stream().map(ChallengeEnrolment::id).toList());
        for (ChallengeEnrolment enrolment : live) {
            ChallengeDefinition definition = offered.get(enrolment.challengeCode());
            if (definition == null) {
                log.warn("enrolment not judged customerId={} enrolmentId={} challenge={} "
                                + "reason=the bank no longer offers a challenge under that code",
                        customerId, enrolment.id(), enrolment.challengeCode());
                continue;
            }
            judge(customerId, definition, enrolment, now,
                    alreadyWon.getOrDefault(enrolment.id(), Map.of()).keySet(),
                    seasons.get(definition.campaignCode()));
        }
    }

    /**
     * One enrolment judged: every rung the reading clears and that has no award row yet, in the
     * order they are climbed, and then the enrolment ended if it has finished or if the season it
     * belongs to has closed underneath it.
     *
     * <p>Clearing means reaching the threshold rather than passing it, exactly as
     * {@link TheNextRungUp} reads it — a challenge that asks for EUR 500 is finished by the
     * five-hundredth euro. The two have to agree, because a card saying nothing is still needed
     * beside a pass that had awarded nothing would be two answers to one question.
     *
     * <p><strong>The season is looked at after the rungs and not before.</strong> That ordering is
     * the whole of "judged one last time and then lapses": the reading is taken as of now, every
     * rung it clears is paid, and only then does the closed window end the enrolment. Checking the
     * window first would mean a customer whose last deposit landed inside the season but who did not
     * open the tab again before it closed lost the rung they had genuinely reached — which is the
     * one thing a season ending must not cost anybody.
     *
     * <p>Finishing wins over lapsing when both are true at once, and deliberately. An enrolment that
     * cleared gold on this very pass has done the thing, whatever the calendar did in the same
     * instant, and {@code COMPLETED} is what a customer would call it.
     *
     * @param season the campaign this challenge belongs to, or null if it is evergreen — in which
     *               case there is no window and the enrolment ends only by being finished
     */
    private void judge(long customerId, ChallengeDefinition definition, ChallengeEnrolment enrolment,
                       Instant now, Set<Rung> alreadyWon, Campaign season) {
        List<ChallengeRung> rungs = definition.rungs();
        BigDecimal reading = theReadingOf(definition, enrolment, now);
        // Every input behind the judgement, so that a badge that was or was not minted can be
        // explained from the log alone: the enrolment, the reading that came out, the thresholds it
        // was compared against and the rungs already held. What the reading itself was worked out
        // from is on the line the kind's own reading logged just above this one.
        log.debug("enrolment judged customerId={} challenge={} enrolmentId={} kind={} "
                        + "reading={} thresholds={} alreadyAwarded={}",
                customerId, definition.code(), enrolment.id(), definition.kind(), reading,
                rungs.stream().map(ChallengeRung::threshold).toList(), alreadyWon);

        Set<Rung> held = EnumSet.noneOf(Rung.class);
        held.addAll(alreadyWon);
        for (ChallengeRung rung : rungs) {
            if (reading.compareTo(rung.threshold()) < 0 || held.contains(rung.rung())) {
                continue;
            }
            awardAndPayFor(customerId, definition, enrolment, rung, reading, now);
            held.add(rung.rung());
        }

        Rung theLastRung = rungs.get(rungs.size() - 1).rung();
        if (held.contains(theLastRung)) {
            enrolment.complete(now);
            AnEnrolment finished = enrolments.save(enrolment).asTaken();
            log.info("challenge enrolment ended customerId={} challenge={} enrolmentId={} state={} "
                            + "rung={} reading={} endedAt={}",
                    customerId, finished.challengeCode(), finished.id(), finished.state(),
                    theLastRung, reading, finished.endedAt());
        } else if (season != null && season.hasClosedBy(now)) {
            enrolment.lapse(now);
            AnEnrolment lapsed = enrolments.save(enrolment).asTaken();
            log.info("challenge enrolment ended customerId={} challenge={} enrolmentId={} state={} "
                            + "campaign={} closedOn={} reading={} rungsKept={} endedAt={}",
                    customerId, lapsed.challengeCode(), lapsed.id(), lapsed.state(), season.code(),
                    season.closesOn(), reading, held, lapsed.endedAt());
        }
    }

    /**
     * Writes the award and pays what the rung is worth, in that order.
     *
     * <p>The award first, because the credit references it and because a payment nothing recorded
     * would be a payment nothing could refuse to make twice. The row carries the reading that won it
     * and the points it paid, both as they stood at this moment, and is never touched again.
     */
    private void awardAndPayFor(long customerId, ChallengeDefinition definition,
                                ChallengeEnrolment enrolment, ChallengeRung rung,
                                BigDecimal reading, Instant now) {
        ChallengeAward award = awards.save(ChallengeAward.won(customerId, enrolment.id(),
                definition.code(), rung.rung(), reading, rung.points(), now));
        if (rung.points() > 0) {
            points.creditChallengeReward(customerId, award.id(), rung.points(), now);
        } else {
            // A rung the bank has priced at nothing. The badge is still minted, because reaching it
            // happened; there is simply no batch of points to leave behind, the same way a streak
            // uplift of nothing leaves none.
            log.debug("challenge rung paid no points customerId={} challenge={} rung={} "
                            + "reason=the bank prices this rung at nothing",
                    customerId, definition.code(), rung.rung());
        }
        log.info("challenge rung awarded customerId={} challenge={} rung={} reading={} points={} "
                        + "threshold={} enrolmentId={} awardId={} awardedAt={}",
                customerId, definition.code(), rung.rung(), reading, rung.points(),
                rung.threshold(), enrolment.id(), award.id(), now);
    }

    /**
     * Which rungs of which of these enrolments have already been won, and when, in one query rather
     * than one per enrolment.
     *
     * <p>Two callers want different halves of the same answer and neither wants a query of its own.
     * The judging pass reads the rungs and nothing else — "not already awarded" is the whole of what
     * keeps it from paying twice — and the card reads the moments too, because a lit rung with no
     * date on it is a claim nobody can check.
     */
    private Map<Long, Map<Rung, Instant>> theRungsAlreadyWonOn(List<ChallengeEnrolment> taken) {
        List<Long> ids = taken.stream().map(ChallengeEnrolment::id).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Rung, Instant>> byEnrolment = new HashMap<>();
        for (ChallengeAwardRepository.RungAlreadyAwarded won : awards.rungsAlreadyAwardedFor(ids)) {
            byEnrolment.computeIfAbsent(won.getEnrolmentId(), any -> new EnumMap<>(Rung.class))
                    .put(won.getRung(), won.getAwardedAt());
        }
        return byEnrolment;
    }

    /**
     * For each enrolment that has an award on it, the reading recorded on its latest one — which
     * for a finished enrolment is the reading that cleared its last rung.
     *
     * <p>The awards come back newest first, so the first one seen per enrolment is the latest, and
     * that is the figure a card reports for an enrolment that is over. Read for the whole customer
     * in one query, beside the mark and the enrolments, rather than once per card.
     */
    private Map<Long, BigDecimal> theReadingThatFinishedEachEnrolment(long customerId) {
        Map<Long, BigDecimal> latest = new HashMap<>();
        for (ChallengeAward award : awards.findByCustomerIdOrderByAwardedAtDescIdDesc(customerId)) {
            latest.putIfAbsent(award.enrolmentId(), award.reading());
        }
        return latest;
    }

    /**
     * Takes the moment from the application's clock and judges as of it.
     *
     * <p>The clock rather than {@code Instant.now()}, so that a trainer winding time forward during
     * a session has their challenges judged in the week they have wound to, and truncated the way a
     * deposit's moment is, so an award and the deposit that won it are dated in the same units.
     *
     * <p>Answers the moment it judged as of, so that a caller drawing cards straight afterwards can
     * ask the readings about the same instant the pass did rather than about a clock read a moment
     * later.
     */
    private Instant judgeBeforeAnswering(long customerId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        judge(customerId, now);
        return now;
    }

    /**
     * One card: the challenge as the bank describes it, plus where this customer stands in it.
     *
     * <p><strong>The three states report three different things, and the differences are the
     * point.</strong> A live enrolment's reading is worked out now, from the mark as it stands. A
     * challenge nobody joined has no reading at all, because enrolling is a decision somebody makes
     * and a nought would read as progress they have made. One somebody <em>left</em> has none
     * either: a figure worked out from to-day's mark would be a challenge quietly carrying on
     * counting for a customer who is no longer in it, and there is nothing it was frozen at, because
     * leaving is not an outcome the challenge reached.
     *
     * <p>A <em>finished</em> one is the case that does report a figure, and it reports the reading
     * that finished it, read off its last award rather than worked out again. That is the honest
     * number — the one that cleared gold, recorded at the moment it did — and it is the only one
     * that stays still: a completed {@code NEW_SAVINGS} challenge whose reading kept being
     * recomputed would go on climbing for years after the customer finished it, which is a card
     * describing something that is over as though it were not.
     */
    private AChallengeAsItStands asItStands(long customerId, ChallengeDefinition definition,
                                            ChallengeEnrolment enrolment, Instant now,
                                            Map<Long, BigDecimal> whatFinishedIt,
                                            Map<Long, Map<Rung, Instant>> wonOnEach,
                                            Campaign season) {
        List<ChallengeRung> rungs = definition.rungs();
        Optional<BigDecimal> reading = Optional.ofNullable(enrolment).flatMap(taken -> switch (taken.state()) {
            case ACTIVE -> Optional.of(readingOf(customerId, definition, taken, now, rungs));
            case COMPLETED -> Optional.ofNullable(whatFinishedIt.get(taken.id()));
            case ABANDONED, EXPIRED -> Optional.<BigDecimal>empty();
        });
        return new AChallengeAsItStands(
                definition.code(),
                definition.title(),
                definition.words(),
                definition.kind(),
                definition.repeatable(),
                theLadderOf(rungs, enrolment, wonOnEach),
                // Worked out as of the same moment the reading is, so a card cannot say a season is
                // open beside a progress bar read a tick either side of the close.
                Optional.ofNullable(season).map(running -> running.asRunning(now)),
                Optional.ofNullable(enrolment).map(ChallengeEnrolment::asTaken),
                reading,
                reading.flatMap(read -> TheNextRungUp.above(read, rungs)));
    }

    /**
     * The rungs of a challenge with the ones this enrolment has already won dated, and the rest
     * left empty.
     *
     * <p>This enrolment's awards and no others. A repeatable challenge taken on a second time
     * measures from a fresh mark and asks for the whole of it again, so a ladder lit from the first
     * round would be telling somebody they were two thirds of the way through something they have
     * only just started. What they won the first time is in the trophy case, where nothing is ever
     * taken away.
     */
    private List<ARungAsItStands> theLadderOf(List<ChallengeRung> rungs,
                                              ChallengeEnrolment enrolment,
                                              Map<Long, Map<Rung, Instant>> wonOnEach) {
        if (enrolment == null) {
            return rungs.stream().map(ARungAsItStands::notWonYet).toList();
        }
        Map<Rung, Instant> won = wonOnEach.getOrDefault(enrolment.id(), Map.of());
        return rungs.stream()
                .map(rung -> new ARungAsItStands(rung, Optional.ofNullable(won.get(rung.rung()))))
                .toList();
    }

    /**
     * How far a live enrolment has got, and the inputs it was worked out from, said out loud.
     *
     * <p>What the figure is worked out from is the kind's own business and is logged by the kind's
     * own {@link HowAChallengeIsRead}; what this adds is where the figure leaves the customer on the
     * ladder, which is the part that belongs to the card.
     */
    private BigDecimal readingOf(long customerId, ChallengeDefinition definition,
                                 ChallengeEnrolment enrolment, Instant now,
                                 List<ChallengeRung> rungs) {
        BigDecimal reading = theReadingOf(definition, enrolment, now);
        Optional<TheNextRungUp> next = TheNextRungUp.above(reading, rungs);
        // What the card will say, beside the rung the figure is being compared against — so that a
        // customer told their progress is EUR 40 when they have saved EUR 400 this year can be
        // answered from the log, together with the reading's own line just above this one.
        log.debug("challenge reading customerId={} challenge={} enrolmentId={} kind={} reading={} "
                        + "nextRung={} stillNeeded={}",
                customerId, definition.code(), enrolment.id(), definition.kind(), reading,
                next.map(up -> up.rung().rung()).orElse(null),
                next.map(TheNextRungUp::stillNeeded).orElse(null));
        return reading;
    }

    /**
     * The figure itself, with nothing said about it — the one derivation the card and the judging
     * pass both go through, so that what a customer is shown and what they are paid for can never
     * be two different answers to the same question.
     *
     * <p>The kind picks the reading and the reading does the rest. The index was built and checked
     * when this service was, so there is no kind that can arrive here without one and no decision
     * left to take: see {@link HowAChallengeIsRead} for why adding a kind touches nothing in this
     * file.
     */
    private BigDecimal theReadingOf(ChallengeDefinition definition, ChallengeEnrolment enrolment,
                                    Instant now) {
        return readings.get(definition.kind()).readingOf(definition, enrolment, now);
    }

    /**
     * The customer's newest enrolment in each challenge they have ever taken on.
     *
     * <p>Newest, because a repeatable challenge somebody has been round twice has two rows and the
     * one they are standing in is the later. Read in one query rather than one per challenge: the
     * card list is drawn on every visit to the tab, and a query per row would grow with the
     * catalogue.
     */
    private Map<String, ChallengeEnrolment> theLatestEnrolmentPerChallenge(long customerId) {
        Map<String, ChallengeEnrolment> latest = new HashMap<>();
        for (ChallengeEnrolment taken : enrolments.findByCustomerIdOrderByIdDesc(customerId)) {
            latest.putIfAbsent(taken.challengeCode(), taken);
        }
        return latest;
    }

    private ChallengeDefinition theChallengeOrRefuse(long customerId, String challengeCode) {
        String code = challengeCode == null ? "" : challengeCode.trim();
        return definitions.findByCode(code)
                .orElseThrow(() -> refusing(customerId, code, NO_SUCH_CHALLENGE,
                        "There is no challenge called \"" + code + "\"."));
    }

    /**
     * Whoever asked has to be somebody, and being told so is more use than the empty card of a
     * customer who has simply never joined anything.
     *
     * <p>Asked of Accounts rather than inferred from having no enrolments, for the reason every
     * module here asks: this one records nothing about who exists, and a customer with no enrolments
     * and a customer who is not there look identical from in here.
     */
    private void refuseUnlessTheCustomerIsThere(long customerId, String challengeCode) {
        if (!accounts.customerExists(customerId)) {
            throw refusing(customerId, challengeCode, NO_SUCH_CUSTOMER,
                    AccountsService.noSuchCustomer(customerId));
        }
    }

    /**
     * Every season the bank runs, against its code, read once for whatever is about to ask a
     * handful of challenges the same question.
     *
     * <p>A map rather than a lookup per definition, for the reason the enrolments are read in one
     * query: both the card list and the judging pass walk everything the customer has, and a
     * campaign fetched per row would grow a query count with the catalogue. There are a handful of
     * seasons and they are the same handful for everybody.
     */
    private Map<String, Campaign> theSeasonsByCode() {
        return campaigns.findAllByOrderByIdAsc().stream()
                .collect(Collectors.toMap(Campaign::code, Function.identity()));
    }

    /**
     * A challenge inside a season nobody can join yet, or one whose season is over.
     *
     * <p>Evergreen challenges never reach the question: a definition with no campaign belongs to no
     * window, and there is nothing about the calendar that could refuse it.
     *
     * <p><strong>A campaign code naming a season that is not there is refused rather than read as
     * evergreen.</strong> That row can only be a definition seeded wrong, and the quiet reading of
     * it — no season found, so always open — is the dangerous one: a challenge the bank believed it
     * had closed would go on being joinable for ever, and nothing would say so. It is refused as a
     * season that has not opened, because that is the truthful sentence, and the log line says which
     * code was not found so the person who caused it can fix the row.
     */
    private void refuseUnlessTheSeasonIsOpen(long customerId, ChallengeDefinition definition,
                                             Instant now) {
        String seasonCode = definition.campaignCode();
        if (seasonCode == null) {
            return;
        }
        Optional<Campaign> season = campaigns.findByCode(seasonCode);
        if (season.isEmpty()) {
            log.warn("a challenge names a season the bank does not run customerId={} challenge={} "
                            + "campaign={} reason=the definition was seeded against a campaign row "
                            + "that is not there",
                    customerId, definition.code(), seasonCode);
            throw refusing(customerId, definition.code(), THE_SEASON_HAS_NOT_OPENED,
                    definition.title() + " (" + definition.code() + ") belongs to a season that is "
                            + "not running, so there is nothing to join yet.");
        }
        Campaign window = season.get();
        log.debug("a season is asked whether it is open customerId={} challenge={} campaign={} "
                        + "opensOn={} closesOn={} now={} open={}",
                customerId, definition.code(), window.code(), window.opensOn(), window.closesOn(),
                now, window.isOpenAt(now));
        if (!window.hasOpenedBy(now)) {
            throw refusing(customerId, definition.code(), THE_SEASON_HAS_NOT_OPENED,
                    window.title() + " has not opened yet. It opens on " + window.opensOn()
                            + ", and " + definition.title() + " (" + definition.code()
                            + ") can be taken on from then.");
        }
        if (window.hasClosedBy(now)) {
            throw refusing(customerId, definition.code(), THE_SEASON_HAS_CLOSED,
                    window.title() + " closed on " + window.closesOn() + ", so "
                            + definition.title() + " (" + definition.code() + ") can no longer be "
                            + "taken on. Anything you won while it was open is still yours.");
        }
    }

    /**
     * A challenge the bank offers once in a lifetime, and a customer who has already had it.
     *
     * <p><strong>Completed and nothing else.</strong> A one-off somebody enrolled in and never
     * finished is still theirs to be in, and one they abandoned part-way is theirs to come back to:
     * the promise the bank made was about the badge, and they have not won it. Only a finished
     * enrolment has used the one go up.
     *
     * <p>Asked after the judging pass has run, which is what makes it honest at the one moment it
     * matters most — a customer whose last deposit carried them past gold but who has not looked at
     * the tab since has a finished enrolment by the time this reads it, rather than a running one
     * that would let them start again and be paid for the same rungs twice.
     *
     * <p>Every enrolment of theirs is read rather than only the latest, because "have they ever
     * finished this" is the question, and reading the newest row would be trusting that nothing can
     * ever sit on top of a completed one. It is the whole of somebody's own history with this
     * module and it is already read once per judging pass, so there is nothing to save by asking
     * more narrowly.
     */
    private void refuseASecondGoAtAOneOff(long customerId, ChallengeDefinition definition) {
        if (definition.repeatable()) {
            return;
        }
        enrolments.findByCustomerIdOrderByIdDesc(customerId).stream()
                .filter(taken -> taken.challengeCode().equals(definition.code()))
                .filter(taken -> taken.state() == EnrolmentState.COMPLETED)
                .findFirst()
                .ifPresent(finished -> {
                    log.debug("a one-off is being asked for a second time customerId={} "
                                    + "challenge={} finishedEnrolmentId={} finishedAt={}",
                            customerId, definition.code(), finished.id(),
                            finished.asTaken().endedAt());
                    throw refusing(customerId, definition.code(), ALREADY_DONE_AND_NOT_REPEATABLE,
                            "You have already finished " + definition.title() + " ("
                                    + definition.code() + "), and it is not one you can take on "
                                    + "again.");
                });
    }

    /**
     * Every refusal says why in the log as well as to whoever asked, because only one of the two is
     * kept. Worth having for this module in particular: "already enrolled" and "not enrolled" are
     * the two ways a page that has been open for a while gets out of step with what the customer
     * has since done on another device, and the log is where that is told apart from a bug.
     */
    private ChallengeRefused refusing(long customerId, String challengeCode,
                                      ChallengeRefused.Kind kind, String reason) {
        log.warn("challenge request refused customerId={} challenge={} kind={} reason={}",
                customerId, challengeCode, kind, reason);
        return new ChallengeRefused(kind, reason);
    }
}
