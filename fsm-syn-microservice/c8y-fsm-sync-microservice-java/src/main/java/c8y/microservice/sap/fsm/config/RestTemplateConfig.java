package c8y.microservice.sap.fsm.config;

import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Slf4j
@Configuration
public class RestTemplateConfig {

    @Bean(name = "fsmRestTemplate")
    public RestTemplate fsmRestTemplate(RestTemplateBuilder builder, FsmProperties properties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectionTimeout()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeout()))
                .requestFactory(() -> new BufferingClientHttpRequestFactory(
                        new SimpleClientHttpRequestFactory()))
                .build();
    }

   /* @Bean(name = "serviceRequestRestTemplate")
    public RestTemplate serviceRequestRestTemplate(RestTemplateBuilder builder, ServiceRequestProperties properties) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofSeconds(30))
                .rootUri(properties.getC8yTenantUrl() + properties.getServiceRequestMsUrl())
                .basicAuthentication(properties.getC8yTenantId() + "/" + properties.getC8yUsername(), properties.getC8yPassword())
                .build();
    }*/

    @Bean(name = "serviceRequestRestTemplate")
    public RestTemplate serviceRequestRestTemplate(RestTemplateBuilder builder,
                                                   MicroserviceSubscriptionsService subscriptionsService,
                                                   ServiceRequestProperties properties) {

        String baseUrl = System.getenv("C8Y_BASEURL");

        // Fallback: If C8Y_BASEURL is missing, use a property or default
        if (baseUrl == null || baseUrl.isEmpty()) {
            // You could use properties.getPlatformUrl() if you have one
            baseUrl = properties.getC8yTenantUrl();
        }

        // Ensure it starts with http
        if (!baseUrl.startsWith("http")) {
            baseUrl = "https://" + baseUrl;
        }

        String fullRootUri = baseUrl + properties.getServiceRequestMsUrl();
        log.info("Configuring ServiceRequest RestTemplate with Root URI: {}", fullRootUri);

        return builder
                .additionalInterceptors((request, body, execution) -> {
                    // This pulls the "Service User" credentials for the current tenant automatically
                    subscriptionsService.getCredentials(subscriptionsService.getTenant())
                            .ifPresent(creds -> {
                                String auth = creds.toCumulocityCredentials().getAuthenticationString();
                                request.getHeaders().set("Authorization", auth);
                            });
                    return execution.execute(request, body);
                })
                .rootUri(fullRootUri)
                .build();
    }

    @Bean(name = "serviceRequestByIDRestTemplate")
    public RestTemplate serviceRequestByIDRestTemplate(RestTemplateBuilder builder,
                                                   MicroserviceSubscriptionsService subscriptionsService,
                                                   ServiceRequestProperties properties) {

        String baseUrl = System.getenv("C8Y_BASEURL");

        // Fallback: If C8Y_BASEURL is missing, use a property or default
        if (baseUrl == null || baseUrl.isEmpty()) {
            // You could use properties.getPlatformUrl() if you have one
            baseUrl = properties.getC8yTenantUrl();
        }

        // Ensure it starts with http
        if (!baseUrl.startsWith("http")) {
            baseUrl = "https://" + baseUrl;
        }

        String fullRootUri = baseUrl + properties.getServiceRequestMsUrl();
        log.info("Configuring ServiceRequestByID RestTemplate with Root URI: {}", fullRootUri);

        return builder
                .additionalInterceptors((request, body, execution) -> {
                    // This pulls the "Service User" credentials for the current tenant automatically
                    subscriptionsService.getCredentials(subscriptionsService.getTenant())
                            .ifPresent(creds -> {
                                String auth = creds.toCumulocityCredentials().getAuthenticationString();
                                request.getHeaders().set("Authorization", auth);
                            });
                    return execution.execute(request, body);
                })
                .rootUri(fullRootUri)
                .build();
    }
}
