package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TransactionDetails {
    private Transaction transaction;
    private IPurchaseDetails purchaseDetails;
    private AbstractPaymentChargingRequest request;
}
