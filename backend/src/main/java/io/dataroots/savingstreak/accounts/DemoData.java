package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.dataroots.savingstreak.budgets.APartAsAsked;
import io.dataroots.savingstreak.budgets.ASpendAsAsked;
import io.dataroots.savingstreak.budgets.BudgetsService;
import io.dataroots.savingstreak.budgets.RolloverRule;
import io.dataroots.savingstreak.budgets.SpendsService;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.goals.GoalsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the customers a trainer demonstrates against, each with an account to deposit from and at
 * least one account to deposit into, and each with a household already living in it — a salary
 * arriving, real bills going out, the things their money goes on named, a figure put on the ones
 * they police and a few weeks of spending already filed against it — so the app is useful seconds
 * after it starts. Seeding only happens when there are no customers at all, so restarting the app
 * does not multiply them; deleting the database file and starting again returns the app to exactly
 * this state.
 *
 * <p><strong>Declared, never generated.</strong> There is no generator and no random spending here,
 * for the reason {@link RecurringBill} gives about the feature as a whole: a household scenario is a
 * named set of declarations written out below and nothing else, so that everyone who resets the
 * database gets the identical account back and a demonstration given yesterday is the demonstration
 * given today. The income, the bills, the categories, the budgets and every spend go in through the
 * services rather than straight into the repositories, so the seeded household is held to exactly
 * the rules a customer typing the same figures would meet — a seed able to declare a bill the
 * application would refuse, or to file a split that does not add up, is a seed that lies about the
 * application.
 *
 * <p><strong>Why this class knows about three modules and nothing else in {@code accounts} does.
 * </strong> {@code budgets} reads Accounts' public read models and takes money through
 * {@link AccountsService#withdrawFrom}; Accounts does not know that module exists, and a category
 * reference on {@link RecurringBill} was rejected precisely to keep it that way. This class is the
 * exception because it is not the application: it is the composition of one demonstration, it runs
 * only under the {@code dev} profile, nothing in the running application reads it, and it already
 * reached across for {@code AmountOfMoney} before any of this existed. The alternative — a second
 * seeder living in {@code budgets} — could not see whether the database was empty when it started,
 * so "the seed fires only on a database with no customers in it" would stop being one guarantee and
 * become two that can disagree. One gate, one transaction, one place to read what a trainer is about
 * to be shown.
 *
 * <p><strong>Why the two shapes are different, and why the gap between them is load-bearing.</strong>
 * The point of declaring what leaves a current account is that saving becomes a claim on money
 * something else also wants. One household teaches that by itself only if you already know the
 * answer; two households with visibly different room to manoeuvre teach it by comparison, which is
 * why the numbers below are not two samples of the same shape.
 *
 * <p><strong>Anke is comfortable.</strong> EUR 2 600,00 lands on the 25th and EUR 1 165,00 goes out
 * across four bills — rent on the 1st, energy on the 5th, phone on the 12th, insurance on the 20th —
 * leaving about EUR 1 435,00 of room every month. She can carry a real saving rule and still pay for
 * everything, she holds EUR 2 480,00 when the seed is done, and her last bill of the month falls
 * five days before her salary does. Sweeping the lot is a mistake she survives for a while rather
 * than one that bites on the first cycle, which is what makes her the household a trainer shows
 * saving <em>working</em> on.
 *
 * <p><strong>Bram is tight.</strong> EUR 1 750,00 lands on the 28th and EUR 945,00 goes out across
 * three bills — rent on the 1st, energy on the 5th, phone on the 12th — leaving about EUR 805,00.
 * The figure that matters is not the size of that gap but where his rent sits: four days after
 * payday, and the largest single thing he owes. A saving rule that takes his salary on the morning
 * it arrives leaves nothing standing between him and the rent, and the rent goes unpaid inside one
 * cycle. That is the demonstration this whole feature exists for.
 *
 * <p><strong>Bram's failure has to be reachable and must not be automatic.</strong> EUR 805,00 of
 * room against EUR 945,00 of bills means a fixed-amount rule sized at or under his surplus runs for
 * as many months as anyone cares to wind the clock through without missing anything: he holds
 * EUR 1 150,00, which is more than a month of his bills, and every month afterwards pays for itself.
 * Only over-committing — sweeping the account to a floor of nothing, or naming a figure above
 * EUR 805,00 — starves the rent. If a later adjustment closes that gap so that he misses the rent
 * whatever he does, this application stops teaching that saving is a judgement and starts teaching
 * that saving is impossible, which is the opposite lesson. The three numbers to keep apart are
 * therefore: his income, the total of his bills, and whatever rule the demonstration puts on him.
 *
 * <p><strong>And the budgets are the same demonstration run a second time, one level down.</strong>
 * Anke has put a figure on the things she can actually change and is comfortably inside all of them,
 * so her surplus carries forward and a trainer winding the clock sees a frugal month buy a generous
 * one. Bram has budgeted groceries at EUR 200,00 — an honest, optimistic figure for a household as
 * tight as his — and has spent EUR 455,00 against it, under the envelope rule. The overspend follows
 * him: next month starts at <em>minus</em> EUR 55,00 of room on groceries, the overspending alert is
 * raised on him on the first sweep of that month with nothing yet spent in it, and it is raised on
 * her not at all. Two rollover rules doing opposite work on the same night, which is what user
 * stories 46 and 47 ask to be able to see in one sitting.
 *
 * <p><strong>And Anke is the one with a decision to make.</strong> Under the {@code demo} profile —
 * which is how {@code spring-boot:run} starts this application and is not how the test suite does —
 * her first savings account also arrives with weeks of saving already behind it: three deposits a
 * week apart, a withdrawal that leaves her below her own high-water mark, a run of secured weeks, two
 * dated goals that her declared capacity cannot both pay for and a rule quietly moving sixty euros
 * every Tuesday. None of that could be declared, because all of it is derived from a ledger, and
 * seeding it means moving the development clock; {@link AHouseholdWithADecisionToMake} holds every
 * figure and the whole of the argument for why it is a profile of its own. What matters here is the
 * order it goes in at, which is twice: the history before anything with a cursor in it is declared,
 * and the goals and the rule after, both of them marked in {@link #seed}.
 *
 * <p><strong>Why every seeded category is kept below four fifths of what it allows except the one
 * that is over.</strong> A category at or above eighty per cent earns the amber "running low" line,
 * which is a true thing to say and the wrong thing to have said on the screen a trainer is using to
 * contrast a household that is fine with a household that is not. So Anke's categories sit near half
 * spent, with enough headroom that the bills falling into them next month do not push any of them
 * across, and Bram's second category sits there too — the only warning either household earns is the
 * one about his groceries. That is a constraint on the figures below, not a coincidence of them.
 *
 * <p><strong>The spends are a few weeks of a household's life recorded in one sitting.</strong> A
 * spend is not backdated — the day it counts to is the day the application's clock reads, and
 * {@code SpendsService} has no other moment to take it from — so all of them land on the day the
 * database was created, in the month the database was created in. They are named and sized as three
 * weeks of shopping because that is what they are a record of; a reader of the ledger sees them at
 * one moment, and the honest way to get a second month of history is the way every other history in
 * this application is made, by winding the clock.
 *
 * <p><strong>What must not be touched.</strong> The names, the contact details, the ibans and what
 * each account holds when the seed has finished are constants the shared test fixtures find their
 * accounts by and assert against. The last of those is the reason the accounts are <em>opened</em>
 * with more than they end up holding: the seeded spends really take their money, through the same
 * all-or-nothing withdrawal a customer's would, so the figure each account is opened with is the
 * figure it must be left holding plus everything the seeded weeks spent — arithmetic, in
 * {@link AHousehold#spentInTheSeededWeeks()} and, for the household that carries a decision,
 * {@link AHouseholdWithADecisionToMake#takenOutOfTheEverydayAccount()} — rather than a second
 * constant that the first change to a supermarket trip or to a seeded deposit would silently make
 * wrong. The income and the bills may be adjusted with the reasoning above in hand; those five may
 * not.
 */
@Component
@Profile("dev")
class DemoData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final CustomerRepository customers;
    private final CurrentAccountRepository currentAccounts;
    private final SavingsAccountRepository savingsAccounts;

    /**
     * The one door a declaration goes through, used here rather than the repositories so that the
     * seeded household meets the same rules, writes the same rows and leaves the same log lines a
     * customer declaring it by hand would. It also starts every cursor at the moment of seeding,
     * which is what makes the seeded bills a promise about the month ahead rather than a year of
     * back rent presented on the first nightly run.
     */
    private final AccountsService accounts;

    /**
     * The same door for the half of a household that says where the money goes: the categories, the
     * bills filed into them and the figure put on each. Asked here rather than written as rows for
     * the reason above — a seeded budget that the application would have refused is a budget nobody
     * can reproduce by typing it.
     */
    private final BudgetsService budgets;

    /**
     * And the door a spend goes through, which is the only one of the three that moves money. Every
     * seeded spend is taken out of the balance by the same all-or-nothing withdrawal a customer's
     * is, which is why the accounts are opened with more than they are left holding.
     */
    private final SpendsService spends;

    /**
     * What each household says they can save in a week, declared here so that the weekly forecast
     * has something to sit beside on the automatic-saving page from the first minute.
     *
     * <p>Both figures are deliberately modest — below what six weeks of their own budgets say they
     * could put away — because the thing worth demonstrating is the offer: the application says "your
     * own budget says this much" beside "you said this much", and adopting it is one press. Two
     * households that had already declared more than their budgets offer would leave that press with
     * nothing to do.
     *
     * <p><strong>And Anke's forty euros is the most load-bearing single figure in this seed.</strong>
     * It sits <em>below</em> the weekly minimum version 1 of the scheme publishes, EUR 50,00, on
     * purpose, so
     * that her own weekly plan says in as many words that a week is not secured at the rate she has
     * declared. That one fact is what gives four separate screens something to say: the goals are
     * funded at forty and so the first of them is off track and the second gets nothing at all;
     * asking what another twenty-five a week would do is a question about crossing a line rather than
     * about a bigger number; and adopting that branch visibly turns "a week is not secured at this
     * rate" off. Raised to or above fifty, every one of those goes quiet.
     */
    private final GoalsService goals;

    /**
     * The lived-in half of the demonstration, present only when the {@code demo} profile is on and
     * absent under the bare {@code dev} profile the test suite runs — see
     * {@link AHouseholdWithADecisionToMake} for why a run of secured weeks cannot be seeded into the
     * same Anke that a hundred test classes count their first week from. An {@link Optional} rather
     * than a second seeder, so that there is still one gate, one transaction and one place to read
     * what a trainer is about to be shown.
     */
    private final Optional<AHouseholdWithADecisionToMake> aDecisionToMake;

    /**
     * Whatever keeps the record of which agreement a savings account is living under, told about
     * each account this seed opens.
     *
     * <p>Needed here because this class writes its accounts through the repository rather than
     * through {@link AccountsService#addCustomer} — a seeded household is opened with a stated
     * balance and a stated number of savings accounts, neither of which that door offers — so the
     * announcement that door makes has to be made here too. The alternative is a demonstration
     * whose accounts are the only ones in the application with no agreement until the next restart,
     * and every deposit into them stamped with no version.
     */
    private final WhoeverRecordsWhatASavingsAccountIsOn whatAnAccountIsPutOn;

    /**
     * The other half of the lived-in demonstration, present only under the {@code demo} profile like
     * the household one above it: the saver who holds an account on each of the four savings
     * products, three of them with a quarter of interest already paid into them.
     *
     * <p>An {@link Optional} of an interface this module declares, rather than a class it names, for
     * the reason {@link WhoeverGivesTheDemonstrationItsSavingsProducts} gives — Accounts cannot see
     * the Products module and does not have to in order to say where the seed has got to.
     *
     * <p><strong>Called from {@link #run} and not from {@link #seed}, and the order is the whole
     * point.</strong> Its first sentence spends three months of development clock, and every bill,
     * salary, budget and spend below starts a cursor at the moment it is declared: on the near side
     * of that wind both households would open a quarter in arrears and the first nightly run of the
     * demonstration would present three months of back rent. So it goes first of all, after the gate
     * and before a household exists. Its second sentence goes last of all, because what it opens is
     * meant to read as opened on the morning the demonstration starts.
     */
    private final Optional<WhoeverGivesTheDemonstrationItsSavingsProducts> theSavingsProducts;

    DemoData(CustomerRepository customers,
             CurrentAccountRepository currentAccounts,
             SavingsAccountRepository savingsAccounts,
             AccountsService accounts,
             BudgetsService budgets,
             SpendsService spends,
             GoalsService goals,
             WhoeverRecordsWhatASavingsAccountIsOn whatAnAccountIsPutOn,
             Optional<AHouseholdWithADecisionToMake> aDecisionToMake,
             Optional<WhoeverGivesTheDemonstrationItsSavingsProducts> theSavingsProducts) {
        this.aDecisionToMake = aDecisionToMake;
        this.theSavingsProducts = theSavingsProducts;
        this.whatAnAccountIsPutOn = whatAnAccountIsPutOn;
        this.customers = customers;
        this.currentAccounts = currentAccounts;
        this.savingsAccounts = savingsAccounts;
        this.accounts = accounts;
        this.budgets = budgets;
        this.spends = spends;
        this.goals = goals;
    }

    /**
     * Spring calls this through the bean's proxy, so the check and the inserts genuinely share one
     * transaction. Splitting the work into a @Transactional method called from here would not:
     * self-invocation bypasses the proxy and the annotation would do nothing.
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (customers.count() > 0) {
            return;
        }
        // The savings products, first of all and before a household exists, because giving three
        // accounts a quarter of interest costs a quarter of development clock and no cursor in
        // either household may be started on the near side of it. See the field's own note.
        theSavingsProducts.ifPresent(
                WhoeverGivesTheDemonstrationItsSavingsProducts::openTheAccountsThatHaveMonthsBehindThem);
        // Anke saves towards two goals at once, so a demo can show that a deposit into one savings
        // account leaves the other where it was. Her current account is the deeper of the two, so
        // that a session of demonstrating deposits does not run it dry.
        //
        // Her budgets are the comfortable half of the comparison. Groceries and Transport carry
        // their surplus, so a trainer winding the clock one month watches EUR 260,00 and the rest of
        // the fuel money arrive in the following month as extra room; Utilities and Going out carry
        // nothing, so the same wind shows two categories starting clean beside two that did not.
        // Housing carries the rent and no figure at all, because a limit on a rent is a limit nobody
        // can act on — it is the category she watches rather than polices, which is user story 10.
        seed("Anke Peeters", "anke.peeters@example.be", "BE68539007547034", "2480.00", 2,
                new AHousehold(25, "2600.00",
                        List.of(bill("Rent", 1, "950.00"),
                                bill("Energy", 5, "120.00"),
                                bill("Phone", 12, "35.00"),
                                bill("Insurance", 20, "60.00")),
                        List.of(budgeted("Groceries", "500.00", RolloverRule.THE_SURPLUS_ROLLS_OVER),
                                watched("Housing", "Rent"),
                                budgeted("Utilities", "250.00", RolloverRule.NOTHING_ROLLS_OVER,
                                        "Energy", "Phone"),
                                // The spec's own worked example, seeded: a budgeted category holding
                                // a standing bill and a spend, so the screen can say which part of
                                // it she could do something about and which part had already left.
                                budgeted("Transport", "200.00", RolloverRule.THE_SURPLUS_ROLLS_OVER,
                                        "Insurance"),
                                budgeted("Going out", "150.00", RolloverRule.NOTHING_ROLLS_OVER)),
                        List.of(spend("Supermarket, the weekly shop", on("Groceries", "76.45")),
                                spend("Market and bakery", on("Groceries", "34.20")),
                                // One trip that was two things, which is user story 15 and the
                                // reason a split exists at all.
                                spend("Supermarket and a bottle of wine",
                                        on("Groceries", "52.30"), on("Going out", "16.00")),
                                spend("Dinner with friends", on("Going out", "46.50")),
                                spend("Fuel", on("Transport", "55.00")),
                                // Filed under nothing, deliberately. Recording it at all is never
                                // meant to be the hard part, and a demo with no unfiled euros in it
                                // has nowhere to show the correction that files them.
                                spend("Cash machine", unfiled("40.00")),
                                spend("The big shop before the weekend", on("Groceries", "77.05"))),
                        "40.00"),
                aDecisionToMake);
        // Bram's is deliberately shallower: a deposit bigger than this is refused, and being told so
        // is part of what there is to demonstrate. His household is the tight one — see the class
        // documentation for why the rent on the 1st and the salary on the 28th are the pair that
        // makes the demonstration work.
        //
        // And his groceries are the other half of the budget comparison. EUR 200,00 under the
        // envelope rule against EUR 455,00 actually spent: he is over by more than the envelope
        // held, so next month opens at minus EUR 55,00 rather than merely short — the one state only
        // this rule can produce, and the state that makes the overspending alert fire on him on a
        // month he has not spent a cent in yet. Everything else of his is either unbudgeted or a
        // long way inside, so the alert a trainer sees points at exactly one thing.
        seed("Bram De Vos", "bram.devos@example.be", "BE87734291658494", "1150.00", 1,
                new AHousehold(28, "1750.00",
                        List.of(bill("Rent", 1, "820.00"),
                                bill("Energy", 5, "95.00"),
                                bill("Phone", 12, "30.00")),
                        List.of(budgeted("Groceries", "200.00",
                                        RolloverRule.THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER),
                                watched("Housing", "Rent"),
                                watched("Utilities", "Energy", "Phone"),
                                budgeted("Going out", "60.00", RolloverRule.NOTHING_ROLLS_OVER)),
                        List.of(spend("Supermarket", on("Groceries", "92.80")),
                                spend("Supermarket again", on("Groceries", "88.45")),
                                spend("Corner shop, late", on("Groceries", "24.75")),
                                // Half filed and half not: the honest record of somebody in a hurry,
                                // and the split a correction is actually for.
                                spend("Supermarket and something for the flat",
                                        on("Groceries", "72.00"), unfiled("23.00")),
                                spend("The big shop", on("Groceries", "104.60")),
                                spend("Takeaway", on("Going out", "22.50")),
                                spend("Groceries, end of the week", on("Groceries", "72.40"))),
                        "50.00"),
                Optional.empty());
        // And last of all, on the day the demonstration opens: the empty twelve-month term with its
        // whole length still to run, and a notice with its whole thirty-two days still to run. Both
        // are about today, so both wait until every household has been written and the clock is
        // standing where a trainer will find it.
        theSavingsProducts.ifPresent(WhoeverGivesTheDemonstrationItsSavingsProducts
                ::openTheAccountsThatAreOpenedOnTheDayItStarts);
    }

    /**
     * One household written into an empty database, in the order a customer would have built it up:
     * the accounts, then what arrives, then what is committed, then the words they file money under,
     * then the figures on those words, and only then the spending that is measured against them.
     *
     * <p>The order is not decoration. A bill cannot be put in a category that has not been named, a
     * budget cannot be declared on one either, and a spend naming a category the account does not
     * carry is refused in words — so the sequence below is the only one the application would accept
     * from anybody, which is exactly the property this seed is supposed to have.
     *
     * @param heldWhenTheSeedIsDone what the current account must be left holding, which is the
     *                              constant the shared test fixtures assert against. The account is
     *                              opened with this plus everything the seeded weeks spend, because
     *                              the spends really take their money.
     */
    private void seed(String name, String contactDetails, String iban, String heldWhenTheSeedIsDone,
                      int savingsAccountsHeld, AHousehold household,
                      Optional<AHouseholdWithADecisionToMake> aDecisionToMake) {
        Customer customer = customers.save(new Customer(name, contactDetails));
        BigDecimal held = new BigDecimal(heldWhenTheSeedIsDone);
        BigDecimal openedWith = held.add(household.spentInTheSeededWeeks())
                // And whatever the weeks already saved took out of it, for exactly the reason the
                // spends are added on: those deposits really leave this account, so the figure it is
                // opened with is the figure it must be left holding plus everything the seeding of a
                // history moves out of it. Net of what came back, because a withdrawal from savings
                // lands here again.
                .add(aDecisionToMake.map(AHouseholdWithADecisionToMake::takenOutOfTheEverydayAccount)
                        .orElse(BigDecimal.ZERO));
        CurrentAccount toSpendFrom = currentAccounts.save(
                new CurrentAccount(customer, iban, openedWith));
        long account = toSpendFrom.getId();
        long theFirstPot = 0;
        for (int i = 0; i < savingsAccountsHeld; i++) {
            SavingsAccount opened = savingsAccounts.save(new SavingsAccount(customer));
            // On free savings, like every account this application opens, and said here because
            // this seed writes its accounts through the repository rather than through the door
            // that would have said it. Before the weeks behind her are paid in, so that every
            // seeded deposit is stamped with the version it landed under.
            whatAnAccountIsPutOn.aSavingsAccountWasOpened(opened.getId());
            if (i == 0) {
                theFirstPot = opened.getId();
                // The first pot only, and the capacity is declared here rather than at the end so
                // that there is no way to write one against an account this household never opened.
                // A capacity is per savings account, deliberately — a customer saving into two of
                // them is saving at two rates — so the second is left unsaid, which is also what
                // keeps "a figure on one account leaves another of the same customer's unset"
                // assertable against this seed.
                goals.declareSavingCapacity(opened.getId(),
                        new BigDecimal(household.canSaveWeekly()));
            }
        }
        // The weeks already behind her, before anything with a cursor in it is declared: these move
        // the clock, and a salary or a bill declared on the far side of that move would open the
        // demonstration a fortnight in arrears. The first pot, always — the second is left pristine
        // on purpose, so that a trainer who has adopted a branch on one of them still holds an
        // untouched one to ask the same question about again.
        long savingsAccount = theFirstPot;
        aDecisionToMake.ifPresent(decision ->
                decision.giveHerTheWeeksAlreadyBehindHer(account, savingsAccount));
        accounts.declareMonthlyIncome(account, household.paidOn(),
                new BigDecimal(household.income()));
        // The identifiers the declarations below name each other by. Kept in the order they were
        // declared in, so a reader of the log sees the household built in the order it is written.
        Map<String, Long> billIds = new LinkedHashMap<>();
        for (ABillAsAsked declaration : household.bills()) {
            billIds.put(declaration.name(), accounts.declareABill(account, declaration).billId());
        }
        Map<String, Long> categoryIds = new LinkedHashMap<>();
        for (ACategoryOnTheHousehold declared : household.categories()) {
            long categoryId = budgets.declareACategory(account, declared.name()).categoryId();
            categoryIds.put(declared.name(), categoryId);
            for (String billInIt : declared.bills()) {
                budgets.putBillInACategory(account, billIds.get(billInIt), categoryId);
            }
            if (declared.isBudgeted()) {
                budgets.declareABudget(account, categoryId, new BigDecimal(declared.budget()),
                        declared.rollover());
            }
        }
        for (ASpendInTheSeededWeeks declared : household.spends()) {
            // A part with no category is filed under nothing, which Map.get answers with null for —
            // the same nothing a customer sends when they have not decided yet.
            spends.recordASpend(account, new ASpendAsAsked(declared.name(), declared.amount(),
                    declared.parts().stream()
                            .map(part -> new APartAsAsked(categoryIds.get(part.category()),
                                    new BigDecimal(part.amount())))
                            .toList()));
        }
        // And the decision itself last of all, because a rule left standing needs the salary it
        // draws against to have been declared and its deadlines are counted off the clock the winds
        // above have already moved.
        aDecisionToMake.ifPresent(decision ->
                decision.giveHerTheDecisionSheIsWeighingUp(account, savingsAccount));
        // One line per household seeded, with the three figures a reader compares: what arrives,
        // what leaves, and what is left over. A demonstration that goes wrong is almost always a
        // rule sized against the wrong one of those, and this line is where the right one is read
        // off without opening the source. The bills are named as well as counted, because "four
        // bills totalling EUR 1 165,00" does not say which of them a nightly run failed to take.
        // What the account was opened with is here beside what it is left holding, because those two
        // are different for the first time in this seed's life and a reviewer reading a balance that
        // is not the figure in the source should find the difference explained on the same line.
        log.info("household seeded customerId={} name={} currentAccountId={} openedWith={} "
                        + "spentInTheSeededWeeks={} holding={} incomeDayOfMonth={} income={} "
                        + "bills={} billedEachMonth={} leftEachMonth={} declared={} "
                        + "savingsAccounts={} canSaveWeekly={}",
                customer.getId(), customer.getName(), account, AmountOfMoney.asMoney(openedWith),
                AmountOfMoney.asMoney(household.spentInTheSeededWeeks()),
                AmountOfMoney.asMoney(held), household.paidOn(),
                AmountOfMoney.asMoney(new BigDecimal(household.income())), household.bills().size(),
                AmountOfMoney.asMoney(household.billedEachMonth()),
                AmountOfMoney.asMoney(household.leftEachMonth()), household.inWords(),
                savingsAccountsHeld, AmountOfMoney.asMoney(new BigDecimal(household.canSaveWeekly())));
        // And one line saying where every figure the demonstration turns on already stands, before
        // anybody opens a screen: what each category allows, under which rule, what the seeded weeks
        // put against it and what that leaves. The category that is over is the one the nightly
        // sweep will warn about and the one whose next month opens short, and this is the line that
        // says so without anybody having to add up seven supermarket trips by hand.
        log.info("household budgets seeded customerId={} currentAccountId={} categories={} "
                        + "budgeted={} unfiled={} standing={}",
                customer.getId(), account, household.categories().size(),
                household.howManyAreBudgeted(),
                AmountOfMoney.asMoney(household.spentUnder(null)),
                household.whereTheBudgetsStand());
    }

    /** One declaration, in the shape {@link AccountsService#declareABill} takes it. */
    private static ABillAsAsked bill(String name, int dayOfMonth, String amount) {
        return new ABillAsAsked(name, dayOfMonth, new BigDecimal(amount));
    }

    /** A category with a figure on it and a rule for what becomes of the difference. */
    private static ACategoryOnTheHousehold budgeted(String name, String budget,
                                                    RolloverRule rollover, String... billsInIt) {
        return new ACategoryOnTheHousehold(name, List.of(billsInIt), budget, rollover);
    }

    /**
     * A category with no figure on it: a word its holder wants their money described in without
     * having decided to police it, which is a state the application supports on purpose and which
     * every household in this seed uses for its rent.
     */
    private static ACategoryOnTheHousehold watched(String name, String... billsInIt) {
        return new ACategoryOnTheHousehold(name, List.of(billsInIt), null, null);
    }

    /** One outgoing and the split it was recorded with. */
    private static ASpendInTheSeededWeeks spend(String name, APartOfASpend... parts) {
        return new ASpendInTheSeededWeeks(name, List.of(parts));
    }

    /** Part of a spend, filed under a category this household carries. */
    private static APartOfASpend on(String category, String amount) {
        return new APartOfASpend(category, amount);
    }

    /** And part of one filed under nothing at all, which is uncategorised rather than missing. */
    private static APartOfASpend unfiled(String amount) {
        return new APartOfASpend(null, amount);
    }

    /**
     * One household as it is written down here: the day the salary lands, what lands, everything
     * that leaves on a standing order, the words the rest of it is described in, the weeks of
     * spending already filed under them, and what its holder says they can save in a week.
     *
     * <p>A record rather than a longer parameter list, because they travel together and the figures
     * a reader actually judges the shape by — what goes out every month, what is left, what each
     * budget has already had spent against it — are arithmetic on them rather than further
     * constants. Written out as constants they would be more numbers to keep in step with the bills
     * and the supermarket trips, and the first adjustment to a rent or a weekly shop would make them
     * wrong silently.
     */
    private record AHousehold(int paidOn, String income, List<ABillAsAsked> bills,
                              List<ACategoryOnTheHousehold> categories,
                              List<ASpendInTheSeededWeeks> spends, String canSaveWeekly) {

        /** What leaves every month, which is the figure a saving rule has to be sized against. */
        BigDecimal billedEachMonth() {
            return bills.stream().map(ABillAsAsked::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** And what is left after it: the room this household really has to save in. */
        BigDecimal leftEachMonth() {
            return new BigDecimal(income).subtract(billedEachMonth());
        }

        /**
         * Everything the seeded weeks take out of the balance.
         *
         * <p>The figure the current account has to be opened above what it is meant to end up
         * holding by. Derived rather than declared, so that changing a supermarket trip cannot
         * quietly change the balance the shared test fixtures assert against.
         */
        BigDecimal spentInTheSeededWeeks() {
            return spends.stream().map(ASpendInTheSeededWeeks::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** How many of the words this household uses actually carry a limit. */
        long howManyAreBudgeted() {
            return categories.stream().filter(ACategoryOnTheHousehold::isBudgeted).count();
        }

        /**
         * What the seeded weeks put under one category — or, for a null name, what they left filed
         * under nothing, which is the figure a correction demo starts from.
         */
        BigDecimal spentUnder(String category) {
            return spends.stream()
                    .flatMap(spend -> spend.parts().stream())
                    .filter(part -> Objects.equals(part.category(), category))
                    .map(part -> new BigDecimal(part.amount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /**
         * Where every budget on this household already stands, as a reader of the log would say it.
         *
         * <p>The committed half is deliberately absent: no bill has been presented at the moment the
         * seed runs — every cursor starts now — so what a category has cost so far is exactly what
         * the seeded spends put in it, and quoting a committed figure of nothing on every row would
         * be four extra numbers saying nothing. What the bills add arrives on the first nightly run
         * and is on the screen from then on.
         */
        String whereTheBudgetsStand() {
            return categories.stream()
                    .map(category -> {
                        BigDecimal spent = spentUnder(category.name());
                        if (!category.isBudgeted()) {
                            return category.name() + " unbudgeted, spent "
                                    + AmountOfMoney.asMoney(spent) + ", bills ["
                                    + String.join(", ", category.bills()) + "]";
                        }
                        BigDecimal allows = new BigDecimal(category.budget());
                        return category.name() + " allows " + AmountOfMoney.asMoney(allows) + " "
                                + category.rollover() + ", spent " + AmountOfMoney.asMoney(spent)
                                + ", left " + AmountOfMoney.asMoney(allows.subtract(spent))
                                + ", bills [" + String.join(", ", category.bills()) + "]";
                    })
                    .reduce((one, another) -> one + "; " + another)
                    .orElse("nothing");
        }

        /** The bills as a reader of the log would say them, so a missing one is visible at a glance. */
        String inWords() {
            return bills.stream()
                    .map(bill -> bill.name() + " " + AmountOfMoney.asMoney(bill.amount())
                            + " on day " + bill.dayOfMonth())
                    .reduce((one, another) -> one + ", " + another)
                    .orElse("nothing");
        }
    }

    /**
     * One word this household files money under: what it is called, which of its standing bills
     * belong to it, and the limit on it if there is one.
     *
     * <p>The bills are named rather than pointed at, because the declarations above are read by a
     * person and "Energy" says which bill it means where an identifier nobody has seen yet does not.
     * The names are resolved to identifiers once, in {@link DemoData#seed}, against the bills this
     * same household declared a few lines earlier.
     *
     * <p>A null {@code budget} is a category with no figure on it, which is a real and supported
     * state rather than a missing one: it is watched and not policed, nothing is ever measured
     * against it, and no alert can be raised about it.
     */
    private record ACategoryOnTheHousehold(String name, List<String> bills, String budget,
                                           RolloverRule rollover) {

        boolean isBudgeted() {
            return budget != null;
        }
    }

    /**
     * One spend in the seeded weeks: what it was called and how it was split.
     *
     * <p><strong>Its amount is the sum of its parts rather than a figure beside them.</strong> The
     * invariant this module holds every customer to is that a split adds up exactly to what was
     * spent, and a seed that stated both could state them differently — which would be a seed
     * demonstrating a refusal instead of a household. Stated once, it cannot.
     */
    private record ASpendInTheSeededWeeks(String name, List<APartOfASpend> parts) {

        BigDecimal amount() {
            return parts.stream().map(part -> new BigDecimal(part.amount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    /**
     * One part of a split: the category it is filed under, by name, or null for the euros nobody has
     * decided about yet.
     */
    private record APartOfASpend(String category, String amount) {
    }
}
