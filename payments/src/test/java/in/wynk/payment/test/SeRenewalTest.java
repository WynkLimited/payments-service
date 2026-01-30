package in.wynk.payment.test;

import in.wynk.http.config.HttpClientConfig;
import in.wynk.payment.PaymentApplication;
import in.wynk.payment.scheduler.SeRenewalService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {PaymentApplication.class, HttpClientConfig.class})
public class SeRenewalTest {

    @Autowired
    private SeRenewalService seRenewalService;

    @Test
    public void testSeRenewal(){
        seRenewalService.startSeRenewal();
    }

}
