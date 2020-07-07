package in.wynk.payment;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication(scanBasePackages = "in.wynk")
public class PaymentApplication implements ApplicationRunner {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("PaymentApplication Starting");
    }

    //TODO: to be removed.
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
