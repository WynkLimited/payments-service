package in.wynk.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "in.wynk.payment.dao.receipts", mongoTemplateRef = "seTemplate")
public class ReceiptDaoConfig {
}
