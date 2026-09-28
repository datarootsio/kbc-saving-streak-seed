package io.dataroots.savingstreak.support;

/**
 * One challenge named in the seasons listing: its code and the words at the top of its card.
 *
 * <p>Nothing about anybody's progress, because the listing belongs to no customer — it is the
 * bank's own answer to "which seasons are running and what is in them", and where somebody stands
 * in one of them is read off their own challenges tab.
 */
public record ChallengeInASeasonView(String code, String title) {
}
