package io.dataroots.savingstreak.challenges;

/**
 * One challenge named in the seasons listing: its code and the words at the top of its card.
 *
 * <p>Deliberately not {@link AChallengeAsItStands}. That is a card, and a card is the definition and
 * somebody's place in it answered together — the reading, the next rung, what it still asks for. The
 * seasons listing names no customer at all, so there is no place in anything to report, and handing
 * back a card with every one of those fields empty would be offering a shape that could only ever be
 * half filled in.
 *
 * <p>Two fields, because two are what the question needs: a page showing the season's banner wants
 * to say what is in it, and anything more about one of them is a read of the customer's own tab.
 */
public record AChallengeInASeason(String code, String title) {
}
