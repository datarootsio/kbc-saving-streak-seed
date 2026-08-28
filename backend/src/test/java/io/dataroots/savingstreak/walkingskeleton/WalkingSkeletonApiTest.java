package io.dataroots.savingstreak.walkingskeleton;

import java.nio.file.Path;
import java.util.List;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The app stands up end to end: it boots, creates and populates its own database file, and serves
 * the customers in it over HTTP. Two of these tests start a second copy of the application, which
 * is the only honest way to assert what happens on a restart.
 */
class WalkingSkeletonApiTest extends ApiIntegrationTest {

    private static final List<String> SEEDED_CUSTOMERS = List.of("Anke Peeters", "Bram De Vos");

    record CustomerView(Long id, String name) {
    }

    @Test
    void the_customer_list_names_the_seeded_customers() {
        ResponseEntity<CustomerView[]> response = http.getForEntity("/api/customers", CustomerView[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(CustomerView::name)
                .containsExactlyInAnyOrderElementsOf(SEEDED_CUSTOMERS);
    }

    @Test
    void every_customer_carries_an_identifier_of_its_own() {
        ResponseEntity<CustomerView[]> response = http.getForEntity("/api/customers", CustomerView[].class);

        assertThat(response.getBody())
                .allSatisfy(customer -> assertThat(customer.id()).isNotNull())
                .extracting(CustomerView::id)
                .doesNotHaveDuplicates();
    }

    @Test
    void starting_where_there_is_no_database_creates_one_and_seeds_it() {
        Path untouchedFile = aDatabaseFileThatDoesNotExistYet("saving-streak-first-start");

        try (ConfigurableApplicationContext freshStart = startApplicationAgainst(untouchedFile)) {
            assertThat(untouchedFile).exists();
            assertThat(customersServedBy(freshStart))
                    .extracting(CustomerView::name)
                    .containsExactlyInAnyOrderElementsOf(SEEDED_CUSTOMERS);
        }
    }

    @Test
    void starting_again_on_an_existing_database_does_not_duplicate_the_seeded_customers() {
        try (ConfigurableApplicationContext restart = startApplicationAgainst(databaseFile())) {
            assertThat(customersServedBy(restart))
                    .extracting(CustomerView::name)
                    .containsExactlyInAnyOrderElementsOf(SEEDED_CUSTOMERS);
        }
    }

    /**
     * Configuration rather than behaviour, and asserted directly because there is nothing to
     * observe over HTTP yet — no endpoint writes. SQLite serialises writers, so a second pooled
     * connection buys no concurrency and costs SQLITE_BUSY failures. That is the kind of setting
     * quietly raised by someone chasing throughput, and it fails at a demo rather than in a build.
     */
    @Test
    void the_connection_pool_is_capped_at_a_single_connection(@Autowired DataSource dataSource) {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(1);
    }

    private ConfigurableApplicationContext startApplicationAgainst(Path database) {
        // Passed as command-line arguments, not as default properties: defaults lose to
        // application.properties, which would quietly point this instance at the real database.
        return new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + database,
                        "--spring.profiles.active=dev",
                        "--server.port=0");
    }

    private List<CustomerView> customersServedBy(ConfigurableApplicationContext application) {
        Integer port = application.getEnvironment().getProperty("local.server.port", Integer.class);
        CustomerView[] customers = new TestRestTemplate()
                .getForObject("http://localhost:" + port + "/api/customers", CustomerView[].class);
        return List.of(customers);
    }
}
