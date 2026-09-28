package io.dataroots.savingstreak.schemepreview;

import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.notifications.HowManyStandOnTheFarSideOfALine;
import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;

/**
 * The whole answer a preview gives: what the candidate changes, what it would have done to
 * everybody's run over the last twenty-six weeks, what it does to points, and how much more or less
 * this application would be saying to people tonight.
 *
 * <p><strong>It says what it is, in itself.</strong> {@link #whatThisIs} is not decoration and it is
 * not a caption for a screen to remember to draw: because the scheme is never retroactive, no week
 * has yet been judged under the candidate, so "what changes on the effective date" has the answer
 * <em>nothing</em>. The question worth asking before publishing is the counterfactual, and a number
 * answering a counterfactual that arrives unlabelled will be read as a forecast by the first person
 * in a hurry. Carrying the label in the response body is the only place it cannot be lost: a screen
 * can forget it, a copied figure in an email has already lost it, and this cannot be quoted without
 * it.
 *
 * <p><strong>Twenty-six weeks because it fills the ladder four times over.</strong> The run a rate
 * is paid on tops out at six weeks, so a stretch of twenty-six is long enough that a customer who
 * has been saving steadily is at the cap under both readings and a customer who has not is visibly
 * not — and short enough to walk for every customer inside one request.
 *
 * <p><strong>Both versions travel whole.</strong> {@link #theVersionInForce} and
 * {@link #theVersionThisWouldBecome} are the same value the scheme endpoint serves, in their own
 * units, carrying the version number the publish would give the candidate. The figure-by-figure
 * difference beside them is the reading, not the data: everything that wants to compute has the
 * typed figures, and everything that wants to show a person what they changed has the table.
 *
 * <p><strong>Nothing here was written down anywhere.</strong> The whole record is derived, twice,
 * from a ledger nobody touched.
 *
 * @param whatThisIs                 the counterfactual, stated as a sentence, in the body
 * @param overHowManyWeeks           how far back the counterfactual reaches
 * @param asIfItHadBeenTheRuleSince  the Monday the counterfactual starts from
 * @param theVersionInForce          what the scheme says today
 * @param theVersionThisWouldBecome  what the candidate would be published as, numbered
 * @param figures                    the difference between those two, figure by figure
 * @param runs                       the roll-up over every customer
 * @param theWorstAffected           named, worst first, capped at twenty
 * @param points                     what happens to batches already earned, and to new ones
 * @param notifications              per line the scheme draws, who moves across it either way
 */
public record WhatThisSchemeWouldDo(String whatThisIs, int overHowManyWeeks,
                                    LocalDate asIfItHadBeenTheRuleSince,
                                    TheSchemeAsPublished theVersionInForce,
                                    TheSchemeAsPublished theVersionThisWouldBecome,
                                    List<AFigureAsItWouldRead> figures, HowManyRunsWouldRead runs,
                                    List<ACustomerWhoseRunWouldRead> theWorstAffected,
                                    WhatWouldHappenToPoints points,
                                    List<HowManyStandOnTheFarSideOfALine> notifications) {

    /**
     * Whether this candidate changes nothing at all — no figure, no run, nobody told anything
     * different.
     *
     * <p>Answered here rather than by whoever draws the preview, because "nothing changes" is the
     * one reading of this record that has to be the same everywhere: it is what an administrator
     * checks the tool against by previewing the version already in force, and a screen that decided
     * it from a subset of the fields would eventually declare a scheme harmless because it only
     * looked at the figures.
     */
    public boolean itWouldChangeNothing() {
        return figures.stream().noneMatch(AFigureAsItWouldRead::itWouldChange)
                && runs.runsThatWouldReadDifferently() == 0
                && notifications.stream().allMatch(HowManyStandOnTheFarSideOfALine::itWouldMoveNobody);
    }
}
