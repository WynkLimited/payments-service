package in.wynk.payment.test;

import com.google.gson.Gson;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.service.IPaymentOptionService;
import in.wynk.payment.test.utils.PaymentTestUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class PaymentMethodServiceTest {

    @Autowired
    private IPaymentOptionService paymentOptionService;
    @Autowired
    private Gson gson;

    @Test
    public void testPaymentOptions(){
        SessionDTO dummySession = PaymentTestUtils.dummySession();
        PaymentOptionsDTO dto  = paymentOptionService.getPaymentOptions(dummySession, "123");
        System.out.println(gson.toJson(dto));

    }
}
