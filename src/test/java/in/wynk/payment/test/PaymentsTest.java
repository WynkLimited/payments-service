package in.wynk.payment.test;

import in.wynk.common.dto.SessionDTO;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.http.config.HttpClientConfig;
import in.wynk.payment.PaymentApplication;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.*;
import in.wynk.payment.test.utils.PaymentTestUtils;
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

import java.util.concurrent.TimeUnit;

import static in.wynk.payment.test.utils.PaymentTestUtils.PLAN_ID;
import static in.wynk.payment.test.utils.PaymentTestUtils.dummyPlanDTO;
import static org.mockito.ArgumentMatchers.anyInt;

@SpringBootTest(classes = {HttpClientConfig.class, PaymentApplication.class})
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
        sessionManager.put(session, 10, TimeUnit.MINUTES);
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

    protected BaseResponse<?> callbackTest(PaymentCode paymentCode, CallbackRequest request) {
        IMerchantPaymentCallbackService callbackService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentCallbackService.class);
        return callbackService.handleCallback(request);
    }

    protected BaseResponse<?> statusTest(PaymentCode paymentCode, AbstractTransactionStatusRequest statusRequest){
        IMerchantPaymentStatusService callbackService = BeanLocatorFactory.getBean(paymentCode.getCode(), IMerchantPaymentStatusService.class);
        return callbackService.status(statusRequest);
    }

    @After
    public void finish(){
        Session<SessionDTO> session = SessionContextHolder.get();
        sessionManager.put(session,  10, TimeUnit.MINUTES);
    }
}
