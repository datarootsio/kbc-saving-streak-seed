package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Puts the challenges the bank offers into the database before the application serves anything, and
 * leaves them exactly as they are on every start after the first.
 *
 * <p><strong>Why they are rows at all.</strong> The thresholds, the words on the card and the points
 * each rung pays are things the bank tunes and seasons, and none of that should be a release. So a
 * challenge is a {@link ChallengeDefinition} row. What a challenge <em>asks</em> is still code, in
 * {@link ChallengeKind}, because a question over the deposit ledger is not a thing a row can say.
 *
 * <p><strong>Why they are seeded rather than migrated in.</strong> This is a training application
 * whose database is thrown away and remade; a participant who resets it has to find something to
 * join without configuring anything first. The same reason the customers, their households and their
 * budgets are seeded.
 *
 * <p><strong>Idempotent, by code.</strong> A row already carrying the code is left alone, untouched
 * and unread — so a database somebody has edited to retune a threshold keeps their figure, and a
 * restart never puts a second card of the same name on anybody's tab. That also means a change to
 * the figures below does <em>not</em> reach a database that already has the row: retuning a seeded
 * challenge is a database edit or a fresh file, and that is the trade this makes on purpose, because
 * the alternative is a start-up that overwrites whatever the bank changed last night.
 *
 * <p><strong>It seeds one season as well, and the season is open.</strong> A campaign nobody can
 * see is a feature nobody can demonstrate, and the alternative — a trainer configuring one before
 * the session — is the configuration this whole step exists to avoid. Its window is anchored to the
 * day of the first start rather than written out as dates, which is argued out at
 * {@link #theSeasonTheBankIsRunning}.
 *
 * <p><strong>It enrols nobody.</strong> A challenge counts nothing until a customer joins it, and a
 * season enrols nobody in the challenge inside it, so nothing anywhere else in this application
 * reads differently because these rows exist.
 *
 * <p><strong>It also makes the trophy case unique per enrolment and rung</strong>, which is the one
 * thing here that is not about the catalogue. It belongs in the same step because it has the same
 * deadline: both have to be true before the application serves a request, and a second component
 * doing one of them would be a second place to look for why a start did nothing.
 *
 * <p>The same shape as the other modules' start-up steps — {@code RewardsOnStartUp},
 * {@code PointsOnStartUp}, {@code BudgetsOnStartUp} — and run at the same point, which is once every
 * bean exists and before the web server binds its port. That ordering is the reason it is not a
 * {@code CommandLineRunner}: one of those runs after the application is already accepting requests,
 * and the first person to open the Challenges tab on the morning of a release would be shown an
 * empty catalogue.
 */
@Component
class ChallengesOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ChallengesOnStartUp.class);

    /** The one season the bank seeds, and the one challenge that belongs to it. */
    private static final String THE_SEASON = "THE_NINETY_DAY_PUSH";
    private static final String THE_SEASONS_CHALLENGE = "THE_SEASONS_THOUSAND";

    /**
     * How long before and after the day the seed first runs the season's window reaches.
     *
     * <p>Thirty behind so that it is plainly already running rather than opening this morning, and
     * far enough ahead that a participant can wind the development clock and watch it close over an
     * enrolment inside one session.
     *
     * <p><strong>Two hundred and forty ahead rather than the sixty this started at, and the reason
     * is the demonstration seed rather than the season.</strong> This window is anchored at
     * start-up, which is before anything else in this application has run; the demonstration then
     * spends development clock giving its accounts a past — a fortnight for the household that
     * arrives with a run of secured weeks, and a quarter for the saver who arrives with three months
     * of interest already paid (see {@code ASaverWithAnAccountOnEveryProduct}). A season sixty days
     * wide was over before the first screen of a fresh demonstration database was opened, and a
     * closed season is not a demonstration of a season. Two hundred and forty leaves about four
     * months of it standing on the day a trainer starts, which is room enough to wind it shut and to
     * spare. The trade is that closing it takes a longer wind than it used to, and the days are
     * measured against the seed because that is the thing that moves.
     */
    private static final int THE_SEASON_OPENED_THIS_MANY_DAYS_AGO = 30;
    private static final int THE_SEASON_CLOSES_IN_THIS_MANY_DAYS = 240;

    /**
     * What the bank offers out of the box: six challenges, one of every kind there is and the
     * saving question twice, so that a catalogue a participant has just reset already answers every
     * shape of question this module can ask.
     *
     * <p><strong>Two sizes of the same saving question</strong> — {@code SAVE_FIVE_HUNDRED} and
     * {@code SAVE_TWO_THOUSAND} — so that somebody putting away twenty euros a week has something to
     * aim at and somebody putting away four hundred has something harder. Then one about weeks, one
     * about what is being held, one about how long it has been held, and one about finishing the
     * goals you set: five kinds, six cards.
     *
     * <p><strong>They are not all repeatable, and the flag is the anti-farming rule rather than a
     * taste.</strong> The four flow challenges can be taken on again, because a fresh enrolment
     * takes a fresh reading to measure from and a second EUR 500 is a real second EUR 500. The two
     * stock ones — the buffer and the ninety days — cannot, because the same thousand euros would
     * otherwise be worth a gold badge every time somebody re-enrolled. The one inside the season is
     * a one-off as well: a season is won once.
     *
     * <p><strong>Nor are they all evergreen.</strong> Five belong to no campaign and are always
     * open; {@code THE_SEASONS_THOUSAND} belongs to the seeded season and can only be joined while
     * that window is open. That is the whole reason a season is seeded at all — see
     * {@link #theSeasonTheBankIsRunning}.
     *
     * <p>The words say the thing the arithmetic cannot, which for a saving challenge is that a
     * withdrawal costs a customer nothing and for a holding one is that it costs them everything.
     * That asymmetry is deliberate — one asks whether money is there and the other whether money was
     * put there — and saying so on the card is part of the work, because a customer who thinks using
     * their savings will wipe out their progress will not use their savings.
     *
     * <p>Built afresh on every call rather than held as a constant, because these are entities and a
     * constant would be one instance handed to {@code save} — which attaches it, gives it an
     * identifier, and turns the next start's insert into an update of a row this step is supposed
     * never to touch. Two applications in one JVM is not a hypothetical here: it is how the tests
     * assert what a restart does.
     */
    private static List<ChallengeDefinition> whatTheBankOffers() {
        return List.of(
            ChallengeDefinition.offering(
                    "SAVE_FIVE_HUNDRED",
                    "Save your first EUR 500",
                    "Put EUR 500 into savings that you have never had in savings before. Taking "
                            + "money out costs you nothing here: this counts what you have put "
                            + "away, not what you are holding. Paying the same euros back in after "
                            + "a withdrawal does not count them a second time.",
                    ChallengeKind.NEW_SAVINGS,
                    null,
                    List.of(new ChallengeRung(Rung.BRONZE, new BigDecimal("100.00"), 25),
                            new ChallengeRung(Rung.SILVER, new BigDecimal("250.00"), 75),
                            new ChallengeRung(Rung.GOLD, new BigDecimal("500.00"), 200)),
                    true,
                    null),
            ChallengeDefinition.offering(
                    "SAVE_TWO_THOUSAND",
                    "Save EUR 2,000",
                    "The same challenge at a bigger number, for a year of saving rather than a few "
                            + "months of it. EUR 2,000 of genuinely new saving, counted from the "
                            + "day you take it on and across every savings account you hold.",
                    ChallengeKind.NEW_SAVINGS,
                    null,
                    List.of(new ChallengeRung(Rung.BRONZE, new BigDecimal("500.00"), 150),
                            new ChallengeRung(Rung.SILVER, new BigDecimal("1000.00"), 350),
                            new ChallengeRung(Rung.GOLD, new BigDecimal("2000.00"), 800)),
                    true,
                    null),
            // Weeks rather than euros, and the thresholds below are counts: one week, three weeks,
            // five. The gold rung is the headline the challenge is named for, and bronze sits at a
            // single week on purpose — the first badge should land the first time somebody puts a
            // week's saving away, not a month later.
            //
            // The words have to say that these weeks need not be consecutive, because the same
            // application shows this customer a streak on another tab that says the opposite about
            // weeks in a row, and a customer who reads "five weeks" as "five weeks running" will
            // think a missed week cost them something it did not.
            ChallengeDefinition.offering(
                    "SECURE_FIVE_WEEKS",
                    "Five weeks of real saving",
                    "Secure five weeks. A week is secured when you have put at least the weekly "
                            + "minimum away in it, after anything you took back out during it — the "
                            + "same week your streak is counted in. They do not have to be five "
                            + "weeks in a row: a week you miss costs you your streak and costs this "
                            + "nothing, and every week you have already secured stays secured.",
                    ChallengeKind.SECURED_WEEKS,
                    null,
                    List.of(new ChallengeRung(Rung.BRONZE, new BigDecimal("1"), 50),
                            new ChallengeRung(Rung.SILVER, new BigDecimal("3"), 150),
                            new ChallengeRung(Rung.GOLD, new BigDecimal("5"), 400)),
                    true,
                    null),
            // The two stock challenges — money being there rather than having been put there — and
            // the first rows here that are not repeatable. That flag is the whole of their
            // anti-farming rule: a balance cannot be inflated by moving money about, so the only
            // way to sell the same thousand euros to the bank twice would be to finish the
            // challenge and join it again, and a challenge that can only be taken on once cannot be
            // finished twice. The words say the asymmetry out loud, because a customer who has just
            // watched a withdrawal wipe out eighty-nine days is owed the reason in advance.
            ChallengeDefinition.offering(
                    "BUILD_A_BUFFER",
                    "Build an emergency buffer",
                    "Get money behind you and keep it there. This one counts what you are holding "
                            + "right now across every savings account you have — not what you have "
                            + "ever put away — so taking money out lowers it. That is the point: a "
                            + "buffer you have spent is not a buffer. You can only take this "
                            + "challenge on once.",
                    ChallengeKind.BALANCE_REACHED,
                    null,
                    List.of(new ChallengeRung(Rung.BRONZE, new BigDecimal("500.00"), 100),
                            new ChallengeRung(Rung.SILVER, new BigDecimal("1000.00"), 250),
                            new ChallengeRung(Rung.GOLD, new BigDecimal("2500.00"), 600)),
                    false,
                    null),
            ChallengeDefinition.offering(
                    "HOLD_A_THOUSAND_FOR_NINETY_DAYS",
                    "Hold EUR 1,000 for ninety days",
                    "Leave it alone. Keep at least EUR 1,000 in savings, across every account you "
                            + "hold, for ninety days running. The count is in days held, and it "
                            + "starts again from nothing if you ever dip under EUR 1,000 — even "
                            + "for a day, and even if you put the money straight back. You can "
                            + "only take this challenge on once.",
                    ChallengeKind.BALANCE_HELD,
                    // The floor, in euros, and the one figure this kind asks of the bank. Every
                    // rung above is counted in days, so this is the only place the amount appears —
                    // which is what makes a January campaign asking for EUR 2,000 a row rather than
                    // a release.
                    new BigDecimal("1000.00"),
                    List.of(new ChallengeRung(Rung.BRONZE, new BigDecimal("30"), 100),
                            new ChallengeRung(Rung.SILVER, new BigDecimal("60"), 250),
                            new ChallengeRung(Rung.GOLD, new BigDecimal("90"), 600)),
                    false,
                    null),
            ChallengeDefinition.offering(
                    "FINISH_YOUR_GOALS",
                    "Finish what you started",
                    "Finish the savings goals you set yourself, on any savings account you hold. "
                            + "Each goal counts once: taking the money back out of a goal you have "
                            + "already finished and filling it again does not count it a second "
                            + "time. Goals you had already finished when you took this on do not "
                            + "count, because this counts what you finish from here.",
                    ChallengeKind.GOALS_COMPLETED,
                    null,
                    List.of(new ChallengeRung(Rung.BRONZE, new BigDecimal("1"), 40),
                            new ChallengeRung(Rung.SILVER, new BigDecimal("3"), 120),
                            new ChallengeRung(Rung.GOLD, new BigDecimal("5"), 300)),
                    true,
                    null),
            // The one card that is not evergreen, and the reason the season above it exists. It
            // pays better than the evergreen EUR 2,000 for less money, because that is what a
            // campaign is for — a reason to come back while it is on — and it is a one-off because
            // a season is won once.
            //
            // The words have to say when it closes as well as what it asks, because the date is on
            // the card and a customer deciding whether to take it on is deciding whether they have
            // time.
            ChallengeDefinition.offering(
                    THE_SEASONS_CHALLENGE,
                    "The season's EUR 1,000",
                    "The season's own challenge, and it only counts while the season is open. "
                            + "EUR 1,000 of genuinely new saving, counted from the day you take it "
                            + "on. What you have reached when the season closes is yours to keep; "
                            + "the rest of it simply lapses, and it costs you nothing but the "
                            + "prize. You can only take this one on once.",
                    ChallengeKind.NEW_SAVINGS,
                    null,
                    List.of(new ChallengeRung(Rung.BRONZE, new BigDecimal("250.00"), 100),
                            new ChallengeRung(Rung.SILVER, new BigDecimal("500.00"), 300),
                            new ChallengeRung(Rung.GOLD, new BigDecimal("1000.00"), 900)),
                    false,
                    THE_SEASON));
    }

    /**
     * The season the bank is running, anchored to the day this seed first ran.
     *
     * <p><strong>Relative to the first start rather than written out as dates, and the argument is
     * worth having out loud.</strong> A window hard-coded to, say, the first quarter of 2026 is a
     * season that is shut for every trainer who runs this application in 2027: the one thing the
     * seeded campaign has to be is <em>open</em>, because it exists so that a participant who has
     * just reset the database can see a campaign without configuring one. A fixed window fails at
     * exactly that, quietly, and later every year.
     *
     * <p>The objection to the other choice is that a window seeded from "now" moves every time the
     * database is rebuilt, and it does. That is the right trade here and very nearly a feature: this
     * is a training application whose database is thrown away and remade, seeding is idempotent so
     * the window is written once and then never touched again, and for the whole life of that file
     * the season sits still. A trainer who wants a season with the dates on the poster edits the row
     * — which is the same answer this class gives about retuning a threshold.
     *
     * <p>The day comes off the application's clock rather than {@code LocalDate.now()}, so that
     * everything about this application's calendar agrees, and it is read in the zone this
     * application counts its days in. On the only start that matters — the first one, against a
     * database with nothing in it — a development clock has not been wound anywhere yet, so the
     * anchor is simply to-day.
     */
    private Campaign theSeasonTheBankIsRunning() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
        return Campaign.running(
                THE_SEASON,
                "The ninety-day push",
                today.minusDays(THE_SEASON_OPENED_THIS_MANY_DAYS_AGO),
                today.plusDays(THE_SEASON_CLOSES_IN_THIS_MANY_DAYS));
    }

    private final ChallengeDefinitionRepository definitions;
    private final CampaignRepository campaigns;
    private final ChallengeAwardRepository awards;
    private final GoalSeenFinishedRepository goalsSeenFinished;
    private final Clock clock;

    ChallengesOnStartUp(ChallengeDefinitionRepository definitions, CampaignRepository campaigns,
                        ChallengeAwardRepository awards,
                        GoalSeenFinishedRepository goalsSeenFinished, Clock clock) {
        this.definitions = definitions;
        this.campaigns = campaigns;
        this.awards = awards;
        this.goalsSeenFinished = goalsSeenFinished;
        this.clock = clock;
    }

    @Override
    public void afterSingletonsInstantiated() {
        makeTheTrophyCaseUniquePerRung();
        makeAGoalCountOncePerEnrolment();
        seedTheSeason();
        List<ChallengeDefinition> offers = whatTheBankOffers();
        List<String> written = offers.stream()
                .filter(offer -> !definitions.existsByCode(offer.code()))
                .map(offer -> definitions.save(offer).code())
                .toList();
        if (written.isEmpty()) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that a tab that looks short after a restart is not blamed on a step nobody can see.
            log.debug("the challenges the bank offers are already there challenges={} written=0",
                    offers.stream().map(ChallengeDefinition::code).toList());
            return;
        }
        log.info("challenges the bank offers seeded written={} codes={} alreadyThere={}",
                written.size(), written, offers.size() - written.size());
    }

    /**
     * Writes the one season the bank runs, and leaves it exactly where it is on every start after
     * the first.
     *
     * <p>Before the definitions, because one of them names it: a challenge seeded against a campaign
     * row that is not there is one this module refuses to enrol anybody in, and a start that wrote
     * them the other way round would leave a window — brief, but real — in which the season's
     * challenge was on the tab and could not be joined.
     *
     * <p>Idempotent by code, the way the definitions are and for the same reason: the dates are the
     * bank's to change, and a restart that rewrote them would move a season out from under whoever
     * had already joined it. It also means the window is anchored to the day of the first start and
     * stays there, which is the whole of the argument in {@link #theSeasonTheBankIsRunning}.
     */
    private void seedTheSeason() {
        if (campaigns.existsByCode(THE_SEASON)) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // a season that reads as older than a participant expected is not blamed on a step
            // nobody can see.
            log.debug("the season the bank is running is already there campaign={}", THE_SEASON);
            return;
        }
        Campaign season = campaigns.save(theSeasonTheBankIsRunning());
        log.info("the season the bank is running was seeded campaign={} title={} opensOn={} "
                        + "closesOn={} anchoredTo={}",
                season.code(), season.title(), season.opensOn(), season.closesOn(), clock.instant());
    }

    /**
     * Makes one award per enrolment per rung a rule the database keeps, before anything can judge.
     *
     * <p>Which is what makes the judging pass idempotent by construction rather than by care. The
     * pass also checks in Java — it awards only rungs that have no award row yet — so a second run
     * pays nothing without this; but the check and the guarantee are different things. Two reads of
     * the challenges tab arriving at the same instant would both find no bronze and both mint one,
     * and only a rule the database keeps stops that.
     *
     * <p>Here rather than on the entity because the entity cannot say it. The schema is generated
     * from the entity model ({@code ddl-auto=update}) against SQLite, and that dialect writes a
     * composite unique clause nowhere. A {@code create unique index} is a statement it does accept,
     * so this is where the guarantee comes from. {@code LoyaltyOnStartUp} says the same thing at
     * greater length about the same problem, and this is deliberately the same answer.
     *
     * <p>Before the definitions are seeded and before the web server binds its port, so no rung can
     * be judged against a trophy case that is not yet unique.
     */
    private void makeTheTrophyCaseUniquePerRung() {
        if (awards.theTrophyCaseIsAlreadyUniquePerRung() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("the trophy case is already unique per enrolment and rung "
                    + "index=one_award_per_enrolment_per_rung");
            return;
        }
        awards.makeTheTrophyCaseUniquePerRung();
        log.info("the trophy case was made unique per enrolment and rung "
                + "index=one_award_per_enrolment_per_rung columns=[enrolment_id, rung]");
    }

    /**
     * Makes one sighting per enrolment per goal a rule the database keeps, before anything can read a
     * goals challenge.
     *
     * <p>Which is what makes "each goal counts once" true by construction rather than by care.
     * {@link TheGoalsFinishedSinceYouEnrolled} also checks in Java — it writes a sighting only for a
     * goal this enrolment has none of — and would count nothing twice on its own; the check and the
     * guarantee are different things. Two reads of the challenges tab arriving at the same instant
     * would both find the goal unseen and both write it down, and the reading is a count of rows, so
     * without this a customer who looked twice at once would have finished the same goal twice.
     *
     * <p>Here rather than on the entity for the reason the trophy case's own index is, and in the
     * same step for the same reason: both have to be true before the application serves a request,
     * and a second component doing one of them would be a second place to look for why a start did
     * nothing.
     */
    private void makeAGoalCountOncePerEnrolment() {
        if (goalsSeenFinished.aGoalIsAlreadySeenOncePerEnrolment() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("a goal is already seen once per enrolment "
                    + "index=one_sighting_per_enrolment_per_goal");
            return;
        }
        goalsSeenFinished.makeAGoalSeenOncePerEnrolment();
        log.info("goals seen finished were made unique per enrolment and goal "
                + "index=one_sighting_per_enrolment_per_goal columns=[enrolment_id, goal_id]");
    }
}
