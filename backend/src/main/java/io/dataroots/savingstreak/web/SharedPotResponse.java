package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import io.dataroots.savingstreak.sharedpots.ASharedPot;

/**
 * A shared pot as the API reports one: what it is called, what it holds, which savings account holds
 * it, when it was opened, and who is in it with what role.
 *
 * <p>One shape for a pot just opened, a pot read back on its own and a pot in somebody's list, so
 * that nothing rendering a pot has to know which of those it is holding in order to render it. A pot
 * just opened holds nothing, and says so as 0.00 rather than as an absence.
 *
 * <p><strong>The balance is not the pot module's figure.</strong> It is what the pot's savings
 * account holds, which is the sum of what the deposits into it still hold, and it is put beside the
 * pot here — the same assembling the goals endpoints do with the balance goals are claims against.
 * The module that owns the pot owns no money, and a figure stored on the pot would be a second
 * answer to the one question everybody saving into it is asking.
 *
 * <p>The savings account is named because that is where a member pays in: a deposit into a pot is
 * made through the deposit endpoint that already exists, against this account. It is held by nobody,
 * so it appears in no customer's list of accounts and in nobody's totals.
 *
 * <p><strong>{@code closedAt} is null for as long as the pot is open, and a page reads it as the
 * word "closed".</strong> A closed pot is still here, still readable and still in each member's list
 * of pots — which is the point of closing rather than deleting — so the only thing that tells the
 * two apart has to travel on the one shape a pot arrives in, or a list would show a finished pot as
 * though the group were still saving into it. A moment rather than a flag, so that the page can also
 * say when.
 *
 * <p>A closed pot's balance is 0.00, because closing returned every member's own euros to them. It
 * is read rather than assumed, like every other balance here.
 */
record SharedPotResponse(Long id, String name, BigDecimal moneyBalance, Long savingsAccountId,
                         Instant openedAt, Instant closedAt, List<PotMemberResponse> members) {

    static SharedPotResponse of(ASharedPot pot, BigDecimal moneyBalance) {
        return new SharedPotResponse(pot.id(), pot.name(), moneyBalance, pot.savingsAccountId(),
                pot.openedAt(), pot.closedAt(),
                pot.members().stream().map(PotMemberResponse::of).toList());
    }
}
