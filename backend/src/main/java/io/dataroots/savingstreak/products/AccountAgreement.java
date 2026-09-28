package io.dataroots.savingstreak.products;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * What one savings account is living under: the product it is on, the version of that product's
 * terms it was opened with, and the day it was opened on them.
 *
 * <p><strong>A row of this module rather than three columns on the savings account.</strong> The
 * account belongs to Accounts, which does not know that a product exists and must not learn: the
 * whole of the catalogue's dependency runs the other way. Columns on {@code savings_account} would
 * also have to survive {@code AccountsOnStartUp}, which rebuilds that table column by column to
 * relax the holder on an older file — a rebuild that copies the two columns it names and would
 * silently drop any third. A table this module owns is a table that migration cannot reach.
 *
 * <p><strong>The pair, never a copy of the numbers.</strong> This row says "free savings, version
 * 1" and nothing about what version 1 pays. Copying the rates onto the account would put the
 * agreement in two places, and the second one would be the one nobody remembered to change; worse,
 * it would make an account's terms editable by whoever could write this row.
 * {@link ProductTerms} argues the whole of that at length, and this is the row it was arguing for.
 *
 * <p><strong>The day it was opened is the account's own date and not the version's.</strong> The
 * version has an effective date — the day the bank started selling it — and an account opened eight
 * months later is still on it. What is written here is the day this agreement began, which is what
 * a monthly interest period will be counted from, what a term will mature from, and what the
 * customer reads on the screen as the day they started.
 *
 * <p><strong>The day interest starts counting is a second date, and it is not always the
 * first.</strong> For an account opened on this application's own door the two are the same day.
 * For an account that was already there when the catalogue arrived they are not: its agreement is
 * dated at the day its money first arrived, which may be a year ago, and paying it a year of
 * interest on the morning of an upgrade would be a lab waking up with money nobody can explain. The
 * spec rules that out in so many words, so the migration writes the day it ran and the sweep
 * refuses to pay for any period that began before it. Two dates rather than one, because they
 * answer two questions: how long this agreement has been running, and how far back this bank has
 * been paying interest on it.
 *
 * <p><strong>One agreement per account, and the database keeps that rule</strong> through an index
 * {@link ProductsOnStartUp} creates, for the same SQLite reason every other guarantee in this
 * application is made that way. Two rows for one account would be two answers to "what am I on",
 * and the account pointing at neither could not say which.
 *
 * <p><strong>And the day it ended, which is what closing a savings account means here.</strong>
 * Nothing in this application is ever deleted, and an account is no exception: a closed account
 * keeps its identifier, its deposits, its withdrawals, its goals and the whole of its history, and
 * what changes is that this row now names a day it stopped. That is the shape an ended bill, an
 * abandoned goal and a withdrawn offer all take — a row that says when it stopped rather than a row
 * that went away — and it is the shape for the same reason: what was true is a record, and a record
 * that is gone cannot be asked about.
 *
 * <p><strong>Here rather than on the savings account, and the argument is the one above made
 * twice.</strong> The account belongs to Accounts, which cannot be told that products exist and
 * cannot ask what an account still holds — so it could neither write this column with a reason nor
 * refuse to write it. And a column on {@code savings_account} is a column {@code AccountsOnStartUp}
 * silently drops on the start that rebuilds that table. What an account's life on a product is,
 * including the day it ended, is this module's record to keep.
 *
 * <p><strong>Closing is one-way, and there is deliberately no reopening.</strong> A product's door
 * to new accounts is a flag that goes both ways because it is a fact about what the bank is selling
 * today; this is a fact about something a customer did, and the precedent for those in this
 * codebase — an ended bill, an abandoned goal, a withdrawn offer — is a door that only opens
 * outwards. Somebody who wants to save on that product again opens an account on it, which is one
 * press and leaves the record of the one they closed exactly as it was.
 *
 * <p><strong>There is no history here, and that is a decision rather than an omission.</strong>
 * Taking a product's newer terms is a later ticket, and when it arrives it changes this row rather
 * than adding to it: what an account was on in March is answered by the interest postings and the
 * deposits of March, each of which names the version it was decided under. A second version table
 * would be a second record of the same thing, and the two would drift.
 *
 * <p>The account is held by identifier rather than by association, like every other reference to an
 * account outside the module that owns them.
 */
@Entity
class AccountAgreement {

    /** SQLite has no sequences, so identity values are generated by the database. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long savingsAccountId;

    private String productCode;

    private int version;

    private LocalDate openedOn;

    /**
     * The day the account was closed, and nothing at all while it is still open.
     *
     * <p>Null is the whole of "still open", which is why there is no state column beside it. An
     * ended bill and an abandoned goal each carry a state as well as a moment, because each of them
     * has a state that says something the date does not — a bill's cursor still moves, a goal still
     * holds or has given up its rank. An agreement has neither: there is one thing that can happen
     * to it and one day it happened on, and a second column would be a second answer to the same
     * question that could disagree with the first.
     *
     * <p>A day rather than a moment, matching {@link #openedOn} exactly. An agreement began on a day
     * and ended on a day; those are the two dates a customer reads on the same panel, and one of
     * them arriving as an instant would make a screen turn it into a day in whatever zone the screen
     * was standing in.
     */
    private LocalDate closedOn;

    /**
     * The first day this account could be paid interest for.
     *
     * <p>The day it was opened, for an account this application opened. The day the migration ran,
     * for an account that predates the catalogue — {@code SavingsAccountsGetAProductOnStartUp} is
     * where that is decided and why.
     *
     * <p>It does not anchor the periods; {@link #openedOn} does, so an account opened on the 20th
     * is paid for the 20th to the 20th whatever day the migration happened to run on. This says
     * which of those periods the bank is willing to pay for, which is every period that begins on
     * or after it.
     *
     * <p>Nullable in the database only because an agreement written before this column existed has
     * no value in it. {@link ProductsOnStartUp} fills those in with the day it runs — which is the
     * migration, for a file that has never paid a cent of interest — before the application serves
     * a single request.
     */
    private LocalDate interestCountsFrom;

    /**
     * The day this account's <em>current</em> term began running, which is the day it was opened
     * until a maturity has rolled it over.
     *
     * <p><strong>This column exists because a maturity date is derived rather than stored, and a
     * derived date has to be able to move.</strong> {@link TheTermAnAccountIsLockedInto} works a
     * maturity out as a day plus the months the terms name; that day used to be {@link #openedOn}
     * alone, and on the morning a rolling term reached its maturity the subtraction would go on
     * reporting the <em>first</em> maturity for ever — an account permanently unlocked while
     * nominally serving a twelve-month term, because nothing anywhere had moved.
     *
     * <p><strong>The alternative, rejected, was to re-date {@link #openedOn} on a roll-over.</strong>
     * It is the smaller change and it is the wrong one. That date anchors the monthly interest
     * periods: {@code TheMonthlyPeriodsOfAnAccount} counts ordinals from it, and an interest posting
     * is unique over the account and the ordinal. Moving it would restart those ordinals at one, so
     * the sweep would offer to pay again for months already recorded — and the unique index would
     * then refuse the second payment with a constraint violation rather than with a sentence, which
     * is a start-up-shaped failure arriving in the middle of a night's work. Two dates that answer
     * two questions cost one nullable column; one date answering both costs the interest record.
     *
     * <p><strong>Null is "the term began when the account did", and it is the ordinary case.</strong>
     * Every agreement written before this column existed reads null, and so does every account that
     * has never rolled over — which is all of them until the first maturity. Read through
     * {@link #theDayTheTermRunsFrom()} rather than directly, so that the fallback is written once.
     * There is deliberately no start-up back-fill: null already means exactly the right thing here,
     * where {@link #interestCountsFrom} needed one because null there meant the wrong thing twice
     * over.
     */
    private LocalDate termStartedOn;

    protected AccountAgreement() {
        // for JPA
    }

    private AccountAgreement(long savingsAccountId, String productCode, int version,
                             LocalDate openedOn, LocalDate interestCountsFrom) {
        this.savingsAccountId = savingsAccountId;
        this.productCode = productCode;
        this.version = version;
        this.openedOn = openedOn;
        this.interestCountsFrom = interestCountsFrom;
    }

    /**
     * An account put on a product under a named version of its terms, on a named day.
     *
     * <p>A factory rather than a public constructor, so that the sentence at the call site reads as
     * the event it is. Both callers are inside this module and there are exactly two of them: an
     * account that has just been opened, and an account that existed before this catalogue did.
     *
     * <p>They are also the two callers that disagree about the second date, which is why it is a
     * parameter rather than a copy of the first. A freshly opened account earns interest from the
     * day it opened; an account being migrated earns it from the day of the migration, however long
     * its money has been sitting there.
     */
    static AccountAgreement putting(long savingsAccountId, String productCode, int version,
                                    LocalDate openedOn, LocalDate interestCountsFrom) {
        return new AccountAgreement(savingsAccountId, productCode, version, openedOn,
                interestCountsFrom);
    }

    long savingsAccountId() {
        return savingsAccountId;
    }

    String productCode() {
        return productCode;
    }

    int version() {
        return version;
    }

    LocalDate openedOn() {
        return openedOn;
    }

    /** The day it was closed, and null while the account is still open. */
    LocalDate closedOn() {
        return closedOn;
    }

    /** Whether the account has been closed, which is the reading every rule wants of the date. */
    boolean isClosed() {
        return closedOn != null;
    }

    /**
     * Ends the agreement on the day named, and answers whether it was still running — so that a
     * second closing is a decision the caller takes rather than a date this row quietly overwrites.
     *
     * <p>Nothing else about the row moves. The product, the version and the day it began are what
     * the account lived under and go on saying so: a closed account still names the agreement its
     * deposits were made under, which is the whole reason the row is closed rather than removed.
     */
    boolean closeOn(LocalDate day) {
        if (closedOn != null) {
            return false;
        }
        closedOn = day;
        return true;
    }

    /**
     * Puts this account on another product, under a named version of that product's terms.
     *
     * <p><strong>The one mutator on this row that moves the account onto another product, and it
     * exists for exactly one event: a fixed term broken early.</strong> A locked account whose lock
     * has been paid off is not a locked account, and the honest way to say so is to move it onto
     * the product whose rules it now actually follows — free savings — rather than to add a flag
     * beside the term saying to ignore it. A flag would leave two readings of the same row, and the
     * day something read the product without the flag the money would be locked again.
     *
     * <p><strong>Two fields move and three deliberately do not.</strong> The day the agreement
     * began and the day its interest counts from answer how long this account has been running and
     * how far back this bank is willing to pay for it, and neither of those changed — the same
     * account has been open since the same morning, and the months already paid are recorded
     * against ordinals counted from that morning. Re-dating it would restart those ordinals and
     * offer to pay for months that have been paid. The day it was closed does not move either,
     * because a closed account is a record and nothing moves a record onto a new product.
     *
     * <p>No history is written, which is the decision the class note above already argues: what an
     * account was on in March is answered by the interest postings and the deposits of March, each
     * of which names the version it was decided under, and a second record of the same thing would
     * drift from the first.
     */
    void moveTo(String productCode, int version) {
        this.productCode = productCode;
        this.version = version;
    }

    /**
     * The first day a period may begin on and still be paid for, and nothing at all for a row
     * written before there was such a day.
     *
     * <p>Handed back as it is rather than read as the day the account was opened, because those two
     * readings differ by exactly the thing this column exists to prevent: an account migrated with
     * a year of history behind it would be paid for that year. A null can only be seen before
     * {@link ProductsOnStartUp} has filled it in, which is before the application serves anything,
     * and the sweep treats it as an account it cannot yet pay rather than as one it may pay from
     * the beginning.
     */
    LocalDate interestCountsFrom() {
        return interestCountsFrom;
    }

    /**
     * Puts this account on a newer version of the product it is already on.
     *
     * <p><strong>One field moves and every other one deliberately does not</strong> — not the
     * product, which has not changed; not the day the agreement began, which is still the morning
     * this account has been open since; not the day its interest counts from, which is still how far
     * back this bank is willing to pay for it; and not the day it was closed, because nothing moves
     * a record onto newer terms. Re-dating the agreement would restart the ordinals the interest
     * sweep counts periods by and offer to pay again for months already paid, which is the argument
     * {@link #moveTo} makes about the same two dates.
     *
     * <p><strong>Its own method rather than {@link #moveTo} called with the product it is already
     * on</strong>, and the reason is that these are two different events that happen to write
     * overlapping fields. Breaking a term moves an account onto <em>another product</em> because the
     * rules it now follows are that product's; this is the holder of an account choosing a newer
     * edition of the agreement they already have. Two names mean the log lines, the refusals and the
     * callers of each stay separately readable — and the day a rule has to differ between them,
     * there is somewhere for it to go that does not need a boolean.
     *
     * <p><strong>No history is written here either.</strong> What this account was on in March is
     * answered by the interest postings and the deposits of March, each of which names the version it
     * was decided under; the class note above argues why a second record of the same thing would
     * drift from the first, and taking newer terms is the event it was written in anticipation of.
     *
     * @param version the version of this same product the account is taking on, which its holder
     *                asked for by name — nothing in this application calls this on anybody's behalf
     *                except a maturity the holder agreed to when they opened the term
     */
    void takeTheNewerTermsOfTheSameProduct(int version) {
        this.version = version;
    }

    /**
     * The day the term now in force started running: the day it last rolled over, or the day the
     * account was opened when it never has.
     *
     * <p>The one reading of {@link #termStartedOn}, so that the fallback is decided here and not by
     * each caller. {@link TheTermAnAccountIsLockedInto#whenTheTermIsUp} is handed this rather than
     * {@link #openedOn}, and that one argument is the whole of what makes a roll-over produce a new
     * maturity a term further out instead of restating the one that has already passed.
     *
     * <p>It is emphatically <em>not</em> "the day this account began", which is {@link #openedOn}
     * and is what the screen shows, what the interest periods are counted from and what a customer
     * means by how long they have been saving. A rolled-over account has one of each and they say
     * different true things.
     */
    LocalDate theDayTheTermRunsFrom() {
        return termStartedOn == null ? openedOn : termStartedOn;
    }

    /**
     * Starts another term of the same product on the day the last one matured, pinned to the version
     * named.
     *
     * <p><strong>A mutator of its own rather than an argument on {@link #moveTo}, and the two must
     * not be merged.</strong> They are opposite events: that one takes an account off the product it
     * was on and deliberately leaves the term dates alone, this one keeps the product and moves
     * exactly the date that one refuses to. A single method taking both would be a method whose
     * meaning depended on which arguments were null, in a class where the argument about which
     * fields move is the entire point.
     *
     * <p><strong>The version moves because a roll-over is a new agreement.</strong> The spec's word
     * for it is honest: a term that reaches its day goes into another term at the terms on offer
     * <em>that day</em>, not the old one extended, so the account is pinned to whatever the product
     * is selling on the maturity morning. That is the same rule a freshly opened account follows and
     * the same rule breaking a term follows, and it is the reason the day is an argument rather than
     * read off a clock here: a sweep settling a maturity three months in the past has to pin the
     * version that was current on <em>its</em> day, not the one current tonight.
     *
     * <p><strong>The product does not move and neither do {@link #openedOn} nor
     * {@link #interestCountsFrom}.</strong> The account has been open since the same morning, the
     * months it has been paid for are recorded against ordinals counted from that morning, and this
     * bank has been willing to pay since the same day. A roll-over changes what the money is locked
     * into next, and nothing about how long the account has existed.
     */
    void rollTheTermOverInto(int version, LocalDate theDayTheNewTermStarts) {
        this.version = version;
        this.termStartedOn = theDayTheNewTermStarts;
    }
}
