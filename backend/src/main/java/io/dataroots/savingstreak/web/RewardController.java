package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.rewards.RewardsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The catalogue. It belongs to no account and no customer: the same rewards at the same prices for
 * everybody, which is what makes a price something a customer can save towards.
 */
@RestController
@RequestMapping("/api/rewards")
class RewardController {

    private final RewardsService rewards;

    RewardController(RewardsService rewards) {
        this.rewards = rewards;
    }

    @GetMapping
    List<RewardResponse> catalogue() {
        return rewards.catalogue().stream().map(RewardResponse::of).toList();
    }
}
