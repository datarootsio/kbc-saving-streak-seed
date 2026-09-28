package io.dataroots.savingstreak.rewards;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Puts five offers on the demonstration catalogue that between them say every new thing this
 * feature can say, so that a trainer opening the rewards page sees the feature rather than the
 * four constants it grew out of.
 *
 * <p><strong>Why anything is seeded here at all.</strong> {@link RewardsOnStartUp} seeds the four
 * entries this application has always offered, and every one of them deliberately says nothing
 * about itself — no stock, no window, no cap, no rule, no discount, no shelf life — because that
 * is the whole safety argument of the change. Which leaves a catalogue on which not one of the
 * columns this feature added is set to anything. A trainer would have to type five offers into
 * the administration screen before the first sentence of the session, and a reviewer opening the
 * page would see exactly what they saw before the feature was written. So the demonstration data
 * is data, and this is where it lives.
 *
 * <p><strong>Why the demo profile and not the dev one.</strong> The tests run on {@code dev}
 * alone, and two of them are the strongest rails on this feature: the claiming test asserts the
 * catalogue is four entries at four prices with {@code containsExactly}, and
 * {@code TheCatalogueSeededAsRowsApiTest} counts the rows in {@code reward_offer} and asserts
 * four. A fifth published offer under {@code dev} breaks both, and the honest reading of that is
 * not that the rails are in the way — it is that they are doing their job, because a customer's
 * catalogue growing by five entries is exactly the kind of change they exist to catch. So this is
 * a second profile rather than more of the first, which is the same answer, for the same reason
 * and in the same words, that {@code AHouseholdWithADecisionToMake} gives about the half of Anke
 * that has already been lived in. Running the application locally activates both — the Maven
 * plugin's configuration says so and explains why — so a trainer, a reviewer and anybody who
 * types {@code ./mvnw spring-boot:run} gets all five.
 *
 * <p><strong>Why it waits for the application to be ready.</strong> Every other seed in this
 * application runs before the web server binds its port, and says at length why. This one cannot,
 * and the reason is the clock. {@code AHouseholdWithADecisionToMake} winds the development clock
 * fourteen days forward as it seeds, because the only way to put a deposit in a week that has
 * ended is to spend a fortnight of clock getting there — and it does that from a
 * {@code CommandLineRunner}, which is after every {@code SmartInitializingSingleton} has already
 * run. An offer seeded before that wind is an offer anchored to a day a fortnight before the one
 * the demonstration opens on: the promotion below would be a week over before anybody looked at
 * it, and "on discount this week" would be a card nobody could ever catch. {@link
 * ApplicationReadyEvent} is published after the runners have finished, so it is the first moment
 * at which the clock reads the day the session is actually being given on. The price is a window
 * of a second or two in which the server is up and the demonstration catalogue is not; the
 * household seeding has always had the same window, for the same reason, and it is not a window
 * anybody can be inside of and care.
 *
 * <p><strong>Everything is positioned against that clock and nothing is written out as a
 * date.</strong> {@code ChallengesOnStartUp} argues this out in full for its season and the
 * argument is identical here: a promotion hard-coded to the first week of March is a promotion
 * that is over for every trainer who runs this in April, quietly and then for ever. The
 * demonstration has to work on any day it is given, so every day below is {@code today} plus or
 * minus something, read once, in the zone this application counts its weeks in. The trade —
 * that the window moves each time the database is rebuilt — is the one the season makes
 * knowingly, and for a training application whose file is thrown away and remade it is very
 * nearly a feature.
 *
 * <p><strong>Seed-if-absent, by code, like everything else.</strong> A code already in the
 * catalogue is left alone, unread and uncorrected, so an administrator who retitles the hamper or
 * restocks it keeps their figure through every restart. Seeding is a floor and never a reset, and
 * the trade is the one {@link RewardsOnStartUp} names: a change to the figures below does not
 * reach a database that already has the rows, so re-seeding an edited demonstration catalogue is
 * a fresh file.
 *
 * <p><strong>It goes through {@link RewardsService} rather than the repositories</strong>, which
 * is the one place it parts company with {@link RewardsOnStartUp} and the one place it follows
 * {@code DemoData} instead. The reason is that class's: a seeded offer should meet the same rules,
 * write the same rows and leave the same log lines an administrator typing it into the screen
 * would. Every figure below is one the administration screen would accept, and it is checked
 * rather than promised — a demonstration offer the application would have refused is a
 * demonstration nobody can reproduce by typing it. It also means the bundle's members are read
 * out of the catalogue that {@link RewardsOnStartUp} has already seeded, which is a check this
 * could not have made for itself.
 *
 * <p><strong>They are written as drafts and then published</strong>, because that is the only way
 * an offer goes on sale and the second decision is the one that leaves the second line in the
 * log. A demonstration catalogue in which one entry had skipped the read-back would be a
 * demonstration of a door this feature does not have.
 */
@Component
@Profile("demo")
class TheDemoCatalogue implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(TheDemoCatalogue.class);

    /**
     * The scarce one, and the offer the whole queue story is told on.
     *
     * <p>Two in stock, which is the figure the queue slice's author asked for by name. It has to
     * be small enough that a session can sell it out in two claims and large enough that selling
     * it out is a thing somebody does rather than a thing that has already happened — nought
     * would be honest ("the hampers have not arrived yet") and would make the first four minutes
     * of a demonstration a restock. Two it is: claim, claim, sold out, queue, restock, promote,
     * convert.
     *
     * <p>Sixty points, which is a deposit of sixty euros. Every figure on this catalogue is
     * priced for a session rather than for a scheme: a hamper worth five hundred points is a
     * hamper a trainer spends ten minutes earning, and the thing being demonstrated is the
     * scarcity rather than the saving.
     */
    private static final int HOW_MANY_HAMPERS_THERE_ARE = 2;

    /**
     * How the promotion sits around to-day: it started the day before yesterday and it has five
     * more days to run.
     *
     * <p>Behind as well as ahead on purpose. A discount that begins to-day is one a room will
     * reasonably suspect of having been arranged for them, and one that has been running for two
     * days is a promotion somebody put on last week — which is what a promotion looks like. Five
     * days ahead rather than fifty because the other half of the demonstration is the discount
     * <em>ending</em>: a trainer who winds the clock a week watches the struck-through price
     * disappear and the card go back to eighty points, and a window that outlasted the session
     * could never show that.
     */
    private static final int THE_PROMOTION_STARTED_THIS_MANY_DAYS_AGO = 2;
    private static final int THE_PROMOTION_HAS_THIS_MANY_DAYS_TO_RUN = 5;

    /**
     * And how the festival sits: a fortnight off, running for a month once it arrives.
     *
     * <p>A fortnight because the card has to read as "not open yet" to somebody who has just
     * opened the page — that is the locked state this offer exists to show — and because a
     * trainer who wants to watch it open can wind fourteen days and watch it open. Closing thirty
     * days after that makes it a season with two ends, so the card can say both dates and the
     * customer can work out whether they have time to save the two hundred and fifty points.
     */
    private static final int THE_FESTIVAL_OPENS_IN_THIS_MANY_DAYS = 14;
    private static final int THE_FESTIVAL_RUNS_FOR_THIS_MANY_DAYS = 30;

    /**
     * How long a bicycle voucher is good for, in days.
     *
     * <p>Seven, so that a shelf life running out is a wind of the clock rather than a wait, and so
     * that the number on the card is one a person would actually put on a day out — "use it
     * within the week" is a sentence somebody says. It is also the only shelf life on this
     * catalogue, which is the point: three of the four original entries and three of the five
     * below issue vouchers that never expire, so a room can see that an expiry is something an
     * offer opts into rather than something the scheme does to everybody.
     */
    private static final int A_BICYCLE_VOUCHER_IS_GOOD_FOR_THIS_MANY_DAYS = 7;

    /**
     * The run of secured weeks the savers' dinner asks for.
     *
     * <p>Ten, which is more than anybody in the seeded data has: Anke arrives on a run of three
     * and Bram on none at all. That is deliberate and it is the whole of what this offer
     * demonstrates — an offer nobody present qualifies for, shown on the page rather than hidden
     * from it, locked, with the sentence saying what the scheme wants from them. An offer
     * everybody already met would be a card indistinguishable from the four that ask nothing.
     */
    private static final int WEEKS_THE_DINNER_ASKS_FOR = 10;

    private final RewardsService rewards;
    private final Clock clock;

    TheDemoCatalogue(RewardsService rewards, Clock clock) {
        this.rewards = rewards;
        this.clock = clock;
    }

    /**
     * The five offers, as somebody running the scheme would have typed them, anchored to the day
     * handed in.
     *
     * <p>Built afresh from a day rather than held as a constant, for the reason every other seed
     * in this application gives about its own rows and one more of its own: the days below are
     * only right for the moment they were read, and a static list would carry the day the class
     * was loaded into a database seeded an hour later.
     *
     * <p>One offer per new thing the row can say, and no offer saying two of them where one would
     * do — except the bicycle, which deliberately says three. A discount, a shelf life and a
     * lifetime cap on one card is the card a room learns the most from: claim it, watch the
     * struck-through price be the one you are charged, watch the same card come back locked
     * because you have had your one, and watch the voucher run out a week later. Splitting those
     * across three offers would be three demonstrations of one mechanism each and no
     * demonstration that they compose.
     */
    private static List<ANewOffer> whatTheDemonstrationNeedsToShow(LocalDate today) {
        return List.of(
                new ANewOffer(
                        "WINTER_HAMPER",
                        "The winter hamper",
                        "A box of the good stuff, made up by the people who run the scheme and "
                                + "collected from the branch. There are only ever a few of them. "
                                + "When they are gone you can put your name down, and the first "
                                + "person in the queue gets the next one to come back.",
                        60,
                        "HAM",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        HOW_MANY_HAMPERS_THERE_ARE,
                        null,
                        null,
                        null,
                        null),
                new ANewOffer(
                        "CITY_BIKE_DAY",
                        "A day on a city bike",
                        "One bicycle, one day, picked up and dropped off anywhere in the city. "
                                + "Half price this week. One per customer, and the voucher is "
                                + "good for a week from the day you claim it — a day out you "
                                + "never take is a day out somebody else could have had.",
                        80,
                        "BIK",
                        null,
                        null,
                        A_BICYCLE_VOUCHER_IS_GOOD_FOR_THIS_MANY_DAYS,
                        null,
                        null,
                        null,
                        1,
                        null,
                        null,
                        50L,
                        today.minusDays(THE_PROMOTION_STARTED_THIS_MANY_DAYS_AGO),
                        today.plusDays(THE_PROMOTION_HAS_THIS_MANY_DAYS_TO_RUN),
                        null),
                new ANewOffer(
                        "FESTIVAL_WEEKEND_PASS",
                        "Festival weekend pass",
                        "Three days, one field, and a wristband that gets you in on all of "
                                + "them. It is not on sale yet; the card says the day it opens "
                                + "and the day it closes, and there is time to save for it if "
                                + "you start now. The words do not repeat those two dates, "
                                + "because a card that said \"a fortnight from now\" would go on "
                                + "saying it a month later.",
                        250,
                        "FES",
                        today.plusDays(THE_FESTIVAL_OPENS_IN_THIS_MANY_DAYS),
                        today.plusDays(THE_FESTIVAL_OPENS_IN_THIS_MANY_DAYS
                                + THE_FESTIVAL_RUNS_FOR_THIS_MANY_DAYS),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                new ANewOffer(
                        "TEN_WEEK_SAVERS_DINNER",
                        "The ten-week savers' dinner",
                        "Dinner for two, on the scheme, for the people who have kept it up. You "
                                + "need a run of ten secured weeks behind you before you can "
                                + "claim it. It is shown to everybody on purpose: this is the one "
                                + "the scheme is asking you for, and hiding it would be hiding "
                                + "what it wants.",
                        150,
                        "DIN",
                        null,
                        null,
                        null,
                        WEEKS_THE_DINNER_ASKS_FOR,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                new ANewOffer(
                        "WEEKEND_TREAT_BUNDLE",
                        "The weekend treat",
                        "Two seats at the cinema and something to eat with them, handed over as "
                                + "one voucher. Two hundred points for what would be two hundred "
                                + "and forty bought separately — the saving is the scheme's "
                                + "decision rather than a sum, which is why it is a price and not "
                                + "a percentage.",
                        200,
                        "WKD",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(new AMemberOfABundle("CINEMA_TICKET", 2),
                                new AMemberOfABundle("SNACK_VOUCHER", 1))));
    }

    /**
     * Writes whichever of the five the database has not got, publishes each one it wrote, and
     * leaves everything it found exactly as it is.
     *
     * <p>Matched by code through {@link RewardsService#theOffer}, which is the same question
     * {@link RewardsOnStartUp} asks of its repository and gives the same answer: a row found under
     * a code is not read, not compared and not corrected. What it says is what whoever runs the
     * scheme last said.
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent ready) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
        List<ANewOffer> demonstration = whatTheDemonstrationNeedsToShow(today);
        List<String> added = demonstration.stream()
                .filter(offer -> rewards.theOffer(offer.code()).isEmpty())
                .map(this::writeItAndPutItOnSale)
                .toList();
        // At INFO on every start, including the ordinary one where nothing was added, and in the
        // shape the catalogue's own seed uses. "added=0 leftAlone=5" is what says the question was
        // asked, which is the first thing worth knowing about a page that is missing a card — and
        // the anchor is on the line because every window below is relative to it and a trainer
        // working out why the promotion has already ended wants the day it was read.
        log.info("the demonstration catalogue was seeded added={} codes={} leftAlone={} "
                        + "anchoredTo={}",
                added.size(), added, demonstration.size() - added.size(), today);
    }

    /**
     * Writes one of them and puts it on sale, which is two calls because going on sale is a second
     * decision everywhere else in this application and there is no reason for a seed to be the
     * exception.
     */
    private String writeItAndPutItOnSale(ANewOffer offer) {
        AnOfferAsItStands written = rewards.createADraft(offer);
        rewards.publish(written.code());
        return written.code();
    }
}
