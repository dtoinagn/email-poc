package ca.iiroc.halt.email;

import ca.iiroc.halt.email.config.EmailServiceProperties;
import ca.iiroc.halt.email.config.HaltReasonProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({EmailServiceProperties.class, HaltReasonProperties.class})
public class EmailServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmailServiceApplication.class, args);
    }
}
