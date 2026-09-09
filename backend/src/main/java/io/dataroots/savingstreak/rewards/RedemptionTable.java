package io.dataroots.savingstreak.rewards;

import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The {@code redemption} table as the database actually holds it, which is not always the table the
 * entity describes.
 *
 * <p>The schema is generated from the entity model rather than migrated ({@code ddl-auto=update}),
 * and generation only ever adds. A column this application has stopped writing — because a claim was
 * reshaped, or because the file was first written by something else entirely — stays behind exactly
 * as it was declared, and if it was declared {@code not null} with no default then every insert from
 * here on names fewer columns than the table demands. The claim is refused by the database on the way
 * in, the transaction rolls back, and the customer is told nothing more useful than 500.
 *
 * <p>So this finds those columns and takes them away. Not a list of names to keep up to date: the
 * table is asked what it carries, and anything not-null that this application has no value to put in
 * is by definition a column no claim can ever fill. Naming the leftovers instead would mean the next
 * reshape breaks claiming again and waits for somebody to notice.
 *
 * <p>The one list here is the other half of that question — the columns a claim <em>does</em> fill,
 * which is {@link Redemption}'s mapping written out. It has to move when that entity moves, and a
 * column added there and forgotten here would be dropped on the next start, so it is kept next to
 * the reason it exists rather than anywhere further away.
 */
@Component
class RedemptionTable {

    private static final Logger log = LoggerFactory.getLogger(RedemptionTable.class);

    /**
     * Every column {@link Redemption} writes, as the database spells them. Anything else the table
     * insists on is a leftover, and the point of comparing against this rather than against a list
     * of known leftovers is that this one is checkable by reading the entity beside it.
     */
    private static final Set<String> FILLED_BY_A_CLAIM =
            Set.of("id", "customer_id", "reward", "points_spent", "voucher_code", "claimed_at");

    private final EntityManager entityManager;

    RedemptionTable(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Drops every column the table requires and a claim cannot supply, and answers how many went.
     *
     * <p>Idempotent, and cheap when there is nothing to do: a table this application created itself
     * has no such columns, so the ordinary start reads one catalogue query and stops.
     *
     * <p>Whatever is dropped is gone with whatever was in it. That is the intended trade and it is
     * only ever made against a column this application never reads: keeping the data would mean
     * keeping the column, and keeping the column means no customer can claim anything again.
     */
    @Transactional
    int takeAwayColumnsNoClaimCanFill() {
        List<String> unfillable = columnsNoClaimCanFill();
        if (unfillable.isEmpty()) {
            // Worth a line even though nothing happened: it says the question was asked, so that a
            // claim failing after a restart is not blamed on a step nobody can see.
            log.debug("every column the redemption table requires is one a claim fills columns=0");
            return 0;
        }
        for (String column : unfillable) {
            // One line each, before the drop rather than after it. If the statement is the thing
            // that fails, the log still names the column it failed on.
            log.warn("redemption column cannot be filled by a claim and is being dropped column={}",
                    column);
            entityManager.createNativeQuery("alter table redemption drop column " + column)
                    .executeUpdate();
        }
        log.info("redemption columns no claim can fill dropped columns={} names={}",
                unfillable.size(), unfillable);
        return unfillable.size();
    }

    /**
     * The columns the table demands a value for and a claim has none to give: {@code not null}, no
     * default to fall back on, and not part of what {@link Redemption} writes.
     *
     * <p>Asked of SQLite's own catalogue, because both the shape of this file and the shape of the
     * entity have changed independently of each other and only the database knows what it ended up
     * with. A column with a default is left alone — the table can fill that one itself.
     *
     * <p>The names are the database's, never anybody's input, which is what makes them safe to put
     * into the statement above: there is no binding form for an identifier in SQL.
     */
    private List<String> columnsNoClaimCanFill() {
        @SuppressWarnings("unchecked")
        List<String> required = entityManager
                .createNativeQuery("select name from pragma_table_info('redemption') "
                        + "where \"notnull\" = 1 and dflt_value is null")
                .getResultList();
        return required.stream().filter(column -> !FILLED_BY_A_CLAIM.contains(column)).toList();
    }
}
