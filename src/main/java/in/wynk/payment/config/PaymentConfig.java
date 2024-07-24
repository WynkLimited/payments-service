package in.wynk.payment.config;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import in.wynk.aws.common.properties.AmazonSdkProperties;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.context.WynkApplicationContext;
import in.wynk.common.properties.CorsProperties;
import in.wynk.payment.filter.TransactionContextCleanUpFilter;
import in.wynk.payment.mapper.WinBackTokenMapper;
import in.wynk.payment.provider.WinBackAuthenticationProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.Ordered;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@EnableAsync
@EnableRetry
@Configuration
@EnableScheduling
@EnableConfigurationProperties({CorsProperties.class})
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
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(40);
        executor.setMaxPoolSize(80);
        executor.setQueueCapacity(400);
        executor.initialize();
        return executor;
    }

    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        // Configure the retry policy, backoff policy, etc.
        return retryTemplate;
    }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService executorService() {
        return Executors.newWorkStealingPool();
    }


    @Bean
    public WynkApplicationContext myApplicationContext(ClientDetailsCachingService cachingService) {
        ClientDetails client = (ClientDetails) cachingService.getClientByAlias(applicationAlias);
        return WynkApplicationContext.builder()
                .meta(client.getMeta())
                .clientId(client.getClientId())
                .clientAlias(client.getAlias())
                .clientSecret(client.getClientSecret()).clientAlias(client.getAlias())
                .build();
    }


    @Bean
    public AmazonS3 amazonS3Client(AmazonSdkProperties sdkProperties) {
        return AmazonS3ClientBuilder.standard().withRegion(sdkProperties.getSdk().getRegions()).build();
    }

    @Bean
    public WinBackTokenMapper winBackTokenMapper() {
        return new WinBackTokenMapper();
    }

    @Bean
    public AuthenticationProvider winBackAuthenticationProvider(ClientDetailsCachingService cachingService) {
        return new WinBackAuthenticationProvider(cachingService);
    }

    @Bean
    public FilterRegistrationBean<TransactionContextCleanUpFilter> transactionContextCleanUpFilter() {
        FilterRegistrationBean<TransactionContextCleanUpFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new TransactionContextCleanUpFilter());
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

}
