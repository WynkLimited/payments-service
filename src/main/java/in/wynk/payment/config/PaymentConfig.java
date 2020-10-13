package in.wynk.payment.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.wynk.auth.dao.entity.ClientDetails;
import in.wynk.auth.mapper.S2SPreAuthTokenMapper;
import in.wynk.auth.provider.S2SAuthenticationProvider;
import in.wynk.auth.service.IClientDetailsService;
import in.wynk.auth.service.IS2SDetailsService;
import in.wynk.auth.service.impl.S2SClientDetailsService;
import in.wynk.data.config.WynkMongoDbFactoryBuilder;
import in.wynk.data.config.properties.MongoProperties;
import in.wynk.payment.config.properties.CorsProperties;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.service.impl.PaymentClientDetailsService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({CorsProperties.class})
@EnableMongoRepositories(basePackages = "in.wynk.payment.core.dao", mongoTemplateRef = BeanConstant.PAYMENT_MONGO_TEMPLATE_REF)
public class PaymentConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public PaymentConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public IClientDetailsService<ClientDetails> paymentClientDetailsService() {
        return new PaymentClientDetailsService();
    }

    @Bean
    public IS2SDetailsService s2sDetailsService(IClientDetailsService<ClientDetails> paymentClientDetailsService) {
        return new S2SClientDetailsService(paymentClientDetailsService);
    }

    @Bean(in.wynk.auth.constant.BeanConstant.PRE_AUTH_S2S_DETAILS_TOKEN_MAPPER)
    public S2SPreAuthTokenMapper preAuthS2STokenMapper() {
        return new S2SPreAuthTokenMapper();
    }

    @Bean
    public AuthenticationProvider s2sAuthenticationProvider(IS2SDetailsService s2sDetailsService) {
        return new S2SAuthenticationProvider(s2sDetailsService);
    }

    @Bean
    public Gson gson() {
        return new GsonBuilder().disableHtmlEscaping().create();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsProperties.getAllowed().getOrigins())
                .allowedMethods(corsProperties.getAllowed().getMethods())
                .maxAge(corsProperties.getMaxAge());
    }

    @Profile("!local")
    @Bean(name = "applicationEventMulticaster")
    public ApplicationEventMulticaster simpleApplicationEventMulticaster() {
        SimpleApplicationEventMulticaster eventMulticaster = new SimpleApplicationEventMulticaster();
        eventMulticaster.setTaskExecutor(new SimpleAsyncTaskExecutor("sub-event"));
        return eventMulticaster;
    }

    public MongoDbFactory paymentDbFactory(MongoProperties mongoProperties) {
        return WynkMongoDbFactoryBuilder.buildMongoDbFactory(mongoProperties, "payment");
    }

    @Bean(BeanConstant.PAYMENT_MONGO_TEMPLATE_REF)
    public MongoTemplate paymentMongoTemplate(MongoProperties mongoProperties) {
        return new MongoTemplate(paymentDbFactory(mongoProperties));
    }

}
