package in.wynk.payment.presentation.dto.qrCode.upi;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.PollingConfig;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class SeamlessUpiPaymentChargingResponse extends UpiPaymentChargingResponse {
    private PollingConfig pollingConfig;
}