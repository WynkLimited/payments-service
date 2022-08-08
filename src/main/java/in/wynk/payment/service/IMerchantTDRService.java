package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.payu.PayUTdrResponse;

public interface IMerchantTDRService {
    PayUTdrResponse getTDR(String transactionId );
}
