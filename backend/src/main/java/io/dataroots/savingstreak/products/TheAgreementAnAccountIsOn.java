package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What one savings account is living under, as the rest of the application reads it: the product,
 * the version of its terms the account was opened with, the day it was opened on them, and the one
 * condition that product attaches.
 *
 * <p><strong>This is the sentence the feature exists to make sayable.</strong>
 * {@link AProductOnOffer} says what a product is selling <em>today</em>; this says what an account
 * is living under, and the two are deliberately different records because they are deliberately
 * different answers. Free savings has published a second version at a lower rate and every account
 * opened before it carries on under the first: a page that read the offer and called it "your
 * account" would be repeating the exact confusion this module was built to remove, and a single
 * record used for both would let it do so by accident.
 *
 * <p><strong>The condition, and only the one the product actually attaches.</strong> An instant
 * access account has none, a notice account has a number of days, a minimum balance account has a
 * floor in euros and a fixed term has a day it matures on. All four are carried here, all four read
 * as the absence of the rule when the product does not have it — zero days, a floor of nothing, no
 * maturity date — because a page drawing an agreement has to render "nothing to keep, nothing to
 * give notice of" for three products out of four, and one reading for every absence is the only
 * version of that a reader can hold in their head. The one exception is
 * {@link #maturesOn}, which is null rather than a date, because there is no date that means "this
 * never matures" and inventing one would have a screen printing it.
 *
 * <p><strong>{@link #maturesOn} is derived from the day the account was opened and the term the
 * version names</strong>, rather than stored. A second copy would be a figure that could disagree
 * with the two it was worked out from, and it is a subtraction away from both of them. What
 * <em>happens</em> on that day — rolled over, moved to instant access, or held — is a later
 * ticket's business and is not promised here; this is the date, and nothing in this application
 * acts on it yet.
 *
 * <p><strong>And the day it ended, which is null for the overwhelming majority of accounts.</strong>
 * An account that has been emptied and closed keeps everything else on this reading — its product,
 * its version, the day it began — because all of that is still what the money in its history lived
 * under. {@link #closedOn} is the one field that says the account is a record rather than somewhere
 * to put money, and it is a date rather than a flag so that a panel can say when without a second
 * field to read it from.
 *
 * <p><strong>There is no rate here, and that is on purpose.</strong> This ticket records which
 * agreement an account is on; it does not pay a cent of interest, and a rate quoted on this reading
 * would be a promise the application cannot yet keep. The version is the address of the rate, and
 * the version's own reading — {@link ASetOfTerms}, through the catalogue — is where it can be
 * looked up by anybody who wants it.
 */
public record TheAgreementAnAccountIsOn(

        /** The savings account this is about. */
        long savingsAccountId,

        /** The product it is on, by the code that never changes. */
        String productCode,

        /** What that product is called, so a page can name it without knowing the catalogue. */
        String productName,

        /** Which of the four shapes of agreement it is. */
        ProductKind kind,

        /** Which version of that product's terms it was opened under, counting from one. */
        int version,

        /** The day this agreement began, which is not the day the version took effect. */
        LocalDate openedOn,

        /** Days of warning before money may leave, and zero when the product asks for none. */
        int noticeDays,

        /** The floor to keep for the bonus, in euros, and zero when there is no floor. */
        BigDecimal minimumBalance,

        /** The day the term is up, and null when the account is not on a term at all. */
        LocalDate maturesOn,

        /** The day the account was closed, and null while it is still open — which is most of them. */
        LocalDate closedOn) {

    /** Whether the account has been closed, so that no caller has to decide what a null date means. */
    public boolean isClosed() {
        return closedOn != null;
    }
}
