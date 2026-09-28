package io.dataroots.savingstreak.rewards;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static io.dataroots.savingstreak.rewards.OfferRefused.Kind.AGAINST_THE_RULES;
import static io.dataroots.savingstreak.rewards.OfferRefused.Kind.CODE_ALREADY_TAKEN;
import static io.dataroots.savingstreak.rewards.OfferRefused.Kind.NO_SUCH_MEMBER;
import static io.dataroots.savingstreak.rewards.OfferRefused.Kind.STOCK_BELOW_WHAT_IS_ALREADY_OUT;
import static io.dataroots.savingstreak.rewards.OfferRefused.Kind.THE_OFFER_IS_WITHDRAWN;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.NO_SUCH_OFFER;

/**
 * The catalogue as somebody runs it: writing an offer, editing one, publishing it, taking it down,
 * and every rule about what an offer may say about itself.
 *
 * <p><strong>The first of the two seams cut out of {@link RewardsService}, and the easiest one to
 * defend.</strong> That class had grown to about 3,800 lines because seven slices written in
 * parallel each appended their own validation block and their own parameter to it, and by the end
 * it held three separable concerns: the catalogue somebody runs, the voucher's life after it is
 * issued, and the claim-and-reservation pipeline. This is the first of the three. Nothing in here
 * knows what a claim is, what a hold is or what a queue is; it reads the claims table exactly once,
 * to refuse a stock figure below what has already gone out, and it reads the holds table in the
 * same breath and for the same sentence. Everything else here is an offer and the words on it.
 *
 * <p><strong>Package-private, behind the same public face.</strong> Nothing outside this module
 * learns that the service was split: {@link RewardsService} still declares every one of these
 * methods, still carries the {@code @Transactional} that opens the transaction, and still refuses
 * everything this module refuses. That is deliberate and it is the constraint the split was made
 * under — a module with one public service has one public service, and turning one into three
 * would have made every caller in the web layer learn a shape that is none of its business. What
 * a split is worth here is entirely internal: a reader looking for the rule about a bundle's
 * members no longer reads past the whole of the claim path to find it.
 *
 * <p><strong>The transaction stays on the face and not here.</strong> Every method below is
 * package-private and unannotated, because Spring's proxy cannot intercept a package-private
 * method and an {@code @Transactional} written on one would be an annotation that silently does
 * nothing — the very worst kind. The boundary is where it always was: a caller enters through
 * {@link RewardsService}, whose method opens the transaction, and everything here runs inside it.
 *
 * <p><strong>There is nothing about who is asking, anywhere in this class.</strong> Nothing checks
 * that the caller runs the scheme, because there is no authentication in this application to check
 * it with. This is an administration surface and not authorisation, and the difference is the
 * whole of what a later feature would add — the same sentence the sign-in endpoint carries about
 * itself, and it is meant just as literally here.
 */
@Component
class TheCatalogueAsItIsRun {

    private static final Logger log = LoggerFactory.getLogger(TheCatalogueAsItIsRun.class);

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

    /**
     * The claims and the holds, read in one place only: the stock figure an edit may not go
     * below.
     *
     * <p>They are the one thing about an offer that cannot be decided from the offer, and they
     * are the whole of this class's business with either table. Everything else these two answer
     * — what a card says, what a claim costs, whose turn it is — belongs to the pipeline and is
     * none of a form's concern.
     */
    private final RedemptionRepository redemptions;
    private final RewardHoldRepository holds;

    private final Clock clock;

    TheCatalogueAsItIsRun(RewardOfferRepository offers, OfferMemberLineRepository memberLines,
                          RedemptionRepository redemptions, RewardHoldRepository holds,
                          Clock clock) {
        this.offers = offers;
        this.memberLines = memberLines;
        this.redemptions = redemptions;
        this.holds = holds;
        this.clock = clock;
    }

    /** The moment, read the way every other moment in this module is read. */
    private Instant theMomentItIs() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * Every offer the catalogue holds, in every state, in the order they were written.
     *
     * <p>The administration screen's read, and the one place a draft and a withdrawn offer are
     * visible at all. It is deliberately the whole catalogue rather than a page of it: this is a
     * training application whose catalogue is four entries and whatever a session has added, and
     * paging would be machinery for a problem that does not exist here.
     *
     * <p><strong>There is nothing about who is asking.</strong> Nothing checks that the caller
     * runs the scheme, because there is no authentication in this application to check it with.
     * This is an administration screen and not authorisation, and the difference is the whole of
     * what a later feature would add — the same sentence the sign-in endpoint carries about
     * itself, and it is meant just as literally here.
     */
    List<AnOfferAsItStands> everyOffer() {
        List<RewardOffer> everyOne = offers.findAllByOrderByIdAsc();
        // The lines for the whole list in one read rather than one read per bundle, exactly as
        // the customer's reading does it, and free when there are none — which is every
        // catalogue until somebody composes one.
        // No claims and no holds handed in, because this reading answers neither question: an
        // administrator's list says what each offer's stock figure is rather than what is left
        // of it, so the only thing wanted from the fold is what each bundle contains.
        TheBundlesAsTheyStand bundles = TheBundlesAsTheyStand.of(
                memberLines.findAllByOrderByIdAsc(), everyOne, Map.of(), Map.of());
        return everyOne.stream().map(offer -> offer.asItStands(bundles.inside(offer.code())))
                .toList();
    }

    /**
     * One offer as it stands, whatever state it is in, or nothing if the catalogue has no such
     * code.
     *
     * <p>An {@link Optional} rather than a refusal, because the caller is the administration
     * controller answering for an address in its own path: an offer that is not there is a 404
     * about a URL rather than a rule about the catalogue, and it is answered in the words above.
     * The mutators below still refuse for themselves, because a caller that did not look first is
     * not a caller this module can rely on having looked.
     */
    Optional<AnOfferAsItStands> theOffer(String code) {
        return offers.findByCode(code).map(offer -> offer.asItStands(whatIsInside(offer)));
    }

    /**
     * What one offer hands over, and nothing at all when it is not a bundle.
     *
     * <p><strong>Public, because a counter needs it and a voucher cannot carry it.</strong> A
     * voucher is a claim, and what a claim snapshots is what the spec says it snapshots: the
     * code, the title and the points spent. A bundle's contents are deliberately not on that
     * list. They are a fact about the catalogue rather than about the purchase — the lines are
     * never edited once a bundle is composed, and the offers in them are never deleted — so
     * reading them live tells somebody at a till exactly what to put on the counter, while
     * snapshotting them would have meant columns on the claim table, which is the one table in
     * this application whose shape is dangerous to move.
     *
     * <p>Answered here rather than folded onto the voucher's own reading, because the voucher's
     * reading is built from the claim and this is built from the catalogue. The web layer puts
     * the two together, which is exactly what it already does when it hangs the holder's name
     * off a voucher by asking Accounts.
     *
     * <p>Empty for an item, for a code the catalogue has never heard of, and for a bundle whose
     * members have somehow gone — a list, in every case, because the caller draws a list.
     */
    List<WhatABundleContains> whatIsInside(String code) {
        return offers.findByCode(code).map(this::whatIsInside).orElseGet(List::of);
    }

    /**
     * The same answer for a row already in hand, which is how every reading of one offer gets it.
     *
     * <p>A query per line, and that is the right shape here precisely because it is the wrong
     * shape for the catalogue: this is one offer, asked for by somebody who named it, and a
     * bundle has two or three lines. The reading that draws forty cards is the one that folds
     * every line and every offer into one pass, and it is beside it above.
     */
    private List<WhatABundleContains> whatIsInside(RewardOffer offer) {
        if (!offer.isABundle()) {
            return List.of();
        }
        return memberLines.findByBundleCodeOrderByIdAsc(offer.code()).stream()
                .map(line -> offers.findByCode(line.memberCode())
                        .map(member -> new WhatABundleContains(member.code(), member.title(),
                                line.quantity()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Writes a new offer into the catalogue as a draft, invisible to customers until somebody
     * publishes it.
     *
     * <p>A draft and never anything else. The reason the state exists is that a half-written
     * reward should never be on a customer's screen, and an offer that arrived published would
     * make the read-back optional.
     *
     * <p>The rules are checked here rather than at the edge, all of them, because they are rules
     * about the catalogue rather than facts about the request: what a title has to be, what the
     * least an offer may cost is, and whether a code is free are things this module knows and the
     * web layer would have to be told. The code is checked last of them, so that a form with a
     * blank title and a taken code is answered about the blank title — the mistake somebody made,
     * rather than the one they were about to.
     *
     * <p><strong>A bundle is composed here and only here.</strong> An offer written with member
     * lines is a bundle; one written without them is an item. There is no way to add a line to
     * an offer afterwards, to take one out, or to turn an item into a bundle, and that is a
     * decision rather than a gap: a bundle's contents are what a voucher already issued for it
     * promises, and this application does not snapshot them onto the claim — a counter reads
     * them off the catalogue when somebody hands the voucher over. Contents that could be edited
     * would therefore quietly rewrite what somebody already bought, which is the one thing the
     * price snapshot exists to prevent. Somebody who wants a different hamper composes a
     * different hamper, which is honest about it being a different one, and withdraws the first.
     *
     * @throws OfferRefused if the code is already in use, if what arrived does not describe an
     *         offer this application will keep, or if what it says goes into it is not two or
     *         more offers that exist, are not withdrawn, and are not themselves bundles
     */
    AnOfferAsItStands createADraft(ANewOffer asked) {
        String code = tidied(asked.code());
        String title = tidied(asked.title());
        String voucherPrefix = tidied(asked.voucherPrefix());
        log.debug("an offer is being written code={} title={} costInPoints={} voucherPrefix={} "
                        + "opensOn={} closesOn={} voucherValidForDays={} minimumStreakWeeks={} "
                        + "requiresBadge={} minimumLifetimePointsEarned={} maxPerCustomer={} "
                        + "maxPerCustomerPerWeek={} stock={} discountedCostInPoints={} "
                        + "discountOpensOn={} discountClosesOn={}",
                code, title, asked.costInPoints(), voucherPrefix, asked.opensOn(),
                asked.closesOn(), asked.voucherValidForDays(), asked.minimumStreakWeeks(),
                asked.requiresBadge(), asked.minimumLifetimePointsEarned(),
                asked.maxPerCustomer(), asked.maxPerCustomerPerWeek(), asked.stock(),
                asked.discountedCostInPoints(), asked.discountOpensOn(),
                asked.discountClosesOn());
        if (code.isEmpty()) {
            throw refusingTheChange(asked.code(), AGAINST_THE_RULES, "An offer needs a code — "
                    + "something like WINTER_HAMPER — and it can never be changed afterwards.");
        }
        checkItReadsLikeAnOffer(new WhatAnOfferWouldSay(code, title, asked.costInPoints(),
                asked.description(), voucherPrefix, asked.opensOn(), asked.closesOn(),
                asked.voucherValidForDays(), asked.minimumStreakWeeks(),
                asked.minimumLifetimePointsEarned(), asked.maxPerCustomer(),
                asked.maxPerCustomerPerWeek(), asked.stock(),
                asked.discountedCostInPoints(), asked.discountOpensOn(),
                asked.discountClosesOn()));
        if (offers.existsByCode(code)) {
            throw refusingTheChange(code, CODE_ALREADY_TAKEN, "There is already an offer called \""
                    + code + "\". Edit that one, or choose another code.");
        }
        RewardOffer writing = RewardOffer.asADraft(code, title, tidiedWords(asked.description()),
                asked.costInPoints(), voucherPrefix, asked.opensOn(), asked.closesOn());
        // Through the same mutator an edit goes through, rather than through a factory parameter,
        // so that what a shelf life does to an offer is written down once. Absent stays absent:
        // an offer nobody gave a number to issues vouchers that never run out.
        if (asked.voucherValidForDays() != null) {
            writing.goodForDays(asked.voucherValidForDays());
        }
        // The three rules arrive the same way and for the same reason: through the mutator an
        // edit goes through, so that what each one does to an offer is written down once. Absent
        // stays absent — an offer nobody restricted is an offer for everybody, which is what all
        // four seeded entries say about themselves.
        if (asked.minimumStreakWeeks() != null) {
            writing.requireAStreakOf(asked.minimumStreakWeeks());
        }
        if (tidiedBadge(asked.requiresBadge()) != null) {
            writing.requireTheBadge(tidiedBadge(asked.requiresBadge()));
        }
        if (asked.minimumLifetimePointsEarned() != null) {
            writing.requireALifetimeOfPointsEarnedOf(asked.minimumLifetimePointsEarned());
        }
        // The caps arrive the same way and for the same reason: through the mutator an edit goes
        // through, so that what a cap does to an offer is written down once, and only when the
        // form actually named one. Absent stays absent, which is an offer anybody may have as
        // often as they like — and is every offer this application seeds.
        if (asked.maxPerCustomer() != null) {
            writing.atMostPerCustomer(asked.maxPerCustomer());
        }
        if (asked.maxPerCustomerPerWeek() != null) {
            writing.atMostPerCustomerPerWeek(asked.maxPerCustomerPerWeek());
        }
        // Through the same mutator a restock goes through, for the same reason, and absent stays
        // absent: an offer nobody gave a number to is one that never runs out, which is what all
        // four seeded offers are. There is nothing to refuse about a new offer's stock beyond it
        // being a number of things — nothing has been claimed from something that did not exist
        // a line ago, so "below what is already out" cannot arise here and is a rule only an
        // edit can break.
        if (asked.stock() != null) {
            writing.stockedWith(asked.stock());
        }
        // The promotion goes on through the same door an edit uses, for the same reason, and all
        // three parts of it in one call: the check above has already said they are three or none,
        // so there is no half-promoted object here even for a statement. Nothing at all is the
        // ordinary case and is what the three nulls of an unpromoted draft mean.
        if (asked.discountedCostInPoints() != null) {
            writing.runAPromotion(asked.discountedCostInPoints(), asked.discountOpensOn(),
                    asked.discountClosesOn());
        }
        // What is in it, if anything is: checked against the catalogue as it stands before the
        // row is written, so that a bundle naming something impossible never exists even as a
        // draft. Empty is an ordinary item and is what every offer this application ships is.
        List<AMemberOfABundle> goingIn = asked.members() == null ? List.of() : asked.members();
        if (!goingIn.isEmpty()) {
            checkItReadsLikeABundle(code, goingIn);
            writing.handedOverAsABundle();
        }
        RewardOffer written = offers.save(writing);
        // The lines after the row and in the same transaction, which is the whole of what keeps
        // the kind and the contents from being able to disagree: a bundle with no lines and a
        // set of lines belonging to no bundle are both states this application cannot commit.
        for (AMemberOfABundle line : goingIn) {
            memberLines.save(new OfferMemberLine(written.code(), tidied(line.code()),
                    line.quantity()));
        }
        log.info("offer created code={} title={} costInPoints={} voucherPrefix={} state={} "
                        + "opensOn={} closesOn={} voucherValidForDays={} minimumStreakWeeks={} "
                        + "requiresBadge={} minimumLifetimePointsEarned={} maxPerCustomer={} "
                        + "maxPerCustomerPerWeek={} stock={} discountedCostInPoints={} "
                        + "discountOpensOn={} discountClosesOn={}",
                written.code(), written.title(), written.costInPoints(), written.voucherPrefix(),
                written.state(), written.opensOn(), written.closesOn(),
                written.voucherValidForDays(), written.minimumStreakWeeks(),
                written.requiresBadge(), written.minimumLifetimePointsEarned(),
                written.maxPerCustomer(), written.maxPerCustomerPerWeek(), written.stock(),
                written.discountedCostInPoints(), written.discountOpensOn(),
                written.discountClosesOn());
        List<WhatABundleContains> contents = whatIsInside(written);
        if (!contents.isEmpty()) {
            // Its own line, because composing a bundle is its own business event: the row above
            // is an offer being written and this is the thing it is made of, and a hamper whose
            // contents were only ever in a request body would be unaccountable the moment
            // somebody asked why a cinema ticket sold out.
            log.info("bundle composed code={} members={}", written.code(), contents);
        }
        return written.asItStands(contents);
    }

    /**
     * Changes whatever the request named about an offer that already exists, and leaves the rest
     * exactly as it was.
     *
     * <p>Absent means "leave it alone", field by field, which is what makes correcting a typo a
     * correction rather than a retyping of the whole form. The argument is written out on
     * {@link AChangeToAnOffer}, along with the reason its code field exists only in order to be
     * refused.
     *
     * <p><strong>A published offer can be edited, on purpose.</strong> A typo that is live is
     * exactly the thing somebody needs to be able to fix without taking the reward off the screen
     * first, and there is nothing to protect by forbidding it: what was already claimed carries
     * its own title and its own price on the claim, so a repricing never rewrites what anybody
     * did. A withdrawn offer is the exception, because a withdrawn offer is a record.
     *
     * @throws RewardRefused if the catalogue has nothing under that code
     * @throws OfferRefused if the offer has been withdrawn, if the change asks for nothing, or if
     *         what it asks for is not something an offer may say
     */
    AnOfferAsItStands change(String code, AChangeToAnOffer asked) {
        RewardOffer offer = theRow(code);
        refuseToTouchAWithdrawnOffer(offer, "edited");
        if (asked.saysNothing()) {
            throw refusingTheChange(offer.code(), AGAINST_THE_RULES,
                    "Say what to change about \"" + offer.code() + "\".");
        }
        if (asked.code() != null && !tidied(asked.code()).equals(offer.code())) {
            throw refusingTheChange(offer.code(), AGAINST_THE_RULES, "An offer's code is fixed "
                    + "once it exists, because the vouchers already issued name it. \""
                    + offer.code() + "\" cannot become \"" + tidied(asked.code()) + "\" — "
                    + "withdraw it and create the one you want instead.");
        }
        String title = asked.title() == null ? offer.title() : tidied(asked.title());
        long costInPoints = asked.costInPoints() == null ? offer.costInPoints()
                : asked.costInPoints();
        String voucherPrefix = asked.voucherPrefix() == null ? offer.voucherPrefix()
                : tidied(asked.voucherPrefix());
        // The window is resolved before anything is written, both ends of it, because whether it
        // runs backwards is a question about the pair: an offer opening on the first and closing
        // on the thirtieth is edited to close on the first of the month before, and only the day
        // that is not being changed says so.
        LocalDate opensOn = asked.theOpeningDayIsBeingChanged() ? asked.opensOn() : offer.opensOn();
        LocalDate closesOn = asked.theClosingDayIsBeingChanged() ? asked.closesOn()
                : offer.closesOn();
        Integer voucherValidForDays = asked.voucherValidForDays() == null
                ? offer.voucherValidForDays() : asked.voucherValidForDays();
        // Absent leaves the rule alone, like the shelf life above and unlike the two days: the
        // reading and the wart that comes with it are argued out on AChangeToAnOffer.
        Integer minimumStreakWeeks = asked.minimumStreakWeeks() == null
                ? offer.minimumStreakWeeks() : asked.minimumStreakWeeks();
        String requiresBadge = tidiedBadge(asked.requiresBadge()) == null
                ? offer.requiresBadge() : tidiedBadge(asked.requiresBadge());
        Long minimumLifetimePointsEarned = asked.minimumLifetimePointsEarned() == null
                ? offer.minimumLifetimePointsEarned() : asked.minimumLifetimePointsEarned();
        Integer maxPerCustomer = asked.maxPerCustomer() == null
                ? offer.maxPerCustomer() : asked.maxPerCustomer();
        Integer maxPerCustomerPerWeek = asked.maxPerCustomerPerWeek() == null
                ? offer.maxPerCustomerPerWeek() : asked.maxPerCustomerPerWeek();
        Integer stock = asked.stock() == null ? offer.stock() : asked.stock();
        // The promotion, resolved the same way and for the same reason: whether the three parts
        // of it make a promotion at all is a question about the set, and a form that moved only
        // the closing day is answered against the price and the opening day the offer already
        // holds.
        Long discountedCostInPoints = asked.discountedCostInPoints() == null
                ? offer.discountedCostInPoints() : asked.discountedCostInPoints();
        LocalDate discountOpensOn = asked.theDiscountsOpeningDayIsBeingChanged()
                ? asked.discountOpensOn() : offer.discountOpensOn();
        LocalDate discountClosesOn = asked.theDiscountsClosingDayIsBeingChanged()
                ? asked.discountClosesOn() : offer.discountClosesOn();
        // Both boxes emptied is a sale called off, and the price comes off with the days. It is
        // the only way to take a promotion off an offer, and it is the reading that matches what
        // somebody emptying both boxes meant: a price applying on no day is not a cheaper offer,
        // it is a figure nobody will ever be charged left in a column. Refusing them instead
        // would leave an administrator with a promotion they could move but never end.
        if (asked.theDiscountsOpeningDayIsBeingChanged() && discountOpensOn == null
                && asked.theDiscountsClosingDayIsBeingChanged() && discountClosesOn == null) {
            if (asked.discountedCostInPoints() != null) {
                throw refusingTheChange(offer.code(), AGAINST_THE_RULES, "This change gives \""
                        + offer.code() + "\" a discounted price of "
                        + asked.discountedCostInPoints() + " points and empties both of the days "
                        + "it would apply on. A promotion is a price and two days: set all three, "
                        + "or empty both days to take the promotion off.");
            }
            discountedCostInPoints = null;
        }
        checkItReadsLikeAnOffer(new WhatAnOfferWouldSay(offer.code(), title, costInPoints,
                asked.description(), voucherPrefix, opensOn, closesOn,
                asked.voucherValidForDays(), asked.minimumStreakWeeks(),
                asked.minimumLifetimePointsEarned(), asked.maxPerCustomer(),
                asked.maxPerCustomerPerWeek(), asked.stock(),
                discountedCostInPoints, discountOpensOn, discountClosesOn));
        // A stock figure below what has already gone out is refused here rather than with the
        // rules above, because it is the one rule about an offer that cannot be decided from the
        // offer: it needs the claims, and the claims are the other table. It is asked only when
        // the stock is actually being moved, so an administrator correcting a typo in the title
        // of an offer that oversold itself in a database somebody else wrote is not stopped by a
        // figure they never touched.
        //
        // What has gone out no longer includes what this same administrator cancelled, which is
        // the one place the cancellation slice reaches into this method. Three claimed and one
        // revoked is two out there, so two is the lowest the stock may be set to — and being
        // told "three have already been claimed" about a claim the scheme itself undid would be
        // the screen refusing a correction on the strength of its own mistake. It is the same
        // subtraction the customer's card is greyed against, asked here for one offer with
        // nobody in front of it.
        //
        // A live hold counts as gone, and that is a decision rather than an oversight. A hold is
        // a promise this scheme has made to a named person for the next seventy-two hours: they
        // will convert it or they will not, and until one of those happens the thing is
        // committed. An administrator allowed to set the stock to exactly what has been claimed
        // would leave a hold with nothing behind it — the customer converts, the claim goes
        // through, and the catalogue is one in the hole with nobody having done anything wrong.
        // The alternative reading, that only vouchers count because only vouchers have left the
        // building, makes the figure on the administration screen true for about a day and then
        // false for whoever is holding one. The rule this refusal exists for is that the figure
        // is never a lie.
        //
        // So the floor is the two of them added: the claims that were not cancelled, plus the
        // holds that are still live. The two come from two tables and two queries, and they are
        // added here rather than in either of them.
        if (!Objects.equals(stock, offer.stock()) && stock != null) {
            Instant now = theMomentItIs();
            long claimed = redemptions.countByReward(offer.code(), VoucherState.CANCELLED);
            long held = holds.howManyAreHeldOf(offer.code(), HoldState.HELD, now);
            long alreadyGone = claimed + held;
            log.debug("a stock figure is being checked against what has gone reward={} was={} "
                            + "asked={} claimed={} held={} alreadyGone={}",
                    offer.code(), offer.stock(), stock, claimed, held, alreadyGone);
            if (stock < alreadyGone) {
                throw refusingTheChange(offer.code(), STOCK_BELOW_WHAT_IS_ALREADY_OUT,
                        claimed + " of \"" + offer.code() + "\" have already been claimed"
                                + (held == 0 ? "" : " and " + held + " more are being held")
                                + ", so its stock cannot be set to " + stock + ". The lowest it "
                                + "can be is " + alreadyGone + ".");
            }
        }

        // Gathered as it is applied rather than worked out afterwards, so that the line in the log
        // says what actually moved rather than what the request mentioned: a title sent back
        // unchanged is not an edit, and a log that called it one would put a change in the record
        // of a night nothing happened.
        List<String> changed = new ArrayList<>();
        if (!title.equals(offer.title())) {
            offer.retitle(title);
            changed.add("title=" + title);
        }
        String words = tidiedWords(asked.description());
        if (words != null && !words.equals(offer.words())) {
            offer.sayInstead(words);
            // The words themselves are not in the line. They are a paragraph, and a log whose
            // lines wrap is a log nobody greps; that one changed is the fact worth keeping, and
            // what it now says is one read of the offer away.
            changed.add("words");
        }
        if (costInPoints != offer.costInPoints()) {
            offer.reprice(costInPoints);
            changed.add("costInPoints=" + costInPoints);
        }
        if (!voucherPrefix.equals(offer.voucherPrefix())) {
            offer.stampVouchersWith(voucherPrefix);
            changed.add("voucherPrefix=" + voucherPrefix);
        }
        // Compared by value rather than by whether the request mentioned them, like every other
        // field here: a form that sends the window back unchanged has not moved a season, and a
        // log that said it had would put a change in the record of an afternoon nothing happened.
        // A day taken away is a change and reads as one, which is why the line can say "null".
        // Compared with Objects.equals rather than with == or equals, because both sides can be
        // null: an offer whose vouchers never run out has nothing here, and a change that left
        // the field out is asking for it to stay that way.
        if (!Objects.equals(opensOn, offer.opensOn())) {
            offer.openOn(opensOn);
            changed.add("opensOn=" + opensOn);
        }
        if (!Objects.equals(closesOn, offer.closesOn())) {
            offer.closeOn(closesOn);
            changed.add("closesOn=" + closesOn);
        }
        if (!Objects.equals(voucherValidForDays, offer.voucherValidForDays())) {
            offer.goodForDays(voucherValidForDays);
            // In the line because a shelf life changing is the sort of edit somebody asks about
            // afterwards, and because the vouchers already issued are unaffected by it — which is
            // only checkable later if the night it changed is in the log.
            changed.add("voucherValidForDays=" + voucherValidForDays);
        }
        // Compared by value like everything else here, and each on its own: an administrator
        // adding a streak requirement to an offer that already asks for a badge has changed one
        // thing, and a log line saying they changed three would put two edits in the record of an
        // afternoon one happened.
        if (!Objects.equals(minimumStreakWeeks, offer.minimumStreakWeeks())) {
            offer.requireAStreakOf(minimumStreakWeeks);
            changed.add("minimumStreakWeeks=" + minimumStreakWeeks);
        }
        if (!Objects.equals(requiresBadge, offer.requiresBadge())) {
            offer.requireTheBadge(requiresBadge);
            changed.add("requiresBadge=" + requiresBadge);
        }
        if (!Objects.equals(minimumLifetimePointsEarned, offer.minimumLifetimePointsEarned())) {
            offer.requireALifetimeOfPointsEarnedOf(minimumLifetimePointsEarned);
            changed.add("minimumLifetimePointsEarned=" + minimumLifetimePointsEarned);
        }
        // A cap changing is the edit most likely to be asked about afterwards, because it is the
        // only one that can lock a card for somebody who could see it this morning without
        // anything about them having changed. A customer complaining about that is answered by
        // the day the number moved and by nothing else, and there is no audit of administrative
        // edits anywhere in this application.
        if (!Objects.equals(maxPerCustomer, offer.maxPerCustomer())) {
            offer.atMostPerCustomer(maxPerCustomer);
            changed.add("maxPerCustomer=" + maxPerCustomer);
        }
        if (!Objects.equals(maxPerCustomerPerWeek, offer.maxPerCustomerPerWeek())) {
            offer.atMostPerCustomerPerWeek(maxPerCustomerPerWeek);
            changed.add("maxPerCustomerPerWeek=" + maxPerCustomerPerWeek);
        }
        // Compared by value like every other field here, so a form that sent the stock back
        // unchanged has not restocked anything and the log does not say it did. The line is
        // worth having above all the others: a restock is the one edit a customer notices
        // immediately, because a sold-out card unlocks itself on their very next read.
        if (!Objects.equals(stock, offer.stock())) {
            offer.stockedWith(stock);
            changed.add("stock=" + stock);
        }
        // Applied as a set, because it is one, and only when some part of it actually moved. A
        // promotion's three columns change together or not at all, so one call says what
        // happened and the log carries all three of the figures that now decide the price —
        // which is the whole of what somebody asking "why was I charged that" needs.
        if (!Objects.equals(discountedCostInPoints, offer.discountedCostInPoints())
                || !Objects.equals(discountOpensOn, offer.discountOpensOn())
                || !Objects.equals(discountClosesOn, offer.discountClosesOn())) {
            offer.runAPromotion(discountedCostInPoints, discountOpensOn, discountClosesOn);
            changed.add("discountedCostInPoints=" + discountedCostInPoints);
            changed.add("discountOpensOn=" + discountOpensOn);
            changed.add("discountClosesOn=" + discountClosesOn);
        }
        offers.save(offer);
        log.info("offer edited code={} state={} changed={}", offer.code(), offer.state(), changed);
        return offer.asItStands(whatIsInside(offer));
    }

    /**
     * Puts an offer on sale, which is the moment a customer can first see it.
     *
     * <p>Pressed on something already published it changes nothing and is not a refusal: whoever
     * pressed it wants the offer on sale and it is, which is the reading pausing an already-paused
     * saving rule is given. Pressed on a withdrawn offer it is refused, because withdrawn is the
     * end of an offer's life and putting one back on sale would make "taken down for good" a
     * thing this application says and does not mean.
     *
     * @throws RewardRefused if the catalogue has nothing under that code
     * @throws OfferRefused if the offer has been withdrawn
     */
    AnOfferAsItStands publish(String code) {
        RewardOffer offer = theRow(code);
        refuseToTouchAWithdrawnOffer(offer, "published");
        OfferState was = offer.state();
        offer.publish();
        offers.save(offer);
        log.info("offer published code={} was={} costInPoints={} kind={}",
                offer.code(), was, offer.costInPoints(),
                offer.isABundle() ? OfferKind.BUNDLE : OfferKind.ITEM);
        return offer.asItStands(whatIsInside(offer));
    }

    /**
     * Takes an offer out of the catalogue for good, and takes nothing else with it.
     *
     * <p>Never a deletion, which is the whole of the decision: every claim already made names this
     * offer by its code, and a row that went away would leave somebody holding a voucher for
     * something the application has no record of. What a claim reads by — its title and what it
     * cost — is written on the claim itself, so a withdrawn offer's vouchers go on saying exactly
     * what they said yesterday.
     *
     * <p>Pressed twice it changes nothing, for the same reason publishing twice does.
     *
     * @throws RewardRefused if the catalogue has nothing under that code
     */
    AnOfferAsItStands withdraw(String code) {
        RewardOffer offer = theRow(code);
        OfferState was = offer.state();
        offer.withdraw();
        offers.save(offer);
        log.info("offer withdrawn code={} was={}", offer.code(), was);
        return offer.asItStands(whatIsInside(offer));
    }

    /**
     * The row behind a code, or the refusal that says the catalogue has never heard of it.
     *
     * <p>{@link RewardRefused} rather than {@link OfferRefused}, and the same kind a customer's
     * claim raises, because it is the same fact: whether the catalogue contains a thing is the
     * catalogue's answer and it does not change depending on who is asking. What changes is the
     * status it is reported with, and that is the web layer's decision — which is why the
     * administration controller looks the code up itself first and answers 404 for an address that
     * names nothing.
     */
    private RewardOffer theRow(String code) {
        return offers.findByCode(code).orElseThrow(() -> {
            log.warn("change to the catalogue rejected code={} kind={} reason={}",
                    code, NO_SUCH_OFFER, RewardsService.noSuchOffer(code));
            return new RewardRefused(NO_SUCH_OFFER, RewardsService.noSuchOffer(code));
        });
    }

    /** Every change to a withdrawn offer, refused in one sentence and for one reason. */
    private void refuseToTouchAWithdrawnOffer(RewardOffer offer, String whatWasAsked) {
        if (offer.isWithdrawn()) {
            throw refusingTheChange(offer.code(), THE_OFFER_IS_WITHDRAWN, "\"" + offer.code()
                    + "\" has been withdrawn, and a withdrawn offer is a record rather than "
                    + "something still being run. It cannot be " + whatWasAsked + ".");
        }
    }

    /**
     * What an offer has to say about itself before this application will keep it, in one place so
     * that creating one and editing one cannot drift into disagreeing about it.
     *
     * <p>A price of at least one point, because the cheapest thing in the catalogue is still
     * something the customer saved for: an offer at nought points is not a reward, it is a button,
     * and it would make the one figure this whole application asks people to work towards
     * meaningless. Negative prices are the same argument with the sign flipped, and would credit
     * points through the door marked spend.
     *
     * <p>A shelf life of at least a day when there is one at all, and nothing at all when there
     * is not. The argument for the floor is on {@link VoucherShelfLife}; what belongs here is that
     * the value checked is the one that <em>arrived</em> rather than the one the offer will end up
     * with, because leaving the field out is not asking for anything and an offer already holding
     * a number is not being asked to justify it again.
     *
     * <p>The words are measured against the column that holds them rather than against anybody's
     * taste. SQLite would take a longer description and Hibernate would hand it over, and the
     * database somebody restores from a backup into a stricter engine is where that stops being
     * true — so the limit the schema declares is the limit somebody is told about, in a sentence,
     * at the moment they typed it.
     *
     * <p>A threshold of at least one on each of the two figures, for the reason the shelf life
     * has a floor: a rule set to nought is not a loose rule, it is a rule that restricts nobody
     * while appearing on the form as a restriction, and the person it misleads is the
     * administrator who set it. The badge an offer may ask for is not here at all, and its
     * absence is the point: there is nothing this module could check a badge code against,
     * because what badges exist is the challenges module's answer and asking for it is the one
     * dependency this whole design refuses to take. An offer asking for a badge nobody can win
     * locks itself for everybody, which is a visible mistake rather than a hidden one — it is on
     * the administration screen, in the words somebody typed, beside the offer nobody claims.
     *
     * <p>A cap of at least one when there is one at all, and nothing at all when there is not —
     * the shelf life's argument again, and the value checked is the one that <em>arrived</em>
     * rather than the one the offer will end up with, for the shelf life's reason: leaving the
     * field out is not asking for anything, and an offer already carrying a cap is not being
     * asked to justify it a second time every time somebody fixes a typo in its title. A cap of
     * nought is refused rather than read as "no cap", because the two are said differently on
     * the form and a screen that quietly turned one into the other would be deciding something
     * it was not told.
     *
     * <p>Nothing here compares the weekly cap with the lifetime one. A weekly cap above a
     * lifetime cap is not a contradiction, it is a weekly cap that never binds — an offer
     * limited to two in all and five a week is perfectly sensible and reads exactly as it is
     * written — and a refusal invented for it would be this application having an opinion about
     * an arrangement that costs nobody anything.
     *
     * <p>A stock figure of at least nought when there is one at all, and nothing at all when
     * there is not. Nought is deliberately legal: "there are none of these at the moment" is a
     * thing somebody can honestly write down and the offer simply reads as sold out until they
     * raise it, which is the same shape a restock has. Below nought is not a scarcer offer, it is
     * a number of things that does not exist, and the arithmetic would report it as sold out —
     * which would be the right answer to the wrong question. Whether the figure is below what has
     * <em>already gone out</em> is a different rule with a different kind, and it is not here,
     * because answering it needs the claims table and this method is a function of the form.
     *
     * <p>A window that runs backwards is refused where it is written, copying {@code Campaign},
     * which refuses a season whose close is before its open for exactly this reason: it is not a
     * short window, it is a window nobody can ever claim in, and a row that can only be a mistake
     * should be answered at the form rather than puzzled over a fortnight later by somebody
     * wondering why nobody claimed the hamper. A single-day window is legal and is not a mistake —
     * "today only" is a thing a scheme says — which is why the comparison is strictly before.
     * Neither day is measured against the clock: an offer whose season is already over is a
     * perfectly ordinary thing to write down, and a season written in advance is the point of
     * having one.
     *
     * <p><strong>A promotion is three things or none of them</strong>, and the three checked here
     * are the ones the offer would end up holding rather than the ones that arrived — unlike the
     * shelf life above, and deliberately so. A change that moves only the promotion's closing day
     * has to be judged against the price and the opening day already on the row, because what
     * this refuses is a <em>row</em> that cannot be read rather than a form that was half filled
     * in. Half of a promotion is refused in a sentence naming which half is missing: a price with
     * no window would strike the ordinary figure through forever and make "usually 250" a
     * sentence about a price nobody was ever charged, and a window with no price is two dates
     * that do nothing and that nobody reading the row a month later could tell from a discount
     * which silently failed to apply.
     *
     * <p><strong>A discounted price at or above the ordinary one is refused</strong>, which is
     * the rule the whole ticket turns on. It is not a badly judged promotion, it is a card
     * striking a figure through to advertise a saving that does not exist, and the customer
     * reading it is being told something untrue by an application that could have known. Equal is
     * refused with above for the same reason: a saving of nothing is still a claim of a saving.
     * The floor of a single point applies to it exactly as it does to the ordinary price, and for
     * the same argument — a reward at nought points is not a reward, it is a button.
     *
     * <p><strong>A promotion's window that runs backwards is refused</strong>, copying the
     * offer's own window above, and a single-day promotion is legal for the reason a single-day
     * season is: "half price today" is a thing a scheme says.
     *
     * <p><strong>The promotion's window is deliberately not required to sit inside the offer's
     * own.</strong> An offer with no window at all can have a promotion — there would be nothing
     * for it to sit inside — so a containment rule would either have to exempt the commonest case
     * or forbid it, and both are worse than the thing they prevent. A promotion positioned partly
     * before an offer opens is not a mistake either: it is a price that applies on the days the
     * offer is claimable and does nothing on the days it is not, which is exactly what it would
     * do if the rule existed and somebody had trimmed it by hand. And an administrator moving a
     * season and its sale is making two edits in some order; a containment rule would refuse
     * whichever of them came first and make the order of two correct changes matter.
     */
    private void checkItReadsLikeAnOffer(WhatAnOfferWouldSay proposed) {
        // Read out once, at the top, and argued with below as the nouns they are. This method
        // used to take these sixteen as sixteen parameters, which is how it arrived: seven
        // slices written in parallel each appended one rule and the one value it needed, and
        // every one of those was a correct small change to a list that was already too long.
        // What it cost was a call nobody could check without counting commas and a compiler
        // that would have said nothing about two dates swapped between the window and the
        // promotion. The record is the fix and the argument for it is on
        // {@link WhatAnOfferWouldSay}; the locals stay because every refusal below quotes its
        // figure inside an English sentence, and a sentence reads better with a noun in it than
        // with an accessor.
        String code = proposed.code();
        String title = proposed.title();
        long costInPoints = proposed.costInPoints();
        String description = proposed.description();
        String voucherPrefix = proposed.voucherPrefix();
        LocalDate opensOn = proposed.opensOn();
        LocalDate closesOn = proposed.closesOn();
        Integer voucherValidForDays = proposed.voucherValidForDays();
        Integer minimumStreakWeeks = proposed.minimumStreakWeeks();
        Long minimumLifetimePointsEarned = proposed.minimumLifetimePointsEarned();
        Integer maxPerCustomer = proposed.maxPerCustomer();
        Integer maxPerCustomerPerWeek = proposed.maxPerCustomerPerWeek();
        Integer stock = proposed.stock();
        Long discountedCostInPoints = proposed.discountedCostInPoints();
        LocalDate discountOpensOn = proposed.discountOpensOn();
        LocalDate discountClosesOn = proposed.discountClosesOn();
        if (title.isEmpty()) {
            throw refusingTheChange(code, AGAINST_THE_RULES,
                    "An offer needs a title — it is what the card is headed with.");
        }
        if (costInPoints < 1) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "An offer has to cost at least one "
                    + "point. " + costInPoints + " is not a price somebody can save towards.");
        }
        if (voucherPrefix.isEmpty()) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "An offer needs a voucher prefix — "
                    + "the few letters in the middle of a code, so that somebody holding one can "
                    + "see what it is for.");
        }
        if (description != null && description.length() > WORDS_A_CARD_CAN_HOLD) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "The words on a card can be at most "
                    + WORDS_A_CARD_CAN_HOLD + " characters, and those are "
                    + description.length() + ".");
        }
        if (opensOn != null && closesOn != null && closesOn.isBefore(opensOn)) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "\"" + code + "\" would close on "
                    + closesOn + ", which is before it opens on " + opensOn + ". A window that "
                    + "runs backwards is not a short season, it is one nobody could ever claim "
                    + "in.");
        }
        if (voucherValidForDays != null
                && voucherValidForDays < VoucherShelfLife.THE_LEAST_A_SHELF_LIFE_CAN_BE) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "A voucher's shelf life is a number "
                    + "of days and the least it can be is "
                    + VoucherShelfLife.THE_LEAST_A_SHELF_LIFE_CAN_BE + ". "
                    + voucherValidForDays + " would issue a voucher nobody could ever use. Leave "
                    + "it blank for vouchers that never run out.");
        }
        // A threshold of at least one, on both the figures, and the same argument as the shelf
        // life's floor: the value checked is the one that arrived rather than the one the offer
        // will end up with, because leaving a box empty is not asking for anything. A streak of
        // nought weeks and a lifetime of nought points are rules every customer already meets —
        // they are not restrictions, they are a restriction somebody meant to type and did not,
        // and an offer carrying one would read in the administration form as though it were
        // gated when it is not. A negative one is the same mistake with the sign flipped.
        if (minimumStreakWeeks != null && minimumStreakWeeks < 1) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "A streak requirement is a number of "
                    + "weeks and the least it can be is 1. " + minimumStreakWeeks + " is a rule "
                    + "every customer already meets. Leave it blank for an offer anybody may "
                    + "claim.");
        }
        if (minimumLifetimePointsEarned != null && minimumLifetimePointsEarned < 1) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "A lifetime-of-points requirement is "
                    + "a number of points and the least it can be is 1. "
                    + minimumLifetimePointsEarned + " is a rule every customer already meets. "
                    + "Leave it blank for an offer anybody may claim.");
        }
        if (maxPerCustomer != null && maxPerCustomer < THE_LEAST_A_CAP_CAN_BE) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "A limit of " + maxPerCustomer
                    + " is not a limit — it is an offer nobody may ever claim. The least a cap "
                    + "can be is " + THE_LEAST_A_CAP_CAN_BE + ". Leave it blank for an offer one "
                    + "customer may have as often as they like.");
        }
        if (maxPerCustomerPerWeek != null && maxPerCustomerPerWeek < THE_LEAST_A_CAP_CAN_BE) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "A limit of " + maxPerCustomerPerWeek
                    + " a week is not a limit — it is an offer nobody may ever claim. The least "
                    + "a cap can be is " + THE_LEAST_A_CAP_CAN_BE + ". Leave it blank for an "
                    + "offer one customer may have as often as they like.");
        }
        if (stock != null && stock < 0) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "A stock figure is how many of "
                    + "something there are, and " + stock + " is not a number of things. Nought "
                    + "is allowed and means there are none left; leave it blank for an offer "
                    + "that never runs out.");
        }
        List<String> theMissingHalfOfThePromotion = new ArrayList<>();
        if (discountedCostInPoints == null) {
            theMissingHalfOfThePromotion.add("a discounted price");
        }
        if (discountOpensOn == null) {
            theMissingHalfOfThePromotion.add("a day it starts on");
        }
        if (discountClosesOn == null) {
            theMissingHalfOfThePromotion.add("a day it ends on");
        }
        boolean somethingAboutAPromotionWasSaid =
                theMissingHalfOfThePromotion.size() < THE_PARTS_OF_A_PROMOTION;
        if (somethingAboutAPromotionWasSaid && !theMissingHalfOfThePromotion.isEmpty()) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "A promotion on \"" + code
                    + "\" is a price and two days, and this one is missing "
                    + String.join(" and ", theMissingHalfOfThePromotion)
                    + ". Give it all three, or leave all three empty and the offer stays at its "
                    + "ordinary price.");
        }
        if (discountedCostInPoints != null && discountedCostInPoints < 1) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "A discounted price has to be at "
                    + "least one point, like every other price in the catalogue. "
                    + discountedCostInPoints + " is not a price somebody can save towards.");
        }
        if (discountedCostInPoints != null && discountedCostInPoints >= costInPoints) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "A discounted price has to be below "
                    + "the ordinary one, and " + discountedCostInPoints + " is not below "
                    + costInPoints + ". A card striking a price through is telling a customer "
                    + "they are saving something.");
        }
        if (discountOpensOn != null && discountClosesOn != null
                && discountClosesOn.isBefore(discountOpensOn)) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "The promotion on \"" + code
                    + "\" would end on " + discountClosesOn + ", which is before it starts on "
                    + discountOpensOn + ". A window that runs backwards is not a short sale, it "
                    + "is one nobody could ever be charged in.");
        }
    }

    /**
     * A badge code as it was typed, trimmed, and null when the box was empty.
     *
     * <p>Blank and absent both come back as nothing here, unlike {@link #tidied}, and that is the
     * difference between a field that is required and one that is a rule: a title of three
     * spaces is a blank title and is refused, whereas a badge box of three spaces is somebody
     * having typed nothing into an optional box, which is the ordinary case for every offer this
     * scheme has. It is nevertheless trimmed rather than taken as sent, because a badge code with
     * a trailing space is a rule that can never be met and would lock the offer for everybody
     * with nothing on the screen to show why.
     */
    /**
     * Everything a bundle has to say about what is in it before this application will keep it.
     *
     * <p><strong>Five refusals, and they are five because a person fixing one of them does five
     * different things next.</strong> Fewer than two members is a form that does not describe a
     * bundle; a quantity below one is a line that does not describe a contents; a code the
     * catalogue has never heard of is somebody who needs to go and find out what the thing is
     * called; a withdrawn member is a thing the scheme no longer hands over at all; and a
     * bundle inside a bundle is a shape this application will not read. The sentences say which,
     * because the sentence is the part that has to be right.
     *
     * <p><strong>All of it checked here, before the row is written, against the catalogue as it
     * stands.</strong> A draft that named something impossible would be a half-written reward
     * that nobody could publish and nobody could correct, since there is no way to edit a
     * bundle's contents: what goes in is decided when it is composed. That is a deliberate
     * narrowness rather than an oversight — see the note on the create path — and it is exactly
     * what makes checking once here enough.
     *
     * <p><strong>Nesting is refused rather than walked.</strong> A bundle of bundles has no
     * natural bottom: what is left of the outermost one becomes a walk of unknown depth done on
     * every read of every card, a cycle becomes possible the moment two of them name each other,
     * and the sentence a customer reads about a sold-out member stops being a sentence. One
     * level deep is a rule that can be checked in a line and proved by reading it.
     *
     * <p>A bundle naming itself is refused as a form that describes something impossible, and
     * before the catalogue is asked whether the code exists — which, at the moment this runs, it
     * does not. "A bundle cannot contain itself" is the true sentence; "there is no such offer"
     * would be technically accurate and completely useless.
     *
     * <p>One code twice is refused too. Two lines naming the same offer would add up correctly
     * and read as a hamper containing popcorn and popcorn, and somebody who meant two of it has
     * a quantity box to say so in. It also keeps "two or more members" meaning two or more
     * <em>things</em>, which is what anybody reading the rule assumes.
     */
    private void checkItReadsLikeABundle(String code, List<AMemberOfABundle> goingIn) {
        log.debug("a bundle is being composed code={} members={}", code, goingIn);
        Set<String> alreadyNamed = new LinkedHashSet<>();
        for (AMemberOfABundle line : goingIn) {
            String memberCode = tidied(line.code());
            if (memberCode.isEmpty()) {
                throw refusingTheChange(code, AGAINST_THE_RULES, "Every line of a bundle needs "
                        + "the code of the offer that goes in it.");
            }
            if (memberCode.equals(code)) {
                throw refusingTheChange(code, AGAINST_THE_RULES, "A bundle cannot contain "
                        + "itself, and \"" + code + "\" names itself.");
            }
            if (!alreadyNamed.add(memberCode)) {
                throw refusingTheChange(code, AGAINST_THE_RULES, "\"" + memberCode + "\" is in "
                        + "this bundle twice. Say how many of it go in on one line instead.");
            }
            if (line.quantity() == null || line.quantity() < THE_LEAST_OF_A_MEMBER) {
                throw refusingTheChange(code, AGAINST_THE_RULES, "A bundle has to contain at "
                        + "least " + THE_LEAST_OF_A_MEMBER + " of \"" + memberCode
                        + "\", and it says " + line.quantity() + ".");
            }
            RewardOffer member = offers.findByCode(memberCode).orElseThrow(() ->
                    refusingTheChange(code, NO_SUCH_MEMBER, "There is no offer called \""
                            + memberCode + "\", so \"" + code + "\" cannot contain one."));
            if (member.isWithdrawn()) {
                throw refusingTheChange(code, THE_OFFER_IS_WITHDRAWN, "\"" + memberCode
                        + "\" has been withdrawn, so it cannot go into \"" + code + "\".");
            }
            if (member.isABundle()) {
                throw refusingTheChange(code, AGAINST_THE_RULES, "\"" + memberCode + "\" is "
                        + "itself a bundle, and a bundle cannot contain another one. Name what "
                        + "is inside it instead.");
            }
        }
        // Counted after the lines have been read one by one, so that a bundle with one good
        // line and one typo is answered about the typo — the mistake somebody made, rather than
        // the one they were about to. The same order the blank title and the taken code are
        // answered in above.
        if (alreadyNamed.size() < THE_LEAST_A_BUNDLE_CAN_CONTAIN) {
            throw refusingTheChange(code, AGAINST_THE_RULES, "A bundle has to contain at least "
                    + THE_LEAST_A_BUNDLE_CAN_CONTAIN + " offers, and \"" + code + "\" names "
                    + alreadyNamed.size() + ".");
        }
    }

    private static String tidiedBadge(String typed) {
        if (typed == null) {
            return null;
        }
        String badge = typed.strip();
        return badge.isEmpty() ? null : badge;
    }

    /**
     * Every refusal of a change to the catalogue says why in the log as well as to whoever asked,
     * because only one of the two is kept — the same bargain a refused claim makes, where the
     * kind is a value and the figures are in the sentence.
     * There is no audit of administrative edits anywhere in this application, so this line is the
     * whole of the record that somebody tried.
     */
    private OfferRefused refusingTheChange(String code, OfferRefused.Kind kind, String reason) {
        log.warn("change to the catalogue rejected code={} kind={} reason={}", code, kind, reason);
        return new OfferRefused(kind, reason);
    }

    /**
     * What somebody typed, without the spaces at either end, and never null.
     *
     * <p>Trimmed rather than taken as sent, because a title of three spaces is a blank title to
     * everybody who reads the card and a code with a trailing space is a code nobody can ever type
     * again. Nothing else is done to it: a code is not uppercased and a title is not capitalised,
     * because what the catalogue says is whoever runs it's decision and an application that
     * quietly rewrote their words would be making a little of it.
     */
    private static String tidied(String typed) {
        return typed == null ? "" : typed.strip();
    }

    /** The words on a card, kept null when there are none rather than turned into an empty line. */
    private static String tidiedWords(String typed) {
        return typed == null ? null : typed.strip();
    }
}
