package in.wynk.payment.dto.phonepe.autodebit;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.StringUtils;

@Getter
@SuperBuilder
@NoArgsConstructor
public class PhonePeAutoDebitCallbackRequest extends CallbackRequest {

    private String transactionId;
    private String phonePeVersionCode;

    public Long getPhonePeVersionCode() {
        if(!StringUtils.isNotEmpty(phonePeVersionCode) || !NumberUtils.isNumber(phonePeVersionCode)) throw new WynkRuntimeException(PaymentErrorType.PAY400, "malformed phonePeVersionCode is supplied");
        return NumberUtils.toLong(phonePeVersionCode);
    }

}