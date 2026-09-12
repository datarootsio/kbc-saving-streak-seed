package be.kbc.savingstreak;

import static org.assertj.core.api.Assertions.assertThat;

import be.kbc.savingstreak.domain.AccountType;
import be.kbc.savingstreak.domain.Member;
import be.kbc.savingstreak.domain.Transfer;
import be.kbc.savingstreak.domain.TransferDirection;
import be.kbc.savingstreak.repo.MemberRepository;
import be.kbc.savingstreak.repo.TransferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Boots against its own empty database so the seeding runs the way it does on a first start:
 * through the ApplicationRunner, not through a reset call from a test.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:sqlite:target/test-seeding.db")
class DemoSeedingTest {

    @Autowired
    private MemberRepository members;

    @Autowired
    private be.kbc.savingstreak.repo.AccountRepository accounts;

    @Autowired
    private be.kbc.savingstreak.repo.PointsLotRepository lots;

    @Autowired
    private be.kbc.savingstreak.service.PointsWallet wallet;

    @Autowired
    private java.time.Clock clock;

    @Autowired
    private TransferRepository transfers;

    @Test
    void startingUpOnAnEmptyDatabaseFillsTheWalletFromTheSeededHistory() {
        Member member = members.findAll().get(0);
        int earnedByHistory = transfers.findAll().stream()
                .filter(transfer -> transfer.getDirection() == TransferDirection.DEPOSIT)
                .mapToInt(Transfer::getPointsEarned)
                .sum();

        int fromLoyalty = wallet.everEarnedFromLoyalty(memberId());
        assertThat(earnedByHistory).isPositive();
        // the seeded history includes a deposit old enough to have had an anniversary
        assertThat(fromLoyalty).isPositive();
        assertThat(wallet.everEarned(memberId())).isEqualTo(earnedByHistory + fromLoyalty);
        assertThat(wallet.balanceAt(memberId(), clock.instant()))
                .isEqualTo(wallet.everEarned(memberId()) - wallet.lapsedAt(memberId(), clock.instant()));
        assertThat(member.getStreakWeeks()).isEqualTo(3);
    }

    @Test
    void everySeededBatchOfPointsCarriesTheDateItWasEarned() {
        transfers.findAll().stream()
                .filter(transfer -> transfer.getPointsEarned() > 0)
                .forEach(transfer -> assertThat(lots.findAll())
                        .anySatisfy(lot -> {
                            assertThat(lot.getTransferId()).isEqualTo(transfer.getId());
                            assertThat(lot.getEarnedAt()).isEqualTo(transfer.getCreatedAt());
                            assertThat(lot.getExpiresAt()).isEqualTo(wallet.expiryFor(transfer.getCreatedAt()));
                        }));
    }

    @Test
    void theSavingsPeakStartsAtTheSeededSavingsTotal() {
        long savings = accounts.sumBalanceCentsByType(AccountType.SAVINGS);
        assertThat(savings).isPositive();
        assertThat(members.findAll().get(0).savingsPeakAtLeast(0)).isEqualTo(savings);
    }

    @Test
    void oneEuroSavedIsOnePoint() {
        transfers.findAll().stream()
                .filter(transfer -> transfer.getDirection() == TransferDirection.DEPOSIT)
                .forEach(transfer ->
                        assertThat(transfer.getPointsEarned()).isEqualTo((int) (transfer.getAmountCents() / 100)));
    }

    private Long memberId() {
        return memberRepository.findFirstByPrimaryCustomerTrue().orElseThrow().getId();
    }

    @Autowired
    private be.kbc.savingstreak.repo.MemberRepository memberRepository;
}
