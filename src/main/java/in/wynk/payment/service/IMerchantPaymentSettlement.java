package in.wynk.payment.service;

import in.wynk.payment.dto.request.AbstractPaymentSettlementRequest;
import in.wynk.payment.dto.response.AbstractPaymentSettlementResponse;

public interface IMerchantPaymentSettlement<R extends AbstractPaymentSettlementResponse, T extends AbstractPaymentSettlementRequest> {
    R settle(T request);
}
