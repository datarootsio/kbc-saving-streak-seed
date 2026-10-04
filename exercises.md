# Data cleaning debug

Investigate and repair the inconsistent behavior reported in the data-cleaning app.

## Investigate inconsistent behavior

[Issue #4](https://github.com/datarootsio/kbc-saving-streak-seed/issues/4) reports confusing cell values, numeric filtering, text search, and project selection. Use `sample-data/inconsistent-behavior.csv` to explore the report.

**Objective:** investigate unfamiliar code with an agent, test competing explanations, and verify repairs with meaningful regression coverage.

1. Read the report and ask for clarification. Record observations separately from hypotheses, including the report's proposed cause.
2. Reproduce each reported behavior. Check the stored or returned values as well as what the interface displays.
3. Trace the causes before editing. Use experiments to distinguish competing explanations.
4. Fix the issues and add regression tests that fail before the repair and pass afterward. Verify that nearby behavior still works.

**Be ready to show:** the reproductions, evidence supporting your diagnosis, focused changes, regression results, and remaining uncertainties.

## Working agreement

Create your own working branch from `exercise/data-cleaning-debug`. Preserve existing authorship and license notices. Bring your evidence and remaining questions to the debrief; different repairs can satisfy the same expected behavior.
