package io.dataroots.savingstreak.web;

import java.util.List;

/**
 * What a customer sends to fix what a spend was for: the whole split, and nothing else at all.
 *
 * <p><strong>The absences here are the feature.</strong> There is no name and no amount, because
 * neither can be changed: the money moved, and the only thing about a spend that was ever an opinion
 * is which categories it belongs to. A field for either would be the first step towards an endpoint
 * that rewrote what a balance did, and there is deliberately no such endpoint and no such field.
 *
 * <p><strong>The whole split rather than the part that changed.</strong> The parts have to sum to
 * what was spent, so a request naming one of them would leave the application to decide which of the
 * others lost the difference — which is exactly the arithmetic a customer is correcting. Sending all
 * of them is also what makes a correction idempotent: the same request twice says the same thing.
 *
 * <p>The parts are {@link NewSpendPartRequest}s, the very ones recording a spend arrives with,
 * because a corrected split is held to exactly the rules the original was held to. A shape of its
 * own here would be a second place for those two fields to drift apart.
 */
record CorrectedSplitRequest(List<NewSpendPartRequest> parts) {
}
