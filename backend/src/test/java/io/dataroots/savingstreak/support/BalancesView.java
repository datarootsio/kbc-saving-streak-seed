package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A savings account as the API reports it: what it holds, what its holder has to spend, how far into
 * this week's saving it has got, the run of consecutive secured weeks behind that week, and what that
 * run pays per whole euro. Every figure is derived on every read. Shared by every test that reads a
 * balance back, so that none of them can drift into disagreeing about the shape of the answer.
 *
 * <p>The money is the account's and the points are the customer's: paying in here earns them, and the
 * same figure is reported beside every account that customer holds.
 *
 * <p>{@code mostEverSaved} is the customer's too: the most they have ever had in savings across every
 * account they hold, and the mark a deposit is judged against — euros above it are new saving and
 * earn, euros below it have been saved once already.
 *
 * <p>{@code schemeVersion} is which published version of the scheme decided {@code weeklyMinimum}
 * and {@code currentMultiplier}: the one in force on this week's Monday. It is the row a rate can be
 * traced to, added beside the multiplier rather than in place of anything, and it is never absent —
 * there is always a version in force, because a bank with no scheme written down is a broken
 * database rather than a state this application hands out.
 *
 * <p>{@code agreement} is the one thing here that is about the account's rules rather than its
 * figures: which savings product it is on, which version of that product's terms it was opened
 * with, and what that version asks of it. It is null only for an account nothing has recorded an
 * agreement for, which is a database that has not been through the start-up migration.
 *
 * <p>{@code newerTerms} is the comparison beside it: whether that product has published anything
 * newer than the version the account is on, and what taking it would change. It arrives with the
 * agreement rather than from a door of its own, so that a test reading both is reading one answer
 * about one instant. It is null for the same account the agreement is null for.
 */
public record BalancesView(Long id, String customerName, BigDecimal moneyBalance, long pointsBalance,
                           BigDecimal mostEverSaved,
                           Long pointsExpiringNext, LocalDate pointsExpiringNextOn,
                           BigDecimal newSavingsThisWeek, BigDecimal weeklyMinimum,
                           BigDecimal stillNeededThisWeek,
                           int currentStreakWeeks, int bestStreakWeeks,
                           BigDecimal currentMultiplier,
                           int schemeVersion,
                           AnAgreementView agreement, TheNewerTermsView newerTerms) {
}
