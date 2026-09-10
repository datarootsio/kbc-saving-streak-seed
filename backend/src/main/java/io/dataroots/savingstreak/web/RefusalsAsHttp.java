package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.clock.ClockRefused;
import io.dataroots.savingstreak.deposits.DepositRefused;
import io.dataroots.savingstreak.deposits.WithdrawalRefused;
import io.dataroots.savingstreak.gifting.GiftRefused;
import io.dataroots.savingstreak.jobs.JobFailed;
import io.dataroots.savingstreak.jobs.JobRefused;
import io.dataroots.savingstreak.notifications.NotificationRefused;
import io.dataroots.savingstreak.rewards.RewardRefused;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Reports a refusal from the domain over HTTP, and decides nothing else.
 *
 * <p>Whether a deposit can be made is the domain's answer; which status code says so is a fact about
 * this API, and this is the only place that knows it. The reason travels as written, because the
 * whole point of a refusal carrying words is that a person reads them.
 */
@RestControllerAdvice
class RefusalsAsHttp {

    /**
     * A deposit the application will not make. Never a server error: nothing went wrong, the answer
     * is no, and the person who asked is the one who can act on it.
     */
    @ExceptionHandler(DepositRefused.class)
    ResponseEntity<ProblemDetail> depositRefused(DepositRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            // An identifier for something that is not there, which is what a 404 says.
            case NO_SUCH_ACCOUNT -> HttpStatus.NOT_FOUND;
            // Real accounts, and a request they cannot be asked to honour.
            case AGAINST_THE_RULES -> HttpStatus.BAD_REQUEST;
            // Not a conflict: the account is in no unexpected state, there is simply less in it
            // than the customer asked to move, and the sentence that comes back says how much.
            case NOT_ENOUGH_MONEY -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    @ExceptionHandler(WithdrawalRefused.class)
    ResponseEntity<ProblemDetail> withdrawalRefused(WithdrawalRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_ACCOUNT -> HttpStatus.NOT_FOUND;
            case AGAINST_THE_RULES, NOT_ENOUGH_MONEY -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A move of the clock the application will not make. Always a bad request: the clock is in no
     * unexpected state, somebody asked it to go somewhere it does not go, and the sentence that comes
     * back says why not.
     *
     * <p>Handled here alongside the refusals a customer can cause even though only the development
     * profile can raise it, so that every refusal in this application answers in one shape and there
     * is one place to read what those shapes are.
     */
    @ExceptionHandler(ClockRefused.class)
    ResponseEntity<ProblemDetail> clockRefused(ClockRefused refusal) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refusal.getMessage()));
    }

    /**
     * A request to run a job the application will not act on — no job answers to that name, or more
     * than one does. A bad request rather than a missing page: the endpoint is there, it is the name
     * in it that is wrong, and the sentence that comes back lists the names that are right.
     *
     * <p>Told apart from a 404 deliberately. Outside the development profile these paths do not
     * exist at all, and a wrong name answering with the same status as a missing route would leave a
     * participant unable to tell "you spelled it wrong" from "this application has no such feature".
     */
    @ExceptionHandler(JobRefused.class)
    ResponseEntity<ProblemDetail> jobRefused(JobRefused refusal) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refusal.getMessage()));
    }

    /**
     * A job that was found, run, and threw. The one thing here that really is a server error: the
     * request was fine and the job broke, which is a participant's own code failing and the most
     * useful thing this API can do is say so in the sentence rather than answer an empty 500.
     */
    @ExceptionHandler(JobFailed.class)
    ResponseEntity<ProblemDetail> jobFailed(JobFailed failure) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, failure.getMessage()));
    }

    /**
     * A reward the application will not hand over. Not enough points is a bad request rather than a
     * conflict: the account is in no unexpected state, the customer asked for something that costs
     * more than they have, and the sentence that comes back says how much more.
     */
    @ExceptionHandler(RewardRefused.class)
    ResponseEntity<ProblemDetail> rewardRefused(RewardRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_CUSTOMER -> HttpStatus.NOT_FOUND;
            case NOT_ENOUGH_POINTS -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A read of somebody's notifications the application will not answer. One reason so far, and it
     * is about somebody who is not there, which is what a 404 says.
     *
     * <p>A switch over the kind even with one value in it, like every other refusal here: the next
     * reason a notification read can be refused for arrives as a value in
     * {@link NotificationRefused.Kind} and the compiler then asks this method what status it
     * deserves, rather than letting it quietly inherit the 404 that only fits an absence.
     */
    @ExceptionHandler(NotificationRefused.class)
    ResponseEntity<ProblemDetail> notificationRefused(NotificationRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_CUSTOMER -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A gift the application will not make. Two of them are about somebody who is not there, which
     * is what a 404 says; the other three are about a real pair of customers and a request they
     * cannot be asked to honour.
     *
     * <p>Not enough points is a bad request rather than a conflict, for the reason a claim's is:
     * nothing is in an unexpected state, the customer tried to give away more than they hold, and
     * the sentence that comes back says how much they have.
     */
    @ExceptionHandler(GiftRefused.class)
    ResponseEntity<ProblemDetail> giftRefused(GiftRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_CUSTOMER, NO_SUCH_RECIPIENT -> HttpStatus.NOT_FOUND;
            case TO_YOURSELF, NOT_A_NUMBER_OF_POINTS, NOT_ENOUGH_POINTS -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }
}
