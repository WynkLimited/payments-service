package in.wynk.payment.test.payu.testcases.callback;

import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.SubscribePlanAsyncRequest;
import in.wynk.payment.dto.request.SyncTransactionRevisionRequest;
import in.wynk.payment.dto.request.UnSubscribePlanAsyncRequest;
import in.wynk.payment.service.*;
import in.wynk.payment.test.config.PaymentTestConfiguration;
import in.wynk.payment.test.payu.constant.PayUDataConstant;
import in.wynk.payment.test.payu.data.PayUTestData;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.enums.PlanType;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Order;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static in.wynk.common.constant.BaseConstants.TRANSACTION_ID;
import static in.wynk.payment.core.constant.PaymentConstants.PAYU;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = PaymentTestConfiguration.class)
public class PayUPaymentCallbackTest {

    @Value("${payment.merchant.payu.key}")
    private String payUMerchantKey;

    @Value("${payment.merchant.payu.api.info}")
    private String payUInfoApiUrl;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private ISubscriptionServiceManager subscriptionManager;

    @MockBean
    private ITransactionManagerService transactionManager;

    @MockBean
    private IRecurringPaymentManagerService recurringPaymentManager;

    @MockBean
    private PaymentCachingService paymentCachingService;

    private IMerchantPaymentCallbackService callbackService;

    @Before
    @SneakyThrows
    public void setup() {
        if (SessionContextHolder.<String, SessionDTO>get() == null || SessionContextHolder.<String, SessionDTO>get().getBody() == null) {
            SessionContextHolder.set(PayUTestData.initSession());
        }
        Mockito.doNothing().when(recurringPaymentManager).scheduleRecurringPayment(any(SyncTransactionRevisionRequest.class));
        Mockito.doNothing().when(recurringPaymentManager).unScheduleRecurringPayment(transactionManager.get(PayUDataConstant.RECURRING_TRANSACTION_ID.toString()), Mockito.any(PaymentEvent.class), Mockito.anyLong(), Mockito.anyLong());
        Mockito.doNothing().when(subscriptionManager).validateAndSubscribePlanAsync(any(SubscribePlanAsyncRequest.class));
        Mockito.doNothing().when(subscriptionManager).unSubscribePlanAsync(any(UnSubscribePlanAsyncRequest.class));

        Mockito.when(paymentCachingService.getPlan(eq(PayUDataConstant.RECURRING_PLAN_ID))).thenReturn(PayUTestData.getPlanOfType(PayUDataConstant.RECURRING_PLAN_ID, PlanType.SUBSCRIPTION));
        Mockito.when(paymentCachingService.getPlan(eq(PayUDataConstant.ONE_TIME_PLAN_ID))).thenReturn(PayUTestData.getPlanOfType(PayUDataConstant.ONE_TIME_PLAN_ID, PlanType.ONE_TIME_SUBSCRIPTION));

        Mockito.when(restTemplate.postForObject(eq(payUInfoApiUrl), eq(PayUTestData.buildOneTimePayUTransactionStatusRequest(payUMerchantKey)), eq(String.class))).thenReturn(PayUTestData.buildSuccessOneTimePayUTransactionStatusResponse());
        Mockito.when(restTemplate.postForObject(eq(payUInfoApiUrl), eq(PayUTestData.buildRecurringPayUTransactionStatusRequest(payUMerchantKey)), eq(String.class))).thenReturn(PayUTestData.buildSuccessRecurringPayUTransactionStatusResponse());

        callbackService = BeanLocatorFactory.getBean(PaymentCodeCachingService.getFromPaymentCode(PAYU).getCode(), IMerchantPaymentCallbackService.class);
    }

    @Test
    @Order(1)
    public void handleOneTimeCallbackTest() {
        SessionContextHolder.<String, SessionDTO>get().getBody().put(TRANSACTION_ID, PayUDataConstant.ONE_TIME_TRANSACTION_ID);
        CallbackRequest request = PayUTestData.buildOneTimeCallbackRequest();
        WynkResponseEntity<?> response = callbackService.handleCallback(request);
        Assert.assertEquals(response.getStatusCode(), HttpStatus.FOUND);
        Assert.assertNull(response.getBody());
        Assert.assertNotNull(response.getHeaders().get(HttpHeaders.LOCATION));
    }

    @Test
    @Order(2)
    public void handleRecurringCallbackTest() {
        SessionContextHolder.<String, SessionDTO>get().getBody().put(TRANSACTION_ID, PayUDataConstant.RECURRING_TRANSACTION_ID);
        CallbackRequest request = PayUTestData.buildRecurringCallbackRequest();
        WynkResponseEntity<?> response = callbackService.handleCallback(request);
        Assert.assertEquals(response.getStatusCode(), HttpStatus.FOUND);
        Assert.assertNull(response.getBody());
        Assert.assertNotNull(response.getHeaders().get(HttpHeaders.LOCATION));
    }

}