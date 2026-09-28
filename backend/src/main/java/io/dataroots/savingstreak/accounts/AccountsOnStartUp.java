package io.dataroots.savingstreak.accounts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Makes this module's two records unique before the application serves anything: the record of
 * credited income over the current account and the day it was due, and the record of what became of
 * a recurring bill over the bill and the day it was due.
 *
 * <p>Which is the whole of what makes the two nightly jobs idempotent by construction rather than by
 * care. Both jobs also check in Java, so a second run credits and debits nothing without this; but
 * the check and the guarantee are different things. Two runs of a job at the same moment would both
 * read the same empty set of already-settled days, and only a rule the database keeps stops both of
 * them from moving money — which on these two tables means euros, not points.
 *
 * <p>Here rather than on the entities because the entities cannot say it. The schema is generated
 * from the entity model ({@code ddl-auto=update}) against SQLite, and that dialect writes a
 * composite unique clause nowhere: declared as a unique constraint, or as an index marked unique,
 * the table is created without it and the only statement that reaches the database is a drop that
 * does nothing. A {@code create unique index} is a statement SQLite does accept, so this is where
 * the guarantee comes from — and it is a step a reviewer can watch happen in a DEBUG start-up log
 * rather than an annotation they would have to take on trust. {@code AutomationOnStartUp},
 * {@code LoyaltyOnStartUp}, {@code GoalsOnStartUp} and {@code NotificationsOnStartUp} are the same
 * step for the same reason.
 *
 * <p>The same shape as those: it runs on every start, and all but the first do nothing.
 *
 * <p><strong>Both indexes are made here rather than each beside its own job</strong>, because they
 * are the same statement about the same module's rows and a second component would be a second place
 * to remember. The bills' index could not be created until there was a table to name, which is why
 * it arrives with the slice that first writes a row into it: a {@code create unique index} against a
 * table Hibernate has not generated fails the start it was meant to protect.
 *
 * <p><strong>And one repair, which is not an index.</strong> The column saying which night an
 * arrear fell short on arrived after the rows did, and a still-outstanding row written before it
 * carries null — invisible to the read the notifications module counts an account's pile from,
 * while the still-owed panel lists it all the same. The two must not disagree, the figure is
 * recoverable for exactly the rows that are still unpaid, and this is the moment to recover it:
 * before the web server binds its port and before any raiser runs.
 *
 * <p><strong>And one relaxation, which is not an index either.</strong> A savings account may now be
 * held by nobody — a shared pot holds its own — and every database written before that was generated
 * with the holder required. Schema generation adds columns and never relaxes one, so the first pot
 * opened against such a file would be refused by the database rather than opened, in a constraint
 * nobody could read. The column is therefore relaxed here, where it is a statement with its reason
 * beside it rather than a surprise in a stack trace, and before the web server can be asked to open
 * anything. {@code PointsOnStartUp} fills the same kind of gap for the same reason: this repository
 * has no migrations yet, and until it does the start is where a schema catches up.
 */
@Component
class AccountsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(AccountsOnStartUp.class);

    private final IncomePaidRepository paid;

    private final BillOccurrenceRepository settled;

    private final SavingsAccountRepository savingsAccounts;

    AccountsOnStartUp(IncomePaidRepository paid, BillOccurrenceRepository settled,
                      SavingsAccountRepository savingsAccounts) {
        this.paid = paid;
        this.settled = settled;
        this.savingsAccounts = savingsAccounts;
    }

    /**
     * Called once every bean exists and before the context finishes refreshing — which is before the
     * web server binds its port and before the scheduler can fire either nightly job, so no payday
     * can be credited and no bill taken against a record that is not yet unique, and no pot opened
     * against a table that will not have its account.
     */
    @Override
    public void afterSingletonsInstantiated() {
        makeCreditedIncomeUniquePerPayday();
        makeSettledBillsUniquePerDueDate();
        fillInTheNightEachOutstandingArrearFellShortOn();
        letASavingsAccountBeHeldByNobody();
    }

    private void makeCreditedIncomeUniquePerPayday() {
        if (paid.theRecordIsAlreadyUniquePerPayday() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("the record of credited income is already unique per current account and "
                    + "payday index=one_income_per_account_per_payday");
            return;
        }
        paid.makeTheRecordUniquePerPayday();
        log.info("the record of credited income was made unique per current account and payday "
                + "index=one_income_per_account_per_payday columns=[current_account_id, due_on]");
    }

    private void makeSettledBillsUniquePerDueDate() {
        if (settled.theRecordIsAlreadyUniquePerDueDate() > 0) {
            log.debug("the record of what became of the bills is already unique per bill and due "
                    + "date index=one_settlement_per_bill_per_due_date");
            return;
        }
        settled.makeTheRecordUniquePerDueDate();
        log.info("the record of what became of the bills was made unique per bill and due date "
                + "index=one_settlement_per_bill_per_due_date "
                + "columns=[recurring_bill_id, due_on]");
    }

    /**
     * Gives every arrear still outstanding the night it fell short on, which is what the
     * notifications module's raiser reads an account's pile off.
     *
     * <p>Here rather than in that module because it is this module's table and this module's column,
     * and the raiser cannot be the thing that repairs the record it judges. The repository says why
     * the figure is recoverable and why only for a row that is still unpaid.
     *
     * <p>A database written entirely by this version has nothing to fill in and says so at DEBUG,
     * like the two checks above it.
     */
    private void fillInTheNightEachOutstandingArrearFellShortOn() {
        int filledIn = settled.fillInTheNightEachOutstandingArrearFellShortOn();
        if (filledIn == 0) {
            log.debug("every outstanding arrear already says which night it fell short on "
                    + "column=bill_occurrence.fell_short_at");
            return;
        }
        // A business event, and one a reviewer looking at an older database will want to see: these
        // arrears were invisible to the piling-up count until this line ran.
        log.info("outstanding arrears were given the night they fell short on arrears={} "
                        + "column=bill_occurrence.fell_short_at filledFrom=settled_at "
                        + "reason=these dates were recorded before the column existed, and an "
                        + "unpaid row's settled moment is the night it was presented and refused",
                filledIn);
    }

    /**
     * Relaxes the holder on the savings accounts, so that an account can belong to something that is
     * not a person.
     *
     * <p>Four statements and a question, because SQLite has no way to relax a column: the table is
     * rebuilt with the holder optional, every account is copied across as it stands, the old table
     * goes and the new one takes its name. The repository carries each statement with the reason it
     * is that statement, and it is asked first whether any of it is needed at all — a database this
     * release created already has an optional holder and says so at DEBUG, like the checks above.
     *
     * <p>Nothing about any account changes. Every row keeps the customer it had; what changes is
     * that a row may now be written without one, which is what a shared pot's account is.
     */
    private void letASavingsAccountBeHeldByNobody() {
        if (savingsAccounts.aSavingsAccountStillHasToHaveAHolder() == 0) {
            log.debug("a savings account may already be held by nobody column=savings_account.customer_id");
            return;
        }
        savingsAccounts.clearAwayAnyHalfFinishedRebuild();
        savingsAccounts.openATableWhoseHolderIsOptional();
        int carriedOver = savingsAccounts.copyEverySavingsAccountAcross();
        savingsAccounts.takeAwayTheTableThatRequiredAHolder();
        savingsAccounts.letTheRebuiltTableTakeItsName();
        // A business event, and one a reviewer looking at an older database will want to see: this
        // is the line that says the file was rebuilt and how many accounts went through it.
        log.info("a savings account may now be held by nobody column=savings_account.customer_id "
                        + "accountsCarriedOver={} reason=a shared pot holds its own account, and "
                        + "the holder was required of every account before pots existed",
                carriedOver);
    }
}
