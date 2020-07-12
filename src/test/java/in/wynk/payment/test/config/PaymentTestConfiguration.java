package in.wynk.payment.test.config;

import in.wynk.http.config.properties.HttpProperties;
import in.wynk.http.template.HttpTemplate;
import in.wynk.http.template.impl.HttpTemplateImpl;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

@TestConfiguration
@TestPropertySource(locations = "classpath:application-test.yml")
public class PaymentTestConfiguration {

    private final HttpProperties httpProperties;

    public PaymentTestConfiguration(HttpProperties httpProperties) {
        this.httpProperties = httpProperties;
    }

    @Bean
    public HttpTemplate subscriptionHttpTemplate() {
        HttpProperties.HttpTemplateProperties templateProperties = httpProperties.getTemplates().get("subscriptionHttpTemplate");
        return HttpTemplateImpl.builder().withHttpTemplateProperties(templateProperties).build();
    }

}
