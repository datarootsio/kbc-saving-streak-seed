package io.dataroots.savingstreak.budgets;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link BudgetsService}. */
interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, Long> {

    /**
     * The figure in force on one category, if there is one.
     *
     * <p>Scoped by the account as well as the category, the same way a category and a bill are: an
     * identifier somebody guessed answers as a budget that is not there rather than with somebody
     * else's grocery allowance.
     *
     * <p>One row at most, and the guarantee of that is the partial unique index below rather than
     * this signature — see {@link BudgetsOnStartUp}, which says why the two are different things.
     */
    Optional<MonthlyBudget> findByCurrentAccountIdAndCategoryIdAndStateIn(long currentAccountId,
                                                                          long categoryId,
                                                                          List<BudgetState> states);

    /**
     * Every budget this account has ever declared, on every category, oldest first.
     *
     * <p>One read for the whole account rather than one per category, because the read it feeds is a
     * month: twenty categories asked one at a time is twenty round trips to draw one page, and the
     * page asks on every open. Which of them governed the month being read is arithmetic over the
     * two month columns and is done in {@link BudgetsService}, where the rule about it lives.
     *
     * <p>The superseded and the stopped ones are in it, which is the whole reason it is not a read
     * of the standing ones: a month already gone is judged against the figure that stood <em>then</em>,
     * and a query that fetched only what is in force now would quote April this June's allowance.
     *
     * <p>Oldest first, so that when two rows somehow cover one month — which the arithmetic makes
     * impossible and a hand-edited database does not — the answer is stable between two reads rather
     * than whatever order SQLite happened to store them in.
     */
    List<MonthlyBudget> findByCurrentAccountIdOrderByEffectiveFromAscIdAsc(long currentAccountId);

    /**
     * Whether the index that makes one standing budget per category a rule the database keeps —
     * rather than one this application merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, so that the ordinary start — every
     * start after the first — reads one row and does nothing, and so that the start-up step can say
     * whether it did anything.
     */
    @Query(value = "select count(*) from pragma_index_list('monthly_budget') "
            + "where name = 'one_standing_budget_per_category'", nativeQuery = true)
    long theCategoriesAreAlreadyBudgetedOnce();

    /**
     * Makes it so, over the account and the category, for the standing rows only.
     *
     * <p>Created here rather than declared on the entity for the reason
     * {@link SpendingCategoryRepository#makeTheStandingCategoriesUniquePerName} gives at length: the
     * SQLite dialect writes a composite unique clause nowhere, so a constraint on the class would be
     * an intention the database never hears about. A {@code create unique index} is a statement it
     * does accept.
     *
     * <p><strong>Partial, and the shape of the rule is the whole reason.</strong> What must never
     * happen twice is a category with two figures <em>in force</em>: that is two answers to "what is
     * this allowed to cost" with nothing to say which the customer meant, and every month derived
     * from it would inherit the ambiguity. The rows that have stood down are deliberately outside
     * it, because a category legitimately has as many of those as its holder has changed their
     * mind — including two carrying the same first month, when a figure was named and replaced
     * inside one month, one of which governs no month at all. An index over the pair alone would
     * refuse a customer the second change of mind in a month, which is not a rule anybody meant to
     * make.
     *
     * <p>{@code where state = 'STANDING'} is how SQLite says "only the rows that are competing", and
     * it is the counterpart of the partial index the categories already have. The literal is the
     * value {@code @Enumerated(EnumType.STRING)} writes, which is why {@link BudgetState#STANDING}
     * must keep its name for the index to keep its meaning.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_standing_budget_per_category "
            + "on monthly_budget (current_account_id, category_id) where state = 'STANDING'",
            nativeQuery = true)
    void makeEachCategoryBudgetedOnce();
}
