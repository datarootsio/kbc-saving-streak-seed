package io.dataroots.savingstreak.challenges;

import java.time.LocalDate;

/**
 * A season as the rest of the application reads it: which campaign, the window it runs in, and
 * whether that window is open as of the moment it was asked about.
 *
 * <p><strong>{@code open} travels with the dates rather than being left to whoever draws the
 * screen.</strong> Whether to-day is inside a window sounds like two comparisons anybody could
 * make, and it is not: it depends on which zone the day is read in and on both ends of the window
 * being inclusive, and a page that worked it out for itself would disagree with the module that
 * refuses the enrolment — showing a customer an open season and then refusing them for joining it.
 * There is one answer to "is it open", it is {@link Campaign}'s, and this is how it leaves.
 *
 * <p>Nothing about which challenges are in it, because this is the season a <em>challenge</em>
 * reports belonging to and the challenge already knows what it is.
 * {@link ASeasonAndItsChallenges} is the other reading, the bank's own listing, and it is the one
 * that names what is inside.
 */
public record ASeason(String code, String title, LocalDate opensOn, LocalDate closesOn,
                      boolean open) {
}
