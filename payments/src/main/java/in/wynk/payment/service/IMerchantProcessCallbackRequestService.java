package in.wynk.payment.service;

import java.util.Map;

public interface IMerchantProcessCallbackRequestService {
    String getTxnId(Map<String, Object> payload);
}