package in.wynk.payment.test.payu.testcases.renew;

import in.wynk.http.config.HttpClientConfig;
import in.wynk.payment.PaymentApplication;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {PaymentApplication.class, HttpClientConfig.class})
public class PayUPaymentRenewTest {

    @Before
    public void setup() {

    }

    @Test
    public void test() {

    }

}
