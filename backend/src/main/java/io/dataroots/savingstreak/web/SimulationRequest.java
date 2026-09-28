package io.dataroots.savingstreak.web;

import java.util.List;

/**
 * What a customer sends to ask about their futures: the branches they want folded. Which account
 * they are branches of is the one in the path.
 *
 * <p>A body rather than a query string, which is the whole reason this read is a {@code POST}: four
 * scenarios of ten changes each is neither readable in a log nor within anything's length
 * guarantees, and it is the same concession a saving rule's preview already makes for a rule as
 * typed.
 *
 * <p><strong>No scenarios at all is a question rather than an empty request.</strong> A customer who
 * has typed nothing is still asking what they are heading for, and that branch comes back whether or
 * not anybody asked for it — so an absent body, {@code {}} and {@code {"scenarios": []}} are all the
 * same question and all get the same answer. A page that has yet to let anybody type a branch still
 * has a column to draw.
 */
record SimulationRequest(List<AScenarioRequest> scenarios) {
}
