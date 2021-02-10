package in.wynk.payment.core.constant;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.exception.WynkRuntimeException;
import org.apache.commons.lang3.StringUtils;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_METHOD;

public enum StatusMode {
    SOURCE("SOURCE"), LOCAL("LOCAL");

    @Analysed(name = PAYMENT_METHOD)
    private final String code;

    public static StatusMode getFromMode(String codeStr) {
        for (PaymentCode code : values()) {
            if (StringUtils.equalsIgnoreCase(codeStr, code.getCode())) {
                return code;
            }
        }
        throw new WynkRuntimeException(PaymentErrorType.PAY001);

    }
