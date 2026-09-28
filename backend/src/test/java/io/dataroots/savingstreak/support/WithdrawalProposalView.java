package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A proposal to take money out of a shared pot as the API reports one, which is exactly as a test
 * reads it: which pot and what it is called, who asked and for how much, where the money would go,
 * where the asking stands, when it was made and closed, whose approval it is still waiting on, and
 * who has answered it.
 *
 * <p>One shape for the proposal a pot lists, the proposal a member has just made and the proposal a
 * DELETE hands straight back — which is the API's own promise, and reading all three as one record
 * is what would fail if it ever stopped being true. Shared by every test that reads a proposal, so
 * that none of them can drift into disagreeing about the shape of the answer.
 *
 * <p>{@code whoseAssentItNeeds} is the field these tests are really about: every other member who
 * still has money in the pot. An empty list is an answer and not an absence — it says the proposer
 * is the only one with money in there, so nobody's approval is needed.
 *
 * <p>The state is read as the word the API sends rather than mapped onto an enum of the test's own,
 * for the reason {@link PotMemberView} gives: a rename in the backend should fail a test rather than
 * be quietly translated back.
 *
 * <p>{@code answeredBy} is the other half of that: every member who has answered, which way they
 * went and when. A member moves from the first list to the second the moment they answer, and the
 * last approval to arrive is the one the money moves in — so a test that reads both lists is reading
 * the whole state of a decision.
 *
 * <p>{@code closedAt} is null while the proposal is still waiting, which is the one absence here.
 */
public record WithdrawalProposalView(Long id, Long potId, String potName,
                                     Long proposedByCustomerId, String proposedByName,
                                     BigDecimal amount, Long toCurrentAccountId, String state,
                                     Instant proposedAt, Instant closedAt,
                                     List<PotMemberView> whoseAssentItNeeds,
                                     List<ProposalAnswerView> answeredBy) {
}
