package in.wynk.payment.dto.gpbs;

/**
 * @author Nishesh Pandey
 */
public enum GooglePlayStatusCodes {

    GOOGLE_31000("The App Store could not read the JSON object you provided.", 31000),
    GOOGLE_31002("The data in the purchase token property was malformed or missing.", 31002),
    GOOGLE_31003("The receipt could not be authenticated.", 31003),
    GOOGLE_31004("The shared secret you provided does not match the shared secret on file for your account..", 31004),
    GOOGLE_31005("The receipt server is not currently available.", 31005),
    GOOGLE_31006("This receipt is valid but the subscription has expired. When this status code is returned to your server, the receipt data is also decoded and returned as part of the response. Only returned for iOS 6-style transaction receipts for auto-renewable subscriptions." , 31006),
    GOOGLE_31007("This receipt is from the test environment, but it was sent to the production environment for verification. Send it to the test environment instead.", 31007),
    GOOGLE_31008("This receipt is from the production environment, but it was sent to the test environment for verification. Send it to the production environment instead.", 31008),
    GOOGLE_31009("Internal data access error. Try again later.", 31009),
    GOOGLE_31010("The user account cannot be found or has been deleted.", 31010),
    GOOGLE_31015("Latest receipt is expired", 31015),
    GOOGLE_31018("No google play receipt found for selected plan", 31018),
    GOOGLE_31019("User has cancelled the subscription from app store", 31019),
    GOOGLE_31020("Given plan id is invalid for the service", 31020);

    private final String errorTitle;
    private final int errorCode;

    GooglePlayStatusCodes(String errorTitle, int errorCode) {
        this.errorTitle = errorTitle;
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorTitle() {
        return errorTitle;
    }

    public static GooglePlayStatusCodes getGooglePlayStatusCodes(int id) {
        for(GooglePlayStatusCodes statusCode : values()) {
            if(statusCode.getErrorCode() == id) {
                return statusCode;
            }
        }
        return null;
    }
}
