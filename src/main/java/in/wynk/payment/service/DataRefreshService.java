package in.wynk.payment.service;

/**
 * @author Nishesh Pandey
 */
public interface DataRefreshService {

    void refreshMerchantTableData (String transactionId, String paymentCode);
}
