package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.sharedpots.AContribution;

/**
 * What one member has put into a shared pot, as the API reports it: who they are, what they are to
 * the pot, how much they have paid in altogether, how much of their money is still in it, and how
 * many points their contributions to this pot have earned them.
 *
 * <p>The name as well as the identifier, so that the screen this feeds reads as people rather than
 * as numbers, and the role beside it so a page can say who was allowed to pay in at all — a viewer's
 * nought and a contributor's nought are different noughts, and only the role tells them apart.
 *
 * <p><strong>Two money figures rather than one, because they come apart.</strong> An approved
 * withdrawal draws the pot's oldest deposits down first whoever paid them in, so what somebody paid
 * in and what is still theirs stop agreeing the moment money leaves. Both go out, and what is still
 * each member's adds up across the rows to exactly what the pot holds.
 *
 * <p>Both are quoted to the cent, for the reason a pot's balance is: a figure that has been through
 * SQLite comes back as 500.5 — it has no decimal type and keeps an amount as a float — and a page
 * that had to decide how many places money has would be deciding it again.
 *
 * <p>The points are a whole number and they are the member's own. Nothing about a pot pools points,
 * so this is a statement about what this pot has been worth to each person rather than about
 * anything the pot holds.
 *
 * <p><strong>The role is null for somebody who has left the pot</strong>, and a page reads that
 * absence as "no longer a member". The list runs the pot's members first and then everybody else
 * whose contributions are in its account, so that what a departed member paid in survives their
 * leaving; a word like "LEFT" in this field would be a fourth role for a page to have to know about
 * and would not be one. {@code ProposalAnswerResponse} leaves the same field empty for the same
 * reason.
 */
record PotContributionResponse(Long customerId, String name, String role,
                               BigDecimal paidInAltogether, BigDecimal stillTheirs,
                               long pointsEarned) {

    static PotContributionResponse of(AContribution contribution) {
        return new PotContributionResponse(contribution.customerId(), contribution.name(),
                contribution.role() == null ? null : contribution.role().name(),
                AmountOfMoney.quotedToTheCent(contribution.paidInAltogether()),
                AmountOfMoney.quotedToTheCent(contribution.stillTheirs()),
                contribution.pointsEarned());
    }
}
