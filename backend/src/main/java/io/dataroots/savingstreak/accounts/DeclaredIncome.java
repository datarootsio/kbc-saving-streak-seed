package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What a current account's holder says lands in it every month — or that they have said nothing.
 *
 * <p><strong>Not declared is a state, not a zero.</strong> {@link #amount()} is null until the
 * customer says a figure, and everything downstream is expected to report that no income has been
 * declared rather than to quote a number. The distinction is the whole of this ticket's argument: an
 * account nobody has declared an income against receives nothing and behaves exactly as current
 * accounts behaved before, whereas an income of zero would be a customer saying they are paid
 * nothing — a sentence nobody said, and one this application refuses to keep anyway. It is the same
 * shape {@code SavingCapacityOnAnAccount} uses for a capacity nobody declared.
 *
 * <p>{@link #nextPayday()} is the calendar reading that goes with it: the next day this income is
 * due, worked out from the day the customer said and clamped to the end of a short month. It is
 * carried here rather than left to whoever shows the income, so that the page and the job cannot
 * disagree about what "the 31st" means in February — both ask {@link WhenIncomeIsDue}.
 *
 * <p>The cursor is deliberately not in it. Which paydays have been settled is how the job keeps
 * itself bounded, and it is an answer about the application's own bookkeeping rather than about the
 * customer's salary; a page that showed it would be showing somebody their database.
 *
 * <p>A record rather than the entity, like everything that leaves this module.
 */
public record DeclaredIncome(long currentAccountId, Integer dayOfMonth, BigDecimal amount,
                             Instant declaredAt, LocalDate nextPayday) {

    /** An account whose holder has said nothing: no day, no figure, no moment and nothing coming. */
    static DeclaredIncome notDeclaredOn(long currentAccountId) {
        return new DeclaredIncome(currentAccountId, null, null, null, null);
    }

    /** Whether the customer has said anything at all, which is what tells absence from a small figure. */
    public boolean isDeclared() {
        return amount != null;
    }
}
