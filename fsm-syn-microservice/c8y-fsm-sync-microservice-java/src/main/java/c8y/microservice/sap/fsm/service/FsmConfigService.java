package c8y.microservice.sap.fsm.service;

import c8y.microservice.sap.fsm.config.FsmProperties;
import com.cumulocity.microservice.context.ContextService;
import com.cumulocity.microservice.context.credentials.MicroserviceCredentials;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.sdk.client.SDKException;
import com.cumulocity.sdk.client.option.TenantOptionApi;
import com.cumulocity.rest.representation.tenant.OptionRepresentation;
import org.springframework.core.env.Environment;

import com.cumulocity.model.option.OptionPK;



import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;


import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class FsmConfigService {

    private final MicroserviceSubscriptionsService subscriptionsService;


    // USE A MAP TO CACHE PER TENANT
    private final Map<String, FsmProperties> tenantCache = new ConcurrentHashMap<>();
    private TenantOptionApi tenantOptionApi;
    private final FsmProperties globalFsmProperties;
    private final ContextService contextService;
    //private final String category = "c8y.sap.fsm.sync";
    private final String category = "sap.fsm";
    private final Environment environment;

    public FsmConfigService(MicroserviceSubscriptionsService subscriptionsService, TenantOptionApi tenantOptionApi
                            ,FsmProperties globalFsmProperties, @Qualifier("microserviceContextService") ContextService  contextService,
                            Environment environment) {
        this.subscriptionsService = subscriptionsService;
        this.tenantOptionApi = tenantOptionApi;
        this.globalFsmProperties = globalFsmProperties;
        this.contextService = contextService;
        this.environment = environment;
    }

    @PostConstruct
    public void loadSettings() {
        log.info("Start of loadSettings......");
        //cachedSettings = new FsmProperties();

        try {
            // Run this logic for each tenant to safely fetch TenantOptions
            subscriptionsService.runForEachTenant(() -> {
                String tenantId = subscriptionsService.getTenant();
                loadSettingsForSingleTenant(tenantId);
            });
        } catch (Exception e) {
            log.warn("Could not load cloud subscriptions (likely running locally). Falling back to global properties.");
        }
    }

    public String getOption(String category, String key) {
        OptionPK optionPK = new OptionPK();
        optionPK.setCategory(category);
        optionPK.setKey(key);

        String optoinValue = tenantOptionApi.getOption(optionPK).getValue();
        log.info("Get Option key {} in  category {} and value is {}", key, category, optoinValue);

        return optoinValue;
    }

   /* private String decodeSecret(String encoded) {
        return encoded != null ? new String(Base64.getDecoder().decode(encoded)) : null;
    }*/

    public FsmProperties getCachedSettings(String tenantId) {
        // 1. Try to get from memory
        FsmProperties props = tenantCache.get(tenantId);

        // 2. If missing, try to load it immediately (Lazy Loading)
        if (props == null) {
            log.info("Cache miss for tenant {}. Attempting to load FSM settings now...", tenantId);
            loadSettingsForSingleTenant(tenantId);
            props = tenantCache.get(tenantId);
        }

        return props;
    }

    public void loadSettingsForSingleTenant(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            log.warn("Cannot load settings: tenantId is null or empty");
            return;
        }

        try {
            subscriptionsService.runForTenant(tenantId, () -> {
                fetchAndCache(tenantId);
            });
        } catch (Exception e) {
            log.error("Failed to execute sync in tenant context for {}", tenantId, e);

            // Local Fallback: If subscriptionsService failed (likely local dev),
            // then and ONLY then use the manual context faker.
            if (!contextService.isInContext()) {
                runInLocalContext(tenantId);
            }
        }
    }

    private void runInLocalContext(String tenantId) {
        log.info("Establishing manual context for local tenant: {}", tenantId);

        // Build credentials from Environment Variables
        MicroserviceCredentials credentials = MicroserviceCredentials.builder()
                .tenant(tenantId)
                .username(System.getenv("C8Y_BOOTSTRAP_USER"))
                .password(System.getenv("C8Y_BOOTSTRAP_PASSWORD"))
                .build();

        try {
            // This activates the 'tenant' scope so tenantOptionApi can work
            contextService.runWithinContext(credentials, () -> fetchAndCache(tenantId));
        } catch (Exception e) {
            log.error("Local context execution failed. Ensure C8Y_BOOTSTRAP env vars are set: {}", e.getMessage());
        }
    }

    private void fetchAndCache(String tenantId) {
        try {
            FsmProperties fsmProperties = new FsmProperties();

            fsmProperties.setApiUrl(getOption(category, "api-url"));
            fsmProperties.setHeaderAccountId(getOption(category, "header-account-id"));
            fsmProperties.setHeaderCompanyId(getOption(category, "header-company-id"));
            fsmProperties.setHeaderClientId(getOption(category, "header-client-id"));
            fsmProperties.setHeaderClientVersion(getOption(category, "header-client-version"));
            fsmProperties.setClientId(getOption(category, "client-id"));

            // Check if we are running locally
            boolean isLocal = Arrays.asList(environment.getActiveProfiles()).contains("local");
            String encodedSecret = "";

            if(isLocal) {
                log.info("Local profile detected with encrypted C8Y option. Using secret from YAML.");
                encodedSecret = globalFsmProperties.getClientSecret();
                log.info("Get Option key {} in  category {} and value is {}", "credentials.client-secret", category, encodedSecret);
                //fsmProperties.setClientSecret();

            } else {
                encodedSecret = getOption(category, "credentials.client-secret");
            }

            //fsmProperties.setClientSecret(decodeSecret(encodedSecret));
            fsmProperties.setClientSecret(encodedSecret);

            fsmProperties.setServiceCallVersion(getOption(category, "service-call-version"));

            fsmProperties.setConnectionTimeout(globalFsmProperties.getConnectionTimeout());
            fsmProperties.setReadTimeout(globalFsmProperties.getReadTimeout());
            fsmProperties.setServiceCallOriginC8y(globalFsmProperties.getServiceCallOriginC8y());


            tenantCache.put(tenantId, fsmProperties);

            log.info("FSM Settings loaded successfully for tenant: {}", tenantId);
        } catch (SDKException e) {
            log.error("Failed to load FSM settings for tenant: {}", tenantId, e);
        }
    }
}
