# Data cleaning debug

A local browser app for investigating and repairing the four bugs reported in [issue #1](https://github.com/datarootsio/data-cleaning-workshop/issues/1). Start with [exercises.md](exercises.md).

## Prerequisites

- JDK 21–26; set `JAVA_HOME` to a supported JDK.
- Apache Maven on `PATH`.
- Node.js 24+ and npm 11.16.0+.
- GNU Make (the macOS system `make` is also supported).

## Start locally

```sh
git switch exercise/data-cleaning-debug
make build
make run
```

Open <http://127.0.0.1:3333>. Stop the server with Ctrl+C. Projects are saved in `data/data-cleaning`, which is ignored by git. Override the defaults with `make run PORT=3334 DATA_DIR=/path/to/disposable-data`.

## Verification

```sh
make test          # Java tests
make server-test   # Application tests
```

Browser tests live in `main/tests/cypress`. See [the Cypress README](main/tests/cypress/Readme.md) for dependency setup and targeted execution. Start with tests relevant to the behavior you change. `make lint` applies the existing Java formatting rules; review its diff before committing.

## Source and scope

The participant code comes from [`datarootsio/data-cleaning-workshop`](https://github.com/datarootsio/data-cleaning-workshop), revision `db446ba1fc5c6f49f2c8a230d6407ecc32f424be`. Application behavior and existing tests are preserved. The build includes the core model, expression language, web application and local server. Optional database, Jython, PC-Axis and Wikibase extensions, distribution assembly and benchmarks are excluded.

This workshop is derived from OpenRefine, originally created by Metaweb Technologies and conceived by David Huynh, with work by its many contributors. Existing source credits and license notices are retained. See [LICENSE.txt](LICENSE.txt), [third-party notices](packaging/THIRD-PARTY.txt), and the notices in `main/webapp/licenses`.
