package io.dataroots.savingstreak.rewards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every bundle in the catalogue as of one reading: what each of them contains, and how much of
 * each member's stock the bundles have already taken.
 *
 * <p><strong>Why this exists at all: a claim of a bundle names the bundle, and draws down
 * something else.</strong> Every other figure in this module can be counted straight off the
 * claims, because a claim row names the thing it was made against. A bundle breaks that in one
 * place and one place only: one claim, one voucher, one code — and two cinema tickets and a bag
 * of popcorn gone out of the world under a code that says neither. So "how many cinema tickets
 * have gone" is no longer "how many claims name {@code CINEMA_TICKET}", and the missing half has
 * to come from somewhere.
 *
 * <p><strong>It is derived, not written.</strong> The alternative considered was to write a
 * second claim row per member at the moment a bundle is claimed — a phantom claim, in a table
 * whose every row is a voucher somebody holds. It was rejected twice over: those rows would be
 * vouchers by construction, appearing in the customer's own list of what they have claimed and
 * at a counter, which is precisely the "one voucher per member" design the spec argues against;
 * and they would be a second stored figure that has to agree with the bundle's own claim,
 * maintained by hand through a cancellation, which is the bargain this module refuses everywhere
 * else. What a member has lost is therefore multiplication: the bundle's claims, times the
 * quantity on the line. Nothing has to be kept in step with anything, because nothing is kept.
 *
 * <p><strong>The nicest consequence is the one nobody has to write.</strong> Whatever rule the
 * claim count applies to a bundle's own claims — a cancelled one not counting, the day something
 * can cancel one — applies to its members automatically, because the members' figure is that
 * figure multiplied. A cancellation that puts a bundle back in the window puts every one of its
 * members back in the window in the same breath, without a line of code that says so.
 *
 * <p><strong>Read once per reading, and not at all in the application as it ships.</strong> The
 * lines are one query for the whole catalogue, and when there are none — which is every database
 * this application has ever written, because nothing seeded is a bundle — this is {@link #none}
 * and every lookup below answers "nothing" without a map in sight. That is the same
 * short-circuit the stock arithmetic already makes on a null stock, and it is what lets the
 * four offers a customer has always known keep doing exactly the arithmetic they did before
 * this table existed.
 *
 * <p>Package-private, like everything it holds. What leaves the module is
 * {@link WhatABundleContains}, already folded into the two readings.
 *
 * @param contents what each bundle contains, by the bundle's code, in the order it was composed
 * @param takenFromMembers how many of each member the bundles' claims have drawn down, by the
 *        member's code, and absent for a code no bundle has ever taken any of
 * @param everyOfferByCode every offer in the catalogue by its code, so that a member's own stock
 *        can be read without a query per line
 * @param counted how many of each offer have gone out, as the one grouped count of the claims
 *        table produced it, so that a member's remainder is worked out from the same figures
 *        every other remainder is
 * @param heldByAnybody how many of each offer are being held right now, as the one grouped count
 *        of the holds table produced it, and absent for a code nobody is holding any of
 */
record TheBundlesAsTheyStand(Map<String, List<WhatABundleContains>> contents,
                             Map<String, Long> takenFromMembers,
                             Map<String, RewardOffer> everyOfferByCode,
                             Map<String, HowManyHaveGone> counted,
                             Map<String, HowManyAreHeld> heldByAnybody) {

    /**
     * The answer for a catalogue with no bundles in it, which is what this application ships and
     * what every reading of it does until somebody composes one.
     *
     * <p>Named rather than four empty maps at the call site, because "there are no bundles" is a
     * fact worth reading as a sentence — and because it is the state in which nothing below this
     * record changes any figure this module produced before it existed.
     */
    static TheBundlesAsTheyStand none() {
        return new TheBundlesAsTheyStand(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    /**
     * Folds the lines of the catalogue into the two answers the arithmetic needs, once.
     *
     * <p>The titles come out of the offers that were read for the arithmetic anyway rather than
     * out of a query per line, and a line naming an offer that is somehow not in that list is
     * dropped rather than printed as a code at a customer: an offer is never deleted, so the
     * only way to get one is a database somebody edited, and half a hamper described honestly is
     * better than a hamper with {@code null} in it.
     */
    static TheBundlesAsTheyStand of(List<OfferMemberLine> lines, List<RewardOffer> everyOffer,
                                    Map<String, HowManyHaveGone> counted,
                                    Map<String, HowManyAreHeld> heldByAnybody) {
        Map<String, RewardOffer> byCode = new HashMap<>();
        everyOffer.forEach(offer -> byCode.put(offer.code(), offer));
        Map<String, List<WhatABundleContains>> contents = new HashMap<>();
        Map<String, Long> taken = new HashMap<>();
        for (OfferMemberLine line : lines) {
            RewardOffer member = byCode.get(line.memberCode());
            if (member == null) {
                continue;
            }
            contents.computeIfAbsent(line.bundleCode(), code -> new ArrayList<>())
                    .add(new WhatABundleContains(member.code(), member.title(), line.quantity()));
            long claimsOfTheBundle = counted
                    .getOrDefault(line.bundleCode(), HowManyHaveGone.noneOf(
                            line.bundleCode()))
                    .goneInAll();
            taken.merge(line.memberCode(), claimsOfTheBundle * line.quantity(), Long::sum);
        }
        return new TheBundlesAsTheyStand(contents, taken, byCode, counted, heldByAnybody);
    }

    /** What one offer contains, and nothing at all when it is not a bundle — which is most of them. */
    List<WhatABundleContains> inside(String code) {
        return contents.getOrDefault(code, List.of());
    }

    /**
     * How many of one offer the bundles containing it have drawn down, and nought when no bundle
     * contains it — which is every offer in the catalogue until somebody composes one.
     */
    long takenFrom(String code) {
        return takenFromMembers.getOrDefault(code, 0L);
    }

    /** One offer by its code, or nothing when the catalogue has no such row. */
    RewardOffer theOffer(String code) {
        return everyOfferByCode.get(code);
    }

    /** How many of one offer have gone out in claims of its own code, from the one grouped count. */
    long claimedInItsOwnRight(String code) {
        return counted.getOrDefault(code, HowManyHaveGone.noneOf(code)).goneInAll();
    }

    /**
     * How many of one offer somebody has set aside right now, from the holds table's own grouped
     * count, and nought when nobody is holding any — which is every offer almost always.
     *
     * <p><strong>Here because a member of a bundle is an offer like any other, and the two
     * slices that arrived together could not see that.</strong> Bundles and holds were written
     * in parallel: one taught the catalogue that a claim can draw down something it does not
     * name, the other that stock can be spoken for without being claimed, and neither subtracted
     * the other's figure from a bundle's members. The consequence was a real one — the last
     * cinema seat held by one customer and handed over inside a hamper to another — so the hold
     * count travels with the bundles and is charged to every member alongside its own claims and
     * the bundles' draws.
     *
     * <p>A separate query and a separate map from {@link #counted}, deliberately: they are counts
     * of two different tables under two different rules, and merging them into one figure would
     * make a cancelled claim and a lapsed hold indistinguishable in the one place somebody has to
     * be able to tell them apart.
     */
    long heldOf(String code) {
        return heldByAnybody.getOrDefault(code, HowManyAreHeld.noneOf(code)).heldInAll();
    }
}
