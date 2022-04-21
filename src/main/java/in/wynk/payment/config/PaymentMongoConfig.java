package in.wynk.payment.config;

import in.wynk.data.config.WynkMongoDbFactoryBuilder;
import in.wynk.data.config.properties.MongoProperties;
import in.wynk.payment.core.constant.BeanConstant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@ConditionalOnProperty("wynk.mongodb.enabled")
@EnableMongoRepositories(basePackages = "in.wynk.payment.core.dao", mongoTemplateRef = BeanConstant.PAYMENT_MONGO_TEMPLATE_REF)
public class PaymentMongoConfig {

    @Primary
    @Bean(name = {BeanConstant.PAYMENT_MONGO_DB_FACTORY_REF, "mongoDbFactory"})
    public MongoDbFactory paymentDbFactory(MongoProperties mongoProperties) {
        return WynkMongoDbFactoryBuilder.buildMongoDbFactory(mongoProperties, "payment");
    }

    @Bean(name = {BeanConstant.PAYMENT_MONGO_TEMPLATE_REF, "mongoTemplate"})
    @Primary
    public MongoTemplate paymentMongoTemplate(MongoProperties mongoProperties) {
        return new MongoTemplate(paymentDbFactory(mongoProperties));
    }

}
