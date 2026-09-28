package io.dataroots.savingstreak.products;

import java.util.List;

/**
 * One entry in a product's version history: the version itself, and what it changed about the
 * version before it.
 *
 * <p><strong>The pair rather than a wider {@link ASetOfTerms}.</strong> A set of terms is what one
 * agreement says, and it is the same record whether it is read off an account, off a card in the
 * catalogue or out of a history — adding a list of differences to it would put a fact about a
 * <em>pair</em> of versions onto a record that describes one, and would leave every other reader of
 * it holding a field that is empty for a reason they have to look up.
 *
 * <p><strong>The differences are the same function's answer as the account's reading.</strong>
 * {@link WhatIsDifferentBetweenTwoSetsOfTerms} words both, which is what makes "how the offer has
 * moved over time" and "what taking the newer terms would change" two views of one comparison rather
 * than two comparisons that happen to agree. A customer reading the history and then their own
 * account meets the same sentence twice, word for word, which is the point.
 *
 * <p><strong>Empty on the first version, because nothing changed.</strong> Version one is what the
 * product has always said; there is no version before it to have moved from, and an invented line
 * saying "this is where it started" would be the history narrating itself. The same reading
 * {@link ASetOfTerms#whatChanged} already takes of the prose beside it, which is null on a first
 * version for the same reason.
 *
 * <p>It is also empty on a version that moved no figure — one published to reword the explanation,
 * or to put a rate back where it was — and that is not the same statement as the first version's,
 * but it renders identically and should: a version that changed nothing has nothing to list.
 */
public record AVersionAndWhatItChanged(

        /** The version itself, exactly as every other reading of a version sends it. */
        ASetOfTerms terms,

        /**
         * One sentence per figure this version moved from the version before it, and empty on the
         * first version and on a version that moved nothing.
         */
        List<String> whatIsDifferent) {
}
