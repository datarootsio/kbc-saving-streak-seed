package io.dataroots.savingstreak.jobs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * The scheduled jobs this application has in it: what they are called, and a way to run one now
 * instead of when its schedule says.
 *
 * <p>A lab affordance, not a feature: it exists in the development profile alone, so an application
 * started without that profile has scheduling switched on and no way for anybody to reach past it.
 * A demonstration of a twelve-month rule that waited on a cron expression would not be one, and this
 * is how a trainer skips the wait.
 *
 * <p>The jobs are found by looking at the application rather than read off a list somebody
 * maintains. A list would be a second place to add a job to, and the job a participant adds during
 * an exercise would be missing from exactly the tool that exists to run it.
 */
@Service
@Profile("dev")
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    /**
     * The application itself, asked what is in it at the moment of the question rather than once at
     * startup: a job can be defined on a bean that was itself created late, and an answer cached
     * before it existed would be missing it.
     */
    private final ApplicationContext application;

    private final Clock clock;

    ScheduledJobs(ApplicationContext application, Clock clock) {
        this.application = application;
        this.clock = clock;
    }

    /** The jobs there are to run, so that nobody has to guess a name. Sorted, because a list read by a person is. */
    public List<AScheduledJob> whatCanBeRun() {
        List<AScheduledJob> jobs = whatIsThere().stream().map(AJob::describe).toList();
        log.debug("scheduled jobs listed count={} names={}", jobs.size(), namesOf(jobs));
        return jobs;
    }

    /**
     * Runs a job now, on the calling thread, and reports what happened.
     *
     * <p>On the calling thread on purpose: the answer comes back after the job has finished, so that
     * whoever asked can look at what it did rather than at a promise that it would be done shortly.
     * These jobs are run one at a time by a person with curl, so there is nothing to be gained by
     * handing the work to a pool and quite a lot to be lost.
     *
     * @throws JobRefused if no job answers to that name, or more than one does
     * @throws JobFailed  if the job itself threw
     */
    public AJobThatRan runNow(String name) {
        List<AJob> jobs = whatIsThere();
        List<AJob> answering = jobs.stream().filter(job -> job.answersTo(name)).toList();
        log.debug("job asked for by name name={} jobsAnswering={} jobsInTheApplication={}",
                name, answering.size(), longNamesOf(jobs));
        AJob job = theOneJobOrRefuse(name, answering, jobs);

        Instant ranAt = clock.instant();
        long startedAt = System.nanoTime();
        try {
            job.run();
        } catch (RuntimeException | Error thrown) {
            // Logged here with the exception, because the caller is handed a sentence and the stack
            // trace is the half of the answer that says where in the job it went wrong.
            log.error("job threw name={} definedBy={} ranAt={}", job.name(), job.definedBy(), ranAt, thrown);
            throw new JobFailed("The job \"" + job.name() + "\" was run and threw "
                    + thrown.getClass().getSimpleName() + ": " + thrown.getMessage(), thrown);
        }
        long tookMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        log.info("job run on demand name={} definedBy={} schedule={} ranAt={} tookMillis={}",
                job.name(), job.definedBy(), job.schedule(), ranAt, tookMillis);
        return new AJobThatRan(job.name(), job.definedBy(), ranAt, tookMillis);
    }

    /**
     * Says what scheduling found, once the application is up.
     *
     * <p>The first question a participant whose job did nothing asks is whether it was scheduled at
     * all, and this line answers it without them having to call anything. An application with no jobs
     * says so too — that is the seed's normal state, and a reader who sees the line knows the
     * machinery is there and empty rather than absent.
     */
    @EventListener(ApplicationStartedEvent.class)
    void sayWhichJobsAreScheduled() {
        List<AScheduledJob> jobs = whatIsThere().stream().map(AJob::describe).toList();
        log.info("scheduling is on jobs={} names={}", jobs.size(), namesOf(jobs));
        for (AScheduledJob job : jobs) {
            log.debug("scheduled job name={} definedBy={} schedule={}",
                    job.name(), job.definedBy(), job.schedule());
        }
    }

    private AJob theOneJobOrRefuse(String name, List<AJob> answering, List<AJob> jobs) {
        if (answering.isEmpty()) {
            throw refusing("There is no scheduled job called \"" + name + "\". " + whatThereIsInstead(jobs));
        }
        if (answering.size() > 1) {
            // Two jobs whose methods share a name. Both are still reachable, by the longer name that
            // says which class each is on, so the way out is in the sentence rather than in the docs.
            throw refusing("More than one scheduled job is called \"" + name
                    + "\". Name the one you mean: " + longNamesOf(answering) + ".");
        }
        return answering.get(0);
    }

    private String whatThereIsInstead(List<AJob> jobs) {
        if (jobs.isEmpty()) {
            return "This application has no scheduled jobs in it at all — it ships none of its own,"
                    + " and a job appears here as soon as a method in it is annotated @Scheduled.";
        }
        return "The jobs that can be run are: " + namesOf(jobs.stream().map(AJob::describe).toList()) + ".";
    }

    /** Every refusal says why in the log as well as to the caller, because only one of them is kept. */
    private JobRefused refusing(String reason) {
        log.warn("job not run: {}", reason);
        return new JobRefused(reason);
    }

    /**
     * Every scheduled method in the application, named and described.
     *
     * <p>Read off the {@link Scheduled} annotations rather than out of Spring's own registry of
     * scheduled tasks. The registry knows what it is running but keeps each job wrapped in a runnable
     * that does not say which method it came from, and a name guessed from a wrapper's toString would
     * be a name that changed with the framework. The annotations are what a participant wrote, so
     * they are what this reads.
     *
     * <p>Types are asked before beans are, so that a bean nobody has needed yet is not built merely
     * because somebody listed the jobs. Keyed by the long name so that a job cannot be listed twice.
     */
    private List<AJob> whatIsThere() {
        Map<String, AJob> byLongName = new LinkedHashMap<>();
        for (String beanName : application.getBeanNamesForType(Object.class, false, false)) {
            Class<?> type = application.getType(beanName, false);
            if (type == null) {
                continue;
            }
            Class<?> defining = ClassUtils.getUserClass(type);
            Map<Method, Set<Scheduled>> scheduledMethods = MethodIntrospector.selectMethods(defining,
                    (MethodIntrospector.MetadataLookup<Set<Scheduled>>) method -> {
                        Set<Scheduled> found = AnnotatedElementUtils.getMergedRepeatableAnnotations(
                                method, Scheduled.class, Schedules.class);
                        return found.isEmpty() ? null : found;
                    });
            if (scheduledMethods.isEmpty()) {
                continue;
            }
            Object bean = application.getBean(beanName);
            scheduledMethods.forEach((method, schedules) -> {
                AJob job = new AJob(method.getName(), defining.getSimpleName(), scheduleOf(schedules), bean, method);
                byLongName.putIfAbsent(job.longName(), job);
            });
        }
        List<AJob> jobs = new ArrayList<>(byLongName.values());
        jobs.sort(Comparator.comparing(AJob::longName));
        return jobs;
    }

    /**
     * When the job would have run of its own accord, in the terms its author wrote it in — the cron
     * expression as typed, rather than a next-run moment. Somebody reading a list of jobs is deciding
     * which one they mean, and the line they wrote is what they will recognise.
     */
    private static String scheduleOf(Set<Scheduled> schedules) {
        return schedules.stream().map(ScheduledJobs::scheduleOf).toList().stream()
                .reduce((one, another) -> one + ", and " + another)
                .orElse("no schedule at all");
    }

    private static String scheduleOf(Scheduled scheduled) {
        if (!scheduled.cron().isEmpty()) {
            return "cron " + scheduled.cron();
        }
        if (!scheduled.fixedRateString().isEmpty()) {
            return "every " + scheduled.fixedRateString();
        }
        if (scheduled.fixedRate() >= 0) {
            return "every " + scheduled.fixedRate() + " " + unitOf(scheduled);
        }
        if (!scheduled.fixedDelayString().isEmpty()) {
            return scheduled.fixedDelayString() + " after the last run";
        }
        if (scheduled.fixedDelay() >= 0) {
            return scheduled.fixedDelay() + " " + unitOf(scheduled) + " after the last run";
        }
        // A schedule given some way this method has not been taught to read. Saying so is honest and
        // costs nothing; the job is still there and still runs on demand under its name.
        return "on a schedule this application cannot put into words";
    }

    private static String unitOf(Scheduled scheduled) {
        return scheduled.timeUnit().name().toLowerCase(Locale.ROOT);
    }

    private static String longNamesOf(List<AJob> jobs) {
        return jobs.stream().map(AJob::longName).toList().toString();
    }

    private static String namesOf(List<AScheduledJob> jobs) {
        return jobs.stream().map(AScheduledJob::name).toList().toString();
    }

    /**
     * One scheduled job, with the means to run it. Kept inside this class because a method that
     * anybody can call out of turn is the one thing here worth keeping out of reach.
     */
    private record AJob(String name, String definedBy, String schedule, Object bean, Method method) {

        /**
         * The name that always means this job and no other. Two beans can have a method of the same
         * name, and then the short name is ambiguous while this one is not.
         */
        String longName() {
            return definedBy + "." + name;
        }

        boolean answersTo(String asked) {
            return name.equals(asked) || longName().equals(asked);
        }

        /**
         * Calls the method, on this thread, the way the scheduler would have called it — through the
         * bean rather than around it, so that a job wrapped in a transaction or a proxy is run with
         * its wrapping on and does on demand exactly what it would have done on its schedule.
         */
        void run() {
            Method invocable = AopUtils.selectInvocableMethod(method, bean.getClass());
            ReflectionUtils.makeAccessible(invocable);
            try {
                invocable.invoke(bean);
            } catch (InvocationTargetException thrownByTheJob) {
                // The job's own failure, not a failure to call it: the wrapper reflection put round it
                // is unwrapped here so that what the caller and the log see is what the job threw.
                Throwable cause = thrownByTheJob.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(cause);
            } catch (IllegalAccessException cannotCallIt) {
                throw new IllegalStateException("could not call the job " + longName(), cannotCallIt);
            }
        }

        AScheduledJob describe() {
            return new AScheduledJob(name, definedBy, schedule);
        }
    }
}
