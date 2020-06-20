package in.wynk.payment.config;

import in.wynk.payment.config.properties.Application;
import org.bson.BsonUndefined;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDbFactory;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.ArrayList;
import java.util.List;

public class MongoConfig {
    private final Application.MongoProperties mongoProperties;

    public MongoConfig(Application application) {
        this.mongoProperties = application.getMongo();
    }

    @Bean
    public MongoDbFactory seDbFactory() {
        return new SimpleMongoClientDbFactory(mongoProperties.getSources().get("se").getConnectionSettings());
    }

    @Bean
    public MongoTemplate seTemplate() {
        return new MongoTemplate(seDbFactory());
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
