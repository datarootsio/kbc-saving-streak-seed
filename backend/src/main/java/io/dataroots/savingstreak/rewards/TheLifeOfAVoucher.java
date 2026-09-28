package io.dataroots.savingstreak.rewards;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.points.PointsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static io.dataroots.savingstreak.rewards.RewardsService.THE_ZONE_IT_READS_IN;
import static io.dataroots.savingstreak.rewards.VoucherRefused.Kind.ALREADY_USED;
import static io.dataroots.savingstreak.rewards.VoucherRefused.Kind.NO_SUCH_VOUCHER;
import static io.dataroots.savingstreak.rewards.VoucherRefused.Kind.THE_VOUCHER_HAS_EXPIRED;
import static io.dataroots.savingstreak.rewards.VoucherRefused.Kind.THE_VOUCHER_WAS_CANCELLED;

/**
 * What becomes of a voucher after it is issued: read at a counter, handed over, revoked by
 * whoever runs the scheme, or quietly retired by the sweep when it outlives its shelf life.
 *
 * <p><strong>The second seam cut out of {@link RewardsService}, and it is the one the spec
 * describes as a life.</strong> A claim used to be the end of the story — the application
 * printed six characters and forgot about them — and everything here is the other end of it.
 * {@code ISSUED} becomes {@code USED}, {@code EXPIRED} or {@code CANCELLED}, all three are
 * terminal, and nothing is ever un-used. Which of the three a voucher reaches, what each of them
 * costs and what each of them gives back is the whole subject of this class, and it is a subject
 * that has nothing to do with what the catalogue says today or with whose turn it is in a queue.
 *
 * <p><strong>The one thing in this module that puts points back.</strong> A cancellation is the
 * scheme's own mistake, so it refunds — as a fresh batch through the Points module's one door,
 * dated now and carrying its own twelve months, never as a restoration of the batches that were
 * spent, which may since have expired. An expiry refunds nothing, because a shelf life that cost
 * nobody anything would mean nothing. The two are deliberately written in terms of each other on
 * {@link #cancelTheVoucher} and {@link #expireVouchersPastTheirDay}: the day either of them
 * starts behaving like the other, a voucher has two ends rather than three.
 *
 * <p><strong>Package-private, behind the same public face.</strong> Nothing outside this module
 * learns that the service was split; {@link RewardsService} still declares every one of these
 * methods and still carries the {@code @Transactional} that opens the transaction. The methods
 * here are package-private and unannotated on purpose — Spring's proxy cannot intercept a
 * package-private method, so an {@code @Transactional} written on one would be an annotation
 * that silently does nothing.
 *
 * <p><strong>There is nothing about who is asking.</strong> Nothing checks that the caller is at
 * a counter or runs the scheme, because there is no authentication in this application to check
 * it with — the same sentence the sign-in endpoint carries about itself, and meant just as
 * literally.
 */
@Component
class TheLifeOfAVoucher {

    private static final Logger log = LoggerFactory.getLogger(TheLifeOfAVoucher.class);

    /**
     * The day a refusal quotes back, written the way the API writes every other date.
     *
     * <p>A day rather than a moment, and a day decided here rather than by whatever machine
     * draws the screen: "used on 14 March" is what somebody at a counter says out loud, and a
     * browser in another zone turning an instant into a date is how a customer gets told the
     * wrong one. The zone it is read in is {@link RewardsService#THE_ZONE_IT_READS_IN}, which is
     * the one the weeks are counted in, named once where it already lives.
     */
    private static final DateTimeFormatter ON_THE_DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RedemptionRepository redemptions;

    /**
     * The offers and the holds, read for one figure: how many of the offer behind a cancelled
     * voucher are left once the cancellation has put one back.
     *
     * <p>It is a figure for the log line and for nothing else — the argument is on
     * {@link #whatIsLeftOfTheOfferBehind} — and it is the whole of this class's business with
     * either table.
     */
    private final RewardOfferRepository offers;
    private final RewardHoldRepository holds;

    /** The one door points go back in through, and the only reason this class knows about them. */
    private final PointsService points;

    private final Clock clock;

    TheLifeOfAVoucher(RedemptionRepository redemptions, RewardOfferRepository offers,
                      RewardHoldRepository holds, PointsService points, Clock clock) {
        this.redemptions = redemptions;
        this.offers = offers;
        this.holds = holds;
        this.points = points;
        this.clock = clock;
    }

    /** The moment, read the way every other moment in this module is read. */
    private Instant theMomentItIs() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * One voucher, by the code printed on it: what it is for, whose it is, and where it is in its
     * life.
     *
     * <p>Looking one up is not marking it used, and keeping the two apart is the whole shape of
     * the counter screen. Somebody at a till reads the thing out to the person in front of them
     * before they hand anything over, and a screen whose only action also spent the voucher would
     * make "let me check" impossible. This one writes nothing and may be asked as often as
     * anybody likes.
     *
     * <p>The code is matched exactly as it was sent. Trimming and upper-casing it happens where
     * the typing happens — a code arrives from a keyboard at a counter, and what to forgive about
     * a keyboard is a question about the surface rather than about the voucher.
     *
     * @throws VoucherRefused if no voucher has ever carried that code
     */
    AVoucherAtTheCounter voucherReading(String voucherCode) {
        AVoucherAtTheCounter voucher = theVoucherUnder(voucherCode).atACounter();
        log.debug("voucher looked up at a counter voucher={} reward={} customerId={} state={}",
                voucher.voucherCode(), voucher.rewardCode(), voucher.customerId(), voucher.state());
        return voucher;
    }

    /**
     * Marks a voucher handed over at a counter, recording the moment and which counter it was.
     *
     * <p><strong>Nothing moves.</strong> No points, no money, no balance and no catalogue figure:
     * the points were spent on the day the voucher was claimed, and this is the other end of that
     * transaction — somebody actually receiving the coffee. A redemption that touched a balance
     * would be charging twice for one thing, and it is worth saying here because this is the one
     * method in the module that changes something and does not.
     *
     * <p><strong>The counter is required and is not checked for being anything in particular.</strong>
     * A voucher marked used by nobody is a redemption nobody can ask about afterwards, so a name
     * has to be given; what a counter is called is not this application's business, and there is
     * no directory of them to check against. Whether one was named at all is read at the edge,
     * where every other "was this field filled in" is read, and what arrives here has already been
     * trimmed.
     *
     * <p>The moment comes from the injected clock and is truncated the way a claim's is, so that a
     * wound-forward clock moves a redemption along with everything else and the moment reported
     * back is the moment later listed.
     *
     * @throws VoucherRefused if no voucher carries that code, or if it has already been handed over
     */
    AVoucherAtTheCounter handOverTheVoucher(String voucherCode, String counter) {
        Redemption claim = theVoucherUnder(voucherCode);
        switch (claim.voucherState()) {
            case ISSUED -> {
                // The only state a voucher can be handed over from. The other three are named
                // below rather than swept into a default, which is what stopped each slice that
                // learned to write one at this switch instead of letting it quietly inherit this
                // branch — and all three of them now answer with a refusal of their own rather
                // than with a fault, which is the shape this switch was waiting to reach.
            }
            case USED -> throw refusingAVoucher(claim, ALREADY_USED,
                    "That voucher was already used at " + claim.usedByCounter() + " on "
                            + ON_THE_DAY.format(claim.usedAt().atZone(THE_ZONE_IT_READS_IN))
                            + ". A voucher can only be handed over once.");
            case EXPIRED -> throw refusingAVoucher(claim, THE_VOUCHER_HAS_EXPIRED,
                    itRanOut(claim));
            case CANCELLED -> throw refusingAVoucher(claim, THE_VOUCHER_WAS_CANCELLED,
                    itWasCancelled(claim));
        }
        Instant clockReads = clock.instant();
        Instant usedAt = clockReads.truncatedTo(ChronoUnit.MILLIS);
        log.debug("a voucher being handed over takes its moment from the application clock "
                + "voucher={} clockReads={} recordedMoment={}", voucherCode, clockReads, usedAt);

        claim.handedOverAt(counter, usedAt);
        AVoucherAtTheCounter handedOver = claim.atACounter();
        log.info("voucher used voucher={} reward={} customerId={} counter={} usedAt={}",
                handedOver.voucherCode(), handedOver.rewardCode(), handedOver.customerId(),
                handedOver.usedByCounter(), handedOver.usedAt());
        return handedOver;
    }

    /**
     * Revokes a voucher an administrator says should never have been issued: the claim is undone,
     * the points go back and the thing goes back in the window.
     *
     * <p><strong>Three consequences in one transaction, and that is the load-bearing guarantee
     * of this method in exactly the way it is of {@link RewardsService#claim}.</strong> A voucher marked
     * cancelled with no points credited would be a customer robbed by a correction; points
     * credited against a voucher still good would be a customer paid twice, once in points and
     * once at a counter. There is no reversal of a cancellation either, so the only protection
     * against half of one is that half of one cannot be committed.
     *
     * <p><strong>The refund is a new batch with a fresh twelve months, and never the batches that
     * were spent put back.</strong> This is the decision the ticket turns on and it is argued at
     * length where the points actually arrive — {@code PointsService.creditCancellationRefund}
     * and {@link io.dataroots.savingstreak.points.PointsReason#REDEMPTION_CANCELLED} — but the
     * short of it belongs here too, because this is where somebody looking for the refund will
     * come first. Points are spent oldest-first, so a claim takes from the batches nearest the
     * end of their own twelve months; a cancellation can arrive months later, by which time some
     * of them have expired, and points returned to an expired batch are counted by nothing and
     * spendable by nobody while the customer has been told they have them back. So the points
     * come in through the one door every other kind of point has ever come through, under a
     * reason of their own, dated now — the pattern gifting and challenges set before this.
     *
     * <p><strong>The stock returns by arithmetic and not by an instruction</strong>, which is why
     * there is no line below that does it. What is left of an offer is its stock less the claims
     * standing against it, counted on every read; this row stops standing, so the next person to
     * read the card is told there is one more of it. The same is true of the two purchase limits.
     * {@code RedemptionRepository.howManyOfEachHaveGone} is where all three of those
     * counts leave the cancelled claims out; the only other count of them anywhere is
     * {@code countByReward}, which answers the administrator lowering a stock figure and leaves
     * them out for the same reason. Those two queries are the whole of the exclusion, and
     * keeping it to two is what makes "does a cancelled claim count?" a question with one
     * answer.
     *
     * <p><strong>Only an issued voucher can be cancelled.</strong> A used one has been handed
     * over — the customer has the thing, and refunding the points as well would be giving them
     * both — and an expired one was the customer's own miss, which is the meaning of a shelf life
     * and would be undone by a cancellation that refunded it anyway. Cancelling a cancelled one
     * would refund twice. All three are refused before anything is written, each with the
     * sentence its own state deserves, and the entity refuses them again underneath for the
     * reason every one-way door here does.
     *
     * <p>The reason is required and is not checked for being anything in particular, exactly as
     * the counter's name is: what a good reason looks like is not this application's business,
     * and there is nothing to check one against. Whether one was given at all is read at the
     * edge, where every other "was this field filled in" is read, and what arrives here has
     * already been trimmed.
     *
     * <p>The moment comes from the injected clock and is truncated the way a claim's and a
     * redemption's are, so that a wound-forward clock moves a cancellation along with everything
     * else and the moment reported back is the moment the customer's own list shows.
     *
     * @throws VoucherRefused if no voucher carries that code, or if it has already been handed
     *         over, has run out, or has been cancelled already
     */
    AVoucherAtTheCounter cancelTheVoucher(String voucherCode, String reason) {
        Redemption claim = theVoucherUnder(voucherCode);
        switch (claim.voucherState()) {
            case ISSUED -> {
                // The only state a voucher can be revoked from, for the reasons above. The other
                // three are named rather than swept into a default, so that a state added to the
                // enum is a compilation failure here rather than a voucher quietly cancelled
                // twice.
            }
            case USED -> throw refusingAVoucher(claim, ALREADY_USED,
                    "That voucher was already used at " + claim.usedByCounter() + " on "
                            + ON_THE_DAY.format(claim.usedAt().atZone(THE_ZONE_IT_READS_IN))
                            + ". The customer has had the thing, so there is nothing to cancel "
                            + "and nothing to give back.");
            case EXPIRED -> throw refusingAVoucher(claim, THE_VOUCHER_HAS_EXPIRED, itRanOut(claim));
            case CANCELLED -> throw refusingAVoucher(claim, THE_VOUCHER_WAS_CANCELLED,
                    itWasCancelled(claim));
        }
        Instant clockReads = clock.instant();
        Instant cancelledAt = clockReads.truncatedTo(ChronoUnit.MILLIS);
        // The inputs behind the decision, before any of it is written: what the claim cost is
        // what is about to be credited, and the two moments are what a customer asking "when did
        // my points come back" is answered with.
        log.debug("a voucher being cancelled takes its moment from the application clock "
                        + "voucher={} clockReads={} recordedMoment={} pointsSpent={} reason={}",
                voucherCode, clockReads, cancelledAt, claim.pointsSpent(), reason);

        claim.cancelledBecause(reason, cancelledAt);
        redemptions.save(claim);
        // The points last, after the state is written, so that a refund can never be credited
        // against a row this transaction failed to move. Through the Points module's one door,
        // as a fresh batch dated now: this module asks for a number of points to be credited and
        // knows nothing about batches, which is the same arrangement it has always had with
        // spending.
        points.creditCancellationRefund(claim.customerId(), claim.id(), claim.pointsSpent(),
                cancelledAt);

        AVoucherAtTheCounter cancelled = claim.atACounter();
        // One line for the business event, carrying all three of the things that moved: which
        // voucher, how many points went back, and what the offer's stock now reads. The stock
        // figures are the half nothing else can reconstruct — "the card said sold out and then it
        // did not" is answered by exactly these three numbers — and they are read after the row
        // was written, so the line says what is true afterwards rather than what was about to be.
        Integer whatIsLeftNow = whatIsLeftOfTheOfferBehind(cancelled.rewardCode());
        log.info("voucher cancelled voucher={} reward={} customerId={} pointsRefunded={} "
                        + "stockReturned=1 whatIsLeftNow={} cancelledAt={} reason={}",
                cancelled.voucherCode(), cancelled.rewardCode(), cancelled.customerId(),
                cancelled.pointsSpent(), whatIsLeftNow, cancelled.cancelledAt(),
                cancelled.cancelledBecause());
        return cancelled;
    }

    /**
     * How many of the offer behind a cancelled voucher are left now, for the line above, and null
     * when the offer never runs out or is not in the catalogue at all.
     *
     * <p>Read after the cancellation rather than worked out from what it was before, because the
     * point of the figure in the log is that it is the one the next customer will be shown. It
     * goes through the same subtraction every other reading goes through, so a log line that
     * disagreed with a card would mean the arithmetic disagreed with itself.
     *
     * <p>An offer that is missing answers null rather than throwing. A claim names its offer as
     * text and an offer is never deleted, so nothing this application does can get here — but a
     * cancellation that failed because the log line could not be assembled would be the record
     * of an event preventing the event.
     *
     * <p>Live holds come off it as well as uncancelled claims, and that arrived in the merge
     * with the slice that added holds. The promise the paragraph above makes is that this figure
     * is the one the next customer will be shown, and held stock is not available to the next
     * customer; a line that counted only claims would have said "whatIsLeftNow=1" on a night
     * every card in the catalogue read sold out. Bundles are not walked here and deliberately
     * so: this is a log line about one offer, and the four-way minimum a bundle's card is drawn
     * from belongs where a card is drawn.
     */
    private Integer whatIsLeftOfTheOfferBehind(String rewardCode) {
        Instant now = theMomentItIs();
        return offers.findByCode(rewardCode)
                .map(offer -> offer.whatIsLeftAfter(
                        redemptions.countByReward(rewardCode, VoucherState.CANCELLED)
                                + holds.howManyAreHeldOf(rewardCode, HoldState.HELD, now)))
                .orElse(null);
    }

    /**
     * The claim behind a voucher code, or a refusal saying there is no such thing.
     *
     * <p>Shared by the reading and the handing over because the sentence a counter gets for a code
     * nobody has heard of should not depend on which button they pressed. The code goes into the
     * sentence, because a mistyped code is how this refusal actually happens and the person has to
     * be able to see what the screen thinks they typed.
     */
    private Redemption theVoucherUnder(String voucherCode) {
        return redemptions.findByVoucherCode(voucherCode).orElseThrow(() -> {
            String reason = "There is no voucher with the code \"" + voucherCode + "\".";
            log.warn("voucher rejected voucher={} kind={} reason={}",
                    voucherCode, NO_SUCH_VOUCHER, reason);
            return new VoucherRefused(NO_SUCH_VOUCHER, reason);
        });
    }

    /**
     * Every refusal at a counter says why in the log as well as to whoever asked, because only one
     * of the two is kept — and this is the one refusal in the module that is read out loud to a
     * customer standing in front of somebody, which is exactly the complaint a log has to be able
     * to answer afterwards.
     */
    /**
     * What a counter is told about a voucher that has run out, in words they can read out loud.
     *
     * <p>The day is in it, because "expired" on its own is an assertion and the person in front of
     * them has a code they believe is good: the date is what turns a refusal into something the
     * two of them can agree about, and it is the same date the customer's own screen has been
     * showing since the day they claimed. It is said as the plain day it is — this one is already
     * a calendar date decided in this application's zone, so unlike the moment a voucher was used
     * there is nothing here to format and nothing to get wrong.
     *
     * <p>That the points are not coming back is said here rather than left for somebody to ask,
     * because it is the first thing the customer will ask and the person at the till should not
     * have to guess. It is also the difference between this and a cancellation.
     *
     * <p>The null branch cannot happen in anything this application wrote: a voucher only reaches
     * {@code EXPIRED} through the sweep, and the sweep only sees vouchers that have a day on them.
     * It is here because a database that has been through something else can carry anything, and
     * a sentence reading "ran out on null" would be worse than one that simply says it has run
     * out.
     */
    /**
     * And what a counter is told about a voucher somebody revoked, in words they can read out
     * loud — including the words whoever revoked it typed.
     *
     * <p>The reason is quoted rather than summarised, and that is the whole of this sentence.
     * "Cancelled" on its own puts the person at the till in the worst position this surface can
     * put them in: the code is real, the customer is standing there believing it is good, and
     * nothing either of them did caused it. What makes that conversation possible is the
     * sentence somebody actually wrote when they cancelled it, which is why the reason is a
     * required field and why it is stored rather than logged.
     *
     * <p>That the points went back is said here as well, and it is the opposite of the sentence
     * an expiry gets. It is the first thing the customer will ask, it is true, and it is the one
     * piece of good news in the exchange; leaving the person at the till to guess at it would
     * have them either promising something they cannot see or denying something that already
     * happened.
     *
     * <p>The blank branch cannot happen in anything this application wrote: a reason is required
     * at the edge and the column is written in the same breath as the state. It is here because
     * a database that has been through something else can carry anything, and a sentence reading
     * {@code cancelled: null} would be worse than one that simply says it was cancelled.
     */
    private static String itWasCancelled(Redemption claim) {
        String opening = "That voucher was cancelled and cannot be handed over.";
        String refund = " The points that paid for it have been given back.";
        if (claim.cancelledBecause() == null || claim.cancelledBecause().isBlank()) {
            return opening + refund;
        }
        return opening + " The reason given was: " + claim.cancelledBecause() + "." + refund;
    }

    private static String itRanOut(Redemption claim) {
        if (claim.expiresOn() == null) {
            return "That voucher has expired and cannot be handed over. The points that paid for "
                    + "it are not coming back — an expiry is not a cancellation.";
        }
        return "That voucher ran out on " + claim.expiresOn() + " and cannot be handed over. The "
                + "points that paid for it are not coming back — an expiry is not a cancellation.";
    }

    private VoucherRefused refusingAVoucher(Redemption claim, VoucherRefused.Kind kind,
                                            String reason) {
        log.warn("voucher rejected voucher={} state={} kind={} reason={}",
                claim.voucherCode(), claim.voucherState(), kind, reason);
        return new VoucherRefused(kind, reason);
    }

    /**
     * Retires every voucher whose last good day is behind us, and answers how many went.
     *
     * <p><strong>The first step of the scheme's one nightly sweep</strong>, called by
     * {@link TheRewardsSweepRunsNightly}, which owns the order the steps run in and says why.
     * The day is handed in rather than read from the clock here, so that everything one night
     * retires is judged against one day — and so that this method is a function of a date rather
     * than of when somebody happened to call it.
     *
     * <p><strong>Nothing is refunded and nothing is returned.</strong> No points go back into the
     * ledger, no stock goes back into the window, the claim goes on counting against every limit
     * it counted against before, and no notification is raised. That is not a simplification: it
     * is the whole meaning of a shelf life. An expiry is the customer's own miss, and a scheme
     * that handed the points back for one would be a scheme in which the deadline cost nobody
     * anything and nobody ever had a reason to use a voucher in time. The thing that <em>does</em>
     * refund is {@link #cancelTheVoucher}, because that one is the scheme's mistake rather than
     * the customer's, and it is why the two are different states rather than one. The two are
     * meant to be read against each other and their paragraphs are kept in step deliberately:
     * the day either of them starts refunding is the day a shelf life stops meaning anything.
     *
     * <p>Whatever the clock says, a voucher whose offer set no shelf life is never here: the query
     * cannot see a voucher with no day on it, and the reasoning is written out on the query
     * itself.
     *
     * <p>One transaction for the whole sweep, like the claim it undoes nothing of. Either the
     * night's expiries are all written or none of them are, and a half-swept night would be a set
     * of vouchers whose state depended on where the run stopped.
     */
    int expireVouchersPastTheirDay(LocalDate today) {
        List<Redemption> pastTheirDay =
                redemptions.vouchersThatRanOutBefore(VoucherState.ISSUED, today);
        // The window the database was asked for, before anything is written, so that a sweep that
        // retired nothing can be told from a sweep that was handed nothing to look at.
        log.debug("vouchers considered for expiry today={} state={} vouchers={}",
                today, VoucherState.ISSUED, pastTheirDay.size());
        for (Redemption voucher : pastTheirDay) {
            voucher.ranOut();
            // One line per voucher, at DEBUG for the reason the points sweep gives for its own:
            // the totals are the line worth grepping, and this is the half of the answer the
            // totals cannot give — which code went, and which day it had been promised. The two
            // noughts are said out loud rather than left to be inferred from silence, because
            // "an expiry refunds nothing" is the rule this whole slice exists to make true and a
            // log that never mentions it is a log that cannot be used to check it.
            log.debug("voucher expired voucher={} reward={} customerId={} expiresOn={} today={} "
                            + "pointsRefunded=0 stockReturned=0",
                    voucher.voucherCode(), voucher.reward(), voucher.customerId(),
                    voucher.expiresOn(), today);
        }
        // Managed rows would be written out at the end of the transaction anyway; saying so leaves
        // nothing for a reader to infer from Hibernate's behaviour, as the points sweep does.
        redemptions.saveAll(pastTheirDay);
        return pastTheirDay.size();
    }
}
