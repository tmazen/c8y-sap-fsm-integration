package c8y.microservice.sap.fsm.config.local;

import c8y.microservice.sap.fsm.service.FsmApiClient;
import c8y.microservice.sap.fsm.service.FsmConfigService;
import c8y.microservice.sap.fsm.service.ServiceRequestClient;
import com.cumulocity.microservice.context.ContextService;
import com.cumulocity.microservice.context.credentials.MicroserviceCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Profile("local")
@Slf4j
public class LocalSubscriptionInitializer {

    private final ServiceRequestClient serviceRequestClient;
    private final FsmConfigService fsmConfigService;
    private final ContextService<MicroserviceCredentials> contextService;

    public LocalSubscriptionInitializer(ServiceRequestClient serviceRequestClient, FsmConfigService fsmConfigService,
                                        @Qualifier("microserviceContextService") ContextService<MicroserviceCredentials> contextService) {
        this.serviceRequestClient = serviceRequestClient; // assign via constructor
        this.fsmConfigService = fsmConfigService;
        this.contextService = contextService;
    }

    @PostConstruct
    public void init() {
        // Directly call the logic your event listener would normally trigger
        String tenantId = System.getenv("C8Y_BOOTSTRAP_TENANT");

        if (tenantId == null) {
            log.error("LOCAL STARTUP FAILED: C8Y_BOOTSTRAP_TENANT environment variable is missing.");
            return;
        }
        // Create credentials for the context
        MicroserviceCredentials credentials = MicroserviceCredentials.builder()
                .tenant(tenantId)
                .username(System.getenv("C8Y_BOOTSTRAP_USER"))
                .password(System.getenv("C8Y_BOOTSTRAP_PASSWORD"))
                .build();

        contextService.runWithinContext(credentials, () -> {
            log.info("Entered local context for tenant: {}", tenantId);
            try {

                serviceRequestClient.refreshStatusCache();
                serviceRequestClient.refreshPriorityCache();

                // This now runs inside a 'faked' context
                fsmConfigService.loadSettingsForSingleTenant(tenantId);
            } catch (Exception e) {
                log.error("Failed to initialize local context: {}", e.getMessage(), e);
            }
        });

    }
}