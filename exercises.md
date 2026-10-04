# Data cleaning: investigation and feature delivery

Work through these tickets in order in the data-cleaning app.

## 1. Investigate inconsistent behavior

[Issue #1](https://github.com/datarootsio/data-cleaning-workshop/issues/1) reports confusing cell values, numeric filtering, text search, and project selection. Use `sample-data/inconsistent-behavior.csv` to explore the report.

**Objective:** investigate unfamiliar code with an agent, test competing explanations, and verify repairs with meaningful regression coverage.

1. Read the report and ask for clarification. Record observations separately from hypotheses, including the report's proposed cause.
2. Reproduce each reported behavior. Check the stored or returned values as well as what the interface displays.
3. Trace the causes before editing. Use experiments to distinguish competing explanations.
4. Fix the issues and add regression tests that fail before the repair and pass afterward. Verify that nearby behavior still works.

**Be ready to show:** the reproductions, evidence supporting your diagnosis, focused changes, regression results, and remaining uncertainties.

## 2. Make numeric columns easier to investigate

[Issue #2](https://github.com/datarootsio/data-cleaning-workshop/issues/2) asks for a quicker way to understand numeric values and inspect the relevant rows. Discuss the user's workflow with the facilitator before choosing a feature.

**Objective:** align with the user and agent, turn an ambiguous request into an agreed contract, and deliver small verified slices.

1. Ask what the user needs to investigate, which values matter, and how the change should interact with existing filters. Record answers and assumptions.
2. Agree a small feature scope with the facilitator. Ask the agent to explain the behavior using examples and correct misunderstandings.
3. Save the agreement, define acceptance criteria, and divide the work into slices with observable outcomes.
4. Implement and verify each slice with behavioral tests and a working browser demonstration.

The broader workspace redesign pictured in issue #2 is outside this exercise's scope.

**Be ready to show:** the questions you asked, agreed behavior, task breakdown, working feature, test evidence, and tradeoffs.

## Working agreement

Create your own working branch from `exercise/data-cleaning`. Complete the investigation before starting the feature. Preserve existing authorship and license notices. Bring your evidence and remaining questions to the debrief; different implementations can satisfy the same agreed behavior.
