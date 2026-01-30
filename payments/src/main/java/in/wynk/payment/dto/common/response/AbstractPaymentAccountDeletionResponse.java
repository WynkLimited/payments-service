package in.wynk.payment.dto.common.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class AbstractPaymentAccountDeletionResponse {
    private Boolean deleted;
}

