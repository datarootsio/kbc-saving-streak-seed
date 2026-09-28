package io.dataroots.savingstreak.schemepreview;

/**
 * One figure of the scheme, as the version in force has it and as the candidate would have it.
 *
 * <p><strong>Both readings are text, and that is a decision rather than laziness.</strong> The
 * figures the scheme carries are three different kinds of thing — amounts of money, multiples,
 * percentages, counts of months and days, and a whole ladder of six euro amounts — and the answer
 * to "what did I change" is one table with one column per side. A record typed to hold all of them
 * would need a field per kind and a screen would need a renderer per kind to draw one table; a
 * record holding the widest of them would report a ladder as a number. Nothing computes on these:
 * they are read by a person checking that they changed what they meant to change.
 *
 * <p><strong>Nothing is lost by it, because the typed figures are in the same response.</strong>
 * The version in force and the version this would become both travel whole and in their own units,
 * so anything that wants to do arithmetic has both. This is the difference, spelled out, and a
 * difference is something you read.
 *
 * <p><strong>{@code itWouldChange} is here rather than left to a string comparison on the
 * screen.</strong> The two sides are formatted by this application and a page comparing them would
 * be comparing this application's formatting, which is a thing that can change under it. It is also
 * the field the whole "a preview of the version in force reports that nothing changes" claim is
 * read off.
 *
 * @param figure       the name the publishing form uses for it, so the diff and the form agree
 * @param asItReadsNow what the version in force says
 * @param asItWouldRead what the candidate says
 * @param itWouldChange whether those two are different
 */
public record AFigureAsItWouldRead(String figure, String asItReadsNow, String asItWouldRead,
                                   boolean itWouldChange) {

    /**
     * The two readings of one figure, with the comparison made here rather than by whoever builds
     * one.
     *
     * <p>Compared as the text both sides were rendered into, which is the only comparison that
     * matches what the field claims: two amounts that differ only in trailing zeros are the same
     * amount of money, and both come out of the same formatter, so they arrive here spelled the
     * same way. Comparing them as {@code BigDecimal} would have needed the figures untyped again
     * two lines after this record argued its way out of that.
     */
    static AFigureAsItWouldRead of(String figure, String asItReadsNow, String asItWouldRead) {
        return new AFigureAsItWouldRead(figure, asItReadsNow, asItWouldRead,
                !asItReadsNow.equals(asItWouldRead));
    }
}
