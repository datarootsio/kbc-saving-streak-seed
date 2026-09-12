package be.kbc.savingstreak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * The single logged-in customer. Holds the loyalty state: points wallet and the
 * weekly saving streak.
 */
@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** True for the customer this demo is signed in as; the others are contacts. */
    @Column(name = "is_primary", nullable = false)
    private boolean primaryCustomer;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    /** Number of consecutive calendar weeks with at least one deposit into savings. */
    @Column(name = "streak_weeks", nullable = false)
    private int streakWeeks;

    @Column(name = "best_streak_weeks", nullable = false)
    private int bestStreakWeeks;

    /** Monday of the week of the most recent deposit, null when never deposited. */
    @Column(name = "last_deposit_week")
    private LocalDate lastDepositWeek;

    /**
     * The highest total ever held across all savings accounts, in eurocents. Points are only
     * paid on savings above this mark, so moving the same money in and out cannot earn twice.
     * Nullable so the column can be added to an existing database.
     */
    @Column(name = "savings_peak_cents")
    private Long savingsPeakCents;

    protected Member() {
        // for JPA
    }

    public Member(String firstName, String lastName) {
        this(firstName, lastName, false);
    }

    public Member(String firstName, String lastName, boolean primaryCustomer) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.primaryCustomer = primaryCustomer;
    }

    public boolean isPrimaryCustomer() {
        return primaryCustomer;
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    public String initials() {
        return ("" + firstName.charAt(0) + lastName.charAt(0)).toUpperCase();
    }

    /**
     * Rolls the streak forward for a week that just reached the weekly minimum: the same week
     * keeps the streak, the next week extends it, a gap starts over at one.
     */
    public void secureWeek(LocalDate weekStart) {
        if (lastDepositWeek == null || lastDepositWeek.isBefore(weekStart.minusWeeks(1))) {
            streakWeeks = 1;
        } else if (lastDepositWeek.isBefore(weekStart)) {
            streakWeeks++;
        }
        lastDepositWeek = weekStart;
        bestStreakWeeks = Math.max(bestStreakWeeks, streakWeeks);
    }

    /**
     * The streak as it counts right now. A streak whose last secured week is older than last
     * week is already broken, even though the next deposit is what will reset the stored value.
     */
    public int effectiveStreakWeeks(LocalDate currentWeekStart) {
        if (lastDepositWeek == null || lastDepositWeek.isBefore(currentWeekStart.minusWeeks(1))) {
            return 0;
        }
        return streakWeeks;
    }

    /**
     * The peak to measure new savings against. Never below the current total, which keeps a
     * missing or stale mark from over-rewarding the next deposit.
     */
    public long savingsPeakAtLeast(long currentTotalCents) {
        return Math.max(savingsPeakCents == null ? 0L : savingsPeakCents, currentTotalCents);
    }

    public void raiseSavingsPeak(long totalCents) {
        if (savingsPeakCents == null || totalCents > savingsPeakCents) {
            savingsPeakCents = totalCents;
        }
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public int getStreakWeeks() {
        return streakWeeks;
    }

    public int getBestStreakWeeks() {
        return bestStreakWeeks;
    }

    public LocalDate getLastDepositWeek() {
        return lastDepositWeek;
    }
}
