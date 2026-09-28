package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.accounts.ABillThatCouldNotBePaid;
import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.DeclaredIncome;
import io.dataroots.savingstreak.accounts.TheMonthAhead;
import io.dataroots.savingstreak.accounts.WhatACurrentAccountHolds;
import io.dataroots.savingstreak.automation.AutomationService;
import io.dataroots.savingstreak.budgets.SpendingService;
import io.dataroots.savingstreak.budgets.WhatACategoryCostInAMonth;
import io.dataroots.savingstreak.budgets.WhatAnAccountSpentInAMonth;
import io.dataroots.savingstreak.automation.OccurrenceOutcome;
import io.dataroots.savingstreak.automation.RecordedOccurrence;
import io.dataroots.savingstreak.deposits.DepositStillHoldingMoney;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.loyalty.LoyaltyService;
import io.dataroots.savingstreak.products.ASetOfTerms;
import io.dataroots.savingstreak.products.AVersionAndWhatItChanged;
import io.dataroots.savingstreak.products.NoticeGiven;
import io.dataroots.savingstreak.products.NoticesService;
import io.dataroots.savingstreak.products.ProductsService;
import io.dataroots.savingstreak.products.TheAgreementAnAccountIsOn;
import io.dataroots.savingstreak.products.TheNewerTermsOnOffer;
import io.dataroots.savingstreak.products.TheNoticeOnAnAccount;
import io.dataroots.savingstreak.loyalty.NextAnniversaryOfADeposit;
import io.dataroots.savingstreak.scheme.SchemeService;
import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the rules have decided was worth saying: the Notifications module's face to the rest of the
 * application, and the whole of the rule.
 *
 * <p>A module of its own because a notification is not owned by any of the rules it reports on. It
 * reads a savings balance and the deposits still holding money from Deposits, who holds an account
 * from Accounts, and when those deposits next pay from Loyalty; it writes nothing but its own
 * record, and none of those modules learns that notifications exist. It moves no money, credits no
 * points and secures no week: this is a record addressed to a customer, not a rule that pays them.
 *
 * <p>Three things happen here: a sweep raises what the rules have to say, a customer reads what has
 * been said to them ({@link #notificationsOf}), and a customer marks what they have read
 * ({@link #markEverythingReadFor}). The two reads are addressed to a customer rather than to an
 * account — a notification carries the account it is about so the panel can name the pot, but who it
 * concerns is a person, and somebody saving towards two goals has one panel and not two.
 *
 * <p>Three rules, and the sweep runs all of them over every savings account. One is about where a
 * balance stands on a fixed ladder, argued out below; one is about a deposit's coming anniversary
 * and is argued out at {@link #whatThisAccountsAnniversariesHaveToSay}; one is about a saving rule
 * that fell due and could not be honoured, and is argued out at
 * {@link #whatThisAccountsRulesHaveToSay}. They share the walk, the moment and the one write, and
 * share nothing else — a balance rung is about an account, an anniversary is about one deposit
 * inside it, and a transfer that did not happen is about one day one rule fell due on.
 *
 * <p><strong>The budgets are the newest of the rules and the only ones about a plan.</strong> Two of
 * them are about one spending category's month — that it has crossed the published share of what
 * the month allows, four fifths under the scheme as it is seeded, or all of it — and one is about
 * the account's whole promise for the month. All three are raised on the crossing and not on the
 * condition, and all three are at most one per month for the thing they are about: a category for
 * the first two and the account for the third. The month is therefore part of what makes each of
 * them unique, and it travels in the row's existing
 * {@code occursOn} as the day that month began. They are argued out at
 * {@link #whatThisAccountsBudgetsHaveToSay} and {@link #whatThisMonthsPromisesHaveToSay}, and the
 * rules themselves live in {@link WhenABudgetIsRunningOut} and {@link WhenAMonthIsOverCommitted}.
 *
 * <p><strong>Three of the rules are about an agreement rather than about money, and they are the
 * newest.</strong> One says a fixed term is within the published notice period of the day it is up
 * — thirty days under the scheme as it is seeded — so that the ending is the customer's decision
 * before it is made for them; one says notice given on an amount has run its days; and one says the
 * product an account is on has published terms that <em>better</em> the ones it is living under.
 * They share the one reading of the account's agreement that the walk takes, and they are argued
 * out at {@link #whatThisAccountsTermHasToSay},
 * {@link #whatThisAccountsNoticeHasToSay} and {@link #whatThisAccountsProductHasToSay}.
 *
 * <p><strong>The bettered-terms rule holds the only opinion in this module, and it is held here on
 * purpose.</strong> Products words what differs between two agreements and deliberately has no view
 * on whether the difference is an improvement — free savings' seeded second version cut the rate,
 * and a comparison that graded itself is how "nothing adopts newer terms on your behalf" quietly
 * becomes "and we decided this one was an improvement". So the sentences a notification carries are
 * quoted from there word for word and the judgement is made in {@link WhenTermsHaveBeenBettered},
 * in the open, where it can be read and disagreed with.
 *
 * <p><strong>Nothing this module writes is ever retracted, and the budgets are where that shows.
 * </strong> Every figure the three rules above are judged from is derived on every read, so a
 * correction to a spend's split can empty the very month a warning was raised about. The warning
 * stays: an inbox is a log of what was said on the nights it was said, and the budget screen is what
 * is true now. Both are right, they will look inconsistent to somebody reading a June inbox against
 * a June budget, and that is why it is written down on both sides rather than on neither —
 * {@code Spend} and {@code SpendsService.correctTheSplitOf} say it from the other one.
 *
 * <p><strong>A balance notification is raised on a change of rung, not on a crossing event.</strong>
 * That is the load-bearing decision in here and it is forced by what this application stores. A
 * savings balance is derived by summing what every deposit still holds, every time it is asked for;
 * no previous balance is written down anywhere, so there is nothing for a crossing to be measured
 * against. So the sweep reads the rung the account stands on now, reads the rung it was last known
 * to stand on out of the newest thing said about that account's balance, and raises only on a
 * difference. A higher rung is one {@link NotificationReason#BALANCE_THRESHOLD_REACHED} naming the
 * rung landed on, however many rungs a single deposit vaulted. A lower rung is one
 * {@link NotificationReason#BALANCE_THRESHOLD_LOST}. The same rung is nothing at all, which is what
 * stops a balance resting at EUR 1.001 announcing itself every night for a year.
 *
 * <p>Reading the record backwards is therefore part of the rule, and it is why a lost rung names the
 * lowest rung the balance no longer reaches rather than the rung it was last known on. A row is read
 * back as a position: {@code REACHED(t)} says the balance stood on {@code t}, {@code LOST(t)} says
 * it stood on the rung below {@code t}, and no row at all says it stood on none. A balance that
 * falls from EUR 1.100 to EUR 120 has left the EUR 1.000 rung and the EUR 500 rung, and a row
 * naming EUR 1.000 would read back as "it stands on EUR 500" — which it does not, so the next sweep
 * would announce the same fall a second time. Naming the lowest rung it no longer reaches, EUR 500,
 * reads back as EUR 100, which is exactly where it stands. One fall, one notification, and a fall of
 * a single rung — every fall the acceptance criteria describe, and the ordinary case — names the rung
 * the customer had reached.
 *
 * <p>An account that already stands on a rung and has never been told so is announced on the first
 * sweep. Nothing is backfilled beyond that: the first run says where an account stands today and
 * does not walk backwards inventing the moments it climbed there.
 *
 * <p><strong>The five figures that decide when this module speaks come from the scheme in force on
 * the night the sweep runs.</strong> The rungs a balance is congratulated on reaching, the share of
 * a month's allowance at which a budget is called running low, how many unpaid bills is a spiral,
 * and the two notice periods — a maturity's and an anniversary's — were constants beside the rules
 * that read them and are now published figures a bank can change without a deploy.
 * {@link #theFiguresTonightsSweepRunsUnder} reads them, and the DEBUG line it writes is what a
 * reviewer holds a night's decisions against.
 *
 * <p><strong>Read once for the night and not once per customer, and that is a decision rather than
 * an optimisation.</strong> One sweep is one event: every row it writes carries the one moment it
 * was told about, and every row it writes should have been decided by the one set of figures that
 * was in force at that moment. Reading the scheme inside the walk would let a version published
 * mid-run — which cannot happen today, because the whole sweep is one transaction, but which is the
 * shape a reader has to be able to rule out — congratulate one customer on EUR 500 and the next on
 * EUR 250 in a single night, and the DEBUG line saying what tonight ran under would be a lie about
 * half of it. The cost is a read of a handful of rows per night against a read per account, which
 * is the cheaper side as well as the honest one. What it is emphatically not is the streak
 * derivation: that spans weeks, is handed the scheme's whole history, and is right to be, because
 * one set of figures could never judge twenty-six weeks published under several.
 *
 * <p><strong>A notification already raised is never rewritten.</strong> Nothing in here reaches back
 * into a row, and a threshold that moved last Monday does not move what somebody was told the Monday
 * before: the inbox is a log of what was said on the nights it was said, which is the same promise
 * the budgets already make about a month a correction has emptied. The consequence that needs care
 * is the balance record, because the sweep reads its own rows back as positions — and it stays
 * readable because {@link TheBalanceRungs}'s three answers are total. A row naming EUR 500, written
 * under a ladder that has since dropped EUR 500, is read back as the highest rung the figure does
 * reach under tonight's ladder rather than as a position that no longer exists; the account is then
 * judged against where it stands tonight, which is the only comparison that could be right.
 */
@Service
public class NotificationsService {

    private static final Logger log = LoggerFactory.getLogger(NotificationsService.class);

    /**
     * Two, like every other amount of money in this application. Written here rather than borrowed,
     * because the only figures this module quotes are the ones it logs and the ladder's own rungs.
     */
    private static final int DECIMAL_PLACES_IN_MONEY = 2;

    /**
     * The place an account's read of its failed occurrences starts from when nothing has ever been
     * announced on it. Zero rather than null, because identifiers start at one and a floor no row
     * can sit on is one fewer branch than an absence.
     */
    private static final long NOTHING_HAS_BEEN_ANNOUNCED_YET = 0L;

    private final AccountsService accounts;
    private final DepositsService deposits;
    private final LoyaltyService loyalty;

    /**
     * What the saving rules actually did, which this module reads and never writes to.
     *
     * <p>The one inward edge of the automation feature, and the direction this module already reads
     * in: it reads Deposits, Accounts and Loyalty the same way and none of them learns that
     * notifications exist. A rules job that raised its own notification would be a second producer
     * in a second module and would split the one place this feature logs — and the hours already
     * line up, the rules running at two and this sweep at four over a night that has settled.
     */
    private final AutomationService automation;

    /**
     * How the month the clock is in is going on a current account, category by category, which is
     * the whole of what the three budget reasons are judged from.
     *
     * <p>Read and never recomputed. What a category was allowed to cost in a month, what carried
     * into it under the customer's own rollover rule and what it has cost are the budgets module's
     * answers, and a second fold of the carry in here would be a second answer free to disagree with
     * the one the budget screen draws. The edge runs the way every other edge out of this module
     * runs: this module reads, and the module it reads gains nothing and learns nothing.
     */
    private final SpendingService spending;

    /**
     * What each savings account's agreement says, which is where a maturity date, a product's name
     * and the versions of its terms all come from.
     *
     * <p>Read and never decided. When a term is up, which version an account is living under and
     * what differs between two versions are Products' answers, and this module works out none of
     * them a second time — a second maturity calendar would disagree with the sweep that settles
     * maturities on exactly the dates that are hard, and a second wording of a difference would put
     * two descriptions of one rate cut on one screen.
     *
     * <p><strong>One judgement is nevertheless this module's own and is deliberately not asked
     * for.</strong> Whether a newer version <em>betters</em> the one an account holds is an opinion,
     * Products holds none, and it must stay that way: a comparison that graded itself is how
     * "nothing adopts newer terms on your behalf" quietly becomes "and we decided this one was an
     * improvement". So the sentences are quoted from there and the judgement is made here, in
     * {@link WhenTermsHaveBeenBettered}, where it can be read and argued with.
     */
    private final ProductsService products;

    /**
     * What notice is standing on each savings account and which of it has run its days.
     *
     * <p>Asked rather than worked out, for the reason above: whether notice is ready is two
     * subtractions against the day it was given and the days the agreement asks for, done in
     * {@code WhenANoticeIsReady} and nowhere else. This module reads the answer and decides only
     * whether it has already said it.
     */
    private final NoticesService notices;

    /**
     * Where the five figures that decide when this module speaks come from.
     *
     * <p>Read on every sweep rather than held, which is the arrangement the points ledger already
     * has with this module and for the reason it gives: a stored copy of the scheme is a second
     * place the answer lives, and the copy nobody remembered to refresh is the one that tells
     * somebody the wrong thing. A sweep is a nightly job that writes, so one small read at the top
     * of it is not the cost worth saving.
     *
     * <p>The edge runs the way every other edge out of this module runs — this module reads, and the
     * module it reads gains nothing and learns nothing. The scheme module depends on nothing in this
     * application, which is exactly what lets six modules be priced from it without any of them
     * learning about the others.
     */
    private final SchemeService scheme;

    private final NotificationRepository notifications;

    /**
     * For the one moment this module stamps that nobody hands it: the moment a customer looked.
     *
     * <p>The sweep is told when, because its caller runs on a schedule and one moment has to be
     * stamped on everything one run raises. A customer reading their notifications is not a sweep —
     * it is a request arriving now, exactly like a deposit landing or a reward being claimed, and
     * every one of those reads the clock in the service that owns the rule rather than being told
     * the time by a controller. The application's clock, which a trainer can wind, so that a
     * notification read on a wound-forward clock is marked at the moment the application thinks it
     * is.
     */
    private final Clock clock;

    NotificationsService(AccountsService accounts, DepositsService deposits, LoyaltyService loyalty,
                         AutomationService automation, SpendingService spending,
                         ProductsService products, NoticesService notices, SchemeService scheme,
                         NotificationRepository notifications, Clock clock) {
        this.accounts = accounts;
        this.deposits = deposits;
        this.loyalty = loyalty;
        this.automation = automation;
        this.spending = spending;
        this.products = products;
        this.notices = notices;
        this.scheme = scheme;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Raises everything the rules have to say as at the given moment, and writes it down.
     *
     * <p>Every savings account on every run. With the seeded customers that is three accounts, and
     * with a real population it would be the first thing to make incremental — noted rather than
     * built, because a sweep that only looked at accounts that had moved would need a record of what
     * had moved, which is a second store to keep in step with this one.
     *
     * <p>Answers nothing, as neither of the other two nightly sweeps does: its caller runs on a
     * schedule with nobody waiting on it, and a figure handed back to a scheduled method is a figure
     * nothing can read. What the sweep did is in the INFO lines below.
     *
     * <p>The caller says what time it is. A sweep run against a clock a trainer has wound forward
     * has to judge everything against the moment the application thinks it is, and nothing in here
     * reads a clock of its own — one moment is stamped on everything one run raises.
     *
     * <p>The figures come off the scheme once, before the walk, for the reason the class comment
     * argues: one sweep is one event and every row it writes was decided by one published version.
     * The day that version is chosen for is the day the application's clock reads, which is where
     * the moment above came from too — the job takes both off the one clock, so a trainer who winds
     * forward across a published Monday gets a sweep judged by the version that Monday brought in.
     *
     * <p>Public, unlike the rest of this module, and not because anything outside wants it:
     * {@code @Transactional} is applied by a proxy, a proxy cannot advise a method that is not
     * public, and the annotation would otherwise be quietly ignored and a half-finished sweep would
     * commit. The power it leaks is the power to run tonight's sweep early, which raises nothing
     * that has already been raised and is exactly what the development jobs endpoint offers anyway.
     */
    @Transactional
    public void raiseNotifications(Instant now) {
        TheFiguresASweepRunsUnder tonight = theFiguresTonightsSweepRunsUnder();
        List<Long> savingsAccounts = accounts.everySavingsAccount();
        List<Notification> raising = new ArrayList<>();
        int depositsConsidered = 0;
        int occurrencesConsidered = 0;
        int agreementsConsidered = 0;
        int noticesConsidered = 0;
        for (long savingsAccountId : savingsAccounts) {
            Optional<AccountHolder> holder = accounts.holderOfSavingsAccount(savingsAccountId);
            if (holder.isEmpty()) {
                sayWhyThisAccountIsPassedOver(savingsAccountId);
                continue;
            }
            long customerId = holder.get().customerId();
            whatThisAccountsBalanceHasToSay(savingsAccountId, customerId, tonight.rungs(), now)
                    .ifPresent(raising::add);
            // The deposits still holding money, oldest first — which is both the set the
            // anniversary rule has anything to say about and the order the next withdrawal would
            // drain them in. Read here rather than inside the rule so that the count reaches the
            // one line the sweep logs about itself.
            List<DepositStillHoldingMoney> holding =
                    deposits.depositsStillHoldingMoneyIn(savingsAccountId);
            depositsConsidered += holding.size();
            raising.addAll(whatThisAccountsAnniversariesHaveToSay(savingsAccountId, customerId,
                    holding, tonight.daysBeforeAnAnniversaryIsWorthSaying(), now));
            // Only the ones this module has not already spoken about. Automation keeps a failed
            // occurrence for ever, so asking for all of them would mean re-reading every failure
            // this account has ever had, every night, for ever — and expanding the whole lot into
            // one bind list to ask what had already been said about them. Where this module got to
            // is its own record to keep, so it keeps it: the newest occurrence it has announced on
            // this account is the place the next read starts from.
            long announcedThrough = whereThisAccountWasLeftOff(savingsAccountId);
            List<RecordedOccurrence> couldNotBeHonoured =
                    automation.occurrencesThatCouldNotBeHonouredIn(savingsAccountId, announcedThrough);
            occurrencesConsidered += couldNotBeHonoured.size();
            raising.addAll(whatThisAccountsRulesHaveToSay(
                    savingsAccountId, customerId, couldNotBeHonoured, now));
            // What this account is living under, read once and handed to all three of the rules
            // that are about an agreement rather than about money. The maturity date, the product's
            // name and whether the account has been closed all come off it, and reading it three
            // times would let three notifications raised in one sweep describe three instants of a
            // row a customer can move with one press. The same bargain the two budget rules strike
            // with one month's spending.
            Optional<TheAgreementAnAccountIsOn> agreement =
                    products.theAgreementOf(savingsAccountId);
            agreementsConsidered += agreement.isPresent() ? 1 : 0;
            whatThisAccountsTermHasToSay(savingsAccountId, customerId, agreement,
                    tonight.daysBeforeAMaturityIsWorthSaying(), now)
                    .ifPresent(raising::add);
            List<NoticeGiven> standing = whatIsStandingOn(savingsAccountId, agreement);
            noticesConsidered += standing.size();
            raising.addAll(whatThisAccountsNoticeHasToSay(
                    savingsAccountId, customerId, standing, now));
            whatThisAccountsProductHasToSay(savingsAccountId, customerId, agreement, now)
                    .ifPresent(raising::add);
        }
        // The other side of the customer's money, walked separately and on its own list of accounts.
        // Bills, arrears and a declared income are facts about a current account, and an account
        // with bills and no savings account at all would never be reached by the loop above.
        List<Long> currentAccounts = accounts.everyCurrentAccount();
        int datesThatFellShortConsidered = 0;
        int categoriesConsidered = 0;
        for (long currentAccountId : currentAccounts) {
            Optional<WhatACurrentAccountHolds> holds =
                    accounts.currentAccountWith(currentAccountId);
            if (holds.isEmpty()) {
                // Only reachable if an account is closed between the listing and this read, which
                // nothing in this application does yet. Said out loud for the reason the savings
                // side says it: with nobody to address a notification to there is nothing either
                // rule below could say about the account.
                log.warn("account passed over currentAccountId={} "
                        + "reason=nobody holds it any more", currentAccountId);
                continue;
            }
            long customerId = holds.get().customerId();
            // Every date this account has ever failed to pay, settled or not, read once and handed
            // to both rules. One is about the dates themselves and the other about how the pile of
            // them has moved over time, and they are the same record read two ways — which is also
            // why this is not the cursor-driven read the automation rule uses: the piling-up replay
            // needs the whole history, so there is nothing for a cursor to save.
            List<ABillThatCouldNotBePaid> fellShort =
                    accounts.billsThatCouldNotBePaidOn(currentAccountId);
            datesThatFellShortConsidered += fellShort.size();
            raising.addAll(whatThisAccountsBillsHaveToSay(
                    currentAccountId, customerId, fellShort, now));
            whatThisAccountsArrearsHaveToSay(currentAccountId, customerId, fellShort,
                    accounts.monthlyIncomeOn(currentAccountId),
                    tonight.howManyOutstandingIsASpiral(), now)
                    .ifPresent(raising::add);
            // How the month the clock is in is going, read once and handed to both budget rules.
            // One is about each category's own line and the other about what the account's whole
            // plan comes to, and they are two readings of one answer — so reading it twice would
            // let a warning about a category and a warning about the month it is in describe two
            // different instants of a record that is derived on every read.
            WhatAnAccountSpentInAMonth thisMonth = spending.thisMonthOn(currentAccountId);
            categoriesConsidered += thisMonth.categories().size();
            raising.addAll(whatThisAccountsBudgetsHaveToSay(currentAccountId, customerId, thisMonth,
                    tonight.shareOfWhatAMonthAllows(), now));
            whatThisMonthsPromisesHaveToSay(currentAccountId, customerId, thisMonth, now)
                    .ifPresent(raising::add);
        }
        // One write for the run, so that a sweep either says everything it decided or nothing at
        // all. Nothing raised is no statement at all rather than an empty one.
        List<Notification> raised = notifications.saveAll(raising);
        for (Notification notification : raised) {
            // One line per notification with the figures that produced it and the row it became, so
            // that a reviewer can check by hand that the rule fired on the values it claims. After
            // the write rather than before it, because the identifier is half of what makes the line
            // worth having.
            log.info("notification raised customerId={} reason={} savingsAccountId={} "
                            + "currentAccountId={} depositId={} occurrenceId={} noticeId={} "
                            + "billId={} billName={} categoryId={} categoryName={} "
                            + "productCode={} productName={} termsVersion={} amount={} balance={} "
                            + "arrears={} points={} occursOn={} notificationId={}",
                    notification.getCustomerId(), notification.getReason(),
                    notification.getSavingsAccountId(), notification.getCurrentAccountId(),
                    notification.getDepositId(), notification.getOccurrenceId(),
                    notification.getNoticeId(), notification.getBillId(),
                    notification.getBillName(), notification.getCategoryId(),
                    notification.getCategoryName(), notification.getProductCode(),
                    notification.getProductName(), notification.getTermsVersion(),
                    // Two of the fourteen reasons fill these with a percentage rather than with
                    // money, and it is quoted to two places either way — which is what a rate and
                    // an amount both read as. The reason on the same line says which it is.
                    asMoney(notification.getAmount()), asMoney(notification.getBalance()),
                    notification.getArrears(), notification.getPoints(),
                    notification.getOccursOn(), notification.getId());
        }
        // One line per sweep: the moment it judged everything against, how many accounts, deposits
        // and failed occurrences it looked at, and how many notifications it raised. A quiet night
        // and a night that was handed nothing to look at can be told apart from this line alone.
        log.info("notifications raised asAt={} schemeVersion={} accountsConsidered={} "
                        + "depositsConsidered={} occurrencesNotYetAnnouncedConsidered={} "
                        + "agreementsConsidered={} noticesStandingConsidered={} "
                        + "currentAccountsConsidered={} datesThatFellShortConsidered={} "
                        + "categoriesConsidered={} raised={}",
                now, tonight.version(), savingsAccounts.size(), depositsConsidered,
                occurrencesConsidered, agreementsConsidered, noticesConsidered,
                currentAccounts.size(), datesThatFellShortConsidered, categoriesConsidered,
                raised.size());
    }

    /**
     * For each of the five lines the scheme draws, how many customers would have something said to
     * them under a candidate scheme that is not being said under the version in force, and how many
     * the other way about. Nothing is written and nothing is raised.
     *
     * <p><strong>This is a question on the module's face rather than a set of rules let out of
     * it.</strong> The five predicates stay exactly where they are argued and exactly as visible as
     * they were: the balance ladder, the budget share, the arrears replay and the two notice
     * periods are package-private and remain so. What a caller outside gets is one question and two
     * value types, which is the arrangement every other module in this application has with the
     * ones that read it. The alternative — widening five rule classes and letting the administration
     * path evaluate them itself — would have put a second place where a scheme figure is worked
     * out, which is the single failure mode this whole feature was written to remove.
     *
     * <p><strong>Both sets of figures are assembled through the same factory tonight's sweep
     * uses.</strong> The euro rungs become a ladder and the published percentage becomes a fraction
     * in {@code TheFiguresASweepRunsUnder} and nowhere else, so the preview cannot be comparing a
     * percentage against a share on one side and a share against a share on the other. That record
     * stays private; it is used here, not exposed.
     *
     * <p><strong>Who stands on the far side of a line, not what tonight's sweep would write.</strong>
     * The sweep is once-only by design — a rung announced last night is not announced again — so
     * "how many rows would the four o'clock job write" would answer a different question every
     * night and would read as nought on the second preview of the same evening. What an
     * administrator is asking is about the population, not about the job: how many people are over
     * the line the candidate draws who are not over the line drawn now. That is stable, and it is
     * the only version of the question that can be checked by hand.
     *
     * <p><strong>Counted per customer, per occasion.</strong> A customer with two savings accounts
     * standing on two different rungs is one customer, not two; but a customer who would gain one
     * occasion and lose another is counted on both sides, because both things are true of them.
     * {@link HowManyStandOnTheFarSideOfALine} argues that at length, and it is why the two figures
     * on it are not meant to sum to anything.
     *
     * <p>Read-only, and it is one of the two halves of a promise the preview makes about the whole
     * application: looking is free.
     *
     * @param inForce   the version deciding what is being said tonight
     * @param candidate the version somebody is thinking about publishing
     * @param now       the moment both readings are taken at, so that one clock decides both
     */
    @Transactional(readOnly = true)
    public List<HowManyStandOnTheFarSideOfALine> whoWouldStandOnTheFarSideOfEachLine(
            TheSchemeAsPublished inForce, TheSchemeAsPublished candidate, Instant now) {
        Map<ALineTheSchemeDraws, Map<Long, Set<String>>> asItIs =
                whatThereIsToSayUnder(TheFiguresASweepRunsUnder.in(inForce), now);
        Map<ALineTheSchemeDraws, Map<Long, Set<String>>> asItWouldBe =
                whatThereIsToSayUnder(TheFiguresASweepRunsUnder.in(candidate), now);
        List<HowManyStandOnTheFarSideOfALine> counted = new ArrayList<>();
        for (ALineTheSchemeDraws line : ALineTheSchemeDraws.values()) {
            Map<Long, Set<String>> isSaid = asItIs.get(line);
            Map<Long, Set<String>> wouldBeSaid = asItWouldBe.get(line);
            Set<Long> everybodyEitherWay = new HashSet<>(isSaid.keySet());
            everybodyEitherWay.addAll(wouldBeSaid.keySet());
            int wouldBeToldAndIsNot = 0;
            int isToldAndWouldNotBe = 0;
            for (long customerId : everybodyEitherWay) {
                Set<String> is = isSaid.getOrDefault(customerId, Set.of());
                Set<String> would = wouldBeSaid.getOrDefault(customerId, Set.of());
                if (!is.containsAll(would)) {
                    wouldBeToldAndIsNot++;
                }
                if (!would.containsAll(is)) {
                    isToldAndWouldNotBe++;
                }
            }
            // The inputs behind one line's two figures: how many customers had anything to be said
            // to them either way, and how many of them moved in each direction. A reviewer redoes
            // the subtraction from this line, and a line that moved nobody is visibly a line that
            // was looked at rather than one that was skipped.
            log.debug("a line the scheme draws was counted for a preview line={} "
                            + "customersWithSomethingEitherWay={} wouldBeToldAndIsNot={} "
                            + "isToldAndWouldNotBe={} inForceVersion={} candidateVersion={}",
                    line, everybodyEitherWay.size(), wouldBeToldAndIsNot, isToldAndWouldNotBe,
                    inForce.version(), candidate.version());
            counted.add(new HowManyStandOnTheFarSideOfALine(
                    line, wouldBeToldAndIsNot, isToldAndWouldNotBe));
        }
        return counted;
    }

    /**
     * Everything there would be to say tonight under one set of figures, as an occasion per line
     * per customer.
     *
     * <p>An occasion rather than a count, because the two readings have to be compared and not
     * merely subtracted: a customer standing on one rung under the ladder in force and a different
     * rung under the candidate has the same number of things to be told and two different things to
     * be told, and a count would report no change at all. The occasion is a string built from the
     * identifiers the decision was made on — which account, which rung, which category, which
     * month, which deposit and which day — because all that is ever done with it is equality, and a
     * record per line would be five records whose only method is the one {@code String} already
     * has.
     *
     * <p>Every account on every call, exactly as the sweep walks them, and for the same reason the
     * sweep gives: a walk that only looked at what had moved would need a record of what had moved.
     * A preview is one request and a handful of households, and the arithmetic below is the same
     * arithmetic the four o'clock job does twice a night.
     */
    private Map<ALineTheSchemeDraws, Map<Long, Set<String>>> whatThereIsToSayUnder(
            TheFiguresASweepRunsUnder figures, Instant now) {
        Map<ALineTheSchemeDraws, Map<Long, Set<String>>> occasions =
                new EnumMap<>(ALineTheSchemeDraws.class);
        for (ALineTheSchemeDraws line : ALineTheSchemeDraws.values()) {
            occasions.put(line, new HashMap<>());
        }
        for (long savingsAccountId : accounts.everySavingsAccount()) {
            Optional<AccountHolder> holder = accounts.holderOfSavingsAccount(savingsAccountId);
            if (holder.isEmpty()) {
                sayWhyThisAccountIsPassedOver(savingsAccountId);
                continue;
            }
            long customerId = holder.get().customerId();
            figures.rungs().theRungStoodOnWith(deposits.moneyBalanceOf(savingsAccountId))
                    .ifPresent(rung -> thereIsSomethingToSay(occasions,
                            ALineTheSchemeDraws.A_BALANCE_RUNG, customerId,
                            savingsAccountId + " stands on " + asMoney(rung)));
            products.theAgreementOf(savingsAccountId)
                    .filter(itIsOn -> !itIsOn.isClosed() && itIsOn.maturesOn() != null)
                    .filter(itIsOn -> AMaturityComingSoon.isWorthSayingAsAt(itIsOn.maturesOn(), now,
                            figures.daysBeforeAMaturityIsWorthSaying()))
                    .ifPresent(itIsOn -> thereIsSomethingToSay(occasions,
                            ALineTheSchemeDraws.A_MATURITY_COMING_SOON, customerId,
                            savingsAccountId + " matures on " + itIsOn.maturesOn()));
            List<DepositStillHoldingMoney> holding =
                    deposits.depositsStillHoldingMoneyIn(savingsAccountId);
            if (holding.isEmpty()) {
                continue;
            }
            Map<Long, NextAnniversaryOfADeposit> whenTheyNextPay =
                    loyalty.whenTheDepositsInAnAccountNextPay(savingsAccountId);
            for (DepositStillHoldingMoney deposit : holding) {
                NextAnniversaryOfADeposit nextPays = whenTheyNextPay.get(deposit.id());
                // A deposit holding nothing, and a deposit whose tenth rounds down to no points:
                // the two the sweep passes over before the notice period is ever consulted, so a
                // preview counting them would report a notice period moving people it would not
                // actually say anything to.
                if (nextPays == null || nextPays.points() == 0) {
                    continue;
                }
                if (AnAnniversaryComingSoon.isWorthSayingAsAt(nextPays.on(), now,
                        figures.daysBeforeAnAnniversaryIsWorthSaying())) {
                    thereIsSomethingToSay(occasions,
                            ALineTheSchemeDraws.AN_ANNIVERSARY_COMING_SOON, customerId,
                            deposit.id() + " pays on " + nextPays.on());
                }
            }
        }
        for (long currentAccountId : accounts.everyCurrentAccount()) {
            Optional<WhatACurrentAccountHolds> holds = accounts.currentAccountWith(currentAccountId);
            if (holds.isEmpty()) {
                log.warn("account passed over currentAccountId={} "
                                + "reason=it has no holder, which a current account cannot be "
                                + "opened without",
                        currentAccountId);
                continue;
            }
            long customerId = holds.get().customerId();
            DeclaredIncome income = accounts.monthlyIncomeOn(currentAccountId);
            WhenArrearsArePilingUp.ThePile pile = WhenArrearsArePilingUp.replaying(
                    accounts.billsThatCouldNotBePaidOn(currentAccountId),
                    income.isDeclared() ? income.amount() : null,
                    figures.howManyOutstandingIsASpiral());
            if (pile.isASpiral()) {
                thereIsSomethingToSay(occasions, ALineTheSchemeDraws.ARREARS_PILING_UP, customerId,
                        currentAccountId + " owes " + pile.outstanding() + " dates");
            }
            WhatAnAccountSpentInAMonth thisMonth = spending.thisMonthOn(currentAccountId);
            for (WhatACategoryCostInAMonth category : thisMonth.categories()) {
                WhenABudgetIsRunningOut
                        .whatThereIsToSayAbout(category, figures.shareOfWhatAMonthAllows())
                        .ifPresent(reason -> thereIsSomethingToSay(occasions,
                                ALineTheSchemeDraws.A_BUDGET_RUNNING_LOW, customerId,
                                category.categoryId() + " in " + thisMonth.month() + " is "
                                        + reason));
            }
        }
        return occasions;
    }

    /**
     * Files one occasion under the line it belongs to and the customer it is about.
     *
     * <p>A method rather than two chained {@code computeIfAbsent} calls at four call sites, because
     * the four call sites are the interesting part of the walk above and this is bookkeeping.
     */
    private void thereIsSomethingToSay(Map<ALineTheSchemeDraws, Map<Long, Set<String>>> occasions,
                                       ALineTheSchemeDraws line, long customerId, String occasion) {
        occasions.get(line).computeIfAbsent(customerId, nobodyYet -> new HashSet<>()).add(occasion);
    }

    /**
     * Tells one customer that their turn in a waiting list came and one of the thing is being
     * held for them until the moment on it.
     *
     * <p><strong>The one thing this module raises outside its own nightly sweep, and the
     * exception is argued rather than taken.</strong> Everything above is raised by
     * {@link #raiseNotifications} because one producer is the whole point of this module: a
     * rung lost at noon is announced at four the next morning, and raising it inside the
     * withdrawal would put a second writer in a second module and split the one place this
     * feature logs. Nothing about that changes here. The writer is still this module — this
     * method, in this class, writing this module's rows — and what moves is only when it is
     * asked. It has to move because the thing it is about has a clock on it: a hold is
     * seventy-two hours long and starts running the moment the rewards sweep creates it, at
     * five, an hour after this module's own sweep has already been and gone. Waiting for the
     * next one would mean telling somebody about a three-day deadline twenty-three hours into
     * it.
     *
     * <p><strong>Called through an interface the rewards module declares, and this module
     * implements.</strong> Rewards has spent an entire feature not gaining an outbound
     * dependency, so it states what happened in its own vocabulary and
     * {@code APromotedWaiterIsTold} beside this class answers it. Nothing in Rewards learns
     * that notifications exist, which is the same arrangement Accounts already has with Shared
     * Pots.
     *
     * <p><strong>Deliberately not {@code @Transactional}, which is the opposite of everything
     * else public here.</strong> It is called from inside the rewards sweep's own transaction
     * and has to join it rather than open a boundary of its own: a boundary would let a failure
     * in here mark the caller's transaction rollback-only and take a night's promotions down
     * with it, which is the one outcome worse than a customer not being told. The caller
     * catches and logs instead, and says so where it does.
     *
     * <p>Nothing is deduplicated and nothing is a crossing. A promotion happens once to one
     * place in one queue; a customer promoted twice has been promoted twice and is told twice.
     * The moment is the caller's, like every other moment this module stamps, so that a
     * wound-forward clock moves this along with the hold it is about.
     */
    public void aRewardIsBeingHeldFor(long customerId, String offerCode, String offerTitle,
                                      Instant lapsesAt, Instant now) {
        Notification raised = notifications.save(Notification.aRewardIsBeingHeldFor(
                customerId, offerCode, offerTitle, lapsesAt, now));
        // One line with the row it became and the two facts a customer acts on, shaped like the
        // line the sweep writes per notification and for the same reason: a reviewer checking
        // by hand that a promotion reached the person it was for reads this beside the rewards
        // module's own "waiter promoted" line and the two have to name the same customer, the
        // same offer and the same deadline.
        log.info("notification raised customerId={} reason={} offerCode={} offerTitle={} "
                        + "lapsesAt={} raisedAt={} notificationId={}",
                customerId, raised.getReason(), offerCode, offerTitle, lapsesAt, now,
                raised.getId());
    }

    /**
     * Everything this customer has been told, newest first, read and unread together.
     *
     * <p>Read and unread together because the panel is a record rather than an inbox that empties.
     * A rule that fired is worth being able to look up after you have seen it once, and a training
     * application whose whole point is showing a rule fire should not hide the evidence that it did
     * the moment somebody glances at it.
     *
     * <p>Newest first, ordered by the moment each was raised and then by identifier, so that two
     * notifications raised by the same sweep — one sweep stamps one moment on everything it raises —
     * still come back in a settled order rather than in whatever order the rows are read in.
     *
     * <p>Whether the customer exists is asked first, so that somebody nobody has heard of is refused
     * rather than answered with the empty list of somebody who has simply never been told anything.
     * That is the line every per-customer read in this application draws.
     *
     * <p>One transaction over both reads, so the existence check and the list describe the same
     * instant of the record.
     *
     * @throws NotificationRefused if there is no such customer
     */
    @Transactional(readOnly = true)
    public List<RaisedNotification> notificationsOf(long customerId) {
        refuseUnlessTheCustomerExists(customerId);
        List<RaisedNotification> raised = notifications
                .findByCustomerIdOrderByRaisedAtDescIdDesc(customerId).stream()
                .map(RaisedNotification::of)
                .toList();
        log.debug("notifications read customerId={} notifications={} unread={}",
                customerId, raised.size(),
                raised.stream().filter(one -> one.readAt() == null).count());
        return raised;
    }

    /**
     * Marks everything this customer has not yet looked at as looked at, now, and answers the whole
     * list back exactly as {@link #notificationsOf} would.
     *
     * <p>The list comes back so that the caller needs one round trip rather than two. Opening the
     * panel is one action, and a page that had to ask again afterwards to find out what it was
     * showing would render the count it just cleared for as long as the second request took.
     *
     * <p>Idempotent, and the notification is what makes it so: {@link Notification#read} keeps the
     * moment it was first read at, so a second call marks nothing, changes nothing and answers the
     * same list. The count in the INFO line is what was actually marked and not how many the
     * customer holds, which is what makes a second call's line say plainly that it did nothing.
     *
     * <p>Every unread notification of theirs, across every savings account they hold, because that
     * is what the panel showed: it lists everything, so reading it is a statement about everything
     * in it.
     *
     * <p>The whole list is loaded rather than only the unread ones, because both things this method
     * does need it — the unread rows to mark, and all of them to answer with. Two queries would be
     * two readings of a record that changed in between.
     *
     * @throws NotificationRefused if there is no such customer
     */
    @Transactional
    public List<RaisedNotification> markEverythingReadFor(long customerId) {
        refuseUnlessTheCustomerExists(customerId);
        Instant now = clock.instant();
        List<Notification> theirs =
                notifications.findByCustomerIdOrderByRaisedAtDescIdDesc(customerId);
        int marked = 0;
        for (Notification notification : theirs) {
            if (notification.read(now)) {
                marked++;
            }
        }
        // The business event, with the values that decided it: who looked, when they looked, how
        // many were actually marked and how many they hold altogether. A count dropping to zero has
        // this line behind it, and a second call over the same rows says marked=0 rather than going
        // silent.
        log.info("notifications marked read customerId={} notifications={} asAt={} held={}",
                customerId, marked, now, theirs.size());
        return theirs.stream().map(RaisedNotification::of).toList();
    }

    /**
     * Refuses, in the words Accounts owns, unless this application has heard of the customer.
     *
     * <p>One place decides the words and one place says them out loud, which is why both reads call
     * this rather than each asking and refusing for itself: a refusal's reason only reaches whoever
     * asked, and the WARN line is the only copy anybody reviewing the application afterwards can
     * read.
     */
    private void refuseUnlessTheCustomerExists(long customerId) {
        if (accounts.customerExists(customerId)) {
            return;
        }
        String reason = AccountsService.noSuchCustomer(customerId);
        log.warn("notifications rejected customerId={} kind={} reason={}",
                customerId, NotificationRefused.Kind.NO_SUCH_CUSTOMER, reason);
        throw new NotificationRefused(NotificationRefused.Kind.NO_SUCH_CUSTOMER, reason);
    }

    /**
     * Says why a savings account no customer holds is walked past, and raises nothing for it.
     *
     * <p>It is a shared pot's account. The pot holds it and no person does, which is what keeps a
     * pot's money out of everybody's personal totals — and it is why this sweep, which has a rule
     * for every savings account in the application, has nothing to say about this one.
     *
     * <p><strong>What a balance-threshold notice about a pot would mean, and who it would be
     * addressed to, is a real question with no cheap answer.</strong> Every rule this class has is
     * addressed to one customer about their own saving: what you have not put away this week, what
     * your deposit is about to earn, the rule of yours that could not be honoured. A pot's balance
     * is several people's money at once. Addressed to whoever owns the pot, a notice would tell one
     * member about euros that are mostly somebody else's; addressed to every member, it would tell
     * somebody who paid in yesterday that they are behind. The honest answer is that a pot needs
     * rules written for a group — "the pot reached its goal", "somebody has stopped paying in" —
     * and the spec puts notifications about pot activity in a slice of their own. Until that slice
     * exists, raising nothing is right, and this line is what makes it visible rather than silent.
     *
     * <p>It also replaces a sentence that was not true. It used to read "nobody holds it any more",
     * written for an account whose holder had gone, which nothing in this application does; a pot's
     * account never had a holder to lose.
     *
     * <p>At DEBUG rather than WARN, which is the other half of the correction. One line per pot per
     * night is an expected part of every sweep from now on, and a WARN raised nightly by something
     * working exactly as designed is how a log stops being read.
     */
    private void sayWhyThisAccountIsPassedOver(long savingsAccountId) {
        log.debug("account passed over savingsAccountId={} reason=a shared pot holds it, and these "
                + "rules are addressed to one customer about their own saving", savingsAccountId);
    }

    /**
     * The five figures tonight's sweep judges everything by, read off the scheme in force and said
     * out loud before a single account is looked at.
     *
     * <p><strong>One read for the night.</strong> The class comment argues why it is here and not
     * inside the walk; what this method adds is the line that makes the choice checkable. A reviewer
     * reading a night's log sees which version of the scheme was in force, the Monday it started on
     * and every figure it decided with, once, at the top — and every "passed over" line under it is
     * then a line they can redo by hand. A figure read per customer could not be reported this way
     * at all, which is the second reason the read is where it is.
     *
     * <p>DEBUG rather than INFO, like every other line in this module that reports the inputs behind
     * a decision rather than the decision. The INFO line the sweep writes at the end carries the
     * version, which is the one figure worth having in a log nobody has turned up.
     *
     * <p>The percentage the scheme publishes and the fraction the budget rule compares against are
     * both quoted, because they are the one figure in two units and a reviewer checking a month
     * called running low needs to see that the conversion happened exactly once.
     */
    private TheFiguresASweepRunsUnder theFiguresTonightsSweepRunsUnder() {
        TheSchemeAsPublished inForce = scheme.theSchemeInForce();
        TheFiguresASweepRunsUnder tonight = TheFiguresASweepRunsUnder.in(inForce);
        log.debug("the figures tonight's sweep runs under schemeVersion={} effectiveFrom={} "
                        + "balanceRungs={} whatShareOfABudgetIsRunningLow={} "
                        + "shareOfWhatAMonthAllows={} howManyOutstandingIsASpiral={} "
                        + "daysBeforeAMaturityIsWorthSaying={} "
                        + "daysBeforeAnAnniversaryIsWorthSaying={}",
                tonight.version(), tonight.effectiveFrom(), tonight.rungs().rungs(),
                inForce.whatShareOfABudgetIsRunningLow(), tonight.shareOfWhatAMonthAllows(),
                tonight.howManyOutstandingIsASpiral(), tonight.daysBeforeAMaturityIsWorthSaying(),
                tonight.daysBeforeAnAnniversaryIsWorthSaying());
        return tonight;
    }

    /**
     * The one thing this account's balance has to say tonight, if it has anything to say at all.
     *
     * <p>At most one: a balance is in one position, and the notification is about the position
     * having changed rather than about the amount that changed it. A deposit that vaults from
     * nothing to EUR 2.600 is one occasion, not four.
     *
     * <p>The ladder arrives rather than being read here, and it is tonight's: the rungs are a figure
     * the scheme publishes, so both sides of the comparison — where the balance stands and where the
     * record says it stood — are read against the one ladder in force on the night the sweep runs.
     * Reading the old row against the old ladder was considered and is wrong: the question this
     * rule asks is whether the account's position has moved, and a position is only a position with
     * respect to a ladder. Two ladders would make every account on the application move the night a
     * rung was published, whether its balance had moved a cent or not.
     *
     * @param rungs the balance rungs the scheme in force tonight publishes
     */
    private Optional<Notification> whatThisAccountsBalanceHasToSay(long savingsAccountId,
                                                                   long customerId,
                                                                   TheBalanceRungs rungs,
                                                                   Instant now) {
        BigDecimal balance = deposits.moneyBalanceOf(savingsAccountId);
        Optional<BigDecimal> standsOn = rungs.theRungStoodOnWith(balance);
        Optional<Notification> lastSaid = notifications
                .findFirstBySavingsAccountIdAndReasonInOrderByRaisedAtDescIdDesc(
                        savingsAccountId, NotificationReason.THE_BALANCE_REASONS);
        Optional<BigDecimal> stoodOn = theRungThatWasLastSaidToBeStoodOn(lastSaid, rungs);
        // The inputs behind the decision, before it is taken: the balance, where that puts the
        // account, where the record says it was, and which row that reading came from. A reviewer
        // redoes the comparison from this line.
        log.debug("the rung a savings account stands on savingsAccountId={} customerId={} "
                        + "balance={} rungNow={} rungLastSaid={} fromNotificationId={}",
                savingsAccountId, customerId, asMoney(balance), asMoney(standsOn.orElse(null)),
                asMoney(stoodOn.orElse(null)), lastSaid.map(Notification::getId).orElse(null));
        int moved = howTheRungHasMoved(standsOn, stoodOn);
        if (moved == 0) {
            // Which is every account on the second run of a night, so this is the line that says a
            // sweep raising nothing is a sweep whose balances have not moved between rungs.
            log.debug("account passed over for a balance notification savingsAccountId={} "
                            + "balance={} rung={} reason=it stands on the rung it already stood on",
                    savingsAccountId, asMoney(balance), asMoney(standsOn.orElse(null)));
            return Optional.empty();
        }
        if (moved > 0) {
            // Higher, so the account is standing on a rung: nothing is lower than standing on none.
            return Optional.of(Notification.balanceRungReached(
                    customerId, savingsAccountId, standsOn.orElseThrow(), now));
        }
        // Lower, so the record had it on a rung. The rung named is the lowest one the balance no
        // longer reaches — the class comment argues out why that, and not the rung it was last
        // known on. There is always one, because a balance that has fallen is below the rung it
        // fell from and therefore below the top of the ladder; the fallback names that rung so
        // that an impossible reading cannot make the sweep say nothing at all.
        BigDecimal fallenOff = rungs.theRungAbove(balance).orElseGet(stoodOn::orElseThrow);
        return Optional.of(
                Notification.balanceRungLost(customerId, savingsAccountId, fallenOff, now));
    }

    /**
     * Everything this account's deposits have to say about their coming anniversaries tonight.
     *
     * <p>One notification per deposit at most, and none for most deposits most nights. A deposit is
     * worth saying something about when its next anniversary is near enough
     * ({@link AnAnniversaryComingSoon}), is worth at least one point, and has not already been
     * announced under the reason it would get now.
     *
     * <p><strong>The split is the whole of this rule.</strong> The deposits arrive oldest first,
     * ordered by the moment the money landed and then by identifier, which is precisely the order
     * {@code WithdrawalsService} drains them in — so the first of them is the deposit the next euro
     * withdrawn from this account comes out of. That one's bonus is
     * {@link NotificationReason#LOYALTY_BONUS_AT_RISK}; every other deposit near its anniversary is
     * standing behind money that would go first, and gets
     * {@link NotificationReason#LOYALTY_BONUS_ABOUT_TO_PAY}. Mutually exclusive by construction, so
     * one deposit never says both things about one anniversary — and a deposit that becomes the
     * oldest, when what stood in front of it is emptied, is announced again under the other reason,
     * which is the escalation this feature is for.
     *
     * <p>Nothing here works out what an anniversary falls on or what it pays. Both come from
     * {@code LoyaltyService.whenTheDepositsInAnAccountNextPay} exactly as the deposits table already
     * shows them, and the rate they were worked out from is written down once, in {@code
     * LoyaltyRate}. A tenth computed a second time here would be a second answer to disagree with
     * the first the day anybody reprices loyalty.
     *
     * <p>How far ahead the window reaches arrives rather than being read here, and it is tonight's:
     * the notice period is a figure the scheme publishes. A deposit passed over for being further
     * off than the window is passed over for being further off than <em>tonight's</em> window, and
     * the DEBUG line quotes the day the window actually reached to — which is the figure a reviewer
     * needs when the length is no longer the same thirty days it was last month.
     *
     * @param daysBeforeAnAnniversaryIsWorthSaying how many days before an anniversary it is worth
     *                                             saying so, from the scheme in force tonight
     */
    private List<Notification> whatThisAccountsAnniversariesHaveToSay(
            long savingsAccountId, long customerId, List<DepositStillHoldingMoney> holding,
            int daysBeforeAnAnniversaryIsWorthSaying, Instant now) {
        if (holding.isEmpty()) {
            // Which is every account nobody has saved into, so this is the line that says a sweep
            // raising no anniversary for an account had no anniversary to raise one about. The
            // deposits that have been emptied are outside the listing rather than passed over in
            // it: money never comes back into one, and a deposit at zero has no anniversary left to
            // reach.
            log.debug("account passed over for anniversary notifications savingsAccountId={} "
                    + "reason=none of its deposits holds money", savingsAccountId);
            return List.of();
        }
        Map<Long, NextAnniversaryOfADeposit> whenTheyNextPay =
                loyalty.whenTheDepositsInAnAccountNextPay(savingsAccountId);
        Set<AnAnnouncedAnniversary> alreadyAnnounced = whatHasAlreadyBeenAnnouncedFor(holding);
        LocalDate theLastDayWorthSaying = AnAnniversaryComingSoon.theLastDayWorthSayingAsAt(
                now, daysBeforeAnAnniversaryIsWorthSaying);
        // The oldest deposit still holding money, which is the one the next withdrawal empties
        // first. Present because the listing is not empty.
        long firstInLineForTheNextWithdrawal = holding.get(0).id();
        // The inputs behind every decision below, before any of them is taken: how far the window
        // reaches tonight, and which deposit the queue puts first. A reviewer redoes the split from
        // this line and the per-deposit lines under it.
        log.debug("the withdrawal queue in a savings account savingsAccountId={} customerId={} "
                        + "depositsStillHoldingMoney={} firstInLineForTheNextWithdrawal={} "
                        + "daysBeforeAnAnniversaryIsWorthSaying={} "
                        + "anniversariesWorthSayingUpToAndIncluding={} alreadyAnnounced={}",
                savingsAccountId, customerId, holding.size(), firstInLineForTheNextWithdrawal,
                daysBeforeAnAnniversaryIsWorthSaying, theLastDayWorthSaying,
                alreadyAnnounced.size());
        List<Notification> raising = new ArrayList<>();
        for (DepositStillHoldingMoney deposit : holding) {
            NextAnniversaryOfADeposit nextPays = whenTheyNextPay.get(deposit.id());
            if (nextPays == null) {
                // Loyalty's way of saying a deposit holds nothing, and unreachable while both reads
                // ask the same question inside one transaction. Kept because the absence has a
                // meaning and a sweep that silently dropped a deposit it had counted would be the
                // one silence this module cannot explain.
                log.debug("deposit passed over for an anniversary notification depositId={} "
                        + "reason=it holds no money", deposit.id());
                continue;
            }
            if (!AnAnniversaryComingSoon.isWorthSayingAsAt(nextPays.on(), now,
                    daysBeforeAnAnniversaryIsWorthSaying)) {
                log.debug("deposit passed over for an anniversary notification depositId={} "
                                + "occursOn={} worthSayingUpToAndIncluding={} "
                                + "daysBeforeAnAnniversaryIsWorthSaying={} "
                                + "reason=its anniversary is further off than the notice period "
                                + "tonight's scheme publishes",
                        deposit.id(), nextPays.on(), theLastDayWorthSaying,
                        daysBeforeAnAnniversaryIsWorthSaying);
                continue;
            }
            if (nextPays.points() == 0) {
                log.debug("deposit passed over for an anniversary notification depositId={} "
                                + "occursOn={} remainingAmount={} "
                                + "reason=a tenth of what it still holds rounds down to no points",
                        deposit.id(), nextPays.on(), asMoney(deposit.remainingAmount()));
                continue;
            }
            NotificationReason reason = deposit.id() == firstInLineForTheNextWithdrawal
                    ? NotificationReason.LOYALTY_BONUS_AT_RISK
                    : NotificationReason.LOYALTY_BONUS_ABOUT_TO_PAY;
            // Added rather than asked, so that the set carries what this run has decided as well as
            // what the record already held: an account listed twice, or a deposit reached twice by
            // any later change to this loop, cannot announce the same occasion under the same reason
            // twice. Which is also what the database's own index refuses.
            if (!alreadyAnnounced.add(
                    new AnAnnouncedAnniversary(deposit.id(), reason, nextPays.on()))) {
                // Which is every deposit on the second run of a night, and on all thirty nights an
                // anniversary is near after the first of them. One occasion, one notification.
                log.debug("deposit passed over for an anniversary notification depositId={} "
                                + "reason=this anniversary has already been announced occursOn={} "
                                + "announcedReason={}",
                        deposit.id(), nextPays.on(), reason);
                continue;
            }
            // The figures behind the decision and, above all, why this deposit got this reason
            // rather than the other one — the split is the load-bearing call in this module and it
            // should not have to be inferred from which enum came out.
            log.debug("a deposit's anniversary is worth saying depositId={} savingsAccountId={} "
                            + "occursOn={} points={} remainingAmount={} "
                            + "firstInLineForTheNextWithdrawal={} reason={}",
                    deposit.id(), savingsAccountId, nextPays.on(), nextPays.points(),
                    asMoney(deposit.remainingAmount()),
                    firstInLineForTheNextWithdrawal, reason);
            raising.add(reason == NotificationReason.LOYALTY_BONUS_AT_RISK
                    ? Notification.anniversaryAtRiskFor(customerId, savingsAccountId, deposit.id(),
                            nextPays.on(), nextPays.points(), now)
                    : Notification.anniversaryComingFor(customerId, savingsAccountId, deposit.id(),
                            nextPays.on(), nextPays.points(), now));
        }
        return raising;
    }

    /**
     * Everything this account's saving rules have to say tonight, which is one line per transfer
     * that was relied on and did not happen, and nothing at all about the ones that worked.
     *
     * <p><strong>Only the occurrences that could not be honoured reach here.</strong> Automation
     * answers that question itself, and the two outcomes it leaves out are left out deliberately: a
     * customer who set up automation did so in order to stop being told about it, so a transfer that
     * moved its money says nothing; and a sweep whose balance was already at or under its floor
     * moved nothing because the arithmetic said nothing, so announcing that as a failure would be
     * reporting arithmetic as an error and would train a customer to ignore the thing that tells
     * them about the real ones.
     *
     * <p><strong>Two reasons come out of one walk, because there are two ways a day can be
     * refused.</strong> The current account did not hold what the rule asked for, or the savings
     * account it pays into had been closed — and they are different things to say, for the reason
     * {@code OccurrenceOutcome} keeps them apart: one says top the current account up before next
     * time, and the other says this rule is pointing at an account that will never take money
     * again, so change it or end it. Telling the second customer they were short would send them to
     * fix an account that was never the problem.
     *
     * <p>Ticket 18 wrote the closed-account outcome and deliberately left this sweep asking only for
     * the other one, on the ground that the announcement wanted deciding rather than inheriting.
     * This is that decision: it is announced, under a reason of its own, with no money on it at all.
     * The occurrence carries nought moved and no shortfall, so there is no figure to quote and
     * inventing one would be this module saying something the record does not.
     *
     * <p><strong>Once per occurrence, however many nights the sweep runs.</strong> Automation's
     * record keeps a failed occurrence for ever — that is what that record is for — so the whole of
     * what makes this idempotent is the set of occurrences already announced, read back out of this
     * module's own rows. The same shape the anniversary rule uses, and the database keeps it too:
     * {@code one_notification_per_occurrence} refuses a second row naming the same occurrence.
     *
     * <p>The day the transfer was due travels as {@code occursOn} and what the account was short as
     * {@code amount}, which are the two figures the sentence on the page is written from. What the
     * rule asked for is not repeated here: it is on the occurrence, in the rule's own history, where
     * the rest of what happened that day already is.
     *
     * <p><strong>An occurrence recorded before the shortfall was is refused rather than
     * announced.</strong> The figure is a new column on an old table, so an occurrence settled as
     * one that could not be honoured before this rule existed has none, and no later reading can
     * recover it: it is the difference between what the rule asked for on a day that has passed and
     * what the account held that morning, and neither of those is written down anywhere. Both of
     * the two figures are the sentence — "a transfer did not happen, and you were this much short"
     * — so a row without one is not a quieter notification but an empty line on the page, which is
     * the opposite of what this rule is for. Refusing it also keeps {@link Notification}'s promise
     * that its factories cannot make an inconsistent row, which the factory now enforces for
     * itself. The account's own history still shows the occurrence, its outcome and its day; what
     * this module declines to do is announce a failure it cannot describe.
     *
     * <p>That refusal is the same judgement the balance rule makes on its first sweep — where an
     * account stands today, and no walking backwards inventing the moments it got there.
     */
    private List<Notification> whatThisAccountsRulesHaveToSay(
            long savingsAccountId, long customerId, List<RecordedOccurrence> couldNotBeHonoured,
            Instant now) {
        if (couldNotBeHonoured.isEmpty()) {
            // Which is every account whose rules all fired, every account with no rules at all, and
            // every account whose failures have all been announced already — so this is the line
            // that says a sweep raising nothing about automation had nothing to raise one about
            // rather than having decided against saying something.
            log.debug("account passed over for automation notifications savingsAccountId={} "
                            + "reason=no occurrence on it went unhonoured since the newest one it "
                            + "has already been told about", savingsAccountId);
            return List.of();
        }
        Set<Long> alreadyAnnounced = whatHasAlreadyBeenAnnouncedAbout(couldNotBeHonoured);
        log.debug("the occurrences a savings account could not honour savingsAccountId={} "
                        + "customerId={} occurrencesThatCouldNotBeHonoured={} alreadyAnnounced={}",
                savingsAccountId, customerId, couldNotBeHonoured.size(), alreadyAnnounced.size());
        List<Notification> raising = new ArrayList<>();
        for (RecordedOccurrence occurrence : couldNotBeHonoured) {
            // Added rather than asked, so that the set carries what this run has decided as well as
            // what the record already held — which is also what the database's own index refuses.
            if (!alreadyAnnounced.add(occurrence.id())) {
                // Which is every failed occurrence on the second run of a night, and on every night
                // after the one it was first announced on. One transfer that did not happen, one
                // notification, for ever.
                log.debug("occurrence passed over for an automation notification occurrenceId={} "
                                + "ruleId={} dueOn={} reason=this transfer has already been "
                                + "announced as one that did not happen",
                        occurrence.id(), occurrence.ruleId(), occurrence.dueOn());
                continue;
            }
            if (occurrence.outcome() == OccurrenceOutcome.THE_ACCOUNT_IS_CLOSED) {
                // The figures behind the decision, and there is deliberately no money among them:
                // the day it fell due and the rule that was still standing are the whole of what
                // happened, because the current account was never the problem.
                log.debug("a saving rule that fired into a closed savings account is worth saying "
                                + "occurrenceId={} ruleId={} savingsAccountId={} dueOn={} "
                                + "daysLate={}",
                        occurrence.id(), occurrence.ruleId(), savingsAccountId, occurrence.dueOn(),
                        occurrence.daysLate());
                raising.add(Notification.anAutomaticTransferHadNowhereToGo(customerId,
                        savingsAccountId, occurrence.id(), occurrence.dueOn(), now));
                continue;
            }
            if (occurrence.shortfall() == null) {
                // A refusal rather than an oversight, so it is said at WARN with what decided it:
                // the occurrence, the rule and the day, so that whoever reads this can go and look
                // the row up and see for themselves that the figure is not there to be had.
                log.warn("occurrence refused an automation notification occurrenceId={} ruleId={} "
                                + "savingsAccountId={} dueOn={} reason=this occurrence was settled "
                                + "before what the account was short was recorded, and what a "
                                + "transfer on a day that has passed was short cannot be worked "
                                + "out afterwards, so there is no notification to raise that would "
                                + "say anything",
                        occurrence.id(), occurrence.ruleId(), savingsAccountId, occurrence.dueOn());
                continue;
            }
            // The figures behind the decision, before the row is made: which rule, which day, and
            // what the account was short — the three things the WARN the rules job left at two in
            // the morning can be held against.
            log.debug("an automatic transfer that did not happen is worth saying occurrenceId={} "
                            + "ruleId={} savingsAccountId={} dueOn={} daysLate={} shortfall={}",
                    occurrence.id(), occurrence.ruleId(), savingsAccountId, occurrence.dueOn(),
                    occurrence.daysLate(), asMoney(occurrence.shortfall()));
            raising.add(Notification.automaticTransferDidNotHappen(customerId, savingsAccountId,
                    occurrence.id(), occurrence.dueOn(), occurrence.shortfall(), now));
        }
        return raising;
    }

    /**
     * What this current account's refused dates have to say: one notification per due date that went
     * unpaid, raised the first time and never again for that date.
     *
     * <p><strong>The failure most likely to be got wrong, and the reason the record is asked before
     * anything is said.</strong> A rent owed for six months is presented on six nightly runs and
     * refused on all six, and it is one problem. The date is the thing that is announced, not the
     * refusal: the first time a due date appears in the record of what fell short it is said out
     * loud, and afterwards that pair — the bill and the day — is in the set of what has already been
     * announced for ever, whatever happens to the arrear.
     *
     * <p><strong>Which is why a date settled and then missed again is announced again.</strong> That
     * is a different due date, a different row and a different pair, and a customer who cleared their
     * arrears in April and missed the rent in June has a new problem worth being told about.
     *
     * <p>The two figures are insisted on here rather than left to the row's own constructor, for the
     * reason the automation rule insists on its shortfall: a date recorded before this application
     * wrote down what the account held cannot say what fell short, and a warning with a hole where
     * the money goes is worse than no warning. Such a date is refused with a WARN naming it. The
     * bill's name is insisted on in the same breath and for the same reason — it is the word the
     * sentence leads with, and a red warning that opens with a blank is the worst of the three.
     */
    private List<Notification> whatThisAccountsBillsHaveToSay(
            long currentAccountId, long customerId, List<ABillThatCouldNotBePaid> fellShort,
            Instant now) {
        if (fellShort.isEmpty()) {
            // Which is every account that has never missed a bill, and every account with no bills
            // at all — so this is the line that says a sweep raising nothing about this account had
            // nothing to raise one about rather than having decided against saying something.
            log.debug("account passed over for bill notifications currentAccountId={} "
                    + "reason=no date on it has ever been presented and refused", currentAccountId);
            return List.of();
        }
        Set<AnAnnouncedDueDate> alreadyAnnounced =
                whichOfTheseDueDatesHaveAlreadyBeenAnnounced(fellShort);
        log.debug("the dates a current account could not pay currentAccountId={} customerId={} "
                        + "datesThatFellShort={} stillOwed={} alreadyAnnounced={}",
                currentAccountId, customerId, fellShort.size(),
                fellShort.stream().filter(one -> !one.isCleared()).count(),
                alreadyAnnounced.size());
        List<Notification> raising = new ArrayList<>();
        for (ABillThatCouldNotBePaid refused : fellShort) {
            // Added rather than asked, so that the set carries what this run has decided as well as
            // what the record already held — which is also what the database's own index refuses.
            if (!alreadyAnnounced.add(
                    new AnAnnouncedDueDate(refused.billId(), refused.dueOn()))) {
                // Which is every refused date on the second run of a night, and on every one of the
                // six months a rent is carried after the night it was first missed. One due date,
                // one notification, for ever — this is the line a reviewer reads to confirm it.
                log.debug("date passed over for a bill notification currentAccountId={} billId={} "
                                + "name={} dueOn={} stillOwed={} reason=this due date has already "
                                + "been announced as one that could not be paid",
                        currentAccountId, refused.billId(), refused.billName(), refused.dueOn(),
                        !refused.isCleared());
                continue;
            }
            if (refused.amount() == null || refused.balanceThatFellShort() == null) {
                log.warn("date refused a bill notification currentAccountId={} billId={} name={} "
                                + "dueOn={} amount={} balance={} reason=this date was recorded "
                                + "before the application wrote down what the account held when it "
                                + "fell short, and what a balance was on a night that has passed "
                                + "cannot be worked out afterwards, so there is no notification to "
                                + "raise that would say anything",
                        currentAccountId, refused.billId(), refused.billName(), refused.dueOn(),
                        asMoney(refused.amount()), asMoney(refused.balanceThatFellShort()));
                continue;
            }
            if (refused.billName() == null || refused.billName().isBlank()) {
                // The one field the figures argument skips, and it is the word the sentence leads
                // with. A name is read off the bill the date belongs to, and nothing in this
                // application deletes a bill — so this is the same kind of refusal as the one above
                // it rather than one anybody has seen, and it is refused rather than drawn blank.
                log.warn("date refused a bill notification currentAccountId={} billId={} "
                                + "dueOn={} amount={} balance={} reason=the bill this date belongs "
                                + "to has no name to lead the sentence with, and a red warning "
                                + "opening with a blank says less than no warning at all",
                        currentAccountId, refused.billId(), refused.dueOn(),
                        asMoney(refused.amount()), asMoney(refused.balanceThatFellShort()));
                continue;
            }
            // The figures behind the decision, before the row is made: which bill, which day, what
            // it asked for and what was there — the three things the WARN the bills job left at half
            // past two can be held against.
            log.debug("a bill that could not be paid is worth saying currentAccountId={} billId={} "
                            + "name={} dueOn={} amount={} balance={} shortBy={} fellShortAt={} "
                            + "stillOwed={}",
                    currentAccountId, refused.billId(), refused.billName(), refused.dueOn(),
                    asMoney(refused.amount()), asMoney(refused.balanceThatFellShort()),
                    asMoney(refused.amount().subtract(refused.balanceThatFellShort())),
                    refused.fellShortAt(), !refused.isCleared());
            raising.add(Notification.aBillCouldNotBePaid(customerId, currentAccountId,
                    refused.billId(), refused.billName(), refused.dueOn(), refused.amount(),
                    refused.balanceThatFellShort(), now));
        }
        return raising;
    }

    /**
     * The louder warning: this account's unpaid bills have crossed from a bad month into a spiral.
     *
     * <p><strong>Raised on the crossing and not on the condition</strong>, which is the whole of the
     * rule and the thing that makes it worth reading twice. {@link WhenArrearsArePilingUp} replays
     * the account's whole record of failures and settlements and answers not only whether the pile
     * is over the threshold tonight but the moment it last got there. If that moment is older than
     * the last thing this module said about this account, the crossing has already been announced
     * and nothing is said — however many nights the pile has stood over the threshold since, and
     * however many more dates have joined it.
     *
     * <p>It is said again exactly when the account has come back under the threshold and crossed it
     * a second time, because coming back under clears the crossing the replay carries and the next
     * one is a new moment, later than anything already said.
     *
     * <p>One notification at most per account per sweep, which is why this answers with an
     * {@link Optional} where the bill rule answers with a list: an account has one pile.
     *
     * <p>How many outstanding is a spiral arrives rather than being read here, and the whole replay
     * is judged at tonight's count. {@link WhenArrearsArePilingUp} argues that out at length beside
     * the same concession the declared income already makes: a count that moves can move the
     * crossing the replay finds, and an account whose crossing moves is told once more. A crossing
     * already announced is not rewritten by any of this — the row stands as the record of the night
     * it was written on, and it is the crossing the <em>replay</em> now finds that is compared
     * against when it was written.
     *
     * @param howManyOutstandingIsASpiral how many dates outstanding at once is a spiral on its own,
     *                                    from the scheme in force tonight
     */
    private Optional<Notification> whatThisAccountsArrearsHaveToSay(
            long currentAccountId, long customerId, List<ABillThatCouldNotBePaid> fellShort,
            DeclaredIncome income, int howManyOutstandingIsASpiral, Instant now) {
        BigDecimal declared = income.isDeclared() ? income.amount() : null;
        WhenArrearsArePilingUp.ThePile pile =
                WhenArrearsArePilingUp.replaying(fellShort, declared, howManyOutstandingIsASpiral);
        Optional<Notification> lastSaid = notifications
                .findFirstByCurrentAccountIdAndReasonOrderByRaisedAtDescIdDesc(
                        currentAccountId, NotificationReason.BILLS_ARE_PILING_UP);
        // The inputs behind the decision, before it is taken: where the pile stands, what the two
        // conditions were judged against, when it last crossed, and what this module has already
        // said about this account. A reviewer redoes the comparison from this line, and it is the
        // line that shows the once-only rule holding on every night after the first.
        log.debug("the transition check behind the piling-up warning currentAccountId={} "
                        + "customerId={} outstanding={} owed={} declaredMonthlyIncome={} "
                        + "outstandingIsASpiralAt={} isASpiral={} crossedAt={} "
                        + "lastSaidAt={} fromNotificationId={}",
                currentAccountId, customerId, pile.outstanding(), asMoney(pile.owed()),
                asMoney(declared), howManyOutstandingIsASpiral,
                pile.isASpiral(), pile.crossedAt(),
                lastSaid.map(Notification::getRaisedAt).orElse(null),
                lastSaid.map(Notification::getId).orElse(null));
        if (!pile.isASpiral()) {
            // Which is every account that owes nothing, and every account owing one or two dates
            // worth less than a month's income — so this is the line that says a quiet account was
            // looked at rather than skipped.
            log.debug("account passed over for a piling-up warning currentAccountId={} "
                            + "outstanding={} owed={} reason=its arrears are not over the threshold",
                    currentAccountId, pile.outstanding(), asMoney(pile.owed()));
            return Optional.empty();
        }
        if (lastSaid.isPresent() && !pile.crossedAt().isAfter(lastSaid.get().getRaisedAt())) {
            // Which is every night after the one the crossing was announced on, and the night a
            // fourth arrear joins three that were already over the threshold. One crossing, one
            // warning, until the account climbs out and falls back in.
            log.debug("account passed over for a piling-up warning currentAccountId={} "
                            + "outstanding={} owed={} crossedAt={} lastSaidAt={} reason=this "
                            + "crossing has already been announced and the account has not been "
                            + "back under the threshold since",
                    currentAccountId, pile.outstanding(), asMoney(pile.owed()), pile.crossedAt(),
                    lastSaid.get().getRaisedAt());
            return Optional.empty();
        }
        return Optional.of(Notification.billsArePilingUp(
                customerId, currentAccountId, pile.outstanding(), pile.owed(), now));
    }

    /**
     * What this account's budgets have to say tonight: one line per category that has crossed a line
     * it had not crossed before in this month, and nothing at all about the ones that have not.
     *
     * <p><strong>Judged against what the month allows and never against what was budgeted.</strong>
     * {@link WhenABudgetIsRunningOut} argues that out; what it means here is that a category
     * carrying forty euros forward is not called four fifths gone until it has spent four fifths of
     * a hundred and forty, and an envelope that carried a deficit in is called overspent before a
     * single euro is spent in the month — which is exactly what its holder asked for by choosing the
     * rule.
     *
     * <p><strong>Once per category per month, and the month is part of the key.</strong> A category
     * four fifths gone on the tenth is four fifths gone on every night to the thirty-first, and a
     * line raised on that state would put the same sentence in front of its holder twenty times
     * about one thing. The next month is a new allowance and a category that crosses again in it has
     * climbed out — the month ended, the budget started again — and fallen back in, which is worth
     * saying a second time. That is the same bargain {@link NotificationReason#BILLS_ARE_PILING_UP}
     * strikes with a crossing, read in the only unit a budget has.
     *
     * <p><strong>A category that crosses both lines on one night is called overspent and is never
     * called running low.</strong> The quieter reason is the crossing of four fifths <em>without</em>
     * the crossing of all of it, so the two are exclusive by their own definitions and saying both
     * would put a line that is already false beside the line that replaced it. The record is asked
     * about both reasons together for the same reason: once a month has been called overspent, the
     * quieter line is not raised in it afterwards either — a correction that brings the month back
     * to nine tenths leaves the loud warning standing as the record of the night it was written on,
     * and a warning that got louder and then got softer about one month would be one nobody could
     * act on.
     *
     * <p><strong>Nothing here is retracted.</strong> Every figure it is judged from is derived on
     * every read, so a correction can empty the very month a warning is about; the warning stays,
     * because a notification is a record of what was said on a night rather than a claim about what
     * is true now. {@code Spend} and {@code SpendsService.correctTheSplitOf} say the same thing from
     * the other side of the application.
     *
     * <p><strong>The share arrives as a fraction and never as the percentage the scheme publishes.
     * </strong> {@code 80.00} is what an administrator types and {@code 0.8000} is what an allowance
     * is multiplied by, and {@link WhenABudgetIsRunningOut#theShareThatIs} is the one place the two
     * units meet — called once for the sweep rather than once per category, so that there is no
     * second site where somebody could hand this rule the percentage and get a month called running
     * low the moment a euro was spent in it. Which month the line lands in is unaffected: a category
     * that crossed under last month's share was told so then, once, and a share that moves does not
     * take that back or say it again.
     *
     * @param shareOfWhatAMonthAllows the fraction of a month's allowance at which it is worth saying
     *                                something — {@code 0.8000} for four fifths, never {@code 80.00}
     */
    private List<Notification> whatThisAccountsBudgetsHaveToSay(
            long currentAccountId, long customerId, WhatAnAccountSpentInAMonth thisMonth,
            BigDecimal shareOfWhatAMonthAllows, Instant now) {
        List<WhatACategoryCostInAMonth> budgeted = thisMonth.categories().stream()
                .filter(WhatACategoryCostInAMonth::isBudgeted)
                .toList();
        if (budgeted.isEmpty()) {
            // Which is every account whose holder has named no categories, and every account whose
            // categories are watched rather than policed — so this is the line that says a sweep
            // raising nothing about the budgets had no budget to raise anything about rather than
            // having decided against saying something.
            log.debug("account passed over for budget notifications currentAccountId={} "
                            + "month={} categories={} reason=no category on it carries a figure in "
                            + "this month",
                    currentAccountId, thisMonth.month(), thisMonth.categories().size());
            return List.of();
        }
        LocalDate theMonthBegan = thisMonth.from();
        Set<AnAnnouncedBudgetMonth> alreadyRaised =
                whichOfTheseCategoriesHaveAlreadyBeenSpokenAbout(budgeted, theMonthBegan);
        log.debug("the budgets standing on a current account currentAccountId={} customerId={} "
                        + "month={} theMonthBegan={} categoriesBudgeted={} allowed={} spent={} "
                        + "runningLowAtShareOfWhatIsAllowed={} alreadyRaised={}",
                currentAccountId, customerId, thisMonth.month(), theMonthBegan, budgeted.size(),
                asMoney(thisMonth.allowed()), asMoney(thisMonth.spent()),
                shareOfWhatAMonthAllows, alreadyRaised.size());
        List<Notification> raising = new ArrayList<>();
        for (WhatACategoryCostInAMonth category : budgeted) {
            Optional<NotificationReason> toSay = WhenABudgetIsRunningOut.whatThereIsToSayAbout(
                    category, shareOfWhatAMonthAllows);
            // The inputs behind the decision, before it is taken: what the month allows, what has
            // been spent, where the quieter line falls, and what the record already holds about this
            // category in this month. A reviewer redoes the comparison from this line, and it is the
            // line that shows the once-only rule holding on every night after the first.
            log.debug("the transition check behind a budget warning currentAccountId={} "
                            + "categoryId={} name={} month={} budgeted={} carriedIn={} allowed={} "
                            + "committed={} discretionary={} spent={} left={} runningLowAt={} "
                            + "overspent={} wouldSay={} alreadySaidRunningLow={} "
                            + "alreadySaidOverspent={}",
                    currentAccountId, category.categoryId(), category.name(), thisMonth.month(),
                    asMoney(category.budgeted()), asMoney(category.carriedIn()),
                    asMoney(category.allowed()), asMoney(category.committed()),
                    asMoney(category.discretionary()), asMoney(category.spent()),
                    asMoney(category.left()),
                    asMoney(WhenABudgetIsRunningOut.whereAMonthStartsRunningLow(
                            category, shareOfWhatAMonthAllows)),
                    category.isOverspent(), toSay.orElse(null),
                    hasAlreadyBeenSaid(alreadyRaised, category,
                            NotificationReason.A_BUDGET_IS_RUNNING_LOW),
                    hasAlreadyBeenSaid(alreadyRaised, category,
                            NotificationReason.A_BUDGET_HAS_BEEN_OVERSPENT));
            if (toSay.isEmpty()) {
                // Which is every category still comfortably inside its month, and every category
                // whose month allows nothing at all and has cost nothing at all.
                log.debug("category passed over for a budget notification currentAccountId={} "
                                + "categoryId={} name={} allowed={} spent={} reason=it has not "
                                + "crossed either line",
                        currentAccountId, category.categoryId(), category.name(),
                        asMoney(category.allowed()), asMoney(category.spent()));
                continue;
            }
            NotificationReason reason = toSay.get();
            if (reason == NotificationReason.A_BUDGET_IS_RUNNING_LOW
                    && hasAlreadyBeenSaid(alreadyRaised, category,
                            NotificationReason.A_BUDGET_HAS_BEEN_OVERSPENT)) {
                // The louder line stands for the month. A category told it had gone over, and later
                // brought back under by a correction or by a budget its holder raised, is not told
                // afterwards that it is merely running low: that would be a warning walking
                // backwards, and the record is a log of the nights it was written on.
                log.debug("category passed over for a budget notification currentAccountId={} "
                                + "categoryId={} name={} allowed={} spent={} reason=this month has "
                                + "already been called overspent, and the quieter line is not said "
                                + "after the louder one",
                        currentAccountId, category.categoryId(), category.name(),
                        asMoney(category.allowed()), asMoney(category.spent()));
                continue;
            }
            // Added rather than asked, so that the set carries what this run has decided as well as
            // what the record already held — which is also what the database's own index refuses.
            if (!alreadyRaised.add(
                    new AnAnnouncedBudgetMonth(category.categoryId(), reason))) {
                // Which is every crossed category on the second run of a night, and on every night
                // to the end of the month after the one it crossed on. One category, one month, one
                // warning — this is the line a reviewer reads to confirm it.
                log.debug("category passed over for a budget notification currentAccountId={} "
                                + "categoryId={} name={} month={} reason={} reason=this has already "
                                + "been said about this category in this month",
                        currentAccountId, category.categoryId(), category.name(),
                        thisMonth.month(), reason);
                continue;
            }
            // The figures behind the decision, before the row is made: which category, which month,
            // what it allowed and what it cost — the three things the budget screen can be held
            // against.
            log.debug("a budget is worth saying something about currentAccountId={} categoryId={} "
                            + "name={} month={} allowed={} spent={} left={} reason={}",
                    currentAccountId, category.categoryId(), category.name(), thisMonth.month(),
                    asMoney(category.allowed()), asMoney(category.spent()),
                    asMoney(category.left()), reason);
            raising.add(reason == NotificationReason.A_BUDGET_HAS_BEEN_OVERSPENT
                    ? Notification.aBudgetHasBeenOverspent(customerId, currentAccountId,
                            category.categoryId(), category.name(), theMonthBegan,
                            category.allowed(), category.spent(), now)
                    : Notification.aBudgetIsRunningLow(customerId, currentAccountId,
                            category.categoryId(), category.name(), theMonthBegan,
                            category.allowed(), category.spent(), now));
        }
        return raising;
    }

    /**
     * The quietest of the three and the widest: this account's month is promised to more than the
     * month has.
     *
     * <p>What the bills and the arrears still want plus what the budgets still allow, against the
     * balance and the income still due to arrive. {@link WhenAMonthIsOverCommitted} argues out why
     * both sides are counted forwards, why each category is floored at nothing before it is added
     * up, and why an account with nothing budgeted in the month is passed over rather than told
     * something its bills already say.
     *
     * <p><strong>Once per account per month, on the crossing.</strong> An account promised to more
     * than it holds on the third is promised to more than it holds on every night to the
     * thirty-first, and the record's own question — has this been said about this account in this
     * month — is the whole of the transition check. The next month is a new promise and is worth its
     * own line, which is what "climbing out and falling back in" means for a figure that is only
     * ever about one month.
     *
     * <p>One notification at most per account per sweep, which is why this answers with an
     * {@link Optional} where the category rule answers with a list: an account has one month.
     */
    private Optional<Notification> whatThisMonthsPromisesHaveToSay(
            long currentAccountId, long customerId, WhatAnAccountSpentInAMonth thisMonth,
            Instant now) {
        LocalDate theMonthBegan = thisMonth.from();
        if (thisMonth.allowed() == null) {
            // Which is every account whose holder has put a figure on nothing. Said out loud rather
            // than skipped silently, because a warning that never fires on an account should be
            // explainable without anybody having to read the rule.
            log.debug("account passed over for an over-committed month currentAccountId={} "
                            + "month={} reason=nothing is budgeted on it in this month, and a plan "
                            + "nobody has made cannot be bigger than the month",
                    currentAccountId, thisMonth.month());
            return Optional.empty();
        }
        TheMonthAhead monthAhead = accounts.theMonthAheadOn(currentAccountId);
        WhenAMonthIsOverCommitted.ThePromise promise =
                WhenAMonthIsOverCommitted.of(monthAhead, thisMonth);
        boolean alreadySaid = notifications.existsByCurrentAccountIdAndReasonAndOccursOn(
                currentAccountId, NotificationReason.THE_MONTH_IS_OVER_COMMITTED, theMonthBegan);
        // The inputs behind the decision, before it is taken: both sides of the comparison, every
        // figure they were made of, and whether this has already been said about this month. A
        // reviewer redoes the arithmetic from this line, and it is the line that shows the once-only
        // rule holding on every night after the first.
        log.debug("the transition check behind the over-committed warning currentAccountId={} "
                        + "customerId={} month={} theMonthBegan={} balance={} incomeDue={} "
                        + "billsDue={} arrearsOutstanding={} budgetsStillClaim={} promisedTo={} "
                        + "hasGot={} isOverCommitted={} alreadySaidThisMonth={}",
                currentAccountId, customerId, thisMonth.month(), theMonthBegan,
                asMoney(monthAhead.balance()), asMoney(monthAhead.incomeDue()),
                asMoney(monthAhead.billsDue()), asMoney(monthAhead.arrearsOutstanding()),
                asMoney(promise.budgetsStillClaim()), asMoney(promise.promisedTo()),
                asMoney(promise.hasGot()), promise.isOverCommitted(), alreadySaid);
        if (!promise.isOverCommitted()) {
            // Which is every account whose month covers what it has promised — so this is the line
            // that says a comfortable account was looked at rather than skipped.
            log.debug("account passed over for an over-committed month currentAccountId={} "
                            + "month={} promisedTo={} hasGot={} reason=the month covers what it has "
                            + "been promised to",
                    currentAccountId, thisMonth.month(), asMoney(promise.promisedTo()),
                    asMoney(promise.hasGot()));
            return Optional.empty();
        }
        if (alreadySaid) {
            // Which is every night after the one the crossing was announced on, and every night a
            // further spend makes the same month worse. One month, one warning.
            log.debug("account passed over for an over-committed month currentAccountId={} "
                            + "month={} promisedTo={} hasGot={} reason=this month has already been "
                            + "called over-committed, and it is one month",
                    currentAccountId, thisMonth.month(), asMoney(promise.promisedTo()),
                    asMoney(promise.hasGot()));
            return Optional.empty();
        }
        return Optional.of(Notification.theMonthIsOverCommitted(customerId, currentAccountId,
                theMonthBegan, promise.promisedTo(), promise.hasGot(), now));
    }

    /**
     * Which of these categories this module has already spoken about in this month, and under which
     * reason — in one question rather than one per category, the same bargain every other memory in
     * this sweep strikes.
     */
    private Set<AnAnnouncedBudgetMonth> whichOfTheseCategoriesHaveAlreadyBeenSpokenAbout(
            List<WhatACategoryCostInAMonth> budgeted, LocalDate theMonthBegan) {
        List<Long> categoryIds = budgeted.stream()
                .map(WhatACategoryCostInAMonth::categoryId)
                .toList();
        return notifications
                .budgetWarningsAlreadyRaisedFor(
                        categoryIds, NotificationReason.THE_BUDGET_REASONS, theMonthBegan)
                .stream()
                .map(said -> new AnAnnouncedBudgetMonth(said.getCategoryId(), said.getReason()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Whether one thing has already been said about one category in the month being judged. Asked of
     * the set the loop is also adding to, so that the record and what this run has already decided
     * are one memory rather than two that could disagree.
     */
    private static boolean hasAlreadyBeenSaid(Set<AnAnnouncedBudgetMonth> alreadyRaised,
                                              WhatACategoryCostInAMonth category,
                                              NotificationReason reason) {
        return alreadyRaised.contains(
                new AnAnnouncedBudgetMonth(category.categoryId(), reason));
    }

    /**
     * The newest occurrence this module has already announced on an account, which is where
     * tonight's read of that account's failures starts.
     *
     * <p>Zero when it has never announced one, which is every account until the first transfer on it
     * does not happen — and identifiers start at one, so zero is a floor no row can sit on rather
     * than a value that has to be handled separately.
     *
     * <p>A place rather than a set, and that is what makes it a bound. Occurrences are written in
     * one sequence and read back in it, so everything this module has said anything about is behind
     * the newest thing it has said something about, and the read that matters is the one in front of
     * it. That rests on an occurrence identifier never being handed out twice: they are identity
     * values on a SQLite {@code integer primary key} without {@code AUTOINCREMENT}, so the sequence
     * only ever climbs while nothing deletes a {@code rule_occurrence} row — and nothing does, an
     * ended rule keeps its history. A delete would let SQLite reuse the top identifier and hand this
     * place a row it has never seen but that sits behind the mark, so if occurrences ever become
     * deletable this bound needs {@code AUTOINCREMENT} or a mark that is not an identifier.
     *
     * <p>The set of what has already been announced is still asked for — it is what keeps "once"
     * true if this place is ever wrong, and it is what stops one run announcing a row twice — but it
     * is now asked about tonight's handful rather than about every failure an account has ever had.
     *
     * <p><strong>Nothing is caught up from behind this place.</strong> An occurrence this module has
     * passed over — one settled before what the account was short was recorded — stays passed over
     * once anything newer has been announced, rather than being refused afresh every night for the
     * rest of the application's life. That is deliberate: the refusal is permanent, so saying it
     * once an account has moved on is a record, and saying it nightly is noise.
     *
     * <p>Not capped to a number of occurrences per night, although the catch-up that writes them is.
     * A cap here would be read from the front of this same place, so a run whose first occurrences
     * are all ones that cannot be announced would leave the place where it found it and read the
     * same unannounceable rows again next night, never reaching the ones behind them. Bounding the
     * read to what has not been said is the half of it that is safe; drip-feeding a backlog needs a
     * record of where a run stopped that is not the record of what was said.
     */
    private long whereThisAccountWasLeftOff(long savingsAccountId) {
        Long newest = notifications.theNewestOccurrenceAlreadyAnnouncedOn(savingsAccountId);
        return newest == null ? NOTHING_HAS_BEEN_ANNOUNCED_YET : newest;
    }

    /**
     * Which of these occurrences have already been announced as transfers that did not happen.
     *
     * <p>One question for the whole account rather than one per occurrence, and read as a set the
     * loop can both ask and add to — so the check against the record and the check against what this
     * run has already decided are one check rather than two that could disagree.
     */
    private Set<Long> whatHasAlreadyBeenAnnouncedAbout(List<RecordedOccurrence> couldNotBeHonoured) {
        List<Long> occurrenceIds = couldNotBeHonoured.stream().map(RecordedOccurrence::id).toList();
        return notifications.whichOccurrencesHaveAlreadyBeenAnnounced(occurrenceIds).stream()
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Which of these due dates this module has already announced as ones that could not be paid, in
     * one question rather than one per date — the same bargain the anniversary read strikes.
     */
    private Set<AnAnnouncedDueDate> whichOfTheseDueDatesHaveAlreadyBeenAnnounced(
            List<ABillThatCouldNotBePaid> fellShort) {
        List<Long> billIds = fellShort.stream()
                .map(ABillThatCouldNotBePaid::billId)
                .distinct()
                .toList();
        return notifications
                .billDatesAlreadyAnnouncedFor(billIds, NotificationReason.A_BILL_COULD_NOT_BE_PAID)
                .stream()
                .map(said -> new AnAnnouncedDueDate(said.getBillId(), said.getOccursOn()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Which anniversaries of these deposits have already been announced, and under which reason.
     *
     * <p>Read in one question for the whole account rather than one per deposit, and read as a set
     * the loop can both ask and add to — so the check against the record and the check against what
     * this run has already decided are one check rather than two that could disagree.
     */
    private Set<AnAnnouncedAnniversary> whatHasAlreadyBeenAnnouncedFor(
            List<DepositStillHoldingMoney> holding) {
        List<Long> depositIds = holding.stream().map(DepositStillHoldingMoney::id).toList();
        return notifications
                .anniversariesAlreadyAnnouncedFor(
                        depositIds, NotificationReason.THE_ANNIVERSARY_REASONS)
                .stream()
                .map(said -> new AnAnnouncedAnniversary(
                        said.getDepositId(), said.getReason(), said.getOccursOn()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * What this account's term has to say tonight, which is one line the published notice period
     * before it is up and nothing on any other night.
     *
     * <p><strong>Before the maturity morning and never on it or after it.</strong> What happens when
     * a term is up was agreed at the beginning — {@code MaturitiesService} rolls it, moves it to
     * instant access or leaves it waiting, at a quarter past three, by the version the account was
     * opened under — and this sweep runs at four. A notification raised on the morning itself would
     * be a letter telling somebody what had already been decided on their behalf, and a term left
     * waiting keeps its passed maturity date for the rest of its life, so it would be that letter
     * every night for ever. {@link AMaturityComingSoon} holds both edges of the window and argues
     * why it has two where the anniversary window has one.
     *
     * <p><strong>Once per maturity date rather than once per account.</strong> The twelve-month
     * fixed term rolls, so an account that holds one has a maturity every year and every one of
     * those years is a decision worth putting in front of its holder. The day is therefore part of
     * what makes the announcement unique, kept by an index over the account and the day — and it is
     * the reason a check on the account alone would have been wrong in a way nobody would notice
     * until the second year.
     *
     * <p><strong>The date is Products' and is never worked out here.</strong> A second maturity
     * calendar would disagree with the gate that refuses withdrawals and with the sweep that settles
     * maturities, on precisely the dates that are hard, and the disagreement would be a customer
     * warned about a morning that was not the one their money actually moved on.
     *
     * <p>Nothing at all for an account with no agreement on record, which is a database the start-up
     * migration has not reached; nothing for a closed account, which has no term left to run; and
     * nothing for the three products of the four that have no term at all, which arrive here with a
     * null maturity date and are passed over before anything else is read.
     *
     * <p>How far ahead the window reaches arrives rather than being read here, and it is tonight's:
     * the notice period is a figure the scheme publishes, and it is a figure of its own rather than
     * the anniversary's read twice. Two columns in the scheme for what used to be two constants,
     * deliberately, so that shortening the loyalty warning cannot silently shorten the notice a
     * customer gets before a year of their money is rolled into another year of it.
     *
     * @param daysBeforeAMaturityIsWorthSaying how many days before a maturity it is worth saying so,
     *                                         from the scheme in force tonight
     */
    private Optional<Notification> whatThisAccountsTermHasToSay(
            long savingsAccountId, long customerId, Optional<TheAgreementAnAccountIsOn> agreement,
            int daysBeforeAMaturityIsWorthSaying, Instant now) {
        if (agreement.isEmpty()) {
            log.debug("account passed over for a maturity notification savingsAccountId={} "
                    + "reason=no agreement has been written for it", savingsAccountId);
            return Optional.empty();
        }
        TheAgreementAnAccountIsOn itIsOn = agreement.get();
        if (itIsOn.isClosed() || itIsOn.maturesOn() == null) {
            // Which is every account on free savings, the notice account and the core saver, and
            // every account whose holder has closed it — so this is the line that says a sweep
            // raising nothing about a maturity had no term to raise one about.
            log.debug("account passed over for a maturity notification savingsAccountId={} "
                            + "product={} closedOn={} maturesOn={} reason=it is not on a term that "
                            + "is still running", savingsAccountId, itIsOn.productCode(),
                    itIsOn.closedOn(), itIsOn.maturesOn());
            return Optional.empty();
        }
        LocalDate maturesOn = itIsOn.maturesOn();
        if (!AMaturityComingSoon.isWorthSayingAsAt(maturesOn, now,
                daysBeforeAMaturityIsWorthSaying)) {
            log.debug("account passed over for a maturity notification savingsAccountId={} "
                            + "product={} maturesOn={} today={} "
                            + "daysBeforeAMaturityIsWorthSaying={} "
                            + "worthSayingUpToAndIncluding={} "
                            + "reason=its maturity is further off than the notice period tonight's "
                            + "scheme publishes, or the day has come and the maturity has already "
                            + "been settled",
                    savingsAccountId, itIsOn.productCode(), maturesOn,
                    AMaturityComingSoon.theDayItIs(now), daysBeforeAMaturityIsWorthSaying,
                    AMaturityComingSoon.theLastDayWorthSayingAsAt(now,
                            daysBeforeAMaturityIsWorthSaying));
            return Optional.empty();
        }
        if (notifications.existsBySavingsAccountIdAndReasonAndOccursOn(
                savingsAccountId, NotificationReason.A_TERM_IS_ABOUT_TO_MATURE, maturesOn)) {
            // Which is every account on every one of the thirty nights after the first. One
            // maturity, one notification — and the next year's is a different day and a new line.
            log.debug("account passed over for a maturity notification savingsAccountId={} "
                            + "maturesOn={} reason=this maturity has already been announced",
                    savingsAccountId, maturesOn);
            return Optional.empty();
        }
        // The figures behind the decision, before the row is made: which product, which day, and
        // how far off it is, so that a reviewer can check by hand that the window fired on the
        // values it claims.
        log.debug("a term coming up for maturity is worth saying savingsAccountId={} "
                        + "customerId={} product={} productName={} version={} maturesOn={} "
                        + "today={}",
                savingsAccountId, customerId, itIsOn.productCode(), itIsOn.productName(),
                itIsOn.version(), maturesOn, AMaturityComingSoon.theDayItIs(now));
        return Optional.of(Notification.aTermIsAboutToMature(customerId, savingsAccountId,
                itIsOn.productCode(), itIsOn.productName(), maturesOn, now));
    }

    /**
     * The notice standing on this account, or none at all for an account that has nothing to give
     * notice of.
     *
     * <p>Read here rather than inside the rule, so that the count reaches the one line the sweep
     * logs about itself — the same arrangement the deposits still holding money have.
     *
     * <p>The agreement is asked first and the notices are not read at all when it says nought days,
     * which is free savings, the core saver and the twelve-month fixed term: three of the four
     * products this bank sells have nothing to give notice of, and a question asked of the notice
     * table for each of their accounts every night is a question whose answer can only ever be
     * empty. An account with no agreement on record is the same case for the same reason.
     */
    private List<NoticeGiven> whatIsStandingOn(long savingsAccountId,
                                               Optional<TheAgreementAnAccountIsOn> agreement) {
        if (agreement.isEmpty() || agreement.get().noticeDays() == 0) {
            log.debug("account passed over for notice notifications savingsAccountId={} "
                            + "reason=its agreement asks for no notice, so there is none to run",
                    savingsAccountId);
            return List.of();
        }
        TheNoticeOnAnAccount itsNotice = notices.noticeOn(savingsAccountId);
        return itsNotice.notices();
    }

    /**
     * Everything this account's notice has to say tonight, which is one line per notice that has run
     * its days and has not been spoken about.
     *
     * <p><strong>Ready is Products' answer and is never worked out here.</strong> Whether notice has
     * run is the day it was given plus the days the agreement asks for, against the application's
     * clock, decided in {@code WhenANoticeIsReady} and nowhere else — and nothing is stored about
     * it, which is that module's deliberate choice. This rule reads the yes or no and decides only
     * whether it has already said it.
     *
     * <p><strong>Once per notice, and the notice is the key rather than the day.</strong> Two
     * notices given on one morning come free on one morning and are two separate amounts, each
     * spent separately against a withdrawal, so a day-keyed record would quietly merge the second
     * into the first. The database keeps it too: {@code one_notification_per_notice} refuses a
     * second row naming the same notice.
     *
     * <p><strong>Said the first night the sweep sees it ready rather than only on the day
     * itself.</strong> Ready notice does not lapse, so a notice whose day passed while nothing was
     * running is still standing, still unspent and still something its holder has not been told. On
     * an application whose job runs nightly the first night the sweep sees it <em>is</em> the day,
     * which is what the criterion asks for; on one that was switched off for a fortnight, saying
     * nothing because the day has technically gone would be the one silence a customer would call a
     * bug. The same reading {@link AnAnniversaryComingSoon} takes of an anniversary that is already
     * owed.
     *
     * <p>What it carries is what the notice still covers rather than what it was given on: a notice
     * on EUR 500 that has already paid for a withdrawal of EUR 200 is good for EUR 300 tonight, and
     * that is the figure the customer can act on. The act itself is in the notice's own history.
     *
     * <p>A notice spent to nothing is not in this listing at all — a reading is what a customer
     * still has — so one whose money left before any sweep saw it is never announced, which is the
     * honest answer.
     */
    private List<Notification> whatThisAccountsNoticeHasToSay(
            long savingsAccountId, long customerId, List<NoticeGiven> standing, Instant now) {
        if (standing.isEmpty()) {
            return List.of();
        }
        Set<Long> alreadyAnnounced = whichNoticesHaveAlreadyBeenAnnounced(standing);
        log.debug("the notice standing on a savings account savingsAccountId={} customerId={} "
                        + "standing={} alreadyAnnounced={}",
                savingsAccountId, customerId, standing.size(), alreadyAnnounced.size());
        List<Notification> raising = new ArrayList<>();
        for (NoticeGiven notice : standing) {
            if (!notice.ready()) {
                log.debug("notice passed over for a notification noticeId={} readyOn={} "
                                + "daysLeft={} reason=its days have not run yet",
                        notice.id(), notice.readyOn(), notice.daysLeft());
                continue;
            }
            // Added rather than asked, so that the set carries what this run has decided as well as
            // what the record already held — which is also what the database's own index refuses.
            if (!alreadyAnnounced.add(notice.id())) {
                // Which is every ready notice on every night after the one it came free on, for as
                // long as its money is unspent. One notice, one notification.
                log.debug("notice passed over for a notification noticeId={} readyOn={} "
                                + "reason=this notice has already been announced as ready",
                        notice.id(), notice.readyOn());
                continue;
            }
            log.debug("notice that has run its days is worth saying noticeId={} "
                            + "savingsAccountId={} givenOn={} readyOn={} amount={} "
                            + "stillStanding={}",
                    notice.id(), savingsAccountId, notice.givenOn(), notice.readyOn(),
                    asMoney(notice.amount()), asMoney(notice.stillStanding()));
            raising.add(Notification.aNoticeHasBecomeReady(customerId, savingsAccountId,
                    notice.id(), notice.readyOn(), notice.stillStanding(), now));
        }
        return raising;
    }

    /**
     * What this account's product has to say tonight, which is one line when it has published terms
     * that better the ones the account is living under, and nothing at all otherwise.
     *
     * <p><strong>The judgement is this module's and the words are Products'.</strong> That split is
     * the whole point of this rule and it is the instruction ticket 11 left behind.
     * {@code ProductsService.theNewerTermsFor} answers which version the account is on, which
     * version is on offer and what differs between the two — as sentences, from the one function in
     * this application that words a difference between two agreements. It says that the rate goes
     * from 0.60% a year to 0.50% a year and it deliberately never says whether that is better,
     * because a comparison with an opinion in it is how "nothing adopts newer terms on your behalf"
     * quietly becomes "and we decided this one was an improvement". Free savings' seeded second
     * version <em>cut</em> the rate, so that is not a hypothetical. The opinion is made here, in
     * {@link WhenTermsHaveBeenBettered}, where it can be read and argued with, and the sentences are
     * quoted rather than rebuilt.
     *
     * <p><strong>Once per version, and an account that has taken the newer terms is never told
     * about them again.</strong> The second half falls out of the first without a rule of its own:
     * an account that presses the button is <em>on</em> the version it was told about, so the
     * product has nothing newer to offer it, {@code newerTermsExist} is false and this rule stops
     * before it reads a figure. A version published later is a new version and earns its own line if
     * it too betters what the account then holds.
     *
     * <p><strong>Nothing is moved and nothing is recommended.</strong> A notification is an
     * invitation to read: it carries both headline rates and every sentence the comparison produced,
     * including the ones about figures that moved against the customer, and the press stays theirs.
     *
     * <p>Nothing for an account with no agreement on record and nothing for a closed one — a closed
     * account takes no more money and has no agreement left to improve, so telling its former holder
     * that the product has repriced would be an advertisement rather than a notification.
     */
    private Optional<Notification> whatThisAccountsProductHasToSay(
            long savingsAccountId, long customerId, Optional<TheAgreementAnAccountIsOn> agreement,
            Instant now) {
        if (agreement.isEmpty() || agreement.get().isClosed()) {
            log.debug("account passed over for a bettered-terms notification savingsAccountId={} "
                            + "reason=it has no agreement on record, or the agreement has ended",
                    savingsAccountId);
            return Optional.empty();
        }
        Optional<TheNewerTermsOnOffer> offered = products.theNewerTermsFor(savingsAccountId);
        if (offered.isEmpty() || !offered.get().newerTermsExist()) {
            // Which is every account already on the version its product is selling — including
            // every account whose holder has pressed the button, which is why "not told again" needs
            // no rule of its own.
            log.debug("account passed over for a bettered-terms notification savingsAccountId={} "
                            + "product={} version={} reason=its product is selling the very version "
                            + "it is on", savingsAccountId, agreement.get().productCode(),
                    agreement.get().version());
            return Optional.empty();
        }
        TheNewerTermsOnOffer newer = offered.get();
        Map<Integer, ASetOfTerms> published = everyVersionOf(newer.productCode());
        ASetOfTerms whatYouAreOn = published.get(newer.theVersionYouAreOn());
        ASetOfTerms whatIsOnOffer = published.get(newer.theVersionOnOfferToday());
        if (whatYouAreOn == null || whatIsOnOffer == null) {
            // A broken record rather than a customer's mistake: an account can only ever be put on a
            // version that was published, and the version on offer is read out of the same history.
            // Said at WARN rather than thrown, because one impossible account must not stop a sweep
            // that has every other customer to get through.
            log.warn("account refused a bettered-terms notification savingsAccountId={} product={} "
                            + "theVersionYouAreOn={} theVersionOnOfferToday={} versionsPublished={} "
                            + "reason=one of the two versions being compared is not in the "
                            + "product's own history, so there is nothing to judge",
                    savingsAccountId, newer.productCode(), newer.theVersionYouAreOn(),
                    newer.theVersionOnOfferToday(), published.size());
            return Optional.empty();
        }
        BigDecimal theRateYouAreOn = WhenTermsHaveBeenBettered.theHeadlineRateOf(whatYouAreOn);
        BigDecimal theRateOnOffer = WhenTermsHaveBeenBettered.theHeadlineRateOf(whatIsOnOffer);
        if (!WhenTermsHaveBeenBettered.isBettered(whatYouAreOn, whatIsOnOffer)) {
            // The live case, and the one the seeded catalogue was built to show: free savings'
            // version 2 cut the rate from 0.60% to 0.50%, so every account still on version 1 has
            // newer terms on offer and is told nothing about them. Said with both rates, so that a
            // reviewer can see that the silence was a judgement rather than an omission.
            log.debug("account passed over for a bettered-terms notification savingsAccountId={} "
                            + "product={} theVersionYouAreOn={} theVersionOnOfferToday={} "
                            + "theRateYouAreOn={} theRateOnOffer={} reason=the version on offer "
                            + "does not better the headline rate of the version the account holds",
                    savingsAccountId, newer.productCode(), newer.theVersionYouAreOn(),
                    newer.theVersionOnOfferToday(), theRateYouAreOn, theRateOnOffer);
            return Optional.empty();
        }
        if (whichVersionsHaveAlreadyBeenAnnouncedOn(savingsAccountId)
                .contains(newer.theVersionOnOfferToday())) {
            // Which is every account on every night after the one the version was first announced
            // on. Once per version, however long the account stays on the older one.
            log.debug("account passed over for a bettered-terms notification savingsAccountId={} "
                            + "product={} theVersionOnOfferToday={} reason=this version has "
                            + "already been announced", savingsAccountId, newer.productCode(),
                    newer.theVersionOnOfferToday());
            return Optional.empty();
        }
        // The figures behind the decision and, above all, the two rates it was taken on — the
        // judgement is the load-bearing call in this rule and it should not have to be inferred
        // from the fact that a row came out.
        log.debug("a product that has bettered an account's terms is worth saying "
                        + "savingsAccountId={} customerId={} product={} productName={} "
                        + "theVersionYouAreOn={} theVersionOnOfferToday={} theRateYouAreOn={} "
                        + "theRateOnOffer={} whatWouldChange={}",
                savingsAccountId, customerId, newer.productCode(), newer.productName(),
                newer.theVersionYouAreOn(), newer.theVersionOnOfferToday(), theRateYouAreOn,
                theRateOnOffer, newer.whatWouldChange());
        return Optional.of(Notification.termsHaveBeenBettered(customerId, savingsAccountId,
                newer.productCode(), newer.productName(), newer.theVersionOnOfferToday(),
                theRateOnOffer, theRateYouAreOn, newer.whatWouldChange(), now));
    }

    /**
     * Every version that product has published, by version number, so that the judgement can read
     * the two agreements it is about.
     *
     * <p>Through the history rather than through a read of its own, because the history is the only
     * public door onto a version that is not the one being sold today: the account is on an older
     * one by definition, or there would be nothing newer to announce. The list also carries the
     * sentences each version changed, which this rule does not use — it quotes the ones
     * {@code theNewerTermsFor} worded for the step the account would actually take, which is a
     * different pair of versions whenever the account is more than one version behind.
     */
    private Map<Integer, ASetOfTerms> everyVersionOf(String productCode) {
        return products.everyVersionOf(productCode).stream()
                .map(AVersionAndWhatItChanged::terms)
                .collect(Collectors.toMap(ASetOfTerms::version, terms -> terms));
    }

    /**
     * Which of these standing notices this module has already announced as ready, in one question
     * for the account rather than one per notice — the same bargain the anniversary read strikes,
     * and read as a set the loop can both ask and add to.
     */
    private Set<Long> whichNoticesHaveAlreadyBeenAnnounced(List<NoticeGiven> standing) {
        List<Long> noticeIds = standing.stream().map(NoticeGiven::id).toList();
        return notifications.whichNoticesHaveAlreadyBeenAnnounced(noticeIds).stream()
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Which versions of its product this account has already been told about, which is how "once per
     * version" is kept in Java as well as in the index that guarantees it.
     */
    private Set<Integer> whichVersionsHaveAlreadyBeenAnnouncedOn(long savingsAccountId) {
        return Set.copyOf(notifications.whichVersionsHaveAlreadyBeenAnnouncedOn(
                savingsAccountId, NotificationReason.A_PRODUCT_HAS_BETTERED_YOUR_TERMS));
    }

    /**
     * Which rung the newest thing said about an account's balance says it was standing on.
     *
     * <p>The sweep's whole memory, and the reason the record has to be readable as a position rather
     * than as an event. A row saying a rung was reached says the balance stood on that rung; a row
     * saying a rung was lost says it stood on the rung below that one; no row at all says it stood
     * on none, which is where every account starts.
     *
     * <p><strong>Read against tonight's ladder, and the row is never touched.</strong> What somebody
     * was told is what they were told: a notification naming EUR 500 goes on naming EUR 500 after
     * the bank has stopped publishing EUR 500 as a rung, because an inbox is a log of the nights it
     * was written on. What changes is only how tonight's sweep reads it — as a position on the
     * ladder it is comparing against, which is the only ladder a comparison can be made on.
     *
     * @param rungs the balance rungs the scheme in force tonight publishes, under which the row is
     *              read back — total in all three answers, which is precisely what makes a row
     *              written against a ladder nobody publishes any more still readable
     */
    private Optional<BigDecimal> theRungThatWasLastSaidToBeStoodOn(Optional<Notification> lastSaid,
                                                                   TheBalanceRungs rungs) {
        if (lastSaid.isEmpty()) {
            return Optional.empty();
        }
        Notification said = lastSaid.get();
        if (said.getReason() == NotificationReason.BALANCE_THRESHOLD_LOST) {
            return rungs.theRungBelow(said.getAmount());
        }
        // Read back through the ladder rather than taken at face value, so that a rung tonight's
        // ladder no longer has — which is now a Monday away rather than a release away — is read as
        // the highest rung the figure does reach instead of as a position that no longer exists.
        return rungs.theRungStoodOnWith(said.getAmount());
    }

    /**
     * How the rung an account stands on now compares with the rung it was last said to stand on:
     * positive for higher, negative for lower, zero for the same.
     *
     * <p>Standing on no rung at all is a position and it is the lowest one, which is what lets an
     * account that has never been told anything be announced on the first sweep and an account
     * whose balance has fallen off the ladder altogether be told it has.
     */
    private int howTheRungHasMoved(Optional<BigDecimal> standsOn, Optional<BigDecimal> stoodOn) {
        if (standsOn.isEmpty() && stoodOn.isEmpty()) {
            return 0;
        }
        if (stoodOn.isEmpty()) {
            return 1;
        }
        if (standsOn.isEmpty()) {
            return -1;
        }
        return standsOn.get().compareTo(stoodOn.get());
    }

    /**
     * An amount quoted to the cent for a log line, and null left as null: the figures a notification
     * carries are per-reason, and a line reading {@code amount=null} says which family the row is
     * from.
     */
    private static String asMoney(BigDecimal amount) {
        return amount == null
                ? null
                : amount.setScale(DECIMAL_PLACES_IN_MONEY, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * One anniversary that has been announced: the deposit, the reason it was announced under and
     * the day.
     *
     * <p>The key uniqueness is judged on, in Java and in the database's own index, and the same
     * three columns in both places. The reason belongs in it because the two anniversary reasons are
     * an escalation rather than two names for one statement: a deposit told it is shielded, and
     * later told it is first in line, has said two different things about one day and both are worth
     * having.
     *
     * <p>A record so that equality is the three values, which is the whole of what it is for.
     */
    private record AnAnnouncedAnniversary(long depositId, NotificationReason reason,
                                          LocalDate occursOn) {
    }

    /**
     * One due date this module has already said could not be paid: the bill and the day. The pair
     * the record's own unique index is over, and there is one reason in this family so the reason is
     * not part of it.
     */
    private record AnAnnouncedDueDate(long billId, LocalDate dueOn) {
    }

    /**
     * One thing this module has already said about one category in the month being judged: the
     * category and the reason.
     *
     * <p>The month is not in the key although it is in the index, and deliberately: every question
     * this set is asked is a question about one month, because the set was read for that month
     * alone. Carrying it would be carrying the same value on every element of the set.
     *
     * <p>The reason <em>is</em> in it, because the two budget reasons are a sequence rather than two
     * spellings of one statement — and because the sweep has to be able to ask whether the louder
     * one has been said before it decides whether the quieter one may be.
     */
    private record AnAnnouncedBudgetMonth(long categoryId, NotificationReason reason) {
    }

    /**
     * The five figures one sweep runs under, taken off one published version of the scheme, plus the
     * two facts that say which version they came from.
     *
     * <p><strong>A value rather than five parameters threaded through the walk</strong>, and it
     * carries the version so that the line reporting it and the line closing the sweep name the same
     * one. Each rule is still handed only the figure it is about — the ladder to the balance rule,
     * the share to the budgets, the count to the arrears, a notice period each to the maturity and
     * the anniversary — because a rule handed the whole scheme is a rule that could quietly start
     * reading a second figure out of it, which is how the one place a threshold is decided becomes
     * two. This exists to be read once and taken apart, not to be passed down.
     *
     * <p>Built here rather than by each rule for the same reason: the ladder is assembled once and
     * the percentage is converted once, at the top, where a reader can see both happen.
     *
     * <p>Private and a record, because equality of its values is all it is: two sweeps that ran
     * under the same figures ran under the same figures.
     */
    private record TheFiguresASweepRunsUnder(int version, LocalDate effectiveFrom,
                                             TheBalanceRungs rungs,
                                             BigDecimal shareOfWhatAMonthAllows,
                                             int howManyOutstandingIsASpiral,
                                             int daysBeforeAMaturityIsWorthSaying,
                                             int daysBeforeAnAnniversaryIsWorthSaying) {

        /**
         * One published version of the scheme as the five figures this module reads out of it.
         *
         * <p>The two conversions live here and nowhere else. The euro amounts become a ladder
         * through {@link TheBalanceRungs#theRungsIn}, and the published percentage becomes the
         * fraction a budget is compared against through
         * {@link WhenABudgetIsRunningOut#theShareThatIs} — which is the named place those two units
         * meet, and the reason this is a factory rather than a constructor call at the call site.
         * The other three figures are counts in the units the scheme already publishes them in, and
         * converting a count would be inventing a unit it does not have.
         */
        static TheFiguresASweepRunsUnder in(TheSchemeAsPublished scheme) {
            return new TheFiguresASweepRunsUnder(
                    scheme.version(),
                    scheme.effectiveFrom(),
                    TheBalanceRungs.theRungsIn(scheme),
                    WhenABudgetIsRunningOut.theShareThatIs(scheme.whatShareOfABudgetIsRunningLow()),
                    scheme.howManyOutstandingIsASpiral(),
                    scheme.daysBeforeAMaturityIsWorthSaying(),
                    scheme.daysBeforeAnAnniversaryIsWorthSaying());
        }
    }
}
