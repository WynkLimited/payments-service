package in.wynk.payment.dto.response.apb;

import in.wynk.payment.dto.apb.ApbTransaction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class ApbChargingStatusResponse {
    private String merchantId;
    private List<ApbTransaction> txns;
    private String messageText;
    private String code;
    private String errorCode;
    private String hash;
}
