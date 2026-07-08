package org.asvosonk.infrastructure.configuration;

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardManager;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Disables Tomcat's persistence of HTTP sessions to disk (SESSIONS.ser).
 *
 * By default the embedded Tomcat serializes active sessions on shutdown and
 * reloads them on the next startup. After a code change / devtools restart the
 * serialized session objects reference classes whose structure changed, so the
 * reload fails with:
 *   java.lang.ClassCastException: ... ObjectStreamClass cannot be cast to String
 *   (StandardManager: "Exception loading sessions from persistent storage")
 *
 * Setting the manager pathname to null turns off both the save-on-stop and the
 * load-on-start, which removes the error permanently. Sessions simply do not
 * survive an application restart — acceptable for this app (users just log in
 * again).
 */
@Configuration
public class WebServerConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> disableTomcatSessionPersistence() {
        return factory -> factory.addContextCustomizers(context -> {
            Manager manager = context.getManager();
            if (manager == null) {
                manager = new StandardManager();
                context.setManager(manager);
            }
            if (manager instanceof StandardManager standardManager) {
                // null pathname => Tomcat neither saves nor reloads SESSIONS.ser
                standardManager.setPathname(null);
            }
        });
    }
}
