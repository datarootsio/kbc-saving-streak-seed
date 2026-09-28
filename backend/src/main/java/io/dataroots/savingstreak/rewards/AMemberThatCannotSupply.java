package io.dataroots.savingstreak.rewards;

/**
 * The line of a bundle that is the reason nobody can have one: what it is, how many of it the
 * bundle needs, and how many of it there actually are.
 *
 * <p><strong>It exists so that the card and the refusal can say the same true sentence.</strong>
 * "The hamper has sold out" is what a sold-out offer says, and it is not quite a lie about a
 * bundle whose popcorn ran out — but it sends somebody to ask when the hampers are coming back
 * when what is actually coming back is popcorn. A customer refused a bundle is owed the part of
 * it that is missing, and so is whoever runs the scheme when they read the log. The figure is on
 * here as well as the quantity because "it needs two and there is one" is the whole of what is
 * wrong, and either number on its own is half of it.
 *
 * <p>The first one that is short and not a list of all of them. One reason, always, is the rule
 * the whole check order is written on: a refusal is an instruction rather than an inventory, and
 * a customer cannot act on either of two things being out of stock any more than on one.
 *
 * <p>Package-private, and it travels on {@link WhatIsKnownToday} rather than on the reading that
 * leaves the module. What the customer receives is the sentence it produced.
 *
 * @param line the member that cannot supply, as a page would name it
 * @param whatIsLeftOfIt how many of that member there are, which is fewer than the bundle needs
 */
record AMemberThatCannotSupply(WhatABundleContains line, int whatIsLeftOfIt) {
}
