package io.dataroots.savingstreak.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.rewards.AChangeToAnOffer;
import io.dataroots.savingstreak.rewards.AMemberOfABundle;
import io.dataroots.savingstreak.rewards.ANewOffer;
import io.dataroots.savingstreak.rewards.AnOfferAsItStands;
import io.dataroots.savingstreak.rewards.RewardsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The back office of the catalogue: every offer in every state, the writing of a new one, and the
 * four things that can be done to one that exists.
 *
 * <p><strong>Nothing here checks that the caller runs the scheme.</strong> There is no Spring
 * Security in this application, no role, no session and no password, and the sign-in endpoint's
 * javadoc is honest about exactly that: nothing checks that the browser was ever told about the
 * account it names. The same sentence is true of every handler below, and it is worth saying in
 * the same words rather than in softer ones — <em>this is an administration screen and not
 * authorisation</em>. Anybody who can reach this application can reprice the catalogue. Building
 * the thing that would stop them is a feature of its own, and a comment implying it half-exists
 * would be worse than this paragraph.
 *
 * <p><strong>A controller of its own rather than more handlers on {@code RewardController}.</strong>
 * That one is the catalogue as a customer reads it — published offers, no state, no prefixes, and
 * a shape that a test pins to four entries at four prices. This one is about running the thing.
 * They are addressed differently on purpose, so that the day somebody does put authentication in
 * front of the administration surface, the surface is a path prefix rather than a list of
 * handlers scattered through a class a customer also uses.
 *
 * <p>Beyond reading the request it judges nothing. Whether a title is a title, what the least an
 * offer may cost is, whether a code is free and whether a withdrawn offer may be touched are all
 * rules about the catalogue, and every one of them belongs to Rewards, which refuses on its own. A
 * body that did not arrive at all is a fact about the request and is answered here, in this
 * application's words rather than in Spring's — the shape the categories and the bills already
 * use.
 *
 * <p>An offer nobody has heard of is a 404, and that is this class's decision rather than the
 * module's. The module raises the same refusal for an unknown code whoever asks, because whether
 * the catalogue contains a thing does not depend on who wants to know; but a customer sending a
 * code in the body of a claim is a form with a wrong word in it, while an administrator opening
 * {@code /api/admin/rewards/WINTER_HAMPER} has asked for a page that is not there. So the code in
 * the path is vouched for first, in the words the module owns, exactly as every other controller
 * here vouches for the account in its own path.
 */
@RestController
@RequestMapping("/api/admin/rewards")
class RewardAdministrationController {

    private static final Logger log =
            LoggerFactory.getLogger(RewardAdministrationController.class);

    private final RewardsService rewards;

    /**
     * Who a customer is, for the one address here that lists people rather than offers.
     *
     * <p>Asked of Accounts here rather than carried out of Rewards, exactly as the cancelled
     * voucher's holder is: a name belongs to Accounts, and a waiting list that came out of
     * Rewards with names on it would be Rewards having read another module for the sake of a
     * screen.
     */
    private final AccountsService accounts;

    RewardAdministrationController(RewardsService rewards, AccountsService accounts) {
        this.rewards = rewards;
        this.accounts = accounts;
    }

    /**
     * Every offer the catalogue holds, in every state, in the order they were written — which is
     * the order the customer's catalogue serves the published ones in, so the two pages read the
     * same way round.
     */
    @GetMapping
    List<OfferResponse> everyOffer() {
        return rewards.everyOffer().stream().map(OfferResponse::of).toList();
    }

    /**
     * Writes a new offer, as a draft, and answers with it.
     *
     * <p>Created, with the offer that now exists: the caller needs to be able to name it straight
     * away — the screen publishes it in the next press — and although the code is one they chose,
     * what came back through the rules is what the catalogue actually holds.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OfferResponse createADraft(@RequestBody(required = false) NewOfferRequest request) {
        if (request == null) {
            String reason = "An offer needs a code, a title, a price in points and a voucher "
                    + "prefix.";
            log.warn("offer rejected reason={}", reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        // A price nobody sent is nought points, which the rules refuse in a sentence about the
        // price. Reading it as "they meant to leave it out" would be this layer inventing a rule
        // about what an offer may cost, which is the one thing it is not allowed to know. The
        // shelf life below it is the opposite case and is passed through as the null it arrived
        // as: absent there genuinely means "no shelf life", which is what every offer this
        // application ships says about itself.
        long costInPoints = request.costInPoints() == null ? 0 : request.costInPoints();
        return OfferResponse.of(rewards.createADraft(new ANewOffer(request.code(), request.title(),
                request.description(), costInPoints, request.voucherPrefix(),
                aDay(request.code(), "opensOn", request.opensOn()),
                aDay(request.code(), "closesOn", request.closesOn()),
                request.voucherValidForDays(), request.minimumStreakWeeks(),
                request.requiresBadge(), request.minimumLifetimePointsEarned(),
                request.maxPerCustomer(), request.maxPerCustomerPerWeek(), request.stock(),
                request.discountedCostInPoints(),
                aDay(request.code(), "discountOpensOn", request.discountOpensOn()),
                aDay(request.code(), "discountClosesOn", request.discountClosesOn()),
                whatGoesIn(request.members()))));
    }

    /** One offer as it stands, whatever state it is in. */
    @GetMapping("/{code}")
    OfferResponse theOffer(@PathVariable String code) {
        return OfferResponse.of(vouchFor(code));
    }

    /**
     * Changes whatever the body named and leaves the rest alone, and answers with the offer as it
     * now reads.
     *
     * <p>A PATCH rather than a PUT, for the reason renaming a category is one: what arrives is a
     * change to an offer that is already there rather than an offer being put at that address. The
     * difference is load-bearing here in a way it is not there — an offer has eight fields today
     * and will have twenty by the end of this feature, and a PUT would make every one of them
     * something the screen has to send back correctly in order to fix a typo.
     *
     * <p>The promotion's two days are read the way the window's are, and the flag beside each of
     * them is the same one-line test: a field that arrived at all is an instruction, and whether
     * it was a day or an emptied box is the difference between moving the sale and calling it
     * off. Which of those the module does with them is its business, and the reading that a sale
     * called off takes its price with it is written down over there.
     */
    @PatchMapping("/{code}")
    OfferResponse change(@PathVariable String code,
                         @RequestBody(required = false) ChangeOfferRequest request) {
        vouchFor(code);
        if (request == null) {
            String reason = "Say what to change about \"" + code + "\".";
            log.warn("change to the catalogue rejected code={} reason={}", code, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return OfferResponse.of(rewards.change(code, new AChangeToAnOffer(request.code(),
                request.title(), request.description(), request.costInPoints(),
                request.voucherPrefix(),
                aDay(code, "opensOn", request.opensOn()), request.opensOn() != null,
                aDay(code, "closesOn", request.closesOn()), request.closesOn() != null,
                request.voucherValidForDays(), request.minimumStreakWeeks(),
                request.requiresBadge(), request.minimumLifetimePointsEarned(),
                request.maxPerCustomer(), request.maxPerCustomerPerWeek(), request.stock(),
                request.discountedCostInPoints(),
                aDay(code, "discountOpensOn", request.discountOpensOn()),
                request.discountOpensOn() != null,
                aDay(code, "discountClosesOn", request.discountClosesOn()),
                request.discountClosesOn() != null)));
    }

    /**
     * Puts it on sale. A POST to a path of its own rather than a state field on the PATCH above,
     * because publishing is a decision with consequences a typo correction does not have: it is
     * the moment customers can see the thing and spend points on it, and a press that meant it is
     * worth telling apart from a form that happened to carry a field.
     */
    @PostMapping("/{code}/publish")
    OfferResponse publish(@PathVariable String code) {
        vouchFor(code);
        return OfferResponse.of(rewards.publish(code));
    }

    /**
     * Who is queued for this offer, in the order they joined.
     *
     * <p>The answer to "so that I know what to restock", and it is a list of people rather than
     * a number for a reason: an offer twelve customers are waiting for is a different decision
     * from one that two are, and the names are what turn a restock from a guess into a figure.
     * The length of the list is the figure, and it is the list itself rather than a count
     * because a count cannot say that the person at the front has been waiting since March.
     *
     * <p>The people still waiting and nobody else — not the ones already promoted and not the
     * ones who left. The module argues that where it answers: a queue an administrator is
     * looking at is the line as it stands, and a history mixed into it would make the only
     * number on the screen that matters the wrong number.
     *
     * <p>Empty for an offer nobody is waiting for, which is every offer this application ships,
     * and empty rather than absent for one that has never run out. A queue with nobody in it is
     * a perfectly good answer to the question.
     */
    @GetMapping("/{code}/waiting-list")
    List<WaitingListEntryResponse> theWaitingList(@PathVariable String code) {
        vouchFor(code);
        return rewards.theQueueFor(code).stream()
                .map(place -> WaitingListEntryResponse.of(place, whoIs(place.customerId())))
                .toList();
    }

    /**
     * Puts a name beside an identifier, and null when nobody on file answers to it.
     *
     * <p>Null rather than a refusal, for the reason the cancelled voucher's holder is null:
     * somebody is in that queue whether or not this application can still say who, and a read
     * that fell over because one row could not be named would take the whole list away from
     * the person trying to act on it.
     */
    private String whoIs(long customerId) {
        return accounts.customerWith(customerId).map(customer -> customer.getName()).orElse(null);
    }

    /** Takes it out of the catalogue for good, and leaves every voucher already issued alone. */
    @PostMapping("/{code}/withdraw")
    OfferResponse withdraw(@PathVariable String code) {
        vouchFor(code);
        return OfferResponse.of(rewards.withdraw(code));
    }

    /**
     * Asked before anything else on every path that names an offer, so that an address naming
     * nothing is a 404 rather than a rule about the catalogue reported as a bad form. Logged as
     * well as answered, because a refusal decided here would otherwise leave no line in the
     * application's log at all.
     */
    /**
     * A day somebody typed, read as a day — or a refusal naming what could not be read as one.
     *
     * <p>Read here rather than by the module, and that is the line this whole layer draws. Whether
     * "31/12/2026" is a date is a fact about the form, in the same family as a body that never
     * arrived and a price that is not a number; whether the two days make a window an offer can be
     * claimed in is a rule about the catalogue and belongs to Rewards, which refuses it in its own
     * sentence. The wording is the one a goal's deadline already gets, word for word, because a
     * date box is a date box wherever it is on the screen and two applications' worth of phrasing
     * for one mistake is how a product starts sounding like two.
     *
     * <p>Null and blank both come back as no day at all. They mean different things to the caller
     * — one is a field nobody sent and the other is a box somebody emptied — and telling them
     * apart is the caller's job, done by looking at the text rather than at what this returns.
     */
    private LocalDate aDay(String code, String which, String typed) {
        if (typed == null || typed.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(typed.trim());
        } catch (DateTimeParseException notADay) {
            String reason = "\"" + typed + "\" is not a day. Write it as a date, like 2026-12-31.";
            log.warn("offer rejected code={} field={} value={} reason={}",
                    code, which, typed, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * The bundle's lines, read into the module's own record and judged by nobody here.
     *
     * <p>A missing list and an empty one are the same request — an ordinary item, which is what
     * every offer this application ships is — so both become an empty list rather than a null
     * the module would have to have an opinion about. Everything else about them, including
     * whether two is enough of them, is a rule about the catalogue and comes back as a sentence.
     *
     * <p>Nothing is trimmed, upper-cased or defaulted on the way through. A quantity nobody
     * filled in stays absent and is refused by name, because "how many" is the one thing about a
     * hamper this application must not guess at.
     */
    private static List<AMemberOfABundle> whatGoesIn(List<BundleMemberRequest> members) {
        if (members == null) {
            return List.of();
        }
        return members.stream()
                .map(member -> new AMemberOfABundle(member.code(), member.quantity()))
                .toList();
    }

    private AnOfferAsItStands vouchFor(String code) {
        return rewards.theOffer(code).orElseThrow(() -> {
            String reason = RewardsService.noSuchOffer(code);
            log.warn("offer read rejected code={} reason={}", code, reason);
            return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
        });
    }
}
