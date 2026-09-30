package io.workshop.reminders;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReminderServiceTest {
    @TempDir Path directory;
    ReminderStore store;
    ReminderService service;

    @BeforeEach
    void setUp() throws Exception {
        store = new ReminderStore(JsonMapper.builder().findAndAddModules().build(), directory.resolve("reminders.json").toString());
        service = at("2026-09-29T10:00:00Z");
    }

    ReminderService at(String instant) {
        return new ReminderService(store, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    Reminder create(String dueAt, Reminder.Repeat repeat) {
        return service.create(new ReminderInput("  Send the update  ", Instant.parse(dueAt), repeat, "Europe/Brussels"));
    }

    @Test
    void reminders_survive_a_new_store_instance_including_completion() throws Exception {
        Reminder reminder = create("2026-09-29T09:00:00Z", Reminder.Repeat.ONCE);
        service.complete(reminder.id(), reminder.dueAt());

        var restarted = new ReminderStore(JsonMapper.builder().findAndAddModules().build(), directory.resolve("reminders.json").toString());
        assertThat(restarted.all()).hasSize(1);
        assertThat(restarted.all().get(0).title()).isEqualTo("Send the update");
        assertThat(restarted.all().get(0).completed()).isTrue();
        assertThat(restarted.isNew()).isFalse();
    }

    @Test
    void completing_a_one_off_reminder_twice_keeps_it_completed() {
        Reminder reminder = create("2026-09-29T09:00:00Z", Reminder.Repeat.ONCE);
        Reminder completed = service.complete(reminder.id(), reminder.dueAt());
        assertThat(service.complete(reminder.id(), reminder.dueAt())).isEqualTo(completed);
        assertThat(completed.completed()).isTrue();
    }

    @Test
    void an_overdue_daily_reminder_skips_missed_occurrences_and_keeps_its_local_time() {
        Reminder reminder = create("2026-09-25T07:00:00Z", Reminder.Repeat.DAILY);
        Reminder next = service.complete(reminder.id(), reminder.dueAt());
        assertThat(next.completed()).isFalse();
        assertThat(next.dueAt()).isEqualTo(Instant.parse("2026-09-30T07:00:00Z"));
    }

    @Test
    void retrying_a_daily_completion_does_not_skip_another_day() {
        Reminder reminder = create("2026-09-29T09:00:00Z", Reminder.Repeat.DAILY);
        Reminder next = service.complete(reminder.id(), reminder.dueAt());
        assertThatThrownBy(() -> service.complete(reminder.id(), reminder.dueAt()))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(service.find(reminder.id()).dueAt()).isEqualTo(next.dueAt());
    }

    @Test
    void daily_wall_clock_time_recovers_after_the_spring_daylight_saving_gap() {
        service = at("2026-03-28T12:00:00Z");
        Reminder reminder = create("2026-03-28T01:30:00Z", Reminder.Repeat.DAILY); // 02:30 Brussels
        Reminder gapDay = service.complete(reminder.id(), reminder.dueAt());
        assertThat(gapDay.dueAt()).isEqualTo(Instant.parse("2026-03-29T01:30:00Z")); // 03:30, gap adjusted

        Reminder renamed = service.update(gapDay.id(), new ReminderInput("Renamed reminder", gapDay.dueAt(), gapDay.repeat(), gapDay.timeZone()));
        assertThat(renamed.dailyTime()).isEqualTo(reminder.dailyTime());

        Reminder next = at("2026-03-29T12:00:00Z").complete(reminder.id(), gapDay.dueAt());
        assertThat(next.dueAt()).isEqualTo(Instant.parse("2026-03-30T00:30:00Z")); // 02:30 again
    }

    @Test
    void editing_and_deleting_preserve_the_expected_identity_and_content() {
        Reminder reminder = create("2026-09-29T09:00:00Z", Reminder.Repeat.ONCE);
        Reminder updated = service.update(reminder.id(), new ReminderInput("New title", Instant.parse("2026-10-01T12:00:00Z"), Reminder.Repeat.DAILY, "UTC"));
        assertThat(updated.id()).isEqualTo(reminder.id());
        assertThat(updated.title()).isEqualTo("New title");
        assertThat(updated.repeat()).isEqualTo(Reminder.Repeat.DAILY);
        service.delete(reminder.id());
        assertThat(service.list()).isEmpty();
    }

    @Test
    void deleting_all_examples_does_not_reseed_them_on_restart() throws Exception {
        service.seedExamples();
        service.list().forEach(reminder -> service.delete(reminder.id()));
        var restarted = new ReminderStore(JsonMapper.builder().findAndAddModules().build(), directory.resolve("reminders.json").toString());
        new ReminderService(restarted, Clock.systemUTC()).seedExamples();
        assertThat(restarted.all()).isEmpty();
    }
}
