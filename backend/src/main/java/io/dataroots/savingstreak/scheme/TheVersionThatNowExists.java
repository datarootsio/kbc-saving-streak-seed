package io.dataroots.savingstreak.scheme;

/**
 * What publishing a version of the scheme answers with: the version that now exists, and whether
 * its day has come.
 *
 * <p><strong>A receipt rather than a reading.</strong> {@link TheSchemeAsPublished} says what a
 * version <em>is</em>, and it is the same shape wherever a version is read; this says what just
 * happened, and it exists because an administrator who has pressed Publish has two questions and
 * the second one is not answered by the row. Which version did I just write — that is the module's
 * answer, never the caller's, so it has to be handed back. And is it deciding anything yet.
 *
 * <p><strong>{@link #itsDayHasCome()} is false on every version this module will accept today, and
 * it is still a field.</strong> That is worth arguing, because a constant dressed as a fact is
 * usually a mistake. Two things make it one here. The screen that confirms a publish has to word
 * "published, and in force from Monday the twelfth" without re-deriving the rule that made it so —
 * a page that computed "not yet" from its own reading of the effective date and the clock would be
 * a second place the rule lives, and it would be wrong for anybody whose tab was open across a
 * Monday morning. And it is read off the application's own clock at the moment of writing, so it is
 * a fact this module observed rather than a constant it asserted: the day the refusal that makes it
 * false is loosened — for a correction published on the morning it takes effect, say — this field
 * is already telling the truth and no screen has to be told.
 *
 * <p>Deliberately not on {@code TheSchemeAsPublished}, which the whole application reads and which
 * says so in its own javadoc: whether a version has started is a comparison against a clock, and a
 * version listed in a history would carry an answer computed at whatever moment the list was built.
 * Here there is exactly one moment and it is the one being reported on.
 */
public record TheVersionThatNowExists(

        /** The version as it now reads, every figure of it, in the units the application speaks. */
        TheSchemeAsPublished version,

        /** Whether it is already deciding anything, which is whether its Monday has arrived. */
        boolean itsDayHasCome) {
}
