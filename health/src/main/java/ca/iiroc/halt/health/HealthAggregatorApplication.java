package ca.iiroc.halt.health;

import ca.iiroc.halt.health.config.HealthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(HealthProperties.class)
public class HealthAggregatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthAggregatorApplication.class, args);
    }
}
