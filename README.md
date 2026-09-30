# Remind — reminder app

A small reminder app for the **specification swap** exercise. Java 17 and Spring Boot serve an AngularJS 1.8.3 frontend from a single process. Reminders are saved to a local JSON file; no database server or frontend build is needed.

This application lives on the `exercise/reminder-app` branch of `kbc-saving-streak-seed`.

## Run

Switch to the reminder app before running it:

```sh
git switch exercise/reminder-app
```

Install a Java 17+ JDK, then run from this directory:

```sh
./mvnw spring-boot:run
```

Open **http://localhost:8080**. On Windows, use `mvnw.cmd spring-boot:run`. Maven and dependencies download on the first run. AngularJS is included locally, so the browser does not need a CDN connection.

If port 8080 is already in use:

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8085
```

## What the starter does

- Create, edit, complete, and delete reminders.
- Choose a one-off reminder or a daily reminder.
- See active and completed reminders, with due reminders highlighted in the page.
- Keep changes across refreshes and server restarts.
- Start with three examples the first time the application runs.

This is a single-user, local workshop application. Notifications are **in-page highlights** while the app is open. There are no emails, operating-system notifications, or user accounts.

**Daily reminders:** completing an occurrence schedules the next future occurrence at the same local time in its saved time zone. Renaming a reminder preserves that time zone and daily schedule, including when the browser is in another time zone. Creating a reminder or changing its **When** or **Repeat** value sets the schedule in the browser's time zone, as displayed below the form. Missed days are skipped. Daily reminders stay active; the starter does not keep completion history for each occurrence. Spring daylight-saving gaps move that occurrence forward; the following day returns to the original local time. A repeated request to complete an old occurrence returns `409` instead of advancing the schedule twice.

**Snooze is the student feature.** The starter deliberately leaves its behavior open for the product-owner interview. See [the exercise brief](exercises.md).

## Check

```sh
./mvnw test
```

Tests cover the HTTP workflow, invalid inputs, persistence, daily recurrence, daylight-saving behavior, and repeated completion requests. They use isolated temporary data files.

For browser tests, install Node.js 20+ and set up the pinned test dependencies once:

```sh
npm ci
npx playwright install chromium
```

Then run:

```sh
npm run test:e2e
```

This compiles and starts a separate Java app on **127.0.0.1:18081** with temporary storage, runs the actual AngularJS UI in Chromium, then stops the test app and removes its data. Keep that port free; tests refuse to reuse an existing server. The normal app on port 8080 and `data/reminders.json` are not used. The browser suite covers create/edit/complete with refreshes, title-only edits across time zones and daylight saving, and explicit schedule changes. Failure screenshots and traces go in `output/playwright/test-results/`. On Linux, `npx playwright install --with-deps chromium` can install browser system dependencies if needed.

To build a standalone app:

```sh
./mvnw package
java -jar target/reminder-app-0.0.1-SNAPSHOT.jar
```

Frontend files are copied into the app during Maven resource processing. Restart `spring-boot:run` after frontend edits to pick them up.

## Files

```text
frontend/                            AngularJS controller, HTML, CSS, vendored AngularJS
src/main/java/io/workshop/reminders/  REST API, reminder rules, JSON storage
src/main/resources/                   Server configuration
src/test/                            Backend and HTTP tests
tests/browser/                       Repeatable browser tests and isolated test server
data/reminders.json                  Local runtime data (created automatically)
```

The main places to explore are `ReminderService.java` for behavior, `ReminderController.java` for HTTP endpoints, and `frontend/app.js` for UI interactions.

Set `REMINDERS_DATA_FILE` to use a different JSON file, or `REMINDERS_SEED_DATA=false` to start without example reminders. Only one running app should use a given data file. To start over, stop the app, move `data/reminders.json` aside, and restart. An intentionally emptied list stays empty on restart.

## API

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/api/reminders` | List all reminders, sorted by due time |
| POST | `/api/reminders` | Create a reminder |
| GET | `/api/reminders/{id}` | Read a reminder |
| PUT | `/api/reminders/{id}` | Edit an active reminder |
| POST | `/api/reminders/{id}/complete` | Complete the specified occurrence |
| DELETE | `/api/reminders/{id}` | Delete a reminder |

Create and edit accept:

```json
{
  "title": "Send the agenda",
  "dueAt": "2026-10-01T09:00:00Z",
  "repeat": "ONCE",
  "timeZone": "Europe/Brussels"
}
```

`repeat` is `ONCE` or `DAILY`. Completion accepts `{ "dueAt": "2026-10-01T09:00:00Z" }`, using the exact occurrence returned by the API. The browser displays dates in its local time zone.

Framework references: [Spring Boot 3.5 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [AngularJS 1.8.3 source](https://github.com/angular/angular.js/tree/v1.8.3). AngularJS is the 1.x framework requested for this workshop; its MIT license is included in `frontend/vendor/`.
