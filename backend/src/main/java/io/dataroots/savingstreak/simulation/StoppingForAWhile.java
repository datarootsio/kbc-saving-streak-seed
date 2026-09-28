package io.dataroots.savingstreak.simulation;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A first day and a last day, between which nothing at all is paid into savings and every standing
 * saving rule is treated as paused.
 *
 * <p><strong>The interesting part is not the balance that fails to grow.</strong> A customer can do
 * that half in their head: two months of a fifty-a-week rule is four hundred euros that are not
 * there. What they cannot do in their head is the run. Every week inside the stop takes in nothing,
 * so the first Sunday inside it fails whatever that week asked for — the threshold published for
 * that week's own Monday, off the scheme's history on the snapshot — the run ends rather
 * than shortening, and {@code StreakMultiplier} drops back to the ordinary rate for every euro paid
 * in afterwards — which has to be climbed a second time, a week at a time, before the branch is
 * earning what it was earning the morning before the stop began. The months <em>after</em> the stop
 * are where that shows, and they are the reason this branch is worth drawing over a year rather than
 * over the two months it is about.
 *
 * <p><strong>The silence is the silence {@code RulePause} already produces, and it is that silence
 * deliberately.</strong> A rule whose occurrence falls inside a pause is excluded from the run's
 * range outright rather than recorded and skipped, and the cursor moves over it, so a pause is never
 * made up afterwards — a fortnight paused is a fortnight in which nothing happened, and the day the
 * customer resumes there is no arrears of four Mondays waiting for them. This does exactly that, at
 * the one place in {@link TheNightReplayed} where the rules fire, and it is why a stop asked about
 * here and a real pause a customer went and applied answer with the same year. The alternative —
 * letting the occurrences pile up behind the cursor and fire on the morning the stop lifts — would
 * have been the fold inventing a kind of pause this application does not have.
 *
 * <p><strong>A pause of the saving rather than a pause of the life.</strong> The salary still lands,
 * the bills are still taken, batches still reach their twelve months and go, and anniversaries still
 * pay their tenth on the deposits the customer already holds. A customer who stops saving for two
 * months has not stopped being paid or stopped owing rent, and a branch that froze all six steps of
 * the night would be answering a question about a coma. It costs nothing to arrange: those are
 * separate steps of {@link TheNightReplayed#theNightOf} and this kind is simply not asked about them.
 *
 * <p><strong>It stops what the customer would put away themselves as well as what the rules
 * move</strong>, and that is the whole of how it composes with another change. A scenario carrying a
 * stop and another twenty-five a week is a customer who intends both, and the honest reading is that
 * the twenty-five does not land on the mornings they said they were stopping — a fold that paid it in
 * anyway would show a stop that never broke a single week, which is the opposite of the answer they
 * came for. Every adjustment on a branch is asked every question, so this holds however many changes
 * are in the scenario and whichever order they were typed in.
 *
 * <p><strong>It says nothing to the weekly plan the goals are funded out of, and that is knowingly
 * approximate.</strong> A branch's goals are projected from the capacity its holder declared rather
 * than allocated out of the euros the walk pays in — the reading {@link TheNightReplayed} argues at
 * length — and a declared capacity is a customer's sentence about what they can put away in an
 * ordinary week, which two stopped months in the middle of a year does not change. Taking a share of
 * it away for the stop would be this fold inventing a figure nobody declared, and it would push every
 * goal's Monday out by an arithmetic the goals screen has never used. The balance, the points and the
 * run carry the cost of the stop; every figure on the answer already says it is an illustration
 * rather than a promise.
 *
 * <p>Both days may be missing, and the last may be before the first: a change is built out of
 * whatever a customer typed and refused by {@link #whyItCannotBeAsked} in one sentence before a
 * single day of any branch is folded, so nothing below is ever asked of a stop that has not been
 * found askable.
 *
 * @param firstDay the first morning nothing is paid in on, included
 * @param lastDay  the last morning nothing is paid in on, included — so a stop whose two days are
 *                 the same day is one morning of silence rather than none
 */
public record StoppingForAWhile(LocalDate firstDay, LocalDate lastDay) implements AnAdjustment {

    @Override
    public AKindOfAdjustment kind() {
        return AKindOfAdjustment.STOP_FOR_A_WHILE;
    }

    @Override
    public String asAsked() {
        return "STOP_FOR_A_WHILE from=" + (firstDay == null ? "not said" : firstDay)
                + " until=" + (lastDay == null ? "not said" : lastDay);
    }

    /**
     * Why stopping for a while cannot be asked about: one of the two days is not a day inside the
     * year this simulation is drawn over, or the stop is asked to end before it begins.
     *
     * <p>The days first and the pair afterwards, because an objection to what is in one box is the
     * one a customer can act on without reading the other: told their last day is in 2031 they type
     * a different last day, where told their stop ends before it begins they have to work out which
     * of the two the application means. The first two sentences are
     * {@link AnAdjustment#whyThatDayIsOutsideTheWindow}'s rather than a third and fourth wording of
     * one objection, and they name the window for the reason every refusal in this application names
     * its figures.
     *
     * <p>The third is this kind's own and belongs to no other: a stop is the only change in this
     * application made of two days that have to be in an order. It names both of them back, because
     * a customer who has typed two dates into two boxes and been told they are the wrong way round
     * has otherwise to guess which box the application read as which.
     */
    @Override
    public Optional<String> whyItCannotBeAsked(TheStartingPoint standing) {
        Optional<String> firstOutside = AnAdjustment.whyThatDayIsOutsideTheWindow(
                "The day a stop begins", firstDay, standing);
        if (firstOutside.isPresent()) {
            return firstOutside;
        }
        Optional<String> lastOutside = AnAdjustment.whyThatDayIsOutsideTheWindow(
                "The day a stop ends", lastDay, standing);
        if (lastOutside.isPresent()) {
            return lastOutside;
        }
        if (lastDay.isBefore(firstDay)) {
            return Optional.of("A stop has to end on or after the day it begins, and " + lastDay
                    + " is before " + firstDay + ".");
        }
        return Optional.empty();
    }

    /**
     * True on every morning from the first day to the last, both of them included, and false on
     * every other morning of the year.
     *
     * <p>Both ends included because a customer who says "from the first of March to the thirtieth of
     * April" has named two days they are stopping on, not a day they stop on and a day they resume
     * on. The day after the last is the morning the rules fire again and the morning a weekly extra
     * lands again, and neither needs saying here.
     */
    @Override
    public boolean itStopsTheSavingOn(LocalDate day) {
        return !day.isBefore(firstDay) && !day.isAfter(lastDay);
    }

    /**
     * Adopting a branch that stops for a while stands nothing down, and says so: pausing is what
     * {@code RulePause} already is, and it is the customer's to carry out.
     *
     * <p><strong>The durable counterpart exists, and that is exactly why this is not adopted.</strong>
     * A rule can be paused, on the automatic saving screen, one rule at a time and with the days on
     * it. A stop inside a branch is a different shape: it silences <em>every</em> rule standing on
     * the account for a run of days, because that is what "what if I stopped for two months" means
     * to somebody folding a year. Turning that into presses would mean this module deciding to pause
     * each of a customer's rules, which of them to leave alone, and what to do about the ones they
     * add or end while the stop is running — four decisions the branch never made and that the
     * customer would discover afterwards on a screen they were not looking at.
     *
     * <p>So the answer names the thing rather than doing it, and names both days, because a pause is
     * entered per rule with its own two days and the customer is about to type them.
     *
     * <p>A stop is also the one change in a branch that a customer can simply <em>do</em>, by not
     * doing anything. The rules that move money are theirs to pause; the money they were going to
     * put in by hand is theirs to keep. Saying so is more honest than a press that silences an
     * account's automation off the back of a question.
     */
    @Override
    public AChangeThePlanNowCarries adoptedThrough(ThePressesACustomerWouldHaveMade presses) {
        return AChangeThePlanNowCarries.yoursToCarryOut(AKindOfAdjustment.STOP_FOR_A_WHILE,
                "Stopping from " + (firstDay == null ? "the day you choose" : firstDay) + " until "
                        + (lastDay == null ? "the day you choose" : lastDay)
                        + " is yours to carry out. No rule has been stood down: pause the rules you "
                        + "want quiet for those days on the automatic saving screen, and they will "
                        + "start themselves again afterwards.");
    }
}
