package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.clock.ClockRefused;
import io.dataroots.savingstreak.deposits.DepositRefused;
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
     * A reward the application will not hand over. Not enough points is a bad request rather than a
     * conflict: the account is in no unexpected state, the customer asked for something that costs
     * more than they have, and the sentence that comes back says how much more.
     */
    @ExceptionHandler(RewardRefused.class)
    ResponseEntity<ProblemDetail> rewardRefused(RewardRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_ACCOUNT -> HttpStatus.NOT_FOUND;
            case NOT_ENOUGH_POINTS -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }
}
