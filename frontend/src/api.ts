/**
 * Where the backend is, worked out from the page rather than assumed to be the server root.
 *
 * <p>A URL that starts with "/" throws away whatever path prefix the app is mounted under, so
 * `/api/customers` only ever works when the app is served at "/". It is also served under
 * `/proxy/80/` when it is reached through code-server's port proxy, and there a leading slash asks
 * code-server for the endpoint rather than this app's backend. Resolving against `document.baseURI`
 * keeps one prefix for the page and its API wherever it is mounted, and resolves to exactly the old
 * `/api/...` when that prefix is "/".
 */
const apiUrl = (path: string) => new URL(`api/${path}`, document.baseURI).toString()

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
  const response = await fetch(apiUrl('customers/sign-in'), {
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
export async function fetchCustomers(): Promise<Customer[]> {
  const response = await fetch(apiUrl('customers'))
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load the customer list'))
  }
  return response.json()
}

export async function fetchAccounts(
  customerId: number,
  signal?: AbortSignal,
): Promise<CustomerAccounts> {
  const response = await fetch(apiUrl(`customers/${customerId}/accounts`), { signal })
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
 * A deposit that was made, and what it earned when it was made.
 *
 * <p>`pointsEarned` is everything it earned however it earned it, which is what it has always meant:
 * `basePoints` and `streakBonusPoints` are the two parts of that figure and always add up to it, so
 * nine points against a seven-euro deposit is an arithmetic a customer can check rather than a number
 * they have to take on trust.
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
  multiplierApplied: number
  depositedAt: string
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
  const response = await fetch(apiUrl(`savings-accounts/${savingsAccountId}`), { signal })
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
  const response = await fetch(apiUrl(`savings-accounts/${savingsAccountId}/deposits`), { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load this account’s deposits'))
  }
  return response.json()
}

export async function fetchWithdrawals(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<RecordedWithdrawal[]> {
  const response = await fetch(apiUrl(`savings-accounts/${savingsAccountId}/withdrawals`), { signal })
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
  const response = await fetch(apiUrl('rewards'), { signal })
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
  const response = await fetch(apiUrl(`customers/${customerId}/redemptions`), { signal })
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
  const response = await fetch(apiUrl(`customers/${customerId}/redemptions`), {
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
  const response = await fetch(apiUrl(`savings-accounts/${savingsAccountId}/deposits`), {
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
  const response = await fetch(apiUrl(`savings-accounts/${savingsAccountId}/withdrawals`), {
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
  const response = await fetch(apiUrl(`customers/${customerId}/money-movements`), { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load your money history'))
  }
  return response.json()
}
