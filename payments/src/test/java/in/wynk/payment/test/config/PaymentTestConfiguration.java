package in.wynk.payment.test.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.TestPropertySource;

@TestConfiguration
@TestPropertySource(locations = "classpath:application-test.yml")
public class PaymentTestConfiguration {

}
