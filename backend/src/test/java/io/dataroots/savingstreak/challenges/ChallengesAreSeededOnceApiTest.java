package io.dataroots.savingstreak.challenges;

import java.nio.file.Path;
import java.util.List;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ChallengeView;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The challenges the bank offers are rows, put there on the first start and left alone on every one
 * after it.
 *
 * <p>Rows rather than code because the thresholds, the words and what each rung pays are things the
 * bank tunes and seasons; the <em>kinds</em> are code, because a kind is a rule. Seeding them on
 * start-up is what makes a training application that has just been reset offer something to join
 * without anybody configuring it first.
 *
 * <p>Idempotent, and that is the whole of this test. Every other start-up step in this application
 * is written so that all but the first do nothing, and a seeding step that were not would hand a
 * customer two cards of the same name after a restart — and two enrolments they could take out on
 * the same challenge.
 *
 * <p>A second copy of the application against the same database file is the only honest way to
 * assert what happens on a restart, which is the argument {@code WalkingSkeletonApiTest} makes about
 * the seeded customers and the same one here.
 */
class ChallengesAreSeededOnceApiTest extends ApiIntegrationTest {

    /** The evergreen challenges a reset application offers, whatever else the bank adds later. */
    private static final List<String> SEEDED_CHALLENGES =
            List.of("SAVE_FIVE_HUNDRED", "SAVE_TWO_THOUSAND");

    @Test
    void the_seeded_challenges_are_offered_with_three_rungs_each() {
        List<ChallengeView> offered = challengesOfTheFirstCustomerServedBy(null);

        assertThat(offered)
                .extracting(ChallengeView::code)
                .containsAll(SEEDED_CHALLENGES);
        assertThat(offered).allSatisfy(challenge -> assertThat(challenge.rungs())
                .describedAs("every challenge climbs bronze, silver, gold")
                .hasSize(3));
    }

    @Test
    void starting_again_on_an_existing_database_does_not_duplicate_the_seeded_challenges() {
        try (ConfigurableApplicationContext restart = startApplicationAgainst(databaseFile())) {
            assertThat(challengesOfTheFirstCustomerServedBy(restart))
                    .extracting(ChallengeView::code)
                    // Once each is the assertion this test is named after. A restart that re-seeded
                    // would put a second SAVE_FIVE_HUNDRED on the card, and that is exactly what
                    // this fails on.
                    .containsOnlyOnceElementsOf(SEEDED_CHALLENGES);
        }
    }

    private ConfigurableApplicationContext startApplicationAgainst(Path database) {
        // Passed as command-line arguments, not as default properties: defaults lose to
        // application.properties, which would quietly point this instance at the real database.
        return new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + database,
                        "--spring.profiles.active=dev",
                        "--server.port=0");
    }

    /**
     * What one customer of the given application is offered, because the catalogue is only readable
     * through somebody's card — challenges are customer-scoped throughout, and there is deliberately
     * no endpoint that answers about them in the abstract.
     *
     * <p>{@code null} means the application this test class is already running against.
     */
    private List<ChallengeView> challengesOfTheFirstCustomerServedBy(
            ConfigurableApplicationContext application) {
        if (application == null) {
            return List.of(http.getForObject("/api/customers/{id}/challenges", ChallengeView[].class,
                    aCustomerServedBy(http, "")));
        }
        String base = "http://localhost:"
                + application.getEnvironment().getProperty("local.server.port");
        TestRestTemplate elsewhere = new TestRestTemplate();
        return List.of(elsewhere.getForObject(base + "/api/customers/{id}/challenges",
                ChallengeView[].class, aCustomerServedBy(elsewhere, base)));
    }

    private long aCustomerServedBy(TestRestTemplate client, String base) {
        CustomerView[] customers = client.getForObject(base + "/api/customers", CustomerView[].class);
        assertThat(customers).describedAs("the seeded customers this card is read as").isNotEmpty();
        return customers[0].id();
    }

    private record CustomerView(Long id, String name) {
    }
}
