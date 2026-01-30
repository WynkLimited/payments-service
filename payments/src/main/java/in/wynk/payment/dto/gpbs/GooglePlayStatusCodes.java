package in.wynk.payment.dto.gpbs;

/**
 * @author Nishesh Pandey
 */
public enum GooglePlayStatusCodes {

    GOOGLE_31000("The Subscription is on hold or paused.", 31000),
    GOOGLE_31001("The Subscription has been restarted.", 31001),
    GOOGLE_31002("The Subscription has been recovered.", 31002),
    GOOGLE_31003("The Subscription has been revoked.", 31003),
    GOOGLE_31004("The Subscription is expired or entered into grace period.", 31004),
    GOOGLE_31005("This receipt is valid but the subscription has expired. When this status code is returned to your server, the receipt data is also decoded and returned as part of the response. Only returned for iOS 6-style transaction receipts for auto-renewable subscriptions." , 31006),
    GOOGLE_31006("No receipt found for the notification.", 31006),
    GOOGLE_31018("No google play receipt found for selected plan", 31018),
    GOOGLE_31019("User has cancelled the subscription from app store", 31019),
    GOOGLE_31020("Given sku id is invalid for the service", 31020),
    GOOGLE_31021("Receipt is already processed", 31021),
    GOOGLE_31022("Subscription is auto renewed", 31022),
    GOOGLE_31023("Subscription is not renewed from google", 31023);


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
