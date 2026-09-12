package be.kbc.savingstreak;

import static org.assertj.core.api.Assertions.assertThat;

import be.kbc.savingstreak.config.DemoDataSeeder;
import be.kbc.savingstreak.domain.Account;
import be.kbc.savingstreak.domain.AccountType;
import be.kbc.savingstreak.domain.Notification;
import be.kbc.savingstreak.domain.NotificationKind;
import be.kbc.savingstreak.repo.AccountRepository;
import be.kbc.savingstreak.repo.MemberRepository;
import be.kbc.savingstreak.service.BankingService;
import be.kbc.savingstreak.service.NotificationService;
import be.kbc.savingstreak.web.dto.TransferRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:sqlite:target/test-notifications.db")
class NotificationsTest {

    @Autowired
    private NotificationService notifications;

    @Autowired
    private BankingService banking;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private MemberRepository members;

    @Autowired
    private DemoDataSeeder seeder;

    private Long memberId;

    @BeforeEach
    void seedDemoData() {
        seeder.reset();
        memberId = members.findFirstByPrimaryCustomerTrue().orElseThrow().getId();
    }

    @Test
    void anAnniversaryComingUpIsAnnouncedOnce() {
        // the seeded EUR 75 deposit reaches its year in 19 days
        assertThat(notifications.evaluate(memberId)).isPositive();
        assertThat(kinds(NotificationKind.BONUS_VESTING_SOON))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getPoints()).isEqualTo(7);
                    assertThat(notification.getTitle()).contains("vest in 19 days");
                    assertThat(notification.getBody()).contains("Finn's savings");
                });

        // looking again changes nothing
        notifications.evaluate(memberId);
        notifications.evaluate(memberId);
        assertThat(kinds(NotificationKind.BONUS_VESTING_SOON)).hasSize(1);
    }

    @Test
    void aBalanceCrossingDownwardsIsAnnouncedOnCrossingOnly() {
        Account current = accountOf(AccountType.CURRENT);
        notifications.setThreshold(current, 240_000L, null);
        notifications.evaluate(memberId);
        assertThat(kinds(NotificationKind.BALANCE_BELOW)).isEmpty();

        // 2431.58 -> 2331.58, under the 2400 line
        banking.transfer(new TransferRequest(current.getId(), savingsId(), new BigDecimal("100.00")));
        notifications.evaluate(memberId);
        assertThat(kinds(NotificationKind.BALANCE_BELOW))
                .singleElement()
                .satisfies(notification -> assertThat(notification.getBody()).contains("2331.58"));

        // still under, so nothing new
        banking.transfer(new TransferRequest(current.getId(), savingsId(), new BigDecimal("10.00")));
        notifications.evaluate(memberId);
        assertThat(kinds(NotificationKind.BALANCE_BELOW)).hasSize(1);

        // back over the line and under again: that is a second crossing
        banking.transfer(new TransferRequest(savingsId(), current.getId(), new BigDecimal("200.00")));
        notifications.evaluate(memberId);
        banking.transfer(new TransferRequest(current.getId(), savingsId(), new BigDecimal("200.00")));
        notifications.evaluate(memberId);
        assertThat(kinds(NotificationKind.BALANCE_BELOW)).hasSize(2);
    }

    @Test
    void aSavingsAccountPassingItsLevelIsAnnounced() {
        Account savings = accountOf(AccountType.SAVINGS);
        notifications.setThreshold(savings, null, 530_000L);
        notifications.evaluate(memberId);
        assertThat(kinds(NotificationKind.BALANCE_ABOVE)).isEmpty();

        banking.transfer(new TransferRequest(accountOf(AccountType.CURRENT).getId(), savings.getId(),
                new BigDecimal("100.00")));
        notifications.evaluate(memberId);

        assertThat(kinds(NotificationKind.BALANCE_ABOVE))
                .singleElement()
                .satisfies(notification -> assertThat(notification.getTitle()).contains("passed"));
    }

    @Test
    void clearingAnAlertStopsIt() {
        Account current = accountOf(AccountType.CURRENT);
        notifications.setThreshold(current, 240_000L, null);
        notifications.setThreshold(current, null, null);

        banking.transfer(new TransferRequest(current.getId(), savingsId(), new BigDecimal("500.00")));
        notifications.evaluate(memberId);

        assertThat(kinds(NotificationKind.BALANCE_BELOW)).isEmpty();
    }

    @Test
    void takingOutMoneyThatWasAboutToVestIsReported() {
        Account finns = accountNamed("Finn's savings");

        // 475 clears the older position and reaches the one whose year is 19 days away
        banking.transfer(new TransferRequest(finns.getId(), accountOf(AccountType.CURRENT).getId(),
                new BigDecimal("475.00")));

        assertThat(kinds(NotificationKind.BONUS_FORFEITED))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getPoints()).isEqualTo(7);
                    assertThat(notification.getBody()).contains("19 days");
                    assertThat(notification.getBody()).contains("Finn's savings");
                });
    }

    @Test
    void aWithdrawalThatOnlyTouchesAFarOffAnniversaryIsNotReported() {
        Account finns = accountNamed("Finn's savings");

        // the oldest position here is 7 months from its next anniversary
        banking.transfer(new TransferRequest(finns.getId(), accountOf(AccountType.CURRENT).getId(),
                new BigDecimal("75.00")));

        assertThat(kinds(NotificationKind.BONUS_FORFEITED)).isEmpty();
    }

    @Test
    void readingClearsTheUnreadCount() {
        notifications.evaluate(memberId);
        assertThat(notifications.unreadCount(memberId)).isPositive();

        int marked = notifications.markAllRead(memberId);

        assertThat(marked).isPositive();
        assertThat(notifications.unreadCount(memberId)).isZero();
        assertThat(notifications.feed(memberId)).isNotEmpty()
                .allSatisfy(notification -> assertThat(notification.isUnread()).isFalse());
    }

    private List<Notification> kinds(NotificationKind kind) {
        return notifications.feed(memberId).stream()
                .filter(notification -> notification.getKind() == kind)
                .toList();
    }

    private Account accountOf(AccountType type) {
        return accounts.findAllByOrderBySortOrderAsc().stream()
                .filter(account -> account.getType() == type)
                .findFirst()
                .orElseThrow();
    }

    private Account accountNamed(String name) {
        return accounts.findAll().stream()
                .filter(account -> account.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private Long savingsId() {
        return accountOf(AccountType.SAVINGS).getId();
    }
}
