package be.kbc.savingstreak.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

/**
 * SQLite creates its database file but not the folder around it. This runs before the
 * DataSource is built and makes sure the folder from the JDBC url exists.
 */
public class SqliteDirectoryPreparer implements BeanFactoryPostProcessor {

    private static final String PREFIX = "jdbc:sqlite:";

    private final Environment environment;

    public SqliteDirectoryPreparer(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        String url = environment.getProperty("spring.datasource.url", "");
        if (!url.startsWith(PREFIX)) {
            return;
        }
        String file = url.substring(PREFIX.length());
        if (file.isBlank() || file.startsWith(":") || file.startsWith("file:")) {
            return; // in-memory or resource based, nothing to create
        }
        Path parent = Paths.get(file).toAbsolutePath().getParent();
        if (parent == null || Files.isDirectory(parent)) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot create the folder for the SQLite database: " + parent, exception);
        }
    }
}
