package be.kbc.savingstreak;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.kbc.savingstreak.config.DemoDataSeeder;
import be.kbc.savingstreak.domain.Account;
import be.kbc.savingstreak.domain.AccountType;
import be.kbc.savingstreak.repo.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SavingStreakApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private DemoDataSeeder seeder;

    private long currentId;
    private long savingsId;
    private long otherSavingsId;

    @BeforeEach
    void resetDemoData() {
        seeder.reset();
        currentId = accountOf(AccountType.CURRENT, 0);
        savingsId = accountOf(AccountType.SAVINGS, 0);
        otherSavingsId = accountOf(AccountType.SAVINGS, 1);
    }

    private long accountOf(AccountType type, int index) {
        return accounts.findAllByOrderBySortOrderAsc().stream()
                .filter(account -> account.getType() == type)
                .map(Account::getId)
                .toList()
                .get(index);
    }

    @Test
    void overviewShowsAccountsRewardsAndPoints() throws Exception {
        mockMvc.perform(get("/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.firstName").value("Lotte"))
                .andExpect(jsonPath("$.member.streakWeeks").value(3))
                .andExpect(jsonPath("$.accounts.length()").value(4))
                .andExpect(jsonPath("$.rewards.length()").value(6))
                .andExpect(jsonPath("$.totalBalance").value(greaterThan(0.0)));
    }

    @Test
    void depositMovesMoneyAndEarnsPointsWithStreakBonus() throws Exception {
        mockMvc.perform(transfer(currentId, savingsId, "50.00"))
                .andExpect(status().isOk())
                // fourth week in a row -> multiplier 1.30 on 50 base points
                .andExpect(jsonPath("$.pointsEarned").value(65))
                .andExpect(jsonPath("$.multiplier").value(1.30))
                .andExpect(jsonPath("$.streakExtended").value(true))
                .andExpect(jsonPath("$.streakWeeks").value(4))
                .andExpect(jsonPath("$.overview.member.pointsBalance").value(1180))
                .andExpect(jsonPath("$.overview.accounts[0].balance").value(2381.58))
                .andExpect(jsonPath("$.overview.accounts[1].balance").value(5300.00));
    }

    @Test
    void adepositBelowTheWeeklyMinimumEarnsPointsButDoesNotSecureTheWeek() throws Exception {
        mockMvc.perform(transfer(currentId, savingsId, "20.00"))
                .andExpect(status().isOk())
                // still 1.20x from the three seeded weeks, and the week is not secured yet
                .andExpect(jsonPath("$.pointsEarned").value(24))
                .andExpect(jsonPath("$.multiplier").value(1.20))
                .andExpect(jsonPath("$.streakExtended").value(false))
                .andExpect(jsonPath("$.streakWeeks").value(3))
                .andExpect(jsonPath("$.overview.member.streakSafeThisWeek").value(false))
                .andExpect(jsonPath("$.overview.member.newSavingsThisWeek").value(20.00));
    }

    @Test
    void depositsInTheSameWeekAddUpToSecureIt() throws Exception {
        mockMvc.perform(transfer(currentId, savingsId, "20.00")).andExpect(status().isOk());

        // 20 + 30 reaches the 50 minimum, so this deposit secures the week and is paid at 1.30x
        mockMvc.perform(transfer(currentId, savingsId, "30.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsEarned").value(39))
                .andExpect(jsonPath("$.multiplier").value(1.30))
                .andExpect(jsonPath("$.streakExtended").value(true))
                .andExpect(jsonPath("$.streakWeeks").value(4))
                .andExpect(jsonPath("$.overview.member.streakSafeThisWeek").value(true))
                .andExpect(jsonPath("$.overview.member.newSavingsThisWeek").value(50.00));
    }

    @Test
    void aSecondDepositOnceTheWeekIsSecuredDoesNotExtendTheStreakAgain() throws Exception {
        mockMvc.perform(transfer(currentId, savingsId, "50.00")).andExpect(status().isOk());

        mockMvc.perform(transfer(currentId, savingsId, "50.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streakExtended").value(false))
                .andExpect(jsonPath("$.streakWeeks").value(4))
                .andExpect(jsonPath("$.pointsEarned").value(65));
    }

    @Test
    void withdrawalGivesNoPointsAndDoesNotTouchTheStreak() throws Exception {
        mockMvc.perform(transfer(savingsId, currentId, "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsEarned").value(0))
                .andExpect(jsonPath("$.streakExtended").value(false))
                .andExpect(jsonPath("$.overview.member.pointsBalance").value(1115))
                .andExpect(jsonPath("$.overview.member.streakWeeks").value(3));
    }

    @Test
    void movingTheSameMoneyBackAndForthEarnsNothingTheSecondTime() throws Exception {
        mockMvc.perform(transfer(currentId, savingsId, "50.00"))
                .andExpect(jsonPath("$.pointsEarned").value(65));
        mockMvc.perform(transfer(savingsId, currentId, "50.00"))
                .andExpect(jsonPath("$.pointsEarned").value(0));

        // Savings are back below the peak, so this deposit is not new savings.
        mockMvc.perform(transfer(currentId, savingsId, "50.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsEarned").value(0))
                .andExpect(jsonPath("$.overview.member.pointsBalance").value(1180))
                .andExpect(jsonPath("$.overview.member.newSavingsThisWeek").value(50.00));
    }

    @Test
    void onlyTheShareOfADepositAboveThePeakEarnsPoints() throws Exception {
        mockMvc.perform(transfer(currentId, savingsId, "100.00"))
                .andExpect(jsonPath("$.pointsEarned").value(130));
        mockMvc.perform(transfer(savingsId, currentId, "100.00"))
                .andExpect(jsonPath("$.pointsEarned").value(0));

        // 150 deposited, 100 of it merely restores the peak, so 50 is new: 50 x 1.30
        mockMvc.perform(transfer(currentId, savingsId, "150.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsEarned").value(65));
    }

    @Test
    void restoringWithdrawnSavingsDoesNotSecureTheWeek() throws Exception {
        mockMvc.perform(transfer(savingsId, currentId, "200.00")).andExpect(status().isOk());

        mockMvc.perform(transfer(currentId, savingsId, "200.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsEarned").value(0))
                .andExpect(jsonPath("$.streakExtended").value(false))
                .andExpect(jsonPath("$.streakWeeks").value(3))
                .andExpect(jsonPath("$.overview.member.streakSafeThisWeek").value(false))
                .andExpect(jsonPath("$.overview.member.newSavingsThisWeek").value(0.00));
    }

    @Test
    void thePeakIsTheStartingSavingsTotalSoTheFirstDepositEarnsInFull() throws Exception {
        mockMvc.perform(get("/api/overview"))
                .andExpect(jsonPath("$.member.savingsPeak").value(7055.00))
                .andExpect(jsonPath("$.totalSaved").value(7055.00));
    }

    @Test
    void aTransferLargerThanTheBalanceIsRejected() throws Exception {
        mockMvc.perform(transfer(currentId, savingsId, "99999.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("not enough money")));
    }

    @Test
    void movingBetweenSavingsAccountsIsAllowedButEarnsNothing() throws Exception {
        mockMvc.perform(transfer(savingsId, otherSavingsId, "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfer.direction").value("REBALANCE"))
                .andExpect(jsonPath("$.transfer.description").value("Moved to Lisbon travel fund"))
                .andExpect(jsonPath("$.pointsEarned").value(0))
                .andExpect(jsonPath("$.streakExtended").value(false))
                .andExpect(jsonPath("$.streakWeeks").value(3))
                // the money moved, but nothing about the loyalty state did
                .andExpect(jsonPath("$.overview.accounts[1].balance").value(5150.00))
                .andExpect(jsonPath("$.overview.accounts[2].balance").value(1220.00))
                .andExpect(jsonPath("$.overview.totalSaved").value(7055.00))
                .andExpect(jsonPath("$.overview.member.pointsBalance").value(1115))
                .andExpect(jsonPath("$.overview.member.savingsPeak").value(7055.00))
                .andExpect(jsonPath("$.overview.member.newSavingsThisWeek").value(0.00))
                .andExpect(jsonPath("$.overview.member.streakSafeThisWeek").value(false));
    }

    @Test
    void shufflingBetweenSavingsAccountsCannotSecureTheWeek() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(transfer(savingsId, otherSavingsId, "50.00")).andExpect(status().isOk());
            mockMvc.perform(transfer(otherSavingsId, savingsId, "50.00")).andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/overview"))
                .andExpect(jsonPath("$.member.pointsBalance").value(1115))
                .andExpect(jsonPath("$.member.streakSafeThisWeek").value(false))
                .andExpect(jsonPath("$.totalSaved").value(7055.00));
    }

    @Test
    void aTransferToTheSameAccountIsRejected() throws Exception {
        mockMvc.perform(transfer(savingsId, savingsId, "10.00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("two different accounts")));
    }

    @Test
    void aNegativeAmountIsRejected() throws Exception {
        mockMvc.perform(transfer(currentId, savingsId, "-5.00"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redeemingARewardSpendsPointsAndReturnsAVoucher() throws Exception {
        long cinemaTicket = 4;
        mockMvc.perform(post("/api/rewards/{id}/redeem", cinemaTicket))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redemption.rewardTitle").value("Cinema ticket"))
                .andExpect(jsonPath("$.redemption.pointsSpent").value(900))
                .andExpect(jsonPath("$.redemption.voucherCode", containsString("KBC-")))
                .andExpect(jsonPath("$.overview.member.pointsBalance").value(215))
                .andExpect(jsonPath("$.overview.redemptions.length()").value(1));
    }

    @Test
    void theOverviewSaysWhenTheNextBatchOfPointsExpires() throws Exception {
        // the seeded history has one deposit from almost a year ago
        mockMvc.perform(get("/api/overview"))
                .andExpect(jsonPath("$.member.pointsValidMonths").value(12))
                .andExpect(jsonPath("$.member.pointsExpiringNext").value(75))
                .andExpect(jsonPath("$.member.pointsExpiringOn").isNotEmpty())
                // the year-old deposit's own 400 points have already lapsed
                .andExpect(jsonPath("$.member.pointsLapsed").value(400))
                .andExpect(jsonPath("$.member.loyaltyPointsEarned").value(40));
    }

    @Test
    void redeemingSpendsThePointsClosestToExpiryFirst() throws Exception {
        mockMvc.perform(post("/api/rewards/{id}/redeem", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.member.pointsBalance").value(965))
                // the 150 spent empties the 75 point batch, then the 40 point loyalty batch,
                // then takes 35 off the 300 one, leaving 265 of it to expire first
                .andExpect(jsonPath("$.overview.member.pointsExpiringNext").value(265));
    }

    @Test
    void theHistorySaysWhenEachRowsPointsExpire() throws Exception {
        mockMvc.perform(get("/api/overview"))
                // newest first: the most recent deposit earned 100 points, still all unspent
                .andExpect(jsonPath("$.transfers[0].pointsEarned").value(100))
                .andExpect(jsonPath("$.transfers[0].pointsLeft").value(100))
                .andExpect(jsonPath("$.transfers[0].pointsHaveExpired").value(false))
                .andExpect(jsonPath("$.transfers[0].pointsExpireOn").isNotEmpty())
                // a withdrawal earned nothing, so it has no expiry to show
                .andExpect(jsonPath("$.transfers[1].pointsEarned").value(0))
                .andExpect(jsonPath("$.transfers[1].pointsExpireOn").doesNotExist())
                .andExpect(jsonPath("$.transfers[1].pointsLeft").value(0));
    }

    @Test
    void aHistoryRowShowsItsPointsAsSpentOnceTheyAreUsed() throws Exception {
        // the coffee costs 150, which empties the oldest batch of 75 and takes 75 off the next
        mockMvc.perform(post("/api/rewards/{id}/redeem", 1L)).andExpect(status().isOk());

        mockMvc.perform(get("/api/overview"))
                // the year-old batch of 75 is fully spent
                .andExpect(jsonPath("$.transfers[?(@.pointsEarned == 75)].pointsLeft").value(contains(0)))
                // and the 300 batch lost the last 35
                .andExpect(jsonPath("$.transfers[?(@.pointsEarned == 300)].pointsLeft").value(contains(265)));
    }

    @Test
    void aFreshTransferComesBackWithItsOwnExpiryDate() throws Exception {
        mockMvc.perform(transfer(currentId, savingsId, "50.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transfer.pointsEarned").value(65))
                .andExpect(jsonPath("$.transfer.pointsLeft").value(65))
                .andExpect(jsonPath("$.transfer.pointsExpireOn").isNotEmpty());
    }

    @Test
    void theOverviewListsWhoYouCanSendPointsTo() throws Exception {
        mockMvc.perform(get("/api/overview"))
                .andExpect(jsonPath("$.contacts.length()").value(4))
                .andExpect(jsonPath("$.contacts[0].name").value("Amina Haddad"))
                .andExpect(jsonPath("$.contacts[0].initials").value("AH"))
                .andExpect(jsonPath("$.gifts").isEmpty())
                .andExpect(jsonPath("$.member.giftedAwayPoints").value(0))
                .andExpect(jsonPath("$.member.giftedToYouPoints").value(0));
    }

    @Test
    void sendingAGiftMovesPointsAndShowsUpInTheGiftList() throws Exception {
        long jasper = 2; // the contacts list is sorted by first name, ids are seeding order
        mockMvc.perform(post("/api/gifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toMemberId\":" + jasper + ",\"points\":200,\"message\":\"Thanks!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gift.direction").value("SENT"))
                .andExpect(jsonPath("$.gift.points").value(200))
                .andExpect(jsonPath("$.gift.message").value("Thanks!"))
                .andExpect(jsonPath("$.overview.member.pointsBalance").value(915))
                .andExpect(jsonPath("$.overview.member.giftedAwayPoints").value(200))
                .andExpect(jsonPath("$.overview.gifts.length()").value(1))
                .andExpect(jsonPath("$.overview.gifts[0].counterpartName").value("Jasper De Wit"));
    }

    @Test
    void aGiftBiggerThanTheBalanceIsRejected() throws Exception {
        mockMvc.perform(post("/api/gifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toMemberId\":2,\"points\":5000}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("more points to send")));
    }

    @Test
    void aGiftOfNoPointsIsRejected() throws Exception {
        mockMvc.perform(post("/api/gifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toMemberId\":2,\"points\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("at least one point")));
    }

    @Test
    void addingSomebodyMakesThemAvailableToSendPointsTo() throws Exception {
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"  Noor \",\"lastName\":\"Van   Dijck\"}"))
                .andExpect(status().isOk())
                // the name is trimmed and inner runs of whitespace collapsed
                .andExpect(jsonPath("$.contact.name").value("Noor Van Dijck"))
                .andExpect(jsonPath("$.contact.initials").value("NV"))
                .andExpect(jsonPath("$.overview.contacts.length()").value(5))
                // the list stays sorted by first name
                .andExpect(jsonPath("$.overview.contacts[3].name").value("Noor Van Dijck"))
                // and nobody's points moved
                .andExpect(jsonPath("$.overview.member.pointsBalance").value(1115));
    }

    @Test
    void somebodyJustAddedCanReceivePointsStraightAway() throws Exception {
        String body = mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Noor\",\"lastName\":\"Van Dijck\"}"))
                .andReturn().getResponse().getContentAsString();
        long newContactId = com.jayway.jsonpath.JsonPath.parse(body).read("$.contact.id", Integer.class);

        mockMvc.perform(post("/api/gifts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toMemberId\":" + newContactId + ",\"points\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gift.counterpartName").value("Noor Van Dijck"))
                .andExpect(jsonPath("$.overview.member.pointsBalance").value(1075));
    }

    @Test
    void aContactNeedsBothNames() throws Exception {
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"lastName\":\"Van Dijck\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("first name")));

        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Noor\",\"lastName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("last name")));
    }

    @Test
    void theOverviewCarriesTheNotificationFeedAndAlertLevels() throws Exception {
        mockMvc.perform(get("/api/overview"))
                // the seeded deposit whose year is nearly up
                .andExpect(jsonPath("$.notifications[0].kind").value("BONUS_VESTING_SOON"))
                .andExpect(jsonPath("$.notifications[0].unread").value(true))
                .andExpect(jsonPath("$.unreadNotifications").value(1))
                // the current account ships with a low balance alert
                .andExpect(jsonPath("$.accounts[0].alertBelow").value(2000.00))
                .andExpect(jsonPath("$.accounts[1].alertBelow").doesNotExist());
    }

    @Test
    void alertLevelsCanBeSetAndCleared() throws Exception {
        mockMvc.perform(put("/api/accounts/{id}/alerts", savingsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"above\":\"6000.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[1].alertAbove").value(6000.00));

        mockMvc.perform(put("/api/accounts/{id}/alerts", savingsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[1].alertAbove").doesNotExist());
    }

    @Test
    void aNegativeAlertLevelIsRejected() throws Exception {
        mockMvc.perform(put("/api/accounts/{id}/alerts", savingsId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"above\":\"-5.00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be negative")));
    }

    @Test
    void markingNotificationsReadClearsTheCount() throws Exception {
        mockMvc.perform(get("/api/overview")).andExpect(jsonPath("$.unreadNotifications").value(1));

        mockMvc.perform(post("/api/notifications/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadNotifications").value(0))
                .andExpect(jsonPath("$.notifications[0].unread").value(false));
    }

    @Test
    void aRewardYouCannotAffordIsRejected() throws Exception {
        long familyPack = 6;
        mockMvc.perform(post("/api/rewards/{id}/redeem", familyPack))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("more points")));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder transfer(
            long from, long to, String amount) {
        return post("/api/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccountId\":" + from + ",\"toAccountId\":" + to + ",\"amount\":" + amount + "}");
    }
}
