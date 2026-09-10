# 03: A customer reads their notifications

Status: ready-for-agent

**Blocked by:** 02 (An anniversary coming soon says whether it is first in line).

**What to build:** The two endpoints, and the read mark behind them.

`GET /api/customers/{customerId}/notifications` returns `List<NotificationResponse>` newest first,
read and unread together, because the panel is a record rather than an inbox that empties.
`POST /api/customers/{customerId}/notifications/read` takes no body, sets `readAt` on every unread
notification belonging to that customer, and returns the same list so the caller needs one round
trip rather than two.

`NotificationResponse(Long id, NotificationReason reason, Long savingsAccountId, Long depositId,
BigDecimal amount, Long points, LocalDate occursOn, Instant raisedAt, Instant readAt)`. The reason
serialises as its enum name and the frontend writes the sentence, the way `WhatItEarned` already
composes a sentence from three numbers: every euro and every date in this application is formatted
Dutch-style in the browser, and rendering sentences in Java would fork that formatting into a second
place that will drift. Refusal messages stay as they are, because a refusal's wording is domain
logic and a notification's wording is not. `occursOn` is a `LocalDate` and not an `Instant`, for the
reason `PointsExpiringNext` gives: the backend picks the zone rather than the browser.

`readAt` is a moment and is set once. Marking an already-read notification leaves the original
moment alone, so the operation is idempotent on the entity in the same way `PointsCredit.expire` is,
and a second call changes nothing and reports the same list.

Both endpoints are customer-scoped, matching money-movements, redemptions and gifts. An unknown
customer is refused with the sentence `AccountsService.noSuchCustomer` already owns, mapped to 404
in `RefusalsAsHttp` alongside the others.

- [ ] A customer's notifications come back newest first, read and unread together
- [ ] A customer with nothing comes back as an empty list rather than an error
- [ ] Marking read sets a moment on every unread notification and returns the updated list
- [ ] Marking read twice leaves the first moment alone and changes nothing
- [ ] A notification's figures match the sweep that raised it, field for field
- [ ] Naming a customer who does not exist is refused with a readable reason and a 404
- [ ] One INFO line when a customer marks read, carrying the customer and how many were marked
- [ ] Every refusal is one WARN line carrying its kind and its reason
