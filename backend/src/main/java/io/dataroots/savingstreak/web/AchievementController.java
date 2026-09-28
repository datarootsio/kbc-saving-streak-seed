package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.challenges.ChallengesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The trophy case: everything the customer has ever won, newest first.
 *
 * <p>A resource of its own rather than a field on the challenges list, because it is a different
 * thing to look at. The challenges list answers "where do I stand and what should I do to-day"; this
 * answers "what have I done", and it goes on answering for challenges the customer has long since
 * finished or left and for ones the bank no longer offers. A customer with six years of banking here
 * has a trophy case far longer than their list of live challenges, and the two want different
 * screens.
 *
 * <p>Read-only, and there is deliberately nothing else here. An achievement is minted by reaching a
 * rung and by nothing else: it cannot be claimed, edited, hidden or given back, so there is no verb
 * to offer but GET.
 *
 * <p><strong>It judges nothing and grants nothing.</strong> Whether the customer exists and what
 * they have won are the Challenges module's answers; this class turns one request into one call and
 * its answer into JSON.
 */
@RestController
@RequestMapping("/api/customers/{customerId}/achievements")
class AchievementController {

    private final ChallengesService challenges;

    AchievementController(ChallengesService challenges) {
        this.challenges = challenges;
    }

    /**
     * Everything this customer has achieved, newest first — and a badge they earned a moment ago is
     * already in it, because the module judges before it answers rather than waiting for the night.
     */
    @GetMapping
    List<AchievementResponse> wonBy(@PathVariable long customerId) {
        return challenges.achievementsOf(customerId).stream().map(AchievementResponse::of).toList();
    }
}
