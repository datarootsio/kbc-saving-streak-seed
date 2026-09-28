package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.accounts.CustomerRefused.Kind.ALREADY_BANKS_HERE;
import static io.dataroots.savingstreak.accounts.CustomerRefused.Kind.NO_CONTACT_DETAILS;
import static io.dataroots.savingstreak.accounts.CustomerRefused.Kind.NO_NAME;
import static io.dataroots.savingstreak.accounts.MonthlyIncomeRefused.Kind.AGAINST_THE_RULES;
import static io.dataroots.savingstreak.accounts.RecurringBillRefused.Kind.NO_SUCH_BILL;
import static io.dataroots.savingstreak.accounts.RecurringBillRefused.Kind.THE_BILL_IS_ENDED;

/**
 * The Accounts module's face to the rest of the application. It answers who exists, which accounts
 * belong to whom, and what is in the ones this application keeps a figure for.
 *
 * <p>It also keeps the one thing that puts money <em>into</em> a current account: the monthly income
 * its holder declares, and the nightly credit that honours it. Nothing else anywhere in this
 * application does, which is why it is here — a current account's balance is this module's figure to
 * keep truthful, and a module that could only ever take money out of one would leave every
 * conditional rule downstream firing once and then sitting inert for a year.
 *
 * <p>And the other half of the same sentence: the <strong>recurring bills</strong> its holder
 * declares, which are what leaves the account every month, and the nightly run that presents them.
 * They are here beside the income they oppose because a bill is a fact about a current account and
 * nothing else in this application needs to have heard of one — what a bill does to deposits,
 * automation, goals and streaks is move the balance they already read.
 *
 * <p>The two nightly runs this class owns are deliberately an hour and a half apart and the gap
 * between them is not this class's to decide: {@code IncomeLandsOnPayday} credits at one,
 * {@code SavingRulesRunNightly} takes its cut at two, and {@link BillsAreTakenWhenTheyFallDue}
 * presents the bills at half past two against whatever is left. That last ordering is the whole
 * point of declaring what leaves an account, and the job class says at length what moving it would
 * destroy.
 */
@Service
public class AccountsService {

    private static final Logger log = LoggerFactory.getLogger(AccountsService.class);

    /**
     * What a customer this application opens finds in their current account on the first day.
     *
     * <p>They are given some, rather than none, because a customer who cannot deposit is not a
     * customer this application can demonstrate anything with: every point in it comes from moving
     * euros into savings, and somebody added with an empty current account could only ever receive
     * gifts. The seeded two were written down with EUR 2.480,00 and EUR 1.150,00 for the same
     * reason, and this figure sits between them.
     *
     * <p>Invented money, like theirs. Nothing anywhere in this application pretends otherwise, and
     * the footer on every screen says so.
     */
    private static final BigDecimal WHAT_A_NEW_CUSTOMER_STARTS_WITH = new BigDecimal("1500.00");

    /**
     * How many recurring bills one current account may have standing at once.
     *
     * <p>A cap rather than nothing, for the reason the ten saving rules one customer may leave
     * standing are capped: the nightly run that takes them walks every standing bill on every
     * account, and an unbounded list is a denial of service on every one of those runs. It is also
     * what a page can promise to draw.
     *
     * <p>Twenty rather than ten, because the two limits bound different things. Ten rules is a
     * customer's own instructions to themselves and nobody writes eleven; twenty bills is a real
     * household's rent, energy, water, phone, internet, insurance and the rest, and a household that
     * met a cap of ten would be a household this application cannot describe. Ended bills do not
     * count against it, so ending one makes room for another.
     */
    private static final int HOW_MANY_BILLS_ONE_ACCOUNT_MAY_CARRY = 20;

    /**
     * The most due dates one bill catches up in one run.
     *
     * <p>Five hundred, the same figure the saving rules catch up in, and it is a guard against a
     * demonstration rather than against a customer. The development clock goes a hundred years
     * forward in one move, and a monthly bill over that span is twelve hundred debits: a trainer who
     * winds a century by accident should be told what happened, not watch the application work
     * through twelve hundred withdrawals in one transaction while the page waits.
     *
     * <p><strong>It delays a catch-up rather than losing one.</strong> The cursor is left at the
     * beginning of the date this run stopped at instead of at the moment it ran, so the next run
     * picks the rest up where this one put it down. Nothing is dropped and nothing is taken twice —
     * the record keeps that promise — and the WARN names the dates that were left so that the delay
     * is visible rather than inferred from a total that looks low.
     */
    private static final int MOST_DUE_DATES_ONE_BILL_CATCHES_UP_IN_ONE_RUN = 500;

    private final CustomerRepository customers;
    private final CurrentAccountRepository currentAccounts;
    private final SavingsAccountRepository savingsAccounts;
    private final MonthlyIncomeRepository monthlyIncomes;
    private final IncomePaidRepository incomePaid;

    /**
     * What each current account's holder says leaves it every month — the outbound half of the same
     * declaration the two fields above are the inbound half of.
     *
     * <p>Here rather than in a module of its own because a bill is a fact about a current account,
     * and this is the module that owns what a current account is. Nothing in deposits, automation,
     * goals or streaks has heard of one: what a bill does to them is move the balance they already
     * read.
     */
    private final RecurringBillRepository recurringBills;

    /**
     * What became of every date a bill has fallen due on — the outbound mirror of {@link IncomePaid},
     * and the only record there is of a bill having been presented at all.
     *
     * <p>Here rather than derived from the money that moved, because a date that met an empty
     * account moved no money and is as much a part of the history as one that took nine hundred
     * euros. It is also what makes the nightly run idempotent: one row per bill per due date, unique
     * in the database, so a job run twice over one date takes the rent once.
     */
    private final BillOccurrenceRepository billsSettled;

    /**
     * The application's clock, for the things in this module that are dated: a declaration of
     * income and the credits that follow from it, and a bill declared, changed or ended. Everything
     * else here answers about state rather than about a moment.
     */
    private final Clock clock;

    /**
     * Whatever holds the savings accounts no customer holds, for the one question about them this
     * module cannot answer: may this customer pay into one? {@link WhoMayPayIntoAnAccountNobodyHolds}
     * says at length why the dependency runs this way round.
     *
     * <p>Injected as the interface and never as the thing behind it, which is what keeps this class
     * free of any knowledge that a shared pot exists — and what keeps the two services out of a
     * start-up cycle, since the implementation reads its own records and asks this module nothing.
     */
    private final WhoMayPayIntoAnAccountNobodyHolds whoMayPayIn;

    /**
     * Whatever keeps the record of which agreement a savings account is living under, told whenever
     * one is opened. {@link WhoeverRecordsWhatASavingsAccountIsOn} says at length why the
     * dependency runs this way round.
     *
     * <p>Injected as the interface and never as the thing behind it, for the same reason as the
     * field above: it is what keeps this class free of any knowledge that a savings product exists,
     * and what keeps the two services out of a start-up cycle, since the implementation reads its
     * own records and asks this module nothing.
     */
    private final WhoeverRecordsWhatASavingsAccountIsOn whatAnAccountIsPutOn;

    AccountsService(CustomerRepository customers,
                    CurrentAccountRepository currentAccounts,
                    SavingsAccountRepository savingsAccounts,
                    MonthlyIncomeRepository monthlyIncomes,
                    IncomePaidRepository incomePaid,
                    RecurringBillRepository recurringBills,
                    BillOccurrenceRepository billsSettled,
                    Clock clock,
                    WhoMayPayIntoAnAccountNobodyHolds whoMayPayIn,
                    WhoeverRecordsWhatASavingsAccountIsOn whatAnAccountIsPutOn) {
        this.customers = customers;
        this.currentAccounts = currentAccounts;
        this.savingsAccounts = savingsAccounts;
        this.monthlyIncomes = monthlyIncomes;
        this.incomePaid = incomePaid;
        this.recurringBills = recurringBills;
        this.billsSettled = billsSettled;
        this.clock = clock;
        this.whoMayPayIn = whoMayPayIn;
        this.whatAnAccountIsPutOn = whatAnAccountIsPutOn;
    }

    @Transactional(readOnly = true)
    public List<Customer> customers() {
        return customers.findAll();
    }

    /**
     * Opens a customer, with the accounts that make them one, and answers with the customer that
     * now exists.
     *
     * <p>They arrive able to do everything the seeded two can: a current account with money in it
     * to move, and a savings account to move it into. One savings account rather than Anke's two,
     * because a second one exists to demonstrate that saving towards two goals stays apart, and
     * that is a thing the seeded data is there to show rather than a thing every customer needs.
     * Nothing else is created — no points, no deposits, no streak — because a customer who has
     * saved nothing has earned nothing, and a pot handed out at zero is the same pot as no pot.
     *
     * <p>Three things are refused and nothing else: no name, no contact details, and contact
     * details somebody already banks under. The first two are settled on the trimmed text, so a
     * name of spaces is no name. The third is the one that protects something: signing in and
     * addressing a gift both look a customer up <em>by</em> that address and expect at most one, so
     * a second customer sharing it would not be a duplicate to tidy up later — it would make both
     * of them unreachable.
     *
     * <p>That refusal tells whoever asked that somebody banks under the address they typed, which
     * is more than they knew before they asked. It is not a leak this application has anything left
     * to protect: {@link #customers()} publishes the whole directory, contact details and all, and
     * both are noted as the first things an authentication slice would take away.
     *
     * <p>The name and the address are stored trimmed but otherwise exactly as typed. Nothing is
     * capitalised, corrected or checked for looking like an email: a training application whose
     * participants type deliberately odd things should record what they typed, and the one place
     * case is not allowed to matter — finding somebody by their address — already ignores it.
     *
     * @throws CustomerRefused if the customer is one of the three this module will not open
     */
    @Transactional
    public Customer addCustomer(String name, String contactDetails) {
        String theirName = name == null ? "" : name.trim();
        String theirAddress = contactDetails == null ? "" : contactDetails.trim();
        log.debug("customer asked for name={} contactDetails={}", theirName, theirAddress);
        if (theirName.isBlank()) {
            throw refusingToOpen(theirAddress, NO_NAME, "Fill in the name of the person to add.");
        }
        if (theirAddress.isBlank()) {
            throw refusingToOpen(theirAddress, NO_CONTACT_DETAILS,
                    "Fill in the email address " + theirName + " will bank with.");
        }
        // Found the way signing in finds somebody, so that an address differing only in case is the
        // same address. Otherwise "Anke.Peeters@example.be" would open a second customer that
        // signing in could never reach past the first.
        Optional<Customer> alreadyHere = customers.findByContactDetailsIgnoreCase(theirAddress);
        if (alreadyHere.isPresent()) {
            throw refusingToOpen(theirAddress, ALREADY_BANKS_HERE,
                    alreadyHere.get().getName() + " already banks here under that email address.");
        }
        Customer customer = customers.save(new Customer(theirName, theirAddress));
        // The account number is computed from the identifier, so it cannot be worked out until the
        // row has one. Same transaction, so a customer never exists without the accounts that make
        // them one.
        CurrentAccount toSpendFrom = currentAccounts.save(new CurrentAccount(
                customer, BelgianIban.forCustomer(customer.getId()), WHAT_A_NEW_CUSTOMER_STARTS_WITH));
        SavingsAccount toSaveInto = savingsAccounts.save(new SavingsAccount(customer));
        // And the agreement it is going to live under, written in the same transaction as the
        // account itself: an account that existed for a moment with no product would be an account
        // no rule could answer about, and the moment would be exactly the moment somebody paid into
        // it. What that agreement says is not this module's answer and never arrives here.
        whatAnAccountIsPutOn.aSavingsAccountWasOpened(toSaveInto.getId());
        // One line per customer opened, with the accounts they were opened with: a savings account
        // appearing in the nightly sweep that belongs to nobody a reviewer recognises is explainable
        // from this line alone.
        log.info("customer added customerId={} name={} contactDetails={} currentAccountId={} "
                        + "iban={} openingBalance={} savingsAccountId={}",
                customer.getId(), customer.getName(), customer.getContactDetails(),
                toSpendFrom.getId(), toSpendFrom.getIban(),
                WHAT_A_NEW_CUSTOMER_STARTS_WITH.toPlainString(), toSaveInto.getId());
        return customer;
    }

    /**
     * Every refusal to open a customer says why in the log as well as to whoever asked, because
     * only one of the two is kept: the reason reaches the person at the keyboard and nowhere else.
     *
     * <p>The address is logged as it was given, because that is what a reviewer tracing "I could not
     * add my colleague" needs — the typo is the whole of the story.
     */
    private CustomerRefused refusingToOpen(String addressAsGiven, CustomerRefused.Kind kind,
                                           String reason) {
        log.warn("customer rejected contactDetailsAsGiven={} kind={} reason={}",
                addressAsGiven, kind, reason);
        return new CustomerRefused(kind, reason);
    }

    /**
     * The customer who gave this address, or nothing at all if no customer did.
     *
     * <p>Recognising somebody is not the same as letting them in, and this method does only the
     * first. Nothing is checked beyond the address — there is no password to check — so what comes
     * back is "this address belongs to a customer we have", and a caller that treats that as proof
     * of who is at the keyboard is deciding something this module never said.
     */
    @Transactional(readOnly = true)
    public Optional<Customer> customerIdentifiedBy(String contactDetails) {
        return customers.findByContactDetailsIgnoreCase(contactDetails.trim());
    }

    /**
     * The customer with this identifier, or nothing at all if there is no such customer.
     *
     * <p>The customer rather than only whether they exist, for a caller that has to name them: a
     * module reporting something one customer did to another has to be able to put both names in
     * front of whoever reads it, and asking whether they exist and then asking who they are is two
     * queries for one fact. {@link #customerExists} is still the answer where only the fact is
     * wanted.
     */
    @Transactional(readOnly = true)
    public Optional<Customer> customerWith(long customerId) {
        return customers.findById(customerId);
    }

    /**
     * Opens a savings account that nobody holds, and answers with its identifier.
     *
     * <p>For whatever holds an account that is not a person. A shared pot holds one, and this module
     * does not know that: it is asked for an account with no holding customer and answers with one,
     * which is the whole of what it has to understand. The pot points at the account and the account
     * points at nobody, so nothing here ever has to learn that pots exist.
     *
     * <p>The identifier rather than the account, like every other answer this module gives about an
     * account somebody else is going to use: a module that handed out its entities to be read
     * elsewhere would have no boundary left to speak of.
     *
     * <p>There is deliberately no current account beside it. A pot is paid into from its members'
     * own current accounts, and an account of its own would be money belonging to nobody with no way
     * in and no way out.
     */
    @Transactional
    public long openASavingsAccountNobodyHolds() {
        SavingsAccount account = savingsAccounts.save(SavingsAccount.heldByNobody());
        // On a product like any other account, and for the same reason: instant access is exactly
        // how an account behind a pot already behaves, and one with no agreement would be one no
        // rule could answer about. Nobody holding it changes nothing here — an agreement belongs to
        // the account rather than to a customer, which is why this is not the second migration in
        // this application to have to leave the holderless accounts behind.
        whatAnAccountIsPutOn.aSavingsAccountWasOpened(account.getId());
        // One line per account opened this way, because an account with no holder is the one thing
        // in this database a reviewer cannot explain from the customer it belongs to: every sweep
        // that walks the accounts will pass over it, and this is the line that says where it came
        // from.
        log.info("savings account opened with no holder savingsAccountId={}", account.getId());
        return account.getId();
    }

    /**
     * Opens another savings account for a customer who already banks here, on the savings product
     * they chose, and answers with its identifier.
     *
     * <p><strong>A customer may hold as many savings accounts as they open, on as many different
     * products.</strong> That is the whole of this method's rule and it is a rule by omission:
     * nothing here asks whether they already hold one, whether they already hold one on this
     * product, or how many is too many. Money kept where it can be reached tomorrow beside money
     * locked away for a year is the reason the catalogue exists, and an account per product is how
     * a saver does that. The one thing a second account is not is a second customer — the points,
     * the week and the run of weeks are all theirs and read the same beside every account they
     * hold.
     *
     * <p><strong>The product travels through this module without being read.</strong> It is a word
     * that arrived on a request and is handed to whoever keeps the record of what accounts are
     * living under; this class does not know what the bank sells, cannot tell a code from a typo,
     * and deliberately does not look. {@link WhoeverRecordsWhatASavingsAccountIsOn} argues the whole
     * of that, including why the refusal comes back out of the announcement rather than being asked
     * about first.
     *
     * <p><strong>One transaction, so a refused choice leaves nothing behind.</strong> The row is
     * written, the choice is announced, and a product the bank does not sell — or one closed to new
     * accounts — rolls both back together. What the customer is left holding is exactly what they
     * held before they pressed the button.
     *
     * <p>Nothing at all for a customer nobody has heard of, rather than a refusal of this module's
     * own: that is the same absence {@link #accountsOf} answers with and the same one the caller is
     * already wording for this customer elsewhere on the same screen. An identifier that belongs to
     * nobody is a thing that is not there, and this module has one sentence for that —
     * {@link #noSuchCustomer} — rather than two.
     *
     * <p>There is deliberately no current account beside it, and no opening balance in it. A second
     * savings account is somewhere else to put money that is already in this bank; a customer opens
     * one to move money, and money that appeared in it would be money nobody saved.
     *
     * @param theProductChosen what the customer chose, as they typed it and unread here
     * @return the account that now exists, or nothing at all when no customer banks under that
     *         identifier
     */
    @Transactional
    public Optional<Long> openASavingsAccountFor(long customerId, String theProductChosen) {
        log.debug("another savings account asked for customerId={} product={}",
                customerId, theProductChosen);
        Optional<Customer> customer = customers.findById(customerId);
        if (customer.isEmpty()) {
            // Not a refusal of this module's own, and logged all the same: a caller asking to open
            // an account for somebody who is not here is the sort of thing a reviewer wants to find
            // by grepping rather than to deduce from a 404 in an access log.
            log.warn("another savings account was not opened customerId={} product={} "
                    + "reason={}", customerId, theProductChosen, noSuchCustomer(customerId));
            return Optional.empty();
        }
        SavingsAccount opened = savingsAccounts.save(new SavingsAccount(customer.get()));
        // And the agreement the choice writes, in this same transaction. This is the line that can
        // refuse — a product nobody sells, a product closed to new accounts — and the account above
        // goes back with it when it does.
        whatAnAccountIsPutOn.aSavingsAccountWasOpenedOn(opened.getId(), theProductChosen);
        // One line per account opened this way, with the word that was chosen beside the account it
        // wrote: "why is this account on that product" is answerable from this line and the
        // agreement line that follows it, without either of them naming the other's business.
        log.info("another savings account opened customerId={} savingsAccountId={} product={}",
                customerId, opened.getId(), theProductChosen);
        return Optional.of(opened.getId());
    }

    /**
     * Who holds the given savings account, or nothing at all if there is no such account — or if
     * nobody holds it.
     *
     * <p>The holder rather than the customer record: whoever asks wants to say whose account this is
     * and to name the customer whose points a deposit into it earns, and a module that hands out its
     * entities to be read elsewhere has no boundary left to speak of.
     *
     * <p><strong>An account nobody holds answers "nobody" rather than throwing</strong>, and it
     * answers it in the same empty the absent account gets. The two are not told apart here on
     * purpose: every caller of this method already has to say what it does when there is no holder
     * to name — credit nobody's points, address nobody's notification, refuse — and every one of
     * those answers is the right one for an account that belongs to a pot as well. A third case
     * would be a distinction none of them has anything different to do about.
     */
    @Transactional(readOnly = true)
    public Optional<AccountHolder> holderOfSavingsAccount(long savingsAccountId) {
        return savingsAccounts.findHolderById(savingsAccountId);
    }

    /**
     * Every savings account there is, by identifier, in the order they were opened.
     *
     * <p>For a nightly sweep, which has nobody's account in front of it and has to walk them all.
     * Identifiers rather than accounts or holders: whoever sweeps asks this module who holds each
     * one as it gets to it, and a module that handed out its entities to be read elsewhere would
     * have no boundary left to speak of.
     *
     * <p>All of them on every call. With the seeded customers that is three rows, and with a real
     * population a sweep would want to be handed them a page at a time — which is a change to make
     * when there is a population, not before.
     */
    @Transactional(readOnly = true)
    public List<Long> everySavingsAccount() {
        return savingsAccounts.findEveryId();
    }

    /**
     * Every current account there is, by identifier, in the order they were opened.
     *
     * <p>The outbound twin of {@link #everySavingsAccount}, and for the same caller: a nightly sweep
     * has nobody's account in front of it and has to walk them all. Bills, arrears and a declared
     * income are facts about a current account, so the sweep that has something to say about them
     * walks current accounts rather than savings ones — an account with bills and no savings account
     * at all would otherwise never be looked at.
     */
    @Transactional(readOnly = true)
    public List<Long> everyCurrentAccount() {
        return currentAccounts.findEveryId();
    }

    /** Whether there is a customer with this identifier at all, for callers that only ask. */
    @Transactional(readOnly = true)
    public boolean customerExists(long customerId) {
        return customers.existsById(customerId);
    }

    /** Whether there is a savings account with this identifier at all, for callers that only ask. */
    @Transactional(readOnly = true)
    public boolean savingsAccountExists(long savingsAccountId) {
        return savingsAccounts.existsById(savingsAccountId);
    }

    /**
     * How to tell somebody that the savings account they named is not there, in words they can act
     * on. Every module that answers for a savings account has to say this at some point — reading
     * one, paying into one, spending what one has earned — and three modules wording it three ways
     * is three sentences that will drift apart. Accounts owns what a savings account is, so it owns
     * what its absence is called.
     *
     * <p>A sentence rather than a refusal, because who refuses and how it is reported differ: each
     * caller wraps this in its own module's refusal, and the web layer decides the status.
     */
    public static String noSuchSavingsAccount(long savingsAccountId) {
        return "There is no savings account " + savingsAccountId + ".";
    }

    /**
     * The same for a current account, which money moves into as well as out of. Both directions of a
     * transfer have to be able to say this, and a sentence written out in each of them is two
     * sentences one rewording away from disagreeing about what absence sounds like.
     */
    public static String noSuchCurrentAccount(long currentAccountId) {
        return "There is no current account " + currentAccountId + ".";
    }

    /**
     * And for a customer, which anything kept per customer has to be able to say. Points are kept
     * that way and rewards are claimed that way, so the sentence would otherwise be written out in
     * the Rewards module as well as here.
     */
    public static String noSuchCustomer(long customerId) {
        return "There is no customer " + customerId + ".";
    }

    /**
     * And for contact details nobody banks under, which anything that finds a customer the way
     * signing in does has to be able to say. Signing in says it about the address somebody typed
     * about themselves, and a gift says it about the address they typed about somebody else — the
     * same objection, and worth being the same sentence.
     *
     * <p>The address is deliberately not named back. This application knows who exists and not who
     * is typing, so an answer that echoed the address would be telling whoever asked slightly more
     * about who banks here than they gave it.
     */
    public static String noCustomerBanksUnderThoseContactDetails() {
        return "No customer banks here under that email address.";
    }

    /**
     * How the two accounts a transfer would run between stand to each other: whether each one is
     * real, and whether one customer holds both. Money moves only across the last of those, because
     * a savings account is funded from its own holder's money and from nobody else's.
     *
     * <p>Every way the pair can be wrong comes back distinguished, so the caller can say which it
     * was rather than only that something was. Whoever asks decides what to do about it; this module
     * only knows who holds what.
     *
     * <p><strong>A savings account no customer holds is a pairing of its own</strong>, and this
     * module does not decide it. Such an account belongs to something that is not a person — a
     * shared pot — and who may pay into it is that thing's rule: the payer is looked up here,
     * because who holds a current account is this module's answer, and the pair is then put to
     * {@link WhoMayPayIntoAnAccountNobodyHolds}, which answers in the same vocabulary. Nothing in
     * this class learns what a pot is, and nothing outside it has to ask two questions to find out
     * whether money may move.
     *
     * <p>The account's existence is asked separately in that case and only in that case. An empty
     * holder used to mean "there is no such account", and now means either that or an account
     * belonging to something else; one extra query on the path that used to be a flat refusal is the
     * price of telling the two apart, and the ordinary pairing still costs the two reads it always
     * did.
     */
    @Transactional(readOnly = true)
    public AccountPairing pairingFor(long savingsAccountId, long currentAccountId) {
        Optional<Long> saver = savingsAccounts.findHolderIdById(savingsAccountId);
        if (saver.isEmpty()) {
            return pairingWithAnAccountNoCustomerHolds(savingsAccountId, currentAccountId);
        }
        Optional<Long> payer = currentAccounts.findHolderIdById(currentAccountId);
        if (payer.isEmpty()) {
            return AccountPairing.NO_SUCH_CURRENT_ACCOUNT;
        }
        return saver.equals(payer)
                ? AccountPairing.HELD_BY_ONE_CUSTOMER
                : AccountPairing.HELD_BY_DIFFERENT_CUSTOMERS;
    }

    /**
     * The same question about an account whose holder is empty: is there such an account at all,
     * whose current account is this, and may that customer pay into whatever holds it.
     *
     * <p>The missing savings account is ruled on before the missing current account, so that the two
     * halves of {@link #pairingFor} answer a pair of made-up identifiers in the same order and with
     * the same sentence. A caller that sent nonsense for both is told about the one in its path.
     */
    private AccountPairing pairingWithAnAccountNoCustomerHolds(long savingsAccountId,
                                                               long currentAccountId) {
        if (!savingsAccounts.existsById(savingsAccountId)) {
            return AccountPairing.NO_SUCH_SAVINGS_ACCOUNT;
        }
        Optional<Long> payer = currentAccounts.findHolderIdById(currentAccountId);
        if (payer.isEmpty()) {
            return AccountPairing.NO_SUCH_CURRENT_ACCOUNT;
        }
        AccountPairing pairing = whoMayPayIn.pairingWith(savingsAccountId, payer.get());
        // What this module contributed to the decision and what it was told, on one line, because
        // the two halves are in different modules and a reader tracing "why was my deposit
        // refused" would otherwise have to guess which of them said no. The line that explains the
        // answer itself — which pot, what role — is logged where that is known.
        log.debug("pairing with a savings account no customer holds savingsAccountId={} "
                        + "currentAccountId={} payingCustomerId={} pairing={}",
                savingsAccountId, currentAccountId, payer.get(), pairing);
        return pairing;
    }

    /**
     * Takes an amount out of a current account, and answers whether there was enough to take.
     * Nothing leaves unless the whole amount can.
     *
     * <p>Taken rather than checked and then taken. "Can this account afford it" and "take it" as two
     * questions is a gap between them, and the gap is where the same money gets spent twice; asked
     * as one, the balance that was tested is the balance that changed.
     *
     * <p>Why it could not be taken is not reported: the caller knows which account it named, and
     * {@link #balanceOfCurrentAccount} says what is in it. What to tell the person who asked is for
     * whoever was moving the money, who is the only one who knows what they were moving it for.
     *
     * @throws IllegalArgumentException if asked to withdraw nothing or less, which is a mistake in
     *                                  the caller rather than a refusal to report to anybody
     */
    @Transactional
    public boolean withdrawFrom(long currentAccountId, BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "an amount to withdraw has to be more than zero, was " + amount.toPlainString());
        }
        return currentAccounts.findById(currentAccountId)
                .map(account -> {
                    boolean taken = account.withdraw(amount);
                    if (taken) {
                        // The account is managed and would be written out at the end of the
                        // transaction anyway. Saying so leaves nothing for a reader to infer from
                        // Hibernate's behaviour.
                        currentAccounts.save(account);
                    }
                    return taken;
                })
                .orElse(false);
    }

    /**
     * Puts money into a current account after another module has established that it belongs to the
     * customer making the transfer. The pairing check and the movement rule belong to that module;
     * Accounts only keeps this account's stored balance truthful.
     */
    @Transactional
    public void depositInto(long currentAccountId, BigDecimal amount) {
        CurrentAccount account = currentAccounts.findById(currentAccountId)
                .orElseThrow(() -> new IllegalArgumentException("no current account " + currentAccountId));
        account.deposit(amount);
        currentAccounts.save(account);
    }

    /** What is in a current account, or nothing at all if there is no such account. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> balanceOfCurrentAccount(long currentAccountId) {
        return currentAccounts.findById(currentAccountId).map(CurrentAccount::getBalance);
    }

    /**
     * What the given customer holds, or nothing at all if there is no such customer. The two cases
     * the caller has to tell apart are exactly these: a customer nobody has heard of, and a customer
     * who happens to hold no accounts — the second is an answer, and comes back with empty lists.
     *
     * <p>An account nobody holds is in nobody's list, which is how a shared pot's savings account
     * stays out of the personal screens of the customer who opened the pot: their balance, their
     * goals and their totals go on meaning what they meant before, without any of those screens
     * having to know that pots exist.
     */
    @Transactional(readOnly = true)
    public Optional<CustomerAccounts> accountsOf(long customerId) {
        if (!customers.existsById(customerId)) {
            return Optional.empty();
        }
        return Optional.of(new CustomerAccounts(
                currentAccounts.findByCustomerId(customerId),
                savingsAccounts.findByCustomerId(customerId)));
    }

    /** Whether there is a current account with this identifier at all, for callers that only ask. */
    @Transactional(readOnly = true)
    public boolean currentAccountExists(long currentAccountId) {
        return currentAccounts.existsById(currentAccountId);
    }

    /**
     * One current account as its holder finds it — what it is called, what is in it, whose it is —
     * or nothing at all if there is no such account.
     *
     * <p>For the screen that belongs to the account rather than for the directory that lists it.
     * {@link #accountsOf} answers "what do I hold" and is the wrong read for a page about one
     * account: it loads every account the customer has so that a caller can pick a row out of it,
     * and it cannot tell an account that is not there from an account that is not this customer's.
     */
    @Transactional(readOnly = true)
    public Optional<WhatACurrentAccountHolds> currentAccountWith(long currentAccountId) {
        return currentAccounts.findWhatItHoldsById(currentAccountId);
    }

    /**
     * What the holder of this current account says lands in it every month, or that they have said
     * nothing.
     *
     * <p>Answered rather than left to an absence, because "nobody has declared one" is an answer
     * about an account that exists and a page has something to draw for it: the boxes to type one
     * into. It is the same reading {@code SavingCapacityOnAnAccount} gives, and here the difference
     * between the two states is money — an account with no declaration receives nothing from the
     * nightly job and behaves exactly as current accounts behaved before this existed.
     *
     * <p>The next payday comes with it, worked out through {@link WhenIncomeIsDue} off the
     * application's clock, so that the day a page shows and the day the job credits cannot be two
     * different readings of "the 31st".
     */
    @Transactional(readOnly = true)
    public DeclaredIncome monthlyIncomeOn(long currentAccountId) {
        return monthlyIncomes.findByCurrentAccountId(currentAccountId)
                .map(this::asDeclared)
                .orElseGet(() -> DeclaredIncome.notDeclaredOn(currentAccountId));
    }

    /**
     * Which days a salary <strong>actually landed</strong> in this current account between those two
     * days, both ends included, oldest first.
     *
     * <p><strong>The record, not the calendar.</strong> A declared income says which day of the
     * month its holder expects to be paid on; it says nothing about whether they were. The two part
     * company the moment anything moves — a declaration made after a month has already gone by, one
     * withdrawn and made again, one whose day was corrected, or simply a job nobody ran on a wound
     * clock — and a reader that works the days out from the day-of-the-month alone is answering a
     * question about money from a question about the calendar. {@link IncomePaid} is the only record
     * of what landed, it is unique per account per payday, and this is how the rest of the
     * application reads it.
     *
     * <p>It exists for {@code automation}: a rule that fires on payday fires on the days its
     * holder's income lands, so its days are whatever this answers and nothing else. The alternative
     * — deriving them from {@link DeclaredIncome#dayOfMonth} and walking the rule's own cursor — is
     * the same mistake this module was itself sent back for in {@code dueInMonthsNotAlreadyPaid},
     * seen from the outside: two cursors that nothing reconciles, and money moving on mornings
     * nobody was paid.
     *
     * <p>Days rather than rows, and no amount with them, for the reason
     * {@code IncomePaidRepository.whichPaydaysWereCreditedBetween} gives: the day is the whole of
     * what a caller has to know, and what an account holds is the account's own balance. Reading it
     * does not leak {@link IncomePaid}, which stays package-private like every row in this module.
     *
     * <p><strong>Asked by when the money was credited, and answered with the days it was due
     * for.</strong> A caller with a cursor of its own has to be able to find a salary that landed
     * <em>late</em> — after its cursor had already gone past the day that salary was due. That
     * happens whenever the two jobs run out of the order the night runs them in, which is every
     * time a trainer types the job names in the other order, and it happens after downtime. Asked
     * instead for the days due in a stretch, a late credit is behind the cursor the moment it is
     * written, is never fetched, and the rule waiting on it loses that month for good — money the
     * customer asked to have saved, sitting in their current account, with nothing anywhere saying
     * why. So the question is "what have you credited since I last looked", and the answer is the
     * days those credits were for.
     *
     * <p>A stretch in one query rather than a question per day, so that a clock wound three years
     * forward is one query per rule and not a thousand.
     *
     * @param creditedAfter   the moment the caller last looked, excluded
     * @param creditedThrough the moment it is looking as at, included — an empty answer for a
     *                        stretch that runs backwards, which is what "nothing was credited in no
     *                        time at all" means
     */
    @Transactional(readOnly = true)
    public List<LocalDate> paydaysCreditedBetween(long currentAccountId, Instant creditedAfter,
                                                  Instant creditedThrough) {
        return incomePaid
                .whichPaydaysWereCreditedBetween(currentAccountId, creditedAfter, creditedThrough)
                .stream()
                .sorted()
                .toList();
    }

    /**
     * Declares what lands in a current account every month, or declares it again, and answers with
     * the income as the account now reports it.
     *
     * <p>Replaced rather than appended to. A second declaration is the customer saying what they are
     * paid now, not a second salary, so there is one row per account and changing it changes the
     * figure — which is also what makes the endpoint a PUT.
     *
     * <p>Three things are refused and nothing else: an amount of nothing or less, an amount carrying
     * more than two decimal places, and a day outside 1 to 31. The first two are
     * {@code AmountOfMoney}'s rule word for word, quoted rather than restated so that the objection a
     * customer meets here is the objection they have already met on a deposit; the third is this
     * module's, because a day of the month is not an amount of anything. The day is kept exactly as
     * the customer said it — the 31st stays the 31st — and which day a short month lands it on is
     * worked out on every run rather than baked in here.
     *
     * <p>The cursor is not moved by a change, and {@link MonthlyIncome} argues why: a customer
     * correcting their salary is not asking to be paid again for the months already settled, nor to
     * have a payday that fell yesterday quietly swallowed.
     *
     * @throws MonthlyIncomeRefused if that is not an income this application will keep
     */
    @Transactional
    public DeclaredIncome declareMonthlyIncome(long currentAccountId, int dayOfMonth,
                                               BigDecimal amount) {
        Optional<MonthlyIncome> alreadyDeclared = monthlyIncomes.findByCurrentAccountId(currentAccountId);
        String was = alreadyDeclared
                .map(income -> "day " + income.getDayOfMonth() + " for EUR "
                        + AmountOfMoney.asMoney(income.getAmount()))
                .orElse("nothing");
        log.debug("monthly income asked for currentAccountId={} dayOfMonth={} amount={} was={}",
                currentAccountId, dayOfMonth, amount, was);
        refuseUnlessAnIncome(currentAccountId, dayOfMonth, amount);

        BigDecimal figure = AmountOfMoney.quotedToTheCent(amount);
        Instant declaredAt = clock.instant();
        MonthlyIncome income = alreadyDeclared.orElseGet(
                () -> new MonthlyIncome(currentAccountId, dayOfMonth, figure, declaredAt));
        income.redeclare(dayOfMonth, figure, declaredAt);
        monthlyIncomes.save(income);

        DeclaredIncome now = asDeclared(income);
        log.info("monthly income declared currentAccountId={} dayOfMonth={} amount={} "
                        + "nextPayday={} settledThrough={} was={}",
                currentAccountId, now.dayOfMonth(), AmountOfMoney.asMoney(now.amount()),
                now.nextPayday(), income.getPaidThrough(), was);
        return now;
    }

    /**
     * Takes the declaration away, and answers what the account now says — which is that nobody has
     * declared anything.
     *
     * <p>After which the nightly job credits that account nothing, because there is no row for it to
     * walk. What has already been credited stays credited: the money is in the account and
     * {@link IncomePaid} is the record of how it got there, and unpicking a salary somebody was paid
     * because they later said they are no longer paid it would be a balance nobody could explain.
     *
     * <p>Withdrawing a declaration nobody made is accepted quietly rather than refused, for the
     * reason a pause pressed twice is: the customer asked for there to be no income on this account
     * and there is none. It is said in the log, which is where the difference between the two is
     * worth having.
     */
    @Transactional
    public DeclaredIncome withdrawMonthlyIncome(long currentAccountId) {
        long taken = monthlyIncomes.deleteByCurrentAccountId(currentAccountId);
        if (taken == 0) {
            log.info("monthly income withdrawn currentAccountId={} declarationsRemoved=0 "
                    + "note=nothing had been declared", currentAccountId);
        } else {
            log.info("monthly income withdrawn currentAccountId={} declarationsRemoved={}",
                    currentAccountId, taken);
        }
        return DeclaredIncome.notDeclaredOn(currentAccountId);
    }

    /**
     * The bills standing against this current account, in the order the customer declared them.
     *
     * <p>Standing only. An ended bill is a record rather than an instruction and has no place in a
     * list of what is about to go out; it is still readable through {@link #endedBillsOn}, which is
     * what keeps "what did I used to pay" answerable.
     */
    @Transactional(readOnly = true)
    public List<ADeclaredBill> billsOn(long currentAccountId) {
        List<ADeclaredBill> standing = asDeclared(recurringBills
                .findByCurrentAccountIdAndStateInOrderByIdAsc(currentAccountId,
                        BillState.theOnesStillStanding()));
        log.debug("bills read currentAccountId={} standing={}", currentAccountId, standing.size());
        return standing;
    }

    /**
     * How far the nightly run has already settled each bill standing against this current account:
     * the bill's identifier against the moment through which its due dates are done with.
     *
     * <p><strong>The narrowest fact that answers one question, and the question is "what is this
     * account still about to be asked for".</strong> A forecast that walks forward from today has to
     * know whether tonight's run still owes a due date that has already fallen — on a wound clock it
     * always does, because {@code MovableClock} moves in whole calendar days and the cron never fires
     * for the days it skipped — and, the other way round, whether this morning's rent has already
     * gone. Assuming either costs a projection a month's rent in one direction or the other.
     *
     * <p><strong>Not on {@link ADeclaredBill}, and that record says why in its own words.</strong>
     * Which due dates have been settled is the application's bookkeeping rather than the customer's
     * rent, and a page that showed it would be showing somebody their database. A caller that needs
     * it is predicting the run rather than drawing a bill, so it asks for it on its own and is handed
     * nothing else — the shape {@code DepositsService.mostEverSavedBy} set for a figure wanted by one
     * caller and by nobody looking at a screen.
     *
     * <p>It is not {@code lastTakenOn} and cannot be derived from it: the cursor moves past a date
     * that was presented and could not be paid, and that date is then an arrear rather than a
     * payment. What is still owed is {@link #arrearsOn}'s answer and is a different question from
     * this one.
     *
     * <p>Standing bills only, which is every bill {@link #billsOn} answers with. An ended bill is a
     * record rather than an instruction and has nothing left to fall due.
     */
    @Transactional(readOnly = true)
    public Map<Long, Instant> howFarEachBillOnAnAccountIsSettled(long currentAccountId) {
        Map<Long, Instant> settled = new LinkedHashMap<>();
        for (RecurringBill bill : recurringBills.findByCurrentAccountIdAndStateInOrderByIdAsc(
                currentAccountId, BillState.theOnesStillStanding())) {
            settled.put(bill.getId(), bill.getSettledThrough());
        }
        log.debug("how far each bill is settled currentAccountId={} bills={} settled={}",
                currentAccountId, settled.size(), settled);
        return settled;
    }

    /**
     * How far the nightly run has already credited the salary declared against this current account:
     * the moment through which its paydays are done with, and null when nobody has declared one.
     *
     * <p>The other half of {@link #howFarEachBillOnAnAccountIsSettled}, for the same caller and for
     * the same reason, and {@link DeclaredIncome} keeps it out of itself in the same words: which
     * paydays have been settled is how the job keeps itself bounded rather than anything the holder
     * of the account said about their salary.
     *
     * <p>Null rather than a moment for an account nobody has declared an income against, which is the
     * distinction that record exists to make. Nothing has been settled because nothing is ever due,
     * and a moment here would invite a caller to treat the two as the same thing.
     */
    @Transactional(readOnly = true)
    public Instant howFarTheIncomeOnAnAccountIsPaid(long currentAccountId) {
        Instant paidThrough = monthlyIncomes.findByCurrentAccountId(currentAccountId)
                .map(MonthlyIncome::getPaidThrough)
                .orElse(null);
        log.debug("how far the income is paid currentAccountId={} paidThrough={}",
                currentAccountId, paidThrough);
        return paidThrough;
    }

    /** What was ended, oldest first, so that a bill closed rather than deleted stays readable. */
    @Transactional(readOnly = true)
    public List<ADeclaredBill> endedBillsOn(long currentAccountId) {
        List<ADeclaredBill> ended = asDeclared(recurringBills
                .findByCurrentAccountIdAndStateInOrderByIdAsc(currentAccountId,
                        List.of(BillState.ENDED)));
        log.debug("ended bills read currentAccountId={} ended={}", currentAccountId, ended.size());
        return ended;
    }

    /**
     * That one bill, if it is on that current account, as the rest of the application reads it.
     *
     * <p>An {@link Optional} rather than a refusal, unlike every other read that names a single
     * bill: the caller is another module deciding what to do about a bill it was handed an
     * identifier for, and what it says when there is no such bill is its own sentence to write in
     * its own words. {@link #historyOfBill} refuses instead because it has an answer to give and
     * cannot give one.
     *
     * <p>Scoped by the account, so a bill identifier somebody guessed answers as a bill that is not
     * there rather than with somebody else's rent — the same answer, for the same reason, that every
     * other read of a single bill gives.
     *
     * <p>Answered for an ended bill as well as for a standing one. Whether a bill that is over may
     * still be argued about is the asking module's rule rather than this one's, and it needs the
     * state to decide: the budgets module keeps an ended bill's category readable and refuses to
     * change it, which it could not do if this read pretended the bill had gone.
     *
     * <p>It teaches this module nothing. What a caller does with the record — hangs a category off
     * it, counts it into a month, draws it on a page — is entirely theirs, and the dependency runs
     * one way because of exactly that.
     */
    @Transactional(readOnly = true)
    public Optional<ADeclaredBill> billOn(long currentAccountId, long billId) {
        return recurringBills.findByIdAndCurrentAccountId(billId, currentAccountId)
                .map(this::asDeclared);
    }

    /**
     * Every date one bill has fallen due on and what became of each, newest first.
     *
     * <p>The half of a bill's story the balance cannot tell. A current account keeps a figure rather
     * than a sum of records, so nothing about a balance says which bills moved it — and a date that
     * was presented against an empty account moved nothing at all and would leave no trace anywhere
     * else. Both are here, because "my rent did not go out in March" is exactly the question a
     * customer brings to this list.
     *
     * <p>Readable for a bill that has been ended as well as for one still standing, which is the
     * whole reason ending is a closing rather than a deletion: the months it was taken for stay
     * explained.
     *
     * @throws RecurringBillRefused if there is no such bill on that account
     */
    @Transactional(readOnly = true)
    public List<ABillThatFellDue> historyOfBill(long currentAccountId, long billId) {
        RecurringBill bill = theBillOn(currentAccountId, billId);
        List<ABillThatFellDue> history = billsSettled
                .findByRecurringBillIdOrderByDueOnDescIdDesc(bill.getId())
                .stream()
                .map(settled -> ABillThatFellDue.of(settled,
                        AmountOfMoney.quotedToTheCent(settled.getAmount())))
                .toList();
        log.debug("bill history read currentAccountId={} billId={} name={} dueDatesRecorded={}",
                currentAccountId, billId, bill.getName(), history.size());
        return history;
    }

    /**
     * What this account still owes, oldest first: every date a bill fell due on and was not paid and
     * has not been settled since.
     *
     * <p><strong>The section this whole feature exists to put in front of a customer.</strong> A
     * bill that could not be paid is not waived and not forgotten — it stays owed, it is presented
     * again ahead of anything newly due on every later run, and it accumulates until the customer
     * does something about it. This is the list that says how big the hole is, and the way out of it
     * is the withdrawal from savings this application already has.
     *
     * <p><strong>Oldest first, which is also the order the nightly run settles them in.</strong> One
     * order, read off the same rows, so that the list a customer is looking at is the queue their
     * money will actually go into rather than a second opinion about it.
     *
     * <p>How late each one is, is counted against now rather than against the moment it was last
     * presented — {@link AnArrear} says why at length — which is why this read takes the clock. It
     * is a figure about today and it changes every day the debt is carried.
     *
     * <p><strong>A date owed by a bill that has since been ended is still owed.</strong> Ending a
     * bill stops it being presented again; it does not waive money that was already owed, and an
     * application where it did would make ending a bill the way out of a debt and delete the lesson.
     * The name comes off the bill either way, because a list of identifiers is a list nobody can act
     * on.
     *
     * <p>Empty when nothing is owed, and the page draws no section at all for an empty list rather
     * than a panel reading nought. A panel that is there every day is a panel people learn to
     * ignore, and this one has something to say exactly when it appears.
     */
    @Transactional(readOnly = true)
    public List<AnArrear> arrearsOn(long currentAccountId) {
        List<BillOccurrence> owed = billsSettled
                .findByCurrentAccountIdAndOutcomeOrderByDueOnAscIdAsc(currentAccountId,
                        BillOutcome.UNPAID);
        if (owed.isEmpty()) {
            log.debug("arrears read currentAccountId={} owed=0", currentAccountId);
            return List.of();
        }
        Instant asAt = clock.instant();
        Map<Long, String> names = whatEachOfTheseBillsIsCalled(owed);
        List<AnArrear> arrears = owed.stream()
                .map(unpaid -> AnArrear.of(unpaid, names.get(unpaid.getRecurringBillId()),
                        AmountOfMoney.quotedToTheCent(unpaid.getAmount()), asAt))
                .toList();
        log.debug("arrears read currentAccountId={} owed={} amount={} oldestDueOn={} "
                        + "daysLateOnTheOldest={} asAt={}",
                currentAccountId, arrears.size(), AmountOfMoney.asMoney(totalOf(arrears)),
                arrears.get(0).dueOn(), arrears.get(0).daysLate(), asAt);
        return arrears;
    }

    /**
     * What this current account has to cover between today and this day next month: what is in it,
     * what is due to arrive, what is due to leave, and what that leaves.
     *
     * <p><strong>The decision support this feature exists to give.</strong> It is asked before a
     * customer decides how much to sweep into savings, which is the only moment at which knowing
     * what the month has to cover changes anything.
     *
     * <p>Every figure is derived here so that the subtraction is made once: {@code leavesYou} is the
     * balance plus what is due in minus what is due out, to the cent. {@link TheMonthAhead} argues
     * out why the arrears are inside {@code billsDue} and why the answer is allowed to be negative.
     *
     * <p>The bills come out of {@link #whatTheBillsWillTake}, which shares {@link #theDatesToCome}
     * with the year-ahead bar, so the month ahead and the year ahead quote the same calendar over a
     * shorter window, and both quote the one the nightly run uses.
     * The income comes out of {@link WhenIncomeIsDue} counted from the account's own cursor, for
     * exactly the reason the bills are counted from theirs: a salary the 01:00 run still owes is
     * money about to arrive, and a forecast that clamped its bottom to today would leave it out and
     * then watch the run credit it.
     *
     * <p>How far the window reaches is {@link TheMonthAhead#closesOn}'s answer alone, and that is
     * the one place in this read where a month shorter than this one has to be thought about: both
     * figures above are counted up to the day it names, so a window a day short of a month is a
     * month with a rent and a salary missing from it.
     *
     * <p>In one read transaction by whoever calls it — the account's own endpoint reads it beside
     * the balance and the bills — so that every figure on the page describes one instant of the
     * ledger.
     */
    @Transactional(readOnly = true)
    public TheMonthAhead theMonthAheadOn(long currentAccountId) {
        Instant now = clock.instant();
        LocalDate from = TheMonthAhead.opensOn(now);
        LocalDate until = TheMonthAhead.closesOn(from);
        BigDecimal balance = balanceOfCurrentAccount(currentAccountId).orElse(BigDecimal.ZERO);
        BigDecimal incomeDue = whatIsDueToArriveBy(currentAccountId, from, until);
        List<ABillToCome> billsToCome = whatTheBillsWillTake(currentAccountId, from, until);
        BigDecimal stillToFall = billsToCome.stream()
                .map(ABillToCome::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal arrearsOutstanding = totalOf(arrearsOn(currentAccountId));
        BigDecimal billsDue = stillToFall.add(arrearsOutstanding);
        BigDecimal leavesYou = balance.add(incomeDue).subtract(billsDue);
        // The window this was built over and everything that decided the four figures, because a
        // customer who says the sum on their screen is wrong is otherwise a customer somebody has to
        // reconstruct a calendar for. The dates are counted rather than listed: the same line for
        // the year ahead lists them, and this one runs on every visit to the page.
        log.debug("the month ahead currentAccountId={} between={}..{} balance={} incomeDue={} "
                        + "billDatesToCome={} billsStillToFall={} arrearsOutstanding={} "
                        + "billsDue={} leavesYou={} asAt={}",
                currentAccountId, from, until, AmountOfMoney.asMoney(balance),
                AmountOfMoney.asMoney(incomeDue), billsToCome.size(),
                AmountOfMoney.asMoney(stillToFall), AmountOfMoney.asMoney(arrearsOutstanding),
                AmountOfMoney.asMoney(billsDue), AmountOfMoney.asMoney(leavesYou), now);
        return new TheMonthAhead(from, until, AmountOfMoney.quotedToTheCent(balance),
                AmountOfMoney.quotedToTheCent(incomeDue), AmountOfMoney.quotedToTheCent(billsDue),
                AmountOfMoney.quotedToTheCent(arrearsOutstanding),
                AmountOfMoney.quotedToTheCent(leavesYou));
    }

    /**
     * Every date the bills standing on this current account will fall due on, from wherever each of
     * them is settled through up to and including the given day, oldest first.
     *
     * <p><strong>Derived on every read and stored nowhere</strong>, and out of the very calendar the
     * nightly run walks: {@link WhenABillIsDue#dueDatesBetween} answers the dates, and the same
     * question the run asks the record — has this bill already been presented for that month —
     * takes out the ones that are settled. So the forecast and the run can never disagree about
     * which dates a bill falls on, which is the promise this read is under and the one a trainer
     * checks by winding the clock a year and counting the debits.
     *
     * <p><strong>Counted from each bill's own cursor and never from today.</strong> That is the
     * whole of not reproducing the defect the saving rules' forecast still carries: clamping the
     * bottom of the window to the start of yesterday hides every date the next run still owes, and
     * on a wound clock <em>every</em> bill is behind its cursor, because the clock moves in whole
     * calendar days and the cron never fires for the ones it skipped. Those dates come back at the
     * head of the list, marked as owed rather than still to come.
     *
     * <p>Standing bills only. An ended bill is a record rather than an instruction, so it leaves
     * this forecast the moment it is ended — while every date it was actually taken on stays in its
     * history and in the ledger, which is the whole reason ending is a closing rather than a
     * deletion.
     *
     * <p>What it costs: one calendar walk and one question to the record per standing bill, capped
     * by the twenty bills an account may carry. Making it cheaper would mean storing the dates,
     * which is the projection {@link TheMonthAhead} and every other preview in this codebase refuse
     * to store.
     *
     * <p>Private, and handed the window rather than reading the clock for itself, because the one
     * caller it has — {@link #theMonthAheadOn} — has already read the clock once to place the
     * balance, the income and the arrears. A second read of the same clock is a second answer to
     * "what day is it" inside one screenful of figures, and a screen drawn across midnight would
     * quote a window it had not counted the bills over.
     *
     * @param from  the day the window opens, which is what says whether a date is still to come or
     *              is one the nightly run already owes
     * @param until the last day the window holds, inclusive — a date falling on it is in the answer
     */
    private List<ABillToCome> whatTheBillsWillTake(long currentAccountId, LocalDate from,
                                                   LocalDate until) {
        return theDatesToCome(recurringBills.findByCurrentAccountIdAndStateInOrderByIdAsc(
                currentAccountId, BillState.theOnesStillStanding()), from, until);
    }

    /**
     * The same forecast for every current account one customer holds, merged into one list in date
     * order.
     *
     * <p>For the year-ahead screen, which belongs to a savings account and is about what the
     * customer's money is going to do: the bills that compete with a sweep are the ones on the
     * current accounts the sweep draws from, and a household with two of them has claims on both.
     * By the customer rather than by the rules standing on the account, because the question is
     * asked hardest by somebody who has written no rule yet — deciding how much to sweep is exactly
     * what this is for, and a forecast that appeared only once a rule existed would arrive after the
     * decision it exists to inform.
     *
     * <p>A customer nobody has heard of is an empty list rather than a refusal, for the reason the
     * timeline gives about an account it has never seen: whoever asked has already asked the
     * question this module can answer about whose an account is.
     *
     * <p><strong>Handed both ends of the window rather than reading the clock for itself</strong>,
     * exactly as the month-ahead sibling {@link #whatTheBillsWillTake} is and for the same reason:
     * the caller has already read the clock to place the bar's own {@code from}, and that is the day
     * drawn at the left-hand edge beside these lines. A second read of the same clock is a second
     * answer to "what day is it" inside one screenful of figures, and a request that straddled
     * midnight would mark these bills owed-rather-than-still-to-come against a different day from
     * the one the bar says it opens on.
     *
     * @param from  the day the window opens, which is what says whether a date is still to come or
     *              is one the nightly run already owes
     * @param until the last day the window holds, inclusive — a date falling on it is in the answer
     */
    @Transactional(readOnly = true)
    public List<ABillToCome> whatTheBillsOfACustomerWillTake(long customerId, LocalDate from,
                                                            LocalDate until) {
        List<RecurringBill> standing = new ArrayList<>();
        for (CurrentAccount account : currentAccounts.findByCustomerId(customerId)) {
            standing.addAll(recurringBills.findByCurrentAccountIdAndStateInOrderByIdAsc(
                    account.getId(), BillState.theOnesStillStanding()));
        }
        return theDatesToCome(standing, from, until);
    }

    /**
     * The dates those bills fall on inside the window, oldest first, each carrying what the bill is
     * worth and whether the next run already owes it.
     *
     * <p>The one place either forecast is worked out, so that the month ahead and the year ahead are
     * the same answer read over two lengths of window rather than two answers that could disagree.
     *
     * <p>Ordered by the day and then by the order the bills were declared, which is the order the
     * run would present two bills falling on one morning in — {@code takeBillsDueBy} sorts by
     * exactly the same pair.
     *
     * <p>A bill whose amount is not an amount of money contributes no dates and a WARN, which is
     * what {@link #present} does with the same row: a forecast is a promise about money that is
     * going to move, and that row's is not going to.
     */
    private List<ABillToCome> theDatesToCome(List<RecurringBill> standing, LocalDate from,
                                             LocalDate until) {
        Instant theEndOfTheWindow = WhenABillIsDue.theMomentThatDayBegins(until);
        List<ABillToCome> coming = new ArrayList<>();
        for (RecurringBill bill : standing) {
            if (bill.getAmount() == null || bill.getAmount().signum() <= 0) {
                // Guarded for the reason present() guards the same field, and left out of the
                // forecast for the reason present() skips it: a row whose amount is not an amount of
                // money moves no money, so a forecast quoting it would promise a debit the 02:30 run
                // is going to refuse. Unreachable in the application as it ships — AmountOfMoney
                // refuses this both when a bill is declared and when one is changed — but this read
                // runs on every visit to the account page, quotedToTheCent calls setScale, and
                // setScale on a null throws: one hand-edited row in a training database would turn
                // the whole page into a 500 with nothing saying which bill did it. This line is what
                // says which bill did it.
                log.warn("bill left out of the forecast currentAccountId={} billId={} name={} "
                                + "dayOfMonth={} amount={} between={}..{} reason=what this bill "
                                + "says it is worth is not an amount of money, so there is nothing "
                                + "to forecast; the rest of the window is worked out without it",
                        bill.getCurrentAccountId(), bill.getId(), bill.getName(),
                        bill.getDayOfMonth(), bill.getAmount(), from, until);
                continue;
            }
            List<LocalDate> dates = WhenABillIsDue.dueDatesBetween(bill.getDayOfMonth(),
                    bill.getSettledThrough(), theEndOfTheWindow);
            List<LocalDate> stillToCome = dueInMonthsNotAlreadySettled(bill, dates);
            for (LocalDate date : stillToCome) {
                coming.add(new ABillToCome(bill.getId(), bill.getCurrentAccountId(), bill.getName(),
                        date, AmountOfMoney.quotedToTheCent(bill.getAmount()),
                        date.isBefore(from)));
            }
            // The inputs behind one bill's share of the forecast, said the way the run says the
            // inputs behind a presentation: the cursor the window was counted from, the day the
            // customer named, the window itself, and the dates it came to at each subtraction. A
            // forecast somebody says is missing a month is reconstructable from this line, and
            // owedFromBefore is what says the next run is behind rather than the forecast wrong.
            // The amount needs no guard of its own here: a bill whose amount is not an amount of
            // money never reaches this line, having been said out loud and skipped above.
            log.debug("what a bill has coming currentAccountId={} billId={} name={} dayOfMonth={} "
                            + "amount={} settledThrough={} between={}..{} dueDates={} "
                            + "stillToCome={} owedFromBefore={}",
                    bill.getCurrentAccountId(), bill.getId(), bill.getName(), bill.getDayOfMonth(),
                    AmountOfMoney.asMoney(bill.getAmount()),
                    bill.getSettledThrough(), from, until, dates.size(), stillToCome.size(),
                    stillToCome.stream().filter(date -> date.isBefore(from)).count());
        }
        coming.sort(Comparator.comparing(ABillToCome::dueOn).thenComparing(ABillToCome::billId));
        // One line per forecast with the window it was built over and how many dates fell inside it,
        // so that a bar somebody says is short can be told from an account whose bills really do
        // nothing. owedFromBefore counts the dates already past: debits the next run owes and has
        // not made, which are in the forecast precisely so that it and the run cannot disagree.
        log.debug("what the bills will take between={}..{} billsStanding={} billDatesToCome={} "
                        + "owedFromBefore={} amount={}",
                from, until, standing.size(), coming.size(),
                coming.stream().filter(ABillToCome::owedRatherThanStillToCome).count(),
                AmountOfMoney.asMoney(coming.stream().map(ABillToCome::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
        return List.copyOf(coming);
    }

    /**
     * What the income declared against this account is due to credit it between its own cursor and
     * the end of the window, which is nothing at all when nobody has declared one.
     *
     * <p>Counted from the cursor rather than from today for the reason the bills are: the 01:00 run
     * catches up, so a salary whose day has already passed without a run is money about to arrive,
     * and leaving it out would quote a month's room that the very next night makes wrong.
     *
     * <p>A figure rather than a list of paydays, because one total is what this card draws. The days
     * behind it come out of {@link #whatTheIncomeOfACurrentAccountWillBring}, which is the one
     * calendar walk either shape is taken from — a second walk would be a second answer to which day
     * a salary lands on, and the weekly forecast beside this card is built entirely out of that
     * answer.
     */
    private BigDecimal whatIsDueToArriveBy(long currentAccountId, LocalDate from, LocalDate until) {
        BigDecimal due = whatTheIncomeOfACurrentAccountWillBring(currentAccountId, from, until)
                .stream()
                .map(AnIncomeToCome::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.debug("what an income will bring altogether currentAccountId={} between={}..{} due={}",
                currentAccountId, from, until, AmountOfMoney.asMoney(due));
        return due;
    }

    /**
     * Every day the income declared against this current account is due to land on, from its own
     * cursor up to and including the given day, oldest first — and nothing at all when nobody has
     * declared one.
     *
     * <p><strong>Derived on every read and stored nowhere</strong>, out of the very calendar the
     * nightly run walks: {@link WhenIncomeIsDue#paydaysBetween} answers the days, and the same
     * question the run asks the record — has this month's payday already been credited — takes out
     * the ones that are settled. So the forecast and the 01:00 run can never disagree about which
     * day a salary lands on, which is the promise the weekly rows beside the month card are under.
     *
     * <p><strong>Counted from the cursor rather than from today</strong>, exactly as the bills are:
     * the run catches up, so a salary whose day has already passed without a run is money about to
     * arrive, and leaving it out would quote room that the very next night makes wrong. Those days
     * come back at the head of the list, marked as owed rather than still to come.
     *
     * <p><strong>The days and not only the total, because a week is not a month.</strong>
     * {@link #theMonthAheadOn} wants one figure over one window and adds these up to get it; a
     * forecast cut into six weekly rows wants to know <em>which</em> week the salary lands in, which
     * is the whole of what makes one row the one that pays for the others. One walk, two shapes.
     *
     * <p>A declaration whose amount is not an amount of money brings nothing and says so, which is
     * what {@link #theDatesToCome} does with a bill in the same state, and the symmetry is the
     * point: both figures on the month card are read on every visit to the account page, and either
     * of them multiplying a null would turn the whole page into a 500 with nothing saying what did
     * it.
     *
     * @param from  the day the window opens, which is what says whether a day is still to come or is
     *              one the nightly run already owes — days before it are still in the answer
     * @param until the last day the window holds, inclusive — a payday falling on it is in the answer
     */
    @Transactional(readOnly = true)
    public List<AnIncomeToCome> whatTheIncomeOfACurrentAccountWillBring(long currentAccountId,
                                                                        LocalDate from,
                                                                        LocalDate until) {
        MonthlyIncome income = monthlyIncomes.findByCurrentAccountId(currentAccountId).orElse(null);
        if (income == null) {
            return List.of();
        }
        if (income.getAmount() == null || income.getAmount().signum() <= 0) {
            // Guarded for the reason the bills beside it are guarded, and left out of the forecast
            // for the reason a bill in the same state is left out: a declaration whose amount is not
            // an amount of money credits nothing, so a forecast quoting it would promise a salary
            // the 01:00 run is not going to pay. Unreachable in the application as it ships —
            // AmountOfMoney refuses this when an income is declared and when one is changed — but
            // this read runs on every visit to the account page and multiply on a null throws, so
            // one hand-edited row in a training database would take the page down. This line is what
            // says which account did it.
            log.warn("income left out of the forecast currentAccountId={} dayOfMonth={} amount={} "
                            + "until={} reason=what this declaration says is paid is not an amount "
                            + "of money, so there is nothing to forecast; the rest of the month "
                            + "ahead is worked out without it",
                    currentAccountId, income.getDayOfMonth(), income.getAmount(), until);
            return List.of();
        }
        List<LocalDate> paydays = WhenIncomeIsDue.paydaysBetween(income.getDayOfMonth(),
                income.getPaidThrough(), WhenIncomeIsDue.startOf(until));
        List<LocalDate> stillToCome = dueInMonthsNotAlreadyPaid(income, paydays);
        BigDecimal amount = AmountOfMoney.quotedToTheCent(income.getAmount());
        List<AnIncomeToCome> coming = stillToCome.stream()
                .map(payday -> new AnIncomeToCome(currentAccountId, payday, amount,
                        payday.isBefore(from)))
                .toList();
        log.debug("what an income will bring currentAccountId={} dayOfMonth={} amount={} "
                        + "paidThrough={} between={}..{} paydays={} stillToCome={} "
                        + "owedFromBefore={} due={}",
                currentAccountId, income.getDayOfMonth(), AmountOfMoney.asMoney(amount),
                income.getPaidThrough(), from, until, paydays.size(), coming.size(),
                coming.stream().filter(AnIncomeToCome::owedRatherThanStillToCome).count(),
                AmountOfMoney.asMoney(amount.multiply(new BigDecimal(coming.size()))));
        return coming;
    }

    /**
     * Every date any of these accounts was presented with, paid or not, newest settlement first:
     * the bills as a ledger of everything that moved reads them.
     *
     * <p><strong>This is how bills reach the money-movement ledger, and it is a read rather than a
     * second record of them.</strong> The ledger stops lying by omission the moment it can see these
     * rows: until now it knew every euro a customer moved into savings and none of the euros their
     * landlord moved out of their current account, which is exactly how somebody ends up unable to
     * account for a balance.
     *
     * <p><strong>Ordered by the moment each date was settled, not by the day it was owed.</strong>
     * {@link ABillOnTheLedger} argues it out: an arrear owed in March and settled in June belongs
     * where the money left. The day it was owed from goes out on the row beside it, so a late
     * payment still reads as the two dates it is.
     *
     * <p>Asked for by account rather than by customer, like every other read this module answers for
     * a page: who holds what is this module's answer and the caller has already been told.
     *
     * <p>No accounts at all is an empty list rather than a query, matching the ledger's own answer
     * to a customer who holds no savings account.
     */
    @Transactional(readOnly = true)
    public List<ABillOnTheLedger> billsPresentedOn(Collection<Long> currentAccountIds) {
        if (currentAccountIds.isEmpty()) {
            log.debug("bills on the ledger asked for across no current accounts, so there are none");
            return List.of();
        }
        List<BillOccurrence> presented =
                billsSettled.findByCurrentAccountIdInOrderBySettledAtDescIdDesc(currentAccountIds);
        if (presented.isEmpty()) {
            log.debug("bills on the ledger read currentAccounts={} presented=0",
                    currentAccountIds.size());
            return List.of();
        }
        Map<Long, String> names = whatEachOfTheseBillsIsCalled(presented);
        List<ABillOnTheLedger> ledger = presented.stream()
                .map(one -> ABillOnTheLedger.of(one, names.get(one.getRecurringBillId())))
                .toList();
        // How many dates went into the ledger and how many of them took nothing, so that a history
        // page missing a rent can be checked against what this module actually handed over. Counts
        // rather than a line per row: this runs on every read of the page.
        log.debug("bills on the ledger read currentAccounts={} presented={} paid={} unpaid={} "
                        + "newestSettledAt={} oldestSettledAt={}",
                currentAccountIds.size(), ledger.size(),
                ledger.stream().filter(one -> one.outcome() == BillOutcome.PAID).count(),
                ledger.stream().filter(one -> one.outcome() == BillOutcome.UNPAID).count(),
                ledger.get(0).settledAt(), ledger.get(ledger.size() - 1).settledAt());
        return ledger;
    }

    /**
     * Every date this account was ever presented with and could not pay, oldest failure first,
     * including the ones that have since been settled.
     *
     * <p><strong>The record the 04:00 raiser reads, and the other half of the bargain the 02:30 run
     * strikes.</strong> The billing run records facts and says nothing to anybody; this is how those
     * facts leave the module, so that a notification can be worked out again from the record without
     * re-running a debit. It is deliberately not {@link #arrearsOn}: that answers what is still owed
     * and drops a date the moment it is cleared, and a raiser deciding whether the arrears have
     * <em>crossed</em> the piling-up threshold — rather than merely stood over it for the fortieth
     * night running — needs to see the ones that were cleared and when.
     *
     * <p>Oldest failure first, which is the order the account got into trouble in and therefore the
     * order a reader of this list has to replay it in.
     *
     * <p>A date presented before the application recorded the night it fell short on is not in here
     * at all; {@link BillOccurrence} argues out why that is the right answer rather than a gap.
     */
    @Transactional(readOnly = true)
    public List<ABillThatCouldNotBePaid> billsThatCouldNotBePaidOn(long currentAccountId) {
        List<BillOccurrence> fellShort = billsSettled
                .findByCurrentAccountIdAndFellShortAtNotNullOrderByFellShortAtAscIdAsc(
                        currentAccountId);
        if (fellShort.isEmpty()) {
            log.debug("dates that could not be paid read currentAccountId={} fellShort=0",
                    currentAccountId);
            return List.of();
        }
        Map<Long, String> names = whatEachOfTheseBillsIsCalled(fellShort);
        List<ABillThatCouldNotBePaid> refused = fellShort.stream()
                .map(one -> ABillThatCouldNotBePaid.of(
                        one, names.get(one.getRecurringBillId())))
                .toList();
        log.debug("dates that could not be paid read currentAccountId={} fellShort={} "
                        + "stillOwed={} oldestFellShortAt={}",
                currentAccountId, refused.size(),
                refused.stream().filter(one -> !one.isCleared()).count(),
                refused.get(0).fellShortAt());
        return refused;
    }

    /**
     * The names of the bills these dates belong to, in one question rather than one per date, so
     * that an account carrying six months of arrears is two queries and not seven.
     *
     * <p>A bill that has been ended is looked up exactly like one still standing: the date is still
     * owed and the customer still recognises the name.
     */
    private Map<Long, String> whatEachOfTheseBillsIsCalled(List<BillOccurrence> owed) {
        Set<Long> billIds = owed.stream()
                .map(BillOccurrence::getRecurringBillId)
                .collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        recurringBills.findAllById(billIds)
                .forEach(bill -> names.put(bill.getId(), bill.getName()));
        return names;
    }

    /** What a list of arrears comes to altogether, which is the figure the still-owed panel leads with. */
    private static BigDecimal totalOf(List<AnArrear> arrears) {
        return arrears.stream().map(AnArrear::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Declares something that leaves this current account every month, and answers with the bill
     * that now stands.
     *
     * <p><strong>Added rather than replacing.</strong> This is the one place the outbound
     * declaration parts company with {@link #declareMonthlyIncome}: a second income is the customer
     * saying what they are paid <em>now</em>, and a second bill is a second bill. A household has a
     * rent and a phone bill and an energy bill, and an account that could hold one of them would be
     * an account nobody could describe their month with.
     *
     * <p>Which is why there is a cap instead. Four things are settled before a row is written, in
     * the order the customer typed them: that the bill has a name, that its day is a day of the
     * month, that its amount is an amount of money, and — last — that the account has room for
     * another. Last, so that a twenty-first bill is refused for being a twenty-first bill rather
     * than for a typo it also had.
     *
     * <p>Nothing is debited by declaring one. A bill is a sentence about what will happen, and the
     * cursor it starts life with is what makes it a sentence about the future rather than about
     * months that have already gone by — {@link RecurringBill} says how.
     *
     * @throws RecurringBillRefused if that is not a bill this application will keep
     */
    @Transactional
    public ADeclaredBill declareABill(long currentAccountId, ABillAsAsked asked) {
        log.debug("bill asked for currentAccountId={} name={} dayOfMonth={} amount={}",
                currentAccountId, asked.name(), asked.dayOfMonth(),
                asked.amount() == null ? null : asked.amount().toPlainString());
        ABillAsItWouldRead itWouldRead = judgedAsABill(currentAccountId, null, asked.name(),
                asked.dayOfMonth(), asked.amount());
        refuseUnlessThereIsRoomForAnotherBill(currentAccountId);

        Instant declaredAt = clock.instant();
        RecurringBill bill = recurringBills.save(new RecurringBill(currentAccountId,
                itWouldRead.name(), itWouldRead.dayOfMonth(), itWouldRead.amount(), declaredAt));
        log.info("bill declared currentAccountId={} billId={} name={} dayOfMonth={} amount={} "
                        + "state={} declaredAt={} settledThrough={}",
                currentAccountId, bill.getId(), bill.getName(), bill.getDayOfMonth(),
                AmountOfMoney.asMoney(bill.getAmount()), bill.getState(), bill.getDeclaredAt(),
                bill.getSettledThrough());
        // Never taken, and known rather than asked: it was written a line ago, so a query for the
        // dates it has been paid on could only ever answer nothing.
        return ADeclaredBill.of(bill, null);
    }

    /**
     * Says a standing bill differently — its name, the day it goes out on, what it is worth, or any
     * combination of the three — and answers with the bill as it now reads.
     *
     * <p>Only what was given is changed; anything absent is left exactly as it was, which is what
     * makes putting the rent up a thing somebody can do without restating the day it leaves on. What
     * is judged is the bill <em>as it would then read</em> rather than the fields that arrived, so a
     * change that would leave it with a blank name is refused as the bill it would make rather than
     * as the field it named.
     *
     * <p>A change that says nothing at all is refused rather than answered with an untouched bill:
     * answering 200 to it would tell a page its edit went through.
     *
     * <p>A bill that has been ended is refused rather than edited. It is kept so that the months it
     * was taken for stay explained, and a record that could be rewritten afterwards is not a record.
     *
     * <p>Nothing is written until every objection has been heard, so a refused change leaves the
     * bill exactly as the customer last left it. The cursor is left where it was, for the reason
     * {@link RecurringBill} gives: a rent that has gone up is not a request to be charged again for
     * the months already settled.
     *
     * @throws RecurringBillRefused if there is no such bill on that account, if it has been ended,
     *                              or if the change is one this module will not make
     */
    @Transactional
    public ADeclaredBill changeBill(long currentAccountId, long billId, AChangeToABill change) {
        log.debug("bill change asked for currentAccountId={} billId={} name={} dayOfMonth={} "
                        + "amount={}",
                currentAccountId, billId, change.name(), change.dayOfMonth(),
                change.amount() == null ? null : change.amount().toPlainString());
        RecurringBill bill = theBillOn(currentAccountId, billId);
        refuseUnlessTheBillHasNotBeenEnded(currentAccountId, bill, "changed");
        if (change.saysNothing()) {
            throw refusingTheBill(currentAccountId, billId, RecurringBillRefused.Kind.AGAINST_THE_RULES,
                    "Say what to change about \"" + bill.getName() + "\": its name, the day of the "
                            + "month it goes out on, or what it is worth.");
        }
        ABillAsItWouldRead itWouldRead = judgedAsABill(currentAccountId, billId,
                change.name() == null ? bill.getName() : change.name(),
                change.dayOfMonth() == null ? bill.getDayOfMonth() : change.dayOfMonth(),
                change.amount() == null ? bill.getAmount() : change.amount());

        String wasNamed = bill.getName();
        int wasOnDayOfMonth = bill.getDayOfMonth();
        BigDecimal wasWorth = bill.getAmount();
        bill.nowSays(itWouldRead.name(), itWouldRead.dayOfMonth(), itWouldRead.amount(),
                clock.instant());
        recurringBills.save(bill);
        log.info("bill changed currentAccountId={} billId={} name={} dayOfMonth={} amount={} "
                        + "declaredAt={} wasNamed={} wasOnDayOfMonth={} wasWorth={}",
                currentAccountId, billId, bill.getName(), bill.getDayOfMonth(),
                AmountOfMoney.asMoney(bill.getAmount()), bill.getDeclaredAt(), wasNamed,
                wasOnDayOfMonth, AmountOfMoney.asMoney(wasWorth));
        return asDeclared(bill);
    }

    /**
     * Ends a bill for good, and answers with the bill as it now reads rather than with nothing.
     *
     * <p>One-way, and the whole of what that means is here: the bill leaves {@link #billsOn},
     * appears in {@link #endedBillsOn}, and every later change or ending of it is refused. Nothing
     * that was taken under it is unpicked — the money left the account and the record of it is what
     * explains a balance — and ending it makes room under the cap for another.
     *
     * @throws RecurringBillRefused if there is no such bill on that account, or it has already ended
     */
    @Transactional
    public ADeclaredBill endBill(long currentAccountId, long billId) {
        log.debug("bill end asked for currentAccountId={} billId={}", currentAccountId, billId);
        RecurringBill bill = theBillOn(currentAccountId, billId);
        refuseUnlessTheBillHasNotBeenEnded(currentAccountId, bill, "ended");
        bill.end(clock.instant());
        recurringBills.save(bill);
        log.info("bill ended currentAccountId={} billId={} name={} dayOfMonth={} amount={} "
                        + "stoodSince={} endedAt={} state={}",
                currentAccountId, billId, bill.getName(), bill.getDayOfMonth(),
                AmountOfMoney.asMoney(bill.getAmount()), bill.getDeclaredAt(), bill.getEndedAt(),
                bill.getState());
        return asDeclared(bill);
    }

    /**
     * Presents every date every standing bill has fallen due on by the given moment and has not been
     * settled yet, oldest first, taking what it can and recording what it could not.
     *
     * <p><strong>Every date rather than only today's</strong>, which is what makes a monthly bill
     * demonstrable in an afternoon: winding the clock two months forward and running this once
     * presents two rents, not one. It is not a nicety here but the only path there is — the clock
     * moves in whole calendar days and the cron never fires for the days it skipped, so on a wound
     * clock catching up is the way a bill is <em>ever</em> taken.
     *
     * <p><strong>Oldest first, across bills and not only within one.</strong> The whole run is
     * gathered and then sorted by the date each debit fell on, and by the order the bills were
     * declared where two fall on one morning — so a household whose rent and phone bill land on the
     * same day meets them in the order it wrote them, and a two-month catch-up works forwards
     * through the calendar rather than finishing one bill before starting the next. Sorting after
     * gathering rather than presenting each bill's dates as they are found is what makes that order a
     * promise rather than an accident of which row was read first.
     *
     * <p><strong>The money is taken through {@link #withdrawFrom}</strong>, the same all-or-nothing
     * withdrawal a manual transfer uses, which already refuses rather than going negative. A short
     * balance therefore means the outcome is {@link BillOutcome#UNPAID} and the balance is left
     * exactly where it was: a bill of nine hundred against a balance of six hundred takes nothing and
     * leaves six hundred. There is no partial payment and no overdraft, and a half-paid rent is
     * neither a lesson nor a thing that happens.
     *
     * <p><strong>The cursor advances past a date that went unpaid.</strong> What is still owed is
     * carried by the unpaid rows, not by holding the cursor back — a cursor left behind would
     * re-derive the same date every night for ever and destroy the idempotence the record exists to
     * give. Those rows are what {@link #settleWhatIsStillOwed} works through at the top of every
     * run, oldest first, before a newly due date is looked at.
     *
     * <p><strong>At most five hundred due dates per bill, and the rest are delayed rather than
     * lost</strong> — see {@link #asManyOfThemAsOneRunCatchesUp}, which also says in a WARN which
     * dates were left.
     *
     * <p>Answers nothing, for the reason the income run gives: its one caller runs on a schedule with
     * nobody waiting on it, and a figure returned to a scheduled method is a figure nothing can read.
     * What the run did is in the INFO and WARN lines below.
     *
     * <p>Public, unlike the rows and the repositories, and not because anything outside wants it:
     * {@code @Transactional} is applied by a proxy and a proxy cannot advise a method that is not
     * public, so the annotation would be silently ignored and a half-finished run would commit. The
     * power this leaks is the power to run the nightly job early, which is idempotent and is exactly
     * what the development jobs endpoint offers anyway.
     *
     * <p>The caller says what time it is. This module reads the clock for a declaration, which has a
     * customer in front of it; a run has to judge every bill against one moment, and taking it once
     * in the job is what guarantees that.
     *
     * <p><strong>Idempotent, and a month holds one rent.</strong> Every settlement writes a row
     * naming the bill and the date due, and that pair is unique in the database
     * ({@link AccountsOnStartUp}) — but the index is not the guarantee money needs. Before anything
     * is presented the run asks the record which <em>months</em> this bill already has a settlement
     * in and drops those dates, exactly as the income run asks which months have been paid. That is
     * what makes a bill whose day was corrected inside a month it has already been taken in a
     * correction rather than a second debit; {@link #dueInMonthsNotAlreadySettled} says why at
     * length. The cursor is past them as well, and is the third thing rather than the first.
     */
    @Transactional
    public void takeBillsDueBy(Instant now) {
        // Outstanding first, and the whole of "outstanding first" is that this line comes before the
        // one below it. What is already owed is settled out of whatever the account holds before a
        // newly due date is allowed to look at it, so March's rent is paid before April's and a
        // customer who scrapes together nine hundred euros pays the older rent rather than the
        // convenient one.
        ArrearsThisRunSettled arrears = settleWhatIsStillOwed(now);
        List<RecurringBill> standing =
                recurringBills.findByStateInOrderByIdAsc(BillState.theOnesStillStanding());
        List<ABillOnItsDate> due = new ArrayList<>();
        // Where each bill's cursor is left once its dates have been presented. A bill whose catch-up
        // was capped is settled through the beginning of the date this run stopped at rather than
        // through the moment it ran, which is the whole of "the next run continues from where this
        // one stopped"; every other bill is settled through the moment, and is not in here at all.
        Map<Long, Instant> settledThroughByBill = new HashMap<>();
        Instant lookedBackTo = now;
        long leftForANextRun = 0;
        for (RecurringBill bill : standing) {
            Instant settledThrough = bill.getSettledThrough();
            if (settledThrough == null) {
                // Unreachable in the application as it ships: declaring a bill sets its cursor and
                // nothing ever clears it. Said out loud because the alternative readings of a
                // missing cursor are a century of back rent or an exception that would abort this
                // one transaction and present no bill at all. Nothing is presented and the cursor is
                // set to this run's moment below, so the next run counts from somewhere.
                log.warn("bill cursor missing currentAccountId={} billId={} name={} "
                                + "reason=the moment this bill is settled through is not recorded, "
                                + "so no due date can be judged against it; nothing is taken and "
                                + "the cursor is set to asAt={}",
                        bill.getCurrentAccountId(), bill.getId(), bill.getName(), now);
            } else if (settledThrough.isBefore(lookedBackTo)) {
                lookedBackTo = settledThrough;
            }
            List<LocalDate> dates = WhenABillIsDue.dueDatesBetween(bill.getDayOfMonth(),
                    settledThrough, now);
            List<LocalDate> notSettledYet = dueInMonthsNotAlreadySettled(bill, dates);
            // The inputs behind the decision: the cursor this bill's range was counted from, the day
            // the customer said, what it is worth, and which dates that came to. A run that took
            // nothing is explainable from this line without anybody reconstructing the calendar by
            // hand, and it is the line that says which cursor each bill started from.
            //
            // The amount is guarded rather than passed straight to asMoney, and the guard is not a
            // tidy-up to be deleted: asMoney calls setScale, which throws on a null, SLF4J builds
            // its arguments whether or not DEBUG is on, and this line runs for every standing bill
            // before present() has looked at any of them. Handing it a raw null would therefore
            // throw here, out of the one transaction this whole run is, and the night would roll
            // back for every customer — with no line naming which bill did it, because the line
            // that would have named it is this one. present() is where an amount that is not an
            // amount of money is decided on and said out loud; this line's only job is to not
            // pre-empt it. The same shape guards declareABill's own DEBUG line.
            log.debug("bill considered currentAccountId={} billId={} name={} dayOfMonth={} "
                            + "amount={} settledThrough={} asAt={} dueDates={} toPresent={}",
                    bill.getCurrentAccountId(), bill.getId(), bill.getName(), bill.getDayOfMonth(),
                    bill.getAmount() == null ? null : AmountOfMoney.asMoney(bill.getAmount()),
                    settledThrough, now, dates, notSettledYet);
            List<LocalDate> thisRun = asManyOfThemAsOneRunCatchesUp(bill, notSettledYet);
            if (thisRun.size() < notSettledYet.size()) {
                leftForANextRun += notSettledYet.size() - thisRun.size();
                settledThroughByBill.put(bill.getId(), WhenABillIsDue
                        .theMomentThatDayBegins(thisRun.get(thisRun.size() - 1)));
            }
            thisRun.forEach(date -> due.add(new ABillOnItsDate(bill, date)));
        }
        due.sort(Comparator.comparing(ABillOnItsDate::dueOn)
                .thenComparing(presented -> presented.bill().getId()));

        long dueDatesTaken = 0;
        long dueDatesUnpaid = 0;
        BigDecimal takenAltogether = BigDecimal.ZERO;
        Set<Long> billsWithSomethingStillDue = new HashSet<>();
        for (ABillOnItsDate presented : due) {
            Optional<BillOutcome> outcome = present(presented.bill(), presented.dueOn(), now);
            if (outcome.isEmpty()) {
                billsWithSomethingStillDue.add(presented.bill().getId());
                continue;
            }
            if (outcome.get() == BillOutcome.PAID) {
                dueDatesTaken++;
                takenAltogether = takenAltogether.add(presented.bill().getAmount());
            } else {
                dueDatesUnpaid++;
            }
        }

        for (RecurringBill bill : standing) {
            if (billsWithSomethingStillDue.contains(bill.getId())) {
                // A date that could not be presented at all stays due. Moving the cursor over it
                // would lose it for good, and the two things that stop a date being presented — a
                // current account that is no longer there, and an amount that is not an amount of
                // money — are both states somebody could put right, and both already named by the
                // WARN from the presenting itself. An unpaid date is not this case: it was
                // presented, it is recorded, and the cursor moves past it.
                log.warn("bill cursor left where it was currentAccountId={} billId={} "
                                + "settledThrough={} reason=a due date this run could not present "
                                + "at all is still due",
                        bill.getCurrentAccountId(), bill.getId(), bill.getSettledThrough());
                continue;
            }
            bill.settledThrough(settledThroughByBill.getOrDefault(bill.getId(), now));
            recurringBills.save(bill);
        }

        // One line per run with everything that decided it: the stretch of time it covered — which
        // on a wound clock or after downtime is months rather than a night, and is the figure that
        // explains a run that took six debits — how many standing bills it walked, how many due
        // dates it took, how many it could not, how many it deliberately left for the next run, and
        // what the money came to. A current account that fell overnight is explainable from this
        // line alone, and a run that walked three bills and took none can be told from one handed
        // none.
        //
        // Bills are counted in bills and due dates in due dates, and the two are deliberately named
        // apart: one bill caught up over two months presents two dates, so a line calling those
        // "billsTaken" would report more bills taken than were considered and contradict itself.
        log.info("bills run asAt={} lookedBetween={}..{} billsConsidered={} dueDatesTaken={} "
                        + "dueDatesUnpaid={} dueDatesLeftForANextRun={} amount={} "
                        + "arrearsSettled={} arrearsSettledAmount={}",
                now, lookedBackTo, now, standing.size(), dueDatesTaken, dueDatesUnpaid,
                leftForANextRun, AmountOfMoney.asMoney(takenAltogether), arrears.settled(),
                AmountOfMoney.asMoney(arrears.amountSettled()));

        sayWhatIsStillOwedAfterAllThat(now);
    }

    /**
     * Settles every date on every account that was presented and not paid, oldest due date first,
     * before anything newly due is looked at. Answers how it went, for the run's own line.
     *
     * <p><strong>This is the feature.</strong> A bill that could not be paid is not waived: it stays
     * owed as an unpaid row, and every later run offers the account's money to it before offering it
     * to a date falling due tonight. Miss the rent in March and April presents two rents, and the
     * one that clears is March's.
     *
     * <p><strong>Oldest first across every account at once.</strong> Arrears on one account can only
     * ever be settled out of that account's own balance, so one pass over every unpaid date in the
     * application, oldest first, settles each account's own arrears in exactly the order that
     * account would have had them settled in on its own — and it is one query rather than one per
     * account.
     *
     * <p><strong>A date that cannot be settled does not stop the ones behind it.</strong> The money
     * is always offered to the oldest debt first and that is what "oldest first" promises; what it
     * does not promise is that a rent nobody can afford freezes the account until they can. A
     * hundred euros against a nine hundred euro rent and a fifty euro phone bill pays the phone
     * bill, having offered the rent the money first and been refused — the rent is no worse off, and
     * an application that had held the fifty back would have taught nothing except that debt is
     * sticky.
     *
     * <p><strong>Still all-or-nothing, and still at the amount originally due.</strong> The
     * withdrawal is {@link #withdrawFrom}, the same one every other caller uses, so two rents owed
     * and nine hundred euros in the account pays one rent and not one and a half. The figure asked
     * for is the one on the row, which is what was owed on the day: no interest, no fee, no charge
     * of any kind is ever added, because the accumulation is the whole of the consequence.
     *
     * <p><strong>Nothing here touches a cursor.</strong> The cursor moved past these dates when they
     * first went unpaid and it stays past them — arrears are carried by the rows, and a cursor held
     * back would re-derive the same date for ever and destroy the idempotence the record exists to
     * give. It touches no streak either: a week is secured by the weekly minimum of new saving and
     * by nothing else, and an unpaid bill is a fact about a current account.
     */
    private ArrearsThisRunSettled settleWhatIsStillOwed(Instant now) {
        List<BillOccurrence> owed =
                billsSettled.findByOutcomeOrderByDueOnAscIdAsc(BillOutcome.UNPAID);
        if (owed.isEmpty()) {
            log.debug("arrears none outstanding asAt={}", now);
            return new ArrearsThisRunSettled(0, BigDecimal.ZERO);
        }
        // The order they are about to be tried in, said out loud before any of them is: it is the
        // rule this slice is made of, and a reviewer confirming that the older rent was offered the
        // money first should be able to read that it was rather than infer it from which one moved.
        log.debug("arrears to settle before anything newly due asAt={} outstanding={} "
                        + "inThisOrder={}",
                now, owed.size(), owed.stream().map(AccountsService::asAQueueEntry).toList());
        long settled = 0;
        BigDecimal amountSettled = BigDecimal.ZERO;
        for (BillOccurrence arrear : owed) {
            if (settle(arrear, now)) {
                settled++;
                amountSettled = amountSettled.add(arrear.getAmount());
            }
        }
        return new ArrearsThisRunSettled(settled, amountSettled);
    }

    /**
     * Offers one arrear the account's money, and answers whether it took it.
     *
     * <p><strong>The row is updated in place.</strong> The outcome becomes paid and the settled-at
     * moment moves to when the money was actually taken, while the due date stays the day it was
     * owed from — so there is never a second row for one due date and the history never rewrites
     * when something was owed. {@link BillOccurrence#settledLate} is where that is written down.
     *
     * <p>A date that still cannot be paid is left exactly as it was, moment and all: it was
     * presented when it was presented, and moving that moment every night would turn a record of
     * what happened into a record of when the application last looked. How late it is by now is
     * counted against the clock instead, which is what {@link AnArrear} reports.
     *
     * <p>The same two states that stop a newly due date being presented at all stop one being
     * settled, and for the identical reason: this runs inside the one transaction the whole night
     * is, and an exception here would roll back every customer's run rather than skip one row.
     */
    private boolean settle(BillOccurrence arrear, Instant now) {
        String name = recurringBills.findById(arrear.getRecurringBillId())
                .map(RecurringBill::getName)
                .orElse(null);
        long daysLate = AnArrear.daysLateOn(arrear.getDueOn(), now);
        if (arrear.getAmount() == null || arrear.getAmount().signum() <= 0) {
            log.warn("arrear not presented currentAccountId={} billId={} name={} dueOn={} "
                            + "amount={} reason=what this date says is owed is not an amount of "
                            + "money, so there is nothing to withdraw; it is skipped and the rest "
                            + "of the run goes on",
                    arrear.getCurrentAccountId(), arrear.getRecurringBillId(), name,
                    arrear.getDueOn(), arrear.getAmount());
            return false;
        }
        Optional<BigDecimal> held = currentAccounts.findById(arrear.getCurrentAccountId())
                .map(CurrentAccount::getBalance);
        if (held.isEmpty()) {
            log.warn("arrear not presented currentAccountId={} billId={} name={} dueOn={} "
                            + "amount={} reason=there is no such current account any more",
                    arrear.getCurrentAccountId(), arrear.getRecurringBillId(), name,
                    arrear.getDueOn(), AmountOfMoney.asMoney(arrear.getAmount()));
            return false;
        }
        BigDecimal before = held.get();
        // The figure on the row and not the figure on the bill. It is what was owed on the day, and
        // it is what is owed now: a rent that went up in April does not make March's arrear bigger,
        // and nothing is ever added to it.
        if (!withdrawFrom(arrear.getCurrentAccountId(), arrear.getAmount())) {
            log.warn("arrear still unpaid currentAccountId={} billId={} name={} dueOn={} amount={} "
                            + "balance={} shortBy={} daysLate={} asAt={} reason=the current account "
                            + "still does not hold what this date asked for, so nothing at all was "
                            + "taken and it stays owed",
                    arrear.getCurrentAccountId(), arrear.getRecurringBillId(), name,
                    arrear.getDueOn(), AmountOfMoney.asMoney(arrear.getAmount()),
                    AmountOfMoney.asMoney(before),
                    AmountOfMoney.asMoney(arrear.getAmount().subtract(before)), daysLate, now);
            return false;
        }
        arrear.settledLate(now);
        billsSettled.save(arrear);
        // The business event: a debt cleared. The bill, the day it was owed from, how late that
        // turned out to be, what left and what the account then held — which is what makes the next
        // arrear in the same queue explainable.
        log.info("arrear settled currentAccountId={} billId={} name={} dueOn={} daysLate={} "
                        + "amount={} settledAt={} balance={}",
                arrear.getCurrentAccountId(), arrear.getRecurringBillId(), name, arrear.getDueOn(),
                daysLate, AmountOfMoney.asMoney(arrear.getAmount()), now,
                AmountOfMoney.asMoney(theBalanceNowIn(arrear.getCurrentAccountId(), before)));
        return true;
    }

    /**
     * One WARN per account that the run has left with something still owed, naming how many dates
     * and what they come to.
     *
     * <p>Asked after both passes rather than after the first, because a date that fell due tonight
     * and could not be paid is owed from tonight: a line that counted only what was owed when the
     * run started would tell a customer who has just missed their first rent that they owe nothing.
     *
     * <p>Per account rather than one line for the whole application, because arrears are a fact
     * about one current account and a total across every customer is a figure nobody can act on.
     * Nothing is said at all for an account that owes nothing, which is the same bargain the panel
     * on the page strikes: a line that appears every night is a line people stop reading.
     */
    private void sayWhatIsStillOwedAfterAllThat(Instant now) {
        Map<Long, List<BillOccurrence>> byAccount =
                billsSettled.findByOutcomeOrderByDueOnAscIdAsc(BillOutcome.UNPAID).stream()
                        .collect(Collectors.groupingBy(BillOccurrence::getCurrentAccountId,
                                LinkedHashMap::new, Collectors.toList()));
        byAccount.forEach((currentAccountId, stillOwed) -> log.warn(
                "arrears outstanding currentAccountId={} asAt={} arrears={} amount={} "
                        + "oldestDueOn={} daysLateOnTheOldest={} reason=these dates were presented "
                        + "and the account could not cover them; they stay owed and every later run "
                        + "settles them before anything newly due",
                currentAccountId, now, stillOwed.size(),
                AmountOfMoney.asMoney(stillOwed.stream()
                        .map(BillOccurrence::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)),
                stillOwed.get(0).getDueOn(),
                AnArrear.daysLateOn(stillOwed.get(0).getDueOn(), now)));
    }

    /** One arrear as the queue line names it, so that the order can be read rather than inferred. */
    private static String asAQueueEntry(BillOccurrence owed) {
        return "currentAccountId=" + owed.getCurrentAccountId()
                + " billId=" + owed.getRecurringBillId()
                + " dueOn=" + owed.getDueOn()
                + " amount=" + (owed.getAmount() == null ? null
                        : AmountOfMoney.asMoney(owed.getAmount()));
    }

    /**
     * What one run's arrears pass came to: how many dates it cleared and what they were worth. A
     * record rather than two out-parameters, so that the run's own INFO line reads one answer.
     */
    private record ArrearsThisRunSettled(long settled, BigDecimal amountSettled) {
    }

    /**
     * Presents one bill on one of its due dates: takes the money if the account holds it, writes down
     * what happened either way, and answers how it went.
     *
     * <p>The withdrawal is {@link #withdrawFrom}, the same one a customer's own transfer uses, and it
     * is called on this object rather than through the proxy — which changes nothing, because the run
     * this is inside is already the transaction. What matters is that the rule about going negative
     * is the account's own and is not restated here: a second opinion about whether a balance can
     * cover an amount is a second place for the two to disagree.
     *
     * <p>The row is written whichever way it went. That is the whole reason this table exists beside
     * the money that moved: a date that met an empty account moved nothing, and a ledger of movements
     * has no row for a movement that did not happen.
     *
     * <p><strong>Nothing a single row can be wrong about takes the night down.</strong> This runs
     * inside one transaction over every standing bill on every account, so an exception thrown here
     * rolls back the whole run and no customer anywhere is presented at all. Each state that could
     * do that is therefore checked and skipped with a WARN instead: an account that is no longer
     * there, and an amount that is not one {@link #withdrawFrom} would accept.
     *
     * @return how it went, or nothing at all when the date could not be presented — which is a bill
     *         standing against an account that is no longer there, or one whose amount is not an
     *         amount of money
     */
    private Optional<BillOutcome> present(RecurringBill bill, LocalDate dueOn, Instant now) {
        if (bill.getAmount() == null || bill.getAmount().signum() <= 0) {
            // Not reachable in the application as it ships: AmountOfMoney refuses nought and less on
            // the way in, both when a bill is declared and when one is changed. Checked all the same
            // because withdrawFrom answers this with an IllegalArgumentException, and an exception
            // here is not one bill skipped — it is the whole night rolled back for every customer,
            // because this run is one transaction. A row somebody hand-edited into a training
            // database would take the application's bills down until they noticed.
            log.warn("bill not presented currentAccountId={} billId={} name={} dueOn={} amount={} "
                            + "reason=what this bill says it is worth is not an amount of money, so "
                            + "there is nothing to withdraw; it is skipped and the rest of the run "
                            + "goes on",
                    bill.getCurrentAccountId(), bill.getId(), bill.getName(), dueOn,
                    bill.getAmount());
            return Optional.empty();
        }
        Optional<BigDecimal> held = currentAccounts.findById(bill.getCurrentAccountId())
                .map(CurrentAccount::getBalance);
        if (held.isEmpty()) {
            // Not reachable in the application as it ships, which never deletes an account. Said out
            // loud rather than skipped silently because a bill pointing at nothing is the kind of row
            // a participant's own exercise leaves behind, and a run that quietly presented one bill
            // fewer than a trainer expected is the hardest sort of nothing to diagnose.
            log.warn("bill not presented currentAccountId={} billId={} name={} dueOn={} amount={} "
                            + "reason=there is no such current account any more",
                    bill.getCurrentAccountId(), bill.getId(), bill.getName(), dueOn,
                    AmountOfMoney.asMoney(bill.getAmount()));
            return Optional.empty();
        }
        BigDecimal before = held.get();
        boolean paid = withdrawFrom(bill.getCurrentAccountId(), bill.getAmount());
        BillOutcome outcome = paid ? BillOutcome.PAID : BillOutcome.UNPAID;
        // A refused date writes down the night it was refused on and what the account held, which a
        // paid one has nothing to say about. Those two figures are what the 04:00 raiser turns into
        // the notification a customer reads: this run records the facts and says nothing to anybody,
        // exactly as the rules run at two does.
        billsSettled.save(paid
                ? BillOccurrence.paid(bill.getId(), bill.getCurrentAccountId(), dueOn, now,
                        bill.getAmount())
                : BillOccurrence.fellShort(bill.getId(), bill.getCurrentAccountId(), dueOn, now,
                        bill.getAmount(), before));
        if (paid) {
            // One line per debit, which is the business event: the account, the bill, the date it
            // was owed on, what left, when it actually left, and what the account then held. The gap
            // between the two moments is the lateness a caught-up debit was taken with, and the
            // resulting balance is what makes the next bill in the same run explainable.
            log.info("bill taken currentAccountId={} billId={} name={} dueOn={} amount={} "
                            + "settledAt={} balance={}",
                    bill.getCurrentAccountId(), bill.getId(), bill.getName(), dueOn,
                    AmountOfMoney.asMoney(bill.getAmount()), now,
                    AmountOfMoney.asMoney(theBalanceNowIn(bill.getCurrentAccountId(), before)));
        } else {
            // Every refusal says why, with both figures that decided it: what was asked for and what
            // was actually there. A customer told only that their rent did not go out cannot act;
            // told that it was short by EUR 300,00 on a balance of EUR 600,00, they can.
            log.warn("bill unpaid currentAccountId={} billId={} name={} dueOn={} amount={} "
                            + "balance={} shortBy={} settledAt={} reason=the current account did "
                            + "not hold what the bill asked for, so nothing at all was taken and "
                            + "the balance is exactly where it was",
                    bill.getCurrentAccountId(), bill.getId(), bill.getName(), dueOn,
                    AmountOfMoney.asMoney(bill.getAmount()), AmountOfMoney.asMoney(before),
                    AmountOfMoney.asMoney(bill.getAmount().subtract(before)), now);
        }
        return Optional.of(outcome);
    }

    /**
     * What the account holds now that the withdrawal has been made, read back rather than subtracted,
     * so that the figure on the INFO line is the account's own answer and not this method's
     * arithmetic about it. An account that has vanished between two statements of one transaction is
     * impossible; the balance handed in is the answer if it somehow did.
     */
    private BigDecimal theBalanceNowIn(long currentAccountId, BigDecimal ifItCannotBeRead) {
        return currentAccounts.findById(currentAccountId)
                .map(CurrentAccount::getBalance)
                .orElse(ifItCannotBeRead);
    }

    /**
     * The dates of that range falling in a month this bill has not already been settled in.
     *
     * <p><strong>A month holds one rent.</strong> That is the rule, and it is the record that keeps
     * it rather than the cursor: the cursor is what keeps a run bounded and the record is what makes
     * it idempotent, and they are different guarantees. This is
     * {@link #dueInMonthsNotAlreadyPaid} turned outwards, word for word and for the same reason,
     * and it is written here rather than left to the unique index because the index and the
     * guarantee are not the same thing — the index says a bill has one row per due date, and what
     * money needs said is that a bill leaves the account once a month.
     *
     * <p>Asking the record which exact <em>days</em> were settled keeps neither on its own, and the
     * gap is money. A customer whose rent went out on the 10th of March and who then corrects the
     * bill to the 20th has a 20th of March that no cursor sitting at the 11th excludes and no row
     * under that day denies, and would be charged twice in the month for having told the
     * application when their landlord actually takes it. The 10th and the 20th are different days;
     * March is one March. On the outbound side that is a debit the customer never agreed to, which
     * is the worse of the two directions this mistake runs in.
     *
     * <p>It covers the other ways one month can be asked for twice as well, which is why it is here
     * and not in the calendar or in the entity: a day corrected twice inside one month, and — once
     * arrears are re-presented — an unpaid date retried alongside a newly due one in the same month.
     * All of them are one question: has this bill been presented for that month.
     *
     * <p>One query for the whole stretch rather than one per date, so that a clock wound three years
     * forward is a query per bill and not thirty-six. The stretch is whole months — the first of the
     * first to the last of the last — because which day of a month was settled is exactly what must
     * not be assumed, and {@code due} is oldest first, which {@link WhenABillIsDue#dueDatesBetween}
     * promises.
     */
    private List<LocalDate> dueInMonthsNotAlreadySettled(RecurringBill bill, List<LocalDate> due) {
        if (due.isEmpty()) {
            return due;
        }
        LocalDate fromTheFirstOfTheFirstMonth = due.get(0).withDayOfMonth(1);
        LocalDate toTheLastOfTheLastMonth = due.get(due.size() - 1)
                .with(TemporalAdjusters.lastDayOfMonth());
        Set<YearMonth> monthsAlreadySettled = billsSettled
                .whichDueDatesWereSettledBetween(bill.getId(), fromTheFirstOfTheFirstMonth,
                        toTheLastOfTheLastMonth)
                .stream()
                .map(YearMonth::from)
                .collect(Collectors.toSet());
        if (monthsAlreadySettled.isEmpty()) {
            return due;
        }
        // Which months were passed over and why they were asked about at all, because a run that
        // took one debit fewer than a trainer expected is otherwise a silent subtraction.
        log.debug("bill due dates passed over as already settled this month currentAccountId={} "
                        + "billId={} monthsAlreadySettled={} lookedBetween={}..{}",
                bill.getCurrentAccountId(), bill.getId(), monthsAlreadySettled,
                fromTheFirstOfTheFirstMonth, toTheLastOfTheLastMonth);
        return due.stream()
                .filter(date -> !monthsAlreadySettled.contains(YearMonth.from(date)))
                .toList();
    }

    /**
     * As many of the dates a bill is due for as one run will present, oldest first, saying what it
     * left behind when it leaves anything.
     *
     * <p>The cap is applied to the dates that are actually about to be presented — after the record
     * has been asked which are already settled — so that five hundred is five hundred withdrawals and
     * not five hundred questions, and a run is never capped over dates it was going to drop anyway.
     *
     * <p>The WARN names the dates left as well as how many, because "seven hundred dates were left"
     * is a number somebody has to go and reconstruct a calendar from, while the first and the last
     * date left are the two figures that say when the catch-up will finish.
     */
    private List<LocalDate> asManyOfThemAsOneRunCatchesUp(RecurringBill bill, List<LocalDate> due) {
        if (due.size() <= MOST_DUE_DATES_ONE_BILL_CATCHES_UP_IN_ONE_RUN) {
            return due;
        }
        List<LocalDate> thisRun = due.subList(0, MOST_DUE_DATES_ONE_BILL_CATCHES_UP_IN_ONE_RUN);
        List<LocalDate> left = due.subList(MOST_DUE_DATES_ONE_BILL_CATCHES_UP_IN_ONE_RUN,
                due.size());
        log.warn("bill catch-up capped currentAccountId={} billId={} name={} dayOfMonth={} "
                        + "dueDates={} presentedThisRun={} caughtUpTo={} leftForTheNextRun={} "
                        + "firstLeft={} lastLeft={} reason=at most {} due dates are caught up for "
                        + "one bill in one run, so that a clock wound a century forward cannot hang "
                        + "the application; nothing is lost, and the next run continues from the "
                        + "date this one stopped at",
                bill.getCurrentAccountId(), bill.getId(), bill.getName(), bill.getDayOfMonth(),
                due.size(), thisRun.size(), thisRun.get(thisRun.size() - 1), left.size(),
                left.get(0), left.get(left.size() - 1),
                MOST_DUE_DATES_ONE_BILL_CATCHES_UP_IN_ONE_RUN);
        return thisRun;
    }

    /**
     * One bill and one of the dates it fell due on, held together while the whole night is gathered
     * and sorted.
     *
     * <p>A record rather than two parallel lists, mirroring {@code AnOccurrenceToFire}: the pair is
     * what the sort is over, and a pair that could come apart is a sort that could put a date against
     * the wrong bill.
     */
    private record ABillOnItsDate(RecurringBill bill, LocalDate dueOn) {
    }

    /**
     * The bills as the rest of the application reads them, each with the day it was last actually
     * taken beside it.
     *
     * <p>One question for the whole list rather than one per bill, so that drawing an account with
     * twenty bills on it is two queries and not twenty-one. A bill that has never been taken is
     * simply missing from the answer and reads as a null date, which is the honest shape: there is no
     * such day.
     */
    private List<ADeclaredBill> asDeclared(List<RecurringBill> bills) {
        if (bills.isEmpty()) {
            return List.of();
        }
        Map<Long, LocalDate> lastTaken = billsSettled
                .whenEachOfTheseWasLastTaken(bills.stream().map(RecurringBill::getId).toList())
                .stream()
                .collect(Collectors.toMap(WhenABillWasLastTaken::billId,
                        WhenABillWasLastTaken::lastTakenOn));
        return bills.stream()
                .map(bill -> ADeclaredBill.of(bill, lastTaken.get(bill.getId())))
                .toList();
    }

    /** The same for one bill, which is what changing or ending one answers with. */
    private ADeclaredBill asDeclared(RecurringBill bill) {
        return asDeclared(List.of(bill)).get(0);
    }

    /**
     * Credits every payday that has arrived by the given moment and has not been credited yet, each
     * one dated at the day it was due.
     *
     * <p>Every payday rather than the most recent one, which is what makes a monthly rule
     * demonstrable in an afternoon: winding the clock three months forward and running this once
     * credits three salaries, not one. It is not a nicety here but the only path there is — the
     * clock moves in whole calendar days and the cron never fires for the days it skipped, so on a
     * wound clock catching up is the way a payday is <em>ever</em> credited.
     *
     * <p>Oldest first, within an account and across them: the accounts are walked in the order the
     * declarations were made, and each account's paydays in the order the days fell. Deterministic
     * and reconstructable from the log, which is what a trainer pointing at a history needs.
     *
     * <p>Answers nothing, for the reason the loyalty sweep gives: its one caller runs on a schedule
     * with nobody waiting on it, and a figure returned to a scheduled method is a figure nothing can
     * read. What the run did is in the INFO lines below.
     *
     * <p>Public, unlike the rows and the repositories, and not because anything outside wants it:
     * {@code @Transactional} is applied by a proxy and a proxy cannot advise a method that is not
     * public, so the annotation would be silently ignored and a half-finished run would commit. The
     * power this leaks is the power to run the nightly job early, which is idempotent and is exactly
     * what the development jobs endpoint offers anyway.
     *
     * <p>The caller says what time it is. This module reads the clock for a declaration, which has a
     * customer in front of it; a run has to judge every account against one moment, and taking it
     * once in the job is what guarantees that.
     *
     * <p>Idempotent by construction. Every credit writes a row naming the account and the day due,
     * the pair is unique in the database ({@link AccountsOnStartUp}), and a second run finds every
     * day already paid — and would in any case find the cursor already past them.
     */
    @Transactional
    public void creditIncomeDueBy(Instant now) {
        List<MonthlyIncome> declarations = monthlyIncomes.findAllByOrderByIdAsc();
        long paydaysCredited = 0;
        BigDecimal creditedAltogether = BigDecimal.ZERO;
        for (MonthlyIncome income : declarations) {
            if (income.getPaidThrough() == null) {
                // Unreachable in the application as it ships: a declaration sets its cursor and
                // nothing ever clears it. Said out loud because the alternative readings of a
                // missing cursor are a century of back pay or an exception that would abort this
                // one transaction and credit no account at all. Nothing is credited and the cursor
                // is set to this run's moment below, so the next run counts from somewhere.
                log.warn("income cursor missing currentAccountId={} dayOfMonth={} "
                                + "reason=the moment this account is settled through is not "
                                + "recorded, so no payday can be judged against it; nothing is "
                                + "credited and the cursor is set to asAt={}",
                        income.getCurrentAccountId(), income.getDayOfMonth(), now);
            }
            List<LocalDate> due = WhenIncomeIsDue.paydaysBetween(
                    income.getDayOfMonth(), income.getPaidThrough(), now);
            // The inputs behind the decision: the cursor the range was counted from, the day the
            // customer said, and what that came to. A run that credited nothing is explainable from
            // this line without anybody having to reconstruct the calendar by hand.
            log.debug("income considered currentAccountId={} dayOfMonth={} amount={} "
                            + "settledThrough={} asAt={} paydaysDue={}",
                    income.getCurrentAccountId(), income.getDayOfMonth(),
                    AmountOfMoney.asMoney(income.getAmount()), income.getPaidThrough(), now, due);
            boolean everythingDueWasCredited = true;
            for (LocalDate payday : dueInMonthsNotAlreadyPaid(income, due)) {
                if (creditTo(income, payday, now)) {
                    paydaysCredited++;
                    creditedAltogether = creditedAltogether.add(income.getAmount());
                } else {
                    everythingDueWasCredited = false;
                }
            }
            if (everythingDueWasCredited) {
                income.settledThrough(now);
                monthlyIncomes.save(income);
            } else {
                // A payday that was due and was not credited stays due. Moving the cursor over it
                // would lose it for good, and the one thing that stops a credit is an account that
                // is no longer there — which is a state somebody could put right, and which the
                // WARN from the credit itself already names.
                log.warn("income cursor left where it was currentAccountId={} settledThrough={} "
                                + "reason=a payday this run could not credit is still due",
                        income.getCurrentAccountId(), income.getPaidThrough());
            }
        }
        // One line per run with everything that decided it: the moment it judged paydays against,
        // how many declarations it walked, how many paydays it credited and what they came to. A
        // current account that grew overnight is explainable from this line alone, and a run that
        // walked three declarations and credited none can be told from a run that was handed none.
        log.info("monthly income credited asAt={} accountsConsidered={} paydaysCredited={} amount={}",
                now, declarations.size(), paydaysCredited, AmountOfMoney.asMoney(creditedAltogether));
    }

    /**
     * The days of that range falling in a month this account has not already been credited in.
     *
     * <p><strong>A month holds one salary.</strong> That is the rule, and it is the record that
     * keeps it rather than the cursor: the cursor is what keeps a run bounded and the record is what
     * makes it idempotent, and they are different guarantees. Asking the record which exact
     * <em>days</em> were paid keeps neither on its own, and the gap is money — a customer paid on
     * the 5th of January who corrects their payday to the 25th has a 25th of January that no cursor
     * sitting at the 6th excludes and no row under that day denies, and would be paid twice in the
     * month for having told the application when they are paid. The 5th and the 25th are different
     * days; January is one January.
     *
     * <p>It covers the other ways a month can be asked for twice as well, which is why it is here
     * and not in the calendar or in the entity. A declaration withdrawn and made again on a
     * different day starts a fresh cursor over a month already paid for; so does an income declared
     * again after the account has been credited under an older figure. All of them are one question:
     * has this account had its salary for that month.
     *
     * <p>One query for the whole stretch rather than one per day, so that a clock wound three years
     * forward is a query per account and not thirty-six. The stretch is whole months — the first of
     * the first to the last of the last — because which day of a month was paid is exactly what must
     * not be assumed, and {@code due} is oldest first, which {@link WhenIncomeIsDue#paydaysBetween}
     * promises.
     */
    private List<LocalDate> dueInMonthsNotAlreadyPaid(MonthlyIncome income, List<LocalDate> due) {
        if (due.isEmpty()) {
            return due;
        }
        LocalDate fromTheFirstOfTheFirstMonth = due.get(0).withDayOfMonth(1);
        LocalDate toTheLastOfTheLastMonth = due.get(due.size() - 1)
                .with(TemporalAdjusters.lastDayOfMonth());
        Set<YearMonth> monthsAlreadyPaid = incomePaid
                .whichDaysWerePaidBetween(income.getCurrentAccountId(),
                        fromTheFirstOfTheFirstMonth, toTheLastOfTheLastMonth)
                .stream()
                .map(YearMonth::from)
                .collect(Collectors.toSet());
        if (monthsAlreadyPaid.isEmpty()) {
            return due;
        }
        // Which months were skipped and why they were asked about at all, because a run that
        // credited one payday fewer than a trainer expected is otherwise a silent subtraction.
        log.debug("paydays passed over as already credited this month currentAccountId={} "
                        + "monthsAlreadyPaid={} lookedBetween={}..{}",
                income.getCurrentAccountId(), monthsAlreadyPaid,
                fromTheFirstOfTheFirstMonth, toTheLastOfTheLastMonth);
        return due.stream().filter(day -> !monthsAlreadyPaid.contains(YearMonth.from(day))).toList();
    }

    /**
     * Puts one payday's money into the account and writes down that it was paid, in that order and
     * in the run's own transaction, so that neither can exist without the other.
     *
     * @return whether anything was credited, which is false only for a declaration standing against
     *         an account that is no longer there
     */
    private boolean creditTo(MonthlyIncome income, LocalDate payday, Instant now) {
        Optional<CurrentAccount> account = currentAccounts.findById(income.getCurrentAccountId());
        if (account.isEmpty()) {
            // Not reachable in the application as it ships, which never deletes an account. Said out
            // loud rather than skipped silently because a declaration pointing at nothing is the
            // kind of row a participant's own exercise leaves behind, and a run that quietly paid
            // one account fewer than a trainer expected is the hardest sort of nothing to diagnose.
            log.warn("income not credited currentAccountId={} dueOn={} amount={} "
                            + "reason=there is no such current account any more",
                    income.getCurrentAccountId(), payday, AmountOfMoney.asMoney(income.getAmount()));
            return false;
        }
        CurrentAccount credited = account.get();
        credited.deposit(income.getAmount());
        currentAccounts.save(credited);
        incomePaid.save(IncomePaid.of(income.getCurrentAccountId(), payday, now, income.getAmount()));
        // One line per credit, which is the business event: the account, the day it was due, what
        // landed, when it actually landed, and what the account then held. The gap between the two
        // moments is the lateness a caught-up payday was paid with, and it is readable here without
        // anybody having to join two lines together.
        log.info("income credited currentAccountId={} dueOn={} amount={} paidAt={} balance={}",
                income.getCurrentAccountId(), payday, AmountOfMoney.asMoney(income.getAmount()),
                now, AmountOfMoney.asMoney(credited.getBalance()));
        return true;
    }

    /** The row as the rest of the application reads it, with the calendar's next payday beside it. */
    private DeclaredIncome asDeclared(MonthlyIncome income) {
        return new DeclaredIncome(
                income.getCurrentAccountId(),
                income.getDayOfMonth(),
                income.getAmount(),
                income.getDeclaredAt(),
                theNextPaydayOf(income));
    }

    /**
     * The next day money will actually land in this account — which is the next payday the job will
     * credit, and not merely the next one the calendar names.
     *
     * <p>Counted from the account's cursor rather than from the clock, because the job counts from
     * the cursor. Read from midnight, the two disagree for an hour every payday: the job runs at one
     * in the morning, so between 00:00 and 01:00 — and for the whole day if a run is missed or the
     * clock was wound past it — a page reading "now" would name next month while this month's salary
     * is still coming. From the cursor, a payday the job has not credited is still what is next.
     *
     * <p>And then past any month the record already holds a salary for, because that is the rule the
     * run keeps: a customer who moves their payday from the 5th to the 25th halfway through a month
     * they have already been paid in is told the 25th of <em>next</em> month, which is the day the
     * money will land. A next payday that named a day the job has been told to skip would be the
     * same page-against-job disagreement in its other direction.
     */
    private LocalDate theNextPaydayOf(MonthlyIncome income) {
        // A cursor that is not there is read as this moment: nothing is owed from a settling nobody
        // recorded, and the run says the same thing about the same row.
        Instant settledThrough = income.getPaidThrough() == null
                ? clock.instant()
                : income.getPaidThrough();
        LocalDate next = WhenIncomeIsDue.theNextPaydayAfter(income.getDayOfMonth(), settledThrough);
        return incomePaid.findFirstByCurrentAccountIdOrderByDueOnDesc(income.getCurrentAccountId())
                .map(paid -> YearMonth.from(paid.getDueOn()))
                .filter(latestMonthPaid -> !latestMonthPaid.isBefore(YearMonth.from(next)))
                .map(latestMonthPaid -> WhenIncomeIsDue.paydayIn(
                        latestMonthPaid.plusMonths(1), income.getDayOfMonth()))
                .orElse(next);
    }

    /**
     * Refuses anything that is not an income this application will pay, in a sentence that says what
     * is wrong with it.
     *
     * <p>The amount's rule is quoted from {@code AmountOfMoney} rather than written out again, which
     * is what that class exists for: "more than zero, at most two decimal places" is the same
     * question a deposit and a goal's target each ask, and three copies of it are three chances to
     * drift into three different sentences about the same comma. Quoting it is not this module
     * reading Deposits — there is no repository, no entity and no state in it — and the Goals module
     * already shares it on exactly that argument.
     */
    private void refuseUnlessAnIncome(long currentAccountId, int dayOfMonth, BigDecimal amount) {
        if (amount == null) {
            throw refusingTheIncome(currentAccountId, dayOfMonth, null,
                    "Say what lands in the account each month, as an amount of money.");
        }
        Optional<String> notAnAmount = AmountOfMoney.whyItIsNotOne("monthly income", amount);
        if (notAnAmount.isPresent()) {
            throw refusingTheIncome(currentAccountId, dayOfMonth, amount, notAnAmount.get());
        }
        if (!WhenIncomeIsDue.isADayOfTheMonth(dayOfMonth)) {
            throw refusingTheIncome(currentAccountId, dayOfMonth, amount,
                    "An income lands on a day of the month between "
                            + WhenIncomeIsDue.EARLIEST_DAY_OF_THE_MONTH + " and "
                            + WhenIncomeIsDue.LATEST_DAY_OF_THE_MONTH + ", and " + dayOfMonth
                            + " is not one. An income due on the 31st is paid on the last day of a "
                            + "shorter month.");
        }
    }

    /**
     * Every refusal of an income says why in the log as well as to whoever asked, because only one
     * of the two is kept: the reason reaches the person at the keyboard and nowhere else.
     */
    private MonthlyIncomeRefused refusingTheIncome(long currentAccountId, int dayOfMonth,
                                                   BigDecimal amountAsGiven, String reason) {
        log.warn("monthly income rejected currentAccountId={} dayOfMonth={} amount={} kind={} "
                        + "reason={}",
                currentAccountId, dayOfMonth,
                amountAsGiven == null ? null : amountAsGiven.toPlainString(),
                AGAINST_THE_RULES, reason);
        return new MonthlyIncomeRefused(AGAINST_THE_RULES, reason);
    }

    /**
     * That bill on that account, or a refusal that it is not there.
     *
     * <p>Scoped by the account in the path, so a bill identifier somebody guessed answers as a bill
     * that is not there rather than with somebody else's rent. It is the whole of what this
     * application can say about whose a bill is: there is no authentication to ask, and the same
     * answer a saving rule gives for the same reason.
     */
    private RecurringBill theBillOn(long currentAccountId, long billId) {
        return recurringBills.findByIdAndCurrentAccountId(billId, currentAccountId)
                .orElseThrow(() -> refusingTheBill(currentAccountId, billId, NO_SUCH_BILL,
                        "There is no bill " + billId + " on current account " + currentAccountId
                                + "."));
    }

    /**
     * That the bill is still an instruction rather than only a record.
     *
     * <p>The one state a bill can be refused for, because there are only two of them and ending is
     * one-way: a bill that has been ended cannot be changed and cannot be ended again, and the kind
     * is named after exactly that.
     */
    private void refuseUnlessTheBillHasNotBeenEnded(long currentAccountId, RecurringBill bill,
                                                    String whatWasAsked) {
        if (bill.isEnded()) {
            throw refusingTheBill(currentAccountId, bill.getId(), THE_BILL_IS_ENDED,
                    "\"" + bill.getName() + "\" was ended and cannot be " + whatWasAsked
                            + ". Declare it again if you are paying it once more.");
        }
    }

    /**
     * That the account has not already got as many bills standing as this application will keep for
     * one of them.
     *
     * <p>Per account rather than per customer, which is where it differs from the cap on saving
     * rules: what the limit protects here is the nightly run that walks one account's bills, and a
     * household with two current accounts genuinely has two sets of things going out. Ended bills
     * are not counted — they are not on the page and nothing will take them, so they cost neither of
     * the things the cap protects, and ending one is what makes room for another.
     *
     * <p>Twenty, mirroring the ten one customer may leave standing in saving rules and for the
     * identical reason: an unbounded list is a denial of service on every later nightly run, and the
     * figure is large enough that no real household meets it.
     */
    private void refuseUnlessThereIsRoomForAnotherBill(long currentAccountId) {
        long standing = recurringBills.countByCurrentAccountIdAndStateIn(currentAccountId,
                BillState.theOnesStillStanding());
        log.debug("bills standing on the account currentAccountId={} standing={} limit={}",
                currentAccountId, standing, HOW_MANY_BILLS_ONE_ACCOUNT_MAY_CARRY);
        if (standing >= HOW_MANY_BILLS_ONE_ACCOUNT_MAY_CARRY) {
            throw refusingTheBill(currentAccountId, null, RecurringBillRefused.Kind.AGAINST_THE_RULES,
                    "This account already has " + standing + " bills standing, and "
                            + HOW_MANY_BILLS_ONE_ACCOUNT_MAY_CARRY + " is the most one account can "
                            + "carry at once. End one you no longer pay before declaring another.");
        }
    }

    /**
     * Whether the bill, as it would read once this declaration or change is made, is one this
     * application will keep — and the bill itself, tidied, if it is.
     *
     * <p>One function for declaring a bill and for changing one, for the reason
     * {@code AutomationService.judged} gives: a bill that would be refused if it were typed from
     * scratch should not be reachable by editing one that was not. It answers with the values to
     * write rather than merely saying yes, because judging and tidying are the same pass — the name
     * is trimmed and the figure is quoted to the cent exactly where it was established that they
     * could be.
     *
     * <p>The three objections are asked in the order the boxes are on the screen, so a customer
     * fixing them works downwards rather than being sent back up.
     *
     * <p>The amount's rule is quoted from {@code AmountOfMoney} rather than written out again, which
     * is what that class exists for: "more than zero, at most two decimal places" is the same
     * question a deposit, a goal's target and a declared income each ask, and a fourth copy of it is
     * a fourth chance to drift into a different sentence about the same comma. Refused rather than
     * rounded, which is that class's decision and not this one's.
     *
     * <p>The day's rule is quoted from {@link WhenABillIsDue} for the same reason, and the sentence
     * around it is this one's own because it is about a bill. 1 to 31 with the 31st accepted: a rent
     * taken on the last day of the month is declared as the 31st, and refusing it here would make a
     * real bill undeclarable. Which day a short month takes it on is arithmetic made when the bill
     * is taken, by that same class — so the rule about which days may be typed and the rule about
     * what they mean in February are one place rather than two.
     *
     * @param billId the bill being changed, or null while one is being declared, for the log
     */
    private ABillAsItWouldRead judgedAsABill(long currentAccountId, Long billId, String name,
                                             Integer dayOfMonth, BigDecimal amount) {
        String itsName = name == null ? "" : name.trim();
        if (itsName.isBlank()) {
            throw refusingTheBill(currentAccountId, billId, RecurringBillRefused.Kind.AGAINST_THE_RULES,
                    "Give the bill a name, so you can tell the rent from the phone bill when you "
                            + "look at your account.");
        }
        if (dayOfMonth == null) {
            throw refusingTheBill(currentAccountId, billId, RecurringBillRefused.Kind.AGAINST_THE_RULES,
                    "Say which day of the month this bill goes out on.");
        }
        if (!WhenABillIsDue.isADayOfTheMonth(dayOfMonth)) {
            throw refusingTheBill(currentAccountId, billId, RecurringBillRefused.Kind.AGAINST_THE_RULES,
                    "A bill goes out on a day of the month between "
                            + WhenABillIsDue.EARLIEST_DAY_OF_THE_MONTH + " and "
                            + WhenABillIsDue.LATEST_DAY_OF_THE_MONTH + ", and " + dayOfMonth
                            + " is not one. A bill due on the 31st is taken on the last day of a "
                            + "shorter month.");
        }
        if (amount == null) {
            throw refusingTheBill(currentAccountId, billId, RecurringBillRefused.Kind.AGAINST_THE_RULES,
                    "Say what this bill is worth, as an amount of money.");
        }
        Optional<String> notAnAmount = AmountOfMoney.whyItIsNotOne("bill", amount);
        if (notAnAmount.isPresent()) {
            throw refusingTheBill(currentAccountId, billId, RecurringBillRefused.Kind.AGAINST_THE_RULES,
                    notAnAmount.get());
        }
        return new ABillAsItWouldRead(itsName, dayOfMonth, AmountOfMoney.quotedToTheCent(amount));
    }

    /**
     * Every refusal of a bill says why in the log as well as to whoever asked, because only one of
     * the two is kept: the reason reaches the person at the keyboard and nowhere else. The account
     * and the bill are on the line because a reviewer tracing "it would not take my rent" needs to
     * know which account's bills were being argued about.
     */
    private RecurringBillRefused refusingTheBill(long currentAccountId, Long billId,
                                                 RecurringBillRefused.Kind kind, String reason) {
        log.warn("bill rejected currentAccountId={} billId={} kind={} reason={}",
                currentAccountId, billId, kind, reason);
        return new RecurringBillRefused(kind, reason);
    }

    /**
     * A bill as it would read once what somebody asked for has been applied, with the name trimmed
     * and the figure quoted to the cent.
     *
     * <p>A record rather than three out-parameters, mirroring {@code ARuleAsItWouldRead}: the values
     * that will be written travel together from the one place that decided they were legal, so the
     * write below cannot quietly use the untrimmed name it was handed.
     */
    private record ABillAsItWouldRead(String name, int dayOfMonth, BigDecimal amount) {
    }
}
