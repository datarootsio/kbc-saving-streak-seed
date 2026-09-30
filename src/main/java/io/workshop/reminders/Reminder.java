package io.workshop.reminders;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record Reminder(UUID id, String title, Instant dueAt, Repeat repeat,
                       String timeZone, LocalTime dailyTime, boolean completed) {
    public enum Repeat { ONCE, DAILY }
}
