package be.kbc.savingstreak.service;

/**
 * What a withdrawal gave up: the bonus points lost, and how far away the anniversary was.
 * Only bonuses that were about to vest are counted; losing one that was months off is not
 * worth telling the customer about.
 */
public record ForfeitedBonus(int points, long daysAway) {

    public static final ForfeitedBonus NOTHING = new ForfeitedBonus(0, 0);

    public boolean isSomething() {
        return points > 0;
    }
}
