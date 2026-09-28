package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * One challenge as it stands for one customer: what it is, what it asks of them, and how far through
 * it they are.
 *
 * <p>The card, in other words — the definition and the customer's place in it answered together,
 * because a page that fetched the catalogue and then a standing per challenge would be making one
 * request per row to draw one screen.
 *
 * <p><strong>Everything about the customer is optional, and the absences mean different things.</strong>
 * No enrolment at all means they have never joined: enrolling is a decision somebody makes, and a
 * card reporting a nought would be showing them progress they have not made. An enrolment that has
 * ended reports its state and no reading, because a reading is a statement about a live enrolment
 * and this module stores no progress that could be frozen at the moment somebody left. A live
 * enrolment with no next rung is one that has cleared gold, and there is genuinely nothing left to
 * ask for.
 *
 * <p><strong>The season is absent on an evergreen challenge</strong>, and that is a different
 * statement from a season with a very long window. An evergreen challenge belongs to no campaign at
 * all and is always open; a season is a thing that opens and closes, and it is carried here so that
 * the card can say when — a customer cannot tell whether they have time unless the date is on the
 * card, and a page left to work out whether the window was open would be a second answer to a
 * question the module has already answered by refusing or allowing the enrolment.
 *
 * <p><strong>The rungs carry the badges this enrolment has already won.</strong> A ladder drawn
 * with its won rungs lit is the whole point of drawing a ladder, and a page that had to cross-refer
 * the trophy case to colour one would be reading a history to answer a question about now — and
 * would get a repeatable challenge wrong, because an old round's badges are in the case and are not
 * this round's. See {@link ARungAsItStands}.
 *
 * <p>Nothing here is stored. The reading and the next rung are worked out from the deposit ledger
 * every time the card is read, so winding the development clock cannot leave either of them
 * describing a week that is now in the future. Whether the season is open is worked out on every
 * read for the same reason and against the same moment.
 */
public record AChallengeAsItStands(String code, String title, String words, ChallengeKind kind,
                                   boolean repeatable, List<ARungAsItStands> rungs,
                                   Optional<ASeason> season, Optional<AnEnrolment> enrolment,
                                   Optional<BigDecimal> reading, Optional<TheNextRungUp> nextRung) {

    /** Whether the customer is in it now, which is the one thing the enrol-or-leave control needs. */
    public boolean enrolled() {
        return enrolment.map(taken -> taken.state().isLive()).orElse(false);
    }
}
