# Notification investigation

## The incident

Customers report that balance milestone notifications they have already read reappear on a later day, even though their balances have not changed. The nightly notification job reports success, and the existing tests pass.

You are the engineer responsible for investigating the report and delivering a verified repair. Use a coding agent as your collaborator. You remain responsible for deciding whether its explanation and evidence are convincing.

## Goal

Reproduce the customer-visible failure, explain its cause, and repair it with a regression test that demonstrates the problem before the change and passes afterward.

Preserve notifications for genuine balance changes, including falling below a milestone and reaching it again later.

## What you should learn

- **Investigating with an agent:** guide it through the running application, API, logs, requirements, and code; distinguish observations from hypotheses.
- **Understanding passing tests:** identify which user journeys the existing tests establish and which combinations they miss.
- **Test-driven repair:** turn the incident into a failing test through a meaningful interface, then make the smallest change that addresses its cause.
- **Independent verification:** challenge the repair with related scenarios and a fresh reviewer rather than relying only on the implementer's summary.
- **Context handoff:** leave enough evidence for another person or agent to understand and verify the work without your conversation.

## Start here

Use the trainer-provided checkout and running application, or set up the app locally. This exercise lives on `exercise/notification-investigation` in `kbc-saving-streak-seed`.

For a local setup, install Java 17+ and Node.js 20.19+, then:

```sh
git clone --single-branch --branch exercise/notification-investigation \
  https://github.com/datarootsio/kbc-saving-streak-seed.git kbc-notification-investigation
cd kbc-notification-investigation
git switch -c fix/notification-alerts
```

Run the existing backend tests once and record their result:

```sh
cd backend
./mvnw test
```

From the repository root, start the backend in one terminal:

```sh
cd backend
SAVING_STREAK_DB="$(mktemp -d)/saving-streak.db" ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--logging.level.io.dataroots.savingstreak=DEBUG
```

This command uses a fresh temporary database with development seed data. Stop and rerun it to reset your practice state. The tests manage their own databases.

From the repository root, start the frontend in a second terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open <http://localhost:5173> and choose a demo customer on the sign-in screen. The API runs at <http://localhost:8080>. In a hosted lab, use the ports and URLs supplied by your trainer.

Read the [notification specification](.scratch/notifications/spec.md) for the intended behavior and [repository guidance](CLAUDE.md) for working conventions.

## 1. Investigate and reproduce

Ask the agent to help you investigate before changing code. Give it the incident, access to the repository, and the requirement to provide evidence for its claims.

Explore the notification flow in the UI and API. Use the development tools to advance time and run scheduled work without waiting overnight:

| Action | Endpoint |
| --- | --- |
| Discover the demo customers | `GET /api/customers` |
| Read the application's clock | `GET /api/dev/clock` |
| Advance the clock | `POST /api/dev/clock/advance`, body `{ "days": 1 }` |
| List available jobs | `GET /api/dev/jobs` |
| Run the notification job | `POST /api/dev/jobs/raiseNotifications/run` |
| List a customer's notifications | `GET /api/customers/{id}/notifications` |
| Mark their notifications read | `POST /api/customers/{id}/notifications/read` |

Record a short sequence that reliably reproduces the report. Capture the account balance, notification identifiers and read state, API responses, and relevant log lines. Compare the observed result with the specification.

Ask the agent to explain the cause and why the existing tests did not expose it. Challenge explanations that merely repeat the symptom or lack evidence.

**Done when:** another person can follow your steps and observe the same failure, and your explanation connects the behavior to the code that produces it.

## 2. Write the regression and repair the cause

Turn your reproduction into an automated test using the repository's existing integration-test support. Exercise the real behavior through HTTP and SQLite rather than mocking away the part you are investigating.

Run the new test against the starting implementation. Confirm that it fails for the notification behavior you observed, rather than a setup error.

Guide the agent to make a focused repair. Run the new test again, then run the existing suite. Review the diff and remove unrelated changes.

**Done when:** you have evidence that the new test fails before the repair and passes afterward, and the existing tests still pass.

## 3. Verify beyond the original report

Test the repair against related customer journeys. Include an unchanged balance, a genuine upward crossing, a genuine downward crossing, and falling below a milestone before reaching it again. Consider reading notifications at different points in these journeys.

Have a fresh agent session or another pair review the requirement, reproduction, tests, and diff. Ask for concrete findings and evidence, including anything they could not verify. Address valid findings and repeat the affected checks.

**Done when:** the original failure is gone, legitimate alerts still work, and an independent reviewer can explain why the repair is sufficient.

## Deliverables

Commit your regression test and repair on your own branch. Save a short report in `docs/notification-investigation.md` containing:

- Reproduction steps, expected behavior, and observed behavior.
- The cause, with code references and evidence.
- Why the original tests passed despite the incident.
- The repair and its scope.
- Commands and results for the failing regression, repaired regression, and existing suite.
- Related scenarios checked, review findings, and remaining verification gaps.

Be ready to demonstrate the incident and the repair. Another pair should be able to understand and verify your work using the repository and report alone.
