package io.dataroots.savingstreak.sharedpots;

import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.PotMemberView;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A customer opens a savings pot that belongs to a group rather than to them: it has the name they
 * chose, a savings account of its own, nothing in it, and exactly one member — themselves, as its
 * owner, from the moment it exists.
 *
 * <p>That last part is the one worth asserting hardest. A pot with nobody allowed to administer it
 * is a pot nobody can invite into, change or close, and there is no request in this application that
 * could repair one — so the moment a pot exists without an owner is a moment it can never leave. The
 * owner arrives in the same transaction as the pot, and the only thing that can say so from outside
 * is that the very first answer about a brand-new pot already names them.
 *
 * <p>Its own application, on a database nothing has ever been written to, for a reason the shared
 * one cannot give: a pot's savings account is held by nobody, and several tests on the run's shared
 * database ask for "an identifier no savings account has" by walking every account every customer
 * holds. An account held by nobody is in none of those lists, so a pot opened there would hand those
 * tests an identifier that does exist — a failure in whichever test happened to run second, about a
 * row it never created. Pots are therefore opened where they are the only thing that has happened.
 */
class APotIsOpenedByACustomerWhoBecomesItsOwnerApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-pot-is-opened"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The whole point of the slice: a pot exists, it is called what somebody called it, it has an
     * account of its own, and it holds nothing at all.
     */
    @Test
    void a_pot_is_opened_with_a_name_an_account_of_its_own_and_nothing_in_it() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");

        assertThat(pot.id()).isNotNull();
        assertThat(pot.name()).isEqualTo("Kitchen");
        assertThat(pot.savingsAccountId())
                .as("a pot has a savings account of its own, made with it")
                .isNotNull();
        assertThat(pot.moneyBalance())
                .as("and it starts empty, which is a figure and not an absence")
                .isEqualByComparingTo("0.00");
        // Off the application's clock rather than the machine's, so a pot opened against a clock a
        // trainer wound forward is dated where they wound it to.
        assertThat(pot.openedAt())
                .as("the moment comes off the clock the application judges everything else by")
                .isNotNull()
                .isBeforeOrEqualTo(app.theClockReads());
    }

    /**
     * The customer who opened it is in it, as its owner, in the very first answer about it — before
     * anything else could have been asked, let alone done.
     */
    @Test
    void the_customer_who_opened_it_is_its_owner_from_the_moment_it_exists() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");

        assertThat(pot.members()).hasSize(1);
        PotMemberView owner = pot.members().get(0);
        assertThat(owner.customerId()).isEqualTo(app.customerIdOf(ANKE));
        assertThat(owner.name())
                .as("a list of members reads as people rather than as numbers")
                .isEqualTo(ANKE);
        assertThat(owner.role()).isEqualTo("OWNER");
        assertThat(owner.joinedAt())
                .as("they have belonged to it since the moment it existed")
                .isEqualTo(pot.openedAt());
    }

    /**
     * And it reads the same back on its own page, which is the answer every screen after the first
     * is going to make. One shape for a pot just opened and a pot read back, so nothing rendering
     * one has to know which it is holding.
     */
    @Test
    void the_pot_reads_back_exactly_as_it_was_answered_when_it_was_opened() {
        SharedPotView opened = app.openAPot(ANKE, "New bike");

        SharedPotView read = app.potWith(opened.id());

        assertThat(read).isEqualTo(opened);
    }

    /**
     * And the membership is readable on its own, for the page that shows who can do what. The same
     * list as the one on the pot, because two answers to "who is in this" would be two answers to
     * every question about who may spend what.
     */
    @Test
    void the_members_read_the_same_on_a_page_of_their_own() {
        SharedPotView pot = app.openAPot(ANKE, "Winter tyres");

        List<PotMemberView> members = app.membersOfThePot(pot.id());

        assertThat(members).isEqualTo(pot.members());
    }

    /**
     * A name is kept as it was typed, apart from the spaces around it — the same treatment a
     * customer's own name gets. Nothing is capitalised or corrected: a training application whose
     * participants type deliberately odd things should record what they typed.
     */
    @Test
    void a_name_is_kept_as_it_was_typed_without_the_spaces_around_it() {
        SharedPotView pot = app.openAPot(ANKE, "  a new kitchen TABLE  ");

        assertThat(pot.name()).isEqualTo("a new kitchen TABLE");
        assertThat(app.potWith(pot.id()).name()).isEqualTo("a new kitchen TABLE");
    }

    /**
     * Two pots opened by the same customer are two pots, with two accounts. Somebody saving for a
     * kitchen with one person and a holiday with another is the ordinary case, and a second pot that
     * quietly reused the first one's account would be one pile of money with two names.
     */
    @Test
    void two_pots_opened_by_the_same_customer_are_two_pots_with_two_accounts() {
        SharedPotView one = app.openAPot(ANKE, "Roof");
        SharedPotView other = app.openAPot(ANKE, "Sofa");

        assertThat(other.id()).isNotEqualTo(one.id());
        assertThat(other.savingsAccountId()).isNotEqualTo(one.savingsAccountId());
        assertThat(app.potWith(one.id()).name()).isEqualTo("Roof");
        assertThat(app.potWith(other.id()).name()).isEqualTo("Sofa");
    }
}
