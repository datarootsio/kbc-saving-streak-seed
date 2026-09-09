import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type FormEvent,
  type ReactNode,
} from 'react'
import {
  claimReward,
  fetchAccounts,
  fetchClaimed,
  fetchCustomers,
  fetchDeposits,
  fetchMoneyMovements,
  fetchWithdrawals,
  fetchRewards,
  fetchSavingsAccount,
  makeDeposit,
  makeWithdrawal,
  signIn,
  SignInFailed,
  type ClaimedReward,
  type CurrentAccount,
  type Customer,
  type CustomerAccounts,
  type MoneyMovement,
  type RecordedDeposit,
  type RecordedWithdrawal,
  type Reward,
  type SavingsAccount,
  type SavingsAccountBalances,
} from './api'

const euros = new Intl.NumberFormat('nl-BE', { style: 'currency', currency: 'EUR' })
const points = new Intl.NumberFormat('nl-BE')
const dateAndTime = new Intl.DateTimeFormat('nl-BE', { dateStyle: 'short', timeStyle: 'short' })

/**
 * A date with no time on it, for a deadline that is a day rather than a moment. Points reach their
 * twelve-month anniversary at whatever time of day they were earned, and telling somebody their
 * points go at 14:32 would be precision they cannot act on — the sweep that acts on it runs
 * overnight, so the day is the promise.
 */
const dateOnly = new Intl.DateTimeFormat('nl-BE', { dateStyle: 'long' })

/**
 * A `YYYY-MM-DD` from the backend, written out as a date somebody reads.
 *
 * <p>The time is appended, and that is the whole point of this function rather than passing the
 * string straight to `new Date`. A date on its own is parsed as midnight **UTC**, so anybody west of
 * Greenwich would be shown the day before the one the backend sent; the same string with a time and
 * no offset is parsed in the browser's own zone, which leaves the three numbers exactly as they
 * arrived. Nothing here converts between zones, because the backend has already decided which day
 * this is — in {@link https://en.wikipedia.org/wiki/Time_in_Belgium Europe/Brussels}, named once
 * there — and a page that converted it would be picking the zone of the machine it happened to be
 * drawing on.
 */
function asADay(day: string): string {
  return dateOnly.format(new Date(`${day}T00:00:00`))
}

/**
 * A multiplier, always to two places. 1,5 and 1,50 are the same number and only one of them reads as
 * a rate on a ladder that climbs in tenths; both places are kept so that the rungs line up with each
 * other however the backend's JSON happened to write the figure.
 */
const rate = new Intl.NumberFormat('nl-BE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/**
 * Whether the person at the screen has asked their system for less movement. Read once: it decides
 * whether a figure counts up to its new value or simply arrives at it, and a balance must never be
 * animated for someone who has said they do not want that.
 */
const stillness =
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * What the browser keeps between visits: the address somebody signed in with, and nothing else. Not
 * a token, because there is none — signing in is the backend recognising an address, and this is
 * only saving whoever comes back the trouble of typing it again.
 */
const REMEMBERED = 'saving-streak.signed-in-as'

function remember(contactDetails: string | null) {
  try {
    if (contactDetails === null) {
      window.localStorage.removeItem(REMEMBERED)
    } else {
      window.localStorage.setItem(REMEMBERED, contactDetails)
    }
  } catch {
    // A browser that will not store anything is a browser that asks for the address every time,
    // which is no worse than never having asked.
  }
}

function remembered(): string | null {
  try {
    return window.localStorage.getItem(REMEMBERED)
  } catch {
    return null
  }
}

export default function App() {
  const [customer, setCustomer] = useState<Customer | null>(null)
  // Whether a remembered address is still being tried. Without it the sign-in screen appears for a
  // moment in front of somebody who is already signed in, and then vanishes.
  const [returning, setReturning] = useState(remembered() !== null)

  useEffect(() => {
    const address = remembered()
    if (address === null) {
      return
    }
    // Signed in again rather than restored from what was stored: the customer it answers with is
    // the one the backend has now. A database rebuilt between demonstrations hands out new
    // identifiers, and a remembered one would point at somebody else's accounts or at nothing.
    signIn(address)
      .then(setCustomer)
      .catch((problem: unknown) => {
        // Forgotten only when the backend has actually answered that the address is not a
        // customer's. A request that never got an answer — a backend still starting up, most
        // likely — says nothing about the address, and forgetting it there would sign somebody out
        // over a few seconds of the application not being ready yet.
        if (problem instanceof SignInFailed && problem.addressRejected) {
          remember(null)
        }
      })
      .finally(() => setReturning(false))
  }, [])

  function signedIn(who: Customer) {
    remember(who.contactDetails)
    setCustomer(who)
  }

  function signOut() {
    remember(null)
    setCustomer(null)
  }

  return (
    <>
      <Backdrop />
      {customer === null ? (
        // The remembered address fills the field when signing back in did not work. Whoever it
        // belongs to is looking at a sign-in screen they did not expect, and the least this can do
        // is not make them type it again.
        <SignIn stillTrying={returning} knownAddress={remembered() ?? ''} onSignedIn={signedIn} />
      ) : (
        <Banking customer={customer} onSignOut={signOut} />
      )}
    </>
  )
}

/**
 * The way in: an address, and the backend saying who that is.
 *
 * <p>There is no password field because there is nothing that would check one, and the screen says
 * so rather than leaving somebody to wonder where it went. What this establishes is who the page is
 * showing, not who is allowed to see it — every request after it still names the account it is
 * about, and nothing anywhere asks whether this browser was ever told about that account.
 */
function SignIn({
  stillTrying,
  knownAddress,
  onSignedIn,
}: {
  stillTrying: boolean
  knownAddress: string
  onSignedIn: (customer: Customer) => void
}) {
  const [address, setAddress] = useState(knownAddress)
  const [signingIn, setSigningIn] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)
  const [demonstrating, setDemonstrating] = useState<Customer[] | null>(null)

  useEffect(() => {
    // Shortcuts for a demonstration, and nothing depends on them: a list that will not load costs
    // this screen a row of buttons and leaves the field to be typed into as it always could be.
    fetchCustomers()
      .then(setDemonstrating)
      .catch(() => setDemonstrating([]))
  }, [])

  function submit(event: FormEvent) {
    event.preventDefault()
    setSigningIn(true)
    setRefusal(null)
    // Sent exactly as typed, and refused in the backend's own words. Whether an address belongs to
    // a customer is not something this screen could know, so it is not something it checks.
    signIn(address)
      .then(onSignedIn)
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setSigningIn(false))
  }

  if (stillTrying) {
    return (
      <div className="shell gate">
        <div className="panel signin">
          <Waiting label="Signing you back in…" bars={['60%', '100%', '45%']} />
        </div>
      </div>
    )
  }

  return (
    <div className="shell gate">
      <form className="panel signin" onSubmit={submit}>
        <span className="mark">
          <StreakIcon />
        </span>
        <h1>Saving Streak</h1>
        <p className="tagline">Move money into savings, earn a point for every whole euro.</p>

        <div className="field">
          <label htmlFor="contactDetails">Email address</label>
          <input
            id="contactDetails"
            name="contactDetails"
            type="email"
            inputMode="email"
            autoComplete="email"
            placeholder="you@example.be"
            value={address}
            onChange={(event) => setAddress(event.target.value)}
          />
        </div>

        <button type="submit" disabled={signingIn}>
          {signingIn ? (
            <>
              <span className="spinner" />
              Signing in…
            </>
          ) : (
            'Log in'
          )}
        </button>

        {refusal !== null && <Refusal reason={refusal} />}

        {demonstrating !== null && demonstrating.length > 0 && (
          <div className="demo">
            <p className="explanation">No password — pick one of these to try it.</p>
            <ul>
              {demonstrating.map((who) => (
                <li key={who.id}>
                  <button
                    type="button"
                    className="quiet"
                    onClick={() => {
                      setAddress(who.contactDetails)
                      setRefusal(null)
                    }}
                  >
                    <span className="avatar" aria-hidden="true">{initialsOf(who.name)}</span>
                    <span className="choice-name">
                      {who.name}
                      <span className="choice-note">{who.contactDetails}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </form>
    </div>
  )
}

/**
 * Which screen the signed-in application is showing.
 *
 * <p>A union rather than a couple of nullable fields, so that the states this component can be in
 * are exactly the states it has a screen for: an open savings account and an open history at the
 * same time is not one of them and cannot be arrived at.
 *
 * <p>Held in state rather than in the URL, which is the same bargain the rest of this application
 * strikes: there is no router, no history entry and no shareable link to a screen. A reload comes
 * back to the overview.
 */
type Screen =
  | { at: 'home' }
  | { at: 'history' }
  | { at: 'savings-account'; savingsAccountId: number }

/**
 * Everything behind the sign-in screen: what this customer holds, whichever savings account they
 * have opened, and the history of everything they have moved.
 *
 * <p>The accounts are read here rather than on the page that shows them, because the page that
 * changes them is the other one. A deposit or a claim tells this component to read them again, so
 * going back to the overview finds the figures that were just changed rather than the ones that
 * were there when it was last drawn.
 */
function Banking({ customer, onSignOut }: { customer: Customer; onSignOut: () => void }) {
  const [accounts, setAccounts] = useState<CustomerAccounts | null>(null)
  const [accountsError, setAccountsError] = useState<string | null>(null)
  // The catalogue belongs to no account and no customer, so it is read once here and handed to the
  // page that browses and spends against it.
  const [rewards, setRewards] = useState<Reward[] | null>(null)
  const [rewardsError, setRewardsError] = useState<string | null>(null)
  // What this customer has claimed, read here beside their accounts because it belongs to the same
  // person: their points are one pot, so what has come out of it is one list rather than one per
  // account.
  const [claimed, setClaimed] = useState<ClaimedReward[] | null>(null)
  const [claimedError, setClaimedError] = useState<string | null>(null)
  // Which of the three screens is showing. A union rather than a pair of nullable fields, so that
  // "an account is open and so is the history" is not a state this component can get into.
  const [screen, setScreen] = useState<Screen>({ at: 'home' })

  const loadAccounts = useCallback((signal?: AbortSignal) => {
    fetchAccounts(customer.id, signal)
      .then((held) => {
        if (signal?.aborted !== true) {
          setAccounts(held)
          setAccountsError(null)
        }
      })
      .catch((problem: Error) => {
        if (signal?.aborted !== true) {
          setAccountsError(problem.message)
        }
      })
  }, [customer.id])

  const loadClaimed = useCallback((signal?: AbortSignal) => {
    fetchClaimed(customer.id, signal)
      .then((theirs) => {
        if (signal?.aborted !== true) {
          setClaimed(theirs)
          setClaimedError(null)
        }
      })
      .catch((problem: Error) => {
        if (signal?.aborted !== true) {
          setClaimedError(problem.message)
        }
      })
  }, [customer.id])

  useEffect(() => {
    const request = new AbortController()
    loadAccounts(request.signal)
    return () => request.abort()
  }, [loadAccounts])

  useEffect(() => {
    const request = new AbortController()
    loadClaimed(request.signal)
    return () => request.abort()
  }, [loadClaimed])

  useEffect(() => {
    fetchRewards()
      .then(setRewards)
      .catch((problem: Error) => setRewardsError(problem.message))
  }, [])

  const openedAccount =
    screen.at === 'savings-account'
      ? accounts?.savingsAccounts.find((account) => account.id === screen.savingsAccountId) ?? null
      : null

  return (
    <>
      <TopBar
        customer={customer}
        onSignOut={onSignOut}
        // One level deep is as deep as this application goes, so the way back is always the
        // overview — from an open account and from the history alike.
        onBack={screen.at === 'home' ? null : () => setScreen({ at: 'home' })}
      />

      <div className="shell">
        {screen.at === 'home' && (
          <Home
            customerId={customer.id}
            accounts={accounts}
            accountsError={accountsError}
            rewards={rewards}
            rewardsError={rewardsError}
            claimed={claimed}
            claimedError={claimedError}
            onClaimed={() => {
              // The points that paid for it are on the overview and the voucher belongs in the
              // list beside them, so both are read again.
              loadAccounts()
              loadClaimed()
            }}
            onOpen={(savingsAccountId) => setScreen({ at: 'savings-account', savingsAccountId })}
            onOpenHistory={() => setScreen({ at: 'history' })}
          />
        )}

        {screen.at === 'savings-account' && (
          <main>
            <SavingsAccountPage
              // Remounting on a change of account is what keeps a half-typed amount, an error and a
              // stale balance from following the customer to a different account.
              key={screen.savingsAccountId}
              savingsAccountId={screen.savingsAccountId}
              currentAccounts={accounts?.currentAccounts ?? []}
              // Every figure on the overview is behind whatever just happened here: the money in
              // both accounts, and the points the deposit earned.
              onChanged={loadAccounts}
            />
          </main>
        )}

        {screen.at === 'history' && (
          <main>
            <MoneyHistory
              customerId={customer.id}
              currentAccounts={accounts?.currentAccounts ?? []}
            />
          </main>
        )}

        {/* Held by the account it belongs to, so it is still on screen while one is open. */}
        {screen.at === 'savings-account' && openedAccount === null && accountsError !== null && (
          <Refusal reason={accountsError} />
        )}
      </div>
    </>
  )
}

/** Who is signed in, the way back, and the way out. */
function TopBar({
  customer,
  onSignOut,
  onBack,
}: {
  customer: Customer
  onSignOut: () => void
  onBack: (() => void) | null
}) {
  return (
    <header className="topbar">
      <div className="topbar-inner masthead">
        <span className="mark">
          <StreakIcon />
        </span>
        <div className="topbar-words">
          <h1>Saving Streak</h1>
          {onBack === null ? (
            <p className="tagline">Signed in as {customer.name}</p>
          ) : (
            <button type="button" className="quiet back" onClick={onBack}>
              <BackIcon />
              All accounts
            </button>
          )}
        </div>
        <div className="who">
          <span className="avatar" aria-hidden="true">{initialsOf(customer.name)}</span>
          <button type="button" className="quiet" onClick={onSignOut}>
            Sign out
          </button>
        </div>
      </div>
    </header>
  )
}

/**
 * What the customer holds and what they can spend it on: every account with what is in it, and
 * underneath, the points all of that saving has earned them and the rewards those points buy.
 *
 * <p>Nothing here is added up. Two savings balances are two balances, and a total across them would
 * be a figure this page worked out for itself — which is the one thing no figure on any of these
 * screens is. The points are one figure because the backend sends one: they belong to the customer,
 * and summing what each account earned is its arithmetic and not this page's.
 */
function Home({
  customerId,
  accounts,
  accountsError,
  rewards,
  rewardsError,
  claimed,
  claimedError,
  onClaimed,
  onOpen,
  onOpenHistory,
}: {
  customerId: number
  accounts: CustomerAccounts | null
  accountsError: string | null
  rewards: Reward[] | null
  rewardsError: string | null
  claimed: ClaimedReward[] | null
  claimedError: string | null
  onClaimed: () => void
  onOpen: (savingsAccountId: number) => void
  onOpenHistory: () => void
}) {
  return (
    <main>
      {accountsError !== null && (
        <div className="panel">
          <Refusal reason={accountsError} />
        </div>
      )}

      {accounts === null && accountsError === null && (
        <div className="panel">
          <Waiting label="Loading your accounts…" bars={['7rem', '100%', '60%']} />
        </div>
      )}

      {accounts !== null && (
        <>
          <section className="panel">
            <h2>Current account</h2>
            {accounts.currentAccounts.length === 0 ? (
              <p className="nothing">No current account.</p>
            ) : (
              <ul className="cards">
                {accounts.currentAccounts.map((account) => (
                  <CurrentAccountCard key={account.id} account={account} />
                ))}
              </ul>
            )}
          </section>

          <section className="panel">
            <h2>Savings accounts</h2>
            {accounts.savingsAccounts.length === 0 ? (
              <p className="nothing">No savings account.</p>
            ) : (
              <ul className="cards">
                {accounts.savingsAccounts.map((account) => (
                  <SavingsAccountCard key={account.id} account={account} onOpen={onOpen} />
                ))}
              </ul>
            )}
            {/* In this panel rather than a panel of its own, because the history is a history of
                these accounts: every row in it moved money into or out of one of the cards above.
                Shown even with no accounts, so that an empty overview still has a way onwards. */}
            <button type="button" className="way-through" onClick={onOpenHistory}>
              <span className="way-through-words">
                <span className="way-through-name">Money history</span>
                <span className="way-through-note">
                  Every euro in and out, across all your savings, newest first
                </span>
              </span>
              <span className="card-go" aria-hidden="true">
                <ForwardIcon />
              </span>
            </button>
          </section>

          <SavingStreak
            newSavingsThisWeek={accounts.newSavingsThisWeek}
            weeklyMinimum={accounts.weeklyMinimum}
            currentStreakWeeks={accounts.currentStreakWeeks}
            bestStreakWeeks={accounts.bestStreakWeeks}
            currentMultiplier={accounts.currentMultiplier}
          />

          <Rewards
            customerId={customerId}
            pointsToSpend={accounts.pointsBalance}
            expiringNext={accounts.pointsExpiringNext}
            expiringNextOn={accounts.pointsExpiringNextOn}
            rewards={rewards}
            rewardsError={rewardsError}
            claimed={claimed}
            claimedError={claimedError}
            onClaimed={onClaimed}
          />
        </>
      )}
    </main>
  )
}

/** An everyday account: the IBAN it is known by, and the money in it. */
function CurrentAccountCard({ account }: { account: CurrentAccount }) {
  return (
    <li className="card current">
      <span className="card-icon" aria-hidden="true">
        <BankIcon />
      </span>
      <div className="card-words">
        <span className="card-name">Current account</span>
        <span className="iban">{spacedIban(account.iban)}</span>
      </div>
      <p className="card-figure">{euros.format(account.balance)}</p>
    </li>
  )
}

/**
 * A savings account: what it holds, and the way into it.
 *
 * <p>The money and nothing else. The points a customer has are theirs rather than any one account's,
 * so a figure repeated on every card would say that each account had its own — and somebody holding
 * two would appear to have twice the points they can actually spend. They are shown once, under
 * Rewards, where they are spent.
 */
function SavingsAccountCard({
  account,
  onOpen,
}: {
  account: SavingsAccount
  onOpen: (savingsAccountId: number) => void
}) {
  return (
    <li className="card savings">
      <button type="button" onClick={() => onOpen(account.id)}>
        <span className="card-icon pot" style={hueOf(account.id)} aria-hidden="true">
          <PotIcon />
        </span>
        <div className="card-words">
          <span className="card-name">Savings account {account.id}</span>
        </div>
        <p className="card-figure">{euros.format(account.moneyBalance)}</p>
        <span className="card-go" aria-hidden="true">
          <ForwardIcon />
        </span>
      </button>
    </li>
  )
}

/**
 * How the saving is going: what has landed in the week the customer is part-way through, how much
 * more that week asks for, the run of consecutive weeks behind it, and what that run pays per euro.
 *
 * <p>One run for the person rather than one per account, which is what makes it belong on this page:
 * a week counts what they put away wherever they put it, so the figure would be the same on every
 * savings account card and is shown once instead.
 *
 * <p>Every figure is the backend's, including what a week asks for. The cell underneath draws the
 * amount, the bar and the sentence from one number so that no frame of it can say the week is done
 * while the figure on the screen is still short — which is why {@link ThisWeek} takes the figure
 * being shown rather than the one it is climbing towards.
 */
function SavingStreak({
  newSavingsThisWeek,
  weeklyMinimum,
  currentStreakWeeks,
  bestStreakWeeks,
  currentMultiplier,
}: {
  newSavingsThisWeek: number
  weeklyMinimum: number
  currentStreakWeeks: number
  bestStreakWeeks: number
  currentMultiplier: number
}) {
  return (
    <section className="panel">
      <h2>Your saving streak</h2>
      <p className="explanation">
        Save enough in a week and it counts. Longer runs pay more per euro.
      </p>
      <dl className="balances saving">
        <div className="week">
          <dt>This week</dt>
          <dd>
            <Rising
              value={newSavingsThisWeek}
              format={(shown) => <ThisWeek shown={shown} weeklyMinimum={weeklyMinimum} />}
            />
            {/* Outside the rise on purpose, as on the account page: neither figure is climbing
                anywhere, and both are about weeks already settled rather than about the amount
                still moving above. */}
            <Streak
              currentWeeks={currentStreakWeeks}
              bestWeeks={bestStreakWeeks}
              multiplier={currentMultiplier}
            />
          </dd>
        </div>
      </dl>
    </section>
  )
}

/**
 * The points the customer has, what they buy, and what has already been bought with them.
 *
 * <p>All three in one panel, and that is the point of the panel: the balance is the pot every one of
 * their savings accounts earns into, the catalogue is what the pot buys, and the list underneath is
 * what has come out of it. A customer can add the vouchers up against the figure above them.
 *
 * <p>Claimed from here rather than from an account, because the points are the person's. There used
 * to be nothing to press on this screen — points belonged to one savings account at a time, so there
 * was no such thing as what this customer could afford — and one pot is exactly what makes the button
 * possible.
 *
 * <p>What each reward is, what it costs and what to call it all come from the backend, so a reward
 * added or repriced there appears here with no change: the only thing this page decides is which
 * picture to put beside a code it recognises, and there is one for a code it does not.
 */
function Rewards({
  customerId,
  pointsToSpend,
  expiringNext,
  expiringNextOn,
  rewards,
  rewardsError,
  claimed,
  claimedError,
  onClaimed,
}: {
  customerId: number
  pointsToSpend: number
  expiringNext: number | null
  expiringNextOn: string | null
  rewards: Reward[] | null
  rewardsError: string | null
  claimed: ClaimedReward[] | null
  claimedError: string | null
  onClaimed: () => void
}) {
  // Which reward is being claimed rather than whether one is, so that the button that was pressed is
  // the one that shows it is working and the others simply stop being pressable.
  const [claiming, setClaiming] = useState<string | null>(null)
  const [claimError, setClaimError] = useState<string | null>(null)
  // The voucher just issued, kept only long enough to say so. It is the backend's own answer to the
  // request, so the celebration cannot congratulate someone for a voucher they were not issued.
  const [celebrated, setCelebrated] = useState<ClaimedReward | null>(null)

  useEffect(() => {
    if (celebrated === null) {
      return
    }
    const over = setTimeout(() => setCelebrated(null), 2600)
    return () => clearTimeout(over)
  }, [celebrated])

  function claim(reward: Reward) {
    setClaiming(reward.code)
    setClaimError(null)
    claimReward(customerId, reward.code)
      .then((issued) => {
        setCelebrated(issued)
        onClaimed()
      })
      .catch((problem: Error) => setClaimError(problem.message))
      .finally(() => setClaiming(null))
  }

  return (
    <section className="panel">
      <h2>Rewards</h2>
      <p className="explanation">Claims are final — the voucher is issued straight away.</p>

      {/* The figure, its unit and the deadline under it are drawn from whatever the rise is
          showing at this moment rather than from the settled figure, the way the week's cell is:
          no frame of it can say more points are about to go than the balance above them.

          The deadline is under the balance rather than anywhere else because it is the same figure
          read from the other end — what they can spend, and how long they have to spend it in.
          Points that vanish with no warning are indistinguishable from points gone missing. */}
      <p className="points-to-spend">
        <SparkIcon />
        <Rising
          value={pointsToSpend}
          format={(shown) => (
            <>
              {points.format(Math.round(shown))}
              <span className="unit">points to spend</span>
              <ExpiringNext
                expiring={expiringNext}
                on={expiringNextOn}
                outOf={Math.round(shown)}
              />
            </>
          )}
        />
      </p>

      {rewardsError !== null && <Refusal reason={rewardsError} />}
      {rewards === null && rewardsError === null && (
        <Waiting label="Loading rewards…" bars={['100%', '100%']} />
      )}

      {rewards !== null && (
        <div className="spend-area">
          {celebrated !== null && <Issued claim={celebrated} />}
          <ul className="catalogue">
            {rewards.map((reward) => (
              <Offer
                key={reward.code}
                reward={reward}
                pointsToSpend={pointsToSpend}
                claiming={claiming === reward.code}
                anyClaiming={claiming !== null}
                onClaim={() => claim(reward)}
              />
            ))}
          </ul>
        </div>
      )}

      {claimError !== null && <Refusal reason={claimError} />}
      {claimedError !== null && <Refusal reason={claimedError} />}
      {claimed !== null && (
        <Claimed claimed={claimed} landedId={celebrated === null ? null : celebrated.id} />
      )}
    </section>
  )
}

/**
 * A savings account as this page reads it: what it is worth, and every movement of money in and out
 * of it. All three arrive together, because the money balance only makes sense beside both lists.
 */
type SavingsAccountView = {
  balances: SavingsAccountBalances
  deposits: RecordedDeposit[]
  withdrawals: RecordedWithdrawal[]
}

/**
 * The thing that just happened on this account and is worth saying out loud for a moment. One at a
 * time, because a person did one thing: they paid money in, or they took some back out.
 *
 * <p>Claiming a reward is not one of them any more. Points belong to the customer, so a claim is
 * made on the overview and celebrated there.
 */
type Celebration =
  | { kind: 'deposit'; deposit: RecordedDeposit }
  | { kind: 'withdrawal'; withdrawal: RecordedWithdrawal }

/**
 * One savings account: what it holds, what its holder has to spend, the deposits and withdrawals
 * behind the money, and the forms that move it.
 *
 * Nothing on this page is computed here. Every figure is read back from the backend after a deposit,
 * so what is on screen is the derived answer rather than a guess this page kept in step by itself.
 *
 * <p>Rewards are not claimed from here. The points beside this balance are the customer's rather
 * than this account's, so there is one place to spend them and it is the overview.
 */
function SavingsAccountPage({
  savingsAccountId,
  currentAccounts,
  onChanged,
}: {
  savingsAccountId: number
  currentAccounts: CurrentAccount[]
  onChanged: () => void
}) {
  const [account, setAccount] = useState<SavingsAccountView | null>(null)
  const [accountError, setAccountError] = useState<string | null>(null)
  // What just happened, kept only long enough to say so. It is the backend's own answer to the
  // request, so the celebration cannot congratulate someone for points they did not get or a
  // voucher they were not issued.
  const [celebrated, setCelebrated] = useState<Celebration | null>(null)

  /**
   * The balances and both lists behind them, read back as one thing. Loading them separately would
   * let one of the three fail and leave a balance on screen beside lists that do not account for it
   * — which is the one thing showing the lists is meant to let someone check: the deposits say what
   * came in, the withdrawals say what went back out, and the money balance is what the two leave
   * behind.
   */
  const loadAccount = useCallback((signal?: AbortSignal) => {
    Promise.all([
      fetchSavingsAccount(savingsAccountId, signal),
      fetchDeposits(savingsAccountId, signal),
      fetchWithdrawals(savingsAccountId, signal),
    ])
      .then(([balances, deposits, withdrawals]) => {
        if (signal?.aborted !== true) {
          setAccount({ balances, deposits, withdrawals })
          setAccountError(null)
        }
      })
      .catch((problem: Error) => {
        if (signal?.aborted !== true) {
          setAccountError(problem.message)
        }
      })
  }, [savingsAccountId])

  useEffect(() => {
    const request = new AbortController()
    loadAccount(request.signal)
    return () => request.abort()
  }, [loadAccount])

  useEffect(() => {
    if (celebrated === null) {
      return
    }
    const over = setTimeout(() => setCelebrated(null), 2600)
    return () => clearTimeout(over)
  }, [celebrated])

  return (
    <section className="panel account">
      <header className="account-head">
        <span className="avatar pot" style={hueOf(savingsAccountId)} aria-hidden="true">
          <PotIcon />
        </span>
        <div>
          <h2>Savings account {savingsAccountId}</h2>
          <span className="choice-note">
            {account === null ? 'Reading the account…' : `Held by ${account.balances.customerName}`}
          </span>
        </div>
      </header>

      {accountError !== null && <Refusal reason={accountError} />}
      {account === null && accountError === null && (
        <div className="balances">
          {/* Each cell waits under the class it will fill: the week's, so the third one spans the row
              while it is loading exactly as it does once it has loaded — without it the row is three
              cells in a two-column grid and the square beside the third stays empty, which reads as a
              balance that failed — and the other two so that all three wear the coloured rule that
              says which figure is coming rather than only the one that needed a class for the grid. */}
          <div className="saved">
            <Waiting label="Loading balances…" bars={['4rem', '8rem']} />
          </div>
          <div className="earned">
            <Waiting label="" bars={['4rem', '8rem']} />
          </div>
          <div className="week">
            <Waiting label="" bars={['4rem', '8rem']} />
          </div>
        </div>
      )}
      {account !== null && (
        <dl className="balances">
          {/* Both figures move to what they now are: a deposit or a claim is a number changing, and
              seeing it change is what tells you it landed. The points figure goes down as readily as
              up now, which is the whole of what this slice added to it. */}
          <div className={moneyMoved(celebrated) ? 'saved bumped' : 'saved'}>
            <dt>Saved</dt>
            <dd>
              <Rising value={account.balances.moneyBalance} format={(shown) => euros.format(shown)} />
            </dd>
          </div>
          <div className={pointsMoved(celebrated) ? 'earned bumped' : 'earned'}>
            {/* The customer's points rather than this account's, which is why it is not "earned
                here": paying in here adds to it, claiming a reward takes from it, and it reads the
                same beside every account they hold. What paying in here earned is in the history
                below, against the deposit that earned it. */}
            <dt>Your points</dt>
            <dd>
              {/* The holder's deadline, beside the holder's balance — the same two figures as on
                  the overview, because the twelve months run against their points rather than
                  against this account's saving. Inside the rise, as the week's cell is: while the
                  balance is still climbing, a deadline taken from the settled figure would say more
                  points were going than the customer appears to have. */}
              <Rising
                value={account.balances.pointsBalance}
                format={(shown) => (
                  <>
                    {points.format(Math.round(shown))}
                    <span className="unit">points</span>
                    <ExpiringNext
                      expiring={account.balances.pointsExpiringNext}
                      on={account.balances.pointsExpiringNextOn}
                      outOf={Math.round(shown)}
                    />
                  </>
                )}
              />
            </dd>
          </div>
          {/* The week, beside the two totals rather than under them: "saved altogether" is history
              and "saved since Monday" is the thing there is still time to change. */}
          <div className={weekMoved(celebrated) ? 'week bumped' : 'week'}>
            <dt>This week</dt>
            <dd>
              {/* The figure, the bar and the sentence are one thing here rather than a figure with
                  two things said beside it: the whole cell is drawn from whatever figure the rise is
                  showing at this moment, so no frame of it can say the week is done while the figure
                  on the screen is still short of what the week asks for. */}
              <Rising
                value={account.balances.newSavingsThisWeek}
                format={(shown) => (
                  <ThisWeek shown={shown} weeklyMinimum={account.balances.weeklyMinimum} />
                )}
              />
              {/* The run of weeks, beside the week it is made of. Outside the rise on purpose:
                  neither figure is climbing anywhere, and the two of them are about weeks already
                  settled rather than about the amount still moving above. */}
              <Streak
                currentWeeks={account.balances.currentStreakWeeks}
                bestWeeks={account.balances.bestStreakWeeks}
                multiplier={account.balances.currentMultiplier}
              />
            </dd>
          </div>
        </dl>
      )}

      {/* Outside the check above on purpose: a read that failed is no reason to stop someone
          depositing, and the deposit is what will read the account again. */}
      <div className="transfer-area">
        {celebrated?.kind === 'deposit' && <Earned deposit={celebrated.deposit} />}
        <DepositForm
          currentAccounts={currentAccounts}
          savingsAccountId={savingsAccountId}
          onDeposited={(made) => {
            setCelebrated({ kind: 'deposit', deposit: made })
            loadAccount()
            // The money came out of a current account, and that figure is on the overview.
            onChanged()
          }}
        />
        <WithdrawalForm
          currentAccounts={currentAccounts}
          savingsAccountId={savingsAccountId}
          onWithdrawn={(made) => {
            setCelebrated({ kind: 'withdrawal', withdrawal: made })
            loadAccount()
            onChanged()
          }}
        />
      </div>

      {account !== null && (
        <div className="ledger">
          <Deposits
            deposits={account.deposits}
            landedId={celebrated?.kind === 'deposit' ? celebrated.deposit.id : null}
          />
          <Withdrawals
            withdrawals={account.withdrawals}
            currentAccounts={currentAccounts}
            landedId={celebrated?.kind === 'withdrawal' ? celebrated.withdrawal.id : null}
          />
        </div>
      )}
    </section>
  )
}

/** Deposits and withdrawals move money. Claiming a reward never touches euros. */
function moneyMoved(celebrated: Celebration | null): boolean {
  return celebrated?.kind === 'deposit' || celebrated?.kind === 'withdrawal'
}

/**
 * Whether the week's progress changed. Only a deposit adds to it: a withdrawal takes money back out
 * without un-happening the deposit it came from, so the week is where it was and flashing it would
 * say the money had been taken off the week as well as out of the account.
 */
function weekMoved(celebrated: Celebration | null): boolean {
  return celebrated?.kind === 'deposit'
}

/**
 * The whole of the week cell: what the week has taken in, what it asks for, a bar for the same thing
 * at a glance, and what is left to find said in words.
 *
 * <p>All four are drawn from one number — the figure on the screen at this moment, which is handed
 * in by {@link Rising} while it is still climbing towards what the account now holds. That is the
 * point of this taking the shown figure rather than the balances: three parts of one cell reading
 * off two different numbers is a cell that contradicts itself, and a bar drawn full under
 * "the week has what it asks for" beside a figure still passing € 21,00 tells a customer their week
 * is done while showing them that it is not.
 *
 * <p>The €50 is the backend's, so this page never names it and a repricing needs no change here.
 * What the week still asks for is the gap up to it — the same subtraction the backend publishes as
 * {@code stillNeededThisWeek}, taken here against the figure actually on the screen so that the
 * words are never about a figure the customer cannot see. Once the figure has arrived, the two are
 * the same amount.
 *
 * <p>The bar is hidden from a screen reader because the sentence under it says the same thing in
 * words, and hearing the same fact twice is worse than hearing it once.
 */
function ThisWeek({ shown, weeklyMinimum }: { shown: number; weeklyMinimum: number }) {
  const asksForNoMore = shown >= weeklyMinimum
  const howFarAlong =
    weeklyMinimum <= 0 ? 100 : Math.min(100, Math.max(0, (shown / weeklyMinimum) * 100))
  return (
    <>
      {euros.format(shown)}
      {/* A space, and it is load-bearing: without one the figure and "of € 50,00" are a single
          unbreakable run, and a narrow cell has nowhere to put the second half but outside itself,
          where it is hidden. With it the phrase drops to its own line. */}
      {' '}
      <span className="unit">of {euros.format(weeklyMinimum)}</span>
      <span className={asksForNoMore ? 'week-bar full' : 'week-bar'} aria-hidden="true">
        <i style={{ width: `${howFarAlong}%` }} />
      </span>
      <span className="week-note">
        {asksForNoMore
          ? 'the week has what it asks for'
          : `${euros.format(weeklyMinimum - shown)} more to go`}
      </span>
    </>
  )
}

/**
 * What a euro paid into this account earns right now, the run of consecutive weeks that rate comes
 * from, and the longest run the account has ever had — said beside the week they are all about.
 *
 * <p>Beside the week's progress rather than in a cell of its own, because they are one story: the
 * week above is the week this run is currently made of, the rate is what carrying it on is worth,
 * and what a customer is deciding is whether to pay in before Sunday. Every figure is the backend's
 * — how long a run is, whether it is still alive and what it pays are all decided there, so a rate
 * of 1,00 here is a lapse the page was told about rather than one it worked out.
 *
 * <p>The rate first, because it is the figure being acted on and the other two explain it. It is
 * shown even when there is no run at all: 1,00× is what a euro has always earned, and a customer who
 * cannot see the ordinary rate has nothing to read the better ones against.
 *
 * <p>Two week figures rather than one, so that a lapse leaves something behind. The current run is
 * what there is to lose and the best-ever run is what there is to beat, and an account that has never
 * secured a week has neither: it gets one sentence saying so, because "best ever 0 weeks" is a
 * record nobody set.
 */
function Streak({
  currentWeeks,
  bestWeeks,
  multiplier,
}: {
  currentWeeks: number
  bestWeeks: number
  multiplier: number
}) {
  return (
    <span className="streak">
      <span className="streak-rate">earning {rate.format(multiplier)}× per euro</span>
      {bestWeeks <= 0 ? (
        <span className="streak-best">no week secured yet</span>
      ) : (
        <>
          <span className="streak-now">
            {currentWeeks === 0 ? 'no weeks' : inWeeks(currentWeeks)} in a row
          </span>
          <span className="streak-best">best ever {inWeeks(bestWeeks)}</span>
        </>
      )}
    </span>
  )
}

/**
 * A number of weeks with its noun agreeing with it, which one week and five weeks do not share.
 *
 * <p>Written out rather than run through a formatter: a run of weeks is a small count the backend
 * sends as a whole number, and there is no thousands separator or decimal in it to get wrong.
 */
function inWeeks(weeks: number): string {
  return weeks === 1 ? '1 week' : `${weeks} weeks`
}

/**
 * Whether the points figure changed. A deposit under a euro earns none, so it did not — and flashing
 * a figure that stayed put would say something happened to it that did not. A withdrawal never
 * touches the points: what was earned stays earned.
 */
function pointsMoved(celebrated: Celebration | null): boolean {
  return celebrated?.kind === 'deposit' && celebrated.deposit.pointsEarned > 0
}

/**
 * The withdrawals behind the money balance, newest first, beside the deposits that make up the other
 * half of it. Together the two lists account for every movement on the account: the deposits say what
 * came in, these say what went back out, and the balance is what the two leave.
 *
 * <p>Each one names the account it returned to by its IBAN rather than by the identifier the backend
 * files it under, because a customer picked that IBAN out of the form's list and it is the only form
 * of the destination that can be checked against a bank statement. An identifier this page cannot put
 * a name to is still shown as one, so a withdrawal is never hidden by not knowing where it went.
 */
function Withdrawals({
  withdrawals,
  currentAccounts,
  landedId,
}: {
  withdrawals: RecordedWithdrawal[]
  currentAccounts: CurrentAccount[]
  landedId: number | null
}) {
  const ibans = new Map(currentAccounts.map((account) => [account.id, account.iban]))
  return (
    <div className="history">
      <h3>Withdrawals</h3>
      {withdrawals.length === 0 ? (
        <p className="nothing">No withdrawals yet.</p>
      ) : (
        <table className="deposits">
          <thead>
            <tr>
              <th scope="col">When</th>
              <th scope="col">Amount</th>
              <th scope="col">Returned to</th>
            </tr>
          </thead>
          <tbody>
            {withdrawals.map((made, place) => (
              <tr
                key={made.id}
                className={made.id === landedId ? 'landed' : undefined}
                style={{ '--row-delay': `${Math.min(place, 8) * 45}ms` } as CSSProperties}
              >
                <td className="when">{dateAndTime.format(new Date(made.withdrawnAt))}</td>
                <td className="amount">{euros.format(made.amount)}</td>
                {/* Named for the same reason the deposits' points cell is: the stylesheet folds
                    this row into a block at a phone's width and needs to say which cell goes
                    where without counting columns. */}
                <td className="returned">
                  {ibans.get(made.toCurrentAccountId) ?? `Current account ${made.toCurrentAccountId}`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

/**
 * The deposits behind the balances above, newest first, each with what it earned and when it next
 * pays. Together they are what makes the two figures checkable: the amounts add up to the one, the
 * points to the other.
 *
 * <p>The rule behind the loyalty part of a row is written once, over the list, rather than on every
 * row that shows it. A row is then free to be as short as a fact — "30 points due on 30 september
 * 2028" — and the sentence a customer needs in order to read it, including why a small deposit is
 * plainly worth nothing on its anniversary rather than mysteriously worth nothing, is stated where
 * it belongs to the whole list. Repeating it per row would turn a history into an advertisement,
 * which is exactly what a promise on this page must not become.
 */
function Deposits({
  deposits,
  landedId,
}: {
  deposits: RecordedDeposit[]
  landedId: number | null
}) {
  return (
    <div className="history">
      <h3>Deposits</h3>
      {deposits.length === 0 ? (
        <p className="nothing">No deposits yet.</p>
      ) : (
        <>
          <p className="explanation rule">
            A deposit earns again every year it stays: a tenth of the euros still in it, rounded
            down.
          </p>
          <table className="deposits">
            <thead>
              <tr>
                <th scope="col">When</th>
                <th scope="col">Amount</th>
                <th scope="col">Points earned</th>
              </tr>
            </thead>
            <tbody>
              {deposits.map((made, place) => (
                <tr
                  key={made.id}
                  className={made.id === landedId ? 'landed' : undefined}
                  // Rows arrive one after another rather than all at once, which reads as a list
                  // being filled in. Only the first handful are staggered; past that it is a wait.
                  style={{ '--row-delay': `${Math.min(place, 8) * 45}ms` } as CSSProperties}
                >
                  <td className="when">{dateAndTime.format(new Date(made.depositedAt))}</td>
                  <td className="amount">{euros.format(made.amount)}</td>
                  {/* Named so the stylesheet can lay the row out as a block at a phone's width
                      without counting columns, the way the money history's cells are. This one
                      holds three things now and is the reason the row has to fold at all. */}
                  <td className="gained">
                    <span className={made.pointsEarned > 0 ? 'earnings' : 'earnings none'}>
                      {made.pointsEarned > 0 && <SparkIcon />}
                      {points.format(made.pointsEarned)}
                    </span>
                    <WhatItEarned deposit={made} />
                    <NextAnniversary deposit={made} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  )
}

/**
 * The deadline on the points a customer is holding: how many go next, and the day they go.
 *
 * <p>Nothing at all where there is nothing to lose, which is why both figures arrive as nullable and
 * are checked together. "No points expire next" is true of somebody who has never earned anything;
 * "zero points expire on the 14th" is not true of anybody, and a cell showing a 0 beside a date would
 * be inventing a deadline out of an absence of one.
 *
 * <p>A day rather than a moment. Points reach their anniversary at whatever time of day they were
 * earned and the sweep that acts on it runs overnight, so an exact time would be precision the
 * customer cannot act on — and a figure they could catch the application out on.
 *
 * <p>The figures are the backend's and this works out none of them: not the anniversary, not which
 * batch is next, not how many days are left. Counting the days here would be a second place the
 * twelve months lived.
 *
 * <p>The one arithmetic it does is the clamp below, and it is the same bargain {@link ThisWeek}
 * strikes: the figure is held against the balance being shown beside it rather than against the
 * settled one, so that no frame of the balance's nine-hundred-millisecond climb says more points are
 * about to go than the customer appears to have. It lands on the backend's number the moment the
 * climb does, and the only figure it can ever show that the backend did not send is one on its way
 * there.
 */
function ExpiringNext({
  expiring,
  on,
  outOf,
}: {
  expiring: number | null
  on: string | null
  outOf: number
}) {
  if (expiring === null || on === null) {
    return null
  }
  // Never more than the balance being shown beside it. The balance climbs to its figure over
  // nine hundred milliseconds, and for as long as that lasts a deadline taken from the settled
  // figure would say more points were about to go than the customer appears to have — which reads
  // as a fault rather than as an animation. Held against the figure on the screen, exactly as the
  // week's progress bar is, and it lands on the backend's number the moment the climb does.
  const going = Math.min(expiring, outOf)
  return (
    <span className="expiring">
      <span className="expiring-count">
        {points.format(going)} {going === 1 ? 'point' : 'points'}
      </span>{' '}
      {/* The date travels with the word in front of it: a date alone at the start of a line reads
          as a heading rather than as the end of this sentence. */}
      <span className="expiring-when">expire on {asADay(on)}</span>
    </span>
  )
}

/**
 * Every euro this customer has moved into or out of savings, newest first, as one list.
 *
 * <p>The question a person asks before they ask anything else about their money, and until this page
 * the application could only answer it one account and one direction at a time. Somebody saving
 * towards two goals moved their money once; reading it back as four lists to interleave by eye is not
 * an answer.
 *
 * <p>Read here rather than handed down, because this is the only screen that wants it and it is read
 * fresh every time the screen is opened — which is what makes going back to it after a deposit show
 * the deposit.
 *
 * <p>Nothing is added up. No running total and no balance column: the same euro moving out of one pot
 * and into another would be counted twice by anybody following a column down, and a running balance
 * across several accounts is not a figure that means anything. What each account is worth is on the
 * account.
 *
 * <p>Each row names the everyday account at the other end by its IBAN rather than by the identifier
 * the backend files it under, the way a withdrawal already does on the account page: a customer
 * picked that IBAN out of a form and it is the only form of it that can be checked against a bank
 * statement. An identifier this page cannot put a name to is still shown as one, so a movement is
 * never hidden by not knowing where it went.
 */
function MoneyHistory({
  customerId,
  currentAccounts,
}: {
  customerId: number
  currentAccounts: CurrentAccount[]
}) {
  const [movements, setMovements] = useState<MoneyMovement[] | null>(null)
  const [problem, setProblem] = useState<string | null>(null)

  useEffect(() => {
    const request = new AbortController()
    fetchMoneyMovements(customerId, request.signal)
      .then((theirs) => {
        if (!request.signal.aborted) {
          setMovements(theirs)
          setProblem(null)
        }
      })
      .catch((refused: Error) => {
        if (!request.signal.aborted) {
          setProblem(refused.message)
        }
      })
    return () => request.abort()
  }, [customerId])

  const ibans = new Map(currentAccounts.map((account) => [account.id, account.iban]))

  return (
    <section className="panel">
      <h2>Money history</h2>
      <p className="explanation">
        Every movement in and out of your savings, newest first. Each row is one movement — nothing
        here is a total.
      </p>

      {problem !== null && <Refusal reason={problem} />}
      {movements === null && problem === null && (
        <Waiting label="Loading your money history…" bars={['100%', '100%', '70%']} />
      )}

      {movements !== null &&
        (movements.length === 0 ? (
          <p className="nothing">No money has moved in or out of your savings yet.</p>
        ) : (
          <div className="history">
            {/* The same table the account page's histories use, because it is the same kind of
                thing read at a different scope. Scrolls inside its own frame at a phone's width
                rather than pushing the page sideways. */}
            <div className="movements-frame">
              <table className="deposits movements">
                <thead>
                  <tr>
                    <th scope="col">When</th>
                    <th scope="col">Movement</th>
                    <th scope="col">Amount</th>
                    <th scope="col">Points earned</th>
                  </tr>
                </thead>
                <tbody>
                  {movements.map((moved, place) => (
                    <tr
                      // Deposits and withdrawals are numbered separately, so the identifier only
                      // means anything alongside the direction and the key is the pair.
                      key={`${moved.direction}-${moved.id}`}
                      // Rows arrive one after another rather than all at once, as they do in the
                      // account page's histories. Only the first handful; past that it is a wait.
                      style={{ '--row-delay': `${Math.min(place, 8) * 45}ms` } as CSSProperties}
                    >
                      <td className="when">{dateAndTime.format(new Date(moved.movedAt))}</td>
                      {/* Both cells are named so the stylesheet can lay the row out as a block at
                          a phone's width without counting columns. */}
                      <td className="moved">
                        <Movement moved={moved} ibans={ibans} />
                      </td>
                      <td className="amount">{euros.format(moved.amount)}</td>
                      <td className="gained">
                        {moved.direction === 'INTO_SAVINGS' ? (
                          <span className={moved.pointsEarned > 0 ? 'earnings' : 'earnings none'}>
                            {moved.pointsEarned > 0 && <SparkIcon />}
                            {points.format(moved.pointsEarned)}
                          </span>
                        ) : (
                          // Not a zero. A withdrawal has never earned a point here, and a 0 in the
                          // column would read as a deposit that happened to earn nothing.
                          <span className="earnings none" title="Withdrawals earn no points">
                            —
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        ))}
    </section>
  )
}

/**
 * Which way one movement went, and between which two accounts.
 *
 * <p>Named from the savings account's point of view, because that is the account this ledger is about
 * and the one whose balance the movement changed. The two accounts are written in the order the money
 * travelled, so the row reads as the sentence it is rather than as two labels a reader has to work out
 * the direction of for themselves.
 */
function Movement({
  moved,
  ibans,
}: {
  moved: MoneyMovement
  ibans: Map<number, string>
}) {
  const everyday = ibans.get(moved.currentAccountId) ?? `Current account ${moved.currentAccountId}`
  const savings = `Savings account ${moved.savingsAccountId}`
  const into = moved.direction === 'INTO_SAVINGS'
  return (
    <>
      <span className={into ? 'movement in' : 'movement out'}>
        {into ? 'Into savings' : 'Out of savings'}
      </span>
      <span className="between">
        {into ? everyday : savings}
        {' \u2192 '}
        {into ? savings : everyday}
      </span>
    </>
  )
}

/**
 * How a past deposit's points were arrived at: the euros, the uplift the run of weeks added, and the
 * rate it was paid at — under the total they add up to.
 *
 * <p>Under the figure rather than in columns of their own, because they are one statement about one
 * number and the third of a row this cell gets is not three cells wide. Read as a sentence it is the
 * arithmetic itself: seven base and two bonus at 1,30× is where the nine above it came from, which is
 * the whole reason for showing it — a customer who cannot check a total has to trust it.
 *
 * <p>All three, always, including a bonus of nothing and a rate of 1,00×. Dropping them when they are
 * unremarkable would make "this deposit earned no uplift" and "this deposit does not say" the same
 * row, and would leave the rows a customer most wants to compare — the one before the run started and
 * the one after — laid out differently from each other.
 *
 * <p>Every figure is the backend's, and the rate especially: it is what this deposit was paid when it
 * was made, not what the account earns today. A run that has since lapsed leaves the two disagreeing,
 * and a page that worked one out from the other would be rewriting history to match the present.
 */
function WhatItEarned({ deposit }: { deposit: RecordedDeposit }) {
  return (
    <span className="breakdown">
      {points.format(deposit.basePoints)} base + {points.format(deposit.streakBonusPoints)} bonus{' '}
      {/* The rate and its × stay on one line: a × alone at the start of a line reads as a figure
          with something snapped off it, the way the rate beside the week does. */}
      <span className="breakdown-rate">at {rate.format(deposit.multiplierApplied)}×</span>
      {/* Last, and only when there is one. It is the only part of this sum that arrived after the
          money did, so it reads as something added to a settled arithmetic rather than as a third
          thing the deposit was paid on the day — which is what it is. It also keeps the rate next
          to the bonus it is the rate of; "at 1,30×" after a loyalty figure would read as the rate
          of the loyalty, and the loyalty deliberately has no rate. */}
      {deposit.loyaltyBonusPoints > 0 && (
        <span className="breakdown-loyalty">
          {' + '}
          {points.format(deposit.loyaltyBonusPoints)} loyalty
        </span>
      )}
    </span>
  )
}

/**
 * When this deposit next pays a loyalty bonus, and what that day is worth at what it holds now.
 *
 * <p>The one thing on this page that is about the future. Everything else in a history row is a
 * record of something that happened; this is a promise, and it is here because it is the figure a
 * customer would decide against — what leaving the money alone pays them, which is the same number
 * as what taking it out would cost. Stated once and left alone: a row of a history is not the place
 * to argue for anything, so there is no "don't miss out" and no arrow pointing at it.
 *
 * <p>**"due", never "next".** The date the backend sends is the day this deposit next *pays*, and
 * that is not always the next date its calendar reaches — between an anniversary falling and the
 * overnight sweep paying it, the day reported is the one just gone. "Next anniversary: yesterday"
 * is a contradiction on a page; "50 points due on 9 september" is true whichever side of the date
 * today falls, so the word carries both readings and the page needs no opinion about which one it
 * is looking at. It has none to offer either: the clock this promise is kept by is the
 * application's, which a trainer winds forward by years, and the browser's would say the deposit
 * pays in 2029 while the application was already three anniversaries behind on it. Nothing here
 * calls `new Date()` for today, and that is deliberate rather than an omission.
 *
 * <p>Three states arrive here and three different things are said, because they say different
 * things:
 *
 * - both null, and only ever together: the deposit has been emptied. Nothing is drawn at all. There
 *   is no anniversary left for money that has gone to reach, and a row saying "nothing due" would
 *   be making a promise about a deposit that has none to make.
 * - a date, worth nothing: the deposit still holds money, but under ten euros of it, and a tenth of
 *   nine euros rounds down. The date is real, so it is shown, with the figure named as nothing
 *   rather than drawn as a 0 beside a date — and the rule it follows from is stated over the list,
 *   so a customer reading this row can see why it says what it says rather than suspecting the
 *   application of a fault.
 * - a date and a figure: the promise itself.
 *
 * <p>Neither figure is worked out here, and the date least of all. Twelve months, a tenth, and the
 * rounding all live in the backend, which is the only place they can be checked against what it
 * actually paid.
 */
function NextAnniversary({ deposit }: { deposit: RecordedDeposit }) {
  const on = deposit.nextAnniversaryOn
  const worth = deposit.nextAnniversaryPoints
  // Together, always, and the check says so rather than trusting one of them: the pair is the
  // backend's way of saying "this deposit is empty", and half of it would be a promise with no date
  // or a date with no promise.
  if (on === null || worth === null) {
    return null
  }
  if (worth === 0) {
    return (
      <span className="anniversary none">
        {/* The date travels with the words in front of it, the way the expiry deadline's does: a
            date alone at the start of a wrapped line reads as a heading rather than as the end of
            this sentence. */}
        <span className="anniversary-when">Nothing due on {asADay(on)}</span>
      </span>
    )
  }
  return (
    <span className="anniversary">
      <span className="anniversary-count">
        {points.format(worth)} {worth === 1 ? 'point' : 'points'}
      </span>{' '}
      <span className="anniversary-when">due on {asADay(on)}</span>
    </span>
  )
}

/**
 * One reward, and whether the customer can have it yet.
 *
 * Whether they can afford it is worked out here from two figures already on screen, and that is all
 * it decides: the button goes grey and says how many points are still missing. The rule itself is the
 * backend's — it refuses a claim it cannot honour whatever this page allowed to be pressed — which is
 * why a page that gets the sum wrong is a page that looks wrong rather than one that spends points
 * nobody had.
 */
function Offer({
  reward,
  pointsToSpend,
  claiming,
  anyClaiming,
  onClaim,
}: {
  reward: Reward
  pointsToSpend: number
  claiming: boolean
  anyClaiming: boolean
  onClaim: () => void
}) {
  const short = reward.costInPoints - pointsToSpend
  const affordable = short <= 0

  return (
    <li className={affordable ? 'offer' : 'offer out-of-reach'}>
      <span className="offer-icon" aria-hidden="true">
        <RewardIcon code={reward.code} />
      </span>
      <div className="offer-words">
        <h4>{reward.title}</h4>
        <p>{reward.description}</p>
      </div>
      <p className="price">
        <SparkIcon />
        {points.format(reward.costInPoints)}
      </p>
      <button type="button" onClick={onClaim} disabled={!affordable || anyClaiming}>
        {claiming ? (
          <>
            <span className="spinner" />
            Claiming…
          </>
        ) : affordable ? (
          'Claim'
        ) : (
          `${points.format(short)} to go`
        )}
      </button>
    </li>
  )
}

/** The voucher, the moment it exists. It is the thing the points were spent on. */
function Issued({ claim }: { claim: ClaimedReward }) {
  return (
    <>
      <p className="reward voucher-flash">
        <TicketIcon />
        {claim.title} — {claim.voucherCode}
      </p>
      <Confetti />
    </>
  )
}

/**
 * What the customer has spent their points on, newest first, each with the voucher it produced. Drawn
 * as stubs rather than as rows because that is what they are: the voucher is the thing the customer
 * got, and it is the part they will be reading back off the screen.
 *
 * <p>One list for the person, not one per account: the points came out of a single pot, so which
 * account had earned them is not a question a claim can answer.
 */
function Claimed({ claimed, landedId }: { claimed: ClaimedReward[]; landedId: number | null }) {
  return (
    <div className="history">
      <h3>Claimed</h3>
      {claimed.length === 0 ? (
        <p className="nothing">Nothing claimed yet.</p>
      ) : (
        <ul className="vouchers">
          {claimed.map((one, place) => (
            <li
              key={one.id}
              className={one.id === landedId ? 'voucher landed' : 'voucher'}
              style={{ '--row-delay': `${Math.min(place, 8) * 45}ms` } as CSSProperties}
            >
              <span className="voucher-icon" aria-hidden="true">
                <RewardIcon code={one.code} />
              </span>
              <div className="voucher-words">
                <strong>{one.title}</strong>
                <span className="when">{dateAndTime.format(new Date(one.claimedAt))}</span>
              </div>
              <code className="voucher-code">{one.voucherCode}</code>
              <span className="earnings spent">−{points.format(one.pointsSpent)}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * A picture for a reward, chosen by its code. Presentation and nothing else — the words beside it are
 * the backend's — and a code this page has never heard of still gets something to look at, so a
 * reward added server-side is not an empty square.
 */
function RewardIcon({ code }: { code: string }) {
  switch (code) {
    case 'CINEMA_TICKET':
      return <TicketIcon />
    case 'FAMILY_CINEMA_PACK':
      return <PopcornIcon />
    case 'SNACK_VOUCHER':
      return <CupIcon />
    case 'CHARITY_DONATION':
      return <HeartIcon />
    default:
      return <GiftIcon />
  }
}

/** Moves an amount out of one of the customer's current accounts and into this savings account. */
function DepositForm({
  savingsAccountId,
  currentAccounts,
  onDeposited,
}: {
  savingsAccountId: number
  currentAccounts: CurrentAccount[]
  onDeposited: (made: RecordedDeposit) => void
}) {
  const [amount, setAmount] = useState('')
  const [fromCurrentAccountId, setFromCurrentAccountId] = useState<number | null>(
    currentAccounts[0]?.id ?? null,
  )
  const [depositing, setDepositing] = useState(false)
  const [depositError, setDepositError] = useState<string | null>(null)

  if (fromCurrentAccountId === null) {
    return <p className="nothing">A deposit needs a current account to come from.</p>
  }

  function deposit(event: FormEvent) {
    event.preventDefault()
    if (fromCurrentAccountId === null) {
      return
    }
    setDepositing(true)
    setDepositError(null)
    // The amount is cleared only once a deposit has actually been made. A refusal leaves what was
    // typed where it is, because the next thing the person does is correct it, and it is displayed
    // as the backend worded it: this form decides nothing about what counts as an amount of money,
    // which is why it can be submitted at all.
    makeDeposit(savingsAccountId, amount, fromCurrentAccountId)
      .then((made) => {
        setAmount('')
        onDeposited(made)
      })
      .catch((problem: Error) => setDepositError(problem.message))
      .finally(() => setDepositing(false))
  }

  return (
    <form className="deposit" onSubmit={deposit}>
      <div className="field">
        <label htmlFor="amount">Deposit</label>
        <div className="amount-box">
          <input
            id="amount"
            name="amount"
            inputMode="decimal"
            placeholder="25.00"
            autoComplete="off"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
          />
        </div>
      </div>
      <div className="field from">
        <label htmlFor="fromCurrentAccount">From</label>
        <select
          id="fromCurrentAccount"
          value={fromCurrentAccountId}
          onChange={(event) => setFromCurrentAccountId(Number(event.target.value))}
        >
          {currentAccounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.iban}
            </option>
          ))}
        </select>
      </div>
      <button type="submit" disabled={depositing}>
        {depositing ? (
          <>
            <span className="spinner" />
            Depositing…
          </>
        ) : (
          'Deposit'
        )}
      </button>
      {depositError !== null && <Refusal reason={depositError} />}
    </form>
  )
}

/** Moves money the other way, beside the deposit form that moves it into savings. */
function WithdrawalForm({
  savingsAccountId,
  currentAccounts,
  onWithdrawn,
}: {
  savingsAccountId: number
  currentAccounts: CurrentAccount[]
  onWithdrawn: (made: RecordedWithdrawal) => void
}) {
  const [amount, setAmount] = useState('')
  const [toCurrentAccountId, setToCurrentAccountId] = useState<number | null>(currentAccounts[0]?.id ?? null)
  const [withdrawing, setWithdrawing] = useState(false)
  const [withdrawalError, setWithdrawalError] = useState<string | null>(null)

  if (toCurrentAccountId === null) {
    return <p className="nothing">A withdrawal needs a current account to return to.</p>
  }

  function withdraw(event: FormEvent) {
    event.preventDefault()
    if (toCurrentAccountId === null) {
      return
    }
    setWithdrawing(true)
    setWithdrawalError(null)
    makeWithdrawal(savingsAccountId, amount, toCurrentAccountId)
      .then((made) => {
        setAmount('')
        onWithdrawn(made)
      })
      .catch((problem: Error) => setWithdrawalError(problem.message))
      .finally(() => setWithdrawing(false))
  }

  return (
    <form className="deposit withdrawal" onSubmit={withdraw}>
      <div className="field">
        <label htmlFor="withdrawalAmount">Withdraw</label>
        <div className="amount-box">
          <input
            id="withdrawalAmount"
            name="withdrawalAmount"
            inputMode="decimal"
            placeholder="25.00"
            autoComplete="off"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
          />
        </div>
      </div>
      <div className="field from">
        <label htmlFor="toCurrentAccount">Return to</label>
        <select
          id="toCurrentAccount"
          value={toCurrentAccountId}
          onChange={(event) => setToCurrentAccountId(Number(event.target.value))}
        >
          {currentAccounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.iban}
            </option>
          ))}
        </select>
      </div>
      <button type="submit" disabled={withdrawing}>
        {withdrawing ? <><span className="spinner" />Withdrawing…</> : 'Withdraw'}
      </button>
      {withdrawalError !== null && <Refusal reason={withdrawalError} />}
    </form>
  )
}

/**
 * What the deposit just earned, for a moment, above the form that earned it. It says the backend's
 * figure back: a deposit under a euro earns nothing and is told so, which is the whole rule made
 * visible.
 */
function Earned({ deposit }: { deposit: RecordedDeposit }) {
  const earned = deposit.pointsEarned > 0
  return (
    <>
      <p className="reward">
        {earned && <SparkIcon />}
        {earned
          ? `+${points.format(deposit.pointsEarned)} points`
          : `${euros.format(deposit.amount)} saved, no whole euro yet`}
      </p>
      {earned && <Confetti />}
    </>
  )
}

/** Paper thrown in the air, and nothing more. Twelve pieces is enough to read as a celebration. */
function Confetti() {
  const colours = ['var(--points)', 'var(--brand)', 'var(--brand-deep)', 'var(--points-warm)']
  return (
    <div className="confetti" aria-hidden="true">
      {Array.from({ length: 12 }, (_, piece) => {
        const angle = (piece / 12) * Math.PI * 2
        const reach = 4.5 + (piece % 3)
        return (
          <i
            key={piece}
            style={
              {
                '--piece': colours[piece % colours.length],
                '--dx': `${(Math.cos(angle) * reach).toFixed(2)}rem`,
                '--dy': `${(Math.sin(angle) * reach - 1.5).toFixed(2)}rem`,
                '--spin': `${piece % 2 === 0 ? 1 : -1}turn`,
                '--delay': `${piece * 18}ms`,
              } as CSSProperties
            }
          />
        )
      })}
    </div>
  )
}

/**
 * A figure on its way to a new value. The value itself is never touched — only how much of the way
 * there has been drawn — and someone who has asked for less motion is simply shown the new figure.
 *
 * <p>What is drawn from the figure part-way there is the caller's, and it is a whole piece of the
 * screen rather than a string: anything said beside a figure that is still moving has to be said
 * about the figure being shown, not about the one it is heading for. A bar and a sentence drawn from
 * the destination while the figure climbs towards it contradict the figure for as long as the climb
 * lasts — see {@link ThisWeek}, which is drawn entirely from what it is handed here.
 */
function Rising({ value, format }: { value: number; format: (shown: number) => ReactNode }) {
  const [shown, setShown] = useState(stillness ? value : 0)
  const from = useRef(stillness ? value : 0)

  useEffect(() => {
    if (stillness || from.current === value) {
      from.current = value
      setShown(value)
      return
    }
    const started = performance.now()
    const startedAt = from.current
    let frame = 0
    const draw = (now: number) => {
      // Never below nothing of the way there: the moment a frame carries is the moment the browser
      // began it, which can be a hair earlier than the moment this effect read, and a negative
      // fraction of the way draws a figure below the one being left — € -0,04 on a sign-in, and a
      // week that has taken in less than nothing.
      const through = Math.max(0, Math.min(1, (now - started) / 900))
      const eased = 1 - Math.pow(1 - through, 3)
      const reached = startedAt + (value - startedAt) * eased
      from.current = reached
      setShown(reached)
      if (through < 1) {
        frame = requestAnimationFrame(draw)
      }
    }
    frame = requestAnimationFrame(draw)
    return () => cancelAnimationFrame(frame)
  }, [value])

  return <>{format(shown)}</>
}

/** A refusal, worded by whoever refused it. Nothing here rewords or shortens what it says. */
function Refusal({ reason }: { reason: string }) {
  return (
    <p role="alert">
      <WarningIcon />
      <span>{reason}</span>
    </p>
  )
}

/** The shape of the answer while it is on its way, rather than a sentence about waiting for it. */
function Waiting({ label, bars }: { label: string; bars: string[] }) {
  return (
    <div className="skeleton" aria-busy="true">
      {label !== '' && <span className="sr-only">{label}</span>}
      {bars.map((width, bar) => (
        <i key={bar} style={{ '--w': width } as CSSProperties} />
      ))}
    </div>
  )
}

/** Three washes of colour behind the page. Decoration, and marked as such for anything reading it. */
function Backdrop() {
  return (
    <div className="backdrop" aria-hidden="true">
      <i />
      <i />
      <i />
    </div>
  )
}

/** Initials for the avatar beside a name, so two customers are told apart before either is read. */
function initialsOf(name: string): string {
  return name
    .split(/\s+/)
    .filter((part) => part !== '')
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}

/** An IBAN in fours, the way it is printed on a card, without changing what it is. */
function spacedIban(iban: string): string {
  return iban.replace(/(.{4})/g, '$1 ').trim()
}

/**
 * A colour per savings account, off the account's own identifier so it never moves.
 *
 * <p>Picked from the blues the rest of the page is drawn in rather than from anywhere on the wheel.
 * Two accounts still have to be told apart at a glance, but a page that puts a pink and a green
 * beside a bank's own two colours is no longer that bank's page.
 */
const ACCOUNT_HUES = [199, 213, 187, 206, 193]

function hueOf(savingsAccountId: number): CSSProperties {
  return { '--hue': ACCOUNT_HUES[savingsAccountId % ACCOUNT_HUES.length] } as CSSProperties
}

function StreakIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M13 2 5.5 12.5h5L9 22l8.5-11.5h-5.2L13 2Z"
        fill="currentColor"
        stroke="currentColor"
        strokeWidth="1.2"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function PotIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M4 11a7 7 0 0 1 7-7h3a6 6 0 0 1 6 6v1h1v3h-1.4A7 7 0 0 1 14 18h-1v2h-3v-2a7 7 0 0 1-6-7Z"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <circle cx="15.5" cy="10.5" r="1.1" fill="currentColor" />
      <path d="M8 6.5 10.5 4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  )
}

function BackIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M14.5 5.5 8 12l6.5 6.5"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function ForwardIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M9.5 5.5 16 12l-6.5 6.5"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function BankIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" className="row-icon">
      <path
        d="M3 10 12 4l9 6M5 10v9h14v-9M9 19v-5h6v5"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function SparkIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M12 3l2.2 5.4L20 10.5l-5.8 2.1L12 18l-2.2-5.4L4 10.5l5.8-2.1L12 3Z"
        fill="currentColor"
      />
    </svg>
  )
}

function TicketIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M3 9.5V7a1 1 0 0 1 1-1h16a1 1 0 0 1 1 1v2.5a2.5 2.5 0 0 0 0 5V17a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-2.5a2.5 2.5 0 0 0 0-5Z"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <path d="M13 7v10" stroke="currentColor" strokeWidth="1.6" strokeDasharray="2 2.5" />
    </svg>
  )
}

function PopcornIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M6 10h12l-1.2 10H7.2L6 10Z"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <path d="M10 10v10M14 10v10" stroke="currentColor" strokeWidth="1.2" />
      <path
        d="M7.5 10a2 2 0 0 1 1.2-3.4A2.2 2.2 0 0 1 12 4.4a2.2 2.2 0 0 1 3.3 2.2A2 2 0 0 1 16.5 10"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function CupIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M5 8h11v6a5 5 0 0 1-5 5H10a5 5 0 0 1-5-5V8Z"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <path d="M16 10h1.8a2.2 2.2 0 0 1 0 4.4H16" stroke="currentColor" strokeWidth="1.6" />
      <path d="M8.5 5.5c0-1 1-1.2 1-2.2M12 5.5c0-1 1-1.2 1-2.2" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  )
}

function HeartIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M12 20s-7.5-4.3-7.5-9.4A4.1 4.1 0 0 1 12 8a4.1 4.1 0 0 1 7.5 2.6C19.5 15.7 12 20 12 20Z"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function GiftIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M4 9h16v3H4V9Zm1 3h14v8H5v-8Zm7-3v11"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <path
        d="M12 9S10.5 4.5 8.5 4.5a2 2 0 0 0 0 4.5M12 9s1.5-4.5 3.5-4.5a2 2 0 0 1 0 4.5"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function WarningIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.6" />
      <path d="M12 7.5v6M12 16.5h.01" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}
