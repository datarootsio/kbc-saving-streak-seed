package be.kbc.savingstreak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.kbc.savingstreak.config.DemoDataSeeder;
import be.kbc.savingstreak.domain.Member;
import be.kbc.savingstreak.domain.PointsLot;
import be.kbc.savingstreak.domain.PointsSource;
import be.kbc.savingstreak.repo.MemberRepository;
import be.kbc.savingstreak.repo.PointsGiftRepository;
import be.kbc.savingstreak.repo.PointsLotRepository;
import be.kbc.savingstreak.service.BusinessRuleException;
import be.kbc.savingstreak.service.GiftService;
import be.kbc.savingstreak.service.NotFoundException;
import be.kbc.savingstreak.service.PointsWallet;
import be.kbc.savingstreak.web.dto.GiftRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:sqlite:target/test-gifting.db")
class GiftingTest {

    @Autowired
    private GiftService giftService;

    @Autowired
    private PointsWallet wallet;

    @Autowired
    private PointsLotRepository lots;

    @Autowired
    private PointsGiftRepository gifts;

    @Autowired
    private MemberRepository members;

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private Clock clock;

    private Long me;
    private Long friend;
    private Instant now;

    @BeforeEach
    void freshWallets() {
        seeder.reset();
        lots.deleteAll();
        gifts.deleteAll();
        me = members.findFirstByPrimaryCustomerTrue().orElseThrow().getId();
        friend = members.findByPrimaryCustomerFalseOrderByFirstNameAsc().get(0).getId();
        now = clock.instant();
    }

    @Test
    void sendingPointsMovesThemAndRecordsTheGift() {
        wallet.credit(me, 300, now, null);

        giftService.send(new GiftRequest(friend, 120, "Enjoy"));

        assertThat(wallet.balanceAt(me, now)).isEqualTo(180);
        assertThat(wallet.balanceAt(friend, now)).isEqualTo(120);
        assertThat(gifts.findAll()).singleElement().satisfies(gift -> {
            assertThat(gift.getFromMemberId()).isEqualTo(me);
            assertThat(gift.getToMemberId()).isEqualTo(friend);
            assertThat(gift.getPoints()).isEqualTo(120);
            assertThat(gift.getMessage()).isEqualTo("Enjoy");
        });
    }

    @Test
    void theReceivedBatchKeepsTheSendersExpiryDate() {
        Instant earnedLongAgo = now.minus(300, ChronoUnit.DAYS);
        PointsLot original = wallet.credit(me, 100, earnedLongAgo, null);

        giftService.send(new GiftRequest(friend, 100, null));

        // SQLite keeps instants to the millisecond, so compare at that precision
        assertThat(lotsOf(friend)).singleElement().satisfies(lot -> {
            assertThat(lot.getSource()).isEqualTo(PointsSource.GIFT_RECEIVED);
            assertThat(lot.getEarnedAt()).isEqualTo(earnedLongAgo.truncatedTo(ChronoUnit.MILLIS));
            assertThat(lot.getExpiresAt()).isEqualTo(original.getExpiresAt().truncatedTo(ChronoUnit.MILLIS));
        });
    }

    @Test
    void passingPointsBackAndForthCannotExtendTheirLife() {
        Instant almostLapsed = now.minus(360, ChronoUnit.DAYS);
        Instant expiry = wallet.credit(me, 50, almostLapsed, null).getExpiresAt();

        // there and back again, several times
        for (int round = 0; round < 3; round++) {
            wallet.giveTo(me, friend, 50, now, null);
            wallet.giveTo(friend, me, 50, now, null);
        }

        assertThat(lotsOf(me)).allSatisfy(lot ->
                assertThat(lot.getExpiresAt()).isEqualTo(expiry.truncatedTo(ChronoUnit.MILLIS)));
        assertThat(wallet.balanceAt(me, now)).isEqualTo(50);
        // and they are gone once that original date passes
        assertThat(wallet.balanceAt(me, expiry)).isZero();
    }

    @Test
    void theBatchClosestToExpiryIsGivenAwayFirst() {
        wallet.credit(me, 30, now.minus(300, ChronoUnit.DAYS), null);
        wallet.credit(me, 50, now, null);

        giftService.send(new GiftRequest(friend, 40, null));

        assertThat(wallet.balanceAt(me, now)).isEqualTo(40);
        assertThat(lotsOf(friend)).hasSize(2);
        // the old batch went entirely, the newer one gave up 10 and keeps the rest
        assertThat(lotsOf(me)).filteredOn(lot -> lot.getPointsRemaining() > 0)
                .singleElement()
                .satisfies(lot -> assertThat(lot.getPointsRemaining()).isEqualTo(40));
    }

    @Test
    void thereIsNoLimitOnHowOftenOrHowMuchYouCanGive() {
        wallet.credit(me, 100, now, null);

        for (int i = 0; i < 60; i++) {
            giftService.send(new GiftRequest(friend, 1, null));
        }
        // and the whole remainder in one go
        giftService.send(new GiftRequest(friend, 40, null));

        assertThat(gifts.findAll()).hasSize(61);
        assertThat(wallet.balanceAt(me, now)).isZero();
        assertThat(wallet.balanceAt(friend, now)).isEqualTo(100);
    }

    @Test
    void youCannotGiveAwayMoreThanYouHave() {
        wallet.credit(me, 10, now, null);

        assertThatThrownBy(() -> giftService.send(new GiftRequest(friend, 11, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("1 more points");
        assertThat(wallet.balanceAt(me, now)).isEqualTo(10);
        assertThat(gifts.findAll()).isEmpty();
    }

    @Test
    void lapsedPointsCannotBeGivenAway() {
        wallet.credit(me, 500, now.minus(400, ChronoUnit.DAYS), null);

        assertThatThrownBy(() -> giftService.send(new GiftRequest(friend, 100, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(wallet.balanceAt(friend, now)).isZero();
    }

    @Test
    void youCannotSendPointsToYourself() {
        wallet.credit(me, 100, now, null);

        assertThatThrownBy(() -> giftService.send(new GiftRequest(me, 10, null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("someone else");
    }

    @Test
    void sendingToSomebodyWhoDoesNotExistIsRejected() {
        wallet.credit(me, 100, now, null);

        assertThatThrownBy(() -> giftService.send(new GiftRequest(9_999L, 10, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void pointsSomebodyGaveYouCountAsBalanceButNotAsEarned() {
        wallet.credit(friend, 80, now, null);
        wallet.giveTo(friend, me, 80, now, null);

        assertThat(wallet.balanceAt(me, now)).isEqualTo(80);
        assertThat(wallet.everEarned(me)).isZero();
        assertThat(wallet.everCredited(me)).isEqualTo(80);
    }

    @Test
    void oneWalletIsNeverAffectedByAnother() {
        wallet.credit(me, 100, now, null);
        wallet.credit(friend, 700, now, null);

        giftService.send(new GiftRequest(friend, 100, null));

        assertThat(wallet.balanceAt(me, now)).isZero();
        assertThat(wallet.balanceAt(friend, now)).isEqualTo(800);
    }

    @Test
    void aLongMessageIsCutToLength() {
        wallet.credit(me, 10, now, null);

        giftService.send(new GiftRequest(friend, 10, " ".repeat(3) + "x".repeat(200)));

        assertThat(gifts.findAll()).singleElement()
                .satisfies(gift -> assertThat(gift.getMessage()).hasSize(140));
    }

    private List<PointsLot> lotsOf(Long memberId) {
        return lots.findAll().stream().filter(lot -> lot.getMemberId().equals(memberId)).toList();
    }
}
