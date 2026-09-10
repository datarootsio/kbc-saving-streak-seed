# 06: The gift page

Status: needs-review

**Blocked by:** 01 (a customer can give points to another customer), 02 (a gift is refused in words
and changes nothing), 05 (each customer's list of the gifts they were part of).

**What to build:** A page where a customer picks somebody who banks here, says how many points to
give, and sends them — with the list of gifts they have been part of underneath.

A screen of its own rather than a panel on the home screen: gifting has a form *and* a ledger, which
is the money-history screen's shape, where the rewards catalogue is a panel because it is only a
catalogue. It joins the frontend's screen union and is reached by a way-through button on the home
screen beside Money history.

- The recipient is chosen from the people who bank here, **with the signed-in customer excluded**, so
  the one refusal a customer could stumble into is unreachable from the page. The customers endpoint
  is the only way this frontend ever learns of another customer; behind the sign-in gate nothing
  currently calls it. The stylesheet already carries a radio-card and avatar pattern that no
  component uses yet — reuse it rather than inventing a third way to present a person.
- The request still carries the recipient's email address. The picker is a convenience over the
  human-facing contract, not a different way in.
- The points figure goes over the wire **as the customer typed it**, the way deposit and withdrawal
  amounts already do, so the backend stays the authority on what a valid gift is and the page shows
  its answer unchanged through the existing refusal component. Affordability greys the send button —
  a hint computed on the page for the same reason a reward offer does it, never the ruling.
- The send button is unavailable while a gift is in flight, in the shape the reward claim already
  uses, so one click cannot become two gifts.
- A gift that goes through is shown as confirmation, and refreshes both the account read and the gift
  list — the pattern the claim already follows when it reloads accounts and redemptions together.
- A new icon: the existing gift icon is already spoken for as the unknown-reward fallback.

This repository has no frontend test tooling, so verification is `npm run typecheck` plus driving the
page in the lab as both demo customers — give points as one, sign in as the other, see them arrive.

- [x] A way-through button on the home screen opens the gift page, and its back button returns home.
- [x] The page lists the other people who bank here to choose from, and never offers the signed-in customer themselves.
- [x] Choosing someone and entering a number of points sends the gift and shows what went through.
- [x] The signed-in customer's points balance on screen falls by the gift without a reload.
- [x] The gift appears in the list on the page immediately after it is sent.
- [x] The list shows sent and received gifts together, newest first, naming the other person, the direction and the points.
- [x] A refused gift shows the backend's reason verbatim and nothing on the page changes.
- [x] Typing something that is not a whole number of points produces the backend's refusal in words rather than a silently coerced gift.
- [x] The send button is disabled while a gift is in flight and while no recipient or no points are chosen.
- [x] `npm run typecheck` passes, and the page has been driven end to end as both demo customers with a clean browser console.
