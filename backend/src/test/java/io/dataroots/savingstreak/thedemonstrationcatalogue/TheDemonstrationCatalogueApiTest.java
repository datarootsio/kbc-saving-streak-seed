package io.dataroots.savingstreak.thedemonstrationcatalogue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClockView;
import io.dataroots.savingstreak.support.OfferView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalogue a demonstration is given from: five offers on top of the four this application has
 * always had, every one of them positioned against the clock rather than against a date somebody
 * typed, and none of them anywhere near a database that is not being demonstrated with.
 *
 * <p><strong>Its own applications, on the demo profile, and that is the whole reason this class
 * exists rather than a few more assertions somewhere else.</strong> Every other test in this
 * repository runs on {@code dev} alone, and the five offers below are deliberately invisible there:
 * {@code ClaimingRewardsApiTest} asserts the catalogue is four entries at four prices with
 * {@code containsExactly}, and {@code TheCatalogueSeededAsRowsApiTest} counts the rows in
 * {@code reward_offer} and asserts four. Those two are the strongest rails on this feature and
 * they are doing their job — a customer's catalogue growing by five entries is exactly what they
 * were written to catch. So the demonstration catalogue is a second profile, the way the half of
 * Anke that has already been lived in is, and the only way to assert anything about it is to start
 * an application that has that profile on.
 *
 * <p><strong>Three starts against one file</strong>, for the reason the rows test gives: "nothing
 * changed on the restart" is a statement about two starts rather than about one, and a
 * demonstration catalogue somebody has retuned is worth nothing if the application puts it back
 * every morning. The edit in the middle goes through the administration API rather than through
 * raw JDBC, because by this slice there is one, and what the criterion promises is that an edit
 * made <em>through the screen</em> survives.
 *
 * <p>The windows are asserted against the clock the application was left on rather than against
 * dates written out here, which is the same thing the seed does and the reason it does it: a
 * figure typed into a test would be right for exactly one day, and the demonstration has to work
 * on any day it is given.
 */
class TheDemonstrationCatalogueApiTest extends ApiIntegrationTest {

    private static final Path DATABASE = aDatabaseFileThatDoesNotExistYet("saving-streak-demo");

    /** The one offer this test retunes, and what somebody running the scheme changed it to. */
    private static final String EDITED = "WINTER_HAMPER";
    private static final String THE_NEW_TITLE = "The winter hamper, made up locally";
    private static final int THE_NEW_STOCK = 12;

    private static List<OfferView> onTheFirstStart;
    private static List<OfferView> onTheSecondStart;
    private static List<OfferView> afterSomebodyRetunedOne;
    /** The day the application's own clock read while the catalogue above was being read back. */
    private static LocalDate theDayTheDemonstrationOpensOn;

    @BeforeAll
    static void startTwiceThenRetuneOneAndStartAgain() {
        onTheFirstStart = whatAStartLeavesInTheCatalogue(
                TheDemonstrationCatalogueApiTest::theWholeCatalogue);
        theDayTheDemonstrationOpensOn = whatAStartLeavesInTheCatalogue(
                TheDemonstrationCatalogueApiTest::theDayTheApplicationThinksItIs);
        onTheSecondStart = whatAStartLeavesInTheCatalogue(
                TheDemonstrationCatalogueApiTest::theWholeCatalogue);
        afterSomebodyRetunedOne = whatAStartLeavesInTheCatalogue(http -> {
            retuneTheHamper(http);
            return theWholeCatalogue(http);
        });
    }

    /**
     * A first start against an empty file offers the four this application has always had and the
     * five a demonstration needs, in that order.
     *
     * <p>The four first, because they are seeded before the web server binds its port and the
     * demonstration five after it, and the catalogue is served in the order the rows were written.
     * That ordering is worth pinning: a customer who knew the catalogue yesterday should find the
     * four they knew at the top of it rather than scattered through a longer list.
     */
    @Test
    void a_first_start_writes_the_demonstration_catalogue_after_the_four_that_were_always_there() {
        assertThat(onTheFirstStart).extracting(OfferView::code).containsExactly(
                "CHARITY_DONATION", "SNACK_VOUCHER", "CINEMA_TICKET", "FAMILY_CINEMA_PACK",
                "WINTER_HAMPER", "CITY_BIKE_DAY", "FESTIVAL_WEEKEND_PASS",
                "TEN_WEEK_SAVERS_DINNER", "WEEKEND_TREAT_BUNDLE");
        assertThat(onTheFirstStart).allSatisfy(offer -> {
            assertThat(offer.title()).isNotBlank();
            assertThat(offer.description()).isNotBlank();
            assertThat(offer.state()).isEqualTo("PUBLISHED");
        });
    }

    /**
     * The four are untouched by the demonstration's arrival. This is the safety argument of the
     * whole feature restated where it could most easily be broken: a seed that gave the originals
     * a window or a stock figure in passing would change what a customer sees, and the profile
     * this runs on is the one place nothing else is watching.
     */
    @Test
    void the_four_that_were_always_there_still_say_nothing_about_themselves() {
        assertThat(onTheFirstStart)
                .filteredOn(offer -> List.of("CHARITY_DONATION", "SNACK_VOUCHER", "CINEMA_TICKET",
                        "FAMILY_CINEMA_PACK").contains(offer.code()))
                .hasSize(4)
                .allSatisfy(offer -> {
                    assertThat(offer.opensOn()).isNull();
                    assertThat(offer.closesOn()).isNull();
                    assertThat(offer.stock()).isNull();
                    assertThat(offer.maxPerCustomer()).isNull();
                    assertThat(offer.maxPerCustomerPerWeek()).isNull();
                    assertThat(offer.minimumStreakWeeks()).isNull();
                    assertThat(offer.requiresBadge()).isNull();
                    assertThat(offer.minimumLifetimePointsEarned()).isNull();
                    assertThat(offer.voucherValidForDays()).isNull();
                    assertThat(offer.discountedCostInPoints()).isNull();
                    assertThat(offer.contents()).isEmpty();
                });
    }

    /**
     * Something scarce, and few enough of them that a session can sell it out.
     *
     * <p>Two is the figure the queue slice's author asked for by name, and the reason is that the
     * whole of the scarcity demonstration hangs off it: claim, claim, sold out, queue, restock,
     * promote, convert. A hamper with forty in stock would need a trainer to type an offer first,
     * which is the thing seeding exists to avoid.
     */
    @Test
    void something_scarce_with_a_handful_in_stock() {
        assertThat(theOffer("WINTER_HAMPER").stock()).isEqualTo(2);
    }

    /**
     * Something whose window has not opened yet, positioned against the clock the application was
     * left on rather than against a date.
     *
     * <p>Asserted as "a fortnight after to-day and a month open after that" rather than as two
     * dates, because that is the promise: the demonstration works on any day it is given. A test
     * carrying the dates would pass on the day it was written and on no other.
     */
    @Test
    void something_whose_window_opens_relative_to_the_clock() {
        OfferView festival = theOffer("FESTIVAL_WEEKEND_PASS");
        assertThat(festival.opensOn()).isEqualTo(theDayTheDemonstrationOpensOn.plusDays(14));
        assertThat(festival.closesOn()).isEqualTo(theDayTheDemonstrationOpensOn.plusDays(44));
        assertThat(festival.opensOn()).isAfter(theDayTheDemonstrationOpensOn);
    }

    /** Something gated behind a run of weeks longer than anybody in the seeded data has. */
    @Test
    void something_gated_behind_a_streak() {
        assertThat(theOffer("TEN_WEEK_SAVERS_DINNER").minimumStreakWeeks()).isEqualTo(10);
    }

    /**
     * Something on discount, in a window that is running on the day the demonstration opens and
     * over within the week — so that a trainer can show both the struck-through price and, a wind
     * of the clock later, the price going back up.
     */
    @Test
    void something_on_discount_this_week() {
        OfferView bicycle = theOffer("CITY_BIKE_DAY");
        assertThat(bicycle.discountedCostInPoints()).isLessThan(bicycle.costInPoints());
        assertThat(bicycle.discountOpensOn())
                .isEqualTo(theDayTheDemonstrationOpensOn.minusDays(2));
        assertThat(bicycle.discountClosesOn())
                .isEqualTo(theDayTheDemonstrationOpensOn.plusDays(5));
        // The other two things this one card carries, because a discount, a shelf life and a
        // lifetime cap on one offer is the card a room learns the most from.
        assertThat(bicycle.voucherValidForDays()).isEqualTo(7);
        assertThat(bicycle.maxPerCustomer()).isEqualTo(1);
    }

    /** And a bundle, composed out of two offers that exist in their own right. */
    @Test
    void a_bundle_made_of_offers_that_are_already_in_the_catalogue() {
        OfferView bundle = theOffer("WEEKEND_TREAT_BUNDLE");
        assertThat(bundle.contents())
                .extracting(member -> member.code() + " x" + member.quantity())
                .containsExactly("CINEMA_TICKET x2", "SNACK_VOUCHER x1");
        assertThat(bundle.costInPoints()).isLessThan(2 * 100 + 40);
    }

    /**
     * The second start finds all nine and does nothing at all — not a tenth row, not a
     * reordering, not a word changed, and not a window moved on. Asserted as the whole list
     * against the whole list, because a seed that wrote a duplicate would still have the right
     * nine somewhere in it.
     */
    @Test
    void a_restart_finds_them_and_changes_nothing() {
        assertThat(onTheSecondStart).containsExactlyElementsOf(onTheFirstStart);
    }

    /**
     * And the promise the whole administration surface rests on, asked of the demonstration
     * offers as well: what somebody running the scheme changed through the screen is still
     * changed after the application has been up and down again. A demonstration catalogue that
     * reset itself would make every restock last until the next restart.
     */
    @Test
    void a_retuned_demonstration_offer_survives_the_next_start() {
        assertThat(afterSomebodyRetunedOne)
                .extracting(OfferView::code)
                .containsExactlyElementsOf(onTheFirstStart.stream().map(OfferView::code).toList());
        assertThat(afterSomebodyRetunedOne)
                .filteredOn(offer -> offer.code().equals(EDITED))
                .singleElement()
                .satisfies(offer -> {
                    assertThat(offer.title()).isEqualTo(THE_NEW_TITLE);
                    assertThat(offer.stock()).isEqualTo(THE_NEW_STOCK);
                });
    }

    private static OfferView theOffer(String code) {
        return onTheFirstStart.stream()
                .filter(offer -> offer.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no offer " + code + " in the catalogue"));
    }

    private static List<OfferView> theWholeCatalogue(TestRestTemplate http) {
        return List.of(http.getForObject("/api/admin/rewards", OfferView[].class));
    }

    private static LocalDate theDayTheApplicationThinksItIs(TestRestTemplate http) {
        // Off the application's own clock, which the demonstration household has wound a fortnight
        // forward before this catalogue was seeded, and read in the zone the seed reads it in.
        return LocalDate.ofInstant(http.getForObject("/api/dev/clock", ClockView.class).now(),
                io.dataroots.savingstreak.streaks.SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
    }

    /** What somebody running the scheme does to an offer, through the screen they are given. */
    private static void retuneTheHamper(TestRestTemplate http) {
        http.patchForObject("/api/admin/rewards/{code}",
                Map.of("title", THE_NEW_TITLE, "stock", THE_NEW_STOCK),
                OfferView.class, EDITED);
    }

    /** One start, one question asked of it, and the application stopped again. */
    private static <T> T whatAStartLeavesInTheCatalogue(Function<TestRestTemplate, T> ask) {
        try (ConfigurableApplicationContext application = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = new TestRestTemplate();
            http.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                    + application.getEnvironment().getProperty("local.server.port")));
            return ask.apply(http);
        }
    }

    private static ConfigurableApplicationContext startAnApplicationAgainstTheFile() {
        // Command-line arguments rather than default properties, for the reason the walking
        // skeleton gives: defaults lose to application.properties, which would point this
        // instance at the real database. Both profiles, because that is what running this
        // application locally does and because demo without dev is not a thing anybody starts.
        return new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + DATABASE,
                        "--spring.profiles.active=dev,demo",
                        "--server.port=0");
    }
}
