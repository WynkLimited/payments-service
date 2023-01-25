package in.wynk.payment.test;

import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.junit.Before;
import org.junit.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.APB_GATEWAY;

public class ApbPaymentsTest extends PaymentsTest {

    private static final String TXN_ID = "c912c207-ca52-11ea-a7b4-1bdc0febe120";
    private static final String FAILURE_TXN_ID = "c912c207-ca52-11ea-a7b4-1bdc0febe120";
    private static final PaymentGateway CODE = PaymentCodeCachingService.getFromPaymentCode(APB_GATEWAY);

    private static SessionDTO dummyAPBSession() {
        Map<String, Object> map = new HashMap<>();
        map.put(MSISDN, "9813265283");
        map.put(UID, "SpTr7ZHG7ZrgfDMEl0");
        map.put(SERVICE, "airteltv");
        SessionDTO sessionDTO = new SessionDTO();
        sessionDTO.setSessionPayload(map);
        return sessionDTO;
    }

    private static CallbackRequest dummyApbFailureCallback() {
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.put("STATUS", Collections.singletonList("FAL"));
        map.put("TXN_REF_NO", Collections.singletonList(TXN_ID));
        map.put("TRAN_AMT", Collections.singletonList("50.00"));
        map.put("MID", Collections.singletonList("180704"));
        map.put("CODE", Collections.singletonList("900"));
        map.put("MSG", Collections.singletonList("Transaction%20cancelled%20by%20user."));
        map.put("HASH", Collections.singletonList("fef41ed1875a22ebc9a4666bcdc4c154422acbf7a88dc14b7b0de715aa9e5fde6d3498c2d32fbe8d915c95e87c8633e28e5dafb647edfc6065335592aa92f705"));
        return CallbackRequestWrapper.builder().payload(map.toSingleValueMap()).build();
    }

    private static CallbackRequest dummyApbSuccessCallback() {
        MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.put("STATUS", Collections.singletonList("SUC"));
        map.put("TXN_REF_NO", Collections.singletonList(TXN_ID));
        map.put("TRAN_AMT", Collections.singletonList("50.00"));
        map.put("MID", Collections.singletonList("180704"));
        map.put("CODE", Collections.singletonList("900"));
        map.put("MSG", Collections.singletonList("Transaction%20completed%20by%20user."));
        map.put("HASH", Collections.singletonList("undefined"));
        return CallbackRequestWrapper.builder().payload(map.toSingleValueMap()).build();
    }

    private static AbstractTransactionStatusRequest dummyLocalChargingStatusRequest(PaymentGateway code) {
        return ChargingTransactionStatusRequest.builder().transactionId(TXN_ID).build();
    }

    @Before
    public void setup() {
        Session<String, SessionDTO> session = Session.<String, SessionDTO>builder().body(dummyAPBSession()).id(UUID.fromString(TXN_ID).toString()).build();
        super.setup(session);
    }

    @Test
    public void apbChargingTest() {
        WynkResponseEntity<?> response = doChargingTest(CODE);
        System.out.println(response);
        assert response.getStatus().is3xxRedirection();
    }

    @Test
    public void apbCallbackFailureTest() {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        sessionDTO.put(TRANSACTION_ID, TXN_ID);
        WynkResponseEntity<?> response = callbackTest(CODE, dummyApbFailureCallback());
        System.out.println(response);
        assert response.getStatus().is3xxRedirection();
    }

    @Test
    public void apbLocalFailedStatusTest() {
        WynkResponseEntity<?> response = statusTest(CODE, dummyLocalChargingStatusRequest(CODE));
        System.out.println(response);
        ChargingStatusResponse statusResponse = (ChargingStatusResponse) response.getBody().getData();
        assert statusResponse.getTransactionStatus().equals(TransactionStatus.FAILURE);
    }

    @Test
    public void apbLocalSuccessStatusTest(PaymentGateway code) {
        WynkResponseEntity<?> response = statusTest(CODE, dummyLocalChargingStatusRequest(code));
        System.out.println(response);
        ChargingStatusResponse statusResponse = (ChargingStatusResponse) response.getBody().getData();
        assert statusResponse.getTransactionStatus().equals(TransactionStatus.FAILURE);
    }

    @Test
    public void apbSourceStatusFailureTest() {
        WynkResponseEntity<?> response = statusTest(CODE, dummyChargingStatusSourceRequest(CODE));
        System.out.println(response);
        ChargingStatusResponse statusResponse = (ChargingStatusResponse) response.getBody().getData();
        assert statusResponse.getTransactionStatus().equals(TransactionStatus.FAILURE);
    }

    @Test
    public void apbSourceStatusSuccessTest() {
        WynkResponseEntity<?> response = statusTest(CODE, dummyChargingStatusSourceRequest(CODE));
        System.out.println(response);
        ChargingStatusResponse statusResponse = (ChargingStatusResponse) response.getBody().getData();
        assert statusResponse.getTransactionStatus().equals(TransactionStatus.FAILURE);
    }

    private AbstractTransactionStatusRequest dummyChargingStatusSourceRequest(PaymentGateway code) {
        return ChargingTransactionReconciliationStatusRequest.builder().transactionId(TXN_ID).build();
    }

    @Test
    public void apbCallbackSuccessTest() {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        sessionDTO.put(TRANSACTION_ID, TXN_ID);
        WynkResponseEntity<?> response = callbackTest(CODE, dummyApbSuccessCallback());
        System.out.println(response);
        assert response.getStatus().is3xxRedirection();
    }

}