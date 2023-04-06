package in.wynk.payment.dto.gateway.wallet;

import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class AbstractCoreWalletChargingResponse extends AbstractPaymentChargingResponse {
}
