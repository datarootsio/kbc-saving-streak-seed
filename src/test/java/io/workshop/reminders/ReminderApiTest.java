package io.workshop.reminders;

import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "reminders.seed-data=false")
@AutoConfigureMockMvc
class ReminderApiTest {
    @TempDir static Path directory;
    @Autowired MockMvc http;
    @Autowired ObjectMapper mapper;
    @Autowired ReminderStore store;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry properties) {
        properties.add("reminders.data-file", () -> directory.resolve("reminders.json").toString());
    }

    @BeforeEach
    void cleanStore() { store.save(List.of()); }

    private String validReminder() {
        return """
                {"title":"Send the agenda","dueAt":"2026-10-01T09:00:00Z","repeat":"ONCE","timeZone":"Europe/Brussels"}
                """;
    }

    @Test
    void create_read_update_complete_and_delete_through_http() throws Exception {
        String created = http.perform(post("/api/reminders").contentType("application/json").content(validReminder()))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        String path = "/api/reminders/" + mapper.readTree(created).get("id").asText();
        http.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Send the agenda"));
        http.perform(put(path).contentType("application/json").content(validReminder().replace("Send the agenda", "Updated title")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated title"));
        http.perform(post(path + "/complete").contentType("application/json").content("{\"dueAt\":\"2026-10-01T09:00:00Z\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completed").value(true));
        http.perform(put(path).contentType("application/json").content(validReminder())).andExpect(status().isConflict());
        http.perform(delete(path)).andExpect(status().isNoContent());
        http.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());
        http.perform(get("/api/reminders")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalid_input_has_clear_errors_and_writes_nothing() throws Exception {
        for (String input : List.of(
                validReminder().replace("Send the agenda", "   "),
                validReminder().replace("Send the agenda", "a".repeat(121)),
                validReminder().replace("Europe/Brussels", "Not/A_Zone"),
                validReminder().replace("ONCE", "SOMEDAY"),
                validReminder().replace("2026-10-01T09:00:00Z", "not a date"),
                "{}", "null")) {
            http.perform(post("/api/reminders").contentType("application/json").content(input))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        }
        http.perform(get("/api/reminders")).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void a_missing_or_malformed_id_is_not_a_server_error() throws Exception {
        http.perform(get("/api/reminders/not-a-uuid")).andExpect(status().isBadRequest());
        http.perform(delete("/api/reminders/11111111-1111-1111-1111-111111111111")).andExpect(status().isNotFound());
    }

    @Test
    void angularjs_and_the_frontend_are_served_by_the_same_application() throws Exception {
        http.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(containsString("ng-app=\"reminderApp\"")));
        http.perform(get("/app.js")).andExpect(status().isOk()).andExpect(content().string(containsString("angular.module")));
        http.perform(get("/vendor/angular.min.js")).andExpect(status().isOk()).andExpect(content().string(containsString("AngularJS v1.8.3")));
    }
}
