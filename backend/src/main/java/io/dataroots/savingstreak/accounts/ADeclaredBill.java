package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One recurring bill as its holder finds it: what they call it, the day of the month it goes out on,
 * what it is worth, when they said so, whether it is still standing, and when it was last actually
 * taken.
 *
 * <p>The outbound mirror of {@link DeclaredIncome}, and the shape differs from it in exactly the
 * place the two ideas differ. An income is one per account, so its record has a field saying whether
 * anything has been declared at all; a bill is one of many, so "nothing has been declared" is an
 * empty list and needs no field to say it. What a bill carries instead is its own identifier —
 * everything a customer does to one afterwards names it — and a state, because an ended bill is
 * still readable.
 *
 * <p>{@code endedAt} is filled exactly when {@code state} is {@link BillState#ENDED}, which is the
 * same bargain {@code RecordedSavingRule} strikes: the state is what a page switches on and the
 * moment is what it prints beside it.
 *
 * <p><strong>{@code lastTakenOn} is the day the money last actually left</strong>, and null on a
 * bill that has never been taken. It is a fact about what happened rather than about what was
 * declared, and it is here because it is the one thing a customer looking at a list of standing
 * bills wants to know that the declaration cannot tell them: a balance is a figure rather than a sum
 * of records, so nothing about it says which bills moved it. A date that was presented and refused
 * is deliberately not this date — the bill was not taken then, and saying otherwise would tell the
 * customer the opposite of what happened. Which dates went unpaid is the bill's own history, read
 * from its own path.
 *
 * <p>The cursor is deliberately not in it, for the reason {@link DeclaredIncome} gives about its
 * own: which due dates have been settled is the application's bookkeeping rather than the customer's
 * rent, and a page that showed it would be showing somebody their database. {@code lastTakenOn} is
 * not the cursor and is not derived from it — the cursor moves past a date that went unpaid, and
 * this does not.
 *
 * <p>A record rather than the entity, like everything that leaves this module.
 */
public record ADeclaredBill(long billId, long currentAccountId, String name, int dayOfMonth,
                            BigDecimal amount, Instant declaredAt, BillState state,
                            Instant endedAt, LocalDate lastTakenOn) {

    /**
     * The row as the rest of the application reads it, with the day it was last taken beside it. The
     * one place the entity becomes a record.
     *
     * @param lastTakenOn the day its money last left the account, or null if it never has
     */
    static ADeclaredBill of(RecurringBill bill, LocalDate lastTakenOn) {
        return new ADeclaredBill(bill.getId(), bill.getCurrentAccountId(), bill.getName(),
                bill.getDayOfMonth(), bill.getAmount(), bill.getDeclaredAt(), bill.getState(),
                bill.getEndedAt(), lastTakenOn);
    }
}
