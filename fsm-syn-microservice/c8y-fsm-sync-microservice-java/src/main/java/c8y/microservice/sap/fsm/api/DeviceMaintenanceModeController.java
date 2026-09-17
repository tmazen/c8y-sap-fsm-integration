package c8y.microservice.sap.fsm.api;

import c8y.microservice.sap.fsm.model.DeviceMaintenanceModeRequest;
import c8y.microservice.sap.fsm.model.ServiceRequest;
import c8y.microservice.sap.fsm.service.ServiceRequestClient;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.identity.ExternalIDRepresentation;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/maintenanceMode")
@RequiredArgsConstructor
public class DeviceMaintenanceModeController {

    private final InventoryApi inventoryApi;

    private final ServiceRequestClient serviceRequestClient;

    /**
     * Updates the maintenance mode of a device associated with a Service Request.
     * URL: POST /service/sap-fsm-sync/maintenance/updateMaintenanceMode
     */
    @PostMapping(value ="/updateMaintenanceMode", consumes = "application/json")
    public ResponseEntity<String> updateDeviceMaintenanceMode(
            @RequestBody DeviceMaintenanceModeRequest deviceMaintenanceModeRequest) {

        log.info("Updating maintenance Mode for device has  SR: {} to mode: {}", deviceMaintenanceModeRequest.getServiceRequestId(), deviceMaintenanceModeRequest.getMaintenanceMode());

        try {
            // 1. Identify the Managed Object
            //Find which MO has this serviceRequestId.
            log.info("Fetching service requests to get device ID...");
            ServiceRequest serviceRequest = serviceRequestClient.getServiceRequestByID(deviceMaintenanceModeRequest.getServiceRequestId());

            log.info("Retrieved Device Id for Service Request is {}", serviceRequest.getSourceID());

            //2. Get Monaged Object
            ManagedObjectRepresentation device = null;
            device = inventoryApi.get(GId.asGId(serviceRequest.getSourceID()));

            // 3.Get Availbility Frgament
            Map<String, Object> availabilityFragment = (Map<String, Object>)device.get("c8y_RequiredAvailability");
            Long responseInterval = (Long)availabilityFragment.get("responseInterval");
            //Integer newResponseInterval = responseInterval;

            if ( (responseInterval <= 0 && deviceMaintenanceModeRequest.getMaintenanceMode().equals("Off")) ||
                    (responseInterval > 0 && deviceMaintenanceModeRequest.getMaintenanceMode().equals("On")) ) {
                  // update responseInterval
                responseInterval = -1 * responseInterval;
                availabilityFragment.put("responseInterval", responseInterval);

                // 3. Set the maintenance fragment
                ManagedObjectRepresentation updateDevice = new ManagedObjectRepresentation();
                updateDevice.setId(device.getId());

                Map<String, Object> c8yRequiredAvailability = new HashMap<>();
                c8yRequiredAvailability.put("responseInterval", responseInterval);
                updateDevice.setProperty("c8y_RequiredAvailability", c8yRequiredAvailability);
                inventoryApi.update(updateDevice);

                return ResponseEntity.ok("Device Maintenance Mode updated to " + deviceMaintenanceModeRequest.getMaintenanceMode());

            } else {
                log.info("No need to update Maintenance Mode.....");
                return ResponseEntity.ok("No Need to update Device Maintenance Mode " );
            }

        } catch (Exception e) {
            log.error("Failed to update maintenance for SR: {}", deviceMaintenanceModeRequest.getServiceRequestId(), e);
            return ResponseEntity.status(500).body("Error updating device: " + e.getMessage());
        }
    }
}
