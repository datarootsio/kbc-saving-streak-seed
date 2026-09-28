package io.dataroots.savingstreak.goals;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The declared weekly capacity of one savings account, and nothing wider.
 *
 * <p>One read, by savings account, and it answers with nothing at all when the customer has not said
 * what they can save. Nothing here invents a figure for an account that has none: a default would be
 * the application putting words in the customer's mouth and then presenting them back as their own
 * plan, and the place that would happen is a method on a repository nobody reviews twice.
 *
 * <p>Package-private, like the row it reads.
 */
interface TheWeeklyAmountThatCanBeSavedRepository extends JpaRepository<TheWeeklyAmountThatCanBeSaved, Long> {

    /** What that account's holder said they can put away in a week, or nothing if they never have. */
    Optional<TheWeeklyAmountThatCanBeSaved> findBySavingsAccountId(long savingsAccountId);
}
