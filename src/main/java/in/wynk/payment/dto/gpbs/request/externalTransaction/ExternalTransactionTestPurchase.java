package in.wynk.payment.dto.gpbs.request.externalTransaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalTransactionTestPurchase {
}
