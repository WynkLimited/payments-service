package in.wynk.payment.gateway;

import in.wynk.payment.dto.request.AbstractPaymentSettlementRequest;
import in.wynk.payment.dto.response.AbstractPaymentSettlementResponse;

public interface IPaymentSettlement<R extends AbstractPaymentSettlementResponse, T extends AbstractPaymentSettlementRequest> {
    R settle(T request);
}
