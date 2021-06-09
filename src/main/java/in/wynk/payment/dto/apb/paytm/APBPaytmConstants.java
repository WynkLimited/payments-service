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
}
