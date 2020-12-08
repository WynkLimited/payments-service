package in.wynk.payment.test;

import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.test.utils.PaymentTestUtils;
import in.wynk.session.dto.Session;
import in.wynk.session.service.ISessionManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@RunWith(SpringRunner.class)
public class SessionCrudTest {

    @Autowired
    private ISessionManager sessionManager;

    @Test
    public void saveSessionTest() {
        SessionDTO dto = PaymentTestUtils.dummySession();
        sessionManager.put(Session.<SessionDTO>builder().body(dto).id(UUID.randomUUID()).build(), 10, TimeUnit.MINUTES);

    }
}
