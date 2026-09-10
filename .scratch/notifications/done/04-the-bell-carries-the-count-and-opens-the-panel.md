# 04: The bell carries the count and opens the panel

Status: done

**Blocked by:** 03 (A customer reads their notifications).

**What to build:** A bell in `TopBar` with the unread count, and the panel it opens.

The bell is a button rendered on every screen, showing the number of unread notifications as a badge
when that number is above zero and nothing when it is zero, with an `aria-label` that says how many
are unread rather than leaving the count to the badge alone. Opening it renders
`NotificationsPanel`: every notification newest-first, read rows carrying a dimmed modifier, each row
an icon, a sentence and an absolute date. Opening the panel fires the read call once and the badge
drops to zero; the rows stay.

`WhatHappened` turns a reason and its figures into the sentence, in the frontend, following
`WhatItEarned`: "Your savings passed EUR 1.000,00", "Your savings fell below EUR 1.000,00",
"30 points arrive on 9 september 2028", and for the at-risk reason "30 points arrive on 9 september
2028 — money taken out now comes out of this deposit first". Euros and dates go through the
formatters this application already has; nothing new invents a format.

Notifications load with the accounts on boot and re-load on the callbacks that already exist —
`onChanged`, `onClaimed`, `onGiven` — and after a development clock advance or job run, which is
exactly when a trainer wants the new notification to appear. No interval: this application has no
polling anywhere, and adding its first `setInterval` for a nightly rule would be out of proportion.

An empty panel says plainly that nothing has happened yet, so emptiness is not mistaken for a page
that failed to load. Follow the `.nothing` idiom.

- [x] The bell shows the unread count and shows no badge at all when nothing is unread
- [x] Opening the panel lists every notification newest-first with read rows visibly dimmed
- [x] Opening the panel marks everything read in one call and the badge drops to zero
- [x] Read notifications stay in the panel after being read
- [x] Each of the four reasons renders its own sentence with the figures the backend sent
- [x] Euros and dates are formatted by the existing helpers and match the rest of the application
- [x] The panel re-loads after a deposit, a withdrawal, a claim, a gift, a clock advance and a job
      run, and there is no interval anywhere
- [x] An empty panel says so in words
- [x] The bell and panel are legible at 320px wide and in both colour schemes, and the badge count
      is reachable to a screen reader

## Verified

Reviewed on attempt 1 over `ticket/03-a-customer-reads-their-notifications..ticket/04-the-bell-carries-the-count-and-opens-the-panel`
(two commits, frontend only: `App.tsx` +396, `api.ts` +95, `index.css` +174; the backend is untouched).

**Checks.** Application stopped first, per `logs/THE-PHANTOM-RED-BUILD.md`. `lab.sh checks` →
287 tests, 0 failures, BUILD SUCCESS; `tsc --noEmit` clean
(`logs/04-….review.1.checks.log`). Application restarted on a fresh throwaway database as
`…app.1b`.

**Driven with Playwright, chromium, on a throwaway database seeded by hand over the API**
(twelve runs, `logs/04-….review.1.browser.log`, screenshots `…review.1.shot.*.png`). Setup:
two deposits into savings account 1 (EUR 600 then EUR 200), clock +340 days, sweep, then a
EUR 400 withdrawal and a second sweep — which raises all four reasons at once.

- *Badge.* Four unread → `aria-label='Notifications, 4 unread'`, badge text `'4'`, badge
  `aria-hidden`. Bram, who has nothing, → `'Notifications, none unread'` and `0` badge
  elements. `shot.01-badge.png`, `shot.13-badge-*.png`.
- *The four sentences,* read off the rendered rows: `'Your savings fell below € 500,00'`,
  `'20 points arrive on 10 september 2027'`, `'60 points arrive on 10 september 2027 — money
  taken out now comes out of this deposit first'` (that row `class='notification at-risk'`),
  `'Your savings passed € 500,00'`. Euros through `euros`, the anniversary through `asADay`,
  the raised-at through `dateAndTime` (`'16/08/2027, 17:04 · Savings account 1'`) — the same
  three formatters the rest of the page uses.
- *Newest first,* by notification id: rows came back 4, 3, 2, 1 and later 5, 4, 3, 2, 1.
- *One read call, badge to zero, rows stay.* Counted requests: `read calls fired by this
  opening: 1`; afterwards `badges=0`, `'Notifications, none unread'`, `rows=4`, all four
  `class='notification … read'` at computed `opacity=0.62`. `shot.02-panel-read.png`.
- *Dimming is real and not blanket.* With the read POST blocked, the panel rendered two unread
  rows at `opacity=1` above a dimmed read one, and showed the refusal inline.
  `shot.10-unread-undimmed.png`.
- *Empty panel.* Bram: `'Notifications\n\nNothing has happened yet.'`, one `.nothing` element.
  `shot.04-empty.png`.
- *Every re-load trigger, one at a time.* Each notification was raised over the API first and
  the page left alone to prove it was not picked up, then the action performed on the page:
  deposit `none unread → 1 unread`; withdrawal `none → 2`; claim `none → 1`; gift `none → 1`.
- *No interval.* Six seconds idle after a sweep: `GET /notifications count 2 -> 2`, label
  unchanged. `grep -rn setInterval frontend/src` finds nothing; the only timers in the file are
  the three pre-existing 2600 ms celebration dismissals and the canvas `requestAnimationFrame`.
- *Clock advance and job run.* The page cannot see these — they happen over the API — so the
  branch re-loads on `visibilitychange` and window `focus`. Both fire and both re-load: after a
  clock advance and `POST /api/dev/jobs/raiseNotifications/run`, an untouched page stayed at
  `'none unread'` and then read `'Notifications, 2 unread'` after a dispatched
  `visibilitychange`, and `'Notifications, 1 unread'` after a dispatched window `focus`.
  **Caveat, stated plainly:** I could only fire those events by dispatching them myself.
  Chromium under Playwright reports the page visible and focused whatever tab is in front —
  measured: `visibilityState while another tab is in front: visible hasFocus=True` — so a real
  `bring_to_front` triggers nothing to observe. The wiring is `document.visibilityState ===
  'visible'`-guarded, event-driven and torn down on unmount, and a real alt-tab dispatches
  exactly the events I dispatched; I judge the criterion met, but I did not see a human tab
  switch do it.
- *320px and both colour schemes.* Panel `x=16 width=288` at a 320px viewport in both schemes,
  scrolling inside itself, top bar `scrollWidth == clientWidth == 320`. The page's 361px
  `scrollWidth` is pre-existing `LI.offer` reward cards, not the bell — identical with
  `.bell-holder` hidden. Warning text `rgb(200,16,46)` light / `rgb(255,128,149)` dark; badge
  `#003665` on white light, `#9FD8F2` on `#0A3053` dark. `shot.06-320-*.png`,
  `shot.12-320-panel.png`, `shot.07-dark.png`.
- *Reachable.* The count is in the button's `aria-label`, not only the badge; the badge is
  `aria-hidden`. Bell focusable and openable with Enter, focus stays on the bell, `aria-expanded`
  flips true/false, panel `role='group' aria-label='Notifications' id='notifications'` matching
  `aria-controls`. Escape closes it; a press elsewhere closes it. Measured alignment in `.who`:
  bell, avatar and Sign out all centre on y=42.1.

**Logs.** `logs/04-….app.1.backend.log`: 26 `notification raised` INFO lines, e.g.
`notification raised customerId=1 reason=LOYALTY_BONUS_AT_RISK savingsAccountId=1 depositId=1
amount=null points=60 occursOn=2027-09-10 notificationId=2`; 24 `notifications marked read`
lines behind the badge dropping, e.g. `notifications marked read customerId=1 notifications=5
asAt=2030-12-28T16:12:05Z held=20`; sweep summaries `notifications raised asAt=… accountsConsidered=3
depositsConsidered=3 raised=1`. No WARN or ERROR from `io.dataroots.savingstreak`, no 5xx. The
browser log has no `pageerror` in any run and no console error except the ones I caused by
blocking the read request; the Vite log is clean.

**Two non-blocking notes for whoever works here next.**

1. *The read POST and a notifications GET are not sequenced.* `onOpened` and `loadNotifications`
   both `setNotifications` with no generation counter, so if a GET is answered before the read
   commits but delivered to the page after it, the stale unread list wins: the badge shows a
   count the server no longer has, until the next re-load. Reproduced deliberately by holding
   the GET's *response* back 1.5 s
   (`[race2] page: 'Notifications, 2 unread' badges=1 …; server unread=0`). It never happened
   in about a dozen ordinary openings on localhost, and delaying the *request* by 900/300/60 ms
   does not produce it. Latent, not observable in the trainer's setup.
2. *A tab return fires two GETs,* because `visibilitychange` and window `focus` both fire and
   both re-load. Harmless, and the obvious fix is to keep only the `visibilitychange` handler.

Verified by review attempt 1.
