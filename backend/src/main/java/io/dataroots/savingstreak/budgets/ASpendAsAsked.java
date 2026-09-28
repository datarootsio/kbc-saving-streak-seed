package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.util.List;

/**
 * A spend somebody is asking to record, exactly as they said it, before this module has ruled on any
 * of it.
 *
 * <p>A record rather than three parameters, for the reason {@code ABillAsAsked} is one: what arrived
 * travels as one thing, so the sentence that comes back can name the field that was wrong rather
 * than the position it was in — and every one of them is allowed to be absent, which is a call
 * nobody reads correctly at the call site.
 *
 * <p><strong>Every field is allowed to be absent, and that is the point.</strong> This is what was
 * asked for and not what will be kept: a spend with no name, no figure and no split is a thing a
 * customer can send, and each of those has a sentence waiting for it in {@link SpendsService}. A
 * record that could only hold a legal spend would move the refusals into the web layer, which does
 * not own them.
 *
 * <p>The figures arrive already read as numbers rather than as the characters that were typed.
 * Whether "25,00" is a number at all is a question about the request and is answered in the web
 * layer, in the same division of labour a bill's amount is read under; whether the number describes
 * a spend this application will record is a rule, and it belongs here.
 *
 * <p><strong>There is no date in it.</strong> A spend is not backdated: the day it counts to is the
 * day the application's clock reads, and a field for it here is the first step towards a customer
 * rewriting a rollover chain by typing one.
 */
public record ASpendAsAsked(String name, BigDecimal amount, List<APartAsAsked> parts) {
}
