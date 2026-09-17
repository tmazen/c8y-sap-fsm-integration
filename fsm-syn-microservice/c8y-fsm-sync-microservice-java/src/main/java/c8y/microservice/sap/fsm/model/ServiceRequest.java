package c8y.microservice.sap.fsm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.Map;

//@Data
@Getter // Using explicit Getter/Setter instead of @Data
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class ServiceRequest {
    private String id;
    private String title;
    private String description;

    //private String status; // NEW, IN_PROGRESS, COMPLETED, CANCELLED


    //private String priority; // LOW, MEDIUM, HIGH, CRITICAL
    private String category;
    private String subcategory;

    private boolean isOpened;
    private boolean isClosed;

    private Status status;
    private Priority priority;

    @JsonProperty("externalId")
    private String fsmServiceCallId; // FSM Service Call ID

    //@JsonProperty("customProperties.fsmLastSyncDate")
    //private LocalDateTime fsmLastSyncDate;

    //@JsonProperty("customProperties.fsmSyncStatus")
    //private String fsmSyncStatus; // NOT_SYNCED, SYNCING, SYNCED, SYNC_FAILED


    @JsonProperty("source")
    private Source source;

    private String sourceID;

    public String getSourceID() {
        return source != null ? source.getId() : null;
    }

    @JsonProperty("syncError")
    private String syncError;

    private Map<String, Object> customFields;

    //@Data
    //@JsonIgnoreProperties(ignoreUnknown = true)
    @Getter @Setter @ToString
    public static class Status {
        private int id;
        private String name;
    }

    //@Data
    //@JsonIgnoreProperties(ignoreUnknown = true)
    @Getter @Setter @ToString
    public static class Priority {
        private int ordinal;
        private String name;
    }

    public Status getStatus() {
        return this.status;
    }

    public Priority getPriority() {
        return this.priority;
    }

    private CustomProperties customProperties;

    @Getter @Setter @ToString
    public static class CustomProperties {
        private String fsmLastSyncDate; // Using String for ISO format is safer
        private String fsmSyncStatus;
    }

    @Getter @Setter @ToString
    public static class Source {
        private String id;
    }
}

