package in.wynk.payment.test;

import com.datastax.driver.core.utils.UUIDs;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.http.config.HttpClientConfig;
import in.wynk.payment.PaymentApplication;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.PlanDetails;
import in.wynk.payment.dto.S2SPurchaseDetails;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.DefaultChargingRequest;
import in.wynk.payment.service.*;
import in.wynk.payment.test.utils.PaymentTestUtils;
import in.wynk.session.constant.SessionConstant;
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

import static in.wynk.payment.constant.WalletConstants.PHONEPE_WALLET;
import static in.wynk.payment.test.utils.PaymentTestUtils.PLAN_ID;
import static in.wynk.payment.test.utils.PaymentTestUtils.dummyPlanDTO;
import static org.mockito.ArgumentMatchers.anyInt;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {HttpClientConfig.class, PaymentApplication.class})
public class PaymentsTest {

    @MockBean
    protected ISubscriptionServiceManager subscriptionServiceManager;

    @MockBean
    protected PaymentCachingService cachingService;

    @Autowired
    protected ISessionManager sessionManager;

    public void setup (Session<String, SessionDTO> session) {
        Mockito.doReturn(PaymentTestUtils.dummyPlansDTO()).when(subscriptionServiceManager).getPlans();
        sessionManager.put(SessionConstant.SESSION_KEY + SessionConstant.COLON_DELIMITER + UUIDs.timeBased().toString(), session, 10, TimeUnit.MINUTES);
        SessionContextHolder.set(session);
        Mockito.doReturn(dummyPlanDTO()).when(cachingService).getPlan(anyInt());
    }

    @Test
    public void phonePeChargingTest () {
        PaymentGateway code = PaymentCodeCachingService.getFromPaymentCode(PHONEPE_WALLET);
        WynkResponseEntity<?> response = doChargingTest(code);
        assert response.getStatus().is3xxRedirection();
    }

    protected WynkResponseEntity<?> doChargingTest (PaymentGateway paymentGateway) {
        IMerchantPaymentChargingService chargingService = BeanLocatorFactory.getBean(paymentGateway.getCode(), IMerchantPaymentChargingService.class);
        AbstractChargingRequest<?> request =
                DefaultChargingRequest.builder().purchaseDetails(S2SPurchaseDetails.builder().productDetails(PlanDetails.builder().planId(PLAN_ID).build()).build()).build();
        return chargingService.charge(request);
    }

    protected WynkResponseEntity<?> callbackTest (PaymentGateway paymentGateway, CallbackRequest request) {
        IMerchantPaymentCallbackService callbackService = BeanLocatorFactory.getBean(paymentGateway.getCode(), IMerchantPaymentCallbackService.class);
        return callbackService.handleCallback(request);
    }

    protected WynkResponseEntity<?> statusTest (PaymentGateway paymentGateway, AbstractTransactionStatusRequest statusRequest) {
        IMerchantPaymentStatusService callbackService = BeanLocatorFactory.getBean(paymentGateway.getCode(), IMerchantPaymentStatusService.class);
        return callbackService.status(statusRequest);
    }

    @After
    public void finish () {
        Session<String, SessionDTO> session = SessionContextHolder.get();
        sessionManager.put(SessionConstant.SESSION_KEY + SessionConstant.COLON_DELIMITER + UUIDs.timeBased().toString(), session, 10, TimeUnit.MINUTES);
    }

}