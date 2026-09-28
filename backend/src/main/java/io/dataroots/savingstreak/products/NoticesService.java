package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.dataroots.savingstreak.deposits.AConditionInTheWay;
import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.deposits.ConditionOnTheWayOut;
import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.dataroots.savingstreak.deposits.AmountOfMoney.asMoney;

/**
 * The notice a customer has given on money in a savings account: giving it, reading what is ready
 * and what is still waiting, cancelling it, and spending it when a withdrawal runs on it.
 *
 * <p><strong>Nothing here is stored about whether a notice is ready.</strong> Every figure this
 * class answers with is worked out from the day a notice was given, the notice days the account's
 * agreement asks for and the day the injected clock is standing on — read once per question, at the
 * moment it is asked. {@link WhenANoticeIsReady} holds the arithmetic and argues the decision; this
 * class is where the clock is read and where the rows are walked.
 *
 * <p><strong>The agreement is asked rather than assumed.</strong> How many days an account gives
 * notice for is the version of the terms that account was opened under, which is
 * {@link WhatEachSavingsAccountIsOn}'s answer — so an account opened before a product lengthened
 * its notice period goes on giving the notice it agreed to, and this class never learns what a
 * product is beyond the number of days it was told.
 *
 * <p><strong>An account with nothing to give notice of is refused everything and gated by
 * nothing.</strong> Free savings and the core saver ask for no notice, so
 * {@link #whatNoticeStops} answers empty for them without reading a row, {@link #moneyHasLeft}
 * spends nothing, and giving notice on one is refused in a sentence saying the money is already
 * theirs. That is what makes this slice invisible to every account that existed before it.
 */
@Service
public class NoticesService {

    private static final Logger log = LoggerFactory.getLogger(NoticesService.class);

    private final NoticeRepository notices;
    private final WhatEachSavingsAccountIsOn agreements;
    private final Clock clock;

    NoticesService(NoticeRepository notices, WhatEachSavingsAccountIsOn agreements, Clock clock) {
        this.notices = notices;
        this.agreements = agreements;
        this.clock = clock;
    }

    /**
     * Records notice on an amount, starting the clock on it from today.
     *
     * <p><strong>Several at once, deliberately, and never merged into one.</strong> Two amounts a
     * customer is saving for two things are two notices with two ready dates, and adding a second
     * notice onto the first would move the money they gave notice of last week to the back of the
     * queue. Nothing here looks for a notice to extend.
     *
     * <p><strong>Not weighed against the balance.</strong> Notice is a statement about intent — a
     * customer who expects to be paid on Friday may give notice today on money that is not there
     * yet — and a withdrawal is weighed against the balance anyway, by the module that sums it. A
     * gate here would refuse something harmless and would make this module read a ledger to do it.
     */
    @Transactional
    public NoticeGiven give(long savingsAccountId, BigDecimal amount) {
        log.debug("notice requested savingsAccountId={} amount={}", savingsAccountId, amount);
        refuseUnlessAnAmountOfMoney(savingsAccountId, amount);
        TheAgreementAnAccountIsOn agreement = theAgreementOrRefuse(savingsAccountId);
        LocalDate today = today();
        Notice written = notices.save(Notice.givenOn(savingsAccountId, amount, today));
        NoticeGiven given = asRead(written, agreement.noticeDays(), today);
        log.info("notice given noticeId={} savingsAccountId={} product={} amount={} givenOn={} "
                        + "noticeDays={} readyOn={}",
                given.id(), savingsAccountId, agreement.productCode(), asMoney(given.amount()),
                given.givenOn(), agreement.noticeDays(), given.readyOn());
        return given;
    }

    /**
     * Cancels a notice, leaving nothing standing behind it.
     *
     * <p>The account is named as well as the notice, so a notice identifier belonging to somebody
     * else's account is an absence rather than a thing this door will act on. That is the same
     * reading every other nested resource in this application takes of an identifier that does not
     * belong where it was sent.
     *
     * <p>A notice a withdrawal has already used in full is refused rather than quietly cancelled:
     * there is nothing left to cancel, and answering "done" would tell a customer they had undone
     * something they had not.
     */
    @Transactional
    public NoticeGiven cancel(long savingsAccountId, long noticeId) {
        log.debug("notice cancellation requested savingsAccountId={} noticeId={}",
                savingsAccountId, noticeId);
        TheAgreementAnAccountIsOn agreement = theAgreementOrRefuse(savingsAccountId);
        Notice notice = notices.findByIdAndSavingsAccountId(noticeId, savingsAccountId)
                .orElseThrow(() -> {
                    String reason = "There is no notice numbered " + noticeId + " on that savings "
                            + "account.";
                    log.warn("notice cancellation refused savingsAccountId={} noticeId={} kind={} "
                                    + "reason={}", savingsAccountId, noticeId,
                            NoticeRefused.Kind.NO_SUCH_NOTICE, reason);
                    return new NoticeRefused(NoticeRefused.Kind.NO_SUCH_NOTICE, reason);
                });
        LocalDate today = today();
        if (notice.stillStanding().signum() == 0) {
            String reason = notice.cancelledOn() != null
                    ? "That notice was cancelled on " + notice.cancelledOn() + ", so there is "
                            + "nothing left standing to cancel."
                    : "That notice has already been used in full by a withdrawal, so there is "
                            + "nothing left standing to cancel.";
            log.warn("notice cancellation refused savingsAccountId={} noticeId={} cancelledOn={} "
                            + "consumed={} kind={} reason={}", savingsAccountId, noticeId,
                    notice.cancelledOn(), asMoney(notice.consumed()),
                    NoticeRefused.Kind.A_NOTICE_NO_LONGER_STANDING, reason);
            throw new NoticeRefused(NoticeRefused.Kind.A_NOTICE_NO_LONGER_STANDING, reason);
        }
        BigDecimal wasStanding = notice.stillStanding();
        notice.cancelOn(today);
        notices.save(notice);
        log.info("notice cancelled noticeId={} savingsAccountId={} product={} amount={} "
                        + "wasStanding={} givenOn={} cancelledOn={}",
                noticeId, savingsAccountId, agreement.productCode(), asMoney(notice.amount()),
                asMoney(wasStanding), notice.givenOn(), today);
        return asRead(notice, agreement.noticeDays(), today);
    }

    /**
     * What this account's notice says today: the days it asks for, what ready notice covers, what
     * is still running, and every notice still standing behind those two figures.
     *
     * <p>Answered for every savings account, including one that gives no notice at all: a screen
     * asking about an account it has just opened is entitled to an answer rather than a refusal,
     * and nought days with an empty list is the true one.
     */
    @Transactional(readOnly = true)
    public TheNoticeOnAnAccount noticeOn(long savingsAccountId) {
        int noticeDays = agreements.theAgreementOf(savingsAccountId)
                .map(TheAgreementAnAccountIsOn::noticeDays)
                .orElse(0);
        LocalDate today = today();
        List<NoticeGiven> standing = standingOn(savingsAccountId, noticeDays, today);
        BigDecimal ready = totalOf(standing.stream().filter(NoticeGiven::ready).toList());
        BigDecimal waiting = totalOf(standing.stream().filter(given -> !given.ready()).toList());
        log.debug("the notice on a savings account was read savingsAccountId={} noticeDays={} "
                        + "on={} standing={} readyToTakeToday={} stillWaiting={}",
                savingsAccountId, noticeDays, today, standing.size(), asMoney(ready),
                asMoney(waiting));
        return new TheNoticeOnAnAccount(savingsAccountId, noticeDays,
                AmountOfMoney.quotedToTheCent(ready), AmountOfMoney.quotedToTheCent(waiting),
                standing);
    }

    /**
     * What, if anything, the notice on this account says about taking that much money out today.
     *
     * <p>Answers empty for an account that asks for no notice — before a single row is read, which
     * is what makes this free for every account that existed before notice did — and empty again
     * when ready notice covers the amount. Otherwise one condition and one sentence, and the
     * sentence is where the days left go.
     *
     * <p>Package-private because the only caller is {@link TheConditionsOnTheWayOut}, which is the
     * one place the conditions an agreement carries are asked in order. A second caller reaching
     * past it would be a second answer to "may this money leave", asked without the conditions it
     * does not know about.
     */
    Optional<AConditionInTheWay> whatNoticeStops(long savingsAccountId, BigDecimal amount) {
        Optional<TheAgreementAnAccountIsOn> agreement = agreements.theAgreementOf(savingsAccountId);
        int noticeDays = agreement.map(TheAgreementAnAccountIsOn::noticeDays).orElse(0);
        if (noticeDays == 0) {
            log.debug("notice has nothing to say about a withdrawal savingsAccountId={} "
                            + "noticeDays=0 amount={}", savingsAccountId, asMoney(amount));
            return Optional.empty();
        }
        LocalDate today = today();
        List<NoticeGiven> standing = standingOn(savingsAccountId, noticeDays, today);
        BigDecimal readyToday =
                WhatAnAgreementStopsOnADay.readyToTakeOn(noticeDays, standing, today);
        log.debug("a withdrawal weighed against the notice given savingsAccountId={} noticeDays={} "
                        + "amount={} readyToTakeToday={} standing={} on={}",
                savingsAccountId, noticeDays, asMoney(amount), asMoney(readyToday), standing.size(),
                today);
        // The judgement and the words are WhatAnAgreementStopsOnADay's, asked of today. They used
        // to be written out here, which was fine while a withdrawal made now was the only thing
        // that ever met them; the what-if fold is the second caller and asks about a day that has
        // not happened yet, and a projection writing its own version of this sentence would be the
        // second place this bank says why it is holding somebody's money. What stayed here is the
        // reading of the rows, the transaction around it and the two lines that make a refusal
        // greppable.
        Optional<AConditionInTheWay> stopped = WhatAnAgreementStopsOnADay.noticeThatHasNotRun(
                noticeDays, standing, amount, today);
        if (stopped.isEmpty()) {
            return Optional.empty();
        }
        TheAgreementAnAccountIsOn itIsOn = agreement.orElseThrow();
        log.warn("notice refuses a withdrawal savingsAccountId={} product={} noticeDays={} "
                        + "amount={} readyToTakeToday={} condition={} reason={}",
                savingsAccountId, itIsOn.productCode(), noticeDays, asMoney(amount),
                asMoney(readyToday), ConditionOnTheWayOut.NOTICE_THAT_HAS_NOT_RUN,
                stopped.get().reason());
        return stopped;
    }

    /**
     * Spends ready notice on money that has just left, oldest first and partly rather than wholly
     * where that is all it takes.
     *
     * <p><strong>Oldest first, because notice given earliest is used earliest.</strong> The same
     * rule the deposits' own draw-down follows, for the same reason: which of a customer's rows
     * goes first is a rule rather than an accident of storage, and a customer watching their oldest
     * notice disappear is watching the rule they were told about.
     *
     * <p><strong>Partly consumed rather than wasted.</strong> Notice on EUR 500 that paid for a
     * withdrawal of EUR 200 is still notice on EUR 300 — throwing the rest away would be a penalty
     * nobody agreed to, and it would push customers towards giving notice in small pieces to avoid
     * it.
     *
     * <p><strong>Only ready notice is spent.</strong> Notice still running did not pay for this
     * withdrawal; the gate above has already made sure ready notice covered the whole of it, so a
     * walk that reached a waiting notice would be spending something that had not happened yet.
     *
     * <p>Nothing is spent on an account with no notice period, which is the ordinary case and is
     * answered without reading a row.
     */
    void moneyHasLeft(long savingsAccountId, BigDecimal amount) {
        int noticeDays = agreements.theAgreementOf(savingsAccountId)
                .map(TheAgreementAnAccountIsOn::noticeDays)
                .orElse(0);
        if (noticeDays == 0) {
            return;
        }
        LocalDate today = today();
        BigDecimal stillToSpend = amount;
        List<String> spent = new ArrayList<>();
        for (Notice notice : notices.findBySavingsAccountIdOrderByGivenOnAscIdAsc(savingsAccountId)) {
            if (stillToSpend.signum() == 0) {
                break;
            }
            if (!WhenANoticeIsReady.readyOn(notice.givenOn(), noticeDays, today)) {
                continue;
            }
            BigDecimal taken = notice.consume(stillToSpend);
            if (taken.signum() > 0) {
                notices.save(notice);
                stillToSpend = stillToSpend.subtract(taken);
                spent.add("[noticeId=" + notice.id() + " givenOn=" + notice.givenOn()
                        + " took=" + asMoney(taken) + " leftStanding="
                        + asMoney(notice.stillStanding()) + "]");
            }
        }
        // One line rather than one per notice, so that a withdrawal spread over a long list of
        // notices is still one thing in the log — the same shape the withdrawal's own draw-down
        // line takes, and read beside it. At INFO rather than DEBUG because notice being spent is
        // the business event that makes the next withdrawal behave differently, and "why can I not
        // take this out, I gave notice" is answered from this line alone.
        log.info("notice spent on a withdrawal savingsAccountId={} amount={} noticesTouched={} "
                        + "leftUnspent={} spent={}", savingsAccountId, asMoney(amount), spent.size(),
                asMoney(stillToSpend), String.join(" ", spent));
    }

    /**
     * The notices still standing on an account, oldest first, as the readings and the refusal both
     * see them.
     *
     * <p>Cancelled and fully-spent notices are gone from here, because what is standing is what a
     * customer still has — and one filter, used by every caller, is what stops a cancelled notice
     * covering a withdrawal in one place while being hidden on the screen in another.
     */
    private List<NoticeGiven> standingOn(long savingsAccountId, int noticeDays, LocalDate today) {
        return notices.findBySavingsAccountIdOrderByGivenOnAscIdAsc(savingsAccountId).stream()
                .filter(notice -> notice.stillStanding().signum() > 0)
                .map(notice -> asRead(notice, noticeDays, today))
                .toList();
    }

    /**
     * Notices as they leave this module: quoted to the cent, once, here — SQLite has no decimal
     * type and hands EUR 12.50 back as 12.5, and a figure printed into a refusal or onto a screen
     * would otherwise read as a number rather than as money.
     */
    private static NoticeGiven asRead(Notice notice, int noticeDays, LocalDate today) {
        return new NoticeGiven(
                notice.id(),
                notice.savingsAccountId(),
                AmountOfMoney.quotedToTheCent(notice.amount()),
                AmountOfMoney.quotedToTheCent(notice.stillStanding()),
                notice.givenOn(),
                WhenANoticeIsReady.readyOn(notice.givenOn(), noticeDays),
                WhenANoticeIsReady.readyOn(notice.givenOn(), noticeDays, today),
                WhenANoticeIsReady.daysLeftOn(notice.givenOn(), noticeDays, today));
    }

    private static BigDecimal totalOf(List<NoticeGiven> given) {
        return given.stream().map(NoticeGiven::stillStanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * The agreement this account is on, refusing when it asks for no notice at all.
     *
     * <p>The two absences answer in one sentence, which is the honest reading of both: an account
     * with no agreement recorded is one the start-up migration has not reached, and an account on
     * free savings has nothing to give notice of. Neither is a notice account, and what the
     * customer does about either is the same — nothing, because the money is already theirs.
     */
    private TheAgreementAnAccountIsOn theAgreementOrRefuse(long savingsAccountId) {
        Optional<TheAgreementAnAccountIsOn> agreement = agreements.theAgreementOf(savingsAccountId);
        if (agreement.isPresent() && agreement.get().noticeDays() > 0) {
            return agreement.get();
        }
        String reason = "That savings account has nothing to give notice of — money leaves it "
                + "whenever you like.";
        log.warn("notice refused savingsAccountId={} product={} noticeDays={} kind={} reason={}",
                savingsAccountId,
                agreement.map(TheAgreementAnAccountIsOn::productCode).orElse(null),
                agreement.map(TheAgreementAnAccountIsOn::noticeDays).orElse(0),
                NoticeRefused.Kind.NOT_A_NOTICE_ACCOUNT, reason);
        throw new NoticeRefused(NoticeRefused.Kind.NOT_A_NOTICE_ACCOUNT, reason);
    }

    /**
     * Refuses anything that is not an amount of money to give notice on, in the words a deposit of
     * the same figure would be refused in.
     *
     * <p>What counts as one is {@link AmountOfMoney}'s answer and not a second copy of it here, so
     * a figure quoted more finely than to the cent is refused the same way wherever it is typed.
     */
    private void refuseUnlessAnAmountOfMoney(long savingsAccountId, BigDecimal amount) {
        AmountOfMoney.whyItIsNotOne("notice", amount).ifPresent(reason -> {
            log.warn("notice refused savingsAccountId={} amount={} kind={} reason={}",
                    savingsAccountId, amount.toPlainString(),
                    NoticeRefused.Kind.NOT_AN_AMOUNT_OF_MONEY, reason);
            throw new NoticeRefused(NoticeRefused.Kind.NOT_AN_AMOUNT_OF_MONEY, reason);
        });
    }

    /** The day this application is standing on, in the one zone it counts its calendars in. */
    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
    }
}
