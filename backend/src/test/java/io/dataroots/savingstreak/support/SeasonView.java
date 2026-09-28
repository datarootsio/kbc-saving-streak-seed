package io.dataroots.savingstreak.support;

import java.time.LocalDate;

/**
 * The season a challenge belongs to, as the card reports it: which season, the window it runs in,
 * and whether that window is open as the application's clock now reads.
 *
 * <p>Null on an evergreen challenge, which belongs to no season and is always open — and that
 * absence is the claim a test about evergreen challenges makes directly, rather than by reading a
 * window stretched to the end of time.
 *
 * <p>{@code open} is answered by the application rather than worked out here from the two dates,
 * because whether to-day is inside a window is a question about the zone the application counts its
 * days in, and a test that recomputed it would be asserting its own arithmetic against itself.
 */
public record SeasonView(String code, String title, LocalDate opensOn, LocalDate closesOn,
                         boolean open) {
}
