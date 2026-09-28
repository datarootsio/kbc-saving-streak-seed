package io.dataroots.savingstreak.deposits;

/**
 * Which of an agreement's conditions is standing between a customer and their money, named in the
 * order a person meets them.
 *
 * <p><strong>Declaration order is the design.</strong> Whatever answers
 * {@link WhatAnAgreementSaysAboutMoneyLeaving} asks these in the order they are written below and
 * stops at the first one with something to say, so a customer is given one reason at a time rather
 * than a list of everything wrong at once. The order is not arbitrary: a term that has not matured
 * is a fact about the whole account and about every euro in it, notice is a fact about part of the
 * balance, and a floor is a fact about what is left behind afterwards. Told the smallest objection
 * first, somebody would give notice on money that was locked away anyway.
 *
 * <p><strong>Two of the three have nothing behind them yet, and are named all the same.</strong>
 * Only {@link #NOTICE_THAT_HAS_NOT_RUN} is answered today. The other two are written here because
 * the thing worth getting right about this seam is the order the conditions are asked in, and an
 * enum whose constants are declared in that order is where that order is recorded — a later slice
 * adds the reading behind a constant rather than deciding, then, what a withdrawal should be
 * refused for first.
 *
 * <p><strong>{@link #A_FLOOR_TO_KEEP} is the one that may never be used at all.</strong> A minimum
 * balance withholds the bonus rate for a period that dipped and leaves the money alone: a rule that
 * both refused the withdrawal and withheld the bonus could only ever do one of them, because a
 * refused withdrawal never takes a balance under the floor. It is named because the strict reading
 * of a floor is a real reading that a bank could take, and taking it should be the one line that
 * answers with this constant rather than a new vocabulary invented on the day somebody changes
 * their mind.
 *
 * <p>Not an enum of products. A notice account and a fixed term are two products today and could be
 * one tomorrow — a bank that sold a notice account with a floor would attach two of these
 * conditions to one agreement — so what is named here is the condition, which is the thing a
 * customer runs into, rather than the shape of product it happens to be attached to.
 */
public enum ConditionOnTheWayOut {

    /**
     * The account is locked until the day it matures, and that day has not come.
     *
     * <p>First because it is the only one of the three that no amount of patience inside the month
     * gets round and the only one that is about every euro in the account at once.
     */
    A_TERM_THAT_HAS_NOT_MATURED,

    /**
     * Notice has not been given on this much money, or the notice that was given has not run its
     * course yet.
     *
     * <p>The two are one condition and not two, because what the customer does about them is the
     * same thing seen at two points in time: notice is given, notice runs, money is free. A
     * refusal naming this condition says which of the two it is in words — how many days are left
     * on the notice already given, or that there is none — and that sentence is the reason the
     * answer carries one rather than being a bare constant.
     */
    NOTICE_THAT_HAS_NOT_RUN,

    /** What would be left behind is under the floor the agreement asks to be kept. */
    A_FLOOR_TO_KEEP
}
