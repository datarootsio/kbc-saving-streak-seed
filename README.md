# KBC Saving Streak

A savings app in the KBC visual language: see your current account and savings accounts,
move money freely between any of them, and earn points for every euro you actually put
aside. Points are exchanged for rewards — a coffee, a snack voucher, a cinema ticket, a
donation or a family cinema pack.

Spring Boot backend, SQLite database, vanilla-JS single page frontend served by the same app.

## Run it

```bash
./mvnw spring-boot:run
```

Then open <http://localhost:8080>. The database is created at `data/saving-streak.db` and
seeded on first boot with four accounts, six rewards, a three-week streak and some history.
The interface, API messages and demo data are all in English.
Override the location with `SAVING_STREAK_DB=/path/to/file.db`. Points are owned by a
customer, so a database created before gifting existed should be reseeded with
`POST /api/demo/reset` (or by deleting `data/`).

```bash
./mvnw test          # 99 tests: rules, streak, expiry, loyalty, gifting, timeline, alerts, REST API
./mvnw package       # runnable jar in target/
```

"Reset demo" on the History tab (or `POST /api/demo/reset`) puts everything back to its
starting position.

## How the points work

| Rule | Value |
| --- | --- |
| Base earning | 1 point per whole euro of **new** savings |
| Streak bonus | +10% for every consecutive week that reached the minimum |
| Maximum multiplier | 1.50×, reached at six weeks in a row |
| Withdrawals | no points, and they never break the streak |
| Between savings accounts | allowed, but the total saved does not change, so it earns nothing |
| Weekly minimum | €50 of new savings in a calendar week — below that, the week does not join the streak |
| Loyalty rate | +10% of a deposit's base points on every anniversary it stays untouched |
| Points validity | 12 months from the day they are earned, oldest spent first |
| Gifting | send points to another customer: no cap on frequency, size, or amount per day |

*New savings* means the part of a deposit that takes your total savings above the highest
total you have ever held — your savings peak. Move €50 out and back in and the second deposit
earns nothing, because it never passes the peak; deposit €150 when only €50 of it clears the
peak and you are paid for that €50. Without this, moving the same money back and forth would
mint points forever.

A *streak week* is a calendar week (Monday–Sunday, Europe/Brussels) with at least €50 of new
savings. It accumulates, so €20 on Tuesday plus €30 on Friday secures the week — and the
deposit that crosses €50 is already paid at the higher multiplier. Smaller deposits still
earn points, they just do not secure the week. A week only counts once, and a missed week
drops the streak back to one while the best streak is kept.

### The loyalty rate

Every deposit keeps its own recurring 12-month clock (`SavingsPosition`). On each anniversary
the money is still sitting there, it pays another 10% of the base points that principal is
worth — €300 left in place pays 30 points a year, every year. Anniversaries are settled on
access rather than by a scheduled job, and settlement counts every anniversary that has
passed, so an app that was switched off for two years still pays both of them.

Take the money out before an anniversary and that year is forfeited for the part that left;
bonuses already paid on earlier anniversaries stand. A withdrawal eats into the deposits in
that account oldest first, and whatever is left keeps its original clock — withdraw €75 from
an account holding a €400 deposit and a later €75 one, and it is the €400 deposit that drops
to €325, so its next anniversary pays 32 instead of 40. Moving money between two savings
accounts is not a withdrawal: the money is still sitting still, so the clock follows it to the
other pot, splitting the position if only part of it moves.

Only the part of a deposit that earned points goes onto a clock, so restoring money you
withdrew earlier cannot start a new one.

### Notifications

The bell in the top bar carries an unread count and opens the feed. Four things get announced:

- **A balance crossing a level you set.** Each account can carry a "warn me below" and a
  "tell me above" figure, edited from the bell panel. Alerts fire on the *crossing*, not on
  every visit while the balance sits the wrong side of the line: each account remembers
  whether it is currently breached, so going under, staying under, then coming back and going
  under again produces exactly two notifications.
- **A loyalty bonus about to vest.** Any deposit whose anniversary falls within 30 days is
  announced once — "+7 points vest in 19 days. €75.00 in Finn's savings reaches another year
  on 26 Sep 2026. Leave it there and the bonus is yours." A dedupe key on the notification
  keeps it to one per position per year, however often the app is opened.
- **A loyalty bonus given up.** Withdrawing money whose anniversary was within 30 days records
  what it cost: "You gave up +7 points. That money was 19 days from another year." Because the
  oldest principal goes first, a withdrawal often misses the nearly-vested deposit entirely,
  and then nothing is reported — there is nothing to report.
- **A warning before that happens.** The transfer form flags it live while you type: "It may
  also give up the +7 bonus due in 19 days." It says *may* because whether that particular
  deposit is touched depends on how much principal sits in front of it.

Notifications are worked out on access, right after loyalty settlement, so the loyalty ones
reflect bonuses that have just vested.

### The savings timeline

Every savings account card carries a bar covering the year behind and the year ahead, with a
dashed "now" line down the middle. The top lane plots money in (up, blue) and out (down, grey),
scaled against the largest movement in the window. The rail below plots what happens to the
points: a filled green dot for a loyalty bonus already paid, a hollow green one for the next
anniversary due, a hollow amber one for points about to expire, and a red one for points that
lapsed unused. Two chips underneath name the soonest of each in words.

A legend above the account grid names all six marks, and its swatches are the very same
elements the bars draw, so the two can never drift apart.

Clicking a savings account opens its own panel: the bar again at full width with month
gridlines and tick labels, the balance and goal, and every mark on the bar listed as a dated
statement row — "26 Sept 2025 · Money in · +€75.00", "02 Apr 2026 · Points lapsed unused ·
−400 pts", "26 Sept 2026 · Loyalty bonus due · +7 pts". Movements older than the window are
noted underneath, and "Top up this account" stays pinned to the bottom however long the list
runs. The current account has no points timeline, so clicking it still goes straight to the
transfer form.

It spans two years rather than one because a points expiry and a loyalty anniversary fall
exactly twelve months after the deposit that caused them — inside a single twelve month window
a deposit and its maturity can never both appear. Movements older than the window are counted
as "+2 earlier" rather than dropped silently. Events that would overlap are spread apart just
far enough to stay countable, and every marker carries the exact date and amount as a tooltip.

### Gifting

Customers can give points to each other from the Gifts tab, as often and in whatever amounts
they like — there is deliberately no cap on frequency, size, or daily total. Every gift is
recorded as its own `PointsGift` row, so who gave what to whom is auditable.

Gifted batches keep the expiry date they had in the sender's wallet. Without that, passing
points back and forth would reset the 12-month clock and make them immortal; a test sends the
same 50 points around six times and asserts they still lapse on their original date. Gifting
also spends closest-to-expiry first, like redeeming, and points somebody gave you count
towards your balance but not towards "earned in total".

"+ Add someone" on the Gifts tab creates a new contact (`POST /api/contacts`) who can be sent
points immediately; names are trimmed and inner runs of whitespace collapsed so initials stay
predictable. Contacts cannot be removed — deciding what should happen to the points and gift
records of a deleted person is a bigger question than this demo needs.

The demo is signed in as one customer, so the contacts exist as people to send to rather than
as full customers with their own accounts. "Simulate a gift to you" (`POST
/api/demo/gifts/incoming`) has a contact send you points so the receiving side is visible.

### Expiry

Points lapse 12 months after they are earned, so the wallet is a ledger of dated batches
(`PointsLot`) rather than a single total: the balance is the sum of the batches that are still
valid at the moment you ask. Nothing needs to sweep the database on a schedule — a wallet left
alone for a year reports itself empty, and what was never spent shows up as "lapsed unused".
Redeeming always spends the batch closest to expiry first, so points cannot lapse while a
later batch is used up. The points panel warns when the next batch goes within 60 days, and
every history row carries the fate of the points it earned: the date they lapse, "expires in
19 days" highlighted in amber when it is close, "225 left" once a batch is partly spent,
"spent", or "expired" for points that were never used.

Rewards range from a €3.20 coffee at 150 points to the family cinema pack at 2,500 points.
Redeeming creates a voucher with a code such as `KBC-4CY9-MRANF`.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/overview` | Everything the frontend needs: member, accounts, totals, history, rewards, vouchers |
| `POST` | `/api/transfers` | `{"fromAccountId":1,"toAccountId":2,"amount":"50.00"}` — returns the points earned plus a fresh overview |
| `POST` | `/api/notifications/read` | Marks the feed as read |
| `PUT` | `/api/accounts/{id}/alerts` | `{"below":"2000.00","above":null}` — sets or clears the balance alerts |
| `POST` | `/api/contacts` | `{"firstName":"Noor","lastName":"Van Dijck"}` — adds someone to send points to |
| `POST` | `/api/gifts` | `{"toMemberId":2,"points":200,"message":"Thanks!"}` — gives points to another customer |
| `POST` | `/api/rewards/{id}/redeem` | Spends points and returns the voucher |
| `POST` | `/api/demo/reset` | Reseeds the demo data |

Broken banking rules (too little money, the same account twice, too few points) return `422`
with `{"message":"…"}`; malformed input returns `400`.

## Layout

```
src/main/java/be/kbc/savingstreak/
  domain/      Account, Transfer, Reward, Redemption, PointsLot, PointsGift, SavingsPosition,
               Notification, Member
  repo/        Spring Data repositories
  service/     PointsRules, PointsWallet, LoyaltyService, GiftService, ContactService,
               NotificationService, AccountTimelineService, BankingService, RewardService,
               OverviewService, Money
  web/         REST controller, error handling, DTO records
  config/      Clock/zone beans, SQLite folder preparation, demo data seeder
src/main/resources/static/
  index.html   the whole app shell, with an inline SVG icon sprite
  css/kbc.css  KBC palette, cards, hero and every animation
  js/app.js    state, API calls, rendering
  js/animations.js  number tweens, confetti, ripples, flying coins, toasts
```

Amounts are stored as eurocents in `long` fields and only converted to euros at the API
edge (`service/Money`), so no balance is ever rounded away.

## Design notes

The interface follows kbc.com: light grey canvas, white cards with a hairline border, KBC
blue call-to-action buttons, a navy panel for the points wallet (the shape of the share
price block on kbc.com) and a sky-blue gradient hero. Numbers tween from their old value to
the new one, coins fly from the transfer button to the points pill, the reward voucher pops
up as a perforated ticket, and confetti fires when you earn or redeem. Everything respects
`prefers-reduced-motion`.
