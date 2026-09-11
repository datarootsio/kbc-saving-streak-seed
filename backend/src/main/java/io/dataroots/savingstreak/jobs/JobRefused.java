package io.dataroots.savingstreak.jobs;

/**
 * A request to run a job that the application will not act on, carrying the reason in words the
 * person who asked can act on — the same bargain a refused deposit or a refused move of the clock
 * strikes.
 *
 * <p>Not a Spring exception, and no status code anywhere near it: which HTTP status reports a
 * refusal is a question about the API, and it is answered in the web layer.
 */
public class JobRefused extends RuntimeException {

    JobRefused(String reason) {
        super(reason);
    }
}
