package c8y.microservice.sap.fsm.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class ServiceRequestResponse {
    // This variable name MUST match the key in the JSON response
    // If the JSON key is "serviceRequests", name this "serviceRequests"
    @JsonProperty("list")
    private List<ServiceRequest> serviceRequests;

    // Cumulocity APIs usually include these; adding them prevents mapping errors
    private Object statistics;
    private String next;
    private String prev;
}
