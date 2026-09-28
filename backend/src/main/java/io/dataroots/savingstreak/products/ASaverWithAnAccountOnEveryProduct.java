package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.Customer;
import io.dataroots.savingstreak.accounts.CustomerAccounts;
import io.dataroots.savingstreak.accounts.WhoeverGivesTheDemonstrationItsSavingsProducts;
import io.dataroots.savingstreak.clock.ClockService;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The saver a trainer opens this feature on: one customer holding an account on each of the four
 * products, with a quarter of interest already paid into three of them, notice already given on part
 * of one, and a twelve-month term opened this morning with its whole length still to run.
 *
 * <p><strong>Why anything is seeded here at all.</strong> Four products on a shelf are four cards
 * until somebody is living under one of them. Every sentence this feature exists to say — that an
 * account carries on under the version it was opened with, that a notice period means some of your
 * money is yours today and some of it is not, that a month pays a twelfth of a rate on what the
 * balance averaged, that a term is a date rather than a feeling — needs an account with a past to be
 * said about, and a past cannot be declared: it is derived from a ledger and from a clock. So this
 * class opens the accounts, pays into them, gives the notice, spends the months and runs the sweep,
 * which is precisely what a trainer would otherwise have to do by hand before the first sentence of
 * a session.
 *
 * <p><strong>Why a customer of her own rather than more accounts on Anke.</strong> The obvious home
 * for these accounts was the household that already carries a decision worth simulating, and it is
 * the wrong one, for a reason that is a fact about the application rather than about taste: the most
 * a customer has ever saved is a figure <em>per customer</em> and not per account. Anke stands at
 * EUR 1 050,00 against a pot of EUR 850,00, and that gap is the whole reason
 * {@code AHouseholdWithADecisionToMake} takes money back out — it is what makes money arriving and
 * earning nothing demonstrable at all. Nineteen thousand euros paid into three more of her accounts
 * would move that mark to twenty thousand and quietly delete the state four of the simulator's
 * branches are about. A second reason agrees with the first: her run of secured weeks is derived
 * from deposits in weeks that have ended, and the months of clock this class spends have to be spent
 * <em>before</em> those deposits land or the run is three months of silence. One customer holding
 * one of everything is also simply the clearer demonstration, because the four screens sit side by
 * side under one sign-in.
 *
 * <p><strong>Declared, never generated</strong>, on the same terms as the rest of the seed: every
 * figure below is written out here, there is no randomness anywhere in it, and every day is counted
 * off the application's own clock so that a reset on any day of any month produces the same shape.
 * Everything goes in through {@link AccountsService}, {@link DepositsService},
 * {@link NoticesService}, {@link ClockService} and {@link InterestService} rather than into rows, so
 * the seeded past is held to exactly the rules a trainer doing it by hand would meet — including the
 * interest, which nobody could have written down correctly in advance because it compounds.
 *
 * <p><strong>The one exception, and it is deliberate: the agreement on the older terms is
 * written.</strong> There is no door anywhere in this application that moves an account onto an
 * <em>older</em> version, and there must not be — nothing adopts a version on anybody's behalf and
 * taking newer terms only ever goes forward. But an account still living under the terms the bank
 * sold before it repriced is the single most important thing this feature has to show, and on a
 * freshly reset database nothing needs migrating, so every account reads the same version the
 * catalogue is offering and the difference is invisible. Ticket 02 wrote that consequence down and
 * said where to fix it: here. So this class replaces one agreement row with the row that account
 * would have had if it had been opened before the repricing, through
 * {@link AccountAgreement#putting} — the same factory the module's own two writers use — rather than
 * by calling {@link AccountAgreement#takeTheNewerTermsOfTheSameProduct} with an older number, which
 * would be a lie about what that method means.
 *
 * <p><strong>The clock is left a quarter forward, and that is the price of a real history.</strong>
 * Winding is the only way to put a deposit in a month that has ended, and the clock is forward-only
 * by design, so a seed that wants three months behind it has to spend three months of clock getting
 * there. A fresh demonstration database therefore reads about a hundred and six days ahead of the
 * machine — this quarter plus the fortnight
 * {@code AHouseholdWithADecisionToMake} has always spent — and everything else in the application is
 * relative to that reading, so nothing else notices. Two things did have to notice and both are
 * handled where they live: the seeded challenge season is sized to outlast this wind (see
 * {@code ChallengesOnStartUp}), and the demonstration reward catalogue was already anchored at
 * {@code ApplicationReadyEvent} rather than at start-up, which is after every day of this is spent.
 *
 * <p><strong>All of it before the first household is written</strong>, which
 * {@link WhoeverGivesTheDemonstrationItsSavingsProducts} argues in full: a salary or a bill declared
 * on the near side of this wind would start its cursor a quarter in arrears and the first nightly
 * run of the demonstration would present three months of back rent.
 */
@Component
@Profile("demo")
class ASaverWithAnAccountOnEveryProduct implements WhoeverGivesTheDemonstrationItsSavingsProducts {

    private static final Logger log =
            LoggerFactory.getLogger(ASaverWithAnAccountOnEveryProduct.class);

    /**
     * The saver herself. A third name beside the two households, and deliberately not one of them —
     * see the class note for the figure that made it impossible.
     */
    private static final String HER_NAME = "Lotte Vermeulen";
    private static final String HER_CONTACT_DETAILS = "lotte.vermeulen@example.be";

    /**
     * What her everyday account is topped up with before anything is moved out of it.
     *
     * <p>A new customer opens with EUR 1 500,00, and three savings accounts to fund plus a term for
     * a trainer to fund during the session come to a good deal more than that. The figure is sized
     * from the two demands on it rather than picked: EUR 19 000,00 leaves for savings below, and
     * what is left has to be deep enough that the walkthrough can put five thousand into a
     * twelve-month term and still have something to demonstrate a deposit with afterwards.
     */
    private static final String HER_EVERYDAY_ACCOUNT_IS_TOPPED_UP_WITH = "30000.00";

    /**
     * What goes into the account on the older terms, the core saver and the notice account.
     *
     * <p>Sized so that a month's interest is a figure a room can read rather than a rounding. At the
     * rates this bank publishes a month pays a twelfth of the annual rate, so EUR 4 000,00 of free
     * savings pays two euros a month and EUR 9 000,00 of notice pays twelve — different enough,
     * across the three, that the products are visibly not paying the same thing for the same money,
     * which is the entire argument of the products screen.
     *
     * <p>The core saver's EUR 6 000,00 is also comfortably above its EUR 500,00 floor and stays
     * there for every one of the seeded months, so all three of its postings read "bonus earned".
     * Dipping under the floor is the walkthrough's job and not the seed's: a seeded dip would be a
     * month a trainer has to explain before they have explained the floor.
     */
    private static final String IN_THE_ACCOUNT_ON_THE_OLDER_TERMS = "4000.00";
    private static final String IN_THE_CORE_SAVER = "6000.00";
    private static final String IN_THE_NOTICE_ACCOUNT = "9000.00";

    /**
     * The notice she gave on the day she opened the notice account, and the notice she gave this
     * morning.
     *
     * <p>Two of them, because one notice can only say one thing. The first has had a quarter to run
     * and is long ready, so EUR 3 000,00 of that account is money she may take today; the second was
     * given on the day the demonstration opens and has thirty-two days still to go. Between them the
     * account says all three things a notice product has to say at once — this much is free, this
     * much is waiting, and the rest of it is neither until somebody gives notice on it.
     */
    private static final String THE_NOTICE_THAT_IS_READY = "3000.00";
    private static final String THE_NOTICE_THAT_IS_STILL_RUNNING = "1500.00";

    /**
     * How many months of past these accounts arrive with.
     *
     * <p>Three, which is the smallest number that is plainly a history rather than an accident: one
     * posting could be the first month of anything, two is a pair, and three is a column somebody
     * can read a trend down and check the compounding in by hand. It is also, at a hundred and some
     * days once the households have spent their fortnight, as much development clock as this
     * demonstration can spend without outliving the ninety-day challenge season that is seeded
     * beside it.
     */
    private static final int MONTHS_ALREADY_BEHIND_THESE_ACCOUNTS = 3;

    /**
     * The four product codes, named here rather than reached for.
     *
     * <p>{@code INSTANT} is taken from {@link WhatEachSavingsAccountIsOn} because that class already
     * has to know which product an account with nothing chosen goes on, and two spellings of the one
     * code that this module branches on would be one spelling too many. The other three are written
     * out: they are what a customer types into the chooser, this seed is a customer typing them, and
     * a constant borrowed from the start-up seed would make a demonstration that cannot be
     * reproduced by hand.
     */
    private static final String FREE_SAVINGS = WhatEachSavingsAccountIsOn.FREE_SAVINGS;
    private static final String THE_CORE_SAVER = "CORE";
    private static final String THE_NOTICE_ACCOUNT = "NOTICE32";
    private static final String THE_TWELVE_MONTH_FIXED = "FIXED12";

    /**
     * The version of free savings the bank opened with, which pays 0.60% against the 0.50% it sells
     * today. One account is put back onto it, and that is the difference the first screen is about.
     */
    private static final int THE_TERMS_THE_BANK_OPENED_WITH = 1;

    private final AccountsService accounts;
    private final DepositsService deposits;
    private final NoticesService notices;
    private final InterestService interest;
    private final ClockService theClock;
    private final Clock clock;

    /**
     * The record of what each account is living under, written to exactly once and for exactly one
     * account — see the class note on the agreement that is put back onto the older terms.
     */
    private final AccountAgreementRepository agreements;

    ASaverWithAnAccountOnEveryProduct(AccountsService accounts, DepositsService deposits,
                                      NoticesService notices, InterestService interest,
                                      ClockService theClock, Clock clock,
                                      AccountAgreementRepository agreements) {
        this.accounts = accounts;
        this.deposits = deposits;
        this.notices = notices;
        this.interest = interest;
        this.theClock = theClock;
        this.clock = clock;
        this.agreements = agreements;
    }

    /**
     * The first half: three accounts opened and funded, one of them put onto the terms the bank has
     * since stopped selling, a notice given on part of a third, and then the months spent that turn
     * all of that into a past.
     *
     * <p>The order inside it is the only one that works, and each step says why at the call. The
     * accounts are opened and paid into <em>before</em> the clock moves, because the average daily
     * balance a month pays on is walked out of the ledger and money that arrives after a month has
     * ended was not in it. The sweep is run at the end rather than nightly through the wind, because
     * the sweep pays every period it has passed and not been paid for — running it once is what the
     * job promises, and a demonstration that needed it run ninety times would be demonstrating
     * something untrue about it.
     */
    @Override
    @Transactional
    public void openTheAccountsThatHaveMonthsBehindThem() {
        Customer her = accounts.addCustomer(HER_NAME, HER_CONTACT_DETAILS);
        long customerId = her.getId();
        CustomerAccounts hers = accounts.accountsOf(customerId).orElseThrow();
        long everyday = hers.currentAccounts().get(0).getId();
        accounts.depositInto(everyday, new BigDecimal(HER_EVERYDAY_ACCOUNT_IS_TOPPED_UP_WITH));

        // The account a new customer is opened with, which is on free savings already, put back onto
        // the version the bank opened with. Done before a cent is paid in, so that every deposit
        // into it — and every month of interest after them — is stamped with the version it really
        // landed under rather than with the one the catalogue happened to be selling first.
        long onTheOlderTerms = hers.savingsAccounts().get(0).getId();
        putItBackOntoTheTermsTheBankOpenedWith(onTheOlderTerms);
        long coreSaver = accounts.openASavingsAccountFor(customerId, THE_CORE_SAVER).orElseThrow();
        long noticeAccount =
                accounts.openASavingsAccountFor(customerId, THE_NOTICE_ACCOUNT).orElseThrow();

        deposits.deposit(onTheOlderTerms, everyday, new BigDecimal(IN_THE_ACCOUNT_ON_THE_OLDER_TERMS));
        deposits.deposit(coreSaver, everyday, new BigDecimal(IN_THE_CORE_SAVER));
        deposits.deposit(noticeAccount, everyday, new BigDecimal(IN_THE_NOTICE_ACCOUNT));
        // Given on the day the money arrived, so that it has run its thirty-two days several times
        // over by the time anybody looks: this is the half of the notice account that says "you may
        // have this much today".
        notices.give(noticeAccount, new BigDecimal(THE_NOTICE_THAT_IS_READY));

        long daysSpent = spendTheMonths();
        // And the sweep, once, on the far side of them. Everything these three accounts have been
        // paid is decided here, by the same job a trainer runs by name from the jobs endpoint.
        interest.postMonthlyInterest(clock.instant());

        // One line per account seeded, with the figures a reader checks a screen against before
        // opening it: what it is on, what it holds now that its months have been paid, and — for the
        // one that matters — the version it is living under beside the one the shelf is selling.
        log.info("a saver on every product seeded customerId={} name={} currentAccountId={} "
                        + "onTheOlderTerms={} version={} holding={} coreSaver={} holding={} "
                        + "noticeAccount={} holding={} noticeReadyOn={} monthsSpent={} "
                        + "clockMovedForwardByDays={}",
                customerId, her.getName(), everyday,
                onTheOlderTerms, THE_TERMS_THE_BANK_OPENED_WITH,
                AmountOfMoney.asMoney(deposits.moneyBalanceOf(onTheOlderTerms)),
                coreSaver, AmountOfMoney.asMoney(deposits.moneyBalanceOf(coreSaver)),
                noticeAccount, AmountOfMoney.asMoney(deposits.moneyBalanceOf(noticeAccount)),
                AmountOfMoney.asMoney(new BigDecimal(THE_NOTICE_THAT_IS_READY)),
                MONTHS_ALREADY_BEHIND_THESE_ACCOUNTS, daysSpent);
    }

    /**
     * And the second half, on the day the demonstration opens: the empty twelve-month term, and a
     * second notice that has its whole thirty-two days still to run.
     *
     * <p>Both of them are here rather than above because both are about <em>today</em>. A term
     * opened before the wind would arrive a quarter old with nine months left, which is a term
     * halfway through rather than a term to wind towards; and a notice given before the wind is a
     * notice that has been ready for months, which is the one this account already has.
     */
    @Override
    @Transactional
    public void openTheAccountsThatAreOpenedOnTheDayItStarts() {
        long customerId = accounts.customerIdentifiedBy(HER_CONTACT_DETAILS).orElseThrow().getId();
        long term = accounts.openASavingsAccountFor(customerId, THE_TWELVE_MONTH_FIXED).orElseThrow();
        // Deliberately not funded. The term is somewhere for a trainer to put money in front of a
        // room, and money already in it would make the first thing they do a withdrawal that the
        // term refuses — which is the right refusal at the wrong moment in the story.
        long noticeAccount = whicheverOfHersIsOn(customerId, THE_NOTICE_ACCOUNT);
        notices.give(noticeAccount, new BigDecimal(THE_NOTICE_THAT_IS_STILL_RUNNING));

        LocalDate today = LocalDate.ofInstant(clock.instant(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
        log.info("the accounts the demonstration opens on seeded customerId={} term={} openedOn={} "
                        + "holding={} noticeAccount={} noticeStillRunningOn={} today={}",
                customerId, term, today,
                AmountOfMoney.asMoney(deposits.moneyBalanceOf(term)), noticeAccount,
                AmountOfMoney.asMoney(new BigDecimal(THE_NOTICE_THAT_IS_STILL_RUNNING)), today);
    }

    /**
     * Puts one account back onto the terms the bank opened with, by writing the agreement it would
     * have had if it had been opened before free savings was repriced.
     *
     * <p>The row is replaced rather than edited through a method of the entity's, and the class note
     * above argues why at length: there is no such thing in this application as an account moving to
     * an older version, and the two methods that do move a version are named for events this is not.
     * Both dates are carried across untouched — the account really was opened this morning and its
     * interest really does count from this morning, and it is only the agreement it is living under
     * that the demonstration is arranging.
     *
     * <p>Flushed between the two, because one agreement per savings account is a unique index this
     * module creates on purpose and an insert reordered ahead of the delete would meet it.
     */
    private void putItBackOntoTheTermsTheBankOpenedWith(long savingsAccountId) {
        AccountAgreement asOpened = agreements.findBySavingsAccountId(savingsAccountId).orElseThrow();
        LocalDate openedOn = asOpened.openedOn();
        LocalDate interestCountsFrom = asOpened.interestCountsFrom();
        int wasOn = asOpened.version();
        agreements.delete(asOpened);
        agreements.flush();
        agreements.save(AccountAgreement.putting(savingsAccountId, FREE_SAVINGS,
                THE_TERMS_THE_BANK_OPENED_WITH, openedOn, interestCountsFrom));
        // The one line that explains a screen nobody else in this application can produce: an
        // account whose agreement and whose shelf disagree. A reviewer who finds "your agreement
        // pays more than this product pays" and wonders where it came from finds it here.
        log.info("a demonstration account was put onto the terms the bank opened with "
                        + "savingsAccountId={} product={} wasOnVersion={} nowOnVersion={} "
                        + "openedOn={} interestCountsFrom={}",
                savingsAccountId, FREE_SAVINGS, wasOn, THE_TERMS_THE_BANK_OPENED_WITH,
                openedOn, interestCountsFrom);
    }

    /**
     * Moves the development clock on by exactly the months these accounts are to have behind them.
     *
     * <p>Counted through the calendar rather than as ninety days, because a month is not thirty days
     * and the interest periods are months: the clock is asked for the number of days between today
     * and the same day of the month three months on, so that the third period has ended on the day
     * the sweep is run and not two days before or after it. The same multiplied-out addition
     * {@code TheMonthlyPeriodsOfAnAccount} makes, for the same reason.
     *
     * @return how many days were spent, for the log line that says where the demonstration opens
     */
    private long spendTheMonths() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
        long days = ChronoUnit.DAYS.between(today,
                today.plusMonths(MONTHS_ALREADY_BEHIND_THESE_ACCOUNTS));
        theClock.advanceBy(days);
        return days;
    }

    /** Whichever of this customer's savings accounts is on the named product. */
    private long whicheverOfHersIsOn(long customerId, String productCode) {
        List<Long> hers = accounts.accountsOf(customerId).orElseThrow().savingsAccounts().stream()
                .map(account -> account.getId().longValue())
                .toList();
        return agreements.everyAgreement().stream()
                .filter(agreement -> hers.contains(agreement.savingsAccountId()))
                .filter(agreement -> productCode.equals(agreement.productCode()))
                .map(AccountAgreement::savingsAccountId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "the demonstration has no account on " + productCode));
    }
}
