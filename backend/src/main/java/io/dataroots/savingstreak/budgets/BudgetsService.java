package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.accounts.ADeclaredBill;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.BillState;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.budgets.CategorisedBillRefused.Kind.NO_SUCH_BILL;
import static io.dataroots.savingstreak.budgets.CategorisedBillRefused.Kind.THE_BILL_IS_ENDED;
import static io.dataroots.savingstreak.budgets.SpendingCategoryRefused.Kind.AGAINST_THE_RULES;
import static io.dataroots.savingstreak.budgets.SpendingCategoryRefused.Kind.ALREADY_A_CATEGORY_HERE;
import static io.dataroots.savingstreak.budgets.SpendingCategoryRefused.Kind.NO_SUCH_CATEGORY;
import static io.dataroots.savingstreak.budgets.SpendingCategoryRefused.Kind.THE_CATEGORY_IS_ENDED;

/**
 * The declarations this module keeps: what a customer says their money goes on, which of their
 * standing bills sit in each of those words, and — as the feature grows — what each of them is
 * allowed to cost in a month.
 *
 * <p><strong>One of three faces, mirroring {@code deposits}.</strong> This one owns the
 * declarations; recording a spend and correcting its split is a face of its own, and so are the
 * reads that answer "what have I actually got?" — this month, a month already gone, the history and
 * the weeks ahead. Each is the right answer to its own question, and the third exists because
 * neither of the other two answers it. They are separate classes rather than three sections of one,
 * for the reason {@code DepositsService}, {@code WithdrawalsService} and
 * {@code MoneyMovementsService} are three: a class that both declares and derives is a class two
 * different readers open for two different reasons.
 *
 * <p><strong>The module's dependency runs one way.</strong> It reads Accounts' public read models —
 * a bill, to know whether it is on the account and whether it is still standing — and, once there
 * are spends, takes money through {@code AccountsService.withdrawFrom}, exactly as
 * {@code deposits} does. Accounts gains nothing and learns nothing, which is what keeps a bill's
 * category a row in here rather than a column on {@code RecurringBill}.
 *
 * <p><strong>It never asks whether a current account is real.</strong> The controller vouches for
 * the identifier in the path first, in the words {@code AccountsService.noSuchCurrentAccount} owns,
 * the same order {@code GoalsService} and {@code AutomationService} are called in — which is why
 * {@link SpendingCategoryRefused} has no kind for an account that is not there.
 */
@Service
public class BudgetsService {

    private static final Logger log = LoggerFactory.getLogger(BudgetsService.class);

    /**
     * How many spending categories one current account may have standing at once.
     *
     * <p>Twenty, mirroring the cap on recurring bills and for the two reasons that one exists:
     * every fold in this module walks one account's categories — a month's arithmetic, the carry
     * chain, the weekly spread, the nightly alert sweep — and an unbounded list is a denial of
     * service on all of them; and it is what a page can promise to draw with a bar each.
     *
     * <p>Twenty is also enough for the household this application is about. A customer who genuinely
     * needs a twenty-first has categories finer than they can police, which is the opposite of what
     * budgeting is for — and the honest answer, which the refusal gives them, is to end one they no
     * longer use.
     *
     * <p>Ended categories do not count against it, so ending one makes room for another.
     */
    private static final int HOW_MANY_CATEGORIES_ONE_ACCOUNT_MAY_CARRY = 20;

    private final SpendingCategoryRepository categories;

    /**
     * Which bill is in which category: this module's own rows, keyed on the bill's identifier.
     *
     * <p>Here rather than on {@code RecurringBill}, because a category reference on the bill would
     * make {@code accounts} depend on {@code budgets} and close a cycle. {@link CategorisedBill}
     * argues it at length.
     */
    private final CategorisedBillRepository categorisedBills;

    /**
     * What each category is allowed to cost, and what it used to be allowed to cost.
     *
     * <p>Several rows per category over its life and one of them standing, because a budget is
     * superseded rather than mutated: the figure that stood in April has to stay readable in June,
     * or "changing it leaves last month alone" is a promise nothing keeps. {@link MonthlyBudget}
     * argues it at length.
     */
    private final MonthlyBudgetRepository budgets;

    /**
     * Accounts' public read models, for the one question this module cannot answer about a bill:
     * whether it is on the account at all, and whether it is still standing.
     *
     * <p>The dependency runs this way and only this way. Nothing here writes to a bill, nothing here
     * asks Accounts to keep anything about a category, and Accounts does not know this class exists
     * — which is exactly what makes a bill's category a row in this module rather than a column on
     * the bill.
     */
    private final AccountsService accounts;

    /**
     * The application's clock, for the two moments a category has — when it was declared and when it
     * was ended — and for the one question every budget asks: which month it is. Nothing here calls
     * {@code Instant.now()} — the development clock can be wound years forward, and a declaration
     * stamped from the wall clock would take effect in a month this module never reads.
     */
    private final Clock clock;

    BudgetsService(SpendingCategoryRepository categories,
                   CategorisedBillRepository categorisedBills, MonthlyBudgetRepository budgets,
                   AccountsService accounts, Clock clock) {
        this.categories = categories;
        this.categorisedBills = categorisedBills;
        this.budgets = budgets;
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * The categories standing on this current account, in the order the customer named them.
     *
     * <p>Standing only. An ended category is a record rather than a word still in use and has no
     * place in a list of what money is being filed under; it is still readable through
     * {@link #endedCategoriesOn}, which is what keeps "what did I used to spend on" answerable.
     */
    @Transactional(readOnly = true)
    public List<ADeclaredCategory> categoriesOn(long currentAccountId) {
        List<ADeclaredCategory> standing = asDeclared(categories
                .findByCurrentAccountIdAndStateInOrderByIdAsc(currentAccountId,
                        CategoryState.theOnesStillStanding()));
        log.debug("spending categories read currentAccountId={} standing={}", currentAccountId,
                standing.size());
        return standing;
    }

    /** What was ended, oldest first, so that a category closed rather than deleted stays readable. */
    @Transactional(readOnly = true)
    public List<ADeclaredCategory> endedCategoriesOn(long currentAccountId) {
        List<ADeclaredCategory> ended = asDeclared(categories
                .findByCurrentAccountIdAndStateInOrderByIdAsc(currentAccountId,
                        List.of(CategoryState.ENDED)));
        log.debug("ended spending categories read currentAccountId={} ended={}", currentAccountId,
                ended.size());
        return ended;
    }

    /**
     * Names one more thing this account's money goes on, and answers with the category that now
     * stands.
     *
     * <p>The objections are asked in the order somebody fixes them in: whether it is a name at all,
     * whether the account is already using that word, and only then whether there is room for
     * another. Last, so that a twenty-first category is refused for being a twenty-first category
     * rather than for a typo it also had.
     *
     * <p>Nothing moves. A category is a word for money that leaves, not money leaving, and the
     * balance is exactly where the customer left it afterwards.
     *
     * @throws SpendingCategoryRefused if that is not a category this application will keep
     */
    @Transactional
    public ADeclaredCategory declareACategory(long currentAccountId, String name) {
        log.debug("spending category asked for currentAccountId={} name={}", currentAccountId, name);
        String itsName = judgedAsAName(currentAccountId, null, name);
        refuseUnlessThatNameIsFree(currentAccountId, null, itsName);
        refuseUnlessThereIsRoomForAnotherCategory(currentAccountId);

        SpendingCategory category = categories.save(
                new SpendingCategory(currentAccountId, itsName, clock.instant()));
        log.info("spending category declared currentAccountId={} categoryId={} name={} state={} "
                        + "declaredAt={} standing={}",
                currentAccountId, category.getId(), category.getName(), category.getState(),
                category.getDeclaredAt(), howManyAreStanding(currentAccountId));
        return ADeclaredCategory.of(category);
    }

    /**
     * Says a standing category differently, and answers with the category as it now reads.
     *
     * <p>A rename and nothing else, because a category <em>is</em> a name: there is no other field
     * on it to change. What it is allowed to cost is a budget, declared and superseded on its own
     * path, and what has been spent under it is a record nobody edits.
     *
     * <p>The identifier does not change and neither does the moment it was declared, which is the
     * whole difference between renaming a category and ending one to declare another: everything
     * already filed under it stays filed under it, so a customer who fixes a spelling in June still
     * reads March correctly. That is exactly what makes a rename safe to offer and a resurrection
     * not.
     *
     * <p>A category that has been ended is refused rather than renamed. It is kept so that the
     * months it was live for stay explained, and a record that could be rewritten afterwards is not
     * a record.
     *
     * <p>Nothing is written until every objection has been heard, so a refused rename leaves the
     * category exactly as the customer last left it.
     *
     * @throws SpendingCategoryRefused if there is no such category on that account, if it has been
     *                                 ended, or if that is not a name this module will keep
     */
    @Transactional
    public ADeclaredCategory renameCategory(long currentAccountId, long categoryId, String name) {
        log.debug("spending category rename asked for currentAccountId={} categoryId={} name={}",
                currentAccountId, categoryId, name);
        SpendingCategory category = theCategoryOn(currentAccountId, categoryId);
        refuseUnlessTheCategoryHasNotBeenEnded(currentAccountId, category, "renamed");
        String itsName = judgedAsAName(currentAccountId, categoryId, name);
        refuseUnlessThatNameIsFree(currentAccountId, categoryId, itsName);

        String wasCalled = category.getName();
        category.isNowCalled(itsName);
        categories.save(category);
        log.info("spending category renamed currentAccountId={} categoryId={} name={} "
                        + "wasCalled={} declaredAt={}",
                currentAccountId, categoryId, category.getName(), wasCalled,
                category.getDeclaredAt());
        return ADeclaredCategory.of(category);
    }

    /**
     * Ends a category for good, and answers with the category as it now reads rather than with
     * nothing.
     *
     * <p>One-way, and the whole of what that means is here: it leaves {@link #categoriesOn},
     * appears in {@link #endedCategoriesOn}, and every later rename or ending of it is refused.
     * Nothing filed under it is unpicked — the money left the account and the record of it is what
     * explains a balance — and ending it both makes room under the cap and frees its name for a
     * fresh declaration.
     *
     * @throws SpendingCategoryRefused if there is no such category on that account, or it has
     *                                 already ended
     */
    @Transactional
    public ADeclaredCategory endCategory(long currentAccountId, long categoryId) {
        log.debug("spending category end asked for currentAccountId={} categoryId={}",
                currentAccountId, categoryId);
        SpendingCategory category = theCategoryOn(currentAccountId, categoryId);
        refuseUnlessTheCategoryHasNotBeenEnded(currentAccountId, category, "ended");
        Instant now = clock.instant();
        category.end(now);
        categories.save(category);
        stopTheBudgetBecauseTheCategoryEnded(currentAccountId, category, now);
        log.info("spending category ended currentAccountId={} categoryId={} name={} stoodSince={} "
                        + "endedAt={} state={} standing={}",
                currentAccountId, categoryId, category.getName(), category.getDeclaredAt(),
                category.getEndedAt(), category.getState(), howManyAreStanding(currentAccountId));
        return ADeclaredCategory.of(category);
    }

    /**
     * Says what a category is allowed to cost each month, and answers with the figure that now
     * stands.
     *
     * <p><strong>Superseded, never mutated.</strong> A category that already carries a figure keeps
     * the row it carried: that row's run ends at the month before this one and a new row opens
     * effective this month. So a customer who raises their grocery budget in June leaves April and
     * May quoting what stood then, which is what makes their history what happened rather than what
     * they currently intend — and it costs one mechanism, because a change of amount and a change of
     * rollover rule are the same supersession. A customer who turns Groceries into an envelope in
     * June has said something about June; April and May go on folding under the rule they were
     * actually kept under, which is user story 27 and is free.
     *
     * <p>One press covers both. There is no separate "change it" path, because there is nothing a
     * change could be that declaring the figure again is not: what the customer is saying is "from
     * now on, this much", and whether they had said anything before is this method's business rather
     * than theirs. The PUT it is served under says the same thing.
     *
     * <p>The objections are asked in the order somebody fixes them in: the category in the path —
     * whether it is on this account at all, and whether it is still a word in use — and then the
     * figure in the body. A budget on an ended category is refused rather than kept, for the reason
     * a rename of one is: the months it was live for stay explained, and a limit put on a word
     * nobody is spending under any more is a limit nothing will ever be measured against.
     *
     * <p><strong>Nothing moves.</strong> A budget is a plan for money that will leave, not money
     * leaving, and the balance is exactly where the customer left it afterwards. Nothing refuses a
     * spend for being over a budget either: the balance is the only hard constraint in this
     * application, and a budget a customer can break is the whole reason the read below is worth
     * reading.
     *
     * @param rollover what becomes of the difference when a month it governs ends, or null where the
     *                 customer said nothing — which is not a refusal but the default, because
     *                 somebody who has never thought about rollover has a rule all the same
     * @throws SpendingCategoryRefused if there is no such category on that account, or it has ended
     * @throws BudgetRefused           if that figure is not one worth holding a category to
     */
    @Transactional
    public ABudgetOnACategory declareABudget(long currentAccountId, long categoryId,
                                             BigDecimal amount, RolloverRule rollover) {
        log.debug("budget asked for currentAccountId={} categoryId={} amount={} rollover={}",
                currentAccountId, categoryId, amount, rollover);
        SpendingCategory category = theCategoryOn(currentAccountId, categoryId);
        refuseUnlessTheCategoryHasNotBeenEnded(currentAccountId, category, "budgeted");
        BigDecimal figure = judgedAsAMonthlyLimit(currentAccountId, categoryId, amount);
        // Nothing said about rollover is the default rather than a refusal: a customer who has
        // never thought about it has a rule all the same, and it is the one that surprises nobody.
        RolloverRule rule = RolloverRule.orTheDefault(rollover);

        Instant now = clock.instant();
        YearMonth thisMonth = TheMonthAMomentFallsIn.of(now);
        Optional<MonthlyBudget> standing = theBudgetStandingOn(currentAccountId, categoryId);
        standing.ifPresent(replaced -> {
            replaced.supersededFrom(thisMonth, now);
            // Flushed rather than merely saved, and the flush is load-bearing. Hibernate orders
            // every insert in a transaction ahead of every update, so without it the new standing
            // row would reach the database while the old one was still standing there — and the
            // partial unique index that makes one figure in force per category a real rule would
            // refuse the customer their own change of mind. The guarantee is doing exactly what it
            // is for; this is the one write that has to say in which order it wants to be believed.
            budgets.saveAndFlush(replaced);
            // One line per figure replaced, naming both amounts and both ends of the run it kept,
            // because "what was my grocery budget in April" is a question this is the only record
            // of. A reviewer tracing a month that quotes an unexpected figure reads this line.
            log.info("budget superseded currentAccountId={} categoryId={} category={} budgetId={} "
                            + "was={} nowIs={} ruleWas={} ruleNowIs={} effectiveFrom={} "
                            + "stoodThrough={} state={}",
                    currentAccountId, categoryId, category.getName(), replaced.getId(),
                    AmountOfMoney.asMoney(replaced.getAmount()), AmountOfMoney.asMoney(figure),
                    replaced.getRollover(), rule, replaced.effectiveFrom(),
                    replaced.stoodThrough(), replaced.getState());
        });

        MonthlyBudget declared = budgets.save(
                new MonthlyBudget(currentAccountId, categoryId, figure, rule, thisMonth, now));
        log.info("budget declared currentAccountId={} categoryId={} category={} budgetId={} "
                        + "amount={} rollover={} effectiveFrom={} supersededBudgetId={} "
                        + "declaredAt={}",
                currentAccountId, categoryId, category.getName(), declared.getId(),
                AmountOfMoney.asMoney(declared.getAmount()), declared.getRollover(),
                declared.effectiveFrom(), standing.map(MonthlyBudget::getId).orElse(null),
                declared.getDeclaredAt());
        return ABudgetOnACategory.of(declared, category);
    }

    /**
     * Stops budgeting a category without ending the category, and answers with the figure as it now
     * reads rather than with nothing.
     *
     * <p><strong>The category stands and its spending is still counted.</strong> That is the whole
     * of what this is for: a customer who has stopped policing something they still want to watch
     * has, until now, had only two options that both lose something — leave a figure they no longer
     * believe in, or end the word and lose the record. After this the category reports what it cost
     * and a budget that is <em>absent</em>, which is a state rather than a nought.
     *
     * <p><strong>From this month, this month included</strong>, which is deliberately not what
     * ending the category does. Stopping is a customer disowning the limit, so the month they say it
     * in should not go on measuring them against it; ending a category is a decision about the word,
     * and the month it happens in keeps the figure because real spending in it was made against
     * that figure. {@link MonthlyBudget#stoppedBecauseTheCategoryEnded} argues the other side.
     *
     * <p>Every month already gone is untouched, and the row is kept rather than deleted, for the
     * reason every other ending in this application keeps its record: "what was I allowing myself in
     * March" has to stay answerable after the customer has stopped allowing themselves anything.
     *
     * <p>Asked of a category that carries no figure, it refuses rather than shrugging. A customer
     * pressing the button twice has a right to know the second press did nothing, and an application
     * that answers yes to both is one nobody can use to find out what state anything is in.
     *
     * @throws SpendingCategoryRefused if there is no such category on that account
     * @throws BudgetRefused           if that category has no figure in force to stop
     */
    @Transactional
    public ABudgetOnACategory stopBudgeting(long currentAccountId, long categoryId) {
        log.debug("budget stop asked for currentAccountId={} categoryId={}", currentAccountId,
                categoryId);
        SpendingCategory category = theCategoryOn(currentAccountId, categoryId);
        MonthlyBudget standing = theBudgetStandingOn(currentAccountId, categoryId)
                .orElseThrow(() -> refusingTheBudget(currentAccountId, categoryId,
                        BudgetRefused.Kind.NO_SUCH_BUDGET,
                        "You are not budgeting \"" + category.getName() + "\", so there is nothing "
                                + "to stop. Say what it is allowed to cost if you want a figure on "
                                + "it."));

        Instant now = clock.instant();
        standing.stoppedFrom(TheMonthAMomentFallsIn.of(now), now);
        budgets.save(standing);
        log.info("budget stopped currentAccountId={} categoryId={} category={} budgetId={} "
                        + "amount={} effectiveFrom={} stoodThrough={} state={} stoppedAt={} "
                        + "categoryState={}",
                currentAccountId, categoryId, category.getName(), standing.getId(),
                AmountOfMoney.asMoney(standing.getAmount()), standing.effectiveFrom(),
                standing.stoodThrough(), standing.getState(), standing.getStoodDownAt(),
                category.getState());
        return ABudgetOnACategory.of(standing, category);
    }

    /**
     * What each of this account's categories was allowed to cost, and under what rule, in every
     * month of a stretch — by category identifier, and then by month.
     *
     * <p><strong>A stretch rather than a month, and that is the whole point of the signature.</strong>
     * The carry is a fold over as many as twenty-four months, so a read that answered about one
     * month would be asked twenty-four times to draw one page — and each of those asks reads the
     * account's whole history of figures, because which row governed a month is arithmetic over two
     * date columns rather than something a query can select. Twenty-four identical queries per page
     * is exactly the shape this method exists to make impossible: the rows are read once, here, and
     * every month in the window is answered out of them.
     *
     * <p>{@link MonthlyBudget#stoodIn} stays the single predicate. Which figure governed which month
     * is one rule and is written once; this walks the months against it rather than growing a second
     * opinion about effective dates that could drift from the first.
     *
     * <p>A category missing from the answer carried no figure in any month of the stretch, and a
     * month missing from a category's own map is a month it carried none in — which is the honest
     * shape and is what both the read and the fold want. "Absent" is a state and not a nought: not
     * budgeting a category is a customer choosing to watch it rather than police it, and a month
     * nobody was held to anything in hands nothing on.
     *
     * <p>Package-private, and it stays that way. This class is the declarations face — what a
     * customer says their money goes on and what they are holding themselves to — and the reads that
     * answer "how is this month going" are a face of their own. What leaves this module about a
     * month is {@link WhatAnAccountSpentInAMonth}, assembled by {@link SpendingService}, and nothing
     * outside has any business walking budget rows.
     *
     * @param from the first month to answer about, inclusive — which the caller takes from
     *             {@link WhatCarriesIntoAMonth#theEarliestMonthItWalksFrom}, so that the window read
     *             and the window folded are the same window
     * @param to   the last month to answer about, inclusive
     */
    @Transactional(readOnly = true)
    Map<Long, Map<YearMonth, ABudgetThatStood>> whatEachCategoryWasAllowedOver(long currentAccountId,
                                                                               YearMonth from,
                                                                               YearMonth to) {
        Map<Long, Map<YearMonth, ABudgetThatStood>> allowed = new HashMap<>();
        List<MonthlyBudget> everyFigureEverNamed = budgets
                .findByCurrentAccountIdOrderByEffectiveFromAscIdAsc(currentAccountId);
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            for (MonthlyBudget budget : everyFigureEverNamed) {
                if (budget.stoodIn(month)) {
                    // Last one wins, which the ordering makes the latest-starting row that covers
                    // the month. The arithmetic already makes two rows covering one month
                    // impossible; this is what keeps a hand-edited database from turning that into
                    // an answer that changes between two reads.
                    allowed.computeIfAbsent(budget.getCategoryId(), category -> new HashMap<>())
                            .put(month, ABudgetThatStood.of(budget));
                }
            }
        }
        log.debug("budgets that stood read currentAccountId={} from={} to={} figuresEverNamed={} "
                        + "budgetedCategories={}",
                currentAccountId, from, to, everyFigureEverNamed.size(), allowed.size());
        return allowed;
    }

    /**
     * Every category this account has ever had, standing and ended alike, in declaration order.
     *
     * <p>For the month read, which has to be able to draw a category that was ended halfway through
     * the month it is reporting on: the spending under it happened, the figure it was measured
     * against stood, and a list of the standing ones would answer that month with a hole in it.
     * Which of them a given month actually shows is the read's own decision, argued out in
     * {@link WhatAnAccountSpentInAMonth}.
     *
     * <p>Package-private, for the reason {@link #whatTheCategoriesAreCalledAcross} is: naming a
     * category by identifier is a question about a declaration, and the two callers that do it are
     * inside this module.
     */
    @Transactional(readOnly = true)
    List<ADeclaredCategory> everyCategoryOn(long currentAccountId) {
        List<ADeclaredCategory> every = asDeclared(
                categories.findByCurrentAccountIdOrderByIdAsc(currentAccountId));
        log.debug("every spending category read currentAccountId={} categories={}", currentAccountId,
                every.size());
        return every;
    }

    /**
     * The standing category a part of a spend names, or the refusal that it is not one.
     *
     * <p>Asked of this class rather than answered wherever a category is named, so that "there is no
     * category 7 on current account 3" and "\"Groceries\" was ended" are one sentence each in this
     * application. The same bargain {@code AccountsService.noSuchCurrentAccount} strikes for an
     * account that is not there: the module that owns a concept owns the words for its absence, and
     * a second copy of them somewhere else is a copy free to drift.
     *
     * <p>Package-private, and it stays that way. This class is the declarations face — what a
     * customer says their money goes on — and answering "may money be filed under this?" is a
     * question about a declaration. What it is <em>not</em> is a public read: nothing outside this
     * module has any business naming a category by identifier, and the two callers that do are the
     * ones recording a spend and correcting its split.
     *
     * <p>It answers with the category rather than merely saying yes, because both callers need the
     * name: a split read back has to print what each part was filed under, and a category that has
     * been ended since is a name only this module can still find.
     *
     * @throws SpendingCategoryRefused if there is no such category on that account, or it has ended
     */
    @Transactional(readOnly = true)
    ADeclaredCategory aCategoryMoneyCanBeFiledUnder(long currentAccountId, long categoryId) {
        SpendingCategory category = theCategoryOn(currentAccountId, categoryId);
        refuseUnlessTheCategoryHasNotBeenEnded(currentAccountId, category, "filed under");
        log.debug("spending category is one money can be filed under currentAccountId={} "
                + "categoryId={} name={}", currentAccountId, categoryId, category.getName());
        return ADeclaredCategory.of(category);
    }

    /**
     * What every category on any of these accounts is called, by identifier — the ended ones
     * included.
     *
     * <p>For whoever is printing a split rather than for anybody choosing what to file under.
     * Ending a category leaves every euro ever filed under it where it is, so a spend read back long
     * afterwards still has to say what it was for, and the standing list alone would answer with
     * nothing.
     *
     * <p>One read for the whole list rather than a lookup per part: a page of spends with a split
     * each would otherwise ask this module the same question dozens of times, and an account carries
     * at most twenty standing categories and a handful of ended ones.
     *
     * <p><strong>Several accounts rather than one</strong>, because the two callers ask the same
     * question of different holdings: the recent-spends page asks it of one account and the
     * money-movement ledger asks it of every everyday account a customer holds. One method rather
     * than one each, so that there is only ever one answer to what an ended category is called.
     *
     * <p>A category identifier is unique across the table, so one map serves every account in the
     * list and nothing has to say which account a name came from. That is safe here precisely
     * because it is a printing lookup: whoever is choosing a category to file money under goes
     * through {@link #aCategoryMoneyCanBeFiledUnder}, which is scoped to one account and is the only
     * thing that ever grants permission.
     *
     * <p>No accounts at all is an empty answer rather than a query, matching every other read in
     * this application that is handed a customer's holding: {@code in ()} is not a thing to ask a
     * database.
     */
    @Transactional(readOnly = true)
    Map<Long, String> whatTheCategoriesAreCalledAcross(Collection<Long> currentAccountIds) {
        if (currentAccountIds.isEmpty()) {
            log.debug("spending category names asked for across no current accounts, so there are "
                    + "none");
            return Map.of();
        }
        Map<Long, String> called = categories.findByCurrentAccountIdInOrderByIdAsc(currentAccountIds)
                .stream()
                .collect(Collectors.toMap(SpendingCategory::getId, SpendingCategory::getName));
        log.debug("spending category names read currentAccounts={} categories={}",
                currentAccountIds.size(), called.size());
        return called;
    }

    /**
     * Where each of this account's bills is filed, for the bills that are filed anywhere at all.
     *
     * <p>One answer for the whole account rather than one per bill, because both pages that ask are
     * drawing a list: the account's own screen puts a label on each bill it already has, and the
     * budget screen groups the bills under the categories it already has. A bill in no category is
     * simply missing from this list, which is the honest shape — there is no row — and is what a
     * page draws as "not in a category".
     *
     * <p>Standing categories and ended ones alike. A category that has been ended keeps the bills
     * that pointed at it, and every row says which it is, so that a customer reading a bill labelled
     * with a word they have stopped using is told that is what happened rather than finding the
     * label silently gone.
     */
    @Transactional(readOnly = true)
    public List<TheCategoryABillIsIn> theCategoryEachBillIsIn(long currentAccountId) {
        List<CategorisedBill> filed = categorisedBills
                .findByCurrentAccountIdOrderByBillIdAsc(currentAccountId);
        List<TheCategoryABillIsIn> where = withTheirCategories(currentAccountId, filed);
        log.debug("bill categories read currentAccountId={} billsInACategory={}", currentAccountId,
                where.size());
        return where;
    }

    /**
     * Puts one standing bill in one standing category, moves it from wherever it was, and answers
     * with where it now sits.
     *
     * <p><strong>One bill is in one category and a bill is never split.</strong> A bill is one
     * declared payment to one provider for an amount the declaration fixes, so every occurrence of
     * it counts wholly in one place; a customer who wants their rent divided between Housing and
     * Utilities declares two bills, which is both simpler and truer to what a bill is. That is why
     * putting a bill somewhere moves the row rather than adding one.
     *
     * <p><strong>It re-labels the bill's whole history, and that is the point.</strong> There is one
     * row and no dated versions of it, so a customer who discovers in June that the rent has been
     * under Groceries since March fixes March by fixing the label — which is what somebody correcting
     * a mistake means by correcting it.
     *
     * <p>The objections are asked in the order the request makes them: the bill in the path, then
     * the category in the body. An ended bill is refused rather than re-labelled, for the reason its
     * name and its amount are: the months it was taken for stay explained, and a record that could
     * be rewritten afterwards is not a record. An ended category is refused as the destination for
     * the same reason it is refused a rename — it is a record of months already gone rather than a
     * word still in use — while a bill already sitting in one that was ended afterwards is left
     * exactly where it is.
     *
     * <p>Nothing moves. A category is a word for money that leaves, not money leaving, and the
     * balance is exactly where the customer left it afterwards.
     *
     * @throws CategorisedBillRefused    if there is no such bill on that account, or it has ended
     * @throws SpendingCategoryRefused   if there is no such category on that account, or it has
     *                                   ended
     */
    @Transactional
    public TheCategoryABillIsIn putBillInACategory(long currentAccountId, long billId,
                                                   long categoryId) {
        log.debug("bill category asked for currentAccountId={} billId={} categoryId={}",
                currentAccountId, billId, categoryId);
        ADeclaredBill bill = theBillOn(currentAccountId, billId);
        refuseUnlessTheBillHasNotBeenEnded(currentAccountId, bill, "put in a category");
        SpendingCategory category = theCategoryOn(currentAccountId, categoryId);
        refuseUnlessTheCategoryHasNotBeenEnded(currentAccountId, category, "given a bill");

        Optional<CategorisedBill> alreadyFiled = categorisedBills
                .findByCurrentAccountIdAndBillId(currentAccountId, billId);
        CategorisedBill filed = alreadyFiled.orElseGet(() -> new CategorisedBill(currentAccountId,
                billId, categoryId, clock.instant()));
        // Where it was, read before the row is moved, so that the one line below says what changed
        // rather than only what is now true. A reviewer asking "who moved the rent out of Housing"
        // has the answer on the line that moved it.
        Long wasIn = alreadyFiled.map(CategorisedBill::getCategoryId).orElse(null);
        boolean moved = filed.isNowIn(categoryId, clock.instant());
        categorisedBills.save(filed);

        log.info("bill put in a category currentAccountId={} billId={} bill={} categoryId={} "
                        + "category={} wasInCategoryId={} moved={} filedAt={}",
                currentAccountId, billId, bill.name(), categoryId, category.getName(), wasIn,
                alreadyFiled.isEmpty() || moved, filed.getFiledAt());
        return TheCategoryABillIsIn.of(filed, category);
    }

    /**
     * Takes one standing bill out of every category, leaving it filed nowhere.
     *
     * <p>The one deletion in this module, and it is the honest shape: what is being unmade is an
     * opinion about a payment rather than the record of the payment, which is the bill's own
     * occurrences and is untouched by any of this. A row kept with an empty category would be a
     * second way of saying "in no category" beside the absence of a row, and every read here would
     * then have to mean the same thing twice.
     *
     * <p>Asked of a bill that is in no category, it does nothing and says so, which is what a
     * customer pressing the same button twice means. An ended bill is refused, exactly as it is for
     * putting one in.
     *
     * @throws CategorisedBillRefused if there is no such bill on that account, or it has ended
     */
    @Transactional
    public void takeBillOutOfEveryCategory(long currentAccountId, long billId) {
        log.debug("bill taken out of every category asked for currentAccountId={} billId={}",
                currentAccountId, billId);
        ADeclaredBill bill = theBillOn(currentAccountId, billId);
        refuseUnlessTheBillHasNotBeenEnded(currentAccountId, bill, "taken out of its category");

        Optional<CategorisedBill> filed = categorisedBills
                .findByCurrentAccountIdAndBillId(currentAccountId, billId);
        filed.ifPresent(categorisedBills::delete);
        log.info("bill taken out of every category currentAccountId={} billId={} bill={} "
                        + "wasInCategoryId={} removed={}",
                currentAccountId, billId, bill.name(),
                filed.map(CategorisedBill::getCategoryId).orElse(null), filed.isPresent());
    }

    /**
     * The rows with the categories they point at, read in one question rather than one per bill.
     *
     * <p>A row whose category is not there cannot happen — a category is ended and never deleted,
     * and both are written in the one transaction that made them agree — and if one ever did, a bill
     * that draws without its label is a better answer than a page that will not draw at all. It is
     * warned about rather than swallowed, because it would mean this module's own rows disagree with
     * each other and nothing else in the application would ever say so.
     */
    private List<TheCategoryABillIsIn> withTheirCategories(long currentAccountId,
                                                           List<CategorisedBill> filed) {
        if (filed.isEmpty()) {
            return List.of();
        }
        Map<Long, SpendingCategory> byId = categories
                .findAllById(filed.stream().map(CategorisedBill::getCategoryId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(SpendingCategory::getId, Function.identity()));
        return filed.stream()
                .map(bill -> {
                    SpendingCategory category = byId.get(bill.getCategoryId());
                    if (category == null) {
                        log.warn("bill filed under a category that is not there currentAccountId={} "
                                        + "billId={} categoryId={}", currentAccountId,
                                bill.getBillId(), bill.getCategoryId());
                        return null;
                    }
                    return TheCategoryABillIsIn.of(bill, category);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * That bill on that account, or a refusal that it is not there.
     *
     * <p>Read from Accounts rather than from anything here, because a bill is Accounts' record and
     * this module holds nothing but a label pointing at it. Scoped by the account in the path, so a
     * bill identifier somebody guessed answers as a bill that is not there rather than with somebody
     * else's rent — the same answer a category gives, for the same reason.
     */
    private ADeclaredBill theBillOn(long currentAccountId, long billId) {
        return accounts.billOn(currentAccountId, billId)
                .orElseThrow(() -> refusingTheBillsCategory(currentAccountId, billId, NO_SUCH_BILL,
                        "There is no bill " + billId + " on current account " + currentAccountId
                                + "."));
    }

    /**
     * That the bill is still an instruction rather than only a record.
     *
     * <p>The one state a bill can be refused for here, because there are only two of them and ending
     * is one-way. Its category stays readable — that is the whole reason ending a bill is a closing
     * rather than a deletion — and every change to it is refused, so that the months it was taken
     * for keep reading the way they read when they happened.
     */
    private void refuseUnlessTheBillHasNotBeenEnded(long currentAccountId, ADeclaredBill bill,
                                                    String whatWasAsked) {
        if (bill.state() == BillState.ENDED) {
            throw refusingTheBillsCategory(currentAccountId, bill.billId(), THE_BILL_IS_ENDED,
                    "\"" + bill.name() + "\" was ended and cannot be " + whatWasAsked
                            + ". The category it was in is still readable. Declare the bill again "
                            + "if you are paying it once more.");
        }
    }

    /**
     * Every refusal about a bill's category says why in the log as well as to whoever asked, for the
     * reason every refusal about a category does: only one of the two is kept, and the reason
     * reaches the person at the keyboard and nowhere else.
     */
    private CategorisedBillRefused refusingTheBillsCategory(long currentAccountId, long billId,
                                                            CategorisedBillRefused.Kind kind,
                                                            String reason) {
        log.warn("bill category rejected currentAccountId={} billId={} kind={} reason={}",
                currentAccountId, billId, kind, reason);
        return new CategorisedBillRefused(kind, reason);
    }

    /**
     * That category on that account, or a refusal that it is not there.
     *
     * <p>Scoped by the account in the path, so a category identifier somebody guessed answers as a
     * category that is not there rather than with somebody else's groceries. It is the whole of what
     * this application can say about whose a category is: there is no authentication to ask, and it
     * is the same answer a bill and a saving rule give for the same reason.
     */
    private SpendingCategory theCategoryOn(long currentAccountId, long categoryId) {
        return categories.findByIdAndCurrentAccountId(categoryId, currentAccountId)
                .orElseThrow(() -> refusingTheCategory(currentAccountId, categoryId,
                        NO_SUCH_CATEGORY, "There is no category " + categoryId
                                + " on current account " + currentAccountId + "."));
    }

    /**
     * That the category is still a word in use rather than only a record.
     *
     * <p>The one state a category can be refused for, because there are only two of them and ending
     * is one-way: an ended category cannot be renamed and cannot be ended again, and the kind is
     * named after exactly that.
     */
    private void refuseUnlessTheCategoryHasNotBeenEnded(long currentAccountId,
                                                        SpendingCategory category,
                                                        String whatWasAsked) {
        if (category.isEnded()) {
            throw refusingTheCategory(currentAccountId, category.getId(), THE_CATEGORY_IS_ENDED,
                    "\"" + category.getName() + "\" was ended and cannot be " + whatWasAsked
                            + ". Declare it again if you are spending on it once more.");
        }
    }

    /**
     * That no other standing category on this account is already called that.
     *
     * <p>Two live categories of one name would be two answers to "what did the groceries cost" with
     * nothing to say which the customer meant, and every figure derived below would inherit the
     * ambiguity. Refused with the word named back, because a customer with twenty categories cannot
     * be expected to remember which of them they already have.
     *
     * <p>Standing only. A name whose category was ended is free again: the old one is a record of
     * months already gone, and declaring the word afresh makes a new category with its own
     * identifier and no claim on them.
     *
     * <p>The category being renamed is excused from its own name, so that a customer who retyped
     * what was already there is not told they are duplicating themselves. That is the one case where
     * the row found is not a rival.
     *
     * <p>This check is what produces the sentence. The guarantee against two of them being written
     * at the same moment is the partial unique index {@link BudgetsOnStartUp} creates, and the two
     * are deliberately different things.
     *
     * @param categoryId the category being renamed, or null while one is being declared
     */
    private void refuseUnlessThatNameIsFree(long currentAccountId, Long categoryId, String name) {
        Optional<SpendingCategory> alreadyHere = categories
                .findByCurrentAccountIdAndNameAndStateIn(currentAccountId, name,
                        CategoryState.theOnesStillStanding());
        log.debug("spending category name checked currentAccountId={} name={} alreadyStanding={}",
                currentAccountId, name, alreadyHere.map(SpendingCategory::getId).orElse(null));
        if (alreadyHere.isEmpty() || alreadyHere.get().getId().equals(categoryId)) {
            return;
        }
        throw refusingTheCategory(currentAccountId, categoryId, ALREADY_A_CATEGORY_HERE,
                "\"" + name + "\" is already one of the things this account's money goes on. Use "
                        + "that one, or give this a name of its own.");
    }

    /**
     * That the account has not already got as many categories standing as this application will
     * keep for one of them.
     *
     * <p>Per account rather than per customer, which is where it differs from the cap on saving
     * rules: what the limit protects is every fold that walks one account's categories, and a
     * household with two current accounts genuinely has two sets of things its money goes on. Ended
     * categories are not counted — nothing will be filed under them and no page draws them beside
     * the standing ones — so ending one is what makes room for another.
     */
    private void refuseUnlessThereIsRoomForAnotherCategory(long currentAccountId) {
        long standing = howManyAreStanding(currentAccountId);
        log.debug("spending categories standing on the account currentAccountId={} standing={} "
                + "limit={}", currentAccountId, standing, HOW_MANY_CATEGORIES_ONE_ACCOUNT_MAY_CARRY);
        if (standing >= HOW_MANY_CATEGORIES_ONE_ACCOUNT_MAY_CARRY) {
            throw refusingTheCategory(currentAccountId, null, AGAINST_THE_RULES,
                    "This account already has " + standing + " categories, and "
                            + HOW_MANY_CATEGORIES_ONE_ACCOUNT_MAY_CARRY + " is the most one account "
                            + "can carry at once. End one you no longer use before naming another.");
        }
    }

    /**
     * Whether what arrived is a name this application will keep — and the name itself, trimmed, if
     * it is.
     *
     * <p>One function for declaring a category and for renaming one, for the reason
     * {@code AccountsService.judgedAsABill} gives: a name that would be refused if it were typed
     * from scratch should not be reachable by editing one that was not. It answers with the value to
     * write rather than merely saying yes, because judging and tidying are the same pass — the
     * trimmed name is what the duplicate check below then asks about, so " Groceries " cannot slip
     * past a category called "Groceries".
     *
     * @param categoryId the category being renamed, or null while one is being declared, for the log
     */
    private String judgedAsAName(long currentAccountId, Long categoryId, String name) {
        String itsName = name == null ? "" : name.trim();
        if (itsName.isBlank()) {
            throw refusingTheCategory(currentAccountId, categoryId, AGAINST_THE_RULES,
                    "Give the category a name, so that your spending is described in your own "
                            + "words — \"Groceries\", \"Fuel\", \"Going out\".");
        }
        return itsName;
    }

    /**
     * The figure in force on one category, if there is one.
     *
     * <p>One row at most, and that is the database's rule rather than this method's optimism: the
     * partial unique index {@link BudgetsOnStartUp} creates covers exactly the standing rows. The
     * states travel in from {@link BudgetState#theOnesStillStanding} so that what "standing" means
     * stays in one place.
     */
    private Optional<MonthlyBudget> theBudgetStandingOn(long currentAccountId, long categoryId) {
        return budgets.findByCurrentAccountIdAndCategoryIdAndStateIn(currentAccountId, categoryId,
                BudgetState.theOnesStillStanding());
    }

    /**
     * Ends a category's budget with the category, leaving the month it happened in still governed by
     * the figure that stood.
     *
     * <p>Done here rather than left to the customer, because a limit on a word nobody uses any more
     * is a limit nothing will ever be measured against, and a standing row pointing at an ended
     * category would be a figure the next month's read would quote for spending that cannot happen.
     * Ending is one-way in both directions: declaring the word afresh makes a new category with its
     * own identifier and no claim on this figure.
     *
     * <p>This month is kept, which is the one difference from a customer stopping the budget
     * themselves, and {@link MonthlyBudget#stoppedBecauseTheCategoryEnded} argues why: the spending
     * in this month really was made against this figure, and a month read that dropped it would
     * erase the standard somebody was actually held to. Months already gone are untouched either
     * way.
     *
     * <p>A category with no figure on it is the ordinary case and says so in the log, because a
     * reviewer asking why a month stopped quoting a budget should be able to see that the ending
     * was asked about at all.
     */
    private void stopTheBudgetBecauseTheCategoryEnded(long currentAccountId,
                                                      SpendingCategory category, Instant now) {
        Optional<MonthlyBudget> standing = theBudgetStandingOn(currentAccountId, category.getId());
        if (standing.isEmpty()) {
            log.debug("ended spending category carried no budget to stop currentAccountId={} "
                    + "categoryId={}", currentAccountId, category.getId());
            return;
        }
        MonthlyBudget budget = standing.get();
        budget.stoppedBecauseTheCategoryEnded(TheMonthAMomentFallsIn.of(now), now);
        budgets.save(budget);
        log.info("budget stopped with its category currentAccountId={} categoryId={} category={} "
                        + "budgetId={} amount={} effectiveFrom={} stoodThrough={} state={} "
                        + "stoppedAt={}",
                currentAccountId, category.getId(), category.getName(), budget.getId(),
                AmountOfMoney.asMoney(budget.getAmount()), budget.effectiveFrom(),
                budget.stoodThrough(), budget.getState(), budget.getStoodDownAt());
    }

    /**
     * Whether what arrived is a figure worth holding a category to — and the figure itself, quoted
     * to the cent, if it is.
     *
     * <p>{@code AmountOfMoney} owns both halves of the rule, so a budget of nothing and a budget of
     * 12.505 are refused here in the same sentences a deposit or a spend of them would be. A figure
     * that was never sent is its own objection, because "a budget must be worth having" is not a
     * helpful thing to say to somebody who left the box empty.
     *
     * <p>A budget of nought is refused rather than kept, and it is the one worth saying out loud: a
     * category allowed to cost nothing is a category its holder has stopped budgeting, which
     * {@link #stopBudgeting} already says — and two ways of saying one thing would be two states
     * every month read, and every carry after it, would have to mean the same by.
     */
    private BigDecimal judgedAsAMonthlyLimit(long currentAccountId, long categoryId,
                                             BigDecimal amount) {
        if (amount == null) {
            throw refusingTheBudget(currentAccountId, categoryId, BudgetRefused.Kind.AGAINST_THE_RULES,
                    "Say what this category is allowed to cost each month, in digits with a full "
                            + "stop, like 250.00.");
        }
        AmountOfMoney.whyItIsNotOne("budget", amount).ifPresent(reason -> {
            throw refusingTheBudget(currentAccountId, categoryId,
                    BudgetRefused.Kind.AGAINST_THE_RULES, reason);
        });
        return AmountOfMoney.quotedToTheCent(amount);
    }

    /**
     * Every refusal about a budget says why in the log as well as to whoever asked, because only one
     * of the two is kept: the reason reaches the person at the keyboard and nowhere else. The
     * account and the category are on the line because a reviewer tracing "it would not take my
     * figure" needs to know which category was being argued about.
     */
    private BudgetRefused refusingTheBudget(long currentAccountId, long categoryId,
                                            BudgetRefused.Kind kind, String reason) {
        log.warn("budget rejected currentAccountId={} categoryId={} kind={} reason={}",
                currentAccountId, categoryId, kind, reason);
        return new BudgetRefused(kind, reason);
    }

    /** How many words this account is currently describing its money with, for the cap and the log. */
    private long howManyAreStanding(long currentAccountId) {
        return categories.countByCurrentAccountIdAndStateIn(currentAccountId,
                CategoryState.theOnesStillStanding());
    }

    /**
     * Every refusal of a category says why in the log as well as to whoever asked, because only one
     * of the two is kept: the reason reaches the person at the keyboard and nowhere else. The
     * account and the category are on the line because a reviewer tracing "it would not take my
     * category" needs to know which account's list was being argued about.
     */
    private SpendingCategoryRefused refusingTheCategory(long currentAccountId, Long categoryId,
                                                        SpendingCategoryRefused.Kind kind,
                                                        String reason) {
        log.warn("spending category rejected currentAccountId={} categoryId={} kind={} reason={}",
                currentAccountId, categoryId, kind, reason);
        return new SpendingCategoryRefused(kind, reason);
    }

    /** The rows as the rest of the application reads them. */
    private List<ADeclaredCategory> asDeclared(List<SpendingCategory> found) {
        return found.stream().map(ADeclaredCategory::of).toList();
    }
}
