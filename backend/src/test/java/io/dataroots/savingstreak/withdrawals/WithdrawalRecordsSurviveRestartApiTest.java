package io.dataroots.savingstreak.withdrawals;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import io.dataroots.savingstreak.SavingStreakApplication;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.DefaultUriBuilderFactory;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/** Withdrawal history and its deposit allocations remain meaningful after a real application restart. */
class WithdrawalRecordsSurviveRestartApiTest extends ApiIntegrationTest {

    private static final Path DATABASE = aDatabaseFileThatDoesNotExistYet("saving-streak-withdrawal-restart");

    private static ConfigurableApplicationContext reopenedApplication;
    private static TestRestTemplate reopenedHttp;
    private static WithdrawalView made;

    @BeforeAll
    static void withdrawThenRestart() {
        try (ConfigurableApplicationContext beforeRestart = startAnApplicationAgainstTheFile()) {
            TestRestTemplate http = boundTo(beforeRestart);
            SeededAccounts seeded = new SeededAccounts(http);
            long savings = seeded.savingsAccountOf(ANKE);
            long current = seeded.currentAccountOf(ANKE);
            DepositView first = deposit(http, savings, current, "4.00");
            DepositView second = deposit(http, savings, current, "8.00");
            made = withdraw(http, savings, current, "9.00");
            assertThat(made.allocations()).extracting(AllocationView::depositId)
                    .containsExactly(first.id(), second.id());
        }
        reopenedApplication = startAnApplicationAgainstTheFile();
        reopenedHttp = boundTo(reopenedApplication);
    }

    @AfterAll
    static void stopTheReopenedApplication() {
        if (reopenedApplication != null) {
            reopenedApplication.close();
        }
    }

    @Test
    void the_reopened_application_reads_back_the_withdrawal_and_each_deposit_it_reduced() {
        SeededAccounts seeded = new SeededAccounts(reopenedHttp);
        ResponseEntity<WithdrawalView[]> response = reopenedHttp.getForEntity(
                "/api/savings-accounts/{id}/withdrawals", WithdrawalView[].class, seeded.savingsAccountOf(ANKE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).anySatisfy(recorded -> {
            assertThat(recorded.id()).isEqualTo(made.id());
            assertThat(recorded.amount()).isEqualByComparingTo("9.00");
            assertThat(recorded.toCurrentAccountId()).isEqualTo(made.toCurrentAccountId());
            assertThat(recorded.withdrawnAt()).isEqualTo(made.withdrawnAt());
            assertThat(recorded.allocations()).hasSize(2);
            assertThat(recorded.allocations()[0].depositId()).isEqualTo(made.allocations()[0].depositId());
            assertThat(recorded.allocations()[0].amount()).isEqualByComparingTo("4.00");
            assertThat(recorded.allocations()[1].depositId()).isEqualTo(made.allocations()[1].depositId());
            assertThat(recorded.allocations()[1].amount()).isEqualByComparingTo("5.00");
        });
    }

    private static ConfigurableApplicationContext startAnApplicationAgainstTheFile() {
        return new SpringApplicationBuilder(SavingStreakApplication.class)
                .run("--spring.datasource.url=jdbc:sqlite:" + DATABASE, "--spring.profiles.active=dev", "--server.port=0");
    }

    private static TestRestTemplate boundTo(ConfigurableApplicationContext application) {
        TestRestTemplate http = new TestRestTemplate();
        http.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:"
                + application.getEnvironment().getProperty("local.server.port")));
        return http;
    }

    private static DepositView deposit(TestRestTemplate http, long savingsAccountId, long currentAccountId,
                                       String amount) {
        return http.postForEntity("/api/savings-accounts/{id}/deposits",
                Map.of("amount", amount, "fromCurrentAccountId", currentAccountId), DepositView.class,
                savingsAccountId).getBody();
    }

    private static WithdrawalView withdraw(TestRestTemplate http, long savingsAccountId, long currentAccountId,
                                           String amount) {
        ResponseEntity<WithdrawalView> response = http.postForEntity("/api/savings-accounts/{id}/withdrawals",
                Map.of("amount", amount, "toCurrentAccountId", currentAccountId), WithdrawalView.class,
                savingsAccountId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    record WithdrawalView(Long id, BigDecimal amount, Long toCurrentAccountId, Instant withdrawnAt,
                          AllocationView[] allocations) {
    }

    record AllocationView(Long depositId, BigDecimal amount) {
    }
}
