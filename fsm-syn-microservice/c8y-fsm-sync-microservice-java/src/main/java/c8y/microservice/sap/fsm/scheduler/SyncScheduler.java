package c8y.microservice.sap.fsm.scheduler;

import c8y.microservice.sap.fsm.config.FsmProperties;
import c8y.microservice.sap.fsm.model.SyncResult;
import c8y.microservice.sap.fsm.service.SyncOrchestrationService;
import com.cumulocity.microservice.context.ContextService;
import com.cumulocity.microservice.context.credentials.MicroserviceCredentials;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class SyncScheduler {

    private final SyncOrchestrationService syncOrchestrationService;
    private final FsmProperties fsmProperties;

    private final MicroserviceSubscriptionsService subscriptionsService;

    @Qualifier("microserviceContextService")
    private final ContextService<MicroserviceCredentials> contextService;

    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private volatile boolean initialized = false;
    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("FSM Sync Microservice started successfully");
        log.info("Sync enabled: {}", fsmProperties.getSync().isEnabled());
        log.info("Sync interval: {} minutes", fsmProperties.getSync().getIntervalMinutes());
        initialized = true;
    }

    /**
     * Scheduled sync job - runs every 5 minutes
     */
    @Scheduled(fixedDelayString = "#{${fsm.sync.interval-minutes:2} * 60000}", initialDelayString = "10000") // 10 second initial delay
    public void scheduledSync() {
        if (!initialized ) {
            log.debug("Microservice not fully initialized yet, skipping sync");
            return;
        }

        if (!fsmProperties.getSync().isEnabled()) {
            log.debug("Sync is disabled, skipping");
            return;
        }

        // Prevent concurrent executions
        if (!syncInProgress.compareAndSet(false, true)) {
            log.warn("Previous sync still in progress, skipping this execution");
            return;
        }


        try {
            log.info("=== Starting Multi-tenant Sync Cycle ===");

            // Check if we are running in the 'local' profile
            boolean isLocal = Arrays.asList(environment.getActiveProfiles()).contains("local");

            if(!isLocal) {
                runLocalSync();
            } else {
                subscriptionsService.runForEachTenant(() -> {
                    String currentTenant = subscriptionsService.getTenant();
                    try {
                        log.info("Processing sync for tenant: {}", currentTenant);
                        SyncResult result = syncOrchestrationService.performSync();
                        logSummary(result);
                    } catch (Exception e) {
                        log.error("Sync failed for tenant: {}", currentTenant, e);
                    }
                });
            }

        } finally {
            // 2. Unlock globally once ALL tenants are finished
            syncInProgress.set(false);
        }
    }

    private void runLocalSync() {
        String localTenant = System.getenv("C8Y_BOOTSTRAP_TENANT");
        if (localTenant == null || localTenant.isEmpty()) {
            log.error("C8Y_BOOTSTRAP_TENANT env var is missing. Local sync cannot proceed.");
            return;
        }

        MicroserviceCredentials credentials = MicroserviceCredentials.builder()
                .tenant(localTenant)
                .username(System.getenv("C8Y_BOOTSTRAP_USER"))
                .password(System.getenv("C8Y_BOOTSTRAP_PASSWORD"))
                .build();

        // Establish context for the background scheduler thread
        contextService.runWithinContext(credentials, () -> {
            runSyncWithSummary(localTenant);
        });
    }

    private void runSyncWithSummary(String tenantId) {
        try {
            log.info("Processing sync for tenant: {}", tenantId);
            SyncResult result = syncOrchestrationService.performSync();
            logSummary(result);
        } catch (Exception e) {
            log.error("Sync failed for tenant: {}", tenantId, e);
        }
    }

    /**
     * Manual trigger for sync (can be called via REST endpoint)
     */
    public SyncResult triggerManualSync() {
        // 1. Check if we are even allowed to sync
        if (!fsmProperties.getSync().isEnabled()) {
            throw new IllegalStateException("Sync is currently disabled");
        }

        // 2. Try to grab the lock ONCE
        if (!syncInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("Sync already in progress");
        }

        try {
            log.info("=== Manual Sync Triggered for Tenant: {} ===", subscriptionsService.getTenant());
            // 3. Just run the sync. The SDK already has the tenant context from the API call.
            return syncOrchestrationService.performSync();
        } finally {
            // 4. Always release the lock
            syncInProgress.set(false);
        }
    }

    private void logSummary(SyncResult result) {
        log.info("Sync completed - Processed: {}, Success: {}, Failed: {}, Skipped: {}",
                result.getTotalProcessed(), result.getSuccessCount(),
                result.getFailureCount(), result.getSkippedCount());

        if (!result.getErrors().isEmpty()) {
            result.getErrors().forEach(error ->
                    log.warn("  - SR {}: {}", error.getServiceRequestId(), error.getErrorMessage()));
        }
    }
}
