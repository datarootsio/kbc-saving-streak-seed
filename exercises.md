# The specification swap

## The request

“Add a snooze feature to our reminder app.”

The app already supports one-off and daily reminders, completion, editing, and persistence. Due reminders are highlighted while the app is open. Snooze has not been implemented, and its behavior has deliberately been left open.

Work in pairs. One person plays the product owner; the other directs a coding agent. You will clarify the request, save the agreement, and exchange it with another pair. That pair will implement one slice in a fresh agent session using your saved artifacts.

## Goal

Produce a specification and task breakdown that another pair can use to implement and demonstrate the agreed behavior without access to your original conversation.

The exercise succeeds when the receiving pair's implementation matches the product owner's examples. A feature that merely looks like snooze is not enough.

## What you should learn

- **Resolving ambiguity:** turn a short request into clear behavior through questions, examples, and explicit decisions.
- **Aligning with an agent:** check its understanding, challenge assumptions, and correct misunderstandings before it writes code.
- **Slicing work:** divide a feature into small, complete behaviors that can be implemented and verified independently, with genuine dependencies recorded.
- **Managing context:** decide what a fresh session needs, and preserve decisions in repository artifacts rather than relying on conversation history.
- **Verifying intent:** judge the result against the agreed examples and use mismatches to improve the specification or implementation.

## Start here

Use the trainer-provided checkout and running app, or set it up locally. The starter lives on the `exercise/reminder-app` branch of `kbc-saving-streak-seed`.

For a local setup, install Java 17+ and run:

```sh
git clone --single-branch --branch exercise/reminder-app \
  https://github.com/datarootsio/kbc-saving-streak-seed.git kbc-reminder-exercise
cd kbc-reminder-exercise
git switch -c feature/snooze
./mvnw spring-boot:run
```

Open <http://localhost:8080>. If that port is already in use:

```sh
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8085
```

Then open <http://localhost:8085>. In a hosted lab, use the URL supplied by your trainer. See the [README](README.md) for the starter's behavior, daily scheduling rules, and browser-test setup.

Try creating, editing, and completing both a one-off reminder and a daily reminder. Refresh the page and check what persists. Run the existing backend tests and record the result:

```sh
./mvnw test
```

## 1. Prepare the product-owner role

The product owner privately decides how they want snooze to work before the interview. Write those decisions on a separate card; keep it out of the coding agent's context and the receiving pair's handoff.

Decide what snoozing changes, how the duration is chosen, which reminders can be snoozed, what happens if snooze is used again, how daily reminders behave, and what should survive a refresh or restart. Keep the desired behavior small enough for the workshop.

The interviewer starts with the request and the working starter. Their job is to discover the decisions through the interview.

**Done when:** the product owner has a consistent intended behavior and examples they can use to judge the eventual result.

## 2. Interview and align

Ask the agent to help you interview the product owner before implementing anything. Have it ask questions, challenge assumptions, and summarize the agreement in plain language.

Use concrete examples to expose ambiguity. For example: a daily 09:00 reminder is snoozed at 09:05 for ten minutes. What should the customer see immediately, at 09:15, and the following morning? What if they refresh or snooze it again?

Explicitly agree whether the app's existing in-page due highlights remain the notification mechanism for this slice.

Compare the agent's summary with the product owner's private decisions. Correct misunderstandings until both people and the agent share the same understanding. Record unresolved questions instead of silently filling them in.

**Done when:** the product owner confirms the behavior through examples, and the agent's summary contains no unapproved assumptions.

## 3. Save the specification and slice the work

Ask the agent to capture the agreement in `docs/snooze/spec.md`. Include the customer problem, agreed behavior, scope, acceptance examples, relevant existing behavior, and unresolved questions. Make the document usable without the interview transcript.

Then create `docs/snooze/tasks.md`. Each task should deliver one narrow behavior, include observable acceptance criteria, and identify its blockers. Prefer a small complete slice over separate tasks for every technical layer.

Choose one unblocked slice for the receiving pair. Make clear which behavior it must deliver and which parts of the broader feature are outside it. Resolve questions that block that slice before the swap.

Review both files yourself and have the product owner check the examples. Commit them on your feature branch.

**Done when:** another pair can identify a slice they can start now, understand its boundaries, and tell how to demonstrate it.

## 4. Swap and implement in a fresh session

Exchange the specification and tasks with another pair. Give them the same starter revision and access to the saved artifacts. Keep the original chat and private product-owner card out of the handoff.

The receiving pair starts a fresh agent session. Ask it to explain the selected slice, its acceptance examples, and any missing information before coding. Record any clarification the handoff still requires; that is evidence about the specification's quality.

Implement only the selected slice. Add tests for its agreed behavior through the existing API or UI, run the relevant checks, and inspect the diff for changes outside the agreement. Restart the backend after frontend edits so the updated files are served.

**Done when:** the slice is implemented and checked, and the receiving pair can demonstrate each of its acceptance examples.

## 5. Demonstrate and examine the handoff

Demonstrate the implemented slice to the original product owner. Walk through the agreed examples, including the relevant refresh, repeat, and daily-schedule cases. Compare the actual result with the private card and saved specification.

For each mismatch, decide whether a decision was never discovered, the specification lost it, the receiver interpreted it differently, or the implementation did not follow the agreement. Update the relevant artifact and repeat the affected verification.

Save a short record in `docs/snooze/verification.md`: the slice demonstrated, commands and test results, observed behavior, clarification requests, mismatches, corrections, and anything left unverified.

**Done when:** the product owner accepts the demonstrated slice against the agreed examples, with any remaining gaps stated explicitly.

## Deliverables

- A committed specification with behavior, scope, examples, and explicit open questions.
- A task breakdown with acceptance criteria, dependencies, and one selected slice.
- A committed implementation and tests for that slice from the receiving pair.
- A verification record explaining what the swap preserved, what it lost, and how the result was checked.

Be ready to explain which decisions were hardest to communicate, what context the fresh agent actually needed, and how you would improve the next handoff.
