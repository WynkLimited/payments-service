package in.wynk.payment.config;

import in.wynk.common.validations.IHandler;
import in.wynk.payment.validations.ClientValidator;
import in.wynk.payment.validations.CouponValidator;
import in.wynk.payment.validations.PaymentMethodValidator;
import in.wynk.payment.validations.PlanValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static in.wynk.payment.core.constant.BeanConstant.CHARGING_FRAUD_DETECTION_CHAIN;

@Configuration
public class PaymentFraudCheckConfig {
    @Bean(CHARGING_FRAUD_DETECTION_CHAIN)
    public IHandler paymentChargingChain() {
        final PlanValidator planValidator = new PlanValidator();
        final CouponValidator couponValidator = new CouponValidator();
        final ClientValidator clientValidator = new ClientValidator();
        final PaymentMethodValidator paymentMethodValidator = new PaymentMethodValidator();
        planValidator.setNext(couponValidator);
        paymentMethodValidator.setNext(planValidator);
        clientValidator.setNext(paymentMethodValidator);
        return clientValidator;
    }
}