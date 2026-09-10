# 05: A savings account page carries the notice that concerns it

Status: needs-review

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
