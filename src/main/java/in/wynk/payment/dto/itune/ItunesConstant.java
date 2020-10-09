package in.wynk.payment.dto.itune;

import java.util.Arrays;
import java.util.List;

public interface ItunesConstant {

    String PRODUCT_ID = "product_id";
    String ORIGINAL_TRANSACTION_ID = "original_transaction_id";
    String TRANSACTION_ID = "transaction_id";
    String STATUS = "status";
    String RECEIPT_DATA = "receipt-data";
    String PASSWORD = "password";
    String LATEST_RECEIPT_INFO = "latest_receipt_info";
    String PURCHASE_DATE_MS = "purchase_date_ms";
    String EXPIRES_DATE = "expires_date";
    String EXPIRES_DATE_MS = "expires_date_ms";
    String PURCHASE_INFO = "purchase-info";
    String DECODED_RECEIPT = "decodedReceipt";
    String LATEST_RECEIPT = "latestReceipt";

    List<ItunesStatusCodes> FAILURE_CODES = Arrays.asList(ItunesStatusCodes.APPLE_21000, ItunesStatusCodes.APPLE_21002, ItunesStatusCodes.APPLE_21003, ItunesStatusCodes.APPLE_21004, ItunesStatusCodes.APPLE_21005, ItunesStatusCodes.APPLE_21007, ItunesStatusCodes.APPLE_21008, ItunesStatusCodes.APPLE_21009, ItunesStatusCodes.APPLE_21010);

}