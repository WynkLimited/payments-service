package in.wynk.payment.dto.gpbs.request.externalTransaction;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class OneTimeExternalTransaction {
    private String externalTransactionToken;
}
