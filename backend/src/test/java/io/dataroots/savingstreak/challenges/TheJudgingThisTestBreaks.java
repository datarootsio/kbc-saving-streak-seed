package io.dataroots.savingstreak.challenges;

import java.util.concurrent.atomic.AtomicLong;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;

/**
 * One customer whose judging throws, brought by the test that needs one.
 *
 * <p>The nightly sweep promises that a customer it cannot judge costs the bank one customer's
 * badges and not the night's, and that promise cannot be tested against a customer who can be
 * judged. Nothing a test can do through the endpoints arranges a genuine failure: every refusal the
 * module has is a refusal rather than a fault, and the one case the judging pass already treats as
 * damaged — an enrolment in a challenge the bank no longer offers — it warns about and steps over
 * rather than throwing. So the fault is brought, the way the scheduled jobs a test needs are
 * brought in {@code JobsThisTestDefines}.
 *
 * <p><strong>It wraps the service rather than replacing it.</strong> Everything the application does
 * is the real thing: the real judging pass judges every other customer, through the real sweep, and
 * the only difference is that one nominated customer's pass throws on its way in. A hand-written
 * stand-in would have to be kept in step with a service this feature is still growing, and the test
 * would then be asserting against the stand-in.
 *
 * <p><strong>Nothing about this is in the production code.</strong> The application has no hook for
 * making a customer unjudgeable and must not grow one; a proxy put round a bean in a test's own
 * application is the whole of the arrangement, and it exists in that one application alone.
 *
 * <p>Guarded twice, like the jobs a test brings: {@link TestConfiguration} keeps it out of the
 * component scan of the contexts Spring Boot's test support builds, and the profile keeps it out of
 * every application that did not ask for it. An application in this run with a working judging pass
 * must keep one.
 */
@TestConfiguration(proxyBeanMethods = false)
@Profile(TheJudgingThisTestBreaks.PROFILE)
public class TheJudgingThisTestBreaks implements BeanPostProcessor {

    /** Named on the command line by the test that wants a broken judging pass, and by nobody else. */
    public static final String PROFILE = "judging-that-breaks";

    /** No customer, which is what this is until a test nominates one — so an application that has
     * this class in it but never says whom judges everybody exactly as it always did. */
    private static final long NOBODY = Long.MIN_VALUE;

    private final AtomicLong theOneThatCannotBeJudged = new AtomicLong(NOBODY);

    /**
     * Nominates the customer whose judging throws from now on, by identifier, because that is what
     * the sweep judges by.
     */
    public void nothingCanJudge(long customerId) {
        theOneThatCannotBeJudged.set(customerId);
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof ChallengesService)) {
            return bean;
        }
        ProxyFactory wrapping = new ProxyFactory(bean);
        // Round the class, because the sweep and the controllers hold the service by its own type
        // and not by an interface.
        wrapping.setProxyTargetClass(true);
        wrapping.addAdvice((MethodInterceptor) call -> {
            if (call.getMethod().getName().equals("judge") && call.getArguments().length > 0
                    && call.getArguments()[0] instanceof Long customerId
                    && customerId == theOneThatCannotBeJudged.get()) {
                throw new IllegalStateException(
                        "this customer's judging pass was written to fall over, customerId="
                                + customerId);
            }
            return call.proceed();
        });
        return wrapping.getProxy();
    }
}
