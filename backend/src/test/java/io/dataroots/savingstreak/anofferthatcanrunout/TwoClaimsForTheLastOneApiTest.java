package io.dataroots.savingstreak.anofferthatcanrunout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ClaimedRewardView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.SeededAccounts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static io.dataroots.savingstreak.support.SeededAccounts.BRAM;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two people reaching for the last one at the same moment, and only one of them getting it.
 *
 * <p><strong>This is the test that pays for deriving what is left rather than storing it.</strong>
 * The argument the ticket makes — that a remaining figure held in a column would drift, and that
 * subtracting on every read cannot — has one obvious objection, which is that two claims in
 * flight might both read "one left" and both go through. The answer is written into the
 * application rather than hoped for: the connection pool holds a single connection because SQLite
 * serialises writers anyway, and the claim path is one transaction, so the second claim cannot
 * begin counting until the first has committed the row it is about to count. An argument in a
 * javadoc is worth what a test that provokes the race says it is, and this is that test.
 *
 * <p><strong>Two customers rather than one, deliberately.</strong> One person pressing twice is a
 * double submit, which the sequential test in this package already covers; two people is the case
 * the scheme actually has to survive, and it is the only one where neither request is a duplicate
 * of the other. Both earn their own points first, so that "the second one was refused" cannot be
 * a customer who simply could not afford it.
 *
 * <p><strong>The barrier is what makes it a race.</strong> Two tasks submitted and left to it
 * would very likely run one after the other and the test would pass against an application with
 * no protection at all. Both threads are held at the gate and released together, so the two
 * requests are genuinely in the air at once — which is the only arrangement under which a passing
 * assertion means anything.
 *
 * <p>Over HTTP, like everything else here: the race that matters is the one two browsers can
 * cause, and a test that reached for the service would be racing something no customer can.
 */
class TwoClaimsForTheLastOneApiTest extends ApiIntegrationTest {

    /** Long enough for a machine under load, short enough that a deadlock fails rather than hangs. */
    private static final int SECONDS_A_CLAIM_MAY_TAKE = 30;

    /** More than the one thing costs, so that neither refusal can be about the price. */
    private static final int WHAT_IT_COSTS = 5;

    private SeededAccounts seeded;

    private OffersWithStockThisTestWrites offers;

    @BeforeEach
    void findTheSeededAccounts() {
        seeded = new SeededAccounts(http);
        offers = new OffersWithStockThisTestWrites(http);
    }

    @AfterEach
    void leaveTheCatalogueAsItWasFound() {
        offers.putTheCatalogueBack();
    }

    @Test
    void two_claims_in_flight_cannot_both_take_the_last_one() throws Exception {
        String code = offers.aCodeNobodyHasUsed();
        offers.onSale(code, "The very last one", WHAT_IT_COSTS, 1);
        earn(ANKE, WHAT_IT_COSTS);
        earn(BRAM, WHAT_IT_COSTS);
        long ankeBefore = seeded.pointsBalanceOf(ANKE);
        long bramBefore = seeded.pointsBalanceOf(BRAM);

        List<ResponseEntity<JsonNode>> answers = bothAtOnce(
                () -> tryToClaim(ANKE, code), () -> tryToClaim(BRAM, code));

        assertThat(answers)
                .as("exactly one of them got the thing: " + answers)
                .filteredOn(answer -> answer.getStatusCode() == HttpStatus.CREATED)
                .hasSize(1);
        assertThat(answers)
                .as("and the other was told it had sold out, rather than being served a second "
                        + "one that does not exist")
                .filteredOn(answer -> answer.getStatusCode() == HttpStatus.CONFLICT)
                .hasSize(1);

        assertThat(howManyWentOut(code))
                .as("one of it existed and one of it went out, whatever arrived at once")
                .isEqualTo(1);
        assertThat(seeded.pointsBalanceOf(ANKE) + seeded.pointsBalanceOf(BRAM))
                .as("exactly one price was paid between the two of them, because the refused "
                        + "claim is refused before the points are touched")
                .isEqualTo(ankeBefore + bramBefore - WHAT_IT_COSTS);
        assertThat(offers.asReadBy(seeded.customerIdOf(ANKE), code).whatIsLeft())
                .as("and the catalogue agrees with the claims, because it is the claims it counts")
                .isEqualTo(0);
    }

    /**
     * Runs both calls on threads of their own, holds them at a barrier, and lets them go
     * together.
     *
     * <p>The barrier rather than two bare submissions, for the reason the class javadoc gives:
     * two tasks handed to a pool are very likely to run one after the other, and a test that did
     * that would pass against an application with nothing protecting it at all.
     */
    private static List<ResponseEntity<JsonNode>> bothAtOnce(
            Callable<ResponseEntity<JsonNode>> one, Callable<ResponseEntity<JsonNode>> other)
            throws Exception {
        ExecutorService both = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier gate = new CyclicBarrier(2);
            Future<ResponseEntity<JsonNode>> first = both.submit(atTheSameMoment(gate, one));
            Future<ResponseEntity<JsonNode>> second = both.submit(atTheSameMoment(gate, other));
            return List.of(first.get(SECONDS_A_CLAIM_MAY_TAKE, TimeUnit.SECONDS),
                    second.get(SECONDS_A_CLAIM_MAY_TAKE, TimeUnit.SECONDS));
        } finally {
            both.shutdownNow();
        }
    }

    private static Callable<ResponseEntity<JsonNode>> atTheSameMoment(
            CyclicBarrier gate, Callable<ResponseEntity<JsonNode>> call) {
        return () -> {
            gate.await(SECONDS_A_CLAIM_MAY_TAKE, TimeUnit.SECONDS);
            return call.call();
        };
    }

    /** How many claims for this offer exist across both customers, which is what actually went out. */
    private long howManyWentOut(String code) {
        return claimsOf(ANKE).stream().filter(claim -> code.equals(claim.code())).count()
                + claimsOf(BRAM).stream().filter(claim -> code.equals(claim.code())).count();
    }

    private List<ClaimedRewardView> claimsOf(String customerName) {
        ResponseEntity<ClaimedRewardView[]> read = http.getForEntity(
                "/api/customers/{id}/redemptions", ClaimedRewardView[].class,
                seeded.customerIdOf(customerName));
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        return List.of(read.getBody());
    }

    private ResponseEntity<JsonNode> tryToClaim(String customerName, String code) {
        return http.postForEntity("/api/customers/{id}/redemptions", Map.of("reward", code),
                JsonNode.class, seeded.customerIdOf(customerName));
    }

    /** Each customer earns what they are about to try to spend, so no refusal is about a price. */
    private void earn(String customerName, long points) {
        ResponseEntity<DepositView> deposit = http.postForEntity(
                "/api/savings-accounts/{id}/deposits",
                Map.of("amount", points + ".00", "fromCurrentAccountId",
                        seeded.currentAccountOf(customerName)),
                DepositView.class, seeded.savingsAccountOf(customerName));
        assertThat(deposit.getStatusCode())
                .describedAs("a deposit this test needs in order to have points to spend")
                .isEqualTo(HttpStatus.CREATED);
    }
}
