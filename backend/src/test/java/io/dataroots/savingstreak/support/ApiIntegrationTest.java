package io.dataroots.savingstreak.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.dataroots.savingstreak.SavingStreakApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The seam this application is tested at: the whole thing booted for real, talking to a real
 * SQLite database, driven over HTTP the way the frontend drives it.
 *
 * <p>Behaviour is asserted here and nowhere lower. A service- or repository-level seam would bind
 * tests to the storage decisions that later slices need free to change. A test may still read
 * configuration directly where the configuration is itself the thing under test and has no
 * observable behaviour yet — such a test says so in a comment.
 *
 * <p>The database is a throwaway file rather than an in-memory substitute: the dialect's quirks,
 * identity generation and write serialisation among them, should fail in a test rather than at a
 * demo. One file serves the whole run, so tests assert on what their own requests changed rather
 * than on absolute state they did not put there.
 */
@SpringBootTest(classes = SavingStreakApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public abstract class ApiIntegrationTest {

    private static final Path DATABASE_FILE = aDatabaseFileThatDoesNotExistYet("saving-streak-test");

    @Autowired
    protected TestRestTemplate http;

    /** The file this run's application is reading and writing, for tests that restart against it. */
    protected static Path databaseFile() {
        return DATABASE_FILE;
    }

    /**
     * A path in a fresh temporary directory where no database exists yet, discarded when the JVM
     * exits. Starting an application against one of these is how a first-ever start is tested.
     */
    protected static Path aDatabaseFileThatDoesNotExistYet(String purpose) {
        try {
            Path directory = Files.createTempDirectory(purpose);
            directory.toFile().deleteOnExit();
            Path file = directory.resolve("saving-streak.db");
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("could not create a throwaway database for " + purpose, e);
        }
    }

    @DynamicPropertySource
    static void useThrowawayDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATABASE_FILE);
    }
}
