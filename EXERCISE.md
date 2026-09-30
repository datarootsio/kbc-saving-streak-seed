# The specification swap

**The request:** “Add a snooze feature to our reminder app.”

Work in pairs. One person is the product owner; the other directs the coding agent. The product owner decides the desired behavior before the interview and keeps those decisions on a separate card.

1. Run the starter and try a one-off reminder and a daily reminder.
2. Have the agent help you interview the product owner. Resolve the behavior through examples before coding.
3. Save the agreed behavior, scope, unresolved questions, and acceptance examples in a short specification.
4. Divide the request into small, independently verifiable tasks. Record any dependencies.
5. Swap the specification with another pair. They start a fresh agent session and implement one slice using the saved artifacts.
6. Demonstrate that slice to the original product owner. Compare its behavior with the agreed examples.

The product owner can decide what snoozing changes, how the duration is chosen, what happens to recurring reminders, and what should survive a refresh. Use specific times to expose assumptions: a daily 09:00 reminder snoozed at 09:05 is a good starting example.

The starter uses in-page due indicators. The pair should explicitly agree whether that remains the notification mechanism for their slice.

**Learning material:** resolving ambiguities and alignment, slicing requests into tasks, and context window management. Verification is practiced when the receiving pair demonstrates the agreed behavior.

**Done when:** another pair can implement and demonstrate one agreed slice using the saved specification and repository, without access to the original conversation.
