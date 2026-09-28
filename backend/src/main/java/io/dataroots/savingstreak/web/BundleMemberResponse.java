package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.rewards.WhatABundleContains;

/**
 * One thing that is inside a bundle, as every screen that draws one reads it: its code, what it
 * is called, and how many of it go in.
 *
 * <p>One response for all three surfaces, unlike the offer itself — which has a customer's
 * reading, an administrator's and a plain catalogue entry, all deliberately apart. The reason
 * the three are apart does not apply here: a line of a bundle says the same thing to a customer
 * choosing one, to whoever composed it, and to somebody at a till putting it on the counter, and
 * there is nothing on it that one of them may see and another may not. Three identical records
 * would be three places to add a field to.
 *
 * <p>No price, because a bundle carries its own and the members' prices are not what anybody is
 * charged; the argument is on the module's own {@code WhatABundleContains}. No count of how many
 * of the member are left either: how many of the <em>bundle</em> can be handed over already
 * accounts for every member's stock, and it is beside the bundle where somebody can act on it.
 */
record BundleMemberResponse(String code, String title, int quantity) {

    static BundleMemberResponse of(WhatABundleContains line) {
        return new BundleMemberResponse(line.code(), line.title(), line.quantity());
    }
}
