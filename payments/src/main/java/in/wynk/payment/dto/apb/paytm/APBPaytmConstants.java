package in.wynk.payment.dto.apb.paytm;

public interface APBPaytmConstants {
    String ABP_PAYTM_OTP_TOKEN="apbPaytmOtpToken";

    String ABP_PAYTM_SEND_OTP="/v1/wallet/initiate-link";
    String ABP_PAYTM_VERIFY_OTP="/v1/wallet/link";
    String ABP_PAYTM_TOP_UP="/v1/wallet/topup";
    String ABP_PAYTM_GET_BALANCE="/v1/wallet/profile";
    String ABP_PAYTM_WALLET_PAYMENT="/v1/payment/wallet";
    String ABP_PAYTM_TRANSACTION_STATUS="/v1/payment/status/order/";

    String ABP_PAYTM_CHANNEL_ID="WEB_UNAUTH";
    String AUTH_TYPE_WEB_UNAUTH="WEB_UNAUTH";
    String AUTH_TYPE_UN_AUTH="UN_AUTH";
    String CHANNEL_WEB="WEB";
    int CIRCLE_ID=-1;
    String WALLET_PAYTM="PAYTM";
    String CURRENCY_INR="INR";
    String ABP_PAYTM_PAYMENT_SUCCESS="PAYMENT_SUCCESS";
    String ABP_PAYTM_PAYMENT_PENDING="PAYMENT_PENDING";
    String ABP_ADD_MONEY_SUCCESS="addMoney_Success";
    String CHANNEL_ID="channel-id";
}
