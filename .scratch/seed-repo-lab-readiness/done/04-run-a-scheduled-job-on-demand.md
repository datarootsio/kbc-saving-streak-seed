# 04: Run a scheduled job on demand

**What to build:** Scheduling is switched on in the application, so that a participant who writes a
scheduled job during an exercise sees it run at all rather than sitting in silence wondering why
nothing happened. On top of that, any scheduled job can be run immediately by name instead of waiting
for its schedule to come round. The application ships no jobs of its own — points expiry and the
loyalty bonus are exercises — so this delivers the machinery and the guarantee that a job added later
is reachable.

**Blocked by:** 03 (Advance the clock in the development profile).

Status: done

- [x] Scheduling is enabled, so a job annotated as scheduled anywhere in the application actually runs
- [x] A scheduled job can be run immediately by name while the development profile is active
- [x] The jobs available to run can be listed, so nobody has to guess a name
- [x] Naming a job that does not exist is refused with a reason saying so
- [x] Verified against a job defined in test scope, since the application deliberately ships none
- [x] Neither control exists when the development profile is not active

## Verified

Reviewed `ticket/03-advance-the-clock-in-the-development-profile..ticket/04-run-a-scheduled-job-on-demand`
(2 commits, 16 files) and drove the running application. Everything below was seen, not inferred.

**Checks.** `cd backend && ./mvnw test` → BUILD SUCCESS, 99 tests, 0 failures, 0 errors (18 suites,
`RunningAScheduledJobApiTest` 7 and `JobsCannotBeRunOutsideDevelopmentApiTest` 4). The two new classes
also pass on their own (`-Dtest=RunningAScheduledJobApiTest,JobsCannotBeRunOutsideDevelopmentApiTest`),
so they are not order-dependent on the rest of the run. `cd frontend && npm run typecheck` clean on
Node v24.16.0.

**The shipped application ships no jobs, and says so.** Against the orchestrator's instance on :8080
the startup log reads `i.d.savingstreak.jobs.ScheduledJobs : scheduling is on jobs=0 names=[]`;
`GET /api/dev/jobs` → `200 []` with `scheduled jobs listed count=0 names=[]`; `POST
/api/dev/jobs/expirePoints/run` → 400 `"There is no scheduled job called \"expirePoints\". This
application has no scheduled jobs in it at all …"`. So the empty list is a working feature, not an
absent one — but it cannot on its own prove a job runs.

**So it was exercised against real jobs.** A throwaway `reviewjobs` package (never added to this
repository) with three `@Scheduled` methods on `ReviewerJobs` — `heartbeat` (fixedRate 1000),
`expireOldPoints` (cron `0 0 3 1 1 *`), `aJobThatBreaks` (fixedDelay 90 minutes) — plus a second class
`ClashingJobs` also defining `expireOldPoints`, was compiled against `target/classes` and started on
:8081 with `--spring.profiles.active=dev --spring.main.sources=reviewjobs.ReviewerJobs,reviewjobs.ClashingJobs`.
Log kept at `logs/04-run-a-scheduled-job-on-demand.app.1.reviewer-demojob.backend.log`.

- *Scheduling is on.* `REVIEWER heartbeat n=1 … n=38` fired with nobody asking, from thread
  `[scheduling-1]`. Startup line: `scheduling is on jobs=4 names=[expireOldPoints, aJobThatBreaks,
  expireOldPoints, heartbeat]`, followed by a DEBUG line per job with its schedule.
- *Listing.* `GET /api/dev/jobs` → 200 with all four, each carrying `name`, `definedBy` and the
  schedule in the author's own words — `"cron 0 0 3 1 1 *"`, `"every 1000 milliseconds"`,
  `"90 minutes after the last run"` — sorted by `Class.method`.
- *Running by name.* `POST /api/dev/jobs/heartbeat/run` → 200
  `{"name":"heartbeat","definedBy":"ReviewerJobs","ranAt":"2026-09-02T12:00:11.429461Z","tookMillis":0}`,
  log `INFO … job run on demand name=heartbeat definedBy=ReviewerJobs schedule=every 1000 milliseconds
  ranAt=… tookMillis=0`. The answer comes back after the job has finished, not as a promise.
- *Running by the long name.* `POST /api/dev/jobs/ReviewerJobs.expireOldPoints/run` → 200, and the
  job's own log line `REVIEWER expireOldPoints ran` appears on the request thread `[nio-8081-exec-3]`.
- *Unknown name refused with a reason.* `POST /api/dev/jobs/noSuchJob/run` → 400 problem document,
  `detail` = `"There is no scheduled job called \"noSuchJob\". The jobs that can be run are: […]"`,
  with `WARN … job not run: There is no scheduled job called "noSuchJob" …` in the log.
- *Ambiguous name refused with the way out.* With two classes defining `expireOldPoints`,
  `POST /api/dev/jobs/expireOldPoints/run` → 400 `"More than one scheduled job is called
  \"expireOldPoints\". Name the one you mean: [ClashingJobs.expireOldPoints,
  ReviewerJobs.expireOldPoints]."`, and a matching WARN.
- *A job that throws.* `POST /api/dev/jobs/aJobThatBreaks/run` → 500 `"The job \"aJobThatBreaks\" was
  run and threw IllegalStateException: reviewer made this one break"`, with `ERROR … job threw
  name=aJobThatBreaks definedBy=ReviewerJobs ranAt=…` and the stack trace beside it.
- *The clock and the job together — the point of the slice.* `POST /api/dev/clock/advance {"days":400}`
  → `{"movedForwardByDays":400,"now":"2027-10-07T…"}`; the next
  `POST /api/dev/jobs/ReviewerJobs.expireOldPoints/run` answered `ranAt":"2027-10-07T12:00:20.715672Z"`
  and the job's own injected `Clock` read the same: `REVIEWER expireOldPoints ran, clock reads
  2027-10-07T12:00:20.715683Z`. The ticking `heartbeat` moved with it too (`n=36` at 2026-09-02,
  `n=37` at 2027-10-07).

**Neither control exists outside development.** The same two classes started on :8082 with no `dev`
profile (`logs/04-run-a-scheduled-job-on-demand.app.1.reviewer-nodev.backend.log`): `GET /api/dev/jobs`
→ 404, `POST /api/dev/jobs/heartbeat/run` → 404, `GET /api/dev/clock` → 404, while `GET /api/customers`
→ 200 and `REVIEWER heartbeat` still fired 19 times. Scheduling ships everywhere; running a job out of
turn does not. `DevelopmentJobsController` and `ScheduledJobs` are both `@Profile("dev")`;
`SchedulingIsOn` deliberately is not.

**Logging meets the repository's rules.** SLF4J throughout, no `System.out` or `printStackTrace` in the
diff. INFO one line per business event with the values that decided it, WARN on every refusal carrying
the whole reason, DEBUG for the inputs (`job asked for by name name=… jobsAnswering=…
jobsInTheApplication=[…]`), ERROR with the exception. Nothing logged inside the job loop.

**Conventions.** `DevelopmentJobsController` mirrors `DevelopmentClockController` (same `/api/dev`
prefix, `@Profile("dev")`, package-private class, constructor injection, response records with `of`);
`JobRefused` mirrors `ClockRefused` exactly, carrying no status code, with the status decided in
`RefusalsAsHttp` as a problem document with the reason in `detail`. Test package `runningajob` is
feature-named like the rest, and the test jobs are guarded both by `@TestConfiguration` and by a
`jobs-under-test` profile so no other application in the run finds one ticking. No frontend change,
which is right — the ticket is backend-only and the lab controls stay off the page. A Playwright pass
over http://localhost:5173 confirmed no regression: the styled sign-in page renders, `body` has 301
characters of real content, and the browser log holds only Vite's connect messages and the React
DevTools notice — no `pageerror`, no `requestfailed`.

Nothing was found missing. Merging this branch is safe.

### Noted, not blocking

`/code-review` over the same range raised five points. None fails an acceptance criterion and none is
reachable by anything the seed or its four exercises do, so they are recorded here rather than sent
back — worth knowing if a future slice leans harder on job discovery.

1. `ScheduledJobs.whatIsThere()` resolves the defining class from the *declared* bean type
   (`getType` + `ClassUtils.getUserClass`), which unwraps CGLIB but not a JDK dynamic proxy, and takes
   a `@Bean` method's declared return type at its word. A job behind an interface-based proxy, or
   returned from `@Bean Runnable expiryJob()`, is scheduled by Spring and does run, but would not be
   listed or runnable by name. `AopProxyUtils.ultimateTargetClass(bean)` — what Spring's own
   `ScheduledAnnotationBeanPostProcessor` uses — would close it.
2. `byLongName.putIfAbsent(...)` keys on `SimpleClassName.methodName`, so two instances of the same
   job class, two overloads of one name, or two same-simple-name classes in different packages would
   collide and only the first would be listed.
3. `thrown.getMessage()` at `ScheduledJobs:97` is interpolated unguarded, so a job throwing with no
   message answers `… threw IllegalStateException: null`.
4. `application.getBean(beanName)` inside `whatIsThere()` can throw `BeansException` for a `@Lazy`
   bean that fails to construct; that would escape unmapped by `RefusalsAsHttp` as a bare 500. The
   javadoc's claim that listing does not build beans holds only for beans with no scheduled method.
5. `a_job_run_after_the_clock_moved_runs_on_the_moved_clock` advances the class-scoped application's
   clock 400 days and never puts it back. Harmless today — nothing else in the class asserts on time —
   but a future time-sensitive test in this class would inherit it depending on method order.
