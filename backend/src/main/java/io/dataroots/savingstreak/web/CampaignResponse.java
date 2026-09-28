package io.dataroots.savingstreak.web;

import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.challenges.ASeasonAndItsChallenges;

/**
 * One season as the listing reports it: its code, its title, the window it runs in, whether that
 * window is open now, and the challenges the bank has put in it.
 *
 * <p>Flat, with the window beside the challenges rather than nested in a season of its own, for the
 * reason {@link ChallengeResponse} is flat: this is one banner on one screen, and a page that had to
 * reach through a wrapper to find the closing date would be doing the frontend's thinking in its own
 * template. The module holds the two apart because the same season is also reported on a card, and
 * that distinction stops mattering the moment it becomes JSON.
 *
 * <p>Every season, open or not. A campaign that is over is still something a customer's card points
 * at, and a listing showing what has finished beside what is running is how somebody tells "I missed
 * it" from "there has never been one".
 */
record CampaignResponse(String code, String title, LocalDate opensOn, LocalDate closesOn,
                        boolean open, List<ChallengeInASeasonResponse> challenges) {

    static CampaignResponse of(ASeasonAndItsChallenges season) {
        return new CampaignResponse(
                season.season().code(),
                season.season().title(),
                season.season().opensOn(),
                season.season().closesOn(),
                season.season().open(),
                season.challenges().stream().map(ChallengeInASeasonResponse::of).toList());
    }
}
