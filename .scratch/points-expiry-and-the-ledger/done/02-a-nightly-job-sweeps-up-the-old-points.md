# 02: A nightly job sweeps up the old points, and a trainer can run it on demand

**What to build:** The application's first scheduled job of its own. It runs at 03:00 every day, asks
the Points module to sweep, and reads the application's clock rather than the machine's — so a trainer
who winds the clock a year forward and runs the job sees the year the job thought it was running in.

It is reachable by name through the development jobs endpoint, which is the whole reason that endpoint
exists. Until now the application shipped no jobs and the list was empty; the test that asserted that
emptiness says something different now, and says why.

Blocked by: 01

Status: done

- [x] A job appears in `GET /api/dev/jobs` with a name, the class that defines it, and its cron expression as written.
- [x] `POST /api/dev/jobs/{name}/run` runs it once, on the request thread, and answers after it has finished.
- [x] The moment the job judges anniversaries against comes from the application's clock, so winding the clock forward a year and running the job expires a year-old batch.
- [x] The job is present in every profile; only the ability to run it out of turn is development-only.
- [x] `JobsCannotBeRunOutsideDevelopmentApiTest` no longer asserts that the application ships no jobs, and says what it asserts instead.

## Verified

- `GET /api/dev/jobs` → `[{"name":"expireOldPoints","definedBy":"OldPointsExpireNightly","schedule":"cron 0 0 3 * * *"}]`,
  and the startup line reads `scheduling is on jobs=1 names=[expireOldPoints]`.
- `POST /api/dev/jobs/expireOldPoints/run` answered `{"name":"expireOldPoints","definedBy":"OldPointsExpireNightly","ranAt":"2027-09-22T…","tookMillis":3}`
  — after the sweep had finished, with `ranAt` the moment the **wound-forward** clock reads rather
  than the machine's. A job that read `Instant.now()` would have found nothing to do and said so
  convincingly; `TheExpirySweepIsAJobThatCanBeRunOnDemandApiTest` asserts that moment is within five
  minutes of the clock endpoint's own reading.
- **Present outside the development profile**, which is where the distinction lives:
  `JobsCannotBeRunOutsideDevelopmentApiTest` now asserts the bean is registered in an application
  started without `dev`, while the two endpoints that would run it early are still 404 there. The
  bean is asked for by name because the class is package-private to the points module, which is
  where it belongs.
- Three comments that said the application ships no jobs of its own were true when they were written
  and are not now: `SchedulingIsOn`, and two in `ScheduledJobs` (the startup line's rationale and
  the "there are none at all" refusal sentence, which is no longer reachable in the shipped
  application and now says why it is kept).
