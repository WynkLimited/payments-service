package in.wynk.payment.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties("payment")
@Getter
@Setter
public class PaymentProperties {

    private MongoProperties mongo;
}