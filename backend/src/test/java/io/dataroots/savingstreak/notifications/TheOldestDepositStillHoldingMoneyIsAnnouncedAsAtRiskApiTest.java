package io.dataroots.savingstreak.notifications;

import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which of the two loyalty reasons a deposit gets is decided by where it stands in the withdrawal
 * queue — and a deposit that reaches the front of that queue is told so, even though its anniversary
 * has not changed.
 *
 * <p>User stories 8, 9 and 12, and the load-bearing decision of this feature. Withdrawals have
 * always drained the oldest deposit first, and {@code WithdrawalsService} calls that ordering "a
 * protection"; a customer who cannot see the queue cannot be protected by it. So the oldest deposit
 * still holding money is the one whose bonus the next euro withdrawn would eat, and it is the one
 * announced as {@link NotificationReason#LOYALTY_BONUS_AT_RISK}. Everything behind it is
 * {@link NotificationReason#LOYALTY_BONUS_ABOUT_TO_PAY}: coming, and shielded.
 *
 * <p>Two deposits ten days apart in one pot, so that both anniversaries fall inside one thirty-day
 * window and one run of the sweep has to decide between them. Then the older one is emptied and the
 * sweep runs again: the newer deposit is now first in line, its anniversary is the same day it
 * always was, and it is announced a second time under the other reason. That escalation is why the
 * reason is part of what makes an announcement unique rather than something the record overwrites —
 * and the run in between, which raises nothing, is what says the record is not simply announcing
 * everything every night.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives.
 */
class TheOldestDepositStillHoldingMoneyIsAnnouncedAsAtRiskApiTest extends ApiIntegrationTest {

    /** What the older deposit holds — a tenth of it is 20 points. */
    private static final String WHAT_THE_OLDER_DEPOSIT_HOLDS = "200.00";

    /** What the newer deposit holds — a tenth of it is 30 points. */
    private static final String WHAT_THE_NEWER_DEPOSIT_HOLDS = "300.00";

    private static final long WHAT_THE_OLDER_ANNIVERSARY_IS_WORTH = 20;

    private static final long WHAT_THE_NEWER_ANNIVERSARY_IS_WORTH = 30;

    /**
     * Ten days between the two deposits, so their anniversaries are ten days apart and both fit
     * inside one thirty-day window — which is what makes the sweep choose between them rather than
     * reach one of them at a time.
     */
    private static final int DAYS_BETWEEN_THE_TWO_DEPOSITS = 10;

    /**
     * Twenty days short of the older deposit's first anniversary, which puts the newer deposit's
     * exactly thirty days out — both inside the window, with the older one nearer.
     */
    private static final int DAYS_UNTIL_BOTH_ANNIVERSARIES_ARE_NEAR = 345;

    private static AnApplicationWithAClockToMove app;
    private static TheNotificationSweep sweep;

    @BeforeAll
    static void startAnApplicationWhoseYearThisTestMovesOn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-first-in-line"));
        sweep = new TheNotificationSweep(app);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_queue_decides_the_reason_and_reaching_the_front_of_it_is_announced_again() {
        long savingsAccount = app.savingsAccountOf(ANKE);

        DepositView firstInLine = app.deposit(savingsAccount, ANKE, WHAT_THE_OLDER_DEPOSIT_HOLDS);
        app.daysPass(DAYS_BETWEEN_THE_TWO_DEPOSITS);
        DepositView shieldedBehindIt = app.deposit(savingsAccount, ANKE, WHAT_THE_NEWER_DEPOSIT_HOLDS);
        app.daysPass(DAYS_UNTIL_BOTH_ANNIVERSARIES_ARE_NEAR - DAYS_BETWEEN_THE_TWO_DEPOSITS);

        sweep.runs();

        List<RaisedNotification> said =
                sweep.whatWasSaidAboutAnAnniversaryIn(savingsAccount, ANKE);
        assertThat(said)
                .as("two deposits near their anniversaries, two notifications, and neither deposit "
                        + "said both things about one anniversary")
                .hasSize(2);
        RaisedNotification atRisk = whatWasSaidAbout(firstInLine, said);
        assertThat(atRisk.reason())
                .as("the oldest deposit still holding money is the one the next euro withdrawn "
                        + "comes out of, so its bonus is the one actually exposed")
                .isEqualTo(NotificationReason.LOYALTY_BONUS_AT_RISK);
        assertThat(atRisk.points()).isEqualTo(WHAT_THE_OLDER_ANNIVERSARY_IS_WORTH);

        RaisedNotification aboutToPay = whatWasSaidAbout(shieldedBehindIt, said);
        assertThat(aboutToPay.reason())
                .as("a deposit standing behind an older one would not be touched by the next "
                        + "withdrawal, so its anniversary is coming rather than at risk")
                .isEqualTo(NotificationReason.LOYALTY_BONUS_ABOUT_TO_PAY);
        assertThat(aboutToPay.points()).isEqualTo(WHAT_THE_NEWER_ANNIVERSARY_IS_WORTH);
        LocalDate theNewerDepositsAnniversary = aboutToPay.occursOn();

        sweep.runs();

        assertThat(sweep.whatWasSaidAboutAnAnniversaryIn(savingsAccount, ANKE))
                .as("a second sweep over an unchanged queue says nothing: an anniversary is near "
                        + "for thirty nights and is one occasion, not thirty")
                .hasSize(2);

        // The shield is emptied. The newer deposit's anniversary has not moved and is worth exactly
        // what it was; what has changed is that it is now first in line for the next withdrawal.
        app.withdraw(savingsAccount, ANKE, WHAT_THE_OLDER_DEPOSIT_HOLDS);
        assertThat(app.depositsInto(savingsAccount))
                .as("the withdrawal came out of the oldest deposit, which is the ordering this "
                        + "whole rule reports on")
                .anySatisfy(deposit -> {
                    assertThat(deposit.id()).isEqualTo(firstInLine.id());
                    assertThat(deposit.nextAnniversaryOn())
                            .as("emptied, so it has no anniversary left to reach")
                            .isNull();
                });

        sweep.runs();

        List<RaisedNotification> saidAfterwards =
                sweep.whatWasSaidAboutAnAnniversaryIn(savingsAccount, ANKE);
        assertThat(saidAfterwards)
                .as("one more thing to say: the deposit that was shielded is now first in line")
                .hasSize(3);
        RaisedNotification escalated = saidAfterwards.get(0);
        assertThat(escalated.depositId())
                .as("newest first, and the newest thing said is about the deposit that moved up "
                        + "the queue")
                .isEqualTo(shieldedBehindIt.id());
        assertThat(escalated.reason())
                .as("the money in front of it has gone, so the next euro withdrawn comes out of "
                        + "this deposit and its bonus is now the one exposed")
                .isEqualTo(NotificationReason.LOYALTY_BONUS_AT_RISK);
        assertThat(escalated.occursOn())
                .as("the same anniversary it was already told about — what changed is the queue, "
                        + "not the day, which is exactly why the reason is part of the key")
                .isEqualTo(theNewerDepositsAnniversary);
        assertThat(escalated.points())
                .as("and the same figure, because the withdrawal never reached this deposit")
                .isEqualTo(WHAT_THE_NEWER_ANNIVERSARY_IS_WORTH);
        assertThat(saidAfterwards.stream()
                .filter(raised -> firstInLine.id().equals(raised.depositId())))
                .as("nothing new was said about the emptied deposit, whose own anniversary is "
                        + "still inside the window: money that has gone has nothing at stake, and "
                        + "the one row about it is the warning it got while it still held its "
                        + "euros")
                .hasSize(1);

        sweep.runs();

        assertThat(sweep.whatWasSaidAboutAnAnniversaryIn(savingsAccount, ANKE))
                .as("and the escalation is an occasion too, so running the sweep again adds "
                        + "nothing")
                .hasSize(3);
    }

    /** The one thing said about this deposit, insisted on: two would be the bug this test is for. */
    private static RaisedNotification whatWasSaidAbout(DepositView deposit,
                                                       List<RaisedNotification> said) {
        List<RaisedNotification> about = said.stream()
                .filter(raised -> deposit.id().equals(raised.depositId()))
                .toList();
        assertThat(about)
                .as("the two reasons are mutually exclusive, so one deposit says one thing about "
                        + "one anniversary")
                .hasSize(1);
        return about.get(0);
    }
}
