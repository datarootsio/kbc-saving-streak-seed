import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type FormEvent,
} from 'react'
import {
  claimReward,
  fetchAccounts,
  fetchClaimed,
  fetchCustomers,
  fetchDeposits,
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
            <p className="explanation">
              No password: this is a training application, and signing in only says whose accounts to
              show. Fill in one of these to try it.
            </p>
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
 * Everything behind the sign-in screen: what this customer holds, and whichever savings account they
 * have opened.
 *
 * <p>The accounts are read here rather than on the page that shows them, because the page that
 * changes them is the other one. A deposit or a claim tells this component to read them again, so
 * going back to the overview finds the figures that were just changed rather than the ones that
 * were there when it was last drawn.
 */
function Banking({ customer, onSignOut }: { customer: Customer; onSignOut: () => void }) {
  const [accounts, setAccounts] = useState<CustomerAccounts | null>(null)
  const [accountsError, setAccountsError] = useState<string | null>(null)
  // The catalogue belongs to no account and no customer, so it is read once here and handed to both
  // the page that browses it and the page that spends against it.
  const [rewards, setRewards] = useState<Reward[] | null>(null)
  const [rewardsError, setRewardsError] = useState<string | null>(null)
  const [opened, setOpened] = useState<number | null>(null)

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

  useEffect(() => {
    const request = new AbortController()
    loadAccounts(request.signal)
    return () => request.abort()
  }, [loadAccounts])

  useEffect(() => {
    fetchRewards()
      .then(setRewards)
      .catch((problem: Error) => setRewardsError(problem.message))
  }, [])

  const openedAccount = accounts?.savingsAccounts.find((account) => account.id === opened) ?? null

  return (
    <>
      <TopBar
        customer={customer}
        onSignOut={onSignOut}
        onBack={opened === null ? null : () => setOpened(null)}
      />

      <div className="shell">
        {opened === null ? (
          <Home
            accounts={accounts}
            accountsError={accountsError}
            rewards={rewards}
            rewardsError={rewardsError}
            onOpen={setOpened}
          />
        ) : (
          <main>
            <SavingsAccountPage
              // Remounting on a change of account is what keeps a half-typed amount, an error and a
              // stale balance from following the customer to a different account.
              key={opened}
              savingsAccountId={opened}
              currentAccounts={accounts?.currentAccounts ?? []}
              rewards={rewards}
              rewardsError={rewardsError}
              // Both balances on the overview are behind whatever just happened here.
              onChanged={loadAccounts}
            />
          </main>
        )}

        {/* Held by the account it belongs to, so it is still on screen while one is open. */}
        {opened !== null && openedAccount === null && accountsError !== null && (
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
 * What the customer holds and what they could spend it on: every account with what is in it, and the
 * catalogue underneath.
 *
 * <p>Nothing here is added up. Two savings balances are two balances, and a total across them would
 * be a figure this page worked out for itself — which is the one thing no figure on any of these
 * screens is.
 */
function Home({
  accounts,
  accountsError,
  rewards,
  rewardsError,
  onOpen,
}: {
  accounts: CustomerAccounts | null
  accountsError: string | null
  rewards: Reward[] | null
  rewardsError: string | null
  onOpen: (savingsAccountId: number) => void
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
            <p className="explanation">
              Everyday money. A deposit takes what it moves out of one of these, and is refused if it
              is not there.
            </p>
            {accounts.currentAccounts.length === 0 ? (
              <p className="nothing">You hold no current account.</p>
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
            <p className="explanation">
              What you have put away, and the points it earned. Open one to pay into it or to spend
              what it has earned.
            </p>
            {accounts.savingsAccounts.length === 0 ? (
              <p className="nothing">You hold no savings account.</p>
            ) : (
              <ul className="cards">
                {accounts.savingsAccounts.map((account) => (
                  <SavingsAccountCard key={account.id} account={account} onOpen={onOpen} />
                ))}
              </ul>
            )}
          </section>

          <Catalogue rewards={rewards} rewardsError={rewardsError} />
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
 * A savings account: what it holds, what that has earned, and the way into it.
 *
 * <p>Both figures are shown, because they are the two things this application is about and a
 * customer choosing which account to open is choosing between them.
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
          <span className="card-points">
            <SparkIcon />
            {points.format(account.pointsBalance)} points to spend
          </span>
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
 * The catalogue, to look at.
 *
 * <p>Nothing is claimed from here, and the reason is on the page: points belong to one savings
 * account at a time, so there is no such thing as what this customer can afford — only what each of
 * their accounts can. A button here would have to pick one of them on their behalf.
 */
function Catalogue({
  rewards,
  rewardsError,
}: {
  rewards: Reward[] | null
  rewardsError: string | null
}) {
  return (
    <section className="panel">
      <h2>Rewards</h2>
      <p className="explanation">
        What points buy, at the same price for everybody. Points are earned and spent by one savings
        account at a time — open the account that saved for it to claim one.
      </p>

      {rewardsError !== null && <Refusal reason={rewardsError} />}
      {rewards === null && rewardsError === null && (
        <Waiting label="Loading the rewards catalogue…" bars={['100%', '100%']} />
      )}

      {rewards !== null && (
        <ul className="catalogue browsing">
          {rewards.map((reward) => (
            <li className="offer" key={reward.code}>
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
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/**
 * A savings account as this page reads it: what it is worth, every deposit that put points in, and
 * every reward that took some out. All three arrive together, because the balance only makes sense
 * beside both lists.
 */
type SavingsAccountView = {
  balances: SavingsAccountBalances
  deposits: RecordedDeposit[]
  withdrawals: RecordedWithdrawal[]
  claimed: ClaimedReward[]
}

/**
 * The thing that just happened and is worth saying out loud for a moment. One at a time, because a
 * person did one thing: they made a deposit, or they claimed a reward.
 */
type Celebration =
  | { kind: 'deposit'; deposit: RecordedDeposit }
  | { kind: 'withdrawal'; withdrawal: RecordedWithdrawal }
  | { kind: 'claim'; claim: ClaimedReward }

/**
 * One savings account: what it holds, what that has earned, the deposits behind both, and the form
 * that adds to them.
 *
 * Nothing on this page is computed here. Every figure is read back from the backend after a deposit,
 * so what is on screen is the derived answer rather than a guess this page kept in step by itself.
 */
function SavingsAccountPage({
  savingsAccountId,
  currentAccounts,
  rewards,
  rewardsError,
  onChanged,
}: {
  savingsAccountId: number
  currentAccounts: CurrentAccount[]
  rewards: Reward[] | null
  rewardsError: string | null
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
   * — which is the one thing showing the lists is meant to let someone check. It takes both of them
   * now: the deposits say what was earned, the claims say what was spent, and the points balance is
   * what the two leave behind.
   */
  const loadAccount = useCallback((signal?: AbortSignal) => {
    Promise.all([
      fetchSavingsAccount(savingsAccountId, signal),
      fetchDeposits(savingsAccountId, signal),
      fetchWithdrawals(savingsAccountId, signal),
      fetchClaimed(savingsAccountId, signal),
    ])
      .then(([balances, deposits, withdrawals, claimed]) => {
        if (signal?.aborted !== true) {
          setAccount({ balances, deposits, withdrawals, claimed })
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
          <div>
            <Waiting label="Loading balances…" bars={['4rem', '8rem']} />
          </div>
          <div>
            <Waiting label="" bars={['4rem', '8rem']} />
          </div>
          {/* The week's own class, so the third cell spans the row while it is loading exactly as it
              does once it has loaded. Without it the row is three cells in a two-column grid and the
              square beside the third one stays empty, which reads as a balance that failed. */}
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
            {/* Not "earned" any more: points can leave, so what this says is what is left to spend. */}
            <dt>To spend</dt>
            <dd>
              <Rising
                value={account.balances.pointsBalance}
                format={(shown) => points.format(Math.round(shown))}
              />
              <span className="unit">points</span>
            </dd>
          </div>
          {/* The week, beside the two totals rather than under them: "saved altogether" is history
              and "saved since Monday" is the thing there is still time to change. */}
          <div className={weekMoved(celebrated) ? 'week bumped' : 'week'}>
            <dt>This week</dt>
            <dd>
              <Rising
                value={account.balances.newSavingsThisWeek}
                format={(shown) => euros.format(shown)}
              />
              {/* A space, and it is load-bearing: without one the figure and "of € 50,00" are a
                  single unbreakable run, and a narrow cell has nowhere to put the second half but
                  outside itself, where it is hidden. With it the phrase drops to its own line. */}
              {' '}
              <ThisWeek balances={account.balances} />
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

      <Spend
        savingsAccountId={savingsAccountId}
        rewards={rewards}
        rewardsError={rewardsError}
        // What the account can afford is the backend's figure, read back after every claim. While it
        // is still loading, nothing is offered as affordable rather than everything.
        pointsToSpend={account?.balances.pointsBalance ?? 0}
        claimed={celebrated?.kind === 'claim' ? celebrated.claim : null}
        onClaimed={(claim) => {
          setCelebrated({ kind: 'claim', claim })
          loadAccount()
          onChanged()
        }}
      />

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
          <Claimed
            claimed={account.claimed}
            landedId={celebrated?.kind === 'claim' ? celebrated.claim.id : null}
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
 * What the week the account is part-way through still asks for: the amount, a bar for the same thing
 * at a glance, and what is left to find said in words.
 *
 * <p>Every figure is the backend's, including the €50 — it comes down with the account, so this page
 * never names it and a repricing needs no change here. The bar's width is the only arithmetic on the
 * screen, and it is a length rather than a figure: the amounts either side of it are the answer, and
 * a bar is how far along it looks.
 *
 * <p>The bar is hidden from a screen reader because the sentence under it says the same thing in
 * words, and hearing the same fact twice is worse than hearing it once.
 */
function ThisWeek({ balances }: { balances: SavingsAccountBalances }) {
  const asksForNoMore = balances.stillNeededThisWeek <= 0
  const howFarAlong =
    balances.weeklyMinimum <= 0
      ? 100
      : Math.min(100, Math.max(0, (balances.newSavingsThisWeek / balances.weeklyMinimum) * 100))
  return (
    <>
      <span className="unit">of {euros.format(balances.weeklyMinimum)}</span>
      <span className={asksForNoMore ? 'week-bar full' : 'week-bar'} aria-hidden="true">
        <i style={{ width: `${howFarAlong}%` }} />
      </span>
      <span className="week-note">
        {asksForNoMore
          ? 'the week has what it asks for'
          : `${euros.format(balances.stillNeededThisWeek)} more to go`}
      </span>
    </>
  )
}

/**
 * Whether the points figure changed. A deposit under a euro earns none, so it did not — and
 * flashing a figure that stayed put would say something happened to it that did not.
 */
function pointsMoved(celebrated: Celebration | null): boolean {
  if (celebrated === null) {
    return false
  }
  return celebrated.kind === 'claim' || (celebrated.kind === 'deposit' && celebrated.deposit.pointsEarned > 0)
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
        <p className="nothing">Nothing has been withdrawn from this account yet.</p>
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
                <td>
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
 * The deposits behind the balances above, newest first, each with what it earned. Together they are
 * what makes the two figures checkable: the amounts add up to the one, the points to the other.
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
        <p className="nothing">Nothing has been paid into this account yet.</p>
      ) : (
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
                <td>
                  <span className={made.pointsEarned > 0 ? 'earnings' : 'earnings none'}>
                    {made.pointsEarned > 0 && <SparkIcon />}
                    {points.format(made.pointsEarned)}
                  </span>
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
 * The catalogue, and the claiming of something out of it.
 *
 * <p>What each reward is, what it costs and what to call it all come from the backend, so a reward
 * added or repriced there appears here with no change: the only thing this page decides is which
 * picture to put beside a code it recognises, and there is one for a code it does not.
 */
function Spend({
  savingsAccountId,
  rewards,
  rewardsError,
  pointsToSpend,
  claimed,
  onClaimed,
}: {
  savingsAccountId: number
  rewards: Reward[] | null
  rewardsError: string | null
  pointsToSpend: number
  claimed: ClaimedReward | null
  onClaimed: (claim: ClaimedReward) => void
}) {
  // Which reward is being claimed rather than whether one is, so that the button that was pressed is
  // the one that shows it is working and the others simply stop being pressable.
  const [claiming, setClaiming] = useState<string | null>(null)
  const [claimError, setClaimError] = useState<string | null>(null)

  function claim(reward: Reward) {
    setClaiming(reward.code)
    setClaimError(null)
    claimReward(savingsAccountId, reward.code)
      .then(onClaimed)
      .catch((problem: Error) => setClaimError(problem.message))
      .finally(() => setClaiming(null))
  }

  return (
    <div className="spend">
      <h3>Spend your points</h3>
      <p className="explanation">
        Every reward costs the same for everybody. There is no way back from a claim — the voucher is
        issued the moment you make it.
      </p>

      {rewardsError !== null && <Refusal reason={rewardsError} />}
      {rewards === null && rewardsError === null && (
        <Waiting label="Loading the rewards catalogue…" bars={['100%', '100%']} />
      )}

      {rewards !== null && (
        <div className="spend-area">
          {claimed !== null && <Issued claim={claimed} />}
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
    </div>
  )
}

/**
 * One reward, and whether this account can have it yet.
 *
 * Whether it can afford it is worked out here from two figures already on screen, and that is all it
 * decides: the button goes grey and says how many points are still missing. The rule itself is the
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
 * What this account has spent its points on, newest first, each with the voucher it produced. Drawn
 * as stubs rather than as rows because that is what they are: the voucher is the thing the customer
 * got, and it is the part they will be reading back off the screen.
 */
function Claimed({ claimed, landedId }: { claimed: ClaimedReward[]; landedId: number | null }) {
  return (
    <div className="history">
      <h3>Claimed</h3>
      {claimed.length === 0 ? (
        <p className="nothing">Nothing has been claimed out of this account yet.</p>
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
    return (
      <p className="nothing">
        A deposit needs a current account to come from, and this customer holds none.
      </p>
    )
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
    return <p className="nothing">A withdrawal needs a current account to return money to.</p>
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
 */
function Rising({ value, format }: { value: number; format: (shown: number) => string }) {
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
      const through = Math.min(1, (now - started) / 900)
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
