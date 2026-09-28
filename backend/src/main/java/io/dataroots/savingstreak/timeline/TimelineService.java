package io.dataroots.savingstreak.timeline;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.loyalty.LoyaltyService;
import io.dataroots.savingstreak.loyalty.NextAnniversaryOfADeposit;
import io.dataroots.savingstreak.points.PointsExpiringOnADay;
import io.dataroots.savingstreak.points.PointsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Timeline module's face to the rest of the application: what one savings account has coming in
 * the year ahead, as one ordered list of dated things.
 *
 * <p>A module of its own because no existing one can hold it. It needs Deposits to say which
 * payments went into the account, Points to say when what those payments earned is due to go,
 * Loyalty to say when each of them next pays, and a clock to know which year it is drawing. Deposits
 * has no opinion about points, Points cannot see a deposit's anniversaries and Loyalty knows nothing
 * about expiry — this is the situation the Loyalty module was itself created for, and the same
 * answer applies a second time.
 *
 * <p>The web layer is not that home either, although it already assembles five modules' figures into
 * an account's overview. Assembling is not a rule; this is. How far ahead to look, which markers are
 * worth drawing, how they are grouped and what order a shared day runs in are all decisions, and a
 * controller taking them would put a rule in the web layer and a calendar reading in a package that
 * has never made one.
 *
 * <p><strong>Everything on an account's bar belongs to that account's deposits.</strong> That is a
 * departure from how points are reported everywhere else and it is deliberate. A points balance is
 * the customer's, because it is one number and repeating it per account would claim they held as
 * many pots as they hold accounts. A date is not a number: "these points go on the 14th" stays true
 * however it is grouped, and grouping it by the deposits that earned it is exactly what lets this be
 * about the pot on the screen rather than about the person. The customer's own figures — the balance
 * and what expires next — are untouched and stay beside it, saying what they always said.
 *
 * <p>Points somebody was given are on no bar. They were earned by no deposit of the customer's, so
 * there is no account they could belong to; they are in the balance and in what expires next, which
 * is where points that are nobody's account's already live.
 */
@Service
public class TimelineService {

    private static final Logger log = LoggerFactory.getLogger(TimelineService.class);

    /**
     * The order two markers sharing a day come back in: by day, and then by kind, which is declared
     * in the order a day actually runs in — the expiry sweep is scheduled before the loyalty sweep,
     * so the points go and then the bonus arrives.
     */
    private static final Comparator<TimelineEvent> AS_THE_YEAR_RUNS =
            Comparator.comparing(TimelineEvent::on).thenComparing(TimelineEvent::kind);

    private final DepositsService deposits;
    private final PointsService points;
    private final LoyaltyService loyalty;
    private final Clock clock;

    TimelineService(DepositsService deposits, PointsService points, LoyaltyService loyalty, Clock clock) {
        this.deposits = deposits;
        this.points = points;
        this.loyalty = loyalty;
        this.clock = clock;
    }

    /**
     * What this savings account has coming: the window, and every dated thing inside it.
     *
     * <p>In one read transaction, for the reason the account's own endpoints give: the days points
     * go and the days bonuses arrive are read from two modules, and a claim or a withdrawal
     * committing between the two would put a bar on the screen whose two halves describe different
     * instants of the ledger — a marker for points that had just been spent, beside an anniversary
     * priced on euros that had already left.
     *
     * <p>An account nobody has heard of is not refused here, and that is not an omission. This
     * module has no way to tell one from an account that exists and has never been paid into, both
     * being a list of no deposits; who holds which account is the Accounts module's answer and the
     * caller has already asked it. So the honest answer to "no deposits" is an empty year.
     *
     * <p>The clock is read once here and once more inside Loyalty, which reads it on its own behalf
     * for the reason its own documentation gives. The two readings are microseconds apart inside one
     * transaction and no rule turns on the difference — the window is a day, and an anniversary that
     * fell between them is reported as owed either way.
     */
    @Transactional(readOnly = true)
    public AccountTimeline timelineOf(long savingsAccountId) {
        Instant now = clock.instant();
        LocalDate from = TimelineHorizon.opensOn(now);
        LocalDate until = TimelineHorizon.closesOn(from);

        // Every deposit ever made into the account, emptied ones included: points outlive the euros
        // that earned them, and a drained deposit's points have their own twelve months left to run.
        List<Long> madeInto = deposits.whichDepositsWereMadeInto(savingsAccountId);
        List<PointsExpiringOnADay> going = points.whenThePointsEarnedByDepositsGo(madeInto);

        // And when each deposit that still holds money next pays, which is already one answer per
        // deposit, already inside the window, and already reporting a fallen anniversary the sweep
        // has not got to. Nothing is worked out about anniversaries here.
        Map<Long, NextAnniversaryOfADeposit> nextPay =
                loyalty.whenTheDepositsInAnAccountNextPay(savingsAccountId);

        List<TimelineEvent> events = new ArrayList<>();
        for (PointsExpiringOnADay day : going) {
            events.add(new TimelineEvent(day.on(), TimelineEventKind.POINTS_EXPIRE, day.points()));
        }
        for (Map.Entry<LocalDate, Long> day : arrivingByDay(nextPay).entrySet()) {
            events.add(new TimelineEvent(day.getKey(), TimelineEventKind.LOYALTY_BONUS, day.getValue()));
        }
        events.sort(AS_THE_YEAR_RUNS);

        // One line per read with the window, what was behind it and what each kind came to, so that a
        // bar on somebody's screen can be checked against the log rather than taken on trust. The
        // deposits considered are counted rather than named, and the markers are counted rather than
        // listed, because this runs on every visit to an account: the two modules underneath have
        // already logged their own halves in the same shape, and this line is what ties them together.
        log.debug("what an account has coming savingsAccountId={} from={} until={} deposits={} "
                        + "daysPointsGo={} pointsGoing={} daysBonusesArrive={} pointsArriving={}",
                savingsAccountId, from, until, madeInto.size(),
                going.size(), going.stream().mapToLong(PointsExpiringOnADay::points).sum(),
                events.size() - going.size(),
                events.stream()
                        .filter(event -> event.kind() == TimelineEventKind.LOYALTY_BONUS)
                        .mapToLong(TimelineEvent::points)
                        .sum());
        return new AccountTimeline(from, until, List.copyOf(events));
    }

    /**
     * The anniversaries the account's deposits have coming, gathered onto the days they fall on.
     *
     * <p>Two deposits paying on one day are one marker, because the bar has one position for that
     * day and what a customer reads off it is what the day is worth. Which deposit paid what is the
     * history's answer, one row each, and it is already on the same screen.
     *
     * <p>An anniversary worth nothing is left out rather than drawn as a marker with a zero on it. A
     * deposit holding under ten euros is paid nothing, because a tenth of nine euros rounds down —
     * which is a fact about that deposit, stated beside it in the history along with the rule that
     * explains it, and not a thing that happens on a day. This is the module's one filter and it is
     * the reason {@link TimelineEvent} can promise it is never worth nothing.
     *
     * <p>Sorted by day on the way through, so that the markers are built in the order they will be
     * read even though they are sorted again with the departures afterwards.
     */
    private static Map<LocalDate, Long> arrivingByDay(Map<Long, NextAnniversaryOfADeposit> nextPay) {
        Map<LocalDate, Long> byDay = new TreeMap<>();
        for (NextAnniversaryOfADeposit anniversary : nextPay.values()) {
            if (anniversary.points() > 0) {
                byDay.merge(anniversary.on(), anniversary.points(), Long::sum);
            }
        }
        return byDay;
    }
}
