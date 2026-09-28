package io.dataroots.savingstreak.simulation;

/**
 * One line of the answer to "I will have this one": which of the four changes it was about, whether
 * the application made it or it is the customer's to make, and the sentence a customer reads to see
 * the plan they now have.
 *
 * <p><strong>The two outcomes are one shape rather than two lists.</strong> A branch that raises a
 * weekly amount and takes five hundred out on a day is one decision, and a customer who pressed
 * once is owed one account of what that press did — read straight down, in the order they built the
 * branch in. Splitting it into "applied" and "not applied" would make the reader assemble the story
 * back out of two lists and, worse, would let a page draw only the first of them: a plan that
 * quietly omits the withdrawal the customer still has to make is exactly the half-told plan this
 * feature exists to prevent.
 *
 * <p><strong>{@code yoursToCarryOut} is a flag and not the absence of a sentence.</strong> Both
 * kinds of line carry words, because "nothing was done about this" is not an answer a customer can
 * act on. A withdrawal is a thing somebody does on the day and a pause is what {@code RulePause}
 * already is, so each line says which screen it is done on rather than leaving the customer to
 * believe the press did it. The application never quietly moves money or stands a rule down off the
 * back of a projection, and this field is how the answer says so out loud.
 *
 * <p><strong>The sentence is written where the change is, not here.</strong> What a change did — or
 * what is left to do about it — is the kind's own to say, in its own figures, which is why there is
 * no switch over kinds anywhere near this record and why a fifth kind of change would arrive
 * carrying its own words rather than needing a case added to somebody else's method.
 *
 * @param kind           which of the four changes this line is about, so that a page can draw the
 *                       same chip it drew when the branch was being built
 * @param yoursToCarryOut true when the application deliberately did nothing durable and the customer
 *                       is the one who carries it out
 * @param what           the whole of what happened, or what is left to happen, in one sentence
 */
public record AChangeThePlanNowCarries(AKindOfAdjustment kind, boolean yoursToCarryOut, String what) {

    /**
     * A change the application made, through the module that owns the rule it changed.
     *
     * <p>The sentence names what the figure was before as well as what it is now, because a customer
     * who presses twice raises their capacity twice and has to be able to see that the second press
     * was a second decision rather than a repeat of the first.
     */
    static AChangeThePlanNowCarries applied(AKindOfAdjustment kind, String what) {
        return new AChangeThePlanNowCarries(kind, false, what);
    }

    /**
     * A change the application deliberately did not make, named as the customer's own to carry out.
     *
     * <p>Not a refusal and not a silence. The branch was perfectly askable and the rest of it was
     * applied; this part simply has no durable counterpart in this application that adopting could
     * honestly press, and saying which screen it is done on is the whole of what this line is for.
     */
    static AChangeThePlanNowCarries yoursToCarryOut(AKindOfAdjustment kind, String what) {
        return new AChangeThePlanNowCarries(kind, true, what);
    }
}
