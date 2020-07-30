package in.wynk.payment.config.properties;

import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.NoSuchElementException;

@Data
public class MongoProperties {

    private Map<String, DataSource> sources;
    private Integer minConnectionsPerHost = 50;
    private Integer maxConnectionsPerHost = 200;
    private Integer threadsAllowedToBlockForConnectionMultiplier = 5;
    private Integer maxWaitTime = 30000;
    private Integer connectTimeout = 10000;
    private Integer socketTimeout = 30000;
    private String readPreference = ReadPreference.secondaryPreferred().getName();

    public ConnectionString getConnectionSettings(String dataSource) {
        if (!sources.containsKey(dataSource)) {
            throw new NoSuchElementException(dataSource + " key does not exists");
        }
        String connectionString = this.sources.get(dataSource).getConnectionSettings();
        StringBuilder builder = new StringBuilder(connectionString);
        builder.append("?");
        builder.append("&connectTimeoutMS=").append(connectTimeout);
        builder.append("&socketTimeoutMS=").append(socketTimeout);
        builder.append("&minpoolsize=").append(minConnectionsPerHost);
        builder.append("&maxPoolSize=").append(maxConnectionsPerHost);
        builder.append("&waitqueuemultiple=").append(threadsAllowedToBlockForConnectionMultiplier);
        builder.append("&waitqueuetimeoutms=").append(maxWaitTime);
        builder.append("&readpreference=").append(readPreference);
        return new ConnectionString(builder.toString());
    }

    @Getter
    @Setter
    public static class DataSource {
        private String nodes;
        private String username;
        private String password;
        private String database;

        private String getConnectionSettings() {
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
            return builder.toString();
        }
    }

}

