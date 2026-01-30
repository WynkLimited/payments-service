package in.wynk.payment.presentation.dto.callback;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.enums.TransactionStatus;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DefaultCallbackResponse extends PaymentCallbackResponse {
    private TransactionStatus transactionStatus;
    private String redirectUrl;
}