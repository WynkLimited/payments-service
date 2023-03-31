package in.wynk.payment.dto.gateway.delete;

import in.wynk.payment.dto.common.response.AbstractPaymentMethodDeleteResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class DeleteCardResponse extends AbstractPaymentMethodDeleteResponse {
}
