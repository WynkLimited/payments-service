package in.wynk.payment.test;

import in.wynk.http.config.HttpClientConfig;
import in.wynk.payment.PaymentApplication;
import in.wynk.payment.consumer.PaymentRecurringSchedulingPollingQueue;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.repository.IPaymentRenewalDao;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.test.utils.PaymentTestUtils;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.PriceDTO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest(classes = {PaymentApplication.class, HttpClientConfig.class})
@RunWith(SpringRunner.class)
public class RenewalSubscriptionPollingQueueTest {

    @Autowired
    private PaymentRecurringSchedulingPollingQueue paymentRecurringSchedulingPollingQueue;

    @MockBean
    private PaymentCachingService paymentCachingService;

    @MockBean
    private IPaymentRenewalDao paymentRenewalDao;

    @Test
    public void testForConsumer() {
        Mockito.when(paymentRenewalDao.save(Mockito.any(PaymentRenewal.class))).thenReturn(null);
        Mockito.when(paymentCachingService.getPlan(Mockito.anyInt())).thenReturn(PlanDTO.builder()
                .price(PriceDTO.builder().amount(1.0).build()).build());
        paymentRecurringSchedulingPollingQueue.consume(PaymentTestUtils.getDummyRenewalSubscriptionMessage());
    }

}
