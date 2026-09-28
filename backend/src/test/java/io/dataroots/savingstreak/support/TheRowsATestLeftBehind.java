package io.dataroots.savingstreak.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

/**
 * The rows a test wrote that the API gives it no way to take back out, deleted straight out of
 * the throwaway database when the test is done with them.
 *
 * <p><strong>This reaches below the seam every test in this suite is written at, and it does so
 * in teardown only.</strong> The rule that matters is the one the base class states: behaviour is
 * asserted at the HTTP API and nowhere lower, because a service- or repository-level seam binds
 * tests to storage decisions. Nothing here asserts anything. It is the equivalent of deleting a
 * temporary file, and the one thing it must never become is a way of arranging a state the API
 * cannot reach — a test that needed that would be testing something this application cannot do.
 *
 * <p><strong>Why it has to exist at all.</strong> One SQLite file serves the whole run, so a row
 * a test leaves behind is a row every later test pays for. Two kinds of row cannot be taken back
 * out over HTTP:
 *
 * <ul>
 *   <li>A bundle's member lines. An offer is withdrawn rather than deleted — that is deliberate
 *       and right, because vouchers already issued have to go on meaning something — but
 *       withdrawing a bundle leaves its lines in {@code offer_member_line}, and there is no
 *       address that un-composes a bundle. That would not matter if the lines were merely inert:
 *       they are not. The catalogue's stock arithmetic short-circuits on <em>the whole table
 *       being empty</em>, because a catalogue with no bundles in it is the one this application
 *       ships, so the first bundle any test ever writes makes every claim in the rest of the run
 *       pay for a member-line query and a grouped hold query it would not otherwise make.
 *   <li>A live hold belonging to another customer. A hold is given up by the customer holding
 *       it, over their own address, and a helper tidying up after a test does not know whose it
 *       was. The alternative is to wait seventy-two hours.
 * </ul>
 *
 * <p><strong>Claims are deliberately not in that list.</strong> A claim is the customer's own
 * history and points were spent to make it: deleting one would leave the points gone and the
 * reason for their going missing, which is precisely the sum two other test classes assert over
 * the seeded customer. Withdrawing the offer is enough — a claim against a withdrawn offer is a
 * voucher that still reads correctly, which is the thing the catalogue was built to allow.
 *
 * <p>Its own connection to the file the run is using, opened and closed around the deletion. The
 * application holds a single pooled connection and SQLite serialises writers, so the two cannot
 * overlap; this runs between tests, when nothing of the application's is in flight.
 */
public final class TheRowsATestLeftBehind {

    private TheRowsATestLeftBehind() {
    }

    /**
     * Deletes the member lines of, and every hold on, each of these offer codes.
     *
     * <p>Both statements are addressed by code and by nothing else, so a test can only ever
     * remove rows about offers it wrote itself — which is the property that makes this safe to
     * call from a teardown while the rest of the run's data sits in the same file. An empty
     * collection does nothing, which is the ordinary case for a test whose offer was refused
     * before it was ever written.
     *
     * @param offerCodes the codes this test wrote, member lines keyed by the bundle and holds by
     *        the offer they are on
     */
    public static void forTheOffers(Collection<String> offerCodes) {
        if (offerCodes.isEmpty()) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + ApiIntegrationTest.databaseFile())) {
            try (Statement waiting = connection.createStatement()) {
                // The application's own connection may be mid-write when a test class finishes
                // beside another; waiting is what SQLite offers instead of failing immediately.
                waiting.execute("pragma busy_timeout = 5000");
            }
            try (PreparedStatement lines = connection.prepareStatement(
                         "delete from offer_member_line where bundle_code = ?");
                 PreparedStatement heldOnes = connection.prepareStatement(
                         "delete from reward_hold where offer_code = ?")) {
                for (String code : offerCodes) {
                    lines.setString(1, code);
                    lines.executeUpdate();
                    heldOnes.setString(1, code);
                    heldOnes.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new AssertionError("could not take back out the rows " + offerCodes
                    + " left behind; every test after this one would pay for them", e);
        }
    }
}
