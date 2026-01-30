package in.wynk.payment.service;

import in.wynk.payment.dto.request.AbstractTransactionInitRequest;

public interface IPricingManager {

    void computePriceAndApplyDiscount(AbstractTransactionInitRequest request);

}
