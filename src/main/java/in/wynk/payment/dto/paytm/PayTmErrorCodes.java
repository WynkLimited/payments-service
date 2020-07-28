package in.wynk.payment.dto.paytm;

import org.apache.commons.lang3.StringUtils;

public enum PayTmErrorCodes {
    /**
     * Auth/Account specific error codes
     */
    INVALID_AUTH("430", "Invalid Authorization"),
    INVALID_MSISDN("431", "Invalid Msisdn"),
    LOGIN_FAILED("432", "Login failed"),
    ACCOUNT_BLOCKED("433", "Account blocked"),
    BAD_REQUEST("434", "Bad request"),
    INVALID_EMAIL("465", "Invalid email"),
    INVALID_TOKEN("530", "Invalid token"),
    USER_NOT_FOUND("404", "User doesn't exists"),
    UNAUTHORIZED_ACCESS("403", "Unauthorized access"),

    GENERIC_ERROR_01("GE_0001", "Unknown error"),
    GENERIC_ERROR_03("GE_0003", "We could not get the requested details. Please try again"),

    CS_0000("CS_0000", "Request served successfully"),
    CS_0001("CS_0001", "Invalid request/ requestBody received"),
    CS_0002("CS_0002", "Invalid userToken received"),
    CS_0003("CS_0003", "Invalid mid received"),
    CS_0004("CS_0004", "Invalid amount received"),
    INSUFFICIENT_BALANCE("235", "Insufficient Balance"),

    REQUEST_TIMED_OUT("408", "Request timed out"),
    INTERNAL_SERVER_ERROR("500", "Internal server error"),
    INCORRECT_MERCHANT_DETAILS("CBM_1001", "Balance could not be fetched due to incorrect merchant details"),
    INCORRECT_PAYEE_DETAILS("CBM_1002", "Balance could not be fetched due to incorrect payee details"),
    TRY_AGAIN("AM_1001", "Could not complete request. Please retry again"),
    UNKNOWN("", "Unknown reason");

    private String code;
    private String message;

    PayTmErrorCodes(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static PayTmErrorCodes resolveErrorCode(String code) {
        if (StringUtils.isBlank(code)) {
            return UNKNOWN;
        }

        for (PayTmErrorCodes errCode : values()) {
            if (String.valueOf(errCode.code).equalsIgnoreCase(code)) {
                return errCode;
            }
        }

        return UNKNOWN;
    }

}
