package in.wynk.payment.presentation.dto.verify;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VpaVerifyUserPaymentResponse extends VerifyUserPaymentResponse {
}
