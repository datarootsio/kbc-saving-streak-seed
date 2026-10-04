# Exercise 4: Numeric investigation

Use AI to turn a user's request into a small, useful improvement in the data-cleaning app.

## Make numeric columns easier to investigate

[Issue #5](https://github.com/datarootsio/kbc-saving-streak-seed/issues/5) asks for a quicker way to understand numeric values and inspect relevant rows. Clarify the workflow and missing requirements before choosing a feature. Use `sample-data/inconsistent-behavior.csv` to ground the conversation in examples.

**Objective:** clarify an ambiguous request, align with the agent on testable behavior, and deliver a feature in small verified slices.

1. Interview the user. Ask which values need attention, what they need to inspect, and how the feature should interact with existing filters. Record answers and assumptions.
2. Agree a small scope. Have the agent explain the proposed behavior through examples, including ambiguous values and edge cases. Correct misunderstandings before coding.
3. Save the agreement as a specification with acceptance criteria. Break it into slices with observable outcomes and implement one slice at a time.
4. Verify each slice with relevant backend tests and browser checks. Demonstrate the agreed behavior and confirm that existing filtering still works.

**Be ready to show:** the questions you asked, agreed behavior, acceptance criteria, task breakdown, working feature, verification evidence, and tradeoffs.

## Starting point and scope

The four bugs from the debugging exercise are repaired, and the removed original browser tests are restored. The new numeric-investigation feature is still to build. The broader workspace redesign pictured in the original ticket is outside this exercise.

## Working agreement

Create your own working branch from `exercise/numeric-investigation`. Define a small scope and acceptance criteria before implementation. Preserve existing authorship and license notices. Bring your evidence and remaining questions to the debrief; different implementations can satisfy the same agreed behavior.
