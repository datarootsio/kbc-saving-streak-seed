package be.kbc.savingstreak.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class AppConfig {

    @Bean
    public static BeanFactoryPostProcessor sqliteDirectoryPreparer(Environment environment) {
        return new SqliteDirectoryPreparer(environment);
    }

    /**
     * Seeds the demo data on first boot. It lives here rather than on the seeder itself so the
     * call goes through the transactional proxy instead of self-invoking it.
     */
    @Bean
    public ApplicationRunner demoDataRunner(DemoDataSeeder seeder) {
        return args -> seeder.seedIfEmpty();
    }

    @Bean
    public ZoneId zoneId() {
        return ZoneId.of("Europe/Brussels");
    }

    @Bean
    public Clock clock(ZoneId zoneId) {
        return Clock.system(zoneId);
    }
}
