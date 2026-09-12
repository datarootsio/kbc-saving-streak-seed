package be.kbc.savingstreak.web;

import be.kbc.savingstreak.config.DemoDataSeeder;
import be.kbc.savingstreak.service.BankingService;
import be.kbc.savingstreak.service.ContactService;
import be.kbc.savingstreak.service.GiftService;
import be.kbc.savingstreak.service.Money;
import be.kbc.savingstreak.service.NotificationService;
import be.kbc.savingstreak.service.OverviewService;
import be.kbc.savingstreak.service.RewardService;
import be.kbc.savingstreak.web.dto.ContactRequest;
import be.kbc.savingstreak.web.dto.ContactResult;
import be.kbc.savingstreak.web.dto.GiftRequest;
import be.kbc.savingstreak.web.dto.GiftResult;
import be.kbc.savingstreak.web.dto.OverviewResponse;
import be.kbc.savingstreak.web.dto.ThresholdRequest;
import be.kbc.savingstreak.web.dto.RedeemResult;
import be.kbc.savingstreak.web.dto.TransferRequest;
import be.kbc.savingstreak.web.dto.TransferResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final OverviewService overviewService;
    private final BankingService bankingService;
    private final RewardService rewardService;
    private final GiftService giftService;
    private final ContactService contactService;
    private final NotificationService notificationService;
    private final DemoDataSeeder seeder;

    public ApiController(OverviewService overviewService, BankingService bankingService, RewardService rewardService,
                         GiftService giftService, ContactService contactService,
                         NotificationService notificationService, DemoDataSeeder seeder) {
        this.overviewService = overviewService;
        this.bankingService = bankingService;
        this.rewardService = rewardService;
        this.giftService = giftService;
        this.contactService = contactService;
        this.notificationService = notificationService;
        this.seeder = seeder;
    }

    @GetMapping("/overview")
    public OverviewResponse overview() {
        return overviewService.overview();
    }

    @PostMapping("/transfers")
    public TransferResult transfer(@Valid @RequestBody TransferRequest request) {
        return bankingService.transfer(request);
    }

    /** Marks every notification as read. */
    @PostMapping("/notifications/read")
    public OverviewResponse markNotificationsRead() {
        notificationService.markAllRead(overviewService.member().getId());
        return overviewService.overview();
    }

    /** Sets or clears the balance levels an account should warn about. */
    @PutMapping("/accounts/{accountId}/alerts")
    public OverviewResponse setAlerts(@PathVariable Long accountId, @Valid @RequestBody ThresholdRequest request) {
        notificationService.setThreshold(
                overviewService.account(accountId),
                request.below() == null ? null : Money.toCents(request.below()),
                request.above() == null ? null : Money.toCents(request.above()));
        return overviewService.overview();
    }

    /** Adds somebody the customer can send points to. */
    @PostMapping("/contacts")
    public ContactResult addContact(@Valid @RequestBody ContactRequest request) {
        return contactService.add(request);
    }

    /** Peer-to-peer gifting: no cap on frequency, amount, or amount per day. */
    @PostMapping("/gifts")
    public GiftResult sendGift(@Valid @RequestBody GiftRequest request) {
        return giftService.send(request);
    }

    @PostMapping("/rewards/{rewardId}/redeem")
    public RedeemResult redeem(@PathVariable Long rewardId) {
        return rewardService.redeem(rewardId);
    }

    /** Puts the demo back to its starting position. */
    @PostMapping("/demo/reset")
    public OverviewResponse reset() {
        seeder.reset();
        return overviewService.overview();
    }

    /** Demo affordance: has a contact send you points, so the receiving side is visible. */
    @PostMapping("/demo/gifts/incoming")
    public GiftResult receiveGift(@Valid @RequestBody GiftRequest request) {
        return giftService.receiveFrom(request.toMemberId(), request.points());
    }
}
