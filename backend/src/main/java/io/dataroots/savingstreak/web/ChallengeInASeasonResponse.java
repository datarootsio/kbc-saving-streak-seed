package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.challenges.AChallengeInASeason;

/**
 * One challenge named in the seasons listing: its code and the words at the top of its card.
 *
 * <p>Deliberately not a {@link ChallengeResponse}. That is a card, and a card is the challenge and
 * somebody's place in it answered together; the seasons listing names no customer, so every field
 * about a standing would be null and the shape would be one that could only ever be half filled in.
 * A page wanting more about one of these asks for the customer's own tab, where the reading lives.
 */
record ChallengeInASeasonResponse(String code, String title) {

    static ChallengeInASeasonResponse of(AChallengeInASeason challenge) {
        return new ChallengeInASeasonResponse(challenge.code(), challenge.title());
    }
}
