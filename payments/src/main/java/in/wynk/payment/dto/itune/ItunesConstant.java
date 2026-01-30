package in.wynk.payment.dto.itune;

import java.util.Arrays;
import java.util.List;

public interface ItunesConstant {

    String STATUS = "status";
    String PASSWORD = "password";
    String RECEIPT_DATA = "receipt-data";
    String RECEIPT_TYPE = "RECEIPT_TYPE";
    String PURCHASE_INFO = "purchase-info";
    String ALL_ITUNES_RECEIPT = "allItunesReceipt";
    String LATEST_EXPIRED_RECEIPT_INFO = "latest_expired_receipt_info";
    String LATEST_RECEIPT_INFO = "latest_receipt_info";
    List<ItunesStatusCodes> ALTERNATE_URL_FAILURE_CODES = Arrays.asList(ItunesStatusCodes.APPLE_21007, ItunesStatusCodes.APPLE_21008);
    List<String> NOTIFICATIONS_TYPE_ALLOWED = Arrays.asList("DID_RENEW", "DID_CHANGE_RENEWAL_STATUS", "INTERACTIVE_RENEWAL", "DID_RECOVER", "CANCEL");
    List<ItunesStatusCodes> FAILURE_CODES = Arrays.asList(ItunesStatusCodes.APPLE_21000, ItunesStatusCodes.APPLE_21002, ItunesStatusCodes.APPLE_21003, ItunesStatusCodes.APPLE_21004, ItunesStatusCodes.APPLE_21005, ItunesStatusCodes.APPLE_21006, ItunesStatusCodes.APPLE_21007, ItunesStatusCodes.APPLE_21008, ItunesStatusCodes.APPLE_21009, ItunesStatusCodes.APPLE_21010);

}