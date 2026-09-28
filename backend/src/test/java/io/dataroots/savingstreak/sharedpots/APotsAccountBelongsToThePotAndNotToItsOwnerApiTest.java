package io.dataroots.savingstreak.sharedpots;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ScheduledJobView;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A pot's savings account is held by nobody, and that is what keeps opening a pot from changing a
 * single thing about the personal screens of the customer who opened it.
 *
 * <p>The account is not in their list of accounts, its money is not in their total, and it is not in
 * anybody else's either. None of that is arranged by a screen leaving it out: a savings account
 * belongs to a customer through one link, that link is empty on a pot's account, and every question
 * this application asks about what somebody holds is asked through it. This class is what says so
 * from outside.
 *
 * <p>And the other half of the same fact: asking who holds such an account answers "nobody" rather
 * than falling over. Half a dozen places in this application ask that question — the nightly sweeps
 * among them — and every one of them already had to have an answer for an account it could not find
 * a holder for. A pot's account arrives at those places as exactly that, which is why the change is
 * a small one.
 *
 * <p>Its own application, for the reason {@link APotIsOpenedByACustomerWhoBecomesItsOwnerApiTest}
 * gives: a savings account nobody holds has no business on the run's shared database.
 */
class APotsAccountBelongsToThePotAndNotToItsOwnerApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-pots-account"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The story this feature was asked for in: my own list of accounts goes on meaning what it meant
     * before I opened a pot. Asserted against the list as it was rather than against a figure this
     * test knows absolutely, because what opening a pot changed is the only thing it can honestly
     * claim.
     */
    @Test
    void the_pots_account_is_not_among_the_accounts_of_the_customer_who_opened_it() {
        List<Long> hers = app.savingsAccountsOf(ANKE);

        SharedPotView pot = app.openAPot(ANKE, "Kitchen");

        assertThat(pot.savingsAccountId())
                .as("a pot's account belongs to the pot, and the pot belongs to nobody alone")
                .isNotIn(hers);
        assertThat(app.savingsAccountsOf(ANKE))
                .as("and her own accounts are the ones she had")
                .isEqualTo(hers);
    }

    /** Nor in anybody else's, which is the same sentence read from the other side. */
    @Test
    void nor_among_any_other_customers_accounts() {
        List<Long> his = app.savingsAccountsOf(BRAM);

        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");

        assertThat(pot.savingsAccountId()).isNotIn(his);
        assertThat(app.savingsAccountsOf(BRAM)).isEqualTo(his);
    }

    /**
     * And its money is not counted as theirs. The pot holds nothing today — there is no way to pay
     * into one yet — so what this can say is that the figure did not move and that the account it
     * would move is not one of the accounts the figure is summed over. The euros themselves are the
     * next slice's to put in.
     */
    @Test
    void nothing_the_pot_holds_is_counted_as_that_customers_savings() {
        BigDecimal hers = app.stillSavedBy(ANKE);

        SharedPotView pot = app.openAPot(ANKE, "New bike");

        assertThat(app.stillSavedBy(ANKE))
                .as("opening a pot moves nothing of her own")
                .isEqualByComparingTo(hers);
        assertThat(app.savingsAccountsOf(ANKE))
                .as("and the total is summed over accounts that do not include the pot's")
                .doesNotContain(pot.savingsAccountId());
    }

    /**
     * Asking who holds a pot's account answers "nobody", and the personal account page says so as
     * the refusal it has for an account it cannot report on. A sentence somebody could read out
     * loud, in the shape every other refusal in this application arrives in — and emphatically not a
     * stack trace, which is what a link that could not be empty would have produced.
     */
    @Test
    void asking_who_holds_a_pots_account_answers_nobody_rather_than_falling_over() {
        SharedPotView pot = app.openAPot(ANKE, "Winter tyres");

        ResponseEntity<JsonNode> asked = app.tryToReadTheSavingsAccount(pot.savingsAccountId());

        assertThat(asked.getStatusCode())
                .as("an account nobody holds is not a page this application can draw, and it says so")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(asked.getStatusCode().is5xxServerError())
                .as("and it is an answer rather than a failure, which is the whole of this test")
                .isFalse();
        assertThat(asked.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + asked.getBody())
                .isTrue();
    }

    /**
     * And the sweeps that walk every savings account in the application walk this one too, and come
     * through it.
     *
     * <p>This is the half of "nobody holds it" that nothing else in the suite would notice. The
     * nightly runs ask who holds each account they pass — to address a notification, to credit a
     * bonus, to know whose money a rule is moving — and an account that answered nothing where they
     * expected somebody is exactly the shape of thing that takes a night down for every customer,
     * not only for the pot. Every job this application says it has is run, rather than the two that
     * happen to walk the accounts today, so that a job added later is covered by this test the day
     * it arrives.
     *
     * <p>What they do about it is each job's own business and is logged where the job runs: the
     * notifications sweep says it passed the account over and why. What is asserted here is only
     * that the night finished.
     */
    @Test
    void the_nightly_sweeps_walk_an_account_nobody_holds_and_come_through_it() {
        app.openAPot(ANKE, "Roof");

        ScheduledJobView[] nightly = app.whatCanBeRun();

        assertThat(nightly)
                .as("a sweep that could not be named would make this test pass by running nothing")
                .isNotEmpty();
        for (ScheduledJobView job : nightly) {
            assertThat(app.runJob(job.name()).name())
                    .as("the night ran with a pot's account among the accounts it walked")
                    .isEqualTo(job.name());
        }
    }
}
