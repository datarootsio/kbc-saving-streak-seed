package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.products.ProductsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * What the bank sells, what each product is offering today, and every version each of them has
 * published.
 *
 * <p>It belongs to no account and no customer, in exactly the way the rewards catalogue and the
 * seasons listing do: the same four products at the same four rates for everybody, which is what
 * makes a rate something a customer can weigh before they are holding anything. A customer's own
 * agreement — which product their account is on and which version it was opened under — is a
 * different read on a different endpoint, and it is deliberately not here.
 *
 * <p>Read-only, and there is deliberately nothing else. Products and their versions are seeded
 * rows; the administration door that publishes a version and closes a product to new accounts is a
 * later ticket, so there is no verb to offer but GET.
 *
 * <p><strong>It decides nothing.</strong> Which version is on offer today, whether the bank sells a
 * product at all, and what a closed product reads as are all the Products module's answers. This
 * class turns one request into one call and its answer into JSON, and the one thing it knows that
 * the module does not is which status reports a refusal — which is answered next door in
 * {@link RefusalsAsHttp}.
 */
@RestController
@RequestMapping("/api/savings-products")
class SavingsProductController {

    private final ProductsService products;

    SavingsProductController(ProductsService products) {
        this.products = products;
    }

    /**
     * The whole shelf, in the order somebody chose, each with the terms it is offering today.
     *
     * <p>Closed products included, marked as closed. A page comparing what each product pays is
     * drawn from this and from nothing else.
     */
    @GetMapping
    List<SavingsProductResponse> catalogue() {
        return products.catalogue().stream().map(SavingsProductResponse::of).toList();
    }

    /**
     * What a named amount would be worth after twelve months in each product somebody may still
     * open an account on, in the catalogue's own order.
     *
     * <p><strong>A literal path beside {@code /{code}}, and it is not ambiguous.</strong> A request
     * for {@code /projections} matches the exact segment rather than the template, because a
     * literal pattern is more specific than a variable one and Spring's own ordering says so. It is
     * worth naming out loud all the same: the day somebody publishes a product whose code is
     * "projections", this endpoint is what they would reach, which is one more reason product codes
     * are the bank's and not a customer's.
     *
     * <p><strong>A GET with the amount in the query, because it reads and decides nothing.</strong>
     * Nothing is written, no account is touched and the same request twice gives the same answer;
     * the figure is the question rather than something submitted. It also means the whole
     * comparison — four products, their rates, their conditions, their loyalty benefits and the
     * projection — is one round trip that a page can repeat as somebody types.
     *
     * <p><strong>The amount arrives as text and is judged by the domain.</strong> What the
     * characters are is this class's question — "25,00" deserves a sentence about the amount rather
     * than a request that could not be read — and whether the number is an amount of money at all
     * is {@code AmountOfMoney}'s rule, asked by the Products module and refused in its words. A
     * missing parameter is not required here because absence is one of the things that rule has a
     * sentence for.
     */
    @GetMapping("/projections")
    List<WhatAYearInAProductWouldPayResponse> projections(
            @RequestParam(required = false) String amount) {
        return products.whatAYearWouldPayOn(amountIn(amount)).stream()
                .map(WhatAYearInAProductWouldPayResponse::of)
                .toList();
    }

    /**
     * The figure as typed, read as a number, or nothing at all when nothing was sent.
     *
     * <p>Nothing rather than a refusal of this class's own, so that "you sent no amount" is
     * answered by the one rule that owns what an amount of money is, in the same voice as every
     * other objection to one. What is refused here is only what cannot be read as a number at all,
     * which is a question about the characters and therefore this layer's — and the sentence quotes
     * them back, because somebody who typed a comma has to see the comma to see the mistake.
     */
    private static BigDecimal amountIn(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException notANumber) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\"" + amount
                    + "\" is not an amount of money. Write it in digits with a full stop, like 25.00.");
        }
    }

    /**
     * One product by its code, with the terms it is offering today.
     *
     * <p>A product the bank does not sell is refused by the module in its own words and reported as
     * a 404 next door. A product closed to new accounts is not refused: it exists, somebody is
     * holding it, and it reads perfectly well.
     */
    @GetMapping("/{code}")
    SavingsProductResponse product(@PathVariable String code) {
        return SavingsProductResponse.of(products.product(code));
    }

    /**
     * Every version that product has published, oldest first, each with the line saying what
     * changed.
     *
     * <p>Its own path rather than another field on the product above, because a history is a
     * different question asked at a different moment: the shelf is drawn from four cards and the
     * history from one card somebody has clicked into, and sending every version of every product
     * to draw the shelf would be four histories nobody asked for.
     *
     * <p>Each version carries what it changed about the one before it, in sentences the module
     * worded. They are the same sentences an account's own page shows about the step from the
     * version it is on to the version on offer, because one function in the backend words a
     * difference and both readings render from it.
     */
    @GetMapping("/{code}/versions")
    List<TermsVersionResponse> versions(@PathVariable String code) {
        return products.everyVersionOf(code).stream().map(TermsVersionResponse::of).toList();
    }
}
