package in.wynk.payment.aspect;

import org.aspectj.lang.Aspects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Abhishek
 * @created 13/08/20
 */
@Configuration
public class PaymentAspectConfiguration {

    @Bean
    public FraudAwareAspect fraudAwareAspect() {
        return Aspects.aspectOf(FraudAwareAspect.class);
    }

    @Bean
    public TransactionAwareAspect transactionAwareAspect() {
        return Aspects.aspectOf(TransactionAwareAspect.class);
    }

}
