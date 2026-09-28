package io.dataroots.savingstreak.potgoals;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
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
 * The role check in front of a pot's goals applies to an account a pot holds and to nothing else:
 * goals on a personal savings account are exactly what they were before shared pots existed.
 *
 * <p><strong>This is the risk the whole slice carries.</strong> A pot's goals are goals on the
 * pot's savings account, so the rule about who may write them had to be put in front of endpoints
 * that every personal account in this application already uses. Getting it slightly wrong would not
 * break pots — it would quietly break the goals of every customer who has never heard of one, on a
 * path nothing in these tests would otherwise exercise. So it is asserted here directly, from three
 * sides.
 *
 * <p><strong>Nobody named is the request that has always been sent</strong>, and it still works,
 * because {@code ?customerId=} is optional and a personal account never looks at it. Every goals
 * screen in this application sends exactly what it sent yesterday.
 *
 * <p><strong>Somebody named changes nothing either</strong>, and that includes somebody who has
 * nothing to do with the account. That is not an oversight and it is worth saying out loud: this
 * application has no authentication — it trusts the customer it is handed, the way
 * {@code POST /api/customers/{customerId}/gifts} trusts its path variable — and a personal
 * account's goals were never gated on anything. Starting to gate them here would be a different
 * feature, imposed on every customer in the application by a ticket about shared pots. The rule
 * added by this slice is a rule about pots, and this test is what keeps it one.
 *
 * <p>Its own application, so that the accounts it writes goals on are nobody else's.
 */
class NothingAboutAPersonalAccountsGoalsChangesApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    /** Somebody with no connection to either account below, named on requests about them anyway. */
    private static String stranger;

    /** A pot beside the personal accounts, so that both kinds of account exist in one application. */
    private static SharedPotView pot;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-personal-accounts-goals"));
        stranger = app.aCustomerOfItsOwn("a stranger to every account here");
        pot = app.openAPot(ANKE, "A pot beside her own savings");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The whole life of a goal on a personal account, in the requests the goals screen has always
     * sent: not one of them says who is asking, and not one of them is refused.
     */
    @Test
    void a_goal_on_a_personal_account_is_opened_read_changed_and_given_up_with_nobody_named() {
        long herOwn = app.savingsAccountOf(ANKE);

        ResponseEntity<JsonNode> opened = app.tryToOpenAGoal(herOwn, null, "Kitchen", "1000.00");
        assertThat(opened.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long kitchen = opened.getBody().get("id").asLong();
        long greece = app.tryToOpenAGoal(herOwn, null, "Greece", "500.00")
                .getBody().get("id").asLong();

        assertThat(app.tryToReadTheGoals(herOwn, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(app.tryToReadTheGoal(herOwn, kitchen, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(app.tryToReadTheAllocations(herOwn, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(app.tryToRenameTheGoal(herOwn, kitchen, null, "Kitchen and floor")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(app.tryToReorderTheGoals(herOwn, null, List.of(greece, kitchen)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(app.tryToPinAWeeklyAmount(herOwn, kitchen, null, "25.00").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(app.tryToUnpinTheWeeklyAmount(herOwn, kitchen, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(app.tryToAbandonTheGoal(herOwn, kitchen, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(app.goalsOn(herOwn))
                .as("and everything those requests said they did, they did")
                .extracting(GoalView::name)
                .containsExactly("Greece");
    }

    /**
     * And naming somebody changes nothing at all, whoever they are. The parameter is read only when
     * a pot holds the account, so on a personal account it is not a permission check — it is a field
     * the account has no use for.
     */
    @Test
    void naming_a_customer_on_a_personal_accounts_goals_changes_nothing() {
        long herOther = app.otherSavingsAccountOf(ANKE);

        long hers = app.tryToOpenAGoal(herOther, ANKE, "Her own idea", "300.00")
                .getBody().get("id").asLong();
        ResponseEntity<JsonNode> byAStranger =
                app.tryToOpenAGoal(herOther, stranger, "Opened by somebody else", "100.00");

        assertThat(byAStranger.getStatusCode())
                .as("as trusting as it was before pots existed, which is the claim being kept")
                .isEqualTo(HttpStatus.CREATED);
        assertThat(app.tryToReadTheGoals(herOther, stranger).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(app.tryToRenameTheGoal(herOther, hers, stranger, "Renamed by somebody else")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(app.goalsOn(herOther))
                .extracting(GoalView::name)
                .containsExactly("Renamed by somebody else", "Opened by somebody else");
    }

    /** The weekly capacity of a personal account is its holder's, and it is gated on nothing. */
    @Test
    void the_weekly_capacity_of_a_personal_account_is_declared_and_read_by_anybody() {
        long his = app.savingsAccountOf(BRAM);

        assertThat(app.tryToDeclareTheSavingCapacity(his, null, "75.00").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(app.tryToReadTheSavingCapacity(his, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(app.tryToDeclareTheSavingCapacity(his, stranger, "80.00").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(app.savingCapacityOf(his).weeklyCapacity())
                .as("and the figure that was last declared is the figure that stands")
                .isEqualByComparingTo("80.00");
    }

    /**
     * The two accounts side by side, which is the sharpest form of the claim: one request shape,
     * two answers, and the only difference is which of them a pot holds.
     */
    @Test
    void the_same_request_is_allowed_on_her_own_account_and_refused_on_the_pots() {
        assertThat(app.tryToReadTheGoals(app.savingsAccountOf(ANKE), null).getStatusCode())
                .as("her own savings account, with nobody named, as it always was")
                .isEqualTo(HttpStatus.OK);

        assertThat(app.tryToReadTheGoals(pot.savingsAccountId(), null).getStatusCode())
                .as("and the pot's account, with nobody named, refused")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
