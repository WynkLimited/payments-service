package in.wynk.payment.core.constant;

import in.wynk.exception.IWynkErrorType;
import in.wynk.logging.BaseLoggingMarkers;
import org.slf4j.Marker;
import org.springframework.http.HttpStatus;

public enum PaymentErrorType implements IWynkErrorType {

    /**
     * PAYU ERROR CODES
     **/
    PAY001("Invalid Payment Method", "unable to charge, unknown payment method is supplied", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY002("Charging Failure", "Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYU_CHARGING_FAILURE),
    PAY003("PayU Recon Transaction Status Failure", "No matching status found for payU side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY004("PayU Recon Transaction Status Failure", "Transaction is still pending from payU side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY005("Invalid Payment Method", "Unknown payment method is supplied", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY006("Payment Charging Callback Failure", "Something went wrong", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYU_CHARGING_CALLBACK_FAILURE),
    PAY008("Payment Charging Status Failure", "Invalid Fetching strategy is supplied", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION_FAILURE),
    PAY009("Payment Renewal Failure", "An Error occurred while making SI payment on payU", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYU_RENEWAL_STATUS_ERROR),
    PAY010("Invalid txnId", "Invalid txnId", HttpStatus.NOT_FOUND, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY011("Verification Failure ", "Failure in validating transaction from itunes", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE),
    PAY012("Verification Failure ", "Failure in validating transaction from amazon iap", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.AMAZON_IAP_VERIFICATION_FAILURE),
    PAY013("Subscription Provision Failure", "Unable to subscribe plan after successful payment", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.SUBSCRIPTION_ERROR),
    PAY014("Payment Renewal Timeout", "Timeout occurred while making SI payment on payU", HttpStatus.REQUEST_TIMEOUT, PaymentLoggingMarker.PAYU_RENEWAL_TIMEOUT_ERROR),
    PAY015("PayU API Failure", "Could Not process transaction on payU", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYU_API_FAILURE),
    PAY018("PhonePe Recon Transaction Status Failure", "No matching status found for PhonePe side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY019("PhonePe Recon Transaction Status Failure", "Transaction is still pending from PhonePe side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY400("Invalid Request", "Invalid request", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY998("External Partner failure", "External Partner failure", HttpStatus.SERVICE_UNAVAILABLE, BaseLoggingMarkers.SERVICE_PARTNER_ERROR),
    PAY016("Subscription unProvision Failure", "Unable to unsubscribe plan after failed payment", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.SUBSCRIPTION_ERROR),
    PAY017("Payment Recurring failure", "Unable to add payment recurring", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYMENT_ERROR),
    PAY020("Payment Refund init failure", "Failure to refund amount", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYMENT_ERROR),
    PAY997("Point Purchase Failure", "Unable to generate session, something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.POINT_PURCHASE_SESSION_INIT_FAILURE),
    PAY888("Not Supported", "This service is currently not supported", HttpStatus.NOT_FOUND, PaymentLoggingMarker.NOT_SUPPORTED_SERVICE),
    PAY889("Refund Failure", "Refund process is not supported by the payment partner", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_REFUND_ERROR),
    PYA887("Transaction Reconciliation Failure", "Failed to reconcile refund", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    /**
     * Payment Redirect webview code
     */
    PAY300("Payment Charging Callback Pending", "Transaction is still pending at source", "${payment.pending.page}", HttpStatus.FOUND, PaymentLoggingMarker.PAYMENT_CHARGING_CALLBACK_PENDING),
    PAY301("Payment Charging Callback Failure", "No matching status found at source", "${payment.unknown.page}", HttpStatus.FOUND, PaymentLoggingMarker.PAYMENT_CHARGING_CALLBACK_FAILURE),
    PAY302("Payment Charging Callback Failure", "Payment Failed.", "${payment.failure.page}", HttpStatus.FOUND, PaymentLoggingMarker.PAYMENT_CHARGING_CALLBACK_FAILURE);


    /**
     * The error title.
     */
    private final String errorTitle;

    /**
     * The error msg.
     */
    private final String errorMsg;

    /**
     * The http response status.
     */
    private String redirectUrlProp;

    /**
     * The http response status.
     */
    private final HttpStatus httpResponseStatusCode;

    private final Marker marker;

    /**
     * Instantiates a new wynk error type.
     *
     * @param errorTitle         the error title
     * @param errorMsg           the error msg
     * @param httpResponseStatus the http response status
     */
    PaymentErrorType(String errorTitle, String errorMsg, String redirectUrlProp, HttpStatus httpResponseStatus, Marker marker) {
        this.errorTitle = errorTitle;
        this.errorMsg = errorMsg;
        this.redirectUrlProp = redirectUrlProp;
        this.httpResponseStatusCode = httpResponseStatus;
        this.marker = marker;
    }


    /**
     * Instantiates a new wynk error type.
     *
     * @param errorTitle         the error title
     * @param errorMsg           the error msg
     * @param httpResponseStatus the http response status
     */
    PaymentErrorType(String errorTitle, String errorMsg, HttpStatus httpResponseStatus, Marker marker) {
        this.errorTitle = errorTitle;
        this.errorMsg = errorMsg;
        this.httpResponseStatusCode = httpResponseStatus;
        this.marker = marker;
    }

    public static PaymentErrorType getWynkErrorType(String name) {
        return PaymentErrorType.valueOf(name);
    }

    /**
     * Gets the error code.
     *
     * @return the error code
     */
    @Override
    public String getErrorCode() {
        return this.name();
    }

    /**
     * Gets the error title.
     *
     * @return the error title
     */
    @Override
    public String getErrorTitle() {
        return errorTitle;
    }

    /**
     * Gets the error message.
     *
     * @return the error message
     */
    @Override
    public String getErrorMessage() {
        return errorMsg;
    }


    /**
     * Gets the redirect Url Prop.
     *
     * @return the redirect url prop
     */
    public String getRedirectUrlProp() {
        return redirectUrlProp;
    }

    /**
     * Gets the http response status.
     *
     * @return the http response status
     */
    @Override
    public HttpStatus getHttpResponseStatusCode() {
        return httpResponseStatusCode;
    }

    @Override
    public Marker getMarker() {
        return marker;
    }

    @Override
    public String toString() {
        return "{" + "errorTitle:'" + errorTitle + '\'' + ", errorMsg:'" + errorMsg + '\'' + ", httpResponseStatusCode" + httpResponseStatusCode + '}';
    }

}
