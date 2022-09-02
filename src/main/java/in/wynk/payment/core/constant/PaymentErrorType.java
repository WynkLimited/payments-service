package in.wynk.payment.core.constant;

import in.wynk.exception.IWynkErrorType;
import in.wynk.logging.BaseLoggingMarkers;
import org.slf4j.Marker;
import org.springframework.http.HttpStatus;

public enum PaymentErrorType implements IWynkErrorType {

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
    PAY016("Subscription unProvision Failure", "Unable to unsubscribe plan after failed payment", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.SUBSCRIPTION_ERROR),
    PAY017("Payment Recurring failure", "Unable to add payment recurring", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYMENT_ERROR),
    PAY018("PhonePe Recon Transaction Status Failure", "No matching status found for PhonePe side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY019("PhonePe Recon Transaction Status Failure", "Transaction is still pending from PhonePe side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY020("Payment Refund init failure", "Failure to refund amount", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYMENT_ERROR),
    PAY021("PhonePe Charging Failure", "PhonePe Charging Failure", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PHONEPE_CHARGING_FAILURE),
    PAY022("Payment Options Failure", "Payment Options Failure", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYMENT_OPTIONS_FAILURE),
    PAY023("Payment Options Failure", "Mandatory fields not passed", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYMENT_OPTIONS_FAILURE),
    PAY024("APS API Failure", "Could Not process transaction on airtel pay stack", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.APS_API_FAILURE),
    PAY025("Aps Recon Transaction Status Failure", "No matching status found for payU side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY026("Payment Renewal Failure", "Unable tot renewal itunes subscription", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.ITUNES_RENEWAL_ERROR),

    PAY103("Paytm Recon Transaction Status Failure", "No matching status found for paytm side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY104("Paytm Recon Transaction Status Failure", "Transaction is still pending from paytm side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY105("Renewal Eligibility API Failure", "Renewal Eligibility API Failure", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.RENEWAL_ELIGIBILITY_API_ERROR),
    PAY106("Invalid Item", "Invalid item is is supplied", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY107("Selective Computation Failed", "Unable to compute selective eligibility for purchase", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_ERROR),

    PAY111("PayU Pre Debit Notification Failure", "Pre Debit Notification Failed at PayU side.", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYU_PRE_DEBIT_NOTIFICATION_ERROR),
    PAY112("PayU UPI Mandate Revoke Failure", "Cancelling Recurring failed at PayU side.", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYU_UPI_MANDATE_REVOKE_ERROR),
    PAY201("Saved Payment Option Failure", "Either this payment option is not currently supported to fetch user saved payments or we are getting timeout from external server", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.SAVED_OPTIONS_TIMED_OUT),
    PAY202("Link Wallet Error", "Unable to find any linked wallet for corresponding uid-paymentCode combination. Try linking a fresh wallet", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.LINK_WALLET_ERROR),
    PAY203("Saved Cards Error", "Unable to find any saved cards for corresponding uid-paymentCode combination. Try saving a fresh card", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.SAVED_CARDS_ERROR),

    PAY300("Payment Charging Callback Pending", "Transaction is still pending at source", "${payment.pending.page}", HttpStatus.FOUND, PaymentLoggingMarker.PAYMENT_CHARGING_CALLBACK_PENDING),
    PAY301("Payment Charging Callback Failure", "No matching status found at source", "${payment.unknown.page}", HttpStatus.FOUND, PaymentLoggingMarker.PAYMENT_CHARGING_CALLBACK_FAILURE),
    PAY302("Payment Charging Callback Failure", "Payment Failed.", "${payment.failure.page}", HttpStatus.FOUND, PaymentLoggingMarker.PAYMENT_CHARGING_CALLBACK_FAILURE),
    PAY303("APBPaytm Recon Transaction Status Failure", "No matching status found for APBPaytm side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY304("APBPaytm Recon Transaction Status Failure", "Transaction is still pending from APBPaytm side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),

    PAY400("Invalid Request", "Invalid request", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY401("Lock Can not be acquired over given id", "Lock Can not be acquired over given id", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),

    PAY601("Validation Failure", "Given Payment Method Is Not Eligible", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY602("Validation Failure", "Given Plan Id Is Not Eligible", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),

    PAY701("Validation Failure", "Receipt is already processed", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),

    PAY888("Not Supported", "This service is currently not supported", HttpStatus.NOT_FOUND, PaymentLoggingMarker.NOT_SUPPORTED_SERVICE),
    PAY889("Refund Failure", "Refund process is not supported by the payment partner", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_REFUND_ERROR),

    PAY997("Point Purchase Failure", "Unable to generate session, something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.POINT_PURCHASE_SESSION_INIT_FAILURE),
    PAY998("External Partner failure", "External Partner failure", HttpStatus.SERVICE_UNAVAILABLE, BaseLoggingMarkers.SERVICE_PARTNER_ERROR),

    ATB01("ATB Charging API Failure", "Could Not process transaction on addToBill", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.ADDTOBILL_API_FAILURE),
    ATB02("ATB Status API Failure", "Transaction is still pending from addToBill side", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.ADDTOBILL_CHARGING_STATUS_VERIFICATION),
    ATB03("ATB Status API Failure", "No matching status found at addToBill side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.ADDTOBILL_CHARGING_STATUS_VERIFICATION),;

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
    private final HttpStatus httpResponseStatusCode;
    private final Marker marker;
    /**
     * The http response status.
     */
    private String redirectUrlProp;

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
