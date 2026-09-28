package io.dataroots.savingstreak.simulation;

import java.util.Optional;

/**
 * A question about a future this application will not answer, carrying the reason in words the
 * person who asked can act on — and, when the reason is about one part of a question made of
 * several, which part it was.
 *
 * <p>Its own refusal rather than one borrowed from Deposits or Goals, for the reason every other
 * module's is its own: they refuse for their own reasons and will grow apart. What it does share is
 * the wording of anything more than one of them has to say — a figure that is not an amount of money
 * is {@code AmountOfMoney}'s sentence here exactly as it is on a deposit, because a customer who has
 * met both objections has met one objection.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API and it is answered in the web layer, where a new value below breaks the switch by design.
 *
 * <p><strong>One kind, and the list of things it covers is longer than most.</strong> Every way a
 * question about a future can be refused comes to the same thing for whoever is reading it: change
 * what you asked for and ask again. An amount that is not an amount of money, a day outside the year
 * the simulation is drawn over, a change nobody has heard of, more futures than can be compared at
 * once and a column with no heading are all that sentence, and the reason says which of them it was.
 * The kind is here rather than absent so that the next reason that is genuinely a different thing to
 * do next — a conflict, say, or something about the account rather than about the request — arrives
 * as a value the web layer is made to price rather than as a 400 it quietly inherits.
 *
 * <p><strong>Where it was asked travels beside the reason rather than inside it.</strong> A question
 * can now carry four futures of ten changes each, so "that day is not inside the year this
 * simulation is drawn over" is a true sentence that leaves a customer staring at four columns
 * wondering which of their forty boxes it is about. The scenario's own name, the change's position
 * in it and the change as it was asked therefore ride along, and the web layer puts them on the
 * problem detail as members of their own.
 *
 * <p>They are <em>not</em> spliced into the sentence, and that is deliberate. Ticket after ticket in
 * this module has settled on one wording per objection — {@code AmountOfMoney}'s sentence word for
 * word, {@link AnAdjustment#whyThatDayIsOutsideTheWindow}'s window named the same way by every kind
 * that carries a day — and a refusal that prefixed each of them with a column heading would be a
 * different sentence for the same mistake depending on how many questions were asked at once. A
 * problem detail has extension members for exactly this: the reason is the reason, and where it was
 * asked is somewhere to point. A page drawing four columns highlights the column and the chip; a
 * customer reading the sentence reads the same sentence they would have read had they asked about
 * one future.
 *
 * <p><strong>There is no {@code NO_SUCH_ACCOUNT} kind.</strong> This module cannot tell a savings
 * account that exists from a number somebody made up — both are a balance of nothing and a list of
 * no deposits — so whoever owns accounts vouches for the identifier before a future is ever folded,
 * and says so in the words {@code AccountsService} owns.
 */
public class SimulationRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal can be about. One, for now, and the paragraph above says why
     * the enumeration exists at all with one value in it.
     */
    public enum Kind {

        /**
         * The question does not describe a future this application will fold: an amount that is not
         * an amount of money, a day outside the window, a kind of change nobody has heard of, more
         * futures than can be compared side by side, a scenario nobody named, or one made of more
         * changes than a question is allowed to carry. All of them are "change what you asked and
         * ask again", and the sentence says which it was.
         */
        AGAINST_THE_RULES
    }

    private final Kind kind;
    private final String scenario;
    private final Integer changeNumber;
    private final String change;

    private SimulationRefused(Kind kind, String scenario, Integer changeNumber, String change,
                              String reason) {
        super(reason);
        this.kind = kind;
        this.scenario = scenario == null || scenario.isBlank() ? null : scenario;
        this.changeNumber = changeNumber;
        this.change = change;
    }

    /**
     * A question this application will not fold, in the words of whichever rule refused it, and
     * about the asking as a whole rather than about any one part of it.
     *
     * <p>Two things are refused this way and both are about the request rather than about a column:
     * a kind of change nobody has heard of, which is refused before there is anything to call a
     * scenario, and more futures than can be compared at once, which is an objection to how many
     * columns there are rather than to any of them.
     */
    static SimulationRefused againstTheRules(String reason) {
        return new SimulationRefused(Kind.AGAINST_THE_RULES, null, null, null, reason);
    }

    /**
     * A refusal about one of the futures in the question: the scenario's own name travels with it so
     * that a page drawing several columns can say which one.
     *
     * <p>A scenario nobody named is refused this way too, and hands over nothing to point at, which
     * is the whole of why it is refused: its sentence names the position it was asked in instead,
     * because that is the only thing there is to call it.
     */
    static SimulationRefused aboutThatScenario(String scenario, String reason) {
        return new SimulationRefused(Kind.AGAINST_THE_RULES, scenario, null, null, reason);
    }

    /**
     * A refusal about one change inside one of the futures: which scenario, which of its changes
     * counting from one, and how that change reads.
     *
     * <p>The position as well as the words, because two changes of one kind in one scenario compose
     * rather than replacing each other — two withdrawals on two days are two withdrawals — and a
     * customer who has typed the same figure twice needs to be told which of the two chips to fix.
     */
    static SimulationRefused aboutThatChange(String scenario, int changeNumber, String change,
                                             String reason) {
        return new SimulationRefused(Kind.AGAINST_THE_RULES, scenario, changeNumber, change, reason);
    }

    public Kind kind() {
        return kind;
    }

    /** The name the customer gave the future this is about, when it is about one of them. */
    public Optional<String> scenario() {
        return Optional.ofNullable(scenario);
    }

    /** Which of that scenario's changes it is about, counting from one, when it is about one. */
    public Optional<Integer> changeNumber() {
        return Optional.ofNullable(changeNumber);
    }

    /** That change as it was asked, in the phrase the log line names it by. */
    public Optional<String> change() {
        return Optional.ofNullable(change);
    }
}
