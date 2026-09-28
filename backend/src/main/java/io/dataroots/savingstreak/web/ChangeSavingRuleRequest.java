package io.dataroots.savingstreak.web;

import java.util.List;

/**
 * What a customer wants said differently about a rule they already have standing: any of its name,
 * what makes it move, the day it moves on, how much moves, the amount and the floor.
 *
 * <p>Absent means "leave it alone", everywhere. A page that only lets somebody edit the amount sends
 * the amount, and the rule's day and trigger are none of that request's business.
 *
 * <p>The current account is deliberately not here. Where the money comes from is half of what a rule
 * is, and a rule that had drawn from one account and then another would have a history whose entries
 * mean different things depending on when they were made — so changing it is ending this rule and
 * leaving a new one standing, which keeps both records true.
 *
 * <p><strong>{@code split} is the one field with two ways to be absent, and both are things a
 * customer means.</strong> Leaving it out is "leave the split alone", like every other field here.
 * An empty list is "stop spreading it": the shares go and the rule deposits unallocated again. A
 * list replaces the old split whole rather than being merged into it, because a split is a sentence
 * about proportions and half of one does not add to a hundred.
 *
 * <p>The same shapes as {@link NewSavingRuleRequest}, field for field, so that a page can send the
 * boxes it is holding without translating between two vocabularies.
 */
record ChangeSavingRuleRequest(String name, String trigger, String dayOfWeek, String dayOfMonth,
                               String howMuchMoves, String amount, String floor,
                               List<SavingRuleSplitRequest> split) {
}
