package io.workshop.reminders;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReminderService {
    private final ReminderStore store;
    private final Clock clock;

    public ReminderService(ReminderStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public List<Reminder> list() {
        return store.all().stream().sorted(Comparator.comparing(Reminder::dueAt).thenComparing(Reminder::id)).toList();
    }

    public Reminder find(UUID id) {
        return store.all().stream().filter(reminder -> reminder.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This reminder no longer exists."));
    }

    public synchronized Reminder create(ReminderInput input) {
        Reminder reminder = fromInput(UUID.randomUUID(), input, false);
        var updated = new ArrayList<>(store.all());
        updated.add(reminder);
        store.save(updated);
        return reminder;
    }

    public synchronized Reminder update(UUID id, ReminderInput input) {
        Reminder current = find(id);
        if (current.completed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed reminders cannot be edited.");
        }
        Reminder updated = fromInput(id, input, false);
        // Renaming a gap-adjusted occurrence must not change the original daily schedule.
        if (current.repeat() == Reminder.Repeat.DAILY && updated.repeat() == Reminder.Repeat.DAILY
                && current.dueAt().equals(updated.dueAt()) && current.timeZone().equals(updated.timeZone())) {
            updated = new Reminder(id, updated.title(), updated.dueAt(), updated.repeat(),
                    updated.timeZone(), current.dailyTime(), false);
        }
        return replace(updated);
    }

    public synchronized Reminder complete(UUID id, Instant expectedDueAt) {
        Reminder current = find(id);
        if (current.completed()) return current;
        // A retried completion must not advance a daily reminder twice.
        if (!current.dueAt().equals(expectedDueAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This reminder changed. Refresh and try again.");
        }
        boolean completed = current.repeat() == Reminder.Repeat.ONCE;
        Instant dueAt = completed ? current.dueAt() : nextDailyOccurrence(current);
        return replace(new Reminder(id, current.title(), dueAt, current.repeat(),
                current.timeZone(), current.dailyTime(), completed));
    }

    public synchronized void delete(UUID id) {
        find(id);
        store.save(store.all().stream().filter(reminder -> !reminder.id().equals(id)).toList());
    }

    private Reminder fromInput(UUID id, ReminderInput input, boolean completed) {
        try {
            ZoneId zone = ZoneId.of(input.timeZone());
            var localTime = input.repeat() == Reminder.Repeat.DAILY ? input.dueAt().atZone(zone).toLocalTime() : null;
            return new Reminder(id, input.title().strip(), input.dueAt(), input.repeat(), zone.getId(), localTime, completed);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid date and time zone.");
        }
    }

    private Instant nextDailyOccurrence(Reminder reminder) {
        ZoneId zone = ZoneId.of(reminder.timeZone());
        ZonedDateTime now = clock.instant().atZone(zone);
        LocalDate nextDate = reminder.dueAt().atZone(zone).toLocalDate().plusDays(1);
        if (nextDate.isBefore(now.toLocalDate())) nextDate = now.toLocalDate();
        // Keep the original wall-clock time even after a daylight-saving gap.
        ZonedDateTime next = nextDate.atTime(reminder.dailyTime()).atZone(zone);
        if (!next.toInstant().isAfter(now.toInstant())) {
            next = nextDate.plusDays(1).atTime(reminder.dailyTime()).atZone(zone);
        }
        return next.toInstant();
    }

    private Reminder replace(Reminder replacement) {
        store.save(store.all().stream()
                .map(reminder -> reminder.id().equals(replacement.id()) ? replacement : reminder).toList());
        return replacement;
    }

    public synchronized void seedExamples() {
        if (!store.isNew() || !store.all().isEmpty()) return;
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = clock.instant().atZone(zone).withSecond(0).withNano(0);
        create(new ReminderInput("Send the project update", now.minusMinutes(15).toInstant(), Reminder.Repeat.ONCE, zone.getId()));
        create(new ReminderInput("Take a screen break", now.plusMinutes(45).toInstant(), Reminder.Repeat.DAILY, zone.getId()));
        create(new ReminderInput("Review tomorrow’s schedule", now.toLocalDate().plusDays(1).atTime(9, 0).atZone(zone).toInstant(),
                Reminder.Repeat.ONCE, zone.getId()));
    }
}
