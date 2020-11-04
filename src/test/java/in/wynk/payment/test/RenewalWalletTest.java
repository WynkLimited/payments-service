package in.wynk.payment.test;

import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.service.IRenewalMerchantWalletService;
import in.wynk.payment.test.utils.PaymentTestUtils;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

@RunWith(SpringRunner.class)
@SpringBootTest
public class RenewalWalletTest {

    @Autowired
    @Qualifier(BeanConstant.PAYTM_MERCHANT_WALLET_SERVICE)
    private IRenewalMerchantWalletService renewalMerchantWalletService;

    @Before
    public void setup(){
        SessionDTO sessionDTO = PaymentTestUtils.dummySession();
        Session<SessionDTO> session = Session.<SessionDTO>builder().body(sessionDTO).id(UUID.randomUUID()).build();
        SessionContextHolder.set(session);
    }


    @Test
    public void testWalletBalance(){
        renewalMerchantWalletService.balance();
    }
}
