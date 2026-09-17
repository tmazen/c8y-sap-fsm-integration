package c8y.microservice.sap.fsm.service;

import c8y.microservice.sap.fsm.config.FsmProperties;
import c8y.microservice.sap.fsm.model.FsmServiceCall;
import c8y.microservice.sap.fsm.model.ServiceRequest;
import c8y.microservice.sap.fsm.model.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncOrchestrationService {

    private final ServiceRequestClient serviceRequestClient;
    private final FsmApiClient fsmApiClient;
    private final DataMappingService dataMappingService;
    private final FsmProperties fsmProperties;

    /**
     * Main synchronization orchestration method
     */
    public SyncResult performSync() {
        log.info("=== Starting FSM Sync Process ===");

        LocalDateTime syncStartTime = LocalDateTime.now();
        SyncResult.SyncResultBuilder resultBuilder = SyncResult.builder()
                .syncStartTime(syncStartTime);

        AtomicInteger totalProcessed = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        try {
            // Step 1: Sync new/failed service requests to FSM
            syncNewServiceRequests(resultBuilder, totalProcessed, successCount,
                    failureCount, skippedCount);

            // Step 2: Sync updates from FSM back to service requests
            syncUpdatesFromFsm(resultBuilder, totalProcessed, successCount,
                    failureCount, skippedCount);

        } catch (Exception e) {
            log.error("Unexpected error during sync process", e);
            resultBuilder.errors(List.of(SyncResult.SyncError.builder()
                    .serviceRequestId("SYSTEM")
                    .errorMessage(e.getMessage())
                    .errorType("SYSTEM_ERROR")
                    .build()));
        }

        LocalDateTime syncEndTime = LocalDateTime.now();
        SyncResult result = resultBuilder
                .syncEndTime(syncEndTime)
                .totalProcessed(totalProcessed.get())
                .successCount(successCount.get())
                .failureCount(failureCount.get())
                .skippedCount(skippedCount.get())
                .build();

        log.info("=== FSM Sync Completed: {} processed, {} succeeded, {} failed, {} skipped ===",
                result.getTotalProcessed(), result.getSuccessCount(),
                result.getFailureCount(), result.getSkippedCount());

        return result;
    }

    /**
     * Syncs new service requests from Cumulocity to FSM
     */
    private void syncNewServiceRequests(SyncResult.SyncResultBuilder resultBuilder,
                                        AtomicInteger totalProcessed,
                                        AtomicInteger successCount,
                                        AtomicInteger failureCount,
                                        AtomicInteger skippedCount) {

        log.info("Fetching service requests to sync to FSM...");
        List<ServiceRequest> serviceRequests = serviceRequestClient.getServiceRequests(false);

        if (serviceRequests.isEmpty()) {
            log.info("No service requests to sync to FSM");
            return;
        }

        log.info("Found {} service requests to sync to FSM", serviceRequests.size());

        for (ServiceRequest serviceRequest : serviceRequests) {
            totalProcessed.incrementAndGet();

            try {
                log.debug("Processing service request: {}", serviceRequest.getId());

                // Map to FSM service call
                FsmServiceCall fsmServiceCall = dataMappingService.mapToFsmServiceCall(serviceRequest);

                //log.debug("Description in service call is : {}", fsmServiceCall.getRemarks());

                // Create in FSM
                Optional<String> fsmIdOpt;
            /*    if (serviceRequest.getFsmServiceCallId() != null) {
                    // Update existing
                    boolean updated = fsmApiClient.updateServiceCall(
                            serviceRequest.getFsmServiceCallId(), fsmServiceCall);

                    if (updated) {
                        fsmIdOpt = Optional.of(serviceRequest.getFsmServiceCallId());
                    } else {
                        fsmIdOpt = Optional.empty();
                    }
                } else {
                    // Create new
                    fsmIdOpt = fsmApiClient.createServiceCall(fsmServiceCall);
                }*/

                // Create new
                fsmIdOpt = fsmApiClient.createServiceCall(fsmServiceCall);

                log.info("fsmIdOpt is : ", fsmIdOpt.isPresent());

                if (fsmIdOpt.isPresent()) {
                    // Update service request with FSM ID and sync status
                    FsmServiceCall updatedFsmServiceCall = FsmServiceCall.builder().build();

                    updatedFsmServiceCall.setId(fsmIdOpt.get());
                    boolean updated = serviceRequestClient.updateServiceRequest(updatedFsmServiceCall,
                            serviceRequest.getId() ,"SYNCED", null);

                    if (updated) {
                        successCount.incrementAndGet();
                        log.info("Successfully synced service request {} to FSM (FSM ID: {})",
                                serviceRequest.getId(), fsmIdOpt.get());
                    } else {
                        failureCount.incrementAndGet();
                        log.error("Created FSM service call but failed to update service request {}",
                                serviceRequest.getId());
                    }
                } else {
                    // Failed to create/update in FSM
                    failureCount.incrementAndGet();
                    String errorMsg = "Failed to create/update service call in FSM";

                    /*serviceRequestClient.updateServiceRequest(
                            serviceRequest.getId(),
                            null,
                            "SYNC_FAILED",
                            errorMsg
                    );*/
                    FsmServiceCall updatedFsmServiceCall = FsmServiceCall.builder().build();
                    updatedFsmServiceCall.setId(fsmIdOpt.get());
                    serviceRequestClient.updateServiceRequest(updatedFsmServiceCall,
                            serviceRequest.getId(), "SYNC_FAILED", null);

                    resultBuilder.errors(List.of(SyncResult.SyncError.builder()
                            .serviceRequestId(serviceRequest.getId())
                            .errorMessage(errorMsg)
                            .errorType("FSM_API_ERROR")
                            .build()));

                    log.error("Failed to sync service request {} to FSM",
                            serviceRequest.getId());
                }

            } catch (Exception e) {
                failureCount.incrementAndGet();
                String errorMsg = "Exception during sync: " + e.getMessage();

                log.error("Error syncing service request {}", serviceRequest.getId(), e);


                /*serviceRequestClient.updateServiceRequest(
                        serviceRequest.getId(),
                        null,
                        "SYNC_FAILED",
                        errorMsg
                );*/
                FsmServiceCall updatedFsmServiceCall = FsmServiceCall.builder().build();
                //updatedFsmServiceCall.setId(fsmIdOpt.get());
                serviceRequestClient.updateServiceRequest(updatedFsmServiceCall,
                        serviceRequest.getId(), "SYNC_FAILED", errorMsg);


                resultBuilder.errors(List.of(SyncResult.SyncError.builder()
                        .serviceRequestId(serviceRequest.getId())
                        .errorMessage(errorMsg)
                        .errorType("EXCEPTION")
                        .build()));
            }
        }
    }

    /**
     * Syncs status updates from FSM back to Cumulocity service requests
     */
    private void syncUpdatesFromFsm(SyncResult.SyncResultBuilder resultBuilder,
                                    AtomicInteger totalProcessed,
                                    AtomicInteger successCount,
                                    AtomicInteger failureCount,
                                    AtomicInteger skippedCount) {

        log.info("Fetching synced service requests to check for FSM updates...");
        List<ServiceRequest> syncedRequests = serviceRequestClient.getServiceRequests(true);

        if (syncedRequests.isEmpty()) {
            log.info("No synced service requests to check for updates");
            return;
        }

        log.info("Checking {} synced service requests for FSM updates", syncedRequests.size());

        boolean thereIsChange = false;

        for (ServiceRequest serviceRequest : syncedRequests) {
            if (serviceRequest.getFsmServiceCallId() == null) {
                skippedCount.incrementAndGet();
                continue;
            }

            totalProcessed.incrementAndGet();

            try {
                // Fetch current state from FSM
                Optional<FsmServiceCall> fsmServiceCallOpt =
                        fsmApiClient.getServiceCall(serviceRequest.getFsmServiceCallId());

                //ServiceRequest updaytedServiceRequest = new ServiceRequest();
                FsmServiceCall updatedFsmServiceCall = FsmServiceCall.builder().build();

                //log.debug("fsmServiceCallOpt.isPresent() is  {}",
                //        fsmServiceCallOpt.isPresent());

                if (fsmServiceCallOpt.isPresent()) {
                    FsmServiceCall fsmServiceCall = fsmServiceCallOpt.get();

                    // Check if status changed in FSM
                    String fsmStatus = fsmServiceCall.getStatusName();

                    String serviceRequesttStatusName = (serviceRequest.getStatus() != null)
                            ? serviceRequest.getStatus().getName()
                            : "";
                    if (!fsmStatus.equals(serviceRequesttStatusName)) {
                        log.info("Status changed in FSM for service request {}: {} -> {}",
                                serviceRequest.getId(), serviceRequesttStatusName, fsmStatus);
                        updatedFsmServiceCall.setStatusName(fsmStatus);
                        thereIsChange = true;
                    }

                    // Check if priority changed in FSM
                    String fsmPriority = fsmServiceCall.getPriority();

                    String serviceRequestPriority = (serviceRequest.getPriority() != null)
                            ? serviceRequest.getPriority().getName()
                            : "";
                    if (!fsmPriority.equals(serviceRequestPriority)) {
                        log.info("Priority changed in FSM for service request {}: {} -> {}",
                                serviceRequest.getId(), serviceRequestPriority, fsmPriority);
                        updatedFsmServiceCall.setPriority(fsmPriority);
                        thereIsChange = true;
                    }

                    // Check if subject changed in FSM
                    String fsmSubject = fsmServiceCall.getSubject();

                    String serviceRequestTitle = (serviceRequest.getTitle() != null)
                            ? serviceRequest.getTitle()
                            : "";
                    if (!fsmSubject.equals(serviceRequestTitle)) {
                        log.info("Subject changed in FSM for service request {}: {} -> {}",
                                serviceRequest.getId(), serviceRequestTitle, fsmSubject);
                        updatedFsmServiceCall.setSubject(fsmSubject);
                        thereIsChange = true;
                    }

                    // Check if remarks changed in FSM
                    String fsmRemarks = fsmServiceCall.getRemarks();

                    String serviceRequestDescription = (serviceRequest.getDescription() != null)
                            ? serviceRequest.getDescription()
                            : "";
                    if (!fsmRemarks.equals(serviceRequestDescription)) {
                        log.info("Remarks changed in FSM for service request {}: {} -> {}",
                                serviceRequest.getId(), serviceRequestDescription, fsmRemarks);
                        updatedFsmServiceCall.setRemarks(fsmRemarks);
                        thereIsChange = true;
                    }

                    if (thereIsChange) {
                        log.info("thereIsChange is  {}", thereIsChange);

                        // Update service request for status, priority, title and description

                        //boolean updated = serviceRequestClient.updateServiceRequest(updaytedServiceRequest, "SYNC_FAILED");

                        //serviceRequestClient.updateServiceRequest(updatedFsmServiceCall,
                        //       serviceRequest.getId(), "SYNC_FAILED", null);
                        boolean updated = serviceRequestClient.updateServiceRequest(updatedFsmServiceCall,
                                serviceRequest.getId(), null, null);

                        if (updated) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } else {
                        thereIsChange = false;
                        skippedCount.incrementAndGet();
                    }
                } else {
                    log.warn("Could not fetch FSM service call {} for service request {}",
                            serviceRequest.getFsmServiceCallId(), serviceRequest.getId());
                    thereIsChange = false;
                    skippedCount.incrementAndGet();
                }

            } catch (Exception e) {
                thereIsChange = false;
                failureCount.incrementAndGet();
                log.error("Error checking FSM updates for service request {}",
                        serviceRequest.getId(), e);
            }
        } // end of for
    }
}
