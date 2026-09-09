package io.dataroots.savingstreak.gifting;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.Customer;
import io.dataroots.savingstreak.points.MovedPoints;
import io.dataroots.savingstreak.points.PointsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.gifting.GiftRefused.Kind.NOT_A_NUMBER_OF_POINTS;
import static io.dataroots.savingstreak.gifting.GiftRefused.Kind.NOT_ENOUGH_POINTS;
import static io.dataroots.savingstreak.gifting.GiftRefused.Kind.NO_SUCH_CUSTOMER;
import static io.dataroots.savingstreak.gifting.GiftRefused.Kind.NO_SUCH_RECIPIENT;
import static io.dataroots.savingstreak.gifting.GiftRefused.Kind.TO_YOURSELF;

/**
 * The Gifting module's face to the rest of the application: one customer handing points to another.
 *
 * <p>Built in {@link io.dataroots.savingstreak.rewards.RewardsService}'s image, because a claim and
 * a gift are the same shape of act — check a customer, take points off them, stamp the moment off
 * the clock, write a row, hand back what happened. Its dependencies are that service's dependencies:
 * Accounts to say who exists and what they are called, Points to move the points, the clock to say
 * when, and a repository of its own to remember it.
 *
 * <p>It owns the rule and the refusals, and it does not own the points. How many somebody has, which
 * of theirs leave first, and what the batches underneath are dated at is the Points module's answer;
 * this module asks for a number of them to be moved and is told what moved or that there were not
 * enough. Points is the wrong home for the rule because the rule is about two people and the ledger
 * has no opinion about people; Accounts is the wrong home because it knows people and nothing about
 * points.
 *
 * <p>A gift is final the moment it is made. There is no acceptance step, no pending state, no
 * cancel window and no reversal, so there is nothing here that could leave a gift half made — and
 * the one transaction below is what makes that true rather than intended.
 */
@Service
public class GiftingService {

    private static final Logger log = LoggerFactory.getLogger(GiftingService.class);

    private final GiftRepository gifts;
    private final AccountsService accounts;
    private final PointsService points;
    private final Clock clock;

    GiftingService(GiftRepository gifts, AccountsService accounts, PointsService points, Clock clock) {
        this.gifts = gifts;
        this.accounts = accounts;
        this.points = points;
        this.clock = clock;
    }

    /**
     * Gives points from one customer to another, and answers with the gift that was made.
     *
     * <p>The points come off the sender's oldest batches first — the same draw a reward claim makes
     * — and each slice arrives in the recipient's pot dated at the moment the batch it came out of
     * was earned. A gift drawn from three batches of different ages therefore arrives as three
     * batches of different ages. That is the one decision in this feature a customer would not have
     * guessed, and it is what stops unlimited gifting from being an expiry-laundering machine:
     * {@link PointsService#movePoints} carries the reasoning where the dating happens.
     *
     * <p>Five things are refused and nothing else: a sender nobody has heard of, a recipient nobody
     * banks under, a gift to yourself, a figure that is not a positive whole number of points, and a
     * gift larger than the sender's balance. The first four are settled before anything at all is
     * written. The fifth is not: how many points somebody holds needs no gift identifier and could
     * have been asked before the row was saved, but it is the ledger's answer to the move rather
     * than a question asked ahead of it, so it comes back after the save. That is what the
     * save-before-move order below concedes, and the rollback is what makes it safe.
     *
     * <p>The gift row is saved <em>before</em> the points move, which is the opposite order to a
     * reward claim — that spends the points and then saves the redemption. It has to be this way
     * round because the batches arriving in the recipient's pot carry this gift's identifier, and
     * there is no identifier until the row exists. What makes it safe is that the whole method is
     * one transaction: a gift refused for want of points throws after the row was saved and leaves
     * no row behind, exactly as a claim refused for want of points leaves no redemption.
     *
     * <p>Nothing else moves. No euros, no week secured, no streak changed, nothing in the ledger of
     * money that moved — a gift moves points and only points.
     *
     * @param pointsAsTyped the figure exactly as the customer typed it, so that "2.5" and "abc" are
     *                      ruled on here and answered in words rather than being coerced into
     *                      something plausible on the way in
     * @throws GiftRefused if the gift is one of the five this module will not make
     */
    @Transactional
    public GiftGiven give(long senderCustomerId, String recipientContactDetails, String pointsAsTyped) {
        Customer sender = accounts.customerWith(senderCustomerId)
                .orElseThrow(() -> refusing(senderCustomerId, recipientContactDetails, NO_SUCH_CUSTOMER,
                        AccountsService.noSuchCustomer(senderCustomerId)));
        // Found the way signing in finds somebody: trimmed, matched without regard to case. A
        // customer names their recipient by the address that person banks under, and it should not
        // matter that they capitalised it.
        Customer recipient = accounts.customerIdentifiedBy(recipientContactDetails)
                .orElseThrow(() -> refusing(senderCustomerId, recipientContactDetails, NO_SUCH_RECIPIENT,
                        AccountsService.noCustomerBanksUnderThoseContactDetails()));
        if (recipient.getId().equals(sender.getId())) {
            throw refusing(senderCustomerId, recipientContactDetails, TO_YOURSELF,
                    "A gift goes to somebody else, and " + recipient.getName()
                            + " is who you are signed in as.");
        }
        long pointsToGive = wholePositivePointsIn(senderCustomerId, recipientContactDetails, pointsAsTyped);
        // Read only when somebody is listening, because it is a query of its own: the sender's whole
        // balance is not needed to make the gift — Points counts the batches it draws from — and it
        // is the figure a reviewer checks the refusal boundary against.
        if (log.isDebugEnabled()) {
            log.debug("gift judged against the sender's pot senderCustomerId={} recipientCustomerId={} "
                            + "points={} senderBalance={}",
                    senderCustomerId, recipient.getId(), pointsToGive, points.balanceOf(senderCustomerId));
        }
        // One moment for the row and the batches, read from the application's clock and truncated the
        // way a claim's and a deposit's are, so that the moment reported back is the moment the gift
        // is later listed under — and so that a clock wound forward moves gifts along with deposits.
        Instant clockReads = clock.instant();
        Instant givenAt = clockReads.truncatedTo(ChronoUnit.MILLIS);
        log.debug("gift takes its moment from the application clock senderCustomerId={} "
                + "clockReads={} recordedMoment={}", senderCustomerId, clockReads, givenAt);

        Gift gift = gifts.save(Gift.of(sender.getId(), recipient.getId(), pointsToGive, givenAt));
        List<MovedPoints> moved = points
                .movePoints(sender.getId(), recipient.getId(), pointsToGive, gift.getId())
                // Read after the refusal rather than before the attempt: nothing was taken, so this
                // is still what they have, and it is the figure the person needs in order to correct
                // the gift without going to look it up. The same shape of sentence a reward claim
                // gives for the same shortfall.
                .orElseThrow(() -> refusing(senderCustomerId, recipientContactDetails, NOT_ENOUGH_POINTS,
                        "That gift costs " + pointsToGive + " points, and you have "
                                + points.balanceOf(senderCustomerId) + "."));
        // What the gift was drawn from as this module can honestly describe it: how much came out of
        // each slice and when each of those points was originally earned, which is the inherited
        // dating a reviewer checks by hand. Gathered into one line and guarded, because rendering the
        // slices is work and the string is thrown away when the application runs at INFO. The other
        // half of the same picture — which batch each slice came off of and what was left in it — is
        // the ledger's own DEBUG line, because a batch identifier is not this module's to know.
        if (log.isDebugEnabled()) {
            log.debug("gift drawn from the sender's oldest points first giftId={} senderCustomerId={} "
                            + "recipientCustomerId={} points={} slices={} drawnOn={}",
                    gift.getId(), sender.getId(), recipient.getId(), pointsToGive, moved.size(),
                    moved.stream()
                            .map(slice -> "[points=" + slice.points() + " earnedAt=" + slice.earnedAt() + "]")
                            .collect(Collectors.joining(" ")));
        }
        // One line per gift with everything that decided it. A balance that moved without a deposit
        // and without a claim is explainable from this line alone, at both ends.
        log.info("gift given giftId={} senderCustomerId={} recipientCustomerId={} points={} givenAt={}",
                gift.getId(), sender.getId(), recipient.getId(), gift.getPoints(), gift.getGivenAt());
        return new GiftGiven(gift.getId(), GiftDirection.SENT, sender.getId(), sender.getName(),
                recipient.getId(), recipient.getName(), gift.getPoints(), gift.getGivenAt());
    }

    /**
     * The whole positive number of points the customer typed, or a refusal saying what was wrong
     * with what they typed.
     *
     * <p>Ruled on here rather than read as a number on the way in, for the reason a deposit's amount
     * gives: some of what has to be said about a figure is about the characters — "2.5" is a mistake
     * somebody makes and deserves an answer about points, not about a request that could not be read
     * — and the text is the only form that still has them. Nothing is rounded or coerced, because a
     * bank that quietly decides what a figure was meant to say is worse than one that asks.
     */
    private long wholePositivePointsIn(long senderCustomerId, String recipientContactDetails,
                                       String pointsAsTyped) {
        String typed = pointsAsTyped == null ? "" : pointsAsTyped.trim();
        BigDecimal figure;
        try {
            figure = new BigDecimal(typed);
        } catch (NumberFormatException notANumberAtAll) {
            throw refusing(senderCustomerId, recipientContactDetails, NOT_A_NUMBER_OF_POINTS,
                    "A gift is a whole number of points, and \"" + typed + "\" is not a number.");
        }
        // Every sentence from here on quotes the characters that were typed, and never the figure
        // they parsed into. BigDecimal reads an exponent of any size for almost nothing —
        // "1e999999999" is a couple of small fields — and it is writing that figure back out that
        // costs: toPlainString() of "1.5e-999999999" is a billion characters and takes the heap with
        // it, and toBigInteger() of "1e999999999" throws rather than answering, out of the middle of
        // a refusal that was on its way to being reported in words. A refusal that kills the request
        // is not a refusal in words, which is the whole reason the figure is carried as text. So the
        // typed characters are what every one of these sentences quotes — the cheap answer and the
        // honest one at once, because they are what the person can see they typed.
        if (figure.signum() <= 0) {
            throw refusing(senderCustomerId, recipientContactDetails, NOT_A_NUMBER_OF_POINTS,
                    "A gift has to be more than zero points, and " + typed + " is not.");
        }
        // Trailing zeroes stripped first, so that "5.0" is the whole number somebody meant and only
        // a figure with something after the point is refused.
        if (figure.stripTrailingZeros().scale() > 0) {
            throw refusing(senderCustomerId, recipientContactDetails, NOT_A_NUMBER_OF_POINTS,
                    "Points are whole, and " + typed + " is not a whole number.");
        }
        try {
            return figure.longValueExact();
        } catch (ArithmeticException moreThanAnybodyCouldEverHold) {
            // A whole positive figure too large to count in points. It is a shortfall rather than a
            // typo — nobody holds that many — so it is answered as one, with the balance quoted.
            throw refusing(senderCustomerId, recipientContactDetails, NOT_ENOUGH_POINTS,
                    "That gift costs " + typed + " points, and you have "
                            + points.balanceOf(senderCustomerId) + ".");
        }
    }

    /**
     * Every refusal says why in the log as well as to whoever asked, because only one of the two is
     * kept: the reason reaches the person at the keyboard and nowhere else, and the log is the only
     * copy anybody reviewing this afterwards can read.
     *
     * <p>The recipient is logged as it was given rather than as it was found, because a refusal for
     * an address nobody banks under has nothing to have found — and that is the case a reviewer
     * tracing somebody's complaint most needs the typed text for.
     */
    private GiftRefused refusing(long senderCustomerId, String recipientAsGiven, GiftRefused.Kind kind,
                                 String reason) {
        log.warn("gift rejected senderCustomerId={} recipientAsGiven={} kind={} reason={}",
                senderCustomerId, recipientAsGiven, kind, reason);
        return new GiftRefused(kind, reason);
    }
}
