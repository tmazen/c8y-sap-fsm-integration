package c8y.microservice.sap.fsm.service;

import c8y.microservice.sap.fsm.config.FsmProperties;
import c8y.microservice.sap.fsm.model.FsmServiceCall;
import c8y.microservice.sap.fsm.model.ServiceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataMappingService {

    private final FsmProperties fsmProperties;

    /**
     * Maps Cumulocity Service Request to FSM Service Call
     */
    public FsmServiceCall mapToFsmServiceCall(ServiceRequest serviceRequest) {
        log.debug("Mapping service request {} to FSM service call", serviceRequest.getId());

        String currentStatusName = (serviceRequest.getStatus() != null)
                ? serviceRequest.getStatus().getName()
                : "";

        int currentStatusId = (serviceRequest.getStatus() != null)
                ? serviceRequest.getStatus().getId()
                : -1000;

        String currentPriorityName = (serviceRequest.getPriority() != null)
                ? serviceRequest.getPriority().getName()
                : "";

        FsmServiceCall.FsmServiceCallBuilder builder = FsmServiceCall.builder()
                .subject(serviceRequest.getTitle())
                .remarks(StringUtils.hasText(serviceRequest.getDescription())
                        ? serviceRequest.getDescription()
                        : serviceRequest.getTitle())
                //.statusName(mapStatusToFsm(currentStatusName))
                .statusName(currentStatusName)
                //.statusCode(mapStatusToFsm(currentStatusId))
                .statusCode(Integer.toString(currentStatusId))
                //.priority(mapPriorityToFsm(currentPriorityName))
                .priority(currentPriorityName)
                .externalId(serviceRequest.getId())
                .originName(fsmProperties.getServiceCallOriginC8y())
                .originCode(fsmProperties.getServiceCallOriginC8y());



       /* // Map contact information
        if (serviceRequest.getCustomerName() != null ||
                serviceRequest.getCustomerEmail() != null ||
                serviceRequest.getCustomerPhone() != null) {

            builder.contact(FsmServiceCall.Contact.builder()
                    .name(serviceRequest.getCustomerName())
                    .email(serviceRequest.getCustomerEmail())
                    .phone(serviceRequest.getCustomerPhone())
                    .build());
        }*/

        // Map address
/*        if (serviceRequest.getServiceAddress() != null) {
            ServiceRequest.Address srAddress = serviceRequest.getServiceAddress();

            FsmServiceCall.Address.FsmServiceCallAddressBuilder addressBuilder =
                    FsmServiceCall.Address.builder()
                            .street(srAddress.getStreet())
                            .city(srAddress.getCity())
                            .state(srAddress.getState())
                            .postalCode(srAddress.getPostalCode())
                            .country(srAddress.getCountry());

            // Map geolocation if available
            if (srAddress.getLatitude() != null && srAddress.getLongitude() != null) {
                addressBuilder.geoLocation(FsmServiceCall.GeoLocation.builder()
                        .latitude(srAddress.getLatitude())
                        .longitude(srAddress.getLongitude())
                        .build());
            }

            builder.address(addressBuilder.build());
        }*/
        /*if (serviceRequest.getServiceAddress() != null) {
            ServiceRequest.Address srAddress = serviceRequest.getServiceAddress();

            // Start building the Address object
            var addressBuilder = FsmServiceCall.Address.builder()
                    .street(srAddress.getStreet())
                    .city(srAddress.getCity())
                    .state(srAddress.getState())
                    .postalCode(srAddress.getPostalCode())
                    .country(srAddress.getCountry());

            // Map geolocation if available
            if (srAddress.getLatitude() != null && srAddress.getLongitude() != null) {
                addressBuilder.geoLocation(FsmServiceCall.GeoLocation.builder()
                        .latitude(srAddress.getLatitude())
                        .longitude(srAddress.getLongitude())
                        .build());
            }

            // Build the Address and assign to the main builder
            builder.address(addressBuilder.build());
        }*/

        // Map scheduled date
    /*    if (serviceRequest.getScheduledDate() != null) {
            builder.plannedStartDateTime(
                    serviceRequest.getScheduledDate().atOffset(ZoneOffset.UTC));
        }*/

        FsmServiceCall fsmServiceCall = builder.build();
        log.debug("Successfully mapped service request to FSM service call");

        return fsmServiceCall;
    }

    /**
     * Maps FSM status back to Cumulocity status
     */
    /*public String mapStatusFromFsm(String fsmStatus) {
        if (fsmStatus == null) {
            return "NEW";
        }

        return switch (fsmStatus.toUpperCase()) {
            case "NEW", "ASSIGNED" -> "NEW";
            case "IN_PROGRESS", "STARTED" -> "IN_PROGRESS";
            case "COMPLETED", "CLOSED" -> "COMPLETED";
            case "CANCELLED" -> "CANCELLED";
            default -> {
                log.warn("Unknown FSM status: {}, defaulting to NEW", fsmStatus);
                yield "NEW";
            }
        };
    }*/

    /**
     * Maps Cumulocity status to FSM status
     */
    /*private String mapStatusToFsm(String cumulocityStatus) {
        if (cumulocityStatus == null) {
            return "NEW";
        }

        return switch (cumulocityStatus.toUpperCase()) {
            case "NEW" -> "NEW";
            case "IN_PROGRESS" -> "IN_PROGRESS";
            case "COMPLETED" -> "COMPLETED";
            case "CANCELLED" -> "CANCELLED";
            default -> {
                log.warn("Unknown Cumulocity status: {}, defaulting to NEW", cumulocityStatus);
                yield "NEW";
            }
        };
    }*/

    /**
     * Maps Cumulocity priority to FSM priority
     */
    /*private String mapPriorityToFsm(String cumulocityPriority) {
        if (cumulocityPriority == null) {
            return "MEDIUM";
        }

        return switch (cumulocityPriority.toUpperCase()) {
            case "LOW" -> "LOW";
            case "MEDIUM" -> "MEDIUM";
            case "HIGH" -> "HIGH";
            case "CRITICAL" -> "CRITICAL";
            default -> {
                log.warn("Unknown priority: {}, defaulting to MEDIUM", cumulocityPriority);
                yield "MEDIUM";
            }
        };
    }*/
}

