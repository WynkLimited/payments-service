package in.wynk.payment.aspect;

import org.aspectj.lang.Aspects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentAspectConfiguration {
    @Bean
    public TransactionAwareAspect transactionAwareAspect() {
        return Aspects.aspectOf(TransactionAwareAspect.class);
    }
}