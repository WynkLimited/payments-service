package in.wynk.payment.service;

import in.wynk.common.dto.EmptyResponse;
import org.springframework.http.HttpHeaders;

/**
 * @author Nishesh Pandey
 */
public interface IDataRefreshService {

    void refreshMerchantTableData (String transactionId, String paymentCode);

    void handleCallback (String partner, String applicationAlias, HttpHeaders headers, String payload);

    void handleCallback (String applicationAlias, HttpHeaders headers, String txnId);
}
