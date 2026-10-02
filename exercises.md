# Add snooze to the reminder app

Work in pairs to add a snooze feature to the simple reminder app using AI.

**Goal:** practise alignment, slicing tasks, and managing the agent's context window.

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

## Run

Requires Java 17+. Run `./mvnw spring-boot:run` and open <http://localhost:8080>. Tests: `./mvnw test`.

The starter supports one-off, daily, weekly (chosen weekday), and monthly (chosen day) reminders, editing, completion, deletion, and persistence. Shorter months use their last day without changing the chosen monthly day. Notifications appear as in-page due highlights. Restart the app after frontend edits.
