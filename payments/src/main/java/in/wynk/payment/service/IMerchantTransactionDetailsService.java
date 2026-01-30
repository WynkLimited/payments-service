package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.MerchantTransaction;

import java.util.Map;

public interface IMerchantTransactionDetailsService {

    MerchantTransaction getMerchantTransactionDetails(Map<String, String> params);

}
