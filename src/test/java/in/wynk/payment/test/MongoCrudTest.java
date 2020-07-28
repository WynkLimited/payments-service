package in.wynk.payment.test;

import in.wynk.http.config.HttpClientConfig;
import in.wynk.payment.PaymentApplication;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.repository.PaymentMethodDao;
import in.wynk.payment.core.dao.repository.UserPreferredPaymentsDao;
import in.wynk.payment.test.utils.PaymentTestUtils;
import org.junit.Test;
import org.junit.platform.commons.util.StringUtils;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.List;

import static in.wynk.payment.test.utils.PaymentTestUtils.DUMMY_UID;

@SpringBootTest(classes = {HttpClientConfig.class, PaymentApplication.class})
@RunWith(SpringRunner.class)
public class MongoCrudTest {

    @Autowired
    private PaymentMethodDao paymentMethodDao;
    @Autowired
    private UserPreferredPaymentsDao preferredPaymentsDao;

    @Test
    public void insertPaymentMethod() {
        PaymentMethod cardMethod = PaymentTestUtils.dummyCardMethod();
        PaymentMethod netBankingMethod = PaymentTestUtils.dummyNetbankingMethod();
        PaymentMethod walletMethod = PaymentTestUtils.dummyWalletMethod();
        List<PaymentMethod> methods = Arrays.asList(cardMethod, walletMethod, netBankingMethod);
        List<PaymentMethod> methods1 = paymentMethodDao.insert(methods);
        assert methods1.stream().allMatch(m -> StringUtils.isNotBlank(m.getId()));
    }


    @Test
    public void findPaymentMethod() {
        List<PaymentMethod> methods = paymentMethodDao.findAll();
        assert methods.size() > 0;
    }

    @Test
    public void insertUserPreferredCards() {
        UserPreferredPayment preferredPayment = PaymentTestUtils.dummyPreferredCard();
        preferredPaymentsDao.insert(preferredPayment);
    }

    @Test
    public void insertUserPreferredWallets() {
        UserPreferredPayment preferredPayment = PaymentTestUtils.dummyPreferredWallet();
        preferredPaymentsDao.insert(preferredPayment);
    }

    @Test
    public void findPreferredPaymentMethod() {
        List<UserPreferredPayment> methods = preferredPaymentsDao.findByUid(DUMMY_UID);
        assert methods.size() > 0;
    }
}
