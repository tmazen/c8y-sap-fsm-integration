package c8y.microservice.sap.fsm.service;


import c8y.microservice.sap.fsm.config.ServiceRequestProperties;
import c8y.microservice.sap.fsm.model.FsmServiceCall;
import c8y.microservice.sap.fsm.model.ServiceRequest;
import c8y.microservice.sap.fsm.model.ServiceRequestResponse;
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionAddedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;


@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceRequestClient {

    @Qualifier("serviceRequestRestTemplate")
    private final RestTemplate restTemplate;

    @Qualifier("serviceRequestByIDRestTemplate")
    private final RestTemplate serviceRequestByIDRestTemplate;

    private final ServiceRequestProperties serviceRequestPropertiesProperties;

    //String SERVICE_REQUEST_BASE_URL = serviceRequestPropertiesProperties.getC8yTenantUrl() + serviceRequestPropertiesProperties.getServiceRequestMsUrl();

    // Cache service request status lookup
    private final Map<String, Integer> statusCache = new ConcurrentHashMap<>();

    // Cache service request priority lookup
    private final Map<String, Integer> priorityCache = new ConcurrentHashMap<>();

    private final FsmConfigService fsmConfigService;

    /**
     * This runs for each tenant that subscribes
     */
    @EventListener
    public void onSubscription(MicroserviceSubscriptionAddedEvent event) {
        refreshStatusCache();
        refreshPriorityCache();
        fsmConfigService.loadSettings();

    }

    public void refreshStatusCache() {
        log.info("Fetching Service Request statuses from API...");
        try {
            String url = "/request/status";

            ResponseEntity<ServiceRequest.Status[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders()),
                    ServiceRequest.Status[].class
            );

            if (response.getBody() != null) {
                statusCache.clear();
                Arrays.stream(response.getBody())
                        .forEach(s -> statusCache.put(s.getName(), s.getId()));
                log.info("Cached {} statuses: {}", statusCache.size(), statusCache);
            }
        } catch (Exception e) {
            log.error("Could not initialize status cache: {}", e.getMessage());
        }
    }

    public void refreshPriorityCache() {
        log.info("Fetching Service Request priorites from API...");
        try {
            String url = "/request/priority";

            ResponseEntity<ServiceRequest.Priority[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders()),
                    ServiceRequest.Priority[].class
            );

            if (response.getBody() != null) {
                priorityCache.clear();
                Arrays.stream(response.getBody())
                        .forEach(s -> priorityCache.put(s.getName(), s.getOrdinal()));
                log.info("Cached {} priorites: {}", priorityCache.size(), priorityCache);
            }
        } catch (Exception e) {
            log.error("Could not initialize priority cache: {}", e.getMessage());
        }
    }

    /**
     * Fetches new service requests Synced or Not depedning on the input paramter
     */
    public List<ServiceRequest> getServiceRequests(boolean hasExternalID) {
        try {
            String url =  "/request/";

            log.info("Fetching new service requests to sync from: {}", url);


            ResponseEntity<ServiceRequestResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    createHttpEntity(),
                    ServiceRequestResponse.class
            );


            // Safely get the list from the body
            ServiceRequestResponse body = response.getBody();
            if (body == null || body.getServiceRequests() == null) {
                return Collections.emptyList();
            }
            List<ServiceRequest> requests = body.getServiceRequests();

            log.info("Retrieved {} service requests to sync",
                    requests != null ? requests.size() : 0);

            // filter requests based theu have hasExternalID
            // New requests will not have hasExternalID
            // Old requsets will have hasExternalID
            List<ServiceRequest> openRequests = requests.stream()
                    //.filter(sr -> !sr.isClosed())
                    .filter(sr -> StringUtils.hasText(sr.getFsmServiceCallId()) == hasExternalID)
                    .toList();


            return openRequests != null ? openRequests : Collections.emptyList();

        } catch (RestClientException e) {
            log.error("Error fetching service requests to sync", e);
            return Collections.emptyList();
        }
    }

    /**
     * Fetches all service requests that are already synced to check for updates
     */
    /*public List<ServiceRequest> getSyncedServiceRequests() {
        try {
            //String url =  "/search?customProperties.fsmSyncStatus=SYNCED&limit=100";
              String url = "/request/";
            log.debug("Fetching synced service requests from: {}", url);

            ResponseEntity<List<ServiceRequest>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    createHttpEntity(),
                    new ParameterizedTypeReference<List<ServiceRequest>>() {}
            );

            List<ServiceRequest> requests = response.getBody();
            log.debug("Retrieved {} synced service requests",
                    requests != null ? requests.size() : 0);

            return requests != null ? requests : Collections.emptyList();

        } catch (RestClientException e) {
            log.error("Error fetching synced service requests", e);
            return Collections.emptyList();
        }
    }*/

    /**
     * Updates a service request with FSM sync information
     */
    public boolean updateServiceRequest(FsmServiceCall updatedFsmServiceCall,
                                        String serviceRequestId,
                                        String fsmSyncStatus,
                                        /*String serviceRequestId,
                                        String fsmServiceCallId,
                                        String fsmSyncStatus,*/
                                        String syncError) {
        try {
            String url =  "/request/" + serviceRequestId;

            Map<String, Object> updatePayload = new HashMap<>();

            if (updatedFsmServiceCall.getId()!= null && !updatedFsmServiceCall.getId().isEmpty()) {
                updatePayload.put("externalId", updatedFsmServiceCall.getId());
            }

            if (fsmSyncStatus != null) {
                Map<String, Object> customProperties = new HashMap<>();
                customProperties.put("fsmSyncStatus", fsmSyncStatus);
                customProperties.put("fsmLastSyncDate", Instant.now().toString()); // ISO-8601 format
                updatePayload.put("customProperties", customProperties);
                if (syncError != null) {
                    updatePayload.put("syncError", syncError);
                }
            }

            if (updatedFsmServiceCall.getStatusName()!= null) {
                Map<String, Object> newStatus = new HashMap<>();
                log.info("updatedFsmServiceCall.getStatusName() is  {}", updatedFsmServiceCall.getStatusName());
                log.info("statusCache.get(updatedFsmServiceCall.getStatusName()) is  {}", statusCache.get(updatedFsmServiceCall.getStatusName()));
                newStatus.put("id", statusCache.get(updatedFsmServiceCall.getStatusName()));
                newStatus.put("name", updatedFsmServiceCall.getStatusName());

                updatePayload.put("status", newStatus);
            }

            if (updatedFsmServiceCall.getPriority()!= null) {
                Map<String, Object> newPriority = new HashMap<>();
                newPriority.put("ordinal", priorityCache.get(updatedFsmServiceCall.getPriority()));
                newPriority.put("name", updatedFsmServiceCall.getPriority());

                updatePayload.put("priority", newPriority);
            }

            if (updatedFsmServiceCall.getSubject() != null && !updatedFsmServiceCall.getSubject().isEmpty()) {
                updatePayload.put("title", updatedFsmServiceCall.getSubject());
            }

            if (updatedFsmServiceCall.getRemarks() != null && !updatedFsmServiceCall.getRemarks().isEmpty()) {
                updatePayload.put("description", updatedFsmServiceCall.getRemarks());
            }

            //log.debug("Updating service request {} with sync status: {}", serviceRequestId, fsmSyncStatus);

            log.info("Sending Partial Update to {}: {}", url, updatePayload);

            // Print to console/logs
            //log.info("Request Body: {}", requestEntity.getBody());

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(updatePayload, createHeaders());

            restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    //new HttpEntity<>(updateRequest, createHeaders()),
                    requestEntity,
                    //ServiceRequest.class
                    Void.class
            );

            //log.info("Successfully updated service request {} sync status to {}",  serviceRequestId, fsmSyncStatus);

            log.info("Successfully updated service request {}",  serviceRequestId);

            return true;

        } catch (RestClientException e) {
            log.error("Error updating service request {}", serviceRequestId, e);
            return false;
        }
    }

    /**
     * Gets a specific service request by ID
     */
    /*public Optional<ServiceRequest> getServiceRequestById(String id) {
        try {
            String url = "/" + id;

            ResponseEntity<ServiceRequest> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    createHttpEntity(),
                    ServiceRequest.class
            );

            return Optional.ofNullable(response.getBody());

        } catch (RestClientException e) {
            log.error("Error fetching service request {}", id, e);
            return Optional.empty();
        }
    }*/

    private HttpEntity<?> createHttpEntity() {
        return new HttpEntity<>(createHeaders());
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        return headers;
    }

    /**
     * Fetches  service requests with ID
     */
    public ServiceRequest getServiceRequestByID(String serviceRequestId) {
        try {
            String url =  "/request/" + serviceRequestId ;

            log.info("Fetching service request with ID {}  from: {}", serviceRequestId, url);


            ResponseEntity<ServiceRequest> response = serviceRequestByIDRestTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    createHttpEntity(),
                    ServiceRequest.class
            );


            // Safely get service request  from the body
            ServiceRequest body = response.getBody();
            if (body == null ) {
                return new ServiceRequest();
            }
            //List<ServiceRequest> requests = body.getServiceRequests();

            log.info("Retrieved service requests is {}", body);

            return body;

        } catch (RestClientException e) {
            log.error("Error fetching service requests to sync", e);
            return new ServiceRequest();
        }
    }

}
