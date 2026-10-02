package io.workshop.reminders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurringReminderTest {
    @TempDir Path directory;
    ReminderStore store;

    @BeforeEach
    void setUp() throws Exception {
        store = openStore();
    }

    ReminderStore openStore() throws Exception {
        return new ReminderStore(JsonMapper.builder().findAndAddModules().build(),
                directory.resolve("reminders.json").toString());
    }

    ReminderService at(String now) {
        return new ReminderService(store, Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }

    ReminderInput weekly(String dueAt, DayOfWeek day) {
        return new ReminderInput("Weekly review", Instant.parse(dueAt), Reminder.Repeat.WEEKLY,
                "Europe/Brussels", day, null);
    }

    ReminderInput monthly(String dueAt, int day) {
        return new ReminderInput("Monthly review", Instant.parse(dueAt), Reminder.Repeat.MONTHLY,
                "Europe/Brussels", null, day);
    }

    @Test
    void a_weekly_reminder_starts_on_the_chosen_weekday_and_completes_to_the_following_week() {
        var service = at("2026-10-01T10:00:00Z");
        var reminder = service.create(weekly("2026-09-29T07:00:00Z", DayOfWeek.FRIDAY));
        assertThat(reminder.dueAt()).isEqualTo(Instant.parse("2026-10-02T07:00:00Z"));
        var next = service.complete(reminder.id(), reminder.dueAt());
        assertThat(next.dueAt()).isEqualTo(Instant.parse("2026-10-09T07:00:00Z"));
        assertThat(next.dayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(next.completed()).isFalse();
    }

    @Test
    void an_overdue_weekly_reminder_skips_missed_weeks() {
        var service = at("2026-10-12T10:00:00Z");
        var reminder = service.create(weekly("2026-09-25T07:00:00Z", DayOfWeek.FRIDAY));
        assertThat(service.complete(reminder.id(), reminder.dueAt()).dueAt())
                .isEqualTo(Instant.parse("2026-10-16T07:00:00Z"));
    }

    @Test
    void weekly_wall_clock_time_recovers_after_a_gap_even_when_the_gap_occurrence_is_renamed() {
        var service = at("2026-03-28T12:00:00Z");
        var reminder = service.create(weekly("2026-03-22T01:30:00Z", DayOfWeek.SUNDAY));
        var gap = service.complete(reminder.id(), reminder.dueAt());
        assertThat(gap.dueAt()).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
        var renamed = service.update(gap.id(), new ReminderInput("Renamed", gap.dueAt(), gap.repeat(),
                gap.timeZone(), gap.dayOfWeek(), gap.dayOfMonth()));
        assertThat(renamed.dailyTime()).isEqualTo(reminder.dailyTime());
        assertThat(at("2026-03-29T12:00:00Z").complete(renamed.id(), renamed.dueAt()).dueAt())
                .isEqualTo(Instant.parse("2026-04-05T00:30:00Z"));
    }

    @Test
    void selecting_a_monthly_day_before_the_start_date_starts_in_the_next_month() {
        var reminder = at("2026-10-01T10:00:00Z").create(monthly("2026-10-20T07:00:00Z", 15));
        assertThat(reminder.dueAt()).isEqualTo(Instant.parse("2026-11-15T08:00:00Z"));
    }

    @Test
    void monthly_day_thirty_one_uses_februarys_last_day_then_returns_to_thirty_one_after_reload() throws Exception {
        var service = at("2026-01-31T12:00:00Z");
        var reminder = service.create(monthly("2026-01-31T08:00:00Z", 31));
        var february = service.complete(reminder.id(), reminder.dueAt());
        assertThat(february.dueAt()).isEqualTo(Instant.parse("2026-02-28T08:00:00Z"));
        assertThat(february.dayOfMonth()).isEqualTo(31);
        store = openStore();
        var renamed = at("2026-02-28T12:00:00Z").update(february.id(),
                new ReminderInput("Renamed", february.dueAt(), february.repeat(), february.timeZone(), null, 31));
        assertThat(at("2026-02-28T12:00:00Z").complete(renamed.id(), renamed.dueAt()).dueAt())
                .isEqualTo(Instant.parse("2026-03-31T07:00:00Z"));
    }

    @Test
    void monthly_day_thirty_one_uses_february_twenty_nine_in_a_leap_year() {
        var service = at("2028-01-31T12:00:00Z");
        var reminder = service.create(monthly("2028-01-31T08:00:00Z", 31));
        assertThat(service.complete(reminder.id(), reminder.dueAt()).dueAt())
                .isEqualTo(Instant.parse("2028-02-29T08:00:00Z"));
    }

    @Test
    void an_overdue_monthly_reminder_skips_missed_months_and_todays_elapsed_time() {
        var service = at("2026-06-30T12:00:00Z");
        var reminder = service.create(monthly("2026-01-31T08:00:00Z", 31));
        assertThat(service.complete(reminder.id(), reminder.dueAt()).dueAt())
                .isEqualTo(Instant.parse("2026-07-31T07:00:00Z"));
    }

    @Test
    void monthly_wall_clock_time_recovers_after_the_spring_gap() {
        var service = at("2026-03-28T12:00:00Z");
        var reminder = service.create(monthly("2026-02-28T01:30:00Z", 29));
        var gap = service.complete(reminder.id(), reminder.dueAt());
        assertThat(gap.dueAt()).isEqualTo(Instant.parse("2026-03-29T01:30:00Z"));
        var renamed = service.update(gap.id(), new ReminderInput("Renamed", gap.dueAt(), gap.repeat(),
                gap.timeZone(), null, 29));
        assertThat(at("2026-03-29T12:00:00Z").complete(renamed.id(), renamed.dueAt()).dueAt())
                .isEqualTo(Instant.parse("2026-04-29T00:30:00Z"));
    }

    @Test
    void retries_do_not_complete_another_week_or_month() {
        for (var input : new ReminderInput[] {
                weekly("2026-10-02T07:00:00Z", DayOfWeek.FRIDAY), monthly("2026-10-02T07:00:00Z", 2) }) {
            var service = at("2026-10-02T12:00:00Z");
            var reminder = service.create(input);
            var next = service.complete(reminder.id(), reminder.dueAt());
            assertThatThrownBy(() -> service.complete(reminder.id(), reminder.dueAt()))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(409));
            assertThat(service.find(reminder.id()).dueAt()).isEqualTo(next.dueAt());
        }
    }

    @Test
    void reminders_saved_before_weekly_and_monthly_support_still_load_and_recur() throws Exception {
        UUID id = UUID.randomUUID();
        Files.writeString(directory.resolve("reminders.json"), """
                [{"id":"%s","title":"Legacy daily","dueAt":"2026-10-02T09:00:00Z",
                  "repeat":"DAILY","timeZone":"UTC","dailyTime":"09:00:00","completed":false}]
                """.formatted(id));
        store = openStore();
        assertThat(at("2026-10-02T12:00:00Z").complete(id, Instant.parse("2026-10-02T09:00:00Z")).dueAt())
                .isEqualTo(Instant.parse("2026-10-03T09:00:00Z"));
    }
}
