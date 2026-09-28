package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.products.TheNoticeOnAnAccount;

/**
 * Everything one savings account's notice says at once: the days its agreement asks for, what ready
 * notice covers today, what is still running, and the notices behind both figures.
 *
 * <p><strong>One reading rather than four, because they are one walk.</strong> What is ready and
 * what is waiting are two filters over the same rows judged against one reading of the clock, and a
 * page that fetched them separately could be told two things that were true a second apart. The
 * same argument the agreement's own reading makes about travelling with the account rather than
 * being looked up beside it.
 *
 * <p><strong>{@code noticeDays} is what tells a page whether to draw the panel at all.</strong>
 * Nought is free savings and the core saver: nothing to give notice of, both amounts nought, the
 * list empty. Saying it as a figure rather than as an absence is what lets one component render
 * every savings account without first asking what kind it is — and it is why this endpoint answers
 * for every account rather than refusing the ones with no notice period.
 *
 * <p><strong>{@code readyToTakeToday} is a ceiling and not a promise.</strong> It is what ready
 * notice covers, which is one of the three things a withdrawal is weighed against: how much the
 * account holds and how much of that a savings goal has spoken for are two other readings, and a
 * page that printed this as "what you can take" without them would be the second place this
 * application decides what may be withdrawn.
 */
record TheNoticeOnAnAccountResponse(long savingsAccountId, int noticeDays,
                                    BigDecimal readyToTakeToday, BigDecimal stillWaiting,
                                    List<NoticeResponse> notices) {

    static TheNoticeOnAnAccountResponse of(TheNoticeOnAnAccount notice) {
        return new TheNoticeOnAnAccountResponse(
                notice.savingsAccountId(),
                notice.noticeDays(),
                notice.readyToTakeToday(),
                notice.stillWaiting(),
                notice.notices().stream().map(NoticeResponse::of).toList());
    }
}
