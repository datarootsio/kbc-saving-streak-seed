package io.dataroots.savingstreak.rewards;

import java.util.Set;

/**
 * Where one customer stands, as the Rewards module needs to be told it: what they have to spend,
 * what they have earned in all, how long a run of weeks they are on, and which badges they hold.
 *
 * <p><strong>This record is the whole of how eligibility is decided without this module reading
 * another one.</strong> The rules an offer may carry are about a streak, a badge and a lifetime of
 * points earned, and not one of those three is this module's to know: a streak is derived from the
 * deposit ledger, a badge is minted by the challenges the bank runs, and what somebody has earned
 * in all is a sum over the points ledger. A {@code RewardsService} that went and asked for them
 * would gain three outbound dependencies in one slice, and this module has spent the whole feature
 * not gaining any — the claim path already refuses to ask Points how many points there are and
 * simply asks for some, and is told whether there were enough.
 *
 * <p>So the facts arrive. The web layer, which already reads across modules — it is the place
 * {@code CustomerController.accountsOf} asks Accounts which accounts somebody holds and puts a
 * balance from Deposits beside each one — assembles one of these and hands it in. It is the shape
 * the {@code goals} module was built to and argues for at length: <em>the module reads no other
 * module; it is handed what it needs</em>. There it is load-bearing because Deposits has to ask
 * Goals a question and a Goals that asked back would be a cycle the context could not start. Here
 * it is load-bearing for a different reason and the reason is worth saying: Challenges pays its
 * rewards in points and Points is what a claim spends, so a Rewards that asked Challenges what
 * somebody has won would put a third module in a ring that is presently a line.
 *
 * <p><strong>The components are named after what they are, not after who produced them.</strong>
 * There is no {@code challengeBadges} and no {@code streaksWeeks} here, because the day the bank
 * decides a badge can be won some other way, a rule reading "requires this badge" should not have
 * to be rewritten — and because a record naming its sources would be this module knowing them
 * after all, in words rather than in imports. {@link #badgesHeld} is a set of plain strings for
 * exactly that reason: the column on the offer holds text, so nothing here and nothing in the
 * database names another module's enum.
 *
 * <p><strong>Every figure is a reading of a moment, and none of them is stored anywhere.</strong>
 * A run of weeks lapses, a balance is spent down and a badge is won; what is true when the
 * catalogue is read is what the card says, and the next read asks again. That is the same line the
 * whole feature holds about derived state, and it is why this is a parameter rather than a column
 * on the customer.
 *
 * <p><strong>{@link #pointsBalance} is here although no rule in this release reads it.</strong>
 * It is named because this record is the module's statement of what a customer's standing
 * <em>is</em> rather than a list of this slice's three thresholds, and because it is the fact the
 * contract's order of checks ends on: being short of points is asked last, always. It is in the
 * DEBUG line behind every lock, which is where it earns its place today — a customer complaining
 * that a card went grey is answered by all four figures the decision was made against, and by
 * nothing less than all four.
 *
 * <p>Public, unlike almost everything else in this module, because it is what comes <em>in</em>.
 * The entity, the repository and the rule that reads this stay behind {@link RewardsService}.
 */
public record CustomerStanding(long pointsBalance, long lifetimePointsEarned, int streakWeeks,
                               Set<String> badgesHeld) {

    public CustomerStanding {
        // Copied, so that a standing handed in cannot change underneath the rule reading it, and
        // so that every eligibility decision made against one was made against the set the caller
        // meant. Set.copyOf refuses a null element too, which is the one way a badge code could
        // arrive meaning nothing.
        badgesHeld = Set.copyOf(badgesHeld);
        // Said out loud rather than allowed through, because none of the three is a figure
        // anybody typed: a negative run of weeks or a negative lifetime is a mistake in whoever
        // derived one, and a rule quietly comparing against it would lock or unlock an offer for
        // a reason nobody could reconstruct. The balance is the same argument — points are never
        // owed here, and a debt would make "you have 40" a sentence about nothing.
        if (pointsBalance < 0 || lifetimePointsEarned < 0 || streakWeeks < 0) {
            throw new IllegalArgumentException("a customer's standing is never negative, and this "
                    + "one was pointsBalance=" + pointsBalance + " lifetimePointsEarned="
                    + lifetimePointsEarned + " streakWeeks=" + streakWeeks);
        }
        // A lifetime is everything that was ever earned and a balance is what is left of it, so a
        // balance above the lifetime is two figures that cannot both be true. Worth refusing here
        // rather than reasoning about later: a threshold on a lifetime is only worth setting if
        // the lifetime genuinely never goes down.
        if (pointsBalance > lifetimePointsEarned) {
            throw new IllegalArgumentException("what is left cannot be more than what was ever "
                    + "earned, and this standing had pointsBalance=" + pointsBalance
                    + " against lifetimePointsEarned=" + lifetimePointsEarned);
        }
    }

    /**
     * The standing of somebody this application cannot say anything about.
     *
     * <p>Not a customer with nothing: a customer with nothing has earned nothing and held no
     * badge, and this answers exactly that — which is safe precisely because it is never read.
     * Every path that hands a standing in checks first that the customer exists and refuses if
     * they do not, and the check that matters is the module's own, made after the offer and the
     * window and before anything about the customer's standing. This exists so that assembling a
     * standing for a customer nobody has heard of is not itself the refusal: the sentence somebody
     * gets for a bad identifier is "there is no such customer", and it must not become whichever
     * module happened to be asked first.
     */
    public static CustomerStanding nothingIsKnown() {
        return new CustomerStanding(0, 0, 0, Set.of());
    }

    /** Whether they hold the badge an offer asks for, which is the whole of that rule. */
    boolean holds(String badge) {
        return badgesHeld.contains(badge);
    }
}
