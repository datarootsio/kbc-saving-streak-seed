package io.dataroots.savingstreak.rewards;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private: the rest of the application goes through {@link RewardsService}. */
interface OfferMemberLineRepository extends JpaRepository<OfferMemberLine, Long> {

    /**
     * Every member line in the catalogue, in the order they were written.
     *
     * <p><strong>All of them in one read rather than one read per bundle, and the reason is the
     * reason the claim counts are one grouped query.</strong> Both readings that need these need
     * them for the whole catalogue at once: the customer's page has to say what is in every
     * bundle on it, and — the part that is easy to miss — an <em>item's</em> remaining stock now
     * depends on every bundle that contains it, so even a reading with no bundle on it would
     * have to ask. A query per row is how a page that was fast in a training session stops being
     * fast in a demonstration.
     *
     * <p>Small, and expected to stay small: a bundle has two or three lines and a catalogue has a
     * handful of bundles. The one place this would stop being true is a catalogue of hundreds,
     * which is the same place the grouped count of every claim ever made would stop being true,
     * so the two would be answered together or not at all.
     *
     * <p>Empty is the ordinary answer and the one the whole application ships in — nothing seeded
     * is a bundle — and {@code RewardsService} short-circuits on it so that a catalogue with no
     * bundles in it does exactly the arithmetic it did before this table existed.
     *
     * <p>In written order, so that "what is in it" reads back in the order somebody composed it.
     */
    List<OfferMemberLine> findAllByOrderByIdAsc();

    /**
     * The lines of one bundle, in the order they were composed in.
     *
     * <p>Beside the whole-catalogue read above rather than instead of it, because the two answer
     * different shapes of question. A customer's page and every stock figure on it need all of
     * them at once; the back office reading one offer, and a counter holding one voucher, need
     * one bundle's worth and have no catalogue in front of them — which is the same distinction
     * {@code countByReward} draws against the grouped count of the claims it sits beside.
     */
    List<OfferMemberLine> findByBundleCodeOrderByIdAsc(String bundleCode);
}
