package c8y.microservice.sap.fsm.service;

import c8y.microservice.sap.fsm.config.FsmProperties;
import c8y.microservice.sap.fsm.model.FsmServiceCall;
import com.cumulocity.microservice.context.ContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import com.cumulocity.microservice.context.credentials.MicroserviceCredentials;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FsmApiClient {

    @Qualifier("fsmRestTemplate")
    private final RestTemplate restTemplate;

    private final FsmConfigService fsmConfigService;
    private final ContextService<MicroserviceCredentials> contextService;

    private final ObjectMapper objectMapper;

    private String cachedAccessToken;
    private Instant tokenExpiryTime;


    /**
     * Helper to get the correct properties for the current tenant context
     */
    private FsmProperties getContextProps() {
        String tenantId = contextService.getContext().getTenant();
        FsmProperties props = fsmConfigService.getCachedSettings(tenantId);

        if (props == null || props.getApiUrl() == null) {
            // Log a warning instead of crashing the thread with an IllegalStateException
            log.warn("FSM Sync skipped: No Tenant Options found for category 'sap.fms' on tenant {}", tenantId);
            return null;
        }
        return props;
    }

    /**
     * Creates a new service call in FSM
     */
    public Optional<String> createServiceCall(FsmServiceCall serviceCall) {
        FsmProperties props = getContextProps();

        if (props == null || props.getApiUrl() == null) {
            // Logged already in getContextProps()
            return Optional.empty();
        }

        try {
            String accessToken = getAccessToken(props);
            if (accessToken == null) {
                log.error("Failed to obtain FSM access token");
                return Optional.empty();
            }

            //String url = fsmProperties.getApiUrl() + "/api/query/v1";
            String url = props.getApiUrl() + "/api/data/v4/ServiceCall/?dtos=ServiceCall." + props.getServiceCallVersion();

            // FSM uses a query-based API
            //String query = buildCreateServiceCallQuery(serviceCall);


            HttpHeaders headers = createFsmHeaders(accessToken);


            //Map<String, Object> requestBody = new HashMap<>();
            //requestBody.put("data", serviceCall);

            log.info("Creating service call in FSM for external ID: {}",
                    serviceCall.getExternalId());

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(serviceCall, headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                String fsmId = extractServiceCallIdFromResponse(response.getBody());
                log.info("Successfully created FSM service call with ID: {}", fsmId);
                return Optional.ofNullable(fsmId);
            }

            log.error("Failed to create service call in FSM. Status: {}",
                    response.getStatusCode());
            return Optional.empty();

        } catch (RestClientException e) {
            log.error("Error creating service call in FSM", e);
            return Optional.empty();
        }
    }

    /**
     * Updates an existing service call in FSM
     */
   /* public boolean updateServiceCall(String fsmServiceCallId, FsmServiceCall serviceCall) {
        try {
            String accessToken = getAccessToken();
            if (accessToken == null) {
                log.error("Failed to obtain FSM access token");
                return false;
            }

            String url = fsmProperties.getApiUrl() + "/api/query/v1";

            String query = buildUpdateServiceCallQuery(fsmServiceCallId, serviceCall);

            HttpHeaders headers = createFsmHeaders(accessToken);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);

            log.info("Updating FSM service call: {}", fsmServiceCallId);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully updated FSM service call: {}", fsmServiceCallId);
                return true;
            }

            log.error("Failed to update service call in FSM. Status: {}",
                    response.getStatusCode());
            return false;

        } catch (RestClientException e) {
            log.error("Error updating service call in FSM", e);
            return false;
        }
    }*/

    /**
     * Retrieves a service call from FSM by ID
     */
    public Optional<FsmServiceCall> getServiceCall(String fsmServiceCallId) {
        FsmProperties props = getContextProps();

        if (props == null || props.getApiUrl() == null) {
            // Logged already in getContextProps()
            return Optional.empty();
        }

        try {
            String accessToken = getAccessToken(props);
            if (accessToken == null) {
                return Optional.empty();
            }

            String url = props.getApiUrl() + "/api/data/v4/ServiceCall/" + fsmServiceCallId
                         + "?dtos=ServiceCall." + props.getServiceCallVersion();  ;

            /*String query = String.format(
                    "query { serviceCall(id: \"%s\") { id subject description status priority } }",
                    fsmServiceCallId
            );*/

            HttpHeaders headers = createFsmHeaders(accessToken);

            //Map<String, Object> requestBody = new HashMap<>();
            //requestBody.put("dtos", "ServiceCall." + fsmProperties.getServiceCallVersion());

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return parseServiceCallFromResponse(response.getBody());
            }

            return Optional.empty();

        } catch (RestClientException e) {
            log.error("Error retrieving service call from FSM", e);
            return Optional.empty();
        }
    }

    /**
     * Obtains an OAuth access token from FSM
     */
    private String getAccessToken(FsmProperties props) {
        // Return cached token if still valid
        String tenantId = contextService.getContext().getTenant();

        if (cachedAccessToken != null && tokenExpiryTime != null &&
                Instant.now().isBefore(tokenExpiryTime)) {
            return cachedAccessToken;
        }

        try {
            String url = props.getApiUrl() + "/api/oauth2/v2/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(props.getClientId(),
                    props.getClientSecret());

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");

            log.debug("Requesting FSM access token");

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenResponse = response.getBody();
                cachedAccessToken = (String) tokenResponse.get("access_token");
                Integer expiresIn = (Integer) tokenResponse.get("expires_in");

                // Set expiry time with 5 minute buffer
                tokenExpiryTime = Instant.now().plusSeconds(expiresIn - 300);

                log.info("Successfully obtained FSM access token");
                return cachedAccessToken;
            }

            log.error("Failed to obtain FSM access token. Status: {}",
                    response.getStatusCode());
            return null;

        } catch (RestClientException e) {
            log.error("Error obtaining FSM access token", e);
            return null;
        }
    }

    private String buildCreateServiceCallQuery(FsmServiceCall serviceCall) {
        return String.format(
                "mutation { createServiceCall(input: { " +
                        "subject: \"%s\", " +
                        "description: \"%s\", " +
                        "status: \"%s\", " +
                        "priority: \"%s\", " +
                        "externalId: \"%s\", " +
                        "origin: \"CUMULOCITY\" " +
                        "}) { id } }",
                escapeGraphQL(serviceCall.getSubject()),
                escapeGraphQL(serviceCall.getRemarks()),
                serviceCall.getStatusName(),
                serviceCall.getPriority(),
                serviceCall.getExternalId()
        );
    }

    private String buildUpdateServiceCallQuery(String id, FsmServiceCall serviceCall) {
        return String.format(
                "mutation { updateServiceCall(id: \"%s\", input: { " +
                        "subject: \"%s\", " +
                        "description: \"%s\", " +
                        "status: \"%s\", " +
                        "priority: \"%s\" " +
                        "}) { id } }",
                id,
                escapeGraphQL(serviceCall.getSubject()),
                escapeGraphQL(serviceCall.getRemarks()),
                serviceCall.getStatusName(),
                serviceCall.getPriority()
        );
    }

    private String extractServiceCallIdFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 1. Get the "data" array
            JsonNode dataArray = root.path("data");

            // 2. Access the first element [0], then "serviceCall", then "id"
            if (dataArray.isArray() && !dataArray.isEmpty()) {
                JsonNode idNode = dataArray.get(0).path("serviceCall").path("id");

                if (!idNode.isMissingNode()) {
                    return idNode.asText();
                }
            }

            log.warn("ID not found in FSM response path data[0].serviceCall.id");
            return null;
        } catch (Exception e) {
            log.error("Error parsing service call ID from response: {}", responseBody, e);
            return null;
        }
    }

    private Optional<FsmServiceCall> parseServiceCallFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            //JsonNode serviceCallNode = root.path("data").path("serviceCall");
            JsonNode serviceCallNode = root.path("data");

            // 1. Navigate to the first element of the "data" array
            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray() || dataArray.isEmpty()) {
                log.warn("FSM response 'data' array is empty or missing");
                return Optional.empty();
            }

            // 2. Access the "serviceCall" object inside the first array element
            JsonNode scNode = dataArray.get(0).path("serviceCall");
            if (scNode.isMissingNode()) {
                log.warn("Node 'serviceCall' not found in the first data element");
                return Optional.empty();
            }

            // 3. Map the fields exactly as they appear in your JSON
            FsmServiceCall serviceCall = FsmServiceCall.builder()
                    .id(scNode.path("id").asText())
                    .subject(scNode.path("subject").asText())
                    // In your JSON, "remarks" is used instead of "description"
                    .remarks(scNode.path("remarks").asText(null))
                    .statusName(scNode.path("statusName").asText())
                    .statusCode(scNode.path("statusCode").asText())
                    .priority(scNode.path("priority").asText())
                    .externalId(scNode.path("externalId").asText(null))
                    .build();

            log.debug("Retrivied Service Call is {}", serviceCall);
            return Optional.of(serviceCall);

        } catch (Exception e) {
            log.error("Error parsing service call from response", e);
            return Optional.empty();
        }
    }

    private String escapeGraphQL(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private HttpHeaders createFsmHeaders(String accessToken) {
        FsmProperties props = getContextProps();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);


        headers.set("X-Account-Id", props.getHeaderAccountId());
        headers.set("X-Company-Id", props.getHeaderCompanyId());
        headers.set("X-Client-Id", props.getHeaderClientId());
        headers.set("X-Client-Version", props.getHeaderClientVersion());

        return headers;
    }


}

