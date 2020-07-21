package in.wynk.payment.core.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ConsultBalanceResponse {

    private ConsultBalanceResponseBody body;
    private ConsultBalanceResponseHead head;

}
