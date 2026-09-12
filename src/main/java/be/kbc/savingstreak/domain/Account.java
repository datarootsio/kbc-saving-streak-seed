package be.kbc.savingstreak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String iban;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    /** Balance in eurocents so we never lose money to floating point. */
    @Column(name = "balance_cents", nullable = false)
    private long balanceCents;

    /** Optional savings target in eurocents. */
    @Column(name = "goal_cents")
    private Long goalCents;

    /** Short subtitle shown on the account card, e.g. "Buffer voor later". */
    @Column(name = "subtitle")
    private String subtitle;

    @Column(name = "interest_bp")
    private Integer interestBasisPoints;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Warn me when the balance drops below this, in eurocents. Null means no alert. */
    @Column(name = "alert_below_cents")
    private Long alertBelowCents;

    /** Tell me when the balance climbs above this, in eurocents. Null means no alert. */
    @Column(name = "alert_above_cents")
    private Long alertAboveCents;

    /**
     * Whether each alert is currently breached, so a notification fires on the crossing
     * rather than on every visit while the balance sits the wrong side of the line.
     */
    @Column(name = "below_breached")
    private Boolean belowBreached;

    @Column(name = "above_breached")
    private Boolean aboveBreached;

    protected Account() {
        // for JPA
    }

    public Account(String name, String iban, AccountType type, long balanceCents, Long goalCents,
                   String subtitle, Integer interestBasisPoints, int sortOrder) {
        this.name = name;
        this.iban = iban;
        this.type = type;
        this.balanceCents = balanceCents;
        this.goalCents = goalCents;
        this.subtitle = subtitle;
        this.interestBasisPoints = interestBasisPoints;
        this.sortOrder = sortOrder;
    }

    public void deposit(long cents) {
        balanceCents += cents;
    }

    public void withdraw(long cents) {
        balanceCents -= cents;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIban() {
        return iban;
    }

    public AccountType getType() {
        return type;
    }

    public long getBalanceCents() {
        return balanceCents;
    }

    public Long getGoalCents() {
        return goalCents;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public Integer getInterestBasisPoints() {
        return interestBasisPoints;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Long getAlertBelowCents() {
        return alertBelowCents;
    }

    public Long getAlertAboveCents() {
        return alertAboveCents;
    }

    public void alertBelow(Long cents) {
        this.alertBelowCents = cents;
        this.belowBreached = null;
    }

    public void alertAbove(Long cents) {
        this.alertAboveCents = cents;
        this.aboveBreached = null;
    }

    public boolean wasBelowBreached() {
        return Boolean.TRUE.equals(belowBreached);
    }

    public boolean wasAboveBreached() {
        return Boolean.TRUE.equals(aboveBreached);
    }

    public void recordBelowBreached(boolean breached) {
        this.belowBreached = breached;
    }

    public void recordAboveBreached(boolean breached) {
        this.aboveBreached = breached;
    }
}
