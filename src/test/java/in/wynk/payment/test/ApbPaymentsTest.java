package in.wynk.payment.test;

import in.wynk.commons.dto.SessionDTO;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.CallbackRequest;
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

    @Test
    public void apbCallbackFailureTest() {
        Map<String, String> map = new HashMap<>();
        map.put("STATUS", "FAL");
        map.put("TXN_REF_NO", "c912c207-ca52-11ea-a7b4-1bdc0febe120");
        map.put("TRAN_AMT", "50.00");
        map.put("MID", "180704");
        map.put("CODE", "900");
        map.put("MSG", "Transaction%20cancelled%20by%20user.");
        map.put("HASH", "undefined");
        PaymentCode code = PaymentCode.APB_GATEWAY;
        CallbackRequest callbackRequest = CallbackRequest.builder().body(map).build();
        BaseResponse<?> response = callbackTest(code, callbackRequest);
        System.out.println(response);
        assert response.getStatus().is3xxRedirection();
    }
}
