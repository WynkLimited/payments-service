package in.wynk.payment.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Deprecated
public enum ErrorCode {

    PAYTM001("PAYTM001", "1", "Txn Successful.", "Txn Successful."),
    PAYTM002("PAYTM002", "10", "Refund successful", "Refund successful"),
    PAYTM003("PAYTM003", "118", "The transaction amount specified by the user exceeds the per transaction limit for this merchant.", "The transaction amount specified by the user exceeds the per transaction limit for this merchant."),
    PAYTM004("PAYTM004", "130", "This user is blocked at Paytm end", "This user is blocked at Paytm end"),
    PAYTM005("PAYTM005", "151", "Transaction with the same order Id already exists", "Transaction with the same order Id already exists"),
    PAYTM006("PAYTM006", "227", "Transaction failed. Your payment has been declined by your bank. Please contact your bank for any queries. If money has been deducted from your account, your bank will inform us within 48 hrs and we will refund the same.", "Transaction failed. Your payment has been declined by your bank. Please contact your bank for any queries. If money has been deducted from your account, your bank will inform us within 48 hrs and we will refund the same."),
    PAYTM007("PAYTM007", "235", "Wallet balance Insufficient. You don't have sufficient balance in your account. Please try with a different account.", "Wallet balance Insufficient. You don't have sufficient balance in your account. Please try with a different account."),
    PAYTM008("PAYTM008", "237", "Could not complete request. Please retry again.", "Could not complete request. Please retry again."),
    PAYTM009("PAYTM009", "239", "Merchant does not exist", "Merchant does not exist"),
    PAYTM010("PAYTM010", "240", "Invalid total amount", "Invalid total amount"),
    PAYTM011("PAYTM011", "243", "Wallet not exist for the user", "Wallet not exist for the user"),
    PAYTM012("PAYTM012", "260", "Maximum allowed amount in Wallet exceeds limit", "Maximum allowed amount in Wallet exceeds limit"),
    PAYTM013("PAYTM013", "267", "User does not exist", "User does not exist"),
    PAYTM014("PAYTM014", "274", "User not verified", "User not verified"),
    PAYTM015("PAYTM015", "295", "Your payment failed as the UPI ID entered is incorrect. Please try again by entering a valid VPA or use a different method to complete the payment.", "Your payment failed as the UPI ID entered is incorrect. Please try again by entering a valid VPA or use a different method to complete the payment."),
    PAYTM016("PAYTM016", "305", "Merchant Id not registered.", "Merchant Id not registered."),
    PAYTM017("PAYTM017", "325", "Duplicate order id", "Duplicate order id"),
    PAYTM018("PAYTM018", "327", "Channel is not associated", "Channel is not associated"),
    PAYTM019("PAYTM019", "330", "Paytm checksum mismatch. Checksum provided is invalid", "Paytm checksum mismatch. Checksum provided is invalid"),
    PAYTM020("PAYTM020", "334", "Invalid Order Id.", "Invalid Order Id."),
    PAYTM021("PAYTM021", "335", "Mid is invalid", "Mid is invalid"),
    PAYTM022("PAYTM022", "343", "Invalid Token", "Invalid Token"),
    PAYTM023("PAYTM023", "344", "Invalid wallet type", "Invalid wallet type"),
    PAYTM024("PAYTM024", "345", "Request not unique", "Request not unique"),
    PAYTM025("PAYTM025", "400", "Bad request", "Bad request"),
    PAYTM026("PAYTM026", "401", "Unauthorized", "Unauthorized"),
    PAYTM027("PAYTM027", "402", "Looks like the payment is not complete. Please wait while we confirm the status with your bank.", "Looks like the payment is not complete. Please wait while we confirm the status with your bank."),
    PAYTM028("PAYTM028", "403", "Unauthorized Access, scope is not refreshable", "Unauthorized Access, scope is not refreshable"),
    PAYTM029("PAYTM029", "434", "Bad request", "Bad request"),
    PAYTM030("PAYTM030", "501", "System Error", "System Error"),
    PAYTM031("PAYTM031", "530", "Invalid Token", "Invalid Token"),
    PAYTM032("PAYTM032", "600", "Invalid refund request.", "Invalid refund request."),
    PAYTM033("PAYTM033", "601", "Refund request was raised for this transaction. But it is pending state.", "Refund request was raised for this transaction. But it is pending state."),
    PAYTM034("PAYTM034", "607", "Refund can not be initiated for a cancelled transaction.", "Refund can not be initiated for a cancelled transaction."),
    PAYTM035("PAYTM035", "617", "Refund Already Raised (If merchant repeated their request within the 10 minutes after initiate the first refund request)", "Refund Already Raised (If merchant repeated their request within the 10 minutes after initiate the first refund request)"),
    PAYTM036("PAYTM036", "619", "Invalid refund amount", "Invalid refund amount"),
    PAYTM037("PAYTM037", "620", "BALANCE_NOT_ENOUGH", "BALANCE_NOT_ENOUGH"),
    PAYTM038("PAYTM038", "626", "Another Refund on same order is already in Progress, please retry after few minutes", "Another Refund on same order is already in Progress, please retry after few minutes"),
    PAYTM039("PAYTM039", "627", "Order Details Mismatch", "Order Details Mismatch"),
    PAYTM040("PAYTM040", "628", "Refund request was raised to respective bank. But it is in pending state from bank side.", "Refund request was raised to respective bank. But it is in pending state from bank side."),
    PAYTM041("PAYTM041", "629", "Refund is already Successful", "Refund is already Successful"),
    PAYTM042("PAYTM042", "631", "Record not found", "Record not found"),
    PAYTM043("PAYTM043", "635", "Partial Refund under Rupee 1 is not allowed", "Partial Refund under Rupee 1 is not allowed"),
    PAYTM044("PAYTM044", "712", "Applying Promo Code Failed", "Applying Promo Code Failed"),
    PAYTM045("PAYTM045", "810", "Txn Failed", "Txn Failed"),
    PAYTM046("PAYTM046", "BE1400001", "Success", "Success"),
    PAYTM047("PAYTM047", "BE1422001", "scope is not refreshable, when some token is requested for refresh and is not refreshable.", "scope is not refreshable, when some token is requested for refresh and is not refreshable."),
    PAYTM048("PAYTM048", "BE1422002", "invalid refresh token", "invalid refresh token"),
    PAYTM049("PAYTM049", "BE1423001", "authorization failed, token format not supported, illegal parameters etc.", "authorization failed, token format not supported, illegal parameters etc."),
    PAYTM050("PAYTM050", "BE1423003", "The grant type is not given to the client", "The grant type is not given to the client"),
    PAYTM051("PAYTM051", "BE1423004", "Authorization is invalid", "Authorization is invalid"),
    PAYTM052("PAYTM052", "BE1423005", "Invalid Authorization Code", "Invalid Authorization Code"),
    PAYTM053("PAYTM053", "BE1423006", "Client permission not found", "Client permission not found"),
    PAYTM054("PAYTM054", "BE1423011", "Authorization client and state token client mismatch", "Authorization client and state token client mismatch"),
    PAYTM055("PAYTM055", "BE1423012", "Device Identifier is missing", "Device Identifier is missing"),
    PAYTM056("PAYTM056", "BE1423013", "Device Identifier is mismatch", "Device Identifier is mismatch"),
    PAYTM057("PAYTM057", "BE1424001", "We have found suspicious activity from this number. Therefore, we have blocked this account. Please raise a request at paytm.com/care to unblock your account.", "We have found suspicious activity from this number. Therefore, we have blocked this account. Please raise a request at paytm.com/care to unblock your account."),
    PAYTM058("PAYTM058", "BE1425004", "Mobile number is already pending for verification. Please try after 48 hours.", "Mobile number is already pending for verification. Please try after 48 hours."),
    PAYTM059("PAYTM059", "BE1425005", "Scope not allowed", "Scope not allowed"),
    PAYTM060("PAYTM060", "BE1425006", "Oops! You have reached an OTP limit, please raise a query at paytm.com/care.", "Oops! You have reached an OTP limit, please raise a query at paytm.com/care."),
    PAYTM061("PAYTM061", "BE1425007", "Please enter a valid OTP", "Please enter a valid OTP"),
    PAYTM062("PAYTM062", "BE1425008", "You have exceeded the number of attempts for entering a valid OTP. Please click Resend to continue with new OTP.", "You have exceeded the number of attempts for entering a valid OTP. Please click Resend to continue with new OTP."),
    PAYTM063("PAYTM063", "BE1426003", "There was some issue in processing this request", "There was some issue in processing this request"),
    PAYTM064("PAYTM064", "BE1426011", "We have found a suspicious activity from this number. Therefore, we have blocked your account. Please raise a request at paytm.com/care. The response code may change.", "We have found a suspicious activity from this number. Therefore, we have blocked your account. Please raise a request at paytm.com/care. The response code may change."),
    PAYTM065("PAYTM065", "BE1526000", "internal server error", "internal server error"),
    PAYTM066("PAYTM066", "FI_0001", "Invalid Request.", "Invalid Request."),
    PAYTM067("PAYTM067", "FI_0002", "Merchant is on agreement pay.", "Merchant is on agreement pay."),
    PAYTM068("PAYTM068", "FI_0003", "Invalid Token.", "Invalid Token."),
    PAYTM069("PAYTM069", "GE_0003", "We could not get the requested details. Please try again.", "We could not get the requested details. Please try again."),
    PAYTM070("PAYTM070", "GE_3", "Internal server error.", "Internal server error."),
    PAYTM071("PAYTM071", "NA", "Client permission not found", "Client permission not found"),
    PAYTM072("PAYTM072", "WM_1003", "Merchant does not exist.", "Merchant does not exist."),

    PHONEPE001("PHONEPE001", "AUTHORIZATION_FAILED", "Unauthorized", "Value of X-VERIFY is incorrect."),
    PHONEPE002("PHONEPE002", "BAD_REQUEST", "please provide valid details.", "Invalid request payload."),
    PHONEPE003("PHONEPE003", "INTERNAL_SERVER_ERROR", "Internal server error. Try again after sometime.", "There is an error trying to process your transaction at the moment. Please try again in a while."),
    PHONEPE004("PHONEPE004", "USER_BLACKLISTED", "PhonePe has blocked your account. Try another payment method", "Customer is blacklisted on PhonePe side."),
    PHONEPE005("PHONEPE005", "USER_BLOCKED", "Your PhonePe account is blocked for 24 hours. Try another payment method.", "Too many incorrect attempts. Please try again after 24 hours."),
    PHONEPE006("PHONEPE006", "OTP_LIMIT_EXCEEDED", "You cannot request OTP anymore in this session as your limit has expired. Try again after sometime.", "There is a limit on number of times OTP can be sent on a mobile number. This code will be received is that limit is crossed."),
    PHONEPE007("PHONEPE007", "TOO_MANY_REQUESTS", "Oops! something went wrong. Try again after sometime or try another payment method", "Getting too many requests from merchant for this API."),
    PHONEPE008("PHONEPE008", "INVALID_OTP_TOKEN", "Something went wrong. Try again / Resend OTP", "OTP token is not valid or expired."),
    PHONEPE009("PHONEPE009", "OTP_VERIFY_FAILED", "Invalid OTP. Enter the correct OTP.", "OTP is not valid"),
    PHONEPE010("PHONEPE010", "OTP_EXPIRED", "OTP has expired. Resend OTP", "Your OTP has expired. Please regenerate your otp."),
    PHONEPE011("PHONEPE011", "OTP_ALREADY_VERIFIED", "The OTP has been already verified", "The OTP has been already verified."),
    PHONEPE012("PHONEPE012", "INVALID_DEVICE_ID", "Device id mismatch. Please relink PhonePe wallet on the current device.", "The device id you have provided seems to be invalid."),
    PHONEPE013("PHONEPE013", "WALLET_RELINK_REQUIRED", "We have found suspicious activity from this number. Therefore, we have blocked your account. Please relink PhonePe wallet on the current device.", "Fraud suspected. Please relink PhonePe wallet on the current device."),
    PHONEPE014("PHONEPE014", "WALLET_NOT_ACTIVATED", "Incomplete KYC on Phonepe. Complete your KYC to use your PhonePe wallet or try another method.", "As per RBI guidelines, please complete your KYC to use your PhonePe wallet."),
    PHONEPE015("PHONEPE015", "WALLET_LIMIT_BREACHED", "Transaction or top-up exceeds the user’s debit limit or credit limit.", "Transaction or top-up  exceeds the user’s debit limit or credit limit."),
    PHONEPE016("PHONEPE016", "APP_VERSION_NOT_SUPPORTED", "Update phonePe app. You are on an older app version which does not support this feature.", "Update app. You are on an older app version which does not support this feature."),
    PHONEPE017("PHONEPE017", "TIMED_OUT", "Request timed out.", "Your request was timed out. Call the transaction status API to get the transaction state."),
    PHONEPE018("PHONEPE018", "TRANSACTION_NOT_FOUND", "Transaction Failed.", "Payment not initiated inside PhonePe."),
    PHONEPE019("PHONEPE019", "PAYMENT_PENDING", "Transaction Pending.", "Payment is pending.Call transaction status API to verify the transaction status."),
    PHONEPE020("PHONEPE020", "PAYMENT_ERROR", "Request timed out.", "Your request was timed out. Call the transaction status API to verify the transaction status."),
    PHONEPE021("PHONEPE021", "PAYMENT_DECLINED", "Transaction Failed.", "Payment declined by user."),
    PHONEPE022("PHONEPE022", "PAYMENT_CANCELLED", "Transaction Failed.", "Payment canceled"),
    PHONEPE023("PHONEPE023", "PHONEPE023", "Your phonePe wallet is not active. Try another payment method", ""),
    PHONEPE024("PHONEPE024", "PHONEPE024", "Your wallet is not linked. Please link your wallet", ""),
    PHONEPE025("PHONEPE025", "PHONEPE025", "Your balance is low.Sending deeplink to add money to your wallet", ""),
    PHONEPE026("PHONEPE026", "PHONEPE026", "Your balance is low and phonePe is not allowing to add money to you wallet. Please try another payment method", ""),
    PHONEPE027("PHONEPE027", "INVALID_USER_AUTH_TOKEN", "Your PhonePe account is not linked. Please link your account", "User auth token is invalid"),

    APBPAYTM001("APBPAYTM001","PGS-5088","Unable to send otp, try after sometimes or try another payment method", "Unable to send otp to the user"),
    APBPAYTM002("APBPAYTM002","PGS-5084","Failed to link wallet, try again", "Failed to link wallet"),
    APBPAYTM003("APBPAYTM003","PGS-5083","Failed to fetch wallet balance, try again", "Failed to fetch wallet balance"),
    APBPAYTM004("APBPAYTM004","PGS-5000","Oops! something went wrong", "Internal Server Error"),
    APBPAYTM005("APBPAYTM005","PGS-4113","Mobile no. is required", "Wallet Login id is required"),

    SUCCESS("SUCCESS", "SUCCESS", "Request served successfully.", "Request served successfully."),
    UNKNOWN("UNKNOWN", "", "Oops something went wrong", ""),

    FAIL001("FAIL001","FAILURE","Don’t worry, any amount deducted will be credited back to the payment source in a few working days.","Something went wrong"),
    FAIL002("FAIL002","PAYMENT-PENDING","You will soon get a confirmation message from us regarding your payment. Kindly wait for some time.","Payment under process");

    @Setter
    private String internalCode;

    private String externalCode;

    @Setter
    private String internalMessage;

    private String externalMessage;

    ErrorCode(String internalCode, String externalCode, String internalMessage, String externalMessage) {
        this.internalCode = internalCode;
        this.externalCode = externalCode;
        this.internalMessage = internalMessage;
        this.externalMessage = externalMessage;
    }

    @Deprecated
    public static ErrorCode getErrorCodesFromExternalCode(String externalCode) {
        for (ErrorCode errorCode : values()) {
            if (String.valueOf(errorCode.getExternalCode()).equalsIgnoreCase(externalCode)) {
                return errorCode;
            }
        }
        return UNKNOWN;
    }

}