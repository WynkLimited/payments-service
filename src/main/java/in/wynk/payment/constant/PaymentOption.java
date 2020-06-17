package in.wynk.payment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentOption {

    GOOGLE_UPI("googleUPI"),
    AMAZON_WALLET("amazonWallet"),
    ITUNES("itunesMerchantPaymentBean"),
    PAYU_GATEWAY("payUMerchantPaymentBean"),
    PAYTM_WALLET("payTMMerchantWalletBean"),
    PHONEPE_WALLET("phonePeMerchantPaymentBean"),
    GOOGLE_WALLET("googleWalletMerchantPaymentBean"),
    APB_GATEWAY("airtelPaymentBankMerchantPaymentBean"),
    SE_BILLING("airtelCarrierBillingMerchantPaymentBean");

    private final String type;

}
