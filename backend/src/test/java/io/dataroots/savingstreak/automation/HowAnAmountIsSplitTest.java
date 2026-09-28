package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cents behind a split: that the pieces add up to exactly what was moved, for every arrangement
 * of shares and every amount that does not divide evenly, and that the leftover cents go where this
 * application says they go.
 *
 * <p>A direct unit test rather than an API one, and the exception is the one
 * {@code WhichOccurrencesAreDueTest}, {@code HowTheWeeklyMoneyIsSpentTest} and
 * {@code AReallocationWorthSuggestingTest} already take: this is a function of its arguments with no
 * database, no clock and no HTTP in it, and the combinatorics it has to be right across — a thousand
 * amounts against a dozen share arrangements — are a day of wound clocks and round-trips otherwise.
 * What a rule <em>does</em> with the answer is asserted over HTTP, where everything else in this
 * feature is.
 *
 * <p><strong>A split of 100.00 by 60/30/10 is exact and proves nothing.</strong> It is here because
 * it is the brief's own example and somebody has to be able to read it, but every other test in this
 * class is about an amount that does not divide evenly — which is where a rounding rule loses a cent,
 * and where a balance and a goals page stop agreeing.
 */
class HowAnAmountIsSplitTest {

    /** The brief's own split, which divides 100.00 exactly and 100.01 not at all. */
    private static final List<Integer> SIXTY_THIRTY_TEN = List.of(60, 30, 10);

    /** Three shares that cannot divide anything evenly: a hundred is not three times thirty-three. */
    private static final List<Integer> THREE_UNEVEN_THIRDS = List.of(34, 33, 33);

    // ------------------------------------------------------- the split the brief actually names

    @Test
    void sixty_thirty_ten_of_a_hundred_euros_is_sixty_thirty_and_ten() {
        assertThat(HowAnAmountIsSplit.ofAmountBy(new BigDecimal("100.00"), SIXTY_THIRTY_TEN))
                .as("a split that divides evenly divides evenly, and the shares come back in the "
                        + "order the customer wrote them")
                .containsExactly(
                        new BigDecimal("60.00"), new BigDecimal("30.00"), new BigDecimal("10.00"));
    }

    @Test
    void the_pieces_come_back_in_the_order_the_shares_were_given() {
        assertThat(HowAnAmountIsSplit.ofAmountBy(new BigDecimal("100.00"), List.of(10, 30, 60)))
                .as("the answer is positional: the same three shares in another order are the same "
                        + "three figures in that other order, so a caller walking its split and this "
                        + "answer together never has to match them up")
                .containsExactly(
                        new BigDecimal("10.00"), new BigDecimal("30.00"), new BigDecimal("60.00"));
    }

    // ------------------------------------------------------------------- the leftover cent

    @Test
    void a_leftover_cent_goes_to_the_share_that_was_furthest_past_a_whole_cent() {
        // 100.01 by 34/33/33: the exact figures are 34.0034, 33.0033 and 33.0033, so flooring gives
        // 34.00 + 33.00 + 33.00 = 100.00 and one cent is over. The 34 was 0.34 of a cent past its
        // floor and the other two 0.33, so the cent is the 34's.
        assertThat(HowAnAmountIsSplit.ofAmountBy(new BigDecimal("100.01"), THREE_UNEVEN_THIRDS))
                .as("the leftover cent goes by largest remainder")
                .containsExactly(
                        new BigDecimal("34.01"), new BigDecimal("33.00"), new BigDecimal("33.00"));
    }

    @Test
    void the_leftover_cent_is_not_simply_the_first_shares() {
        // The same three shares with the biggest one written last. A rule that handed every leftover
        // cent to whoever was written first would pass the test above and fail this one, which is
        // the whole reason both are here: "largest remainder" and "the first one" agree above and
        // disagree here.
        assertThat(HowAnAmountIsSplit.ofAmountBy(new BigDecimal("100.01"), List.of(33, 33, 34)))
                .as("the cent follows the biggest remainder wherever the customer wrote it")
                .containsExactly(
                        new BigDecimal("33.00"), new BigDecimal("33.00"), new BigDecimal("34.01"));
    }

    @Test
    void a_tie_for_a_leftover_cent_is_broken_by_the_order_the_customer_wrote_the_split() {
        // Both shares are 0.50 of a cent past their floor, so nothing but the order can separate
        // them. Without a tie-break this answer would depend on which way a sort happened to fall.
        assertThat(HowAnAmountIsSplit.ofAmountBy(new BigDecimal("100.01"), List.of(50, 50)))
                .as("the first share the customer wrote takes the cent")
                .containsExactly(new BigDecimal("50.01"), new BigDecimal("50.00"));
    }

    @Test
    void several_leftover_cents_are_handed_out_one_each_rather_than_all_to_one_share() {
        // 10.03 by six shares: the four 17s come to 1.7051 each and the two 16s to 1.6048, so
        // flooring gives 10.00 and three cents are over. The three go to three of the four 0.51s,
        // separated by the order the customer wrote them, rather than all three to one share.
        List<BigDecimal> pieces = HowAnAmountIsSplit.ofAmountBy(
                new BigDecimal("10.03"), List.of(17, 17, 17, 17, 16, 16));
        assertThat(pieces)
                .containsExactly(
                        new BigDecimal("1.71"), new BigDecimal("1.71"), new BigDecimal("1.71"),
                        new BigDecimal("1.70"), new BigDecimal("1.60"), new BigDecimal("1.60"));
        assertThat(addedUp(pieces)).isEqualByComparingTo(new BigDecimal("10.03"));
    }

    // ------------------------------------------------------------- the invariant, exhaustively

    @Test
    void the_pieces_always_add_up_to_exactly_the_amount_that_was_split() {
        List<List<Integer>> everyArrangement = List.of(
                List.of(100),
                List.of(50, 50),
                List.of(60, 30, 10),
                List.of(34, 33, 33),
                List.of(1, 99),
                List.of(99, 1),
                List.of(33, 33, 33, 1),
                List.of(17, 17, 17, 17, 16, 16),
                List.of(7, 11, 13, 17, 19, 33),
                List.of(1, 1, 1, 1, 1, 1, 1, 1, 1, 91));

        for (List<Integer> shares : everyArrangement) {
            // Every amount from a cent to a hundred euros, cent by cent, which is ten thousand
            // amounts against each arrangement. A rounding rule that loses a cent loses it on one of
            // them, and the point of doing it here rather than over HTTP is that this is a second.
            for (long cents = 1; cents <= 10_000; cents++) {
                BigDecimal amount = BigDecimal.valueOf(cents, 2);
                List<BigDecimal> pieces = HowAnAmountIsSplit.ofAmountBy(amount, shares);
                assertThat(addedUp(pieces))
                        .as("splitting " + amount + " by " + shares + " came to " + pieces
                                + ", which does not add up to what was split — a cent that appears "
                                + "or vanishes here is a balance and a goals page that stop agreeing")
                        .isEqualByComparingTo(amount);
                assertThat(pieces)
                        .as("splitting " + amount + " by " + shares + " gave a share of its own")
                        .hasSameSizeAs(shares)
                        .allSatisfy(piece -> assertThat(piece.signum())
                                .as("no share of a positive amount is negative")
                                .isNotNegative());
            }
        }
    }

    @Test
    void a_share_is_never_more_than_a_cent_away_from_its_exact_proportion() {
        // The other half of "the cents add up": a rule that handed the whole amount to the first
        // share would add up perfectly and be a split in name only. Flooring plus at most one cent
        // is the whole of what this rule does, so no share can be off by a cent or more.
        for (long cents = 1; cents <= 5_000; cents++) {
            BigDecimal amount = BigDecimal.valueOf(cents, 2);
            List<Integer> shares = List.of(60, 30, 10);
            List<BigDecimal> pieces = HowAnAmountIsSplit.ofAmountBy(amount, shares);
            for (int i = 0; i < shares.size(); i++) {
                BigDecimal exact = amount.multiply(BigDecimal.valueOf(shares.get(i)))
                        .divide(BigDecimal.valueOf(100));
                assertThat(pieces.get(i).subtract(exact).abs())
                        .as("splitting " + amount + " by " + shares + " gave share " + i + " "
                                + pieces.get(i) + ", which is more than a cent from its exact "
                                + exact)
                        .isLessThan(new BigDecimal("0.01"));
            }
        }
    }

    // ------------------------------------------------------------------ nothing to split

    @Test
    void a_rule_with_no_split_has_nothing_to_divide() {
        assertThat(HowAnAmountIsSplit.ofAmountBy(new BigDecimal("50.00"), List.of()))
                .as("no shares is no pieces, which is the ordinary rule: what it moves lands "
                        + "unallocated, exactly as a manual deposit does")
                .isEmpty();
    }

    @Test
    void nothing_split_three_ways_is_nothing_three_times() {
        assertThat(HowAnAmountIsSplit.ofAmountBy(new BigDecimal("0.00"), SIXTY_THIRTY_TEN))
                .as("there is no money to divide, and every share of it is nothing — quoted as "
                        + "money, because what a share comes to is money")
                .containsExactly(
                        new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"));
    }

    private static BigDecimal addedUp(List<BigDecimal> pieces) {
        BigDecimal total = BigDecimal.ZERO;
        List<BigDecimal> each = new ArrayList<>(pieces);
        for (BigDecimal piece : each) {
            total = total.add(piece);
        }
        return total;
    }
}
