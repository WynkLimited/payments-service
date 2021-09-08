package in.wynk.payment.config;

import in.wynk.common.validations.IHandler;
import in.wynk.payment.validations.CouponValidator;
import in.wynk.payment.validations.ClientValidator;
import in.wynk.payment.validations.PaymentMethodValidator;
import in.wynk.payment.validations.PlanValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static in.wynk.payment.core.constant.BeanConstant.PAYMENT_CHARGING_CHAIN;

@Configuration
public class PaymentFraudCheckConfig {
    @Bean(PAYMENT_CHARGING_CHAIN)
    public IHandler paymentChargingChain() {
        PlanValidator planValidator = new PlanValidator();
        CouponValidator couponValidator = new CouponValidator();
        ClientValidator clientValidator = new ClientValidator();
        PaymentMethodValidator paymentMethodValidator = new PaymentMethodValidator();
        planValidator.setNext(couponValidator);
        paymentMethodValidator.setNext(planValidator);
        clientValidator.setNext(paymentMethodValidator);
        return clientValidator;
    }
}