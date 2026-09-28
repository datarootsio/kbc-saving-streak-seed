package io.dataroots.savingstreak.simulation;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The fields a customer can fill in about a change, before any kind of change has made sense of
 * them: an amount, a day, a second day, and a goal.
 *
 * <p><strong>Four nullable fields rather than four shapes, and only at this one seam.</strong> The
 * whole of the rest of this module knows changes as {@link AnAdjustment}s, each carrying exactly
 * what its own kind needs. But a request arrives as a form, and a form has the same boxes on it
 * whichever kind is ticked; something has to carry what was typed from the web layer to the kind
 * that can read it, and a shape per kind out here would mean the web layer choosing between four
 * shapes, which is the switch this design exists to avoid. So the form is one record, each kind
 * reads the boxes it needs, and an objection to what is in them is that kind's own sentence.
 *
 * <p><strong>The four boxes are the union of what the spec's four kinds carry, and they are
 * complete.</strong> Another amount every week is an {@code amount} and an {@code on}; stopping for
 * a while is an {@code on} and an {@code until}; taking money out is an {@code amount} and an
 * {@code on}; moving a deadline is a {@code goalId} and an {@code on}. That is deliberate and it is
 * worth saying out loud: the three slices that add the last three kinds add no field here, so none of
 * them is editing a line another of them is editing.
 *
 * <p>Read rather than parsed. Text that is not a number and text that is not a day are answered in
 * the web layer, in the words that layer already uses for the same mistake on a goal's target and a
 * goal's deadline, because "2500,00" is a question about the characters somebody typed. What arrives
 * here is a figure and a day, and whether they are an amount of money and a day inside the window is
 * the kind's own answer.
 */
public record AnAdjustmentAsAsked(BigDecimal amount, LocalDate on, LocalDate until, Long goalId) {
}
