package be.kbc.savingstreak;

import static org.assertj.core.api.Assertions.assertThat;

import be.kbc.savingstreak.config.DemoDataSeeder;
import be.kbc.savingstreak.domain.PointsSource;
import be.kbc.savingstreak.domain.SavingsPosition;
import be.kbc.savingstreak.repo.PointsLotRepository;
import be.kbc.savingstreak.repo.SavingsPositionRepository;
import be.kbc.savingstreak.service.LoyaltyService;
import be.kbc.savingstreak.service.PointsWallet;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * The loyalty rate, seen from different points in time. Settlement takes the moment as a
 * parameter, so anniversaries can be tested without touching the clock.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:sqlite:target/test-loyalty.db")
class LoyaltyRateTest {

    private static final long ACCOUNT = 42L;
    private static final long OTHER_ACCOUNT = 43L;

    @Autowired
    private LoyaltyService loyalty;

    @Autowired
    private PointsWallet wallet;

    @Autowired
    private SavingsPositionRepository positions;

    @Autowired
    private PointsLotRepository lots;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private ZoneId zone;

    private Instant opened;

    @BeforeEach
    void emptyEverything() {
        seeder.reset();
        positions.deleteAll();
        lots.deleteAll();
        opened = Instant.parse("2026-01-15T10:00:00Z");
    }

    private Instant plusMonths(Instant from, int months) {
        return from.atZone(zone).plusMonths(months).toInstant();
    }

    @Test
    void anAnniversaryPaysTenPercentOfTheDepositsBasePoints() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened); // EUR 300 -> 300 base points

        assertThat(loyalty.settleDue(plusMonths(opened, 11))).isZero();
        assertThat(loyalty.settleDue(plusMonths(opened, 12))).isEqualTo(30);
    }

    @Test
    void settlingTwiceDoesNotPayTwice() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);
        Instant firstAnniversary = plusMonths(opened, 12);

        assertThat(loyalty.settleDue(firstAnniversary)).isEqualTo(30);
        assertThat(loyalty.settleDue(firstAnniversary)).isZero();
        assertThat(loyalty.settleDue(plusMonths(opened, 23))).isZero();
    }

    @Test
    void everyFurtherYearPaysAgain() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);

        assertThat(loyalty.settleDue(plusMonths(opened, 12))).isEqualTo(30);
        assertThat(loyalty.settleDue(plusMonths(opened, 24))).isEqualTo(30);
        assertThat(loyalty.settleDue(plusMonths(opened, 36))).isEqualTo(30);
        assertThat(onlyPosition().getAnniversariesPaid()).isEqualTo(3);
        assertThat(onlyPosition().getLoyaltyPointsPaid()).isEqualTo(90);
    }

    @Test
    void yearsMissedWhileNobodyLookedAreAllPaidAtOnce() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);

        assertThat(loyalty.settleDue(plusMonths(opened, 37))).isEqualTo(90);
        assertThat(onlyPosition().getAnniversariesPaid()).isEqualTo(3);
    }

    @Test
    void eachBonusIsDatedToItsAnniversaryAndRunsItsOwnTwelveMonths() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);
        Instant anniversary = plusMonths(opened, 12);
        loyalty.settleDue(anniversary);

        assertThat(lots.findAll()).singleElement().satisfies(lot -> {
            assertThat(lot.getSource()).isEqualTo(PointsSource.LOYALTY_BONUS);
            assertThat(lot.getEarnedAt()).isEqualTo(anniversary);
            assertThat(lot.getExpiresAt()).isEqualTo(wallet.expiryFor(anniversary));
        });
    }

    @Test
    void withdrawingBeforeAnAnniversaryForfeitsThatYearForWhatLeft() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);
        assertThat(loyalty.settleDue(plusMonths(opened, 12))).isEqualTo(30);

        // two thirds taken out during the second year
        loyalty.withdrawPrincipal(ACCOUNT, 20_000);

        assertThat(loyalty.settleDue(plusMonths(opened, 24))).isEqualTo(10);
        // the bonus already paid on the first anniversary stands
        assertThat(onlyPosition().getLoyaltyPointsPaid()).isEqualTo(40);
    }

    @Test
    void aFullyWithdrawnDepositStopsEarningButKeepsWhatItWasPaid() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);
        loyalty.settleDue(plusMonths(opened, 12));

        loyalty.withdrawPrincipal(ACCOUNT, 30_000);

        assertThat(loyalty.settleDue(plusMonths(opened, 60))).isZero();
        assertThat(onlyPosition().getLoyaltyPointsPaid()).isEqualTo(30);
        assertThat(onlyPosition().isOpen()).isFalse();
        assertThat(loyalty.nextAnniversary(onlyPosition())).isNull();
    }

    @Test
    void aWithdrawalTakesFromTheOldestDepositFirst() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);                       // oldest
        loyalty.openPosition(2L, ACCOUNT, 10_000, plusMonths(opened, 6));

        loyalty.withdrawPrincipal(ACCOUNT, 25_000);

        assertThat(positionOf(1L).getPrincipalLeftCents()).isEqualTo(5_000);
        assertThat(positionOf(2L).getPrincipalLeftCents()).isEqualTo(10_000);
    }

    @Test
    void aWithdrawalRollsOnToTheNextDepositOnceTheOldestIsEmpty() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);
        loyalty.openPosition(2L, ACCOUNT, 10_000, plusMonths(opened, 6));

        loyalty.withdrawPrincipal(ACCOUNT, 34_000);

        assertThat(positionOf(1L).getPrincipalLeftCents()).isZero();
        assertThat(positionOf(2L).getPrincipalLeftCents()).isEqualTo(6_000);
    }

    @Test
    void aWithdrawalOnlyTouchesTheAccountItCameFrom() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);
        loyalty.openPosition(2L, OTHER_ACCOUNT, 10_000, opened);

        loyalty.withdrawPrincipal(OTHER_ACCOUNT, 10_000);

        assertThat(positionOf(1L).getPrincipalLeftCents()).isEqualTo(30_000);
        assertThat(positionOf(2L).getPrincipalLeftCents()).isZero();
    }

    @Test
    void movingToAnotherSavingsAccountKeepsTheClockRunning() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);

        loyalty.movePrincipal(ACCOUNT, OTHER_ACCOUNT, 30_000);

        assertThat(onlyPosition().getAccountId()).isEqualTo(OTHER_ACCOUNT);
        assertThat(onlyPosition().getOpenedAt()).isEqualTo(opened);
        assertThat(loyalty.settleDue(plusMonths(opened, 12))).isEqualTo(30);
    }

    @Test
    void movingPartOfADepositSplitsItAndBothHalvesKeepTheClock() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);
        loyalty.settleDue(plusMonths(opened, 12));

        loyalty.movePrincipal(ACCOUNT, OTHER_ACCOUNT, 10_000);

        assertThat(positions.findAll()).hasSize(2)
                .allSatisfy(position -> {
                    assertThat(position.getOpenedAt()).isEqualTo(opened);
                    assertThat(position.getAnniversariesPaid()).isEqualTo(1);
                });
        // the whole EUR 300 is still sitting still, so the second year pays in full
        assertThat(loyalty.settleDue(plusMonths(opened, 24))).isEqualTo(30);
    }

    @Test
    void theBonusComesFromTheMoneyNotFromThePointsItEarned() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);
        // the deposit's own points are earned and then spent to nothing
        wallet.credit(memberId(), 300, opened, 1L);
        wallet.spend(memberId(), 300, opened);

        // the anniversary still pays, because the EUR 300 never left
        assertThat(loyalty.settleDue(plusMonths(opened, 12))).isEqualTo(30);
    }

    @Test
    void aDepositLeftAloneHoldsOneYearOfBonusAtATimeRatherThanPilingUp() {
        loyalty.openPosition(1L, ACCOUNT, 30_000, opened);

        for (int year = 1; year <= 4; year++) {
            Instant anniversary = plusMonths(opened, 12 * year);
            assertThat(loyalty.settleDue(anniversary)).isEqualTo(30);
            // each bonus runs its own 12 months, so the previous one lapses as this one lands
            assertThat(wallet.balanceAt(memberId(), anniversary)).isEqualTo(30);
        }
        assertThat(wallet.everEarnedFromLoyalty(memberId())).isEqualTo(120);
        assertThat(onlyPosition().getPrincipalLeftCents()).isEqualTo(30_000);
    }

    @Test
    void aDepositTooSmallToBeWorthAPointPaysNothing() {
        loyalty.openPosition(1L, ACCOUNT, 900, opened); // EUR 9 -> 9 base points -> 0.9 bonus

        assertThat(loyalty.settleDue(plusMonths(opened, 12))).isZero();
        assertThat(onlyPosition().getAnniversariesPaid()).isEqualTo(1);
    }

    private SavingsPosition onlyPosition() {
        return positions.findAll().get(0);
    }

    private SavingsPosition positionOf(long transferId) {
        return positions.findAll().stream()
                .filter(position -> position.getTransferId() == transferId)
                .findFirst()
                .orElseThrow();
    }

    private Long memberId() {
        return memberRepository.findFirstByPrimaryCustomerTrue().orElseThrow().getId();
    }

    @Autowired
    private be.kbc.savingstreak.repo.MemberRepository memberRepository;
}
