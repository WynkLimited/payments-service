package in.wynk.payment.enums;

import in.wynk.payment.service.impl.ITunesMerchantPaymentService;

public enum ItunesStatusCodes {
    /* 21000 */
    APPLE_21000("he App Store could not read the JSON object you provided.", 21000),
    /* 21002 */
    APPLE_21002("The data in the receipt-data property was malformed or missing.", 21002),
    /* 21003 */
    APPLE_21003("The receipt could not be authenticated.", 21003),
    /* 21004 */
    APPLE_21004("The shared secret you provided does not match the shared secret on file for your account..", 21004),
    /* 21005 */
    APPLE_21005("The receipt server is not currently available.", 21005),
    /* 21006 */
    APPLE_21006(
            "This receipt is valid but the subscription has expired. When this status code is returned to your server, the receipt data is also decoded and returned as part of the response.",
            21006),
    /* 21007 */
    APPLE_21007("This receipt is from the test environment, but it was sent to the production environment for verification. Send it to the test environment instead.", 21007),
    /* 21008 */
    APPLE_21008("This receipt is from the production environment, but it was sent to the test environment for verification. Send it to the production environment instead.", 21008);

    private String errorTitle;
    private int    errorCode;

    private ItunesStatusCodes(String errorTitle, int errorCode) {
        this.errorTitle = errorTitle;
        this.errorCode = errorCode;

    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorTitle() {
        return errorTitle;
    }

    public static ItunesStatusCodes getItunesStatusCodes(int id) {
        for(ItunesStatusCodes statusCode : values()) {
            if(statusCode.getErrorCode() == id) {
                return statusCode;
            }
        }
        return null;
    }
}


