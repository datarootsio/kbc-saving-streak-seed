package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * What one savings account's notice says, as the API sends it, mirroring
 * {@code TheNoticeOnAnAccountResponse} field for field.
 *
 * <p>{@code noticeDays} is on it because the tests that matter most are about an account with none:
 * free savings and the core saver answer with nought days, nought ready, nought waiting and an
 * empty list, and asserting that is how "nothing changed for the accounts that existed before this"
 * stops being a claim and becomes a test.
 */
public record TheNoticeOnAnAccountView(Long savingsAccountId, int noticeDays,
                                       BigDecimal readyToTakeToday, BigDecimal stillWaiting,
                                       List<NoticeView> notices) {
}
