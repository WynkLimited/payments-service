package in.wynk.payment.test.payu.testcases.renew;

import in.wynk.payment.test.config.PaymentTestConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Order;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = PaymentTestConfiguration.class)
public class PayUPaymentRenewTest {

    @Before
    public void setup() {

    }

    @Test
    @Order(1)
    public void test() {

    }

}
