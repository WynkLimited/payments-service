package in.wynk.payment.test.payu.testcases.status;

import in.wynk.common.constant.SessionKeys;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = PaymentTestConfiguration.class)
public class PayUPaymentStatusTest {

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

    private IMerchantPaymentStatusService statusService;

    @Before
    @SneakyThrows
    public void setup() {
        if (SessionContextHolder.<SessionDTO>get() == null || SessionContextHolder.<SessionDTO>get().getBody() == null) {
            SessionContextHolder.set(PayUTestData.initSession());
        }
        Mockito.doNothing().when(recurringPaymentManager).scheduleRecurringPayment(any(Transaction.class), any(TransactionStatus.class), any(TransactionStatus.class));
        Mockito.doNothing().when(recurringPaymentManager).unScheduleRecurringPayment(eq(PayUDataConstant.RECURRING_TRANSACTION_ID).toString(), Mockito.any(PaymentEvent.class), Mockito.anyLong(), Mockito.anyLong());
        Mockito.doNothing().when(subscriptionManager).subscribePlanAsync(anyInt(), anyString(), anyString(), anyString(), anyString(), any(), any());
        Mockito.doNothing().when(subscriptionManager).unSubscribePlanAsync(anyInt(), anyString(), anyString(), anyString(), any(), any());

        Mockito.when(transactionManager.upsert(any())).thenReturn(null);
        Mockito.when(transactionManager.get(eq(PayUDataConstant.ONE_TIME_TRANSACTION_ID.toString()))).thenReturn(PayUTestData.initOneTimePaymentTransaction());
        Mockito.when(transactionManager.get(eq(PayUDataConstant.RECURRING_TRANSACTION_ID.toString()))).thenReturn(PayUTestData.initRecurringPaymentTransaction());

        Mockito.when(paymentCachingService.getPlan(eq(PayUDataConstant.RECURRING_PLAN_ID))).thenReturn(PayUTestData.getPlanOfType(PayUDataConstant.RECURRING_PLAN_ID, PlanType.SUBSCRIPTION));
        Mockito.when(paymentCachingService.getPlan(eq(PayUDataConstant.ONE_TIME_PLAN_ID))).thenReturn(PayUTestData.getPlanOfType(PayUDataConstant.ONE_TIME_PLAN_ID, PlanType.ONE_TIME_SUBSCRIPTION));

        Mockito.when(restTemplate.postForObject(eq(payUInfoApiUrl), eq(PayUTestData.buildOneTimePayUTransactionStatusRequest(payUMerchantKey)), eq(String.class))).thenReturn(PayUTestData.buildSuccessOneTimePayUTransactionStatusResponse());
        Mockito.when(restTemplate.postForObject(eq(payUInfoApiUrl), eq(PayUTestData.buildRecurringPayUTransactionStatusRequest(payUMerchantKey)), eq(String.class))).thenReturn(PayUTestData.buildSuccessRecurringPayUTransactionStatusResponse());

        statusService = BeanLocatorFactory.getBean(PaymentCode.PAYU.getCode(), IMerchantPaymentStatusService.class);
    }

    @Test
    @Order(1)
    public void handleOneTimePaymentStatusTest() {
        SessionContextHolder.<SessionDTO>get().getBody().put(SessionKeys.TRANSACTION_ID, PayUDataConstant.ONE_TIME_TRANSACTION_ID);
        AbstractTransactionStatusRequest request = PayUTestData.buildOneTimePaymentStatusRequest(PaymentCode.PAYU);
        BaseResponse<?> response = statusService.status(request);
        Assert.assertEquals(response.getResponse().getStatusCode(), HttpStatus.OK);
        Assert.assertNotNull(response.getResponse().getBody());
        Assert.assertEquals(((ChargingStatusResponse) response.getResponse().getBody()).getTransactionStatus(), TransactionStatus.SUCCESS);
    }

    @Test
    @Order(2)
    public void handleRecurringPaymentStatusTest() {
        SessionContextHolder.<SessionDTO>get().getBody().put(SessionKeys.TRANSACTION_ID, PayUDataConstant.RECURRING_TRANSACTION_ID);
        AbstractTransactionStatusRequest request = PayUTestData.buildRecurringPaymentStatusRequest(PaymentCode.PAYU);
        BaseResponse<?> response = statusService.status(request);
        Assert.assertEquals(response.getResponse().getStatusCode(), HttpStatus.OK);
        Assert.assertNotNull(response.getResponse().getBody());
        Assert.assertEquals(((ChargingStatusResponse) response.getResponse().getBody()).getTransactionStatus(), TransactionStatus.SUCCESS);
    }

}
