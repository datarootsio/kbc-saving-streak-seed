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

/** A savings account in an overview, worth what it holds and what that has earned. */
export type SavingsAccount = {
  id: number
  moneyBalance: number
  pointsBalance: number
}

export type CustomerAccounts = {
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
export async function fetchCustomers(): Promise<Customer[]> {
  const response = await fetch('/api/customers')
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
  pointsBalance: number
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

export type RecordedDeposit = {
  id: number
  amount: number
  pointsEarned: number
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
 * What this savings account has claimed, newest first. The other half of the points balance: the
 * deposits say what came in, these say what went out, and the balance is what the two leave.
 */
export async function fetchClaimed(
  savingsAccountId: number,
  signal?: AbortSignal,
): Promise<ClaimedReward[]> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/redemptions`, { signal })
  if (!response.ok) {
    throw new Error(await reasonRefused(response, 'Could not load what this account has claimed'))
  }
  return response.json()
}

/**
 * Claims a reward, and there is no way back: the voucher exists the moment this succeeds. What it
 * costs is not sent — the price is the backend's, and a page that named one could name the wrong one.
 */
export async function claimReward(
  savingsAccountId: number,
  reward: string,
): Promise<ClaimedReward> {
  const response = await fetch(`/api/savings-accounts/${savingsAccountId}/redemptions`, {
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
