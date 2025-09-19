package in.wynk.payment.config;

import in.wynk.common.validations.IBaseRequest;
import in.wynk.common.validations.IHandler;
import in.wynk.client.validations.ClientValidator;
import in.wynk.payment.validations.CouponValidator;
import in.wynk.payment.validations.PaymentMethodValidator;
import in.wynk.payment.validations.ProductValidator;
import in.wynk.payment.validations.ReceiptValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static in.wynk.payment.core.constant.BeanConstant.CHARGING_FRAUD_DETECTION_CHAIN;
import static in.wynk.payment.core.constant.BeanConstant.VERIFY_IAP_FRAUD_DETECTION_CHAIN;
import static in.wynk.payment.core.constant.BeanConstant.OPTION_FRAUD_DETECTION_CHAIN;

@Configuration
public class PaymentFraudCheckConfig {

    @Bean(CHARGING_FRAUD_DETECTION_CHAIN)
    public IHandler chargingFraudDetectionChain() {
        //final ProductValidator planValidator = new ProductValidator();
        final CouponValidator couponValidator = new CouponValidator();
        final ClientValidator clientValidator = new ClientValidator();
        final PaymentMethodValidator paymentMethodValidator = new PaymentMethodValidator();
        //planValidator.setNext(couponValidator);
        paymentMethodValidator.setNext(couponValidator);
        clientValidator.setNext(paymentMethodValidator);
        return clientValidator;
    }

    @Bean(VERIFY_IAP_FRAUD_DETECTION_CHAIN)
    public IHandler verifyIAPFraudDetectionChain() {
        final CouponValidator couponValidator = new CouponValidator();
        final ClientValidator clientValidator = new ClientValidator();
        final ReceiptValidator receiptValidator = new ReceiptValidator();
        clientValidator.setNext(couponValidator);
        couponValidator.setNext(receiptValidator);
        return clientValidator;
    }

    @Bean(OPTION_FRAUD_DETECTION_CHAIN)
    public IHandler optionFraudDetectionChain() {

//        final ProductValidator planValidator = new ProductValidator();
//        return planValidator;

        return new IHandler<IBaseRequest>() {
            @Override
            public void setNext(IHandler<IBaseRequest> handler) {
                // no-op
            }

            @Override
            public void handle(IBaseRequest request) {
                // no-op
            }
        };
    }
}