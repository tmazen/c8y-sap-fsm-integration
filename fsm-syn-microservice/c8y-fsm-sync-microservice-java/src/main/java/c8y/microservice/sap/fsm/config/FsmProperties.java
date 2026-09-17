package c8y.microservice.sap.fsm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fsm")
public class FsmProperties {
    private String apiUrl;
    private String headerAccountId;
    private String headerCompanyId;
    private String headerClientId;
    private String headerClientVersion;
    private String clientId;
    private String clientSecret;
    //private String authUrl;
    private String serviceCallVersion;
    private String serviceCallOriginC8y;

    private int connectionTimeout = 30000;
    private int readTimeout = 60000;

    private Sync sync = new Sync();

    @Data
    public static class Sync {
        private boolean enabled = true;
        private int intervalMinutes = 5;
        private int batchSize = 50;
        private int maxRetries = 3;
    }
}

