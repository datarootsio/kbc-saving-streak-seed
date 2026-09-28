package io.dataroots.savingstreak.loyalty;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What moving money from one savings account to another of your own would cost you in loyalty: the
 * anniversary the arriving euros would start counting towards, the soonest anniversary they would
 * give up, and what each of those is worth.
 *
 * <p><strong>The one price of a move, said before the button rather than after it.</strong> A move
 * is free in every other respect on purpose — it earns no points and it costs none, it neither
 * secures a week nor breaks a streak, and the most you have ever saved does not move — and that is
 * the whole point of it being an operation rather than a withdrawal somebody happens to follow with
 * a deposit. What it cannot be free of is the clock: the euros arrive as a new deposit dated today,
 * so the twelve months they were part-way through are gone and a fresh twelve begin. That is a real
 * cost, it is honest, and it is the thing the customer is actually being asked to weigh against a
 * better rate. An application that let them find out afterwards would be hiding the only price
 * there is.
 *
 * <p><strong>{@code pointsGivenUp} is a subtraction rather than a total.</strong> It is what the
 * deposits the move would empty are promised on their next anniversary <em>now</em>, less what they
 * would be promised once the move had taken its euros out of them — so a move that empties half a
 * deposit gives up half its anniversary and not the whole of it, which is exactly what a withdrawal
 * of the same size would do and is the reading the loyalty rule has always taken. Worked out from
 * the same rows the move would actually draw down, in the same order, so the figure quoted and the
 * euros moved are the same euros.
 *
 * <p><strong>{@code theSoonestAnniversaryGivenUp} is a day and not a count of them</strong>, because
 * the day is what a person weighs. Somebody eleven months into a clock is being asked a different
 * question from somebody eleven days in, and a number of points on its own cannot tell them which
 * they are. It is null when the move would give up no anniversary at all — a move out of an account
 * holding nothing but the interest the bank paid, which has never had a clock of its own.
 *
 * <p><strong>{@code theNewAnniversary} is twelve months from today</strong>, and
 * {@code pointsTheArrivingMoneyWouldBeWorthOnIt} is what the <em>destination</em> account's product
 * would pay on it. The two rates are not assumed to be the same: moving to a product that pays
 * twelve percent is worth more per euro than the tenth the money was earning, and a customer
 * weighing a move deserves both sides of that rather than the loss alone. Where the second figure is
 * the larger, the arithmetic says out loud that the move pays for itself in a year.
 *
 * <p>Nothing here is a promise about money staying put. Both figures are worked out at what the
 * accounts hold today, exactly as every other loyalty reading in this application is, and a
 * withdrawal tomorrow moves them.
 *
 * <p>Public because it crosses the boundary out of this module, and it is the Loyalty module's
 * answer rather than the Deposits module's: which euros would move is Deposits' question and is
 * asked of it, and what those euros were worth on their anniversaries is this one's.
 */
public record WhatMovingWouldCostInLoyalty(

        /** The account the euros would leave. */
        long fromSavingsAccountId,

        /** The account they would arrive in. */
        long toSavingsAccountId,

        /** What would move, quoted to the cent. */
        BigDecimal amount,

        /**
         * The day the arriving money's first anniversary falls, which is twelve months from the
         * day of the move.
         */
        LocalDate theNewAnniversary,

        /** What that anniversary would pay, at the rate the destination account's product pays. */
        long pointsTheArrivingMoneyWouldBeWorthOnIt,

        /**
         * The earliest anniversary among the deposits the move would draw down, and null when it
         * would draw down nothing that has a clock.
         */
        LocalDate theSoonestAnniversaryGivenUp,

        /**
         * What those deposits' next anniversaries would lose by the move, in points: what they are
         * promised now less what they would be promised afterwards.
         */
        long pointsGivenUp,

        /** How many deposits the move would take euros out of, which is what the two figures are summed over. */
        int depositsItWouldDrawDown) {
}
