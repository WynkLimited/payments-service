package in.wynk.payment.dto.response.Apb;

import in.wynk.payment.dto.ApbTransaction;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import java.util.List;

public class ApbChargingStatusResponse extends ChargingStatusResponse {
    private String merchantId;
    private List<ApbTransaction> txns;
    private String messageText;
    private String code;
    private String errorCode;
    private String hash;
}
