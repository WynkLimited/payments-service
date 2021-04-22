package in.wynk.payment.core.constant;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.exception.WynkRuntimeException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import static in.wynk.payment.core.constant.BeanConstant.*;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_GATEWAY;

@Getter
@RequiredArgsConstructor
@AnalysedEntity
public enum PaymentCode {

    AMAZON_IAP(AMAZON_IAP_PAYMENT_SERVICE, false),
    ITUNES(ITUNES_PAYMENT_SERVICE, false),
    PAYU(PAYU_MERCHANT_PAYMENT_SERVICE, true),
    PAYTM_WALLET(PAYTM_MERCHANT_WALLET_SERVICE, true),
    PHONEPE_WALLET(PHONEPE_MERCHANT_PAYMENT_SERVICE, true),
    PHONEPE_AUTO_DEBIT(PHONEPE_MERCHANT_PAYMENT_SERVICE, true),
    GOOGLE_WALLET(GOOGLE_WALLET_MERCHANT_PAYMENT_SERVICE, false),
    APB_GATEWAY(APB_MERCHANT_PAYMENT_SERVICE, true),
    SE_BILLING(ACB_MERCHANT_PAYMENT_SERVICE, false);

    @Analysed(name = PAYMENT_GATEWAY)
    private final String code;
    private final boolean isInternalRecurring;

    public static PaymentCode getFromCode(String codeStr) {
        for (PaymentCode code : values()) {
            if (StringUtils.equalsIgnoreCase(codeStr, code.getCode())) {
                return code;
            }
        }
        throw new WynkRuntimeException(PaymentErrorType.PAY001);
    }

}
