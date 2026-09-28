package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What a current account has to cover between today and this day next month, as the API reports it —
 * which is exactly as a test reads it.
 *
 * <p>{@code from} and {@code until} are read rather than worked out, for the reason
 * {@link RulePreviewView}'s are: a test that added a month to today for itself would be asserting
 * its own arithmetic and would pass against an application whose window was some other length.
 *
 * <p>The four figures are read together because the claim about them is a claim about all four at
 * once: {@code leavesYou} is {@code balance + incomeDue - billsDue}, and a test reading one of them
 * could not say so.
 *
 * <p>{@code arrearsOutstanding} is inside {@code billsDue} rather than beside it. It is named
 * separately so that a test can say what a month has to cover counts what is already owed, which is
 * the claim, rather than inferring it from a total.
 */
public record MonthAheadView(LocalDate from, LocalDate until, BigDecimal balance,
                             BigDecimal incomeDue, BigDecimal billsDue,
                             BigDecimal arrearsOutstanding, BigDecimal leavesYou) {
}
