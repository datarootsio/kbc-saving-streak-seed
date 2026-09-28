package io.dataroots.savingstreak.web;

import java.time.LocalDate;

import io.dataroots.savingstreak.challenges.ASeason;

/**
 * The season a challenge belongs to, as a page draws it: which campaign, the window it runs in, and
 * whether that window is open now.
 *
 * <p>Nested rather than five more fields on the challenge, which is the one place this API's
 * flatness is worth breaking. A season is a thing several cards share, and a page that has to
 * decide whether to draw a banner at all is asking one question — is there a season here — which a
 * null object answers and five parallel nulls do not.
 *
 * <p>{@code open} comes from the application rather than being left to the page to work out from the
 * two dates. Whether to-day is inside a window depends on the zone the application counts its days
 * in and on both ends of the window being inclusive, and a page that decided for itself would
 * eventually show an enrol button the API refuses.
 *
 * <p>The dates are days and travel as days. A season is announced on a poster — "until the 31st of
 * March" — and sending it as an instant would make a page render the bank's campaign in the reader's
 * own timezone and put it a day out for anybody east of here.
 */
record SeasonResponse(String code, String title, LocalDate opensOn, LocalDate closesOn,
                      boolean open) {

    static SeasonResponse of(ASeason season) {
        return new SeasonResponse(season.code(), season.title(), season.opensOn(),
                season.closesOn(), season.open());
    }
}
