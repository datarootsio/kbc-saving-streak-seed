# Add snooze to the reminder app

Work in pairs to add a snooze feature to the simple reminder app using AI.

**Goal:** practise alignment, slicing tasks, and managing the agent's context window.

## Deploy on your workshop VM

In a terminal inside the browser IDE, switch to this exercise and deploy:

```bash
git switch exercise/reminder-app
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

1. Agree on the behavior together before coding. Resolve at least these ambiguities:
   - Does snooze postpone the reminder's due date or only its notification?
   - How long is the snooze, and who chooses the duration?
   - What happens to recurring reminders and their next occurrence?
2. Align with the agent. Have it explain the agreed behavior through examples, and correct assumptions that do not match your decisions.
3. Save the agreement and slice the feature into small tasks with observable outcomes.
4. Implement and verify one slice at a time. For one slice, start a fresh agent session using your saved agreement and tasks. Check what context it needs and whether it preserves your decisions.

## Be ready to show

Your agreed behavior, task breakdown, and working snooze feature. Explain how you checked alignment, chose the slices, and preserved the context needed for a fresh session.
