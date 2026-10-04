# Duplicate notifications

The worked-out Saving Streak app has a bug: customers receive duplicate balance milestone notifications. The existing tests pass. Reading a notification should not make it reappear when the balance is unchanged.

**Goal:** understand agentic capabilities, verification loops, and subagents by investigating and repairing a real failure.

## Deploy on your workshop VM

In a terminal inside the browser IDE, switch to this exercise and deploy:

```bash
git switch exercise/notification-investigation
make deploy
```

Open the URL printed at the end. `make deploy` builds the current code, installs it, restarts the app, and checks its page and API. Run it again after each change you want to try. It uses the VM's existing nginx and systemd setup; sudo may prompt for your VM password. The first build downloads checksum-verified Java 21, Node 24 and Maven into your user cache. Later builds reuse them.

All exercises use the same browser URL and one service. Switching branches alone keeps the previous deployment running; run `make deploy` to replace it with the selected exercise. Each exercise stores its own data outside the checkout, so switching exercises preserves your previous work. The app stays running after you close the terminal.

- `make stop` stops the deployed app.
- `make start` starts it again.
- `make status` shows the service status.
- `make logs` follows its log; Ctrl-C stops following.
- `make url` prints the browser URL again.
- `make test` runs backend tests separately from deployment.

## Your task

1. Use AI to investigate why the duplicates occur before changing code. Follow how the agent finds the relevant code: what it searches, which tools it uses, and how it checks its assumptions.
2. Reproduce the issue. Explain the sequence that triggers it and why the implementation produces the failure.
3. Propose a fix and test it. Observe how the agent verifies its own work. Check that a regression test detects the original failure and passes after the repair, and that legitimate notifications still work.
4. Have another agent review the fix in a fresh context. Give it the issue, your evidence, and the changes. Evaluate its findings and address valid concerns.

## Be ready to show

The reproduction, your explanation of the cause, the fix and verification evidence, and the review findings. Explain how the agent discovered the issue and what made its verification convincing.
