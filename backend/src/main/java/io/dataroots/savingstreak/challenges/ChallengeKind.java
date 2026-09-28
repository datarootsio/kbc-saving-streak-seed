package io.dataroots.savingstreak.challenges;

/**
 * The question a challenge asks. One value here is one rule, and a rule is code.
 *
 * <p><strong>Why the kinds are an enum and the challenges are rows.</strong> The bank wants to add a
 * challenge, retune a threshold, change what a rung pays, or run a January season, and none of those
 * should be a release — so a challenge is a {@link ChallengeDefinition} row with its code, its
 * words, its thresholds and its points in it. What a challenge <em>means</em> is a different sort of
 * thing entirely: "how much has genuinely gone into savings since you enrolled" is a derivation over
 * the deposit ledger, and there is no row that can express one. Making the rules configurable too
 * would have meant inventing a scripting language for a training application that has no use for
 * one. So the definitions name a kind, and the kinds live here.
 *
 * <p><strong>{@link #NEW_SAVINGS} is a flow rather than a stock</strong>, and the distinction decides
 * its anti-farming rule. It measures what has happened since the enrolment began, which is why it is
 * repeatable: a fresh enrolment takes a fresh reading to measure from, so doing "save EUR 500" three
 * times means EUR 1,500 genuinely put away. A stock kind — how much are you holding right now —
 * would not be, because the same thousand euros would otherwise be worth a gold badge every time
 * somebody re-enrolled.
 *
 * <p><strong>Five kinds, in two families, and the family decides the anti-farming rule.</strong> A
 * <em>flow</em> kind — {@link #NEW_SAVINGS}, {@link #SECURED_WEEKS}, {@link #GOALS_COMPLETED} —
 * measures what has happened since the enrolment began, and is safe to repeat because a fresh
 * enrolment takes a fresh reading to measure from. A <em>stock</em> kind —
 * {@link #BALANCE_REACHED}, {@link #BALANCE_HELD} — measures what is true right now, needs no
 * anti-farming rule of its own because a balance cannot be inflated by moving money about, and must
 * not be repeatable, because the same thousand euros would otherwise be worth a gold badge every
 * time somebody re-enrolled. Between them they cover everything the feature spec asks for, and
 * there is no sixth waiting.
 *
 * <p>The thresholds are counted in whatever the kind measures: euros for the two that ask about
 * money, weeks for one, days for one and goals for the last. That is why
 * {@link ChallengeRung#threshold()} is a plain number rather than an amount of money.
 *
 * <p><strong>The derivation is a {@link HowAChallengeIsRead} beside this file, and adding one edits
 * nothing.</strong> A value here, an implementation of that interface that names it, and a seeded
 * row: those three, and no signature anywhere is widened. {@link ChallengesService} indexes the
 * readings by kind when it is built and refuses to start on a value that has none, naming it — which
 * is what replaces the exhaustive switch that used to make the compiler ask. Later rather than the
 * compiler, and in exchange four kinds can arrive at once without four tickets editing one method.
 */
public enum ChallengeKind {

    /**
     * How much has genuinely gone into savings since you enrolled: the customer's high-water mark
     * now, less the mark their enrolment recorded.
     *
     * <p>The same figure a deposit earns points against, deliberately. Euros that only refill the
     * gap a withdrawal left do not move the mark, so they move no challenge either — the rule falls
     * out of the arithmetic rather than being policed. Its rungs are amounts of money.
     */
    NEW_SAVINGS,

    /**
     * How many separate weeks you have secured since you enrolled: every Monday-to-Sunday week in
     * which the net new savings reached the weekly minimum, counted whether or not they are next to
     * each other. Its rungs are counts of weeks.
     *
     * <p><strong>Deliberately a different game from the streak, and the difference is the point.</strong>
     * A streak is a run and a week that falls short ends it; this is a count and a week that falls
     * short costs it nothing. A customer who dips into their savings in week two loses their run and
     * keeps both of the weeks they secured either side of it. The bank offers both because they ask
     * for different things — one for consistency, one for weeks of real saving however they fall —
     * and a customer afraid to use their own savings is the failure mode this half exists to avoid.
     *
     * <p><strong>It reads the week rule rather than restating it.</strong> What a week is and what
     * secures one are the Streaks module's answers, asked for through
     * {@code StreaksService.securedWeeksBetween}: the same EUR 50, the same net-of-withdrawals
     * figure, the same Brussels Monday. Nothing in this module knows the minimum, and there is no
     * second place for it to drift from.
     *
     * <p>A flow rather than a stock, so it is repeatable and a re-enrolment counts from its own
     * start: the weeks that filled the first round stay in the trophy case and count for nothing in
     * the second. Its anti-farming rule is the weekly one it borrows — securing a week means EUR 50
     * genuinely going in and staying in over it — so it needs none of its own.
     */
    SECURED_WEEKS,

    /**
     * How much is in savings right now: what the customer holds across every savings account they
     * have, as it stands at this moment.
     *
     * <p>A stock rather than a flow, and the first of the two. It asks nothing about how the money
     * got there and everything about whether it is there — which is what makes it the honest
     * question to put to an emergency buffer, because a buffer that was built and then spent is not
     * a buffer. Its rungs are amounts of money.
     *
     * <p>It needs no anti-farming rule of its own, and that is the whole reason a stock kind is safe
     * to pay for. A balance cannot be inflated by moving money about: paying a thousand euros in and
     * taking them out again leaves the reading exactly where it started. What it cannot survive is
     * being re-enrolled in, because the same thousand euros would be worth a fresh gold badge every
     * time — so a challenge of this kind is seeded as once in a lifetime.
     */
    BALANCE_REACHED,

    /**
     * How many consecutive days the balance has stayed at or above a floor, the floor being the
     * challenge's {@link ChallengeDefinition#kindParameter()}.
     *
     * <p>The other stock kind, and the one that pays for leaving money alone rather than for putting
     * it there. Its rungs are counts of days, and the headline case is ninety of them.
     *
     * <p><strong>Derived by walking the movements, never written down.</strong> The deposits and
     * withdrawals across everything the customer holds are run forward in time order, and the days
     * held are the days since <em>the later of</em> two moments: the last moment the balance rose to
     * the floor and did not fall below it again, and the moment the customer enrolled. A dip below
     * the floor, however brief, restarts the count — that is what the word means — and a customer
     * who has never reached the floor has held it for no days. Because it is derived, winding the
     * development clock back un-holds it correctly, which a stored "held since" date could not do.
     *
     * <p><strong>The later of the two, because enrolling counts from the moment you enrol.</strong>
     * Without it, somebody who had quietly left a thousand euros alone for the past year would join
     * and be ninety days in before they had done anything at all — gold on the spot, for a badge
     * that would mean nothing. It is the same rule the other kinds keep in their own way: the mark
     * on a {@link #NEW_SAVINGS} enrolment, and the goals a {@link #GOALS_COMPLETED} enrolment rules
     * out as already finished. The clamp does not turn a stock into a flow: the reading is still a
     * fact about the balance as it stands, and the floor moment still governs whenever it is the
     * later one — which it is for everybody who joins before they have the money.
     *
     * <p>Once in a lifetime for the reason {@link #BALANCE_REACHED} is: the same untouched thousand
     * euros would otherwise be worth a fresh ninety days, and a fresh gold badge, every quarter for
     * as long as the customer left it alone. The clamp above already stops the cruder version of
     * that — re-enrolling the next morning now reads zero, not ninety — so this rule rests on the
     * standing objection rather than on the instant one.
     */
    BALANCE_HELD,

    /**
     * How many of your savings goals have you finished since you enrolled: the goals of the
     * customer's, on any savings account they hold, that this enrolment has seen standing at
     * {@code COMPLETED} while it was live. Its rungs are counts of goals.
     *
     * <p><strong>The one kind that stores anything, and what it stores is an observation rather than
     * a counter.</strong> The goals module derives completion from a goal's allocation and does not
     * date it, so "finished since you enrolled" is a question its ledger cannot answer however hard
     * anybody looks: it says whether a goal has arrived, never when it arrived or how many times. So
     * the reading writes down one goal it has seen finished at a time and counts what it has written
     * down. Adding a completion date to the goals module was the alternative and was rejected in the
     * feature spec — it changes a module this feature has no other business in, and it would still
     * need back-filling for every goal already complete.
     *
     * <p>Keyed by the goal, so emptying a finished goal and filling it again counts once; keyed by
     * the enrolment as well, which makes it repeatable in the same way the other flow kinds are — a
     * second time round counts the goals of the second time round. A goal that had already reached
     * its target when the customer enrolled counts for nothing, because enrolling counts from the
     * moment you enrol. See {@link TheGoalsFinishedSinceYouEnrolled}.
     */
    GOALS_COMPLETED
}
