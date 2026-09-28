package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.challenges.ChallengesService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a customer could take on, and their taking one on or leaving it.
 *
 * <p>Under the customer rather than beside the rewards catalogue, and that is not a filing decision.
 * A challenge without somebody in it is half a thing: the card a page draws is the definition and
 * the reading together, and the reading only exists for a person. There is deliberately no endpoint
 * that answers about challenges in the abstract, because nothing in this application wants one — the
 * points a rung pays are the customer's, the mark it measures against is the customer's, and a
 * catalogue with no reader would be a screen nobody asked for.
 *
 * <p>A resource of its own rather than three more methods on {@code CustomerController}, which
 * already answers for accounts, movements, claims, gifts, notifications and pots. The shared pots
 * drew the same line for the same reason.
 *
 * <p><strong>It judges nothing.</strong> Whether the customer exists, whether the code names a
 * challenge, and whether there is an enrolment to make or to end are all rules, and they belong to
 * the module that owns them. This class turns three requests into three calls and their answers into
 * JSON.
 */
@RestController
@RequestMapping("/api/customers/{customerId}/challenges")
class ChallengeController {

    private final ChallengesService challenges;

    ChallengeController(ChallengesService challenges) {
        this.challenges = challenges;
    }

    /**
     * Every challenge open to this customer, with their standing in each — the whole tab in one
     * read, because a page that fetched the catalogue and then a standing per card would make a
     * request per row to draw one screen.
     */
    @GetMapping
    List<ChallengeResponse> openTo(@PathVariable long customerId) {
        return challenges.challengesFor(customerId).stream().map(ChallengeResponse::of).toList();
    }

    /**
     * Takes the customer on to the challenge, and answers with the enrolment including the mark it
     * will measure from.
     *
     * <p>A POST and not a PUT, and 201 rather than 200: an enrolment is a new thing with a moment
     * and a mark of its own, and sending this twice is not the same as sending it once — the second
     * one is refused, which is exactly the difference between the two verbs.
     *
     * <p>No body. Everything the request says is in its path: who, and which challenge. A body
     * carrying the mark would be a client telling the application what it had already saved.
     */
    @PostMapping("/{code}/enrolments")
    @ResponseStatus(HttpStatus.CREATED)
    EnrolmentResponse enrol(@PathVariable long customerId, @PathVariable String code) {
        return EnrolmentResponse.of(challenges.enrol(customerId, code));
    }

    /**
     * Leaves the challenge, and answers with the enrolment as it now stands.
     *
     * <p>A DELETE on the enrolment, because that is what the customer is doing: the thing they made
     * by posting here is the thing they are getting rid of. What the module does with it is end it
     * rather than remove it — the badges hanging off it have to keep pointing somewhere — and the
     * enrolment comes back in the answer rather than an empty 204 precisely so that the page can see
     * it is still there and ended, which is the claim the feature makes.
     */
    @DeleteMapping("/{code}/enrolments")
    EnrolmentResponse leave(@PathVariable long customerId, @PathVariable String code) {
        return EnrolmentResponse.of(challenges.leave(customerId, code));
    }
}
