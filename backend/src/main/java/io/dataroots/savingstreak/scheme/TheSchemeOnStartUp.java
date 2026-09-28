package io.dataroots.savingstreak.scheme;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Writes version 1 of the scheme into the database before the application serves anything, at
 * exactly the figures this application has always run on, and leaves it exactly as it is on every
 * start after the first.
 *
 * <p><strong>Version 1 is what the application always did, transcribed.</strong> EUR 50 a week;
 * 1,00× rising by 0,10× to a cap of 1,50×; points that last twelve months; the six balance rungs;
 * four fifths of a budget; three bills outstanding; thirty days before a maturity and thirty before
 * an anniversary.
 * That is what made the upgrade safe: an upgraded database behaved on the morning after exactly as
 * it had the night before, because the rows said what the constants said. The constants are now
 * gone and every rule reads these rows instead — which is what the seed was always for, and why
 * what it writes has to go on being exactly what the application always did.
 *
 * <p><strong>The figures are written down here and nowhere else now.</strong> They were once
 * duplicated — the classes that held them had constants saying the same thing — and importing one
 * of those would have reversed the dependency this module is arranged around: Streaks, Points and
 * Notifications depend on the scheme, not the other way about. The duplication was deliberate and
 * temporary, and the contract ticket ended it by deleting the constants. What is left is a literal
 * table in this class, which is the last place in the application that says what the scheme has
 * always been, and the seeding test pins it figure by figure for exactly that reason.
 *
 * <p><strong>Its date is a literal Monday long before any data, and that is a decision against the
 * house pattern.</strong> {@code ProductsOnStartUp} anchors its effective dates to the day the seed
 * first ran, and argues well for it: a hard-coded calendar date is a catalogue whose second version
 * has not happened yet for a trainer who runs the application before it. The scheme cannot afford
 * that argument, because this application's clock moves in <em>both</em> directions. A seed date
 * computed from today would sit after some weeks already in the ledger the moment somebody wound
 * the clock back, and those weeks would then be judged against no published scheme at all — which
 * is a week with no threshold, no ladder and no answer to "did I secure it". A literal date cannot
 * move.
 *
 * <p><strong>Seed-if-absent, and that is a promise rather than an implementation detail.</strong> A
 * version already carrying the number is left alone, untouched and unread. Seeding is a floor and
 * never a reset, so an administrator who publishes version 3 keeps it through every restart
 * afterwards. The trade is the one the products catalogue, the rewards catalogue and the challenges
 * make knowingly: a change to the figures below does <em>not</em> reach a database that already has
 * them, so re-seeding an edited scheme is a fresh file — because the alternative is a start-up that
 * overwrites a scheme somebody's weeks were judged under.
 *
 * <p>Runs once every bean exists and before the context finishes refreshing — which is before the
 * web server binds its port and can therefore be asked what the scheme says. That ordering is the
 * reason it is not a {@code CommandLineRunner}: one of those runs after the application is already
 * accepting requests, and the first person to ask on the morning of a release would be told the
 * bank has no scheme at all.
 */
@Component
class TheSchemeOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(TheSchemeOnStartUp.class);

    /**
     * The Monday the scheme is written as having started on: the first Monday of the century.
     *
     * <p>A literal date, for the reason the class javadoc argues at length, and this particular
     * literal for two reasons. It is a Monday, which every version of the scheme has to be. And it
     * is far enough back that no clock a trainer could plausibly wind — this application's
     * development clock is moved in weeks and months, not decades — reaches a day before it, so
     * every week anybody can produce has a scheme in force over it.
     */
    private static final LocalDate THE_MONDAY_THE_SCHEME_HAS_ALWAYS_RUN_FROM =
            LocalDate.of(2000, 1, 3);

    /** EUR 50 a week, in cents, which is what a week has asked for since this application began. */
    private static final long WHAT_A_WEEK_HAS_ALWAYS_ASKED_FOR_IN_CENTS = 5_000L;

    /** 0,10× per further week, in basis points of a whole multiple. */
    private static final int WHAT_EACH_FURTHER_WEEK_HAS_ALWAYS_ADDED = 1_000;

    /** 1,50×, in basis points of a whole multiple, which is where the ladder has always stopped. */
    private static final int WHERE_THE_LADDER_HAS_ALWAYS_STOPPED_CLIMBING = 15_000;

    /** Twelve months, which is how long a batch of points has always lasted. */
    private static final int HOW_LONG_A_BATCH_OF_POINTS_HAS_ALWAYS_LASTED = 12;

    /**
     * The six balance rungs, in cents, ascending: EUR 100, 500, 1 000, 2 500, 5 000 and 10 000.
     *
     * <p>Round figures far enough apart that climbing from one to the next is an occasion, which is
     * the argument the notifications module makes about the same six. They are written in cents
     * here because that is the unit the column holds, and they read back out as euros.
     */
    private static final List<Long> THE_RUNGS_A_BALANCE_HAS_ALWAYS_CLIMBED = List.of(
            10_000L, 50_000L, 100_000L, 250_000L, 500_000L, 1_000_000L);

    /** Four fifths of a budget, in basis points of a share. */
    private static final int WHEN_A_BUDGET_HAS_ALWAYS_BEEN_RUNNING_LOW = 8_000;

    /** Three bills outstanding at once, which is what arrears piling up has always meant. */
    private static final int HOW_MANY_OUTSTANDING_HAS_ALWAYS_BEEN_A_SPIRAL = 3;

    /** Thirty days, which is how long before a maturity it has always been worth saying so. */
    private static final int DAYS_BEFORE_A_MATURITY_IT_HAS_ALWAYS_BEEN_WORTH_SAYING = 30;

    /** Thirty days, which is how long before an anniversary it has always been worth saying so. */
    private static final int DAYS_BEFORE_AN_ANNIVERSARY_IT_HAS_ALWAYS_BEEN_WORTH_SAYING = 30;

    /**
     * The line version 1 carries, because every version of the scheme owes the customer a sentence.
     *
     * <p>A product's first version says nothing changed, because nothing did and a customer chose
     * that agreement. Nobody chose the scheme, so version 1 says what it is: not that something
     * moved, but that this is what the bank has always been doing and here it is written down.
     */
    private static final String WHAT_VERSION_ONE_SAYS =
            "The scheme this application has always run on, written down for the first time so that "
                    + "it can be read, explained and changed. A week asks for EUR 50; a run of weeks "
                    + "pays 1,00 times per euro, rising by 0,10 for each further week to a cap of "
                    + "1,50; and a batch of points lasts twelve months. Nothing about what anybody "
                    + "earns changes today.";

    /**
     * Version 1, built afresh on every call rather than held as a constant.
     *
     * <p>For the reason the products, rewards and challenges seeds all give: this is an entity, and
     * a constant would be one instance handed to {@code save}, which attaches it, gives it an
     * identifier, and turns the next start's insert into an update of a row this step is supposed
     * never to touch. Two applications in one JVM is not a hypothetical here — it is how the tests
     * assert what a restart does.
     */
    private static SchemeVersion theSchemeThisApplicationHasAlwaysRunOn() {
        return SchemeVersion.published(
                1,
                THE_MONDAY_THE_SCHEME_HAS_ALWAYS_RUN_FROM,
                WHAT_A_WEEK_HAS_ALWAYS_ASKED_FOR_IN_CENTS,
                BasisPointsOfTheScheme.ONE_WHOLE_MULTIPLE,
                WHAT_EACH_FURTHER_WEEK_HAS_ALWAYS_ADDED,
                WHERE_THE_LADDER_HAS_ALWAYS_STOPPED_CLIMBING,
                HOW_LONG_A_BATCH_OF_POINTS_HAS_ALWAYS_LASTED,
                THE_RUNGS_A_BALANCE_HAS_ALWAYS_CLIMBED,
                WHEN_A_BUDGET_HAS_ALWAYS_BEEN_RUNNING_LOW,
                HOW_MANY_OUTSTANDING_HAS_ALWAYS_BEEN_A_SPIRAL,
                DAYS_BEFORE_A_MATURITY_IT_HAS_ALWAYS_BEEN_WORTH_SAYING,
                DAYS_BEFORE_AN_ANNIVERSARY_IT_HAS_ALWAYS_BEEN_WORTH_SAYING,
                WHAT_VERSION_ONE_SAYS);
    }

    private final SchemeVersionRepository versions;

    TheSchemeOnStartUp(SchemeVersionRepository versions) {
        this.versions = versions;
    }

    /**
     * The guarantee, then the seed, and the order is what makes the guarantee worth having.
     *
     * <p>A unique index created after the rows it is about are already in would be an index that
     * cannot be created, on a database where the duplicate it was meant to prevent has already
     * happened. The same argument {@code ProductsOnStartUp} makes about the same two steps.
     */
    @Override
    public void afterSingletonsInstantiated() {
        makeAVersionOfTheSchemeUnique();
        seedTheSchemeThisApplicationHasAlwaysRunOn();
    }

    /**
     * Makes one row per version a rule the database keeps, before anything can be written or read.
     *
     * <p>Here rather than on the entity because the entity cannot say it: the schema is generated
     * from the entity model against SQLite, and that dialect writes a unique clause from an
     * annotation nowhere. {@code SchemeVersionRepository} argues that at greater length, and the
     * Products, Challenges and Loyalty modules say the same thing about the same problem.
     */
    private void makeAVersionOfTheSchemeUnique() {
        if (versions.aVersionOfTheSchemeIsAlreadyUnique() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("a version of the scheme is already unique "
                    + "index=one_row_per_version_of_the_scheme");
            return;
        }
        versions.makeAVersionOfTheSchemeUnique();
        log.info("a version of the scheme was made unique "
                + "index=one_row_per_version_of_the_scheme columns=[version]");
    }

    /**
     * Writes version 1 if the database has not got it, and leaves whatever is there exactly as it
     * is.
     *
     * <p>Matched by the version number, which is the whole key of a scheme that has no plural. A
     * row found under it is not read, not compared and not corrected: what it says is what whoever
     * runs the bank last said, and a start-up that argued with them would make every published
     * version last until the next restart.
     *
     * <p>Only version 1 is ever seeded, and versions 2 upwards are nobody's business but the
     * administrator's. The products seed writes a second version of one product deliberately, so
     * that "what the product pays" and "what your account pays" differ on the very first screen;
     * the scheme has no equivalent to demonstrate, because nobody is pinned to a version and a
     * second seeded version would mean a change nobody made and no line honestly explaining it.
     */
    private void seedTheSchemeThisApplicationHasAlwaysRunOn() {
        if (versions.existsByVersion(1)) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that a scheme reading at yesterday's figures after a restart is not blamed on a step
            // nobody can see.
            log.debug("the scheme is already written down versions={} written=0",
                    versions.count());
            return;
        }
        TheSchemeAsPublished written =
                versions.save(theSchemeThisApplicationHasAlwaysRunOn()).asPublished();
        // One INFO line carrying every figure, because this row is what four families of rules will
        // be priced from and "what was the scheme on the day this file was made" is the first
        // question anybody debugging a rate, an expiry or a notification will ask.
        log.info("the scheme this application has always run on was seeded version={} "
                        + "effectiveFrom={} weeklyThreshold={} theOrdinaryRate={} "
                        + "extraForEachFurtherWeek={} theMostAStreakPays={} "
                        + "howLongABatchOfPointsLasts={} balanceRungs={} "
                        + "whatShareOfABudgetIsRunningLow={} howManyOutstandingIsASpiral={} "
                        + "daysBeforeAMaturityIsWorthSaying={} "
                        + "daysBeforeAnAnniversaryIsWorthSaying={} whatChanged={}",
                written.version(), written.effectiveFrom(), written.weeklyThreshold(),
                written.theOrdinaryRate(), written.extraForEachFurtherWeek(),
                written.theMostAStreakPays(), written.howLongABatchOfPointsLasts(),
                written.balanceRungs(), written.whatShareOfABudgetIsRunningLow(),
                written.howManyOutstandingIsASpiral(), written.daysBeforeAMaturityIsWorthSaying(),
                written.daysBeforeAnAnniversaryIsWorthSaying(), written.whatChanged());
    }
}
