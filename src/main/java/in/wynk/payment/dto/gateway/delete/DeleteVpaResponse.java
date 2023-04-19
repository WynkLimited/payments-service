package in.wynk.payment.dto.gateway.delete;

import in.wynk.payment.dto.common.response.AbstractPaymentAccountDeletionResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class DeleteVpaResponse extends AbstractPaymentAccountDeletionResponse {
}
