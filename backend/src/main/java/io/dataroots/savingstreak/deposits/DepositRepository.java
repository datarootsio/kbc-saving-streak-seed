package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private: the rest of the application goes through {@link DepositsService}.
 *
 * <p><strong>Half of these queries ask for the customer's own rows and half ask for every row, and
 * which of the two a query wants is the load-bearing decision in this interface.</strong> Since a
 * month's interest is written here as a row of another {@link DepositOrigin}, every query over this
 * table had to be asked one question: is it about money that is <em>in</em> the account, or about
 * money the customer <em>put</em> in it. A balance, a draw-down and the ledger of what moved are
 * the first kind and take everything. The mark a deposit is judged against, the week, the
 * anniversaries and the histories a customer reads are the second kind and say
 * {@code origin = CUSTOMER} out loud, each with its own reason written beside it — because a
 * paragraph saying "interest is not that" three times over is the only thing standing between this
 * application and paying somebody points for money the bank added.
 */
interface DepositRepository extends JpaRepository<Deposit, Long> {

    /**
     * The origin a query means when it says the customer's own money, spelled out in full because
     * that is what the query language wants and written once because three queries want it.
     *
     * <p>A compile-time constant rather than a parameter, so that the condition reads as part of
     * the rule in the query it is in rather than as an argument a caller could hand in wrong. The
     * rows the bank wrote are the ones it leaves out, and every query carrying it says which of the
     * three rules that is.
     */
    String THE_CUSTOMERS_OWN_MONEY =
            "io.dataroots.savingstreak.deposits.DepositOrigin.CUSTOMER";

    /**
     * The one origin that is not the customer's money at all, for the queries whose rule is "not
     * what the bank added" rather than "paid in this week".
     *
     * <p><strong>The two are different rules and used to be the same query, which is what a third
     * origin exposed.</strong> A row that arrived by a move between two of the customer's own
     * savings accounts is their money in every sense the mark, the balance and an anniversary care
     * about — it is euros they saved, sitting in a savings account, ageing towards a bonus — and it
     * is emphatically <em>not</em> new saving for the week, because those euros were already saved
     * in the account next door. Asked as {@code origin = CUSTOMER}, every query got the second
     * answer; asked as {@code origin &lt;&gt; INTEREST}, the queries that mean the first get it, and
     * the two week queries go on saying {@code CUSTOMER} because for them the old reading was the
     * right one all along.
     *
     * <p>A row whose origin says nothing is left out by both readings, which is the same window the
     * paragraph above describes: {@link DepositsOnStartUp} names every such row before the
     * application serves a single request.
     */
    String MONEY_THE_BANK_ADDED =
            "io.dataroots.savingstreak.deposits.DepositOrigin.INTEREST";

    /**
     * Every row in one savings account, whoever put the money there.
     *
     * <p>All of them, including the interest the bank paid in: this is what the account's money
     * balance is summed from, and interest is money in the account. That is the whole reason it is
     * a row of this table.
     */
    List<Deposit> findBySavingsAccountId(long savingsAccountId);

    /**
     * The deposits a customer made into one savings account, newest first, and the identifier
     * settles it when two share a moment: a moment is only kept to the millisecond, and two
     * deposits can land inside one.
     *
     * <p>Theirs alone, because this is the history a customer reads back over what they paid in and
     * what each payment earned. A month's interest earned no points, was paid at no rate and
     * carries no anniversary, so it would be a row of nulls in a list whose every column is about
     * how a deposit was priced. What the bank paid is read as what it is — an interest posting,
     * with the period and the rate behind it — and the ledger of money that moved is where the two
     * kinds appear side by side.
     */
    @Query("select deposit from Deposit deposit "
            + "where deposit.savingsAccountId = :savingsAccountId "
            + "and deposit.origin = " + THE_CUSTOMERS_OWN_MONEY + " "
            + "order by deposit.depositedAt desc, deposit.id desc")
    List<Deposit> madeIntoNewestFirst(@Param("savingsAccountId") long savingsAccountId);

    /**
     * The deposits a customer made into one savings account, oldest first, with the identifier
     * settling ties at the millisecond the application records.
     *
     * <p>Theirs alone, for the reason the listing above gives: the callers are reading what was
     * paid in and by whom. {@link #whatItHoldsInTheOrderAWithdrawalTakesIt} is the query for
     * everything the account holds, in the order money actually leaves it.
     */
    @Query("select deposit from Deposit deposit "
            + "where deposit.savingsAccountId = :savingsAccountId "
            + "and deposit.origin = " + THE_CUSTOMERS_OWN_MONEY + " "
            + "order by deposit.depositedAt asc, deposit.id asc")
    List<Deposit> madeIntoOldestFirst(@Param("savingsAccountId") long savingsAccountId);

    /**
     * Everything the account holds, in the order a withdrawal takes it: the interest the bank paid
     * first, and then the customer's own deposits oldest first.
     *
     * <p><strong>Interest before deposits, because it is the cheapest money the customer has.</strong>
     * A euro of interest carries no loyalty clock and has earned no points, so spending it costs
     * nothing at all; a euro of a deposit is counting towards an anniversary that pays a tenth of
     * what is still in it. Taking the interest first is the bank spending what it gave before it
     * spends what the customer saved, which is the reading that costs them least — and it is a rule
     * about which euros leave rather than a favour, so it lives in the order the rows come back in.
     *
     * <p><strong>The existing promise is untouched.</strong> "A withdrawal takes the oldest deposit
     * first" was written about deposits, and among the deposits this still hands them over oldest
     * first; what is new is a kind of row that did not exist when that sentence was written, and it
     * goes in front of all of them rather than being slotted in by its date. An interest payment
     * dated at the end of the month it pays for would otherwise sit in the middle of the
     * chronology, and a withdrawal would spend half the customer's oldest deposits before reaching
     * it.
     *
     * <p>Ordered by the database rather than sorted afterwards in Java, like every other listing
     * here, so there is one statement of the order and it is the one the rows arrive in. The
     * {@code case} is the whole of the new rule; the two columns after it are the promise that was
     * already there.
     *
     * <p><strong>The {@code case} asks which rows the bank wrote rather than which rows the
     * customer paid in</strong>, and the difference is a row that arrived by a move. Such a row is
     * the customer's own saved euros with a loyalty clock running on them, so it belongs among the
     * deposits and in date order among them — not in front of all of them beside the interest,
     * which is where a test for {@code origin = CUSTOMER} would have quietly put it. Asked this way
     * the rule reads as what it is: the cheapest money first, and the cheapest money is the money
     * nobody saved.
     */
    @Query("select deposit from Deposit deposit "
            + "where deposit.savingsAccountId = :savingsAccountId "
            + "order by case when deposit.origin = " + MONEY_THE_BANK_ADDED + " then 0 else 1 end asc, "
            + "deposit.depositedAt asc, deposit.id asc")
    List<Deposit> whatItHoldsInTheOrderAWithdrawalTakesIt(
            @Param("savingsAccountId") long savingsAccountId);

    /**
     * Every deposit into any of a set of savings accounts, newest first.
     *
     * <p>Several accounts at once because the ledger it feeds is a customer's rather than an
     * account's: somebody saving towards two goals moved their money once, and reading it back as
     * two histories to interleave by eye is not an answer.
     *
     * <p>Ordered here rather than in Java, and by the identifier as well as the moment, the way this
     * module's other listings are: a moment is only kept to the millisecond and two deposits can
     * land inside one.
     */
    @Query("select deposit from Deposit deposit "
            + "where deposit.savingsAccountId in :savingsAccountIds "
            + "order by deposit.depositedAt desc, deposit.id desc")
    List<Deposit> intoAnyOfNewestFirst(@Param("savingsAccountIds") Collection<Long> savingsAccountIds);

    /**
     * The deposits that landed in a stretch of time, oldest first, with the identifier settling ties
     * at the millisecond the application records.
     *
     * <p>Half-open — from the first moment inclusive, to the last exclusive — so that two adjacent
     * stretches agree about which of them owns the moment between them. A deposit on the stroke of
     * Monday belongs to the week beginning and not to both, and a closed interval on either side
     * would count it twice or not at all depending on which end was asked first.
     *
     * <p>By customer, because the stretch of time this answers about is a week of somebody's saving:
     * a week counts what they paid in, whichever of their savings accounts it went into. Which
     * account holds the money is a different question and {@link #findBySavingsAccountId} answers it.
     *
     * <p><strong>Their own money, because a secured week measures what the customer moved.</strong>
     * A month's interest landing in an account is not somebody saving that week, and counting it
     * would let a run of weeks be kept going by money nobody put away — the week would secure
     * itself out of the balance, for ever, on any account large enough. The same reading applies to
     * {@link #landedBefore} beneath it, which is the other half of the same walk.
     *
     * <p><strong>And not a euro that arrived by a move, which is the other half of that same
     * rule.</strong> Money moved from one of the customer's savings accounts to another was saved
     * when it first went in and was counted into the week it went in; counted again on arrival it
     * would secure a week out of euros that were already in savings, and the withdrawal on the
     * other side of the move would net that same week to nothing. So a move is left out at both
     * ends, and this query leaves out its arriving half by asking for the one origin that means
     * "paid in from an everyday account" rather than for the broader one beside it.
     *
     * <p>Written out rather than derived from the method name: the name that says this reads
     * {@code findByCustomerIdAndDepositedAtGreaterThanEqualAndDepositedAtLessThanOrderBy...}, which
     * is a sentence nobody can check against the query it stands for.
     */
    @Query("select deposit from Deposit deposit "
            + "where deposit.customerId = :customerId "
            + "and deposit.origin = " + THE_CUSTOMERS_OWN_MONEY + " "
            + "and deposit.depositedAt >= :from and deposit.depositedAt < :until "
            + "order by deposit.depositedAt asc, deposit.id asc")
    List<Deposit> landedBetween(@Param("customerId") long customerId,
                                @Param("from") Instant from,
                                @Param("until") Instant until);

    /**
     * Everything that landed before a moment, oldest first, with the identifier settling ties at the
     * millisecond the application records.
     *
     * <p>Exclusive of the moment itself, for the reason {@link #landedBetween} gives: a caller
     * splitting time at that moment gets each deposit on exactly one side of it. The two queries
     * agree about the boundary, so a caller can ask this for everything before a week and that for
     * the week itself and count nothing twice.
     *
     * <p>No lower bound, because there is nothing below the first deposit the customer ever made. A
     * caller walking the whole of somebody's saving back through the weeks wants exactly this.
     */
    @Query("select deposit from Deposit deposit "
            + "where deposit.customerId = :customerId "
            + "and deposit.origin = " + THE_CUSTOMERS_OWN_MONEY + " "
            + "and deposit.depositedAt < :until "
            + "order by deposit.depositedAt asc, deposit.id asc")
    List<Deposit> landedBefore(@Param("customerId") long customerId,
                               @Param("until") Instant until);

    /**
     * Every deposit that still has money in it and landed before a moment, oldest first, with the
     * identifier settling ties at the millisecond the application records.
     *
     * <p>Everybody's at once, because the sweep that asks for these is one pass over the deposits
     * rather than a pass per customer.
     *
     * <p>Deposits holding nothing are outside the query rather than filtered out of the answer.
     * Money cannot come back into a deposit — a new payment in is a new deposit with a clock of its
     * own — so a deposit drawn down to zero can never be worth anything again, and reading it back
     * every night in order to decide that afresh would be reading a row to say nothing about it.
     *
     * <p>Landed before a moment rather than every deposit ever made, because the caller's rule
     * turns on age and a deposit made this morning cannot yet be old enough for it. Exclusive of
     * the moment, for the reason {@link #landedBetween} gives.
     *
     * <p>Oldest first because that is the order the money arrived in, and because it makes the
     * caller's line-per-deposit read as a chronology rather than as whatever order the database
     * felt like.
     *
     * <p><strong>A row that arrived by a move is in this answer, and that is the price of
     * moving.</strong> Its euros are the customer's own, saved, sitting in a savings account and
     * ageing — everything an anniversary is paid for — so leaving it out would be this application
     * quietly withholding a bonus from somebody for having taken the bank's better offer. What the
     * move does cost is that the clock starts again: the row landed on the day of the move, so the
     * anniversary it is counting towards is a year from then and the one its euros were part-way
     * through is gone. That is the honest cost, it is stated before the move is confirmed, and it
     * is charged here by the row simply being new.
     *
     * <p><strong>The customer's own deposits, because interest pays no anniversary.</strong> An
     * anniversary pays for having left money alone; interest is the reward for having done so, not
     * a second thing to be rewarded for. A row of interest left in this answer would start a clock
     * of its own and pay a tenth of itself every year, which is the bank paying points on the
     * points it has already paid. The same reading applies to {@link #stillHoldingMoneyIn} beneath
     * it, which answers the same question for one account.
     */
    @Query("select deposit from Deposit deposit "
            + "where deposit.remainingAmount > 0 and deposit.depositedAt < :until "
            + "and deposit.origin <> " + MONEY_THE_BANK_ADDED + " "
            + "order by deposit.depositedAt asc, deposit.id asc")
    List<Deposit> stillHoldingMoneyThatLandedBefore(@Param("until") Instant until);

    /**
     * The deposits into one savings account that still have money in them, oldest first, with the
     * identifier settling ties at the millisecond the application records.
     *
     * <p>One account rather than everybody's, because the caller is a customer looking at their own
     * history rather than a sweep passing over all of them. {@link #stillHoldingMoneyThatLandedBefore}
     * is the sweep's question and the two differ in exactly that.
     *
     * <p>No boundary in time, either. A rule that turns on age asks for the deposits old enough for
     * it; a page asks about the deposits in front of the customer, whatever age they are, because
     * every one of them has an anniversary coming.
     *
     * <p>Deposits holding nothing are outside the query rather than filtered out of the answer, for
     * the reason the sweep's query gives: money never comes back into one, so a deposit at zero has
     * nothing left to decide about.
     */
    @Query("select deposit from Deposit deposit "
            + "where deposit.savingsAccountId = :savingsAccountId and deposit.remainingAmount > 0 "
            + "and deposit.origin <> " + MONEY_THE_BANK_ADDED + " "
            + "order by deposit.depositedAt asc, deposit.id asc")
    List<Deposit> stillHoldingMoneyIn(@Param("savingsAccountId") long savingsAccountId);

    /**
     * Gives what remains to every deposit that has no answer to the question, and reports how many
     * that was.
     *
     * <p>A deposit recorded before the column existed says nothing about how much of it is left, and
     * all of it is: nothing could have taken any, because nothing could take money out of a savings
     * account when that deposit was made. Run at start-up by {@link DepositsOnStartUp}, which is what
     * lets the money balance be summed from what remains without a balance changing underneath
     * anybody the first time a database written before this is opened.
     *
     * <p>Only the deposits with nothing recorded, so that running it again on a database it has
     * already been through changes nothing — and so that a deposit a withdrawal has drawn down is
     * never handed its money back.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query("update Deposit deposit set deposit.remainingAmount = deposit.amount "
            + "where deposit.remainingAmount is null")
    int giveEveryDepositWhatRemainsOfIt();

    /**
     * Says that every row which does not name an origin was money the customer put in, and reports
     * how many that was.
     *
     * <p><strong>The migration that makes the three rules above true of an existing file.</strong>
     * Every query that asks for {@code origin = CUSTOMER} leaves out a row whose origin is null —
     * that is what comparing to null does — so on the morning this release opens a database written
     * by the last one, a customer's mark, their week and their anniversaries would every one of
     * them be counted over no rows at all. Somebody would open the application to find they had
     * never saved anything.
     *
     * <p>The value is honest rather than a default: until interest existed, money reached a savings
     * account in exactly one way, which was somebody moving it there. {@link Deposit#getOrigin}
     * reads a null the same way, so the application is correct before this runs and merely faster
     * afterwards — but the queries above are the ones that decide, and they ask the database.
     *
     * <p>Only the rows with nothing recorded, so a start after the first changes nothing, and so
     * that no row the bank wrote can ever be relabelled as something a customer paid in.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query("update Deposit deposit set deposit.origin = " + THE_CUSTOMERS_OWN_MONEY + " "
            + "where deposit.origin is null")
    int sayThatEveryRecordedDepositWasTheCustomersOwnMoney();

    /**
     * The savings accounts behind every deposit that does not yet say whose saving it was, each named
     * once.
     *
     * <p>Which customer holds them is not this module's to know: {@link DepositsOnStartUp} asks
     * Accounts and comes back with the answer. Named accounts rather than deposits, so that a
     * customer with a hundred deposits into two accounts is two questions and two statements.
     */
    /**
     * What this customer holds across every savings account they have, and nought for somebody who
     * has never paid anything in — an answer rather than an absence.
     *
     * <p>What is left rather than what landed: money that has since been withdrawn is not being
     * saved, which is the whole point of asking.
     */
    @Query("select coalesce(sum(deposit.remainingAmount), 0) from Deposit deposit "
            + "where deposit.customerId = :customerId")
    BigDecimal stillSavedBy(@Param("customerId") long customerId);

    /**
     * The same figure counting only what the customer put there themselves: what is left of their
     * own deposits, and nought for somebody who has never paid anything in.
     *
     * <p><strong>The other half of the pair the mark is judged with, and it has to be blind to
     * interest for the same reason {@link #everEarnedOnBy} is.</strong> What a deposit earns on is
     * {@code stillSaved + amount - everEarnedOn}, so both figures in that subtraction have to be
     * about the same money or the difference between them stops meaning "what an earlier withdrawal
     * took and has not been put back". Count the bank's payments in one and not the other and a
     * customer who withdrew five hundred euros would find a year of interest had quietly filled
     * part of the gap back in — earning them points, a second time, on euros they never moved.
     *
     * <p><strong>Euros that arrived by a move are counted, and they have to be.</strong> A move
     * takes them out of one of this customer's accounts and puts them into another, so if the
     * arriving row were left out the figure would fall by the whole amount while the mark stayed
     * where it was — opening a gap the customer never made, and quietly earning them nothing on
     * the next real deposit that filled it back in. Counted on both sides, a move leaves this
     * figure exactly as it was, which is what it should do: nothing was saved and nothing was
     * spent.
     *
     * <p><strong>Deliberately not what {@link #stillSavedBy} answers, and both are needed.</strong>
     * That one is a balance: every euro actually sitting in their savings accounts, interest
     * included, which is what a challenge asking somebody to hold a buffer is about and what the
     * account's own balance agrees with. This one is a term in an arithmetic about what has been
     * saved. Two questions, two queries, and naming the difference is cheaper than one query with a
     * flag.
     */
    @Query("select coalesce(sum(deposit.remainingAmount), 0) from Deposit deposit "
            + "where deposit.customerId = :customerId "
            + "and deposit.origin <> " + MONEY_THE_BANK_ADDED + "")
    BigDecimal stillSavedOutOfTheirOwnMoneyBy(@Param("customerId") long customerId);

    /**
     * The most this customer has ever held in savings: everything their deposits have earned on,
     * added up.
     *
     * <p>That sum is the high-water mark rather than merely related to it, and
     * {@link TheMostEverSaved} carries the reasoning. A deposit recorded before the column existed
     * earned on every euro it moved, which is what the fallback to the amount says.
     *
     * <p><strong>A row that arrived by a move is counted here too, and it carries the figure its
     * source rows gave up.</strong> That is what makes the mark stand still across a move: the
     * rows the euros left are lighter by exactly what the row they arrived in is heavier by. Left
     * out, the mark would fall by what those euros had already earned on and they would be paid
     * for a second time the day they came back; counted with a nought on it, the mark would fall
     * just the same. The figure travels with the euros because the mark is a statement about euros
     * and not about rows.
     *
     * <p><strong>Their own money, and this is the rule that would be most expensive to get
     * wrong.</strong> The mark answers "how much of the money you put in have you already been paid
     * points for", and money the bank added is not money you put in. A year of interest counted
     * here would raise the mark by what it paid and quietly reduce what the customer's next real
     * deposit earns — and the symptom would surface months later as a deposit that earned nothing
     * for no visible reason. The rows the bank wrote carry a nought of their own as well, so the
     * figure would be right even without this line; the line is here because the rule is a rule
     * about which rows count, and a rule is worth saying where it is kept.
     */
    @Query("select coalesce(sum(coalesce(deposit.earnedOnAmount, deposit.amount)), 0) from Deposit deposit "
            + "where deposit.customerId = :customerId "
            + "and deposit.origin <> " + MONEY_THE_BANK_ADDED + "")
    BigDecimal everEarnedOnBy(@Param("customerId") long customerId);

    /**
     * Which deposits were made into one savings account, oldest first, and nothing else about them.
     *
     * <p>Every one of them, including deposits that have since been emptied. A caller asking this is
     * asking about what the deposits <em>earned</em> rather than about what they still hold, and the
     * points a drained deposit earned are still in the customer's pot with their own twelve months
     * to run — leaving it out would take a real deadline off a real screen.
     *
     * <p>Identifiers rather than deposits, which is the whole point of it being its own query.
     * {@link #madeIntoNewestFirst} answers this too, and a caller
     * that wanted nothing but the numbers would be loading every row and looking up what each of
     * them earned in order to throw all of it away.
     *
     * <p>Oldest first, because that is the order the money went in and it makes a log line naming
     * them read as a chronology. Nothing depends on the order: the one caller groups these by the
     * day their points go.
     *
     * <p>A row that arrived by a move is in this list, because it earns: no points on the way in,
     * but a loyalty anniversary every year it is left alone, credited against exactly this
     * identifier. Left out, the days those points go would be missing from the account's own
     * reading of when its points expire.
     *
     * <p>The customer's own, because the one caller is drawing the days this account's points go
     * and a month's interest has never earned a point. A row of interest in this list would be an
     * identifier the points ledger has never heard of, looked up on every read of a screen in order
     * to be told nothing.
     */
    @Query("select deposit.id from Deposit deposit "
            + "where deposit.savingsAccountId = :savingsAccountId "
            + "and deposit.origin <> " + MONEY_THE_BANK_ADDED + " "
            + "order by deposit.depositedAt asc, deposit.id asc")
    List<Long> idsOfDepositsInto(@Param("savingsAccountId") long savingsAccountId);

    @Query("select distinct deposit.savingsAccountId from Deposit deposit "
            + "where deposit.customerId is null")
    List<Long> savingsAccountsBehindDepositsWithoutACustomer();

    /**
     * Says whose saving every deposit into one savings account was, and reports how many that was.
     *
     * <p>A deposit recorded before this says nothing about whose saving it was, and a week and a run
     * of weeks are now counted by exactly that: without this, a customer opening the application on
     * the morning of the upgrade has saved nothing this week and is on no run, whatever they paid in
     * yesterday. Only the deposits with nothing recorded, so a start after the first changes nothing.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query("update Deposit deposit set deposit.customerId = :customerId "
            + "where deposit.customerId is null and deposit.savingsAccountId = :savingsAccountId")
    int sayWhoseSavingWentInto(@Param("savingsAccountId") long savingsAccountId,
                               @Param("customerId") long customerId);

    /**
     * The first money ever paid into one savings account, or nothing at all when none ever was.
     *
     * <p>Oldest first with the identifier breaking a tie, so that two deposits recorded in the same
     * millisecond — which a wound clock and a seeded history both produce — answer with the one
     * that was written first rather than with whichever the database felt like.
     *
     * <p>It exists for the migration that dates a savings account's agreement, which has no opening
     * date to work from on an account written before there were agreements: the day the money first
     * arrived is the honest reading of when that account started being a savings account.
     */
    Optional<Deposit> findFirstBySavingsAccountIdOrderByDepositedAtAscIdAsc(long savingsAccountId);

    /**
     * How many deposits do not say which version of their account's terms they landed under.
     *
     * <p>A count over the whole ledger rather than a list, because the only caller wants to know
     * whether there is anything to do at all: on every start after the first the answer is nought
     * and the migration reads one row and stops. A list would be every deposit ever made, loaded to
     * be counted and thrown away.
     */
    @Query("select count(deposit) from Deposit deposit where deposit.termsVersion is null")
    long howManyDoNotSayWhichVersionTheyLandedUnder();

    /**
     * Says which version every deposit into one savings account landed under, and reports how many
     * that was.
     *
     * <p>Only the deposits that say nothing, so a start after the first changes nothing — and so
     * that a deposit which already names a version is never rewritten. That matters more than
     * idempotence: the version on a deposit is the agreement it was actually priced under, and an
     * account that has since taken newer terms would have its older deposits quietly restamped with
     * the version it is on today.
     *
     * <p>It carries its own transaction for the reason the migration above it does: its one caller
     * runs before the application has a transaction, a request, or a web server.
     */
    @Transactional
    @Modifying
    @Query("update Deposit deposit set deposit.termsVersion = :version "
            + "where deposit.termsVersion is null and deposit.savingsAccountId = :savingsAccountId")
    int sayWhichVersionTheyLandedUnder(@Param("savingsAccountId") long savingsAccountId,
                                       @Param("version") int version);
}
