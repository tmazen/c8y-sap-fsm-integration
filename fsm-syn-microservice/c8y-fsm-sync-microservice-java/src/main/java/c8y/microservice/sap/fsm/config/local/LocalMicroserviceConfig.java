package c8y.microservice.sap.fsm.config.local;
import com.cumulocity.microservice.context.ContextService; // Import this
import com.cumulocity.microservice.context.credentials.MicroserviceCredentials;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Callable;

@Configuration
@Profile("local")
public class LocalMicroserviceConfig {

    @Bean
    @Primary
    public MicroserviceSubscriptionsService subscriptionsService(
            @Qualifier("microserviceContextService") ContextService<MicroserviceCredentials> contextService) { // Inject contextService

        return new MicroserviceSubscriptionsService() {

            private MicroserviceCredentials getLocalCredentials() {
                return MicroserviceCredentials.builder()
                        .tenant(System.getenv("C8Y_BOOTSTRAP_TENANT"))
                        .username(System.getenv("C8Y_BOOTSTRAP_USER"))
                        .password(System.getenv("C8Y_BOOTSTRAP_PASSWORD"))
                        .build();
            }

            @Override
            public String getTenant() {
                // Return the actual ID so code calling getTenant() doesn't get ""
                return System.getenv("C8Y_BOOTSTRAP_TENANT");
            }

            @Override
            public void runForEachTenant(Runnable runnable) {
                // FIX: Wrap the runnable in the manual context
                contextService.runWithinContext(getLocalCredentials(), runnable);
            }

            @Override
            public void runForTenant(String tenant, Runnable runnable) {
                // FIX: Wrap the runnable in the manual context
                contextService.runWithinContext(getLocalCredentials(), runnable);
            }

            @Override
            public <T> T callForTenant(String tenant, Callable<T> callable) {
                // FIX: Wrap the callable in the manual context
                return contextService.callWithinContext(getLocalCredentials(), callable);
            }

            // --- Remaining methods stay mostly the same ---
            @Override
            public Collection<MicroserviceCredentials> getAll() {
                return java.util.Collections.singletonList(getLocalCredentials());
            }

            @Override
            public Optional<MicroserviceCredentials> getCredentials(String tenant) {
                return Optional.of(getLocalCredentials());
            }

            @Override
            public boolean isRegisteredSuccessfully() {
                return true;
            }

            @Override
            public void subscribe() {}
        };
    }
}