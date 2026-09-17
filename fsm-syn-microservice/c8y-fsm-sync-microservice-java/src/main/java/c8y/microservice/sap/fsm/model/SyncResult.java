package c8y.microservice.sap.fsm.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class SyncResult {
    private LocalDateTime syncStartTime;
    private LocalDateTime syncEndTime;
    private int totalProcessed;
    private int successCount;
    private int failureCount;
    private int skippedCount;

    @Builder.Default
    private List<SyncError> errors = new ArrayList<>();

    @Data
    @Builder
    public static class SyncError {
        private String serviceRequestId;
        private String errorMessage;
        private String errorType;
    }
}

