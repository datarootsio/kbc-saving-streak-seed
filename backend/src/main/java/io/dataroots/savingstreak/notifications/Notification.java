package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * A moment a rule decided was worth saying, written down.
 *
 * <p>Stored rather than derived, for the reason a paid loyalty bonus is stored: what happened is a
 * row, and a condition that has passed can no longer be worked out from the present. A balance that
 * reached EUR 1.000 last month and has since been drawn down cannot be asked about the month it was
 * over the rung; only the row it left behind can.
 *
 * <p>Nothing is ever deleted and there is no third state. Two states, unread and read, is one state
 * machine, and a training application whose whole point is showing a rule fire should not offer a
 * button that throws away the evidence that it did.
 *
 * <p>Which fields are filled is decided by the reason, and totally. A balance notification carries
 * the rung as its {@code amount} and no deposit, points or day; a notification about a deposit's
 * anniversary carries the deposit, the points and the day and no amount; a notification about an
 * automatic transfer that did not happen carries the occurrence, the day it was due and what the
 * account was short, and neither a deposit nor points. The static factories are
 * the only way to make one, so an inconsistent combination — a balance row naming a deposit, say —
 * cannot be constructed. The columns the anniversary reasons need are here already and stay null
 * until the rule that fills them arrives: a column added later against a table SQLite has already
 * created is a schema change, and a null column nobody writes is not.
 *
 * <p>{@code readAt} is a moment rather than a flag, and it is set once. Re-reading an
 * already-read notification leaves the original moment alone, which is what makes marking-read
 * idempotent on the entity itself in the way {@code PointsCredit.expire} is.
 *
 * <p>Package-private, like every other thing in this module except the reason and the projection:
 * a row of this module's record is not something another module gets a handle on.
 */
@Entity
class Notification {

    /**
     * What separates two of the quoted sentences inside the one column that holds them. A newline,
     * because the sentences are the domain's own, none of them contains one, and a file of them
     * read straight out of the database reads as the list it is.
     */
    private static final String BETWEEN_SENTENCES = "\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long customerId;

    /**
     * Written as its name rather than as its position, so that a row means the same thing after a
     * value is added to the enum — and so that a reviewer reading the table with a SQLite client
     * sees the rule rather than an ordinal to look up.
     */
    @Enumerated(EnumType.STRING)
    private NotificationReason reason;

    /**
     * The savings account the notification is about, and null for the reasons that are not about
     * one. A balance rung belongs to a savings account and so does the deposit an anniversary
     * belongs to, and carrying it is what lets that account's own page draw the notice that concerns
     * it rather than hiding every warning behind one icon.
     *
     * <p><strong>It stopped being "always present" when bills arrived.</strong> A bill that could not
     * be paid and a current account whose arrears are piling up are facts about a current account,
     * and there is no savings account they are about — filling this in with whichever savings account
     * the customer happens to hold would put an unpaid rent on the savings page as though a saving
     * rule had refused it. Null says what is true, which is that this notification is about the other
     * side of the customer's money.
     */
    private Long savingsAccountId;

    /**
     * The current account the notification is about, and null for the reasons that are not about one.
     *
     * <p>Added rather than read off {@code savingsAccountId}, because the two are different accounts
     * and a column meaning "one account or the other depending on the reason" is a column nobody can
     * query. Which fields are filled is decided by the reason and totally, and that stays true with
     * two account columns exactly as it was with one.
     */
    private Long currentAccountId;

    /** The deposit an anniversary belongs to, and null for a balance rung. */
    private Long depositId;

    /**
     * The recurring bill a date that could not be paid belongs to, and null for every other reason —
     * the piling-up warning included, which is about an account's whole hole and blames no one bill.
     *
     * <p>It is half of what makes this family's uniqueness a rule rather than an intention: with the
     * day, it names one due date, and one due date is one notification however many nights the bill
     * is presented and refused afterwards. The index over it is partial for the same reason the
     * anniversary one is.
     */
    private Long billId;

    /**
     * What that bill was called when the notification was raised, and null for every other reason.
     *
     * <p><strong>A value, not a sentence.</strong> Every euro and every date in this application is
     * written in the browser and this row carries figures rather than prose — but "Rent could not be
     * paid" is something a customer can act on and "bill 4 could not be paid" is not, and the name is
     * the customer's own word rather than wording this module composed. It is a snapshot on purpose:
     * a bill renamed or ended afterwards does not rewrite what the customer was told at the time, and
     * an ended bill's arrears are still owed.
     */
    private String billName;

    /**
     * The spending category a budget warning belongs to, and null for every other reason — the
     * over-committed month included, which is about an account's whole promise and blames no one
     * category.
     *
     * <p>A column of its own rather than a reference overloaded onto {@code billId}, for the reason
     * {@code currentAccountId} is a column of its own rather than a reading of
     * {@code savingsAccountId}: a bill and a category are two different things, and a column meaning
     * "a bill or a category depending on the reason" is a column nobody can query.
     *
     * <p>It is half of what makes this family's uniqueness a rule rather than an intention: with
     * {@link #occursOn}, which carries the day the month began, it names one category's one month,
     * and one month is one warning however many nights the category stands over its line
     * afterwards. The index over it is partial for the same reason the bill one is.
     */
    private Long categoryId;

    /**
     * What that category was called when the notification was raised, and null for every other
     * reason.
     *
     * <p><strong>A value, not a sentence</strong>, exactly as {@link #billName} is. "Groceries is
     * running low" is something a customer can act on and "category 4 is running low" is not, and
     * the name is the customer's own word rather than wording this module composed. It is a snapshot
     * on purpose: a category renamed or ended afterwards does not rewrite what its holder was told
     * at the time, and this record is a log of the nights it was written on rather than a second
     * reading of what is true now.
     */
    private String categoryName;

    /**
     * The saving-rule occurrence an automatic transfer that did not happen belongs to, and null for
     * every other reason.
     *
     * <p>It is what makes this family's uniqueness a rule rather than an intention: one occurrence
     * is one thing that did not happen, however many nights the sweep runs afterwards. The balance
     * family cannot be keyed that way — a rung can honestly be announced, lost and announced again —
     * and the anniversary family is keyed on its own three columns, which is why the index over this
     * one is partial too.
     */
    private Long occurrenceId;

    /**
     * The rung, for a balance reason; what the current account was short, for an automatic transfer
     * that did not happen; what the bill asked for, for a date that could not be paid; what the
     * arrears come to altogether, for the piling-up warning; what the month allows, for a budget
     * warning; what the month is promised to, for an over-committed month; and null for an
     * anniversary.
     */
    private BigDecimal amount;

    /**
     * The figure the one above it was measured against, for the reasons that were decided by
     * comparing two, and null for the reasons that were decided by one.
     *
     * <p>What the current account actually held, for a date that could not be paid — the balance on
     * the night the date fell short, read off the record the billing run wrote at half past two, and
     * not the balance now. What has been spent, for a budget warning, against what that month
     * allows. What the month has, for an over-committed month, against what it is promised to.
     *
     * <p><strong>A second money column rather than a single difference</strong>, and the reason is
     * the same in all three: a customer told only that a bill was "short by" a figure cannot see the
     * balance it was measured against, one told only that a category has thirty euros left cannot
     * tell thirty of forty from thirty of three thousand, and one told only that a month is over by
     * a hundred cannot tell whether the month is small or the promise enormous. Both halves of a
     * decision are what make it a decision somebody can act on.
     */
    private BigDecimal balance;

    /**
     * How many dates are outstanding, for the piling-up warning, and null for every other reason.
     *
     * <p>One of the two figures that decided the warning — three or more outstanding, or a total over
     * the declared income — and the one a customer can count. It is a count and not money, which is
     * why it is not {@code amount}, and it is not {@code points}, which means points everywhere else
     * in this application.
     */
    private Long arrears;

    /** What an anniversary pays, and null for a balance rung. */
    private Long points;

    /**
     * The day an anniversary falls on, the day a transfer was due, or the day a bill was owed on,
     * and null for a balance rung and for the piling-up warning.
     */
    private LocalDate occursOn;

    /**
     * The catalogue code of the offer being held, for a promoted waiter, and null for every
     * other reason.
     *
     * <p>Text rather than a reference, exactly as every table in the rewards module names an
     * offer: the code is that catalogue's immutable natural key, and it is what the page's
     * reward-icon map is keyed on — which is the one place in the frontend that turns a
     * catalogue code into a picture, and the reason a notification about a brand new offer
     * draws correctly with no change here or there.
     */
    private String offerCode;

    /**
     * What that offer is called, beside its code, and null for every other reason.
     *
     * <p><strong>Snapshotted, unlike the title on a hold.</strong> A hold reads its title off
     * the catalogue as it stands because it is a live thing with three days to run; this is an
     * inbox entry that is never retracted and never rewritten, sitting beside notifications
     * about months that ended and bills that were paid years ago. The same argument the claim
     * makes for snapshotting a title applies word for word: a line reading "one of null is
     * being held for you" after somebody withdrew the offer would be a record that had rotted
     * behind its reader. It is the same shape {@link #billName} and {@link #categoryName}
     * already take, and for the same reason.
     */
    private String offerTitle;

    /**
     * The moment the hold runs out, for a promoted waiter, and null for every other reason.
     *
     * <p>A second {@link Instant} column beside {@link #raisedAt} rather than a day in
     * {@link #occursOn}, which is where every other deadline in this record lives. A hold is
     * seventy-two hours from the moment it was created, so a calendar day would round the
     * promise rather than record it, and the record would then disagree with the hold it is
     * about by up to a day. The argument is {@code TheShelfLifeOfAHold}'s.
     */
    private Instant lapsesAt;

    /**
     * The notice given on an amount that has now run its days, for the one reason that is about
     * notice, and null on every other.
     *
     * <p>A column of its own beside {@link #billId}, {@link #categoryId}, {@link #depositId} and
     * {@link #occurrenceId} rather than one shared "the thing this is about", for the reason all
     * four of those are separate: a column whose meaning depends on the reason is a column nobody
     * can query, and it is what makes each family's once-only rule a partial index the database
     * keeps rather than an intention this application merely holds.
     *
     * <p>The notice and not the day is what makes the announcement unique. Two notices given on one
     * morning come free on one morning and are two separate amounts, each spent separately, so a
     * day-keyed record would quietly merge them.
     */
    private Long noticeId;

    /**
     * The savings product the notice is about, by the code that never changes, for the two reasons
     * that are about an agreement rather than about money — and null on every other.
     *
     * <p>The code as well as {@link #productName} for the reason {@link #offerCode} sits beside
     * {@link #offerTitle}: the code is the address a page links to the product's own screen with,
     * and the name is the word a sentence is written from. Neither stands in for the other.
     */
    private String productCode;

    /**
     * What that product was called when the notice was raised, and null on every other reason.
     *
     * <p>A snapshot on purpose, exactly like {@link #billName}, {@link #categoryName} and
     * {@link #offerTitle}: renaming the product afterwards does not rewrite what the customer was
     * told, and an inbox line goes on reading correctly years later.
     */
    private String productName;

    /**
     * The version of that product's terms the notice is about, for the one reason that announces a
     * bettered agreement, and null on every other.
     *
     * <p><strong>It is what makes that announcement once-per-version</strong>, which is the rule the
     * spec asks for: an account told about version 3 is not told about it again, and a version 4
     * published later that also betters what the account holds is a new thing to say. The uniqueness
     * is a partial index over this column and the account, so it is a rule the database keeps rather
     * than one the sweep merely intends.
     *
     * <p>Boxed, because null here means "this reason is not about a version" and a primitive would
     * have to say that with a zero that looks like a version number.
     */
    private Integer termsVersion;

    /**
     * What the two agreements say differently, one sentence per figure that moved, joined by
     * newlines — and null on every reason but the bettered one.
     *
     * <p><strong>Quoted and never written here.</strong> The sentences are
     * {@code WhatIsDifferentBetweenTwoSetsOfTerms}'s, reached through
     * {@code ProductsService.theNewerTermsFor}, which is the only place in this application a
     * difference between two agreements is put into words. A notification that worded its own would
     * be the second place, and the two would describe one rate cut differently on one screen. What
     * this module decides for itself is whether the move was an improvement — that judgement is
     * {@link WhenTermsHaveBeenBettered}'s and touches no words at all.
     *
     * <p><strong>One column of joined lines rather than a table of them.</strong> The list is one
     * statement about one pair of versions: it is written once, never added to, never queried and
     * never filtered on, and every reader wants the whole of it. A child table would be a second
     * table to keep in step with this one, a join on every read of the panel, and a schema change
     * against a database this application only ever migrates by adding nullable columns. The
     * separator is a newline because the sentences are the domain's own and none of them contains
     * one.
     *
     * <p>It can legitimately be empty — a version can move the rate and nothing else is not one of
     * those, but a version that moves no figure at all can be published — and such a version is
     * never announced by this reason anyway, because a rate that did not move is not a betterment.
     * The empty case therefore only reaches the database through a hand-edited row.
     */
    private String whatIsDifferent;

    private Instant raisedAt;

    private Instant readAt;

    protected Notification() {
    }

    private Notification(long customerId, NotificationReason reason, Long savingsAccountId,
                         BigDecimal amount, Instant raisedAt) {
        this.customerId = customerId;
        this.reason = reason;
        this.savingsAccountId = savingsAccountId;
        this.amount = amount;
        this.raisedAt = raisedAt;
    }

    /**
     * The other half of the per-reason nullability, and the reason there are two constructors rather
     * than one taking everything: a caller that could pass both an amount and a deposit could
     * construct a row belonging to neither family. Each of these two fills exactly the columns one
     * family has and leaves the other family's null.
     */
    private Notification(long customerId, NotificationReason reason, Long savingsAccountId,
                         long depositId, LocalDate occursOn, long points, Instant raisedAt) {
        this.customerId = customerId;
        this.reason = reason;
        this.savingsAccountId = savingsAccountId;
        this.depositId = depositId;
        this.occursOn = occursOn;
        this.points = points;
        this.raisedAt = raisedAt;
    }

    /**
     * The third family's own, for the same reason the second has one: the columns an automatic
     * transfer that did not happen fills are not the columns either of the others fills, and a
     * single constructor taking everything is a constructor that can build a row belonging to no
     * family at all. The reason is not a parameter because there is exactly one reason in this
     * family, so nothing may pass a different one.
     *
     * <p>The shortfall is insisted on here, which is the one place the claim above this class is
     * kept rather than merely made. A row of this family with no figure on it is not a quieter
     * notification: the page writes its sentence out of the day and the figure together, so such a
     * row reaches a customer as a red alert with no words in it. The caller that could hand one
     * over is the sweep reading an occurrence settled before the figure was recorded, and it
     * refuses that occurrence itself and says why — {@link NotificationsService} argues that out.
     * This is the backstop, not the rule, and reaching it is a programming error rather than a
     * thing a customer can cause.
     */
    private Notification(long customerId, long savingsAccountId, long occurrenceId, LocalDate dueOn,
                         BigDecimal shortfall, Instant raisedAt) {
        if (shortfall == null) {
            throw new IllegalArgumentException(
                    "a notification about a transfer that did not happen carries what the account "
                            + "was short, and occurrence " + occurrenceId + " has no such figure");
        }
        this.customerId = customerId;
        this.reason = NotificationReason.AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN;
        this.savingsAccountId = savingsAccountId;
        this.occurrenceId = occurrenceId;
        this.occursOn = dueOn;
        this.amount = shortfall;
        this.raisedAt = raisedAt;
    }

    /**
     * A savings balance now stands on a rung it was not last known to stand on, and the new rung is
     * the higher one. The amount is the rung it has landed on — one notification however many rungs
     * a single deposit vaulted, because what a customer wants told is where they are.
     */
    static Notification balanceRungReached(long customerId, long savingsAccountId, BigDecimal rung,
                                           Instant raisedAt) {
        return new Notification(
                customerId, NotificationReason.BALANCE_THRESHOLD_REACHED, savingsAccountId, rung,
                raisedAt);
    }

    /**
     * A savings balance no longer reaches a rung it did. The amount is the lowest rung it no longer
     * reaches, which is what makes the row readable backwards into the position the balance is in —
     * {@link NotificationsService} argues that out.
     */
    static Notification balanceRungLost(long customerId, long savingsAccountId, BigDecimal rung,
                                        Instant raisedAt) {
        return new Notification(
                customerId, NotificationReason.BALANCE_THRESHOLD_LOST, savingsAccountId, rung,
                raisedAt);
    }

    /**
     * A deposit's next anniversary is near enough to be worth saying, it is worth at least one
     * point, and the deposit stands behind an older one still holding money — so the money the
     * anniversary would pay on is not what the next withdrawal would reach first.
     *
     * <p>The day and the points come from Loyalty exactly as they are: they are the same figures
     * the deposits table already shows, and nothing in this module works out what an anniversary is
     * worth.
     */
    static Notification anniversaryComingFor(long customerId, long savingsAccountId, long depositId,
                                             LocalDate on, long points, Instant raisedAt) {
        return new Notification(
                customerId, NotificationReason.LOYALTY_BONUS_ABOUT_TO_PAY, savingsAccountId,
                depositId, on, points, raisedAt);
    }

    /**
     * The same anniversary on the deposit that is first in line for the next withdrawal, which is
     * the oldest one in the account still holding money — so the euros this anniversary would be
     * paid on are exactly the euros a withdrawal would take.
     */
    static Notification anniversaryAtRiskFor(long customerId, long savingsAccountId, long depositId,
                                             LocalDate on, long points, Instant raisedAt) {
        return new Notification(
                customerId, NotificationReason.LOYALTY_BONUS_AT_RISK, savingsAccountId, depositId,
                on, points, raisedAt);
    }

    /**
     * A saving rule fell due, the current account did not hold what it asked for, and nothing moved.
     *
     * <p>The third family, and its own constructor for the reason the other two have theirs: it
     * fills the occurrence, the day the transfer was due and what the account was short, and leaves
     * the deposit and the points null. A row naming both a deposit and an occurrence is not
     * something this class can be made to build.
     *
     * <p>The shortfall travels as the {@code amount}, which is the money column this record already
     * has, and it is the figure the customer can act on: a transfer that did not happen is worth
     * knowing about, and how far short they were is the thing that says whether it will happen next
     * time. What the rule asked for is on the occurrence itself, in the rule's own history, where
     * the rest of what happened that day already is.
     */
    static Notification automaticTransferDidNotHappen(long customerId, long savingsAccountId,
                                                      long occurrenceId, LocalDate dueOn,
                                                      BigDecimal shortfall, Instant raisedAt) {
        return new Notification(customerId, savingsAccountId, occurrenceId, dueOn, shortfall,
                raisedAt);
    }

    /**
     * A bill fell due on a current account that could not cover it, so nothing at all was taken and
     * the date stays owed.
     *
     * <p>The fourth family's own constructor, for the reason the third has one: these are not the
     * columns any other family fills. It insists on the two figures for the same reason as well —
     * the page writes its sentence out of the day, what was asked for and what was there, and a row
     * missing either of them reaches a customer as a red warning with a hole in it. The caller that
     * could hand one over is the raiser reading a date recorded before the application wrote down
     * what fell short, and it leaves those dates out itself and says why. This is the backstop.
     *
     * <p><strong>The name is insisted on beside the figures</strong>, because it is the word the
     * sentence leads with: "Rent could not be paid" is something a customer can act on, and a red
     * warning that opens with a blank is worse than the four other families' worst case. The name is
     * looked up against the bill the date belongs to, and nothing in this application deletes a
     * bill — so this is latent in the same way the figures are, and is held the same way.
     */
    private Notification(long customerId, long currentAccountId, long billId, String billName,
                         LocalDate dueOn, BigDecimal amount, BigDecimal balance, Instant raisedAt) {
        if (amount == null || balance == null || billName == null || billName.isBlank()) {
            throw new IllegalArgumentException(
                    "a notification about a bill that could not be paid carries the bill's name, "
                            + "what it asked for and what the account held, and bill " + billId
                            + " on " + dueOn + " has name=" + billName + " amount=" + amount
                            + " balance=" + balance);
        }
        this.customerId = customerId;
        this.reason = NotificationReason.A_BILL_COULD_NOT_BE_PAID;
        this.currentAccountId = currentAccountId;
        this.billId = billId;
        this.billName = billName;
        this.occursOn = dueOn;
        this.amount = amount;
        this.balance = balance;
        this.raisedAt = raisedAt;
    }

    /**
     * A current account's unpaid bills have crossed into a spiral: three or more dates outstanding
     * at once, or a total that has passed the declared monthly income.
     *
     * <p>The fifth family, and the only one about an account's whole state rather than about one
     * thing that happened. It names no bill and no day on purpose: the figure worth acting on is the
     * hole, and no single date is to blame for there being three of them.
     */
    private Notification(long customerId, long currentAccountId, long arrears,
                         BigDecimal amountOwed, Instant raisedAt) {
        if (amountOwed == null) {
            throw new IllegalArgumentException(
                    "a notification about arrears piling up carries what they come to, and current "
                            + "account " + currentAccountId + " has no such figure");
        }
        this.customerId = customerId;
        this.reason = NotificationReason.BILLS_ARE_PILING_UP;
        this.currentAccountId = currentAccountId;
        this.arrears = arrears;
        this.amount = amountOwed;
        this.raisedAt = raisedAt;
    }

    /**
     * A date that was presented to a current account and refused. Raised once, on the night the date
     * went unpaid, and never again for that date however many runs present it afterwards.
     */
    static Notification aBillCouldNotBePaid(long customerId, long currentAccountId, long billId,
                                            String billName, LocalDate dueOn, BigDecimal amount,
                                            BigDecimal balance, Instant raisedAt) {
        return new Notification(customerId, currentAccountId, billId, billName, dueOn, amount,
                balance, raisedAt);
    }

    /**
     * An account's arrears have crossed the threshold at which they are a spiral rather than a bad
     * month. Raised on the crossing, and not again until the account has come back under it and
     * crossed again.
     */
    static Notification billsArePilingUp(long customerId, long currentAccountId, long arrears,
                                         BigDecimal amountOwed, Instant raisedAt) {
        return new Notification(customerId, currentAccountId, arrears, amountOwed, raisedAt);
    }

    /**
     * A spending category's month has crossed a line: four fifths of what it allows, or all of it.
     *
     * <p>The sixth family, and one constructor for its two reasons rather than two identical ones,
     * because these two really are one statement about one month read at two distances — the columns
     * they fill are the same columns, and the difference between them is which line was crossed.
     * That is the same bargain the balance family strikes with its own pair, and it is why the
     * reason is a parameter here where the third and fifth families would not allow one.
     *
     * <p><strong>The month travels as {@code occursOn}, as the day it began.</strong> That is a
     * date, it is the honest one, and a second date column meaning "month" would be a column two
     * reasons out of ten could use. {@code TheMonthAMomentFallsIn} makes the same bargain from the
     * other side: a month is a {@code YearMonth} where the arithmetic is written and the first of
     * the month where a row holds it, and the pair is the only place the two representations meet.
     * It is also half of what makes this family unique — one category, one month, one warning — so
     * the column is doing the work of a key rather than merely carrying a figure.
     *
     * <p>The two figures and the name are insisted on for the reason the bill family insists on its
     * three: the page writes its sentence out of the category's own word, what the month allows and
     * what has been spent, and a row missing any of them reaches a customer as a warning with a hole
     * in it. Reaching this is a programming error rather than something a customer can cause — every
     * row the sweep builds comes out of a read model that has all three — so this is the backstop
     * and not the rule.
     */
    private Notification(long customerId, NotificationReason reason, long currentAccountId,
                         long categoryId, String categoryName, LocalDate theMonthBegan,
                         BigDecimal allowed, BigDecimal spent, Instant raisedAt) {
        if (allowed == null || spent == null || categoryName == null || categoryName.isBlank()) {
            throw new IllegalArgumentException(
                    "a notification about a budget carries the category's name, what its month "
                            + "allows and what has been spent against it, and category " + categoryId
                            + " in the month beginning " + theMonthBegan + " has name="
                            + categoryName + " allowed=" + allowed + " spent=" + spent);
        }
        this.customerId = customerId;
        this.reason = reason;
        this.currentAccountId = currentAccountId;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.occursOn = theMonthBegan;
        this.amount = allowed;
        this.balance = spent;
        this.raisedAt = raisedAt;
    }

    /**
     * A current account's month is promised to more than it holds.
     *
     * <p>The seventh family. It names no category for the reason the piling-up warning names no
     * bill: the figure worth acting on is the whole promise, and no single word the customer files
     * their money under is to blame for there being more of them than the month can pay for. It
     * carries the month all the same — unlike the piling-up warning, which is about a pile that has
     * no month — because the promise it is about is one month's promise and is a different promise
     * in the next one.
     *
     * <p>Both figures are insisted on for the reason the family above insists on its two: one of
     * them alone is a difference, and a difference cannot be checked against anything.
     */
    private Notification(long customerId, long currentAccountId, LocalDate theMonthBegan,
                         BigDecimal promisedTo, BigDecimal hasGot, Instant raisedAt) {
        if (promisedTo == null || hasGot == null) {
            throw new IllegalArgumentException(
                    "a notification about a month promised to more than it holds carries both "
                            + "figures, and current account " + currentAccountId + " in the month "
                            + "beginning " + theMonthBegan + " has promisedTo=" + promisedTo
                            + " hasGot=" + hasGot);
        }
        this.customerId = customerId;
        this.reason = NotificationReason.THE_MONTH_IS_OVER_COMMITTED;
        this.currentAccountId = currentAccountId;
        this.occursOn = theMonthBegan;
        this.amount = promisedTo;
        this.balance = hasGot;
        this.raisedAt = raisedAt;
    }

    /**
     * A category has spent four fifths of what its month allows without spending all of it. Raised
     * once for that category and that month, on the night it crossed, and never again in it.
     */
    static Notification aBudgetIsRunningLow(long customerId, long currentAccountId, long categoryId,
                                            String categoryName, LocalDate theMonthBegan,
                                            BigDecimal allowed, BigDecimal spent,
                                            Instant raisedAt) {
        return new Notification(customerId, NotificationReason.A_BUDGET_IS_RUNNING_LOW,
                currentAccountId, categoryId, categoryName, theMonthBegan, allowed, spent, raisedAt);
    }

    /**
     * A category has spent more than its month allows — the budget and the carry together. Raised
     * once for that category and that month, and in place of the quieter warning rather than beside
     * it.
     */
    static Notification aBudgetHasBeenOverspent(long customerId, long currentAccountId,
                                                long categoryId, String categoryName,
                                                LocalDate theMonthBegan, BigDecimal allowed,
                                                BigDecimal spent, Instant raisedAt) {
        return new Notification(customerId, NotificationReason.A_BUDGET_HAS_BEEN_OVERSPENT,
                currentAccountId, categoryId, categoryName, theMonthBegan, allowed, spent, raisedAt);
    }

    /**
     * An account's bills, arrears and budgets together claim more of this month than its balance and
     * the income still due in it come to. Raised once for that account and that month.
     */
    static Notification theMonthIsOverCommitted(long customerId, long currentAccountId,
                                                LocalDate theMonthBegan, BigDecimal promisedTo,
                                                BigDecimal hasGot, Instant raisedAt) {
        return new Notification(customerId, currentAccountId, theMonthBegan, promisedTo, hasGot,
                raisedAt);
    }

    /**
     * The eighth family, and the only one that is about a reward rather than about money.
     *
     * <p>Its own constructor for the reason every other family here has one: the three columns
     * a promoted waiter fills are not the columns any other family fills, and one constructor
     * taking everything is a constructor that can build a row belonging to no family at all.
     * The reason is not a parameter because there is exactly one reason in this family, so
     * nothing may pass a different one. There is no account on it either — a hold belongs to a
     * customer and to no account of theirs, exactly as their points do.
     *
     * <p>All three are insisted on, and reaching this is a programming error rather than a
     * thing a customer can cause. The page writes its sentence out of the title and the moment
     * together, so a row missing either reaches its reader as a line with a hole in it — and a
     * row missing the deadline is precisely the deadline nobody saw coming that a hold must
     * never be.
     */
    private Notification(long customerId, String offerCode, String offerTitle, Instant lapsesAt,
                         Instant raisedAt) {
        if (offerCode == null || offerCode.isBlank() || offerTitle == null
                || offerTitle.isBlank() || lapsesAt == null) {
            throw new IllegalArgumentException(
                    "a notification about a reward being held carries the offer, its title and "
                            + "the moment the hold runs out, and this one has offerCode="
                            + offerCode + " offerTitle=" + offerTitle + " lapsesAt=" + lapsesAt);
        }
        this.customerId = customerId;
        this.reason = NotificationReason.A_REWARD_IS_BEING_HELD_FOR_YOU;
        this.offerCode = offerCode;
        this.offerTitle = offerTitle;
        this.lapsesAt = lapsesAt;
        this.raisedAt = raisedAt;
    }

    /**
     * This customer's turn in a waiting list came and one of the thing is being held for them
     * until the moment on it. Raised once per promotion, because a promotion happens once.
     */
    static Notification aRewardIsBeingHeldFor(long customerId, String offerCode,
                                              String offerTitle, Instant lapsesAt,
                                              Instant raisedAt) {
        return new Notification(customerId, offerCode, offerTitle, lapsesAt, raisedAt);
    }

    /**
     * A fixed term whose day is coming.
     *
     * <p>The product travels as both its code and its name for the reason the reward's does: the
     * code addresses the product's own screen and the name is the word the sentence leads with, and
     * a name read out of the catalogue later would be the wrong one once the catalogue had been
     * reworded.
     *
     * <p>The day is insisted on and so is the product, because a warning about a maturity with no
     * date in it is a blank where the whole point goes — the same refusal the shortfall and the
     * bill's name make, and for the same reason.
     */
    static Notification aTermIsAboutToMature(long customerId, long savingsAccountId,
                                             String productCode, String productName,
                                             LocalDate maturesOn, Instant raisedAt) {
        return new Notification(customerId, savingsAccountId, productCode, productName, maturesOn,
                raisedAt);
    }

    private Notification(long customerId, long savingsAccountId, String productCode,
                         String productName, LocalDate maturesOn, Instant raisedAt) {
        if (productCode == null || productCode.isBlank() || productName == null
                || productName.isBlank() || maturesOn == null) {
            throw new IllegalArgumentException(
                    "a notification about a term coming up for maturity carries the product and the "
                            + "day it is up, and savings account " + savingsAccountId
                            + " has productCode=" + productCode + " productName=" + productName
                            + " maturesOn=" + maturesOn);
        }
        this.customerId = customerId;
        this.reason = NotificationReason.A_TERM_IS_ABOUT_TO_MATURE;
        this.savingsAccountId = savingsAccountId;
        this.productCode = productCode;
        this.productName = productName;
        this.occursOn = maturesOn;
        this.raisedAt = raisedAt;
    }

    /**
     * Notice given on an amount that has run its days.
     *
     * <p>What it still covers rather than what it was given on, because that is what the customer
     * can act on tonight: a notice on EUR 500 that has already paid for a withdrawal of EUR 200 is
     * good for EUR 300, and telling somebody five hundred is ready would be telling them something
     * that is not true. The act itself is in the notice's own history, which is where a history
     * belongs.
     */
    static Notification aNoticeHasBecomeReady(long customerId, long savingsAccountId, long noticeId,
                                              LocalDate readyOn, BigDecimal stillStanding,
                                              Instant raisedAt) {
        // The amount before the day, which is the other way round from every other private
        // constructor here and is forced rather than chosen: a transfer that did not happen already
        // takes (customer, account, identifier, day, amount, moment), and two constructors with one
        // erasure is not a thing a class can have.
        return new Notification(customerId, savingsAccountId, noticeId, stillStanding, readyOn,
                raisedAt);
    }

    private Notification(long customerId, long savingsAccountId, long noticeId,
                         BigDecimal stillStanding, LocalDate readyOn, Instant raisedAt) {
        if (readyOn == null || stillStanding == null) {
            throw new IllegalArgumentException(
                    "a notification about notice becoming ready carries the day it came free and "
                            + "what it still covers, and notice " + noticeId + " has readyOn="
                            + readyOn + " stillStanding=" + stillStanding);
        }
        this.customerId = customerId;
        this.reason = NotificationReason.A_NOTICE_HAS_BECOME_READY;
        this.savingsAccountId = savingsAccountId;
        this.noticeId = noticeId;
        this.occursOn = readyOn;
        this.amount = stillStanding;
        this.raisedAt = raisedAt;
    }

    /**
     * A product that has published terms better than the ones an account is living under.
     *
     * <p><strong>The caller has already made the judgement and this factory does not repeat
     * it.</strong> Whether a move counts as a betterment is {@link WhenTermsHaveBeenBettered}'s and
     * is argued there at length; a row is a record of a decision that was taken, and a constructor
     * that re-decided would be the second place the rule lived.
     *
     * <p>Two rates, in the shape {@link #aBillCouldNotBePaid} sends two figures: what is on offer
     * and what the account is on, so that the page can print both halves of the comparison rather
     * than a difference nobody can place. They are percentages rather than money and share the two
     * columns every other reason measures something against something in — a rate column would be
     * a fifteenth column null on thirteen reasons.
     *
     * <p>The sentences are quoted from Products and are joined here rather than anywhere else, so
     * that the one place they become a column is the one place they are split back.
     */
    static Notification termsHaveBeenBettered(long customerId, long savingsAccountId,
                                              String productCode, String productName, int version,
                                              BigDecimal theRateOnOffer, BigDecimal theRateYouAreOn,
                                              List<String> whatIsDifferent, Instant raisedAt) {
        return new Notification(customerId, savingsAccountId, productCode, productName, version,
                theRateOnOffer, theRateYouAreOn, whatIsDifferent, raisedAt);
    }

    private Notification(long customerId, long savingsAccountId, String productCode,
                         String productName, int version, BigDecimal theRateOnOffer,
                         BigDecimal theRateYouAreOn, List<String> whatIsDifferent,
                         Instant raisedAt) {
        if (productCode == null || productCode.isBlank() || productName == null
                || productName.isBlank() || theRateOnOffer == null || theRateYouAreOn == null
                || whatIsDifferent == null) {
            throw new IllegalArgumentException(
                    "a notification about a product bettering an account's terms carries the "
                            + "product, both rates and what is different, and savings account "
                            + savingsAccountId + " on version " + version + " has productCode="
                            + productCode + " productName=" + productName + " theRateOnOffer="
                            + theRateOnOffer + " theRateYouAreOn=" + theRateYouAreOn
                            + " whatIsDifferent=" + whatIsDifferent);
        }
        this.customerId = customerId;
        this.reason = NotificationReason.A_PRODUCT_HAS_BETTERED_YOUR_TERMS;
        this.savingsAccountId = savingsAccountId;
        this.productCode = productCode;
        this.productName = productName;
        this.termsVersion = version;
        this.amount = theRateOnOffer;
        this.balance = theRateYouAreOn;
        this.whatIsDifferent = joined(whatIsDifferent);
        this.raisedAt = raisedAt;
    }

    /**
     * A saving rule that fell due on a savings account that had been closed.
     *
     * <p>No money at all, unlike {@link #automaticTransferDidNotHappen}, and the absence is the
     * statement: the current account held whatever it held and none of it was the reason. A figure
     * here would be this row saying something Automation's own record does not — that record
     * carries nought moved and no shortfall for exactly this outcome.
     */
    static Notification anAutomaticTransferHadNowhereToGo(long customerId, long savingsAccountId,
                                                          long occurrenceId, LocalDate dueOn,
                                                          Instant raisedAt) {
        return new Notification(customerId, savingsAccountId, occurrenceId, dueOn, raisedAt);
    }

    private Notification(long customerId, long savingsAccountId, long occurrenceId, LocalDate dueOn,
                         Instant raisedAt) {
        if (dueOn == null) {
            throw new IllegalArgumentException(
                    "a notification about a transfer with nowhere to go carries the day it was due, "
                            + "and occurrence " + occurrenceId + " has none");
        }
        this.customerId = customerId;
        this.reason = NotificationReason.AN_AUTOMATIC_TRANSFER_HAD_NOWHERE_TO_GO;
        this.savingsAccountId = savingsAccountId;
        this.occurrenceId = occurrenceId;
        this.occursOn = dueOn;
        this.raisedAt = raisedAt;
    }

    /**
     * The sentences as one column, and the one place they are joined.
     *
     * <p>An empty list becomes an empty string rather than null, so that "nothing differs" and
     * "this reason is not about an agreement" stay two different readings of the column.
     */
    private static String joined(List<String> sentences) {
        return String.join(BETWEEN_SENTENCES, sentences);
    }

    /**
     * The sentences back out of the column, and the one place they are split.
     *
     * <p>Null answers an empty list, which is every reason that is not about an agreement. An empty
     * string does too, rather than the one-element list {@code String.split} would otherwise give
     * back — a list holding one empty sentence is a line the page would draw as a bullet with
     * nothing after it.
     */
    private static List<String> split(String sentences) {
        if (sentences == null || sentences.isEmpty()) {
            return List.of();
        }
        return List.of(sentences.split(BETWEEN_SENTENCES));
    }

    Long getId() {
        return id;
    }

    long getCustomerId() {
        return customerId;
    }

    NotificationReason getReason() {
        return reason;
    }

    Long getSavingsAccountId() {
        return savingsAccountId;
    }

    Long getCurrentAccountId() {
        return currentAccountId;
    }

    Long getBillId() {
        return billId;
    }

    String getBillName() {
        return billName;
    }

    Long getCategoryId() {
        return categoryId;
    }

    String getCategoryName() {
        return categoryName;
    }

    BigDecimal getBalance() {
        return balance;
    }

    Long getArrears() {
        return arrears;
    }

    Long getDepositId() {
        return depositId;
    }

    Long getOccurrenceId() {
        return occurrenceId;
    }

    BigDecimal getAmount() {
        return amount;
    }

    Long getPoints() {
        return points;
    }

    LocalDate getOccursOn() {
        return occursOn;
    }

    String getOfferCode() {
        return offerCode;
    }

    String getOfferTitle() {
        return offerTitle;
    }

    Instant getLapsesAt() {
        return lapsesAt;
    }

    Long getNoticeId() {
        return noticeId;
    }

    String getProductCode() {
        return productCode;
    }

    String getProductName() {
        return productName;
    }

    Integer getTermsVersion() {
        return termsVersion;
    }

    /** What the two agreements say differently, read back out of the one column that holds it. */
    List<String> getWhatIsDifferent() {
        return split(whatIsDifferent);
    }

    Instant getRaisedAt() {
        return raisedAt;
    }

    Instant getReadAt() {
        return readAt;
    }

    /**
     * Marks this notification as looked at, as at the given moment, and answers whether that moment
     * is the one it now carries.
     *
     * <p>The notification decides, so that nothing outside can read one twice: one that has already
     * been read answers {@code false} and keeps the moment it was originally read at, which is what
     * makes a second call to mark a customer's notifications read a call that changes nothing. The
     * same shape {@code PointsCredit.expire} has, and for the same reason — a moment set once is a
     * record of when something happened, and overwriting it would turn it into a record of when it
     * was last asked about.
     *
     * <p>Answering whether it changed rather than answering nothing, because the count of what was
     * actually marked is what the caller logs: a line saying nine were marked when eight of them had
     * been read yesterday would be a line a reviewer could not check.
     */
    boolean read(Instant readAt) {
        if (this.readAt != null) {
            return false;
        }
        this.readAt = readAt;
        return true;
    }
}
