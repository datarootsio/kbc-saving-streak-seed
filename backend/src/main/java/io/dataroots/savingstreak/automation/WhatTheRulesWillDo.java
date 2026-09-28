package io.dataroots.savingstreak.automation;

import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.accounts.ABillToCome;

/**
 * What every rule standing on one savings account will do over the coming twelve months, merged into
 * one list in date order.
 *
 * <p>Merged rather than grouped by rule, because "what will my rules do?" is a question about a
 * calendar and not about a list of instructions: a customer wants to read down the year and see the
 * mornings money leaves their current account, whichever rule asked for it. Which rule it was is on
 * each line.
 *
 * <p>In the order the night would fire them — by the day, and then by the order the customer wrote
 * the rules — so that two rules falling on one morning appear in the order their money will actually
 * move.
 *
 * <p><strong>{@code from} and {@code until} are the window, said out loud rather than left to be
 * inferred from the first and last lines.</strong> An account whose rules do nothing at all for a
 * fortnight would otherwise leave a page unable to say whether it is showing an empty start or a
 * short window, and an account with no rules at all would answer with nothing and no way to say how
 * far "nothing" reaches. They are {@code TimelineHorizon}'s two days, quoted rather than restated,
 * so that the bar and this list look equally far ahead.
 *
 * <p><strong>Derived on every read and stored nowhere.</strong> A stored projection goes stale the
 * moment a balance, a rule or a goal changes, and then needs invalidation rules that are themselves
 * a source of bugs — the argument {@code AReallocationWorthSuggesting} makes at length and this
 * makes again. Change anything and the next read says something else, with nothing to invalidate.
 *
 * <p><strong>A line may be dated before {@code from}, and it means the next run owes that
 * transfer.</strong> Every rule is forecast from its own cursor rather than from today, so a rule
 * the nightly run has not caught up with yet carries the mornings it is late for at the head of the
 * list. That is the ordinary state of every rule on a wound clock — the clock moves in whole days
 * and the cron never fires for the ones it skipped — and it is the only honest answer to "what will
 * my rules do", because those transfers are the next thing the rules do. {@code from} and
 * {@code until} still name the twelve months this looked <em>forward</em> over, which is the window
 * the bar looks over and not a summary of the lines.
 *
 * <p>A paused rule contributes nothing, because nothing falls due while a rule is paused and a pause
 * has no end date for this to guess at; an ended rule is absent, because it is a record rather than
 * an instruction. <strong>Neither does a morning that fell inside a pause somebody has since resumed
 * from</strong>, which is the same sentence read across a closed window: the days inside it are
 * inside the range a resumed rule is forecast over, because a resume deliberately leaves the cursor
 * where it was, and they are taken out by the night's own pause filter rather than by a second one.
 * All of those are the same answer the nightly run would give, which is the promise this whole
 * record is under.
 *
 * <p><strong>{@code bills} is what the same twelve months has going the other way</strong>, and it
 * is the whole of what turns this list from decoration into decision support. A sweep is a claim on
 * a current account, and so is the rent; a customer reading down the year to decide how much to save
 * has to see the two in one place, or they are choosing a figure against a balance they only think
 * they have. It is every standing bill on every current account the holder of this savings account
 * holds, on the dates the shared calendar says they fall on — asked of the Accounts module, which
 * owns bills, and derived on every read for the same reason the occurrences are.
 *
 * <p>A second list rather than a single merged one, because the two are different sentences: an
 * occurrence carries a trigger, a figure that may be an illustration and a split across goals, and a
 * bill carries a name and a figure that is never anything but exact. Merging them into one array
 * would mean a row with half its fields null and a page inferring which kind it was holding. Both
 * are in date order over the same window, which is all a page needs to interleave them — and that is
 * not re-deriving a boundary against the browser's clock, because every day in both lists and the
 * owed/still-to-come reading of each line were decided here.
 *
 * <p>A rule that fires on payday is forecast out of two sources with today as the seam: the days it
 * is already owed come from the record of salaries actually credited, exactly as the run reads them,
 * and only the days still ahead come from the income its holder has declared. A month already past
 * with no salary in it is a month the run passes over, and a forecast that derived it from the
 * declared day-of-the-month instead would promise a transfer on a morning nobody was paid.
 */
public record WhatTheRulesWillDo(LocalDate from, LocalDate until,
                                 List<AnOccurrenceToCome> occurrences, List<ABillToCome> bills) {
}
