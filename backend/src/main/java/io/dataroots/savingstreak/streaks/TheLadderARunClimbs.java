package io.dataroots.savingstreak.streaks;

import java.math.BigDecimal;
import java.util.Objects;

import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;

/**
 * The three figures a run of weeks is priced from, as one thing: what the first week pays, what each
 * further week adds, and where the climbing stops.
 *
 * <p><strong>One argument rather than three, and the reason is the one this codebase keeps
 * making.</strong> {@link StreakMultiplier#paidByAStreakOf(int, TheLadderARunClimbs)} needs all
 * three of them or none, and three loose {@link BigDecimal} parameters in a row is a signature where
 * the step and the cap can be handed over the wrong way round without anything objecting — a ladder
 * that climbs by one and a half and caps at a tenth is arithmetic that runs perfectly well and pays
 * nonsense. A caller naming the type cannot make that mistake silently, and a reader of a call site
 * sees a ladder rather than three numbers.
 *
 * <p><strong>It lives in {@code streaks} and not in {@code scheme}, deliberately.</strong> The
 * scheme module's whole stated direction is that it depends on nothing in this application: it holds
 * numbers, and the rules stay where they are argued. A ladder is not a number, it is the shape of
 * one rule in this module, so building it out of the plain multiples the scheme publishes is this
 * module's job and {@link #theLadderIn(TheSchemeAsPublished)} is where it is done — once, so that no
 * caller assembles a ladder out of loose figures. The rungs a balance climbs are the same argument
 * pointing the other way, and live in {@code notifications} for it.
 *
 * <p><strong>The figures keep the scale they arrive with, and the rounding happens once at the
 * end.</strong> A published scheme quotes its multiples to four places — {@code 1.0000},
 * {@code 0.1000}, {@code 1.5000} — because the step is the figure that needs them: a ladder that
 * climbs by 0,0250 a week is a real ladder, and a step rounded to two places on the way in would be
 * 0,03 or 0,02 and would be a different scheme from the one that was published. So nothing is
 * requoted here. {@link StreakMultiplier#asARate} puts the answer back to the two places a rate is
 * written at, after the arithmetic rather than before it, which is the only place the scale of the
 * figures and the scale of the rate have to agree.
 *
 * <p><strong>No rule about the figures is enforced here.</strong> A cap equal to the ordinary rate
 * is a flat scheme, a step of nought is the same scheme said another way, and both are things a bank
 * may legitimately publish. Whether a ladder that <em>descends</em> may be published is a question
 * about publishing and is refused at the door that publishes, which is where every other refusal
 * about the scheme is argued. What is checked here is only that all three figures are present,
 * because a null in one of them would surface as a {@link NullPointerException} several frames away
 * from whoever left it out.
 *
 * <p>Compared as a value by {@link BigDecimal#equals}, which is scale-sensitive: a ladder built from
 * {@code 1.00} and one built from {@code 1.0000} are the same ladder and are not equal objects. That
 * is a statement about this record and not a defect to work around — a test comparing two ladders
 * should compare the rates they pay, which is the only thing about a ladder anybody is paid from.
 */
public record TheLadderARunClimbs(

        /** What the first week of a run pays per whole euro, as a multiple — {@code 1.0000}. */
        BigDecimal theOrdinaryRate,

        /** What each further consecutive secured week adds to it, as a multiple — {@code 0.1000}. */
        BigDecimal extraForEachFurtherWeek,

        /** Where the ladder stops climbing, as a multiple — {@code 1.5000}. */
        BigDecimal theMostAStreakPays) {

    public TheLadderARunClimbs {
        Objects.requireNonNull(theOrdinaryRate, "a ladder says what its first week pays");
        Objects.requireNonNull(extraForEachFurtherWeek, "a ladder says what a further week adds");
        Objects.requireNonNull(theMostAStreakPays, "a ladder says where it stops climbing");
    }

    /**
     * The ladder a published version of the scheme describes.
     *
     * <p>The one place the scheme's three multiples become a ladder. A caller holding a
     * {@link TheSchemeAsPublished} — the week-and-streak derivation, asking the history what each
     * week's own Monday published — reaches for this rather than calling the constructor with three
     * accessors in a row, because the constructor with three accessors in a row is exactly the call
     * that can be written with the step and the cap transposed.
     *
     * <p>Nothing is converted. The scheme already publishes plain multiples of one, in the units the
     * rest of this application speaks, and turning them into anything else here would be a second
     * opinion about a unit that module has already settled.
     */
    public static TheLadderARunClimbs theLadderIn(TheSchemeAsPublished scheme) {
        return new TheLadderARunClimbs(scheme.theOrdinaryRate(), scheme.extraForEachFurtherWeek(),
                scheme.theMostAStreakPays());
    }
}
