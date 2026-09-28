package io.dataroots.savingstreak.budgets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Makes this module's two rules about what may be written twice — one standing category of each name
 * per current account, and one category per bill — rules the database keeps, before the application
 * serves anything.
 *
 * <p>{@link BudgetsService} also checks in Java, and has to: the check is what produces the sentence
 * the customer reads, naming the category they already have. But the check and the guarantee are
 * different things. Two requests declaring "Groceries" at the same moment would both read the same
 * empty answer and both write, leaving an account with two categories of one name — and every
 * figure this module derives would then have two answers to "what did the groceries cost" with
 * nothing to say which the customer meant. What serialises writers today is a connection pool of
 * one, which is a property of a configuration file rather than a rule about categories.
 *
 * <p>Here rather than on the entity because the entity cannot say it. The schema is generated from
 * the entity model ({@code ddl-auto=update}) against SQLite, and that dialect writes a composite
 * unique clause nowhere: declared as a unique constraint, or as an index marked unique, the table is
 * created without it and the only statement that reaches the database is a drop that does nothing.
 * A {@code create unique index} is a statement SQLite does accept, so this is where the guarantee
 * comes from — and it is a step a reviewer can watch happen in a DEBUG start-up log rather than an
 * annotation they would have to take on trust. {@code AccountsOnStartUp}, {@code GoalsOnStartUp},
 * {@code AutomationOnStartUp}, {@code LoyaltyOnStartUp} and {@code NotificationsOnStartUp} are the
 * same step for the same reason, and this module has one because it has rows of its own.
 *
 * <p><strong>Ended categories are outside it, and that is not an escape hatch.</strong> A category
 * that has been ended is a record of the months it was live for, and the word it was using is free
 * again: a customer who ended Groceries in March and starts filing groceries again in June declares
 * it afresh, and gets a new category with its own identifier rather than the old one resurrected.
 * The index is partial for exactly that reason — it covers the rows that are competing and no
 * others, which is the counterpart of the nulls-are-distinct argument {@code GoalsOnStartUp} makes
 * about abandoned goals.
 *
 * <p>The same shape as the other modules' start-up steps: it runs on every start, and all but the
 * first do nothing.
 *
 * <p>Named for the module rather than for the categories, because it is this module's one start-up
 * step and the budgets, the spends and the bill links that arrive in later slices have their
 * guarantees to make here too. Each one waits for the slice that first writes a row into the table
 * it names: a {@code create unique index} against a table Hibernate has not generated yet fails the
 * start it was meant to protect.
 */
@Component
class BudgetsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(BudgetsOnStartUp.class);

    private final SpendingCategoryRepository categories;

    private final CategorisedBillRepository categorisedBills;

    private final MonthlyBudgetRepository budgets;

    BudgetsOnStartUp(SpendingCategoryRepository categories,
                     CategorisedBillRepository categorisedBills, MonthlyBudgetRepository budgets) {
        this.categories = categories;
        this.categorisedBills = categorisedBills;
        this.budgets = budgets;
    }

    /**
     * Called once every bean exists and before the context finishes refreshing — which is before the
     * web server binds its port, so no category can be declared against a record that is not yet
     * unique.
     */
    @Override
    public void afterSingletonsInstantiated() {
        makeTheStandingCategoriesUniquePerName();
        makeEachBillFiledInAtMostOneCategory();
        makeEachCategoryBudgetedOnce();
    }

    private void makeTheStandingCategoriesUniquePerName() {
        if (categories.theStandingCategoriesAreAlreadyUniquePerName() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("the spending categories are already unique per account and name "
                    + "index=one_standing_category_of_each_name_per_account");
            return;
        }
        categories.makeTheStandingCategoriesUniquePerName();
        log.info("the spending categories were made unique per account and name "
                + "index=one_standing_category_of_each_name_per_account "
                + "columns=[current_account_id, name] over=[categories still standing; an ended "
                + "category frees its name again]");
    }

    /**
     * The second guarantee, and the one the bill labels need: a bill is in one category or in none.
     *
     * <p>The service checks too, and has to — it is what finds the row to move and what produces
     * every sentence a customer reads. But two requests filing one bill at the same moment would
     * both read the same empty answer and both write, and a bill in two categories would be a bill
     * counted twice in the committed half of two different months, with nothing to say which the
     * customer meant. What serialises writers today is a connection pool of one, which is a property
     * of a configuration file rather than a rule about bills.
     *
     * <p>It joins the module's start-up step in this slice rather than in the one that created the
     * class, because this is the slice that first writes a row into the table: a
     * {@code create unique index} against a table Hibernate has not generated yet fails the start it
     * was meant to protect.
     */
    private void makeEachBillFiledInAtMostOneCategory() {
        if (categorisedBills.theBillsAreAlreadyInAtMostOneCategoryEach() > 0) {
            // The ordinary case, and worth a line all the same, for the reason the one above it is:
            // it says the question was asked, so that the guarantee is something a reviewer can
            // confirm on any start rather than only on the first.
            log.debug("the bills are already in at most one category each index=one_category_per_bill");
            return;
        }
        categorisedBills.makeEachBillFiledInAtMostOneCategory();
        log.info("the bills were made to sit in at most one category each index=one_category_per_bill "
                + "columns=[current_account_id, bill_id] over=[every bill that is in a category; a "
                + "bill taken out of every category leaves no row behind]");
    }

    /**
     * The third guarantee, and the one every figure this module derives rests on: a category is held
     * to one figure at a time.
     *
     * <p>The service checks too, and has to — it is what finds the row to supersede and what
     * produces every sentence a customer reads. But two requests declaring a figure on one category
     * at the same moment would both read the same standing row and both write, and a category with
     * two budgets in force would be two answers to "what is this allowed to cost" with nothing to
     * say which the customer meant. Every month derived from it — and, from the next slice, every
     * month of the carry chain after it — would inherit the ambiguity. What serialises writers today
     * is a connection pool of one, which is a property of a configuration file rather than a rule
     * about budgets.
     *
     * <p><strong>The rows that have stood down are outside it, and that is not an escape hatch.</strong>
     * A budget is superseded rather than mutated, so a category legitimately carries as many
     * settled rows as its holder has changed their mind — including two that share a first month,
     * when a figure was named and replaced inside one month and one of the two therefore governs no
     * month at all. An index over the account and the category alone would refuse a customer the
     * second change of mind in a month, which is not a rule anybody meant to make. It is the same
     * partial shape, for the same reason, as the one the standing categories already have.
     *
     * <p>It joins the module's start-up step in this slice rather than in the one that created this
     * class, because this is the slice that first writes a row into the table: a
     * {@code create unique index} against a table Hibernate has not generated yet fails the start it
     * was meant to protect.
     */
    private void makeEachCategoryBudgetedOnce() {
        if (budgets.theCategoriesAreAlreadyBudgetedOnce() > 0) {
            // The ordinary case, and worth a line all the same, for the reason the two above it
            // are: it says the question was asked, so that the guarantee is something a reviewer can
            // confirm on any start rather than only on the first.
            log.debug("the categories are already budgeted once each "
                    + "index=one_standing_budget_per_category");
            return;
        }
        budgets.makeEachCategoryBudgetedOnce();
        log.info("the categories were made to carry one standing budget each "
                + "index=one_standing_budget_per_category columns=[current_account_id, category_id] "
                + "over=[the budgets still standing; a superseded or stopped figure keeps the months "
                + "it governed and competes for nothing]");
    }
}
