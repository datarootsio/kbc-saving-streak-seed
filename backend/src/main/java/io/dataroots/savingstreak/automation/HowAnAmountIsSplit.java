package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The cents: how an amount of money is divided by whole-percentage shares so that the pieces add up
 * to exactly the amount, every time.
 *
 * <p>A function of its arguments and nothing else — no database, no clock, no state — in the shape
 * {@link WhatARuleWouldMove}, {@code HowTheWeeklyMoneyIsSpent} and {@code AReallocationWorthSuggesting}
 * already set, and unit-tested directly for the reason those are: the combinatorics of "every share
 * arrangement that does not divide evenly" are an hour of HTTP round-trips otherwise.
 *
 * <p><strong>The invariant is the whole of this class.</strong> The pieces add to the amount, to the
 * cent, for every amount and every arrangement of shares. This application derives what no goal has
 * claimed as {@code balance − allocated} precisely so that two stored figures cannot drift apart; a
 * rounding rule that loses a cent would put that cent back into the drift by the back door, and a
 * balance and a goals page that stop agreeing is the defect this feature exists to avoid. A split of
 * 100 cents by 60/30/10 is exact and proves nothing. A split of 10 001 cents by three shares of
 * 34/33/33 is where a rule gets this wrong.
 *
 * <p><strong>Largest remainder, ties broken by the order the customer wrote the split.</strong> Each
 * share is floored to the cent first, which always leaves fewer cents over than there are shares;
 * those cents are then handed out one each, to the shares whose exact figure was furthest past the
 * cent it was floored to. Two shares left over by the same fraction are separated by where they sit
 * in the split, which is the one ordering the customer themselves chose — so the answer is the same
 * every time the same split is fired, rather than depending on which order a map happened to iterate
 * in.
 *
 * <p>The alternative rules were rejected for saying something this one does not. Rounding each share
 * half-up independently does not add up: three shares of a third of 100.01 come to 100.02, which is
 * a cent this application would have to invent. Giving every leftover cent to the first share adds
 * up but concentrates the whole rounding error on whichever goal the customer happened to write
 * first, and on a monthly rule that is a cent a month for ever in one direction.
 *
 * <p>Whole cents throughout rather than {@code BigDecimal} division, because the question is about
 * whole cents: dividing in decimals would need a scale and a rounding mode chosen at every step, and
 * each of those choices is a place the total can stop adding up.
 */
final class HowAnAmountIsSplit {

    /** Shares are whole percentages, so they add to this and each exact figure is in hundredths. */
    private static final int A_WHOLE = 100;

    /** Two, like every other amount of money in this application. */
    private static final int DECIMAL_PLACES_IN_MONEY = 2;

    private HowAnAmountIsSplit() {
    }

    /**
     * What each share comes to, in the order the shares were given, adding to exactly {@code amount}.
     *
     * <p>The answer is positional: the figure at index {@code i} belongs to the share at index
     * {@code i}, so a caller that walks its split and this answer together never has to match them
     * up by goal — which is what lets one goal appear in a split at most once without this class
     * having to know that rule.
     *
     * @param amount         what is being split, an amount of money quoted to the cent. Nothing or
     *                       less answers nothing for every share: there is no money to divide, and
     *                       inventing a negative piece would be money travelling the other way
     * @param sharesInOrder  whole percentages in the order the customer wrote them, adding to a
     *                       hundred — which is a rule about a rule and is settled in
     *                       {@link AutomationService} before a split is ever stored
     */
    static List<BigDecimal> ofAmountBy(BigDecimal amount, List<Integer> sharesInOrder) {
        List<BigDecimal> pieces = new ArrayList<>(sharesInOrder.size());
        if (sharesInOrder.isEmpty()) {
            return pieces;
        }
        long cents = inWholeCents(amount);
        if (cents <= 0) {
            sharesInOrder.forEach(share -> pieces.add(nothing()));
            return pieces;
        }

        // Every share floored to the cent, and how far past that cent its exact figure reached. The
        // exact figure is in hundredths of a cent because a share is a whole percentage, so both
        // halves are whole numbers and neither of them can lose anything.
        long[] flooredCents = new long[sharesInOrder.size()];
        long handedOut = 0;
        List<AShareWithSomethingOver> byWhatIsOver = new ArrayList<>(sharesInOrder.size());
        for (int i = 0; i < sharesInOrder.size(); i++) {
            long exactInHundredthsOfACent = cents * sharesInOrder.get(i);
            flooredCents[i] = exactInHundredthsOfACent / A_WHOLE;
            handedOut += flooredCents[i];
            byWhatIsOver.add(new AShareWithSomethingOver(i, exactInHundredthsOfACent % A_WHOLE));
        }

        // What the flooring left over, which is always fewer cents than there are shares: each share
        // is left over by at most ninety-nine hundredths of a cent, so the lot come to less than one
        // cent per share. One each, biggest remainder first, and the order of the split breaks a tie.
        long leftOver = cents - handedOut;
        byWhatIsOver.sort(Comparator
                .comparingLong(AShareWithSomethingOver::hundredthsOfACentOver).reversed()
                .thenComparingInt(AShareWithSomethingOver::whereItSitsInTheSplit));
        for (int i = 0; i < leftOver; i++) {
            flooredCents[byWhatIsOver.get(i).whereItSitsInTheSplit()]++;
        }

        for (long piece : flooredCents) {
            pieces.add(BigDecimal.valueOf(piece, DECIMAL_PLACES_IN_MONEY));
        }
        return pieces;
    }

    /**
     * The amount as a whole number of cents.
     *
     * <p>Rounded half-up at the cent rather than truncated, and it is a rounding that never has
     * anything to do: everything this splits has already been through
     * {@code AmountOfMoney.quotedToTheCent}. It is here so that a figure that has been through
     * SQLite — which has no decimal type and holds an amount as a float — cannot arrive carrying a
     * trailing fraction and take a cent off the total.
     */
    private static long inWholeCents(BigDecimal amount) {
        return amount.movePointRight(DECIMAL_PLACES_IN_MONEY)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** Nothing, quoted to the cent, because what a share comes to is money and is written as money. */
    private static BigDecimal nothing() {
        return BigDecimal.ZERO.setScale(DECIMAL_PLACES_IN_MONEY);
    }

    /**
     * One share, where it sits in the split, and how far past a whole cent its exact figure reached
     * — in hundredths of a cent, which is exact because a share is a whole percentage.
     */
    private record AShareWithSomethingOver(int whereItSitsInTheSplit, long hundredthsOfACentOver) {
    }
}
