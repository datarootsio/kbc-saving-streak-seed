package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;

/**
 * What one goal actually received out of one firing of a rule, in the order the split offered it.
 *
 * <p>What it <em>received</em> and not what it was offered, which is the whole reason this is
 * recorded rather than worked back out of the rule: a goal that was nearly complete takes part of
 * its share and the rest spills to the next goal in the split, and a reader redoing the percentages
 * against the rule as it reads today would get a different answer from the one the money took.
 *
 * <p>A goal that took nothing is not in the list at all. Nothing moved, no row was written in the
 * goals ledger, and an entry of 0.00 would be this record claiming a movement that never happened.
 * What the split could not place is the occurrence's {@code leftUnallocated}, which is one figure
 * for all of it.
 *
 * <p>Public, unlike the row it is read from, because {@link RecordedOccurrence} carries it out of
 * the module.
 */
public record WhatAGoalGot(long goalId, BigDecimal amount) {
}
