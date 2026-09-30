package io.workshop.reminders;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** A small, single-process store. Save to disk before publishing a change in memory. */
@Component
public class ReminderStore {
    private final ObjectMapper mapper;
    private final Path file;
    private final boolean newStore;
    private List<Reminder> reminders;

    public ReminderStore(ObjectMapper mapper, @Value("${reminders.data-file}") String filename) throws IOException {
        this.mapper = mapper;
        this.file = Path.of(filename).toAbsolutePath();
        this.newStore = !Files.exists(file);
        this.reminders = newStore ? List.of()
                : List.copyOf(mapper.readValue(file.toFile(), new TypeReference<List<Reminder>>() {}));
    }

    public boolean isNew() {
        return newStore;
    }

    public synchronized List<Reminder> all() {
        return reminders;
    }

    public synchronized void save(List<Reminder> updated) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "reminders-", ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), updated);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            reminders = List.copyOf(updated);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save reminders", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Best effort cleanup. */ }
            }
        }
    }
}
