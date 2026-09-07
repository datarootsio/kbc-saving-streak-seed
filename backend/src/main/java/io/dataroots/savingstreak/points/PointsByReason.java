package io.dataroots.savingstreak.points;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Points that were earned, split by the reason each of them was earned under, with the total the
 * sum of the split.
 *
 * <p>A breakdown rather than a number, because a total on its own cannot be added to. Base accrual
 * is the only reason there is today, so every one of these carries a single entry — and that is
 * exactly why the shape is settled now: the slice that earns points a second way credits another
 * reason into the same breakdown, and no caller has to be taught that the figure it already had has
 * quietly come to mean something narrower than it did.
 *
 * <p>A reason with no points is not the same as a reason that is absent, and both are kept as they
 * are given: a deposit whose euros floor away has earned nothing under base accrual, which is a
 * different statement from having earned nothing at all.
 */
public record PointsByReason(Map<PointsReason, Long> points) {

    public PointsByReason {
        // Copied, and in the enumeration's own order, so that a breakdown handed out cannot be
        // changed underneath whoever is holding it and reads the same way in every log line.
        // An EnumMap cannot be built from an empty ordinary map, so nothing is its own case.
        points = points.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(points));
    }

    /** Points earned for one reason, which is all a single credit ever is. */
    public static PointsByReason of(PointsReason reason, long points) {
        return new PointsByReason(Map.of(reason, points));
    }

    /** No points under any reason — what something this ledger has no record of earned. */
    public static PointsByReason nothing() {
        return new PointsByReason(Map.of());
    }

    /** Everything that was earned, however it was earned. */
    public long total() {
        return points.values().stream().mapToLong(Long::longValue).sum();
    }

    /** What was earned for this reason alone, and zero for a reason nothing was earned under. */
    public long earnedAs(PointsReason reason) {
        return points.getOrDefault(reason, 0L);
    }
}
