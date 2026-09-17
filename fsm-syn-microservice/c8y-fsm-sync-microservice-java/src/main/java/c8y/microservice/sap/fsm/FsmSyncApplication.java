package c8y.microservice.sap.fsm;

import com.cumulocity.microservice.autoconfigure.MicroserviceApplication;
import com.cumulocity.microservice.context.annotation.EnableContextSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

//@SpringBootApplication
@MicroserviceApplication
@EnableScheduling
@EnableContextSupport
public class FsmSyncApplication {
    public static void main(String[] args) {
        SpringApplication.run(FsmSyncApplication.class, args);
    }
}
