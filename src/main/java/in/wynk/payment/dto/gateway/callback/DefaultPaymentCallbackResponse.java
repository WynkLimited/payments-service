package in.wynk.payment.dto.gateway.callback;

import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.dto.gateway.IRedirectSpec;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class DefaultPaymentCallbackResponse extends AbstractPaymentCallbackResponse implements IRedirectSpec<String> {
    private String redirectUrl;
    private TransactionStatus transactionStatus;
}
