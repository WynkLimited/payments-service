package in.wynk.payment.config;

import in.wynk.payment.config.properties.MongoProperties;
import in.wynk.payment.config.properties.PaymentProperties;
import org.bson.BsonUndefined;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDbFactory;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties({PaymentProperties.class})
@EnableMongoRepositories(basePackages = "in.wynk.payment.core.dao", mongoTemplateRef = "paymentMongoTemplate")
@EnableMongoAuditing
public class PaymentMongoConfig {
    private final MongoProperties mongoProperties;

    public PaymentMongoConfig(PaymentProperties paymentProperties) {
        this.mongoProperties = paymentProperties.getMongo();
    }

    @Bean
    public MongoDbFactory paymentDbFactory() {
        return new SimpleMongoClientDbFactory(mongoProperties.getConnectionSettings("payment"));
    }

    @Bean
    public MongoTemplate paymentMongoTemplate() {
        return new MongoTemplate(paymentDbFactory());
    }

    @Bean
    public MongoCustomConversions customConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(BsonUndefinedToNullObjectConverterFactory.INSTANCE);
        return new MongoCustomConversions(converters);

    }

    @ReadingConverter
    enum BsonUndefinedToNullObjectConverterFactory implements Converter<BsonUndefined, String> {

        INSTANCE;

        @Override
        public String convert(BsonUndefined source) {
            return null;
        }

    }
}
