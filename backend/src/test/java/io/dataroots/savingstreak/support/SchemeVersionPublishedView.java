package io.dataroots.savingstreak.support;

/**
 * What the API answers when a version of the scheme has been published: the version that now
 * exists, and whether its day has come.
 *
 * <p>The version is the same {@link SchemeView} every other read of the scheme serves, because that
 * is what the API sends — one shape for a version of the scheme, everywhere. A test with a second
 * shape for the receipt would stop noticing the day the receipt and the reading stopped agreeing,
 * which is exactly the drift the shared views exist to catch.
 *
 * <p>{@code itsDayHasCome} is false on every version this application will currently publish,
 * because a version may only take effect on a Monday still to come. It is asserted on all the same:
 * a field that is always false because of a rule is worth pinning, since the day the rule quietly
 * stopped holding is the day this assertion would say so.
 */
public record SchemeVersionPublishedView(SchemeView published, boolean itsDayHasCome) {
}
