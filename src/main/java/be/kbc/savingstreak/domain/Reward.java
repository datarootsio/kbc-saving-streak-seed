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
@Table(name = "reward")
public class Reward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String partner;

    @Column(nullable = false, length = 400)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RewardCategory category;

    @Column(name = "points_cost", nullable = false)
    private int pointsCost;

    /** Indicative retail value in eurocents, shown as "waarde". */
    @Column(name = "value_cents", nullable = false)
    private long valueCents;

    /** Name of the inline SVG icon rendered by the frontend. */
    @Column(nullable = false)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Reward() {
        // for JPA
    }

    public Reward(String title, String partner, String description, RewardCategory category,
                  int pointsCost, long valueCents, String icon, int sortOrder) {
        this.title = title;
        this.partner = partner;
        this.description = description;
        this.category = category;
        this.pointsCost = pointsCost;
        this.valueCents = valueCents;
        this.icon = icon;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getPartner() {
        return partner;
    }

    public String getDescription() {
        return description;
    }

    public RewardCategory getCategory() {
        return category;
    }

    public int getPointsCost() {
        return pointsCost;
    }

    public long getValueCents() {
        return valueCents;
    }

    public String getIcon() {
        return icon;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
