package io.dataroots.savingstreak.rewards;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: the rest of the application goes through {@link RewardsService}. */
interface RewardOfferRepository extends JpaRepository<RewardOffer, Long> {

    /**
     * The whole catalogue, in the order it was written.
     *
     * <p>By identifier rather than by price, and the difference matters the day somebody adds an
     * offer. The four seeded entries were declared cheapest first and are written in that order, so
     * this serves exactly the ladder the enum served and the test that asserts the four prices in
     * order goes on passing. Sorting by cost would give the same answer today and a different one
     * the first time two offers share a price; sorting by the order somebody wrote them in is a
     * decision a person made, which is what the challenges catalogue next door says about its own.
     */
    List<RewardOffer> findAllByOrderByIdAsc();

    /**
     * The offers in one state, in that same written order — which is how the customer's catalogue
     * is now read, with {@code PUBLISHED} handed in.
     *
     * <p>A query rather than a filter over the list above, because a draft is not a row the
     * catalogue happens to leave out: it is a row the catalogue must never be able to serve, and
     * asking the database for the published ones means there is one place that can go wrong rather
     * than one per caller. The order is the same order for the same reason — the four seeded
     * entries are written cheapest first, and a customer sees the ladder they saw yesterday.
     */
    List<RewardOffer> findAllByStateOrderByIdAsc(OfferState state);

    /** One offer by the code a claim names it with, or nothing if the catalogue has no such thing. */
    Optional<RewardOffer> findByCode(String code);

    /** Whether the seed has already written this one, which is what makes seeding idempotent. */
    boolean existsByCode(String code);
}
