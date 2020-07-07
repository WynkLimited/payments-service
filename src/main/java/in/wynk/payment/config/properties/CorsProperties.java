package in.wynk.payment.config.properties;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cors")
@Data
public class CorsProperties {

    private int maxAge;
    private Allowed allowed;

    @Getter
    @Setter
    public static class Allowed {

        private String[] origins;
        private String[] methods;

    }

}
