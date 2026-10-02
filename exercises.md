# Duplicate notifications

The worked-out Saving Streak app has a bug: customers receive duplicate balance milestone notifications. The existing tests pass. Reading a notification should not make it reappear when the balance is unchanged.

**Goal:** understand agentic capabilities, verification loops, and subagents by investigating and repairing a real failure.

## Your task

1. Use AI to investigate why the duplicates occur before changing code. Follow how the agent finds the relevant code: what it searches, which tools it uses, and how it checks its assumptions.
2. Reproduce the issue. Explain the sequence that triggers it and why the implementation produces the failure.
3. Propose a fix and test it. Observe how the agent verifies its own work. Check that a regression test detects the original failure and passes after the repair, and that legitimate notifications still work.
4. Have another agent review the fix in a fresh context. Give it the issue, your evidence, and the changes. Evaluate its findings and address valid concerns.

## Be ready to show

The reproduction, your explanation of the cause, the fix and verification evidence, and the review findings. Explain how the agent discovered the issue and what made its verification convincing.

## Run

Requires Java 17+ and Node.js 20.19+. From the repository root, use separate terminals:

```sh
# Backend: http://localhost:8080
./backend/mvnw -f backend/pom.xml spring-boot:run

# Frontend: http://localhost:5173
npm --prefix frontend ci
npm --prefix frontend run dev
```

Tests: `./backend/mvnw -f backend/pom.xml test`. The development app includes demo customers and controls to advance time and run scheduled jobs.

A deposit taking a savings balance from below €100 to €100 or more raises a milestone notification immediately. Other notification checks run in the nightly job.
