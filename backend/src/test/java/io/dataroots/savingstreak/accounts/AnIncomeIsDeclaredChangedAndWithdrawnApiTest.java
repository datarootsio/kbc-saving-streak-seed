package io.dataroots.savingstreak.accounts;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MonthlyIncomeView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer declares what lands in their current account every month, changes it, and takes it
 * away again.
 *
 * <p>User stories 15 and 17. The account is the one thing in this application whose balance only
 * ever went down, and this is the sentence that turns it into an account somebody lives out of. The
 * three states are all here because the middle one is the trap: a change has to replace the figure
 * rather than leave a second declaration standing beside it, or the customer would be paid twice a
 * month by a mistake they could not see.
 *
 * <p>Read back off the account as well as taken from the answer, because they are two different
 * claims: that the application said yes, and that it wrote down what it said yes to.
 */
class AnIncomeIsDeclaredChangedAndWithdrawnApiTest extends ApiIntegrationTest {

    private static final String A_SALARY = "2500.00";
    private static final String A_DIFFERENT_SALARY = "2750.50";

    private AnAccountWithAnIncome account;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithAnIncome(http, "declared-changed-withdrawn");
    }

    @Test
    void an_account_nobody_has_declared_an_income_against_says_so_rather_than_saying_nought() {
        MonthlyIncomeView income = account.income();

        assertThat(income.declared())
                .as("nobody has said what lands in it, which is a different sentence from somebody "
                        + "having said that nothing does")
                .isFalse();
        assertThat(income.amount()).isNull();
        assertThat(income.dayOfMonth()).isNull();
        assertThat(income.nextPayday())
                .as("no income means nothing coming, rather than a date the account will not honour")
                .isNull();
        assertThat(income.currentAccountId()).isEqualTo(account.id());
    }

    @Test
    void a_declared_income_is_what_the_account_reports_back() {
        MonthlyIncomeView declared = account.declares("25", A_SALARY);

        assertThat(declared.declared()).isTrue();
        assertThat(declared.dayOfMonth()).isEqualTo(25);
        assertThat(declared.amount()).isEqualByComparingTo(A_SALARY);
        assertThat(declared.nextPayday())
                .as("a customer who has said when they are paid is told when the money next arrives")
                .isNotNull();
        assertThat(declared.declaredAt()).isNotNull();

        MonthlyIncomeView readBack = account.income();
        assertThat(readBack.dayOfMonth())
                .as("the answer to declaring it and the answer to asking for it are one figure")
                .isEqualTo(declared.dayOfMonth());
        assertThat(readBack.amount()).isEqualByComparingTo(A_SALARY);
        assertThat(readBack.nextPayday()).isEqualTo(declared.nextPayday());
    }

    @Test
    void changing_a_declaration_replaces_the_figure_rather_than_adding_a_second() {
        account.declares("25", A_SALARY);

        MonthlyIncomeView changed = account.declares("5", A_DIFFERENT_SALARY);

        assertThat(changed.dayOfMonth()).isEqualTo(5);
        assertThat(changed.amount()).isEqualByComparingTo(A_DIFFERENT_SALARY);
        MonthlyIncomeView readBack = account.income();
        assertThat(readBack.dayOfMonth())
                .as("one income per account: the account reports the new figure and only the new "
                        + "figure, because a second declaration would be a second salary")
                .isEqualTo(5);
        assertThat(readBack.amount()).isEqualByComparingTo(A_DIFFERENT_SALARY);
    }

    @Test
    void withdrawing_a_declaration_leaves_the_account_saying_nothing_was_ever_declared() {
        account.declares("25", A_SALARY);

        MonthlyIncomeView withdrawn = account.withdrawsTheDeclaration();

        assertThat(withdrawn.declared()).isFalse();
        assertThat(withdrawn.amount()).isNull();
        assertThat(account.income().declared())
                .as("and it stays withdrawn, which is what stops the nightly job ever finding it")
                .isFalse();
    }

    @Test
    void withdrawing_a_declaration_nobody_made_is_accepted_quietly() {
        MonthlyIncomeView withdrawn = account.withdrawsTheDeclaration();

        assertThat(withdrawn.declared())
                .as("the customer asked for there to be no income on this account, and there is "
                        + "none — pressing a button twice is not a mistake worth a sentence")
                .isFalse();
    }

    @Test
    void declaring_an_income_moves_no_money_by_itself() {
        var before = account.balance();

        account.declares("25", A_SALARY);

        assertThat(account.balance())
                .as("a declaration is a sentence about the future; the money arrives when the job "
                        + "that credits it runs, and not a moment sooner")
                .isEqualByComparingTo(before);
    }
}
