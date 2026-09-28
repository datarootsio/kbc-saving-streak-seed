package io.dataroots.savingstreak.accounts;

import java.math.BigDecimal;

/**
 * A bill somebody is asking to declare, exactly as they said it, before this module has ruled on any
 * of it.
 *
 * <p>A record rather than three parameters, for the reason {@code ARuleAsAsked} is one: what arrived
 * travels as one thing, so the sentence that comes back can name the field that was wrong rather
 * than the position it was in — and two of the three are nullable, which is a call nobody reads
 * correctly at the call site.
 *
 * <p><strong>Every field is allowed to be absent, and that is the point.</strong> This is what was
 * asked for and not what will be kept: a bill with no name, no day and no figure is a thing a
 * customer can send, and each of those has a sentence waiting for it in {@link AccountsService}. A
 * record that could only hold a legal bill would move the refusals into the web layer, which does
 * not own them.
 *
 * <p>The figures arrive already read as numbers rather than as the characters that were typed.
 * Whether "2.500,00" is a number at all is a question about the request and is answered in the web
 * layer, in the same division of labour a monthly income's amount is read under; whether the number
 * describes a bill this application will keep is a rule, and it belongs here.
 */
public record ABillAsAsked(String name, Integer dayOfMonth, BigDecimal amount) {
}
