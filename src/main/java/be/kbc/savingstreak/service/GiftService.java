package be.kbc.savingstreak.service;

import be.kbc.savingstreak.domain.Member;
import be.kbc.savingstreak.domain.PointsGift;
import be.kbc.savingstreak.domain.PointsSource;
import be.kbc.savingstreak.repo.PointsGiftRepository;
import be.kbc.savingstreak.web.dto.GiftRequest;
import be.kbc.savingstreak.web.dto.GiftResult;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Peer-to-peer points gifting. There is no cap on how often, how much, or how much per day a
 * customer may give away, so every gift is recorded individually and the wallet keeps the
 * batches' original expiry dates: points cannot gain life by being passed around.
 */
@Service
public class GiftService {

    private final PointsGiftRepository gifts;
    private final PointsWallet wallet;
    private final LoyaltyService loyalty;
    private final OverviewService overviewService;
    private final Clock clock;

    public GiftService(PointsGiftRepository gifts, PointsWallet wallet, LoyaltyService loyalty,
                       OverviewService overviewService, Clock clock) {
        this.gifts = gifts;
        this.wallet = wallet;
        this.loyalty = loyalty;
        this.overviewService = overviewService;
        this.clock = clock;
    }

    @Transactional
    public GiftResult send(GiftRequest request) {
        // Vested loyalty bonuses are giftable, so settle before checking the balance.
        loyalty.settleDue();

        Member sender = overviewService.member();
        Member recipient = overviewService.memberById(request.toMemberId());
        if (recipient.getId().equals(sender.getId())) {
            throw new BusinessRuleException("Choose someone else to send points to.");
        }
        if (request.points() <= 0) {
            throw new BusinessRuleException("Send at least one point.");
        }

        Instant now = clock.instant();
        int balance = wallet.balanceAt(sender.getId(), now);
        if (balance < request.points()) {
            throw new BusinessRuleException(
                    "You need " + (request.points() - balance) + " more points to send that.");
        }

        PointsGift gift = gifts.save(new PointsGift(sender.getId(), recipient.getId(), request.points(),
                trim(request.message()), now));
        wallet.giveTo(sender.getId(), recipient.getId(), request.points(), now, gift.getId());

        return new GiftResult(
                OverviewService.toView(gift, sender, recipient, sender.getId()),
                overviewService.overview());
    }

    /**
     * Demo affordance: lets a contact send points to the signed-in customer, so the receiving
     * side can be seen without a second logged-in user. The contact is credited with the points
     * two months ago first, so the batch that arrives carries a realistic expiry date.
     */
    @Transactional
    public GiftResult receiveFrom(Long fromMemberId, int points) {
        Member sender = overviewService.memberById(fromMemberId);
        Member recipient = overviewService.member();
        Instant now = clock.instant();

        wallet.credit(sender.getId(), points, now.minus(60, ChronoUnit.DAYS), null, PointsSource.DEPOSIT);
        PointsGift gift = gifts.save(new PointsGift(sender.getId(), recipient.getId(), points,
                "Thanks for the help!", now));
        wallet.giveTo(sender.getId(), recipient.getId(), points, now, gift.getId());

        return new GiftResult(
                OverviewService.toView(gift, sender, recipient, recipient.getId()),
                overviewService.overview());
    }

    private static String trim(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String cleaned = message.strip();
        return cleaned.length() > 140 ? cleaned.substring(0, 140) : cleaned;
    }
}
