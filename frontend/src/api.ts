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

/** A savings account in an overview, worth the money in it. */
export type SavingsAccount = {
  id: number
  moneyBalance: number
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
  /** Gross new saving that has landed since Monday, across every account they hold. */
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
  try {
    const problem: unknown = await response.json()
    const reason = (problem as { detail?: unknown } | null)?.detail
    if (typeof reason === 'string' && reason.trim() !== '') {
      return reason
    }
  } catch {
    // A body that is not JSON says no more than the status already did.
  }
  return `${whenNoneGiven} (${response.status})`
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
   * What the holder has to spend, which is not this account's figure but theirs: the same number is
   * reported beside every account they hold. Paying in here adds to it, which is why it is shown
   * beside this balance — what paying in *here* earned is on each deposit in the history.
   */
  pointsBalance: number
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
  /** Gross new saving that has landed since Monday, counted in the backend's own timezone. */
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
 * <p>`multiplierApplied` is the rate this deposit was in fact paid at, decided when the money moved
 * and never worked out again. Not the rate on `SavingsAccountBalances`, which is what the *next*
 * deposit will earn at: a run that has since lapsed leaves the two disagreeing, and both are right.
 * A deposit made before the scheme existed reports the ordinary 1.00, which is what it was paid.
 */
export type RecordedDeposit = {
  id: number
  amount: number
  pointsEarned: number
  basePoints: number
  streakBonusPoints: number
  loyaltyBonusPoints: number
  multiplierApplied: number
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

/** A reward that has been claimed, and the voucher that came out of it. */
export type ClaimedReward = {
  id: number
  code: string
  title: string
  pointsSpent: number
  voucherCode: string
  claimedAt: string
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
 * One movement of money across the boundary between an everyday account and savings.
 *
 * <p>One shape for both kinds, which is the point of the ledger: a deposit and a withdrawal are the
 * same event seen from opposite sides, and a customer reading back over what they have done with
 * their money reads one story rather than two lists they have to interleave by eye. What differs
 * between the two is in `direction` rather than in the fields, so a row renders the same way
 * whichever it is.
 *
 * <p>`direction` arrives as the backend's own word rather than as a sign on the amount. An amount of
 * money in this application is always a positive figure, and a ledger that carried the direction in
 * the sign of the number would be the one place that stopped being true.
 *
 * <p>`pointsEarned` is 0 for a withdrawal, which is what a withdrawal earns rather than a gap in the
 * record — money coming back out has never earned a point here.
 *
 * <p>`id` is unique within a direction and not across the ledger: deposits and withdrawals are
 * numbered separately, so anything keying rows off it has to key off the pair.
 */
export type MoneyMovement = {
  direction: 'INTO_SAVINGS' | 'OUT_OF_SAVINGS'
  id: number
  savingsAccountId: number
  currentAccountId: number
  amount: number
  pointsEarned: number
  movedAt: string
}

/**
 * Every euro this customer has moved into or out of savings, newest first, across every savings
 * account they hold.
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
 * <p>Four values and no more today, and the union is written out here rather than left as a
 * `string` so that a page switching on it is told by the compiler when the backend grows a fifth
 * — points about to expire, a reward newly affordable — instead of quietly rendering nothing for
 * a reason it has never heard of.
 */
export type NotificationReason =
  | 'BALANCE_THRESHOLD_REACHED'
  | 'BALANCE_THRESHOLD_LOST'
  | 'LOYALTY_BONUS_ABOUT_TO_PAY'
  | 'LOYALTY_BONUS_AT_RISK'

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
 *   `depositId`, `points` or `occursOn`.
 * - `LOYALTY_BONUS_ABOUT_TO_PAY` and `LOYALTY_BONUS_AT_RISK` carry `depositId`, `points` and
 *   `occursOn`, and no `amount`.
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
  savingsAccountId: number
  depositId: number | null
  /** The rung, for a balance reason, and null for the two anniversary reasons. */
  amount: number | null
  /** What the anniversary pays, for a loyalty reason, and null for the two balance reasons. */
  points: number | null
  /** The anniversary day as `YYYY-MM-DD`, for a loyalty reason, and null for the balance ones. */
  occursOn: string | null
  raisedAt: string
  /** When the customer read it, and null while it is unread. */
  readAt: string | null
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
