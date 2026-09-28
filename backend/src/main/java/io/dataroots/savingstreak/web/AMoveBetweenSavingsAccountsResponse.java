package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.deposits.AMoveBetweenSavingsAccounts;
import io.dataroots.savingstreak.loyalty.NextAnniversaryOfADeposit;

/**
 * A move that happened, as the API reports it: which two accounts, how much, the two rows it
 * became, why it earned nothing, and the anniversary the money has just started counting towards.
 *
 * <p><strong>One answer for one thing the customer did.</strong> A move writes a row out of one
 * savings account and a row into another, because that is what a balance at each end is summed
 * from — but nobody pressed two buttons, so nothing here is reported twice. The two identifiers
 * are in it because a movement is the one thing in this application a person may want to point at
 * afterwards, and because the record of money that moved reports the move under the row that left.
 *
 * <p><strong>{@code earnedOnCarriedAcross} is why the points on this row are nought.</strong> It is
 * what the deposits the money left had already been paid for, carried onto the deposit it arrived
 * in — so the most the customer has ever saved is exactly what it was, and euros that have been
 * earned on once are not earned on again for changing which of their holder's accounts they sit in.
 * A customer looking at a five-thousand-euro movement that earned nothing is owed that figure
 * rather than left to guess.
 *
 * <p><strong>{@code newAnniversary} is the price, and it is here because it has just been
 * paid.</strong> The arriving euros are a new deposit with a clock that starts today, so the day
 * quoted before the move and the day reported after it are the same day — read the same way the
 * deposit endpoint reads it, off the Loyalty module, so a move just made and the same money looked
 * at tomorrow cannot answer differently. {@code pointsOnTheNewAnniversary} is what it will pay at
 * what the account now holds.
 *
 * <p>A day rather than a moment on the anniversary, and a moment on the move itself: the
 * anniversary is a promise about a calendar day and the move is an event, and each is reported in
 * the units the module that owns it reports it in.
 */
record AMoveBetweenSavingsAccountsResponse(long fromSavingsAccountId, long toSavingsAccountId,
                                           long customerId, BigDecimal amount,
                                           BigDecimal earnedOnCarriedAcross, long pointsEarned,
                                           long withdrawalId, long depositId, Instant movedAt,
                                           LocalDate newAnniversary, long pointsOnTheNewAnniversary) {

    /**
     * The move as the domain reported it, with the anniversary the arriving money has just started
     * counting towards read off the Loyalty module beside it.
     *
     * <p>The anniversary is never absent in practice — the arriving deposit holds every cent of
     * itself the instant it is written, so it is in the account's reading of what next pays — but
     * it is read defensively all the same, because a null day is a panel a page can leave out and a
     * failed lookup is not a reason to fail a move that has already happened.
     *
     * <p>{@code pointsEarned} is written as the nought it is rather than left out. A move earns
     * nothing, and that is the rule rather than a gap in the answer: the euros were earned on once,
     * in the account next door, and a euro saved twice is one euro.
     */
    static AMoveBetweenSavingsAccountsResponse of(AMoveBetweenSavingsAccounts moved,
                                                  NextAnniversaryOfADeposit itsNewAnniversary) {
        return new AMoveBetweenSavingsAccountsResponse(moved.fromSavingsAccountId(),
                moved.toSavingsAccountId(), moved.customerId(), moved.amount(),
                moved.earnedOnCarriedAcross(), 0, moved.withdrawalId(), moved.depositId(),
                moved.movedAt(),
                itsNewAnniversary == null ? null : itsNewAnniversary.on(),
                itsNewAnniversary == null ? 0 : itsNewAnniversary.points());
    }
}
