package io.dataroots.savingstreak.notifications;

import java.util.EnumSet;
import java.util.Set;

/**
 * Why a notification was raised: the rules that have decided something was worth saying.
 *
 * <p>An enum, and public, for two reasons that pull the same way. The web layer sends the reason as
 * its name and the browser writes the sentence, because every euro and every date in this
 * application is formatted in the page and a sentence composed in Java would fork that formatting
 * into a second place that drifts. And a later feature — points about to expire, a reward newly
 * affordable — adds a value here without touching any of the machinery that stores, sweeps or
 * reports a notification.
 *
 * <p>A reason names a rule rather than a severity. Whether a notification is a warning or a
 * congratulation is a reading of it, and the page is where that reading belongs.
 */
public enum NotificationReason {

    /**
     * A savings balance stands on a rung of {@link TheBalanceRungs} it was not last known to
     * stand on, and the new rung is the higher one. The rung landed on is the notification's
     * {@code amount}.
     */
    BALANCE_THRESHOLD_REACHED,

    /**
     * A savings balance no longer reaches a rung it did. The notification's {@code amount} is the
     * lowest rung the balance no longer reaches, so that reading the record backwards says exactly
     * where the balance now stands — see {@link NotificationsService}.
     */
    BALANCE_THRESHOLD_LOST,

    /**
     * A deposit's next anniversary falls within the notice period the scheme in force publishes —
     * {@link AnAnniversaryComingSoon} is the window and {@code daysBeforeAnAnniversaryIsWorthSaying}
     * is its length — is worth at least one point, and the deposit stands behind an older one still
     * holding money. The day and
     * the points are the notification's {@code occursOn} and {@code points}.
     *
     * <p>The calm one of the pair. A withdrawal drains the oldest deposit first, so the euros in
     * this one are behind at least one other deposit's worth of money: the anniversary is coming
     * and nothing a single withdrawal does reaches it first.
     */
    LOYALTY_BONUS_ABOUT_TO_PAY,

    /**
     * The same anniversary, on the deposit that is first in line for the next withdrawal — the
     * oldest one in the account still holding money.
     *
     * <p>The warning of the pair, and the reason this feature exists. A bonus is worked out on what
     * the deposit still holds at the moment its anniversary is judged, so the next euro withdrawn
     * from this account comes out of exactly the money this anniversary would have been paid on.
     * {@code WithdrawalsService} has always drained the oldest deposit first and calls that a
     * protection; this is that protection said out loud to the person it protects.
     */
    LOYALTY_BONUS_AT_RISK,

    /**
     * A saving rule fell due and the current account did not hold what it asked for, so nothing
     * moved. The day it was due is the notification's {@code occursOn} and what the account was
     * short is its {@code amount}; the occurrence it is about is its {@code occurrenceId}.
     *
     * <p>The one reason in this enum that is about something the application did <em>not</em> do,
     * and the only one of the three automation outcomes worth saying anything about. A transfer that
     * worked is precisely what a customer automated their saving in order to stop being told about,
     * and a sweep that found nothing above its floor moved nothing because the arithmetic said
     * nothing — announcing that as a failure would train a customer to ignore the thing that tells
     * them about the real ones.
     *
     * <p>Raised by this module's own sweep reading Automation's record, an hour after the rules job
     * has settled the night, rather than by the rules job itself. One producer of notifications is
     * the whole point of {@code NotificationsAreRaisedNightly}; a second writer in a second module
     * would split the one place this feature logs.
     */
    AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN,

    /**
     * A recurring bill fell due on a current account that did not hold what it asked for, so nothing
     * at all was taken and the date stays owed. The bill and its name are the notification's
     * {@code billId} and {@code billName}, the day it was owed is its {@code occursOn}, what it
     * asked for is its {@code amount}, and what the account actually held is its {@code balance};
     * the account it happened on is its {@code currentAccountId}.
     *
     * <p><strong>Once per due date, on the transition into unpaid, and never again.</strong> A rent
     * owed for six months is presented on six nightly runs and refused on all six; a reason raised
     * on the state rather than on the transition would put six identical lines in front of a
     * customer who has one problem, and an inbox holding forty copies of one line is worse than no
     * notification at all. The record's own unique index over the bill, the reason and the day is
     * what makes that a guarantee rather than an intention.
     *
     * <p>The money is two figures and not one, unlike {@link #AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN},
     * which sends only the shortfall. A customer who is told what the rent asked for and what was
     * actually there can see both halves of the decision and knows what to move back out of savings;
     * a single "short by" figure hides the balance it was measured against.
     */
    A_BILL_COULD_NOT_BE_PAID,

    /**
     * A current account's unpaid bills have crossed into being a spiral rather than a bad month:
     * three or more dates are outstanding at once, or what they come to altogether has passed the
     * monthly income the customer declared. Either condition alone is the trigger — one enormous
     * unpaid bill is as much trouble as three small ones. How many are outstanding is the
     * notification's {@code arrears} and what they come to is its {@code amount}.
     *
     * <p><strong>The louder warning, and raised on the crossing rather than on the condition.</strong>
     * An account that has stood over the threshold every night since March crossed it once, in March,
     * and gets one notification. It is said a second time only when the account has come back under
     * the threshold and crossed it again — which is what keeps it meaning something, and is the same
     * bargain {@link #BALANCE_THRESHOLD_REACHED} strikes with a rung.
     *
     * <p>About an account rather than about one bill: the number worth acting on is the whole hole,
     * and no single date is to blame for the account having three of them. So it names no bill and
     * carries no {@code occursOn}.
     */
    BILLS_ARE_PILING_UP,

    /**
     * A spending category's month has crossed the share of what it allows — the budget <em>and</em>
     * the carry — that the scheme in force publishes as running low, without crossing all of it.
     * {@link WhenABudgetIsRunningOut} is the rule and {@code whatShareOfABudgetIsRunningLow} is the
     * figure it is asked at. The category and its name are
     * the notification's {@code categoryId} and {@code categoryName}, the month is its
     * {@code occursOn} as the day that month began, what the month allows is its {@code amount} and
     * what has been spent against it is its {@code balance}; the account it is about is its
     * {@code currentAccountId}.
     *
     * <p><strong>Measured against what the month allows and never against what was budgeted.</strong>
     * A category carrying forty euros forward is allowed forty euros more this month, and a warning
     * drawn against the bare figure would call a month four-fifths gone that is nothing of the kind —
     * and, on an envelope carrying a deficit, would fail to call one that is already over. The
     * allowance is the figure the customer is really spending out of, so it is the figure the
     * fraction is taken of.
     *
     * <p><strong>Once per category per month, on the crossing.</strong> A category four-fifths gone
     * on the tenth is four-fifths gone on every night to the thirty-first, and a reason raised on
     * that state would put the same line in front of its holder twenty times about one thing they
     * have already been told. The month is part of what makes it unique precisely because a new
     * month is a new allowance: a category that crosses again in May has climbed out — the month
     * ended and the budget started again — and fallen back in, and that is worth saying a second
     * time. The record's own unique index over the category, the reason and the day the month began
     * is what makes that a guarantee rather than an intention.
     *
     * <p><strong>Never said after {@link #A_BUDGET_HAS_BEEN_OVERSPENT} has been said about the same
     * category and month.</strong> The two are mutually exclusive by their own definitions — this
     * one is the crossing of four fifths <em>without</em> the crossing of all of it — so a category
     * that goes straight past its limit in one night is called overspent and is never called
     * running low, and a correction that later brings it back to nine tenths does not earn it the
     * quieter line afterwards. A warning that got louder and then got softer about one month would
     * be a warning nobody could act on. {@link NotificationsService} argues that out.
     *
     * <p><strong>It is a record of what was said on a night, not a claim about what is true
     * now.</strong> Every figure in the budgets module is derived on every read, so a correction
     * that moves a spend's euros out of this category empties the very month this warning is about
     * — and the warning is not retracted, because nothing in this record ever is. Both halves are
     * right: the inbox is a log of the nights it was written on, and the budget screen is what is
     * true now. {@code Spend} and {@code SpendsService.correctTheSplitOf} say the same thing from
     * the other side, because the tension is the first thing a reviewer reading a June inbox
     * against a June budget will flag and the answer is not obvious from either side alone.
     */
    A_BUDGET_IS_RUNNING_LOW,

    /**
     * The same category's month has crossed all of what it allows: the budget and the carry together
     * are less than what has been spent. It carries the same figures as
     * {@link #A_BUDGET_IS_RUNNING_LOW} — the category, its name, the month as {@code occursOn}, what
     * the month allows as {@code amount} and what has been spent as {@code balance} — because it is
     * the same statement about the same month, further along.
     *
     * <p><strong>The louder of the pair, and the one that wins when both would be true.</strong> A
     * customer who spends three hundred euros against a two-hundred-euro allowance in one night has
     * crossed four fifths and all of it between two sweeps; saying both would put a line saying
     * "running low" beside a line saying "you are over" about one category on one night, and the
     * first of those is false as soon as the second is true. So the crossing of all of it is what is
     * said, the quieter reason is not raised at all, and it is not raised later either.
     *
     * <p><strong>Nothing refuses a spend for being over a budget.</strong> A budget is a plan and
     * the balance is the only hard constraint in this application, so this reason reports something
     * that has already happened rather than something that was stopped — which is why it is worth
     * raising before the month ends, when slowing down still helps, rather than at a close this
     * feature does not have.
     *
     * <p>Once per category per month, on the crossing, kept by the same index and for the same
     * reasons as its quieter twin — and, like its twin, never retracted by a later correction that
     * empties the month it was about.
     */
    A_BUDGET_HAS_BEEN_OVERSPENT,

    /**
     * What this current account has promised for the month it is in comes to more than the month
     * has: its bills and arrears still to be met, plus what its budgets still allow, exceed its
     * balance plus the income still due to arrive. What it is promised to is the notification's
     * {@code amount} and what it has is its {@code balance}; the month is its {@code occursOn} as
     * the day that month began, and the account it is about is its {@code currentAccountId}.
     *
     * <p><strong>About the account and not about any one category</strong>, so it names none: the
     * figure worth acting on is the whole promise, and no single word the customer files their money
     * under is to blame for there being more of them than the month can pay for. The same shape
     * {@link #BILLS_ARE_PILING_UP} has, for the same reason.
     *
     * <p><strong>Both sides are counted forwards, which is the whole of the arithmetic.</strong>
     * What has already been spent this month has already left the balance, so the budgets claim what
     * is <em>left</em> of them rather than the whole of what they allow; counting the whole
     * allowance against a balance the month's groceries have already come out of would count those
     * groceries twice and would raise this on every account that has ever spent anything. The bills
     * are counted the same way and by the same read — what is still to fall, plus what is already
     * owed — which is why this is taken from {@code TheMonthAhead} rather than assembled here out of
     * a second walk of the same calendars.
     *
     * <p><strong>An account with nothing budgeted in the month is passed over.</strong> This reason
     * is about a plan being bigger than the month, and an account whose holder has made no plan has
     * nothing of the kind to say: bills and arrears alone outrunning the balance is what
     * {@link #A_BILL_COULD_NOT_BE_PAID} and {@link #BILLS_ARE_PILING_UP} already say, and the month
     * card on the account's own screen has been drawing that shortfall in red since before this
     * feature existed.
     *
     * <p><strong>Once per account per month, on the crossing.</strong> An account promised to more
     * than it holds on the third is promised to more than it holds on every night to the
     * thirty-first, and saying so nightly would be the inbox of forty identical lines that
     * {@link #BILLS_ARE_PILING_UP}'s own documentation argues against. It is said again in the next
     * month the crossing happens in, because a month is the thing it is about and a new month is a
     * new promise. It is not retracted when a correction or a superseded budget later makes the
     * month affordable, for the reason the two above it are not.
     */
    THE_MONTH_IS_OVER_COMMITTED,

    /**
     * A customer's turn in a waiting list came: something they were queued for came back into
     * stock and the rewards scheme has put one aside for them. Which offer it is and what it is
     * called are the notification's {@code offerCode} and {@code offerTitle}, and the moment the
     * hold runs out is its {@code lapsesAt}.
     *
     * <p><strong>The one reason in this enum that is unambiguously good news, and the only one
     * raised outside this module's own nightly sweep.</strong> Both of those follow from the
     * same fact: it is about a deadline that has just started running. A hold lasts
     * seventy-two hours from the instant the rewards sweep creates it, so a customer told at
     * four the following morning — the next time this module's sweep runs, twenty-three hours
     * after the rewards sweep at five — would have lost a third of it to a schedule. Every
     * other reason here is about a standing state a customer loses nothing by hearing about
     * half a day late. The writing is still this module's: Rewards states what happened
     * through an interface it declares, and a component in this package answers it. The
     * argument is on {@code rewards.TellingAPromotedWaiter}.
     *
     * <p><strong>A hold and never a claim, which is the whole of what the sentence has to
     * get across.</strong> No points have been spent and no voucher exists. What the customer
     * has is three days in which the last one is theirs, and what they have to do is claim it
     * or give it up — so the deadline is on the record rather than left to the rewards page,
     * because a notification saying "one is being kept for you" with no "until when" is the
     * deadline nobody saw coming that this whole feature exists to avoid.
     *
     * <p><strong>Not once per anything — once per promotion.</strong> Unlike the bill, budget
     * and arrears reasons, there is nothing here to raise on a crossing and nothing to say
     * twice: a promotion is an event that happens once to one place in one queue, and a
     * customer who joins a queue again months later and is promoted again has been promoted
     * twice and should be told twice. So there is no index keeping this unique and no cursor
     * remembering where the sweep got to; the row is written in the same transaction as the
     * hold it is about.
     *
     * <p>A moment rather than a day, unlike every other deadline this module records. Seventy-two
     * hours is not a calendar quantity, and rounding it to a date would make the record promise
     * something other than what the hold says — the argument is {@code TheShelfLifeOfAHold}'s,
     * repeated here because this is the second place the same moment is written down.
     */
    A_REWARD_IS_BEING_HELD_FOR_YOU,

    /**
     * A fixed term is within the notice period the scheme in force publishes of the day it is up —
     * {@link AMaturityComingSoon} is the window and {@code daysBeforeAMaturityIsWorthSaying} is its
     * length. The account is the notification's
     * {@code savingsAccountId}, the day it matures is its {@code occursOn}, and the product it is a
     * term of is its {@code productCode} and {@code productName}.
     *
     * <p><strong>Before the day and never on it, which is the whole reason it exists.</strong> What
     * happens on a maturity morning was agreed at the beginning — a rolling term rolls, a term set
     * to move goes to instant access, a term set to wait stops being locked — and
     * {@code MaturitiesService} does it at a quarter past three without asking anybody. A customer
     * who wants a different ending has to say so before that morning, so a notification raised on
     * the morning itself would be a letter telling somebody what had already been decided. Thirty
     * days is {@link AMaturityComingSoon}'s, and that class argues the length.
     *
     * <p><strong>Once per maturity date rather than once per account.</strong> A rolling term has a
     * new maturity every year and every one of them is a decision worth putting in front of its
     * holder, so the day is part of what makes the announcement unique — the same bargain
     * {@link #LOYALTY_BONUS_ABOUT_TO_PAY} strikes with an anniversary, for the same reason.
     *
     * <p>No money on it. What the term holds is on the account's own panel and is true now, where
     * this record is a log of a night; the figure worth acting on is a date, and the spec's own
     * criterion asks for the figure <em>or</em> the date it is about.
     */
    A_TERM_IS_ABOUT_TO_MATURE,

    /**
     * Notice given on an amount has run its days and the money may be taken. The account is the
     * notification's {@code savingsAccountId}, the notice is its {@code noticeId}, the day it came
     * free is its {@code occursOn} and what it still covers is its {@code amount}.
     *
     * <p><strong>Once per notice, and the notice is what makes it unique rather than the day.</strong>
     * Two notices given a week apart on one account come free a week apart and are two things to
     * say; two notices given on one morning come free on one morning and are still two things to
     * say, because each of them covers its own amount and each is spent separately. A day-keyed
     * announcement would silently merge the second into the first.
     *
     * <p><strong>Said the first night the sweep sees it ready, which is the day itself on an
     * application whose job runs nightly, and the first night afterwards on one that was switched
     * off.</strong> Ready notice does not lapse — that is
     * {@code WhenANoticeIsReady}'s promise — so a notice whose day passed while nothing was running
     * is still a thing the customer has not been told and still a thing they can act on. The same
     * reading {@link AnAnniversaryComingSoon} takes of an anniversary that is already owed: one
     * boundary and not two.
     *
     * <p>A notice spent to nothing before it was ever announced is never announced, because it
     * stops standing and stops being read. That is the honest answer: the money has already left.
     */
    A_NOTICE_HAS_BECOME_READY,

    /**
     * The product an account is on has published a version whose headline rate beats the one the
     * account is living under. The account is the notification's {@code savingsAccountId}, the
     * product is its {@code productCode} and {@code productName}, the version on offer is its
     * {@code termsVersion}, the rate on offer is its {@code amount} and the rate the account is on
     * is its {@code balance}; what the two agreements say differently is its {@code whatIsDifferent}.
     *
     * <p><strong>The only reason in this enum that carries an opinion, and the opinion is this
     * module's own.</strong> {@code WhatIsDifferentBetweenTwoSetsOfTerms} says that the rate moved
     * from 0.60% to 0.50% and deliberately never says whether that is better — free savings' seeded
     * second version <em>cut</em> the rate, so a comparison with an opinion in it would have been an
     * application recommending a worse agreement to everybody it had ever repriced. Which moves
     * count as better is argued out in the open, in {@link WhenTermsHaveBeenBettered}, where it can
     * be read and disagreed with.
     *
     * <p><strong>Once per version, and never again once the account has taken them.</strong> An
     * account that presses the button is on the version it was told about, so the product has
     * nothing newer to offer it and there is nothing left to say; a further version published later
     * is a new version and earns its own line if it too betters what the account then holds.
     *
     * <p><strong>Two rates and a list of sentences, rather than a sentence of this module's
     * own.</strong> The rates are the two halves of the judgement and are what the page leads with;
     * the sentences are quoted from the one function in this application that words a difference
     * between two agreements, so a customer reading this notice and then the product's version
     * history meets the same words twice. A second wording here would be the first place the two
     * disagree about one rate cut.
     *
     * <p><strong>It is an invitation to read and never a recommendation.</strong> Nothing in this
     * application adopts a version on anybody's behalf, and the quoted sentences list every figure
     * that moved — including the ones that moved against the customer, which a version with a
     * better rate and a longer notice period really does have.
     */
    A_PRODUCT_HAS_BETTERED_YOUR_TERMS,

    /**
     * A saving rule fell due and the savings account it pays into had been closed, so there was
     * nowhere for the money to go. The occurrence is the notification's {@code occurrenceId}, the
     * day it was due is its {@code occursOn}, and the account it was pointing at is its
     * {@code savingsAccountId}.
     *
     * <p><strong>Its own reason rather than a second reading of
     * {@link #AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN}</strong>, for the reason
     * {@code OccurrenceOutcome.THE_ACCOUNT_IS_CLOSED} is its own outcome: one of them says top the
     * current account up before next time, and the other says this rule is pointing at an account
     * that will never take money again, so change it or end it. Telling a customer they were short
     * would send them to fix an account that was never the problem, and the shortfall beside it
     * would have to be nought or a lie.
     *
     * <p><strong>No money on it at all, which is the shape of the fact.</strong> The current
     * account held whatever it held and none of it was the reason; the occurrence itself carries
     * nought moved and no shortfall, and inventing a figure here would be this module saying
     * something the record does not.
     *
     * <p>Once per occurrence, kept by the same index and read through the same cursor as its
     * louder twin, because both are written into Automation's one record of what happened.
     */
    AN_AUTOMATIC_TRANSFER_HAD_NOWHERE_TO_GO;

    /**
     * The reasons that are about a savings balance rather than about a deposit, which is the set the
     * sweep asks the record for when it wants to know which rung an account was last known to stand
     * on. Written down here because it is a property of the reasons themselves, and a caller
     * listing them by hand would be a second list to keep in step with this one.
     */
    static final Set<NotificationReason> THE_BALANCE_REASONS =
            EnumSet.of(BALANCE_THRESHOLD_REACHED, BALANCE_THRESHOLD_LOST);

    /**
     * The reasons that are about one deposit's anniversary, which is the set the sweep asks the
     * record for when it wants to know which anniversaries it has already announced.
     *
     * <p>Mutually exclusive for one deposit and one anniversary: a deposit is either the oldest one
     * in its account still holding money or it is not. Both can nevertheless stand against the same
     * deposit and the same day over time, and that is the point — a deposit shielded behind an older
     * one, whose shield is then emptied, is first in line for the next withdrawal and is announced
     * again under the other reason. That escalation is why the reason is part of what makes an
     * announcement unique rather than something the record overwrites.
     */
    static final Set<NotificationReason> THE_ANNIVERSARY_REASONS =
            EnumSet.of(LOYALTY_BONUS_ABOUT_TO_PAY, LOYALTY_BONUS_AT_RISK);

    /**
     * The reasons that are about one spending category's month, which is the set the sweep asks the
     * record for when it wants to know which of tonight's categories it has already spoken about in
     * this month. Written down here for the reason the two sets above are: it is a property of the
     * reasons themselves, and a caller listing them by hand would be a second list to keep in step
     * with this one.
     *
     * <p>Both of them together rather than one question each, because the pair is judged as a pair:
     * the quieter one is not raised about a category and month the louder one has already been
     * raised about, so the sweep has to see both to decide either.
     */
    static final Set<NotificationReason> THE_BUDGET_REASONS =
            EnumSet.of(A_BUDGET_IS_RUNNING_LOW, A_BUDGET_HAS_BEEN_OVERSPENT);
}
