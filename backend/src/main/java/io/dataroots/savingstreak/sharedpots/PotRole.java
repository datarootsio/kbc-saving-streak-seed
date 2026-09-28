package io.dataroots.savingstreak.sharedpots;

/**
 * What one member is to the pot they belong to, and therefore what they may do to it.
 *
 * <p>One role per member, and never a set of permissions. A shared pot has three things somebody can
 * be — the person who administers it, the person who pays into it, and the person who only watches —
 * and a page that had to render a bag of flags would be asking whoever reads it to work out which of
 * those three they are looking at. Three words are the whole model, and every refusal this module
 * makes about who may do what names one of them.
 *
 * <p>It travels out of the module as a word rather than as a number or a flag, the idiom a goal's
 * state and a gift's direction already set: whoever renders it decides what to call each kind, and
 * that is easier to get right from a word.
 *
 * <p>Until a pot can be invited into, {@link #OWNER} is the only one any member holds — the customer
 * who opened the pot, from the moment it exists. The other two are here rather than added later
 * because they are the vocabulary this whole feature is written in, and a role that arrives with the
 * slice that first grants it would leave the slices before it unable to say what they are refusing.
 */
public enum PotRole {

    /**
     * Administers the pot: who is in it, what it is saving for, and when it ends. An owner may also
     * do everything a contributor may do — the roles are a ladder rather than three separate lists,
     * which is what stops somebody having to be made a contributor in order to pay into the pot they
     * opened.
     *
     * <p>A pot always has at least one. That is a rule about membership rather than about this word,
     * and it is kept where members are removed and demoted.
     */
    OWNER,

    /**
     * Pays into the pot from their own current account, and may propose taking money out of it.
     * Everything about the pot's membership and its goals is somebody else's decision.
     */
    CONTRIBUTOR,

    /**
     * Watches. A viewer sees what the pot holds, who is in it and what everybody has paid in, and
     * changes none of it — which is the point of the word: somebody can be shown the money two other
     * people are saving without being given the power to spend it.
     */
    VIEWER;

    /**
     * Whether a member holding this role may put money into the pot.
     *
     * <p>Asked of the role rather than worked out by whoever is checking, because it is the one rule
     * that is read from outside this module — the pairing a deposit is judged by runs through it —
     * and a comparison written at the place that asks would be this decision kept somewhere it could
     * quietly disagree with the next one.
     *
     * <p>Stated as what a viewer may not do rather than as a list of the two who may, because the
     * roles are a ladder: an owner does everything a contributor does, and a role added above them
     * would pay in too. A list would have to be remembered; this cannot be forgotten.
     */
    public boolean mayPayIn() {
        return this != VIEWER;
    }
}
