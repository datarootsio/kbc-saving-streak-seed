package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import io.dataroots.savingstreak.points.PointsExpiringNext;
import io.dataroots.savingstreak.products.TheAgreementAnAccountIsOn;
import io.dataroots.savingstreak.products.TheNewerTermsOnOffer;
import io.dataroots.savingstreak.streaks.WeekAndStreak;

/**
 * A savings account and what it is worth, in the two currencies the customer cares about: the money
 * they have saved here and the points their saving has earned them, side by side because the point
 * of the application is the connection between the two.
 *
 * <p>The money is this account's; the points, the week and the run of weeks are the holder's. All
 * three belong to the customer rather than to any one account they save into, so they read the same
 * on every account they hold — what paying in <em>here</em> earned is on the deposit, in the history
 * underneath.
 *
 * <p>And the week they are part-way through, the run of weeks behind it, and what that run pays. The
 * two balances say where the account has got to altogether; the three weekly figures say where it has
 * got to since Monday, which is the thing a customer can still do something about before Sunday; the
 * two streak figures say how many weeks like this one have run consecutively and how many ever did —
 * what there is to lose, and what there is to beat; and the multiplier says what the run is worth per
 * euro, which is the figure that decides whether to pay in now. All eight are derived on every read
 * and none of them is worked out here.
 *
 * <p>The multiplier is the account's rate as it stands, not a rate any deposit was paid at. What a
 * past deposit was paid is on the deposit, was decided at the moment the money moved, and does not
 * move when this figure does.
 *
 * <p>What the week asks for travels with what has landed in it. A screen showing progress towards
 * EUR 50 that had the 50 written into its own markup would be a second place the weekly minimum
 * lives, and the two would be one repricing away from disagreeing — and the figure is now genuinely
 * repriceable, because it is the threshold the version of the scheme in force on this week's Monday
 * published rather than a constant that had always been true.
 *
 * <p><strong>And {@code schemeVersion}, which names the row those two figures came out of.</strong>
 * Added beside the multiplier and taking nothing away, because it is the answer to the one question
 * the multiplier raises and cannot settle: "why is it 1,30×". A customer can read the published
 * history, find that version, and read the line saying what changed and when — which is the whole
 * difference between a rate that fell and a rate that fell for a reason. It is the version in force
 * <em>this week</em>, which is the version that decided both the threshold above and the rate below;
 * the weeks behind it were each judged under their own, and no single number could name those.
 *
 * <p>It is a plain {@code int} and never null. There is always a version in force — a history with
 * nothing in it is a database whose scheme was never written down, which this application refuses to
 * hand out rather than papering over — so there is no "no scheme" state for a screen to draw, unlike
 * the agreement below it.
 *
 * <p>And the most the holder has ever had in savings, which is the mark a deposit is judged
 * against: euros above it are new saving and earn points, euros that only fill a gap an earlier
 * withdrawal left have been saved before and earned then. Beside the balances rather than anywhere
 * else, because it is the pair of figures that explains a deposit which earned less than it moved —
 * and because it never falls, it is also the best they have ever done, which is the other thing on
 * this screen worth knowing.
 *
 * <p>And what the holder stands to lose next: how many of their points expire soonest, and the day
 * they do. Beside the balance rather than anywhere else, because it is the same figure read from the
 * other end — what they can spend, and how long they have to spend it in. Both are null for a
 * customer with nothing left to lose rather than zero on no date, because "nothing expires next" and
 * "nothing expires on some particular day" are different statements and only the first is true of
 * somebody who has never earned anything.
 *
 * <p>The day travels as a plain date rather than as a moment. Which calendar day a moment falls on
 * depends on the zone it is read in, and the Points module has already read it in the one zone this
 * application counts calendars in — so a client is handed the answer instead of the means to get it
 * wrong.
 *
 * <p>And the agreement this account is living under: which savings product it is on, which version
 * of that product's terms it was opened with, the day it was opened, and the condition that product
 * attaches. It is the one thing on this reading that is about the account's <em>rules</em> rather
 * than about its figures, and it belongs here because it is the answer to a question the balance
 * raises and cannot settle — what is this money allowed to do, and what is it being paid for. What
 * it has actually been paid is a reading of its own, month by month, because each month names the
 * balance and the rate behind it and that is more than a summary line could carry. It is
 * emphatically not what the product is selling today: an account opened before free savings was
 * repriced carries on under the version it was written with, and {@link AnAgreementResponse} says
 * at length why the two readings are kept apart.
 *
 * <p>It is null for an account nothing has recorded an agreement for, which is a database written
 * by a release older than the catalogue and not yet through its start-up migration. Null rather
 * than an invented agreement, so that a screen draws no panel instead of a false one.
 *
 * <p><strong>And, beside it, whether that product has published anything newer and what taking it
 * would change.</strong> The two belong on one read because they are one question asked from two
 * ends — what this account is living under, and what it could be living under instead — and a page
 * that had to fetch the second separately would draw an agreement panel that was momentarily silent
 * about a rate cut sitting on the shelf. It is emphatically not an instruction: nothing in this
 * application adopts newer terms on anybody's behalf, because newer is not the same as better, and
 * {@link TheNewerTermsResponse} says at length why the sentences arrive already worded. Null for the
 * same account the agreement is null for, and for the same reason.
 */
record SavingsAccountResponse(Long id, String customerName, BigDecimal moneyBalance, long pointsBalance,
                              BigDecimal mostEverSaved,
                              Long pointsExpiringNext, LocalDate pointsExpiringNextOn,
                              BigDecimal newSavingsThisWeek, BigDecimal weeklyMinimum,
                              BigDecimal stillNeededThisWeek,
                              int currentStreakWeeks, int bestStreakWeeks,
                              BigDecimal currentMultiplier,
                              int schemeVersion,
                              AnAgreementResponse agreement,
                              TheNewerTermsResponse newerTerms) {

    static SavingsAccountResponse of(long savingsAccountId, String customerName, BigDecimal moneyBalance,
                                     long pointsBalance, BigDecimal mostEverSaved,
                                     Optional<PointsExpiringNext> expiringNext,
                                     WeekAndStreak saving,
                                     Optional<TheAgreementAnAccountIsOn> agreement,
                                     Optional<TheNewerTermsOnOffer> newerTerms) {
        return new SavingsAccountResponse(savingsAccountId, customerName, moneyBalance, pointsBalance,
                mostEverSaved,
                expiringNext.map(PointsExpiringNext::points).orElse(null),
                expiringNext.map(PointsExpiringNext::on).orElse(null),
                saving.week().newSavings(), saving.week().weeklyMinimum(), saving.week().stillNeeded(),
                saving.streak().currentWeeks(), saving.streak().bestWeeks(),
                saving.streak().multiplier(),
                saving.theVersionOfTheSchemeThisWeekWasJudgedUnder(),
                agreement.map(AnAgreementResponse::of).orElse(null),
                newerTerms.map(TheNewerTermsResponse::of).orElse(null));
    }
}
