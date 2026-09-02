package io.dataroots.savingstreak.jobs;

/**
 * A job that was found, run, and threw.
 *
 * <p>Distinct from {@link JobRefused} because it is a different answer to a different question: the
 * request was fine and the job is the thing that went wrong. Told apart on purpose — a participant
 * demonstrating the job they just wrote needs to know whether they misspelled its name or broke it,
 * and the two answers look identical in a log that lumps them together.
 */
public class JobFailed extends RuntimeException {

    JobFailed(String what, Throwable cause) {
        super(what, cause);
    }
}
