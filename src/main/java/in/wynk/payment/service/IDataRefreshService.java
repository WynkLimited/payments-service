package in.wynk.payment.service;

/**
 * @author Nishesh Pandey
 */
public interface IDataRefreshService {

    void refreshMerchantTableData (String transactionId, String paymentCode);
}
