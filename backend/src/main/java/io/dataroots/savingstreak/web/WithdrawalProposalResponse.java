package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.dataroots.savingstreak.sharedpots.AWithdrawalProposal;

/**
 * A proposal to take money out of a shared pot as the API reports one: which pot and what it is
 * called, who asked and for how much, where the money would go, where the asking stands, when it was
 * made and closed, whose approval it is still waiting on, and who has answered it.
 *
 * <p>One shape for a proposal just made, a proposal in the pot's list and a proposal that has been
 * taken back, so that nothing rendering one has to know which of those it is holding.
 *
 * <p><strong>{@code whoseAssentItNeeds} is the field this whole feature exists for.</strong> It is
 * every other member who still has money in the pot — the people whose euros a withdrawal would
 * actually spend — and an empty list is an answer: the member who proposed it is the only one with
 * money in the pot, so nobody's approval is needed, because there is nobody else's money to spend. A
 * page renders it as the row of faces still to answer, and as nothing at all when there are none.
 *
 * <p>{@code answeredBy} is the other half of it: every member who has answered, which way they went
 * and when. The two lists are disjoint — a member moves from the first to the second the moment they
 * answer — and together they are the whole story of a proposal, which is what story 58 asks a page
 * to be able to show. Empty on a proposal nobody had to approve, because nobody was asked.
 *
 * <p>The proposer's name and the pot's name travel beside their identifiers, for the reason an
 * invitation's do: "Bram De Vos is asking to take EUR 500.00 out of Kitchen" is the sentence this
 * list writes, and a page that had to make two more calls to write it would be two round trips short
 * of saying one thing.
 *
 * <p>The state travels as its own word, the idiom a goal's state and an invitation's already set:
 * whoever renders it decides what to call {@code PROPOSED} and which of the three closed ones to put
 * a line through.
 *
 * <p>{@code closedAt} is null while the proposal is still waiting, which is the one absence here and
 * the one that says nobody has decided anything yet.
 */
record WithdrawalProposalResponse(Long id, Long potId, String potName, Long proposedByCustomerId,
                                  String proposedByName, BigDecimal amount, Long toCurrentAccountId,
                                  String state, Instant proposedAt, Instant closedAt,
                                  List<PotMemberResponse> whoseAssentItNeeds,
                                  List<ProposalAnswerResponse> answeredBy) {

    static WithdrawalProposalResponse of(AWithdrawalProposal proposal) {
        return new WithdrawalProposalResponse(proposal.id(), proposal.potId(), proposal.potName(),
                proposal.proposedByCustomerId(), proposal.proposedByName(), proposal.amount(),
                proposal.toCurrentAccountId(), proposal.state().name(), proposal.proposedAt(),
                proposal.closedAt(),
                proposal.whoseAssentItNeeds().stream().map(PotMemberResponse::of).toList(),
                proposal.answeredBy().stream().map(ProposalAnswerResponse::of).toList());
    }
}
