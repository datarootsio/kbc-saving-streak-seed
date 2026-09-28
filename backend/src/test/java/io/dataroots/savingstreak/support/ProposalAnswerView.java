package io.dataroots.savingstreak.support;

import java.time.Instant;

/**
 * One member's answer to a withdrawal proposal as the API reports it, which is exactly as a test
 * reads it: who answered, what they are to the pot, which way they went, and when.
 *
 * <p>Which way is the field these tests are really about. A list of people who had spoken would let
 * a rejected proposal pass a test written for an approved one, and the whole of this slice is that
 * the two are different.
 *
 * <p>The answer and the role are read as the words the API sends rather than mapped onto enums of
 * the test's own, for the reason {@link PotMemberView} gives: a rename in the backend should fail a
 * test rather than be quietly translated back.
 *
 * <p>The role is null for somebody who has answered and since left the pot, which is the one absence
 * here and is a fact rather than a gap.
 */
public record ProposalAnswerView(Long customerId, String name, String role, String answer,
                                 Instant answeredAt) {
}
