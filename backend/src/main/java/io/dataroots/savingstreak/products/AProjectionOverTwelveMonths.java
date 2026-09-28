package io.dataroots.savingstreak.products;

import java.math.BigDecimal;

/**
 * What a named amount would be worth after twelve months in one product: the product itself, the
 * euros of interest, and the points — so that the difference between two products is a figure
 * rather than an adjective.
 *
 * <p><strong>The product travels with its projection rather than beside it.</strong> A screen
 * drawing four cards needs the rate, the condition, the loyalty benefits and the figure in the same
 * row, and two lists to be zipped together by code is two lists that can be zipped in the wrong
 * order — or drawn from two reads taken either side of an administrator closing a product. One
 * answer per card is what makes "these are the four things you can open, and this is what each
 * would pay you" a single true sentence.
 *
 * <p><strong>Only products open to new accounts are ever projected.</strong> That is the one thing
 * this reading does that {@link ProductsService#catalogue()} deliberately does not: the catalogue
 * includes a closed product because customers are holding it, and this is a screen about what to
 * open. Offering a figure for an agreement nobody will sign would be inviting somebody to choose
 * it. Whoever holds an account on a closed product reads their own agreement panel, which is a
 * different question asked in a different place.
 *
 * <p><strong>Two interest figures, and the second one is why a minimum-balance product is not a
 * liar.</strong> {@link #interest} is what the money earns if it is put in and left alone — the
 * floor judged against the balance as it actually stands, which is the honest reading and the one a
 * wound-forward clock will reproduce to the cent. {@link #interestIfTheFloorIsNotKept} is the same
 * twelve months at the headline rate alone, and it is present on exactly the products that have a
 * bonus to lose. A core saver's headline figure means nothing on its own: one of those two numbers
 * is what the customer gets and which one it is depends on a discipline they have not yet been
 * asked to keep, so the card shows both and {@link #theBonusIsInThatFigure} says which of them the
 * first one is.
 *
 * <p><strong>The points are two figures added up, because they are earned on two different
 * days.</strong> One when the money lands, at the product's own multiple; one a year later, when
 * its first and only anniversary inside this window falls. A single total would hide that a notice
 * account's better anniversary is worth nothing to somebody who will move the money in six months,
 * and hiding that is the opposite of what a comparison is for.
 *
 * <p>Nothing here is about a customer. There is no streak in it, no balance they already hold and
 * no account: the same answer for everybody, in the way the rewards catalogue is, which is what
 * makes it something to weigh before holding anything.
 */
public record AProjectionOverTwelveMonths(

        /** The product this is a projection of, with the terms it is selling today. */
        AProductOnOffer product,

        /** The figure that was typed, quoted back to the cent so the card can print it. */
        BigDecimal amount,

        /** The euros of interest twelve months would pay on it, money left alone. */
        BigDecimal interest,

        /** What the account would hold at the end of them: the amount and its interest. */
        BigDecimal balanceAfterTwelveMonths,

        /**
         * Whether the bonus rate is part of {@link #interest} — false on the three products with no
         * bonus to earn, and false on a floored one where the amount itself is under the floor.
         */
        boolean theBonusIsInThatFigure,

        /**
         * The same twelve months at the headline rate alone, and null on a product with no bonus to
         * lose. The two figures are the cost of dipping, priced.
         */
        BigDecimal interestIfTheFloorIsNotKept,

        /** Points the amount earns the day it lands, at the product's multiple and no streak. */
        long pointsWhenTheMoneyLands,

        /** Points its first anniversary pays, a year later, at what this product pays for staying. */
        long pointsOnItsFirstAnniversary,

        /** The two of them together, which is what twelve months in this product is worth in points. */
        long points) {
}
