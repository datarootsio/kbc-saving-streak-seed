export type Customer = {
  id: number
  name: string
  /** The address they sign in with. Shown on the sign-in screen as a shortcut for demonstrations. */
  contactDetails: string
}

/** An everyday account: what it is called, and what is in it. */
export type CurrentAccount = {
  id: number
  iban: string
  balance: number
}

/**
 * A savings account in an overview: worth the money in it, and on one of the bank's savings
 * products.
 *
 * <p>The product is on the overview and not only on the page behind it, because the overview is
 * where somebody holding two accounts finds out whether they are the same kind of account. The name
 * is what a card prints; the code is what everything else is addressed by, so a page that wanted to
 * group or link by product never has to match on words meant for a person.
 *
 * <p>Both are `null` for an account the backend has recorded no agreement for, which is a database
 * that has not been through its start-up migration. A card then simply says nothing about a
 * product, rather than inventing one.
 *
 * <p>`closedOn` is the day the account was closed and `null` while it is still open. A closed
 * account stays on this list — the money history on the same screen reads every euro that moved
 * through it, and an account that vanished would leave those euros attributed to nothing — so this
 * date is what tells a card it is drawing a record rather than somewhere to put money.
 */
export type SavingsAccount = {
  id: number
  moneyBalance: number
  productCode: string | null
  productName: string | null
  /** A plain `YYYY-MM-DD`, and `null` for an account that is still open. */
  closedOn: string | null
}

/**
 * What one savings account is living under: the product it is on, the version of that product's
 * terms it was opened with, the day it was opened on them, and the one condition that product
 * attaches.
 *
 * <p>**This is not what the product is selling today, and the difference is the whole point.** Free
 * savings has published a second version at a lower rate, and every account opened before it goes
 * on under the first for as long as its holder wants it. A page that drew the catalogue's figures
 * under the heading "Your agreement" would be stating the exact confusion this reading exists to
 * remove, which is why the agreement comes down with the account rather than being looked up beside
 * it.
 *
 * <p>The condition arrives in all of its shapes with the absences said as absences: `noticeDays` is
 * `0` for an account with nothing to give notice of, `minimumBalance` is `0` for one with no floor
 * to keep, and `maturesOn` is `null` for one that never matures. One reading per rule, so the panel
 * renders "nothing to give notice of, nothing to keep in" from the figures rather than from a guess
 * about the kind.
 *
 * <p>There is no rate here, and that is the backend's decision rather than an omission: interest is
 * paid monthly and every payment names the rate it was paid at beside the balance it was worked out
 * from, which is where a rate is something a customer can check. `version` is the address of the
 * agreement that decides it.
 */
export type TheAgreement = {
  productCode: string
  productName: string
  /** One of `INSTANT_ACCESS`, `NOTICE`, `FIXED_TERM`, `MINIMUM_BALANCE`. */
  productKind: string
  version: number
  /** A plain `YYYY-MM-DD`, as the backend's own zone read it. */
  openedOn: string
  noticeDays: number
  minimumBalance: number
  /** A plain `YYYY-MM-DD`, and `null` for an account that is not on a term at all. */
  maturesOn: string | null
  /**
   * The day the account was closed, as `YYYY-MM-DD`, and `null` while it is still open.
   *
   * <p>Everything else on this agreement goes on reading after it — the product, the version, the
   * day it began — because all of it is still what the money in the history below lived under.
   * Closing ends an agreement; it deletes nothing, here or in the backend.
   */
  closedOn: string | null
}

/**
 * What a customer holds, what their saving has earned them, and how the saving is going.
 *
 * <p>`pointsBalance` is the customer's own figure and sits beside the two lists rather than inside
 * either of them: points are earned by paying into any of these savings accounts and spent on
 * rewards, and they belong to the person rather than to one account. It is the backend's figure,
 * summed there from everything they have earned less everything they have claimed.
 *
 * <p>So are the week and the run of weeks. A week counts what the customer put away wherever they
 * put it, so there is one week in progress and one run behind it however many accounts they keep —
 * the same six figures a savings account's own endpoint reports, under the same names, because they
 * are the same figures read for the same person.
 *
 * <p>What the week asks for comes down with the progress towards it, for the reason
 * {@link SavingsAccountBalances} gives: the €50 a week costs is the backend's figure, and a page
 * that wrote it into its own markup would be a second place it lived.
 */
export type CustomerAccounts = {
  pointsBalance: number
  /**
   * The most the customer has ever had in savings, across every account they hold.
   *
   * <p>The mark a deposit is judged against: euros above it are new saving and earn points, euros
   * below it have been saved once already and were paid for then. It never falls, so taking money
   * out leaves it exactly where it was — which is what makes a withdrawal cost nothing in points and
   * what stops the same euros from earning twice.
   *
   * <p>Theirs rather than any one account's, like the points balance: a euro moved between two of
   * their own savings accounts is not new saving in either.
   */
  mostEverSaved: number
  /**
   * How many of those points are the next to expire, and `null` when there are none left to lose.
   *
   * <p>Null rather than zero, and the two are different statements: "nothing expires next" is true of
   * somebody who has never earned anything, and "zero points expire on the 14th" is not true of
   * anybody. A page that showed a 0 would be inventing a deadline.
   */
  pointsExpiringNext: number | null
  /**
   * The day those points go — their twelve-month anniversary — as `YYYY-MM-DD`, and `null` when
   * there are none.
   *
   * <p>A plain calendar day rather than a moment, decided by the backend in the one timezone this
   * application counts calendars in. A moment would have had to be turned into a day here, in the
   * zone of whatever machine is drawing the screen, and a customer in London would have been shown
   * a deadline a day early.
   */
  pointsExpiringNextOn: string | null
  /**
   * Net new saving since Monday, across every account they hold: everything paid into savings during the week
   * less everything taken back out of it during the week.
   *
   * <p>Net, because a week is what the customer has actually put away by the end of it — money paid
   * in on Monday and taken back out on Tuesday has been put away by nobody, and counting it would
   * let the same fifty euros secure a week for ever.
   *
   * <p>It can be negative, in a week where more came out than went in. That is the honest figure and
   * the one `stillNeededThisWeek` is worked out from: somebody EUR 50 down on the week needs EUR 100
   * before it counts, not EUR 50.
   */
  newSavingsThisWeek: number
  weeklyMinimum: number
  /** What the week still asks for, and never below zero. */
  stillNeededThisWeek: number
  /** Consecutive weeks the customer has secured, and zero once the run has lapsed. */
  currentStreakWeeks: number
  /** The longest run they have ever had, which a lapse does not erase. */
  bestStreakWeeks: number
  /** What a whole euro paid in earns right now, as a multiple of a point. */
  currentMultiplier: number
  currentAccounts: CurrentAccount[]
  savingsAccounts: SavingsAccount[]
}

/**
 * Why the backend said no, in its own words. Every error it answers with carries the reason in
 * `detail` (RFC 9457), and it is passed on untouched: what may and may not be done is decided in one
 * place, and rewording its answer here would be this page deciding a little of it too. A response
 * carrying no reason falls back to naming what failed, so nothing ever fails silently.
 */
async function reasonRefused(response: Response, whenNoneGiven: string): Promise<string> {
  return theReasonIn(await theProblemIn(response), response.status, whenNoneGiven)
}

/**
 * The problem document itself, or nothing when there was not one to read.
 *
 * <p>Split out of {@link reasonRefused} rather than duplicated inside it, because a response body
 * can only be read once and a caller that wants more than the sentence — the simulator wants the
 * column and the change a refusal is about, which the backend hangs beside `detail` as RFC 9457
 * extension members — cannot read it a second time to find them. Every caller still gets its
 * sentence from the one function that decides what a sentence is.
 */
async function theProblemIn(response: Response): Promise<{ detail?: unknown } | null> {
  try {
    const problem: unknown = await response.json()
    return (problem ?? null) as { detail?: unknown } | null
  } catch {
    // A body that is not JSON says no more than the status already did.
    return null
  }
}

/** The sentence in a problem document, or a fallback naming what failed. */
function theReasonIn(
  problem: { detail?: unknown } | null,
  status: number,
  whenNoneGiven: string,
): string {
  const reason = problem?.detail
  if (typeof reason === 'string' && reason.trim() !== '') {
    return reason
  }
  return `${whenNoneGiven} (${status})`
}

/**
 * A sign-in that did not happen, and whether the backend was the one saying so.
 *
 * <p>The two are different answers and a caller acts differently on them. "No customer banks here
 * under that address" is about the address; a request that never got an answer at all — a backend
 * still starting up, a connection that dropped — says nothing about the address, and treating the
 * second as the first is how somebody gets signed out by a hiccup.
 */
export class SignInFailed extends Error {
  readonly addressRejected: boolean

  constructor(reason: string, addressRejected: boolean) {
    super(reason)
    this.name = 'SignInFailed'
    this.addressRejected = addressRejected
  }
}

/**
 * Signs in, which is the backend recognising the address and answering with the customer it belongs
 * to. It is not authentication and nothing here pretends it is: no token comes back, nothing is sent
 * on later requests, and the customer this returns is remembered by the browser alone.
 *
 * <p>Sent in the body rather than in the URL, because a customer's address should not end up written
 * into a log or a browser history on its way here.
 */
export async function signIn(contactDetails: string): Promise<Customer> {
  const response = await fetch('/api/customers/sign-in', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ contactDetails }),
  })
  if (!response.ok) {
    // A refusal in the 400s is the backend having read the address and answered about it. Anything
    // else happened on the way there or inside, and is not an answer about the address at all.
    const aboutTheAddress = response.status >= 400 && response.status < 500
    throw new SignInFailed(await reasonRefused(response, 'Could not sign in'), aboutTheAddress)
  }
  return response.json()
}

// All backend endpoints sit under /api, which the dev server proxies to the backend.
export async function fetchCustomers(signal?: AbortSignal): Promise<Customer[]> {
  const response = await fetch('/api/customers', { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the customer list'))
  }
  return response.json()
}

/**
 * Opens a customer, so there is somebody new to bank as and somebody new to give points to.
 *
 * <p>Both fields go up exactly as they were typed. Whether a name of spaces is a name and whether
 * an address is one somebody already banks under are the backend's rules, and both come back as
 * sentences this page shows unchanged — including the one naming whoever already banks under the
 * address, which is the answer that tells the person adding a colleague that the colleague is
 * already here.
 *
 * <p>What comes back is the customer that now exists, carrying the identifier nothing on this page
 * could have known. The gift form selects them with it the moment they arrive.
 */
export async function addCustomer(name: string, contactDetails: string): Promise<Customer> {
  const response = await fetch('/api/customers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, contactDetails }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'They were not added'))
  }
  return response.json()
}

export async function fetchAccounts(
  customerId: number,
  signal?: AbortSignal,
): Promise<CustomerAccounts> {
  const response = await fetch(`/api/customers/${customerId}/accounts`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load this customer’s accounts'))
  }
  return response.json()
}

/**
 * A savings account and what it is worth, plus how far into this week's saving it has got and the run
 * of weeks behind it. Every figure is derived by the backend on every read and arrives here as a JSON
 * number: this page formats them and never works any of them out for itself.
 *
 * <p>What the week asks for comes down with the progress towards it. The €50 a week costs is the
 * backend's figure and is named in one place there; a page that wrote it into its own markup would
 * be the second place it lived, and the two would be one repricing away from disagreeing.
 */
export type SavingsAccountBalances = {
  id: number
  customerName: string
  moneyBalance: number
  /**
   * What this account is living under. The one thing on this reading that is about the account's
   * rules rather than its figures, and `null` only for a database that has not been through the
   * backend's start-up migration — the panel is then not drawn at all.
   */
  agreement: TheAgreement | null
  /**
   * Whether this account's product has published anything newer than the version above, and what
   * taking it would change.
   *
   * <p>It comes down with the agreement rather than from a fetch of its own, so that the panel
   * saying "version 1" and the panel saying "version 2 is on offer" cannot describe two different
   * moments. `null` for the same account the agreement is null for.
   *
   * <p>It is a reading and never an instruction. Nothing in this application moves an account onto
   * newer terms — newer is not the same as better, and free savings' second version cut the rate
   * from 0.60% to 0.50% — so the difference is shown and the press is the customer's.
   */
  newerTerms: TheNewerTerms | null
  /**
   * What the holder has to spend, which is not this account's figure but theirs: the same number is
   * reported beside every account they hold. Paying in here adds to it, which is why it is shown
   * beside this balance — what paying in *here* earned is on each deposit in the history.
   */
  pointsBalance: number
  /**
   * The most the customer has ever had in savings, across every account they hold.
   *
   * <p>The mark a deposit is judged against: euros above it are new saving and earn points, euros
   * below it have been saved once already and were paid for then. It never falls, so taking money
   * out leaves it exactly where it was — which is what makes a withdrawal cost nothing in points and
   * what stops the same euros from earning twice.
   *
   * <p>Theirs rather than any one account's, like the points balance: a euro moved between two of
   * their own savings accounts is not new saving in either.
   */
  mostEverSaved: number
  /**
   * How many of the holder's points go next, and `null` when there are none. Theirs rather than this
   * account's, like the balance above it: the twelve months run against their points.
   */
  pointsExpiringNext: number | null
  /**
   * The day those points reach their anniversary, as `YYYY-MM-DD`, and `null` when there are none.
   * The backend's day, for the reason {@link CustomerAccounts} gives.
   */
  pointsExpiringNextOn: string | null
  /**
   * Net new saving since Monday, counted in the backend's own timezone: everything paid into savings during the week
   * less everything taken back out of it during the week.
   *
   * <p>Net, because a week is what the customer has actually put away by the end of it — money paid
   * in on Monday and taken back out on Tuesday has been put away by nobody, and counting it would
   * let the same fifty euros secure a week for ever.
   *
   * <p>It can be negative, in a week where more came out than went in. That is the honest figure and
   * the one `stillNeededThisWeek` is worked out from: somebody EUR 50 down on the week needs EUR 100
   * before it counts, not EUR 50.
   */
  newSavingsThisWeek: number
  weeklyMinimum: number
  /**
   * What the week still asks for, and never below zero.
   *
   * <p>Part of the resource, and deliberately not what the cell reads: the figure it sits beside is
   * drawn while it is still climbing to what has landed, and a gap belonging to the figure it is
   * climbing towards would contradict the one on the screen for as long as the climb lasted. The
   * cell takes the same gap against the figure it is actually showing, and lands on this number.
   */
  stillNeededThisWeek: number
  /**
   * How many consecutive weeks this account has secured, counting back from the most recently
   * secured one — and zero once the run has lapsed, which the backend decides. A week counts once
   * `weeklyMinimum` of new saving has landed in it.
   */
  currentStreakWeeks: number
  /**
   * The longest run this account has ever had, which a lapse does not erase. Never smaller than
   * `currentStreakWeeks`: a run happening now is a run that has happened.
   */
  bestStreakWeeks: number
  /**
   * What a whole euro paid in earns right now, as a multiple of a point: 1.00 with no run behind the
   * account, a tenth more for each further consecutive secured week, and never past the backend's
   * cap. The rate the account is on, not a rate any past deposit was paid at — what a deposit was
   * actually paid was settled when it was made and travels with the deposit.
   */
  currentMultiplier: number
  /**
   * Which version of the published scheme decided `weeklyMinimum` and `currentMultiplier`: the one
   * in force on this week's Monday.
   *
   * <p>A name rather than a figure, and the answer to the only question the rate raises and cannot
   * settle — why is it 1.30×. Every version the bank has published is readable, each with the day it
   * took effect and one line saying what changed, so a rate that moved has a document behind it
   * rather than only a number that used to be different.
   *
   * <p>This week's version and no other. The weeks behind it were each judged under their own
   * Monday's version, which is what stops a repricing shortening a run somebody earned, and no
   * single number could name all of those.
   */
  schemeVersion: number
}

/**
 * A deposit that was made, what it has earned since, and when it next pays.
 *
 * <p>`pointsEarned` is everything it earned however it earned it, which is what it has always meant:
 * `basePoints`, `streakBonusPoints` and `loyaltyBonusPoints` are the three parts of that figure and
 * always add up to it, so nine points against a seven-euro deposit is an arithmetic a customer can
 * check rather than a number they have to take on trust.
 *
 * <p>`loyaltyBonusPoints` is every anniversary this deposit has been paid, added up. It is the one
 * figure here that grows after the money moved — a deposit left alone is paid a tenth of its euros
 * again every twelve months — so the total answers "what has this deposit been worth to me" while
 * the base and the streak bonus still answer "what did it earn when it landed", unchanged.
 *
 * <p>`nextAnniversaryOn` and `nextAnniversaryPoints` are the promise rather than the record: the day
 * this deposit next pays, and what that day is worth at what the deposit holds today. Three states,
 * and the page draws each of them differently because they say different things:
 *
 * - both `null`, and only ever together: the deposit has been emptied, and money that has gone has
 *   no anniversary left to reach. There is no promise to make, so the page makes none.
 * - a date with `0`: the deposit is still holding money, but under ten euros of it — a tenth of nine
 *   euros rounds down to nothing. The date is real and the figure is honest, which is a different
 *   statement from having no anniversary at all.
 * - a date with a figure: what leaving the money alone pays on that day, and what taking it out
 *   would cost. The figure falls when the customer withdraws from the deposit.
 *
 * <p>The date is the day this deposit next *pays*, which is not always the next date its calendar
 * reaches: between an anniversary falling and the overnight sweep paying it, the day reported is the
 * one just gone. A date in the past here means a bonus is owed and coming — under a day of it in
 * normal running, and arbitrarily long on a clock a trainer has wound forward without sweeping. That
 * is why the page never writes the word "next" in front of it and says "due" instead, which is true
 * on either side of the date and needs no opinion about what day it is today. This page has no such
 * opinion and wants none: the clock this promise is kept by is the application's, and the browser's
 * would disagree with it by years on a demonstration.
 *
 * <p>`productMultiplierApplied` is the part of that rate the account's savings product accounts for,
 * on its own: `1` where the product changes nothing, which is what free savings pays and what every
 * deposit made before there were products was paid. It is sent apart from the combined rate because
 * neither can be worked out from the other — 1.375 is 1.10 times 1.25 and equally 1.25 times 1.10 —
 * and because a customer reading a row is entitled to see which of the two schemes earned them
 * what: a run of weeks is something they did, and a product is something they chose.
 *
 * <p>Neither rate says which points came from which factor, and no field here does. The base points
 * are the euros and the streak bonus is the whole of the uplift over them, because two factors that
 * multiply have no shares to divide an uplift into.
 *
 * <p>`multiplierApplied` is the rate this deposit was in fact paid at, decided when the money moved
 * and never worked out again. Not the rate on `SavingsAccountBalances`, which is what the *next*
 * deposit will earn at: a run that has since lapsed leaves the two disagreeing, and both are right.
 * A deposit made before the scheme existed reports the ordinary 1.00, which is what it was paid.
 */
export type RecordedDeposit = {
  id: number
  amount: number
  /**
   * How much of the amount was new saving, and so how much of it earned.
   *
   * <p>The whole amount for anybody who has never taken money back out of savings, which is nearly
   * every deposit there is. Less when the deposit is filling a gap an earlier withdrawal left: those
   * euros earned their points the first time they were saved, and a euro saved twice is one euro.
   *
   * <p>It is what explains a deposit whose points look short, and it is sent on every deposit rather
   * than only on the short ones so that a page can say why without working anything out for itself.
   */
  newSavings: number
  pointsEarned: number
  basePoints: number
  streakBonusPoints: number
  loyaltyBonusPoints: number
  multiplierApplied: number
  /** What the account's savings product contributed to that rate, on its own. `1` changes nothing. */
  productMultiplierApplied: number
  /**
   * Which version of the account's product terms this deposit landed under: what was in force when
   * the money arrived, not what is in force now. The day an account takes its product's newer
   * terms, every row already in the history goes on naming the agreement it was priced under.
   *
   * `null` only for a deposit the backend has never stamped — one recorded before there were
   * agreements — because a version nobody wrote down is not a version and printing "v1" would be
   * inventing one.
   */
  termsVersion: number | null
  depositedAt: string
  /** A plain `YYYY-MM-DD`, as the backend's own zone read it. Null exactly when the deposit is empty. */
  nextAnniversaryOn: string | null
  /** Null exactly when `nextAnniversaryOn` is, and `0` for a deposit holding under ten euros. */
  nextAnniversaryPoints: number | null
}

/** Money returned from savings to a current account, newest first when read as history. */
export type RecordedWithdrawal = {
  id: number
  amount: number
  toCurrentAccountId: number
  withdrawnAt: string
}

export async function fetchSavingsAccount(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<SavingsAccountBalances> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load this savings account'))
  }
  return response.json()
}

/**
 * The deposits behind a savings account's balances, newest first. Every figure is the backend's:
 * this page adds nothing up for itself, which is what lets the list be checked against the balances.
 */
export async function fetchDeposits(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<RecordedDeposit[]> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/deposits`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load this account’s deposits'))
  }
  return response.json()
}

export async function fetchWithdrawals(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<RecordedWithdrawal[]> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/withdrawals`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load this account’s withdrawals'))
  }
  return response.json()
}

/**
 * Which of the two dated things a marker on an account's bar is.
 *
 * <p>A name rather than a flag saying whether it is good news. There are two dated rules today and
 * this application keeps growing them, and a boolean would have to be widened the first time a date
 * on the bar was neither a gain nor a loss.
 */
export type TimelineEventKind = 'POINTS_EXPIRE' | 'LOYALTY_BONUS'

/**
 * One dated thing a savings account has coming: the day, which kind it is, and how many points.
 *
 * <p>One marker per day per kind, already grouped by the backend: two deposits paying on one day are
 * one arrival, and points from four lots reaching their twelve months on one day are one departure,
 * because the bar has one position for that day. Which deposit is behind it is the history's answer
 * a row at a time, on the same screen.
 *
 * <p>Never worth nothing, so this page never has to decide whether a zero is worth drawing. An
 * anniversary that pays nothing is a fact about a deposit holding under ten euros; it is stated
 * beside that deposit in the history with the rule that explains it, and it is not a thing that
 * happens on a day.
 *
 * <p>`on` is a plain `YYYY-MM-DD`, for the reason {@link CustomerAccounts} gives about an expiry
 * day: which calendar day a moment falls on depends on the zone it is read in, and the backend has
 * already read it in the one zone this application counts calendars in.
 *
 * <p>It can be a day already gone, and that means something exact rather than being an error: the
 * promise fell and the sweep that keeps it runs overnight, so this is owed and is happening tonight.
 */
export type TimelineEvent = {
  on: string
  kind: TimelineEventKind
  points: number
}

/**
 * The year one savings account has ahead of it: the two days the bar is drawn between, and every
 * dated thing the deposits in that account have coming in between.
 *
 * <p>The window comes down from the backend, and this page must never work it out for itself. This
 * application's clock can be wound a year forward for a demonstration, so `new Date()` here would
 * draw a bar of a year nobody is in — every marker crowded off the right-hand end while the
 * application went on behaving as though it were next March. `from` is today as the application
 * reads it.
 *
 * <p>Everything in `events` belongs to the deposits made into *this* account — the anniversaries are
 * theirs, and so are the expiries, which are the points those deposits earned. It is the one place
 * in this API where points are reported per account rather than per customer, and what makes it
 * honest is that these are dates rather than balances: a date stays true however it is grouped,
 * while a balance repeated against every account would claim the customer held several of them. The
 * two customer-wide figures on {@link SavingsAccountBalances} are unchanged beside it.
 *
 * <p>Twelve months, and that is the whole of what is coming rather than the first screenful: nothing
 * survives longer than its own twelve months and nothing waits longer than a year to pay, so there
 * is nothing past the right-hand edge and the screen says so.
 *
 * <p>Nothing is added up, here or there. A point can be paid inside this window and expire inside it
 * too, so a net of the two kinds would count one point twice in opposite directions.
 */
export type AccountTimeline = {
  from: string
  until: string
  events: TimelineEvent[]
}

/** What the account has coming in the year ahead, read fresh whenever the account is. */
export async function fetchTimeline(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<AccountTimeline> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/timeline`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load what this account has coming'))
  }
  return response.json()
}

/**
 * One thing points can be spent on. Every word of it is the backend's, including the price: this page
 * renders a catalogue it knows nothing about, so a reward added or repriced server-side needs no
 * change here.
 */
export type Reward = {
  code: string
  title: string
  description: string
  costInPoints: number
}

/**
 * Where a voucher is in its life, as the backend names it.
 *
 * <p>Sent as a word rather than worked out here from a date or a flag, for the reason every other
 * status on these screens is: what makes a voucher good is the backend's rule, and a page that
 * decided for itself would eventually draw a green panel over something the counter endpoint
 * refuses. All four can be seen now: `EXPIRED` arrived with the nightly sweep and `CANCELLED`
 * with the screen that revokes one, and neither had to reopen a page that already handled the
 * whole set — which was the point of naming all four before any of them could happen.
 */
export type VoucherState = 'ISSUED' | 'USED' | 'EXPIRED' | 'CANCELLED'

/**
 * A reward that has been claimed, the voucher that came out of it, and where that voucher has got
 * to since.
 *
 * <p>`usedAt` and `usedByCounter` are one fact between them — what happened at the till — so they
 * are filled in together or not at all, and they are null for anything but a used voucher.
 *
 * <p>`expiresOn` is a plain `YYYY-MM-DD` and not a moment, because the backend has already decided
 * which day this voucher runs out — in the zone it counts calendars in, named once over there —
 * and a page that turned an instant into a date would be picking the zone of whatever machine it
 * is drawing on and telling somebody in London a deadline a day early. Null when the offer set no
 * shelf life, which is what all four of the offers this application ships say, so the field is
 * present and empty on almost every voucher and an absence means "this one does not run out".
 */
export type ClaimedReward = {
  id: number
  code: string
  title: string
  pointsSpent: number
  voucherCode: string
  claimedAt: string
  state: VoucherState
  usedAt: string | null
  usedByCounter: string | null
  expiresOn: string | null
  /**
   * When the voucher was revoked and why, in the words whoever runs the scheme typed. One fact
   * between them, like `usedAt` and `usedByCounter` above, and null for anything but a cancelled
   * voucher.
   *
   * <p>The reason is shown to the customer exactly as it arrives. A cancellation is the one
   * thing that happens to a claim which is nobody's doing but the scheme's, so a row that went
   * grey with nothing beside it would be the application telling somebody their voucher had
   * stopped working — and there is nothing this page could say instead that would be truer than
   * the sentence a person actually wrote.
   *
   * <p>What came back is not a field, deliberately: a cancellation refunds exactly what the
   * claim cost, which is `pointsSpent` and already here.
   */
  cancelledAt: string | null
  cancelledBecause: string | null
}

/**
 * A voucher as the counter surface reports it: what it is for, who is entitled to it, whether to
 * hand the thing over, and what happened at the till if anything has.
 *
 * <p>`good` is the backend's verdict and not a comparison this page makes against `state`. The
 * screen draws its panel from the boolean and says the word from the state, which is what keeps a
 * state added later from needing a change here at all.
 *
 * <p>`customerName` can be null, when the customer behind the claim is no longer on file. The
 * voucher is still a voucher and can still be handed over; there is simply nobody to name.
 */
export type VoucherAtTheCounter = {
  voucherCode: string
  code: string
  title: string
  pointsSpent: number
  customerId: number
  customerName: string | null
  claimedAt: string
  state: VoucherState
  good: boolean
  usedAt: string | null
  usedByCounter: string | null
  expiresOn: string | null
  /**
   * When it was revoked and why, null for anything else. `good` already says the voucher is no
   * good and `state` already says which of the three it is; what the reason adds is the only
   * thing the person at the till can actually say to the customer in front of them, because a
   * cancellation is the one refusal here that nothing the customer did caused.
   */
  cancelledAt: string | null
  cancelledBecause: string | null
  /**
   * What to put on the counter, when the voucher is a bundle's, and an empty list otherwise.
   *
   * <p>A bundle is one voucher covering several things, which is what makes the list necessary:
   * the title says "family night in" and the person at the till needs the two seats and the bag
   * of popcorn spelled out. Empty for every voucher that is not a bundle's, which is every
   * voucher this application has ever issued.
   */
  contents: BundleMember[]
}

/** The catalogue is the same for everybody, so it hangs off nothing but itself. */
export async function fetchRewards(signal?: AbortSignal): Promise<Reward[]> {
  const response = await fetch('/api/rewards', { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the rewards catalogue'))
  }
  return response.json()
}

/**
 * What this customer has claimed, newest first. The other half of their points balance: the deposits
 * into every account they hold say what came in, these say what went out, and the balance is what
 * the two leave.
 */
export async function fetchClaimed(
  customerId: number,
  signal?: AbortSignal,
): Promise<ClaimedReward[]> {
  const response = await fetch(`/api/customers/${customerId}/redemptions`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load what you have claimed'))
  }
  return response.json()
}

/**
 * Claims a reward, and there is no way back: the voucher exists the moment this succeeds.
 *
 * <p>Claimed by the customer rather than out of a savings account, because that is whose points pay
 * for it: somebody saving towards two goals has one pot and does not have to pick which one buys the
 * cinema ticket. What it costs is not sent — the price is the backend's, and a page that named one
 * could name the wrong one.
 */
export async function claimReward(customerId: number, reward: string): Promise<ClaimedReward> {
  const response = await fetch(`/api/customers/${customerId}/redemptions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reward }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The reward was not handed over'))
  }
  return response.json()
}

/**
 * Looks a voucher up by the code somebody at a counter typed in. Writes nothing, so it can be
 * asked as often as they like — which is the point of it being a separate call from marking one
 * used: the person at the till checks what the voucher is before they hand anything over.
 *
 * <p>The code goes into the path as typed. What to forgive about a keyboard — the spaces, the
 * case — is the backend's decision, and a page that tidied the code up first would be deciding a
 * little of it and could tidy a real code into a different one.
 */
export async function lookUpVoucher(
  voucherCode: string,
  signal?: AbortSignal,
): Promise<VoucherAtTheCounter> {
  const response = await fetch(`/api/staff/vouchers/${encodeURIComponent(voucherCode)}`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not look that voucher up'))
  }
  return response.json()
}

/**
 * Marks a voucher handed over at a counter, naming the counter.
 *
 * <p>No points and no money move: they were spent on the day the voucher was claimed, and this is
 * the other end of that — somebody actually receiving the thing.
 *
 * <p>The counter's name is required and the backend says so if it is missing. It is not checked
 * here, for the reason nothing else on these screens is: a rule enforced in two places is a rule
 * that will one day be enforced differently in each.
 */
export async function markVoucherUsed(
  voucherCode: string,
  counter: string,
): Promise<VoucherAtTheCounter> {
  const response = await fetch(`/api/staff/vouchers/${encodeURIComponent(voucherCode)}/use`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ counter }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The voucher was not marked used'))
  }
  return response.json()
}

/**
 * The amount travels as the text that was typed rather than as a number: the backend decides what
 * counts as an amount of money, and rounding it through a floating-point number on the way there
 * would make that decision here instead. It is sent exactly as typed for the same reason — nothing
 * here judges whether it is an amount at all, and a refusal comes back saying why it was not.
 */
export async function makeDeposit(
  savingsAccountId: number,
  amount: string,
  fromCurrentAccountId: number,
): Promise<RecordedDeposit> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/deposits`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount, fromCurrentAccountId }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The deposit was not accepted'))
  }
  return response.json()
}

/** Returns money from savings without translating or rounding the amount the person typed. */
export async function makeWithdrawal(
  savingsAccountId: number,
  amount: string,
  toCurrentAccountId: number,
): Promise<RecordedWithdrawal> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/withdrawals`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount, toCurrentAccountId }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The withdrawal was not accepted'))
  }
  return response.json()
}

/**
 * What every entry in the ledger carries, whichever of the four kinds it is.
 *
 * <p>`direction` arrives as the backend's own word rather than as a sign on the amount. An amount of
 * money in this application is always a positive figure, and a ledger that carried the direction in
 * the sign of the number would be the one place that stopped being true. It is also the tag that
 * tells the kinds apart, which is why every one of them is read off it.
 *
 * <p>`id` is unique within a direction and not across the ledger: deposits, withdrawals, the dates a
 * bill fell due and the spends are numbered separately, so anything keying rows off it has to key
 * off the pair.
 *
 * <p>`movedAt` is the one moment the whole list is ordered by. For a bill that is the moment it was
 * settled rather than the day it was owed from, so an arrear owed in March and taken in June sits
 * where the money actually left. For a spend it is the moment it was recorded, which is the only
 * moment it has, and it does not move when the split is corrected afterwards.
 */
type LedgerEntry = {
  id: number
  currentAccountId: number
  amount: number
  pointsEarned: number
  movedAt: string
}

/**
 * One movement of money across the boundary between an everyday account and savings.
 *
 * <p>One shape for both kinds, which is the point of the ledger: a deposit and a withdrawal are the
 * same event seen from opposite sides, and a customer reading back over what they have done with
 * their money reads one story rather than two lists they have to interleave by eye. What differs
 * between the two is in `direction` rather than in the fields, so a row renders the same way
 * whichever it is.
 *
 * <p>`pointsEarned` is 0 for a withdrawal, which is what a withdrawal earns rather than a gap in the
 * record — money coming back out has never earned a point here.
 */
export type SavingsMovement = LedgerEntry & {
  direction: 'INTO_SAVINGS' | 'OUT_OF_SAVINGS'
  savingsAccountId: number
  /**
   * Whether a saving rule made this movement rather than a person.
   *
   * <p>The one thing that lets a customer tell what they did from what the application did for
   * them, and it is the backend's answer rather than something this page could work out: a deposit
   * a rule made is an ordinary deposit in every other respect, of an amount somebody might well
   * have typed themselves.
   *
   * <p>False on every movement out of savings, and that is a statement rather than a gap: nothing
   * in this application takes money back out automatically.
   */
  automatic: boolean
}

/**
 * One date a recurring bill fell due on, in the same list as the deposits: the rent leaving the
 * everyday account, beside the money that was saved out of it.
 *
 * <p><strong>An unpaid attempt is one of these too, and that is the point of it.</strong> A ledger
 * that recorded only successes is exactly how somebody ends up confused about their balance, and the
 * answer they need — that the money never moved — is a row rather than a silence. `outcome` says
 * which it was, and nothing about an unpaid one has reduced a balance anywhere.
 *
 * <p>`savingsAccountId` is null and that is a statement rather than a gap: money leaving a current
 * account for the rent never goes near savings.
 *
 * <p><strong>Two dates, both shown.</strong> `dueOn` is the day it was owed from and `movedAt` is
 * when it was actually settled, so a late payment reads as "rent, due 1 March, taken 14 June"
 * instead of rewriting itself back into March. `daysLate` is the backend's own subtraction between
 * them, like a bill history's — a second place deciding what a day is would be a second answer.
 */
export type BillMovement = LedgerEntry & {
  direction: 'OUT_OF_CURRENT_ACCOUNT'
  savingsAccountId: null
  /** The name the customer recognises: the rent, told apart from the phone bill. */
  billName: string
  dueOn: string
  outcome: BillOutcome
  daysLate: number
}

/**
 * One thing the customer spent, in the same list as the deposits and the bills: the groceries
 * leaving the everyday account beside the rent that left it and the money that was saved out of it.
 *
 * <p><strong>Not a bill, and the direction is what says so.</strong> Both leave a current account,
 * and there the likeness stops: a bill was declared in advance and every date it falls due repeats
 * the declaration, where a spend is one afternoon named afterwards. So a spend carries no due date
 * and no outcome — nothing about it was ever presented and refused, because the money left as it was
 * recorded or the spend was refused outright and there is no row at all.
 *
 * <p>`savingsAccountId` is null and that is a statement rather than a gap: money spent on groceries
 * never goes near savings.
 *
 * <p><strong>`parts` is what the customer said it went on, in the words they chose.</strong> A row
 * saying EUR 30,00 left for "Supermarket" is half an answer; the split is the other half. A part
 * filed under nothing at all is in it too, because that is somebody recording a spend before
 * deciding what it was for, and the parts always add up to the amount.
 *
 * <p><strong>`correctedAt` is null on a spend nobody has corrected</strong>, and that null is the
 * whole of what it is for: the ledger says plainly which spends were not right the first time rather
 * than quietly showing something different from what it showed yesterday. The row still sits at
 * `movedAt`, because the money left when it left and only the opinion about it has changed.
 */
export type SpendMovement = LedgerEntry & {
  direction: 'SPENT_OUT_OF_CURRENT_ACCOUNT'
  savingsAccountId: null
  /** The name its holder gave it, so the list reads like their week rather than a bank statement. */
  spendName: string
  correctedAt: string | null
  parts: RecordedSpendPart[]
}

/**
 * One entry in the money-movement ledger, in whichever of the five kinds it is.
 *
 * <p>Read as a union on `direction` rather than as one record with everything on it, so that a page
 * cannot ask a deposit what it was due on: the compiler is what keeps the shapes apart, and it is
 * the only gate this frontend has.
 *
 * <p>`InterestMovement` is the one kind with no current account at either end, and the union is
 * what makes that safe to read: a page has to deal with it before it can touch `currentAccountId`
 * on any of the others.
 */
export type MoneyMovement =
  | SavingsMovement
  | BillMovement
  | SpendMovement
  | InterestMovement
  | AnEarlyExitChargeMovement
  | AMoveBetweenSavingsAccountsMovement

/**
 * Every euro this customer has moved into or out of savings, every date one of their bills fell due
 * on and everything they spent, newest first, across every account they hold.
 *
 * <p>The customer's rather than one account's, because that is the question: somebody saving towards
 * two goals moved their money once. Nothing is added up here or by the page that shows it — a running
 * balance across several accounts is not a figure that means anything.
 */
export async function fetchMoneyMovements(
  customerId: number,
  signal?: AbortSignal,
): Promise<MoneyMovement[]> {
  const response = await fetch(`/api/customers/${customerId}/money-movements`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load your money history'))
  }
  return response.json()
}

/**
 * One gift of points, as either end of it reads.
 *
 * <p>One shape for a gift just made and for a gift read back out of a list, which is the same
 * bargain {@link MoneyMovement} strikes: a gift is one event seen from two sides, and a customer
 * reading back what they have given and been given reads one list rather than two they have to
 * interleave by eye. What differs between the two ends is `direction` rather than the fields.
 *
 * <p>`direction` is the backend's own word rather than a sign on the points, for the reason the
 * money ledger's is: points here are always a positive figure, and a list that carried the
 * direction in the sign of the number would be the one place that stopped being true. A gift just
 * created comes back `SENT`, because the person who made it is the person being answered.
 *
 * <p>Both people rather than only the other one, so a row says who it is about whoever fetched it.
 * Which of the two is "the other person" follows from the direction and is the page's to decide.
 *
 * <p>`givenAt` is off the application's clock rather than the browser's, so a gift made against a
 * clock a trainer has wound forward reads where they wound it to.
 */
export type Gift = {
  id: number
  direction: 'SENT' | 'RECEIVED'
  senderId: number
  senderName: string
  recipientId: number
  recipientName: string
  points: number
  givenAt: string
}

/**
 * Every gift this customer was part of, sent and received together, newest first.
 *
 * <p>The customer's rather than one account's, because points are the person's: they are earned by
 * paying into any savings account and given away by the person, so there is one list and it hangs
 * off them.
 */
export async function fetchGifts(customerId: number, signal?: AbortSignal): Promise<Gift[]> {
  const response = await fetch(`/api/customers/${customerId}/gifts`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load your gifts'))
  }
  return response.json()
}

/**
 * Gives some of this customer's points to another customer, and there is no way back: the points
 * are the recipient's the moment this succeeds.
 *
 * <p>The recipient travels as the address they bank under rather than as an identifier, because
 * that is the contract — the same address they would type to sign in. A page may offer a picker
 * over the people who bank here, and this is still the request underneath it.
 *
 * <p>The number of points travels as the text that was typed, the way a deposit's amount does. What
 * counts as a number of points is the backend's decision — whole, positive, and no more than the
 * sender holds — and reading "2.5" into something plausible on the way there would be this page
 * taking a little of that decision. Sent as typed, and refused in words if it was not a gift.
 */
export async function giveGift(
  customerId: number,
  recipientContactDetails: string,
  points: string,
): Promise<Gift> {
  const response = await fetch(`/api/customers/${customerId}/gifts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ recipientContactDetails, points }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The gift was not made'))
  }
  return response.json()
}

/**
 * Why a rule decided something was worth saying. The backend's own enum name, sent as it is:
 * a notification travels as its reason and its figures, and the sentence is written in the page.
 *
 * <p>Ten values and no more today, and the union is written out here rather than left as a
 * `string` so that a page switching on it is told by the compiler when the backend grows an
 * eleventh — points about to expire, a reward newly affordable — instead of quietly rendering
 * nothing for a reason it has never heard of. It has already paid for itself twice: the two bill
 * reasons arrived, and then the three budget reasons below them, and every `switch` over this union
 * stopped compiling both times until somebody had decided what each screen says about them.
 */
export type NotificationReason =
  | 'BALANCE_THRESHOLD_REACHED'
  | 'BALANCE_THRESHOLD_LOST'
  | 'LOYALTY_BONUS_ABOUT_TO_PAY'
  | 'LOYALTY_BONUS_AT_RISK'
  | 'AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN'
  | 'A_BILL_COULD_NOT_BE_PAID'
  | 'BILLS_ARE_PILING_UP'
  | 'A_BUDGET_IS_RUNNING_LOW'
  | 'A_BUDGET_HAS_BEEN_OVERSPENT'
  | 'THE_MONTH_IS_OVER_COMMITTED'
  | 'A_REWARD_IS_BEING_HELD_FOR_YOU'
  | 'A_TERM_IS_ABOUT_TO_MATURE'
  | 'A_NOTICE_HAS_BECOME_READY'
  | 'A_PRODUCT_HAS_BETTERED_YOUR_TERMS'
  | 'AN_AUTOMATIC_TRANSFER_HAD_NOWHERE_TO_GO'

/**
 * A moment a rule decided was worth saying: the reason, the figures behind it, when it was raised
 * and — once the customer has looked — when it was read.
 *
 * <p>**Figures and no sentence.** Every euro and every date in this application is written
 * Dutch-style here, in the browser, and a sentence composed in Java would fork that formatting into
 * a second place that will drift. So the backend sends numbers and `WhatHappened` writes the words,
 * exactly as `WhatItEarned` already composes a sentence out of three numbers on a deposit. Refusals
 * are the exception and stay as the backend's own sentences, because a refusal's wording is domain
 * logic and a notification's wording is not.
 *
 * <p>Which figures are filled in is decided by the reason and is total, so a page that has read the
 * reason knows which fields it can rely on:
 *
 * - `BALANCE_THRESHOLD_REACHED` and `BALANCE_THRESHOLD_LOST` carry `amount`, the rung, and no
 *   `depositId`, `occurrenceId`, `points` or `occursOn`.
 * - `LOYALTY_BONUS_ABOUT_TO_PAY` and `LOYALTY_BONUS_AT_RISK` carry `depositId`, `points` and
 *   `occursOn`, and no `amount`.
 * - `AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN` carries `occurrenceId`, `occursOn` — the day the
 *   transfer was due — and `amount`, which for this one reason is what the current account was
 *   short rather than a rung. No `depositId` and no `points`: nothing moved, so nothing was
 *   deposited and nothing was earned.
 * - `A_BILL_COULD_NOT_BE_PAID` carries `currentAccountId`, `billId`, `billName`, `occursOn` — the
 *   day the bill was owed on — `amount`, what the bill asked for, and `balance`, what the account
 *   actually held. Two money figures rather than one shortfall, so the page can say both halves of
 *   the decision.
 * - `BILLS_ARE_PILING_UP` carries `currentAccountId`, `arrears` — how many dates are outstanding —
 *   and `amount`, what they come to altogether. No `billId` and no `occursOn`: it is about the whole
 *   hole and blames no single date for it.
 * - `A_BUDGET_IS_RUNNING_LOW` and `A_BUDGET_HAS_BEEN_OVERSPENT` carry `currentAccountId`,
 *   `categoryId`, `categoryName`, `occursOn` — the day the month they are about *began* — `amount`,
 *   what that month allows, and `balance`, what has been spent against it. Two money figures rather
 *   than one difference, for the reason the unpaid bill sends two: "EUR 30,00 left" cannot be told
 *   from thirty of forty or thirty of three thousand.
 * - `THE_MONTH_IS_OVER_COMMITTED` carries `currentAccountId`, `occursOn` — the same day — `amount`,
 *   what the month is promised to, and `balance`, what it has. No `categoryId`: it is about the
 *   whole promise and blames no single category for it, exactly as the piling-up warning blames no
 *   single bill.
 * - `A_REWARD_IS_BEING_HELD_FOR_YOU` carries `offerCode`, `offerTitle` and `lapsesAt`, and no
 *   account at all — a hold belongs to the customer and to none of their pots, exactly as their
 *   points do. It is the only reason with no money on it and the only one whose deadline is a
 *   moment rather than a day, because a hold is seventy-two hours and not a number of calendar
 *   days.
 * - `A_TERM_IS_ABOUT_TO_MATURE` carries `savingsAccountId`, `productCode`, `productName` and
 *   `occursOn` — the day the term is up — and no money at all. What the term holds is on the
 *   account's own panel and is true now; what this notice is about is a date.
 * - `A_NOTICE_HAS_BECOME_READY` carries `savingsAccountId`, `noticeId`, `occursOn` — the day it
 *   came free — and `amount`, which for this reason is what the notice still covers rather than
 *   what it was given on. A notice that has already paid for part of a withdrawal is good for what
 *   is left of it, and that is the figure the customer can act on.
 * - `A_PRODUCT_HAS_BETTERED_YOUR_TERMS` carries `savingsAccountId`, `productCode`, `productName`,
 *   `termsVersion` — the version on offer — `amount`, the headline rate that version pays, and
 *   `balance`, the headline rate the account is on. Two rates rather than one difference, for the
 *   reason the unpaid bill sends two figures. `whatIsDifferent` holds the backend's own sentences
 *   about every figure that moved, and `occursOn` is null: a published version has no day the
 *   customer has to act by, which is exactly the promise that nothing is adopted on their behalf.
 * - `AN_AUTOMATIC_TRANSFER_HAD_NOWHERE_TO_GO` carries `savingsAccountId`, `occurrenceId` and
 *   `occursOn` — the day the transfer was due — and no money at all. The rule fired into a savings
 *   account that had been closed, so the current account was never the problem and a shortfall
 *   beside it would have to be nought or a lie.
 *
 * <p>**`occursOn` means a different day for each family**, which is the one field worth reading this
 * list for. For the three budget reasons it is the first of the month the warning is about: a month
 * is not a type a database has, the first of it is a date that sorts and compares, and a second
 * field meaning "month" would be one three reasons out of ten could use.
 *
 * <p>**Two account references, and each null for the reasons that are not about that account.**
 * `savingsAccountId` was on every notification until bills arrived; an unpaid rent is a fact about a
 * current account and there is no savings account it is about. A screen that shows the notice
 * belonging to the account it is about reads whichever of the two it is a screen for, and neither
 * stands in for the other.
 *
 * <p>`occursOn` is a plain `YYYY-MM-DD` rather than a moment, for the reason
 * {@link CustomerAccounts} gives about an expiry day: the backend has already decided which day
 * this is, in the one zone this application counts calendars in.
 *
 * <p>`readAt` is a moment rather than a flag, and null until somebody looks. Two states and not
 * three — there is no dismissing a notification, and nothing is ever deleted — so a row that has
 * been read stays in the panel, dimmed, as the record that the rule fired.
 */
export type Notification = {
  id: number
  reason: NotificationReason
  /** The savings account the notice is about, and null for the two that are about bills. */
  savingsAccountId: number | null
  /** The current account the notice is about, and null for every reason about savings. */
  currentAccountId: number | null
  depositId: number | null
  /** The saving-rule occurrence a transfer that did not happen is about, and null otherwise. */
  occurrenceId: number | null
  /** The recurring bill a date that could not be paid is about, and null for every other reason. */
  billId: number | null
  /**
   * What that bill was called when the notice was raised, and null for every other reason. A
   * snapshot on purpose: renaming the bill afterwards does not rewrite what the customer was told.
   */
  billName: string | null
  /**
   * The spending category a budget warning is about, and null for every other reason — the
   * over-committed month included, which is about the whole promise and names no category.
   */
  categoryId: number | null
  /**
   * What that category was called when the notice was raised, and null for every other reason. A
   * snapshot on purpose: renaming or ending the category afterwards does not rewrite what the
   * customer was told.
   */
  categoryName: string | null
  /**
   * The rung, for a balance reason; what the account was short, for a transfer that did not
   * happen; what the bill asked for, for a date that could not be paid; what the arrears come to,
   * for the piling-up warning; what the month allows, for a budget warning; what the month is
   * promised to, for an over-committed month; and null for the two anniversary reasons.
   */
  amount: number | null
  /**
   * The figure `amount` was measured against, for the reasons decided by comparing two, and null
   * for the ones decided by one: what the account held on the night a bill could not be paid, what
   * has been spent for a budget warning, and what the month has for an over-committed month. The
   * balance that night, not the balance now.
   */
  balance: number | null
  /** How many dates are outstanding, for the piling-up warning, and null for every other reason. */
  arrears: number | null
  /** What the anniversary pays, for a loyalty reason, and null for every other reason. */
  points: number | null
  /**
   * The anniversary day for a loyalty reason, the day a transfer was due for one that did not
   * happen, the day a bill was owed on for one that could not be paid, or the day the month began
   * for the three about budgets, as `YYYY-MM-DD` — and null for the balance ones and for the
   * piling-up warning.
   */
  occursOn: string | null
  /**
   * The catalogue code of the offer being held, for a promoted waiter, and null for every other
   * reason. It is what {@link RewardIcon}'s map is keyed on, which falls back to a generic gift
   * for anything it has not heard of — so a notice about a brand new offer draws correctly with
   * no change here.
   */
  offerCode: string | null
  /**
   * What that offer was called when the notice was raised, and null for every other reason. A
   * snapshot on purpose, exactly like `billName` and `categoryName`: renaming or withdrawing the
   * offer afterwards does not rewrite what the customer was told.
   */
  offerTitle: string | null
  /**
   * The moment that hold runs out, for a promoted waiter, and null for every other reason. The
   * only deadline in this type that is a moment rather than a `YYYY-MM-DD` day — a hold is
   * seventy-two hours from when it was created, so a date would round the promise rather than be
   * it, and the rewards card counts down to this very instant.
   */
  lapsesAt: string | null
  raisedAt: string
  /** When the customer read it, and null while it is unread. */
  readAt: string | null
  /**
   * The notice that has run its days, for `A_NOTICE_HAS_BECOME_READY`, and null for every other
   * reason. The notice rather than the day is what makes that announcement unique: two notices
   * given on one morning come free on one morning and are two separate amounts.
   */
  noticeId: number | null
  /**
   * The savings product the notice is about, for `A_TERM_IS_ABOUT_TO_MATURE` and
   * `A_PRODUCT_HAS_BETTERED_YOUR_TERMS`, and null for every other reason. The code is the address —
   * what a link to the product's own screen is built from — and `productName` is the word the
   * sentence is written with, exactly as `offerCode` sits beside `offerTitle`.
   */
  productCode: string | null
  /**
   * What that product was called when the notice was raised, and null for every other reason. A
   * snapshot on purpose, like `billName`, `categoryName` and `offerTitle`.
   */
  productName: string | null
  /**
   * The version of that product's terms on offer, for `A_PRODUCT_HAS_BETTERED_YOUR_TERMS`, and null
   * for every other reason. It is what makes that announcement once-per-version.
   */
  termsVersion: number | null
  /**
   * One sentence per figure that differs between the version the account is on and the version on
   * offer, for `A_PRODUCT_HAS_BETTERED_YOUR_TERMS`, and empty for every other reason.
   *
   * <p>**The backend's own words, and the one exception to "figures and no sentence" above.** These
   * are the very sentences a product's version history prints, worded in one place in Java so that
   * a customer reading this notice and then the history meets the same words twice. Composing them
   * here would be the second place, and the two would describe one rate cut differently on one
   * screen. Whether the move was an *improvement* is a judgement, and it is not in the words at all
   * — it is the reason the notice exists.
   */
  whatIsDifferent: string[]
}

/**
 * Everything that has been said to this customer, newest first, read and unread together.
 *
 * <p>The customer's rather than one account's, because a notification is addressed to the person
 * who reads it: the bell is in the top bar on every screen and counts everything, whichever pot it
 * is about. Each one carries the savings account it concerns, so the panel can name the pot and a
 * page can find the notice that concerns it.
 */
export async function fetchNotifications(
  customerId: number,
  signal?: AbortSignal,
): Promise<Notification[]> {
  const response = await fetch(`/api/customers/${customerId}/notifications`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load your notifications'))
  }
  return response.json()
}

/**
 * Marks everything unread as read, and answers with the whole list as it now stands.
 *
 * <p>One call rather than one per row, and one round trip rather than two: the backend returns the
 * same list the read endpoint would, so opening the panel both clears the count and refreshes what
 * the panel is about to show. It is idempotent — a notification already read keeps the moment it
 * was first read at — so opening the panel again changes nothing.
 */
export async function markNotificationsRead(customerId: number): Promise<Notification[]> {
  const response = await fetch(`/api/customers/${customerId}/notifications/read`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not mark your notifications as read'))
  }
  return response.json()
}

/* ------------------------------------------------------------------- savings goals
 *
 * What a savings account is being saved towards. Every figure below is the backend's — the
 * allocation, what is still needed, the weekly amount the plan gives a goal, the projected day and
 * the status it is judged by are all derived there on every read. Nothing in this file works any of
 * them out, and nothing caches them: a change to one of them changes most of the others, so the
 * page reads them back rather than keeping its own copy in step.
 */

/** Whether a goal is still being saved towards, or has been given up on. */
export type GoalState = 'LIVE' | 'ABANDONED'

/**
 * What a goal says about itself once the plan has been laid over it.
 *
 * <p>The backend's own words, passed through as they arrive. `OFF_TRACK` and `UNREACHABLE` are
 * different answers and the page has to keep them apart: off track is a day that lands after the
 * deadline, unreachable is a projection that does not land at all.
 */
export type GoalStatus =
  | 'STILL_SAVING'
  | 'COMPLETED'
  | 'ON_TRACK'
  | 'OFF_TRACK'
  | 'UNREACHABLE'
  | 'NO_DEADLINE'
  | 'ABANDONED'

/**
 * One savings goal: what it is for, what it is holding, and when the plan says it arrives.
 *
 * <p>`allocation` is a claim on the account's balance rather than money of its own — the balance is
 * still the one true figure, and what no goal has claimed is `unallocated` on the account.
 *
 * <p>`pinnedWeeklyAmount` is the fact that makes `weeklyAmount` readable: the same figure means two
 * different things depending on whether the customer chose it or the plan worked it out, and a
 * customer comparing two goals needs to know which of them the application decided.
 */
export type SavingsGoal = {
  id: number
  savingsAccountId: number
  name: string
  target: number
  /** `YYYY-MM-DD`, or absent for a goal with no day to be late for. */
  deadline: string | null
  /** Its place in the order of importance, 1 first. Absent once it has been abandoned. */
  rank: number | null
  state: GoalState
  status: GoalStatus
  allocation: number
  stillNeeded: number
  /**
   * What the plan gives this goal each week out of the declared capacity, and absent on an account
   * where nobody has declared one.
   *
   * <p>Null there and never a zero, which is the backend's own distinction: nobody having said how
   * fast anything fills is a different sentence from a plan in which this goal is given nothing,
   * and a page that read a zero for the first would draw the second. Once a capacity exists every
   * goal carries a figure and 0.00 is a real answer — the capacity ran out before this goal, or the
   * goal has arrived. Typed nullable so the compiler holds whoever renders it to the difference:
   * `Intl.NumberFormat` turns a null into `€ 0,00` without complaint.
   */
  weeklyAmount: number | null
  /** The figure the customer chose, or absent when the plan worked the weekly amount out itself. */
  pinnedWeeklyAmount: number | null
  /**
   * The projected day, and absent on a goal with no week left to count: one that has arrived, one
   * the plan gives nothing, one on an account where no capacity has been declared, and one that was
   * given up on. Four different sentences with the same absence — `status` says which of them it
   * was, so nothing reading this has to guess the reason from the missing date.
   */
  willBeReachedOn: string | null
  createdAt: string
  abandonedAt: string | null
}

/**
 * The account's money seen through its goals: what it holds, what the goals have claimed, and what
 * is left over.
 *
 * <p>`unallocated` is `balance − allocated`, derived there and never stored, and it is the figure
 * every action on the page is measured against — an allocation beyond it is refused, and so is a
 * withdrawal.
 */
export type AllocationsOnAnAccount = {
  savingsAccountId: number
  balance: number
  allocated: number
  unallocated: number
  /** Live goals in rank order, first one first. Abandoned goals are not among them. */
  goals: SavingsGoal[]
}

/**
 * The most its holder says they can put away in a week, and what that rate does not reach.
 *
 * <p>`declared` is the field that matters: a capacity nobody has ever declared is not a capacity of
 * zero, and showing 0,00 for one would be this page inventing an answer the customer never gave.
 *
 * <p>`weeklyMinimum` and `aWeekIsNotSecuredAtThisRate` are the backend's, for the reason every other
 * figure here is: the €50 a week costs is named in one place there, and a page that wrote it into
 * its own markup would be a second place it lived.
 */
export type SavingCapacity = {
  savingsAccountId: number
  declared: boolean
  weeklyCapacity: number | null
  weeklyMinimum: number
  aWeekIsNotSecuredAtThisRate: boolean
  declaredAt: string | null
}

/**
 * One move worth making: this much, out of that goal, into this one.
 *
 * <p>Both ends are typed nullable, but a suggested move never actually has one: unlike a move in
 * the ledger, this suggestion only ever moves money between two goals, never in or out of the part
 * of the balance no goal has claimed, which the backend's own `SuggestedMoveResponse` says. The
 * nulls are the shape of a move, kept here so that nothing reading one has to know which of the two
 * kinds it is holding; whoever renders a name still has a sentence for the absent case.
 *
 * <p>`reason` is the backend's sentence saying why this move is worth making, and it is shown as it
 * arrives for the same reason a refusal is.
 */
export type SuggestedMove = {
  outOfGoalId: number | null
  outOfGoalName: string | null
  intoGoalId: number | null
  intoGoalName: string | null
  amount: number
  reason: string
}

/**
 * The reallocation this account's order of importance makes worth suggesting, derived on demand and
 * stored nowhere.
 *
 * <p>`inWords` is present-tense advice — "2 moves worth making" — and it is the wording of a
 * suggestion that has not been taken. The same record comes back from the acceptance under
 * `applied`, where that sentence would be false, so this page never prints it after accepting; see
 * {@link AppliedReallocation}.
 */
export type SuggestedReallocation = {
  savingsAccountId: number
  worthSuggesting: boolean
  inWords: string
  moves: SuggestedMove[]
}

/**
 * What accepting did: the moves that were applied, and the account's allocations afterwards.
 *
 * <p>`applied` is a {@link SuggestedReallocation} carrying the moves that were actually made. Its
 * `inWords` is still worded as advice, so what this page says afterwards is built from `moves` —
 * the amounts and the two names — rather than from that sentence.
 */
export type AppliedReallocation = {
  applied: SuggestedReallocation
  allocations: AllocationsOnAnAccount
}

/**
 * Which way money moves with respect to one goal.
 *
 * <p>A word rather than a sign, exactly as the backend takes it: no amount anywhere in this feature
 * is negative, and "free 50.00 from the holiday" and "put 50.00 towards the holiday" are two
 * instructions rather than one instruction and a minus sign.
 */
export type AllocationDirection = 'INTO_THE_GOAL' | 'OUT_OF_THE_GOAL'

/** What the account holds, what its goals have claimed, and what is left over. */
export async function fetchAllocations(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<AllocationsOnAnAccount> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/goals/allocations`, {
    signal,
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load this account’s goals'))
  }
  return response.json()
}

/** The weekly figure its holder declared, or the record saying they never have. */
export async function fetchSavingCapacity(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<SavingCapacity> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/saving-capacity`, {
    signal,
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the weekly amount'))
  }
  return response.json()
}

/** Declares the weekly figure, sending what was typed rather than a number worked out from it. */
export async function declareSavingCapacity(
  savingsAccountId: number,
  weeklyCapacity: string,
): Promise<SavingCapacity> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/saving-capacity`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ weeklyCapacity }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The weekly amount was not accepted'))
  }
  return response.json()
}

/**
 * What was given up on: the account's abandoned goals, so that a goal closed rather than deleted can
 * still be read back by name.
 *
 * <p>A read of its own because the ordinary goals read is the ordinary goals read — it answers with
 * the live ones, and a page showing what somebody is saving for should not have to say every time
 * that it does not want the things they gave up on. The one screen that does want them is the one
 * showing a rule's split: the backend keeps an abandoned goal's line in a rule on purpose, and a
 * form that could not name that goal could only show the customer a number.
 */
export async function fetchAbandonedGoals(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<SavingsGoal[]> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/goals/abandoned`,
    { signal },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the goals you gave up on'))
  }
  return response.json()
}

/** Opens a goal, ranked last. The target and the day travel as they were typed. */
export async function addGoal(
  savingsAccountId: number,
  name: string,
  target: string,
  deadline: string | null,
): Promise<SavingsGoal> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/goals`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, target, deadline }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The goal was not opened'))
  }
  return response.json()
}

/**
 * Changes a goal's name, its target or the day it is wanted by.
 *
 * <p>Absent means "leave it alone" and an empty deadline means "there is no longer a day", which is
 * the backend's own distinction: a PATCH that sent every field on every edit could not say the
 * difference between a deadline nobody touched and a deadline somebody removed.
 */
export async function changeGoal(
  savingsAccountId: number,
  goalId: number,
  changes: { name?: string; target?: string; deadline?: string },
): Promise<SavingsGoal> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/goals/${goalId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(changes),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The goal was not changed'))
  }
  return response.json()
}

/** Gives up on a goal, which closes it and returns what it was holding to unallocated. */
export async function abandonGoal(
  savingsAccountId: number,
  goalId: number,
): Promise<SavingsGoal> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/goals/${goalId}/abandon`,
    { method: 'POST' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The goal was not abandoned'))
  }
  return response.json()
}

/**
 * Sets the whole order of importance in one call.
 *
 * <p>The whole order rather than one goal's place, because a strict total order has no valid
 * intermediate state: moving one goal up is the same event as moving another down, and two calls
 * would leave the account between two orders in between them.
 */
export async function reorderGoals(
  savingsAccountId: number,
  goalIds: number[],
): Promise<SavingsGoal[]> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/goals/order`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ goalIds }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The order was not changed'))
  }
  return response.json()
}

/** Pins what this goal gets each week, so the plan works around it rather than over it. */
export async function pinWeeklyAmount(
  savingsAccountId: number,
  goalId: number,
  weeklyAmount: string,
): Promise<SavingsGoal> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/goals/${goalId}/weekly-amount`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ weeklyAmount }),
    },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The weekly amount was not pinned'))
  }
  return response.json()
}

/** Hands the weekly figure back to the plan. */
export async function unpinWeeklyAmount(
  savingsAccountId: number,
  goalId: number,
): Promise<SavingsGoal> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/goals/${goalId}/weekly-amount`,
    { method: 'DELETE' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The weekly amount was not unpinned'))
  }
  return response.json()
}

/**
 * Moves money into a goal or frees it back out, and answers with the account's allocations
 * afterwards.
 *
 * <p>The amount travels as it was typed and the direction travels as a word. What comes back is
 * every goal on the account rather than the one that was touched: money moving into one goal is
 * money the others can no longer have, and their weekly amounts and statuses move with it.
 */
export async function moveAllocation(
  savingsAccountId: number,
  goalId: number,
  amount: string,
  direction: AllocationDirection,
): Promise<AllocationsOnAnAccount> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/goals/${goalId}/allocations`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ amount, direction }),
    },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The money was not moved'))
  }
  return response.json()
}

/** The moves worth suggesting on this account, or the record saying there is nothing to suggest. */
export async function fetchSuggestedReallocation(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<SuggestedReallocation> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/goals/suggested-reallocation`,
    { signal },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not read the suggested reallocation'))
  }
  return response.json()
}

/** Takes the suggestion whole. It is the only thing that ever applies one; reading never does. */
export async function acceptSuggestedReallocation(
  savingsAccountId: number,
): Promise<AppliedReallocation> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/goals/suggested-reallocation`,
    { method: 'POST' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The reallocation was not applied'))
  }
  return response.json()
}

/* --------------------------------------------------------------- automatic saving
 *
 * The instructions a customer has left standing against a savings account, what each one will do
 * next, what each one has done, and the money that arrives for them to move. Every figure below is
 * the backend's: what a rule would move, the day it next fires, what a year of them comes to and
 * whether a sweep's figure is a promise or an illustration are all derived there on every read, and
 * nothing in this file works any of them out or keeps a copy of one.
 */

/** What makes a rule move: a day of the week, a date in the month, or the day the salary lands. */
export type RuleTrigger = 'WEEKLY' | 'MONTHLY' | 'ON_PAYDAY'

/** How much moves: the figure the customer named, or everything above the floor they named. */
export type HowMuchMoves = 'A_FIXED_AMOUNT' | 'EVERYTHING_ABOVE'

/**
 * Where a rule stands: still firing, stopped until its holder says otherwise, or closed for good.
 *
 * <p>`ENDED` is not reachable through the list of standing rules — an ended rule is read back
 * through its own endpoint — but it is a state a rule can be in and it arrives on those reads, so it
 * is named here rather than left for a page to meet without a word for it.
 */
export type RuleState = 'LIVE' | 'PAUSED' | 'ENDED'

/**
 * What became of one day a rule fell due on.
 *
 * <p>Four outcomes and not two: money moved, there was nothing above the floor to move, the
 * account could not cover a fixed amount, and the savings account it pays into had been closed. The
 * second is arithmetic rather than a failure — a sweep on an account already at its floor has
 * nothing to sweep — and a page that drew it as a failure would be reporting a subtraction as
 * something gone wrong. The last is the one outcome no amount of money in the current account would
 * have changed: the rule is pointing at an account that takes no more, and what its holder does
 * about it is change the rule or end it.
 */
export type OccurrenceOutcome =
  | 'MOVED'
  | 'NOTHING_TO_MOVE'
  | 'NOT_ENOUGH_MONEY'
  | 'THE_ACCOUNT_IS_CLOSED'

/** The seven days, as the backend writes them. */
export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY'

/** One goal's share of what a rule moves, as the whole percentage the customer wrote. */
export type SavingRuleShare = {
  goalId: number
  share: number
}

/** What one goal would get out of a transfer that has not happened yet, to the cent. */
export type WhatAGoalWouldGet = {
  goalId: number
  share: number
  amount: number
}

/**
 * What a rule would move, and what would become of it.
 *
 * <p>`amount` is what would move. `floor` is filled exactly on a sweep — what would be left behind —
 * so a page can say the two different sentences rather than printing one figure under a label it
 * has to guess at.
 *
 * <p>`anIllustrationRatherThanAPromise` is the backend saying which kind of figure this is. A fixed
 * amount will move what it says; a sweep's figure is worked out from a balance nobody can know in
 * advance, and is quoted as a worked example of today's balance. A page that printed the two the
 * same way would be handing somebody a confident figure about a Tuesday in March.
 */
export type WhatWouldMove = {
  amount: number
  floor: number | null
  anIllustrationRatherThanAPromise: boolean
  intoGoals: WhatAGoalWouldGet[]
  leftUnallocated: number
}

/**
 * One rule as the API reports it: what it is called, where the money comes from, what makes it
 * move, which day that is, how much moves, and whether it is still standing.
 *
 * <p>`dayOfMonth` on a payday rule is the day its holder declared their income lands, read from that
 * declaration on every read — and null on a payday rule whose holder has declared no income yet,
 * which is a thing this page has something to say about: nothing yet says when that rule moves.
 *
 * <p>`nextFiresOn` and `nextMoves` are the two halves of the question a customer arrives with. Both
 * are null on a rule that will not fire — a paused one, an ended one, a payday rule with no income
 * behind it — and a page draws that absence as the sentence it is rather than as a blank.
 */
export type SavingRule = {
  id: number
  savingsAccountId: number
  fromCurrentAccountId: number
  name: string
  trigger: RuleTrigger
  dayOfWeek: DayOfWeek | null
  dayOfMonth: number | null
  howMuchMoves: HowMuchMoves
  amount: number | null
  floor: number | null
  split: SavingRuleShare[]
  state: RuleState
  createdAt: string
  pausedAt: string | null
  endedAt: string | null
  /** `YYYY-MM-DD`, or null when nothing is coming. */
  nextFiresOn: string | null
  nextMoves: WhatWouldMove | null
}

/** What one goal actually got out of a transfer that has already happened. */
export type WhatAGoalGot = {
  goalId: number
  amount: number
}

/**
 * One day a rule fell due on and what became of it.
 *
 * <p>`daysLate` is the distance between the day it was due and the moment it was actually made,
 * which on a wound clock or after downtime is the whole point: a transfer made five days after the
 * morning it belonged to is part of the history, and a record that quoted only the moment it moved
 * could not say so.
 *
 * <p>`shortfall` is filled exactly on an occurrence the account could not cover, and `amount` is
 * what moved — zero on the two outcomes where nothing did.
 */
export type RuleOccurrence = {
  id: number
  ruleId: number
  /** The morning it belonged to, as `YYYY-MM-DD`. */
  dueOn: string
  /** The moment it was actually dealt with. */
  settledAt: string
  daysLate: number
  outcome: OccurrenceOutcome
  amount: number
  shortfall: number | null
  depositId: number | null
  intoGoals: WhatAGoalGot[]
  leftUnallocated: number
}

/**
 * One day a rule is going to fall due on, and what it would move when it does.
 *
 * <p>`owedRatherThanStillToCome` says which of the two kinds of line this is: a morning already past
 * that the next nightly run will make, or a day in the future. The backend draws that boundary
 * against its own clock, and a page comparing each day against the browser's idea of today would be
 * re-deriving it in a second calendar.
 */
export type RuleForecast = {
  ruleId: number
  ruleName: string
  dueOn: string
  trigger: RuleTrigger
  howMuchMoves: HowMuchMoves
  wouldMove: WhatWouldMove
  owedRatherThanStillToCome: boolean
}

/**
 * One date a bill is going to fall due on, and what it will take when it does.
 *
 * <p>The outbound half of the year ahead. `owedRatherThanStillToCome` means the same thing it means
 * on a rule's line: a bill is forecast from its own cursor rather than from today, so one the 02:30
 * run has not caught up with carries the dates it is late for at the head of the list. The backend
 * draws that boundary against its own clock.
 */
export type BillForecast = {
  billId: number
  currentAccountId: number
  billName: string
  /** The day it falls due, as `YYYY-MM-DD`, with the month-end clamp already applied. */
  dueOn: string
  amount: number
  owedRatherThanStillToCome: boolean
}

/**
 * Everything every rule on one account has coming, merged into one list in date order — and the
 * bills going the other way over the same twelve months.
 *
 * <p>Two lists rather than one, because the two are different sentences: an occurrence carries a
 * trigger, a figure that may be an illustration and a split across goals, and a bill carries a name
 * and a figure that is never anything but exact. Both are in date order over the same window, which
 * is all the page needs to draw them as one year — interleaving two lists of the backend's own days
 * decides nothing the backend has not already decided.
 */
export type SavingRulePreview = {
  from: string
  until: string
  occurrences: RuleForecast[]
  bills: BillForecast[]
}

/**
 * What a rule nobody has saved would do if it fired this minute: the balance it would draw from, the
 * figure, and whether that figure could actually be honoured today.
 *
 * <p>Nothing is written by asking. No rule, no occurrence, no deposit and no point — which is what
 * makes it safe to ask again on every keystroke.
 */
export type SavingRuleDryRun = {
  asAt: string
  balance: number
  outcome: OccurrenceOutcome
  wouldMove: WhatWouldMove
  shortfall: number | null
}

/**
 * What a customer has declared lands in one current account every month, or that they have declared
 * nothing.
 *
 * <p>`declared` is the answer rather than an absence, because "nobody has said" is a fact about an
 * account that exists and a page has something to draw for it: the boxes to type one into.
 */
export type MonthlyIncome = {
  currentAccountId: number
  declared: boolean
  dayOfMonth: number | null
  amount: number | null
  /** `YYYY-MM-DD`, and null when nothing has been declared. */
  nextPayday: string | null
  declaredAt: string | null
}

/**
 * A rule as somebody has typed it, on its way to being saved or tried out.
 *
 * <p>Every figure travels as the text that was typed, the way a deposit's amount and a goal's target
 * do. What a day of the month is, what an amount of money is and whether shares add to a hundred are
 * the backend's rulings, and a page that turned "5o" into a number first would be answering in its
 * own words a question that has a sentence waiting for it.
 *
 * <p>The fields a rule does not need are left out rather than sent empty: a weekly rule has no day
 * of the month and a fixed amount has no floor, and the backend refuses a rule carrying a figure it
 * would have no use for.
 */
export type ARuleAsTyped = {
  name: string
  fromCurrentAccountId: number
  trigger: string
  dayOfWeek?: string
  dayOfMonth?: string
  howMuchMoves: string
  amount?: string
  floor?: string
  split?: { goalId: number; share: string }[]
}

/** What a change says: only the parts that are being changed travel, and the rest is left alone. */
export type AChangeToARule = {
  name?: string
  trigger?: string
  dayOfWeek?: string
  dayOfMonth?: string
  howMuchMoves?: string
  amount?: string
  floor?: string
  split?: { goalId: number; share: string }[]
}

/** The rules standing against one savings account, in the order their holder wrote them. */
export async function fetchSavingRules(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<SavingRule[]> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/saving-rules`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load your automatic saving'))
  }
  return response.json()
}

/**
 * The rules that have been ended, read back so that the money they moved stays explainable.
 *
 * <p>A read of its own rather than a flag on the list above, which is how the backend answers it:
 * the ordinary read stays the ordinary read, and "what happened to my money last year" is still
 * answerable after the instruction behind it has been closed.
 */
export async function fetchEndedSavingRules(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<SavingRule[]> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/saving-rules/ended`, {
    signal,
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the rules you have ended'))
  }
  return response.json()
}

/** One rule's own history, newest first: every day it fell due and what became of it. */
export async function fetchRuleHistory(
  savingsAccountId: number,
  ruleId: number,
  signal?: AbortSignal,
): Promise<RuleOccurrence[]> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/saving-rules/${ruleId}/history`,
    { signal },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load what this rule has done'))
  }
  return response.json()
}

/** What every rule on this account will do over the coming twelve months, in date order. */
export async function fetchRulesPreview(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<SavingRulePreview> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/saving-rules/preview`, {
    signal,
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the year ahead'))
  }
  return response.json()
}

/**
 * What a rule nobody has saved would move if it fired this minute.
 *
 * <p>A POST because the rule travels in the body, and not because anything is written: the backend
 * is explicit that no rule, occurrence, deposit or point comes out of it. That is what lets the form
 * ask it again as the amount is typed.
 */
export async function dryRunSavingRule(
  savingsAccountId: number,
  rule: ARuleAsTyped,
): Promise<SavingRuleDryRun> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/saving-rules/preview`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(rule),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not work out what this rule would move'))
  }
  return response.json()
}

/**
 * What a rule that already stands would move if it fired this minute with a change applied to it.
 *
 * <p>A path of its own rather than the preview above, and the difference is the ten-rule limit: the
 * preview of an unsaved rule refuses a customer who has no room for another, which is the right
 * answer about a rule that would be an eleventh and the wrong answer about one that already exists.
 * A change makes no room, so a customer holding ten can still see what a change to one of them
 * would move.
 *
 * <p>Nothing is written by asking, exactly as above, which is what lets the change form ask it again
 * on every keystroke.
 */
export async function dryRunAChangeToARule(
  savingsAccountId: number,
  ruleId: number,
  change: AChangeToARule,
): Promise<SavingRuleDryRun> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/saving-rules/${ruleId}/preview`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(change),
    },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not work out what this rule would move'))
  }
  return response.json()
}

/** Leaves a rule standing, and answers with the rule that now exists. */
export async function leaveARuleStanding(
  savingsAccountId: number,
  rule: ARuleAsTyped,
): Promise<SavingRule> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/saving-rules`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(rule),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The rule was not left standing'))
  }
  return response.json()
}

/** Changes whichever parts of a rule were sent, and leaves the rest of it alone. */
export async function changeSavingRule(
  savingsAccountId: number,
  ruleId: number,
  change: AChangeToARule,
): Promise<SavingRule> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/saving-rules/${ruleId}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(change),
    },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The rule was not changed'))
  }
  return response.json()
}

/** Stops a rule until its holder resumes it. Pressing it twice is accepted quietly. */
export async function pauseSavingRule(
  savingsAccountId: number,
  ruleId: number,
): Promise<SavingRule> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/saving-rules/${ruleId}/pause`,
    { method: 'POST' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The rule was not paused'))
  }
  return response.json()
}

/** Starts a paused rule again from this moment. What fell while it was paused is never made up. */
export async function resumeSavingRule(
  savingsAccountId: number,
  ruleId: number,
): Promise<SavingRule> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/saving-rules/${ruleId}/resume`,
    { method: 'POST' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The rule was not resumed'))
  }
  return response.json()
}

/** Ends a rule for good. The instruction stops existing; its record does not. */
export async function endSavingRule(
  savingsAccountId: number,
  ruleId: number,
): Promise<SavingRule> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/saving-rules/${ruleId}`,
    { method: 'DELETE' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The rule was not ended'))
  }
  return response.json()
}

/**
 * One current account on its own: what is in it, who holds it, and what its holder says lands in it
 * every month.
 *
 * <p>The read behind the account's own screen. `CurrentAccount` above is the same account as one row
 * of the customer's directory, which is what the overview draws its cards from; this is the account
 * asked for by itself, and it is what a page about one account reads rather than fetching every
 * account somebody holds to pick a row out of it.
 *
 * <p>The income arrives inside it, in the shape the income's own endpoint answers with, so a page
 * that has just declared one and a page that has just been opened are looking at one shape rather
 * than two.
 */
export type TheCurrentAccount = {
  currentAccountId: number
  iban: string
  customerName: string
  balance: number
  income: MonthlyIncome
  /**
   * What the holder says leaves the account every month — the standing bills, in the order they
   * were declared. They come down with the account because the page draws them in the same breath
   * as the balance they are claimed against; what has been ended is a record rather than a claim
   * and is read from {@link fetchEndedBills}.
   */
  bills: RecurringBill[]
  /**
   * What the account still owes, oldest first — every date a bill fell due on that could not be
   * paid and has not been settled since.
   *
   * <p>A claim on the balance exactly as a standing bill is, and already overdue, so it arrives with
   * the account rather than on a request of its own: the page exists to show what arrives, what goes
   * out and what is owed in one place. Empty when nothing is owed, and the page draws no section at
   * all for it — a panel that is there every day is a panel people learn to skip.
   */
  arrears: Arrear[]
  /**
   * What the month ahead has to cover: what is in the account, what is due to arrive, what is due
   * to leave and what that leaves. The sentence the three lists above add up to, and the reason
   * this page exists — a balance on its own says "you have 2480 euros", and this says "you have
   * 2480 euros and 1165 of it is spoken for".
   */
  monthAhead: MonthAhead
}

/**
 * What one current account has to cover between today and this day next month.
 *
 * <p>The figures add up: `leavesYou` is `balance + incomeDue - billsDue` to the cent, worked out by
 * the backend. A page doing its own subtraction would be a second place this application decides
 * what a month costs.
 *
 * <p>`billsDue` counts what is already owed as well as what is still to fall, because an arrear is a
 * claim on this balance exactly as a standing bill is and the nightly run offers the money to it
 * first. `arrearsOutstanding` says how much of the figure that is.
 *
 * <p>`leavesYou` may be negative, and that is the answer rather than an error: a month that cannot
 * be paid in full unless something changes is exactly the month worth being told about.
 */
export type MonthAhead = {
  /** `YYYY-MM-DD`, the day the window opens — the backend's today, never the browser's. */
  from: string
  /** `YYYY-MM-DD`, the last day it holds, inclusive. */
  until: string
  balance: number
  incomeDue: number
  billsDue: number
  arrearsOutstanding: number
  leavesYou: number
}

/**
 * One thing the account still owes: which bill, the day it was owed from, how late that is by now,
 * and what is still owed for it.
 *
 * <p>`daysLate` is counted to today rather than to when the date was last presented, which is the
 * figure a customer is actually asking for: how long they have been carrying this. It is the
 * backend's own subtraction, as every date arithmetic in this application is.
 *
 * <p>`amount` is what was originally due and never a cent more. Nothing is ever added to an arrear —
 * no interest, no fee, no charge of any kind — because the accumulation is the whole of the
 * consequence.
 */
export type Arrear = {
  id: number
  billId: number
  currentAccountId: number
  billName: string
  dueOn: string
  daysLate: number
  amount: number
}

/** Whether a bill is still going out every month, or has been ended for good. Ending is one-way. */
export type BillState = 'STANDING' | 'ENDED'

/**
 * One thing that leaves a current account every month: a name its holder recognises, a day of the
 * month and an amount.
 *
 * <p>The outbound mirror of {@link MonthlyIncome}, and the shape differs where the ideas do. An
 * income is one per account, so it carries a flag saying whether anything was declared at all; a
 * bill is one of many, so "nothing declared" is an empty list. What a bill carries instead is an
 * identifier — everything done to one afterwards names it — and a state, because an ended bill stays
 * readable.
 *
 * <p>Nothing here says when it will next be taken: the day of the month says when it falls due, the
 * backend clamps that to the last day of a short month, and a forecast of the dates ahead is the
 * timeline's answer rather than this one's.
 */
export type RecurringBill = {
  billId: number
  currentAccountId: number
  name: string
  dayOfMonth: number
  amount: number
  declaredAt: string
  state: BillState
  /** The moment it was ended, and null while it still stands. */
  endedAt: string | null
  /**
   * The day the money last actually left, as YYYY-MM-DD, and null on a bill that has never been
   * taken.
   *
   * The one thing about a standing bill a customer cannot work out for themselves: a current
   * account holds a figure rather than a sum of records, so a balance says nothing about which
   * bills moved it. A date presented against an account that could not cover it is deliberately
   * *not* this date — it is in the bill's own history as unpaid, and claiming it here would tell
   * the customer the opposite of what happened.
   */
  lastTakenOn: string | null
}

/** What became of one date a bill fell due on. Two words, and there is no third: nothing is ever
 * partly taken. */
export type BillOutcome = 'PAID' | 'UNPAID'

/**
 * One date a bill fell due on and what became of it: the day it was owed, the moment it was
 * settled, how late that was, what it asked for and whether it was paid.
 *
 * `dueOn` and `settledAt` are different facts and both are here. After downtime or on a wound clock
 * they are different days, and `daysLate` is the backend's own subtraction between them rather than
 * one this page makes — a second place deciding what a day is would be a second answer.
 *
 * `amount` is what the date asked for on both outcomes: what left on a paid one, and what is still
 * owed on an unpaid one.
 */
export type BillOccurrence = {
  id: number
  billId: number
  currentAccountId: number
  dueOn: string
  settledAt: string
  daysLate: number
  outcome: BillOutcome
  amount: number
}

/**
 * One bill's own history, newest first: every date it fell due and what became of it.
 *
 * Read on demand rather than with the account, for the reason a rule's history is: a bill caught up
 * over three years is thirty-six dates, and a page that opened with all of them for every bill
 * would read an account's whole record to answer "what is standing here".
 */
export async function fetchBillHistory(
  currentAccountId: number,
  billId: number,
  signal?: AbortSignal,
): Promise<BillOccurrence[]> {
  const response = await fetch(
    `/api/current-accounts/${currentAccountId}/bills/${billId}/history`,
    { signal },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load what this bill has done'))
  }
  return response.json()
}

/**
 * The bills no longer standing against the account, so that a bill closed rather than deleted can
 * still be read back.
 *
 * <p>Its own read rather than a flag on the account, so the ordinary read stays the ordinary read —
 * the same shape the ended saving rules and the abandoned goals are read under.
 */
export async function fetchEndedBills(
  currentAccountId: number,
  signal?: AbortSignal,
): Promise<RecurringBill[]> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/bills/ended`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the bills you have ended'))
  }
  return response.json()
}

/**
 * Declares something that leaves the account every month, in the words that were typed.
 *
 * <p>A POST rather than the PUT an income is declared with: there is at most one income per account
 * and a second bill is a second bill.
 */
export async function declareABill(
  currentAccountId: number,
  bill: { name: string; dayOfMonth: string; amount: string },
): Promise<RecurringBill> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/bills`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(bill),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The bill was not declared'))
  }
  return response.json()
}

/**
 * Changes whichever parts of a bill were sent, and leaves the rest of it alone. A field left out is
 * "leave it alone", which is what makes putting the rent up one edit rather than a retyped form.
 */
export async function changeBill(
  currentAccountId: number,
  billId: number,
  change: { name?: string; dayOfMonth?: string; amount?: string },
): Promise<RecurringBill> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/bills/${billId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(change),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The bill was not changed'))
  }
  return response.json()
}

/** Ends a bill for good. The instruction stops existing; its record does not. */
export async function endBill(
  currentAccountId: number,
  billId: number,
): Promise<RecurringBill> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/bills/${billId}`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The bill was not ended'))
  }
  return response.json()
}

/** That account, read by itself. An account nobody has heard of is refused in words, not drawn empty. */
export async function fetchCurrentAccount(
  currentAccountId: number,
  signal?: AbortSignal,
): Promise<TheCurrentAccount> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load this current account'))
  }
  return response.json()
}

/**
 * Declares it, or declares it again, in the words that were typed.
 *
 * <p>There is no read beside it. The declaration comes down with the account it belongs to, in
 * {@link fetchCurrentAccount}, because the page that shows it shows the balance in the same breath —
 * and the endpoint that answers it on its own is still there for anything that ever wants it alone.
 */
export async function declareMonthlyIncome(
  currentAccountId: number,
  dayOfMonth: string,
  amount: string,
): Promise<MonthlyIncome> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/monthly-income`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ dayOfMonth, amount }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The income was not declared'))
  }
  return response.json()
}

/** Takes the declaration back, after which nothing is credited to the account. */
export async function withdrawMonthlyIncome(currentAccountId: number): Promise<MonthlyIncome> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/monthly-income`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The declaration was not withdrawn'))
  }
  return response.json()
}

/** Whether a spending category is still one of the things money goes on, or was ended for good. */
export type CategoryState = 'STANDING' | 'ENDED'

/**
 * One thing a customer says their money goes on: a word of their own, on one current account.
 *
 * <p>A name and nothing else. There is no colour and no icon here because there is none in the
 * backend either — a colour is a reading of a category rather than a fact about one, and it belongs
 * to whoever is drawing it. What the category is allowed to cost is a budget, which is a
 * declaration of its own; what it has actually cost is derived from a month's spends and bills, and
 * is a figure with a month named beside it.
 *
 * <p>`state` rather than an inference from `endedAt`: a page draws a standing category and an ended
 * one differently, and reading the absence of a moment would be working out a fact the backend
 * already knows.
 */
export type SpendingCategory = {
  categoryId: number
  currentAccountId: number
  name: string
  declaredAt: string
  state: CategoryState
  /** The moment it was ended, and null while it still stands. */
  endedAt: string | null
}

/**
 * The categories standing on the account, in the order their holder named them.
 *
 * <p>Read on its own rather than with the account, because they are different questions: the
 * account's page is what arrives, what goes out on a standing instruction and what that leaves, and
 * these are the words the budget screen is built out of.
 */
export async function fetchCategories(
  currentAccountId: number,
  signal?: AbortSignal,
): Promise<SpendingCategory[]> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/categories`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load what your money goes on'))
  }
  return response.json()
}

/**
 * The categories no longer standing, so that one ended rather than deleted can still be read back —
 * and with it the months that were filed under it.
 */
export async function fetchEndedCategories(
  currentAccountId: number,
  signal?: AbortSignal,
): Promise<SpendingCategory[]> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/categories/ended`, {
    signal,
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the categories you have ended'))
  }
  return response.json()
}

/**
 * Names one more thing this account's money goes on, in the words that were typed.
 *
 * <p>A POST rather than a PUT: a second category is a second category with an identifier of its
 * own, and it is that identifier everything afterwards names it by.
 */
export async function declareACategory(
  currentAccountId: number,
  name: string,
): Promise<SpendingCategory> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/categories`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The category was not declared'))
  }
  return response.json()
}

/**
 * Says a category differently. The identifier does not change, so everything already filed under it
 * stays filed under it — which is the whole difference between renaming a category and ending one
 * to declare another.
 */
export async function renameCategory(
  currentAccountId: number,
  categoryId: number,
  name: string,
): Promise<SpendingCategory> {
  const response = await fetch(
    `/api/current-accounts/${currentAccountId}/categories/${categoryId}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name }),
    },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The category was not renamed'))
  }
  return response.json()
}

/**
 * Ends a category for good. The word stops being one the customer uses; what was spent under it
 * does not move, and the backend refuses every later rename or ending of it.
 */
export async function endCategory(
  currentAccountId: number,
  categoryId: number,
): Promise<SpendingCategory> {
  const response = await fetch(
    `/api/current-accounts/${currentAccountId}/categories/${categoryId}`,
    { method: 'DELETE' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The category was not ended'))
  }
  return response.json()
}

/**
 * One part of a split as the API reports it: how much of a spend it accounts for, and what it is
 * filed under.
 *
 * <p>`categoryId` and `categoryName` are both null on an uncategorised part, which is a state the
 * customer chose rather than a gap — money recorded by somebody who had not decided what it was for
 * yet. How to word that on the screen is this side's own reading, the same bargain a category's
 * colour is struck under.
 *
 * <p>The name travels beside the identifier, so a split can be drawn without holding the account's
 * category list — and so that a part filed under a category since ended still says what it says. An
 * ended category has left the standing list and keeps every euro ever filed under it.
 */
export type RecordedSpendPart = {
  categoryId: number | null
  categoryName: string | null
  amount: number
}

/**
 * One spend as the API reports it: what its holder called it, what it cost, when it was recorded,
 * and what it was for.
 *
 * <p>The moment is when the money left the current account, and it comes from the application's
 * clock rather than from anything this side sends. A spend cannot be backdated: there is no date in
 * the request, which is what stops a customer filing a spend into a month they have already read.
 *
 * <p>There is no balance on it. What the account holds afterwards is the account's own read, and a
 * figure copied here would be wrong the moment anything else moved.
 *
 * <p>`correctedAt` is null on a spend nobody has corrected, rather than a stand-in date, and that
 * null is the whole of what the field is for: the ledger says plainly which spends were not right
 * the first time instead of quietly showing something different from what it showed yesterday. How
 * to word the difference on the screen is this side's own reading.
 */
export type RecordedSpend = {
  spendId: number
  currentAccountId: number
  name: string
  amount: number
  recordedAt: string
  correctedAt: string | null
  parts: RecordedSpendPart[]
}

/**
 * One part of a split as it is sent: an amount as the text that was typed, and the category it goes
 * under — or null, which is a customer saying "not yet" rather than a field they forgot.
 */
export type SpendPartToRecord = {
  categoryId: number | null
  amount: string
}

/**
 * The recent spends on the account, newest first, each carrying the split it was recorded with.
 *
 * <p>"What have I spent lately", which is why the last thing is first — the opposite of the
 * categories' own list, which is in the order the customer named them. The backend bounds the list,
 * because what a month cost is a figure rather than a list to be added up by hand.
 */
export async function fetchSpends(
  currentAccountId: number,
  signal?: AbortSignal,
): Promise<RecordedSpend[]> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/spends`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load what you have spent'))
  }
  return response.json()
}

/**
 * The category one recurring bill is in: which bill, which category, what it is called and whether
 * that category is still standing.
 *
 * <p>A shape of its own rather than a field on `RecurringBill`, because it is a different module's
 * answer. The backend keeps the label as a row keyed on the bill so that the module owning the bills
 * never has to hear about categories, and the page puts the two reads side by side on the
 * identifier — which is the same join the money movements list already does with its bills.
 *
 * <p>The category is nullable because taking a bill out of every category answers with exactly that:
 * the bill, and no category behind it. The read of the whole account simply leaves such bills out,
 * so a bill missing from that list is a bill in no category.
 *
 * <p>`categoryState` rather than an inference: a category that was ended keeps the bills that
 * pointed at it, so a page drawing a label has to be able to say that the word behind it is one its
 * holder has stopped using.
 */
export type BillInACategory = {
  billId: number
  currentAccountId: number
  categoryId: number | null
  categoryName: string | null
  categoryState: CategoryState | null
  filedAt: string | null
}

/**
 * Where each of this account's bills is filed, for the bills that are filed anywhere at all.
 *
 * <p>One read for the whole account rather than one per bill: the account screen puts a label on
 * each bill it already has, and the budget screen groups the bills under the categories it already
 * has. Both draw a bill that is missing from this answer as one that is in no category.
 */
export async function fetchBillCategories(
  currentAccountId: number,
  signal?: AbortSignal,
): Promise<BillInACategory[]> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/bills/categories`, {
    signal,
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load what your bills are filed under'))
  }
  return response.json()
}

/**
 * Records what was spent and takes the money for it, in one request.
 *
 * <p>The amounts travel as the text that was typed, the way a deposit's and a bill's do: whether
 * "25,00" is a figure at all is the backend's answer, in a sentence written for the person who
 * typed it, and reading it through a floating-point number on the way there would lose the comma
 * that is the whole of the mistake.
 *
 * <p>Nothing is judged here. Whether the parts add up to what was spent, whether there are too many
 * of them and whether the account holds it are all refusals with sentences waiting for them — and a
 * spend larger than the balance moves nothing at all rather than taking what is there.
 */
export async function recordASpend(
  currentAccountId: number,
  name: string,
  amount: string,
  parts: SpendPartToRecord[],
): Promise<RecordedSpend> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/spends`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, amount, parts }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The spend was not recorded'))
  }
  return response.json()
}

/**
 * Puts a standing bill in a category, moving it from wherever it was.
 *
 * <p>A PUT: a bill is in one category or in none, so this is the whole of that fact being put at an
 * address that names it, and a bill is never split — a customer who wants their rent divided between
 * two categories declares two bills. It re-labels the whole of that bill's history, which is what
 * somebody correcting a mistake means by correcting it.
 */
export async function putBillInACategory(
  currentAccountId: number,
  billId: number,
  categoryId: number,
): Promise<BillInACategory> {
  const response = await fetch(
    `/api/current-accounts/${currentAccountId}/bills/${billId}/category`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ categoryId }),
    },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The bill was not put in that category'))
  }
  return response.json()
}

/**
 * Takes a bill out of every category again. What is unmade is an opinion about a payment rather than
 * the record of the payment: the bill, its declaration and every date it fell due on are untouched.
 */
export async function takeBillOutOfEveryCategory(
  currentAccountId: number,
  billId: number,
): Promise<BillInACategory> {
  const response = await fetch(
    `/api/current-accounts/${currentAccountId}/bills/${billId}/category`,
    { method: 'DELETE' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The bill was not taken out of its category'))
  }
  return response.json()
}

/**
 * Corrects what a spend was for, replacing its whole split in one request.
 *
 * <p>The whole split rather than the part that changed, because the parts have to add up to what
 * was spent: a request that moved one of them would leave the backend to decide which of the others
 * lost the difference, which is exactly the arithmetic somebody is correcting. A PUT, so sending it
 * twice says the same thing.
 *
 * <p><strong>There is nothing beside this.</strong> The amount and the name cannot be changed and a
 * spend cannot be deleted — the money moved, and a record that can be unmade is not a record. The
 * split is the only thing about a spend that was ever an opinion, and this is the only endpoint
 * that touches it.
 *
 * <p>Nothing is judged here. Whether the new parts add up, whether there are too many of them and
 * whether each names a category this account may file money under are all the backend's rulings, in
 * the very sentences recording a spend meets, because a corrected split is held to exactly the
 * rules the original was. A refused correction leaves the old split standing.
 */
export async function correctTheSplitOf(
  currentAccountId: number,
  spendId: number,
  parts: SpendPartToRecord[],
): Promise<RecordedSpend> {
  const response = await fetch(
    `/api/current-accounts/${currentAccountId}/spends/${spendId}/split`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ parts }),
    },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The split was not corrected'))
  }
  return response.json()
}

/**
 * What became of one monthly budget: it is the figure a category is held to now, it was replaced by
 * a figure its holder preferred, or its holder stopped policing the category altogether.
 *
 * <p>Three values rather than two, and `STOPPED` is not `SUPERSEDED` with nothing after it. They
 * read differently: superseding leaves the category with a figure, and stopping leaves it with none
 * — the word is still one this account's money is described in, and its spending is still counted
 * and reported, but nothing is being held against it.
 */
export type BudgetState = 'STANDING' | 'SUPERSEDED' | 'STOPPED'

/**
 * What a customer has said should happen to the difference between what a category was allowed to
 * cost in a month and what it actually cost, when that month ends.
 *
 * <p>Three rules and no fourth. A category can start every month clean, it can carry its surplus
 * forward so that a frugal month buys a generous one, or it can carry both its surplus and its
 * overspend forward — which is envelope budgeting, and the only one of the three where a month can
 * begin with less in it than its budget. That consequence is the whole reason anybody keeps
 * envelopes, so this page draws it rather than softening it.
 *
 * <p>`NOTHING_ROLLS_OVER` is the default, and it is a real choice rather than the absence of one: a
 * customer is allowed not to be haunted by January.
 *
 * <p>No money moves for any of them. An envelope holds nothing — it is a claim on the one balance,
 * exactly as a savings goal is a claim on the one savings balance — so no screen here should draw a
 * pot, a sub-balance or a transfer.
 */
export type RolloverRule =
  | 'NOTHING_ROLLS_OVER'
  | 'THE_SURPLUS_ROLLS_OVER'
  | 'THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER'

/**
 * The three rules in the order a person meets them, with the words this application says them in.
 *
 * <p>Written once, here, beside the type, because a chooser and a caption that named them separately
 * would drift the first time either was edited — and the backend's own names are shouted constants
 * nobody should be asked to read on a screen.
 */
export const theRolloverRules: { rule: RolloverRule; name: string; what: string }[] = [
  {
    rule: 'NOTHING_ROLLS_OVER',
    name: 'Start each month fresh',
    what: 'Whatever is left ends with the month, and so does anything you went over by.',
  },
  {
    rule: 'THE_SURPLUS_ROLLS_OVER',
    name: 'Carry what is left over',
    what: 'A frugal month buys a generous one. Going over is forgiven and never follows you.',
  },
  {
    rule: 'THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER',
    name: 'Carry both ways — an envelope',
    what: 'What is left follows you, and so does what you went over by: overfill it this month and there is less in it next month.',
  },
]

/** What one rollover rule is called on screen, for a row drawing the rule a month was kept under. */
export function theRolloverRuleCalled(rule: RolloverRule): string {
  return theRolloverRules.find((one) => one.rule === rule)?.name ?? rule
}

/**
 * One monthly budget as the API reports it: which category it is on, what that category is allowed
 * to cost, the months the figure governs, and what became of it.
 *
 * <p>The months are text, `"2026-03"`, because that is what a month is. Writing one as the first of
 * the month would invite a page to print a day nobody meant.
 *
 * <p>`stoodThrough` is null while the budget stands, which is not a missing value: a figure in force
 * has no last month, because the customer has not said there is one. The figure is kept rather than
 * cleared when it stops, because the months it governed still quote it.
 */
export type MonthlyBudget = {
  budgetId: number
  currentAccountId: number
  categoryId: number
  categoryName: string
  amount: number
  /**
   * What becomes of the difference when a month this figure governs ends. Half of what was
   * declared, so a page that has just changed a rule draws the rule it now has without fetching a
   * month to find out — and a superseded row quotes the rule it governed its months under.
   */
  rollover: RolloverRule
  effectiveFrom: string
  stoodThrough: string | null
  state: BudgetState
  declaredAt: string
  stoodDownAt: string | null
}

/**
 * One spending category's month as the API reports it: what it was allowed to cost, what its bills
 * committed to it, what its holder chose to spend, the two added, and what that leaves.
 *
 * <p>Every figure arrives worked out. `spent` is `committed + discretionary` and `left` is
 * `budgeted - spent`, both to the cent, so this page draws a bar and a number and decides nothing —
 * and so that the account's card and the budget screen cannot arrive at two different answers about
 * one category.
 *
 * <p><strong>`budgeted` and `left` are null when nothing was declared, and this page must draw that
 * as "no budget" rather than as a nought.</strong> A category with no figure is being watched and
 * not policed, which is a thing a customer is allowed to choose; a blank drawn as 0.00 would tell
 * somebody they had overspent a limit they never set.
 *
 * <p>`left` may be negative and is drawn as it is. Nothing refuses a spend for being over a budget —
 * the balance is the only hard constraint in this application — so a category going over is a fact
 * to show rather than an error.
 *
 * <p>`overspent` comes down rather than being worked out here, because "over" is a comparison
 * against an absent budget as well as a small one, and a page comparing `left < 0` would call every
 * unbudgeted category overspent the moment it printed a null as a nought.
 *
 * <p>`carriedIn` is what the months before this one handed this category under the rule that stood
 * in each of them, and `allowed` is the budget and that carry together — which is what `left` is
 * taken from and what a bar should be drawn against. A bar drawn against `budgeted` on a category
 * carrying forty euros forward would call a month overspent that is nothing of the kind, and a page
 * adding the two itself would be a second place this application decides what a month allows.
 *
 * <p>`allowed` may be negative: an envelope overfilled last month leaves this one with less than
 * nothing to spend. It is drawn as the negative figure it is, because the consequence is the whole
 * reason anybody keeps envelopes and clamping it at nought would remove the mechanic while leaving
 * the word.
 *
 * <p>`rollover` is the rule that stood in *this* month rather than the one standing now, so a month
 * already gone reads as the month its holder actually lived through. It is null exactly where
 * `budgeted` is: there is no rule on a limit nobody set, nothing carries into a month nobody was
 * measuring, and the four are absent together or present together.
 */
export type CategorySpending = {
  categoryId: number
  name: string
  categoryState: CategoryState
  rollover: RolloverRule | null
  budgeted: number | null
  carriedIn: number | null
  allowed: number | null
  committed: number
  discretionary: number
  spent: number
  left: number | null
  overspent: boolean
}

/**
 * One current account's month as the API reports it: which month, the days it runs between, the
 * account's totals, and a row per category under them.
 *
 * <p>One read answers both screens. The current account's budget card draws the totals and the
 * categories nearest their limit; the budget screen draws every row with a bar. They cannot disagree
 * about what the month cost, because they are two readings of one answer.
 *
 * <p>`uncategorised` is money that left and is filed under nothing — a spend can carry a part with
 * no category at all, which is a state a customer chooses. It is deliberately not inside `spent`,
 * which is the sum of the rows: a total holding money belonging to none of them would be a total
 * nobody could check, and euros that simply vanished from the page would be worse still.
 *
 * <p>Every figure is derived on the read from the declarations and the movements. Nothing is stored,
 * so correcting a split or moving a bill into another category changes the month it happened in.
 */
export type MonthOfSpending = {
  currentAccountId: number
  month: string
  from: string
  until: string
  budgeted: number | null
  /**
   * What every budgeted category on the account was handed by the months before this one, added up,
   * and what the budgets plus that carry allow altogether. `left` is taken from `allowed` rather
   * than from `budgeted`, so the card and the rows under it agree about a month that a frugal
   * spring paid for. Both are null exactly where `budgeted` is, and both may be negative.
   */
  carriedIn: number | null
  allowed: number | null
  committed: number
  discretionary: number
  spent: number
  left: number | null
  uncategorised: number
  categories: CategorySpending[]
}

/**
 * How the month the application's clock is in is going on one current account.
 *
 * <p>Without naming a month, which is the whole point of the address: this side would have to work
 * out what month it is, and on a wound clock it would get that wrong — a screen quoting a month
 * nobody in this application is in is exactly the bug the read exists to avoid.
 */
export async function fetchThisMonthsSpending(
  currentAccountId: number,
  signal?: AbortSignal,
): Promise<MonthOfSpending> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/spending`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load how this month is going'))
  }
  return response.json()
}

/**
 * The same answer about a month already gone, named as the year and the month — `"2026-03"`.
 *
 * <p>The same fold over the same records, asked about a different window, which is what makes "a
 * budget raised in June left April quoting the figure that stood then" something a customer can go
 * and look at.
 */
export async function fetchSpendingInMonth(
  currentAccountId: number,
  yearMonth: string,
  signal?: AbortSignal,
): Promise<MonthOfSpending> {
  const response = await fetch(
    `/api/current-accounts/${currentAccountId}/spending/${yearMonth}`,
    { signal },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, `Could not load ${yearMonth}`))
  }
  return response.json()
}

/**
 * Says what a category is allowed to cost each month, superseding whatever stood before.
 *
 * <p>A PUT, and the same request whether or not a figure is already there: what the customer is
 * saying is "from now on, this much", and whether they had said anything before is the backend's
 * business rather than theirs. Underneath, the figure that stood is kept rather than written over,
 * so the months it governed go on quoting it.
 *
 * <p>The amount travels as the text that was typed, the way a bill's and a spend's do: whether
 * "250,00" is a figure at all is the backend's answer, in a sentence written for the person who
 * typed it, and reading it through a number on the way there would lose the comma that is the whole
 * of the mistake.
 *
 * <p>The rollover rule goes with it rather than to an address of its own, because the two are one
 * declaration — "from now on, this much, and this is what happens to what is left of it" — and one
 * supersession covers both. Changing only the rule therefore sends the figure that is already there
 * beside it, and what the months already gone quote does not move.
 */
export async function declareABudget(
  currentAccountId: number,
  categoryId: number,
  amount: string,
  rollover: RolloverRule,
): Promise<MonthlyBudget> {
  const response = await fetch(
    `/api/current-accounts/${currentAccountId}/categories/${categoryId}/budget`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ amount, rollover }),
    },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The budget was not set'))
  }
  return response.json()
}

/**
 * Stops budgeting a category without ending the category.
 *
 * <p>What is unmade is the figure and not the word: the category stays on the list, money can still
 * be filed under it, and its spending is still reported — with a budget that is absent rather than a
 * nought. The months the figure governed keep quoting it, which is why this is a stop rather than a
 * deletion.
 */
export async function stopBudgeting(
  currentAccountId: number,
  categoryId: number,
): Promise<MonthlyBudget> {
  const response = await fetch(
    `/api/current-accounts/${currentAccountId}/categories/${categoryId}/budget`,
    { method: 'DELETE' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The budget was not stopped'))
  }
  return response.json()
}

/**
 * One month of one category as the comparison reports it: which month, what stood over it, what it
 * cost, and the difference.
 *
 * <p>The same figures a `CategorySpending` carries, with the month said out loud, because six of
 * these are a list and a row that did not name its month would be one this page had to name from its
 * position in an array.
 *
 * <p>`month` is `"2026-03"`, which is what a month is.
 *
 * <p><strong>`budgeted`, `carriedIn`, `allowed` and `left` are null in a month no figure stood in,
 * and this page must draw that as "no budget" rather than as a nought.</strong> That is the whole of
 * what the comparison exists to say about the months before a customer had a budget: the spending is
 * a fact and is shown, and the absence of a limit is shown as an absence. A blank drawn as 0.00
 * would tell somebody they had overspent a figure they never set, which is exactly the wrong lesson
 * to take from your own history.
 *
 * <p>`allowed` and `left` may be negative and are drawn as they are, for the reason a month's own
 * row gives: an envelope overfilled leaves the next month allowing less than nothing.
 */
export type MonthCompared = {
  month: string
  rollover: RolloverRule | null
  budgeted: number | null
  carriedIn: number | null
  allowed: number | null
  committed: number
  discretionary: number
  spent: number
  left: number | null
  overspent: boolean
}

/**
 * One spending category over the last few months: a row per month it was live in, the average of the
 * months behind this one, and this month quoted against it.
 *
 * <p><strong>`months` holds only the months the category existed for</strong>, oldest first, and may
 * be shorter than the window the account's read names. A category declared last month has one row,
 * and one that has been ended stops at the month it was ended in. Neither is padded with noughts,
 * because a nought is a claim that somebody spent nothing on a thing that was not one of the things
 * they spent on — so this page lines the rows it has up under the account's column headings rather
 * than assuming there is one per column.
 *
 * <p><strong>`trailingAverage` is over the months before this one and is null when there are
 * none.</strong> An average that included this month would be comparing a month with itself. A
 * category in its first month has nothing behind it, which is an absence and not a nought: drawing
 * EUR 0.00 there would say the customer used to spend nothing, and the comparison beside it would
 * report their first real month as infinitely above a habit that does not exist.
 *
 * <p>`monthsTheAverageIsOver` is between one and three and is worth printing beside the figure,
 * because somebody told "EUR 92.00 on average" wants to know whether that is three months or one.
 *
 * <p>`comparedWithTheAverage` is this month's spending less that average: positive for a month
 * costing more than usual, negative for one costing less. It comes down worked out rather than being
 * subtracted here, so that two screens cannot decide differently what "more than usual" means. It is
 * null where the average is, and on a category that is not live this month — an ended one has no
 * `thisMonth` to quote.
 */
export type CategoryCompared = {
  categoryId: number
  name: string
  categoryState: CategoryState
  months: MonthCompared[]
  thisMonth: MonthCompared | null
  trailingAverage: number | null
  monthsTheAverageIsOver: number
  comparedWithTheAverage: number | null
}

/**
 * One current account's last few months: which months they are, and a row of history per category.
 *
 * <p>One read for the whole comparison. A request per month, or per category, would be six or twenty
 * round trips for one screen and would fold the same carry chain every time.
 *
 * <p>`months` is the window every category is drawn against, oldest first, so this page draws one set
 * of column headings and lines each category's own shorter run of months up under them. `month` is
 * the month the application's clock is in and `earliest` the one the window begins with — both sent
 * rather than worked out here, because a page doing its own month arithmetic would name the wrong
 * months on a wound clock.
 *
 * <p>A category the window holds nothing for is not in `categories` at all: one ended before the
 * window began is a record of months this read is not about.
 */
export type SpendingHistory = {
  currentAccountId: number
  month: string
  earliest: string
  months: string[]
  categories: CategoryCompared[]
}

/**
 * The last few months on one current account, category by category, with the average behind them.
 *
 * <p>Without naming a month, for the reason `fetchThisMonthsSpending` takes none: the comparison
 * always ends with the month the application's clock is in, and this side working that out would get
 * it wrong the moment a trainer wound the clock.
 *
 * <p>Every figure in it is derived on the read. Correct a split in a month already gone and asking
 * again is the whole of what makes this page agree with the backend about what that month cost.
 */
export async function fetchSpendingHistory(
  currentAccountId: number,
  signal?: AbortSignal,
): Promise<SpendingHistory> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/spending/history`, {
    signal,
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load how the last few months went'))
  }
  return response.json()
}

/**
 * One of the six weekly rows: the days it runs between, what arrives in it, what is committed in
 * it, what the budgets claim of it, and what that leaves.
 *
 * <p>The four figures add up — `leftOver` is `arriving - committed - claimedByBudgets` — and the
 * subtraction is the backend's, so this page draws figures a customer can check against each other
 * with a pencil rather than doing the arithmetic a second time and disagreeing with the card above.
 *
 * <p>`committed` is the bill dates falling in the week and, on the first row, every arrear
 * outstanding: a debt is offered the very next money in, so it is a claim on this week rather than
 * on the week its date comes round again.
 *
 * <p>`claimedByBudgets` assumes every budget is spent to its limit — what is left of them in the
 * month the clock is in, and the whole of them in the months after it — which is what makes
 * `leftOver` a floor rather than a hope.
 *
 * <p>`takesMoreThanItBrings` is the comparison already made, true exactly when `leftOver` is
 * negative. That is a real week and not an error, and it is the one this card exists to show
 * coming.
 */
export type WeekAhead = {
  startsOn: string
  endsOn: string
  arriving: number
  committed: number
  claimedByBudgets: number
  leftOver: number
  takesMoreThanItBrings: boolean
}

/**
 * The next six weeks of a current account's cash flow, and what that says its holder could save
 * each week.
 *
 * <p>Six weeks beginning on the Monday the current week began on — the week boundary the streak
 * already counts in, so a budgeting week and a streak week are the same seven days. `from` and
 * `until` are read rather than worked out here, for the reason {@link MonthAhead}'s are: this side
 * adding sevens to its own idea of today would draw six weeks nobody in the application is in on a
 * wound clock.
 *
 * <p>`couldSaveWeekly` is what the six weeks leave, divided by six. It is **offered and never
 * applied**: nothing about reading this card writes to the declared saving capacity, and adopting
 * it is a press that sends this figure to the capacity's own address. `worthOffering` is the
 * comparison already made, so this page does not decide for itself when there is an offer to show.
 *
 * <p>`leftOver` may be negative, and `couldSaveWeekly` never is: a customer whose six weeks do not
 * cover themselves could save nothing, and a negative offer is not an offer.
 */
export type TheWeeksAhead = {
  currentAccountId: number
  from: string
  until: string
  arriving: number
  committed: number
  claimedByBudgets: number
  leftOver: number
  couldSaveWeekly: number
  worthOffering: boolean
  weeks: WeekAhead[]
}

/**
 * The six weeks ahead on one current account.
 *
 * <p>Without naming a week, which is the whole point of the address: this side would have to work
 * out which Monday the current week began on, and on a wound clock it would get that wrong.
 */
export async function fetchWeeksAhead(
  currentAccountId: number,
  signal?: AbortSignal,
): Promise<TheWeeksAhead> {
  const response = await fetch(`/api/current-accounts/${currentAccountId}/weeks-ahead`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the weeks ahead'))
  }
  return response.json()
}

/**
 * Branches of a future that nobody has taken: what may be asked about one savings account, and what
 * comes back.
 *
 * <p>Every one of these is read and never written. Asking is free in the strong sense the backend's
 * own resource promises: no account moves, no deposit is made, no point is earned or expired, no
 * rule is created and no row is written anywhere. The scenarios themselves are written down nowhere
 * either — not here, not there — which is why the page that builds them holds them in its own state
 * and says out loud that closing it loses them.
 */
export type AKindOfAdjustment =
  | 'SAVE_MORE_EACH_WEEK'
  | 'STOP_FOR_A_WHILE'
  | 'TAKE_MONEY_OUT'
  | 'MOVE_A_DEADLINE'

/**
 * One change to a future, as it is asked about: the kind, and the boxes that kind needs.
 *
 * <p>Four optional boxes rather than four shapes, which is how the backend reads it — a kind reads
 * its own boxes, and the web layer there does nothing but turn the characters into a figure and a
 * day. The boxes a kind has no use for are left out rather than sent empty, exactly as a saving
 * rule as typed leaves out the fields it has no use for.
 *
 * <p><strong>`amount` travels as the text that was typed</strong>, the way a deposit's amount and a
 * goal's target do. Whether "25,00" or "5o" is an amount of money is the backend's ruling and comes
 * back as a sentence shown unchanged; a page that turned it into a number on the way would be
 * answering that question itself, badly, in floating point.
 *
 * <p>`on` and `until` are plain `YYYY-MM-DD` days for the same reason: whether a day is inside the
 * twelve months the simulation is drawn over is the backend's ruling, in words that name the window.
 */
export type AnAdjustmentAsAsked = {
  kind: AKindOfAdjustment
  amount?: string
  on?: string
  until?: string
  goalId?: number
}

/**
 * One future to ask about: what the customer calls it, and the changes that make it what it is.
 *
 * <p>The name is the customer's own and nothing here judges it. Two futures may be called the same
 * thing, which is why the page that draws them keys its columns off its own identifiers rather than
 * off this word.
 */
export type AScenarioToAskAbout = {
  called: string
  adjustments: AnAdjustmentAsAsked[]
}

/**
 * What kind of dated thing a branch has in it.
 *
 * <p><strong>`figure` means a different thing in each of them</strong>, and there is no one rule for
 * formatting it: euros for a goal's target, for what a short withdrawal did take and for money that
 * arrived and earned nothing; points for a bonus and for an expiry; and a run of weeks for a week
 * that was lost. Whoever draws these owns a sentence per kind, which is the price of events that
 * carry a figure and no words — and the right price, because the words belong to the screen and the
 * figure belongs to the fold.
 */
export type AKindOfThingThatHappens =
  | 'MONEY_ARRIVES_AND_EARNS_NOTHING'
  | 'POINTS_EXPIRE'
  | 'A_BONUS_IS_PAID'
  | 'A_WITHDRAWAL_FALLS_SHORT'
  | 'A_GOAL_IS_REACHED'
  | 'A_DEADLINE_IS_MISSED'
  | 'A_WEEK_IS_LOST'

/**
 * One dated thing that happens in a branch: the day, the kind and the figure.
 *
 * <p>Ordered by day, and within a day in the order the night runs them, which is the order the
 * account's own bar already established for the same reason: a bonus credited this morning and a
 * batch expiring this morning are a different day's worth of points depending on which went first.
 * Nothing re-sorts them.
 */
export type AThingThatHappens = {
  on: string
  kind: AKindOfThingThatHappens
  figure: number
}

/**
 * One of the twelve rows of a branch: where the money, the points and the run of weeks stand at the
 * end of a month, and what moved inside it.
 *
 * <p><strong>`month` is a caption and `closesOn` is the day.</strong> The rows are anniversary-
 * relative rather than calendar months — a window opening on the 20th closes its `2026-10` row on
 * 2026-10-20 — so anything positioning a row on a timeline positions it by `closesOn`, and `month`
 * is only what the row is called.
 *
 * <p>Every figure is a JSON number rather than the string an amount travels *to* the backend as:
 * these are derived, not typed, and `300.00` arrives as `300.0`. They are formatted before they are
 * shown and never printed raw.
 *
 * <p>Nothing is summed across the twelve and nothing is summed across branches. The same point can
 * be paid on an anniversary and expire inside the same twelve months, so a net would count one point
 * twice in opposite directions — the argument the account's own bar already makes.
 */
export type AMonthOfTheFuture = {
  /** `"2026-10"`, which is what the row is called rather than where it sits. */
  month: string
  /** The day this row closes on, which is where it sits. */
  closesOn: string
  balance: number
  pointsStanding: number
  securedWeeks: number
  pointsEarned: number
  pointsABonusPaid: number
  pointsThatExpired: number
}

/**
 * How one branch turns out: what it is called, the two days it is drawn between, its twelve months
 * and the dated things that happen in it.
 *
 * <p>`called` is the customer's own word for it, except on the branch nobody asked for — the future
 * they are already in, which the backend names itself and which is always the first in the list.
 * Whoever draws these reads that name rather than writing one, because the word the backend chose is
 * the word the log and the API both use.
 */
export type HowAScenarioTurnsOut = {
  called: string
  from: string
  until: string
  months: AMonthOfTheFuture[]
  thingsThatHappen: AThingThatHappens[]
}

/** A deposit that still holds money, and the day it landed, which its anniversaries are counted from. */
export type ADepositStillHoldingMoney = {
  depositId: number
  stillHolding: number
  landedAt: string
}

/** How many of the customer's points go on one day. */
export type PointsGoingOnADay = {
  on: string
  points: number
}

/** A current account the fold draws on: what is in it, what it is paid and what it owes. */
export type ACurrentAccountBehindIt = {
  currentAccountId: number
  balance: number
  income: MonthlyIncome
  bills: RecurringBill[]
}

/**
 * The present every branch starts from, read once on the day the application's clock reads.
 *
 * <p>Sent with the answer rather than left to be asked for separately, because every branch was
 * folded off exactly this and a page that read the account again would be comparing twelve months of
 * arithmetic against a present that had moved underneath it.
 *
 * <p>The streak figures are the customer's rather than this account's, which is the snapshot's own
 * decision and worth knowing before quoting them beside one pot: a week is secured by what somebody
 * put away across every savings account they hold.
 */
export type WhereThisAccountStands = {
  /** The day the clock reads, and the day the window opens on. */
  asAt: string
  savingsAccountId: number
  customerId: number
  balance: number
  deposits: ADepositStillHoldingMoney[]
  pointsGoing: PointsGoingOnADay[]
  pointsStanding: number
  allocated: number
  unallocated: number
  /** The live goals on this account, in rank order — which is what a deadline can be moved on. */
  goals: SavingsGoal[]
  weeklyCapacity: SavingCapacity
  rules: SavingRule[]
  currentAccounts: ACurrentAccountBehindIt[]
  newSavingsThisWeek: number
  weeklyMinimum: number
  stillNeededThisWeek: number
  currentStreakWeeks: number
  bestStreakWeeks: number
  currentMultiplier: number
  mostEverSaved: number
}

/**
 * The answer to "what if I did this instead": the window, the present every branch starts from, and
 * one turned-out future per branch.
 *
 * <p><strong>`from` and `until` come down and are never worked out here.</strong> This application's
 * clock can be wound a year forward for a demonstration, and a page positioning twelve months
 * against `new Date()` would draw a year nobody is in — the same reason the account's own bar takes
 * its two days from the backend.
 *
 * <p><strong>`scenarios[0]` is always the future the customer is already in</strong>, whether or not
 * anybody asked for a branch, and it is identified by being first and by the name the backend gave
 * it. A projection with nothing to compare against answers no question: "EUR 4 210 in September" is
 * a number, and "EUR 4 210 rather than the EUR 3 890 you were heading for" is an argument. Every
 * such difference is worked out by whoever draws this — nothing is subtracted for us, by design.
 *
 * <p>`anIllustrationRatherThanAPromise` is said once about the whole answer rather than on each of
 * ninety fields, and the screen says it once too.
 */
export type TheFutures = {
  from: string
  until: string
  anIllustrationRatherThanAPromise: boolean
  whereThisAccountStands: WhereThisAccountStands
  scenarios: HowAScenarioTurnsOut[]
}

/**
 * A simulation the backend would not fold, and which part of the question it was about.
 *
 * <p>The sentence is the rule's own and is shown unchanged, exactly as every other refusal on these
 * screens is. What this class adds is where to point: the backend hangs `scenario`, `changeNumber`
 * and `change` beside `detail` as RFC 9457 extension members, deliberately rather than prefixing the
 * sentence, so that one mistake reads the same however many columns are on the screen. They are
 * absent — null here — when the refusal is about the asking as a whole rather than about one column.
 *
 * <p><strong>A refusal aborts the whole request, and there is no partial answer.</strong> Nothing
 * comes back for the columns that were fine, so the page keeps the customer's scenarios itself and
 * draws them again with the refusal beside the one it names.
 */
export class TheSimulationWasRefused extends Error {
  readonly scenario: string | null
  readonly changeNumber: number | null
  readonly change: string | null

  constructor(
    reason: string,
    scenario: string | null,
    changeNumber: number | null,
    change: string | null,
  ) {
    super(reason)
    this.name = 'TheSimulationWasRefused'
    this.scenario = scenario
    this.changeNumber = changeNumber
    this.change = change
  }
}

/**
 * Asks what these futures would look like, beside the one the customer is already in.
 *
 * <p><strong>A `POST` for something that reads</strong>, and the backend's own resource argues the
 * case: four scenarios of ten changes each is a body rather than a query string. Nothing is written
 * by it, and asking again with the same scenarios gets the same answer.
 *
 * <p>All the branches go up in one request because they are one question. Asking per column would
 * fold a different present under each of them the moment anything moved, and two columns drawn off
 * two snapshots are not a comparison.
 *
 * <p>A request carrying no scenarios is a question too — "what am I heading for" — and comes back
 * with the one branch nobody has to ask for. That is how the page draws something before anybody has
 * typed a thing.
 */
export async function askAboutTheseFutures(
  savingsAccountId: number,
  scenarios: AScenarioToAskAbout[],
  signal?: AbortSignal,
): Promise<TheFutures> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/simulations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ scenarios }),
    signal,
  })
  if (!response.ok) {
    const problem = await theProblemIn(response)
    const named = problem as { scenario?: unknown; changeNumber?: unknown; change?: unknown } | null
    throw new TheSimulationWasRefused(
      theReasonIn(problem, response.status, 'Could not work out what these futures would look like'),
      typeof named?.scenario === 'string' ? named.scenario : null,
      typeof named?.changeNumber === 'number' ? named.changeNumber : null,
      typeof named?.change === 'string' ? named.change : null,
    )
  }
  return response.json()
}

/**
 * One line of what adopting a branch changed: which of the four changes it was about, whether the
 * application made it or it is the customer's to make, and the sentence they read.
 *
 * <p><strong>`yoursToCarryOut` is the field a screen must not ignore.</strong> Two of the four kinds
 * are deliberately not applied — a withdrawal is a thing a customer does on the day, and pausing is
 * what a rule's own pause already is — and they come back in the same list as the changes that were
 * written, precisely so that a page cannot show a plan with the withdrawal quietly missing from it.
 * A half-told plan is the one thing this press exists to prevent.
 *
 * <p>`what` is the backend's own sentence and is shown unchanged, as every other sentence on these
 * screens is. It carries the figures — what the capacity was raised to, by, and from; which goal is
 * now wanted by which day — because those figures are the plan, and a page that reworded them would
 * be a second place the plan lived.
 */
export type AChangeThePlanNowCarries = {
  kind: AKindOfAdjustment
  yoursToCarryOut: boolean
  what: string
}

/**
 * What one press of adopt changed: the pot, the branch's own name, and one line per change in the
 * order the customer built it.
 *
 * <p><strong>What changed, and not the account as it now stands.</strong> The figures this press
 * moved live on the screens that own them — the weekly plan and the goal — and they are read from
 * there rather than from here, which is why the page re-asks after a press instead of patching what
 * it already had.
 *
 * <p>Nothing here is an illustration. Every other answer this resource gives is a projection and
 * says so at the top; this one is a record of writes that have happened, and it deliberately carries
 * no such word.
 */
export type WhatAdoptingChanged = {
  savingsAccountId: number
  called: string
  changes: AChangeThePlanNowCarries[]
}

/**
 * Turns one branch into the plan the customer has decided on, and says exactly what changed.
 *
 * <p><strong>This one writes, and it is the only thing on this screen that does.</strong> Everything
 * else here is free in the strong sense; this raises a declared weekly capacity and moves a goal's
 * deadline, through the modules that own them, which is why whoever calls it has to re-read whatever
 * it may have moved rather than assuming the screen is still true.
 *
 * <p><strong>All of it or none of it.</strong> A refusal means nothing at all was applied — not the
 * parts before the one that failed — so there is no partial answer to merge and nothing to undo. The
 * sentence is the owning module's own, word for word: a deadline in the past is refused in the words
 * the goal screen already refuses one in. What comes back beside it is a locator and never a second
 * reason, in the same three extension members a refused simulation carries, which is why this throws
 * the same class.
 *
 * <p>The scenario goes up exactly as it was asked about, so that "adopt this column" means "adopt
 * the thing that produced this column" and cannot mean anything else.
 */
export async function adoptThisFuture(
  savingsAccountId: number,
  scenario: AScenarioToAskAbout,
  signal?: AbortSignal,
): Promise<WhatAdoptingChanged> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/simulations/adopt`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(scenario),
      signal,
    },
  )
  if (!response.ok) {
    const problem = await theProblemIn(response)
    const named = problem as { scenario?: unknown; changeNumber?: unknown; change?: unknown } | null
    throw new TheSimulationWasRefused(
      theReasonIn(problem, response.status, 'Could not turn this future into a plan'),
      typeof named?.scenario === 'string' ? named.scenario : null,
      typeof named?.changeNumber === 'number' ? named.changeNumber : null,
      typeof named?.change === 'string' ? named.change : null,
    )
  }
  return response.json()
}

/* ------------------------------------------------------- challenges and achievements
 *
 * Longer things than a week: named challenges a customer takes on, the rungs they climb in them,
 * and the badges those rungs mint. Every figure below is the backend's and nothing here works one
 * out — the reading, what the next rung still asks for, whether a season is open and whether the
 * customer is in it are all derived there on every read, against the application's own clock.
 */

/** The three rungs of every challenge, in the order they are climbed. */
export type Rung = 'BRONZE' | 'SILVER' | 'GOLD'

/**
 * The question a challenge asks. The backend's own enum name, sent as it is, because what each one
 * means is a rule and a rule is code.
 *
 * <p>Written out as a union rather than left as a `string` for the reason {@link NotificationReason}
 * is: a page has to say something different about each of them — what the reading is counted in,
 * and what a withdrawal does to it — and the compiler is the only thing that will notice a sixth
 * kind arriving before a customer does.
 */
export type ChallengeKind =
  | 'NEW_SAVINGS'
  | 'SECURED_WEEKS'
  | 'BALANCE_REACHED'
  | 'BALANCE_HELD'
  | 'GOALS_COMPLETED'

/**
 * What became of a customer's go at a challenge. Four states and no fifth, and the two that end it
 * unfinished are deliberately different words: `ABANDONED` is the customer leaving, `EXPIRED` is
 * the season closing underneath them, and a screen that drew them the same way would be blaming
 * somebody for a deadline.
 */
export type EnrolmentState = 'ACTIVE' | 'COMPLETED' | 'ABANDONED' | 'EXPIRED'

/**
 * One rung of a challenge: what it asks for, what it pays, and when this customer won it.
 *
 * <p>`threshold` is counted in whatever the challenge's kind measures — euros for the two that ask
 * about money, weeks, days or goals for the other three — which is why it is a plain number and why
 * nothing may print it without reading the kind first.
 *
 * <p>`wonAt` is null for a rung that is not lit, and that covers two cases: one nobody has reached,
 * and one won in an earlier round of a repeatable challenge. The card is about the enrolment the
 * customer is in now, which measures from its own mark and asks for the whole rung again; the old
 * badge is still in the trophy case, where nothing is ever taken away.
 */
export type ChallengeRung = {
  rung: Rung
  threshold: number
  points: number
  /** The moment it was won on this enrolment, as an instant, and null while it is not won. */
  wonAt: string | null
}

/**
 * The season a challenge belongs to: which campaign, the window it runs in, and whether that window
 * is open now.
 *
 * <p>**`open` is the backend's answer and is never worked out here.** Whether to-day is inside a
 * window depends on the zone the application counts its days in and on both ends being inclusive,
 * and it is the same answer the API refuses an enrolment with — so a page that decided for itself
 * would eventually draw a button the backend rejects.
 *
 * <p>`opensOn` and `closesOn` are plain `YYYY-MM-DD` days rather than moments, for the reason a
 * points deadline is: a season is announced on a poster, and an instant would put the bank's
 * campaign a day out for anybody east of here.
 */
export type Season = {
  code: string
  title: string
  opensOn: string
  closesOn: string
  open: boolean
}

/**
 * One challenge as it stands for one customer: what it is, what it asks of them, and where they
 * stand in it.
 *
 * <p>**Null means something different in each place.** No `state` is a challenge they have never
 * joined, and there is nothing about them to say. A `state` with no `reading` is an enrolment that
 * is over — nothing here is stored, so there was never a figure to freeze at the moment they left.
 * No `nextRung` on a live enrolment is gold already cleared. No `season` is an evergreen challenge,
 * which belongs to no campaign and is always open — not a season with a very long window, and a
 * countdown drawn over one would be inventing an urgency the bank never claimed.
 *
 * <p>`enrolled` is not the same question as `state !== null`: it is whether the enrolment is live,
 * so a card can perfectly well read `enrolled: false, state: 'ABANDONED'`.
 *
 * <p>The `words` are the backend's, so a page draws a challenge it has never heard of.
 */
export type Challenge = {
  code: string
  title: string
  words: string
  kind: ChallengeKind
  /** Whether it can be taken on again once finished. The two that measure a balance cannot. */
  repeatable: boolean
  rungs: ChallengeRung[]
  /** Whether they are in it now, which is the one thing the enrol-or-leave control needs. */
  enrolled: boolean
  state: EnrolmentState | null
  /** The high-water mark the enrolment measures from, on the kinds that measure from one. */
  measuringFrom: number | null
  /** Where they stand now, and null on an enrolment that is over or one never taken on. */
  reading: number | null
  nextRung: Rung | null
  /** What that next rung still asks for, in the kind's own units, and null when there is none. */
  stillNeeded: number | null
  season: Season | null
}

/** An enrolment as the API answers with it, which is what taking one on and leaving one return. */
export type Enrolment = {
  id: number
  challenge: string
  state: EnrolmentState
  measuringFrom: number
  enrolledAt: string
  endedAt: string | null
}

/**
 * One thing in the trophy case: which challenge, which rung of it, when it was won, the reading
 * that won it and the points it paid.
 *
 * <p>The reading and the points are the figures recorded at the moment it was won rather than
 * anything worked out now, which is what lets an old badge explain itself: what the saving stood at
 * on the day, whatever the challenge asks for since.
 */
export type Achievement = {
  id: number
  /** The code of the challenge it was won in, which outlives the bank offering it. */
  challenge: string
  title: string
  rung: Rung
  awardedAt: string
  reading: number
  points: number
}

/** One season in the listing, with the challenges the bank has put in it. */
export type Campaign = {
  code: string
  title: string
  opensOn: string
  closesOn: string
  open: boolean
  challenges: { code: string; title: string }[]
}

/**
 * Every challenge open to this customer with their standing in each — the whole tab in one read,
 * because a page that fetched a catalogue and then a standing per card would make a request per row
 * to draw one screen.
 *
 * <p>The backend judges before it answers, so a rung cleared by a deposit made a moment ago is
 * already lit here rather than at 3:30 to-morrow morning.
 */
export async function fetchChallenges(
  customerId: number,
  signal?: AbortSignal,
): Promise<Challenge[]> {
  const response = await fetch(`/api/customers/${customerId}/challenges`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load your challenges'))
  }
  return response.json()
}

/**
 * Takes this customer on to a challenge, and answers with the enrolment including the mark it will
 * measure from.
 *
 * <p>No body: everything the request says is in its path. Whether they may — a season not open, a
 * challenge they are already in, a one-off they have already finished — is the backend's decision,
 * and its refusal comes back as the sentence it wrote for the customer.
 */
export async function enrolInChallenge(customerId: number, code: string): Promise<Enrolment> {
  const response = await fetch(`/api/customers/${customerId}/challenges/${code}/enrolments`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not take this challenge on'))
  }
  return response.json()
}

/**
 * Leaves a challenge, and answers with the enrolment as it now stands.
 *
 * <p>Ended rather than removed, and it comes back rather than an empty 204 precisely so the page
 * can show that it is still there and over. Everything already won stays won.
 */
export async function leaveChallenge(customerId: number, code: string): Promise<Enrolment> {
  const response = await fetch(`/api/customers/${customerId}/challenges/${code}/enrolments`, {
    method: 'DELETE',
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not leave this challenge'))
  }
  return response.json()
}

/** The trophy case: everything this customer has ever won, newest first. */
export async function fetchAchievements(
  customerId: number,
  signal?: AbortSignal,
): Promise<Achievement[]> {
  const response = await fetch(`/api/customers/${customerId}/achievements`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load what you have won'))
  }
  return response.json()
}

/**
 * The seasons the bank is running, their windows and the challenges in each — every one of them,
 * finished included, because a campaign that is over is how somebody tells "I missed it" from
 * "there has never been one".
 *
 * <p>It belongs to no customer: what is running and until when is the same answer for everybody, in
 * exactly the way the rewards catalogue is.
 */
export async function fetchCampaigns(signal?: AbortSignal): Promise<Campaign[]> {
  const response = await fetch('/api/campaigns', { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the seasons'))
  }
  return response.json()
}

/**
 * Where an offer is in its life. The three the backend stores and the only three there are:
 * whether it is open, sold out or over somebody's limit is worked out when it is read and is
 * never a state anybody sets.
 */
export type OfferState = 'DRAFT' | 'PUBLISHED' | 'WITHDRAWN'

/**
 * One offer as whoever runs the catalogue reads it: everything a customer is shown, plus the state
 * it is in and the letters its vouchers are stamped with.
 *
 * <p>Deliberately not the same type as {@link Reward}. That one is the catalogue a customer sees
 * and it must keep saying exactly what it has always said; this is the back office, and the two
 * being one type is how the prefix and the state end up on a card somebody is being sold.
 */
export type AdministeredOffer = {
  code: string
  title: string
  description: string | null
  costInPoints: number
  voucherPrefix: string
  state: OfferState
  /** The first day it can be claimed, or null for an offer that has always been open. */
  opensOn: string | null
  /** The last day it can be claimed, inclusive, or null for one that never closes. */
  closesOn: string | null
  /** How long a voucher from it is good for, or null for one that never runs out. */
  voucherValidForDays: number | null
  /**
   * The run of consecutive weeks it asks for, or null for an offer that asks for none.
   *
   * <p>The three below are the whole of who an offer is for: a short closed list of plain
   * thresholds, all of which must be met, and not one of them an expression anybody writes. That
   * is the backend's decision and its reason is worth repeating here, because this page is the
   * place it pays off — a rule that can be drawn as three boxes is a rule an administrator can
   * read back, and a rule nobody can read back is a rule nobody can debug.
   */
  minimumStreakWeeks: number | null
  /** The achievement badge it asks for, by its code, or null for an offer that asks for none. */
  requiresBadge: string | null
  /** The lifetime of points earned it asks for, or null for an offer that asks for none. */
  minimumLifetimePointsEarned: number | null
  /** How many one customer may ever have, or null for an offer with no lifetime cap. */
  maxPerCustomer: number | null
  /** How many one customer may have in a week, or null for an offer with no weekly cap. */
  maxPerCustomerPerWeek: number | null
  /**
   * How many of it exist in total, or null for an offer that never runs out.
   *
   * <p>The total somebody set, not how many are left. What is left is that number less the claims
   * already made and it is worked out by the backend on every read — it belongs on the customer's
   * card, where the person asking is the person it is about, and not on a back office somebody
   * leaves open all afternoon watching one moment's answer go stale.
   */
  stock: number | null
  /**
   * The promotion, as three parts that are always all set or all null: what it costs while the
   * sale is on, the first day of the sale and the last. What somebody chose, not a verdict about
   * today — whether the sale is running is an answer with a clock in it and belongs on the
   * customer's read, because this is a page somebody leaves open all afternoon.
   */
  discountedCostInPoints: number | null
  discountOpensOn: string | null
  discountClosesOn: string | null
  /**
   * What a bundle hands over, and an empty list for everything that is not one — which is every
   * offer this application ships.
   *
   * <p>Read back rather than editable, because a bundle's contents are fixed when it is
   * composed: the vouchers already issued for it promise exactly these things, and a till reads
   * them off the catalogue when somebody hands one over. What the back office is for here is
   * checking that what went in is what was meant to go in.
   */
  contents: BundleMember[]
}

/**
 * An offer as somebody writes one. Everything created is a draft, so there is no state to send.
 *
 * <p>The two days go up as the text in the box, because that is what the contract carries: whether
 * "31/12/2026" is a date is the backend's answer and it comes back as a sentence about the date.
 * An empty box is an offer with no such day, which is the ordinary case.
 *
 * <p>`voucherValidForDays` is left out for vouchers that never run out, which is what every offer
 * this application ships does. It is left out rather than sent as nought: nought days would be a
 * voucher dead before it was printed and the backend refuses it in a sentence, so an empty box on
 * the form is an absence rather than a zero.
 */
export type ANewOffer = {
  code: string
  title: string
  description: string
  costInPoints: number
  voucherPrefix: string
  opensOn?: string
  closesOn?: string
  voucherValidForDays?: number
  /**
   * Who the offer is for. Each is left out for an offer that asks no such thing, which is what
   * every reward this application ships says about itself, and left out rather than sent as
   * nought for the reason the shelf life is: a streak requirement of nought weeks restricts
   * nobody while sitting on the form looking like a restriction, and the backend refuses it in a
   * sentence.
   */
  minimumStreakWeeks?: number
  requiresBadge?: string
  minimumLifetimePointsEarned?: number
  /**
   * How many one customer may ever have, and how many they may have in a week. Both left out for
   * an offer anybody may have as often as they like, which is what every reward this application
   * ships is. Left out rather than sent as nought, for the shelf life's reason: nought is not
   * "no cap", it is an offer nobody may ever claim, and the backend refuses it in a sentence.
   */
  maxPerCustomer?: number
  maxPerCustomerPerWeek?: number
  /**
   * How many of it there are. Left out for an offer that never runs out, which is what every
   * offer this application ships is. Left out rather than sent as nought: nought is a real and
   * different answer meaning there are none of it at the moment, and the offer reads as sold out
   * from the minute it is published.
   */
  stock?: number
  /**
   * A promotion, which is a price and two days or nothing at all. The backend refuses half of one
   * in a sentence naming the missing half, so all three are left out together for the ordinary
   * case — an offer at one price, which is every offer this application ships.
   */
  discountedCostInPoints?: number
  discountOpensOn?: string
  discountClosesOn?: string
  /**
   * What goes into it, which is what makes it a bundle at all. Left out for an ordinary offer,
   * which is every reward this application ships.
   *
   * <p>There is no field beside this saying "this is a bundle": an offer with members is one and
   * an offer without them is not, which is the backend's reading and the only one that cannot
   * be self-contradictory. Two or more of them, each an offer that already exists, each with how
   * many of it go in — and every one of those rules comes back as a sentence rather than being
   * checked here.
   */
  members?: AMemberGoingIn[]
}

/**
 * A change to an offer that already exists. Every field is optional and whatever is left out is
 * left alone, which is the backend's reading and is what makes fixing one word one field.
 *
 * <p>There is no code here, and that is this page choosing not to be able to send one. An offer's
 * code is fixed the moment it exists because the vouchers already issued name it; the backend
 * refuses a change that carries a different one, and a form with a field for it would be inviting
 * somebody to find that out.
 */
export type AChangeToAnOffer = {
  title?: string
  description?: string
  costInPoints?: number
  voucherPrefix?: string
  /**
   * A day, or `''` to say there is no longer one — which is what the box sends when somebody
   * empties it. Left out means leave the day exactly as it was, like every other field here; the
   * three readings are the backend's and are the ones a goal's deadline already has.
   */
  opensOn?: string
  closesOn?: string
  /**
   * A new shelf life in days. Left out leaves it exactly as it was; there is no way here to take
   * one back off, which is the backend's reading and is written down on its own record.
   */
  voucherValidForDays?: number
  /**
   * A new threshold. Left out leaves the rule exactly as it was; there is no way here to take
   * one back off, which is the backend's reading and is argued out on its own record.
   */
  minimumStreakWeeks?: number
  requiresBadge?: string
  minimumLifetimePointsEarned?: number
  /**
   * New caps on how often one customer may have it. Left out leaves each exactly as it was, and
   * there is no way here to take one back off either — the same reading and the same wart the
   * shelf life above carries, argued out on the backend's own record.
   */
  maxPerCustomer?: number
  maxPerCustomerPerWeek?: number
  /**
   * A restock, as the new total rather than as a difference: "there are forty of these now"
   * rather than "add twelve", because the second depends on what the box said when the page was
   * loaded. Raising it puts a sold-out offer back on customers' screens on their very next read.
   * The backend refuses a figure below what has already gone out, quoting how many that is.
   */
  stock?: number
  /**
   * The promotion's price, read like every other field: left out leaves it alone.
   *
   * <p>There is no separate way to take it away, and none is needed. A promotion is one fact in
   * three parts, so emptying both of its days calls the sale off and the price comes with them —
   * the backend's reading, and the one that matches what somebody emptying both boxes meant. A
   * change that empties both days *and* names a price is refused, because it starts a sale and
   * ends one in the same breath.
   */
  discountedCostInPoints?: number
  /**
   * A day, or `''` to say the sale no longer runs then — the same three readings the window's
   * days have, and the same ones a goal's deadline has.
   */
  discountOpensOn?: string
  discountClosesOn?: string
}

/**
 * Every offer in the catalogue, in every state.
 *
 * <p><strong>Nothing about this is authenticated.</strong> There is no token to send and nothing
 * to send it with: the backend does not check that whoever is asking runs the scheme, because
 * there is nothing in this application to check it against. This is an administration screen and
 * not authorisation, exactly as signing in is a sign-in screen and not authentication.
 */
export async function fetchEveryOffer(signal?: AbortSignal): Promise<AdministeredOffer[]> {
  const response = await fetch('/api/admin/rewards', { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the catalogue'))
  }
  return response.json()
}

/**
 * Writes a new offer. It arrives as a draft, invisible to customers, and stays one until somebody
 * publishes it.
 *
 * <p>Every field goes up as it was typed. Whether a title is a title, what the least an offer may
 * cost is and whether a code is free are the backend's rules, and each comes back as a sentence
 * this page shows unchanged — including the one naming the offer already using the code.
 */
export async function writeAnOffer(offer: ANewOffer): Promise<AdministeredOffer> {
  const response = await fetch('/api/admin/rewards', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(offer),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The offer was not written'))
  }
  return response.json()
}

/** Changes whatever this names about an offer, including one that is already on sale. */
export async function changeAnOffer(
  code: string,
  change: AChangeToAnOffer,
): Promise<AdministeredOffer> {
  const response = await fetch(`/api/admin/rewards/${encodeURIComponent(code)}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(change),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The offer was not changed'))
  }
  return response.json()
}

/** Puts an offer on sale, which is the moment a customer can first see it. */
export async function publishAnOffer(code: string): Promise<AdministeredOffer> {
  const response = await fetch(`/api/admin/rewards/${encodeURIComponent(code)}/publish`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The offer was not published'))
  }
  return response.json()
}

/**
 * One place in a queue, as whoever runs the scheme reads it.
 *
 * <p>A name beside the identifier because that is the whole point of the list: "customer 7 is
 * first" tells an administrator deciding what to restock nothing they can act on. It is nullable
 * for the row whose customer is no longer on file, which the backend answers honestly rather than
 * dropping — a queue that quietly got shorter would be a queue nobody could reconcile.
 */
export type APlaceInAQueue = {
  position: number
  customerId: number
  customerName: string | null
  joinedAt: string
}

/**
 * Who is waiting for an offer, oldest first.
 *
 * <p>Asked for a row at a time rather than loaded with the catalogue, because a queue is empty
 * for almost every offer and a list of nine offers would be nine requests answering `[]` on every
 * visit to the screen. It is also the one figure on this screen that moves without anybody here
 * doing anything: a sweep at five in the morning empties it, so it is read when somebody asks and
 * never cached behind them.
 *
 * <p>Unauthenticated, like everything else addressed at `/api/admin`, and for the reason those
 * say: there is nothing in this application to check a member of staff against.
 */
export async function fetchTheWaitingList(
  code: string,
  signal?: AbortSignal,
): Promise<APlaceInAQueue[]> {
  const response = await fetch(`/api/admin/rewards/${encodeURIComponent(code)}/waiting-list`, {
    signal,
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the waiting list'))
  }
  return response.json()
}

/**
 * Takes an offer out of the catalogue for good. Nothing is deleted: the vouchers already issued
 * for it go on saying exactly what they said, which is the whole reason withdrawing exists.
 */
export async function withdrawAnOffer(code: string): Promise<AdministeredOffer> {
  const response = await fetch(`/api/admin/rewards/${encodeURIComponent(code)}/withdraw`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The offer was not withdrawn'))
  }
  return response.json()
}

/**
 * Why a customer cannot claim an offer they can see, as the backend names it.
 *
 * <p>A word rather than something this page works out. Whether a season has opened depends on the
 * zone the application counts its days in and on both ends of the window being inclusive, and a
 * page that decided for itself would eventually draw a claim button the backend refuses. Only
 * these two exist today; the slices that add stock, limits and eligibility add their own, and a
 * page that already reads the sentence beside the word keeps telling the truth when they do.
 *
 * <p>`NOT_FOR_YOU` is the third, and it is one word for three rules — a run of weeks, a badge
 * and a lifetime of points earned. Which of the three stopped them is in the sentence beside it
 * and deliberately not in the word: a value per threshold would have this page switching over a
 * vocabulary that grows every time the scheme learns to ask for something new, in order to draw
 * the same greyed card three ways. What the page has to know is that the card is locked because
 * of something the customer *is* rather than because of a date, and that is one fact.
 */
export type WhyAnOfferIsLocked =
  | 'NOT_OPEN_YET'
  | 'CLOSED'
  | 'NOT_FOR_YOU'
  | 'YOU_HAVE_HAD_YOUR_LIMIT'
  | 'NOTHING_LEFT'

/**
 * One catalogue entry as it stands for one customer right now: what it is, what it costs, whether
 * they can claim it this minute, and the single reason if they cannot.
 *
 * <p>Deliberately not the same type as {@link Reward}. That one is the catalogue with nobody in
 * it — the same entries for everybody, at an address whose shape must not move — and this is the
 * read the rewards page actually makes, whose answer changes overnight.
 *
 * <p>`claimable` is the backend's verdict and not `lockedBecause === null` worked out here, for
 * the reason `VoucherAtTheCounter.good` is a boolean beside its own state: the day a lock arrives
 * that this page has never heard of, a page reading the boolean greys the card and a page
 * inferring it draws the card as claimable.
 *
 * <p>`whyItIsLocked` is the backend's sentence, shown unchanged, and it is word for word the
 * sentence a claim would be refused with — so the card and the refusal cannot tell somebody two
 * different stories about one rule.
 *
 * <p>The window is on every entry, locked or not, because an offer somebody can claim today and
 * which closes on the thirtieth is exactly the one they need the thirtieth for.
 *
 * <p>So are the limits, and for the same reason turned around: "one left" is what makes somebody
 * claim this week rather than next, and a card that only mentioned a cap once it had stopped them
 * would be explaining a rule at the one moment they can no longer act on it. The two caps are
 * what somebody set, null for a cap that does not exist; `howManyYouHaveHad` is this customer's
 * own count, and `howManyYouMayStillHave` is the backend's subtraction — the tighter of the two
 * remainders when both caps run, and null when nothing caps them at all. A page that worked it
 * out here would have to know which week this application counts in and where its boundaries
 * are, which is exactly the knowledge the sentence beside the lock exists to save it.
 */
export type RewardForACustomer = {
  code: string
  title: string
  description: string
  costInPoints: number
  claimable: boolean
  lockedBecause: WhyAnOfferIsLocked | null
  whyItIsLocked: string | null
  opensOn: string | null
  closesOn: string | null
  maxPerCustomer: number | null
  maxPerCustomerPerWeek: number | null
  howManyYouHaveHad: number
  howManyYouMayStillHave: number | null
  /**
   * How many are left, or null for an offer that never runs out — which is what all four of the
   * rewards this application has always had say.
   *
   * <p>How many are *left*, not how many exist: the total is the administrator's figure and a
   * customer has nothing to do with it, while "three left" is the whole of what makes somebody
   * hurry. Null rather than nought for an unlimited offer, because those are opposite facts and a
   * page reading a nought as "none left" would grey the entire catalogue.
   */
  whatIsLeft: number | null
  /**
   * What it usually costs, to be struck through beside the price above — and null whenever there
   * is nothing to strike through.
   *
   * <p>`costInPoints` is always the figure this customer would actually be charged today, which
   * is the discounted one while a sale is on. This is the other one, and it arrives already
   * decided rather than worked out here: a card that compared two numbers, or subtracted one from
   * the other, would be a second copy of the pricing rule living at this end of the wire, and the
   * second copy is always the one that goes out of date. Null means draw one figure.
   */
  ordinaryCostInPoints: number | null
  /**
   * The last day of the sale, null unless one is running. A different promise from `closesOn`:
   * one is the day the offer goes away and this is the day the price goes back up.
   */
  discountClosesOn: string | null
  /**
   * The moment this customer's own hold on this offer runs out, and null when they hold none —
   * which is nearly everybody and nearly every card.
   *
   * <p>When it is set, three of the fields above read differently and this is how the page
   * knows: `claimable` is true because they may claim the one being kept for them,
   * `whatIsLeft` is very likely nought because their own hold is one of the ones taken off it,
   * and the card draws two buttons rather than one.
   *
   * <p>A moment and not a day, unlike every other deadline this API sends. A hold is
   * seventy-two hours from the moment it was taken, so a date would round the promise rather
   * than be it, and the page counts down to it.
   */
  yourHoldLapsesAt: string | null
  /**
   * Where this customer stands in the queue for this offer, counted from one, and null for
   * everybody who is not in it — which is nearly everybody and nearly every card.
   *
   * <p>It never arrives beside `yourHoldLapsesAt`. The two are the two ends of one pipeline:
   * somebody waits for a thing that has run out, their turn comes, and what the nightly sweep
   * hands them is the hold. So the card reads the hold first, this second, and draws the
   * ordinary foot otherwise.
   *
   * <p>There is deliberately no field saying a queue *could* be joined. An offer worth queueing
   * for is one locked with `NOTHING_LEFT`, which is already on the card; whether this particular
   * customer may join it — the window, the rules, the caps — is answered when they press, in a
   * sentence, by the one place that owns those rules.
   */
  yourPlaceInTheQueue: number | null
  /**
   * What is in it, when it is a bundle, and an empty list when it is not — which is every offer
   * this application has always had.
   *
   * <p>One price against three things is exactly the card nobody can read without being told
   * the three things. There are no prices on the lines and there is no arithmetic to do here:
   * the bundle carries its own price, chosen by whoever composed it, and a card that added the
   * members up would be quoting a saving nobody decided.
   */
  contents: BundleMember[]
}

/**
 * The catalogue as it stands for this customer: every offer on sale, each one saying where they
 * stand with it.
 *
 * <p>A second read beside {@link fetchRewards} rather than a replacement for it. That one is the
 * catalogue with nobody in it and it stays exactly as it is, because it is the shape the whole
 * feature was built not to move; this is the one the rewards page draws from, because a card
 * cannot say "opens on the third" without asking on behalf of somebody on a particular day.
 *
 * <p>An offer nobody can claim yet comes back locked rather than missing. Hiding it would take
 * away the only thing on the screen that tells a customer what the scheme wants from them.
 */
export async function fetchRewardsFor(
  customerId: number,
  signal?: AbortSignal,
): Promise<RewardForACustomer[]> {
  const response = await fetch(`/api/customers/${customerId}/rewards`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the rewards catalogue'))
  }
  return response.json()
}

/**
 * Revokes a voucher and says why, which gives the customer their points back and puts the thing
 * back in the window.
 *
 * <p><strong>The reason is required and the backend says so if it is missing</strong>, which is
 * why nothing here checks it first: a rule enforced in two places is a rule that will one day be
 * enforced differently in each, and the sentence that comes back is shown unchanged. What is
 * worth saying here is why the field exists at all — "a mistake can be undone" is only half of
 * what somebody running the scheme is promised, and the other half is "and explained". The words
 * typed into that box are what the customer reads on their own page and what a counter reads out
 * loud to them.
 *
 * <p>Nothing about the refund comes back on this call. What returns is the voucher as it now
 * reads, which is exactly what a till would see if the customer turned up with it — the surest
 * way for the two screens to agree — and how many points somebody has is a different read
 * belonging to a different page.
 *
 * <p>The code goes into the path as typed, like the counter's two calls, because what to forgive
 * about a keyboard is the backend's decision and a page that tidied a code up first could tidy a
 * real one into a different one.
 */
export async function cancelVoucher(
  voucherCode: string,
  reason: string,
): Promise<VoucherAtTheCounter> {
  const response = await fetch(`/api/admin/vouchers/${encodeURIComponent(voucherCode)}/cancel`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The voucher was not cancelled'))
  }
  return response.json()
}

/**
 * A hold as the backend reports it: which offer is being kept for whom, until when, and what
 * became of it.
 *
 * <p>Its own type rather than fields on {@link RewardForACustomer}, because it is the answer to
 * taking one and to giving one up — two requests about a hold — while that one is a card in a
 * catalogue that happens to mention one.
 *
 * <p>`state` is the backend's own word. A page that inferred it from `endedAt` being present
 * would have to decide for itself whether a hold that ended was converted, given up or left too
 * long, which are three quite different things to say to somebody.
 */
export type AHold = {
  id: number
  customerId: number
  offerCode: string
  title: string
  costInPoints: number
  takenAt: string
  lapsesAt: string
  state: 'HELD' | 'CONVERTED' | 'GIVEN_UP' | 'LAPSED'
  endedAt: string | null
}

/**
 * Puts the last of something aside for this customer for seventy-two hours.
 *
 * <p>It costs nothing. No points move and no batch in the ledger is touched — the whole of what
 * a hold takes is the stock — which is why this page can offer it beside a price the customer
 * cannot yet afford.
 *
 * <p>How long it lasts is not sent, for the reason a claim does not send a price: the
 * seventy-two hours are the scheme's rule, and a page that named a duration could name the
 * wrong one.
 */
export async function takeAHold(customerId: number, reward: string): Promise<AHold> {
  const response = await fetch(`/api/customers/${customerId}/holds`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reward }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The hold was not taken'))
  }
  return response.json()
}

/**
 * Turns a hold into the claim it was being kept for: the points go now, at the price in force
 * now, and the voucher exists the moment this succeeds.
 *
 * <p>Its own call rather than {@link claimReward} on the same code, because the two are
 * different requests with different refusals: claiming something you used to hold is a perfectly
 * good request that succeeds if there is stock, and converting a hold that ran out has to be
 * told so in those words rather than quietly served.
 */
export async function convertAHold(customerId: number, reward: string): Promise<ClaimedReward> {
  const response = await fetch(
    `/api/customers/${customerId}/holds/${encodeURIComponent(reward)}/claim`,
    { method: 'POST' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The hold was not claimed'))
  }
  return response.json()
}

/**
 * Gives a hold up, which puts the thing back in the window at once — no job, no night, no
 * waiting for anybody.
 *
 * <p>Nothing is refunded because nothing was taken. A DELETE, because what the customer is doing
 * is removing their claim on the last one; the row keeps its history at the far end, as every
 * one-way door in this application does.
 */
export async function giveUpAHold(customerId: number, reward: string): Promise<AHold> {
  const response = await fetch(
    `/api/customers/${customerId}/holds/${encodeURIComponent(reward)}`,
    { method: 'DELETE' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The hold was not given up'))
  }
  return response.json()
}

/**
 * One thing inside a bundle: its code, what it is called, and how many of it go in.
 *
 * <p>One type for all three surfaces that draw a bundle — the customer's card, the back office
 * and the till — because a line of a bundle says the same thing to all three, and the backend
 * sends one record to all three for the same reason. That is unlike the offer itself, which has
 * a type per reader on purpose.
 *
 * <p>No price on it, and no count of how many of the member are left. The bundle carries its own
 * price and its own "how many left", which already accounts for every member's stock; a page
 * that added the members' prices up, or worried about each member's shelf, would be doing
 * arithmetic the backend has already done and would be the copy that goes out of date.
 *
 * <p>The code is here because the reward icon map is the one place this application maps a
 * catalogue code to a picture, and it falls back to a generic gift for anything it has not heard
 * of — which is what makes a bundle's lines drawable without a frontend change.
 */
export type BundleMember = {
  code: string
  title: string
  quantity: number
}

/**
 * One line of a bundle as the form sends it: a code and how many of it go in.
 *
 * <p>Deliberately not {@link BundleMember}. What comes back carries the member's title, because
 * a card has to draw it; what goes up carries only what somebody typed, because the title is
 * the catalogue's and a form that sent one would be asking the backend to believe it. The same
 * reason an offer is written with a code and read back with everything.
 */
export type AMemberGoingIn = {
  code: string
  quantity: number
}

/**
 * A place in a queue as the backend reports it: which offer is being waited for, since when, and
 * how many people are in front.
 *
 * <p>Its own type rather than fields on {@link RewardForACustomer}, for the reason {@link AHold}
 * is its own: it is the answer to joining and to leaving — two requests about a place — while
 * that one is a card in a catalogue that happens to mention one.
 *
 * <p>`position` is the backend's count and not an index worked out of a list. This page never
 * sees the list, and must not: a queue is exactly the thing a customer may not be shown other
 * people's names in.
 */
export type APlaceInTheQueue = {
  id: number
  customerId: number
  offerCode: string
  title: string
  /** Counted from one, because it is read out loud: first in line is first, not nought. */
  position: number
  joinedAt: string
}

/**
 * Puts this customer in the queue for something that has run out.
 *
 * <p>It costs nothing and locks nothing in — not even a price. What a turn buys is a hold, and
 * converting a hold pays whatever is in force then, which is why this is worth offering to
 * somebody who cannot afford the thing today.
 *
 * <p>Only for an offer that has genuinely run out. Joining one that has not is refused in a
 * sentence telling the customer to claim it instead, which is why this page only draws the
 * button on a card the backend has locked as sold out.
 */
export async function joinTheQueue(
  customerId: number,
  reward: string,
): Promise<APlaceInTheQueue> {
  const response = await fetch(`/api/customers/${customerId}/waiting-lists`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reward }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The queue was not joined'))
  }
  return response.json()
}

/**
 * Takes this customer out of a queue, which closes the gap behind them at once — no job, no
 * night, no waiting for anybody.
 *
 * <p>Nothing is refunded because nothing was taken, and no stock moves either: a place in a
 * queue holds nothing. A DELETE, because what the customer is doing is withdrawing their claim
 * on a turn; the row keeps its history at the far end, as every one-way door here does.
 */
export async function leaveTheQueue(
  customerId: number,
  reward: string,
): Promise<APlaceInTheQueue> {
  const response = await fetch(
    `/api/customers/${customerId}/waiting-lists/${encodeURIComponent(reward)}`,
    { method: 'DELETE' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The queue was not left'))
  }
  return response.json()
}

/**
 * One published version of a savings product's agreement: the day it took effect, every figure it
 * carries, what happens at the end of it, and the line saying what changed.
 *
 * <p>The same shape in both places the backend sends one — nested on a product as the terms it is
 * offering today, and listed on its own as the history it has published — because they are the same
 * thing at two moments, and a page that rendered a version differently depending on which list it
 * came out of would be two renderers to keep in agreement.
 *
 * <p><strong>Every rate is a percentage and the floor is a euro amount.</strong> The backend holds
 * rates as basis points and money as cents and hands out neither: `0.60` is 0.60% a year, `1.25` is
 * a quarter more points per euro, and `500` is five hundred euros. This page does no arithmetic on
 * any of them — it has never priced anything in this application and it does not start here.
 *
 * <p><strong>Zero is the absence of the rule, in six of these fields.</strong> No bonus to earn, no
 * notice to give, no term to serve, no floor to keep, no price for leaving, no interest at all. One
 * reading for six absences is the version a person can hold in their head, and it is why none of
 * them is nullable.
 *
 * <p>`whatChanged` is null on a first version, because nothing changed — that is what a first
 * version is — and the screen draws nothing for it.
 */
export type TermsVersion = {
  productCode: string
  version: number
  effectiveFrom: string
  annualRatePercent: number
  bonusRatePercent: number
  noticeDays: number
  termMonths: number
  minimumBalance: number
  earlyExitPenaltyDays: number
  pointsMultiplier: number
  anniversaryRatePercent: number
  maturityAction: string
  whatChanged: string | null
  /**
   * What this version moved about the version before it, one sentence per figure, worded by the
   * backend.
   *
   * <p>The backend has exactly one function that puts a difference between two agreements into
   * words, and both the history and an account's own comparison render from it. A page that built
   * "The rate goes from 0.60% a year to 0.50% a year." out of two numbers would be the second place
   * that wording lived, and the second place is always the one that forgets the field somebody
   * added last week.
   *
   * <p>Empty on a first version, because nothing changed, and empty again on the copy nested on a
   * product's card, which is an offer rather than a comparison.
   */
  whatIsDifferent: string[]
}

/**
 * One savings product on the shelf, with the terms it is offering today.
 *
 * <p>`currentTerms` is what the product pays now and emphatically not what any account on it pays:
 * free savings has published a second version at a lower rate, and every account opened before it
 * carries on under the first. Naming the field for the offer rather than for the agreement is the
 * one piece of vocabulary that stops a page from quietly promising somebody the wrong rate.
 *
 * <p>`openToNewAccounts` false is a fact about whether a button is drawn, not about whether the
 * card is. A closed product is still sent, because customers are still on it and leaving it out
 * would hide a product somebody is holding.
 */
export type SavingsProduct = {
  code: string
  name: string
  kind: string
  description: string
  sortOrder: number
  openToNewAccounts: boolean
  currentTerms: TermsVersion
}

/**
 * What the bank sells, in the order somebody chose, each with the terms it is offering today.
 *
 * <p>The customer's own read rather than an administration one, and that is deliberate on the
 * backend's side: nothing about a savings product is hidden from a customer — closed products are
 * in the catalogue marked closed, and the history includes a version dated for next month — so a
 * second administration read would have been a second shape to keep in agreement for no gain.
 */
export async function fetchSavingsProducts(signal?: AbortSignal): Promise<SavingsProduct[]> {
  const response = await fetch('/api/savings-products', { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the savings products'))
  }
  return response.json()
}

/** Every version that product has published, oldest first, because the history reads as a story. */
export async function fetchTheVersionsOf(
  code: string,
  signal?: AbortSignal,
): Promise<TermsVersion[]> {
  const response = await fetch(`/api/savings-products/${encodeURIComponent(code)}/versions`, {
    signal,
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the versions'))
  }
  return response.json()
}

/**
 * What goes up to publish the next version of a product's terms.
 *
 * <p><strong>Every field is text, and every field is sent.</strong> Text, because some of what the
 * backend has to say about a rate is about the characters — "0,50" deserves a sentence about the
 * rate rather than a request that could not be read — and an empty box has to stay an empty box
 * rather than becoming a nought on the way. Sent, all of them, because a version carries nothing
 * over from the version before it: it is the complete list of numbers, published outright, and the
 * backend refuses an absent figure by name.
 *
 * <p>There is no version number and no code. The product is in the path and the number is the
 * catalogue's answer — one higher than the last it published — which is what stops two people
 * publishing the same version at once.
 */
export type ANewVersionOfTerms = {
  effectiveFrom: string
  annualRatePercent: string
  bonusRatePercent: string
  noticeDays: string
  termMonths: string
  minimumBalance: string
  earlyExitPenaltyDays: string
  pointsMultiplier: string
  anniversaryRatePercent: string
  maturityAction: string
  whatChanged: string
}

/**
 * Publishes the next version of a product's terms and answers with it.
 *
 * <p><strong>Nothing about this is authenticated.</strong> There is no token to send and nothing to
 * send it with: the backend does not check that whoever is asking runs the bank, because there is
 * nothing in this application to check it against. This is an administration screen and not
 * authorisation, exactly as signing in is a sign-in screen and not authentication.
 *
 * <p>Every field goes up as it was typed and nothing is checked here first. Whether a rate may be
 * quoted to three places, whether nought is a thing the points multiplier may be and whether a
 * version may take effect on the day named are the backend's rules, and each comes back as a
 * sentence this page shows unchanged. A page that checked first would be a second copy of the
 * rules, and the second copy is always the one that is out of date.
 */
export async function publishANewVersion(
  code: string,
  version: ANewVersionOfTerms,
): Promise<TermsVersion> {
  const response = await fetch(
    `/api/admin/savings-products/${encodeURIComponent(code)}/versions`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(version),
    },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The version was not published'))
  }
  return response.json()
}

/**
 * Stops anybody opening a new account on a product, and disturbs nothing already on it: the
 * accounts carry on, every published version reads exactly as it did, and the card stays in the
 * catalogue marked closed.
 *
 * <p>Nothing is deleted, here or anywhere in this module. An account will point at a product and
 * every interest posting will point at one through the version it was paid under, so a product that
 * went away would orphan all of them. Retiring one is this press.
 */
export async function closeAProductToNewAccounts(code: string): Promise<SavingsProduct> {
  return theDoorToNewAccounts(code, 'close', 'The product was not closed')
}

/** Puts it back on sale, and publishes nothing: it comes back offering whatever version is effective that day. */
export async function reopenAProductToNewAccounts(code: string): Promise<SavingsProduct> {
  return theDoorToNewAccounts(code, 'reopen', 'The product was not reopened')
}

/** The one request underneath both, because they are one flag read two ways. */
async function theDoorToNewAccounts(
  code: string,
  door: 'close' | 'reopen',
  whenNoneGiven: string,
): Promise<SavingsProduct> {
  const response = await fetch(
    `/api/admin/savings-products/${encodeURIComponent(code)}/${door}`,
    { method: 'POST' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, whenNoneGiven))
  }
  return response.json()
}

/**
 * One notice a customer has given on money in a savings account.
 *
 * <p>`ready` and `daysLeft` come down worked out and are stored nowhere on the backend either: both
 * are the day the notice was given plus the days the agreement asks for, read against the
 * application's own clock at the moment this was fetched. A page that derived them from `readyOn`
 * would be doing calendar arithmetic in the browser's zone, which is a different zone on a laptop
 * in the wrong country and a different answer on exactly the morning it matters.
 *
 * <p>`amount` is what the customer gave notice on and never changes; `stillStanding` is what it is
 * good for now, after a withdrawal has spent part of it. Both are here because they are two
 * different facts, and a card that showed only one would either lose the act or lose the
 * arithmetic.
 */
export type NoticeGiven = {
  id: number
  savingsAccountId: number
  amount: number
  stillStanding: number
  givenOn: string
  readyOn: string
  ready: boolean
  daysLeft: number
}

/**
 * What one savings account's notice says altogether: the days its agreement asks for, what ready
 * notice covers today, what is still running, and the notices behind both figures.
 *
 * <p>`noticeDays` of `0` is an account with nothing to give notice of — free savings and the core
 * saver — and it is what tells this page to draw no panel at all. The endpoint answers for every
 * savings account rather than refusing the ones with no notice period, so no screen has to work out
 * what kind of account it is holding before it dares ask.
 *
 * <p>`readyToTakeToday` is a ceiling and not a promise. It is what ready notice covers, which is
 * one of the three things a withdrawal is weighed against — what the account holds and what its
 * goals have claimed are the other two — so this page says "ready to take" rather than "available",
 * and lets the withdrawal form's own refusal be the last word.
 */
export type TheNoticeOnAnAccount = {
  savingsAccountId: number
  noticeDays: number
  readyToTakeToday: number
  stillWaiting: number
  notices: NoticeGiven[]
}

/** What this account's notice says today, for any savings account, notice period or not. */
export async function fetchTheNoticeOn(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<TheNoticeOnAnAccount> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/notices`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load this account’s notice'))
  }
  return response.json()
}

/**
 * Starts the clock on an amount, and answers with the notice and the day it comes free.
 *
 * <p>The amount goes up exactly as it was typed and nothing is checked here first. Whether it is an
 * amount of money at all, and whether this account has anything to give notice of, are the
 * backend's rules and each comes back as a sentence this page shows unchanged. A page that checked
 * first would be a second copy of the rules, and the second copy is always the one that is out of
 * date.
 */
export async function giveNoticeOn(
  savingsAccountId: number,
  amount: string,
): Promise<NoticeGiven> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/notices`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The notice was not given'))
  }
  return response.json()
}

/**
 * Opens another savings account for this customer, on the savings product they chose, and answers
 * with the card the overview will draw for it.
 *
 * <p><strong>The product goes up as text and nothing is checked here first.</strong> Whether the
 * bank sells such a thing, and whether it is still opening accounts on it, are the backend's rules
 * and each comes back as a sentence this page shows unchanged. A page that checked first would be a
 * second copy of the catalogue, and the second copy is the one that is out of date the morning a
 * fifth product is added.
 *
 * <p>The three refusals are three different things to do next, and the backend says which is which
 * with a status: a product nobody sells is a 404, a product closed to new accounts is a 409, and an
 * empty choice is a 400. This function passes the sentence on and lets the screen show it beside
 * the button that was pressed.
 */
export async function openASavingsAccount(
  customerId: number,
  product: string,
): Promise<SavingsAccount> {
  const response = await fetch(`/api/customers/${customerId}/savings-accounts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ product }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The savings account was not opened'))
  }
  return response.json()
}

/**
 * Cancels a notice, leaving nothing standing behind it.
 *
 * <p>A press rather than a `DELETE`, because nothing is deleted: the backend keeps the row so that
 * a later question about why a withdrawal was refused can still name the notice that was cancelled
 * the day before. What comes back is the notice with nothing left standing on it.
 */
export async function cancelANotice(
  savingsAccountId: number,
  noticeId: number,
): Promise<NoticeGiven> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/notices/${noticeId}/cancel`,
    { method: 'POST' },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The notice was not cancelled'))
  }
  return response.json()
}

/**
 * Closes a savings account its holder has emptied, and answers with the agreement as it now reads —
 * including the day it ended on.
 *
 * <p><strong>A POST to a door rather than a DELETE, because nothing goes away.</strong> Every
 * deposit, every withdrawal, every goal and every saving rule on the account stays readable
 * afterwards, and so does the agreement: a closed account still names the product and the version
 * its history was decided under. A DELETE would promise the opposite of what happens.
 *
 * <p>An account with money still in it is refused, in a sentence that says how much — which is
 * exactly the figure the customer needs in order to do the thing that would let them close it.
 * Nothing is emptied on their behalf: where those euros land decides a week, a streak and a loyalty
 * clock, and all three are theirs to decide.
 */
export async function closeASavingsAccount(savingsAccountId: number): Promise<TheAgreement> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/close`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The savings account was not closed'))
  }
  return response.json()
}

/**
 * A month's interest arriving in a savings account, as a row of the same ledger as the deposits.
 *
 * <p><strong>Its own kind rather than a deposit with a flag on it, because it crosses no boundary.</strong>
 * Nothing left a current account to pay it — the bank added it — so `currentAccountId` is `null`,
 * which is the only null of its sort in this ledger. A row drawn as a transfer would have an arrow
 * pointing out of an account that was never touched.
 *
 * <p>`pointsEarned` is 0, and that is the rule rather than a gap: a euro the bank added has never
 * earned a point in this application and never will. `automatic` is `false` for a plainer reason
 * still — that word means a saving rule, and no rule made this.
 */
export type InterestMovement = Omit<LedgerEntry, 'currentAccountId'> & {
  direction: 'INTEREST_INTO_SAVINGS'
  savingsAccountId: number
  /** Nothing at all: interest has no second account, so there is none to name. */
  currentAccountId: null
  automatic: false
}

/**
 * One month of interest an account has been paid: which period it was, the days it covered, the
 * balance it was worked out on, the rate it was paid at, the version that rate came from, and what
 * it paid.
 *
 * <p><strong>Everything needed to check the arithmetic, which is the point of showing it at
 * all.</strong> A figure on its own is a number the customer has to take on trust; the period, the
 * average balance and the rate together are a sentence they can redo — divide the rate by twelve,
 * multiply, floor. In an application about how saving is rewarded, a reward nobody can check is the
 * one thing worth not shipping.
 *
 * <p>`averageDailyBalance` is the average across the period rather than the balance at either end
 * of it, which is why money paid in on the last day earns one day of interest and not a month's.
 * `lowestDailyBalance` comes out of the same walk and is what a minimum-balance product's floor is
 * judged on: a month whose lowest day stayed at or above the floor earned the bonus rate, and one
 * that dipped for a single day did not. `bonusEarned` says which, and `annualRatePercent` is the
 * rate that was **actually** paid — the headline rate and the bonus already added together on a
 * month that earned it — so the two cannot disagree. It is `false` on the three products with no
 * bonus to offer, because nothing was earned there either.
 *
 * <p>`until` is the day the period **ended** rather than its last day — the half-open reading the
 * backend uses everywhere — so a panel printing a range takes a day off it, and two consecutive
 * periods meet rather than overlap.
 *
 * <p>`annualRatePercent` is the rate **this account** is on, which is not always what its product
 * is selling today: an account opened before free savings was repriced goes on being paid at the
 * rate it was written with. `termsVersion` is the address of that agreement.
 */
export type InterestPaid = {
  periodOrdinal: number
  /** A plain `YYYY-MM-DD`, as the backend's own zone read it. */
  from: string
  /** A plain `YYYY-MM-DD`, and the day after the last day the period covers. */
  until: string
  averageDailyBalance: number
  lowestDailyBalance: number
  annualRatePercent: number
  bonusEarned: boolean
  termsVersion: number
  interest: number
  postedAt: string
}

/**
 * Every month of interest one savings account has been paid, oldest first.
 *
 * <p>Its own request rather than part of the account's overview, which is the backend's shape and
 * the right one: the overview is read on every visit to every screen, and this is wanted by one
 * panel on one of them.
 *
 * <p>The months that paid nothing are in the answer. A customer whose account was empty in March is
 * owed the row that says so — a list that quietly skipped it would leave them counting months to
 * work out which one was missing.
 */
export async function fetchInterestPaid(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<InterestPaid[]> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/interest`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the interest this account has been paid'))
  }
  return response.json()
}

/**
 * The price of breaking a fixed term early, as the money-movement ledger reports it.
 *
 * <p><strong>A fourth kind of movement rather than a withdrawal with a flag on it.</strong> What
 * `OUT_OF_SAVINGS` says about a movement — that the money went back to a current account — is
 * exactly what is not true of a charge, so a row drawn from that word would point an arrow at an
 * account that was never credited. `currentAccountId` is `null` on it for the same reason it is
 * null on a month's interest: there is no second account, and one invented to fill the field would
 * be a lie a page would print.
 *
 * <p>`pointsEarned` is 0 and `automatic` is `false`, and neither is a gap. Nothing has ever earned
 * a point for leaving a savings account, and the word `automatic` means a saving rule, which this
 * is not: a customer pressed a button that said what it would cost.
 */
export type AnEarlyExitChargeMovement = Omit<LedgerEntry, 'currentAccountId'> & {
  direction: 'AN_EARLY_EXIT_CHARGE'
  savingsAccountId: number
  /** Nothing at all: a charge has no second account, so there is none to name. */
  currentAccountId: null
  automatic: false
}

/**
 * What one savings account's fixed term says today: how long it was locked for, the day it matures,
 * how long is left, and what breaking it now would cost.
 *
 * <p><strong>`termMonths` is what tells a page whether to draw the panel at all.</strong> Nought is
 * free savings, the core saver and the notice account — no lock, no maturity date, nothing to
 * break. The backend answers this question for every savings account and says `0` for the ones with
 * no term, which is what lets one component be rendered unconditionally and decide for itself
 * whether there is anything to show.
 *
 * <p><strong>`whatBreakingWouldCost` is a price and not an estimate.</strong> It is worked out from
 * the balance the account holds right now, by the same function that charges it, so the figure
 * printed beside the button is the figure the button takes. A page that assembled a price of its
 * own out of `earlyExitPenaltyDays` and a rate would be a second opinion about a charge, and the
 * second opinion is the one that is wrong.
 *
 * <p><strong>Nothing here works out whether a term has matured.</strong> `matured`, `locked` and
 * `daysLeft` arrive decided, against the application's own clock — which is the clock a trainer has
 * wound forward. A page counting days from `maturesOn` would disagree with the backend on exactly
 * the morning the money comes free, and would disagree with it permanently on a wound clock.
 */
export type TheTermOnAnAccount = {
  savingsAccountId: number
  termMonths: number
  /** A plain `YYYY-MM-DD`, and `null` for an account that is not on a term at all. */
  maturesOn: string | null
  matured: boolean
  /** Whether money is actually locked away right now, which is the backend's own reading. */
  locked: boolean
  daysLeft: number
  balance: number
  earlyExitPenaltyDays: number
  whatBreakingWouldCost: number
  /**
   * What the terms this account was opened under say happens on the day the term is up, by name, and
   * `null` for an account that is not on a term.
   *
   * <p>Off the version the account is *living under* and never the one on the shelf: the bank may be
   * selling a twelve-month fixed term that comes free at the end while this account agreed to one
   * that rolls into another year, and those are different mornings for the customer holding it.
   */
  maturityAction: 'ROLL_OVER' | 'MOVE_TO_INSTANT' | 'HOLD' | null
  /**
   * That ending as a sentence naming the day, written by the backend, and `null` without a term.
   *
   * <p>Printed rather than assembled here, for the reason the price beside it is: a page that turned
   * `maturityAction` into prose of its own would be a second statement of what an agreement says,
   * kept in step by hand, and the day it fell behind somebody would be told the wrong ending in
   * perfectly confident English.
   */
  whatHappensAtMaturity: string | null
}

/**
 * What happened when a customer broke a fixed term: the day, the maturity they gave up, what it
 * cost, and the agreement the account is living under now.
 *
 * <p>`nowOn.maturesOn` is `null`, which is the point of it being in this answer rather than fetched
 * again: breaking ends the term by moving the account onto free savings, so the maturity date is
 * gone because the account is on a product without one and not because anything cleared a field.
 *
 * <p>`charge` is what was actually taken and is the same number the reading quoted a moment
 * earlier. `balanceItWasChargedOn` and `earlyExitPenaltyDays` are beside it so the customer can redo
 * the sum rather than take it on trust.
 */
export type ATermBroken = {
  savingsAccountId: number
  /** A plain `YYYY-MM-DD`, as the backend's own zone read it. */
  brokenOn: string
  /** A plain `YYYY-MM-DD`: the day it would have matured had they left it alone. */
  wouldHaveMaturedOn: string
  termMonths: number
  earlyExitPenaltyDays: number
  balanceItWasChargedOn: number
  charge: number
  nowOn: TheAgreement
}

/** What this account's term says today, for any savings account, term or not. */
export async function fetchTheTermOn(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<TheTermOnAnAccount> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/term`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load this account’s term'))
  }
  return response.json()
}

/**
 * Breaks a fixed term: charges the stated price, ends the term and moves the account onto free
 * savings.
 *
 * <p><strong>Nothing is sent up, because there is nothing to send.</strong> Breaking a fixed term
 * breaks the whole of it — a partly-broken term would be two agreements over one balance — so there
 * is no amount to name, and the price comes from what the account holds rather than from anything
 * anybody types.
 *
 * <p><strong>Nothing is withdrawn by it either.</strong> The money is the customer's from this
 * moment and where it goes decides a week, a streak and a loyalty clock; the withdrawal that
 * follows is an ordinary withdrawal that nothing refuses.
 *
 * <p>An account with no term and one that has already matured are both refused in a sentence this
 * page shows unchanged — both of which are good news, because the money was already theirs.
 */
export async function breakAFixedTerm(savingsAccountId: number): Promise<ATermBroken> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/term/break`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The term was not broken'))
  }
  return response.json()
}

/**
 * What one savings account would be taking on if its holder took the terms its product is offering
 * today: the version they are on, the version on offer, and what differs between the two.
 *
 * <p><strong>`newerTermsExist` is its own field rather than a list being non-empty.</strong> A
 * version can be published that moves no figure at all — to reword the explanation beside it, or to
 * put a rate back where it was — and such a version is genuinely newer and genuinely takeable. The
 * button is drawn from the flag and the explanation from the list, which keeps "there is something
 * newer" and "here is what it does" two separate statements, as they are.
 *
 * <p><strong>The sentences arrive written.</strong> They are the same strings, in the same order,
 * that the product's version history shows about the same step between two versions, because one
 * function in the backend words a difference. This page prints them and composes nothing.
 *
 * <p>Nothing here says whether taking them is a good idea. The rate cut sitting in free savings'
 * second version is why: an application that marked newer terms as an improvement would be
 * recommending a worse agreement to everybody it had ever repriced.
 */
export type TheNewerTerms = {
  savingsAccountId: number
  productCode: string
  productName: string
  theVersionYouAreOn: number
  theVersionOnOfferToday: number
  newerTermsExist: boolean
  whatWouldChange: string[]
}

/**
 * Takes the newer terms of the product this account is on: the account moves onto the version being
 * sold today, and nothing else moves at all.
 *
 * <p><strong>Nothing is sent up, because there is nothing to send.</strong> There is one version on
 * offer, it is the one the difference was worded against, and it is the one that gets written. A
 * body naming a version would let a page ask for one published for next month, or one that stopped
 * being sold in March.
 *
 * <p><strong>No money, no points and no allocations move.</strong> Taking newer terms is an
 * agreement rather than a transaction, so what comes back is the agreement as it now reads rather
 * than a receipt — there is nothing to give a receipt for.
 *
 * <p>An account inside a fixed term is refused in a sentence naming the day it matures, and an
 * account already on the version on offer is refused in a sentence saying so. Both come back as the
 * backend's own words and this page shows them unchanged.
 */
export async function takeTheNewerTerms(savingsAccountId: number): Promise<TheAgreement> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/newer-terms/take`, {
    method: 'POST',
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The newer terms were not taken'))
  }
  return response.json()
}

/**
 * One card of the comparison screen: a savings product somebody may open an account on, and what a
 * named amount would be worth in it after twelve months — in euros of interest and in points.
 *
 * <p>The product is the same {@link SavingsProduct} the chooser on the overview is drawn from,
 * nested rather than flattened, because it is the same thing: the rate, the condition, the version
 * and the loyalty figures a customer weighs. One shape means the two screens cannot drift into
 * disagreeing about what a product is.
 *
 * <p><strong>`interestIfTheFloorIsNotKept` is null on every product with no bonus to lose, and the
 * null is the message.</strong> Absent means there is nothing to fall under, so the figure beside
 * it is the whole answer; present means the product pays a bonus for keeping a balance, and the two
 * figures together are what a month's slip would cost. `theBonusIsInThatFigure` says which of the
 * two `interest` actually is — an amount smaller than the floor cannot keep it and is projected
 * honestly without the bonus.
 *
 * <p><strong>Three point figures rather than one</strong>, because they are earned on two different
 * days: what the money is worth the moment it lands, what its first anniversary pays a year later,
 * and the two added up. A card showing only the total would hide that half of a notice account's
 * advantage does not arrive for twelve months, which is exactly what somebody weighing a year of
 * lock-in is trying to see.
 *
 * <p>Closed products are not in this list at all. That is the backend's decision rather than this
 * page's: a figure beside an agreement nobody will sign is an invitation to choose it. The chooser
 * on the overview still draws them, marked closed, because customers are holding them.
 */
export type WhatAYearInAProductWouldPay = {
  product: SavingsProduct
  amount: number
  interest: number
  balanceAfterTwelveMonths: number
  theBonusIsInThatFigure: boolean
  interestIfTheFloorIsNotKept: number | null
  pointsWhenTheMoneyLands: number
  pointsOnItsFirstAnniversary: number
  points: number
}

/**
 * What a typed amount would earn in each product over twelve months, in the catalogue's own order.
 *
 * <p>The amount goes up as the text that was typed rather than as a number this page has already
 * read for us. Some of what the backend has to say about a figure is about the characters — "25,00"
 * deserves a sentence about the amount rather than a request that could not be read — and text is
 * the only form that still has them. An empty box is sent as nothing at all, because "you typed
 * nothing" and "what you typed is not an amount" are different sentences and the backend owns both.
 *
 * <p>A GET, because nothing is written and the same question twice gives the same answer: the
 * figure is the question rather than something submitted. That is what lets the screen ask again as
 * somebody types without anything being recorded.
 */
export async function fetchWhatAYearWouldPay(
  amount: string,
  signal?: AbortSignal,
): Promise<WhatAYearInAProductWouldPay[]> {
  const response = await fetch(
    `/api/savings-products/projections?amount=${encodeURIComponent(amount)}`,
    { signal },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not work out what each product would pay'))
  }
  return response.json()
}

/**
 * A move of money from one of a customer's savings accounts to another of their own, as one entry
 * in the ledger.
 *
 * <p><strong>One row although the backend writes two, and that is the claim it makes.</strong> A
 * move takes euros out of one account and puts them into another, so each account's balance has a
 * row of its own behind it — but the customer pressed one button, and a list that showed two would
 * be asking them to pair the halves up by amount and by moment. `savingsAccountId` is where the
 * euros left from and `toSavingsAccountId` is where they arrived, so the row reads as the sentence
 * it is and the arrow is drawn from the pair.
 *
 * <p>`currentAccountId` is `null` for the reason it is null on interest and on a charge: no everyday
 * account was touched at either end, and an identifier invented to fill it would have this page
 * drawing an arrow at an account that was never credited.
 *
 * <p>`pointsEarned` is 0, and it is the rule rather than a gap. The euros were earned on once, in
 * the account next door, and a euro saved twice is one euro — which is exactly why moving is an
 * operation of its own rather than a withdrawal somebody follows with a deposit. `automatic` is
 * `false` because that word means a saving rule, and no rule moves money between savings accounts.
 */
export type AMoveBetweenSavingsAccountsMovement = Omit<LedgerEntry, 'currentAccountId'> & {
  direction: 'BETWEEN_SAVINGS_ACCOUNTS'
  /** The account the euros left. */
  savingsAccountId: number
  /** The account they arrived in, which is another of the same customer's. */
  toSavingsAccountId: number
  /** Nothing at all: a move has no everyday account at either end, so there is none to name. */
  currentAccountId: null
  automatic: false
}

/**
 * What moving money to another of your own savings accounts would cost, read before anything moves.
 *
 * <p><strong>The loyalty clock is the only price, which is the whole reason this reading
 * exists.</strong> Moving is deliberately free of everything a customer would expect it to cost: it
 * earns no points and loses none, it neither secures a week nor breaks a streak, and the most they
 * have ever saved does not move. What it cannot be free of is the clock — the euros arrive as a new
 * deposit dated today, so whatever they were part-way towards is gone and a fresh twelve months
 * begins. A screen that let somebody find that out afterwards would be hiding the one thing there
 * was to weigh.
 *
 * <p>`soonestAnniversaryGivenUp` is `null` when there is nothing to give up at all — an account
 * holding only the interest the bank paid has never had a clock of its own — and a page draws no
 * line at all rather than a line with a blank in it.
 *
 * <p>`pointsOnTheNewAnniversary` is worked out at the rate the **destination** pays, which is not
 * the rate the money is leaving: moving to a better product is worth more per euro, and a reading
 * that quoted only the loss would be arguing one way about a decision this is meant to let somebody
 * make with their eyes open.
 */
export type WhatMovingWouldCost = {
  fromSavingsAccountId: number
  toSavingsAccountId: number
  amount: number
  /** A plain `YYYY-MM-DD`: twelve months from today, when the arriving money first pays. */
  newAnniversary: string
  pointsOnTheNewAnniversary: number
  /** A plain `YYYY-MM-DD`, and `null` when the move would give up no anniversary at all. */
  soonestAnniversaryGivenUp: string | null
  pointsGivenUp: number
  depositsItWouldDrawDown: number
}

/**
 * A move that happened: which two accounts, how much, the two rows it became, why it earned
 * nothing, and the anniversary the arriving money has just started counting towards.
 *
 * <p>`earnedOnCarriedAcross` is what the deposits the money left had already been paid for, carried
 * onto the deposit it arrived in. It is why `pointsEarned` is 0 and why the most this customer has
 * ever saved did not move, and it is shown rather than hidden because somebody looking at a
 * five-thousand-euro movement that earned nothing is owed the reason.
 *
 * <p>`newAnniversary` is the price, reported after it has been paid and identical to the day the
 * reading quoted a moment earlier — both are read the same way, so they cannot disagree.
 */
export type AMoveBetweenSavingsAccounts = {
  fromSavingsAccountId: number
  toSavingsAccountId: number
  customerId: number
  amount: number
  earnedOnCarriedAcross: number
  pointsEarned: number
  withdrawalId: number
  depositId: number
  movedAt: string
  /** A plain `YYYY-MM-DD`, and `null` only if the arriving money has no anniversary to name. */
  newAnniversary: string | null
  pointsOnTheNewAnniversary: number
}

/**
 * Asks what moving this much to that account would cost, and moves nothing.
 *
 * <p>A POST although nothing changes, which is the shape the saving rule's own preview already set:
 * the amount has to reach the backend as the customer typed it, and a figure in a query string is a
 * figure something has already decided how to read.
 *
 * <p>It is refused by everything that would refuse the move itself — a notice period that has not
 * run, a term that has not matured, a goal that has spoken for the money, a closed destination — in
 * the sentence the move would have used. A price quoted for a move that was never going to happen
 * is worse than no price at all.
 */
export async function fetchWhatMovingWouldCost(
  fromSavingsAccountId: number,
  toSavingsAccountId: number,
  amount: string,
  signal?: AbortSignal,
): Promise<WhatMovingWouldCost> {
  const response = await fetch(`/api/savings-accounts/${fromSavingsAccountId}/moves/preview`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount, toSavingsAccountId }),
    signal,
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not work out what moving would cost'))
  }
  return response.json()
}

/**
 * Moves money from one savings account to another of the same customer's, in one press.
 *
 * <p><strong>Not a withdrawal followed by a deposit, and the difference is the feature.</strong>
 * Done as two presses, moving five thousand euros to a better product earns nothing on arrival and
 * nets the week to nothing — each of which is the right answer to its own question, and which
 * together charge somebody a week and a streak for taking the better offer. As one operation it
 * costs neither.
 *
 * <p>The amount travels as the text that was typed, as every amount in this API does, so that a
 * figure with three decimal places comes back as a sentence about the figure rather than being
 * silently rounded on the way.
 */
export async function moveMoneyBetweenSavingsAccounts(
  fromSavingsAccountId: number,
  toSavingsAccountId: number,
  amount: string,
): Promise<AMoveBetweenSavingsAccounts> {
  const response = await fetch(`/api/savings-accounts/${fromSavingsAccountId}/moves`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ amount, toSavingsAccountId }),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The money was not moved'))
  }
  return response.json()
}

/**
 * How much of what a savings account holds its agreement would let leave today, beside what it
 * holds — and, when the two differ, the one condition in the way and the sentence that says what to
 * do about it.
 *
 * <p><strong>One figure, so that no screen composes one.</strong> "What can I take today" used to
 * be three answers a page had to put together: the balance, whether a term has the account locked,
 * and how much ready notice covers. Putting them together means holding the order the conditions
 * compose in — that a lock beats notice, that a floor holds nothing back — and a page holding that
 * order is a second copy of a rule that lives in the backend. The module that owns the conditions
 * answers, and the panel prints.
 *
 * <p><strong>`whyItIsLess` is the sentence a withdrawal of the whole balance would be refused
 * in.</strong> Not a wording invented for a screen: the same words, from the same rule, so somebody
 * who reads the panel and types the amount anyway is told the same thing twice rather than two
 * different things. It is `null` exactly when `freeToTakeToday` equals `balance`, and `condition`
 * is `null` with it — a reason without a shortfall would be a warning about nothing.
 *
 * <p><strong>It is the agreement's answer and not the whole of what may be withdrawn.</strong> A
 * withdrawal is weighed against three things — the balance, the agreement, and what a savings goal
 * has claimed — and the third is the goals panel's own reading further down the same screen. A page
 * that presented this as the last word would be the second place this application decides what can
 * leave a savings account.
 */
export type WhatCanLeaveToday = {
  savingsAccountId: number
  balance: number
  freeToTakeToday: number
  /**
   * The condition in the way, by name, and `null` when the whole balance is free.
   *
   * <p>Beside the sentence rather than instead of it: the sentence is for the person, and the name
   * is for a panel that wants to draw a locked account differently from one waiting on notice
   * without matching on prose a later version of the backend will reword.
   */
  condition: 'A_TERM_THAT_HAS_NOT_MATURED' | 'NOTICE_THAT_HAS_NOT_RUN' | 'A_FLOOR_TO_KEEP' | null
  /** The backend's own sentence, and `null` when nothing is in the way. */
  whyItIsLess: string | null
}

/**
 * What this account's agreement would let leave today, asked for every savings account.
 *
 * <p>Answered for all four products rather than only the ones with a condition attached: free
 * savings comes back with the whole balance free, no condition and no sentence, which is the true
 * answer and the one a panel prints without a warning beside it. A page never has to ask what kind
 * of account it is holding before it dares ask this.
 */
export async function fetchWhatCanLeaveToday(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<WhatCanLeaveToday> {
  const response = await fetch(
    `/api/savings-accounts/${savingsAccountId}/free-to-take-today`,
    { signal },
  )
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not work out what can leave this account today'))
  }
  return response.json()
}

/* ------------------------------------------------------------------- the scheme

   What the bank pays for saving, published in versions and dated. Four calls: two reads anybody
   may make, and two administration posts that carry byte-for-byte the same body.

   The two reads are the customer's own, and that is the backend's decision rather than a shortcut
   taken here. Nothing about the scheme is hidden — the version in force and the whole published
   history, including a version announced for a Monday still to come, are served to anybody who
   asks — so there is no administration read to make, and the workspace that publishes is drawn
   from exactly the reads a customer's own screens use. A second read answering the same thing
   would be a second shape to keep in agreement with the first. */

/**
 * One published version of the scheme: every figure the bank has decided about saving, the Monday
 * it takes effect, its number, and the line saying what changed.
 *
 * <p>The same shape in all three places the backend sends one — on its own as the version in force
 * today, listed as the history, and nested twice inside a preview as the two versions being
 * compared — because they are the same thing at different moments. A page that rendered a version
 * differently depending on which list it came out of would be several renderers to keep in
 * agreement the day somebody adds a figure to the scheme.
 *
 * <p><strong>Every rate is a multiple, every share a percentage and every amount is in euros.</strong>
 * The backend holds rates as basis points and money as cents and hands out neither: `50` is EUR 50 a
 * week, `1` is the ordinary rate per euro, `0.1` is what a further week adds, `1.5` is the cap, and
 * `80` is four fifths of a budget — a percentage and emphatically not a fraction. This page does no
 * arithmetic on any of them. The one exception on the whole workspace is the ladder drawn live under
 * the form, which is a picture of what is in the boxes rather than a figure quoted to anybody, and
 * it says so where it is drawn.
 *
 * <p>`balanceRungs` is an ordered list of euro amounts, ascending, and never empty: a scheme with no
 * rungs is one the backend refuses to publish.
 *
 * <p><strong>Nothing here is nullable, including `whatChanged`.</strong> Where a savings product's
 * first version leaves that line empty because nothing changed, the scheme requires one on every
 * version including the seeded one: nobody opted into the scheme, it applies to everybody from its
 * Monday, and a repricing with a date and no explanation is one of the things this feature exists to
 * fix. A page prints it unconditionally.
 *
 * <p><strong>There is deliberately no "in force" flag.</strong> The Monday is here and whether it has
 * arrived is a comparison somebody makes at the moment they draw a list; a boolean beside the date
 * would be a second answer to the same question, computed at a different moment, and the two would
 * disagree for anybody whose tab was open across a Monday morning.
 */
export type TheSchemeAsPublished = {
  version: number
  effectiveFrom: string
  weeklyThreshold: number
  theOrdinaryRate: number
  extraForEachFurtherWeek: number
  theMostAStreakPays: number
  howLongABatchOfPointsLasts: number
  balanceRungs: number[]
  whatShareOfABudgetIsRunningLow: number
  howManyOutstandingIsASpiral: number
  daysBeforeAMaturityIsWorthSaying: number
  daysBeforeAnAnniversaryIsWorthSaying: number
  whatChanged: string
}

/**
 * The version of the scheme in force today — in force rather than newest published, which is the
 * difference a version announced for next Monday makes.
 *
 * <p>Read by the workspace that reprices the scheme, and read again by the three sentences on the
 * customer's own screens that name how long a batch of points lasts. Those sentences used to say
 * "twelve months" in markup, which was true for exactly as long as nobody could change it.
 */
export async function fetchTheSchemeInForce(signal?: AbortSignal): Promise<TheSchemeAsPublished> {
  const response = await fetch('/api/scheme', { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the scheme'))
  }
  return response.json()
}

/**
 * Every version of the scheme ever published, newest first, including any announced for a Monday
 * still to come.
 *
 * <p>Newest first because this history is read as an announcement board rather than as a story: the
 * question somebody standing in front of it has is "what is coming and what did we just do", which
 * is the opposite of the question a savings product's history answers and is why that one is oldest
 * first. Which of these rows has started is not a field on any of them — see
 * {@link TheSchemeAsPublished} — and the screen works it out from the version in force.
 */
export async function fetchEveryVersionOfTheScheme(
  signal?: AbortSignal,
): Promise<TheSchemeAsPublished[]> {
  const response = await fetch('/api/scheme/versions', { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the published versions'))
  }
  return response.json()
}

/**
 * A candidate version of the scheme, exactly as it was typed.
 *
 * <p><strong>Every figure is text, including the ones that are obviously numbers.</strong> The same
 * reading a deposit's amount and a product's rate already get, and for the same two reasons. Some of
 * what the backend has to say about a figure is about the characters — "0,50" is a mistake somebody
 * makes and deserves an answer about the rate rather than about the request being unreadable — and
 * text is the only form that still has them. And it is what lets an empty box stay empty: a number
 * box left blank arriving as a `null` number is indistinguishable from one somebody typed a nought
 * into, and on this form that difference decides whether every week in the bank secures itself.
 *
 * <p>The rungs are a list of text for the same reason, and a blank one inside the list travels as a
 * blank rather than being dropped, so that "the third rung is empty" is a sentence the backend can
 * say instead of a ladder quietly one rung shorter than the one on the screen.
 *
 * <p><strong>No version number.</strong> Which version this becomes is the backend's answer — one
 * higher than the last published — and a number sent from a form would be a number two people could
 * send at once.
 *
 * <p>This one type is posted to both doors, because the candidate a preview is taken of has to be
 * exactly the candidate a publish would write, down to how an empty box is read. Two types would be
 * two answers to that question, and the whole promise of the preview is that what it looked at is
 * what gets published.
 */
export type ACandidateVersionOfTheScheme = {
  effectiveFrom: string
  weeklyThreshold: string
  theOrdinaryRate: string
  extraForEachFurtherWeek: string
  theMostAStreakPays: string
  howLongABatchOfPointsLasts: string
  balanceRungs: string[]
  whatShareOfABudgetIsRunningLow: string
  howManyOutstandingIsASpiral: string
  daysBeforeAMaturityIsWorthSaying: string
  daysBeforeAnAnniversaryIsWorthSaying: string
  whatChanged: string
}

/**
 * One figure of the scheme as it reads now and as it would read, with the comparison already made.
 *
 * <p>Both sides arrive as text and the screen prints them as they came. They are amounts, multiples,
 * percentages, counts and a whole ladder written out as one string, so there is no one formatter
 * that could be right — and `itWouldChange` is the backend's own comparison rather than
 * `asItReadsNow !== asItWouldRead` worked out here, because a figure that reads the same either side
 * is a figure that did not move and deciding that is not a page's job.
 *
 * <p>`figure` is the name of the box on the form it came out of, so "this row is the one I typed in
 * that box" needs no translation table.
 */
export type AFigureAsItWouldRead = {
  figure: string
  asItReadsNow: string
  asItWouldRead: string
  itWouldChange: boolean
}

/** The size of what publishing a candidate would do, rolled up over every customer the bank has. */
export type HowManyRunsWouldRead = {
  customersExamined: number
  runsThatWouldReadDifferently: number
  whoWouldGain: number
  whoWouldLose: number
  whoAreUntouched: number
  theLargestFallInWeeks: number
  theLargestFallInRate: number
}

/**
 * One customer whose run of weeks would read differently, named, with both readings of both figures.
 *
 * <p>Worst first, capped at twenty, and only those who actually moved. The two falls are negative for
 * somebody a candidate makes better off, which is what lets one list carry both directions without a
 * second field saying which way to read it.
 */
export type ACustomerWhoseRunWouldRead = {
  customerId: number
  name: string
  currentRunNow: number
  currentRunWouldRead: number
  bestRunNow: number
  bestRunWouldRead: number
  rateNow: number
  rateWouldRead: number
  theFallInWeeks: number
  theFallInRate: number
}

/**
 * What a candidate does to points: nothing at all to anything already earned, and a new lifetime
 * from the Monday it takes effect.
 *
 * <p>`saidPlainly` is the backend's own paragraph and the screen prints it unchanged. The day a
 * batch earned on the effective date would die is worked out where the ledger works it out, rather
 * than by adding months to a date in a browser, which is how February gets it wrong.
 */
export type WhatWouldHappenToPoints = {
  nothingAlreadyEarnedChangesItsExpiry: boolean
  howLongABatchLastsNow: number
  howLongABatchWouldLast: number
  aBatchEarnedOnTheEffectiveDateWouldDieOn: string
  saidPlainly: string
}

/** The five lines the scheme draws, by the names they travel under. */
export type ALineTheSchemeDraws =
  | 'A_BALANCE_RUNG'
  | 'A_BUDGET_RUNNING_LOW'
  | 'ARREARS_PILING_UP'
  | 'A_MATURITY_COMING_SOON'
  | 'AN_ANNIVERSARY_COMING_SOON'

/**
 * One of those five lines with the count on either side of it: how many people would be told
 * something tonight who are not being told it now, and how many are being told it now who would not
 * be. All five always come back, including the ones where both counts are nought, because "this line
 * does not move" is an answer and a table that dropped the row would make it look like a line
 * nobody had checked.
 */
export type HowManyStandOnTheFarSideOfALine = {
  line: ALineTheSchemeDraws
  wouldBeToldAndIsNot: number
  isToldAndWouldNotBe: number
}

/**
 * What publishing a candidate version of the scheme would do — and what it is, which is the field
 * worth reading this type for.
 *
 * <p><strong>`whatThisIs` is part of the answer and not a caption somebody writes on a screen.</strong>
 * Everything below it is a counterfactual: what everybody's run *would* read had this been the rule
 * since `asIfItHadBeenTheRuleSince`, and not what happens when the version takes effect, which is
 * nothing, because a week is judged by the scheme in force on its own Monday. A page could carry that
 * sentence and a page could forget it. The screen prints the backend's words and writes none of its
 * own.
 *
 * <p>`itWouldChangeNothing` is answered there too rather than compared here. It is the reading the
 * whole tool is checked against — preview the version already in force and it must say nothing
 * changes — and a screen that decided it from a subset of what came back would eventually call a
 * repricing harmless because it had only looked at some of the figures.
 *
 * <p>`theVersionThisWouldBecome` already carries the version number a publish would give it, so
 * nothing here adds one to work it out.
 *
 * <p>`figures` is always the same ten rows in the same order, which is what lets the comparison panel
 * be a table rather than a search.
 */
export type WhatThisSchemeWouldDo = {
  whatThisIs: string
  overHowManyWeeks: number
  asIfItHadBeenTheRuleSince: string
  itWouldChangeNothing: boolean
  theVersionInForce: TheSchemeAsPublished
  theVersionThisWouldBecome: TheSchemeAsPublished
  figures: AFigureAsItWouldRead[]
  runs: HowManyRunsWouldRead
  theWorstAffected: ACustomerWhoseRunWouldRead[]
  points: WhatWouldHappenToPoints
  notifications: HowManyStandOnTheFarSideOfALine[]
}

/**
 * Asks what publishing this candidate would do, and writes nothing whatsoever.
 *
 * <p>A POST that changes nothing, which is worth one sentence. It is a POST because the candidate is
 * thirteen figures and a ladder — a body, not a query string — and because it is the same body the
 * publishing door takes: this screen holds one form and points it at two addresses.
 *
 * <p><strong>A candidate the publish door would refuse is refused here in the same words.</strong>
 * Not by a check repeated on the backend and certainly not by one repeated here: the same reading
 * runs on both paths, so preview and publish cannot disagree about what is sayable, which is the
 * property that makes previewing worth doing at all.
 */
export async function previewAVersionOfTheScheme(
  candidate: ACandidateVersionOfTheScheme,
): Promise<WhatThisSchemeWouldDo> {
  const response = await fetch('/api/admin/scheme/preview', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(candidate),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The candidate could not be previewed'))
  }
  return response.json()
}

/**
 * What comes back from publishing: the version that now exists, and whether its day has come.
 *
 * <p>The boolean is the half a page cannot work out for itself safely. Whether a dated row has
 * started is a comparison made at a moment, and the confirmation somebody reads after pressing
 * Publish should be worded from an answer the backend gave at the moment it wrote the row rather than
 * from a page's arithmetic about a clock it read separately. It is false on every publish this
 * application will currently accept, because a version may only take effect on a Monday still to
 * come — a fact about the rule rather than about the field.
 */
export type TheVersionOfTheSchemeThatNowExists = {
  published: TheSchemeAsPublished
  itsDayHasCome: boolean
}

/**
 * Publishes the next version of the scheme and answers with it.
 *
 * <p><strong>Nothing about this is authenticated.</strong> There is no token to send and nothing to
 * send it with: the backend does not check that whoever is asking runs the bank, because there is
 * nothing in this application to check it against. Anybody who can reach this page can reprice the
 * scheme for every customer the bank has, and the screen says so at the top in those words.
 *
 * <p>Every field goes up as it was typed and nothing is checked here first. Whether a rate may be
 * quoted to five places, whether nought is a thing the ordinary rate may be, whether the rungs climb
 * and whether a version may take effect on the day named are all the backend's rules, and each comes
 * back as a sentence this page shows unchanged. A page that checked first would be a second copy of
 * the rules, and the second copy is always the one that is out of date.
 *
 * <p>Independent of {@link previewAVersionOfTheScheme}. The backend will publish a candidate nobody
 * previewed, quite deliberately — remembering what it had been asked about first is the draft table
 * the module spent a ticket not building — so "you may not publish what you have not looked at" is a
 * rule of the screen, enforced by the screen, and it is enforced by comparing the previewed form
 * with the form as it now stands rather than by a flag a later keystroke could forget to clear.
 */
export async function publishAVersionOfTheScheme(
  candidate: ACandidateVersionOfTheScheme,
): Promise<TheVersionOfTheSchemeThatNowExists> {
  const response = await fetch('/api/admin/scheme/versions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(candidate),
  })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'The version was not published'))
  }
  return response.json()
}
