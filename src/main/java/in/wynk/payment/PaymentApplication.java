package in.wynk.payment;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(scanBasePackages = "in.wynk", exclude = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
public class PaymentApplication implements ApplicationRunner {


    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("PaymentApplication Starting");
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
