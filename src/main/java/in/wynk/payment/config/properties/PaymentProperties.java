package in.wynk.payment.config.properties;

import com.mongodb.ConnectionString;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.Map;


@ConfigurationProperties("app")
@Getter
@Setter
public class PaymentProperties {

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