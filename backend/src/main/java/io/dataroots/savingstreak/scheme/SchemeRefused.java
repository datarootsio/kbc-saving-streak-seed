package io.dataroots.savingstreak.scheme;

/**
 * A version of the scheme this application will not publish, carrying the reason in words the
 * person who typed it can act on.
 *
 * <p><strong>A refusal of this module's own rather than {@code ProductRefused} borrowed.</strong>
 * The two modules publish versions the same way and are about different things, and a shared
 * vocabulary would tie them together exactly where they are about to grow apart: the products
 * module's reasons are about an agreement — a product closed to new accounts, an account locked
 * into a term — and not one of them is a thing a scheme could be refused for. The scheme's reasons
 * are about a ladder, a calendar and a promise made to everybody at once.
 *
 * <p><strong>It did not exist until there was a door to raise it at.</strong> Ticket 1 wrote the
 * rows, the readings and the seed, and deliberately wrote no refusal, because an enum of reasons
 * with no thrower is a vocabulary nobody speaks. Every value below is raised somewhere.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer.
 */
public class SchemeRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal can be about.
     *
     * <p>A {@code switch} over it in the web layer — like every other refusal in this codebase. The
     * next reason a version can be refused for arrives as a value here and the compiler then asks
     * the web layer what status it deserves, rather than letting it quietly inherit one that may
     * not fit.
     *
     * <p><strong>Seven values for fifteen refusals, and the grouping is the question "what does the
     * person do next".</strong> A value per objection would be fifteen entries in that switch that
     * every reader has to check are all answering the same question, in order to draw a distinction
     * nothing downstream makes. So the figures that are simply wrong share one value and the
     * sentence says which box; the two shapes that cannot be a ladder have one each, because a
     * ladder that descends and a set of rungs out of order are two different things to go and look
     * at; the calendar has one for the box left empty and one for a day that cannot start a
     * version; and the one refusal that is about the scheme's own history rather than about the
     * form has its own, because that difference is exactly the one the web layer turns into a
     * status.
     */
    public enum Kind {

        /**
         * One of the numbers is missing, is outside what it may be, or is quoted more finely than
         * the scheme can hold.
         *
         * <p><strong>One value for eleven figures, and the sentence says which one.</strong> The
         * same reading {@code ProductRefused.A_FIGURE_THESE_TERMS_CANNOT_CARRY} makes of its eight:
         * which box stopped you belongs in the sentence and deliberately not in the word, because
         * they are all a box to go back and fix.
         *
         * <p><strong>A figure nobody sent is refused rather than read as nought, and here that
         * matters more than it does on a product's terms.</strong> Six of a product's eight figures
         * read nought as a real agreement — no notice, no term, no floor — so an empty box read as
         * nought publishes something somebody could have meant. Not one figure of the scheme has an
         * absence to read. A weekly threshold of nought is a scheme in which every week secures
         * itself; an ordinary rate of nought is a deposit that silently earns no points; a points
         * lifetime of nought is a batch that expires the moment it is earned; a notice period of
         * nought days is a warning sent on the morning of the thing it warns about. So the figures
         * are required and most of them are required to be more than nothing, and only the step of
         * the ladder may honestly be nought.
         *
         * <p><strong>A figure too precise is refused rather than rounded</strong>, because rounding
         * would publish a scheme nobody typed, to everybody, from a Monday. That is the silent
         * change this whole feature exists to prevent, and it is the reading
         * {@link BasisPointsOfTheScheme} and {@code AmountOfMoney} already make of their own units.
         */
        A_FIGURE_THE_SCHEME_CANNOT_CARRY,

        /**
         * The ladder does not climb: a step below nothing, or a cap below the ordinary rate.
         *
         * <p><strong>Its own value because it is not a figure that is wrong — it is two figures
         * that disagree.</strong> Every number in {@code 1.5000}, {@code -0.1000} and
         * {@code 0.8000} is a multiple this module can hold perfectly well; what cannot be
         * published is the shape they make. A person told "the step is not a figure the scheme can
         * carry" would go and look at the step, which is fine, and change it to something else that
         * is also below nothing; a person told the ladder descends goes and looks at the ladder.
         *
         * <p>Flat is allowed and is not this refusal. A step of nought with a cap equal to the
         * ordinary rate is a scheme that pays the same for one week as for twenty, which is a
         * decision a bank may honestly make and publish a line about. Only descending is
         * impossible, because a run of weeks that paid less the longer it ran would be a reward for
         * stopping.
         */
        A_LADDER_THAT_DESCENDS,

        /**
         * The balance rungs are not a ladder: none at all, out of order, repeated, not whole euros,
         * or a rung at nothing.
         *
         * <p><strong>Separate from the ladder above, because they are a different ladder.</strong>
         * One is what a run of weeks pays and the other is what a balance is congratulated on
         * reaching, and a single value covering both would make the sentence the only way to tell
         * which of the two an administrator has to go and fix.
         *
         * <p>Whole euros because these are the figures a customer is congratulated with — "you have
         * reached EUR 500" — and a rung at EUR 499.99 is a threshold nobody would write down on
         * purpose and everybody would read as a bug. Strictly ascending because the order is the
         * ladder: two rungs at one figure would congratulate somebody twice for arriving once, and
         * a rung below the one before it would be one nobody can ever climb past.
         */
        RUNGS_THAT_ARE_NOT_A_LADDER,

        /**
         * No day was named for it to take effect on.
         *
         * <p>Its own value rather than one of the figures, for the reason the products module gives
         * about the same box: the day is not a number that could be wrong by a hundredth, it is the
         * whole of what "from when" means, and there is no sensible thing to assume in its place.
         * Next Monday would be an assumption, and which Monday a repricing starts on is the single
         * most consequential thing on this form.
         */
        A_VERSION_WITH_NO_DAY_IT_TAKES_EFFECT,

        /**
         * The day named is not a Monday, or is not still to come.
         *
         * <p><strong>Two objections in one value, because the thing to do about either is to pick a
         * Monday further on.</strong> Which of the two stopped you is in the sentence, and the
         * sentence names the earliest day that would have been accepted, so the fix is one edit
         * rather than a calendar exercise.
         *
         * <p><strong>This is the one rule of the scheme the products module does not have, and it
         * is the point of the ticket.</strong> A savings week runs Monday to Sunday and is judged
         * by the scheme in force on its own Monday, so a version taking effect on a Wednesday would
         * judge one week under two schemes — half at EUR 50 and half at EUR 80, which is not a week
         * anybody can be told the rules of. And a version taking effect today or earlier would
         * change which weeks have already counted: a customer's run of weeks is re-derived from the
         * ledger every time anybody reads it, so raising the threshold with yesterday's date would
         * un-secure weeks somebody was told they had secured. A product may be backdated precisely
         * because neither of those is true of it: an account is pinned to the version it was opened
         * under, so nothing already decided moves.
         */
        A_MONDAY_A_VERSION_CANNOT_TAKE_EFFECT_ON,

        /**
         * It would take effect before a version that is already announced and has not started yet,
         * so publishing it would strand that one.
         *
         * <p><strong>The one refusal here that is about the scheme's history rather than about the
         * form.</strong> Nothing was typed wrong — the day is a Monday, it is still to come, the
         * figures are figures — and it is a version somebody else published that will not have it.
         * That difference is what the web layer turns into a conflict rather than a bad request.
         *
         * <p><strong>The same Monday is allowed, and that is deliberate.</strong> Two versions
         * taking effect on one Monday is a correction announced in advance and then thought better
         * of, {@link TheSchemeInForceOn} settles it by the higher version number, and superseding
         * by publishing again is the only way to correct an announcement in a module with no edit
         * door. What is refused is an <em>earlier</em> Monday, because the version in force is the
         * highest number whose day has come: a version 4 starting a fortnight before an announced
         * version 3 would mean version 3 never came into force at all, on any day, having been
         * published and paid for and read by customers. That is not a tie for the web layer to
         * break — it is a version quietly deleted by arithmetic, in a module whose whole promise is
         * that nothing is ever deleted.
         */
        A_MONDAY_BEFORE_ONE_ALREADY_ANNOUNCED,

        /**
         * No line saying what changed and why.
         *
         * <p>Required on every version, where a product's first one is allowed to say nothing
         * because nothing changed and the customer chose that agreement. Nobody chose the scheme.
         * It applies from its Monday to everybody the bank has, so a version that arrived without a
         * sentence would be a rate change with a date and no explanation — and "it cannot be
         * explained" is one of the three things this feature exists to fix. The seeded version 1
         * writes its own line, so there is no first version here for this rule to make an exception
         * of.
         *
         * <p>Blank counts as absent, because a space is what a required field gets filled with by
         * somebody who has decided the rule does not apply to them.
         */
        A_VERSION_THAT_DOES_NOT_SAY_WHAT_CHANGED
    }

    private final Kind kind;

    SchemeRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
