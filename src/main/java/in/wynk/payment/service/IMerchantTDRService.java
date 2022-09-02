package in.wynk.payment.service;

import in.wynk.payment.dto.BaseTDRResponse;
public interface IMerchantTDRService {
    BaseTDRResponse getTDR(String transactionId);
}
