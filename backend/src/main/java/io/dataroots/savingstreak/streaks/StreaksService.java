package io.dataroots.savingstreak.streaks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.deposits.DepositLanded;
import io.dataroots.savingstreak.deposits.DepositsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Streaks module's face to the rest of the application. It answers what a savings account's
 * week looks like: how much new saving has landed in the week the account is part-way through, and
 * how much more that week still asks for.
 *
 * <p>Nothing is stored. Every answer is derived from the deposit records at the moment it is asked
 * for, which is the deciding property of the whole feature: the application's clock is movable, in
 * a training session it is wound both ways, and a weekly total written down would routinely find
 * itself describing a week that is now in the future. A derivation cannot disagree with the ledger
 * it is derived from, and the cost — one query per read against a training-sized history — is not
 * one.
 *
 * <p>Which week it is comes from the application's clock rather than from the machine's, so a week
 * moves when a trainer moves time. What a week is — Monday to Sunday, in Brussels — is
 * {@link SavingsWeek}'s answer and is stated there once.
 */
@Service
public class StreaksService {

    private static final Logger log = LoggerFactory.getLogger(StreaksService.class);

    private final DepositsService deposits;
    private final Clock clock;

    StreaksService(DepositsService deposits, Clock clock) {
        this.deposits = deposits;
        this.clock = clock;
    }

    /**
     * What has landed in this savings account so far in the week it is part-way through, and what
     * the week still needs.
     *
     * <p>Gross: every deposit that landed in the week is counted, and money that has since been
     * withdrawn is counted with them. Withdrawals are invisible to a week's progress on purpose —
     * see {@link NewSavingsThisWeek} — so nothing in this method has to know that they exist.
     */
    @Transactional(readOnly = true)
    public NewSavingsThisWeek newSavingsThisWeekIn(long savingsAccountId) {
        Instant now = clock.instant();
        SavingsWeek week = SavingsWeek.containing(now);
        List<DepositLanded> landed =
                deposits.depositsLandedBetween(savingsAccountId, week.startsAt(), week.endsAt());
        BigDecimal newSavings = landed.stream()
                .map(DepositLanded::amount)
                // Added back as decimals rather than summed by the database, for the reason the
                // money balance gives: SQLite has no decimal type and a sum it worked out itself
                // would accumulate in floating point.
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        NewSavingsThisWeek thisWeek = new NewSavingsThisWeek(week, newSavings);
        // Everything the answer was made of: the moment the clock read, the week that moment falls
        // in, the zone it was read in, the two boundaries the ledger was actually queried between,
        // and the deposits that came back. A reviewer can add the amounts up by hand and check both
        // that the total is right and that the deposits either side of the boundary are the ones a
        // customer in Brussels would expect. Guarded, because rendering the deposits is work, and
        // this runs on every read of an account.
        if (log.isDebugEnabled()) {
            log.debug("this week's new savings derived from the ledger savingsAccountId={} zone={} "
                            + "clockReads={} week={} weekStartsAt={} weekEndsAt={} deposits={} "
                            + "counted=[{}] newSavings={} weeklyMinimum={} stillNeeded={}",
                    savingsAccountId, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN, now, week,
                    week.startsAt(), week.endsAt(), landed.size(), countedInto(landed),
                    thisWeek.newSavings(), thisWeek.weeklyMinimum(), thisWeek.stillNeeded());
        }
        return thisWeek;
    }

    /**
     * The deposits the week was counted from, written out for the log line above: each one's
     * identifier, its amount to the cent, and the moment it landed both as the application recorded
     * it and as a customer in Brussels would read it. The second is what makes a boundary case
     * checkable — "23:30 on Sunday" is not something a reader can see in a UTC instant.
     */
    private static String countedInto(List<DepositLanded> landed) {
        return landed.stream()
                .map(deposit -> "deposit " + deposit.id()
                        + " EUR " + deposit.amount().setScale(2, RoundingMode.HALF_UP).toPlainString()
                        + " at " + deposit.depositedAt()
                        + " (" + deposit.depositedAt().atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN) + ")")
                .collect(Collectors.joining("; "));
    }
}
