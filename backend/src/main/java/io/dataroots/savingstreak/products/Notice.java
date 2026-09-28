package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Notice given on an amount of money in one savings account: how much, the day it was given, how
 * much of it a withdrawal has since used, and the day it was cancelled if it was.
 *
 * <p><strong>Four facts and no fifth one saying whether it is ready.</strong> Whether a notice has
 * run its course is {@code givenOn} plus the product's notice days against the application's clock,
 * worked out at the moment somebody asks — never written down here. A column saying "ready" would
 * be a third clock to keep truthful beside the injected one and the day above, and the job that set
 * it would be a nightly sweep to answer a subtraction. This application already holds that line for
 * the points balance, for a goal's status and for every offer window, and a notice is the same
 * shape of question.
 *
 * <p><strong>Consumed as an amount rather than as a flag.</strong> A withdrawal takes the oldest
 * ready notice first and takes part of one rather than wasting it, so notice on EUR 500 that paid
 * for a withdrawal of EUR 200 is still notice on EUR 300 — and a boolean could only have thrown the
 * other three hundred away. The amount consumed never exceeds the amount given, which is arithmetic
 * the service does rather than a constraint this row could express.
 *
 * <p><strong>Cancelled by a date rather than by deletion.</strong> Cancelling leaves nothing
 * standing — a cancelled notice covers nothing, appears on no list of what is waiting and can never
 * be uncancelled — but the row stays, because a customer asking why their withdrawal was refused on
 * the fifteenth deserves an answer that includes the notice they cancelled on the fourteenth. The
 * day rather than a boolean for the same reason every other state in this module carries its date:
 * "it was cancelled" and "it was cancelled a week before the money was needed" are different
 * answers, and one of them costs nothing to keep.
 *
 * <p>The product's notice days are deliberately not copied here. What this account is on is
 * {@link AccountAgreement}'s row and what that version asks for is {@link ProductTerms}', and a
 * third copy on every notice would be the one nobody remembered to change — worse, it would make a
 * notice period editable by whoever could write a notice.
 */
@Entity
class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long savingsAccountId;

    private BigDecimal amount;

    private LocalDate givenOn;

    private BigDecimal consumed;

    private LocalDate cancelledOn;

    protected Notice() {
    }

    private Notice(long savingsAccountId, BigDecimal amount, LocalDate givenOn) {
        this.savingsAccountId = savingsAccountId;
        this.amount = amount;
        this.givenOn = givenOn;
        this.consumed = BigDecimal.ZERO;
        this.cancelledOn = null;
    }

    /**
     * A factory rather than a public constructor, so that the sentence at the call site reads as
     * the thing that happened: somebody gave notice on an amount, on a day.
     */
    static Notice givenOn(long savingsAccountId, BigDecimal amount, LocalDate day) {
        return new Notice(savingsAccountId, amount, day);
    }

    long id() {
        return id;
    }

    long savingsAccountId() {
        return savingsAccountId;
    }

    BigDecimal amount() {
        return amount;
    }

    LocalDate givenOn() {
        return givenOn;
    }

    BigDecimal consumed() {
        return consumed;
    }

    LocalDate cancelledOn() {
        return cancelledOn;
    }

    /**
     * What this notice still covers: what it was given on, less whatever withdrawals have already
     * used, and nothing at all once it has been cancelled.
     *
     * <p>Cancelled first, so that a cancelled notice covers nothing whatever its arithmetic says.
     * That is what "leaves nothing standing behind it" means, and saying it here rather than at
     * every call site is what stops one of them forgetting.
     */
    BigDecimal stillStanding() {
        if (cancelledOn != null) {
            return BigDecimal.ZERO;
        }
        return amount.subtract(consumed).max(BigDecimal.ZERO);
    }

    /**
     * Uses part or all of what this notice still covers, and answers with how much it took.
     *
     * <p>Answers with the amount rather than with nothing, because the caller is walking notices
     * oldest first and subtracting as it goes: a walk that had to read the row back to find out
     * what it had just done would be reading its own write.
     */
    BigDecimal consume(BigDecimal wanted) {
        BigDecimal taken = stillStanding().min(wanted);
        consumed = consumed.add(taken);
        return taken;
    }

    /** Answers whether this press was the one that cancelled it, so a second press says nothing. */
    boolean cancelOn(LocalDate day) {
        if (cancelledOn != null) {
            return false;
        }
        cancelledOn = day;
        return true;
    }
}
