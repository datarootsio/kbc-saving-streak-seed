package io.dataroots.savingstreak.accounts;

/**
 * Whatever puts the savings products into the demonstration, told when the seed has reached the two
 * moments it has to be told about.
 *
 * <p><strong>Declared here and implemented elsewhere, for the same reason as
 * {@link WhoeverRecordsWhatASavingsAccountIsOn}.</strong> {@link DemoData} composes one
 * demonstration and decides the order it goes in; what a savings product is, which versions of its
 * terms have been published, what a notice is and what a month of interest pays are all somebody
 * else's answers, and an {@code accounts} class that imported them would make the oldest module in
 * this application depend on one of the newest. So the dependency runs the only way it can: the
 * seed says where it has got to, and whoever keeps the products writes what belongs at that point.
 * Nothing about a product — no code, no version, no rate, no balance — travels through here in
 * either direction.
 *
 * <p><strong>There are two sentences because the seed has two moments and they are months of clock
 * apart.</strong> Accounts that are to have a past behind them must be opened and funded
 * <em>before</em> the seed spends the months of development clock that give them one, and the
 * months must be spent before any household declares a salary or a bill — a cursor started on the
 * near side of that wind would open the demonstration a quarter in arrears, which is the state
 * {@code AnIncomeDeclaredTodayIsNotAYearOfBackPay} exists to keep this application out of. An
 * account that is meant to read as opened <em>today</em> has to be opened on the far side of it,
 * which is the second sentence and the last thing the seed does.
 *
 * <p>One implementation, under the {@code demo} profile only, and no registry of them: this is here
 * for the direction of the dependency and not for the plurality, which is the same thing the
 * pairing interface says about itself.
 */
public interface WhoeverGivesTheDemonstrationItsSavingsProducts {

    /**
     * Open and fund the savings accounts that are to arrive with months behind them, and spend the
     * months.
     *
     * <p>Said before the first household is seeded, because this moves the development clock and
     * every declaration made afterwards counts from where it leaves it. Whoever holds these
     * accounts is the implementation's own business: the seed is not asked for a customer and does
     * not offer one, so nothing about whose demonstration this is leaks into the module that keeps
     * the agreements.
     */
    void openTheAccountsThatHaveMonthsBehindThem();

    /**
     * Open the accounts that are to read as opened today, on the day the demonstration opens.
     *
     * <p>Said last of all, after every household has been written and the clock is standing where a
     * trainer will find it. A term opened before the wind above would read as a term a quarter old
     * on the first screen, and the whole point of it is that it has its full length still to run.
     */
    void openTheAccountsThatAreOpenedOnTheDayItStarts();
}
