package io.dataroots.savingstreak.accounts;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.TheCurrentAccountView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A current account answers for itself: what is in it, who holds it, and what its holder says lands
 * in it every month.
 *
 * <p>User story 31, and the read behind the screen that story asks for. Until this existed a current
 * account could only be read as one row of its holder's directory of accounts, so a page about one
 * account had to fetch every account somebody holds and pick a row out of it — and the income beside
 * that balance had to be fetched from somewhere else again. This is the one read a page of its own
 * makes, and the two halves of it come back describing the same account at the same moment.
 *
 * <p>Asserted through the API and nothing lower, like everything else in this application: the
 * balance is checked against what the customer's own directory reports for the same account, because
 * two endpoints disagreeing about one account's money is exactly the failure worth catching.
 */
class ACurrentAccountReportsWhatIsInItAndWhatLandsInItApiTest extends ApiIntegrationTest {

    private static final String A_SALARY = "2500.00";

    private AnAccountWithAnIncome account;

    @BeforeEach
    void anAccountOfThisTestsOwn() {
        account = new AnAccountWithAnIncome(http, "the-account-itself");
    }

    @Test
    void the_account_reports_the_money_in_it_and_the_iban_its_holder_knows_it_by() {
        TheCurrentAccountView read = account.theAccount();

        assertThat(read.currentAccountId()).isEqualTo(account.id());
        assertThat(read.balance())
                .as("the account's own resource and the customer's directory are two reads of one "
                        + "balance, and a page drawn from either has to show the same figure")
                .isEqualByComparingTo(account.balance());
        assertThat(read.iban())
                .as("what the customer knows the account by, so a page naming it does not have to "
                        + "invent a name for it")
                .isNotBlank();
        assertThat(read.customerName())
                .as("who holds it, so the page can say so without a second request")
                .isNotBlank();
    }

    @Test
    void an_account_nobody_has_declared_an_income_against_says_so_in_the_same_read() {
        TheCurrentAccountView read = account.theAccount();

        assertThat(read.income().declared())
                .as("nobody has said what lands in it, which is a different sentence from somebody "
                        + "having said that nothing does — and the page draws different words for "
                        + "each")
                .isFalse();
        assertThat(read.income().amount()).isNull();
        assertThat(read.income().dayOfMonth()).isNull();
        assertThat(read.income().currentAccountId()).isEqualTo(account.id());
    }

    @Test
    void a_declaration_made_against_the_account_is_part_of_what_the_account_reports() {
        account.declares("25", A_SALARY);

        TheCurrentAccountView read = account.theAccount();

        assertThat(read.income().declared()).isTrue();
        assertThat(read.income().dayOfMonth()).isEqualTo(25);
        assertThat(read.income().amount()).isEqualByComparingTo(A_SALARY);
        assertThat(read.income().nextPayday())
                .as("when the money next arrives, worked out by the application rather than by "
                        + "whoever draws it")
                .isNotNull();
    }

    @Test
    void withdrawing_the_declaration_leaves_the_account_reporting_the_money_and_no_income() {
        account.declares("25", A_SALARY);

        account.withdrawsTheDeclaration();

        TheCurrentAccountView read = account.theAccount();
        assertThat(read.income().declared())
                .as("the page that has just cleared the boxes reads the same nothing back")
                .isFalse();
        assertThat(read.balance())
                .as("a declaration and its withdrawal are sentences about the future; neither moves "
                        + "a cent")
                .isEqualByComparingTo(account.balance());
    }

    @Test
    void an_account_nobody_has_heard_of_is_refused_in_words_rather_than_drawn_empty() {
        long noSuchAccount = account.anIdNoCurrentAccountHas();

        ResponseEntity<JsonNode> refused = account.tryToRead(noSuchAccount);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(refused.getBody().get("detail").asText())
                .as("the reason travels in detail, which is the field the page shows unchanged — a "
                        + "page drawn with an empty balance instead would be stating a falsehood "
                        + "about somebody's money")
                .contains("no current account " + noSuchAccount);
    }
}
