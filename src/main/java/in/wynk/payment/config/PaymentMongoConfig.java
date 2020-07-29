package in.wynk.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "in.wynk.payment.core.dao", mongoTemplateRef = "paymentMongoTemplate")
@EnableMongoAuditing
public class PaymentMongoConfig {

}
