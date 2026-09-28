package io.dataroots.savingstreak.challenges;

/**
 * A step on a challenge's ladder, in the order it is climbed.
 *
 * <p>Three of them, the same three on every challenge, because the point of a ladder is that a
 * customer learns it once. What each one asks for and what it pays are the challenge's to say —
 * bronze on one challenge is EUR 100 and on another EUR 500 — and the word is only the rank.
 *
 * <p><strong>Rungs are marks on one running figure, not challenges of their own.</strong> Progress
 * is a single reading and the rungs are places on it, which is what stops the same euro from paying
 * bronze and then paying bronze again: there is one number and it only goes up. It is also what lets
 * one large deposit clear all three at once — the reading passes all three marks, so all three are
 * reached, and a customer who saves a lot in one go is never worse off than one who saved it in
 * instalments.
 *
 * <p>Declared cheapest first and served in that order, the way the rewards catalogue is, so the
 * ladder reads as a ladder wherever it is written down.
 */
public enum Rung {

    BRONZE,
    SILVER,
    GOLD
}
