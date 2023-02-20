package in.wynk.payment.dto.gateway.verify;

import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.dto.payu.VerificationType;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public abstract class AbstractPaymentInstrumentVerificationResponse {
    private String verifyValue;
    private VerificationType verificationType;
    private boolean isValid;
    private boolean isAutoRenewSupported;
    private TransactionStatus transactionStatus;

}
