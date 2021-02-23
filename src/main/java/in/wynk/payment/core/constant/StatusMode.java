package in.wynk.payment.core.constant;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.exception.WynkRuntimeException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import static in.wynk.payment.core.constant.PaymentConstants.SOURCE_MODE;

@Getter
@AnalysedEntity
@RequiredArgsConstructor
public enum StatusMode {
    SOURCE("SOURCE"), LOCAL("LOCAL");

    @Analysed(name = SOURCE_MODE)
    private final String code;

    public static StatusMode getFromMode(String codeStr) {
        for (StatusMode code : values()) {
            if (StringUtils.equalsIgnoreCase(codeStr, code.getCode())) {
                return code;
            }
        }
        throw new WynkRuntimeException(PaymentErrorType.PAY001);

    }
}