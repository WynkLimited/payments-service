package in.wynk.payment.dto.phonepe;

public interface PhonePeConstants {
    String X_VERIFY = "x-verify";
    String CONTENT_TYPE = "content-type";
    String X_REDIRECT_URL = "x-redirect-url";
    String X_REDIRECT_MODE = "x-redirect-mode";
    String PHONE_STATUS_CODE = "phonePeStatusCode";
    String DEBIT_API = "/v4/debit";
    String REFUND_API = "/v3/credit/backToSource";
    String X_DEVICE_ID="x-device-id";
    String X_VERIFY_SUFFIX="###1";
    String TRANS_STATUS_API_PREFIX="/v3/transaction/";
    String TRANS_STATUS_API_SUFFIX="/status";
    String PHONEPE_OTP_TOKEN="phonePeOtpToken";
    String AUTO_DEBIT_API = "/v3/wallet/debit";
    String TRIGGER_OTP_API = "/v3/merchant/otp/send";
    String VERIFY_OTP_API = "/v3/merchant/otp/verify";
    String UNLINK_API = "/v3/merchant/token/unlink";
    String BALANCE_API = "/v3/wallet/balance";
    String TOPUP_API = "/v3/wallet/topup";
    String PHONEPE_VERSION_CODE="phonePeVersionCode";
}
