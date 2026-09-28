package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A deposit that happened, as the rest of the application sees it: how much moved, what it earned and
 * how, the rate it was paid at, and when. The stored record stays inside the module; this is a
 * statement about what took place.
 *
 * <p>{@code pointsEarned} is everything the deposit earned however it earned it, which is what it has
 * always meant: before there were streaks every deposit was paid at the ordinary rate, so the figure
 * is unchanged for every deposit made before this scheme existed. {@code basePoints},
 * {@code streakBonusPoints} and {@code loyaltyBonusPoints} are what it is made of, and the three
 * always sum to it — a customer looking at nine points against a seven-euro deposit can see where
 * the nine came from.
 *
 * <p>{@code loyaltyBonusPoints} is every anniversary this deposit has ever been paid, added up, and
 * it is the one figure here that grows after the money has moved: a deposit left alone is paid again
 * every twelve months, so what it has been worth to the customer altogether goes up while what it
 * earned <em>when it landed</em> stays exactly what it was. That is what a recurring reward means,
 * and it is why the total is asked of the points ledger on every read rather than fixed at the
 * moment the euros moved.
 *
 * <p>{@code newSavings} is how much of the amount was new saving — the part that took its holder
 * above the most they had ever had in savings, and so the part that earned. It is the whole amount
 * for anybody who has never taken money back out; it is less when an earlier withdrawal left a gap
 * this deposit is filling back in, because those euros have already been paid for once. It is the
 * figure that explains a deposit whose points look short, and without it a customer would be left
 * subtracting one from the other and guessing why.
 *
 * <p>{@code multiplierApplied} is the rate this deposit was in fact paid at, not the rate the account
 * is on today. It was decided at the moment the money moved and is never worked out again, so a
 * deposit keeps explaining itself after the ladder changes and after the run it was paid on has
 * lapsed.
 *
 * <p>{@code productMultiplierApplied} is the part of that rate the account's savings product
 * accounts for, on its own — {@code 1.0000} where the product changed nothing, which is what free
 * savings pays and what every deposit made before there were products was paid. The two are sent
 * apart rather than only combined because neither can be recovered from the other: 1.375 is 1.10
 * times 1.25 and equally 1.25 times 1.10, and the ladder the first factor came off has usually
 * moved on by the time anybody reads the row. Sent on every deposit rather than only where it is
 * remarkable, so that a history laid out beside itself compares like with like — the same argument
 * {@code newSavings} makes two paragraphs up.
 *
 * <p>Neither rate is a claim about which points came from which factor. The ledger credits the
 * euros as base points and the whole of the uplift as the bonus, as it always has, because two
 * factors that multiply have no shares to divide an uplift into. What the rates say is what each
 * factor was.
 *
 * <p>{@code termsVersion} is which version of the account's product terms this deposit landed
 * under, and it is the same kind of statement as the rate beside it: what was in force when the
 * money arrived, not what is in force now. An account that later takes its product's newer terms
 * keeps a history whose older rows still name the agreement they were priced under, which is the
 * whole reason a version is written on a deposit rather than read off the account.
 *
 * <p>It is null, and only null, for a deposit nothing has stamped: one recorded before this column
 * existed and not yet reached by the start-up migration, or one that landed in an account whose
 * agreement had not yet been written. Null rather than a one, because there is no honest default —
 * a version nobody recorded is not a version, and printing "version 1" would be inventing the
 * agreement the money was priced under.
 */
public record RecordedDeposit(Long id, BigDecimal amount, BigDecimal newSavings, long pointsEarned,
                              long basePoints, long streakBonusPoints, long loyaltyBonusPoints,
                              BigDecimal multiplierApplied, BigDecimal productMultiplierApplied,
                              Integer termsVersion, Instant depositedAt) {
}
