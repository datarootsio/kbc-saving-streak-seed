package io.workshop.reminders;

import java.time.Instant;
import java.time.DayOfWeek;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReminderInput(
        @NotBlank(message = "Enter a reminder title.")
        @Size(max = 120, message = "Use a title of 120 characters or fewer.") String title,
        @NotNull(message = "Choose a date and time.") Instant dueAt,
        @NotNull(message = "Choose a repeat option.") Reminder.Repeat repeat,
        @NotBlank(message = "Choose a time zone.") String timeZone,
        DayOfWeek dayOfWeek,
        @Min(value = 1, message = "Choose a day of the month from 1 to 31.")
        @Max(value = 31, message = "Choose a day of the month from 1 to 31.") Integer dayOfMonth) {
    public ReminderInput(String title, Instant dueAt, Reminder.Repeat repeat, String timeZone) {
        this(title, dueAt, repeat, timeZone, null, null);
    }
}
