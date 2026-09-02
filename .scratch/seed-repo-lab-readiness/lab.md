### Checks

    cd backend && ./mvnw test
    cd frontend && npm run typecheck

When a backend test fails, `backend/target/surefire-reports/<test class>.txt` holds the
assertion text and the full stack trace for that class; the console summary does not.

### Running the application

Spring Boot API on http://localhost:8080, Vite page on http://localhost:5173. Vite needs
Node 20.19+; if `node -v` is older, put the newest `~/.nvm/versions/node/v2*/bin` first on
PATH. Start it on a throwaway database so you do not spend the trainer's demo data, with
the app's own package, the web layer and Hibernate's SQL at DEBUG:

    cd backend && SAVING_STREAK_DB=$(mktemp -d)/s.db ./mvnw -q spring-boot:run \
      -Dspring-boot.run.arguments="--logging.level.io.dataroots.savingstreak=DEBUG --logging.level.org.springframework.web=DEBUG --logging.level.org.hibernate.SQL=DEBUG" \
      > <prefix>.backend.log 2>&1 &
    cd frontend && npm run dev > <prefix>.frontend.log 2>&1 &

`<prefix>.backend.log` then has every request served, the SQL it ran and the stack trace
behind any 500; `<prefix>.frontend.log` is where Vite's build and transform errors show
up, and nowhere else. The database is seeded with the demo customers, so deposit,
withdraw, claim and change anything you like.

### Exercising it

A ticket that changes the page: drive it with Playwright (python3, sync API, chromium;
check with `python3 -c "import playwright"`). Before navigating, subscribe and write to
`<prefix>.browser.log`:

    page.on("console", lambda m: log(f"[console:{m.type}] {m.text}"))
    page.on("pageerror", lambda e: log(f"[pageerror] {e}"))
    page.on("requestfailed", lambda r: log(f"[requestfailed] {r.url} {r.failure}"))

A backend-only ticket: exercise it over HTTP against the running API with curl.

### Rules this repository holds work to

Logging is part of the feature, not an extra (CLAUDE.md). A reviewer reads
`io.dataroots.savingstreak` at DEBUG in the backend log to see what the app actually
did, so silent code is unreviewable and is sent back.

- SLF4J (`LoggerFactory.getLogger(Thing.class)`), never `System.out`.
- INFO: one line per business event with the values that decided it. WARN: every
  refusal with its reason. DEBUG: the inputs behind a decision. ERROR: unexpected
  failures, with the exception.
- Log the reason, not only the fact: "rejected deposit: amount 0" beats "deposit failed".
- Greppable, in the style of the surrounding code:
  `log.info("deposit accepted customerId={} cents={} points={}", ...)`.
- No secrets, no full request bodies, nothing inside a tight loop.

Reviewer: exercise the new behaviour, then grep the backend log for it. A flow with no
line from `io.dataroots.savingstreak`, or a refusal you triggered that left no line
saying why, is a missing acceptance criterion.
