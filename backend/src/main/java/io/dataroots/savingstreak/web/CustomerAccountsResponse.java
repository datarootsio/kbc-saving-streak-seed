package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import io.dataroots.savingstreak.accounts.CurrentAccount;
import io.dataroots.savingstreak.points.PointsExpiringNext;
import io.dataroots.savingstreak.streaks.WeekAndStreak;

/**
 * A customer's accounts, kept in two lists because the two kinds are not interchangeable: a current
 * account is where a deposit takes its money from, a savings account is where it goes.
 *
 * <p>Each account carries the money in it, so that the first thing a customer sees after signing in
 * is everything they hold and what is in it. No figure is worked out here — a current account keeps
 * its balance and a savings account's is derived from its deposits — and this record only carries
 * them.
 *
 * <p>The points, the week and the run of weeks are the customer's own figures and sit beside the
 * lists rather than inside either of them. They are all earned by paying into any of these savings
 * accounts, and they belong to the person: repeating the same totals against every savings account
 * would say they were that account's, and the customer would appear to hold as many copies of their
 * points and as many streaks as they hold accounts.
 *
 * <p>The nine that describe the saving are the same nine, under the same names, that a savings
 * account's own endpoint reports — because they are the same figures, read for the same customer.
 * What the week asks for travels with what has landed in it, so a page showing progress towards
 * EUR 50 never writes the 50 into its own markup.
 *
 * <p>One of them is the most they have ever had in savings: the mark a deposit is judged against,
 * since euros above it are new saving and euros below it have been saved and paid for before. It is
 * the customer's across every account they hold, for the same reason the points are — a euro moved
 * from one of their savings accounts to another is not new saving in either.
 *
 * <p>Two of the nine are what the customer stands to lose next: how many points expire soonest and
 * the day they do. Both are null where there is nothing left to lose, rather than zero on no date.
 * The day is a plain date rather than a moment, for the reason a savings account's own resource
 * gives: which day a moment falls on depends on the zone it is read in, and that zone is the
 * backend's to know.
 */
record CustomerAccountsResponse(long pointsBalance,
                                BigDecimal mostEverSaved,
                                Long pointsExpiringNext,
                                LocalDate pointsExpiringNextOn,
                                BigDecimal newSavingsThisWeek,
                                BigDecimal weeklyMinimum,
                                BigDecimal stillNeededThisWeek,
                                int currentStreakWeeks,
                                int bestStreakWeeks,
                                BigDecimal currentMultiplier,
                                List<CurrentAccountResponse> currentAccounts,
                                List<SavingsAccountResponse> savingsAccounts) {

    /**
     * The customer's figures put together with their accounts. A factory rather than a constructor
     * call at the call site, because the nine figures come from three modules and unpacking one
     * module's answer into six arguments is the sort of thing that gets done differently the second
     * time.
     */
    static CustomerAccountsResponse of(long pointsBalance,
                                       BigDecimal mostEverSaved,
                                       Optional<PointsExpiringNext> expiringNext,
                                       WeekAndStreak saving,
                                       List<CurrentAccountResponse> currentAccounts,
                                       List<SavingsAccountResponse> savingsAccounts) {
        return new CustomerAccountsResponse(pointsBalance, mostEverSaved,
                expiringNext.map(PointsExpiringNext::points).orElse(null),
                expiringNext.map(PointsExpiringNext::on).orElse(null),
                saving.week().newSavings(), saving.week().weeklyMinimum(), saving.week().stillNeeded(),
                saving.streak().currentWeeks(), saving.streak().bestWeeks(),
                saving.streak().multiplier(),
                currentAccounts, savingsAccounts);
    }

    /** An everyday account, by the IBAN the customer knows it by and what is left in it. */
    record CurrentAccountResponse(Long id, String iban, BigDecimal balance) {

        static CurrentAccountResponse of(CurrentAccount account) {
            return new CurrentAccountResponse(account.getId(), account.getIban(), account.getBalance());
        }
    }

    /**
     * A savings account in an overview: what it holds, and which savings product it is on. Both are
     * the same figures the account's own endpoint reports and read the same way. There is no
     * factory for it, because neither comes from this module and both are assembled by whoever
     * asked.
     *
     * <p><strong>The product is named here rather than left to the account's own page</strong>,
     * because the overview is where a customer holding two accounts finds out that they are not the
     * same kind of account. A list of savings accounts distinguished only by a number and a balance
     * is a list that hides the single most important thing about them the moment more than one
     * product exists — which of them the money can actually come back out of.
     *
     * <p>The name rather than only the code, so that a card reads "Free savings" without the page
     * keeping a table of what each code is called. The code travels with it because it is what
     * everything else is addressed by, and a page that wanted to group or link by product would
     * otherwise have to match on words meant for a person.
     *
     * <p>Both are null for an account nothing has recorded an agreement for, which is a database
     * that has not been through the start-up migration. The card then says nothing about a product
     * rather than saying something invented.
     *
     * <p><strong>A closed account is still on this list, and says the day it closed.</strong> It is
     * not on it to be saved into — nothing may be, and nothing is in it — but leaving it out would
     * make the overview disagree with the money history hanging off the same screen, which reads
     * every euro that moved through every account this customer holds. A screen that showed those
     * euros with no account to attribute them to would have lost the customer's own record. So the
     * account stays, {@code closedOn} says what it is, and where to draw it is the page's decision
     * rather than this record's — exactly as a product closed to new accounts stays in the
     * catalogue marked closed.
     */
    record SavingsAccountResponse(Long id, BigDecimal moneyBalance, String productCode,
                                  String productName, LocalDate closedOn) {
    }
}
