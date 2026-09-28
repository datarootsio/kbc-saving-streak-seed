package io.dataroots.savingstreak.support;

import java.math.BigDecimal;

/**
 * How much of what a savings account holds its agreement would let leave today, as the API sends
 * it, mirroring {@code WhatCanLeaveTodayResponse} field for field.
 *
 * <p>{@code condition} is read as the plain name rather than as an enum, for the reason every other
 * view here gives about the words the API sends: a test that deserialised into the application's
 * own enum would stop failing on the day somebody renamed a constant, which is exactly the day a
 * screen matching on the old name breaks.
 *
 * <p>Both {@code condition} and {@code whyItIsLess} are null on an account whose whole balance is
 * free, and the tests assert that rather than ignoring it — "nothing is in the way" is the answer
 * three products in four give, and a reading that quietly invented a reason for them would be the
 * thing worth catching.
 */
public record WhatCanLeaveTodayView(Long savingsAccountId, BigDecimal balance,
                                    BigDecimal freeToTakeToday, String condition,
                                    String whyItIsLess) {
}
