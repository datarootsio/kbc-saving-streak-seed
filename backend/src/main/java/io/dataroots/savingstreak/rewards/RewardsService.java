package io.dataroots.savingstreak.rewards;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.points.PointsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.NOT_ENOUGH_POINTS;
import static io.dataroots.savingstreak.rewards.RewardRefused.Kind.NO_SUCH_ACCOUNT;

/**
 * The Rewards module's face to the rest of the application: what points can be spent on, the claiming
 * of one, and what a savings account has claimed already.
 *
 * <p>It owns what a reward is worth and refuses a claim it cannot honour. It does not own the points:
 * how many an account has, and which of them leave first, is the Points module's answer, and this
 * module only asks for a number of them and is told whether there were enough.
 */
@Service
public class RewardsService {

    private static final Logger log = LoggerFactory.getLogger(RewardsService.class);

    private final RedemptionRepository redemptions;
    private final AccountsService accounts;
    private final PointsService points;
    private final Clock clock;

    RewardsService(RedemptionRepository redemptions, AccountsService accounts, PointsService points, Clock clock) {
        this.redemptions = redemptions;
        this.accounts = accounts;
        this.points = points;
        this.clock = clock;
    }

    /** Everything points can be spent on, cheapest first. The same list for every customer. */
    public List<Reward> catalogue() {
        return List.of(Reward.values());
    }

    /**
     * Spends the reward's cost out of the savings account and issues the voucher for it.
     *
     * <p>Both happen in one transaction, and that is the load-bearing guarantee of this slice: points
     * spent on nothing would be a balance nobody could explain, and a voucher nobody paid for would
     * be worse. There is no reversal — the requirement is that a claim is instant and final — so the
     * only protection against a half-made claim is that a half-made one cannot be committed.
     *
     * <p>The account is checked before the points are, for the same reason a deposit checks its
     * accounts before its amount: if there is no such account, what it can afford is beside the point.
     *
     * @throws RewardRefused if there is no such savings account, or it cannot afford the reward
     */
    @Transactional
    public ClaimedReward claim(long savingsAccountId, Reward reward) {
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            throw new RewardRefused(NO_SUCH_ACCOUNT,
                    AccountsService.noSuchSavingsAccount(savingsAccountId));
        }
        long cost = reward.costInPoints();
        if (!points.spend(savingsAccountId, cost)) {
            // Read after the refusal rather than before the attempt: nothing was taken, so this is
            // still what the account has, and it is the figure the person needs in order to know how
            // much more saving stands between them and this reward.
            throw new RewardRefused(NOT_ENOUGH_POINTS, reward.title() + " costs " + cost
                    + " points, and this account has " + points.balanceOf(savingsAccountId) + ".");
        }
        // One moment for the spend and the voucher, read from the application's clock and truncated
        // the way a deposit's is, so that the moment reported back is the same moment the claim is
        // later listed under — and so that a clock wound forward moves claims along with deposits.
        Instant clockReads = clock.instant();
        Instant claimedAt = clockReads.truncatedTo(ChronoUnit.MILLIS);
        log.debug("claim takes its moment from the application clock savingsAccountId={} "
                + "clockReads={} recordedMoment={}", savingsAccountId, clockReads, claimedAt);

        ClaimedReward claimed =
                redemptions.save(Redemption.issue(savingsAccountId, reward, cost, claimedAt)).asClaimed();
        log.info("claim issued redemptionId={} savingsAccountId={} reward={} pointsSpent={} claimedAt={}",
                claimed.id(), savingsAccountId, reward.name(), cost, claimed.claimedAt());
        return claimed;
    }

    /**
     * What the savings account has claimed, newest first. Together with the deposits, this is the
     * whole account of a points balance: the deposits say what came in, these say what went out, and
     * the balance is what the two leave behind.
     */
    @Transactional(readOnly = true)
    public List<ClaimedReward> claimedIn(long savingsAccountId) {
        return redemptions.findBySavingsAccountIdOrderByClaimedAtDescIdDesc(savingsAccountId).stream()
                .map(Redemption::asClaimed)
                .toList();
    }
}
