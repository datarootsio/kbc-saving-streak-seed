package io.workshop.reminders;

import java.time.Instant;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.UUID;

public record Reminder(UUID id, String title, Instant dueAt, Repeat repeat,
                       String timeZone, LocalTime dailyTime, DayOfWeek dayOfWeek,
                       Integer dayOfMonth, boolean completed) {
    // dailyTime is the saved wall-clock time for every recurring schedule, including legacy data.
    public enum Repeat { ONCE, DAILY, WEEKLY, MONTHLY }
}
