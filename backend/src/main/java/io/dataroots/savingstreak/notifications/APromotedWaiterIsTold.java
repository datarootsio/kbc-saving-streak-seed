package io.dataroots.savingstreak.notifications;

import io.dataroots.savingstreak.rewards.AHoldOnAnOffer;
import io.dataroots.savingstreak.rewards.TellingAPromotedWaiter;
import org.springframework.stereotype.Component;

/**
 * The Rewards module's one question to this one, answered: a customer's turn in a waiting list
 * came, and they should be told that the thing is being held for them.
 *
 * <p><strong>Here rather than in Rewards, and that is the whole reason this class exists.</strong>
 * The rewards module reads no other module — it asks Points to spend some points and Accounts
 * whether a customer exists, and that is the entirety of what it imports — and a
 * {@code RewardsService} that called a notifications service would have gained the outbound
 * dependency fifteen slices of that feature were built to avoid. So Rewards states the question
 * in its own vocabulary, as {@link TellingAPromotedWaiter}, and this module answers it. The
 * dependency runs the only way it can: Notifications already reads half the application in order
 * to have anything to say, and one more read costs it nothing. It is the same arrangement
 * {@code accounts.WhoMayPayIntoAnAccountNobodyHolds} and
 * {@code sharedpots.MembersOfThePotMayPayIntoItsAccount} already have, and the second one this
 * application has needed.
 *
 * <p><strong>One producer of notifications is preserved and not broken.</strong> The rule this
 * module holds is that nothing outside it writes a notification, and nothing does: this is a bean
 * of this package writing this package's rows through this package's service. What is different
 * from every other reason is only the timing, and the argument for it — a hold's seventy-two
 * hours start running at the moment of the promotion, while the sweep that would otherwise carry
 * the news ran an hour earlier — is written out on {@code NotificationsService} and on the
 * interface.
 *
 * <p>Package-private, like the sweep beside it: what answers Rewards' question is this module's
 * own business, and nothing outside needs a handle on it. Spring finds it by the interface,
 * which is how Rewards receives it and the only name Rewards ever knows it by.
 *
 * <p>No clock of its own. The moment stamped on the row is the moment the hold was created,
 * which is the moment the rewards sweep read from the application's clock at the top of its run
 * — so a trainer who winds the clock forward and runs the sweep by hand finds the notification
 * dated the same night as the hold, rather than dated whatever the machine thought. A clock read
 * here would be a second reading of the same instant and would be the one that disagreed.
 */
@Component
class APromotedWaiterIsTold implements TellingAPromotedWaiter {

    private final NotificationsService notifications;

    APromotedWaiterIsTold(NotificationsService notifications) {
        this.notifications = notifications;
    }

    /**
     * Unpacks the hold into the four facts a notification carries and writes one.
     *
     * <p>Unpacked here rather than passed along whole, so that the service and the row keep
     * speaking their own vocabulary: {@code NotificationsService} takes a customer, a code, a
     * title and a moment, exactly as it takes a bill and a name and a day, and does not learn
     * what a hold is. This class is the only thing in the module that knows, which is what
     * makes it the only thing that would have to change if a hold ever carried something else.
     *
     * <p>The moment the hold was taken is the moment the notification is raised at, rather than
     * a clock read here. A hold is created by the nightly sweep against one reading of the
     * application's clock, and a second reading in here would put the notification a few
     * milliseconds after the thing it is about for no reason anybody could use.
     */
    @Override
    public void aHoldIsWaitingFor(AHoldOnAnOffer hold) {
        notifications.aRewardIsBeingHeldFor(hold.customerId(), hold.offerCode(), hold.title(),
                hold.lapsesAt(), hold.takenAt());
    }
}
