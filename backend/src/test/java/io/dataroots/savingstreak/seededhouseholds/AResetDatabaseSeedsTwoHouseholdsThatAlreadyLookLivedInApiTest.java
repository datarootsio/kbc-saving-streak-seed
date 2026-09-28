package io.dataroots.savingstreak.seededhouseholds;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.RecurringBillView;
import io.dataroots.savingstreak.support.TheCurrentAccountView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A database with nothing in it comes back with two households already living in it — a salary
 * arriving and real bills going out — and a second database seeded from nothing comes back with
 * exactly the same two.
 *
 * <p>User stories 38, 39 and 41. The first two are what a demonstration needs in its first minute:
 * an account that has to be typed into for twenty minutes before it shows anything is an account
 * nobody sees the trade-off in, and one household on its own teaches nothing by comparison. The
 * third is what makes the demonstration worth giving twice — the seed is a set of declarations and
 * not a generator, so what was shown yesterday is what is shown today.
 *
 * <p><strong>Two applications, deliberately.</strong> "Resetting the database produces the same
 * accounts" cannot be asserted from one database: every figure in it would agree with itself. Two
 * applications on two files that never existed before are two resets, and the comparison between
 * them is the whole claim. What is compared is the household as a person reads it — the iban, the
 * balance, the declared income and the declared bills — and not the moments they were declared at,
 * which are two different real instants and are meant to be.
 *
 * <p>The opening figures are asserted by hand as well as against each other, because the two could
 * agree perfectly on the wrong household. The names, the contact details, the ibans and the opening
 * balances are the constants the shared test fixtures find their accounts by, and this is the test
 * that says out loud that declaring an income and four bills changed none of them.
 */
class AResetDatabaseSeedsTwoHouseholdsThatAlreadyLookLivedInApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove oneReset;
    private static AnApplicationWithAClockToMove anotherReset;

    @BeforeAll
    static void resetTheDatabaseTwice() {
        oneReset = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-seeded-households-one"));
        anotherReset = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-seeded-households-another"));
    }

    @AfterAll
    static void stopTheApplications() {
        if (oneReset != null) {
            oneReset.close();
        }
        if (anotherReset != null) {
            anotherReset.close();
        }
    }

    @Test
    void anke_is_the_comfortable_household_and_arrives_with_a_salary_and_four_bills() {
        TheCurrentAccountView hers = oneReset.theCurrentAccountOf(ANKE);

        assertThat(hers.customerName()).isEqualTo("Anke Peeters");
        assertThat(oneReset.contactDetailsOf(ANKE)).isEqualTo("anke.peeters@example.be");
        assertThat(hers.iban())
                .as("the iban the shared fixtures find her account by, untouched by this feature")
                .isEqualTo("BE68539007547034");
        assertThat(hers.balance())
                .as("and the opening balance they assert against, untouched too")
                .isEqualByComparingTo(new BigDecimal("2480.00"));

        assertThat(hers.income().declared())
                .as("a demonstration starts from a household with money coming in")
                .isTrue();
        assertThat(hers.income().dayOfMonth()).isEqualTo(25);
        assertThat(hers.income().amount()).isEqualByComparingTo(new BigDecimal("2600.00"));

        assertThat(asDeclared(hers.bills()))
                .as("rent, energy, phone and insurance, in the order they were declared")
                .containsExactly("Rent 950.00 on day 1", "Energy 120.00 on day 5",
                        "Phone 35.00 on day 12", "Insurance 60.00 on day 20");
        assertThat(totalOf(hers.bills()))
                .as("about a thousand one hundred out against two thousand six hundred in: real "
                        + "room to save, which is what makes her the household saving works on")
                .isEqualByComparingTo(new BigDecimal("1165.00"));
        assertThat(hers.arrears())
                .as("nothing is owed on a household nothing has yet been presented to")
                .isEmpty();
    }

    @Test
    void bram_is_the_tight_household_and_his_rent_falls_four_days_after_payday() {
        TheCurrentAccountView his = oneReset.theCurrentAccountOf(BRAM);

        assertThat(his.customerName()).isEqualTo("Bram De Vos");
        assertThat(oneReset.contactDetailsOf(BRAM)).isEqualTo("bram.devos@example.be");
        assertThat(his.iban()).isEqualTo("BE87734291658494");
        assertThat(his.balance()).isEqualByComparingTo(new BigDecimal("1150.00"));

        assertThat(his.income().declared()).isTrue();
        assertThat(his.income().dayOfMonth())
                .as("the 28th, so that the rent on the 1st falls four days after his salary — which "
                        + "is what a rule taking that salary on the morning it lands starves")
                .isEqualTo(28);
        assertThat(his.income().amount()).isEqualByComparingTo(new BigDecimal("1750.00"));

        assertThat(asDeclared(his.bills()))
                .containsExactly("Rent 820.00 on day 1", "Energy 95.00 on day 5",
                        "Phone 30.00 on day 12");
        assertThat(totalOf(his.bills()))
                .as("945 out against 1750 in: about 805 of room, which is enough to save steadily "
                        + "every month and not enough to survive sweeping the lot")
                .isEqualByComparingTo(new BigDecimal("945.00"));
        assertThat(his.arrears()).isEmpty();
    }

    @Test
    void the_two_households_differ_in_shape_and_not_only_in_size() {
        TheCurrentAccountView hers = oneReset.theCurrentAccountOf(ANKE);
        TheCurrentAccountView his = oneReset.theCurrentAccountOf(BRAM);

        assertThat(roomToSaveIn(hers))
                .as("Anke's room every month, which a rule can be sized well inside")
                .isEqualByComparingTo(new BigDecimal("1435.00"));
        assertThat(roomToSaveIn(his))
                .as("and Bram's, which is real but narrow — the gap between the two is the whole "
                        + "reason there are two households rather than one")
                .isEqualByComparingTo(new BigDecimal("805.00"));
        assertThat(roomToSaveIn(his))
                .as("his failure has to be reachable and must not be automatic: room of more than "
                        + "nothing is what lets him save every month without missing anything")
                .isPositive();
    }

    @Test
    void a_second_reset_produces_the_identical_pair_of_households() {
        assertThat(theHouseholdOf(anotherReset, ANKE))
                .as("what a trainer demonstrated yesterday is what they demonstrate today")
                .isEqualTo(theHouseholdOf(oneReset, ANKE));
        assertThat(theHouseholdOf(anotherReset, BRAM))
                .isEqualTo(theHouseholdOf(oneReset, BRAM));
    }

    /**
     * One household written out as a person reads it, for comparing two resets against each other.
     *
     * <p>The moments the declarations were made at are deliberately left out. They are two different
     * real instants — the two applications were started one after the other — and a comparison that
     * included them would fail on the one thing a reset is not expected to repeat.
     */
    private static String theHouseholdOf(AnApplicationWithAClockToMove application, String who) {
        TheCurrentAccountView account = application.theCurrentAccountOf(who);
        return account.customerName() + " / " + account.iban() + " / holding "
                + account.balance().stripTrailingZeros().toPlainString() + " / paid "
                + account.income().amount().stripTrailingZeros().toPlainString() + " on day "
                + account.income().dayOfMonth() + " / owing "
                + String.join(", ", asDeclared(account.bills())) + " / arrears "
                + account.arrears().size();
    }

    private static List<String> asDeclared(List<RecurringBillView> bills) {
        return bills.stream()
                .map(bill -> bill.name() + " " + bill.amount().setScale(2).toPlainString()
                        + " on day " + bill.dayOfMonth())
                .collect(Collectors.toList());
    }

    private static BigDecimal totalOf(List<RecurringBillView> bills) {
        return bills.stream().map(RecurringBillView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal roomToSaveIn(TheCurrentAccountView account) {
        return account.income().amount().subtract(totalOf(account.bills()));
    }
}
