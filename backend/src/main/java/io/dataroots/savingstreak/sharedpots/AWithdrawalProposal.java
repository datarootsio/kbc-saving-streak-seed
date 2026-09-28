package io.dataroots.savingstreak.sharedpots;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A proposal to take money out of a shared pot, as the rest of the application sees one: which pot
 * and what it is called, who asked and how much for, which of their own current accounts it would go
 * to, where it stands, when it was made and closed, whose approval it is still waiting on, and who
 * has answered it.
 *
 * <p>One shape for a proposal just made, a proposal in the pot's list and a proposal that has been
 * taken back, so that nothing rendering one has to know which of those it is holding in order to
 * render it.
 *
 * <p><strong>{@code whoseAssentItNeeds} is the answer this whole feature is about</strong>, and it
 * is derived on every read rather than stored: it is every other member who still has money in the
 * pot, and both halves of that move while a proposal waits. An empty list is an answer and not an
 * absence — it says the proposer is the only member with money in the pot, so their withdrawal needs
 * nobody's approval, because there is nobody else's money to spend. {@link
 * WhoseAssentAWithdrawalNeeds} is the rule and argues it out.
 *
 * <p><strong>{@code answeredBy} is the other half of that sentence</strong>: every member who has
 * answered, which way they went and when. The two lists are disjoint and together they are the whole
 * story of a proposal — the people still to answer, and the people who have. A member moves from the
 * first to the second the moment they approve or reject, which is why {@code whoseAssentItNeeds} is
 * the members whose money is at stake <em>less</em> the members who have already spoken.
 *
 * <p>Which way each of them answered, and not merely that they did, because a rejected proposal
 * would otherwise read exactly like an approved one with somebody still to answer.
 *
 * <p>The proposer's name and the pot's name travel beside their identifiers, for the reason
 * {@link APotInvitation} gives: "Bram is asking to take EUR 500.00 out of Kitchen" is the sentence
 * this list writes, and a caller that had to ask Accounts for a name and Shared Pots for a pot in
 * order to write it would be making two more calls to say one thing.
 *
 * <p>The amount is quoted to the cent, because a figure that has been through SQLite comes back as
 * 500.5 and a page that had to decide how many places money has would be deciding it again.
 *
 * <p>The stored proposal stays inside the module; this is a statement about somebody having asked
 * for something.
 */
public record AWithdrawalProposal(Long id, Long potId, String potName, Long proposedByCustomerId,
                                  String proposedByName, BigDecimal amount,
                                  Long toCurrentAccountId, ProposalState state, Instant proposedAt,
                                  Instant closedAt, List<APotMember> whoseAssentItNeeds,
                                  List<AnAnswerToAProposal> answeredBy) {
}
