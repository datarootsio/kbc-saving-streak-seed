package io.dataroots.savingstreak.challenges;

import java.util.List;

/**
 * One season and what the bank has put in it — the listing, which is the one read in this module
 * that belongs to nobody.
 *
 * <p>Every other answer here is somebody's: their card, their enrolment, their trophy case. What
 * seasons are running and until when is the same answer for everybody, in the way the rewards
 * catalogue is, and a customer who has joined nothing still needs to be able to see that there is a
 * campaign on and how long is left of it.
 *
 * <p>The season and its contents rather than one flat record, because they are answers to two
 * different questions and only one of them is a season: the window is what
 * {@link AChallengeAsItStands} also carries, and holding it as the same {@link ASeason} in both
 * places is what stops the two from drifting into disagreeing about what open means.
 */
public record ASeasonAndItsChallenges(ASeason season, List<AChallengeInASeason> challenges) {
}
