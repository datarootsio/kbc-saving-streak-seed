package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.jobs.ScheduledJobs;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The application's scheduled jobs: which ones there are, and a way to run one now.
 *
 * <p>A lab affordance rather than part of the product, and it exists in the development profile
 * alone. Without that profile this controller is not built, so the paths below are not routes and an
 * application in front of a customer has no way to make a job fire out of turn. Scheduling itself is
 * on everywhere — a job that only ran when somebody asked would not be a scheduled job.
 *
 * <p>Under {@code /api/dev} beside the clock, so that what is a demonstration aid and what is the
 * application is legible from the path alone. Nothing in the frontend calls either of these; they are
 * a trainer's or a participant's tools, reached with curl, and the pair of them is the whole
 * demonstration: wind the clock a year forward, then run the job that cares about the year.
 */
@RestController
@RequestMapping("/api/dev/jobs")
@Profile("dev")
class DevelopmentJobsController {

    private final ScheduledJobs jobs;

    DevelopmentJobsController(ScheduledJobs jobs) {
        this.jobs = jobs;
    }

    /** What there is to run, so that nobody has to guess a name or go reading for one. */
    @GetMapping
    List<ScheduledJobResponse> jobs() {
        return jobs.whatCanBeRun().stream().map(ScheduledJobResponse::of).toList();
    }

    /**
     * Runs the named job and answers once it has finished.
     *
     * <p>Whether there is such a job is a question for the jobs themselves, which refuse on their own
     * and say what there is instead; this method takes the name off the path and nothing else.
     */
    @PostMapping("/{name}/run")
    JobRunResponse run(@PathVariable String name) {
        return JobRunResponse.of(jobs.runNow(name));
    }
}
