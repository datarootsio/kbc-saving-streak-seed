package io.dataroots.savingstreak.web;

import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.timeline.AccountTimeline;
import io.dataroots.savingstreak.timeline.TimelineEvent;
import io.dataroots.savingstreak.timeline.TimelineEventKind;

/**
 * The year a savings account has ahead of it: the two days a bar is drawn between, and every dated
 * thing the deposits in that account have coming in between them.
 *
 * <p>The window is sent because a bar cannot be drawn without it and because working out what day it
 * is, is not the client's to do. This application's clock can be wound a year forward for a
 * demonstration, and a screen positioning markers against the machine's own date would draw a bar of
 * a year nobody is in — every marker crowded off the right-hand end while the application went on
 * behaving as though it were next March. The backend knows which year it is in, and says so.
 *
 * <p>Both days are plain dates rather than moments, as every other date on this account's resources
 * is and for the same reason: which calendar day a moment falls on depends on the zone it is read
 * in, and that zone is named once in the backend rather than guessed at by whichever machine is
 * drawing the screen.
 *
 * <p>Everything here belongs to the deposits made into <em>this</em> account — the anniversaries are
 * theirs and so are the expiries, which are the points those deposits earned. It is the one place in
 * this API where points are reported per account rather than per customer, and the reason it is
 * honest is that these are dates rather than balances: a date stays true however it is grouped,
 * while a balance repeated against every account would claim the customer held several of them. The
 * two customer-wide figures on the account's own overview are unchanged and still say what they said.
 *
 * <p>Twelve months, and that is the whole of what is coming rather than the first screenful of it:
 * nothing survives longer than its own twelve months and nothing waits longer than a year to pay, so
 * there is nothing past the right-hand edge. Whoever draws this can say so out loud.
 *
 * <p>Nothing is added up. No total of what the year costs and no net of the two kinds, for the reason
 * the ledger of money gives about its own columns: a point can be paid inside this window and expire
 * inside it too, and a net would count the one point twice in opposite directions.
 */
record SavingsAccountTimelineResponse(LocalDate from, LocalDate until, List<TimelineEventResponse> events) {

    static SavingsAccountTimelineResponse of(AccountTimeline timeline) {
        return new SavingsAccountTimelineResponse(timeline.from(), timeline.until(),
                timeline.events().stream().map(TimelineEventResponse::of).toList());
    }

    /**
     * One marker: the day, which of the two things it is, and how many points.
     *
     * <p>{@code kind} travels as a name rather than as a flag saying whether it is good news. There
     * are two dated rules today and this application keeps growing them, and a boolean would have to
     * be widened the first time a date on the bar was neither a gain nor a loss.
     *
     * <p>The day can be one already gone, and that is not an error: an anniversary falls at the
     * moment the money landed and a batch's twelve months are up at the moment it was earned, while
     * the sweeps that act on them run overnight. A date in the past here means a thing that is owed
     * and is happening tonight — the same reading the deposit history's next-anniversary date
     * already has.
     *
     * <p>Never worth nothing, so a client never has to decide whether a zero is worth drawing. An
     * anniversary that pays nothing is a fact about a deposit holding under ten euros, it is stated
     * beside that deposit in the history with the rule that explains it, and it is not a thing that
     * happens on a day.
     */
    record TimelineEventResponse(LocalDate on, TimelineEventKind kind, long points) {

        static TimelineEventResponse of(TimelineEvent event) {
            return new TimelineEventResponse(event.on(), event.kind(), event.points());
        }
    }
}
