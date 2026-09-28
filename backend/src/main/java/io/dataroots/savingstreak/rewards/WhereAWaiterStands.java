package io.dataroots.savingstreak.rewards;

import java.util.Optional;

/**
 * Whatever can assemble a {@link CustomerStanding}, asked the one question this module cannot
 * answer for itself at five in the morning: where does this customer stand today?
 *
 * <p><strong>Declared here and implemented elsewhere, which is the whole point of it</strong> —
 * the shape {@code accounts.WhoMayPayIntoAnAccountNobodyHolds} already established in this
 * application, and for the same reason. A run of weeks, a trophy case and a lifetime of points
 * earned belong to Streaks, Challenges and Points, and this module has spent an entire feature
 * not importing any of them. It states the question in its own vocabulary; the web layer, which
 * already reads across modules, answers it. The dependency runs the only way it can, and
 * nothing in this package gains an import.
 *
 * <p><strong>Why a question at all, when every other path is simply handed a standing.</strong>
 * Claiming, holding and reading the catalogue all arrive through a controller, and a controller
 * has a customer in its path: it assembles one standing and hands it in, which is the
 * arrangement {@link CustomerStanding} argues for at length and which nothing here replaces. The
 * sweep is the one caller that cannot do that. It runs on a schedule with no request behind it,
 * and — this is the part that decides the shape — <em>it does not know whose standing it will
 * need until it has read the queues</em>. Who is next in line for an offer that got its stock
 * back overnight is an answer that lives in this module's own tables, and no caller outside it
 * can assemble the right standings in advance without first being told the thing only this
 * module knows. Handing in a list would mean handing in either the wrong list or the whole
 * customer base.
 *
 * <p>So the fact still arrives from outside and is still assembled by the layer that owns the
 * modules it comes from. What changes is that the sweep asks for it by name when it turns out
 * to need it, rather than being given it before anybody knew which names mattered. The
 * alternative — promoting whoever is at the front of the queue without checking that the offer
 * is still theirs to have — is the one this ticket rules out: a customer whose streak has
 * lapsed would be handed a hold on something they would be refused at the moment they tried to
 * convert it, which is worse than not being promoted.
 *
 * <p>One implementation today and no registry of them, for the reason the accounts interface
 * gives: this is here for the direction of the dependency and not for the plurality.
 */
public interface WhereAWaiterStands {

    /**
     * Where one customer stands, or nothing at all if this application has never heard of them.
     *
     * <p><strong>An {@link Optional} rather than the empty standing.</strong>
     * {@link CustomerStanding#nothingIsKnown} exists so that a request for an unknown customer
     * is refused by the module that was actually asked to do something rather than by whichever
     * one was asked first, and its own javadoc says it is safe <em>precisely because it is never
     * read</em>. The sweep would read it: a customer nobody has heard of would come back with
     * no streak, no badges and no points, an offer with no rules on it would let them through,
     * and the queue would hand a hold to nobody. So the absence is said out loud here and the
     * sweep passes that waiter over — which cannot happen in anything this application does,
     * because nothing deletes a customer, and is written down rather than left to luck.
     *
     * @param customerId the customer whose standing is wanted, who may not exist
     * @return their standing as of this moment, or empty if nothing is known about them
     */
    Optional<CustomerStanding> theStandingOf(long customerId);
}
