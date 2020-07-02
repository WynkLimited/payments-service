package in.wynk.payment.dto.response.Apb;

import in.wynk.payment.dto.ApbTransaction;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import lombok.*;

import java.util.List;

@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class ApbChargingStatusResponse extends ChargingStatusResponse {
    private String merchantId;
    private List<ApbTransaction> txns;
    private String messageText;
    private String code;
    private String errorCode;
    private String hash;
}
