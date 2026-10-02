package io.workshop.reminders;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
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
        // Renaming a gap-adjusted occurrence must not change its original wall-clock time.
        if (current.repeat() != Reminder.Repeat.ONCE && current.repeat() == updated.repeat()
                && current.dueAt().equals(updated.dueAt()) && current.timeZone().equals(updated.timeZone())
                && java.util.Objects.equals(current.dayOfWeek(), updated.dayOfWeek())
                && java.util.Objects.equals(current.dayOfMonth(), updated.dayOfMonth())) {
            updated = new Reminder(id, updated.title(), updated.dueAt(), updated.repeat(),
                    updated.timeZone(), current.dailyTime(), updated.dayOfWeek(), updated.dayOfMonth(),
                    current.visibleFrom(), false);
        }
        return replace(updated);
    }

    public synchronized Reminder complete(UUID id, Instant expectedDueAt) {
        Reminder current = find(id);
        if (current.completed()) return current;
        // A retried completion must not advance a recurring reminder twice.
        if (!current.dueAt().equals(expectedDueAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This reminder changed. Refresh and try again.");
        }
        boolean completed = current.repeat() == Reminder.Repeat.ONCE;
        Instant dueAt = completed ? current.dueAt() : nextOccurrence(current);
        Instant visibleFrom = completed ? null : dueAt.atZone(ZoneId.of(current.timeZone()))
                .toLocalDate().atStartOfDay(ZoneId.of(current.timeZone())).toInstant();
        return replace(new Reminder(id, current.title(), dueAt, current.repeat(),
                current.timeZone(), current.dailyTime(), current.dayOfWeek(), current.dayOfMonth(), visibleFrom, completed));
    }

    public synchronized void delete(UUID id) {
        find(id);
        store.save(store.all().stream().filter(reminder -> !reminder.id().equals(id)).toList());
    }

    private Reminder fromInput(UUID id, ReminderInput input, boolean completed) {
        if (input.repeat() == Reminder.Repeat.WEEKLY && input.dayOfWeek() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a day of the week.");
        }
        if (input.repeat() == Reminder.Repeat.MONTHLY
                && (input.dayOfMonth() == null || input.dayOfMonth() < 1 || input.dayOfMonth() > 31)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a day of the month from 1 to 31.");
        }
        try {
            ZoneId zone = ZoneId.of(input.timeZone());
            var local = input.dueAt().atZone(zone);
            var localTime = input.repeat() != Reminder.Repeat.ONCE ? local.toLocalTime() : null;
            var weekday = input.repeat() == Reminder.Repeat.WEEKLY ? input.dayOfWeek() : null;
            var monthDay = input.repeat() == Reminder.Repeat.MONTHLY ? input.dayOfMonth() : null;
            LocalDate date = local.toLocalDate();
            if (weekday != null) date = date.with(TemporalAdjusters.nextOrSame(weekday));
            if (monthDay != null) {
                YearMonth month = YearMonth.from(date);
                LocalDate selected = inMonth(month, monthDay);
                date = selected.isBefore(date) ? inMonth(month.plusMonths(1), monthDay) : selected;
            }
            // Keep the exact supplied instant when its date already matches the recurrence.
            Instant dueAt = date.equals(local.toLocalDate()) ? input.dueAt()
                    : date.atTime(localTime).atZone(zone).toInstant();
            return new Reminder(id, input.title().strip(), dueAt, input.repeat(), zone.getId(),
                    localTime, weekday, monthDay, null, completed);
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid date and time zone.");
        }
    }

    private Instant nextOccurrence(Reminder reminder) {
        ZoneId zone = ZoneId.of(reminder.timeZone());
        ZonedDateTime now = clock.instant().atZone(zone);
        LocalDate currentDate = reminder.dueAt().atZone(zone).toLocalDate();
        // Completing counts for today; an overdue reminder must not immediately return later today.
        LocalDate nextDate = currentDate.isAfter(now.toLocalDate()) ? currentDate : now.toLocalDate().plusDays(1);
        nextDate = switch (reminder.repeat()) {
            case DAILY -> nextDate;
            case WEEKLY -> nextDate.with(TemporalAdjusters.nextOrSame(reminder.dayOfWeek()));
            case MONTHLY -> inMonth(YearMonth.from(nextDate), reminder.dayOfMonth());
            case ONCE -> throw new IllegalArgumentException("One-off reminders do not recur.");
        };
        // Keep the saved wall-clock time and monthly anchor even after a DST gap or short month.
        ZonedDateTime next = nextDate.atTime(reminder.dailyTime()).atZone(zone);
        while (!nextDate.isAfter(now.toLocalDate()) || !next.toInstant().isAfter(now.toInstant())
                || !next.toInstant().isAfter(reminder.dueAt())) {
            nextDate = switch (reminder.repeat()) {
                case DAILY -> nextDate.plusDays(1);
                case WEEKLY -> nextDate.plusWeeks(1);
                case MONTHLY -> inMonth(YearMonth.from(nextDate).plusMonths(1), reminder.dayOfMonth());
                case ONCE -> throw new IllegalArgumentException("One-off reminders do not recur.");
            };
            next = nextDate.atTime(reminder.dailyTime()).atZone(zone);
        }
        return next.toInstant();
    }

    private LocalDate inMonth(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
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
