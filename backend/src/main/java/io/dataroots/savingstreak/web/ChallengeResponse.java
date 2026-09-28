package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.challenges.AChallengeAsItStands;
import io.dataroots.savingstreak.challenges.AnEnrolment;
import io.dataroots.savingstreak.challenges.TheNextRungUp;

/**
 * One challenge on a customer's tab: what it is, what it asks of them, and where they stand in it.
 *
 * <p>Flat rather than a card with a standing nested inside it. Everything here is one row on one
 * screen — the title, the ladder, the bar and the words under the bar — and a page that had to
 * reach through an optional to find out whether to draw the bar would be doing the frontend's
 * thinking in its own template.
 *
 * <p><strong>Null means something different in each place, and each one is deliberate.</strong> A
 * null {@code state} is a challenge nobody has joined: the words and the ladder are there to read,
 * and there is nothing about this customer to say. A {@code state} with a null {@code reading} is an
 * enrolment that is over — the tab says they left it rather than pretending they were never in it,
 * and there is no reading because progress is derived rather than stored and there was never a
 * figure to freeze. A null {@code nextRung} on a live enrolment is gold already cleared, and a bar
 * that could never fill would read as unfinished work. A null {@code season} is an evergreen
 * challenge, which belongs to no campaign and is always open — not a season with a very long window,
 * and a page drawing a countdown over one would be inventing an urgency the bank never claimed.
 *
 * <p>The season is the one nested thing here, and it earns it: it is shared by several cards, and
 * "is there a banner to draw" is one question a null object answers and five parallel nulls do not.
 *
 * <p>Each rung carries the moment this customer won it, so the ladder on the card can be drawn lit
 * without the page cross-referring the trophy case — which would answer the wrong question anyway,
 * because a badge from an earlier round of a repeatable challenge is history rather than progress.
 * See {@link ChallengeRungResponse}.
 *
 * <p>The words are the backend's, so a page renders a challenge it has never heard of.
 */
record ChallengeResponse(String code, String title, String words, String kind, boolean repeatable,
                         List<ChallengeRungResponse> rungs, boolean enrolled, String state,
                         BigDecimal measuringFrom, BigDecimal reading, String nextRung,
                         BigDecimal stillNeeded, SeasonResponse season) {

    static ChallengeResponse of(AChallengeAsItStands challenge) {
        return new ChallengeResponse(
                challenge.code(),
                challenge.title(),
                challenge.words(),
                challenge.kind().name(),
                challenge.repeatable(),
                challenge.rungs().stream().map(ChallengeRungResponse::of).toList(),
                challenge.enrolled(),
                challenge.enrolment().map(taken -> taken.state().name()).orElse(null),
                challenge.enrolment().map(AnEnrolment::measuringFrom).orElse(null),
                challenge.reading().orElse(null),
                challenge.nextRung().map(next -> next.rung().rung().name()).orElse(null),
                challenge.nextRung().map(TheNextRungUp::stillNeeded).orElse(null),
                challenge.season().map(SeasonResponse::of).orElse(null));
    }
}
