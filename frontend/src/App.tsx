import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type ComponentPropsWithoutRef,
  type CSSProperties,
  type FormEvent,
  type PointerEvent,
  type ReactElement,
  type ReactNode,
} from 'react'
import {
  abandonGoal,
  acceptSuggestedReallocation,
  addGoal,
  changeGoal,
  changeSavingRule,
  claimReward,
  takeAHold,
  convertAHold,
  giveUpAHold,
  joinTheQueue,
  leaveTheQueue,
  declareABudget,
  declareACategory,
  declareMonthlyIncome,
  declareSavingCapacity,
  dryRunAChangeToARule,
  dryRunSavingRule,
  changeBill,
  declareABill,
  endBill,
  endCategory,
  endSavingRule,
  renameCategory,
  fetchBillCategories,
  fetchCategories,
  fetchEndedCategories,
  fetchSpends,
  fetchSpendingHistory,
  fetchThisMonthsSpending,
  recordASpend,
  correctTheSplitOf,
  stopBudgeting,
  fetchAccounts,
  fetchAchievements,
  fetchCampaigns,
  fetchChallenges,
  enrolInChallenge,
  leaveChallenge,
  fetchAbandonedGoals,
  fetchAllocations,
  fetchClaimed,
  fetchCurrentAccount,
  fetchCustomers,
  fetchEndedBills,
  fetchBillHistory,
  fetchDeposits,
  fetchInterestPaid,
  fetchTheTermOn,
  breakAFixedTerm,
  takeTheNewerTerms,
  type TheNewerTerms,
  type InterestPaid,
  type InterestMovement,
  type TheTermOnAnAccount,
  type AnEarlyExitChargeMovement,
  addCustomer,
  fetchEndedSavingRules,
  fetchGifts,
  fetchMoneyMovements,
  fetchNotifications,
  fetchRuleHistory,
  fetchRulesPreview,
  fetchSavingRules,
  fetchSavingCapacity,
  fetchSuggestedReallocation,
  fetchWithdrawals,
  fetchRewardsFor,
  fetchSavingsAccount,
  giveGift,
  leaveARuleStanding,
  makeDeposit,
  makeWithdrawal,
  markNotificationsRead,
  moveAllocation,
  pauseSavingRule,
  pinWeeklyAmount,
  putBillInACategory,
  takeBillOutOfEveryCategory,
  reorderGoals,
  resumeSavingRule,
  signIn,
  SignInFailed,
  unpinWeeklyAmount,
  withdrawMonthlyIncome,
  type AChangeToARule,
  type ARuleAsTyped,
  type AllocationDirection,
  type AllocationsOnAnAccount,
  type DayOfWeek,
  type GoalStatus,
  type MonthlyIncome,
  type OccurrenceOutcome,
  type RuleOccurrence,
  type RuleState,
  type SavingRule,
  type SavingRuleDryRun,
  type SavingRulePreview,
  type RuleForecast,
  type BillForecast,
  type SavingRuleShare,
  type SavingCapacity,
  type SavingsGoal,
  type SuggestedMove,
  type SuggestedReallocation,
  type BillOccurrence,
  type BillOutcome,
  type BillInACategory,
  type RecurringBill,
  type SpendingCategory,
  type CategorySpending,
  type CategoryCompared,
  type MonthOfSpending,
  type SpendingHistory,
  type RolloverRule,
  theRolloverRuleCalled,
  theRolloverRules,
  type RecordedSpend,
  type SpendPartToRecord,
  type Arrear,
  type MonthAhead,
  type TheCurrentAccount,
  type Achievement,
  type Campaign,
  type Challenge,
  type ChallengeKind,
  type ChallengeRung,
  type EnrolmentState,
  type Rung,
  type ClaimedReward,
  type CurrentAccount,
  type Customer,
  type CustomerAccounts,
  type Gift,
  type MoneyMovement,
  type BillMovement,
  type SpendMovement,
  type Notification,
  type NotificationReason,
  type RecordedDeposit,
  type RecordedWithdrawal,
  type RewardForACustomer,
  type WhyAnOfferIsLocked,
  type SavingsAccount,
  type SavingsAccountBalances,
  type TheAgreement,
  type AccountTimeline,
  type TimelineEvent,
  fetchTimeline,
  fetchWeeksAhead,
  type TheWeeksAhead,
  type WeekAhead,
  askAboutTheseFutures,
  adoptThisFuture,
  TheSimulationWasRefused,
  type WhatAdoptingChanged,
  type AKindOfAdjustment,
  type AnAdjustmentAsAsked,
  type AScenarioToAskAbout,
  type AThingThatHappens,
  type HowAScenarioTurnsOut,
  type TheFutures,
  fetchEveryOffer,
  fetchTheWaitingList,
  writeAnOffer,
  changeAnOffer,
  publishAnOffer,
  withdrawAnOffer,
  type AChangeToAnOffer,
  type AdministeredOffer,
  type APlaceInAQueue,
  type BundleMember,
  type OfferState,
  lookUpVoucher,
  markVoucherUsed,
  cancelVoucher,
  type VoucherAtTheCounter,
  fetchSavingsProducts,
  openASavingsAccount,
  closeASavingsAccount,
  fetchTheVersionsOf,
  publishANewVersion,
  closeAProductToNewAccounts,
  reopenAProductToNewAccounts,
  type ANewVersionOfTerms,
  type SavingsProduct,
  type TermsVersion,
  fetchTheNoticeOn,
  giveNoticeOn,
  cancelANotice,
  type TheNoticeOnAnAccount,
  fetchWhatAYearWouldPay,
  type WhatAYearInAProductWouldPay,
  fetchWhatMovingWouldCost,
  moveMoneyBetweenSavingsAccounts,
  type AMoveBetweenSavingsAccountsMovement,
  type WhatMovingWouldCost,
  fetchWhatCanLeaveToday,
  type WhatCanLeaveToday,
  fetchTheSchemeInForce,
  fetchEveryVersionOfTheScheme,
  previewAVersionOfTheScheme,
  publishAVersionOfTheScheme,
  type ACandidateVersionOfTheScheme,
  type ALineTheSchemeDraws,
  type TheSchemeAsPublished,
  type WhatThisSchemeWouldDo,
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
 * whether a figure counts up to its new value or simply arrives at it, and whether a press throws a
 * ripple. A balance must never be animated for someone who has said they do not want that.
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

  if (customer === null) {
    // The remembered address fills the field when signing back in did not work. Whoever it belongs
    // to is looking at a sign-in screen they did not expect, and the least this can do is not make
    // them type it again.
    return <SignIn stillTrying={returning} knownAddress={remembered() ?? ''} onSignedIn={signedIn} />
  }
  return <Banking customer={customer} onSignOut={signOut} />
}

/**
 * The way in: an address, and the backend saying who that is.
 *
 * <p>There is no password field because there is nothing that would check one, and the screen says
 * so rather than leaving somebody to wonder where it went. What this establishes is who the page is
 * showing, not who is allowed to see it — every request after it still names the account it is
 * about, and nothing anywhere asks whether this browser was ever told about that account.
 *
 * <p>The one screen with nothing behind it, so it is the one screen that stands in the sky rather
 * than under it: the gradient the rest of the application wears as a band across the top is the
 * whole page here, with a single white card in the middle of it.
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

  return (
    <div className="gate">
      {stillTrying ? (
        <div className="card signin">
          <Waiting label="Signing you back in…" bars={['60%', '100%', '45%']} />
        </div>
      ) : (
        <form className="card signin" onSubmit={submit}>
          {/* The chip and nothing else: on this screen there is nowhere for it to go, so it is a
              mark rather than a control and the stylesheet gives the hand cursor and the lift only
              to the one in the bar, which is a real button. */}
          <span className="brand">
            <span className="brand__mark">KBC</span>
          </span>
          <h1>Saving Streak</h1>
          <p className="signin__lead">
            Move money into savings, earn a point for every whole euro.
          </p>

          <div className="field">
            <label htmlFor="contactDetails">Email address</label>
            <input
              className="text-input"
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

          <Button type="submit" block busy={signingIn} disabled={signingIn}>
            {signingIn ? 'Signing in…' : 'Log in'}
          </Button>

          {refusal !== null && <Refusal reason={refusal} />}

          {demonstrating !== null && demonstrating.length > 0 && (
            <div className="demo">
              <p>No password — pick one of these to try it.</p>
              <ul className="people">
                {demonstrating.map((who) => (
                  <li key={who.id}>
                    <button
                      type="button"
                      className="person"
                      onClick={() => {
                        setAddress(who.contactDetails)
                        setRefusal(null)
                      }}
                    >
                      <span className="avatar" aria-hidden="true">
                        {initialsOf(who.name)}
                      </span>
                      <span className="person__name">
                        {who.name}
                        <span className="person__note">{who.contactDetails}</span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </form>
      )}

      {/* Under the card and centred on it rather than off in a corner of the sky. It is the only
          picture on the screen and it belongs to the one thing on it; at arm's length it read as
          something left behind by another page. */}
      <div className="gate__art" aria-hidden="true">
        <CoinStack />
      </div>
    </div>
  )
}

/**
 * The five screens the tab strip moves between.
 *
 * <p>There is still no "Transfer" here: money moves into and out of one savings account at a time,
 * so the forms that move it live on that account's own page, behind the card that names the account
 * they would move money into. A tab that asked "transfer to which of your pots?" before showing a
 * form would be a picker with a form behind it, which is exactly what the account cards already are.
 *
 * <p>Challenges earns one because it is a place rather than a drill-down: it belongs to the
 * customer rather than to any account they hold, it is somewhere to start rather than somewhere
 * arrived at while looking at something else, and what is on it — a season, a ladder, a trophy case
 * — is the only thing in this application with a horizon longer than a week.
 */
type TabName = 'home' | 'rewards' | 'challenges' | 'gifts' | 'history'

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
  | { at: TabName }
  | { at: 'current-account'; currentAccountId: number }
  | { at: 'budget'; currentAccountId: number }
  | { at: 'savings-account'; savingsAccountId: number }
  | { at: 'automatic-saving'; savingsAccountId: number }
  | { at: 'simulator'; savingsAccountId: number }
  // Not a drill-down from anything: it is reached from the footer, because running the catalogue
  // is not something a customer does and a sixth tab would put it in their way.
  | { at: 'administration' }
  // The other back office, beside the rewards one and reached the same way. A screen of its own
  // rather than a section of that one: repricing a savings product and repricing a reward are two
  // different jobs done by two different people on two different mornings, and a single
  // administration page holding both would make the one somebody wanted the harder half to find.
  | { at: 'savings-products-administration' }
  // The third back office, and the argument for it being its own screen is the one the second made
  // about the first, one size up. Repricing a savings product changes what one shelf-item offers to
  // whoever opens it next; repricing the scheme changes what every euro every customer has ever put
  // away is worth, on a date, for everybody at once. Those are different jobs done by different
  // people with different things to be afraid of, and a single administration page holding all
  // three would make the one somebody wanted the hardest third to find. It is emphatically not a
  // section of the products screen either: the loyalty figures a product carries and the ladder the
  // scheme publishes are the two halves of what this bank pays, and the reason this screen shows
  // the first half read-only is precisely so that neither figure acquires a second place it can be
  // changed from.
  | { at: 'scheme-administration' }
  // The counter. Not a tab and not a drill-down either: it belongs to somebody standing at a till
  // rather than to the customer whose accounts the rest of this union is about, which is why it is
  // reached from a link in the footer and carries no identifier at all. A voucher is addressed by
  // the code printed on it, because the code is the only thing the person at the till has.
  | { at: 'counter' }
  // The comparison. A customer's screen rather than a back office one, and a drill-down from the
  // chooser on the overview rather than a sixth tab: choosing a product is the first decision a
  // saver makes and then hardly ever makes again, so it belongs one press behind the panel that
  // raises the question rather than in the strip of five places they come back to every day.
  | { at: 'savings-products' }

/**
 * Which tab is lit for a screen. An open account is the overview's drill-down rather than a screen
 * of its own, so the strip keeps pointing at where the customer came from — and pressing that tab
 * is the way back, alongside the link the page carries.
 *
 * <p>The budget is one step further down the same path: it is opened from a current account, it is
 * keyed on that account, and it lights Overview for the same reason the account itself does. A tab
 * of its own would make it a fifth place to start rather than something a customer arrives at while
 * looking at the money it is about.
 *
 * <p>The simulator is the same argument again, one pot along: it is opened from a savings account,
 * it is keyed on that account, and it is a question about that pot rather than a fifth place to
 * begin. A tab of its own would invite somebody to start there, with no account chosen and nothing
 * to branch from.
 *
 * <p>The administration screen is the odd one and lights Overview for a different reason. It hangs
 * off nothing: it is reached from a link in the footer, because running the catalogue is not
 * something the customer whose name is in the bar does, and a sixth tab would offer it to them
 * every time they looked at the strip. Something has to be lit, Overview is where pressing a tab
 * from here goes, and `aria-current` stays off it — which is exactly the distinction the strip
 * already draws between the tab it lights and the page it claims you are on.
 */
function tabOf(screen: Screen): TabName {
  if (
    screen.at === 'savings-account' ||
    screen.at === 'automatic-saving' ||
    screen.at === 'simulator' ||
    screen.at === 'current-account' ||
    screen.at === 'budget' ||
    screen.at === 'administration' ||
    screen.at === 'savings-products-administration' ||
    screen.at === 'scheme-administration' ||
    // The comparison is the overview's drill-down, one step along from the chooser it is opened
    // from, so the strip keeps pointing at where the customer came from — and pressing that tab is
    // the way back, alongside the link the page carries.
    screen.at === 'savings-products' ||
    // The counter is nobody's tab, and the strip has no fifth place to light for it. Overview is
    // where its own way back goes, which is exactly what the lit tab means everywhere else on
    // this list — a breadcrumb, drawn. `aria-current` is still not set, because the attribute is
    // read off the screen itself and announcing "Overview, current page" to somebody at a till
    // would be false.
    screen.at === 'counter'
  ) {
    return 'home'
  }
  return screen.at
}

/** The strip itself, in reading order: what you have, what it buys, what you are working towards,
 *  who you can give it to, and what has happened. */
const TABS: { at: TabName; label: string; icon: ReactNode }[] = [
  { at: 'home', label: 'Overview', icon: <HomeIcon /> },
  { at: 'rewards', label: 'Rewards', icon: <GiftIcon /> },
  { at: 'challenges', label: 'Challenges', icon: <TrophyIcon /> },
  { at: 'gifts', label: 'Gifts', icon: <GivingIcon /> },
  { at: 'history', label: 'History', icon: <ClockIcon /> },
]

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
  const [rewards, setRewards] = useState<RewardForACustomer[] | null>(null)
  const [rewardsError, setRewardsError] = useState<string | null>(null)
  // What this customer has claimed, read here beside their accounts because it belongs to the same
  // person: their points are one pot, so what has come out of it is one list rather than one per
  // account.
  const [claimed, setClaimed] = useState<ClaimedReward[] | null>(null)
  const [claimedError, setClaimedError] = useState<string | null>(null)
  // Everything the rules have said to this customer, read and unread together. Held here rather
  // than in the bell that shows it, because it is read on every screen and because the savings
  // account page carries the notice that concerns it: one list, loaded once, shown twice.
  const [notifications, setNotifications] = useState<Notification[] | null>(null)
  const [notificationsError, setNotificationsError] = useState<string | null>(null)
  // Which screen is showing. A union rather than a set of nullable fields, so that "an account is
  // open and so is the history" is not a state this component can get into.
  const [screen, setScreen] = useState<Screen>({ at: 'home' })

  // A new screen starts at the top of itself, which a router would have done for nothing and
  // which nothing here was doing at all. Screens are state rather than URLs, so changing one
  // re-renders the document underneath a scroll position that belongs to the screen that has just
  // gone: the browser keeps the offset and there is no navigation for it to reset on. Nobody
  // noticed while every way into a screen was a tab or a drill-down near the top of the page —
  // land at an offset of forty pixels and you are still looking at the heading. The two screens
  // this catalogue added are reached from links in the *footer*, which is the one place on a long
  // page where the offset is large: pressing "Run the catalogue" at the bottom of the overview
  // arrived at scrollY 831, with the administration screen's own title 264 pixels above the fold,
  // the "nothing here checks that you are staff" warning off-screen with it, and the voucher-prefix
  // box of a form nobody had asked for sitting under the tab strip. That reads as a broken page
  // rather than as a screen, and the first thing a trainer would do in front of a room is scroll
  // up looking for what they had just pressed.
  //
  // Keyed on the whole screen rather than on `screen.at`, so that opening a second savings account
  // from a list goes to the top of the second one as well: `{at: 'savings-account', id: 1}` and
  // `{at: 'savings-account', id: 2}` are different screens and the identifier is the only thing
  // that says so. The object is fresh on every `setScreen`, so this fires once per press and never
  // on a re-render caused by anything else.
  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
  }, [screen])

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

  const loadNotifications = useCallback((signal?: AbortSignal) => {
    fetchNotifications(customer.id, signal)
      .then((said) => {
        if (signal?.aborted !== true) {
          setNotifications(said)
          setNotificationsError(null)
        }
      })
      .catch((problem: Error) => {
        if (signal?.aborted !== true) {
          setNotificationsError(problem.message)
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
    loadNotifications(request.signal)
    return () => request.abort()
  }, [loadNotifications])

  /**
   * Reads them again when the page is looked at again, and that is the whole of the refreshing this
   * feature does. There is no interval anywhere in this application and this does not add the
   * first: nothing is asked for while nobody is looking, and coming back to the tab asks once.
   *
   * <p>It is here for the trainer. Every other thing that raises a notification is something done
   * on this page — a deposit, a withdrawal, a claim, a gift — and each of those already says so
   * through the callbacks below. A wound-forward clock and a job run are not: they are done against
   * the API from a terminal, and without this the bell would still be showing last night's count
   * when the trainer turned back to the browser to demonstrate what the sweep had just raised.
   */
  useEffect(() => {
    const lookedAtAgain = () => {
      if (document.visibilityState === 'visible') {
        loadNotifications()
      }
    }
    document.addEventListener('visibilitychange', lookedAtAgain)
    window.addEventListener('focus', lookedAtAgain)
    return () => {
      document.removeEventListener('visibilitychange', lookedAtAgain)
      window.removeEventListener('focus', lookedAtAgain)
    }
  }, [loadNotifications])

  useEffect(() => {
    const request = new AbortController()
    loadClaimed(request.signal)
    return () => request.abort()
  }, [loadClaimed])

  /**
   * The catalogue, read on arrival and again whenever somebody running it has changed something.
   *
   * <p>It used to be read once and never again, which was true of a catalogue that was four
   * constants in a release. Now that an offer can be published or withdrawn from the
   * administration screen, the Rewards tab would otherwise go on showing whatever the catalogue
   * held when the page was opened — and in a training session, the person who just published
   * something is the person about to go and look at it.
   *
   * <p>Read on the customer's behalf rather than as the plain catalogue, because a card now says
   * whether <em>they</em> can claim the thing and why not — and because an offer that opens
   * tomorrow becomes claimable by nothing more than the day changing. A trainer who winds the
   * clock and comes back to this tab is exactly the person that matters to, and the read they
   * trigger by publishing something is the one that picks it up.
   */
  const loadRewards = useCallback(() => {
    fetchRewardsFor(customer.id)
      .then((catalogue) => {
        setRewards(catalogue)
        setRewardsError(null)
      })
      .catch((problem: Error) => setRewardsError(problem.message))
  }, [customer.id])

  useEffect(() => {
    loadRewards()
  }, [loadRewards])

  // Read again after anything that moves money or points: the balance in the bar, the figures on
  // the overview, and the rung a deposit has just passed are all the backend's answers rather than
  // this page's arithmetic.
  const readAgain = () => {
    loadAccounts()
    loadNotifications()
  }

  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>

      <TopBar
        customer={customer}
        onSignOut={onSignOut}
        screen={screen}
        onChoose={(at) => setScreen({ at })}
        pointsBalance={accounts?.pointsBalance ?? null}
        notifications={notifications}
        notificationsError={notificationsError}
        // Opening the panel is the customer having looked, so everything in it is marked read in
        // one call — and that call answers with the list as it now stands, which is what the
        // panel then shows. One round trip: the count drops and the rows stay, dimmed.
        onOpened={() =>
          markNotificationsRead(customer.id)
            .then((said) => {
              setNotifications(said)
              setNotificationsError(null)
            })
            .catch((problem: Error) => setNotificationsError(problem.message))
        }
      />

      {/* The sky band is inside the landmark rather than above it, because it is this screen's own
          headline rather than furniture that repeats: it carries the document's only <h1>, and it
          says something different on every screen. What the skip link exists to jump past is the
          bar and the tab strip above, which are chrome; this is where the page starts.

          tabIndex={-1} is what makes the skip link actually skip. Without it Safari does not even
          move the sequential-focus starting point, so pressing the link and then Tab lands back
          inside the chrome the link was there to get past. */}
      <main id="main" tabIndex={-1}>
        <ScreenHero
          screen={screen}
          customer={customer}
          accounts={accounts}
          onOpen={(savingsAccountId) => setScreen({ at: 'savings-account', savingsAccountId })}
        />

        <div className="wrap">
        {screen.at === 'home' && (
          <Overview
            customerId={customer.id}
            accounts={accounts}
            accountsError={accountsError}
            onOpen={(savingsAccountId) => setScreen({ at: 'savings-account', savingsAccountId })}
            onOpenCurrentAccount={(currentAccountId) =>
              setScreen({ at: 'current-account', currentAccountId })
            }
            onOpenHistory={() => setScreen({ at: 'history' })}
            onOpenChallenges={() => setScreen({ at: 'challenges' })}
            onCompareProducts={() => setScreen({ at: 'savings-products' })}
            // Read the accounts again, and then go straight into the one that was just opened:
            // somebody who chose a product wants to see the agreement they have just signed, and
            // the list they came from is one press behind them.
            onOpened={(savingsAccountId) => {
              readAgain()
              setScreen({ at: 'savings-account', savingsAccountId })
            }}
          />
        )}

        {screen.at === 'current-account' && (
          <CurrentAccountPage
            // Remounting on a change of account is what keeps a half-typed income and a stale
            // balance from following the customer to a different account.
            key={screen.currentAccountId}
            currentAccountId={screen.currentAccountId}
            // Whose accounts these are, which is what this page checks its own identifier against.
            // Null until the read lands, so that "not arrived yet" is never mistaken for "not
            // yours" — the two are different answers and only one of them is a refusal.
            currentAccounts={accounts?.currentAccounts ?? null}
            accountsError={accountsError}
            onBack={() => setScreen({ at: 'home' })}
            onOpenBudget={() => setScreen({ at: 'budget', currentAccountId: screen.currentAccountId })}
          />
        )}

        {screen.at === 'budget' && (
          <BudgetPage
            // Remounting on a change of account is what keeps a half-typed category and a stale
            // list from following the customer to a different account.
            key={screen.currentAccountId}
            currentAccountId={screen.currentAccountId}
            // Whose accounts these are, which is what this page checks its own identifier against,
            // exactly as the account's own page does. Null until the read lands, so that "not
            // arrived yet" is never mistaken for "not yours".
            currentAccounts={accounts?.currentAccounts ?? null}
            accountsError={accountsError}
            onBack={() =>
              setScreen({ at: 'current-account', currentAccountId: screen.currentAccountId })
            }
          />
        )}

        {screen.at === 'rewards' && (
          <Rewards
            customerId={customer.id}
            accounts={accounts}
            accountsError={accountsError}
            rewards={rewards}
            rewardsError={rewardsError}
            claimed={claimed}
            claimedError={claimedError}
            onClaimed={() => {
              // The points that paid for it are in the bar and on the overview, and the voucher
              // belongs in the list beside the catalogue, so both are read again.
              readAgain()
              loadClaimed()
              // And the catalogue itself, which is the one that was missing. Everything a card
              // now says about itself is derived per customer and per read — what is left of a
              // scarce offer, how many more of it they may have, whether it has sold out, whether
              // they are holding one and where they stand in a queue — so a claim, a hold, a
              // conversion, a hold given up, a queue joined and a queue left all change the answer
              // and none of them were asking for it again. The whole page except the cards
              // refreshed, which is the worst of the three possible behaviours: pressing "Join the
              // queue" left the card reading "Sold out · Join the queue" exactly as before, with
              // no line saying where in the line the customer now stood, and pressing "Hold it"
              // left no hold on the screen at all. `actOnAHold` says of taking a hold and giving
              // one up that "the card redrawing itself is the whole of the feedback those two
              // need" — this is the line that makes that sentence true.
              loadRewards()
            }}
          />
        )}

        {screen.at === 'challenges' && (
          <Challenges
            customerId={customer.id}
            // A rung pays into the same pot a reward is claimed from, and taking a challenge on
            // runs the judging pass — so joining one can itself be the moment points land. The
            // pill in the bar and the figures on the overview are read again for the same reason a
            // claim reads them again.
            onChanged={readAgain}
          />
        )}

        {screen.at === 'gifts' && (
          <GiftPage
            customerId={customer.id}
            // The balance the page greys its button against, and the figure that has to fall the
            // moment a gift goes through. It is this component's read of the accounts rather than
            // a figure the gift page keeps for itself, which is what makes the fall real: the
            // gift asks for the accounts again below, and the new figure arrives as a prop.
            pointsToSpend={accounts?.pointsBalance ?? null}
            // A read that failed leaves this screen with no balance to grey its button against and
            // no way to say why, so the refusal travels with the figure it stands in for. Both of
            // these screens became reachable from the tab strip before the accounts have arrived,
            // which is a state neither of them could get into when the only way in was through a
            // loaded overview.
            accountsError={accountsError}
            // The points that paid for the gift are the overview's headline figure, so the
            // accounts are read again — the same thing a claim does with the points it spent.
            onGiven={readAgain}
          />
        )}

        {screen.at === 'history' && (
          <MoneyHistory
            customerId={customer.id}
            currentAccounts={accounts?.currentAccounts ?? []}
            accountsError={accountsError}
          />
        )}

        {screen.at === 'automatic-saving' && (
          <AutomaticSaving
            // Remounting on a change of account is what keeps a half-typed rule and a stale list
            // from following the customer to a different account.
            key={screen.savingsAccountId}
            savingsAccountId={screen.savingsAccountId}
            currentAccounts={accounts?.currentAccounts ?? []}
            // Without it a failed customer-level read leaves this page with an empty list of
            // current accounts, and both forms then state that the customer holds none — a
            // sentence this page wrote, and a false one, in place of the backend's own.
            accountsError={accountsError}
            onBack={() =>
              setScreen({ at: 'savings-account', savingsAccountId: screen.savingsAccountId })
            }
            // The income moved to the current account's own page, and this is the way through to
            // it: a payday rule takes its day from a declaration that is now made somewhere else.
            onOpenCurrentAccount={(currentAccountId) =>
              setScreen({ at: 'current-account', currentAccountId })
            }
          />
        )}

        {screen.at === 'simulator' && (
          <WhatIfIDidThisInstead
            // Remounting on a change of account is what keeps half-built scenarios from following
            // the customer to a different pot — the same bargain every other drill-down strikes,
            // and the one that matters most here, because these scenarios exist nowhere else and
            // a column headed "Five hundred out" drawn against the wrong account is a wrong answer
            // rather than a stale one.
            key={screen.savingsAccountId}
            savingsAccountId={screen.savingsAccountId}
            // Asking writes nothing, but adopting does — it raises a declared weekly capacity and
            // moves a goal's deadline — and both of those are figures the overview above this
            // screen is drawn from. Without this, a customer who adopts a branch and presses back
            // lands on a savings account page still showing the plan they have just replaced.
            onChanged={readAgain}
            onBack={() =>
              setScreen({ at: 'savings-account', savingsAccountId: screen.savingsAccountId })
            }
          />
        )}

        {screen.at === 'counter' && <Counter onBack={() => setScreen({ at: 'home' })} />}

        {screen.at === 'savings-account' && (
          <SavingsAccountPage
            // Remounting on a change of account is what keeps a half-typed amount, an error and a
            // stale balance from following the customer to a different account.
            key={screen.savingsAccountId}
            savingsAccountId={screen.savingsAccountId}
            currentAccounts={accounts?.currentAccounts ?? []}
            savingsAccounts={accounts?.savingsAccounts ?? []}
            // Every figure on the overview is behind whatever just happened here: the money in
            // both accounts, and the points the deposit earned. The notifications too: a rung
            // this deposit has just passed, or a bonus the withdrawal has just exposed, is
            // raised by the overnight sweep against balances this has just moved.
            onChanged={readAgain}
            // The whole list rather than the one notice, because which of them concerns this
            // account is the page's own question and it is the page that knows which account it
            // is. Read once in this component and shown twice — in the bell above, and here.
            notifications={notifications ?? []}
            // Without it a failed customer-level read leaves this page with an empty list of
            // current accounts, and both forms then state that the customer holds none — a
            // sentence this page wrote, and a false one, in place of the backend's own.
            accountsError={accountsError}
            onBack={() => setScreen({ at: 'home' })}
            onOpenAutomaticSaving={() =>
              setScreen({ at: 'automatic-saving', savingsAccountId: screen.savingsAccountId })
            }
            // The other forward-looking way out of this page, beside the rules: automation is what
            // this pot is already going to do, and the simulator is what it would do instead.
            onOpenSimulator={() =>
              setScreen({ at: 'simulator', savingsAccountId: screen.savingsAccountId })
            }
          />
        )}

        {screen.at === 'administration' && (
          <RunningTheCatalogue
            onBack={() => setScreen({ at: 'home' })}
            // Publishing or withdrawing changes what the Rewards tab shows, and that list is held
            // up here rather than on the page that shows it. Without this, somebody who published
            // an offer and went straight to Rewards would be looking at the catalogue as it was
            // when they signed in.
            onCatalogueChanged={() => {
              loadRewards()
              // And the two lists that a cancellation moves, which the catalogue read alone does
              // not cover. Cancelling a voucher refunds the points as a fresh batch and retires
              // the voucher, and both of those are the signed-in customer's own figures held up
              // here: the first time anybody watched this happen in a browser, the card's "only
              // one left" came back correctly while the points pill went on reading 60 against a
              // backend that said 120, and the cancelled voucher still sat in "Your vouchers"
              // with no line saying what had become of it. A screen showing three numbers, one of
              // which has quietly stopped being true, is worse than a screen that shows two.
              //
              // Read on every catalogue change rather than only on a cancellation, because the
              // administration screen is one screen and "which of the things you just did moved a
              // balance" is a question this end has no business answering. Publishing an offer
              // then costs two reads that find nothing changed, which is a great deal cheaper
              // than a wrong figure in front of a room.
              readAgain()
              loadClaimed()
            }}
          />
        )}

        {screen.at === 'savings-products' && (
          <SavingsProductsScreen
            customerId={customer.id}
            onBack={() => setScreen({ at: 'home' })}
            // The same two steps opening an account from the overview's chooser takes: read the
            // accounts again, and go straight into the one that was just opened. Somebody who has
            // just chosen a product wants to see the agreement they signed rather than the list
            // they chose it from.
            onOpened={(savingsAccountId) => {
              readAgain()
              setScreen({ at: 'savings-account', savingsAccountId })
            }}
          />
        )}

        {screen.at === 'savings-products-administration' && (
          <RunningTheSavingsProducts onBack={() => setScreen({ at: 'home' })} />
        )}

        {screen.at === 'scheme-administration' && (
          <RunningTheScheme
            onBack={() => setScreen({ at: 'home' })}
            // The loyalty table at the foot of that screen is read-only and says where the figures
            // on it are changed. Sending somebody there is the whole of what that link does, and
            // the scheme screen is left exactly as it was: coming back is the same footer link
            // that got them here, which is what every other door in this footer already does.
            onOpenTheProducts={() => setScreen({ at: 'savings-products-administration' })}
            // Publishing a version moves figures the customer's own screens quote — what a week
            // asks for, what a run pays, how long a batch of points lasts. Nothing on this page is
            // one of the signed-in customer's figures, so their accounts and their notifications
            // are read again here rather than left until something else happens to ask: a trainer
            // who publishes a scheme and goes straight to Overview should find the ladder they
            // just published, not the one that was in force when they signed in. It is the same
            // two reads the catalogue screen already triggers, for the same reason.
            onSchemeChanged={readAgain}
          />
        )}
        </div>
      </main>

      <footer className="foot">
        <p>
          Saving Streak · a training application · every figure on these screens is the backend's,
          and none of it is real money
          {/* A footer link rather than a tab, because running the catalogue is not something the
              customer whose name is in the bar ever does. It is where a bank puts the door marked
              staff: findable by somebody who knows it is there, and not offered to everybody else
              five times a screen. It is not hidden either — nothing here is protected, and a link
              that pretended otherwise would be security theatre in a training application.

              It wears `link link--small`, which is what the counter link beside it wears and what
              every other small link in this file wears. It arrived with a `foot__link` of its
              own, written in the same week and in ignorance of the other, and the two sat side by
              side in one sentence looking like two different kinds of thing — one permanently
              underlined, one underlined on hover — when they are one row of doors to the two
              screens that are not a customer's. There is nothing a footer link needs that `.link`
              does not already do. */}
          {' · '}
          <button
            type="button"
            className="link link--small"
            onClick={() => setScreen({ at: 'administration' })}
          >
            Run the catalogue
          </button>
          {/* Beside it rather than inside it, and for the reason that one gives about not being a
              tab: this is the other door marked staff. Two links rather than one screen with two
              halves, because repricing a savings product and repricing a reward are different
              jobs — and because a savings product's terms are an agreement customers live under,
              which is a heavier thing to be one press away from than a hamper's price. */}
          {' · '}
          <button
            type="button"
            className="link link--small"
            onClick={() => setScreen({ at: 'savings-products-administration' })}
          >
            Run the savings products
          </button>
          {/* The third door, and the one the other two were leading up to. The second link argued
              that repricing a savings product and repricing a reward are different jobs; this is
              that argument one size up. A product's terms are an agreement the accounts opened
              under them live by, and nobody already on the product moves when a new version is
              published; the scheme is a promotion nobody is pinned to, so publishing a version of
              it changes what a week asks for and what a run pays for every customer the bank has,
              from its Monday, all at once. Putting that behind the same press as a hamper's price
              would be putting the largest lever in the application in the smallest drawer.

              Fourth rather than first, although it is the heaviest of the three, because the
              footer is read left to right and these are in the order somebody learns them: the
              catalogue, then what the bank sells, then what the bank pays, then the till. Nothing
              here is hidden and nothing here is locked — the sentence at the top of the screen
              says so in as many words, and a link that pretended otherwise would be security
              theatre in a training application. */}
          {' · '}
          <button
            type="button"
            className="link link--small"
            onClick={() => setScreen({ at: 'scheme-administration' })}
          >
            Run the scheme
          </button>
          {/* A footer link and not a tab. The tab strip is the customer's five places and this is
              not one of them — it belongs to somebody behind a counter, who arrives here on their
              own phone and not through a customer's navigation. Discreet on purpose: a sixth
              prominent control would invite the customer to press it, and what is behind it is
              not theirs. */}
          {' · '}
          <button
            type="button"
            className="link link--small"
            onClick={() => setScreen({ at: 'counter' })}
          >
            Counter
          </button>
        </p>
      </footer>
    </>
  )
}

/** Who is signed in, where they are, what they have to spend, and what has been said to them. */
function TopBar({
  customer,
  onSignOut,
  screen,
  onChoose,
  pointsBalance,
  notifications,
  notificationsError,
  onOpened,
}: {
  customer: Customer
  onSignOut: () => void
  screen: Screen
  onChoose: (at: TabName) => void
  pointsBalance: number | null
  notifications: Notification[] | null
  notificationsError: string | null
  onOpened: () => void
}) {
  const bumped = useJustChanged(pointsBalance)

  return (
    <header className="topbar">
      <div className="topbar__inner">
        <button
          type="button"
          className="brand"
          aria-label="Saving Streak, back to the overview"
          onClick={() => onChoose('home')}
        >
          <span className="brand__mark">KBC</span>
          <span className="brand__name">Saving&nbsp;Streak</span>
        </button>

        <Tabs screen={screen} onChoose={onChoose} />

        <div className="topbar__right">
          {/* In the bar rather than on the overview, because it is the one thing here that is
              about something the customer has not been to look at. It stays on screen wherever
              they are, which is the whole reason a bell is a bell. */}
          <Bell
            notifications={notifications}
            notificationsError={notificationsError}
            onOpened={onOpened}
          />

          {/* The one figure that follows the customer from screen to screen, because it is the one
              they are spending: a reward on the rewards tab and a gift on the gifts tab are both
              paid out of it, and neither page should have to restate it to be read.

              It swells for a moment when the figure changes, which is the point of carrying it up
              here at all: a claim is made on one screen and a deposit on another, and this is the
              only thing on the page that is in front of the customer for both. */}
          <span
            className={bumped ? 'points-pill is-bumped' : 'points-pill'}
            title="Your points balance"
          >
            <SparkIcon className="points-pill__icon" />
            <strong>
              {pointsBalance === null ? (
                '—'
              ) : (
                <Rising value={pointsBalance} format={(shown) => points.format(Math.round(shown))} />
              )}
            </strong>
            <span className="points-pill__label">points</span>
          </span>

          <span className="avatar" aria-hidden="true">
            {initialsOf(customer.name)}
          </span>
          <button type="button" className="link link--small" onClick={onSignOut}>
            Sign out
          </button>
        </div>
      </div>
    </header>
  )
}

/** How long the points pill wears its swell. Long enough to catch, short enough not to be a state. */
const BUMP_LASTS = 600

/**
 * Whether a figure has just changed, for a moment afterwards.
 *
 * <p>The first arrival is not a change: a balance going from "not read yet" to a number is the page
 * loading, and the count-up already says so. Nor is it a change for somebody who has asked for less
 * motion — they get the new figure and nothing else.
 */
function useJustChanged(value: number | null): boolean {
  const [changed, setChanged] = useState(false)
  const before = useRef(value)

  useEffect(() => {
    const first = before.current === null
    if (before.current === value) {
      return
    }
    before.current = value
    if (stillness || first) {
      return
    }
    setChanged(true)
    const over = setTimeout(() => setChanged(false), BUMP_LASTS)
    return () => clearTimeout(over)
  }, [value])

  return changed
}

/**
 * The four screens, with one blue rule sliding between them.
 *
 * <p>The rule is a single element placed from the active tab's own offset and width rather than a
 * border on each tab, because a border cannot travel and the travel is the point: it says the page
 * changed rather than reloaded. Measured in a layout effect so the first paint already has it under
 * the right tab, and measured again whenever the strip's own width changes — a window resized, or a
 * font that arrived late and made every label a little wider.
 *
 * <p>Plain navigation buttons rather than the ARIA tab pattern. These do not switch panels inside
 * one page; they change what the whole application is showing, which is what `aria-current="page"`
 * is for, and the roving focus a tablist asks for would take Tab away from its ordinary job.
 *
 * <p>The lit tab and the announced one are deliberately not the same thing. An open savings account
 * lights Overview, because that is the screen it hangs from and pressing it is the way back — a
 * breadcrumb, drawn. `aria-current="page"` is not a breadcrumb: it is a claim about where the
 * reader is, and announcing "Overview, current page" to somebody looking at a deposit form on
 * savings account 3 is simply false. So the attribute is set from the screen itself and the class
 * from the tab it belongs under.
 */
function Tabs({
  screen,
  onChoose,
}: {
  screen: Screen
  onChoose: (at: TabName) => void
}) {
  const current = tabOf(screen)
  const strip = useRef<HTMLElement>(null)
  const [glider, setGlider] = useState<CSSProperties>({ width: 0 })

  useLayoutEffect(() => {
    const nav = strip.current
    if (nav === null) {
      return
    }
    const measure = () => {
      const active = nav.querySelector<HTMLElement>('.tab.is-active')
      if (active !== null) {
        setGlider({ width: active.offsetWidth, transform: `translateX(${active.offsetLeft}px)` })
      }
    }
    measure()
    const watching = new ResizeObserver(measure)
    watching.observe(nav)
    return () => watching.disconnect()
  }, [current])

  return (
    <nav className="tabs" aria-label="Screens" ref={strip}>
      {TABS.map((tab) => (
        <button
          key={tab.at}
          type="button"
          className={tab.at === current ? 'tab is-active' : 'tab'}
          aria-current={tab.at === screen.at ? 'page' : undefined}
          onClick={() => onChoose(tab.at)}
        >
          <span className="tab__icon" aria-hidden="true">
            {tab.icon}
          </span>
          <span>{tab.label}</span>
        </button>
      ))}
      <span className="tabs__glider" style={glider} aria-hidden="true" />
    </nav>
  )
}

/**
 * The bell: how many notifications have not been read, and the panel behind it.
 *
 * <p>The count is a badge only when there is one to show. A 0 in a circle is a figure somebody has
 * to read before they can find out that nothing happened, and the absence of the badge says the
 * same thing faster. The badge is decoration for anything that cannot see it — the button's own
 * label carries the count in words, so a screen reader is told "3 unread" rather than being handed
 * a bare number next to a picture of a bell.
 *
 * <p>Opening it is the customer having looked, so it marks everything read in one call. The rows do
 * not go anywhere: they lose their tint, and stay. Nothing is ever deleted here and there is no
 * dismissing — this is a record of what the rules decided, and a training application should not
 * let you throw away the evidence that one fired.
 */
function Bell({
  notifications,
  notificationsError,
  onOpened,
}: {
  notifications: Notification[] | null
  notificationsError: string | null
  onOpened: () => void
}) {
  const [open, setOpen] = useState(false)
  // The bell and its panel together, so that a press inside the panel is not a press outside it.
  const holding = useRef<HTMLDivElement>(null)

  const unread =
    notifications === null ? 0 : notifications.filter((said) => said.readAt === null).length

  // A panel that hangs over the page closes the two ways anything hanging over a page closes:
  // Escape, and a press anywhere else. Listened for only while it is open, so a closed bell adds
  // nothing to the page's handling of a key or a click.
  useEffect(() => {
    if (!open) {
      return
    }
    const elsewhere = (pressed: MouseEvent) => {
      if (holding.current !== null && !holding.current.contains(pressed.target as Node)) {
        setOpen(false)
      }
    }
    const escaped = (key: KeyboardEvent) => {
      if (key.key === 'Escape') {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', elsewhere)
    document.addEventListener('keydown', escaped)
    return () => {
      document.removeEventListener('mousedown', elsewhere)
      document.removeEventListener('keydown', escaped)
    }
  }, [open])

  return (
    <div className="bell-wrap" ref={holding}>
      <button
        type="button"
        className="bell"
        aria-expanded={open}
        aria-controls="notifications"
        aria-label={
          unread === 0
            ? 'Notifications, none unread'
            : `Notifications, ${points.format(unread)} unread`
        }
        onClick={() => {
          const opening = !open
          setOpen(opening)
          if (opening) {
            // Once per opening, and it is also the read: the call answers with the list as it now
            // stands, so the panel about to be drawn is showing what the backend has this moment
            // rather than what it had when the page was last loaded.
            onOpened()
          }
        }}
      >
        <BellIcon />
        {unread > 0 && (
          <span className="bell__count" aria-hidden="true">
            {points.format(unread)}
          </span>
        )}
      </button>

      {open && <NotificationsPanel notifications={notifications} error={notificationsError} />}
    </div>
  )
}

/**
 * Everything that has been said to this customer, newest first, read and unread together.
 *
 * <p>Read rows keep their place and lose their tint rather than being dropped. The panel is a record
 * and not an inbox that empties: a customer who has seen that their balance passed EUR 1.000 should
 * still be able to find the night it did.
 *
 * <p>The order is the backend's — newest first, as it sends them — and nothing here sorts. A row
 * says which pot it is about, because a customer holding two savings accounts cannot tell from
 * "your savings" alone which one crossed the rung.
 */
function NotificationsPanel({
  notifications,
  error,
}: {
  notifications: Notification[] | null
  error: string | null
}) {
  return (
    <div className="bell-panel" id="notifications" aria-label="Notifications" role="group">
      <div className="bell-panel__head">
        <h2>Notifications</h2>
      </div>

      {notifications !== null && notifications.length > 0 && (
        <ul className="notes">
          {notifications.map((said, place) => (
            // Two things in the class: which of the ten rules spoke, and whether it has been
            // read. The tint is what unread looks like, so it is the modifier that comes last.
            <li
              key={said.id}
              className={said.readAt === null ? `${toneOf(said.reason)} is-unread` : toneOf(said.reason)}
              style={rowDelay(place)}
            >
              <span className="note__icon" aria-hidden="true">
                <WhatHappenedIcon reason={said.reason} />
              </span>
              <div className="note__body">
                <p className="note__title">
                  <WhatHappened notification={said} />
                </p>
                <span className="note__when">
                  {dateAndTime.format(new Date(said.raisedAt))} · <WhichAccount notification={said} />
                </span>
              </div>
              {said.readAt === null && <span className="note__unread" aria-hidden="true" />}
            </li>
          ))}
        </ul>
      )}

      {/* Underneath the rows rather than instead of them: a read that failed after the panel had
          already been filled is worth saying without taking the record away. */}
      {(error !== null || notifications === null || notifications.length === 0) && (
        <div className="bell-panel__body">
          {error !== null && <Refusal reason={error} standing />}
          {notifications === null && error === null && (
            <Waiting label="Loading your notifications…" bars={['100%', '70%']} />
          )}
          {notifications !== null && notifications.length === 0 && (
            <p className="nothing">Nothing has happened yet.</p>
          )}
        </div>
      )}
    </div>
  )
}

/**
 * How a notification row is dressed, out of the rule that raised it.
 *
 * <p>Ten rules and four dressings, and the reading of each one is made here on the page rather than
 * sent from the backend: a reason is a rule, not a severity, and which of them is worth a red circle
 * is a question about this screen. A rung reached is the page's own blue; a rung lost is the amber
 * that means "worth knowing", and a budget running low wears it too — nothing has gone wrong yet and
 * there is still a fifth of the month's room to slow down in, which is the whole reason it is said
 * before the limit rather than after; a bonus on its way is green; the six that warn — a bonus first
 * in line for the next withdrawal, a transfer that did not happen, a bill that could not be paid,
 * bills piling up, a budget already overspent and a month promised to more than it holds — are red.
 */
function toneOf(reason: NotificationReason): string {
  switch (reason) {
    case 'BALANCE_THRESHOLD_REACHED':
      return 'note--above'
    case 'BALANCE_THRESHOLD_LOST':
    case 'A_BUDGET_IS_RUNNING_LOW':
      return 'note--below'
    case 'LOYALTY_BONUS_ABOUT_TO_PAY':
    // A turn in a queue that has come is the other unambiguously good thing a rule ever says,
    // so it wears the same green. It is nevertheless a deadline — three days and then it is
    // somebody else's — which is what the sentence is for; the dressing says "good news" and
    // the words say "and it runs out on Thursday".
    case 'A_REWARD_IS_BEING_HELD_FOR_YOU':
      return 'note--vesting'
    case 'LOYALTY_BONUS_AT_RISK':
      return 'note--forfeited'
    case 'AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN':
    case 'AN_AUTOMATIC_TRANSFER_HAD_NOWHERE_TO_GO':
    case 'A_BILL_COULD_NOT_BE_PAID':
    case 'BILLS_ARE_PILING_UP':
    case 'A_BUDGET_HAS_BEEN_OVERSPENT':
    case 'THE_MONTH_IS_OVER_COMMITTED':
      return 'note--missed'
    // A term coming up for maturity and notice that has run its days are both deadlines rather than
    // things gone wrong: nothing is broken, and what is worth knowing is that a decision is now the
    // customer's to take. They wear the amber a rung lost wears, which on these screens means
    // "worth knowing" and not "you were careless". A product that has bettered an account's terms is
    // the unambiguously good one and wears the green a bonus on its way does.
    case 'A_TERM_IS_ABOUT_TO_MATURE':
    case 'A_NOTICE_HAS_BECOME_READY':
      return 'note--below'
    case 'A_PRODUCT_HAS_BETTERED_YOUR_TERMS':
      return 'note--vesting'
  }
}

/**
 * Which pot a notification is about, in the line under the sentence.
 *
 * <p>It used to read `Savings account {id}` for every row, because every notification was about a
 * savings account. Five of the ten reasons are about a current account instead and carry no savings
 * account at all, so the line asks the notification which of the two it has rather than printing a
 * label with a blank after it. Neither reference is a stand-in for the other, and a reason that is
 * about neither — there is none today — draws nothing rather than a lie.
 */
function WhichAccount({ notification }: { notification: Notification }): ReactElement | null {
  const { currentAccountId, savingsAccountId } = notification
  if (savingsAccountId !== null) {
    return <>Savings account {savingsAccountId}</>
  }
  if (currentAccountId !== null) {
    return <>Current account {currentAccountId}</>
  }
  return null
}

/**
 * What a rule decided was worth saying, as a sentence, out of the reason and the figures behind it.
 *
 * <p>The sentence is written here and not in Java, which is the same bargain {@link WhatItEarned}
 * strikes with "7 base + 2 bonus at 1,30× + 30 loyalty": every euro and every date in this
 * application is written Dutch-style in the browser, and a sentence composed on the backend would
 * fork that formatting into a second place that will drift. Both figures go through the formatters
 * this page already has — {@link asADay} for the day, so an anniversary is written the way the
 * expiry deadline and the deposits list write theirs.
 *
 * <p>The ten sentences say ten different things and are deliberately not one sentence with a
 * word swapped:
 *
 * - a rung reached is a plain statement of something good, and says nothing else;
 * - a rung lost is the same statement in the other direction, and does not scold;
 * - an anniversary coming is a promise: what arrives, and when;
 * - an anniversary at risk is that promise plus the one thing the customer can act on — that a
 *   withdrawal now comes out of this deposit before any other, which is `WithdrawalsService`'s
 *   oldest-deposit-first rule said out loud to the person it protects;
 * - an automatic transfer that did not happen names the day it was due and, underneath, how far
 *   short the current account was. The day comes first because that is what the customer is trying
 *   to place; the shortfall is the consequence, and is the one thing they can do something about;
 * - a bill that could not be paid leads with the customer's own word for it — "Rent", "Energy" —
 *   because that is what they are trying to place, and then says both figures: what it asked for
 *   and what was actually there. Both, rather than one "short by" number, because the balance is
 *   what tells them how much to move back out of savings. It stops at what happened that night and
 *   does not add "and it is still owed": a notification is a record of a night, it is raised by a
 *   job that can run long after the night it is about, and the arrear may well have been settled in
 *   between — by the same run, even, on a raiser catching up. What is still owed today is on the
 *   current account's own panel, which is drawn from today's rows and is never out of date;
 * - bills piling up names no bill at all. It is about the whole hole, so it says how many dates are
 *   outstanding, what they come to, and the one thing worth knowing about paying them off: money
 *   moved back goes to the oldest first;
 * - a budget running low leads with the customer's own word for it and says which month, because a
 *   notification is raised on a night and read on some later one, and a budget is only ever about a
 *   month. Then all three figures: what has gone, what the month allows and what is therefore left,
 *   which is the one a customer trying to slow down is actually deciding against. "Four fifths" is
 *   never said — it is the rule behind the line, not the thing the customer needs;
 * - a budget overspent says the same three figures the other way round, ending on how far over it
 *   is, because that is what has changed. It does not scold and it does not say what to do: nothing
 *   in this application refuses a spend for being over a budget, and a warning that pretended
 *   otherwise would be describing an application this is not;
 * - a month over-committed names no category. It is about the whole promise, so it says what the
 *   bills, the arrears and the budgets come to together against what the month has, exactly as bills
 *   piling up says what the hole comes to.
 *
 * <p>**All three budget sentences say what was true on the night they were raised.** Every figure
 * behind them is derived, so a correction can empty the very month one of them is about, and none of
 * them is ever retracted — the panel is a log of the nights it was written on and the budget screen
 * is what is true now. Which is also why each of them writes its month out: read a fortnight later,
 * "Groceries is running low" without a month is a sentence about nothing in particular.
 *
 * <p>A figure the reason says should be there and is not draws nothing at all. Which fields are
 * filled in is decided by the reason and the backend keeps that total, so this is unreachable in
 * practice; drawing "Your savings passed" with a gap where the money goes would be worse than
 * drawing nothing.
 */
function WhatHappened({ notification }: { notification: Notification }): ReactElement | null {
  const { amount, arrears, balance, billName, categoryName, occursOn, points: worth, reason } =
    notification
  switch (reason) {
    case 'BALANCE_THRESHOLD_REACHED':
      return amount === null ? null : <>Your savings passed {euros.format(amount)}</>
    case 'BALANCE_THRESHOLD_LOST':
      return amount === null ? null : <>Your savings fell below {euros.format(amount)}</>
    case 'LOYALTY_BONUS_ABOUT_TO_PAY':
    case 'LOYALTY_BONUS_AT_RISK':
      if (worth === null || occursOn === null) {
        return null
      }
      return (
        <>
          {points.format(worth)} {worth === 1 ? 'point arrives' : 'points arrive'} on{' '}
          {asADay(occursOn)}
          {reason === 'LOYALTY_BONUS_AT_RISK' && (
            <span className="note__warning">
              {' '}
              — money taken out now comes out of this deposit first
            </span>
          )}
        </>
      )
    case 'AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN':
      if (amount === null || occursOn === null) {
        return null
      }
      return (
        <>
          An automatic transfer did not happen on {asADay(occursOn)}
          <span className="note__warning">
            — your current account was {euros.format(amount)} short, and nothing was moved
          </span>
        </>
      )
    case 'A_BILL_COULD_NOT_BE_PAID':
      if (amount === null || balance === null || occursOn === null || billName === null) {
        return null
      }
      return (
        <>
          {billName} of {euros.format(amount)} could not be paid on {asADay(occursOn)}
          <span className="note__warning">
            — your current account held {euros.format(balance)}, so nothing at all was taken
          </span>
        </>
      )
    case 'BILLS_ARE_PILING_UP':
      if (amount === null || arrears === null) {
        return null
      }
      return (
        <>
          Your unpaid bills are piling up
          <span className="note__warning">
            — {points.format(arrears)} {arrears === 1 ? 'bill is' : 'bills are'} still owed, coming
            to {euros.format(amount)}. Money moved back from savings goes to the oldest one first.
          </span>
        </>
      )
    case 'A_BUDGET_IS_RUNNING_LOW':
      if (amount === null || balance === null || categoryName === null || occursOn === null) {
        return null
      }
      return (
        <>
          {categoryName} is running low in {asTheMonthBeginningOn(occursOn)}
          <span className="note__warning">
            — {euros.format(balance)} of the {euros.format(amount)} it allows has gone, so there is{' '}
            {euros.format(amount - balance)} left for the rest of the month
          </span>
        </>
      )
    case 'A_BUDGET_HAS_BEEN_OVERSPENT':
      if (amount === null || balance === null || categoryName === null || occursOn === null) {
        return null
      }
      return (
        <>
          {categoryName} has gone over its budget for {asTheMonthBeginningOn(occursOn)}
          <span className="note__warning">
            — {euros.format(balance)} spent against the {euros.format(amount)} it allows, which is{' '}
            {euros.format(balance - amount)} over
          </span>
        </>
      )
    case 'THE_MONTH_IS_OVER_COMMITTED':
      if (amount === null || balance === null || occursOn === null) {
        return null
      }
      return (
        <>
          {asTheMonthBeginningOn(occursOn)} is promised to more than it holds
          <span className="note__warning">
            — your bills, what is already owed and what your budgets still allow come to{' '}
            {euros.format(amount)}, against the {euros.format(balance)} this month has
          </span>
        </>
      )
    case 'A_REWARD_IS_BEING_HELD_FOR_YOU':
      if (notification.offerTitle === null || notification.lapsesAt === null) {
        return null
      }
      // The deadline is the half of this the customer can act on, so it is in the sentence and
      // not left to the rewards card: a line saying one is being kept for them with no "until
      // when" is exactly the deadline nobody saw coming that a hold is built not to be. It is
      // written as a moment rather than as a day for the same reason the card counts down to
      // one — seventy-two hours is not a number of calendar days.
      return (
        <>
          Your turn came: one {notification.offerTitle} is being held for you
          <span className="note__aside">
            — it is yours until {asAMoment(notification.lapsesAt)}, and claiming it spends the
            points then rather than now
          </span>
        </>
      )
    case 'A_TERM_IS_ABOUT_TO_MATURE':
      if (notification.productName === null || occursOn === null) {
        return null
      }
      // The ending is not spelled out, and that is deliberate: what happens on the morning is what
      // the account agreed to at the beginning, it differs by product and by version, and a
      // sentence here would be a second place it is written down. The account's own page says it,
      // and this line's job is to send the customer there while the decision is still theirs.
      return (
        <>
          Your {notification.productName} matures on {asADay(occursOn)}
          <span className="note__aside">
            — what happens then is what these terms say it does, so change it before that morning if
            you want something else
          </span>
        </>
      )
    case 'A_NOTICE_HAS_BECOME_READY':
      if (amount === null || occursOn === null) {
        return null
      }
      return (
        <>
          {euros.format(amount)} you gave notice on came free on {asADay(occursOn)}
          <span className="note__aside">
            — it is yours to take whenever you like, and ready notice never runs out
          </span>
        </>
      )
    case 'A_PRODUCT_HAS_BETTERED_YOUR_TERMS':
      if (amount === null || balance === null || notification.productName === null) {
        return null
      }
      // The sentences are the backend's own and are printed rather than composed, which is the one
      // place on this page that happens for something other than a refusal. They are the very words
      // the product's version history prints, worded in one place in Java so that a customer
      // reading this line and then that history meets the same words twice. The two rates lead,
      // because the judgement behind the notice was taken on those two figures and nothing else —
      // and the sentences after it say the whole trade, including any figure that moved the other
      // way. They run on rather than becoming a list, because this sits inside a `<span>` and a
      // `<ul>` inside one is not a thing HTML has — the account's own panel draws the same
      // sentences as the list they are, which is where somebody comparing versions is looking.
      // Nothing is adopted here: the press is on that panel.
      return (
        <>
          {notification.productName} now pays {asAPercentage(amount)}, against the{' '}
          {asAPercentage(balance)} your account is on
          <span className="note__aside">
            — nothing has changed for you unless you take the newer terms.{' '}
            {notification.whatIsDifferent.join(' ')}
          </span>
        </>
      )
    case 'AN_AUTOMATIC_TRANSFER_HAD_NOWHERE_TO_GO':
      if (occursOn === null) {
        return null
      }
      // No figure, because there is none: the rule fired into a savings account that had been
      // closed, none of the money left the current account, and a "short by" number here would send
      // somebody to top up an account that was never the problem. What it says instead is the one
      // thing that fixes it, which is changing or ending the rule.
      return (
        <>
          An automatic transfer had nowhere to go on {asADay(occursOn)}
          <span className="note__warning">
            — the savings account it pays into has been closed, so nothing moved and nothing will
            until the rule is changed or ended
          </span>
        </>
      )
  }
}

/**
 * The month a budget notification is about, out of the day the backend sends for it.
 *
 * <p>The three reasons about budgets carry their month as the day it began, because a month is not a
 * type a database has and the first of it is a date that sorts and compares — so the page reads the
 * month back off that day rather than being sent a second field meaning "month". It goes through
 * {@link theMonthInWords}, the same formatter the budget screen's own month card uses, so a notice
 * and the screen it is about never spell one month two ways.
 *
 * <p>The time is pinned on for the reason {@link asADay} gives: a date on its own is parsed as
 * midnight UTC, so anybody west of Greenwich would be shown the month before.
 */
function asTheMonthBeginningOn(day: string): string {
  return theMonthInWords.format(new Date(`${day}T00:00:00`))
}

/**
 * The picture beside the sentence: which way a balance went, or that points are coming and whether
 * anything stands between them and the next withdrawal.
 *
 * <p>The six warnings of the ten get the warning icon the refusals already use, and the two
 * anniversaries share the spark that means points everywhere else on these screens. A budget running
 * low takes the falling arrow rather than the warning triangle, because what it is reporting is a
 * figure on its way down and not something that has gone wrong — the same picture a rung lost gets,
 * for the same reason.
 */
function WhatHappenedIcon({ reason }: { reason: NotificationReason }): ReactElement {
  switch (reason) {
    case 'BALANCE_THRESHOLD_REACHED':
      return <RisingIcon />
    case 'BALANCE_THRESHOLD_LOST':
    case 'A_BUDGET_IS_RUNNING_LOW':
      return <FallingIcon />
    case 'LOYALTY_BONUS_ABOUT_TO_PAY':
      return <SparkIcon />
    // The gift rather than the spark, because no points are involved: what is being held is a
    // thing, and the spark means points everywhere else on these screens.
    case 'A_REWARD_IS_BEING_HELD_FOR_YOU':
      return <GiftIcon />
    case 'LOYALTY_BONUS_AT_RISK':
      return <WarningIcon />
    case 'AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN':
    case 'AN_AUTOMATIC_TRANSFER_HAD_NOWHERE_TO_GO':
    case 'A_BILL_COULD_NOT_BE_PAID':
    case 'BILLS_ARE_PILING_UP':
    case 'A_BUDGET_HAS_BEEN_OVERSPENT':
    case 'THE_MONTH_IS_OVER_COMMITTED':
      return <WarningIcon />
    // A deadline coming and money that is now free to take are both the calendar rather than the
    // ledger, and the falling arrow is the picture this page already uses for "a figure is on the
    // move and nothing has gone wrong". A better rate is money going up, which is the rising one.
    case 'A_TERM_IS_ABOUT_TO_MATURE':
    case 'A_NOTICE_HAS_BECOME_READY':
      return <FallingIcon />
    case 'A_PRODUCT_HAS_BETTERED_YOUR_TERMS':
      return <RisingIcon />
  }
}

/**
 * Which dress the standing notice wears for one reason: the red left rule and tinted ground of a
 * warning, or the sky of a remark.
 *
 * <p>A `switch` with no `default` and a declared return type, exactly like {@link toneOf} and
 * {@link WhatHappenedIcon}, and for exactly the same reason: the day `NotificationReason` grows a
 * sixth value this function stops compiling and somebody has to decide whether the new reason
 * warns. It used to be an `if` testing one reason by name, which is what let
 * {@code AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN} arrive and be dressed as a remark — a red warning
 * icon and a red shortfall line inside the calm blue ground — while the panel behind the bell drew
 * the very same notification red. Two components deciding the same question from two lists is one
 * list too many; this is the second one made answerable to the compiler.
 */
function noticeToneOf(reason: NotificationReason): string {
  switch (reason) {
    case 'BALANCE_THRESHOLD_REACHED':
    case 'BALANCE_THRESHOLD_LOST':
    case 'LOYALTY_BONUS_ABOUT_TO_PAY':
    case 'A_BUDGET_IS_RUNNING_LOW':
    case 'A_REWARD_IS_BEING_HELD_FOR_YOU':
      return 'notice--calm'
    case 'LOYALTY_BONUS_AT_RISK':
    case 'AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN':
    case 'AN_AUTOMATIC_TRANSFER_HAD_NOWHERE_TO_GO':
    case 'A_BILL_COULD_NOT_BE_PAID':
    case 'BILLS_ARE_PILING_UP':
    case 'A_BUDGET_HAS_BEEN_OVERSPENT':
    case 'THE_MONTH_IS_OVER_COMMITTED':
      return 'notice--warning'
    // Calm, all three: a maturity coming, notice that has run and a better rate on offer are
    // things to act on rather than things gone wrong, and a red rule on the account page beside
    // the withdrawal form would be crying wolf about a term ending exactly as it was agreed to.
    case 'A_TERM_IS_ABOUT_TO_MATURE':
    case 'A_NOTICE_HAS_BECOME_READY':
    case 'A_PRODUCT_HAS_BETTERED_YOUR_TERMS':
      return 'notice--calm'
  }
}

/**
 * The newest thing a rule has said about one savings account that its holder has not read yet,
 * drawn on that account's own page rather than left behind the bell.
 *
 * <p>The point of it being here is proximity. The warning worth having — a deposit whose
 * anniversary is close and which the next withdrawal would empty first — belongs beside the
 * withdrawal form that would cost the customer that bonus, not two clicks away behind an icon.
 *
 * <p>Six of the ten reasons warn and four are remarks, and they are dressed as what they are.
 * The three calm ones wear the sky the rest of the identity is built on; {@code
 * LOYALTY_BONUS_AT_RISK} and {@code AN_AUTOMATIC_TRANSFER_DID_NOT_HAPPEN} wear the red left rule
 * and the tinted ground {@link Refusal} has, because a bonus about to be lost and a transfer the
 * customer was relying on that did not happen are both things gone wrong. Which is which is
 * {@link noticeToneOf}'s to say, and the icon is {@link WhatHappenedIcon}'s, so this notice and the
 * panel behind the bell cannot disagree about the same notification.
 *
 * <p>Neither of them is announced. {@code role="alert"} is an assertive live region — it means
 * "this has just happened" — and this notice is drawn again on every load of the account page, so
 * announcing it would interrupt a screen-reader user every single visit to say something that has
 * been true for days. It is a standing record, and it is read in its place like the rest of the
 * page. The same reasoning is why it does not shake: a refusal shakes because it has just happened
 * in answer to something the customer did; movement that arrives unasked on every load is noise.
 * Colour, geometry and wording all stay; only the claim that this is news goes.
 *
 * <p>There is no dismiss button, here or anywhere. The notice goes when the notification has been
 * read, which happens when the panel is opened — two states is one state machine, and a training
 * application should not let you throw away the evidence that a rule fired.
 */
function Notice({ notification }: { notification: Notification }) {
  return (
    <p className={`notice ${noticeToneOf(notification.reason)}`}>
      <WhatHappenedIcon reason={notification.reason} />
      <span>
        <WhatHappened notification={notification} />
      </span>
    </p>
  )
}

/**
 * The sky band at the top of a screen: an eyebrow, the screen's headline, a sentence under it, and
 * whatever there is to press.
 *
 * <p>It holds the document's only `<h1>`, which is why {@link ScreenHero} gives it different words
 * on every screen rather than the one greeting vibe's hero repeats above all five of its views: a
 * heading that says "hello" over a page about giving points away is a heading that titles nothing,
 * and a screen reader jumping by heading would be told the same thing five times.
 *
 * <p>`compact` is for the four screens that are not the overview. They have no greeting and no
 * call to action, so the band gives back the height it was carrying them in.
 */
function Hero({
  eyebrow,
  title,
  sub,
  compact = false,
  children,
}: {
  eyebrow: string
  title: ReactNode
  sub: ReactNode
  compact?: boolean
  children?: ReactNode
}) {
  return (
    <div className={compact ? 'hero hero--compact' : 'hero'}>
      <div className="hero__wash" aria-hidden="true" />
      <div className="hero__inner">
        <p className="hero__eyebrow">{eyebrow}</p>
        <h1 className="hero__title">{title}</h1>
        <p className="hero__sub">{sub}</p>
        {children}
      </div>
      <div className="hero__art" aria-hidden="true">
        <CoinStack />
      </div>
    </div>
  )
}

/**
 * What each screen puts in the band above it.
 *
 * <p>The overview keeps vibe's own greeting word for word, because on a page that is nothing but a
 * person's own figures the greeting *is* the title — and the half of it that is a figure is the
 * backend's. The other four say what they are, so that the one heading a page carries at headline
 * size names the page rather than the person.
 *
 * <p>The sentence under each headline is the sentence that screen used to carry inside itself. It
 * is said once, here, rather than once here and once again in the first card.
 *
 * <p>The button appears only for a customer holding exactly one savings account. With two pots,
 * "move money into savings" is a question — into which one? — and the cards below are where that
 * question is already answered; a hero button that picked one of them would be picking for them.
 */
function ScreenHero({
  screen,
  customer,
  accounts,
  onOpen,
}: {
  screen: Screen
  customer: Customer
  accounts: CustomerAccounts | null
  onOpen: (savingsAccountId: number) => void
}): ReactElement {
  switch (screen.at) {
    case 'simulator':
      return (
        <Hero
          compact
          eyebrow="What if I did this instead"
          title={`Savings account ${screen.savingsAccountId}`}
          sub="Branches of the year ahead, side by side with the year you are already in. Nothing
               here moves any money, and nothing here is kept."
        />
      )
    case 'automatic-saving':
      return (
        <Hero
          compact
          eyebrow="Automatic saving"
          title={`Savings account ${screen.savingsAccountId}`}
          sub="The rules you have left standing here, what each will do next, what each has done, and
               where the income they move is declared."
        />
      )
    case 'savings-account':
      return (
        <Hero
          compact
          eyebrow="Your savings"
          title={`Savings account ${screen.savingsAccountId}`}
          sub="Everything you have put away here, what it has earned, and the two forms that move it."
        />
      )
    case 'budget':
      return (
        <Hero
          compact
          eyebrow="Your budget"
          title={`Current account ${screen.currentAccountId}`}
          sub="The things your money goes on, in your own words. Name one, rename one you chose
               badly, and end one you no longer use — what you ended stays readable."
        />
      )
    case 'current-account':
      return (
        <Hero
          compact
          eyebrow="Your everyday money"
          title={`Current account ${screen.currentAccountId}`}
          sub="What is in it, and what you have said arrives in it every month. This is where your
               income is declared — one place, one version of it."
        />
      )
    case 'administration':
      return (
        <Hero
          compact
          eyebrow="Administration"
          title="The rewards catalogue"
          sub="Write an offer, correct one, publish it when it is ready and withdraw it when it is
               finished. Nothing here checks who you are."
        />
      )
    case 'savings-products':
      return (
        <Hero
          compact
          eyebrow="Savings products"
          title="What this bank pays for saving"
          sub="Type what you would put away and see what each product would be worth over twelve
               months, in euros and in points. Nothing here opens anything until you press it."
        />
      )
    case 'savings-products-administration':
      return (
        <Hero
          compact
          eyebrow="Administration"
          title="What the bank sells as savings"
          sub="Publish the next version of a product's terms, and close a product to new accounts or
               put it back on sale. Nothing already published is ever edited, and nothing here
               checks who you are."
        />
      )
    case 'scheme-administration':
      return (
        <Hero
          compact
          eyebrow="Administration"
          title="What this bank pays for saving"
          sub="The scheme in force, the form that publishes the next version of it, and what that
               version would have done had it been the rule all along. Nothing is published until
               you have looked at it, and nothing here checks who you are."
        />
      )
    case 'counter':
      return (
        <Hero
          compact
          eyebrow="Counter"
          title="Hand a voucher over"
          sub="Type the code on the voucher, check what it is for, and mark it used. Nothing here
               checks who you are — this is a counter screen and not authorisation."
        />
      )
    case 'rewards':
      return (
        <Hero
          compact
          eyebrow="Rewards"
          title="Trade in your points"
          sub="Claims are final — the voucher is issued straight away."
        />
      )
    case 'challenges':
      return (
        <Hero
          compact
          eyebrow="Challenges"
          title="Things worth being part-way towards"
          sub="Longer than a week: take one on, climb its rungs, and keep every badge you reach —
               nothing you win here is ever taken back, whatever you do with the money afterwards."
        />
      )
    case 'gifts':
      return (
        <Hero
          compact
          eyebrow="Gifts"
          title="Give points to somebody who banks here"
          sub="Points you give are theirs straight away — there is nothing to accept and no way back.
               They keep the age they were earned at, so a gift does not buy them another twelve
               months."
        />
      )
    case 'history':
      return (
        <Hero
          compact
          eyebrow="History"
          title="Money history"
          sub="Everything that moved, newest first: what you saved, what you took back, and the
               bills that went out — including the ones that could not be paid. Each row is one
               movement, and nothing here is a total."
        />
      )
    case 'home': {
      const only =
        accounts !== null && accounts.savingsAccounts.length === 1
          ? accounts.savingsAccounts[0]
          : null
      return (
        <Hero
          eyebrow="Saving pays off"
          title={
            <>
              Hello {firstNameOf(customer.name)}, <span>{streakLine(accounts)}</span>
            </>
          }
          sub="Every euro you move into savings earns a point. Spend them on a reward, or give them
               to somebody else who banks here."
        >
          {only !== null && (
            <p className="hero__cta">
              <Button onClick={() => onOpen(only.id)}>
                Move money into savings
                <ForwardIcon className="btn__chevron" />
              </Button>
            </p>
          )}
        </Hero>
      )
    }
  }
}

/**
 * The second half of the greeting: where the run of weeks stands, or what it would take to start
 * one. Every figure in it is the backend's, including what a week asks for — this page has never
 * known that it is fifty euros.
 */
function streakLine(accounts: CustomerAccounts | null): string {
  if (accounts === null) {
    return 'welcome back.'
  }
  if (accounts.currentStreakWeeks > 0) {
    return `you have saved ${inWeeks(accounts.currentStreakWeeks)} in a row.`
  }
  // The gap rather than the whole minimum, and the backend's own subtraction rather than one taken
  // here: somebody who has already put away forty of the fifty this week is not being asked for
  // fifty, and a greeting that said so would be the one sentence on the page contradicting the bar
  // under it.
  if (accounts.stillNeededThisWeek > 0) {
    return `save ${euros.format(accounts.stillNeededThisWeek)} more this week to start a streak.`
  }
  return 'this week already has what it asks for.'
}

/** Four coins, bobbing. Decoration, and marked as such by whoever draws it. */
function CoinStack() {
  return (
    <div className="coinstack">
      <span className="coin" />
      <span className="coin" />
      <span className="coin" />
      <span className="coin" />
    </div>
  )
}

/**
 * What the customer holds: how the week is going, what all that saving has earned, and every
 * account with what is in it.
 *
 * <p>Nothing here is added up. Two savings balances are two balances, and a total across them would
 * be a figure this page worked out for itself — which is the one thing no figure on any of these
 * screens is. The points are one figure because the backend sends one: they belong to the customer,
 * and summing what each account earned is its arithmetic and not this page's.
 */
function Overview({
  customerId,
  accounts,
  accountsError,
  onOpen,
  onOpenCurrentAccount,
  onOpenHistory,
  onOpenChallenges,
  onCompareProducts,
  onOpened,
}: {
  customerId: number
  accounts: CustomerAccounts | null
  accountsError: string | null
  onOpen: (savingsAccountId: number) => void
  onOpenCurrentAccount: (currentAccountId: number) => void
  onOpenHistory: () => void
  onOpenChallenges: () => void
  onCompareProducts: () => void
  onOpened: (savingsAccountId: number) => void
}) {
  return (
    <section className="view">
      {/* Three conditions side by side rather than one early return, because a read that failed
          leaves the figures it failed to refresh standing: `loadAccounts` puts the message in
          `accountsError` and deliberately does not clear `accounts`, so the last good balances are
          still in state. Blanking the screen to a red band would throw away figures the customer
          can still read — and take with them the only way back to a screen that could ask for
          them again. */}
      {accountsError !== null && <Refusal reason={accountsError} standing />}

      {accounts === null && accountsError === null && (
        <div className="card">
          <Waiting label="Loading your accounts…" bars={['7rem', '100%', '60%']} />
        </div>
      )}

      {accounts !== null && (
        <>
          <div className="grid grid--overview">
            <WeekCard week={accounts} />
            <PointsCard held={accounts} delay="60ms" />
          </div>

          <ChallengesLine customerId={customerId} onOpenChallenges={onOpenChallenges} />

          <div className="section-head">
            <h2>Your accounts</h2>
            {/* The history is a history of these accounts: every row in it moved money into or
                out of one of the cards below, which is what makes this the section that carries
                the way in. */}
            <button type="button" className="link" onClick={onOpenHistory}>
              Money history
              <ForwardIcon />
            </button>
          </div>

          <div className="grid grid--accounts">
            {accounts.currentAccounts.map((account, place) => (
              <CurrentAccountCard
                key={account.id}
                account={account}
                place={place}
                onOpen={onOpenCurrentAccount}
              />
            ))}
            {accounts.savingsAccounts.map((account, place) => (
              <SavingsAccountCard
                key={account.id}
                account={account}
                place={accounts.currentAccounts.length + place}
                onOpen={onOpen}
              />
            ))}
          </div>

          {accounts.currentAccounts.length === 0 && <p className="nothing">No current account.</p>}
          {accounts.savingsAccounts.length === 0 && <p className="nothing">No savings account.</p>}

          {/* Under the accounts rather than above them, because it is the answer to a question the
              grid has just raised: these are the ones you hold, and here is what else this bank
              sells. It is on the overview rather than on a screen of its own because choosing a
              product is the first decision a saver makes and a decision nobody can find is a
              decision nobody makes. */}
          <OpenASavingsAccount
            customerId={customerId}
            onOpened={onOpened}
            onCompare={onCompareProducts}
          />
        </>
      )}
    </section>
  )
}

/**
 * One line on the overview about the challenges that are running, and what the nearest rung still
 * asks for — because a challenge nobody is reminded of is a challenge nobody finishes.
 *
 * <p><strong>Nothing at all when nothing is running.</strong> Not an empty state, not an invitation,
 * not a dimmed row saying there could be something here. The tab is one press away in the strip
 * above and advertises itself; this line exists to remind somebody of a thing they already started,
 * and an application that says "you have no challenges" every morning to somebody who does not want
 * one has turned a reminder into nagging. `null`, and the accounts below simply move up.
 *
 * <p><strong>One line, and deliberately not a second Challenges panel.</strong> Everything about a
 * challenge — its rungs, its words, what a withdrawal does to it — is on the tab, said once. What
 * belongs on the home screen is the single fact that decides whether to open it.
 *
 * <p>Its own read rather than the accounts', for the reason the tab reads its own: this is the tab's
 * subject and nothing else on this screen wants it. It is re-read whenever the overview is drawn,
 * which is every time the customer comes back to it from anywhere that could have moved the figure.
 *
 * <p>A failed read says nothing, where every other read on this screen says why. That is the one
 * place this line is not like the rest of the page, and it follows from the first rule above: the
 * silent state is a legitimate state here, so a red band over a line that is allowed to be absent
 * would be louder than the line itself ever is. Nothing is lost — the tab shows the same refusal in
 * full, with its reason, on a screen that is about challenges.
 */
function ChallengesLine({
  customerId,
  onOpenChallenges,
}: {
  customerId: number
  onOpenChallenges: () => void
}) {
  const [challenges, setChallenges] = useState<Challenge[] | null>(null)

  useEffect(() => {
    const request = new AbortController()
    fetchChallenges(customerId, request.signal)
      .then((offered) => {
        if (!request.signal.aborted) {
          setChallenges(offered)
        }
      })
      .catch(() => undefined)
    return () => request.abort()
  }, [customerId])

  // `enrolled` rather than `state !== null`: what is *running*. A challenge finished last month and
  // one walked away from are both things this customer has a state in, and neither is something to
  // be reminded about.
  const running = (challenges ?? []).filter((challenge) => challenge.enrolled)
  if (running.length === 0) {
    return null
  }
  const nearest = theNearestRung(running)

  return (
    <p className="chal-line">
      <TrophyIcon />
      <span>
        {running.length === 1 ? '1 challenge running' : `${running.length} challenges running`}.{' '}
        {nearest === null
          ? running.length === 1
            ? 'Every rung on it is cleared.'
            : 'Every rung on them is cleared.'
          : `The nearest rung is ${RUNG_WORDS[nearest.rung].toLowerCase()} on ${
              nearest.challenge.title
            }, which still asks for ${inTheUnitsOf(nearest.challenge.kind, nearest.stillNeeded)}.`}
      </span>
      <button type="button" className="link link--small" onClick={onOpenChallenges}>
        Your challenges
        <ForwardIcon />
      </button>
    </p>
  )
}

/**
 * The nearest of the next rungs, across every challenge the customer has running.
 *
 * <p><strong>"Nearest" is how far along the way there they are, not how little is left.</strong> The
 * five kinds are read in five different units — euros, weeks, days, goals — and the remainders are
 * bare numbers in each of them, so the smallest remainder across a list is an arithmetic that
 * compares sixteen days against sixteen euros and calls them the same distance. It is worse than
 * meaningless: it is systematically wrong in one direction, because the kinds counted in small
 * numbers would win every time and the euro challenges — the ones with three figures on every rung
 * — would never be named however close they were. A proportion is the one comparison the five kinds
 * share: the reading over what the next rung asks for is a fraction of the way there in every one
 * of them, and the largest fraction is honestly the nearest.
 *
 * <p>A challenge whose gold is already cleared has no next rung and so is not a candidate at all —
 * it is not nearly anywhere, it is finished — and neither is one whose next rung asks for nothing,
 * because a proportion of zero is not a number. Both are skipped rather than ranked, which is why
 * this can answer `null` while the customer is plainly in something.
 *
 * <p>Ties keep the first, which is the order the backend sent the catalogue in: two challenges
 * exactly as close is not a question this line has to arbitrate, and picking by some second figure
 * would only make which of them is named harder to predict.
 */
function theNearestRung(
  running: Challenge[],
): { challenge: Challenge; rung: Rung; stillNeeded: number } | null {
  let nearest: { challenge: Challenge; rung: Rung; stillNeeded: number } | null = null
  let howFarAlong = -1
  for (const challenge of running) {
    const next = challenge.rungs.find((rung) => rung.rung === challenge.nextRung)
    if (
      next === undefined ||
      next.threshold <= 0 ||
      challenge.reading === null ||
      challenge.stillNeeded === null
    ) {
      continue
    }
    const thisFar = challenge.reading / next.threshold
    if (thisFar > howFarAlong) {
      howFarAlong = thisFar
      nearest = { challenge, rung: next.rung, stillNeeded: challenge.stillNeeded }
    }
  }
  return nearest
}

/**
 * The two shapes a card on either screen reads, named off the customer's accounts because the two
 * endpoints publish the same figures under the same names: what the overview knows about the whole
 * customer, one savings account knows about itself. Stating them as a `Pick` rather than as fresh
 * interfaces is what lets one card serve both screens without either endpoint's answer being
 * unpacked into props a field at a time.
 */
type WeekView = Pick<CustomerAccounts, 'newSavingsThisWeek' | 'weeklyMinimum'>

type PointsView = Pick<
  CustomerAccounts,
  | 'pointsBalance'
  | 'pointsExpiringNext'
  | 'pointsExpiringNextOn'
  | 'currentStreakWeeks'
  | 'bestStreakWeeks'
  | 'currentMultiplier'
>

/**
 * How the week is going: what has landed since Monday, and how much more it asks for.
 *
 * <p>The figure, the bar and the sentence are one thing rather than a figure with two things said
 * beside it: the whole card is drawn from whatever figure the rise is showing at this moment, so no
 * frame of it can say the week is done while the figure on the screen is still short of what the
 * week asks for.
 */
function WeekCard({ week }: { week: WeekView }) {
  return (
    <article className="card reveal">
      <h2 className="card__title">This week</h2>
      <Rising
        value={week.newSavingsThisWeek}
        format={(shown) => (
          <>
            <p className="amount amount--xl">{euros.format(shown)}</p>
            {/* What the week asks for, stated once, here — the bar underneath says only how far
                along the figure above is, so the two never repeat each other. */}
            <dl className="split">
              <div>
                <dt>Weekly minimum</dt>
                <dd>{euros.format(week.weeklyMinimum)}</dd>
              </div>
            </dl>
            <WeekBar shown={shown} weeklyMinimum={week.weeklyMinimum} />
          </>
        )}
      />
    </article>
  )
}

/**
 * How far the week has got, as a length, with what it still asks for said in words beside it.
 *
 * <p>Both are drawn from one number — the figure on the screen at this moment, which is handed in by
 * {@link Rising} while it is still climbing towards what the account now holds. That is the point of
 * this taking the shown figure rather than the settled one: two parts of one card reading off two
 * different numbers is a card that contradicts itself, and a bar drawn full beside "the week has
 * what it asks for" while the figure above still reads € 21,00 tells a customer their week is done
 * and shows them that it is not.
 *
 * <p>The € 50 is the backend's, and it is named once by the card above this rather than twice: the
 * bar says only how far along the figure is. What the week still asks for is the gap up to it — the
 * same subtraction the backend publishes as {@code stillNeededThisWeek}, taken here against the
 * figure actually on the screen so that the
 * words are never about a figure the customer cannot see. Once the figure has arrived, the two are
 * the same amount.
 *
 * <p>The bar is hidden from a screen reader because the sentence beside it says the same thing in
 * words, and hearing the same fact twice is worse than hearing it once.
 */
function WeekBar({ shown, weeklyMinimum }: { shown: number; weeklyMinimum: number }) {
  const asksForNoMore = shown >= weeklyMinimum
  const howFarAlong =
    weeklyMinimum <= 0 ? 100 : Math.min(100, Math.max(0, (shown / weeklyMinimum) * 100))
  return (
    <div className="weekbar">
      <p className={asksForNoMore ? 'weekbar__state is-safe' : 'weekbar__state is-open'}>
        {asksForNoMore
          ? 'the week has what it asks for'
          : `${euros.format(weeklyMinimum - shown)} more to go`}
      </p>
      <div className="progress" aria-hidden="true">
        <span className="progress__fill" style={{ width: `${howFarAlong}%` }} />
      </div>
    </div>
  )
}

/**
 * The points, the run of weeks that grew them, and what a euro is worth right now.
 *
 * <p>The one panel on either screen that is not white, because it holds the one figure this
 * application exists to grow. The same card serves the overview and an open savings account: both
 * endpoints publish the same six names, and the points are the customer's rather than any one
 * account's, so the figure reads the same beside every account they hold.
 *
 * <p>The rate first among the facts, because it is the figure being acted on and the other two
 * explain it. It is shown even when there is no run at all: 1,00× is what a euro has always earned,
 * and a customer who cannot see the ordinary rate has nothing to read the better ones against.
 *
 * <p>Two week figures rather than one, so that a lapse leaves something behind. The current run is
 * what there is to lose and the best-ever run is what there is to beat, and an account that has
 * never secured a week has neither: it says so in a sentence, because "best ever 0 weeks" is a
 * record nobody set.
 */
function PointsCard({ held, delay }: { held: PointsView; delay?: string }) {
  return (
    <article className="card card--navy reveal" style={{ '--delay': delay } as CSSProperties}>
      <div className="streak">
        <span className="streak__flame" aria-hidden="true">
          <FlameIcon />
        </span>
        <div>
          <h2 className="card__title card__title--light">Saving streak</h2>
          <p className="streak__weeks">
            {/* Three states, three sentences. An account that has never secured a week has no
                record to report; one whose run has lapsed has a record and no run, and "0 weeks in
                a row" is a figure nobody has — the row underneath still shows the best it reached.
                Only a live run gets the figure set large, because it is the only one of the three
                that is a number the customer is holding. */}
            {held.bestStreakWeeks <= 0 ? (
              'no week secured yet'
            ) : held.currentStreakWeeks === 0 ? (
              'no weeks in a row'
            ) : (
              <>
                <strong>{points.format(held.currentStreakWeeks)}</strong>{' '}
                {held.currentStreakWeeks === 1 ? 'week' : 'weeks'} in a row
              </>
            )}
          </p>
        </div>
      </div>

      {/* The deadline is inside the rise, as the week's bar is: while the balance is still climbing,
          a deadline taken from the settled figure would say more points were going than the
          customer appears to have. */}
      <Rising
        value={held.pointsBalance}
        format={(shown) => (
          <>
            <p className="points-big">
              {points.format(Math.round(shown))} <small>points</small>
            </p>
            <ExpiringNext
              expiring={held.pointsExpiringNext}
              on={held.pointsExpiringNextOn}
              outOf={Math.round(shown)}
            />
          </>
        )}
      />

      <ul className="navy-facts">
        <li>
          <span>Earning</span>
          <strong>{rate.format(held.currentMultiplier)}× per euro</strong>
        </li>
        <li>
          <span>Best streak</span>
          <strong>{held.bestStreakWeeks <= 0 ? 'none yet' : inWeeks(held.bestStreakWeeks)}</strong>
        </li>
      </ul>

      <StreakRungs current={held.currentStreakWeeks} best={held.bestStreakWeeks} />
    </article>
  )
}

/**
 * A row of rungs only says something between these two. One rung is a solid bar rather than a
 * record; past a dozen the rungs are slivers nobody counts. Outside the band the two figures above
 * say the same thing in words, which is why nothing is lost by leaving the row out.
 */
const FEWEST_RUNGS_WORTH_DRAWING = 2
const MOST_RUNGS_WORTH_DRAWING = 12

/**
 * One rung per week of the best run this account has ever had, lit for the weeks of the run it is on
 * now. Both figures are the backend's and neither is scaled: the rungs are the record, and the lit
 * ones are how much of it has been matched.
 *
 * <p>Drawn only while the record is long enough to have rungs and short enough for them to be
 * counted at a glance — see the two bounds above.
 */
function StreakRungs({ current, best }: { current: number; best: number }) {
  if (best < FEWEST_RUNGS_WORTH_DRAWING || best > MOST_RUNGS_WORTH_DRAWING) {
    return null
  }
  return (
    <div className="streak__dots" aria-hidden="true">
      {Array.from({ length: best }, (_, week) => (
        <span key={week} className={week < current ? 'streak__dot is-on' : 'streak__dot'} />
      ))}
    </div>
  )
}

/**
 * An everyday account: what is in it, and the way into it.
 *
 * <p>A button rather than a tile, like the savings card beside it. The account has a page of its own
 * now — what arrives in it every month, and in the slices that follow what leaves it — and the card
 * naming the account is where a customer looks for it. There is no router, so this press is the only
 * way in and a reload comes back here, which is the bargain every screen in this application
 * strikes.
 */
function CurrentAccountCard({
  account,
  place,
  onOpen,
}: {
  account: CurrentAccount
  place: number
  onOpen: (currentAccountId: number) => void
}) {
  return (
    <button
      type="button"
      className="acc is-current"
      style={rowDelay(place)}
      onClick={() => onOpen(account.id)}
    >
      <span className="acc__top">
        <span className="acc__icon" aria-hidden="true">
          <BankIcon />
        </span>
        <span>
          <span className="acc__name">Current account</span>
          <span className="acc__iban">{spacedIban(account.iban)}</span>
        </span>
      </span>
      <span className="acc__amount">{euros.format(account.balance)}</span>
      <span className="acc__badge">Everyday</span>
      <span className="acc__go" aria-hidden="true">
        <ForwardIcon />
      </span>
    </button>
  )
}

/**
 * A savings account: what it holds, which product it is on, and the way into it.
 *
 * <p>The money and the product, and nothing else. The points a customer has are theirs rather than any one account's,
 * so a figure repeated on every card would say that each account had its own — and somebody holding
 * two would appear to have twice the points they can actually spend. They are shown once, on the
 * navy panel, where the whole run of weeks that earned them is.
 */
function SavingsAccountCard({
  account,
  place,
  onOpen,
}: {
  account: SavingsAccount
  place: number
  onOpen: (savingsAccountId: number) => void
}) {
  const closed = account.closedOn !== null
  return (
    <button
      type="button"
      // A closed account is drawn quieter rather than left out. It is still the customer's, its
      // history still reads, and the money history on this same screen still counts every euro that
      // moved through it — a card that vanished would leave those euros belonging to nothing. What
      // it must not look like is somewhere to put money, which is what the dimming and the badge
      // below are for.
      className={closed ? 'acc acc--closed' : 'acc'}
      style={{ ...rowDelay(place), ...hueOf(account.id) }}
      onClick={() => onOpen(account.id)}
    >
      <span className="acc__top">
        <span className="acc__icon" aria-hidden="true">
          <PotIcon />
        </span>
        <span>
          <span className="acc__name">Savings account {account.id}</span>
          {/* Which product it is on, under its number, where a current account's card carries its
              IBAN. It is the second thing about a savings account worth knowing and the first thing
              that distinguishes two of them: from the day a notice account exists, "which of these
              can I take money out of today" is a question this grid has to answer without being
              opened. Nothing at all for an account the backend recorded no agreement for, rather
              than a name this page made up. */}
          {account.productName !== null && (
            <span className="acc__product">{account.productName}</span>
          )}
        </span>
      </span>
      <span className="acc__amount">{euros.format(account.moneyBalance)}</span>
      {/* The badge says which of the two this card is, and for a closed account that is the most
          useful word on it: the balance is nought and the product is one the customer no longer
          uses, and neither of those on its own says the account has been ended. */}
      <span className="acc__badge">
        {closed ? `Closed ${asADay(account.closedOn as string)}` : 'Savings'}
      </span>
      <span className="acc__go" aria-hidden="true">
        <ForwardIcon />
      </span>
    </button>
  )
}

/**
 * The points the customer has, what they buy, and what has already been bought with them.
 *
 * <p>A screen of its own rather than a panel under the accounts, because spending is a different
 * errand from saving: the balance is the pot every one of their savings accounts earns into, the
 * catalogue is what the pot buys, and the list underneath is what has come out of it. A customer can
 * add the vouchers up against the figure above them.
 *
 * <p>Claimed from here rather than from an account, because the points are the person's. There used
 * to be nothing to press on this screen — points belonged to one savings account at a time, so there
 * was no such thing as what this customer could afford — and one pot is exactly what makes the
 * button possible.
 *
 * <p>What each reward is, what it costs and what to call it all come from the backend, so a reward
 * added or repriced there appears here with no change: the only thing this page decides is which
 * picture to put beside a code it recognises, and there is one for a code it does not.
 */
function Rewards({
  customerId,
  accounts,
  accountsError,
  rewards,
  rewardsError,
  claimed,
  claimedError,
  onClaimed,
}: {
  customerId: number
  accounts: CustomerAccounts | null
  accountsError: string | null
  rewards: RewardForACustomer[] | null
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

  function claim(reward: RewardForACustomer) {
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

  // Which card is waiting on a hold, rather than whether one is, for the same reason claiming
  // tracks the code: the button that was pressed is the one that shows it is working and the
  // others simply stop being pressable. The three hold actions share it because no card can be
  // doing two of them at once.
  const [holding, setHolding] = useState<string | null>(null)

  // Taking a hold, claiming the one being held, and giving it up all go through here. They
  // share a refusal line with claiming deliberately: there is one thing standing between this
  // customer and this card at any moment, and two refusal areas would mean a page that could
  // show two contradictory sentences at once.
  function actOnAHold(reward: RewardForACustomer, act: () => Promise<unknown>) {
    setHolding(reward.code)
    setClaimError(null)
    act()
      .then((outcome) => {
        // A conversion comes back with the voucher it issued, and that is worth celebrating in
        // exactly the way an ordinary claim is — it is the same event. Taking a hold and giving
        // one up come back with the hold, which is not a voucher and is not celebrated; the
        // card redrawing itself is the whole of the feedback those two need.
        if (outcome !== null && typeof outcome === 'object' && 'voucherCode' in outcome) {
          setCelebrated(outcome as ClaimedReward)
        }
        onClaimed()
      })
      .catch((problem: Error) => setClaimError(problem.message))
      .finally(() => setHolding(null))
  }

  // What can be afforded is worked out against the balance, so nothing in the catalogue can say
  // whether it is within reach until the balance is here. Everything else on the screen can: the
  // vouchers are their own read, and a refusal stands above them rather than in place of them —
  // blanking this screen would take away the code of a voucher the customer had just been issued.
  const balance = accounts?.pointsBalance ?? null

  return (
    <section className="view">
      {accountsError !== null && <Refusal reason={accountsError} standing />}

      {accounts === null && accountsError === null && (
        <div className="card">
          <Waiting label="Loading your points…" bars={['7rem', '60%']} />
        </div>
      )}

      {/* The deadline is under the balance rather than anywhere else because it is the same figure
          read from the other end — what they can spend, and how long they have to spend it in.
          Points that vanish with no warning are indistinguishable from points gone missing. Both
          are drawn from whatever the rise is showing at this moment, so no frame of it can say more
          points are about to go than the balance beside them. */}
      {accounts !== null && (
        <p className="points-line">
          <Rising
            value={accounts.pointsBalance}
            format={(shown) => (
              <>
                You have <strong>{points.format(Math.round(shown))}</strong> points to spend.
                <ExpiringNext
                  expiring={accounts.pointsExpiringNext}
                  on={accounts.pointsExpiringNextOn}
                  outOf={Math.round(shown)}
                />
              </>
            )}
          />
        </p>
      )}

      {rewardsError !== null && <Refusal reason={rewardsError} standing />}
      {rewards === null && rewardsError === null && (
        <div className="card">
          <Waiting label="Loading rewards…" bars={['100%', '100%']} />
        </div>
      )}

      {rewards !== null && (
        <div className="flash-area">
          {celebrated !== null && <Issued claim={celebrated} />}
          <ul className="grid grid--rewards">
            {rewards.map((reward, place) => (
              <Offer
                key={reward.code}
                reward={reward}
                place={place}
                pointsToSpend={balance}
                claiming={claiming === reward.code || holding === reward.code}
                anyClaiming={claiming !== null || holding !== null}
                onClaim={() => claim(reward)}
                onHold={() => actOnAHold(reward, () => takeAHold(customerId, reward.code))}
                onClaimTheHold={() =>
                  actOnAHold(reward, () => convertAHold(customerId, reward.code))
                }
                onGiveUpTheHold={() =>
                  actOnAHold(reward, () => giveUpAHold(customerId, reward.code))
                }
                onJoinTheQueue={() =>
                  actOnAHold(reward, () => joinTheQueue(customerId, reward.code))
                }
                onLeaveTheQueue={() =>
                  actOnAHold(reward, () => leaveTheQueue(customerId, reward.code))
                }
              />
            ))}
          </ul>
        </div>
      )}

      {claimError !== null && <Refusal reason={claimError} />}

      <div className="section-head">
        <h2>Your vouchers</h2>
      </div>
      {claimedError !== null && <Refusal reason={claimedError} standing />}
      {claimed === null && claimedError === null && (
        <div className="card">
          <Waiting label="Loading your vouchers…" bars={['100%', '70%']} />
        </div>
      )}
      {claimed !== null && (
        <Claimed claimed={claimed} landedId={celebrated === null ? null : celebrated.id} />
      )}
    </section>
  )
}

/**
 * One reward, and whether the customer can have it yet.
 *
 * <p><strong>There are two kinds of "not yet" on this card and they are decided in two different
 * places.</strong> Being short of points is worked out here, from two figures already on the
 * screen, because the page has both of them and a card that waited for a round trip to grey a
 * button would flicker every time the balance ticked up. Everything else — a season that has not
 * opened, one that has closed, and the stock, the limits and the rules the slices after this one
 * add — is the backend's answer, arriving as a verdict and a sentence, because not one of them is
 * something this page could work out and every one of them is a rule somebody set in the
 * administration screen.
 *
 * <p><strong>The backend's lock wins, and it is read as a boolean rather than inferred.</strong>
 * `claimable` is the verdict; `lockedBecause` is what it is called. A page that decided
 * "locked if there is a reason" would draw a card the backend refuses the day a reason arrives
 * this page has never heard of — and three slices are about to add some. The sentence is shown
 * unchanged, and it is the same sentence the claim would be refused with, so the card and the
 * refusal cannot say two different things about one rule.
 *
 * <p>A balance that has not arrived is not a balance of nothing. With none to hold the price
 * against, the card says what the reward costs and leaves the button pressable: the backend is the
 * one that knows, and a card greyed against a figure this page does not have would be inventing a
 * refusal.
 *
 * <p>Out of reach is paler rather than hidden, and that goes double for a locked one: a reward you
 * cannot afford yet is the reason to keep saving, and a reward that opens on the third is a reason
 * to come back on the third. Hiding either would take the instruction away with the card.
 */
function Offer({
  reward,
  place,
  pointsToSpend,
  claiming,
  anyClaiming,
  onClaim,
  onHold,
  onClaimTheHold,
  onGiveUpTheHold,
  onJoinTheQueue,
  onLeaveTheQueue,
}: {
  reward: RewardForACustomer
  place: number
  pointsToSpend: number | null
  claiming: boolean
  anyClaiming: boolean
  onClaim: () => void
  onHold: () => void
  onClaimTheHold: () => void
  onGiveUpTheHold: () => void
  onJoinTheQueue: () => void
  onLeaveTheQueue: () => void
}) {
  const short = pointsToSpend === null ? 0 : reward.costInPoints - pointsToSpend
  const affordable = short <= 0
  const locked = !reward.claimable
  const withinReach = affordable && !locked
  // Held by this customer, which is the backend saying the thing is theirs for the moment. It
  // is read off the moment rather than off anything else, because the moment is the only field
  // that is about them: `claimable` is true for a held card and for an ordinary one alike.
  const held = reward.yourHoldLapsesAt !== null
  // And whether holding one is worth offering at all. Only on something scarce: a hold on an
  // offer that never runs out buys the customer nothing they did not already have, and putting
  // the button on every card would add a second action to the four rewards this application has
  // always had — which are precisely the four nothing about this feature may change.
  const worthHolding = !held && !locked && reward.whatIsLeft !== null
  // And whether they are in the queue for it, which is the other thing the backend says about
  // this customer and this card. Read off the position rather than off the lock, because the
  // lock is about the offer and this is about them — and never drawn beside a hold: a promotion
  // moves somebody out of the queue and into one, which is why the position comes back null the
  // moment the hold appears.
  const waiting = reward.yourPlaceInTheQueue !== null
  // And whether a queue is worth offering. Only on something the backend has locked as sold
  // out: a queue for anything else is refused in a sentence telling them to claim it instead,
  // and a button that was always refused would be a button that taught the customer to ignore
  // buttons. Whether this particular person may join — the window, the rules, the caps — is the
  // backend's to answer when they press, in the same words the card would have been locked with.
  const worthWaitingFor = !held && !waiting && reward.lockedBecause === 'NOTHING_LEFT'

  return (
    <li className={withinReach ? 'reward' : 'reward is-locked'} style={rowDelay(place)}>
      <span className="reward__icon" aria-hidden="true">
        <RewardIcon code={reward.code} />
      </span>
      {/* A level two, like every other card title on these screens, and like "Your vouchers"
          underneath — which is what these are: the page's heading is in the band above, and the
          catalogue and the vouchers are the two things under it. As an h3 each reward sat below a
          level that no longer exists, and somebody moving by heading went from the page's title
          straight past the whole catalogue. */}
      <h2 className="reward__title">{reward.title}</h2>
      <p className="reward__desc">{reward.description}</p>
      {/* The backend's sentence, shown as it arrived. It is the one part of the card this page
          could not have written — it names the day, and the day is the only thing the customer
          can act on. */}
      {reward.whyItIsLocked !== null && <p className="reward__lock">{reward.whyItIsLocked}</p>}
      {/* And the last day, on an offer they can still claim, because that is the date that
          decides whether saving up for it is worth starting. Not on a locked one: the sentence
          above already says what became of the window. */}
      {reward.whyItIsLocked === null && reward.closesOn !== null && (
        <p className="reward__window">Until {asADay(reward.closesOn)}</p>
      )}
      {/* And where they stand inside a limit, on a card they can still claim — because "one
          left" is what makes somebody claim this week rather than next, and a limit first
          mentioned by a refusal is a limit mentioned too late to act on. */}
      <WhatIsLeftOfYourLimit reward={reward} />
      {/* And how many are left, on a scarce one. Its own component at the foot of this file, so
          that what counts as scarce and how it is worded live in one place rather than in the
          middle of a card. */}
      <WhatIsLeft whatIsLeft={reward.whatIsLeft} />
      {/* And the countdown, on the one card being kept for this customer. Its own component at
          the foot of this file, so that what "another two days" means lives in one place. */}
      <YourHold lapsesAt={reward.yourHoldLapsesAt} />
      {/* And where they stand in the line, on the one card they are queued for. Its own
          component at the foot of this file, so that what "second in line" reads like lives in
          one place rather than in the middle of a card. */}
      <YourPlaceInTheQueue position={reward.yourPlaceInTheQueue} />
      {/* And what is in it, on a bundle — the whole of what makes one price against three
          things readable. Its own component at the foot of this file, and drawn from the list
          the backend sent rather than from a kind: an offer with contents is a bundle. */}
      <WhatIsInABundle contents={reward.contents} />
      <div className="reward__foot">
        <p className="reward__cost">
          <SparkIcon />
          {points.format(reward.costInPoints)}
          {/* And what it usually costs, struck through, when there is a sale on. Drawn by asking
              whether the backend sent a second figure rather than by comparing the two, because
              comparing them would be this page holding a copy of the pricing rule. */}
          <WhatItUsuallyCosts reward={reward} />
        </p>
        {/* A held card has two things to do with it and an ordinary one has one. Both of the
            hold's buttons are drawn, even when the customer cannot afford it yet: giving it up
            is the thing somebody short of points most needs to be able to do, and hiding it
            until they could pay would leave stock locked away by a customer who had decided
            against it. */}
        {held ? (
          <span className="reward__hold-actions">
            <Button
              tone={affordable ? 'primary' : 'ghost'}
              small
              busy={claiming}
              disabled={!affordable || anyClaiming}
              onClick={onClaimTheHold}
            >
              {claiming ? 'Claiming…' : affordable ? 'Claim it' : `${points.format(short)} to go`}
            </Button>
            <Button tone="ghost" small disabled={anyClaiming} onClick={onGiveUpTheHold}>
              Give it up
            </Button>
          </span>
        ) : waiting ? (
          /* In the queue: the only thing to do with this card is get out of the line. There is
             no claim button, because there is nothing to claim — the card is locked as sold out
             and stays locked, and what changes when their turn comes is that the sweep gives
             them a hold and this branch stops being the one that draws. */
          <span className="reward__hold-actions">
            <Button tone="ghost" small disabled={anyClaiming} onClick={onLeaveTheQueue}>
              Leave the queue
            </Button>
          </span>
        ) : (
          <span className="reward__hold-actions">
            <Button
              tone={withinReach ? 'primary' : 'ghost'}
              small
              busy={claiming}
              disabled={!withinReach || anyClaiming}
              onClick={onClaim}
            >
              {claiming
                ? 'Claiming…'
                : locked
                  ? whatALockIsCalled(reward.lockedBecause)
                  : affordable
                    ? 'Claim'
                    : `${points.format(short)} to go`}
            </Button>
            {/* And "hold it", on a scarce card only. It is a ghost beside the claim because it
                is the lesser of the two things somebody came here to do — what it buys is three
                days to think, and the card says so once it is taken. */}
            {worthHolding && (
              <Button tone="ghost" small disabled={anyClaiming} onClick={onHold}>
                Hold it
              </Button>
            )}
            {/* And "join the queue", on a sold-out card only. It is the one thing a customer
                can still do about something that has run out, which is the whole of why it is
                offered beside a button that says "Sold out" and does nothing. */}
            {worthWaitingFor && (
              <Button tone="ghost" small disabled={anyClaiming} onClick={onJoinTheQueue}>
                Join the queue
              </Button>
            )}
          </span>
        )}
      </div>
    </li>
  )
}

/**
 * The two or three words a locked button says, from the backend's own name for the lock.
 *
 * <p>Short on purpose: the whole reason is already on the card, in the backend's sentence, and a
 * button repeating it would wrap. What this has to do is say why the press is not available, in
 * the fewest words that are still true.
 *
 * <p>The fallback is not dead code and will not stay unused. Three slices after this one add
 * locks of their own, and a button that said "Locked" for a reason it had not been taught yet is
 * a page that is out of date rather than a page that is wrong — which is the whole point of the
 * sentence travelling beside the word.
 */
function whatALockIsCalled(lockedBecause: WhyAnOfferIsLocked | null): string {
  switch (lockedBecause) {
    case 'NOT_OPEN_YET':
      return 'Not open yet'
    case 'CLOSED':
      return 'Closed'
    case 'NOT_FOR_YOU':
      // Not the enum's own words, which are blunt in a way a button on a customer's screen
      // should not be. Every rule behind this lock is something the scheme wants somebody to
      // go and do — hold a run of weeks, win a badge, keep saving — and "not yet earned" says
      // that in three words. Which rule it is, and how far off they are, is the backend's
      // sentence on the card above, where there is room for it.
      return 'Not yet earned'
    case 'YOU_HAVE_HAD_YOUR_LIMIT':
      return 'Had your limit'
    case 'NOTHING_LEFT':
      return 'Sold out'
    default:
      return 'Locked'
  }
}

/**
 * The voucher, the moment it exists. It is the thing the points were spent on.
 *
 * <p>The day it runs out is said here as well as on the list underneath, and that is the whole
 * point of saying it here: a deadline a customer only meets when they go looking for it is a
 * deadline they will meet too late. It is only there for a voucher that has one — most do not —
 * and it is the backend's own date, written out rather than counted down to, because "runs out in
 * six days" is a number this page would have to keep recomputing against a clock that is not the
 * application's.
 */
function Issued({ claim }: { claim: ClaimedReward }) {
  return (
    <>
      <p className="flash">
        <TicketIcon />
        {claim.title} — {claim.voucherCode}
        {claim.expiresOn !== null && (
          <span className="flash__until">· use it by {asADay(claim.expiresOn)}</span>
        )}
      </p>
      <Confetti />
    </>
  )
}

/**
 * What the customer has spent their points on, newest first, each with the voucher it produced.
 * Drawn as stubs rather than as rows because that is what they are: the voucher is the thing the
 * customer got, and the code is the part they will be reading back off the screen.
 *
 * <p>One list for the person, not one per account: the points came out of a single pot, so which
 * account had earned them is not a question a claim can answer.
 */
function Claimed({ claimed, landedId }: { claimed: ClaimedReward[]; landedId: number | null }) {
  if (claimed.length === 0) {
    return <p className="nothing">Nothing claimed yet.</p>
  }
  return (
    <ul className="vouchers">
      {claimed.map((one, place) => (
        <li key={one.id} className={voucherDress(one, landedId)} style={rowDelay(place)}>
          <p className="voucher__title">
            <RewardIcon code={one.code} />
            {one.title}
          </p>
          <p className="voucher__code">{one.voucherCode}</p>
          <p className="voucher__meta">
            <span>{dateAndTime.format(new Date(one.claimedAt))}</span>
            <span>−{points.format(one.pointsSpent)} points</span>
          </p>
          {/* The deadline, and only while there is still something to do about it. A voucher
              that has already been used or has already run out says so on the line below, and
              telling somebody the day a dead code was going to run out would be the page
              answering a question nobody is asking. Most vouchers have no deadline at all — no
              offer this application ships sets one — so this line appears exactly when it means
              something. */}
          {one.state === 'ISSUED' && one.expiresOn !== null && (
            <p className="voucher__until">Use it by {asADay(one.expiresOn)}.</p>
          )}
          {/* Only for a voucher that is no longer good. A line under every one of them saying
              "still good" would be a page telling the customer something they can see from the
              absence of this line, and the codes they can still spend are the ones this list is
              mostly for. */}
          {one.state !== 'ISSUED' && <p className="voucher__spent">{whatBecameOf(one)}</p>}
        </li>
      ))}
    </ul>
  )
}

/**
 * How a voucher row is dressed: greyed once it is no longer good, and marked for a moment when it
 * has only just been claimed.
 *
 * <p>Both can be true of the same row and the landing mark wins, because a voucher claimed this
 * second cannot also have been used — and if a page ever manages to draw both, the one worth
 * seeing is the one that just happened.
 */
function voucherDress(claim: ClaimedReward, landedId: number | null): string {
  if (claim.id === landedId) {
    return 'voucher is-landed'
  }
  return claim.state === 'ISSUED' ? 'voucher' : 'voucher is-spent'
}

/**
 * What happened to a voucher that is no longer good, in a sentence.
 *
 * <p>The day is here and the counter is not. Where it was handed over is the counter's own record
 * and means nothing to the person who was standing there; when it went is the thing a customer
 * checks a code against. An expired voucher gets its day from a different field — the day it was
 * always going to run out, which was on their screen from the moment they claimed — and that is
 * the date they will recognise.
 *
 * <p><strong>A cancelled one says three things where the other two say one, and it needs all
 * three.</strong> Being used and running out are both things the customer did; being cancelled is
 * the scheme's own doing, and a grey row saying only "Cancelled." would be this application
 * telling somebody their voucher had stopped working and leaving it there. So the reason comes
 * through exactly as whoever revoked it typed it — there is nothing this page could write instead
 * that would be truer than a sentence a person actually wrote — and the points are named, because
 * "did I get my points back?" is the next thing anybody would ask and the answer is on this row
 * already. It is `pointsSpent`, which is the same figure that has been on the row since the day
 * they claimed: a cancellation gives back exactly what the claim cost, so there is no second
 * number to fetch and none to disagree with.
 *
 * <p>The blanks are covered rather than assumed away. A cancellation always has a reason and a
 * day — the backend requires both in the same breath as the state — but a sentence reading
 * "Cancelled null" would be worse than one that simply says it was cancelled.
 */
function whatBecameOf(claim: ClaimedReward): string {
  switch (claim.state) {
    case 'USED':
      return claim.usedAt === null
        ? 'Used.'
        : `Used ${dateAndTime.format(new Date(claim.usedAt))}.`
    case 'EXPIRED':
      return claim.expiresOn === null
        ? 'Expired.'
        : `Expired ${asADay(claim.expiresOn)}.`
    case 'CANCELLED': {
      const when =
        claim.cancelledAt === null
          ? 'Cancelled.'
          : `Cancelled ${dateAndTime.format(new Date(claim.cancelledAt))}.`
      const why =
        claim.cancelledBecause === null || claim.cancelledBecause.trim() === ''
          ? ''
          : ` ${claim.cancelledBecause}`
      return `${when}${why} ${points.format(claim.pointsSpent)} points came back.`
    }
    default:
      return ''
  }
}

/**
 * A picture for a reward, chosen by its code. Presentation and nothing else — the words beside it
 * are the backend's — and a code this page has never heard of still gets something to look at, so a
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

/**
 * The challenges tab: the season the bank is running, a card for every challenge with the customer's
 * place in it, and the trophy case underneath.
 *
 * <p>Its own three reads rather than the accounts' one, for the reason the gifts page reads its own
 * gifts: these are the tab's subject and nothing else on the screen wants them. They are read here
 * together because they answer one question between them — what is on, where do I stand, and what
 * have I won — and because the backend judges before it answers every one of them, so a badge
 * cleared by this morning's deposit is already lit the first time this screen is drawn.
 *
 * <p><strong>Nothing on it is worked out here.</strong> The reading, what the next rung still asks
 * for, whether a season is open and whether the customer is in a challenge are all the backend's
 * answers, read back after anything that could have changed them. That is not tidiness: `open` is
 * the same answer the API refuses an enrolment with, and a page that decided for itself from two
 * dates would sooner or later draw a button the backend rejects.
 *
 * <p>`onChanged` is the points pill in the bar above and the figures on the overview. A rung pays
 * into the same pot a reward is claimed from, and taking a challenge on runs the judging pass — so
 * enrolling can itself be the moment a badge is minted and points land.
 */
function Challenges({
  customerId,
  onChanged,
}: {
  customerId: number
  onChanged: () => void
}) {
  const [challenges, setChallenges] = useState<Challenge[] | null>(null)
  const [challengesError, setChallengesError] = useState<string | null>(null)
  const [achievements, setAchievements] = useState<Achievement[] | null>(null)
  const [achievementsError, setAchievementsError] = useState<string | null>(null)
  const [seasons, setSeasons] = useState<Campaign[] | null>(null)
  const [seasonsError, setSeasonsError] = useState<string | null>(null)
  // Which challenge is being joined or left rather than whether one is, so that the card that was
  // pressed is the one that shows it is working and the others simply stop being pressable.
  const [working, setWorking] = useState<string | null>(null)
  const [refusal, setRefusal] = useState<string | null>(null)
  // The badges that were not in the trophy case last time it was read. Kept only long enough to say
  // so — they are the backend's own rows, so the celebration cannot congratulate anybody for a rung
  // they did not reach.
  const [justWon, setJustWon] = useState<Achievement[]>([])
  /**
   * Everything already in the case the last time it was read, so that "new" means new to this
   * screen rather than new to this customer.
   *
   * <p>Seeded by the first read without celebrating anything, which is the difference between a
   * badge arriving and a badge being looked at: somebody opening the tab for the first time in a
   * year is owed their trophy case, not a year of confetti.
   */
  const seenAlready = useRef<Set<number> | null>(null)

  const loadChallenges = useCallback((signal?: AbortSignal) => {
    return fetchChallenges(customerId, signal)
      .then((offered) => {
        if (signal?.aborted !== true) {
          setChallenges(offered)
          setChallengesError(null)
        }
      })
      .catch((problem: Error) => {
        if (signal?.aborted !== true) {
          setChallengesError(problem.message)
        }
      })
  }, [customerId])

  const loadAchievements = useCallback((signal?: AbortSignal) => {
    return fetchAchievements(customerId, signal)
      .then((won) => {
        if (signal?.aborted === true) {
          return
        }
        setAchievements(won)
        setAchievementsError(null)
        const before = seenAlready.current
        seenAlready.current = new Set(won.map((one) => one.id))
        if (before !== null) {
          setJustWon(won.filter((one) => !before.has(one.id)))
        }
      })
      .catch((problem: Error) => {
        if (signal?.aborted !== true) {
          setAchievementsError(problem.message)
        }
      })
  }, [customerId])

  useEffect(() => {
    const request = new AbortController()
    loadChallenges(request.signal)
    loadAchievements(request.signal)
    return () => request.abort()
  }, [loadChallenges, loadAchievements])

  useEffect(() => {
    const request = new AbortController()
    fetchCampaigns(request.signal)
      .then((running) => {
        if (!request.signal.aborted) {
          setSeasons(running)
          setSeasonsError(null)
        }
      })
      .catch((problem: Error) => {
        if (!request.signal.aborted) {
          setSeasonsError(problem.message)
        }
      })
    return () => request.abort()
  }, [])

  useEffect(() => {
    if (justWon.length === 0) {
      return
    }
    const over = setTimeout(() => setJustWon([]), 2600)
    return () => clearTimeout(over)
  }, [justWon])

  /**
   * Joining and leaving are the same three steps, so they are written once: the backend is asked,
   * and then everything it may have moved is read back.
   *
   * <p>Read back rather than patched in place. Taking a challenge on runs a judging pass, which can
   * mint a badge and pay points before the answer has even returned; guessing at the new card here
   * would be this page working out a reading the backend derives from four ledgers.
   */
  function attempt(code: string, what: Promise<unknown>) {
    setWorking(code)
    setRefusal(null)
    what
      .then(() => Promise.all([loadChallenges(), loadAchievements()]))
      .then(() => onChanged())
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setWorking(null))
  }

  const openSeasons = seasons === null ? [] : seasons.filter((season) => season.open)

  return (
    <section className="view">
      {seasonsError !== null && <Refusal reason={seasonsError} standing />}
      {openSeasons.map((season) => (
        <SeasonBanner key={season.code} season={season} />
      ))}

      {challengesError !== null && <Refusal reason={challengesError} standing />}
      {challenges === null && challengesError === null && (
        <div className="card">
          <Waiting label="Loading your challenges…" bars={['100%', '80%', '60%']} />
        </div>
      )}

      {challenges !== null && (
        <div className="flash-area">
          {justWon.length > 0 && <JustWon won={justWon} />}
          <ul className="challenges">
            {challenges.map((challenge, place) => (
              <ChallengeCard
                key={challenge.code}
                challenge={challenge}
                place={place}
                working={working === challenge.code}
                anyWorking={working !== null}
                onTakeOn={() => attempt(challenge.code, enrolInChallenge(customerId, challenge.code))}
                onLeave={() => attempt(challenge.code, leaveChallenge(customerId, challenge.code))}
              />
            ))}
          </ul>
        </div>
      )}

      {refusal !== null && <Refusal reason={refusal} />}

      <div className="section-head">
        <h2>Your trophy case</h2>
      </div>
      {achievementsError !== null && <Refusal reason={achievementsError} standing />}
      {achievements === null && achievementsError === null && (
        <div className="card">
          <Waiting label="Loading what you have won…" bars={['100%', '70%']} />
        </div>
      )}
      {achievements !== null && (
        <TrophyCase
          won={achievements}
          landed={new Set(justWon.map((one) => one.id))}
          // What each challenge counts in, so an old badge's reading can be printed as the euros,
          // weeks, days or goals it actually was. A badge carries the code of its challenge and not
          // its kind, because an award outlives the bank offering the challenge — so a badge whose
          // challenge is no longer on this tab falls back to the bare figure rather than guessing.
          kinds={
            new Map((challenges ?? []).map((challenge) => [challenge.code, challenge.kind]))
          }
        />
      )}
    </section>
  )
}

/**
 * The season, while it is on, with the day it closes.
 *
 * <p>Only while it is on, and `open` is the backend's word for that. A season that has finished is
 * still on the card of every challenge that belonged to it — that is where "you missed it" is said —
 * but a banner across the top of the tab is an invitation, and inviting somebody to something that
 * closed last month is worse than saying nothing.
 */
function SeasonBanner({ season }: { season: Campaign }) {
  return (
    <section className="season reveal" aria-labelledby={`season-${season.code}`}>
      <p className="season__eyebrow">
        <SparkIcon />
        The season is on
      </p>
      <h2 className="season__title" id={`season-${season.code}`}>
        {season.title}
      </h2>
      <p className="season__when">
        Open until <strong>{asADay(season.closesOn)}</strong>. What you reach by then is yours to
        keep; anything you have not finished simply lapses.
      </p>
      {season.challenges.length > 0 && (
        <p className="season__what">
          In it: {season.challenges.map((challenge) => challenge.title).join(', ')}.
        </p>
      )}
    </section>
  )
}

/**
 * The badge that landed while the customer was looking at this screen, thrown up over the cards.
 *
 * <p>One pill however many arrived, because they are all announced in the same place and two of
 * them would be drawn on top of each other. The newest is named and the rest are counted: every one
 * of them is in the trophy case below, lit, which is where somebody goes to read them properly.
 */
function JustWon({ won }: { won: Achievement[] }) {
  const newest = won[0]
  return (
    <>
      <p className="flash">
        <TrophyIcon />
        {RUNG_WORDS[newest.rung]} — {newest.title}, {points.format(newest.points)} points
        {won.length > 1 && ` and ${won.length - 1} more`}
      </p>
      <Confetti />
    </>
  )
}

/** Each rung in the one word this page shows it as. */
const RUNG_WORDS: Record<Rung, string> = {
  BRONZE: 'Bronze',
  SILVER: 'Silver',
  GOLD: 'Gold',
}

/**
 * Each state of an enrolment in the one word this page shows it as, with "never joined" among them
 * as `none`.
 *
 * <p>`EXPIRED` and `ABANDONED` get different words because they are different things that happened.
 * The season running out is not the customer walking away, and the one word that would cover both —
 * "over" — would be the application quietly blaming somebody for a date.
 */
const ENROLMENT_WORDS: Record<EnrolmentState | 'none', string> = {
  none: 'Not taken on',
  ACTIVE: 'Under way',
  COMPLETED: 'Finished',
  ABANDONED: 'You left it',
  EXPIRED: 'The season ran out',
}

/**
 * The class that colours each state, so no two of them are told apart by their wording alone.
 *
 * <p>Green for the one that was finished, blue for the one that is running, and two different
 * quiet looks for the two that ended unfinished: a faded, broken edge for a challenge the customer
 * walked away from, and amber — the colour this application uses for a deadline — for one the
 * season closed underneath them. A challenge nobody has joined is plainer than any of them, because
 * nothing has happened to it.
 */
const ENROLMENT_TONES: Record<EnrolmentState | 'none', string> = {
  none: 'untaken',
  ACTIVE: 'running',
  COMPLETED: 'finished',
  ABANDONED: 'left',
  EXPIRED: 'lapsed',
}

/** The mark beside the word, so the state is legible before it is read. */
function EnrolmentMark({ state }: { state: EnrolmentState | 'none' }) {
  switch (state) {
    case 'ACTIVE':
      return <RisingIcon />
    case 'COMPLETED':
      return <TickIcon />
    // The customer's own decision, and the one mark on the list that says nothing is happening here.
    case 'ABANDONED':
      return <ClosedIcon />
    // A clock, because what ended this was a date rather than anything the customer did.
    case 'EXPIRED':
      return <ClockIcon />
    case 'none':
      return <OpenEndedIcon />
  }
}

/**
 * A picture for a challenge, chosen by the question it asks. Presentation and nothing else — the
 * words beside it are the backend's — and the switch is total, so a sixth kind stops this compiling
 * rather than drawing an empty square.
 */
function ChallengeIcon({ kind }: { kind: ChallengeKind }) {
  switch (kind) {
    case 'NEW_SAVINGS':
      return <RisingIcon />
    case 'SECURED_WEEKS':
      return <FlameIcon />
    case 'BALANCE_REACHED':
      return <PotIcon />
    case 'BALANCE_HELD':
      return <ClockIcon />
    case 'GOALS_COMPLETED':
      return <TickIcon />
  }
}

/**
 * A figure in whatever the challenge counts in.
 *
 * <p>The thresholds and the reading are plain numbers and mean five different things: euros on the
 * two kinds that ask about money, and weeks, days or goals on the three that do not. Nothing on this
 * screen prints one of them without coming through here, because "90" beside a euro sign is a
 * different promise from ninety days.
 */
function inTheUnitsOf(kind: ChallengeKind, figure: number): string {
  switch (kind) {
    case 'NEW_SAVINGS':
    case 'BALANCE_REACHED':
      return euros.format(figure)
    case 'SECURED_WEEKS':
      return inWeeks(figure)
    case 'BALANCE_HELD':
      return figure === 1 ? '1 day' : `${points.format(figure)} days`
    case 'GOALS_COMPLETED':
      return figure === 1 ? '1 goal' : `${points.format(figure)} goals`
  }
}

/** What the reading on this kind of challenge is a reading of, as the label above the figure. */
function whatTheReadingIs(kind: ChallengeKind): string {
  switch (kind) {
    case 'NEW_SAVINGS':
      return 'Put away since you joined'
    case 'SECURED_WEEKS':
      return 'Weeks secured since you joined'
    case 'BALANCE_REACHED':
      return 'Holding right now'
    case 'BALANCE_HELD':
      return 'Held without dipping'
    case 'GOALS_COMPLETED':
      return 'Goals finished since you joined'
  }
}

/**
 * What a withdrawal does to this challenge, in the customer's words, on every card.
 *
 * <p>**The most important sentence on the screen, and it is different for every kind.** A
 * withdrawal stops a holding challenge dead and leaves a saving challenge exactly where it was.
 * That is not an inconsistency — one is about money being there and the other about money having
 * been put there — but it is only obvious to somebody who already knows, and a customer who thinks
 * using their savings will cost them their progress stops using their savings. An application that
 * makes people afraid of their own money has failed at the thing it is for, so each card says the
 * truth about itself rather than one comfortable sentence about all five.
 *
 * <p>Every one of them ends the same way for the same reason: nothing already won is ever taken
 * back, whatever happens to the money afterwards.
 */
function whatAWithdrawalDoes(kind: ChallengeKind): string {
  switch (kind) {
    case 'NEW_SAVINGS':
      return (
        'Taking money out costs you nothing here. This counts what you have put away, not what ' +
        'you are holding — though paying the same euros back in afterwards does not count them a ' +
        'second time.'
      )
    case 'SECURED_WEEKS':
      return (
        'Taking money out in the same week can cost you that week, because a week counts what is ' +
        'left after anything you took back out of it. Weeks you have already secured stay ' +
        'secured, and so do the rungs they won you.'
      )
    case 'BALANCE_REACHED':
      return (
        'Taking money out lowers this straight away: it counts what you are holding right now, ' +
        'not what you have ever put away. Rungs you have already reached stay yours.'
      )
    case 'BALANCE_HELD':
      return (
        'Taking money out below the floor stops this one dead and the days start again from ' +
        'nothing — even for a day, and even if you put it straight back. Rungs you have already ' +
        'reached stay yours.'
      )
    case 'GOALS_COMPLETED':
      return (
        'Taking money out costs you nothing here. A goal you have finished stays counted, even if ' +
        'you spend what was in it afterwards.'
      )
  }
}

/**
 * One challenge, and where this customer stands in it: the words, the ladder with its won rungs
 * lit, a bar against the rung above, what a withdrawal does to it, and the one control that joins
 * or leaves.
 *
 * <p><strong>The five standings are told apart three ways over</strong> — the word in the chip, the
 * mark beside it and the colour of the card's edge — for the reason a goal's status is: a colour
 * alone is no answer to somebody who cannot see it. `EXPIRED` in particular is neither of the two it
 * could be mistaken for: the season ran out, which is not finishing and is not leaving.
 *
 * <p><strong>The season decides whether joining is offered, and the backend decides the season.</strong>
 * A card whose season is shut says so and greys its button; a card with no season at all is
 * evergreen and always open, and drawing a countdown over one would be inventing a deadline. Every
 * other refusal — a one-off already finished, a challenge already joined — is the backend's to make,
 * and its sentence is shown exactly as it wrote it.
 */
function ChallengeCard({
  challenge,
  place,
  working,
  anyWorking,
  onTakeOn,
  onLeave,
}: {
  challenge: Challenge
  place: number
  working: boolean
  anyWorking: boolean
  onTakeOn: () => void
  onLeave: () => void
}) {
  const state: EnrolmentState | 'none' = challenge.state ?? 'none'
  const tone = ENROLMENT_TONES[state]
  const season = challenge.season
  // The backend's answer, never worked out from the two dates: it is the same answer it refuses an
  // enrolment with, and a second opinion here would eventually disagree with it.
  const theSeasonIsShut = season !== null && !season.open
  const doneForGood = challenge.state === 'COMPLETED' && !challenge.repeatable
  const next =
    challenge.nextRung === null
      ? null
      : (challenge.rungs.find((rung) => rung.rung === challenge.nextRung) ?? null)
  const howFarAlong =
    challenge.reading === null || next === null || next.threshold <= 0
      ? challenge.state === 'COMPLETED'
        ? 100
        : 0
      : Math.min(100, Math.max(0, (challenge.reading / next.threshold) * 100))

  return (
    <li className={`chal chal--${tone}`} style={rowDelay(place)}>
      <div className="chal__top">
        <span className="chal__icon" aria-hidden="true">
          <ChallengeIcon kind={challenge.kind} />
        </span>
        <div className="chal__what">
          <h3 className="chal__name">{challenge.title}</h3>
          <p className="chal__meta">
            {season === null ? (
              'Always open'
            ) : season.open ? (
              <>
                {season.title} · closes {asADay(season.closesOn)}
              </>
            ) : (
              <>
                {season.title} ·{' '}
                {/* Two shut seasons and two different sentences. One has finished and one has not
                    started, and "not available" would cover both while telling neither. */}
                {new Date(`${season.opensOn}T00:00:00`) > new Date()
                  ? `opens ${asADay(season.opensOn)}`
                  : `closed ${asADay(season.closesOn)}`}
              </>
            )}
            {' · '}
            {challenge.repeatable ? 'you can take it on again' : 'once only'}
          </p>
        </div>
        <span className={`chal__status chal__status--${tone}`}>
          <EnrolmentMark state={state} />
          {ENROLMENT_WORDS[state]}
        </span>
      </div>

      <p className="chal__words">{challenge.words}</p>

      <RungLadder rungs={challenge.rungs} kind={challenge.kind} />

      {/* The bar is only drawn against a rung there is one of. An enrolment that is over reports no
          reading at all, and a bar over nothing would be a figure this page had invented. */}
      {challenge.reading !== null && (
        <div className="progress" aria-hidden="true">
          <span className="progress__fill" style={{ width: `${howFarAlong}%` }} />
        </div>
      )}

      <dl className="split chal__facts">
        {challenge.reading !== null && (
          <div>
            <dt>{whatTheReadingIs(challenge.kind)}</dt>
            <dd>{inTheUnitsOf(challenge.kind, challenge.reading)}</dd>
          </div>
        )}
        {/* Three different silences, and each says something. No enrolment at all is a challenge
            nobody has started; an enrolment that is over has no reading because nothing about it is
            stored; and a live one with no rung above it has cleared the lot. */}
        {next !== null && challenge.stillNeeded !== null ? (
          <div>
            <dt>{RUNG_WORDS[next.rung]} still asks for</dt>
            <dd>{inTheUnitsOf(challenge.kind, challenge.stillNeeded)}</dd>
          </div>
        ) : challenge.state === null ? (
          <div>
            <dt>Where you stand</dt>
            <dd>Nothing counted yet — it starts when you take it on</dd>
          </div>
        ) : challenge.reading === null ? (
          <div>
            <dt>Where you stand</dt>
            <dd>
              {challenge.state === 'EXPIRED'
                ? 'The season closed on this one. What you reached is still yours.'
                : 'You left this one. What you reached is still yours.'}
            </dd>
          </div>
        ) : (
          <div>
            <dt>Where you stand</dt>
            <dd>Every rung cleared.</dd>
          </div>
        )}
      </dl>

      <p className="chal__withdrawal">
        <ArrowDownIcon />
        <span>{whatAWithdrawalDoes(challenge.kind)}</span>
      </p>

      <div className="chal__actions">
        {challenge.enrolled ? (
          <Button tone="ghost" small busy={working} disabled={anyWorking} onClick={onLeave}>
            {working ? 'Leaving…' : 'Leave it'}
          </Button>
        ) : (
          <Button
            small
            tone={theSeasonIsShut || doneForGood ? 'ghost' : 'primary'}
            busy={working}
            disabled={anyWorking || theSeasonIsShut || doneForGood}
            onClick={onTakeOn}
          >
            {working
              ? 'Taking it on…'
              : doneForGood
                ? 'Finished for good'
                : theSeasonIsShut
                  ? 'Not open'
                  : challenge.state === null
                    ? 'Take it on'
                    : 'Take it on again'}
          </Button>
        )}
      </div>
    </li>
  )
}

/**
 * The three rungs of a challenge, drawn as a row of bars with the ones this enrolment has won lit —
 * the same picture the streak's own rungs make on the navy card, because it is the same idea and a
 * second vocabulary for it would make two things look different that are not.
 *
 * <p>Each bar carries what its rung asks for and what it pays underneath, which the streak's rungs
 * do not need and this cannot do without: a customer judging whether a challenge is worth their
 * while is comparing a threshold against a number of points.
 *
 * <p>A lit rung says when it was won. Lit means won <em>on the enrolment this card is about</em>, so
 * a repeatable challenge taken on a second time starts dark again however full the trophy case is —
 * the second round asks for the whole of it again, and a ladder that said otherwise would be
 * flattering somebody who has just started.
 *
 * <p>The row is hidden from a screen reader because the list underneath it says the same three
 * facts in words, and hearing each rung twice is worse than hearing it once.
 */
function RungLadder({ rungs, kind }: { rungs: ChallengeRung[]; kind: ChallengeKind }) {
  return (
    <ol className="ladder">
      {rungs.map((rung) => (
        <li key={rung.rung} className={rung.wonAt === null ? 'ladder__rung' : 'ladder__rung is-on'}>
          <span className="ladder__bar" aria-hidden="true" />
          <span className="ladder__name">{RUNG_WORDS[rung.rung]}</span>
          <span className="ladder__asks">{inTheUnitsOf(kind, rung.threshold)}</span>
          <span className="ladder__pays">
            <SparkIcon />
            {points.format(rung.points)}
          </span>
          {rung.wonAt !== null && (
            <span className="ladder__won">Won {dateOnly.format(new Date(rung.wonAt))}</span>
          )}
        </li>
      ))}
    </ol>
  )
}

/**
 * The reading a badge was won at, in the units of the challenge it was won in.
 *
 * <p>A badge carries the code of its challenge rather than its kind, because it outlives the bank
 * offering the challenge: the trophy case goes on listing something won in a challenge that is no
 * longer on the tab. So the units are looked up among the challenges on offer, and a badge whose
 * challenge is not among them is printed as the bare figure the award recorded rather than as euros
 * this page has decided it must have been.
 */
function theReadingOf(won: Achievement, kinds: Map<string, ChallengeKind>): string {
  const kind = kinds.get(won.challenge)
  return kind === undefined ? points.format(won.reading) : inTheUnitsOf(kind, won.reading)
}

/**
 * Everything this customer has ever won, newest first, each with the day it was won, the reading
 * that won it and what it paid.
 *
 * <p>A history rather than a standing, which is why it is a list of its own underneath the cards
 * and not something read off them. Nothing in it is ever revoked, recomputed or expired: a badge
 * from a challenge the customer has since left, or one the bank no longer offers, goes on saying
 * exactly what it said on the day.
 */
function TrophyCase({
  won,
  landed,
  kinds,
}: {
  won: Achievement[]
  landed: Set<number>
  kinds: Map<string, ChallengeKind>
}) {
  if (won.length === 0) {
    return <p className="nothing">Nothing won yet. Take a challenge on and the first rung is close.</p>
  }
  return (
    <ul className="trophies">
      {won.map((one, place) => (
        <li
          key={one.id}
          className={
            landed.has(one.id)
              ? `trophy trophy--${one.rung.toLowerCase()} is-landed`
              : `trophy trophy--${one.rung.toLowerCase()}`
          }
          style={rowDelay(place)}
        >
          <span className="trophy__medal" aria-hidden="true">
            <TrophyIcon />
          </span>
          <div className="trophy__body">
            <p className="trophy__title">
              {RUNG_WORDS[one.rung]} — {one.title}
            </p>
            <p className="trophy__meta">
              <span>{dateAndTime.format(new Date(one.awardedAt))}</span>
              {/* The figure recorded on the day rather than anything worked out now, which is what
                  lets an old badge explain itself. It is printed in the challenge's own units, and
                  the code is the only thing a badge carries about a challenge the bank may since
                  have withdrawn. */}
              <span>won at {theReadingOf(one, kinds)}</span>
            </p>
          </div>
          <p className="trophy__points">
            <SparkIcon />+{points.format(one.points)}
          </p>
        </li>
      ))}
    </ul>
  )
}

/**
 * One current account: what is in it, and what its holder says arrives in it every month.
 *
 * <p>A screen of its own, drilled into from the card on the overview, because a current account has
 * become something a customer decides about rather than a number they read. What arrives is declared
 * here and so is what leaves, in that order down the page, because that is the order the money moves
 * in: the salary lands, the bills are presented against it, and what is left is what there is to
 * save. What is still owed is the section the slice after this one adds. There is no router, so this
 * page is reached by pressing the card and a reload comes back to the overview — the bargain every
 * screen in this application strikes.
 *
 * <p>Nothing on it is computed here. The balance and the declaration are one read of the account's
 * own resource, and what declaring an income did is the backend's own answer, put back in state as
 * it arrived.
 *
 * <p><strong>Only the customer's own accounts are drawn.</strong> The identifier this page is opened
 * with is checked against the accounts the signed-in customer holds before anything is read, so an
 * account belonging to somebody else is refused in words rather than fetched and drawn. Pressing a
 * card is the only way in and every card is one of theirs, which makes this a belt to that braces:
 * it is the check that would still hold if a second way in were ever added.
 */
function CurrentAccountPage({
  currentAccountId,
  currentAccounts,
  accountsError,
  onBack,
  onOpenBudget,
}: {
  currentAccountId: number
  currentAccounts: CurrentAccount[] | null
  accountsError: string | null
  onBack: () => void
  onOpenBudget: () => void
}) {
  const [account, setAccount] = useState<TheCurrentAccount | null>(null)
  const [accountError, setAccountError] = useState<string | null>(null)
  // Read on its own rather than with the account, because it is a different question: the standing
  // bills are a claim on the balance beside them and come down with it, while what has been ended
  // is a record and is only worth fetching for the section that shows it.
  const [endedBills, setEndedBills] = useState<RecurringBill[] | null>(null)
  const [endedBillsError, setEndedBillsError] = useState<string | null>(null)
  // The labels on the bills, and the words available to label them with. Two reads from the budgets
  // module beside the account's own, joined on the identifier by the page rather than by either
  // module: the bills belong to one module and the categories to another, and the whole design of
  // this feature is that neither of them knows the other exists.
  const [billCategories, setBillCategories] = useState<BillInACategory[] | null>(null)
  const [categories, setCategories] = useState<SpendingCategory[] | null>(null)

  // Three answers rather than two: theirs, not theirs, and the customer's accounts have not arrived
  // yet. Only the middle one is a refusal — treating "not read yet" as "not yours" would refuse
  // every customer their own account for as long as the first read takes.
  const theirs =
    currentAccounts === null ? null : currentAccounts.some((held) => held.id === currentAccountId)

  const loadAccount = useCallback(
    (signal?: AbortSignal) => {
      fetchCurrentAccount(currentAccountId, signal)
        .then((held) => {
          if (signal?.aborted !== true) {
            setAccount(held)
            setAccountError(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true) {
            setAccountError(problem.message)
          }
        })
    },
    [currentAccountId],
  )

  const loadEndedBills = useCallback(
    (signal?: AbortSignal) => {
      fetchEndedBills(currentAccountId, signal)
        .then((ended) => {
          if (signal?.aborted !== true) {
            setEndedBills(ended)
            setEndedBillsError(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true) {
            setEndedBillsError(problem.message)
          }
        })
    },
    [currentAccountId],
  )

  // No refusal is kept for either of these. A label is an extra line on a bill rather than the
  // reason the page exists, and an account screen that refused to draw the rent because the budgets
  // module could not be reached would be the tail wagging the dog — the bills draw with no label,
  // which is what a bill that has never been filed draws as anyway.
  const loadBillCategories = useCallback(
    (signal?: AbortSignal) => {
      fetchBillCategories(currentAccountId, signal)
        .then((filed) => {
          if (signal?.aborted !== true) {
            setBillCategories(filed)
          }
        })
        .catch(() => undefined)
    },
    [currentAccountId],
  )

  const loadCategories = useCallback(
    (signal?: AbortSignal) => {
      fetchCategories(currentAccountId, signal)
        .then((named) => {
          if (signal?.aborted !== true) {
            setCategories(named)
          }
        })
        .catch(() => undefined)
    },
    [currentAccountId],
  )

  // How this month is going, for the card at the foot of the page. No refusal is kept, for the
  // reason the labels' is not: this screen is about the balance, what arrives and what goes out,
  // and an account page that refused to draw the rent because the budgets module could not be
  // reached would be the tail wagging the dog. The card is simply absent instead, and the whole
  // list is one press away on the budget screen, which does keep its refusal.
  const [month, setMonth] = useState<MonthOfSpending | null>(null)

  const loadMonth = useCallback(
    (signal?: AbortSignal) => {
      fetchThisMonthsSpending(currentAccountId, signal)
        .then((going) => {
          if (signal?.aborted !== true) {
            setMonth(going)
          }
        })
        .catch(() => undefined)
    },
    [currentAccountId],
  )

  useEffect(() => {
    if (theirs === false) {
      return
    }
    const request = new AbortController()
    loadAccount(request.signal)
    loadEndedBills(request.signal)
    loadBillCategories(request.signal)
    loadCategories(request.signal)
    loadMonth(request.signal)
    return () => request.abort()
  }, [loadAccount, loadBillCategories, loadCategories, loadEndedBills, loadMonth, theirs])

  // Both halves again after anything was declared, changed or ended. The answer to the request is
  // one bill and what changed is two lists — a bill ended leaves the standing one and joins the
  // ended one — so the page asks the backend what it now holds rather than patching its own copy
  // into what it guesses the backend would say.
  function billsChanged() {
    loadAccount()
    loadEndedBills()
    // The labels too, because ending a bill leaves its label exactly where it was and the ended
    // section below draws it: a page that read the bills again and not the labels would show the
    // bill it has just ended with the category stripped off it, which is the opposite of what the
    // backend did.
    loadBillCategories()
    // And the budget card, because a bill moved into another category moves every euro it has ever
    // taken with it: the committed half of two categories' months changes although no money did.
    loadMonth()
  }

  // And the whole account again after the income was declared, changed or withdrawn, for the same
  // reason and a sharper one. A declaration is not only the box it was typed into: "The month
  // ahead" above it counts the paydays still to fall, so a page that patched its own copy of the
  // declaration and left the rest standing would go on promising a salary the customer has just
  // withdrawn — the card would offer room to sweep that is not there, which is the one thing this
  // screen exists not to do. The answer to the request still goes in first, so the section the
  // customer typed into shows what was written down without waiting on the second read; the read
  // that follows is what brings the figures derived from it back into agreement with it.
  function incomeChanged(declared: MonthlyIncome) {
    setAccount((held) => (held === null ? held : { ...held, income: declared }))
    loadAccount()
  }

  return (
    <section className="view">
      <button type="button" className="link link--back" onClick={onBack}>
        <BackIcon />
        All accounts
      </button>

      {/* The customer-level read, which is what says whose accounts these are. When it fails this
          page cannot tell its own account from somebody else's, so the backend's own sentence goes
          here and the page carries on with what it can read. */}
      {accountsError !== null && <Refusal reason={accountsError} standing />}

      {theirs === false ? (
        // The one sentence on this screen that is not the backend's. There is no endpoint to ask —
        // this application has no authentication and every request names its account by identifier —
        // so whose an account is, is answered from the accounts the signed-in customer holds, which
        // is the only place that answer exists on this side.
        <Refusal reason={`Current account ${currentAccountId} is not one of yours.`} standing />
      ) : (
        <>
          {accountError !== null && <Refusal reason={accountError} standing />}

          {account === null && accountError === null && (
            <div className="card">
              <Waiting label="Loading the account…" bars={['9rem', '100%', '60%']} />
            </div>
          )}

          {account !== null && (
            <article className="card reveal">
              {/* The account's own number is the headline in the band above, so this card leads
                  with what the band cannot carry: who the backend says holds it, and the IBAN the
                  customer actually knows the account by. */}
              <div className="sheet__head">
                <span className="acc__icon" aria-hidden="true">
                  <BankIcon />
                </span>
                <h2 className="card__title">Held by {account.customerName}</h2>
              </div>
              <p className="amount amount--xl">{euros.format(account.balance)}</p>
              <p className="acc__iban">{spacedIban(account.iban)}</p>
            </article>
          )}

          {/* Directly under the balance, because it is what the balance means. The three sections
              below it are the inputs — what arrives, what goes out, what is already owed — and a
              customer who reads no further than this card has still been told the one thing this
              screen exists to tell them. */}
          {account !== null && <TheMonthAhead month={account.monthAhead} />}

          <div className="section-head">
            <h2>Money arriving</h2>
          </div>
          {account !== null && (
            <WhatYouArePaid
              currentAccountId={currentAccountId}
              income={account.income}
              onDeclared={incomeChanged}
            />
          )}

          {/* Under what arrives, because that is the order the money moves in: the salary lands,
              the bills are presented against it, and what is left is what there is to save. */}
          <div className="section-head">
            <h2>Money going out</h2>
          </div>
          {account !== null && (
            <MoneyGoingOut
              currentAccountId={currentAccountId}
              bills={account.bills}
              endedBills={endedBills}
              endedBillsError={endedBillsError}
              categories={categories}
              billCategories={billCategories}
              onChanged={billsChanged}
              onFiled={loadBillCategories}
            />
          )}

          {/* Last, under what goes out, because that is where it comes from: a bill was presented
              against what the saving rules left behind and could not be paid. Absent rather than
              empty when nothing is owed — a heading that is there every day is a heading people
              learn to skip, and this one has something to say exactly when it appears. */}
          {account !== null && account.arrears.length > 0 && (
            <StillOwed arrears={account.arrears} />
          )}

          {/* Under what is still owed, because that is the order the money moves in: the salary
              lands, the standing bills and the arrears are presented against it, and what is left
              is the part of the month nobody writes down in advance. The way through to it is a
              card rather than a tab: the budget is about this account's money, and a customer
              should find it on the page that shows that money rather than by remembering where it
              was. */}
          <div className="section-head">
            <h2>Your budget</h2>
            <button type="button" className="link link--small" onClick={onOpenBudget}>
              What your money goes on
              <ForwardIcon />
            </button>
          </div>
          {month !== null && <TheBudgetThisMonth month={month} />}
        </>
      )}
    </section>
  )
}

/**
 * The budget screen: the things one current account's money goes on, in its holder's own words.
 *
 * <p>A screen of its own rather than another section on the current account page, and drilled into
 * from a card there. The account's page is about what arrives, what goes out on a standing
 * instruction and what that leaves; this is about the part of the month nobody declares in advance,
 * and it is where the budgets, the spends, the weeks ahead and the comparison with the months
 * before it will all hang. Putting that behind one press keeps the account's own page the short
 * answer it is.
 *
 * <p>Keyed on the current account, like the page it is opened from, because a household in this
 * application <em>is</em> a current account: a budget beside a balance it does not match would be
 * two figures about two different things on one screen.
 *
 * <p>Whose the account is, is answered here as well as on the page behind it. There is no endpoint
 * to ask — this application has no authentication and every request names its account by identifier
 * — so it is checked against the accounts the signed-in customer holds, which is the only place that
 * answer exists on this side. Three answers rather than two: theirs, not theirs, and the customer's
 * accounts have not arrived yet.
 *
 * <p>Nothing on it is computed here. The two lists are read back from the backend after anything is
 * declared, renamed or ended, because what changed is both of them at once — a category ended leaves
 * the standing list and joins the ended one — and a page that patched its own copy would be
 * guessing at what the backend would say.
 */
function BudgetPage({
  currentAccountId,
  currentAccounts,
  accountsError,
  onBack,
}: {
  currentAccountId: number
  currentAccounts: CurrentAccount[] | null
  accountsError: string | null
  onBack: () => void
}) {
  const [categories, setCategories] = useState<SpendingCategory[] | null>(null)
  const [categoriesError, setCategoriesError] = useState<string | null>(null)
  // Read on its own rather than with the standing ones, because it is a different question: what a
  // customer is describing their money with is the page, and what they have stopped using is a
  // record, worth fetching for the section that shows it.
  const [ended, setEnded] = useState<SpendingCategory[] | null>(null)
  const [endedError, setEndedError] = useState<string | null>(null)
  // What the account has actually spent lately, newest first. A third read rather than anything
  // worked out from the two above it: a category is a word, and a spend is money that left — the
  // backend is the only thing that knows which words the euros were filed under.
  const [spends, setSpends] = useState<RecordedSpend[] | null>(null)
  const [spendsError, setSpendsError] = useState<string | null>(null)
  // What this account is committed to, and which category each of those commitments sits in. The
  // bills come from the account's own read because that is where a standing bill lives; the labels
  // come from this module. Neither module knows about the other and this page joins them on the
  // identifier, which is what a category's committed half is made of.
  const [bills, setBills] = useState<RecurringBill[] | null>(null)
  const [billCategories, setBillCategories] = useState<BillInACategory[] | null>(null)
  // How this month is going: the account's totals and a row per category, derived by the backend on
  // every read from the declarations and the movements. A fifth read rather than arithmetic over the
  // four above it — the categories say what the words are and the spends say where the euros went,
  // and only the backend knows which months a figure governed and which bills were actually taken.
  const [month, setMonth] = useState<MonthOfSpending | null>(null)
  const [monthError, setMonthError] = useState<string | null>(null)
  // And the same records over a longer window: the last six months, category by category, with the
  // average behind them. A sixth read rather than six reads of the one above it — one month says
  // whether a figure was kept to and cannot say whether the month was unusual, which is the
  // judgement somebody deciding whether to change their budget actually needs.
  const [history, setHistory] = useState<SpendingHistory | null>(null)
  const [historyError, setHistoryError] = useState<string | null>(null)
  // The next six weeks, which is the figure this whole screen builds up to. A read of its own and
  // not the month above it: that one is a month of spending already recorded, and this is a
  // forecast made out of two calendars the nightly runs walk as well as these budgets. They share
  // the budgets and nothing else, and the backend is the only thing that knows which day a salary
  // lands on.
  const [weeks, setWeeks] = useState<TheWeeksAhead | null>(null)
  const [weeksError, setWeeksError] = useState<string | null>(null)

  const theirs =
    currentAccounts === null ? null : currentAccounts.some((held) => held.id === currentAccountId)

  const loadCategories = useCallback(
    (signal?: AbortSignal) => {
      fetchCategories(currentAccountId, signal)
        .then((named) => {
          if (signal?.aborted !== true) {
            setCategories(named)
            setCategoriesError(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true) {
            setCategoriesError(problem.message)
          }
        })
    },
    [currentAccountId],
  )

  const loadEnded = useCallback(
    (signal?: AbortSignal) => {
      fetchEndedCategories(currentAccountId, signal)
        .then((over) => {
          if (signal?.aborted !== true) {
            setEnded(over)
            setEndedError(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true) {
            setEndedError(problem.message)
          }
        })
    },
    [currentAccountId],
  )

  const loadSpends = useCallback(
    (signal?: AbortSignal) => {
      fetchSpends(currentAccountId, signal)
        .then((recorded) => {
          if (signal?.aborted !== true) {
            setSpends(recorded)
            setSpendsError(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true) {
            setSpendsError(problem.message)
          }
        })
    },
    [currentAccountId],
  )

  // No refusal is kept for either of these. The bills and their labels are what a category has
  // committed to it, and a budget screen that refused to draw the words a customer named because
  // their bills could not be read would be withholding the part it can answer for.
  const loadBills = useCallback(
    (signal?: AbortSignal) => {
      fetchCurrentAccount(currentAccountId, signal)
        .then((held) => {
          if (signal?.aborted !== true) {
            setBills(held.bills)
          }
        })
        .catch(() => undefined)
    },
    [currentAccountId],
  )

  const loadBillCategories = useCallback(
    (signal?: AbortSignal) => {
      fetchBillCategories(currentAccountId, signal)
        .then((filed) => {
          if (signal?.aborted !== true) {
            setBillCategories(filed)
          }
        })
        .catch(() => undefined)
    },
    [currentAccountId],
  )

  // This one keeps its refusal, unlike the two above it. The figures are the reason a customer
  // opened this screen — a page that quietly drew the words with no figures against them would be
  // showing a budget that says nothing, which is worse than saying why.
  const loadMonth = useCallback(
    (signal?: AbortSignal) => {
      fetchThisMonthsSpending(currentAccountId, signal)
        .then((going) => {
          if (signal?.aborted !== true) {
            setMonth(going)
            setMonthError(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true) {
            setMonthError(problem.message)
          }
        })
    },
    [currentAccountId],
  )

  // This one keeps its refusal too. A customer who cannot tell a bad month from a habit is being
  // shown half of what they came for, and a section that quietly disappeared would leave them
  // wondering whether they had no history or the read had failed.
  const loadHistory = useCallback(
    (signal?: AbortSignal) => {
      fetchSpendingHistory(currentAccountId, signal)
        .then((behind) => {
          if (signal?.aborted !== true) {
            setHistory(behind)
            setHistoryError(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true) {
            setHistoryError(problem.message)
          }
        })
    },
    [currentAccountId],
  )

  // This one keeps its refusal too, and for a sharper reason than the month does: the six weeks are
  // the figure a customer came here to read before deciding what to sweep into savings, and a page
  // that drew nothing where they should be would look like an account with nothing coming.
  const loadWeeks = useCallback(
    (signal?: AbortSignal) => {
      fetchWeeksAhead(currentAccountId, signal)
        .then((ahead) => {
          if (signal?.aborted !== true) {
            setWeeks(ahead)
            setWeeksError(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true) {
            setWeeksError(problem.message)
          }
        })
    },
    [currentAccountId],
  )

  useEffect(() => {
    if (theirs === false) {
      return
    }
    const request = new AbortController()
    loadCategories(request.signal)
    loadEnded(request.signal)
    loadSpends(request.signal)
    loadBills(request.signal)
    loadBillCategories(request.signal)
    loadMonth(request.signal)
    loadHistory(request.signal)
    loadWeeks(request.signal)
    return () => request.abort()
  }, [
    loadBillCategories,
    loadBills,
    loadCategories,
    loadEnded,
    loadHistory,
    loadMonth,
    loadSpends,
    loadWeeks,
    theirs,
  ])

  // This category's row out of the month, or null while the read has not arrived — and null for
  // ever on a category the month does not hold, which is an ended one that nothing happened in.
  function theMonthOf(categoryId: number): CategorySpending | null {
    return month?.categories.find((row) => row.categoryId === categoryId) ?? null
  }

  // The bills a category has committed to it, standing ones only: an ended bill is no longer a
  // claim on next month's balance, and a category showing one would be promising money that is not
  // going anywhere. The label on an ended bill is still readable, on the account's own page, which
  // is where an ended bill is drawn.
  function theBillsIn(categoryId: number): RecurringBill[] {
    const filedHere = new Set(
      (billCategories ?? [])
        .filter((filed) => filed.categoryId === categoryId)
        .map((filed) => filed.billId),
    )
    return (bills ?? []).filter((bill) => filedHere.has(bill.billId))
  }

  // All three lists again after anything was declared, renamed or ended, for the reason the bills
  // are read again: the answer to the request is one category and what changed is more than one
  // list. The spends are in there because a renamed category is a renamed category everywhere —
  // everything filed under it follows the name, and a split still printing the old word would be
  // this page disagreeing with the backend about what it just did.
  function categoriesChanged() {
    loadCategories()
    loadEnded()
    loadSpends()
    // And the labels, because ending a category leaves the bills that pointed at it pointing at an
    // ended category rather than silently unlinking them: what the cards say about those bills
    // changes although no bill did.
    loadBillCategories()
    // And the month, because every figure on this page is derived from what has just changed:
    // ending a category stops its budget, setting one changes what it is measured against, and a
    // page that patched its own copy would be guessing at what the backend would say.
    loadMonth()
    // And the months behind it, for the same reason and one more: a category ended leaves the
    // comparison for the months it was live in, and a figure declared changes what every month it
    // governs was measured against.
    loadHistory()
    // And the six weeks, for the same reason one step further out: what the budgets claim of every
    // week ahead is made of exactly the figures that have just moved, so a forecast left as it was
    // would be advising a customer out of a budget they have already changed.
    loadWeeks()
  }

  return (
    <section className="view">
      <button type="button" className="link link--back" onClick={onBack}>
        <BackIcon />
        The account
      </button>

      {/* The customer-level read, which is what says whose accounts these are. When it fails this
          page cannot tell its own account from somebody else's, so the backend's own sentence goes
          here and the page carries on with what it can read. */}
      {accountsError !== null && <Refusal reason={accountsError} standing />}

      {theirs === false ? (
        <Refusal reason={`Current account ${currentAccountId} is not one of yours.`} standing />
      ) : (
        <>
          <div className="section-head">
            <h2>How this month is going</h2>
          </div>

          {monthError !== null && <Refusal reason={monthError} standing />}

          {month === null && monthError === null && (
            <div className="card">
              <Waiting label="Working out how this month is going…" bars={['9rem', '100%', '60%']} />
            </div>
          )}

          {month !== null && <TheMonthSoFar month={month} />}

          <div className="section-head">
            <h2>How it compares with the last few</h2>
          </div>

          {historyError !== null && <Refusal reason={historyError} standing />}

          {history === null && historyError === null && (
            <div className="card">
              <Waiting label="Looking back over the last few months…" bars={['9rem', '100%']} />
            </div>
          )}

          {history !== null && history.categories.length === 0 && (
            <article className="card reveal">
              <p className="nothing">
                Nothing to compare yet. Name what your money goes on and record what you spend, and
                these months will start telling you whether an expensive month was a bad one or just
                what you do.
              </p>
            </article>
          )}

          {history !== null &&
            history.categories.map((category, place) => (
              <TheMonthsBehind
                key={category.categoryId}
                category={category}
                windowOfMonths={history.months}
                place={place}
              />
            ))}

          <div className="section-head">
            <h2>What your money goes on</h2>
          </div>

          {categoriesError !== null && <Refusal reason={categoriesError} standing />}

          {categories === null && categoriesError === null && (
            <div className="card">
              <Waiting label="Loading what your money goes on…" bars={['9rem', '100%', '60%']} />
            </div>
          )}

          {categories !== null && categories.length === 0 && (
            <article className="card reveal">
              <p className="nothing">
                You have not said what your money goes on. Name the things you actually spend on —
                groceries, fuel, going out — and the part of the month nobody writes down in advance
                becomes something you can decide about beforehand rather than discover afterwards.
              </p>
            </article>
          )}

          {categories !== null &&
            categories.map((category, place) => (
              <CategoryCard
                key={category.categoryId}
                category={category}
                place={place}
                currentAccountId={currentAccountId}
                bills={theBillsIn(category.categoryId)}
                month={theMonthOf(category.categoryId)}
                onChanged={categoriesChanged}
              />
            ))}

          <DeclareACategory currentAccountId={currentAccountId} onDeclared={categoriesChanged} />

          {endedError !== null && <Refusal reason={endedError} standing />}

          {/* Absent rather than empty while nothing has been ended, the same bargain the ended
              bills strike: a heading with nothing under it is a heading people learn to skip. */}
          {ended !== null && ended.length > 0 && (
            <>
              <div className="section-head">
                <h2>Categories you have ended</h2>
              </div>
              {ended.map((category, place) => (
                <CategoryCard
                  key={category.categoryId}
                  category={category}
                  place={place}
                  currentAccountId={currentAccountId}
                  bills={theBillsIn(category.categoryId)}
                  month={theMonthOf(category.categoryId)}
                  onChanged={categoriesChanged}
                />
              ))}
            </>
          )}

          <div className="section-head">
            <h2>What you have spent</h2>
          </div>

          <RecordASpend
            currentAccountId={currentAccountId}
            categories={categories ?? []}
            onRecorded={() => {
              loadSpends()
              // The money left, so every figure above it moved. Nothing is stored, so asking again
              // is the whole of what keeps this page and the backend saying the same thing.
              loadMonth()
              // Including this month's place among the months behind it, which is the figure a
              // spend recorded now moves most.
              loadHistory()
              // And what the weeks ahead claim, because the month the clock is in contributes what
              // is left of its budgets: a spend recorded now is a euro the forecast can no longer
              // claim, and the leftover under it rises by exactly that much.
              loadWeeks()
            }}
          />

          {spendsError !== null && <Refusal reason={spendsError} standing />}

          {spends === null && spendsError === null && (
            <div className="card">
              <Waiting label="Loading what you have spent…" bars={['9rem', '100%', '60%']} />
            </div>
          )}

          {spends !== null && spends.length === 0 && (
            <article className="card reveal">
              <p className="nothing">
                Nothing recorded yet. Write down what you actually spend — the groceries, the fuel,
                the round of drinks — and the balance you are deciding to save from becomes the
                balance you really have.
              </p>
            </article>
          )}

          {spends !== null && spends.map((spend, place) => (
            <SpendCard
              key={spend.spendId}
              spend={spend}
              place={place}
              currentAccountId={currentAccountId}
              categories={categories ?? []}
              onCorrected={() => {
                loadSpends()
                // A correction rewrites the past: the euros move out of one category's month and
                // into another's, and every figure derived from that month moves with them. That
                // is the point of the correction rather than a side effect of it, so both reads
                // are taken again.
                loadMonth()
                loadHistory()
              }}
            />
          ))}

          {/* Six weeks, here and on the card below, is a display horizon and not the scheme —
              see the note on the card's own title. Nothing on this section is priced by anything
              the bank publishes. */}
          <div className="section-head">
            <h2>The weeks ahead</h2>
          </div>

          {weeksError !== null && <Refusal reason={weeksError} standing />}

          {weeks === null && weeksError === null && (
            <div className="card">
              <Waiting label="Working out the next six weeks…" bars={['9rem', '100%', '60%']} />
            </div>
          )}

          {weeks !== null && <TheSixWeeksAhead ahead={weeks} />}
        </>
      )}
    </section>
  )
}

/**
 * The next six weeks, a row at a time: what arrives, what is committed, what the budgets claim, and
 * what that leaves.
 *
 * <p><strong>The figure this whole screen builds up to.</strong> Everything above it — the words,
 * the figures on them, the spends — is what makes the last column of these rows mean something: it
 * is money that could go into savings without breaking anything its owner has already promised
 * themselves.
 *
 * <p><strong>Nothing here is worked out on this side.</strong> The subtraction at the end of each
 * row and the weekly figure under the rows are the backend's, and drawing them is all this does. A
 * page dividing a total by six for itself would be a second place the application decided what a
 * customer could save, and the two would disagree the first time either changed.
 *
 * <p>The rows say the leftover assumes every budget is spent in full, because a customer reading a
 * promising number has to know what it assumed. It is a floor rather than a forecast of what they
 * will actually spend — which is the conservative direction, and the only one worth being wrong in
 * for a figure somebody is about to lock money away on.
 */
function TheSixWeeksAhead({ ahead }: { ahead: TheWeeksAhead }) {
  return (
    <>
      <article className="card reveal">
        {/* Six weeks is a display horizon and is not the scheme. It is how many rows of cash flow
            fit on a card somebody reads, fixed by the backend's TheWeeksAhead, and it has nothing
            to do with a run of secured weeks — which is also counted in weeks, which is also six
            long by the time the ladder caps, and which is priced by figures the bank publishes and
            can change. Deliberately left saying six. */}
        <h3 className="card__title">What the next six weeks leave</h3>
        <p className="amount amount--xl">{euros.format(ahead.leftOver)}</p>
        <dl className="split">
          <div>
            <dt>Arriving</dt>
            <dd>{euros.format(ahead.arriving)}</dd>
          </div>
          <div>
            <dt>Committed</dt>
            <dd>{euros.format(ahead.committed)}</dd>
          </div>
          <div>
            <dt>Budgets claim</dt>
            <dd>{euros.format(ahead.claimedByBudgets)}</dd>
          </div>
        </dl>
        {ahead.worthOffering ? (
          <p className="rule-card__what">
            That is {euros.format(ahead.couldSaveWeekly)} a week you could save. The weekly amount on
            your savings account is where you say so — it is offered here and never set for you.
          </p>
        ) : (
          <p className="rule-card__what">
            These six weeks are already spoken for, so there is nothing here to put away. Lower a
            budget, or wait for the salary the sixth week is still short of.
          </p>
        )}
        <p className="rule">
          {asADay(ahead.from)} to {asADay(ahead.until)}, counted in the weeks your streak is counted
          in. Every budget is assumed to be spent in full, so what is left is a floor rather than a
          hope. Nothing is stored — record a spend and this changes with it.
        </p>
      </article>

      {ahead.weeks.map((week, place) => (
        <WeekAheadCard key={week.startsOn} week={week} place={place} />
      ))}
    </>
  )
}

/**
 * One week of the six: the days it runs between, and the four figures that add up to what it
 * leaves.
 *
 * <p>The week is named by its two days rather than by a number, because "week 4" is a label nobody
 * can hold a calendar up against.
 *
 * <p>A week that takes more than it brings says so in words. It is a real week and not an error —
 * the one this card exists to show coming — and drawing it exactly like the others would leave a
 * customer to spot the minus sign for themselves.
 */
function WeekAheadCard({ week, place }: { week: WeekAhead; place: number }) {
  return (
    <article className="card reveal" style={rowDelay(place)}>
      <h3 className="card__title">
        {asADay(week.startsOn)} – {asADay(week.endsOn)}
      </h3>
      <dl className="split">
        <div>
          <dt>Arriving</dt>
          <dd>{euros.format(week.arriving)}</dd>
        </div>
        <div>
          <dt>Committed</dt>
          <dd>{euros.format(week.committed)}</dd>
        </div>
        <div>
          <dt>Budgets claim</dt>
          <dd>{euros.format(week.claimedByBudgets)}</dd>
        </div>
        <div>
          <dt>Leaves</dt>
          <dd>{euros.format(week.leftOver)}</dd>
        </div>
      </dl>
      {week.takesMoreThanItBrings && (
        <p className="goals__warn">
          <WarningIcon />
          <span>
            This week claims {euros.format(Math.abs(week.leftOver))} more than lands in it, so it
            has to come out of what is already in the account.
          </span>
        </p>
      )}
    </article>
  )
}

/**
 * Recording what was spent: a name, what it cost, and the split it was for.
 *
 * <p>The money leaves the current account the moment this is accepted, which is why the form says so
 * before it is pressed rather than afterwards. A spend larger than the balance moves nothing at all
 * — the backend refuses it outright rather than taking what is there — and the sentence that comes
 * back names both figures.
 *
 * <p><strong>Nothing here is judged.</strong> Whether the parts add up to what was spent, whether
 * there are too many of them, whether a figure is an amount of money at all and whether the account
 * holds it are every one of them the backend's rulings, in sentences written for the person who
 * typed them. A form that judged any of it here would answer in its own words a question that
 * already has a sentence waiting for it.
 *
 * <p>The one figure this form works out is the one under the boxes: what is still unsplit, which is
 * arithmetic on what the person is typing at that moment rather than a fact about their money. It
 * is shown only while every box reads as a plain number, so a half-typed figure is never told it is
 * wrong — that is the backend's job, once.
 *
 * <p>The boxes are emptied only when the spend was recorded, so a refused one is still on screen to
 * be corrected rather than retyped.
 */
function RecordASpend({
  currentAccountId,
  categories,
  onRecorded,
}: {
  currentAccountId: number
  categories: SpendingCategory[]
  onRecorded: () => void
}) {
  const [name, setName] = useState('')
  const [amount, setAmount] = useState('')
  // One part to begin with, filed under nothing: recording a spend at all should never be the hard
  // part, and a form that opened demanding a category would make it one.
  const [parts, setParts] = useState<SpendPartToRecord[]>([{ categoryId: null, amount: '' }])
  const [saving, setSaving] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  function record(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setRefusal(null)
    recordASpend(currentAccountId, name, amount, parts)
      .then(() => {
        setName('')
        setAmount('')
        setParts([{ categoryId: null, amount: '' }])
        onRecorded()
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setSaving(false))
  }

  function change(place: number, part: SpendPartToRecord) {
    setParts(parts.map((existing, at) => (at === place ? part : existing)))
  }

  return (
    <article className="card reveal">
      <h3 className="card__title">Record what you spent</h3>
      <form onSubmit={record}>
        <div className="field">
          <label htmlFor="spendName">What it was</label>
          <input
            id="spendName"
            className="text-input"
            placeholder="Delhaize"
            autoComplete="off"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </div>

        <EuroAmount
          id="spendAmount"
          label="Spend amount"
          value={amount}
          onType={(typed) => setAmount(typed)}
        />

        <fieldset className="rules__shares">
          <legend>What it was for</legend>
          {parts.map((part, place) => (
            <div className="rules__share" key={place}>
              <div className="field">
                <label htmlFor={`spendPart${place}Category`}>Filed under</label>
                <div className="select">
                  <select
                    id={`spendPart${place}Category`}
                    value={part.categoryId === null ? '' : String(part.categoryId)}
                    onChange={(event) =>
                      change(place, {
                        ...part,
                        categoryId: event.target.value === '' ? null : Number(event.target.value),
                      })
                    }
                  >
                    {/* Nothing at all, first and always offered: uncategorised is a state somebody
                        chooses, not a category missing from the list. */}
                    <option value="">Not filed yet</option>
                    {categories.map((category) => (
                      <option key={category.categoryId} value={category.categoryId}>
                        {category.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="field">
                <label htmlFor={`spendPart${place}Amount`}>Of which</label>
                <input
                  id={`spendPart${place}Amount`}
                  className="text-input"
                  inputMode="decimal"
                  placeholder="25.00"
                  autoComplete="off"
                  value={part.amount}
                  onChange={(event) => change(place, { ...part, amount: event.target.value })}
                />
              </div>
              {parts.length > 1 && (
                <button
                  type="button"
                  className="link link--small"
                  onClick={() => setParts(parts.filter((_, at) => at !== place))}
                >
                  Take this part off
                </button>
              )}
            </div>
          ))}
          {parts.length < 10 && (
            <button
              type="button"
              className="link link--small"
              onClick={() => setParts([...parts, { categoryId: null, amount: '' }])}
            >
              Split it another way
            </button>
          )}
          <StillToSplit amount={amount} parts={parts} />
        </fieldset>

        <div className="rule-card__actions">
          <Button type="submit" busy={saving} disabled={saving}>
            Record it
          </Button>
        </div>
        {refusal !== null && <Refusal reason={refusal} />}
      </form>
      <p className="rule">
        The money leaves this account as soon as you record it, and a spend larger than the balance
        is refused outright — nothing moves and nothing is written down. The parts have to add up to
        what you spent, and a part can be left unfiled and put in a category later.
      </p>
    </article>
  )
}

/**
 * What of the spend is still unsplit, while it is being typed.
 *
 * <p>Arithmetic on the boxes rather than a figure about anybody's money, and it is here so that the
 * one invariant this form has — the parts add up to what was spent — is learnable before it is
 * refused rather than only after. It says nothing at all unless every box reads as a plain number,
 * because a half-typed figure is not a mistake and the backend is the only thing that decides what
 * is.
 */
function StillToSplit({ amount, parts }: { amount: string; parts: SpendPartToRecord[] }) {
  const spent = Number(amount)
  const split = parts.map((part) => Number(part.amount))
  const readable =
    amount.trim() !== '' &&
    Number.isFinite(spent) &&
    split.every((part, at) => parts[at].amount.trim() !== '' && Number.isFinite(part))
  if (!readable) {
    return null
  }
  const left = spent - split.reduce((together, part) => together + part, 0)
  // Rounded to the cent for the reading only: the figures themselves travel as the text that was
  // typed, and what counts as an amount of money is decided in one place, which is not this one.
  const toTheCent = Math.round(left * 100) / 100
  return (
    <p className="rule-card__next">
      {toTheCent === 0
        ? 'The parts add up to what you spent.'
        : `Still to split: ${euros.format(toTheCent)}`}
    </p>
  )
}

/**
 * One spend as it was recorded: what it was, what it cost, when the money left, what it was for, and
 * the one press that puts that last part right.
 *
 * <p>The amount and the name are not offered for editing and the card has no way to undo the spend,
 * because there is no endpoint for either: the money moved, and a record that could be unmade is not
 * a record. What is offered is the split — the only part of a spend that was ever an opinion — and
 * it is offered on the card rather than on a screen of its own, because the customer deciding that
 * something is filed wrongly is the customer reading the list.
 *
 * <p>A spend that has been corrected says so, beside the moment the money left. That is the whole
 * point of the marker: the ledger does not pretend somebody got it right the first time, and a card
 * that quietly showed something different from what it showed yesterday would be the application
 * editing the customer's memory. A spend nobody has corrected says nothing there rather than
 * "never", because a line about a thing that did not happen is a line people learn to skip.
 *
 * <p>A part filed under nothing says so in words rather than showing a gap. Uncategorised is a state
 * somebody chose, and a blank where a category should be reads as something the application lost.
 */
function SpendCard({
  spend,
  place,
  currentAccountId,
  categories,
  onCorrected,
}: {
  spend: RecordedSpend
  place: number
  currentAccountId: number
  categories: SpendingCategory[]
  onCorrected: () => void
}) {
  const [correcting, setCorrecting] = useState(false)

  return (
    <article className="card reveal rule-card rule-card--live" style={rowDelay(place)}>
      <div className="rule-card__top">
        <h3 className="rule-card__name">{spend.name}</h3>
        <span className="rule-card__state rule-card__state--live">
          {euros.format(spend.amount)}
        </span>
      </div>
      <p className="rule-card__next">
        Recorded {dateAndTime.format(new Date(spend.recordedAt))}
      </p>
      {spend.correctedAt !== null && (
        <p className="rule-card__next">
          Put right {dateAndTime.format(new Date(spend.correctedAt))}
        </p>
      )}
      <ul className="rule-card__split">
        {spend.parts.map((part, at) => (
          <li key={at}>
            <span className="rule-card__share">{euros.format(part.amount)}</span>
            {part.categoryName ?? 'Not filed yet'}
          </li>
        ))}
      </ul>

      {correcting ? (
        <CorrectTheSplit
          currentAccountId={currentAccountId}
          spend={spend}
          categories={categories}
          onDone={() => {
            setCorrecting(false)
            onCorrected()
          }}
          onLeaveItAlone={() => setCorrecting(false)}
        />
      ) : (
        <div className="rule-card__actions">
          <button type="button" className="link link--small" onClick={() => setCorrecting(true)}>
            {spend.parts.some((part) => part.categoryId === null)
              ? 'File what is still unfiled'
              : 'Correct what this was for'}
          </button>
        </div>
      )}
    </article>
  )
}

/**
 * Correcting what one spend was for: the whole split, restated.
 *
 * <p><strong>It opens on the split the spend already has</strong> rather than on an empty form, for
 * two reasons. A correction replaces the whole split, so a form that started blank would make
 * somebody moving thirty euros between two categories retype the seventy that were right; and the
 * figures already there are what they are checking their arithmetic against.
 *
 * <p>Nothing here is judged. Whether the parts add up to what was spent, whether there are too many
 * of them and whether each names a category this account may file money under are every one of them
 * the backend's rulings, in the very sentences recording a spend meets — a corrected split is held
 * to exactly the rules the original was. A refused correction leaves the old split standing, so the
 * boxes stay as they were typed, to be fixed rather than retyped.
 *
 * <p>There is no amount here and no name, because a correction cannot carry either. The money
 * moved. The one figure worked out on this side is what is still unsplit, which is arithmetic on
 * what somebody is typing at that moment rather than a fact about their money.
 *
 * <p>Ended categories are not offered, and one a part already sits under is left exactly where it
 * is: ending a category keeps every euro ever filed under it, and the backend refuses it as a
 * destination. What a correction can always reach is "not filed yet", which is a state a customer
 * chooses rather than a category missing from the list.
 */
function CorrectTheSplit({
  currentAccountId,
  spend,
  categories,
  onDone,
  onLeaveItAlone,
}: {
  currentAccountId: number
  spend: RecordedSpend
  categories: SpendingCategory[]
  onDone: () => void
  onLeaveItAlone: () => void
}) {
  const [parts, setParts] = useState<SpendPartToRecord[]>(
    spend.parts.map((part) => ({
      categoryId: part.categoryId,
      // The text that travels, from the number the API sent: the figures are quoted to the cent by
      // the backend, so this is what was already true rather than a reading of it.
      amount: part.amount.toFixed(2),
    })),
  )
  const [saving, setSaving] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  function correct(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setRefusal(null)
    correctTheSplitOf(currentAccountId, spend.spendId, parts)
      .then(() => onDone())
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setSaving(false))
  }

  function change(place: number, part: SpendPartToRecord) {
    setParts(parts.map((existing, at) => (at === place ? part : existing)))
  }

  return (
    <form onSubmit={correct}>
      <fieldset className="rules__shares">
        <legend>What it was really for</legend>
        {parts.map((part, place) => (
          <div className="rules__share" key={place}>
            <div className="field">
              <label htmlFor={`correct${spend.spendId}Part${place}Category`}>Filed under</label>
              <div className="select">
                <select
                  id={`correct${spend.spendId}Part${place}Category`}
                  value={part.categoryId === null ? '' : String(part.categoryId)}
                  onChange={(event) =>
                    change(place, {
                      ...part,
                      categoryId: event.target.value === '' ? null : Number(event.target.value),
                    })
                  }
                >
                  <option value="">Not filed yet</option>
                  {categories.map((category) => (
                    <option key={category.categoryId} value={category.categoryId}>
                      {category.name}
                    </option>
                  ))}
                  {/* A category this part already sits under that is no longer standing: kept as an
                      option so that the box says what the part is filed under rather than silently
                      reading as unfiled. Choosing it again is refused by the backend in words. */}
                  {part.categoryId !== null &&
                    !categories.some((category) => category.categoryId === part.categoryId) && (
                      <option value={String(part.categoryId)}>
                        {spend.parts.find((was) => was.categoryId === part.categoryId)
                          ?.categoryName ?? 'A category you have ended'}
                      </option>
                    )}
                </select>
              </div>
            </div>
            <div className="field">
              <label htmlFor={`correct${spend.spendId}Part${place}Amount`}>Of which</label>
              <input
                id={`correct${spend.spendId}Part${place}Amount`}
                className="text-input"
                inputMode="decimal"
                placeholder="25.00"
                autoComplete="off"
                value={part.amount}
                onChange={(event) => change(place, { ...part, amount: event.target.value })}
              />
            </div>
            {parts.length > 1 && (
              <button
                type="button"
                className="link link--small"
                onClick={() => setParts(parts.filter((_, at) => at !== place))}
              >
                Take this part off
              </button>
            )}
          </div>
        ))}
        {parts.length < 10 && (
          <button
            type="button"
            className="link link--small"
            onClick={() => setParts([...parts, { categoryId: null, amount: '' }])}
          >
            Split it another way
          </button>
        )}
        <StillToSplit amount={spend.amount.toFixed(2)} parts={parts} />
      </fieldset>

      <div className="rule-card__actions">
        <Button type="submit" busy={saving} disabled={saving}>
          Put it right
        </Button>
        <button type="button" className="link link--small" onClick={onLeaveItAlone}>
          Leave it as it is
        </button>
      </div>
      {refusal !== null && <Refusal reason={refusal} />}
      <p className="rule">
        This changes only what the spend was for. The {euros.format(spend.amount)} left the account
        when you recorded it and stays gone, the name stays as you wrote it, and correcting the
        split moves no money at all — but every figure worked out from it moves with the euros, so
        the month you filed it in reads correctly afterwards.
      </p>
    </form>
  )
}

/**
 * One spending category: what it is called, whether it is still standing, and the two things a
 * customer can do to one that is.
 *
 * <p>Renaming and ending are gone on an ended category rather than shown disabled: there is nothing
 * left to rename and nothing left to end, and the backend refuses both in words. What stays is the
 * record, which is the whole reason ending is a closing and not a deletion — the months that were
 * filed under it stay readable, and saying so on the card is what stops somebody hunting for the
 * button that would bring it back.
 */
function CategoryCard({
  category,
  place,
  currentAccountId,
  bills,
  month,
  onChanged,
}: {
  category: SpendingCategory
  place: number
  currentAccountId: number
  bills: RecurringBill[]
  /**
   * This category's row out of the month read, or null while that read has not arrived — and null
   * for ever on a category the month does not hold, which is an ended one that nothing happened in.
   * A card that worked the figures out from the bills above it would be a second place this
   * application decides what a category's month cost.
   */
  month: CategorySpending | null
  onChanged: () => void
}) {
  const ended = category.state === 'ENDED'
  const [renaming, setRenaming] = useState(false)
  const [budgeting, setBudgeting] = useState(false)
  const [busy, setBusy] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  function end() {
    setBusy(true)
    setRefusal(null)
    endCategory(currentAccountId, category.categoryId)
      .then(() => onChanged())
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setBusy(false))
  }

  return (
    <article
      className={`card reveal rule-card ${ended ? 'rule-card--ended' : 'rule-card--live'}`}
      style={rowDelay(place)}
    >
      <div className="rule-card__top">
        <h3 className="rule-card__name">{category.name}</h3>
        <span
          className={`rule-card__state ${ended ? 'rule-card__state--ended' : 'rule-card__state--live'}`}
        >
          {ended ? 'Ended' : 'Standing'}
        </span>
      </div>

      {/* What is committed to this category, under the name and before whatever can still be done
          to it. It is the half of a category's month that a customer cannot do anything about this
          week — a standing bill is money already promised — and it is here so that the two halves,
          what is committed and what is chosen, are read in the same place rather than on two
          screens. The bills themselves are put in and taken out on the account's own page, beside
          the bill, which is where somebody looking at a bill will go. */}
      {bills.length > 0 && (
        <div className="rule-card__filed">
          <p className="rule-card__what">
            {bills.length === 1 ? 'One bill counts here' : `${bills.length} bills count here`} ·{' '}
            {euros.format(bills.reduce((sum, bill) => sum + bill.amount, 0))} a month
          </p>
          <ul className="plain-list">
            {bills.map((bill) => (
              <li key={bill.billId}>
                {bill.name} · {euros.format(bill.amount)} on the {dayInTheMonth(bill.dayOfMonth)}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* How this month is going against it, under what is committed to it and above whatever can
          still be done to it. It is drawn on an ended category too — the money left, and the figure
          the spending was actually measured against is part of the record of it. */}
      {month !== null && <ThisMonthAgainstTheBudget month={month} />}

      {ended && (
        <p className="rule-card__next rule-card__next--ended">
          {/* A moment rather than a day, formatted the way an ended bill's is: the backend sends an
              instant, and asADay reads a YYYY-MM-DD. */}
          Ended
          {category.endedAt === null
            ? ''
            : ` on ${dateAndTime.format(new Date(category.endedAt))}`}
          . It is kept so that what you spent under it stays readable; name it again if you are
          spending on it once more.
        </p>
      )}

      {!ended && (
        <div className="rule-card__actions">
          <Button
            small
            tone="ghost"
            disabled={busy}
            aria-expanded={budgeting}
            onClick={() => {
              setRefusal(null)
              setBudgeting((open) => !open)
            }}
          >
            {budgeting
              ? 'Leave the figure as it is'
              : month?.budgeted == null
                ? 'Set a budget'
                : 'Change the budget'}
          </Button>
          <Button
            small
            tone="ghost"
            disabled={busy}
            aria-expanded={renaming}
            onClick={() => {
              setRefusal(null)
              setRenaming((open) => !open)
            }}
          >
            {renaming ? 'Leave the name as it is' : 'Rename'}
          </Button>
          <Button small tone="ghost" busy={busy} disabled={busy} onClick={end}>
            End it
          </Button>
        </div>
      )}

      {refusal !== null && <Refusal reason={refusal} />}

      {budgeting && !ended && (
        <SetTheBudget
          category={category}
          currentAccountId={currentAccountId}
          budgeted={month?.budgeted ?? null}
          rollover={month?.rollover ?? null}
          onSettled={() => {
            setBudgeting(false)
            onChanged()
          }}
        />
      )}

      {renaming && !ended && (
        <RenameTheCategory
          category={category}
          currentAccountId={currentAccountId}
          onRenamed={() => {
            setRenaming(false)
            onChanged()
          }}
        />
      )}
    </article>
  )
}

/**
 * How far into its month a category is: what it was allowed to cost, what it has cost, and what that
 * leaves — said in words over a length that says the same thing at a glance.
 *
 * <p><strong>Nothing here is worked out.</strong> Every figure comes down from the backend already
 * added, because the same figures are drawn on the account's own screen and two subtractions would
 * be two answers to one question. The only arithmetic this component does is the width of the bar,
 * which is a drawing decision and not a fact about anybody's money.
 *
 * <p><strong>A category with no budget draws what it cost and says plainly that nothing was
 * set.</strong> Not declared is a state and not a nought: a blank drawn as 0.00 would tell somebody
 * they had overspent a limit they never made, and watching a category without policing it is a thing
 * a customer is allowed to choose. It is the same bargain the declared saving capacity strikes.
 *
 * <p><strong>The bar is drawn against what the month allows rather than against the budget.</strong>
 * A category carrying forty euros forward really does have a hundred and forty to spend, and a bar
 * measured against the hundred would call a month overspent that is nothing of the kind. What
 * carried in is said in words under it, because a figure a customer did not type is one they are
 * entitled to see the reason for — and an envelope carrying a debt is told plainly rather than
 * softened, since that consequence is the whole reason they chose one.
 *
 * <p><strong>A month that allows less than nothing draws an empty track and says so.</strong> There
 * is no honest length for a negative allowance; the words above it carry the answer, which is that
 * the month started in the red.
 *
 * <p><strong>Committed and chosen are drawn apart.</strong> Nine hundred euros of rent and fifty
 * euros of plumber are one figure to a balance and two entirely different things to a person: only
 * the second is something they can do anything about this month, which is the whole reason the
 * backend reports them separately.
 *
 * <p>The bar is hidden from anything reading the page out, the way the week's own bar is: the words
 * above it are the answer and the length is the impression of it.
 */
function ThisMonthAgainstTheBudget({ month }: { month: CategorySpending }) {
  const allowed = month.allowed
  const carriedIn = month.carriedIn ?? 0
  // Capped at a full bar, because a length longer than its track is not a length anybody can draw,
  // and empty where the month allows nothing or less — there is no length for a negative allowance.
  // How far over it went is said in the words above, where a figure belongs.
  const howFarAlong =
    allowed === null || allowed <= 0 ? 0 : Math.min(100, (month.spent / allowed) * 100)
  const nearlyThere = !month.overspent && howFarAlong >= 80

  return (
    <div className="weekbar">
      <p
        className={
          allowed === null
            ? 'weekbar__state'
            : month.overspent
              ? 'weekbar__state is-over'
              : nearlyThere
                ? 'weekbar__state is-open'
                : 'weekbar__state is-safe'
        }
      >
        {allowed === null
          ? `${euros.format(month.spent)} spent this month · no budget set`
          : month.overspent
            ? `${euros.format(month.spent)} of ${euros.format(allowed)} · ${euros.format(
                Math.abs(month.left ?? 0),
              )} over`
            : `${euros.format(month.spent)} of ${euros.format(allowed)} · ${euros.format(
                month.left ?? 0,
              )} left`}
      </p>
      {/* Only when something actually carried, because a line saying nothing followed you is a line
          nobody reads twice — and the rule is named beside it so that a figure the customer did not
          type is one they can see the reason for. */}
      {month.rollover !== null && carriedIn !== 0 && (
        <p className="rule-card__what">
          {carriedIn > 0
            ? `${euros.format(carriedIn)} carried in from last month, on top of ${euros.format(
                month.budgeted ?? 0,
              )} budgeted.`
            : `${euros.format(Math.abs(carriedIn))} carried in from last month's overspend, against ${euros.format(
                month.budgeted ?? 0,
              )} budgeted.`}{' '}
          {theRolloverRuleCalled(month.rollover)}.
        </p>
      )}
      {allowed !== null && (
        <div className="progress" aria-hidden="true">
          <span
            className={
              month.overspent
                ? 'progress__fill progress__fill--over'
                : nearlyThere
                  ? 'progress__fill progress__fill--near'
                  : 'progress__fill'
            }
            style={{ width: `${howFarAlong}%` }}
          />
        </div>
      )}
      {/* Only when there is something to tell apart. A category holding nothing but spends would
          otherwise carry a line saying nought is committed, which is a line nobody reads twice. */}
      {month.committed > 0 && (
        <p className="rule-card__what">
          {euros.format(month.committed)} of it went out on bills you have already agreed to
          {month.discretionary > 0
            ? `, and ${euros.format(month.discretionary)} you chose.`
            : '.'}
        </p>
      )}
    </div>
  )
}

/**
 * The one box a budget has: what this category is allowed to cost each month.
 *
 * <p>It opens with the figure already in it when there is one, because changing a budget is almost
 * always nudging it rather than starting again, and an empty box would ask the customer to retype
 * what they can see above it.
 *
 * <p><strong>Nothing here is judged.</strong> Whether a figure is an amount of money, whether a
 * budget of nothing is a budget, and whether this category can carry one at all are the backend's
 * rulings in sentences written for the person who typed them. The figure travels as the text that
 * was typed, so somebody who wrote a comma sees the comma in the answer.
 *
 * <p>Stopping is beside it rather than behind a nought, because they are different things: a budget
 * of nothing is refused, and a customer who has stopped policing a category still wants to watch it.
 * The months the figure governed go on quoting it either way — which is why this is a stop rather
 * than a deletion, and why the button says so.
 *
 * <p><strong>The rule sits in the same box as the figure, and is sent with it.</strong> They are one
 * declaration — "from now on, this much, and this is what happens to what is left of it" — and one
 * supersession covers both, so a customer changing only their rule presses the same button and the
 * months already gone go on carrying under the rule that stood in them. A separate control would
 * invite two requests taking effect in two different months.
 *
 * <p>It opens on the rule already in force for the same reason the box opens on the figure: changing
 * a budget is almost always nudging it, and a chooser that reset itself to the default would quietly
 * turn somebody's envelope back into a clean slate every time they raised their grocery figure.
 */
function SetTheBudget({
  category,
  currentAccountId,
  budgeted,
  rollover,
  onSettled,
}: {
  category: SpendingCategory
  currentAccountId: number
  budgeted: number | null
  /** The rule in force this month, or null on a category nobody has put a figure on yet. */
  rollover: RolloverRule | null
  onSettled: () => void
}) {
  const [amount, setAmount] = useState(budgeted === null ? '' : budgeted.toFixed(2))
  const [rule, setRule] = useState<RolloverRule>(rollover ?? 'NOTHING_ROLLS_OVER')
  const [saving, setSaving] = useState(false)
  const [stopping, setStopping] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  function declare(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setRefusal(null)
    declareABudget(currentAccountId, category.categoryId, amount, rule)
      .then(() => onSettled())
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setSaving(false))
  }

  function stop() {
    setStopping(true)
    setRefusal(null)
    stopBudgeting(currentAccountId, category.categoryId)
      .then(() => onSettled())
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setStopping(false))
  }

  return (
    <form className="rule-card__form" onSubmit={declare}>
      <div className="field">
        <label htmlFor={`category${category.categoryId}Budget`}>
          What it is allowed to cost each month
        </label>
        <input
          id={`category${category.categoryId}Budget`}
          className="text-input"
          inputMode="decimal"
          placeholder="250.00"
          autoComplete="off"
          value={amount}
          onChange={(event) => setAmount(event.target.value)}
        />
      </div>
      <fieldset className="rollover">
        <legend>What happens to the difference when the month ends</legend>
        {theRolloverRules.map((one) => (
          <label
            key={one.rule}
            className="rollover__choice"
            htmlFor={`category${category.categoryId}${one.rule}`}
          >
            <input
              id={`category${category.categoryId}${one.rule}`}
              type="radio"
              name={`category${category.categoryId}Rollover`}
              value={one.rule}
              checked={rule === one.rule}
              onChange={() => setRule(one.rule)}
            />
            <span>
              <strong>{one.name}</strong>
              <span className="rule">{one.what}</span>
            </span>
          </label>
        ))}
      </fieldset>
      <Button type="submit" block busy={saving} disabled={saving || stopping}>
        {saving ? 'Setting it…' : budgeted === null ? 'Set this budget' : 'Change this budget'}
      </Button>
      {budgeted !== null && (
        <div className="rule-card__actions">
          <Button small tone="ghost" busy={stopping} disabled={saving || stopping} onClick={stop}>
            Stop budgeting this
          </Button>
        </div>
      )}
      {refusal !== null && <Refusal reason={refusal} />}
      <p className="rule">
        From this month on, the figure and the rule together. What you had before is kept, so the
        months it applied to go on showing it and carrying under it — changing your mind now does not
        change what you decided then.
      </p>
    </form>
  )
}

/**
 * The one box a category has.
 *
 * <p>It opens with the name already in it, because renaming is almost always fixing a spelling
 * rather than choosing a different word, and an empty box would ask the customer to retype what
 * they can see above it.
 *
 * <p>Nothing is sent when the name has not changed: the backend would accept it — a category is
 * allowed to be renamed to what it is already called — but a request that changes nothing is a
 * request worth not making.
 */
function RenameTheCategory({
  category,
  currentAccountId,
  onRenamed,
}: {
  category: SpendingCategory
  currentAccountId: number
  onRenamed: () => void
}) {
  const [name, setName] = useState(category.name)
  const [renaming, setRenaming] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  function rename(event: FormEvent) {
    event.preventDefault()
    if (name === category.name) {
      onRenamed()
      return
    }
    setRenaming(true)
    setRefusal(null)
    renameCategory(currentAccountId, category.categoryId, name)
      .then(() => onRenamed())
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setRenaming(false))
  }

  return (
    <form className="rule-card__form" onSubmit={rename}>
      <div className="field">
        <label htmlFor={`category${category.categoryId}Name`}>What you call it</label>
        <input
          id={`category${category.categoryId}Name`}
          className="text-input"
          autoComplete="off"
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </div>
      <Button type="submit" block busy={renaming} disabled={renaming}>
        {renaming ? 'Renaming it…' : 'Rename this category'}
      </Button>
      {refusal !== null && <Refusal reason={refusal} />}
    </form>
  )
}

/**
 * The one box for naming something new.
 *
 * <p>Text, like every form in this application: what a name is, and whether this account is already
 * using that word, are the backend's rulings in sentences written for the person who typed them,
 * and a form that judged either of them here would be answering in its own words a question that
 * already has a sentence waiting for it.
 *
 * <p>The box is emptied only when the declaration was accepted, so that a refused name is still on
 * screen to be corrected rather than retyped.
 */
function DeclareACategory({
  currentAccountId,
  onDeclared,
}: {
  currentAccountId: number
  onDeclared: () => void
}) {
  const [name, setName] = useState('')
  const [saving, setSaving] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  function declare(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setRefusal(null)
    declareACategory(currentAccountId, name)
      .then(() => {
        setName('')
        onDeclared()
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setSaving(false))
  }

  return (
    <article className="card reveal">
      <h3 className="card__title">Name something your money goes on</h3>
      <form onSubmit={declare}>
        <div className="field">
          <label htmlFor="categoryName">What you call it</label>
          <input
            id="categoryName"
            className="text-input"
            placeholder="Groceries"
            autoComplete="off"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </div>
        <div className="rule-card__actions">
          <Button type="submit" busy={saving} disabled={saving}>
            Name it
          </Button>
        </div>
        {refusal !== null && <Refusal reason={refusal} />}
      </form>
      <p className="rule">
        Your own words, not this application's. A category can be renamed whenever you like — what
        you spent under it follows the name — and ending one is final: it leaves this list, keeps
        everything filed under it, and cannot be brought back.
      </p>
    </article>
  )
}

/**
 * The name of a month as a person says it, from the `"2026-03"` the backend sends.
 *
 * <p>Read the way {@link asADay} reads a date and for the same reason: `new Date("2026-03")` is
 * parsed as midnight UTC on the first, so anybody west of Greenwich would be shown February. The day
 * and the time are pinned on so that the three numbers stay exactly as they arrived — the backend
 * has already decided which month this is, in the zone it counts every calendar thing in.
 */
function asAMonth(yearMonth: string): string {
  return theMonthInWords.format(new Date(`${yearMonth}-01T00:00:00`))
}

/** A month a person can read, spelled out, because a budget card is about one month and says which. */
const theMonthInWords = new Intl.DateTimeFormat('nl-BE', { month: 'long', year: 'numeric' })

/**
 * The account's own month, above the categories: what was budgeted altogether, what it has cost so
 * far, and what that leaves.
 *
 * <p>The figures are the backend's, added up once there. This card and the one on the account screen
 * draw the same read, so the two cannot disagree about what the month cost — which they would the
 * first time either of them did its own subtraction.
 *
 * <p><strong>An account with no figure anywhere on it says so rather than quoting a nought.</strong>
 * Not declared is a state: a customer who has named their categories and not yet decided what any of
 * them is allowed to cost has a month worth reading — it says what they have spent — and a total of
 * 0.00 budgeted would read as a plan to spend nothing.
 *
 * <p>Money filed under nothing is drawn on its own line when there is any, because it is money that
 * left the account and belongs to none of the rows below. Leaving it out would make the total
 * quietly smaller than the spends the customer can see on the same page.
 */
function TheMonthSoFar({ month }: { month: MonthOfSpending }) {
  return (
    <article className="card reveal">
      <h3 className="card__title">{asAMonth(month.month)}</h3>
      <dl className="split">
        <div>
          <dt>Budgeted</dt>
          <dd>{month.budgeted === null ? 'Not set' : euros.format(month.budgeted)}</dd>
        </div>
        <div>
          <dt>Spent</dt>
          <dd>{euros.format(month.spent)}</dd>
        </div>
        <div>
          <dt>Left</dt>
          <dd>{month.left === null ? 'Not set' : euros.format(month.left)}</dd>
        </div>
      </dl>
      {/* Only where something actually carried. Nought carried is the ordinary case — every budget
          starts on the rule that carries nothing — and a line saying so on every account would be a
          line nobody reads. What is left is taken from the budget and the carry together, so a
          customer who can see the two figures can check the third with a pencil. */}
      {month.carriedIn !== null && month.carriedIn !== 0 && (
        <p className="rule-card__what">
          {month.carriedIn > 0
            ? `${euros.format(month.carriedIn)} carried in from last month, so this month allows ${euros.format(
                month.allowed ?? 0,
              )}.`
            : `${euros.format(Math.abs(month.carriedIn))} carried in from last month's overspend, so this month allows ${euros.format(
                month.allowed ?? 0,
              )}.`}
        </p>
      )}
      {month.committed > 0 && (
        <p className="rule-card__what">
          {euros.format(month.committed)} of it went out on bills you had already agreed to, and{' '}
          {euros.format(month.discretionary)} you chose.
        </p>
      )}
      {month.uncategorised > 0 && (
        <p className="rule-card__what">
          {euros.format(month.uncategorised)} more left the account under nothing at all. Put a
          category on those spends and they join the figures above.
        </p>
      )}
      {month.budgeted === null ? (
        <p className="rule">
          You have not said what any of these is allowed to cost. Decide before the month rather than
          after it: a figure set now is something you can still act on, and one worked out at the end
          is only ever news.
        </p>
      ) : (
        <p className="rule">
          Worked out from what you budgeted, the bills that went out on the categories you filed them
          in, and the spends you recorded. Nothing is stored — correct a spend and this changes with
          it.
        </p>
      )}
    </article>
  )
}

/**
 * One category's last few months, side by side, with the average behind them.
 *
 * <p><strong>What this exists for is the judgement a single month cannot make.</strong> A hundred
 * and forty euros of groceries is alarming after three months of ninety and ordinary after three
 * months of a hundred and forty, and nothing but the months behind it tells the two apart. So the
 * months are drawn next to each other and the sentence under them says, in words, how this one sits
 * against the last few.
 *
 * <p><strong>Nothing here is worked out.</strong> What each month cost, what it was allowed, the
 * difference, the average and this month's distance from it all arrive already decided, which is
 * what stops this page and the card above it giving two answers about one month. The only arithmetic
 * below is how tall to draw a bar, which is a fact about pixels rather than about money.
 *
 * <p><strong>The bars are drawn against the tallest month rather than against the budget.</strong>
 * The question this section answers is "is this month unusual", and a month before the customer had
 * a budget has no budget to be drawn against at all — a section that needed one would have to leave
 * those months blank, which is precisely the months it is most worth showing. Whether a month went
 * over its own figure is said by its colour and by the row under it, where the figure exists.
 *
 * <p><strong>A month the category was not live in is a gap and never a nought.</strong> The window
 * belongs to the account and is the same six months for every category on it; a category younger
 * than the window, or one ended inside it, has fewer rows, and the months it has nothing to say
 * about are drawn as an absence. A nought there would claim the customer spent nothing on something
 * that was not one of the things they spent on — and it is what an average over those months would
 * be quietly built from.
 *
 * <p>The average is absent rather than nought where there are no months behind this one, and the
 * sentence says so instead of quoting a figure. A first month reported as infinitely above its habit
 * would be a number this page had invented.
 */
function TheMonthsBehind({
  category,
  windowOfMonths,
  place,
}: {
  category: CategoryCompared
  // Named at length rather than `window`, which is the browser's own and is the one variable name
  // that should never be shadowed in a file this size.
  windowOfMonths: string[]
  place: number
}) {
  const byMonth = new Map(category.months.map((row) => [row.month, row]))
  // The tallest month in this category's own run, which is what every bar in the row is drawn
  // against. Nought when nothing has ever gone out under it, and the guard below keeps a division
  // by nought from drawing six bars of NaN.
  const tallest = category.months.reduce((most, row) => Math.max(most, row.spent), 0)

  return (
    <article className="card reveal behind" style={rowDelay(place)}>
      <h3 className="card__title">
        {category.name}
        {category.categoryState === 'ENDED' && <span className="behind__ended">ended</span>}
      </h3>

      <ol className="behind__months">
        {windowOfMonths.map((month) => {
          const row = byMonth.get(month)
          return (
            <li key={month} className="behind__month">
              <span className="behind__bar">
                {row !== undefined && (
                  <i
                    className={row.overspent ? 'behind__fill behind__fill--over' : 'behind__fill'}
                    style={
                      {
                        '--h': `${tallest === 0 ? 0 : Math.round((row.spent / tallest) * 100)}%`,
                      } as CSSProperties
                    }
                  />
                )}
              </span>
              <span className="behind__when">{asAShortMonth(month)}</span>
              {/* An em dash rather than EUR 0.00 on a month this category was not one of the
                  things the money went on. The two are different claims and only one of them is
                  true. */}
              <span className="behind__spent">
                {row === undefined ? '—' : euros.format(row.spent)}
              </span>
              <span className="behind__budget">
                {row === undefined || row.budgeted === null
                  ? 'No budget'
                  : `of ${euros.format(row.budgeted)}`}
              </span>
            </li>
          )
        })}
      </ol>

      <p className="behind__quote">{howThisMonthSits(category)}</p>
    </article>
  )
}

/**
 * How this month sits against the months behind it, in a sentence.
 *
 * <p>Words rather than a figure on its own, because the figure is only worth reading as a comparison:
 * "EUR 13,33" says nothing and "EUR 13,33 more than you usually spend" is the whole point. Three
 * different absences get three different sentences, because they are three different facts — the
 * category is over, it is too new to compare, or it is spending exactly what it usually does.
 */
function howThisMonthSits(category: CategoryCompared): string {
  if (category.thisMonth === null) {
    return `You ended ${category.name}. The months above are the ones it was live in, and they stay readable.`
  }
  const spentThisMonth = euros.format(category.thisMonth.spent)
  if (category.trailingAverage === null || category.comparedWithTheAverage === null) {
    return `${spentThisMonth} this month. There is nothing behind it to compare with yet — one month is a month, not a habit.`
  }
  const months =
    category.monthsTheAverageIsOver === 1
      ? 'the month before it'
      : `the ${category.monthsTheAverageIsOver} months before it`
  const usually = `${euros.format(category.trailingAverage)} a month over ${months}`
  if (category.comparedWithTheAverage === 0) {
    return `${spentThisMonth} this month, against ${usually}. Exactly what you usually spend.`
  }
  return category.comparedWithTheAverage > 0
    ? `${spentThisMonth} this month, against ${usually} — ${euros.format(category.comparedWithTheAverage)} more than usual.`
    : `${spentThisMonth} this month, against ${usually} — ${euros.format(Math.abs(category.comparedWithTheAverage))} less than usual.`
}

/**
 * A month short enough to sit under a bar in a row of six. The year is not on it, because the six
 * are consecutive and the account's own read says which window they are.
 */
function asAShortMonth(yearMonth: string): string {
  return theMonthShort.format(new Date(`${yearMonth}-01T00:00:00`))
}

const theMonthShort = new Intl.DateTimeFormat('nl-BE', { month: 'short' })

/**
 * The budget card on the current account's own screen: this month's totals, and the categories
 * closest to their limit.
 *
 * <p>Under what is still owed, because that is the order the money moves in — the salary lands, the
 * standing bills and the arrears are presented against it, and what is left is the part of the month
 * nobody writes down in advance. A customer who reads no further than this card has still been told
 * the one thing it exists to tell them.
 *
 * <p><strong>Three categories, and the ones nearest their limit.</strong> This is the account's
 * screen and not the budget screen: the whole list lives one press away, and a card that drew twenty
 * rows would be the budget screen with a different heading. Nearest is the fraction of what each is
 * actually allowed this month that it has used — the budget and whatever carried into it — so a
 * hundred euros over on a hundred-euro budget outranks a hundred over on a thousand, and a category
 * that a frugal spring paid for is not sorted to the top for spending money it really has.
 *
 * <p>Categories with no figure are left out of that shortlist rather than sorted to the bottom.
 * There is no limit to be near, and a row that could never move is a row that only takes the place
 * of one that can. So is an envelope whose overspend has left it allowing nothing or less: there is
 * no fraction of a negative allowance, and the row it would displace is one a customer can act on.
 */
function TheBudgetThisMonth({ month }: { month: MonthOfSpending }) {
  const nearestTheirLimit = month.categories
    .filter((row) => row.allowed !== null && row.allowed > 0)
    .sort((one, other) => other.spent / (other.allowed ?? 1) - one.spent / (one.allowed ?? 1))
    .slice(0, 3)

  return (
    <article className="card reveal">
      <dl className="split">
        <div>
          <dt>Budgeted</dt>
          <dd>{month.budgeted === null ? 'Not set' : euros.format(month.budgeted)}</dd>
        </div>
        <div>
          <dt>Spent</dt>
          <dd>{euros.format(month.spent)}</dd>
        </div>
        <div>
          <dt>Left</dt>
          <dd>{month.left === null ? 'Not set' : euros.format(month.left)}</dd>
        </div>
      </dl>
      {nearestTheirLimit.length > 0 && (
        <ul className="plain-list">
          {nearestTheirLimit.map((row) => (
            <li key={row.categoryId}>
              {row.name} · {euros.format(row.spent)} of {euros.format(row.allowed ?? 0)}
              {row.overspent
                ? ` · ${euros.format(Math.abs(row.left ?? 0))} over`
                : ` · ${euros.format(row.left ?? 0)} left`}
            </li>
          ))}
        </ul>
      )}
      <p className="rule">
        {month.budgeted === null
          ? 'Groceries, fuel, a round of drinks — the part of the month that nobody declares in advance. Name the things your money goes on, say what each is allowed to cost, and this account\'s spending is described in your own words rather than in this application\'s.'
          : `${asAMonth(month.month)} so far, against what you decided before it. The bills you have filed in a category count towards it, so what is left here is what you can still choose to spend.`}
      </p>
    </article>
  )
}

/**
 * Your agreement: the product this account is on, the version of its terms it was opened under, the
 * day it was opened, and what that product asks of you.
 *
 * <p><strong>Not what the product is selling today, and the panel says so in as many words.</strong>
 * Free savings has published a second version at a lower rate; an account opened before it carries
 * on under the first for as long as its holder wants it. Printing the catalogue's figures under
 * this heading would state the exact confusion the backend built two separate readings to remove,
 * so this panel draws only what came down with the account.
 *
 * <p><strong>The version is shown, and it is not an internal detail.</strong> It is the answer to
 * "what am I actually on", it is what every deposit in the history below is stamped with, and it is
 * the only thing on this page that tells two accounts apart that are paying different rates. A
 * panel that named the product and hid the version would be a panel that could not explain the
 * difference it exists to show.
 *
 * <p><strong>The conditions are said as sentences, including the absences.</strong> An instant
 * access account asks for nothing — no notice, no floor, no end date — and a panel that simply
 * omitted all three would read as a panel that had failed to load. It says what it asks for
 * instead, which is the sentence a customer weighing a notice account against this one needs to
 * have read first.
 *
 * <p>No rate, because the backend sends none — and it is right not to. What this agreement has
 * actually paid is the panel underneath, a month at a time, each month naming the rate it was paid
 * at and the balance it was worked out on. A figure repeated up here would be the same rate in two
 * places, and the day an account takes newer terms one of them would be wrong.
 *
 * <p>Nothing at all when the account has no agreement recorded, which is a database that has not
 * been through the backend's start-up migration. A panel is better absent than filled in with
 * something this page decided.
 */
function YourAgreement({ agreement }: { agreement: TheAgreement | null }) {
  if (agreement === null) {
    return null
  }
  return (
    <article className="card reveal">
      <h2 className="card__title">Your agreement</h2>
      <p className="agreement__product">{agreement.productName}</p>
      <p className="rule">{whatThisAgreementAsksFor(agreement)}</p>
      <dl className="split">
        <div>
          <dt>Terms</dt>
          <dd>Version {agreement.version}</dd>
        </div>
        <div>
          <dt>Opened</dt>
          <dd>{asADay(agreement.openedOn)}</dd>
        </div>
        {agreement.maturesOn !== null && (
          <div>
            <dt>Matures</dt>
            <dd>{asADay(agreement.maturesOn)}</dd>
          </div>
        )}
      </dl>
    </article>
  )
}

/**
 * What this agreement asks of its holder, in one sentence built from the figures rather than from
 * the kind.
 *
 * <p>Built from the figures on purpose. The backend sends every condition on every agreement, with
 * the absent ones as zero and no maturity date as `null`, so one reading covers all four products
 * and a fifth one changes nothing here. A page that switched on `productKind` would be a second
 * place that had to learn every product this bank ever sells.
 *
 * <p>An account with nothing to give notice of and nothing to keep in gets a sentence of its own
 * rather than an empty line, because "this asks nothing of you" is the thing the freest product is
 * being paid less for, and a customer weighing it against a notice account has to read it.
 */
function whatThisAgreementAsksFor(agreement: TheAgreement): string {
  const asked: string[] = []
  if (agreement.noticeDays > 0) {
    asked.push(`${agreement.noticeDays} days' notice before money leaves`)
  }
  if (agreement.minimumBalance > 0) {
    asked.push(`${euros.format(agreement.minimumBalance)} kept in for the bonus rate`)
  }
  if (agreement.maturesOn !== null) {
    asked.push(`the money left in place until ${asADay(agreement.maturesOn)}`)
  }
  return asked.length === 0
    ? 'Money in and out whenever you like. Nothing to give notice of, nothing to keep in.'
    : `This agreement asks for ${asked.join(', and ')}.`
}

/**
 * A savings account as this page reads it: what it is worth, and every movement of money in and out
 * of it. All three arrive together, because the money balance only makes sense beside both lists.
 */
type SavingsAccountView = {
  balances: SavingsAccountBalances
  deposits: RecordedDeposit[]
  withdrawals: RecordedWithdrawal[]
  /**
   * Every month of interest this account has been paid. Read with the rest rather than on its own,
   * for the reason the lists are: the interest is part of the balance beside it, so a panel
   * showing months the balance does not account for would be two answers to one question.
   */
  interest: InterestPaid[]
  /**
   * What the account has coming in the year ahead. Read with the rest rather than on its own,
   * because it is derived from the same deposits the lists below are: a bar promising a bonus on a
   * deposit the history beside it does not show would be two answers to one question.
   */
  coming: AccountTimeline
}

/**
 * The thing that just happened on this account and is worth saying out loud for a moment. One at a
 * time, because a person did one thing: they paid money in, or they took some back out.
 *
 * <p>Claiming a reward is not one of them. Points belong to the customer, so a claim is made on the
 * rewards screen and celebrated there.
 */
type Celebration =
  | { kind: 'deposit'; deposit: RecordedDeposit }
  | { kind: 'withdrawal'; withdrawal: RecordedWithdrawal }

/**
 * One savings account: what it holds, what its holder has to spend, the deposits and withdrawals
 * behind the money, and the forms that move it.
 *
 * <p>Nothing on this page is computed here. Every figure is read back from the backend after a
 * deposit, so what is on screen is the derived answer rather than a guess this page kept in step by
 * itself.
 *
 * <p>Rewards are not claimed from here. The points beside this balance are the customer's rather
 * than this account's, so there is one place to spend them and it is the rewards screen.
 */
function SavingsAccountPage({
  savingsAccountId,
  currentAccounts,
  savingsAccounts,
  onChanged,
  notifications,
  accountsError,
  onBack,
  onOpenAutomaticSaving,
  onOpenSimulator,
}: {
  savingsAccountId: number
  currentAccounts: CurrentAccount[]
  /**
   * Every savings account this customer holds, so that moving money to another of their own is a
   * choice from a list rather than an account number somebody has to go and find. Handed down
   * rather than fetched here, because the overview above has just read it and a second request
   * would be a second answer to the same question.
   */
  savingsAccounts: SavingsAccount[]
  onChanged: () => void
  notifications: Notification[]
  accountsError: string | null
  onBack: () => void
  onOpenAutomaticSaving: () => void
  onOpenSimulator: () => void
}) {
  const [account, setAccount] = useState<SavingsAccountView | null>(null)
  const [accountError, setAccountError] = useState<string | null>(null)
  // What just happened, kept only long enough to say so. It is the backend's own answer to the
  // request, so the celebration cannot congratulate someone for points they did not get.
  const [celebrated, setCelebrated] = useState<Celebration | null>(null)
  // Bumped every time money crosses the boundary between this account and an everyday one. The
  // goals below are claims on the balance, so a deposit or a withdrawal changes what is spare, what
  // each goal gets each week and every projection under it — none of which passes through anything
  // this page holds. It is a count rather than a flag because two deposits in a row are two
  // changes, and a flag that was already true the second time would read nothing back.
  const [moneyMoved, setMoneyMoved] = useState(0)

  /**
   * The balances, both lists behind them and the year ahead, read back as one thing. Loading them
   * separately would let one of the four fail and leave a balance on screen beside lists that do not
   * account for it — which is the one thing showing the lists is meant to let someone check: the
   * deposits say what came in, the withdrawals say what went back out, and the money balance is what
   * the two leave behind.
   *
   * <p>The year ahead is read with them for the same reason one step on: it is derived from those
   * same deposits, so a bar promising a bonus on a deposit the history beside it does not show would
   * be two answers to one question — and a withdrawal changes both at once.
   */
  const loadAccount = useCallback((signal?: AbortSignal) => {
    Promise.all([
      fetchSavingsAccount(savingsAccountId, signal),
      fetchDeposits(savingsAccountId, signal),
      fetchWithdrawals(savingsAccountId, signal),
      fetchTimeline(savingsAccountId, signal),
      fetchInterestPaid(savingsAccountId, signal),
    ])
      .then(([balances, deposits, withdrawals, coming, interest]) => {
        if (signal?.aborted !== true) {
          setAccount({ balances, deposits, withdrawals, coming, interest })
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

  // The one thing said about this account that its holder has not read. Nothing here sorts: the
  // list arrives newest first, as the backend sends it, so the first one that matches is the newest
  // one. Another pot's notification never matches, and neither does one already read — which is
  // what makes the notice go away by itself once the panel has been opened and everything in it
  // marked.
  const notice =
    notifications.find(
      (said) => said.savingsAccountId === savingsAccountId && said.readAt === null,
    ) ?? null

  // Whether there is anything left to do about the agreement, which is the one thing the page —
  // rather than any panel on it — has to decide, because it draws the heading the three panels sit
  // under. A closed account is offered no newer terms, is empty by construction so has nothing to
  // move, and cannot be closed twice; an account with no agreement on record has none of the three
  // either. Neither is a rule restated: it is the same `closedOn` each of those panels reads, and
  // what is being decided here is a heading and never whether a panel is drawn.
  const theAgreementIsOpen =
    account !== null &&
    account.balances.agreement !== null &&
    account.balances.agreement.closedOn === null

  return (
    <section className="view">
      <button type="button" className="link link--back" onClick={onBack}>
        <BackIcon />
        All accounts
      </button>

      {accountError !== null && <Refusal reason={accountError} standing />}

      {/* The customer-level read, which is where the everyday accounts on both forms come from. It
          is a different read from the one above and it fails on its own, and when it does the two
          forms fall back to an empty list and state that the customer holds no current account —
          a sentence this page wrote, and a false one. The backend's own words go here instead. */}
      {accountsError !== null && <Refusal reason={accountsError} standing />}

      {account === null && accountError === null && (
        <div className="card">
          <Waiting label="Loading the account…" bars={['9rem', '100%', '60%']} />
        </div>
      )}

      {account !== null && (
        <div className="grid grid--overview">
          <article className="card reveal">
            {/* The account's own name is the headline in the band above, so this card leads with
                the one fact the band cannot carry: who the backend says holds it. */}
            <div className="sheet__head">
              <span className="acc__icon" style={hueOf(savingsAccountId)} aria-hidden="true">
                <PotIcon />
              </span>
              <h2 className="card__title">Held by {account.balances.customerName}</h2>
            </div>

            {/* The figure moves to what it now is: a deposit or a withdrawal is a number changing,
                and seeing it change is what tells you it landed. */}
            <p className="amount amount--xl">
              <Rising value={account.balances.moneyBalance} format={(shown) => euros.format(shown)} />
            </p>

            {/* The week, under what the account holds altogether rather than beside it: "saved
                here in total" is history, and "saved since Monday" is the thing there is still
                time to change. Both the cell and the bar are drawn from the one rising figure. */}
            <Rising
              value={account.balances.newSavingsThisWeek}
              format={(shown) => (
                <>
                  <dl className="split">
                    <div>
                      <dt>Saved this week</dt>
                      <dd>{euros.format(shown)}</dd>
                    </div>
                    <div>
                      <dt>Weekly minimum</dt>
                      <dd>{euros.format(account.balances.weeklyMinimum)}</dd>
                    </div>
                  </dl>
                  <WeekBar shown={shown} weeklyMinimum={account.balances.weeklyMinimum} />
                </>
              )}
            />
          </article>

          <PointsCard held={account.balances} delay="60ms" />
        </div>
      )}

      {/* ──────────────────────────────────────────────────────────────────────────────────
          THE ORDER OF THIS PAGE, AND THE ARGUMENT FOR IT.

          Nine slices each hung a panel on this screen as they landed, and three of them
          documented themselves as sitting "directly under the agreement", which is a place only
          one panel can be. What follows is the order decided once, on purpose, and it is three
          questions in the order a customer asks them:

          1. WHAT THIS ACCOUNT IS LIVING UNDER. The agreement first, because it is the only panel
             that names the thing every other panel here is about. Then its conditions, in the
             order the backend itself asks them on the way out — a term that has not matured, then
             notice that has not run, then the floor — so that somebody reading downwards meets the
             refusals in the order they would actually meet them. A term is asked first because it
             is one lock over every euro at once and nothing about amounts matters inside it; the
             floor is last because it is the condition that never holds money back at all. Last in
             the group is what all of that comes to this morning, as one figure: the conclusion the
             four panels above raise and none of them answers.

          2. WHAT IT HAS PAID. Interest, month by month, and then the year ahead. History before
             promises, and both after the rules, because a rate is only readable once you know
             which agreement wrote it.

          3. CHANGING THIS AGREEMENT. Newer terms, moving the money to another of your own
             accounts, and closing the account: the three presses that end or replace what group 1
             described. They come after it, because every one of them is a decision about something
             the customer has just finished reading, and the panels quote what each costs before
             the button rather than after.

          Then the page carries on as it did: automation, the simulator, the goals that claim this
          money, the two forms that move it by hand, and the histories behind it.

          A PANEL THAT DOES NOT APPLY IS ABSENT AND NEVER EMPTY. Each of these decides that for
          itself, off a figure the backend sends for every account — no notice panel on free
          savings, no floor on a fixed term, no maturity on an instant-access account, no interest
          before the first month has been judged, and nothing at all about what can leave an
          account holding nothing. That is why the page reads short on the products that ask for
          little and long on the one that asks for most, rather than reading the same length with
          four apologies on it.

          The exception worth naming is the closing panel, which is two panels in one: a statement
          that the account is closed, and a button that closes it. The statement belongs with what
          the account is living under, because on a closed account it is the most important thing
          there is to say; the button belongs with the other things you can do about the agreement.
          So it is rendered in group 1 or in group 3 according to which of the two it is about to
          be, and never in both. ────────────────────────────────────────────────────────────── */}

      {account !== null && (
        <div className="section-head">
          <h2>What this account is living under</h2>
        </div>
      )}

      {/* First in the group and first on the page after the figures, because it is what every one
          of those figures is decided by: what this money is allowed to do, and under which version
          of whose terms. */}
      {account !== null && <YourAgreement agreement={account.balances.agreement} />}

      {/* The statement half of the closing panel, directly under the agreement it is about. An
          account that has ended is not a thing a customer can do anything with, and the sentence
          saying so has to be read before the deposits, the goals and the rules underneath that all
          go on reading exactly as they did. */}
      {account !== null && (account.balances.agreement?.closedOn ?? null) !== null && (
        <ClosingThisAccount
          savingsAccountId={savingsAccountId}
          agreement={account.balances.agreement}
          moneyBalance={account.balances.moneyBalance}
          onClosed={() => {
            loadAccount()
            onChanged()
          }}
        />
      )}

      {/* The first condition the backend asks on the way out, so the first one here: an account
          inside its term refuses every euro, and somebody reading downwards should meet the lock
          before anything about amounts. Absent altogether on an account that is not on a term,
          which is three products in four. */}
      <TheTermYouAreLockedInto
        savingsAccountId={savingsAccountId}
        afterMoneyMoved={moneyMoved}
        onBroken={() => {
          // Three things moved at once and the page holds none of them: the balance fell by the
          // charge, the agreement is a different agreement, and the money history has a row in it
          // that was not there a second ago.
          loadAccount()
          setMoneyMoved((moves) => moves + 1)
        }}
      />

      {/* The second condition, in the same order, and the agreement's own sentence seen from the
          customer's side: the panel above says "32 days", and this says which of your money has
          already waited them. Absent altogether on an account with nothing to give notice of. */}
      <TheNoticeYouHaveGiven savingsAccountId={savingsAccountId} afterMoneyMoved={moneyMoved} />

      {/* The third and last condition, and the only one that never holds a euro back: the
          agreement says "EUR 500,00 kept in for the bonus rate", and this says how much of what is
          in here you could take today without losing it. Absent altogether on the three products
          with no floor to keep. */}
      {account !== null && (
        <TheFloorYouAreKeeping
          agreement={account.balances.agreement}
          moneyBalance={account.balances.moneyBalance}
        />
      )}

      {/* The conclusion of the four panels above, and the end of the group: one figure for what
          the agreement would actually let leave this morning, with the backend's own sentence when
          it is less than the balance. It is read through a door of its own rather than composed
          here, because the order those conditions compose in is the one thing this page must not
          hold a second copy of. */}
      <WhatYouCanTakeToday savingsAccountId={savingsAccountId} afterMoneyMoved={moneyMoved} />

      {/* What the agreement above has actually paid, a month at a time, each month naming the rate
          it was paid at, the balance it was worked out on and — on an account with a bonus to earn
          — whether the bonus was earned. It draws its own heading, so the heading goes when the
          panel does. */}
      {account !== null && (
        <WhatInterestHasBeenPaid
          interest={account.interest}
          agreement={account.balances.agreement}
        />
      )}

      {/* What has been paid, then what is coming: the year ahead follows the months behind for the
          same reason the whole group follows the agreement above it. It draws its own heading. */}
      {account !== null && <YearAhead coming={account.coming} />}

      {/* Nothing to change about an agreement that has ended: a closed account is offered no newer
          terms, has nothing left to move and cannot be closed twice, so the heading would stand
          over an empty stretch of page. Agreement-less accounts get no heading either, for the
          reason the agreement panel draws nothing for them. */}
      {theAgreementIsOpen && (
        <div className="section-head">
          <h2>Changing this agreement</h2>
        </div>
      )}

      {/* The same question the agreement panel answers, asked from the other end: this is what you
          are on, and this is what the same product is offering today. Nothing here moves anybody —
          the button is the whole of how an account takes newer terms, and newer is not the same as
          better. Absent altogether for an account whose product has published nothing since it was
          opened, which is most of them. */}
      {account !== null && (
        <TheNewerTermsOnOffer
          newerTerms={account.balances.newerTerms}
          agreement={account.balances.agreement}
          onTaken={() => {
            loadAccount()
            onChanged()
          }}
        />
      )}

      {/* Leaving this agreement for another of your own, which is what a customer weighing the
          rate, the lock and the notice they have just read does about them. The panel quotes the
          one thing it costs before the button rather than after. Absent when they hold nothing
          else to move to. */}
      <MovingMoneyToAnotherAccount
        savingsAccountId={savingsAccountId}
        savingsAccounts={savingsAccounts}
        onMoved={() => {
          // Four things moved at once and the page holds none of them: the balance here, the
          // balance there, the money history, and what each deposit's anniversary is worth.
          loadAccount()
          setMoneyMoved((moves) => moves + 1)
          onChanged()
        }}
      />

      {/* The button half of the closing panel, last in the group because ending the agreement is
          the last thing there is to do about it. The statement half, for an account that has
          already ended, is drawn in the first group instead. */}
      {theAgreementIsOpen && account !== null && (
        <ClosingThisAccount
          savingsAccountId={savingsAccountId}
          agreement={account.balances.agreement}
          moneyBalance={account.balances.moneyBalance}
          onClosed={() => {
            loadAccount()
            onChanged()
          }}
        />
      )}

      {/* Above the forms rather than below them, because a warning about what a withdrawal would
          cost has to be read before the amount is typed. */}
      {notice !== null && <Notice notification={notice} />}

      {/* The way through to the rules standing against this account, above the forms that move
          money by hand: automation is the other way money gets in here, and a customer who has
          left a rule standing should find it on the page the money lands on rather than by
          remembering where it was set up. */}
      <div className="section-head">
        <h2>Automatic saving</h2>
        <button type="button" className="link link--small" onClick={onOpenAutomaticSaving}>
          Rules, income and the year ahead
          <ForwardIcon />
        </button>
      </div>
      <article className="card reveal">
        <p className="rule">
          Money can move itself into this pot — every week, every month, or on payday — and what a
          rule moves earns points exactly as a deposit you make yourself does.
        </p>
      </article>

      {/* Beside the way through to the rules rather than anywhere else on the page, because the two
          are the same question asked twice: the rules are what this pot is already going to do over
          the next twelve months, and the simulator is what it would do instead. Somebody who has
          just read what their automation will do is exactly the person wondering whether a different
          one would do better. */}
      <div className="section-head">
        <h2>What if I did this instead</h2>
        <button type="button" className="link link--small" onClick={onOpenSimulator}>
          Compare futures side by side
          <ForwardIcon />
        </button>
      </div>
      <article className="card reveal">
        <p className="rule">
          Put another twenty-five a week away, stop for a couple of months, take five hundred out or
          move a deadline — and see the year each of those leads to, beside the year you are already
          in. Asking changes nothing at all.
        </p>
      </article>

      {/* Above the forms for the same reason, one step stronger: what no goal has claimed is the
          figure a withdrawal is measured against, and somebody who has not seen it cannot tell
          which amount is about to be refused. It reads the plan itself rather than taking it from
          the account read above — the goals module answers separately, and a failure on one of the
          two is no reason to hide the other. */}
      <Goals
        savingsAccountId={savingsAccountId}
        currentAccounts={currentAccounts}
        afterMoneyMoved={moneyMoved}
      />

      {/* Outside the check above on purpose: a read that failed is no reason to stop someone
          depositing, and the deposit is what will read the account again. */}
      <div className="section-head">
        <h2>Move money</h2>
      </div>
      <div className="grid grid--pair">
        {/* No deposit form on an account whose agreement has ended, because the backend now refuses
            the deposit: a form that could only ever be refused is a promise this page cannot keep,
            and somebody would type an amount to find that out. The panel above already says the
            account is closed and on which day, so nothing is drawn in its place here — the answer
            is on the screen, above the heading that used to offer both halves of moving money.
            The withdrawal form stays: an account can only be closed once it is empty, and money
            that somehow arrives in one later must not be trapped in it. */}
        {(account?.balances.agreement?.closedOn ?? null) === null && (
          <DepositForm
            currentAccounts={currentAccounts}
            savingsAccountId={savingsAccountId}
            // Handed back down so the celebration rises out of the card that earned it rather
            // than out of the gutter between the two forms. The page owns how long it lasts,
            // because the page is what clears it.
            justEarned={celebrated?.kind === 'deposit' ? celebrated.deposit : null}
            onDeposited={(made) => {
              setCelebrated({ kind: 'deposit', deposit: made })
              loadAccount()
              setMoneyMoved((moves) => moves + 1)
              // The money came out of a current account, and that figure is on the overview.
              onChanged()
            }}
          />
        )}
        <WithdrawalForm
          currentAccounts={currentAccounts}
          savingsAccountId={savingsAccountId}
          onWithdrawn={(made) => {
            setCelebrated({ kind: 'withdrawal', withdrawal: made })
            loadAccount()
            setMoneyMoved((moves) => moves + 1)
            onChanged()
          }}
        />
      </div>

      {account !== null && (
        <div className="grid grid--pair">
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

/* ----------------------------------------------------------------------- goals
 *
 * What a savings account is being saved towards, under the account it belongs to.
 *
 * <p>The layout follows what the numbers mean. What no goal has claimed sits at the top and sits
 * large, because it is the figure that constrains every action on this screen — allocating,
 * withdrawing, taking a suggestion — and somebody who cannot see it cannot predict which of the
 * things they are about to do will be refused. The weekly figure sits beside it, because the two
 * scarcities are different: one is money already saved, the other is money that can still be saved,
 * and every projected day on the screen below comes out of the second one.
 *
 * <p>Nothing here is computed. The weekly amounts, the projections and the statuses are the
 * backend's, read back after every change, and the refusals are the backend's sentences shown as
 * they arrived.
 */

/**
 * The three reads that make up the plan, held as one thing.
 *
 * <p>One state rather than three, for the reason the account page gives about its own four reads:
 * they answer one question between them and they move together. Allocating money changes what is
 * spare, what each goal gets each week, every projection below it and whether there is anything
 * left worth suggesting — so a screen holding one of the three from before the change would be
 * showing two answers to one question.
 */
type ThePlan = {
  allocations: AllocationsOnAnAccount
  capacity: SavingCapacity
  suggestion: SuggestedReallocation
}

/**
 * What taking a suggestion did, and whether what is on screen underneath it is from after it.
 *
 * <p>Two facts rather than one because they can disagree. The acceptance either moved money or it
 * did not — it can come back with no moves at all, when the plan changed between the screen being
 * read and the button being pressed — and the read that follows it either landed or did not. A
 * screen that kept only the moves would have to guess which figures it was printing them over.
 */
type WhatAcceptingDid = {
  moves: SuggestedMove[]
  readBack: boolean
}

/** Each status in the one word this page shows it as. */
const STATUS_WORDS: Record<GoalStatus, string> = {
  STILL_SAVING: 'Still saving',
  COMPLETED: 'Reached',
  ON_TRACK: 'On track',
  OFF_TRACK: 'Off track',
  UNREACHABLE: 'Unreachable',
  NO_DEADLINE: 'No deadline',
  ABANDONED: 'Abandoned',
}

/**
 * The class that colours each status, so no two of them are told apart by their wording alone.
 *
 * <p>`OFF_TRACK` and `UNREACHABLE` get the two ends of the warning scale rather than one shade
 * between them, and a different mark each. They are different answers: off track is a day that
 * lands after the deadline, which more money or more time fixes; unreachable is a projection that
 * does not land at all, which is a different conversation.
 *
 * <p>The three that carry no verdict — nothing to project yet, nothing to be late for, and closed
 * — are not warnings, so none of them is coloured like one. They are still told apart from each
 * other: every class here has its own rule in `index.css`, so no two of the seven share a colour.
 *
 * <p>`StatusMark` names all seven and gives six of them their own mark. `COMPLETED` and `ON_TRACK`
 * deliberately share the tick: they are the two pieces of good news, and a mark that separated them
 * would be saying something the green and the blue already say. Those two aside, a status is said
 * three times over — word, colour and mark — and never by its wording alone.
 *
 * <p>`ABANDONED` cannot reach this list — a closed goal leaves the plan and is read back through
 * its own endpoint, which this screen does not call — but it is a status, so it is given a look of
 * its own rather than a default that would make it look like one of the others.
 */
const STATUS_TONES: Record<GoalStatus, string> = {
  STILL_SAVING: 'saving',
  COMPLETED: 'reached',
  ON_TRACK: 'on-track',
  OFF_TRACK: 'off-track',
  UNREACHABLE: 'unreachable',
  NO_DEADLINE: 'no-deadline',
  ABANDONED: 'abandoned',
}

/** The mark beside the word, so the status is legible before it is read. */
function StatusMark({ status }: { status: GoalStatus }) {
  switch (status) {
    case 'COMPLETED':
    case 'ON_TRACK':
      return <TickIcon />
    case 'OFF_TRACK':
      return <ClockIcon />
    case 'UNREACHABLE':
      return <WarningIcon />
    // Money is going in and there is nothing to project it against yet, so the pot and nothing
    // about a day.
    case 'STILL_SAVING':
      return <PotIcon />
    // A goal with no day to be late for: time runs on past the right-hand edge.
    case 'NO_DEADLINE':
      return <OpenEndedIcon />
    // A goal that was closed. The one mark on the card that says nothing is happening here.
    case 'ABANDONED':
      return <ClosedIcon />
  }
}

/**
 * Everything this savings account is being saved towards, and the plan that funds it.
 *
 * <p>`afterMoneyMoved` is bumped by the page above whenever a deposit or a withdrawal lands. It is
 * a prop rather than a shared read because the balance is what the goals are claims on: money
 * arriving changes what is spare, and money leaving changes it the other way, and neither of them
 * goes through anything on this component.
 */
function Goals({
  savingsAccountId,
  currentAccounts,
  afterMoneyMoved,
}: {
  savingsAccountId: number
  /**
   * The customer's everyday accounts, passed through to the weekly capacity so that the figure
   * their budget says they could save can be drawn beside the figure they declared. The goals
   * module knows nothing about budgets and the budgets module knows nothing about goals; the two
   * are put side by side here, which is where this application already puts modules that must not
   * know about each other.
   */
  currentAccounts: CurrentAccount[]
  afterMoneyMoved: number
}) {
  const [plan, setPlan] = useState<ThePlan | null>(null)
  const [planError, setPlanError] = useState<string | null>(null)
  // Refusals from the two things done to the account rather than to one goal: changing the order,
  // and taking the suggestion. A goal's own refusals stay inside that goal's card, next to the
  // control that caused them.
  const [accountError, setAccountError] = useState<string | null>(null)
  const [reordering, setReordering] = useState<number | null>(null)
  const [accepting, setAccepting] = useState(false)
  // The suggestion is derived on every read and stored nowhere, so ignoring it is a decision this
  // screen holds and nothing else does. Every read clears it, which is what makes an ignored
  // suggestion come back rather than stay gone.
  const [ignored, setIgnored] = useState(false)
  // What accepting actually moved, in the past tense, built from the moves that came back. The
  // suggestion's own `inWords` is present-tense advice — "2 moves worth making" — and the backend
  // hands the same sentence back under `applied`, where printing it would tell somebody there are
  // still two moves worth making a moment after they took them.
  const [applied, setApplied] = useState<WhatAcceptingDid | null>(null)

  /**
   * Reads the three things this screen is, and answers whether what is on screen is now the plan.
   *
   * <p>It resolves rather than rejects when a read fails, because every caller wants the refusal
   * shown and nothing else, but it says which happened: `accept` has something to put on the
   * screen that is only true if the figures beside it came from after the money moved.
   *
   * <p>Only the newest read paints. Two actions a moment apart — a goal freed while another is
   * allocated to — each read the plan back, and three requests answered out of order would leave
   * the screen showing the account as it was between them. The counter is what makes the boolean
   * honest as well: it says what is on screen came from this read, and a read overtaken by a later
   * one did not put anything there.
   */
  const reads = useRef(0)
  const loadPlan = useCallback(
    (signal?: AbortSignal): Promise<boolean> => {
      const mine = ++reads.current
      return Promise.all([
        fetchAllocations(savingsAccountId, signal),
        fetchSavingCapacity(savingsAccountId, signal),
        fetchSuggestedReallocation(savingsAccountId, signal),
      ])
        .then(([allocations, capacity, suggestion]) => {
          if (signal?.aborted === true || mine !== reads.current) {
            return false
          }
          setPlan({ allocations, capacity, suggestion })
          setPlanError(null)
          setAccountError(null)
          setIgnored(false)
          setApplied(null)
          return true
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true && mine === reads.current) {
            setPlanError(problem.message)
          }
          return false
        })
    },
    [savingsAccountId],
  )

  useEffect(() => {
    const request = new AbortController()
    loadPlan(request.signal)
    return () => request.abort()
  }, [loadPlan, afterMoneyMoved])

  /**
   * Moves one goal one place, by sending the whole order.
   *
   * <p>The list on screen is the order, so the swap is worked out here and the result is what goes
   * up — in one call, because a strict order has no valid state between two of them.
   *
   * <p>While one is in flight every goal's arrows are dead, not only the moved goal's. All of them
   * work the next order out from the same list on screen, which is the order from before the call
   * that has not come back, so a second click anywhere in the list would send an order that had
   * never seen the first.
   */
  function reorder(goalId: number, by: -1 | 1) {
    if (plan === null) {
      return
    }
    const order = plan.allocations.goals.map((goal) => goal.id)
    const at = order.indexOf(goalId)
    const to = at + by
    if (at < 0 || to < 0 || to >= order.length) {
      return
    }
    const moved = [...order]
    moved[at] = order[to]
    moved[to] = order[at]
    setReordering(goalId)
    setAccountError(null)
    reorderGoals(savingsAccountId, moved)
      .then(() => loadPlan())
      .catch((problem: Error) => setAccountError(problem.message))
      .finally(() => setReordering(null))
  }

  /**
   * Takes the suggestion whole, then reads the plan back.
   *
   * <p>The acceptance answers with the new allocations, and they are not used: the capacity and the
   * suggestion are read in the same breath as them everywhere else on this screen, and taking one
   * of the three from the response and the other two from before it is the split this component
   * exists to avoid. What the moves were is kept from the response, because after the read there is
   * nothing left to suggest and no other record of what was just done.
   *
   * <p>Whether the read landed travels with the record, because the two answer different halves of
   * one question. The money moved either way and somebody has to be told so; what is underneath it
   * is only the result of that move if the read came back. When it did not, the record says the
   * figures are from before, and the suggestion is taken off the screen: it was worked out before
   * the money moved, and leaving a live Accept under an acceptance that has already happened would
   * offer the same move twice.
   */
  function accept() {
    setAccepting(true)
    setAccountError(null)
    acceptSuggestedReallocation(savingsAccountId)
      .then((taken) =>
        loadPlan().then((read) => setApplied({ moves: taken.applied.moves, readBack: read })),
      )
      .catch((problem: Error) => setAccountError(problem.message))
      .finally(() => setAccepting(false))
  }

  return (
    <>
      <div className="section-head">
        <h2>What this is for</h2>
      </div>

      {planError !== null && <Refusal reason={planError} standing />}
      {accountError !== null && <Refusal reason={accountError} standing />}

      {plan === null && planError === null && (
        <div className="card">
          <Waiting label="Loading the goals…" bars={['9rem', '100%', '60%']} />
        </div>
      )}

      {plan !== null && (
        <>
          <div className="grid grid--pair">
            <SpareMoney allocations={plan.allocations} />
            <WeeklyCapacity
              savingsAccountId={savingsAccountId}
              capacity={plan.capacity}
              currentAccounts={currentAccounts}
              onDeclared={loadPlan}
            />
          </div>

          {applied !== null && <WhatWasApplied applied={applied} />}

          {/* Suppressed only when an acceptance could not be read back: that banner was worked out
              before the money moved, so it is not advice any more, it is the past. A suggestion
              that survives a successful read is a new one and is offered. */}
          {plan.suggestion.worthSuggesting && !ignored && applied?.readBack !== false && (
            <Suggestion
              suggestion={plan.suggestion}
              accepting={accepting}
              onAccept={accept}
              onIgnore={() => setIgnored(true)}
            />
          )}

          {ignored && (
            <p className="goals__ignored">
              Left as it is, and nothing was moved. The suggestion is worked out fresh every time
              this screen is read, so it will be offered again on the next one.
            </p>
          )}

          {plan.allocations.goals.length === 0 ? (
            <p className="nothing">
              Nothing is being saved for on this account yet. Everything in it is spare.
            </p>
          ) : (
            <ul className="goals">
              {plan.allocations.goals.map((goal, place) => (
                <GoalCard
                  key={goal.id}
                  goal={goal}
                  place={place}
                  savingsAccountId={savingsAccountId}
                  first={place === 0}
                  last={place === plan.allocations.goals.length - 1}
                  reordering={reordering !== null}
                  onReorder={(by) => reorder(goal.id, by)}
                  onChanged={() => loadPlan()}
                />
              ))}
            </ul>
          )}

          <NewGoal savingsAccountId={savingsAccountId} onAdded={loadPlan} />
        </>
      )}
    </>
  )
}

/**
 * What no goal has claimed, large, above everything it constrains.
 *
 * <p>It is `balance − allocated`, worked out by the backend and never stored, and the two figures
 * it came from are under it so the sum can be read rather than trusted.
 */
function SpareMoney({ allocations }: { allocations: AllocationsOnAnAccount }) {
  return (
    <article className="card reveal">
      <h2 className="card__title">Not claimed by a goal</h2>
      <p className="amount amount--xl">
        <Rising value={allocations.unallocated} format={(shown) => euros.format(shown)} />
      </p>
      <p className="goals__lead">
        This is what a withdrawal can take and what a goal can be given. Anything beyond it is
        refused until money is freed from a goal.
      </p>
      <dl className="split">
        <div>
          <dt>In this account</dt>
          <dd>{euros.format(allocations.balance)}</dd>
        </div>
        <div>
          <dt>Claimed by goals</dt>
          <dd>{euros.format(allocations.allocated)}</dd>
        </div>
      </dl>
    </article>
  )
}

/**
 * The most its holder says they can put away in a week, the form that says it, and — beside it —
 * what their own budget says they could.
 *
 * <p>A capacity nobody has declared says so, in words. Showing 0,00 would be this screen answering
 * a question the customer has never been asked — and a plan built on a capacity of nothing gives
 * every goal nothing, which is a very loud answer to invent on somebody's behalf.
 *
 * <p><strong>The derived figure is offered and never applied.</strong> It comes from the budget on
 * the customer's everyday account — what the next six weeks leave, divided by six — and reading it
 * writes nothing anywhere. Adopting it is the press below, which sends that figure to this very
 * form's own address. The two halves of the application therefore agree without anybody retyping a
 * number, and the sentence the customer said about what they can afford stays theirs until they
 * say otherwise.
 *
 * <p>The two are joined here rather than by either side, because neither module knows the other
 * exists: the capacity belongs to the goals engine and hangs off a savings account, the forecast
 * belongs to the budgets and hangs off a current account, and this is the layer where the customer
 * sees both at once.
 *
 * <p>The first of the customer's everyday accounts, the way the rule form picks the account a rule
 * draws from. A household in this application is a current account, and a customer holding two has
 * two budgets; naming which one this offer came from is a question for a later screen rather than
 * a reason to show them neither.
 *
 * <p>A failed read of the forecast shows nothing at all — no refusal, no empty figure. This card's
 * job is the figure its holder declared, and a budget that could not be read is no reason to stop
 * them declaring one.
 */
function WeeklyCapacity({
  savingsAccountId,
  capacity,
  currentAccounts,
  onDeclared,
}: {
  savingsAccountId: number
  capacity: SavingCapacity
  currentAccounts: CurrentAccount[]
  onDeclared: () => void
}) {
  const [weekly, setWeekly] = useState('')
  const [declaring, setDeclaring] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)
  const [ahead, setAhead] = useState<TheWeeksAhead | null>(null)
  const [adopting, setAdopting] = useState(false)

  const everydayAccount = currentAccounts[0]?.id ?? null

  useEffect(() => {
    if (everydayAccount === null) {
      return
    }
    const request = new AbortController()
    fetchWeeksAhead(everydayAccount, request.signal)
      .then((forecast) => {
        if (request.signal.aborted !== true) {
          setAhead(forecast)
        }
      })
      .catch(() => undefined)
    return () => request.abort()
  }, [everydayAccount])

  function declare(event: FormEvent) {
    event.preventDefault()
    setDeclaring(true)
    setRefusal(null)
    declareSavingCapacity(savingsAccountId, weekly)
      .then(() => {
        setWeekly('')
        onDeclared()
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setDeclaring(false))
  }

  /**
   * The press that adopts the offer: the derived figure, sent to the address this form sends to.
   *
   * <p>Quoted to the cent as text, because that is what an amount is on the way in — the backend
   * decides what it will keep, in a sentence written for whoever typed it, and a number sent as a
   * number would have this page deciding instead.
   */
  function adopt() {
    if (ahead === null) {
      return
    }
    setAdopting(true)
    setRefusal(null)
    declareSavingCapacity(savingsAccountId, ahead.couldSaveWeekly.toFixed(2))
      .then(() => {
        setWeekly('')
        onDeclared()
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setAdopting(false))
  }

  const declared = capacity.declared && capacity.weeklyCapacity !== null
  const offered = ahead !== null && ahead.worthOffering
  const alreadyAdopted =
    offered && declared && capacity.weeklyCapacity === (ahead?.couldSaveWeekly ?? null)

  return (
    <article className="card reveal" style={{ '--delay': '60ms' } as CSSProperties}>
      <h2 className="card__title">Saved each week</h2>
      {declared ? (
        <p className="amount amount--xl">{euros.format(capacity.weeklyCapacity ?? 0)}</p>
      ) : (
        <p className="goals__unset">Not set yet</p>
      )}
      <p className="goals__lead">
        {declared
          ? 'What the plan below has to share out between the goals, in rank order.'
          : 'Until this is set there is nothing for the plan to share out, so no goal has a weekly amount and none of them has a projected day.'}
      </p>

      {/* The backend's own judgement, and its own figure for what a week costs. This screen does
          not compare the two: the €50 is named in one place and that place is not here. */}
      {capacity.aWeekIsNotSecuredAtThisRate && (
        <p className="goals__warn">
          <WarningIcon />
          <span>
            A streak week is not secured at this rate. Securing one asks for{' '}
            {euros.format(capacity.weeklyMinimum)} a week.
          </span>
        </p>
      )}

      {/* Outside the form on purpose, and a plain button rather than a submit: adopting is a
          different sentence from typing a figure, and a press that submitted the empty box beside
          it would refuse rather than adopt. */}
      {offered && ahead !== null && (
        <div className="capacity-offer">
          <p className="rule-card__what">
            Your budget says {euros.format(ahead.couldSaveWeekly)} a week. That is what the next six
            weeks leave once everything you have committed to and every budget spent in full is
            taken off, divided by six.
          </p>
          <Button
            tone="ghost"
            type="button"
            block
            busy={adopting}
            disabled={adopting || alreadyAdopted}
            onClick={adopt}
          >
            {adopting
              ? 'Taking it…'
              : alreadyAdopted
                ? 'This is what you have set'
                : `Use ${euros.format(ahead.couldSaveWeekly)}`}
          </Button>
        </div>
      )}

      <form onSubmit={declare}>
        <EuroAmount
          id="weeklyCapacity"
          label={declared ? 'New weekly amount' : 'Weekly amount you can save'}
          value={weekly}
          onType={setWeekly}
        />
        <Button tone="ghost" type="submit" block busy={declaring} disabled={declaring}>
          {declaring ? 'Saving…' : declared ? 'Change it' : 'Set it'}
        </Button>
        {refusal !== null && <Refusal reason={refusal} />}
      </form>
    </article>
  )
}

/**
 * The reallocation worth suggesting, as the moves it is made of.
 *
 * <p>Each move is named in words — this much, out of that goal, into this one — with the backend's
 * own sentence saying why underneath it. "We recommend rebalancing" would be the screen keeping the
 * reasoning to itself; the whole point of the engine is that the customer can see the trade-off and
 * disagree with it.
 *
 * <p>Accept takes it whole and ignore takes nothing. There is no third button, because the engine
 * offers a set of moves that only add up to something together.
 */
function Suggestion({
  suggestion,
  accepting,
  onAccept,
  onIgnore,
}: {
  suggestion: SuggestedReallocation
  accepting: boolean
  onAccept: () => void
  onIgnore: () => void
}) {
  return (
    <article className="card suggestion reveal">
      <div className="suggestion__head">
        <span className="suggestion__mark" aria-hidden="true">
          <SparkIcon />
        </span>
        <h2 className="card__title">
          {suggestion.moves.length === 1
            ? 'One move would help'
            : `${suggestion.moves.length} moves would help`}
        </h2>
      </div>

      <ul className="suggestion__moves">
        {suggestion.moves.map((move, place) => (
          <li key={place} style={rowDelay(place)}>
            <p className="suggestion__move">{worthMaking(move)}</p>
            {/* The backend's sentence, unchanged. It is the only thing on the screen that says why
                this move and not another, and rewording it here would make this page the second
                place that reasoning lived. */}
            <p className="suggestion__why">{move.reason}</p>
          </li>
        ))}
      </ul>

      <p className="goals__lead">
        Nothing has moved. This is worked out from the order of importance every time the screen is
        read, and it is stored nowhere.
      </p>

      <div className="suggestion__actions">
        <Button type="button" busy={accepting} disabled={accepting} onClick={onAccept}>
          {accepting ? 'Moving the money…' : 'Accept and move the money'}
        </Button>
        <Button tone="ghost" type="button" disabled={accepting} onClick={onIgnore}>
          Ignore
        </Button>
      </div>
    </article>
  )
}

/**
 * One end of a move. Either end can be money no goal has claimed rather than a goal, which is why
 * neither name is assumed to be there.
 */
function endOfAMove(goalName: string | null): string {
  return goalName === null ? 'money no goal has claimed' : `“${goalName}”`
}

/** A move that has not been made: the amount, where it would come from and where it would go. */
function worthMaking(move: SuggestedMove): string {
  return `Move ${euros.format(move.amount)} out of ${endOfAMove(move.outOfGoalName)} into ${endOfAMove(
    move.intoGoalName,
  )}.`
}

/**
 * The same move, once it has been made.
 *
 * <p>A second sentence rather than the first one reused, which is the whole reason this page does
 * not print the record's own `inWords` after accepting: advice and a record are different tenses,
 * and one string doing both jobs tells somebody there is still something to do.
 */
function alreadyMade(move: SuggestedMove): string {
  return `${euros.format(move.amount)} moved out of ${endOfAMove(move.outOfGoalName)} into ${endOfAMove(
    move.intoGoalName,
  )}.`
}

/**
 * What accepting just moved, in the past tense.
 *
 * <p>Built from the moves rather than from the record's own `inWords`, which is worded as advice —
 * "2 moves worth making" — and comes back unchanged from the acceptance. Printing that sentence
 * after the money had moved would tell somebody there was still something worth doing at the exact
 * moment there was not.
 *
 * <p>An acceptance can come back with no moves at all: the suggestion on screen is a reading from
 * a moment ago, and the backend works it out again at the moment it is taken, so anything that
 * changed the allocations in between — this customer in another window, a deposit, a withdrawal —
 * can leave nothing to do. That is not a success and does not get the green tick: nothing moved,
 * and the sentence says so.
 *
 * <p>`readBack` is the other half. The money has moved whatever it says, so the sentence about the
 * moves is the same; what changes is what the figures underneath are. A read that did not come
 * back leaves them as they were before the acceptance, and the band says so rather than letting
 * somebody read a stale allocation as the result of what they just did.
 */
function WhatWasApplied({ applied }: { applied: WhatAcceptingDid }) {
  const { moves, readBack } = applied
  const fromBefore = readBack ? null : (
    <>
      {' '}
      The figures below could not be read back afterwards, so they are the ones from before it.
      Opening this screen again reads them.
    </>
  )
  if (moves.length === 0) {
    return (
      <p className="goals__applied goals__applied--nothing" role="status">
        <ClosedIcon />
        <span>
          Nothing was moved. The suggestion is worked out again at the moment it is taken, and by
          then there was nothing left worth moving.
          {readBack ? ' The figures below are what this account holds now.' : fromBefore}
        </span>
      </p>
    )
  }
  return (
    <p className={`goals__applied${readBack ? '' : ' goals__applied--unread'}`} role="status">
      {readBack ? <TickIcon /> : <WarningIcon />}
      <span>
        {moves.length === 1 ? 'One move made. ' : `${moves.length} moves made. `}
        {moves.map(alreadyMade).join(' ')}
        {fromBefore}
      </span>
    </p>
  )
}

/**
 * What the card says the plan gives this goal each week.
 *
 * <p>The backend sends no figure at all — null, and deliberately never a zero — on an account where
 * nobody has declared a weekly capacity, because nobody having said how fast anything fills is a
 * different sentence from a plan in which this goal is given nothing. `euros.format` would print
 * `€ 0,00` for that null and the card would say the second thing, an inch under the capacity card
 * saying the first. So it is said in the same words that card uses: not set yet.
 *
 * <p>Once a capacity exists, 0.00 is a real answer and is printed as one: the capacity ran out
 * before this goal's turn came.
 */
function whatThePlanGives(goal: SavingsGoal) {
  return goal.weeklyAmount === null ? (
    <span className="goal__unsaid">Not set yet</span>
  ) : (
    euros.format(goal.weeklyAmount)
  )
}

/**
 * What the card says under “Gets there”.
 *
 * <p>Three different absences arrive here as the same missing date, and `status` is what tells them
 * apart, so this reads the status rather than guessing from the null:
 *
 * <ul>
 *   <li>`COMPLETED` — there is no day left to project because the goal is already there, and saying
 *       "not at this rate" under it would be the screen contradicting the chip above it.
 *   <li>`STILL_SAVING` — nobody has declared a weekly capacity, so there is no rate to project from.
 *       Not a verdict: a customer who has said nothing has not said they can save nothing, and this
 *       is the one line where the page could most easily put that plan in their mouth.
 *   <li>`UNREACHABLE` — there is a rate, and at it this goal never arrives. That one is a verdict,
 *       and it is the only case that earns the sentence.
 * </ul>
 */
function whenItGetsThere(goal: SavingsGoal) {
  if (goal.status === 'COMPLETED') {
    return 'Already there'
  }
  if (goal.status === 'STILL_SAVING') {
    return <span className="goal__unsaid">No projected day</span>
  }
  return goal.willBeReachedOn === null ? 'Not at this rate' : asADay(goal.willBeReachedOn)
}

/**
 * One goal: what it holds against what it is for, what the plan gives it each week, when that gets
 * it there, and everything that can be done to it.
 *
 * <p>Every control that can be refused keeps its refusal here, beside itself, because the sentence
 * that comes back names this goal and no other.
 */
function GoalCard({
  goal,
  place,
  savingsAccountId,
  first,
  last,
  reordering,
  onReorder,
  onChanged,
}: {
  goal: SavingsGoal
  place: number
  savingsAccountId: number
  first: boolean
  last: boolean
  reordering: boolean
  onReorder: (by: -1 | 1) => void
  /** Reads the plan back. Awaited, so this card's controls stay dead until the figures are its own. */
  onChanged: () => Promise<unknown>
}) {
  // One panel open at a time. Four forms unfolded at once on every goal would bury the figures the
  // goal is read for under the controls that change them.
  const [open, setOpen] = useState<'money' | 'weekly' | 'edit' | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  const [amount, setAmount] = useState('')
  const [weekly, setWeekly] = useState('')
  const [name, setName] = useState(goal.name)
  const [target, setTarget] = useState(String(goal.target))
  const [deadline, setDeadline] = useState(goal.deadline ?? '')

  /**
   * Opens or shuts the Edit panel, and seeds its three fields from the goal every time it opens.
   *
   * <p>They are this card's own state and the card is keyed by the goal's id, so left alone they
   * outlive everything: a name somebody typed and then cancelled would still be sitting in the
   * field the next time the panel opened, and `change` — which sends whatever differs from the
   * goal — would send that abandoned rename along with the one thing they did mean to change. The
   * same holds after a save the backend normalised: typing `1500,00` leaves `1500,00` here and
   * `1500` on the goal, and every later edit would re-send a target nobody touched. The panel shows
   * the goal as it is now, every time it is opened, which is also what makes Cancel cancel.
   */
  function showEdit(showing: boolean) {
    if (showing) {
      setName(goal.name)
      setTarget(String(goal.target))
      setDeadline(goal.deadline ?? '')
    }
    setOpen(showing ? 'edit' : null)
  }

  /**
   * Every control on the card ends the same way: the refusal shows here, or the plan is read back.
   *
   * <p>The read is waited for before the controls come back, the way `reorder` and `accept` wait
   * for theirs. Dropping that promise would let the buttons re-enable over the figures from before
   * the action — an abandon whose card still shows what it held, under a live "Yes, abandon it" —
   * for as long as the read takes.
   */
  function attempt(what: Promise<unknown>, afterwards?: () => void) {
    setBusy(true)
    setRefusal(null)
    what
      .then(() => {
        afterwards?.()
        return onChanged()
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setBusy(false))
  }

  function move(direction: AllocationDirection) {
    attempt(moveAllocation(savingsAccountId, goal.id, amount, direction), () => setAmount(''))
  }

  function pin(event: FormEvent) {
    event.preventDefault()
    attempt(pinWeeklyAmount(savingsAccountId, goal.id, weekly), () => setWeekly(''))
  }

  /**
   * Sends only what was actually changed.
   *
   * <p>A field left alone is left out, and an emptied deadline is sent as the empty string, which is
   * how the backend tells "nobody touched the day" from "there is no longer a day". A PATCH that
   * sent all three every time could not say the difference.
   */
  function change(event: FormEvent) {
    event.preventDefault()
    const changes: { name?: string; target?: string; deadline?: string } = {}
    if (name !== goal.name) {
      changes.name = name
    }
    if (target !== String(goal.target)) {
      changes.target = target
    }
    if (deadline !== (goal.deadline ?? '')) {
      changes.deadline = deadline
    }
    if (Object.keys(changes).length === 0) {
      setOpen(null)
      return
    }
    attempt(changeGoal(savingsAccountId, goal.id, changes), () => setOpen(null))
  }

  const howFarAlong =
    goal.target <= 0 ? 0 : Math.min(100, Math.max(0, (goal.allocation / goal.target) * 100))

  return (
    <li className={`goal goal--${STATUS_TONES[goal.status]}`} style={rowDelay(place)}>
      <div className="goal__top">
        {/* The order is changed where the order is shown. Two buttons rather than a drag: a rank is
            a whole number and moving one goal one place is the whole gesture, and it is the same
            gesture from a keyboard. */}
        <div className="goal__rank">
          <button
            type="button"
            className="goal__nudge"
            onClick={() => onReorder(-1)}
            disabled={first || reordering}
            aria-label={`Move “${goal.name}” up the order`}
          >
            <ArrowUpIcon />
          </button>
          <span className="goal__place">{goal.rank ?? place + 1}</span>
          <button
            type="button"
            className="goal__nudge"
            onClick={() => onReorder(1)}
            disabled={last || reordering}
            aria-label={`Move “${goal.name}” down the order`}
          >
            <ArrowDownIcon />
          </button>
        </div>

        <div className="goal__what">
          <h3 className="goal__name">{goal.name}</h3>
          <p className="goal__meta">
            {euros.format(goal.allocation)} of {euros.format(goal.target)}
            {goal.deadline !== null && <> · wanted by {asADay(goal.deadline)}</>}
          </p>
        </div>

        <span className={`goal__status goal__status--${STATUS_TONES[goal.status]}`}>
          <StatusMark status={goal.status} />
          {STATUS_WORDS[goal.status]}
        </span>
      </div>

      <div className="progress" aria-hidden="true">
        <span className="progress__fill" style={{ width: `${howFarAlong}%` }} />
      </div>

      <dl className="split goal__facts">
        <div>
          <dt>Each week</dt>
          <dd>
            {whatThePlanGives(goal)}
            {/* A chosen figure and a computed one look the same on a screen, and somebody comparing
                two goals has to know which of them this application decided.

                The chip carries the figure in the one case where there is no figure beside it to
                qualify: a goal can be pinned on an account with no declared capacity, and a bare
                "Pinned" against "Not set yet" would read as the pin being the thing not set. The
                pin was set — it is the plan that has nothing to share out yet. */}
            {goal.pinnedWeeklyAmount !== null && (
              <span className="goal__pinned">
                <PinIcon />
                {goal.weeklyAmount === null
                  ? `Pinned ${euros.format(goal.pinnedWeeklyAmount)}`
                  : 'Pinned'}
              </span>
            )}
          </dd>
        </div>
        <div>
          <dt>Gets there</dt>
          <dd>{whenItGetsThere(goal)}</dd>
        </div>
        <div>
          <dt>Still needed</dt>
          <dd>{euros.format(goal.stillNeeded)}</dd>
        </div>
      </dl>

      <div className="goal__actions">
        <Button
          small
          tone={open === 'money' ? 'primary' : 'ghost'}
          onClick={() => setOpen(open === 'money' ? null : 'money')}
        >
          Money
        </Button>
        <Button
          small
          tone={open === 'weekly' ? 'primary' : 'ghost'}
          onClick={() => setOpen(open === 'weekly' ? null : 'weekly')}
        >
          Weekly amount
        </Button>
        <Button
          small
          tone={open === 'edit' ? 'primary' : 'ghost'}
          onClick={() => showEdit(open !== 'edit')}
        >
          Edit
        </Button>
        {confirming ? (
          <>
            <Button small busy={busy} disabled={busy} onClick={() => attempt(abandonGoal(savingsAccountId, goal.id))}>
              Yes, abandon it
            </Button>
            <Button small tone="ghost" disabled={busy} onClick={() => setConfirming(false)}>
              Keep it
            </Button>
          </>
        ) : (
          <Button small tone="ghost" onClick={() => setConfirming(true)}>
            Abandon
          </Button>
        )}
      </div>

      {confirming && (
        <p className="goals__lead">
          Abandoning closes the goal and returns the {euros.format(goal.allocation)} it holds to
          money no goal has claimed.
        </p>
      )}

      {open === 'money' && (
        <form className="goal__form" onSubmit={(event) => event.preventDefault()}>
          <EuroAmount
            id={`goal-${goal.id}-amount`}
            label={`Amount for “${goal.name}”`}
            value={amount}
            onType={setAmount}
          />
          {/* Two buttons and one amount, and the direction travels as a word. A signed figure would
              be one typo away from meaning the opposite of what somebody meant. */}
          <div className="goal__pair">
            <Button small busy={busy} disabled={busy} onClick={() => move('INTO_THE_GOAL')}>
              Put towards it
            </Button>
            <Button
              small
              tone="ghost"
              busy={busy}
              disabled={busy}
              onClick={() => move('OUT_OF_THE_GOAL')}
            >
              Free from it
            </Button>
          </div>
        </form>
      )}

      {open === 'weekly' && (
        <form className="goal__form" onSubmit={pin}>
          <EuroAmount
            id={`goal-${goal.id}-weekly`}
            label={`Weekly amount for “${goal.name}”`}
            value={weekly}
            onType={setWeekly}
          />
          <div className="goal__pair">
            <Button small type="submit" busy={busy} disabled={busy}>
              Pin it
            </Button>
            <Button
              small
              tone="ghost"
              type="button"
              busy={busy}
              disabled={busy || goal.pinnedWeeklyAmount === null}
              onClick={() => attempt(unpinWeeklyAmount(savingsAccountId, goal.id))}
            >
              Unpin
            </Button>
          </div>
          <p className="goals__lead">
            A pinned amount is taken before anything else is shared out, so the goals under this one
            get what is left. More than the weekly amount is allowed, and its consequence is
            visible below.
          </p>
        </form>
      )}

      {open === 'edit' && (
        <form className="goal__form" onSubmit={change}>
          <div className="field">
            <label htmlFor={`goal-${goal.id}-name`}>Name</label>
            <input
              id={`goal-${goal.id}-name`}
              className="text-input"
              value={name}
              autoComplete="off"
              onChange={(event) => setName(event.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor={`goal-${goal.id}-target`}>Target</label>
            <input
              id={`goal-${goal.id}-target`}
              className="text-input"
              inputMode="decimal"
              value={target}
              autoComplete="off"
              onChange={(event) => setTarget(event.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor={`goal-${goal.id}-deadline`}>Wanted by</label>
            <input
              id={`goal-${goal.id}-deadline`}
              className="text-input"
              type="date"
              value={deadline}
              onChange={(event) => setDeadline(event.target.value)}
            />
          </div>
          <div className="goal__pair">
            <Button small type="submit" busy={busy} disabled={busy}>
              Save
            </Button>
            {/* Cancel puts the panel away and leaves the goal as it was — and because the fields
                are seeded from the goal again the next time it opens, what was typed here goes
                with it rather than waiting to be sent alongside somebody's next change. */}
            <Button small tone="ghost" type="button" onClick={() => showEdit(false)}>
              Cancel
            </Button>
          </div>
          <p className="goals__lead">
            Clearing the day removes the deadline, and the goal has nothing left to be late for.
          </p>
        </form>
      )}

      {refusal !== null && <Refusal reason={refusal} />}
    </li>
  )
}

/** Opens a goal. It joins the order last, because nothing else would be a decision this form could
 *  make on the customer's behalf. */
function NewGoal({
  savingsAccountId,
  onAdded,
}: {
  savingsAccountId: number
  onAdded: () => void
}) {
  const [name, setName] = useState('')
  const [target, setTarget] = useState('')
  const [deadline, setDeadline] = useState('')
  const [adding, setAdding] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  function add(event: FormEvent) {
    event.preventDefault()
    setAdding(true)
    setRefusal(null)
    addGoal(savingsAccountId, name, target, deadline === '' ? null : deadline)
      .then(() => {
        setName('')
        setTarget('')
        setDeadline('')
        onAdded()
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setAdding(false))
  }

  return (
    <article className="card reveal">
      <h2 className="card__title">Save for something else</h2>
      <form onSubmit={add}>
        <div className="field">
          <label htmlFor="newGoalName">What it is for</label>
          <input
            id="newGoalName"
            className="text-input"
            placeholder="Holiday"
            autoComplete="off"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </div>
        <EuroAmount id="newGoalTarget" label="Target" value={target} onType={setTarget} />
        <div className="field">
          <label htmlFor="newGoalDeadline">Wanted by (optional)</label>
          <input
            id="newGoalDeadline"
            className="text-input"
            type="date"
            value={deadline}
            onChange={(event) => setDeadline(event.target.value)}
          />
        </div>
        <Button type="submit" block busy={adding} disabled={adding}>
          {adding ? 'Opening…' : 'Start saving for it'}
        </Button>
        <p className="goals__lead">
          A new goal joins the order last and holds nothing until money is put towards it.
        </p>
        {refusal !== null && <Refusal reason={refusal} />}
      </form>
    </article>
  )
}

/* --------------------------------------------------------- automatic saving
 *
 * The screen a customer manages their automation from, reached from the savings account page: the
 * rules standing against the account, what each one will do next, what each one has done, the money
 * that arrives for them to move, and the form that leaves a new one standing.
 *
 * <p>Nothing on it is computed here. What a rule would move, the day it next fires, what a year of
 * them comes to, whether a figure is a promise or an illustration and every refusal are the
 * backend's answers, read back after each change. The one comparison this screen makes for itself is
 * against the weekly minimum, and both figures in it are the backend's.
 *
 * <p>The form asks the dry run as the amount is typed rather than hiding it behind a button. A
 * customer filling in "everything above 800, on payday" wants to know what that means for their
 * account before they commit to it, and the backend answers that question without writing anything:
 * no rule, no occurrence, no deposit and no point comes out of a dry run.
 */

/**
 * What this screen reads about one account's automation, held as one thing.
 *
 * <p>Three reads rather than one state each, for the reason the goals screen gives about its own
 * three: they answer one question between them and they move together. Leaving a rule standing adds
 * to the list *and* changes what the year ahead holds; pausing one takes its days out of that year;
 * ending one moves it from the first list to the last. A screen holding one of the three from before
 * a change would be showing two answers to one question.
 */
type TheAutomation = {
  rules: SavingRule[]
  ended: SavingRule[]
  coming: SavingRulePreview
}

/**
 * The three things the account itself has to say to the rules standing on it: the goals a split may
 * name, the goals it has been given up on that a split already names, and what a week asks for.
 *
 * <p>Read separately from the rules and failing separately, because none of them is about
 * automation: a goals module that cannot answer is no reason to stop somebody pausing a rule, and
 * the weekly minimum is the account's own figure. All of them are the backend's — this page has
 * never known that a week costs fifty euros.
 *
 * <p>`goalsGivenUpOn` is here for naming and for nothing else. A rule keeps the line for a goal that
 * has been given up on, so this screen has shares to show against goals the ordinary read does not
 * mention; what those shares are is read off the rule, and this only says what to call them. Where
 * it cannot, they are shown under an identifier, which is the fallback a split has always had.
 */
type WhatTheAccountSays = {
  goals: SavingsGoal[]
  goalsGivenUpOn: SavingsGoal[]
  weeklyMinimum: number
}

/** The seven days, in the one word each is shown as. A record rather than a function, so that a day
 *  the backend can send and this page has no word for does not compile. */
const DAY_NAMES: Record<DayOfWeek, string> = {
  MONDAY: 'Monday',
  TUESDAY: 'Tuesday',
  WEDNESDAY: 'Wednesday',
  THURSDAY: 'Thursday',
  FRIDAY: 'Friday',
  SATURDAY: 'Saturday',
  SUNDAY: 'Sunday',
}

/** The order they are offered in, which is the order of the week rather than the order of an enum. */
const DAYS_OF_THE_WEEK: DayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
]

/** Where a rule stands, in the one word this page shows it as. */
const RULE_STATE_WORDS: Record<RuleState, string> = {
  LIVE: 'Standing',
  PAUSED: 'Paused',
  ENDED: 'Ended',
}

/**
 * The class that dresses each state, so that a paused rule is told apart from a standing one by
 * something other than the absence of a date.
 *
 * <p>A paused rule is the one this matters for. Pausing takes its next day away, and a card that
 * said nothing about the state would leave "paused" and "waiting for an income to be declared"
 * looking identical — two quite different situations with the same blank where the day goes.
 */
const RULE_STATE_TONES: Record<RuleState, string> = {
  LIVE: 'rule-card__state--live',
  PAUSED: 'rule-card__state--paused',
  ENDED: 'rule-card__state--ended',
}

/** What became of one day a rule fell due on, in the one word the history shows it as. */
const OUTCOME_WORDS: Record<OccurrenceOutcome, string> = {
  MOVED: 'Moved',
  NOTHING_TO_MOVE: 'Nothing to move',
  NOT_ENOUGH_MONEY: 'Not enough money',
  THE_ACCOUNT_IS_CLOSED: 'Account closed',
}

/** And the class that colours it: money moved, arithmetic said nothing, or something went wrong. */
const OUTCOME_TONES: Record<OccurrenceOutcome, string> = {
  MOVED: 'occ--moved',
  NOTHING_TO_MOVE: 'occ--nothing',
  NOT_ENOUGH_MONEY: 'occ--short',
  // The same tone as a shortfall rather than one of its own: both are days the customer was
  // relying on that did not happen, and the word beside the mark is what tells them apart.
  THE_ACCOUNT_IS_CLOSED: 'occ--short',
}

/**
 * What became of one date a bill fell due on, in the one word its history shows it as.
 *
 * <p>Two rather than the three a saving rule has, and the missing one is the point: a sweep can
 * honestly move nothing because its figure is derived from the balance, while a bill is a number
 * somebody typed and is either taken or not. There is no partial payment to name.
 */
const BILL_OUTCOME_WORDS: Record<BillOutcome, string> = {
  PAID: 'Taken',
  UNPAID: 'Not paid',
}

/** And the class that colours it, reusing the rule history's palette so one list teaches the other. */
const BILL_OUTCOME_TONES: Record<BillOutcome, string> = {
  PAID: 'occ--moved',
  UNPAID: 'occ--short',
}

/**
 * A day of the month written the way somebody says it. The backend sends 31 for a rule set for the
 * 31st and clamps it to the last day of a short month itself, so this is a number being read out and
 * not a date being worked out.
 */
function dayInTheMonth(day: number): string {
  const teen = day % 100
  if (teen >= 11 && teen <= 13) {
    return `${day}th`
  }
  switch (day % 10) {
    case 1:
      return `${day}st`
    case 2:
      return `${day}nd`
    case 3:
      return `${day}rd`
    default:
      return `${day}th`
  }
}

/**
 * When a rule moves, in words, out of its trigger and the day that trigger needs.
 *
 * <p>A `switch` with no `default` and a declared return type, like the notification handlers above
 * and for the same reason: the day a fourth trigger is added this function stops compiling and
 * somebody has to decide what it is called on a card.
 *
 * <p>A payday rule with no day is its own sentence rather than a blank. Its day is read from the
 * income its holder declared, so "nobody has said when you are paid" is the whole explanation for
 * why nothing is scheduled — and it is the thing the card above it can do something about.
 */
function whenItMoves(rule: SavingRule): string {
  switch (rule.trigger) {
    case 'WEEKLY':
      return rule.dayOfWeek === null ? 'Every week' : `Every ${DAY_NAMES[rule.dayOfWeek]}`
    case 'MONTHLY':
      return rule.dayOfMonth === null
        ? 'Every month'
        : `Every month on the ${dayInTheMonth(rule.dayOfMonth)}`
    case 'ON_PAYDAY':
      return rule.dayOfMonth === null
        ? 'On payday — nobody has said when you are paid'
        : `On payday, the ${dayInTheMonth(rule.dayOfMonth)}`
  }
}

/**
 * How much a rule moves, in words: the figure it will move, or the line it sweeps down to.
 *
 * <p>Two sentences rather than one figure under a label, because they say opposite things — what
 * leaves, as against what stays behind — and a card that printed one of them under the other's
 * wording would be telling somebody their account will be emptied to fifty euros when it will be
 * emptied *of* fifty.
 */
function whatItMoves(rule: SavingRule): string {
  switch (rule.howMuchMoves) {
    case 'A_FIXED_AMOUNT':
      return rule.amount === null ? 'A fixed amount' : `Moves ${euros.format(rule.amount)}`
    case 'EVERYTHING_ABOVE':
      return rule.floor === null
        ? 'Sweeps everything above a floor'
        : `Sweeps everything above ${euros.format(rule.floor)}`
  }
}

/**
 * What one day in a rule's history came to, as a sentence.
 *
 * <p>Four outcomes and four different sentences, none of them a template with a word swapped: an
 * occurrence that moved money says what moved; one that found nothing above its floor says that the
 * arithmetic came to nothing, which is not a failure; one the account could not cover says what it
 * was short by, which is the figure the customer can act on; and one that fell due on a savings
 * account since closed says so, because there is no figure to act on and the thing to do is to the
 * rule rather than to a balance.
 *
 * <p>A figure the outcome says should be there and is not draws the plain word instead of a sentence
 * with a gap in it. The backend keeps that total, so it is unreachable in practice.
 */
function WhatBecameOfIt({ occurrence }: { occurrence: RuleOccurrence }): ReactElement {
  switch (occurrence.outcome) {
    case 'MOVED':
      return <>{euros.format(occurrence.amount)} moved into savings</>
    case 'NOTHING_TO_MOVE':
      return <>Nothing above the floor that morning, so nothing moved</>
    case 'NOT_ENOUGH_MONEY':
      return occurrence.shortfall === null ? (
        <>There was not enough in the current account, so nothing moved</>
      ) : (
        <>
          {euros.format(occurrence.shortfall)} short, so nothing moved — a fixed amount moves all of
          itself or none of it
        </>
      )
    case 'THE_ACCOUNT_IS_CLOSED':
      return (
        <>
          This savings account had been closed, so nothing moved — change the rule or end it
        </>
      )
  }
}

/** The mark beside that sentence, so an outcome is legible before it is read. */
function OutcomeMark({ outcome }: { outcome: OccurrenceOutcome }): ReactElement {
  switch (outcome) {
    case 'MOVED':
      return <ArrowUpIcon />
    case 'NOTHING_TO_MOVE':
      return <OpenEndedIcon />
    case 'NOT_ENOUGH_MONEY':
      return <WarningIcon />
    case 'THE_ACCOUNT_IS_CLOSED':
      return <WarningIcon />
  }
}

/**
 * What a rule will do next: the day, and the figure — or the reason there is no day.
 *
 * <p>Four sentences, and which of them is drawn is decided by the state and by what the backend
 * sent. A paused rule says it is paused, because taking the day away is not the same statement as
 * saying why it is missing; an ended rule says when it was ended; a rule with no day at all says the
 * one thing that explains it. Only the fourth is the ordinary case.
 *
 * <p>The figure beside the day is the backend's, and a sweep's is marked as the illustration it is:
 * it is worked out from the balance the account has now, and nobody knows what will be in it on the
 * morning the rule fires.
 */
function NextFiring({ rule }: { rule: SavingRule }): ReactElement {
  if (rule.state === 'PAUSED') {
    return (
      <p className="rule-card__next rule-card__next--paused">
        Paused{rule.pausedAt === null ? '' : ` on ${dateAndTime.format(new Date(rule.pausedAt))}`} —
        nothing moves until you resume it, and the mornings it passes are never made up.
      </p>
    )
  }
  if (rule.state === 'ENDED') {
    return (
      <p className="rule-card__next rule-card__next--ended">
        Ended{rule.endedAt === null ? '' : ` on ${dateAndTime.format(new Date(rule.endedAt))}`}. What
        it did is below; it will not fire again.
      </p>
    )
  }
  if (rule.nextFiresOn === null || rule.nextMoves === null) {
    return (
      <p className="rule-card__next rule-card__next--waiting">
        {rule.trigger === 'ON_PAYDAY'
          ? 'Nothing is scheduled: declare what you are paid, below, and this rule follows it.'
          : 'Nothing is scheduled for this rule.'}
      </p>
    )
  }
  return (
    <p className="rule-card__next">
      <strong>Next on {asADay(rule.nextFiresOn)}</strong>
      {' — '}
      {euros.format(rule.nextMoves.amount)}
      {rule.nextMoves.anIllustrationRatherThanAPromise &&
        ' at today’s balance, which is an illustration rather than a promise'}
    </p>
  )
}

/**
 * How a rule spreads what it moves, if it does: the goals, in the order the customer wrote them, and
 * the whole percentage each was given.
 *
 * <p>A goal this page cannot put a name to is still shown, by its identifier, rather than dropped: a
 * share going somewhere is the fact, and a failed read of the goals is no reason to draw a split
 * that adds up to less than a hundred. A goal given up on is one of those and has a name waiting
 * for it, because a rule keeps that line on purpose.
 */
function TheSplit({
  split,
  goals,
  goalsGivenUpOn,
}: {
  split: SavingRuleShare[]
  goals: SavingsGoal[]
  goalsGivenUpOn: SavingsGoal[]
}) {
  if (split.length === 0) {
    return null
  }
  return (
    <ul className="rule-card__split">
      {split.map((share) => (
        <li key={share.goalId}>
          <span className="rule-card__share">{share.share}%</span>
          {nameOfTheGoal(share.goalId, goals, goalsGivenUpOn)}
        </li>
      ))}
    </ul>
  )
}

/**
 * That a weekly rule at or above the weekly minimum keeps a streak alive by itself.
 *
 * <p>The most useful sentence this application can put in front of somebody setting up automation,
 * and it costs one comparison. Both figures in it are the backend's: what the rule would move is
 * whatever the backend read out of the box, and what a week asks for is the account's own
 * `weeklyMinimum` — this page has never known that it is fifty euros.
 *
 * <p>Only for a fixed amount, and deliberately not for a sweep. What a sweep moves is worked out
 * from a balance nobody can know in advance, so "this secures a week, for ever" would be a promise
 * about a figure that changes every Tuesday.
 */
function SecuresAWeekByItself({
  trigger,
  howMuchMoves,
  amount,
  weeklyMinimum,
}: {
  trigger: string
  howMuchMoves: string
  amount: number | null
  weeklyMinimum: number | null
}): ReactElement | null {
  if (trigger !== 'WEEKLY' || howMuchMoves !== 'A_FIXED_AMOUNT') {
    return null
  }
  if (amount === null || weeklyMinimum === null || amount < weeklyMinimum) {
    return null
  }
  return (
    <p className="rules__secures">
      <TickIcon />
      <span>
        {euros.format(amount)} every week is at or above the {euros.format(weeklyMinimum)} a week
        asks for, so this rule secures a week by itself — for ever, without you remembering anything.
      </span>
    </p>
  )
}

/**
 * The screen itself: what is standing, what is arriving, the form that leaves another rule standing,
 * the year those rules add up to, and what has been ended.
 *
 * <p>In that order because it is the order the questions are asked in. The rules first, because that
 * is what somebody came to look at; the income second, because a payday rule is meaningless without
 * one and a page that offered the trigger while hiding the thing it depends on would generate the
 * support question it exists to answer; the form third; and the year ahead last, because it is the
 * sum of everything above it.
 *
 * <p>The income is named here and changed elsewhere. It is a declaration about a current account,
 * it has a page of its own on that account, and what is left here is the link to it — one editable
 * copy, because two of them on two screens is how one declaration becomes two answers.
 */
function AutomaticSaving({
  savingsAccountId,
  currentAccounts,
  accountsError,
  onBack,
  onOpenCurrentAccount,
}: {
  savingsAccountId: number
  currentAccounts: CurrentAccount[]
  accountsError: string | null
  onBack: () => void
  onOpenCurrentAccount: (currentAccountId: number) => void
}) {
  const [automation, setAutomation] = useState<TheAutomation | null>(null)
  const [automationError, setAutomationError] = useState<string | null>(null)
  const [account, setAccount] = useState<WhatTheAccountSays | null>(null)
  const [accountError, setAccountError] = useState<string | null>(null)

  /**
   * Only the newest read paints. Two actions a moment apart — a rule paused while another is being
   * ended — each read the three things back, and answers arriving out of order would leave the
   * screen showing the account as it was in between.
   */
  const reads = useRef(0)
  const loadAutomation = useCallback(
    (signal?: AbortSignal) => {
      const mine = ++reads.current
      Promise.all([
        fetchSavingRules(savingsAccountId, signal),
        fetchEndedSavingRules(savingsAccountId, signal),
        fetchRulesPreview(savingsAccountId, signal),
      ])
        .then(([rules, ended, coming]) => {
          if (signal?.aborted !== true && mine === reads.current) {
            setAutomation({ rules, ended, coming })
            setAutomationError(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true && mine === reads.current) {
            setAutomationError(problem.message)
          }
        })
    },
    [savingsAccountId],
  )

  const loadAccount = useCallback(
    (signal?: AbortSignal) => {
      Promise.all([
        fetchSavingsAccount(savingsAccountId, signal),
        fetchAllocations(savingsAccountId, signal),
        fetchAbandonedGoals(savingsAccountId, signal),
      ])
        .then(([balances, plan, givenUpOn]) => {
          if (signal?.aborted !== true) {
            setAccount({
              goals: plan.goals,
              goalsGivenUpOn: givenUpOn,
              weeklyMinimum: balances.weeklyMinimum,
            })
            setAccountError(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true) {
            setAccountError(problem.message)
          }
        })
    },
    [savingsAccountId],
  )

  useEffect(() => {
    const request = new AbortController()
    loadAutomation(request.signal)
    return () => request.abort()
  }, [loadAutomation])

  useEffect(() => {
    const request = new AbortController()
    loadAccount(request.signal)
    return () => request.abort()
  }, [loadAccount])

  const goals = account?.goals ?? []
  const goalsGivenUpOn = account?.goalsGivenUpOn ?? []

  return (
    <section className="view">
      <button type="button" className="link link--back" onClick={onBack}>
        <BackIcon />
        Back to the account
      </button>

      {/* The customer-level read, which is where the everyday accounts on both forms come from.
          Without it the income card and the new-rule form have no account to draw from, and both
          would state that the customer holds none — a sentence this page wrote, and a false one. */}
      {accountsError !== null && <Refusal reason={accountsError} standing />}
      {accountError !== null && <Refusal reason={accountError} standing />}
      {automationError !== null && <Refusal reason={automationError} standing />}

      <div className="section-head">
        <h2>Rules standing here</h2>
      </div>

      {automation === null && automationError === null && (
        <div className="card">
          <Waiting label="Loading your automatic saving…" bars={['9rem', '100%', '60%']} />
        </div>
      )}

      {automation !== null &&
        (automation.rules.length === 0 ? (
          <article className="card reveal">
            <p className="nothing">
              No rules are standing against this account yet. The form below leaves one standing.
            </p>
          </article>
        ) : (
          <div className="rules">
            {automation.rules.map((rule, place) => (
              <RuleCard
                key={rule.id}
                rule={rule}
                place={place}
                savingsAccountId={savingsAccountId}
                currentAccounts={currentAccounts}
                goals={goals}
                goalsGivenUpOn={goalsGivenUpOn}
                weeklyMinimum={account?.weeklyMinimum ?? null}
                onChanged={loadAutomation}
              />
            ))}
          </div>
        ))}

      <div className="section-head">
        <h2>Money arriving</h2>
      </div>
      <WhereTheIncomeIsDeclared
        currentAccounts={currentAccounts}
        onOpenCurrentAccount={onOpenCurrentAccount}
      />

      <div className="section-head">
        <h2>Leave another rule standing</h2>
      </div>
      <NewRule
        savingsAccountId={savingsAccountId}
        currentAccounts={currentAccounts}
        goals={goals}
        goalsGivenUpOn={goalsGivenUpOn}
        weeklyMinimum={account?.weeklyMinimum ?? null}
        onLeftStanding={loadAutomation}
      />

      {automation !== null && <RulesToCome coming={automation.coming} />}

      {automation !== null && automation.ended.length > 0 && (
        <>
          <div className="section-head">
            <h2>Rules you have ended</h2>
          </div>
          <div className="rules">
            {automation.ended.map((rule, place) => (
              <RuleCard
                key={rule.id}
                rule={rule}
                place={place}
                savingsAccountId={savingsAccountId}
                currentAccounts={currentAccounts}
                goals={goals}
                goalsGivenUpOn={goalsGivenUpOn}
                weeklyMinimum={account?.weeklyMinimum ?? null}
                onChanged={loadAutomation}
              />
            ))}
          </div>
        </>
      )}
    </section>
  )
}

/**
 * One rule: what it is called, where the money comes from, when it moves, how much, what it will do
 * next, and everything that can be done to it.
 *
 * <p>Its history is read on demand rather than with the card, because a rule caught up over three
 * years is a thousand rows and a page that opened with all of them for every rule would be reading
 * an account's whole record to answer "what is standing here".
 *
 * <p>Every refusal stays inside this card, next to the button that caused it, and is the backend's
 * own sentence. Pausing something already paused is not one of them: the backend accepts it quietly,
 * because a button pressed twice is not a mistake worth a sentence.
 */
function RuleCard({
  rule,
  place,
  savingsAccountId,
  currentAccounts,
  goals,
  goalsGivenUpOn,
  weeklyMinimum,
  onChanged,
}: {
  rule: SavingRule
  place: number
  savingsAccountId: number
  currentAccounts: CurrentAccount[]
  goals: SavingsGoal[]
  goalsGivenUpOn: SavingsGoal[]
  weeklyMinimum: number | null
  onChanged: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)
  const [changing, setChanging] = useState(false)
  const [history, setHistory] = useState<RuleOccurrence[] | null>(null)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [showing, setShowing] = useState(false)

  const ibans = ibansOf(currentAccounts)

  /**
   * Does the thing, then reads the account's automation back rather than trusting the rule that came
   * back on its own. Pausing a rule changes what the year ahead holds and ending one moves it
   * between two lists, and neither of those is in the one rule the call answers with.
   */
  function does(what: () => Promise<SavingRule>) {
    setBusy(true)
    setRefusal(null)
    what()
      .then(() => onChanged())
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setBusy(false))
  }

  function readTheHistory() {
    setHistoryError(null)
    fetchRuleHistory(savingsAccountId, rule.id)
      .then(setHistory)
      .catch((problem: Error) => setHistoryError(problem.message))
  }

  return (
    <article className={`card reveal rule-card rule-card--${rule.state.toLowerCase()}`} style={rowDelay(place)}>
      <div className="rule-card__top">
        <h3 className="rule-card__name">{rule.name}</h3>
        <span className={`rule-card__state ${RULE_STATE_TONES[rule.state]}`}>
          {RULE_STATE_WORDS[rule.state]}
        </span>
      </div>

      <p className="rule-card__what">
        {whenItMoves(rule)} · {whatItMoves(rule)}
      </p>
      <p className="rule-card__from">
        From {ibans.get(rule.fromCurrentAccountId) ?? `current account ${rule.fromCurrentAccountId}`}
      </p>

      <NextFiring rule={rule} />
      <TheSplit split={rule.split} goals={goals} goalsGivenUpOn={goalsGivenUpOn} />
      {/* Only about a rule that is actually standing. A paused rule moves nothing until it is
          resumed and an ended one moves nothing ever, so "secures a week by itself — for ever" two
          lines under "nothing moves until you resume it" is the card contradicting itself about the
          one sentence on it that is worth reading. */}
      {rule.state === 'LIVE' && (
        <SecuresAWeekByItself
          trigger={rule.trigger}
          howMuchMoves={rule.howMuchMoves}
          amount={rule.amount}
          weeklyMinimum={weeklyMinimum}
        />
      )}

      <div className="rule-card__actions">
        {rule.state !== 'ENDED' && (
          <>
            {rule.state === 'PAUSED' ? (
              <Button
                small
                tone="ghost"
                busy={busy}
                disabled={busy}
                onClick={() => does(() => resumeSavingRule(savingsAccountId, rule.id))}
              >
                Resume
              </Button>
            ) : (
              <Button
                small
                tone="ghost"
                busy={busy}
                disabled={busy}
                onClick={() => does(() => pauseSavingRule(savingsAccountId, rule.id))}
              >
                Pause
              </Button>
            )}
            <Button
              small
              tone="ghost"
              disabled={busy}
              onClick={() => setChanging((open) => !open)}
              aria-expanded={changing}
            >
              {changing ? 'Leave it as it is' : 'Change'}
            </Button>
            <Button
              small
              tone="ghost"
              busy={busy}
              disabled={busy}
              onClick={() => does(() => endSavingRule(savingsAccountId, rule.id))}
            >
              End it
            </Button>
          </>
        )}
        <Button
          small
          tone="ghost"
          aria-expanded={showing}
          onClick={() => {
            const open = !showing
            setShowing(open)
            if (open) {
              readTheHistory()
            }
          }}
        >
          {showing ? 'Hide what it has done' : 'What it has done'}
        </Button>
      </div>

      {refusal !== null && <Refusal reason={refusal} />}

      {changing && (
        <ChangeTheRule
          rule={rule}
          savingsAccountId={savingsAccountId}
          goals={goals}
          goalsGivenUpOn={goalsGivenUpOn}
          weeklyMinimum={weeklyMinimum}
          onChanged={() => {
            setChanging(false)
            onChanged()
          }}
        />
      )}

      {showing && (
        <TheRuleHistory
          history={history}
          problem={historyError}
          goals={goals}
          goalsGivenUpOn={goalsGivenUpOn}
        />
      )}
    </article>
  )
}

/**
 * What one rule has actually done: every day it fell due on, what became of it, and how late it was.
 *
 * <p>Newest first, as the backend sends it. Nothing here sorts and nothing here counts: the lateness
 * is the backend's own subtraction between the morning an occurrence belonged to and the moment it
 * was actually made, which on a wound clock or after downtime is the whole point of the record.
 *
 * <p>A day that moved nothing is in the list beside the days that moved money, which is the half a
 * deposits ledger can never hold: an occurrence that found an empty account is as much a part of the
 * history as one that moved a hundred euros.
 */
function TheRuleHistory({
  history,
  problem,
  goals,
  goalsGivenUpOn,
}: {
  history: RuleOccurrence[] | null
  problem: string | null
  goals: SavingsGoal[]
  goalsGivenUpOn: SavingsGoal[]
}) {
  if (problem !== null) {
    return <Refusal reason={problem} />
  }
  if (history === null) {
    return <Waiting label="Loading what this rule has done…" bars={['100%', '80%']} />
  }
  if (history.length === 0) {
    return <p className="nothing">This rule has not fallen due yet.</p>
  }
  return (
    <ul className="timeline occ">
      {history.map((occurrence, place) => (
        <li key={occurrence.id} className={OUTCOME_TONES[occurrence.outcome]} style={rowDelay(place)}>
          <span className="tl__icon" aria-hidden="true">
            <OutcomeMark outcome={occurrence.outcome} />
          </span>
          <div className="tl__body">
            <p className="tl__title">
              {OUTCOME_WORDS[occurrence.outcome]} · due {asADay(occurrence.dueOn)}
            </p>
            <p className="tl__meta">
              <WhatBecameOfIt occurrence={occurrence} />
            </p>
            <span className="tl__tags">
              <span className="tl__tag">
                settled {dateAndTime.format(new Date(occurrence.settledAt))}
              </span>
              {occurrence.daysLate > 0 && (
                <span className="tl__tag tl__tag--late">
                  {occurrence.daysLate === 1 ? '1 day late' : `${occurrence.daysLate} days late`}
                </span>
              )}
              {occurrence.intoGoals.map((got) => (
                <span key={got.goalId} className="tl__tag">
                  {euros.format(got.amount)} to {nameOfTheGoal(got.goalId, goals, goalsGivenUpOn)}
                </span>
              ))}
              {occurrence.outcome === 'MOVED' && occurrence.leftUnallocated > 0 && (
                <span className="tl__tag">
                  {euros.format(occurrence.leftUnallocated)} left unallocated
                </span>
              )}
            </span>
          </div>
        </li>
      ))}
    </ul>
  )
}

/**
 * What a customer has said lands in this account every month, and the boxes to say it in.
 *
 * <p><strong>The one editable copy of the declaration</strong>, and it is on the current account's
 * own page because that is the account the money lands in. It used to be on a savings account's
 * automatic saving page, where it ended up because a payday rule was the feature that needed it —
 * a fact about a current account, filed under the thing that reads it rather than under the thing
 * it is about. Two editable copies of one declaration is how they drift apart, so the rules page
 * links here and holds none of its own.
 *
 * <p>No account picker, unlike the form this grew out of. The page belongs to one account, so which
 * account is not a question to ask: the boxes describe the account whose page this is, and what is
 * in them is the declaration the page has already read.
 *
 * <p>Every figure travels as the text that was typed. What a day of the month is and what an amount
 * of money is are the backend's rulings, in sentences written for the person who typed them; a form
 * that turned "5o" into a number before sending it would be answering in its own words a question
 * that has a sentence waiting for it.
 */
function WhatYouArePaid({
  currentAccountId,
  income,
  onDeclared,
}: {
  currentAccountId: number
  income: MonthlyIncome
  onDeclared: (declared: MonthlyIncome) => void
}) {
  const [refusal, setRefusal] = useState<string | null>(null)
  const [dayOfMonth, setDayOfMonth] = useState(
    income.dayOfMonth === null ? '' : String(income.dayOfMonth),
  )
  const [amount, setAmount] = useState(income.amount === null ? '' : income.amount.toFixed(2))
  const [saving, setSaving] = useState(false)

  // What is on file goes into the boxes, so that changing an income is an edit rather than a
  // retyping — and so that the figures on the screen are the ones the backend is holding. It runs
  // again every time the page reads a declaration back, which is what empties the boxes the moment
  // the declaration behind them is withdrawn.
  useEffect(() => {
    setDayOfMonth(income.dayOfMonth === null ? '' : String(income.dayOfMonth))
    setAmount(income.amount === null ? '' : income.amount.toFixed(2))
  }, [income])

  function declare(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setRefusal(null)
    declareMonthlyIncome(currentAccountId, dayOfMonth, amount)
      .then(onDeclared)
      .catch((problemDeclaring: Error) => setRefusal(problemDeclaring.message))
      .finally(() => setSaving(false))
  }

  function withdraw() {
    setSaving(true)
    setRefusal(null)
    withdrawMonthlyIncome(currentAccountId)
      .then(onDeclared)
      .catch((problemWithdrawing: Error) => setRefusal(problemWithdrawing.message))
      .finally(() => setSaving(false))
  }

  return (
    <article className="card reveal">
      <h3 className="card__title">What you are paid</h3>

      {income.declared ? (
        <dl className="split">
          <div>
            <dt>Lands</dt>
            <dd>
              {income.amount === null ? '—' : euros.format(income.amount)}
              {income.dayOfMonth === null ? '' : ` on the ${dayInTheMonth(income.dayOfMonth)}`}
            </dd>
          </div>
          <div>
            <dt>Next payday</dt>
            <dd>{income.nextPayday === null ? '—' : asADay(income.nextPayday)}</dd>
          </div>
        </dl>
      ) : (
        <p className="nothing">
          You have not said what you are paid, so nothing is credited to this account and a payday
          rule has no day to move on.
        </p>
      )}

      <form onSubmit={declare}>
        <div className="field">
          <label htmlFor="incomeDay">Day of the month you are paid</label>
          <input
            id="incomeDay"
            className="text-input"
            inputMode="numeric"
            placeholder="25"
            autoComplete="off"
            value={dayOfMonth}
            onChange={(event) => setDayOfMonth(event.target.value)}
          />
        </div>
        <EuroAmount id="incomeAmount" label="What lands" value={amount} onType={setAmount} />
        <div className="rule-card__actions">
          <Button type="submit" busy={saving} disabled={saving}>
            {income.declared ? 'Change it' : 'Declare it'}
          </Button>
          {income.declared && (
            <Button tone="ghost" disabled={saving} onClick={withdraw}>
              Withdraw the declaration
            </Button>
          )}
        </div>
        {refusal !== null && <Refusal reason={refusal} />}
      </form>
      <p className="rule">
        The nightly job credits this to the account on that day, and catches up every payday that
        fell while nobody was looking — which is what gives a rule that reads a balance something to
        read.
      </p>
    </article>
  )
}

/**
 * What leaves this current account every month, and what used to.
 *
 * <p>The outbound half of the page, drawn directly under what arrives, because the order on screen
 * is the order the money actually moves: the salary lands, the bills are presented, and what is left
 * is what there is to save out of. A customer reading down the page is reading their month.
 *
 * <p>Nothing here is computed. The list comes down with the account, the total is a sum of figures
 * the backend sent, and every refusal is the backend's own sentence put on screen unchanged — which
 * is the only way somebody who typed a comma finds out about the comma.
 *
 * <p><strong>Ended bills are a section rather than a row style</strong>, and the section is absent
 * rather than empty when nothing has been ended. An empty heading is a thing people learn to ignore,
 * and this one has something to say exactly when it appears.
 *
 * <p>The rows in it are the same {@link BillCard} the standing ones are, the way the ended saving
 * rules are the same {@code RuleCard}. A bill that has been ended still has a record, and a card
 * that could not open it would be the section promising what you used to pay while giving no way to
 * read it.
 */
function MoneyGoingOut({
  currentAccountId,
  bills,
  endedBills,
  endedBillsError,
  categories,
  billCategories,
  onChanged,
  onFiled,
}: {
  currentAccountId: number
  bills: RecurringBill[]
  endedBills: RecurringBill[] | null
  endedBillsError: string | null
  categories: SpendingCategory[] | null
  billCategories: BillInACategory[] | null
  onChanged: () => void
  onFiled: () => void
}) {
  const everyMonth = bills.reduce((sum, bill) => sum + bill.amount, 0)

  // A bill missing from the labels is a bill in no category: the backend keeps a row only for the
  // bills that are filed somewhere, and the absence of one is the answer rather than a gap in it.
  function theCategoryOf(billId: number): BillInACategory | null {
    return billCategories?.find((filed) => filed.billId === billId) ?? null
  }

  return (
    <>
      {bills.length === 0 ? (
        <article className="card reveal">
          <p className="nothing">
            You have not said what leaves this account. Declare your rent, your energy and your
            phone bill, and the balance you are deciding to save from is the balance you really
            have.
          </p>
        </article>
      ) : (
        <>
          <article className="card reveal">
            <dl className="split">
              <div>
                <dt>Going out every month</dt>
                <dd>{euros.format(everyMonth)}</dd>
              </div>
              <div>
                <dt>Bills standing</dt>
                <dd>{bills.length}</dd>
              </div>
            </dl>
          </article>
          {bills.map((bill, place) => (
            <BillCard
              key={bill.billId}
              bill={bill}
              place={place}
              currentAccountId={currentAccountId}
              categories={categories}
              filed={theCategoryOf(bill.billId)}
              onChanged={onChanged}
              onFiled={onFiled}
            />
          ))}
        </>
      )}

      <DeclareABill currentAccountId={currentAccountId} onDeclared={onChanged} />

      {endedBillsError !== null && <Refusal reason={endedBillsError} standing />}

      {endedBills !== null && endedBills.length > 0 && (
        <>
          <div className="section-head">
            <h2>Bills you have ended</h2>
          </div>
          {endedBills.map((bill, place) => (
            <BillCard
              key={bill.billId}
              bill={bill}
              place={place}
              currentAccountId={currentAccountId}
              categories={categories}
              filed={theCategoryOf(bill.billId)}
              onChanged={onChanged}
              onFiled={onFiled}
            />
          ))}
        </>
      )}
    </>
  )
}

/**
 * What this month has to cover: what is in the account, what is due to arrive, what is due to leave
 * and what that leaves.
 *
 * <p><strong>The number this whole screen exists to show.</strong> A balance on its own says "you
 * have 2480 euros"; these four figures say "you have 2480 euros and 1165 of it is spoken for", which
 * is the sentence somebody needs *before* they decide how much to sweep into savings rather than
 * after. It sits directly under the balance for that reason: the balance is the figure people act
 * on, and this is what it actually means.
 *
 * <p>Nothing here is worked out. Every figure, including the subtraction at the bottom, is the
 * backend's — a page doing its own arithmetic would be a second place this application decides what
 * a month costs, and the two would disagree the first time either changed. The window is the
 * backend's two days as well, because this application's clock can be wound a year forward and a
 * month counted from `new Date()` would be a month nobody is in.
 *
 * <p>What is already owed is inside "due out", because an arrear is a claim on this balance exactly
 * as a standing bill is and the nightly run offers the money to it first. It is named underneath so
 * that a total larger than the bills on the page is explained rather than mysterious.
 *
 * <p><strong>The window is named as what it is, which is not quite a month either end.</strong>
 * Every figure is counted from each bill's and the salary's own cursor rather than from the day the
 * window opens, so a date the nightly run has not reached yet — every date of a clock wound forward
 * without the jobs being run — is inside these totals although it sits before `from`. That is what
 * keeps the figures honest: it is money that has not moved. Saying only "between these two days"
 * would leave somebody reading two salaries under a one-month label with nothing to explain it, so
 * the line says both halves.
 *
 * <p>A shortfall is said out loud rather than hidden. `leavesYou` is allowed to be negative — a
 * balance never goes below nought, but a month can plainly cost more than there is — and that is
 * exactly the month worth being warned about.
 */
function TheMonthAhead({ month }: { month: MonthAhead }) {
  const short = month.leavesYou < 0

  return (
    <>
      <div className="section-head">
        <h2>The month ahead</h2>
      </div>
      <article className="card reveal">
        <p className={short ? 'amount amount--xl amount--short' : 'amount amount--xl'}>
          {euros.format(month.leavesYou)}
        </p>
        <p className="month-ahead__when">
          left once everything still to move is covered: {asADay(month.from)} to{' '}
          {asADay(month.until)}, and whatever tonight's run still owes from before that
        </p>
        <dl className="split">
          <div>
            <dt>In the account</dt>
            <dd>{euros.format(month.balance)}</dd>
          </div>
          <div>
            <dt>Due in</dt>
            <dd>+ {euros.format(month.incomeDue)}</dd>
          </div>
          <div>
            <dt>Due out</dt>
            <dd>− {euros.format(month.billsDue)}</dd>
          </div>
        </dl>
        {month.arrearsOutstanding > 0 && (
          <p className="rule">
            {euros.format(month.arrearsOutstanding)} of what is due out is already owed from a date
            that could not be paid. Every night the oldest of those is paid first, before anything
            newly due.
          </p>
        )}
        {short ? (
          <p className="notice notice--warning">
            This month asks for more than the account will hold. Something goes unpaid unless money
            comes back from savings — so this is not the month to sweep the balance into a pot.
          </p>
        ) : (
          <p className="rule">
            What arrives and what goes out over the next month, against what is in the account now.
            This is the room you have to save into: sweep more than it and a bill goes unpaid, which
            is a debt rather than a discount.
          </p>
        )}
      </article>
    </>
  )
}

/**
 * What the account still owes: every date a bill fell due on that could not be paid, oldest first,
 * with how late each one is and what they come to altogether.
 *
 * <p><strong>The section this whole feature exists to put in front of a customer.</strong> A bill
 * that could not be paid is not waived and not forgotten. It stays owed, every later night settles
 * the oldest of these before anything newly due, and they accumulate until the customer does
 * something about it: miss the rent in March and April presents two rents. The balance has less and
 * less room until they act, and acting is the lesson.
 *
 * <p><strong>Absent rather than empty when nothing is owed</strong>, which is why the caller decides
 * whether to draw this at all. A panel that reads nought every day is a panel people learn to
 * ignore, and this is the one panel on the page that must never be ignored.
 *
 * <p>Nothing here is worked out. The order is the backend's, the lateness is the backend's own
 * subtraction against today rather than against the night the date was refused, and the total is a
 * sum of the figures it sent — the same sum the bills above are totalled with.
 *
 * <p>The rows wear the bill history's own colours, because they are the same fact seen from the
 * other side: a row here is an unpaid date in some bill's history, still open.
 */
function StillOwed({ arrears }: { arrears: Arrear[] }) {
  const owedAltogether = arrears.reduce((sum, owed) => sum + owed.amount, 0)

  return (
    <>
      <div className="section-head">
        <h2>Still owed</h2>
      </div>
      <article className="card reveal">
        <dl className="split">
          <div>
            <dt>Still owed</dt>
            <dd>{euros.format(owedAltogether)}</dd>
          </div>
          <div>
            <dt>Dates owed</dt>
            <dd>{arrears.length}</dd>
          </div>
        </dl>
        <p className="rule-card__next">
          Nothing has been added to these — no interest, no fee, no charge of any kind. Each night
          the oldest is paid first, before anything newly due, out of whatever is in the account.
          Move money back from savings to clear them; what a goal has claimed will not come back.
        </p>
      </article>
      <ul className="timeline occ">
        {arrears.map((owed, place) => (
          <li key={owed.id} className="occ--short" style={rowDelay(place)}>
            <span className="tl__icon" aria-hidden="true">
              <WarningIcon />
            </span>
            <div className="tl__body">
              <p className="tl__title">
                {owed.billName} · due {asADay(owed.dueOn)}
              </p>
              <p className="tl__meta">
                {euros.format(owed.amount)} was due and there was not enough in the account, so
                nothing at all was taken. It is still owed.
              </p>
              <span className="tl__tags">
                <span className="tl__tag tl__tag--late">
                  {owed.daysLate === 1 ? '1 day late' : `${owed.daysLate} days late`}
                </span>
              </span>
            </div>
          </li>
        ))}
      </ul>
    </>
  )
}

/**
 * One bill on the account: what it is called, what it costs and the day it goes out on, with
 * whatever can still be done to it.
 *
 * <p>The card is the saving rules' card, reused rather than reinvented, because a customer who has
 * read one list of standing instructions should not have to learn a second layout to read the other.
 *
 * <p><strong>Ending is one-way and the button says so plainly.</strong> There is no confirmation
 * step, exactly as there is none on ending a saving rule: the bill is not deleted, it moves to the
 * list below, and the sentence on it says how to start paying it again.
 *
 * <p><strong>One card for a standing bill and an ended one</strong>, exactly as {@link RuleCard} is
 * one card for a standing rule and an ended one. What ending takes away is the two things that
 * would change a bill that has stopped existing — there is nothing to put up and nothing left to
 * end — and what it deliberately does not take away is the record. The ended card's own sentence
 * promises that what you used to pay stays readable, and a promise with no way to read it is the
 * page telling the customer something untrue; this is also the second half of "I want to see the
 * bills I have ended, so that I can check what I used to pay". An ended bill's history is the only
 * place that answer lives, because a balance is a figure rather than a sum of records.
 */
function BillCard({
  bill,
  place,
  currentAccountId,
  categories,
  filed,
  onChanged,
  onFiled,
}: {
  bill: RecurringBill
  place: number
  currentAccountId: number
  categories: SpendingCategory[] | null
  filed: BillInACategory | null
  onChanged: () => void
  onFiled: () => void
}) {
  const ended = bill.state === 'ENDED'
  const [changing, setChanging] = useState(false)
  const [busy, setBusy] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)
  const [history, setHistory] = useState<BillOccurrence[] | null>(null)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [showing, setShowing] = useState(false)

  function end() {
    setBusy(true)
    setRefusal(null)
    endBill(currentAccountId, bill.billId)
      .then(() => onChanged())
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setBusy(false))
  }

  function readTheHistory() {
    setHistoryError(null)
    fetchBillHistory(currentAccountId, bill.billId)
      .then(setHistory)
      .catch((problem: Error) => setHistoryError(problem.message))
  }

  return (
    <article
      className={`card reveal rule-card ${ended ? 'rule-card--ended' : 'rule-card--live'}`}
      style={rowDelay(place)}
    >
      <div className="rule-card__top">
        <h3 className="rule-card__name">{bill.name}</h3>
        <span className={`rule-card__state ${ended ? 'rule-card__state--ended' : 'rule-card__state--live'}`}>
          {ended ? 'Ended' : 'Standing'}
        </span>
      </div>
      <p className="rule-card__what">
        {euros.format(bill.amount)} · on the {dayInTheMonth(bill.dayOfMonth)}
      </p>
      {ended ? (
        <p className="rule-card__next rule-card__next--ended">
          {/* A moment rather than a day, formatted the way an ended saving rule's is: the
              backend sends an instant, and asADay reads a YYYY-MM-DD. */}
          Ended
          {bill.endedAt === null ? '' : ` on ${dateAndTime.format(new Date(bill.endedAt))}`}. It is
          kept so that what you used to pay stays readable; declare it again if you are paying it
          once more.
        </p>
      ) : (
        <LastTaken bill={bill} />
      )}

      <WhatThisBillCountsAs
        bill={bill}
        currentAccountId={currentAccountId}
        categories={categories}
        filed={filed}
        onFiled={onFiled}
      />

      <div className="rule-card__actions">
        {/* Changing and ending are gone on an ended bill rather than shown disabled: there is
            nothing to put up and nothing left to end, and the backend refuses both in words. What
            stays is the record, which is the whole reason ending is a closing and not a deletion. */}
        {!ended && (
          <>
            <Button
              small
              tone="ghost"
              disabled={busy}
              aria-expanded={changing}
              onClick={() => {
                setRefusal(null)
                setChanging((open) => !open)
              }}
            >
              {changing ? 'Leave it as it is' : 'Change'}
            </Button>
            <Button small tone="ghost" busy={busy} disabled={busy} onClick={end}>
              End it
            </Button>
          </>
        )}
        <Button
          small
          tone="ghost"
          aria-expanded={showing}
          onClick={() => {
            const open = !showing
            setShowing(open)
            if (open) {
              readTheHistory()
            }
          }}
        >
          {showing
            ? ended
              ? 'Hide what it took'
              : 'Hide what it has taken'
            : ended
              ? 'What it took'
              : 'What it has taken'}
        </Button>
      </div>

      {refusal !== null && <Refusal reason={refusal} />}

      {changing && !ended && (
        <ChangeTheBill
          bill={bill}
          currentAccountId={currentAccountId}
          onChanged={() => {
            setChanging(false)
            onChanged()
          }}
        />
      )}

      {showing && <TheBillHistory history={history} ended={ended} problem={historyError} />}
    </article>
  )
}

/**
 * When the bill last actually went out, or that it never has.
 *
 * <p>The one thing about a standing bill a customer cannot work out from the page in front of them.
 * A current account holds a figure rather than a sum of records, so the balance beside this says
 * nothing about which bills moved it, and "did my rent go out this month" is exactly the question
 * this list is read with.
 *
 * <p>A bill that has never been taken says so rather than showing an empty space, and the sentence
 * says why it might be: a bill declared today is a promise about next month, because the date it was
 * declared is where its counting starts.
 *
 * <p>The date the backend sends is the last date that was <em>paid</em>. A month the account could
 * not cover is in the history below as not paid and is deliberately absent from here, because the
 * bill was not taken then.
 */
function LastTaken({ bill }: { bill: RecurringBill }) {
  if (bill.lastTakenOn === null) {
    return (
      <p className="rule-card__next">
        Not taken yet. It goes out on the {dayInTheMonth(bill.dayOfMonth)} of the month, from the
        first one that falls after you declared it.
      </p>
    )
  }
  return <p className="rule-card__next">Last taken on {asADay(bill.lastTakenOn)}.</p>
}

/**
 * What one bill has actually done: every date it fell due on, what became of it, and how late it
 * was.
 *
 * <p>Newest first, as the backend sends it. Nothing here sorts and nothing here counts: the lateness
 * is the backend's own subtraction between the day the money was owed and the moment it was
 * actually settled, which on a wound clock or after downtime is the whole point of the record.
 *
 * <p><strong>A date that took nothing is in the list beside the ones that took money.</strong> That
 * is the half a balance can never hold: a month the rent did not go out leaves no trace in a figure
 * at all, and this is the only place a customer can see that it was presented and refused rather
 * than wonder whether they imagined declaring it.
 *
 * <p>The amount is shown on both, because on an unpaid date it is what is still owed.
 */
function TheBillHistory({
  history,
  ended,
  problem,
}: {
  history: BillOccurrence[] | null
  /**
   * Whether the bill has been ended, which changes only the sentence shown when there is nothing to
   * show. "It has not fallen due yet" is a promise about a date still to come, and on a bill that
   * has stopped existing that date is never coming.
   */
  ended: boolean
  problem: string | null
}) {
  if (problem !== null) {
    return <Refusal reason={problem} />
  }
  if (history === null) {
    return <Waiting label="Loading what this bill has taken…" bars={['100%', '80%']} />
  }
  if (history.length === 0) {
    return (
      <p className="nothing">
        {ended
          ? 'You ended this bill before it ever fell due, so nothing was ever taken for it.'
          : 'This bill has not fallen due yet.'}
      </p>
    )
  }
  return (
    <ul className="timeline occ">
      {history.map((presented, place) => (
        <li key={presented.id} className={BILL_OUTCOME_TONES[presented.outcome]} style={rowDelay(place)}>
          <span className="tl__icon" aria-hidden="true">
            {presented.outcome === 'PAID' ? <ArrowDownIcon /> : <WarningIcon />}
          </span>
          <div className="tl__body">
            <p className="tl__title">
              {BILL_OUTCOME_WORDS[presented.outcome]} · due {asADay(presented.dueOn)}
            </p>
            <p className="tl__meta">
              {presented.outcome === 'PAID' ? (
                <>{euros.format(presented.amount)} left the account</>
              ) : (
                <>
                  {euros.format(presented.amount)} was due and there was not enough in the account,
                  so nothing at all was taken
                </>
              )}
            </p>
            <span className="tl__tags">
              <span className="tl__tag">
                settled {dateAndTime.format(new Date(presented.settledAt))}
              </span>
              {presented.daysLate > 0 && (
                <span className="tl__tag tl__tag--late">
                  {presented.daysLate === 1 ? '1 day late' : `${presented.daysLate} days late`}
                </span>
              )}
            </span>
          </div>
        </li>
      ))}
    </ul>
  )
}

/**
 * What a bill counts as: the category it is in, and the one control that puts it in another or takes
 * it out of every one.
 *
 * <p><strong>The line is there whether the bill is filed or not.</strong> "Not in a category" is an
 * answer rather than a blank — it is exactly the state a customer has to be able to see in order to
 * fix — and a card that only mentioned categories once a bill was in one would hide the thing it is
 * asking them to do.
 *
 * <p><strong>Ended bills read it and cannot change it</strong>, which is the same shape the rest of
 * the card already has: the control is gone rather than shown disabled, because there is nothing
 * left to file, and the label stays because the months the bill was taken for stay explained. An
 * ended <em>category</em> is different again — the bill goes on pointing at it and the card says so
 * out loud, because nothing was silently unlinked and a customer who wants it somewhere else moves
 * it themselves.
 *
 * <p>A select rather than a form with a button, because there is one field and it has a fixed list
 * of values: choosing is the whole request, and a button beside it would be a second press for a
 * decision already made. Taking the bill out is the empty option in that same list for the same
 * reason.
 *
 * <p>Nothing is worked out here. What the categories are is the backend's answer, what happened to
 * the label is the backend's answer, and a refusal is the backend's own sentence put on screen
 * unchanged — which is the only way somebody who filed a bill under a category they have since
 * ended finds out why.
 */
function WhatThisBillCountsAs({
  bill,
  currentAccountId,
  categories,
  filed,
  onFiled,
}: {
  bill: RecurringBill
  currentAccountId: number
  categories: SpendingCategory[] | null
  filed: BillInACategory | null
  onFiled: () => void
}) {
  const [busy, setBusy] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)
  const ended = bill.state === 'ENDED'
  const endedCategory = filed !== null && filed.categoryState === 'ENDED'

  function file(chosen: string) {
    setBusy(true)
    setRefusal(null)
    const asked =
      chosen === ''
        ? takeBillOutOfEveryCategory(currentAccountId, bill.billId)
        : putBillInACategory(currentAccountId, bill.billId, Number(chosen))
    asked
      .then(() => onFiled())
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setBusy(false))
  }

  return (
    <div className="rule-card__filed">
      <p className="rule-card__what">
        {filed === null || filed.categoryName === null ? (
          <>Not in a category</>
        ) : (
          <>
            Counts as {filed.categoryName}
            {endedCategory && ' · a category you have ended'}
          </>
        )}
      </p>

      {/* The words to choose from are the standing ones. A category that was ended is a record of
          months already gone rather than a word still in use, and the backend refuses a bill filed
          under one — so offering it here would be offering a press that cannot succeed. The one it
          is already in is offered even so when that one has ended, because a list that did not hold
          the current value would silently show the bill as filed nowhere. */}
      {!ended && categories !== null && categories.length > 0 && (
        <div className="select select--small">
          <label className="sr-only" htmlFor={`bill${bill.billId}Category`}>
            What this bill counts as
          </label>
          <select
            id={`bill${bill.billId}Category`}
            value={filed?.categoryId ?? ''}
            disabled={busy}
            onChange={(event) => file(event.target.value)}
          >
            <option value="">Not in a category</option>
            {endedCategory && filed !== null && filed.categoryId !== null && (
              <option value={filed.categoryId}>{filed.categoryName} (ended)</option>
            )}
            {categories.map((category) => (
              <option key={category.categoryId} value={category.categoryId}>
                {category.name}
              </option>
            ))}
          </select>
        </div>
      )}

      {refusal !== null && <Refusal reason={refusal} />}
    </div>
  )
}

/**
 * The boxes for changing a bill, filled with what it says now.
 *
 * <p><strong>Only the boxes that were actually edited are sent.</strong> Absent means "leave it
 * alone" to the backend, so a customer putting the rent up sends an amount and nothing else — which
 * is what makes the day and the name safe from a form that merely redisplayed them. A form that sent
 * all three every time would work, and would quietly overwrite a change somebody made in another
 * tab a moment ago.
 *
 * <p>Sending nothing at all is refused by the backend rather than guarded against here, and the
 * sentence it answers with is shown as it arrived: it is the same refusal a change with an empty
 * body gets from anywhere else.
 */
function ChangeTheBill({
  bill,
  currentAccountId,
  onChanged,
}: {
  bill: RecurringBill
  currentAccountId: number
  onChanged: () => void
}) {
  const [name, setName] = useState(bill.name)
  const [dayOfMonth, setDayOfMonth] = useState(String(bill.dayOfMonth))
  const [amount, setAmount] = useState(bill.amount.toFixed(2))
  const [changing, setChanging] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  function change(event: FormEvent) {
    event.preventDefault()
    setChanging(true)
    setRefusal(null)
    const asked: { name?: string; dayOfMonth?: string; amount?: string } = {}
    if (name !== bill.name) {
      asked.name = name
    }
    if (dayOfMonth !== String(bill.dayOfMonth)) {
      asked.dayOfMonth = dayOfMonth
    }
    if (amount !== bill.amount.toFixed(2)) {
      asked.amount = amount
    }
    changeBill(currentAccountId, bill.billId, asked)
      .then(() => onChanged())
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setChanging(false))
  }

  return (
    <form className="rule-card__form" onSubmit={change}>
      <div className="field">
        <label htmlFor={`bill${bill.billId}Name`}>What it is</label>
        <input
          id={`bill${bill.billId}Name`}
          className="text-input"
          autoComplete="off"
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`bill${bill.billId}Day`}>Day of the month it goes out</label>
        <input
          id={`bill${bill.billId}Day`}
          className="text-input"
          inputMode="numeric"
          autoComplete="off"
          value={dayOfMonth}
          onChange={(event) => setDayOfMonth(event.target.value)}
        />
      </div>
      <EuroAmount
        id={`bill${bill.billId}Amount`}
        label="What it costs"
        value={amount}
        onType={setAmount}
      />
      <Button type="submit" block busy={changing} disabled={changing}>
        {changing ? 'Changing it…' : 'Change this bill'}
      </Button>
      {refusal !== null && <Refusal reason={refusal} />}
    </form>
  )
}

/**
 * The boxes for declaring a new bill.
 *
 * <p>Three of them, all text, for the reason every form in this application is text: what a day of
 * the month is and what an amount of money is are the backend's rulings, in sentences written for
 * the person who typed them, and a form that turned "9OO" into a number before sending it would be
 * answering in its own words a question that already has a sentence waiting for it.
 *
 * <p>The boxes are emptied only when the declaration was accepted, so that a refused bill is still
 * on screen to be corrected rather than retyped.
 */
function DeclareABill({
  currentAccountId,
  onDeclared,
}: {
  currentAccountId: number
  onDeclared: () => void
}) {
  const [name, setName] = useState('')
  const [dayOfMonth, setDayOfMonth] = useState('')
  const [amount, setAmount] = useState('')
  const [saving, setSaving] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  function declare(event: FormEvent) {
    event.preventDefault()
    setSaving(true)
    setRefusal(null)
    declareABill(currentAccountId, { name, dayOfMonth, amount })
      .then(() => {
        setName('')
        setDayOfMonth('')
        setAmount('')
        onDeclared()
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setSaving(false))
  }

  return (
    <article className="card reveal">
      <h3 className="card__title">Declare a bill</h3>
      <form onSubmit={declare}>
        <div className="field">
          <label htmlFor="billName">What it is</label>
          <input
            id="billName"
            className="text-input"
            placeholder="Rent"
            autoComplete="off"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="billDay">Day of the month it goes out</label>
          <input
            id="billDay"
            className="text-input"
            inputMode="numeric"
            placeholder="1"
            autoComplete="off"
            value={dayOfMonth}
            onChange={(event) => setDayOfMonth(event.target.value)}
          />
        </div>
        <EuroAmount id="billAmount" label="What it costs" value={amount} onType={setAmount} />
        <div className="rule-card__actions">
          <Button type="submit" busy={saving} disabled={saving}>
            Declare it
          </Button>
        </div>
        {refusal !== null && <Refusal reason={refusal} />}
      </form>
      <p className="rule">
        A bill declared on the 31st is taken on the last day of a shorter month. It goes out
        overnight, from the first date that falls after you declare it, and it is presented after
        your saving rules have had their cut — so saving too hard is a thing that can cost you.
      </p>
    </article>
  )
}

/**
 * Where the income is declared, on the page that used to declare it.
 *
 * <p>A link and nothing else. A payday rule takes its day from the declaration, so this page has to
 * say where that declaration lives — but the moment it also held boxes to change it there would be
 * two editable copies of one sentence, on two screens, each able to make the other wrong.
 *
 * <p>One link per current account, because the declaration belongs to an account rather than to a
 * customer: a household with two of them is paid into one, and picking which would be this page
 * deciding something the customer has not said.
 */
function WhereTheIncomeIsDeclared({
  currentAccounts,
  onOpenCurrentAccount,
}: {
  currentAccounts: CurrentAccount[]
  onOpenCurrentAccount: (currentAccountId: number) => void
}) {
  return (
    <article className="card reveal">
      <p className="rule">
        What you are paid is declared on the current account it lands in, and every payday rule here
        takes its day from that declaration. It is kept in one place so that the two can never
        disagree about when you are paid.
      </p>
      {currentAccounts.length === 0 ? (
        <p className="nothing">An income needs a current account to land in.</p>
      ) : (
        <div className="rule-card__actions">
          {currentAccounts.map((account) => (
            <button
              key={account.id}
              type="button"
              className="link link--small"
              onClick={() => onOpenCurrentAccount(account.id)}
            >
              {currentAccounts.length === 1
                ? 'Declare it on your current account'
                : `Current account ${spacedIban(account.iban)}`}
              <ForwardIcon />
            </button>
          ))}
        </div>
      )}
    </article>
  )
}

/**
 * A rule as somebody is typing it: every field as the text in its box, including the figures.
 *
 * <p>Strings throughout, and that is the contract this whole screen is built on. What a day of the
 * month is, what an amount of money is and whether shares add to a hundred are the backend's
 * rulings, in sentences written to be read by the person who typed them; a form that turned "5o"
 * into a number before sending it would be answering in its own words a question that has a sentence
 * waiting for it.
 */
type RuleAsTyped = {
  name: string
  fromCurrentAccountId: number | null
  trigger: string
  dayOfWeek: string
  dayOfMonth: string
  howMuchMoves: string
  amount: string
  floor: string
  /** What was typed into each goal's share box, by goal. A box left empty is not in the split. */
  shares: Record<number, string>
}

/**
 * The shares a rule is giving to goals its holder has since given up on: what those goals are called
 * and what each of them has.
 *
 * <p>There is no box for one of these and there deliberately is not, because the backend will not
 * take one: a split it is *sent* may only name goals that are live on the account, and it refuses
 * one that does not in so many words. What it does keep is the line an abandoned goal already had —
 * the share then spills into what nothing has claimed rather than vanishing — and that line is the
 * customer's, so this screen says it is there instead of pretending the split is only what it can
 * draw boxes for.
 *
 * <p>See {@link theSplitWasRewritten} for what the form does about it: nothing, unless the customer
 * writes in the boxes it does have.
 */
function theSharesForGoalsGivenUpOn(
  shares: Record<number, string>,
  goals: SavingsGoal[],
  goalsGivenUpOn: SavingsGoal[],
): { id: number; name: string; share: string }[] {
  const live = new Set(goals.map((goal) => goal.id))
  return Object.keys(shares)
    .map(Number)
    .filter((goalId) => !live.has(goalId) && (shares[goalId] ?? '').trim() !== '')
    .sort((left, right) => left - right)
    .map((goalId) => ({
      id: goalId,
      name: nameOfTheGoal(goalId, goals, goalsGivenUpOn),
      share: shares[goalId],
    }))
}

/**
 * Whether the boxes now say something about the split that the rule does not already say.
 *
 * <p><strong>This is what stops a change losing a split nobody touched.</strong> A change PATCHes
 * the split as a whole — an empty one means "stop spreading it" — and the boxes this form can draw
 * are the account's live goals. So a form that sent its boxes on every change would drop the line
 * belonging to a goal that has been given up on, and the customer who was only renaming the rule
 * would get either "the shares in a split add up to 60" on a rule they can now never change, or, if
 * every goal the split named is gone, a silent deletion of the whole split. The same thing would
 * happen on a failed read of the goals, where there are no boxes at all.
 *
 * <p>So the split travels only when it was actually written in. Left alone it is left out of the
 * request, which is the backend's own "leave it alone", and whatever the rule says about goals this
 * page cannot show stays exactly as it was.
 *
 * <p>Written in, the boxes are the split — including dropping a line the backend would refuse to
 * take back — and the note beside them says so before anything is typed.
 */
function theSplitWasRewritten(typed: RuleAsTyped, rule: SavingRule): boolean {
  const asItStands = theRuleAsItStands(rule).shares
  const named = new Set([...Object.keys(asItStands), ...Object.keys(typed.shares)].map(Number))
  return [...named].some(
    (goalId) => (asItStands[goalId] ?? '').trim() !== (typed.shares[goalId] ?? '').trim(),
  )
}

/**
 * The same rule as the API takes it: only the fields this kind of rule has any use for.
 *
 * <p>The backend refuses a rule carrying a figure it would have no use for — a weekly rule with a
 * day of the month, a sweep with a fixed amount — and those refusals are about a request that
 * contradicts itself rather than about anything the customer did. The form has radio buttons and a
 * select, so it always knows which kind is being described, and it sends that kind and nothing else.
 */
function asTheApiTakesIt(typed: RuleAsTyped, goals: SavingsGoal[]): ARuleAsTyped {
  const rule: ARuleAsTyped = {
    name: typed.name,
    fromCurrentAccountId: typed.fromCurrentAccountId ?? 0,
    trigger: typed.trigger,
    howMuchMoves: typed.howMuchMoves,
    // In the account's own order of goals, which is the order they are shown in and therefore the
    // order the customer wrote them: the backend breaks a tie in the cents by that order. Only the
    // live ones, because a split that is sent may only name goals that are live — see
    // {@link theSplitWasRewritten} for what that costs and what the change form does about it.
    split: goals
      .filter((goal) => (typed.shares[goal.id] ?? '').trim() !== '')
      .map((goal) => ({ goalId: goal.id, share: typed.shares[goal.id] })),
  }
  if (typed.trigger === 'WEEKLY') {
    rule.dayOfWeek = typed.dayOfWeek
  }
  if (typed.trigger === 'MONTHLY') {
    rule.dayOfMonth = typed.dayOfMonth
  }
  if (typed.howMuchMoves === 'A_FIXED_AMOUNT') {
    rule.amount = typed.amount
  }
  if (typed.howMuchMoves === 'EVERYTHING_ABOVE') {
    rule.floor = typed.floor
  }
  return rule
}

/**
 * The same thing as a change, which is the rule without the account it draws from: that is not a
 * thing a standing rule can be moved between, so the backend's change takes no such field.
 *
 * <p>And without the split, unless the split is what is being changed. A change says only what it is
 * changing — a field left out is a field left alone — and {@link theSplitWasRewritten} is the whole
 * argument for leaving this one out: the boxes cannot show a share going to a goal its holder gave
 * up on, and a request built from boxes alone would take that share away from somebody who was
 * renaming their rule.
 */
function asAChangeToARule(typed: RuleAsTyped, goals: SavingsGoal[],
                          rule: SavingRule): AChangeToARule {
  const asked = asTheApiTakesIt(typed, goals)
  return {
    name: asked.name,
    trigger: asked.trigger,
    dayOfWeek: asked.dayOfWeek,
    dayOfMonth: asked.dayOfMonth,
    howMuchMoves: asked.howMuchMoves,
    amount: asked.amount,
    floor: asked.floor,
    split: theSplitWasRewritten(typed, rule) ? asked.split : undefined,
  }
}

/** What a rule about to be saved starts out as, on an account with a current account to draw from. */
function aBlankRule(currentAccounts: CurrentAccount[]): RuleAsTyped {
  return {
    name: '',
    fromCurrentAccountId: currentAccounts[0]?.id ?? null,
    trigger: 'WEEKLY',
    dayOfWeek: 'MONDAY',
    dayOfMonth: '1',
    howMuchMoves: 'A_FIXED_AMOUNT',
    amount: '',
    floor: '',
    shares: {},
  }
}

/** And what an existing rule reads as, so that changing one is an edit rather than a retyping. */
function theRuleAsItStands(rule: SavingRule): RuleAsTyped {
  const shares: Record<number, string> = {}
  rule.split.forEach((share) => {
    shares[share.goalId] = String(share.share)
  })
  return {
    name: rule.name,
    fromCurrentAccountId: rule.fromCurrentAccountId,
    trigger: rule.trigger,
    dayOfWeek: rule.dayOfWeek ?? 'MONDAY',
    dayOfMonth: rule.trigger === 'MONTHLY' && rule.dayOfMonth !== null ? String(rule.dayOfMonth) : '1',
    howMuchMoves: rule.howMuchMoves,
    amount: rule.amount === null ? '' : rule.amount.toFixed(2),
    floor: rule.floor === null ? '' : rule.floor.toFixed(2),
    shares,
  }
}

/**
 * The boxes a rule is written in, shared by the form that leaves one standing and the form that
 * changes one.
 *
 * <p>One set of controls rather than two, because the two are the same sentence: a rule is a
 * trigger, a day, a kind of amount, a figure and a split, whether it is being written for the first
 * time or corrected. Two copies would be two places for the wording of "everything above" to drift.
 *
 * <p>The account a rule draws from is the one field only the first of them has. A standing rule
 * cannot be moved to a different current account — the backend's change takes no such field — so
 * offering it on a change would be offering something that cannot happen.
 */
function TheRuleBoxes({
  idPrefix,
  typed,
  onType,
  currentAccounts,
  goals,
  goalsGivenUpOn,
  showTheAccount,
}: {
  idPrefix: string
  typed: RuleAsTyped
  onType: (typed: RuleAsTyped) => void
  currentAccounts: CurrentAccount[]
  goals: SavingsGoal[]
  goalsGivenUpOn: SavingsGoal[]
  showTheAccount: boolean
}) {
  const givenUpOn = theSharesForGoalsGivenUpOn(typed.shares, goals, goalsGivenUpOn)
  return (
    <>
      <div className="field">
        <label htmlFor={`${idPrefix}Name`}>What it is for</label>
        <input
          id={`${idPrefix}Name`}
          className="text-input"
          placeholder="Fifty a week"
          autoComplete="off"
          value={typed.name}
          onChange={(event) => onType({ ...typed, name: event.target.value })}
        />
      </div>

      {showTheAccount && typed.fromCurrentAccountId !== null && (
        <EverydayAccounts
          id={`${idPrefix}From`}
          label="Takes the money from"
          chosen={typed.fromCurrentAccountId}
          accounts={currentAccounts}
          onChoose={(id) => onType({ ...typed, fromCurrentAccountId: id })}
        />
      )}

      <div className="field">
        <label htmlFor={`${idPrefix}Trigger`}>What makes it move</label>
        <div className="select">
          <select
            id={`${idPrefix}Trigger`}
            value={typed.trigger}
            onChange={(event) => onType({ ...typed, trigger: event.target.value })}
          >
            <option value="WEEKLY">Every week</option>
            <option value="MONTHLY">Every month</option>
            <option value="ON_PAYDAY">On payday</option>
          </select>
        </div>
      </div>

      {typed.trigger === 'WEEKLY' && (
        <div className="field">
          <label htmlFor={`${idPrefix}DayOfWeek`}>Which day</label>
          <div className="select">
            <select
              id={`${idPrefix}DayOfWeek`}
              value={typed.dayOfWeek}
              onChange={(event) => onType({ ...typed, dayOfWeek: event.target.value })}
            >
              {DAYS_OF_THE_WEEK.map((day) => (
                <option key={day} value={day}>
                  {DAY_NAMES[day]}
                </option>
              ))}
            </select>
          </div>
        </div>
      )}

      {typed.trigger === 'MONTHLY' && (
        <div className="field">
          <label htmlFor={`${idPrefix}DayOfMonth`}>Which date</label>
          <input
            id={`${idPrefix}DayOfMonth`}
            className="text-input"
            inputMode="numeric"
            placeholder="25"
            autoComplete="off"
            value={typed.dayOfMonth}
            onChange={(event) => onType({ ...typed, dayOfMonth: event.target.value })}
          />
        </div>
      )}

      {typed.trigger === 'ON_PAYDAY' && (
        <p className="rule">
          A payday rule has no day of its own: it moves on the day you declared your income lands, so
          moving payday moves the rule with it.
        </p>
      )}

      <div className="field">
        <label htmlFor={`${idPrefix}HowMuchMoves`}>How much moves</label>
        <div className="select">
          <select
            id={`${idPrefix}HowMuchMoves`}
            value={typed.howMuchMoves}
            onChange={(event) => onType({ ...typed, howMuchMoves: event.target.value })}
          >
            <option value="A_FIXED_AMOUNT">A fixed amount</option>
            <option value="EVERYTHING_ABOVE">Everything above a floor</option>
          </select>
        </div>
      </div>

      {typed.howMuchMoves === 'A_FIXED_AMOUNT' ? (
        <EuroAmount
          id={`${idPrefix}Amount`}
          label="Amount to move"
          value={typed.amount}
          onType={(amount) => onType({ ...typed, amount })}
        />
      ) : (
        <EuroAmount
          id={`${idPrefix}Floor`}
          label="Leave behind (the floor)"
          value={typed.floor}
          onType={(floor) => onType({ ...typed, floor })}
        />
      )}

      {(goals.length > 0 || givenUpOn.length > 0) && (
        <fieldset className="rules__shares">
          <legend>Spread it across your goals (optional)</legend>
          {/* About the boxes, so it is not said on a rule whose only shares are the ones below,
              where there is no box to leave empty and nothing to add to a hundred. */}
          {goals.length > 0 && (
            <p className="rule">
              Whole percentages, adding to a hundred. Leave every box empty and what the rule moves
              lands unallocated, exactly as a deposit you make yourself does.
            </p>
          )}
          {goals.map((goal) => (
            <div className="field rules__share" key={goal.id}>
              <label htmlFor={`${idPrefix}Share${goal.id}`}>{goal.name}</label>
              <input
                id={`${idPrefix}Share${goal.id}`}
                className="text-input"
                inputMode="numeric"
                placeholder="%"
                autoComplete="off"
                value={typed.shares[goal.id] ?? ''}
                onChange={(event) =>
                  onType({
                    ...typed,
                    shares: { ...typed.shares, [goal.id]: event.target.value },
                  })
                }
              />
            </div>
          ))}
          {/* Said rather than drawn as a box, because the backend will not take a split naming a
              goal that is not live and a box that could be typed into would be a box whose contents
              are always refused. The share is real all the same, so it is on the screen: leaving it
              out would make the split look as though it added up to less than a hundred, and it is
              what the sentence underneath is about. */}
          {givenUpOn.length > 0 && (
            <>
              <ul className="rule-card__split rules__gone">
                {givenUpOn.map((share) => (
                  <li key={share.id}>
                    <span className="rule-card__share">{share.share}%</span>
                    {share.name} · a goal you gave up on
                  </li>
                ))}
              </ul>
              <p className="rule">
                {givenUpOn.length === 1
                  ? 'That share is still part of this rule, and what it moves lands in the account without any goal claiming it. It stays exactly as it is while you change anything else here.'
                  : 'Those shares are still part of this rule, and what they move lands in the account without any goal claiming them. They stay exactly as they are while you change anything else here.'}
                {goals.length > 0 &&
                  ' Write in a box above and the split becomes those boxes alone, which then have to add to a hundred between them.'}
              </p>
            </>
          )}
        </fieldset>
      )}
    </>
  )
}

/**
 * What the rule being typed would move if it fired this minute, asked of the backend as it is typed.
 *
 * <p>This is where the dry run earns its place. Somebody filling in "everything above €800, on
 * payday" wants to know what that means for their account *before* they commit to it, and the answer
 * follows the boxes rather than sitting behind a button nobody presses.
 *
 * <p>Nothing is written by asking, which is what makes asking on every keystroke honest: the backend
 * is explicit that a dry run creates no rule, no occurrence, no deposit and no point.
 *
 * <p>A refusal is shown here as the sentence the backend wrote, and calmly: a rule half-typed is
 * refused on nearly every keystroke, and a red alert shaking itself at somebody in the middle of
 * typing an amount would train them to ignore the one that matters when they press the button.
 *
 * <p>The question itself belongs to whoever is asking it, which is why `ask` is handed in rather
 * than built here. The two forms send two different bodies to two different paths: a rule nobody has
 * saved goes to the preview for unsaved rules, which refuses a customer who has no room for an
 * eleventh — the right answer about a rule that would be an eleventh — while a rule that already
 * stands goes to its own preview, because changing it makes no room. Asked the first way, a customer
 * holding the ten this application allows would read "You already have 10 saving rules standing" on
 * every keystroke of a form that creates nothing.
 *
 * <p>`asAsked` is the body as text, and it is what says whether anything has actually changed: two
 * renders of an untouched form build two equal objects that are not the same object. `ask` is held
 * through a reference for the other half of that, so the question actually sent is the newest one
 * rather than whichever one the effect closed over.
 */
function WhatItWouldMoveNow({
  typed,
  ask,
  asAsked,
  goals,
  goalsGivenUpOn,
  weeklyMinimum,
}: {
  typed: RuleAsTyped
  ask: () => Promise<SavingRuleDryRun>
  asAsked: string
  goals: SavingsGoal[]
  goalsGivenUpOn: SavingsGoal[]
  weeklyMinimum: number | null
}) {
  const [dryRun, setDryRun] = useState<SavingRuleDryRun | null>(null)
  const [refusal, setRefusal] = useState<string | null>(null)

  const figure = typed.howMuchMoves === 'A_FIXED_AMOUNT' ? typed.amount : typed.floor
  const worthAsking = typed.fromCurrentAccountId !== null && figure.trim() !== ''
  const latest = useRef(ask)
  latest.current = ask

  /**
   * Only the newest answer paints, and none of them is cancelled. A keystroke is a cheap read that
   * writes nothing, so a request already on its way is left to arrive and ignored rather than
   * aborted — an abort is a failed request in every log that watches this page, and there is nothing
   * failing here.
   */
  const asks = useRef(0)
  useEffect(() => {
    if (!worthAsking) {
      setDryRun(null)
      setRefusal(null)
      return
    }
    const mine = ++asks.current
    // A pause long enough that typing "1250.00" is one question rather than seven, and short enough
    // that the figure feels like it follows the box.
    const soon = setTimeout(() => {
      latest
        .current()
        .then((said) => {
          if (mine === asks.current) {
            setDryRun(said)
            setRefusal(null)
          }
        })
        .catch((problem: Error) => {
          if (mine === asks.current) {
            setDryRun(null)
            setRefusal(problem.message)
          }
        })
    }, 350)
    return () => clearTimeout(soon)
  }, [asAsked, worthAsking])

  if (!worthAsking) {
    return (
      <p className="preview">
        <span className="preview__icon" aria-hidden="true">
          <SparkIcon />
        </span>
        <span className="preview__text">
          Fill in the figure and this says what the rule would move today, before you commit to it.
        </span>
      </p>
    )
  }

  if (refusal !== null) {
    return (
      <p className="preview preview--refused">
        <span className="preview__icon" aria-hidden="true">
          <WarningIcon />
        </span>
        <span className="preview__text">{refusal}</span>
      </p>
    )
  }

  if (dryRun === null) {
    return (
      <p className="preview">
        <span className="preview__icon" aria-hidden="true">
          <SparkIcon />
        </span>
        <span className="preview__text">Working out what it would move…</span>
      </p>
    )
  }

  return (
    <>
      <p className="preview">
        <span className="preview__icon" aria-hidden="true">
          <SparkIcon />
        </span>
        <span className="preview__text">
          If it fired right now it would move <strong>{euros.format(dryRun.wouldMove.amount)}</strong>
          , out of {euros.format(dryRun.balance)} in the account.
          {dryRun.outcome === 'NOTHING_TO_MOVE' &&
            ' There is nothing above the floor today, so it would move nothing — which is arithmetic rather than a failure.'}
          {dryRun.outcome === 'NOT_ENOUGH_MONEY' &&
            dryRun.shortfall !== null &&
            ` The account is ${euros.format(dryRun.shortfall)} short of that today, and a fixed amount moves all of itself or none of it, so nothing would move.`}
        </span>
      </p>
      {dryRun.wouldMove.intoGoals.length > 0 && (
        <ul className="rule-card__split">
          {dryRun.wouldMove.intoGoals.map((share) => (
            <li key={share.goalId}>
              <span className="rule-card__share">{share.share}%</span>
              {nameOfTheGoal(share.goalId, goals, goalsGivenUpOn)} · {euros.format(share.amount)}
            </li>
          ))}
          {dryRun.wouldMove.leftUnallocated > 0 && (
            <li>
              <span className="rule-card__share">rest</span>
              {euros.format(dryRun.wouldMove.leftUnallocated)} would stay unallocated
            </li>
          )}
        </ul>
      )}
      {/* Only about a rule that would actually move what it says. On a NOT_ENOUGH_MONEY outcome
          `wouldMove.amount` is still the whole fixed amount — the figure asked for rather than the
          figure that would move — and printing the sentence off it put "nothing would move" and
          "secures a week by itself — for ever" in the same box, two lines apart. */}
      {dryRun.outcome === 'MOVED' && (
        <SecuresAWeekByItself
          trigger={typed.trigger}
          howMuchMoves={typed.howMuchMoves}
          amount={dryRun.wouldMove.amount}
          weeklyMinimum={weeklyMinimum}
        />
      )}
    </>
  )
}

/**
 * What to call a goal a share names: its name, whether it is still live or was given up on, and its
 * identifier if neither read could be asked.
 */
function nameOfTheGoal(goalId: number, goals: SavingsGoal[], goalsGivenUpOn: SavingsGoal[]): string {
  const known =
    goals.find((goal) => goal.id === goalId) ??
    goalsGivenUpOn.find((goal) => goal.id === goalId)
  return known?.name ?? `Goal ${goalId}`
}

/**
 * The form that leaves a rule standing, with the dry run wired into it.
 *
 * <p>Nothing is judged here. The name, the day, the figure and the shares travel as the text that
 * was typed, and every objection to any of them comes back as the sentence the backend wrote and is
 * shown unchanged — which is why this form can be submitted at all.
 */
function NewRule({
  savingsAccountId,
  currentAccounts,
  goals,
  goalsGivenUpOn,
  weeklyMinimum,
  onLeftStanding,
}: {
  savingsAccountId: number
  currentAccounts: CurrentAccount[]
  goals: SavingsGoal[]
  goalsGivenUpOn: SavingsGoal[]
  weeklyMinimum: number | null
  onLeftStanding: () => void
}) {
  const [typed, setTyped] = useState<RuleAsTyped>(() => aBlankRule(currentAccounts))
  const [leaving, setLeaving] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  // The current accounts arrive after this form is first drawn, so the account it draws from is
  // picked up as soon as there is one to pick.
  useEffect(() => {
    setTyped((was) =>
      was.fromCurrentAccountId === null && currentAccounts.length > 0
        ? { ...was, fromCurrentAccountId: currentAccounts[0].id }
        : was,
    )
  }, [currentAccounts])

  function leaveItStanding(event: FormEvent) {
    event.preventDefault()
    setLeaving(true)
    setRefusal(null)
    leaveARuleStanding(savingsAccountId, asTheApiTakesIt(typed, goals))
      .then(() => {
        setTyped(aBlankRule(currentAccounts))
        onLeftStanding()
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setLeaving(false))
  }

  return (
    <article className="card reveal">
      <h3 className="card__title">A new rule</h3>
      {currentAccounts.length === 0 ? (
        <p className="nothing">A rule needs a current account to take the money from.</p>
      ) : (
        <form onSubmit={leaveItStanding}>
          <TheRuleBoxes
            idPrefix="newRule"
            typed={typed}
            onType={setTyped}
            currentAccounts={currentAccounts}
            goals={goals}
            goalsGivenUpOn={goalsGivenUpOn}
            showTheAccount
          />

          <WhatItWouldMoveNow
            typed={typed}
            ask={() => dryRunSavingRule(savingsAccountId, asTheApiTakesIt(typed, goals))}
            asAsked={JSON.stringify(asTheApiTakesIt(typed, goals))}
            goals={goals}
            goalsGivenUpOn={goalsGivenUpOn}
            weeklyMinimum={weeklyMinimum}
          />

          <Button type="submit" block busy={leaving} disabled={leaving}>
            {leaving ? 'Leaving it standing…' : 'Leave this rule standing'}
          </Button>
          {refusal !== null && <Refusal reason={refusal} />}
        </form>
      )}
    </article>
  )
}

/**
 * The same boxes, filled in with a rule that already stands, and a save that changes it.
 *
 * <p>The whole rule is sent rather than the fields that were touched, because the form is showing
 * the whole rule: what is in the boxes is what the customer means it to say. The one thing it cannot
 * send is the account it draws from — that is not a thing a standing rule can be moved between.
 */
function ChangeTheRule({
  rule,
  savingsAccountId,
  goals,
  goalsGivenUpOn,
  weeklyMinimum,
  onChanged,
}: {
  rule: SavingRule
  savingsAccountId: number
  goals: SavingsGoal[]
  goalsGivenUpOn: SavingsGoal[]
  weeklyMinimum: number | null
  onChanged: () => void
}) {
  const [typed, setTyped] = useState<RuleAsTyped>(() => theRuleAsItStands(rule))
  const [changing, setChanging] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  function change(event: FormEvent) {
    event.preventDefault()
    setChanging(true)
    setRefusal(null)
    changeSavingRule(savingsAccountId, rule.id, asAChangeToARule(typed, goals, rule))
      .then(() => onChanged())
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setChanging(false))
  }

  return (
    <form className="rule-card__form" onSubmit={change}>
      <TheRuleBoxes
        idPrefix={`rule${rule.id}`}
        typed={typed}
        onType={setTyped}
        currentAccounts={[]}
        goals={goals}
        goalsGivenUpOn={goalsGivenUpOn}
        showTheAccount={false}
      />

      <WhatItWouldMoveNow
        typed={{ ...typed, fromCurrentAccountId: rule.fromCurrentAccountId }}
        ask={() =>
          dryRunAChangeToARule(savingsAccountId, rule.id, asAChangeToARule(typed, goals, rule))
        }
        asAsked={JSON.stringify(asAChangeToARule(typed, goals, rule))}
        goals={goals}
        goalsGivenUpOn={goalsGivenUpOn}
        weeklyMinimum={weeklyMinimum}
      />

      <Button type="submit" block busy={changing} disabled={changing}>
        {changing ? 'Changing it…' : 'Change this rule'}
      </Button>
      {refusal !== null && <Refusal reason={refusal} />}
    </form>
  )
}

/**
 * What every rule standing on this account has coming over the next twelve months, in date order.
 *
 * <p>The backend merges them and decides the order, the window and which lines are owed rather than
 * still to come — a page comparing each day against the browser's idea of today would be re-deriving
 * a boundary this application has already drawn against its own clock.
 *
 * <p>A sweep's figure is marked as an illustration rather than a promise, on the line itself. It is
 * worked out from the balance the account has now, and quoting it the way a fixed amount is quoted
 * would be handing somebody a confident figure about a Tuesday in March that nobody can know.
 */
function RulesToCome({ coming }: { coming: SavingRulePreview }) {
  const year = theYearAsOneList(coming)

  return (
    <>
      <div className="section-head">
        <h2>The year ahead</h2>
      </div>
      <article className="card reveal">
        {/* The twelve months named here is a display horizon and is not the scheme. It is how far
            the rules preview looks — the same window the year-ahead bar uses, quoted from one
            place so that the two forward-looking screens a customer can open look equally far —
            and it is decided by the backend's TimelineHorizon rather than by anything the bank
            publishes. The scheme's own twelve, how long a batch of points lasts, is a figure on a
            published version that somebody can reprice from a screen, and every sentence naming
            *that* now reads it from the scheme. Telling the two apart is the whole of the prose
            audit; this one is deliberately left saying twelve. */}
        {year.length === 0 ? (
          <p className="nothing">
            Nothing is due from your rules or your bills in the next twelve months.
          </p>
        ) : (
          <>
            <ul className="ra">
              {year.map((line, place) => (
                <li
                  key={line.key}
                  className={lineClasses(line)}
                  style={rowDelay(place)}
                >
                  <span className="ra__day">
                    {line.owed ? `due tonight, from ${asADay(line.dueOn)}` : asADay(line.dueOn)}
                  </span>
                  {line.kind === 'rule' ? (
                    <>
                      <span className="ra__name">{line.due.ruleName}</span>
                      <span className="ra__amount">{euros.format(line.due.wouldMove.amount)}</span>
                      {line.due.wouldMove.anIllustrationRatherThanAPromise && (
                        <span className="tl__tag tl__tag--illustration">
                          an illustration at today’s balance
                          {line.due.wouldMove.floor === null
                            ? ''
                            : `, down to ${euros.format(line.due.wouldMove.floor)}`}
                        </span>
                      )}
                    </>
                  ) : (
                    <>
                      <span className="ra__name">{line.bill.billName}</span>
                      <span className="ra__amount">− {euros.format(line.bill.amount)}</span>
                      <span className="tl__tag tl__tag--bill">bill</span>
                    </>
                  )}
                </li>
              ))}
            </ul>
            <p className="rule">
              Everything your rules and your bills have coming up to {asADay(coming.until)}. A fixed
              amount will move what it says; a sweep is quoted from today’s balance as an
              illustration, because what it actually moves depends on what is in the account on the
              morning. A bill is exact, and it leaves your current account after the rules have taken
              their cut that morning — so a sweep sitting above a rent is a rent that may go unpaid.
              Anything marked “due tonight” is a morning already past that the next nightly run will
              make.
            </p>
          </>
        )}
      </article>
    </>
  )
}

/**
 * One line of the year ahead: either a morning a rule fires on or a date a bill goes out on.
 *
 * <p>Two shapes rather than one, because the backend sends two and they say different things. A
 * discriminated union is what lets the row below switch on `kind` rather than infer which sort of
 * line it is holding from whichever field happens to be there.
 */
type AYearAheadLine =
  | { kind: 'rule'; key: string; dueOn: string; owed: boolean; due: RuleForecast }
  | { kind: 'bill'; key: string; dueOn: string; owed: boolean; bill: BillForecast }

/**
 * The rules and the bills as one year, in date order.
 *
 * <p>The whole point of drawing them together: a customer looking at March sees the rent sitting
 * next to the sweep that would starve it, which is what makes this list decision support rather
 * than decoration.
 *
 * <p>Nothing is decided here. Both lists arrive in date order over one window, both carry the days
 * the backend’s own calendar named, and each line already says whether it is owed or still to come
 * — so this sorts two ordered lists into one and does no arithmetic against the browser’s clock.
 * Sorting is stable in every engine this runs in, and the rules go in first, so two things on one
 * morning read in the order the night actually runs them: the rules at two, the bills at half past.
 */
function theYearAsOneList(coming: SavingRulePreview): AYearAheadLine[] {
  const lines: AYearAheadLine[] = [
    ...coming.occurrences.map(
      (due): AYearAheadLine => ({
        kind: 'rule',
        key: `rule-${due.ruleId}-${due.dueOn}`,
        dueOn: due.dueOn,
        owed: due.owedRatherThanStillToCome,
        due,
      }),
    ),
    ...coming.bills.map(
      (bill): AYearAheadLine => ({
        kind: 'bill',
        key: `bill-${bill.billId}-${bill.dueOn}`,
        dueOn: bill.dueOn,
        owed: bill.owedRatherThanStillToCome,
        bill,
      }),
    ),
  ]
  return lines.sort((one, other) => one.dueOn.localeCompare(other.dueOn))
}

/** The row’s classes: which of the two it is, and whether the next run already owes it. */
function lineClasses(line: AYearAheadLine): string {
  return [
    'ra__item',
    line.kind === 'bill' ? 'ra__item--bill' : null,
    line.owed ? 'ra__item--owed' : null,
  ]
    .filter((one) => one !== null)
    .join(' ')
}

/**
 * A `YYYY-MM-DD` as a `Date` in the browser's own zone, for measuring one day against another.
 *
 * <p>The time is appended for the reason {@link asADay} gives: a date on its own is parsed as
 * midnight UTC, and the same string with a time and no offset is parsed locally, which leaves the
 * three numbers exactly as the backend sent them. Nothing here converts between zones — every day
 * on this bar was decided in Brussels by the backend, and all this page does is subtract one from
 * another, which is the same answer in any zone as long as it is the same zone for both.
 */
function asADate(day: string): Date {
  return new Date(`${day}T00:00:00`)
}

const A_DAY_IN_MILLISECONDS = 24 * 60 * 60 * 1000

/** Whole days between two of the backend's days. */
function daysBetween(from: string, until: string): number {
  return Math.round((asADate(until).getTime() - asADate(from).getTime()) / A_DAY_IN_MILLISECONDS)
}

/**
 * The month and year a tick is under, for the two ends of the bar. Short, because the right-hand end
 * of a bar on a phone has about six characters to play with.
 */
const monthAndYear = new Intl.DateTimeFormat('nl-BE', { month: 'short', year: 'numeric' })

/**
 * What a savings account has coming in the year ahead, as a bar: the days its points go, and the
 * days its deposits pay.
 *
 * <p>The two rules this application is about are both promises about dates, and until this bar
 * neither was legible as one. What expires next was a single figure on a single day, and when a
 * deposit next pays was a date on a row — one row at a time, in a list ordered by when the money
 * went in rather than by when anything is going to happen. Somebody with eight deposits had eight
 * dates scattered down a page in an order unrelated to the order they arrive in.
 *
 * <p>Everything on it belongs to the deposits in *this* account. That is a departure from the two
 * figures above it, which are the customer's and read the same beside every account they hold, and
 * it is what makes a bar per pot mean anything. Nothing here decided that: the backend answers per
 * account, and this page draws what it is sent.
 *
 * <p>Nothing on this page works out what day it is. The window comes down with the markers, because
 * this application's clock can be wound a year forward for a demonstration and a bar positioned
 * against `new Date()` would be drawn for a year nobody is in — every marker crowded off the
 * right-hand end while the application went on behaving as though it were next March. The only
 * arithmetic here is where a day falls between two days.
 *
 * <p>Twelve months is the whole of what is coming rather than the first screenful of it: nothing
 * survives longer than its own twelve months and nothing waits longer than a year to pay. The
 * sentence under the bar says so, because a bar that did not would leave somebody wondering what was
 * past its right-hand edge.
 *
 * <p>It is a list as well as a picture. Each marker carries its own sentence for a screen reader, so
 * that what the bar is for is not information only available to somebody who can see it — and the
 * figures are in text beside the dots rather than only in their positions, because a position is not
 * a number anybody can read off a 400px screen.
 */
function YearAhead({ coming }: { coming: AccountTimeline }) {
  const span = daysBetween(coming.from, coming.until)
  // How long a batch of points lasts, for the sentence under the bar. Read from the scheme and not
  // from this window: the twelve months the bar is drawn over is a display horizon this page was
  // handed, and how long points last is policy the bank publishes. They agree today and there is no
  // reason they have to.
  const pointsLast = useTheSchemeInForce()?.howLongABatchOfPointsLasts ?? null

  return (
    <>
      <div className="section-head">
        <h2>The year ahead</h2>
      </div>
      <article className="card reveal">
        {/* And this twelve is the same display horizon: the length of the bar, handed down with
            the markers on it so that nothing here works out what day it is. Not the scheme's
            points lifetime, which is published, changeable, and named in the sentence under the
            bar from the scheme rather than from here. Deliberately left saying twelve. */}
        {coming.events.length === 0 ? (
          <p className="nothing">
            Nothing is due in or out of this pot in the next twelve months.
          </p>
        ) : (
          <>
            {/* The picture, and only the picture: dots on a line, arrivals above it and departures
                below. Every figure and every date is in the list underneath rather than printed
                against the dots, because two markers a week apart are two labels on top of each
                other at any width worth designing for — and a number a reader has to disentangle
                from another number is worse than no number at all. The dots are what the bar is
                for: where the year is busy, and which way it goes. */}
            <div className="ya" aria-hidden="true">
              <div className="ya__track">
                {/* A tick a month, worked out from the window rather than from a count written in
                    here: the bar is twelve months because the backend said so, and a row of ticks
                    with the 12 in its own markup would be a second place that lived. */}
                {Array.from({ length: 11 }, (_, month) => (
                  <span
                    key={`tick-${month}`}
                    className="ya__tick"
                    style={{ left: `${((month + 1) / 12) * 100}%` }}
                  />
                ))}
                {coming.events.map((event) => (
                  <Dot key={`${event.kind}-${event.on}`} event={event} from={coming.from} span={span} />
                ))}
              </div>
              <div className="ya__ends">
                <span>today</span>
                <span>{monthAndYear.format(asADate(coming.until))}</span>
              </div>
            </div>

            {/* The same markers as facts, in the same order, which is what a screen reader is given
                and what anybody reads the actual figures off. The bar above is marked decorative
                rather than duplicated into labels nobody can lay out. */}
            <ul className="ya__list">
              {coming.events.map((event) => (
                <Coming
                  key={`${event.kind}-${event.on}`}
                  event={event}
                  owed={daysBetween(coming.from, event.on) < 0}
                />
              ))}
            </ul>

            {/* The two rules behind the bar, and the points half of it is now read from the
                scheme rather than written here. It said "Points last twelve months" until the
                scheme stopped being a constant in a Java class and became a row somebody reprices
                from a screen; the day a bank publishes a six-month lifetime, a sentence that goes
                on saying twelve is the application telling a customer the wrong day their points
                go.

                The clause about there being nothing past the right-hand edge went with it, and
                that is a deliberate loss rather than an oversight. It was only ever true because
                the lifetime and this window happened to be the same twelve months, which was safe
                while neither could move and is not safe now that one of them can. The end of the
                bar is named in the sentence, which is the honest version of the same reassurance.

                The second clause is *not* the scheme: what a deposit earns on an anniversary is a
                figure of the savings product's own terms, published from the savings products back
                office, and it is worded here the way the deposits list above words it. */}
            <p className="rule">
              Everything the deposits in this pot have coming, up to {asADay(coming.until)}.
              {pointsLast !== null && (
                <>
                  {' '}
                  Points last {inMonths(pointsLast)} from the day they are earned, and a deposit
                  earns again every year it stays.
                </>
              )}
            </p>
          </>
        )}
      </article>
    </>
  )
}

/**
 * One marker on the bar: a dot, and nothing else.
 *
 * <p>Arrivals sit above the line and departures below it, which is the one thing about this bar that
 * can be read without reading anything: up is points coming to you and down is points leaving. The
 * colours say it a second time — the warm ink points are always written in on these screens, and the
 * amber a deadline has always been written in.
 *
 * <p>A day already gone is pinned to the head of the bar rather than hanging off the front of it.
 * That is not a rounding and it is not a guess: the promise fell, and the sweeps that keep it run
 * overnight, so a marker dated yesterday is a thing happening tonight and the head of the bar is
 * where tonight is. The clamp is the same bargain {@link WeekBar} strikes — the only position it can
 * show that the backend did not send is one the backend's own date has already gone past.
 */
function Dot({ event, from, span }: { event: TimelineEvent; from: string; span: number }) {
  const across = Math.min(Math.max(daysBetween(from, event.on) / span, 0), 1)
  const arriving = event.kind === 'LOYALTY_BONUS'
  return (
    <span
      className={arriving ? 'ya__dot ya__dot--in' : 'ya__dot ya__dot--out'}
      style={{ left: `${across * 100}%` }}
    />
  )
}

/**
 * One thing happening on one day, in words: which way it goes, how many points, and when.
 *
 * <p>The figures live here rather than against the dots, and this list is the accessible content of
 * the whole card — the bar above it is decorative, because a picture of where a year is busy has
 * nothing to say to somebody who is not looking at it that these sentences do not say better.
 *
 * <p>A day already gone is said as tonight rather than as a date in the past, with the date still
 * beside it. "Due tonight" is what a customer can act on; the day it fell on is what explains why,
 * and dropping it would make a bonus look like it arrived from nowhere.
 *
 * <p>Nothing here counts the days between now and then. How long a customer has is the difference
 * between two of the backend's dates, and a page working it out would be a second place the twelve
 * months lived — the same reason {@link ExpiringNext} does no arithmetic on the deadline beside the
 * balance.
 */
function Coming({ event, owed }: { event: TimelineEvent; owed: boolean }) {
  const arriving = event.kind === 'LOYALTY_BONUS'
  return (
    <li className={arriving ? 'ya__item ya__item--in' : 'ya__item ya__item--out'}>
      <span className="ya__sign" aria-hidden="true">
        {arriving ? <SparkIcon /> : <WarningIcon />}
      </span>
      <span className="ya__points">
        {arriving ? '+' : '−'}
        {points.format(event.points)} {event.points === 1 ? 'point' : 'points'}
      </span>
      <span className="ya__what">{arriving ? 'loyalty bonus' : 'expire'}</span>
      <span className="ya__day">
        {owed ? `due tonight, from ${asADay(event.on)}` : asADay(event.on)}
      </span>
    </li>
  )
}

/**
 * The deposits behind the balance above, newest first, each with what it earned and when it next
 * pays. Together with the withdrawals beside them they are what makes the balance checkable: the
 * amounts add up to the one, the points to the other.
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
    <article className="card reveal">
      <h2 className="card__title">Deposits</h2>
      {deposits.length === 0 ? (
        <p className="nothing">No deposits yet.</p>
      ) : (
        <>
          <p className="rule">
            A deposit earns again every year it stays: a tenth of the euros still in it, rounded
            down.
          </p>
          <ul className="timeline">
            {deposits.map((made, place) => (
              <li
                key={made.id}
                className={made.id === landedId ? 'is-landed' : undefined}
                style={rowDelay(place)}
              >
                <span className="tl__icon tl__icon--in" aria-hidden="true">
                  <ArrowUpIcon />
                </span>
                <div className="tl__body">
                  <p className="tl__title">{euros.format(made.amount)}</p>
                  <p className="tl__meta">{dateAndTime.format(new Date(made.depositedAt))}</p>
                  <WhatItEarned deposit={made} />
                  <SavedBefore deposit={made} />
                  <NextAnniversary deposit={made} />
                </div>
                <div className="tl__right">
                  <PointsEarned earned={made.pointsEarned} />
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
    </article>
  )
}

/**
 * The withdrawals behind the money balance, newest first, beside the deposits that make up the other
 * half of it.
 *
 * <p>Each one names the account it returned to by its IBAN rather than by the identifier the backend
 * files it under, because a customer picked that IBAN out of the form's list and it is the only form
 * of the destination that can be checked against a bank statement. An identifier this page cannot
 * put a name to is still shown as one, so a withdrawal is never hidden by not knowing where it went.
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
  const ibans = ibansOf(currentAccounts)
  return (
    <article className="card reveal" style={{ '--delay': '60ms' } as CSSProperties}>
      <h2 className="card__title">Withdrawals</h2>
      {withdrawals.length === 0 ? (
        <p className="nothing">No withdrawals yet.</p>
      ) : (
        <ul className="timeline">
          {withdrawals.map((made, place) => (
            <li
              key={made.id}
              className={made.id === landedId ? 'is-landed' : undefined}
              style={rowDelay(place)}
            >
              <span className="tl__icon tl__icon--out" aria-hidden="true">
                <ArrowDownIcon />
              </span>
              <div className="tl__body">
                <p className="tl__title">{euros.format(made.amount)}</p>
                <p className="tl__meta">{dateAndTime.format(new Date(made.withdrawnAt))}</p>
                <span className="tl__tags">
                  <span className="tl__tag tl__tag--iban">
                    {ibans.get(made.toCurrentAccountId) ??
                      `Current account ${made.toCurrentAccountId}`}
                  </span>
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </article>
  )
}

/**
 * What one movement into savings earned, in the warm ink points are always written in.
 *
 * <p>A deposit that earned nothing says 0 rather than saying nothing: the figure is real — a deposit
 * under a whole euro earns none — and an empty space would read as a row that failed to load. It
 * goes grey instead of warm, because the warm colour is for points a customer actually got.
 */
function PointsEarned({ earned }: { earned: number }) {
  return (
    <span className={earned > 0 ? 'tl__points' : 'tl__points tl__points--none'}>
      {earned > 0 ? `+${points.format(earned)}` : '0'} points
    </span>
  )
}

/**
 * The deadline on the points a customer is holding: how many go next, and the day they go.
 *
 * <p>Nothing at all where there is nothing to lose, which is why both figures arrive as nullable and
 * are checked together. "No points expire next" is true of somebody who has never earned anything;
 * "zero points expire on the 14th" is not true of anybody, and a line showing a 0 beside a date
 * would be inventing a deadline out of an absence of one.
 *
 * <p>A day rather than a moment. Points reach their anniversary at whatever time of day they were
 * earned and the sweep that acts on it runs overnight, so an exact time would be precision the
 * customer cannot act on — and a figure they could catch the application out on.
 *
 * <p>The figures are the backend's and this works out none of them: not the anniversary, not which
 * batch is next, not how many days are left. Counting the days here would be a second place the
 * twelve months lived.
 *
 * <p>The one arithmetic it does is the clamp below, and it is the same bargain {@link WeekBar}
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
 * The end of the money history's reading of a direction: the one it has not been taught.
 *
 * <p><strong>It takes `never`, and that is the whole of what it is for.</strong> The ledger draws
 * six directions and every one of them is named in the rows above; a seventh added to the
 * `MoneyMovement` union in api.ts narrows to this call instead of to nothing, and does not compile
 * until the page has been told what it looks like. Before this the list ended in a fall-through —
 * whatever had not been recognised became `direction === 'INTO_SAVINGS'`, which is `false`, so an
 * unknown row was drawn as money *leaving* savings, with an arrow out of an account it never left
 * and "no points" beside it. A wrong row in a record of somebody's money is worse than a missing
 * one, and nothing would have caught it: the tail had no type error to give, and no test can read a
 * direction nobody has invented yet.
 *
 * <p>It throws rather than drawing something apologetic, because by construction it cannot be
 * reached: the compiler refuses the build that would make it reachable. The sentence is for
 * whoever is looking at a console having forced it past that refusal.
 */
function aDirectionThisPageHasNotBeenTaught(direction: never): never {
  throw new Error(
    `the money history was handed a movement in a direction it has not been taught: ${String(
      direction,
    )}`,
  )
}

/**
 * Everything that moved, newest first, as one list: the euros this customer saved, the euros they
 * took back, and the bills that went out of their everyday account.
 *
 * <p>The question a person asks before they ask anything else about their money, and until this page
 * the application could only answer it one account and one direction at a time. Somebody saving
 * towards two goals moved their money once; reading it back as four lists to interleave by eye is
 * not an answer.
 *
 * <p><strong>The bills are here because a list without them lies by omission.</strong> A customer
 * was being shown every euro they chose to save and none of the euros their landlord took, which is
 * exactly how somebody ends up unable to account for a balance. An attempt that took nothing is in
 * the list too and says so — the money not moving is the information, and a ledger that recorded
 * only successes is the one that leaves people guessing.
 *
 * <p>One list rather than a bills section bolted onto the side of it. The backend sends the four
 * kinds already interleaved and already ordered, and nothing here sorts: a bill sits at the moment
 * it was settled, so an arrear cleared months late reads where the money actually left, and a spend
 * sits at the moment it was recorded and stays there when its split is put right afterwards.
 *
 * <p>Read here rather than handed down, because this is the only screen that wants it and it is read
 * fresh every time the screen is opened — which is what makes going back to it after a deposit show
 * the deposit.
 *
 * <p>Nothing is added up. No running total and no balance column: the same euro moving out of one
 * pot and into another would be counted twice by anybody following a column down, and a running
 * balance across several accounts is not a figure that means anything. What each account is worth is
 * on the account.
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
  accountsError,
}: {
  customerId: number
  currentAccounts: CurrentAccount[]
  accountsError: string | null
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

  const ibans = ibansOf(currentAccounts)

  return (
    <section className="view">
      {/* The everyday accounts are the customer-level read, and without them every row here falls
          back to naming an identifier instead of the IBAN a customer can check against a bank
          statement — which is the whole reason the IBAN is in the row. Said out loud rather than
          left as a difference nobody can account for. */}
      {accountsError !== null && <Refusal reason={accountsError} standing />}

      <article className="card reveal">
        {problem !== null && <Refusal reason={problem} standing />}
        {movements === null && problem === null && (
          <Waiting label="Loading your money history…" bars={['100%', '100%', '70%']} />
        )}

        {movements !== null &&
          (movements.length === 0 ? (
            <p className="nothing">
              Nothing has moved yet: no money in or out of your savings, no bill has fallen due, and
              you have not recorded anything you spent.
            </p>
          ) : (
            <ul className="timeline">
              {movements.map((moved, place) => {
                // Interest first, and before anything reads the current account, because it is the
                // one kind of row with no current account at either end: nothing was debited to
                // pay it. Its own row rather than a branch inside the transfer below, for the same
                // reason a bill has its own — it has no second account and no arrow to draw.
                if (moved.direction === 'INTEREST_INTO_SAVINGS') {
                  return (
                    <InterestOnTheLedger
                      key={`INTEREST-${moved.id}`}
                      interest={moved}
                      place={place}
                    />
                  )
                }
                // And the price of breaking a fixed term, which is the other row with no
                // current account at either end — the euros did not go back to the customer,
                // which is the whole of what a charge is. Its own row rather than a branch inside
                // the transfer below, for the same reason as the interest above it.
                if (moved.direction === 'AN_EARLY_EXIT_CHARGE') {
                  return (
                    <AnEarlyExitChargeOnTheLedger
                      key={`CHARGE-${moved.id}`}
                      charge={moved}
                      place={place}
                    />
                  )
                }
                // And a move between two of this customer's own savings accounts, which is the
                // third row with no everyday account at either end and the only one with savings
                // at both. One row although the backend wrote two, because one button was
                // pressed — so it has to come before anything reads a current account, like the
                // two above it.
                if (moved.direction === 'BETWEEN_SAVINGS_ACCOUNTS') {
                  return (
                    <AMoveOnTheLedger key={`MOVE-${moved.id}`} move={moved} place={place} />
                  )
                }
                const everyday =
                  ibans.get(moved.currentAccountId) ?? `Current account ${moved.currentAccountId}`
                // A bill is its own row rather than a branch inside this one: it carries two
                // dates, a name and an outcome, and none of those mean anything on a deposit.
                if (moved.direction === 'OUT_OF_CURRENT_ACCOUNT') {
                  return (
                    <BillOnTheLedger
                      key={`BILL-${moved.id}`}
                      bill={moved}
                      everyday={everyday}
                      place={place}
                    />
                  )
                }
                // And a spend is its own row again, for the same reason and none of the bill's: it
                // carries a split and a moment it may have been put right at, and it carries no due
                // date and no outcome because nothing about it was ever presented and refused.
                if (moved.direction === 'SPENT_OUT_OF_CURRENT_ACCOUNT') {
                  return (
                    <SpendOnTheLedger
                      key={`SPEND-${moved.id}`}
                      spend={moved}
                      everyday={everyday}
                      place={place}
                    />
                  )
                }
                // Every other direction has been named and returned above, so what is left here
                // is the deposit or the withdrawal this row draws. These three lines are what keep
                // that a fact rather than an observation: inside the guard the direction narrows
                // to `never`, so a direction added to the union arrives as a type error here
                // instead of falling through the line below and being drawn as money leaving
                // savings. See `aDirectionThisPageHasNotBeenTaught`.
                if (moved.direction !== 'INTO_SAVINGS' && moved.direction !== 'OUT_OF_SAVINGS') {
                  return aDirectionThisPageHasNotBeenTaught(moved.direction)
                }
                const into = moved.direction === 'INTO_SAVINGS'
                const savings = `Savings account ${moved.savingsAccountId}`
                return (
                  <li
                    // Deposits and withdrawals are numbered separately, so the identifier only
                    // means anything alongside the direction and the key is the pair.
                    key={`${moved.direction}-${moved.id}`}
                    style={rowDelay(place)}
                  >
                    <span
                      className={into ? 'tl__icon tl__icon--in' : 'tl__icon tl__icon--out'}
                      aria-hidden="true"
                    >
                      {into ? <ArrowUpIcon /> : <ArrowDownIcon />}
                    </span>
                    <div className="tl__body">
                      <p className="tl__title">{into ? 'Into savings' : 'Out of savings'}</p>
                      <p className="tl__meta">{dateAndTime.format(new Date(moved.movedAt))}</p>
                      {/* The two accounts in the order the money travelled, so the row reads as
                          the sentence it is rather than as two labels a reader has to work out
                          the direction of for themselves. */}
                      <span className="tl__between">
                        {into ? everyday : savings}
                        {' → '}
                        {into ? savings : everyday}
                      </span>
                      {/* What the customer did, told apart from what the application did for them.
                          The backend's own answer: a deposit a rule made is an ordinary deposit in
                          every other respect, of an amount somebody might well have typed
                          themselves, so nothing on this row could work it out. Only the automatic
                          ones are marked — a tag on every manual row as well would be a label on
                          the ordinary case, which says nothing. */}
                      {moved.automatic && (
                        <span className="tl__tags">
                          <span className="tl__tag tl__tag--automatic">
                            <SparkIcon />
                            a saving rule did this
                          </span>
                        </span>
                      )}
                    </div>
                    <div className="tl__right">
                      <span className="tl__amount">{euros.format(moved.amount)}</span>
                      {into ? (
                        <PointsEarned earned={moved.pointsEarned} />
                      ) : (
                        // Not a zero. A withdrawal has never earned a point here, and a 0 beside
                        // it would read as a deposit that happened to earn nothing.
                        <span className="tl__points tl__points--none">no points</span>
                      )}
                    </div>
                  </li>
                )
              })}
            </ul>
          ))}
      </article>
    </section>
  )
}

/**
 * One date a bill fell due on, as a row in the ledger of everything that moved.
 *
 * <p><strong>Distinguishable on sight from a deposit, because it is a different kind of
 * thing.</strong> It carries the name the customer recognises rather than "Into savings", it wears
 * the bill history's own colours — green for taken, red for a date that took nothing — and an unpaid
 * one carries the warning mark rather than an arrow. A customer scanning this list for where their
 * money went should not have to read the small print to tell the rent from the saving.
 *
 * <p><strong>Both dates, always.</strong> The day it was owed from and the moment it was settled are
 * different facts, and on an arrear cleared months later they are months apart. The row sits at the
 * settlement, because that is when the money actually left; the day it was owed from is written out
 * beside it, so the history reads as "rent, due 1 March, taken 14 June" rather than quietly moving
 * itself back to March.
 *
 * <p><strong>An unpaid attempt says so twice over</strong> — in the mark, and in the sentence that
 * the money never left. Nothing about it has come off a balance anywhere, and the amount beside it is
 * what was owed rather than what moved, which is the figure the customer still has to find.
 *
 * <p>Nothing here is worked out. The lateness is the backend's own subtraction and the two dates are
 * its own; this draws them.
 */
function BillOnTheLedger({
  bill,
  everyday,
  place,
}: {
  bill: BillMovement
  /** The everyday account the money left, by the IBAN a customer can check against a statement. */
  everyday: string
  place: number
}) {
  const paid = bill.outcome === 'PAID'
  return (
    <li className={BILL_OUTCOME_TONES[bill.outcome]} style={rowDelay(place)}>
      <span className="tl__icon" aria-hidden="true">
        {paid ? <ArrowDownIcon /> : <WarningIcon />}
      </span>
      <div className="tl__body">
        {/* The name first, because that is what the row is about: the rent, told apart from the
            phone bill and from the fifty euros this customer chose to save. */}
        <p className="tl__title">
          {bill.billName} · {BILL_OUTCOME_WORDS[bill.outcome]}
        </p>
        <p className="tl__meta">{dateAndTime.format(new Date(bill.movedAt))}</p>
        <span className="tl__between">
          {paid ? (
            <>
              Due {asADay(bill.dueOn)} · taken from {everyday}
            </>
          ) : (
            <>
              Due {asADay(bill.dueOn)} · there was not enough in {everyday}, so nothing at all left
              it
            </>
          )}
        </span>
        <span className="tl__tags">
          <span className="tl__tag tl__tag--bill">a bill you declared</span>
          {bill.daysLate > 0 && (
            <span className="tl__tag tl__tag--late">
              {bill.daysLate === 1 ? '1 day late' : `${bill.daysLate} days late`}
            </span>
          )}
        </span>
      </div>
      <div className="tl__right">
        <span className="tl__amount">{euros.format(bill.amount)}</span>
        {/* Never a nought and never a points figure. A bill has earned a point in this application
            exactly as often as a withdrawal has, and on an unpaid date the line says the thing the
            whole row exists for: the money is still where it was. */}
        <span className="tl__points tl__points--none">{paid ? 'no points' : 'nothing moved'}</span>
      </div>
    </li>
  )
}

/**
 * One thing the customer spent, as a row in the ledger of everything that moved.
 *
 * <p><strong>Distinguishable on sight from a bill, because it is a different kind of thing.</strong>
 * A bill is a declaration made in advance that repeats every month; a spend is one afternoon, named
 * afterwards by the person who made it. It wears its own mark and its own chip, so that a customer
 * scanning the list for where their money went can tell the rent from the groceries without reading
 * the small print.
 *
 * <p><strong>The split is on the row, in the customer's own words.</strong> "EUR 30,00, Supermarket"
 * is half an answer; what it went on is the other half, and it is the half the whole budgeting
 * feature exists to record. Drawn as chips rather than as a list, because a row in this timeline is
 * scanned and a list is read — and because the parts always add up to the amount already shown on
 * the right, so nothing here has to be totalled by eye.
 *
 * <p>A part filed under nothing says so in words rather than showing a gap, in the same words the
 * budget screen uses: uncategorised is a state somebody chose in a hurry, and a blank where a
 * category should be reads as something the application lost.
 *
 * <p><strong>A spend put right since says so, and a spend nobody has touched says nothing.</strong>
 * That is the whole point of the marker: the ledger does not pretend the customer got it right the
 * first time, and a row that quietly showed a different category from the one it showed yesterday
 * would be the application editing their memory. "Never corrected" on every other row would be a
 * label on the ordinary case, which is a line people learn to skip.
 *
 * <p>The row does not move when it is corrected. The money left when it was recorded, and the only
 * thing a correction changes is the opinion about what it left for.
 *
 * <p>Nothing here is worked out. The split, its names and the moment it was put right are all the
 * backend's own answers; this draws them.
 */
function SpendOnTheLedger({
  spend,
  everyday,
  place,
}: {
  spend: SpendMovement
  /** The everyday account the money left, by the IBAN a customer can check against a statement. */
  everyday: string
  place: number
}) {
  return (
    <li style={rowDelay(place)}>
      <span className="tl__icon tl__icon--spend" aria-hidden="true">
        <BasketIcon />
      </span>
      <div className="tl__body">
        {/* The name first, because that is what the row is about: the supermarket trip, told apart
            from the rent and from the fifty euros this customer chose to save. */}
        <p className="tl__title">{spend.spendName}</p>
        <p className="tl__meta">{dateAndTime.format(new Date(spend.movedAt))}</p>
        <span className="tl__between">Spent out of {everyday}</span>
        <span className="tl__tags">
          <span className="tl__tag tl__tag--spend">something you spent</span>
          {spend.parts.map((part, at) => (
            <span className="tl__tag" key={at}>
              {part.categoryName ?? 'Not filed yet'} · {euros.format(part.amount)}
            </span>
          ))}
          {spend.correctedAt !== null && (
            <span className="tl__tag tl__tag--corrected">
              put right {dateAndTime.format(new Date(spend.correctedAt))}
            </span>
          )}
        </span>
      </div>
      <div className="tl__right">
        <span className="tl__amount">{euros.format(spend.amount)}</span>
        {/* Never a nought. A spend has earned a point in this application exactly as often as a
            bill or a withdrawal has, and a 0 beside it would read as one that happened to earn
            nothing. */}
        <span className="tl__points tl__points--none">no points</span>
      </div>
    </li>
  )
}

/**
 * Giving points away: who they go to, how many, and every gift this customer has been part of.
 *
 * <p>A screen of its own rather than a panel on the overview, and the money history is the shape it
 * borrows: a form *and* the ledger the form writes into.
 *
 * <p>Nothing here decides whether a gift is a gift. The recipient travels as the address they bank
 * under and the points travel as the text that was typed, so every one of the four refusals is the
 * backend's and arrives here already worded. The two things this page does work out for itself are
 * both conveniences over that contract and neither is a ruling:
 *
 * - the picker leaves the signed-in customer out, so the one refusal a customer could stumble into
 *   is unreachable rather than merely explained;
 * - a whole number of points larger than the balance greys the button, the way a reward out of
 *   reach greys its own. Anything that is not a plain whole number — "2.5", "-5", "abc" — is left
 *   pressable on purpose whatever the balance is: the answer somebody needs there is about what a
 *   number of points is, and only the backend gets to give it.
 *
 * <p>The gifts are read here, like the money history's rows, because this is the only screen that
 * wants them. The points balance is not: it is the overview's read, handed down, so that the figure
 * falling after a gift is the backend's new answer rather than this page's subtraction.
 */
function GiftPage({
  customerId,
  pointsToSpend,
  accountsError,
  onGiven,
}: {
  customerId: number
  pointsToSpend: number | null
  accountsError: string | null
  onGiven: () => void
}) {
  // Everybody else who banks here. The customers endpoint is the only way this frontend ever
  // learns of another customer, and this is the first thing behind the sign-in gate to call it.
  const [others, setOthers] = useState<Customer[] | null>(null)
  const [othersError, setOthersError] = useState<string | null>(null)
  const [gifts, setGifts] = useState<Gift[] | null>(null)
  const [giftsError, setGiftsError] = useState<string | null>(null)
  // The recipient as the address they bank under rather than as an identifier, because that is
  // what the request carries: the picker is a convenience over the contract, not a way around it.
  const [recipient, setRecipient] = useState<string | null>(null)
  const [howMany, setHowMany] = useState('')
  const [giving, setGiving] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)
  // The gift just made, kept only long enough to say so. It is the backend's own answer to the
  // request, so the confirmation cannot congratulate somebody for points that did not move.
  const [celebrated, setCelebrated] = useState<Gift | null>(null)

  const loadGifts = useCallback((signal?: AbortSignal) => {
    fetchGifts(customerId, signal)
      .then((theirs) => {
        if (signal?.aborted !== true) {
          setGifts(theirs)
          setGiftsError(null)
        }
      })
      .catch((problem: Error) => {
        if (signal?.aborted !== true) {
          setGiftsError(problem.message)
        }
      })
  }, [customerId])

  useEffect(() => {
    const request = new AbortController()
    loadGifts(request.signal)
    return () => request.abort()
  }, [loadGifts])

  useEffect(() => {
    const request = new AbortController()
    fetchCustomers(request.signal)
      .then((everybody) => {
        if (!request.signal.aborted) {
          // The signed-in customer is not in the list, so a gift to yourself cannot be chosen. The
          // backend still refuses one; this is only about not offering somebody a mistake.
          setOthers(everybody.filter((who) => who.id !== customerId))
          setOthersError(null)
        }
      })
      .catch((problem: Error) => {
        if (!request.signal.aborted) {
          setOthersError(problem.message)
        }
      })
    return () => request.abort()
  }, [customerId])

  /**
   * Somebody who has just been opened joins the list and is chosen, because adding them was the
   * act of deciding who this gift is for — the alternative is a picker that grew a row the person
   * then has to go and click.
   *
   * <p>Added to what is already on screen rather than fetched again. The backend has just answered
   * with the customer it created, so a second read of the directory would ask for a list this page
   * can already write down, and would leave the new row missing for as long as it took.
   */
  const welcome = useCallback((who: Customer) => {
    setOthers((alreadyHere) => (alreadyHere === null ? [who] : [...alreadyHere, who]))
    setRecipient(who.contactDetails)
  }, [])

  useEffect(() => {
    if (celebrated === null) {
      return
    }
    const over = setTimeout(() => setCelebrated(null), 2600)
    return () => clearTimeout(over)
  }, [celebrated])

  // Read only to grey the button, and only for a plain run of digits: a whole number of points is
  // the one class of figure this page is allowed to have an opinion about, because it is the only
  // one it can be sure the backend would read the same way. Anything else — "2.5", "-5", "abc",
  // "1e3" — stays pressable so the answer comes back from the backend in words. Keeping the hint
  // to whole numbers also keeps a half point out of the button's own label.
  const typed = howMany.trim()
  const asked = /^\d+$/.test(typed) ? Number(typed) : null
  const short = asked !== null && pointsToSpend !== null ? asked - pointsToSpend : 0
  const beyondTheBalance = short > 0

  function give(event: FormEvent) {
    event.preventDefault()
    if (recipient === null) {
      return
    }
    setGiving(true)
    setRefusal(null)
    // Sent exactly as typed. What was typed is cleared only once a gift has actually been made: a
    // refusal leaves the form as it was, because the next thing the person does is correct it, and
    // because nothing on this page may change on a gift that did not happen.
    giveGift(customerId, recipient, howMany)
      .then((given) => {
        setHowMany('')
        setCelebrated(given)
        // Both, together, the way a claim reloads the accounts and the redemptions: the points
        // that left are the overview's figure and the gift itself belongs in the list beside it.
        loadGifts()
        onGiven()
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setGiving(false))
  }

  return (
    <section className="view">
      {/* The balance below is the overview's read. Without it the preview can only say it is still
          reading, and the button can never grey against a figure it does not have, so the reason
          has to be on the screen rather than left as a wait that never ends. */}
      {accountsError !== null && <Refusal reason={accountsError} standing />}

      <div className="grid grid--pair">
        <article className="card reveal flash-area">
          {celebrated !== null && <Given gift={celebrated} />}
          <h2 className="card__title">Send points to someone</h2>

          {othersError !== null && <Refusal reason={othersError} standing />}
          {others === null && othersError === null && (
            <Waiting label="Loading the people who bank here…" bars={['100%', '100%']} />
          )}

          {others !== null &&
            (others.length === 0 ? (
              <p className="nothing">
                Nobody else banks here yet. Add somebody below and they can be given points.
              </p>
            ) : (
              <form onSubmit={give}>
                <fieldset className="who-for">
                  <legend>Who is it for?</legend>
                  <ul className="people">
                    {others.map((who) => (
                      <li key={who.id}>
                        {/* The row is the control and the radio underneath it is what the keyboard
                            and a screen reader get, which is the pattern the stylesheet was written
                            for. Named out loud because the words beside it are drawn, not
                            labelled. */}
                        <label className="person">
                          <input
                            type="radio"
                            name="recipient"
                            value={who.contactDetails}
                            aria-label={`${who.name}, ${who.contactDetails}`}
                            checked={recipient === who.contactDetails}
                            onChange={() => setRecipient(who.contactDetails)}
                          />
                          <span className="avatar" aria-hidden="true">
                            {initialsOf(who.name)}
                          </span>
                          {/* Drawn, not read: the radio above already says the name and the
                              address out loud, so leaving these audible would announce the person
                              twice. */}
                          <span className="person__name" aria-hidden="true">
                            {who.name}
                            {/* The address the gift will actually name, shown rather than hidden
                                behind the picker: it is the whole of the request underneath. */}
                            <span className="person__note">{who.contactDetails}</span>
                          </span>
                        </label>
                      </li>
                    ))}
                  </ul>
                </fieldset>

                <div className="amount-field">
                  <label htmlFor="giftPoints">How many points</label>
                  <div className="amount-input">
                    <SparkIcon className="amount-input__star" />
                    <input
                      id="giftPoints"
                      name="giftPoints"
                      inputMode="numeric"
                      placeholder="25"
                      autoComplete="off"
                      value={howMany}
                      onChange={(event) => setHowMany(event.target.value)}
                    />
                  </div>
                </div>

                {/* The overview's figure, and it falls here the moment a gift goes through because
                    the gift asks for the accounts again. Nothing is subtracted on this page. */}
                <p className="preview">
                  <SparkIcon className="preview__icon" />
                  <span className="preview__text">
                    {pointsToSpend === null ? (
                      <span className="preview__hint">Reading your points…</span>
                    ) : (
                      <Rising
                        value={pointsToSpend}
                        format={(shown) => (
                          <>
                            You have <strong>{points.format(Math.round(shown))}</strong> points
                            <span className="preview__hint">
                              {recipient === null
                                ? 'Pick somebody to send points to.'
                                : 'They arrive the moment you send them.'}
                            </span>
                          </>
                        )}
                      />
                    )}
                  </span>
                </p>

                {refusal !== null && <Refusal reason={refusal} />}

                {/* Unavailable while a gift is in flight, so one press cannot become two gifts —
                    the shape the reward claim already uses. Unavailable too with nobody chosen or
                    nothing typed, which are not refusals but a form that has not been filled in,
                    and greyed for a figure beyond the balance, which is a hint and not the rule. */}
                <Button
                  type="submit"
                  block
                  busy={giving}
                  disabled={giving || recipient === null || typed === '' || beyondTheBalance}
                >
                  {giving
                    ? 'Giving…'
                    : beyondTheBalance
                      ? `${points.format(short)} to go`
                      : 'Give points'}
                </Button>
              </form>
            ))}

          {/* Outside the branch above on purpose: the moment there is nobody to give to is exactly
              the moment somebody needs adding, so the one control that fixes it must not be the one
              the empty list hides. */}
          {others !== null && <AddSomebody onAdded={welcome} />}
        </article>

        <aside className="card reveal" style={{ '--delay': '60ms' } as CSSProperties}>
          <h2 className="card__title">Gift history</h2>
          <Gifts
            gifts={gifts}
            problem={giftsError}
            landedId={celebrated === null ? null : celebrated.id}
          />
        </aside>
      </div>
    </section>
  )
}

/**
 * Every gift this customer was part of, sent and received in one list, newest first.
 *
 * <p>One list rather than two, because which end of a gift they were on is a property of who is
 * reading it: the whole story reads chronologically, and the direction is carried by the colour of
 * the figure and by the word above it rather than by a minus sign.
 *
 * <p>Each row names the other person, because the customer already knows which one they are.
 */
/**
 * Opening a customer from the gift page, because this is the screen on which their absence is felt:
 * a gift can only be addressed to somebody who banks here, and until there was a way to add one, a
 * training session could demonstrate gifting in exactly one direction.
 *
 * <p>Folded away behind a single control until it is wanted. Giving points is what this page is
 * for and adding somebody is what you do once; two forms competing for the top of the same card
 * would make the rare act look as important as the common one.
 *
 * <p>Nothing here decides whether a person can be added. A name of spaces, an address somebody
 * already banks under, an address in a different case — all of them are the backend's rules, and
 * its sentence is shown unchanged. The form only declines to send a request it can see is empty,
 * which is a form that has not been filled in rather than a refusal.
 */
function AddSomebody({ onAdded }: { onAdded: (who: Customer) => void }) {
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [address, setAddress] = useState('')
  const [adding, setAdding] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)
  // Kept only long enough to say who arrived, the way a gift's own flash is.
  const [welcomed, setWelcomed] = useState<string | null>(null)

  useEffect(() => {
    if (welcomed === null) {
      return
    }
    const over = setTimeout(() => setWelcomed(null), 2600)
    return () => clearTimeout(over)
  }, [welcomed])

  const nothingTyped = name.trim() === '' || address.trim() === ''

  function add(event: FormEvent) {
    event.preventDefault()
    if (adding || nothingTyped) {
      return
    }
    setAdding(true)
    setRefusal(null)
    // Sent as typed. The backend trims, and what it answers with is what it stored — so the name
    // shown in the list below is its reading of the form rather than this page's.
    addCustomer(name, address)
      .then((who) => {
        setName('')
        setAddress('')
        setOpen(false)
        setWelcomed(who.name)
        onAdded(who)
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setAdding(false))
  }

  if (!open) {
    return (
      <div className="add-somebody">
        {/* Said beside the control that did it, rather than in the floating flash a gift gets.
            That one is absolutely positioned above the whole card, which is right for the act the
            card is named after and wrong for this one: it would announce an addition made at the
            foot of the card by covering the heading at the top of the page. */}
        {welcomed !== null && (
          <p className="add-somebody__welcomed" role="status">
            <GivingIcon />
            {welcomed} banks here now
          </p>
        )}
        <Button tone="ghost" small onClick={() => setOpen(true)}>
          Add somebody new
        </Button>
      </div>
    )
  }

  return (
    <form className="add-somebody" onSubmit={add}>
      <h3 className="add-somebody__title">Add somebody to give to</h3>
      {/* What they get is stated rather than left to be discovered, because it is money appearing
          out of nothing and a participant should not have to wonder where it came from. */}
      <p className="add-somebody__note">
        They open with a current account to save from and a savings account to save into.
      </p>

      <div className="field">
        <label htmlFor="newCustomerName">Name</label>
        <input
          className="text-input"
          id="newCustomerName"
          name="newCustomerName"
          autoComplete="off"
          placeholder="Chloe Janssens"
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </div>

      <div className="field">
        <label htmlFor="newCustomerAddress">Email address</label>
        <input
          className="text-input"
          id="newCustomerAddress"
          name="newCustomerAddress"
          type="email"
          inputMode="email"
          autoComplete="off"
          placeholder="chloe.janssens@example.be"
          value={address}
          onChange={(event) => setAddress(event.target.value)}
        />
      </div>

      {refusal !== null && <Refusal reason={refusal} />}

      <div className="add-somebody__actions">
        {/* Unavailable while one is in flight, so one press cannot open two customers — the shape
            the gift button beside it already uses. */}
        <Button type="submit" busy={adding} disabled={adding || nothingTyped}>
          {adding ? 'Adding…' : 'Add them'}
        </Button>
        <Button
          tone="ghost"
          disabled={adding}
          onClick={() => {
            setOpen(false)
            setRefusal(null)
          }}
        >
          Cancel
        </Button>
      </div>
    </form>
  )
}

function Gifts({
  gifts,
  problem,
  landedId,
}: {
  gifts: Gift[] | null
  problem: string | null
  landedId: number | null
}) {
  if (problem !== null) {
    return <Refusal reason={problem} standing />
  }
  if (gifts === null) {
    return <Waiting label="Loading your gifts…" bars={['100%', '100%', '70%']} />
  }
  if (gifts.length === 0) {
    return <p className="nothing">No points given or received yet.</p>
  }
  return (
    <ul className="gifts">
      {gifts.map((gift, place) => {
        const sent = gift.direction === 'SENT'
        const other = sent ? gift.recipientName : gift.senderName
        return (
          <li
            key={gift.id}
            className={`${sent ? 'gift--sent' : 'gift--received'}${gift.id === landedId ? ' is-landed' : ''}`}
            style={rowDelay(place)}
          >
            <span className="gift__who" aria-hidden="true">
              {initialsOf(other)}
            </span>
            <div className="gift__body">
              <p className="gift__name">{sent ? `To ${other}` : `From ${other}`}</p>
              <p className="gift__meta">{dateAndTime.format(new Date(gift.givenAt))}</p>
            </div>
            <span className="gift__points">
              {sent ? '−' : '+'}
              {points.format(gift.points)}
            </span>
          </li>
        )
      })}
    </ul>
  )
}

/** The gift, the moment it exists, said back in the backend's own figures. */
function Given({ gift }: { gift: Gift }) {
  return (
    <>
      <p className="flash">
        <GivingIcon />
        {points.format(gift.points)} points to {gift.recipientName}
      </p>
      <Confetti />
    </>
  )
}

/**
 * How a past deposit's points were arrived at: the euros, the uplift the run of weeks added, and the
 * rate it was paid at — under the date it was made on.
 *
 * <p>Read as a sentence it is the arithmetic itself: seven base and two bonus at 1,30× is where the
 * nine on the right came from, which is the whole reason for showing it — a customer who cannot
 * check a total has to trust it.
 *
 * <p>All three, always, including a bonus of nothing and a rate of 1,00×. Dropping them when they
 * are unremarkable would make "this deposit earned no uplift" and "this deposit does not say" the
 * same row, and would leave the rows a customer most wants to compare — the one before the run
 * started and the one after — laid out differently from each other.
 *
 * <p>Every figure is the backend's, and the rate especially: it is what this deposit was paid when
 * it was made, not what the account earns today. A run that has since lapsed leaves the two
 * disagreeing, and a page that worked one out from the other would be rewriting history to match the
 * present.
 */
function WhatItEarned({ deposit }: { deposit: RecordedDeposit }) {
  return (
    <span className="tl__breakdown">
      {points.format(deposit.basePoints)} base + {points.format(deposit.streakBonusPoints)} bonus{' '}
      {/* The rate and its × stay on one line, and so does the loyalty and its plus: either sign
          alone at the start of a wrapped line reads as a figure with something snapped off it. */}
      <span>at {rate.format(deposit.multiplierApplied)}×</span>{' '}
      {/* What the product contributed, and only where it contributed something. A chip reading
          "1,00× of it the product" on every row of every account on free savings would be noise
          saying that nothing happened — the same reason the new-saving chip is absent on the
          ordinary deposit. Where it is there, it is the half of the rate the customer chose rather
          than earned, and the run of weeks is the rest. */}
      {deposit.productMultiplierApplied !== 1 && (
        <span>({rate.format(deposit.productMultiplierApplied)}× of it the product)</span>
      )}{' '}
      {deposit.loyaltyBonusPoints > 0 && (
        <span>+ {points.format(deposit.loyaltyBonusPoints)} loyalty</span>
      )}
    </span>
  )
}

/**
 * Why a deposit earned on less than it moved: some of these euros had been saved before.
 *
 * <p>A point is paid for a euro saved, and a euro saved twice is one euro — so a deposit filling a
 * gap an earlier withdrawal left earns on the part above the most the customer has ever saved, and
 * on nothing else. That rule is invisible in the two figures beside it: a EUR 100 deposit showing 0
 * points reads as an application that has lost something unless the row says otherwise.
 *
 * <p>Nothing at all for the ordinary deposit, which earned on every euro it moved. A chip on every
 * row saying "all of it was new saving" would be noise on a page where that is almost always true.
 *
 * <p>Two states, two sentences. Part of it counted, and the amount that did is the useful figure;
 * none of it counted, and the amount is nothing, which is not worth printing — what the customer
 * needs then is the reason.
 */
function SavedBefore({ deposit }: { deposit: RecordedDeposit }) {
  if (deposit.newSavings >= deposit.amount) {
    return null
  }
  return (
    <span className="tl__tags">
      <span className="tl__tag">
        {deposit.newSavings > 0
          ? `${euros.format(deposit.newSavings)} of it was new saving`
          : 'Savings you had already earned on'}
      </span>
    </span>
  )
}

/**
 * When this deposit next pays a loyalty bonus, and what that day is worth at what it holds now.
 *
 * <p>The one thing on this list that is about the future. Everything else in a history row is a
 * record of something that happened; this is a promise, and it is here because it is the figure a
 * customer would decide against — what leaving the money alone pays them, which is the same number
 * as what taking it out would cost. Stated once as a chip and left alone: a row of a history is not
 * the place to argue for anything, so there is no "don't miss out" and no arrow pointing at it.
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
 *   rather than drawn as a 0 beside a date — in the page's own grey rather than the warm colour,
 *   because the warm colour is for points a customer is actually getting. The rule it follows from
 *   is stated over the list, so a customer reading this row can see why it says what it says rather
 *   than suspecting the application of a fault.
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
  return (
    <span className="tl__tags">
      <span className={worth === 0 ? 'tl__tag' : 'tl__tag tl__tag--due'}>
        {worth === 0
          ? `Nothing due on ${asADay(on)}`
          : `${points.format(worth)} ${worth === 1 ? 'point' : 'points'} due on ${asADay(on)}`}
      </span>
    </span>
  )
}

/** Moves an amount out of one of the customer's current accounts and into this savings account. */
function DepositForm({
  savingsAccountId,
  currentAccounts,
  justEarned,
  onDeposited,
}: {
  savingsAccountId: number
  currentAccounts: CurrentAccount[]
  justEarned: RecordedDeposit | null
  onDeposited: (made: RecordedDeposit) => void
}) {
  const [amount, setAmount] = useState('')
  const [fromCurrentAccountId, setFromCurrentAccountId] = useState<number | null>(
    currentAccounts[0]?.id ?? null,
  )
  const [depositing, setDepositing] = useState(false)
  const [depositError, setDepositError] = useState<string | null>(null)

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
    <article className="card reveal flash-area">
      {justEarned !== null && <Earned deposit={justEarned} />}
      <h2 className="card__title">Deposit</h2>
      {fromCurrentAccountId === null ? (
        <p className="nothing">A deposit needs a current account to come from.</p>
      ) : (
        <form onSubmit={deposit}>
          <EverydayAccounts
            id="fromCurrentAccount"
            label="From"
            chosen={fromCurrentAccountId}
            accounts={currentAccounts}
            onChoose={setFromCurrentAccountId}
          />
          <EuroAmount id="amount" label="Deposit amount" value={amount} onType={setAmount} />

          <Button type="submit" block busy={depositing} disabled={depositing}>
            {depositing ? 'Depositing…' : 'Deposit'}
          </Button>

          {depositError !== null && <Refusal reason={depositError} />}
        </form>
      )}
    </article>
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
  const [toCurrentAccountId, setToCurrentAccountId] = useState<number | null>(
    currentAccounts[0]?.id ?? null,
  )
  const [withdrawing, setWithdrawing] = useState(false)
  const [withdrawalError, setWithdrawalError] = useState<string | null>(null)

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
    <article className="card reveal" style={{ '--delay': '60ms' } as CSSProperties}>
      <h2 className="card__title">Withdraw</h2>
      {toCurrentAccountId === null ? (
        <p className="nothing">A withdrawal needs a current account to return to.</p>
      ) : (
        <form onSubmit={withdraw}>
          <EverydayAccounts
            id="toCurrentAccount"
            label="Return to"
            chosen={toCurrentAccountId}
            accounts={currentAccounts}
            onChoose={setToCurrentAccountId}
          />
          <EuroAmount
            id="withdrawalAmount"
            label="Withdraw amount"
            value={amount}
            onType={setAmount}
          />

          <Button tone="ghost" type="submit" block busy={withdrawing} disabled={withdrawing}>
            {withdrawing ? 'Withdrawing…' : 'Withdraw'}
          </Button>

          {withdrawalError !== null && <Refusal reason={withdrawalError} />}
        </form>
      )}
    </article>
  )
}

/**
 * Which everyday account the money travels to or from.
 *
 * <p>The same control in both directions, because it is the same question asked from either end and
 * only the word above it changes. Each account is offered as the IBAN it is known by rather than as
 * the identifier the backend files it under: the IBAN is the form of it a customer can check against
 * a bank statement, and it is what a withdrawal names when it turns up in the history afterwards.
 */
function EverydayAccounts({
  id,
  label,
  chosen,
  accounts,
  onChoose,
}: {
  id: string
  label: string
  chosen: number
  accounts: CurrentAccount[]
  onChoose: (currentAccountId: number) => void
}) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="select">
        <select id={id} value={chosen} onChange={(event) => onChoose(Number(event.target.value))}>
          {accounts.map((account) => (
            <option key={account.id} value={account.id}>
              {account.iban}
            </option>
          ))}
        </select>
      </div>
    </div>
  )
}

/**
 * An amount of money, as large as the form can make it, because it is the one thing on the form a
 * person has to get right.
 *
 * <p>The euro sign belongs to the field rather than to what gets typed in it, so nothing has to be
 * stripped back off before the amount is sent. What was typed travels to the backend exactly as it
 * was typed: neither this control nor the form around it has an opinion about what counts as an
 * amount of money, which is why either of them can be submitted at all.
 *
 * <p>The label is the caller's, and it names the direction as well as the thing: "Deposit amount",
 * not "Amount" and not "Deposit". Both forms stand side by side on one screen — one paying money in,
 * one taking it back out — and two fields announced as "Amount, edit text" are two fields nobody
 * listening to the page can tell apart, in a place where getting it wrong moves money the wrong way.
 * The noun stays on the end because the label has to say what the field holds: a card headed
 * "Deposit", holding a field labelled "Deposit", over a button reading "Deposit" is one word doing
 * three jobs and answering none of them.
 */
function EuroAmount({
  id,
  label,
  value,
  onType,
}: {
  id: string
  label: string
  value: string
  onType: (amount: string) => void
}) {
  return (
    <div className="amount-field">
      <label htmlFor={id}>{label}</label>
      <div className="amount-input">
        <span className="amount-input__euro" aria-hidden="true">
          €
        </span>
        <input
          id={id}
          name={id}
          inputMode="decimal"
          placeholder="25.00"
          autoComplete="off"
          value={value}
          onChange={(event) => onType(event.target.value)}
        />
      </div>
    </div>
  )
}

/**
 * What the deposit just earned, for a moment, above the forms that earned it. It says the backend's
 * figure back: a deposit under a euro earns nothing and is told so, which is the whole rule made
 * visible.
 */
function Earned({ deposit }: { deposit: RecordedDeposit }) {
  const earned = deposit.pointsEarned > 0
  return (
    <>
      <p className="flash">
        {earned && <SparkIcon />}
        {earned
          ? `+${points.format(deposit.pointsEarned)} points`
          : `${euros.format(deposit.amount)} saved, no whole euro yet`}
      </p>
      {earned && <Confetti />}
    </>
  )
}

/** Paper thrown in the air, and nothing more. Twelve pieces is enough to read as a celebration, and
 *  it is thrown in the four colours the rest of the page is drawn in. */
function Confetti() {
  const colours = ['var(--kbc-blue)', 'var(--kbc-navy)', 'var(--amber)', 'var(--green)']
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
 * The one thing on a card there is to press.
 *
 * <p>Everything about how a button looks and how it answers a press is decided here rather than at
 * each of the eight places one appears: the dress, the spinner that replaces nothing while it works,
 * and the ripple thrown from wherever the press landed. A caller says what kind of button it is and
 * what it says — and what it says while it is working, because that wording belongs to the thing
 * being done and not to the button.
 *
 * <p>The ripple is drawn from the pointer's own position, so a press at the edge of a wide button
 * spreads from the edge. It is state rather than a node appended to the DOM, because a React tree
 * that is written into from the outside is a tree with two owners; each splash removes itself when
 * its animation ends, so nothing is left behind and no timer is needed to clean up. Nobody who has
 * asked for less motion gets one at all.
 *
 * <p>Only a primary press throws one. A right-click, a long press raising a context menu, or a
 * second finger arriving all fire `pointerdown` without ever firing `click`, and a ripple there
 * would be the button acknowledging a deposit that is not going to happen. The mirror of that is
 * left alone on purpose: Enter and Space fire `click` without `pointerdown`, so a keyboard press
 * gets no ripple — the focus ring it is already wearing is what answers a keyboard.
 *
 * <p>`type` defaults to `button` rather than to HTML's own `submit`. Four of these are inside forms
 * that move money or give points away, and a button added to one of them later — a Cancel, a Max,
 * a picker — must not fire the form by saying nothing. The four that do submit say so.
 *
 * <p>The class name and the press handler are deliberately not accepted from a caller: the whole
 * point of this component is that there is one answer to "what does a button look like, and what
 * happens when you press it".
 */
/* ------------------------------------------------------------------ simulator
 *
 * Branches of a future nobody has taken, drawn side by side with the future the customer is already
 * in. A screen rather than a card, because four columns need the width, and keyed on the account id
 * so that switching pots discards half-built scenarios as every other drill-down does.
 *
 * Three things about this screen are decided here and nowhere else, and each of them is a decision
 * the backend deliberately did not take:
 *
 * One — **every difference is this page's own subtraction.** Nothing is summed across the twelve
 * months and nothing is compared across branches by the backend, by design: the same point can be
 * paid on an anniversary and expire inside the same twelve months, so a net would count it twice in
 * opposite directions. So the backend answers `scenarios[0]` — the year already under way, named in
 * its own words and identified only by being first — and every other column quotes itself against
 * that, here.
 *
 * Two — **the bars are normalised against the tallest month across every column.** A per-column
 * normalisation would draw two very different futures as the same picture, which is the one thing a
 * comparison must not do.
 *
 * Three — **the events carry a day, a kind and a figure, and no words at all**, and `figure` means a
 * different thing in each kind: euros for a goal's target, for what a short withdrawal did take and
 * for money that arrived and earned nothing; points for a bonus and for an expiry; a run of weeks
 * for a week that was lost. There is no one rule for formatting it, so there is a sentence per kind
 * below and a line under the list saying which figure is which.
 *
 * Nothing on this screen works out what day it is. `from` and `until` come down with the answer, for
 * the reason {@link YearAhead}'s bar already gives: this application's clock can be wound a year
 * forward for a demonstration, and a page reading `new Date()` would draw a year nobody is in.
 *
 * And nothing here is kept. The scenarios live in this component's state and are written down
 * nowhere — the page says so at the top, because somebody who closes it loses them.
 */

/** The four changes a customer may make to a future, in the order the form offers them. */
const KINDS_OF_CHANGE: { kind: AKindOfAdjustment; label: string }[] = [
  { kind: 'SAVE_MORE_EACH_WEEK', label: 'Save more each week' },
  { kind: 'STOP_FOR_A_WHILE', label: 'Stop for a while' },
  { kind: 'TAKE_MONEY_OUT', label: 'Take money out' },
  { kind: 'MOVE_A_DEADLINE', label: 'Move a deadline' },
]

/**
 * One change as this page holds it: the change itself, and an identifier of this page's own.
 *
 * <p>The identifier is beside the change rather than inside it, so that what goes up the wire is the
 * change and nothing else. It exists because two withdrawals on two days are two changes and read
 * identically as chips — removing "the second one" has to mean the second one and not the first one
 * that happens to match.
 */
type AChangeBeingBuilt = { id: number; change: AnAdjustmentAsAsked }

/**
 * One future being built, before anybody has asked what it comes to.
 *
 * <p>Keyed on an identifier of this page's own rather than on its name, for the reason the wire
 * format's own note gives: two columns may be called the same thing, and a customer who has named
 * two futures "Take some out" has not asked for one of them to disappear.
 */
type AFutureBeingBuilt = { id: number; called: string; changes: AChangeBeingBuilt[] }

/** Which part of the question a refusal was about, when the backend named one. */
type WhichPartWasRefused = {
  scenario: string | null
  changeNumber: number | null
  change: string | null
}

/**
 * The simulator: build futures, ask about all of them at once, and read them side by side.
 *
 * <p><strong>The first question is asked before anybody has typed anything</strong>, with no
 * scenarios in it at all. That is a real question — what am I heading for — and the backend answers
 * it with the one branch nobody has to ask for, which is how this page has a column to draw, a
 * window to bound its date boxes with, and the account's goals to offer a deadline off, all from the
 * one read that the rest of the screen is measured against.
 *
 * <p><strong>A refusal aborts the whole request and there is no partial answer</strong>, so the
 * scenarios are held here and the last answer is left on screen rather than cleared. Somebody whose
 * third column named a day outside the window has not asked to lose the other three, and a page that
 * emptied itself would make them type the lot again to find out which day was wrong.
 *
 * <p>Nothing here counts the columns or the changes in them. How much this application is willing to
 * imagine at once is a rule, it is written down once in the backend with the reasoning beside it, and
 * a page enforcing it would be a second place those numbers lived — and would have to invent a
 * sentence for a refusal that already has one.
 */
function WhatIfIDidThisInstead({
  savingsAccountId,
  onChanged,
  onBack,
}: {
  savingsAccountId: number
  onChanged: () => void
  onBack: () => void
}) {
  const [futures, setFutures] = useState<TheFutures | null>(null)
  const [futuresError, setFuturesError] = useState<string | null>(null)
  // The scenarios that produced the columns on screen, snapshotted the moment an answer came back.
  //
  // This is the one piece of state on this page that exists for adopting, and it exists because
  // there is no honest alternative. A column carries only what it comes to — the two days, the
  // twelve months and the events — and never the changes it was folded from, so "adopt this column"
  // has to be answered from somewhere else. The obvious somewhere else is `beingBuilt`, and it is
  // wrong: it is a different array, offset by one because the first column is the branch nobody
  // built, and it moves under the customer's fingers. Somebody who asks, then edits a chip, then
  // presses adopt on the column they are reading would adopt the branch they have just half-typed.
  //
  // Held beside `futures` and set in the same `.then`, so the two cannot drift: the columns and the
  // scenarios that made them arrive together or not at all, and `scenarios[n]` is `asAsked[n - 1]`
  // for as long as both are on screen.
  const [asAsked, setAsAsked] = useState<AScenarioToAskAbout[]>([])
  // Beside the sentence rather than inside it: the backend hangs the column and the change a
  // refusal is about next to `detail` as extension members, precisely so that a page drawing four
  // columns can point at one without the sentence changing shape. Null when the refusal is about
  // the asking as a whole.
  const [refusedPart, setRefusedPart] = useState<WhichPartWasRefused | null>(null)
  const [beingBuilt, setBeingBuilt] = useState<AFutureBeingBuilt[]>([])
  const [asking, setAsking] = useState(false)
  // Which column is being adopted, what the last press changed, and what a refused press said —
  // each carrying the column it belongs to, because the answer belongs under the column the
  // customer pressed and nowhere else.
  //
  // One at a time, and a new press clears the last one. A customer adopts the branch they have
  // chosen between, not two of them, and two plans left standing on one screen would read as a
  // question about which of them is in force.
  const [adopting, setAdopting] = useState<number | null>(null)
  const [adopted, setAdopted] = useState<{ place: number; changed: WhatAdoptingChanged } | null>(
    null,
  )
  const [adoptionRefused, setAdoptionRefused] = useState<{ place: number; reason: string } | null>(
    null,
  )
  // One counter for futures and for the changes in them. It only has to be unique on this screen,
  // and it never leaves it.
  const counted = useRef(0)

  const ask = useCallback(
    (asked: AScenarioToAskAbout[], signal?: AbortSignal) => {
      setAsking(true)
      askAboutTheseFutures(savingsAccountId, asked, signal)
        .then((answered) => {
          if (signal?.aborted !== true) {
            setFutures(answered)
            // Together with the columns they produced, never separately: see `asAsked` above.
            setAsAsked(asked)
            setFuturesError(null)
            setRefusedPart(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true) {
            // The backend's own sentence, unchanged. What is added beside it is a locator and not a
            // reason: which column, and which change in it.
            setFuturesError(problem.message)
            setRefusedPart(
              problem instanceof TheSimulationWasRefused
                ? {
                    scenario: problem.scenario,
                    changeNumber: problem.changeNumber,
                    change: problem.change,
                  }
                : null,
            )
          }
        })
        .finally(() => {
          if (signal?.aborted !== true) {
            setAsking(false)
          }
        })
    },
    [savingsAccountId],
  )

  useEffect(() => {
    const request = new AbortController()
    ask([], request.signal)
    return () => request.abort()
  }, [ask])

  function addAFuture() {
    counted.current += 1
    setBeingBuilt((futuresSoFar) => [
      ...futuresSoFar,
      { id: counted.current, called: '', changes: [] },
    ])
  }

  function rename(id: number, called: string) {
    setBeingBuilt((futuresSoFar) =>
      futuresSoFar.map((future) => (future.id === id ? { ...future, called } : future)),
    )
  }

  function addAChange(id: number, change: AnAdjustmentAsAsked) {
    counted.current += 1
    const made = { id: counted.current, change }
    setBeingBuilt((futuresSoFar) =>
      futuresSoFar.map((future) =>
        future.id === id ? { ...future, changes: [...future.changes, made] } : future,
      ),
    )
  }

  function removeAChange(id: number, changeId: number) {
    setBeingBuilt((futuresSoFar) =>
      futuresSoFar.map((future) =>
        future.id === id
          ? { ...future, changes: future.changes.filter((made) => made.id !== changeId) }
          : future,
      ),
    )
  }

  function removeAFuture(id: number) {
    setBeingBuilt((futuresSoFar) => futuresSoFar.filter((future) => future.id !== id))
  }

  /**
   * Turns the column at this place into the plan, and reads everything it may have moved again.
   *
   * <p>The branch that goes up is the one snapshotted beside the answer rather than the one still
   * being edited two sections above, which is the whole of why `asAsked` exists: a press has to
   * adopt the thing that produced the column the customer is looking at, and anything else is the
   * application doing something other than what the screen showed.
   *
   * <p><strong>Afterwards the whole question is asked again.</strong> Adopting raises a declared
   * capacity and moves a deadline, and every column on this screen — including the year already
   * under way, which is what the others are quoted against — was folded from a present that no
   * longer exists. Patching the one column would leave four others arguing against a future the
   * customer has just left. It goes up with the same scenarios, so the comparison the customer was
   * reading is the comparison they get back, redrawn against the plan they now have.
   *
   * <p>And the pages above are told, because the savings account's own figures have moved too.
   *
   * <p>The first column can never be pressed: adopting the year you are already in is not a
   * decision, and there is no button on it to make one with.
   */
  function adopt(place: number) {
    const scenario = asAsked[place - 1]
    if (scenario === undefined) {
      return
    }
    setAdopting(place)
    setAdopted(null)
    setAdoptionRefused(null)
    adoptThisFuture(savingsAccountId, scenario)
      .then((changed) => {
        setAdopted({ place, changed })
        onChanged()
        ask(asAsked)
      })
      .catch((problem: Error) => {
        // The backend's own sentence, unchanged, under the column it was about. All of it or none
        // of it, so there is nothing half-applied to draw and nothing to undo.
        setAdoptionRefused({ place, reason: problem.message })
      })
      .finally(() => setAdopting(null))
  }

  // The tallest month in any column, which every bar on the screen is drawn against. Across all of
  // them rather than within each: two futures that differ by two thousand euros drawn to their own
  // ceilings are two identical pictures, and the whole point of putting them side by side is that
  // they are not.
  const tallest =
    futures === null
      ? 0
      : futures.scenarios.reduce(
          (most, branch) =>
            branch.months.reduce((high, month) => Math.max(high, month.balance), most),
          0,
        )

  const goals = futures?.whereThisAccountStands.goals ?? []

  return (
    <section className="view">
      <button type="button" className="link link--back" onClick={onBack}>
        <BackIcon />
        Back to the account
      </button>

      {/* Standing only while there is nothing on screen yet: a refusal that answers a press should
          move, and one that is already there when the page arrives should not. */}
      {futuresError !== null && <Refusal reason={futuresError} standing={futures === null} />}
      {futuresError !== null && refusedPart !== null && refusedPart.scenario !== null && (
        <p className="sim__where">
          In “{refusedPart.scenario}”
          {refusedPart.changeNumber !== null && `, change ${refusedPart.changeNumber}`}
          {refusedPart.change !== null && ` (${refusedPart.change})`}.
        </p>
      )}

      {futures === null && futuresError === null && (
        <div className="card">
          <Waiting label="Working out the year you are already in…" bars={['11rem', '100%', '68%']} />
        </div>
      )}

      {futures !== null && (
        <>
          <article className="card reveal sim__note">
            <p>
              <strong>Nothing here is saved.</strong> The futures you build below are held on this
              screen and written down nowhere — not in the bank and not on this machine — so closing
              the page, or opening a different pot, loses them. Asking moves no money, earns no point
              and changes no rule.
            </p>
            {futures.anIllustrationRatherThanAPromise && (
              <p className="sim__note-rule">
                Every figure on this screen is an illustration rather than a promise. Each one depends
                on a balance nobody has yet, worked out by replaying the nights from{' '}
                {asADay(futures.from)} to {asADay(futures.until)} one at a time against the rules as
                they stand today.
              </p>
            )}
          </article>

          <div className="section-head">
            <h2>The futures you are asking about</h2>
            <Button small tone="ghost" onClick={addAFuture}>
              Add a future
            </Button>
          </div>

          {beingBuilt.length === 0 ? (
            <article className="card reveal">
              <p className="nothing">
                Nothing asked about yet. The column below is the year already under way; add a future
                to put another beside it.
              </p>
            </article>
          ) : (
            <div className="sim__builders">
              {beingBuilt.map((future, place) => (
                <AFutureToAskAbout
                  key={future.id}
                  future={future}
                  place={place}
                  goals={goals}
                  opensOn={futures.from}
                  closesOn={futures.until}
                  refusedPart={refusedPart}
                  onRename={(called) => rename(future.id, called)}
                  onAddAChange={(change) => addAChange(future.id, change)}
                  onRemoveAChange={(changeId) => removeAChange(future.id, changeId)}
                  onRemove={() => removeAFuture(future.id)}
                />
              ))}
            </div>
          )}

          <div className="sim__ask">
            <Button
              busy={asking}
              disabled={asking}
              onClick={() => ask(beingBuilt.map(asItIsAsked))}
            >
              {asking ? 'Working them out…' : 'Show me these futures'}
            </Button>
            <p className="sim__ask-note">
              All of them go up as one question, so every column is folded off the same present and
              the columns can honestly be read against each other.
            </p>
          </div>

          <div className="section-head">
            <h2>What each of them comes to</h2>
          </div>

          <div className="sim__columns">
            {futures.scenarios.map((branch, place) => (
              // Keyed on where it sits rather than on what it is called, because two columns may be
              // called the same thing and because position is what identifies the first one.
              <ABranchOfTheFuture
                key={place}
                branch={branch}
                carryingOn={futures.scenarios[0]}
                theYearAlreadyUnderWay={place === 0}
                tallest={tallest}
                place={place}
                adopting={adopting === place}
                // Anything at all is in flight, not only this column's own press: two adoptions
                // racing would leave the second one drawn against a screen the first had already
                // moved, and a customer cannot choose between branches they have both taken.
                anotherIsBeingAdopted={adopting !== null && adopting !== place}
                adopted={adopted?.place === place ? adopted.changed : null}
                adoptionRefused={
                  adoptionRefused?.place === place ? adoptionRefused.reason : null
                }
                onAdopt={() => adopt(place)}
              />
            ))}
          </div>
        </>
      )}
    </section>
  )
}

/** A future as the wire takes it: this page's own identifiers left behind. */
function asItIsAsked(future: AFutureBeingBuilt): AScenarioToAskAbout {
  return { called: future.called, adjustments: future.changes.map((made) => made.change) }
}

/**
 * One future being built: what it is called, the changes in it as chips that can be taken out again,
 * and the form that adds another.
 *
 * <p>The name is a box rather than a heading the page writes, because the word is the customer's own
 * and it is what they will read the column back under. Nothing here judges it: a future nobody has
 * named is refused by the backend, in the backend's words, which is where that rule lives.
 */
function AFutureToAskAbout({
  future,
  place,
  goals,
  opensOn,
  closesOn,
  refusedPart,
  onRename,
  onAddAChange,
  onRemoveAChange,
  onRemove,
}: {
  future: AFutureBeingBuilt
  place: number
  goals: SavingsGoal[]
  opensOn: string
  closesOn: string
  refusedPart: WhichPartWasRefused | null
  onRename: (called: string) => void
  onAddAChange: (change: AnAdjustmentAsAsked) => void
  onRemoveAChange: (changeId: number) => void
  onRemove: () => void
}) {
  // Only when the backend named a column and this is it. An unnamed future matching an unnamed
  // locator would ring every card on the screen, so the empty name never matches.
  const refused =
    refusedPart !== null &&
    refusedPart.scenario !== null &&
    refusedPart.scenario !== '' &&
    refusedPart.scenario === future.called

  return (
    <article
      className={refused ? 'card reveal sim__builder sim__builder--refused' : 'card reveal sim__builder'}
      style={rowDelay(place)}
    >
      <div className="field">
        <label htmlFor={`futureCalled${future.id}`}>What you would call it</label>
        <input
          id={`futureCalled${future.id}`}
          className="text-input"
          placeholder="Five hundred out"
          autoComplete="off"
          value={future.called}
          onChange={(event) => onRename(event.target.value)}
        />
      </div>

      {future.changes.length === 0 ? (
        <p className="nothing">
          Nothing in it yet, which would make it the year you are already in a second time.
        </p>
      ) : (
        <ul className="sim__chips">
          {future.changes.map((made, at) => {
            const said = whatThisChangeSays(made.change, goals)
            const pointedAt = refused && refusedPart?.changeNumber === at + 1
            return (
              <li key={made.id} className={pointedAt ? 'sim__chip sim__chip--refused' : 'sim__chip'}>
                <span>{said}</span>
                <button
                  type="button"
                  className="sim__chip-off"
                  aria-label={`Take out: ${said}`}
                  onClick={() => onRemoveAChange(made.id)}
                >
                  <span aria-hidden="true">×</span>
                </button>
              </li>
            )
          })}
        </ul>
      )}

      <AChangeToMake
        at={future.id}
        goals={goals}
        opensOn={opensOn}
        closesOn={closesOn}
        onAdd={onAddAChange}
      />

      <p className="sim__builder-foot">
        <button type="button" className="link link--small" onClick={onRemove}>
          Take this future off the comparison
        </button>
      </p>
    </article>
  )
}

/**
 * The form that adds one change to a future: which of the four, and the boxes that one needs.
 *
 * <p>Four kinds and not a general rule editor, which is what makes two columns comparable and what
 * keeps this form small enough to sit in one. A customer who wants to redesign their automation has
 * the automation screen, which previews itself.
 *
 * <p><strong>The amount goes up as the text that was typed.</strong> Nothing here decides whether
 * "25,00" is an amount of money — that is the backend's ruling and it comes back as a sentence shown
 * unchanged, which is the same bargain the deposit form strikes and the reason this form can be
 * submitted with anything in it at all.
 *
 * <p>The two ends of the window are the backend's own days, handed to the date boxes as their limits
 * rather than worked out from a calendar here. A page that added twelve months to `new Date()` would
 * offer a year nobody is in the moment a trainer wound the clock.
 */
function AChangeToMake({
  at,
  goals,
  opensOn,
  closesOn,
  onAdd,
}: {
  at: number
  goals: SavingsGoal[]
  opensOn: string
  closesOn: string
  onAdd: (change: AnAdjustmentAsAsked) => void
}) {
  const [kind, setKind] = useState<AKindOfAdjustment>('SAVE_MORE_EACH_WEEK')
  const [amount, setAmount] = useState('')
  const [on, setOn] = useState('')
  const [lastDay, setLastDay] = useState('')
  const [goalId, setGoalId] = useState<number | null>(goals[0]?.id ?? null)

  const wantsAnAmount = kind === 'SAVE_MORE_EACH_WEEK' || kind === 'TAKE_MONEY_OUT'
  const wantsALastDay = kind === 'STOP_FOR_A_WHILE'
  const wantsAGoal = kind === 'MOVE_A_DEADLINE'

  function add(event: FormEvent) {
    event.preventDefault()
    onAdd(theChangeAsAsked(kind, amount, on, lastDay, goalId))
    setAmount('')
    setOn('')
    setLastDay('')
  }

  return (
    <form className="sim__form" onSubmit={add}>
      <div className="field">
        <label htmlFor={`changeKind${at}`}>What you would change</label>
        <div className="select select--small">
          <select
            id={`changeKind${at}`}
            value={kind}
            onChange={(event) => setKind(event.target.value as AKindOfAdjustment)}
          >
            {KINDS_OF_CHANGE.map((one) => (
              <option key={one.kind} value={one.kind}>
                {one.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="sim__boxes">
        {wantsAnAmount && (
          <div className="field">
            <label htmlFor={`changeAmount${at}`}>
              {kind === 'SAVE_MORE_EACH_WEEK' ? 'Extra each week' : 'Amount out'}
            </label>
            <input
              id={`changeAmount${at}`}
              className="text-input"
              inputMode="decimal"
              placeholder="25.00"
              autoComplete="off"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
            />
          </div>
        )}

        {wantsAGoal &&
          (goals.length === 0 ? (
            <p className="nothing">There is no goal on this pot to move the day of.</p>
          ) : (
            <div className="field">
              <label htmlFor={`changeGoal${at}`}>Which goal</label>
              <div className="select select--small">
                <select
                  id={`changeGoal${at}`}
                  value={goalId ?? ''}
                  onChange={(event) => setGoalId(Number(event.target.value))}
                >
                  {goals.map((goal) => (
                    <option key={goal.id} value={goal.id}>
                      {goal.name}
                      {goal.deadline === null ? ' (no day on it)' : ` (${asADay(goal.deadline)})`}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          ))}

        <div className="field">
          <label htmlFor={`changeOn${at}`}>{whatTheFirstDayIsCalled(kind)}</label>
          <input
            id={`changeOn${at}`}
            className="text-input"
            type="date"
            min={opensOn}
            max={closesOn}
            value={on}
            onChange={(event) => setOn(event.target.value)}
          />
        </div>

        {wantsALastDay && (
          <div className="field">
            <label htmlFor={`changeUntil${at}`}>Until</label>
            <input
              id={`changeUntil${at}`}
              className="text-input"
              type="date"
              min={opensOn}
              max={closesOn}
              value={lastDay}
              onChange={(event) => setLastDay(event.target.value)}
            />
          </div>
        )}
      </div>

      <Button type="submit" small block tone="ghost">
        Add this change
      </Button>
    </form>
  )
}

/** What the day a change starts on is called, which is a different word in each of the four. */
function whatTheFirstDayIsCalled(kind: AKindOfAdjustment): string {
  switch (kind) {
    case 'SAVE_MORE_EACH_WEEK':
      return 'Starting'
    case 'STOP_FOR_A_WHILE':
      return 'Stopping from'
    case 'TAKE_MONEY_OUT':
      return 'On'
    case 'MOVE_A_DEADLINE':
      return 'Wanted by instead'
  }
}

/**
 * One change as the wire takes it: the kind, and only the boxes that kind uses.
 *
 * <p>The boxes a kind has no use for are left out rather than sent empty, exactly as a saving rule
 * as typed leaves out the fields it has no use for — the backend refuses a change carrying a figure
 * it would have nothing to do with, and sending one would be asking to be refused for something the
 * customer never typed.
 */
function theChangeAsAsked(
  kind: AKindOfAdjustment,
  amount: string,
  on: string,
  lastDay: string,
  goalId: number | null,
): AnAdjustmentAsAsked {
  switch (kind) {
    case 'SAVE_MORE_EACH_WEEK':
    case 'TAKE_MONEY_OUT':
      return { kind, amount, on }
    case 'STOP_FOR_A_WHILE':
      return { kind, on, until: lastDay }
    case 'MOVE_A_DEADLINE':
      return { kind, on, goalId: goalId ?? undefined }
  }
}

/** A change as a chip reads it: what it does, in the customer's own figures. */
function whatThisChangeSays(change: AnAdjustmentAsAsked, goals: SavingsGoal[]): string {
  switch (change.kind) {
    case 'SAVE_MORE_EACH_WEEK':
      return `${theAmountTyped(change.amount)} more a week, from ${theDayChosen(change.on)}`
    case 'STOP_FOR_A_WHILE':
      return `Stop from ${theDayChosen(change.on)} until ${theDayChosen(change.until)}`
    case 'TAKE_MONEY_OUT':
      return `${theAmountTyped(change.amount)} out on ${theDayChosen(change.on)}`
    case 'MOVE_A_DEADLINE': {
      const goal = goals.find((one) => one.id === change.goalId)
      return `${goal?.name ?? 'A goal'} wanted by ${theDayChosen(change.on)} instead`
    }
  }
}

/**
 * The amount exactly as it was typed, behind a euro sign this page draws rather than a figure it
 * formatted. Running it through {@link euros} would be this page deciding what the characters mean,
 * which is the question the backend answers and the whole reason it travels as text.
 *
 * <p>An em dash for a box nobody has filled in, rather than a nought or a sentence: the same reading
 * {@link TheMonthsBehind} gives a month with nothing in it. A nought would be a figure the customer
 * did not type, and a sentence in the middle of a chip would stop the chip being scannable.
 */
function theAmountTyped(amount: string | undefined): string {
  return amount === undefined || amount.trim() === '' ? '€ —' : `€ ${amount}`
}

/** A day that has been chosen, or a dash where one has not been. */
function theDayChosen(day: string | undefined): string {
  return day === undefined || day === '' ? '—' : asADay(day)
}

/**
 * One column: a future, its twelve months as bars and as figures, and the dated things that happen
 * in it.
 *
 * <p>The first column is the year already under way, and it is drawn quieter than the rest —
 * greyer bars, no quote of its own — because it is the thing the others are measured against rather
 * than one of the answers. It is identified by sitting first and named by the backend, which is the
 * only identification there is: a column is not the do-nothing one because of what it is called.
 *
 * <p><strong>The bars are decorative and the figures under them are the content.</strong> A picture
 * of where a year is going has nothing to say to somebody who is not looking at it that a list of
 * twelve balances does not say better, and twelve labels inside a column 230px wide are twelve
 * labels on top of each other — the same bargain {@link YearAhead} strikes with its own bar.
 *
 * <p>Each row is positioned and keyed by the day it closes on rather than by the month it is called.
 * A window opening on the 20th closes its `2026-10` row on 2026-10-20, so the caption is a caption
 * and the day is the fact — which is why the last of them is named in full under the list.
 */
function ABranchOfTheFuture({
  branch,
  carryingOn,
  theYearAlreadyUnderWay,
  tallest,
  place,
  adopting,
  anotherIsBeingAdopted,
  adopted,
  adoptionRefused,
  onAdopt,
}: {
  branch: HowAScenarioTurnsOut
  carryingOn: HowAScenarioTurnsOut
  theYearAlreadyUnderWay: boolean
  tallest: number
  place: number
  adopting: boolean
  anotherIsBeingAdopted: boolean
  adopted: WhatAdoptingChanged | null
  adoptionRefused: string | null
  onAdopt: () => void
}) {
  const opens = branch.months[0]
  const closes = branch.months[branch.months.length - 1]

  return (
    <article
      className={
        theYearAlreadyUnderWay
          ? 'card reveal sim__column sim__column--carrying-on'
          : 'card reveal sim__column'
      }
      style={rowDelay(place)}
    >
      <h3 className="card__title sim__called">{branch.called}</h3>

      {theYearAlreadyUnderWay ? (
        <p className="sim__lead">
          The year already under way. Every column beside it is quoted against this one.
        </p>
      ) : (
        <WhatItComesTo branch={branch} carryingOn={carryingOn} />
      )}

      {/* The picture, and only the picture. Every figure in it is in the list underneath. */}
      <div className="sim__bars" aria-hidden="true">
        <ol className="sim__months">
          {branch.months.map((month) => (
            <li key={month.closesOn} className="sim__month">
              <span className="sim__bar">
                <i
                  className="sim__fill"
                  style={
                    {
                      '--h': `${tallest === 0 ? 0 : Math.round((Math.max(month.balance, 0) / tallest) * 100)}%`,
                    } as CSSProperties
                  }
                />
              </span>
            </li>
          ))}
        </ol>
        {opens !== undefined && closes !== undefined && (
          <div className="sim__ends">
            <span>{monthAndYear.format(asADate(opens.closesOn))}</span>
            <span>{monthAndYear.format(asADate(closes.closesOn))}</span>
          </div>
        )}
      </div>

      <ol className="sim__said">
        {branch.months.map((month) => (
          <li key={month.closesOn} className="sim__row">
            <span className="sim__row-when">{asAShortMonth(month.month)}</span>
            <span className="sim__row-money">{euros.format(month.balance)}</span>
            <span className="sim__row-points">{points.format(month.pointsStanding)} pts</span>
          </li>
        ))}
      </ol>
      {closes !== undefined && (
        <p className="sim__said-rule">
          What the pot holds and what the points balance stands at when each month closes. A row
          closes on the day of the month this window opened on, the last of them on{' '}
          {asADay(closes.closesOn)}. Nothing here is added up: a point can be paid and expire inside
          the same twelve months.
        </p>
      )}

      <WhatHappensAlongTheWay things={branch.thingsThatHappen} />

      {/* The one press on this screen that writes anything, at the foot of the column it is about
          and on no other. There is none on the year already under way: adopting the future you are
          already in is not a decision, and a button offering to make it would be the screen
          inventing one. */}
      {!theYearAlreadyUnderWay && (
        <div className="sim__adopt">
          <Button
            small
            block
            busy={adopting}
            disabled={adopting || anotherIsBeingAdopted}
            onClick={onAdopt}
          >
            {adopting ? 'Writing it down…' : 'I will have this one'}
          </Button>
          <p className="sim__adopt-note">
            Writes down the parts of this future the bank can keep: what you can put away each week,
            and the day a goal is wanted by. All of it or none of it.
          </p>
          {adoptionRefused !== null && <Refusal reason={adoptionRefused} />}
          {adopted !== null && <WhatThePlanNowCarries adopted={adopted} />}
        </div>
      )}
    </article>
  )
}

/**
 * What one press of adopt changed, under the column it was pressed on: one line per change, in the
 * order the customer built the branch in.
 *
 * <p><strong>Every line, and the ones that changed nothing most of all.</strong> Two of the four
 * kinds are deliberately not applied — a withdrawal is a thing a customer does on the day, and
 * pausing is what a rule's own pause already is — and they come back in the same list precisely so
 * that this can draw them. A screen that showed only what was written would be telling somebody
 * they had adopted a future in which five hundred euros comes out, when nothing has taken it out
 * and nothing is going to.
 *
 * <p>So the two readings are told apart by a word and by colour, and the sentence under the list
 * says what the second of them means. The sentences themselves are the backend's own and are shown
 * unchanged: they carry the figures — what the capacity was raised to, by, and from; which goal is
 * now wanted by which day — and those figures are the plan.
 *
 * <p>A branch made of no changes adopts to nothing, and that is not an error. It is a customer
 * deciding to carry on as they are, which this application has nothing to write down, and it says
 * so rather than showing an empty box.
 */
function WhatThePlanNowCarries({ adopted }: { adopted: WhatAdoptingChanged }) {
  const yours = adopted.changes.filter((change) => change.yoursToCarryOut)
  return (
    <div className="sim__adopted" role="status">
      <p className="sim__adopted-head">This is your plan now</p>
      {adopted.changes.length === 0 ? (
        <p className="nothing">
          Nothing to write down: a future made of no changes is the year you are already in.
        </p>
      ) : (
        <ul className="sim__adopted-list">
          {adopted.changes.map((change, at) => (
            <li
              key={`${change.kind}-${at}`}
              className={
                change.yoursToCarryOut
                  ? 'sim__adopted-line sim__adopted-line--yours'
                  : 'sim__adopted-line'
              }
            >
              <span className="sim__adopted-mark" aria-hidden="true">
                {change.yoursToCarryOut ? <WarningIcon /> : <TickIcon />}
              </span>
              <span>{change.what}</span>
            </li>
          ))}
        </ul>
      )}
      {yours.length > 0 && (
        <p className="sim__said-rule">
          The lines above with a warning beside them were not done for you and have not been diarised
          either. Taking money out and standing your rules down are yours to carry out, on the day
          and on the screens they belong to.
        </p>
      )}
    </div>
  )
}

/**
 * A column quoted against the year already under way, which is the whole reason the do-nothing
 * branch is computed at all: "EUR 4 210 in September" is a number, and "EUR 4 210 rather than the
 * EUR 3 890 you were heading for" is an argument.
 *
 * <p>The subtraction is this page's own. The backend compares nothing across branches by design, so
 * two figures are taken off the last month of each column — the balance and the points standing —
 * and nothing else is netted, for the reason stated over the list of months.
 */
function WhatItComesTo({
  branch,
  carryingOn,
}: {
  branch: HowAScenarioTurnsOut
  carryingOn: HowAScenarioTurnsOut
}): ReactElement | null {
  const mine = branch.months[branch.months.length - 1]
  const heading = carryingOn.months[carryingOn.months.length - 1]
  if (mine === undefined || heading === undefined) {
    return null
  }
  return (
    <p className="sim__quote">
      <span className="sim__quote-line">
        {euros.format(mine.balance)} rather than the {euros.format(heading.balance)} you were heading
        for
        {theDifference(mine.balance - heading.balance, (figure) => euros.format(figure))}.
      </span>
      <span className="sim__quote-line sim__quote-line--points">
        {points.format(mine.pointsStanding)} points standing rather than{' '}
        {points.format(heading.pointsStanding)}
        {theDifference(
          mine.pointsStanding - heading.pointsStanding,
          (figure) => `${points.format(figure)} points`,
        )}
        .
      </span>
    </p>
  )
}

/** The gap between two figures, said as a gap. Nothing at all when there is not one. */
function theDifference(difference: number, format: (figure: number) => string): string {
  if (difference === 0) {
    return ', which is exactly the same'
  }
  return difference > 0
    ? ` — ${format(difference)} more`
    : ` — ${format(Math.abs(difference))} less`
}

/**
 * The dated things that happen in one branch, in the order the backend sent them — by day, and
 * within a day in the order the night runs them.
 *
 * <p>Each row is a day, a figure and a sentence, because the events are what the customer is
 * actually deciding about: twelve balances say how a year goes and "a run of four weeks ends on the
 * 27th" says what it costs.
 */
function WhatHappensAlongTheWay({ things }: { things: AThingThatHappens[] }) {
  // The one figure in this list's wording that is policy rather than arithmetic: how long a batch
  // of points lasts, which decides the words on an expiry marker and used to be "twelve months"
  // written into the sentence. It is read here rather than inside the sentence-writer below,
  // because that one is a pure function of an event and is worth keeping that way.
  const pointsLast = useTheSchemeInForce()?.howLongABatchOfPointsLasts ?? null

  if (things.length === 0) {
    return <p className="nothing">Nothing worth marking happens along the way in this one.</p>
  }
  return (
    <>
      <ul className="sim__events">
        {things.map((thing, place) => {
          const said = whatHappensInWords(thing, pointsLast)
          return (
            <li
              key={`${thing.on}-${thing.kind}-${place}`}
              className={`sim__event sim__event--${said.tone}`}
            >
              <span className="sim__event-sign" aria-hidden="true">
                {said.sign}
              </span>
              <span className="sim__event-figure">{said.figure}</span>
              <span className="sim__event-what">{said.what}</span>
              <span className="sim__event-day">{asADay(thing.on)}</span>
            </li>
          )
        })}
      </ul>
      <p className="sim__said-rule">
        A goal reached and a deadline missed carry the goal's target; a bonus and an expiry carry
        points; a short withdrawal carries what it did take; and a week lost carries the run that
        ended.
      </p>
    </>
  )
}

/**
 * One dated thing in words: the figure with its unit, what happened, and how it is coloured.
 *
 * <p><strong>A sentence per kind, because `figure` means a different thing in each.</strong> There
 * is no single formatter that could be right: the same field is a goal's target in euros, a run in
 * weeks, and a count of points. Owning seven sentences is the price of events that carry a figure
 * and no words, and it is the right price — the words belong to the screen, which knows who is
 * reading them, and the figure belongs to the fold.
 *
 * <p>Points are written in amber and money is not, which is the one colour rule this application
 * holds to everywhere. Expiring points are the exception the account's own bar already makes: a
 * departure is set in the quiet ink, because amber on this page means points arriving.
 */
function whatHappensInWords(
  thing: AThingThatHappens,
  /**
   * How long a batch of points lasts, off the scheme in force, and null while that read has not
   * come back. One event's wording names it; the other six do not take it and do not want it.
   *
   * <p>An argument rather than a read of its own, so that this stays a function of an event and a
   * figure. Null is answered by saying less rather than by falling back on twelve, which is the
   * whole point of having stopped writing twelve here.
   */
  howLongABatchOfPointsLasts: number | null,
): {
  figure: string
  what: string
  tone: string
  sign: ReactElement
} {
  switch (thing.kind) {
    case 'A_GOAL_IS_REACHED':
      return {
        figure: euros.format(thing.figure),
        what: 'a goal this size is reached',
        tone: 'good',
        sign: <TickIcon />,
      }
    case 'A_DEADLINE_IS_MISSED':
      return {
        figure: euros.format(thing.figure),
        what: 'a goal this size passes its day unmet',
        tone: 'bad',
        sign: <WarningIcon />,
      }
    case 'A_WEEK_IS_LOST':
      return {
        figure: inWeeks(thing.figure),
        what: 'a run ends, and the rate drops back',
        tone: 'bad',
        sign: <FallingIcon />,
      }
    case 'POINTS_EXPIRE':
      return {
        figure: `−${points.format(thing.figure)} points`,
        what:
          howLongABatchOfPointsLasts === null
            ? 'reach the end of their lifetime and go'
            : `reach their ${inMonths(howLongABatchOfPointsLasts)} and go`,
        tone: 'out',
        sign: <WarningIcon />,
      }
    case 'A_BONUS_IS_PAID':
      return {
        figure: `+${points.format(thing.figure)} points`,
        what: 'a deposit pays its anniversary',
        tone: 'in',
        sign: <SparkIcon />,
      }
    case 'A_WITHDRAWAL_FALLS_SHORT':
      return {
        figure: euros.format(thing.figure),
        what: 'is all the withdrawal could take',
        tone: 'bad',
        sign: <WarningIcon />,
      }
    case 'MONEY_ARRIVES_AND_EARNS_NOTHING':
      return {
        figure: euros.format(thing.figure),
        what: 'arrives and earns nothing — you have saved these euros once already',
        tone: 'out',
        sign: <WarningIcon />,
      }
  }
}

type Splash = { id: number; x: number; y: number; size: number }

function Button({
  type = 'button',
  tone = 'primary',
  small = false,
  block = false,
  busy = false,
  children,
  ...rest
}: Omit<ComponentPropsWithoutRef<'button'>, 'className' | 'onPointerDown'> & {
  tone?: 'primary' | 'ghost'
  small?: boolean
  block?: boolean
  busy?: boolean
}) {
  const [splashes, setSplashes] = useState<Splash[]>([])
  const counted = useRef(0)

  function splash(press: PointerEvent<HTMLButtonElement>) {
    if (stillness || press.button !== 0 || !press.isPrimary) {
      return
    }
    const box = press.currentTarget.getBoundingClientRect()
    const size = Math.max(box.width, box.height)
    counted.current += 1
    setSplashes((made) => [
      ...made,
      {
        id: counted.current,
        size,
        x: press.clientX - box.left - size / 2,
        y: press.clientY - box.top - size / 2,
      },
    ])
  }

  const dress = `btn btn--${tone}${small ? ' btn--small' : ''}${block ? ' btn--block' : ''}`

  return (
    <button {...rest} type={type} className={dress} aria-busy={busy} onPointerDown={splash}>
      {busy && <span className="btn__spinner" aria-hidden="true" />}
      {children}
      {splashes.map((one) => (
        <span
          key={one.id}
          className="ripple"
          style={{ width: one.size, height: one.size, left: one.x, top: one.y }}
          onAnimationEnd={() => setSplashes((made) => made.filter((was) => was.id !== one.id))}
        />
      ))}
    </button>
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
 * lasts — see {@link WeekBar}, which is drawn entirely from what it is handed here.
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

/**
 * A refusal, worded by whoever refused it. Nothing here rewords or shortens what it says.
 *
 * <p>It carries a class of its own as well as the role. The role is the truth about it — something
 * has just gone wrong and it should be announced — but styling off the role alone would mean any
 * `role="alert"` added anywhere later silently inherited a red band, and it is the class that says
 * "this is a refusal" rather than the assistive-technology contract that happens to fit.
 *
 * <p>`standing` is about the shake and only the shake. A refusal that answers a press — an amount
 * the backend would not take, a gift it would not make — arrives because the customer just did
 * something, and the movement is what says "this is about what you did". A refusal that a failed
 * read puts on the screen is already there when the screen arrives, and a page that shakes as it
 * loads is noise rather than emphasis; it is the same argument the stylesheet makes above
 * {@link Notice}, one class over.
 *
 * <p>Both keep the role. That is where a standing refusal and a standing notice part company: the
 * notice is a record that has been true for days and is drawn again on every visit, so announcing
 * it would interrupt somebody to tell them nothing new — whereas a refusal band only exists while
 * something is actually broken, and its arrival is the news.
 */
function Refusal({ reason, standing = false }: { reason: string; standing?: boolean }) {
  return (
    <p className={standing ? 'refusal refusal--standing' : 'refusal'} role="alert">
      <WarningIcon />
      <span>{reason}</span>
    </p>
  )
}

/** The shape of the answer while it is on its way, rather than a sentence about waiting for it. */
function Waiting({ label, bars }: { label: string; bars: string[] }) {
  return (
    <div className="skeleton" aria-busy="true">
      <span className="sr-only">{label}</span>
      {bars.map((width, bar) => (
        <i key={bar} style={{ '--w': width } as CSSProperties} />
      ))}
    </div>
  )
}

/**
 * Rows and cards arrive one after another rather than all at once, which reads as a list being
 * filled in. Only the first handful are staggered; past that it is a wait.
 */
function rowDelay(place: number): CSSProperties {
  return { '--delay': `${Math.min(place, 8) * 45}ms` } as CSSProperties
}

/** The everyday accounts by the identifier the backend files them under, so a movement can name the
 *  IBAN a customer actually picked out of a form. */
function ibansOf(currentAccounts: CurrentAccount[]): Map<number, string> {
  return new Map(currentAccounts.map((account) => [account.id, account.iban]))
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

/** What somebody is greeted by. A full name in a headline reads as an addressee on an envelope;
 *  the first word of it reads as a person being spoken to. */
function firstNameOf(name: string): string {
  const [first] = name.split(/\s+/)
  return first === '' ? name : first
}

/** An IBAN in fours, the way it is printed on a card, without changing what it is. */
function spacedIban(iban: string): string {
  return iban.replace(/(.{4})/g, '$1 ').trim()
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

/* ---------------------------------------------------------------------- icons
 *
 * Every picture on these screens is drawn here, inline, at the size of the text beside it. There is
 * no icon package and no sprite sheet: a dozen paths are cheaper than a dependency, and an icon that
 * lives in the same file as the component using it cannot drift from what that component means.
 */

function HomeIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M3 11.2 12 4l9 7.2V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function ClockIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="8.6" stroke="currentColor" strokeWidth="1.7" />
      <path d="M12 7.4V12l3.4 2" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  )
}

/** The run of weeks, as the one warm thing on the navy panel. */
function FlameIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M13 2.5s1 3.2-1.2 5.4C9.6 10.1 7 10.9 7 14.4A6.2 6.2 0 0 0 13.2 21c3.6 0 6.3-2.6 6.3-6.4 0-4.9-4.3-6.4-4.3-6.4s.6 2.6-.9 3.6c0 0 .4-5.9-1.3-9.3"
        fill="currentColor"
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
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function ForwardIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" className={className}>
      <path
        d="M9.5 5.5 16 12l-6.5 6.5"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function BankIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
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

/** Money on its way into savings. */
function ArrowUpIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M12 19V6m0-2 6 6m-6-6-6 6"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** And on its way back out again. */
function ArrowDownIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M12 5v13m0 2 6-6m-6 6-6-6"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** Something bought rather than a balance moved: the mark a spend wears in the money history. */
function BasketIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M4 9h16l-1.4 9.2a2 2 0 0 1-2 1.8H7.4a2 2 0 0 1-2-1.8L4 9Zm4.5 0L11 4m4.5 5L13 4M9.5 13v3m5-3v3"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** Points, everywhere they are counted. */
function SparkIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" className={className}>
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
      <path
        d="M8.5 5.5c0-1 1-1.2 1-2.2M12 5.5c0-1 1-1.2 1-2.2"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
      />
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

/**
 * A cup on a plinth, for the tab where things are won. The one picture in the strip that is about
 * having achieved something rather than about money moving.
 */
function TrophyIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M7 4h10v5a5 5 0 0 1-10 0V4Z"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <path
        d="M7 6H4.5v1.5A3.5 3.5 0 0 0 8 11M17 6h2.5v1.5A3.5 3.5 0 0 1 16 11"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
      <path
        d="M12 14v3m-3.5 3h7M9 20a3 3 0 0 1 3-3 3 3 0 0 1 3 3"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
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

/**
 * Points on their way to somebody else: an arrow into a person.
 *
 * <p>Its own icon rather than the wrapped box above, which is already spoken for as the picture a
 * reward gets when this page has never heard of its code. A gift here is not a parcel — it is a
 * figure moving from one customer to another — and drawing it as one would have the same square
 * mean two things on the same screen.
 */
function GivingIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      {/* The person it is going to. */}
      <circle cx="16" cy="7" r="3.2" stroke="currentColor" strokeWidth="1.6" />
      <path
        d="M10.4 20.5a5.6 5.6 0 0 1 11.2 0"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
      {/* The points, on their way there. */}
      <path
        d="M2.5 12.5h6.2M5.8 9.6l2.9 2.9-2.9 2.9"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/**
 * A bell, and nothing hanging off it. The count is a badge drawn beside it in the markup rather
 * than a dot inside the picture, so that the number is text a browser can size and read out.
 */
function BellIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M6 9a6 6 0 0 1 12 0c0 4 1.2 5.4 2 6.4H4c.8-1 2-2.4 2-6.4Z"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinejoin="round"
      />
      <path
        d="M10 18.5a2 2 0 0 0 4 0"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  )
}

/** A balance that has climbed past something: the line it stood on, and an arrow off the top. */
function RisingIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M4 16h16"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeDasharray="2.5 2.5"
        strokeLinecap="round"
      />
      <path
        d="M12 13V4m0 0L8 8m4-4 4 4"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** The same picture the other way up: a balance that no longer reaches the line it did. */
function FallingIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M4 8h16"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeDasharray="2.5 2.5"
        strokeLinecap="round"
      />
      <path
        d="M12 11v9m0 0 4-4m-4 4-4-4"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function WarningIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.6" />
      <path
        d="M12 7.5v6M12 16.5h.01"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
    </svg>
  )
}

/** A goal that arrives, and a move that has been made. */
function TickIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="m5 12.6 4.4 4.4L19 7.4"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** A goal with no day on it: the line runs on rather than stopping somewhere. */
function OpenEndedIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M4 12h13m-3.6-3.8L17.6 12l-4.2 3.8"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/** A goal that was closed, and is no longer being saved for. */
function ClosedIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="8.6" stroke="currentColor" strokeWidth="1.7" />
      <path d="M6.1 6.1l11.8 11.8" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  )
}

/** A weekly figure somebody chose, rather than one the plan worked out. */
function PinIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M9 3h6l-1 6 3.4 3.4H6.6L10 9 9 3Zm3 9.4V21"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

/**
 * The catalogue as whoever runs it sees it: every offer in every state, a form for a new one, and
 * the four things that can be done to one that exists.
 *
 * <p><strong>Nothing here is authenticated, and the screen says so out loud.</strong> There is no
 * token, no role and no session anywhere in this application, and the backend does not check that
 * whoever is asking runs the scheme — it cannot, because there is nothing to check against. The
 * sign-in screen is honest about the same thing in the same words, and the sentence at the top of
 * this page is meant just as literally: this is an administration screen and not authorisation.
 * Hiding the link to it would have been worse than saying so, because it would look like a lock.
 *
 * <p>Reached from the footer rather than the tab strip. The five tabs are what a customer does
 * with their money, and pricing the catalogue is not one of those things however many people in a
 * training session want to try it.
 *
 * <p>Drafts are shown beside published offers rather than filed away behind a filter. A draft is a
 * thing somebody is part-way through writing, and the reason it exists is to be finished — so the
 * list it needs to be in is the one they are already looking at, with the state written on it and
 * the button that publishes it beside the words that are still wrong.
 */
function RunningTheCatalogue({
  onBack,
  onCatalogueChanged,
}: {
  onBack: () => void
  onCatalogueChanged: () => void
}) {
  const [offers, setOffers] = useState<AdministeredOffer[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  // A refusal to a press, kept apart from the one a failed read puts up: this one is about what
  // somebody just did, and blanking the list to show it would take the catalogue away from them
  // in order to tell them about one row of it.
  const [refusal, setRefusal] = useState<string | null>(null)
  // Which offer is being worked on rather than whether one is, so the button that was pressed is
  // the one that shows it is working.
  const [working, setWorking] = useState<string | null>(null)

  const load = useCallback((signal?: AbortSignal) => {
    fetchEveryOffer(signal)
      .then((held) => {
        if (signal?.aborted !== true) {
          setOffers(held)
          setLoadError(null)
        }
      })
      .catch((problem: Error) => {
        if (signal?.aborted !== true) {
          setLoadError(problem.message)
        }
      })
  }, [])

  useEffect(() => {
    const request = new AbortController()
    load(request.signal)
    return () => request.abort()
  }, [load])

  /**
   * Whatever the backend answered with is what the row becomes. The offer that comes back is the
   * catalogue as it now stands rather than what this page hoped it sent, which is the difference
   * between showing an edit and showing the edit that was actually made.
   */
  function nowReads(changed: AdministeredOffer) {
    setOffers((held) =>
      held === null ? held : held.map((was) => (was.code === changed.code ? changed : was)),
    )
    onCatalogueChanged()
  }

  function press(code: string, doIt: () => Promise<AdministeredOffer>) {
    setWorking(code)
    setRefusal(null)
    doIt()
      .then(nowReads)
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setWorking(null))
  }

  return (
    <section className="view">
      <p className="admin__unlocked" role="note">
        <WarningIcon />
        <span>
          Nothing here checks that you are staff. There is no sign-in, no role and no password
          anywhere in this application: anybody who can reach this page can reprice the catalogue.
          This is an administration screen and not authorisation.
        </span>
      </p>

      <WritingAnOffer
        onWritten={(written) => {
          setOffers((held) => (held === null ? [written] : [...held, written]))
          setRefusal(null)
          onCatalogueChanged()
        }}
      />

      {/* Revoking a voucher sits on this screen rather than on the counter's, because it is not
          something a person at a till may do: it moves points. It sits above the list of offers
          rather than beside one, because a voucher is addressed by the code printed on it and
          has nothing to do with which row of the catalogue anybody is looking at. The catalogue
          is told afterwards all the same — a cancellation puts the thing back in the window, so
          a sold-out card can unlock itself the moment one is revoked. */}
      <CancellingAVoucher onCancelled={onCatalogueChanged} />

      <div className="section-head">
        <h2>Every offer</h2>
        <button type="button" className="link link--back" onClick={onBack}>
          <BackIcon />
          Back to your money
        </button>
      </div>

      {loadError !== null && <Refusal reason={loadError} standing />}
      {offers === null && loadError === null && (
        <div className="card">
          <Waiting label="Loading the catalogue…" bars={['100%', '80%', '60%']} />
        </div>
      )}
      {refusal !== null && <Refusal reason={refusal} />}

      {offers !== null && (
        <ul className="offers">
          {offers.map((offer, place) => (
            <AnOfferToRun
              key={offer.code}
              offer={offer}
              place={place}
              working={working === offer.code}
              onChanged={nowReads}
              onPublish={() => press(offer.code, () => publishAnOffer(offer.code))}
              onWithdraw={() => press(offer.code, () => withdrawAnOffer(offer.code))}
              onRefused={setRefusal}
            />
          ))}
        </ul>
      )}
    </section>
  )
}

/**
 * The other back office: what the bank sells as savings, every version each product has published,
 * and the two things somebody running the bank can do — publish the next version of a product's
 * terms, and close a product to new accounts or put it back on sale.
 *
 * <p><strong>There is no way to edit a version and no way to delete anything, because the backend
 * has no door for either.</strong> That is the point of the whole screen rather than an omission
 * from it: a published set of terms is an agreement somebody's money is living under, so changing
 * what a product offers writes the next version and leaves every earlier one exactly as it was. A
 * button that asked to edit one would be a button that only ever got a refusal, and a page that
 * offered it would be teaching the wrong idea about what these rows are.
 *
 * <p><strong>Nothing here checks that you run the bank.</strong> There is no sign-in behind it, no
 * role and no password, and the backend endpoints it calls say the same thing in their own javadoc.
 * It is reached from a footer link rather than a tab for the reason the rewards back office is: it
 * belongs to somebody behind the bank rather than to the customer whose name is in the bar.
 *
 * <p><strong>It is drawn from the customer's own reads.</strong> The catalogue already serves closed
 * products marked closed and the history already serves every version including one dated for next
 * month, so there is nothing about a savings product an administrator sees that a customer does not
 * — and a second administration read returning an identical answer would be a second shape to keep
 * in agreement for no gain.
 *
 * <p>The versions fold open per product rather than being drawn down the page. Four products with
 * their whole histories open is a page nobody can read down, and the history is what somebody looks
 * at when they have decided which product they are here about.
 */
function RunningTheSavingsProducts({ onBack }: { onBack: () => void }) {
  const [products, setProducts] = useState<SavingsProduct[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  // A refusal to a press, kept apart from the one a failed read puts up: this one is about what
  // somebody just did, and blanking the list to show it would take the catalogue away from them in
  // order to tell them about one row of it.
  const [refusal, setRefusal] = useState<string | null>(null)
  // Which product is being worked on rather than whether one is, so the button that was pressed is
  // the one that shows it is working.
  const [working, setWorking] = useState<string | null>(null)

  const load = useCallback((signal?: AbortSignal) => {
    fetchSavingsProducts(signal)
      .then((shelf) => {
        if (signal?.aborted !== true) {
          setProducts(shelf)
          setLoadError(null)
        }
      })
      .catch((problem: Error) => {
        if (signal?.aborted !== true) {
          setLoadError(problem.message)
        }
      })
  }, [])

  useEffect(() => {
    const request = new AbortController()
    load(request.signal)
    return () => request.abort()
  }, [load])

  /**
   * Whatever the backend answered with is what the row becomes. The product that comes back is the
   * catalogue as it now stands rather than what this page hoped it sent, which is the difference
   * between showing a change and showing the change that was actually made.
   */
  function nowReads(changed: SavingsProduct) {
    setProducts((held) =>
      held === null ? held : held.map((was) => (was.code === changed.code ? changed : was)),
    )
  }

  function press(code: string, doIt: () => Promise<SavingsProduct>) {
    setWorking(code)
    setRefusal(null)
    doIt()
      .then(nowReads)
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setWorking(null))
  }

  return (
    <section className="view">
      <p className="admin__unlocked" role="note">
        <WarningIcon />
        <span>
          Nothing here checks that you are staff. There is no sign-in, no role and no password
          anywhere in this application: anybody who can reach this page can reprice a savings
          product. This is an administration screen and not authorisation.
        </span>
      </p>

      <div className="section-head">
        <h2>What the bank sells</h2>
        <button type="button" className="link link--back" onClick={onBack}>
          <BackIcon />
          Back to your money
        </button>
      </div>

      <p className="products__note">
        Changing what a product offers publishes the <em>next</em> version of its terms. Nothing
        already published is ever edited or removed, because accounts are living under it — so a
        rate cut is a new version from a day you choose, and everybody opened before that day
        carries on exactly as they were.
      </p>

      {loadError !== null && <Refusal reason={loadError} standing />}
      {products === null && loadError === null && (
        <div className="card">
          <Waiting label="Loading the savings products…" bars={['100%', '80%', '60%']} />
        </div>
      )}
      {refusal !== null && <Refusal reason={refusal} />}

      {products !== null && (
        <ul className="products">
          {products.map((product, place) => (
            <ASavingsProductToRun
              key={product.code}
              product={product}
              place={place}
              working={working === product.code}
              onPublished={() => load()}
              onClose={() => press(product.code, () => closeAProductToNewAccounts(product.code))}
              onReopen={() => press(product.code, () => reopenAProductToNewAccounts(product.code))}
              onRefused={setRefusal}
            />
          ))}
        </ul>
      )}
    </section>
  )
}

/**
 * One savings product in the back office: what it is, what it is offering today, whether its door
 * is open, and the two things that can be done to it.
 *
 * <p>Both the form and the history are folded away until they are asked for, and separately from
 * each other: publishing a version and reading what has already been published are two different
 * jobs, and a page that made them one would open a form nobody asked for every time somebody asked
 * a question.
 *
 * <p>The close and reopen buttons are one button showing whichever of the two applies, rather than
 * two with one of them always refused. There are exactly two states and the press that changes them
 * is the same press either way round.
 */
function ASavingsProductToRun({
  product,
  place,
  working,
  onPublished,
  onClose,
  onReopen,
  onRefused,
}: {
  product: SavingsProduct
  place: number
  working: boolean
  onPublished: () => void
  onClose: () => void
  onReopen: () => void
  onRefused: (reason: string) => void
}) {
  const [publishing, setPublishing] = useState(false)
  const [historyOpen, setHistoryOpen] = useState(false)

  return (
    <li
      className={`product${product.openToNewAccounts ? '' : ' product--closed'}`}
      style={rowDelay(place)}
    >
      <div className="product__head">
        <span className="product__state">
          {product.openToNewAccounts ? 'On sale' : 'Closed to new accounts'}
        </span>
        <h3 className="product__title">{product.name}</h3>
        <span className="product__rate">
          {asAPercentage(product.currentTerms.annualRatePercent)} a year
        </span>
      </div>
      <p className="product__code">
        {product.code} · {whatShapeOfAgreement(product.kind)} · version{' '}
        {product.currentTerms.version} since {asADay(product.currentTerms.effectiveFrom)}
      </p>
      <p className="product__words">{product.description}</p>

      <TheFiguresOfAVersion terms={product.currentTerms} />

      <div className="product__doing">
        <Button tone="ghost" small onClick={() => setPublishing((was) => !was)}>
          {publishing ? 'Never mind' : 'Publish a new version'}
        </Button>
        <Button tone="ghost" small onClick={() => setHistoryOpen((was) => !was)}>
          {historyOpen ? 'Hide the versions' : 'Every version'}
        </Button>
        {/* One button for the one flag, showing whichever half of it applies. Closing stops the
            next account and disturbs nothing already on the product, which is the sentence under
            the button rather than in a dialogue nobody reads. */}
        <Button
          tone="ghost"
          small
          busy={working}
          disabled={working}
          onClick={product.openToNewAccounts ? onClose : onReopen}
        >
          {product.openToNewAccounts ? 'Close to new accounts' : 'Put back on sale'}
        </Button>
      </div>

      {publishing && (
        <PublishingAVersion
          product={product}
          onPublished={() => {
            setPublishing(false)
            setHistoryOpen(true)
            onPublished()
          }}
          onRefused={onRefused}
        />
      )}

      {historyOpen && <TheVersionsOfAProduct code={product.code} />}
    </li>
  )
}

/**
 * The figures a set of terms carries, as a small table under the row.
 *
 * <p>Every figure, including the ones that are nothing, because zero is the absence of the rule and
 * the whole point of showing these side by side is that an administrator can see which rules a
 * product has. A table that left out the absent ones would make two products with different shapes
 * look like two products with different numbers of facts.
 */
function TheFiguresOfAVersion({ terms }: { terms: TermsVersion }) {
  return (
    <dl className="terms">
      <div>
        <dt>Bonus rate</dt>
        <dd>{asAPercentage(terms.bonusRatePercent)}</dd>
      </div>
      <div>
        <dt>Notice</dt>
        <dd>{terms.noticeDays === 0 ? 'None' : `${terms.noticeDays} days`}</dd>
      </div>
      <div>
        <dt>Term</dt>
        <dd>{terms.termMonths === 0 ? 'None' : `${terms.termMonths} months`}</dd>
      </div>
      <div>
        <dt>Keep at least</dt>
        <dd>{terms.minimumBalance === 0 ? 'Nothing' : euros.format(terms.minimumBalance)}</dd>
      </div>
      <div>
        <dt>Breaking the term</dt>
        <dd>
          {terms.earlyExitPenaltyDays === 0
            ? 'Nothing to pay'
            : `${terms.earlyExitPenaltyDays} days of interest`}
        </dd>
      </div>
      <div>
        <dt>Points per euro</dt>
        <dd>×{rate.format(terms.pointsMultiplier)}</dd>
      </div>
      <div>
        <dt>Anniversary</dt>
        <dd>{asAPercentage(terms.anniversaryRatePercent)}</dd>
      </div>
      <div>
        <dt>At maturity</dt>
        <dd>{whatHappensAtMaturity(terms.maturityAction)}</dd>
      </div>
    </dl>
  )
}

/**
 * The form that publishes the next version of a product's terms.
 *
 * <p><strong>It opens filled in with the version on offer today.</strong> That is a decision worth
 * stating: a version carries nothing over from the one before it — every figure is published
 * outright, and the backend refuses an empty box by name — so an administrator changing one rate
 * would otherwise have to retype ten figures they were not changing. Pre-filling makes the usual
 * case one box and one press, and what goes up is still the whole agreement, visibly, in boxes
 * somebody can read before they send it.
 *
 * <p><strong>The day is required and is not defaulted to today.</strong> This bank announces rate
 * changes ahead of time and backdates corrections, and both are ordinary, so a page that filled the
 * date in would be choosing the day an agreement starts on behalf of the person publishing it.
 *
 * <p><strong>Nothing here is validated.</strong> Whether a rate may be quoted to three places,
 * whether nought is a thing the points multiplier may be, and whether a version may take effect on
 * the day named are the backend's rules, and each comes back as a sentence shown unchanged. A page
 * that checked first would be a second copy of the rules, and the second copy is always the one
 * that is out of date.
 */
function PublishingAVersion({
  product,
  onPublished,
  onRefused,
}: {
  product: SavingsProduct
  onPublished: () => void
  onRefused: (reason: string) => void
}) {
  const now = product.currentTerms
  const [effectiveFrom, setEffectiveFrom] = useState('')
  const [annualRatePercent, setAnnualRatePercent] = useState(String(now.annualRatePercent))
  const [bonusRatePercent, setBonusRatePercent] = useState(String(now.bonusRatePercent))
  const [noticeDays, setNoticeDays] = useState(String(now.noticeDays))
  const [termMonths, setTermMonths] = useState(String(now.termMonths))
  const [minimumBalance, setMinimumBalance] = useState(String(now.minimumBalance))
  const [earlyExitPenaltyDays, setEarlyExitPenaltyDays] = useState(
    String(now.earlyExitPenaltyDays),
  )
  const [pointsMultiplier, setPointsMultiplier] = useState(String(now.pointsMultiplier))
  const [anniversaryRatePercent, setAnniversaryRatePercent] = useState(
    String(now.anniversaryRatePercent),
  )
  const [maturityAction, setMaturityAction] = useState(now.maturityAction)
  const [whatChanged, setWhatChanged] = useState('')
  const [writing, setWriting] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  const box = (field: string) => `${product.code}-${field}`

  function publish(submitted: FormEvent) {
    submitted.preventDefault()
    setWriting(true)
    setRefusal(null)
    const version: ANewVersionOfTerms = {
      effectiveFrom,
      annualRatePercent,
      bonusRatePercent,
      noticeDays,
      termMonths,
      minimumBalance,
      earlyExitPenaltyDays,
      pointsMultiplier,
      anniversaryRatePercent,
      maturityAction,
      whatChanged,
    }
    publishANewVersion(product.code, version)
      .then(() => onPublished())
      .catch((problem: Error) => {
        setRefusal(problem.message)
        onRefused(problem.message)
      })
      .finally(() => setWriting(false))
  }

  return (
    <div className="product__form">
      <form className="admin-form" onSubmit={publish}>
        <div className="field">
          <label htmlFor={box('effective')}>Takes effect on</label>
          <input
            className="text-input"
            id={box('effective')}
            type="date"
            value={effectiveFrom}
            onChange={(typed) => setEffectiveFrom(typed.target.value)}
          />
          <p className="field__note">
            A day in the past backdates the change; a day still to come publishes it now and starts
            selling it that morning. Everybody opened before it carries on as they were.
          </p>
        </div>
        <div className="field">
          <label htmlFor={box('rate')}>Annual rate, as a percentage</label>
          <input
            className="text-input"
            id={box('rate')}
            inputMode="decimal"
            value={annualRatePercent}
            onChange={(typed) => setAnnualRatePercent(typed.target.value)}
            placeholder="0.50"
          />
          <p className="field__note">0.50 means 0.50% a year. At most two decimal places.</p>
        </div>
        <div className="field">
          <label htmlFor={box('bonus')}>Bonus rate on top</label>
          <input
            className="text-input"
            id={box('bonus')}
            inputMode="decimal"
            value={bonusRatePercent}
            onChange={(typed) => setBonusRatePercent(typed.target.value)}
            placeholder="0.00"
          />
          <p className="field__note">0.00 for a product with no condition to keep.</p>
        </div>
        <div className="field">
          <label htmlFor={box('notice')}>Notice, in days</label>
          <input
            className="text-input"
            id={box('notice')}
            inputMode="numeric"
            value={noticeDays}
            onChange={(typed) => setNoticeDays(typed.target.value)}
            placeholder="0"
          />
          <p className="field__note">0 when money may leave the same day.</p>
        </div>
        <div className="field">
          <label htmlFor={box('term')}>Term, in months</label>
          <input
            className="text-input"
            id={box('term')}
            inputMode="numeric"
            value={termMonths}
            onChange={(typed) => setTermMonths(typed.target.value)}
            placeholder="0"
          />
          <p className="field__note">0 when it is not a term account.</p>
        </div>
        <div className="field">
          <label htmlFor={box('floor')}>Minimum balance, in euros</label>
          <input
            className="text-input"
            id={box('floor')}
            inputMode="decimal"
            value={minimumBalance}
            onChange={(typed) => setMinimumBalance(typed.target.value)}
            placeholder="0.00"
          />
          <p className="field__note">0.00 when there is no floor to keep.</p>
        </div>
        <div className="field">
          <label htmlFor={box('penalty')}>Early exit, in days of interest</label>
          <input
            className="text-input"
            id={box('penalty')}
            inputMode="numeric"
            value={earlyExitPenaltyDays}
            onChange={(typed) => setEarlyExitPenaltyDays(typed.target.value)}
            placeholder="0"
          />
          <p className="field__note">0 when there is nothing to pay for leaving.</p>
        </div>
        <div className="field">
          <label htmlFor={box('multiplier')}>Points per euro, as a multiple</label>
          <input
            className="text-input"
            id={box('multiplier')}
            inputMode="decimal"
            value={pointsMultiplier}
            onChange={(typed) => setPointsMultiplier(typed.target.value)}
            placeholder="1.0000"
          />
          <p className="field__note">
            1.0000 changes nothing and 1.2500 is a quarter more. This one cannot be nothing — a
            multiple of nought is a deposit that earns no points at all.
          </p>
        </div>
        <div className="field">
          <label htmlFor={box('anniversary')}>Anniversary rate, as a percentage</label>
          <input
            className="text-input"
            id={box('anniversary')}
            inputMode="decimal"
            value={anniversaryRatePercent}
            onChange={(typed) => setAnniversaryRatePercent(typed.target.value)}
            placeholder="10.00"
          />
          <p className="field__note">
            What an anniversary pays on money still sitting there. 10.00 is the tenth this
            application has always paid.
          </p>
        </div>
        <div className="field">
          <label htmlFor={box('maturity')}>When a term is up</label>
          <select
            className="text-input"
            id={box('maturity')}
            value={maturityAction}
            onChange={(typed) => setMaturityAction(typed.target.value)}
          >
            {/* The three the backend offers, by the words that actually go over the wire. A
                fourth would arrive as a refusal listing what there is, which is the honest
                failure for a list this page cannot be told about. */}
            <option value="HOLD">Hold it where it is</option>
            <option value="ROLL_OVER">Roll it into another term at that day&apos;s rates</option>
            <option value="MOVE_TO_INSTANT">Move it to instant access</option>
          </select>
          <p className="field__note">
            Hold is the one that does nothing, for a product that never reaches a maturity.
          </p>
        </div>
        <div className="field">
          <label htmlFor={box('changed')}>What changed, and why</label>
          <textarea
            className="text-input"
            id={box('changed')}
            rows={2}
            value={whatChanged}
            onChange={(typed) => setWhatChanged(typed.target.value)}
            placeholder="Rate cut from 0.60% to 0.50% a year in line with the market."
          />
          <p className="field__note">
            Required. A customer comparing this version with the one they are on reads this rather
            than working out the difference themselves.
          </p>
        </div>
        {refusal !== null && <Refusal reason={refusal} />}
        <Button type="submit" busy={writing} disabled={writing}>
          Publish version {product.currentTerms.version + 1}
        </Button>
      </form>
    </div>
  )
}

/**
 * Every version a product has published, oldest first, each with the line saying what changed.
 *
 * <p>Asked for a row at a time rather than loaded with the catalogue, for the reason the rewards
 * waiting list is: four products loaded with their histories would be four requests answering a
 * question nobody on this screen has asked yet.
 *
 * <p>Oldest first because it reads as a story: what the product said at the start, and what each
 * change did to it. The first version says nothing changed, because nothing did — that is what a
 * first version is — and nothing is drawn for it.
 */
function TheVersionsOfAProduct({ code }: { code: string }) {
  const [versions, setVersions] = useState<TermsVersion[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    const request = new AbortController()
    fetchTheVersionsOf(code, request.signal)
      .then((published) => {
        if (!request.signal.aborted) {
          setVersions(published)
          setLoadError(null)
        }
      })
      .catch((problem: Error) => {
        if (!request.signal.aborted) {
          setLoadError(problem.message)
        }
      })
    return () => request.abort()
  }, [code])

  return (
    <div className="product__versions">
      {loadError !== null && <Refusal reason={loadError} />}
      {versions === null && loadError === null && (
        <Waiting label="Loading the versions…" bars={['70%', '90%']} />
      )}
      {versions !== null && (
        <ol className="versions">
          {versions.map((version) => (
            <li className="version" key={version.version}>
              <p className="version__head">
                <span className="version__number">Version {version.version}</span>
                <span className="version__from">from {asADay(version.effectiveFrom)}</span>
                <span className="version__rate">
                  {asAPercentage(version.annualRatePercent)} a year
                </span>
              </p>
              {version.whatChanged !== null && (
                <p className="version__changed">{version.whatChanged}</p>
              )}
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

/**
 * A rate as a percentage somebody reads. The backend sends the figure on the poster — 0.6 is 0.6% a
 * year — so this only puts the sign on it and keeps two places, so that a column of rates lines up
 * however the JSON happened to write each one.
 */
const percentage = new Intl.NumberFormat('nl-BE', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

function asAPercentage(figure: number): string {
  return `${percentage.format(figure)}%`
}

/** The shape of agreement, in words, because the backend sends the closed set as its own name. */
function whatShapeOfAgreement(kind: string): string {
  switch (kind) {
    case 'INSTANT_ACCESS':
      return 'Instant access'
    case 'NOTICE':
      return 'Notice account'
    case 'FIXED_TERM':
      return 'Fixed term'
    case 'MINIMUM_BALANCE':
      return 'Minimum balance'
    default:
      // A kind this page has not been told about, shown as the backend named it rather than hidden.
      // A row that said nothing about its own shape would be worse than one saying a word nobody
      // has translated yet.
      return kind
  }
}

/** The same, for what a set of terms says happens on the day a term is up. */
function whatHappensAtMaturity(action: string): string {
  switch (action) {
    case 'ROLL_OVER':
      return 'Rolls into another term'
    case 'MOVE_TO_INSTANT':
      return 'Moves to instant access'
    case 'HOLD':
      return 'Held where it is'
    default:
      return action
  }
}

/* ----------------------------------------------------------------- running the scheme

   The third back office. Its furniture is deliberately the two beside it — the same unlocked
   warning at the top, the same `.card`, `.terms`, `.versions` and `.admin-form`, the same back
   link in the same place — because three administration screens that looked like three
   applications would be three things to learn for one job. What is new here is new because the
   thing is new: a ladder drawn as it is typed, a comparison panel, and a Publish button that is
   grey until somebody has looked at what they are about to do. */

/**
 * The scheme as whoever reprices it sees it: what is true today, a form pre-filled from it, what a
 * candidate would have done, the door that publishes one, and everything that has been published.
 *
 * <p><strong>Nothing here checks that you run the bank, and the screen says so out loud.</strong>
 * There is no token, no role and no session anywhere in this application, and the backend does not
 * check that whoever is asking runs the scheme — it cannot, because there is nothing to check
 * against. The two administration screens beside this one are honest about the same thing in the
 * same words, and it matters more here than on either of them: this is the one form in the
 * application whose single press moves what every customer's week is worth.
 *
 * <p><strong>It is drawn entirely from the customer's own reads.</strong> The version in force and
 * the whole published history are served to anybody who asks, including a version announced for a
 * Monday still to come, so there is nothing about the scheme an administrator sees that a customer
 * could not — and the backend deliberately has no administration read, because a second endpoint
 * answering the same question would be a second shape to keep in agreement with the first. That is
 * the argument the savings products back office already makes, and the scheme is more public than a
 * product's terms rather than less: it is what the overview's "1,30× per euro" is explained *by*.
 *
 * <p><strong>Two doors, one form, and the rule that joins them lives here.</strong> Preview and
 * publish are independent endpoints taking byte-for-byte the same body; the backend will happily
 * publish a candidate nobody previewed, and it is right to — remembering what it had been asked
 * about first is the draft table the module spent a ticket not building. So "you may not publish
 * what you have not looked at" is a rule of this screen, and {@link RepricingTheScheme} enforces it
 * by comparing the body that was previewed with the body that would now be sent, rather than by a
 * flag a later keystroke could forget to clear.
 *
 * <p><strong>The loyalty table at the foot is read-only on purpose.</strong> What a product pays on
 * an anniversary and what it multiplies points by are figures of that product's terms, published
 * from the other back office, and this screen shows them so that the whole priced surface can be
 * read in one place — and shows them without a box to type in so that no figure in this application
 * acquires a second place it can be changed from. The link beside them goes to the door that does
 * change them.
 */
function RunningTheScheme({
  onBack,
  onOpenTheProducts,
  onSchemeChanged,
}: {
  onBack: () => void
  onOpenTheProducts: () => void
  onSchemeChanged: () => void
}) {
  const [inForce, setInForce] = useState<TheSchemeAsPublished | null>(null)
  const [inForceError, setInForceError] = useState<string | null>(null)
  const [everyVersion, setEveryVersion] = useState<TheSchemeAsPublished[] | null>(null)
  const [historyError, setHistoryError] = useState<string | null>(null)

  /**
   * Both reads, together, because the screen is about both at once: the panel at the top and the
   * form under it are the version in force, and the list at the bottom is the history — and which
   * rows of that history have started is answered by comparing them with the first. Two reads that
   * arrived at different moments would let the list disagree with the panel above it for as long as
   * one of them was in flight.
   */
  const load = useCallback((signal?: AbortSignal) => {
    fetchTheSchemeInForce(signal)
      .then((scheme) => {
        if (signal?.aborted !== true) {
          setInForce(scheme)
          setInForceError(null)
        }
      })
      .catch((problem: Error) => {
        if (signal?.aborted !== true) {
          setInForceError(problem.message)
        }
      })
    fetchEveryVersionOfTheScheme(signal)
      .then((published) => {
        if (signal?.aborted !== true) {
          setEveryVersion(published)
          setHistoryError(null)
        }
      })
      .catch((problem: Error) => {
        if (signal?.aborted !== true) {
          setHistoryError(problem.message)
        }
      })
  }, [])

  useEffect(() => {
    const request = new AbortController()
    load(request.signal)
    return () => request.abort()
  }, [load])

  return (
    <section className="view">
      <p className="admin__unlocked" role="note">
        <WarningIcon />
        <span>
          Nothing here checks that you are staff. There is no sign-in, no role and no password
          anywhere in this application: anybody who can reach this page can reprice the scheme for
          every customer the bank has. This is an administration screen and not authorisation.
        </span>
      </p>

      <div className="section-head">
        <h2>The scheme in force</h2>
        <button type="button" className="link link--back" onClick={onBack}>
          <BackIcon />
          Back to your money
        </button>
      </div>

      {inForceError !== null && <Refusal reason={inForceError} standing />}
      {inForce === null && inForceError === null && (
        <div className="card">
          <Waiting label="Loading the scheme…" bars={['100%', '80%', '60%']} />
        </div>
      )}

      {inForce !== null && (
        <>
          <TheSchemeAsItStands scheme={inForce} />
          {/* Remounted when the version in force changes, so the boxes refill from whatever is now
              true rather than keeping the figures of a scheme that has since been superseded. It
              almost never fires: a version may only take effect on a Monday still to come, so
              publishing one leaves the version in force exactly where it was and the form keeps
              whatever is in it. What it covers is the tab left open across a Monday morning. */}
          <RepricingTheScheme
            key={inForce.version}
            inForce={inForce}
            onPublished={() => {
              load()
              onSchemeChanged()
            }}
          />
        </>
      )}

      <ThePublishedVersions versions={everyVersion} inForce={inForce} loadError={historyError} />

      <LoyaltyByProduct onOpenTheProducts={onOpenTheProducts} />
    </section>
  )
}

/**
 * The version in force today, figure by figure, with the line saying what changed.
 *
 * <p>Every figure, in the four groups the form is in, so that reading what is true and changing it
 * are the same shape twice: somebody who has found the weekly threshold in the panel knows which
 * box it is in without hunting. That is the same argument the savings products back office makes
 * for showing a version's whole table above the form that publishes the next one.
 *
 * <p>Nothing here is worked out. Every figure below is the backend's own, in the units it sends
 * them in — euros, multiples, a percentage, counts of months and days — and the only thing this
 * function does to any of them is put a currency sign, a cross or a noun beside it.
 */
function TheSchemeAsItStands({ scheme }: { scheme: TheSchemeAsPublished }) {
  return (
    <article className="card reveal scheme-now">
      <div className="scheme-now__head">
        <h3 className="card__title">Version {scheme.version}</h3>
        <span className="scheme-now__from">in force since {asADay(scheme.effectiveFrom)}</span>
      </div>
      <p className="scheme-now__changed">{scheme.whatChanged}</p>
      <TheFiguresOfAScheme scheme={scheme} />
    </article>
  )
}

/**
 * The thirteen figures of a version as a table, in the order the form asks for them.
 *
 * <p>Its own component because it is drawn twice — for the version in force at the top of the
 * screen, and for whichever published version somebody unfolds at the bottom — and a second copy of
 * this list would be the one that forgot the figure somebody added to the scheme last week.
 */
function TheFiguresOfAScheme({ scheme }: { scheme: TheSchemeAsPublished }) {
  return (
    <dl className="terms">
      <div>
        <dt>A week asks for</dt>
        <dd>{euros.format(scheme.weeklyThreshold)}</dd>
      </div>
      <div>
        <dt>The ordinary rate</dt>
        <dd>×{schemeRate.format(scheme.theOrdinaryRate)}</dd>
      </div>
      <div>
        <dt>Each further week adds</dt>
        <dd>×{schemeRate.format(scheme.extraForEachFurtherWeek)}</dd>
      </div>
      <div>
        <dt>The most a streak pays</dt>
        <dd>×{schemeRate.format(scheme.theMostAStreakPays)}</dd>
      </div>
      <div>
        <dt>A batch of points lasts</dt>
        <dd>{inMonths(scheme.howLongABatchOfPointsLasts)}</dd>
      </div>
      <div>
        <dt>Balance rungs</dt>
        <dd>{scheme.balanceRungs.map((rung) => euros.format(rung)).join(' · ')}</dd>
      </div>
      <div>
        <dt>A budget is running low at</dt>
        <dd>{asAPercentage(scheme.whatShareOfABudgetIsRunningLow)}</dd>
      </div>
      <div>
        <dt>Arrears are a spiral at</dt>
        <dd>{inBills(scheme.howManyOutstandingIsASpiral)}</dd>
      </div>
      <div>
        <dt>A maturity is worth saying</dt>
        <dd>{inDays(scheme.daysBeforeAMaturityIsWorthSaying)} ahead</dd>
      </div>
      <div>
        <dt>An anniversary is worth saying</dt>
        <dd>{inDays(scheme.daysBeforeAnAnniversaryIsWorthSaying)} ahead</dd>
      </div>
    </dl>
  )
}

/**
 * The form that publishes the next version of the scheme, the preview it insists on first, and the
 * comparison that comes back.
 *
 * <p><strong>It opens filled in with the version in force.</strong> A version of the scheme carries
 * nothing over from the one before it — every figure is published outright and the backend refuses
 * an empty box by name — so an administrator moving one rate would otherwise retype eleven figures
 * they were not changing, and a retyped figure is a figure that can be retyped wrong. Pre-filling
 * makes the usual case one box and two presses, and what goes up is still the whole scheme,
 * visibly, in boxes somebody can read before they send it.
 *
 * <p><strong>The Monday is required and is deliberately not filled in.</strong> It is the most
 * consequential thing on this form — the day every customer's week starts being judged differently
 * — and a page that pre-filled it would be choosing that day on behalf of the person publishing.
 * The backend refuses an empty one by name, and refuses a date that is not a Monday, or is not
 * still to come, or would step in front of a version already announced, each in its own sentence.
 *
 * <p><strong>Grouped by what the figures do, not by what type they are.</strong> The week, the
 * ladder, points, telling people. A table of twelve boxes in the order the JSON happens to list
 * them would be a row of a database on a screen; these four groups are the four things the scheme
 * decides, and somebody who came here to make the bank more generous knows which group they are
 * looking for before they know which figure.
 *
 * <p><strong>Nothing here is validated.</strong> Whether a rate may be quoted to five places,
 * whether nought is a thing the ordinary rate may be, whether the rungs climb, whether the ladder
 * descends and whether the Monday named is one this scheme may start on are all the backend's
 * rules, and each comes back as a sentence shown unchanged. A page that checked first would be a
 * second copy of the rules, and the second copy is always the one that is out of date.
 *
 * <p><strong>And this is where Publish is locked.</strong> See {@link theSameCandidate}.
 */
function RepricingTheScheme({
  inForce,
  onPublished,
}: {
  inForce: TheSchemeAsPublished
  onPublished: () => void
}) {
  const [effectiveFrom, setEffectiveFrom] = useState('')
  const [weeklyThreshold, setWeeklyThreshold] = useState(asTyped(inForce.weeklyThreshold))
  const [theOrdinaryRate, setTheOrdinaryRate] = useState(asTyped(inForce.theOrdinaryRate))
  const [extraForEachFurtherWeek, setExtraForEachFurtherWeek] = useState(
    asTyped(inForce.extraForEachFurtherWeek),
  )
  const [theMostAStreakPays, setTheMostAStreakPays] = useState(asTyped(inForce.theMostAStreakPays))
  const [howLongABatchOfPointsLasts, setHowLongABatchOfPointsLasts] = useState(
    String(inForce.howLongABatchOfPointsLasts),
  )
  const [balanceRungs, setBalanceRungs] = useState<string[]>(
    inForce.balanceRungs.map((rung) => asTyped(rung)),
  )
  const [whatShareOfABudgetIsRunningLow, setWhatShareOfABudgetIsRunningLow] = useState(
    asTyped(inForce.whatShareOfABudgetIsRunningLow),
  )
  const [howManyOutstandingIsASpiral, setHowManyOutstandingIsASpiral] = useState(
    String(inForce.howManyOutstandingIsASpiral),
  )
  const [daysBeforeAMaturityIsWorthSaying, setDaysBeforeAMaturityIsWorthSaying] = useState(
    String(inForce.daysBeforeAMaturityIsWorthSaying),
  )
  const [daysBeforeAnAnniversaryIsWorthSaying, setDaysBeforeAnAnniversaryIsWorthSaying] = useState(
    String(inForce.daysBeforeAnAnniversaryIsWorthSaying),
  )
  const [whatChanged, setWhatChanged] = useState('')

  // What came back from the preview, and the exact body it was taken of. The second of those is the
  // Publish button's whole lock, and it is a copy of the form rather than a boolean about it.
  const [comparison, setComparison] = useState<WhatThisSchemeWouldDo | null>(null)
  const [whatWasPreviewed, setWhatWasPreviewed] = useState<ACandidateVersionOfTheScheme | null>(
    null,
  )
  const [refusal, setRefusal] = useState<string | null>(null)
  const [saidAfterPublishing, setSaidAfterPublishing] = useState<string | null>(null)
  const [previewing, setPreviewing] = useState(false)
  const [publishing, setPublishing] = useState(false)

  // The body as the form now stands, rebuilt on every render because that is what it is: not state,
  // but a reading of the twelve boxes at this moment. Both doors are pointed at this one value, so
  // the candidate a preview is taken of is the candidate a publish would write, character for
  // character, including which boxes are empty.
  const asItStands: ACandidateVersionOfTheScheme = {
    effectiveFrom,
    weeklyThreshold,
    theOrdinaryRate,
    extraForEachFurtherWeek,
    theMostAStreakPays,
    howLongABatchOfPointsLasts,
    balanceRungs,
    whatShareOfABudgetIsRunningLow,
    howManyOutstandingIsASpiral,
    daysBeforeAMaturityIsWorthSaying,
    daysBeforeAnAnniversaryIsWorthSaying,
    whatChanged,
  }

  const lookedAt = whatWasPreviewed !== null && theSameCandidate(whatWasPreviewed, asItStands)

  /**
   * Every change to every box comes through here, and all it does beyond setting the value is take
   * down the confirmation of the last publish: that sentence is about a version that now exists and
   * has nothing to do with what is being typed next.
   *
   * <p><strong>It deliberately does not clear the comparison, and this is the whole reason the rule
   * is written the way it is.</strong> The panel below is shown on exactly the condition the Publish
   * button is enabled on — `lookedAt`, which asks whether the body that was previewed is the body
   * that would now be sent — so a keystroke takes both of them away at once and typing the figure
   * back again brings both of them back. One condition, asked in one place, of the whole form.
   * Clearing a flag from every handler would be the same rule written thirteen times, and the
   * fourteenth box added to this form next year is the one whose handler forgets.
   */
  function typed<Held>(set: (value: Held) => void): (value: Held) => void {
    return (value) => {
      set(value)
      setSaidAfterPublishing(null)
    }
  }

  function preview(submitted: FormEvent) {
    submitted.preventDefault()
    setPreviewing(true)
    setRefusal(null)
    setSaidAfterPublishing(null)
    // Held before the request goes out rather than after it comes back, so that the body compared
    // against is the one that was actually sent. A form edited while the answer was in flight would
    // otherwise light Publish for a scheme nobody had previewed.
    const asked = asItStands
    previewAVersionOfTheScheme(asked)
      .then((wouldDo) => {
        setComparison(wouldDo)
        setWhatWasPreviewed(asked)
      })
      .catch((problem: Error) => {
        setRefusal(problem.message)
        setComparison(null)
        setWhatWasPreviewed(null)
      })
      .finally(() => setPreviewing(false))
  }

  function publish() {
    setPublishing(true)
    setRefusal(null)
    publishAVersionOfTheScheme(asItStands)
      .then((now) => {
        setSaidAfterPublishing(
          now.itsDayHasCome
            ? `Version ${now.published.version} is published and in force from today.`
            : `Version ${now.published.version} is published and takes effect on ` +
              `${asADay(now.published.effectiveFrom)}. Nothing changes for anybody until then.`,
        )
        // The candidate has been written, so the preview that unlocked the button is spent. Without
        // this, a second press would publish the identical scheme a second time — which the backend
        // allows, because a version dated the same Monday as one already announced is how an
        // announcement is corrected — and the history would carry a version that said nothing.
        setComparison(null)
        setWhatWasPreviewed(null)
        onPublished()
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setPublishing(false))
  }

  return (
    <article className="card reveal scheme-form">
      <h3 className="card__title">Publish the next version</h3>
      <p className="scheme-form__note">
        Every figure is published outright and nothing is carried over, so the boxes open filled in
        with the version in force. Change what you are changing, leave the rest, and look at what it
        would do before you publish it.
      </p>

      <form className="admin-form" onSubmit={preview}>
        <fieldset className="scheme-group">
          <legend>The week</legend>
          <div className="field">
            <label htmlFor="schemeWeeklyThreshold">What a week asks for, in euros</label>
            <input
              className="text-input"
              id="schemeWeeklyThreshold"
              inputMode="decimal"
              value={weeklyThreshold}
              onChange={(box) => typed(setWeeklyThreshold)(box.target.value)}
              placeholder="50.00"
            />
            <p className="field__note">
              Put this much away between one Monday and the next and the week is secured, which is
              what a run of weeks is made of.
            </p>
          </div>
        </fieldset>

        <fieldset className="scheme-group">
          <legend>The ladder</legend>
          <div className="field">
            <label htmlFor="schemeOrdinaryRate">The ordinary rate, as a multiple</label>
            <input
              className="text-input"
              id="schemeOrdinaryRate"
              inputMode="decimal"
              value={theOrdinaryRate}
              onChange={(box) => typed(setTheOrdinaryRate)(box.target.value)}
              placeholder="1.0000"
            />
            <p className="field__note">
              What a euro earns outside any run, and in the first week of one. It cannot be nothing
              — a multiple of nought is a deposit that earns no points at all.
            </p>
          </div>
          <div className="field">
            <label htmlFor="schemeStep">What each further week adds</label>
            <input
              className="text-input"
              id="schemeStep"
              inputMode="decimal"
              value={extraForEachFurtherWeek}
              onChange={(box) => typed(setExtraForEachFurtherWeek)(box.target.value)}
              placeholder="0.1000"
            />
            <p className="field__note">0.0000 is a scheme that pays the same however long the run.</p>
          </div>
          <div className="field">
            <label htmlFor="schemeCap">The most a streak pays</label>
            <input
              className="text-input"
              id="schemeCap"
              inputMode="decimal"
              value={theMostAStreakPays}
              onChange={(box) => typed(setTheMostAStreakPays)(box.target.value)}
              placeholder="1.5000"
            />
            <p className="field__note">
              Where the climb stops. It may equal the ordinary rate — that is a flat ladder — but it
              may not fall below it.
            </p>
          </div>

          <WhatThisLadderWouldPay
            theOrdinaryRate={theOrdinaryRate}
            extraForEachFurtherWeek={extraForEachFurtherWeek}
            theMostAStreakPays={theMostAStreakPays}
          />
        </fieldset>

        <fieldset className="scheme-group">
          <legend>Points</legend>
          <div className="field">
            <label htmlFor="schemePointsLifetime">How long a batch of points lasts, in months</label>
            <input
              className="text-input"
              id="schemePointsLifetime"
              inputMode="numeric"
              value={howLongABatchOfPointsLasts}
              onChange={(box) => typed(setHowLongABatchOfPointsLasts)(box.target.value)}
              placeholder="12"
            />
            <p className="field__note">
              Every batch already earned keeps the lifetime it was promised, written on the batch
              itself. This is the lifetime batches earned from the Monday below will carry.
            </p>
          </div>
        </fieldset>

        <fieldset className="scheme-group">
          <legend>Telling people</legend>
          <TheBalanceRungs rungs={balanceRungs} onChanged={typed(setBalanceRungs)} />
          <div className="field">
            <label htmlFor="schemeRunningLow">A budget is running low at, as a percentage</label>
            <input
              className="text-input"
              id="schemeRunningLow"
              inputMode="decimal"
              value={whatShareOfABudgetIsRunningLow}
              onChange={(box) => typed(setWhatShareOfABudgetIsRunningLow)(box.target.value)}
              placeholder="80.00"
            />
            <p className="field__note">
              80.00 means four fifths of a month&apos;s budget spent. A percentage between 1 and
              100, and not a fraction.
            </p>
          </div>
          <div className="field">
            <label htmlFor="schemeSpiral">How many outstanding is a spiral</label>
            <input
              className="text-input"
              id="schemeSpiral"
              inputMode="numeric"
              value={howManyOutstandingIsASpiral}
              onChange={(box) => typed(setHowManyOutstandingIsASpiral)(box.target.value)}
              placeholder="3"
            />
            <p className="field__note">Unpaid bill dates on one current account, counted together.</p>
          </div>
          <div className="field">
            <label htmlFor="schemeMaturityDays">Days before a maturity is worth saying</label>
            <input
              className="text-input"
              id="schemeMaturityDays"
              inputMode="numeric"
              value={daysBeforeAMaturityIsWorthSaying}
              onChange={(box) => typed(setDaysBeforeAMaturityIsWorthSaying)(box.target.value)}
              placeholder="30"
            />
          </div>
          <div className="field">
            <label htmlFor="schemeAnniversaryDays">Days before an anniversary is worth saying</label>
            <input
              className="text-input"
              id="schemeAnniversaryDays"
              inputMode="numeric"
              value={daysBeforeAnAnniversaryIsWorthSaying}
              onChange={(box) => typed(setDaysBeforeAnAnniversaryIsWorthSaying)(box.target.value)}
              placeholder="30"
            />
          </div>
        </fieldset>

        <div className="field">
          <label htmlFor="schemeEffectiveFrom">Takes effect on</label>
          <input
            className="text-input"
            id="schemeEffectiveFrom"
            type="date"
            value={effectiveFrom}
            onChange={(box) => typed(setEffectiveFrom)(box.target.value)}
          />
          <p className="field__note">
            A Monday still to come, and nothing else. A week is judged by the scheme in force on its
            own Monday, so a version dated in the past would change which weeks had counted — which
            is the one thing versioning the scheme exists to prevent.
          </p>
        </div>

        <div className="field">
          <label htmlFor="schemeWhatChanged">What changed, and why</label>
          <textarea
            className="text-input"
            id="schemeWhatChanged"
            rows={2}
            value={whatChanged}
            onChange={(box) => typed(setWhatChanged)(box.target.value)}
            placeholder="The weekly threshold rises from €50 to €80 as part of the spring campaign."
          />
          <p className="field__note">
            Required. Nobody opted into the scheme and it applies to everybody from its Monday, so a
            repricing with a date and no explanation is exactly what this line is here to stop.
          </p>
        </div>

        {refusal !== null && <Refusal reason={refusal} />}

        <div className="scheme-form__doing">
          <Button type="submit" tone="ghost" busy={previewing} disabled={previewing || publishing}>
            {comparison === null ? 'Preview it' : 'Preview it again'}
          </Button>
          {/* Not a submit, and not inside the form's own press either: the form's press is the
              preview, because previewing is what this form is for and the one a stray Enter in a
              text box should reach. Publishing is a second, deliberate press on a button that is
              grey until the thing below it is a picture of exactly these figures. */}
          <Button
            type="button"
            busy={publishing}
            disabled={!lookedAt || publishing || previewing}
            onClick={publish}
          >
            Publish it
          </Button>
        </div>

        <p className="scheme-form__lock" role="status">
          {lookedAt
            ? 'Publishing will write exactly the figures you have just previewed.'
            : 'Preview these figures to unlock publishing. Changing any box locks it again.'}
        </p>

        {saidAfterPublishing !== null && (
          <p className="scheme-form__published" role="status">
            <TickIcon />
            <span>{saidAfterPublishing}</span>
          </p>
        )}
      </form>

      {/* On exactly the condition the Publish button is enabled on, and never on `comparison`
          alone. A panel describing figures that are no longer in the boxes above it is worse than
          no panel: somebody reads it, presses a greyed-out button, and concludes the screen is
          broken rather than that they have changed something since they looked. */}
      {lookedAt && comparison !== null && <WhatItWouldDo comparison={comparison} />}
    </article>
  )
}

/**
 * What a run of weeks would be paid at each of its first several weeks, drawn as the three boxes
 * above it are typed.
 *
 * <p><strong>This is the only arithmetic on the screen, and it is worth saying why it is
 * allowed.</strong> Every other figure in this application is the backend's answer, and this one is
 * not an answer at all: it is a picture of what is in three boxes, redrawn as they change, so that
 * somebody building a ladder can see its shape before they ask anybody about it. It is quoted to
 * nobody, it decides nothing, and the moment a real answer is wanted the Preview button fetches one
 * — which is also where a ladder the backend would refuse gets refused. The climb is the same
 * shape the backend uses: the ordinary rate for the first week, a step for each further week, and
 * never past the cap.
 *
 * <p><strong>It draws nothing at all until all three boxes read as numbers.</strong> A half-typed
 * "0." is not a rate and a ladder drawn from it would be inventing figures out of a keystroke. An
 * empty box is likewise nothing rather than nought, which is the same reading the backend gives it.
 *
 * <p>Eight weeks because a ladder of tenths reaches its cap at six and a reader needs to see it go
 * flat to believe it has. It is a table with a row per week rather than a sentence, because what an
 * administrator is checking is the shape of a column of figures.
 */
function WhatThisLadderWouldPay({
  theOrdinaryRate,
  extraForEachFurtherWeek,
  theMostAStreakPays,
}: {
  theOrdinaryRate: string
  extraForEachFurtherWeek: string
  theMostAStreakPays: string
}) {
  const ordinary = asAFigure(theOrdinaryRate)
  const step = asAFigure(extraForEachFurtherWeek)
  const cap = asAFigure(theMostAStreakPays)

  if (ordinary === null || step === null || cap === null) {
    return (
      <p className="climb__nothing">
        Fill all three boxes in with figures and the first eight weeks of a run are drawn here.
      </p>
    )
  }

  const weeks = [1, 2, 3, 4, 5, 6, 7, 8]

  return (
    <div className="climb">
      <p className="climb__what">What a run would be paid, week by week</p>
      <ol className="climb__weeks">
        {weeks.map((week) => {
          const paid = Math.min(ordinary + step * (week - 1), cap)
          return (
            <li key={week} className={paid >= cap ? 'climb__week climb__week--capped' : 'climb__week'}>
              <span className="climb__when">{inWeeks(week)}</span>
              <span className="climb__rate">×{rate.format(paid)}</span>
            </li>
          )
        })}
      </ol>
      <p className="climb__rule">
        Drawn from the three boxes above and from nothing else — it is the shape you are building
        rather than an answer anybody has given. Preview it to have the backend price it.
      </p>
    </div>
  )
}

/**
 * The balance rungs: the amounts at which the application tells somebody their savings have passed
 * a mark.
 *
 * <p>A list of boxes rather than one box of commas, because that is what the backend takes — an
 * ordered collection of amounts, of a length nobody has fixed — and because a comma-separated box
 * would make "the third rung is blank" impossible to say. An empty box in the middle stays an empty
 * box and travels as one, so the refusal names the blank rather than a ladder quietly one rung
 * shorter than the one on the screen.
 *
 * <p>Each rung carries its own label rather than a placeholder standing in for one. A row of
 * unlabelled boxes is a row a screen reader reads as "edit text, edit text, edit text", and the
 * whole point of these is which one is which.
 */
function TheBalanceRungs({
  rungs,
  onChanged,
}: {
  rungs: string[]
  onChanged: (rungs: string[]) => void
}) {
  return (
    <div className="field rungs">
      <span className="rungs__label" id="schemeRungsLabel">
        The balance rungs, in whole euros
      </span>
      <ul className="rungs__list" aria-labelledby="schemeRungsLabel">
        {rungs.map((rung, place) => (
          <li className="rungs__one" key={place}>
            <label className="rungs__number" htmlFor={`schemeRung${place}`}>
              Rung {place + 1}
            </label>
            <input
              className="text-input"
              id={`schemeRung${place}`}
              inputMode="decimal"
              value={rung}
              onChange={(box) =>
                onChanged(rungs.map((was, which) => (which === place ? box.target.value : was)))
              }
              placeholder="100.00"
            />
            <button
              type="button"
              className="link link--small"
              onClick={() => onChanged(rungs.filter((_, which) => which !== place))}
            >
              Take it out
            </button>
          </li>
        ))}
      </ul>
      <button type="button" className="link link--small" onClick={() => onChanged([...rungs, ''])}>
        Add a rung
      </button>
      <p className="field__note">
        Whole euros, strictly climbing, and at least one of them. A rung is a mark a customer is
        congratulated on passing and warned about falling back below.
      </p>
    </div>
  )
}

/**
 * The comparison: what the candidate is, what it moves, who it would have moved, what it does to
 * points, and how much more or less this application would be saying to people tonight.
 *
 * <p><strong>The label on it is the backend's own field and not a caption written here.</strong>
 * Everything in this panel is a counterfactual — what everybody's run *would* read had this been
 * the rule for the last twenty-six weeks, and not what happens when it takes effect, which is
 * nothing, because a week is judged by the scheme in force on its own Monday. A screen that worded
 * that itself is a screen that can word it differently from the endpoint, or forget it; the
 * sentence is part of the answer for the same reason the refusals are.
 *
 * <p>Nothing in here is computed. Every count, every fall, every day and the whole paragraph about
 * points came back from the one request this panel is drawn from.
 */
function WhatItWouldDo({ comparison }: { comparison: WhatThisSchemeWouldDo }) {
  return (
    <div className="would">
      <p className="would__what" role="note">
        {comparison.whatThisIs}
      </p>

      <p className="would__span">
        Version {comparison.theVersionThisWouldBecome.version} as against version{' '}
        {comparison.theVersionInForce.version}, over {inWeeks(comparison.overHowManyWeeks)} as if it
        had been the rule since {asADay(comparison.asIfItHadBeenTheRuleSince)}.
      </p>

      {comparison.itWouldChangeNothing && (
        <p className="would__nothing">
          <TickIcon />
          <span>This candidate changes nothing. Every figure reads exactly as it does today.</span>
        </p>
      )}

      <h4 className="would__title">Figure by figure</h4>
      <ul className="would__figures">
        {comparison.figures.map((figure) => (
          <li
            key={figure.figure}
            className={figure.itWouldChange ? 'would__figure would__figure--moved' : 'would__figure'}
          >
            <span className="would__figure-name">{theBoxCalled(figure.figure)}</span>
            <span className="would__figure-now">{figure.asItReadsNow}</span>
            <span className="would__figure-arrow" aria-hidden="true">
              →
            </span>
            <span className="would__figure-would">{figure.asItWouldRead}</span>
            {figure.itWouldChange && <span className="would__moved">moves</span>}
          </li>
        ))}
      </ul>

      <h4 className="would__title">Runs of weeks</h4>
      <dl className="terms">
        <div>
          <dt>Customers examined</dt>
          <dd>{comparison.runs.customersExamined}</dd>
        </div>
        <div>
          <dt>Runs that would read differently</dt>
          <dd>{comparison.runs.runsThatWouldReadDifferently}</dd>
        </div>
        <div>
          <dt>Who would gain</dt>
          <dd>{comparison.runs.whoWouldGain}</dd>
        </div>
        <div>
          <dt>Who would lose</dt>
          <dd>{comparison.runs.whoWouldLose}</dd>
        </div>
        <div>
          <dt>Untouched</dt>
          <dd>{comparison.runs.whoAreUntouched}</dd>
        </div>
        <div>
          <dt>The largest fall</dt>
          <dd>
            {inWeeks(comparison.runs.theLargestFallInWeeks)} and ×
            {rate.format(comparison.runs.theLargestFallInRate)}
          </dd>
        </div>
      </dl>

      <h4 className="would__title">Who it would move</h4>
      {comparison.theWorstAffected.length === 0 ? (
        <p className="nothing">Nobody&apos;s run of weeks would read differently.</p>
      ) : (
        <>
          <ul className="would__people">
            {comparison.theWorstAffected.map((who) => (
              <li
                key={who.customerId}
                className={
                  who.theFallInWeeks > 0 || who.theFallInRate > 0
                    ? 'would__person would__person--worse'
                    : 'would__person would__person--better'
                }
              >
                <span className="would__person-name">{who.name}</span>
                <span className="would__person-run">
                  run {who.currentRunNow} → {who.currentRunWouldRead}, best {who.bestRunNow} →{' '}
                  {who.bestRunWouldRead}
                </span>
                <span className="would__person-rate">
                  ×{rate.format(who.rateNow)} → ×{rate.format(who.rateWouldRead)}
                </span>
              </li>
            ))}
          </ul>
          {/* The list is the backend's and is capped there, at the twenty who moved most. Saying so
              is the difference between a short list and a list somebody reads as everybody. */}
          <p className="would__rule">
            Worst affected first, and only those who moved. The backend sends at most twenty of
            them, so a bank-wide count is the table above rather than the length of this list.
          </p>
        </>
      )}

      <h4 className="would__title">Points</h4>
      <p className="would__points">{comparison.points.saidPlainly}</p>
      <dl className="terms">
        <div>
          <dt>A batch lasts</dt>
          <dd>{inMonths(comparison.points.howLongABatchLastsNow)}</dd>
        </div>
        <div>
          <dt>A batch would last</dt>
          <dd>{inMonths(comparison.points.howLongABatchWouldLast)}</dd>
        </div>
        <div>
          <dt>A batch earned on the effective date would go on</dt>
          <dd>{asADay(comparison.points.aBatchEarnedOnTheEffectiveDateWouldDieOn)}</dd>
        </div>
      </dl>

      <h4 className="would__title">What would be said tonight</h4>
      <ul className="would__lines">
        {comparison.notifications.map((line) => (
          <li key={line.line} className="would__line">
            <span className="would__line-name">{theLineCalled(line.line)}</span>
            <span className="would__line-more">
              +{line.wouldBeToldAndIsNot} told who are not
            </span>
            <span className="would__line-fewer">
              −{line.isToldAndWouldNotBe} told who are
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/**
 * Every version the bank has published, newest first, with the ones whose Monday has not arrived
 * marked as not yet in force.
 *
 * <p>Newest first because this list is an announcement board rather than a story. A savings
 * product's history is drawn oldest first, deliberately, because what a customer reads there is how
 * the product got to where it is; what somebody standing in front of *this* list wants is what is
 * coming and what was just done, and the row they came for is at the top.
 *
 * <p><strong>Which rows have started is worked out from the version in force, and not from a
 * clock.</strong> The reading deliberately carries no "in force" flag — a boolean beside a date
 * would be a second answer to the same question, computed at a different moment — so something has
 * to make the comparison, and the honest thing to compare against is the answer the backend already
 * gave: `GET /api/scheme` is defined as the version in force *today*, on the application's own
 * clock, which is the clock a trainer has been winding. Every version numbered above it is one
 * whose Monday has not come, because a version may only ever be dated on or after the Monday the
 * announced one is waiting on and numbers ascend with publication.
 *
 * <p>The browser's own idea of the date is deliberately not consulted, and neither is the
 * development clock endpoint. `new Date()` is the machine's day rather than the application's, and
 * on a clock wound a year forward for a demonstration it would mark a version that is plainly
 * deciding everybody's rate as not yet in force. The `/api/dev/clock` read exists only under the
 * development profile, answers with an instant rather than a day, and says in its own javadoc that
 * nothing in the frontend calls it; making this screen the first thing that did would give the
 * workspace a hole in every deployment where that profile is off, in exchange for an answer the
 * version in force already gives exactly.
 */
function ThePublishedVersions({
  versions,
  inForce,
  loadError,
}: {
  versions: TheSchemeAsPublished[] | null
  inForce: TheSchemeAsPublished | null
  loadError: string | null
}) {
  return (
    <>
      <div className="section-head">
        <h2>Every version published</h2>
      </div>

      {loadError !== null && <Refusal reason={loadError} standing />}
      {versions === null && loadError === null && (
        <div className="card">
          <Waiting label="Loading the published versions…" bars={['70%', '90%']} />
        </div>
      )}

      {versions !== null && (
        <ol className="versions scheme-versions">
          {versions.map((version) => {
            // Null only while the version in force is still in flight or its read failed, and then
            // no row claims anything either way: a list that guessed would be a list quietly
            // telling somebody a published scheme is not deciding anything.
            const notYet = inForce !== null && version.version > inForce.version
            return (
              <li className="version" key={version.version}>
                <p className="version__head">
                  <span className="version__number">Version {version.version}</span>
                  <span className="version__from">from {asADay(version.effectiveFrom)}</span>
                  {notYet && <span className="version__waiting">not yet in force</span>}
                  {inForce !== null && version.version === inForce.version && (
                    <span className="version__now">in force</span>
                  )}
                </p>
                <p className="version__changed">{version.whatChanged}</p>
                <TheFiguresOfAScheme scheme={version} />
              </li>
            )
          })}
        </ol>
      )}
    </>
  )
}

/**
 * What each savings product pays on an anniversary and what it multiplies points by, read-only,
 * with the link to the door that changes them.
 *
 * <p><strong>Read-only is the whole point of the panel.</strong> These two figures and the scheme's
 * ladder are the two halves of what this bank pays for saving, and somebody repricing one is
 * entitled to see the other without going and looking for it. What they are not entitled to is a
 * second box to change it in: a product's terms are an agreement the accounts opened under them
 * live by, published as a whole version from the products back office, and a figure with two places
 * it can be edited from is a figure that will be edited from the wrong one.
 *
 * <p>Two figures of the many a set of terms carries, because these are the two that are about
 * *loyalty* — what staying is worth — which is the question the scheme is also an answer to. The
 * rate, the notice, the term and the floor are about the shape of the product and are on the screen
 * that owns them.
 */
function LoyaltyByProduct({ onOpenTheProducts }: { onOpenTheProducts: () => void }) {
  const [products, setProducts] = useState<SavingsProduct[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    const request = new AbortController()
    fetchSavingsProducts(request.signal)
      .then((shelf) => {
        if (!request.signal.aborted) {
          setProducts(shelf)
          setLoadError(null)
        }
      })
      .catch((problem: Error) => {
        if (!request.signal.aborted) {
          setLoadError(problem.message)
        }
      })
    return () => request.abort()
  }, [])

  return (
    <>
      <div className="section-head">
        <h2>Loyalty, by product</h2>
        <button type="button" className="link link--small" onClick={onOpenTheProducts}>
          Run the savings products
          <ForwardIcon />
        </button>
      </div>

      {loadError !== null && <Refusal reason={loadError} standing />}
      {products === null && loadError === null && (
        <div className="card">
          <Waiting label="Loading the savings products…" bars={['70%', '90%']} />
        </div>
      )}

      {products !== null && (
        <article className="card reveal">
          <p className="scheme-loyalty__note">
            What each product pays for staying, as its terms stand today. These are published from
            the savings products back office and are shown here so the whole priced surface reads in
            one place — there is deliberately no way to change them from this screen.
          </p>
          <ul className="scheme-loyalty">
            {products.map((product, place) => (
              <li className="scheme-loyalty__row" key={product.code} style={rowDelay(place)}>
                <span className="scheme-loyalty__name">{product.name}</span>
                <span className="scheme-loyalty__figure">
                  <span className="scheme-loyalty__what">Anniversary</span>
                  {asAPercentage(product.currentTerms.anniversaryRatePercent)}
                </span>
                <span className="scheme-loyalty__figure">
                  <span className="scheme-loyalty__what">Points per euro</span>×
                  {rate.format(product.currentTerms.pointsMultiplier)}
                </span>
              </li>
            ))}
          </ul>
        </article>
      )}
    </>
  )
}

/**
 * Whether the body that was previewed is the body that would now be published.
 *
 * <p><strong>This is the Publish button's lock, and it is a comparison rather than a flag on
 * purpose.</strong> A boolean set by the preview and cleared by every `onChange` is the same rule
 * written twice — once where it is granted and once, thirteen times over, where it is revoked — and
 * the thirteenth box added to this form next year is the one whose handler forgets to clear it. A
 * comparison cannot be forgotten: it is asked afresh on every render, of the whole body, and a
 * figure nobody has taught it about is included because it is in the object.
 *
 * <p><strong>The two bodies are compared as the JSON that would go over the wire</strong>, which is
 * exactly the thing the rule is about: not "has anything conceptually changed" but "is the request
 * I am about to send the request you looked at". Both objects are built by the same literal in the
 * same key order, so the serialisation is stable, and a field added to
 * {@link ACandidateVersionOfTheScheme} is covered the day it is added rather than the day somebody
 * remembers to extend a hand-written list of twelve equality tests.
 *
 * <p>It follows that typing a figure and typing it straight back unlocks publishing again, which is
 * right: the scheme on the form is once more the scheme that was previewed, character for
 * character, and the preview that was taken of it is still an honest answer about it.
 */
function theSameCandidate(
  previewed: ACandidateVersionOfTheScheme,
  now: ACandidateVersionOfTheScheme,
): boolean {
  return JSON.stringify(previewed) === JSON.stringify(now)
}

/**
 * A figure the backend published, put back in a box somebody types in.
 *
 * <p>Plain digits with a full stop, because that is what the form sends and what the backend reads.
 * Emphatically not the Dutch formatting the rest of this application prints figures in: `1,50` in
 * one of these boxes comes back as a refusal about a number that could not be read, which would be
 * the screen having filled the box in wrongly itself.
 *
 * <p>The scale the backend published at is lost on the way through JSON — a rate of `1.0000` is the
 * number one by the time it is here — so a box opens reading `1` rather than `1.0000`. That is the
 * same figure and the backend takes it; the placeholder beside it shows the fuller spelling for
 * anybody wondering how finely they may quote one.
 */
function asTyped(figure: number): string {
  return String(figure)
}

/**
 * Text out of a box as the number it spells, or nothing at all.
 *
 * <p>Used by the live ladder and by nothing else, which is the only place on this screen that has
 * any reason to read a figure rather than pass it on. An empty box and anything the language cannot
 * read as a number — "0,50" with a comma in it, a word, a stray sign — come back as nothing, and the
 * ladder then draws nothing rather than a shape made up out of a keystroke. What the backend thinks
 * of a figure it *can* read is still entirely the backend's to say: this decides whether to draw,
 * never whether to publish.
 */
function asAFigure(typed: string): number | null {
  if (typed.trim() === '') {
    return null
  }
  const figure = Number(typed)
  return Number.isFinite(figure) ? figure : null
}

/**
 * The name of the box a preview's figure came out of, in the words the form's own label uses, so
 * that a row of the comparison and the box it is about are recognisably the same thing.
 *
 * <p>A figure this page has not been told about is printed as the backend named it rather than
 * hidden — the same reading the products screen gives an unknown kind of agreement. A row missing
 * from a comparison of what a repricing moves would be worse than a row with a programmer's word in
 * it.
 */
function theBoxCalled(figure: string): string {
  switch (figure) {
    case 'weeklyThreshold':
      return 'What a week asks for'
    case 'theOrdinaryRate':
      return 'The ordinary rate'
    case 'extraForEachFurtherWeek':
      return 'Each further week adds'
    case 'theMostAStreakPays':
      return 'The most a streak pays'
    case 'howLongABatchOfPointsLasts':
      return 'A batch of points lasts, in months'
    case 'balanceRungs':
      return 'The balance rungs'
    case 'whatShareOfABudgetIsRunningLow':
      return 'A budget is running low at'
    case 'howManyOutstandingIsASpiral':
      return 'How many outstanding is a spiral'
    case 'daysBeforeAMaturityIsWorthSaying':
      return 'Days before a maturity is worth saying'
    case 'daysBeforeAnAnniversaryIsWorthSaying':
      return 'Days before an anniversary is worth saying'
    default:
      return figure
  }
}

/** The same, for the five lines the scheme draws, which travel as their own names. */
function theLineCalled(line: ALineTheSchemeDraws | string): string {
  switch (line) {
    case 'A_BALANCE_RUNG':
      return 'A balance rung passed or lost'
    case 'A_BUDGET_RUNNING_LOW':
      return 'A budget running low'
    case 'ARREARS_PILING_UP':
      return 'Arrears piling up'
    case 'A_MATURITY_COMING_SOON':
      return 'A maturity coming soon'
    case 'AN_ANNIVERSARY_COMING_SOON':
      return 'An anniversary coming soon'
    default:
      return line
  }
}

/**
 * The version of the scheme in force, for the handful of sentences elsewhere in this application
 * that name one of its figures in words.
 *
 * <p><strong>Why this exists at all.</strong> Every figure on every screen in this application is
 * already the backend's — a balance, a rate, a run of weeks — but a few *sentences* had a policy
 * figure written into their markup. "Points last twelve months" was one of them, in three places,
 * and it was true for exactly as long as twelve months was a constant in a Java class. It is a row
 * in a table now, changeable from the screen above, so a sentence that goes on saying twelve is a
 * sentence that will one day be lying to a customer about when their points go.
 *
 * <p><strong>A hook each caller uses rather than a figure drilled down to them.</strong> The three
 * sentences are on three unrelated screens — a savings account's year ahead, a branch of the
 * simulator, and the voucher-cancellation form in the rewards back office — with nothing above them
 * in common but `Banking` itself. Threading one number from there through six components that have
 * no other business with the scheme would put the scheme in the signature of everything it passed
 * through; a context would make this file's first one for a footnote. Every screen in this
 * application already reads what it needs where it needs it, and this is one more small read of an
 * endpoint that belongs to no customer and no account.
 *
 * <p><strong>A failed read is deliberately swallowed, and it is the only swallowed failure in this
 * file.</strong> Everywhere else a refusal is the news and gets a band of its own. Here the caller
 * is a subordinate clause inside a sentence about something else, and a red warning across somebody's
 * savings account because the wording of a footnote could not be fetched would be the page shouting
 * about its own plumbing. What every caller does instead is say less: the clause naming the figure
 * is simply not drawn until the figure is known, and no caller ever prints a number this hook did
 * not give it.
 */
function useTheSchemeInForce(): TheSchemeAsPublished | null {
  const [scheme, setScheme] = useState<TheSchemeAsPublished | null>(null)

  useEffect(() => {
    const request = new AbortController()
    fetchTheSchemeInForce(request.signal)
      .then((inForce) => {
        if (!request.signal.aborted) {
          setScheme(inForce)
        }
      })
      .catch(() => {
        // Nothing, on purpose — see above. The sentence goes out without its clause.
      })
    return () => request.abort()
  }, [])

  return scheme
}

/**
 * A number of months with its noun agreeing with it, the way {@link inWeeks} does for a run.
 *
 * <p>Digits rather than words, which is the choice `inWeeks` already made and the one that keeps a
 * lifetime the bank can publish at any whole number from having to be spelled out. "6 months" and
 * "18 months" read; "eighteen months" is a sentence this file would have to learn to count in.
 */
function inMonths(months: number): string {
  return months === 1 ? '1 month' : `${months} months`
}

/** The same, for a count of days before something is worth saying. */
function inDays(days: number): string {
  return days === 1 ? '1 day' : `${days} days`
}

/** And for the count of outstanding bill dates that the application reads as a spiral. */
function inBills(bills: number): string {
  return bills === 1 ? '1 unpaid date' : `${bills} unpaid dates`
}

/**
 * A multiple as the scheme publishes it, to between two and four places.
 *
 * <p>Two at the least, because 1,5 and 1,50 are the same number and only one of them reads as a rate
 * on a ladder that climbs in tenths. Four at the most, because the scheme may publish a step of
 * 0,0250 — a real step, and one that two places would flatten to 0,03 and misreport. The rates
 * *paid* are quoted to two by {@link rate}, which is the backend's own rounding of an answer; this
 * is the figure as published, and rounding a published figure on the screen that reprices it would
 * be the one place in the application where that could not be allowed to happen.
 */
const schemeRate = new Intl.NumberFormat('nl-BE', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
})


/**
 * The counter: a box for a code, and one large button that hands the thing over.
 *
 * <p><strong>Laid out for the phone somebody is actually holding.</strong> This is the one screen
 * in the application whose real device is known — it is a member of staff at a till with a queue
 * behind them and one hand free — so it is a single column at any width, the code field is as
 * tall as a thumb, and the confirmation is a full-width button rather than something to aim at. The
 * input asks for no autocorrect, no autocapitalisation of its own and no spellcheck, because every
 * one of them is a phone keyboard improving a voucher code into a different one.
 *
 * <p><strong>Nothing here checks who is using it.</strong> There is no sign-in behind this, no
 * role and no password, and the backend endpoints it calls say the same thing in their own
 * javadoc: this is a counter screen and not authorisation. It is reached from a footer link rather
 * than a tab for the same reason — it is not the customer's, and the application should not
 * pretend it is anybody's in particular.
 *
 * <p><strong>Looking up and handing over are two presses, deliberately.</strong> The person at the
 * till reads the voucher out to the customer before they give them anything, and a single button
 * that did both would make "let me just check" impossible. So the code is looked up, the screen
 * says what it is and whether it is good, and only then does the confirmation appear.
 *
 * <p><strong>Every sentence on it is the backend's.</strong> Whether a voucher is good, why it is
 * not, and what it is for are all answers this page is given rather than answers it works out —
 * which is what lets the two states nothing yet writes arrive without a change here.
 *
 * <p>The counter's name is remembered for as long as the screen is open, because one till hands
 * over many vouchers in an afternoon and retyping "Leuven, till 2" every time is how a required
 * field turns into a field somebody fills with a full stop. It is not remembered between visits:
 * a stale counter name on somebody else's phone is exactly the attribution this field exists to
 * get right.
 */
function Counter({ onBack }: { onBack: () => void }) {
  const [typed, setTyped] = useState('')
  const [counter, setCounter] = useState('')
  const [voucher, setVoucher] = useState<VoucherAtTheCounter | null>(null)
  const [looking, setLooking] = useState(false)
  const [handingOver, setHandingOver] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)
  const [justUsed, setJustUsed] = useState(false)

  function lookUp(event: FormEvent) {
    event.preventDefault()
    setLooking(true)
    setRefusal(null)
    // The voucher on screen goes before the new one arrives. A code that comes back refused must
    // not leave the previous customer's voucher sitting there looking like an answer to it.
    setVoucher(null)
    setJustUsed(false)
    lookUpVoucher(typed)
      .then(setVoucher)
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setLooking(false))
  }

  function handOver() {
    if (voucher === null) {
      return
    }
    setHandingOver(true)
    setRefusal(null)
    // The code that was found rather than the code that was typed, so that the thing marked used
    // is unambiguously the thing on the screen.
    markVoucherUsed(voucher.voucherCode, counter)
      .then((used) => {
        setVoucher(used)
        setJustUsed(true)
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setHandingOver(false))
  }

  return (
    <section className="till">
      <button type="button" className="link link--back" onClick={onBack}>
        <BackIcon />
        Back to the overview
      </button>

      <div className="card till__card">
        <p className="till__warning" role="note">
          Nothing here checks who you are. Anybody who can reach this screen can mark any voucher
          used.
        </p>

        <form className="till__find" onSubmit={lookUp}>
          <div className="field">
            <label htmlFor="voucherCode">Voucher code</label>
            <input
              className="text-input till__code"
              id="voucherCode"
              name="voucherCode"
              type="text"
              inputMode="text"
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="characters"
              spellCheck={false}
              placeholder="SS-CIN-7F3K2Q"
              value={typed}
              onChange={(event) => setTyped(event.target.value)}
            />
          </div>
          <Button type="submit" block busy={looking} disabled={looking || typed.trim() === ''}>
            {looking ? 'Looking it up…' : 'Look it up'}
          </Button>
        </form>

        {refusal !== null && <Refusal reason={refusal} />}

        {voucher !== null && (
          <div className={voucher.good ? 'till__voucher is-good' : 'till__voucher is-spent'}>
            {/* The verdict first and in one word, because it is the thing being read at arm's
                length over a counter. Everything under it is the detail that backs it up. */}
            <p className="till__verdict">{voucher.good ? 'Good' : verdictFor(voucher)}</p>
            <p className="till__what">
              <RewardIcon code={voucher.code} />
              {voucher.title}
            </p>
            <p className="till__code-read">{voucher.voucherCode}</p>
            {/* What to actually put on the counter, when the one voucher covers several things.
                The till is the one screen that cannot do without this: "family night in" is
                what the code says and two seats and a bag of popcorn is what has to be handed
                over. Empty for every voucher that is not a bundle's, and it draws nothing. */}
            <WhatIsInABundle contents={voucher.contents} />
            <dl className="till__facts">
              <div>
                <dt>Held by</dt>
                <dd>{voucher.customerName ?? `Customer ${voucher.customerId}`}</dd>
              </div>
              <div>
                <dt>Claimed</dt>
                <dd>{dateAndTime.format(new Date(voucher.claimedAt))}</dd>
              </div>
              {voucher.usedAt !== null && (
                <div>
                  <dt>Used</dt>
                  <dd>
                    {dateAndTime.format(new Date(voucher.usedAt))}
                    {voucher.usedByCounter !== null && ` · ${voucher.usedByCounter}`}
                  </dd>
                </div>
              )}
              {/* Whether it has run out or not. A till asked "how long have I got?" should be
                  able to answer, and a till refusing one that has expired should be able to say
                  which day it went — the same day the customer's own screen has been showing
                  them all along, which is what stops the two of them arguing about it. */}
              {voucher.expiresOn !== null && (
                <div>
                  <dt>{voucher.state === 'EXPIRED' ? 'Ran out' : 'Good until'}</dt>
                  <dd>{asADay(voucher.expiresOn)}</dd>
                </div>
              )}
              {/* The one refusal at this counter that nothing the customer did caused: the code
                  is real, they believe it is good, and it was the scheme that revoked it. The
                  reason is the only thing the person at the till can honestly say out loud, so
                  it is on the screen in the words whoever cancelled it typed rather than
                  summarised into a verdict. The points having gone back is not said here — it
                  is on the customer's own page, and it is not the till's to promise. */}
              {voucher.cancelledBecause !== null && (
                <div>
                  <dt>Cancelled</dt>
                  <dd>
                    {voucher.cancelledAt !== null &&
                      `${dateAndTime.format(new Date(voucher.cancelledAt))} · `}
                    {voucher.cancelledBecause}
                  </dd>
                </div>
              )}
            </dl>

            {voucher.good && (
              <>
                <div className="field">
                  <label htmlFor="counter">Which counter are you?</label>
                  <input
                    className="text-input"
                    id="counter"
                    name="counter"
                    type="text"
                    autoComplete="off"
                    placeholder="Leuven Bondgenotenlaan, till 2"
                    value={counter}
                    onChange={(event) => setCounter(event.target.value)}
                  />
                </div>
                {/* One press, full width, and the last thing on the screen. Disabled until a
                    counter has been named, because the backend requires one and a button that
                    reached it only to be refused would be a queue standing still for nothing. */}
                <Button
                  block
                  busy={handingOver}
                  disabled={handingOver || counter.trim() === ''}
                  onClick={handOver}
                >
                  {handingOver ? 'Marking it used…' : 'Mark used'}
                </Button>
              </>
            )}

            {justUsed && (
              <p className="flash">
                <TicketIcon />
                Handed over — {voucher.title}
              </p>
            )}
          </div>
        )}
      </div>
    </section>
  )
}

/**
 * The form that adds an offer, which is the whole point of the feature: a reward that does not
 * need a release.
 *
 * <p>It says "draft" on the button rather than "add", because that is what pressing it does and
 * the difference matters — a half-written reward never reaches a customer, and somebody who
 * expected this to go live would find out from the list rather than from the screen.
 *
 * <p>Nothing here is validated. Whether a title is a title, what the least an offer may cost is
 * and whether the code is free are the backend's rules, and each comes back as a sentence shown
 * unchanged. A page that checked first would be a second copy of the rules, and the second copy is
 * always the one that is out of date.
 */
function WritingAnOffer({ onWritten }: { onWritten: (written: AdministeredOffer) => void }) {
  const [code, setCode] = useState('')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [costInPoints, setCostInPoints] = useState('')
  const [voucherPrefix, setVoucherPrefix] = useState('')
  const [opensOn, setOpensOn] = useState('')
  const [closesOn, setClosesOn] = useState('')
  const [voucherValidForDays, setVoucherValidForDays] = useState('')
  const [minimumStreakWeeks, setMinimumStreakWeeks] = useState('')
  const [requiresBadge, setRequiresBadge] = useState('')
  const [minimumLifetimePointsEarned, setMinimumLifetimePointsEarned] = useState('')
  const [maxPerCustomer, setMaxPerCustomer] = useState('')
  const [maxPerCustomerPerWeek, setMaxPerCustomerPerWeek] = useState('')
  const [stock, setStock] = useState('')
  const [discountedCostInPoints, setDiscountedCostInPoints] = useState('')
  const [discountOpensOn, setDiscountOpensOn] = useState('')
  const [discountClosesOn, setDiscountClosesOn] = useState('')
  // What goes into it, as rows somebody adds. Text rather than numbers even for the quantity,
  // because an emptied number box is its own kind of nothing in a browser and the backend
  // refuses a line with no quantity in a sentence naming it — which is a better answer than
  // this page deciding what an empty box meant.
  const [members, setMembers] = useState<{ code: string; quantity: string }[]>([])
  const [writing, setWriting] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)

  function write(submitted: FormEvent) {
    submitted.preventDefault()
    setWriting(true)
    setRefusal(null)
    writeAnOffer({
      code,
      title,
      description,
      // An empty box is nought points, which the backend refuses in a sentence about the price.
      // Reading it as "they meant to leave it out" would be this page inventing a rule.
      costInPoints: Number(costInPoints),
      voucherPrefix,
      // An empty date box is an offer with no such day, which is the ordinary case and what all
      // four of the rewards this application has always had look like. The text goes up as it
      // was typed: whether it is a day is the backend's answer.
      opensOn,
      closesOn,
      // Left out when the box is empty, which is the answer for a voucher that never runs out —
      // and the answer for every offer this scheme has today. Sending a nought instead would be
      // this page turning "they said nothing" into "no days at all", which the backend refuses
      // in a sentence about a voucher nobody could use.
      ...(voucherValidForDays.trim() === ''
        ? {}
        : { voucherValidForDays: Number(voucherValidForDays) }),
      // Who the offer is for: three boxes, each left out when it is empty, which is what an
      // offer that restricts nobody looks like and what all four of the rewards this
      // application has always had look like. Left out rather than sent as nought for the same
      // reason the shelf life is — a streak requirement of nought weeks is a rule every
      // customer already meets, and the backend refuses it in a sentence about the rule.
      ...(minimumStreakWeeks.trim() === ''
        ? {}
        : { minimumStreakWeeks: Number(minimumStreakWeeks) }),
      ...(requiresBadge.trim() === '' ? {} : { requiresBadge: requiresBadge.trim() }),
      ...(minimumLifetimePointsEarned.trim() === ''
        ? {}
        : { minimumLifetimePointsEarned: Number(minimumLifetimePointsEarned) }),
      // The caps read exactly like the shelf life, and are left out for the same reason: an
      // empty box is an offer one customer may have as often as they like, which is what every
      // reward this application ships is. A nought is not that — the backend refuses it as an
      // offer nobody may ever claim — so an empty box travels as an absence.
      ...(maxPerCustomer.trim() === '' ? {} : { maxPerCustomer: Number(maxPerCustomer) }),
      ...(maxPerCustomerPerWeek.trim() === ''
        ? {}
        : { maxPerCustomerPerWeek: Number(maxPerCustomerPerWeek) }),
      // Left out when the box is empty, which is an offer that never runs out — and the answer
      // for all four of the rewards this application has always had. Emphatically not sent as a
      // nought: nought is a real and different answer meaning there are none of it at the
      // moment, and reading an empty box as one would publish every new offer sold out.
      ...(stock.trim() === '' ? {} : { stock: Number(stock) }),
      // The sale price is left out when the box is empty, like the shelf life above and for the
      // same reason: absence is what an offer at one price says about itself, and a nought would
      // be this page turning "they said nothing" into a giveaway the backend refuses. The two
      // days go up as they were typed, empty or not, and whether the three of them make a
      // promotion is the backend's sentence.
      ...(discountedCostInPoints.trim() === ''
        ? {}
        : { discountedCostInPoints: Number(discountedCostInPoints) }),
      discountOpensOn,
      discountClosesOn,
      // What goes into it, when anything does. Lines with no code at all are dropped, because
      // an empty row is a row somebody added and did not fill in rather than a bundle naming
      // nothing; everything else goes up as typed, including a quantity that is not a number,
      // which arrives as a nought and is refused in a sentence about that line. Left out
      // entirely when there is nothing in it, which is what an ordinary offer is.
      ...(members.filter((member) => member.code.trim() !== '').length === 0
        ? {}
        : {
            members: members
              .filter((member) => member.code.trim() !== '')
              .map((member) => ({
                code: member.code.trim(),
                quantity: Number(member.quantity),
              })),
          }),
    })
      .then((written) => {
        onWritten(written)
        setCode('')
        setTitle('')
        setDescription('')
        setCostInPoints('')
        setVoucherPrefix('')
        setOpensOn('')
        setClosesOn('')
        setVoucherValidForDays('')
        setMinimumStreakWeeks('')
        setRequiresBadge('')
        setMinimumLifetimePointsEarned('')
        setMaxPerCustomer('')
        setMaxPerCustomerPerWeek('')
        setStock('')
        setDiscountedCostInPoints('')
        setDiscountOpensOn('')
        setDiscountClosesOn('')
        setMembers([])
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setWriting(false))
  }

  return (
    <div className="card">
      <h2 className="card__title">Write an offer</h2>
      <form className="admin-form" onSubmit={write}>
        <div className="field">
          <label htmlFor="offer-code">Code</label>
          <input
            className="text-input"
            id="offer-code"
            value={code}
            onChange={(typed) => setCode(typed.target.value)}
            placeholder="WINTER_HAMPER"
          />
          <p className="field__note">
            Fixed for the life of the offer, because the vouchers already issued name it.
          </p>
        </div>
        <div className="field">
          <label htmlFor="offer-title">Title</label>
          <input
            className="text-input"
            id="offer-title"
            value={title}
            onChange={(typed) => setTitle(typed.target.value)}
            placeholder="Winter hamper"
          />
        </div>
        <div className="field">
          <label htmlFor="offer-words">Words on the card</label>
          <textarea
            className="text-input"
            id="offer-words"
            rows={2}
            value={description}
            onChange={(typed) => setDescription(typed.target.value)}
            placeholder="What the customer is actually getting."
          />
        </div>
        <div className="field">
          <label htmlFor="offer-cost">Price in points</label>
          <input
            className="text-input"
            id="offer-cost"
            inputMode="numeric"
            value={costInPoints}
            onChange={(typed) => setCostInPoints(typed.target.value)}
            placeholder="250"
          />
        </div>
        <div className="field">
          <label htmlFor="offer-prefix">Voucher prefix</label>
          <input
            className="text-input"
            id="offer-prefix"
            value={voucherPrefix}
            onChange={(typed) => setVoucherPrefix(typed.target.value)}
            placeholder="WIN"
          />
          <p className="field__note">The letters in the middle of a code: SS-WIN-7F3K2Q.</p>
        </div>
        <div className="field">
          <label htmlFor="offer-opens">Opens on</label>
          <input
            className="text-input"
            id="offer-opens"
            type="date"
            value={opensOn}
            onChange={(typed) => setOpensOn(typed.target.value)}
          />
          <p className="field__note">
            Leave it empty for an offer that is open from the moment it is published.
          </p>
        </div>
        <div className="field">
          <label htmlFor="offer-closes">Closes on</label>
          <input
            className="text-input"
            id="offer-closes"
            type="date"
            value={closesOn}
            onChange={(typed) => setClosesOn(typed.target.value)}
          />
          <p className="field__note">
            The last day it can be claimed, and it can be claimed all through that day. Leave it
            empty for an offer that never closes.
          </p>
        </div>
        <div className="field">
          <label htmlFor="offer-shelf-life">Shelf life in days</label>
          <input
            className="text-input"
            id="offer-shelf-life"
            inputMode="numeric"
            value={voucherValidForDays}
            onChange={(typed) => setVoucherValidForDays(typed.target.value)}
            placeholder="30"
          />
          <p className="field__note">
            How long a voucher for this is good for, counting the day it was claimed. Leave it
            empty and the vouchers never run out.
          </p>
        </div>
        {/* Who it is for: three plain thresholds and nothing anybody has to write. A customer
            who does not meet one of them is shown the offer locked, with the rule, rather than
            not shown it at all — which is the only thing on their screen that says what the
            scheme wants from them. */}
        <div className="field">
          <label htmlFor="offer-streak">Only for a streak of</label>
          <input
            className="text-input"
            id="offer-streak"
            inputMode="numeric"
            value={minimumStreakWeeks}
            onChange={(typed) => setMinimumStreakWeeks(typed.target.value)}
            placeholder="10"
          />
          <p className="field__note">
            Weeks in a row, right now. Leave it empty for an offer anybody may claim.
          </p>
        </div>
        <div className="field">
          <label htmlFor="offer-badge">Only for holders of</label>
          <input
            className="text-input"
            id="offer-badge"
            value={requiresBadge}
            onChange={(typed) => setRequiresBadge(typed.target.value)}
            placeholder="SAVE_FIVE_HUNDRED"
          />
          <p className="field__note">
            The code of a challenge, as the trophy case names it. Any rung of it counts.
          </p>
        </div>
        <div className="field">
          <label htmlFor="offer-lifetime">Only for points earned in all</label>
          <input
            className="text-input"
            id="offer-lifetime"
            inputMode="numeric"
            value={minimumLifetimePointsEarned}
            onChange={(typed) => setMinimumLifetimePointsEarned(typed.target.value)}
            placeholder="5000"
          />
          <p className="field__note">
            Everything they have ever earned, not what they are holding — spending points never
            takes this away. Every rule set here has to be met.
          </p>
        </div>
        <div className="field">
          <label htmlFor="offer-max-per-customer">Most one customer may ever have</label>
          <input
            className="text-input"
            id="offer-max-per-customer"
            inputMode="numeric"
            value={maxPerCustomer}
            onChange={(typed) => setMaxPerCustomer(typed.target.value)}
            placeholder="2"
          />
          <p className="field__note">
            Counting every claim they have ever made of it. Leave it empty for an offer one
            person may have as often as they like.
          </p>
        </div>
        <div className="field">
          <label htmlFor="offer-max-per-week">Most one customer may have in a week</label>
          <input
            className="text-input"
            id="offer-max-per-week"
            inputMode="numeric"
            value={maxPerCustomerPerWeek}
            onChange={(typed) => setMaxPerCustomerPerWeek(typed.target.value)}
            placeholder="1"
          />
          <p className="field__note">
            The week everything else here is counted in: Monday to Sunday. The allowance comes
            back on the Monday.
          </p>
        </div>
        <div className="field">
          <label htmlFor="offer-stock">How many there are</label>
          <input
            className="text-input"
            id="offer-stock"
            inputMode="numeric"
            value={stock}
            onChange={(typed) => setStock(typed.target.value)}
            placeholder="40"
          />
          <p className="field__note">
            How many of it exist altogether. Customers are shown how many are left, which is this
            less what has been claimed. Leave it empty for something that never runs out.
          </p>
        </div>
        <div className="field">
          <label htmlFor="offer-sale-price">Sale price in points</label>
          <input
            className="text-input"
            id="offer-sale-price"
            inputMode="numeric"
            value={discountedCostInPoints}
            onChange={(typed) => setDiscountedCostInPoints(typed.target.value)}
            placeholder="180"
          />
          <p className="field__note">
            A second, lower price. It has to be below the ordinary one, and it needs both of the
            days below: a promotion is a price and two dates, or nothing at all.
          </p>
        </div>
        <div className="field">
          <label htmlFor="offer-sale-opens">Sale starts on</label>
          <input
            className="text-input"
            id="offer-sale-opens"
            type="date"
            value={discountOpensOn}
            onChange={(typed) => setDiscountOpensOn(typed.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="offer-sale-closes">Sale ends on</label>
          <input
            className="text-input"
            id="offer-sale-closes"
            type="date"
            value={discountClosesOn}
            onChange={(typed) => setDiscountClosesOn(typed.target.value)}
          />
          <p className="field__note">
            The last day at the lower price, and it applies all through that day. The price goes
            back up on its own the morning after.
          </p>
        </div>
        {/* What goes into it, if anything does — which is what makes it a bundle. There is no
            box anywhere saying "this is a bundle": an offer with things in it is one. Nothing
            here is checked, like everything else on this form; whether the codes name offers
            that exist, whether two is enough of them and whether one of something is enough of
            it all come back as sentences. */}
        <div className="field">
          <label htmlFor="offer-member-0">What is in it</label>
          {members.map((member, at) => (
            <div className="bundle-line" key={at}>
              <input
                className="text-input bundle-line__code"
                id={`offer-member-${at}`}
                value={member.code}
                onChange={(typed) =>
                  setMembers(
                    members.map((each, which) =>
                      which === at ? { ...each, code: typed.target.value } : each,
                    ),
                  )
                }
                placeholder="CINEMA_TICKET"
              />
              <input
                className="text-input bundle-line__how-many"
                aria-label={`How many of line ${at + 1}`}
                type="number"
                min="1"
                value={member.quantity}
                onChange={(typed) =>
                  setMembers(
                    members.map((each, which) =>
                      which === at ? { ...each, quantity: typed.target.value } : each,
                    ),
                  )
                }
                placeholder="1"
              />
              <Button
                tone="ghost"
                small
                onClick={() => setMembers(members.filter((_leaving, which) => which !== at))}
              >
                Take it out
              </Button>
            </div>
          ))}
          <Button
            tone="ghost"
            small
            onClick={() => setMembers([...members, { code: '', quantity: '1' }])}
          >
            Put something in
          </Button>
          <p className="field__note">
            Leave this empty for an ordinary offer. A bundle is two or more offers that already
            exist, handed over together for one voucher at the price above — which is the price
            somebody pays, rather than the members&apos; prices added up.
          </p>
        </div>
        {refusal !== null && <Refusal reason={refusal} />}
        <Button type="submit" busy={writing} disabled={writing}>
          Save as a draft
        </Button>
      </form>
    </div>
  )
}

/**
 * One offer in the back office: what it says, what state it is in, and what can be done to it
 * next.
 *
 * <p>What can be done to it is read off the state rather than offered to everything. A draft is
 * published; a published offer is withdrawn; a withdrawn offer is neither, because withdrawn is
 * the end of an offer's life and the backend refuses both. A button that was always there and
 * sometimes refused would be a screen inviting somebody to find out.
 *
 * <p>The edit form is folded away until it is asked for. Every row is editable and a page of
 * twenty open forms is a page nobody can read down.
 */
function AnOfferToRun({
  offer,
  place,
  working,
  onChanged,
  onPublish,
  onWithdraw,
  onRefused,
}: {
  offer: AdministeredOffer
  place: number
  working: boolean
  onChanged: (changed: AdministeredOffer) => void
  onPublish: () => void
  onWithdraw: () => void
  onRefused: (reason: string) => void
}) {
  const [open, setOpen] = useState(false)
  // Folded away like the edit form and for the same reason, and separately from it: reading who
  // is waiting and correcting the row are two different jobs, and a page that made them one
  // would open a form nobody asked for every time somebody asked a question.
  const [queueOpen, setQueueOpen] = useState(false)

  return (
    <li className={`offer offer--${offer.state.toLowerCase()}`} style={rowDelay(place)}>
      <div className="offer__head">
        <span className="offer__state">{whatStateItIsIn(offer.state)}</span>
        <h3 className="offer__title">{offer.title}</h3>
        <span className="offer__cost">{points.format(offer.costInPoints)} points</span>
      </div>
      <p className="offer__code">
        {offer.code} · vouchers stamped SS-{offer.voucherPrefix}-
        {/* Only when there is one. "No shelf life" is the ordinary case and saying it on every
            row would bury the rows where it matters. */}
        {offer.voucherValidForDays !== null &&
          ` · vouchers last ${offer.voucherValidForDays} ${
            offer.voucherValidForDays === 1 ? 'day' : 'days'
          }`}
      </p>
      {offer.description !== null && <p className="offer__words">{offer.description}</p>}
      {/* The window as it was set, and nothing about whether it is open today. Whether today is
          inside it is the customer's read, on the customer's screen; this page is one somebody
          leaves open all afternoon, and a verdict on it would be one moment's answer sitting
          there going stale. */}
      {(offer.opensOn !== null || offer.closesOn !== null) && (
        <p className="offer__window">
          {offer.opensOn !== null && <>Opens {asADay(offer.opensOn)}</>}
          {offer.opensOn !== null && offer.closesOn !== null && <> · </>}
          {offer.closesOn !== null && <>Closes {asADay(offer.closesOn)}</>}
        </p>
      )}
      {/* Who it is for, as the three plain thresholds somebody set — read back rather than
          summarised, which is the whole argument for a closed list instead of an expression: a
          rule an administrator cannot read back is a rule nobody can debug. Only when there is
          one, because "for everybody" is the ordinary case and saying it on every row would
          bury the rows where it matters. */}
      {theRulesOn(offer).length > 0 && (
        <p className="offer__rules">Only for {theRulesOn(offer).join(' · ')}</p>
      )}
      <TheCapsOnAnOffer offer={offer} />
      {/* How many exist, as somebody set it, and only when somebody did — "no limit" is the
          ordinary case and saying it on every row would bury the rows where it matters. How many
          are *left* is deliberately not here: that figure moves every time anybody claims, and
          this is a page somebody leaves open all afternoon. */}
      {offer.stock !== null && (
        <p className="offer__stock">
          {offer.stock === 1 ? 'One of these exists' : `${points.format(offer.stock)} of these exist`}
        </p>
      )}
      {/* What went into it, read back so that whoever composed it can check it. Nothing here
          edits it: a bundle's contents are fixed when it is composed, because the vouchers
          already issued for it promise exactly these things. */}
      <WhatIsInABundle contents={offer.contents} />
      {/* The sale, as it was set, and only when there is one. Three parts that arrive together
          or not at all, so one test covers the row. No verdict about today for the reason the
          window above carries none: this page is one somebody leaves open all afternoon, and a
          "on sale now" sitting on it would be one moment's answer going stale. */}
      {offer.discountedCostInPoints !== null &&
        offer.discountOpensOn !== null &&
        offer.discountClosesOn !== null && (
          <p className="offer__promotion">
            {points.format(offer.discountedCostInPoints)} points from{' '}
            {asADay(offer.discountOpensOn)} to {asADay(offer.discountClosesOn)}
          </p>
        )}

      <div className="offer__doing">
        {offer.state !== 'WITHDRAWN' && (
          <Button tone="ghost" small onClick={() => setOpen(!open)}>
            {open ? 'Leave it as it is' : 'Edit'}
          </Button>
        )}
        {offer.state === 'DRAFT' && (
          <Button small busy={working} disabled={working} onClick={onPublish}>
            Publish
          </Button>
        )}
        {offer.state === 'PUBLISHED' && (
          <Button tone="ghost" small busy={working} disabled={working} onClick={onWithdraw}>
            Withdraw
          </Button>
        )}
        {/* Only where there could be a queue at all. An offer with no stock figure never runs
            out, so nobody can ever have joined one for it, and a button that opened an empty
            list on every row would bury the rows where it matters. */}
        {offer.stock !== null && (
          <Button tone="ghost" small onClick={() => setQueueOpen(!queueOpen)}>
            {queueOpen ? 'Hide who is waiting' : 'Who is waiting'}
          </Button>
        )}
      </div>

      {queueOpen && <TheQueueForAnOffer code={offer.code} />}

      {open && (
        <ChangingAnOffer
          offer={offer}
          onChanged={(changed) => {
            onChanged(changed)
            setOpen(false)
          }}
          onRefused={onRefused}
        />
      )}
    </li>
  )
}

/**
 * Who is queued for one offer, oldest first, under the row it belongs to.
 *
 * <p><strong>Why this exists at all.</strong> The backend has answered
 * `GET /api/admin/rewards/{code}/waiting-list` since the queue was built, and the whole reason an
 * administrator is given it is user story 51 — "so that I know what to restock". Until the first
 * time anybody opened this screen in a browser, the only way to read it was with curl, which is
 * not a thing somebody running a scheme has. A restock is a number typed into the box above, and
 * the number worth typing is how many people are waiting.
 *
 * <p>Read when it is opened rather than with the catalogue, for the reason the API client gives:
 * almost every offer has nobody waiting, and a screen of nine rows would be nine requests
 * answering nothing. Read again every time it is opened, too — the sweep empties this list at
 * five in the morning and this is a page somebody leaves open all afternoon, which is the same
 * argument the row above makes for keeping "how many are left" off it.
 *
 * <p>An empty queue says so. "Nobody is waiting" is the answer to the question that was asked,
 * and a panel that opened onto nothing would read as a screen that had failed to load.
 */
function TheQueueForAnOffer({ code }: { code: string }) {
  const [queue, setQueue] = useState<APlaceInAQueue[] | null>(null)
  const [refusal, setRefusal] = useState<string | null>(null)

  useEffect(() => {
    const asking = new AbortController()
    fetchTheWaitingList(code, asking.signal)
      .then((waiting) => {
        setQueue(waiting)
        setRefusal(null)
      })
      .catch((problem: Error) => {
        if (!asking.signal.aborted) {
          setRefusal(problem.message)
        }
      })
    return () => asking.abort()
  }, [code])

  if (refusal !== null) {
    return <Refusal reason={refusal} />
  }
  if (queue === null) {
    return <Waiting label="Loading the waiting list…" bars={['100%', '70%']} />
  }
  if (queue.length === 0) {
    return <p className="offer__queue-empty">Nobody is waiting for this one.</p>
  }
  return (
    <ol className="offer-queue">
      {queue.map((place) => (
        <li className="offer-queue__place" key={place.customerId}>
          <span className="offer-queue__where">{place.position}</span>
          {/* The name the backend sent, and the identifier when it has none to send: a customer
              who is no longer on file still holds a place, and a blank row would be a place
              nobody could account for. */}
          <span className="offer-queue__who">
            {place.customerName ?? `customer ${place.customerId}`}
          </span>
          <span className="offer-queue__since">joined {asAMoment(place.joinedAt)}</span>
        </li>
      ))}
    </ol>
  )
}

/**
 * The form that corrects an offer, including one that is already on sale — which is the point of
 * it: a typo on a live reward should be a correction rather than an incident, and taking the
 * reward off the screen to fix a word would be the incident.
 *
 * <p>Only what was actually changed is sent. Absence means "leave it alone" at the other end, so a
 * form that posted all four fields every time would be rewriting three of them with themselves —
 * which reads identically today and stops reading identically the moment two people have this
 * page open.
 *
 * <p>There is no field for the code, and that is deliberate rather than forgotten. An offer's code
 * is what every voucher already issued names it by, so it cannot change; a box that let somebody
 * type a new one and then refused them would be a screen offering something it cannot do.
 */
function ChangingAnOffer({
  offer,
  onChanged,
  onRefused,
}: {
  offer: AdministeredOffer
  onChanged: (changed: AdministeredOffer) => void
  onRefused: (reason: string) => void
}) {
  const [title, setTitle] = useState(offer.title)
  const [description, setDescription] = useState(offer.description ?? '')
  const [costInPoints, setCostInPoints] = useState(String(offer.costInPoints))
  const [voucherPrefix, setVoucherPrefix] = useState(offer.voucherPrefix)
  const [opensOn, setOpensOn] = useState(offer.opensOn ?? '')
  const [closesOn, setClosesOn] = useState(offer.closesOn ?? '')
  const [voucherValidForDays, setVoucherValidForDays] = useState(
    offer.voucherValidForDays === null ? '' : String(offer.voucherValidForDays),
  )
  const [minimumStreakWeeks, setMinimumStreakWeeks] = useState(
    offer.minimumStreakWeeks === null ? '' : String(offer.minimumStreakWeeks),
  )
  const [requiresBadge, setRequiresBadge] = useState(offer.requiresBadge ?? '')
  const [minimumLifetimePointsEarned, setMinimumLifetimePointsEarned] = useState(
    offer.minimumLifetimePointsEarned === null
      ? ''
      : String(offer.minimumLifetimePointsEarned),
  )
  const [maxPerCustomer, setMaxPerCustomer] = useState(
    offer.maxPerCustomer === null ? '' : String(offer.maxPerCustomer),
  )
  const [maxPerCustomerPerWeek, setMaxPerCustomerPerWeek] = useState(
    offer.maxPerCustomerPerWeek === null ? '' : String(offer.maxPerCustomerPerWeek),
  )
  const [stock, setStock] = useState(offer.stock === null ? '' : String(offer.stock))
  const [discountedCostInPoints, setDiscountedCostInPoints] = useState(
    offer.discountedCostInPoints === null ? '' : String(offer.discountedCostInPoints),
  )
  const [discountOpensOn, setDiscountOpensOn] = useState(offer.discountOpensOn ?? '')
  const [discountClosesOn, setDiscountClosesOn] = useState(offer.discountClosesOn ?? '')
  const [changing, setChanging] = useState(false)

  function change(submitted: FormEvent) {
    submitted.preventDefault()
    const asked: AChangeToAnOffer = {}
    if (title !== offer.title) {
      asked.title = title
    }
    if (description !== (offer.description ?? '')) {
      asked.description = description
    }
    if (costInPoints !== String(offer.costInPoints)) {
      asked.costInPoints = Number(costInPoints)
    }
    if (voucherPrefix !== offer.voucherPrefix) {
      asked.voucherPrefix = voucherPrefix
    }
    // An emptied date box sends '', which the backend reads as "there is no longer such a day" —
    // and that is the whole reason the days are compared against '' rather than against null. A
    // season somebody announced and then called off has to be undoable, and a field that could
    // set a date but never clear one would show an administrator an edit that never happened.
    if (opensOn !== (offer.opensOn ?? '')) {
      asked.opensOn = opensOn
    }
    if (closesOn !== (offer.closesOn ?? '')) {
      asked.closesOn = closesOn
    }
    // Emptying the box sends nothing, because absence means "leave it alone" at the other end and
    // there is no way to say "take the shelf life off" — the backend's own reading, argued out
    // over there. A form that sent something for an emptied box would be inventing the field the
    // backend deliberately does not have.
    if (
      voucherValidForDays.trim() !== '' &&
      voucherValidForDays !== String(offer.voucherValidForDays ?? '')
    ) {
      asked.voucherValidForDays = Number(voucherValidForDays)
    }
    // The three rules read exactly like the shelf life above and unlike the two days: emptying
    // a box sends nothing, because absence means "leave it alone" at the other end and there is
    // no way to say "take the rule off". A form that sent something for an emptied box would be
    // inventing a field the backend deliberately does not have.
    if (
      minimumStreakWeeks.trim() !== '' &&
      minimumStreakWeeks !== String(offer.minimumStreakWeeks ?? '')
    ) {
      asked.minimumStreakWeeks = Number(minimumStreakWeeks)
    }
    if (requiresBadge.trim() !== '' && requiresBadge !== (offer.requiresBadge ?? '')) {
      asked.requiresBadge = requiresBadge.trim()
    }
    if (
      minimumLifetimePointsEarned.trim() !== '' &&
      minimumLifetimePointsEarned !== String(offer.minimumLifetimePointsEarned ?? '')
    ) {
      asked.minimumLifetimePointsEarned = Number(minimumLifetimePointsEarned)
    }
    // The caps read exactly as the shelf life above does, including the part that is a wart:
    // emptying the box sends nothing, because absence means "leave it alone" at the other end
    // and there is no way to say "take the cap off". Raising or lowering one is a number.
    if (maxPerCustomer.trim() !== '' && maxPerCustomer !== String(offer.maxPerCustomer ?? '')) {
      asked.maxPerCustomer = Number(maxPerCustomer)
    }
    if (
      maxPerCustomerPerWeek.trim() !== '' &&
      maxPerCustomerPerWeek !== String(offer.maxPerCustomerPerWeek ?? '')
    ) {
      asked.maxPerCustomerPerWeek = Number(maxPerCustomerPerWeek)
    }
    // A restock, sent as the new total. Emptying the box sends nothing, for the same reason the
    // shelf life above does: absence means "leave it alone" at the other end and there is no way
    // to say "take the limit off", which is the backend's reading and is argued out over there.
    // A figure below what has already gone out comes back as a sentence quoting how many that
    // is, and this page shows it unchanged rather than guessing first.
    if (stock.trim() !== '' && stock !== String(offer.stock ?? '')) {
      asked.stock = Number(stock)
    }
    // The sale price is sent only when it is both filled in and different, like the shelf life
    // above: emptying the box is not how a sale is called off, because a price is not a sale. A
    // sale is called off by emptying both of its days, and the backend takes the price off with
    // them — which is why an emptied price box sends nothing at all rather than something that
    // would contradict the days below it.
    if (
      discountedCostInPoints.trim() !== '' &&
      discountedCostInPoints !== String(offer.discountedCostInPoints ?? '')
    ) {
      asked.discountedCostInPoints = Number(discountedCostInPoints)
    }
    // And the two days are compared against '' rather than null, exactly as the window's are: an
    // emptied date box sends '', which is the instruction that the sale no longer runs then.
    if (discountOpensOn !== (offer.discountOpensOn ?? '')) {
      asked.discountOpensOn = discountOpensOn
    }
    if (discountClosesOn !== (offer.discountClosesOn ?? '')) {
      asked.discountClosesOn = discountClosesOn
    }
    setChanging(true)
    changeAnOffer(offer.code, asked)
      .then(onChanged)
      .catch((problem: Error) => onRefused(problem.message))
      .finally(() => setChanging(false))
  }

  return (
    <form className="offer__form admin-form" onSubmit={change}>
      <div className="field">
        <label htmlFor={`title-${offer.code}`}>Title</label>
        <input
          className="text-input"
          id={`title-${offer.code}`}
          value={title}
          onChange={(typed) => setTitle(typed.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`words-${offer.code}`}>Words on the card</label>
        <textarea
          className="text-input"
          id={`words-${offer.code}`}
          rows={2}
          value={description}
          onChange={(typed) => setDescription(typed.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`cost-${offer.code}`}>Price in points</label>
        <input
          className="text-input"
          id={`cost-${offer.code}`}
          inputMode="numeric"
          value={costInPoints}
          onChange={(typed) => setCostInPoints(typed.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`prefix-${offer.code}`}>Voucher prefix</label>
        <input
          className="text-input"
          id={`prefix-${offer.code}`}
          value={voucherPrefix}
          onChange={(typed) => setVoucherPrefix(typed.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`opens-${offer.code}`}>Opens on</label>
        <input
          className="text-input"
          id={`opens-${offer.code}`}
          type="date"
          value={opensOn}
          onChange={(typed) => setOpensOn(typed.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`closes-${offer.code}`}>Closes on</label>
        <input
          className="text-input"
          id={`closes-${offer.code}`}
          type="date"
          value={closesOn}
          onChange={(typed) => setClosesOn(typed.target.value)}
        />
        <p className="field__note">
          Emptying either box takes that day off the offer. The closing day is the last day it can
          be claimed on.
        </p>
      </div>
      <div className="field">
        <label htmlFor={`shelf-life-${offer.code}`}>Shelf life in days</label>
        <input
          className="text-input"
          id={`shelf-life-${offer.code}`}
          inputMode="numeric"
          value={voucherValidForDays}
          onChange={(typed) => setVoucherValidForDays(typed.target.value)}
        />
        <p className="field__note">
          Vouchers already issued keep the day they were promised; this is what the next ones get.
        </p>
      </div>
      <div className="field">
        <label htmlFor={`streak-${offer.code}`}>Only for a streak of</label>
        <input
          className="text-input"
          id={`streak-${offer.code}`}
          inputMode="numeric"
          value={minimumStreakWeeks}
          onChange={(typed) => setMinimumStreakWeeks(typed.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`badge-${offer.code}`}>Only for holders of</label>
        <input
          className="text-input"
          id={`badge-${offer.code}`}
          value={requiresBadge}
          onChange={(typed) => setRequiresBadge(typed.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`lifetime-${offer.code}`}>Only for points earned in all</label>
        <input
          className="text-input"
          id={`lifetime-${offer.code}`}
          inputMode="numeric"
          value={minimumLifetimePointsEarned}
          onChange={(typed) => setMinimumLifetimePointsEarned(typed.target.value)}
        />
        <p className="field__note">
          Every rule set here has to be met. Emptying a box leaves the rule exactly as it was —
          an offer that should be open to everybody again is one to withdraw and write afresh.
        </p>
      </div>
      <div className="field">
        <label htmlFor={`most-${offer.code}`}>Most one customer may ever have</label>
        <input
          className="text-input"
          id={`most-${offer.code}`}
          inputMode="numeric"
          value={maxPerCustomer}
          onChange={(typed) => setMaxPerCustomer(typed.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`most-a-week-${offer.code}`}>Most one customer may have in a week</label>
        <input
          className="text-input"
          id={`most-a-week-${offer.code}`}
          inputMode="numeric"
          value={maxPerCustomerPerWeek}
          onChange={(typed) => setMaxPerCustomerPerWeek(typed.target.value)}
        />
        <p className="field__note">
          Claims already made are not taken back. Lowering a cap under somebody who has had more
          than it allows stops them having another; it does not owe anybody anything.
        </p>
      </div>
      <div className="field">
        <label htmlFor={`stock-${offer.code}`}>How many there are</label>
        <input
          className="text-input"
          id={`stock-${offer.code}`}
          inputMode="numeric"
          value={stock}
          onChange={(typed) => setStock(typed.target.value)}
        />
        <p className="field__note">
          The total, not how many are left. Raising it puts a sold-out offer back on customers'
          screens straight away; it cannot be set below what has already been claimed.
        </p>
      </div>
      <div className="field">
        <label htmlFor={`sale-price-${offer.code}`}>Sale price in points</label>
        <input
          className="text-input"
          id={`sale-price-${offer.code}`}
          inputMode="numeric"
          value={discountedCostInPoints}
          onChange={(typed) => setDiscountedCostInPoints(typed.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`sale-opens-${offer.code}`}>Sale starts on</label>
        <input
          className="text-input"
          id={`sale-opens-${offer.code}`}
          type="date"
          value={discountOpensOn}
          onChange={(typed) => setDiscountOpensOn(typed.target.value)}
        />
      </div>
      <div className="field">
        <label htmlFor={`sale-closes-${offer.code}`}>Sale ends on</label>
        <input
          className="text-input"
          id={`sale-closes-${offer.code}`}
          type="date"
          value={discountClosesOn}
          onChange={(typed) => setDiscountClosesOn(typed.target.value)}
        />
        <p className="field__note">
          Emptying both of these days calls the sale off, and the sale price comes off with them.
          Claims already made keep what they paid.
        </p>
      </div>
      <Button type="submit" small busy={changing} disabled={changing}>
        Save the change
      </Button>
    </form>
  )
}

/**
 * The rules an offer carries, each in a few words, in the order the backend asks them in.
 *
 * <p>The order matters on this screen and not only in the backend: when a customer fails
 * several, the sentence they are shown names the first one, so an administrator reading this
 * row top to bottom can say which sentence their offer will show without trying it.
 *
 * <p>An empty list is an offer for everybody, and the row draws nothing at all rather than
 * "no rules" — an absence said out loud on every row is an absence nobody reads past.
 */
function theRulesOn(offer: AdministeredOffer): string[] {
  const rules: string[] = []
  if (offer.minimumStreakWeeks !== null) {
    rules.push(
      `a streak of ${offer.minimumStreakWeeks} ${
        offer.minimumStreakWeeks === 1 ? 'week' : 'weeks'
      }`,
    )
  }
  if (offer.requiresBadge !== null) {
    rules.push(`holders of ${offer.requiresBadge}`)
  }
  if (offer.minimumLifetimePointsEarned !== null) {
    rules.push(`${points.format(offer.minimumLifetimePointsEarned)} points earned in all`)
  }
  return rules
}

/** What a state is called on the screen, in the words somebody running the scheme would use. */
function whatStateItIsIn(state: OfferState): string {
  switch (state) {
    case 'DRAFT':
      return 'Draft'
    case 'PUBLISHED':
      return 'On sale'
    case 'WITHDRAWN':
      return 'Withdrawn'
  }
}

/**
 * The one word a counter reads off a voucher that is not good.
 *
 * <p>Which of the three it is matters to the person at the till, because it is what they say out
 * loud: "somebody has already had this" and "this ran out last week" are different conversations
 * with the customer standing in front of them. Only the first can happen today; the other two are
 * named so that the slices which write those states find a screen that already tells the truth.
 */
function verdictFor(voucher: VoucherAtTheCounter): string {
  switch (voucher.state) {
    case 'USED':
      return 'Already used'
    case 'EXPIRED':
      return 'Expired'
    case 'CANCELLED':
      return 'Cancelled'
    default:
      return 'Not good'
  }
}

/**
 * Where one customer stands inside an offer's limits, on the card they are reading.
 *
 * <p><strong>Only while there is still something left.</strong> A card somebody has had their
 * limit of already carries the backend's sentence, which says how many they have had and what
 * the cap was in better words than a counter could; a second line underneath it repeating the
 * arithmetic would be the same fact twice, and the one thing this line is for — telling somebody
 * to hurry — is meaningless once there is nothing to hurry for. So it draws nothing for a locked
 * card and nothing at all for an offer nobody capped, which is every reward this application
 * ships.
 *
 * <p><strong>Nothing here is computed.</strong> How many are left is the backend's subtraction,
 * arriving as a number, for the reason the price and the lock already arrive that way: with two
 * caps running there are two remainders, only the tighter of them is true, and working out which
 * would need this page to know where the application's weeks begin. What is left here is
 * choosing the words for a number somebody else worked out.
 *
 * <p>The cap is named beside the remainder because "1 left" on its own is a fact with no shape.
 * "1 of 2 left" says what kind of offer this is, which is the thing that decides whether
 * somebody claims it today.
 */
function WhatIsLeftOfYourLimit({ reward }: { reward: RewardForACustomer }) {
  if (!reward.claimable || reward.howManyYouMayStillHave === null) {
    return null
  }
  const left = reward.howManyYouMayStillHave
  // The tighter cap is the one the remainder was worked out against, so it is the one named
  // beside it: saying "1 of 5 left" about somebody who may have one more this week would be
  // two true numbers making one false sentence.
  const cap = reward.maxPerCustomerPerWeek ?? reward.maxPerCustomer
  return (
    <p className="reward__limit">
      {cap === null ? `${left} left` : `${left} of ${cap} left`}
      {reward.maxPerCustomerPerWeek !== null && ' this week'}
      {reward.howManyYouHaveHad > 0 && ` · you have had ${reward.howManyYouHaveHad}`}
    </p>
  )
}

/**
 * How many of a scarce reward are left, on the card, and nothing at all on one that never runs
 * out.
 *
 * <p><strong>Null and nought are opposite facts and this is the component that keeps them
 * apart.</strong> Null is the absence of scarcity — no limit was ever set, which is what all four
 * of the rewards this application has always had say — and putting "0 left" on those cards would
 * be the page inventing a shortage. Nought is a real answer and is deliberately silent here too,
 * because an offer with none left is already locked with the backend's own sentence saying it has
 * sold out, and a second line underneath saying "0 left" would be the same news twice.
 *
 * <p>What is left is the backend's figure and never this page's arithmetic. It is the stock
 * somebody set less every claim already made, which is a count of rows this page does not have
 * and could not be trusted with — the same reason the price is the backend's.
 *
 * <p>Urgent below a handful, plainer above it. "2 left" is the whole reason a customer hurries
 * and "40 left" is not, so the last few are marked; where the line falls is a presentation
 * decision and lives here rather than in the backend, which reports a number and says nothing
 * about how alarming it is.
 */
function WhatIsLeft({ whatIsLeft }: { whatIsLeft: number | null }) {
  if (whatIsLeft === null || whatIsLeft <= 0) {
    return null
  }
  return (
    <p className={whatIsLeft <= THE_LAST_FEW ? 'reward__left is-scarce' : 'reward__left'}>
      {whatIsLeft === 1 ? 'Only one left' : `${points.format(whatIsLeft)} left`}
    </p>
  )
}

/**
 * The caps as somebody running the scheme set them, on the row in the back office.
 *
 * <p>What they set, read back, so that a number they can see is a number they can correct —
 * which is the argument the voucher prefix and the shelf life already make on this same row.
 * Deliberately not how many anybody has had: that is a fact about a customer, it is different
 * for every one of them, and a back-office row reporting one person's count would be reporting
 * whichever person happened to be asked about.
 *
 * <p>Nothing at all when neither cap is set, because "no limit" is the ordinary case and saying
 * it on every row would bury the rows where it matters.
 */
function TheCapsOnAnOffer({ offer }: { offer: AdministeredOffer }) {
  if (offer.maxPerCustomer === null && offer.maxPerCustomerPerWeek === null) {
    return null
  }
  return (
    <p className="offer__limits">
      {offer.maxPerCustomer !== null && <>At most {offer.maxPerCustomer} per customer</>}
      {offer.maxPerCustomer !== null && offer.maxPerCustomerPerWeek !== null && <> · </>}
      {offer.maxPerCustomerPerWeek !== null && <>{offer.maxPerCustomerPerWeek} a week</>}
    </p>
  )
}

/** How few is few enough to say so loudly. A word about the screen, not a rule about the scheme. */
const THE_LAST_FEW = 5

/**
 * What an offer usually costs, struck through beside what it costs today — and nothing at all
 * when there is no sale on.
 *
 * <p><strong>Drawn by asking whether the figure is there, never by comparing two figures.</strong>
 * `costInPoints` is always what this customer would be charged; `ordinaryCostInPoints` is null
 * unless a sale is running. So the presence of the second number is the whole of the decision,
 * and the page performs no arithmetic — no subtraction, no percentage, no "is this one bigger
 * than that one". A card that worked the saving out for itself would be a second copy of the
 * pricing rule living at this end of the wire, and the second copy is always the one that goes
 * out of date the week somebody changes the first.
 *
 * <p>A component of its own rather than four lines inside the card, because the price is the one
 * place on that card where two things are being said at once and it is worth being able to read
 * the rule for it in one place. `aria-label` rather than a visually hidden span: a struck-through
 * number read out as a number is a screen reader telling somebody they pay the higher price.
 */
function WhatItUsuallyCosts({ reward }: { reward: RewardForACustomer }) {
  if (reward.ordinaryCostInPoints === null) {
    return null
  }
  return (
    <s className="reward__was" aria-label={`usually ${reward.ordinaryCostInPoints} points`}>
      {points.format(reward.ordinaryCostInPoints)}
    </s>
  )
}

/**
 * Revoking a voucher: the code printed on it, the reason it is being revoked, and one press.
 *
 * <p><strong>On the administration screen and deliberately not on the counter's.</strong> Both
 * act on a voucher by its code, and that is where the resemblance stops. A till checks a voucher
 * and hands it over, which are the two things a person standing at a counter may do; cancelling
 * moves points out of the scheme and back into somebody's pot, and putting it behind the same
 * screen would mean that the day anybody puts a sign-in in front of these surfaces, "staff" would
 * have to be split in two before it could be locked.
 *
 * <p><strong>The reason is a field and not a formality.</strong> The backend refuses a
 * cancellation without one and this form does not pre-empt that refusal — a rule enforced in two
 * places is a rule that will one day be enforced differently in each — but the button stays
 * disabled until both boxes have something in them, because a press that could only ever be
 * refused is a press nobody should be invited to make. What is typed here is what the customer
 * reads on their own page and what a counter reads out loud to them, which is worth knowing while
 * typing it.
 *
 * <p><strong>Nothing here is worked out.</strong> Whether the code names a voucher, whether that
 * voucher can still be cancelled and how many points go back are all the backend's answers. What
 * comes back is the voucher as it now reads — the same shape the till sees — so the confirmation
 * below is the state that was actually written rather than the state this page hoped for.
 *
 * <p>The code box asks for no autocorrect, no capitalisation of its own and no spellcheck, for
 * the reason the counter's does: every one of them is a keyboard improving a voucher code into a
 * different one.
 */
function CancellingAVoucher({ onCancelled }: { onCancelled: () => void }) {
  // What a refunded batch will be good for, off the scheme rather than out of the sentence below.
  // This one is a promise made to a member of staff about to take something back off a customer,
  // which makes it the worst of the three to have had a number written into it.
  const pointsLast = useTheSchemeInForce()?.howLongABatchOfPointsLasts ?? null
  const [typed, setTyped] = useState('')
  const [reason, setReason] = useState('')
  const [working, setWorking] = useState(false)
  const [refusal, setRefusal] = useState<string | null>(null)
  const [cancelled, setCancelled] = useState<VoucherAtTheCounter | null>(null)

  function cancel(event: FormEvent) {
    event.preventDefault()
    setWorking(true)
    setRefusal(null)
    // The previous confirmation goes before the new answer arrives. A code that comes back
    // refused must not leave the last customer's cancellation sitting there looking like an
    // answer to it.
    setCancelled(null)
    cancelVoucher(typed, reason)
      .then((revoked) => {
        setCancelled(revoked)
        setTyped('')
        setReason('')
        onCancelled()
      })
      .catch((problem: Error) => setRefusal(problem.message))
      .finally(() => setWorking(false))
  }

  return (
    <form className="card cancelling" onSubmit={cancel}>
      <h2>Cancel a voucher</h2>
      <p className="cancelling__what">
        The customer gets their points back as a fresh batch
        {pointsLast === null ? '' : ` with ${inMonths(pointsLast)} of its own`}, and the thing goes
        back in the window. It cannot be undone.
      </p>

      <div className="field">
        <label htmlFor="cancelVoucherCode">Voucher code</label>
        <input
          className="text-input cancelling__code"
          id="cancelVoucherCode"
          name="cancelVoucherCode"
          type="text"
          inputMode="text"
          autoComplete="off"
          autoCorrect="off"
          autoCapitalize="characters"
          spellCheck={false}
          placeholder="SS-CIN-7F3K2Q"
          value={typed}
          onChange={(event) => setTyped(event.target.value)}
        />
      </div>

      <div className="field">
        <label htmlFor="cancelReason">Why is it being cancelled?</label>
        <input
          className="text-input"
          id="cancelReason"
          name="cancelReason"
          type="text"
          autoComplete="off"
          placeholder="Claimed twice by mistake at the Leuven desk."
          value={reason}
          onChange={(event) => setReason(event.target.value)}
        />
      </div>

      <Button
        type="submit"
        block
        busy={working}
        disabled={working || typed.trim() === '' || reason.trim() === ''}
      >
        {working ? 'Cancelling…' : 'Cancel the voucher'}
      </Button>

      {refusal !== null && <Refusal reason={refusal} />}

      {cancelled !== null && (
        <p className="flash">
          <TicketIcon />
          Cancelled {cancelled.voucherCode} — {points.format(cancelled.pointsSpent)} points back to{' '}
          {cancelled.customerName ?? `customer ${cancelled.customerId}`}
        </p>
      )}
    </form>
  )
}

/**
 * The deadline on the one card being kept for this customer: the moment their seventy-two hours
 * run out.
 *
 * <p>Null for every other card and every other customer, which is nearly all of them, and the
 * absence is the whole of the decision — a page that drew this from `whatIsLeft` or from
 * `claimable` would be guessing at something the backend states.
 *
 * <p><strong>A moment, and not a countdown, and the reason is the one this whole page is built
 * around.</strong> It used to say "Yours for another 2 days", worked out here as
 * `lapsesAt − Date.now()`, and that is the one thing a page in this application may never do:
 * `Date.now()` is the machine's clock and the application has one of its own, which a trainer
 * winds forward and which every date the backend sends is measured against. The first time
 * anybody looked at this card in a browser, the demonstration database was a fortnight ahead of
 * the machine and a hold with three hours under three days left read <em>"Yours for another 16
 * days"</em> — a sentence the helper's own comment said could not happen, because a hold is
 * seventy-two hours long and the three shapes it could take were days, hours and "less than an
 * hour". A customer told they had a fortnight would come back on the fourth day to a hold that
 * had lapsed on the second.
 *
 * <p>So the page does no arithmetic about time at all, and says the deadline the backend sent it,
 * in the customer's own zone: the rule the anniversary line and the month-ahead figures already
 * state at length, which is that nothing here calls `new Date()` for today. It is also word for
 * word what the bell already says when the hold is raised — "it is yours until …" — and one
 * sentence in two places beats two sentences that can disagree.
 *
 * <p>There is no "your hold has run out" reading any more either, because there is no longer a
 * clock here to notice. Nor was there ever a use for one: what a customer is holding is derived
 * on every read against the application's clock and a lapsed hold is not in the answer, so the
 * only way this component ever saw a deadline in the past was by measuring it against the wrong
 * clock.
 */
function YourHold({ lapsesAt }: { lapsesAt: string | null }) {
  if (lapsesAt === null) {
    return null
  }
  return <p className="reward__hold">Yours until {asAMoment(lapsesAt)}</p>
}

/**
 * What is inside a bundle, listed under whatever card is showing one — and nothing at all for
 * an offer that is not one.
 *
 * <p><strong>One component for three screens, which is unusual on this page and deliberate
 * here.</strong> A customer choosing a bundle, an administrator checking what they composed and
 * somebody at a till putting it on the counter are all asking the same question — what is in
 * it — and all three are answered by the same list from the same backend record. The three
 * cards around it are separate because they answer different questions; this is the one part
 * they genuinely share, and three copies of it would be three places for the wording to drift.
 *
 * <p><strong>Drawn from the list rather than from a kind.</strong> There is no "is this a
 * bundle" flag on the wire and this page does not want one: an offer with contents is a bundle,
 * an empty list draws nothing, and every offer this application has always had draws nothing.
 * That also means a page that had never heard of bundles would render one correctly minus this
 * list, which is the property the whole feature is built on.
 *
 * <p>The quantity is drawn for every line, including one of something. "1 × A bag of popcorn"
 * beside "2 × A cinema seat" is a column somebody reads down; a bare title on the first line
 * would make the reader work out whether the number was missing or the one was implied.
 *
 * <p>The icon comes from the same map the reward cards use, which falls back to a generic gift
 * for a code it has not heard of — the one place this application hardcodes catalogue codes,
 * and the fallback is what makes any new member render without a frontend change.
 */
function WhatIsInABundle({ contents }: { contents: BundleMember[] }) {
  if (contents.length === 0) {
    return null
  }
  return (
    <ul className="bundle">
      {contents.map((member) => (
        <li className="bundle__line" key={member.code}>
          <span className="bundle__icon" aria-hidden="true">
            <RewardIcon code={member.code} />
          </span>
          <span className="bundle__how-many">{member.quantity} ×</span>
          <span className="bundle__what">{member.title}</span>
        </li>
      ))}
    </ul>
  )
}

/**
 * Where this customer stands in the queue for one card, and nothing at all on every other.
 *
 * <p>Null for nearly every card and nearly every customer, and the absence is the whole of the
 * decision: a page that inferred a queue from `whatIsLeft` being nought, or from the card being
 * locked, would be guessing at something the backend states — most people looking at a sold-out
 * card are not in the line for it.
 *
 * <p><strong>Ordinal words rather than a bare number.</strong> "You are 1 in the queue" is a
 * figure somebody has to interpret; "You are first in the queue" is a sentence. Only the first
 * three are written out, because those are the ones worth reading as words and the rest are
 * better as a number somebody can compare with last week's — "you are 9th in the queue" says
 * exactly what it is.
 *
 * <p>It says nothing about when their turn might come, deliberately. Nothing in this
 * application can know: it depends on somebody restocking, on somebody else's hold running out
 * or on a voucher being cancelled, and a page that guessed would be making the one promise this
 * feature is careful never to make.
 */
function YourPlaceInTheQueue({ position }: { position: number | null }) {
  if (position === null) {
    return null
  }
  return (
    <p className="reward__queue">
      You are {whereThatIsInALine(position)} in the queue — we will put one aside for you when
      your turn comes.
    </p>
  )
}

/**
 * A position in a line, in the fewest words that read as a place rather than as a count.
 *
 * <p>Three words and then digits. Written out where writing it out helps and left as an ordinal
 * where it does not: the coarsest form that is still useful, and no library for it.
 */
function whereThatIsInALine(position: number): string {
  switch (position) {
    case 1:
      return 'first'
    case 2:
      return 'second'
    case 3:
      return 'third'
    default:
      return `${position}th`
  }
}

/**
 * A moment from the backend, written out as a day and a time somebody reads.
 *
 * <p>Beside {@link asADay} rather than instead of it, because the two are different promises.
 * Almost every deadline this application sends is a calendar date the backend has already
 * decided in the one zone it counts calendars in, and {@link asADay} deliberately does not
 * convert it. A hold's deadline is not one of those: it is seventy-two hours from an instant, it
 * is sent as an instant, and it genuinely should be rendered in the zone of whoever is reading —
 * which is what `new Date` on an instant does on its own.
 *
 * <p>To the minute and no further. The seconds of a three-day deadline are precision nobody can
 * act on, and a line that changed while somebody read it would be worse than one that did not.
 */
const dayAndTime = new Intl.DateTimeFormat('nl-BE', { dateStyle: 'long', timeStyle: 'short' })

function asAMoment(moment: string): string {
  return dayAndTime.format(new Date(moment))
}

/**
 * The notice standing on a savings account: what the agreement asks for, what is ready to take
 * today, what is still counting down, and the box that starts the clock on more.
 *
 * <p><strong>Absent entirely for an account with nothing to give notice of.</strong> The backend
 * answers this question for every savings account and says `noticeDays: 0` for the ones that ask
 * for none, which is what lets this component be rendered unconditionally and decide for itself
 * whether there is a panel to draw. Free savings and the core saver get no panel, no heading and no
 * empty state — the page reads exactly as it did before notice accounts existed.
 *
 * <p><strong>It reads for itself rather than taking the notice from the account above.</strong> The
 * same arrangement the goals panel is in, and for the same reason: two reads that fail
 * independently are two things that can still be shown, and a page that folded this into the
 * account's own read would hide the balance whenever the notice could not be loaded.
 *
 * <p><strong>Above the forms that move money</strong>, because "EUR 250 of this is ready and EUR
 * 300 of it is not" has to be read before an amount is typed into the withdrawal box — the same
 * position, and the same argument, as what the goals have claimed.
 *
 * <p><strong>Nothing here works out whether a notice is ready.</strong> `ready` and `daysLeft`
 * arrive decided, against the application's own clock, which is the clock a trainer has wound
 * forward — a page counting days from `readyOn` would disagree with the backend on exactly the
 * morning the money comes free, and would disagree with it permanently on a wound clock.
 */
function TheNoticeYouHaveGiven({
  savingsAccountId,
  afterMoneyMoved,
}: {
  savingsAccountId: number
  /** Bumped by the page whenever money has moved, because a withdrawal spends notice. */
  afterMoneyMoved: number
}) {
  const [standing, setStanding] = useState<TheNoticeOnAnAccount | null>(null)
  const [readError, setReadError] = useState<string | null>(null)
  const [amount, setAmount] = useState('')
  const [giving, setGiving] = useState(false)
  // The refusal from the box, kept apart from the one about reading: a notice this bank would not
  // give is something the customer typed, and it belongs beside what they typed it into.
  const [givingError, setGivingError] = useState<string | null>(null)
  const [cancelling, setCancelling] = useState<number | null>(null)

  const read = useCallback(
    (signal?: AbortSignal): Promise<void> =>
      fetchTheNoticeOn(savingsAccountId, signal)
        .then((notice) => {
          if (signal?.aborted !== true) {
            setStanding(notice)
            setReadError(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true) {
            setReadError(problem.message)
          }
        }),
    [savingsAccountId],
  )

  useEffect(() => {
    const abort = new AbortController()
    void read(abort.signal)
    return () => abort.abort()
  }, [read, afterMoneyMoved])

  function give(event: FormEvent) {
    event.preventDefault()
    setGiving(true)
    setGivingError(null)
    giveNoticeOn(savingsAccountId, amount)
      .then(() => {
        setAmount('')
        return read()
      })
      .catch((problem: Error) => setGivingError(problem.message))
      .finally(() => setGiving(false))
  }

  function cancel(noticeId: number) {
    setCancelling(noticeId)
    setGivingError(null)
    cancelANotice(savingsAccountId, noticeId)
      .then(() => read())
      .catch((problem: Error) => setGivingError(problem.message))
      .finally(() => setCancelling(null))
  }

  // Nothing at all until the first read has answered, rather than a skeleton. Almost every account
  // in this application has no notice period, so a placeholder would be a panel appearing and
  // vanishing on every savings account page — which is worse than a panel that arrives a moment
  // late on the few that have one.
  if (standing === null) {
    return readError === null ? null : <Refusal reason={readError} standing />
  }
  if (standing.noticeDays === 0) {
    return null
  }

  return (
    <article className="card reveal">
      <h2 className="card__title">Notice</h2>
      <p className="rule">
        This account asks for {standing.noticeDays} days’ notice. Tell us what you will need, wait{' '}
        {standing.noticeDays} days, and it is yours — until then it stays where it is.
      </p>

      <dl className="split">
        <div>
          <dt>Ready to take today</dt>
          <dd>{euros.format(standing.readyToTakeToday)}</dd>
        </div>
        <div>
          <dt>Still counting down</dt>
          <dd>{euros.format(standing.stillWaiting)}</dd>
        </div>
      </dl>

      {standing.notices.length === 0 ? (
        <p className="nothing">
          You have given notice on nothing, so nothing can leave this account today.
        </p>
      ) : (
        <ul className="given-notices">
          {standing.notices.map((notice) => (
            <li key={notice.id} className={notice.ready ? 'given-notice is-ready' : 'given-notice'}>
              <span className="given-notice__amount">{euros.format(notice.stillStanding)}</span>
              <span className="given-notice__when">
                {notice.ready
                  ? `Ready since ${asADay(notice.readyOn)}`
                  : `${notice.daysLeft} ${notice.daysLeft === 1 ? 'day' : 'days'} left — ready on ${asADay(notice.readyOn)}`}
                {/* What it was given on, said only when a withdrawal has already spent part of
                    it: otherwise it is the same figure twice, and the card would be explaining
                    arithmetic nobody did. */}
                {notice.stillStanding !== notice.amount &&
                  ` · given on ${euros.format(notice.amount)}`}
              </span>
              <button
                type="button"
                className="link link--small"
                disabled={cancelling === notice.id}
                onClick={() => cancel(notice.id)}
              >
                {cancelling === notice.id ? 'Cancelling…' : 'Cancel'}
              </button>
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={give}>
        <EuroAmount
          id="noticeAmount"
          label="Give notice on"
          value={amount}
          onType={setAmount}
        />
        <Button type="submit" block busy={giving} disabled={giving}>
          {giving ? 'Giving notice…' : 'Give notice'}
        </Button>
        {givingError !== null && <Refusal reason={givingError} />}
      </form>

      {readError !== null && <Refusal reason={readError} />}
    </article>
  )
}

/**
 * What this account has been paid, month by month: the period, the balance it was worked out on,
 * the rate it was paid at, and what it came to.
 *
 * <p><strong>The arithmetic is on the page on purpose.</strong> A panel saying "EUR 0,50" is a
 * figure the customer has to take on trust; "20 Jan – 19 Feb, averaging EUR 1.200,00, at 0,50% a
 * year" is a sentence they can redo — a twelfth of the rate, on that balance. In an application
 * whose whole subject is how saving is rewarded, a reward nobody can check is the one thing worth
 * not shipping.
 *
 * <p><strong>The average balance rather than the balance today</strong>, because that is what the
 * month was actually paid on, and it is the only figure that explains a small payment on a large
 * balance: money that arrived on the last day of the month was there for one day of it. A panel
 * that showed the closing balance beside the interest would make every one of those look like a
 * mistake.
 *
 * <p><strong>The rate shown is this account's own, and it comes down with each month rather than
 * from the catalogue.</strong> An account opened before its product was repriced goes on being
 * paid at the rate it was written with, and a month paid last year was paid at whatever the
 * agreement said then — so the rate belongs on the row rather than in a heading over all of them.
 * The version number is beside it, which is the address of the agreement that decided it.
 *
 * <p>The period is printed as its days rather than as a month's name, because a period runs from
 * the day the account was opened and almost never lines up with a calendar month. The last day is
 * the day before the period ended, since the backend sends the day it ended and that day belongs
 * to the next one.
 *
 * <p>Newest first, because a customer opening this wants the month that just paid. The backend
 * sends them oldest first, which is the order a story reads in and the order every other list here
 * arrives in, so the reversing happens here where the reason for it is a reason about a screen.
 *
 * <p><strong>The bonus is marked on each month, and only on an account that has one to earn.</strong>
 * That is the whole of what ticket 05 left here, and it is why the agreement comes down with the
 * list. `bonusEarned` has always been on every row, but `false` says two different things — "the
 * floor was broken this month" and "this product never offered a bonus" — and a panel that printed
 * it without knowing which would put "bonus not earned" on every month of free savings, which is a
 * reproach for something nobody was ever offered. The agreement is what tells the two apart: a
 * floor to keep is a bonus to earn.
 *
 * <p>A month that earned it is marked and so is a month that did not, rather than only the good
 * news. The row also carries the lowest day the walk found, which is the figure the judgement was
 * made on and the one that explains a month paid less than the one before it on the same balance —
 * the whole panel exists so somebody can redo the sum, and half a reason is not a reason.
 *
 * <p>Nothing here decides whether the bonus was earned: `bonusEarned` arrives decided by the sweep
 * that paid the month, against the floor as it stood then, and `annualRatePercent` already has the
 * bonus in it on a month that earned one. A page comparing the lowest balance with today's floor
 * would disagree with the backend about every month paid before the account took newer terms.
 *
 * <p>Nothing at all until the first month has been judged. An empty panel headed "Interest" on an
 * account opened this morning would read as something that had failed to load, and the honest
 * thing to say about a month that has not ended is nothing — and the heading goes with it, which is
 * why the heading is drawn here rather than by the page. A heading standing over nothing is the
 * same empty furniture the panel is careful not to be.
 */
function WhatInterestHasBeenPaid({
  interest,
  agreement,
}: {
  interest: InterestPaid[]
  /**
   * The agreement these months were paid under, for the one thing the rows cannot say by
   * themselves: whether there was a bonus on offer at all.
   */
  agreement: TheAgreement | null
}) {
  if (interest.length === 0) {
    return null
  }
  const newestFirst = [...interest].reverse()
  const paidAltogether = interest.reduce((total, month) => total + month.interest, 0)
  // A floor to keep is a bonus to earn — the sentence the agreement panel above already prints — and
  // a month that was actually paid one settles it for an account whose floor is nought. Read as an
  // "or" because the two answer for different accounts: the floor answers for a core saver that has
  // dipped every month, and a paid month answers for a product that offers a bonus with nothing to
  // keep in, which ticket 05 argues is paid every month.
  const aBonusIsOnOffer =
    (agreement !== null && agreement.minimumBalance > 0) ||
    interest.some((month) => month.bonusEarned)
  return (
    <>
      <div className="section-head">
        <h2>What it has paid</h2>
      </div>
      <article className="card reveal">
        <h2 className="card__title">Interest</h2>
        <p className="rule">
          Every month, this account is paid a twelfth of its rate on the average balance it held
          across that month — so money that arrives on the last day earns one day, and the interest
          itself stays in the account and is in next month's average.
        </p>
        <p className="amount amount--lg">{euros.format(paidAltogether)}</p>
        <p className="tl__meta">
          {interest.length === 1 ? 'paid over one month' : `paid over ${interest.length} months`}
        </p>
        <ul className="interest">
          {newestFirst.map((month, place) => (
            <li key={month.periodOrdinal} className="interest__row" style={rowDelay(place)}>
              <div className="interest__body">
                <p className="interest__period">
                  {asADay(month.from)} – {asADay(lastDayOf(month.until))}
                </p>
                {/* The two figures the payment was made of, in the order the sentence needs them:
                    what the account held on average, and what the account's own terms pay on it. The
                    lowest day joins them only on an account with a bonus to earn, because that is
                    the only account it decided anything for — on the other three it would be a
                    figure printed because it happened to be in the response. */}
                <span className="interest__how">
                  averaging {euros.format(month.averageDailyBalance)}
                  {aBonusIsOnOffer && ` · lowest day ${euros.format(month.lowestDailyBalance)}`} ·{' '}
                  {asAPercentage(month.annualRatePercent)} a year · terms v{month.termsVersion}
                </span>
                {/* Said in words and coloured, never by the colour alone, and drawn for both answers
                    rather than only the good one: a month with no mark at all would be a month whose
                    bonus nobody had looked at. */}
                {aBonusIsOnOffer && (
                  <span
                    className={
                      month.bonusEarned
                        ? 'interest__bonus interest__bonus--earned'
                        : 'interest__bonus interest__bonus--lost'
                    }
                  >
                    {month.bonusEarned ? 'bonus earned' : 'bonus not earned'}
                  </span>
                )}
              </div>
              <span className="interest__amount">{euros.format(month.interest)}</span>
            </li>
          ))}
        </ul>
      </article>
    </>
  )
}


/**
 * The catalogue on the overview: what this bank sells, what each product pays, what each asks for,
 * and one press to open an account on any of them.
 *
 * <p><strong>On the overview rather than on a screen of its own, and under the accounts rather than
 * above them.</strong> It is the answer to the question the grid above it has just raised — these
 * are the accounts you hold, and this is what else there is — and choosing a product is the first
 * decision a saver makes. A decision nobody can find is a decision nobody makes, which is what a
 * page behind a link in a footer would have been.
 *
 * <p><strong>What each pays and what each asks are on the same line, because that is the
 * weighing.</strong> A rate on its own is a number to compare; a rate beside "32 days' notice
 * before money leaves" is a choice. The condition is built from the figures rather than from the
 * kind, exactly as the agreement panel builds its sentence — one reading covers all four products,
 * and a fifth changes nothing here.
 *
 * <p><strong>A product closed to new accounts is drawn and cannot be pressed.</strong> The backend
 * sends it because customers are holding it, and hiding it would leave somebody with an account
 * whose product this page would not admit to selling. What it must not do is offer an agreement
 * nobody will sign, so the card says the door is shut instead of carrying a button — and if the
 * press happens anyway, from a screen that has gone stale, the backend's own sentence is what
 * appears.
 *
 * <p>Its own read rather than the accounts', because it is this panel's subject and nothing else on
 * the overview wants it. A failed read says so in the backend's words, like every other read here.
 */
function OpenASavingsAccount({
  customerId,
  onOpened,
  onCompare,
}: {
  customerId: number
  onOpened: (savingsAccountId: number) => void
  onCompare: () => void
}) {
  const [products, setProducts] = useState<SavingsProduct[] | null>(null)
  const [catalogueError, setCatalogueError] = useState<string | null>(null)
  const [opening, setOpening] = useState<string | null>(null)
  const [refused, setRefused] = useState<string | null>(null)

  useEffect(() => {
    const request = new AbortController()
    fetchSavingsProducts(request.signal)
      .then((shelf) => {
        if (!request.signal.aborted) {
          setProducts(shelf)
          setCatalogueError(null)
        }
      })
      .catch((problem: Error) => {
        if (!request.signal.aborted) {
          setCatalogueError(problem.message)
        }
      })
    return () => request.abort()
  }, [])

  function open(product: SavingsProduct) {
    setOpening(product.code)
    setRefused(null)
    openASavingsAccount(customerId, product.code)
      .then((opened) => {
        setOpening(null)
        onOpened(opened.id)
      })
      .catch((problem: Error) => {
        setOpening(null)
        setRefused(problem.message)
      })
  }

  return (
    <>
      <div className="section-head">
        <h2>Open a savings account</h2>
      </div>

      {catalogueError !== null && <Refusal reason={catalogueError} standing />}
      {refused !== null && <Refusal reason={refused} />}

      {products === null && catalogueError === null && (
        <div className="card">
          <Waiting label="Loading the savings products…" bars={['8rem', '100%', '70%']} />
        </div>
      )}

      {products !== null && (
        <ul className="choices">
          {products.map((product, place) => (
            <li
              key={product.code}
              className={`choice${product.openToNewAccounts ? '' : ' choice--closed'}`}
              style={rowDelay(place)}
            >
              <div className="choice__head">
                <h3 className="choice__title">{product.name}</h3>
                <span className="choice__rate">
                  {asAPercentage(product.currentTerms.annualRatePercent)} a year
                </span>
              </div>
              <p className="choice__asks">{whatThisProductAsksFor(product)}</p>
              <p className="rule">{product.description}</p>
              <div className="choice__doing">
                {/* What the account would be written under, said before the press rather than
                    after it: the version is what the agreement will name for as long as the
                    account lives, and a rate change published tomorrow does not touch it. */}
                <span className="choice__version">
                  Version {product.currentTerms.version} · {whatShapeOfAgreement(product.kind)}
                </span>
                {product.openToNewAccounts ? (
                  <Button
                    small
                    busy={opening === product.code}
                    disabled={opening !== null}
                    onClick={() => open(product)}
                  >
                    Open an account
                  </Button>
                ) : (
                  <span className="choice__shut">Closed to new accounts</span>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {/* The way into the comparison, under the cards rather than above them: this panel says
          what there is and that is usually enough, and the screen behind this link is for the
          customer who wants the difference between four rates to be a figure rather than an
          adjective. A link rather than a button, because nothing happens to anybody's money. */}
      <p className="rule">
        You can hold as many of these as you like, on as many products as you like — money you might
        need next week beside money you are not touching for a year. Each account keeps the terms it
        was opened under, whatever the rates do afterwards.{' '}
        <button type="button" className="link link--small" onClick={onCompare}>
          Compare what each would pay you
        </button>
      </p>
    </>
  )
}

/**
 * What a product asks of whoever opens an account on it, in one line built from the figures.
 *
 * <p>The same construction {@link whatThisAgreementAsksFor} makes about an agreement, and
 * deliberately not the same function: that one is about the account somebody holds and says "this
 * agreement asks for", and this one is about an offer nobody has taken yet. They read differently
 * because they are different sentences, and folding them together would have one of the two
 * addressing the customer about an account they do not have.
 */
function whatThisProductAsksFor(product: SavingsProduct): string {
  const asked: string[] = []
  if (product.currentTerms.noticeDays > 0) {
    asked.push(`${product.currentTerms.noticeDays} days' notice before money leaves`)
  }
  if (product.currentTerms.minimumBalance > 0) {
    asked.push(`${euros.format(product.currentTerms.minimumBalance)} kept in for the bonus rate`)
  }
  if (product.currentTerms.termMonths > 0) {
    asked.push(`the money left in place for ${product.currentTerms.termMonths} months`)
  }
  return asked.length === 0
    ? 'Asks nothing of you: money in and out whenever you like.'
    : `Asks for ${asked.join(', and ')}.`
}

/**
 * Closing this savings account, and what it says once it has been closed.
 *
 * <p><strong>Closed is ended, not deleted, and this card says so where somebody is about to press
 * it.</strong> Everything on the page under it — the deposits, the withdrawals, the goals, the
 * rules and the agreement itself — goes on reading afterwards. A button that read "Delete" would be
 * promising the opposite of what happens, and the sentence beside it is the one that stops somebody
 * pressing this to make a record go away.
 *
 * <p><strong>Nothing is checked here first.</strong> Whether an account may be closed is the
 * backend's rule, and an account with money still in it comes back refused with the figure in the
 * sentence — which is exactly what the customer needs in order to do the thing that would let them
 * close it. A page that greyed the button out when the balance was not nought would be a second
 * copy of that rule, and the second copy is the one that is wrong the day the rule gains a clause.
 *
 * <p>Once it is closed there is no way back, and the card becomes a line saying when. There is no
 * reopening an account in this application — the way to save on that product again is to open
 * another account on it, which is one press on the overview and leaves this record exactly as it
 * is.
 *
 * <p>Nothing at all when the account has no agreement on record, for the reason the agreement panel
 * above it draws nothing: that is a database that has not been through the backend's start-up
 * migration, and a button that could not say what it was ending is worse than no button.
 */
function ClosingThisAccount({
  savingsAccountId,
  agreement,
  moneyBalance,
  onClosed,
}: {
  savingsAccountId: number
  agreement: TheAgreement | null
  moneyBalance: number
  onClosed: () => void
}) {
  const [closing, setClosing] = useState(false)
  const [refused, setRefused] = useState<string | null>(null)

  if (agreement === null) {
    return null
  }

  if (agreement.closedOn !== null) {
    return (
      <article className="card reveal">
        <h2 className="card__title">This account is closed</h2>
        <p className="rule">
          Closed on {asADay(agreement.closedOn)}. Nothing was thrown away: the deposits, the
          withdrawals and everything else below are still here, and this account is still on{' '}
          {agreement.productName}, version {agreement.version}. To save on that product again, open
          another account on it from your overview.
        </p>
      </article>
    )
  }

  function close() {
    setClosing(true)
    setRefused(null)
    closeASavingsAccount(savingsAccountId)
      .then(() => {
        setClosing(false)
        onClosed()
      })
      .catch((problem: Error) => {
        setClosing(false)
        setRefused(problem.message)
      })
  }

  return (
    <article className="card reveal">
      <h2 className="card__title">Close this account</h2>
      {refused !== null && <Refusal reason={refused} />}
      <p className="rule">
        An account you have emptied can be closed, so a product you no longer use stops cluttering
        your overview. Nothing is thrown away — every deposit, every withdrawal and the agreement
        itself stay exactly where they are and go on reading. There is no way back, and no need for
        one: opening another account on the same product is one press.
      </p>
      <div className="closing">
        <span className="closing__balance">
          This account holds {euros.format(moneyBalance)}
        </span>
        <Button tone="ghost" small busy={closing} disabled={closing} onClick={close}>
          Close this account
        </Button>
      </div>
    </article>
  )
}

/**
 * The last day a period covers, from the day it ended.
 *
 * <p>The backend sends periods half-open — the first day counts, the day it ends does not — so that
 * two consecutive months meet rather than overlap. That is the right thing to send and the wrong
 * thing to print: "20 Jan – 20 Feb" beside "20 Feb – 20 Mar" reads as a day counted twice. One day
 * is taken off here, where the reason is about a sentence a person reads rather than about how time
 * is divided.
 */
function lastDayOf(until: string): string {
  const day = new Date(`${until}T00:00:00`)
  day.setDate(day.getDate() - 1)
  // Written back out of the local fields rather than through `toISOString`, which converts to UTC
  // and would hand back the day before for every reader east of Greenwich. The date arrived as
  // three numbers the backend had already decided; this gives back three numbers of the same kind.
  const month = `${day.getMonth() + 1}`.padStart(2, '0')
  const dayOfMonth = `${day.getDate()}`.padStart(2, '0')
  return `${day.getFullYear()}-${month}-${dayOfMonth}`
}

/**
 * A month's interest as a row of the money history: the bank adding money to a savings account.
 *
 * <p>Its own row rather than a branch inside the transfer row, and for the reason a bill has its
 * own: there is no second account. Nothing was debited to pay it, so there is no "from → to" to
 * draw and an arrow between two accounts would be a picture of something that did not happen.
 *
 * <p>It says so in as many words — "the bank paid this" — because the one question this row has to
 * answer on a page full of the customer's own movements is why money appeared that nobody moved.
 *
 * <p>It earns no points and says so as a sentence rather than as a zero, exactly as a withdrawal
 * does. A 0 beside it would read as a payment that happened to earn nothing, and this one could
 * never have.
 */
function InterestOnTheLedger({
  interest,
  place,
}: {
  interest: InterestMovement
  place: number
}) {
  return (
    <li style={rowDelay(place)}>
      <span className="tl__icon tl__icon--interest" aria-hidden="true">
        <RisingIcon />
      </span>
      <div className="tl__body">
        <p className="tl__title">Interest</p>
        <p className="tl__meta">{dateAndTime.format(new Date(interest.movedAt))}</p>
        <span className="tl__between">Into savings account {interest.savingsAccountId}</span>
        <span className="tl__tags">
          <span className="tl__tag tl__tag--interest">the bank paid this</span>
        </span>
      </div>
      <div className="tl__right">
        <span className="tl__amount">{euros.format(interest.amount)}</span>
        {/* Not a zero: interest has never earned a point here, and a 0 would read as a payment
            that happened to earn nothing. */}
        <span className="tl__points tl__points--none">no points</span>
      </div>
    </li>
  )
}

/**
 * The floor you are keeping: what this agreement asks you to leave in for its bonus rate, and how
 * far above it you are standing today.
 *
 * <p><strong>The headroom is the figure, and the floor beside it is the context.</strong> "Keep
 * five hundred euros in" is an instruction; "you can take four hundred and twelve euros out and
 * still be paid the bonus this month" is a decision somebody can actually make. The whole point of
 * this bank's reading of a minimum balance is that dipping is a choice rather than a refusal, and a
 * choice nobody can price is not a choice — so this panel exists to price it, in the one sentence a
 * customer is going to read before they type an amount into the form further down.
 *
 * <p><strong>Worked out here rather than sent down, and that is a deliberate reading of a
 * boundary.</strong> The backend already sends the floor on the agreement and the balance on the
 * account, and the difference between two numbers on the same response is not a fact the server
 * knows better than this page does. A third field would be a figure that could disagree with the
 * two it was derived from the moment a deposit landed between the two reads — and this page redraws
 * both together, out of one response, every time money moves.
 *
 * <p><strong>Nothing at all for the three products with no floor.</strong> The absence is read off
 * the figure, exactly as `whatThisAgreementAsksFor` reads it: a floor of zero is no floor to keep,
 * and a panel headed "the floor you are keeping" saying "nothing" would be a rule invented for the
 * screen. An account that has dipped gets the panel all the same, with the sentence turned around,
 * because that is the moment the explanation is worth most — and because the one thing it has to
 * say is that nothing else has been taken.
 *
 * <p>No rate, for the reason the agreement panel above gives: what the bonus actually paid, and
 * whether it was earned, is on each month of the interest panel below, beside the balance it was
 * worked out on. A percentage printed up here would be a second place it lived.
 */
function TheFloorYouAreKeeping({
  agreement,
  moneyBalance,
}: {
  agreement: TheAgreement | null
  moneyBalance: number
}) {
  if (agreement === null || agreement.minimumBalance <= 0) {
    return null
  }
  const aboveTheFloor = moneyBalance - agreement.minimumBalance
  return (
    <article className="card reveal">
      <h2 className="card__title">The floor you are keeping</h2>
      <p className="rule">{howTheFloorStandsToday(agreement.minimumBalance, aboveTheFloor)}</p>
      <dl className="split">
        <div>
          <dt>Floor</dt>
          <dd>{euros.format(agreement.minimumBalance)}</dd>
        </div>
        <div>
          <dt>{aboveTheFloor < 0 ? 'Under it' : 'Above it'}</dt>
          <dd>{euros.format(Math.abs(aboveTheFloor))}</dd>
        </div>
      </dl>
    </article>
  )
}

/**
 * The one sentence the floor panel is for, in the three shapes the figure can take.
 *
 * <p>Each of them says the same two things in a different order: what you may do, and what it would
 * cost. The cost is always exactly one month's bonus and never a euro of anybody's money, which is
 * said in as many words in the two sentences where it is the thing being asked about — a customer
 * weighing a withdrawal against a rule they half remember should not have to go and look up whether
 * this bank charges for it.
 *
 * <p>The exactly-on-the-floor case is written out rather than folded into "above", because
 * "you can take EUR 0,00 out" is a sentence that reads as a bug.
 */
function howTheFloorStandsToday(floor: number, aboveTheFloor: number): string {
  if (aboveTheFloor < 0) {
    return `You are ${euros.format(-aboveTheFloor)} under the ${euros.format(floor)} this account `
      + 'asks you to keep in, so this month is paid at the headline rate without its bonus. That '
      + 'is the whole of it: nothing has been taken, and next month starts clean.'
  }
  if (aboveTheFloor === 0) {
    return `You are exactly on the ${euros.format(floor)} this account asks you to keep in. `
      + 'Anything out of here before the month is up costs this month\'s bonus, and nothing else.'
  }
  return `You can take up to ${euros.format(aboveTheFloor)} out and still be paid the bonus rate `
    + `for this month. Below the ${euros.format(floor)} floor, on any single day, this month pays `
    + 'the headline rate alone — you keep the money, and next month starts clean.'
}


/**
 * The price of breaking a fixed term early, as a row of the money history.
 *
 * <p><strong>Its own row rather than a withdrawal that happens to have no destination.</strong> A
 * withdrawal row draws an arrow from a savings account to a current account, and a charge has no
 * far end: nothing was credited, which is the whole of what a charge is. Drawn as a transfer it
 * would point at an account that was never touched.
 *
 * <p><strong>It is in this list at all because a balance that fell has to be explainable.</strong>
 * An account that quietly held five euros less than the customer expected, with nothing in the
 * history to point at, is the one figure in this application nobody could account for. The row says
 * what it was, what it cost and the day it happened.
 *
 * <p>No points, said as the sentence rather than as a zero, for the reason interest gives about the
 * same field: a 0 beside it would read as something that happened to earn nothing, and this could
 * never have earned anything.
 */
function AnEarlyExitChargeOnTheLedger({
  charge,
  place,
}: {
  charge: AnEarlyExitChargeMovement
  place: number
}) {
  return (
    <li style={rowDelay(place)}>
      <span className="tl__icon tl__icon--charge" aria-hidden="true">
        <ArrowDownIcon />
      </span>
      <div className="tl__body">
        <p className="tl__title">Charge for breaking a term</p>
        <p className="tl__meta">{dateAndTime.format(new Date(charge.movedAt))}</p>
        <span className="tl__between">Out of savings account {charge.savingsAccountId}</span>
        <span className="tl__tags">
          <span className="tl__tag tl__tag--charge">the bank charged this</span>
        </span>
      </div>
      <div className="tl__right">
        <span className="tl__amount">{euros.format(charge.amount)}</span>
        <span className="tl__points tl__points--none">no points</span>
      </div>
    </li>
  )
}

/**
 * The fixed term this account is locked into: the day it matures, how long is left, and what
 * breaking it now would cost — with the button that does it.
 *
 * <p><strong>Absent entirely for an account that is not on a term.</strong> The backend answers
 * this question for every savings account and says `termMonths: 0` for the three products in four
 * that never lock anything, which is what lets this component be rendered unconditionally and
 * decide for itself whether there is a panel to draw. Free savings, the core saver and the notice
 * account get no panel, no heading and no empty state — the page reads exactly as it did before
 * fixed terms existed.
 *
 * <p><strong>The price is printed before the button, and it is the backend's figure.</strong> The
 * whole point of the reading is that a customer knows what breaking costs before they confirm; a
 * panel that worked the price out for itself from the penalty days and a rate would be a second
 * opinion about a charge, and the second opinion is the one that is wrong the morning somebody
 * changes how the flooring works.
 *
 * <p><strong>Breaking does not take the money out, and the panel says so.</strong> It ends the term
 * and moves the account onto free savings; the withdrawal that follows is an ordinary withdrawal
 * from the form further down the page. Saying it here is what stops somebody pressing this
 * expecting their money to arrive in their current account.
 *
 * <p><strong>It reads for itself rather than taking the term off the account above.</strong> The
 * same arrangement the notice panel and the goals panel are in, and for the same reason: two reads
 * that fail independently are two things that can still be shown, and a page that folded this into
 * the account's own read would hide the balance whenever the term could not be loaded.
 *
 * <p><strong>Nothing here works out whether the term has matured.</strong> `matured`, `locked` and
 * `daysLeft` arrive decided, against the application's own clock — which is the clock a trainer has
 * wound forward. A page counting days from `maturesOn` would disagree with the backend on exactly
 * the morning the money comes free.
 */
function TheTermYouAreLockedInto({
  savingsAccountId,
  afterMoneyMoved,
  onBroken,
}: {
  savingsAccountId: number
  /** Bumped by the page whenever money has moved, because the price is a share of the balance. */
  afterMoneyMoved: number
  /** Told when the term has been broken, because the balance and the agreement both moved. */
  onBroken: () => void
}) {
  const [term, setTerm] = useState<TheTermOnAnAccount | null>(null)
  const [readError, setReadError] = useState<string | null>(null)
  const [breaking, setBreaking] = useState(false)
  // The refusal from the button, kept apart from the one about reading: a term this bank would not
  // break is something the customer asked for, and it belongs beside what they pressed.
  const [breakingError, setBreakingError] = useState<string | null>(null)
  // What breaking actually cost, kept only long enough to say so, and taken from the backend's own
  // answer rather than from the price this panel was showing a moment earlier — so the sentence
  // cannot congratulate somebody on a figure they were not charged.
  const [whatItCost, setWhatItCost] = useState<number | null>(null)

  const read = useCallback(
    (signal?: AbortSignal): Promise<void> =>
      fetchTheTermOn(savingsAccountId, signal)
        .then((onIt) => {
          if (signal?.aborted !== true) {
            setTerm(onIt)
            setReadError(null)
          }
        })
        .catch((problem: Error) => {
          if (signal?.aborted !== true) {
            setReadError(problem.message)
          }
        }),
    [savingsAccountId],
  )

  useEffect(() => {
    const abort = new AbortController()
    void read(abort.signal)
    return () => abort.abort()
  }, [read, afterMoneyMoved])

  function breakIt() {
    setBreaking(true)
    setBreakingError(null)
    breakAFixedTerm(savingsAccountId)
      .then((broken) => {
        setWhatItCost(broken.charge)
        onBroken()
        return read()
      })
      .catch((problem: Error) => setBreakingError(problem.message))
      .finally(() => setBreaking(false))
  }

  // Nothing at all until the first read has answered, rather than a skeleton. Most accounts in this
  // application are on no term, so a placeholder would be a panel appearing and vanishing on every
  // savings account page — worse than one that arrives a moment late on the few that have one.
  if (term === null) {
    return readError === null ? null : <Refusal reason={readError} standing />
  }
  // A term that has been broken leaves the account on free savings, so there is no panel to draw —
  // except for the one sentence saying what it cost, which is the answer to the press that made the
  // panel disappear.
  if (term.termMonths === 0) {
    return whatItCost === null ? null : (
      <article className="card reveal">
        <h2 className="card__title">The term is over</h2>
        <p className="rule">
          You broke the term, and it cost {euros.format(whatItCost)}. The account is on free savings
          now: the money leaves it whenever you like, at the instant-access rate.
        </p>
      </article>
    )
  }

  return (
    <article className="card reveal">
      <h2 className="card__title">Fixed term</h2>
      {term.locked ? (
        <p className="rule">
          This account is locked for {term.termMonths} months. Nothing leaves it until it matures on{' '}
          {asADay(term.maturesOn ?? '')} — unless you break the term, which costs{' '}
          {term.earlyExitPenaltyDays} days of interest.
        </p>
      ) : (
        <p className="rule">
          This term matured on {asADay(term.maturesOn ?? '')}. The money is yours: take out what you
          like, and there is nothing to pay for it.
        </p>
      )}

      {/*
        What the terms say happens on the day it is up, in their words rather than in this page's.
        The sentence comes from the backend, off the version this account was opened under — the
        bank may be selling a term with a different ending today, and the money coming free is a
        different morning from the money locking away for another year. A panel that worked the
        wording out for itself would be a second statement of what the agreement says.
      */}
      {term.whatHappensAtMaturity !== null && (
        <p className="rule term-ending">{term.whatHappensAtMaturity}</p>
      )}

      <dl className="split">
        <div>
          <dt>Matures on</dt>
          <dd>{asADay(term.maturesOn ?? '')}</dd>
        </div>
        <div>
          <dt>{term.locked ? 'Still to run' : 'Matured'}</dt>
          <dd>
            {term.locked ? `${term.daysLeft} ${term.daysLeft === 1 ? 'day' : 'days'}` : 'Already'}
          </dd>
        </div>
      </dl>

      {term.locked && (
        <>
          <p className="term-price">
            Breaking it today would cost{' '}
            <strong>{euros.format(term.whatBreakingWouldCost)}</strong> —{' '}
            {term.earlyExitPenaltyDays} days of interest on the{' '}
            {euros.format(term.balance)} in the account. It is charged out of the account as its own
            movement, and it does not take the rest of the money out: the account moves to free
            savings and you withdraw what you need from there.
          </p>
          <Button
            type="button"
            block
            busy={breaking}
            disabled={breaking}
            onClick={breakIt}
          >
            {breaking
              ? 'Breaking the term…'
              : `Break the term for ${euros.format(term.whatBreakingWouldCost)}`}
          </Button>
          {breakingError !== null && <Refusal reason={breakingError} />}
        </>
      )}

      {readError !== null && <Refusal reason={readError} />}
    </article>
  )
}

/**
 * What this account's product is offering today, what the account is on, and — line by line — what
 * taking the newer terms would change.
 *
 * <p><strong>The button is the whole feature.</strong> Nothing in this application moves an account
 * onto newer terms: not a night passing, not a deposit landing, not this page being opened. Free
 * savings' second version cut the rate from 0.60% to 0.50%, so "newer" is not a synonym for
 * "better" and an application that adopted on a customer's behalf would have moved every one of
 * those accounts onto less money. The press is the customer's and it is the only one there is,
 * apart from a roll-over at maturity they agreed to when they opened the term.
 *
 * <p><strong>The sentences are printed and never composed.</strong> They arrive written, from the
 * one function in the backend that words a difference between two agreements — the same one the
 * product's version history renders from. A page that built "The rate goes from 0.60% a year to
 * 0.75% a year." out of two numbers would be the second place that wording lived, and the two would
 * disagree the first time a figure was added to a set of terms.
 *
 * <p><strong>Nothing here says whether to press it.</strong> There is no tick, no "improved" and no
 * colour that means good news: both version numbers, the sentences, and a button. Which of those
 * changes is worth having is the customer's judgement about their own money, and it is the one
 * judgement this screen is deliberately no help with.
 *
 * <p>Absent altogether when there is nothing newer, which is the ordinary case — a panel saying
 * "your terms are up to date" would be a permanent piece of furniture built to say nothing. An
 * empty list of differences beside something newer is a real case all the same: a version published
 * to reword its own explanation moves no figure, and the panel then offers the version without
 * listing anything.
 *
 * <p>A closed account is not offered it either. Its agreement is a record of what its money lived
 * under, the backend refuses the press, and an offer to change the terms of something that has
 * ended would be a button that exists to be refused.
 */
function TheNewerTermsOnOffer({
  newerTerms,
  agreement,
  onTaken,
}: {
  newerTerms: TheNewerTerms | null
  agreement: TheAgreement | null
  onTaken: () => void
}) {
  const [taking, setTaking] = useState(false)
  const [refused, setRefused] = useState<string | null>(null)

  if (newerTerms === null || !newerTerms.newerTermsExist) {
    return null
  }

  if (agreement !== null && agreement.closedOn !== null) {
    return null
  }

  function take() {
    if (newerTerms === null) {
      return
    }
    setTaking(true)
    setRefused(null)
    takeTheNewerTerms(newerTerms.savingsAccountId)
      .then(() => {
        setTaking(false)
        onTaken()
      })
      .catch((problem: Error) => {
        setTaking(false)
        setRefused(problem.message)
      })
  }

  return (
    <article className="card reveal">
      <h2 className="card__title">{newerTerms.productName} has newer terms</h2>
      {refused !== null && <Refusal reason={refused} />}
      <p className="rule">
        You are on version {newerTerms.theVersionYouAreOn} and version{' '}
        {newerTerms.theVersionOnOfferToday} is what this product is offering today. Nothing has moved
        your account and nothing will: you carry on under version {newerTerms.theVersionYouAreOn}{' '}
        until you take the newer terms yourself.
      </p>
      {newerTerms.whatWouldChange.length > 0 ? (
        <ul className="newer-terms__differences">
          {/* Keyed by the sentence, because that is what each line is: the backend sends one line
              per figure that moved, in a fixed order, and no two of them can be the same string. */}
          {newerTerms.whatWouldChange.map((difference) => (
            <li key={difference}>{difference}</li>
          ))}
        </ul>
      ) : (
        <p className="rule">
          Not one figure is different. This version changed the wording rather than the agreement.
        </p>
      )}
      <Button type="button" block busy={taking} disabled={taking} onClick={take}>
        {taking
          ? 'Taking the newer terms…'
          : `Take version ${newerTerms.theVersionOnOfferToday}`}
      </Button>
    </article>
  )
}

/**
 * The figure the comparison starts on before anybody has typed one.
 *
 * <p>A round thousand rather than nothing at all, because an empty screen that says "type something
 * and press a button" is a screen most people leave. A thousand euros is also large enough that
 * every product's answer is a figure with cents in it rather than four noughts, which is what makes
 * the four cards worth comparing on the way in.
 */
const A_FIGURE_TO_START_FROM = '1000.00'

/**
 * The savings products screen: the four things this bank sells, side by side, with what a figure
 * the customer types would be worth in each over the next twelve months — in euros of interest and
 * in points — and one press to open an account on the one they chose.
 *
 * <p><strong>A screen of its own rather than more of the chooser on the overview.</strong> That
 * panel answers "what else is there" beside the accounts somebody already holds and has to stay
 * short enough to be read on the way past. This is the decision itself: a figure typed, four
 * projections, two rates weighed against two conditions. Folding the two together would either make
 * the overview a page nobody scrolls to the bottom of or make this comparison a thing nobody ever
 * sees, and the chooser is where this screen is reached from, which is the one press a customer
 * already knows where to look for.
 *
 * <p><strong>Not one figure on this screen is worked out here.</strong> The euros of interest, the
 * points on the way in, the points the first anniversary pays and the balance after a year all
 * arrive from the backend, which quotes the same rules the nightly sweep prices with. A page
 * multiplying a rate by twelve would be the second place a rate is priced and the first place two
 * rates disagree — and the customer would be the one who found out, a year later, that the card had
 * promised more than the bank pays.
 *
 * <p><strong>Closed products are not here at all, and that absence is the backend's.</strong> The
 * chooser on the overview draws them marked closed, because customers are holding them and their
 * agreements have to read; this screen is where an account is opened from, and a figure beside an
 * agreement nobody will sign is an invitation to choose it. There is no rule for this page to obey,
 * because there is nothing in the list for it to hide.
 *
 * <p><strong>The figure is submitted rather than watched.</strong> A read on every keystroke would
 * send a request for "1", "12", "120" and then "1200" and would paint four cards with three answers
 * nobody asked for on the way. A press is also what makes the refusal readable: somebody who typed
 * "25,00" gets the backend's sentence about the comma once, when they asked, rather than flickering
 * under their hands as they type.
 *
 * <p><strong>The cost of moving is stated under the cards rather than on any one of them.</strong>
 * Money moved to another product arrives as a new deposit with a new anniversary, and the
 * anniversary it was part-way towards is gone. That is a genuine cost of switching and it is true
 * of every card here rather than of one, so it is said once, before the button is pressed, which is
 * the whole reason the spec puts it on this screen.
 */
function SavingsProductsScreen({
  customerId,
  onBack,
  onOpened,
}: {
  customerId: number
  onBack: () => void
  onOpened: (savingsAccountId: number) => void
}) {
  // What is in the box, and what was last asked for. Two pieces of state rather than one, so that
  // the cards on the screen go on saying which figure they are about while somebody is typing the
  // next one — a card that changed its heading before its figures arrived would be a card claiming
  // something it had not been told.
  const [typed, setTyped] = useState(A_FIGURE_TO_START_FROM)
  const [asked, setAsked] = useState(A_FIGURE_TO_START_FROM)
  const [compared, setCompared] = useState<WhatAYearInAProductWouldPay[] | null>(null)
  const [comparisonError, setComparisonError] = useState<string | null>(null)
  const [opening, setOpening] = useState<string | null>(null)
  const [refused, setRefused] = useState<string | null>(null)

  useEffect(() => {
    const request = new AbortController()
    fetchWhatAYearWouldPay(asked, request.signal)
      .then((cards) => {
        if (!request.signal.aborted) {
          setCompared(cards)
          setComparisonError(null)
        }
      })
      .catch((problem: Error) => {
        if (!request.signal.aborted) {
          // The cards that are already drawn are deliberately left standing. A figure the backend
          // will not have is a sentence to read and a box to correct, and blanking four products
          // to a red band would take away the answer the customer already had.
          setComparisonError(problem.message)
        }
      })
    return () => request.abort()
  }, [asked])

  function open(product: SavingsProduct) {
    setOpening(product.code)
    setRefused(null)
    openASavingsAccount(customerId, product.code)
      .then((opened) => {
        setOpening(null)
        onOpened(opened.id)
      })
      .catch((problem: Error) => {
        setOpening(null)
        setRefused(problem.message)
      })
  }

  return (
    <section className="view">
      <button type="button" className="link link--back" onClick={onBack}>
        ← Back to your accounts
      </button>

      <div className="section-head">
        <h2>What each of them would pay you</h2>
      </div>

      <form
        className="pay-ask"
        onSubmit={(event) => {
          event.preventDefault()
          setAsked(typed.trim())
        }}
      >
        <div className="field">
          <label htmlFor="whatYouWouldPutAway">What you would put away</label>
          <input
            id="whatYouWouldPutAway"
            className="text-input"
            inputMode="decimal"
            autoComplete="off"
            placeholder="1000.00"
            value={typed}
            onChange={(event) => setTyped(event.target.value)}
          />
        </div>
        <Button type="submit">Work it out</Button>
      </form>

      {comparisonError !== null && <Refusal reason={comparisonError} standing />}
      {refused !== null && <Refusal reason={refused} />}

      {compared === null && comparisonError === null && (
        <div className="card">
          <Waiting label="Working out what each product would pay…" bars={['9rem', '100%', '70%']} />
        </div>
      )}

      {compared !== null && (
        <ul className="pays">
          {compared.map((card, place) => (
            <li key={card.product.code} className="pay" style={rowDelay(place)}>
              <div className="pay__head">
                <h3 className="pay__title">{card.product.name}</h3>
                <span className="pay__rate">
                  {asAPercentage(card.product.currentTerms.annualRatePercent)}
                  {card.product.currentTerms.bonusRatePercent > 0 &&
                    ` + ${asAPercentage(card.product.currentTerms.bonusRatePercent)}`}{' '}
                  a year
                </span>
              </div>
              <p className="pay__asks">{whatThisProductAsksFor(card.product)}</p>

              {/* The two figures the whole screen is for, side by side and in the same shape on
                  every card, because a comparison is read across four cards rather than down one. */}
              <div className="pay__figures">
                <span className="pay__figure">
                  <span className="pay__label">Interest over a year</span>
                  <span className="pay__amount">{euros.format(card.interest)}</span>
                </span>
                <span className="pay__figure">
                  <span className="pay__label">Points over a year</span>
                  <span className="pay__amount">{points.format(card.points)}</span>
                </span>
              </div>

              <p className="pay__breakdown">
                {points.format(card.pointsWhenTheMoneyLands)} when the money lands, at{' '}
                {card.product.currentTerms.pointsMultiplier.toFixed(2)}× a euro, and{' '}
                {points.format(card.pointsOnItsFirstAnniversary)} on its first anniversary, at{' '}
                {asAPercentage(card.product.currentTerms.anniversaryRatePercent)} of the euros still
                sitting there. The account would hold{' '}
                {euros.format(card.balanceAfterTwelveMonths)} after twelve months.
              </p>

              {/* A minimum-balance product's headline figure means nothing on its own, so both
                  readings are drawn: what keeping the floor is worth, and what letting it slip
                  costs. The backend sends the second figure on exactly the products that have a
                  bonus to lose, so the absence is the message on the other three. */}
              {card.interestIfTheFloorIsNotKept !== null && (
                <p className="pay__floor">
                  {card.theBonusIsInThatFigure ? (
                    <>
                      That is with{' '}
                      {euros.format(card.product.currentTerms.minimumBalance)} kept in every month.
                      A month that dips under the floor is paid{' '}
                      {asAPercentage(card.product.currentTerms.annualRatePercent)} instead, which
                      over a year comes to {euros.format(card.interestIfTheFloorIsNotKept)} — you
                      keep every euro either way.
                    </>
                  ) : (
                    <>
                      {euros.format(card.amount)} is under the{' '}
                      {euros.format(card.product.currentTerms.minimumBalance)} floor, so this is the
                      headline rate without the bonus. Keeping the floor would earn the bonus rate
                      on top for every month you kept it.
                    </>
                  )}
                </p>
              )}

              <div className="pay__doing">
                {/* What the account would be written under, said before the press rather than
                    after it: the version is what the agreement names for as long as the account
                    lives, and a rate published tomorrow does not touch it. */}
                <span className="pay__version">
                  Version {card.product.currentTerms.version} ·{' '}
                  {whatShapeOfAgreement(card.product.kind)}
                </span>
                <Button
                  small
                  busy={opening === card.product.code}
                  disabled={opening !== null}
                  onClick={() => open(card.product)}
                >
                  Open an account
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {/* True of every card rather than of one, and true after the choice rather than before it,
          which is why it is said once and here. */}
      <p className="pay__clock">
        Moving money to another product restarts its loyalty clock. The euros arrive in the new
        account as a new deposit with a new anniversary, and the anniversary the money was part-way
        towards is gone — so a figure moved eleven months in has eleven months to wait again. The
        rate follows you straight away; the anniversary does not.
      </p>

      <p className="rule">
        Every figure here is what these products would pay on the amount you typed if the money went
        in today and stayed put. Nothing has been opened and nothing has moved.
      </p>
    </section>
  )
}

/**
 * One move between two of this customer's own savings accounts, as a row of the money history.
 *
 * <p><strong>One row although two accounts changed, which is what the backend answers and what the
 * row is here to say.</strong> The customer pressed one button; a list that showed a withdrawal
 * here and a deposit next door would be asking them to pair the halves up by amount and by moment,
 * and the two rows would sit next to each other looking like money that had gone out and come back.
 *
 * <p>Its own row rather than a branch inside the transfer below, for the reason the interest and
 * charge rows above it have their own: there is no everyday account at either end, so there is no
 * IBAN to name and the arrow is drawn between two savings accounts instead.
 *
 * <p>It says "no points" in the place a deposit says what it earned, and that is the rule rather
 * than a gap: the euros were earned on once, in the account they came from, and a euro saved twice
 * is one euro. That it cost no points either is the whole reason the operation exists, and it is
 * said in the tag beside it so the row does not read as a loss.
 */
function AMoveOnTheLedger({
  move,
  place,
}: {
  move: AMoveBetweenSavingsAccountsMovement
  place: number
}) {
  return (
    <li style={rowDelay(place)}>
      <span className="tl__icon tl__icon--moved" aria-hidden="true">
        <ArrowUpIcon />
      </span>
      <div className="tl__body">
        <p className="tl__title">Moved between your savings</p>
        <p className="tl__meta">{dateAndTime.format(new Date(move.movedAt))}</p>
        {/* The two accounts in the order the money travelled, so the row reads as the sentence it
            is rather than as two labels a reader has to work out the direction of. */}
        <span className="tl__between">
          {`Savings account ${move.savingsAccountId}`}
          {' → '}
          {`Savings account ${move.toSavingsAccountId}`}
        </span>
        <span className="tl__tags">
          <span className="tl__tag tl__tag--moved">no week, no streak, no points lost</span>
        </span>
      </div>
      <div className="tl__right">
        <span className="tl__amount">{euros.format(move.amount)}</span>
        <span className="tl__points tl__points--none">no points</span>
      </div>
    </li>
  )
}

/**
 * Moving money from this savings account to another of the customer's own, with what it costs in
 * loyalty printed before the button.
 *
 * <p><strong>The panel exists because the two-press route punishes somebody for changing their
 * mind.</strong> Withdrawing five thousand euros and paying them into a better product earns
 * nothing on arrival — correct, because those euros have been saved once — and nets the week to
 * nothing, so a week that needed fifty euros goes unsecured. Both rules are right and together they
 * charge a week and a streak for taking the bank's better offer. Moving costs neither, and the
 * heading says so rather than leaving it to be discovered.
 *
 * <p><strong>What it does cost is quoted before anything moves, and the quote is the backend's
 * figure.</strong> The euros arrive as a new deposit with a new anniversary, so whatever clock they
 * were part-way through is given up. A panel that worked that out for itself from a rate and a date
 * would be a second opinion about a forfeit, and the second opinion is the one that is wrong the
 * morning somebody reprices loyalty. The quote is asked for as the customer types, and it is asked
 * of the same walk the move itself uses — so the day printed here is the day the money actually
 * gets.
 *
 * <p><strong>Absent entirely when there is nowhere to move to.</strong> A customer holding one
 * savings account has no second account to choose, and a panel offering an empty list would be a
 * form nobody can fill in. Closed accounts are not offered either: they take no more money, and
 * being refused after choosing one from a list this page drew would be this page's mistake rather
 * than theirs.
 *
 * <p>A refusal is shown in the backend's own words, whether it arrives from the quote or from the
 * press. Every one of them is a real condition of the account the money is leaving — a notice
 * period, a term, a goal that has spoken for the money — and rewording them here would be this page
 * deciding a little of the rule.
 */
function MovingMoneyToAnotherAccount({
  savingsAccountId,
  savingsAccounts,
  onMoved,
}: {
  savingsAccountId: number
  savingsAccounts: SavingsAccount[]
  onMoved: () => void
}) {
  const elsewhere = savingsAccounts.filter(
    (account) => account.id !== savingsAccountId && account.closedOn === null,
  )
  const [toSavingsAccountId, setToSavingsAccountId] = useState<number | null>(
    elsewhere[0]?.id ?? null,
  )
  const [amount, setAmount] = useState('')
  const [cost, setCost] = useState<WhatMovingWouldCost | null>(null)
  const [quoteProblem, setQuoteProblem] = useState<string | null>(null)
  const [moving, setMoving] = useState(false)
  const [moveProblem, setMoveProblem] = useState<string | null>(null)

  // The quote, asked for as the amount is typed and abandoned the moment it changes again. Nothing
  // is reserved and nothing moves, so asking early is free — and a price that arrived after the
  // button had been pressed would be no price at all.
  useEffect(() => {
    if (toSavingsAccountId === null || amount.trim() === '') {
      setCost(null)
      setQuoteProblem(null)
      return
    }
    const request = new AbortController()
    fetchWhatMovingWouldCost(savingsAccountId, toSavingsAccountId, amount.trim(), request.signal)
      .then((quoted) => {
        if (!request.signal.aborted) {
          setCost(quoted)
          setQuoteProblem(null)
        }
      })
      .catch((refused: Error) => {
        if (!request.signal.aborted) {
          setCost(null)
          setQuoteProblem(refused.message)
        }
      })
    return () => request.abort()
  }, [savingsAccountId, toSavingsAccountId, amount])

  function move(event: FormEvent) {
    event.preventDefault()
    if (toSavingsAccountId === null) {
      return
    }
    setMoving(true)
    setMoveProblem(null)
    moveMoneyBetweenSavingsAccounts(savingsAccountId, toSavingsAccountId, amount.trim())
      .then(() => {
        setAmount('')
        setCost(null)
        onMoved()
      })
      .catch((refused: Error) => setMoveProblem(refused.message))
      .finally(() => setMoving(false))
  }

  if (elsewhere.length === 0 || toSavingsAccountId === null) {
    return null
  }

  return (
    <article className="card reveal">
      <h2 className="card__title">Move to another of your savings accounts</h2>
      <p className="rule">
        One press rather than a withdrawal and a deposit. It earns no points and it costs none, it
        does not count towards this week and it cannot break your streak — the euros have been saved
        once already. What it does cost is the loyalty clock, and you are told what before you press.
      </p>

      <form onSubmit={move}>
        <div className="move-to">
          <label htmlFor="moveTo">Move to</label>
          <select
            id="moveTo"
            name="moveTo"
            value={toSavingsAccountId}
            onChange={(event) => setToSavingsAccountId(Number(event.target.value))}
          >
            {elsewhere.map((account) => (
              <option key={account.id} value={account.id}>
                {account.productName ?? `Savings account ${account.id}`} ·{' '}
                {euros.format(account.moneyBalance)}
              </option>
            ))}
          </select>
        </div>
        <EuroAmount id="moveAmount" label="Move amount" value={amount} onType={setAmount} />

        {cost !== null && (
          <div className="move-cost">
            <p>
              The {euros.format(cost.amount)} would arrive as a new deposit, and its first
              anniversary would fall on <strong>{asADay(cost.newAnniversary)}</strong> — worth{' '}
              {cost.pointsOnTheNewAnniversary}{' '}
              {cost.pointsOnTheNewAnniversary === 1 ? 'point' : 'points'} at the rate that account
              pays.
            </p>
            {cost.soonestAnniversaryGivenUp === null ? (
              <p className="move-cost__given-up">
                There is no anniversary to give up: none of the money being moved has a loyalty
                clock running on it.
              </p>
            ) : (
              <p className="move-cost__given-up">
                You would give up {cost.pointsGivenUp}{' '}
                {cost.pointsGivenUp === 1 ? 'point' : 'points'} due from{' '}
                <strong>{asADay(cost.soonestAnniversaryGivenUp)}</strong>, across{' '}
                {cost.depositsItWouldDrawDown}{' '}
                {cost.depositsItWouldDrawDown === 1 ? 'deposit' : 'deposits'}. That is the whole
                price of moving.
              </p>
            )}
          </div>
        )}

        {quoteProblem !== null && <Refusal reason={quoteProblem} />}

        <Button type="submit" block busy={moving} disabled={moving || cost === null}>
          {moving ? 'Moving…' : 'Move the money'}
        </Button>

        {moveProblem !== null && <Refusal reason={moveProblem} />}
      </form>
    </article>
  )
}

/**
 * What this account's agreement would actually let leave today, as one figure, with the backend's
 * own reason when it is less than the balance.
 *
 * <p><strong>The conclusion of the three panels above it.</strong> The agreement says what it asks
 * for, the term says when the lock comes off, the notice says which of the money has waited its
 * days, and the floor says how far above it you are standing. This says what all of that comes to
 * this morning, in one number — which is the question every one of those panels raises and none of
 * them answers on its own. Somebody who reads only this has read the part that decides what they
 * can do.
 *
 * <p><strong>The figure is the backend's and so is the reason.</strong> Nothing here subtracts a
 * notice from a balance or reads a lock as a nought: the order those conditions compose in is the
 * one thing this page must not hold a copy of, so it is read through a door of its own. The
 * sentence is the sentence a withdrawal of the whole balance would be refused in, so a customer who
 * reads it here and types the amount anyway is told the same thing twice rather than two different
 * things.
 *
 * <p><strong>Nothing at all on an account holding nothing</strong>, which is every account the
 * morning it is opened and every account that has been closed. "EUR 0,00 of the EUR 0,00 in this
 * account" is a panel about a decision nobody has to make, and a screen that drew it would be
 * furniture on the two occasions the page most needs to read short.
 *
 * <p><strong>It reads for itself rather than taking the figure off the account above</strong>, the
 * arrangement the notice, term and goals panels are all in: two reads that fail independently are
 * two things that can still be shown, and a page that folded this into the account's own read would
 * hide the balance whenever this could not be loaded. It is re-read whenever money moves, because a
 * withdrawal spends notice and a deposit raises the ceiling.
 *
 * <p><strong>It says what it is the answer to, and what it is not.</strong> A savings goal is a
 * claim the customer put on their own money and can take off in one press, and how much is
 * unclaimed is the goals panel's reading further down this page. Presenting this figure as the last
 * word would be the second place this application decides what can leave a savings account — which
 * is the line the backend's own reading declines to cross, in as many words.
 */
function WhatYouCanTakeToday({
  savingsAccountId,
  afterMoneyMoved,
}: {
  savingsAccountId: number
  /** Bumped by the page whenever money has moved, because a withdrawal spends the notice it ran on. */
  afterMoneyMoved: number
}) {
  const [free, setFree] = useState<WhatCanLeaveToday | null>(null)
  const [readError, setReadError] = useState<string | null>(null)

  useEffect(() => {
    const abort = new AbortController()
    fetchWhatCanLeaveToday(savingsAccountId, abort.signal)
      .then((answer) => {
        if (!abort.signal.aborted) {
          setFree(answer)
          setReadError(null)
        }
      })
      .catch((problem: Error) => {
        if (!abort.signal.aborted) {
          setReadError(problem.message)
        }
      })
    return () => abort.abort()
  }, [savingsAccountId, afterMoneyMoved])

  // Nothing at all until the first read has answered, rather than a skeleton: the figure is only
  // worth showing once it is right, and a placeholder that resolved to an absent panel on every
  // empty account would be a card appearing and vanishing on the way past.
  if (free === null) {
    return readError === null ? null : <Refusal reason={readError} standing />
  }
  if (free.balance <= 0) {
    return null
  }

  return (
    <article className="card reveal">
      <h2 className="card__title">What you can take today</h2>
      <p className="amount amount--lg">{euros.format(free.freeToTakeToday)}</p>
      <p className="can-leave__of">
        out of the {euros.format(free.balance)} this account holds
      </p>
      {free.whyItIsLess === null ? (
        <p className="rule">
          Your agreement holds none of it back. What a savings goal has claimed is a separate
          question, and the goals below answer it.
        </p>
      ) : (
        /* The backend's sentence, printed and not reworded. It already names the day, the days
           left or the amount notice covers, because the module that knows the condition is the
           only one that can put it into words somebody can act on. */
        <p className="rule can-leave__why">{free.whyItIsLess}</p>
      )}
      {readError !== null && <Refusal reason={readError} />}
    </article>
  )
}
