package in.wynk.payment.test;

import in.wynk.commons.dto.SessionDTO;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantPaymentChargingService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.test.config.PaymentTestConfiguration;
import in.wynk.payment.test.utils.PaymentTestUtils;
import in.wynk.payment.utils.BeanLocatorFactory;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import in.wynk.session.service.ISessionManager;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import static in.wynk.payment.test.utils.PaymentTestUtils.PLAN_ID;
import static in.wynk.payment.test.utils.PaymentTestUtils.dummyPlanDTO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest(classes = PaymentTestConfiguration.class)
@RunWith(SpringRunner.class)
public class PaymentsTest {

    @MockBean
    protected ISubscriptionServiceManager subscriptionServiceManager;

    @MockBean
    protected PaymentCachingService cachingService;

    @Autowired
    protected ISessionManager sessionManager;

    public void setup(Session<SessionDTO> session) {
        Mockito.doReturn(PaymentTestUtils.dummyPlansDTO()).when(subscriptionServiceManager).getPlans();
        Mockito.doReturn("MOCKED_SUCCESS").when(subscriptionServiceManager)
                .publish(anyInt(), anyString(), anyString(), any(), any());
        sessionManager.put(session);
        SessionContextHolder.set(session);
        Mockito.doReturn(dummyPlanDTO()).when(cachingService).getPlan(anyInt());
    }


    @Test
    public void phonePeChargingTest() {
        PaymentCode code = PaymentCode.PHONEPE_WALLET;
        BaseResponse<?> response = doChargingTest(code);
        assert response.getStatus().is3xxRedirection();
    }

    protected BaseResponse<?> doChargingTest(PaymentCode paymentCode) {
        IMerchantPaymentChargingService chargingService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentChargingService.class);
        ChargingRequest request = ChargingRequest.builder().paymentCode(paymentCode).planId(PLAN_ID).build();
        return chargingService.doCharging(request);
    }

    @After
    public void finish(){
        Session<SessionDTO> session = SessionContextHolder.get();
        sessionManager.put(session);
    }
}
