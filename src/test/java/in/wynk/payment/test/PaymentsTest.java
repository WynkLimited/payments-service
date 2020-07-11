package in.wynk.payment.test;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantPaymentChargingService;
import in.wynk.payment.utils.BeanLocatorFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class PaymentsTest {

    private static int PLAN_ID = 1000180;

    @Test
    public void phonePeChargingTest(){
        PaymentCode code = PaymentCode.PHONEPE_WALLET;
        BaseResponse<?> response = doChargingTest(code);
        assert response.getStatus().is2xxSuccessful();
    }

    private BaseResponse<?> doChargingTest(PaymentCode paymentCode){
        IMerchantPaymentChargingService chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentChargingService.class);
        ChargingRequest request = ChargingRequest.builder().paymentCode(paymentCode).planId(PLAN_ID).build();
        return chargingService.doCharging(request);
    }
}
