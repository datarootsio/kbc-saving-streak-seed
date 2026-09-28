package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.dataroots.savingstreak.accounts.ABillOnTheLedger;
import io.dataroots.savingstreak.accounts.ABillToCome;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.AnArrear;
import io.dataroots.savingstreak.accounts.AnIncomeToCome;
import io.dataroots.savingstreak.accounts.BillOutcome;
import io.dataroots.savingstreak.accounts.WhatACurrentAccountHolds;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a customer has actually got: one month of a current account, category by category, with what
 * each was allowed to cost beside what it cost.
 *
 * <p><strong>The third of this module's three faces, and the one the feature exists for.</strong>
 * {@link BudgetsService} owns the declarations — the words a customer describes their money with and
 * what each of them is allowed to cost — and {@link SpendsService} owns the movements. Neither of
 * them answers "how is this month going", because the answer is made of both halves and of a third
 * module's bill occurrences on top; that is this class. They are separate for the reason
 * {@code DepositsService}, {@code WithdrawalsService} and {@code MoneyMovementsService} are three: a
 * class that both declares and derives is a class two different readers open for two different
 * reasons.
 *
 * <p><strong>Named for the reads rather than for the module</strong>, matching the {@code /spending}
 * path it is served under. The alternative names are worse rather than merely different: a second
 * {@code BudgetsService} would say nothing about which half a reader had opened, and naming it for
 * the month would be naming it for one of the several questions it will answer — this month, a month
 * already gone, the history behind them, and the weeks ahead.
 *
 * <p><strong>Every figure is derived on every read and stored nowhere.</strong> No rollup table, no
 * monthly close, no job, no cursor to advance and nothing to invalidate. Correct a spend's split in
 * March and March changes; put a bill in a different category and every month that bill was taken in
 * changes with it; supersede a budget and this month quotes the new figure while the months before
 * it quote the old one. That is the decision the whole module is shaped by, and it is what makes a
 * correction fix the past as well as the present rather than leave a note saying the past was wrong.
 *
 * <p><strong>The arithmetic is done once, here.</strong> Committed and discretionary are told apart
 * by the movement and never by the category — a bill occurrence is money that left on a standing
 * instruction and a spend part is money the customer chose to spend, and one category holds both —
 * and the totals are added up in {@link WhatACategoryCostInAMonth} and
 * {@link WhatAnAccountSpentInAMonth} so that no page can add them differently.
 *
 * <p><strong>The dependency on Accounts runs one way.</strong> The bill occurrences are read through
 * Accounts' public ledger read and are not copied into anything here; the label saying which
 * category a bill is in is this module's own row, keyed on the bill. Accounts gains nothing and
 * learns nothing, which is the whole argument that keeps a bill's category out of
 * {@code RecurringBill}.
 *
 * <p><strong>It never asks whether a current account is real.</strong> The controller vouches for
 * the identifier in the path first, in the words {@code AccountsService.noSuchCurrentAccount} owns —
 * which is why an account with nothing on it answers with an empty month rather than a refusal, and
 * one nobody has heard of answers with a refusal rather than an empty month.
 */
@Service
public class SpendingService {

    private static final Logger log = LoggerFactory.getLogger(SpendingService.class);

    /**
     * How an absent figure reads in a log line, where a null would say less. A category nobody has
     * put a limit on has no budget, no carry, no allowance and nothing left, and a reviewer reading
     * "null" four times would have to go and find out which of those was a bug.
     */
    private static final String NOT_BUDGETED = "not budgeted";

    /**
     * How an absent trailing average reads in a log line. A category in its first month has no
     * months behind it to be judged against, which is an absence with a different cause from an
     * absent budget and deserves its own words rather than a second "not budgeted" a reviewer would
     * have to go and interpret.
     */
    private static final String NO_MONTHS_BEHIND_IT = "no months behind it";

    /**
     * How a category that this month does not hold reads in a log line: an ended one appears in the
     * comparison for the months it was live and has no row for the month the clock is in, which is
     * the honest answer rather than a nought.
     */
    private static final String NOT_LIVE_THIS_MONTH = "not live this month";

    private final SpendRepository spends;

    private final SpendPartRepository parts;

    /**
     * The declarations this read is measured against: what the account's categories are, and what
     * each of them was allowed to cost in the month being asked about.
     *
     * <p>Asked of the face that owns them rather than read from the rows here, for the reason
     * {@link SpendsService} asks it whether a category is one money can be filed under: the module
     * that owns a concept owns the questions about it, and a second copy of "which figure stood in
     * March" is a copy free to drift from the one the declaration path writes.
     */
    private final BudgetsService budgets;

    /**
     * Where the committed half of a month comes from: the dates this account's bills were presented
     * on, paid and unpaid alike, read through Accounts' own ledger read model.
     *
     * <p>A read and never a second record. What a bill is, what it costs and which days it fell due
     * on are Accounts' answers, and copying any of them into this module would be a second answer
     * that could disagree with the first.
     */
    private final AccountsService accounts;

    /**
     * The application's clock, which is the only thing that says which month "this month" is.
     * Nothing here calls {@code Instant.now()} — the development clock can be wound years forward,
     * and a page that read the wall clock would draw a month nobody in this application is in.
     */
    private final Clock clock;

    SpendingService(SpendRepository spends, SpendPartRepository parts, BudgetsService budgets,
                    AccountsService accounts, Clock clock) {
        this.spends = spends;
        this.parts = parts;
        this.budgets = budgets;
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * How this month is going on one current account.
     *
     * <p>"This" is the month the application's clock reads, in the zone every calendar thing here is
     * counted in, which is what makes the answer the same for a customer in Brussels at half past
     * midnight on the first as it is for the machine recording it in UTC an hour earlier.
     *
     * <p>A month still running is reported exactly as one already gone. Nothing about the figures
     * waits for the month to end — there is no close — so a customer reads on the third what they
     * would read on the thirty-first, with less in it.
     */
    @Transactional(readOnly = true)
    public WhatAnAccountSpentInAMonth thisMonthOn(long currentAccountId) {
        Instant now = clock.instant();
        YearMonth month = TheMonthAMomentFallsIn.of(now);
        log.debug("this month's spending asked for currentAccountId={} clockReads={} month={}",
                currentAccountId, now, month);
        return theMonthOn(currentAccountId, month);
    }

    /**
     * How one month went on one current account — this one, or one already gone.
     *
     * <p>One method for both, deliberately, because they are one question. A month already gone is
     * not a different sort of read with different rules: it is the same fold over the same
     * declarations and the same movements, asked about a different window. A second path for history
     * would be a second place to get the arithmetic right, and the first month the two disagreed
     * would be the month a customer stopped believing either.
     *
     * <p>A month in the future answers honestly rather than refusing: the budgets that stand today
     * stand in it, nothing has been spent in it yet, and what comes back is a month entirely unspent
     * — which is a true statement about a month nobody has lived through. The weeks-ahead read is
     * where a forecast belongs, and it is a different question with different assumptions. That is
     * why there is no refusal about a month: every month is answerable, and the only thing that can
     * go wrong is a request whose characters are not a month at all, which is a fact about the
     * request and is answered in the web layer.
     */
    @Transactional(readOnly = true)
    public WhatAnAccountSpentInAMonth theMonthOn(long currentAccountId, YearMonth month) {
        // How far back this read has to look, which is how far back the carry is folded and not one
        // month further. The bound belongs to WhatCarriesIntoAMonth, and asking it rather than
        // holding a second copy of the figure is what keeps the window read and the window folded
        // the same window.
        YearMonth earliest = WhatCarriesIntoAMonth.theEarliestMonthItWalksFrom(month);
        Instant from = TheMonthAMomentFallsIn.theMomentItBegins(earliest);
        Instant before = TheMonthAMomentFallsIn.theMomentTheNextOneBegins(month);
        // The window a reviewer needs in order to check every figure below against the records: the
        // month asked about, the earliest month behind it that can still reach it through a carry,
        // and the two moments the stretch is open between. Both ends on the line because the zone is
        // the whole of what decides which month a spend at midnight belongs to.
        log.debug("spending read for a month currentAccountId={} month={} carriedFrom={} from={} "
                        + "before={}",
                currentAccountId, month, earliest, from, before);

        Map<Long, Map<YearMonth, ABudgetThatStood>> allowed =
                budgets.whatEachCategoryWasAllowedOver(currentAccountId, earliest, month);
        Map<Long, Map<YearMonth, BigDecimal>> committed =
                whatTheBillsCommittedOver(currentAccountId, earliest, month);
        WhatWasChosen chosen = whatWasChosenOver(currentAccountId, earliest, month, from, before);

        List<WhatACategoryCostInAMonth> categories = new ArrayList<>();
        for (ADeclaredCategory category : budgets.everyCategoryOn(currentAccountId)) {
            Map<YearMonth, ABudgetThatStood> itsFigures =
                    allowed.getOrDefault(category.categoryId(), Map.of());
            Map<YearMonth, BigDecimal> itsCommitted =
                    committed.getOrDefault(category.categoryId(), Map.of());
            Map<YearMonth, BigDecimal> itsDiscretionary =
                    chosen.byCategory().getOrDefault(category.categoryId(), Map.of());
            ABudgetThatStood stood = itsFigures.get(month);
            BigDecimal committedThisMonth = itsCommitted.getOrDefault(month, BigDecimal.ZERO);
            BigDecimal chosenThisMonth = itsDiscretionary.getOrDefault(month, BigDecimal.ZERO);
            if (!belongsInThisMonth(category, stood, committedThisMonth, chosenThisMonth)) {
                continue;
            }
            // Folded only where a figure stands, because a category nobody is measuring has nothing
            // arriving in it — and because a fold nobody would read is a fold nobody should pay for
            // or have to skip past in the log.
            BigDecimal carriedIn = stood == null
                    ? BigDecimal.ZERO
                    : WhatCarriesIntoAMonth.carriedInto(category.categoryId(), month, itsFigures,
                            whatItCostInEachMonth(itsCommitted, itsDiscretionary));
            categories.add(WhatACategoryCostInAMonth.of(category, stood, carriedIn,
                    committedThisMonth, chosenThisMonth));
        }

        WhatAnAccountSpentInAMonth read = WhatAnAccountSpentInAMonth.of(currentAccountId, month,
                categories, chosen.uncategorised().getOrDefault(month, BigDecimal.ZERO));
        log.debug("spending read currentAccountId={} month={} categories={} budgeted={} "
                        + "carriedIn={} allowed={} committed={} discretionary={} spent={} left={} "
                        + "uncategorised={}",
                currentAccountId, month, read.categories().size(),
                read.budgeted() == null ? NOT_BUDGETED : AmountOfMoney.asMoney(read.budgeted()),
                read.carriedIn() == null ? NOT_BUDGETED : AmountOfMoney.asMoney(read.carriedIn()),
                read.allowed() == null ? NOT_BUDGETED : AmountOfMoney.asMoney(read.allowed()),
                AmountOfMoney.asMoney(read.committed()),
                AmountOfMoney.asMoney(read.discretionary()), AmountOfMoney.asMoney(read.spent()),
                read.left() == null ? NOT_BUDGETED : AmountOfMoney.asMoney(read.left()),
                AmountOfMoney.asMoney(read.uncategorised()));
        return read;
    }


    /**
     * How the last few months went on one current account, category by category, so that a customer
     * can tell a bad month from a habit.
     *
     * <p><strong>A sibling of {@link #theMonthOn} rather than six calls to it.</strong> Each of
     * those is a full read of the account's budget rows, its bill occurrences and its spends,
     * followed by a fold over as many as twenty-four months; six of them would be six identical
     * passes over almost exactly the same records, on a page a customer opens to look at one
     * account. So the window is read once — from the earliest month a carry into the oldest compared
     * month can reach, through the month the clock is in — and every month of the comparison is
     * folded out of the maps that came back. Those maps are keyed by category <em>and</em> month
     * already, which is exactly the shape a six-month history wants, and
     * {@link WhatCarriesIntoAMonth#carriedInto} is a pure function over them that is cheap to call
     * once per month.
     *
     * <p><strong>The arithmetic is the month read's and is not repeated.</strong> Every row is a
     * {@link WhatACategoryCostInAMonth} assembled by the one factory that adds these figures up, so
     * a month drawn on the budget card and the same month drawn in the comparison beside it cannot
     * disagree — which is the first thing a customer would notice if they did.
     *
     * <p><strong>Only the months a category existed for.</strong> A category declared two months ago
     * has two rows, not six with four noughts in front of them, and one that has been ended stops at
     * the month it was ended in while still appearing for the months it was live. Both are the same
     * rule and the same reason: a nought is a claim that a customer spent nothing on something, and
     * a month before they had ever named the thing is a month in which that claim is not true but
     * meaningless. The average taken over padded noughts would call every real month a spike.
     *
     * <p><strong>A month before the category's first budget took effect keeps its budget
     * absent.</strong> Nothing here fills one in: the row comes back with {@code budgeted},
     * {@code carriedIn}, {@code allowed} and {@code left} all null, and the spending beside them as
     * the fact it is. That is user story 41 and it is the one thing this read must not get wrong —
     * a blank drawn as a nought would tell a customer they overspent a limit they never set.
     *
     * <p>Nothing is stored and no job produces any of it, exactly as for every other read in this
     * module. Correct a split in a month already gone and that month's row, the average taken over
     * it and this month's comparison against that average all move together on the next read.
     */
    @Transactional(readOnly = true)
    public HowAnAccountHasBeenGoing theLastFewMonthsOn(long currentAccountId) {
        Instant now = clock.instant();
        YearMonth thisMonth = TheMonthAMomentFallsIn.of(now);
        YearMonth firstCompared = HowAnAccountHasBeenGoing.theEarliestMonthCompared(thisMonth);
        // How far back the records have to be read: not the first month compared, but the earliest
        // month a carry into it can still reach, because the oldest row in the comparison quotes a
        // carry of its own and that carry is a fold over the months behind it.
        YearMonth earliest = WhatCarriesIntoAMonth.theEarliestMonthItWalksFrom(firstCompared);
        Instant from = TheMonthAMomentFallsIn.theMomentItBegins(earliest);
        Instant before = TheMonthAMomentFallsIn.theMomentTheNextOneBegins(thisMonth);
        log.debug("the last few months asked for currentAccountId={} clockReads={} month={} "
                        + "comparedFrom={} carriedFrom={} from={} before={}",
                currentAccountId, now, thisMonth, firstCompared, earliest, from, before);

        // One set of range reads for the whole comparison. Three queries whatever the window holds,
        // rather than three per month, which is the whole reason this method exists beside
        // theMonthOn rather than calling it six times.
        Map<Long, Map<YearMonth, ABudgetThatStood>> allowed =
                budgets.whatEachCategoryWasAllowedOver(currentAccountId, earliest, thisMonth);
        Map<Long, Map<YearMonth, BigDecimal>> committed =
                whatTheBillsCommittedOver(currentAccountId, earliest, thisMonth);
        WhatWasChosen chosen = whatWasChosenOver(currentAccountId, earliest, thisMonth, from, before);

        List<HowACategoryHasBeenGoing> compared = new ArrayList<>();
        for (ADeclaredCategory category : budgets.everyCategoryOn(currentAccountId)) {
            Map<YearMonth, ABudgetThatStood> itsFigures =
                    allowed.getOrDefault(category.categoryId(), Map.of());
            Map<YearMonth, BigDecimal> itsCommitted =
                    committed.getOrDefault(category.categoryId(), Map.of());
            Map<YearMonth, BigDecimal> itsDiscretionary =
                    chosen.byCategory().getOrDefault(category.categoryId(), Map.of());
            // Added up once for the whole category rather than once per month of the window: the
            // fold takes the same map whichever month it is folding into, and building it six times
            // would be six copies of one answer.
            Map<YearMonth, BigDecimal> itsCost =
                    whatItCostInEachMonth(itsCommitted, itsDiscretionary);

            List<AMonthInTheComparison> months = new ArrayList<>();
            for (YearMonth month = firstCompared; !month.isAfter(thisMonth);
                    month = month.plusMonths(1)) {
                if (!wasLiveIn(category, month)) {
                    continue;
                }
                ABudgetThatStood stood = itsFigures.get(month);
                BigDecimal committedThen = itsCommitted.getOrDefault(month, BigDecimal.ZERO);
                BigDecimal chosenThen = itsDiscretionary.getOrDefault(month, BigDecimal.ZERO);
                // Folded only where a figure stood, for the reason the month read gives: a month
                // nobody was measuring has nothing arriving in it, and a fold nobody would read is
                // one nobody should pay for or have to skip past in the log.
                BigDecimal carriedIn = stood == null
                        ? BigDecimal.ZERO
                        : WhatCarriesIntoAMonth.carriedInto(category.categoryId(), month, itsFigures,
                                itsCost);
                AMonthInTheComparison row = AMonthInTheComparison.of(month,
                        WhatACategoryCostInAMonth.of(category, stood, carriedIn, committedThen,
                                chosenThen));
                months.add(row);
                // One line per month a comparison was folded over, naming what that month
                // contributed. This is what a customer disputing "why does it say I am spending
                // more than usual" is answered from, and it is the reason the fold logs rather than
                // merely returning.
                log.debug("a month in the comparison currentAccountId={} categoryId={} name={} "
                                + "month={} budgeted={} carriedIn={} allowed={} committed={} "
                                + "discretionary={} spent={} left={}",
                        currentAccountId, category.categoryId(), category.name(), month,
                        orNotBudgeted(row.budgeted()), orNotBudgeted(row.carriedIn()),
                        orNotBudgeted(row.allowed()), AmountOfMoney.asMoney(row.committed()),
                        AmountOfMoney.asMoney(row.discretionary()),
                        AmountOfMoney.asMoney(row.spent()), orNotBudgeted(row.left()));
            }
            if (months.isEmpty()) {
                log.debug("a category left out of the comparison currentAccountId={} categoryId={} "
                                + "name={} declaredAt={} endedAt={} reason={}",
                        currentAccountId, category.categoryId(), category.name(),
                        category.declaredAt(), category.endedAt(),
                        "it was not one of the things this account's money went on in any month of "
                                + "the window");
                continue;
            }
            HowACategoryHasBeenGoing going =
                    HowACategoryHasBeenGoing.of(category, List.copyOf(months), thisMonth);
            log.debug("a category compared currentAccountId={} categoryId={} name={} state={} "
                            + "months={} from={} to={} trailingAverage={} averagedOver={} "
                            + "thisMonthSpent={} comparedWithTheAverage={}",
                    currentAccountId, category.categoryId(), category.name(), category.state(),
                    going.months().size(), going.months().get(0).month(),
                    going.months().get(going.months().size() - 1).month(),
                    orNoAverage(going.trailingAverage()), going.monthsTheAverageIsOver(),
                    going.thisMonth() == null
                            ? NOT_LIVE_THIS_MONTH
                            : AmountOfMoney.asMoney(going.thisMonth().spent()),
                    theQuoteAgainstTheAverage(going));
            compared.add(going);
        }

        HowAnAccountHasBeenGoing read =
                HowAnAccountHasBeenGoing.of(currentAccountId, thisMonth, List.copyOf(compared));
        log.debug("the last few months read currentAccountId={} month={} comparedFrom={} "
                        + "monthsInTheWindow={} categoriesCompared={}",
                currentAccountId, read.month(), read.earliest(), read.months().size(),
                read.categories().size());
        return read;
    }

    /**
     * Whether a category was one of the things this account's money went on in a month.
     *
     * <p>From the month it was declared in to the month it was ended in, both included. The month it
     * was declared in counts because the customer was describing their money that way for part of
     * it, and so does the month it was ended in, for the same reason and because that month holds
     * the spending they did before they closed it.
     *
     * <p><strong>This is what stops the comparison padding.</strong> A category younger than the
     * window reports the months it existed for and no others; an ended one appears for the months it
     * was live and stops. A nought in either place would be this application claiming a customer
     * spent nothing on something that was not a thing they spent on, and an average taken over those
     * noughts would make every month they did spend look like a spike.
     */
    private static boolean wasLiveIn(ADeclaredCategory category, YearMonth month) {
        if (month.isBefore(TheMonthAMomentFallsIn.of(category.declaredAt()))) {
            return false;
        }
        return category.endedAt() == null
                || !month.isAfter(TheMonthAMomentFallsIn.of(category.endedAt()));
    }

    /** A figure for the log, or the words that say it was never declared rather than "null". */
    private static String orNotBudgeted(BigDecimal amount) {
        return amount == null ? NOT_BUDGETED : AmountOfMoney.asMoney(amount);
    }

    /**
     * The same for the trailing average, which is absent for a different reason: a category in its
     * first month has no months behind it, and "not budgeted" would be the wrong sentence about a
     * figure that has nothing to do with a budget.
     */
    private static String orNoAverage(BigDecimal amount) {
        return amount == null ? NO_MONTHS_BEHIND_IT : AmountOfMoney.asMoney(amount);
    }

    /**
     * How this month sits against the months behind it, for the log — and, where it does not, which
     * of the two halves of the comparison is missing.
     *
     * <p>Two absences with one figure between them, and telling them apart is the whole of why this
     * is a method rather than a ternary. A category in its first month has nothing behind it to be
     * judged against; an ended one has months behind it and no month of its own to judge. A reviewer
     * reading one sentence for both would have to go and work out which had happened.
     */
    private static String theQuoteAgainstTheAverage(HowACategoryHasBeenGoing going) {
        if (going.comparedWithTheAverage() != null) {
            return AmountOfMoney.asMoney(going.comparedWithTheAverage());
        }
        return going.thisMonth() == null ? NOT_LIVE_THIS_MONTH : NO_MONTHS_BEHIND_IT;
    }

    /**
     * The next six weeks of cash flow on one current account, and what that says its holder could
     * save each week.
     *
     * <p><strong>The figure this whole feature exists to produce.</strong> Everything else this
     * module reads is a step towards it: the words a customer describes their money with, the figure
     * on each of them, the carry between months, the bills filed under them. What comes out is six
     * rows and one offer — and the offer is the sentence the application has never been able to say
     * before, which is how much a customer could put away without breaking anything they have
     * already promised themselves.
     *
     * <p><strong>Six weeks from the Monday this week began on</strong>, using
     * {@link SavingsWeek}'s own boundary rather than a second definition of a week. The window opens
     * behind today on every day but a Monday, and that is right: the row a customer is standing in
     * is the row they most want to read, and a window that opened today would draw a first row four
     * days long and call it a week.
     *
     * <p><strong>The dates come from Accounts' calendars and never from a copy of them.</strong>
     * {@code WhenABillIsDue} and {@code WhenIncomeIsDue} are what the 02:30 and 01:00 runs walk, so
     * this forecast and those runs cannot disagree about which day anything falls on — which is the
     * promise {@link TheWeeksAhead} is under and the one a trainer checks by winding the clock six
     * weeks and counting what moved.
     *
     * <p><strong>One read of this month, and the carry is not folded twice.</strong> Both budget
     * figures — what is left of the budgets in the month the clock is in, and what the budgets
     * allow in the months after it — come off a single {@link #thisMonthOn} call, which has already
     * folded the carry once. Asking {@link WhatCarriesIntoAMonth} again here would be a second fold
     * of the same chain, free to disagree with the one the month card beside this one is drawn from.
     *
     * <p><strong>A future month quotes today's budgets and today's carry, unchanged.</strong> What a
     * category will be allowed in six weeks' time is what it is allowed now plus what has already
     * carried into this month; the surplus this month has not finished earning is not counted,
     * because it has not been earned. That is the conservative direction and it is deliberate: this
     * is a floor, not a projection of what the carry will have become.
     *
     * <p><strong>It never asks whether a current account is real</strong>, for the reason
     * {@link #theMonthOn} does not: the controller vouches for the identifier in the path first, in
     * the words {@code AccountsService.noSuchCurrentAccount} owns.
     */
    @Transactional(readOnly = true)
    public TheWeeksAhead theWeeksAheadOn(long currentAccountId) {
        Instant now = clock.instant();
        LocalDate today = now.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
        List<SavingsWeek> weeks = theWeeksFrom(today);
        LocalDate from = weeks.get(0).startsOn();
        LocalDate until = weeks.get(weeks.size() - 1).endsOn();
        // The window every figure below is counted over, and the day it was counted from, because a
        // customer who says the rows on their screen are about the wrong weeks is otherwise somebody
        // whose calendar has to be reconstructed by hand. Both ends on the line, because the zone is
        // the whole of what decides which week a day at a month's turn belongs to.
        log.debug("the weeks ahead asked for currentAccountId={} clockReads={} today={} "
                        + "between={}..{} weeks={}",
                currentAccountId, now, today, from, until, weeks.size());

        Map<LocalDate, BigDecimal> arriving = whatIsDueToArriveInEachWeek(currentAccountId, from,
                until);
        Map<LocalDate, BigDecimal> committed = whatIsCommittedInEachWeek(currentAccountId, from,
                until);
        Map<LocalDate, BigDecimal> claimed = whatTheBudgetsClaimInEachWeek(currentAccountId, weeks,
                until);

        List<WhatAWeekAheadHolds> rows = new ArrayList<>();
        for (SavingsWeek week : weeks) {
            WhatAWeekAheadHolds row = WhatAWeekAheadHolds.of(week,
                    arriving.getOrDefault(week.startsOn(), BigDecimal.ZERO),
                    committed.getOrDefault(week.startsOn(), BigDecimal.ZERO),
                    claimed.getOrDefault(week.startsOn(), BigDecimal.ZERO));
            // The apportionment behind one row, which is what a customer asking "why does that week
            // leave me forty euros" is answered from: the three figures that went into it and the
            // subtraction that came out. One line per row rather than one for the six, so that the
            // row somebody is asking about can be found by its Monday.
            log.debug("a week ahead currentAccountId={} week={} arriving={} committed={} "
                            + "claimedByBudgets={} leftOver={}",
                    currentAccountId, week, AmountOfMoney.asMoney(row.arriving()),
                    AmountOfMoney.asMoney(row.committed()),
                    AmountOfMoney.asMoney(row.claimedByBudgets()),
                    AmountOfMoney.asMoney(row.leftOver()));
            rows.add(row);
        }

        TheWeeksAhead read = TheWeeksAhead.of(currentAccountId, rows);
        log.debug("the weeks ahead currentAccountId={} between={}..{} weeks={} arriving={} "
                        + "committed={} claimedByBudgets={} leftOver={} couldSaveWeekly={} "
                        + "worthOffering={}",
                currentAccountId, read.from(), read.until(), read.weeks().size(),
                AmountOfMoney.asMoney(read.arriving()), AmountOfMoney.asMoney(read.committed()),
                AmountOfMoney.asMoney(read.claimedByBudgets()),
                AmountOfMoney.asMoney(read.leftOver()),
                AmountOfMoney.asMoney(read.couldSaveWeekly()), read.isWorthOffering());
        return read;
    }

    /**
     * The weeks the card is drawn over: the one today falls in, and the five after it.
     *
     * <p>Counted through {@link SavingsWeek} a week at a time rather than by adding multiples of
     * seven days to a moment, for the reason that class gives about its own {@code previous}: the
     * Monday after a Monday is a Monday whatever the clocks did in between, while a fixed span added
     * to midnight lands an hour either side of it twice a year.
     */
    private static List<SavingsWeek> theWeeksFrom(LocalDate today) {
        List<SavingsWeek> weeks = new ArrayList<>();
        SavingsWeek week = SavingsWeek.containing(today);
        for (int counted = 0; counted < TheWeeksAhead.HOW_MANY_WEEKS_IT_LOOKS_AHEAD; counted++) {
            weeks.add(week);
            week = new SavingsWeek(week.startsOn().plusWeeks(1));
        }
        return weeks;
    }

    /**
     * What is due to land in each week of the window, by the Monday the week begins on.
     *
     * <p>Read through Accounts' own payday calendar, counted from the declaration's cursor rather
     * than from today, so that a salary the 01:00 run still owes is money about to arrive rather
     * than money nobody can see. Those days are dated before the window opens and are folded into
     * the first row by {@link #theWeekItFallsIn}, which is where the next run will actually credit
     * them.
     *
     * <p>An account with no declared income has nothing arriving, which is an empty map rather than
     * a row of noughts: every week still draws, because a week nothing lands in is a real week.
     */
    private Map<LocalDate, BigDecimal> whatIsDueToArriveInEachWeek(long currentAccountId,
                                                                   LocalDate from,
                                                                   LocalDate until) {
        Map<LocalDate, BigDecimal> byWeek = new HashMap<>();
        List<AnIncomeToCome> paydays =
                accounts.whatTheIncomeOfACurrentAccountWillBring(currentAccountId, from, until);
        for (AnIncomeToCome payday : paydays) {
            byWeek.merge(theWeekItFallsIn(payday.dueOn(), from), payday.amount(), BigDecimal::add);
        }
        log.debug("what arrives in the weeks ahead currentAccountId={} between={}..{} paydays={} "
                        + "owedFromBefore={} weeksMoneyArrivesIn={}",
                currentAccountId, from, until, paydays.size(),
                paydays.stream().filter(AnIncomeToCome::owedRatherThanStillToCome).count(),
                byWeek.size());
        return byWeek;
    }

    /**
     * What is committed in each week of the window, by the Monday the week begins on: the bill dates
     * falling in it, and every arrear outstanding on the first row.
     *
     * <p><strong>The arrears go on the first week, and that is the decision this figure rests
     * on.</strong> An arrear is offered the very next money in — the 02:30 run settles what is owed
     * before it presents anything new — so a forecast that waited for a bill's date to come round
     * again would tell a customer carrying two rents that they had room they do not have.
     * {@code TheMonthAhead} puts the same money inside its own {@code billsDue} for the same reason.
     *
     * <p><strong>The bills are asked for by customer and kept by account.</strong> Accounts answers
     * this forecast per customer, which is the shape the year-ahead bar wanted; a household with two
     * current accounts has claims on both, and this card is about one of them. Filtering here is one
     * comparison on a record that already carries the account, and it is cheaper than a second
     * forecast in Accounts that would be a second walk of the same calendar.
     *
     * <p>A bill dated before the window opens is one the nightly run has not caught up with — the
     * ordinary state of every bill on a wound clock — and is folded into the first row, which is
     * where the next run will take it.
     */
    private Map<LocalDate, BigDecimal> whatIsCommittedInEachWeek(long currentAccountId,
                                                                 LocalDate from, LocalDate until) {
        Map<LocalDate, BigDecimal> byWeek = new HashMap<>();
        Long customerId = accounts.currentAccountWith(currentAccountId)
                .map(WhatACurrentAccountHolds::customerId)
                .orElse(null);
        int dates = 0;
        if (customerId != null) {
            for (ABillToCome bill : accounts.whatTheBillsOfACustomerWillTake(customerId, from,
                    until)) {
                if (bill.currentAccountId() != currentAccountId) {
                    continue;
                }
                dates++;
                byWeek.merge(theWeekItFallsIn(bill.dueOn(), from), bill.amount(), BigDecimal::add);
            }
        }
        BigDecimal arrears = accounts.arrearsOn(currentAccountId).stream()
                .map(AnArrear::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (arrears.signum() != 0) {
            byWeek.merge(from, arrears, BigDecimal::add);
        }
        log.debug("what is committed in the weeks ahead currentAccountId={} between={}..{} "
                        + "billDatesToCome={} arrearsOutstanding={} arrearsPutOn={} "
                        + "weeksWithSomethingCommitted={}",
                currentAccountId, from, until, dates, AmountOfMoney.asMoney(arrears), from,
                byWeek.size());
        return byWeek;
    }

    /**
     * What the budgets claim of each week of the window, by the Monday the week begins on.
     *
     * <p><strong>Every budget spent to its limit, which is what makes the leftover a floor.</strong>
     * The month the clock is in contributes what is <em>left</em> of each budget — the figure plus
     * its carry, less what the month has already cost — and the months after it contribute the whole
     * of what each budget allows with today's carry unchanged. Each month's figure is then spread
     * evenly over that month's days and summed into weeks by
     * {@link HowAMonthsBudgetIsSpreadOverWeeks}.
     *
     * <p><strong>A category already over its limit claims nothing more, rather than claiming a
     * negative.</strong> "Spent to its limit" is the assumption, and a category past its limit has
     * no further claim on money that has not arrived yet; letting it contribute a negative would
     * have an overspent Groceries quietly <em>increase</em> what the card says is spare, which is
     * the one direction this figure must never be wrong in. The overspend is money that has already
     * left, and it has already left the balance the customer is reading beside this card.
     *
     * <p><strong>A month behind the current one claims nothing.</strong> The first row begins on the
     * Monday this week began on, which at the turn of a month is a Monday in the month before — and
     * a month already over is a record of what happened rather than a claim on money still to come.
     * Its days in that row contribute nothing, and that is the honest answer rather than a rounding.
     *
     * <p>One read of this month serves both figures, and the carry is folded once. See
     * {@link #theWeeksAheadOn}.
     */
    private Map<LocalDate, BigDecimal> whatTheBudgetsClaimInEachWeek(long currentAccountId,
                                                                     List<SavingsWeek> weeks,
                                                                     LocalDate until) {
        WhatAnAccountSpentInAMonth thisMonth = thisMonthOn(currentAccountId);
        YearMonth theMonthTheClockIsIn = thisMonth.month();
        YearMonth theLastMonthTheWindowTouches = TheMonthAMomentFallsIn.of(until);
        BigDecimal whatIsLeftOfThisMonth = totalOverBudgetedCategories(thisMonth,
                WhatACategoryCostInAMonth::left);
        BigDecimal whatALaterMonthAllows = totalOverBudgetedCategories(thisMonth,
                WhatACategoryCostInAMonth::allowed);

        Set<LocalDate> theWeeksDrawn = new HashSet<>();
        for (SavingsWeek week : weeks) {
            theWeeksDrawn.add(week.startsOn());
        }
        Map<LocalDate, BigDecimal> byWeek = new HashMap<>();
        for (YearMonth month = theMonthTheClockIsIn;
             !month.isAfter(theLastMonthTheWindowTouches);
             month = month.plusMonths(1)) {
            BigDecimal allowance = month.equals(theMonthTheClockIsIn)
                    ? whatIsLeftOfThisMonth
                    : whatALaterMonthAllows;
            HowAMonthsBudgetIsSpreadOverWeeks.acrossTheWeeksItTouches(month, allowance)
                    .forEach((week, share) -> {
                        if (theWeeksDrawn.contains(week)) {
                            byWeek.merge(week, share, BigDecimal::add);
                        }
                    });
        }
        log.debug("what the budgets claim in the weeks ahead currentAccountId={} "
                        + "monthTheClockIsIn={} lastMonthTheWindowTouches={} leftOfThisMonth={} "
                        + "aLaterMonthAllows={} weeksClaimedFrom={}",
                currentAccountId, theMonthTheClockIsIn, theLastMonthTheWindowTouches,
                AmountOfMoney.asMoney(whatIsLeftOfThisMonth),
                AmountOfMoney.asMoney(whatALaterMonthAllows), byWeek.size());
        return byWeek;
    }

    /**
     * One column added down the categories a figure actually stands on, with nothing below nought.
     *
     * <p>Over the budgeted categories only, because a category nobody has put a limit on claims
     * nothing: the customer is watching it rather than policing it, which is a thing they are
     * allowed to choose, and a forecast that invented a claim for it would be holding them to a
     * figure they never gave. And never below nought, for the reason
     * {@link #whatTheBudgetsClaimInEachWeek} gives: a category past its limit has no further claim
     * on money that has not arrived yet.
     */
    private static BigDecimal totalOverBudgetedCategories(WhatAnAccountSpentInAMonth month,
                                                          Function<WhatACategoryCostInAMonth,
                                                                  BigDecimal> column) {
        return month.categories().stream()
                .filter(WhatACategoryCostInAMonth::isBudgeted)
                .map(column)
                .map(figure -> figure.max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Which of the six rows a dated thing belongs to: the week it falls in, or the first row when it
     * is dated before the window opens.
     *
     * <p>The fold is the whole of this method. Both calendars are counted from their own cursors
     * rather than from today — which is what stops the forecast and the nightly runs disagreeing —
     * so anything the runs have not caught up with comes back dated behind the window. That is not a
     * date in the past: it is a debit or a credit the <em>next</em> run is going to make, so it
     * belongs on the row the customer is standing in. Dropping it would hide exactly the money that
     * is about to move, and drawing a seventh row behind the first would be drawing a week that has
     * already gone.
     */
    private static LocalDate theWeekItFallsIn(LocalDate day, LocalDate from) {
        return day.isBefore(from) ? from : SavingsWeek.containing(day).startsOn();
    }

    /**
     * What one category cost in each month of the window: the bills filed to it and the spends made
     * under it, added together, month by month.
     *
     * <p><strong>The committed half is in it, and that is the decision the envelope rests on.</strong>
     * What a month cost is what left the account under that word, whether it left on a standing
     * instruction or because somebody chose to spend it — so a category whose bills alone take it
     * over its figure carries that overspend into next month exactly as one overspent at the
     * supermarket does. A fold that counted only the discretionary half would let a customer
     * overfill an envelope with their own standing orders and never be told.
     *
     * <p>Only the months either half actually holds are in the answer; the fold reads a month that
     * is missing as having cost nothing, which is what a month with no bills and no spends in it did.
     */
    private static Map<YearMonth, BigDecimal> whatItCostInEachMonth(
            Map<YearMonth, BigDecimal> committed, Map<YearMonth, BigDecimal> discretionary) {
        Map<YearMonth, BigDecimal> cost = new HashMap<>(committed);
        discretionary.forEach((month, amount) -> cost.merge(month, amount, BigDecimal::add));
        return cost;
    }

    /**
     * Whether a month has anything to say about a category at all.
     *
     * <p>Every standing category is drawn whatever happened in it, because a category with nothing
     * spent is a real answer and seeing it is how a customer knows they stayed inside it — a page
     * that hid the empty ones would hide exactly the good news.
     *
     * <p>An ended category is drawn only when the month actually holds something for it: a figure
     * that stood, money committed to it, or money spent under it. That is what makes "ending a
     * category mid-month still reports what was spent against the budget that stood" true without
     * making every month from now on carry a list of words its holder has stopped using.
     */
    private static boolean belongsInThisMonth(ADeclaredCategory category, ABudgetThatStood stood,
                                              BigDecimal committed, BigDecimal discretionary) {
        return category.state().isStillStanding()
                || stood != null
                || committed.signum() != 0
                || discretionary.signum() != 0;
    }

    /**
     * What the bills filed in each category actually took in this month, by category identifier.
     *
     * <p><strong>Read either way, paid and unpaid, and the arithmetic is done in one place.</strong>
     * A date the account could not cover took nothing, so it contributes nothing — there is no
     * partial payment in this application and an unpaid bill leaves the balance exactly where it was
     * — but the row is walked all the same rather than filtered out of the query. What a month cost
     * is then one rule with one exception written down in one place, instead of a silence that a
     * later reader would have to reconstruct. The DEBUG line says how many of each there were, which
     * is what a reviewer asking "why is Housing empty this month" reads.
     *
     * <p><strong>The month is the one the date was settled in, not the one it was owed in.</strong>
     * An arrear owed in March and paid in June is money that left the account in June, and a budget
     * is a limit on what leaves: quoting it against March would put a debit in a month whose balance
     * it never touched. {@code ABillOnTheLedger} makes the same argument about where a row belongs
     * in a ledger, and this is the same rule applied to a sum.
     *
     * <p>A bill in no category is simply not in the label list and contributes to nothing, which is
     * the honest shape: there is no row saying where it goes.
     */
    private Map<Long, Map<YearMonth, BigDecimal>> whatTheBillsCommittedOver(long currentAccountId,
                                                                            YearMonth earliest,
                                                                            YearMonth month) {
        Map<Long, Long> categoryOfBill = new HashMap<>();
        for (TheCategoryABillIsIn filed : budgets.theCategoryEachBillIsIn(currentAccountId)) {
            categoryOfBill.put(filed.billId(), filed.categoryId());
        }
        Map<Long, Map<YearMonth, BigDecimal>> committed = new HashMap<>();
        int read = 0;
        int tookNothing = 0;
        for (ABillOnTheLedger presented : accounts.billsPresentedOn(List.of(currentAccountId))) {
            Long categoryId = categoryOfBill.get(presented.billId());
            if (categoryId == null || presented.settledAt() == null) {
                continue;
            }
            YearMonth settledIn = TheMonthAMomentFallsIn.of(presented.settledAt());
            if (settledIn.isBefore(earliest) || settledIn.isAfter(month)) {
                continue;
            }
            read++;
            if (presented.outcome() != BillOutcome.PAID) {
                // Nothing was taken, so nothing is counted. Walked rather than skipped, so that the
                // one rule about what a month cost has its one exception here rather than hidden in
                // a query nobody reads.
                tookNothing++;
                continue;
            }
            committed.computeIfAbsent(categoryId, category -> new HashMap<>())
                    .merge(settledIn, presented.amount(), BigDecimal::add);
        }
        log.debug("what the bills committed currentAccountId={} from={} to={} billsInACategory={} "
                        + "occurrencesInTheWindow={} tookNothing={} categoriesCommittedTo={}",
                currentAccountId, earliest, month, categoryOfBill.size(), read, tookNothing,
                committed.size());
        return committed;
    }

    /**
     * What the customer chose to spend in this month, by category identifier, and how much of it
     * they have not filed anywhere.
     *
     * <p>Two reads and no more, whatever the length of the month: one for the spends inside the
     * window, one for every part of all of them. Asking per spend would be a round trip per
     * purchase, and the page asks this on every open.
     *
     * <p><strong>The month a spend counts in is the month it was recorded in.</strong> There is no
     * date on a spend but the one the application's clock gave it, which is what stops a customer
     * filing a spend into a month they have already read — and, from the next slice, rewriting a
     * carry chain by typing a date.
     *
     * <p>A part with no category is money that left and is filed under nothing. It is summed
     * separately rather than dropped, because dropping it would make the account's total quietly
     * smaller than the spends that produced it, and a budget read that loses euros is the one thing
     * this feature must never do.
     */
    private WhatWasChosen whatWasChosenOver(long currentAccountId, YearMonth earliest,
                                            YearMonth month, Instant from, Instant before) {
        List<Spend> inTheWindow = spends
                .findByCurrentAccountIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAscIdAsc(
                        currentAccountId, from, before);
        if (inTheWindow.isEmpty()) {
            log.debug("what was chosen currentAccountId={} from={} to={} spends=0", currentAccountId,
                    earliest, month);
            return new WhatWasChosen(Map.of(), Map.of());
        }
        Map<Long, YearMonth> monthOfSpend = new HashMap<>();
        for (Spend spend : inTheWindow) {
            monthOfSpend.put(spend.getId(), TheMonthAMomentFallsIn.of(spend.getRecordedAt()));
        }
        Map<Long, Map<YearMonth, BigDecimal>> byCategory = new HashMap<>();
        Map<YearMonth, BigDecimal> uncategorised = new HashMap<>();
        int filed = 0;
        int unfiled = 0;
        for (SpendPart part : parts.findBySpendIdInOrderByIdAsc(
                inTheWindow.stream().map(Spend::getId).toList())) {
            YearMonth recordedIn = monthOfSpend.get(part.getSpendId());
            if (part.getCategoryId() == null) {
                uncategorised.merge(recordedIn, part.getAmount(), BigDecimal::add);
                unfiled++;
                continue;
            }
            byCategory.computeIfAbsent(part.getCategoryId(), category -> new HashMap<>())
                    .merge(recordedIn, part.getAmount(), BigDecimal::add);
            filed++;
        }
        log.debug("what was chosen currentAccountId={} from={} to={} spends={} parts={} "
                        + "unfiledParts={} categoriesSpentOn={} uncategorisedInTheMonth={}",
                currentAccountId, earliest, month, inTheWindow.size(), filed + unfiled, unfiled,
                byCategory.size(),
                AmountOfMoney.asMoney(uncategorised.getOrDefault(month, BigDecimal.ZERO)));
        return new WhatWasChosen(byCategory, uncategorised);
    }

    /**
     * The discretionary half of a month, in one answer: what went to each category and what went
     * nowhere.
     *
     * <p>A record rather than two walks of the same rows, because the two figures come out of one
     * pass and a second pass would be a second chance for them to disagree about which parts they
     * had seen. Private to this class — it is an intermediate, not something that leaves.
     */
    private record WhatWasChosen(Map<Long, Map<YearMonth, BigDecimal>> byCategory,
                                 Map<YearMonth, BigDecimal> uncategorised) {
    }
}
