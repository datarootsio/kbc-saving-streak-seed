package io.dataroots.savingstreak.support;

import java.time.LocalDate;
import java.util.List;

/**
 * One season as the seasons listing reports it: its code, its title, the window it runs in, whether
 * that window is open now, and the challenges that belong to it.
 *
 * <p>The one read in this feature that names no customer. A season is the bank's campaign rather
 * than anybody's progress, so a test asserting on it is asserting what the bank is running — which
 * challenges are in it, and until when — rather than how far through it anybody has got.
 */
public record CampaignView(String code, String title, LocalDate opensOn, LocalDate closesOn,
                           boolean open, List<ChallengeInASeasonView> challenges) {
}
