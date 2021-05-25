package in.wynk.payment.dto;

import lombok.Getter;

@Getter
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
    PAYTM0010("PAYTM0010", "240", "Invalid total amount", "Invalid total amount"),
    PAYTM0011("PAYTM0011", "243", "Wallet not exist for the user", "Wallet not exist for the user"),
    PAYTM0012("PAYTM0012", "260", "Maximum allowed amount in Wallet exceeds limit", "Maximum allowed amount in Wallet exceeds limit"),
    PAYTM0013("PAYTM0013", "267", "User does not exist", "User does not exist"),
    PAYTM0014("PAYTM0014", "274", "User not verified", "User not verified"),
    PAYTM0015("PAYTM0015", "295", "Your payment failed as the UPI ID entered is incorrect. Please try again by entering a valid VPA or use a different method to complete the payment.", "Your payment failed as the UPI ID entered is incorrect. Please try again by entering a valid VPA or use a different method to complete the payment."),
    PAYTM0016("PAYTM0016", "305", "Merchant Id not registered.", "Merchant Id not registered."),
    PAYTM0017("PAYTM0017", "325", "Duplicate order id", "Duplicate order id"),
    PAYTM0018("PAYTM0018", "327", "Channel is not associated", "Channel is not associated"),
    PAYTM0019("PAYTM0019", "330", "Paytm checksum mismatch. Checksum provided is invalid", "Paytm checksum mismatch. Checksum provided is invalid"),
    PAYTM0020("PAYTM0020", "334", "Invalid Order Id.", "Invalid Order Id."),
    PAYTM0021("PAYTM0021", "335", "Mid is invalid", "Mid is invalid"),
    PAYTM0022("PAYTM0022", "343", "Invalid Token", "Invalid Token"),
    PAYTM0023("PAYTM0023", "344", "Invalid wallet type", "Invalid wallet type"),
    PAYTM0024("PAYTM0024", "345", "Request not unique", "Request not unique"),
    PAYTM0025("PAYTM0025", "400", "Bad request", "Bad request"),
    PAYTM0026("PAYTM0026", "401", "Unauthorized", "Unauthorized"),
    PAYTM0027("PAYTM0027", "402", "Looks like the payment is not complete. Please wait while we confirm the status with your bank.", "Looks like the payment is not complete. Please wait while we confirm the status with your bank."),
    PAYTM0028("PAYTM0028", "403", "Unauthorized Access, scope is not refreshable", "Unauthorized Access, scope is not refreshable"),
    PAYTM0029("PAYTM0029", "434", "Bad request", "Bad request"),
    PAYTM0030("PAYTM0030", "501", "System Error", "System Error"),
    PAYTM0031("PAYTM0031", "530", "Invalid Token", "Invalid Token"),
    PAYTM0032("PAYTM0032", "600", "Invalid refund request.", "Invalid refund request."),
    PAYTM0033("PAYTM0033", "601", "Refund request was raised for this transaction. But it is pending state.", "Refund request was raised for this transaction. But it is pending state."),
    PAYTM0034("PAYTM0034", "607", "Refund can not be initiated for a cancelled transaction.", "Refund can not be initiated for a cancelled transaction."),
    PAYTM0035("PAYTM0035", "617", "Refund Already Raised (If merchant repeated their request within the 10 minutes after initiate the first refund request)", "Refund Already Raised (If merchant repeated their request within the 10 minutes after initiate the first refund request)"),
    PAYTM0036("PAYTM0036", "619", "Invalid refund amount", "Invalid refund amount"),
    PAYTM0037("PAYTM0037", "620", "BALANCE_NOT_ENOUGH", "BALANCE_NOT_ENOUGH"),
    PAYTM0038("PAYTM0038", "626", "Another Refund on same order is already in Progress, please retry after few minutes", "Another Refund on same order is already in Progress, please retry after few minutes"),
    PAYTM0039("PAYTM0039", "627", "Order Details Mismatch", "Order Details Mismatch"),
    PAYTM0040("PAYTM0040", "628", "Refund request was raised to respective bank. But it is in pending state from bank side.", "Refund request was raised to respective bank. But it is in pending state from bank side."),
    PAYTM0041("PAYTM0041", "629", "Refund is already Successful", "Refund is already Successful"),
    PAYTM0042("PAYTM0042", "631", "Record not found", "Record not found"),
    PAYTM0043("PAYTM0043", "635", "Partial Refund under Rupee 1 is not allowed", "Partial Refund under Rupee 1 is not allowed"),
    PAYTM0044("PAYTM0044", "712", "Applying Promo Code Failed", "Applying Promo Code Failed"),
    PAYTM0045("PAYTM0045", "810", "Txn Failed", "Txn Failed"),
    PAYTM0046("PAYTM0046", "BE1400001", "Success", "Success"),
    PAYTM0047("PAYTM0047", "BE1422001", "scope is not refreshable, when some token is requested for refresh and is not refreshable.", "scope is not refreshable, when some token is requested for refresh and is not refreshable."),
    PAYTM0048("PAYTM0048", "BE1422002", "invalid refresh token", "invalid refresh token"),
    PAYTM0049("PAYTM0049", "BE1423001", "authorization failed, token format not supported, illegal parameters etc.", "authorization failed, token format not supported, illegal parameters etc."),
    PAYTM0050("PAYTM0050", "BE1423003", "The grant type is not given to the client", "The grant type is not given to the client"),
    PAYTM0051("PAYTM0051", "BE1423004", "Authorization is invalid", "Authorization is invalid"),
    PAYTM0052("PAYTM0052", "BE1423005", "Invalid Authorization Code", "Invalid Authorization Code"),
    PAYTM0053("PAYTM0053", "BE1423006", "Client permission not found", "Client permission not found"),
    PAYTM0054("PAYTM0054", "BE1423011", "Authorization client and state token client mismatch", "Authorization client and state token client mismatch"),
    PAYTM0055("PAYTM0055", "BE1423012", "Device Identifier is missing", "Device Identifier is missing"),
    PAYTM0056("PAYTM0056", "BE1423013", "Device Identifier is mismatch", "Device Identifier is mismatch"),
    PAYTM0057("PAYTM0057", "BE1424001", "We have found suspicious activity from this number. Therefore, we have blocked this account. Please raise a request at paytm.com/care to unblock your account.", "We have found suspicious activity from this number. Therefore, we have blocked this account. Please raise a request at paytm.com/care to unblock your account."),
    PAYTM0058("PAYTM0058", "BE1425004", "Mobile number is already pending for verification. Please try after 48 hours.", "Mobile number is already pending for verification. Please try after 48 hours."),
    PAYTM0059("PAYTM0059", "BE1425005", "Scope not allowed", "Scope not allowed"),
    PAYTM0060("PAYTM0060", "BE1425006", "Oops! You have reached an OTP limit, please raise a query at paytm.com/care.", "Oops! You have reached an OTP limit, please raise a query at paytm.com/care."),
    PAYTM0061("PAYTM0061", "BE1425007", "Please enter a valid OTP", "Please enter a valid OTP"),
    PAYTM0062("PAYTM0062", "BE1425008", "You have exceeded the number of attempts for entering a valid OTP. Please click Resend to continue with new OTP.", "You have exceeded the number of attempts for entering a valid OTP. Please click Resend to continue with new OTP."),
    PAYTM0063("PAYTM0063", "BE1426003", "There was some issue in processing this request", "There was some issue in processing this request"),
    PAYTM0064("PAYTM0064", "BE1426011", "We have found a suspicious activity from this number. Therefore, we have blocked your account. Please raise a request at paytm.com/care. The response code may change.", "We have found a suspicious activity from this number. Therefore, we have blocked your account. Please raise a request at paytm.com/care. The response code may change."),
    PAYTM0065("PAYTM0065", "BE1526000", "internal server error", "internal server error"),
    PAYTM0066("PAYTM0066", "FI_0001", "Invalid Request.", "Invalid Request."),
    PAYTM0067("PAYTM0067", "FI_0002", "Merchant is on agreement pay.", "Merchant is on agreement pay."),
    PAYTM0068("PAYTM0068", "FI_0003", "Invalid Token.", "Invalid Token."),
    PAYTM0069("PAYTM0069", "GE_0003", "We could not get the requested details. Please try again.", "We could not get the requested details. Please try again."),
    PAYTM0070("PAYTM0070", "GE_3", "Internal server error.", "Internal server error."),
    PAYTM0071("PAYTM0071", "NA", "Client permission not found", "Client permission not found"),
    PAYTM0072("PAYTM0072", "SUCCESS", "Request served successfully.", "Request served successfully."),
    PAYTM0073("PAYTM0073", "WM_1003", "Merchant does not exist.", "Merchant does not exist."),
    UNKNOWN("UNKNOWN", "UNKNOWN", "Oops something went wrong", "Oops something went wrong");

    private String internalCode;
    private String externalCode;
    private String internalMessage;
    private String externalMessage;

    ErrorCode(String internalCode, String externalCode, String internalMessage, String externalMessage) {
        this.internalCode = internalCode;
        this.externalCode = externalCode;
        this.internalMessage = internalMessage;
        this.externalMessage = externalMessage;
    }

    public static ErrorCode getErrorCodesFromExternalCode(String externalCode) {
        for (ErrorCode errorCode : values()) {
            if (String.valueOf(errorCode.getExternalCode()).equalsIgnoreCase(externalCode)) {
                return errorCode;
            }
        }
        return UNKNOWN;
    }

}