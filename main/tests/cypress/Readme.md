# Workshop UI tests

Use Node.js 24+ and npm 11.16.0+. The direct Cypress workflow below works without a global Yarn installation.
From the repository root, install the test dependencies and browser binary:

```bash
cd main/tests/cypress
npm install --package-lock=false
npx cypress install
cd ../../..
```

Build with `./refine build` and stop any application already using port 3333.
Then start a dedicated test server in one terminal:

```bash
./refine -d "${TMPDIR:-/tmp}/data-cleaning-workshop-ui-tests" -p 3333
```

The tests create and delete projects, so use this separate test data directory.
In another terminal, run Cypress directly from the repository root:

```bash
cd main/tests/cypress
npx cypress run --browser electron --headless --env OPENREFINE_URL=http://127.0.0.1:3333
```

This preserves Cypress's exit status. Add `--spec 'cypress/e2e/open-project/*.cy.js'` to select a group of specs,
or use `--browser chrome` for an installed Chrome browser. Stop the test server with Ctrl+C when finished.

## Repository launcher

If using the repository's pinned Yarn 4.18.0, `./refine e2e_tests` starts its own server and invokes Cypress.
Stop any development server first. Set `CYPRESS_SPECS` to paths relative to `main/tests/cypress` to select tests:

```bash
CYPRESS_SPECS='cypress/e2e/open-project/*.cy.js' ./refine e2e_tests
```

The launcher can mask Cypress's failing exit status. Inspect the Cypress report, or use the direct invocation above when
you need the command status to reflect test failures. Do not install the legacy global Yarn 1 package for this repository.

Specs live in `cypress/e2e`; reusable helpers are in `cypress/support`.
Use small deterministic fixtures and assert user-visible behavior. Report actual failures, including server or browser
startup failures, when sharing results.

See [CONTRIBUTING.md](../../../CONTRIBUTING.md) for the workshop workflow.
