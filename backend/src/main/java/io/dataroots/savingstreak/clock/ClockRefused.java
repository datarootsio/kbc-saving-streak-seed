package io.dataroots.savingstreak.clock;

/**
 * A move of the clock the application will not make, carrying the reason in words the person who
 * asked for it can act on — the same bargain a refused deposit strikes.
 *
 * <p>Not a Spring exception, and no status code anywhere near it: which HTTP status reports a
 * refusal is a question about the API, and it is answered in the web layer.
 */
public class ClockRefused extends RuntimeException {

    ClockRefused(String reason) {
        super(reason);
    }
}
