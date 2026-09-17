package c8y.microservice.sap.fsm.model;
import lombok.Data;

@Data
public class DeviceMaintenanceModeRequest {
    private String serviceRequestId;
    private String maintenanceMode;
}
