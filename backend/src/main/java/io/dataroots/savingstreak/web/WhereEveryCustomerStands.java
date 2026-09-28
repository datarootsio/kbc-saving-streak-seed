package io.dataroots.savingstreak.web;

import java.util.Optional;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.challenges.AnAchievement;
import io.dataroots.savingstreak.challenges.ChallengesService;
import io.dataroots.savingstreak.points.PointsService;
import io.dataroots.savingstreak.rewards.CustomerStanding;
import io.dataroots.savingstreak.rewards.WhereAWaiterStands;
import io.dataroots.savingstreak.streaks.StreaksService;
import org.springframework.stereotype.Component;

/**
 * Where one customer stands, gathered from the modules that each own a piece of it.
 *
 * <p><strong>The assembly the whole eligibility design rests on.</strong> Rewards declares the
 * facts it needs as a record and reads no module to get them; this is the thing that fills one
 * in, and it is in the web layer because this is where reading across modules already happens —
 * the place {@code CustomerController.accountsOf} asks Accounts which accounts somebody holds
 * and puts a balance from Deposits beside each one. Four facts from three modules and none of
 * them stored: a balance and a lifetime from Points, a run of weeks from Streaks, and a trophy
 * case from Challenges.
 *
 * <p><strong>A component of its own rather than a private method on the controller, which is
 * where it lived until waiting lists arrived.</strong> There are now two readers and only one
 * of them is a request. {@code CustomerController} calls this when somebody claims, holds,
 * joins a queue or reads the catalogue; the rewards module's nightly sweep calls it through
 * {@link WhereAWaiterStands}, which is an interface Rewards declares and this class implements,
 * because promotion has to know whether the person at the front of a queue still qualifies and
 * there is no controller at five in the morning to have worked it out. Two assemblies would be
 * two answers to one question, and the one thing worse than a waiter passed over for a rule is
 * a waiter passed over by a copy of a rule.
 *
 * <p>The dependency still runs the only way it can. Rewards imports nothing new: it states what
 * it needs, in its own vocabulary, and is handed an answer. The argument for the interface
 * rather than for handing standings in as parameters — which is what every request-shaped path
 * still does — is on {@link WhereAWaiterStands}, and it comes down to the sweep not knowing
 * whose standing it will need until it has read tables only Rewards can read.
 *
 * <p><strong>The badges are the codes of the challenges they were won on.</strong> A customer
 * holds a challenge's badge once they have reached any rung of it, which is the honest unit for
 * a closed vocabulary: the rungs are marks on one running figure and gold is climbed through
 * bronze, so "holds a badge for this challenge" is the thing that is either true or not. A rule
 * wanting a particular rung would need either a second field or a compound string, and a
 * compound string is the expression language the spec refuses by name.
 *
 * <p><strong>The run of weeks is the one happening now, never the best there has ever
 * been.</strong> A rule saying "for customers on a run of ten weeks" is a rule about behaviour
 * the scheme wants to be happening, and the record is not it: a customer whose run lapsed in
 * March is not on a run today, and the streak record itself already draws exactly this line
 * where it decides what rate a euro earns — a rate is a state and a record is not. Gating the
 * best rewards on a best-ever figure would pay somebody forever for one good spring.
 *
 * <p><strong>Reading the trophy case also judges it, and that is wanted rather than
 * tolerated.</strong> {@code achievementsOf} mints whatever the customer has earned and not yet
 * been given before it answers, exactly as the achievements screen does — so a customer who has
 * just crossed a challenge's threshold finds the offer it gates already unlocked, with no other
 * action and nothing to press first. An eligibility rule read against yesterday's trophy case
 * would be a lock that only lifts when somebody happens to visit another tab. It is also why
 * the nightly sweep's promotion sees a waiter who qualified overnight as qualifying.
 *
 * <p><strong>A customer nobody has heard of is an empty answer and never an exception.</strong>
 * Challenges refuses a trophy case for an unknown customer, in its own words, and those are the
 * wrong words for somebody claiming a reward: the answer to a bad identifier is "there is no
 * such customer", said by whichever module was actually asked to do something. So this vouches
 * first and answers with nothing rather than throwing, and the refusal is left where it
 * belongs.
 *
 * <p>Package-private, like the controllers beside it. Rewards receives it as the interface and
 * never knows this class by name.
 */
@Component
class WhereEveryCustomerStands implements WhereAWaiterStands {

    private final AccountsService accounts;
    private final ChallengesService challenges;
    private final PointsService points;
    private final StreaksService streaks;

    WhereEveryCustomerStands(AccountsService accounts, ChallengesService challenges,
                             PointsService points, StreaksService streaks) {
        this.accounts = accounts;
        this.challenges = challenges;
        this.points = points;
        this.streaks = streaks;
    }

    /**
     * Where one customer stands, or nothing at all if this application has never heard of them.
     *
     * <p>The empty answer rather than {@link CustomerStanding#nothingIsKnown} is the interface's
     * decision and the argument is there: that constant is safe precisely because the paths
     * that use it never read it, and the sweep would. A caller with a request behind it turns
     * the absence back into the constant, because it has a module waiting to refuse the
     * identifier in its own words.
     */
    @Override
    public Optional<CustomerStanding> theStandingOf(long customerId) {
        if (!accounts.customerExists(customerId)) {
            return Optional.empty();
        }
        return Optional.of(new CustomerStanding(
                points.balanceOf(customerId),
                points.lifetimePointsEarnedBy(customerId),
                streaks.weekAndStreakOf(customerId).streak().currentWeeks(),
                challenges.achievementsOf(customerId).stream()
                        .map(AnAchievement::challengeCode)
                        .collect(Collectors.toUnmodifiableSet())));
    }
}
