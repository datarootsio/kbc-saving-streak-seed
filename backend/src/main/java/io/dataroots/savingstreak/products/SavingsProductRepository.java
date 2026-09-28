package io.dataroots.savingstreak.products;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Package-private: the rest of the application goes through {@link ProductsService}.
 *
 * <p><strong>Nothing here deletes a product and nothing ever will.</strong> The inherited delete
 * methods exist because {@code JpaRepository} has them, and no line in this module calls one: an
 * account will point at a product, every interest posting will point at one through the version it
 * was paid under, and a row that went away would orphan all of them. Retiring a product is a flag
 * going false, which stops the next account and disturbs none of the ones already on it.
 */
interface SavingsProductRepository extends JpaRepository<SavingsProduct, Long> {

    /**
     * The whole catalogue in the order somebody chose, open and closed alike.
     *
     * <p>By sort order rather than by rate, and the difference matters the day a fifth product is
     * written. The four seeded ones are ordered from the account that asks nothing of you to the
     * one that asks for a year, which is the order a person actually weighs these in; sorting by
     * rate would give the same answer today and a different one the first time two products pay the
     * same. The identifier breaks a tie, so two products somebody gave the same place to still come
     * back in a settled order rather than whichever the database felt like.
     *
     * <p>Closed products are in it, because a customer holding one has every right to see what they
     * are holding. Whether a closed product may be opened on is a question for whoever is opening;
     * leaving it out of the catalogue would be this query answering it.
     */
    List<SavingsProduct> findAllByOrderBySortOrderAscIdAsc();

    /** One product by the code an account names it with, or nothing if the bank has no such thing. */
    Optional<SavingsProduct> findByCode(String code);

    /** Whether the seed has already written this one, which is what makes seeding idempotent. */
    boolean existsByCode(String code);
}
