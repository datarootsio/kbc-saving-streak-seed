package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.challenges.ChallengesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The seasons the bank is running, their windows, and which challenges belong to each.
 *
 * <p>It belongs to no customer, and that is the one thing worth saying about where it lives. Every
 * other read in this feature is under {@code /api/customers/{id}} because a challenge without
 * somebody in it is half a thing — the card is the definition and the reading together. A season is
 * not: what is running and until when is the same answer for everybody, in exactly the way the
 * rewards catalogue is, and a customer who has joined nothing still has to be able to see that there
 * is a campaign on and how long is left of it.
 *
 * <p>Read-only, and there is deliberately nothing else here. Seasons are seeded rows; an admin
 * screen for creating and retiming them is out of scope, so there is no verb to offer but GET.
 *
 * <p><strong>It judges nothing.</strong> Whether a window is open is the Challenges module's answer
 * — the same answer it refuses an enrolment with — and this class turns one request into one call
 * and its answer into JSON.
 */
@RestController
@RequestMapping("/api/campaigns")
class CampaignController {

    private final ChallengesService challenges;

    CampaignController(ChallengesService challenges) {
        this.challenges = challenges;
    }

    @GetMapping
    List<CampaignResponse> seasons() {
        return challenges.campaignsOnOffer().stream().map(CampaignResponse::of).toList();
    }
}
