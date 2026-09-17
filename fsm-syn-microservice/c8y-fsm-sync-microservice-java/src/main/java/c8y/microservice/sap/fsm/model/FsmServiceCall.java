package c8y.microservice.sap.fsm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.OffsetDateTime;

//@Data
@Getter // Using explicit Getter/Setter instead of @Data
@Setter
@ToString
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class FsmServiceCall {

    @JsonProperty("id")
    private String id;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("remarks")
    private String remarks;

    @JsonProperty("statusName")
    private String statusName; // NEW, ASSIGNED, IN_PROGRESS, COMPLETED, CLOSED

    @JsonProperty("statusCode")
    private String statusCode; // -5

    @JsonProperty("priority")
    private String priority;

    //@JsonProperty("businessPartner")
    //private String businessPartner; // Customer ID in FSM

    //@JsonProperty("contact")
    ///private Contact contact;

    //@JsonProperty("address")
    //private Address address;

    //@JsonProperty("responsibleId")
    //private String responsibleId; // Technician ID

    //@JsonProperty("plannedStartDateTime")
    //private OffsetDateTime plannedStartDateTime;

    //@JsonProperty("actualStartDateTime")
    //private OffsetDateTime actualStartDateTime;

    //@JsonProperty("actualEndDateTime")
    //private OffsetDateTime actualEndDateTime;

    @JsonProperty("externalId")
    private String externalId; // Reference to Cumulocity Service Request ID

    @JsonProperty("originName")
    private String originName; // Set to "CUMULOCITY"

    @JsonProperty("originCode")
    private String originCode;

/*    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Contact {
        @JsonProperty("name")
        private String name;

        @JsonProperty("email")
        private String email;

        @JsonProperty("phone")
        private String phone;
    }*/

    /*@Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Address {
        @JsonProperty("street")
        private String street;

        @JsonProperty("city")
        private String city;

        @JsonProperty("state")
        private String state;

        @JsonProperty("postalCode")
        private String postalCode;

        @JsonProperty("country")
        private String country;

        @JsonProperty("geoLocation")
        private GeoLocation geoLocation;
    }*/

    /*@Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeoLocation {
        @JsonProperty("latitude")
        private Double latitude;

        @JsonProperty("longitude")
        private Double longitude;
    }*/
}

