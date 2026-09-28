package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.support.AchievementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.CampaignView;
import io.dataroots.savingstreak.support.ChallengeInASeasonView;
import io.dataroots.savingstreak.support.ChallengeView;
import io.dataroots.savingstreak.support.EnrolmentView;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A season is a window, and the window means something: you cannot join before it opens or after it
 * has closed, and an enrolment still running when it closes is judged one last time and then lapses.
 *
 * <p><strong>What "open" is measured against.</strong> The window is two dates and the clock reads
 * instants, so the question "is it open" is really "which day is it, where this application counts
 * its days" — {@code Europe/Brussels}, the zone a savings week is Monday to Sunday in and the zone
 * the development clock moves whole days through. Both ends are inclusive: a season opens at
 * midnight on the day it says it opens, and it is still open all through the day it says it closes.
 * A season that closes on the last day of the month is one a customer can join on that day, which is
 * what anybody reading the date on the card would expect of it.
 *
 * <p><strong>One test method, because the clock only goes forward.</strong> A second method here
 * would find the season already closed and would be asserting against whatever order the two
 * happened to run in. The narrative is asserted on after every step instead, which is what a season
 * running out is.
 *
 * <p><strong>Three seasons of this test's own, and the seeded one beside them.</strong> The bank
 * seeds exactly one season and it is open, because a trainer who has just reset the database has to
 * find a campaign to demonstrate without configuring one — so a season that has <em>not opened</em>
 * is a state no sequence of requests can reach, the development clock being forward-only. Those are
 * written straight into this application's own throwaway database, which is the same answer
 * {@code AOneOffIsFinishedForGoodApiTest} gives to the same problem and for the same reason: a
 * seeded closed season would change what every other test in the run sees on the challenges tab in
 * order to let this one ask its question.
 */
class ASeasonOpensAndClosesApiTest extends ApiIntegrationTest {

    /** A season that has not started yet, which is the state a forward-only clock cannot reach. */
    private static final String THE_SPRING_PUSH = "THE_SPRING_PUSH";

    /** One that is over, so that both ends of the window can be refused in the same narrative. */
    private static final String LAST_WINTER = "LAST_WINTER";

    /** And the one that is open now and closes while this test is watching. */
    private static final String THE_SUMMER_SEASON = "THE_SUMMER_SEASON";

    private static final String SPRING_SPRINT = "SPRING_SPRINT";
    private static final String THE_WINTER_HUNDRED = "THE_WINTER_HUNDRED";
    private static final String THE_SUMMER_FIVE_HUNDRED = "THE_SUMMER_FIVE_HUNDRED";
    private static final String THE_SUMMER_FIFTY = "THE_SUMMER_FIFTY";

    /** Joined on the season's very last day, which is how the inclusive close is demonstrated. */
    private static final String THE_LAST_DAY_ENTRY = "THE_LAST_DAY_ENTRY";

    /** An evergreen challenge the bank seeds, which a season closing must leave entirely alone. */
    private static final String FIVE_HUNDRED = "SAVE_FIVE_HUNDRED";

    /** The season the bank seeds, which is open out of the box, and the challenge inside it. */
    private static final String THE_SEEDED_SEASON = "THE_NINETY_DAY_PUSH";
    private static final String THE_SEEDED_SEASON_CHALLENGE = "THE_SEASONS_THOUSAND";

    /**
     * How many days ahead of the start of this test the summer season's last day is. The narrative
     * winds to exactly that day, joins something on it, and then winds one day more.
     */
    private static final int THE_CLOSING_DAY_IS_THIS_MANY_DAYS_OFF = 10;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWithSeasonsAtEveryStageOfTheirLives() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-seasons"));
        LocalDate today = app.theDateTheClockReads();
        CampaignRepository seasons = app.aBeanOfTheApplication(CampaignRepository.class);
        seasons.save(Campaign.running(THE_SPRING_PUSH, "The spring push",
                today.plusDays(10), today.plusDays(40)));
        seasons.save(Campaign.running(LAST_WINTER, "Last winter",
                today.minusDays(60), today.minusDays(1)));
        seasons.save(Campaign.running(THE_SUMMER_SEASON, "The summer season",
                today.minusDays(5), today.plusDays(THE_CLOSING_DAY_IS_THIS_MANY_DAYS_OFF)));

        ChallengeDefinitionRepository challenges =
                app.aBeanOfTheApplication(ChallengeDefinitionRepository.class);
        challenges.save(aSeasonChallenge(SPRING_SPRINT, "The spring sprint", THE_SPRING_PUSH,
                "100.00", "250.00", "500.00"));
        challenges.save(aSeasonChallenge(THE_WINTER_HUNDRED, "Last winter's hundred", LAST_WINTER,
                "20.00", "50.00", "100.00"));
        challenges.save(aSeasonChallenge(THE_SUMMER_FIVE_HUNDRED, "The summer's EUR 500",
                THE_SUMMER_SEASON, "100.00", "250.00", "500.00"));
        challenges.save(aSeasonChallenge(THE_SUMMER_FIFTY, "The summer's EUR 50", THE_SUMMER_SEASON,
                "20.00", "35.00", "50.00"));
        // Priced out of reach on purpose: it is joined on the last day to show that the last day is
        // a day somebody can join on, and it must still be unfinished when the season closes over
        // it a day later.
        challenges.save(aSeasonChallenge(THE_LAST_DAY_ENTRY, "In at the death", THE_SUMMER_SEASON,
                "5000.00", "7500.00", "10000.00"));
    }

    /**
     * A challenge inside a season, written the way the bank would seed one: a flow kind, so that a
     * deposit moves it inside the one narrative this class has room for.
     */
    private static ChallengeDefinition aSeasonChallenge(String code, String title, String season,
                                                        String bronze, String silver, String gold) {
        return ChallengeDefinition.offering(
                code,
                title,
                "Save it while the season is open. What you have won by the time it closes is "
                        + "yours; the rest of it simply lapses.",
                ChallengeKind.NEW_SAVINGS,
                null,
                List.of(new ChallengeRung(Rung.BRONZE, new BigDecimal(bronze), 10),
                        new ChallengeRung(Rung.SILVER, new BigDecimal(silver), 20),
                        new ChallengeRung(Rung.GOLD, new BigDecimal(gold), 30)),
                true,
                season);
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void a_season_refuses_enrolment_outside_its_window_and_lapses_the_enrolments_still_running_at_its_close() {
        long savings = app.savingsAccountOf(ANKE);
        LocalDate today = app.theDateTheClockReads();

        // The card carries the window, which is the whole of user story 32: a season has to say
        // when it closes, or a customer cannot tell whether they have time.
        ChallengeView summerBeforeJoining = app.challengeOf(ANKE, THE_SUMMER_FIVE_HUNDRED);
        assertThat(summerBeforeJoining.season()).isNotNull();
        assertThat(summerBeforeJoining.season().code()).isEqualTo(THE_SUMMER_SEASON);
        assertThat(summerBeforeJoining.season().title()).isEqualTo("The summer season");
        assertThat(summerBeforeJoining.season().opensOn()).isEqualTo(today.minusDays(5));
        assertThat(summerBeforeJoining.season().closesOn())
                .isEqualTo(today.plusDays(THE_CLOSING_DAY_IS_THIS_MANY_DAYS_OFF));
        assertThat(summerBeforeJoining.season().open())
                .as("to-day is inside the window, so the season is open")
                .isTrue();
        assertThat(summerBeforeJoining.state())
                .as("a seeded season enrols nobody: joining is a decision a customer makes")
                .isNull();

        assertThat(app.challengeOf(ANKE, SPRING_SPRINT).season().open())
                .as("a season whose first day is still ahead is not open")
                .isFalse();
        assertThat(app.challengeOf(ANKE, THE_WINTER_HUNDRED).season().open())
                .as("and one whose last day has gone by is not open either")
                .isFalse();
        assertThat(app.challengeOf(ANKE, FIVE_HUNDRED).season())
                .as("an evergreen challenge belongs to no season at all, which is not the same "
                        + "thing as a season with a very long window")
                .isNull();

        // The listing, which is the bank's own answer rather than anybody's card.
        assertThat(app.campaigns())
                .extracting(CampaignView::code)
                .contains(THE_SEEDED_SEASON, THE_SPRING_PUSH, LAST_WINTER, THE_SUMMER_SEASON);
        CampaignView summer = app.campaign(THE_SUMMER_SEASON);
        assertThat(summer.open()).isTrue();
        assertThat(summer.challenges())
                .extracting(ChallengeInASeasonView::code)
                .as("the listing says which challenges are in each season")
                .containsExactlyInAnyOrder(THE_SUMMER_FIVE_HUNDRED, THE_SUMMER_FIFTY,
                        THE_LAST_DAY_ENTRY);
        CampaignView seeded = app.campaign(THE_SEEDED_SEASON);
        assertThat(seeded.open())
                .as("the bank seeds one open season, so campaigns are demonstrable out of the box")
                .isTrue();
        assertThat(seeded.challenges())
                .extracting(ChallengeInASeasonView::code)
                .as("and it has a challenge in it, because a season with nothing in it is nothing "
                        + "to demonstrate")
                .contains(THE_SEEDED_SEASON_CHALLENGE);
        assertThat(app.challengeOf(ANKE, THE_SEEDED_SEASON_CHALLENGE).state())
                .as("the seed writes the season and the challenge in it and enrols nobody, so a "
                        + "fresh application changes nothing about anybody's tab")
                .isNull();
        assertThat(app.campaign(THE_SPRING_PUSH).open()).isFalse();
        assertThat(app.campaign(LAST_WINTER).open()).isFalse();

        // Before it opens. A conflict, not a bad request: the customer is real, the challenge is
        // real, and it is the state of the season that will not allow it.
        ResponseEntity<JsonNode> tooEarly = app.tryToEnrol(app.customerIdOf(ANKE), SPRING_SPRINT);
        assertThat(tooEarly.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tooEarly.getBody().get("detail").asText())
                .as("and the sentence says which season and when it opens, because the customer's "
                        + "answer is to come back rather than to correct anything")
                .contains("The spring push")
                .contains(today.plusDays(10).toString());
        assertThat(app.challengeOf(ANKE, SPRING_SPRINT).state())
                .as("a refused enrolment is no enrolment")
                .isNull();

        // And after it has closed, which is the same window read from the other end.
        ResponseEntity<JsonNode> tooLate = app.tryToEnrol(app.customerIdOf(ANKE), THE_WINTER_HUNDRED);
        assertThat(tooLate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tooLate.getBody().get("detail").asText())
                .contains("Last winter")
                .contains(today.minusDays(1).toString());
        assertThat(app.challengeOf(ANKE, THE_WINTER_HUNDRED).state()).isNull();

        // While it is open, which is what makes the two refusals above mean anything.
        EnrolmentView inTheSummer = app.enrolIn(ANKE, THE_SUMMER_FIVE_HUNDRED);
        assertThat(inTheSummer.state()).isEqualTo("ACTIVE");
        app.enrolIn(ANKE, THE_SUMMER_FIFTY);
        app.enrolIn(ANKE, FIVE_HUNDRED);

        app.deposit(savings, ANKE, "120.00");

        assertThat(app.challengeOf(ANKE, THE_SUMMER_FIVE_HUNDRED).reading())
                .isEqualByComparingTo("120.00");
        assertThat(app.challengeOf(ANKE, THE_SUMMER_FIFTY).state())
                .as("fifty euros is this one's gold, and gold finishes an enrolment")
                .isEqualTo("COMPLETED");
        assertThat(app.achievementsOf(ANKE))
                .extracting(AchievementView::challenge, AchievementView::rung)
                .contains(Tuple.tuple(THE_SUMMER_FIVE_HUNDRED, "BRONZE"),
                        Tuple.tuple(THE_SUMMER_FIFTY, "GOLD"),
                        Tuple.tuple(FIVE_HUNDRED, "BRONZE"));
        List<AchievementView> wonInsideTheSeason = app.achievementsOf(ANKE);
        long pointsWonInsideTheSeason = app.pointsBalanceOf(ANKE);

        // The very last day of the season, which is still a day it is open on. Both ends of the
        // window are inclusive: "closes on the sixth" that meant "shut all day on the sixth" would
        // be a poster that lied by a day.
        app.daysPass(THE_CLOSING_DAY_IS_THIS_MANY_DAYS_OFF);

        ChallengeView onTheClosingDay = app.challengeOf(ANKE, THE_SUMMER_FIVE_HUNDRED);
        assertThat(onTheClosingDay.season().open())
                .as("the day the season closes on is a day the season is open")
                .isTrue();
        assertThat(onTheClosingDay.state())
                .as("and nothing has lapsed, because nothing is over until the day is")
                .isEqualTo("ACTIVE");
        assertThat(app.enrolIn(ANKE, THE_LAST_DAY_ENTRY).state())
                .as("somebody reading the closing date off the card and joining that afternoon is "
                        + "taken on, which is what the date on the poster promises them")
                .isEqualTo("ACTIVE");

        // A deposit after the last look at the tab, and nothing reads the challenges again until
        // the season is over — which is how "judged one last time at the close" is demonstrated
        // rather than asserted. The reading this earns its silver on is taken after the season has
        // already closed, on the pass that then ends the enrolment.
        app.deposit(savings, ANKE, "140.00");

        app.daysPass(1);

        ChallengeView lapsed = app.challengeOf(ANKE, THE_SUMMER_FIVE_HUNDRED);
        assertThat(lapsed.state())
                .as("an enrolment still running when its season closes lapses rather than being "
                        + "left running for ever in a season nobody can join")
                .isEqualTo("EXPIRED");
        assertThat(lapsed.enrolled()).isFalse();
        assertThat(lapsed.season().open()).isFalse();
        assertThat(app.achievementsOf(ANKE))
                .extracting(AchievementView::challenge, AchievementView::rung)
                .as("and it was judged one last time on the way out: the two hundred and sixty she "
                        + "had saved by the close is this challenge's silver, paid at the close")
                .contains(Tuple.tuple(THE_SUMMER_FIVE_HUNDRED, "SILVER"));
        assertThat(app.achievementsOf(ANKE))
                .as("nothing already won is taken away by a season ending")
                .extracting(AchievementView::challenge, AchievementView::rung)
                .containsAll(wonInsideTheSeason.stream()
                        .map(won -> Tuple.tuple(won.challenge(), won.rung())).toList());
        assertThat(app.pointsBalanceOf(ANKE))
                .as("and the points a rung paid are not clawed back when the season it was in ends")
                .isGreaterThanOrEqualTo(pointsWonInsideTheSeason);

        assertThat(app.challengeOf(ANKE, THE_SUMMER_FIFTY).state())
                .as("one that was finished before the close stays finished: expiring is what "
                        + "happens to a challenge that was still running, not to one that is over")
                .isEqualTo("COMPLETED");
        assertThat(app.challengeOf(ANKE, THE_LAST_DAY_ENTRY).state())
                .as("and one joined on the last day with nothing reached lapses with nothing, "
                        + "which costs her nothing but the prize")
                .isEqualTo("EXPIRED");
        assertThat(app.achievementsOf(ANKE))
                .extracting(AchievementView::challenge)
                .doesNotContain(THE_LAST_DAY_ENTRY);

        ChallengeView evergreen = app.challengeOf(ANKE, FIVE_HUNDRED);
        assertThat(evergreen.state())
                .as("an evergreen challenge belongs to no season and no season closing touches it")
                .isEqualTo("ACTIVE");
        assertThat(evergreen.reading()).isEqualByComparingTo("260.00");

        assertThat(app.tryToEnrolIn(ANKE, THE_SUMMER_FIVE_HUNDRED).getStatusCode())
                .as("and the season that has just closed refuses a fresh enrolment the same way "
                        + "last winter's did")
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
