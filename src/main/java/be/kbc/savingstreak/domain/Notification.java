package be.kbc.savingstreak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationKind kind;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 300)
    private String body;

    /** Points at stake, for the loyalty notifications. */
    @Column(nullable = false)
    private int points;

    @Column(name = "account_id")
    private Long accountId;

    /**
     * Stops the same thing being announced twice. An anniversary approaching is noticed on
     * every visit, but should only ever produce one notification.
     */
    @Column(name = "dedupe_key", unique = true)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // for JPA
    }

    public Notification(Long memberId, NotificationKind kind, String title, String body, int points,
                        Long accountId, String dedupeKey, Instant createdAt) {
        this.memberId = memberId;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.points = points;
        this.accountId = accountId;
        this.dedupeKey = dedupeKey;
        this.createdAt = createdAt;
    }

    public void markRead(Instant moment) {
        if (readAt == null) {
            readAt = moment;
        }
    }

    public Long getId() {
        return id;
    }

    public NotificationKind getKind() {
        return kind;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public int getPoints() {
        return points;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public boolean isUnread() {
        return readAt == null;
    }
}
