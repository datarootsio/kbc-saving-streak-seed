package io.dataroots.savingstreak.rewards;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.points.PointsService;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.rewards.WhoAnOfferIsFor.AnEligibilityRule;

import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.ALREADY_WAITING;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.CLOSED;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.NOTHING_LEFT;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.NOT_ENOUGH_POINTS;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.NOT_FOR_YOU;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.NOT_ON_SALE;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.NOT_OPEN_YET;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.NOT_SOLD_OUT;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.NO_SUCH_CUSTOMER;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.NO_SUCH_OFFER;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.THE_HOLD_HAS_LAPSED;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.YOU_ARE_NOT_IN_THAT_QUEUE;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.YOU_HAVE_HAD_YOUR_LIMIT;

/**
 * The Rewards module's face to the rest of the application: what points can be spent on, the claiming
 * of one, and what a customer has claimed already.
 *
 * <p>A customer, not a savings account. Points belong to the person rather than to any one of the
 * accounts they save into, so a claim is theirs to make out of everything they have earned and their
 * claims are one list.
 *
 * <p>It owns what a reward is worth and refuses a claim it cannot honour. It does not own the points:
 * how many somebody has, and which of them leave first, is the Points module's answer, and this
 * module only asks for a number of them and is told whether there were enough.
 *
 * <p><strong>It still reads no other module for the rules it applies, and this is the release in
 * which that stopped being free.</strong> An offer may now say it is for customers on a run of
 * weeks, holding a badge, or who have earned a lifetime of points — three facts owned by
 * Streaks, Challenges and Points respectively, and not one of them fetched from here. They
 * arrive as a {@link CustomerStanding} the web layer assembles and hands in, which is the shape
 * the goals module was built to and the shape {@code CustomerController.accountsOf} already
 * uses. Deciding is then a pure function of that record and the offer's own thresholds, which is
 * {@code WhoAnOfferIsFor}'s, and is the reason eligibility can be exercised without a streak, a
 * badge and a points history being arranged at once.
 *
 * <p>It owns the voucher's life too, which is new — through {@link TheLifeOfAVoucher}, which
 * holds the whole of it and is reached only from here. A claim used to be the end of the story —
 * the application printed six characters and forgot about them — and the methods at the bottom
 * are the other end of it: somebody at a counter reads a code and hands the thing over, once,
 * and somebody running the scheme revokes one that should never have been issued. Using a voucher moves no
 * points and no money, because the points were spent on the day it was claimed. Cancelling one is
 * the single exception in this whole module and the only way points have ever gone <em>back</em>
 * into the ledger from here: a cancellation is the scheme's own mistake, so it refunds — as a
 * fresh batch through the Points module's one door, never as a restoration of the batches that
 * were spent — and the thing goes back in the window with it.
 *
 * <p><strong>Nothing an offer says about itself is stored twice, and this class is where that
 * costs something.</strong> How many of a scarce offer are left is its stock less the claims
 * already made, counted on every read rather than kept in a column — and the reason that is safe
 * to do under two people claiming the last one at the same moment is written down here rather
 * than left lucky. Hikari is pinned to a single connection because SQLite serialises writers
 * anyway ({@code spring.datasource.hikari.maximum-pool-size=1}), and {@link #claim} is
 * {@code @Transactional}: the second claim cannot begin its count until the first has committed
 * its row, so it counts the first one and refuses. Were the pool ever widened, this is the method
 * that would need a lock rather than an argument, and the existing
 * {@code OptimisticLockingFailureException} handler is what would report the loser.
 *
 * <p><strong>It owns holds too, and a hold is the one thing in this module that takes something
 * without taking any points.</strong> A customer puts the last of something aside for
 * seventy-two hours; their balance does not move and every batch in their ledger goes on running
 * its own twelve-month clock. The argument for that way round is written out on
 * {@link #takeAHold} and again on {@link RewardHold}, and its consequence is the thing worth
 * knowing up here: what is left of a scarce offer is now its stock less the claims made against
 * it <em>and</em> less the holds live on it, two tables feeding one subtraction in
 * {@link #whatIsLeftOf}, which is still the only place that arithmetic happens.
 *
 * <p><strong>And it owns the queue, which is the same pipeline one step further back.</strong>
 * A waiting list, a hold and the stock are one mechanism read three ways rather than three
 * features: somebody joins the queue for a thing that has run out, the nightly sweep hands the
 * oldest waiter a <em>hold</em>, and the hold is theirs to convert or to let go. Never a claim
 * — this application does not spend somebody's points without being asked, and a queue that
 * issued vouchers overnight would be doing exactly that. The whole of the new machinery is
 * {@link #joinTheQueue}, {@link #leaveTheQueue} and {@link #promoteWhoeverIsNextInLine}, and
 * the third of those creates holds through the same row and the same seventy-two hours a
 * customer would have got by pressing the button themselves.
 *
 * <p><strong>Nothing tells the promotion that stock came back, and that is the design.</strong>
 * The spec names three ways it returns — an administrator raises it, a hold lapses, a voucher
 * is cancelled — and says the same promotion path serves all three. It does, by not being told
 * about any of them: the sweep reads what is left of every offer somebody is waiting for and
 * hands out whatever it finds. All three routes reach it because all three change the very
 * subtraction {@link #whatIsLeftOf} performs, and none of them has a line of code about queues
 * in it. An event-driven promotion would need three call sites, three orderings and three
 * transactions to reason about, for an answer that is one query.
 *
 * <p><strong>A bundle is the one offer whose claim draws down something other than
 * itself.</strong> Claiming one spends its own price once, checks every member's stock before
 * anything is spent, and issues a single voucher naming what it contains — and the members'
 * stock is drawn down by arithmetic rather than by a row, because the claim row names the
 * bundle. Where that arithmetic lives and why it is not a phantom claim per member is written
 * out on {@link TheBundlesAsTheyStand} and on {@link #whatIsLeftOf}. Nothing else in this class
 * changes shape for it: a bundle is refused for its window, its limits, its rules and its own
 * stock exactly as anything else is, in that order, with its members' stock asked at the stock
 * question and not before.
 *
 * <p><strong>The two met in the merge, and the meeting is in one method.</strong> What is left
 * of an offer is now four terms: its stock, less the claims that name it, less what the bundles
 * containing it have drawn down, less the live holds on it — and a bundle is additionally no
 * more plentiful than the scarcest thing inside it, each member counted the same four ways.
 * {@link #whatIsLeftOf} is where all of it happens, and it is still the only place it happens.
 *
 * <p>The third way a voucher's life ends is {@link #expireVouchersPastTheirDay}, which is the same
 * argument again from the other side: a voucher that outlived its shelf life moves no points
 * either, and this time that is a rule rather than a convenience. It is the only method here that
 * nobody asks for — the scheme's nightly sweep calls it, and {@link TheRewardsSweepRunsNightly} is
 * where the order a night runs in is decided. That it refunds nothing while
 * {@link #cancelTheVoucher} refunds everything is the whole reason a voucher has three ends rather
 * than two, and the two javadocs are written in terms of each other on purpose.
 *
 * <h2>Why two of the three concerns are somewhere else, and the third is still here</h2>
 *
 * <p><strong>This class was about 3,800 lines, and it got that way honestly.</strong> Seven
 * slices were written in parallel across four waves, and every one of them appended a method, a
 * validation block or a parameter to the class that already held the claim. None of those was a
 * bad change on its own; the sum of them was a file holding three separable subjects. Two of the
 * three now live beside it, package-private, behind this same public face:
 *
 * <ul>
 *   <li>{@link TheCatalogueAsItIsRun} — writing an offer, editing one, publishing it, taking it
 *       down, and every rule about what an offer may say about itself. It knows nothing about
 *       claims, holds or queues; it reads those two tables once, for the one rule about an
 *       offer that cannot be decided from the offer.</li>
 *   <li>{@link TheLifeOfAVoucher} — read at a counter, handed over, revoked, expired. It knows
 *       nothing about the catalogue's rules or about whose turn it is.</li>
 * </ul>
 *
 * <p><strong>Nothing outside this module can tell.</strong> Every method those two implement is
 * still declared here, still carries the {@code @Transactional} that opens the transaction, and
 * still throws what it always threw. That is deliberate: a module with one public service has
 * one public service, and a split that made the web layer learn three would have exported an
 * internal decision as an API. The collaborators' methods are package-private and unannotated
 * for a reason worth knowing — Spring's proxy cannot intercept a package-private method, so a
 * {@code @Transactional} written on one would be an annotation that silently does nothing.
 *
 * <p><strong>The reservation pipeline stays, and it stays because it is not a third subject —
 * it is this one.</strong> The obvious reading of the remaining lines is "the claim, and then
 * holds, queues and the sweep", and it is wrong in the one way that matters: a hold, a place in
 * a queue and a claim are the same request asked at three moments. All three run the contract's
 * order — existence, on sale, window, the customer, who it is for, the limit, stock, points
 * last, always — through the one {@link #theOfferTheyMayAskFor}, because the spec says that
 * order is part of the contract and a contract written twice is two contracts. All three settle
 * against the one subtraction in {@link #whatIsLeftOf}, which is stock less claims less what
 * bundles drew down less live holds, and which is the only place that arithmetic happens
 * anywhere. And they run <em>into</em> each other in both directions: a claim reads the
 * claimant's hold, converting a hold <em>is</em> a claim, the promotion step creates holds, and
 * the customer's reading of the catalogue needs the claim counts, the hold counts and the queue
 * positions at once. Cutting between them would mean either duplicating the gauntlet and the
 * subtraction — the two things in this module that must exist exactly once — or extracting them
 * into a third collaborator that both halves call back into, which is three files to say what
 * one file says and a circle drawn through the middle of the only arithmetic here. The seam
 * would be in the wrong place, and the two that were cut are in the right ones.
 */
@Service
public class RewardsService {

    private static final Logger log = LoggerFactory.getLogger(RewardsService.class);

    /** As many characters as the column the words live in declares, and no more. */
    private static final int WORDS_A_CARD_CAN_HOLD = 1000;

    /**
     * The smallest number that is still a cap.
     *
     * <p>One, because a cap of nought is not a limit on an offer — it is an offer nobody may
     * ever claim, said in the one place on the form least likely to be read back. Somebody who
     * typed it meant "no cap", which is an empty box, and a catalogue that accepted it would put
     * a card on every customer's screen permanently locked for a reason none of them could do
     * anything about. Negative numbers are the same mistake with the sign flipped. The floor is
     * named rather than written as a literal for the reason {@code VoucherShelfLife} names its
     * own: the refusal quotes it, and a sentence quoting a number the check does not use is how
     * the two come apart.
     */
    private static final int THE_LEAST_A_CAP_CAN_BE = 1;

    /**
     * A price, a day it starts on and a day it ends on: three, and the offer holds all of them or
     * none of them.
     *
     * <p>Named rather than written as a literal three, because the check that counts them reads
     * as an assertion about a promotion rather than as arithmetic — and because the count and the
     * three {@code null} tests beside it have to agree, which is easier to see when one of them
     * says what it is counting.
     */
    private static final int THE_PARTS_OF_A_PROMOTION = 3;

    /**
     * The day a refusal quotes back, in the zone this application counts calendars in.
     *
     * <p>A day rather than a moment, and a day decided here rather than by whatever machine draws
     * the screen: "used on 14 March" is what somebody at a counter says out loud, and a browser in
     * another zone turning an instant into a date is how a customer gets told the wrong one. The
     * zone is the one the weeks are counted in, named once where it already lives.
     */
    static final ZoneId THE_ZONE_IT_READS_IN = SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN;

    /**
     * The least a bundle can be made of.
     *
     * <p>Two, because one thing in a wrapper is that thing sold again under a second code, at a
     * second price, out of the same stock — which is not a hamper and is not what anybody
     * composing one means. Named rather than written as a literal for the reason
     * {@link #THE_LEAST_A_CAP_CAN_BE} is: the refusal quotes it, and a sentence quoting a number
     * the check does not use is how the two come apart.
     */
    private static final int THE_LEAST_A_BUNDLE_CAN_CONTAIN = 2;

    /** The least of a member one bundle can contain, for the same reason and read the same way. */
    private static final int THE_LEAST_OF_A_MEMBER = 1;

    private final RewardOfferRepository offers;
    private final OfferMemberLineRepository memberLines;
    private final RedemptionRepository redemptions;
    private final RewardHoldRepository holds;
    private final WaitingListRepository waitingList;
    private final AccountsService accounts;
    private final PointsService points;

    /**
     * Where a waiter stands, asked of whatever can answer it — never of Streaks, Challenges or
     * Points directly.
     *
     * <p>Injected as the interface and never as the thing behind it, which is the arrangement
     * {@code AccountsService} already keeps with its own inbound port and the reason this class
     * still imports nothing it did not import before. Only the promotion step reads it, and
     * only because that step is the one caller which cannot be handed a standing in advance:
     * the argument is written out in full on {@link WhereAWaiterStands}.
     */
    private final WhereAWaiterStands standings;

    /** Whatever tells a customer something, for the one thing this module has to say. */
    private final TellingAPromotedWaiter tellThem;

    private final Clock clock;

    /**
     * Running the catalogue: writing an offer, editing one, publishing it, taking it down.
     *
     * <p>A collaborator rather than nine hundred more lines here, and package-private rather
     * than a second public service. The argument for the seam and for keeping it invisible from
     * outside is on {@link TheCatalogueAsItIsRun}; the methods that delegate to it keep their
     * {@code @Transactional} here, because this is the bean the proxy wraps and the boundary has
     * not moved.
     */
    private final TheCatalogueAsItIsRun catalogue;

    /**
     * The voucher's life after it is issued: read at a counter, handed over, revoked, expired.
     *
     * <p>The second collaborator, on the same terms as the first. The argument for the seam is
     * on {@link TheLifeOfAVoucher}; what stays here is the claim that issues a voucher and the
     * reservation pipeline that decides who may make one, because those two are one concern and
     * the paragraph on this class says why.
     */
    private final TheLifeOfAVoucher vouchers;

    RewardsService(RewardOfferRepository offers, OfferMemberLineRepository memberLines,
                   RedemptionRepository redemptions, RewardHoldRepository holds,
                   WaitingListRepository waitingList, AccountsService accounts,
                   PointsService points, WhereAWaiterStands standings,
                   TellingAPromotedWaiter tellThem, Clock clock,
                   TheCatalogueAsItIsRun catalogue, TheLifeOfAVoucher vouchers) {
        this.offers = offers;
        this.memberLines = memberLines;
        this.redemptions = redemptions;
        this.holds = holds;
        this.waitingList = waitingList;
        this.accounts = accounts;
        this.points = points;
        this.standings = standings;
        this.tellThem = tellThem;
        this.clock = clock;
        this.catalogue = catalogue;
        this.vouchers = vouchers;
    }

    /**
     * Everything points can be spent on, cheapest first. The same list for every customer.
     *
     * <p>Rows now, read in the order they were written. The four the application has always had are
     * seeded cheapest first by {@link RewardsOnStartUp}, so what leaves here is the same four codes
     * at the same four prices in the same order the enum served them in — which is the one thing
     * about this change a customer could notice and the one thing it must not do.
     *
     * <p><strong>Published rows only, and this is the slice that made that a real filter.</strong>
     * Until somebody could write an offer there was nothing that was not published, and the
     * previous slice said so in this javadoc rather than filtering for a state nothing could be
     * in. Now a draft is a half-written reward and a withdrawn offer is one somebody has taken
     * down on purpose, and either appearing here would be the administration screen leaking onto a
     * customer's. The four seeded entries are published, so what leaves here is still the same
     * four codes at the same four prices in the same order.
     *
     * <p>Filtered in the query rather than over the answer, because leaving a draft out is not a
     * convenience: it is the promise the draft state exists to make, and {@link #claim} makes the
     * same check again at the moment it matters so that the promise does not rest on what a page
     * happened to be shown.
     *
     * <p><strong>The price each entry carries is the ordinary one, and no clock is read
     * here.</strong> The promotion slice first made this quote the effective price by reading the
     * clock, and that has been taken back out: this address is customer-free <em>and</em>
     * clock-free — it deliberately does not filter by an offer's window either, because the
     * window is the customer-shaped reading's business — and an address that read the clock for
     * the price while ignoring it for availability would be inconsistent with itself. Every
     * "today" a promotion implies belongs on {@link #catalogueFor}, which has a day, a person,
     * and room to say both figures and when the sale ends.
     *
     * <p>The four seeded entries carry no promotion in any case, so this still serves the same
     * four codes at the same four prices in the same order, on every day there has ever been.
     */
    public List<ARewardOnOffer> catalogue() {
        return offers.findAllByStateOrderByIdAsc(OfferState.PUBLISHED).stream()
                .map(RewardOffer::onOffer).toList();
    }

    /**
     * The same catalogue, in the same order, read <em>for one customer as of right now</em>: every
     * published offer with whether they can claim it this minute and, when they cannot, the single
     * reason why.
     *
     * <p><strong>A second read beside {@link #catalogue} rather than a customer bolted onto
     * it.</strong> That one is the catalogue with nobody in it and it must go on saying exactly
     * what it has always said, to the character, because the test that pins it is the strongest
     * rail on this feature. This one is the read the rewards page actually wants, and the shape
     * every slice after this one fills in: stock, the limits somebody has already used, the rules
     * they do or do not meet. It starts from the same query — published only — so a draft is never
     * shown even locked, and an offer somebody withdrew this morning leaves the page rather than
     * sitting there explaining itself. "Locked with the reason" is for the rules an offer carries,
     * not for an offer nobody is selling at all.
     *
     * <p><strong>Locked, never hidden, and that is the whole of the customer-facing
     * decision.</strong> An offer that disappears teaches nobody anything, and every rule this
     * feature adds is a rule the scheme wants somebody to satisfy: come back on the third, save a
     * bit more, hold a streak. The frontend already renders an offer somebody cannot afford as
     * greyed rather than absent, and this is the same treatment with the backend's sentence in it
     * rather than the page's arithmetic.
     *
     * <p><strong>One day for the whole list.</strong> The clock is read once, here, rather than
     * once per row: a list long enough to cross midnight half way down would show one customer two
     * different todays and lock one offer while unlocking another for reasons nobody could
     * reconstruct.
     *
     * <p><strong>The customer is named, and this is the release in which something finally reads
     * them.</strong> The window is the same for everybody; who an offer is for is not, and the
     * slices after this one add two more answers that depend on the person — how many of
     * something they have already had, and how much of the stock is theirs. An address without
     * the customer in it would have had to be replaced rather than filled in, and every page
     * pointing at it moved. The identifier is in the log line for the same reason.
     *
     * <p><strong>The standing is handed in, exactly once, for the whole list.</strong> It arrives
     * as a parameter rather than being fetched, because this module reads no other module and the
     * facts a rule needs — a run of weeks, a trophy case, a lifetime of points earned — belong to
     * three of them. The argument is written out on {@link CustomerStanding}. That it is one
     * standing for the whole reading rather than one per offer is the same argument the day above
     * makes: a list that asked again per row could show one customer two different streaks half
     * way down and lock one offer while unlocking another for reasons nobody could reconstruct.
     * It is also three queries instead of three per row.
     *
     * <p>Nothing here refuses for a customer nobody has heard of. That is a fact about the address
     * rather than about the catalogue — the read is the same list whoever asks — so it is answered
     * where the other customer-scoped reads answer it, in the controller that owns the path, and
     * it is answered <em>before</em> a standing is assembled.
     */
    @Transactional(readOnly = true)
    public List<AnOfferAsACustomerReadsIt> catalogueFor(long customerId, CustomerStanding standing) {
        LocalDate today = theDayItIs();
        // And the moment, beside the day and read from the same clock, because a hold is
        // seventy-two hours rather than a number of days: whether one is still live is a
        // question the calendar cannot answer. Taken once for the whole reading, exactly as the
        // day is and for the same reason — a list judged against a moving moment could show one
        // card a hold that another card's arithmetic had already let lapse.
        Instant now = theMomentItIs();
        // One pass over this customer's claims for the whole list, taken before the first row is
        // read, for the same reason the day above is taken once: every offer in one reading has
        // to be judged against one set of facts about the person. A count per offer inside the
        // map below would be a query per card, and two of them could disagree about a claim made
        // while the page was being drawn.
        Map<String, HowManyHaveGone> whatHasGone =
                whatHasGoneOfEach(customerId, today);
        // And two more passes, for the two halves of what holds do to a reading: what everybody
        // is holding, which comes off the stock of every card, and what this customer is
        // holding, which is a countdown on one of them. Two queries rather than one, for the
        // reason written on HowManyAreHeld: they are different questions about different rows
        // and only one of them mentions the person asking.
        Map<String, HowManyAreHeld> heldByAnybody = whatIsBeingHeld(now);
        Map<String, AHoldOfYourOwn> theirOwn = whatTheyAreHolding(customerId, today, now);
        // And one pass over the catalogue's member lines, taken once for the whole list for the
        // same reason: what is left of an offer now depends on every bundle that contains it, so
        // even a page with no bundle on it has to have asked. An empty answer — which is every
        // database this application has ever written — costs one query and changes no figure.
        // The holds go in with it, because a member of a bundle that somebody is holding is not
        // available to that bundle either; the grouped count above is handed over rather than
        // taken again, so the figure a card is drawn with and the figure a member is charged
        // are one query's answer.
        TheBundlesAsTheyStand bundles = theBundlesAsTheyStand(whatHasGone, () -> heldByAnybody);
        // And where this customer stands in whatever queues they are in, taken once for the
        // whole list like everything above it. Their own rows are a handful at most and almost
        // always none, so the ordinary reading costs one query that comes back empty — the same
        // bargain the bundle fold strikes, and for the same reason: a figure per card would be
        // a query per card and two of them could disagree about somebody who left the queue
        // while the page was being drawn.
        Map<String, Integer> theirPlaces = whereTheyStandInEveryQueue(customerId);
        List<AnOfferAsACustomerReadsIt> reading =
                offers.findAllByStateOrderByIdAsc(OfferState.PUBLISHED).stream()
                        .map(offer -> howItReadsOn(offer, knownAbout(offer, today, standing,
                                theCountOf(whatHasGone, offer.code()),
                                whatIsHeldOf(heldByAnybody, offer.code()),
                                theirOwn.get(offer.code()), theirPlaces.get(offer.code()),
                                bundles)))
                        .toList();
        // Counts and the day rather than a line per row: this runs on every visit to the rewards
        // tab, and one line per offer would bury the business events in a page load. Which offer
        // was locked and against what is a line of its own, below, and only for the ones that were.
        // The standing is on this line and not on each locked row, because it is one reading of
        // one customer and repeating it per offer would be the same four figures over and over.
        log.debug("catalogue read for a customer customerId={} today={} offers={} locked={} "
                        + "pointsBalance={} lifetimePointsEarned={} streakWeeks={} badgesHeld={}",
                customerId, today, reading.size(),
                reading.stream().filter(entry -> !entry.claimable()).count(),
                standing.pointsBalance(), standing.lifetimePointsEarned(), standing.streakWeeks(),
                standing.badgesHeld());
        return reading;
    }

    /**
     * Where one offer stands on one day: the first thing in the way, or nothing at all.
     *
     * <p><strong>The order of the questions is the contract, and this method is where it is
     * kept.</strong> The spec fixes it — existence, on sale, the window, eligibility, the limit,
     * stock, and points last, always — and the two ends of it are already settled elsewhere:
     * existence and being on sale are the query above, and being short of points is the page's own
     * arithmetic against a balance it already has. What is left is the middle, and this release
     * implements the first of it. A slice adding a reason adds an {@code if} in its place in that
     * order rather than at the end, because the order is what makes the single reason the
     * <em>right</em> single reason: a customer told the cheapest objection first goes away and
     * satisfies it, and is then told the next one.
     *
     * <p>Warn is not used here and that is deliberate. Nothing has been refused — nobody asked for
     * anything — so a locked card is a reading rather than a rejection, and a log that warned
     * about every greyed-out offer on every page load would drown the refusals that matter.
     */
    private AnOfferAsACustomerReadsIt howItReadsOn(RewardOffer offer, WhatIsKnownToday known) {
        LocalDate today = known.today();
        if (!offer.hasOpenedBy(today)) {
            return locked(offer, WhyAnOfferIsLocked.NOT_OPEN_YET, itOpensOn(offer), known);
        }
        if (offer.hasClosedBy(today)) {
            return locked(offer, WhyAnOfferIsLocked.CLOSED, itClosedOn(offer), known);
        }
        // Eligibility, immediately after the window and before everything below it. Who somebody
        // is does not become true by waiting, and a rule about it asked after the limit or the
        // stock would have the page telling a customer to come back for a thing that was never
        // theirs.
        CustomerStanding standing = known.standing();
        Optional<AnEligibilityRule> ruleInTheWay =
                offer.whoItIsFor().theFirstRuleNotMetBy(standing);
        if (ruleInTheWay.isPresent()) {
            theRuleAndTheStandingBehindIt(offer, standing, ruleInTheWay.get());
            return locked(offer, WhyAnOfferIsLocked.NOT_FOR_YOU,
                    itIsNotForYou(offer, standing, ruleInTheWay.get()), known);
        }
        // The limit sits here: after eligibility, and before where stock will. Being over a cap
        // is a fact about this customer rather than about the world, so it is asked once the
        // offer itself has been found to be on and open and theirs at all — and it is asked
        // before anything about how many are left, because a customer who may not have another
        // is not helped by being told the last one has gone. Points are not asked here at all:
        // the page holds the balance and the price and greys the button against the two of them,
        // and being short is said last, always.
        //
        // The lifetime cap before the weekly one, and that order is the same argument the whole
        // list is written on. Somebody at both is told the one they can do nothing about, rather
        // than being sent away to come back on Monday for something they may never have again.
        // Their own hold, above both of the questions below it, because both would get the
        // wrong answer for them. The stock question would: their hold is one of the ones
        // subtracted from what is left, so the very customer the last one is being kept for is
        // the one whose card would read "sold out". And the limit question would: a live hold
        // counts against a cap, so somebody at a cap of one who is holding one would be locked
        // out of the thing being kept for them by the rule that let them keep it. A hold is an
        // affordance rather than a lock — the argument is on AnOfferAsACustomerReadsIt and on
        // RewardOffer.heldByThem — so the reading stops here, claimable, with the moment it runs
        // out on it, and the two questions are asked of everybody who is not holding one.
        if (known.theyHoldOne()) {
            log.debug("offer is being held for this customer reward={} lapsesAt={} today={} "
                            + "whatIsLeftToAnybodyElse={} everHad={}",
                    offer.code(), known.theirHoldLapsesAt(), today, known.whatIsLeft(),
                    known.haveGone().everHad());
            return offer.heldByThem(known);
        }
        //
        // The figures the two questions are asked of are WhatIsKnownToday's, where the argument
        // for counting a live hold at all is written out. Nobody reaching this line is holding
        // one of this offer, so the two sums are this customer's claims — the hold is counted
        // where it can actually stand in somebody's way, which is a claim or a second hold.
        HowManyHaveGone haveGone = known.haveGone();
        if (offer.theyHaveHadTheirLifetimeLimit(known.everHadOrHolds())) {
            return theirLimit(offer, known,
                    theyHaveHadAllTheyMayEverHave(offer, haveGone, known.theyHoldOne()));
        }
        if (offer.theyHaveHadTheirLimitThisWeek(known.hadOrHoldsThisWeek())) {
            return theirLimit(offer, known, theyHaveHadAllTheyMayHaveThisWeek(offer, haveGone,
                    known.theyHoldOne() && known.theirHold().takenThisWeek()));
        }
        // Stock is asked after everything about the offer's own availability and after
        // everything about the customer, and immediately before the price — which is the
        // position the order gives it and the position it has to keep. Being sold out is the
        // last fact that is true of the offer rather than of the person, so it is the last thing
        // that can be said before "you are short", and saying it in the wrong order would send
        // somebody saving for something nobody can have. The figure itself was counted once, for
        // the whole reading, and travels on the parameter object.
        if (theLastOneHasGone(known.whatIsLeft())) {
            return locked(offer, WhyAnOfferIsLocked.NOTHING_LEFT, nothingIsLeftOf(offer, known),
                    known);
        }
        return offer.claimable(known);
    }

    /**
     * One offer locked because of this customer's own history, and the figures behind it.
     *
     * <p>A line of its own beside the one {@link #locked} writes, rather than widening that one,
     * and the duplication is the point. That line carries the day and the window, which is the
     * whole of what decides a lock everybody shares; this one carries the two caps and the two
     * counts, which is the whole of what decides a lock that is this person's alone — and a
     * customer complaining that somebody else can see a card they cannot is answered by exactly
     * these four numbers and by nothing less than all four. Two DEBUG lines for one greyed card
     * is a bargain at DEBUG, where a reviewer is reading the reasoning rather than the traffic.
     */
    private AnOfferAsACustomerReadsIt theirLimit(RewardOffer offer, WhatIsKnownToday known,
                                                 String inWords) {
        HowManyHaveGone haveGone = known.haveGone();
        // Both figures, the claims on their own and the claims with the hold folded in, because
        // a customer locked by a cap they have only reached by holding one is a complaint this
        // line has to be able to answer — and "everHad=1 mayStillHave=0" with no mention of a
        // hold would look like arithmetic that had gone wrong.
        log.debug("offer locked by a limit reward={} maxPerCustomer={} everHad={} "
                        + "maxPerCustomerPerWeek={} hadThisWeek={} holdingOne={} "
                        + "everHadOrHolds={} hadOrHoldsThisWeek={} today={} mayStillHave={}",
                offer.code(), offer.maxPerCustomer(), haveGone.everHad(),
                offer.maxPerCustomerPerWeek(), haveGone.thisWeek(), known.theyHoldOne(),
                known.everHadOrHolds(), known.hadOrHoldsThisWeek(), known.today(),
                offer.howManyMoreTheyMayHave(known.everHadOrHolds(),
                        known.hadOrHoldsThisWeek()));
        return locked(offer, WhyAnOfferIsLocked.YOU_HAVE_HAD_YOUR_LIMIT, inWords, known);
    }

    /**
     * One locked offer, and the line in the log that says what locked it and against what.
     *
     * <p>The inputs behind the decision rather than only the decision: a customer complaining that
     * something they could see yesterday has gone grey is answered by the day the application
     * thought it was and the two days on the offer, and by nothing less than all three.
     */
    private AnOfferAsACustomerReadsIt locked(RewardOffer offer, WhyAnOfferIsLocked why,
                                             String inWords, WhatIsKnownToday known) {
        log.debug("offer locked for a customer reward={} locked={} today={} opensOn={} closesOn={}",
                offer.code(), why, known.today(), offer.opensOn(), offer.closesOn());
        // How many are left is on a locked card too — a card locked for its window is still a
        // scarce card, and a customer told to come back on the third deserves to know there are
        // only three of them. This slice originally counted it again here rather than widening
        // the signature, because a parameter would have made every lock that has nothing to do
        // with stock carry a figure it never looks at; with the facts arriving as one
        // WhatIsKnownToday that argument no longer applies, so the figure is taken off the
        // object and the extra count is gone.
        return offer.lockedBecause(why, inWords, known);
    }

    /**
     * What one offer costs on one day, said in the log as well as worked out.
     *
     * <p>DEBUG and not INFO, because reading a price is not a business event — it happens on
     * every page load and once more for every claim. What makes it worth a line at all is that a
     * customer complaining they were charged the wrong figure is answered by the day the
     * application thought it was and the three columns it read, and by nothing less: "the
     * promotion had ended" and "the promotion never started" look identical from the outside and
     * are the same sentence apart in here.
     */
    private long whatItCostsOn(RewardOffer offer, LocalDate today) {
        long cost = offer.costOn(today);
        if (offer.discountedCostInPoints() != null) {
            log.debug("offer priced reward={} today={} costInPoints={} ordinaryCostInPoints={} "
                            + "discountedCostInPoints={} discountOpensOn={} discountClosesOn={} "
                            + "onPromotion={}",
                    offer.code(), today, cost, offer.costInPoints(),
                    offer.discountedCostInPoints(), offer.discountOpensOn(),
                    offer.discountClosesOn(), offer.isOnPromotionOn(today));
        }
        return cost;
    }

    /**
     * Spends the reward's cost out of the customer's points and issues the voucher for it.
     *
     * <p>Both happen in one transaction, and that is the load-bearing guarantee of this slice: points
     * spent on nothing would be a balance nobody could explain, and a voucher nobody paid for would
     * be worse. There is no reversal — the requirement is that a claim is instant and final — so the
     * only protection against a half-made claim is that a half-made one cannot be committed.
     *
     * <p>The reward is named as text, because a code is what a page sends and what a claim is
     * stored under. Looking it up is now a read of the catalogue table rather than a walk over a
     * list of constants, and it happens first, ahead of the customer and well ahead of the points:
     * whether the catalogue has ever heard of a code is a question that needs neither, and it is
     * the order the request was already answered in when the web layer did the parsing.
     *
     * <p>The customer is checked before the points are, for the same reason a deposit checks its
     * accounts before its amount: if there is no such customer, what they can afford is beside the
     * point. The points come last, always, so that "you are short" is only ever said when being
     * short is genuinely the only thing wrong.
     *
     * <p>Whether it is on sale is asked second, of the row that was just read, and never of the
     * list the page was drawn from. A draft is not in the catalogue and a withdrawn offer has left
     * it, so an honest page never offers either — but a page that has been open since before an
     * offer was taken down is exactly how this arrives, and the check that matters is the one made
     * at the moment the points would be spent.
     *
     * <p><strong>The standing is handed in, and it is read after the customer has been vouched
     * for and never before.</strong> The module reads no other module, so the facts an
     * eligibility rule needs arrive as a parameter from the web layer, exactly as they do for the
     * reading above; the argument is on {@link CustomerStanding}. What a caller must not conclude
     * from that is that the standing decides whether the customer exists — a standing assembled
     * for somebody nobody has heard of says nothing is known, and the sentence such a request
     * gets back has to be "there is no such customer" rather than "you are not eligible". Hence
     * the order below, and hence the check that reads it sits underneath the one that vouches for
     * them.
     *
     * @throws RewardRefused if the catalogue has nothing under that code, if the offer is not on
     *         sale, if the offer is outside its window, if there is no such customer, if the
     * <p>Being sold out is asked immediately before the points and against a count taken inside
     * this transaction. Two people claiming the last one at the same moment is the case that
     * decides whether deriving what is left was safe, and the answer is that it is: the
     * connection pool holds one connection, so the second claim cannot start counting until the
     * first has committed the row it is about to count.
     *
     * @throws RewardRefused if the catalogue has nothing under that code, if the offer is not on
     *         sale, if the offer is outside its window, if there is no such customer, if the
     *         offer is not for them, if they have had their limit, if there are none of it left,
     *         or if they cannot afford the reward
     */
    @Transactional
    public ClaimedReward claim(long customerId, String rewardCode, CustomerStanding standing) {
        return claimIt(customerId, rewardCode, standing, false);
    }

    /**
     * Turns a hold into the claim it was being kept for: the points are spent at this moment, at
     * the price in force at this moment, and the voucher is issued.
     *
     * <p><strong>An address of its own rather than the ordinary claim noticing a hold, and the
     * reason is the refusal.</strong> An ordinary claim of something a customer used to hold and
     * no longer does is a perfectly good request: if there is stock, they get one. A
     * <em>conversion</em> of a hold that lapsed is not, and it has to be told so in those words —
     * "your hold ran out" rather than "sold out" or, worse, a voucher for the last one somebody
     * else was about to be promoted into. The two requests mean different things and they get
     * different answers, which is why they are two methods.
     *
     * <p><strong>The price is today's and not the price on the day the hold was taken.</strong>
     * The spec fixes this and it is worth being plain about the consequence in both directions:
     * a customer who took a hold last week and converts during this week's promotion pays the
     * sale price, and one who took a hold during a promotion that has since ended pays the
     * ordinary price. A price frozen onto a hold would be this application quoting a figure the
     * catalogue no longer charges, for up to three days, and the alternative — refusing to
     * convert at a changed price — would make a promotion into something that cancelled
     * reservations.
     *
     * <p><strong>Everything else is the ordinary claim, gauntlet and all.</strong> A hold is not
     * a licence: an offer withdrawn while it was held, a window that closed, a rule the customer
     * no longer meets and a cap they have since reached all still refuse, in the same order and
     * in the same words. What a hold buys is the stock question and nothing else, because the
     * stock question is the only one it was ever an answer to.
     *
     * @throws RewardRefused if there is no live hold to convert, or for any reason an ordinary
     *         claim of the same offer would be refused other than there being none left
     */
    @Transactional
    public ClaimedReward convertAHold(long customerId, String rewardCode,
                                      CustomerStanding standing) {
        return claimIt(customerId, rewardCode, standing, true);
    }

    /**
     * The claim, whether it came from the catalogue or out of a hold.
     *
     * <p>One method for both, because they are the same sequence of questions asked in the same
     * order and differ in exactly two places: whether a live hold is required before anything
     * else about the customer is looked at, and whether the stock question is asked at all. Two
     * copies of a check order the spec calls a contract would be two orders within a fortnight.
     *
     * @param mustBeHolding true when this is a conversion, in which case the absence of a live
     *                      hold is itself the refusal
     */
    private ClaimedReward claimIt(long customerId, String rewardCode, CustomerStanding standing,
                                  boolean mustBeHolding) {
        LocalDate today = theDayItIs();
        RewardOffer reward = theOfferTheyMayAskFor(WhatWasAskedFor.A_CLAIM, customerId, rewardCode,
                standing, today);
        // The hold, read here because everything below it needs to know whether there is one:
        // the limit does not count it against them when it is the very thing they are claiming,
        // and the stock question is not asked of somebody claiming the one already set aside for
        // them. Read inside the transaction and against the clock, never from the reading a page
        // was drawn from, because a page open since before a hold ran out is exactly how the
        // refusal below arrives.
        Instant now = theMomentItIs();
        RewardHold theirHold = holds.theLiveHoldOneCustomerHasOn(customerId, reward.code(),
                HoldState.HELD, now).orElse(null);
        if (mustBeHolding && theirHold == null) {
            throw refusingForTheMissingHold(customerId, reward, now);
        }
        // The limit, in the same place and the same order the card was greyed in, and counted
        // through the same query — which is what makes the sentence below word for word the one
        // the customer has been reading on the card. After the customer and after eligibility,
        // because a limit is a fact about a person and there is no point counting the claims of
        // somebody who does not exist or who was never allowed this at all; before the points,
        // because being short is said last, always, and telling somebody to save forty more for
        // a thing they may never have again is the exact failure that order exists to prevent.
        //
        // A live hold counts against a cap — that is the rule, and the card has been saying so —
        // but the hold being converted must not count against its own conversion. Somebody at a
        // cap of one who is holding one has reached it as far as taking another is concerned,
        // and has reached nothing at all as far as claiming that one is concerned; counting it
        // here would be an offer refusing to hand over the thing it had set aside.
        //
        // The whole grouped count is kept rather than only this offer's row, because the bundle
        // arithmetic below is worked out from every offer's claims and not from one of them.
        Map<String, HowManyHaveGone> whatHasGone =
                whatHasGoneOfEach(customerId, today);
        HowManyHaveGone haveGone = theCountOf(whatHasGone, rewardCode);
        boolean aHoldThatIsNotBeingConverted = theirHold != null && !mustBeHolding;
        long everHadOrHolds = haveGone.everHad() + (aHoldThatIsNotBeingConverted ? 1 : 0);
        long hadOrHoldsThisWeek = haveGone.thisWeek()
                + (aHoldThatIsNotBeingConverted && takenInTheWeekOf(theirHold, today) ? 1 : 0);
        if (reward.theyHaveHadTheirLifetimeLimit(everHadOrHolds)) {
            throw refusingForALimit(WhatWasAskedFor.A_CLAIM, customerId, reward, haveGone,
                    theyHaveHadAllTheyMayEverHave(
                            reward, haveGone, aHoldThatIsNotBeingConverted));
        }
        if (reward.theyHaveHadTheirLimitThisWeek(hadOrHoldsThisWeek)) {
            throw refusingForALimit(WhatWasAskedFor.A_CLAIM, customerId, reward, haveGone,
                    theyHaveHadAllTheyMayHaveThisWeek(reward, haveGone,
                            aHoldThatIsNotBeingConverted && takenInTheWeekOf(theirHold, today)));
        }
        // Stock last of the offer's own rules and immediately before the money, for the reason
        // written out on howItReadsOn: it is the last fact that is about the thing rather than
        // about the person, and "you are forty points short" said about something that sold out
        // last week is the one sentence the whole order exists to prevent. The count is taken
        // here, inside the transaction, rather than trusted from the reading the page was drawn
        // from — a page open since before the last one went is exactly how this refusal arrives.
        //
        // Not asked at all of a conversion, and that is the whole of what a hold buys. The
        // held one is not in the window and was never being taken out of it: it has been this
        // customer's since they took it, and asking would refuse the one person it is being
        // kept for.
        //
        // It is still asked of an ordinary claim by somebody who happens to be holding one,
        // because those are two acquisitions rather than one — the spec is explicit that a live
        // hold counts while it is live — and the answer is very likely no, since their own hold
        // is one of the ones subtracted. That is the right answer said in the wrong words by
        // default, so it gets words of its own: the only one left is the one they are holding,
        // and there is a button for that.
        //
        // A bundle's members are asked here and nowhere else, because being unable to make one
        // up is being sold out — the same lock, the same kind and the same position in the
        // order. What is left of a bundle is already the least of its own stock and what each
        // member allows, so the line below refuses a hamper whose popcorn has run out without
        // knowing that popcorn exists; which member it was is on the object, for the sentence.
        // The whole reading is assembled here rather than the figure alone, so that the words
        // this refusal carries are literally the words the card was drawn with — and it is
        // assembled whether or not the question is asked, because the line that records which
        // members a claim drew down runs on both paths.
        //
        // The two slices meeting here leave one case asked of nobody: a conversion of a hold on
        // a bundle whose members have run out since the hold was taken. A hold reserves the
        // bundle and cannot reserve its members — there is no row that would say so — and a
        // conversion asks no stock question at all, by the design above. It is left as it is
        // deliberately rather than by omission: refusing a conversion for a member is the one
        // sentence a hold exists to make impossible, and the alternative is the queue slice's
        // to weigh up when it arrives.
        TheBundlesAsTheyStand bundles =
                theBundlesAsTheyStand(whatHasGone, () -> whatIsBeingHeld(now));
        // No place in a queue, and none is wanted. A claim is refused or issued on the offer,
        // the customer and the stock; where somebody stands in a line is a thing a card says
        // and never a thing a rule reads, so the reading a claim builds leaves it out exactly
        // as it leaves out the hold it is not judging.
        WhatIsKnownToday known = knownAbout(reward, today, standing, haveGone,
                whatIsHeldOf(reward, now), null, null, bundles);
        if (!mustBeHolding && theLastOneHasGone(known.whatIsLeft())) {
            throw refusing(WhatWasAskedFor.A_CLAIM, customerId, rewardCode, NOTHING_LEFT,
                    theirHold == null
                            ? nothingIsLeftOf(reward, known) : theOnlyOneLeftIsYourOwn(reward));
        }
        // What it costs today, which is the discounted figure while a promotion is running and
        // the ordinary one otherwise. Asked once and used for all three of the things a price is
        // for — what they can afford, what the refusal quotes back at them, and what is actually
        // taken — because a claim that checked one figure and spent another would be the only
        // kind of pricing bug a customer never sees until their balance is wrong.
        long cost = whatItCostsOn(reward, today);
        if (!points.spend(customerId, cost)) {
            // Read after the refusal rather than before the attempt: nothing was taken, so this is
            // still what they have, and it is the figure the person needs in order to know how much
            // more saving stands between them and this reward.
            throw refusing(WhatWasAskedFor.A_CLAIM, customerId, rewardCode, NOT_ENOUGH_POINTS,
                    reward.title() + " costs " + cost + " points, and you have "
                            + points.balanceOf(customerId) + ".");
        }
        // One moment for the spend and the voucher, read from the application's clock and truncated
        // the way a deposit's is, so that the moment reported back is the same moment the claim is
        // later listed under — and so that a clock wound forward moves claims along with deposits.
        Instant clockReads = clock.instant();
        Instant claimedAt = clockReads.truncatedTo(ChronoUnit.MILLIS);
        log.debug("claim takes its moment from the application clock customerId={} "
                + "clockReads={} recordedMoment={}", customerId, clockReads, claimedAt);

        LocalDate runsOutOn = theDayAVoucherFromThisRunsOutOn(reward, claimedAt);

        ClaimedReward claimed = redemptions
                .save(Redemption.issue(customerId, reward.code(), reward.title(),
                        reward.voucherPrefix(), cost, claimedAt, runsOutOn))
                .asClaimed();
        // The price actually charged, and beside it the ordinary one and whether a promotion is
        // what made them differ. A claim is the business event a promotion exists to move, and a
        // line carrying only "pointsSpent=125" leaves nobody able to say afterwards whether that
        // was the sale price or somebody repricing the catalogue by hand.
        log.info("claim issued redemptionId={} customerId={} reward={} pointsSpent={} "
                        + "ordinaryCostInPoints={} onPromotion={} claimedAt={} expiresOn={} "
                        + "fromAHold={}",
                claimed.id(), customerId, claimed.rewardCode(), cost, reward.costInPoints(),
                reward.isOnPromotionOn(today), claimed.claimedAt(), claimed.expiresOn(),
                mustBeHolding);
        // And the hold is spent, last, after the voucher exists. A hold marked converted with no
        // voucher behind it would be a customer who had lost their reservation and gained
        // nothing; the order here and the one transaction around the whole method are what make
        // that impossible.
        //
        // Only a conversion spends it. An ordinary claim by somebody who also holds one has
        // taken a different one out of the window — the stock question above was asked of them
        // and answered yes — and their hold goes on being theirs for as long as it has left. A
        // claim that quietly consumed a hold nobody had asked it to would be this application
        // deciding which of two things somebody meant.
        if (mustBeHolding) {
            theirHold.converted(claimedAt);
            holds.save(theirHold);
            log.info("hold converted holdId={} customerId={} reward={} pointsSpent={} "
                            + "redemptionId={} voucher={} takenAt={} lapsesAt={} convertedAt={}",
                    theirHold.id(), customerId, theirHold.offerCode(), cost, claimed.id(),
                    claimed.voucherCode(), theirHold.takenAt(), theirHold.lapsesAt(), claimedAt);
        }
        // And the members, on both paths. A bundle claimed out of a hold draws its members down
        // exactly as one claimed off the card does — the claim row is the same row and the
        // arithmetic reads the same table — so the line that records which of them went has to
        // be written here, below both branches, rather than inside either of them.
        theMembersThisClaimDrewDown(customerId, claimed, known, bundles);
        return claimed;
    }

    /**
     * Whether a hold was taken inside the savings week a reading is being taken in.
     *
     * <p>The week the day belongs to, worked out from the day rather than from the clock, so
     * that this and the claim counts beside it can never be counting two different Mondays. The
     * argument for the savings week being the week at all is on
     * {@link #whatHasGoneOfEach}.
     */
    private static boolean takenInTheWeekOf(RewardHold hold, LocalDate today) {
        SavingsWeek week = SavingsWeek.containing(today);
        return !hold.takenAt().isBefore(week.startsAt()) && hold.takenAt().isBefore(week.endsAt());
    }

    /**
     * How many of one offer are being held, for the paths that are about one offer rather than
     * about a whole reading of the catalogue.
     *
     * <p>Its own count rather than the grouped query the reading uses, matching what
     * {@code countByReward} is to the grouped claim count beside it: a claim and an
     * administrator's edit each ask about one code and have no reading to take a figure out of.
     */
    private HowManyAreHeld whatIsHeldOf(RewardOffer offer, Instant now) {
        return new HowManyAreHeld(offer.code(),
                holds.howManyAreHeldOf(offer.code(), HoldState.HELD, now));
    }

    /**
     * Every refusal says why in the log as well as to whoever asked, because only one of the two is
     * kept. Worth having now that points expire: a claim turned down for want of points used to mean
     * somebody had not saved enough yet, and can now mean a batch went stale overnight — and the log
     * is where those two are told apart.
     */
    /**
     * What day it is, where this application counts its days.
     *
     * <p>Off the injected clock, never {@code LocalDate.now()}: a trainer winding the development
     * clock forward has to see a season open and close, and a rule that read the machine's own
     * clock would be the one thing in this application a wound clock could not move.
     *
     * <p>Converted through the zone rather than truncated off the instant, for the reason
     * {@link SavingsWeek} gives about a week and {@code Campaign} repeats about a season: a moment
     * at half past midnight in Brussels is the previous day in UTC, and a window read off the
     * clock's own reading would open a day late for anybody who looked before one in the morning.
     */
    private LocalDate theDayItIs() {
        return LocalDate.ofInstant(clock.instant(), THE_ZONE_IT_READS_IN);
    }

    /**
     * How this application says that an offer has not opened yet, in one place.
     *
     * <p>One wording for the locked card and for the refused claim, because they are the same
     * fact said at two moments: a customer who read "opens on the third" on the card and then
     * pressed anyway deserves to be told the same thing rather than a second sentence they have
     * to reconcile with the first. The day is in it because the day is the only part of it they
     * can act on, and it is written as the offer holds it — a plain date, the way every other day
     * this API sends travels.
     */
    private static String itOpensOn(RewardOffer offer) {
        return offer.title() + " opens on " + offer.opensOn() + ".";
    }

    /** And how it says an offer's last day has gone past, with the day it was. */
    private static String itClosedOn(RewardOffer offer) {
        return offer.title() + " closed on " + offer.closesOn()
                + " and is no longer being offered.";
    }

    /**
     * And how it says an offer is not for this customer, naming the one rule they have not met
     * and both sides of it.
     *
     * <p>One wording for the locked card and for the refused claim, like the two above, because
     * they are the same fact said at two moments — and it matters more here than it does for a
     * window. A date is a date either way round; "you are not eligible" said two ways is a
     * customer who reads one sentence on the card, presses anyway, and is given a second sentence
     * they then have to work out is the same objection.
     *
     * <p><strong>Both sides of the threshold, always: what the offer asks for and what they
     * hold.</strong> A refusal saying only the requirement is an assertion the customer has to
     * take on trust, and half the complaints a scheme like this gets are from somebody who
     * believes they already qualify. The badge is the one that cannot have a figure on the far
     * side — either it is in the trophy case or it is not — so it says so instead, and says the
     * code, because the code is the thing they can go and look for.
     *
     * <p>The rule comes in already decided, rather than being worked out again here. Deciding is
     * {@code WhoAnOfferIsFor}'s, once, so that the sentence a customer reads and the verdict the
     * card is drawn from can never be about two different rules.
     */
    private static String itIsNotForYou(RewardOffer offer, CustomerStanding standing,
                                        AnEligibilityRule rule) {
        return switch (rule) {
            case A_STREAK -> offer.title() + " is for customers on a run of "
                    + offer.minimumStreakWeeks() + " weeks or more, and yours is "
                    + standing.streakWeeks() + ".";
            case A_BADGE -> offer.title() + " is for customers who have won the \""
                    + offer.requiresBadge() + "\" badge, and you have not won it yet.";
            case A_LIFETIME_OF_POINTS_EARNED -> offer.title() + " is for customers who have "
                    + "earned " + offer.minimumLifetimePointsEarned()
                    + " points in all, and you have earned " + standing.lifetimePointsEarned()
                    + ".";
        };
    }

    /**
     * The rule that stood in the way and the figures on both sides of it, for the log.
     *
     * <p>The one lock in this module whose inputs are not on the offer, which is why it gets a
     * line of its own beside the one {@link #locked} writes. A customer complaining that
     * something went grey overnight is answered by the window and the day; a customer complaining
     * that something is grey for them and not for their neighbour is only answerable by what the
     * offer asked for and what the application thought they had — and the second is the
     * derivation, which is exactly the thing worth being able to check afterwards.
     *
     * <p>All three thresholds rather than only the one that failed, because "it locked on the
     * streak" is only half an answer when what somebody actually wants to know is everything the
     * offer wanted. Nulls are printed as nulls: a rule that is not set is a fact about the offer
     * and reads as one.
     */
    private void theRuleAndTheStandingBehindIt(RewardOffer offer, CustomerStanding standing,
                                               AnEligibilityRule rule) {
        log.debug("offer is not for this customer reward={} rule={} minimumStreakWeeks={} "
                        + "requiresBadge={} minimumLifetimePointsEarned={} streakWeeks={} "
                        + "badgesHeld={} lifetimePointsEarned={}",
                offer.code(), rule, offer.minimumStreakWeeks(), offer.requiresBadge(),
                offer.minimumLifetimePointsEarned(), standing.streakWeeks(),
                standing.badgesHeld(), standing.lifetimePointsEarned());
    }

    /**
     * The one refusal in this module whose reason is not enough on its own, so it gets a line of
     * its own rather than going through {@link #refusing}.
     *
     * <p>Still exactly one WARN, because a refusal is one event and two lines for it is two
     * events to anybody counting. What it adds is the rule as a value rather than only as prose:
     * "how many claims did we turn down for a streak last month" is a question a scheme running
     * gated offers will actually ask, and it is answerable by grepping {@code rule=A_STREAK} and
     * not by grepping a sentence somebody may reword.
     *
     * <p>And the standing beside it, which is the half a complaint cannot be answered without. A
     * customer who believes they qualify is answered by what the offer asked for — in the
     * reason — and by what the application thought they had, which is here and nowhere else. It
     * is the same four figures the DEBUG line carries for a card that was merely locked; a claim
     * that was refused is more than worth a WARN's width.
     */
    private RewardRefused refusingForARule(WhatWasAskedFor asked, long customerId,
                                           RewardOffer reward, CustomerStanding standing,
                                           AnEligibilityRule rule) {
        String reason = itIsNotForYou(reward, standing, rule);
        log.warn("{} customerId={} reward={} kind={} rule={} minimumStreakWeeks={} "
                        + "requiresBadge={} minimumLifetimePointsEarned={} streakWeeks={} "
                        + "badgesHeld={} lifetimePointsEarned={} reason={}",
                asked.inTheLog(), customerId, reward.code(), NOT_FOR_YOU, rule,
                reward.minimumStreakWeeks(),
                reward.requiresBadge(), reward.minimumLifetimePointsEarned(),
                standing.streakWeeks(), standing.badgesHeld(),
                standing.lifetimePointsEarned(), reason);
        return new RewardRefused(NOT_FOR_YOU, reason);
    }

    /**
     * How this application says that somebody has had every one of these a person is ever
     * allowed, in one place.
     *
     * <p>One wording for the locked card and for the refused claim, for the reason the window's
     * two sentences give above: they are the same fact said at two moments, and a customer who
     * read one and then pressed anyway should not be handed a second sentence to reconcile with
     * the first.
     *
     * <p><strong>How many they have had is in it, and so is the cap.</strong> "You have had your
     * limit" on its own is an assertion somebody has no way to check, and the two numbers are
     * what turn it into something they can recognise as true — the same reason the window's
     * sentence carries its day. The cap without the count would read as the rule rather than as
     * their position in it, and the count without the cap would not say why it was enough.
     *
     * <p>There is no advice at the end of this one, deliberately, and the weekly sentence below
     * has some. A lifetime cap is not waited out; telling somebody to come back would be telling
     * them something untrue in the one sentence they will remember.
     *
     * <p><strong>A hold they are still holding is said out loud rather than folded into the
     * count.</strong> A live hold counts against a cap, so somebody at a cap of one who is
     * holding one has had none and is nevertheless finished — and "you have already had 0 of
     * this, and one customer may have 1" is a sentence that reads as a bug. The clause is
     * awkward and it is the honest awkwardness of the situation: the thing standing in their way
     * is a reservation they made themselves, and the next thing they will want to do is go and
     * convert it or give it up.
     */
    private static String theyHaveHadAllTheyMayEverHave(RewardOffer offer,
                                                        HowManyHaveGone haveGone,
                                                        boolean holdingOne) {
        return "You have already had " + haveGone.everHad() + " of " + offer.title()
                + (holdingOne ? " and are holding another" : "")
                + ", and one customer may have " + offer.maxPerCustomer() + " in all.";
    }

    /**
     * And how it says they have had this week's allowance, with the day the allowance comes back.
     *
     * <p>That the week turns over on Monday is the part of this a person can act on, which is
     * why it is in the sentence at all: the week is the one this application counts everything
     * else in, so "Monday" is a promise the streak, the loyalty bonus and the challenges are
     * already keeping, and a customer told only that they are at a weekly limit would have to
     * guess whether that meant seven days from now.
     */
    private static String theyHaveHadAllTheyMayHaveThisWeek(RewardOffer offer,
                                                            HowManyHaveGone haveGone,
                                                            boolean holdingOneFromThisWeek) {
        return "You have already had " + haveGone.thisWeek() + " of " + offer.title()
                + " this week" + (holdingOneFromThisWeek ? " and are holding another" : "")
                + ", and one customer may have " + offer.maxPerCustomerPerWeek()
                + " a week. The week turns over on Monday.";
    }

    /**
     * Everything this customer has already claimed, counted once, keyed by the code it was
     * claimed under.
     *
     * <p><strong>The one place a limit's facts are gathered, and both callers use it.</strong>
     * The reading needs it for every row at once and the claim needs it for one, and a narrower
     * query for the claim would be a second definition of "how many they have had" living a
     * hundred lines from the first — which is how a card and a refusal end up disagreeing about
     * a number they both print. The claim asks the broad question and throws most of the answer
     * away; a customer's claim history is a handful of rows, and the guarantee is worth more
     * than the rows.
     *
     * <p><strong>The week is the application's own savings week, named where it already
     * lives.</strong> Monday to Sunday in the zone {@code SavingsWeek} declares, which is the
     * week the streak, the loyalty bonus and the challenges all count in — so a customer who has
     * learned what "this week" means anywhere else in this application has learned what it means
     * here. Nothing in this module declares a second zone or a second Monday, and the week is
     * worked out from the day the whole reading is being taken against rather than from the
     * clock again, so that a list drawn across midnight cannot count two different weeks half
     * way down.
     */
    private Map<String, HowManyHaveGone> whatHasGoneOfEach(long customerId,
                                                                        LocalDate today) {
        SavingsWeek week = SavingsWeek.containing(today);
        List<HowManyHaveGone> counted = redemptions.howManyOfEachHaveGone(
                customerId, VoucherState.CANCELLED, week.startsAt(), week.endsAt());
        // The inputs behind every limit decision that follows, on one line, before any of them
        // are made: which week was counted and how much history there was to count. A limit that
        // looks wrong to a customer is almost always a disagreement about the week.
        log.debug("claims counted against the limits customerId={} today={} week={} "
                        + "offersTheyHaveClaimed={}",
                customerId, today, week, counted.size());
        return counted.stream()
                .collect(Collectors.toMap(HowManyHaveGone::code, Function.identity()));
    }

    /**
     * What that count says about one offer, or nothing at all — which is the answer for almost
     * every offer for almost every person, and the reason the query does not invent a nought row
     * per code.
     */
    private static HowManyHaveGone theCountOf(
            Map<String, HowManyHaveGone> whatHasGone, String code) {
        return whatHasGone.getOrDefault(code, HowManyHaveGone.noneOf(code));
    }

    /**
     * Everything one row of one reading is judged against, gathered into the one object the
     * entity's readings take.
     *
     * <p>Here rather than inline in the stream, because it is the seam where four slices' facts
     * meet: the day and the standing are the same for every row, the counts and what is left are
     * this row's, and {@link WhatIsKnownToday} exists so that none of them arrives as a
     * positional argument nobody can tell from its neighbour.
     */
    private WhatIsKnownToday knownAbout(RewardOffer offer, LocalDate today,
                                        CustomerStanding standing,
                                        HowManyHaveGone counted,
                                        HowManyAreHeld held, AHoldOfYourOwn theirHold,
                                        Integer theirPlaceInTheQueue,
                                        TheBundlesAsTheyStand bundles) {
        List<WhatABundleContains> contents = bundles.inside(offer.code());
        return new WhatIsKnownToday(today, whatIsLeftOf(offer, counted, bundles, held), counted,
                standing, theirHold, theirPlaceInTheQueue, contents,
                theMemberThatCannotSupply(contents, bundles));
    }

    /**
     * What everybody is holding, by offer, for one reading of the catalogue.
     *
     * <p>Beside {@link #whatHasGoneOfEach} and shaped exactly like it, because it is the
     * same kind of thing: one grouped query taken before the first card is drawn, so that every
     * offer in a reading is judged against one set of facts. The moment is handed in rather than
     * read here, for the reason the day is handed to that one.
     */
    private Map<String, HowManyAreHeld> whatIsBeingHeld(Instant now) {
        List<HowManyAreHeld> counted = holds.howManyOfEachAreHeld(HoldState.HELD, now);
        log.debug("holds counted against the stock now={} state={} offersBeingHeld={}",
                now, HoldState.HELD, counted.size());
        return counted.stream()
                .collect(Collectors.toMap(HowManyAreHeld::code, Function.identity()));
    }

    /**
     * And what this one customer is holding, by offer, for the same reading.
     *
     * <p>The savings week is worked out here, once, and written onto each hold as a yes or a no,
     * rather than being carried into {@link WhatIsKnownToday} for a rule to apply for itself.
     * The week is the day's week — the one the whole reading is being taken against — for the
     * reason {@link #whatHasGoneOfEach} gives: a list long enough to cross midnight must
     * not count two different Mondays half way down.
     */
    private Map<String, AHoldOfYourOwn> whatTheyAreHolding(long customerId, LocalDate today,
                                                           Instant now) {
        SavingsWeek week = SavingsWeek.containing(today);
        Map<String, AHoldOfYourOwn> theirs = new LinkedHashMap<>();
        for (RewardHold hold : holds.everythingOneCustomerIsHolding(customerId, HoldState.HELD,
                now)) {
            theirs.put(hold.offerCode(), new AHoldOfYourOwn(hold.lapsesAt(),
                    !hold.takenAt().isBefore(week.startsAt()) && hold.takenAt().isBefore(week.endsAt())));
        }
        log.debug("holds this customer has customerId={} now={} week={} offersTheyAreHolding={}",
                customerId, now, week, theirs.size());
        return theirs;
    }

    /**
     * What that grouped count says about one offer, or nothing at all — which is the answer for
     * almost every offer almost always, and the reason the query invents no nought rows.
     */
    private static HowManyAreHeld whatIsHeldOf(Map<String, HowManyAreHeld> heldByAnybody,
                                               String code) {
        return heldByAnybody.getOrDefault(code, HowManyAreHeld.noneOf(code));
    }

    /**
     * Every bundle in the catalogue as of one reading, or the answer for a catalogue that has
     * none — which is the one this application ships.
     *
     * <p><strong>One read of the lines, and a second read of the offers only if there are
     * any.</strong> The short-circuit is the same one {@link #whatIsLeftOf} makes on a null
     * stock and it is load-bearing in the same way: until somebody composes a bundle, every
     * figure this module produces is worked out from exactly the rows it was worked out from
     * before this table existed, and the four offers a customer has always known cost one
     * additional {@code select} that comes back empty.
     *
     * <p>The offers are read in full rather than filtered to the published ones, because a
     * member may have been withdrawn since the bundle was composed and a bundle that quietly
     * lost a line would be a hamper that shrank without anybody deciding it should. What such a
     * bundle does about its stock is the arithmetic's business, and what it says about itself is
     * still the truth: these are the things in it.
     *
     * <p><strong>The holds arrive as a supplier, and that is the merge's doing.</strong> A
     * member somebody is holding is not available to a bundle either, so the member arithmetic
     * needs the grouped hold count — and a reading of the catalogue has already taken it, while
     * a single claim has not and should not have to. Handing in a supplier keeps both true: the
     * reading passes the map it already holds, a claim passes a call that is only ever made when
     * there turns out to be a bundle, and a catalogue with no bundles in it — which is the one
     * this application ships — asks the holds table nothing extra at all.
     */
    private TheBundlesAsTheyStand theBundlesAsTheyStand(
            Map<String, HowManyHaveGone> counted,
            Supplier<Map<String, HowManyAreHeld>> whatIsHeld) {
        List<OfferMemberLine> lines = memberLines.findAllByOrderByIdAsc();
        if (lines.isEmpty()) {
            return TheBundlesAsTheyStand.none();
        }
        TheBundlesAsTheyStand bundles = TheBundlesAsTheyStand.of(lines,
                offers.findAllByOrderByIdAsc(), counted, whatIsHeld.get());
        log.debug("the bundles behind this reading memberLines={} bundles={} membersDrawnDown={} "
                        + "membersBeingHeld={}",
                lines.size(), bundles.contents().size(), bundles.takenFromMembers(),
                bundles.heldByAnybody().size());
        return bundles;
    }

    /**
     * The first member of a bundle that cannot supply what the bundle needs of it, and nothing at
     * all when every one of them can — which includes every offer that is not a bundle.
     *
     * <p>Worked out beside the remaining count rather than inside it, because the two are
     * different questions with the same inputs: how many of the bundle can be handed over, and
     * which line is the reason that figure is nought. Doing it twice over three lines is cheaper
     * than a method that returns a pair, and it keeps the arithmetic below readable as
     * arithmetic.
     *
     * <p>In the order the bundle was composed in, so that the sentence a customer reads today is
     * the sentence they read yesterday when two things are short at once.
     */
    private AMemberThatCannotSupply theMemberThatCannotSupply(List<WhatABundleContains> contents,
                                                              TheBundlesAsTheyStand bundles) {
        for (WhatABundleContains line : contents) {
            Integer leftOfIt = whatIsLeftOfAMember(line, bundles);
            if (leftOfIt != null && leftOfIt < line.quantity()) {
                return new AMemberThatCannotSupply(line, leftOfIt);
            }
        }
        return null;
    }

    /**
     * How many of one member of a bundle are left, and null when that member never runs out.
     *
     * <p><strong>This is where a bundle's claims are charged to the things it contains.</strong>
     * Its own claims plus what the bundles have taken, which is the whole of the accounting
     * change this slice makes and the whole of what is written down about it: nothing is
     * decremented anywhere, and the second half of the sum is the bundles' claims multiplied by
     * their quantities. The argument for deriving it rather than writing a row per member is on
     * {@link TheBundlesAsTheyStand}.
     *
     * <p><strong>And its live holds, which is the term the merge added and the one neither
     * slice could have written on its own.</strong> Holds and bundles landed in the same wave
     * and neither knew about the other; between them they left a hole with nothing in either
     * branch to catch it. A member whose last one somebody has set aside is not available to
     * anybody — that is the whole of what a hold is — and a bundle is somebody. Without this
     * term the last cinema seat could be held by one customer and handed over inside a hamper
     * to another in the same afternoon, with both readings insisting they were right. So what a
     * member has gone is its own claims, plus what the bundles have drawn down, plus what is
     * being held of it, and the four-term sum here is the same four-term sum
     * {@link #whatIsLeftOf} does for the bundle itself.
     */
    private Integer whatIsLeftOfAMember(WhatABundleContains line, TheBundlesAsTheyStand bundles) {
        RewardOffer member = bundles.theOffer(line.code());
        if (member == null || member.stock() == null) {
            return null;
        }
        return member.whatIsLeftAfter(bundles.claimedInItsOwnRight(line.code())
                + bundles.takenFrom(line.code())
                + bundles.heldOf(line.code()));
    }

    /**
     * A claim refused by a cap, with the cap and the count that met it in the log line rather
     * than only in the sentence.
     *
     * <p>Its own line beside {@link #refusing} for the reason the reading's has one: the words
     * carry the figures for the person, and the line has to carry them as fields for whoever is
     * answering that person's complaint a week later. "Why was I refused and my colleague was
     * not" is a question about four numbers, and a log holding them only inside an English
     * sentence is a log nobody can filter.
     */
    private RewardRefused refusingForALimit(WhatWasAskedFor asked, long customerId,
                                            RewardOffer reward, HowManyHaveGone haveGone,
                                            String reason) {
        log.warn("{} customerId={} reward={} kind={} maxPerCustomer={} everHad={} "
                        + "maxPerCustomerPerWeek={} hadThisWeek={} reason={}",
                asked.inTheLog(), customerId, reward.code(), YOU_HAVE_HAD_YOUR_LIMIT,
                reward.maxPerCustomer(),
                haveGone.everHad(), reward.maxPerCustomerPerWeek(), haveGone.thisWeek(), reason);
        return new RewardRefused(YOU_HAVE_HAD_YOUR_LIMIT, reason);
    }

    /**
     * And how it says there are none of something left, in one place, for the card and the
     * refusal alike.
     *
     * <p>No number in it, unlike the two above, and that is a decision rather than an omission.
     * The day an offer opens is something a customer can act on; "nought left" is not — the
     * figure that matters when there are some is beside the card as {@code whatIsLeft}, and the
     * figure that matters when there are none is no figure at all. What the sentence has to do is
     * be true at both moments it is read: on a card nobody has pressed, and in the refusal of
     * somebody who pressed anyway.
     *
     * <p>Nothing here promises a restock. An administrator may well raise the stock tomorrow and
     * the card will unlock by itself when they do, but the application cannot say so on the day
     * the last one goes, and a sentence implying it would be the scheme making a promise nobody
     * made.
     *
     * <p><strong>A bundle gets a sentence of its own, and it names the part that ran out.</strong>
     * Everything above holds — one wording, for the card and the refusal alike — but "the family
     * night in has sold out" is an answer that sends somebody asking when the hampers are coming
     * back, when what is actually coming back is popcorn. A bundle is the one offer in this
     * catalogue whose sold-out is about something else, so the one thing the sentence has to add
     * is which something, and how many of it there are against how many it needs. Which member
     * that is has already been decided, once, where the arithmetic was done; this only reads it
     * out, so the card and the refusal cannot pick different members.
     */
    private static String nothingIsLeftOf(RewardOffer offer, WhatIsKnownToday known) {
        AMemberThatCannotSupply inTheWay = known.theMemberInTheWay();
        if (inTheWay == null) {
            return offer.title() + " has sold out, and there are none left to claim.";
        }
        return offer.title() + " cannot be made up at the moment: it needs "
                + inTheWay.line().quantity() + " of " + inTheWay.line().title() + ", and "
                + (inTheWay.whatIsLeftOfIt() == 0
                        ? "there are none of those left."
                        : "there are only " + inTheWay.whatIsLeftOfIt() + " of those left.");
    }

    /**
     * And the version of that sentence for the one person it would otherwise mislead: somebody
     * claiming an offer whose last one is the one they are themselves holding.
     *
     * <p>The same kind, because it is the same fact — there is none in the window — and a second
     * kind would be a page having to learn a value in order to draw the message the sentence
     * already carries. Different words, because "sold out" said to the person holding the last
     * one is true and useless: what they need to know is that the thing is theirs and how to
     * take it, which is one button away and not one of these.
     */
    private static String theOnlyOneLeftIsYourOwn(RewardOffer offer) {
        return "The only " + offer.title() + " left is the one being held for you. Claim the one "
                + "you are holding, or give it up and it goes back to whoever wants it.";
    }

    /**
     * How many of an offer are left right now — its stock less every claim already made against
     * it — and null for an offer that never runs out.
     *
     * <p><strong>The one place the scarcity arithmetic is done, and the line that makes it
     * reviewable.</strong> The inputs are logged rather than only the answer, because "the card
     * said sold out and I am sure there were two" is a complaint that can only be answered by the
     * stock, the count and the subtraction, and by nothing less than all three. It is DEBUG for
     * the reason the locked line beside it is: nothing has been refused yet, this runs on every
     * visit to the rewards tab, and a catalogue of forty offers would put forty lines into a page
     * load.
     *
     * <p><strong>Held stock is not available to anybody, so it comes off here and nowhere
     * else.</strong> What is left is the stock less what has gone out <em>and</em> less what is
     * set aside, which is two sources feeding one subtraction: the claims table says what went,
     * the holds table says what is spoken for, and this is the only place the two meet. It is
     * deliberately not two figures on the card — a customer asking "can I have one" is asking
     * one question, and an offer whose last one is being held by somebody else has none for
     * them. The consequence the ticket turns on falls straight out of it: an offer whose last
     * one is held reads as sold out, to everybody except the person holding it, whose reading
     * never reaches this comparison at all.
     *
     * <p>Live holds only, and the query is what decides that. A hold whose seventy-two hours
     * have gone by is holding nothing whether or not the sweep has written it down, so the stock
     * is back from the moment the clock passes rather than from the moment the job runs — which
     * is also what makes an offer unlock itself overnight without anybody pressing anything.
     *
     * <p><strong>The count is handed in rather than fetched, and that changed in the
     * merge.</strong> This slice first asked the claims table for itself, once per scarce offer,
     * and short-circuited on a null stock so that nothing a customer had ever been able to claim
     * gained a query. The limits slice landed in the same wave with a grouped count of the same
     * table on the same page load, so the two are one query now and the figure arrives on the
     * count that query produced. The short-circuit stays, and still means an offer nobody
     * limited gains no arithmetic, no rule and no way to be refused — which is the guarantee
     * that mattered, and which all four seeded offers still rest on.
     *
     * <p><strong>A cancelled claim is not in {@code goneInAll}, so cancelling one returns the
     * stock here and nowhere else.</strong> That is the whole implementation of "revoking a
     * claim puts the thing back in the window", and it is worth saying out loud precisely
     * because there is no code to point at: nothing increments, nothing restocks, and there is
     * no step that could run twice or fail halfway. The claim simply stops being counted, and
     * the next person to read the card is told there is one more. A design that kept what is
     * left in a column would have needed an increment beside the cancellation, a decision about
     * what to do when it ran twice, and a way to tell a restock from a revocation a month later;
     * this is the same feature with none of that, and it is the clearest return the "derive it,
     * never store it" line has paid in this module. It reaches a bundle's members for free: a
     * member's figure is its containing bundle's {@code goneInAll} multiplied by the quantity
     * on the line, so a cancelled hamper puts its popcorn back in the same breath, and there is
     * no code for that either.
     *
     * <p><strong>Bundles broke the identity this method rested on, and here is what replaced
     * it.</strong> Until now, how many of an offer had gone was how many claims named its code,
     * because a claim names what it bought. A bundle is claimed once, under its own code, and
     * hands over two cinema tickets — so {@code goneInAll} for the cinema ticket is short by
     * two, and a catalogue that believed it would sell tickets it had already given away. The
     * missing half is arithmetic rather than a row: every bundle's claims, times the quantity on
     * its line. It comes in on {@link TheBundlesAsTheyStand}, which is also where the argument
     * against writing a phantom claim per member is set out, and it is added to the offer's own
     * count on the line below. An offer no bundle contains is charged nought, which is every
     * offer in this application's catalogue.
     *
     * <p><strong>Held stock is not available to anybody, so it comes off here and nowhere
     * else.</strong> What is left is the stock less what has gone out <em>and</em> less what is
     * set aside, which is two sources feeding one subtraction: the claims table says what went,
     * the holds table says what is spoken for, and this is the only place the two meet. It is
     * deliberately not two figures on the card — a customer asking "can I have one" is asking
     * one question, and an offer whose last one is being held by somebody else has none for
     * them. The consequence the ticket turns on falls straight out of it: an offer whose last
     * one is held reads as sold out, to everybody except the person holding it, whose reading
     * never reaches this comparison at all.
     *
     * <p>Live holds only, and the query is what decides that. A hold whose seventy-two hours
     * have gone by is holding nothing whether or not the sweep has written it down, so the stock
     * is back from the moment the clock passes rather than from the moment the job runs — which
     * is also what makes an offer unlock itself overnight without anybody pressing anything.
     *
     * <p><strong>And a bundle can be no more plentiful than the scarcest thing inside it.</strong>
     * That is the second half of this method and it is the answer to "how many of these can you
     * actually hand over": six hampers in the cupboard and enough popcorn for two is two
     * hampers, whatever the cupboard says. Written as a minimum over the members rather than as
     * a separate check in the two places that ask, because the two places that ask are the card
     * and the claim, and the whole property this feature keeps is that those two agree to the
     * character. It also means a bundle nobody put a stock figure on is not unlimited — it is
     * as limited as its members are — which is why the short-circuit above asks whether there
     * are lines before it lets a null stock mean "never runs out".
     *
     * <p>Integer division, deliberately: enough popcorn for three halves of a hamper is one
     * hamper. And no recursion, because a bundle may not contain a bundle — a rule refused where
     * one is composed, and the reason this walk is one level deep and provably terminates.
     *
     * <p><strong>Four terms, then, and each keeps its own source.</strong> The stock is the
     * row's; the uncancelled claims come off the one grouped count of the claims table; what
     * the bundles drew down comes off the member-line table; and what is held comes off the
     * holds table. Three tables and one subtraction, and every member of a bundle is charged
     * the same four ways by {@link #whatIsLeftOfAMember} — which is the term the two parallel
     * slices between them left out, and the one that would otherwise sell a held cinema seat
     * inside a hamper.
     */
    private Integer whatIsLeftOf(RewardOffer offer, HowManyHaveGone counted,
                                 TheBundlesAsTheyStand bundles, HowManyAreHeld held) {
        List<WhatABundleContains> contents = bundles.inside(offer.code());
        if (offer.stock() == null && contents.isEmpty()) {
            return null;
        }
        long alreadyGone = counted.goneInAll() + bundles.takenFrom(offer.code());
        long setAside = held.heldInAll();
        Integer whatIsLeft = offer.whatIsLeftAfter(alreadyGone + setAside);
        for (WhatABundleContains line : contents) {
            Integer leftOfTheMember = whatIsLeftOfAMember(line, bundles);
            if (leftOfTheMember == null) {
                continue;
            }
            int asManyAsThisMemberAllows = leftOfTheMember / line.quantity();
            log.debug("what one member of a bundle allows reward={} member={} quantity={} "
                            + "whatIsLeftOfTheMember={} bundlesItAllows={}",
                    offer.code(), line.code(), line.quantity(), leftOfTheMember,
                    asManyAsThisMemberAllows);
            if (whatIsLeft == null || asManyAsThisMemberAllows < whatIsLeft) {
                whatIsLeft = asManyAsThisMemberAllows;
            }
        }
        log.debug("what is left of an offer reward={} stock={} claimedUnderItsOwnCode={} "
                        + "takenByBundles={} held={} members={} whatIsLeft={}",
                offer.code(), offer.stock(), counted.goneInAll(),
                bundles.takenFrom(offer.code()), setAside, contents.size(), whatIsLeft);
        return whatIsLeft;
    }

    /**
     * Whether that figure means the offer has run out — which an unlimited offer never does.
     *
     * <p>Its own question rather than the comparison written out twice, because the reading and
     * the claim have to agree about it to the character: a card that said sold out while the
     * claim went through, or the other way round, is the exact failure the one-sentence rule
     * above is built to make impossible. Null is not nought here and never can be: no stock is
     * unlimited, and reading the absence of a rule as the harshest possible value of it would
     * sell out the whole catalogue.
     */
    private static boolean theLastOneHasGone(Integer whatIsLeft) {
        return whatIsLeft != null && whatIsLeft <= 0;
    }

    /**
     * What moment it is, truncated the way every moment this application records is.
     *
     * <p>Off the injected clock, never {@code Instant.now()}, for the reason {@link #theDayItIs}
     * gives about the day: a trainer winding the development clock forward has to be able to
     * watch a hold run out, and a rule that read the machine's own clock would be the one thing
     * a wound clock could not move. Truncated to milliseconds so that the moment reported back
     * when a hold is taken is the moment it is later judged against, exactly as a claim's is.
     */
    private Instant theMomentItIs() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * Every refusal the shared gauntlet raises, built and logged in one place — and the line
     * says which of the four requests was refused.
     *
     * <p><strong>The event is a parameter because the code path is shared and must stay
     * shared.</strong> Claiming, taking a hold, giving one up, joining a queue and leaving one
     * all come through here, and until this ticket every one of them was logged as
     * {@code claim rejected}: grepping that phrase found three different requests, which is the
     * opposite of what the project's logging rule asks a refusal line for. Taking the line into
     * each caller was the other way to fix it, and it would have meant five copies of one WARN
     * and five places for the fields to drift apart. Naming the event instead keeps the one code
     * path and gives each request its own phrase — the phrases are {@link WhatWasAskedFor}'s,
     * which is where the four of them are spelled once.
     *
     * <p>The kind is in the line as well as the sentence, because a kind is what a page switches
     * on and a sentence is what a person reads, and somebody answering a complaint a week later
     * needs to know which refusal was raised rather than only how it was worded.
     */
    private RewardRefused refusing(WhatWasAskedFor asked, long customerId, String rewardCode,
                                   RewardRefused.Kind kind, String reason) {
        log.warn("{} customerId={} reward={} kind={} reason={}",
                asked.inTheLog(), customerId, rewardCode, kind, reason);
        return new RewardRefused(kind, reason);
    }

    /**
     * The day a voucher issued from this offer, at this moment, runs out — and nothing at all when
     * the offer never expires its vouchers.
     *
     * <p>Decided once, here, at the moment of the claim, and written onto the voucher. The
     * argument for writing it down rather than deriving it later is beside the column on
     * {@link Redemption}, and the arithmetic itself is {@link VoucherShelfLife}'s so that the day
     * the customer is told, the day their list shows and the day the sweep judges against cannot
     * come apart.
     *
     * <p>Null in, null out, and that is the rail: an offer that named no shelf life issues a
     * voucher with no day on it, which is a voucher the sweep can never see. Every seeded offer is
     * in exactly that state, so this method answers null for everything the application sold
     * before today.
     */
    private LocalDate theDayAVoucherFromThisRunsOutOn(RewardOffer reward, Instant claimedAt) {
        Integer validForDays = reward.voucherValidForDays();
        if (validForDays == null) {
            log.debug("the voucher for this claim never runs out reward={} voucherValidForDays=null",
                    reward.code());
            return null;
        }
        LocalDate runsOutOn = VoucherShelfLife.theDayItRunsOutOn(claimedAt, validForDays);
        log.debug("the voucher for this claim has a shelf life reward={} voucherValidForDays={} "
                + "claimedAt={} expiresOn={}", reward.code(), validForDays, claimedAt, runsOutOn);
        return runsOutOn;
    }

    /**
     * The first five questions of the contract's order, asked of one code on one day, in one
     * place: does the catalogue have it, is it on sale, is it inside its window, is there such a
     * customer, and is it for them.
     *
     * <p><strong>Shared by claiming and by taking a hold, because the order is a contract and a
     * contract written twice is two contracts.</strong> The spec fixes the sequence — existence,
     * on sale, window, eligibility, limit, stock, points last, always — and this method is the
     * front half of it verbatim. Taking a hold runs exactly the same gauntlet as claiming: the
     * only questions a hold answers differently are the last two, because it takes stock and
     * never points. A second copy here would be the place the two quietly came to disagree about
     * whether a closed offer can be reserved.
     *
     * <p>The day is handed in rather than read here, so that everything one request is judged
     * against is judged against one day — and so that a caller which has already read the clock
     * for something else cannot end up with two.
     *
     * <p><strong>What was asked is handed in too, and only the log reads it.</strong> Nothing
     * about the order, the sentences or the kinds differs between a claim, a hold and a place in
     * a queue — that sameness is the whole value of this method — but a WARN line that said
     * {@code claim rejected} for all three was telling whoever greps it something untrue about
     * three requests out of four. The argument is on {@link WhatWasAskedFor} and the four
     * phrases are spelled there.
     */
    private RewardOffer theOfferTheyMayAskFor(WhatWasAskedFor asked, long customerId,
                                              String rewardCode, CustomerStanding standing,
                                              LocalDate today) {
        RewardOffer reward = offers.findByCode(rewardCode).orElseThrow(() ->
                refusing(asked, customerId, rewardCode, NO_SUCH_OFFER, noSuchOffer(rewardCode)));
        if (!reward.isOnSale()) {
            throw refusing(asked, customerId, rewardCode, NOT_ON_SALE, reward.title()
                    + " is not on sale at the moment.");
        }
        // The window is asked third, of the same row, and before anything about the customer: an
        // offer that has not opened is not one whose claimant or whose balance is worth reasoning
        // about, and the words are the same words the card was locked with — one sentence, so that
        // the page and the refusal cannot tell somebody two different stories about one rule. The
        // day is the one the whole request is being judged against rather than one taken from the
        // reading a page was drawn from, because a page left open since yesterday is exactly how
        // this refusal arrives.
        if (!reward.hasOpenedBy(today)) {
            throw refusing(asked, customerId, rewardCode, NOT_OPEN_YET, itOpensOn(reward));
        }
        if (reward.hasClosedBy(today)) {
            throw refusing(asked, customerId, rewardCode, CLOSED, itClosedOn(reward));
        }
        if (!accounts.customerExists(customerId)) {
            throw refusing(asked, customerId, rewardCode, NO_SUCH_CUSTOMER,
                    AccountsService.noSuchCustomer(customerId));
        }
        // Who the offer is for, asked in the same position it is asked in the reading and in the
        // same words — the fourth question of the contract's order, after the window and a long
        // way before the points. A customer refused here is told what the offer wanted and what
        // they hold, which is the sentence their card has been showing them all along; the two
        // being one sentence is what stops the page and the refusal from telling two stories.
        Optional<AnEligibilityRule> ruleInTheWay =
                reward.whoItIsFor().theFirstRuleNotMetBy(standing);
        if (ruleInTheWay.isPresent()) {
            throw refusingForARule(asked, customerId, reward, standing, ruleInTheWay.get());
        }
        return reward;
    }

    /**
     * Puts the last one aside for one customer for seventy-two hours, and takes no points at all.
     *
     * <p><strong>A hold takes stock and never points, and that is the decision this whole slice
     * turns on.</strong> The customer's balance is untouched, every batch in their ledger goes on
     * running its own twelve-month clock, and nothing about what they can spend elsewhere
     * changes. Holding points was considered and rejected: it would need a second "held" notion
     * inside the points ledger — points that count towards a balance but cannot be spent — and
     * that notion would have to interact with oldest-first spending and with the twelve-month
     * expiry rules, which are the most carefully built and most carefully tested thing in this
     * application. The cost of not doing it is one outcome the spec names and accepts: a hold
     * whose points expire underneath it cannot be converted, the customer is told they are
     * short, and the hold is untouched. That is a true sentence about what happened and it is
     * the reason holding stock rather than points is the right way round.
     *
     * <p><strong>The same gauntlet as a claim, minus the points.</strong> Existence, on sale,
     * window, the customer, who the offer is for, the limit, and then stock — and nothing about
     * the balance, because there is nothing to pay. An offer nobody may claim is not one anybody
     * may reserve, and letting a hold skip a rule would be a customer setting aside something
     * they could never convert.
     *
     * <p><strong>One live hold per offer per customer.</strong> A second is refused under the
     * limit's own kind, because that is what it is: a cap of one, on this offer, for this
     * person. Somebody holding one already has the thing set aside and pressing again cannot
     * give them another — the whole of what a hold does for them is already done.
     *
     * <p>A hold also counts against a purchase limit, which is enforced by the check below
     * rather than by anything the hold does: the figures come from {@link WhatIsKnownToday}'s
     * arithmetic in the reading and from the same two sums in a claim, so a customer cannot hold
     * one and claim another past their cap.
     *
     * @throws RewardRefused if the catalogue has nothing under that code, if the offer is not on
     *         sale, if it is outside its window, if there is no such customer, if it is not for
     *         them, if they have had or are holding their limit, or if there are none left
     */
    @Transactional
    public AHoldOnAnOffer takeAHold(long customerId, String rewardCode, CustomerStanding standing) {
        LocalDate today = theDayItIs();
        Instant now = theMomentItIs();
        RewardOffer reward = theOfferTheyMayAskFor(WhatWasAskedFor.A_HOLD, customerId, rewardCode,
                standing, today);
        // Whether they already hold one, asked here: after everything about the offer and about
        // who they are, and immediately before the cap it is a special case of. A customer who
        // is not allowed this at all should be told that rather than told they already have one
        // of it set aside.
        Optional<RewardHold> already =
                holds.theLiveHoldOneCustomerHasOn(customerId, reward.code(), HoldState.HELD, now);
        if (already.isPresent()) {
            throw refusingForALiveHold(customerId, reward, already.get());
        }
        Map<String, HowManyHaveGone> whatHasGone =
                whatHasGoneOfEach(customerId, today);
        HowManyHaveGone haveGone = theCountOf(whatHasGone, rewardCode);
        if (reward.theyHaveHadTheirLifetimeLimit(haveGone.everHad())) {
            throw refusingForALimit(WhatWasAskedFor.A_HOLD, customerId, reward, haveGone,
                    theyHaveHadAllTheyMayEverHave(reward, haveGone, false));
        }
        if (reward.theyHaveHadTheirLimitThisWeek(haveGone.thisWeek())) {
            throw refusingForALimit(WhatWasAskedFor.A_HOLD, customerId, reward, haveGone,
                    theyHaveHadAllTheyMayHaveThisWeek(reward, haveGone, false));
        }
        // And stock, last, because there is no points question to come after it. The figure is
        // counted inside this transaction against the clock, so the second of two people
        // reaching for the last one at the same moment counts the first one's hold and is
        // refused — the same argument the claim path makes about two claims, resting on the same
        // single connection and the same transaction boundary.
        //
        // A bundle is asked the same question the claim asks it, through the same method and in
        // the same words: a hamper whose popcorn has run out cannot be set aside either, and
        // the sentence names the part that is missing. Reserving something nobody could hand
        // over would be the scheme promising a customer three days of nothing.
        HowManyAreHeld held = whatIsHeldOf(reward, now);
        TheBundlesAsTheyStand bundles =
                theBundlesAsTheyStand(whatHasGone, () -> whatIsBeingHeld(now));
        WhatIsKnownToday known =
                knownAbout(reward, today, standing, haveGone, held, null, null, bundles);
        Integer whatIsLeft = known.whatIsLeft();
        if (theLastOneHasGone(whatIsLeft)) {
            throw refusing(WhatWasAskedFor.A_HOLD, customerId, rewardCode, NOTHING_LEFT,
                    nothingIsLeftOf(reward, known));
        }

        RewardHold taken = holds.save(RewardHold.takenBy(customerId, reward.code(), now));
        // What it would cost them today, asked once and used for the line and for the answer.
        // It is not a price this hold has locked in — converting pays whatever is in force
        // then — and the line says so by naming the day it was true of.
        long costToday = whatItCostsOn(reward, today);
        log.info("hold taken holdId={} customerId={} reward={} takenAt={} lapsesAt={} "
                        + "hoursAHoldLasts={} costInPointsToday={} today={} pointsTaken=0 "
                        + "whatIsLeftToAnybodyElse={}",
                taken.id(), customerId, taken.offerCode(), taken.takenAt(), taken.lapsesAt(),
                TheShelfLifeOfAHold.THE_HOURS_A_HOLD_LASTS, costToday, today,
                whatIsLeft == null ? null : whatIsLeft - 1);
        return taken.asItStands(reward.title(), costToday);
    }

    /**
     * Gives a hold up, which puts the thing back in the window at once.
     *
     * <p>At once and not at the next sweep, because there is nothing to wait for: the customer
     * has said they do not want it, and every reading of what is left counts live holds only. The
     * next person to look at the card sees it available, with no job having run.
     *
     * <p>Nothing is refunded because nothing was taken. A hold costs no points, so giving one up
     * moves no points, and the only thing that changes anywhere is that one row stops being live.
     *
     * <p>A customer who is not holding one is refused in the same words a conversion would be,
     * and under the same kind: what is missing is the same thing, and the sentence says which
     * absence it is.
     *
     * @throws RewardRefused if the catalogue has nothing under that code, or if there is no live
     *         hold of theirs on it
     */
    @Transactional
    public AHoldOnAnOffer giveUpAHold(long customerId, String rewardCode) {
        Instant now = theMomentItIs();
        // The offer is looked up for its words rather than for its rules. Giving a hold up is
        // the one thing in this module that stays possible when everything else about an offer
        // has stopped being: a withdrawn offer, a closed window and a rule the customer no
        // longer meets are all reasons they cannot claim the thing, and none of them is a reason
        // to make them go on holding it. Refusing here would leave stock locked up by somebody
        // who had asked to let it go.
        RewardOffer reward = offers.findByCode(rewardCode).orElseThrow(() ->
                refusing(WhatWasAskedFor.A_HOLD, customerId, rewardCode, NO_SUCH_OFFER,
                        noSuchOffer(rewardCode)));
        RewardHold hold = holds.theLiveHoldOneCustomerHasOn(customerId, reward.code(),
                HoldState.HELD, now).orElseThrow(() ->
                refusingForTheMissingHold(customerId, reward, now));
        hold.givenUp(now);
        holds.save(hold);
        LocalDate today = theDayItIs();
        Map<String, HowManyHaveGone> whatHasGone =
                whatHasGoneOfEach(customerId, today);
        HowManyHaveGone haveGone = theCountOf(whatHasGone, rewardCode);
        log.info("hold given up holdId={} customerId={} reward={} takenAt={} lapsesAt={} "
                        + "givenUpAt={} pointsRefunded=0 whatIsLeft={}",
                hold.id(), customerId, hold.offerCode(), hold.takenAt(), hold.lapsesAt(), now,
                whatIsLeftOf(reward, haveGone,
                        theBundlesAsTheyStand(whatHasGone, () -> whatIsBeingHeld(now)),
                        whatIsHeldOf(reward, now)));
        return hold.asItStands(reward.title(), whatItCostsOn(reward, today));
    }

    /**
     * Writes down every hold whose seventy-two hours have gone by, and answers how many there
     * were.
     *
     * <p><strong>The second step of the scheme's one nightly sweep</strong>, called by
     * {@link TheRewardsSweepRunsNightly}, which owns the order the steps run in and says why.
     * It runs after the vouchers are expired and <strong>before anything is promoted</strong>,
     * and the third step — the one that hands the oldest waiter a hold of their own — goes in
     * immediately after this one for a reason the sweep's own javadoc sets out: promotion hands
     * out whatever stock there is, and a promotion that ran first would hand out a smaller
     * number than the night actually had.
     *
     * <p><strong>The moment is handed in rather than read here, and it is a moment rather than a
     * day.</strong> Its sibling {@link #expireVouchersPastTheirDay} takes a {@link LocalDate},
     * because a voucher's deadline is a date the customer was promised; a hold's deadline is
     * seventy-two hours from when they took it, which no calendar can answer. So the sweep
     * hands this the very instant it read at the top — the same reading it derived its day
     * from — and everything one night lapses is judged against one moment rather than against
     * the clock as it stood a few milliseconds into the run.
     *
     * <p><strong>What this does is record, not decide.</strong> Every one of these holds stopped
     * holding anything the instant the clock passed its moment: the stock came back then, the
     * card unlocked then, and a conversion attempted since has been refused since. The argument
     * is on {@link HoldState}. What the row buys is that the next step has something to read,
     * that the table can be looked at afterwards, and that the customer whose hold it was can
     * take a fresh one without two rows both claiming to be live.
     *
     * <p>Nothing is refunded, because nothing was taken — a hold costs no points, so a hold
     * lapsing moves none. The stock is the only thing that moves and it has already moved.
     *
     * <p>One transaction for the whole sweep, like the step above it: either the night's lapses
     * are all written or none of them are, and a half-swept night would be a set of holds whose
     * state depended on where the run stopped.
     */
    @Transactional
    public int lapseHoldsPastTheirMoment(Instant now) {
        List<RewardHold> pastTheirMoment = holds.holdsPastTheirMoment(HoldState.HELD, now);
        // The window the database was asked for, before anything is written, so that a sweep
        // that lapsed nothing can be told from a sweep that was handed nothing to look at.
        log.debug("holds considered for lapsing now={} state={} holds={}",
                now, HoldState.HELD, pastTheirMoment.size());
        for (RewardHold hold : pastTheirMoment) {
            hold.lapsed(now);
            // INFO and one line each, which is where this parts company with the voucher step
            // beside it — that one writes a line per voucher at DEBUG. An expiring voucher moves
            // nothing: no points, no stock, nobody else's position changes. A lapsing hold puts
            // stock back into the window and is about to decide who gets it, so it is a business
            // event in the way an expiry is not, and "why did that come back into stock
            // overnight" is a question this line is the whole answer to.
            log.info("hold lapsed holdId={} customerId={} reward={} takenAt={} lapsesAt={} "
                            + "sweptAt={} pointsRefunded=0 stockReturned=1",
                    hold.id(), hold.customerId(), hold.offerCode(), hold.takenAt(),
                    hold.lapsesAt(), now);
        }
        // Managed rows would be written out at the end of the transaction anyway; saying so
        // leaves nothing for a reader to infer from Hibernate's behaviour, as the step above does.
        holds.saveAll(pastTheirMoment);
        return pastTheirMoment.size();
    }

    /**
     * Puts a customer in the queue for something that has run out, so that running out is not
     * the end of it.
     *
     * <p><strong>Only for something that has genuinely run out, and this is the check that
     * makes a waiting list a waiting list.</strong> A queue for a thing anybody can walk up and
     * claim is not a queue: there is nothing to be next for, the sweep has no returning stock
     * to hand out, and the customer would sit in a line waiting for something already theirs.
     * So the stock question is asked last of all — the position it holds everywhere else in
     * this module — and this is the one place in the module where it is the <em>presence</em>
     * of stock that refuses. The argument for its own kind is on
     * {@link RewardRefused.Kind#NOT_SOLD_OUT}.
     *
     * <p><strong>The same gauntlet as a hold, and then the stock question inverted.</strong>
     * Existence, on sale, window, the customer, who the offer is for, whether they are already
     * in line, whether the thing is already being kept for them, and the two caps — every one
     * of them exactly as {@link #takeAHold} asks it, in the same order, through the same shared
     * method. A queue somebody may never come to the front of is worse than no queue: they
     * would wait for weeks and be passed over on every sweep for a reason nobody ever told
     * them.
     *
     * <p><strong>Both caps refuse, including the weekly one, and that is a deliberate choice
     * against the obvious alternative.</strong> A weekly cap comes back on Monday, so "let them
     * queue now and promote them next week" is a perfectly reasonable rule — and it would put
     * the card and the button in disagreement, which is the one thing this feature does not
     * do. A customer at their weekly cap sees a card locked with
     * {@link WhyAnOfferIsLocked#YOU_HAVE_HAD_YOUR_LIMIT} rather than one locked as sold out,
     * because the limit is asked before the stock in the reading; the page offers the queue on
     * a sold-out card and on no other, so the button is not there to press. Accepting the press
     * anyway would mean the only way to reach it was a page left open since before the cap was
     * reached.
     *
     * <p><strong>No points are asked about at any point, and none are taken.</strong> Waiting
     * costs nothing and locks nothing in — not even a price, because what a turn buys is a hold
     * and converting a hold pays whatever is in force then. Somebody who cannot afford the
     * thing today is precisely the customer a queue is worth having.
     *
     * @throws RewardRefused if the catalogue has nothing under that code, if the offer is not
     *         on sale, if it is outside its window, if there is no such customer, if it is not
     *         for them, if they are in the queue already, if it is already being held for them,
     *         if they have had their limit, or if it has not run out
     */
    @Transactional
    public APlaceInTheQueue joinTheQueue(long customerId, String rewardCode,
                                         CustomerStanding standing) {
        LocalDate today = theDayItIs();
        Instant now = theMomentItIs();
        RewardOffer reward = theOfferTheyMayAskFor(WhatWasAskedFor.A_PLACE_IN_A_QUEUE, customerId,
                rewardCode, standing, today);
        // Whether they are in it already, asked here: after everything about the offer and
        // about who they are, and before the caps — the position a second hold is refused in,
        // and for the same reason. A customer who may not have this at all should be told that
        // rather than told they are already in line for it.
        Optional<WaitingListPlace> already = waitingList.thePlaceOneCustomerHasIn(
                customerId, reward.code(), WaitingState.WAITING);
        if (already.isPresent()) {
            throw refusingForAPlaceTheyAlreadyHave(customerId, reward, already.get());
        }
        // And whether the thing is already being kept for them, which the stock question at the
        // bottom of this method would otherwise get exactly backwards: their own hold is one of
        // the ones subtracted from what is left, so an offer whose last one they are holding
        // reads as sold out to the arithmetic and would let them join a queue for a thing that
        // is already theirs. The card gets this right by returning before the stock question is
        // ever asked of them; this is the same return, made here.
        Optional<RewardHold> theirs = holds.theLiveHoldOneCustomerHasOn(
                customerId, reward.code(), HoldState.HELD, now);
        if (theirs.isPresent()) {
            throw refusingToQueueForWhatIsAlreadyTheirs(customerId, reward, theirs.get());
        }
        Map<String, HowManyHaveGone> whatHasGone =
                whatHasGoneOfEach(customerId, today);
        HowManyHaveGone haveGone = theCountOf(whatHasGone, rewardCode);
        if (reward.theyHaveHadTheirLifetimeLimit(haveGone.everHad())) {
            throw refusingForALimit(WhatWasAskedFor.A_PLACE_IN_A_QUEUE, customerId, reward,
                    haveGone, theyHaveHadAllTheyMayEverHave(reward, haveGone, false));
        }
        if (reward.theyHaveHadTheirLimitThisWeek(haveGone.thisWeek())) {
            throw refusingForALimit(WhatWasAskedFor.A_PLACE_IN_A_QUEUE, customerId, reward,
                    haveGone, theyHaveHadAllTheyMayHaveThisWeek(reward, haveGone, false));
        }
        // And the stock, last, counted through the one subtraction every other reading goes
        // through — including every bundle that contains this offer, so that a hamper whose
        // popcorn has run out can be queued for exactly as a bare offer can.
        WhatIsKnownToday known = knownAbout(reward, today, standing, haveGone,
                whatIsHeldOf(reward, now), null, null,
                theBundlesAsTheyStand(whatHasGone, () -> whatIsBeingHeld(now)));
        Integer whatIsLeft = known.whatIsLeft();
        if (!theLastOneHasGone(whatIsLeft)) {
            throw refusingToQueueForSomethingStillOnTheShelf(customerId, reward, whatIsLeft);
        }

        WaitingListPlace joined = waitingList.save(
                WaitingListPlace.joinedBy(customerId, reward.code(), now));
        // Counted after the write, because the row they are asking about is one of the ones
        // being counted: a position worked out before the insert would say "you are second"
        // to the person who had just become third.
        int position = thePositionOf(joined);
        log.info("joined the queue placeId={} customerId={} reward={} joinedAt={} position={} "
                        + "whatIsLeft={} pointsTaken=0",
                joined.id(), customerId, joined.offerCode(), joined.joinedAt(), position,
                whatIsLeft);
        return joined.asItStands(reward.title(), position);
    }

    /**
     * Takes a customer out of the queue, which closes the gap behind them at once.
     *
     * <p>At once and not at the next sweep, for the reason giving a hold up is immediate:
     * nothing is waiting to be decided, every position is counted over the rows still waiting,
     * and the next person to look at the card is one place further forward with no job having
     * run.
     *
     * <p>Nothing is refunded because nothing was ever taken, and no stock moves either — which
     * is the difference from giving a hold up. A place in a queue holds nothing; it is a
     * request to be told when something comes back.
     *
     * <p><strong>No standing is assembled and no rule is applied, exactly as giving up a hold
     * applies none.</strong> An offer withdrawn while they waited, a window that closed, a rule
     * they no longer meet — every one of those is a reason they cannot have the thing, and not
     * one of them is a reason to keep somebody in a queue they have asked to leave. The only
     * thing looked up is the offer, and only for its words.
     *
     * <p>It answers with the place as it stood, position and all, rather than with nothing, so
     * that the page can say what happened without a second request — the shape giving up a hold
     * already set.
     *
     * @throws RewardRefused if the catalogue has nothing under that code, or if there is no
     *         place of theirs in that queue to leave
     */
    @Transactional
    public APlaceInTheQueue leaveTheQueue(long customerId, String rewardCode) {
        Instant now = theMomentItIs();
        RewardOffer reward = offers.findByCode(rewardCode).orElseThrow(() ->
                refusing(WhatWasAskedFor.LEAVING_A_QUEUE, customerId, rewardCode, NO_SUCH_OFFER,
                        noSuchOffer(rewardCode)));
        WaitingListPlace place = waitingList.thePlaceOneCustomerHasIn(customerId, reward.code(),
                WaitingState.WAITING).orElseThrow(() ->
                refusingForThePlaceThatIsNotThere(customerId, reward, now));
        // Counted before the row is closed, because it is the position they are leaving from
        // and a count taken afterwards would not include them in their own queue.
        int position = thePositionOf(place);
        place.left(now);
        waitingList.save(place);
        log.info("left the queue placeId={} customerId={} reward={} joinedAt={} "
                        + "positionTheyLeftFrom={} leftAt={} pointsRefunded=0 stockReturned=0",
                place.id(), customerId, place.offerCode(), place.joinedAt(), position, now);
        return place.asItStands(reward.title(), position);
    }

    /**
     * Who is queued for one offer, in the order they joined — what whoever runs the scheme
     * reads in order to know what to restock.
     *
     * <p>The people still waiting and nobody else. A queue an administrator is looking at is
     * the line as it stands, not the history of everybody who was ever in it: somebody who was
     * promoted has their hold and somebody who left has gone, and showing either would make the
     * only number on the screen that matters — how many are waiting — the wrong number.
     *
     * <p><strong>The positions are the list's own indices rather than a count each.</strong>
     * This reads the whole queue in one ordered query, so where somebody stands is where they
     * are in it; a count per row would be the same ordering asked for again, once per person,
     * with the possibility of the two disagreeing. A customer's own card is the other way round
     * for the opposite reason — it wants one number out of a queue it never reads.
     *
     * <p>An empty list for an offer nobody is waiting for, for an offer that never runs out and
     * for a code the catalogue has never heard of. The last of those is answered as the absence
     * it looks like rather than as a refusal, because the controller has already vouched for
     * the code in its own path and a second refusal here would be the same 404 said twice.
     */
    @Transactional(readOnly = true)
    public List<APlaceInTheQueue> theQueueFor(String code) {
        String title = offers.findByCode(code).map(RewardOffer::title).orElse(null);
        List<WaitingListPlace> queue = waitingList.theQueueFor(code, WaitingState.WAITING);
        List<APlaceInTheQueue> inOrder = new ArrayList<>();
        for (int place = 0; place < queue.size(); place++) {
            inOrder.add(queue.get(place).asItStands(title, place + 1));
        }
        log.debug("the queue for an offer read reward={} waiting={}", code, inOrder.size());
        return inOrder;
    }

    /**
     * Hands the oldest waiter on every offer that has stock a hold of their own, and answers
     * how many waiters that was.
     *
     * <p><strong>The third and last step of the scheme's one nightly sweep</strong>, called by
     * {@link TheRewardsSweepRunsNightly}, which owns the order the steps run in and says why.
     * It runs after the holds are lapsed, and the honest version of what that buys is worth
     * repeating here because the stronger version is tempting and wrong. It is <em>not</em>
     * that this step would otherwise see less stock: a hold is over the instant the clock
     * passes the moment written on it, so the stock came back then and the subtraction below
     * has counted it back since. What running second buys is a tidy table — no row still
     * calling itself {@code HELD} for a hold that ended hours ago — and a defined order, so
     * that a night reads in one direction. {@link HoldState} argues the clock's authority and
     * the sweep's javadoc argues the order.
     *
     * <p><strong>Nothing tells this that stock came back.</strong> The spec names three ways it
     * returns — an administrator raises it, a hold lapses, a voucher is cancelled — and says
     * the same promotion path serves all three. It does, by knowing about none of them: this
     * reads what is left of every offer somebody is waiting for and hands out whatever it
     * finds. All three routes arrive here because all three change the subtraction in
     * {@link #whatIsLeftOf}, and not one of them has a line about queues in it. Three call
     * sites would be three orderings and three transactions to reason about, for an answer that
     * is one query.
     *
     * <p><strong>A hold and never a claim.</strong> The customer is handed the same row, with
     * the same seventy-two hours, that they would have got by pressing the button themselves —
     * no points move, no voucher is issued, and nothing they own is spent while they are
     * asleep. Promoting into a claim was the alternative and it is refused by the spec in one
     * sentence: the application does not spend somebody's points without being asked. It is
     * also the reason a promoted waiter can still be short of points and still be told so, in
     * words, when they come to convert.
     *
     * <p><strong>The stock is counted again for every single waiter, which is deliberately not
     * clever.</strong> Each promotion creates a hold, each hold comes off what is left, and the
     * count is a query rather than a running total precisely so that the figure the second
     * waiter is judged against is the one the first waiter's hold has already changed. The
     * flush JPA performs before each of those queries is what makes that true inside one
     * transaction. It is a query per waiter on a training application whose queues are a
     * handful of people, and the alternative — arithmetic carried in a local variable — is a
     * second implementation of the one subtraction this module allows itself.
     *
     * <p><strong>One transaction for the whole run, like the two steps above it</strong>: either
     * the night's promotions are all written or none of them are. A half-promoted night would
     * be a set of holds whose existence depended on where the run stopped, and a queue whose
     * order depended on it too.
     *
     * <p>Telling the customers happens at the end, after every row is written, and cannot stop
     * any of it. The argument is on {@link TellingAPromotedWaiter} and the promise is kept two
     * paragraphs below, in code.
     */
    @Transactional
    public int promoteWhoeverIsNextInLine(Instant now, LocalDate today) {
        List<String> queues = waitingList.everyOfferSomebodyIsWaitingFor(WaitingState.WAITING);
        // The window the database was asked for, before anything is written, so that a sweep
        // that promoted nobody can be told from a sweep that was handed nothing to look at.
        log.debug("queues considered for promotion now={} today={} offersWithSomebodyWaiting={}",
                now, today, queues.size());
        List<AHoldOnAnOffer> promoted = new ArrayList<>();
        for (String code : queues) {
            RewardOffer offer = offers.findByCode(code).orElse(null);
            if (offer == null) {
                // Unreachable in anything this application does — an offer is never deleted,
                // and a queue is only ever joined against one that exists. Said out loud
                // rather than allowed to throw, because a sweep that fell over on one bad row
                // would leave every other queue in the database unpromoted for the night.
                log.warn("queue passed over reward={} reason=the catalogue has no such offer",
                        code);
                continue;
            }
            if (!offer.isOnSale()) {
                // A draft or a withdrawn offer, with a queue somebody joined while it was on
                // sale. Nobody is promoted onto it, and this is the one availability question
                // the reading below does not ask for itself: the customer-facing catalogue
                // filters drafts and withdrawals out in its query rather than locking them, so
                // an offer that has been taken down would otherwise read as perfectly ordinary
                // here. Handing somebody a hold on a withdrawn offer would be the scheme
                // reserving a thing nobody may claim.
                log.warn("queue passed over reward={} state={} reason=the offer is not on sale",
                        code, offer.state());
                continue;
            }
            promoted.addAll(theQueueMovesFor(offer, now, today));
        }
        // Every customer told, after every row is written, and not one of them able to undo
        // any of it. The promise TellingAPromotedWaiter makes is kept here: a notification that
        // failed would otherwise roll the night back and hand the same stock to the same person
        // again tomorrow, which is the one outcome worse than not being told.
        for (AHoldOnAnOffer hold : promoted) {
            try {
                tellThem.aHoldIsWaitingFor(hold);
            } catch (RuntimeException couldNotTellThem) {
                log.error("a promoted waiter could not be told holdId={} customerId={} "
                                + "reward={} lapsesAt={}",
                        hold.id(), hold.customerId(), hold.offerCode(), hold.lapsesAt(),
                        couldNotTellThem);
            }
        }
        return promoted.size();
    }

    /**
     * One offer's queue, moved as far down it as the stock allows.
     *
     * <p><strong>Everything is decided by one reading, which is the same reading the customer's
     * own card is drawn from.</strong> {@link #howItReadsOn} is the contract's order of checks
     * written once — window, eligibility, their own hold, the caps, and stock last — and asking
     * it here rather than writing a second gauntlet is what guarantees that a waiter is
     * promoted exactly when the card would have let them take a hold themselves. Three answers
     * come back and each means something different:
     *
     * <ul>
     *   <li><strong>Claimable</strong> — nothing is in the way, so they get the hold.</li>
     *   <li><strong>Locked because there is nothing left</strong> — the stock has run out part
     *       way down the queue, so this offer is finished for the night and everybody behind
     *       them keeps their place. Not a pass-over: nothing is wrong with the person.</li>
     *   <li><strong>Locked for anything else</strong> — this waiter no longer qualifies, so
     *       they are passed over and the next one is considered against the same stock.</li>
     * </ul>
     *
     * <p><strong>A waiter who is passed over keeps their place in the queue, and that is the
     * sharper of the two decisions in this method.</strong> Removing them was the alternative
     * and it is refused for three reasons that point the same way. Eligibility is a live
     * reading of a run of weeks, a trophy case and a lifetime of points, and a run of weeks is
     * the one of the three that goes <em>down</em>: a customer who missed a week is exactly the
     * person the scheme is trying to get back, and taking their place away would punish one
     * missed week twice over. It would also be irreversible in practice — joining is only
     * allowed while an offer is sold out, so somebody removed on the night the stock came back
     * could not rejoin it. And it would happen silently, at five in the morning, with nothing
     * to press and no way to find out. So the row is untouched, their position is unchanged,
     * and the next sweep asks them again.
     *
     * <p>The position reported is their place in the queue as it stood when this run read it,
     * which is what the log line and the customer's own card both mean by it. Waiters passed
     * over stay in front of the people behind them, so the numbers do not shuffle underneath
     * anybody within one run.
     */
    private List<AHoldOnAnOffer> theQueueMovesFor(RewardOffer offer, Instant now,
                                                  LocalDate today) {
        List<WaitingListPlace> queue = waitingList.theQueueFor(offer.code(), WaitingState.WAITING);
        List<AHoldOnAnOffer> promoted = new ArrayList<>();
        int position = 0;
        for (WaitingListPlace place : queue) {
            position++;
            long customerId = place.customerId();
            Optional<CustomerStanding> theirStanding = standings.theStandingOf(customerId);
            if (theirStanding.isEmpty()) {
                // Unreachable while nothing in this application deletes a customer, and said
                // out loud rather than assumed: the empty standing would let somebody nobody
                // has heard of through every rule that has no threshold on it, and the queue
                // would hand a hold to nobody at all. CustomerStanding.nothingIsKnown is safe
                // only because it is never read, and this is the one place that could have.
                log.warn("waiter passed over customerId={} reward={} position={} "
                        + "reason=nothing is known about this customer", customerId,
                        offer.code(), position);
                continue;
            }
            CustomerStanding standing = theirStanding.get();
            Map<String, HowManyHaveGone> whatHasGone =
                    whatHasGoneOfEach(customerId, today);
            HowManyHaveGone haveGone = theCountOf(whatHasGone, offer.code());
            AHoldOfYourOwn theirHold =
                    whatTheyAreHolding(customerId, today, now).get(offer.code());
            WhatIsKnownToday known = knownAbout(offer, today, standing, haveGone,
                    whatIsHeldOf(offer, now), theirHold, position,
                    theBundlesAsTheyStand(whatHasGone, () -> whatIsBeingHeld(now)));
            if (known.theyHoldOne()) {
                // They took one themselves between joining and their turn coming, which the
                // reading below would call claimable — a hold is an affordance and never a
                // lock, and the card returns before the stock question is asked of its holder.
                // A second hold is exactly what takeAHold refuses, so it is refused here too,
                // and their place is kept: the hold may lapse, and then their turn is real.
                log.info("waiter passed over customerId={} reward={} position={} "
                                + "theirHoldLapsesAt={} reason=they are already holding one",
                        customerId, offer.code(), position, known.theirHoldLapsesAt());
                continue;
            }
            AnOfferAsACustomerReadsIt reading = howItReadsOn(offer, known);
            if (!reading.claimable()) {
                if (reading.lockedBecause() == WhyAnOfferIsLocked.NOTHING_LEFT) {
                    // Not a pass-over. The stock has run out part way down the queue, which is
                    // the ordinary end of a night's promotions, and everybody from here back
                    // keeps their place for the next one.
                    log.debug("the queue stops here reward={} position={} stillWaiting={} "
                                    + "whatIsLeft={}",
                            offer.code(), position, queue.size() - position + 1,
                            known.whatIsLeft());
                    break;
                }
                // INFO rather than WARN, and the distinction is the one this codebase draws
                // between a refusal and a reading: nobody asked for anything, so nothing was
                // refused. It is nevertheless a business event — it decides who gets the last
                // one — so it is not DEBUG either, and "why did the person at the front of the
                // queue not get it" is a question this line is the whole answer to.
                log.info("waiter passed over customerId={} reward={} position={} lockedBecause={} "
                                + "streakWeeks={} lifetimePointsEarned={} badgesHeld={} "
                                + "reason={}",
                        customerId, offer.code(), position, reading.lockedBecause(),
                        standing.streakWeeks(), standing.lifetimePointsEarned(),
                        standing.badgesHeld(), reading.whyItIsLocked());
                continue;
            }
            promoted.add(theTurnOf(place, offer, known, position, now, today));
        }
        return promoted;
    }

    /**
     * One waiter's turn: the hold written, the place closed, and the line that explains both.
     *
     * <p>The hold first and the place second, in that order deliberately. Both are in one
     * transaction so neither can exist without the other, but the order is what a reader
     * follows: the thing the customer gets is created, and then the reason they got it is
     * marked as spent. A place closed against a hold that failed to write would be somebody
     * out of a queue with nothing to show for it, which is the one outcome this order makes
     * impossible to read as accidental.
     */
    private AHoldOnAnOffer theTurnOf(WaitingListPlace place, RewardOffer offer,
                                     WhatIsKnownToday known, int position, Instant now,
                                     LocalDate today) {
        RewardHold taken = holds.save(RewardHold.takenBy(place.customerId(), offer.code(), now));
        place.promoted(now);
        waitingList.save(place);
        // What it would cost them today, asked once and used for the line and for the answer.
        // It is not a price this hold has locked in — converting pays whatever is in force
        // then — and the line says so by naming the day it was true of.
        long costToday = whatItCostsOn(offer, today);
        Integer whatIsLeft = known.whatIsLeft();
        log.info("waiter promoted placeId={} holdId={} customerId={} reward={} position={} "
                        + "joinedAt={} takenAt={} lapsesAt={} hoursAHoldLasts={} "
                        + "costInPointsToday={} today={} pointsTaken=0 whatIsLeftAfter={}",
                place.id(), taken.id(), place.customerId(), offer.code(), position,
                place.joinedAt(), taken.takenAt(), taken.lapsesAt(),
                TheShelfLifeOfAHold.THE_HOURS_A_HOLD_LASTS, costToday, today,
                whatIsLeft == null ? null : whatIsLeft - 1);
        return taken.asItStands(offer.title(), costToday);
    }

    /**
     * Where one customer stands in every queue they are in, by offer, for one reading of the
     * catalogue.
     *
     * <p>Beside {@link #whatTheyAreHolding} and shaped exactly like it, because it is the same
     * kind of thing: their own rows, read once before the first card is drawn, so that every
     * card in one reading is judged against one set of facts about them. It is a count per
     * queue they are in rather than one grouped query, and that is the cheap way round here —
     * almost nobody is in any queue at all, so the ordinary reading is one {@code select} that
     * comes back empty, while a grouped position would have to order and number every waiting
     * row in the database to answer a question about one person.
     */
    private Map<String, Integer> whereTheyStandInEveryQueue(long customerId) {
        Map<String, Integer> places = new LinkedHashMap<>();
        for (WaitingListPlace place
                : waitingList.everyQueueOneCustomerIsIn(customerId, WaitingState.WAITING)) {
            places.put(place.offerCode(), thePositionOf(place));
        }
        log.debug("queues this customer is in customerId={} queues={}", customerId, places.size());
        return places;
    }

    /**
     * Where one place stands in its own queue: however many are ahead of it, and then itself.
     *
     * <p>One-based because it is read out loud — first in line is first — and counted rather
     * than stored, which is the decision {@link WaitingListPlace} is built on. Everywhere a
     * position is worked out in this class works it out through here or through the index of
     * the same ordering, so the number an administrator sees and the number a customer sees
     * cannot come apart.
     */
    private int thePositionOf(WaitingListPlace place) {
        return (int) waitingList.howManyAreAheadOf(place.offerCode(), WaitingState.WAITING,
                place.joinedAt(), place.id()) + 1;
    }

    /**
     * A second place in a queue they are already in, refused with where they already stand.
     *
     * <p>The position is the sentence's whole reason for existing. This is the one refusal in
     * the module that is good news — they wanted to be in line and they are in line — so the
     * thing to tell them is not that something went wrong but where they got to, which is also
     * exactly what their card has been showing them. The argument for its own kind rather than
     * the limit's is on {@link RewardRefused.Kind#ALREADY_WAITING}.
     */
    private RewardRefused refusingForAPlaceTheyAlreadyHave(long customerId, RewardOffer reward,
                                                           WaitingListPlace place) {
        int position = thePositionOf(place);
        String reason = "You are already in the queue for " + reward.title() + ", in position "
                + position + ". When one comes back the oldest waiter is given it first, and we "
                + "will put it aside for you when your turn comes.";
        log.warn("place in a queue rejected customerId={} reward={} kind={} placeId={} "
                        + "position={} reason={}",
                customerId, reward.code(), ALREADY_WAITING, place.id(), position, reason);
        return new RewardRefused(ALREADY_WAITING, reason);
    }

    /**
     * A queue joined for something the customer is already holding, refused under the limit's
     * kind and with the moment their hold runs out.
     *
     * <p>Under {@code YOU_HAVE_HAD_YOUR_LIMIT} rather than {@code NOT_SOLD_OUT}, which the
     * arithmetic would have suggested: what is left of this offer really is nought, because
     * their own hold is one of the ones subtracted from it. Saying "it has not sold out" would
     * therefore be false, and saying "it has sold out, so you may queue" would put somebody in
     * a line for a thing sitting in front of them. It is the same fact
     * {@code refusingForALiveHold} reports when they ask for a second hold — this customer
     * already has this thing set aside — so it is reported under the same kind and in the same
     * shape, and a page that learned one has learned the other.
     */
    private RewardRefused refusingToQueueForWhatIsAlreadyTheirs(long customerId,
                                                                RewardOffer reward,
                                                                RewardHold hold) {
        String reason = "One of " + reward.title() + " is already being held for you until "
                + hold.lapsesAt() + ", so there is nothing to wait for — claim it or give it "
                + "up.";
        log.warn("place in a queue rejected customerId={} reward={} kind={} holdId={} "
                        + "lapsesAt={} reason={}",
                customerId, reward.code(), YOU_HAVE_HAD_YOUR_LIMIT, hold.id(), hold.lapsesAt(),
                reason);
        return new RewardRefused(YOU_HAVE_HAD_YOUR_LIMIT, reason);
    }

    /**
     * A queue joined for something that has not run out, refused with the thing they can do
     * instead.
     *
     * <p>The sentence says how many are left and tells them to claim it, because that is the
     * whole of what they should do next and because this refusal arrives almost exclusively on
     * a page that has been open since before somebody restocked. Being told "there is no queue
     * for this" without being told "there are three of them on the shelf" would send somebody
     * away from the very thing they came for.
     */
    private RewardRefused refusingToQueueForSomethingStillOnTheShelf(long customerId,
                                                                     RewardOffer reward,
                                                                     Integer whatIsLeft) {
        String howMany = whatIsLeft == null ? "It never runs out"
                : whatIsLeft == 1 ? "There is one left" : "There are " + whatIsLeft + " left";
        String reason = "There is no queue for " + reward.title() + " because it has not run "
                + "out. " + howMany + " — you can claim it now.";
        log.warn("place in a queue rejected customerId={} reward={} kind={} whatIsLeft={} "
                        + "reason={}",
                customerId, reward.code(), NOT_SOLD_OUT, whatIsLeft, reason);
        return new RewardRefused(NOT_SOLD_OUT, reason);
    }

    /**
     * Leaving a queue they are not in, refused with a sentence saying which of the three
     * absences it is.
     *
     * <p><strong>Under {@link RewardRefused.Kind#YOU_ARE_NOT_IN_THAT_QUEUE}, which is the
     * twelfth kind and was added for this one refusal.</strong> It was
     * {@code THE_HOLD_HAS_LAPSED} until this ticket, because the spec fixes eleven kinds by
     * name and that was the only one on the list meaning "what you are asking to act on is not
     * there" — an argument about the pipeline, which is true, standing in for an argument about
     * the word, which was not. A place in a queue is indeed the step before a hold in the same
     * mechanism; the name of that kind is nevertheless about a hold, and a customer leaving a
     * queue has no hold, may never have had one, and was being told by the API that theirs had
     * lapsed. The kind is what a page switches on and what the line below carries, so the cost
     * of the stretch fell on every later reader of both. The status is unchanged and the
     * sentences are unchanged: nothing a customer can see moved, and the word is now true.
     *
     * <p>Three sentences rather than one, for the reason a missing hold gets four: "you were
     * given one, and it is yours until Thursday", "you left that queue" and "you are not in
     * that queue" send somebody to do three quite different things next. The row is read back
     * even though the refusal is already decided, because the row is the only thing that can
     * tell them which.
     *
     * <p>Nothing is written.
     */
    private RewardRefused refusingForThePlaceThatIsNotThere(long customerId, RewardOffer reward,
                                                            Instant now) {
        WaitingListPlace last = waitingList
                .findFirstByCustomerIdAndOfferCodeOrderByJoinedAtDescIdDesc(customerId,
                        reward.code()).orElse(null);
        String reason = whatBecameOfThePlace(reward, last, customerId, now);
        log.warn("leaving a queue rejected customerId={} reward={} kind={} placeId={} state={} "
                        + "reason={}",
                customerId, reward.code(), YOU_ARE_NOT_IN_THAT_QUEUE,
                last == null ? null : last.id(), last == null ? null : last.state(), reason);
        return new RewardRefused(YOU_ARE_NOT_IN_THAT_QUEUE, reason);
    }

    /**
     * How this application says that there is no place of theirs in this queue.
     *
     * <p>The promoted sentence carries the deadline of the hold it turned into, because that is
     * the part they will want to act on and the part a screen can be checked against — and
     * because somebody pressing "leave the queue" on a card that has quietly become a held one
     * is exactly how this refusal happens on a page left open overnight. The hold is looked up
     * rather than assumed: a promotion is terminal but the hold it made may since have lapsed
     * or been claimed, and promising a deadline that has gone would be worse than saying
     * nothing.
     */
    private String whatBecameOfThePlace(RewardOffer reward, WaitingListPlace last,
                                        long customerId, Instant now) {
        if (last == null) {
            return "You are not in the queue for " + reward.title() + ".";
        }
        return switch (last.state()) {
            case PROMOTED -> holds.theLiveHoldOneCustomerHasOn(customerId, reward.code(),
                    HoldState.HELD, now)
                    .map(hold -> "Your turn came: one of " + reward.title() + " is being held "
                            + "for you until " + hold.lapsesAt() + ", so you are out of the "
                            + "queue and it is yours to claim or to give up.")
                    .orElse("Your turn came and one of " + reward.title() + " was held for you, "
                            + "so you are out of the queue. That hold is over now.");
            case LEFT -> "You have already left the queue for " + reward.title() + ".";
            // Unreachable: a waiting place is what the caller failed to find. Named rather
            // than swept into a default, so that a state added to the enum is a compilation
            // failure here rather than a customer told something that is not true.
            case WAITING -> "You are not in the queue for " + reward.title() + ".";
        };
    }

    /**
     * A second hold on something they are already holding, refused with the moment the one they
     * have runs out.
     *
     * <p>Under the limit's own kind rather than under a kind of its own, and that is the whole
     * argument: one live hold per offer per customer <em>is</em> a limit, it is this customer's
     * own history standing in the way, and it is reported with the status a limit is reported
     * with. A new kind would be a fourth value for the web layer to map and a fourth word for a
     * page to learn, all to say a thing the existing one already means.
     *
     * <p>The moment is in the sentence because it is the only part of this a customer can act
     * on: they are holding the thing, and what they need to know is how long they have.
     */
    private RewardRefused refusingForALiveHold(long customerId, RewardOffer reward,
                                               RewardHold hold) {
        String reason = "You are already holding one of " + reward.title() + ". It is yours until "
                + hold.lapsesAt() + " — claim it or give it up, and one customer may hold one at "
                + "a time.";
        log.warn("hold rejected customerId={} reward={} kind={} holdId={} lapsesAt={} reason={}",
                customerId, reward.code(), YOU_HAVE_HAD_YOUR_LIMIT, hold.id(), hold.lapsesAt(),
                reason);
        return new RewardRefused(YOU_HAVE_HAD_YOUR_LIMIT, reason);
    }

    /**
     * Converting or giving up a hold that is not there, refused with a sentence saying which of
     * the four absences it is.
     *
     * <p>One kind and four sentences, and the argument for that is on
     * {@link RewardRefused.Kind#THE_HOLD_HAS_LAPSED}. The row is read back even though the
     * answer is already known, because the row is the only thing that can tell a customer
     * something they can use: "it ran out on Tuesday at four" and "you gave that one up" and
     * "you have already claimed it" send somebody to do three quite different things next, and a
     * single sentence covering all three would send them to do none of them.
     *
     * <p>Nothing is written. A hold that ran out is left exactly as the row has it — still
     * {@code HELD} until the sweep gets to it, which changes nothing about what anybody can do,
     * because every question about a live hold asks the clock as well.
     */
    private RewardRefused refusingForTheMissingHold(long customerId, RewardOffer reward,
                                                    Instant now) {
        RewardHold last = holds.findFirstByCustomerIdAndOfferCodeOrderByTakenAtDescIdDesc(
                customerId, reward.code()).orElse(null);
        String reason = whatBecameOfTheHold(reward, last, now);
        log.warn("hold rejected customerId={} reward={} kind={} holdId={} state={} lapsesAt={} "
                        + "now={} reason={}",
                customerId, reward.code(), THE_HOLD_HAS_LAPSED,
                last == null ? null : last.id(), last == null ? null : last.state(),
                last == null ? null : last.lapsesAt(), now, reason);
        return new RewardRefused(THE_HOLD_HAS_LAPSED, reason);
    }

    /**
     * How this application says that there is no live hold here, in one place, for the
     * conversion and the giving up alike.
     *
     * <p>Four sentences and not one, for the reason the window's two are two: they are four
     * different things to be told and only one of them is the customer's own doing. The lapsed
     * one carries the moment, because that is the part they will want to argue with and the part
     * a screen can be checked against.
     *
     * <p>A hold still marked {@code HELD} whose moment has gone reads as lapsed here, which is
     * the same reading every other question in this module takes of it. It is the commonest case
     * by a distance — the sweep runs once a night and a hold can run out at any hour — and
     * telling that customer the row's state rather than the truth would be this application
     * hiding behind its own bookkeeping.
     */
    private static String whatBecameOfTheHold(RewardOffer reward, RewardHold last, Instant now) {
        if (last == null) {
            return "You are not holding one of " + reward.title() + ".";
        }
        return switch (last.state()) {
            case CONVERTED -> "You have already claimed the " + reward.title()
                    + " you were holding.";
            case GIVEN_UP -> "You gave up your hold on " + reward.title()
                    + ", so it is back in the catalogue for whoever wants it.";
            // Held and lapsed read the same, deliberately: a hold whose moment has gone by is
            // over whether or not the sweep has written it down yet, and a customer told
            // otherwise would be told something this application does not act on.
            case HELD, LAPSED -> "Your hold on " + reward.title() + " ran out on "
                    + last.lapsesAt() + ", so it is back in the catalogue for whoever wants it.";
        };
    }

    /**
     * What a bundle's claim took out of the world, said once, in a line a reviewer can read.
     *
     * <p><strong>A second INFO line for one business event, and the only one in this module,
     * deliberately.</strong> A claim is one event and the line above is it; this is the half of
     * that event that no other line can carry, because a bundle's claim row names the bundle and
     * says nothing whatever about the two cinema tickets and the popcorn that just left the
     * shelf. Nothing is written when they go — the whole of the accounting is the multiplication
     * on {@link TheBundlesAsTheyStand} — so without this line the draw-down would be a figure
     * that appears in a later reading and is attributable to nothing at all. A stock figure that
     * moved overnight has to be accountable from the log, which is the spec's own requirement of
     * the sweep and is no less true here.
     *
     * <p>One line with every member on it rather than a line each, so that a claim of a hamper
     * is one thing in the log and not four. Each carries the quantity that went and how many
     * there were before it did, because "we took two" and "there were three" are the two halves
     * of any complaint anybody will make about this figure. What is left afterwards is the
     * subtraction, and it is left as a subtraction rather than counted again: counting again
     * would be two more queries, inside a transaction, for a line — and the figure it produced
     * would be the answer at a moment nobody can name, because the claim after this one may
     * already be waiting.
     *
     * <p>Silent for everything that is not a bundle, which is every offer this application
     * ships: an item's claim draws down an item's stock, and the claim row says so by itself.
     */
    private void theMembersThisClaimDrewDown(long customerId, ClaimedReward claimed,
                                             WhatIsKnownToday known,
                                             TheBundlesAsTheyStand bundles) {
        if (known.contents().isEmpty()) {
            return;
        }
        StringBuilder drawnDown = new StringBuilder();
        for (WhatABundleContains line : known.contents()) {
            if (drawnDown.length() > 0) {
                drawnDown.append(", ");
            }
            drawnDown.append(line.code()).append(" drawnDown=").append(line.quantity())
                    .append(" whatWasLeftOfIt=").append(whatIsLeftOfAMember(line, bundles));
        }
        log.info("bundle claim drew down its members redemptionId={} customerId={} reward={} "
                        + "members=[{}] whatWasLeftOfTheBundle={}",
                claimed.id(), customerId, claimed.rewardCode(), drawnDown, known.whatIsLeft());
    }

    /**
     * Retires every voucher whose last good day is behind us, and answers how many went.
     *
     * <p>The first step of the scheme's one nightly sweep, called by
     * {@link TheRewardsSweepRunsNightly}, which owns the order the steps run in and says why.
     * It refunds nothing and returns nothing, which is the whole meaning of a shelf life; the
     * argument, and the paragraph it is deliberately kept in step with, are on
     * {@link TheLifeOfAVoucher#expireVouchersPastTheirDay}.
     *
     * <p>One transaction for the whole sweep, like the claim it undoes nothing of. Either the
     * night's expiries are all written or none of them are, and a half-swept night would be a
     * set of vouchers whose state depended on where the run stopped.
     */
    @Transactional
    public int expireVouchersPastTheirDay(LocalDate today) {
        return vouchers.expireVouchersPastTheirDay(today);
    }

    /**
     * What the customer has claimed, newest first. Together with the deposits into every account they
     * hold, this is the whole account of a points balance: the deposits say what came in, these say
     * what went out, and the balance is what the two leave behind.
     *
     * <p>Each one reads itself out, catalogue and all. It used to be read out with the words the
     * catalogue holds for the code it names, which was fine while the catalogue was four constants
     * and nothing could ever leave it. Now that somebody runs it, an offer can be renamed or
     * withdrawn, and a claim that borrowed its title from the live row would change its mind about
     * what it was months after the fact — or, once withdrawn, fall back to shouting
     * {@code FAMILY_CINEMA_PACK} at the person holding the voucher. So the title is written onto
     * the claim when it is made, and this method asks the catalogue nothing.
     */
    @Transactional(readOnly = true)
    public List<ClaimedReward> claimedBy(long customerId) {
        return redemptions.findByCustomerIdOrderByClaimedAtDescIdDesc(customerId).stream()
                .map(Redemption::asClaimed)
                .toList();
    }

    /**
     * How this application says that the catalogue has never heard of a code, in one place.
     *
     * <p>Public and static for the reason {@code AccountsService.noSuchCustomer} is: the sentence
     * is asked for from outside as well as thrown from inside. The administration controller
     * vouches for the code in its path before it does anything with it, and a second wording of
     * "there is no such offer" written over there would be the same absence said two ways.
     */
    public static String noSuchOffer(String code) {
        return "There is nothing called \"" + code + "\" in the rewards catalogue.";
    }

    /**
     * Every offer the catalogue holds, in every state, in the order they were written.
     *
     * <p>{@link TheCatalogueAsItIsRun}'s answer, and so is everything else about running the
     * catalogue: this and the six below open the transaction and hand the question straight on.
     * The argument for each of them is written where each of them is implemented.
     */
    @Transactional(readOnly = true)
    public List<AnOfferAsItStands> everyOffer() {
        return catalogue.everyOffer();
    }

    /** One offer as whoever runs the catalogue reads it, in whatever state it is in. */
    @Transactional(readOnly = true)
    public Optional<AnOfferAsItStands> theOffer(String code) {
        return catalogue.theOffer(code);
    }

    /**
     * What a bundle hands over, and an empty list for everything that is not one.
     *
     * @throws RewardRefused if the catalogue has nothing under that code
     */
    @Transactional(readOnly = true)
    public List<WhatABundleContains> whatIsInside(String code) {
        return catalogue.whatIsInside(code);
    }

    /**
     * Writes a new offer as a draft, refusing anything that is not something an offer may say.
     *
     * @throws OfferRefused if the code is taken, or if what the form asks for is not something an
     *         offer may say about itself
     */
    @Transactional
    public AnOfferAsItStands createADraft(ANewOffer asked) {
        return catalogue.createADraft(asked);
    }

    /**
     * Changes whatever the request named about an offer that already exists, and leaves the rest
     * exactly as it was.
     *
     * @throws RewardRefused if the catalogue has nothing under that code
     * @throws OfferRefused if the offer has been withdrawn, if the change asks for nothing, or if
     *         what it asks for is not something an offer may say
     */
    @Transactional
    public AnOfferAsItStands change(String code, AChangeToAnOffer asked) {
        return catalogue.change(code, asked);
    }

    /**
     * Puts an offer on a customer's screen.
     *
     * @throws RewardRefused if the catalogue has nothing under that code
     * @throws OfferRefused if the offer has been withdrawn
     */
    @Transactional
    public AnOfferAsItStands publish(String code) {
        return catalogue.publish(code);
    }

    /**
     * Takes an offer off every screen without deleting it, so that what was already claimed
     * under it goes on meaning something.
     *
     * @throws RewardRefused if the catalogue has nothing under that code
     */
    @Transactional
    public AnOfferAsItStands withdraw(String code) {
        return catalogue.withdraw(code);
    }

    /**
     * One voucher, by the code printed on it: what it is for, whose it is, and where it is in
     * its life. Looking one up is not marking it used.
     *
     * <p>{@link TheLifeOfAVoucher}'s answer, and so are the two below and the sweep's first
     * step: this opens the transaction and hands the question straight on. The argument for
     * each of them is written where each of them is implemented.
     *
     * @throws VoucherRefused if no voucher has ever carried that code
     */
    @Transactional(readOnly = true)
    public AVoucherAtTheCounter voucherReading(String voucherCode) {
        return vouchers.voucherReading(voucherCode);
    }

    /**
     * Marks a voucher handed over at a counter, once and for ever.
     *
     * @throws VoucherRefused if there is no such voucher, or if it has already been used, has
     *         run out, or was cancelled
     */
    @Transactional
    public AVoucherAtTheCounter handOverTheVoucher(String voucherCode, String counter) {
        return vouchers.handOverTheVoucher(voucherCode, counter);
    }

    /**
     * Revokes a voucher with a reason, refunds the points as a fresh batch, and puts the thing
     * back in the window.
     *
     * @throws VoucherRefused if there is no such voucher, or if it has already been used, has
     *         run out, or was cancelled already
     */
    @Transactional
    public AVoucherAtTheCounter cancelTheVoucher(String voucherCode, String reason) {
        return vouchers.cancelTheVoucher(voucherCode, reason);
    }
}
