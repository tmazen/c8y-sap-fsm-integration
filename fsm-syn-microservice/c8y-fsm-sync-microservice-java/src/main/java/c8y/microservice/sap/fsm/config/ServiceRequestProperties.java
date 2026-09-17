package c8y.microservice.sap.fsm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "service-request")
public class ServiceRequestProperties {
    private String c8yTenantUrl;
    //private String c8yTenantId;
    //private String c8yUsername;
    //private String c8yPassword;
    private String serviceRequestMsUrl;
}

