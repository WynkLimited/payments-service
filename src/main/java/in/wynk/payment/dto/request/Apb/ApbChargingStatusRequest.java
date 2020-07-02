package in.wynk.payment.dto.request.Apb;

import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.enums.Apb.StatusMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
public class ApbChargingStatusRequest extends ChargingStatusRequest {
    private String txnId;
    private String externalTxnId;
    private double amount;
    private long txnDate;
    private StatusMode statusMode;
}
