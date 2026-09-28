package io.dataroots.savingstreak.products;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Which of a product's published versions is the one on offer on a given day.
 *
 * <p><strong>Derived, never stored.</strong> A "current version" column on the product would be a
 * second place the answer lives, and two stored figures that must agree eventually stop agreeing —
 * the day somebody publishes a version and the flag is not moved, the catalogue advertises one rate
 * and the accounts opened that morning are written under another. This application already holds
 * that line for a points balance, for a goal's status and for every offer window, and the
 * subtraction here is cheaper than any of those.
 *
 * <p><strong>The rule is: the highest version whose day has come.</strong> A version dated ahead of
 * today has been published but is not being sold yet, which is how a rate change announced on
 * Monday for the first of next month is written down without anybody having to remember to press
 * something. Among the versions that have taken effect, the highest number wins rather than the
 * latest date — the two agree whenever versions are published in order, which is always, and
 * ordering by version is the tie-break that makes two rows sharing an effective date a settled
 * question rather than a coin toss.
 *
 * <p><strong>A product with nothing yet in force still answers, with its first version.</strong>
 * That case cannot arise from the seed, which dates every first version in the past, and it can
 * arise from a database somebody has edited — and the alternative is a product in the catalogue
 * with no rate on its card, which is worse than a rate that has not officially started. The lowest
 * version is the only honest thing to show: it is what the product was written with.
 *
 * <p>A class of its own rather than a method on the service, because it is a pure function over a
 * list and a date, it is asked by everything that reads a product, and the rule it states — what
 * "current" means — is the sort of thing that gets re-decided slightly differently in each of three
 * call sites. There is nothing to construct and nothing to inject.
 */
final class TheTermsOnOfferToday {

    /** Highest version last, so that the newest is the end of the list. */
    private static final Comparator<ProductTerms> BY_VERSION =
            Comparator.comparingInt(ProductTerms::version);

    private TheTermsOnOfferToday() {
    }

    /**
     * The version being sold on that day, out of every version a product has published.
     *
     * @param published every version of one product, in any order and never empty
     * @param today     the day the question is asked on, off the application's own clock
     * @throws IllegalArgumentException when handed no versions at all, which is a product whose
     *                                  terms were never written and is a broken database rather
     *                                  than a customer's mistake — there is no sentence to refuse
     *                                  somebody with, because nobody did anything wrong
     */
    static ProductTerms outOf(List<ProductTerms> published, LocalDate today) {
        if (published.isEmpty()) {
            throw new IllegalArgumentException(
                    "a product with no published terms cannot be offered, and this one has none");
        }
        return published.stream()
                .filter(terms -> !terms.effectiveFrom().isAfter(today))
                .max(BY_VERSION)
                .orElseGet(() -> published.stream().min(BY_VERSION).orElseThrow());
    }
}
