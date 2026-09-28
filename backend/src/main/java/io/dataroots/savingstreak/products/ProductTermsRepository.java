package io.dataroots.savingstreak.products;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private: the rest of the application goes through {@link ProductsService}.
 *
 * <p><strong>There is no update here and no delete, and that is the module's central
 * promise.</strong> A published version is never edited — {@link ProductTerms} has no mutator to
 * edit it with — and nothing removes one, because an account is living under it and every interest
 * posting will name it. The inherited methods that could do either are present because
 * {@code JpaRepository} has them and are called by nothing.
 */
interface ProductTermsRepository extends JpaRepository<ProductTerms, Long> {

    /**
     * Every version of every product, oldest product first and lowest version first.
     *
     * <p>One query for the whole catalogue rather than one per product, because the catalogue is
     * read as a whole: four products would otherwise be five queries to draw one screen, and the
     * rows this reads in one go are exactly the rows it is about to hand out.
     */
    List<ProductTerms> findAllByOrderByProductCodeAscVersionAsc();

    /** Every version one product has published, lowest first — the history a customer reads. */
    List<ProductTerms> findByProductCodeOrderByVersionAsc(String productCode);

    /**
     * Whether that product has already published that version, which is what makes seeding
     * idempotent at the level of a single row.
     *
     * <p>By the pair rather than by the product, deliberately. Asking "does this product have any
     * terms" would make the seed skip free savings' second version on a database that already had
     * its first, which is the one row this whole feature is visible through.
     */
    boolean existsByProductCodeAndVersion(String productCode, int version);

    /**
     * Whether the index that makes one version per product a rule the database keeps — rather than
     * one this module merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, so that the ordinary start — every
     * start after the first — reads one row and does nothing. {@code create unique index if not
     * exists} would be idempotent on its own; asking first is what lets the start-up step say
     * whether it did anything, which is the difference between a log a reviewer can trust and one
     * that always claims the same thing. The same shape as the Loyalty and Challenges modules'
     * own guarantees.
     */
    @Query(value = "select count(*) from pragma_index_list('product_terms') "
            + "where name = 'one_version_per_product'", nativeQuery = true)
    long aVersionIsAlreadyUniquePerProduct();

    /**
     * Makes it so, over the product code and the version number.
     *
     * <p>Created here rather than declared on the entity because the entity cannot: this schema is
     * generated from the entity model against SQLite, and that dialect writes a composite unique
     * clause nowhere — declared as a constraint, the table is simply created without it. A unique
     * index is a statement SQLite accepts, so this is where the guarantee comes from.
     *
     * <p><strong>It is worth having even though only the seed writes versions today.</strong> Two
     * rows claiming to be free savings version 2 would be two answers to "what was I opened under",
     * and the account pointing at one of them could not say which. The seed also checks in Java and
     * would write nothing twice on its own; the check and the guarantee are different things, and
     * the administration door that publishes a version arrives in a later ticket with no idea this
     * conversation happened.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting
     * against one file cannot race each other into a failure. It carries its own transaction
     * because its one caller runs before the application has a transaction, a request, or a web
     * server.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_version_per_product "
            + "on product_terms (product_code, version)", nativeQuery = true)
    void makeAVersionUniquePerProduct();
}
