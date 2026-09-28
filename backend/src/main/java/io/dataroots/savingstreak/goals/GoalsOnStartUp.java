package io.dataroots.savingstreak.goals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Makes the order of importance strict in the database — one goal per place on a savings account —
 * before the application serves anything.
 *
 * <p>The order is the thing this whole feature turns on: which goal takes a deadline minimum first,
 * which one the surplus falls to, and which goals a reallocation may take money out of are all read
 * off it. {@link GoalsService} has kept it a run of 1..n since ticket 01 — a new goal is ranked last,
 * abandoning one closes the gap, and the order is only ever set as a whole permutation — and until
 * now that was the only thing keeping it. It held because SQLite serialises writers and this
 * application runs on a pool of one connection, which is a property of a configuration file rather
 * than a rule about goals: raise {@code spring.datasource.hikari.maximum-pool-size} and two requests
 * adding a goal at the same moment both read the same {@code size() + 1} and both take it, leaving
 * two goals tied for a place the plan then spends money in an arbitrary one of.
 *
 * <p>Here rather than on the entity because the entity cannot say it. The schema is generated from the
 * entity model ({@code ddl-auto=update}) against SQLite, and that dialect writes a composite unique
 * clause nowhere: declared as a unique constraint, or as an index marked unique, the table is created
 * without it and the only statement that reaches the database is a drop that does nothing.
 * A {@code create unique index} is a statement SQLite does accept, so this is where the guarantee
 * comes from — and it is a step a reviewer can watch happen in a DEBUG start-up log rather than an
 * annotation they would have to take on trust. {@code LoyaltyOnStartUp} and
 * {@code NotificationsOnStartUp} are the same step for the same reason.
 *
 * <p>Abandoned goals are outside it, and that is not an escape hatch. A goal that has been given up on
 * holds no place, so its rank is null, and SQLite counts nulls as distinct from one another in a
 * unique index: an account can carry any number of them without two ever colliding. They are not
 * competing for anything, so there is nothing for them to be unique about.
 *
 * <p>The same shape as the other modules' start-up steps: it runs on every start, and all but the
 * first do nothing.
 */
@Component
class GoalsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(GoalsOnStartUp.class);

    private final SavingsGoalRepository goals;

    GoalsOnStartUp(SavingsGoalRepository goals) {
        this.goals = goals;
    }

    /**
     * Called once every bean exists and before the context finishes refreshing — which is before the
     * web server binds its port, so no goal can be opened against an order that is not yet strict.
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (goals.theOrderIsAlreadyStrict() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only on
            // the first.
            log.debug("the order of importance is already strict per savings account "
                    + "index=one_goal_per_place_on_a_savings_account");
            return;
        }
        goals.makeTheOrderStrict();
        log.info("the order of importance was made strict per savings account "
                + "index=one_goal_per_place_on_a_savings_account columns=[savings_account_id, "
                + "goal_rank] over=[goals that still hold a place; an abandoned goal holds none]");
    }
}
