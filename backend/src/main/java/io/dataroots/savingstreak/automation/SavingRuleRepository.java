package io.dataroots.savingstreak.automation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The saving rules standing against an account, and the only store this module has.
 *
 * <p>Every read about one account's rules is scoped by that account, without exception, and that is
 * the whole of how one savings account's rules stay invisible to another. A read by rule identifier
 * alone would answer for somebody else's account whenever a caller guessed a number, and "the caller
 * would not do that" is not a rule — this is.
 *
 * <p>The one read that is not scoped by a single account is {@link #countBySavingsAccountIdInAndState},
 * and it is scoped by a set of them: the limit on how many rules may stand at once is per customer
 * rather than per account, so it has to be asked over every savings account that customer holds.
 * Which accounts those are is Accounts' answer, handed in.
 *
 * <p>Package-private, like the row it reads: what this module keeps, and how, is nobody else's
 * business.
 */
interface SavingRuleRepository extends JpaRepository<SavingRule, Long> {

    /**
     * The account's rules in a given state, oldest first.
     *
     * <p>Oldest first because that is the order the customer wrote them in, and it is the order
     * money will be moved in when two rules fall on one day — a page that listed them any other way
     * would be showing a different sequence from the one the night runs.
     */
    List<SavingRule> findBySavingsAccountIdAndStateOrderByIdAsc(long savingsAccountId, RuleState state);

    /**
     * The account's rules in any of a set of states, oldest first, and for the same reason.
     *
     * <p>What "the rules standing against this account" means is two states rather than one: a rule
     * its holder has paused is still an instruction they have left standing, still on their page and
     * still counting against the ten they may have at once. Which states those are is
     * {@link RuleState#theOnesStillStanding}'s answer and not this interface's — a list written out
     * at the call site would be a place a fourth state could be quietly left out of.
     */
    List<SavingRule> findBySavingsAccountIdAndStateInOrderByIdAsc(long savingsAccountId,
                                                                  Collection<RuleState> states);

    /**
     * Every rule in that state, whoever holds it, in the order they were left standing.
     *
     * <p>The one read that is not scoped by an account at all, and the only caller is the nightly
     * run: a job that fires what is due fires what is due for everybody, and asking it to walk the
     * accounts first would be asking it to do the same work through a slower door.
     *
     * <p>Oldest first, and here that is not a presentation choice but the firing order. Two rules
     * falling due on one morning move money in the order their customer wrote them — deterministic,
     * reconstructable from the log, and needing no priority field anybody would have to maintain.
     */
    List<SavingRule> findByStateOrderByIdAsc(RuleState state);

    /**
     * One rule on one account, whatever state it is in, or nothing at all.
     *
     * <p>Both halves of the key, always. A rule named by a customer who holds a different account is
     * a rule that is not there as far as that customer is concerned, and answering it with the row
     * would be answering a question about somebody else's saving.
     */
    Optional<SavingRule> findByIdAndSavingsAccountId(long id, long savingsAccountId);

    /**
     * How many rules in any of those states stand across a set of savings accounts — the customer's
     * own.
     *
     * <p>Counted rather than fetched, because the only thing the limit needs is the number. A rule
     * that has been ended is not counted by any caller: it is not on the page and it fires nothing,
     * so it costs neither of the things the limit protects. <strong>A paused rule is</strong>, and
     * that is not an oversight: it is on the page and its holder means to have it back, so a
     * customer could otherwise hold twenty rules by pausing ten of them.
     */
    long countBySavingsAccountIdInAndStateIn(Collection<Long> savingsAccountIds,
                                             Collection<RuleState> states);
}
