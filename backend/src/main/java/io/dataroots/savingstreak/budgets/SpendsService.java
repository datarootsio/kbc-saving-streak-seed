package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.budgets.SpendRefused.Kind.AGAINST_THE_RULES;
import static io.dataroots.savingstreak.budgets.SpendRefused.Kind.NOT_ENOUGH_MONEY;
import static io.dataroots.savingstreak.budgets.SpendRefused.Kind.NO_SUCH_SPEND;
import static io.dataroots.savingstreak.budgets.SpendRefused.Kind.THE_PARTS_DO_NOT_ADD_UP;
import static io.dataroots.savingstreak.budgets.SpendRefused.Kind.TOO_MANY_PARTS;
import static io.dataroots.savingstreak.deposits.AmountOfMoney.asMoney;

/**
 * What a customer actually spent: recording one outgoing, taking the money for it, correcting what
 * they said it was for, and reading the recent ones back.
 *
 * <p><strong>The second of this module's three faces.</strong> {@link BudgetsService} owns the
 * declarations — the words a customer describes their money with, and what each of them is allowed
 * to cost — and the reads that answer "what have I actually got?" are a face of their own again.
 * This one owns the movements. They are separate classes rather than three sections of one, for the
 * reason {@code DepositsService}, {@code WithdrawalsService} and {@code MoneyMovementsService} are
 * three: a class that both declares and moves money is a class two different readers open for two
 * different reasons.
 *
 * <p><strong>Named for the spends rather than for the module</strong>, and the alternative was worse
 * rather than merely different. {@code BudgetsService} is where a reader looking for this would
 * first go, and finding the declarations there and the money here is the whole point of the split:
 * what a category is allowed to cost is an intention and can be superseded, and what was spent is a
 * fact that cannot. A second service called after the module would have said nothing about which
 * half a reader had opened.
 *
 * <p><strong>The money is taken through {@code AccountsService.withdrawFrom}</strong> — the same
 * all-or-nothing withdrawal a bill, a deposit and a saving rule use, which refuses rather than going
 * negative. This module introduces no new way of debiting an account and no way of debiting one
 * partially: a spend one cent larger than the balance moves nothing, records nothing, and is refused
 * in words naming both figures. The dependency runs one way, exactly as {@code deposits}' does, and
 * Accounts gains nothing and learns nothing.
 *
 * <p><strong>It never asks whether a current account is real.</strong> The controller vouches for
 * the identifier in the path first, in the words {@code AccountsService.noSuchCurrentAccount} owns,
 * which is why {@link SpendRefused} has no kind for an account that is not there.
 */
@Service
public class SpendsService {

    private static final Logger log = LoggerFactory.getLogger(SpendsService.class);

    /**
     * How many ways one spend may be split.
     *
     * <p>Ten, and a purchase split eleven ways is not a purchase anybody is describing honestly: the
     * split exists so that a supermarket trip that was half food and half wine is recorded as what
     * it was, not so that a receipt can be transcribed line by line. Every fold in this module walks
     * these rows for every spend in a month, and it is also what a page can promise to draw.
     *
     * <p>The eleventh is refused in words rather than dropped, because a split silently shortened is
     * a split that no longer adds up to what left the account.
     */
    private static final int HOW_MANY_PARTS_ONE_SPEND_MAY_HAVE = 10;

    /**
     * How many spends "recent" is.
     *
     * <p>Fifty, which is more than a household records in a month and few enough that the page
     * drawing them stays a page. Bounded at all for the reason the carry fold is bounded: the
     * development clock can be wound years forward, and a list that grew without limit would turn
     * one screen into every spend an account has ever made. What a month cost is a different
     * question, asked of a month and answered by this module's third face, rather than something to
     * be worked out by reading a long enough list.
     */
    private static final int HOW_MANY_RECENT_SPENDS_ARE_READ_BACK = 50;

    private final SpendRepository spends;

    private final SpendPartRepository parts;

    /**
     * Asked whether a category a part names is one money can be filed under, and what this account's
     * categories are called. Both are questions about declarations, so both are answered by the face
     * that owns them rather than by a second copy of the rule here.
     */
    private final BudgetsService budgets;

    /**
     * Where the money actually goes out. Asked to take the amount rather than asked what the balance
     * is and then told to take it: between the question and the instruction the balance can change,
     * and two spends that both asked first would both be told yes.
     */
    private final AccountsService accounts;

    /**
     * The application's clock, which is the only source of the moment a spend counts at. Nothing
     * here calls {@code Instant.now()} — the development clock can be wound years forward, and a
     * spend stamped from the wall clock would sit outside every month this module derives.
     */
    private final Clock clock;

    SpendsService(SpendRepository spends, SpendPartRepository parts, BudgetsService budgets,
                  AccountsService accounts, Clock clock) {
        this.spends = spends;
        this.parts = parts;
        this.budgets = budgets;
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * Records what a customer spent, takes the money for it, and answers with the spend as it now
     * reads.
     *
     * <p><strong>One transaction, so a spend is one event rather than two.</strong> Money that left
     * a current account with no record of what it was for is the worst balance in this application
     * to be asked to explain, and it cannot happen here: if the split cannot be written, the
     * withdrawal is rolled back with it.
     *
     * <p>The objections are asked in the order somebody fixes them in, and it is deliberate: what
     * was typed first, then the words it names, then the money. A name, an amount, how many parts,
     * whether each part is an amount, and whether they add up are all things the customer can see on
     * their own screen; which categories exist is something they have to go and look at; and whether
     * the account holds it is the only one that is about the world rather than about the request. A
     * short balance is therefore the last thing said, so that somebody with a typo <em>and</em> an
     * empty account is told about the typo rather than sent to find money for a spend that was never
     * going to be accepted.
     *
     * <p>Nothing is written until every objection has been heard, so a refused spend leaves the
     * balance exactly where the customer left it and the account with no row to explain.
     *
     * @throws SpendRefused            if that is not a spend this application will record, or the
     *                                 account does not hold it
     * @throws SpendingCategoryRefused if a part names a category that is not on this account, or one
     *                                 that has been ended
     */
    @Transactional
    public ARecordedSpend recordASpend(long currentAccountId, ASpendAsAsked asked) {
        log.debug("spend asked for currentAccountId={} name={} amount={} parts={}", currentAccountId,
                asked.name(), asked.amount(), asked.parts() == null ? null : asked.parts().size());
        String itsName = judgedAsAName(currentAccountId, asked.name());
        BigDecimal amount = judgedAsWhatLeftTheAccount(currentAccountId, asked.amount());
        List<APartAsAsked> split = judgedAsASplit(currentAccountId, asked.parts());
        refuseUnlessThePartsAddUpToIt(currentAccountId, amount, split);
        List<ASpendPart> filedUnder = filedUnderCategoriesOfThisAccount(currentAccountId, split);
        takeTheMoneyOrRefuse(currentAccountId, itsName, amount);

        Instant clockReads = clock.instant();
        Instant now = clockReads.truncatedTo(ChronoUnit.MILLIS);
        // Both readings, the way a deposit says it, so that whoever reads this log can see that the
        // moment came off the application's clock and what the truncation did to it rather than
        // taking the recorded moment on trust. It is also the whole evidence that a spend cannot be
        // backdated: there is no other moment in this method for the day to have come from.
        log.debug("spend takes its moment from the application clock currentAccountId={} "
                + "clockReads={} recordedMoment={}", currentAccountId, clockReads, now);

        Spend spend = spends.save(new Spend(currentAccountId, itsName, amount, now));
        parts.saveAll(split.stream()
                .map(part -> new SpendPart(spend.getId(), part.categoryId(), part.amount()))
                .toList());
        BigDecimal left = whatTheAccountHolds(currentAccountId);
        // One line per spend recorded, with everything that decided it: which account it came out
        // of, what it was called, what it cost, how many ways it was split and what the account
        // holds now. A reviewer can check the balance in the next line of the log against this one
        // rather than against a row nobody can see.
        log.info("spend recorded spendId={} currentAccountId={} name={} amount={} parts={} "
                        + "uncategorisedParts={} balance={} recordedAt={}",
                spend.getId(), currentAccountId, spend.getName(), asMoney(spend.getAmount()),
                filedUnder.size(), howManyAreUnfiled(filedUnder), asMoney(left),
                spend.getRecordedAt());
        return asRecorded(spend, filedUnder);
    }

    /**
     * Replaces the whole split of a spend already recorded, marks the spend as corrected, and
     * answers with it as it now reads.
     *
     * <p><strong>The split is the only thing about a spend that was ever an opinion.</strong> The
     * amount stands, the name stands and the spend cannot be deleted — the money moved, and a record
     * that can be unmade is not a record. So there is one correction method, it takes one thing, and
     * there is deliberately nothing beside it: a mistyped amount is a mistake a customer lives with,
     * and a category got wrong is a mistake that poisons every figure derived from it.
     *
     * <p><strong>Wholesale rather than part by part.</strong> A correction is the customer saying
     * what the spend was for, which is a statement about the whole of it: the parts have to sum to
     * the amount, so a request that moved one part would leave this module to decide which other
     * part lost the difference. The old rows are replaced rather than superseded, for the same
     * reason a bill's category is one row — the split is a label on a payment and not a history of
     * opinions, and a customer correcting a mistake wants it corrected rather than annotated.
     *
     * <p><strong>The new split is held to exactly the rules the old one was held to</strong>, in the
     * same methods and therefore in the same sentences: at least one part, at most ten, each an
     * amount of money, summing to the cent, and every named category one this account may file money
     * under. Anything looser would let a customer reach by correcting a split the application would
     * have refused to record.
     *
     * <p><strong>It moves no money and never asks for any.</strong> The euros left the account when
     * the spend was recorded; this re-files euros that are already gone, so the balance is exactly
     * where the customer left it — which is why {@code AccountsService} is not called here and why a
     * short balance is not a refusal this method has.
     *
     * <p><strong>What it rewrites, it rewrites everywhere, and that is the point.</strong> Every
     * figure this module reports is derived on every read, so moving a spend's euros out of one
     * category and into another moves the month they were spent in, the carry out of it and every
     * month after it. A customer who discovers in June that March's car repair went under Groceries
     * wants March fixed. The one thing that does not move is a notification already raised about
     * that month: an inbox is a record of what was said on the night it was said, the budget is what
     * is true now, and both of those are right.
     *
     * <p>Nothing is written until every objection has been heard, so a refused correction leaves the
     * spend reading exactly as its holder left it — the old split intact and the moment untouched. A
     * correction that emptied the split and then refused the new parts would leave a spend whose
     * euros were filed under nothing, which is the worst record in this feature.
     *
     * @throws SpendRefused            if there is no such spend on that account, or the new split is
     *                                 not one this application will keep
     * @throws SpendingCategoryRefused if a part names a category that is not on this account, or one
     *                                 that has been ended
     */
    @Transactional
    public ARecordedSpend correctTheSplitOf(long currentAccountId, long spendId,
                                            List<APartAsAsked> asked) {
        log.debug("spend split correction asked for currentAccountId={} spendId={} parts={}",
                currentAccountId, spendId, asked == null ? null : asked.size());
        Spend spend = theSpendOn(currentAccountId, spendId);
        List<APartAsAsked> split = judgedAsASplit(currentAccountId, asked);
        refuseUnlessThePartsAddUpToIt(currentAccountId, spend.getAmount(), split);
        List<ASpendPart> filedUnder = filedUnderCategoriesOfThisAccount(currentAccountId, split);

        // Read before anything is written, because it is half of what the one line below says: a
        // reviewer asking "who moved March's car repair out of Groceries" needs where it came from
        // as much as where it went.
        List<SpendPart> before = parts.findBySpendIdOrderByIdAsc(spendId);
        String wasSplit = theSplitAsWritten(before);
        parts.deleteAll(before);
        parts.saveAll(split.stream()
                .map(part -> new SpendPart(spendId, part.categoryId(), part.amount()))
                .toList());

        Instant clockReads = clock.instant();
        Instant now = clockReads.truncatedTo(ChronoUnit.MILLIS);
        boolean again = spend.getCorrectedAt() != null;
        spend.wasCorrectedAt(now);
        spends.save(spend);
        // One line per correction, with the split before it and the split after it, because what
        // changed is the whole of what happened here — the amount, the name and the balance are all
        // exactly as they were, and a line naming only the new split would say nothing a reader
        // could check. Every other figure this module reports moves with these euros.
        log.info("spend split corrected spendId={} currentAccountId={} name={} amount={} "
                        + "wasSplit={} nowSplit={} parts={} uncategorisedParts={} correctedAt={} "
                        + "correctedBefore={}",
                spendId, currentAccountId, spend.getName(), asMoney(spend.getAmount()), wasSplit,
                theSplitAsRead(filedUnder), filedUnder.size(), howManyAreUnfiled(filedUnder), now,
                again);
        return asRecorded(spend, filedUnder);
    }

    /**
     * The recent spends on one account, newest first, each carrying the split it was recorded with.
     *
     * <p>"What have I spent lately", which is the question a customer asks of this list, so the most
     * recent is the first thing on it. Bounded at
     * {@value #HOW_MANY_RECENT_SPENDS_ARE_READ_BACK}, because the list is a page rather than a
     * ledger; the money movements ledger is where every movement of a customer's money is put side
     * by side, and what a month cost is a figure rather than a list to be added up by hand.
     *
     * <p>The splits are read in one further query and matched up here, rather than one query per
     * spend: fifty spends with a split each is fifty round trips to answer one question, and the
     * page that draws them asks it on every open.
     */
    @Transactional(readOnly = true)
    public List<ARecordedSpend> recentSpendsOn(long currentAccountId) {
        List<Spend> recent = spends.findByCurrentAccountIdOrderByRecordedAtDescIdDesc(
                currentAccountId, Limit.of(HOW_MANY_RECENT_SPENDS_ARE_READ_BACK));
        // Gathered once, for the whole list, and then matched up: asking per spend would be the
        // round trip each this method exists to avoid.
        Map<Long, List<ASpendPart>> splits = theSplitsOf(List.of(currentAccountId), recent);
        List<ARecordedSpend> read = recent.stream()
                .map(spend -> asRecorded(spend, splits.getOrDefault(spend.getId(), List.of())))
                .toList();
        log.debug("spends read currentAccountId={} spends={} mostRecentAt={}", currentAccountId,
                read.size(), read.isEmpty() ? null : read.get(0).recordedAt());
        return read;
    }

    /**
     * Every spend recorded on any of these accounts, newest first, each carrying the split it is
     * filed under and the moment it was corrected at if it ever was.
     *
     * <p><strong>The ledger's question rather than the page's, and that is why it is not
     * {@link #recentSpendsOn}.</strong> That one answers "what have I spent lately" about one
     * account and is bounded, because it is a screen. This one answers "where did my money go"
     * about everything a customer holds, and is merged in the web layer with the deposits, the
     * withdrawals and the bills into one list — so it is across accounts, and it is unbounded for
     * the reason {@link SpendRepository} gives: the three kinds merged beside it are unbounded too,
     * and a spend that vanished out from under the rent it was recorded next to would be a worse
     * answer than a long list.
     *
     * <p>Asked for by account rather than by customer, like every other half of that ledger. Who
     * holds what is Accounts' answer and reaches this module already decided; a module working it
     * out for itself would be a second answer to a question it does not own, and a spend on an
     * account the customer does not hold could then reach their page.
     *
     * <p>No accounts at all is an empty list rather than a query. A customer who holds no everyday
     * account has spent nothing out of one, and {@code in ()} is not a thing to ask a database —
     * the same answer {@code MoneyMovementsService} gives a customer with no savings account.
     */
    @Transactional(readOnly = true)
    public List<ARecordedSpend> spendsRecordedOn(Collection<Long> currentAccountIds) {
        if (currentAccountIds.isEmpty()) {
            log.debug("spends on the ledger asked for across no current accounts, so there are none");
            return List.of();
        }
        List<Spend> recorded =
                spends.findByCurrentAccountIdInOrderByRecordedAtDescIdDesc(currentAccountIds);
        Map<Long, List<ASpendPart>> splits = theSplitsOf(currentAccountIds, recorded);
        List<ARecordedSpend> ledger = recorded.stream()
                .map(spend -> asRecorded(spend, splits.getOrDefault(spend.getId(), List.of())))
                .toList();
        // How many spends went into the ledger and how many of them have been corrected since, so
        // that a history page somebody says is missing a supermarket trip can be checked against
        // what this module actually handed over. Counts and two moments rather than a line per row:
        // this runs on every read of the history page.
        log.debug("spends on the ledger read currentAccounts={} spends={} corrected={} "
                        + "newestRecordedAt={} oldestRecordedAt={}",
                currentAccountIds.size(), ledger.size(),
                ledger.stream().filter(spend -> spend.correctedAt() != null).count(),
                ledger.isEmpty() ? null : ledger.get(0).recordedAt(),
                ledger.isEmpty() ? null : ledger.get(ledger.size() - 1).recordedAt());
        return ledger;
    }

    /**
     * Every recent spend's split, by the spend it belongs to, with each part's category named.
     *
     * <p>Two reads and no more, whatever the length of the list: one for the parts of every spend on
     * it, one for what the categories of the accounts it came from are called. The names come from
     * those accounts' whole lists — the ended ones included — because ending a category leaves every
     * euro filed under it exactly where it is, and a lookup against the standing ones would draw a
     * real record as an unknown one.
     *
     * <p>Several accounts rather than one, because the ledger merges a customer's whole holding and
     * the recent-spends page is one account asked the same question. Two methods that each gathered
     * names their own way would be two chances to disagree about what an ended category is called.
     */
    private Map<Long, List<ASpendPart>> theSplitsOf(Collection<Long> currentAccountIds,
                                                    List<Spend> recent) {
        if (recent.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> called = budgets.whatTheCategoriesAreCalledAcross(currentAccountIds);
        Map<Long, List<ASpendPart>> bySpend = new HashMap<>();
        for (SpendPart part : parts.findBySpendIdInOrderByIdAsc(
                recent.stream().map(Spend::getId).toList())) {
            bySpend.computeIfAbsent(part.getSpendId(), spendId -> new ArrayList<>())
                    .add(new ASpendPart(part.getCategoryId(),
                            part.getCategoryId() == null ? null : called.get(part.getCategoryId()),
                            AmountOfMoney.quotedToTheCent(part.getAmount())));
        }
        return bySpend;
    }

    /**
     * Moves the money out of the current account, or refuses the spend because it is not there.
     *
     * <p>All-or-nothing, and not this module's own arithmetic: {@code withdrawFrom} is the one way
     * an account is debited in this application and it already refuses rather than going negative.
     * A spend one cent larger than the balance therefore takes nothing and leaves the balance
     * exactly where it was — there is no partial spend, no overdraft, and nothing to reverse,
     * because nothing happened.
     *
     * <p>The balance is read only once the answer is no. Nothing was taken, so it is still what the
     * account holds, and it is the figure the person needs in order to see how much of the spend
     * they could actually have made. Both figures are on the refusal and both are in the WARN line,
     * because a reviewer tracing "it would not take my spend" needs the gap rather than the verdict.
     */
    private void takeTheMoneyOrRefuse(long currentAccountId, String name, BigDecimal amount) {
        if (accounts.withdrawFrom(currentAccountId, amount)) {
            return;
        }
        BigDecimal left = whatTheAccountHolds(currentAccountId);
        String reason = "There is not enough in that current account to spend EUR "
                + asMoney(amount) + " on \"" + name + "\". It holds EUR " + asMoney(left) + ".";
        log.warn("spend rejected currentAccountId={} name={} amount={} balance={} kind={} reason={}",
                currentAccountId, name, asMoney(amount), asMoney(left), NOT_ENOUGH_MONEY, reason);
        throw new SpendRefused(NOT_ENOUGH_MONEY, reason);
    }

    /**
     * That every part names a category this account may file money under, and what each of them is
     * called.
     *
     * <p>Asked of {@link BudgetsService}, which owns both the rule and the sentences: a category
     * that is not on this account and one that has been ended are its refusals, raised in the words
     * it already uses everywhere else a category is named. A part naming nothing at all is
     * uncategorised and is asked about nothing — that is a state and not a gap.
     *
     * <p>Every part is asked before the money moves, so a split with a wrong category in it leaves
     * the balance alone. The names gathered here are what the answer carries back, so that the page
     * which has just recorded a spend can draw its split without fetching the categories again.
     */
    private List<ASpendPart> filedUnderCategoriesOfThisAccount(long currentAccountId,
                                                               List<APartAsAsked> split) {
        List<ASpendPart> filedUnder = new ArrayList<>();
        for (APartAsAsked part : split) {
            String name = part.categoryId() == null ? null
                    : budgets.aCategoryMoneyCanBeFiledUnder(currentAccountId, part.categoryId())
                            .name();
            filedUnder.add(new ASpendPart(part.categoryId(), name,
                    AmountOfMoney.quotedToTheCent(part.amount())));
        }
        log.debug("spend split checked against the account's categories currentAccountId={} "
                        + "parts={} uncategorisedParts={}", currentAccountId, filedUnder.size(),
                howManyAreUnfiled(filedUnder));
        return filedUnder;
    }

    /**
     * That the parts sum to exactly what was spent.
     *
     * <p>The invariant the whole feature rests on, and it has no exceptions. A split a cent over or
     * a cent under would leave euros unaccounted for in a figure the customer is about to decide how
     * much to save from, and an application that rounded the difference away would be deciding on
     * their behalf which category lost it. Both totals are named back, because the gap is what they
     * have to go and find.
     *
     * <p>Compared with {@code compareTo} rather than {@code equals}, because a figure typed as 50
     * and a figure typed as 50.00 are the same amount of money and differ only in how they were
     * written.
     */
    private void refuseUnlessThePartsAddUpToIt(long currentAccountId, BigDecimal amount,
                                               List<APartAsAsked> split) {
        BigDecimal together = split.stream().map(APartAsAsked::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        log.debug("spend split weighed against what was spent currentAccountId={} amount={} "
                + "parts={} partsTotal={}", currentAccountId, asMoney(amount), split.size(),
                asMoney(together));
        if (together.compareTo(amount) == 0) {
            return;
        }
        throw refusingTheSpend(currentAccountId, THE_PARTS_DO_NOT_ADD_UP,
                "The parts of a spend have to add up to what was spent. These come to EUR "
                        + asMoney(together) + " and the spend was EUR " + asMoney(amount) + ".");
    }

    /**
     * Whether what arrived is a split this application will keep — and the parts themselves if it
     * is.
     *
     * <p>Three objections in one pass, in the order they stop mattering: a spend with no parts at
     * all is not split, a spend split more ways than this application keeps is refused for being
     * too many rather than for anything about the parts themselves, and only then is each part asked
     * whether it is an amount of money. What an amount of money is, is {@code AmountOfMoney}'s
     * answer, so a part quoted more finely than to the cent is refused here in the same words a
     * deposit of it would be.
     *
     * <p>A part worth nothing is refused rather than dropped, and a negative one with it. Allowing
     * either would let a split add up to exactly the right total out of figures that were never
     * spent, which would pass the check below and record a lie.
     */
    private List<APartAsAsked> judgedAsASplit(long currentAccountId, List<APartAsAsked> parts) {
        if (parts == null || parts.isEmpty()) {
            throw refusingTheSpend(currentAccountId, AGAINST_THE_RULES,
                    "Say what the spend was for. One part is enough, and a part does not have to "
                            + "name a category — money you have not filed yet is still money that "
                            + "left the account.");
        }
        if (parts.size() > HOW_MANY_PARTS_ONE_SPEND_MAY_HAVE) {
            throw refusingTheSpend(currentAccountId, TOO_MANY_PARTS,
                    "A spend can be split " + HOW_MANY_PARTS_ONE_SPEND_MAY_HAVE + " ways at most, "
                            + "and this one is split " + parts.size() + ". Put what belongs "
                            + "together in one part.");
        }
        for (APartAsAsked part : parts) {
            if (part == null || part.amount() == null) {
                throw refusingTheSpend(currentAccountId, AGAINST_THE_RULES,
                        "Say what each part of the spend was worth.");
            }
            AmountOfMoney.whyItIsNotOne("part of a spend", part.amount())
                    .ifPresent(reason -> {
                        throw refusingTheSpend(currentAccountId, AGAINST_THE_RULES, reason);
                    });
        }
        return parts;
    }

    /**
     * Whether what arrived is an amount of money to take out of the account — and the amount if it
     * is.
     *
     * <p>{@code AmountOfMoney} owns both halves of the rule, so a spend of nothing and a spend of
     * 12.505 are refused here in the same sentences a deposit or a withdrawal of them would be. An
     * amount that was never sent is its own objection, because "a spend must be worth recording" is
     * not a helpful thing to say to somebody who left the box empty.
     */
    private BigDecimal judgedAsWhatLeftTheAccount(long currentAccountId, BigDecimal amount) {
        if (amount == null) {
            throw refusingTheSpend(currentAccountId, AGAINST_THE_RULES,
                    "Say what the spend cost, in digits with a full stop, like 42.50.");
        }
        AmountOfMoney.whyItIsNotOne("spend", amount).ifPresent(reason -> {
            throw refusingTheSpend(currentAccountId, AGAINST_THE_RULES, reason);
        });
        return amount;
    }

    /**
     * Whether what arrived is a name this application will keep — and the name itself, trimmed, if
     * it is.
     *
     * <p>It answers with the value to write rather than merely saying yes, for the reason
     * {@code BudgetsService.judgedAsAName} does: judging and tidying are the same pass, and the
     * trimmed name is what gets written.
     *
     * <p>A spend needs a name for the reason a category does. A list of spends should read like
     * somebody's week rather than like a bank statement, and nothing else in the record can supply
     * one: an amount and a moment describe a movement, not a purchase.
     */
    private String judgedAsAName(long currentAccountId, String name) {
        String itsName = name == null ? "" : name.trim();
        if (itsName.isBlank()) {
            throw refusingTheSpend(currentAccountId, AGAINST_THE_RULES,
                    "Give the spend a name you will recognise later — \"Delhaize\", \"Round of "
                            + "drinks\", \"New tyres\".");
        }
        return itsName;
    }

    /**
     * What the account holds, for a refusal and for the log.
     *
     * <p>The account was there a moment ago, when the controller vouched for it. Being asked to
     * report a balance for an account that has since gone is not something this module can word
     * helpfully, so it says the part it is sure of — the same bargain a refused deposit strikes.
     */
    private BigDecimal whatTheAccountHolds(long currentAccountId) {
        return accounts.balanceOfCurrentAccount(currentAccountId).orElse(BigDecimal.ZERO);
    }

    /**
     * That spend on that account, or a refusal that it is not there.
     *
     * <p>Scoped by the account in the path, so a spend identifier somebody guessed answers as a
     * spend that is not there rather than with somebody else's groceries — and a spend on another
     * account is refused in exactly the sentence one that was never recorded is refused in. This
     * application has no authentication to ask whose an account is, so telling the two apart would
     * be telling a guesser that the spend exists. The same answer a category and a bill give, for
     * the same reason.
     */
    private Spend theSpendOn(long currentAccountId, long spendId) {
        return spends.findByIdAndCurrentAccountId(spendId, currentAccountId)
                .orElseThrow(() -> refusingTheSpend(currentAccountId, NO_SUCH_SPEND,
                        "There is no spend " + spendId + " on current account " + currentAccountId
                                + "."));
    }

    /** How much of a split its holder has not decided about yet, for the log and for nothing else. */
    private static long howManyAreUnfiled(List<ASpendPart> filedUnder) {
        return filedUnder.stream().filter(part -> part.categoryId() == null).count();
    }

    /**
     * A split as one value for the log: each part's category and what it is worth.
     *
     * <p>One token rather than several lines, because the two that matter are read against each
     * other — a correction's line carries the split before and the split after it, and a reviewer
     * checking that March's car repair moved out of Groceries is comparing two strings on one line.
     * Identifiers rather than names, because a name can be changed afterwards and an identifier is
     * what every other line in this module is greppable by.
     *
     * <p>An unfiled part says so in a word instead of leaving a gap, for the reason the read model
     * does: nothing here is a missing value, it is a customer who has not decided yet.
     */
    private static String theSplitAsWritten(List<SpendPart> split) {
        return split.stream()
                .map(part -> filedUnder(part.getCategoryId()) + ":" + asMoney(part.getAmount()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** The same, for a split that has been read back into this module's own shape. */
    private static String theSplitAsRead(List<ASpendPart> split) {
        return split.stream()
                .map(part -> filedUnder(part.categoryId()) + ":" + asMoney(part.amount()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    /** Which category one part sits in, or the word for the state of sitting in none. */
    private static String filedUnder(Long categoryId) {
        return categoryId == null ? "unfiled" : String.valueOf(categoryId);
    }

    /**
     * Every refusal of a spend says why in the log as well as to whoever asked, because only one of
     * the two is kept: the reason reaches the person at the keyboard and nowhere else. The account
     * is on the line because a reviewer tracing "it would not take my spend" needs to know whose
     * balance was being argued about.
     *
     * <p>The refusal for a short balance is not raised through this one. It carries two figures a
     * reviewer needs beside the reason, and a signature that took them for every other refusal would
     * be a signature with two nulls in it at every other call site.
     */
    private SpendRefused refusingTheSpend(long currentAccountId, SpendRefused.Kind kind,
                                          String reason) {
        log.warn("spend rejected currentAccountId={} kind={} reason={}", currentAccountId, kind,
                reason);
        return new SpendRefused(kind, reason);
    }

    /** The row as the rest of the application reads it, quoted to the cent as it leaves. */
    private static ARecordedSpend asRecorded(Spend spend, List<ASpendPart> filedUnder) {
        return new ARecordedSpend(spend.getId(), spend.getCurrentAccountId(), spend.getName(),
                AmountOfMoney.quotedToTheCent(spend.getAmount()), spend.getRecordedAt(),
                spend.getCorrectedAt(), filedUnder);
    }
}
