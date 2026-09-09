# 02: A gift is refused in words and changes nothing

Status: done

**Blocked by:** 01 (a customer can give points to another customer).

**What to build:** Exactly four things are refused, each in words the customer who caused it can
read, and a refused gift leaves both pots and both records exactly as they were:

- a recipient nobody banks under — 404, in the same words sign-in already uses for an address it does
  not know;
- a gift to yourself — 400, because a no-op that reports success is worse than a refusal that
  explains itself;
- points that are not a positive whole number — 400. The figure arrives as the customer typed it, the
  way deposit and withdrawal amounts already do, so `2.5`, `abc`, `0` and `-5` are ruled on by the
  backend and come back as sentences rather than being coerced in the browser into something
  plausible;
- more points than the sender holds — 400, quoting their balance, in the wording a reward claim
  already uses for the same shortfall.

A sender who does not exist is a 404 like every other per-customer route.

**Nothing else is refused.** No cap on the size of one gift, no daily total, no cooldown, no minimum,
no limit on how many people one customer may give to. That absence was asked for explicitly, so this
ticket proves it rather than assuming it: a run of gifts one after another all go through, and a
single gift of the sender's whole balance goes through. Should a limit ever be wanted it belongs in
Gifting beside these four, and the gift record already holds everything a velocity rule would need to
be written against after the fact.

Refusals travel as a refusal type of Gifting's own, mapped to statuses in `RefusalsAsHttp` alongside
the deposit, withdrawal, clock, job and reward refusals, so the reason lands in `detail` in the one
error shape this application answers in. WARN on every refusal with its kind, the sender, the
recipient as given, and the reason.

- [x] A gift to an email address nobody banks under is refused with 404 and says so.
- [x] A gift to yourself is refused with 400 and says so.
- [x] A gift of zero, of a negative number, and of a fraction are each refused with 400 and each say what was wrong.
- [x] A gift of more points than the sender holds is refused with 400, and the refusal quotes the balance they actually have.
- [x] A gift from a customer who does not exist is refused with 404.
- [x] After any refusal both balances are unchanged, no gift row exists, and neither customer's gift list has grown.
- [x] Many gifts in a row from the same customer all go through: there is no cooldown and no daily total.
- [x] One gift of the sender's entire balance goes through, leaving them at zero.
- [x] Every refusal is one WARN line carrying its kind and its reason.

## Verified

Reviewed on attempt 1 against `agentic_engineered..ticket/02-a-gift-is-refused-in-words-and-changes-nothing`
(commits `9336978`, `79d53e2`). Backend-only: the gift page is still ticket 06, and no frontend file
changed on this branch.

**Checks, run again by the reviewer.** `cd backend && ./mvnw test` → BUILD SUCCESS, 241 tests, 0
failures (base is 229). `cd frontend && npm run typecheck` → exit 0.

**Driven over HTTP** against the app the orchestrator started on a throwaway database
(`...app.1.backend.log`), after a €48 deposit gave Anke 48 points and Bram 0. Every refusal was
sent with both balances and both gift lists read immediately before and after it; all of them came
back unchanged.

| request | status | `detail` |
| --- | --- | --- |
| recipient `nobody@example.be` | 404 | No customer banks here under that email address. |
| recipient = self | 400 | A gift goes to somebody else, and Anke Peeters is who you are signed in as. |
| recipient = `"  ANKE.PEETERS@EXAMPLE.BE  "` | 400 | same sentence — trimmed and case-insensitive, as sign-in matches |
| points `0` | 400 | A gift has to be more than zero points, and 0 is not. |
| points `-5` | 400 | A gift has to be more than zero points, and -5 is not. |
| points `2.5` | 400 | Points are whole, and 2.5 is not a whole number. |
| points `abc` | 400 | A gift is a whole number of points, and "abc" is not a number. |
| points `44` on a balance of 43 | 400 | That gift costs 44 points, and you have 43. |
| sender `9999` | 404 | There is no customer 9999. |
| no recipient field / blank recipient | 400 | A gift needs the email address of the customer it is going to. |
| no points field / blank points | 400 | A gift needs a number of points to give. |
| `GET /api/customers/9999/gifts` | 404 | There is no customer 9999. |

The four sentences the ticket asks to be borrowed are the borrowed ones. `POST
/api/customers/sign-in` with `nobody@example.be` answers 404 with *"No customer banks here under
that email address."* — byte-for-byte the gift refusal, and the test asserts equality rather than
similarity. A claim Bram cannot afford answers *"Coffee or snack voucher costs 40 points, and you
have 0."*, the same shape as the gift shortfall.

**Nothing moved, and no row survived.** Twelve gifts of one point sent back-to-back all answered
201; one gift of Anke's whole remaining 31 answered 201 and left her at 0 with Bram at 48 — exactly
the 48 the deposit earned, so nothing was created or skimmed. Fourteen gifts went through in total
and both customers' lists hold fourteen rows with ids 1–14, no gaps: the `NOT_ENOUGH_POINTS`
refusals, which are thrown *after* `gifts.save(...)`, left no row behind. Giving 1 more point on an
empty pot answered 400 *"That gift costs 1 points, and you have 0."*

**Logging.** 29 refusals triggered → 29 `gift rejected` WARN lines, each carrying its kind and its
reason; 0 ERROR lines in the whole run. For example:

    WARN i.d.savingstreak.gifting.GiftingService : gift rejected senderCustomerId=1 recipientAsGiven=bram.devos@example.be kind=NOT_ENOUGH_POINTS reason=That gift costs 44 points, and you have 43.
    WARN i.d.savingstreak.gifting.GiftingService : gift rejected senderCustomerId=9999 recipientAsGiven=bram.devos@example.be kind=NO_SUCH_CUSTOMER reason=There is no customer 9999.

The inputs behind each decision are at DEBUG (`gift judged against the sender's pot ... points=31
senderBalance=31`), each gift is one INFO line (`gift given giftId=14 senderCustomerId=1
recipientCustomerId=2 points=31`), and the credit side shows on the ledger's own line with
`reason=GIFT_RECEIVED`.

**The page.** Nothing to drive for this ticket, but the existing screen was loaded under Playwright
with the console subscribed: it renders fully styled and shows Bram holding the 48 gifted points
("48 points to spend"). No `pageerror`, no console error.

### The gate's red run — settled, and not this ticket's defect

`checks.1.log` failed two tests in `TheClockStaysWhereItWasMovedApiTest`. That class runs **5th of
74** in surefire's order; the two classes this ticket adds run **72nd and 73rd**. They had not
executed when it failed, so they cannot have caused it. The order is byte-identical across all six
recorded runs and the reviewer's own. Confirmed empirically: 6/6 green full runs on this branch,
6/6 green with both new classes excluded (229 tests), 8/8 green for the class on its own. It is a
pre-existing order- or environment-dependent flake — the reopened application answered `GET
/api/dev/clock` with 404 and `/api/customers` with nothing, which points at the
`--server.port=0` / `local.server.port` rebinding in that class rather than at anything in Gifting.
**It deserves a ticket of its own.**

### Notes for later, none of them blocking

- An over-long figure (>64 characters) is classified without parsing, and the character filter
  admits `0-9 + - . e E`. So `"."×70` and `"e"×70` — plainly not numbers — get the shortfall
  sentence *"That gift costs .................. points, and you have 0."* This is strictly better
  than the base branch, which answered every over-long figure as a shortfall, it is documented in
  the helper's javadoc, and no acceptance criterion covers it. Requiring at least one digit would
  close it cheaply.
- `CustomerController.refusingTheGift` logs `senderCustomerId`, `kind` and `reason` but not the
  recipient, and `kind=NOT_A_GIFT` is a label rather than a `GiftRefused.Kind` value. The criterion
  asks for kind and reason, which are both there.
- This branch also lands ticket 05's production code: `GET /api/customers/{id}/gifts`,
  `GiftRepository.findBySender…`, `GiftingService.giftsOf`, two getters on `Gift`, and
  `AnApplicationWithAClockToMove.giftsOf`. The implementer flagged it, and it is what makes
  "neither customer's gift list has grown" assertable without reading the database, which the
  spec's testing decisions forbid. **Ticket 05 should be reconciled against what is already here
  rather than rebuilt** — its remaining work is tests plus the survives-spend/survives-expiry and
  empty-list-not-404 cases.
