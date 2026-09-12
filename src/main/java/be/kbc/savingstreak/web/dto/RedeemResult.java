package be.kbc.savingstreak.web.dto;

public record RedeemResult(
        RedemptionView redemption,
        OverviewResponse overview) {
}
