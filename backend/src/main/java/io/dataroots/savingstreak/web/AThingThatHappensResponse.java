package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.simulation.AKindOfThingThatHappens;
import io.dataroots.savingstreak.simulation.AThingThatHappens;

/**
 * One dated thing in a branch as the API answers it: the day, the kind and the figure.
 *
 * <p>The kind travels as its name, which is what every other enumerated answer in this application
 * does, so that a page holds the words and the backend holds the rule. A sentence assembled here
 * would be this application deciding what language a customer reads, and a screen reader's list and
 * a bar's tooltip want the same fact said two different ways.
 *
 * <p>Nothing is worked out in this class. Which things are worth dating, what order they come in and
 * what each one's figure means are all the Simulation module's decisions; a response record taking
 * any of them would be a rule in the web layer.
 *
 * <p>Every figure here is an illustration rather than a promise, said once at the top of the answer
 * rather than on each of them.
 */
record AThingThatHappensResponse(LocalDate on, AKindOfThingThatHappens kind, BigDecimal figure) {

    static AThingThatHappensResponse of(AThingThatHappens thing) {
        return new AThingThatHappensResponse(thing.on(), thing.kind(), thing.figure());
    }
}
