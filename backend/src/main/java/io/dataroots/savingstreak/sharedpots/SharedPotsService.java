package io.dataroots.savingstreak.sharedpots;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.Customer;
import io.dataroots.savingstreak.accounts.WhatACurrentAccountHolds;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.deposits.DepositPaidIn;
import io.dataroots.savingstreak.deposits.DepositStillHoldingMoney;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.deposits.RecordedWithdrawal;
import io.dataroots.savingstreak.deposits.WithdrawalsService;
import io.dataroots.savingstreak.goals.GoalsService;
import io.dataroots.savingstreak.goals.RecordedGoal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.AGAINST_THE_RULES;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.ALREADY_A_MEMBER;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.ALREADY_ANSWERED;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.ALREADY_INVITED;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.LAST_OWNER;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.MORE_THAN_THE_POT_HOLDS;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.NOT_ALLOWED;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.NOT_THEIR_INVITATION;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.NO_SUCH_CUSTOMER;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.NO_SUCH_INVITATION;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.NO_SUCH_MEMBER;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.NO_SUCH_POT;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.NO_SUCH_PROPOSAL;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.NO_SUCH_RECIPIENT;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.POT_IS_CLOSED;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.THE_PROPOSAL_IS_CLOSED;
import static io.dataroots.savingstreak.sharedpots.SharedPotRefused.Kind.TO_YOURSELF;

/**
 * The Shared Pots module's face to the rest of the application: a savings pot that belongs to a
 * group of customers rather than to one of them.
 *
 * <p>Built in {@link io.dataroots.savingstreak.gifting.GiftingService}'s image, because opening a
 * pot and making a gift are the same shape of act — check a customer, do the one thing, stamp the
 * moment off the clock, write the rows, hand back what happened — and because the two modules
 * address the same people in the same words. Its dependencies are that service's, less the ledger it
 * does not have: Accounts to say who exists and what they are called, the clock to say when, and
 * repositories of its own to remember the pot and who belongs to it.
 *
 * <p><strong>It owns no money.</strong> The euros live in the deposits module exactly as they
 * already do: a pot's savings account is a savings account, its balance is the sum of what the
 * deposits into it still hold, and this module never adds one up. What it owns is the pot, its
 * membership, and the rules about who may do what to it.
 *
 * <p>Proposals are what made it ask Deposits anything at all, and the sentence above is still true:
 * it asks for the deposits and sums nothing it has not been handed. Whether a proposal is for more
 * than the pot holds, and whose approval it needs, are both questions about money that is somebody
 * else's record — so the figures are fetched at the moment the question is put and never kept, which
 * is the same bargain every derived total in this application makes.
 *
 * <p><strong>And it owns an account that nobody holds.</strong> Opening a pot asks Accounts for a
 * savings account with no holding customer, which is what keeps a pot's money out of every
 * customer's personal list of accounts and out of their totals without a single screen having to
 * learn that pots exist. Accounts knows that an account may be held by nobody; it does not know why,
 * and the pot pointing at the account rather than the account at the pot is what keeps it that way.
 *
 * <p>A pot is opened with its first owner in the same transaction. There is no moment at which a pot
 * exists with nobody allowed to administer it, and the one transaction below is what makes that true
 * rather than intended.
 */
@Service
public class SharedPotsService {

    private static final Logger log = LoggerFactory.getLogger(SharedPotsService.class);

    private final SharedPotRepository pots;
    private final PotMembershipRepository memberships;
    private final PotInvitationRepository invitations;
    private final AccountsService accounts;
    private final Clock clock;

    /** The proposals to take money out of a pot, which is the only way any of it ever leaves. */
    private final WithdrawalProposalRepository proposals;

    /**
     * Who has answered which proposal, and which way.
     *
     * <p>Rows rather than a count, for the reason {@link WithdrawalProposalAnswer} argues: every
     * rule here is about <em>which</em> members have spoken, and the one that moves money is
     * "everybody whose euros are at stake, less everybody who has already answered, is empty".
     */
    private final WithdrawalProposalAnswerRepository answers;

    /**
     * Asked what a pot's savings account holds and whose money is still in it, and asked nothing
     * else.
     *
     * <p>This module owns no money and is not about to start: the euros are deposits in the pot's
     * savings account, and both figures a proposal turns on — what the pot holds, and what is still
     * each member's — are sums over those deposits. Asking is what keeps them one answer; a total
     * kept here would be a second one that eventually disagreed.
     *
     * <p>This direction is the one the two modules can be wired in. Deposits asks Accounts, and
     * Accounts asks {@link MembersOfThePotMayPayIntoItsAccount} — a component of this module that
     * reads two repositories and nothing else, which is exactly why it is not this class. So nothing
     * downstream of here ever wants this service back, and the application context has no cycle to
     * refuse to start on.
     */
    private final DepositsService deposits;

    /**
     * Asked to move the money when a proposal has been approved, and asked nothing else.
     *
     * <p>Not {@code withdraw}, which refuses a pot's savings account outright and goes on refusing
     * it: that refusal is the promise that a pot's money leaves it by proposal, and this module is
     * where the promise is kept rather than where it is got round. The door it does open —
     * {@link WithdrawalsService#takeOutOfAPotWhatItsMembersApprovedOf} — takes no view on who may
     * take money out, because by the time it is called every member whose euros are at stake has
     * already said yes.
     *
     * <p>The draw-down itself stays over there, where it belongs. Which euros leave a savings
     * account is a rule about deposits — the oldest go first, whoever paid them in — and it is the
     * same rule for a pot as for anybody's own account. A copy of it here would be the copy that
     * drifted, and it is the very rule the approval gate exists to protect people from.
     */
    private final WithdrawalsService withdrawals;

    /**
     * Asked to abandon what the pot was saving for when it closes, and asked nothing else.
     *
     * <p>A pot's goals are goals on the pot's savings account and nothing about the Goals module
     * changed for this feature, so there is no second idea here to unwind: closing asks Goals to
     * give up on each live goal exactly as an owner clicking "abandon" would, and whatever a goal
     * was holding goes back to unallocated in one recorded move. Working that out here would be a
     * second copy of a rule that already exists, kept by the module that owns neither the goal nor
     * the allocation.
     *
     * <p>The dependency runs the only way it can and there is no cycle to refuse to start on: Goals
     * reads no other module at all — it cannot tell a pot's account from a person's, which is the
     * very reason {@link WhatAPotIsSavingForIsItsOwnersDecision} exists — so nothing under it ever
     * wants this service back.
     */
    private final GoalsService goals;

    SharedPotsService(SharedPotRepository pots, PotMembershipRepository memberships,
                      PotInvitationRepository invitations, AccountsService accounts, Clock clock,
                      WithdrawalProposalRepository proposals,
                      WithdrawalProposalAnswerRepository answers, DepositsService deposits,
                      WithdrawalsService withdrawals, GoalsService goals) {
        this.pots = pots;
        this.memberships = memberships;
        this.invitations = invitations;
        this.accounts = accounts;
        this.clock = clock;
        this.proposals = proposals;
        this.answers = answers;
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.goals = goals;
    }

    /**
     * How to tell somebody that the pot they named is not there, in words they can act on.
     *
     * <p>Every endpoint this feature has will have to say it at some point — reading a pot, inviting
     * somebody into one, proposing a withdrawal from one — and several sentences written in several
     * places are several sentences one rewording away from disagreeing about what absence sounds
     * like. The same reason Accounts owns what an absent savings account is called.
     *
     * <p>A sentence rather than a refusal, because who refuses and how it is reported differ.
     */
    public static String noSuchPot(long potId) {
        return "There is no shared pot " + potId + ".";
    }

    /**
     * Opens a pot for the customer who asked for it, and answers with the pot that now exists.
     *
     * <p>Three things happen and they are one transaction: a savings account held by nobody is
     * opened, the pot is written against it, and the customer who asked becomes its owner. There is
     * no order of those in which the application can be interrupted and left with a pot nobody
     * administers, or with an account belonging to no pot and no person.
     *
     * <p>It starts empty, and nothing of the customer's own savings is moved or reinterpreted: their
     * accounts, their balance, their mark and their goals go on meaning exactly what they meant
     * before. A pot is somewhere new to save, not a relabelling of somewhere they already were.
     *
     * <p>Two things are refused and nothing else: a customer this application has never heard of,
     * and a pot with no name or a name of nothing but spaces. Who is asking is settled before what
     * they asked for, because "there is no customer 41" is the more useful sentence of the two when
     * both are wrong — a page signed in as nobody will go on getting every other request wrong as
     * well, and a name is the easier of the two to fix once you know who you are.
     *
     * @param nameAsTyped what to call the pot, exactly as it was typed, so that a name of spaces is
     *                    ruled on here and answered in words rather than quietly stored
     * @throws SharedPotRefused if the pot is one of the two this module will not open
     */
    @Transactional
    public ASharedPot openAPot(long openedByCustomerId, String nameAsTyped) {
        String name = nameAsTyped == null ? "" : nameAsTyped.trim();
        log.debug("pot asked for openedByCustomerId={} name={}", openedByCustomerId, name);
        Customer owner = accounts.customerWith(openedByCustomerId)
                .orElseThrow(() -> refusing(openedByCustomerId, name, NO_SUCH_CUSTOMER,
                        AccountsService.noSuchCustomer(openedByCustomerId)));
        if (name.isBlank()) {
            throw refusing(openedByCustomerId, name, AGAINST_THE_RULES,
                    "Give the pot a name, so that everybody saving into it can tell what it is for.");
        }
        // Asked of Accounts rather than made here. What a savings account is, and that one may be
        // held by nobody, is that module's answer; this module only knows that its pot needs one.
        long savingsAccountId = accounts.openASavingsAccountNobodyHolds();
        // One moment for the pot and for the membership that comes with it, read from the
        // application's clock and truncated the way a gift's and a deposit's are, so that a pot
        // opened against a wound clock is dated where the trainer wound it to — and so that its
        // owner has belonged to it since exactly the moment it existed.
        Instant clockReads = clock.instant();
        Instant openedAt = clockReads.truncatedTo(ChronoUnit.MILLIS);
        log.debug("pot takes its moment from the application clock openedByCustomerId={} "
                + "clockReads={} recordedMoment={}", openedByCustomerId, clockReads, openedAt);

        SharedPot pot = pots.save(SharedPot.of(name, savingsAccountId, openedAt));
        PotMembership owning = memberships.save(
                PotMembership.of(pot.getId(), owner.getId(), PotRole.OWNER, openedAt));
        // One line per pot opened, with everything that decided it. A savings account appearing in
        // the nightly sweep that belongs to nobody at all is explainable from this line alone, and
        // so is a pot somebody says they never opened.
        log.info("pot opened potId={} name={} savingsAccountId={} ownerCustomerId={} openedAt={}",
                pot.getId(), pot.getName(), pot.getSavingsAccountId(), owner.getId(),
                pot.getOpenedAt());
        return new ASharedPot(pot.getId(), pot.getName(), pot.getSavingsAccountId(),
                pot.getOpenedAt(), pot.getClosedAt(),
                List.of(new APotMember(owner.getId(), owner.getName(), owning.getRole(),
                        owning.getJoinedAt())));
    }

    /**
     * One pot as anybody looking at it finds it: what it is called, which account holds its money,
     * when it was opened, and who is in it with what role.
     *
     * <p>What the pot holds is not here, for the reason {@link ASharedPot} gives: the balance is the
     * deposits module's answer and this module owns no money. Whoever reports the pot puts the two
     * figures side by side.
     *
     * <p>Not yet asked who is doing the looking. Every member of a pot may read it, and until a pot
     * can have a second member there is nobody a check could keep out; the slice that lets somebody
     * in is the slice that decides what a stranger reading a pot is told.
     *
     * @throws SharedPotRefused if no pot answers to that identifier
     */
    @Transactional(readOnly = true)
    public ASharedPot potWith(long potId) {
        SharedPot pot = pots.findById(potId)
                .orElseThrow(() -> refusingAbout(potId, NO_SUCH_POT, noSuchPot(potId)));
        List<APotMember> members = membersIn(pot.getId(), new HashMap<>());
        log.debug("pot read potId={} savingsAccountId={} members={} closedAt={}",
                pot.getId(), pot.getSavingsAccountId(), members.size(), pot.getClosedAt());
        return new ASharedPot(pot.getId(), pot.getName(), pot.getSavingsAccountId(),
                pot.getOpenedAt(), pot.getClosedAt(), members);
    }

    /**
     * Who belongs to the pot and what each of them is to it, in the order they joined — which puts
     * the owner who opened it first.
     *
     * <p>Readable on its own as well as on the pot, because a page showing who can do what is a page
     * of its own and has no use for the rest. The same list either way, so the two cannot disagree.
     *
     * @throws SharedPotRefused if no pot answers to that identifier
     */
    @Transactional(readOnly = true)
    public List<APotMember> membersOf(long potId) {
        if (!pots.existsById(potId)) {
            throw refusingAbout(potId, NO_SUCH_POT, noSuchPot(potId));
        }
        List<APotMember> members = membersIn(potId, new HashMap<>());
        log.debug("pot members read potId={} members={}", potId, members.size());
        return members;
    }

    /**
     * Every pot this customer belongs to, oldest first, each with its whole membership — so that a
     * list can say both which pots somebody is in and what they are to each of them.
     *
     * <p>Their memberships are what is read, not the pots: a pot is in somebody's list because they
     * belong to it, and a customer who has left one is no longer in it. A customer who belongs to
     * none has an empty list, which is an answer rather than an absence.
     *
     * <p>Three queries however many pots come back — the memberships this customer has, the pots
     * they name, and the memberships of all of those pots — rather than a query per pot. Names are
     * asked of Accounts once per person appearing anywhere in the list, for the reason a list of
     * gifts asks once per person rather than once per row.
     *
     * <p>Whether the customer exists is not asked. A customer who belongs to no pots has an empty
     * list and one who does not exist is a mistake about who — a distinction the endpoint draws, in
     * the words and the status every other per-customer read of this application uses.
     */
    @Transactional(readOnly = true)
    public List<ASharedPot> potsOf(long customerId) {
        List<Long> belongsTo = memberships.findByCustomerIdOrderByJoinedAtAscIdAsc(customerId)
                .stream()
                .map(PotMembership::getSharedPotId)
                .toList();
        if (belongsTo.isEmpty()) {
            log.debug("pots listed customerId={} pots=0", customerId);
            return List.of();
        }
        Map<Long, String> namesById = new HashMap<>();
        Map<Long, List<APotMember>> membersByPot = membersInEachOf(belongsTo, namesById);
        List<ASharedPot> listed = pots.findByIdInOrderByIdAsc(belongsTo).stream()
                .map(pot -> new ASharedPot(pot.getId(), pot.getName(), pot.getSavingsAccountId(),
                        pot.getOpenedAt(), pot.getClosedAt(),
                        membersByPot.getOrDefault(pot.getId(), List.of())))
                .toList();
        log.debug("pots listed customerId={} pots={} people={}",
                customerId, listed.size(), namesById.size());
        return listed;
    }

    /**
     * Invites a customer into the pot by the address they bank under, and answers with the
     * invitation that is now waiting for them.
     *
     * <p>Modelled on {@link io.dataroots.savingstreak.gifting.GiftingService#give}, because
     * inviting somebody into a pot and giving them points are the same shape of act: an owner names
     * somebody by the email address that person banks under, the address is resolved the way signing
     * in resolves one, and the two refusals that follow from addressing a person — nobody banks under
     * that address, and that address is you — are gifting's own sentences rather than second
     * wordings of them.
     *
     * <p><strong>Nothing happens to the person invited.</strong> An invitation is a question, and the
     * answer is theirs: no membership is written here, nothing about what they hold changes, and a
     * pot they never answer about is a pot they are not in. {@link #accept} is where somebody becomes
     * a member, and it is the only place in this application where anybody but an opener does.
     *
     * <p><strong>Only an owner may ask.</strong> A contributor pays in and a viewer watches; who else
     * is in the pot is the owner's decision, because a member added by a contributor is a member the
     * owner never agreed to and — once withdrawals need everybody's assent — somebody whose approval
     * the rest of them now need. That refusal is the first in this application to answer 403.
     *
     * <p>Eight things are refused and nothing else: a pot nobody has heard of, a customer nobody has
     * heard of, a member who is not the owner, an address nobody banks under, an invitation to
     * yourself, a role that is not one of the three, somebody who is already in the pot, and somebody
     * who is already waiting to answer. Who is asking is settled before who they asked for, which is
     * settled before what they asked for — the order {@link #openAPot} sets and for the same reason:
     * a page signed in as nobody, or looking at the wrong pot, will go on getting every other request
     * wrong as well.
     *
     * @param roleAsTyped the role exactly as it arrived, so that a word this application has never
     *                    heard of is ruled on here and answered with the three that exist rather than
     *                    failing to be read at all
     * @throws SharedPotRefused if the invitation is one of the seven this module will not send
     */
    @Transactional
    public APotInvitation invite(long potId, long invitingCustomerId, String contactDetailsAsTyped,
                                 String roleAsTyped) {
        String addressedTo = contactDetailsAsTyped == null ? "" : contactDetailsAsTyped.trim();
        log.debug("pot invitation asked for potId={} invitingCustomerId={} addressedTo={} role={}",
                potId, invitingCustomerId, addressedTo, roleAsTyped);
        SharedPot pot = potWithId(potId);
        Customer inviting = accounts.customerWith(invitingCustomerId)
                .orElseThrow(() -> refusingAnInvitation(potId, null, invitingCustomerId,
                        NO_SUCH_CUSTOMER, AccountsService.noSuchCustomer(invitingCustomerId)));
        insistOnAnOwner(potId, invitingCustomerId, "invite somebody into");
        insistThePotIsOpen(pot, invitingCustomerId, "invite somebody into");
        // No address at all is nobody banking under it, which is the refusal it already has. Said
        // here rather than left to Accounts because looking somebody up trims the address first and
        // would throw on nothing at all — a fault rather than one of the refusals this method
        // promises. The endpoint asks for the address before it gets this far, in its own words; this
        // is for every caller that is not the endpoint. Gifting draws the same line.
        if (addressedTo.isBlank()) {
            throw refusingAnInvitation(potId, null, invitingCustomerId, NO_SUCH_RECIPIENT,
                    AccountsService.noCustomerBanksUnderThoseContactDetails());
        }
        // Found the way signing in finds somebody: trimmed, matched without regard to case. An owner
        // invites somebody by the address that person banks under, and it should not matter that they
        // capitalised it.
        Customer invited = accounts.customerIdentifiedBy(addressedTo)
                .orElseThrow(() -> refusingAnInvitation(potId, null, invitingCustomerId,
                        NO_SUCH_RECIPIENT,
                        AccountsService.noCustomerBanksUnderThoseContactDetails()));
        if (invited.getId().equals(inviting.getId())) {
            throw refusingAnInvitation(potId, null, invitingCustomerId, TO_YOURSELF,
                    "An invitation goes to somebody else, and " + inviting.getName()
                            + " is who you are signed in as.");
        }
        PotRole role = roleWrittenAs(roleAsTyped, "An invitation has to say which role it grants",
                reason -> refusingAnInvitation(potId, null, invitingCustomerId, AGAINST_THE_RULES,
                        reason));
        // Already in the pot, and the role they hold is left exactly as it was. This is the whole of
        // story 13: a second invitation that re-roled somebody would be a way of promoting a viewer
        // to an owner without anybody agreeing to it, and the person doing the promoting would be the
        // only one who ever knew. Changing a role is its own request, with its own rules.
        memberships.findBySharedPotIdAndCustomerId(potId, invited.getId()).ifPresent(already -> {
            throw refusingAnInvitation(potId, null, invitingCustomerId, ALREADY_A_MEMBER,
                    invited.getName() + " is already a member of this pot, as "
                            + already.getRole() + ".");
        });
        // And nobody is asked the same question twice. Two invitations waiting to one pot would be
        // two answers to one question, and both accepted would be two memberships for one person —
        // which one_membership_per_customer_per_pot refuses, out of the middle of an acceptance that
        // had every reason to think it was fine.
        invitations.findBySharedPotIdAndInvitedCustomerIdAndState(potId, invited.getId(),
                InvitationState.PENDING).ifPresent(waiting -> {
                    throw refusingAnInvitation(potId, waiting.getId(), invitingCustomerId,
                            ALREADY_INVITED, invited.getName()
                                    + " has already been invited to this pot and has not answered yet.");
                });
        // One moment, read from the application's clock and truncated the way a pot's and a gift's
        // are, so that an invitation sent against a wound clock is dated where the trainer wound it
        // to.
        Instant invitedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        PotInvitation invitation = invitations.save(PotInvitation.of(potId, inviting.getId(),
                invited.getId(), role, invitedAt));
        // One line per invitation sent, with everything that decided it. Somebody turning up in a pot
        // they say they never joined is explainable from this line and its acceptance alone.
        log.info("pot invitation sent invitationId={} potId={} invitedByCustomerId={} "
                        + "invitedCustomerId={} role={} invitedAt={}",
                invitation.getId(), potId, inviting.getId(), invited.getId(), role, invitedAt);
        return asAnInvitation(invitation, pot.getName(), namesOf(inviting, invited));
    }

    /**
     * Every invitation the pot has issued, in any state, oldest first — the owner's record of who has
     * been asked and what came of it.
     *
     * <p>Answered ones as well as waiting ones, because this is a record rather than an inbox: an
     * invitation that vanished the moment it was declined would leave an owner asking the same person
     * again, and would lose the only trace that they said no. The panel that empties is
     * {@link #invitationsWaitingFor}, which is the other side of the same rows.
     *
     * <p>Not yet asked who is doing the looking. Reading a pot is not yet gated on membership — the
     * slice that decides what a stranger reading a pot is told will decide it for both reads at once,
     * and having one of them ask and the other not would be two answers to one question.
     *
     * @throws SharedPotRefused if no pot answers to that identifier
     */
    @Transactional(readOnly = true)
    public List<APotInvitation> invitationsIssuedBy(long potId) {
        SharedPot pot = potWithId(potId);
        Map<Long, String> namesById = new HashMap<>();
        List<APotInvitation> issued =
                invitations.findBySharedPotIdOrderByInvitedAtAscIdAsc(potId).stream()
                        .map(invitation -> asAnInvitation(invitation, pot.getName(), namesById))
                        .toList();
        log.debug("pot invitations listed potId={} invitations={} people={}",
                potId, issued.size(), namesById.size());
        return issued;
    }

    /**
     * The invitations waiting for this customer to answer, oldest first, each naming the pot it is
     * about, who is asking and what they are being offered.
     *
     * <p>Waiting ones only, because this is the panel a person reads and a panel is a list of
     * questions: an invitation they have already answered is not waiting for them, and one that was
     * revoked was taken back before they ever answered it. What became of any of them is the pot's
     * record.
     *
     * <p>Two queries however many invitations come back — the invitations, then the pots they name —
     * rather than a query per row, and names are asked of Accounts once per person appearing anywhere
     * in the list, for the reason a list of gifts asks once per person rather than once per row.
     *
     * <p>Whether the customer exists is not asked. A customer nobody is asking anything has an empty
     * list and one who does not exist is a mistake about who — a distinction the endpoint draws, in
     * the words and the status every other per-customer read of this application uses.
     */
    @Transactional(readOnly = true)
    public List<APotInvitation> invitationsWaitingFor(long customerId) {
        List<PotInvitation> waiting = invitations
                .findByInvitedCustomerIdAndStateOrderByInvitedAtAscIdAsc(customerId,
                        InvitationState.PENDING);
        if (waiting.isEmpty()) {
            log.debug("pot invitations waiting customerId={} invitations=0", customerId);
            return List.of();
        }
        Map<Long, String> potNames = namesOfThePots(waiting);
        Map<Long, String> namesById = new HashMap<>();
        List<APotInvitation> listed = waiting.stream()
                .map(invitation -> asAnInvitation(invitation,
                        potNames.get(invitation.getSharedPotId()), namesById))
                .toList();
        log.debug("pot invitations waiting customerId={} invitations={} pots={}",
                customerId, listed.size(), potNames.size());
        return listed;
    }

    /**
     * The invited customer accepts, which makes them a member of the pot with the role the invitation
     * named, and answers with the invitation as it now reads.
     *
     * <p><strong>The membership and the answer are one transaction.</strong> There is no order of the
     * two in which the application can be interrupted and leave an invitation somebody has accepted
     * beside a pot they are not in, or a member of a pot with an invitation still waiting to be
     * answered a second time.
     *
     * <p>The role is the invitation's rather than anything sent with the answer: what somebody is
     * agreeing to is what they were offered, and an acceptance that could name its own role would let
     * a viewer let themselves in as an owner.
     *
     * <p>Four things are refused: a pot nobody has heard of, an invitation this pot never issued, an
     * invitation addressed to somebody else, and an invitation that has already been answered. Whose
     * invitation it is comes before what has become of it, for the reason the order everywhere else
     * in this module has: being told that somebody else's invitation was already accepted is being
     * told something about somebody else.
     *
     * @throws SharedPotRefused if the acceptance is one of the four this module will not make
     */
    @Transactional
    public APotInvitation accept(long potId, long invitationId, long answeringCustomerId) {
        return answer(potId, invitationId, answeringCustomerId, InvitationState.ACCEPTED);
    }

    /**
     * The invited customer declines, which leaves them a member of nothing and closes the invitation.
     *
     * <p>A state rather than a deletion, because being asked and saying no is a thing that happened:
     * an owner who could not see it would ask again, and the pot's record would read as though nobody
     * had ever been asked.
     *
     * <p>Refused for exactly what an acceptance is refused for, because it is the same invitation
     * being answered by the same person: an answer is an answer, whichever way it goes.
     *
     * @throws SharedPotRefused if the invitation is not this customer's to answer, or not waiting
     */
    @Transactional
    public APotInvitation decline(long potId, long invitationId, long answeringCustomerId) {
        return answer(potId, invitationId, answeringCustomerId, InvitationState.DECLINED);
    }

    /**
     * An owner takes back an invitation nobody has answered yet — a mistyped address, or a change of
     * mind — and answers with the invitation as it now reads.
     *
     * <p>Only while it is waiting. An invitation somebody has already accepted is a membership, and
     * removing a member is a different act with different consequences: their money is in the pot and
     * it has to come back to them. That is its own slice, and revoking is deliberately not a back
     * door into it.
     *
     * <p>Only an owner, for the reason {@link #invite} gives: who is in the pot is the owner's
     * decision, and taking back the question is the same decision as asking it.
     *
     * @throws SharedPotRefused if the pot, the invitation, the customer or the invitation's state
     *                          will not allow it
     */
    @Transactional
    public APotInvitation revoke(long potId, long invitationId, long revokingCustomerId) {
        SharedPot pot = potWithId(potId);
        PotInvitation invitation = invitationWithId(potId, invitationId);
        if (!accounts.customerExists(revokingCustomerId)) {
            throw refusingAnInvitation(potId, invitationId, revokingCustomerId, NO_SUCH_CUSTOMER,
                    AccountsService.noSuchCustomer(revokingCustomerId));
        }
        insistOnAnOwner(potId, revokingCustomerId, "take back an invitation to");
        insistThePotIsOpen(pot, revokingCustomerId, "take back an invitation to");
        insistItIsStillWaiting(invitation, revokingCustomerId);
        Instant revokedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        invitation.answeredAs(InvitationState.REVOKED, revokedAt);
        log.info("pot invitation revoked invitationId={} potId={} revokedByCustomerId={} "
                        + "invitedCustomerId={} role={} revokedAt={}",
                invitation.getId(), potId, revokingCustomerId, invitation.getInvitedCustomerId(),
                invitation.getRole(), revokedAt);
        return asAnInvitation(invitation, pot.getName(), new HashMap<>());
    }

    /**
     * An owner changes what a member is to the pot, and answers with the membership as it now reads.
     *
     * <p><strong>The word this writes is the word every rule reads.</strong> Nothing is cached and
     * nothing is copied: a promoted viewer may pay into the pot on their very next request, and a
     * demoted contributor may not, because the pairing a deposit is judged by asks this row what
     * they are each time it is asked at all. That is the whole of story 18, and it is why this
     * method writes one column and no more.
     *
     * <p><strong>And a pot always has at least one owner.</strong> The last owner giving up the role
     * is refused and told to pass ownership on first — stories 20 and 21, and the reason this slice
     * exists at all. A pot whose last owner stepped down has nobody who may invite anybody, nobody
     * who may promote anybody back, and therefore no way back: it would be stuck with the membership
     * it had at the moment of the click, for good. Every later way of losing an owner — leaving,
     * being removed — is the same rule asked again, which is why it is stated here over the pot's
     * whole membership rather than as a fact about the person asking.
     *
     * <p>Only an owner may do it, in the words inviting already uses. Somebody promoting themselves
     * would be the role model undone in one request, and a contributor who could make themselves an
     * owner would make "contributor" mean nothing at all.
     *
     * <p>Six things are refused and nothing else: a pot nobody has heard of, a customer nobody has
     * heard of doing it, a member who is not the owner, a customer who is not in this pot, a role
     * that is not one of the three, and a change that would leave the pot ownerless. Who is asking
     * is settled before who they asked about, which is settled before what they asked for — the
     * order every other method in this module uses, and for the same reason.
     *
     * <p>Giving somebody the role they already hold is allowed and changes nothing anybody can see.
     * A form submitted twice is not a mistake worth a sentence, and refusing it would be an
     * application telling somebody off for a double click.
     *
     * @param roleAsTyped what the member is to be, exactly as it arrived, so that a word this
     *                    application has never heard of is ruled on here and answered with the three
     *                    that exist rather than failing to be read at all
     * @throws SharedPotRefused if the change is one of the six this module will not make
     */
    @Transactional
    public APotMember changeTheRoleOf(long potId, long memberCustomerId, long changingCustomerId,
                                      String roleAsTyped) {
        log.debug("pot role change asked for potId={} memberCustomerId={} changingCustomerId={} "
                + "role={}", potId, memberCustomerId, changingCustomerId, roleAsTyped);
        SharedPot pot = potWithId(potId);
        if (!accounts.customerExists(changingCustomerId)) {
            throw refusingAMembership(potId, memberCustomerId, changingCustomerId, NO_SUCH_CUSTOMER,
                    AccountsService.noSuchCustomer(changingCustomerId));
        }
        insistOnAnOwner(potId, changingCustomerId, "change a role in");
        insistThePotIsOpen(pot, changingCustomerId, "change a role in");
        // The pot's whole membership in one read, because two questions are asked of it and both
        // have to be answered of the same instant: what this member holds, and how many owners the
        // pot has. Asked separately they could be answered either side of somebody else's change,
        // and the pair that slipped through would be the one that left a pot with no owner at all.
        List<PotMembership> inThePot = memberships.findBySharedPotIdOrderByJoinedAtAscIdAsc(potId);
        PotMembership membership = inThePot.stream()
                .filter(member -> member.getCustomerId() == memberCustomerId)
                .findFirst()
                .orElseThrow(() -> refusingAMembership(potId, memberCustomerId, changingCustomerId,
                        NO_SUCH_MEMBER, "Customer " + memberCustomerId
                                + " is not a member of shared pot " + potId + "."));
        PotRole held = membership.getRole();
        PotRole role = roleWrittenAs(roleAsTyped, "A role change has to say which role to give them",
                reason -> refusingAMembership(potId, memberCustomerId, changingCustomerId,
                        AGAINST_THE_RULES, reason));
        long owners = ownersAmong(inThePot);
        // The inputs behind the one decision a reader cannot redo in their head: what they hold,
        // what they asked for, and how many owners there are to lose.
        log.debug("pot role change weighed potId={} memberCustomerId={} held={} asked={} owners={}",
                potId, memberCustomerId, held, role, owners);
        if (held == PotRole.OWNER && role != PotRole.OWNER) {
            // Second person, and honest in this one place: a pot with a single owner has nobody but
            // that owner who may change a role at all, so the only demotion that can ever reach this
            // is somebody stepping down from it themselves. Leaving is where the same rule has to
            // say it about a third party.
            insistThePotKeepsAnOwner(potId, memberCustomerId, changingCustomerId, inThePot,
                    "You are the only owner of this pot. Make somebody else an owner first, and "
                            + "then give up the role yourself.");
        }
        membership.holdsInstead(role);
        memberships.save(membership);
        long ownersAfterwards = owners + (role == PotRole.OWNER ? 1 : 0)
                - (held == PotRole.OWNER ? 1 : 0);
        // One line per role changed, with everything that decided it — including how many owners
        // the pot is left with, so that the invariant this method exists for can be read off the
        // log rather than taken on trust.
        log.info("pot role changed potId={} customerId={} from={} to={} changedByCustomerId={} "
                        + "owners={}",
                potId, memberCustomerId, held, role, changingCustomerId, ownersAfterwards);
        return asAMember(membership, new HashMap<>());
    }

    /**
     * A member leaves the pot — or an owner removes them — and the euros that are still their own
     * come back to a current account of theirs.
     *
     * <p><strong>One call for both, because they are one act.</strong> Leaving and being removed
     * differ in who decided and in nothing else: the same membership ends, the same money comes
     * back, the same record of what they paid in stays standing. Two endpoints would be two places
     * for the settlement to be written and one of them would eventually settle differently. Which of
     * the two happened is in the log line and in who sent the request; it is not a different
     * outcome, so it is not a different method.
     *
     * <p><strong>It needs nobody's approval, and that is the approval gate restated rather than got
     * round.</strong> A withdrawal from a pot waits on every other member with money in it because
     * it draws the account's oldest deposits down first whoever paid them in — it spends their euros
     * and, since what somebody has ever earned points on does not fall with what they hold, it costs
     * them the points on their next contributions too. A settlement cannot do any of that: it walks
     * only the deposits this member paid in, so the most it can reach is their own money and every
     * other member's remaining euros are exactly what they were a request ago. You can always take
     * back your own and never somebody else's; the gate and this method are the two halves of that
     * one sentence. It follows that a member whose proposal was rejected can still leave with their
     * own money, which is intended and is not a way round the rejection — the euros they take are
     * the euros nobody was protecting.
     *
     * <p><strong>Exact to the cent, with no share arithmetic anywhere.</strong> What comes back is
     * the sum of what remains of their own deposits, which is a figure the deposits already hold
     * rather than a proportion worked out from what the pot is worth. So there is no rounding rule
     * to state, no cent left over to decide the owner of, and no way for two members leaving in
     * either order to be settled different amounts than if they had gone the other way round.
     *
     * <p><strong>Leaving with nothing in the pot works and moves nothing.</strong> A viewer who never
     * paid in, and a contributor whose money has already gone out in an approved withdrawal, are both
     * settled nought — no withdrawal is recorded, because nothing happened to any money and a
     * movement of nought euros would be a line in the pot's history saying so for ever.
     *
     * <p><strong>What they paid in survives their leaving.</strong> Their deposits are drawn down,
     * not deleted, and their answers to the pot's proposals stay where they are: the pot's history
     * still adds up, and the record of who decided what still names them. What ends is the
     * membership, which is what being in a pot is — so from the next request they may not pay into
     * it, propose out of it or approve anything, and a proposal that was waiting on them stops
     * waiting, because whose assent a withdrawal needs is worked out from the membership as it
     * stands at the moment somebody asks.
     *
     * <p><strong>The last owner may not go.</strong> A pot whose only owner left would have nobody
     * who may invite, promote, change a goal or close it, and no way back — the same rule story 20
     * puts in front of a demotion, asked again here, which is why it is a method of its own rather
     * than a second copy of the count.
     *
     * <p>Six things are refused and nothing else: a pot nobody has heard of, a customer nobody has
     * heard of doing it, a member removing somebody who is not themselves without being an owner, a
     * customer who is not in this pot, a departure that would leave the pot ownerless, and a
     * settlement addressed to a current account that is not the leaving member's own. Who is asking
     * is settled before who they asked about, which is settled before where the money would go — the
     * order every other method in this module uses.
     *
     * @param memberCustomerId  whose membership ends and whose euros come back, named in the path
     *                          because the membership is the thing being ended
     * @param actingCustomerId  who decided, which is the whole difference between leaving and being
     *                          removed
     * @param toCurrentAccountId where the money goes, which must be the <em>departing</em> member's
     *                          own account and not the account of whoever removed them
     * @throws SharedPotRefused if the departure is one of the six this module will not make
     */
    @Transactional
    public ASettlement leaveThePot(long potId, long memberCustomerId, long actingCustomerId,
                                   long toCurrentAccountId) {
        log.debug("pot settlement asked for potId={} memberCustomerId={} actingCustomerId={} "
                        + "toCurrentAccountId={}",
                potId, memberCustomerId, actingCustomerId, toCurrentAccountId);
        SharedPot pot = potWithId(potId);
        if (!accounts.customerExists(actingCustomerId)) {
            throw refusingAMembership(potId, memberCustomerId, actingCustomerId, NO_SUCH_CUSTOMER,
                    AccountsService.noSuchCustomer(actingCustomerId));
        }
        // Anybody may leave; only an owner may make somebody else leave. Asked before the pot's
        // membership is read, because it is a question about the person asking and the module
        // settles that first — and because a contributor reaching for somebody else's membership
        // should be told what would have been needed rather than told about them.
        boolean leavingOfTheirOwnAccord = memberCustomerId == actingCustomerId;
        if (!leavingOfTheirOwnAccord) {
            insistOnAnOwner(potId, actingCustomerId, "remove a member from");
        }
        // Nobody leaves a pot that is over, and nobody is removed from one. Closing already
        // returned every member their own euros, so a departure afterwards would be a settlement
        // of nought dressed up as an act — and the membership it would end is the very row that
        // keeps the closed pot in that member's list of pots and their name on its record.
        insistThePotIsOpen(pot, actingCustomerId,
                leavingOfTheirOwnAccord ? "leave" : "remove a member from");
        // The pot's whole membership in one read, for the reason a role change reads it once: two
        // questions are asked of it — what this member holds, and how many owners the pot has — and
        // answered either side of somebody else's change they could let through the pair that leaves
        // a pot with no owner at all.
        List<PotMembership> inThePot = memberships.findBySharedPotIdOrderByJoinedAtAscIdAsc(potId);
        PotMembership membership = inThePot.stream()
                .filter(member -> member.getCustomerId() == memberCustomerId)
                .findFirst()
                .orElseThrow(() -> refusingAMembership(potId, memberCustomerId, actingCustomerId,
                        NO_SUCH_MEMBER, "Customer " + memberCustomerId
                                + " is not a member of shared pot " + potId + "."));
        Map<Long, String> namesById = new HashMap<>();
        String name = nameOf(memberCustomerId, namesById);
        if (membership.getRole() == PotRole.OWNER) {
            // Two sentences for one rule, because the person being told is not always the person it
            // is about. Somebody giving up their own last ownership is addressed; an owner told they
            // may not remove the pot's last owner is being told about a third party, and a second
            // person sentence would be telling them to make somebody else an owner and then step
            // down from a role they never held.
            insistThePotKeepsAnOwner(potId, memberCustomerId, actingCustomerId, inThePot,
                    leavingOfTheirOwnAccord
                            ? "You are the only owner of this pot. Make somebody else an owner "
                                    + "first, and then leave it yourself."
                            : name + " is the only owner of this pot. Make somebody else an owner "
                                    + "first, and then remove them.");
        }
        insistTheSettlementLandsInTheirOwnAccount(potId, memberCustomerId, name, actingCustomerId,
                toCurrentAccountId);

        // One read of the deposits for both figures, the way a proposal reads them: what is still
        // this member's is what they are owed, and what the pot holds is what all of them still hold
        // in it. Asked twice they could be answered either side of somebody else's deposit, and the
        // pot would be reported as holding one thing and settled out of another.
        Map<Long, BigDecimal> stillTheirs = whatIsStillEachMembersIn(pot.getSavingsAccountId());
        BigDecimal theirs = stillTheirs.getOrDefault(memberCustomerId, BigDecimal.ZERO);
        BigDecimal inThePotBefore = whatThePotHolds(stillTheirs);
        Instant settledAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        ASettlement settlement = settleOutOfThePot(pot, membership, name, toCurrentAccountId, theirs,
                inThePotBefore, settledAt);
        // The membership goes and nothing else does: the deposits they made are drawn down rather
        // than deleted, their answers to the pot's proposals stay, and the pot's record still names
        // them. Removing the row is the whole of leaving, because belonging to a pot is what that
        // row says.
        //
        // A close does not do this, and the difference is the whole difference between the two: a
        // pot that is over keeps its members, because the pot is now a record and the record is of
        // who saved for it together.
        memberships.delete(membership);
        // One line per member gone, with everything that decided it — which way it happened, what
        // they were to the pot, what they were settled and what the pot is left holding. A member
        // who says they never left, and a pot whose balance fell without a proposal, are both
        // explainable from this line alone.
        log.info("pot member {} potId={} customerId={} role={} decidedByCustomerId={} settled={} "
                        + "toCurrentAccountId={} withdrawalId={} potHeld={} potNowHolds={} "
                        + "owners={} settledAt={}",
                leavingOfTheirOwnAccord ? "left" : "removed", potId, memberCustomerId,
                membership.getRole(), actingCustomerId, AmountOfMoney.asMoney(theirs),
                toCurrentAccountId, settlement.withdrawalId(),
                AmountOfMoney.asMoney(inThePotBefore),
                AmountOfMoney.asMoney(inThePotBefore.subtract(theirs)),
                ownersAmong(inThePot) - (membership.getRole() == PotRole.OWNER ? 1 : 0), settledAt);
        return settlement;
    }

    /**
     * Gives one member back what is still their own out of the pot, and answers with the settlement
     * that describes it. It moves money and writes nothing else.
     *
     * <p><strong>One body for the two ways a member's euros come back, because it is one act.</strong>
     * Leaving takes one member out of a pot that goes on; closing takes every member out of a pot
     * that does not. What differs is who decided, how many times it happens and what becomes of the
     * membership afterwards — none of which is about the money. Two copies of the arithmetic would
     * be two answers to "what is still mine", and the copy that drifted would be the one nobody
     * watches, because a pot is closed once.
     *
     * <p><strong>The figure is their own remaining euros and never a share.</strong> It is handed in
     * rather than worked out here, because the caller has already read the pot's deposits once for
     * every member it is about — the whole point of {@link #whatIsStillEachMembersIn} being one read
     * — and a second reading per member would let two members be settled against two different
     * instants of the same account.
     *
     * <p><strong>Nought is not a movement.</strong> A viewer who never paid in, and a contributor
     * whose euros have all gone out in an approved withdrawal, are settled nought and no withdrawal
     * is recorded: there is no money to record the movement of, and a ledger line of EUR 0.00 would
     * sit in the pot's history for ever saying that nothing happened. The absent withdrawal is what
     * says so, which is why a settlement carries one and not a flag.
     *
     * <p>The membership is deliberately not ended here. Leaving ends it and closing keeps it, and a
     * method that decided which would be deciding something it has no way of knowing.
     *
     * @param name              what the member is called, handed in because the caller has asked
     *                          Accounts once for everybody this act is about
     * @param toCurrentAccountId where their money goes, which is null only when there is none of it:
     *                          a member who never paid into the pot has shown it no account to send
     *                          anything back to, and none is needed
     * @param thePotHeld        what the pot held before this settlement, so that the answer can say
     *                          what is left for everybody else without adding it up a second time
     */
    private ASettlement settleOutOfThePot(SharedPot pot, PotMembership membership, String name,
                                          Long toCurrentAccountId, BigDecimal theirs,
                                          BigDecimal thePotHeld, Instant at) {
        long memberCustomerId = membership.getCustomerId();
        // The inputs behind the one decision here a reader cannot redo in their head: what the pot
        // held, how much of it was this member's, and therefore what is left for everybody else.
        // The spec asks for exactly this line, because a settlement is the one place a member is
        // handed a figure that was never typed by anybody.
        log.debug("pot settlement worked out potId={} customerId={} savingsAccountId={} role={} "
                        + "potHolds={} stillTheirs={} leftForTheOthers={} toCurrentAccountId={}",
                pot.getId(), memberCustomerId, pot.getSavingsAccountId(), membership.getRole(),
                AmountOfMoney.asMoney(thePotHeld), AmountOfMoney.asMoney(theirs),
                AmountOfMoney.asMoney(thePotHeld.subtract(theirs)), toCurrentAccountId);
        Long withdrawalId = null;
        if (theirs.signum() > 0) {
            if (toCurrentAccountId == null) {
                // Money of theirs in the pot means a deposit of theirs into it, and every deposit
                // records the current account it came out of. So this is the record having gone
                // wrong rather than anything a person can act on, and it says so rather than
                // settling somebody's euros into nowhere.
                throw new IllegalStateException("customer " + memberCustomerId + " has money in "
                        + "shared pot " + pot.getId() + " and no contribution to it that says "
                        + "which current account it came from");
            }
            withdrawalId = withdrawals.returnToAPotsMemberWhatIsStillTheirOwn(
                    pot.getSavingsAccountId(), toCurrentAccountId, memberCustomerId, theirs).id();
        }
        return new ASettlement(pot.getId(), pot.getName(), memberCustomerId, name,
                membership.getRole(), membership.getJoinedAt(),
                AmountOfMoney.quotedToTheCent(theirs), toCurrentAccountId, withdrawalId, at,
                AmountOfMoney.quotedToTheCent(thePotHeld.subtract(theirs)));
    }

    /**
     * An owner closes the pot: every member gets their own remaining euros back, the goals the group
     * was saving towards are given up on, any proposal still waiting is ended, and the pot becomes a
     * record that can be read for ever and changed by nobody.
     *
     * <p><strong>Everything below happens in one transaction, and that is the whole of the
     * design.</strong> A close interrupted half-way would be the worst state this feature has: some
     * members paid out and others not, a pot that still says it is open, and no way to tell which
     * without reading the ledger by hand. There is no order of these steps in which that can happen,
     * because there is no moment between them.
     *
     * <p><strong>Each member gets their own euros, to the cent, and never a share.</strong> What
     * comes back to somebody is the sum of what remains of their own deposits — the same figure
     * leaving a pot hands them, worked out by the same method, so the two can never be different
     * amounts for the same person. Story 64: there is no arithmetic of proportions here, so no
     * rounding rule to state and no last cent to decide the owner of, and the settlements add up to
     * exactly what the pot held because both are summed from the very same deposits. A member who
     * never contributed is settled nothing and the close does not falter over them — nought is not a
     * movement, so no withdrawal is recorded and there is nowhere for one to go.
     *
     * <p><strong>The money goes where their most recent contribution came from.</strong> Nobody is
     * asked to name an account, because closing is one act and a form per member would turn it into
     * as many acts as there are people. The account each member last paid in from is the one they
     * have shown this application they use for this pot, and it was theirs at the moment the money
     * left it.
     *
     * <p><strong>Only an owner, and deliberately not "an owner who is not the last one".</strong>
     * Story 68 puts the decision to end the arrangement with the owners, in the 403 inviting and
     * changing a role already answer in. What this method pointedly does <em>not</em> ask is
     * {@link #insistThePotKeepsAnOwner}: that rule exists so that a pot is never left standing with
     * nobody able to administer it, and a pot that is not standing any more has nothing left to
     * administer. Closing is the one act in this module that is allowed to take the last owner with
     * it, because it takes everybody.
     *
     * <p><strong>The memberships stay exactly where they are.</strong> Leaving ends a membership;
     * closing does not end a single one. The pot is now a record of what a group of people saved for
     * together, and a record with its members deleted would be a pot nobody was ever in: it would
     * fall out of every member's list of pots, and the screen showing who paid what would have
     * nobody to show it for. Stories 66 and 62 are both that one sentence.
     *
     * <p><strong>The goals go, because nobody is saving for them any more.</strong> Story 65: a
     * goals screen left projecting towards a kitchen that was paid for and a pot that is empty is a
     * plan for a group that no longer exists. They are abandoned rather than deleted, through the
     * Goals module's own act, so what the group was saving for is still readable afterwards and
     * whatever each goal was holding went back to unallocated in one recorded move. They are
     * abandoned before the money leaves, so that no goal is ever holding a claim over euros that
     * have already gone back to the person who paid them in.
     *
     * <p><strong>And a proposal still waiting is ended.</strong> A question put to the members about
     * money that is no longer in the pot is a question nobody can answer, and leaving it at
     * {@code PROPOSED} would show a closed pot with a decision outstanding for ever. It ends as
     * {@code REJECTED}, which is the state this application already uses for a proposal that did not
     * go through for a reason that was not a member's "no" — the pot being unable to pay for it is
     * the other one — rather than as {@code WITHDRAWN}, which would put words into the proposer's
     * mouth. Nothing is lost by it: whatever the proposal asked for came back to the proposer
     * anyway, as their own settlement, if it was ever theirs.
     *
     * <p>Four things are refused and nothing else: a pot nobody has heard of, a customer nobody has
     * heard of, a member who is not an owner, and a pot that has already been closed. Who is asking
     * is settled before what they asked for, which is the order every other method in this module
     * uses — and the pot's own state comes last of the four, so that a contributor is told what
     * would have been needed rather than told about the pot.
     *
     * @param closingCustomerId who decided, which must be an owner and is the only thing about this
     *                          request that could have been anybody else
     * @throws SharedPotRefused if the close is one of the four this module will not make
     */
    @Transactional
    public APotClosed closeThePot(long potId, long closingCustomerId) {
        log.debug("pot close asked for potId={} closingCustomerId={}", potId, closingCustomerId);
        SharedPot pot = potWithId(potId);
        if (!accounts.customerExists(closingCustomerId)) {
            throw refusingAMembership(potId, null, closingCustomerId, NO_SUCH_CUSTOMER,
                    AccountsService.noSuchCustomer(closingCustomerId));
        }
        insistOnAnOwner(potId, closingCustomerId, "close");
        insistThePotIsOpen(pot, closingCustomerId, "close");

        // One moment for the whole close, read from the application's clock and truncated the way
        // every other moment this module writes is, so that the pot, the goals it gave up on, the
        // proposals it ended and every member's settlement are all dated to the one instant a
        // person would call "when we closed it".
        Instant closedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);

        // The goals first, so that nothing is claiming euros that are about to leave. Abandoning
        // gives back whatever each one was holding in a recorded move of its own, which is the
        // Goals module's rule and not a second copy of it here.
        List<Long> goalsAbandoned = abandonWhatThePotWasSavingFor(pot);
        List<Long> proposalsEnded = endWhatThePotWasStillDeciding(pot, closedAt);

        // The pot's membership and its deposits each read once for the whole close, which is what
        // ticket after ticket in this module has done for the same reason: every member is settled
        // against the same instant of the same account, so the figures add up to what the pot held
        // rather than to whatever it happened to hold when each of them was reached.
        List<PotMembership> inThePot = memberships.findBySharedPotIdOrderByJoinedAtAscIdAsc(potId);
        Map<Long, BigDecimal> stillTheirs = whatIsStillEachMembersIn(pot.getSavingsAccountId());
        Map<Long, Long> whereTheyPayFrom =
                whereEachMembersMostRecentContributionCameFrom(pot.getSavingsAccountId());
        BigDecimal thePotHeld = whatThePotHolds(stillTheirs);

        Map<Long, String> namesById = new HashMap<>();
        List<ASettlement> settledTo = new ArrayList<>();
        BigDecimal stillInThePot = thePotHeld;
        for (PotMembership membership : inThePot) {
            long memberCustomerId = membership.getCustomerId();
            BigDecimal theirs = stillTheirs.getOrDefault(memberCustomerId, BigDecimal.ZERO);
            settledTo.add(settleOutOfThePot(pot, membership, nameOf(memberCustomerId, namesById),
                    whereTheyPayFrom.get(memberCustomerId), theirs, stillInThePot, closedAt));
            stillInThePot = stillInThePot.subtract(theirs);
        }

        pot.closedAt(closedAt);
        pots.save(pot);

        // One line per pot closed, naming what each member was settled — which the spec asks for by
        // name, because a close is the one request in this application that hands several people a
        // figure at once and none of them typed any of it. Rendered whether or not anybody is
        // reading at DEBUG, unlike the per-member lines elsewhere in this class: a pot is closed
        // once and this is the only line that says what became of its money.
        List<String> perMember = new ArrayList<>();
        for (ASettlement settlement : settledTo) {
            perMember.add("[customerId=" + settlement.customerId()
                    + " role=" + settlement.role()
                    + " settled=" + AmountOfMoney.asMoney(settlement.settled())
                    + " toCurrentAccountId=" + settlement.toCurrentAccountId()
                    + " withdrawalId=" + settlement.withdrawalId() + "]");
        }
        log.info("pot closed potId={} name={} savingsAccountId={} closedByCustomerId={} members={} "
                        + "potHeld={} potNowHolds={} goalsAbandoned={} proposalsEnded={} "
                        + "closedAt={} settled={}",
                potId, pot.getName(), pot.getSavingsAccountId(), closingCustomerId,
                settledTo.size(), AmountOfMoney.asMoney(thePotHeld),
                AmountOfMoney.asMoney(stillInThePot), goalsAbandoned, proposalsEnded, closedAt,
                String.join(" ", perMember));
        return new APotClosed(potId, pot.getName(), pot.getSavingsAccountId(), closedAt,
                AmountOfMoney.quotedToTheCent(thePotHeld),
                AmountOfMoney.quotedToTheCent(stillInThePot), settledTo, goalsAbandoned,
                proposalsEnded);
    }

    /**
     * Gives up on every goal the pot was still saving towards, and answers with the ones it gave up
     * on.
     *
     * <p>Asked of the Goals module rather than worked out here, because a pot's goals <em>are</em>
     * goals on the pot's savings account and abandoning one is an act that module already owns: it
     * renumbers what is left, returns whatever the goal was holding to unallocated in one recorded
     * move, and keeps the goal readable afterwards. A pot that unwound allocations by hand would be
     * a second copy of a rule nobody would think to keep in step.
     *
     * <p>The live ones only. A goal already given up on is not something anybody is saving for, and
     * abandoning it again would be refused by the rule that a goal is given up on once.
     */
    private List<Long> abandonWhatThePotWasSavingFor(SharedPot pot) {
        List<Long> live = goals.goalsOn(pot.getSavingsAccountId()).stream()
                .map(RecordedGoal::id)
                .toList();
        log.debug("pot goals to abandon on closing potId={} savingsAccountId={} goals={}",
                pot.getId(), pot.getSavingsAccountId(), live);
        for (Long goalId : live) {
            goals.abandonGoal(pot.getSavingsAccountId(), goalId);
        }
        return live;
    }

    /**
     * Ends every proposal the pot was still waiting on an answer to, and says which ones they were.
     *
     * <p>{@code REJECTED} rather than {@code WITHDRAWN}, argued out on {@link #closeThePot}: nobody
     * took these back, and the state a proposal ends in is a statement about who decided.
     *
     * <p>No answer is written for anybody. A close is not a member saying no — it is the question
     * ceasing to be asked — and inventing a rejection from somebody who never clicked one would put
     * a decision in the pot's record that nobody made.
     */
    private List<Long> endWhatThePotWasStillDeciding(SharedPot pot, Instant closedAt) {
        List<Long> ended = new ArrayList<>();
        for (WithdrawalProposal proposal
                : proposals.findBySharedPotIdOrderByProposedAtAscIdAsc(pot.getId())) {
            if (!proposal.getState().isStillWaiting()) {
                continue;
            }
            proposal.closedAs(ProposalState.REJECTED, closedAt);
            ended.add(proposal.getId());
            // One line per proposal the close ended, with the figure that is no longer being asked
            // for. A member who says nobody ever rejected their proposal is answered from this line
            // and the pot's own closing line beside it.
            log.info("pot withdrawal proposal ended by the pot closing proposalId={} potId={} "
                            + "proposedByCustomerId={} amount={} endedAt={}",
                    proposal.getId(), pot.getId(), proposal.getProposedByCustomerId(),
                    AmountOfMoney.asMoney(proposal.getAmount()), closedAt);
        }
        return ended;
    }

    /**
     * Which of their own current accounts each member last paid into this pot from.
     *
     * <p><strong>The account a close settles them into</strong>, and the reason nobody has to name
     * one: closing is a single act by a single owner, so asking every member where their money
     * should go would turn it into a form per person and leave the pot half-closed until the last
     * of them answered. The account somebody most recently paid in from is the account they are
     * using for this pot, and it was theirs at the moment the money came out of it — which is the
     * whole of what a settlement needs to know about it.
     *
     * <p>Most recent rather than first, because the first is the one most likely to have been
     * replaced: somebody who moves their saving to another current account of theirs keeps paying
     * into the pot from the new one, and the euros should follow them there.
     *
     * <p>A member who never contributed is simply absent, which is the same absence
     * {@link #whatIsStillEachMembersIn} leaves for them and means the same thing: there is nothing
     * of theirs in the pot and so nowhere for it to go back to.
     *
     * <p>The deposits arrive oldest first, so writing each one over the last leaves the most recent
     * standing. One read of the account for all of them, the arithmetic every other list in this
     * module does.
     */
    private Map<Long, Long> whereEachMembersMostRecentContributionCameFrom(long savingsAccountId) {
        Map<Long, Long> fromCurrentAccount = new LinkedHashMap<>();
        for (DepositPaidIn deposit : deposits.depositsPaidInto(savingsAccountId)) {
            fromCurrentAccount.put(deposit.customerId(), deposit.fromCurrentAccountId());
        }
        log.debug("where a pot would settle each member savingsAccountId={} contributors={} "
                + "fromCurrentAccountByCustomerId={}", savingsAccountId, fromCurrentAccount.size(),
                fromCurrentAccount);
        return fromCurrentAccount;
    }

    /**
     * Insists that the pot is left with somebody able to administer it, whichever way it is about to
     * lose an owner.
     *
     * <p>Stories 20 and 21, in one place because they are one rule asked twice: a demotion and a
     * departure are different acts with the same consequence, and a pot whose last owner goes either
     * way has nobody who may invite anybody, nobody who may promote anybody back, and therefore no
     * way back at all. Two counts written at the two call sites would be two chances to count it
     * differently, and the one that drifted would be the one that orphaned a pot.
     *
     * <p>Stated over the pot's whole membership rather than as a fact about the person asking. Who
     * is doing it decides the sentence and nothing else: the rule is about how many owners the pot
     * would have left.
     *
     * <p>The memberships are handed in rather than read here, because the caller has already read
     * them to find out what this member holds. Two reads a moment apart is how a pot comes to be
     * judged against one membership and changed against another.
     *
     * @param reason the sentence to refuse in, which the caller writes because the person being told
     *               is sometimes the last owner and sometimes somebody else looking at them
     */
    private void insistThePotKeepsAnOwner(long potId, long memberCustomerId, long actingCustomerId,
                                          List<PotMembership> inThePot, String reason) {
        long owners = ownersAmong(inThePot);
        log.debug("pot weighed for the owner it has to keep potId={} memberCustomerId={} "
                        + "actingCustomerId={} members={} owners={}",
                potId, memberCustomerId, actingCustomerId, inThePot.size(), owners);
        if (owners > 1) {
            return;
        }
        throw refusingAMembership(potId, memberCustomerId, actingCustomerId, LAST_OWNER, reason);
    }

    /** How many of a pot's members may administer it, which is the figure the rule above turns on. */
    private static long ownersAmong(List<PotMembership> inThePot) {
        return inThePot.stream().filter(member -> member.getRole() == PotRole.OWNER).count();
    }

    /**
     * Insists that the settlement lands in a current account the <em>departing</em> member holds.
     *
     * <p>Theirs and not the account of whoever removed them, which is the whole reason this is
     * checked at all: an owner who could name their own account when removing somebody would be
     * taking that member's money as well as their membership, and the request would look like
     * ordinary administration.
     *
     * <p>An account nobody holds and an account somebody else holds are refused in one sentence,
     * deliberately — {@code WithdrawalsService} and a withdrawal proposal draw the same line, and
     * naming whose an account is would tell the person asking something about a customer who is not
     * them. Said about the leaving member by name, because when the two people are different it is
     * the departing member's accounts the sentence is about.
     */
    private void insistTheSettlementLandsInTheirOwnAccount(long potId, long memberCustomerId,
                                                           String name, long actingCustomerId,
                                                           long toCurrentAccountId) {
        Long heldBy = accounts.currentAccountWith(toCurrentAccountId)
                .map(WhatACurrentAccountHolds::customerId)
                .orElse(null);
        log.debug("pot settlement destination read for a decision potId={} memberCustomerId={} "
                        + "toCurrentAccountId={} heldByCustomerId={}",
                potId, memberCustomerId, toCurrentAccountId, heldBy);
        if (heldBy != null && heldBy == memberCustomerId) {
            return;
        }
        throw refusingAMembership(potId, memberCustomerId, actingCustomerId, AGAINST_THE_RULES,
                "What is still a leaving member's comes back to one of their own current accounts, "
                        + "and current account " + toCurrentAccountId + " is not one of " + name
                        + "'s.");
    }

    /** One pot's members, with the names asked of Accounts once per person however many rows name them. */
    private List<APotMember> membersIn(long potId, Map<Long, String> namesById) {
        return memberships.findBySharedPotIdOrderByJoinedAtAscIdAsc(potId).stream()
                .map(membership -> asAMember(membership, namesById))
                .toList();
    }

    /** The same for several pots at once, grouped by the pot, each pot's members still in joining order. */
    private Map<Long, List<APotMember>> membersInEachOf(List<Long> potIds, Map<Long, String> namesById) {
        return memberships.findBySharedPotIdInOrderByJoinedAtAscIdAsc(potIds).stream()
                .collect(Collectors.groupingBy(PotMembership::getSharedPotId, LinkedHashMap::new,
                        Collectors.mapping(membership -> asAMember(membership, namesById),
                                Collectors.toList())));
    }

    private APotMember asAMember(PotMembership membership, Map<Long, String> namesById) {
        return new APotMember(membership.getCustomerId(),
                nameOf(membership.getCustomerId(), namesById), membership.getRole(),
                membership.getJoinedAt());
    }

    /**
     * What a member of a pot is called, asked once however many rows name them.
     *
     * <p>A membership can only have been written for a customer who existed, so one this application
     * has never heard of is not a refusal anybody can act on — it is the record having gone wrong,
     * and it says so rather than reporting a member with a blank beside them. The same line Gifting
     * draws about the two people on a gift.
     */
    private String nameOf(long customerId, Map<Long, String> namesById) {
        return namesById.computeIfAbsent(customerId, id -> accounts.customerWith(id)
                .map(Customer::getName)
                .orElseThrow(() -> new IllegalStateException("a shared pot has customer " + id
                        + " as a member, which this application has never heard of")));
    }

    /**
     * One answer to one invitation, whichever way it goes.
     *
     * <p>Accepting and declining differ in one thing — whether a membership is written — and in
     * nothing else: the same invitation, the same person entitled to answer it, the same rule that it
     * is answered once. Two methods that each restated those rules would be two places for them to
     * drift apart, and the one that drifted would be the one that let somebody in.
     */
    private APotInvitation answer(long potId, long invitationId, long answeringCustomerId,
                                  InvitationState answer) {
        SharedPot pot = potWithId(potId);
        PotInvitation invitation = invitationWithId(potId, invitationId);
        Customer answering = accounts.customerWith(answeringCustomerId)
                .orElseThrow(() -> refusingAnInvitation(potId, invitationId, answeringCustomerId,
                        NO_SUCH_CUSTOMER, AccountsService.noSuchCustomer(answeringCustomerId)));
        // Whose invitation it is, before what has become of it. Being told that somebody else's
        // invitation was accepted already is being told something about somebody else, and the
        // address it was sent to is deliberately not named back for the reason Gifting gives: this
        // application knows who exists and not who is typing.
        if (invitation.getInvitedCustomerId() != answering.getId()) {
            throw refusingAnInvitation(potId, invitationId, answeringCustomerId,
                    NOT_THEIR_INVITATION, "That invitation was addressed to somebody else, and "
                            + answering.getName() + " is who you are signed in as.");
        }
        // Before whether the invitation is still waiting, because the pot being over is the
        // larger piece of news and the one that makes the other moot: an invitation to a pot that
        // has closed is not a question anybody is still asking. Declining is refused as well as
        // accepting, deliberately — a closed pot takes no new answers of any kind, and an
        // invitation left waiting to a pot that is over says exactly what happened.
        insistThePotIsOpen(pot, answeringCustomerId, "answer an invitation to");
        insistItIsStillWaiting(invitation, answeringCustomerId);
        Instant answeredAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (answer == InvitationState.ACCEPTED) {
            // Checked although the invitation is waiting, because the two facts are not the same one:
            // somebody can have been invited, been added as the owner of the pot by some other route,
            // and then answered. The unique index would refuse the second membership as a duplicate
            // row, out of the middle of an acceptance, and a person reading that would learn nothing.
            memberships.findBySharedPotIdAndCustomerId(potId, answering.getId()).ifPresent(already -> {
                throw refusingAnInvitation(potId, invitationId, answeringCustomerId, ALREADY_A_MEMBER,
                        "You are already a member of this pot, as " + already.getRole() + ".");
            });
            PotMembership joining = memberships.save(PotMembership.of(potId, answering.getId(),
                    invitation.getRole(), answeredAt));
            log.debug("pot membership written by an acceptance potId={} customerId={} role={} "
                            + "joinedAt={}",
                    potId, answering.getId(), joining.getRole(), joining.getJoinedAt());
        }
        invitation.answeredAs(answer, answeredAt);
        // One line per answer, with everything that decided it — including the role, because an
        // acceptance is the only way anybody but the opener of a pot comes to hold one.
        log.info("pot invitation {} invitationId={} potId={} customerId={} role={} answeredAt={}",
                answer == InvitationState.ACCEPTED ? "accepted" : "declined",
                invitation.getId(), potId, answering.getId(), invitation.getRole(), answeredAt);
        return asAnInvitation(invitation, pot.getName(), namesOf(answering));
    }

    /** The pot itself, or the refusal that says it is not there, which every call about one starts with. */
    private SharedPot potWithId(long potId) {
        return pots.findById(potId)
                .orElseThrow(() -> refusingAbout(potId, NO_SUCH_POT, noSuchPot(potId)));
    }

    /**
     * One invitation of one pot, or the refusal that says there is no such thing.
     *
     * <p>The pot is part of what identifies it. An invitation answered under a pot that did not issue
     * it is answering something that is not there, and saying so is better than quietly answering an
     * invitation to somewhere else — the identifier in the path would have been a mistake nobody ever
     * saw.
     */
    private PotInvitation invitationWithId(long potId, long invitationId) {
        return invitations.findById(invitationId)
                .filter(invitation -> invitation.getSharedPotId() == potId)
                .orElseThrow(() -> refusingAnInvitation(potId, invitationId, null,
                        NO_SUCH_INVITATION, "There is no invitation " + invitationId
                                + " to shared pot " + potId + "."));
    }

    /**
     * Insists that the customer is this pot's owner, which is the only role that decides who is in
     * it.
     *
     * <p>Somebody who is not in the pot at all is refused in the same words as a contributor, and on
     * purpose: both are being told what would have been needed, and telling a stranger that they are
     * not a member would be telling them there is a pot here to be a member of.
     *
     * <p>Public, alone among the rules in this class, because it is the one sentence this module
     * says about who decides that somebody outside it has to be able to say too: what a pot is
     * saving for is judged in front of the Goals module by a controller, and
     * {@link WhatAPotIsSavingForIsItsOwnersDecision} asks this rather than writing a second
     * "Only an owner may" of its own.
     *
     * <p>The customer is boxed, and a request that names nobody is nobody — not an owner, and
     * refused in the same words a stranger is. Every other rule in this module is reached through an
     * endpoint that insists on a customer before it gets this far; the goal endpoints were there
     * before pots were and carry one only when a pot's account is what they were pointed at, so
     * "nobody said who" is a state this one rule really can be asked about.
     *
     * @param whatTheyTried the act, as a verb phrase the sentence can be built around, so that one
     *                      rule can answer for inviting, for taking an invitation back, for changing
     *                      a role and for every way of changing a pot's goals without the sentence
     *                      being written five times
     */
    public void insistOnAnOwner(long potId, Long customerId, String whatTheyTried) {
        PotRole role = customerId == null ? null
                : memberships.findBySharedPotIdAndCustomerId(potId, customerId)
                        .map(PotMembership::getRole)
                        .orElse(null);
        log.debug("pot role read for a decision potId={} customerId={} role={} tryingTo={}",
                potId, customerId, role, whatTheyTried);
        if (role != PotRole.OWNER) {
            throw refusingAnInvitation(potId, null, customerId, NOT_ALLOWED,
                    "Only an owner may " + whatTheyTried + " this pot.");
        }
    }

    /**
     * Insists that the pot has not been closed, which is the sentence in front of every act this
     * module allows.
     *
     * <p><strong>Story 67, in one place because it is one rule asked a dozen times.</strong> Paying
     * in, inviting, answering an invitation, changing a role, proposing, approving, rejecting,
     * taking a proposal back, leaving and closing again are each refused on a closed pot, and a
     * closed-check written at each of those would be ten chances to word it differently and one
     * chance to forget. What they have in common is exactly what this method says: the pot is over.
     *
     * <p><strong>And it guards no read at all.</strong> A closed pot's name, balance, membership,
     * contributions, money movements, invitations and proposals all go on answering, because the
     * record surviving is the whole point of closing rather than deleting — story 66. The rule is in
     * front of the acts and nowhere else, which is why it is called from them one at a time rather
     * than folded into {@link #potWithId}, where it would have silently shut the reads too.
     *
     * <p>Who is asking never comes into it: an owner, a contributor, a viewer and a stranger are all
     * told the same thing, because what is wrong is the pot rather than them. It is asked
     * <em>after</em> the role rules for that reason — somebody who was never allowed to do this
     * should be told so whether or not the pot is closed, or a contributor would learn from a closed
     * pot that they could have invited people into an open one.
     *
     * <p>The pot is handed in rather than read again, because every caller has already fetched it to
     * find out that it exists. Two reads a moment apart is how a pot comes to be judged open and
     * changed after it was closed.
     *
     * <p>Package-private rather than private, alone with {@link #insistOnAnOwner} among the rules
     * here, and for the same reason: what a pot is saving for is judged in front of the Goals module
     * by {@link WhatAPotIsSavingForIsItsOwnersDecision}, which holds the pot already and asks this
     * rather than writing a second sentence about what a closed pot is.
     *
     * @param whatTheyTried the act, as a verb phrase the sentence is built around, so that one rule
     *                      answers for paying in, inviting, proposing, leaving and closing without
     *                      the sentence being written ten times
     * @throws SharedPotRefused with {@code POT_IS_CLOSED}, which answers 409, if it has been closed
     */
    void insistThePotIsOpen(SharedPot pot, Long customerId, String whatTheyTried) {
        log.debug("pot read for a decision about whether it is still open potId={} customerId={} "
                        + "closedAt={} tryingTo={}",
                pot.getId(), customerId, pot.getClosedAt(), whatTheyTried);
        if (!pot.isClosed()) {
            return;
        }
        throw refusingAnInvitation(pot.getId(), null, customerId, POT_IS_CLOSED,
                "This shared pot was closed on " + pot.getClosedAt() + ", so nobody may "
                        + whatTheyTried + " it. What it did is still there to read.");
    }

    /**
     * Insists that the invitation is still waiting, which is the whole of "an invitation is answered
     * once".
     *
     * <p>The sentence names the state it is in rather than saying only that it is closed, because the
     * three are different news: somebody accepted, somebody said no, or the invitation was taken
     * back before it was ever seen.
     */
    private void insistItIsStillWaiting(PotInvitation invitation, long customerId) {
        if (invitation.getState() != InvitationState.PENDING) {
            throw refusingAnInvitation(invitation.getSharedPotId(), invitation.getId(), customerId,
                    ALREADY_ANSWERED, "That invitation is " + invitation.getState()
                            + ", and an invitation is answered once.");
        }
    }

    /**
     * The role somebody was offered, read off the word that was typed.
     *
     * <p>Ruled on here rather than parsed on the way in, for the reason a deposit's amount gives:
     * what is wrong with "TREASURER" is a fact about the roles a pot has, and it deserves an answer
     * naming the three that exist rather than a request that could not be read. The three are asked
     * of {@link PotRole} rather than written out, so that a role added later cannot leave this
     * sentence quietly out of date.
     *
     * <p>One reading for every request that carries a role word — an invitation offering one, a
     * change granting one — because the reading is the same act: trimmed, matched without regard to
     * case, and refused with the three that exist. Two copies would be two answers to "is
     * {@code contributor} a role", and the one that drifted would be the one that let a typed word
     * through.
     *
     * <p>What differs between them is only how the refusal is worded and logged, which each caller
     * hands in: a form with the role box empty is told what that form had to say, and the refusal is
     * warned about beside the thing it was really about.
     *
     * @param whatHasToSayIt the opening of the sentence for a role that was never given, so that
     *                       somebody who sent an empty box is told which box and not merely that a
     *                       role is one of three words
     * @param refusing       how this caller reports a role it cannot read, so that the warning names
     *                       the invitation or the membership the request was about
     */
    private PotRole roleWrittenAs(String roleAsTyped, String whatHasToSayIt,
                                  Function<String, SharedPotRefused> refusing) {
        String typed = roleAsTyped == null ? "" : roleAsTyped.trim();
        if (typed.isBlank()) {
            throw refusing.apply(whatHasToSayIt + ": " + theRolesThereAre() + ".");
        }
        for (PotRole role : PotRole.values()) {
            if (role.name().equalsIgnoreCase(typed)) {
                return role;
            }
        }
        throw refusing.apply(
                "A role is " + theRolesThereAre() + ", and \"" + typed + "\" is not one of them.");
    }

    /** The roles a pot has, as a sentence says them: "OWNER, CONTRIBUTOR or VIEWER". */
    private static String theRolesThereAre() {
        PotRole[] roles = PotRole.values();
        StringBuilder said = new StringBuilder();
        for (int at = 0; at < roles.length; at++) {
            said.append(at == 0 ? "" : at == roles.length - 1 ? " or " : ", ").append(roles[at]);
        }
        return said.toString();
    }

    /** One invitation as the rest of the application reads it, with both people named. */
    private APotInvitation asAnInvitation(PotInvitation invitation, String potName,
                                          Map<Long, String> namesById) {
        return new APotInvitation(invitation.getId(), invitation.getSharedPotId(), potName,
                invitation.getInvitedByCustomerId(), nameOf(invitation.getInvitedByCustomerId(), namesById),
                invitation.getInvitedCustomerId(), nameOf(invitation.getInvitedCustomerId(), namesById),
                invitation.getRole(), invitation.getState(), invitation.getInvitedAt(),
                invitation.getAnsweredAt());
    }

    /**
     * What each of the pots named in a list of invitations is called, in one query however many rows
     * name them — the same round-trip arithmetic a customer's list of pots does.
     */
    private Map<Long, String> namesOfThePots(List<PotInvitation> invitationsToName) {
        List<Long> potIds = invitationsToName.stream()
                .map(PotInvitation::getSharedPotId)
                .distinct()
                .toList();
        return pots.findByIdInOrderByIdAsc(potIds).stream()
                .collect(Collectors.toMap(SharedPot::getId, SharedPot::getName));
    }

    /**
     * A name cache that already holds the people this call went and found, so that answering with the
     * invitation does not ask Accounts for somebody it has in its hand.
     */
    private static Map<Long, String> namesOf(Customer... known) {
        Map<Long, String> namesById = new HashMap<>();
        for (Customer customer : known) {
            namesById.put(customer.getId(), customer.getName());
        }
        return namesById;
    }

    /**
     * Every refusal says why in the log as well as to whoever asked, because only one of the two is
     * kept: the reason reaches the person at the keyboard and nowhere else, and the log is the only
     * copy anybody reviewing this afterwards can read.
     *
     * <p>The name is logged as it was given rather than as it was stored, because a refusal about a
     * name has nothing stored — and the text is the whole of the story for somebody tracing "it would
     * not let me open it".
     */
    private SharedPotRefused refusing(long customerId, String nameAsGiven, SharedPotRefused.Kind kind,
                                      String reason) {
        log.warn("shared pot rejected customerId={} nameAsGiven={} kind={} reason={}",
                customerId, nameAsGiven, kind, reason);
        return new SharedPotRefused(kind, reason);
    }

    /** The same for a refusal about a pot that was named rather than about one being opened. */
    private SharedPotRefused refusingAbout(long potId, SharedPotRefused.Kind kind, String reason) {
        log.warn("shared pot rejected potId={} kind={} reason={}", potId, kind, reason);
        return new SharedPotRefused(kind, reason);
    }

    /**
     * And for a refusal about an invitation, which carries two more things worth having in the log:
     * which invitation, when there is one yet, and who was asking.
     *
     * <p>The same {@code shared pot rejected} opening as the other two, so that one grep finds every
     * refusal this module has ever made whichever of its rules turned the request down.
     */
    private SharedPotRefused refusingAnInvitation(long potId, Long invitationId, Long customerId,
                                                  SharedPotRefused.Kind kind, String reason) {
        log.warn("shared pot rejected potId={} invitationId={} customerId={} kind={} reason={}",
                potId, invitationId, customerId, kind, reason);
        return new SharedPotRefused(kind, reason);
    }

    /**
     * And for a refusal about somebody's membership of a pot, which carries the two people a role
     * change is about: whose role it would have been, and who was trying to change it.
     *
     * <p>Both, because they are different in every interesting case and the same in the one that
     * matters most — the last owner stepping down is one person appearing twice, and a log line that
     * named only one of them would leave a reader unable to tell that refusal from a refusal about
     * somebody else.
     *
     * <p>The same {@code shared pot rejected} opening as the others, so that one grep finds every
     * refusal this module has ever made whichever of its rules turned the request down.
     */
    private SharedPotRefused refusingAMembership(long potId, Long memberCustomerId, Long customerId,
                                                 SharedPotRefused.Kind kind, String reason) {
        log.warn("shared pot rejected potId={} memberCustomerId={} customerId={} kind={} reason={}",
                potId, memberCustomerId, customerId, kind, reason);
        return new SharedPotRefused(kind, reason);
    }

    // ------------------------------------------------------------- taking money out of a pot

    /**
     * Proposes taking an amount out of the pot and returning it to one of the proposer's own current
     * accounts, and answers with the proposal that is now waiting.
     *
     * <p><strong>Nothing moves, as long as there is somebody to ask.</strong> Not a cent leaves the
     * pot, no deposit is drawn down, no member's contribution changes and nobody's points are
     * touched: a proposal is a question put to the people whose money is in the pot, and proposing
     * is not deciding. Story 47, and the assertion that makes the gate worth having.
     *
     * <p><strong>And it goes through at once when there is nobody to ask.</strong> A proposer who is
     * the only member with money in the pot needs nobody's approval, so there is no approval left
     * for the withdrawal to wait for and it happens here — story 50, and the ordinary rule rather
     * than an exception to it: the last required approval is what moves the money, and a proposal
     * that required none was born with none outstanding. The two paragraphs are one sentence read
     * against an empty list and a full one.
     *
     * <p><strong>Whose approval it needs is the point of the feature.</strong> A withdrawal draws a
     * savings account's oldest deposits down first, whoever paid them in, so one taken out of a
     * shared pot spends other members' euros — and because what somebody has ever earned points on
     * does not fall when what they hold does, it costs them the points on their next contributions
     * as well. Every other member who still has money in the pot therefore gets a veto;
     * {@link WhoseAssentAWithdrawalNeeds} is that rule and argues it out, and a proposer who is the
     * only member with money in the pot is answered with an empty list, because there is nobody
     * else's money to spend.
     *
     * <p>Six things are refused and nothing else: a pot nobody has heard of, a customer nobody has
     * heard of, a member whose role may not pay in — which is a viewer, and a stranger to the pot in
     * the same words — a destination current account that is not the proposer's own, a figure that
     * is not an amount of money, and an amount larger than the pot holds. The order is the one the
     * rest of this module sets and the one {@code WithdrawalsService} sets for the same act: who is
     * asking, then the accounts, then the amount. A figure is beside the point when there is no
     * movement to make it about.
     *
     * <p>The amount is checked against the pot again when the last approval lands, for the reason
     * the spec gives: a settlement in between can leave a proposal that was affordable when it was
     * made asking for more than is there. That second weighing is {@link #approve}'s, not this
     * one's — except when nobody's assent is needed, where there is no interval between the two and
     * the proposal is made and paid out in the one request.
     *
     * @param amount            what they asked to take out, already read as a figure, because
     *                          whether the characters that arrived are a number at all is a question
     *                          about the request and is answered at the endpoint
     * @param toCurrentAccountId one of the proposer's own current accounts, which is checked to be
     *                          theirs: a proposal naming somebody else's account would be a way of
     *                          sending a pot's money to a stranger with the other members' blessing
     * @throws SharedPotRefused if the proposal is one of the six this module will not make
     */
    @Transactional
    public AWithdrawalProposal proposeAWithdrawal(long potId, long proposingCustomerId,
                                                  BigDecimal amount, long toCurrentAccountId) {
        log.debug("pot withdrawal proposal asked for potId={} proposingCustomerId={} amount={} "
                        + "toCurrentAccountId={}",
                potId, proposingCustomerId, amount.toPlainString(), toCurrentAccountId);
        SharedPot pot = potWithId(potId);
        Customer proposing = accounts.customerWith(proposingCustomerId)
                .orElseThrow(() -> refusingAProposal(potId, null, proposingCustomerId,
                        NO_SUCH_CUSTOMER, AccountsService.noSuchCustomer(proposingCustomerId)));
        insistOnAMemberWhoMayPayIn(potId, proposingCustomerId, "propose taking money out of");
        insistThePotIsOpen(pot, proposingCustomerId, "propose taking money out of");
        insistTheDestinationIsTheirOwn(potId, proposing, toCurrentAccountId);
        insistItIsAnAmountOfMoney(potId, proposingCustomerId, amount);

        // One read of the deposits for both figures. What the pot holds is what its members still
        // hold in it, summed, so asking twice would be two answers to one question — and a deposit
        // committing between the two reads would be a proposal refused against one balance and
        // reported against another.
        Map<Long, BigDecimal> stillTheirs = whatIsStillEachMembersIn(pot.getSavingsAccountId());
        BigDecimal inThePot = whatThePotHolds(stillTheirs);
        if (amount.compareTo(inThePot) > 0) {
            throw refusingAProposal(potId, null, proposingCustomerId, MORE_THAN_THE_POT_HOLDS,
                    "This pot holds EUR " + AmountOfMoney.asMoney(inThePot) + ", and a withdrawal "
                            + "of EUR " + AmountOfMoney.asMoney(amount) + " is more than that.");
        }

        // One moment, read from the application's clock and truncated the way a pot's, an
        // invitation's and a deposit's are, so that a proposal made against a wound clock is dated
        // where the trainer wound it to.
        Instant proposedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        WithdrawalProposal proposal = proposals.save(WithdrawalProposal.of(potId,
                proposingCustomerId, amount, toCurrentAccountId, proposedAt));
        Map<Long, String> namesById = namesOf(proposing);
        List<APotMember> members = membersIn(potId, namesById);
        List<APotMember> mustAnswer =
                whoseAssentItNeeds(proposal, members, stillTheirs, Set.of());
        // One line per proposal made, with everything that decided it: the figure, where it would
        // go, what the pot held when it was weighed, and how many people now have to answer. A
        // proposal that went through without anybody approving it is explainable from this line
        // alone — the count is nought — and so is one that is still waiting a week later.
        log.info("pot withdrawal proposed proposalId={} potId={} proposedByCustomerId={} amount={} "
                        + "toCurrentAccountId={} potHolds={} assentNeededFrom={} proposedAt={}",
                proposal.getId(), potId, proposingCustomerId, AmountOfMoney.asMoney(amount),
                toCurrentAccountId, AmountOfMoney.asMoney(inThePot), mustAnswer.size(), proposedAt);
        // Nobody's assent is needed, so there is nobody to wait for and nothing to wait for them
        // with: the money goes now. Story 50, and it is the ordinary rule rather than a special
        // case — the last approval moves the money, and when none is needed the proposal is born
        // with none outstanding. A proposal left sitting at PROPOSED with an empty list of people
        // to ask would be a gate with nobody on either side of it, waiting for a click that no
        // screen could ever offer.
        if (mustAnswer.isEmpty()) {
            executeTheWithdrawal(pot, proposal, stillTheirs, proposedAt);
        }
        return asAProposal(proposal, pot.getName(), mustAnswer, List.of(), namesById);
    }

    /**
     * Every proposal the pot has ever had, in any state, oldest first — who asked, when, for how
     * much, where it would go, and what became of it.
     *
     * <p>Answered ones as well as waiting ones, because this is a record rather than an inbox, and
     * for the reason the pot's list of invitations gives: a proposal that vanished when it was
     * rejected would leave the pot with no trace that anybody ever said no.
     *
     * <p>Whose assent each waiting proposal still needs is worked out here rather than stored, from
     * the membership and the deposits as they stand now. That is the spec's own rule — the set is
     * computed when the proposal is made and recomputed when the last approval lands, because a
     * settlement in between changes who has money in the pot — and deriving it on every read is what
     * makes those two the same answer rather than two lists to keep agreed.
     *
     * <p>Not yet asked who is doing the looking, for the reason the pot's own reads are not: whether
     * a stranger may read a pot is one question, and it will be answered for every read of one at
     * once rather than differently here.
     *
     * @throws SharedPotRefused if no pot answers to that identifier
     */
    @Transactional(readOnly = true)
    public List<AWithdrawalProposal> withdrawalProposalsOf(long potId) {
        SharedPot pot = potWithId(potId);
        List<WithdrawalProposal> made = proposals.findBySharedPotIdOrderByProposedAtAscIdAsc(potId);
        if (made.isEmpty()) {
            log.debug("pot withdrawal proposals listed potId={} proposals=0", potId);
            return List.of();
        }
        // The membership and the deposits once for the whole list rather than once per proposal:
        // every proposal in it is weighed against the same pot at the same instant, which is also
        // what stops two rows of one page disagreeing about who has money in it.
        Map<Long, String> namesById = new HashMap<>();
        List<APotMember> members = membersIn(potId, namesById);
        Map<Long, BigDecimal> stillTheirs = whatIsStillEachMembersIn(pot.getSavingsAccountId());
        Map<Long, List<AnAnswerToAProposal>> answered = whoAnsweredEachOf(made, members);
        List<AWithdrawalProposal> listed = made.stream()
                .map(proposal -> asAProposal(proposal, pot.getName(),
                        whoseAssentItNeeds(proposal, members, stillTheirs,
                                whoHasAnswered(answered.get(proposal.getId()))),
                        answered.getOrDefault(proposal.getId(), List.of()), namesById))
                .toList();
        log.debug("pot withdrawal proposals listed potId={} proposals={} members={} potHolds={}",
                potId, listed.size(), members.size(),
                AmountOfMoney.asMoney(whatThePotHolds(stillTheirs)));
        return listed;
    }

    /**
     * The member who proposed a withdrawal takes it back, and answers with the proposal as it now
     * reads.
     *
     * <p>Theirs alone. A change of mind about your own request needs nobody else's involvement,
     * which is why this is not a rejection: a rejection is somebody protecting their own euros and
     * it says so in the pot's record, and the two being told apart is the difference between "I
     * thought better of it" and "you may not have my money".
     *
     * <p>Only while it is waiting, for the reason an invitation may only be revoked while it is:
     * a proposal that has been approved has already moved money, and taking one back afterwards
     * would be a withdrawal undone by a button rather than by another movement of money.
     *
     * <p>Refused for four things: a pot nobody has heard of, a proposal this pot never had, somebody
     * who is not the member who made it, and a proposal that is no longer waiting. Whose proposal it
     * is comes before what has become of it, the order this module uses everywhere: being told that
     * somebody else's proposal was already approved is being told something about somebody else.
     *
     * @throws SharedPotRefused if the proposal is not this customer's to take back, or not waiting
     */
    @Transactional
    public AWithdrawalProposal takeBackTheProposal(long potId, long proposalId, long customerId) {
        SharedPot pot = potWithId(potId);
        WithdrawalProposal proposal = proposalWithId(potId, proposalId);
        Customer takingBack = accounts.customerWith(customerId)
                .orElseThrow(() -> refusingAProposal(potId, proposalId, customerId, NO_SUCH_CUSTOMER,
                        AccountsService.noSuchCustomer(customerId)));
        if (proposal.getProposedByCustomerId() != takingBack.getId()) {
            throw refusingAProposal(potId, proposalId, customerId, NOT_ALLOWED,
                    "Only the member who proposed a withdrawal may take it back, and "
                            + takingBack.getName() + " is who you are signed in as.");
        }
        insistThePotIsOpen(pot, customerId, "take a withdrawal proposal back in");
        insistTheProposalIsStillWaiting(proposal, customerId);
        Instant takenBackAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        proposal.closedAs(ProposalState.WITHDRAWN, takenBackAt);
        // One line per proposal taken back, with the figure that is no longer being asked for: a
        // member who says they never withdrew their request, and a pot whose waiting list emptied
        // without anybody approving anything, are both explainable from this line alone.
        log.info("pot withdrawal proposal taken back proposalId={} potId={} customerId={} amount={} "
                        + "takenBackAt={}", proposal.getId(), potId, customerId,
                AmountOfMoney.asMoney(proposal.getAmount()), takenBackAt);
        return asAProposal(proposal, pot.getName(), List.of(),
                answersTo(proposal, membersIn(potId, namesOf(takingBack))), namesOf(takingBack));
    }

    /**
     * A member whose money is at stake approves the proposal — and if they are the last one who had
     * to, the money leaves the pot in the same breath.
     *
     * <p><strong>There is no separate step.</strong> Story 51: the withdrawal happens the moment the
     * last required approval arrives, inside this transaction, so there is nothing for anybody to
     * remember and no window in which a proposal everybody has agreed to sits waiting for a click
     * nobody knows is theirs. The pot's balance falls, the proposer's current account rises and the
     * proposal reads {@code APPROVED}, all together or not at all.
     *
     * <p><strong>Whose assent it needs is worked out again here</strong>, from the membership and
     * the deposits as they stand at this instant rather than as they stood when the proposal was
     * made. That is the spec's own rule and it matters: a member settled out of the pot in between
     * has nothing left at stake, and a list written down at proposal time would go on demanding an
     * answer from somebody with no euros in it — a withdrawal everybody concerned had approved,
     * waiting for ever on somebody who had gone.
     *
     * <p>Five things are refused: a pot or a proposal nobody has heard of, a customer nobody has
     * heard of, the proposer answering their own proposal, a proposal that is no longer waiting, a
     * member who has already answered this one, and a member whose assent is not needed at all. The
     * order is the module's own — who is asking, then what they are answering, then whether they may
     * — and the sixth refusal, that the pot can no longer afford it, comes last of all because it is
     * about the money rather than about the request.
     *
     * <p><strong>It commits what it has written even when it refuses</strong>, which is the one
     * place in this module that is true and is why the transaction says so. The last refusal closes
     * the proposal as {@code REJECTED} before raising itself, because a proposal the pot cannot pay
     * for is finished rather than merely unlucky, and the spec asks for it to be rejected rather than
     * left hanging. Rolled back, that closure would vanish and the refusal would be raised again by
     * every member who tried, for ever. The refusals before it write nothing at all, so committing
     * is committing nothing.
     *
     * @throws SharedPotRefused if the approval is one this module will not take, or if the pot no
     *                          longer holds what the proposal asked for
     */
    @Transactional(noRollbackFor = SharedPotRefused.class)
    public AWithdrawalProposal approve(long potId, long proposalId, long customerId) {
        return answer(potId, proposalId, customerId, ProposalAnswer.APPROVED);
    }

    /**
     * A member whose money is at stake says no, which ends the proposal there and then. Nothing
     * moves.
     *
     * <p>Story 52: one rejection is enough. The euros are partly this member's, and a veto that had
     * to be repeated — or that waited to see whether anybody else objected — would be no veto at
     * all. Everybody else who had still to answer is no longer being asked anything, which is what
     * the empty list of outstanding approvals beside a closed proposal says.
     *
     * <p>Refused for exactly what an approval is refused for, because it is the same proposal being
     * answered by the same person: an answer is an answer, whichever way it goes. It cannot meet the
     * last refusal of the six, because a rejection spends nothing and so has nothing to be weighed
     * against what the pot holds.
     *
     * <p>Deliberately not the same act as the proposer taking it back. That distinction is
     * {@link #takeBackTheProposal}'s and the pot's record keeps both, because "I thought better of
     * it" and "you may not have my money" are different things to have happened.
     *
     * @throws SharedPotRefused if the rejection is one this module will not take
     */
    @Transactional(noRollbackFor = SharedPotRefused.class)
    public AWithdrawalProposal reject(long potId, long proposalId, long customerId) {
        return answer(potId, proposalId, customerId, ProposalAnswer.REJECTED);
    }

    /**
     * One answer to one proposal, whichever way it goes.
     *
     * <p>Approving and rejecting differ in what happens afterwards and in nothing before it: the
     * same proposal, the same people entitled to answer it, the same rule that each of them answers
     * once. Two methods that each restated those would be two places for them to drift apart, and
     * the one that drifted would be the one that let somebody's euros go. The same line
     * {@link #answer(long, long, long, InvitationState)} draws for an invitation.
     */
    private AWithdrawalProposal answer(long potId, long proposalId, long answeringCustomerId,
                                       ProposalAnswer way) {
        log.debug("pot withdrawal proposal answer asked for potId={} proposalId={} customerId={} "
                + "answer={}", potId, proposalId, answeringCustomerId, way);
        SharedPot pot = potWithId(potId);
        WithdrawalProposal proposal = proposalWithId(potId, proposalId);
        Customer answering = accounts.customerWith(answeringCustomerId)
                .orElseThrow(() -> refusingAProposal(potId, proposalId, answeringCustomerId,
                        NO_SUCH_CUSTOMER, AccountsService.noSuchCustomer(answeringCustomerId)));
        // Story 55, and the reason it comes first: proposing is asking, and a proposal that counted
        // its own author's assent would be a vote for yourself. Said before anything about the
        // state of the proposal, because being told that your own proposal is closed is a different
        // conversation from being told you do not get to answer it.
        if (proposal.getProposedByCustomerId() == answering.getId()) {
            throw refusingAProposal(potId, proposalId, answeringCustomerId, NOT_ALLOWED,
                    "A member does not answer their own proposal, and " + answering.getName()
                            + " is who proposed this one. Take it back instead if you have thought "
                            + "better of it.");
        }
        // Before the proposal's own state, because the pot being over is the larger piece of
        // news: closing ended every proposal that was still waiting, so "that proposal is
        // REJECTED" would be true and would send somebody looking for the member who rejected it.
        insistThePotIsOpen(pot, answeringCustomerId, "answer a withdrawal proposal in");
        insistTheProposalIsStillWaiting(proposal, answeringCustomerId);

        List<PotMembership> inThePot = memberships.findBySharedPotIdOrderByJoinedAtAscIdAsc(potId);
        Map<Long, String> namesById = namesOf(answering);
        List<APotMember> members = inThePot.stream()
                .map(membership -> asAMember(membership, namesById))
                .toList();
        List<AnAnswerToAProposal> already = asAnswers(
                answers.findByWithdrawalProposalIdOrderByAnsweredAtAscIdAsc(proposalId), members,
                namesById);
        // Before whether their assent is needed, and that order is the whole of story 54: a member
        // who has answered is no longer on the list of people still to answer, so asked the other
        // way round they would be told they have no money in the pot — which is not what happened
        // and not what they should go and check.
        already.stream()
                .filter(answer -> answer.customerId() == answering.getId().longValue())
                .findFirst()
                .ifPresent(answer -> {
                    throw refusingAProposal(potId, proposalId, answeringCustomerId,
                            ALREADY_ANSWERED, answering.getName() + " has already "
                                    + howTheyAnswered(answer.answer()) + " that proposal, and a "
                                    + "member answers a proposal once.");
                });

        // The deposits read once, here, for every question this method asks of the money: whose
        // assent is still outstanding, and — if this answer is the last one — whether the pot still
        // holds what was asked for. Asked twice they could be answered either side of somebody
        // else's deposit, and the withdrawal would be approved against one balance and taken out of
        // another.
        Map<Long, BigDecimal> stillTheirs = whatIsStillEachMembersIn(pot.getSavingsAccountId());
        List<APotMember> outstanding =
                whoseAssentItNeeds(proposal, members, stillTheirs, whoHasAnswered(already));
        if (outstanding.stream().noneMatch(member -> member.customerId() == answering.getId().longValue())) {
            throw refusingAProposal(potId, proposalId, answeringCustomerId, NOT_ALLOWED,
                    "Only the members whose money is still in this pot answer a withdrawal from "
                            + "it, and none of " + answering.getName() + "'s is.");
        }

        Instant answeredAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        answers.save(WithdrawalProposalAnswer.of(proposalId, answering.getId(), way, answeredAt));
        List<AnAnswerToAProposal> answeredBy = new ArrayList<>(already);
        answeredBy.add(new AnAnswerToAProposal(answering.getId(), answering.getName(),
                roleOf(answering.getId(), members), way, answeredAt));
        List<APotMember> stillToAnswer = outstanding.stream()
                .filter(member -> member.customerId() != answering.getId().longValue())
                .toList();
        // One line per answer, with everything that decided it: which way, whose money was at stake
        // when they were asked, and how many people are left. A withdrawal that moved is explainable
        // from the line whose count is nought, and a proposal still waiting a week later from the
        // line whose count is not.
        log.info("pot withdrawal proposal answered proposalId={} potId={} customerId={} answer={} "
                        + "amount={} stillToAnswer={} answeredAt={}",
                proposalId, potId, answering.getId(), way,
                AmountOfMoney.asMoney(proposal.getAmount()), stillToAnswer.size(), answeredAt);

        if (way == ProposalAnswer.REJECTED) {
            proposal.closedAs(ProposalState.REJECTED, answeredAt);
            log.info("pot withdrawal proposal rejected proposalId={} potId={} "
                            + "rejectedByCustomerId={} amount={} rejectedAt={}",
                    proposalId, potId, answering.getId(),
                    AmountOfMoney.asMoney(proposal.getAmount()), answeredAt);
            return asAProposal(proposal, pot.getName(), List.of(), answeredBy, namesById);
        }
        if (stillToAnswer.isEmpty()) {
            executeTheWithdrawal(pot, proposal, stillTheirs, answeredAt);
        }
        return asAProposal(proposal, pot.getName(), stillToAnswer, answeredBy, namesById);
    }

    /**
     * Takes the money out of the pot and closes the proposal as approved, in one transaction with
     * the answer that was the last one needed.
     *
     * <p><strong>The pot is weighed a second time, and this is where story 57 is kept.</strong> The
     * figure was checked when the proposal was made, but a proposal waits, and a pot's balance moves
     * underneath it: another proposal can go through, a member can be settled out. So what the pot
     * holds now decides, and a proposal it can no longer cover is rejected here with a sentence
     * saying what is actually in it — rather than left waiting for an approval it already has, or
     * failing somewhere further down with a stack trace about a savings account.
     *
     * <p>That rejection is written and kept. The refusal is raised afterwards so that whoever
     * clicked approve reads the sentence, and the transaction is told not to roll back on it, which
     * is argued out on {@link #approve}.
     *
     * <p>The movement itself is the Deposits module's, through the one door it opens for a pot:
     * oldest deposits first whoever paid them in, an allocation per deposit the money came out of,
     * and the proposer's own current account credited. Which euros leave is not this module's rule
     * and never has been — it is the rule this whole feature exists to put a gate in front of.
     */
    private void executeTheWithdrawal(SharedPot pot, WithdrawalProposal proposal,
                                      Map<Long, BigDecimal> stillTheirs, Instant at) {
        BigDecimal amount = proposal.getAmount();
        BigDecimal inThePot = whatThePotHolds(stillTheirs);
        // The inputs behind the second weighing, which a reader cannot redo in their head: what was
        // asked for when, and what is there now.
        log.debug("pot withdrawal weighed again as it goes through proposalId={} potId={} "
                        + "savingsAccountId={} amount={} potHolds={} proposedAt={}",
                proposal.getId(), pot.getId(), pot.getSavingsAccountId(),
                AmountOfMoney.asMoney(amount), AmountOfMoney.asMoney(inThePot),
                proposal.getProposedAt());
        if (amount.compareTo(inThePot) > 0) {
            proposal.closedAs(ProposalState.REJECTED, at);
            throw refusingAProposal(pot.getId(), proposal.getId(), null, MORE_THAN_THE_POT_HOLDS,
                    "This pot now holds EUR " + AmountOfMoney.asMoney(inThePot) + ", and the "
                            + "withdrawal of EUR " + AmountOfMoney.asMoney(amount) + " it was "
                            + "approved for is more than that. The proposal has been rejected.");
        }
        RecordedWithdrawal made = withdrawals.takeOutOfAPotWhatItsMembersApprovedOf(
                pot.getSavingsAccountId(), proposal.getDestinationCurrentAccountId(), amount);
        proposal.closedAs(ProposalState.APPROVED, at);
        // One line per withdrawal a pot actually made, with everything that decided it — including
        // how many of the pot's deposits it drew down, because that count is the other members'
        // money and the whole reason anybody had to approve it. What it took out of which deposit
        // is the draw-down's own line, a level below.
        log.info("pot withdrawal executed proposalId={} potId={} withdrawalId={} "
                        + "savingsAccountId={} toCurrentAccountId={} amount={} potHeld={} "
                        + "potNowHolds={} depositsDrawnDown={} executedAt={}",
                proposal.getId(), pot.getId(), made.id(), pot.getSavingsAccountId(),
                proposal.getDestinationCurrentAccountId(), AmountOfMoney.asMoney(amount),
                AmountOfMoney.asMoney(inThePot), AmountOfMoney.asMoney(inThePot.subtract(amount)),
                made.allocations().size(), at);
    }

    /** Which way somebody went, as a sentence about them says it: "has already approved". */
    private static String howTheyAnswered(ProposalAnswer answer) {
        return answer == ProposalAnswer.APPROVED ? "approved" : "rejected";
    }

    /** What this customer is to the pot, or nothing at all if they no longer belong to it. */
    private static PotRole roleOf(long customerId, List<APotMember> members) {
        return members.stream()
                .filter(member -> member.customerId() == customerId)
                .map(APotMember::role)
                .findFirst()
                .orElse(null);
    }

    /** The identifiers of everybody who has answered, which is what the outstanding list subtracts. */
    private static Set<Long> whoHasAnswered(List<AnAnswerToAProposal> answered) {
        if (answered == null || answered.isEmpty()) {
            return Set.of();
        }
        return answered.stream().map(AnAnswerToAProposal::customerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Who has answered one proposal, in the order they answered. */
    private List<AnAnswerToAProposal> answersTo(WithdrawalProposal proposal,
                                                List<APotMember> members) {
        return asAnswers(
                answers.findByWithdrawalProposalIdOrderByAnsweredAtAscIdAsc(proposal.getId()),
                members, new HashMap<>());
    }

    /**
     * Who has answered each of a pot's proposals, in one query however many of them there are — the
     * same round-trip arithmetic the pot's members and their names already do.
     */
    private Map<Long, List<AnAnswerToAProposal>> whoAnsweredEachOf(List<WithdrawalProposal> made,
                                                                   List<APotMember> members) {
        Map<Long, String> namesById = new HashMap<>();
        List<Long> proposalIds = made.stream().map(WithdrawalProposal::getId).toList();
        Map<Long, List<AnAnswerToAProposal>> byProposal = new LinkedHashMap<>();
        for (WithdrawalProposalAnswer answer
                : answers.findByWithdrawalProposalIdInOrderByAnsweredAtAscIdAsc(proposalIds)) {
            byProposal.computeIfAbsent(answer.getWithdrawalProposalId(), id -> new ArrayList<>())
                    .add(asAnAnswer(answer, members, namesById));
        }
        return byProposal;
    }

    private List<AnAnswerToAProposal> asAnswers(List<WithdrawalProposalAnswer> given,
                                                List<APotMember> members,
                                                Map<Long, String> namesById) {
        return given.stream().map(answer -> asAnAnswer(answer, members, namesById)).toList();
    }

    /**
     * One answer as the rest of the application reads it, with the member named.
     *
     * <p>The role is read off the pot's membership as it stands now, and is absent for somebody who
     * has since left: an answer outlives the membership that gave it, and the pot's record has to go
     * on saying who decided what. The name is asked of Accounts, which knows every customer whether
     * or not they are still in this pot.
     */
    private AnAnswerToAProposal asAnAnswer(WithdrawalProposalAnswer answer,
                                           List<APotMember> members, Map<Long, String> namesById) {
        return new AnAnswerToAProposal(answer.getCustomerId(),
                nameOf(answer.getCustomerId(), namesById), roleOf(answer.getCustomerId(), members),
                answer.getAnswer(), answer.getAnsweredAt());
    }

    /**
     * Insists that the customer is a member of the pot whose role may put money into it, which is
     * the same ladder a contribution is judged by.
     *
     * <p>Asked of {@link PotRole#mayPayIn} rather than compared here, so that the people who may
     * propose taking money out and the people who may pay it in cannot drift apart. That they are
     * the same set is the rule: a viewer watches, and somebody who cannot put a euro in has no
     * business proposing that everybody else's go out.
     *
     * <p>Somebody who is not in the pot at all is refused in the same words as a viewer, for the
     * reason {@link #insistOnAnOwner} gives: telling a stranger that they are not a member would be
     * telling them there is a pot here to be a member of.
     *
     * @param whatTheyTried the act, as a verb phrase the sentence can be built around
     */
    private void insistOnAMemberWhoMayPayIn(long potId, long customerId, String whatTheyTried) {
        PotRole role = memberships.findBySharedPotIdAndCustomerId(potId, customerId)
                .map(PotMembership::getRole)
                .orElse(null);
        log.debug("pot role read for a decision potId={} customerId={} role={} tryingTo={}",
                potId, customerId, role, whatTheyTried);
        if (role == null || !role.mayPayIn()) {
            throw refusingAProposal(potId, null, customerId, NOT_ALLOWED,
                    "Only an owner or a contributor may " + whatTheyTried + " this pot.");
        }
    }

    /**
     * Insists that the money would come back to a current account the proposer holds.
     *
     * <p>An account nobody holds and an account somebody else holds are refused in one sentence,
     * deliberately. Whose an account is is not this proposer's business — {@code WithdrawalsService}
     * draws the same line, and saying "that is Bram's" would tell them something about a customer
     * who is not them — and the useful half of the sentence is the same either way: name one of your
     * own.
     */
    private void insistTheDestinationIsTheirOwn(long potId, Customer proposing,
                                                long toCurrentAccountId) {
        Long heldBy = accounts.currentAccountWith(toCurrentAccountId)
                .map(WhatACurrentAccountHolds::customerId)
                .orElse(null);
        log.debug("pot withdrawal destination read for a decision potId={} proposingCustomerId={} "
                        + "toCurrentAccountId={} heldByCustomerId={}",
                potId, proposing.getId(), toCurrentAccountId, heldBy);
        if (proposing.getId().equals(heldBy)) {
            return;
        }
        throw refusingAProposal(potId, null, proposing.getId(), AGAINST_THE_RULES,
                "A withdrawal from a shared pot comes back to one of the proposer's own current "
                        + "accounts, and current account " + toCurrentAccountId + " is not one of "
                        + proposing.getName() + "'s.");
    }

    /**
     * Insists that the figure is an amount of money to move.
     *
     * <p>What counts as one is {@link AmountOfMoney}'s answer, in the words a withdrawal of the same
     * figure from a personal account comes back with — because it is the same objection about the
     * same act, and a member who has met both should not have to work out that they are the same
     * sentence reworded.
     */
    private void insistItIsAnAmountOfMoney(long potId, long customerId, BigDecimal amount) {
        AmountOfMoney.whyItIsNotOne("withdrawal", amount).ifPresent(reason -> {
            throw refusingAProposal(potId, null, customerId, AGAINST_THE_RULES, reason);
        });
    }

    /**
     * Insists that the proposal is still waiting, which is the whole of "a proposal is answered
     * once".
     *
     * <p>The sentence names the state it is in rather than saying only that it is closed, for the
     * reason an invitation's does: approved, rejected and taken back are three different pieces of
     * news, and the member reading it does something different about each.
     */
    private void insistTheProposalIsStillWaiting(WithdrawalProposal proposal, long customerId) {
        if (!proposal.getState().isStillWaiting()) {
            throw refusingAProposal(proposal.getSharedPotId(), proposal.getId(), customerId,
                    THE_PROPOSAL_IS_CLOSED, "That proposal is " + proposal.getState()
                            + ", and a proposal is answered once.");
        }
    }

    /**
     * One proposal of one pot, or the refusal that says there is no such thing.
     *
     * <p>The pot is part of what identifies it, for the reason an invitation's lookup gives: a
     * proposal answered under a pot that never made it is answering something that is not there, and
     * saying so is better than quietly acting on a proposal belonging to somewhere else.
     */
    private WithdrawalProposal proposalWithId(long potId, long proposalId) {
        return proposals.findById(proposalId)
                .filter(proposal -> proposal.getSharedPotId() == potId)
                .orElseThrow(() -> refusingAProposal(potId, proposalId, null, NO_SUCH_PROPOSAL,
                        "There is no withdrawal proposal " + proposalId + " to shared pot "
                                + potId + "."));
    }

    /**
     * Whose approval this proposal is still waiting on, and why — the one decision in this module
     * that a reader cannot redo in their head from the other lines in the log.
     *
     * <p>The rule is {@link WhoseAssentAWithdrawalNeeds} and it lives there rather than here,
     * because it is a function of its arguments and is worth being able to state a table of cases
     * against. What is here is the reading: every member, the role they hold, what is still theirs in
     * this pot, and whether that put them on the list. A refused approval and an accepted one are
     * both explainable from this line, and so is the complaint that somebody was never asked.
     *
     * <p>A proposal that is no longer waiting is waiting on nobody, whatever anybody holds. Saying
     * that a withdrawn proposal still needs Bram's approval would be reporting a question that is no
     * longer being asked, and the state beside it is what says why the list is empty.
     *
     * <p><strong>Less whoever has already answered</strong>, which is the half this slice adds. The
     * rule is about whose euros are at stake and says nothing about who has spoken, so a member who
     * has approved would go on appearing in it for ever and the withdrawal would wait for an answer
     * it already had. Subtracting them here rather than inside the rule is deliberate: the rule is a
     * function of the pot's membership and its money, and answers are a fact about this proposal.
     *
     * <p>Which makes the emptying of this list the thing that moves the money. The approval that
     * empties it is the last one, and it is the request the withdrawal happens in.
     */
    private List<APotMember> whoseAssentItNeeds(WithdrawalProposal proposal,
                                                List<APotMember> members,
                                                Map<Long, BigDecimal> stillTheirs,
                                                Set<Long> whoHasAnswered) {
        if (!proposal.getState().isStillWaiting()) {
            return List.of();
        }
        List<APotMember> mustAnswer = WhoseAssentAWithdrawalNeeds.of(
                proposal.getProposedByCustomerId(), members, stillTheirs).stream()
                .filter(member -> !whoHasAnswered.contains(member.customerId()))
                .toList();
        // Guarded, because rendering it is work — a line per member, two of them figures to format —
        // and the string is thrown away when the application runs at INFO. The same reasoning the
        // withdrawal's own draw-down line uses.
        if (log.isDebugEnabled()) {
            List<String> weighed = new ArrayList<>();
            for (APotMember member : members) {
                weighed.add("[customerId=" + member.customerId() + " role=" + member.role()
                        + " stillTheirs=" + AmountOfMoney.asMoney(whatIsStill(member, stillTheirs))
                        + " hasAnswered=" + whoHasAnswered.contains(member.customerId())
                        + " asked=" + mustAnswer.contains(member) + "]");
            }
            log.debug("whose assent a pot withdrawal needs proposalId={} potId={} "
                            + "proposedByCustomerId={} amount={} assentNeededFrom={} weighed={}",
                    proposal.getId(), proposal.getSharedPotId(),
                    proposal.getProposedByCustomerId(), AmountOfMoney.asMoney(proposal.getAmount()),
                    mustAnswer.size(), String.join(" ", weighed));
        }
        return mustAnswer;
    }

    /**
     * What is still each member's in one pot: the sum of what remains of their own deposits into its
     * savings account, by customer.
     *
     * <p>Per pot and not per customer, which is the distinction the whole approval gate turns on.
     * {@code DepositsService.stillSavedBy} answers about everything a customer holds across every
     * savings account of theirs, and a member with five thousand euros of their own savings
     * elsewhere would look like somebody with a great deal at stake in a pot they have never paid
     * into. What is at stake here is what is in <em>this</em> account.
     *
     * <p>A member who never paid in is simply absent, and so is one whose deposits have all been
     * drawn down: neither has anything left in the pot, which is the same answer read two ways.
     * {@link WhoseAssentAWithdrawalNeeds} treats an absence as nought for exactly that reason.
     */
    private Map<Long, BigDecimal> whatIsStillEachMembersIn(long savingsAccountId) {
        Map<Long, BigDecimal> stillTheirs = new LinkedHashMap<>();
        for (DepositStillHoldingMoney deposit : deposits.depositsStillHoldingMoneyIn(savingsAccountId)) {
            stillTheirs.merge(deposit.customerId(), deposit.remainingAmount(), BigDecimal::add);
        }
        return stillTheirs;
    }

    /**
     * What the pot holds, which is what its members still hold in it, added up.
     *
     * <p>The same figure {@code DepositsService.moneyBalanceOf} answers, worked out from the reading
     * this method's caller already has rather than fetched a second time. Two reads a moment apart
     * is how a proposal comes to be weighed against one balance and reported beside another.
     */
    private static BigDecimal whatThePotHolds(Map<Long, BigDecimal> stillTheirs) {
        return stillTheirs.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** What is still one member's, with an absence read as nought for the reason above. */
    private static BigDecimal whatIsStill(APotMember member, Map<Long, BigDecimal> stillTheirs) {
        return stillTheirs.getOrDefault(member.customerId(), BigDecimal.ZERO);
    }

    /**
     * One proposal as the rest of the application reads it, with the proposer named and the amount
     * quoted to the cent.
     *
     * <p>Quoted here because a figure that has been through SQLite comes back as 500.5 — it has no
     * decimal type and keeps an amount as a float — and a page that had to decide how many places
     * money has would be deciding it again.
     *
     * <p>The answers are handed in rather than fetched, for the reason the members are: a list of
     * proposals reads them once for the whole page, and a single proposal's caller already has the
     * answer it has just written. Fetching per proposal would be a query per row and, worse, a row
     * read back before the answer beside it had been flushed.
     *
     * <p>An empty {@code answeredBy} is an answer and not an absence: nobody has answered yet — which
     * on a proposal that needs nobody's assent is the permanent truth, since it went through without
     * anybody being asked.
     */
    private AWithdrawalProposal asAProposal(WithdrawalProposal proposal, String potName,
                                            List<APotMember> whoseAssentItNeeds,
                                            List<AnAnswerToAProposal> answeredBy,
                                            Map<Long, String> namesById) {
        return new AWithdrawalProposal(proposal.getId(), proposal.getSharedPotId(), potName,
                proposal.getProposedByCustomerId(),
                nameOf(proposal.getProposedByCustomerId(), namesById),
                AmountOfMoney.quotedToTheCent(proposal.getAmount()),
                proposal.getDestinationCurrentAccountId(), proposal.getState(),
                proposal.getProposedAt(), proposal.getClosedAt(), whoseAssentItNeeds, answeredBy);
    }

    /**
     * And for a refusal about a proposal, which carries which proposal — when there is one yet — and
     * who was asking.
     *
     * <p>The same {@code shared pot rejected} opening as the other three, so that one grep finds
     * every refusal this module has ever made whichever of its rules turned the request down.
     */
    private SharedPotRefused refusingAProposal(long potId, Long proposalId, Long customerId,
                                               SharedPotRefused.Kind kind, String reason) {
        log.warn("shared pot rejected potId={} proposalId={} customerId={} kind={} reason={}",
                potId, proposalId, customerId, kind, reason);
        return new SharedPotRefused(kind, reason);
    }

    // --------------------------------------------------- who has put what into the pot

    /**
     * The pot, once it is settled that whoever is asking is entitled to read it.
     *
     * <p>Every member may, whatever their role — the same line
     * {@link WhatAPotIsSavingForIsItsOwnersDecision#insistTheyMayRead} draws in front of a pot's
     * goals, in the same words, because it is the same rule about the same pot. A viewer watching
     * two other people save is being shown the money they are saving; somebody in no pot at all is
     * being shown nothing, and is told what would have been needed rather than told that there is no
     * pot here, because naming the gap would tell whoever is guessing that there is a pot to be a
     * member of.
     *
     * <p><strong>Public, because the reads it guards are assembled outside this module.</strong> A
     * pot's money movements are the Deposits module's ledger and this module owns no money, so the
     * list is put together in the web layer beside the pot's balance — but who may see it is a rule
     * about membership and it is answered here, the same way {@link #insistOnAnOwner} is answered
     * here for the goal endpoints. The pot comes back rather than nothing, because every caller of
     * this needs the savings account the pot holds and a second read to fetch it would be a second
     * chance to be refused differently.
     *
     * <p>The customer is boxed, and a request that names nobody is nobody: not a member, and refused
     * in the same words a stranger is. The reads this guards carry whoever is asking as a query
     * parameter, which is a thing a page can forget to send.
     *
     * <p>Which pot before who is asking, deliberately. Somebody who typed the wrong identifier is
     * told the pot is not there rather than that they are not in it, which is the more useful of the
     * two sentences and the only one they can act on.
     *
     * @param whatTheyAsked the act, as a verb phrase the sentence is built around, so that one rule
     *                      answers for the contributions and for the ledger without the sentence
     *                      being written twice
     * @throws SharedPotRefused with {@code NO_SUCH_POT} if there is no such pot, or
     *                          {@code NOT_ALLOWED}, which answers 403, if the customer asking is not
     *                          in it
     */
    @Transactional(readOnly = true)
    public ASharedPot thePotAMemberMayRead(long potId, Long customerId, String whatTheyAsked) {
        ASharedPot pot = potWith(potId);
        // Read off the membership already in hand rather than asked for again: the pot was just
        // read with every member on it, and a second query would be a second answer to the same
        // question a moment later.
        PotRole role = customerId == null ? null : pot.members().stream()
                .filter(member -> customerId.equals(member.customerId()))
                .map(APotMember::role)
                .findFirst()
                .orElse(null);
        log.debug("pot role read for a decision potId={} customerId={} role={} tryingTo={}",
                potId, customerId, role, whatTheyAsked);
        if (role == null) {
            throw refusingAMembership(potId, customerId, customerId, NOT_ALLOWED,
                    "Only a member may " + whatTheyAsked + " this pot.");
        }
        return pot;
    }

    /**
     * What every member has put into the pot: paid in altogether, still theirs, and what their
     * contributions to this pot have earned them.
     *
     * <p><strong>A read over rows that are already written, and no new record anywhere.</strong> A
     * deposit has carried the customer whose saving it was since long before pots existed, so who
     * paid what into a pot's savings account and how much of each payment is still there are both
     * questions the Deposits module can already answer. A contribution table would be a second copy
     * of those two figures, kept by the module that owns neither, and it would be the copy that
     * eventually disagreed with the money.
     *
     * <p><strong>The figures add up, and that is the point of the screen.</strong> What is still
     * each member's, added across the members, is what the pot holds — to the cent, because both are
     * summed from the very same deposits. Two people saving together can check the arithmetic rather
     * than trusting it, which is the whole difference between a shared pot and a spreadsheet one of
     * them keeps.
     *
     * <p><strong>Paid in does not move when money leaves.</strong> An approved withdrawal draws the
     * pot's oldest deposits down first whoever paid them in, so a member can watch what is still
     * theirs fall without having asked for anything — and what they paid in stays exactly what they
     * paid in. Reporting one figure for both would either forget what somebody carried or claim they
     * still hold money that has gone; it is also the arithmetic the approval gate exists to protect
     * people from, so the screen that shows it is where it has to be legible.
     *
     * <p><strong>Every member, including the ones with nothing in it.</strong> A viewer, a
     * contributor who has not got round to it and a contributor whose euros have all been drawn down
     * all read as noughts, which is an answer; being absent from the list would read as not being in
     * the pot.
     *
     * <p><strong>And everybody who ever paid into it, member or not — which is story 26.</strong>
     * The record of what a departed member paid in has to survive their leaving, or the pot's
     * history stops adding up on the one screen that exists to show it adding up: somebody leaves,
     * their deposits stay in the account with their name on them, and a list built from the current
     * membership alone would quietly drop the euros they carried. So the rows are the pot's members
     * followed by everybody else whose contributions are in its account, and a departed contributor
     * reads back with everything they paid in and everything they earned by it.
     *
     * <p>A departed member is told apart by having <strong>no role</strong> rather than by a word
     * like "left", and that is the same choice {@link AnAnswerToAProposal} already makes for an
     * answer whose author has gone: a role is what somebody <em>is</em> to the pot, and somebody who
     * is not in it is not anything to it. Inventing a fourth role would put a word into the
     * vocabulary that no rule reads, that no invitation can grant and that
     * {@link PotRole#mayPayIn} would have to have an opinion about. An absence says exactly what is
     * true — they were in this pot, they paid this in, and they are not in it now.
     *
     * <p>Their {@code stillTheirs} is very nearly always nought, because leaving settles it; it is
     * not asserted to be, because the figure is read from the same deposits as everybody else's and
     * a line here claiming it must be nought would be the one place this screen stopped reporting
     * and started deciding.
     *
     * <p>Points are the depositor's and are never pooled: this is what <em>this</em> pot's
     * contributions earned each member, asked of the ledger that owns them. They do not fall when
     * the euros that earned them leave, because earning happened once.
     *
     * @param customerId who is asking, as the request named them, which may be nobody at all
     * @throws SharedPotRefused if there is no such pot, or if whoever is asking is not a member of it
     */
    @Transactional(readOnly = true)
    public List<AContribution> contributionsTo(long potId, Long customerId) {
        ASharedPot pot = thePotAMemberMayRead(potId, customerId, "see who has paid what into");
        Map<Long, BigDecimal> stillTheirs = whatIsStillEachMembersIn(pot.savingsAccountId());
        // A LinkedHashMap, and the order matters: the deposits arrive oldest first, so this is
        // every person who has ever paid into the pot in the order they first did — which is what
        // puts a departed contributor in a sensible place on the list below.
        Map<Long, BigDecimal> paidIn = new LinkedHashMap<>();
        Map<Long, Long> pointsEarned = new LinkedHashMap<>();
        for (DepositPaidIn deposit : deposits.depositsPaidInto(pot.savingsAccountId())) {
            paidIn.merge(deposit.customerId(), deposit.amount(), BigDecimal::add);
            pointsEarned.merge(deposit.customerId(), deposit.pointsEarned(), Long::sum);
        }
        List<AContribution> contributions = new ArrayList<>();
        Set<Long> alreadyOnTheList = new LinkedHashSet<>();
        // The pot's members first, in the order they joined it, which puts the owner who opened it
        // at the top and is the order this screen has always read in.
        for (APotMember member : pot.members()) {
            alreadyOnTheList.add(member.customerId());
            contributions.add(new AContribution(member.customerId(), member.name(), member.role(),
                    AmountOfMoney.quotedToTheCent(
                            paidIn.getOrDefault(member.customerId(), BigDecimal.ZERO)),
                    AmountOfMoney.quotedToTheCent(whatIsStill(member, stillTheirs)),
                    pointsEarned.getOrDefault(member.customerId(), 0L)));
        }
        // Then everybody else whose money is in the pot's history — story 26. They come after the
        // members rather than in among them, so that a page which has always shown the membership
        // in joining order goes on doing so and the people who have gone read as the tail of the
        // list they are.
        Map<Long, String> namesById = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> theirs : paidIn.entrySet()) {
            Long departedCustomerId = theirs.getKey();
            if (!alreadyOnTheList.add(departedCustomerId)) {
                continue;
            }
            contributions.add(new AContribution(departedCustomerId,
                    nameOf(departedCustomerId, namesById), null,
                    AmountOfMoney.quotedToTheCent(theirs.getValue()),
                    AmountOfMoney.quotedToTheCent(
                            stillTheirs.getOrDefault(departedCustomerId, BigDecimal.ZERO)),
                    pointsEarned.getOrDefault(departedCustomerId, 0L)));
        }

        BigDecimal thePotHolds = whatThePotHolds(stillTheirs);
        // The members' own, and pointedly not every row: the rows below them are the people who
        // have left, and counting their euros in here would close the arithmetic by definition and
        // make the check beneath it incapable of ever firing.
        BigDecimal itsMembersHold = contributions.stream()
                .filter(contribution -> contribution.role() != null)
                .map(AContribution::stillTheirs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // The invariant this screen is read for, checked where it is worked out rather than left to
        // whoever is looking at it. Money in the pot that no current member's deposits account for
        // is not a refusal — the figures are still every member's own and still add up to
        // themselves — but it means somebody who has left still has euros in here, and the log is
        // the only place that can be noticed before somebody's arithmetic does not close.
        if (thePotHolds.compareTo(itsMembersHold) != 0) {
            log.warn("pot holds money no member's deposits account for potId={} "
                            + "savingsAccountId={} potHolds={} itsMembersHold={} "
                            + "reason=somebody who is no longer a member still has money in the pot",
                    potId, pot.savingsAccountId(), AmountOfMoney.asMoney(thePotHolds),
                    AmountOfMoney.asMoney(itsMembersHold));
        }
        // Guarded, because rendering it is work — a line per member, two of them figures to format —
        // and the string is thrown away when the application runs at INFO. The same reasoning the
        // assent decision's line uses, and the same reason for saying it at all: what each member
        // paid in, what is still theirs and what it earned them cannot be worked back out of any
        // other line in this log.
        if (log.isDebugEnabled()) {
            List<String> perMember = new ArrayList<>();
            for (AContribution contribution : contributions) {
                perMember.add("[customerId=" + contribution.customerId()
                        + " role=" + contribution.role()
                        + " paidInAltogether=" + AmountOfMoney.asMoney(contribution.paidInAltogether())
                        + " stillTheirs=" + AmountOfMoney.asMoney(contribution.stillTheirs())
                        + " pointsEarned=" + contribution.pointsEarned() + "]");
            }
            log.debug("pot contributions read potId={} savingsAccountId={} readByCustomerId={} "
                            + "members={} potHolds={} itsMembersHold={} perMember={}",
                    potId, pot.savingsAccountId(), customerId, contributions.size(),
                    AmountOfMoney.asMoney(thePotHolds), AmountOfMoney.asMoney(itsMembersHold),
                    String.join(" ", perMember));
        }
        return contributions;
    }
}
