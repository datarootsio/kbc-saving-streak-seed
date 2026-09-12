package be.kbc.savingstreak;

import static org.assertj.core.api.Assertions.assertThat;

import be.kbc.savingstreak.config.DemoDataSeeder;
import be.kbc.savingstreak.domain.Account;
import be.kbc.savingstreak.domain.AccountType;
import be.kbc.savingstreak.repo.AccountRepository;
import be.kbc.savingstreak.service.AccountTimelineService;
import be.kbc.savingstreak.web.dto.AccountTimeline;
import be.kbc.savingstreak.web.dto.TimelineEvent;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** The year-behind, year-ahead bar drawn under each savings account. */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:sqlite:target/test-timeline.db")
class AccountTimelineTest {

    @Autowired
    private AccountTimelineService timelines;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private Clock clock;

    @Autowired
    private ZoneId zone;

    @BeforeEach
    void seedDemoData() {
        seeder.reset();
    }

    @Test
    void theCurrentAccountHasNoTimeline() {
        assertThat(timelines.forAccount(accountNamed("Current account"))).isNull();
    }

    @Test
    void theWindowRunsAYearEitherSideOfTodayWithNowInTheMiddle() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        AccountTimeline timeline = timelines.forAccount(accountNamed("Rainy day fund"));

        assertThat(timeline.today()).isEqualTo(today);
        assertThat(timeline.from()).isEqualTo(today.minusMonths(12));
        assertThat(timeline.to()).isEqualTo(today.plusMonths(12));
        assertThat(timeline.nowPosition()).isEqualTo(50);
    }

    @Test
    void moneyMovingInAndOutOfTheAccountIsPlottedWithItsAmount() {
        AccountTimeline timeline = timelines.forAccount(accountNamed("Rainy day fund"));

        assertThat(kinds(timeline, "MONEY_IN"))
                .extracting(event -> event.amount().toPlainString())
                .containsExactly("300.00", "200.00", "100.00");
        assertThat(kinds(timeline, "MONEY_OUT"))
                .singleElement()
                .satisfies(event -> assertThat(event.amount().toPlainString()).isEqualTo("40.00"));
        // the largest movement is what the bar heights are scaled against
        assertThat(timeline.largestMovement().toPlainString()).isEqualTo("300.00");
    }

    @Test
    void anotherAccountsMovementsAreLeftOut() {
        AccountTimeline timeline = timelines.forAccount(accountNamed("Lisbon travel fund"));

        assertThat(timeline.events())
                .filteredOn(event -> event.kind().startsWith("MONEY"))
                .hasSize(3)
                .allSatisfy(event -> assertThat(event.label()).doesNotContain("300.00"));
    }

    @Test
    void everyDepositBringsAnExpiryAndAnAnniversaryTwelveMonthsLater() {
        AccountTimeline timeline = timelines.forAccount(accountNamed("Rainy day fund"));
        LocalDate oldestDeposit = kinds(timeline, "MONEY_IN").get(0).on();

        assertThat(kinds(timeline, "POINTS_EXPIRING"))
                .anySatisfy(event -> {
                    assertThat(event.on()).isEqualTo(oldestDeposit.plusMonths(12));
                    assertThat(event.points()).isEqualTo(300);
                });
        assertThat(kinds(timeline, "BONUS_DUE"))
                .anySatisfy(event -> {
                    assertThat(event.on()).isEqualTo(oldestDeposit.plusMonths(12));
                    assertThat(event.points()).isEqualTo(30);
                });
    }

    @Test
    void aPaidBonusAndTheLapsedPointsOfTheSameDepositBothShow() {
        AccountTimeline timeline = timelines.forAccount(accountNamed("Finn's savings"));

        // the year-old deposit was paid its first anniversary, and its own points lapsed
        assertThat(kinds(timeline, "BONUS_PAID"))
                .singleElement()
                .satisfies(event -> assertThat(event.points()).isEqualTo(40));
        assertThat(kinds(timeline, "POINTS_LAPSED"))
                .singleElement()
                .satisfies(event -> assertThat(event.points()).isEqualTo(400));
    }

    @Test
    void movementsOlderThanTheWindowAreCountedRatherThanDrawn() {
        AccountTimeline timeline = timelines.forAccount(accountNamed("Finn's savings"));

        // the EUR 400 deposit was 75 weeks ago, so it falls before the window
        assertThat(timeline.earlierMovements()).isEqualTo(1);
        assertThat(kinds(timeline, "MONEY_IN"))
                .singleElement()
                .satisfies(event -> assertThat(event.amount().toPlainString()).isEqualTo("75.00"));
    }

    @Test
    void theSummarySaysWhatMaturesSoonest() {
        AccountTimeline timeline = timelines.forAccount(accountNamed("Finn's savings"));

        assertThat(timeline.expiringNextPoints()).isEqualTo(75);
        assertThat(timeline.nextBonusPoints()).isEqualTo(7);
        assertThat(timeline.expiringNextOn()).isEqualTo(timeline.nextBonusOn());
    }

    @Test
    void everyEventSitsInsideTheBarAndIsOrderedByDate() {
        for (Account account : accounts.findAllByOrderBySortOrderAsc()) {
            if (account.getType() != AccountType.SAVINGS) {
                continue;
            }
            AccountTimeline timeline = timelines.forAccount(account);
            assertThat(timeline.events()).isNotEmpty();
            assertThat(timeline.events()).allSatisfy(event -> {
                assertThat(event.position()).isBetween(0, 100);
                assertThat(event.on()).isBetween(timeline.from(), timeline.to());
            });
            assertThat(timeline.events()).extracting(TimelineEvent::on).isSorted();
        }
    }

    private List<TimelineEvent> kinds(AccountTimeline timeline, String kind) {
        return timeline.events().stream().filter(event -> event.kind().equals(kind)).toList();
    }

    private Account accountNamed(String name) {
        return accounts.findAll().stream()
                .filter(account -> account.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
