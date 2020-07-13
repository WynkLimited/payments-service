package in.wynk.payment.core.constant;

import in.wynk.exception.WynkRuntimeException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import static in.wynk.payment.core.constant.BeanConstant.*;

@Getter
@RequiredArgsConstructor
public enum PaymentCode {

    GOOGLE_UPI("googleUPI"),
    AMAZON_WALLET("amazonWallet"),
    AMAZON_IAP(AMAZON_IAP_PAYMENT_SERVICE),
    ITUNES(ITUNES_PAYMENT_SERVICE),
    PAYU(PAYU_MERCHANT_PAYMENT_SERVICE),
    PAYTM_WALLET(PAYTM_MERCHANT_WALLET_SERVICE),
    PHONEPE_WALLET(PHONEPE_MERCHANT_PAYMENT_SERVICE),
    GOOGLE_WALLET(GOOGLE_WALLET_MERCHANT_PAYMENT_SERVICE),
    APB_GATEWAY(APB_MERCHANT_PAYMENT_SERVICE),
    SE_BILLING(ACB_MERCHANT_PAYMENT_SERVICE);

    private final String code;

    public static PaymentCode getFromCode(String codeStr) {
        for (PaymentCode code : values()) {
            if (StringUtils.equalsIgnoreCase(codeStr, code.getCode())) {
                return code;
            }
        }
        throw new WynkRuntimeException(PaymentErrorType.PAY001);
    }

}
