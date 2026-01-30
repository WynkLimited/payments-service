package in.wynk.payment.dto.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DefaultPaymentStatusResponse extends AbstractPaymentStatusResponse {
}