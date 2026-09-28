package io.dataroots.savingstreak.scheme;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private: the rest of the application goes through {@link SchemeService}.
 *
 * <p><strong>There is no update here and no delete, and that is the module's central
 * promise.</strong> A published version of the scheme is never edited — {@link SchemeVersion} has
 * no mutator to edit one with — and nothing removes one, because weeks have already been judged
 * under it and a customer's run is re-derived from those judgements every time anybody reads it.
 * The inherited methods that could do either are present because {@code JpaRepository} has them and
 * are called by nothing.
 *
 * <p>One finder, because the scheme is read whole. A handful of rows answer every question this
 * module is asked — which version is in force today, what the history says, which version a
 * particular week was judged under — and reading them all once is both cheaper and more honest than
 * three queries that could disagree about what "in force" means. The derivation that walks
 * twenty-six weeks backwards is the caller that makes this decisive: it needs the whole history in
 * hand rather than a query per week.
 */
interface SchemeVersionRepository extends JpaRepository<SchemeVersion, Long> {

    /** Every version the bank has published, lowest first — the whole history in one read. */
    List<SchemeVersion> findAllByOrderByVersionAsc();

    /**
     * Whether the bank has published that version already, which is what makes seeding idempotent
     * at the level of a single row.
     *
     * <p>By the number rather than by asking whether there are any versions at all, so that the
     * seed reads the same way the products module's does and so that the question it asks is the
     * one the unique index below answers.
     */
    boolean existsByVersion(int version);

    /**
     * Whether the index that makes one row per version a rule the database keeps — rather than one
     * this module merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, so that the ordinary start — every
     * start after the first — reads one row and does nothing. {@code create unique index if not
     * exists} would be idempotent on its own; asking first is what lets the start-up step say
     * whether it did anything, which is the difference between a log a reviewer can trust and one
     * that always claims the same thing. The same shape as the Products, Loyalty and Challenges
     * modules' own guarantees.
     */
    @Query(value = "select count(*) from pragma_index_list('scheme_version') "
            + "where name = 'one_row_per_version_of_the_scheme'", nativeQuery = true)
    long aVersionOfTheSchemeIsAlreadyUnique();

    /**
     * Makes it so, over the version number alone.
     *
     * <p>Over one column rather than two, which is the difference between the scheme and a
     * product's terms: there is one scheme, so "version 4" is an address on its own.
     *
     * <p>Created here rather than declared on the entity because the entity cannot: this schema is
     * generated from the entity model against SQLite, and that dialect writes a unique clause from
     * an annotation nowhere — declared as a constraint, the table is simply created without it. A
     * unique index is a statement SQLite accepts, so this is where the guarantee comes from.
     *
     * <p><strong>It is worth having even though only the seed writes versions today.</strong> Two
     * rows claiming to be version 4 would be two answers to "what was my week judged under", and
     * {@link TheSchemeInForceOn} picks by the highest number whose day has come — so the tie would
     * be settled by whichever row the database happened to hand back first. The door that publishes
     * a version arrives in a later ticket with no idea this conversation happened, and it will count
     * the next number from the rows; two administrators counting at the same moment is exactly what
     * this catches.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting
     * against one file cannot race each other into a failure. It carries its own transaction
     * because its one caller runs before the application has a transaction, a request, or a web
     * server.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_row_per_version_of_the_scheme "
            + "on scheme_version (version)", nativeQuery = true)
    void makeAVersionOfTheSchemeUnique();
}
