package in.wynk.payment.test.payu.testcases.callback;

import in.wynk.common.constant.SessionKeys;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.response.BaseResponse;
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

import static org.mockito.ArgumentMatchers.*;

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
        if(SessionContextHolder.<SessionDTO>get() == null || SessionContextHolder.<SessionDTO>get().getBody() == null) {
            SessionContextHolder.set(PayUTestData.initSession());
        }
        Mockito.doNothing().when(recurringPaymentManager).scheduleRecurringPayment(any(Transaction.class), any(TransactionStatus.class), any(TransactionStatus.class));
        Mockito.doNothing().when(subscriptionManager).subscribePlanSync(anyInt(), anyString(), anyString(), anyString(), anyString(), any(), any());
        Mockito.when(transactionManager.upsert(any())).thenReturn(null);
        Mockito.when(transactionManager.get(eq(PayUDataConstant.ONE_TIME_TRANSACTION_ID.toString()))).thenReturn(PayUTestData.initOneTimePaymentTransaction());
        Mockito.when(transactionManager.get(eq(PayUDataConstant.RECURRING_TRANSACTION_ID.toString()))).thenReturn(PayUTestData.initRecurringPaymentTransaction());

        Mockito.when(paymentCachingService.getPlan(eq(PayUDataConstant.RECURRING_PLAN_ID))).thenReturn(PayUTestData.getPlanOfType(PayUDataConstant.RECURRING_PLAN_ID, PlanType.SUBSCRIPTION));
        Mockito.when(paymentCachingService.getPlan(eq(PayUDataConstant.ONE_TIME_PLAN_ID))).thenReturn(PayUTestData.getPlanOfType(PayUDataConstant.ONE_TIME_PLAN_ID, PlanType.ONE_TIME_SUBSCRIPTION));

        Mockito.when(restTemplate.postForObject(eq(payUInfoApiUrl), eq(PayUTestData.buildOneTimePayUTransactionStatusRequest(payUMerchantKey)), eq(String.class))).thenReturn(PayUTestData.buildSuccessOneTimePayUTransactionStatusResponse());
        Mockito.when(restTemplate.postForObject(eq(payUInfoApiUrl), eq(PayUTestData.buildRecurringPayUTransactionStatusRequest(payUMerchantKey)), eq(String.class))).thenReturn(PayUTestData.buildSuccessRecurringPayUTransactionStatusResponse());

        callbackService = BeanLocatorFactory.getBean(PaymentCode.PAYU.getCode(), IMerchantPaymentCallbackService.class);
    }

    @Test
    @Order(1)
    public void handleOneTimeCallbackTest() {
        SessionContextHolder.<SessionDTO>get().getBody().put(SessionKeys.TRANSACTION_ID, PayUDataConstant.ONE_TIME_TRANSACTION_ID);
        CallbackRequest request = PayUTestData.buildOneTimeCallbackRequest();
        BaseResponse<?> response = callbackService.handleCallback(request);
        Assert.assertEquals(response.getResponse().getStatusCode(), HttpStatus.FOUND);
        Assert.assertNull(response.getResponse().getBody());
        Assert.assertNotNull(response.getHeaders().get(HttpHeaders.LOCATION));
    }

    @Test
    @Order(2)
    public void handleRecurringCallbackTest() {
        SessionContextHolder.<SessionDTO>get().getBody().put(SessionKeys.TRANSACTION_ID, PayUDataConstant.RECURRING_TRANSACTION_ID);
        CallbackRequest request = PayUTestData.buildRecurringCallbackRequest();
        BaseResponse<?> response = callbackService.handleCallback(request);
        Assert.assertEquals(response.getResponse().getStatusCode(), HttpStatus.FOUND);
        Assert.assertNull(response.getResponse().getBody());
        Assert.assertNotNull(response.getHeaders().get(HttpHeaders.LOCATION));
    }

}
