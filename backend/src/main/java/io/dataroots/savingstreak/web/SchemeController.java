package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.scheme.SchemeService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the scheme says today, and every version of it the bank has ever published.
 *
 * <p>It belongs to no account and no customer, in exactly the way the products catalogue, the
 * rewards catalogue and the seasons listing do: one ladder for everybody, which is what makes the
 * rate something a customer can read before they are holding anything. A customer's own run of
 * weeks and their own multiplier are a different read on a different endpoint, and they are
 * deliberately not here.
 *
 * <p><strong>Readable by anybody, and more public than a product's terms rather than less.</strong>
 * A product's history is already served to customers; the scheme is what the overview's
 * "1,30× per euro" is explained <em>by</em>, so hiding it behind the administration prefix would be
 * hiding the answer to the one question a customer whose rate fell actually has.
 *
 * <p><strong>Read-only, and there is deliberately nothing else.</strong> Not a POST, not a PUT, not
 * a DELETE — not as a check that could be loosened, but as methods that were never written. The
 * door that publishes version <em>n+1</em> and the door that previews one are later tickets and
 * will live behind the administration prefix; nothing anywhere, then or now, edits or deletes a
 * published version, because a scheme somebody's week was judged under cannot be rewritten behind
 * them.
 *
 * <p><strong>It decides nothing.</strong> Which version is in force today, what the figures read as
 * in euros and multiples, and what order a history goes in are all the Scheme module's answers.
 * This class turns one request into one call and its answer into JSON.
 */
@RestController
@RequestMapping("/api/scheme")
class SchemeController {

    private final SchemeService scheme;

    SchemeController(SchemeService scheme) {
        this.scheme = scheme;
    }

    /**
     * The version in force today: every figure, its Monday, its number and its line saying what
     * changed.
     *
     * <p>In force rather than newest published, which is the difference a version announced for
     * next Monday makes. That version is in the history below and is deciding nothing yet.
     */
    @GetMapping
    SchemeResponse theSchemeInForce() {
        return SchemeResponse.of(scheme.theSchemeInForce());
    }

    /**
     * Every version ever published, newest first, including any announced for a Monday still to
     * come.
     *
     * <p>Its own path rather than another field on the reading above, because a history is a
     * different question asked at a different moment: a page quoting what a week asks for wants one
     * version, and sending the whole history to draw that sentence would be a history nobody asked
     * for on every screen that mentions a figure.
     */
    @GetMapping("/versions")
    List<SchemeResponse> everyVersionPublished() {
        return scheme.everyVersionPublished().stream().map(SchemeResponse::of).toList();
    }
}
