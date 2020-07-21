package in.wynk.payment.test;

import in.wynk.commons.dto.SessionDTO;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.session.dto.Session;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static in.wynk.commons.constants.Constants.MSISDN;
import static in.wynk.commons.constants.Constants.SERVICE;
import static in.wynk.commons.constants.Constants.UID;

public class ApbPaymentsTest extends PaymentsTest {

    private static SessionDTO dummyAPBSession() {
        Map<String, Object> map = new HashMap<>();
        map.put(MSISDN, "9813265283");
        map.put(UID, "SpTr7ZHG7ZrgfDMEl0");
        map.put(SERVICE, "airteltv");
        SessionDTO sessionDTO = new SessionDTO();
        sessionDTO.setPayload(map);
        return sessionDTO;
    }

    @Before
    public void setup() {
        Session<SessionDTO> session = Session.<SessionDTO>builder().body(dummyAPBSession()).id(UUID.randomUUID()).build();
        super.setup(session);
    }

    @Test
    public void apbChargingTest() {
        PaymentCode code = PaymentCode.APB_GATEWAY;
        BaseResponse<?> response = doChargingTest(code);
        System.out.println(response);
        assert response.getStatus().is3xxRedirection();
    }
}
