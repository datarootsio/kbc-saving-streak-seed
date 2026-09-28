package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.scheme.TheVersionThatNowExists;

/**
 * What comes back from publishing a version of the scheme: the version that now exists, and whether
 * its day has come.
 *
 * <p><strong>The version is nested as the same {@link SchemeResponse} every other read serves,
 * rather than flattened into thirteen fields here.</strong> A version of the scheme has one shape
 * in this API — the reading in force, the history, and the receipt for a publish are the same
 * thirteen figures — and a second flat copy of it would be a second thing to keep in agreement with
 * the first the day somebody adds a figure to the scheme. The screen that publishes is the screen
 * that pre-fills its form from the version in force, so it already has a renderer for exactly this.
 *
 * <p><strong>The boolean beside it is the half a page cannot work out for itself safely.</strong>
 * {@link SchemeResponse} deliberately has no "in force" field and argues why: a version listed in a
 * history carries a date, and whether that date has arrived is a comparison somebody makes at the
 * moment they draw the list. Here there is one version and one moment, and the confirmation an
 * administrator reads — "published, and in force from Monday" — should be worded from an answer the
 * backend gave rather than from a page's own arithmetic about a clock it read separately.
 *
 * <p>It is false on every publish this application will currently accept, because a version may
 * only take effect on a Monday still to come. That is a fact about the rule rather than about the
 * field, and it is read off the clock at the moment of writing rather than assumed — so the day the
 * rule changes, this answer changes with it.
 */
record SchemeVersionPublishedResponse(SchemeResponse published, boolean itsDayHasCome) {

    static SchemeVersionPublishedResponse of(TheVersionThatNowExists published) {
        return new SchemeVersionPublishedResponse(
                SchemeResponse.of(published.version()), published.itsDayHasCome());
    }
}
