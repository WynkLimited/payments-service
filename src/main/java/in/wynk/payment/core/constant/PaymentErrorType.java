package in.wynk.payment.core.constant;

import in.wynk.exception.IWynkErrorType;
import in.wynk.logging.BaseLoggingMarkers;
import org.slf4j.Marker;
import org.springframework.http.HttpStatus;

public enum PaymentErrorType implements IWynkErrorType {

    APS001("API Failure", "Exception Occurred while calling Aps Server", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.APS_API_FAILURE),
    APS002("Payment Eligibility Failure", "Client is missing in the request", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.APS_API_FAILURE),
    APS003("Card Deletion Failure", "Exception occurred while deleting card from PG", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.APS_SAVED_CARD_DELETION),
    APS004("VPA Deletion Failure", "Exception occurred while deleting vpa from PG", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.APS_SAVED_CARD_DELETION),
    APS005("Aps Recon Transaction Status Pending", "Transaction is still pending at APS", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    APS006("Aps Recon Transaction Status Unknown", "No matching status found from APS", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    APS007("Payment Renewal Timeout", "Timeout occurred while making SI payment on APS", HttpStatus.REQUEST_TIMEOUT, PaymentLoggingMarker.PAYU_RENEWAL_TIMEOUT_ERROR),
    APS008("Payment Renewal Failure", "An Error occurred while making SI payment on APS", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.RENEWAL_STATUS_ERROR),
    APS009("Payment Callback Failure", "Aps payment callback failed.", "${payment.failure.page}", HttpStatus.FOUND, PaymentLoggingMarker.APS_CALLBACK_FAILURE),
    APS010("Aps Callback Parse Failure", "Unable to find order created in database for APS", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.APS_CALLBACK_ORDER_ERROR),
    APS011("Aps Callback Parse Failure", "Unable to parse the request", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.APS_CALLBACK_ORDER_ERROR),
    APS012("Mandate Revoke Failure", "Cancelling Recurring failed at APS side.", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.APS_MANDATE_REVOKE_ERROR),

    /*Common errorCodes start*/
    PAY001("Invalid Payment Method", "unable to charge, unknown payment method is supplied", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY006("Payment Charging Callback Failure", "Something went wrong", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYU_CHARGING_CALLBACK_FAILURE),
    PAY007("Payment Charging Failure", "Exception occurred in charging API ", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.CHARGING_API_FAILURE),
    PAY008("Payment Charging Status Failure", "Invalid Fetching strategy is supplied", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION_FAILURE),
    PAY025("Payment Charging Status Failure", "Exception occurred in data validation", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.APPLICATION_INVALID_USECASE),
    PAY010("Invalid txnId", "Invalid txnId", HttpStatus.NOT_FOUND, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY013("Subscription Provision Failure", "Unable to subscribe plan after successful payment", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.SUBSCRIPTION_ERROR),
    PAY020("Payment Refund init failure", "Failure to refund amount", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYMENT_ERROR),
    PAY016("Subscription unProvision Failure", "Unable to unsubscribe plan after failed payment", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.SUBSCRIPTION_ERROR),
    PAY017("Payment Recurring failure", "Unable to add payment recurring", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYMENT_ERROR),
    PAY022("Payment Options Failure", "Payment Options Failure", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYMENT_OPTIONS_FAILURE),
    PAY023("Payment Options Failure", "Mandatory fields not passed", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYMENT_OPTIONS_FAILURE),
    PAY024("Renewal API Failure", "Could Not process transaction for renewal", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.RENEWAL_API_FAILURE),
    /*Common errorCodes start*/

    /*PhonePay Specific errorCodes start*/
    PAY018("PhonePe Recon Transaction Status Failure", "No matching status found for PhonePe side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY019("PhonePe Recon Transaction Status Failure", "Transaction is still pending from PhonePe side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY021("PhonePe Charging Failure", "PhonePe Charging Failure", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PHONEPE_CHARGING_FAILURE),
    /*PhonePay Specific errorCodes end*/

    /*IAP Specific errorCodes start*/
    PAY011("Verification Failure ", "Failure in validating transaction from itunes", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.ITUNES_VERIFICATION_FAILURE),
    PAY012("Verification Failure ", "Failure in validating transaction from amazon iap", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.AMAZON_IAP_VERIFICATION_FAILURE),
    PAY027("Verification Failure ", "Failure in validating transaction from google play", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.GOOGLE_PLAY_VERIFICATION_FAILURE),
    PAY028("Payment Renewal Failure", "Unable tot renewal google Play subscription", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.GOOGLE_PLAY_RENEWAL_ERROR),
    PAY029("Acknowledgement Failure ", "Failure in acknowledging purchase to google play", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.GOOGLE_PLAY_ACKNOWLEDGEMENT_FAILURE),
    PAY030("Google Play Notification Failure", "Something happened while decoding real time developer notification from Google Play", HttpStatus.INTERNAL_SERVER_ERROR,
            PaymentLoggingMarker.GOOGLE_PLAY_NOTIFICATION_DECODE_ERROR),
    PAY031("Google Play Notification Validation Failure", "No receipt mapping found for the Google Play notification", HttpStatus.BAD_REQUEST,
            PaymentLoggingMarker.GOOGLE_PLAY_NOTIFICATION_VALIDATION_ERROR),
    PAY033("Google Play Notification Processing Failure", "No free plan mapping present for Google Play notification", HttpStatus.INTERNAL_SERVER_ERROR,
            PaymentLoggingMarker.GOOGLE_PLAY_NOTIFICATION_ERROR),
    PAY034("Itunes Notification Processing Failure", "No free plan mapping present for Itunes notification", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.ITUNES_NOTIFICATION_ERROR),
    PAY035("Amazon Iap Notification Processing Failure", "No free plan mapping present for Amazon Iap notification", HttpStatus.INTERNAL_SERVER_ERROR,
            PaymentLoggingMarker.AMAZON_IAP_NOTIFICATION_ERROR),
    PAY050("Google Report Failure ", "Failure in reporting external transaction to google", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.GOOGLE_PLAY_ACKNOWLEDGEMENT_FAILURE),
    PAY026("Payment Renewal Failure", "Unable tot renewal itunes subscription", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.ITUNES_RENEWAL_ERROR),
    PAY701("Validation Failure", "Receipt is already processed", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY702("Validation Failure", "Invalid request with wrong receipt details", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PLAY005("Google Play Renewal Pipeline Failure ", "No new receipt generated by google Play for renewal", HttpStatus.NOT_FOUND, PaymentLoggingMarker.GOOGLE_PLAY_RENEWAL_FAILURE),
    /*IAP Specific errorCodes end*/

    /*PAYU Specific errorCodes*/
    PAY005("Mandate Validation", "Mandate is not Active", HttpStatus.PRECONDITION_FAILED, PaymentLoggingMarker.PAYU_MANDATE_VALIDATION),
    PAY015("PayU API Failure", "Could Not process transaction on payU", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYU_API_FAILURE),
    PAY002("Charging Failure", "Something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYU_CHARGING_FAILURE),
    PAY003("PayU Recon Transaction Status Failure", "No matching status found for payU side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY004("PayU Recon Transaction Status Failure", "Transaction is still pending from payU side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY009("Payment Renewal Failure", "An Error occurred while making SI payment on payU", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYU_RENEWAL_STATUS_ERROR),
    PAY014("Payment Renewal Timeout", "Timeout occurred while making SI payment on payU", HttpStatus.REQUEST_TIMEOUT, PaymentLoggingMarker.PAYU_RENEWAL_TIMEOUT_ERROR),
    PAY045("Payment Renewal Failure", "Unable to renew amazon iap subscription", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.AMAZON_IAP_RENEWAL_ERROR),
    /*PAYU Specific errorCodes end*/

    /*Notification Specific errorCodes start*/
    PAY047("Payment Drop Out Notification Failure", "Unable to schedule the drop out notification", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYMENT_DROP_OUT_NOTIFICATION_FAILURE),
    PAY048("Payment Auto Refund Notification Failure", "Unable to schedule the auto refund notification", HttpStatus.INTERNAL_SERVER_ERROR,
            PaymentLoggingMarker.PAYMENT_AUTO_REFUND_NOTIFICATION_FAILURE),
    /*Notification Specific errorCodes end*/

    PAY103("Paytm Recon Transaction Status Failure", "No matching status found for paytm side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY104("Paytm Recon Transaction Status Failure", "Transaction is still pending from paytm side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY105("Renewal Eligibility API Failure", "Renewal Eligibility API Failure", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.RENEWAL_ELIGIBILITY_API_ERROR),
    PAY106("Invalid Item", "Invalid item is is supplied", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY107("Selective Computation Failed", "Unable to compute selective eligibility for purchase", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_ERROR),

    PAY111("PayU Pre Debit Notification Failure", "Pre Debit Notification Failed at PayU side.", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYU_PRE_DEBIT_NOTIFICATION_ERROR),
    PAY112("PayU UPI Mandate Revoke Failure", "Cancelling Recurring failed at PayU side.", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.PAYU_UPI_MANDATE_REVOKE_ERROR),
    PAY201("Saved Payment Option Failure", "Either this payment option is not currently supported to fetch user saved payments or we are getting timeout from external server",
            HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.SAVED_OPTIONS_TIMED_OUT),
    PAY202("Link Wallet Error", "Unable to find any linked wallet for corresponding uid-paymentCode combination. Try linking a fresh wallet", HttpStatus.BAD_REQUEST,
            PaymentLoggingMarker.LINK_WALLET_ERROR),
    PAY203("Saved Cards Error", "Unable to find any saved cards for corresponding uid-paymentCode combination. Try saving a fresh card", HttpStatus.BAD_REQUEST,
            PaymentLoggingMarker.SAVED_CARDS_ERROR),

    PAY300("Payment Charging Callback Pending", "Transaction is still pending at source", "${payment.pending.page}", HttpStatus.FOUND, PaymentLoggingMarker.PAYMENT_CHARGING_CALLBACK_PENDING),
    PAY301("Payment Charging Callback Failure", "No matching status found at source", "${payment.unknown.page}", HttpStatus.FOUND, PaymentLoggingMarker.PAYMENT_CHARGING_CALLBACK_FAILURE),
    PAY302("Payment Charging Callback Failure", "Payment charging callback failed.", "${payment.failure.page}", HttpStatus.FOUND, PaymentLoggingMarker.PAYMENT_CHARGING_CALLBACK_FAILURE),
    PAY303("APBPaytm Recon Transaction Status Failure", "No matching status found for APBPaytm side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),
    PAY304("APBPaytm Recon Transaction Status Failure", "Transaction is still pending from APBPaytm side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_RECONCILIATION_FAILURE),

    PAY400("Invalid Request", "Invalid request", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY401("Lock Can not be acquired over given id", "Lock Can not be acquired over given id", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),

    PAY440("Invalid Generate Invoice Event Received", "Invoice Generate Event payload is not correct", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.INVALID_INVOICE_EVENT_RECEIVED),
    PAY441("GST State Code Failure", "Unable to find GST State Code from Optimus", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.GST_STATE_CODE_FAILURE),
    PAY442("Invoice Not Found", "Some error occurred in generating invoice. Please contact the customer support", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.INVOICE_DETAILS_NOT_FOUND),
    PAY443("Operator Details Not Found", "Operator details not found for the user", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.OPERATOR_DETAILS_NOT_FOUND),
    PAY444("Invalid Invoice Callback Event Received", "Invoice Callback Event payload is not correct", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.INVALID_INVOICE_EVENT_RECEIVED),
    PAY445("Invoice details not found", "Invoice details not found in DB", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.INVOICE_DETAILS_NOT_FOUND),
    PAY446("Invoice generation failed", "Invoice generation failed, will retry", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.INVOICE_GENERATION_FAILED),
    PAY447("Invoice callback process failed", "Invoice callback process failed", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.INVOICE_PROCESS_CALLBACK_FAILED),
    PAY448("Invoice schedule retry failed", "Unable to schedule the invoice retry", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.INVOICE_SCHEDULE_RETRY_FAILURE),
    PAY449("Invoice trigger retry failed", "Unable to trigger the invoice retry", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.INVOICE_TRIGGER_RETRY_FAILURE),
    PAY450("Kafka Events Consumption Failure", "Event not consumed due to error, something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.KAFKA_CONSUMPTION_HANDLING_ERROR),
    PAY451("Download Invoice Failure", "Invoice not downloaded due to error, something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.DOWNLOAD_INVOICE_ERROR),
    PAY452("Kafka Publisher Failure", "Event not published due to error, something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.KAFKA_PUBLISHER_FAILURE),
    PAY453("Invoice Sequence Details not found", "Unable to find invoice sequence details in mongo for the client", HttpStatus.INTERNAL_SERVER_ERROR,
            PaymentLoggingMarker.INVOICE_SEQUENCE_NOT_CONFIGURED_FOR_CLIENT),
    PAY454("Invoice Number Generation Failed", "Unable to generate invoice number", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.INVOICE_NUMBER_GENERATION_FAILED),
    PAY455("Lock Can not be acquired over given id", "Lock Can not be acquired over given id. Try after some time.", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.LOCK_ACQUIRE_FAILURE),
    PAY501("Invalid Request", "Mandatory data in request is missing.", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),

    /*Validations errorCodes start*/
    PAY601("Validation Failure", "Given Payment Method Is Not Eligible", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY602("Validation Failure", "Trial Plan can not be purchased without auto renew option", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY603("Request Validation Failure", "Given payment method does not support auto renewal", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY604("Validation Failure", "This plan is not eligible for mandate flow", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY605("Validation Failure", "Given plan id is not eligible", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY606("Validation Failure", "Given plan id is not eligible as selective eligibility is null", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY607("Validation Failure", "Trial plan cannot be purchased as no Free trial plan present in db", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY608("Validation Failure", "Both trial and mandate supported can't be true at the same time", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY609("Validation Failure", "Saved Card does not support renewal or mandate or trial opted", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    /*Validations errorCodes end*/

    PAY888("Not Supported", "This service is currently not supported", HttpStatus.NOT_FOUND, PaymentLoggingMarker.NOT_SUPPORTED_SERVICE),
    PAY889("Refund Failure", "Refund process is not supported by the payment partner", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.PAYMENT_REFUND_ERROR),

    /*Point/item specific to bill errorCodes start*/
    PAY994("Validation Failure", "Product is already consumed", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY996("Validation Failure", "Point purchase does not support autoRenew, mandate or trial_subscription  ", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY995("Validation Failure", "Point purchase does not support couponId", HttpStatus.BAD_REQUEST, BaseLoggingMarkers.APPLICATION_INVALID_USECASE),
    PAY997("Point Purchase Failure", "Unable to generate session, something went wrong", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.POINT_PURCHASE_SESSION_INIT_FAILURE),
    /*Point/item specific to bill errorCodes end*/
    PAY998("External Partner failure", "External Partner failure", HttpStatus.SERVICE_UNAVAILABLE, BaseLoggingMarkers.SERVICE_PARTNER_ERROR),
    PAY999("Charging Failure", "Fraud Charging Request for AutoRenew in Direct Payment API", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.CHARGING_API_FAILURE),

    /*Realtime Mandate start*/
    RTMANDATE001("Realtime Mandate Processing Failure", "Could not stop renewal based on realtime mandate as we don't have record for initial transaction id",HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.STOP_RENEWAL_FAILURE),
    RTMANDATE002("Realtime Mandate Processing Failure", "Could not stop renewal based on realtime mandate for IAP",HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.STOP_RENEWAL_FAILURE),
    /*Realtime Mandate start*/

    /*Add to bill errorCodes start*/
    PAY800("Fetching Plan page Url failure", "Unable to get the plan page for DTP", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.BEST_VALUE_PLAN_API_ERROR),
    ATB01("ATB Charging API Failure", "Could Not process transaction on addToBill", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.ADDTOBILL_API_FAILURE),
    ATB02("ATB Status API Failure", "Transaction is still pending from addToBill side", HttpStatus.INTERNAL_SERVER_ERROR, PaymentLoggingMarker.ADDTOBILL_CHARGING_STATUS_VERIFICATION),
    ATB03("ATB Status API Failure", "No matching status found at addToBill side", HttpStatus.BAD_REQUEST, PaymentLoggingMarker.ADDTOBILL_CHARGING_STATUS_VERIFICATION);
    /*Add to bill errorCodes end*/

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
    PaymentErrorType (String errorTitle, String errorMsg, String redirectUrlProp, HttpStatus httpResponseStatus, Marker marker) {
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
    PaymentErrorType (String errorTitle, String errorMsg, HttpStatus httpResponseStatus, Marker marker) {
        this.errorTitle = errorTitle;
        this.errorMsg = errorMsg;
        this.httpResponseStatusCode = httpResponseStatus;
        this.marker = marker;
    }

    public static PaymentErrorType getWynkErrorType (String name) {
        return PaymentErrorType.valueOf(name);
    }

    /**
     * Gets the error code.
     *
     * @return the error code
     */
    @Override
    public String getErrorCode () {
        return this.name();
    }

    /**
     * Gets the error title.
     *
     * @return the error title
     */
    @Override
    public String getErrorTitle () {
        return errorTitle;
    }

    /**
     * Gets the error message.
     *
     * @return the error message
     */
    @Override
    public String getErrorMessage () {
        return errorMsg;
    }


    /**
     * Gets the redirect Url Prop.
     *
     * @return the redirect url prop
     */
    public String getRedirectUrlProp () {
        return redirectUrlProp;
    }

    /**
     * Gets the http response status.
     *
     * @return the http response status
     */
    @Override
    public HttpStatus getHttpResponseStatusCode () {
        return httpResponseStatusCode;
    }

    @Override
    public Marker getMarker () {
        return marker;
    }

    @Override
    public String toString () {
        return "{" + "errorTitle:'" + errorTitle + '\'' + ", errorMsg:'" + errorMsg + '\'' + ", httpResponseStatusCode" + httpResponseStatusCode + '}';
    }

}
