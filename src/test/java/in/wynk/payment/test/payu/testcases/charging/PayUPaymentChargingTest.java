package in.wynk.payment.test.payu.testcases.charging;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.http.config.HttpClientConfig;
import in.wynk.payment.PaymentApplication;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import in.wynk.payment.service.IMerchantPaymentChargingService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.test.payu.constant.PayUDataConstant;
import in.wynk.payment.test.payu.data.PayUTestData;
import in.wynk.payment.test.utils.PaymentTestUtils;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.enums.PlanType;
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

import static in.wynk.payment.core.constant.PaymentConstants.PAYU;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {PaymentApplication.class, HttpClientConfig.class})
public class PayUPaymentChargingTest {

    @MockBean
    protected ISubscriptionServiceManager subscriptionServiceManager;
    @MockBean
    private ITransactionManagerService transactionManager;
    @MockBean
    private PaymentCachingService paymentCachingService;
    private IMerchantPaymentChargingService chargingService;

    @Before
    @SneakyThrows
    public void setup() {
        SessionContextHolder.set(PayUTestData.initSession());
        Mockito.when(subscriptionServiceManager.getPlans()).thenReturn(PaymentTestUtils.dummyPlansDTO());
        Mockito.when((transactionManager.init(any()))).thenReturn(PayUTestData.initOneTimePaymentTransaction());
        Mockito.when((transactionManager.init(any()))).thenReturn(PayUTestData.initRecurringPaymentTransaction());
        Mockito.when(paymentCachingService.getPlan(eq(PayUDataConstant.ONE_TIME_PLAN_ID))).thenReturn(PayUTestData.getPlanOfType(PayUDataConstant.ONE_TIME_PLAN_ID, PlanType.ONE_TIME_SUBSCRIPTION));
        Mockito.when(paymentCachingService.getPlan(eq(PayUDataConstant.RECURRING_PLAN_ID))).thenReturn(PayUTestData.getPlanOfType(PayUDataConstant.RECURRING_PLAN_ID, PlanType.SUBSCRIPTION));
        chargingService = BeanLocatorFactory.getBean(PaymentCodeCachingService.getFromPaymentCode(PAYU).getCode(), IMerchantPaymentChargingService.class);
    }

    @Test
    @Order(1)
    public void doChargingForOneTimePlan() {
        //TransactionContext.set(PayUTestData.initOneTimePaymentTransaction());
        AbstractChargingRequest<?> request = PayUTestData.buildOneTimeChargingRequest();
        WynkResponseEntity<?> response = chargingService.charge(request);
        Assert.assertEquals(response.getStatusCode(), HttpStatus.OK);
        Assert.assertNotNull(response.getBody());
        Assert.assertTrue(((Map<String, String>) response.getBody()).size() > 0);
    }

    @Test
    @Order(2)
    public void doChargingForRecurringPlan() {
        //TransactionContext.set(PayUTestData.initRecurringPaymentTransaction());
        AbstractChargingRequest<?> request = PayUTestData.buildRecurringChargingRequest();
        WynkResponseEntity<?> response = chargingService.charge(request);
        Assert.assertEquals(response.getStatusCode(), HttpStatus.OK);
        Assert.assertNotNull(response.getBody());
        Assert.assertTrue(((Map<String, String>) response.getBody()).size() > 0);
    }

    @Test
    @Order(3)
    public void testSiDetails() {
        // TransactionContext.set(PayUTestData.initRecurringSubscribeTransaction());
        AbstractChargingRequest<?> request = PayUTestData.buildRecurringChargingRequest();
        WynkResponseEntity<?> response = chargingService.charge(request);
        Assert.assertEquals(response.getStatusCode(), HttpStatus.OK);
    }

}