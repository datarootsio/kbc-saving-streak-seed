package io.dataroots.savingstreak.gifting;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * How long the typed figure is allowed to be before it is refused on its length alone, without
     * being read as a number at all.
     *
     * <p>It exists because reading a figure is not free the way reading a short one is. Both
     * {@code new BigDecimal(String)} and {@code stripTrailingZeros()} are quadratic in the number of
     * digits handed to them, and nothing upstream of here limits how many arrive — the body of a
     * request is not capped, so a megabyte of digits is a megabyte of digits. Measured, that is
     * seconds of a core at a hundred thousand digits and minutes at a million, spent before the
     * request has so much as looked at the database, for an answer that could only ever have been a
     * shortfall. A few of those at once are the application unusable. The bound is what makes the
     * cost of reading the figure constant instead of unbounded.
     *
     * <p>Sixty-four characters, which is generous on purpose. A number of points anybody could hold
     * fits in nineteen — {@code Long.MAX_VALUE} is 9223372036854775807 and a pot counts points in a
     * {@code long} — so the bound is more than three times the longest figure that could ever be
     * meant, and further still past the longest a person types by hand. That is the point: sixty-four
     * digits cost nothing at all to read, so being liberal here buys a real figure and a plain typo
     * alike the answer they deserve, quoting every character of what was typed, and only something
     * nobody could have typed on purpose is turned away unread.
     */
    private static final int MOST_CHARACTERS_A_NUMBER_OF_POINTS_CAN_NEED = 64;

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
     * banks under — including no recipient given at all — a gift to yourself, a figure that is not a
     * positive whole number of points, and a gift larger than the sender's balance. A figure so long
     * that reading it would cost more than the request is worth is refused as a shortfall, unread.
     * The first four are settled before anything at all is written. The fifth is not: how many
     * points somebody holds needs no gift identifier and could have been asked before the row was
     * saved, but it is the ledger's answer to the move rather than a question asked ahead of it, so
     * it comes back after the save. That is what the save-before-move order below concedes, and the
     * rollback is what makes it safe.
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
        // No address at all is nobody banking under it, which is the refusal it already has. Said
        // here rather than left to Accounts because looking somebody up trims the address first and
        // would throw on nothing at all — a fault rather than one of the five refusals this method
        // promises. The endpoint asks for the address before it gets this far, in its own words; this
        // is for every caller that is not the endpoint.
        if (recipientContactDetails == null || recipientContactDetails.isBlank()) {
            throw refusing(senderCustomerId, recipientContactDetails, NO_SUCH_RECIPIENT,
                    AccountsService.noCustomerBanksUnderThoseContactDetails());
        }
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
     * Every gift this customer was part of, newest first, each one read from where they stand: the
     * ones they sent marked {@link GiftDirection#SENT} and the ones they received marked
     * {@link GiftDirection#RECEIVED}.
     *
     * <p>One list rather than two, because the whole story of somebody's gifting reads
     * chronologically and direction is a property of who is reading a row rather than of the row —
     * the idiom the money-movement ledger already set. One row per gift is what makes that possible:
     * there is no second row on the sender's side to reconcile.
     *
     * <p>It is asked here rather than derived, and that is why the figure is stored: this list
     * outlives the points. A gift stays in it after its points have been spent, given onward or
     * expired, because what somebody did is not undone by what later happened to the points they did
     * it with.
     *
     * <p>Whether the customer exists is not asked. A customer who has been part of no gifts has an
     * empty list, and one who does not exist is a mistake about who — a distinction the endpoint
     * draws, in the words and the status every other per-customer read of this application uses.
     */
    @Transactional(readOnly = true)
    public List<GiftGiven> giftsOf(long customerId) {
        List<Gift> partOf = gifts
                .findBySenderCustomerIdOrRecipientCustomerIdOrderByGivenAtDescIdDesc(customerId, customerId);
        // Names are asked of Accounts per customer appearing in the list rather than per row, because
        // a list of gifts between the same two people would otherwise ask the same question once a
        // row. Nothing about a name is stored on a gift, so somebody renamed is renamed in every gift
        // they were ever part of.
        Map<Long, String> namesById = new HashMap<>();
        List<GiftGiven> listed = partOf.stream()
                .map(gift -> new GiftGiven(gift.getId(),
                        gift.getSenderCustomerId() == customerId
                                ? GiftDirection.SENT : GiftDirection.RECEIVED,
                        gift.getSenderCustomerId(), nameOf(gift.getSenderCustomerId(), namesById),
                        gift.getRecipientCustomerId(), nameOf(gift.getRecipientCustomerId(), namesById),
                        gift.getPoints(), gift.getGivenAt()))
                .toList();
        log.debug("gifts listed customerId={} gifts={} people={}",
                customerId, listed.size(), namesById.size());
        return listed;
    }

    /**
     * What one of the two customers on a gift is called, asked once however many rows name them.
     *
     * <p>A gift can only have been made between two customers who existed, so one this application
     * has never heard of is not a refusal anybody can act on — it is the record having gone wrong,
     * and it says so rather than reporting a gift with a blank beside it.
     */
    private String nameOf(long customerId, Map<Long, String> namesById) {
        return namesById.computeIfAbsent(customerId, id -> accounts.customerWith(id)
                .map(Customer::getName)
                .orElseThrow(() -> new IllegalStateException("a gift names customer " + id
                        + ", which this application has never heard of")));
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
        // Refused on the length of the characters, before BigDecimal is handed them, because reading
        // them is what costs: see MOST_CHARACTERS_A_NUMBER_OF_POINTS_CAN_NEED for how much and why.
        // Answered as a shortfall rather than as a typo, and in the sentence a shortfall already has,
        // because a figure this long is one: nobody holds that many points, and the person is told
        // how many they do hold, which is what they need in order to correct it. The quote is cut to
        // the same bound — a refusal that hands a megabyte of digits back to whoever sent them is the
        // other half of the same waste, and the first sixty-four characters are more than enough for
        // anybody to recognise what they typed.
        if (typed.length() > MOST_CHARACTERS_A_NUMBER_OF_POINTS_CAN_NEED) {
            String asMuchOfItAsIsWorthQuoting =
                    typed.substring(0, MOST_CHARACTERS_A_NUMBER_OF_POINTS_CAN_NEED) + "...";
            // Which of the two refusals it is, decided without parsing: a single pass over the
            // characters says whether this could be a number at all, and that pass is linear where
            // reading it as one is quadratic. It matters because the two answers tell the person
            // different things to do next — a wall of letters is a typo to retype, and a wall of
            // digits is a figure to reduce — and a shortfall is the wrong sentence for text that was
            // never a number. A previous review left this wording to be settled here.
            if (containsSomethingNoNumberContains(typed)) {
                throw refusing(senderCustomerId, recipientContactDetails, NOT_A_NUMBER_OF_POINTS,
                        "A gift is a whole number of points, and \"" + asMuchOfItAsIsWorthQuoting
                                + "\" is not a number.");
            }
            throw refusing(senderCustomerId, recipientContactDetails, NOT_ENOUGH_POINTS,
                    "That gift costs " + asMuchOfItAsIsWorthQuoting + " points, and you have "
                            + points.balanceOf(senderCustomerId) + ".");
        }
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
        //
        // Guarded, because stripping is the one thing here that can fail on a figure that parsed
        // perfectly well. It takes a zero off the digits and a one off the scale each time round, and
        // a scale that walks past Integer.MIN_VALUE throws rather than answering — so a figure with a
        // huge positive exponent and two or more trailing zeroes ("100e2147483647") faults out of the
        // middle of a method whose entire job is to answer in words. Every figure that can reach the
        // catch is enormous and positive: signum is already known to be positive above, and only an
        // exponent big enough to bottom the scale out can overflow it. So it is the same shortfall the
        // figures too large to count in a long get, and it is answered in the same sentence.
        BigDecimal withoutTrailingZeroes;
        try {
            withoutTrailingZeroes = figure.stripTrailingZeros();
        } catch (ArithmeticException theScaleRanOutOfRoom) {
            throw moreThanAnybodyCouldEverHold(senderCustomerId, recipientContactDetails, typed);
        }
        if (withoutTrailingZeroes.scale() > 0) {
            throw refusing(senderCustomerId, recipientContactDetails, NOT_A_NUMBER_OF_POINTS,
                    "Points are whole, and " + typed + " is not a whole number.");
        }
        try {
            return figure.longValueExact();
        } catch (ArithmeticException tooLargeToCountInPoints) {
            throw moreThanAnybodyCouldEverHold(senderCustomerId, recipientContactDetails, typed);
        }
    }

    /**
     * Whether the text holds a character that appears in no number at all, in one pass and without
     * reading any of it as a figure.
     *
     * <p>Deliberately only that much. It says "this is certainly not a number", never "this is a
     * number": text made only of these characters can still be nonsense ("1e2e3"), and an over-long
     * figure that is number-shaped is answered as the shortfall it almost certainly is. Telling those
     * apart exactly would mean parsing the figure, which is the cost {@link
     * #MOST_CHARACTERS_A_NUMBER_OF_POINTS_CAN_NEED} exists to avoid — and it is only ever asked about
     * text far longer than anybody types on purpose.
     */
    private static boolean containsSomethingNoNumberContains(String typed) {
        for (int at = 0; at < typed.length(); at++) {
            char character = typed.charAt(at);
            if ((character < '0' || character > '9') && "+-.eE".indexOf(character) < 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * A whole positive figure larger than a pot could ever count. It is a shortfall rather than a
     * typo — nobody holds that many — so it is answered as one, with the balance quoted, in the
     * sentence an ordinary over-balance gift already gets.
     *
     * <p>Shared by the two ways a figure can turn out to be that large, because they are the same
     * refusal seen from either end of {@link BigDecimal}: one is a figure too big to fit in a
     * {@code long}, the other a figure whose exponent is too big for the class itself to keep
     * working with. Neither is anything the person typing needs told apart.
     */
    private GiftRefused moreThanAnybodyCouldEverHold(long senderCustomerId,
                                                     String recipientContactDetails, String typed) {
        return refusing(senderCustomerId, recipientContactDetails, NOT_ENOUGH_POINTS,
                "That gift costs " + typed + " points, and you have "
                        + points.balanceOf(senderCustomerId) + ".");
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
