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

## Deploy on your workshop VM

In a terminal inside the browser IDE, switch to this exercise and deploy:

```bash
git switch exercise/data-cleaning-debug
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

After deployment, import `sample-data/inconsistent-behavior.csv` through **Create project → This Computer**. On the preview screen, enable **Attempt to parse cell text into numbers**, then choose **Create project**. Projects are kept separately for this exercise.
