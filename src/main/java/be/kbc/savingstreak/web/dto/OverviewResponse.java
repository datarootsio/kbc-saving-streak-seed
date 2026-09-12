package be.kbc.savingstreak.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record OverviewResponse(
        MemberView member,
        List<AccountView> accounts,
        BigDecimal totalBalance,
        BigDecimal totalSaved,
        List<TransferView> transfers,
        List<RewardView> rewards,
        List<RedemptionView> redemptions,
        List<ContactView> contacts,
        List<GiftView> gifts,
        List<NotificationView> notifications,
        int unreadNotifications,
        /** Loyalty points credited while building this response, for a one-off nudge. */
        int loyaltyJustPaid) {
}
