package in.wynk.payment.dto.itune;

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
    APPLE_21006("This receipt is valid but the subscription has expired. When this status code is returned to your server, the receipt data is also decoded and returned as part of the response. Only returned for iOS 6-style transaction receipts for auto-renewable subscriptions." , 21006),
    /* 21007 */
    APPLE_21007("This receipt is from the test environment, but it was sent to the production environment for verification. Send it to the test environment instead.", 21007),
    /* 21008 */
    APPLE_21008("This receipt is from the production environment, but it was sent to the test environment for verification. Send it to the production environment instead.", 21008),
    /* APPLE_21009 */
    APPLE_21009("Internal data access error. Try again later.", 21009),
    /* APPLE_21010 */
    APPLE_21010("The user account cannot be found or has been deleted.", 21010),

    /* Internal Codes for Itunes */
    /* APPLE_21011 */
    APPLE_21011("Encoded iTunes receipt data is empty!", 21011),
    /* APPLE_21012 */
    APPLE_21012("Receipt Object returned from iTunes is null", 21012),
    /* APPLE_21013 */
    APPLE_21013("Unable to post receipt over iTunes server", 21013),
    /* APPLE_21014 */
    APPLE_21014("Something went wrong", 21014),
    /* APPLE_21015 */
    APPLE_21015("Latest receipt is expired", 21015),
    /* APPLE_21016 */
    APPLE_21016("Already have subscription for the corresponding iTunes id", 21016),
    /* APPLE_21017 */
    APPLE_21017("Itunes transaction Id not found", 21017),
    /* APPLE_21018 */
    APPLE_21018("No itunes receipt found for selected plan", 21018),
    /* APPLE_21019 */
    APPLE_21019("User has cancelled the subscription from app store", 21019);

    private final String errorTitle;
    private final int errorCode;

    ItunesStatusCodes(String errorTitle, int errorCode) {
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


