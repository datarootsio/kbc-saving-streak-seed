package io.workshop.reminders;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReminderInput(
        @NotBlank(message = "Enter a reminder title.")
        @Size(max = 120, message = "Use a title of 120 characters or fewer.") String title,
        @NotNull(message = "Choose a date and time.") Instant dueAt,
        @NotNull(message = "Choose a repeat option.") Reminder.Repeat repeat,
        @NotBlank(message = "Choose a time zone.") String timeZone) {
}
