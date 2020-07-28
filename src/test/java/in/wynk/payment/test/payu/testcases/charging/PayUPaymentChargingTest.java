package in.wynk.payment.test.payu.testcases.charging;

import in.wynk.commons.enums.PlanType;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.core.enums.PaymentCode;
import in.wynk.payment.core.utils.BeanLocatorFactory;
import in.wynk.payment.service.IMerchantPaymentChargingService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.test.config.PaymentTestConfiguration;
import in.wynk.payment.test.payu.constant.PayUDataConstant;
import in.wynk.payment.test.payu.data.PayUTestData;
import in.wynk.payment.test.utils.PaymentTestUtils;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.session.context.SessionContextHolder;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Order;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = PaymentTestConfiguration.class)
public class PayUPaymentChargingTest {

    @MockBean
    private ISQSMessagePublisher sqsMessagePublisher;

    @MockBean
    private ITransactionManagerService transactionManager;

    @MockBean
    private PaymentCachingService paymentCachingService;

    private IMerchantPaymentChargingService chargingService;

    @MockBean
    protected ISubscriptionServiceManager subscriptionServiceManager;

    @Before
    @SneakyThrows
    public void setup() {
        SessionContextHolder.set(PayUTestData.initSession());
        Mockito.when(subscriptionServiceManager.getPlans()).thenReturn(PaymentTestUtils.dummyPlansDTO());
        Mockito.when(sqsMessagePublisher.publish(any())).thenReturn("SUCCESS");
        Mockito.when(transactionManager.initiateTransaction(anyString(), anyString(), eq(PayUDataConstant.ONE_TIME_PLAN_ID), anyDouble(), any(), any())).thenReturn(PayUTestData.initOneTimePaymentTransaction());
        Mockito.when(transactionManager.initiateTransaction(anyString(), anyString(), eq(PayUDataConstant.RECURRING_PLAN_ID), anyDouble(), any(), any())).thenReturn(PayUTestData.initRecurringPaymentTransaction());
        Mockito.when(paymentCachingService.getPlan(eq(PayUDataConstant.ONE_TIME_PLAN_ID))).thenReturn(PayUTestData.getPlanOfType(PayUDataConstant.ONE_TIME_PLAN_ID, PlanType.ONE_TIME_SUBSCRIPTION));
        Mockito.when(paymentCachingService.getPlan(eq(PayUDataConstant.RECURRING_PLAN_ID))).thenReturn(PayUTestData.getPlanOfType(PayUDataConstant.RECURRING_PLAN_ID, PlanType.SUBSCRIPTION));
        chargingService = BeanLocatorFactory.getBean(PaymentCode.PAYU.getCode(), IMerchantPaymentChargingService.class);
    }

    @Test
    @Order(1)
    public void doChargingForOneTimePlan() {
        ChargingRequest request = PayUTestData.buildOneTimeChargingRequest();
        BaseResponse<?> response = chargingService.doCharging(request);
        Assert.assertEquals(response.getResponse().getStatusCode(), HttpStatus.OK);
        Assert.assertNotNull(response.getResponse().getBody());
        Assert.assertTrue(((Map<String, String>) response.getResponse().getBody()).size() > 0);
    }

    @Test
    @Order(2)
    public void doChargingForRecurringPlan() {
        ChargingRequest request = PayUTestData.buildRecurringChargingRequest();
        BaseResponse<?> response = chargingService.doCharging(request);
        Assert.assertEquals(response.getResponse().getStatusCode(), HttpStatus.OK);
        Assert.assertNotNull(response.getResponse().getBody());
        Assert.assertTrue(((Map<String, String>) response.getResponse().getBody()).size() > 0);
    }

}
