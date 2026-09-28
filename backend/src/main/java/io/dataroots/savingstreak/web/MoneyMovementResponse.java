package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import io.dataroots.savingstreak.accounts.ABillOnTheLedger;
import io.dataroots.savingstreak.budgets.ARecordedSpend;
import io.dataroots.savingstreak.deposits.MoneyMovement;

/**
 * One entry in the money-movement ledger as the API reports it.
 *
 * <p>The direction travels as its own name — {@code INTO_SAVINGS}, {@code INTEREST_INTO_SAVINGS},
 * {@code OUT_OF_SAVINGS}, {@code OUT_OF_CURRENT_ACCOUNT} or {@code SPENT_OUT_OF_CURRENT_ACCOUNT} —
 * rather than as a sign on the amount or as a boolean. A page rendering the ledger decides what to call each kind and which
 * way round to draw the arrow, and both of those are easier to get right from a word than from a
 * minus sign. It is also what tells the four kinds apart: everything a bill row carries and a
 * savings movement does not hangs off that word, and so does everything a spend row carries.
 *
 * <p><strong>{@code automatic} says a saving rule made this movement rather than a person.</strong>
 * It is the one thing that lets a customer tell what they did from what the application did for
 * them, and it is assembled here rather than answered by {@code deposits}: that module does not know
 * this application automates anything, and a deposit that could say whether it was automatic would
 * be Deposits reading Automation — the cycle the whole module boundary exists to avoid. The
 * controller asks Automation which of the deposits on the page it made and decorates them, which is
 * the arrangement the feature's spec names.
 *
 * <p>False on every movement out of savings, and it is a statement rather than a gap: nothing in
 * this application takes money back out automatically, so a withdrawal is always somebody pressing a
 * button. False on a bill as well, and that is not a hedge — a bill is neither a person pressing a
 * button nor a saving rule, it is a declaration the customer made once and the word means the rule.
 *
 * <p><strong>A bill row fills in four components no other kind has and leaves
 * {@code savingsAccountId} empty.</strong> Money leaving a current account for the rent never goes near a savings account,
 * and an identifier invented to fill the hole would be a row claiming a pot it never touched. The
 * four it does fill are what the rent is: the name the customer recognises, the day it was owed
 * from, whether it was actually paid, and how late it was by the time it was. {@code movedAt} is the
 * moment it was settled, which is what the whole list is ordered by — and on an unpaid attempt it is
 * the night it was presented and refused, which is where a customer looking for the missing money
 * would go first.
 *
 * <p><strong>A spend row fills in three of its own and leaves {@code savingsAccountId} empty
 * too.</strong> Money spent on groceries never goes near savings either, and a spend is not a bill:
 * it has one date rather than two, it was never declared in advance, and what it was for is a split
 * across several categories rather than one label. So it carries its own name, the split it is
 * filed under, and the moment it was corrected at — which is nothing at all on a spend nobody has
 * corrected, and that null is the whole of what the component is for. The ledger says plainly which
 * spends were not right the first time rather than quietly reading differently from the way it read
 * yesterday.
 *
 * <p>A spend's name is its own component rather than {@code billName} reused. They are the same
 * length of string and they are not the same fact: a bill's name is a declaration the customer made
 * once and every occurrence of it repeats, a spend's is what they called one afternoon. One
 * component meaning either would be a row a page has to consult the direction twice to read, and the
 * first rename of one of them would silently rename the other.
 *
 * <p><strong>A month's interest is the one kind with no current account at either end</strong>, and
 * {@code currentAccountId} is null on it and on nothing else. Nothing was debited to pay it: the
 * bank added it to the savings account, so there is no second account for the row to name and an
 * identifier invented to fill the column would have a page drawing an arrow from an account that
 * was never touched. It earns nothing and was made by nobody pressing a button, and both of those
 * are said as the figures they are, exactly as they are on a bill and on a spend.
 *
 * <p>Each kind's components are null on the other three — a deposit and a withdrawal have no second
 * date, no split and no name beyond the accounts at either end of them. A page reads the direction
 * and knows which shape it is holding.
 *
 * <p><strong>{@code toSavingsAccountId} belongs to a sixth kind and is null on every other.</strong>
 * A move between two of a customer's own savings accounts is the only entry in this ledger with
 * savings at both ends, and it arrives as one row rather than as a withdrawal here and a deposit
 * next door — because the customer did one thing, and a page handed two halves would have to pair
 * them by amount and moment to draw the arrow. {@code savingsAccountId} is where the euros left
 * from and this is where they arrived, so a row reads as the sentence it is. {@code currentAccountId}
 * is null on it for the reason it is null on a month's interest: no everyday account was touched at
 * either end, and one invented to fill the component would have a page drawing an arrow at an
 * account nothing reached.
 */
record MoneyMovementResponse(String direction, long id, Long savingsAccountId, Long currentAccountId,
                             BigDecimal amount, long pointsEarned, Instant movedAt,
                             boolean automatic, String billName, LocalDate dueOn, String outcome,
                             Long daysLate, String spendName, Instant correctedAt,
                             List<SpendPartResponse> parts, Long toSavingsAccountId) {

    /**
     * The word a bill row travels under, from the account it left rather than the one it never
     * reached.
     *
     * <p>Named the way {@code MoneyMovementDirection}'s two are — from the point of view of the
     * account whose balance the movement changed — which for a bill is the everyday account. It is
     * not a member of that enum because that enum is Deposits' word for what crosses the boundary
     * into savings, and a bill never crosses it. The ledger is where the two vocabularies meet, so
     * the word is spelled here.
     */
    static final String OUT_OF_CURRENT_ACCOUNT = "OUT_OF_CURRENT_ACCOUNT";

    /**
     * The word a spend row travels under, from the account it left — and a different word from the
     * bill's although the money leaves the same account the same way.
     *
     * <p>Different because the direction is what tells the kinds apart, and these two are not one
     * kind. A bill is a declaration the customer made in advance and every date it falls due
     * repeats; a spend is one afternoon, named afterwards, split across categories and correctable.
     * A page handed one word for both would have to look for a second field to find out which shape
     * it is holding, which is exactly the reading the direction exists to spare it.
     *
     * <p>It also keeps the tie-break honest. Bill occurrences and spends are numbered from separate
     * sequences, so two of them settled inside one millisecond are only ordered at all because the
     * direction is compared before the identifier — one word for both would make that comparison
     * fall through to two numbers that mean different things.
     */
    static final String SPENT_OUT_OF_CURRENT_ACCOUNT = "SPENT_OUT_OF_CURRENT_ACCOUNT";

    /**
     * Newest first across all four kinds, and the moment settles it: two entries inside one
     * millisecond are ordered by kind and then by identifier, so that a ledger read twice reads the
     * same way both times.
     *
     * <p>The same rule {@code MoneyMovementsService} sorts its own half by, restated here because
     * this is where the halves become one list. Deposits, withdrawals, bill occurrences and spends
     * are numbered from separate sequences, so the identifier only means anything alongside the
     * direction — which is why the direction is the tie-break before it rather than after.
     *
     * <p>A whole nightly run shares one moment, so the tie-break is not an edge case: a rent taken
     * and a phone bill taken by the same run are one millisecond apart at most, and without this
     * they would swap places between two reads of the same page.
     */
    static final Comparator<MoneyMovementResponse> NEWEST_FIRST =
            Comparator.comparing(MoneyMovementResponse::movedAt).reversed()
                    .thenComparing(MoneyMovementResponse::direction)
                    .thenComparing(Comparator.comparingLong(MoneyMovementResponse::id).reversed());

    static MoneyMovementResponse of(MoneyMovement movement, boolean automatic) {
        return new MoneyMovementResponse(movement.direction().name(), movement.id(),
                movement.savingsAccountId(), movement.currentAccountId(), movement.amount(),
                movement.pointsEarned(), movement.movedAt(), automatic, null, null, null, null,
                null, null, null, movement.toSavingsAccountId());
    }

    /**
     * One date a bill fell due on, as a row in the same list.
     *
     * <p>It earns nothing and it was made by nobody pressing a button, and both of those are said as
     * the figures they are rather than left out: a bill has never earned a point in this
     * application, exactly as a withdrawal has not.
     */
    static MoneyMovementResponse ofABill(ABillOnTheLedger bill) {
        return new MoneyMovementResponse(OUT_OF_CURRENT_ACCOUNT, bill.occurrenceId(), null,
                bill.currentAccountId(), bill.amount(), 0, bill.settledAt(), false, bill.billName(),
                bill.dueOn(), bill.outcome().name(), bill.daysLate(), null, null, null, null);
    }

    /**
     * One spend a customer recorded, as a row in the same list.
     *
     * <p><strong>It sits at the moment it was recorded</strong>, which is also the moment the money
     * left: a spend is not backdated in this application, so unlike a bill it has one date and there
     * is nothing to choose between. {@code movedAt} is that moment and it does not move when the
     * split is corrected afterwards — only the opinion about what the money was for changed, and the
     * row would otherwise jump up the page every time somebody fixed a category.
     *
     * <p>It earns nothing and it was made by nobody pressing a saving rule, and both of those are
     * said as the figures they are rather than left out, exactly as they are on a bill.
     *
     * <p>The split comes down with it rather than behind a second request per row. A spend without
     * its parts is half a record — the amount says a balance fell and the split says what its holder
     * believes it fell for — and a page drawing a ledger would make one request per row to put them
     * back together. The parts arrive already named by the module that owns the words, ended
     * categories included.
     */
    static MoneyMovementResponse ofASpend(ARecordedSpend spend) {
        return new MoneyMovementResponse(SPENT_OUT_OF_CURRENT_ACCOUNT, spend.spendId(), null,
                spend.currentAccountId(), spend.amount(), 0, spend.recordedAt(), false, null, null,
                null, null, spend.name(), spend.correctedAt(),
                spend.parts().stream().map(SpendPartResponse::of).toList(), null);
    }
}
