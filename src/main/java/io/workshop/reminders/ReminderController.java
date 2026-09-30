package io.workshop.reminders;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderService reminders;

    public ReminderController(ReminderService reminders) {
        this.reminders = reminders;
    }

    @GetMapping
    public List<Reminder> list() { return reminders.list(); }

    @GetMapping("/{id}")
    public Reminder get(@PathVariable UUID id) { return reminders.find(id); }

    @PostMapping
    public ResponseEntity<Reminder> create(@Valid @RequestBody ReminderInput input) {
        Reminder reminder = reminders.create(input);
        return ResponseEntity.created(URI.create("/api/reminders/" + reminder.id())).body(reminder);
    }

    @PutMapping("/{id}")
    public Reminder update(@PathVariable UUID id, @Valid @RequestBody ReminderInput input) {
        return reminders.update(id, input);
    }

    public record Completion(@NotNull(message = "Include the occurrence being completed.") Instant dueAt) {}

    @PostMapping("/{id}/complete")
    public Reminder complete(@PathVariable UUID id, @Valid @RequestBody Completion input) {
        return reminders.complete(id, input.dueAt());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        reminders.delete(id);
        return ResponseEntity.noContent().build();
    }
}
