package io.dataroots.savingstreak.products;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The day notice given on one day comes free, and how long is left of it — the whole of what
 * "ready" means in this application, in two subtractions and no stored state.
 *
 * <p><strong>Derived at the moment somebody asks, against the injected clock.</strong> That is the
 * decision the spec argues at length and this class is where it is kept: the day the notice was
 * given is a fact, the product's notice days are a fact, and readiness is what those two say when
 * read together today. A column saying "ready" would be a third thing to keep truthful, and the
 * nightly job that set it would be a sweep to answer a subtraction — the same objection this
 * application already makes to sweeping a points balance, a goal's status and an offer's window.
 *
 * <p><strong>Calendar days and not working days.</strong> "Thirty-two days" on a card a customer
 * reads means thirty-two days on a calendar they own, and a rule about bank holidays would be a
 * rule this training application would then have to be right about in two countries.
 *
 * <p><strong>Counted from the day, never from the moment.</strong> Notice given at five to midnight
 * has run the same thirty-two days as notice given at nine in the morning, which is what a bank
 * does and what a customer counting on their fingers expects. The zone the day was read in is the
 * one zone this application counts its calendars in, and it is read once, by the service, rather
 * than here.
 *
 * <p>A class of static methods with no state, like {@code TheTermsOnOfferToday} beside it, so that
 * the arithmetic can be read on its own and cannot pick up a second source of "today".
 *
 * <p><strong>Public, and widened on purpose rather than by drift.</strong>
 * The what-if simulator replays a year of nights and has to say whether a notice given
 * before the window opened has finished running on a day inside it, which is this class's question
 * asked of a day that is not today.
 * The reason it is safe is the reason {@code LoyaltyRate} gives for the same move: there is nothing
 * of this module's machinery in it — no repository, no entity, no clock, no state, just a rule
 * about numbers. A module that calls it is not reading Products; it is quoting a rule Products
 * wrote down, which is the only way a projection and a sweep can be kept from disagreeing.
 */
public final class WhenANoticeIsReady {

    private WhenANoticeIsReady() {
    }

    /**
     * The first day money given notice of on {@code givenOn} may be taken.
     *
     * <p>Notice days added to the day it was given, so notice of thirty-two days given on the first
     * of a month is ready on the second of the next one — and notice of no days at all is ready the
     * moment it is given, which is why an account with nothing to give notice of is never asked
     * about at all.
     */
    public static LocalDate readyOn(LocalDate givenOn, int noticeDays) {
        return givenOn.plusDays(noticeDays);
    }

    /**
     * How many days are still to run, and none at all once the day has come.
     *
     * <p>Never negative. A notice that ran its course a fortnight ago has nothing left to run, not
     * minus fourteen days: a ready notice does not lapse, so how long ago it became ready is not a
     * figure anything in this application decides from, and a negative number on a screen would
     * invite somebody to write a rule about it.
     *
     * <p>Zero on the morning it is ready rather than on the morning after. The day the notice is
     * ready is a day the money can be taken, which is the promise the sentence on the refusal makes
     * when it says the date.
     */
    public static int daysLeftOn(LocalDate givenOn, int noticeDays, LocalDate today) {
        long left = ChronoUnit.DAYS.between(today, readyOn(givenOn, noticeDays));
        return left <= 0 ? 0 : Math.toIntExact(left);
    }

    /** Whether the wait is over, which is the same subtraction read as a yes or a no. */
    public static boolean readyOn(LocalDate givenOn, int noticeDays, LocalDate today) {
        return daysLeftOn(givenOn, noticeDays, today) == 0;
    }

    /**
     * A number of days as a customer would say it, so that a sentence built out of one never reads
     * "1 days".
     *
     * <p>Here rather than on a screen, because the sentence it goes into is the domain's own and
     * travels into a problem detail untouched — a page that fixed the plural afterwards would be
     * editing words the backend chose.
     */
    public static String daysInWords(int days) {
        return days + (days == 1 ? " day" : " days");
    }
}
