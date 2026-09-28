package io.dataroots.savingstreak.potcontributions;

import java.util.Arrays;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.MoneyMovementView;
import io.dataroots.savingstreak.support.PotInvitationView;
import io.dataroots.savingstreak.support.SharedPotView;
import io.dataroots.savingstreak.support.WithdrawalProposalView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A shared pot's money movements are listed the way a customer's own are — same list, same words,
 * same order — so that reading a pot's history needs no second screen.
 *
 * <p><strong>The claim is sameness, so the test reads both.</strong> Every row comes back in the
 * record a personal ledger's rows come back in, carries the same direction words, quotes its amount
 * the same way and reports what the deposit earned; the withdrawal earns nothing, which is what a
 * withdrawal has always earned in this application rather than a gap in the record. A member who has
 * read their own history knows how to read this one, which is the entire feature.
 *
 * <p><strong>And the pot's movements are not in anybody's personal ledger.</strong> Nobody holds a
 * pot's savings account, so a customer's own history goes on meaning exactly what it meant before
 * pots existed — asserted here, because the alternative is a member's personal screen quietly
 * growing rows about money that is not theirs alone.
 *
 * <p>Its own application and its own clock, for the reason every pot test gives: a pot on the shared
 * database would hand every other test class a savings account identifier that exists and that no
 * customer holds.
 */
class ThePotsMoneyMovementsReadBackTheWayAPersonalAccountsDoApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(aDatabaseFileThatDoesNotExistYet(
                "saving-streak-a-pots-money-movements-read-back"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_pots_history_is_the_same_list_in_the_same_shape_as_a_persons_own() {
        long herOwnAccount = app.savingsAccountOf(ANKE);
        SharedPotView pot = app.openAPot(ANKE, "A history to read back");
        PotInvitationView sent = app.invite(pot.id(), ANKE, BRAM, "CONTRIBUTOR");
        app.accept(pot.id(), sent.id(), BRAM);

        app.deposit(pot.savingsAccountId(), ANKE, "100.00");
        app.deposit(pot.savingsAccountId(), BRAM, "40.00");
        app.deposit(herOwnAccount, ANKE, "60.00");
        WithdrawalProposalView proposed = app.proposeAWithdrawal(pot.id(), ANKE, "100.00");
        app.approve(pot.id(), proposed.id(), BRAM);

        List<MoneyMovementView> ledger = app.moneyMovementsOfThePot(pot.id(), ANKE);

        assertThat(ledger)
                .as("newest first, the way a customer's own ledger is ordered: the withdrawal, then "
                        + "the two payments in, latest first")
                .extracting(MoneyMovementView::direction)
                .containsExactly("OUT_OF_SAVINGS", "INTO_SAVINGS", "INTO_SAVINGS");
        assertThat(ledger.get(0).amount()).isEqualByComparingTo("100.00");
        assertThat(ledger.get(1).amount()).isEqualByComparingTo("40.00");
        assertThat(ledger.get(2).amount()).isEqualByComparingTo("100.00");
        assertThat(ledger)
                .as("every row is about the pot's own savings account and no other")
                .extracting(MoneyMovementView::savingsAccountId)
                .containsOnly(pot.savingsAccountId());
        assertThat(ledger)
                .as("a euro saved is a point earned, and a withdrawal earns nothing — which is the "
                        + "figure it has always earned rather than a gap")
                .extracting(MoneyMovementView::pointsEarned)
                .containsExactly(0L, 40L, 100L);
        assertThat(ledger)
                .as("nothing here was made by a saving rule: a pot is filled by hand")
                .extracting(MoneyMovementView::automatic)
                .containsOnly(false);
        assertThat(ledger)
                .as("and none of it is a bill or a spend, so the components those rows carry are "
                        + "empty exactly as they are on a personal deposit")
                .allSatisfy(moved -> {
                    assertThat(moved.billName()).isNull();
                    assertThat(moved.dueOn()).isNull();
                    assertThat(moved.outcome()).isNull();
                    assertThat(moved.daysLate()).isNull();
                    assertThat(moved.spendName()).isNull();
                    assertThat(moved.correctedAt()).isNull();
                });

        // And the same fields read the same way on her own account's deposit, which is the whole
        // claim: this is not a second shape of ledger, it is the ledger.
        MoneyMovementView hersAlone = onlyTheSavingsMovementsOf(ANKE).get(0);
        assertThat(hersAlone.direction()).isEqualTo("INTO_SAVINGS");
        assertThat(hersAlone.amount()).isEqualByComparingTo("60.00");
        assertThat(hersAlone.pointsEarned())
                .as("her own sixty earned her sixty, at the same ordinary rate the pot's deposits "
                        + "were priced at — the pot changed nothing about how a deposit of hers is "
                        + "read back")
                .isEqualTo(60L);
        assertThat(hersAlone.savingsAccountId()).isEqualTo(herOwnAccount);

        assertThat(onlyTheSavingsMovementsOf(ANKE))
                .as("**and the pot's rows are in nobody's personal ledger**: nobody holds a pot's "
                        + "account, so her own history means what it meant before pots existed")
                .extracting(MoneyMovementView::savingsAccountId)
                .doesNotContain(pot.savingsAccountId());

        assertThat(app.moneyMovementsOfThePot(pot.id(), BRAM))
                .as("and every member reads the same history, whoever paid which part of it in")
                .isEqualTo(ledger);
    }

    /**
     * The savings half of a customer's own ledger, newest first.
     *
     * <p>Filtered because a seeded household's ledger carries its bills and its groceries as well,
     * and the claim here is about the rows money moving into and out of savings makes.
     */
    private static List<MoneyMovementView> onlyTheSavingsMovementsOf(String customerName) {
        return Arrays.stream(app.moneyMovementsOf(customerName))
                .filter(moved -> moved.savingsAccountId() != null)
                .toList();
    }
}
