package in.wynk.payment.dto.manager;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.common.AbstractPaymentChargingResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class ChargingResponseWrapper<T extends AbstractPaymentChargingResponse> extends AbstractPaymentChargingResponse {
    private T pgResponse;
    private Transaction transaction;
    private IPurchaseDetails purchaseDetails;
}
