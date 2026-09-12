package be.kbc.savingstreak.service;

import be.kbc.savingstreak.domain.Redemption;
import be.kbc.savingstreak.domain.Reward;
import be.kbc.savingstreak.repo.RedemptionRepository;
import be.kbc.savingstreak.web.dto.RedeemResult;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RewardService {

    private static final String CODE_ALPHABET = "ACDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final RedemptionRepository redemptions;
    private final OverviewService overviewService;
    private final PointsWallet wallet;
    private final LoyaltyService loyalty;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RewardService(RedemptionRepository redemptions, OverviewService overviewService, PointsWallet wallet,
                         LoyaltyService loyalty, Clock clock) {
        this.redemptions = redemptions;
        this.overviewService = overviewService;
        this.wallet = wallet;
        this.loyalty = loyalty;
        this.clock = clock;
    }

    @Transactional
    public RedeemResult redeem(Long rewardId) {
        Reward reward = overviewService.reward(rewardId);
        // Bonuses that have vested are spendable, so settle before checking the balance.
        loyalty.settleDue();
        Instant now = clock.instant();
        Long memberId = overviewService.member().getId();
        int balance = wallet.balanceAt(memberId, now);
        if (balance < reward.getPointsCost()) {
            int missing = reward.getPointsCost() - balance;
            throw new BusinessRuleException("You need " + missing + " more points for this reward.");
        }

        // Spends the points closest to expiry first.
        wallet.spend(memberId, reward.getPointsCost(), now);
        Redemption redemption = redemptions.save(new Redemption(
                reward.getId(),
                reward.getTitle(),
                reward.getPointsCost(),
                voucherCode(),
                now));

        return new RedeemResult(OverviewService.toView(redemption), overviewService.overview());
    }

    private String voucherCode() {
        StringBuilder code = new StringBuilder("KBC-");
        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                code.append('-');
            }
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
