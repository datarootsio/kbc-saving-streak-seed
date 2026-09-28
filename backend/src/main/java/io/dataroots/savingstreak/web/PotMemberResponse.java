package io.dataroots.savingstreak.web;

import java.time.Instant;

import io.dataroots.savingstreak.sharedpots.APotMember;

/**
 * One member of a shared pot as the API reports them: which customer, what they are called, what
 * they are to the pot, and since when.
 *
 * <p>The name as well as the identifier, so a list of members reads as people rather than as
 * numbers. It is looked up when the pot is read and never stored on the membership, so somebody
 * renamed is renamed in every pot they belong to.
 *
 * <p>The role travels as its own word, the idiom a goal's state and a gift's direction already set:
 * whoever renders it decides what to call {@code OWNER}, {@code CONTRIBUTOR} and {@code VIEWER} and
 * which of them to put a padlock beside, and both are easier to get right from a word than from a
 * number.
 *
 * <p>The moment comes off the application's clock, so a member who joined against a wound-forward
 * clock reads where the trainer wound it to.
 */
record PotMemberResponse(Long customerId, String name, String role, Instant joinedAt) {

    static PotMemberResponse of(APotMember member) {
        return new PotMemberResponse(member.customerId(), member.name(), member.role().name(),
                member.joinedAt());
    }
}
