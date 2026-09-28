package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.sharedpots.ASettlement;

/**
 * A member's departure from a shared pot as the API reports it: who left, what they were to the pot,
 * what was still theirs, where it went, and what the pot holds now.
 *
 * <p>The figure is that member's own remaining euros, exact to the cent and quoted as money, because
 * a settlement is the one thing in this feature that hands somebody an amount nobody typed. A page
 * showing "you got back" has to be able to show it without deciding again how many places money has.
 *
 * <p>{@code withdrawalId} is the movement in the account's history, and it is null when there was
 * none: a member with nothing left in the pot leaves without any money moving, which is an ordinary
 * departure and not a failure to settle. A page can say "nothing came back" from that absence.
 *
 * <p>{@code toCurrentAccountId} is null in the same case and only in it. A departure names the
 * account; a settlement made by the pot closing reads it off the member's most recent contribution,
 * and somebody who never contributed has none to read and nothing to send there.
 *
 * <p>The role is the word the member held at the moment they stopped being one, the idiom every
 * other pot response uses: whoever renders it decides what to call {@code OWNER},
 * {@code CONTRIBUTOR} and {@code VIEWER}, and that is easier to get right from a word.
 *
 * <p>The moments come off the application's clock, so a departure against a wound-forward clock
 * reads where the trainer wound it to.
 */
record PotSettlementResponse(long potId, String potName, Long customerId, String name, String role,
                             Instant joinedAt, BigDecimal settled, Long toCurrentAccountId,
                             Long withdrawalId, Instant settledAt, BigDecimal thePotNowHolds) {

    static PotSettlementResponse of(ASettlement settlement) {
        return new PotSettlementResponse(settlement.potId(), settlement.potName(),
                settlement.customerId(), settlement.name(), settlement.role().name(),
                settlement.joinedAt(), settlement.settled(), settlement.toCurrentAccountId(),
                settlement.withdrawalId(), settlement.settledAt(), settlement.thePotNowHolds());
    }
}
