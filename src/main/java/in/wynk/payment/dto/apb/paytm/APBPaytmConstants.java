package in.wynk.payment.dto.apb.paytm;

public interface APBPaytmConstants {
    String ABP_PAYTM_OTP_TOKEN="apbPaytmOtpToken";
    String ABP_PAYTM_ENCRYPTED_TOKEN="apbEncryptedToken";

    String ABP_PAYTM_BALANCE_AMOUNT="apbPaytmBalanceAmount";
    String ABP_PAYTM_SEND_OTP="/v1/wallet/initiate-link";
    String ABP_PAYTM_VERIFY_OTP="/v1/wallet/link";
    String ABP_PAYTM_TOP_UP="/v1/wallet/topup";
    String ABP_PAYTM_GET_BALANCE="/v1/wallet/profile";
    String ABP_PAYTM_WALLET_PAYMENT="/v1/payment/wallet";

    String AUTHORIZATION="authorization";
    String CHANNEL_ID="channel-id";
    String CONTENT_TYPE="content-type";
    String ACCEPT="accept";

    String ABP_PAYTM_AUTHORIZATION="Basic cGF5bWVudDpwYXlAcWNrc2x2cg==";
    String ABP_PAYTM_CHANNEL_ID="WEB_UNAUTH";
    String ABP_PAYTM_CONTENT_TYPE="application/json";
    String ABP_PAYTM_ACCEPT="application/json";

    String AUTH_TYPE_WEB_UNAUTH="WEB_UNAUTH";
    String CHANNEL_WEB="WEB";
    int CIRCLE_ID=-1;
    String WALLET_PAYTM="PAYTM";
    String PAYMENT_MODE_WALLET="WALLET";
    String CURRENCY_INR="INR";

}
