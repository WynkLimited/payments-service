package in.wynk.payment.test;

import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.request.WalletBalanceRequest;
import in.wynk.payment.dto.request.WebWalletBalanceRequest;
import in.wynk.payment.dto.response.UserWalletDetails;
import in.wynk.payment.service.IWalletBalanceService;
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
    private IWalletBalanceService<UserWalletDetails, WalletBalanceRequest> renewalMerchantWalletService;

    @Before
    public void setup(){
        SessionDTO sessionDTO = PaymentTestUtils.dummySession();
        Session<String,SessionDTO> session = Session.<String, SessionDTO>builder().body(sessionDTO).id(UUID.randomUUID().toString()).build();
        SessionContextHolder.set(session);
    }


    @Test
    public void testWalletBalance(){
        renewalMerchantWalletService.balance(WebWalletBalanceRequest.builder().planId(606).build());
    }

}