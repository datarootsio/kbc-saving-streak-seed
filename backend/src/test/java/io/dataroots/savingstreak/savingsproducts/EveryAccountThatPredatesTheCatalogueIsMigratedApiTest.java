package io.dataroots.savingstreak.savingsproducts;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import io.dataroots.savingstreak.support.AnAgreementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A database written before this catalogue existed comes up with every savings account on free
 * savings at the bank's opening terms, every deposit stamped with the version it landed under, and
 * not one euro or point moved.
 *
 * <p><strong>Three starts against one file, with the agreements taken away by hand in between.</strong>
 * There is no other way to ask this question. An application with this release in it writes an
 * agreement the moment an account is opened, so an account without one is a row from an older
 * release — and the only way to have one is to make one. The first start builds a lab the way a
 * trainer would: deposits, a shared pot somebody has paid into, a savings account nobody has ever
 * paid into, and a clock wound forty days on afterwards so that "the day of the first deposit" and
 * "the day of the migration" are two different days and the reading can be asked which it used.
 * Then every agreement is deleted and every deposit's version cleared, which is exactly the shape
 * of the file the release before this one left behind. The second start is the migration. The third
 * asks whether it left well alone.
 *
 * <p>The edits are made with raw JDBC, for the reason the catalogue's own start-up test gives:
 * there is no API for un-writing an agreement and there never will be. What is under test is the
 * start-up behaviour, and a row changed by hand is a row changed by hand however it got that way.
 *
 * <p><strong>The load-bearing assertion is the one about version 1.</strong> Free savings published
 * a second version at a lower rate two months before this file was made, and it is the version
 * every account opened today goes onto. Not one of these accounts moves to it. That is the whole
 * promise of the feature — the bank does not rewrite the agreement your money is living under
 * overnight — and this is the only test in the suite that can catch it being broken.
 *
 * <p>Every start is closed before the next one opens and nothing is read from a running
 * application afterwards: what each start saw is captured while it is up and asserted once they are
 * all down. SQLite serialises writers, and two applications on one file is how a test earns an
 * intermittent failure about a locked database.
 */
class EveryAccountThatPredatesTheCatalogueIsMigratedApiTest extends ApiIntegrationTest {

    private static final Path DATABASE =
            aDatabaseFileThatDoesNotExistYet("saving-streak-accounts-migrated");

    /** Far enough that the day of the migration cannot be mistaken for the day of a deposit. */
    private static final int DAYS_BETWEEN_THE_SAVING_AND_THE_UPGRADE = 40;

    private static long theAccountSheHasPaidInto;
    private static long theAccountNobodyHasPaidInto;
    private static long thePotsAccount;
    private static long thePot;
    private static LocalDate theDayTheMoneyArrived;

    private static WhatAStartSaw beforeTheUpgrade;
    private static WhatAStartSaw afterTheMigration;
    private static WhatAStartSaw afterOneMoreStart;

    @BeforeAll
    static void buildALabForgetEveryAgreementAndStartAgainTwice() {
        beforeTheUpgrade = buildALabTheWayATrainerWould();
        forgetEveryAgreementByHand();
        afterTheMigration = aStart();
        afterOneMoreStart = aStart();
    }

    /**
     * An account with money in it comes up on free savings at version 1, dated at the day its money
     * first arrived rather than at the day of the upgrade.
     *
     * <p>The date is the whole of the second half of this assertion. An account nobody recorded an
     * opening date for has exactly one honest one — the day it first held money — and dating it at
     * the migration instead would tell a customer who has been saving since the spring that they
     * opened their account this morning.
     */
    @Test
    void an_account_with_deposits_is_dated_at_the_day_its_money_first_arrived() {
        AnAgreementView agreement = afterTheMigration.hers();

        assertThat(agreement).isNotNull();
        assertThat(agreement.productCode()).isEqualTo("INSTANT");
        assertThat(agreement.productName()).isEqualTo("Free savings");
        assertThat(agreement.productKind()).isEqualTo("INSTANT_ACCESS");
        assertThat(agreement.version()).isEqualTo(1);
        assertThat(agreement.openedOn()).isEqualTo(theDayTheMoneyArrived);
    }

    /** And one nobody has ever paid into is dated at the migration, because nothing else can date it. */
    @Test
    void an_account_with_no_deposits_is_dated_at_the_migration() {
        AnAgreementView agreement = afterTheMigration.herOtherOne();

        assertThat(agreement.version()).isEqualTo(1);
        assertThat(agreement.openedOn()).isEqualTo(afterTheMigration.theDayItRanOn());
        assertThat(agreement.openedOn()).isAfter(theDayTheMoneyArrived);
    }

    /**
     * No migrated account moves to the version free savings is selling today.
     *
     * <p>The second version has been on offer since two months before this file was made, and an
     * account opened this morning goes straight onto it — which is what the catalogue is asked here
     * rather than told. These accounts did not move, and must not: what a product is selling and
     * what an account is living under are two different sentences, and a migration that made them
     * one would be the bank changing somebody's terms overnight without asking.
     */
    @Test
    void no_migrated_account_moves_to_the_version_on_offer_today() {
        assertThat(afterTheMigration.whatFreeSavingsIsSellingToday()).isGreaterThan(1);
        assertThat(afterTheMigration.hers().version()).isEqualTo(1);
        assertThat(afterTheMigration.herOtherOne().version()).isEqualTo(1);
    }

    /**
     * The account behind a shared pot is migrated like any other, which is visible in the one place
     * a holderless account can be read: the deposits that landed in it.
     *
     * <p>A pot's account is held by nobody, so nothing about a customer can say anything about it —
     * and the version a contribution is stamped with is read off the account's agreement, so a
     * stamped contribution is proof that the agreement is there. If this migration had skipped the
     * accounts nobody holds, as the points and the deposits migrations before it both had to, these
     * rows would still say nothing at all.
     */
    @Test
    void the_account_behind_a_shared_pot_is_migrated_too() {
        assertThat(beforeTheUpgrade.contributionsToThePot()).isNotEmpty();
        assertThat(afterTheMigration.contributionsToThePot()).allSatisfy(
                contribution -> assertThat(contribution.termsVersion()).isEqualTo(1));
    }

    /** And every deposit that predates the catalogue says which version it landed under. */
    @Test
    void every_existing_deposit_is_stamped_with_the_version_its_account_is_on() {
        assertThat(afterTheMigration.herHistory()).hasSize(2);
        assertThat(afterTheMigration.herHistory())
                .allSatisfy(row -> assertThat(row.termsVersion()).isEqualTo(1));
    }

    /**
     * Not a euro, not a point, not a week and not a mark moved because of any of this.
     *
     * <p>The constraint the whole ticket is under, asserted on the figures it would show up in. The
     * migration writes one new row per account and fills in one column per deposit; everything a
     * customer can see is summed from records it never touched, and this is the test that says so
     * rather than the comment that claims it.
     */
    @Test
    void nothing_a_customer_can_see_changed_because_of_the_migration() {
        assertThat(afterTheMigration.whatSheHolds())
                .isEqualByComparingTo(beforeTheUpgrade.whatSheHolds());
        assertThat(afterTheMigration.whatSheCanSpend())
                .isEqualTo(beforeTheUpgrade.whatSheCanSpend());
        assertThat(afterTheMigration.theMostSheHasEverSaved())
                .isEqualByComparingTo(beforeTheUpgrade.theMostSheHasEverSaved());
        assertThat(afterTheMigration.herRunOfWeeks()).isEqualTo(beforeTheUpgrade.herRunOfWeeks());
        assertThat(afterTheMigration.theBestRunOfWeeksSheHasEverHad())
                .isEqualTo(beforeTheUpgrade.theBestRunOfWeeksSheHasEverHad())
                .isEqualTo(1);
        assertThat(afterTheMigration.whatThePotHolds())
                .isEqualByComparingTo(beforeTheUpgrade.whatThePotHolds());
    }

    /**
     * And a start after the migration changes nothing at all: the same product, the same version,
     * the same day, and the same stamped deposits.
     *
     * <p>A migration that re-ran would re-date every agreement at whatever day the application came
     * up on, which on a wound clock is a different day every morning — and it would be the quiet
     * version of the thing the version assertion above is loud about.
     */
    @Test
    void a_start_after_the_migration_leaves_every_agreement_exactly_as_it_is() {
        assertThat(afterOneMoreStart.hers()).isEqualTo(afterTheMigration.hers());
        assertThat(afterOneMoreStart.herOtherOne()).isEqualTo(afterTheMigration.herOtherOne());
        assertThat(afterOneMoreStart.herHistory())
                .allSatisfy(row -> assertThat(row.termsVersion()).isEqualTo(1));
    }

    /**
     * The lab, built through the API the way a trainer builds one, and then read back before it is
     * taken apart.
     */
    private static WhatAStartSaw buildALabTheWayATrainerWould() {
        try (AnApplicationWithAClockToMove application =
                     new AnApplicationWithAClockToMove(DATABASE)) {
            theAccountSheHasPaidInto = application.savingsAccountOf(ANKE);
            theAccountNobodyHasPaidInto = application.otherSavingsAccountOf(ANKE);
            application.deposit(theAccountSheHasPaidInto, ANKE, "60.00");
            application.deposit(theAccountSheHasPaidInto, ANKE, "45.00");
            theDayTheMoneyArrived = application.theDateTheClockReads();

            // An account nobody holds, which is the one kind the two migrations before this one had
            // to leave behind: points and weeks belong to a customer, and a pot has none.
            SharedPotView pot = application.openAPot(BRAM, "Kitchen");
            thePot = pot.id();
            thePotsAccount = pot.savingsAccountId();
            application.deposit(thePotsAccount, BRAM, "80.00");

            // The gap between the saving and the upgrade, so that an agreement dated at the
            // migration and one dated at the first deposit cannot be the same date by accident.
            // Wound before the figures are read rather than after, because a run of weeks is
            // derived from the clock: read on the near side of forty days it would be a one, read
            // on the far side a nought, and the difference would be the clock rather than anything
            // the migration did.
            application.daysPass(DAYS_BETWEEN_THE_SAVING_AND_THE_UPGRADE);
            return whatItSees(application);
        }
    }

    private static WhatAStartSaw aStart() {
        try (AnApplicationWithAClockToMove application =
                     new AnApplicationWithAClockToMove(DATABASE)) {
            return whatItSees(application);
        }
    }

    /**
     * Everything a start is asked about, read while it is up.
     *
     * <p>Read here rather than in the tests, because the application is closed the moment this
     * returns: what is asserted is what a start saw, and holding an application open to be asked
     * later is how two of them come to be on one file at once.
     */
    private static WhatAStartSaw whatItSees(AnApplicationWithAClockToMove application) {
        return new WhatAStartSaw(
                application.balancesOf(theAccountSheHasPaidInto).agreement(),
                application.balancesOf(theAccountNobodyHasPaidInto).agreement(),
                Arrays.asList(application.depositsInto(theAccountSheHasPaidInto)),
                Arrays.asList(application.depositsInto(thePotsAccount)),
                application.balancesOf(theAccountSheHasPaidInto).moneyBalance(),
                application.pointsBalanceOf(ANKE),
                application.mostEverSavedOf(ANKE),
                application.balancesOf(theAccountSheHasPaidInto).currentStreakWeeks(),
                application.balancesOf(theAccountSheHasPaidInto).bestStreakWeeks(),
                application.potWith(thePot).moneyBalance(),
                application.theDateTheClockReads(),
                // Which version free savings is offering that day, asked of the catalogue rather
                // than written down here: a test that said "two" would be asserting what the seed
                // happens to write instead of asserting that an account did not follow the offer.
                application.theSavingsProduct("INSTANT").currentTerms().version());
    }

    /**
     * What a database written by the release before this one looks like: no agreements at all, and
     * no deposit saying what it landed under.
     *
     * <p>Done with every application down, because SQLite serialises writers and a second
     * connection writing to a live database is how a test earns an intermittent SQLITE_BUSY.
     */
    private static void forgetEveryAgreementByHand() {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE);
             Statement statement = connection.createStatement()) {
            int agreements = statement.executeUpdate("delete from account_agreement");
            assertThat(agreements)
                    .as("the agreements this test takes away to make an older database")
                    .isGreaterThan(1);
            int deposits = statement.executeUpdate("update deposit set terms_version = null");
            assertThat(deposits)
                    .as("the deposits this test un-stamps to make an older database")
                    .isGreaterThan(1);
        } catch (SQLException e) {
            throw new AssertionError("could not work on the database this test wrote at " + DATABASE, e);
        }
    }

    /**
     * What one start of the application saw: the two agreements, the two histories, the four
     * figures that must not move, the day it was standing on, and what the catalogue was offering
     * that day.
     */
    private record WhatAStartSaw(AnAgreementView hers, AnAgreementView herOtherOne,
                                 List<DepositView> herHistory,
                                 List<DepositView> contributionsToThePot,
                                 BigDecimal whatSheHolds, long whatSheCanSpend,
                                 BigDecimal theMostSheHasEverSaved, int herRunOfWeeks,
                                 int theBestRunOfWeeksSheHasEverHad,
                                 BigDecimal whatThePotHolds, LocalDate theDayItRanOn,
                                 int whatFreeSavingsIsSellingToday) {
    }
}
