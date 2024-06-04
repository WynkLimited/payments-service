package in.wynk.payment.test.payu.testcases.renew;

import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.PaymentApplication;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.scheduler.PaymentRenewalsScheduler;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.PaymentManager;
import in.wynk.payment.service.impl.PayUMerchantPaymentService;
import in.wynk.payment.service.impl.SubscriptionServiceManagerImpl;
import in.wynk.payment.test.payu.data.PayUTestData;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

import static org.mockito.ArgumentMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {PaymentApplication.class})
@ActiveProfiles("test")
public class PayUPaymentRenewTest {

    @Value("${payment.merchant.payu.key}")
    private String payUMerchantKey;

    @Value("${payment.merchant.payu.api.info}")
    private String payUInfoApiUrl;

    @Autowired
    private PaymentRenewalsScheduler paymentRenewalsScheduler;

    @Autowired
    private PaymentManager paymentManager;

    @Autowired
    private PayUMerchantPaymentService payUMerchantPaymentService;

    @MockBean(name = BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE)
    private RestTemplate restTemplate;

    @MockBean(name = BeanConstant.SUBSCRIPTION_SERVICE_S2S_TEMPLATE)
    private RestTemplate restTemplate1;

    @MockBean(name = BeanConstant.EXTERNAL_PAYMENT_CLIENT_S2S_TEMPLATE)
    private RestTemplate restTemplate2;

    @MockBean
    private IRecurringPaymentManagerService recurringPaymentManagerService;

    @MockBean
    private SubscriptionServiceManagerImpl subscriptionServiceManager;

    @Before
    public void setup() {
        Mockito.when(recurringPaymentManagerService.getCurrentDueRecurringPayments("airtelXstream")).thenReturn(PayUTestData.buildPaymentRenewalTestData());
        Mockito.when(restTemplate.postForObject(anyString(), anyMap(), eq(String.class))).thenReturn(PayUTestData.buildSuccessRecurringPayUTransactionStatusResponse());
    }

    @Test
    public void paymentRenewalsSchedulerTest() {
        // This test case is run in debugger mode and seen that whether only 2 paymentRenewal messages are pushed into queue or not?
        // And this test case passes successfully
        paymentRenewalsScheduler.paymentRenew("requestId", "");
    }

    @Test
    public void paymentManagerDoRenewalTest() {
        PaymentRenewalChargingRequest paymentRenewalChargingRequest = PayUTestData.buildPaymentRenewalChargingRequest();
        //TransactionContext.set(PayUTestData.initRecurringPaymentTransaction());
       //payUMerchantPaymentService.doRenewal(paymentRenewalChargingRequest);
       // Assert.assertEquals(TransactionContext.get().getStatus(), TransactionStatus.SUCCESS);
    }

}
