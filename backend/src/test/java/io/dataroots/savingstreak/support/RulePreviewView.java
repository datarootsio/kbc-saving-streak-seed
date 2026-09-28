package io.dataroots.savingstreak.support;

import java.time.LocalDate;
import java.util.List;

/**
 * What every rule standing on one savings account will do over the coming twelve months, as the API
 * reports it — which is exactly as a test reads it.
 *
 * <p>{@code from} and {@code until} are read rather than worked out, because a test that added
 * twelve months to today for itself would be asserting its own arithmetic and would pass against an
 * application whose window was some other length entirely.
 *
 * <p>{@code bills} is the same twelve months going the other way: every date a standing bill on the
 * holder's current accounts falls due on. Two lists rather than one because the API sends two, and a
 * test asserting that a rent stands beside a sweep in one year reads both.
 */
public record RulePreviewView(LocalDate from, LocalDate until,
                              List<OccurrenceToComeView> occurrences,
                              List<BillToComeView> bills) {
}
