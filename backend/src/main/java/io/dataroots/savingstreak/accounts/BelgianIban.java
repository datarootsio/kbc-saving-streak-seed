package io.dataroots.savingstreak.accounts;

import java.math.BigInteger;

/**
 * Makes up the account number a customer this application opens is given, in the shape a Belgian
 * one really has.
 *
 * <p>It is invented money in a training application, and it is still a well-formed IBAN on purpose.
 * The two seeded customers were written down with real check digits — {@code BE68539007547034} and
 * {@code BE87734291658494} both verify — so an account opened by the application itself computing
 * its digits instead of filling in something that merely looks the part is what keeps every IBAN on
 * these screens the same kind of thing. A participant who pastes one into a validator gets the same
 * answer whichever customer they took it from, and nobody has to know which accounts were seeded
 * and which were added.
 *
 * <p>Derived from the customer's identifier rather than drawn at random, so one customer has one
 * account number: the same person opened twice against the same database is refused before this is
 * ever reached, and a database rebuilt from scratch lays the same numbers out again. That makes a
 * screenshot of this application reproducible, which a random number would not.
 */
final class BelgianIban {

    /**
     * The bank code every account this application opens carries. Distinct from the two the seeded
     * customers were written with (539 and 734) so that an added customer is recognisable as one at
     * a glance, and otherwise arbitrary — no real institution is meant.
     */
    private static final String THE_BANK_CODE_THIS_APPLICATION_OPENS_UNDER = "050";

    /** A Belgian account number is seven digits, between the bank code and the national check. */
    private static final long MORE_ACCOUNTS_THAN_THIS_APPLICATION_WILL_EVER_OPEN = 10_000_000L;

    /** Belgium, as the two letters and as the 11 and 14 the check digits are computed over. */
    private static final String COUNTRY = "BE";
    private static final String COUNTRY_AS_DIGITS = "1114";

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private BelgianIban() {
    }

    /**
     * The IBAN for the current account opened for the customer with this identifier.
     *
     * <p>The identifier is wrapped rather than rejected when it is larger than seven digits can
     * hold. Nothing in this application can reach that — it would take ten million customers — and
     * refusing to open an account over the width of a number would be a worse answer than reusing
     * one, which is itself unreachable.
     */
    static String forCustomer(long customerId) {
        long accountNumber = Math.floorMod(customerId, MORE_ACCOUNTS_THAN_THIS_APPLICATION_WILL_EVER_OPEN);
        String withoutNationalCheck =
                THE_BANK_CODE_THIS_APPLICATION_OPENS_UNDER + "%07d".formatted(accountNumber);
        String bban = withoutNationalCheck + "%02d".formatted(nationalCheckOf(withoutNationalCheck));
        return COUNTRY + "%02d".formatted(checkDigitsOf(bban)) + bban;
    }

    /**
     * The last two digits of the account number itself: the ten before them read as a number, modulo
     * 97, with a remainder of nothing written as 97 rather than as 00. That last part is the rule
     * Belgium actually has, and it is the half a hand-rolled version gets wrong.
     */
    private static int nationalCheckOf(String bankCodeAndAccountNumber) {
        int remainder = (int) (Long.parseLong(bankCodeAndAccountNumber) % 97);
        return remainder == 0 ? 97 : remainder;
    }

    /**
     * The two digits after "BE", by the mod-97-10 rule every IBAN shares: move the country and the
     * placeholder check to the end, read the letters as numbers, and take what is left to reach 98.
     *
     * <p>{@link BigInteger} because the eighteen digits this produces do not fit in a {@code long}.
     */
    private static int checkDigitsOf(String bban) {
        BigInteger rearranged = new BigInteger(bban + COUNTRY_AS_DIGITS + "00");
        return 98 - rearranged.mod(NINETY_SEVEN).intValue();
    }
}
