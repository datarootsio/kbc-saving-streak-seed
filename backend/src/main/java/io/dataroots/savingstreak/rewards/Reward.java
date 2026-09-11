package io.dataroots.savingstreak.rewards;

/**
 * Something points can be spent on, and what it costs.
 *
 * <p>The catalogue is fixed and lives in the code, which is what "fixed-cost catalogue" means here:
 * there is no screen that edits it and no row anybody can add, so a claim can only ever name
 * something the application already knows how to honour. Changing a price is a release, and that is
 * the intent — a loyalty scheme whose prices move without anyone noticing is not one.
 *
 * <p>Declared cheapest first, and served in that order, so the catalogue reads as a ladder from what
 * a first deposit can already afford up to what saving for a while buys.
 */
public enum Reward {

    CHARITY_DONATION(10, "DON", "Charity donation",
            "Ten points, given as money to this season's good cause. Nothing comes back to you."),
    SNACK_VOUCHER(40, "SNK", "Coffee or snack voucher",
            "A coffee and something to go with it, at any counter in the scheme."),
    CINEMA_TICKET(100, "CIN", "Cinema ticket",
            "One seat, one film, any evening of the week."),
    FAMILY_CINEMA_PACK(180, "FAM", "Family cinema pack",
            "Two seats side by side and a snack big enough to share.");

    private final long costInPoints;
    private final String voucherPrefix;
    private final String title;
    private final String description;

    Reward(long costInPoints, String voucherPrefix, String title, String description) {
        this.costInPoints = costInPoints;
        this.voucherPrefix = voucherPrefix;
        this.title = title;
        this.description = description;
    }

    /**
     * What a claim names this reward by, and what a voucher already issued for it is stored under.
     * The name of the constant, so the two can never say different things about the same reward.
     */
    public String code() {
        return name();
    }

    public long costInPoints() {
        return costInPoints;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    /** Three letters at the front of a voucher, so a person holding one can see what it is for. */
    String voucherPrefix() {
        return voucherPrefix;
    }
}
