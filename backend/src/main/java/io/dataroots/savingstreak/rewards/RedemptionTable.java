package io.dataroots.savingstreak.rewards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

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
 *
 * <p><strong>A column is not the only thing generation leaves behind. A constraint is worse,
 * because nothing can see it.</strong> While the reward on a claim was mapped
 * {@code @Enumerated(STRING)}, Hibernate wrote the four constants into the schema as
 * {@code check (reward in ('CHARITY_DONATION', …))}. Dropping the annotation does not drop the
 * check: generation only ever adds, so every database file written by an earlier release still
 * carries it, and the first claim of a fifth reward code on one of those files is refused by SQLite
 * — on a catalogue somebody runs, which is to say on the whole point of this feature. No test could
 * catch it either, because a throwaway test file gets a fresh table with no check in it, so the
 * suite is green and every real database is broken.
 *
 * <p>SQLite cannot drop a constraint with {@code alter table}, so taking it off means rebuilding
 * the table: the same columns with the same types, defaults, key and unique constraints, the rows
 * copied across, the old table dropped and the new one renamed into its place. That is the
 * documented way and it is what {@link #takeTheOldCatalogueCheckOffTheRewardColumn} does. It reads
 * the shape out of the database rather than writing a fresh {@code create table} from what the
 * entity says today, for the reason the sweep below reads the shape too: a file that has been
 * through another application, or through the release before this one, is not the file this
 * application would have written, and a rebuild that assumed otherwise would take away whatever it
 * had not heard of.
 *
 * <p><strong>The rebuild now fires once for every file this application writes, and that is
 * expected.</strong> A voucher's state is mapped {@code @Enumerated(STRING)}, so generation puts a
 * {@code check (voucher_state in ('ISSUED', …))} on a freshly created table — the same thing it
 * used to do for the catalogue, for a set that this time genuinely belongs to the code. The
 * detection below is deliberately "a check anywhere in the declaration" rather than "the
 * catalogue's check", so the next start of that file rebuilds the table without it and logs the
 * warning once. Losing the constraint costs nothing: {@link VoucherState} is what decides which
 * states exist, the states are written by this application and never by anybody typing, and a
 * check that has to be dropped from every file that has ever been opened twice is not a guarantee
 * anybody can rely on. Keeping the broad detection is what makes the surgery go on working when a
 * later slice adds a state, which is the property worth having.
 */
@Component
class RedemptionTable {

    private static final Logger log = LoggerFactory.getLogger(RedemptionTable.class);

    private static final String THE_TABLE = "redemption";

    /** Where the rebuilt table lives for the three statements between its creation and its rename. */
    private static final String WHILE_IT_IS_BEING_REBUILT = "redemption_without_the_old_check";

    /**
     * A {@code check} constraint anywhere in the table's own declaration.
     *
     * <p>The whole declaration rather than the reward column's part of it, because the rebuild
     * takes every check off and there has never been a second one to keep: the only constraint
     * generation ever wrote here was the catalogue's. Matching the keyword and its bracket rather
     * than the list of four codes matters as well — a file from a release that had five rewards
     * carries a check with five codes in it, and it is exactly as fatal.
     */
    private static final Pattern A_CHECK_CONSTRAINT =
            Pattern.compile("\\bcheck\\s*\\(", Pattern.CASE_INSENSITIVE);

    /**
     * Every column {@link Redemption} writes, as the database spells them. Anything else the table
     * insists on is a leftover, and the point of comparing against this rather than against a list
     * of known leftovers is that this one is checkable by reading the entity beside it.
     */
    private static final Set<String> FILLED_BY_A_CLAIM = Set.of(
            "id", "customer_id", "reward", "title", "points_spent", "voucher_code", "claimed_at",
            "voucher_state", "used_at", "used_by_counter", "expires_on", "cancelled_at",
            "cancelled_because");

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

    /**
     * Rebuilds the table without the {@code check} the fixed catalogue left on it, and answers
     * whether it had to.
     *
     * <p>Idempotent, and cheap when there is nothing to do: a table this application created since
     * the catalogue became rows has no check in it, so the ordinary start reads one catalogue query
     * and stops. A file from before it has exactly one, and the rebuild happens once for the life
     * of that file.
     *
     * <p>First of the two pieces of surgery, and the order is not arbitrary: SQLite refuses to drop
     * a column that a check constraint mentions, so a sweep run against a table still carrying one
     * could be stopped by it. Taking the constraint off first can only make the sweep after it more
     * able to do its job.
     *
     * <p>Nothing is lost. Every column comes across with its type, its default, its place in the
     * key and its uniqueness, every row is copied, and the indexes somebody made by hand are put
     * back afterwards. The only thing the new table is missing is the thing it is missing on
     * purpose.
     */
    @Transactional
    int takeTheOldCatalogueCheckOffTheRewardColumn() {
        String declared = howTheTableIsDeclared();
        if (declared == null || !A_CHECK_CONSTRAINT.matcher(declared).find()) {
            // Worth a line even though nothing happened: it says the question was asked, so that a
            // reward code refused by the database is not blamed on a step nobody can see.
            log.debug("the redemption table checks no column against a fixed list of values "
                    + "checks=0");
            return 0;
        }
        // Before the rebuild rather than after it, and at WARN: this is a file that would refuse
        // any reward code outside the four this application used to have, and if one of the
        // statements below is the thing that fails, the log still says what was being taken off.
        log.warn("the redemption table still checks a column against the fixed catalogue this "
                + "application no longer has, and is being rebuilt without it declaration={}",
                declared);

        List<Column> columns = columnsOfTheTable();
        List<String> indexesToPutBack = indexesSomebodyMadeByHand();
        String names = String.join(", ", columns.stream().map(Column::name).toList());

        run("create table " + WHILE_IT_IS_BEING_REBUILT + " ("
                + theSameShapeWithoutTheChecks(columns) + ")");
        run("insert into " + WHILE_IT_IS_BEING_REBUILT + " (" + names + ") "
                + "select " + names + " from " + THE_TABLE);
        run("drop table " + THE_TABLE);
        run("alter table " + WHILE_IT_IS_BEING_REBUILT + " rename to " + THE_TABLE);
        indexesToPutBack.forEach(this::run);

        log.info("the check the fixed rewards catalogue left on the redemption table was taken off "
                + "columns={} indexesPutBack={}", columns.size(), indexesToPutBack.size());
        return 1;
    }

    /**
     * The table's own {@code create table} statement, as SQLite kept it, and null when there is no
     * such table yet.
     *
     * <p>The statement rather than a catalogue of constraints, because SQLite has no catalogue of
     * constraints: a check exists only as text inside the declaration, which is the whole reason
     * this is a rebuild and not a query somebody could have asked more precisely.
     */
    private String howTheTableIsDeclared() {
        @SuppressWarnings("unchecked")
        List<String> declarations = entityManager
                .createNativeQuery("select sql from sqlite_master "
                        + "where type = 'table' and name = '" + THE_TABLE + "'")
                .getResultList();
        return declarations.isEmpty() ? null : declarations.get(0);
    }

    /** Every column the table has, in the order it has them, with everything worth keeping. */
    private List<Column> columnsOfTheTable() {
        @SuppressWarnings("unchecked")
        List<Object[]> found = entityManager
                .createNativeQuery("select name, type, \"notnull\", dflt_value, pk "
                        + "from pragma_table_info('" + THE_TABLE + "') order by cid")
                .getResultList();
        return found.stream()
                .map(column -> new Column(
                        (String) column[0],
                        (String) column[1],
                        ((Number) column[2]).intValue() == 1,
                        (String) column[3],
                        ((Number) column[4]).intValue()))
                .toList();
    }

    /**
     * The same columns, key and unique constraints, written out with no check anywhere in them.
     *
     * <p>The default comes across as the text SQLite kept, because that is what a default is: an
     * expression, and the only faithful way to write one back is the way it was written.
     *
     * <p>The key is a table constraint rather than a column one even for a single column, which is
     * how generation wrote this table in the first place — {@code id integer, …, primary key (id)},
     * still an alias for the row identifier, so identity generation goes on working.
     */
    private String theSameShapeWithoutTheChecks(List<Column> columns) {
        List<String> parts = new ArrayList<>();
        for (Column column : columns) {
            StringBuilder declared = new StringBuilder(column.name());
            if (column.type() != null && !column.type().isBlank()) {
                declared.append(' ').append(column.type());
            }
            if (column.notNull()) {
                declared.append(" not null");
            }
            if (column.defaultValue() != null) {
                declared.append(" default ").append(column.defaultValue());
            }
            parts.add(declared.toString());
        }
        List<String> key = columns.stream()
                .filter(column -> column.positionInTheKey() > 0)
                .sorted(Comparator.comparingInt(Column::positionInTheKey))
                .map(Column::name)
                .toList();
        if (!key.isEmpty()) {
            parts.add("primary key (" + String.join(", ", key) + ")");
        }
        for (List<String> unique : theColumnsThatMustBeUnique()) {
            parts.add("unique (" + String.join(", ", unique) + ")");
        }
        return String.join(", ", parts);
    }

    /**
     * Every {@code unique} the table declares on itself, each as the columns it covers.
     *
     * <p>Only the ones the table declares — {@code origin = 'u'}. The index behind a primary key is
     * rebuilt from the key clause, and an index somebody created by hand is a statement of its own
     * that is put back verbatim, so re-declaring either here would be a duplicate.
     */
    private List<List<String>> theColumnsThatMustBeUnique() {
        @SuppressWarnings("unchecked")
        List<String> constraints = entityManager
                .createNativeQuery("select name from pragma_index_list('" + THE_TABLE + "') "
                        + "where \"unique\" = 1 and origin = 'u'")
                .getResultList();
        return constraints.stream().map(this::theColumnsUnder).toList();
    }

    private List<String> theColumnsUnder(String index) {
        @SuppressWarnings("unchecked")
        List<String> columns = entityManager
                .createNativeQuery("select name from pragma_index_info('" + index + "') "
                        + "order by seqno")
                .getResultList();
        return columns;
    }

    /**
     * The {@code create index} statements for this table, to run again once the rebuild has taken
     * its name.
     *
     * <p>An index belongs to its table and goes when the table does, so anything created by hand —
     * this application's own start-up steps create several elsewhere, and a file from somewhere
     * else may carry more — has to be put back or the rebuild would quietly take a uniqueness
     * guarantee away with it. The ones SQLite made for itself have no statement and are not here.
     */
    private List<String> indexesSomebodyMadeByHand() {
        @SuppressWarnings("unchecked")
        List<String> statements = entityManager
                .createNativeQuery("select sql from sqlite_master where type = 'index' "
                        + "and tbl_name = '" + THE_TABLE + "' and sql is not null")
                .getResultList();
        return statements;
    }

    /**
     * One statement, run. Everything put into one is read out of SQLite's own catalogue and never
     * out of anybody's input, which is what makes it safe to concatenate: there is no binding form
     * for an identifier, a type or a default in SQL.
     */
    private void run(String statement) {
        log.debug("rebuilding the redemption table statement={}", statement);
        entityManager.createNativeQuery(statement).executeUpdate();
    }

    /** One column as SQLite describes it, which is everything needed to declare it again. */
    private record Column(String name, String type, boolean notNull, String defaultValue,
                          int positionInTheKey) {
    }
}
