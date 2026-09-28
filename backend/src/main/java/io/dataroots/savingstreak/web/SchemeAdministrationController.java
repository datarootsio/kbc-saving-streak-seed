package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import io.dataroots.savingstreak.scheme.ANewVersionOfTheScheme;
import io.dataroots.savingstreak.scheme.SchemeService;
import io.dataroots.savingstreak.schemepreview.SchemePreviewService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The back office of the scheme: publishing the next version of what the bank pays for saving.
 *
 * <p><strong>Two doors carrying one body, and nothing that edits or deletes.</strong> One of
 * the two writes and the other is a question: a candidate posted to {@code /preview} is read
 * and refused exactly as a publish would read and refuse it, answered with what publishing it
 * would do, and thrown away. There is no draft record between them, no lifecycle and no
 * activation step — the date on a published row is the activation — so the only state this
 * back office has is the versions themselves. Previewing before publishing is a rule of the
 * screen and deliberately not of this controller: a backend refusing to publish what it had
 * not been asked about first would need to remember what it had been asked about, which is the
 * draft table the module spent a ticket not building.
 *
 * <p><strong>The two verbs that are missing are the point of the ticket.</strong> Nothing
 * here edits a published version and nothing here deletes one, and neither of those is a rule
 * enforced somewhere below — there is simply no handler, no path and no method, so a request to do
 * either arrives at nothing. That is the only way a statement like "a scheme somebody's week was
 * judged under cannot be rewritten behind them" can be made at this layer: not as a check that
 * could be loosened, but as a door that was never built. {@code SchemeService} says the same thing
 * about the module, {@code SchemeVersionRepository} about the rows, and {@code SchemeVersion} —
 * which has no mutator at all — about the row itself. A version announced for next Monday is taken
 * back by publishing another one for that same Monday, which wins by having the higher number; that
 * is the whole of the correction surface, and it is deliberately the same door as the first
 * publish.
 *
 * <p><strong>Nothing here checks that the caller runs the bank.</strong> There is no Spring
 * Security in this application, no role, no session and no password, and both
 * {@link SavingsProductAdministrationController} and {@link RewardAdministrationController} are
 * honest about exactly that in the same words. Anybody who can reach this application can reprice
 * the scheme for every customer the bank has. Building the thing that would stop them is a feature
 * of its own, and a comment implying it half-exists would be worse than this paragraph.
 *
 * <p><strong>A controller of its own under {@code /api/admin}, beside the other two back
 * offices.</strong> The same argument they make: the day somebody does put authentication in front
 * of the administration surface, the surface should be a path prefix rather than a list of handlers
 * scattered through a class customers also use. It is also what keeps the customer's
 * {@link SchemeController} what it is — two GETs and no verb at all.
 *
 * <p><strong>There is deliberately no GET here.</strong> Nothing about the scheme is hidden from a
 * customer: the version in force and the whole published history, including any version announced
 * for a Monday still to come, are already served to anybody who asks. A second read returning an
 * identical answer would be a second shape to keep in agreement with the first for no gain at all,
 * so the administration screen is drawn from the customer's own reads. That is the reason the
 * savings products back office gives, and the scheme is more public than a product's terms rather
 * than less.
 *
 * <p>Beyond reading the form it judges nothing. Whether a rate may be quoted to five places,
 * whether nought is a thing the ordinary rate may be, whether the rungs climb, whether the ladder
 * descends and whether a version may take effect on the day named are all rules about the scheme
 * and belong to the Scheme module, which refuses each in its own sentence. What is decided here is
 * characters: whether "0,50" is a number and whether "31/12/2026" is a day, which is a fact about
 * the form in the same family as a body that never arrived — answered in this application's words
 * rather than in Spring's, the shape the offers, the bills and the savings products already use.
 */
@RestController
@RequestMapping("/api/admin/scheme")
class SchemeAdministrationController {

    private static final Logger log =
            LoggerFactory.getLogger(SchemeAdministrationController.class);

    private final SchemeService scheme;
    private final SchemePreviewService preview;

    SchemeAdministrationController(SchemeService scheme, SchemePreviewService preview) {
        this.scheme = scheme;
        this.preview = preview;
    }

    /**
     * Publishes the next version of the scheme and answers with it.
     *
     * <p>Created, with the version that now exists and whether its day has come, because the number
     * it was given is the module's answer rather than the form's: whoever published it has to be
     * able to see which version they just wrote, and whether it is deciding anything yet.
     *
     * <p>A POST to the collection of versions rather than a PUT at a version's address, and the
     * difference is the ticket. A PUT would be somebody putting a version at a number they chose,
     * which is one keystroke away from putting one at a number that is already taken; a POST to the
     * collection is asking the module to write the next one, which is the only thing this
     * application will do.
     */
    @PostMapping("/versions")
    @ResponseStatus(HttpStatus.CREATED)
    SchemeVersionPublishedResponse publish(
            @RequestBody(required = false) NewVersionOfTheSchemeRequest request) {
        if (request == null) {
            String reason = "A new version of the scheme needs the Monday it takes effect, every "
                    + "figure the scheme carries, and one line saying what changed.";
            log.warn("a new version of the scheme was rejected reason={}", reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return SchemeVersionPublishedResponse.of(
                scheme.publishTheNextVersion(theCandidateOnTheForm(request)));
    }

    /**
     * Answers what publishing this candidate would do, and writes nothing whatsoever.
     *
     * <p><strong>A POST that changes nothing, which is the one thing worth arguing about
     * here.</strong> It is a POST because the candidate is a form of thirteen figures and a ladder,
     * which is a body and not a query string, and because it is the same body the publishing door
     * takes — the screen holds one form and points it at two addresses. Nothing about the verb
     * implies a write: {@code SchemePreviewService} runs in a read-only transaction, raises no
     * notification, expires no batch and adds no row, and the suite proves it by reading the whole
     * application back afterwards.
     *
     * <p><strong>OK rather than Created, because nothing was created.</strong> A preview has no
     * address afterwards, nothing can be fetched back, and a 201 would be this layer's own claim
     * that something now exists. That is also why there is no {@code Location} header: there is
     * nowhere to point one at.
     *
     * <p><strong>A separate door rather than a flag on the publishing one.</strong> Two independent
     * addresses, no draft record, no lifecycle and no activation step: preview computes and
     * returns, publish writes. A {@code ?dryRun=true} would put "did this write" one typo away from
     * being wrong, on the one request in this application whose whole promise is that it did not.
     *
     * <p><strong>A candidate that would be refused on publish is refused here in the same
     * words.</strong> Not by a check repeated in this class — the module reads the candidate with
     * the identical method the publishing path uses. Preview and publish cannot disagree about what
     * is sayable, which is the property that makes it worth previewing at all.
     */
    @PostMapping("/preview")
    SchemePreviewResponse preview(@RequestBody(required = false)
                                  NewVersionOfTheSchemeRequest request) {
        if (request == null) {
            String reason = "A candidate version of the scheme needs the Monday it would take "
                    + "effect, every figure the scheme carries, and one line saying what changed.";
            log.warn("a preview of the scheme was rejected reason={}", reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return SchemePreviewResponse.of(
                preview.whatThisSchemeWouldDo(theCandidateOnTheForm(request)));
    }

    /**
     * The form as the thirteen things the module takes, read out of characters and judged no
     * further.
     *
     * <p>One reading for both doors, because the candidate a preview is taken of has to be exactly
     * the candidate a publish would write — down to how an empty box is read. Two copies of this
     * list would be two answers to "was the third rung blank", and the whole point of the preview
     * is that what it looked at is what gets published.
     */
    private ANewVersionOfTheScheme theCandidateOnTheForm(NewVersionOfTheSchemeRequest request) {
        return new ANewVersionOfTheScheme(
                aDay("effectiveFrom", request.effectiveFrom()),
                aFigure("weeklyThreshold", request.weeklyThreshold()),
                aFigure("theOrdinaryRate", request.theOrdinaryRate()),
                aFigure("extraForEachFurtherWeek", request.extraForEachFurtherWeek()),
                aFigure("theMostAStreakPays", request.theMostAStreakPays()),
                aCount("howLongABatchOfPointsLasts", request.howLongABatchOfPointsLasts()),
                theRungs(request.balanceRungs()),
                aFigure("whatShareOfABudgetIsRunningLow",
                        request.whatShareOfABudgetIsRunningLow()),
                aCount("howManyOutstandingIsASpiral", request.howManyOutstandingIsASpiral()),
                aCount("daysBeforeAMaturityIsWorthSaying",
                        request.daysBeforeAMaturityIsWorthSaying()),
                aCount("daysBeforeAnAnniversaryIsWorthSaying",
                        request.daysBeforeAnAnniversaryIsWorthSaying()),
                request.whatChanged());
    }

    /**
     * A day somebody typed, read as a day — or a refusal naming what could not be read as one.
     *
     * <p>Read here rather than by the module, and that is the line this whole layer draws. Whether
     * "31/12/2026" is a date is a fact about the form; whether a version of the scheme may take
     * effect on the Monday it turns out to be is a rule about the scheme and belongs below. The
     * wording is the one a goal's deadline, an offer's window and a product's effective date
     * already get, word for word, because a date box is a date box wherever it is on the screen.
     *
     * <p>An empty box comes back as no day at all and is refused by the module by name, rather than
     * being read here as next Monday. Which Monday a repricing starts on is the most consequential
     * thing on this form, and a layer that filled it in would be publishing a scheme on a date
     * nobody chose.
     */
    private LocalDate aDay(String which, String typed) {
        if (typed == null || typed.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(typed.trim());
        } catch (DateTimeParseException notADay) {
            String reason = "\"" + typed + "\" is not a day. Write it as a date, like 2026-12-31.";
            log.warn("a new version of the scheme was rejected field={} value={} reason={}",
                    which, typed, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * A rate, a share or an amount as the figure it spells, or a refusal quoting back what was
     * typed.
     *
     * <p>Named back to whoever sent it, for the reason a deposit's amount is: a person who typed a
     * comma has to see the comma to see the mistake. How finely the figure may be quoted and
     * whether nought is a thing it may be are emphatically not asked here — those are the module's
     * rules and its sentences.
     *
     * <p>An empty box is no figure at all, passed through as such. It is not read as nought,
     * because not one figure on this form has an absence to say: an empty weekly threshold read as
     * nought would publish a scheme in which every week secures itself.
     */
    private BigDecimal aFigure(String which, String typed) {
        if (typed == null || typed.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(typed.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + typed + "\" is not a number. Write it in digits with a full "
                    + "stop, like 0.50.";
            log.warn("a new version of the scheme was rejected field={} value={} reason={}",
                    which, typed, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * A count of months, bills or days as the whole number it spells, or a refusal quoting back
     * what was typed. An empty box is no count at all and is refused by the module by name, for the
     * reason {@link #aFigure} gives.
     */
    private Integer aCount(String which, String typed) {
        if (typed == null || typed.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(typed.trim());
        } catch (NumberFormatException notAWholeNumber) {
            String reason = "\"" + typed + "\" is not a whole number. Write it in digits with no "
                    + "decimal point, like 12.";
            log.warn("a new version of the scheme was rejected field={} value={} reason={}",
                    which, typed, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * The balance rungs as the figures they spell, in the order they were sent.
     *
     * <p>A list with no list at all left as none, so that "a scheme has to have at least one rung"
     * is the module's sentence rather than this layer's guess at an empty ladder. An empty box
     * <em>inside</em> the list is kept as a gap rather than dropped, for the same reason: a ladder
     * quietly one rung shorter than the one on the screen is worse than a refusal naming the blank.
     *
     * <p>Each rung is read with the same sentence every other figure gets, and its place in the
     * ladder is named in the log, because "the third one" is what somebody looking at six boxes
     * needs to hear.
     */
    private List<BigDecimal> theRungs(List<String> typed) {
        if (typed == null) {
            return null;
        }
        List<BigDecimal> rungs = new ArrayList<>(typed.size());
        for (int rung = 0; rung < typed.size(); rung++) {
            rungs.add(aFigure("balanceRungs[" + rung + "]", typed.get(rung)));
        }
        return rungs;
    }
}
