package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A shared pot brought to an end as the API reports it: when it closed, what it held, what it holds
 * now, what each member was given back, and which goals and proposals the close finished off.
 *
 * <p>Read as one record because closing answers as one record, which is the API's own promise: a
 * close does several things at once and says what all of them came to, so that a page — and a test —
 * does not have to go and read four screens to find out what happened to the money.
 *
 * <p>The settlements arrive in the shape a departure already answers in, so a test comparing "what
 * she got back on leaving" with "what she got back when it closed" is comparing the same record.
 */
public record PotClosedView(Long potId, String potName, Long savingsAccountId, Instant closedAt,
                            BigDecimal thePotHeld, BigDecimal thePotNowHolds,
                            List<PotSettlementView> settledTo, List<Long> goalsAbandoned,
                            List<Long> proposalsEnded) {
}
