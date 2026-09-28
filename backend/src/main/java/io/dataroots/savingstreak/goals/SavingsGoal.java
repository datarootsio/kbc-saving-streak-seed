package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * One thing a savings account is being saved towards: a name, a target, a deadline it may or may not
 * have, and a place in the account's order of importance.
 *
 * <p>It holds no money. The savings balance stays the one true figure it already is — the sum of
 * what the deposits in the account still hold — and a goal is a claim against it rather than a purse
 * of its own. What each goal has been allocated is a later slice and is kept as a ledger of moves,
 * not as a column here, for the reason every derived figure in this application is derived: two
 * stored figures that have to agree eventually stop agreeing.
 *
 * <p><strong>The savings account is an identifier and not a relation.</strong> Every other module
 * that belongs to an account holds it the same way, and here it is load-bearing rather than
 * stylistic: this module reads no other module, so it has no handle on {@code SavingsAccount} and
 * wants none. Whether the identifier names an account that exists is settled before a goal is ever
 * asked for, by whoever holds the account.
 *
 * <p><strong>The rank is 1 for the most important goal and has no gaps and no ties.</strong> It is
 * null exactly when the goal is not {@link GoalState#LIVE}: an abandoned goal is not competing for
 * anything, so it holds no place in a competition. {@link GoalsService} is what keeps the live ranks
 * a run of 1..n — the column cannot, and a column that could would still not know what to renumber
 * when one goal in the middle leaves.
 *
 * <p><strong>The pinned weekly amount is the one figure here the customer states rather than the
 * application derives.</strong> Everything else this feature reports about a goal — what it holds,
 * what it still needs, what it is given each week, when it arrives — is worked out on every read,
 * and a pin cannot be, because it is a commitment somebody made rather than a consequence of
 * anything. It is null on a goal nobody has pinned, which is the ordinary case and means "the engine
 * decides".
 *
 * <p>The deadline is optional and soft. An emergency fund genuinely has a target with no date, and a
 * house deposit that slips three weeks is still the thing being saved for. Nothing anywhere in this
 * module closes a goal because a date passed; a deadline is a thing to measure against, and that is
 * all it is.
 *
 * <p>{@code abandonedAt} is a moment rather than a flag, set once, exactly as a notification's
 * {@code readAt} is: a moment is a record of when something happened, and one that could be
 * overwritten would be a record of when it was last asked about.
 *
 * <p>Package-private, like the repository that reads it: a row of this module's record is not
 * something another module gets a handle on. {@link RecordedGoal} is what leaves.
 */
@Entity
class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long savingsAccountId;

    private String name;

    private BigDecimal target;

    /** The day it is wanted by, and null for a goal with no date — which is an ordinary goal. */
    private LocalDate deadline;

    /**
     * Where it stands in the order of importance: 1 is the most important, and null for a goal that
     * has left the order.
     *
     * <p>Under a column name of its own because {@code rank} is a window function in SQL:2003 and in
     * SQLite, and a generated statement naming an unquoted column {@code rank} is one dialect
     * upgrade away from failing to parse. The Java side keeps the word, because the word is what the
     * domain calls it.
     */
    @Column(name = "goal_rank")
    private Integer rank;

    /**
     * Written as its name rather than as its position, so a row means the same thing after a value
     * is added to the enum, and so a reviewer reading the table with a SQLite client sees the state
     * rather than an ordinal to look up.
     */
    /**
     * What the customer has committed to putting into this goal every week, and null for a goal
     * they have committed nothing to — which is the ordinary case.
     *
     * <p>A column, unlike every other figure this feature reports, because it is the one of them
     * nothing can derive: it is a sentence the customer said. What a goal is <em>given</em> each
     * week stays derived from it by {@link HowTheWeeklyMoneyIsSpent}, and the two are not the same
     * figure — a pin larger than what is left of the capacity is honoured only as far as the
     * capacity reaches.
     *
     * <p>Null is "the engine decides", not "nothing a week". A pin of zero would be a commitment to
     * save nothing into this goal, which is indistinguishable downstream from the goal simply losing
     * the competition, so it is refused and the way to say it is to unpin.
     */
    private BigDecimal pinnedWeeklyAmount;

    @Enumerated(EnumType.STRING)
    private GoalState state;

    private Instant createdAt;

    /** When it was given up on, and null while it is still being saved towards. */
    private Instant abandonedAt;

    protected SavingsGoal() {
        // for JPA
    }

    SavingsGoal(long savingsAccountId, String name, BigDecimal target, LocalDate deadline,
                int rank, Instant createdAt) {
        this.savingsAccountId = savingsAccountId;
        this.name = name;
        this.target = target;
        this.deadline = deadline;
        this.rank = rank;
        this.state = GoalState.LIVE;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    long getSavingsAccountId() {
        return savingsAccountId;
    }

    String getName() {
        return name;
    }

    BigDecimal getTarget() {
        return target;
    }

    LocalDate getDeadline() {
        return deadline;
    }

    Integer getRank() {
        return rank;
    }

    BigDecimal getPinnedWeeklyAmount() {
        return pinnedWeeklyAmount;
    }

    GoalState getState() {
        return state;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getAbandonedAt() {
        return abandonedAt;
    }

    boolean isLive() {
        return state == GoalState.LIVE;
    }

    void rename(String name) {
        this.name = name;
    }

    void retarget(BigDecimal target) {
        this.target = target;
    }

    /** A new day to want it by, or null to say there is no longer a day it is wanted by. */
    void reschedule(LocalDate deadline) {
        this.deadline = deadline;
    }

    /**
     * Commits an amount to it every week, replacing whatever was committed before.
     *
     * <p>Whether the figure is an amount of money is settled before this is called, and whether the
     * account can afford it is settled nowhere: a pin larger than the whole weekly capacity is a
     * legal thing for a customer to say, and what it costs the goals below it is what the plan
     * answers rather than what this refuses.
     */
    void pinWeeklyAmount(BigDecimal pinnedWeeklyAmount) {
        this.pinnedWeeklyAmount = pinnedWeeklyAmount;
    }

    /**
     * Takes the commitment off again, and answers whether there was one to take.
     *
     * <p>The goal decides, so that unpinning a goal nobody pinned is an honest "nothing changed"
     * rather than a refusal: there is at most one pin, and a customer asking for it to be gone when
     * it already is has got what they asked for. The answer is what lets the service log which of
     * the two happened.
     */
    boolean unpinWeeklyAmount() {
        boolean wasPinned = this.pinnedWeeklyAmount != null;
        this.pinnedWeeklyAmount = null;
        return wasPinned;
    }

    /**
     * Puts it at this place in the order. Only the service calls this, and only while renumbering a
     * whole account's live goals: a rank set on its own is how a total order acquires a tie or a
     * gap.
     */
    void rankedAt(int rank) {
        this.rank = rank;
    }

    /**
     * Gives it up, as at the given moment, and answers whether that moment is the one it now carries.
     *
     * <p>The goal decides, so nothing outside can abandon one twice: one already abandoned answers
     * {@code false} and keeps the moment it was originally given up on. It also gives up its rank in
     * the same breath, because leaving the order and keeping a place in it are not two things that
     * can be true at once — closing the gap the departure leaves is the service's part, and it is
     * the only part it could be.
     */
    boolean abandon(Instant abandonedAt) {
        if (this.state == GoalState.ABANDONED) {
            return false;
        }
        this.state = GoalState.ABANDONED;
        this.abandonedAt = abandonedAt;
        this.rank = null;
        return true;
    }
}
