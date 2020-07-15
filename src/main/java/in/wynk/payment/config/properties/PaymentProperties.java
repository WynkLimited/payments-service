package in.wynk.payment.config.properties;

import com.mongodb.ConnectionString;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Map;

import static javafx.scene.input.KeyCode.T;


@ConfigurationProperties("app")
@Getter
@Setter
public class PaymentProperties implements EnvironmentAware {

    private static Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        PaymentProperties.environment = environment;
    }

    public static <T> T getPropertyValue(String var1, Class<T> var2) {
        return environment.getRequiredProperty(var1, var2);
    }


    private MongoProperties mongo;



    @Setter
    @Getter
    public static class MongoProperties {

        private Map<String, DataSource> sources;

        @Getter
        @Setter
        public static class DataSource {
            private String nodes;
            private String username;
            private String password;
            private String database;

            public ConnectionString getConnectionSettings() {
                StringBuilder builder = new StringBuilder("mongodb://");
                if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
                    builder.append(username);
                    builder.append(":");
                    builder.append(password);
                    builder.append("@");
                }
                builder.append(nodes);
                builder.append("/");
                builder.append(database);
                return new ConnectionString(builder.toString());
            }
        }
    }
}