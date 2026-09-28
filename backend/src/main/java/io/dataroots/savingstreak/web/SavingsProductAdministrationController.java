package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import io.dataroots.savingstreak.products.ANewVersionOfTheTerms;
import io.dataroots.savingstreak.products.ProductsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The back office of the savings catalogue: publishing the next version of a product's terms, and
 * closing a product to new accounts or putting it back on sale.
 *
 * <p><strong>Three verbs, and the two that are missing are the point of the ticket.</strong>
 * Nothing here edits a published version and nothing here deletes anything, and neither of those is
 * a rule enforced somewhere below — there is simply no handler, no path and no method, so a request
 * to do either arrives at nothing. That is the only way a statement like "an agreement somebody is
 * living under cannot be rewritten" can be made at this layer: not as a check that could be
 * loosened, but as a door that was never built. {@code ProductsService} says the same thing about
 * the module and {@code ProductTerms} says it about the row.
 *
 * <p><strong>Nothing here checks that the caller runs the bank.</strong> There is no Spring
 * Security in this application, no role, no session and no password, and
 * {@link RewardAdministrationController} is honest about exactly that in the same words. Anybody
 * who can reach this application can reprice a savings product. Building the thing that would stop
 * them is a feature of its own, and a comment implying it half-exists would be worse than this
 * paragraph.
 *
 * <p><strong>A controller of its own under {@code /api/admin}, beside the rewards back
 * office.</strong> The same argument that one makes: the day somebody does put authentication in
 * front of the administration surface, the surface should be a path prefix rather than a list of
 * handlers scattered through a class customers also use. It is also what keeps the customer's
 * {@link SavingsProductController} what it is — three GETs and no verb at all.
 *
 * <p><strong>There is deliberately no GET here.</strong> The rewards back office needed one because
 * an administrator sees offers a customer cannot — drafts, withdrawn ones, the state each is in.
 * Nothing about a savings product is hidden from a customer: the catalogue already serves closed
 * products marked closed, and the history already serves every version a product has published
 * including one dated for next month. A second read returning an identical answer would be a second
 * shape to keep in agreement with the first for no gain at all, so the administration screen is
 * drawn from the customer's own reads.
 *
 * <p>Beyond reading the form it judges nothing. Whether a rate may be quoted to three places,
 * whether nought is a thing the points multiplier may be, whether the ending is one this bank
 * offers and whether a version may take effect on the day named are all rules about agreements and
 * belong to Products, which refuses each in its own sentence. What is decided here is characters:
 * whether "0,50" is a number and whether "31/12/2026" is a day, which is a fact about the form in
 * the same family as a body that never arrived — answered in this application's words rather than
 * in Spring's, the shape the offers and the bills already use.
 */
@RestController
@RequestMapping("/api/admin/savings-products")
class SavingsProductAdministrationController {

    private static final Logger log =
            LoggerFactory.getLogger(SavingsProductAdministrationController.class);

    private final ProductsService products;

    SavingsProductAdministrationController(ProductsService products) {
        this.products = products;
    }

    /**
     * Publishes the next version of that product's terms and answers with it.
     *
     * <p>Created, with the version that now exists, because the number it was given is the
     * catalogue's answer rather than the form's: whoever published it has to be able to see which
     * version they just wrote, and whether it is the one on offer today or one dated for a morning
     * still to come.
     *
     * <p>A POST to the product's versions rather than a PUT at a version's address, and the
     * difference is the ticket. A PUT would be somebody putting a version at a number they chose,
     * which is one keystroke away from putting one at a number that is already taken; a POST to the
     * collection is asking the catalogue to write the next one, which is the only thing this
     * application will do.
     */
    @PostMapping("/{code}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    TermsVersionResponse publish(@PathVariable String code,
                                 @RequestBody(required = false) NewVersionOfTermsRequest request) {
        if (request == null) {
            String reason = "A new version needs the day it takes effect, every figure the "
                    + "agreement carries, what happens at the end of it, and one line saying what "
                    + "changed.";
            log.warn("a new version of a savings product's terms was rejected product={} reason={}",
                    code, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return TermsVersionResponse.of(products.publishANewVersionOf(code, new ANewVersionOfTheTerms(
                aDay(code, "effectiveFrom", request.effectiveFrom()),
                aFigure(code, "annualRatePercent", request.annualRatePercent()),
                aFigure(code, "bonusRatePercent", request.bonusRatePercent()),
                aCount(code, "noticeDays", request.noticeDays()),
                aCount(code, "termMonths", request.termMonths()),
                aFigure(code, "minimumBalance", request.minimumBalance()),
                aCount(code, "earlyExitPenaltyDays", request.earlyExitPenaltyDays()),
                aFigure(code, "pointsMultiplier", request.pointsMultiplier()),
                aFigure(code, "anniversaryRatePercent", request.anniversaryRatePercent()),
                request.maturityAction(),
                request.whatChanged())));
    }

    /**
     * Stops anybody opening a new account on that product, and answers with it as it now reads.
     *
     * <p>A POST to a path of its own rather than a field on a change, for the reason publishing an
     * offer is one: retiring a product is a decision with consequences a typo correction does not
     * have, and a press that meant it is worth telling apart from a form that happened to carry a
     * field. There is no form here at all, which is the other half of it — closing a product is one
     * press and there is nothing to fill in.
     *
     * <p>Nothing that is already on the product moves. The accounts carry on, every published
     * version reads exactly as it did, and the card stays in the catalogue marked closed, because a
     * customer holding one has every right to see what they are holding.
     */
    @PostMapping("/{code}/close")
    SavingsProductResponse close(@PathVariable String code) {
        return SavingsProductResponse.of(products.closeToNewAccounts(code));
    }

    /** Puts it back on sale to new accounts, and publishes nothing. */
    @PostMapping("/{code}/reopen")
    SavingsProductResponse reopen(@PathVariable String code) {
        return SavingsProductResponse.of(products.reopenToNewAccounts(code));
    }

    /**
     * A day somebody typed, read as a day — or a refusal naming what could not be read as one.
     *
     * <p>Read here rather than by the module, and that is the line this whole layer draws. Whether
     * "31/12/2026" is a date is a fact about the form; whether a version may take effect on the day
     * it turns out to be is a rule about the catalogue and belongs to Products. The wording is the
     * one a goal's deadline and an offer's window already get, word for word, because a date box is
     * a date box wherever it is on the screen.
     *
     * <p>An empty box comes back as no day at all and is refused by the module by name, rather than
     * being read here as today. Which day a version takes effect on is the whole of what "from
     * when" means, and a layer that filled it in would be publishing an agreement on a date nobody
     * chose.
     */
    private LocalDate aDay(String code, String which, String typed) {
        if (typed == null || typed.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(typed.trim());
        } catch (DateTimeParseException notADay) {
            String reason = "\"" + typed + "\" is not a day. Write it as a date, like 2026-12-31.";
            log.warn("a new version of a savings product's terms was rejected product={} field={} "
                    + "value={} reason={}", code, which, typed, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * A rate or an amount as the figure it spells, or a refusal quoting back what was typed.
     *
     * <p>Named back to whoever sent it, for the reason a deposit's amount is: a person who typed a
     * comma has to see the comma to see the mistake. How finely the figure may be quoted and
     * whether nought is a thing it may be are emphatically not asked here — those are the module's
     * rules and its sentences.
     *
     * <p>An empty box is no figure at all, passed through as such. It is not read as nought,
     * because nought is a real and different agreement in six of the eight figures on this form —
     * no bonus, no floor, no interest at all — and a layer that turned "they said nothing" into a
     * rate of nought would publish a product paying nothing to somebody who forgot a field.
     */
    private BigDecimal aFigure(String code, String which, String typed) {
        if (typed == null || typed.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(typed.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + typed + "\" is not a number. Write it in digits with a full "
                    + "stop, like 0.50.";
            log.warn("a new version of a savings product's terms was rejected product={} field={} "
                    + "value={} reason={}", code, which, typed, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * A count of days or months as the whole number it spells, or a refusal quoting back what was
     * typed. An empty box is no count at all and is refused by the module by name, for the reason
     * {@link #aFigure} gives.
     */
    private Integer aCount(String code, String which, String typed) {
        if (typed == null || typed.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(typed.trim());
        } catch (NumberFormatException notAWholeNumber) {
            String reason = "\"" + typed + "\" is not a whole number. Write it in digits with no "
                    + "decimal point, like 32.";
            log.warn("a new version of a savings product's terms was rejected product={} field={} "
                    + "value={} reason={}", code, which, typed, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }
}
