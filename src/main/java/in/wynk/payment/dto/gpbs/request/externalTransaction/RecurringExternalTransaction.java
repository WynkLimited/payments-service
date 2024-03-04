package in.wynk.payment.dto.gpbs.request.externalTransaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.gpbs.ExternalTransactionProgram;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecurringExternalTransaction {
    private String initialExternalTransactionId;
    private String externalTransactionToken;
    private ExternalTransactionProgram migratedTransactionProgram;
    private ExternalSubscription externalSubscription;
}
