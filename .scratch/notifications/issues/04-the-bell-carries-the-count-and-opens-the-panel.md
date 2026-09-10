# 04: The bell carries the count and opens the panel

Status: ready-for-agent

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

- [ ] The bell shows the unread count and shows no badge at all when nothing is unread
- [ ] Opening the panel lists every notification newest-first with read rows visibly dimmed
- [ ] Opening the panel marks everything read in one call and the badge drops to zero
- [ ] Read notifications stay in the panel after being read
- [ ] Each of the four reasons renders its own sentence with the figures the backend sent
- [ ] Euros and dates are formatted by the existing helpers and match the rest of the application
- [ ] The panel re-loads after a deposit, a withdrawal, a claim, a gift, a clock advance and a job
      run, and there is no interval anywhere
- [ ] An empty panel says so in words
- [ ] The bell and panel are legible at 320px wide and in both colour schemes, and the badge count
      is reachable to a screen reader
