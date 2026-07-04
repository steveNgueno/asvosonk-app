package org.asvosonk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ASVOSONK — Application entry point.
 *
 * The application starts an embedded Tomcat server bound to
 * 127.0.0.1:8080 (see application.properties).
 * Access it via http://localhost:8080 in any browser on this machine.
 */
@SpringBootApplication
@EnableScheduling   // enables @Scheduled in BackupScheduler
public class AsvosonkApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsvosonkApplication.class, args);
    }
}
