package in.wynk.payment;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "in.wynk")
public class PaymentBootStarter implements ApplicationRunner {


    public static void main(String[] args) {
        SpringApplication.run(PaymentBootStarter.class, args);
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("PaymentBootStarterApplication Starting");
    }
}
