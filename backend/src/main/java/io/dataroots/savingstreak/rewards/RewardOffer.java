package io.dataroots.savingstreak.rewards;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * One thing points can be spent on: what it is called, what it says to a customer, what it costs,
 * and what the voucher for it is stamped with — plus every column the scheme needs in order to be
 * run rather than released.
 *
 * <p><strong>A row rather than a constant, and this reverses a decision that was written down in
 * this codebase.</strong> The javadoc on the {@code Reward} enum that used to stand here argued the
 * opposite and called it the intent: "The catalogue is fixed and lives in the code… there is no
 * screen that edits it and no row anybody can add… Changing a price is a release, and that is the
 * intent." That was defensible for a points scheme with one shape. It is being reversed on purpose,
 * and named rather than quietly deleted, because the argument against it is already written down
 * next door: {@link io.dataroots.savingstreak.challenges.ChallengeDefinition} chose a row over a
 * constant and made its case against this very enum by name. A catalogue somebody runs is now the
 * challenges case. A season, a restock, a promotion or a corrected typo is not a change to how this
 * application works, and a scheme in which every one of them is a deployment and a code review is a
 * scheme in which none of them ever happen.
 *
 * <p><strong>What the enum carried is carried here unchanged</strong> — a code, a title, words, a
 * price and a voucher prefix — and the four entries this application has always offered are seeded
 * from exactly those figures by {@link RewardsOnStartUp}. A customer who knew the catalogue
 * yesterday sees the catalogue they knew.
 *
 * <p><strong>Everything below the prefix is null on all four of them, and every one of those
 * columns is now read.</strong> Stock, a window, limits, eligibility rules, a discount and a
 * voucher shelf life arrived as columns so that the schema would stop moving, and the wave of
 * slices below has filled in what each of them means. A null is still the absence of the rule,
 * every time and in the same direction: no stock is unlimited, no window is always open, no cap
 * is no cap. That is what makes a seeded offer behave exactly as the constant it replaced.
 *
 * <p><strong>What an offer still cannot say is a bundle, a hold and a waiting list.</strong> A
 * bundle is a row with member lines hanging off it, so it is a table rather than a column; a
 * hold and a queue are both facts about one customer and one unit of stock rather than about
 * the offer, so neither of them belongs here either. Nothing on this row is a placeholder any
 * more.
 *
 * <p><strong>Who an offer is for is three of those columns, and they are now read.</strong> A
 * minimum run of weeks, a badge and a lifetime of points earned — a short closed list rather
 * than anything anybody writes, ANDed, and every one of them null on all four seeded entries so
 * that a seeded offer still behaves exactly as the constant it replaced, for every customer. The
 * badge is stored as text and never as the challenges module's own type, which is this module
 * declining a dependency in the one place a dependency would be hardest to take back out: a
 * column. Nothing here decides whether a customer meets them — that is a pure function of
 * {@link WhoAnOfferIsFor} and a {@link CustomerStanding} the web layer assembles and hands in.
 *
 * <p><strong>Two more of those columns are now read, and they are the two about how often one person
 * may have a thing.</strong> {@link #maxPerCustomer} and {@link #maxPerCustomerPerWeek} are
 * asked of a customer rather than of a day, which makes them the first thing on this row whose
 * answer differs between two people looking at the same offer at the same moment. Neither is
 * decided here: the counting is a question about the claims, and what this class knows is only
 * whether a number that has been counted has reached a number somebody set. Both stay null on
 * everything the application seeds, and null means what null means everywhere else on this row —
 * no cap, anybody, as often as they like.
 *
 * <p><strong>Stock is now one of the ones this module reads.</strong> An administrator says how
 * many of something exist; what is <em>left</em> is that number less the claims already made,
 * worked out at the moment of reading and held nowhere — the argument is on the column itself and
 * on {@link #whatIsLeftAfter}. Nothing about the four seeded offers changes, because a null stock
 * is still unlimited and still skips the counting entirely.
 *
 * <p><strong>The discount is the first of that list to stop being a column nobody reads, and it
 * is three columns rather than one.</strong> The paragraph above is left as it was written,
 * because the rest of the list is still true and rewriting a sentence four slices share is how
 * two people end up hand-merging one sentence. What has changed is only this: a price below the
 * ordinary one, with a first day and a last day of its own, and an offer is sold at it on every
 * day inside that window and at its ordinary price on every day outside. A percentage was
 * rejected by the spec and the reason is arithmetic rather than taste — a percentage of an
 * integer point price produces halves, and no rounding rule wins the argument about which way
 * they go. A second absolute price has no halves in it and reads back off the administration
 * screen as the figure somebody typed.
 *
 * <p><strong>Which price applies today is derived and stored nowhere</strong>, exactly as being
 * open or shut is. The three columns are what an administrator set; {@link #costOn} is what it
 * costs, asked of a day handed in. A nightly job that wrote "on discount" into a column would be
 * a second figure that has to agree with a clock, and this application already refuses that
 * bargain for a points balance and for a goal's status.
 *
 * <p><strong>The code is the natural key and it never changes.</strong> Unique, because it is what a
 * claim names and what the API is addressed with, and two rows sharing one would be two answers to
 * the same request. A single-column unique constraint is one SQLite accepts from generated schema,
 * which is why it is declared here rather than made into an index at start-up the way this
 * application's composite guarantees have to be. Nothing here deletes an offer either: a row that
 * went away would orphan every voucher pointing at it, which is the same reason a goal is abandoned
 * rather than removed.
 *
 * <p><strong>The state and the kind are closed sets, so they may be enums.</strong> That is the one
 * thing the catalogue's own entries were not. Hibernate writes a {@code check} constraint for a
 * string-mapped enum column, which is precisely the trap this ticket had to take back off the claim
 * table — but a check is the right constraint when the set genuinely belongs to the code, and these
 * two do: an administrator publishes and withdraws, they do not invent a fourth state.
 *
 * <p>Package-private, like the repository that reads it: {@link ARewardOnOffer} is what leaves this
 * module.
 */
@Entity
class RewardOffer {

    /** SQLite has no sequences, so identity values are generated by the database. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * What a claim names this offer by, what a voucher already issued for it is stored under, and
     * what the seed matches on when it decides whether this row is already there.
     */
    @Column(nullable = false, unique = true)
    private String code;

    /** What the card is headed with, and what a claim writes down as the name of what was bought. */
    @Column(nullable = false)
    private String title;

    /**
     * What the card says underneath: what the customer is actually getting, in the words they
     * decide on rather than a restatement of the price.
     *
     * <p>Long, like the challenge cards', because this is the only place the scheme gets to explain
     * itself and a catalogue somebody runs will grow entries that need a sentence rather than six
     * words.
     */
    @Column(length = 1000)
    private String words;

    /** The price, in points, that a claim spends and writes down. */
    @Column(nullable = false)
    private long costInPoints;

    /** Three letters at the front of a voucher, so a person holding one can see what it is for. */
    @Column(nullable = false)
    private String voucherPrefix;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferState state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferKind kind;

    /**
     * How many of it exist in total, and null when the offer never runs out.
     *
     * <p><strong>How many exist, not how many are left.</strong> The difference is the whole of
     * this column's design. What is left is {@code stock} less the claims already made, worked out
     * every time anybody asks and written down nowhere — because two stored figures that must
     * agree eventually stop agreeing, and a "remaining" column would have to agree with a table of
     * claims that a claim, a restock and one day a cancellation all move. The application already
     * holds that line in two places: a points balance is a sum over unexpired batches, and a
     * goal's status is computed against its projection. {@link #whatIsLeftAfter} is the whole of
     * the arithmetic.
     *
     * <p>So this number only ever moves when an administrator moves it. Raising it puts the offer
     * back in the window on the next read, with no job to run and no flag to clear, and lowering
     * it below what has already gone out is refused in {@link RewardsService} rather than allowed
     * to make the figure on the screen a lie.
     *
     * <p>Null on all four seeded offers, which is what makes them behave exactly as the constants
     * they replaced: no stock is unlimited, in the same direction every other absent rule here
     * reads.
     */
    private Integer stock;

    /**
     * The first day it can be claimed, and null when it has always been open.
     *
     * <p>A {@link LocalDate} and not an instant, copying {@link io.dataroots.savingstreak.challenges.Campaign}
     * — the only availability window this codebase has, and one that already behaves under a
     * wound-forward clock. A season is a thing announced on a poster and so is an offer's window,
     * and which day it is where the offer is being run is the question {@link #theDayOf} answers.
     */
    private LocalDate opensOn;

    /**
     * The last day it can be claimed, <strong>inclusive</strong>, and null when it never closes.
     *
     * <p>Inclusive because that is what every person reading "closes on the thirtieth" assumes,
     * and because the season next door made the same promise for the same reason: a customer who
     * read the date off the card and came back on the last afternoon would otherwise be refused
     * for no reason they could see.
     */
    private LocalDate closesOn;

    /**
     * What it costs while the discount below is running, and null when there is no discount.
     *
     * <p>Boxed, because null is the answer for every offer that is not on promotion — which is
     * all four seeded ones and most of what anybody writes — and a primitive would read that as
     * nought points, a price this application refuses in a sentence. It is always strictly below
     * {@link #costInPoints}: a "discount" at or above the ordinary price is a card striking a
     * figure through to advertise a saving that is not there, and {@link RewardsService} refuses
     * it before it can be written.
     *
     * <p>It is never the price on a claim. What was paid is written onto the claim at the moment
     * it was made, which is why a promotion ending never reprices somebody's history — the same
     * reason {@link #costInPoints} itself may be edited under a live offer.
     */
    private Long discountedCostInPoints;

    /**
     * The first day the discounted price applies, and null when there is no discount at all.
     *
     * <p>A {@link LocalDate} in the zone the offer's own window is read in, for the reason that
     * one gives: a promotion is a thing announced on a poster, and an instant would start it an
     * evening early for anybody reading west of here.
     *
     * <p><strong>Never null while there is a discounted price.</strong> The three columns are one
     * fact in three parts and this application stores all three or none of them. A price with no
     * window is not a promotion, it is a repricing with a struck-through figure that was never
     * charged; a window with no price is two dates that change nothing, and nobody reading the
     * row afterwards could tell it from a discount that silently failed to apply.
     */
    private LocalDate discountOpensOn;

    /**
     * The last day the discounted price applies, <strong>inclusive</strong>, and null when there
     * is no discount at all.
     *
     * <p>Inclusive for the same reason {@link #closesOn} is, and it matters more here: a customer
     * who read "half price until the eighth" off the card and came back on the eighth afternoon
     * would otherwise be charged the full price by an application that had told them otherwise
     * that morning.
     */
    private LocalDate discountClosesOn;

    /** How many one customer may ever have, and null when there is no lifetime cap. */
    private Integer maxPerCustomer;

    /** How many one customer may have in a savings week, and null when there is no weekly cap. */
    private Integer maxPerCustomerPerWeek;

    /** The streak an offer asks for before it can be claimed, in weeks, and null when it asks none. */
    private Integer minimumStreakWeeks;

    /**
     * The achievement badge an offer asks for, and null when it asks none.
     *
     * <p>Text rather than the badge type the challenges module holds, because this module reads no
     * other module: eligibility is decided against the facts the web layer hands in, and a column
     * naming another module's enum would be this one taking a dependency it has spent the whole
     * feature avoiding.
     */
    private String requiresBadge;

    /** The lifetime of points earned an offer asks for, and null when it asks none. */
    private Long minimumLifetimePointsEarned;

    /**
     * How long a voucher for this is good for, in days, and null when it never expires.
     *
     * <p>Null on all four seeded offers, which is the rail the whole shelf-life slice rests on: a
     * voucher issued from an offer that never named a number is written with no day on it, the
     * nightly sweep cannot see it whatever the clock says, and nothing already in anybody's pocket
     * changes meaning. Setting a number here changes what the <em>next</em> voucher is issued
     * with and nothing else — the day a voucher runs out is copied onto the claim when it is made,
     * for the reason written beside that column on {@link Redemption}.
     *
     * <p>A count of days rather than a date, because a shelf life is a property of the offer and a
     * deadline is a property of a voucher. Every customer who claims this gets the same number of
     * days from the day they claimed, which is what "valid for a month" means to the person
     * reading the card; a fixed date on the offer would be a different promise — the same deadline
     * for somebody who claimed this morning and somebody who claimed in January — and it is the
     * offer's own closing day, which is a separate column above.
     */
    private Integer voucherValidForDays;

    protected RewardOffer() {
        // for JPA
    }

    private RewardOffer(String code, String title, String words, long costInPoints,
                        String voucherPrefix, OfferState state, OfferKind kind,
                        LocalDate opensOn, LocalDate closesOn) {
        this.code = code;
        this.title = title;
        this.words = words;
        this.costInPoints = costInPoints;
        this.voucherPrefix = voucherPrefix;
        this.state = state;
        this.kind = kind;
        this.opensOn = opensOn;
        this.closesOn = closesOn;
    }

    /**
     * An offer as the seed describes it: the five things the enum carried, published, a plain item,
     * and every rule below left unset.
     *
     * <p>The one constructor there is for now, and deliberately so. An administrator's offer arrives
     * with a window or a stock figure on it and that is the slice that adds the way to say so;
     * until then, every row this application writes is one of the four it has always had, and a
     * factory that could not express anything else is a factory that cannot get one of them wrong.
     */
    static RewardOffer onOfferAlready(String code, String title, String words, long costInPoints,
                                      String voucherPrefix) {
        return new RewardOffer(code, title, words, costInPoints, voucherPrefix,
                OfferState.PUBLISHED, OfferKind.ITEM, null, null);
    }

    /**
     * An offer as somebody running the scheme has just written it: their five figures, not on sale.
     *
     * <p>A draft, always, with no way to ask for anything else. A half-written reward on a
     * customer's screen is the thing drafts exist to prevent, and a factory that could be handed
     * {@code PUBLISHED} would let one request skip the read-back that publishing is. It goes on
     * sale when somebody presses publish, which is a second decision and leaves a second line in
     * the log.
     *
     * <p>An item, always, for the same reason the seed writes items: a bundle is a row with member
     * lines hanging off it, nothing can write one yet, and a kind chosen here would be a choice
     * with one valid answer.
     *
     * <p><strong>The window is the first thing below the prefix somebody can now say.</strong> It
     * arrives here rather than being set afterwards because a season is something an offer is
     * written <em>with</em> — "this one runs through December" is part of writing it, not an edit
     * to it — and a factory that could only produce an always-open offer would make every windowed
     * one a create followed by a change, with a moment in between where the catalogue held
     * something nobody meant. Both days stay optional and both stay null on a form that said
     * nothing about them, which is the same reading every other absent rule gets: no window is
     * always open.
     *
     * <p>Every column below the window is still left null, exactly as the seeded four leave them,
     * because a null is the absence of a rule in every direction — no stock, no cap, no shelf
     * life — and an offer somebody has only just named has said none of those things yet.
     *
     * <p>A new offer that does say one of those things says it through that rule's own mutator,
     * immediately afterwards and through the same door an edit uses, rather than through a factory
     * that grows a parameter per slice until nobody can read a call to it. The shelf life is the
     * first of them and arrives exactly that way: {@link #goodForDays} is called on the draft the
     * moment it has been built.
     */
    static RewardOffer asADraft(String code, String title, String words, long costInPoints,
                                String voucherPrefix, LocalDate opensOn, LocalDate closesOn) {
        return new RewardOffer(code, title, words, costInPoints, voucherPrefix,
                OfferState.DRAFT, OfferKind.ITEM, opensOn, closesOn);
    }

    String code() {
        return code;
    }

    String title() {
        return title;
    }

    long costInPoints() {
        return costInPoints;
    }

    /** What the card says under the title, and null when the offer has never said anything. */
    String words() {
        return words;
    }

    /** Three letters at the front of a voucher, so a person holding one can see what it is for. */
    String voucherPrefix() {
        return voucherPrefix;
    }

    /**
     * How many days a voucher issued from here is good for, and null when it never runs out.
     *
     * <p>Read at the one moment it matters — the claim — and nowhere else. Nothing asks an offer
     * whether a voucher has expired, because the voucher itself carries the day it was promised.
     */
    Integer voucherValidForDays() {
        return voucherValidForDays;
    }

    /**
     * The rules this offer carries about who may claim it, as one thing.
     *
     * <p>Gathered into {@link WhoAnOfferIsFor} rather than handed out as three columns, because
     * three columns is what a caller would then have to know the ANDing and the order of. What
     * leaves here is the question "who is this for", and the answer to "and does this customer
     * qualify" is a pure function of it and a {@link CustomerStanding} — which is the whole
     * arrangement that lets eligibility be exercised without a streak, a badge and a points
     * history being arranged at once.
     */
    WhoAnOfferIsFor whoItIsFor() {
        return new WhoAnOfferIsFor(minimumStreakWeeks, requiresBadge, minimumLifetimePointsEarned);
    }

    /** The run of weeks it asks for, and null when it asks for none. */
    Integer minimumStreakWeeks() {
        return minimumStreakWeeks;
    }

    /** The badge it asks for, by the code the trophy case names it, and null when it asks none. */
    String requiresBadge() {
        return requiresBadge;
    }

    /** The lifetime of points earned it asks for, and null when it asks for none. */
    Long minimumLifetimePointsEarned() {
        return minimumLifetimePointsEarned;
    }

    OfferState state() {
        return state;
    }

    /**
     * How many of it exist in total, and null when it never runs out.
     *
     * <p>Read by whoever runs the catalogue, who set it, and by the arithmetic below. A customer
     * is never shown this figure — what a customer is told is how many are <em>left</em>, which is
     * a different number and the only one they can act on.
     */
    Integer stock() {
        return stock;
    }

    /** The first day it can be claimed, and null when it has always been open. */
    LocalDate opensOn() {
        return opensOn;
    }

    /** The last day it can be claimed, inclusive, and null when it never closes. */
    LocalDate closesOn() {
        return closesOn;
    }

    /** How many one customer may ever have, and null when there is no lifetime cap. */
    Integer maxPerCustomer() {
        return maxPerCustomer;
    }

    /** How many one customer may have in a savings week, and null when there is no weekly cap. */
    Integer maxPerCustomerPerWeek() {
        return maxPerCustomerPerWeek;
    }

    /** What it costs while its promotion is running, and null when it has no promotion. */
    Long discountedCostInPoints() {
        return discountedCostInPoints;
    }

    /** The first day of that promotion, and null when there is none. */
    LocalDate discountOpensOn() {
        return discountOpensOn;
    }

    /** The last day of it, inclusive, and null when there is none. */
    LocalDate discountClosesOn() {
        return discountClosesOn;
    }

    /** Whether a customer may be shown this and may claim it: published, and nothing else. */
    boolean isOnSale() {
        return state == OfferState.PUBLISHED;
    }

    /** Whether this offer's life is over, which is what makes every change to it a refusal. */
    boolean isWithdrawn() {
        return state == OfferState.WITHDRAWN;
    }

    /**
     * Whether this is several things handed over together rather than one thing.
     *
     * <p>A question rather than the kind handed out, for the reason {@link #isOnSale} is one:
     * every caller wants the answer and none of them wants the vocabulary, and a {@code kind()}
     * accessor would invite a second {@code switch} over {@link OfferKind} in a class that has
     * no business knowing there is an enum at all.
     *
     * <p>What it does <em>not</em> answer is what is in it. The lines live in a table of their
     * own and this row has none of them on it, which is the same distance a claim keeps from the
     * offer it names: {@link RewardsService} reads them and hands them back in.
     */
    boolean isABundle() {
        return kind == OfferKind.BUNDLE;
    }

    /**
     * Renames it, and there is deliberately nothing here that renames its code.
     *
     * <p>The first mutators this class has ever had, and they are written as four small sentences
     * about what somebody did rather than as setters. A setter is a statement that a field is
     * public with extra steps: {@code setState} would make publishing, withdrawing and putting an
     * offer back into draft the same call with an argument, and the one thing this state machine
     * has to enforce — that withdrawn is the end — would have nowhere to live. Named changes also
     * read back in the log as what they were.
     *
     * <p>There is no {@code renameCode}, and its absence is the whole of the rule. A claim already
     * made names this offer by its code, so a code that could change would take a voucher away from
     * whoever is holding it. The refusal lives in {@link RewardsService} because refusing is a
     * sentence somebody reads; the reason there is nothing here to call is that the rule is not a
     * check this class could fail, it is a change it cannot make.
     */
    void retitle(String title) {
        this.title = title;
    }

    /** Changes what the card says underneath the title. */
    void sayInstead(String words) {
        this.words = words;
    }

    /** Changes what it costs. What was already paid for it is on the claim and does not move. */
    void reprice(long costInPoints) {
        this.costInPoints = costInPoints;
    }

    /**
     * Sets the day it opens on, or takes the day away and leaves it open from the start.
     *
     * <p>A named change beside {@link #reprice} rather than a setter, for the reason that method's
     * neighbours give at length: a change worth logging is a change worth naming, and "the offer
     * was opened on the third" reads back out of the log as something somebody did.
     *
     * <p>Null is a real instruction here and means the offer no longer has a first day. It is not
     * the same as this method never being called — which is why {@link AChangeToAnOffer} carries a
     * flag beside the day saying whether the day is being changed at all, and why this class is
     * only ever reached once that flag has been read. Nothing here goes backwards over the other
     * end of the window either: whether the pair makes a window anybody could claim in is a rule
     * about the offer as a whole, and {@link RewardsService} refuses it in a sentence before it
     * gets this far.
     */
    void openOn(LocalDate opensOn) {
        this.opensOn = opensOn;
    }

    /** Sets the last day it can be claimed on, inclusive, or takes the day away. */
    void closeOn(LocalDate closesOn) {
        this.closesOn = closesOn;
    }

    /**
     * Changes the three letters the next voucher is stamped with. Vouchers already issued keep the
     * code they were printed with: a voucher's code is stored on the claim, so it is a fact about
     * something that happened rather than a thing derived from the offer every time it is read.
     */
    void stampVouchersWith(String voucherPrefix) {
        this.voucherPrefix = voucherPrefix;
    }

    /**
     * Says how long the vouchers this offer issues from now on are good for.
     *
     * <p>From now on, and never retrospectively, for exactly the reason the prefix above is not
     * retrospective either: what a voucher promises is written onto the voucher when it is issued.
     * Raising the number does not give somebody holding a code from last month longer, and
     * lowering it does not take their days away — which is the only reading under which a shelf
     * life is a promise rather than a setting.
     *
     * <p>The one mutator both the writing of a draft and the editing of a live offer go through,
     * so that what a shelf life is allowed to be is decided in one place. What it is allowed to be
     * is checked in {@link RewardsService} before this is called, because refusing is a sentence
     * somebody reads.
     */
    void goodForDays(Integer voucherValidForDays) {
        this.voucherValidForDays = voucherValidForDays;
    }

    /**
     * Says that only a customer on a run of at least this many secured weeks may claim it, or
     * takes the rule away.
     *
     * <p>Three named changes rather than one {@code restrictTo} taking all three, and the reason
     * is the reason there is no {@code setState}: an administrator adds a streak requirement to
     * an offer that already asks for a badge, and a single mutator would make that a call that
     * has to repeat the badge in order not to lose it. Each rule is set, changed and cleared on
     * its own, and each reads back out of the log as the thing somebody did.
     *
     * <p>Nothing here judges the number. What a threshold is allowed to be is checked in
     * {@link RewardsService} before this is called, because refusing is a sentence somebody
     * reads — the same division the shelf life above draws.
     *
     * <p>Applies from now on and never retrospectively, like everything else on this row: a
     * voucher already issued is a fact about a claim that was allowed at the time, and raising
     * the bar takes nothing back off anybody.
     */
    void requireAStreakOf(Integer minimumStreakWeeks) {
        this.minimumStreakWeeks = minimumStreakWeeks;
    }

    /** Says that only a customer holding this badge may claim it, or takes the rule away. */
    void requireTheBadge(String requiresBadge) {
        this.requiresBadge = requiresBadge;
    }

    /** Says how many points they must have earned in all to claim it, or takes the rule away. */
    void requireALifetimeOfPointsEarnedOf(Long minimumLifetimePointsEarned) {
        this.minimumLifetimePointsEarned = minimumLifetimePointsEarned;
    }

    /**
     * Says how many of this one customer may ever have, or takes the lifetime cap off.
     *
     * <p>A named change beside {@link #goodForDays} rather than a setter, for the reason its
     * neighbours give at length. The one door both the writing of a draft and the editing of a
     * live offer go through, so that what a cap is allowed to be is decided in one place — and
     * what it is allowed to be is checked in {@link RewardsService} before this is called,
     * because refusing is a sentence somebody reads.
     *
     * <p><strong>Lowering it takes nothing away from anybody, and cannot.</strong> A claim
     * already made is a voucher somebody is holding; what changes is only what the
     * <em>next</em> claim is judged against, which is the same promise the shelf life above
     * makes and for the same reason. A cap set below what somebody has already had leaves them
     * locked out of another rather than owing one back, which is the only reading under which a
     * limit is a limit rather than a debt.
     */
    void atMostPerCustomer(Integer maxPerCustomer) {
        this.maxPerCustomer = maxPerCustomer;
    }

    /** And how many they may have in one savings week, or takes the weekly cap off. */
    void atMostPerCustomerPerWeek(Integer maxPerCustomerPerWeek) {
        this.maxPerCustomerPerWeek = maxPerCustomerPerWeek;
    }

    /**
     * Says how many of this there are — a restock, or the first time anybody said there was a
     * limit at all.
     *
     * <p>A named change beside {@link #reprice} rather than a setter, for the reason its
     * neighbours give: "the hamper was stocked with forty" reads back out of the log as something
     * somebody did, and a {@code setStock} would be the field made public with extra steps.
     *
     * <p><strong>It takes the total rather than a difference, and takes nothing away from
     * anybody.</strong> Raising it is a restock and the offer is claimable again on the very next
     * read, because what is left is derived and there is no cached verdict to clear. Lowering it
     * below what has already gone out is the one thing it may not do, and that is refused in
     * {@link RewardsService} before this is reached — refusing is a sentence somebody reads, and
     * the count of what has gone lives in the claims rather than here.
     */
    void stockedWith(Integer stock) {
        this.stock = stock;
    }

    /**
     * Makes it the sort of offer that is handed over as several things at once.
     *
     * <p>A named change rather than a setter, like every other change here, and a one-way one:
     * there is no method that turns a bundle back into an item. That is not an oversight and not
     * a wart either — the lines are written once, in the same transaction as the row, by the one
     * caller that composes a bundle, and an offer that stopped being one would leave every
     * voucher already issued for it describing a thing the catalogue no longer knows how to hand
     * over. Somebody who wants to sell the members separately publishes the members separately,
     * which they already are: a bundle is composed out of offers that exist in their own right.
     *
     * <p>It takes no lines, because the lines are not on this row. What keeps the two in step is
     * that {@link RewardsService} is the only thing that calls this and it calls it in the same
     * breath as it writes them.
     */
    void handedOverAsABundle() {
        this.kind = OfferKind.BUNDLE;
    }

    /**
     * Puts a promotion on it, or takes the promotion off — all three parts of it, in one call.
     *
     * <p><strong>One mutator for the three, rather than three beside each other.</strong> The
     * window above got a method per day because either end of it genuinely stands alone: an offer
     * that opens in December and never closes is an ordinary thing to write. A promotion is not
     * like that. A price with no days and days with no price are both states this application
     * will not store, so a {@code discountTo} that could be called without a
     * {@code discountFrom} would be a door onto a row that is only ever a mistake — and the
     * moment between two calls would be exactly that row. Handing all three over at once means
     * the object is never half on promotion, not even for a statement.
     *
     * <p>Three nulls is a real instruction and means the promotion is over: an administrator who
     * called a season off leaves an offer back at its ordinary price with nothing struck through.
     * What it may be when it is not three nulls — below the ordinary price, at least a point, and
     * a window that does not run backwards — is checked in {@link RewardsService} before this is
     * called, because refusing is a sentence somebody reads.
     */
    void runAPromotion(Long discountedCostInPoints, LocalDate discountOpensOn,
                       LocalDate discountClosesOn) {
        this.discountedCostInPoints = discountedCostInPoints;
        this.discountOpensOn = discountOpensOn;
        this.discountClosesOn = discountClosesOn;
    }

    /**
     * Puts it on sale. Pressed on something already on sale, it changes nothing and says so — the
     * same reading pausing an already-paused saving rule is given, because the person pressing it
     * wants the offer published and it is.
     */
    void publish() {
        this.state = OfferState.PUBLISHED;
    }

    /** Takes it down for good. Never a deletion: everything already claimed still points here. */
    void withdraw() {
        this.state = OfferState.WITHDRAWN;
    }

    /**
     * Whether the day this offer opens on has arrived, which is true of an offer that has no
     * first day.
     *
     * <p>A day handed in rather than a clock read here. The entity is not a bean and has nothing
     * injected into it, and — more to the point — every offer in one reading of the catalogue has
     * to be judged against the <em>same</em> day: a list that read the clock once per row could
     * cross midnight half way down and show one customer two different todays.
     */
    boolean hasOpenedBy(LocalDate today) {
        return opensOn == null || !today.isBefore(opensOn);
    }

    /**
     * Whether the day this offer closes on has gone past — false all through the closing day
     * itself, and false forever on an offer that never closes.
     */
    boolean hasClosedBy(LocalDate today) {
        return closesOn != null && today.isAfter(closesOn);
    }

    /**
     * Whether the promotion is running on the day handed in — false all the way through on an
     * offer that has no promotion at all.
     *
     * <p>Both ends inclusive, and both asked rather than assumed. The three columns are written
     * as a set and {@link RewardsService} refuses any other shape, so the two null checks on the
     * days can never fire against a row this application wrote; they are here because a
     * derivation that would throw on a row somebody put in the database by hand is a worse thing
     * to own than two comparisons nobody needs.
     *
     * <p>A day handed in, never a clock read here, for the reason {@link #hasOpenedBy} gives at
     * length: one reading of the catalogue has to price every row against the same day, and a
     * list that read the clock once per row could cross midnight half way down and take a
     * promotion off one card while leaving it on the next.
     */
    boolean isOnPromotionOn(LocalDate today) {
        return discountedCostInPoints != null
                && discountOpensOn != null && !today.isBefore(discountOpensOn)
                && discountClosesOn != null && !today.isAfter(discountClosesOn);
    }

    /**
     * What this offer costs on the day handed in: the discounted price while the promotion runs,
     * and the ordinary price on every other day.
     *
     * <p><strong>The one place in this application that answers "what does it cost".</strong>
     * Every reading of the catalogue, every refusal that quotes a figure and the spend itself go
     * through here, because the alternative is the failure the spec names by its symptom: a card
     * saying one price and a claim charging another. A page that subtracted a discount for itself
     * would be the same bug one layer further out, which is why the reading carries both figures
     * and the page carries no arithmetic.
     */
    long costOn(LocalDate today) {
        return isOnPromotionOn(today) ? discountedCostInPoints : costInPoints;
    }

    /**
     * The price to strike through on the day handed in — the ordinary one while the promotion
     * runs, and nothing at all otherwise.
     *
     * <p>Null rather than the ordinary price repeated, because null is what a page needs in order
     * to draw nothing. A card handed two equal figures would either strike one of them through
     * for no saving or have to compare them, and comparing them is the arithmetic this whole
     * design refuses to put on a page.
     */
    Long theOrdinaryPriceStruckThroughOn(LocalDate today) {
        return isOnPromotionOn(today) ? costInPoints : null;
    }

    /**
     * Whether this customer has had every one of these that a person is ever allowed, which is
     * false of an offer nobody capped.
     *
     * <p>A count handed in rather than counted here, exactly as the day above is handed in
     * rather than read off a clock. This class has no repository and is not a bean, and — more
     * to the point — every offer in one reading of the catalogue has to be judged against
     * <em>one</em> pass over that customer's claims: a row that counted for itself would be a
     * query per card, and two of them could disagree about a claim made while the page was being
     * drawn.
     *
     * <p>At or above, not above. A cap of two means two is all of them: somebody who has had two
     * has had their limit, and a comparison that only locked at three would hand out one more of
     * everything than anybody ever set.
     */
    boolean theyHaveHadTheirLifetimeLimit(long everHad) {
        return maxPerCustomer != null && everHad >= maxPerCustomer;
    }

    /** The same question about one savings week, which is the cap that comes back on a Monday. */
    boolean theyHaveHadTheirLimitThisWeek(long hadThisWeek) {
        return maxPerCustomerPerWeek != null && hadThisWeek >= maxPerCustomerPerWeek;
    }

    /**
     * How many more of it this customer may still have, and null when nothing caps them.
     *
     * <p><strong>The tighter of the two caps, because that is the one they will actually meet
     * next.</strong> An offer limited to five in a lifetime and one a week says "one" to
     * somebody who has had none of it, and saying "five" would be the card promising four claims
     * that this week's rule is about to refuse. The figure is what a page shows beside the
     * limit, and a page that had to work it out would need both counts, both caps and the
     * application's idea of which week it is.
     *
     * <p>Never below nought. A cap lowered underneath somebody who had already had more than it
     * allows is an administrator's decision rather than a debt, and "minus one left" is not a
     * sentence about anything.
     *
     * <p>Null rather than a large number for an offer with no cap at all, so that "there is no
     * limit" and "you may have a great many" stay different answers. Every seeded offer is in
     * the first of them.
     */
    Long howManyMoreTheyMayHave(long everHad, long hadThisWeek) {
        Long left = null;
        if (maxPerCustomer != null) {
            left = Math.max(0L, maxPerCustomer - everHad);
        }
        if (maxPerCustomerPerWeek != null) {
            long leftThisWeek = Math.max(0L, maxPerCustomerPerWeek - hadThisWeek);
            left = left == null ? leftThisWeek : Math.min(left, leftThisWeek);
        }
        return left;
    }

    /**
     * How many are left once this many are spoken for, and null when the offer never runs out.
     *
     * <p><strong>"Spoken for" and not "claimed", and the difference arrived with holds.</strong>
     * The figure handed in is every one of this offer that is not available to the next person
     * who asks: the ones that have gone out as vouchers, and the ones somebody has set aside and
     * has not yet decided about. Both are subtracted here rather than one here and one somewhere
     * else, because there is only one question — how many could somebody have — and an offer
     * whose last one is held has none. Which of the two a missing one is belongs to whoever is
     * doing the counting; this method is the subtraction and nothing more.
     *
     * <p><strong>The whole of the scarcity arithmetic, in one line, in the one place.</strong>
     * Derived on every read and stored nowhere: a second column holding what is left would have to
     * be decremented by the claim, incremented by a restock and — the day cancelling lands —
     * incremented again by something that undoes a claim, and any one of those going missing is a
     * catalogue quietly lying about what it has. The spec's own line applies and is the reason
     * this method exists rather than a column: two stored figures that must agree eventually stop
     * agreeing.
     *
     * <p>The count is handed in rather than fetched, for the reason the day is handed to
     * {@link #hasOpenedBy}: the entity is not a bean, has no repository in it, and — more to the
     * point — every offer in one reading of the catalogue should be judged against figures read in
     * one pass rather than against whatever each row happened to see.
     *
     * <p>Never below nought, although nothing this application does can get it there: the
     * administrator who would have to lower the stock under what has gone out is refused in a
     * sentence first. A database that has been through something else can carry anything, and
     * "-3 left" on a card would be worse than "sold out", which is what it truthfully is.
     */
    Integer whatIsLeftAfter(long alreadyGone) {
        if (stock == null) {
            return null;
        }
        return (int) Math.max(0, stock - alreadyGone);
    }

    /**
     * The entry as everything outside this module reads it: a code, words, and a price.
     *
     * <p><strong>The ordinary price, and no day in the signature.</strong> The promotion slice
     * first made this take a day and quote {@link #costOn}, and that was taken back out: the
     * address this feeds is the catalogue with nobody in it, which the window slice established
     * is customer-free <em>and</em> clock-free — it does not filter by an offer's window either.
     * An address that read the clock for the price and ignored it for availability would be
     * inconsistent with itself. Every "today" the promotion is about belongs on the
     * customer-shaped reading, which has a day, a person and room to say what is going on.
     */
    ARewardOnOffer onOffer() {
        return new ARewardOnOffer(code, title, words, costInPoints);
    }

    /**
     * The entry as a customer reads it when nothing at all is standing in their way.
     *
     * <p>The window still travels on it. An offer a customer can claim today and which closes on
     * the thirtieth is exactly the offer they need to be told the thirtieth about, because that is
     * the date that decides whether saving for it is worth starting.
     *
     * <p>How many are left travels the same way and for the same reason: a customer wants to be
     * told there are three of something <em>while they can still have one</em>, which is the whole
     * of "so that I know whether to hurry". It is handed in rather than worked out here, because
     * it is {@code stock} less the claims already made and this class has no claims in it —
     * {@link #whatIsLeftAfter} is the arithmetic and {@link RewardsService} is what counts.
     *
     * <p>Everything it needs arrives as one {@link WhatIsKnownToday} rather than as a parameter
     * per rule, and the argument for that is written out on that record.
     */
    AnOfferAsACustomerReadsIt claimable(WhatIsKnownToday known) {
        return reading(known, true, null, null);
    }

    /**
     * The entry as the one customer who has it set aside reads it: claimable, with the moment
     * their hold runs out on it.
     *
     * <p><strong>Claimable and not locked, which is the decision this method exists to make
     * visible.</strong> Their own hold has taken the last one out of {@code whatIsLeft} — it has
     * to, because held stock is unavailable and the arithmetic cannot know whose reading it is
     * feeding — so a card drawn by the ordinary path would read "sold out" to the one person the
     * thing is actually being kept for. The reading therefore returns here before the stock
     * question is asked at all. A hold is an affordance: it is the scheme having said yes, and a
     * card saying no to it would be this application taking something away and then refusing to
     * give it back. The full argument is on {@link AnOfferAsACustomerReadsIt}.
     *
     * <p>No sentence and no lock value beside it, deliberately. There is nothing in the way, so
     * there is nothing to explain — what the page needs is the moment, and it gets it.
     */
    AnOfferAsACustomerReadsIt heldByThem(WhatIsKnownToday known) {
        return reading(known, true, null, null);
    }

    /**
     * The same entry with one thing in the way: the value a page styles by, and the sentence a
     * person reads.
     *
     * <p>Shown rather than left out, always, and that is the decision this method exists to make
     * unavoidable. An offer that is hidden tells a customer nothing; an offer that says it opens
     * on the third tells them to come back on the third, and the whole argument for a scheme
     * somebody runs is that a customer can see what it wants from them. The sentence is handed in
     * rather than built here because it is the same sentence a claim is refused with, and one
     * wording written twice is two wordings a week later.
     */
    AnOfferAsACustomerReadsIt lockedBecause(WhyAnOfferIsLocked why, String inWords,
                                            WhatIsKnownToday known) {
        return reading(known, false, why, inWords);
    }

    /**
     * The one place a card is built, whichever of the three ways it is being read.
     *
     * <p>Folded into one method when the hold arrived and a third caller would have been a third
     * copy of a seventeen-component constructor call. Two copies were already one too many: the
     * claimable and the locked readings differed in three arguments and agreed in fourteen, and
     * every slice that added a component had to add it twice and in the same position twice.
     *
     * <p><strong>{@code howManyYouHaveHad} is claims and only claims; the remainder counts the
     * hold.</strong> That is not an inconsistency, it is the two questions being different: what
     * somebody has <em>had</em> is what they have taken away with them, and a hold is a thing
     * they have not had yet. How many more they may have is about what the cap will still let
     * them take, and the cap has already been spoken for by the one they are holding. The
     * argument is written out on {@link WhatIsKnownToday#everHadOrHolds}.
     */
    private AnOfferAsACustomerReadsIt reading(WhatIsKnownToday known, boolean claimable,
                                              WhyAnOfferIsLocked why, String inWords) {
        LocalDate today = known.today();
        return new AnOfferAsACustomerReadsIt(code, title, words, costOn(today), claimable, why,
                inWords, opensOn, closesOn, maxPerCustomer, maxPerCustomerPerWeek,
                known.haveGone().everHad(),
                howManyMoreTheyMayHave(known.everHadOrHolds(), known.hadOrHoldsThisWeek()),
                known.whatIsLeft(), theOrdinaryPriceStruckThroughOn(today),
                isOnPromotionOn(today) ? discountClosesOn : null,
                known.theirHoldLapsesAt(), known.theirPlaceInTheQueue(), known.contents());
    }

    /**
     * The row as whoever runs the catalogue reads it: everything above, including the two things a
     * customer is never shown.
     *
     * <p>A second reading of the same entity rather than a second entity, and the argument for the
     * two records living side by side is on {@link AnOfferAsItStands}.
     *
     * <p>What it contains is handed in rather than read, for the reason every count on the
     * customer's reading is: the lines are a table and this class has no repository in it. Empty
     * for everything that is not a bundle, which is every offer this application seeds.
     */
    AnOfferAsItStands asItStands(List<WhatABundleContains> contents) {
        return new AnOfferAsItStands(code, title, words, costInPoints, voucherPrefix, state,
                opensOn, closesOn, voucherValidForDays, minimumStreakWeeks, requiresBadge,
                minimumLifetimePointsEarned, maxPerCustomer, maxPerCustomerPerWeek, stock,
                discountedCostInPoints, discountOpensOn, discountClosesOn, contents);
    }
}
