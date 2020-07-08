package in.wynk.payment.dto.response;

import in.wynk.commons.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingStatus {

    private TransactionStatus transactionStatus;

}
