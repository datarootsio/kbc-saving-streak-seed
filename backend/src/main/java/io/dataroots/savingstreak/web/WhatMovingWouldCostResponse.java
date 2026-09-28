package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.loyalty.WhatMovingWouldCostInLoyalty;

/**
 * What moving money to another of your own savings accounts would cost, as the API reports it
 * before anything is confirmed.
 *
 * <p><strong>A reading rather than a movement, and it is the whole of the honesty of this
 * feature.</strong> Moving money is free of everything a customer would expect it to cost: it earns
 * no points and loses none, it neither secures a week nor breaks a streak, and the most they have
 * ever saved does not move. The one price is the loyalty clock, and a screen that let somebody find
 * that out afterwards would be hiding the only thing there was to weigh.
 *
 * <p>Every figure in it is the domain's, unchanged. Which deposits would be drawn down is the
 * Deposits module's rule and what their anniversaries are worth is the Loyalty module's, and this
 * record carries both without adding an opinion — so the day quoted here and the day reported by
 * the move itself are one day, worked out once.
 *
 * <p><strong>{@code theSoonestAnniversaryGivenUp} is null when there is nothing to give up</strong>,
 * which is a real answer rather than a gap: an account holding nothing but the interest the bank
 * paid has never had a clock of its own, so moving it forfeits nothing at all. A page draws no
 * "you would give up" line rather than a line with a blank in it.
 *
 * <p>Nothing here refuses. A move that would be turned away — by a notice period, an unmatured
 * term, a goal, a closed destination or two different holders — is turned away by this request too,
 * in the sentence the move itself would have used, and never quoted a price it was never going to
 * charge.
 */
record WhatMovingWouldCostResponse(long fromSavingsAccountId, long toSavingsAccountId,
                                   BigDecimal amount, LocalDate newAnniversary,
                                   long pointsOnTheNewAnniversary,
                                   LocalDate soonestAnniversaryGivenUp, long pointsGivenUp,
                                   int depositsItWouldDrawDown) {

    static WhatMovingWouldCostResponse of(WhatMovingWouldCostInLoyalty cost) {
        return new WhatMovingWouldCostResponse(cost.fromSavingsAccountId(),
                cost.toSavingsAccountId(), cost.amount(), cost.theNewAnniversary(),
                cost.pointsTheArrivingMoneyWouldBeWorthOnIt(), cost.theSoonestAnniversaryGivenUp(),
                cost.pointsGivenUp(), cost.depositsItWouldDrawDown());
    }
}
