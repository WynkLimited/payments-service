package in.wynk.payment.config;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.context.WynkApplicationContext;
import in.wynk.common.properties.CorsProperties;
import in.wynk.data.config.WynkMongoDbFactoryBuilder;
import in.wynk.data.config.properties.MongoProperties;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.queue.config.properties.AmazonSdkProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static in.wynk.payment.core.constant.BeanConstant.PAYMENT_MONGO_DB_FACTORY_REF;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({CorsProperties.class})
@EnableMongoRepositories(basePackages = "in.wynk.payment.core.dao", mongoTemplateRef = BeanConstant.PAYMENT_MONGO_TEMPLATE_REF)
public class PaymentConfig implements WebMvcConfigurer {

    @Value("${spring.application.name}")
    private String applicationAlias;

    private final CorsProperties corsProperties;
    private final AmazonSdkProperties sdkProperties;

    public PaymentConfig(CorsProperties corsProperties, AmazonSdkProperties amazonSdkProperties) {
        this.corsProperties = corsProperties;
        this.sdkProperties = amazonSdkProperties;
    }

    @Bean
    public Gson gson() {
        return new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
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

    @Bean
    public ExecutorService executorService() {
        return Executors.newWorkStealingPool();
    }

    @Primary
    @Bean(name = {PAYMENT_MONGO_DB_FACTORY_REF, "mongoDbFactory"})
    public MongoDbFactory paymentDbFactory(MongoProperties mongoProperties) {
        return WynkMongoDbFactoryBuilder.buildMongoDbFactory(mongoProperties, "payment");
    }

    @Bean(name = {BeanConstant.PAYMENT_MONGO_TEMPLATE_REF, "mongoTemplate"})
    @Primary
    public MongoTemplate paymentMongoTemplate(MongoProperties mongoProperties) {
        return new MongoTemplate(paymentDbFactory(mongoProperties));
    }

    @Bean
    public WynkApplicationContext myApplicationContext(ClientDetailsCachingService cachingService) {
        ClientDetails client = (ClientDetails) cachingService.getClientByAlias(applicationAlias);
        return WynkApplicationContext.builder()
                .meta(client.getMeta())
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret()).clientAlias(client.getAlias())
                .build();
    }


    @Bean
    public AmazonS3 amazonS3Client(AmazonSdkProperties sdkProperties) {
        return AmazonS3ClientBuilder.standard().withRegion(sdkProperties.getSdk().getRegions()).build();
    }

}
