# 05: A savings account page carries the notice that concerns it

Status: done

**Blocked by:** 04 (The bell carries the count and opens the panel).

**What to build:** An inline notice on `SavingsAccountPage`, above the transfer area: the newest
unread notification whose `savingsAccountId` is this account.

The point of putting it here rather than leaving it behind an icon is proximity. The warning that
matters most — a deposit whose anniversary is near and which a withdrawal would empty first — should
be beside the withdrawal form that would cost the customer that bonus, not two clicks away.

The three calm reasons reuse the standing `.explanation` idiom: muted, capped at roughly 60
characters a line. `LOYALTY_BONUS_AT_RISK` uses the `[role='alert']` treatment `Refusal` already
has — the red left border and tinted ground — because it is the one of the four that is a warning
rather than a remark. Do not add the shake animation; a standing notice that shakes on every page
load is noise, and the existing `[role='alert']` styling will need a modifier to opt out of it.

The notice disappears once the notification has been read, which happens when the panel is opened.
It does not have a dismiss button of its own: two states is one state machine, and a training
application should not let you discard the evidence that a rule fired.

- [x] The newest unread notification for this account renders above the transfer area
- [x] Nothing renders when this account has no unread notification
- [x] Only notifications for this account appear on it, never another pot's
- [x] `LOYALTY_BONUS_AT_RISK` renders with the alert treatment and the other three render calm
- [x] The alert treatment does not shake on page load
- [x] The notice goes away after the panel has been opened and everything marked read
- [x] The notice is legible at 320px wide and in both colour schemes

## Verified

Reviewed on attempt 1 over `ticket/04-the-bell-carries-the-count-and-opens-the-panel..ticket/05-a-savings-account-page-carries-the-notice-that-concerns-it`:
88 insertions in `frontend/src/App.tsx` and `frontend/src/index.css` and nothing else. No backend
code, so no new log lines of its own — the lines quoted below are tickets 01-03 doing their work
behind the page, read to confirm the flows I drove really happened.

**Checks, ports free, app stopped first.** `lab.sh checks` -> `Tests run: 287, Failures: 0,
Errors: 0` / `BUILD SUCCESS`, then `tsc --noEmit` clean, first run, no flake.
`.../logs/05-...review.1.checks.log`.

**Driven with Playwright** (chromium, sync API, `console`/`pageerror`/`requestfailed` subscribed
to `.../logs/05-...review.1.browser.log`) against the app on a throwaway database, state built
over HTTP with the dev clock and `POST /api/dev/jobs/raiseNotifications/run`. Eighteen screenshots
at `.../logs/05-...review.1.shot.*.png`, read rather than only saved. All four reasons were put on
a page:

| what I did | what the page showed |
|---|---|
| no notifications at all | nothing: `noticeCount: 0` (shot 00) |
| deposit 600 -> acct 1, 150 -> acct 2, sweep | acct 1 "Your savings passed EUR 500,00", acct 2 "…EUR 100,00", both `notice explanation` (shots 01, 02) |
| open the bell panel | notice gone with no reload, `noticeCount` 1 -> 0 (shot 03); still gone on a fresh load (shot 04) |
| withdraw 200 from acct 1, sweep | acct 1 "Your savings fell below EUR 500,00"; **acct 2 silent** (shots 05, 06) |
| wind to 25 days before the anniversary, sweep | `LOYALTY_BONUS_AT_RISK`, alert dress: `role=alert`, `borderLeftWidth 3px`, ground `rgba(200,16,46,0.07)`, warning icon (shot 07) |
| wind 20 more days, sweep | `LOYALTY_BONUS_ABOUT_TO_PAY` for the younger deposit, calm dress (shot 12) |
| drain the oldest deposit, sweep | the younger deposit escalates to `LOYALTY_BONUS_AT_RISK` (shots 16-18) |

- **Above the transfer area**: `aboveTransferArea: true` on every measurement, computed from
  `compareDocumentPosition` against `.transfer-area`; the screenshots show it between the balances
  and the two forms.
- **Does not shake**: `animationName` is `none` on the alert on every fresh page load. The opt-out
  is scoped, not global — I forced a real refusal on the same page (withdraw 99999) and measured
  both boxes at once: `[{cls: "notice standing", animationName: "none"}, {cls: "",
  animationName: "shake"}]` (shot 11). Refusals still shake.
- **320px and both schemes**: `overflowsRight: false` and `scrollWidth == innerWidth` at 320 in
  every stage; the alert wraps to four lines inside the 1.5rem gutter and the calm one to two.
  Dark repaints both — alert `rgb(255,128,149)`, calm `rgb(169,200,222)` (shots 09, 13, 16).

**Log lines read** from `.../logs/05-...app.1.backend.log`, confirming the flows were real:

```
INFO  i.d.s.n.NotificationsService : notification raised customerId=1 reason=LOYALTY_BONUS_AT_RISK savingsAccountId=1 depositId=1 amount=null points=40 occursOn=2027-09-10 notificationId=5
INFO  i.d.s.n.NotificationsService : notification raised customerId=1 reason=LOYALTY_BONUS_ABOUT_TO_PAY savingsAccountId=1 depositId=3 amount=null points=30 occursOn=2027-09-30 notificationId=7
INFO  i.d.s.n.NotificationsService : notifications marked read customerId=1 notifications=2 asAt=2026-09-10T16:49:26.242151Z held=2
DEBUG i.d.s.n.NotificationsService : deposit passed over for an anniversary notification depositId=2 reason=this anniversary has already been announced occursOn=2027-09-10 announcedReason=LOYALTY_BONUS_AT_RISK
WARN  i.d.s.deposits.WithdrawalsService : withdrawal rejected savingsAccountId=1 amount=99999.00 balance=700.00 reason=There is not enough in that savings account to move EUR 99999.00. It holds EUR 700.00.
```

Browser log over the whole run: no `pageerror`, and one single `console:error` — the 400 from the
over-withdrawal I triggered on purpose. Vite's log has no transform error. The `requestfailed
… net::ERR_ABORTED` lines are React's double-invoked effect aborting its own `AbortController` on
boot and predate this branch.

**The three judgement calls the implementer flagged, judged against the ticket's own wording:** all
three are what the ticket asked for. It names the `[role='alert']` treatment explicitly and says
"the existing `[role='alert']` styling will need a modifier to opt out of it", which presumes the
element carries the role; it puts the "roughly 60 characters a line" cap on the three calm reasons
only, and gives the fourth the Refusal dress, which spans its panel; and `.explanation` is a bare
muted paragraph everywhere else in this stylesheet, so a calm notice without an icon is that idiom.

### Notes for later, none of them blocking

1. **The notice is "newest unread" regardless of reason, so a calmer, newer notification hides the
   at-risk warning.** `LOYALTY_BONUS_AT_RISK` is raised once per `(depositId, reason, occursOn)`
   and never re-raised on the remaining nights of the thirty-day window, so a
   `BALANCE_THRESHOLD_REACHED` raised two nights later takes the slot and the warning is not seen
   again above the withdrawal form. This is exactly what this ticket's first criterion asks for
   ("the newest unread notification for this account"), so it passes — but proximity to the
   withdrawal form is the feature's stated point, and preferring an unread at-risk one is worth a
   ticket of its own.
2. **`role="alert"` is `aria-live="assertive"`, and the node is inserted after mount**, so a screen
   reader may interrupt the page heading with the notice on every visit until the bell is opened —
   the audible counterpart of the shake this branch deliberately removed. `role="status"` with the
   same dress would keep the colour without the interruption.
3. **`Notice` renders its wrapper before knowing it has a sentence.** `WhatHappened` returns `null`
   when a reason's fields are absent, which would leave a wordless red box or an empty paragraph
   still carrying `margin: 1.25rem 1.5rem`. Unreachable today — the backend keeps the
   reason-to-fields mapping total — so this is defensive only.
4. **The notice carries no date**, unlike the panel rows. An unread `BALANCE_THRESHOLD_LOST` from a
   week ago can sit above a balance that has since climbed back over the rung.
