# 02: A gift is refused in words and changes nothing

Status: ready-for-agent

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

- [ ] A gift to an email address nobody banks under is refused with 404 and says so.
- [ ] A gift to yourself is refused with 400 and says so.
- [ ] A gift of zero, of a negative number, and of a fraction are each refused with 400 and each say what was wrong.
- [ ] A gift of more points than the sender holds is refused with 400, and the refusal quotes the balance they actually have.
- [ ] A gift from a customer who does not exist is refused with 404.
- [ ] After any refusal both balances are unchanged, no gift row exists, and neither customer's gift list has grown.
- [ ] Many gifts in a row from the same customer all go through: there is no cooldown and no daily total.
- [ ] One gift of the sender's entire balance goes through, leaving them at zero.
- [ ] Every refusal is one WARN line carrying its kind and its reason.
