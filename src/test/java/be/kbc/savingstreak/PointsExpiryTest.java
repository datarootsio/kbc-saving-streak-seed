package be.kbc.savingstreak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kbc.savingstreak.config.DemoDataSeeder;
import be.kbc.savingstreak.repo.PointsLotRepository;
import be.kbc.savingstreak.service.BusinessRuleException;
import be.kbc.savingstreak.service.PointsWallet;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * The wallet takes the moment to evaluate as a parameter, so expiry can be tested by looking
 * at the same ledger from a later point in time.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:sqlite:target/test-expiry.db")
class PointsExpiryTest {

    @Autowired
    private PointsWallet wallet;

    @Autowired
    private PointsLotRepository lots;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private Clock clock;

    private Instant now;

    @BeforeEach
    void clearTheLedger() {
        seeder.reset();
        lots.deleteAll();
        now = clock.instant();
    }

    @Test
    void pointsLapseTwelveMonthsAfterTheyAreEarned() {
        wallet.credit(memberId(), 100, now, null);

        assertThat(wallet.balanceAt(memberId(), now)).isEqualTo(100);
        assertThat(wallet.balanceAt(memberId(), now.plus(364, ChronoUnit.DAYS))).isEqualTo(100);
        assertThat(wallet.balanceAt(memberId(), wallet.expiryFor(now))).isZero();
        assertThat(wallet.balanceAt(memberId(), now.plus(400, ChronoUnit.DAYS))).isZero();
    }

    @Test
    void lapsedPointsAreReportedSeparatelyFromTheBalance() {
        wallet.credit(memberId(), 100, now.minus(400, ChronoUnit.DAYS), null);
        wallet.credit(memberId(), 40, now, null);

        assertThat(wallet.balanceAt(memberId(), now)).isEqualTo(40);
        assertThat(wallet.lapsedAt(memberId(), now)).isEqualTo(100);
        assertThat(wallet.everEarned(memberId())).isEqualTo(140);
    }

    @Test
    void spendingTakesTheBatchClosestToExpiryFirst() {
        wallet.credit(memberId(), 30, now.minus(300, ChronoUnit.DAYS), null);
        wallet.credit(memberId(), 50, now.minus(10, ChronoUnit.DAYS), null);

        wallet.spend(memberId(), 40, now);

        assertThat(wallet.balanceAt(memberId(), now)).isEqualTo(40);
        // the older batch is emptied, the newer one keeps what is left
        assertThat(lots.findByMemberIdAndPointsRemainingGreaterThanAndExpiresAtAfterOrderByExpiresAtAscIdAsc(memberId(), 0, now))
                .singleElement()
                .satisfies(lot -> assertThat(lot.getPointsRemaining()).isEqualTo(40));
    }

    @Test
    void lapsedBatchesCannotBeSpent() {
        wallet.credit(memberId(), 500, now.minus(400, ChronoUnit.DAYS), null);
        wallet.credit(memberId(), 20, now, null);

        assertThatThrownBy(() -> wallet.spend(memberId(), 100, now))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("80 more points");
        assertThat(wallet.balanceAt(memberId(), now)).isEqualTo(20);
    }

    @Test
    void theNextBatchToExpireIsTheOldestOneStillValid() {
        wallet.credit(memberId(), 11, now.minus(300, ChronoUnit.DAYS), null);
        wallet.credit(memberId(), 22, now.minus(100, ChronoUnit.DAYS), null);

        assertThat(wallet.nextToExpire(memberId(), now))
                .get()
                .satisfies(lot -> assertThat(lot.getPointsRemaining()).isEqualTo(11));
        assertThat(wallet.nextToExpire(memberId(), now.plus(100, ChronoUnit.DAYS)))
                .get()
                .satisfies(lot -> assertThat(lot.getPointsRemaining()).isEqualTo(22));
    }

    @Test
    void aWalletLeftAloneForOverAYearIsEmptyWithoutAnySweep() {
        seeder.reset();
        int seeded = wallet.balanceAt(memberId(), now);
        assertThat(seeded).isPositive();

        assertThat(wallet.balanceAt(memberId(), now.plus(400, ChronoUnit.DAYS))).isZero();
        // nothing was spent, so by then every point ever earned has lapsed
        assertThat(wallet.lapsedAt(memberId(), now.plus(400, ChronoUnit.DAYS))).isEqualTo(wallet.everEarned(memberId()));
    }

    private Long memberId() {
        return memberRepository.findFirstByPrimaryCustomerTrue().orElseThrow().getId();
    }

    @Autowired
    private be.kbc.savingstreak.repo.MemberRepository memberRepository;
}
